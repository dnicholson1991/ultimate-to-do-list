package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
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

import org.droidparts.widget.ClearableEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * The fragment handles the creation and editing of task templates. In version 1.8, this is only
 * called from Tasker (if the add-on is purchased). <br>
 * <br>
 * To call this, put a Bundle in the fragment arguments with the following keys/values: <br><br>
 * TaskerReceiver.EXTRA_BUNDLE: Contains the bundle from Tasker.
 */

public class EditTemplateFragment extends UtlFragment
{
    /** A Tag for logging. */
    public static final String TAG = "EditTemplateFragment";

    // Codes to specify whether we are adding or editing:
    public static final int ADD = 1;
    public static final int EDIT = 2;

    // Codes to track responses to activities:
    public static final int GET_ADVANCED_REPEAT = 1;
    public static final int GET_ADVANCED_REPEAT2 = 2;
    public static final int GET_TAGS = 3;
    public static final int NEW_LOCATION = 4;
    public static final int GET_CONTACT = 5;
    public static final int GET_SHARING = 6;

    /** Identifies this type of fragment. */
    public static final String FRAG_TAG = "EditTemplateFragment";

    /** The operation we're performing (ADD or EDIT). */
    private int _op;

    /** The template we're editing, if _op == EDIT. */
    private TaskTemplate _tt;

    /** The due date modifier.  Either blank, "due_by", "due_on", or "optionally_on". */
    private String _dueModifier;

    /** Flag which indicates if any changes have been made: */
    private boolean _changesMade;

    /** Flag which indicates if the repeat options spinner has initialized: */
    private int _repeatSpinnerDisableCount;

    /** This hash is used to determine if other spinners have changed their selection: */
    private HashMap<Integer,Integer> _spinnerSelections;

    /** Whether the start is currently on. */
    private boolean _starOn;

    /** Flag indicating that we need to put the cursor on the title and open the keyboard. */
    private boolean _askForTitle;

    /** IDs of views whose contents are not automatically saved when the screen changes
     * orientation. */
    static private int[] _viewIDsToSave = new int[] {
        R.id.edit_task_start_date_text,
        R.id.edit_task_start_time_text,
        R.id.edit_task_due_date_text,
        R.id.edit_task_due_date_advanced_text,
        R.id.edit_task_due_time_text,
        R.id.edit_task_reminder_text,
        R.id.edit_task_reminder_time_text,
        R.id.edit_task_tags2,
        R.id.edit_task_expected_length2,
        R.id.edit_task_repeat_advanced2,
        R.id.edit_task_shared_with_value
    };

    /** Holds the scroll position. */
    int _scrollPositionY;

    /** The number of accounts the user has. */
    private int _numAccounts;

    /** An EditText input for use in dialogs: */
    private EditText _editText;

    /** The lookup key for ths selected contact. */
    private String _contactLookupKey;

    /** Flag indicating if the template's note has hyperlinks. */
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

    /** Flag indicating if we're the only fragment in the activity. */
    private boolean _isOnlyFragment;

    /** The save/cancel bar (if it's in use). */
    private SaveCancelTopBar _saveCancelBar;

    /** Holds a version of the task's note without links. */
    private String _noteWithoutLinks;

    /** Holds the button ID of the tab that was last selected. */
    private int _tabButtonID;

    // Quick reference to TextViews that hold values for the task:
    private ClearableEditText _title;
    private TextView _startDate;
    private TextView _startTime;
    private TextView _dueDate;
    private TextView _dueTime;
    private TextView _dueDateAdvanced;
    private CheckBox _addToCal;
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
    private Spinner _account;
    private Spinner _location;
    private CheckBox _locReminder;
    private CheckBox _locNag;
    private TextView _expectedLength;
    private TextView _contact;
    private EditText _note;
    private TextView _sharedWith;

    /** Used for in-app billing and licensing status. */
    private PurchaseManager _pm;

    /** This timestamp is used to prevent the Advanced Repeat activity from launching when the
     * fragment is first initialized, or after a rotation. */
    private long _advancedRepeatBlockerTimestamp;

    // Quick reference to database adapters.
    private AccountsDbAdapter _accountsDB;
    private FoldersDbAdapter _foldersDB;
    private ContextsDbAdapter _contextsDB;
    private GoalsDbAdapter _goalsDB;
    private LocationsDbAdapter _locationsDB;

    // The user's current choice for due date/time, start date/time, and reminder date/time:
    private int _dueDateOffsetUnits;
    private int _dueDateOffset;
    private int _startDateOffsetUnits;
    private int _startDateOffset;
    private int _reminderDateOffsetUnits;
    private int _reminderDateOffset;

    /** Log a string. */
    private void log(String s)
    {
        Log.v(TAG,s);
    }

    /** This returns the view being used by this fragment. */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.edit_template, container, false);
    }

    /** Called when the activity is started. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        _a = (UtlActivity) getActivity();
        _res = _a.getResources();
        _settings = _a._settings;
        _ssMode = _a.getSplitScreenOption();
        _rootView = (ViewGroup) getView();
        _repeatSpinnerDisableCount = 0;
        _tabButtonID = R.id.edit_task_section_main_button;
        _advancedRepeatBlockerTimestamp = System.currentTimeMillis();
        _accountsDB = new AccountsDbAdapter();
        _foldersDB = new FoldersDbAdapter();
        _contextsDB = new ContextsDbAdapter();
        _goalsDB = new GoalsDbAdapter();
        _locationsDB = new LocationsDbAdapter();

        // Get references to views which hold template data:
        _title = (ClearableEditText) _rootView.findViewById(R.id.edit_task_title2);
        _startDate = (TextView) _rootView.findViewById(R.id.edit_task_start_date_text);
        _startTime = (TextView) _rootView.findViewById(R.id.edit_task_start_time_text);
        _dueDate = (TextView) _rootView.findViewById(R.id.edit_task_due_date_text);
        _dueTime = (TextView) _rootView.findViewById(R.id.edit_task_due_time_text);
        _dueDateAdvanced = (TextView) _rootView.findViewById(R.id.edit_task_due_date_advanced_text);
        _addToCal = (CheckBox) _rootView.findViewById(R.id.edit_task_calendar_checkbox);
        _reminderDate = (TextView) _rootView.findViewById(R.id.edit_task_reminder_text);
        _reminderTime = (TextView) _rootView.findViewById(R.id.edit_task_reminder_time_text);
        _reminderNag = (CheckBox) _rootView.findViewById(R.id.edit_task_nag_checkbox);
        _repeat = (Spinner) _rootView.findViewById(R.id.edit_task_repeat2);
        _repeatAdvanced = (TextView) _rootView.findViewById(R.id.edit_task_repeat_advanced2);
        _repeatFrom = (Spinner) _rootView.findViewById(R.id.edit_task_repeat_from2);
        _status = (Spinner) _rootView.findViewById(R.id.edit_task_status2);
        _priority = (Spinner) _rootView.findViewById(R.id.edit_task_priority2);
        _folder = (Spinner) _rootView.findViewById(R.id.edit_task_folder2);
        _context = (Spinner) _rootView.findViewById(R.id.edit_task_context2);
        _goal = (Spinner) _rootView.findViewById(R.id.edit_task_goal2);
        _tags = (TextView) _rootView.findViewById(R.id.edit_task_tags2);
        _account = (Spinner) _rootView.findViewById(R.id.edit_task_accounts2);
        _location = (Spinner) _rootView.findViewById(R.id.edit_task_location2);
        _locReminder = (CheckBox) _rootView.findViewById(R.id.edit_task_location_reminder_checkbox);
        _locNag = (CheckBox) _rootView.findViewById(R.id.edit_task_location_nag_checkbox);
        _expectedLength = (TextView) _rootView.findViewById(R.id.edit_task_expected_length2);
        _contact = (TextView) _rootView.findViewById(R.id.edit_task_contact2);
        _note = (EditText) _rootView.findViewById(R.id.edit_task_note2);
        _sharedWith = (TextView) _rootView.findViewById(R.id.edit_task_shared_with_value);

        // Check to see if we will be using the arguments passed in from the fragment or the
        // arguments passed into the Activity.
        Intent intent = _a.getIntent();
        log("Intent passed to EditTemplateFragment: " + Util.intentToString(intent, 2));
        Bundle extras = intent.getExtras();
        _isOnlyFragment = true;
        _saveCancelBar = null;
        Bundle fragArgs = getArguments();
        if (fragArgs != null && fragArgs.size() > 0)
        {
            // Arguments were passed into the fragment.  These take precedence over anything passed
            // into the parent Activity's Intent.
            extras = fragArgs;
            _isOnlyFragment = false;

            // We will use a separate save/cancel bar:
            _saveCancelBar = new SaveCancelTopBar(_a, _rootView);
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

        // Determine if we're adding or editing a template.  Read the template information from
        // the extras if we're editing.
        setTitle(R.string.task_options);
        if (extras == null)
        {
            log("Adding a new task template.");
            _op = ADD;
        }
        else
        {
            // Check to see if Tasker provided a Bundle with template data. If so, fetch the
            // template from the Bundle.
            log("Bundle being processed: " + Util.bundleToString(extras, 2));
            if (extras.containsKey(TaskerReceiver.EXTRA_BUNDLE))
            {
                log("Modifying an existing template.");
                _op = EDIT;
                _tt = TaskTemplate.fromBundle(extras.getBundle(TaskerReceiver.EXTRA_BUNDLE));
            }
            else
            {
                // If there's no bundle, we're creating a new template.
                log("Adding a new task template.");
                _op = ADD;
            }
        }

        // Update the icon if this is the only fragment on the screen and we're adding.
        if (_isOnlyFragment && _op == ADD)
            _a.getSupportActionBar().setIcon(R.drawable.new_task_widget);

        // If we're editing, we don't want the keyboard to appear automatically. We do want it to
        // appear if adding.
        if (_op == EDIT || (savedInstanceState != null && savedInstanceState.containsKey(
            "dont_show_keyboard")))
        {
            _a.getWindow().setSoftInputMode(WindowManager.LayoutParams.
                SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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

        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
        _numAccounts = c1.getCount();
        c1.close();

        initBannerAd(_rootView);

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
            _rootView.findViewById(R.id.edit_task_length_table).setVisibility(View.GONE);
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
        if (_numAccounts < 2)
        {
            _rootView.findViewById(R.id.edit_task_accounts_container).setVisibility(View.GONE);
        }
        if (_numAccounts < 2 &&
            !_settings.getBoolean(PrefNames.STATUS_ENABLED, true) &&
            !_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true) &&
            !_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true) &&
            !_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true) &&
            !_settings.getBoolean(PrefNames.GOALS_ENABLED, true) &&
            !_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_classification_table).setVisibility(View.GONE);
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
        _repeatFrom.setPromptId(R.string.Repeat_From_);
        _account.setPromptId(R.string.Account);

        // For an edit operation, make sure the template's account is still in the database. If
        // it's not, we need to populate the spinners with all folders, contexts, goals, and
        // locations.
        boolean accountExists = true;
        if (_op==EDIT)
        {
            UTLAccount acct = _accountsDB.getAccount(_tt.account_id);
            if (acct==null)
                accountExists = false;
        }

        // Populate the folder list. Use the existing list if the user rotated the screen.
        ArrayAdapter<String> folderSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_folder2);
        if (savedInstanceState != null && savedInstanceState.containsKey("folders"))
        {
            String[] spinnerItems = savedInstanceState.getStringArray("folders");
            for (int i = 0; i < spinnerItems.length; i++)
            {
                folderSpinnerAdapter.add(spinnerItems[i]);
            }
        }
        else if (_op == ADD || !accountExists)
        {
            Cursor c = _accountsDB.getAllAccounts();
            if (!c.moveToFirst())
            {
                // The user has managed to get here without setting up an account first.
                c.close();
                Intent i = new Intent(_a, main.class);
                startActivity(i);
                _a.finish();
                return;
            }
            UTLAccount firstAccount = _accountsDB.getUTLAccount(c);
            if (c.getCount() > 1 || firstAccount.sync_service != UTLAccount.SYNC_TOODLEDO)
            {
                // Retrieve all folders from all accounts, removing duplicate names and sorting.
                c.close();
                SortedSet<String> folderNames = new TreeSet<String>();
                c = _foldersDB.getFoldersByName();
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
                for (int i = 0; i < sortedNames.length; i++)
                {
                    folderSpinnerAdapter.add(sortedNames[i]);
                }
            }
            else
            {
                // Only 1 account, which syncs with Toodledo. Use Toodledo ordering.
                c.close();
                c = _foldersDB.getFoldersByOrder();
                c.moveToPosition(-1);
                while (c.moveToNext())
                {
                    folderSpinnerAdapter.add(Util.cString(c, "title"));
                }
                c.close();
            }
            folderSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            folderSpinnerAdapter.add(Util.getString(R.string.Add_Folder)); // "Add Folder"
        }
        else
        {
            // We only include the folders from the template's account.  If the folder is
            // archived, put it at the top (because it won't be found in the code below)
            Cursor c = _foldersDB.getFolder(_tt.folder_id);
            if (c.moveToFirst())
            {
                if (Util.cInt(c, "archived") == 1)
                {
                    folderSpinnerAdapter.add(Util.cString(c, "title"));
                }
            }
            c.close();
            UTLAccount a = _accountsDB.getAccount(_tt.account_id);
            if (a != null && a.sync_service == UTLAccount.SYNC_TOODLEDO)
                c = _foldersDB.getFoldersByOrder();
            else
                c = _foldersDB.getFoldersByNameNoCase();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                if (Util.cLong(c, "account_id") == _tt.account_id)
                {
                    folderSpinnerAdapter.add(Util.cString(c, "title"));
                }
            }
            c.close();
            folderSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            folderSpinnerAdapter.add(Util.getString(R.string.Add_Folder)); // "Add Folder"
        }
        _folder.setPromptId(R.string.Folder);

        // Populate the context list:
        ArrayAdapter<String> contextSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_context2);
        if (savedInstanceState != null && savedInstanceState.containsKey("contexts"))
        {
            String[] spinnerItems = savedInstanceState.getStringArray("contexts");
            for (int i = 0; i < spinnerItems.length; i++)
            {
                contextSpinnerAdapter.add(spinnerItems[i]);
            }
        }
        else if (_op == ADD || !accountExists)
        {
            // Retrieve all contexts from all accounts, removing duplicate names and sorting.
            SortedSet<String> contextNames = new TreeSet<String>();
            Cursor c = _contextsDB.getContextsByName();
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
            for (int i = 0; i < sortedNames.length; i++)
            {
                contextSpinnerAdapter.add(sortedNames[i]);
            }
            contextSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            contextSpinnerAdapter.add(Util.getString(R.string.Add_Context)); // "Add Context"
        }
        else
        {
            // We only include the contexts from the task's account.
            Cursor c = _contextsDB.getContextsByNameNoCase();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    if (Util.cLong(c, "account_id") == _tt.account_id)
                    {
                        contextSpinnerAdapter.add(Util.cString(c, "title"));
                    }
                    c.moveToNext();
                }
            }
            c.close();
            contextSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            contextSpinnerAdapter.add(Util.getString(R.string.Add_Context)); // "Add Context"
        }
        _context.setPromptId(R.string.Context);

        // Populate the goal list:
        ArrayAdapter<String> goalSpinnerAdapter = _a.initSpinner(_rootView, R.id.edit_task_goal2);
        if (savedInstanceState != null && savedInstanceState.containsKey("goals"))
        {
            String[] spinnerItems = savedInstanceState.getStringArray("goals");
            for (int i = 0; i < spinnerItems.length; i++)
            {
                goalSpinnerAdapter.add(spinnerItems[i]);
            }
        }
        else if (_op == ADD || !accountExists)
        {
            // Retrieve all goals from all accounts, removing duplicate names and sorting.
            SortedSet<String> goalNames = new TreeSet<String>();
            Cursor c = _goalsDB.getAllGoals();
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
            for (int i = 0; i < sortedNames.length; i++)
            {
                goalSpinnerAdapter.add(sortedNames[i]);
            }
            goalSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            goalSpinnerAdapter.add(Util.getString(R.string.Add_Goal)); // "Add Goal"
        }
        else
        {
            // We only include the goals from the templates's account.  If the goal is
            // archived, we add it in first, because it won't be found in the code below.
            GoalsDbAdapter goalsDB = new GoalsDbAdapter();
            Cursor c = goalsDB.getGoal(_tt.goal_id);
            if (c.moveToFirst())
            {
                if (Util.cInt(c, "archived") == 1)
                {
                    goalSpinnerAdapter.add(Util.cString(c, "title"));
                }
            }
            c.close();
            c = _goalsDB.getAllGoalsNoCase();
            if (c.moveToFirst())
            {
                while (!c.isAfterLast())
                {
                    if (Util.cLong(c, "account_id") == _tt.account_id)
                    {
                        goalSpinnerAdapter.add(Util.cString(c, "title"));
                    }
                    c.moveToNext();
                }
            }
            c.close();
            goalSpinnerAdapter.insert(Util.getString(R.string.None), 0); // Insert "none".
            goalSpinnerAdapter.add(Util.getString(R.string.Add_Goal)); // "Add Goal"
        }
        _goal.setPromptId(R.string.Goal);

        // Populate the location spinner:
        refreshLocationSpinner(savedInstanceState, null, accountExists);

        // Populate the account list:
        SortedSet<String> accountNames = new TreeSet<String>();
        Cursor c2 = _accountsDB.getAllAccounts();
        while (c2.moveToNext())
        {
            UTLAccount acct = _accountsDB.getUTLAccount(c2);
            accountNames.add(acct.name);
        }
        c2.close();
        String[] sortedNames1 = Util.iteratorToStringArray(accountNames.iterator(),
            accountNames.size());
        Arrays.sort(sortedNames1, String.CASE_INSENSITIVE_ORDER);
        ArrayAdapter<String> accountSpinnerAdapter = _a.initSpinner(_rootView, R.id.
            edit_task_accounts2);
        for (String name : sortedNames1)
            accountSpinnerAdapter.add(name);

        //
        // Initialize the data in the views:
        //

        _starOn = false;
        if (_op == ADD)
        {
            // Fill in defaults from the user preferences.

            // Status:
            _status.setSelection(_settings.getInt(PrefNames.DEFAULT_STATUS, 0));

            // Start Date:
            if (_settings.getString(PrefNames.DEFAULT_START_DATE, "").equals("today"))
            {
                _startDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_DAYS, 0));
                _startDateOffsetUnits = TaskTemplate.OFFSET_DAYS;
                _startDateOffset = 0;
            }
            else if (_settings.getString(PrefNames.DEFAULT_START_DATE, "").equals("tomorrow"))
            {
                _startDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_DAYS, 1));
                _startDateOffsetUnits = TaskTemplate.OFFSET_DAYS;
                _startDateOffset = 1;
            }
            else
            {
                _startDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_UNUSED, 0));
                _startDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
                _startDateOffset = 0;
            }

            // Due Date:
            if (_settings.getString(PrefNames.DEFAULT_DUE_DATE, "").equals("today"))
            {
                _dueDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_DAYS, 0));
                _dueModifier = "due_by";
                _dueDateOffsetUnits = TaskTemplate.OFFSET_DAYS;
                _dueDateOffset = 0;
            }
            else if (_settings.getString(PrefNames.DEFAULT_DUE_DATE, "").equals("tomorrow"))
            {
                _dueDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_DAYS, 1));
                _dueModifier = "due_by";
                _dueDateOffsetUnits = TaskTemplate.OFFSET_DAYS;
                _dueDateOffset = 1;
            }
            else
            {
                _dueDate.setText(getRelativeTimestampString(TaskTemplate.OFFSET_UNUSED, 0));
                _dueModifier = "";
                _dueDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
                _dueDateOffset = 0;
            }

            // Add to Calendar:
            _addToCal.setChecked(_settings.getBoolean(PrefNames.DEFAULT_ADD_TO_CAL, false));

            // Reminder date and time:
            _reminderDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
            _reminderDateOffset = 0;

            // Priority:
            _priority.setSelection(_settings.getInt(PrefNames.DEFAULT_PRIORITY, 0));

            // Folder:
            if (_settings.contains(PrefNames.DEFAULT_FOLDER))
            {
                long folderID = _settings.getLong(PrefNames.DEFAULT_FOLDER, 0L);
                Cursor c = _foldersDB.getFolder(folderID);
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                c.close();
            }

            // Context:
            if (_settings.contains(PrefNames.DEFAULT_CONTEXT))
            {
                long contextID = _settings.getLong(PrefNames.DEFAULT_CONTEXT, 0L);
                Cursor c = _contextsDB.getContext(contextID);
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_context, Util.cString(c, "title"));
                c.close();
            }

            // Goal:
            if (_settings.contains(PrefNames.DEFAULT_GOAL))
            {
                Cursor c = _goalsDB.getGoal(_settings.getLong(PrefNames.DEFAULT_GOAL, 0));
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                c.close();
            }

            // Location:
            if (_settings.contains(PrefNames.DEFAULT_LOCATION))
            {
                UTLLocation loc = _locationsDB.getLocation(_settings.getLong(PrefNames.
                    DEFAULT_LOCATION, 0));
                if (loc != null)
                    _a.setSpinnerSelection(_location, loc.title);
            }

            // Account:
            if (_settings.getLong(PrefNames.DEFAULT_ACCOUNT, 0) != 0)
            {
                UTLAccount acct = _accountsDB.getAccount(_settings.getLong(PrefNames.
                    DEFAULT_ACCOUNT, 0));
                _a.setSpinnerSelection(_account, acct.name);
            }
            else
            {
                // Use the first account:
                Cursor c = _accountsDB.getAllAccounts();
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_account, _accountsDB.getUTLAccount(c).name);
                c.close();
            }

            // Tags:
            if (_settings.getString(PrefNames.DEFAULT_TAGS, "").length() > 0)
            {
                _tags.setText(_settings.getString(PrefNames.DEFAULT_TAGS, ""));
            }

            // Contact:
            _contactLookupKey = "";
            _contact.setText(R.string.None);

            // Collaborators:
            _sharedWith.setText(R.string.None);
        }
        else
        {
            // Defaults for the on-screen controls come from the template we are editing.

            // Title:
            _title.setText(_tt.title);

            // Status:
            _status.setSelection(_tt.status);

            // Start Date:
            _startDate.setText(getRelativeTimestampString(_tt.start_date_offset_units, _tt.
                start_date_offset));
            _startDateOffsetUnits = _tt.start_date_offset_units;
            _startDateOffset = _tt.start_date_offset;

            // Start Time:
            if (_tt.start_time_hours<0 || _tt.start_time_minutes<0)
                _startTime.setText(R.string.None);
            else
                _startTime.setText(Util.getTimeString(_tt.start_time_hours, _tt.start_time_minutes));

            // Due Date:
            _dueDate.setText(getRelativeTimestampString(_tt.due_date_offset_units, _tt.
                due_date_offset));
            _dueDateOffsetUnits = _tt.due_date_offset_units;
            _dueDateOffset = _tt.due_date_offset;

            // Due Date Modifier:
            String[] dueDateMods = _res.getStringArray(R.array.advanced_due_date_options);
            if (_tt.due_modifier.equals("due_on"))
                _dueDateAdvanced.setText(dueDateMods[1]);
            else if (_tt.due_modifier.equals("optionally_on"))
                _dueDateAdvanced.setText(dueDateMods[2]);
            else
                _dueDateAdvanced.setText(dueDateMods[0]);
            _dueModifier = _tt.due_modifier;

            // Due Time:
            if (_tt.due_time_hours<0 || _tt.due_time_minutes<0)
                _dueTime.setText(R.string.None);
            else
                _dueTime.setText(Util.getTimeString(_tt.due_time_hours, _tt.due_time_minutes));

            // Add To Calendar:
            _addToCal.setChecked(_tt.add_to_calendar);

            // Reminder Date:
            _reminderDate.setText(getRelativeTimestampString(_tt.reminder_date_offset_units, _tt.
                reminder_date_offset));
            _reminderDateOffsetUnits = _tt.reminder_date_offset_units;
            _reminderDateOffset = _tt.reminder_date_offset;

            // Reminder Time:
            if (_tt.reminder_time_hours<0 || _tt.reminder_time_minutes<0)
                _reminderTime.setText(R.string.None);
            else
            {
                _reminderTime.setText(Util.getTimeString(_tt.reminder_time_hours, _tt.
                    reminder_time_minutes));
            }

            // Nag setting:
            _reminderNag.setChecked(_tt.nag);

            // Repeat:
            if (_tt.repeat > 0)
            {
                if (_tt.repeat < 50)
                {
                    // Ordinary repeat string. Repeat from due date.
                    _repeat.setSelection(_tt.repeat, false);
                    _repeatFrom.setSelection(0);
                    _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setVisibility(
                        View.GONE);
                }
                else if (_tt.repeat == 50 || _tt.repeat == 150)
                {
                    // Advanced repeat setting.  The spinner is set to the "advanced" item,
                    // which is guaranteed to be the last one.
                    String repeatArray[] = this.getResources().getStringArray(
                        R.array.repeat_options);
                    _repeat.setSelection(repeatArray.length - 1, false);
                    AdvancedRepeat ar = new AdvancedRepeat();
                    if (ar.initFromString(_tt.rep_advanced))
                    {
                        _repeatAdvanced.setText(ar.getLocalizedString(_a));
                        _repeatAdvanced.setTag(_tt.rep_advanced);
                    }
                    if (_tt.repeat == 50)
                        _repeatFrom.setSelection(0, false);
                    else
                        _repeatFrom.setSelection(1, false);
                }
                else if (_tt.repeat < 150)
                {
                    // Ordinary repeat, but from completion date
                    _repeat.setSelection(_tt.repeat - 100, false);
                    _repeatFrom.setSelection(1);
                    _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setVisibility(
                        View.GONE);
                }
            }

            // Priority:
            _priority.setSelection(_tt.priority);

            // Folder:
            if (_tt.folder_id > 0)
            {
                // Set the spinner to show the template's current folder:
                Cursor c = _foldersDB.getFolder(_tt.folder_id);
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                c.close();
            }

            // Context:
            if (_tt.context_id > 0)
            {
                // Set the spinner to show the template's current context:
                Cursor c = _contextsDB.getContext(_tt.context_id);
                if (c.moveToFirst())
                {
                    _a.setSpinnerSelection(_context, Util.cString(c, "title"));
                }
                c.close();
            }

            // Tags:
            if (_tt.tags != null && _tt.tags.size() > 0)
                _tags.setText(TextUtils.join(",", _tt.tags));

            // Goal:
            if (_tt.goal_id > 0)
            {
                // Set the spinner to show the task's current goal:
                Cursor c = _goalsDB.getGoal(_tt.goal_id);
                if (c.moveToFirst())
                    _a.setSpinnerSelection(_goal, Util.cString(c, "title"));
                c.close();
            }

            // Location:
            if (_tt.location_id > 0)
            {
                UTLLocation loc = _locationsDB.getLocation(_tt.location_id);
                if (loc != null)
                {
                    _a.setSpinnerSelection(_location, loc.title);
                    _locReminder.setChecked(_tt.location_reminder);
                    _locNag.setChecked(_tt.location_nag);
                }
            }

            // Account:
            UTLAccount acct = _accountsDB.getAccount(_tt.account_id);
            if (acct != null)
                _a.setSpinnerSelection(_account, acct.name);
            else
                _account.setSelection(0);

            // Expected Length:
            if (_tt.length > 0)
            {
                _expectedLength.setText(Integer.toString(_tt.length) + " " + Util.getString(
                    R.string.minutes_abbreviation));
            }

            // Note:
            if (_tt.note.length() > 0)
            {
                _note.setText(_tt.note);
                addLinksToNote();
            }

            // Star:
            if (_tt.star)
            {
                ImageView star = (ImageView) _rootView.findViewById(R.id.edit_task_star_button);
                star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_on_inv));
                _starOn = true;
            }

            // Completed:
            updateCompletedCheckbox(_tt.completed, _tt.priority);

            // Contact:
            if (_tt.contact_lookup_key.length() > 0)
            {
                Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
                    CONTENT_LOOKUP_URI, _tt.contact_lookup_key);
                Cursor c = _a.managedQuery(contactUri, null, null, null, null);
                if (c != null && c.moveToFirst())
                {
                    _contact.setText(Util.cString(c, ContactsContract.Contacts.DISPLAY_NAME));
                    _contactLookupKey = _tt.contact_lookup_key;
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
            if (_tt.shared_with.length() > 0)
            {
                // Convert newline separated to comma separated:
                UTLAccount acc = _accountsDB.getAccount(_tt.account_id);
                CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
                String[] people = _tt.shared_with.split("\n");
                String sharedWith = "";
                for (int i = 0; i < people.length; i++)
                {
                    if (sharedWith.length() > 0)
                        sharedWith += ", ";
                    UTLCollaborator co = cdb.getCollaborator(_tt.account_id, people[i]);
                    if (co != null)
                        sharedWith += co.name;
                    else if (people[i].equals(acc.td_userid))
                        sharedWith += Util.getString(R.string.Myself);
                }
                if (sharedWith.length() > 0)
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
        if (savedInstanceState != null && savedInstanceState.containsKey("changes_made"))
            _changesMade = savedInstanceState.getBoolean("changes_made");

        _scrollPositionY = 0;

        _spinnerSelections = new HashMap<Integer, Integer>();

        Util.logOneTimeEvent(_a, "start_editing_template", 0, new String[]{Integer.valueOf(_op).
            toString()});

        if (savedInstanceState != null)
        {
            // The user likely rotated the screen.  Call the restore function to fill in
            // the values for controls.
            onRestoreInstanceState(savedInstanceState);
        }

        // This function will hide the keyboard if the user taps on something that is not an EditText:
        _a.setupAutoKeyboardHiding(_rootView.findViewById(R.id.edit_task_scrollview));

        // If we're adding, then put the cursor on the task title and bring up the keyboard:
        _askForTitle = false;
        if (_op==ADD && savedInstanceState==null)
            _askForTitle = true;

        // If we've just rotated, go to the tab that the user was last viewing.
        if (!_askForTitle && savedInstanceState!=null && savedInstanceState.containsKey(
            "tab_button_id"))
        {
            _tabButtonID = savedInstanceState.getInt("tab_button_id");
            _rootView.findViewById(_tabButtonID).performClick();
        }

        if (_askForTitle)
            promptForTitle();
        _askForTitle = false;

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

        boolean isPluginAvailable = Util.isTaskerPluginAvailable(_a);
        if (!isPluginAvailable)
        {
            log("Exiting because plugin is not available.");
            return;
        }

        Util.pingServer(_a);

        // Define handlers for buttons, spinners, etc.
        setupHandlers();
    }

    /** Define handlers for buttons, spinners, etc. */
    private void setupHandlers()
    {
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

        // Start Date:
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_start_date_container).setOnClickListener(
                new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    showRelativeDateTimeDialog(_startDateOffsetUnits, _startDateOffset,
                        getString(R.string.Start_Date_), new RelativeDateTimeCallback()
                    {
                        @Override
                        public void onDateTimeChosen(int count, int units)
                        {
                            _startDate.setText(getRelativeTimestampString(units,count));
                            _startDateOffsetUnits = units;
                            _startDateOffset = count;
                            _changesMade = true;
                            refreshFieldVisibility();
                        }

                        @Override
                        public void onCancel()
                        {
                            // Nothing to do.
                        }
                    });
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
            ImageButton startDateButton = (ImageButton)_rootView.findViewById(R.id.
                edit_task_start_date_clear);
            startDateButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    _startDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
                    _startDateOffset = 0;
                    _startDate.setText(R.string.None);
                    refreshFieldVisibility();
                    _changesMade = true;
                }
            });
        }

        // Start Time Clear Button:
        if (_settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
        {
            _rootView.findViewById(R.id.edit_task_start_time_clear).setOnClickListener(
                new
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
                    showRelativeDateTimeDialog(_dueDateOffsetUnits, _dueDateOffset,
                        getString(R.string.Due_Date_), new RelativeDateTimeCallback()
                    {
                        @Override
                        public void onDateTimeChosen(int count, int units)
                        {
                            _dueDate.setText(getRelativeTimestampString(units,count));
                            _dueDateOffsetUnits = units;
                            _dueDateOffset = count;
                            _changesMade = true;
                            refreshFieldVisibility();
                        }

                        @Override
                        public void onCancel()
                        {
                            // Nothing to do.
                        }
                    });
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
                    _dueDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
                    _dueDateOffset = 0;
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
                    if (_dueModifier==null)
                        _dueModifier = "";
                    if (_dueModifier.equals("due_on"))
                    {
                        advancedIndex = 1;
                    }
                    if (_dueModifier.equals("optionally_on"))
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
                                _dueModifier = "optionally_on";
                                _dueDateAdvanced.setText(options[2]);
                            }
                            else if (which==1)
                            {
                                _dueModifier = "due_on";
                                _dueDateAdvanced.setText(options[1]);
                            }
                            else
                            {
                                _dueModifier = "due_by";
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
                    showRelativeDateTimeDialog(_reminderDateOffsetUnits, _reminderDateOffset,
                        getString(R.string.Reminder_Date_), new RelativeDateTimeCallback()
                    {
                        @Override
                        public void onDateTimeChosen(int count, int units)
                        {
                            _reminderDate.setText(getRelativeTimestampString(units,count));
                            _reminderDateOffsetUnits = units;
                            _reminderDateOffset = count;
                            _changesMade = true;
                            refreshFieldVisibility();
                        }

                        @Override
                        public void onCancel()
                        {
                            // Nothing to do.
                        }
                    });
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
                    _reminderDateOffsetUnits = TaskTemplate.OFFSET_UNUSED;
                    _reminderDateOffset = 0;
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
                    if (_repeatSpinnerDisableCount==0)
                    {
                        String[] repeatOptions = _res.getStringArray(R.array.repeat_options);
                        if (position==(repeatOptions.length-1))
                        {
                            // Start the activity that gets the advanced repeat string:
                            _scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).
                                getScrollY();
                            if ((System.currentTimeMillis()-_advancedRepeatBlockerTimestamp)>1500)
                                startAdvancedRepeatActivity(GET_ADVANCED_REPEAT);
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
                        _repeatSpinnerDisableCount--;
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

            // Advanced Repeat Option (tapping on the description):
            _rootView.findViewById(R.id.edit_task_repeat_advanced_container).setOnClickListener(
                new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    // Start the activity that gets the advanced repeat string:
                    _scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).
                        getScrollY();
                    startAdvancedRepeatActivity(GET_ADVANCED_REPEAT2);
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
                        alert.setTitle(Util.getString(R.string.Add_a_Folder));
                        alert.setMessage(Util.getString(R.string.Enter_Folder_Name));
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
        }

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
                        alert.setTitle(Util.getString(R.string.Add_a_Context));
                        alert.setMessage(Util.getString(R.string.Enter_Context_Name));
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
        }

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
                        alert.setTitle(Util.getString(R.string.Add_a_Goal));
                        alert.setMessage(Util.getString(R.string.Enter_Goal_Name));
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
        }

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
        }

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

        // Tag Button:
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
                    _scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(i,GET_TAGS);
                }
            });
        }

        // Sharing button:
        _rootView.findViewById(R.id.edit_task_shared_with_container).setOnClickListener(
            new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // We can't update sharing if the template is a subtask.
                if (_op==EDIT && _tt.parent_id>0)
                {
                    Util.longerPopup(_a, null, Util.getString(R.string.Cannot_share_subtask));
                    return;
                }

                String selectedAccount = _a.getSpinnerSelection(_account);
                UTLAccount acct = _accountsDB.getAccount(selectedAccount);
                if (acct!=null)
                {
                    // Get a list of collaborators the task can be shared with, excluding myself:
                    long aID = acct._id;
                    CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
                    Cursor c = cdb.queryCollaborators("account_id=" + aID + " and sharable=1 and " +
                        "remote_id!='" + Util.makeSafeForDatabase(acct.td_userid) + "'", "name");
                    if (c.getCount() == 0)
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
                    if (_sharedWith.getText().length() > 0 &&
                        !_sharedWith.getText().toString().equals(Util.getString(R.string.None)))
                    {
                        String[] currentCollNames = _sharedWith.getText().toString().split(", ");
                        String[] currentCollIDs = new String[currentCollNames.length];
                        for (int i = 0; i < currentCollNames.length; i++)
                        {
                            UTLCollaborator co = cdb.getCollaboratorByName(aID, currentCollNames[i]);
                            if (co != null)
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
                    in.putExtra("title", Util.getString(R.string.Select_Collaborators));
                    _scrollPositionY = _rootView.findViewById(R.id.edit_task_scrollview).getScrollY();
                    startActivityForResult(in, GET_SHARING);
                }
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
                    _starOn = !_starOn;
                    ImageView starB = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
                    if (_starOn)
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

        // Checking/unchecking the completed checkbox:
        _rootView.findViewById(R.id.edit_task_completed_button).setOnClickListener(
            new View.OnClickListener()
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

        // Tapping on the overall priority container triggers the spinner:
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

            // Tapping on the overall status container triggers the spinner:
            _rootView.findViewById(R.id.edit_task_status_container).setOnClickListener(
                new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    _status.performClick();
                }
            });
        }

        // Handler for account spinner:
        if (_numAccounts>1)
        {
            // The delay prevents the OnItemSelectedListener from being called as the Activity
            // initializes.
            new Handler().postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    _account.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
                    {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
                        {
                            // Whenever the account changes, the list of collaborators needs to be cleared.
                            _changesMade = true;
                            _sharedWith.setText(R.string.None);
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent)
                        {
                            // Nothing to do.
                        }
                    });
                }
            },1000);
        }

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
            ImageButton viewContact = (ImageButton)_rootView.findViewById(R.id.
                edit_task_contact_view);
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
        _rootView.findViewById(R.id.edit_task_note_clear).setOnClickListener(new View.
            OnClickListener()
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
        _rootView.findViewById(R.id.edit_task_note_edit).setOnClickListener(new View.
            OnClickListener()
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
        _note.addTextChangedListener(new TextWatcher()
        {
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
    }

    /** Called just after onCreate() when the activity is restored after an orientation
    change.  Not supported by fragments, but will be called from onActivityCreated(). */
    protected void onRestoreInstanceState(Bundle b)
    {
        // Restore the content of views that are not saved automatically:
        Util.restoreInstanceState(_a, _viewIDsToSave, b);

        // Add links to the note field:
        addLinksToNote();

        // Restore star:
        ImageView star = (ImageView)_rootView.findViewById(R.id.edit_task_star_button);
        if (star!=null)
        {
            if (b.containsKey("star_on") && b.getBoolean("star_on"))
            {
                star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_on_inv));
                _starOn = true;
            }
            else
            {
                star.setImageResource(_a.resourceIdFromAttr(R.attr.ab_star_off_inv));
                _starOn = false;
            }
        }

        // Restore completion state:
        if (b.containsKey("is_completed"))
        {
            updateCompletedCheckbox(b.getBoolean("is_completed"),_priority.getSelectedItemPosition());
        }

        // Restore due modifier:
        if (b.containsKey("due_modifier"))
            _dueModifier = b.getString("due_modifier");
        if (_dueModifier==null)
            _dueModifier = "";

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
                    _repeatAdvanced.setTag(b.getString("advanced_repeat_tag"));
                }
                else
                    _repeatAdvanced.setText(R.string.None);

                // Need to block the spinner's change listener from firing:
                if (_isOnlyFragment)
                    _repeatSpinnerDisableCount++;
                else
                    _repeatSpinnerDisableCount+=2;
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
        _askForTitle = false;
        if (_op==ADD && (title.getText().length()==0 ||
            title.getText().toString().equals(Util.getString(R.string.None))))
        {
            _askForTitle = true;
        }
    }

    /** Populate the action bar buttons and menu */
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        if (!_isOnlyFragment)
        {
            // If there are other fragments on the screen, we do not populate the action bar.
            return;
        }

        menu.clear();
        inflater.inflate(R.menu.save_cancel, menu);
    }

    /** Handler for Activity results. */
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

        switch (requestCode)
        {
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
                        _repeatAdvanced.setTag(extras.getString("text"));

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
                _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,_scrollPositionY);
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
                        _repeatAdvanced.setTag(extras.getString("text"));

                        // Make the advanced repeat TextView visible:
                        _repeatAdvanced.setVisibility(View.VISIBLE);
                        _changesMade = true;
                        refreshFieldVisibilityDelay();
                    }
                }
                // Scroll to the same spot we were in before the button was pressed:
                _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,_scrollPositionY);
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
                _rootView.findViewById(R.id.edit_task_scrollview).scrollTo(0,_scrollPositionY);
                break;

            case NEW_LOCATION:
                if (resultCode==Activity.RESULT_OK)
                {
                    if (extras!=null && extras.containsKey("location_name"))
                        refreshLocationSpinner(null, extras.getString("location_name"),true);
                    else
                        refreshLocationSpinner(null,null,true);
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
                    String accountName = _a.getSpinnerSelection(_account);
                    UTLAccount acct = _accountsDB.getAccount(accountName);
                    if (acct!=null)
                    {
                        for (int i = 0; i < collIDs.length; i++)
                        {
                            if (i > 0)
                                sharedWith += ", ";
                            UTLCollaborator co = cdb.getCollaborator(acct._id,collIDs[i]);
                            if (co != null)
                                sharedWith += co.name;
                        }
                        _sharedWith.setText(sharedWith);
                    }
                    if (sharedWith.length()==0)
                        _sharedWith.setText(R.string.None);
                }
                break;
        }
    }

    /** Set the header title at the top of the screen or fragment. */
    private void setTitle(int resID)
    {
        if (_isOnlyFragment)
            _a.getSupportActionBar().setTitle(resID);
        else
        {
            _saveCancelBar.setTitle(_a.getString(resID));
        }
    }

    /** Hide or show certain fields based on whether other fields have a value. */
    private void refreshFieldVisibility()
    {
        // Start Time:
        ViewGroup startTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
            edit_task_start_time_container);
        if (!_settings.getBoolean(PrefNames.START_DATE_ENABLED,true) ||
            !_settings.getBoolean(PrefNames.START_TIME_ENABLED,true) ||
            _startDateOffsetUnits==TaskTemplate.OFFSET_UNUSED ||
            _startDateOffsetUnits==TaskTemplate.OFFSET_HOURS ||
            _startDateOffsetUnits==TaskTemplate.OFFSET_MINUTES)
        {
            startTimeContainer.setVisibility(View.GONE);
        }
        else
            startTimeContainer.setVisibility(View.VISIBLE);

        // Due Time:
        ViewGroup dueTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
            edit_task_due_time_container);
        if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED,true) ||
            !_settings.getBoolean(PrefNames.DUE_TIME_ENABLED,true) ||
            _dueDateOffsetUnits==TaskTemplate.OFFSET_UNUSED ||
            _dueDateOffsetUnits==TaskTemplate.OFFSET_HOURS ||
            _dueDateOffsetUnits==TaskTemplate.OFFSET_MINUTES)
        {
            dueTimeContainer.setVisibility(View.GONE);
        }
        else
            dueTimeContainer.setVisibility(View.VISIBLE);

        // Due date advanced option:
        ViewGroup dueDateAdvancedContainer = (ViewGroup)_rootView.findViewById(R.id.
            edit_task_due_date_advanced_container);
        if (_dueDateOffsetUnits==TaskTemplate.OFFSET_UNUSED ||
            !_settings.getBoolean(PrefNames.DUE_DATE_ENABLED,true))
        {
            dueDateAdvancedContainer.setVisibility(View.GONE);
        }
        else
            dueDateAdvancedContainer.setVisibility(View.VISIBLE);

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
            if (_startDateOffsetUnits==TaskTemplate.OFFSET_UNUSED || _dueDateOffsetUnits==
                TaskTemplate.OFFSET_UNUSED)
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

        // Reminder Options:
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
            ViewGroup reminderTimeContainer = (ViewGroup)_rootView.findViewById(R.id.
                edit_task_reminder_time_container);
            if (_reminderDateOffsetUnits==TaskTemplate.OFFSET_UNUSED ||
                _reminderDateOffsetUnits==TaskTemplate.OFFSET_HOURS ||
                _reminderDateOffsetUnits==TaskTemplate.OFFSET_MINUTES)
            {
                reminderTimeContainer.setVisibility(View.GONE);
            }
            else
                reminderTimeContainer.setVisibility(View.VISIBLE);

            ViewGroup nagContainer = (ViewGroup)_rootView.findViewById(R.id.
                edit_task_reminder_nag_container);
            if (_reminderDateOffsetUnits==TaskTemplate.OFFSET_UNUSED)
                nagContainer.setVisibility(View.GONE);
            else
                nagContainer.setVisibility(View.VISIBLE);
        }

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
            String selectedAccount = _a.getSpinnerSelection(_account);
            UTLAccount acct = _accountsDB.getAccount(selectedAccount);
            if (acct!=null)
            {
                if (acct.sync_service==UTLAccount.SYNC_TOODLEDO)
                {
                    _rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.
                        VISIBLE);
                }
                else
                {
                    _rootView.findViewById(R.id.edit_task_shared_with_table).setVisibility(View.
                        GONE);
                }
            }
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

    /** Refresh field visibility after a short delay. */
    private void refreshFieldVisibilityDelay()
    {
        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                refreshFieldVisibility();
            }
        },250);
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

    // Remove links from the note.  Needed for editing:
    private void removeLinksFromNote()
    {
        _note.setText(_noteWithoutLinks);
        _rootView.findViewById(R.id.edit_task_note_edit).setVisibility(View.GONE);
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
            if (_op==EDIT && _tt!=null)
                return _tt.completed;
            else
                return false;
        }
        return isCompleted;
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

    // Refresh the location spinner.  The first 2 inputs here may be null.
    private void refreshLocationSpinner(Bundle savedInstanceState, String defaultLocName,
        boolean wasAccountFound)
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
        else if (_op==ADD || !wasAccountFound)
        {
            // Retrieve all locations from all accounts, removing duplicate names and
            // sorting.
            SortedSet<String> locNames = new TreeSet<String>();
            Cursor c = _locationsDB.getAllLocations();
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
            Cursor c = _locationsDB.queryLocations("account_id="+_tt.account_id, "title");
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

    /** Handles for action / menu bar items. */
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
        }

        return super.onOptionsItemSelected(item);
    }

    /** Given the units (days, weeks, etc) and a count, generate a readable string for a relative
     * timestamp. This is used for start dates, due dates, and reminder dates.
     * @param units - One of the OFFSET_ constants in TaskTemplate
     * @param count - The number of those units. */
    public String getRelativeTimestampString(int units, int count)
    {
        switch(units)
        {
            case TaskTemplate.OFFSET_UNUSED:
                return getString(R.string.None);

            case TaskTemplate.OFFSET_MINUTES:
                if (count==1)
                    return getString(R.string.in_1_minute);
                else
                {
                    return getString(R.string.in_x_minutes).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            case TaskTemplate.OFFSET_HOURS:
                if (count==1)
                    return getString(R.string.in_1_hour);
                else
                {
                    return getString(R.string.in_x_hours).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            case TaskTemplate.OFFSET_DAYS:
                if (count==1)
                    return getString(R.string.Tomorrow);
                else if (count==0)
                    return getString(R.string.Today2);
                else
                {
                    return getString(R.string.in_x_days).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            case TaskTemplate.OFFSET_WEEKS:
                if (count==1)
                    return getString(R.string.in_1_week);
                else
                {
                    return getString(R.string.in_x_weeks).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            case TaskTemplate.OFFSET_MONTHS:
                if (count==1)
                    return getString(R.string.in_1_month);
                else
                {
                    return getString(R.string.in_x_months).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            case TaskTemplate.OFFSET_YEARS:
                if (count==1)
                    return getString(R.string.in_1_year);
                else
                {
                    return getString(R.string.in_x_years).replace("[count]",Integer.valueOf(
                        count).toString());
                }

            default:
                // Should not happen.
                return getString(R.string.None);
        }
    }

    /** Check to see if the parent activity is in resize mode. */
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

    /** Launch a dialog to get a relative date/time.
     * @param defaultUnits - One of the "OFFSET" values from TaskTemplate.
     * @param defaultCount - The number of units. May be -1.
     * @param header - The title to display at the top of the dialog. Not null.
     * @param callback - This receives the users's choice or a notification of cancellation. */
    protected void showRelativeDateTimeDialog(int defaultUnits, int defaultCount, String header,
        final RelativeDateTimeCallback callback)
    {
        // Get the dialog's layout and initialize the units spinner:
        LayoutInflater inflater = _a.getLayoutInflater();
        ViewGroup dialogRoot = (ViewGroup)inflater.inflate(R.layout.
            relative_date_time_dialog,null);
        final Spinner spinner = (Spinner)dialogRoot.findViewById(R.id.
            relative_date_time_units);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(_a,R.layout.
            spinner_large, _res.getStringArray(R.array.time_units));
        spinnerAdapter.setDropDownViewResource(android.R.layout.
            simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        // Set the default count and units:
        final EditText editText = (EditText)dialogRoot.findViewById(R.id.relative_date_time_count);
        if (defaultUnits==TaskTemplate.OFFSET_UNUSED || defaultCount<0)
        {
            editText.setText(Util.getNumberString(0));
            spinner.setSelection(TaskTemplate.OFFSET_DAYS-1);
        }
        else
        {
            editText.setText(Util.getNumberString(defaultCount));
            spinner.setSelection(defaultUnits-1);
        }

        // Initialize and show the dialog:
        new AlertDialog.Builder(_a)
            .setTitle(header)
            .setView(dialogRoot)
            .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    callback.onDateTimeChosen(Integer.parseInt(editText.getText().toString()),
                        spinner.getSelectedItemPosition()+1);
                }
            })
            .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    callback.onCancel();
                }
            })
            .show();
    }

    // Start the activity to get an advanced repeat string:
    protected void startAdvancedRepeatActivity(int identifier)
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

    /** Handler for the back button.  Returns true if key handled, else false. */
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_BACK)
        {
            confirmExitWithoutSave(new ExitWithoutSaveHandler()
            {
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
                    if (_ssMode==Util.SS_2_PANE_NAV_LIST)
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

    /** Ask the user if he wants to save changes.  The input is an instance of ConfirmExitWithoutSave
     * that contains some code to execute if the user chooses to exit without saving. */
    private void confirmExitWithoutSave(final ExitWithoutSaveHandler exitHandler)
    {
        if (!_changesMade || (_op==ADD && _title.getText().toString().length()==0))
        {
            // No changes made.  We can simply exit:
            exitHandler.onExitWithoutSave();
            return;
        }

        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which)
                {
                    case DialogInterface.BUTTON_POSITIVE:
                        // Yes clicked:
                        exitHandler.onSave();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        // No clicked:
                        exitHandler.onExitWithoutSave();
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

    /** Save and return (if the information entered is valid). */
    protected void saveAndReturn()
    {
        String none = Util.getString(R.string.None);

        // Verify that the title is not empty:
        if (_title.getText().toString().length()==0 ||
            _title.getText().toString().equals(none))
        {
            Util.popup(_a, R.string.Please_enter_a_title);
            return;
        }

        // Verify that the title is not too long.
        if (_title.getText().toString().trim().length()>Util.MAX_TASK_TITLE_LENGTH)
        {
            Util.popup(_a,R.string.Title_is_too_long);
            return;
        }

        // If a reminder date is specified, then a reminder time must be specified.
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED,true) && (
            _reminderDateOffsetUnits==TaskTemplate.OFFSET_DAYS ||
            _reminderDateOffsetUnits==TaskTemplate.OFFSET_WEEKS ||
            _reminderDateOffsetUnits==TaskTemplate.OFFSET_MONTHS ||
            _reminderDateOffsetUnits==TaskTemplate.OFFSET_YEARS))
        {
            if (_reminderTime.getText().length()==0 || _reminderTime.getText().toString().equals(
                none))
            {
                Util.popup(_a,R.string.reminder_time_needed);
                return;
            }
        }

        // Verify that a start or due date is set if a repeat option is chosen.
        if (_settings.getBoolean(PrefNames.REPEAT_ENABLED, true))
        {
            if (_repeat.getSelectedItemPosition()>0 && _dueDateOffsetUnits==TaskTemplate.
                OFFSET_UNUSED && _startDateOffsetUnits==TaskTemplate.OFFSET_UNUSED)
            {
                Util.popup(_a, R.string.Repeat_Without_Date);
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

        // Make sure a valid account is selected.
        UTLAccount selectedAccount = _accountsDB.getAccount(_a.getSpinnerSelection(_account));
        if (selectedAccount==null)
        {
            Util.popup(_a,R.string.account_doesnt_exist);
            return;
        }

        // If this template is linked to a Google account, it MUST have a folder.
        TextView tv = (TextView)_folder.getSelectedView();
        String folderName;
        if (tv!=null)
            folderName = tv.getText().toString();
        else
            folderName = none;
        if (selectedAccount.sync_service==UTLAccount.SYNC_GOOGLE && folderName.equals(none))
        {
            if (_op==EDIT)
            {
                // Use the folder of the template we're editing.
                Cursor folder = (new FoldersDbAdapter()).getFolder(_tt.folder_id);
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

        // Determine if the "none" priority should be allowed.  It is not allowed for Toodledo
        // accounts.
        if (selectedAccount.sync_service==UTLAccount.SYNC_TOODLEDO)
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

        // At this point, all fields have been validated. Create an instance of TaskTemplate
        // based on the user's inputs.

        TaskTemplate newTemplate = new TaskTemplate();
        if (_op==EDIT)
        {
            // Copy some fields from the template being edited, that do not come from user
            // inputs.
            newTemplate._id = _tt._id;
        }

        // Account ID:
        newTemplate.account_id = selectedAccount._id;

        // Set the title, stripping out newlines and replacing with spaces.
        newTemplate.title = _title.getText().toString().replace("\n"," ");

        // Completed Status:
        newTemplate.completed = getCheckboxStatus();

        // Folder ID:
        boolean newFolderAdded = false;
        if (!folderName.equals(none))
        {
            Cursor c = _foldersDB.queryFolders("lower(title)='"+Util.makeSafeForDatabase(
                folderName.toLowerCase())+"' and account_id="+selectedAccount._id, "title");
            if (!c.moveToFirst())
            {
                // Second test - to account for non-English languages:
                c.close();
                c = _foldersDB.queryFolders("title='"+Util.makeSafeForDatabase(
                    folderName)+"' and account_id="+selectedAccount._id, "title");
                if (!c.moveToFirst())
                {
                    // We need to add in the folder:
                    long folderID = _foldersDB.addFolder(-1, selectedAccount._id, folderName,
                        false,false);
                    if (folderID==-1)
                    {
                        Util.popup(_a, R.string.Unable_to_add_folder);
                        return;
                    }
                    newTemplate.folder_id = folderID;
                    newFolderAdded = true;
                }
                else
                {
                    // Existing folder found.
                    newTemplate.folder_id = Util.cLong(c, "_id");
                }
            }
            else
            {
                // Existing folder found.
                newTemplate.folder_id = Util.cLong(c, "_id");
            }
            c.close();
        }
        else
        {
            // No folder specified.
            if (selectedAccount.sync_service==UTLAccount.SYNC_GOOGLE && _op==EDIT)
            {
                // For a Google task, a folder MUST be specified. Use the folder of the template
                // we're editing.
                newTemplate.folder_id = _tt.folder_id;
            }
            else
                newTemplate.folder_id = 0;
        }

        // Context ID:
        String contextName = _a.getSpinnerSelection(_context);
        boolean newContextAdded = false;
        if (!contextName.equals(none))
        {
            Cursor c = _contextsDB.queryContexts("lower(title)='"+Util.makeSafeForDatabase(
                contextName.toLowerCase())+"' and account_id="+selectedAccount._id,
                "title");
            if (!c.moveToFirst())
            {
                // Second test - to account for non-English languages:
                c.close();
                c = _contextsDB.queryContexts("title='"+Util.makeSafeForDatabase(
                    contextName)+"' and account_id="+selectedAccount._id,
                    "title");
                if (!c.moveToFirst())
                {
                    // We need to add in the context:
                    long contextID = _contextsDB.addContext(-1, selectedAccount._id, contextName);
                    if (contextID==-1)
                    {
                        Util.popup(_a, R.string.Unable_to_add_context);
                        return;
                    }
                    newTemplate.context_id = contextID;
                    newContextAdded = true;
                }
                else
                {
                    newTemplate.context_id = Util.cLong(c, "_id");
                }
            }
            else
            {
                newTemplate.context_id = Util.cLong(c, "_id");
            }
            c.close();
        }
        else
        {
            // No context specified:
            newTemplate.context_id = 0;
        }

        // Goal ID:
        String goalName = _a.getSpinnerSelection(_goal);
        boolean newGoalAdded = false;
        if (!goalName.equals(none))
        {
            Cursor c = _goalsDB.queryGoals("lower(title)='"+Util.makeSafeForDatabase(
                goalName.toLowerCase())+"' and account_id="+selectedAccount._id, "title");
            if (!c.moveToFirst())
            {
                // Second test - to account for non-English languages:
                c.close();
                c = _goalsDB.queryGoals("title='"+Util.makeSafeForDatabase(
                    goalName)+"' and account_id="+selectedAccount._id, "title");
                if (!c.moveToFirst())
                {
                    // We need to add in the goal:
                    long goalID = _goalsDB.addGoal(-1, selectedAccount._id, goalName, false,
                        -1,1);
                    if (goalID==-1)
                    {
                        Util.popup(_a, R.string.Unable_to_add_goal);
                        return;
                    }
                    newTemplate.goal_id = goalID;
                    newGoalAdded = true;
                }
                else
                {
                    newTemplate.goal_id = Util.cLong(c, "_id");
                }
            }
            else
            {
                newTemplate.goal_id = Util.cLong(c, "_id");
            }
            c.close();
        }
        else
        {
            // No goal specified:
            newTemplate.goal_id = 0;
        }

        // Location ID:
        String locName = _a.getSpinnerSelection(_location);
        if (locName.length()>0 && !locName.equals(none))
        {
            LocationsDbAdapter locDb = new LocationsDbAdapter();
            Cursor c = locDb.queryLocations("account_id="+selectedAccount._id+" and ("+
                "lower(title)='"+Util.makeSafeForDatabase(locName.toLowerCase())+"' or "+
                "title='"+Util.makeSafeForDatabase(locName)+"')",
                null);
            if (c.moveToFirst())
            {
                // Found the location.  Update the template:
                newTemplate.location_id = Util.cLong(c, "_id");
                c.close();
            }
            else
            {
                // Could not find the location.  This should not happen, but we will quietly set
                // the location to nothing to avoid a crash.
                Util.log("Could not find matching location when adding task.");
                c.close();
                newTemplate.location_id = 0;
            }
        }
        else
            newTemplate.location_id = 0;

        // Tags:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            String tagsStr = _tags.getText().toString();
            if (tagsStr.length()>0 && !tagsStr.equals(none))
            {
                String[] tagArray = tagsStr.split(",");
                for (String tag : tagArray)
                    newTemplate.tags.add(tag);
            }
        }

        // Due Modifier:
        if (_dueModifier==null)
            newTemplate.due_modifier = "";
        else
            newTemplate.due_modifier = _dueModifier;

        // Start date and time:
        newTemplate.start_date_offset_units = _startDateOffsetUnits;
        newTemplate.start_date_offset = _startDateOffset;
        newTemplate.start_time_hours = -1;
        newTemplate.start_time_minutes = -1;
        if (newTemplate.start_date_offset_units==TaskTemplate.OFFSET_DAYS ||
            newTemplate.start_date_offset_units==TaskTemplate.OFFSET_WEEKS ||
            newTemplate.start_date_offset_units==TaskTemplate.OFFSET_MONTHS ||
            newTemplate.start_date_offset_units==TaskTemplate.OFFSET_YEARS)
        {
            // There may be a time component as well.
            String timeStr = _startTime.getText().toString();
            if (!timeStr.equals(none) && timeStr.length()>0)
            {
                newTemplate.start_time_hours = Util.getHourFromString(timeStr);
                newTemplate.start_time_minutes = Util.getMinuteFromString(timeStr);
            }
        }

        // Due date and time:
        newTemplate.due_date_offset_units = _dueDateOffsetUnits;
        newTemplate.due_date_offset = _dueDateOffset;
        newTemplate.due_time_hours = -1;
        newTemplate.due_time_minutes = -1;
        if (newTemplate.due_date_offset_units==TaskTemplate.OFFSET_DAYS ||
            newTemplate.due_date_offset_units==TaskTemplate.OFFSET_WEEKS ||
            newTemplate.due_date_offset_units==TaskTemplate.OFFSET_MONTHS ||
            newTemplate.due_date_offset_units==TaskTemplate.OFFSET_YEARS)
        {
            // There may be a time component as well.
            String timeStr = _dueTime.getText().toString();
            if (!timeStr.equals(none) && timeStr.length()>0)
            {
                newTemplate.due_time_hours = Util.getHourFromString(timeStr);
                newTemplate.due_time_minutes = Util.getMinuteFromString(timeStr);
            }
        }

        // Reminder date and time:
        newTemplate.reminder_date_offset_units = _reminderDateOffsetUnits;
        newTemplate.reminder_date_offset = _reminderDateOffset;
        newTemplate.reminder_time_hours = -1;
        newTemplate.reminder_time_minutes = -1;
        if (newTemplate.reminder_date_offset_units==TaskTemplate.OFFSET_DAYS ||
            newTemplate.reminder_date_offset_units==TaskTemplate.OFFSET_WEEKS ||
            newTemplate.reminder_date_offset_units==TaskTemplate.OFFSET_MONTHS ||
            newTemplate.reminder_date_offset_units==TaskTemplate.OFFSET_YEARS)
        {
            // There may be a time component as well.
            String timeStr = _reminderTime.getText().toString();
            if (!timeStr.equals(none) && timeStr.length()>0)
            {
                newTemplate.reminder_time_hours = Util.getHourFromString(timeStr);
                newTemplate.reminder_time_minutes = Util.getMinuteFromString(timeStr);
            }
        }

        // Nagging reminder:
        if (newTemplate.reminder_date_offset_units!=TaskTemplate.OFFSET_UNUSED)
            newTemplate.nag = _reminderNag.isChecked();

        // Repeating Pattern:
        int selection = _repeat.getSelectedItemPosition();
        int count = _repeat.getCount();
        if (selection==(count-1))
        {
            // Advanced repeat option:
            if (_repeatAdvanced.getTag()!=null)
                newTemplate.rep_advanced = (String)_repeatAdvanced.getTag();
            else
                newTemplate.rep_advanced = "";
            if (_repeatFrom.getSelectedItemPosition()==1)
            {
                newTemplate.repeat = 150;
            }
            else
            {
                newTemplate.repeat = 50;
            }
        }
        else
        {
            newTemplate.repeat = selection;
            if (_repeatFrom.getSelectedItemPosition()==1)
            {
                newTemplate.repeat += 100;
            }
        }

        // Status:
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED,true))
            newTemplate.status = _status.getSelectedItemPosition();
        else
            newTemplate.status = 0;

        // Expected Length:
        newTemplate.length = expectedLength;

        // Priority:
        if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED,true))
            newTemplate.priority = _priority.getSelectedItemPosition();
        else
            newTemplate.priority = 0;

        // Star:
        newTemplate.star = _starOn;

        // Note:
        if (!_note.getText().toString().equals(none))
            newTemplate.note = _note.getText().toString();

        // Location Reminder and Location Nag:
        if (newTemplate.location_id>0)
        {
            newTemplate.location_reminder = _locReminder.isChecked();
            newTemplate.location_nag = _locNag.isChecked();
            if (newTemplate.location_nag && !newTemplate.location_reminder)
                newTemplate.location_nag = false;
        }

        // Contact:
        if (_contactLookupKey!=null && _contactLookupKey.length()>0)
            newTemplate.contact_lookup_key = _contactLookupKey;
        else
            newTemplate.contact_lookup_key = "";

        // Sharing:
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED,true) &&
            selectedAccount.sync_service==UTLAccount.SYNC_TOODLEDO &&
            _sharedWith.getText().length()>0 && !_sharedWith.getText().toString().equals(none))
        {
            // Collaborators have been chosen.
            CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
            String[] currentCollNames = _sharedWith.getText().toString().split(", ");
            String currentCollIDs = "";
            for (String name : currentCollNames)
            {
                UTLCollaborator co = cdb.getCollaboratorByName(selectedAccount._id, name);
                if (co!=null)
                {
                    if (currentCollIDs.length()>0)
                        currentCollIDs += "\n";
                    currentCollIDs += co.remote_id;
                }
            }
            newTemplate.shared_with = currentCollIDs;
        }
        else
        {
            // No collaborators have been chosen.
            newTemplate.shared_with = "";
        }

        // Add to calendar:
        if ((newTemplate.due_date_offset_units!=TaskTemplate.OFFSET_UNUSED ||
            newTemplate.start_date_offset_units!=TaskTemplate.OFFSET_UNUSED) &&
            _settings.getBoolean(PrefNames.CALENDAR_ENABLED,true))
        {
            newTemplate.add_to_calendar = _addToCal.isChecked();
        }
        else
            newTemplate.add_to_calendar = false;

        // If any tags were used, make sure they are on the recently used tags list:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            if (!_tags.getText().toString().equals(none))
            {
                CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
                String[] tagArray = _tags.getText().toString().split(",");
                currentTags.addToRecent(tagArray);
            }
        }

        // Create a Bundle to return to Tasker:
        Bundle templateBundle = newTemplate.toBundle(_a);
        log("Bundle sent to tasker: "+Util.bundleToString(templateBundle,2));
        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(_a))
        {
            log("Calling setVariableReplaceKeys().");
            TaskerPlugin.Setting.setVariableReplaceKeys(templateBundle,new String[] {"title",
                "note"});
        }
        else
            log("NOT calling setVariableReplaceKeys().");

        // Exit this fragment:
        if (_isOnlyFragment)
        {
            Intent resultIntent = new Intent();
            resultIntent.putExtra(TaskerReceiver.EXTRA_BUNDLE,templateBundle);
            resultIntent.putExtra(TaskerReceiver.EXTRA_BLURB,newTemplate.title);
            _a.setResult(Activity.RESULT_OK,resultIntent);
            _a.finish();
        }
        else
        {
            // The navigation drawer may need refreshed.
            TaskList tl = (TaskList)_a;
            if (newContextAdded || newFolderAdded || newGoalAdded)
                tl.refreshWholeNavDrawer();

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

    /** Called before the Fragment is destroyed due to an orientation change. */
    @Override
    public void onSaveInstanceState(Bundle b)
    {
        int i;

        // Save the content of views that are not saved automatically:
        Util.saveInstanceState(_a, _viewIDsToSave, b);

        // Save items not covered by previous function:
        b.putBoolean("star_on", _starOn);
        b.putString("due_modifier",_dueModifier);
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

    @Override
    public void onDestroy()
    {
        _a.fragmentHandlesHome(false);
        super.onDestroy();
    }

    /** This class is used to store some code to execute after the user confirms that he wants to
    exit without saving changes: */
    private abstract class ExitWithoutSaveHandler
    {
        abstract public void onExitWithoutSave();

        public void onSave()
        {
            EditTemplateFragment.this.saveAndReturn();
        }
    }

    /** This interface is used to pass time information from the relative date/time dialog into
     * this Activity. */
    private interface RelativeDateTimeCallback
    {
        /** Called when the user has chosen a date/time. */
        void onDateTimeChosen(int count, int units);

        /** Called when the user has cancelled. */
        void onCancel();
    }
}
