package com.customsolutions.android.utl;

import android.Manifest;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class TaskList extends UtlNavDrawerActivity
{
	private static final String TAG = "TaskList";

	/** Flag indicating if a permissions request has been displayed. */
	private boolean _permissionsRequestDisplayed = false;

	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		_permissionsRequestDisplayed = false;
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
		// Create the TaskListFragment that will be placed:
		TaskListFragment tl = new TaskListFragment();
		
		// Fetch the parameters for this view.  Try the savedInstanceState first, followed by 
		// the Intent extras.
		String topLevel;
		String viewName;
		if (savedInstanceState!=null && savedInstanceState.containsKey("top_level") &&
        	savedInstanceState.containsKey("view_name") && savedInstanceState.containsKey("title"))
		{
			topLevel = savedInstanceState.getString("top_level");
			viewName = savedInstanceState.getString("view_name");
			
			// The fragment being placed needs arguments set, to make sure it doesn't read from the 
			// Intent (which may be out of date).
			tl.setArguments(savedInstanceState);
		}
		else
		{
			Bundle extras = getIntent().getExtras();
			if (extras==null || !extras.containsKey("top_level") || !extras.containsKey("view_name"))
			{
				Util.log("Null or invalid Bundle passed to TaskList.java.");
				finish();
				return;
			}
			topLevel = extras.getString("top_level");
			viewName = extras.getString("view_name");
			Util.log("Opening a Task List. Top Level: "+topLevel+"; View Name: "+viewName);
		}
		
		// The instance of UtlNavDrawerActivity knows where to place the task list fragment:
		placeFragment(UtlNavDrawerActivity.FRAG_LIST,tl,TaskListFragment.FRAG_TAG+"/"+topLevel+"/"+
			viewName);

	}

    @Override
    public void onResume()
    {
        super.onResume();

        // Because the user can disable permissions when the app is not running, we need to
		// disable features linked to those permissions if applicable.
		if (_settings.getBoolean(PrefNames.CALENDAR_ENABLED,false) && (
			!isPermissionGranted(Manifest.permission.READ_CALENDAR) ||
			!isPermissionGranted(Manifest.permission.WRITE_CALENDAR)
			))
		{
			Util.log("Disabling calendar linking due to lack of permission.");
			_settings.edit().putBoolean(PrefNames.CALENDAR_ENABLED,false).apply();
		}
		if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED,false) &&
			!isPermissionGranted(Manifest.permission.READ_CONTACTS))
		{
			Util.log("Disabling contact linking due to lack of permission.");
			_settings.edit().putBoolean(PrefNames.CONTACTS_ENABLED,false).apply();
		}
		if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED,false) &&
			(
				!isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
				!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
				(Build.VERSION.SDK_INT>Build.VERSION_CODES.P && !isPermissionGranted(Manifest.
					permission.ACCESS_BACKGROUND_LOCATION))
			))
		{
			Util.log("Disabling location features due to lack of permission.");
			_settings.edit().putBoolean(PrefNames.LOCATIONS_ENABLED,false).apply();
		}

        // Check to see if we need to show the "what's new" message, but only if the user is not
        // required to sign in again.
        if (!Util.accountSignInCheck(this))
        {
            int messageVersion = getResources().getInteger(R.integer.whats_new_version);
            int versionLastSeen = Util.settings.getInt(PrefNames.WHATS_NEW_VERSION_SEEN, 0);
            if (messageVersion > versionLastSeen)
            {
                Util.updatePref(PrefNames.WHATS_NEW_VERSION_SEEN, messageVersion);
                startActivity(new Intent(this, WhatsNew.class));
            }
            else if (!_permissionsRequestDisplayed)
			{
				// Check to see if the user has disabled file access permission. It must be
				// enabled to continue.
				if (_settings.getBoolean(PrefNames.REJECTED_STORAGE_PERMISSION,false) ||
					Util.hasPermissionForBackup(this))
				{
					// The user previously rejected storage permission, or storage permission
					// is already granted.  Move on.
					checkLocationAccuracy();
					return;
				}
				_permissionsRequestDisplayed = true;
				requestPermissions(
					new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.
						WRITE_EXTERNAL_STORAGE},
					new Runnable()
					{
						@Override
						public void run()
						{
							// Permission Granted.
							_permissionsRequestDisplayed = false;
							if (Util.hasPermissionForBackup(TaskList.this))
							{
								_settings.edit().putBoolean(PrefNames.BACKUP_DAILY,true)
									.apply();
							}
							checkManageExternalStoragePermission();
						}
					},
					true,
					getString(R.string.file_permission_reason),
					new Runnable()
					{
						@Override
						public void run()
						{
							// Permission denied.
							_permissionsRequestDisplayed = false;
							_settings.edit().putBoolean(PrefNames.BACKUP_DAILY,false).apply();
							_settings.edit().putBoolean(PrefNames.REJECTED_STORAGE_PERMISSION,
								true).apply();
							checkLocationAccuracy();
						}
					}
				);
			}
        }
    }

    /** Check the permission to manage external storage. */
    private void checkManageExternalStoragePermission()
	{
		checkLocationAccuracy();
	}

    /** Check location accuracy. */
    private void checkLocationAccuracy()
	{
		// Check to see if the user has an issue with the location settings not being accurate enough.
		if (!Util.promptForHighLocationAccuracy(TaskList.this))
		{
			checkForGoogleAccountPermission();
		}
	}

    /** Check for permission to get the google accounts on the device, if it's needed. */
    private void checkForGoogleAccountPermission()
	{
		// If the GET_ACCOUNTS permission has been granted, there's nothing to do.
		if (isPermissionGranted(Manifest.permission.GET_ACCOUNTS))
			return;

		// First, go through the accounts and see if any link to a google account.
		final AccountsDbAdapter adb = new AccountsDbAdapter();
		Cursor c = adb.getAllAccounts();
		boolean hasGoogleAccount = false;
		while (c.moveToNext())
		{
			UTLAccount a = adb.getUTLAccount(c);
			if (a.sync_service==UTLAccount.SYNC_GOOGLE && a.protocol==GTasksInterface.
				PROTOCOL_DEVICE_ACCOUNT)
			{
				hasGoogleAccount = true;
				break;
			}
		}
		c.close();

		if (!hasGoogleAccount)
			return;

		// Ask for the permission, which must be granted or else the app must exit.
		_permissionsRequestDisplayed = true;
		requestPermissions(
			new String[]{Manifest.permission.GET_ACCOUNTS},
			new Runnable()
			{
				@Override
				public void run()
				{
					_permissionsRequestDisplayed = false;
					// Now that the permission is accepted, Synchronizer needs to unlink the
					// accounts until the next sync. A reinitialization is needed in order for
					// sync to work again.
					Cursor c = adb.getAllAccounts();
					while (c.moveToNext())
					{
						UTLAccount a = adb.getUTLAccount(c);
						if (a.sync_service==UTLAccount.SYNC_GOOGLE && a.protocol==GTasksInterface.
							PROTOCOL_DEVICE_ACCOUNT)
						{
							Intent i = new Intent(TaskList.this, Synchronizer.class);
							i.putExtra("command", "unlink_account");
							i.putExtra("account_id", a._id);
							Synchronizer.enqueueWork(TaskList.this,i);
						}
					}
					c.close();
				}
			},
			true,
			getString(R.string.accounts_permission_required),
			new Runnable()
			{
				@Override
				public void run()
				{
					finish();
				}
			}
		);
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
       return super.onOptionsItemSelected(item);
	}
	
	// Called before the activity is destroyed due to orientation change:
    @Override
	public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	
    	// We need to get the  parameters of the current task list fragment:
    	TaskListFragment taskListFrag = (TaskListFragment) getFragmentByType(FRAG_LIST);
    	b.putString("top_level", taskListFrag.topLevel);
    	b.putString("view_name", taskListFrag.viewName);
    	b.putString("title", taskListFrag._title);
    }
    
    // Switch to a new task list.  Call this from other fragments whenever the task list needs to change.
    public void changeTaskList(TaskListFragment newFragment, String topLevel, String viewName)
    {
    	placeFragment(UtlNavDrawerActivity.FRAG_LIST, newFragment, TaskListFragment.FRAG_TAG + "/" + 
    		topLevel + "/" + viewName);
    	
    	// We also need to clear the detail view and display a message saying the user can tap on a task
    	// to see its details.
    	showDetailPaneMessage(getString(R.string.Select_a_task_to_display));
    }
    
    // Handle a change in a task when a task is altered from the viewer or editor.
    public void handleTaskChange()
    {
    	TaskListFragment listFrag = (TaskListFragment)getFragmentByType(UtlNavDrawerActivity.FRAG_LIST);
		if (listFrag!=null)
		{
			// If we're in multi-select mode, then get out of it.
			if (listFrag._mode==TaskListFragment.MODE_MULTI_SELECT)
				listFrag.handleModeChange(TaskListFragment.MODE_NORMAL);  // Also refreshes.
			else
			{
				listFrag.saveCurrentPosition();
				listFrag.refreshData();
				listFrag.restorePosition();
			}
		}
    }
    
    // Make sure any Activity Results received here are passed to the task list fragment:
    @Override
    protected void onActivityResult(int requestCode, int resultCode,Intent data) 
    {
    	Util.log("TaskList: onActivityResult called with requestCode "+requestCode+" and "+
			"resultCode "+resultCode+". Intent: "+Util.intentToString(data,2));

    	if (requestCode==Util.REQUEST_CODE_LOCATION_SETTINGS && resultCode==RESULT_OK)
		{
			Util.setupGeofencing(this);
		}

    	super.onActivityResult(requestCode, resultCode, data);
    }
}
