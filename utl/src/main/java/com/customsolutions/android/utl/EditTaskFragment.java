package com.customsolutions.android.utl;

// This handles adding and editing of tasks.

// To call this activity, put a Bundle in the intent or frag args with the following keys/values:
// action:
//    EditTask.ADD:  to add a new task
//    EditTask.EDIT: to edit an existing task
// id: The UTL task ID from the database (passed only for edit operations)
// parent_id: The parent ID of the task, if adding a subtask.  (not included for edit ops)
// clone_id: For an ADD operation, this is the ID of the task we're cloning (if applicable)
// from_viewer_fragment: Set to true if this was launched from a task viewer fragment in split-screen 
//     mode, and should return there when done.

// These can be used for an add operation.  Do not combine with parent_id.
// default_folder_id
// default_context_id
// default_goal_id
// default_loc_id

// The pure calendar widget sends us this URI:
// URI: content://com.customsolutions.android.utl.purecalendarprovider/tasks/<task #>
// If the task # does not exist, then this will be considered to be an "add" operation.

// To create a task using android's sharing functions, this accepts an intent with the 
// following properties:
// Action: ACTION_SEND
// Bundle: EXTRA_TEXT - A string containing some text. This class will determine whether
//     to place this in the title or note of the new task.
// Bundle: EXTRA_SUBJECT - If present, the subject becomes the title and the rest becomes
//     the note.

// The task is actually created or edited in this activity and stored in the DB.  The 
// response sent back to the caller includes the following:
// resultCode: either RESULT_CANCELED or RESULT_OK
// Intent object extras:
//     none

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;

import org.droidparts.widget.ClearableEditText;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;

import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.PopupMenu;
import ezvcard.Ezvcard;
import ezvcard.VCard;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

@SuppressLint("InlinedApi")
public class EditTaskFragment extends UtlFragment
{
    private static final String TAG = "EditTaskFragment";

    // Codes to specify whether we are adding or editing:
    public static final int ADD = 1;
    public static final int EDIT = 2;
    
    // Codes to track responses to activities:
    public static final int GET_START_DATE = 1;
    public static final int GET_DUE_DATE = 2;
    public static final int GET_REMINDER_DATE = 3;
    public static final int GET_ADVANCED_REPEAT = 4;
    public static final int GET_ADVANCED_REPEAT2 = 5;
    public static final int GET_TAGS = 6;
    public static final int GET_ACCOUNTS = 7;
    public static final int EDIT_NOTE = 8;
    public static final int NEW_LOCATION = 9;
    public static final int GET_CONTACT = 10;
    public static final int GET_SHARING = 11;
    
    // Menu items:
    private static final int MENU_DELETE = Menu.FIRST+1;
    private static final int MENU_EDIT_PARENT = Menu.FIRST+2;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditTaskFragment";
    
    // The operation we're performing (ADD or EDIT)
    private int _op;
    
    // The ID of the subtask's parent, if applicable:
    private long parentID;
    boolean isSubtask;
    
    // The ID of the task we're cloning, if applicable:
    private long _cloneID;
    private boolean _isClone;
    
    /** The task we're editing, if applicable: */
    private UTLTask t;
        
    // The due date modifier.  Either blank, "due_by", "due_on", or "optionally_on":
    private String dueModifier;
    
    // Flag which indicates if any changes have been made:
    private boolean _changesMade;
    
    // Flag which indicates if the repeat options spinner has initialized:
    private int repeatSpinnerDisableCount;
    
    // This hash is used to determine if other spinners have changed their selection:
    private HashMap<Integer,Integer> _spinnerSelections;
    
    // This set keeps track of the IDs of selected accounts:
    HashSet<Long> _selectedAccountIDs;
    
    // These keep track of whether or not the star and timer are on:
    private boolean starOn;
    private boolean timerOn;
    
    private boolean askForTitle;
    
    // IDs of views whose contents are not automatically saved when the screen
    // changes orientation:
    static private int[] _viewIDsToSave = new int[] {
    	R.id.edit_task_start_date_text,
    	R.id.edit_task_start_time_text,      R.id.edit_task_due_date_text,
    	R.id.edit_task_due_date_advanced_text,
    	R.id.edit_task_due_time_text,        R.id.edit_task_reminder_text,
    	R.id.edit_task_reminder_time_text,   R.id.edit_task_tags2,
    	R.id.edit_task_accounts2,            R.id.edit_task_expected_length2,
    	R.id.edit_task_actual_length2,
    	R.id.edit_task_repeat_advanced2,	 R.id.edit_task_shared_with_value
    };
    
    // Variables to hold the scroll position
    int scrollPositionY;
    
    // The number of accounts the user has:
    private int _numAccounts;
    
    // An EditText input for use in dialogs:
    private EditText _editText;
    
    // The Lookup Key of the selected contact:
    private String _contactLookupKey;
    
    // Keeps track of whether or not the task note has hyperlinks.
    private boolean _noteHasLinks = false;
    
	// Used for choosing a calendar to link to:
	private long[] _calendarIDs;
	private String[] _calNames;
	
	// Quick reference to key items:
    private UtlActivity _a;
    private Resources _res;
    private int _ssMode;
    private ViewGroup _rootView;
    private SharedPreferences _settings;
    
    // Records if we're the only fragment in the activity.
    private boolean _isOnlyFragment;

    // The save/cancel bar (if it's in use):
    private SaveCancelTopBar _saveCancelBar;
    
    // Set this to true if the editor was launched from the viewer in split-screen mode:
    private boolean _launchedFromViewerInSS;
    
    // Holds a version of the task's note without links:
    private String _noteWithoutLinks;
    
    // Hold the button ID of the tab that was last selected:
    private int _tabButtonID;
    
    // Quick reference to TextViews that hold values for the task:
    private ClearableEditText _title;
    private TextView _startDate;
    private TextView _startTime;
    private TextView _dueDate;
    private TextView _dueTime;
    private TextView _dueDateAdvanced;
    private CheckBox _addToCal;
    private Spinner _relativeReminderSpinner;
    private TextView _reminderDate;
    private TextView _reminderTime;
    private CheckBox _reminderNag;
    private Spinner _repeat;
    private TextView _repeatAdvanced;
    private Spinner _repeatFrom;
    private Spinner _status;
    private Spinner _priority;
    private Spinner _folder;
    private Spinner _context;
    private Spinner _goal;
    private TextView _tags;
    private TextView _accounts;
    private Spinner _location;
    private CheckBox _locReminder;
    private CheckBox _locNag;
    private TextView _expectedLength;
    private TextView _actualLength;
    private TextView _contact;
    private EditText _note;
    private TextView _sharedWith;
    
    // Used for in-app billing and licensing status:
    private PurchaseManager _pm;
    
    // Contains handlers to call if the user exits without saving:
    private ExitWithoutSaveHandler _tempExitWithoutSaveHandler;

    /** This timestamp is used to prevent the Advanced Repeat activity from launching when the
     * fragment is first initialized, or after a rotation. */
    private long _advancedRepeatBlockerTimestamp;

     // This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_task, container, false);
    }
    
    // Called when activity is started:
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        super.onActivityCreated(savedInstanceState);
                
        _a = (UtlActivity) getActivity();
        _res = _a.getResources();
        _settings = _a._settings;
        _ssMode = _a.getSplitScreenOption();
        _rootView = (ViewGroup)getView();
        _launchedFromViewerInSS = false;
        repeatSpinnerDisableCount = 0;
        _tabButtonID = R.id.edit_task_section_main_button;
        _advancedRepeatBlockerTimestamp = System.currentTimeMillis();
        
        // Get references to the views which hold task data:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_task_title2);
        _startDate = (TextView)_rootView.findViewById(R.id.edit_task_start_date_text);
        _startTime = (TextView)_rootView.findViewById(R.id.edit_task_start_time_text);
        _dueDate = (TextView)_rootView.findViewById(R.id.edit_task_due_date_text);
        _dueTime = (TextView)_rootView.findViewById(R.id.edit_task_due_time_text);
        _dueDateAdvanced = (TextView)_rootView.findViewById(R.id.edit_task_due_date_advanced_text);
        _addToCal = (CheckBox)_rootView.findViewById(R.id.edit_task_calendar_checkbox);
        _relativeReminderSpinner = (Spinner)_rootView.findViewById(R.id.edit_task_reminder_list);
        _reminderDate = (TextView)_rootView.findViewById(R.id.edit_task_reminder_text);
        _reminderTime = (TextView)_rootView.findViewById(R.id.edit_task_reminder_time_text);
        _reminderNag = (CheckBox)_rootView.findViewById(R.id.edit_task_nag_checkbox);
        _repeat = (Spinner)_rootView.findViewById(R.id.edit_task_repeat2);
        _repeatAdvanced = (TextView)_rootView.findViewById(R.id.edit_task_repeat_advanced2);
        _repeatFrom = (Spinner)_rootView.findViewById(R.id.edit_task_repeat_from2);
        _status = (Spinner)_rootView.findViewById(R.id.edit_task_status2);
        _priority = (Spinner)_rootView.findViewById(R.id.edit_task_priority2);
        _folder = (Spinner)_rootView.findViewById(R.id.edit_task_folder2);
        _context = (Spinner)_rootView.findViewById(R.id.edit_task_context2);
        _goal = (Spinner)_rootView.findViewById(R.id.edit_task_goal2);
        _tags = (TextView)_rootView.findViewById(R.id.edit_task_tags2);
        _accounts = (TextView)_rootView.findViewById(R.id.edit_task_accounts2);
        _location = (Spinner)_rootView.findViewById(R.id.edit_task_location2);
        _locReminder = (CheckBox)_rootView.findViewById(R.id.edit_task_location_reminder_checkbox);
        _locNag = (CheckBox)_rootView.findViewById(R.id.edit_task_location_nag_checkbox);
        _expectedLength = (TextView)_rootView.findViewById(R.id.edit_task_expected_length2);
        _actualLength = (TextView)_rootView.findViewById(R.id.edit_task_actual_length2);
        _contact = (TextView)_rootView.findViewById(R.id.edit_task_contact2);
        _note = (EditText)_rootView.findViewById(R.id.edit_task_note2);
        _sharedWith = (TextView)_rootView.findViewById(R.id.edit_task_shared_with_value);
        
        Intent intent = _a.getIntent();
        
        // Handler code for pure calendar widget:
        if (intent.getData()!=null && intent.getData().getScheme().equals("content"))
        {
        	// This is being called from the outside. Get the task ID if available:
        	Util.log("EditTask called from outside: "+intent.getDataString());
        	String lastSegment = intent.getData().getLastPathSegment();
        	Long taskID;
        	try
        	{
        		// Add some extras to the intent to make it compatible with the rest of
        		// the code.
        		taskID = Long.parseLong(lastSegment);
        		intent.putExtra("action",EditTaskFragment.EDIT);
        		intent.putExtra("id", taskID);
        	}
        	catch (NumberFormatException e)
        	{
        		// No task ID.  This is an add operation.  Add some extras to the Intent
        		// to reflect this.
        		intent.putExtra("action", EditTaskFragment.ADD);
        	}
        }
        
        Bundle extras = intent.getExtras();
        
        // Handler code for Android's sharing / "send to" functions (for email):
        String defaultTitle = "";
        String defaultNote = "";
        if (intent.getAction()!=null && extras!=null && intent.getAction().equals(Intent.
        	ACTION_SEND) && extras.containsKey(Intent.EXTRA_TEXT))
        {
        	// Add an extra to the Intent to made it compatible with the rest of the code
        	// here:
        	intent.putExtra("action", EditTaskFragment.ADD);
        	
        	if (extras.containsKey(Intent.EXTRA_SUBJECT) && 
        		extras.getString(Intent.EXTRA_SUBJECT) != null &&
        		!extras.getString(Intent.EXTRA_SUBJECT).startsWith("http"))
        	{
        		// Subject is the title, and the rest is the note.
        		defaultTitle = extras.getString(Intent.EXTRA_SUBJECT);
        		if (extras.getCharSequence(Intent.EXTRA_TEXT)!=null)
        			defaultNote = extras.getCharSequence(Intent.EXTRA_TEXT).toString();
        	}
        	else if (extras.getCharSequence(Intent.EXTRA_TEXT)!=null)
        	{
        		String str = extras.getCharSequence(Intent.EXTRA_TEXT).toString();
        		if (str.contains("\n") || str.contains("\r"))
    			{
        			// Text contains at least one newline, so it must go in the note.
        			defaultNote = str;
    			}
        		else if (str.length()>Util.MAX_TASK_TITLE_LENGTH)
        		{
        			// Text is too long, so it must go in the note.
        			defaultNote = str;
        		}
        		else if (str.startsWith("http://") || str.startsWith("https://"))
        		{
        			// It's a URL, so it must go in the note.
        			defaultNote = str;
        		}
        		else
        		{
        			// Text can go in the title:
        			defaultTitle = str;
        		}
        	}
        }
        
        // Handles when this is called from a contact:
        String defaultContactName = "";
        if (intent.getAction()!=null && extras!=null && intent.getAction().equals(Intent.
        	ACTION_SEND) && extras.containsKey(Intent.EXTRA_STREAM) &&
            ContextCompat.checkSelfPermission(_a, Manifest.permission.READ_CONTACTS)==
            PackageManager.PERMISSION_GRANTED)
        {
            try
        	{
        	    // Look up the contact passed in:
                Uri contactUri = (Uri)extras.get(Intent.EXTRA_STREAM);
        		Util.log("Adding a new task from a contact: "+contactUri.toString());
        		Cursor c = _a.managedQuery(contactUri,null,null,null,null);
        		String[] cols = c.getColumnNames();
        		String colString = "";
        		for (int ci=0; ci<cols.length; ci++)
        		{
        			colString+=cols[ci]+", ";
        		}
        		Util.log("Columns from contact query: "+colString+"; Rows matched: "+c.getCount());
        		if (c!=null && c.moveToFirst())
        		{

        			// A second query is needed to get the contact lookup key. The 
        			// query to run depends on whether or not the previous query
        			// gave us the "_id" column.
                    Cursor c2;
        			if (c.getColumnIndex(ContactsContract.Contacts._ID)==-1)
        			{
        				// No "_id" column.  See if we can pull a lookup key from
        				// the URI we received from the contacts app.
        				String lookupKey = contactUri.getLastPathSegment();
        				Log.v(TAG,"No _id column. Trying lookup key: "+lookupKey);
        				if (lookupKey!=null && lookupKey.length()>0)
        				{
        					Uri contactUri2 = Uri.withAppendedPath(ContactsContract.
        						Contacts.CONTENT_LOOKUP_URI,lookupKey);
        					try
        					{
        						c2 = _a.managedQuery(contactUri2,null,null,null,null);
        					}
        					catch (Exception e)
        					{
        						// The lookupKey may actually be a row ID.  Try again
        						// under the assumption that the Uri contains a row ID
        						// at the end and a lookupKey as the second to last
        						// segment.
        						Util.log(e.getClass().getName()+": "+e.getMessage());
        						Util.log("Trying again with lookup key and row ID");
        						List<String> segments = contactUri.getPathSegments();
        						lookupKey = segments.get(segments.size()-2)+"/"+
        							segments.get(segments.size()-1);
        						contactUri2 = Uri.withAppendedPath(ContactsContract.
            						Contacts.CONTENT_LOOKUP_URI,lookupKey);
        						c2 = _a.managedQuery(contactUri2,null,null,null,null);
        					}
        				}
        				else
        					c2 = null;
        			}
        			else
        			{
        			    Log.v(TAG,"Found a contact ID.");
        				Uri contactUri2 = Uri.withAppendedPath(ContactsContract.Contacts.
        					CONTENT_URI, new Long(c.getLong(c.getColumnIndexOrThrow(
        					ContactsContract.Contacts._ID))).toString());
        				c2 = _a.managedQuery(contactUri2,null,null,null,null);
        			}
        			if (c2!=null && c2.moveToFirst())
        			{
        				_contactLookupKey = c2.getString(c2.getColumnIndexOrThrow(
        					ContactsContract.Contacts.LOOKUP_KEY));
        				defaultContactName = c2.getString(c2.getColumnIndexOrThrow(
        					ContactsContract.Contacts.DISPLAY_NAME));
        			
	        			// Add an extra to the Intent to made it compatible with the rest of the code
	                	// here:
	                	intent.putExtra("action", EditTaskFragment.ADD);
	                	extras.putInt("action", EditTaskFragment.ADD);
        			}
        		}
        	}
        	catch (Exception e)
        	{
        		// We get here on newer versions of Android. We no longer receive the exact
                // contact ID or lookup key.  All we can do is process the VCard file we get
                // and hope the name exactly matches an entry in the local contacts database.
                Log.d(TAG,"Using vcard fallback option for contact lookup",e);
                Uri contactUri = (Uri)extras.get(Intent.EXTRA_STREAM);
                if (!contactUri.toString().toLowerCase().contains("vcf") &&
                    (intent.getType()==null || !intent.getType().toLowerCase().contains("vcard")))
                {
                    Log.e(TAG,"Can't process incoming contact.","Received a contact for a new "+
                        "task, but the MIME type was not a vcard. MIME type: "+intent.getType()+
                        "; Contact URI: "+contactUri);
                }
                else
                {
                    try
                    {
                        // Read the VCard contents:

                        BufferedReader reader = new BufferedReader(new InputStreamReader(_a.
                            getContentResolver().openInputStream(contactUri)));
                        VCard vcard = Ezvcard.parse(reader).first();
                        reader.close();

                        // Get the name and check the database for a match.
                        String name = vcard.getFormattedName().getValue();
                        Log.v(TAG,"Got name from vcard: "+name);
                        Cursor c = _a.getContentResolver().query(
                            ContactsContract.Contacts.CONTENT_URI,
                            new String[] {ContactsContract.Contacts.LOOKUP_KEY},
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY+"=?",
                            new String[] {name},
                            null
                        );
                        if (c.moveToFirst())
                        {
                            _contactLookupKey = c.getString(0);
                            defaultContactName = name;
                            Log.v(TAG,"Found matching contact lookup key: "+_contactLookupKey);
                        }
                        else
                        {
                            Log.e(TAG,"Can't find contact","Can't find a lookup key for the "+
                                "contact name passed in: "+name);
                        }
                        c.close();
                    }
                    catch (Exception e2)
                    {
                        Log.e(TAG,"Can't process VCard","Got an exception when processing an "+
                            "incming VCard.",e2);
                    }
                }
        	}
        }
        
        // Handles when this is called from a calendar entry:
        if (intent.getData()!=null && intent.getData().getScheme().startsWith("http") &&
        	intent.getData().getHost().equals("edit.todolist.co"))
        {
        	Util.log("EditTask called from calendar entry.");
        	String taskIDString = intent.getData().getLastPathSegment();
        	if (taskIDString!=null && Util.regularExpressionMatch(taskIDString, "^\\d+$"))
        	{
        		long taskID = Long.parseLong(taskIDString);
        		
        		// Add an extra to the Intent to made it compatible with the rest of the code
            	// here:
            	intent.putExtra("action", EditTaskFragment.EDIT);
            	intent.putExtra("id", taskID);
            	extras = intent.getExtras();
        	}
        	else
        	{
        		Util.log("Bad task ID passed in: "+taskIDString);
        	}
        }

        // Determine whether we're adding or editing:
        _isClone = false;
        _isOnlyFragment = true;
        _saveCancelBar = null;
        Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.size()>0)
        {
        	// Arguments were passed into the fragment.  These take precedence over anything passed
        	// into the parent Activity's Intent.
        	extras = fragArgs;
        	_isOnlyFragment = false;
        	
        	if (extras.containsKey("from_viewer_fragment") && extras.getBoolean("from_viewer_fragment"))
        		_launchedFromViewerInSS = true;
        	
        	// We will use a separate save/cancel bar:
        	_saveCancelBar = new SaveCancelTopBar(_a,_rootView);
        	_saveCancelBar.setSaveHandler(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					// Do nothing if resize mode is on:
					if (inResizeMode()) return;
					
					saveAndReturn();
				}
			});
        	_saveCancelBar.setCancelHandler(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					// Do nothing if resize mode is on:
					if (inResizeMode()) return;
					
					handleCancelButton();
				}
			});
        }
        if (extras==null)
        {
            Util.log("Add a new task");
            _op = ADD;
            setTitle(R.string.Add_a_Task);
        }
        else if (extras.getInt("action")==ADD)
        {
            Util.log("Add a new task");
            _op = ADD;
            if (extras.containsKey("clone_id"))
            {
            	_cloneID = extras.getLong("clone_id");
            	_isClone = true;
            	setTitle(R.string.Clone_a_Task);
            	
            	TasksDbAdapter db = new TasksDbAdapter();
                t = db.getTask(_cloneID);
                if (t==null)
                {
                    Util.log("Bad clone ID passed into EditTask");
                    _op = ADD;
                    setTitle(R.string.Add_a_Task);
                    _isClone = false;
                    _cloneID = 0;
                }
            }
            else
            	setTitle(R.string.Add_a_Task);   
        }
        else if (extras.getInt("action")==EDIT &&
            extras.getLong("id")>0)
        {
            Util.log("Edit task ID "+extras.getLong("id"));
            _op = EDIT;
            setTitle(R.string.Edit_Task);
            
            // When editing, we need to retrieve the task:
            TasksDbAdapter db = new TasksDbAdapter();
            t = db.getTask(extras.getLong("id"));
            if (t==null)
            {
                Util.log("Bad task ID passed into EditTask");
                _op = ADD;
                setTitle(R.string.Add_a_Task);
            }
        }
        else
        {
            Util.log("Add a new task (bad inputs passed in)");
            _op = ADD;
            setTitle(R.string.Add_a_Task);
        }
        
        // Update the icon if this is the only fragment on the screen and we're adding.
        if (_isOnlyFragment && _op==ADD)
        	_a.getSupportActionBar().setIcon(R.drawable.new_task_widget);

        if (_isOnlyFragment)
            initBannerAd(_rootView);

        // Save the parent ID, if applicable:
        isSubtask = false;
        if (extras!=null && extras.containsKey("parent_id"))
        {
            parentID = extras.getLong("parent_id");
            isSubtask = true;
            setTitle(R.string.Add_Subtask);
        }
             
        if (_op==EDIT || _isClone || (savedInstanceState!=null && savedInstanceState.
        	containsKey("dont_show_keyboard")))
        {
        	// When editing or cloning, we don't want the keyboard to appear automatically.
        	_a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        else
        	_a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        
        if (_isOnlyFragment)
        {
        	// This fragment will be updating the Action Bar:
        	setHasOptionsMenu(true);
        	
        	// This fragment also handles the home button.
        	_a.fragmentHandlesHome(true);
        }
        
    	if (_saveCancelBar!=null && _op==EDIT)
    	{
    		// Add additional commands to the save/cancel bar (only applicable when editing):
    		_saveCancelBar.addToOverflow(MENU_DELETE, R.string.Delete);
            if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true) && t.parent_id>0)
                _saveCancelBar.addToOverflow(MENU_EDIT_PARENT, R.string.edit_parent_task);
        	_saveCancelBar.setMenuListener(new PopupMenu.OnMenuItemClickListener()
			{
				@Override
				public boolean onMenuItemClick(MenuItem arg0)
				{
					return onOptionsItemSelected(arg0);
				}
			});
    	}

        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
    	_numAccounts = c1.getCount();
    	c1.close();
    	
        //
        // Hide layout items that apply to certain disabled features.  Also make any
        // other minor tweaks.
        //
        
        if (!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_folder_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_context_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_goal_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_location_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_start_date_container).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.edit_task_start_time_container).setVisibility(View.GONE);
        }
        else if (!_settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_start_time_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_due_date_container).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.edit_task_due_date_advanced_container).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.edit_task_due_time_container).setVisibility(View.GONE);
        }
        else if (!_settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_due_time_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
        	// If both due and start dates are disabled, hide their table:
        	_rootView.findViewById(R.id.edit_task_timing_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_reminder_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_repeat_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_length_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_actual_length_container).setVisibility(View.GONE);
        	_rootView.findViewById(R.id.edit_task_timer_button).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_priority_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_tags_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_status_container).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.STAR_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_star_button).setVisibility(View.GONE);
        }
        if (_numAccounts<2)
        {
        	_rootView.findViewById(R.id.edit_task_accounts_container).setVisibility(View.GONE);
        }
        if (_numAccounts<2 && 
        	!_settings.getBoolean(PrefNames.STATUS_ENABLED, true) && 
        	!_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.GOALS_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_classification_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.LENGTH_ENABLED, true) &&
        	!_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
        {
        	// Don't show the table that encapsulates these:
        	_rootView.findViewById(R.id.edit_task_length_table).setVisibility(View.GONE);
        }
        if (!_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
        	_rootView.findViewById(R.id.edit_task_contact_table).setVisibility(View.GONE);
        if (!_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        	_rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.GONE);
                
        //
        // Populate spinners:
        //
                
        // Set the titles for these spinners:
        _status.setPromptId(R.string.Status);
        _repeat.setPromptId(R.string.Repeat);
        _priority.setPromptId(R.string.Priority);
        _relativeReminderSpinner.setPromptId(R.string.Reminder);
        _repeatFrom.setPromptId(R.string.Repeat_From_);
        
        // Populate the folder list:
        ArrayAdapter<String> folderSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_folder2);
        if (savedInstanceState!=null && savedInstanceState.containsKey("folders"))
        {
        	String[] spinnerItems = savedInstanceState.getStringArray("folders");
        	for (int i=0; i<spinnerItems.length; i++)
        	{
        		folderSpinnerAdapter.add(spinnerItems[i]);
        	}
        }
        else if (_op==ADD)
        {
        	AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        	Cursor c = accountsDB.getAllAccounts();
        	if (!c.moveToFirst())
            {
            	// The user has managed to get here without setting up an account first.
            	c.close();
                Intent i = new Intent(_a,main.class);
                startActivity(i);
                _a.finish();
                return;
            }        	
        	UTLAccount firstAccount = accountsDB.getUTLAccount(c);
        	if (c.getCount()>1 || firstAccount.sync_service!=UTLAccount.SYNC_TOODLEDO)
        	{
	            // Retrieve all folders from all accounts, removing duplicate names and sorting.
	            c.close();
        		SortedSet<String> folderNames = new TreeSet<String>();
	            c = (new FoldersDbAdapter()).getFoldersByName();
	            if (c.moveToFirst())
	            {
	                while (!c.isAfterLast())
	                {
	                    folderNames.add(Util.cString(c, "title"));
	                    c.moveToNext();
	                }
	            }
	            c.close();
	
	            // Go through the items, and add to the spinner:
	            String[] sortedNames = Util.iteratorToStringArray(folderNames.iterator(),
	            	folderNames.size());
	            Arrays.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
	            for (int i=0; i<sortedNames.length; i++)
	            {
	                folderSpinnerAdapter.add(sortedNames[i]);
	            }
        	}
        	else
        	{
        		// Only 1 account, which syncs with Toodledo. Use Toodledo ordering.
        		c.close();
        		c = (new FoldersDbAdapter()).getFoldersByOrder();
        		c.moveToPosition(-1);
        		while (c.moveToNext())
        		{
        			folderSpinnerAdapter.add(Util.cString(c, "title"));
        		}
        		c.close();
        	}
            folderSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            folderSpinnerAdapter.add(Util.getString(R.string.Add_Folder)); // "Add Folder"
        }
        else
        {
            // We only include the folders from the task's account.  If the folder is
        	// archived, put it at the top (because it won't be found in the code below)
        	FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        	Cursor c = foldersDB.getFolder(t.folder_id);
        	if (c.moveToFirst())
        	{
        		if (Util.cInt(c, "archived")==1)
        		{
        			folderSpinnerAdapter.add(Util.cString(c, "title"));
        		}
        	}
        	c.close();
        	UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);
        	if (a!=null && a.sync_service==UTLAccount.SYNC_TOODLEDO)
        		c = (new FoldersDbAdapter()).getFoldersByOrder();
        	else
        		c = (new FoldersDbAdapter()).getFoldersByNameNoCase();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    if (Util.cLong(c, "account_id") == t.account_id)
                    {
                        folderSpinnerAdapter.add(Util.cString(c, "title"));
                    }
                    c.moveToNext();
                }
            }
        	c.close();
            folderSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            folderSpinnerAdapter.add(Util.getString(R.string.Add_Folder)); // "Add Folder"
        }
        Spinner spinner;
        _folder.setPromptId(R.string.Folder);
        
        // Populate the context list:
        ArrayAdapter<String> contextSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_context2);
        if (savedInstanceState!=null && savedInstanceState.containsKey("contexts"))
        {
        	String[] spinnerItems = savedInstanceState.getStringArray("contexts");
        	for (int i=0; i<spinnerItems.length; i++)
        	{
        		contextSpinnerAdapter.add(spinnerItems[i]);
        	}
        }
        else if (_op==ADD)
        {
            // Retrieve all contexts from all accounts, removing duplicate names and sorting.
            SortedSet<String> contextNames = new TreeSet<String>();
            Cursor c = (new ContextsDbAdapter()).getContextsByName();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    contextNames.add(Util.cString(c, "title"));
                    c.moveToNext();
                }
            }
            c.close();

            // Go through the items, and add to the spinner:
            String[] sortedNames = Util.iteratorToStringArray(contextNames.iterator(),
            	contextNames.size());
            Arrays.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
            for (int i=0; i<sortedNames.length; i++)
            {
                contextSpinnerAdapter.add(sortedNames[i]);
            }
            contextSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            contextSpinnerAdapter.add(Util.getString(R.string.Add_Context)); // "Add Context"
        }
        else
        {
            // We only include the contexts from the task's account.
            Cursor c = (new ContextsDbAdapter()).getContextsByNameNoCase();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    if (Util.cLong(c, "account_id") == t.account_id)
                    {
                        contextSpinnerAdapter.add(Util.cString(c, "title"));
                    }
                    c.moveToNext();
                }
            }
            c.close();
            contextSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            contextSpinnerAdapter.add(Util.getString(R.string.Add_Context)); // "Add Context"
        }
        _context.setPromptId(R.string.Context);
        
        // Populate the goal list:
        ArrayAdapter<String> goalSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_goal2);
        if (savedInstanceState!=null && savedInstanceState.containsKey("goals"))
        {
        	String[] spinnerItems = savedInstanceState.getStringArray("goals");
        	for (int i=0; i<spinnerItems.length; i++)
        	{
        		goalSpinnerAdapter.add(spinnerItems[i]);
        	}
        }
        else if (_op==ADD)
        {
            // Retrieve all goals from all accounts, removing duplicate names and sorting.
            SortedSet<String> goalNames = new TreeSet<String>();
            Cursor c = (new GoalsDbAdapter()).getAllGoals();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    goalNames.add(Util.cString(c, "title"));
                    c.moveToNext();
                }
            }
            c.close();

            // Go through the items, and add to the spinner:
            String[] sortedNames = Util.iteratorToStringArray(goalNames.iterator(),
            	goalNames.size());
            Arrays.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
            for (int i=0; i<sortedNames.length; i++)
            {
                goalSpinnerAdapter.add(sortedNames[i]);
            }
            goalSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            goalSpinnerAdapter.add(Util.getString(R.string.Add_Goal)); // "Add Goal"
        }
        else
        {
            // We only include the goals from the task's account.  If the goal is
        	// archived, we add it in first, because it won't be found in the code below.
        	GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        	Cursor c = goalsDB.getGoal(t.goal_id);
        	if (c.moveToFirst())
        	{
        		if (Util.cInt(c, "archived")==1)
        		{
        			goalSpinnerAdapter.add(Util.cString(c, "title"));
        		}
        	}
            c.close();
            c = (new GoalsDbAdapter()).getAllGoalsNoCase();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    if (Util.cLong(c, "account_id") == t.account_id)
                    {
                        goalSpinnerAdapter.add(Util.cString(c, "title"));
                    }
                    c.moveToNext();
                }
            }
            c.close();
            goalSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            goalSpinnerAdapter.add(Util.getString(R.string.Add_Goal)); // "Add Goal"
        }
        _goal.setPromptId(R.string.Goal);

        // Populate the location spinner:
        this.refreshLocationSpinner(savedInstanceState, null);
        
        //
        // Initialize the data in the views:
        //
        
        starOn = false;
        timerOn = false;
        if (_op==ADD && !_isClone)
        {
            // Fill in defaults from the user preference or data passed in:
            
        	// Title:
        	if (defaultTitle.length()>0)
        	{
        		_title.setText(defaultTitle);
        	}
        	
        	// Note:
        	if (defaultNote.length()>0)
        	{
        		_note.setText(defaultNote);
        		addLinksToNote();
        	}
        	
            // Status:
        	_status.setSelection(_settings.getInt(PrefNames.DEFAULT_STATUS, 0));
            
        	// Get the offset in ms between the home time zone and the local one:
        	TimeZone currentTimeZone = TimeZone.getDefault();
        	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
        		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
        	long timeZoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
    			defaultTimeZone.getOffset(System.currentTimeMillis());
        	
            // Start Date:
            if (_settings.getString(PrefNames.DEFAULT_START_DATE, "").equals("today"))
            {
                _startDate.setText(Util.getDateString(System.currentTimeMillis()+
                	timeZoneOffset));
            }
            if (_settings.getString(PrefNames.DEFAULT_START_DATE, "").equals("tomorrow"))
            {
                _startDate.setText(Util.getDateString(System.currentTimeMillis()+
                    24*60*60*1000+timeZoneOffset));
            }
            
            // Due Date:
            dueModifier = "";
            if (_settings.getString(PrefNames.DEFAULT_DUE_DATE, "").equals("today"))
            {
                _dueDate.setText(Util.getDateString(System.currentTimeMillis()+
                	timeZoneOffset));
                dueModifier = "due_by";
            }
            if (_settings.getString(PrefNames.DEFAULT_DUE_DATE, "").equals("tomorrow"))
            {
            	_dueDate.setText(Util.getDateString(System.currentTimeMillis()+
                    24*60*60*1000+timeZoneOffset));
                dueModifier = "due_by";
            }
            
            // Add to Calendar:
           	_addToCal.setChecked(_settings.getBoolean(PrefNames.DEFAULT_ADD_TO_CAL,false));
            
            // Priority:
           	_priority.setSelection(_settings.getInt(PrefNames.DEFAULT_PRIORITY, 0));

            // Some preference items will be taken from the parent task, if applicable:
            UTLTask parent = null;
            if (isSubtask)
            {
                parent = (new TasksDbAdapter()).getTask(parentID);
            }
            
            // Folder:
            if (isSubtask)
            {
                Cursor c = (new FoldersDbAdapter()).getFolder(parent.folder_id);
                if (c.moveToFirst())
                {
                    _a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (extras!=null && extras.containsKey("default_folder_id"))
            {
            	Cursor c = (new FoldersDbAdapter()).getFolder(extras.getLong("default_folder_id"));
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (_settings.contains(PrefNames.DEFAULT_FOLDER))
            {
                long folderID = (long)_settings.getLong(PrefNames.DEFAULT_FOLDER,(long)0);
                Cursor c = (new FoldersDbAdapter()).getFolder(folderID);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                }  
                c.close();
            }

            // Context:
            if (isSubtask)
            {
                Cursor c = (new ContextsDbAdapter()).getContext(parent.context_id);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_context, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (extras!=null && extras.containsKey("default_context_id"))
            {
            	Cursor c = (new ContextsDbAdapter()).getContext(extras.getLong("default_context_id"));
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_context, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (_settings.contains(PrefNames.DEFAULT_CONTEXT))
            {
                Cursor c = (new ContextsDbAdapter()).getContext(_settings.getLong(
                	PrefNames.DEFAULT_CONTEXT,0));
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_context, Util.cString(c, "title"));
                }  
                c.close();
            }
            
            // Goal:
            if (isSubtask)
            {
                Cursor c = (new GoalsDbAdapter()).getGoal(parent.goal_id);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (extras!=null && extras.containsKey("default_goal_id"))
            {
            	Cursor c = (new GoalsDbAdapter()).getGoal(extras.getLong("default_goal_id"));
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                }
                c.close();
            }
            else if (_settings.contains(PrefNames.DEFAULT_GOAL))
            {
                Cursor c = (new GoalsDbAdapter()).getGoal(_settings.getLong(
                	PrefNames.DEFAULT_GOAL,0));
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                }   
                c.close();
            }

            // Location:
            if (isSubtask)
            {
            	UTLLocation loc = (new LocationsDbAdapter()).getLocation(parent.location_id);
            	if (loc!=null)
            	{
            		_a.setSpinnerSelection(_location, loc.title);
            	}
            }
            else if (extras!=null && extras.containsKey("default_loc_id"))
            {
            	UTLLocation loc = (new LocationsDbAdapter()).getLocation(extras.getLong(
            		"default_loc_id"));
                if (loc!=null)
                {
                	_a.setSpinnerSelection(_location, loc.title);
                }
            }
            else if (_settings.contains(PrefNames.DEFAULT_LOCATION))
            {
            	UTLLocation loc = (new LocationsDbAdapter()).getLocation(
            		_settings.getLong(PrefNames.DEFAULT_LOCATION, 0));
            	if (loc!=null)
            		_a.setSpinnerSelection(_location, loc.title);
            }
            
            // Account:
            _selectedAccountIDs = new HashSet<Long>();
            if (isSubtask)
            {
                UTLAccount acc = (new AccountsDbAdapter()).getAccount(parent.account_id);
                if (acc != null)
                {
                    _accounts.setText(acc.name);
                    _selectedAccountIDs.add(parent.account_id);
                }
            }
            else if (_settings.contains(PrefNames.DEFAULT_ACCOUNT))
            {
                UTLAccount acc = (new AccountsDbAdapter()).getAccount(_settings.getLong(
                	PrefNames.DEFAULT_ACCOUNT, 0));
                if (acc != null)
                {
                    _accounts.setText(acc.name);
                    _selectedAccountIDs.add(acc._id);
                }             
                else
                {
                	// Get the first account:
                	AccountsDbAdapter db = new AccountsDbAdapter();
                    Cursor c = db.getAllAccounts();
                    if (c.moveToFirst())
                    {
                        acc = db.getUTLAccount(c);
                        _accounts.setText(acc.name);
                        _selectedAccountIDs.add(acc._id);
                    }
                    c.close();
                }
            }
            else
            {
                // No default account specified.  Get the first account.
                AccountsDbAdapter db = new AccountsDbAdapter();
                Cursor c = db.getAllAccounts();
                if (c.moveToFirst())
                {
                    UTLAccount acc = db.getUTLAccount(c);
                    _accounts.setText(acc.name);
                    _selectedAccountIDs.add(acc._id);
                }
                c.close();
            }

            // Tags:
            if (isSubtask)
            {
                String[] parentTags = (new TagsDbAdapter()).getTagsInDbOrder(parent._id);
                if (parentTags.length>0)
                {
                    String defaultTags = parentTags[0];
                    for (int i=1; i<parentTags.length; i++)
                    {
                        defaultTags += ","+parentTags[i];
                    }
                    _tags.setText(defaultTags);
                }
            }
            else if (_settings.contains(PrefNames.DEFAULT_TAGS) && _settings.getString(
            	PrefNames.DEFAULT_TAGS, "").length()>0)
            {
                _tags.setText(_settings.getString(PrefNames.DEFAULT_TAGS, ""));
            }
            
            // Contact:
            if (defaultContactName.length()>0)
            {
            	_contact.setText(defaultContactName);
            }
            else
            {
            	_contactLookupKey = "";
            	_contact.setText(R.string.None);
            }
            
            if (isSubtask && parentID>0)
            {
            	// When adding a subtask, collaborators come from the parent:
            	if (parent!=null && parent.shared_with.length()>0)
            	{
                	CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
                	String[] people = parent.shared_with.split("\n");
                	String sharedWith = "";
                	UTLAccount acc = (new AccountsDbAdapter()).getAccount(parent.account_id);
                	for (int i=0; i<people.length; i++)
                	{
                		if (sharedWith.length()>0)
                			sharedWith += ", ";
                		UTLCollaborator co = cdb.getCollaborator(parent.account_id, people[i]);
                		if (co!=null)
                			sharedWith += co.name;
                		else if (people[i].equals(acc.td_userid))
                			sharedWith += Util.getString(R.string.Myself);
                	}
                	
                	// Also add in the owner of the task, if not myself:
                	if (parent.owner_remote_id.length()>0)
                	{
                		UTLCollaborator co = cdb.getCollaborator(parent.account_id, 
                			parent.owner_remote_id);
                		if (co!=null && co.remote_id!=acc.td_userid)
                		{
                    		if (sharedWith.length()>0)
                    			sharedWith += ", ";
                			sharedWith += co.name;
                		}
                	}
                	
                	if (sharedWith.length()>0)
                		_sharedWith.setText(sharedWith);
                	else
                		_sharedWith.setText(R.string.None);            		
            	}
            	else
            		_sharedWith.setText(R.string.None);
            }
        }
        else
        {
            // Defaults for the controls come from the task we're editing or cloning.
            
            // Title:
            _title.setText(t.title);
            
            // Status:
            _status.setSelection(t.status);
            
            // Start Date:
            if (t.start_date>0)
                _startDate.setText(Util.getDateString(t.start_date));
            
            // Start Time:
            if (t.start_date>0 && t.uses_start_time)
                _startTime.setText(Util.getTimeString(t.start_date));
            
            // Due Date:
            dueModifier = "";
            if (t.due_date>0)
            {
                _dueDate.setText(Util.getDateString(t.due_date));
                if (t.due_modifier.length() > 0)
                {
                    dueModifier = t.due_modifier;
                }
            }
            
            // Due Date Modifier:
            String[] dueDateMods = _res.getStringArray(R.array.advanced_due_date_options);
            if (dueModifier.equals("due_on"))
            {
            	_dueDateAdvanced.setText(dueDateMods[1]);
            }
            else if (dueModifier.equals("optionally_on"))
            {
            	_dueDateAdvanced.setText(dueDateMods[2]);
            }
            else
            {
            	_dueDateAdvanced.setText(dueDateMods[0]);
            }
            
            // Due Time:
            if (t.due_date>0 && t.uses_due_time)
                _dueTime.setText(Util.getTimeString(t.due_date));
            
            // Add to Calendar:
            if (t.calEventUri!=null && t.calEventUri.length()>0)
            	_addToCal.setChecked(true);
            else
            	_addToCal.setChecked(false);
            
            // Reminder:
            if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
            {
                if (t.uses_due_time && _settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
                {
                    if (t.reminder>0)
                    {
                        // Set the spinner value:
                        long minuteDiff = (t.due_date - t.reminder)/1000/60;
                        if (minuteDiff==0) minuteDiff = 1;
                        _a.setSpinnerSelection(_relativeReminderSpinner, Util.getReminderString(_a, 
                        	minuteDiff));
                    }
                }
                else
                {
                    if (t.reminder>0)
                    {
                        // Get the reminder time in seconds, based on the due date/time:
                        _reminderDate.setText(Util.getDateString(t.reminder));
                        _reminderTime.setText(Util.getTimeString(t.reminder));
                    }
                }
            }
            
            // Nag setting:
            _reminderNag.setChecked(t.nag);
            
            // Repeat setting:
            if (t.repeat>0)
            {
                if (t.repeat < 50)
                {
                    // Ordinary repeat setting:
                	_repeat.setSelection(t.repeat,false);
                    _repeatFrom.setSelection(0);
                    _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setVisibility(
                        View.GONE);
                }
                else if (t.repeat==50)
                {
                    // Advanced repeat setting.  The spinner is set to the "advanced" item,
                    // which is guaranteed to be the last one.
                    String repeatArray[] = this.getResources().getStringArray(
                        R.array.repeat_options);
                    _repeat.setSelection(repeatArray.length-1,false);
                    _repeatFrom.setSelection(0,false);
                    AdvancedRepeat ar = new AdvancedRepeat();
                    if (ar.initFromString(t.rep_advanced))
                    {
                    	_repeatAdvanced.setText(ar.getLocalizedString(_a));
                    	_repeatAdvanced.setTag(new String(t.rep_advanced));
                    }
                }
                else if (t.repeat<150)
                {
                    // Ordinary repeat, but from completion date
                	_repeat.setSelection(t.repeat-100,false);
                    _repeatFrom.setSelection(1);
                    _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setVisibility(
                        View.GONE);
                }
                else if (t.repeat==150)
                {
                    // Advanced repeat, but repeat from completion date.
                    String repeatArray[] = this.getResources().getStringArray(
                            R.array.repeat_options);
                    _repeat.setSelection(repeatArray.length-1,false);
                    _repeatFrom.setSelection(1);
                    AdvancedRepeat ar = new AdvancedRepeat();
                    if (ar.initFromString(t.rep_advanced))
                    {
                    	_repeatAdvanced.setText(ar.getLocalizedString(_a));
                    	_repeatAdvanced.setTag(new String(t.rep_advanced));
                    }
                }
            }
            
            // Priority:
            _priority.setSelection(t.priority);
            
            // Folder:
            if (t.folder_id>0)
            {
                // Set the spinner to show the task's current folder:
                Cursor c = (new FoldersDbAdapter()).getFolder(t.folder_id);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                }
                c.close();
            }
            
            // Context:
            if (t.context_id>0)
            {
                // Set the spinner to show the task's current context:
                Cursor c = (new ContextsDbAdapter()).getContext(t.context_id);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_context, Util.cString(c, "title"));
                }
                c.close();
            }
            
            // Tags:
            String[] tagList = (new TagsDbAdapter()).getTagsInDbOrder(t._id);
            if (tagList.length>0)
            {
                String defaultTags = tagList[0];
                for (int i=1; i<tagList.length; i++)
                {
                    defaultTags += ","+tagList[i];
                }
                _tags.setText(defaultTags);
            }
            
            // Goal:
            if (t.goal_id>0)
            {
                // Set the spinner to show the task's current goal:
                Cursor c = (new GoalsDbAdapter()).getGoal(t.goal_id);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                }
                c.close();
            }
            
            // Location:
            if (t.location_id>0)
            {
            	UTLLocation loc = (new LocationsDbAdapter()).getLocation(t.location_id);
            	if (loc != null)
            	{
            		_a.setSpinnerSelection(_location, loc.title);
            		_locReminder.setChecked(t.location_reminder);
            		_locNag.setChecked(t.location_nag);
            	}
            }
            
            // Account:
            UTLAccount acc = (new AccountsDbAdapter()).getAccount(t.account_id);
            _selectedAccountIDs = new HashSet<Long>();
            _selectedAccountIDs.add(t.account_id);
            if (acc!=null)
            {
                _accounts.setText(acc.name);
            }
            
            // Expected Length:
            if (t.length>0)
            {
                _expectedLength.setText(Integer.toString(t.length)+" "+Util.getString(
                	R.string.minutes_abbreviation));
            }
            
            // Actual Length:
            if (t.timer>0)
            {
                long minutes = Math.round(t.timer/60.0);
                _actualLength.setText(Long.toString(minutes)+" "+Util.getString(
                	R.string.minutes_abbreviation));
            }
            
            // Note:
            if (t.note.length()>0)
            {
                _note.setText(t.note);
                addLinksToNote();
            }
            
            // Star:
            if (t.star)
            {
                ImageView star = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
                star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_on_inv));
                starOn = true;
            }
            
            // Timer:
            if (t.timer_start_time>0)
            {
                timerOn = true;
                ImageView timerB = (ImageView)_rootView.findViewById(R.id.edit_task_timer_button);
                timerB.setImageResource(_a.resourceIdFromAttr(R.attr.ab_timer_running_inv));
            }
            
            // Completed:
            updateCompletedCheckbox(t.completed,t.priority);
            
            // Contact:
            if (t.contactLookupKey.length()>0 && _settings.getBoolean(PrefNames.CONTACTS_ENABLED,
                true))
            {
            	Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
            		CONTENT_LOOKUP_URI,t.contactLookupKey);
            	Cursor c = _a.managedQuery(contactUri,null,null,null,null);
            	if (c!=null && c.moveToFirst())
            	{
            		_contact.setText(Util.cString(c,ContactsContract.Contacts.DISPLAY_NAME));
            		_contactLookupKey = t.contactLookupKey;
            	}
            	else
            	{
            		_contact.setText(R.string.None);
                	_contactLookupKey = "";
            	}
            }
            else
            {
            	_contact.setText(R.string.None);
            	_contactLookupKey = "";
            }
            
            // Sharing:
            if (t.shared_with.length()>0)
            {
            	// Convert newline separated to comma separated:
            	CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
            	String[] people = t.shared_with.split("\n");
            	String sharedWith = "";
            	for (int i=0; i<people.length; i++)
            	{
            		if (_isClone && people[i].equals(acc.td_userid))
            		{
            			// When cloning, we don't include "myself" as someone we're sharing with.
            			continue;
            		}
            		
            		if (sharedWith.length()>0)
            			sharedWith += ", ";
            		UTLCollaborator co = cdb.getCollaborator(t.account_id, people[i]);
            		if (co!=null)
            			sharedWith += co.name;
            		else if (people[i].equals(acc.td_userid))
            			sharedWith += Util.getString(R.string.Myself);
            	}
            	if (sharedWith.length()>0)
            		_sharedWith.setText(sharedWith);
            	else
            		_sharedWith.setText(R.string.None);
            }
            else
            	_sharedWith.setText(R.string.None);
        }
 
        // Show or hide fields as needed:
        refreshFieldVisibility();
        
        // If we're starting the activity for the first time, then we know that no changes
        // have been made.  If it's not the first time (say, if the screen was rotated)
        // then we check the bundle passed in:
        _changesMade = false;
        if (savedInstanceState!=null && savedInstanceState.containsKey("changes_made"))
        	_changesMade = savedInstanceState.getBoolean("changes_made");
        else if (_isClone)
        	_changesMade = true; // Handles case where user clones and doesn't change anything
        
        scrollPositionY = 0;
        
        _spinnerSelections = new HashMap<Integer,Integer>();
        
        Util.logOneTimeEvent(_a, "start_editing_task", 0, new String[] {Integer.valueOf(_op).toString()}
        	);

        if (savedInstanceState!=null)
        {
        	// The user likely rotated the screen.  Call the restore function to fill in
        	// the values for controls.
        	this.onRestoreInstanceState(savedInstanceState);
        }
        
        // This function will hide the keyboard if the user taps on something that is not an EditText:
        _a.setupAutoKeyboardHiding(_rootView.findViewById(R.id.edit_task_scrollview));
        
        //
        // Define handlers for buttons, spinners, etc...
        //
        
        // Switching between tabs:
        _rootView.findViewById(R.id.edit_task_section_main_button).setOnClickListener(
            new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_rootView.findViewById(R.id.edit_task_scrollview).setVisibility(View.VISIBLE);
				_rootView.findViewById(R.id.edit_task_note_container).setVisibility(View.GONE);
				_rootView.findViewById(R.id.edit_task_section_main_underline).setBackgroundColor(
					_res.getColor(_a.resourceIdFromAttr(R.attr.task_editor_section_highlight_color)));
				_rootView.findViewById(R.id.edit_task_section_note_underline).setBackgroundColor(
					Color.TRANSPARENT);
				_tabButtonID = R.id.edit_task_section_main_button;
			}
		});
        _rootView.findViewById(R.id.edit_task_section_note_button).setOnClickListener(
            new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_rootView.findViewById(R.id.edit_task_scrollview).setVisibility(View.GONE);
				_rootView.findViewById(R.id.edit_task_note_container).setVisibility(View.VISIBLE);
				_rootView.findViewById(R.id.edit_task_section_main_underline).setBackgroundColor(
					Color.TRANSPARENT);
				_rootView.findViewById(R.id.edit_task_section_note_underline).setBackgroundColor(
					_res.getColor(_a.resourceIdFromAttr(R.attr.task_editor_section_highlight_color)));
				_tabButtonID = R.id.edit_task_section_note_button;
			}
		});
                
        // A text change listener for the title, to set the _changesMade flag.
        _title.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
				int after)
			{ }

			@Override
			public void afterTextChanged(Editable s)
			{ }

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
				int count)
			{
				_changesMade = true;
			}
        });
        
        // Expected Length:
        _rootView.findViewById(R.id.edit_task_length_container).setOnClickListener(new View.
        	OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				// Define a callback to execute when the user press the OK or Cancel
				// buttons on the dialog:
				DialogInterface.OnClickListener dialogClickListener = new 
					DialogInterface.OnClickListener()
				{					
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (which==DialogInterface.BUTTON_POSITIVE)
						{
							String newText = _editText.getText().toString().trim();
							if (newText.length()>0)
							{
								_expectedLength.setText(newText+" "+Util.getString(R.string.
									minutes_abbreviation));
							}
							else
								_expectedLength.setText(R.string.None);
							_changesMade = true;
						}
					}
				};
				
				// Get the current expected length being displayed, if any:
				String currentValue = _expectedLength.getText().toString();
				if (currentValue!=null && currentValue.length()>0 && !currentValue.equals(
					Util.getString(R.string.None)))
				{
					// A value is currently being displayed:
					int cutoffIndex = currentValue.indexOf(" "+Util.
						getString(R.string.minutes_abbreviation));
					currentValue = currentValue.substring(0, cutoffIndex);
				}
				else
				{
					// The current value is nonexistent:
					currentValue = "";
				}
	            
				// Define and display the actual dialog:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				_editText = new EditText(_a);
				_editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
					TYPE_CLASS_NUMBER);
				_editText.setText(currentValue);
				builder.setView(_editText);
				builder.setTitle(R.string.Enter_Expected_Length);
	            builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();				
			}
		});

        // Actual Length:
        _rootView.findViewById(R.id.edit_task_actual_length_container).setOnClickListener(new View.
        	OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				if (timerOn)
				{
					// Can't adjust this value if the timer is running.
					Util.popup(_a, R.string.Cannot_adjust_actual_length);
					return;
				}
				
				// Define a callback to execute when the user press the OK or Cancel
				// buttons on the dialog:
				DialogInterface.OnClickListener dialogClickListener = new 
					DialogInterface.OnClickListener()
				{					
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (which==DialogInterface.BUTTON_POSITIVE)
						{
							String newText = _editText.getText().toString().trim();
							if (newText.length()>0)
							{
								_actualLength.setText(newText+" "+Util.getString(R.string.
									minutes_abbreviation));
							}
							else
								_actualLength.setText(R.string.None);
							_changesMade = true;
						}
					}
				};
				
				// Get the current expected length being displayed, if any:
				String currentValue = _actualLength.getText().toString();
				if (currentValue!=null && currentValue.length()>0 && !currentValue.equals(
					Util.getString(R.string.None)))
				{
					// A value is currently being displayed:
					int cutoffIndex = currentValue.indexOf(" "+Util.
						getString(R.string.minutes_abbreviation));
					currentValue = currentValue.substring(0, cutoffIndex);
				}
				else
				{
					// The current value is nonexistent:
					currentValue = "";
				}
	            
				// Define and display the actual dialog:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				_editText = new EditText(_a);
				_editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
					TYPE_CLASS_NUMBER);
				_editText.setText(currentValue);
				builder.setView(_editText);
				builder.setTitle(R.string.Enter_Actual_Length);
	            builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();				
			}
		});

        // Start Date:
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_start_date_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	Intent i = new Intent(_a, DateChooser.class);
                    if (!_startDate.getText().toString().equals(Util.getString(R.string.None)) &&
                    	_startDate.getText().length()>0)
                    {
                        i.putExtra("default_date", Util.dateToMillis(_startDate.getText().toString()));
                    }
                    i.putExtra("prompt",Util.getString(R.string.Select_a_Start_Date));
                    scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(i,GET_START_DATE);
                }
            });
        }

        // Start Time:
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true) &&
            _settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_start_time_container).setOnClickListener(
            	new View.OnClickListener() 
            { 
                @Override
                public void onClick(View v) 
                {
                	int hour;  // 24-hour
                    int min;
                    String timeString = _startTime.getText().toString();
                    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
                        _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
                    
                    // Get the initial time for the time picker:
                    if (timeString.equals(Util.getString(R.string.None)) ||
                    	timeString.length()==0)
                    {
                        // Base time is the top of the next hour:
                        min = 0;
                        c.add(Calendar.HOUR, 1);
                        hour = c.get(Calendar.HOUR_OF_DAY);
                    }
                    else
                    {
                        // Base time is taken from button:
                        hour = Util.getHourFromString(timeString);
                        min = Util.getMinuteFromString(timeString);
                    }
                    
                    // Define the callback function:
                    TimePickerDialog.OnTimeSetListener handler = new TimePickerDialog.
                        OnTimeSetListener()
                    {
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute)
                        {
                            // Set the time button:
                            _startTime.setText(Util.getTimeString(hourOfDay, minute));
                            refreshFieldVisibility();
                            _changesMade = true;
                        }
                    };
                    
                    // Construct and display the time picker dialog:
                    TimePickerDialog timePicker = new TimePickerDialog(_a,handler,
                        hour,min,Util.currentTimeFormat==Util.TIME_PREF_24H);
                    timePicker.show();
                }
            });
        }        
        
        // Start Date Clear Button:
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            ImageButton startDateButton = (ImageButton)_rootView.findViewById(R.id.edit_task_start_date_clear);
            startDateButton.setOnClickListener(new View.OnClickListener() 
            { 
                @Override
                public void onClick(View v) 
                {
                    _startDate.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
        }
        
        // Start Time Clear Button:
        if (_settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_start_time_clear).setOnClickListener(new 
            	View.OnClickListener() 
            { 
                @Override
                public void onClick(View v) 
                {
                    _startTime.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
        }

        // Due Date:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_due_date_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	Intent i = new Intent(_a, DateChooser.class);
                    if (!_dueDate.getText().toString().equals(Util.getString(R.string.None)) &&
                    	_dueDate.getText().length()>0)
                    {
                        i.putExtra("default_date", Util.dateToMillis(_dueDate.getText().toString()));
                    }
                    i.putExtra("prompt",Util.getString(R.string.Select_a_Due_Date));
                    scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(i,GET_DUE_DATE);
                }
            });
        }

        // Due Time:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) &&
            _settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_due_time_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	int hour;  // 24-hour
                    int min;
                    String timeString = _dueTime.getText().toString();
                    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
                        _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
                    
                    // Get the initial time for the time picker:
                    if (timeString.equals(Util.getString(R.string.None)) ||
                    	timeString.length()==0)
                    {
                        // Base time is the top of the next hour:
                        min = 0;
                        c.add(Calendar.HOUR, 1);
                        hour = c.get(Calendar.HOUR_OF_DAY);
                    }
                    else
                    {
                        // Base time is taken from button:
                        hour = Util.getHourFromString(timeString);
                        min = Util.getMinuteFromString(timeString);
                    }
                    
                    // Define the callback function:
                    TimePickerDialog.OnTimeSetListener handler = new TimePickerDialog.
                        OnTimeSetListener()
                    {
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute)
                        {
                            // Set the time button:
                            _dueTime.setText(Util.getTimeString(hourOfDay, minute));
                            
                            // When a due time is set, then the reminder option changes
                            // from an exact time to the number of minutes before the
                            // due time.
                            refreshFieldVisibility();
                            _changesMade = true;
                        }
                    };
                    
                    // Construct and display the time picker dialog:
                    TimePickerDialog timePicker = new TimePickerDialog(_a,handler,
                        hour,min,Util.currentTimeFormat==Util.TIME_PREF_24H);
                    timePicker.show();
                }
            });
        }     
        
        // Due Date Clear Button:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_due_date_clear).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                    _dueDate.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
        }

        // Due Time Clear Button:
        if (_settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_due_time_clear).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                    _dueTime.setText(R.string.None);
                    
                    // When the due time is cleared, then the user is presented with
                    // the option of setting an exact reminder time:
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
        }

        // The "Add to Calendar" checkbox:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) || _settings.getBoolean(
        	PrefNames.START_DATE_ENABLED,true))
        {
        	_rootView.findViewById(R.id.edit_task_calendar_container).setOnClickListener(
                new View.OnClickListener()
			{
				
				@Override
				public void onClick(View arg0)
				{
					_addToCal.toggle();
				}
			});
        	
	        _addToCal.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
			{
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
				{
					_changesMade = true;
					if (isChecked)
					{
						// Make sure a calendar has been chosen:
						if (_settings.getLong(PrefNames.LINKED_CALENDAR_ID, -1)==-1)
						{
							// Get a list of calendars in the system:
							CalendarInterface ci = new CalendarInterface(_a);
							ArrayList<CalendarInfo> cals = ci.getAvailableCalendars();
							if (cals.size()==0)
							{
								Util.popup(_a, R.string.No_Calendars_Defined);
								buttonView.setChecked(false);
								return;
							}
							
							// Get arrays of calendar IDs and calendar names:
							_calNames = new String[cals.size()];
							_calendarIDs = new long[cals.size()];
							Iterator<CalendarInfo> it = cals.iterator();
							int i=0;
							int selectedIndex = 0;
							while (it.hasNext())
							{
								CalendarInfo calInfo = it.next();
								_calNames[i] = calInfo.name;
								_calendarIDs[i] = calInfo.id;
								if (_settings.getLong(PrefNames.LINKED_CALENDAR_ID,-1)==calInfo.id)
								{
									selectedIndex = i;
								}
								i++;
							}
							
							// Display the calendar names:
							AlertDialog.Builder builder = new AlertDialog.Builder(_a);
							builder.setTitle(R.string.Choose_Calendar);
							builder.setSingleChoiceItems(_calNames, selectedIndex, 
								new DialogInterface.OnClickListener()
							{
								public void onClick(DialogInterface dialog, int which)
								{
									// Update the preference and display:
									Util.updatePref(PrefNames.LINKED_CALENDAR_ID, _calendarIDs[which]);
									dialog.dismiss();
								}
							});
							builder.show();							
						}
					}
				}
			});
        }

        // Advanced due date options:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_due_date_advanced_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	// Get the index number of the default advanced option:
                    int advancedIndex = 0;
                    if (dueModifier.equals("due_on"))
                    {
                        advancedIndex = 1;
                    }
                    if (dueModifier.equals("optionally_on"))
                    {
                        advancedIndex = 2;
                    }
                    
                    // Create a dialog with the 3 options:
                    AlertDialog.Builder builder = new AlertDialog.Builder(_a);
                    String advancedArray[] = _res.getStringArray(R.array.advanced_due_date_options);
                    builder.setSingleChoiceItems(advancedArray,advancedIndex,
                        new DialogInterface.OnClickListener() 
                    {
                        // Handler for selections:
                        public void onClick(DialogInterface  dialog, int which)
                        {
                        	String[] options = _res.getStringArray(R.array.advanced_due_date_options);
                            if (which==2)
                            {
                                dueModifier = "optionally_on";
                                _dueDateAdvanced.setText(options[2]);
                            }
                            else if (which==1)
                            {
                                dueModifier = "due_on";
                                _dueDateAdvanced.setText(options[1]);
                            }
                            else
                            {
                                dueModifier = "due_by";
                                _dueDateAdvanced.setText(options[0]);
                            }
                            dialog.dismiss();
                            _changesMade = true;
                        }
                    }); 
                    AlertDialog d = builder.create();
                    d.setTitle(Util.getString(R.string.Task_is));
                    d.show();
                }
            });
        }
        
        // Reminder Date and Time:
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
            // Reminder Date:
            _rootView.findViewById(R.id.edit_task_reminder_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	Intent i = new Intent(_a, DateChooser.class);
                    if (!_reminderDate.getText().toString().equals(Util.getString(R.string.None)) 
                    	&& _reminderDate.getText().length()>0)
                    {
                        i.putExtra("default_date", Util.dateToMillis(_reminderDate.getText().toString()));
                    }
                    i.putExtra("prompt",Util.getString(R.string.Select_a_Reminder_Date));
                    scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(i,GET_REMINDER_DATE);
                }
            });
            
            // Reminder Time:
            _rootView.findViewById(R.id.edit_task_reminder_time_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	int hour;  // 24-hour
                    int min;
                    String timeString = _reminderTime.getText().toString();
                    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
                        _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
                    
                    // Get the initial time for the time picker:
                    if (timeString.equals(Util.getString(R.string.None)) ||
                    	timeString.length()==0)
                    {
                        // Base time is the top of the next hour:
                        min = 0;
                        c.add(Calendar.HOUR, 1);
                        hour = c.get(Calendar.HOUR_OF_DAY);
                    }
                    else
                    {
                        // Base time is taken from button:
                        hour = Util.getHourFromString(timeString);
                        min = Util.getMinuteFromString(timeString);
                    }
                    
                    // Define the callback function:
                    TimePickerDialog.OnTimeSetListener handler = new TimePickerDialog.
                        OnTimeSetListener()
                    {
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute)
                        {
                            // Set the time button:
                            _reminderTime.setText(Util.getTimeString(hourOfDay, minute));
                            refreshFieldVisibility();
                            _changesMade = true;
                        }
                    };
                    
                    // Construct and display the time picker dialog:
                    TimePickerDialog timePicker = new TimePickerDialog(_a,handler,
                        hour,min,Util.currentTimeFormat==Util.TIME_PREF_24H);
                    timePicker.show();
                }
            });
            
            // Reminder Date Clear Button:
            _rootView.findViewById(R.id.edit_task_reminder_clear).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                    _reminderDate.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });

            // Reminder Time Clear Button:
            _rootView.findViewById(R.id.edit_task_reminder_time_clear).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                    _reminderTime.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
            
            // Reminder spinner (when reminder is relative to due time):
            if (_relativeReminderSpinner.getSelectedItemPosition()<0)
            	_spinnerSelections.put(R.id.edit_task_reminder_list,0);
            else
            {
	            _spinnerSelections.put(R.id.edit_task_reminder_list, _relativeReminderSpinner.
	            	getSelectedItemPosition());
            }
            _relativeReminderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
            	public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
            		refreshFieldVisibility();
            		if (position!=_spinnerSelections.get(R.id.edit_task_reminder_list))
            		{
            			_changesMade = true;
            		}
                }
            	
            	public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
			});
            _rootView.findViewById(R.id.edit_task_relative_reminder_container).setOnClickListener(
            	new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					_relativeReminderSpinner.performClick();
				}
			});
            
            // Checking/unchecking the nagging reminder option:
            _rootView.findViewById(R.id.edit_task_reminder_nag_container).setOnClickListener(
                new View.OnClickListener()
			{
				@Override
				public void onClick(View arg0)
				{
					_reminderNag.toggle();
				}
			});
            _reminderNag.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
    		{
    			@Override
    			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
    			{
    				_changesMade = true;
    			}
    		});
        }
        
        // Repeat Options.  We need to handle a user's selection of the Advanced option:
        if (_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))   
        {
        	if (_repeat.getSelectedItemPosition()<0)
        		_spinnerSelections.put(R.id.edit_task_repeat2, 0);
        	else
        		_spinnerSelections.put(R.id.edit_task_repeat2, _repeat.getSelectedItemPosition());
            _repeat.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() 
            {
                public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
                    if (repeatSpinnerDisableCount==0)
                    {
                        String[] repeatOptions = _res.getStringArray(R.
                            array.repeat_options);
                        if (position==(repeatOptions.length-1))
                        {
                            // Start the activity that gets the advanced repeat string:
                            scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                            if ((System.currentTimeMillis()-_advancedRepeatBlockerTimestamp)>1500)
                                start_advanced_repeat_activity(GET_ADVANCED_REPEAT);
                            else
                            {
                                Util.log("EditTaskFragment: Advanced Repeat Popup Blocked.");
                            }
                        }
                        else
                        {
                            // The user has chosen something other than the advanced
                            // option.  Make the advanced option text invisible:
                        	refreshFieldVisibility();
                        	if (position != _spinnerSelections.get(R.id.edit_task_repeat2))
                        	{
                        		_changesMade = true;
                        	}
                        }
                    }
                    else
                    {
                    	repeatSpinnerDisableCount--;
                    }
                }
                
                public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
            });
            
            // Tapping on the overall container triggers the spinner:
            _rootView.findViewById(R.id.edit_task_repeat_container).setOnClickListener(
            	new View.OnClickListener()
        	{
        		@Override
        		public void onClick(View v)
        		{
        			_repeat.performClick();
        		}
        	});
        
            // Handle a change to the "repeat from" spinner:
            _spinnerSelections.put(R.id.edit_task_repeat_from2, _repeatFrom.
            	getSelectedItemPosition());
            _repeatFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
            	public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
            		if (position != _spinnerSelections.get(R.id.edit_task_repeat_from2))
            		{
            			_changesMade = true;
            		}
                }
            	
            	public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
			});
            
            // Tapping on the overall container triggers the spinner:
            _rootView.findViewById(R.id.edit_task_repeat_from_container).setOnClickListener(
            	new View.OnClickListener()
        	{
        		@Override
        		public void onClick(View v)
        		{
        			_repeatFrom.performClick();
        		}
        	});
        }
        
        // Advanced Repeat Option (tapping on the description):
        if (_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))   
        {
            _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                    // Start the activity that gets the advanced repeat string:
                	scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    start_advanced_repeat_activity(GET_ADVANCED_REPEAT2);
                }
            });
        }
        
        // Adding a new folder:
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
            if (_folder.getSelectedItemPosition()<0)
            	_spinnerSelections.put(R.id.edit_task_folder2,0);
            else
            	_spinnerSelections.put(R.id.edit_task_folder2, _folder.getSelectedItemPosition());
            _folder.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() 
            {
                public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
                    if (position == parent.getCount()-1)
                    {
                        // The last item (add folder) was selected.  Construct a dialog
                        // that asks for the folder's name.
                        AlertDialog.Builder alert = new AlertDialog.Builder(_a);
                        alert.setTitle(Util.getString(R.string.name_of_new_folder));
                        EditText input = new EditText(_a);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
                        	TYPE_TEXT_FLAG_CAP_SENTENCES);
                        input.setId(0);
                        alert.setView(input);
                        alert.setPositiveButton(Util.getString(R.string.Save), 
                            new DialogInterface.OnClickListener() 
                            {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Add the new folder name to the spinner, and make
                                    // it the current selection.
                                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)
                                        _folder.getAdapter();
                                    AlertDialog dialog2 = (AlertDialog)dialog;
                                    EditText editor = (EditText)dialog2.findViewById(0);
                                    adapter.insert(editor.getText().toString(),
                                        adapter.getCount()-1);
                                    _folder.setSelection(adapter.getCount()-2);
                                    _changesMade = true;
                                }
                            });
                        alert.setNegativeButton(Util.getString(R.string.Cancel),
                            new DialogInterface.OnClickListener() 
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Cancel was clicked.  The spinner must be set to
                                    // the "no folder" option.
                                    _folder.setSelection(0);
                                    _changesMade = true;
                                }
                            });
                        alert.show();
                    }
                    else if (position != _spinnerSelections.get(R.id.edit_task_folder2))
                    {
                    	_changesMade = true;
                    }
                }
                
                public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
            });
        }

        // Tapping on the overall container triggers the spinner:
        _rootView.findViewById(R.id.edit_task_folder_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_folder.performClick();
    		}
    	});
        
        // Adding a new context:
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
            if (_context.getSelectedItemPosition()<0)
            	_spinnerSelections.put(R.id.edit_task_context2,0);
            else
            	_spinnerSelections.put(R.id.edit_task_context2, _context.getSelectedItemPosition());
            _context.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() 
            {
                public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
                    if (position == parent.getCount()-1)
                    {
                        // The last item (add context) was selected.  Construct a dialog
                        // that asks for the context's name.
                        AlertDialog.Builder alert = new AlertDialog.Builder(_a);
                        alert.setTitle(Util.getString(R.string.name_of_new_context));
                        EditText input = new EditText(_a);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
                        	TYPE_TEXT_FLAG_CAP_SENTENCES);
                        input.setId(0);
                        alert.setView(input);
                        alert.setPositiveButton(Util.getString(R.string.Save), 
                            new DialogInterface.OnClickListener() 
                            {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Add the new context name to the spinner, and make
                                    // it the current selection.
                                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)
                                        _context.getAdapter();
                                    AlertDialog dialog2 = (AlertDialog)dialog;
                                    EditText editor = (EditText)dialog2.findViewById(0);
                                    adapter.insert(editor.getText().toString(),
                                        adapter.getCount()-1);
                                    _context.setSelection(adapter.getCount()-2);
                                    _changesMade = true;
                                }
                            });
                        alert.setNegativeButton(Util.getString(R.string.Cancel),
                            new DialogInterface.OnClickListener() 
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Cancel was clicked.  The spinner must be set to
                                    // the "no context" option.
                                    _context.setSelection(0);
                                    _changesMade = true;
                                }
                            });
                        alert.show();
                    }
                    else if (position != _spinnerSelections.get(R.id.edit_task_context2))
                    {
                    	_changesMade = true;
                    }
                }
                
                public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
            });
        }
        
        // Tapping on the overall container triggers the spinner:
        _rootView.findViewById(R.id.edit_task_context_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_context.performClick();
    		}
    	});
        
        // Adding a new goal:
        if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
            if (_goal.getSelectedItemPosition()<0)
            	_spinnerSelections.put(R.id.edit_task_goal2,0);
            else
            	_spinnerSelections.put(R.id.edit_task_goal2, _goal.getSelectedItemPosition());
            _goal.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() 
            {
                public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
                    if (position == parent.getCount()-1)
                    {
                        // The last item (add goal) was selected.  Construct a dialog
                        // that asks for the goal's name.
                        AlertDialog.Builder alert = new AlertDialog.Builder(_a);
                        alert.setTitle(Util.getString(R.string.name_of_new_goal));
                        EditText input = new EditText(_a);
                        input.setId(0);
                        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
                        	TYPE_TEXT_FLAG_CAP_SENTENCES);
                        alert.setView(input);
                        alert.setPositiveButton(Util.getString(R.string.Save), 
                            new DialogInterface.OnClickListener() 
                            {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Add the new goal name to the spinner, and make
                                    // it the current selection.
                                    ArrayAdapter<String> adapter = (ArrayAdapter<String>)
                                    	_goal.getAdapter();
                                    AlertDialog dialog2 = (AlertDialog)dialog;
                                    EditText editor = (EditText)dialog2.findViewById(0);
                                    adapter.insert(editor.getText().toString(),
                                        adapter.getCount()-1);
                                    _goal.setSelection(adapter.getCount()-2);
                                    _changesMade = true;
                                }
                            });
                        alert.setNegativeButton(Util.getString(R.string.Cancel),
                            new DialogInterface.OnClickListener() 
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Cancel was clicked.  The spinner must be set to
                                    // the "no goal" option.
                                    _goal.setSelection(0);
                                    _changesMade = true;
                                }
                            });
                        alert.show();
                    }
                    else if (position != _spinnerSelections.get(R.id.edit_task_goal2))
                    {
                    	_changesMade = true;
                    }
                }
                
                public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
            });
        }
        
        // Tapping on the overall container triggers the spinner:
        _rootView.findViewById(R.id.edit_task_goal_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_goal.performClick();
    		}
    	});
        
        // Adding a new location:
        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        {
            if (_location.getSelectedItemPosition()<0)
            	_spinnerSelections.put(R.id.edit_task_location2,0);
            else
            	_spinnerSelections.put(R.id.edit_task_location2, _location.getSelectedItemPosition());
            _location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
        		public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
        			refreshFieldVisibility();
        			if (position == parent.getCount()-1)
                    {
                        // The last item (add location) was selected.  Call the activity
        				// for adding a location:
        				Intent i = new Intent(_a,EditLocationActivity.class);
        				i.putExtra("action", EditLocationFragment.ADD);
        				startActivityForResult(i, NEW_LOCATION);
                    }
                    else if (position != _spinnerSelections.get(R.id.edit_task_location2))
                    {
                    	_changesMade = true;
                    }
                }
        		
        		public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
			});
        }
        
        // Tapping on the overall container triggers the spinner:
        _rootView.findViewById(R.id.edit_task_location_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_location.performClick();
    		}
    	});
        
        // Checking/unchecking the location reminder option:
        _locReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
		{
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				refreshFieldVisibilityDelay();  // Need to delay in case container tapped.
				_changesMade = true;
			}
		});
        
        // Tapping on the overall container toggles the checkbox.
        _rootView.findViewById(R.id.edit_task_location_reminder_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_locReminder.toggle();
    		}
    	});
        
        // Location Nag: Tapping on the overall container toggles the checkbox.
        _rootView.findViewById(R.id.edit_task_location_nag_container).setOnClickListener(
        	new View.OnClickListener()
    	{
    		@Override
    		public void onClick(View v)
    		{
    			_locNag.toggle();
    			_changesMade = true;
    		}
    	});
        
        // Tag button:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_tags_container).setOnClickListener(
                new View.OnClickListener()
            { 
                @Override
                public void onClick(View v) 
                {
                	// Start up the tag chooser, including any existing tags in the button:
                    Intent i = new Intent(_a, TagPicker.class);
                    if (!_tags.getText().toString().equals(Util.getString(R.string.None)))
                    {
                        String[] tagArray = _tags.getText().toString().split(",");
                        i.putExtra("selected_tags", tagArray);
                        
                        // We also need to update the current tags in the DB:
                        CurrentTagsDbAdapter db = new CurrentTagsDbAdapter();
                        db.addToRecent(tagArray);
                    }
                    scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(i,GET_TAGS);
                }
            });
        }
        
        // Accounts button:
        _rootView.findViewById(R.id.edit_task_accounts_container).setOnClickListener(new 
        	View.OnClickListener() { 
            @Override
            public void onClick(View v) 
            {
            	// We can only edit the account(s) for a new task.
                if (_op==ADD)
                {
                    // We will start up the list item picker.  Begin by getting the current
                    // selected accounts:
                    Intent i = new Intent(_a, ItemPicker.class);
                    Iterator<Long> it = _selectedAccountIDs.iterator();
                    i.putExtra("selected_item_ids", Util.iteratorToLongArray(it, _selectedAccountIDs.size()));
                    
                    // Get an array of all account IDs and names, to put into the chooser:
                    Cursor c = (new AccountsDbAdapter()).getAllAccounts();
                    i.putExtra("item_ids", Util.cursorToLongArray(c, "_id"));
                    i.putExtra("item_names", Util.cursorToStringArray(c, "name"));
                    
                    // The title for the item selector activity:
                    i.putExtra("title",Util.getString(R.string.Select_Accounts));
                    scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();                    
                    startActivityForResult(i,GET_ACCOUNTS);
                }
                else
                {
                    Util.popup(_a, R.string.account_cannot_be_changed);
                }
            }
        });

        // Sharing button:
        _rootView.findViewById(R.id.edit_task_shared_with_container).setOnClickListener(
            new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// We can't update sharing if the task is not owned by myself, or if it is a subtask.
				if (_op==EDIT)
				{
					UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);
					if (t.is_joint && !a.td_userid.equals(t.owner_remote_id))
					{
						// Get the name of the owner:
						CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
						UTLCollaborator coll = cdb.getCollaborator(t.account_id, t.owner_remote_id);
						Util.longerPopup(_a, null, Util.getString(R.string.Not_Owner)+
							" "+(coll!=null ? coll.name : ""));
						return;
					}
					if (t.parent_id>0)
					{
						// Subtasks cannot have sharing updated.
						Util.longerPopup(_a, null, Util.getString(R.string.Cannot_share_subtask));
						return;
					}
				}
				if (_op==ADD && isSubtask)
				{
					Util.longerPopup(_a, null, Util.getString(R.string.Cannot_share_subtask));
					return;
				}
				
				// To edit sharing, only a single account can be chosen:
				if (_selectedAccountIDs.size()>1)
				{
					Util.longerPopup(_a, null, Util.getString(R.string.Sharing_One_Account));
					return;
				}
				
				// Get a list of collaborators the task can be shared with, excluding myself:
				long aID = _selectedAccountIDs.iterator().next();
				CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
				UTLAccount acct = (new AccountsDbAdapter()).getAccount(aID);
				Cursor c = cdb.queryCollaborators("account_id="+aID+" and sharable=1 and "+
					"remote_id!='"+Util.makeSafeForDatabase(acct.td_userid)+"'", "name");
				if (c.getCount()==0)
				{
					Util.longerPopup(_a, null, Util.getString(R.string.no_collaborators3));
					return;
				}
				String[] collIdArray = new String[c.getCount()];
				String[] collNameArray = new String[c.getCount()];
				while (c.moveToNext())
				{
					UTLCollaborator co = cdb.cursorToCollaborator(c);
					collIdArray[c.getPosition()] = co.remote_id;
					collNameArray[c.getPosition()] = co.name;
				}
				c.close();
				
				// Get current collaborators, if any:
                Intent in = new Intent(_a, StringItemPicker.class);
				if (_sharedWith.getText().length()>0 &&
					!_sharedWith.getText().toString().equals(Util.getString(R.string.None)))
				{
					String[] currentCollNames = _sharedWith.getText().toString().split(", ");					
					String[] currentCollIDs = new String[currentCollNames.length];
					for (int i=0; i<currentCollNames.length; i++)
					{
						UTLCollaborator co = cdb.getCollaboratorByName(aID, currentCollNames[i]);
						if (co!=null)
							currentCollIDs[i] = co.remote_id;
						else
							currentCollIDs[i] = "";
					}
	                in.putExtra("selected_item_ids", currentCollIDs);
				}
				
				// Start the list item picker:
				in.putExtra("allow_no_selection", true);
                in.putExtra("item_ids", collIdArray);
                in.putExtra("item_names", collNameArray);
                in.putExtra("title",Util.getString(R.string.Select_Collaborators));
                scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();                    
                startActivityForResult(in,GET_SHARING);				
			}
		});
                
        // Star on/off:
        if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
        {
            ImageView starButton = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
            starButton.setOnClickListener(new View.OnClickListener() 
            {            
                @Override
                public void onClick(View v) 
                {
                    starOn = starOn ? false : true;
                    ImageView starB = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
                    if (starOn)
                    {
                        starB.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_on_inv));
                    }
                    else
                    {
                        starB.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_off_inv));
                    }
                    _changesMade = true;
                }
            });
            
            starButton.setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					Util.shortPopup(_a, v.getContentDescription().toString());
					return true;
				}
			});
        }
        
        // Timer on/off:
        if (_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
        {
            ImageView timerButton = (ImageView)_rootView.findViewById(R.id.edit_task_timer_button);
            timerButton.setOnClickListener(new View.OnClickListener() 
            {    
                @Override
                public void onClick(View v) 
                {
                    ImageView timerB = (ImageView)_rootView.findViewById(R.id.edit_task_timer_button);
                    timerOn = timerOn ? false : true;
                    if (timerOn)
                    {
                    	timerB.setImageResource(_a.resourceIdFromAttr(R.attr.ab_timer_running_inv));
                        Util.popup(_a, R.string.Timer_will_start);
                    }
                    else
                    {
                        Util.popup(_a, R.string.Timer_stopped);
                        timerB.setImageResource(_a.resourceIdFromAttr(R.attr.ab_timer_off_inv));
                        if (_op==EDIT)
                        {
                            // When editing, we have to stop the timer right away and
                            // update the database.
                        	if (t.timer_start_time>0)
                        	{
	                            long elapsedTimeMillis = System.currentTimeMillis() - 
	                                t.timer_start_time;
	                            if (elapsedTimeMillis<0) elapsedTimeMillis=0;
	                            t.timer_start_time = 0;
	                            t.timer = t.timer + (elapsedTimeMillis/1000);
	                            t.mod_date = System.currentTimeMillis();
	                            TasksDbAdapter db = new TasksDbAdapter();
	                            if (!db.modifyTask(t))
	                            {
	                                Util.popup(_a, R.string.DbModifyFailed);
	                                Util.log("Database modification failed after stopping timer.");
	                            }
	                            
	                            // Update the actual length on-screen:
	                            long minutes = t.timer/60;
	                            _actualLength.setText(Long.toString(minutes)+" "+Util.
	                            	getString(R.string.minutes_abbreviation));    
                        	}
                        }
                    }
                    _changesMade = true;
                }
            });
            
            timerButton.setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					Util.shortPopup(_a, v.getContentDescription().toString());
					return true;
				}
			});
        }
        
        // Checking/unchecking the completed checkbox:
        _rootView.findViewById(R.id.edit_task_completed_button).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_changesMade = true;
				boolean currentlyCompleted = getCheckboxStatus();
				if (!currentlyCompleted)
				{
					updateCompletedCheckbox(true,0);
					return;
				}
				if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
				{
					int currentPriority = _priority.getSelectedItemPosition();
					updateCompletedCheckbox(false,currentPriority);
				}
				else
				{
					updateCompletedCheckbox(false,0);
				}
			}
		});
        
        _rootView.findViewById(R.id.edit_task_completed_button).setOnLongClickListener(new View.
        	OnLongClickListener()
		{
			@Override
			public boolean onLongClick(View v)
			{
				Util.shortPopup(_a, v.getContentDescription().toString());
				return true;
			}
		});
        
        // Handler for priority spinner:
        if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        {
        	_spinnerSelections.put(R.id.edit_task_priority2, _priority.getSelectedItemPosition());
        	_priority.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
        		public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
            		if (position != _spinnerSelections.get(R.id.edit_task_priority2))
            		{
            			_changesMade = true;
            		}
        			updateCompletedCheckbox(getCheckboxStatus(),position);
                }
            	
            	public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
			});
        }
        
        // Tapping on the overall container triggers the spinner:
  		_rootView.findViewById(R.id.edit_task_priority_container).setOnClickListener(
          	new View.OnClickListener()
 		{
 			@Override
 			public void onClick(View v)
 			{
 				_priority.performClick();
 			}
 		});
  		
        // Handler for status spinner:
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
        	_spinnerSelections.put(R.id.edit_task_status2, _status.getSelectedItemPosition());
        	_status.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
			{
        		public void onItemSelected(AdapterView<?>  parent, View  view, 
                    int position, long id)
                {
            		if (position != _spinnerSelections.get(R.id.edit_task_status2))
            		{
            			_changesMade = true;
            		}
                }
            	
            	public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
			});
        }
        
        // Tapping on the overall container triggers the spinner:
 		_rootView.findViewById(R.id.edit_task_status_container).setOnClickListener(
         	new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_status.performClick();
			}
		});
     		
        // Handler for contacts selector:
        if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
        {
        	_rootView.findViewById(R.id.edit_task_contact_container).setOnClickListener(
                new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.
						Contacts.CONTENT_URI);
				    startActivityForResult(intent, GET_CONTACT);
				}
			});

        	// Handler for clearing of contact:
            ImageButton clear = (ImageButton)_rootView.findViewById(R.id.edit_task_contact_clear);
            clear.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					_contactLookupKey = "";
					_contact.setText(R.string.None);
                    _changesMade = true;
					refreshFieldVisibility();
				}
			});
            
            // Handler for contact viewing:
            ImageButton viewContact = (ImageButton)_rootView.findViewById(R.id.edit_task_contact_view);
            viewContact.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					if (_contactLookupKey!=null && _contactLookupKey.length()>0)
					{
						Intent i = new Intent(Intent.ACTION_VIEW);
						Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.
							CONTENT_LOOKUP_URI,_contactLookupKey);
						i.setData(uri);
						try
						{
							startActivity(i);
						}
						catch (ActivityNotFoundException e)
						{
							Util.popup(_a, R.string.Not_Supported_By_Device);
						}
					}
				}
			});
        }
        
        // Handler for note clear button:
        _rootView.findViewById(R.id.edit_task_note_clear).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_note.setText("");
				_noteHasLinks = false;
				_note.setFocusableInTouchMode(true);
				_noteWithoutLinks = "";
				_rootView.findViewById(R.id.edit_task_note_edit).setVisibility(View.GONE);
				_changesMade = true;
			}
		});
        
        // A focus change listener for the note.  This will remove links for the note when it has
        // focus to allow for editing.
        _note.setOnFocusChangeListener(new View.OnFocusChangeListener()
		{
			@Override
			public void onFocusChange(View v, boolean hasFocus)
			{
				if (hasFocus)
				{
					// Bring up the keyboard:
					InputMethodManager imm = (InputMethodManager)_a.getSystemService(
		        		Context.INPUT_METHOD_SERVICE);
	        		imm.showSoftInput(_note, 0);
				}
				else
				{
					addLinksToNote();
					
					// Hide the keyboard:
					InputMethodManager imm = (InputMethodManager)_a.getSystemService(
		        		Context.INPUT_METHOD_SERVICE);
		        	imm.hideSoftInputFromWindow(_note.getWindowToken(), 0);
				}
			}
		});
        
        // Handler for note edit button:
        _rootView.findViewById(R.id.edit_task_note_edit).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_note.setFocusableInTouchMode(true);
				removeLinksFromNote();
				_note.requestFocus();
				Util.popup(_a,R.string.Note_Editing_On);
			}
		});
        
        // This listener sets the _changesMade flag if the user changes the note text.
        _note.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
				int after)
			{ }

			@Override
			public void afterTextChanged(Editable s)
			{ }

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
				int count)
			{
				_changesMade = true;
			}
        });
        
        // Handler for why? button which displays if a task cannot fully sync:
        _rootView.findViewById(R.id.edit_task_sync_warning_button).setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				Util.longerPopup(_a, "", Util.getString(R.string.
					Toodledo_reminder_issue));
			}
		});
                
        // If we're adding, then prompt for a task title:
        askForTitle = false;
        if (_op==ADD && savedInstanceState==null && !_isClone && defaultTitle.length()==0)
        {
        	askForTitle = true;
        }
        
        // If we've just rotated, go to the tab that the user was last viewing.
        if (!askForTitle && savedInstanceState!=null && savedInstanceState.containsKey("tab_button_id"))
        {
        	_tabButtonID = savedInstanceState.getInt("tab_button_id");
        	_rootView.findViewById(_tabButtonID).performClick();
        }
        
        onPostResume();
        
        // Check license status and enforce free trial.
        _pm = new PurchaseManager(_a);
        int stat = _pm.stat();
    	switch (stat)
    	{
    	case PurchaseManager.IN_BETA:
    	case PurchaseManager.BETA_EXPIRED:
    		if (_pm.enforceBeta(_a))
    			return;
    		break;
    		
    	case PurchaseManager.DONT_SHOW_ADS:
    	case PurchaseManager.SHOW_ADS:
    		// Verify a purchase or check again to see if one has occurred.
    		_pm.verifyLicensePurchase(false);
    		break;
    	}

        Util.pingServer(_a);
    }
   
    // Pop up a prompt for the title if this is a new task.  The method isn't supported by fragments,
    // so it's called from onActivityCreated().
    protected void onPostResume()
    {
        // Run a sync if needed:
        Util.doMinimalSync(_a);

        if (askForTitle)
    		promptForTitle();
    	askForTitle = false;
    	
    	
    }
    
    // Set the header title at the top of the screen or fragment:
    private void setTitle(int resID)
    {
    	if (_isOnlyFragment)
        	_a.getSupportActionBar().setTitle(resID);
        else
        {
        	_saveCancelBar.setTitle(_a.getString(resID));
        }
    }

    // Called just after onCreate() when the activity is restored after an orientation 
    // change.  Not supported by fragments, but will be called from onActivityCreated().
    protected void onRestoreInstanceState(Bundle b)
    {
    	// Restore the content of views that are not saved automatically:
    	Util.restoreInstanceState(_a, _viewIDsToSave, b);
    	
    	// Add links to the note field:
    	addLinksToNote();
    	
    	// Restore account IDs:
    	if (b.containsKey("account_ids"))
    	{
	    	_selectedAccountIDs.clear();
	    	long[] accountIDs = b.getLongArray("account_ids");
	    	for (int i=0; i<accountIDs.length; i++)
	    		_selectedAccountIDs.add(accountIDs[i]);
    	}
    	
    	// Restore star:
    	ImageView star = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
    	if (star!=null)
    	{
	    	if (b.containsKey("star_on") && b.getBoolean("star_on"))
	    	{
	    		star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_on_inv));
	    		starOn = true;
	    	}
	    	else
	    	{
	    		star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_off_inv));
	    		starOn = false;
	    	}
    	}
    	
    	// Restore timer:
    	ImageView timer = (ImageView)_rootView.findViewById(R.id.edit_task_timer_button);
    	if (timer!=null)
    	{
	    	if (b.containsKey("timer_on") && b.getBoolean("timer_on"))
	    	{
	    		timer.setImageResource(_a.resourceIdFromAttr(R.attr.ab_timer_running_inv));
	    		timerOn = true;
	    	}
	    	else
	    	{
	    		timer.setImageResource(_a.resourceIdFromAttr(R.attr.ab_timer_off_inv));
	    		timerOn = false;
	    	}
    	}
    	
    	// Restore completion state:
    	if (b.containsKey("is_completed"))
    	{
    		updateCompletedCheckbox(b.getBoolean("is_completed"),_priority.getSelectedItemPosition());
    	}
    	
    	// Restore due modifier:
    	if (b.containsKey("due_modifier"))
    		dueModifier = b.getString("due_modifier");
    	    	
    	refreshFieldVisibility();

    	// Restore advanced repeat setting:
    	if (b.containsKey("repeat_index") && _repeatAdvanced!=null)
    	{
    		int repeatIndex = b.getInt("repeat_index");

    		ViewGroup advancedContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_repeat_advanced_container);
    		ViewGroup repeatFromContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_repeat_from_container);
    		String[] repeatOpts = _res.getStringArray(R.array.repeat_options);
    		if (repeatIndex == repeatOpts.length-1)
    		{
    			// It's on the "advanced" option:
    			advancedContainer.setVisibility(View.VISIBLE);
    			if (b.containsKey("advanced_repeat") && b.containsKey("advanced_repeat_tag"))
    			{
    				_repeatAdvanced.setText(b.getString("advanced_repeat"));
    				_repeatAdvanced.setTag(new String(b.getString("advanced_repeat_tag")));
    			}
    			else
    				_repeatAdvanced.setText(R.string.None);
    			
    			// Need to block the spinner's change listener from firing:
    			if (_isOnlyFragment)
    				repeatSpinnerDisableCount++;
    			else
    				repeatSpinnerDisableCount+=2;
    		}
    		else
    			advancedContainer.setVisibility(View.GONE);
    		if (repeatIndex>0)
    			repeatFromContainer.setVisibility(View.VISIBLE);
    		else
    			repeatFromContainer.setVisibility(View.GONE);    		
    	}	
    	
    	// Restore contact:
    	if (b.containsKey("contact_lookup_key"))
    	{
	    	_contactLookupKey = b.getString("contact_lookup_key");
	    	if (_contactLookupKey.length()>0)
	    	{
		    	Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
		    		CONTENT_LOOKUP_URI,_contactLookupKey);
		    	Cursor c = _a.managedQuery(contactUri,null,null,null,null);
		    	if (c!=null && c.moveToFirst())
		    	{
		    		_contact.setText(Util.cString(c,ContactsContract.Contacts.DISPLAY_NAME));
		    	}
		    	else
		    	{
		    		_contact.setText(R.string.None);
		    	}
	    	}
	    	else
	    	{
	    		_contact.setText(R.string.None);
	    	}
    	}

    	// If this is an add operation and the title is blank, then prompt for a title:
    	TextView title = (TextView)_rootView.findViewById(R.id.edit_task_title2);
    	askForTitle = false;
    	if (b!=null && _op==ADD && (title.getText().length()==0 ||
    		title.getText().toString().equals(Util.getString(R.string.None))))
    	{
    		askForTitle = true;
    	}
    }

    // Hide or show certain fields based on whether other fields have a value:
    private void refreshFieldVisibility()
    {
    	String none = Util.getString(R.string.None);
    	
    	// Start Time:
    	ViewGroup startTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
    		edit_task_start_time_container);
    	if (_startDate.getText().toString().equals(none) ||
    		!_settings.getBoolean(PrefNames.START_DATE_ENABLED,true) ||
    		!_settings.getBoolean(PrefNames.START_TIME_ENABLED,true))
    	{
    		startTimeContainer.setVisibility(View.GONE);
    	}
    	else
    		startTimeContainer.setVisibility(View.VISIBLE);
    	
    	// Due date advanced option:
    	ViewGroup dueDateAdvancedContainer = (ViewGroup)_rootView.findViewById(R.id.
    		edit_task_due_date_advanced_container);
    	if (_dueDate.getText().toString().equals(none) ||
    		!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED,true))
    	{
    		dueDateAdvancedContainer.setVisibility(View.GONE);
    	}
    	else
    	{
    		dueDateAdvancedContainer.setVisibility(View.VISIBLE);
    	}
    	
    	// Due Time:
    	ViewGroup dueTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
    		edit_task_due_time_container);
    	if (_dueDate.getText().toString().equals(none) ||
    		!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED,true) ||
    		!_settings.getBoolean(PrefNames.DUE_TIME_ENABLED,true))
    	{
    		dueTimeContainer.setVisibility(View.GONE);
    	}
    	else
    		dueTimeContainer.setVisibility(View.VISIBLE);
    	
    	// Repeat options:
    	if (_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))
    	{
    		ViewGroup advancedContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_repeat_advanced_container);
    		ViewGroup repeatFromContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_repeat_from_container);
    		int repeatIndex = _repeat.getSelectedItemPosition();
    		if (repeatIndex == _repeat.getCount()-1)
    		{
    			// It's on the "advanced" option:
    			advancedContainer.setVisibility(View.VISIBLE);
    		}
    		else
    			advancedContainer.setVisibility(View.GONE);
    		if (repeatIndex>0)
    			repeatFromContainer.setVisibility(View.VISIBLE);
    		else
    			repeatFromContainer.setVisibility(View.GONE);
    	}
    	
    	// Add to calendar option:
    	if (_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    	{
    		if (_startDate.getText().toString().equals(none) && _dueDate.getText().
    			toString().equals(none))
    		{
    			// Neither the start or due date are specified, so we can't add to
    			// calendar.
    			_rootView.findViewById(R.id.edit_task_calendar_container).setVisibility(View.GONE);
    		}
    		else
    			_rootView.findViewById(R.id.edit_task_calendar_container).setVisibility(View.VISIBLE);
    	}
    	else
    	{
    		// Hide the calendar check box:
    		_rootView.findViewById(R.id.edit_task_calendar_container).setVisibility(View.GONE);
    	}
    	
    	// Reminder options:
    	if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
    	{
    		ViewGroup relativeReminderContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_relative_reminder_container);
    		ViewGroup reminderDateContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_reminder_container);
    		ViewGroup reminderTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_reminder_time_container);
    		ViewGroup nagContainer = (ViewGroup)_rootView.findViewById(R.id.
    			edit_task_reminder_nag_container);
    		if (dueTimeContainer.getVisibility()==View.VISIBLE && !_dueTime.getText().
    			toString().equals(none) && _dueTime.getText().length()>0)
    		{
    			// Due time is set.  Show Reminder as spinner:
    			relativeReminderContainer.setVisibility(View.VISIBLE);
    			reminderDateContainer.setVisibility(View.GONE);
    			reminderTimeContainer.setVisibility(View.GONE);
    			
    			// If a reminder is actually set, then show the nag option:
    			if (_relativeReminderSpinner.getSelectedItemPosition()>0)
    				nagContainer.setVisibility(View.VISIBLE);
    			else
    				nagContainer.setVisibility(View.GONE);
    		}
    		else
    		{
    			// Due time is not set.  Show Reminder as explicit date/time:
    			relativeReminderContainer.setVisibility(View.GONE);
    			reminderDateContainer.setVisibility(View.VISIBLE);
    			
    			// If a reminder date is set, then show the nag and reminder time options:
    			if (!_reminderDate.getText().toString().equals(none))
    			{
    	   			reminderTimeContainer.setVisibility(View.VISIBLE);
    	   			nagContainer.setVisibility(View.VISIBLE);
    			}
    			else
    			{
    	   			reminderTimeContainer.setVisibility(View.GONE);
    	   			nagContainer.setVisibility(View.GONE);
    			}
    		}
    	}
    	
    	// Warning about reminder not syncing:
    	int visibility = View.GONE;
    	if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true) && _settings.
    		getBoolean(PrefNames.NO_SYNC_WARNING, true) && _settings.getBoolean(
    		PrefNames.DUE_DATE_ENABLED, true) && _dueDate.getText().toString().equals(none) && 
    		!_reminderDate.getText().toString().equals(none))
    	{
    		Iterator<Long> it = _selectedAccountIDs.iterator();
    		AccountsDbAdapter db = new AccountsDbAdapter();
    		while (it.hasNext())
    		{
    			UTLAccount a = db.getAccount(it.next());
    			if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    			{
    				visibility = View.VISIBLE;
    				break;
    			}
    		}
    	}
    	_rootView.findViewById(R.id.edit_task_sync_warning_container).setVisibility(visibility);
    	
    	// Location fields:
    	if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		if (_location.getSelectedItemPosition()>0)
    		{
    			// A location is selected, so make the reminder settings visible:
    			_rootView.findViewById(R.id.edit_task_location_reminder_container).setVisibility
					(View.VISIBLE);
    			if (_locReminder.isChecked())
    			{
    				// Location reminder is enabled.  Make the nag setting visible:
    				_rootView.findViewById(R.id.edit_task_location_nag_container).setVisibility
						(View.VISIBLE);
    			}
    			else
    			{
    				// Location reminder is disabled.  Make nag setting invisible:
    				_rootView.findViewById(R.id.edit_task_location_nag_container).setVisibility
						(View.GONE);
    			}
    		}
    		else
    		{
    			// A location is not selected, so make the reminder & nag settings invisible:
    			_rootView.findViewById(R.id.edit_task_location_reminder_container).setVisibility
    				(View.GONE);
    			_rootView.findViewById(R.id.edit_task_location_nag_container).setVisibility
					(View.GONE);
    		}
    	}
    	
    	// Contact:
    	if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
    	{
    		if (_contact.getText()!=null && _contact.getText().toString().length()>0
    			&& !_contact.getText().toString().equals(Util.getString(R.string.None)))
    		{
    			_rootView.findViewById(R.id.edit_task_contact_view).setVisibility(View.VISIBLE);
    		}
    		else
    			_rootView.findViewById(R.id.edit_task_contact_view).setVisibility(View.GONE);
    	}
    	
    	// Sharing:
    	if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		// Show the sharing options if at least one compatible account is chosen:
    		Iterator<Long> it = _selectedAccountIDs.iterator();
    		AccountsDbAdapter adb = new AccountsDbAdapter();
    		boolean showSharing = false;
    		while (it.hasNext())
    		{
    			long accountID = it.next();
    			UTLAccount a = adb.getAccount(accountID);
    			if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    				showSharing = true;
    		}
    		if (showSharing)
    			_rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.VISIBLE);
    		else
    			_rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.GONE);
    	}
    	else
    		_rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.GONE);   
    	
    	// Go through each section.  Make sure the last item does not show a bottom border, and all
    	// of the rest do show the border.
    	int[] sectionIDs = {R.id.edit_task_timing_table, R.id.edit_task_reminder_table,
    		R.id.edit_task_repeat_table, R.id.edit_task_classification_table, 
    		R.id.edit_task_location_table, R.id.edit_task_length_table, R.id.edit_task_contact_table};
    	int i;
    	for (i=0; i<sectionIDs.length; i++)
    	{
    		ViewGroup vg = (ViewGroup)_rootView.findViewById(sectionIDs[i]);
    		if (vg.getVisibility()==View.VISIBLE)
    		{
	    		int numChildren = vg.getChildCount();
	    		int j;
	    		boolean lastVisibleEncountered = false;
	    		for (j=(numChildren-1); j>=0; j--)
	    		{
	    			View v = vg.getChildAt(j);
	    			if (v.getVisibility()==View.VISIBLE)
	    			{
	    				if (!lastVisibleEncountered)
	    				{
	    					v.setBackgroundResource(_a.resourceIdFromAttr(android.R.attr.
                                selectableItemBackground));
	    					lastVisibleEncountered = true;
	    				}
	    				else
	    					v.setBackgroundResource(_a.resourceIdFromAttr(R.attr.editor_divider));
	    			}
	    		}
    		}
    	}
    }
    
    // Populate the action bar buttons and menu:
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	if (!_isOnlyFragment)
    	{
    		// If there are other fragments on the screen, we do not populate the action bar.
    		return;
    	}
    	
    	menu.clear();
    	inflater.inflate(R.menu.save_cancel, menu);
    	
    	if (_op==EDIT)
    	{
	    	// Add in additional menu items:
    		MenuItemCompat.setShowAsAction(menu.add(0, MENU_DELETE, 0, R.string.Delete).setIcon(
                _a.resourceIdFromAttr(R.attr.ab_delete)), MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
            if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true) && t.parent_id>0)
            {
                MenuItemCompat.setShowAsAction(menu.add(0, MENU_EDIT_PARENT, 0, R.string.
                    edit_parent_task), MenuItemCompat.SHOW_AS_ACTION_NEVER);
            }
    	}
    }
    
    // Add links to the task's note:
    private void addLinksToNote()
    {
        _noteWithoutLinks = _note.getText().toString();
        _noteHasLinks = Linkify.addLinks(_note, Linkify.ALL);
        if (_noteHasLinks)
        {
            // If the note has links, disable the edittext control until the user taps on the edit
            // button.
            _note.setFocusable(false);
            _rootView.findViewById(R.id.edit_task_note_edit).setVisibility(View.VISIBLE);
        }
        else
        {
            _note.setFocusableInTouchMode(true);
            _rootView.findViewById(R.id.edit_task_note_edit).setVisibility(View.GONE);
        }
    }
    
    // Update the completed checkbox:
    private void updateCompletedCheckbox(boolean isCompleted, int priority)
    {
    	ImageButton cb = (ImageButton)_rootView.findViewById(R.id.edit_task_completed_button);
    	if (isCompleted)
    	{
    		cb.setImageDrawable(_res.getDrawable(R.drawable.checkbox_checked));
    	}
    	else
    	{
    		cb.setImageDrawable(_res.getDrawable(TaskListFragment._priorityCheckboxDrawables[priority]));
    	}
		cb.setTag(Boolean.valueOf(isCompleted));
    }
    
    // Prompt the user for a title:
    private void promptForTitle()
    {
		// Bring up the keyboard when the title is focused:
		_title.setOnFocusChangeListener(new View.OnFocusChangeListener() {
		    @Override
		    public void onFocusChange(View view, boolean focused)
		    {
		        if (focused)
		        {
		        	try
		        	{
		        		InputMethodManager imm = (InputMethodManager)_a.getSystemService(
			        		Context.INPUT_METHOD_SERVICE);
		        		imm.showSoftInput(_title, 0);
		        		
		        	}
		        	catch (IllegalArgumentException e)
		        	{
		        		// For reasons unknown, we get a "view not attached to window manager" failure
		        		// here on rare occasions.  Since popping up the keyboard is nonessential,
		        		// we can handle this exception by ignoring it.  This ensures the user
		        		// does not see an error message.
		        	}
		        }
		        else
		        {
		        	// When the title is defocused, hide the keyboard.
		        	InputMethodManager imm = (InputMethodManager)_a.getSystemService(
		        		Context.INPUT_METHOD_SERVICE);
		        	imm.hideSoftInputFromWindow(_title.getWindowToken(), 0);
		        }
		    }
		});
    	
		_title.setFocusableInTouchMode(true);
		_title.requestFocus();
    }
    
    // Refresh the location spinner.  The 2 inputs here may be null.
    private void refreshLocationSpinner(Bundle savedInstanceState, String defaultLocName)
    {
        
    	ArrayAdapter<String> locSpinnerAdapter = _a.initSpinner(_rootView, 
    		R.id.edit_task_location2);
    	if (savedInstanceState!=null && savedInstanceState.containsKey("locations"))
        {
        	String[] spinnerItems = savedInstanceState.getStringArray("locations");
        	for (int i=0; i<spinnerItems.length; i++)
        	{
        		locSpinnerAdapter.add(spinnerItems[i]);
        	}
        }
    	else if (_op==ADD)
    	{
    		// Retrieve all locations from all accounts, removing duplicate names and 
    		// sorting.
            SortedSet<String> locNames = new TreeSet<String>();
            Cursor c = (new LocationsDbAdapter()).getAllLocations();
            while (c.moveToNext())
            {
                locNames.add(Util.cString(c, "title"));
            }
            c.close();

            // Go through the items, and add to the spinner:
            String[] sortedNames = Util.iteratorToStringArray(locNames.iterator(),
            	locNames.size());
            Arrays.sort(sortedNames, String.CASE_INSENSITIVE_ORDER);
            for (int i=0; i<sortedNames.length; i++)
            {
                locSpinnerAdapter.add(sortedNames[i]);
            }
            locSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            locSpinnerAdapter.add(Util.getString(R.string.Add_Location)); // "Add Location"
    	}
    	else
    	{
    		// Only get locations from the task's account:
    		Cursor c = (new LocationsDbAdapter()).queryLocations("account_id="+
    			t.account_id, "title");
    		while (c.moveToNext())
    		{
    			locSpinnerAdapter.add(Util.cString(c, "title"));
    		}
    		c.close();
    		locSpinnerAdapter.insert(Util.getString(R.string.None),0); // Insert "none".
            locSpinnerAdapter.add(Util.getString(R.string.Add_Location)); // "Add Location"
    	}
    	
    	// Set the spinner title:
        _location.setPromptId(R.string.Location);
        
        // Set the default selection, if specified:
        if (defaultLocName!=null && defaultLocName.length()>0)
        	_a.setSpinnerSelection(_location, defaultLocName);
        else
        	_location.setSelection(0);
    }
    
    // Handlers for action bar items:
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	// Do nothing if resize mode is on:
    	if (inResizeMode())
    		return super.onOptionsItemSelected(item);
    	
    	switch (item.getItemId())
    	{
    	case R.id.menu_save:
    		saveAndReturn();
    		return true;
    		
    	case R.id.menu_cancel:
    		handleCancelButton();
    		return true;
    		
    	case MENU_DELETE:
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
						  Util.deleteTask(t._id);
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
          	  builder.setTitle(t.title);
          	  builder.show();
    		  return true;
    		  
    	case android.R.id.home:
    		// If changes have been made, ask the user if they should be saved.
    		if (_isOnlyFragment)
    		{
            	confirmExitWithoutSave(new ExitWithoutSaveHandler() {

    				@Override
    				public void onExitWithoutSave()
    				{
    					_a.setResult(Activity.RESULT_CANCELED);
                        _a.finish(); 
    				}
            	});
                return true;
    		}

            case MENU_EDIT_PARENT:
                if (inResizeMode()) return true;

                if (_isOnlyFragment)
                {
                    Intent i;
                    if (_a.getClass().getName().endsWith("EditTaskPopup"))
                    {
                        // The task editor is in a popup window, so the new editor should also be.
                        i = new Intent(_a,EditTaskPopup.class);
                    }
                    else
                        i = new Intent(_a,EditTask.class);
                    i.putExtra("action", EditTaskFragment.EDIT);
                    i.putExtra("id", t.parent_id);
                    startActivity(i);
                }
                else
                {
                    // In split-screen mode, display the task editor in a separate fragment:
                    EditTaskFragment frag = new EditTaskFragment();
                    Bundle args = new Bundle();
                    args.putInt("action", EditTaskFragment.EDIT);
                    args.putLong("id", t.parent_id);
                    frag.setArguments(args);
                    UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
                    nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG +
                        "/" + t.parent_id);
                }
                return true;
    	}

    	return super.onOptionsItemSelected(item);
    }

    // Remove links from the note.  Needed for editing:
    private void removeLinksFromNote()
    {
        _note.setText(_noteWithoutLinks);
        _rootView.findViewById(R.id.edit_task_note_edit).setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT>=11)
            _note.setTextIsSelectable(true);
    }
    
    // Get the completion status of the completed checkbox:
    private boolean getCheckboxStatus()
    {
    	ImageButton cb = (ImageButton)_rootView.findViewById(R.id.edit_task_completed_button);
    	Boolean isCompleted = (Boolean)cb.getTag();
    	if (isCompleted==null)
    	{
    		// This can happen as the Activity is being initialized.  If we're editing, get the status
    		// from the task.  If we're adding, then it's false.
    		if (_op==EDIT && t!=null)
    			return t.completed;
    		else
    			return false;
    	}
    	return isCompleted;
    }
    
    // Handlers for activity results:
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        
        // Get extras in the response, if any:
        Bundle extras = new Bundle();
        if (intent != null)
        {
            extras = intent.getExtras();
        }
        
        switch(requestCode)
        {
        case GET_START_DATE:
            if (resultCode==Activity.RESULT_OK && extras.containsKey("chosen_date"))
            {
                _startDate.setText(Util.getDateString(extras.getLong("chosen_date")));
                _changesMade = true;
                refreshFieldVisibility();
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_DUE_DATE:
            if (resultCode==Activity.RESULT_OK && extras.containsKey("chosen_date"))
            {
            	_dueDate.setText(Util.getDateString(extras.getLong("chosen_date")));
                _changesMade = true;
                refreshFieldVisibility();
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_REMINDER_DATE:
            if (resultCode==Activity.RESULT_OK && extras.containsKey("chosen_date"))
            {
                _reminderDate.setText(Util.getDateString(extras.getLong("chosen_date")));
                _changesMade = true;
                
                // If the time button does not have a time entered, put in a default time:
                if (_reminderTime.getText().toString().equals(Util.getString(R.string.None)))
                {
                	if (Util.currentTimeFormat==Util.TIME_PREF_12H)
                		_reminderTime.setText("8:00 AM");
                	else
                		_reminderTime.setText("8:00");
                }
                
                refreshFieldVisibility();
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_ADVANCED_REPEAT:
            // Done with advanced repeat, after user chose spinner item
            if (resultCode==Activity.RESULT_OK && extras.containsKey("text"))
            {
            	// Create an AdvancedRepeat object to hold the result.
            	AdvancedRepeat ar = new AdvancedRepeat();
            	if (ar.initFromString(extras.getString("text")))
            	{
	                // Set the text of the advanced repeat TextView:
	                _repeatAdvanced.setText(ar.getLocalizedString(_a));
	                
	                // Store a standard string to use when inserting into the database:
	                _repeatAdvanced.setTag(new String(extras.getString("text")));
	                
	                // Make the advanced repeat TextView visible:
	                _repeatAdvanced.setVisibility(View.VISIBLE);
	                _changesMade = true;
	                refreshFieldVisibilityDelay();
            	}
            }
            else
            {
                // The user canceled, so set the repeat spinner to "no repeat"
            	_repeat.setSelection(0);
            	refreshFieldVisibilityDelay();
                _changesMade = true;
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_ADVANCED_REPEAT2:
            // Done with advanced repeat, after user tapped on advanced repeat text
            if (resultCode==Activity.RESULT_OK && extras.containsKey("text"))
            {
            	// Create an AdvancedRepeat object to hold the result.
            	AdvancedRepeat ar = new AdvancedRepeat();
            	if (ar.initFromString(extras.getString("text")))
            	{
	                // Set the text of the advanced repeat TextView:
	                _repeatAdvanced.setText(ar.getLocalizedString(_a));
	                
	                // Store a standard string to use when inserting into the database:
	                _repeatAdvanced.setTag(new String(extras.getString("text")));
	                
	                // Make the advanced repeat TextView visible:
	                _repeatAdvanced.setVisibility(View.VISIBLE);
	                _changesMade = true;
	                refreshFieldVisibilityDelay();
            	}
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_TAGS:
            if (resultCode==Activity.RESULT_OK && extras.containsKey("selected_tags"))
            {
                String[] tags = extras.getStringArray("selected_tags");
                if (tags.length>0)
                {
                    // Update the button text:
                    String buttonText = tags[0];
                    for (int i=1; i<tags.length; i++)
                    {
                        buttonText += ","+tags[i];
                    }
                    
                    // The list of tags (with commas) cannot exceed 64 characters:
                    if (buttonText.length()>64)
                    {
                    	Util.popup(_a, R.string.Tag_List_Too_Long);
                    	return;
                    }
                    
                    _tags.setText(buttonText);
                    
                    // Make sure any selected tags are in the current tags list:
                    CurrentTagsDbAdapter db = new CurrentTagsDbAdapter();
                    db.addToRecent(tags);
                }
                else
                {
                    // Clear all tags by setting the button text to <none>:
                    _tags.setText(Util.getString(R.string.None));
                }
                _changesMade = true;
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case GET_ACCOUNTS:
            if (resultCode==Activity.RESULT_OK && extras.containsKey("selected_item_ids"))
            {
                // Get the account IDs from the response and update the button text:
                long[] accountIDs = extras.getLongArray("selected_item_ids");
                AccountsDbAdapter db = new AccountsDbAdapter();
                if (accountIDs.length==0)
                {
                    // This should not happen.
                    Util.log("ERROR: item picker returned an empty array");
                }
                else
                {
                    UTLAccount a = db.getAccount(accountIDs[0]);
                    String buttonText = "";
                    if (a==null)
                    {
                        Util.log("ERROR: Bad account with ID of "+accountIDs[0]+
                            " passed back.");
                        return;
                    }
                    else
                    {
                        buttonText = a.name;
                    }
                    for (int i=1; i<accountIDs.length; i++)
                    {
                        a = db.getAccount(accountIDs[i]);
                        if (a!=null)
                        {
                            buttonText += ","+a.name;
                        }
                    }
                    _accounts.setText(buttonText);
                    
                    // Store the selected account IDs:
                    _selectedAccountIDs.clear();
                    for (int i=0; i<accountIDs.length; i++)
                    {
                        _selectedAccountIDs.add(accountIDs[i]);
                    }
                    _changesMade = true;
                    
                    refreshFieldVisibility();
                }
            }
            // Scroll to the same spot we were in before the button was pressed:
            _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,scrollPositionY);
            break;
        case NEW_LOCATION:
        	if (resultCode==Activity.RESULT_OK)
        	{
        		if (extras!=null && extras.containsKey("location_name"))
        			refreshLocationSpinner(null, extras.getString("location_name"));
        		else
        			refreshLocationSpinner(null,null);
        	}
        	else
        		_location.setSelection(0);
        	refreshFieldVisibility();
        	_changesMade = true;
        	break;
        	
        case GET_CONTACT:
        	if (resultCode==Activity.RESULT_OK && intent!=null && intent.getData()!=null)
        	{
        		Uri contactData = intent.getData();
                Cursor c = _a.managedQuery(contactData,null,null,null,null);
                if (c!=null && c.moveToFirst())
                {
                    _contact.setText(c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.Contacts.DISPLAY_NAME)));
                    _contactLookupKey = c.getString(c.getColumnIndexOrThrow(
                        ContactsContract.Contacts.LOOKUP_KEY));
                }
                refreshFieldVisibility();
        	}
        	break;
        	
        case GET_SHARING:
        	if (resultCode==Activity.RESULT_OK && extras.containsKey("selected_item_ids"))
            {
        		// Get the collaborator IDs from the response and update the button text:
                String[] collIDs = extras.getStringArray("selected_item_ids");
                CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
                String sharedWith = "";
                for (int i=0; i<collIDs.length; i++)
                {
                	if (i>0)
            			sharedWith += ", ";
            		UTLCollaborator co = cdb.getCollaborator(_selectedAccountIDs.iterator().next(), 
            			collIDs[i]);
            		if (co!=null)
            			sharedWith += co.name;
                }
                _sharedWith.setText(sharedWith);
                if (collIDs.length==0)
                	_sharedWith.setText(R.string.None);
            }
        	break;
        }
    }
    
    // Start the activity to get an advanced repeat string:
    protected void start_advanced_repeat_activity(int identifier)
    {
        Intent i = new Intent(_a, AdvancedRepeatPopup.class);
        if (!_repeatAdvanced.getText().toString().equals("Advanced Repeat String") &&
        	_repeatAdvanced.getText().length()>0 && !_repeatAdvanced.getText().toString().equals(
            Util.getString(R.string.None)))
        {
        	if (_repeatAdvanced.getTag()!=null)
        		i.putExtra("text", (String)_repeatAdvanced.getTag());
        }
        startActivityForResult(i,identifier);
    }
    
    // An AsyncTask instance whose purpose is to refresh the field visibility after
    // a delay.  This compensates for an issue in which spinner positions are not
    // correctly reported in the onRestoreInstanceState function.
    private class RefreshFieldVisibilityDelay extends AsyncTask<Void,Void,Void>
    {
    	protected Void doInBackground(Void... v)
    	{
    		try
    		{
    			Thread.sleep(250);
    		}
	    	catch (InterruptedException e)
	    	{
	    	}
	    	return null;
    	}
    	
    	protected void onPostExecute(Void v)
    	{
    		refreshFieldVisibility();
    	}
    }    

    @SuppressLint("NewApi")
	private void refreshFieldVisibilityDelay()
    {
    	if (Build.VERSION.SDK_INT >= 11)
    		new RefreshFieldVisibilityDelay().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    	else
    		new RefreshFieldVisibilityDelay().execute();
    }
    
    // Check to see if the parent activity is is resize mode.  In resize mode, we can't execute any 
    // commands:
    private boolean inResizeMode()
    {
    	if (_isOnlyFragment)
    		return false;
    	
    	if (_a instanceof TaskList && _ssMode!=Util.SS_NONE)
    	{
    		UtlNavDrawerActivity n = (UtlNavDrawerActivity)_a;
    		return n.inResizeMode();
    	}
    	else
    		return false;
    }
    
    // Change the folders of child tasks to match the parent task:
    private void changeSubtaskFolders(UTLTask parent)
    {
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	Cursor c = tasksDB.queryTasks("parent_id="+parent._id, null);
    	c.moveToPosition(-1);
    	while (c.moveToNext())
    	{
    		UTLTask child = tasksDB.getUTLTask(c);
    		child.folder_id = parent.folder_id;
    		child.mod_date = System.currentTimeMillis();
    		tasksDB.modifyTask(child);
    		changeSubtaskFolders(child);
    	}
    	c.close();
    }
    
    // Called before the activity is destroyed due to orientation change:
    @Override
    public void onSaveInstanceState(Bundle b)
    {
    	int i;
    	
    	// Save the content of views that are not saved automatically:
    	Util.saveInstanceState(_a, _viewIDsToSave, b);
    	
    	// Save items not covered by previous function:
    	b.putLongArray("account_ids",Util.iteratorToLongArray(_selectedAccountIDs.iterator(), 
    		_selectedAccountIDs.size()));
    	b.putBoolean("star_on", starOn);
    	b.putBoolean("timer_on", timerOn);
    	b.putString("due_modifier",dueModifier);
    	if (_repeat!=null)
    	{
	    	int repeatIndex = _repeat.getSelectedItemPosition();
	    	b.putInt("repeat_index", repeatIndex);
	    	if (repeatIndex==_repeat.getCount()-1)
	    	{
	    		if (_repeatAdvanced!=null)
	    		{
	    			b.putString("advanced_repeat", _repeatAdvanced.getText().toString());
	    			if (_repeatAdvanced.getTag()!=null)
	    				b.putString("advanced_repeat_tag", (String)_repeatAdvanced.getTag());
	    		}
	    	}
    	}
    	
    	// The completion state:
    	b.putBoolean("is_completed", getCheckboxStatus());
    	
    	// Save the items in the folder, context, and goal spinners (because they 
    	// may be have changed due to the user adding items):
    	
    	// Folders:
    	String[] list = new String[_folder.getCount()];
    	for (i=0; i<_folder.getCount(); i++)
    	{
    		list[i] = (String)_folder.getItemAtPosition(i);
    	}
    	b.putStringArray("folders", list);
    	
    	// Contexts:
    	list = new String[_context.getCount()];
    	for (i=0; i<_context.getCount(); i++)
    	{
    		list[i] = (String)_context.getItemAtPosition(i);
    	}
    	b.putStringArray("contexts", list);
    	
    	// Goals:
    	list = new String[_goal.getCount()];
    	for (i=0; i<_goal.getCount(); i++)
    	{
    		list[i] = (String)_goal.getItemAtPosition(i);
    	}
    	b.putStringArray("goals", list);

    	// Locations:
    	list = new String[_location.getCount()];
    	for (i=0; i<_location.getCount(); i++)
    	{
    		list[i] = (String)_location.getItemAtPosition(i);
    	}
    	b.putStringArray("locations", list);    	

    	// Save the flag indicating if changes have been made:
    	b.putBoolean("changes_made", _changesMade);
    	
    	// Save the contact lookup key (if any):
    	b.putString("contact_lookup_key", _contactLookupKey);
    	
    	// Don't automatically pop up the keyboard after rotation:
    	b.putBoolean("dont_show_keyboard", true);
    	
    	// Store the button ID of the tab we're currently on, so it can be restored:
    	b.putInt("tab_button_id", _tabButtonID);
    	
    	// Call the superclass version to handle the views that are automatically saved:
    	super.onSaveInstanceState(b); 
    }
    
    // Handler for the back button.  Returns true if key handled, else false.
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_BACK)
        {
        	confirmExitWithoutSave(new ExitWithoutSaveHandler() {

				@Override
				public void onExitWithoutSave()
				{
					_a.setResult(Activity.RESULT_CANCELED);
                    _a.finish(); 
				}
        	});
            return true;
        }
        return false;
    }
    
    // Cancel button handler:
    private void handleCancelButton()
    {
    	confirmExitWithoutSave(new ExitWithoutSaveHandler() 
    	{
			@Override
			public void onExitWithoutSave()
			{
		    	if (_isOnlyFragment)
		    	{
		    		// Just stop the parent activity:
		    		_a.setResult(Activity.RESULT_CANCELED);
		    		_a.finish();
		    	}
		    	else
		    	{
		    		UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
		    		if (_launchedFromViewerInSS && _op==EDIT && (_ssMode==Util.SS_2_PANE_LIST_DETAILS ||
		    			_ssMode==Util.SS_3_PANE))
		    		{
		    			// Go back to the task viewer:
		    			ViewTaskFragment frag = new ViewTaskFragment();
		        		Bundle args = new Bundle();
		        		args.putLong("_id", t._id);
		        		frag.setArguments(args);
		        		nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewTaskFragment.FRAG_TAG + 
		        			"/" + t._id);
		    		}
		    		else if (_ssMode==Util.SS_2_PANE_NAV_LIST)
		    		{
		    			// This is displayed in a sliding drawer, so close it:
		    			nav.closeDrawer();
		    		}
		    		else
		    		{
		    			nav.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
		    		}
		    	}
			}
    	});    	
    }
    
    // Save and return (if the information entered is valid):
    protected void saveAndReturn()
    {
        String none = Util.getString(R.string.None);
        
        // Verify that the title is not empty.
        TextView tv = (TextView)_rootView.findViewById(R.id.edit_task_title2);
        if (tv.getText().toString().trim().length()==0 ||
        	tv.getText().toString().trim().equals(Util.getString(R.string.None)))
        {
            Util.popup(_a, R.string.Please_enter_a_title);
            return;
        }
        
        // Verify that title is not too long:
        if (tv.getText().toString().trim().length()>Util.MAX_TASK_TITLE_LENGTH)
        {
        	Util.popup(_a,R.string.Title_is_too_long);
        	return;
        }
        
        // Verify that a start time was not entered without a start date.
        if (_settings.getBoolean(PrefNames.START_TIME_ENABLED, true) &&
            _settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            if (!_startTime.getText().toString().equals(none) &&
                _startDate.getText().toString().equals(none))
            {
                // Time is not "none".  Date is "none".
            	_startTime.setText(R.string.None);
            }
        }
        
        // Verify that a due time was not entered without a due date.
        if (_settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true) &&
            _settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            if (!_dueTime.getText().toString().equals(none) &&
            	_dueDate.getText().toString().equals(none))
            {
                // Time is not "none".  Date is "none".
            	_dueTime.setText(R.string.None);
            }
        }
        
        // Get the start date/time in millis:
        long startDateTime = 0;
        boolean startTimeSet = false;
        TextView timeTV;
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            if (!_startDate.getText().toString().equals(none) && _startDate.getText().length()>0)
            {
                startDateTime = Util.dateToMillis(_startDate.getText().toString());
            }
            if (_settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
            {
                if (!_startTime.getText().toString().equals(none) &&
                	_startTime.getText().length()>0)
                {
                    startDateTime = Util.dateTimeToMillis(_startDate.getText().toString(), 
                    	_startTime.getText().toString());
                    startTimeSet = true;
                }
            }           
        }
        
        // Get the end date/time in millis:
        long dueDateTime = 0;
        boolean dueTimeSet = false;
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            if (!_dueDate.getText().toString().equals(none) && _dueDate.getText().length()>0)
            {
                dueDateTime = Util.dateToMillis(_dueDate.getText().toString());
            }
            if (_settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
            {
                if (!_dueTime.getText().toString().equals(none) &&
                	_dueTime.getText().length()>0)
                {
                    dueDateTime = Util.dateTimeToMillis(_dueDate.getText().toString(), 
                    	_dueTime.getText().toString());
                    dueTimeSet = true;
                }
            }           
        }
        
        // Verify that a start or due date is set if a repeat option is chosen:
        if (_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))
        {
	        if (_repeat.getSelectedItemPosition()>0 && dueDateTime==0 &&
	        	startDateTime==0)
	        {
	        	Util.popup(_a, R.string.Repeat_Without_Date);
	        	return;
	        }
        }
        
        long reminderDateTime = 0;
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true) && !dueTimeSet)
        {
            // Verify that a reminder time is not set without a reminder date:
            if (!_reminderTime.getText().toString().equals(none) &&
                _reminderDate.getText().toString().equals(none))
            {
                // Time is not "none".  Date is "none".
                _reminderTime.setText(R.string.None);
            }
            
            // Get the reminder date/time, if any:
            if (!_reminderDate.getText().toString().equals(none) && _reminderDate.getText().length()>0)
            {
                reminderDateTime = Util.dateToMillis(_reminderDate.getText().toString());
            }
            if (!_reminderTime.getText().toString().equals(none) && _reminderTime.getText().
            	length()>0)
            {
                reminderDateTime = Util.dateTimeToMillis(_reminderDate.getText().toString(), 
                	_reminderTime.getText().toString());
            }  

            // If an explicit reminder date is set, then make sure it is before the due
            // date.
            if (dueDateTime>0 && reminderDateTime>0 && reminderDateTime > 
                (dueDateTime+24*60*60*1000))
            {
                Util.popup(_a,R.string.Reminder_must_be_before_due);
                return;
            }
        }
        
        // The expected length needs to either be blank or be an integer:
        int expectedLength = 0;
        if (_expectedLength!=null && _expectedLength.getText().length()>0 && 
        	!_expectedLength.getText().toString().equals(Util.getString(R.string.None)))
        {
            try
            {
            	int cutoffIndex = _expectedLength.getText().toString().indexOf(" "+Util.
            		getString(R.string.minutes_abbreviation));
                expectedLength = Integer.parseInt(_expectedLength.getText().toString().
                	substring(0, cutoffIndex));
            }
            catch (NumberFormatException e)
            {
                // Don't do anything.  Just keep value at zero.
            }
        }
        
        // The actual length needs to either be blank or be an integer:
        int actualLength = 0;
        if (_actualLength!=null && _actualLength.getText().length()>0 && !_actualLength.getText().
        	toString().equals(Util.getString(R.string.None)))
        {
            try
            {
            	int cutoffIndex = _actualLength.getText().toString().indexOf(" "+Util.
            		getString(R.string.minutes_abbreviation));
                actualLength = Integer.parseInt(_actualLength.getText().toString().
                	substring(0, cutoffIndex));
            }
            catch (NumberFormatException e)
            {
                // Don't do anything.  Just keep value at zero.
            }
        }
        
        // Make sure at least one account is selected:
        if (_selectedAccountIDs==null || _selectedAccountIDs.size()==0)
        {
        	Util.popup(_a,R.string.At_least_one_account);
        	return;
        }
        
        // If this task is linked to a Google account, it MUST have a folder.
        tv = (TextView)_folder.getSelectedView();
        String folderName;
        if (tv!=null)
        	folderName = tv.getText().toString();
        else
        	folderName = Util.getString(R.string.None);
    	boolean gTasksAccountFound = false;
    	Iterator<Long> it = _selectedAccountIDs.iterator();
    	while (it.hasNext())
    	{
    		UTLAccount acct = (new AccountsDbAdapter()).getAccount(it.next());
    		if (acct!=null && acct.sync_service==UTLAccount.SYNC_GOOGLE)
    		{
    			gTasksAccountFound = true;
    			break;
    		}
    	}
		if (gTasksAccountFound && folderName.equals(Util.getString(R.string.None)))
		{
            if (_op==EDIT)
            {
                // Use the folder of the task we're editing.
                Cursor folder = (new FoldersDbAdapter()).getFolder(t.folder_id);
                if (folder.moveToFirst())
                {
                    folderName = Util.cString(folder, "title");
                    folder.close();
                }
                else
                {
                    folder.close();
                    Util.popup(_a, R.string.GTasks_Must_Have_Folder);
                    return;
                }
            }
            else
            {
                Util.popup(_a, R.string.GTasks_Must_Have_Folder);
                return;
            }
		}
		
		// If this is a subtask, the new subtask must be in the same folder as the
		// parent:
		if (_op==ADD && gTasksAccountFound && isSubtask)
		{
			// Get the name of the parent's folder:
			UTLTask parent = (new TasksDbAdapter()).getTask(parentID);
			if (parent!=null)
			{
				Cursor folder = (new FoldersDbAdapter()).getFolder(parent.folder_id);
				if (folder.moveToFirst())
				{
					if (!Util.cString(folder, "title").toLowerCase().equals(folderName.
						toLowerCase()))
					{
						Util.longerPopup(_a, "", Util.getString(R.string.
							Must_be_in_same_folder2)+" ("+Util.cString(folder, "title")+")");
						folder.close();
						return;
					}
				}
				folder.close();
			}
		}
		else if ((_op==EDIT || _isClone) && gTasksAccountFound && t.parent_id>0)
		{
			// This task is being edited or cloned, and it has a parent, so the folder cannot
			// change.
			UTLTask parent = (new TasksDbAdapter()).getTask(t.parent_id);
			if (parent!=null)
			{
				Cursor folder = (new FoldersDbAdapter()).getFolder(parent.folder_id);
				if (folder.moveToFirst())
				{
					if (!Util.cString(folder, "title").toLowerCase().equals(folderName.
						toLowerCase()))
					{
						Util.longerPopup(_a, "", Util.getString(R.string.
							Must_be_in_same_folder3));
						folder.close();
						return;
					}
				}
				folder.close();
			}
		}
        
    	// Determine if the "none" priority should be allowed.  It is not allowed for Toodledo
    	// accounts.
    	boolean allowNone = true;
    	it = _selectedAccountIDs.iterator();
    	while (it.hasNext())
    	{
    		long aID = it.next();
    		UTLAccount a = (new AccountsDbAdapter()).getAccount(aID);
    		if (a!=null && a.sync_service==UTLAccount.SYNC_TOODLEDO)
    			allowNone = false;
    	}
    	if (!allowNone)
    	{
    		// "none" is not allowed, so make sure it is not selected.
            if (_priority!=null && _settings.getBoolean(PrefNames.PRIORITY_ENABLED, true) &&
            	_priority.getSelectedItemPosition()==0)
            {
            	Util.longerPopup(_a, "", Util.getString(R.string.Priority_is_required));
            	return;
            }
    	}

    	// Make sure the note is not too long:
    	if (_note.getText().toString().length()>Util.MAX_TASK_NOTE_LENGTH)
    	{
    		Util.popup(_a, R.string.Note_is_too_long);
    		return;
    	}
    	
    	// At this point, all fields have been validated.  For each account specified, 
        // create a new instance of UTLTask from the information entered, and add it
        // to the DB.
        it = _selectedAccountIDs.iterator();
        boolean newFolderAddedForAnyAccount = false;
        FeatureUsage featureUsage = new FeatureUsage(_a);
        while (it.hasNext())
        {
            UTLTask task = new UTLTask();
            if (_op==EDIT)
            {
                task._id = t._id;
                task.td_id = t.td_id;
                task.sync_date = t.sync_date;
                task.remote_id = t.remote_id;
                task.position = t.position;
                task.new_task_generated = t.new_task_generated;
                task.completion_date = t.completion_date;
                task.calEventUri = t.calEventUri;
                task.is_joint = t.is_joint;
                task.owner_remote_id = t.owner_remote_id;
                task.shared_with = t.shared_with;
                task.added_by = t.added_by;
                task.sort_order = t.sort_order;
                task.is_moved = t.is_moved;
                task.prev_task_id = t.prev_task_id;
            }
            else if (_isClone)
            {
            	task.td_id = -1;
            	task.completion_date = t.completion_date;
            }
            else
            {
                task.td_id = -1;
            }
            task.account_id = it.next();
            task.mod_date = System.currentTimeMillis();
            tv = (TextView)_rootView.findViewById(R.id.edit_task_title2);
            task.title = tv.getText().toString().trim();

            // Strip out newlines from the title and replace with spaces.
            task.title = task.title.replace("\n"," ");

            if (_op==ADD && isSubtask)
            {
                task.parent_id = parentID;
            }
            if (_op==EDIT || _isClone)
            {
            	if (t.account_id==task.account_id)
            		task.parent_id = t.parent_id;
            }
            task.due_date = dueDateTime;
            task.uses_due_time = dueTimeSet;
            task.due_modifier = dueModifier;
            task.start_date = startDateTime;
            task.uses_start_time = startTimeSet;
            if (_status!=null && _settings.getBoolean(PrefNames.STATUS_ENABLED, true))
            	task.status = _status.getSelectedItemPosition();
            else
            	task.status = 0;
            task.length = expectedLength;
            if (_priority!=null && _settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
           		task.priority = _priority.getSelectedItemPosition();
            else
            	task.priority = 0;
            task.star = starOn;
            if (!_note.getText().toString().equals(Util.getString(R.string.None)))
            	task.note = _note.getText().toString();
            
            // Completion Status:
            task.completed = getCheckboxStatus();

            // For the folders, we need to add any folders that don't exist:
            tv = (TextView)_folder.getSelectedView();
            if (tv!=null)
            	folderName = tv.getText().toString();
            else
            	folderName = Util.getString(R.string.None);
            boolean newFolderAdded = false;
            if (!folderName.equals(none))
            {
                FoldersDbAdapter foldersDB = new FoldersDbAdapter();
                Cursor c = foldersDB.queryFolders("lower(title)='"+Util.makeSafeForDatabase(
                     folderName.toLowerCase())+"' and account_id="+task.account_id, 
                     "title");
                if (!c.moveToFirst())
                {
                	// Second test - to account for non-English languages:
                	c.close();
                	c = foldersDB.queryFolders("title='"+Util.makeSafeForDatabase(
                        folderName)+"' and account_id="+task.account_id, 
                        "title");
                	if (!c.moveToFirst())
                	{
	                    // We need to add in the folder:
	                    long folderID = foldersDB.addFolder(-1, task.account_id, folderName, 
	                        false,false);
	                    if (folderID==-1)
	                    {
	                        Util.popup(_a, R.string.Unable_to_add_folder);
	                        return;
	                    }
	                    task.folder_id = folderID;
	                    newFolderAdded = true;
	                    newFolderAddedForAnyAccount = true;
                	}
                	else
                	{
                		task.folder_id = Util.cLong(c, "_id");
                	}
                }
                else
                {
                    task.folder_id = Util.cLong(c, "_id");
                }
                c.close();
            }
            else
            {
                // No folder specified:
                if (gTasksAccountFound && _op==EDIT)
                {
                    // For a Google task, a folder MUST be specified.  Use the folder of the
                    // task we're editing.
                    task.folder_id = t.folder_id;
                }
                else
                    task.folder_id = 0;
            }
            
            // Set the previous folder ID:
            if (_op==EDIT)
            	task.prev_folder_id = t.folder_id;
            else
            	task.prev_folder_id = task.folder_id;
            
            // For the contexts, we need to add any that don't exist:
            tv = (TextView)_context.getSelectedView();
            String contextName;
            if (tv!=null)
            	contextName = tv.getText().toString();
            else
            	contextName = Util.getString(R.string.None);
            boolean newContextAdded = false;
            if (!contextName.equals(none))
            {
                ContextsDbAdapter contextsDB = new ContextsDbAdapter();
                Cursor c = contextsDB.queryContexts("lower(title)='"+Util.makeSafeForDatabase(
                     contextName.toLowerCase())+"' and account_id="+task.account_id, 
                     "title");
                if (!c.moveToFirst())
                {
                	// Second test - to account for non-English languages:
                	c.close();
                	c = contextsDB.queryContexts("title='"+Util.makeSafeForDatabase(
                        contextName)+"' and account_id="+task.account_id, 
                        "title");
                	if (!c.moveToFirst())
                	{
	                    // We need to add in the context:
	                    long contextID = contextsDB.addContext(-1, task.account_id, contextName);
	                    if (contextID==-1)
	                    {
	                        Util.popup(_a, R.string.Unable_to_add_context);
	                        return;
	                    }
	                    task.context_id = contextID;
	                    newContextAdded = true;
                	}
                	else
                	{
                		task.context_id = Util.cLong(c, "_id");
                	}
                }
                else
                {
                    task.context_id = Util.cLong(c, "_id");
                }
                c.close();
            }
            else
            {
                // No context specified:
                task.context_id = 0;
            }

            // For the goals, we need to add any that don't exist:
            tv = (TextView)_goal.getSelectedView();
            String goalName;
            if (tv!=null)
            	goalName = tv.getText().toString();
            else
            	goalName = Util.getString(R.string.None);
            boolean newGoalAdded = false;
            if (!goalName.equals(none))
            {
                GoalsDbAdapter goalsDB = new GoalsDbAdapter();
                Cursor c = goalsDB.queryGoals("lower(title)='"+Util.makeSafeForDatabase(
                     goalName.toLowerCase())+"' and account_id="+task.account_id, "title");
                if (!c.moveToFirst())
                {
                	// Second test - to account for non-English languages:
                	c.close();
                	c = goalsDB.queryGoals("title='"+Util.makeSafeForDatabase(
                        goalName)+"' and account_id="+task.account_id, "title");
                	if (!c.moveToFirst())
                	{
	                    // We need to add in the goal:
	                    long goalID = goalsDB.addGoal(-1, task.account_id, goalName, false,
	                        -1,1);
	                    if (goalID==-1)
	                    {
	                        Util.popup(_a, R.string.Unable_to_add_goal);
	                        return;
	                    }
	                    task.goal_id = goalID;
	                    newGoalAdded = true;
                	}
                	else
                	{
                		task.goal_id = Util.cLong(c, "_id");
                	}
                }
                else
                {
                    task.goal_id = Util.cLong(c, "_id");
                }
                c.close();
            }
            else
            {
                // No goal specified:
                task.goal_id = 0;
            }

            // Get the location ID and store it in the task:
            tv = (TextView)_location.getSelectedView();
            String locName;
            if (tv!=null)
            	locName = tv.getText().toString();
            else
            	locName = Util.getString(R.string.None);
            if (locName.length()>0 && !locName.equals(Util.getString(R.string.None)))
            {
                LocationsDbAdapter locDb = new LocationsDbAdapter();
            	Cursor c = locDb.queryLocations("account_id="+task.account_id+" and ("+
            		"lower(title)='"+Util.makeSafeForDatabase(locName.toLowerCase())+"' or "+
            		"title='"+Util.makeSafeForDatabase(locName)+"')",
            		null);
            	if (c.moveToFirst())
            	{
            		// Found the location.  Update the task:
            		task.location_id = Util.cLong(c, "_id");
            		c.close();
            	}
            	else
            	{
            		// Could not find the location.  Search other selected accounts 
            		// until we find a match:
            		c.close();
            		String queryStr = "";
            		Iterator<Long> it2 = _selectedAccountIDs.iterator();
            		while (it2.hasNext())
            			queryStr += ","+it2.next();
            		queryStr = queryStr.substring(1);
            		c = locDb.queryLocations("account_id in ("+queryStr+") and ("+
            			"lower(title)='"+Util.makeSafeForDatabase(locName.toLowerCase())+"'"+
            			" or title='"+Util.makeSafeForDatabase(locName)+"')", null);
            		if (c.moveToFirst())
            		{
            			// Clone the location and update the task:
            			UTLLocation loc = locDb.cursorToUTLLocation(c);
            			loc.account_id = task.account_id;
            			loc.td_id = -1;
            			loc.mod_date = 0;
            			loc.sync_date = 0;
            			if (locDb.addLocation(loc)>-1)
            				task.location_id = loc._id;
            			else
            				task.location_id = 0;
                		c.close();
            		}
            		else
            		{
            			// This should not happen, but if it does we will quietly set
            			// the location to nothing.
            			Util.log("Could not find matching location when adding task.");
            			c.close();
            			task.location_id = 0;
            		}
            	}
            	
            }
            
            // Location reminder and nag settings:
            if (task.location_id>0)
            {
            	task.location_reminder = _locReminder.isChecked();
            	task.location_nag = _locNag.isChecked();
            	if (task.location_nag && !task.location_reminder)
            		task.location_nag = false;
            }
            
            // Reminder date/time:
            if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
            {
                if (task.uses_due_time)
                {
                    // The reminder (if any) was specified as the number of minutes before
                    // the due time:
                    int minuteArray[] = getResources().getIntArray(R.array.reminder_minutes);
                    long numMinutes = Long.valueOf(minuteArray[_relativeReminderSpinner.getSelectedItemPosition()]);
                    if (numMinutes>0)
                    {
                        task.reminder = task.due_date - numMinutes*60*1000;
                    }
                }
                else
                {
                    // The reminder (if any) was explicitly specified as a date and
                    // time.
                    task.reminder = reminderDateTime;
                }
                
                // Nag setting:
                if (task.reminder>0)
                    task.nag = _reminderNag.isChecked();
            }
            
            // Repeat options:
            int selection = _repeat.getSelectedItemPosition();
            int count = _repeat.getCount();
            if (selection==(count-1))
            {
                // Advanced repeat option:
            	if (_repeatAdvanced.getTag()!=null)
            		task.rep_advanced = (String)_repeatAdvanced.getTag();
            	else
            		task.rep_advanced = "";
                if (_repeatFrom.getSelectedItemPosition()==1)
                {
                    task.repeat = 150;
                }
                else
                {
                    task.repeat = 50;
                }
            }
            else
            {
                task.repeat = selection;
                if (_repeatFrom.getSelectedItemPosition()==1)
                {
                    task.repeat += 100;
                }
            }
            
            // Timer:
            if (_op==EDIT)
            {
                if (timerOn && t.timer_start_time==0)
                {
                    // The timer was off and now is on:
                    task.timer_start_time = System.currentTimeMillis();
                }
                else if (timerOn)
                {
                    // The timer is running but wasn't changed.
                    task.timer_start_time = t.timer_start_time;
                }
                task.timer = actualLength*60;
            }
            else
            {
                if (timerOn)
                {
                    // The new task is created with the timer running:
                    task.timer_start_time = System.currentTimeMillis();
                }
                else
                {
                	// If the user entered an actual length, then record it:
                	task.timer = actualLength*60;
                }
            }
            
            // Completion Date:
            Boolean markedCompletedNow = false;
            if (_op==EDIT)
            {
                if (!t.completed && getCheckboxStatus())
                {
                    // The task was just marked as completed:
                    task.completion_date = System.currentTimeMillis();
                    markedCompletedNow = true;
                }
            }
            else
            {
                if (getCheckboxStatus())
                {
                    // The new task is being marked as completed from the very start:
                    task.completion_date = System.currentTimeMillis();
                    markedCompletedNow = true;
                }
            }
            
            // If the task is not completed, make sure the completion date is empty:
            if (!task.completed)
            {
            	task.completion_date = 0;
            }
            
            // Linked contact:
            if (_contactLookupKey!=null && _contactLookupKey.length()>0)
            {
            	task.contactLookupKey = _contactLookupKey;
            }
            else
            {
            	task.contactLookupKey = "";
            }
            
            // Collaboration / Sharing:
            UTLAccount acct = (new AccountsDbAdapter()).getAccount(task.account_id);
            if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true) &&
            	acct.sync_service==UTLAccount.SYNC_TOODLEDO && (_op==ADD || 
            	( _op==EDIT && (!t.is_joint || t.owner_remote_id.equals(acct.td_userid))) ))
            {
            	if (_sharedWith.getText().length()>0 &&
					!_sharedWith.getText().toString().equals(Util.getString(R.string.None)))
				{
					// Collaborators have been chosen.
            		task.is_joint = true;
            		if (_op==ADD)
            			task.owner_remote_id = acct.td_userid;
            		if (_op==EDIT && t.owner_remote_id.length()==0)
            			task.owner_remote_id = acct.td_userid;
            		
            		// Fetch the IDs of the selected collaborators:
            		CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
            		String[] currentCollNames = _sharedWith.getText().toString().split(", ");					
					String currentCollIDs = "";
					for (int i=0; i<currentCollNames.length; i++)
					{
						UTLCollaborator co = cdb.getCollaboratorByName(task.account_id, 
							currentCollNames[i]);
						if (co!=null)
						{
							if (currentCollIDs.length()>0)
								currentCollIDs += "\n";
							currentCollIDs += co.remote_id;
						}
					}
					task.shared_with = currentCollIDs;
				}
            	else
            	{
            		// No collaborators have been chosen.
            		task.is_joint = false;
            		task.shared_with = "";
            		if (_op==ADD)
            			task.owner_remote_id = acct.td_userid;
            	}
            	
            	// For an edit operation, check to see if the sharing has changed.  If so,
            	// we need to set a special flag indicating that the new sharing information needs
            	// to be uploaded.
            	if (_op==EDIT)
            	{
            		if (!(task.shared_with.equals(t.shared_with)))
            		{
            			task.shared_with_changed = true;
            		}
            	}
            }
            	
            // Make sure the parent task has not been deleted in a recent sync:
            TasksDbAdapter tasksDB = new TasksDbAdapter();
            if (task.parent_id>0)
            {
            	UTLTask parent = tasksDB.getTask(task.parent_id);
            	if (parent==null)
            	{
            		Util.log("The task's parent was deleted while the task was being "+
            			"edited. The task will have no parent.");
            		task.parent_id = 0;
            	}
            }

            // If this is an edited task, the folder has changed, and we're syncing with Google, then
            // the task needs to be placed at the bottom of the list when sorting manually.
            UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
            if (_op==EDIT && a.sync_service==UTLAccount.SYNC_GOOGLE && task.folder_id!=t.folder_id)
            {
                task.setSortOrderToBottom();
            }

            // When adding a task, it is placed at the bottom of the list when sorting manually.
            if (_op==ADD)
                task.setSortOrderToBottom();


            // Link the task with a calendar entry if needed:
            if (_addToCal.isChecked() && (task.due_date>0 || task.start_date>0) &&
            	_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            {
            	CalendarInterface ci = new CalendarInterface(_a);
            	if (_op==EDIT && ci.hasIdenticalCalendarEntries(t, task))
            	{
            		// We are editing the task, but the calendar entry does not change.  In this case,
            		// it doesn't make sense to re-link, since doing so will delete and create a new
            		// calendar entry.
            		task.calEventUri = t.calEventUri;
            	}
            	else
            	{
	            	String uri = ci.linkTaskWithCalendar(task);
	            	if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
	            	{
	            		task.calEventUri = uri;
	            	}
	            	else
	            	{
	            		String errorMsg = uri.substring(CalendarInterface.ERROR_INDICATOR.length());
	            		Util.longerPopup(_a, "", errorMsg);
	            		return;
	            	}
            	}
            }
            else
            	task.calEventUri = "";
            
            // Unlink the task from the calendar if necessary:
            if (_op==EDIT && t.calEventUri!=null && t.calEventUri.length()>0 &&
            	task.calEventUri.length()==0 && _settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            {
            	CalendarInterface ci = new CalendarInterface(_a);
            	ci.unlinkTaskFromCalendar(t);
            }

            // Record features used by the task:
            featureUsage.recordForTask(task);

            // Add or modify the task in the database:
            long taskID = -1;
            if (_op==EDIT)
            {
                boolean isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.popup(_a,R.string.Cannot_modify_task);
                    return;
                }
            }
            else
            {
                task.uuid = UUID.randomUUID().toString();
                taskID = tasksDB.addTask(task);
                if (taskID==-1)
                {
                    Util.popup(_a,R.string.Cannot_add_task);
                    return;
                }   
                
                // For an add operation, we need to update the calendar event's note
                // to include a link to the newly created task:
                if (_addToCal.isChecked() && (task.due_date>0 || task.start_date>0) &&
                	_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
                {
                	CalendarInterface ci = new CalendarInterface(_a);
                	ci.addTaskLinkToEvent(task);
                }
            }
            
            // Add or update the tags for the task:
            if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
            {
                TagsDbAdapter tagsDB = new TagsDbAdapter();
                if (!_tags.getText().toString().equals(Util.getString(R.string.None)))
                {
                    String[] tagArray = _tags.getText().toString().split(",");
                    tagsDB.linkTags(task._id, tagArray);

                    // Record usage of the tags feature:
                    if (!task.completed)
                        featureUsage.record(FeatureUsage.TAGS);
                }
                else
                {
                	tagsDB.linkTags(task._id, new String[0]);
                }
            }
            
            if (markedCompletedNow)
            {
                // The task was marked as completed on this screen.  The following 
                // function will make additional updates to the DB as needed (such as
                // marking subtasks complete and/or generating new recurring tasks):
                if (_op==EDIT)
                {
                    Util.markTaskComplete(task._id);
                }
                else
                {
                    Util.markTaskComplete(taskID);
                }
            }

            /* Uncomment to test location reminders:
            if (BuildConfig.DEBUG && task.location_id!=0 && task.location_reminder &&
                !markedCompletedNow)
            {
                // For testing, generate a location reminder.
                new Handler().postDelayed(() -> {
                    Uri.Builder uriBuilder = new Uri.Builder();
                    uriBuilder.scheme("viewtask");
                    uriBuilder.opaquePart(Long.valueOf(task._id).toString());
                    Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),_a,Notifier.class);
                    i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);
                    _a.sendBroadcast(i);
                },7000);
            } */

            // If the current time zone is different than the home time zone, the
        	// reminder time needs to be offset when comparing it to the current time.  
        	TimeZone currentTimeZone = TimeZone.getDefault();
        	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
        		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
        	long reminderTime = task.reminder;
        	long oldReminderTime = 0;
        	if (_op==EDIT && t!=null)
        	{
        		oldReminderTime = t.reminder;
        	}
        	if (!currentTimeZone.equals(defaultTimeZone))
        	{
        		long difference = currentTimeZone.getOffset(System.currentTimeMillis()) - 
        			defaultTimeZone.getOffset(System.currentTimeMillis());
        		reminderTime = task.reminder - difference;
        		if (_op==EDIT && t!=null)
        			oldReminderTime = t.reminder - difference;
        	}
        	
        	// If a reminder was moved from the past to the future, then remove any 
            // notifications that are displaying, and cancel any nagging alarms:
            if (_op==EDIT && reminderTime>System.currentTimeMillis() && oldReminderTime<
            	System.currentTimeMillis())
            {
            	// Cancel any pending notifications (such as those for nagging):
            	Util.cancelReminderNotification(task._id);
            	
            	// Remove the notification if it is displaying:
            	Util.removeTaskNotification(task._id);
            }

            // If a reminder was set up, then schedule it:
            if (reminderTime>System.currentTimeMillis() && !task.completed)
            {
            	Util.scheduleReminderNotification(task);
            }
            
            if (_op==EDIT && task.reminder==0 && t.reminder>0)
            {
            	// We just removed a reminder.  Cancel the alarm:
            	Util.cancelReminderNotification(task._id);
            }       
            
            // If this is a Google account and the folder was changed, we also need to
            // change the folders of any subtasks:
            boolean fullSyncStarted = false;
            if (_op==EDIT && gTasksAccountFound && t.folder_id!=task.folder_id)
            {
            	changeSubtaskFolders(task);
            	fullSyncStarted = true;
            	Intent i = new Intent(_a, Synchronizer.class);
                i.putExtra("command", "full_sync");
                i.putExtra("is_scheduled", true);
                Synchronizer.enqueueWork(_a,i);
            }
            
            if (!newFolderAdded && !newContextAdded && !newGoalAdded && 
            	_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true) && !fullSyncStarted)
            {
            	// The instant upload feature is turned on, and no new folders, contexts,
            	// or goals were added.  So, we can upload the task.
            	Util.instantTaskUpload(_a, task);
            }
        }
        
        // If any tags were used, make sure they are on the recently used tags list:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            if (!_tags.getText().toString().equals(Util.getString(R.string.None)))
            {
                CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
                String[] tagArray = _tags.getText().toString().split(",");
                currentTags.addToRecent(tagArray);
            }
        }

        Util.logOneTimeEvent(_a, "finish_editing_task", 0, new String[]{Integer.valueOf(_op).toString()});

        // Update any widgets that are on display:
        Util.updateWidgets();
        
        // Exit this fragment:
        if (_isOnlyFragment)
    	{
    		// Just stop the parent activity:
    		_a.setResult(Activity.RESULT_OK);
    		_a.finish();
    	}
    	else
    	{
    		TaskList tl = (TaskList)_a;
    		
    		// The navigation drawer needs refreshed:
    		if (!newFolderAddedForAnyAccount)
    			tl.refreshNavDrawerCounts();
    		else
    			tl.refreshWholeNavDrawer();
    		
    		// The task list also needs refreshed:
    		tl.handleTaskChange();
    		
    		if (_op==EDIT)
    		{
	    		if (_launchedFromViewerInSS && (_ssMode==Util.SS_2_PANE_LIST_DETAILS ||
	    			_ssMode==Util.SS_3_PANE))
	    		{
	    			// Go back to the task viewer:
	    			ViewTaskFragment frag = new ViewTaskFragment();
	        		Bundle args = new Bundle();
	        		args.putLong("_id", t._id);
	        		frag.setArguments(args);
	        		tl.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewTaskFragment.FRAG_TAG + 
	        			"/" + t._id);
	    		}
	    		else if (_ssMode==Util.SS_2_PANE_NAV_LIST)
	    		{
	    			// This is displayed in a sliding drawer, so close it:
	    			tl.closeDrawer();
	    		}
	    		else
	    		{
	    			tl.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
	    		}
    		}
    		else
    		{
    			// Task was just added.
    			if (_ssMode==Util.SS_2_PANE_NAV_LIST)
	    		{
	    			// This is displayed in a sliding drawer, so close it:
	    			tl.closeDrawer();
	    		}
    			else
    			{
    				tl.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
    			}
    		}
    	}               
    }
    
    /** Ask the user if he wants to save changes.  The input is an instance of ConfirmExitWithoutSave
     * that contains some code to execute if the user chooses to exit without saving.
     */
    private void confirmExitWithoutSave(ExitWithoutSaveHandler exitHandler)
    {
    	if (!_changesMade || (_op==ADD && _title.getText().toString().length()==0))
    	{
    		// No changes made.  We can simply exit:
    		exitHandler.onExitWithoutSave();
    		return;
    	}
    	
    	_tempExitWithoutSaveHandler = exitHandler;
    	
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() 
        {                
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which)
                {
                case DialogInterface.BUTTON_POSITIVE:
                    // Yes clicked:
                	_tempExitWithoutSaveHandler.onSave();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // No clicked:
                	_tempExitWithoutSaveHandler.onExitWithoutSave();
                    break;
                }                    
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(_a);
        builder.setMessage(Util.getString(R.string.Save_changes));
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.show();    	
    }
    
    /** This class is used to store some code to execute after the user confirms that he wants to 
	exit without saving changes: */
	private abstract class ExitWithoutSaveHandler
	{
		abstract public void onExitWithoutSave();
		
		public void onSave()
		{
			EditTaskFragment.this.saveAndReturn();
		}
	}
	
	@Override
	public void onDestroy()
	{
		_a.fragmentHandlesHome(false);
		super.onDestroy();
	}
}
