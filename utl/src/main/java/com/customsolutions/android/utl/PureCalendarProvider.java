package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class PureCalendarProvider extends ContentProvider
{
	// If true, send all tasks to Pure Calendar, even if they are future tasks:
	private static final boolean SEND_ALL_TASKS = false;
	
	private TasksDbAdapter _tasksDB;
	private FoldersDbAdapter _foldersDB;
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri)
	{
		// TODO Auto-generated method stub
		if (uri.getPath()!=null && (uri.getPath().endsWith("tasks") ||
			uri.getPath().endsWith("tasks/")))
		{
			return "vnd.android.cursor.item/vnd.customsolutions.tasks";
		}
		else if (uri.getPath()!=null && (uri.getPath().endsWith("tasks_classif") ||
			uri.getPath().endsWith("tasks_classif/")))
		{
			return "vnd.android.cursor.dir/vnd.customsolutions.tasks";
		}
		else
		{
			return "vnd.android.cursor.item/vnd.customsolutions.tasks";
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate()
	{
		// Initialize the app if it has not been:
		Util.appInit(this.getContext());
		
		_tasksDB = new TasksDbAdapter();
		_foldersDB = new FoldersDbAdapter();
		return true;
	}

	// For Pure Calendar, selection, selectionArgs, and sortOrder are not used.
	// Data to return (from Pure Calendar Developer):
	// String[] TASKS_FIELD_LIST = new String[] { ID /* long */ , NAME, DUE_DATE_AND_TIME 
	//    /* long, unix time */, PRIORITY /* integer */, FOLDER_ID /* string */, 
	//    SUBTASK_PARENT_ID /* long */, SUBTASK_LEVEL /* integer, 0 = root */, IMPORTANCE 
	//    /* integer */, IMPORTANCE_COLOR /* integer */};
    // String[] TASKS_CLASSIF_FIELD_LIST = new String[] { ID /* long */, NAME /* string */, 
	//    COLOR /* integer, not mandatory */ };
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
		String[] selectionArgs, String sortOrder)
	{
		if (uri.getPath()!=null && (uri.getPath().endsWith("tasks") ||
			uri.getPath().endsWith("tasks/")))
		{
			long queryDate; // This will be midnight on the following day.
	        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
	                Util.settings.getString("home_time_zone","")));  // Set to current time.
	        
	        // Get the offset in ms between the home time zone and the local one:
	    	TimeZone currentTimeZone = TimeZone.getDefault();
	    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
	    		"home_time_zone", "America/Los_Angeles"));
	    	long zoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
				defaultTimeZone.getOffset(System.currentTimeMillis());
	    	long baseTime = System.currentTimeMillis()+zoneOffset;
	    	
	    	// Get midnight on the following day:
	    	c.setTimeInMillis(baseTime);
            c.add(Calendar.DATE, 1);
            queryDate = Util.getMidnight(c.getTimeInMillis());
	    	
            // This cursor will hold the results in a form that Pure Calendar will recognize:
            MatrixCursor result = new MatrixCursor(new String[] { 
            	"id",
            	"name",
            	"due_date_and_time",
            	"priority",
            	"folder_id",
            	"subtask_parent_id",
            	"subtask_level",
            	"importance",
            	"importance_color",
            	"start_date_and_time"
            });
            
            int[] priorityColors = this.getContext().getResources().getIntArray(R.array.priority_colors);
            
            // Query the tasks:
			Cursor dbCursor;
			if (SEND_ALL_TASKS)
				dbCursor = _tasksDB.queryTasks("completed=?", new String[] {"0"}, null);
			else
			{
				dbCursor = _tasksDB.queryTasks("completed=? and (start_date=? or "+
					"start_date<?)",new String[]{"0","0",String.valueOf(queryDate)},null);
			}
			dbCursor.moveToPosition(-1);
			while (dbCursor.moveToNext())
			{
				// Fill in the row on the MatrixCursor (as defined above):
				UTLTask t = _tasksDB.getUTLTask(dbCursor);
				Object[] values = new Object[10];
				values[0] = t._id;
				values[1] = t.title;
				values[2] = t.due_date;
				values[3] = t.priority;
				values[4] = t.folder_id;
				values[5] = t.parent_id;
				values[6] = getSubtaskLevel(t);
				values[7] = t.importance;
				values[8] = priorityColors[t.priority];
				values[9] = t.start_date;
				result.addRow(values);
			}
			dbCursor.close();
			return result;
		}
		else if (uri.getPath()!=null && (uri.getPath().endsWith("tasks_classif") ||
			uri.getPath().endsWith("tasks_classif/")))
		{
			// This cursor will hold the results in a form that Pure Calendar will recognize:
            MatrixCursor result = new MatrixCursor(new String[] { 
            	"id",
            	"name"
            });
            
            Cursor dbCursor = _foldersDB.getFoldersByNameNoCase();
            dbCursor.moveToPosition(-1);
            while (dbCursor.moveToNext())
            {
            	Object[] values = new Object[2];
            	values[0] = Util.cLong(dbCursor, "_id");
            	values[1] = Util.cString(dbCursor, "title");
            	result.addRow(values);
            }
            dbCursor.close();
			return result;
		}
		else
		{
			Util.log("Bad URI from Pure Calendar: "+uri.toString());
			return null;
		}
	}

	// Given a task, get its subtask level (0=root):
	private int getSubtaskLevel(UTLTask t)
	{
		int level = 0;
		while (t!=null && t.parent_id>0)
		{
			t = _tasksDB.getTask(t.parent_id);
			level++;
		}
		return level;
	}
	@Override
	public int update(Uri uri, ContentValues values, String selection,
		String[] selectionArgs)
	{
		// TODO Auto-generated method stub
		return 0;
	}

}
