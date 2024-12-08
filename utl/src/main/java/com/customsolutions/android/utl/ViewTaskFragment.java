package com.customsolutions.android.utl;

// This activity displays all of the details for a single task.
// To call this activity, put a Bundle in the Intent with the following keys/values:
// _id: The UTL task ID from the database
//
// OR, the intent passed in can have the following data:
// action: Set this to Intent.ACTION_VIEW
// data: This is an opaque URI defined as follows:
//     viewtask:<UTL task ID>
//         scheme is "viewtask"
//          opaque part is the task ID
//
// OR, this activity can be called with the following URIs:
// URI: content://com.customsolutions.android.utl.androidagendaprovider/tasks/<task #>
// URI: content://com.customsolutions.android.utl.purecalendarprovider/tasks/<task #>
//
// Bundle:
//     notification_time: The time in millis that the notification was displayed.
// In this case, the code will assume that the user tapped on a notification.
// 

// This task does not return anything to the caller.

import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;

import androidx.appcompat.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Html;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

public class ViewTaskFragment extends UtlFragment
{
    // IDs for options menu items:
    private static final int SETTINGS_ID = Menu.FIRST+1;
    private static final int HELP_ID = Menu.FIRST+2;
    private static final int MAP_ID = Menu.FIRST+3;
    private static final int NAV_ID = Menu.FIRST+4;
    private static final int REASSIGN_ID = Menu.FIRST+5;
    private static final int ADD_TASK_ID = Menu.FIRST+6;
    private static final int DELETE_ID = Menu.FIRST+7;
    private static final int CLONE_ID = Menu.FIRST+8;
    private static final int VIEW_SUBTASKS_ID = Menu.FIRST+9;
    private static final int VIEW_PARENT_ID = Menu.FIRST+10;
    private static final int TIMER_ID = Menu.FIRST+11;
    private static final int STAR_ID = Menu.FIRST+12;
    private static final int COMPLETED_ID = Menu.FIRST+13;
    private static final int EDIT_ID = Menu.FIRST+14;
    
    // The tag to use when placing this fragment:
    public static final String FRAG_TAG = "ViewTaskFragment";
    
    // The task ID we are displaying:
    private long _id;  
    
    // The task itself:
    private UTLTask task;
    
    // Codes to track responses to activities:
    public static final int EDIT_TASK = 1;
    public static final int ACTIVITY_ADD_SUBTASK = 2;
    
    // This keeps track of whether repeating tasks have been generated for this task:
    private boolean repeatingTasksGenerated;
    
    // Flag which indicates whether the user came here by tapping on a notification:
    private boolean arrivedFromNotification;
    
    // Set to true if this was called from a location reminder:
    private boolean fromLocationReminder;
    
    // If we arrived from a notification, this is the time the notification was displayed:
    private long notificationTime;
    
    private DialogInterface.OnClickListener snoozeListener;
    
    // For use in choosing collaborators:
    private ArrayList<String> _collIdList;
    
    // Quick reference to the Fragment's activity:
    private UtlActivity _a;
    
    // Quick reference to resources:
    private Resources _res;
    
    // Quick reference to settings:
    private SharedPreferences _settings;
    
    // Quick reference to split_screen setting:
    private int _splitScreen;
    
    // Reference to the title bar (if used)
    private TitleBar _tb = null;
    
    // Records if we're the only fragment in the activity.
    private boolean _isOnlyFragment;
    
    // Quick reference to the root view of this fragment:
    private ViewGroup _rootView;

	/** This indicates that the table fields need to be set to zero width (due to automatic
	 * stretching. */
	private boolean _zeroWidthFields;

    // This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.view_task, container, false);
    }
    
    // Called when activity is first created:
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
        
    	_a = (UtlActivity) getActivity();
        _res = _a.getResources();
        _settings = _a._settings;
        _splitScreen = _a.getSplitScreenOption();
        _rootView = (ViewGroup)getView();
        
        // See if this was called by the user tapping on a notification.
        fromLocationReminder = false;
        Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.size()>0)
        {
        	// Arguments were passed into the fragment.
        	arrivedFromNotification = false;
        	if (!fragArgs.containsKey("_id"))
            {
                Util.log("No ID passed into ViewTask.onActivityCreated()");
                _a.finish();
                return;
            }
            _id = fragArgs.getLong("_id");
            
            // Arguments are only passed to the fragment when it this is called from another 
            // fragment.
            _isOnlyFragment = false;
        }
        else
        {
        	// This must be called within a separate activity.  Check the Activity's intent for 
        	// arguments.
        	_isOnlyFragment = true;
	        Intent intent = _a.getIntent();
	        if (intent.getData()!=null && intent.getData().getScheme().equals("content"))
	        {
	        	// Everything after the final / is the task ID:
	        	String lastSegment = intent.getData().getLastPathSegment();
	        	Long taskID;
	        	try
	        	{
	        		taskID = Long.parseLong(lastSegment);
	        	}
	        	catch (NumberFormatException e)
	        	{
	        		Util.log("Got invalid task ID from outside widget: "+intent.getDataString());
	        		_a.finish();
	        		return;
	        	}
	        	_id = taskID;
	        	arrivedFromNotification = false;
	        	notificationTime = 0;
	        }
	        else if (intent.getData()!=null && intent.getData().getScheme().equals("http") &&
	        	intent.getData().getHost().equals("view.todolist.co"))
	        {
	        	Util.log("ViewTask called from calendar entry.");
	        	String taskIDString = intent.getData().getLastPathSegment();
	        	if (taskIDString!=null && Util.regularExpressionMatch(taskIDString, "^\\d+$"))
	        	{
	        		_id = Long.parseLong(taskIDString);
	        		arrivedFromNotification = false;
	        	}
	        	else
	        	{
	        		Util.log("Got invalid task ID from calendar: "+taskIDString);
	        		_a.finish();
	        		return;
	        	}
	        }
	        else if (intent.getAction()!=null && intent.getAction().equals(Intent.ACTION_VIEW))
	        {
	        	Uri uri = intent.getData();
				if (uri.getScheme().equals("viewtask"))
				{
					// Pull out the task ID from the Intent
					String taskIdString = uri.getEncodedSchemeSpecificPart();
					_id = Long.parseLong(taskIdString);
					arrivedFromNotification = true;
					Bundle b = intent.getExtras();
					notificationTime = 0;
					if (b!=null && b.containsKey("notification_time"))
						notificationTime = b.getLong("notification_time");
					if (b!=null && b.containsKey("is_location") && b.getBoolean("is_location"))
						fromLocationReminder = true;
				}
				else
				{
					Util.log("Invalid scheme '"+uri.getScheme()+"' passed to ViewTask.onCreate().");
					_a.finish();
					return;
				}
	        }
	        else
	        {
	            // Extract the parameters from the Bundle passed in:
	            Bundle extras = intent.getExtras();
	            if (extras==null)
	            {
	                Util.log("Null Bundle passed into ViewTask.onCreate()");
	                _a.finish();
	                return;
	            }
	            if (!extras.containsKey("_id"))
	            {
	                Util.log("No ID passed into ViewTask.onCreate()");
	                _a.finish();
	                return;
	            }
	            _id = extras.getLong("_id");
	            arrivedFromNotification = false;
	        }
        }
        
        Util.log("Viewing a Task with ID "+_id);
		if (_isOnlyFragment)
			initBannerAd(_rootView);

		// For certain languages under certain conditions, we need to adjust the table layout due
		// to very long field names.

		_zeroWidthFields = false;
		task = (new TasksDbAdapter()).getTask(_id);
		if (task!=null)
		{
			UTLAccount acct = (new AccountsDbAdapter()).getAccount(task.account_id);

			if (Locale.getDefault().getLanguage().equals("ru") && (_settings.getBoolean(PrefNames.
				LENGTH_ENABLED, true) || _settings.getBoolean(PrefNames.TIMER_ENABLED, true) ||
				(_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true) && acct.sync_service ==
				UTLAccount.SYNC_TOODLEDO)))
			{
				TableLayout table = (TableLayout) _rootView.findViewById(R.id.view_task_table);
				table.setStretchAllColumns(true);
				_zeroWidthFields = true;
			}

			if (Locale.getDefault().getLanguage().equals("ja") && _settings.getBoolean(PrefNames.
				COLLABORATORS_ENABLED, true) && acct.sync_service == UTLAccount.SYNC_TOODLEDO)
			{
				TableLayout table = (TableLayout) _rootView.findViewById(R.id.view_task_table);
				table.setStretchAllColumns(true);
				_zeroWidthFields = true;
			}
		}

		if (_zeroWidthFields)
		{
			// Adjust the width setting of all field names to zero, so the width can be set
			// by the system.

			int[] idList2 = new int[] {
				R.id.view_task_title1,
				R.id.view_task_parent1,
				R.id.view_task_status1,
				R.id.view_task_field_separator1,
				R.id.view_task_start_date1,
				R.id.view_task_due_date1,
				R.id.view_task_completion_date1,
				R.id.view_task_reminder_date1,
				R.id.view_task_repeat1,
				R.id.view_task_repeat_from1,
				R.id.view_task_calendar1,
				R.id.view_task_field_separator2,
				R.id.view_task_priority1,
				R.id.view_task_folder1,
				R.id.view_task_context1,
				R.id.view_task_tags1,
				R.id.view_task_goal1,
				R.id.view_task_location1,
				R.id.view_task_account1,
				R.id.view_task_field_separator3,
				R.id.view_task_expected_length1,
				R.id.view_task_actual_length1,
				R.id.view_task_field_separator4,
				R.id.view_task_contact1,
				R.id.view_task_field_separator5,
				R.id.view_task_added_by,
				R.id.view_task_is_joint,
				R.id.view_task_owner,
				R.id.view_task_shared_with,
				R.id.view_task_field_separator6
			};
			for (int i = 0; i < idList2.length; i++)
			{
				View tv = _rootView.findViewById(idList2[i]);
				LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)tv.
					getLayoutParams();
				layoutParams.width = 0;
				tv.setLayoutParams(layoutParams);
			}
		}
        
        // In portrait mode on a small screen, hide the icon to show more of the title:
        if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT && _settings.getFloat(
        	PrefNames.DIAGONAL_SCREEN_SIZE, 5.0f)<5.2f && _isOnlyFragment)
        {
        	_a.getSupportActionBar().setIcon(android.R.color.transparent);
        }

        //
        // Hide layout items that apply to disabled features:
        //
        
        if (!_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_status_row).setVisibility(View.GONE);
            _rootView.findViewById(R.id.view_task_separator1).setVisibility(View.GONE);
        }
        
        int separator2NumItems = 6;
        if (!_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_start_date_row).setVisibility(View.GONE);
            separator2NumItems--;
        }
        if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_due_date_row).setVisibility(View.GONE);
            separator2NumItems--;
        }
        if (!_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_reminder_row).setVisibility(View.GONE);
            separator2NumItems--;
        }
        if (!_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_repeat_row).setVisibility(View.GONE);
            _rootView.findViewById(R.id.view_task_repeat_from_row).setVisibility(View.GONE);
            separator2NumItems = separator2NumItems-2;
        }
        if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        {
        	_rootView.findViewById(R.id.view_task_calendar_row).setVisibility(View.GONE);
        	separator2NumItems--;
        }
        if (separator2NumItems==0)
        {
            // No need to display the separator due to disabled fields:
            _rootView.findViewById(R.id.view_task_separator2).setVisibility(View.GONE);
        }
        
        if (!_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_priority_row).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_folder_row).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_context_row).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_tags_row).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_goal_row).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
            _rootView.findViewById(R.id.view_task_location_row).setVisibility(View.GONE);
        
        int separator4NumItems = 2;
        if (!_settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_expected_length_row).setVisibility(View.GONE);
            separator4NumItems--;
        }
        if (!_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
        {
            _rootView.findViewById(R.id.view_task_actual_length_row).setVisibility(View.GONE);
            separator4NumItems--;
        }
        if (separator4NumItems==0)
        {
            // No need to display the separator due to disabled fields:
            _rootView.findViewById(R.id.view_task_separator4).setVisibility(View.GONE);
        }
                
        // Show or hide contacts fields:
        if (!_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.view_task_contact_row).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.view_task_separator5).setVisibility(View.GONE);
        }
        
        // Show or hide collaboration fields:
        if (!_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.view_task_is_joint_row).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.view_task_added_by_row).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.view_task_shared_with_row).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.view_task_owner_row).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.view_task_separator6).setVisibility(View.GONE);
        }
        if (!arrivedFromNotification)
        {
        	// This is not being displayed due to the user tapping on a notification.
        	// remove the buttons that shouldn't be displayed:
        	_rootView.findViewById(R.id.view_task_bottom_buttons_container).setVisibility(View.GONE);
        }
        
        //
        // Display the Actual Task Data
        //
        
        refreshDisplay();
        if (task==null)
        	return;
        
        if (_isOnlyFragment)
        {
        	// This fragment will be updating the Action Bar:
        	setHasOptionsMenu(true);
        }
        
        repeatingTasksGenerated = false;

        //
        // Button Handlers:
        //
                
        // Stop Nagging button:
        Button stopNagging = (Button)_rootView.findViewById(R.id.view_task_stop_nagging_button);
        stopNagging.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Cancel any future reminder notifications:
				Util.cancelReminderNotification(task._id);
				
        		// If this is a nonrepeating task, we can safely update the database and
        		// remove the nag setting.  For a repeating task, we cannot because we 
        		// want the repeat to carry on to the next task.
        		if (task.repeat==0)
        		{
        			if (fromLocationReminder)
        				task.location_nag = false;
        			else
        				task.nag = false;
        			TasksDbAdapter tasksDB = new TasksDbAdapter();
        			if (!tasksDB.modifyTask(task))
        			{
        				Util.log("Could not remove nag setting from a task in ViewTask.java.");
        			}
        		}

        		_a.finish();				
			}
		});
        
        // Mark Complete Button (for nagging task):
        Button markComplete = (Button)_rootView.findViewById(R.id.view_task_mark_complete_button);
        markComplete.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (!task.completed)
				{
					Util.markTaskComplete(task._id);
					instantUpload();
				}
				
				// Cancel any reminder notifications (the markTaskComplete() method only 
				// cancels the reminder if the reminder time is in the future.  For a 
				// nagging task, this may not be the case):
				Util.cancelReminderNotification(task._id);
				
				_a.finish();				
			}
		});
        
        // Dismiss Button Handler:
        Button dismissButton = (Button)_rootView.findViewById(R.id.view_task_dismiss_button);
        dismissButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Remove the notification:
				Util.removeTaskNotification(task._id);
				_a.finish();
			}
		});
        
        // Mark Complete Button (for non-nagging task):
        markComplete = (Button)_rootView.findViewById(R.id.view_task_mark_complete_button2);
        markComplete.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (!task.completed)
				{
					Util.markTaskComplete(task._id);
					instantUpload();
				}
				_a.finish();				
			}
		});
        
        // A click listener for the snooze buttons:
        snoozeListener = new DialogInterface.OnClickListener()
		{					
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				// Get the number of minutes chosen:
				int snoozeMinutes[] = _res.getIntArray(
					R.array.snooze_minutes);
				int minutesToSnooze = snoozeMinutes[which];
				
				// Clear the current notification:
				Util.removeTaskNotification(task._id);
				
				// Update the reminder time for the task:
				task.reminder = System.currentTimeMillis() + minutesToSnooze*
					60000;
				
				// Set a new reminder notification based on the snooze time:
				if (fromLocationReminder)
					Util.snoozeLocationReminder(task, task.reminder);
				else
					Util.scheduleReminderNotification(task,task.reminder);
				
				// Note that we do not update the reminder time in the DB.
				// This could cause issues due to limits on valid reminder times.
				// (Such as the reminder needing to be before the due date/time.)
				
				dialog.dismiss();
				
				// Close this Activity:
				_a.finish();
			}
		};
		
        // Snooze button (non-nagging task):
        Button snooze = (Button)_rootView.findViewById(R.id.view_task_snooze_button);
        snooze.setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				// Create a dialog with the snooze options:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				String snoozeStrings[] = _res.getStringArray(
					R.array.snooze_strings);
				builder.setItems(snoozeStrings, snoozeListener);
				AlertDialog d = builder.create();
                d.setTitle(Util.getString(R.string.Remind_me_in_));
                d.show();
			}
		});
        
        // Snooze button (nagging task):
        snooze = (Button)_rootView.findViewById(R.id.view_task_snooze_nag_button);
        snooze.setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				// Create a dialog with the snooze options:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				String snoozeStrings[] = _res.getStringArray(
					R.array.snooze_strings);
				builder.setItems(snoozeStrings, snoozeListener);
				AlertDialog d = builder.create();
                d.setTitle(Util.getString(R.string.Remind_me_in_));
                d.show();
			}
		});
        
        // Open contact button:
        if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
        {
        	TextView openContact = (TextView)_rootView.findViewById(R.id.view_task_contact2);
        	openContact.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					if (inResizeMode()) return;
					
					TextView tv = (TextView)_rootView.findViewById(R.id.view_task_contact2);
					if (task.contactLookupKey!=null && task.contactLookupKey.length()>0 &&
						!tv.getText().toString().equals(_a.getString(R.string.Missing_Contact)))
					{
						Intent i = new Intent(Intent.ACTION_VIEW);
						Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.
							CONTENT_LOOKUP_URI,task.contactLookupKey);
						i.setData(uri);
						try
						{
							_a.startActivity(i);
						}
						catch (ActivityNotFoundException e)
						{
							Util.popup(_a, R.string.Not_Supported_By_Device);
						}
					}
				}
			});
        }
        
        //
        // Command Bar Setup:
        //
        
        CommandBar cb = new CommandBar(_a,(ViewGroup)getView());
        if (_isOnlyFragment)
		{
			cb.setBackground(Util.resourceIdFromAttr(_a,R.attr.cb_background_fullscreen),true);
		}
        else
		{
			cb.setBackground(Util.resourceIdFromAttr(_a,R.attr.cb_background_splitscreen),false);
		}
                
        // Add Task Button:
        cb.addButton(ADD_TASK_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.ab_add :
			R.attr.ab_add_inv), R.string.New_Task, new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				if (inResizeMode()) return;
				
				// Create a dialog with the 2 choices (new regular task, or new subtask):
		    	AlertDialog.Builder builder = new AlertDialog.Builder(_a);
		    	String[] choices = { 
		    		Util.getString(R.string.Add_new_regular_task),
		    		Util.getString(R.string.Add_subtask_of_this_one)
		    	};
		    	builder.setItems(choices, new DialogInterface.OnClickListener()
				{			
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						Intent i = new Intent(_a, EditTask.class);
			            i.putExtra("action", EditTaskFragment.ADD);
			            String tag = "/add";
						if (which==1)
						{
							i.putExtra("parent_id", _id);
							tag = "/add_sub/"+_id;
						}
						if (_isOnlyFragment)
							_a.startActivity(i);
						else
						{
							UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
							EditTaskFragment frag = new EditTaskFragment();
							frag.setArguments(i.getExtras());
							nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.
								FRAG_TAG + tag);
						}
			        	dialog.dismiss();
					}
				});
		    	builder.setTitle(R.string.Select_One_);
		    	
		    	// The dialog only needs to be shown if subtasks are enabled and it is 
		    	// possible to add a subtask from here:
		    	UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
	        	if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true) && a!=null)
	        	{
					if (a.sync_service==UTLAccount.SYNC_NONE ||
						(a.sync_service==UTLAccount.SYNC_TOODLEDO && task.parent_id==0) ||
						(a.sync_service==UTLAccount.SYNC_GOOGLE && task.parent_id==0) ||
						(a.sync_service==UTLAccount.SYNC_GOOGLE && task.parent_id>0 &&
							System.currentTimeMillis()<Util.GOOGLE_SUB_SUB_TASKS_EXPIRY))
					{
						builder.show();
						return;
					}
	        	}

				// We can't add a subtask to a subtask, so just add a regular task:
				Intent i = new Intent(_a, EditTask.class);
				i.putExtra("action", EditTaskFragment.ADD);
				if (_isOnlyFragment)
					_a.startActivity(i);
				else
				{
					UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
					EditTaskFragment frag = new EditTaskFragment();
					frag.setArguments(i.getExtras());
					nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.
						FRAG_TAG + "/add");
				}
		    }				
    	});
        
        // Clone Task:
        cb.addButton(CLONE_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.ab_clone :
			R.attr.ab_clone_inv), R.string.Clone_Task, new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				if (inResizeMode()) return;
				
				Intent i = new Intent(_a,EditTask.class);
				i.putExtra("action", EditTaskFragment.ADD);
				i.putExtra("clone_id", _id);
				if (_isOnlyFragment)
					_a.startActivity(i);
				else
				{
					UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
					EditTaskFragment frag = new EditTaskFragment();
					frag.setArguments(i.getExtras());
					nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.
						FRAG_TAG + "/clone/"+_id);
				}
			}
    	});
        
        // Delete Task:
        cb.addButton(DELETE_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.ab_delete :
			R.attr.ab_delete_inv), R.string.Delete_Task, new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				if (inResizeMode()) return;
				
	        	  // Button handlers for confirmation dialog:
	        	  DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
	        	  	  OnClickListener()
				  {				
					  @Override
					  public void onClick(DialogInterface dialog, int which)
					  {
						  if (which == DialogInterface.BUTTON_POSITIVE)
						  {
							  // Yes clicked:
							  Util.deleteTask(_id);
							  if (_isOnlyFragment)
								  _a.finish();
							  else
							  {
								  // This must be wrapped in a UtlNavDrawerActivity.  Remove this
								  // fragment and display a message saying the user can tap on 
								  // another task to see its info.
								  UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
								  TaskListFragment listFrag = (TaskListFragment)nav.getFragmentByType(
									  UtlNavDrawerActivity.FRAG_LIST);
								  if (listFrag!=null)
									  listFrag.handleDeletion();
							  }
		                  }					
					  }
				  };
	              
	              // Display the confirmation dialog:
				  AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				  builder.setMessage(R.string.Task_Delete_Confirmation);
				  builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
		          builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
		          TasksDbAdapter db = new TasksDbAdapter();
	              UTLTask t = db.getTask(_id);
	              if (t==null)
	              {
	                  // This could happen if the task was deleted from Toodledo.
	                  Util.popup(_a, R.string.Task_no_longer_exists);
	              }
	              else
	              {
	            	  builder.setTitle(t.title);
	            	  builder.show();
	              }
			}
    	});
        
        // Reassign (if supported by the account):
        UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true) &&
        	a.sync_service == UTLAccount.SYNC_TOODLEDO)
        {
        	cb.addButton(REASSIGN_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.ab_reassign :
				R.attr.ab_reassign_inv), R.string.Reassign, new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					if (inResizeMode()) return;
					
		      		// Make sure the task can be reassigned.  Shared tasks cannot be reassigned.  
		      		if (task.is_joint)
		      		{
		      			Util.longerPopup(_a, null, Util.getString(R.string.shared_cannot_be_reassigned));
		      			return;
		      		}
		      		
		      		// Create a list of collaborators to choose from:
		      		ArrayList<String> nameList = new ArrayList<String>();
		      		_collIdList = new ArrayList<String>();
		      		UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
		      		CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
		      		Cursor c = cdb.queryCollaborators("account_id="+a._id+" and reassignable=1 and remote_id!='"+
		    			Util.makeSafeForDatabase(a.td_userid)+"'", "name asc");
		      		while (c.moveToNext())
		      		{
		      			UTLCollaborator coll = cdb.cursorToCollaborator(c);
		      			nameList.add(coll.name);
		      			_collIdList.add(coll.remote_id);
		      		}
		      		c.close();
		      		if (nameList.size()==0)
		      		{
		      			Util.longerPopup(_a, null, Util.getString(R.string.no_collaborators2));
		      			return;
		      		}
		      		
		      		// Create a dialog with the choices:
		      		AlertDialog.Builder builder = new AlertDialog.Builder(_a);
		      		String[] nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.size());
		      		builder.setItems(nameArray, new DialogInterface.OnClickListener()
		  			{				
		      			@Override
		      			public void onClick(DialogInterface dialog, int which)
		      			{
		      				// Dismiss the dialog:
		      				dialog.dismiss();
		      				
		      				// Add an entry to the pending reassignments table:
		      				PendingReassignmentsDbAdapter pr = new PendingReassignmentsDbAdapter();
		      				long rowID = pr.addReassignment(task._id, _collIdList.get(which));
		      				
		      				if (rowID>-1)
		      				{
			      				// Tell synchronizer to upload the change:
			      				Intent i = new Intent(_a,Synchronizer.class);
			      				i.putExtra("command", "sync_item");
			      				i.putExtra("item_type", Synchronizer.TYPE_REASSIGN);
			      				i.putExtra("item_id", rowID);
			      				i.putExtra("account_id",task.account_id);
			      				i.putExtra("operation",Synchronizer.REASSIGN);
								Synchronizer.enqueueWork(_a,i);
			      				
			      				// Display a brief message saying the task will be removed after TD has 
			      				// received the reassignment:
			      				Util.popup(_a, R.string.reassign_wait);

                                if (!task.completed) (new FeatureUsage(_a)).record(FeatureUsage.TOODLEDO_COLLABORATION);
			      				
			      				// Treat this like a task deletion.  Even though the task isn't deleted
			      				// yet, it makes no sense to keep this pane or activity open.
			      				if (_isOnlyFragment)
									  _a.finish();
						    	else
								{
									// This must be wrapped in a UtlNavDrawerActivity.  Remove this
									// fragment and display a message saying the user can tap on 
									// another task to see its info.
									UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
									TaskListFragment listFrag = (TaskListFragment)nav.getFragmentByType(
										UtlNavDrawerActivity.FRAG_LIST);
									if (listFrag!=null)
										listFrag.handleDeletion();
								}
		      				}
		      				
		      			}
		  			});
		      		builder.setTitle(Util.getString(R.string.assign_to));
		      	    builder.show();
				}
			});
        }
        
        // Determine which subtasks options can be shown:
        boolean canViewSubtasks = false;
        boolean canViewParent = false;
        if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true))
        {
        	if (a!=null && (a.sync_service!=UTLAccount.SYNC_TOODLEDO || (a.sync_service==
        		UTLAccount.SYNC_TOODLEDO && task.parent_id==0)))
        	{
        		// Next, check to see if there are actually any subtasks to view.
        		TasksDbAdapter tasksDB = new TasksDbAdapter();
        		Cursor c;
        		if (_settings.getBoolean(PrefNames.HIDE_COMPLETED_SUBTASKS, false))
        		{
        			c = tasksDB.queryTasks("parent_id="+task._id+" and completed=0", null);
        		}
        		else
        		{
        			c = tasksDB.queryTasks("parent_id="+task._id, null);
        		}
        		if (c.getCount()>0)
        			canViewSubtasks = true;
        		c.close();
        	}
        }
        if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true) && task.parent_id>0)
        {
        	canViewParent = true;
        }
    		
        // View Subtasks:
        if (canViewSubtasks)
        {
        	cb.addButton(VIEW_SUBTASKS_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.
				ab_view_subtasks : R.attr.ab_view_subtasks_inv), R.string.View_Subtasks,
				new View.OnClickListener()
			{			
				@Override
				public void onClick(View v)
				{
					if (inResizeMode()) return;
					
		        	  // Create a temporary view for the subtasks:
		          	  ViewsDbAdapter viewsDB = new ViewsDbAdapter();
		          	  String viewName = Long.valueOf(System.currentTimeMillis()).toString();
		          	  DisplayOptions dispOpt = DisplayOptions.getDefaultDisplayOptions(
		          		  "all_tasks");
		          	  dispOpt.subtaskOption = "flattened";
		          	  long tempViewID = viewsDB.addView("subtask", viewName, 
		          		  viewsDB.getDefaultSortOrder("all_tasks"), dispOpt);
		          	  if (tempViewID==-1)
		          	  {
		          		  Util.popup(_a, R.string.DbInsertFailed);
		          		  return;
		          	  }
		          	  
		          	  // Add a rule to the view:
		          	  int intTaskID = Integer.parseInt(new Long(_id).toString());
		          	  MultChoiceViewRule rule = new MultChoiceViewRule("parent_id",new int[] {
		          		  intTaskID });
		          	  long rowID = (new ViewRulesDbAdapter()).addRule(tempViewID,2,rule,0,false);
		          	  if (rowID==-1)
		          	  {
		          		  Util.popup(_a, R.string.DbInsertFailed);
		          		  return;
		          	  }
		          	  if (_settings.getBoolean(PrefNames.HIDE_COMPLETED_SUBTASKS, false))
		        	  {
		        		  // We're hiding completed subtasks. This requires another rule.
		        		  BooleanViewRule br = new BooleanViewRule("completed",false);
		        		  rowID = (new ViewRulesDbAdapter()).addRule(tempViewID, 0, br, 1, false);
		        		  if (rowID==-1)
		            	  {
		            		  Util.popup(_a, R.string.DbInsertFailed);
		            		  return;
		            	  }
		        	  }
		          	  
		          	  // Explain to the user what is happening:
		          	  Util.popup(_a, R.string.Viewing_subtasks);
		          	  
		          	  String newTitle = _a.getString(R.string.Subtasks_of_Task)+" \""+
		          		  task.title+"\"";
		          	  if (_isOnlyFragment)
		          	  {
			          	  // Launch a new TaskList activity that shows the subtasks:
			          	  Intent i = new Intent(_a,TaskList.class);
			          	  i.putExtra("top_level", ViewNames.SUBTASK);
			          	  i.putExtra("view_name", viewName);
			          	  i.putExtra("title", newTitle);
			          	  startActivity(i);		
		          	  }
		          	  else
		          	  {
		          		  // The list fragment needs to show a different set of tasks:
		          		  TaskList tl = (TaskList)_a;
		          		  TaskListFragment frag = new TaskListFragment();
						  Bundle b = new Bundle();
						  b.putString("title", newTitle);
						  b.putString("top_level", ViewNames.SUBTASK);
						  b.putString("view_name", viewName);
						  frag.setArguments(b);
						  tl.changeTaskList(frag, ViewNames.SUBTASK, viewName);
						  tl.closeDrawer();
		          	  }
				}
	    	});
        }
        
        // View Parent (if task has a parent):
        if (canViewParent)
        {
        	cb.addButton(VIEW_PARENT_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.
				ab_view_parent : R.attr.ab_view_parent_inv), R.string.View_Parent_Task, new View.OnClickListener()
    		{			
    			@Override
    			public void onClick(View v)
    			{
    				if (inResizeMode()) return;
    				
		          	  // Explain to the user what is happening:
		          	  Util.popup(_a, R.string.Viewing_parent);
		          	  
		          	  if (_isOnlyFragment)
		          	  {
	    	        	  Intent i = new Intent(_a,ViewTask.class);
	    	        	  i.putExtra("_id", task.parent_id);
	    	        	  startActivity(i);
		          	  }
		          	  else
		          	  {
		          		  // Place the new detail pane fragment showing the parent task:
		          		  ViewTaskFragment vt = new ViewTaskFragment();
		          		  Bundle b = new Bundle();
		          		  b.putLong("_id", task.parent_id);
		          		  vt.setArguments(b);
		          		  UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
		          		  nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, vt, 
		          			  ViewTaskFragment.FRAG_TAG + "/" + task.parent_id);
		          	  }
    			}
        	});
        }
        
        // Map and Navigation, if the task has a location:
        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true) && task.location_id>0)
        {
        	cb.addButton(MAP_ID, _a.resourceIdFromAttr(_isOnlyFragment? R.attr.ab_show_map :
				R.attr.ab_show_map_inv), R.string.Show_Map, new View.OnClickListener()
        	{		
				@Override
				public void onClick(View v)
				{
					if (inResizeMode()) return;
					
					UTLLocation loc = (new LocationsDbAdapter()).getLocation(task.location_id);
		    		String coords = Double.valueOf(loc.lat).toString()+","+
		    			Double.valueOf(loc.lon).toString();
		    		Intent i = new Intent(Intent.ACTION_VIEW,
						Uri.parse("geo:"+coords+"?q="+coords));
					try
					{
						_a.startActivity(i);
					}
					catch (ActivityNotFoundException e)
					{
						Util.popup(_a, R.string.Maps_Not_Installed);
					}
					return;
				}
			});
        	
        	cb.addButton(NAV_ID, _a.resourceIdFromAttr(_isOnlyFragment ? R.attr.ab_navigate :
				R.attr.ab_navigate_inv), R.string.Navigate, new View.OnClickListener()
        	{		
				@Override
				public void onClick(View v)
				{
					if (inResizeMode()) return;
					
					UTLLocation loc = (new LocationsDbAdapter()).getLocation(task.location_id);
					Intent i = new Intent(Intent.ACTION_VIEW,
						Uri.parse("google.navigation:q="+Double.valueOf(loc.lat).toString()+","+
						Double.valueOf(loc.lon).toString()));
					try
					{
						_a.startActivity(i);
					}
					catch (ActivityNotFoundException e)
					{
						Util.popup(_a, R.string.Navigation_Not_Installed);
					}
					return;
				}
			});
        }        
    }

    // Refresh the display:
    public void refreshDisplay()
    {
        // Look up the task in the database:
        task = (new TasksDbAdapter()).getTask(_id);
        if (task==null)
        {
            Util.log("Could not find task ID "+_id+" in the database.");
            if (_isOnlyFragment)
            	_a.finish();
            else
            {
            	// Just quietly exit this fragment and show nothing.
            	UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
            	nav.showDetailPaneMessage("");
            	refreshTaskList();
            }
            return;
        }
        
        // A general pointer to point to the various TextView objects:
        TextView tv;
        
        // Title:
        if (_isOnlyFragment)
        	_a.getSupportActionBar().setTitle(task.title);
        tv = (TextView)_rootView.findViewById(R.id.view_task_title2);
        tv.setText(task.title);

        // Parent task (if any):
        if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true) && task.parent_id>0)
        {
        	_rootView.findViewById(R.id.view_task_parent_row).setVisibility(View.VISIBLE);
        	tv = (TextView)_rootView.findViewById(R.id.view_task_parent2);
        	UTLTask parent = (new TasksDbAdapter()).getTask(task.parent_id);
        	if (parent!=null)
        	{
        		tv.setText(parent.title);
        	}
        }
        else
        {
        	_rootView.findViewById(R.id.view_task_parent_row).setVisibility(View.GONE);
        }
        
        // Status:
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_status2);
            String[] statuses = this.getResources().getStringArray(R.array.statuses);
            tv.setText(statuses[task.status]);
        }
        
        // Start Date/Time:
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            if (_settings.getBoolean(PrefNames.START_TIME_ENABLED, true) && task.uses_start_time)
            {
                tv = (TextView)_rootView.findViewById(R.id.view_task_start_date2);
                if (task.start_date==0)
                {
                    // No start date.  Display "none"
                    tv.setText(Util.getString(R.string.None));
                }      
                else
                {
                    tv.setText(Util.getDateString(task.start_date)+" "+
                        Util.getTimeString(task.start_date));
                }
            }
            else
            {
                // Change the header from "Start Date/Time" to "Start Date".
                tv = (TextView)_rootView.findViewById(R.id.view_task_start_date1);
                tv.setText(Util.getString(R.string.Start_Date_));
                
                tv = (TextView)_rootView.findViewById(R.id.view_task_start_date2);
                if (task.start_date==0)
                {
                    // No start date.  Display "none"
                    tv.setText(Util.getString(R.string.None));
                }
                else
                {
                    tv.setText(Util.getDateString(task.start_date));
                }
            }
        }

        // Due Date/Time:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            if (_settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true) && task.uses_due_time)
            {
                tv = (TextView)_rootView.findViewById(R.id.view_task_due_date2);
                if (task.due_date==0)
                {
                    // No due date.  Display "none"
                    tv.setText(Util.getString(R.string.None));
                }      
                else
                {
                    tv.setText(Util.getDateString(task.due_date)+" "+
                        Util.getTimeString(task.due_date));
                    if (task.due_modifier.equals("due_on"))
                    	tv.setText(tv.getText()+" "+Util.getString(R.string._exact_date_));
                    if (task.due_modifier.equals("optionally_on"))
                    	tv.setText(tv.getText()+" "+Util.getString(R.string._optional_));
                }
            }
            else
            {
                // Change the header from "Due Date/Time" to "Due Date".
                tv = (TextView)_rootView.findViewById(R.id.view_task_due_date1);
                tv.setText(Util.getString(R.string.Due_Date_));

                tv = (TextView)_rootView.findViewById(R.id.view_task_due_date2);
                if (task.due_date==0)
                {
                    // No due date.  Display "none"
                    tv.setText(Util.getString(R.string.None));
                }
                else
                {
                    tv.setText(Util.getDateString(task.due_date));
                    if (task.due_modifier.equals("due_on"))
                    	tv.setText(tv.getText()+" "+Util.getString(R.string._exact_date_));
                    if (task.due_modifier.equals("optionally_on"))
                    	tv.setText(tv.getText()+" "+Util.getString(R.string._optional_));
                }
            }
        }

        // Completed Date/Time:
        if (task.completed)
        {
            _rootView.findViewById(R.id.view_task_completion_date_row).setVisibility(View.VISIBLE);
            tv = (TextView)_rootView.findViewById(R.id.view_task_completion_date2);
            if (task.completion_date==0)
            {
                // Shouldn't happen, but we'll handle it anyway.
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                tv.setText(Util.getDateString(task.completion_date));
            }
        }
        else
        {
            _rootView.findViewById(R.id.view_task_completion_date_row).setVisibility(View.GONE);
        }
        
        // Reminder Date/Time:
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_reminder_date2);
            if (task.reminder==0)
            {
                // No reminder date.  Display "none"
                tv.setText(Util.getString(R.string.None));
            }      
            else
            {
                tv.setText(Util.getDateString(task.reminder)+" "+
                    Util.getTimeString(task.reminder));
                if (task.nag)
                {
                    tv.setText(tv.getText() + " ("+Util.getString(R.string.Nagging_On)+")");
                }
            }
        }
        
        // Repeat:
        if (_settings.getBoolean(PrefNames.REPEAT_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_repeat2);
            if (task.repeat==0)
            {
                // Hide the "repeat from" line:
                _rootView.findViewById(R.id.view_task_repeat_from1).setVisibility(View.GONE);
                _rootView.findViewById(R.id.view_task_repeat_from2).setVisibility(View.GONE);
            }
            else
            {
                // Show the "repeat from" line:
            	_rootView.findViewById(R.id.view_task_repeat_from1).setVisibility(View.VISIBLE);
            	_rootView.findViewById(R.id.view_task_repeat_from2).setVisibility(View.VISIBLE);
            }
            if (task.repeat<50)
            {
                // Ordinary repeat setting:
                String repeatArray[] = this.getResources().getStringArray(
                    R.array.repeat_options);
                tv.setText(repeatArray[task.repeat]);
                tv = (TextView)_rootView.findViewById(R.id.view_task_repeat_from2);
                tv.setText(Util.getString(R.string.Due_Date));
            }
            else if (task.repeat==50)
            {
                // Advanced repeat:
            	AdvancedRepeat ar = new AdvancedRepeat();
            	if (ar.initFromString(task.rep_advanced))
            		tv.setText(ar.getLocalizedString(_a));
            	else
            		tv.setText(R.string.None);
                tv = (TextView)_rootView.findViewById(R.id.view_task_repeat_from2);
                tv.setText(Util.getString(R.string.Due_Date));
            }
            else if (task.repeat<150)
            {
                // Ordinary repeat, from completion date:
                String repeatArray[] = this.getResources().getStringArray(
                    R.array.repeat_options);
                tv.setText(repeatArray[task.repeat-100]);
                tv = (TextView)_rootView.findViewById(R.id.view_task_repeat_from2);
                tv.setText(Util.getString(R.string.CompletionDate));
            }
            else
            {
                // Advanced repeat, from completion date:
            	AdvancedRepeat ar = new AdvancedRepeat();
            	if (ar.initFromString(task.rep_advanced))
            		tv.setText(ar.getLocalizedString(_a));
            	else
            		tv.setText(R.string.None);
                tv = (TextView)_rootView.findViewById(R.id.view_task_repeat_from2);
                tv.setText(Util.getString(R.string.CompletionDate));
            }
        }
        
        // Calendar:
        if (_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        {
        	tv = (TextView)_rootView.findViewById(R.id.view_task_calendar2);
        	if (task.calEventUri!=null && task.calEventUri.length()>0)
        		tv.setText(R.string.Yes);
        	else
        		tv.setText(R.string.No);
        }
        
        // Priority:
        if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_priority2);
            String priorityArray[] = this.getResources().getStringArray(R.array.priorities);
            tv.setText(priorityArray[task.priority]);
        }
        
        // Folder:
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_folder2);
            if (task.folder_id==0)
            {
                // No folder:
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                // Fetch the folder name from the DB and display it:
                Cursor c = (new FoldersDbAdapter()).getFolder(task.folder_id);
                if (!c.moveToFirst())
                {
                    // Very strange.  Task is pointing to an undefined folder.  Set
                    // the text blank.
                    Util.log("Task ID "+_id+" is pointing to an undefined folder with ID "+
                        task.folder_id);
                    tv.setText("");
                }
                else
                {
                    tv.setText(Util.cString(c, "title"));
                }
                c.close();
            }
        }

        // Context:
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_context2);
            if (task.context_id==0)
            {
                // No context:
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                // Fetch the context name from the DB and display it:
                Cursor c = (new ContextsDbAdapter()).getContext(task.context_id);
                if (!c.moveToFirst())
                {
                    // Very strange.  Task is pointing to an undefined context.  Set
                    // the text blank.
                    Util.log("Task ID "+_id+" is pointing to an undefined context with ID "+
                        task.context_id);
                    tv.setText("");
                }
                else
                {
                    tv.setText(Util.cString(c, "title"));
                }
                c.close();
            }
        }
        
        // Tags:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_tags2);
            
            // Query the DB for any tags associated with this task, and display:
            String tags[] = (new TagsDbAdapter()).getTagsInDbOrder(_id);
            if (tags.length==0)
            {
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                String result = tags[0];
                for (int i=1; i<tags.length; i++)
                {
                    result += ", "+tags[i];
                }
                tv.setText(result);
            }
        }
        
        // Goal:
        if (_settings.getBoolean(PrefNames.GOALS_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_goal2);
            if (task.goal_id==0)
            {
                // No goal:
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                // Fetch the goal name from the DB and display it:
                Cursor c = (new GoalsDbAdapter()).getGoal(task.goal_id);
                if (!c.moveToFirst())
                {
                    // Very strange.  Task is pointing to an undefined goal.  Set
                    // the text blank.
                    Util.log("Task ID "+_id+" is pointing to an undefined goal with ID "+
                        task.goal_id);
                    tv.setText("");
                }
                else
                {
                    tv.setText(Util.cString(c, "title"));
                }
                c.close();
            }
        }
        
        // Location:
        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_location2);
            if (task.location_id==0)
            {
                // No goal:
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                // Fetch the location name from the DB and display it:
                UTLLocation loc = (new LocationsDbAdapter()).getLocation(task.location_id);
                if (loc==null)
                {
                    // Very strange.  Task is pointing to an undefined location.  Set
                    // the text blank.
                    Util.log("Task ID "+_id+" is pointing to an undefined goal with ID "+
                        task.goal_id);
                    tv.setText("");
                }
                else
                {
                	String text = loc.title;
                	if (task.location_reminder && task.location_nag)
                		text += " "+Util.getString(R.string.Nagging_Reminder_Enabled);
                	else if (task.location_reminder)
                		text += " "+Util.getString(R.string.Reminder_Enabled);
                    tv.setText(text);
                }
            }
        }
        
        // Account:
        tv = (TextView)_rootView.findViewById(R.id.view_task_account2);
        UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
        if (a==null)
        {
            // Very strange, the task points to a nonexistent account.
            tv.setText("");
        }
        else
        {
            tv.setText(a.name);
        }
        
        // Expected Length:
        if (_settings.getBoolean(PrefNames.LENGTH_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_expected_length2);
            if (task.length==0)
            {
                tv.setText(Util.getString(R.string.None));
            }
            else
            {
                tv.setText(getHourString(task.length));
            }
        }
        
        // Actual Length / Timer:
        if (_settings.getBoolean(PrefNames.TIMER_ENABLED,true))
        {
            tv = (TextView)_rootView.findViewById(R.id.view_task_actual_length2);
            String result;
            if (task.timer>0)
            {
                long minutes = Math.round(task.timer/60.0);
                result = getHourString(minutes);
            }
            else
            {
                result = Util.getString(R.string.None);
            }
            if (task.timer_start_time>0)
            {
                // Note that the timer is running:
                result += " ("+Util.getString(R.string.Timer_is_Running)+")";
            }
            tv.setText(result);
        }
        
        // Contact:
        if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
        {
        	tv = (TextView)_rootView.findViewById(R.id.view_task_contact2);
        	if (task.contactLookupKey!=null && task.contactLookupKey.length()>0)
        	{
        		Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
            		CONTENT_LOOKUP_URI,task.contactLookupKey);
            	Cursor c = _a.managedQuery(contactUri,null,null,null,null);
            	if (c!=null && c.moveToFirst())
            	{
            		tv.setText(Html.fromHtml("<u>"+Util.cString(c,ContactsContract.
            			Contacts.DISPLAY_NAME)+"</u>"));
            		try
            		{
            			tv.setTextColor(_a.getResources().getColor(_a.resourceIdFromAttr(
            				android.R.attr.textColorLink)));
            		}
            		catch (Resources.NotFoundException e)
            		{
            			// Do nothing.  This can happen only on old Android 2.x devices.
            		}
            	}
            	else
            	{
            		tv.setText(R.string.Missing_Contact);
            		tv.setTextColor(_a.getResources().getColor(_a.resourceIdFromAttr(
            			R.attr.utl_text_color)));
            	}
        	}
        	else
        	{
        		tv.setText(R.string.None);
        		tv.setTextColor(_a.getResources().getColor(_a.resourceIdFromAttr(
        			R.attr.utl_text_color)));
        	}
        }
        
        // Collaboration:
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        {
        	if (a.sync_service==UTLAccount.SYNC_GOOGLE || a.sync_service==UTLAccount.SYNC_NONE)
        	{
        		// No collaboration is possible.  Hide the fields:
            	_rootView.findViewById(R.id.view_task_is_joint_row).setVisibility(View.GONE);
            	_rootView.findViewById(R.id.view_task_added_by_row).setVisibility(View.GONE);
            	_rootView.findViewById(R.id.view_task_shared_with_row).setVisibility(View.GONE);
            	_rootView.findViewById(R.id.view_task_owner_row).setVisibility(View.GONE);
            	_rootView.findViewById(R.id.view_task_separator6).setVisibility(View.GONE);
        	}
        	else
        	{
        		// Is Joint?
        		tv = (TextView)_rootView.findViewById(R.id.view_task_is_joint2);
        		tv.setText(task.is_joint ? Util.getString(R.string.Yes) : Util.getString(R.string.No));
        		
        		// Owner:
        		tv = (TextView)_rootView.findViewById(R.id.view_task_owner2);
        		CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        		UTLCollaborator co = cdb.getCollaborator(a._id, task.owner_remote_id);
        		if (co!=null)
        			tv.setText(co.name);
        		else
        		{
        			if (task.owner_remote_id.equals(a.td_userid))
        				tv.setText(Util.getString(R.string.Myself));
        			else
        				tv.setText("");
        		}

        		// Added By:
        		tv = (TextView)_rootView.findViewById(R.id.view_task_added_by2);
        		co = cdb.getCollaborator(a._id, task.added_by);
        		if (co!=null)
        			tv.setText(co.name);
        		else
        		{
        			if (task.added_by.equals(a.td_userid))
        				tv.setText(Util.getString(R.string.Myself));
        			else
        				tv.setText("");
        		}
        		
        		// Shared With:
        		tv = (TextView)_rootView.findViewById(R.id.view_task_shared_with2);
        		String idArray[] = task.shared_with.split("\n");
        		String sharedStr = "";
        		for (int i=0; i<idArray.length; i++)
        		{
        			if (i>0)
        				sharedStr += ", ";
        			co = cdb.getCollaborator(a._id, idArray[i]);
        			if (co!=null)
        				sharedStr += co.name;
        			else
        			{
        				if (idArray[i].equals(a.td_userid))
        					sharedStr += Util.getString(R.string.Myself);
        			}
        		}
        		tv.setText(sharedStr);
        	}
        }
        
        // Note:
        tv = (TextView)_rootView.findViewById(R.id.view_task_note2);
        if (task.note.length()>0)
        {
            tv.setText(task.note);
            Linkify.addLinks(tv,Linkify.ALL);
        }    
        else
        {
            tv.setText("");
        }
                        
        // If we arrived here by the user tapping on a notification, we need to display
        // some buttons and hide others:
        if (arrivedFromNotification && !task.completed)
        {
        	// If the current time zone is different than the home time zone, the
        	// reminder time needs to be offset when comparing it to the current time.  
        	TimeZone currentTimeZone = TimeZone.getDefault();
        	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
        		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
        	long reminderTime = task.reminder;
        	if (!currentTimeZone.equals(defaultTimeZone))
        	{
        		long difference = currentTimeZone.getOffset(System.currentTimeMillis()) - 
        			defaultTimeZone.getOffset(System.currentTimeMillis());
        		reminderTime = task.reminder - difference;
        	}
        	
        	if (fromLocationReminder)
        	{
        		if (task.location_nag)
        		{
        			if (System.currentTimeMillis()>(notificationTime+_settings.getInt(
        				PrefNames.NAG_INTERVAL,15)*60000) && notificationTime>0)
    				{
        				// The "stop nagging" button must have been previously pressed.
        				_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
    						GONE);
    					_rootView.findViewById(R.id.view_task_reminder_buttons_container).
            				setVisibility(View.VISIBLE);
    				}
    				else
    				{
    					_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
    						VISIBLE);
    					_rootView.findViewById(R.id.view_task_reminder_buttons_container).
            				setVisibility(View.GONE);
        			}
        		}
        		else
        		{
        			_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
            			GONE);
        			_rootView.findViewById(R.id.view_task_reminder_buttons_container).
            			setVisibility(View.VISIBLE);
        		}
        	}
        	else if (task.reminder==0 || reminderTime>System.currentTimeMillis())        	
        	{
        		// The task has been edited so that there is either no reminder, or the 
        		// reminder is in the future.  In this case, we need to remove the 
        		// notification and not display any buttons.
        		Util.removeTaskNotification(task._id);
        		_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
        			GONE);
        		_rootView.findViewById(R.id.view_task_reminder_buttons_container).
    				setVisibility(View.GONE);
        	}
        	else if (!task.nag || 
        		(System.currentTimeMillis()>(notificationTime+_settings.getInt(
        		PrefNames.NAG_INTERVAL,15)*60000) && notificationTime>0))
        	{
        		_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
        			GONE);
        		_rootView.findViewById(R.id.view_task_reminder_buttons_container).
        			setVisibility(View.VISIBLE);
        	}
        	else
        	{
        		_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
        			VISIBLE);
        		_rootView.findViewById(R.id.view_task_reminder_buttons_container).
        			setVisibility(View.GONE);
        	}        		
        }
        else
        {
        	_rootView.findViewById(R.id.view_task_nag_buttons_container).setVisibility(View.
    			GONE);
        	_rootView.findViewById(R.id.view_task_reminder_buttons_container).
				setVisibility(View.GONE);
        }
        
        // After a refresh, we need to refresh the available buttons and menus:
        if (!_isOnlyFragment)
        	populateTitleBar();
    }

    // Populate the options menu when it is invoked:
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	if (!_isOnlyFragment)
    	{
    		// If there are other fragments on the screen, we do not populate the action bar.
    		return;
    	}

    	if (task==null)
    	{
    		// This shouldn't happen, but we're being safe here:
    		return;
    	}
    	
    	MenuUtil.init(menu);
    	MenuUtil.add(EDIT_ID, R.string.Edit_Task,_a.resourceIdFromAttr(R.attr.ab_edit));
        if (_settings.getBoolean(PrefNames.TIMER_ENABLED,true))
        {
	        if (task.timer_start_time>0)
	        	MenuUtil.add(TIMER_ID,R.string.Stop_Timer,_a.resourceIdFromAttr(R.attr.ab_timer_running));
	        else
	        	MenuUtil.add(TIMER_ID,R.string.Start_Timer,_a.resourceIdFromAttr(R.attr.ab_timer_off));
        }
        if (_settings.getBoolean(PrefNames.STAR_ENABLED,true))
        {
        	if (task.star)
	        	MenuUtil.add(STAR_ID,R.string.Remove_Star,_a.resourceIdFromAttr(R.attr.ab_star_on));
	        else
	        	MenuUtil.add(STAR_ID,R.string.Add_Star,_a.resourceIdFromAttr(R.attr.ab_star_off));
        }
        if (task.completed)
        	MenuUtil.add(COMPLETED_ID,R.string.Mark_Incomplete,R.drawable.checkbox_checked);
        else
        {
        	// The checkbox we display depends on priority:
        	MenuUtil.add(COMPLETED_ID,R.string.Mark_Complete,TaskListFragment.
        		_priorityCheckboxDrawables[task.priority]);
        }
    }

    // Populate the title bar (either create a new bar or refresh the exiting one)
    public void populateTitleBar()
    {
    	if (_tb==null)
    		_tb = new TitleBar(_a,(ViewGroup)getView());
    	else
    		_tb.reset();
    	_tb.setTitle(task.title);
    	_tb.addButton(EDIT_ID, _a.resourceIdFromAttr(R.attr.ab_edit_inv), R.string.Edit_Task, new
    		View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				handleTopCommand(EDIT_ID);
			}
		});
    	if (_settings.getBoolean(PrefNames.TIMER_ENABLED,true))
    	{
    		int iconResourceID;
    		int stringResourceID;
    		if (task.timer_start_time>0)
    		{
    			iconResourceID = _a.resourceIdFromAttr(R.attr.ab_timer_running_inv);
    			stringResourceID = R.string.Stop_Timer;
    		}
    		else
    		{
    			iconResourceID = _a.resourceIdFromAttr(R.attr.ab_timer_off_inv);
    			stringResourceID = R.string.Start_Timer;
    		}
    		_tb.addButton(TIMER_ID, iconResourceID, stringResourceID, new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					handleTopCommand(TIMER_ID);
					refreshNavDrawer();
					refreshTaskList();
					if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
					{
						// This is displayed in a sliding drawer, so close it.
						UtlNavDrawerActivity navActivity = (UtlNavDrawerActivity)_a;
						navActivity.closeDrawer();
					}
				}
			});
    	}
    	if (_settings.getBoolean(PrefNames.STAR_ENABLED,true))
    	{
    		int iconResourceID;
    		int stringResourceID;
    		if (task.star)
    		{
    			iconResourceID = _a.resourceIdFromAttr(R.attr.ab_star_on_inv);
    			stringResourceID = R.string.Remove_Star;
    		}
    		else
    		{
    			iconResourceID = _a.resourceIdFromAttr(R.attr.ab_star_off_inv);
    			stringResourceID = R.string.Add_Star;
    		}
    		_tb.addButton(STAR_ID, iconResourceID, stringResourceID, new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					handleTopCommand(STAR_ID);
					refreshNavDrawer();
					refreshTaskList();
					if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
					{
						// This is displayed in a sliding drawer, so close it.
						UtlNavDrawerActivity navActivity = (UtlNavDrawerActivity)_a;
						navActivity.closeDrawer();
					}
				}
			});
    	}
    	int iconResourceID;
		int stringResourceID;
		if (task.completed)
		{
			iconResourceID = R.drawable.checkbox_checked;
			stringResourceID = R.string.Mark_Incomplete;
		}
		else
		{
			iconResourceID = TaskListFragment._priorityCheckboxDrawables[task.priority];
			stringResourceID = R.string.Mark_Complete;
		}
		_tb.addButton(COMPLETED_ID, iconResourceID, stringResourceID, new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				handleTopCommand(COMPLETED_ID);
				refreshNavDrawer();
				refreshTaskList();
				if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
				{
					// This is displayed in a sliding drawer, so close it.
					UtlNavDrawerActivity navActivity = (UtlNavDrawerActivity)_a;
					navActivity.closeDrawer();
				}
			}
		});    	
    }
    
    // Handlers for commands at top (timer, star, completed checkbox):
    public void handleTopCommand(int commandID)
    {
    	TasksDbAdapter db = new TasksDbAdapter();
        FeatureUsage featureUsage = new FeatureUsage(_a);
    	
    	if (inResizeMode())
    		return;
    	
    	switch(commandID)
    	{
    	case EDIT_ID:
    		if (_isOnlyFragment)
    		{
	    		Intent i;
	    		if (_a.getClass().getName().endsWith("ViewTaskPopup"))
	    		{
	    			// The task viewer is in a popup window, so the editor should also be.
	    			i = new Intent(_a,EditTaskPopup.class);
	    		}
	    		else
	    			i = new Intent(_a,EditTask.class);
	            i.putExtra("action", EditTaskFragment.EDIT);
	            i.putExtra("id", _id);
	            startActivity(i);
    		}
    		else
    		{
    			// In split-screen mode, display the task editor in a separate fragment:
        		EditTaskFragment frag = new EditTaskFragment();
        		Bundle args = new Bundle();
        		args.putInt("action", EditTaskFragment.EDIT);
        		args.putLong("id", _id);
        		args.putBoolean("from_viewer_fragment", true);
        		frag.setArguments(args);
        		UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
        		nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG + 
        			"/" + _id);
    		}
    		return;
    		
    	case TIMER_ID:
            if (task.timer_start_time>0)
            {
                // Timer was running.  Now it's stopped.
                long elapsedTimeMillis = System.currentTimeMillis() - 
                    task.timer_start_time;
                if (elapsedTimeMillis<0) elapsedTimeMillis=0;
                task.timer_start_time = 0;
                task.timer = task.timer + (elapsedTimeMillis/1000);
                task.mod_date = System.currentTimeMillis();
                if (!db.modifyTask(task))
                {
                    Util.popup(_a, R.string.DbModifyFailed);
                    Util.log("Database modification failed when trying to stop timer "+
                        "for task ID "+task._id);
                }
                else
                {
                    Util.popup(_a, R.string.Timer_stopped);
                    instantUpload();
                    refreshDisplay();
                }                            
            }
            else
            {
                // Timer was stopped, now it's running.
                task.timer_start_time = System.currentTimeMillis();
                task.mod_date = System.currentTimeMillis();
                if (!db.modifyTask(task))
                {
                    Util.log("DB modification failed for task ID "+task._id+
                        " when starting timer.");
                    Util.popup(_a, R.string.DbModifyFailed);
                }
                else
                {
                    Util.popup(_a,R.string.Timer_is_Running);
                    instantUpload();
                    refreshDisplay();
                    if (!task.completed) featureUsage.record(FeatureUsage.TIMER);
                }                    
            }
    		return;
    		
    	case STAR_ID:
            task.star = task.star ? false : true;
            task.mod_date = System.currentTimeMillis();
            if (!db.modifyTask(task))
            {
                Util.popup(_a, R.string.DbModifyFailed);
                Util.log("Database modification failed when trying to update star "+
                    "for task ID "+task._id);
            }
            else
            {
            	instantUpload();
            	refreshDisplay();
                if (!task.completed && task.star) featureUsage.record(FeatureUsage.STAR);
            }
    		return;
    		
    	case COMPLETED_ID:
            if (!task.completed && !repeatingTasksGenerated)
            {
                // The task was incomplete and now is being marked as complete:
                Util.markTaskComplete(task._id);
                repeatingTasksGenerated = true;
                instantUpload();
                refreshDisplay();
                return;
            }
            
            task.completed = task.completed ? false : true;
            task.mod_date = System.currentTimeMillis();
            if (task.completed)
            {
                task.completion_date = System.currentTimeMillis();
            }
            else
            {
                task.completion_date = 0;  // In case task went from completed to not complete.
                
                // Since the task went from completed to incomplete, we can assume
                // that repeating tasks (if any) have already been generated for
                // this task.
                repeatingTasksGenerated = true;
            }
            if (!db.modifyTask(task))
            {
                Util.popup(_a, R.string.DbModifyFailed);
                Util.log("Database modification failed when trying to update completed "+
                    "for task ID "+task._id);
            }
            else
            {
            	instantUpload();
            	refreshDisplay();
            }
    		return;
    	}
    }
    
    // Handlers for an options menu choices:
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
    	handleTopCommand(item.getItemId());
    	
    	// Since these commands change the state of the task, we need to refresh the top menu:
    	_a.supportInvalidateOptionsMenu();
    	
    	return true;
    }
    
    // If we return here after leaving, we must refresh the data:
    @Override
    public void onResume()
    {
        super.onResume();
        refreshDisplay();
        
        // Run a sync if needed:
        Util.doMinimalSync(_a);
    }

    // Perform an instant upload of the task, if enabled by the user:
    private void instantUpload()
    {
    	if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true))
    	{
    		Util.instantTaskUpload(_a, task);
    	}
    }
    
    // Get a string containing the hours and minutes:
    private String getHourString(long minutes)
    {
    	long numHours = minutes/60;
    	long numMinutes = minutes % 60;
    	String result = "";
    	if (numHours==1)
    		result += "1 "+Util.getString(R.string.hour);
    	if (numHours>1)
    		result += numHours+" "+Util.getString(R.string.hours);
    	if (numMinutes>0 && numHours>0)
    		result += ", ";
    	if (numMinutes==1)
    		result += "1 "+Util.getString(R.string.minute);
    	if (numMinutes>1)
    		result += numMinutes+" "+Util.getString(R.string.minutes);
    	return result;
    }
    
    // Refresh the task list (needed when the task changes and a split screen view is in use):
    private void refreshTaskList()
    {
    	TaskList tl = (TaskList)_a;
    	tl.handleTaskChange();
    }
    
    // Refresh the navigation drawer counts (needed when the task changes and a split screen view is
    // in use):
    private void refreshNavDrawer()
    {
    	UtlNavDrawerActivity navActivity = (UtlNavDrawerActivity)_a;
    	navActivity.refreshNavDrawerCounts();
    }
    
    // Check to see if the parent activity is is resize mode.  In resize mode, we can't execute any 
    // commands:
    private boolean inResizeMode()
    {
    	if (_isOnlyFragment)
    		return false;
    	
    	if (_a.getClass().getName().contains("TaskList") && _splitScreen!=
    		Util.SS_NONE)
    	{
    		UtlNavDrawerActivity n = (UtlNavDrawerActivity)_a;
    		return n.inResizeMode();
    	}
    	else
    		return false;
    }
}