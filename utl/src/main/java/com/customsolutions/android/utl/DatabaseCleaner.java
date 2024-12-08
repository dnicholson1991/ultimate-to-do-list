package com.customsolutions.android.utl;

// This class handles cleanup of the database.  It removes old completed tasks,
// based on the user's setting, and it removes old activity log entries.
// No extra arguments are needed in the Intent passed here.

// This also does the following:
// - Moves forward repeating tasks with an optional due date (that are not syncing with TD)
// - Runs a license check.
// - Performs the daily backup, if the user wants to do this.
// - Re-registers geofences if they were previously disabled due to the user adjusting location
//   settings.
// - Uploads stats to our server.

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;

import java.io.File;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class DatabaseCleaner extends JobService
{
	private static final String TAG = "DatabaseCleaner";

	/** The Job ID used by this service. */
	public static final int JOB_ID = 1536705149;

	/** Keeps track of the number of asynchronous tasks are remaining. */
	private int _asyncTasksRemaining;

	/** The job parameters passed in. */
	private JobParameters _jobParams;

	@Override
	public boolean onStartJob(final JobParameters params)
	{
		// Initialize the app if needed:
    	Util.appInit(this);
    	
    	// Catch any uncaught exceptions:
    	Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
    	
		Thread t = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				performWork(params);
			}
		});
		t.start();
		return true;
	}

	/** Perform the work of this class. This method must be called from a background thread. */
	private void performWork(JobParameters params)
	{
		log("Starting Background Tasks");
		_jobParams = params;
		_asyncTasksRemaining = 0;
		SharedPreferences settings = this.getSharedPreferences(Util.PREFS_NAME,0);
		boolean hasSem = Util.acquireSemaphore("DatabaseCleaner", this);
		try
		{
			// Clean up old log entries of user activity:
			SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
			long endTime = System.currentTimeMillis() - 3*24*60*60*1000;
			try
			{
				db.execSQL("delete from log_data where timestamp<"+endTime);
			}
			catch (Exception e)
			{
				// This could happen if another thread is accessing the database.
				// Just ignore this since we will try again later.
			}

			// Clean up old completed tasks, based on the user's preference:
			int numDays = settings.getInt("purge_completed", 365);
			long timeToSubtract = (new Integer(numDays).longValue())*24*60*60*1000;
			endTime = System.currentTimeMillis() - timeToSubtract;
			if (endTime<0) endTime=0;
			try
			{
				db.execSQL("delete from tasks where completed=1 and completion_date<"+endTime);
			}
			catch (Exception e)
			{
				// This could happen if another thread is accessing the database.
				// Just ignore this since we will try again later.
			}

			// Look for repeating tasks that have an optional due date that has past.
			// If any of these tasks are found that are in a non-Toodledo account, then update
			// the start and due dates.
			TasksDbAdapter tasksDB = new TasksDbAdapter();
			AccountsDbAdapter accountsDB = new AccountsDbAdapter();
			long timeThreshold = System.currentTimeMillis()-24*60*60*1000;
			Cursor c = tasksDB.queryTasks("due_date<"+timeThreshold+
				" and completed=0 and repeat>0 and repeat!=9 and repeat!=109 and "+
				"due_modifier='optionally_on'", "_id");
			c.moveToPosition(-1);
			while (c.moveToNext())
			{
				UTLTask t = tasksDB.getUTLTask(c);
				UTLAccount a = accountsDB.getAccount(t.account_id);
				if (a!=null && a.sync_service!=UTLAccount.SYNC_TOODLEDO)
				{
					// Since the task is not complete, we can't repeat from completion date:
					int repeat = t.repeat;
					if (repeat>100) repeat-=100;

					if (t.start_date>0)
						t.start_date = Util.getNextDate(t.start_date, repeat, t.rep_advanced);
					if (t.due_date>0)
						t.due_date = Util.getNextDate(t.due_date, repeat, t.rep_advanced);
					if (t.reminder>0)
					{
						t.reminder = Util.getNextDate(t.reminder, repeat, t.rep_advanced);
						if (t.reminder>System.currentTimeMillis())
						{
							// We need to schedule a reminder notification:
							Util.scheduleReminderNotification(t);
						}
					}

					if (t.calEventUri!=null && t.calEventUri.length()>0 &&
						settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
					{
						// Adjust the linked calendar event:
						CalendarInterface ci = new CalendarInterface(this);
						ci.linkTaskWithCalendar(t);
					}

					t.mod_date = System.currentTimeMillis();
					tasksDB.modifyTask(t);
					log("Updated incomplete optional task '"+t.title+"'.");
				}
			}
			c.close();
			log("Removed old completed tasks and activity log entries.");

			// Let's also run a license check here:
            LicenseAppQuery.enqueueWork(this,new Intent(this,LicenseAppQuery.class));
		}
		finally
		{
			if (hasSem)
				Util._semaphore.release();
		}

		if (settings.getBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED,false))
		{
			// Location reminders are blocked due to the user's settings. In case the user
			// has fixed the issue, try re-enabling them.
			log("Trying to re-enable geofencing.");
			Util.setupGeofencing(this);
		}

		// Run a backup if the user wants, but only once per day:
		if (settings.getBoolean(PrefNames.BACKUP_DAILY, false))
		{
			// Make sure permission is granted first.
			if (!Util.hasPermissionForBackup(this))
			{
				log("Cannot perform database backup due to lack of permission.");
			}
			else if ((System.currentTimeMillis()-Util.getBackupFileModTime(this))>23.5*60*60*1000)
			{
				log("Starting database backup");
				String errorMsg = Util.performBackup(this);
				if (Util.isValid(errorMsg))
				{
					// The backup failed.
					Log.e(TAG,"Daily Backup Failure","The daily backup failed. "+errorMsg);
				}
				else
				{
					log("Daily backup succeeded.");
				}
			}
		}

		// Upload any accumulated stats to our server:
		_asyncTasksRemaining++;
		Stats.uploadStats(this,() -> {
			checkOnJobCompletion();
		});
	}

	@Override
	public boolean onStopJob(JobParameters params)
	{
		log("WARNING: onStopJob() called, but I can't stop it.");
		return true;
	}

	/** Check to see if the job is done, and notifiy Android if it is. Call this after an
	 * asynchronous task is done. */
	private void checkOnJobCompletion()
	{
		_asyncTasksRemaining--;
		if (_asyncTasksRemaining==0)
		{
			// Record the time these daily tasks have been performed.
			Util.updatePref(PrefNames.LAST_DB_CLEANUP,System.currentTimeMillis());

			// Notify the system that the job is done.
			jobFinished(_jobParams, false);
		}
	}

	/** Basic logging function. */
	private void log(String msg)
	{
		Log.v(TAG,msg);
	}
}
