package com.customsolutions.android.utl;

// This fragment displays a list of tasks.

// To call this activity, put a Bundle in the Intent with the following keys/values:
// top_level: A string containing the originating home screen option (all_tasks, hotlist...)
// view_name: A string containing the view name (such as a custom name or folder ID).
//    If this is not needed, set it to an empty string.
// title: The page title, to display at the top of the screen (required).

// This task does not return anything to the caller.

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.speech.SpeechRecognizer;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.SimpleAdapter.ViewBinder;
import android.widget.TextView;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.ump.ConsentInformation;
import com.google.api.client.util.Joiner;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class TaskListFragment extends UtlListFragment
{
    private static final String TAG = "TaskListFragment";

	// Selector drawables to use when color coding the checkboxes:
	public static int[] _priorityCheckboxDrawables = new int[] {
		R.drawable.checkbox_medium_gray_selector,
		R.drawable.checkbox_green_selector,
		R.drawable.checkbox_blue_selector,
		R.drawable.checkbox_yellow_selector,
		R.drawable.checkbox_orange_selector,
		R.drawable.checkbox_red_selector
	};
	
	public static int[] _statusCheckboxDrawables = new int[] {
		R.drawable.checkbox_light_gray_selector,  // None
		R.drawable.checkbox_red_selector,         // Next action
		R.drawable.checkbox_orange_selector,      // Active
		R.drawable.checkbox_yellow_selector,      // Planning
		R.drawable.checkbox_cyan_selector,        // Delegated
		R.drawable.checkbox_purple_selector,      // Waiting
		R.drawable.checkbox_pink_selector,        // Hold
		R.drawable.checkbox_blue_selector,        // Postponed
		R.drawable.checkbox_brown_selector,       // Someday
		R.drawable.checkbox_dark_gray_selector,   // Canceled
		R.drawable.checkbox_green_selector        // Reference
	};
	
    // The number of dp to indent subtasks.  Indent by this much per level:
    private static final int SUBTASK_INDENTATION = 15;
    
    // IDs for action bar menu items (when selecting tasks):
    private static final int EDIT_TASK_ID = Menu.FIRST;
    private static final int START_TIMER_ID = Menu.FIRST+1;
    private static final int STOP_TIMER_ID = Menu.FIRST+2;
    private static final int NEW_SUBTASK_ID = Menu.FIRST+3;
    private static final int DELETE_TASK_ID = Menu.FIRST+4;
    private static final int VIEW_SUBTASKS_ID = Menu.FIRST+5;
    private static final int CHANGE_PARENT_ID = Menu.FIRST+6;
    private static final int UNLINK_FROM_PARENT_ID = Menu.FIRST+7;
    private static final int CLONE_ID = Menu.FIRST+8;
    private static final int CHANGE_START_DATE_ID = Menu.FIRST+9;
    private static final int CHANGE_DUE_DATE_ID = Menu.FIRST+10;
    private static final int TOGGLE_STAR_ID = Menu.FIRST+11;
    private static final int CHANGE_FOLDER_ID = Menu.FIRST+12;
    private static final int CHANGE_STATUS_ID = Menu.FIRST+13;
    private static final int VIEW_TASK_ID = Menu.FIRST+14;
    private static final int CHANGE_PRIORITY_ID = Menu.FIRST+15;
    private static final int OPEN_CONTACT_ID = Menu.FIRST+16;
    private static final int MARK_COMPLETE_ID = Menu.FIRST+17;
    private static final int MARK_INCOMPLETE_ID = Menu.FIRST+18;
    private static final int STAR_ON_ID = Menu.FIRST+19;
    private static final int STAR_OFF_ID = Menu.FIRST+20;
    private static final int SELECT_ALL_ID = Menu.FIRST+21;
    private static final int REASSIGN_ID = Menu.FIRST+22;
    private static final int CANCEL_PARENT_SELECTION_ID = Menu.FIRST+23;
    private static final int CHANGE_CONTEXT_ID = Menu.FIRST+24;
    private static final int RESIZE_PANES_ID = Menu.FIRST+25;
    private static final int SHOW_MAP_ID = Menu.FIRST+26;
    private static final int NAVIGATE_ID = Menu.FIRST+27;
    
    // IDs for activities that send us results back:
    private static final int ACTIVITY_EDIT_TASK = 1;
    private static final int ACTIVITY_ADD_TASK = 2;
    private static final int ACTIVITY_ADD_SUBTASK = 3;
    private static final int ACTIVITY_FILTER = 4;
    private static final int ACTIVITY_SORT = 5;
    private static final int ACTIVITY_DISPLAY = 6;
    private static final int ACTIVITY_GET_START_DATE = 7;
    private static final int ACTIVITY_GET_DUE_DATE = 8;

    // IDs for action mode commands in manual sort mode:
    private static final int AM_MANUAL_SORT_SETTINGS = 2;

    // The tag to use when placing this fragment:
    public static final String FRAG_TAG = "TaskListFragment";

    /** The index in the list of the task that precedes the first native ad. */
    public static final int FIRST_NATIVE_AD_INDEX = 5;

    /** The number of tasks in between native ads, after the first ad. */
    public static final int NATIVE_AD_INTERVAL = 11;

    // Maximum number of action bar buttons we can force to show in an activity:
    private static int MAX_ACTION_BAR_BUTTONS = 4;

    public String topLevel;
    public String viewName;
    private Cursor viewCursor;
    private DisplayOptions _displayOptions;
    private long viewID;
    private ArrayList<HashMap<String,UTLTaskDisplay>> _taskList;
    private HashMap<Long,UTLTaskDisplay> _taskHash;  // Maps task IDs to UTLTaskDisplay objects.
    private SimpleAdapter adapter;
    
    // These guard against generating repeating tasks more than once for a particular task:
    private HashSet<Long> repeatingTasksCreated;
    
    // This holds the task ID of the task that the user long-pressed on:
    private long _chosenTaskID = 0;
    
    // Variables for communicating with the Synchronizer service:
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    // State variable.  If true, we have initiated a manual sync and are waiting on a response:
    private boolean waitingOnManualSync;
    
    // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
    // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
    HashMap<Long,ArrayList<UTLTaskDisplay>> subLists = new HashMap<Long,
        ArrayList<UTLTaskDisplay>>();

    // This keeps track of orphaned subtasks that should not be indented.
    HashSet<Long> orphanedSubtaskIDs;
    
    // This flag records if the user authorized marking a parent task (and all subtasks)
    // as complete:
    private boolean authorizedParentCompletion;
    
    // This variable holds a view temporarily:
    private View tempView;
    
    // The index of the last selected item:
    private int _lastSelection = -1;
    
    // Stores midnight on the current day:
    private long _midnightToday;
    
    // Stores the offset between the current time zone and home time zone:
    private long _timeZoneOffset;
    
    // The scale factor to use when sizing view objects:
    private float _viewScaleFactor;
    
    // The current mode of this activity:
    public int _mode;
    public static final int MODE_NORMAL = 1;
    public static final int MODE_SELECTING_NEW_PARENT = 2;
    public static final int MODE_MULTI_SELECT = 3;
    public static final int MODE_MANUAL_SORT = 4;
    
    // Links to key views:
    LinearLayout _syncProgressContainer;
    ProgressBar _syncProgressBar;
    private ListView _listView;
    private SwipeRefreshLayout _swipeRefreshLayout;
    private BottomAppBar _bottomToolbar;
    private FloatingActionButton _fab;
    private FloatingActionButton _fab2;

    // Used for picking folders:
    ArrayList<Long> _idList;

    // This determines if this is a view that is displaying subtasks of a particular task:
    boolean _isSubtaskView;
    long _parentID;
    
    // This holds the IDs and data structures of all selected tasks in multi-select mode:
    HashMap<Long,UTLTask> _selectedTasks;
    
    // This counts the number of instant upload operations performed in a given
    // multi-select command.
    private int _uploadCount;
    
    // This is the maximum number of uploads we will perform.  This must be limited to
    // avoid overloading the device or making Toodledo or Google unhappy.
    private static final int MAX_UPLOADS = 100;
    
    // The flag records if the user has authorized the deletion of multiple tasks:
    private boolean _authorizedMultiTaskDelete = false;
    
    // This is the chosen folder ID in multi-select mode:
    private long _chosenMultiSelectFolderID = -1;
    
 // This is the chosen folder ID in multi-select mode:
    private long _chosenMultiSelectContextID = -1;
    
    // The chosen statuses and priorities in multi-select mode:
    private int _chosenMultiSelectStatus = -1;
    private int _chosenMultiSelectPriority = -1;
    
    // The chosen start and due dates (multi-select mode):
    private long _chosenMultiSelectStartDate = 0;
    private long _chosenMultiSelectDueDate = 0;
    
    // The chosen parent ID in multi-select mode:
    private long _chosenMultiSelectParent = -1;
    
    // The number of errors that occured when applying a change in multi-select mode.
    // This is needed to prevent too many error messages from displaying:
    private int _multiSelectErrorCount = 0;
    
    // For use in choosing collaborators:
    private ArrayList<String> _collIdList;
    
    // Quick reference to the Fragment's activity:
    private TaskList _a;
    
    // Quick reference to resources:
    private Resources _res;
    
    private OnLongClickListener _longClickListener;
    
    private ActionMode _actionMode;
    private boolean _inActionMode = false;
    private View _amDoneButton = null;
    
    // Quick reference to settings:
    private SharedPreferences _settings;
    
    // Quick reference to the current split-screen mode:
    private int _ssMode;
    
    // Quick reference to this Fragment's root view:
    private ViewGroup _rootView;
    
    public String _title;
    
    public boolean _isFirstRefresh;
    
    // Used for in-app billing and licensing status:
    private PurchaseManager _pm;
 	
    // The 1st level sort field:
    private String _firstSortField;

    /** This listener handles drag events that occur when the user drags a task item over a view */
    private Object _onDragListener;

    /** This listener handles touch events (down and up) on a view. */
    private View.OnTouchListener _onTouchListener;

    /** The screen's display density is used for effects when dragging and dropping tasks */
    private float _displayDensity;

    /** Set this to non-zero to enable automatic scrolling.  Is used for drag and drop. */
    private int _autoScroll = 0;

    /** The ID of a task being dragged, when performing a manual sort operation. */
    private long _draggedTaskID;

    /** Flag indicating whether the manual sort add-on is purchased. */
    private boolean _manualSortPurchased;

    /** Flag indicating if manual sort mode was triggered through a long-press action. */
    private boolean _manualSortFromLongPress = false;

    /** A unique ID, used to identify this fragment to the native ad utilities. */
    private long _nativeAdID;

    /** Flag indicating if the GDPR consent dialog has been shown. */
    private boolean _gdprConsentDialogShown;

    // This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.task_list, container, false);
    }
    
    // Called when activity is first created:
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        _a = (TaskList) getActivity();
        _res = _a.getResources();
        _settings = _a._settings;
        _ssMode = _a.getSplitScreenOption();
        _rootView = (ViewGroup)getView();
        _isFirstRefresh = true;
        _pm = new PurchaseManager(_a);
        _firstSortField = "";
        _gdprConsentDialogShown = false;
        _nativeAdID = System.currentTimeMillis();
        
        // Determine the maximum number of action bar buttons we can force to show on the screen.
        // Tablet devices can display more.  We use this because android tends to not show buttons
        // when the "if room" option is used, even when their really is room.
        MAX_ACTION_BAR_BUTTONS = 4;
        if (_a.getOrientation()==UtlActivity.ORIENTATION_LANDSCAPE || _settings.getFloat(
        	PrefNames.DIAGONAL_SCREEN_SIZE, 5.0f)>7.5)
        {
        	MAX_ACTION_BAR_BUTTONS = 5;
        }
        
        // Extract the parameters from the Bundle passed in.  Try the savedInstanceState first, 
        // followed by fragment arguments, then check for extras in the intent passed to the parent 
        // activity.
        Bundle extras;
        if (savedInstanceState!=null && savedInstanceState.containsKey("top_level") &&
        	savedInstanceState.containsKey("view_name") && savedInstanceState.containsKey("title"))
        {
        	extras = savedInstanceState;
        }
        else
        {
        	extras = getArguments();
        	if (extras==null || extras.size()==0)
        	{
        		extras = _a.getIntent().getExtras();
        	}
        }
        if (extras==null)
        {
            Util.log("Null Bundle passed into TaskList.onActivityCreated().");
            getActivity().finish();
            return;
        }
        if (!extras.containsKey("top_level"))
        {
            Util.log("No top_level passed into TaskList.onCreate().");
            getActivity().finish();
            return;
        }
        if (!extras.containsKey("view_name"))
        {
            Util.log("No view_name passed into TaskList.onCreate().");
            getActivity().finish();
            return;
        }
        topLevel = extras.getString("top_level");
        viewName = extras.getString("view_name");
        
        // Set the screen title:
        if (!extras.containsKey("title"))
        {
            Util.log("No title passed into TaskList.onCreate().");
            getActivity().finish();
            return;
        }
        _a.getSupportActionBar().setTitle(extras.getString("title"));
        _title = extras.getString("title");
        
        repeatingTasksCreated = new HashSet<Long>();
        waitingOnManualSync = false;
        authorizedParentCompletion = false;
        _viewScaleFactor = _a.getResources().getDisplayMetrics().density;
        
    	// Link to key views:
    	_syncProgressContainer = (LinearLayout)getView().findViewById(R.id.sync_status_progress_bar_container);
    	_syncProgressBar = (ProgressBar)getView().findViewById(R.id.sync_status_progress_bar);
        _listView = (ListView)getView().findViewById(android.R.id.list);
        _swipeRefreshLayout = (SwipeRefreshLayout)getView().findViewById(R.id.task_list_swipe_refresh);
    	_bottomToolbar = (BottomAppBar) getView().findViewById(R.id.task_list_bottom_toolbar);

        AccountsDbAdapter db = new AccountsDbAdapter();
        Cursor c = db.getAllAccounts();
        if (!c.moveToFirst())
        {
        	// The user has managed to get here without setting up an account first.
        	c.close();
            Intent i = new Intent(_a,main.class);
            startActivity(i);
            _a.finish();
            return;
        }
        else
        	c.close();

        // We start off in normal mode (not selecting a task):
        if (!_inActionMode)
        {
        	handleModeChange(MODE_NORMAL);
        }
        
        Util.logOneTimeEvent(_a, "view_task_list", 0, null);
        
        if ((System.currentTimeMillis()-_pm.getInstallDate()) > Util.ONE_DAY_MS)
        	Util.logOneTimeEvent(_a, "access_after_first_day", 0, null);

        Util.pingServer(_a);

        // Check to see if the screen was rotated while in multi-select mode:
        _selectedTasks = new HashMap<Long,UTLTask>();
        if (savedInstanceState!=null && savedInstanceState.containsKey("mode") &&
        	savedInstanceState.containsKey("selected_tasks"))
        {
        	int mode = savedInstanceState.getInt("mode");
        	
        	// If a single tap on a task would launch a new Activity, then we don't highlight/select
        	// any tasks now.
        	if (mode!=MODE_NORMAL || !_a.useNewActivityForDetails())
        	{
	    		// We need to re-read the list of selected tasks from the database:
	    		TasksDbAdapter tasksDB = new TasksDbAdapter();
	    		long[] taskIDs = savedInstanceState.getLongArray("selected_tasks");
	    		if (taskIDs!=null && taskIDs.length>0)
	    		{
	    			UTLTask t;
	    			for (int i=0; i<taskIDs.length; i++)
	    			{
	    				t = tasksDB.getTask(taskIDs[i]);
	    				if (t!=null)
	    				{
	    					_selectedTasks.put(taskIDs[i], t);
	    				}
	    			}
	    		}
	    		
	    		if (_selectedTasks.size()==1)
	    		{
	    			_chosenTaskID = _selectedTasks.keySet().iterator().next();
	    		}
	    		
	        	if (mode==MODE_MULTI_SELECT || mode==MODE_SELECTING_NEW_PARENT)
	        		handleModeChange(mode);
	        	if (mode==MODE_MULTI_SELECT)
	        		_inActionMode = true;
	        }
        }
        
        if (savedInstanceState==null)
        {
        	// We know this not an orientation change, so make sure we are not in action mode.
        	if (_a.inActionMode())
        	{
        		_a.getActionMode().finish();
        		_a.recordActionModeEnded();
        	}
        }

        updateBottomToolbarAppearance();

        // This fragment will be updating the Action Bar:
        setHasOptionsMenu(true);
        
        if (savedInstanceState != null)
        {
        	if (savedInstanceState.containsKey("waiting_on_manual_sync"))
        		waitingOnManualSync = savedInstanceState.getBoolean("waiting_on_manual_sync");
        	if (savedInstanceState.containsKey("context_menu_task_id"))
        		_chosenTaskID = savedInstanceState.getLong("context_menu_task_id");
        }
        
        // Need to set the choice mode to allow for multiple sections using contextual action bar:
        _listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // A scroll handler for the listview, which enables the swipe refresh control only when needed.
        // This also manages auto scrolling for drag and drop / manuual sort.
        _listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState)
            {
                if (scrollState==SCROLL_STATE_IDLE && _autoScroll!=0)
                {
                    _listView.smoothScrollByOffset(_autoScroll);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int topRowVerticalPosition = (_listView == null || _listView.getChildCount() == 0) ?
                     0 : _listView.getChildAt(0).getTop();
                _swipeRefreshLayout.setEnabled(firstVisibleItem == 0 && topRowVerticalPosition >= 0);
            }
        });

        _swipeRefreshLayout.setEnabled(false);
        _swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                _swipeRefreshLayout.setRefreshing(true);
                startSync();
                ( new Handler()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        _swipeRefreshLayout.setRefreshing(false);
                    }
                }, 3000);
            }
        });

        // If the user currently holds a license purchased prior to 3.0, then we may want to 
        // display a popup asking them to upgrade.
        if (_pm.stat()==PurchaseManager.DONT_SHOW_ADS &&
            _pm.licenseType()==PurchaseManager.LIC_TYPE_EXT_APP)
        {
            long upgradeNoticeTime = _settings.getLong(PrefNames.UPGRADE_NOTIFICATION_TIME, 0L);
            if (upgradeNoticeTime==0)
                Log.v(TAG,"Upgrade notice message is blocked.");
            else
            {
                Log.v(TAG,"Next upgrade notice: "+Util.getDateTimeString(upgradeNoticeTime));
                if (System.currentTimeMillis() > upgradeNoticeTime)
                    _a.startActivity(new Intent(_a,LicenseUpgradeNotice.class));
            }
        }

        // Handler for tapping on the orphaned subtask warning:
        _rootView.findViewById(R.id.task_list_orphaned_subtask_warning).setOnClickListener(new
            View.OnClickListener() {
            @Override
            public void onClick (View v)
            {
                Intent i = new Intent(_a,EditDisplayOptions.class);
                i.putExtra("view_id", viewID);
                _a.startActivityForResult(i, ACTIVITY_DISPLAY);
            }
        });
        
        // This handler is called when someone long-presses on a task:
        _longClickListener = new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v)
			{
			    v = v.findViewById(R.id.task_list_main_area);

		        // Do nothing if we're in resize mode:
		        if (_a.inResizeMode())
		        	return true;

                if (_mode==MODE_MANUAL_SORT)
                {
                    // A long press does nothing in manual sort mode.
                    return true;
                }

                if (_settings.getBoolean(PrefNames.MANUAL_SORT_ON_LONG_PRESS,false) &&
                    _firstSortField.equals("tasks.sort_order"))
                {
                    boolean inManualSortMode = handleManualSortCommand();
                    if (inManualSortMode)
                    {
                        v.startDrag(null, new View.DragShadowBuilder((View)v.getParent()), null, 0);
                        ViewGroup vg = (ViewGroup)v;
                        TextView hiddenText = (TextView)vg.getChildAt(0);
                        _draggedTaskID = Long.parseLong(hiddenText.getText().toString());
                        _manualSortFromLongPress = true;
                    }
                    return true;
                }

                // Get the task ID and task data that is selected:
		        ViewGroup vg = (ViewGroup)v;
		        TextView hiddenText = (TextView)vg.getChildAt(0);
		        long lastChosenTaskID = _chosenTaskID;
		        _chosenTaskID = Long.parseLong(hiddenText.getText().toString());

                if (_mode==MODE_MULTI_SELECT)
		        {
		        	// We're already in multi-select mode, so just treat this as a normal tap:
		        	TaskListFragment.this.handleTaskClick(v);
					return true;
		        }
		        
		        if (_mode==MODE_NORMAL)
	        	{
		        	// When moving from normal mode to multi-select, make sure the selected tasks are
		        	// cleared:
	        		if (_selectedTasks==null)
	        			_selectedTasks = new HashMap<Long,UTLTask>();
	        		else
	        			_selectedTasks.clear();
	        		
	        		// If an existing task is selected (if we're in split-screen mode),
	        		// then unselect it:
	        		unselectLastSelected(lastChosenTaskID);
	        	}

				handleModeChange(MODE_MULTI_SELECT);

				TaskListFragment.this.handleTaskClick(v);
				return true;
			}
        };

        // Fetch the display density:
        DisplayMetrics metrics = new DisplayMetrics();
        _a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        _displayDensity = metrics.density;

        // Add a footer view to the list. This ensures the + sign at the bottom doesn't hide
        // crucial task details.
        _a.addListViewFooter(_listView,_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT);

        // Handlers for various drag and drop events.  Views in the list will have this assigned
        // to them.
        _onDragListener = new View.OnDragListener()
        {
            // Runnables for initiating auto-scrolling:
            private Runnable _scrollUpRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    _listView.smoothScrollByOffset(-1);
                    _autoScroll = -1;
                }
            };
            private Runnable _scrollDownRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    _listView.smoothScrollByOffset(1);
                    _autoScroll = 1;
                }
            };
            private Handler _scrollHandler = new Handler();

            @TargetApi(11)
            @Override
            public boolean onDrag(View v, DragEvent event)
            {
                if (_mode!=MODE_MANUAL_SORT)
                {
                    Log.d(TAG,"onDrag() called when mode is "+_mode+". Mode should be manual " +
                        "sort.");
                    return false;
                }

                View marker;
                LinearLayout.LayoutParams lp;

                // Get a reference to the divider above the main task area:
                marker = ((ViewGroup)v.getParent()).findViewById(
                    R.id.task_list_insertion_marker);

                // Get the task ID for the view:
                long taskID = 0;
                try
                {
                    taskID = (Long)v.getTag();
                }
                catch (Exception e) { }

                switch (event.getAction())
                {
                    case DragEvent.ACTION_DRAG_STARTED:
                        break;

                    case DragEvent.ACTION_DRAG_ENTERED:
                        Log.i(TAG,"Drag entered task ID "+taskID);

                        // Show the insertion marker
                        marker.setVisibility(View.VISIBLE);

                        // Check to see if we need to scroll up or down.  We scroll when we
                        // enter the top or bottom 2 tasks.
                        int lastPosition = _listView.getLastVisiblePosition();
                        int secondToLast = _listView.getLastVisiblePosition()-1;
                        int firstPosition = _listView.getFirstVisiblePosition();
                        int secondPosition = firstPosition+1;
                        if (secondToLast<0) secondToLast=0;
                        if (secondPosition>=_listView.getChildCount())
                            secondPosition = _listView.getChildCount()-1;
                        if (lastPosition>=_taskList.size())
                        {
                            // 11/20/2020: This should not happen, but a customer has had
                            // crashes here because it does.
                            Log.d(TAG,"getLastVisiblePosition() returned an invalid value of "+
                                lastPosition);
                            lastPosition = _taskList.size()-1;
                            secondToLast = lastPosition - 1;
                        }
                        if (_taskList.get(lastPosition).get("task").task._id == (Long)v.getTag() ||
                            _taskList.get(secondToLast).get("task").task._id == (Long)v.getTag())
                        {
                            if (_autoScroll==0)
                                _scrollHandler.postDelayed(_scrollDownRunnable,1000);
                            else
                                _autoScroll = 1;
                        }
                        else if (_taskList.get(firstPosition).get("task").task._id ==
                            (Long)v.getTag() ||  _taskList.get(secondPosition).get("task").
                            task._id == (Long)v.getTag())
                        {
                            if (_autoScroll==0)
                                _scrollHandler.postDelayed(_scrollUpRunnable,1000);
                            else
                                _autoScroll = -1;
                        }
                        else
                        {
                            _scrollHandler.removeCallbacks(_scrollDownRunnable);
                            _scrollHandler.removeCallbacks(_scrollUpRunnable);
                            _autoScroll = 0;
                        }

                        break;

                    case DragEvent.ACTION_DRAG_EXITED:
                        Log.i(TAG,"Drag exited task ID "+taskID);

                        // Remove the insertion marker:
                        marker.setVisibility(View.GONE);

                        break;

                    case DragEvent.ACTION_DROP:
                        Log.i(TAG,"Drop on task ID "+taskID);

                        // Remove the insertion marker:
                        marker.setVisibility(View.GONE);

                        // Cancel any pending scroll operations:
                        _scrollHandler.removeCallbacks(_scrollDownRunnable);
                        _scrollHandler.removeCallbacks(_scrollUpRunnable);
                        _autoScroll=0;

                        // Update the display and database:
                        handleManualSort((Long)v.getTag());

                        break;

                    case DragEvent.ACTION_DRAG_ENDED:
                        // This code is needed in case the user drags the task off-screen,
                        // in which case there is no drop event.

                        // Remove the insertion marker:
                        marker.setVisibility(View.GONE);

                        // Cancel any pending scroll operations:
                        _scrollHandler.removeCallbacks(_scrollDownRunnable);
                        _scrollHandler.removeCallbacks(_scrollUpRunnable);
                        _autoScroll=0;

                        break;
                }
                return true;
            }
        };


        // This listener responds to touch events on tasks.  If we're in manual sort mode,
        // pressing down on a task initiates drag and drop.
        _onTouchListener = new View.OnTouchListener()
        {

            @Override
            @TargetApi(11)
            public boolean onTouch(View v, MotionEvent event)
            {
                if (event.getAction()==android.view.MotionEvent.ACTION_DOWN && _mode==
                    MODE_MANUAL_SORT)
                {
                    // Start the actual drag and drop process:
                    v.startDrag(null, new View.DragShadowBuilder((View)v.getParent()), null, 0);

                    _draggedTaskID = (Long)v.getTag();

                    return true;
                }
                else
                    return false;
            }
        };

        // Handle a tap on the add button:
        _fab = _rootView.findViewById(R.id.task_list_fab);
        _fab.setOnClickListener((View v) -> {
            startAddingTask();
        });
        _fab2 = _rootView.findViewById(R.id.task_list_fab2);
        _fab2.setOnClickListener((View v) -> {
            startAddingTask();
        });
    }
    
    @SuppressLint("StaticFieldLeak")
    public void refreshData()
    {        
    	// When refreshing, we will update or hide the free trial text:
    	int stat = _pm.stat();
    	switch (stat)
    	{
    	case PurchaseManager.IN_BETA:
    	case PurchaseManager.BETA_EXPIRED:
    		if (_pm.enforceBeta(_a))
    			return;
    		break;
    		
    	case PurchaseManager.DONT_SHOW_ADS:
    	    // Verify the purchase if needed:
    		_pm.verifyLicensePurchase(false);
    		
    		break;
    		
    	case PurchaseManager.SHOW_ADS:
    		// Link to the app store and run a check to see if we have purchased (if a check has not
    		// been recently run).
    		_pm.link();
    		_pm.verifyLicensePurchase(false);
    	}

        // Refresh the status of the manual sort add-on:
        _manualSortPurchased = _pm.isPurchased(PurchaseManager.SKU_MANUAL_SORT);
        if (!_manualSortPurchased)
            _pm.link();
    	
    	// Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
    		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
    	_timeZoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	
    	// Get the timestamp in ms at midnight today.  This is needed for due date
    	// color-coding.
    	_midnightToday = Util.getMidnight(System.currentTimeMillis()+_timeZoneOffset);
    	
        // Get the data on the view to display:
        viewCursor = (new ViewsDbAdapter()).getView(topLevel,viewName);
        if (!viewCursor.moveToFirst())
        {
            Util.popup(_a, "Internal Error: View is not defined.");
            Util.log("View is not defined in TaskList.onCreate().");
            return;
        }
        viewID = Util.cLong(viewCursor, "_id");
        
        // Get the first sort field:
        String[] sortFields = Util.cString(viewCursor, "sort_string").split(",");
        _firstSortField = sortFields[0];
        
        // Initialize the Display Options:
        _displayOptions = new DisplayOptions(Util.cString(viewCursor, "display_string"));
        
        // Initialize the list of orphaned subtasks:
        if (orphanedSubtaskIDs==null)
        	orphanedSubtaskIDs = new HashSet<Long>();
        else
        	orphanedSubtaskIDs.clear();
        
        // Query the database for the task info:
        String query = generateSQLQuery();
        if (query.length()==0)
        {
        	_a.finish();
        	return;
        }
        Cursor c = Util.db().rawQuery(query, null);
        
        // Convert the results of the DB query into the ArrayList required by the
        // SimpleAdapter.  In the HashMap, there is only one key, set to "task".
        _taskList = new ArrayList<HashMap<String,UTLTaskDisplay>>();
        c.moveToFirst();
        while (!c.isAfterLast())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay td = this.cursorToUTLTaskDisplay(c);
            if (td.task.sort_order>=(Long.MAX_VALUE-6))
            {
                // This can happen after a very large amount of sorting. Fix it if possible.
                UTLAccount a = (new AccountsDbAdapter()).getAccount(td.task.account_id);
                if (a.sync_service==UTLAccount.SYNC_GOOGLE)
                {
                    Log.e(TAG,"Maximum Sort Order for Google","Encountered "+
                        "the maximum sort order value for a Google account.");
                }
                else
                {
                    if (a.sync_service==UTLAccount.SYNC_NONE)
                    {
                        Log.w(TAG, "Maximum Sort Order for Unsynced", "Encountered " +
                            "the maximum sort order value for an unsynced account. Redistributing " +
                            "values.");
                    }
                    else
                    {
                        Log.w(TAG,"Maximum Sort Order for Toodledo","Encountered "+
                            "the maximum sort order value for an unsynced account. Redistributing "+
                            "values.");
                    }
                    c.close();
                    final ProgressDialog progressDialog = ProgressDialog.show(_a,null,
                        getString(R.string.resorting_tasks));
                    final long accountID = td.task.account_id;
                    new AsyncTask<Void,Void,Void>()
                    {
                        protected Void doInBackground(Void... params)
                        {
                            Util.distributeSortOrderInAccount(accountID);
                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void x)
                        {
                            progressDialog.dismiss();
                            refreshData();
                        }
                    }.executeOnExecutor(Util.UTL_EXECUTOR);
                    return;
                }
            }
            hash.put("task", td);
            _taskList.add(hash);
            c.moveToNext();
        }
        c.close();
        
        // If necessary, reorder the task list to put subtasks below their parents.
        // This function also generates a list of parent tasks with subtasks.
        reorderTaskList();
        
        // The task list is finalized, so this is a good place to refresh the mapping of task IDs
        // to UTLTaskDisplay objects:
        refreshTaskHash();
        
        // If the task list is empty, check to see if there are any tasks at all in 
        // the database.  If not, display a different message to the user:
        if (_taskList.size()==0)
        {
        	c = Util.db().rawQuery("select count(*) from tasks", null);
        	c.moveToFirst();
        	if (c.getInt(0)==0)
        	{
        		ViewGroup vg = (ViewGroup)_rootView.findViewById(R.id.task_list_container);
        		TextView tv = (TextView)vg.getChildAt(1);
        		tv.setText(R.string.no_tasks_exist);
        	}
        	c.close();
        }

        // Show the banner ad if the number of tasks is below a threshold or if there are no
        // native ads ready to display.
        if (_pm.stat()==PurchaseManager.SHOW_ADS)
        {
            initBannerAd(_rootView);
            _bannerAd.setIsAtTop(true);
        }
        else
            hideBannerAd();

        // Create the SimpleAdapter for this ListActivity:
        adapter = new SimpleAdapter(_a,_taskList,R.layout.task_list_row,
            new String[] {"task","task","task","task","task","task","task","task","task","task"},
            new int[] {R.id.task_list_indenter,
            R.id.task_list_title,R.id.task_list_upper_right,R.id.task_list_lower_left,
            R.id.task_list_lower_right,R.id.task_list_completed_checkbox,
            R.id.task_list_main_area,R.id.task_list_top_layout,R.id.task_list_top_top_layout,
            R.id.task_list_ad_container});
        
        // Define the function that maps the data in the task to a display on-screen:
        adapter.setViewBinder(new SimpleAdapter.ViewBinder() 
        {            
            @Override
            public boolean setViewValue(View view, Object object, String unusedText)
            {
                UTLTaskDisplay td = (UTLTaskDisplay)object;
                switch (view.getId())
                {
                case R.id.task_list_top_top_layout:
                	// Store the task ID as a tag in the topmost layout:
                	view.setTag(Long.valueOf(td.task._id));
                	
                	// Update the header:
                	LinearLayout headerRow = (LinearLayout)view.findViewById(R.id.task_list_header_row);
                	if (td.showHeader && td.header.length()>0 && _displayOptions.showDividers)
                	{
                		headerRow.setVisibility(View.VISIBLE);
                		view.findViewById(R.id.task_list_header_divider).setVisibility(View.VISIBLE);
                		TextView headerText = (TextView)view.findViewById(R.id.task_list_header_text);
                		ImageView headerIcon = (ImageView)view.findViewById(R.id.task_list_header_icon);
                		headerText.setText(td.header);
                		if (td.headerIcon!=0)
                		{
                			headerIcon.setImageResource(td.headerIcon);
                			headerIcon.setVisibility(View.VISIBLE);
                		}
                		else
                			headerIcon.setVisibility(View.GONE);
                	}
                	else
                	{
                		headerRow.setVisibility(View.GONE);
                		view.findViewById(R.id.task_list_header_divider).setVisibility(View.GONE);
                	}

                	break;
                	
                case R.id.task_list_indenter:
                	if (td.level==0)
                		view.setVisibility(View.GONE);
                	else
                	{
                		ViewGroup.LayoutParams params = view.getLayoutParams();
                		params.width = Float.valueOf(TaskListFragment.SUBTASK_INDENTATION*td.level*
                			_viewScaleFactor).intValue();
                		view.setLayoutParams(params);
                		view.setVisibility(View.VISIBLE);
                	}
                	break;
                	
                  case R.id.task_list_title:
                    TextView tv = (TextView)view;
                    tv.setText(td.task.title);
                    if (_selectedTasks!=null && _selectedTasks.containsKey(td.task._id))
                    {
                    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    		R.attr.list_highlight_text_color)));
                    }
                    else
                    {
                    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    		R.attr.utl_text_color)));
                    }
                    break;
                    
                  case R.id.task_list_upper_right:
                    updateVariableView(view, td, _displayOptions.upperRightField);
                    break;

                  case R.id.task_list_lower_left:
                      updateVariableView(view, td, _displayOptions.lowerLeftField);
                      break;

                  case R.id.task_list_lower_right:
                      updateVariableView(view, td, _displayOptions.lowerRightField);
                      break;
                      
                  case R.id.task_list_completed_checkbox:
                      CheckBox cb = (CheckBox)view;
                      
                      // The style of the checkbox varies according to the user's settings:
                      if (_displayOptions.leftColorCodedField.equals("priority"))
                      {
                    	  cb.setButtonDrawable(_priorityCheckboxDrawables[td.task.priority]);
                      }
                      else if (_displayOptions.leftColorCodedField.equals("status"))
                      {
                    	  cb.setButtonDrawable(_statusCheckboxDrawables[td.task.status]);
                      }
                      else if (_displayOptions.leftColorCodedField.equals("star"))
                      {
                          if (td.task.star)
                          {
                        	  cb.setButtonDrawable(R.drawable.checkbox_cyan_selector);
                          }
                          else
                          {
                        	  cb.setButtonDrawable(R.drawable.checkbox_medium_gray_selector);
                          }
                      }
                      else
                      {
                          // The user must not be color-coding the checkbox.
                    	  cb.setButtonDrawable(R.drawable.checkbox_medium_gray_selector);
                      }
                      
                      cb.setChecked(td.task.completed);
                      
                      // Store the task ID in the checkbox.  The handler function needs
                      // access to this.
                      cb.setContentDescription(String.valueOf(td.task._id));
                      
                      if (td.task.completed)
                      {
                          // Since the task is completed, we can assume that any 
                          // repeating tasks (if applicable) have already been created.
                          repeatingTasksCreated.add(td.task._id);
                      }
                      
                      // Define the click handler function:
                      cb.setOnClickListener(new View.OnClickListener() 
                      {                       
                          @Override
                          public void onClick(View v) 
                          {
                        	  TaskListFragment.this.handleCheckBoxClick(v);                            
                          }
                      });

                      break;
                      
                  case R.id.task_list_main_area:
                      // This is the clickable or tappable area for bringing up the 
                      // task viewer.  
                      
                      // Store the task ID in a special hidden view:
                      ViewGroup vg = (ViewGroup)view;
                      TextView hiddenText = (TextView)vg.getChildAt(0);
                      hiddenText.setText(String.valueOf(td.task._id));

                      // Add the task ID as a tag to this view.
                      view.setTag((Long)(td.task._id));

                	  break;
                      
                  case R.id.task_list_top_layout:
                	  // This is the entire task layout. The background color to display
                	  // depends on whether or not the task is selected:
                      if (_selectedTasks.containsKey(td.task._id))
            		  {
            			  view.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr( 
            				  R.attr.list_highlight_bg_color)));
            		  }
            		  else
            		  {
            			  view.setBackgroundResource(Util.resourceIdFromAttr(_a,android.R.attr.
                              selectableItemBackground));
            		  }
                	  
            		  // Define a click handler for the checkbox hit area:
            		  view.findViewById(R.id.task_list_completed_checkbox_hit_area).setOnClickListener(
            			  new View.OnClickListener() 
                      {                       
                          @Override
                          public void onClick(View v) 
                          {
                        	  TaskListFragment.this.handleCheckBoxClick(v.findViewById(
                        		  R.id.task_list_completed_checkbox));                            
                          }
                      });

                      // The drag listener is used for manual sorting:
                      view.setOnDragListener((View.OnDragListener) _onDragListener);

                      // Add the task ID as a tag to this view.
                      view.setTag((Long)(td.task._id));

                      // Define single tap handler:
                      view.setOnClickListener(new View.OnClickListener()
                      {
                          @Override
                          public void onClick(View v)
                          {
                              TaskListFragment.this.handleTaskClick(v.findViewById(R.id.
                                  task_list_main_area));
                          }
                      });

                      // Define the long-press listener:
                      view.setOnLongClickListener(_longClickListener);

                      // The touch listener is used for manual sorting:
                      view.findViewById(R.id.task_list_main_area).setOnTouchListener(
                          _onTouchListener);

                      break;

                case R.id.task_list_ad_container:
                    ViewGroup adContainer = (ViewGroup) view;
                    ViewGroup nativeAdPlaceholder = (ViewGroup) adContainer.findViewById(R.id.
                        task_list_native_ad_placeholder);

                    // 9/30/23: Removing all native ad code due to lack of revenue for these ads.
                    nativeAdPlaceholder.removeAllViews();
                    adContainer.setVisibility(View.GONE);

                    break;
                }
                return true;
            }
        });
        
        // Run a sync if needed:
        Util.doMinimalSync(_a);

        this.setListAdapter(adapter);
        
        // If nothing is selected, display a message to the user in the detail pane (if we're
        // in a compatible split-screen mode).  We only due this on the first refresh since the user
        // may be doing something like adding a new task.
        if (_a.getSplitScreenOption()!=Util.SS_NONE && (_selectedTasks==null ||
        	_selectedTasks.size()==0) && _isFirstRefresh)
        {
        	_a.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
        }
        
        _isFirstRefresh = false;
    }
    
    // Refresh the _taskHash structure, using values from taskList (the ArrayList):
    private void refreshTaskHash()
    {
    	if (_taskHash!=null)
    		_taskHash.clear();
    	else
    		_taskHash = new HashMap<Long,UTLTaskDisplay>();
    	
    	Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
        	HashMap<String,UTLTaskDisplay> hash = it.next();
        	UTLTaskDisplay td = hash.get("task");
        	_taskHash.put(td.task._id, td);
        }
    }
    
    // Generate the massively complex SQL query into the database:
    private String generateSQLQuery()
    {
    	return Util.getTaskSqlQuery(viewID, viewCursor, _a);
    }
    
    // Given a cursor, get an instance of UTLTaskDisplay:
    private UTLTaskDisplay cursorToUTLTaskDisplay(Cursor c)
    {
        UTLTaskDisplay td = new UTLTaskDisplay();
        
        // The cursor fields are in the same order that TasksDbAdapter expects, so use 
        // the TasksDbAdapter function to get the UTLTask object:
        td.task = (new TasksDbAdapter()).getUTLTask(c);
        
        td.firstTagName = Util.cString(c, "tag_name");
        td.accountName = Util.cString(c, "account");
        td.folderName = Util.cString(c,"folder");
        td.contextName = Util.cString(c,"context");
        td.goalName = Util.cString(c, "goal");
        td.locationName = Util.cString(c, "location");
        td.numTags = Util.cLong(c, "num_tags");
        
        // Collaboration - owner:
        td.ownerName = Util.cString(c,"owner_name");
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        
        // Collaboration - Assignor / Added By:
        td.assignorName = Util.cString(c, "assignor_name");
        
        // Collaboration - Shared With:
        if (td.task.shared_with.length()>0)
        {
        	UTLAccount a = (new AccountsDbAdapter()).getAccount(td.task.account_id);
        	String[] collIDs = td.task.shared_with.split("\n");
        	td.sharedWith = "";
        	for (int i=0; i<collIDs.length; i++)
        	{
        		if (td.sharedWith.length()>0)
        			td.sharedWith += ", ";
        		if (collIDs[i].equals(a.td_userid))
        			td.sharedWith += Util.getString(R.string.Myself);
        		else
        		{
        			UTLCollaborator co = cdb.getCollaborator(td.task.account_id, collIDs[i]);
        			if (co!=null)
        				td.sharedWith += co.name;        				
        		}
        	}
        }
        else
        	td.sharedWith = Util.getString(R.string.None);
        
        return(td);
    }
    
    // If the task list indents subtasks, we need to reorder the list.  This will also update the
    // display of headers.
    private void reorderTaskList()
    {
        if (_firstSortField.equals("tasks.sort_order") && _displayOptions.subtaskOption.equals(
            "flattened") && !topLevel.equals(ViewNames.SUBTASK))
        {
            // The user has chosen to sort this view manually, but subtasks are displayed inline
            // with parent tasks.  This does not work.
            _displayOptions.subtaskOption = "indented";
        }

        // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
        // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
    	if (subLists==null)
    	{
    		subLists = new HashMap<Long,ArrayList<UTLTaskDisplay>>();
    	}
    	else
    	{
    		subLists.clear();
    	}
        
        // This is an ArrayList of UTLTaskDisplay objects that are NOT subtasks:
        ArrayList<UTLTaskDisplay> parentList = new ArrayList<UTLTaskDisplay>();
        
        // We also need a hash of all task IDs:
        HashSet<Long> allIDs = new HashSet<Long>();
        
        // Populate the 3 lists described above:
        Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
            UTLTaskDisplay td = it.next().get("task");
            allIDs.add(td.task._id);
            if (td.task.parent_id==0)
            {
                // Not a subtask:
                parentList.add(td);
            }
            else
            {
                if (!subLists.containsKey(td.task.parent_id))
                {
                    subLists.put(td.task.parent_id, new ArrayList<UTLTaskDisplay>());
                }
                subLists.get(td.task.parent_id).add(td);
            }
        }

        // A message will be displayed about missing child tasks if needed.
        int orphanedSubtaskWarningVisibility = View.GONE;

        if (_displayOptions.subtaskOption.equals("indented") && _settings.
        	getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	// Subtasks are indented:
            if (_firstSortField.equals("tasks.sort_order"))
            {
                // The parent option must be 1, indicating that orphaned subtasks are not
                // displayed.
                _displayOptions.parentOption=1;
            }
        	if (_displayOptions.parentOption==1)
        	{
        		// Orphaned subtasks will not be displayed.
	            // Clear out and repopulate the main list for this class:
                int prevNumTasks = _taskList.size();
	            _taskList.clear();
	            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
	            while (it2.hasNext())
	            {
	                // Add in the non-subtask:
	                UTLTaskDisplay td = it2.next();
	                td.level = 0;
	                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
	                hash.put("task", td);
	                _taskList.add(hash);
	                
	                // If this task has any children, then add them in next:
	                if (subLists.containsKey(td.task._id))
	                	addChildTasksToList(td.task._id, 0);
	            }
                if (_taskList.size()<prevNumTasks)
                    orphanedSubtaskWarningVisibility = View.VISIBLE;
        	}
        	else
        	{
        		// Orphaned subtasks will be displayed at the same level as parent
        		// tasks.
         		
        		ArrayList<HashMap<String,UTLTaskDisplay>> taskList2 = 
        			(ArrayList<HashMap<String,UTLTaskDisplay>>)_taskList.clone();
        		_taskList.clear();
        		Iterator<HashMap<String,UTLTaskDisplay>> it2 = taskList2.iterator();
        		while (it2.hasNext())
        		{
        			UTLTaskDisplay td = it2.next().get("task");
        			if (td.task.parent_id==0)
        			{
        				// It's not a subtask.  Add it into the final task list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
        					UTLTaskDisplay>();
        				td.level = 0;
    	                hash.put("task", td);
    	                _taskList.add(hash);
    	                
    	                // If this task has any children, then add them in next:
    	                if (subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        			else if (!allIDs.contains(td.task.parent_id))
        			{
        				// It's an orphaned subtask.  At it into the final list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
    						UTLTaskDisplay>();
        				td.level = 0;
        				hash.put("task", td);
        				_taskList.add(hash);
        				orphanedSubtaskIDs.add(td.task._id);
        				
        				// If this task has any children, then add them in next:
    	                if (subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        		}
        	}
        }
        else if (_displayOptions.subtaskOption.equals("separate_screen") && _settings.
        	getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	// For this option, subtasks are not displayed at all here, so 
        	// we need to repopulate the main list with only parent tasks:
        	_taskList.clear();
            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
            while (it2.hasNext())
            {
                // Add in the non-subtask:
                UTLTaskDisplay td = it2.next();
                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
                td.level = 0;
                hash.put("task", td);
                _taskList.add(hash);                
            }
        }

        // Show or hide the warning about invisible orphaned subtasks:
        TextView orphanedSubtaskWarning = (TextView)_rootView.findViewById(R.id.
            task_list_orphaned_subtask_warning);
        orphanedSubtaskWarning.setVisibility(orphanedSubtaskWarningVisibility);

        // Set header field text and set flags indicating which rows will show a header. Also,
        // store the index of each item and determine which ones will show ads.
        String lastHeader = "";
        for (int i=0; i<_taskList.size(); i++)
        {
        	UTLTaskDisplay td = _taskList.get(i).get("task");
        	td.index = i;
        	if (_pm.stat()==PurchaseManager.SHOW_ADS && ((i-FIRST_NATIVE_AD_INDEX) %
                NATIVE_AD_INTERVAL == 0))
            {
                td.hasNativeAd = true;
            }
        	if (td.level==0)
        	{
        		td.setHeader(_firstSortField, _a);
        		if (!td.header.equals(lastHeader) && td.header.length()>0)
        		{
        			td.showHeader = true;
        			lastHeader = td.header;
        		}
        	}
        }
    }
    
    // Add child tasks to the list of tasks to display:
    private void addChildTasksToList(long taskID, int parentLevel)
    {
    	ArrayList<UTLTaskDisplay> childList = subLists.get(taskID);
        Iterator<UTLTaskDisplay> it3 = childList.iterator();
        while (it3.hasNext())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay child = it3.next();
            child.level = parentLevel+1;
            hash.put("task", child);
            _taskList.add(hash);
            
            if (subLists.containsKey(child.task._id))
            	addChildTasksToList(child.task._id, parentLevel+1);
        }
    }

    // For the upper right, lower left, and lower right fields, update the display:
    void updateVariableView(View view, UTLTaskDisplay td, String displayOption)
    {
        String[] statuses = this.getResources().getStringArray(R.array.statuses);
        String[] priorities = this.getResources().getStringArray(R.array.priorities);

        // Get references to all the elements.  The view passed in is a ViewGroup.
        ViewGroup viewGroup = (ViewGroup)view;
        TextView textView = (TextView)(viewGroup.getChildAt(0));
        ImageView timerIcon = (ImageView)(viewGroup.getChildAt(1));
        ImageView reminderIcon = (ImageView)(viewGroup.getChildAt(2));
        ImageView starIcon = (ImageView)(viewGroup.getChildAt(3));
        ImageView recurringIcon = (ImageView)(viewGroup.getChildAt(4));
        ImageView parentIcon = (ImageView)(viewGroup.getChildAt(5));
        ImageView subtaskIcon = (ImageView)(viewGroup.getChildAt(6));
        ImageView noteIcon = (ImageView)(viewGroup.getChildAt(7));
        ImageView contactIcon = (ImageView)(viewGroup.getChildAt(8));
        ImageView sharingIcon = (ImageView)(viewGroup.getChildAt(9));
        ImageView timerIconInv = (ImageView)(viewGroup.getChildAt(10));
        ImageView reminderIconInv = (ImageView)(viewGroup.getChildAt(11));
        ImageView starIconInv = (ImageView)(viewGroup.getChildAt(12));
        ImageView recurringIconInv = (ImageView)(viewGroup.getChildAt(13));
        ImageView parentIconInv = (ImageView)(viewGroup.getChildAt(14));
        ImageView subtaskIconInv = (ImageView)(viewGroup.getChildAt(15));
        ImageView noteIconInv = (ImageView)(viewGroup.getChildAt(16));
        ImageView contactIconInv = (ImageView)(viewGroup.getChildAt(17));
        ImageView sharingIconInv = (ImageView)(viewGroup.getChildAt(18));
        
        // In most cases, the icons are invisible and the text view is visible:
        timerIcon.setVisibility(View.GONE);
        reminderIcon.setVisibility(View.GONE);
        starIcon.setVisibility(View.GONE);
        recurringIcon.setVisibility(View.GONE);
        parentIcon.setVisibility(View.GONE);
        subtaskIcon.setVisibility(View.GONE);
        noteIcon.setVisibility(View.GONE);
        contactIcon.setVisibility(View.GONE);
        sharingIcon.setVisibility(View.GONE);
        timerIconInv.setVisibility(View.GONE);
        reminderIconInv.setVisibility(View.GONE);
        starIconInv.setVisibility(View.GONE);
        recurringIconInv.setVisibility(View.GONE);
        parentIconInv.setVisibility(View.GONE);
        subtaskIconInv.setVisibility(View.GONE);
        noteIconInv.setVisibility(View.GONE);
        contactIconInv.setVisibility(View.GONE);
        sharingIconInv.setVisibility(View.GONE);
        textView.setVisibility(View.VISIBLE);
        
        boolean overdueFlag = false;
        
        // What we show depends on the display option:
        if (displayOption.equals("account"))
        {
            textView.setText(td.accountName);
        }
        else if (displayOption.equals("folder"))
        {
            textView.setText(td.folderName);
        }
        else if (displayOption.equals("context"))
        {
            textView.setText(td.contextName);
        }
        else if (displayOption.equals("goal"))
        {
            textView.setText(td.goalName);
        }
        else if (displayOption.equals("location"))
        {
            textView.setText(td.locationName);
        }
        else if (displayOption.equals("tags"))
        {
            if (td.numTags==0)
            {
                textView.setText("");
            }
            else if (td.numTags==1)
            {
                textView.setText(td.firstTagName);
            }
            else
            {
                // We need to query the DB for tags.
            	String[] tagList;
           		tagList = (new TagsDbAdapter()).getTagsInDbOrder(td.task._id);
                String tagText = tagList[0];
                for (int i=1; i<tagList.length; i++)
                {
                    tagText += ","+tagList[i];
                }
                textView.setText(tagText);
            }
        }
        else if (displayOption.equals("due_date"))
        {
            if (td.task.due_date>0)
            {
                textView.setText(Util.getDateString(td.task.due_date));
                if (td.task.uses_due_time && td.task.due_date<(System.currentTimeMillis()+
                	_timeZoneOffset))
                {
                    textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                    overdueFlag = true;
                }
                else if (!td.task.uses_due_time && td.task.due_date<_midnightToday)
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.utl_text_color)));
                }
            }
            else
            {
                textView.setText("");
            }
        }
        else if (displayOption.equals("due_time"))
        {
        	if (!td.task.uses_due_time)
        		textView.setText("");
        	else
        		textView.setText(Util.getTimeString(td.task.due_date));
        }
        else if (displayOption.equals("due_date_time"))
        {
        	if (td.task.due_date>0)
        	{
        		if (td.task.uses_due_time)
        			textView.setText(Util.getDateTimeString(td.task.due_date));
        		else
        			textView.setText(Util.getDateString(td.task.due_date));
        		if (td.task.uses_due_time && td.task.due_date<(System.currentTimeMillis()+
                	_timeZoneOffset))
                {
        			textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
        			overdueFlag = true;
                }
                else if (!td.task.uses_due_time && td.task.due_date<_midnightToday)
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.utl_text_color)));
                }        	
            }
        	else
        		textView.setText("");
        }
        else if (displayOption.equals("start_date"))
        {
            if (td.task.start_date>0)
            {
                textView.setText(Util.getDateString(td.task.start_date));
            }
            else
            {
                textView.setText("");
            }        
        }
        else if (displayOption.equals("start_time"))
        {
        	if (!td.task.uses_start_time)
        		textView.setText("");
        	else
        		textView.setText(Util.getTimeString(td.task.start_date));
        }
        else if (displayOption.equals("start_date_time"))
        {
            if (td.task.start_date>0)
            {
            	if (td.task.uses_start_time)
            		textView.setText(Util.getDateTimeString(td.task.start_date));
            	else
            		textView.setText(Util.getDateString(td.task.start_date));
            }
            else
            {
                textView.setText("");
            }        
        }
        else if (displayOption.equals("reminder"))
        {
        	if (td.task.reminder>0)
            {
                textView.setText(Util.getDateTimeString(td.task.reminder));
            }
            else
            {
                textView.setText("");
            }       
        }
        else if (displayOption.equals("completion_date"))
        {
            if (td.task.completion_date>0)
            {
                textView.setText(Util.getDateString(td.task.completion_date));
            }
            else
            {
                textView.setText("");
            }        
        }
        else if (displayOption.equals("mod_date"))
        {
            if (td.task.mod_date>0)
            {
                textView.setText(Util.getDateString(td.task.mod_date));
            }
            else
            {
                textView.setText("");
            }        
        }
        else if (displayOption.equals("status"))
        {
            textView.setText(statuses[td.task.status]);
        }
        else if (displayOption.equals("length"))
        {
            textView.setText(td.task.length+" "+Util.getString(R.string.minutes_abbreviation));
        }
        else if (displayOption.equals("priority"))
        {
            textView.setText(priorities[td.task.priority]);
        }
        else if (displayOption.equals("importance"))
        {
        	textView.setText(Util.getString(R.string.Importance_)+" "+td.task.importance);
        }
        else if (displayOption.equals("note"))
        {
            textView.setText(td.task.note);
        }
        else if (displayOption.equals("timer"))
        {
            // Convert to minutes:
            long minutes = Math.round(td.task.timer / 60.0);
            textView.setText(minutes + " " + Util.getString(R.string.minutes_abbreviation));
        }
        else if (displayOption.equals("icons"))
        {
            // Hide the text view, since it is not used here:
            textView.setVisibility(View.GONE);
            
            // See if the task is selected in mult-select mode:
            boolean isSelected = false;
            if (_selectedTasks!=null && _selectedTasks.containsKey(td.task._id))
            	isSelected = true;
            
            // Make any icons visible that are applicable:
            if (td.task.timer_start_time>0 && _settings.getBoolean(PrefNames.TIMER_ENABLED, 
            	true))
            {
            	if (isSelected)
            		timerIconInv.setVisibility(View.VISIBLE);
            	else
            		timerIcon.setVisibility(View.VISIBLE);
            }
            if ((td.task.reminder>0 && _settings.getBoolean(PrefNames.REMINDER_ENABLED, 
            	true)) || (td.task.location_reminder && _settings.getBoolean(
            	PrefNames.LOCATIONS_ENABLED,true)))
            {
            	if (isSelected)
            		reminderIconInv.setVisibility(View.VISIBLE);
            	else
            		reminderIcon.setVisibility(View.VISIBLE);
            }
            if (td.task.star && _settings.getBoolean(PrefNames.STAR_ENABLED,true))
            {
            	if (isSelected)
            		starIconInv.setVisibility(View.VISIBLE);
            	else
            		starIcon.setVisibility(View.VISIBLE);
            }
            if (td.task.repeat!=0 && _settings.getBoolean(PrefNames.REPEAT_ENABLED,true))
            {
            	if (isSelected)
            		recurringIconInv.setVisibility(View.VISIBLE);
            	else
            		recurringIcon.setVisibility(View.VISIBLE);
            }
            if (subLists!=null && subLists.containsKey(td.task._id) &&
            	_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true))
            {
            	// The task has children that match this view's query.
            	if (isSelected)
            		parentIconInv.setVisibility(View.VISIBLE);
            	else
            		parentIcon.setVisibility(View.VISIBLE);
            }
            if (td.task.parent_id>0 && _settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true))
            {
            	if (isSelected)
            		subtaskIconInv.setVisibility(View.VISIBLE);
            	else
            		subtaskIcon.setVisibility(View.VISIBLE);
            }
            if (td.task.note.length()>0)
            {
            	if (isSelected)
            		noteIconInv.setVisibility(View.VISIBLE);
            	else
            		noteIcon.setVisibility(View.VISIBLE);
            }   
            if (td.task.contactLookupKey!=null && td.task.contactLookupKey.length()>0 &&
            	_settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
            {
            	if (isSelected)
            		contactIconInv.setVisibility(View.VISIBLE);
            	else
            		contactIcon.setVisibility(View.VISIBLE);
            }
            if (td.task.is_joint && _settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
            {
            	if (isSelected)
            		sharingIconInv.setVisibility(View.VISIBLE);
            	else
            		sharingIcon.setVisibility(View.VISIBLE);
            }
        }
        else if (displayOption.equals("folder_context"))
        {
            if (td.task.folder_id>0 && td.task.context_id>0)
            {
                textView.setText(td.folderName+" / "+td.contextName);
            }
            else if (td.task.folder_id>0)
            {
                textView.setText(td.folderName);
            }
            else if (td.task.context_id>0)
            {
                textView.setText(td.contextName);
            }
            else
            {
                textView.setText("");
            }
        }
        else if (displayOption.equals("start_due"))
        {
            if (td.task.due_date>0 && td.task.start_date>0)
            {
                textView.setText(Util.getDateString(td.task.start_date)+" - "+
                    Util.getDateString(td.task.due_date));
                if (td.task.uses_due_time && td.task.due_date<System.currentTimeMillis())
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else if (!td.task.uses_due_time && td.task.due_date<_midnightToday)
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.utl_text_color)));
                }
            }
            else if (td.task.due_date>0)
            {
                textView.setText(Util.getDateString(td.task.due_date));
                if (td.task.uses_due_time && td.task.due_date<System.currentTimeMillis())
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else if (!td.task.uses_due_time && td.task.due_date<_midnightToday)
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.overdue_text_color)));
                	overdueFlag = true;
                }
                else
                {
                	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                    	R.attr.utl_text_color)));
                }                
            }
            else if (td.task.start_date>0)
            {
                textView.setText(Util.getDateString(td.task.start_date));
            }
            else
            {
                textView.setText("");
            }
        }
        else if (displayOption.equals("contact"))
        {
        	if (td.task.contactLookupKey!=null && td.task.contactLookupKey.length()>0)
        	{
	        	Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
	        		CONTENT_LOOKUP_URI,td.task.contactLookupKey);
	        	Cursor c = _a.managedQuery(contactUri,null,null,null,null);
	        	if (c!=null && c.moveToFirst())
	        	{
	        		textView.setText(Util.cString(c,ContactsContract.
            			Contacts.DISPLAY_NAME));
	        	}
	        	else
	        		textView.setText(R.string.Missing_Contact);
        	}
        	else
        		textView.setText("");
        }
        else if (displayOption.equals("is_joint"))
        {
        	if (td.task.is_joint)
        		textView.setText(R.string.Shared);
        	else
        		textView.setText("");
        }
        else if (displayOption.equals("owner_name"))
        {
        	textView.setText(td.ownerName);
        }
        else if (displayOption.equals("shared_with"))
        {
        	textView.setText(td.sharedWith);
        }
        else if (displayOption.equals("assignor_name"))
        {
            textView.setText(td.assignorName);
        }
        else if (displayOption.equals("sort_order"))
        {
            textView.setText(NumberFormat.getNumberInstance(Locale.US).format(td.task.sort_order));
        }
        
        if (!displayOption.equals("icons") && !overdueFlag)
        {
        	// Set the text color, depending on whether the task is currently selected:
        	if (_selectedTasks!=null && _selectedTasks.containsKey(td.task._id))
        	{
        		textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                	R.attr.list_highlight_text_color)));
        	}
        	else
            {
            	textView.setTextColor(_res.getColor(_a.resourceIdFromAttr(
                	R.attr.utl_text_color)));
            }
        }
    }
    
    public void handleTaskClick(View v)
    {
        ViewGroup vg = (ViewGroup)v;
        TextView hiddenText = (TextView)vg.getChildAt(0);
        long taskID = Long.parseLong(hiddenText.getText().toString());
                
        // Do nothing if we're in resize mode:
        if (_a.inResizeMode())
        	return;

        // Do nothing if we're in manual sort mode:
        if (_mode==MODE_MANUAL_SORT)
            return;

        // Keep track of the last task that was tapped on:
        long lastChosenTaskID = _chosenTaskID;
        _chosenTaskID = taskID;
        
        if (_mode == MODE_SELECTING_NEW_PARENT)
        {
        	// Get a reference to the selected parent:
        	TasksDbAdapter tasksDB = new TasksDbAdapter();
        	UTLTask newParent = tasksDB.getTask(taskID);
        	if (newParent==null)
        	{
        		Util.popup(_a, R.string.Item_no_longer_exists);
        		return;
        	}
        	
        	// Loop through all tasks and make sure they can be put underneath the parent:
        	Iterator<Long> it = _selectedTasks.keySet().iterator();
        	while (it.hasNext())
        	{
        		UTLTask child = tasksDB.getTask(it.next());
        		if (child==null)
        		{
        			Util.popup(_a, R.string.Item_no_longer_exists);
            		return;
        		}
        		
        		// We cannot select a new parent that is one of the highlighted tasks:
        		if (newParent._id == child._id)
        		{
        			return;
        		}
        		
        		// The 2 tasks must be in the same account:
        		if (newParent.account_id != child.account_id)
        		{
        			Util.popup(_a, R.string.New_parent_must_be_in_same_account);
        			return;
        		}
        		
        		// The Task cannot be subtask of the current task:
        		UTLTask t = newParent;
        		while (t.parent_id>0)
        		{
        			if (t.parent_id==child._id)
        			{
        				Util.popup(_a,R.string.Cannot_select_subtask);
        				return;
        			}
        			t = tasksDB.getTask(t.parent_id);
        			if (t==null)
        				break;
        		}
        		
        		// For a Google account, the 2 tasks must be in the same folder:
        		UTLAccount a = (new AccountsDbAdapter()).getAccount(newParent.account_id);
        		if (a==null)
        		{
        			Util.popup(_a,R.string.Item_no_longer_exists);
        			return;
        		}
        		else
        		{
        			if (a.sync_service==UTLAccount.SYNC_GOOGLE && newParent.folder_id !=
        				child.folder_id)
        			{
        				Util.popup(_a, R.string.Must_be_in_same_folder);
        				return;
        			}
        		}
        		
        		// For a Toodledo account, we can't have a subtask within a subtask:
        		if (a.sync_service==UTLAccount.SYNC_TOODLEDO && newParent.parent_id>0)
        		{
        			Util.popup(_a, R.string.No_subtasks_in_subtasks);
    				return;
        		}

        		// For a Google account, we can't have a subtask within a subtask after they
                // remove the feature.
                if (a.sync_service==UTLAccount.SYNC_GOOGLE && newParent.parent_id>0 &&
                    System.currentTimeMillis()>Util.GOOGLE_SUB_SUB_TASKS_EXPIRY)
                {
                    Util.popup(_a, R.string.no_subtasks_in_subtasks2);
                    return;
                }
        	}
        	
        	// All tests have passed, so we can go ahead and move the tasks:
        	_chosenMultiSelectParent = newParent._id;
        	handleMultiSelectCommand(CHANGE_PARENT_ID);
        	return;
        }
        else if (_mode==MODE_MULTI_SELECT)
        {
        	ViewGroup topLayout = (ViewGroup)vg.getParent();
        	TasksDbAdapter tasksDB = new TasksDbAdapter();
   
        	if (_selectedTasks==null || !_selectedTasks.containsKey(taskID))
        	{
	        	UTLTask t = tasksDB.getTask(taskID);
	        	if (t==null)
	        	{
	        		Util.popup(_a, R.string.Item_no_longer_exists);
	        		return;
	        	}
	        	_selectedTasks.put(taskID, t);

	        	// Highlight the task's row:
	        	highlightRow(topLayout,taskID);
        	}
        	else
        	{
        		// Unselect the task and unhighlight its row:
        		_selectedTasks.remove(taskID);
        		unhighlightRow(topLayout,taskID);
        	}
        	       	
        	// With every task selection or unselection, the action buttons and menus may change.
        	_actionMode.invalidate();
        	
        	return;
        }
        
        // At this point, we are opening the viewer or editor.
        if (_settings.getBoolean(PrefNames.OPEN_EDITOR_ON_TAP, false))
        {
        	if (_a.useNewActivityForDetails())
        	{
        		// No split-screen in use.  Just start a new activity:
        		Intent i = new Intent(_a,EditTask.class);
        		i.putExtra("action", EditTaskFragment.EDIT);
        		i.putExtra("id", taskID);
        		startActivityForResult(i, ACTIVITY_EDIT_TASK);
        	}
        	else
        	{
        		// Highlight the task that was just selected.
        		ViewGroup topLayout = (ViewGroup)vg.getParent();
        		topLayout.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(
	        		R.attr.list_highlight_bg_color)));
        		if (_taskHash.containsKey(taskID))
            	{
            		_selectedTasks.clear();
            		_selectedTasks.put(taskID, _taskHash.get(taskID).task);
            		highlightRow(topLayout,taskID);
            	}
        		
        		// If another task was previously selected, then unhighlight it:
        		if (lastChosenTaskID!=_chosenTaskID)
        			unselectLastSelected(lastChosenTaskID);
        		
        		// In split-screen mode, display the task editor in a separate fragment:
        		EditTaskFragment frag = new EditTaskFragment();
        		Bundle args = new Bundle();
        		args.putInt("action", EditTaskFragment.EDIT);
        		args.putLong("id", taskID);
        		frag.setArguments(args);
        		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG + 
        			"/" + taskID);
        	}
        }
        else
        {
        	if (_a.useNewActivityForDetails())
        	{
        		// No split-screen in use.  Just start a new activity:
        		Intent i = new Intent(_a,ViewTask.class);
        		i.putExtra("_id", taskID);
        		this.startActivity(i);
        	}
        	else
        	{
        		// Highlight the task that was just selected.
        		ViewGroup topLayout = (ViewGroup)vg.getParent();
        		topLayout.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(
	        		R.attr.list_highlight_bg_color)));
        		if (_taskHash.containsKey(taskID))
            	{
            		_selectedTasks.clear();
            		_selectedTasks.put(taskID, _taskHash.get(taskID).task);
            		highlightRow(topLayout,taskID);
            	}
        		
        		// If another task was previously selected, then unhighlight it:
        		if (lastChosenTaskID!=_chosenTaskID)
        			unselectLastSelected(lastChosenTaskID);
        		
        		// In split-screen mode, display the task details in a separate fragment:
        		ViewTaskFragment frag = new ViewTaskFragment();
        		Bundle args = new Bundle();
        		args.putLong("_id", taskID);
        		frag.setArguments(args);
        		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewTaskFragment.FRAG_TAG + 
        			"/" + taskID);
        	}
        }
    }
    
    // This function asks the user for confirmation before marking a parent task,
    // and all subtasks, as completed.  It relies on the variable tempView being set.
    public void authorizeParentCompletion()
    {
    	// Retrieve the task from the database:
    	long taskID = Long.parseLong(tempView.getContentDescription().toString());
        TasksDbAdapter db = new TasksDbAdapter();
        UTLTask t = db.getTask(taskID);
        if (t==null)
        {
            // This could happen if the task was deleted from Toodledo.
            Util.popup(_a, R.string.Task_no_longer_exists);
            return;
        }
        
    	// Button handlers for the dialog:
    	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
    		OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				switch (which)
                {
                case DialogInterface.BUTTON_POSITIVE:
                	// Yes tapped:
                	authorizedParentCompletion = true;
                	handleCheckBoxClick(tempView);
                	break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // No clicked:
                	authorizedParentCompletion = false;
                	CheckBox cb = (CheckBox)tempView;
                    break;
                }
			}
		};
		
		// Set options and display the dialog:
		AlertDialog.Builder builder = new AlertDialog.Builder(_a);
		builder.setMessage(R.string.Parent_completion_confirmation);
		builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.setTitle(t.title);
        builder.show();
    }
    
    public void handleCheckBoxClick(View v)
    {
    	if (_mode!=MODE_NORMAL)
    		return;

        // Do nothing if we're in resize mode:
        if (_a.inResizeMode())
        	return;
        
        CheckBox cb = (CheckBox)v;
        long taskID = Long.parseLong(cb.getContentDescription().toString());
        
        // Retrieve the task from the database:
        TasksDbAdapter db = new TasksDbAdapter();
        UTLTask t = db.getTask(taskID);
        if (t==null)
        {
            // This could happen if the task was deleted from Toodledo.
            Util.popup(_a, R.string.Task_no_longer_exists);
            return;
        }
        
        // If this is a parent task with children, then ask for confirmation:
        if (!t.completed && !authorizedParentCompletion && subLists.containsKey(taskID) &&
        	subLists.get(taskID).size()>0)
        {
        	tempView = v;
        	authorizeParentCompletion();
        	cb.setChecked(false);
        	return;
        }
        authorizedParentCompletion = false;  // For next time
        
        if (!t.completed && !repeatingTasksCreated.contains(taskID))
        {
            // The task is being marked as completed and repeating tasks (if any) have
            // not yet been created.  The function below will do this:
            Util.markTaskComplete(taskID);
            cb.setChecked(true);
            repeatingTasksCreated.add(taskID);
            t.completed = true;
            t.mod_date = System.currentTimeMillis();
            t.completion_date = System.currentTimeMillis();
        }
        else
        {
	        // Toggle the completed state of the task:
	        t.completed = t.completed ? false : true;
	        t.mod_date = System.currentTimeMillis();
	        if (t.completed)
	        {
	            t.completion_date = System.currentTimeMillis();
	        }
	        else
	        {
	            t.completion_date = 0;  // In case task went from completed to not complete.
	        }
	        if (!db.modifyTask(t))
	        {
	            Util.popup(_a, R.string.DbModifyFailed);
	            Util.log("Database modification failed when trying to toggle completed state "+
	                "for task ID "+t._id);
	        }
	        else
	        {
	            cb.setChecked(t.completed);
	        }        
        }
        
        // Upload the change (if enabled):
        Util.instantTaskUpload(_a, t);
    	
        if (t.completed && subLists.containsKey(taskID) && subLists.get(taskID).size()>0)
        {
        	// We just marked a parent with subtasks as completed.  In this case, 
        	// need to refresh the display because we don't know how to put a checkmark 
        	// by all of the subtasks.
        	refreshData();
        	return;
        }
        
        // We also need to update the ArrayList of HashMap objects that governs the 
        // display.
        Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
        	HashMap<String,UTLTaskDisplay> hash = it.next();
        	UTLTaskDisplay taskDisplay = hash.get("task");
        	if (taskDisplay.task._id==t._id)
        	{
        		taskDisplay.task.completed = t.completed;
        		taskDisplay.task.mod_date = t.mod_date;
        		taskDisplay.task.completion_date = t.completion_date;
        		break;
        	}
        }
    }
    
    // Populate the action bar buttons and menu for a single task selected via long-press:
    public void populateActionMenuForSingleTask(Menu bottomMenu, Menu topMenu)
    {
        // Get the task data that is selected:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        UTLTask t = tasksDB.getTask(_chosenTaskID);
        if (t==null)
        {
        	// This could happen if the task was deleted from toodledo.
        	Util.popup(_a, R.string.Task_no_longer_exists);
        	return;
        }

        if (bottomMenu!=null)
            bottomMenu.clear();
        topMenu.clear();
        showActionModeDoneButton(true);

        // In portrait mode, overflow items are at the top. In landscape mode, all items are
        // at the top.
        Menu mainMenu;
        Menu overflowMenu;
        if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
        {
            mainMenu = bottomMenu;
            overflowMenu = topMenu;
        }
        else
        {
            mainMenu = topMenu;
            overflowMenu = topMenu;
        }

        // Some items will not be shown in the contextual action bar because they will already be 
        // visible in the right or bottom pane.
        boolean viewerShowing = false;
        if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
        {
       		viewerShowing = true;
        }
        
    	UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);

        // Add items to the menu. There's a limit to the number of items that can show on the
        // bottom:
        Menu conditionalMenu = mainMenu;

        // Select All:
    	MenuItemCompat.setShowAsAction(mainMenu.add(0,SELECT_ALL_ID,0,R.string.Select_All).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_select_all))
    		,MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        int buttonCount = 1;

    	// View or Edit:
    	if (!viewerShowing)
    	{
	        if (_settings.getBoolean(PrefNames.OPEN_EDITOR_ON_TAP, false))
	        {
	        	MenuItemCompat.setShowAsAction(mainMenu.add(0,VIEW_TASK_ID,0,R.string.View_Details).
					setIcon(_a.resourceIdFromAttr(R.attr.ab_view)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	        }
	        else
	        {
	        	MenuItemCompat.setShowAsAction(mainMenu.add(0,EDIT_TASK_ID,0,R.string.Edit_Task).
					setIcon(_a.resourceIdFromAttr(R.attr.ab_edit)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
	        }
	        buttonCount++;
    	}

    	// Clone:
    	if (!viewerShowing)
    	{
    		MenuItemCompat.setShowAsAction(mainMenu.add(0,CLONE_ID,0,R.string.Clone_Task).
    			setIcon(_a.resourceIdFromAttr(R.attr.ab_clone)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
    	    buttonCount++;
    	}

    	// Star on/off:
        boolean isPortrait = _a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT;
        int showOption;
    	int showOptionPositive = MenuItemCompat.SHOW_AS_ACTION_ALWAYS;
    	int showOptionNegative = isPortrait ? MenuItemCompat.SHOW_AS_ACTION_NEVER :
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM;
        if (_settings.getBoolean(PrefNames.STAR_ENABLED, true) && !viewerShowing)
        {
        	buttonCount++;
            conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
            showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                showOptionNegative;
        	if (t.star)
        	{
        		MenuItemCompat.setShowAsAction(mainMenu.add(0,TOGGLE_STAR_ID,0,R.string.Remove_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_off)),showOption);
        	}
        	else
        	{
        		MenuItemCompat.setShowAsAction(mainMenu.add(0,TOGGLE_STAR_ID,0,R.string.Add_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_on)),showOption);
        	}
        }

        // Timer on/off:
        if (_settings.getBoolean(PrefNames.TIMER_ENABLED, true) && !viewerShowing)
        {
        	buttonCount++;
            conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
            showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                showOptionNegative;
        	if (t.timer_start_time>0)
        	{            
        		MenuItemCompat.setShowAsAction(conditionalMenu.add(0,STOP_TIMER_ID,0,R.string.Stop_Timer).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_timer_off)),showOption);
        	}
        	else
        	{
        		MenuItemCompat.setShowAsAction(conditionalMenu.add(0,START_TIMER_ID,0,R.string.Start_Timer).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_timer_running)),showOption);
        	}
        }

        // Open Contact:
        if (t.contactLookupKey!=null && t.contactLookupKey.length()>0 && _settings.
        	getBoolean(PrefNames.CONTACTS_ENABLED, true) && !viewerShowing)
        {
        	buttonCount++;
            conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
            showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                showOptionNegative;
        	MenuItemCompat.setShowAsAction(conditionalMenu.add(0,OPEN_CONTACT_ID,0,R.string.Open_Contact).
    			setIcon(_a.resourceIdFromAttr(R.attr.open_contact)),showOption);
        }

        // Subtask options:
        if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true))
        {
        	// The subtask options available depend on the type of account we have:
        	if (a==null)
        	{
        		Util.popup(_a, R.string.Task_no_longer_exists);
            	return;
        	}
        	
        	if (a.sync_service==UTLAccount.SYNC_NONE ||
                (a.sync_service==UTLAccount.SYNC_TOODLEDO && t.parent_id==0) ||
                (a.sync_service==UTLAccount.SYNC_GOOGLE && t.parent_id==0) ||
                (a.sync_service==UTLAccount.SYNC_GOOGLE && t.parent_id>0 &&
                    System.currentTimeMillis()<Util.GOOGLE_SUB_SUB_TASKS_EXPIRY))
        	{
    			MenuItemCompat.setShowAsAction(overflowMenu.add(0,NEW_SUBTASK_ID,0,R.string.Add_Subtask)
    				,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        	}
        	boolean hasSubtasks = false;
            if (subLists!=null && subLists.containsKey(_chosenTaskID))
            {
            	// We know this has subtasks, so display an option to view them:
            	MenuItemCompat.setShowAsAction(overflowMenu.add(0,VIEW_SUBTASKS_ID,0,R.string.View_Subtasks)
            		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
            	hasSubtasks = true;
            }
            else
            {
            	// We need to query the database to see if this task has subtasks:
            	Cursor c = tasksDB.queryTasks("parent_id="+_chosenTaskID, "_id");
            	if (c.getCount()>0)
            	{
            		MenuItemCompat.setShowAsAction(overflowMenu.add(0,VIEW_SUBTASKS_ID,0,R.string.View_Subtasks)
                		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
            		hasSubtasks = true;
            	}
            	c.close();
            }
            
            // An option to change the parent (text depends on whether the task is at top
            // level):
            if (t.parent_id==0)
            {
            	// For a Toodledo task, we can only offer the option to make this a subtask if it
            	// does not currently have subtasks.
            	if (a.sync_service!=UTLAccount.SYNC_TOODLEDO || !hasSubtasks)
            	{
            		MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_PARENT_ID,0,R.string.Make_Subtask)
            			,MenuItemCompat.SHOW_AS_ACTION_NEVER);
            	}
            }
            else
            {
            	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_PARENT_ID,0,R.string.Change_Parent)
            		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
            }
            
            // An option to unlink from the parent:
            if (t.parent_id>0)
            {
            	MenuItemCompat.setShowAsAction(overflowMenu.add(0,UNLINK_FROM_PARENT_ID,0,R.string.Unlink_From_Parent_Task)
            		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
            }
        }
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_DUE_DATE_ID,0,R.string.Change_Due_Date)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_START_DATE_ID,0,R.string.Change_Start_Date)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_PRIORITY_ID,0,R.string.Change_Priority)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_FOLDER_ID,0,R.string.Change_Folder)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_CONTEXT_ID,0,R.string.Change_Context)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_STATUS_ID,0,R.string.Change_Status)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true) &&
        	a.sync_service==UTLAccount.SYNC_TOODLEDO)
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,REASSIGN_ID,0,R.string.Reassign)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }
        if (!viewerShowing)
        {
	        buttonCount++;
            conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
            showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                showOptionNegative;
	        MenuItemCompat.setShowAsAction(conditionalMenu.add(0,DELETE_TASK_ID,0,R.string.Delete_Task).
				setIcon(_a.resourceIdFromAttr(R.attr.ab_delete)),showOption);
        }
        if (!viewerShowing && t.location_id!=0)
        {
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,SHOW_MAP_ID,0,R.string.Open_Location_in_Maps)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        	MenuItemCompat.setShowAsAction(overflowMenu.add(0,NAVIGATE_ID,0,R.string.Navigate_to_Location)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
        }

        if (isPortrait)
        {
            // In portrait mode, the items are on the bottom toolbar. The toolbar's click
            // handler needs adjusted.
            _bottomToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
            {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem)
                {
                    return handleSingleTaskAction(menuItem);
                }
            });
        }
    }
    
    // Handle an action taken on a single task:
    public boolean handleSingleTaskAction(MenuItem item)
    {
        UTLTask t;
        TasksDbAdapter db;
        Intent i;
        FeatureUsage featureUsage = new FeatureUsage(_a);
        
        if (item.getItemId()==SELECT_ALL_ID)
        {
        	// The select all command can be handle by the multi-select code:
        	handleMultiSelectCommand(SELECT_ALL_ID);
        	return(true);
        }
        
        // Get the task object we're looking at:
	  	db = new TasksDbAdapter();
		t = db.getTask(_chosenTaskID);
	    if (t==null)
	    {
	        // This could happen if the task was deleted from Toodledo or Google.
	        Util.popup(_a, R.string.Task_no_longer_exists);
	        return(true);
	    }

        switch(item.getItemId())
        {
        case EDIT_TASK_ID:
          	if (_a.useNewActivityForDetails())
          	{
          		// No split-screen in use.  Just start a new activity:
          		i = new Intent(_a,EditTask.class);
          		i.putExtra("action", EditTaskFragment.EDIT);
          		i.putExtra("id", _chosenTaskID);
          		startActivityForResult(i, ACTIVITY_EDIT_TASK);
          	}
          	else
          	{
          		// In split-screen mode, display the task editor in a separate fragment:
          		EditTaskFragment frag = new EditTaskFragment();
          		Bundle args = new Bundle();
          		args.putInt("action", EditTaskFragment.EDIT);
          		args.putLong("id", _chosenTaskID);
          		frag.setArguments(args);
          		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG + 
          			"/" + _chosenTaskID);
          	}
            return(true);
              
        case VIEW_TASK_ID:
        	  	handleModeChange(MODE_NORMAL);
        	  	if (_a.useNewActivityForDetails())
	          	{
	          		// No split-screen in use.  Just start a new activity:
	          		i = new Intent(_a,ViewTask.class);
	          		i.putExtra("_id", _chosenTaskID);
	          		this.startActivity(i);
	          	}
        	  	else
        	  	{
        	  		// In split-screen mode, display the task details in a separate fragment:
            		ViewTaskFragment frag = new ViewTaskFragment();
            		Bundle args = new Bundle();
            		args.putLong("_id", _chosenTaskID);
            		frag.setArguments(args);
            		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewTaskFragment.FRAG_TAG + 
            			"/" + _chosenTaskID);
        	  	}
        	  	return(true);
              
        case START_TIMER_ID:
              if (t.timer_start_time>0)
              {
                  // Timer is already running:
                  Util.popup(_a, Util.getString(R.string.Timer_already_running));
              }
              else
              {
                  t.timer_start_time = System.currentTimeMillis();
                  Util.popup(_a,R.string.Timer_is_Running);
                  saveUpdateAndRefresh(t,db);
              }
              if (!t.completed) featureUsage.record(FeatureUsage.TIMER);
              return(true);
              
        case STOP_TIMER_ID:
              if (t.timer_start_time==0)
              {
                  Util.popup(_a, Util.getString(R.string.Timer_was_not_running));
              }
              else
              {
                  long elapsedTimeMillis = System.currentTimeMillis() - 
                      t.timer_start_time;
                  if (elapsedTimeMillis<0) elapsedTimeMillis=0;
                  t.timer_start_time = 0;
                  t.timer = t.timer + (elapsedTimeMillis/1000);
                  Util.popup(_a, R.string.Timer_stopped);
                  saveUpdateAndRefresh(t,db);
              }
              return(true);
              
        case NEW_SUBTASK_ID:
        	if (_a.useNewActivityForDetails())
			{
				// No split-screen in use.  Just start a new activity:
				i = new Intent(_a,EditTask.class);
				i.putExtra("action", EditTaskFragment.ADD);
				i.putExtra("parent_id", _chosenTaskID);
				startActivityForResult(i, ACTIVITY_ADD_SUBTASK);
			}
			else
			{
				// In split-screen mode, display the task editor in a separate fragment:
				EditTaskFragment frag = new EditTaskFragment();
				Bundle args = new Bundle();
				args.putInt("action", EditTaskFragment.ADD);
				args.putLong("parent_id", _chosenTaskID);
				frag.setArguments(args);
				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG + 
					"/add_sub/"+_chosenTaskID);
			}
            return(true);
              
        case DELETE_TASK_ID:
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
						  Util.deleteTask(_chosenTaskID);
						  handleModeChange(MODE_NORMAL);
						  
						  // Refresh other panes if necessary:
					      refreshOtherPanes();
	                  }					
				  }
			  };
              
              // Display the confirmation dialog:
			  AlertDialog.Builder builder2 = new AlertDialog.Builder(_a);
			  builder2.setMessage(R.string.Task_Delete_Confirmation);
			  builder2.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
	          builder2.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
              builder2.setTitle(t.title);
              builder2.show();
              return(true);
              
          case VIEW_SUBTASKS_ID:
        	  // Create a temporary view for the subtasks:
        	  ViewsDbAdapter viewsDB = new ViewsDbAdapter();
        	  String viewName = Long.valueOf(System.currentTimeMillis()).toString();
        	  long tempViewID = viewsDB.addView(ViewNames.SUBTASK, viewName, 
        		  Util.cString(viewCursor, "sort_string"), new DisplayOptions(
        		  _displayOptions.leftColorCodedField, _displayOptions.lowerLeftField,
        		  _displayOptions.lowerRightField, _displayOptions.upperRightField,
        		  "flattened",_displayOptions.showDividers));
        	  if (tempViewID==-1)
        	  {
        		  Util.popup(_a, R.string.DbInsertFailed);
        		  return(true);
        	  }
        	  
        	  // Add a rule to the view:
        	  int intTaskID = Integer.parseInt(Long.valueOf(_chosenTaskID).toString());
        	  MultChoiceViewRule rule = new MultChoiceViewRule("parent_id",new int[] {
        		  intTaskID });
        	  long rowID = (new ViewRulesDbAdapter()).addRule(tempViewID, 2, rule, 0, false);
        	  if (rowID==-1)
        	  {
        		  Util.popup(_a, R.string.DbInsertFailed);
        		  return(true);
        	  }
        	  if (_settings.getBoolean(PrefNames.HIDE_COMPLETED_SUBTASKS, false))
        	  {
        		  // We're hiding completed subtasks. This requires another rule.
        		  BooleanViewRule br = new BooleanViewRule("completed",false);
        		  rowID = (new ViewRulesDbAdapter()).addRule(tempViewID, 0, br, 1, false);
        		  if (rowID==-1)
            	  {
            		  Util.popup(_a, R.string.DbInsertFailed);
            		  return(true);
            	  }
        	  }
        	  
        	  // Launch a new TaskList fragment that shows the subtasks:
        	  t = (new TasksDbAdapter()).getTask(_chosenTaskID);
        	  if (t==null)
        	  {
        		  Util.popup(_a, R.string.Task_no_longer_exists);
        		  return(true);
        	  }
        	  handleModeChange(MODE_NORMAL);
        	  Bundle b = new Bundle();
        	  b.putString("top_level", ViewNames.SUBTASK);
        	  b.putString("view_name", viewName);
        	  b.putString("title",_a.getString(R.string.Subtasks_of_Task)+" \""+t.title+"\"");
        	  TaskListFragment frag = new TaskListFragment();
        	  frag.setArguments(b);
        	  _a.changeTaskList(frag, ViewNames.SUBTASK, viewName);
        	  return(true);
        	  
          case CHANGE_PARENT_ID:
        	  handleModeChange(MODE_SELECTING_NEW_PARENT);
        	  return(true);
        	  
          case UNLINK_FROM_PARENT_ID:
              t.parent_id = 0;
              t.setSortOrderToBottom(); // Task moves to bottom of list, if using manual sort.
              t.is_moved = true;
              t.prev_task_id = -1;
              saveUpdateAndRefresh(t,db);
        	  return(true);
        	  
        case CLONE_ID:
        	handleModeChange(MODE_NORMAL);
        	if (_a.useNewActivityForDetails())
  			{
  				// No split-screen in use.  Just start a new activity:
  				i = new Intent(_a,EditTask.class);
  				i.putExtra("action", EditTaskFragment.ADD);
  				i.putExtra("clone_id", _chosenTaskID);
  				this.startActivity(i);
  			}
  			else
  			{
  				// In split-screen mode, display the task editor in a separate fragment:
  				EditTaskFragment editFrag = new EditTaskFragment();
  				Bundle args = new Bundle();
  				args.putInt("action", EditTaskFragment.ADD);
  				args.putLong("clone_id", _chosenTaskID);
  				editFrag.setArguments(args);
  				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, editFrag, EditTaskFragment.FRAG_TAG + 
  					"/clone/"+_chosenTaskID);
  			}
        	return(true);
			  
          case CHANGE_START_DATE_ID:
        	  i = new Intent(_a, DateChooser.class);
        	  if (t.start_date>0)
        		  i.putExtra("default_date",t.start_date);
        	  i.putExtra("prompt",Util.getString(R.string.Select_a_Start_Date));
        	  startActivityForResult(i,ACTIVITY_GET_START_DATE);
        	  return(true);
        	  
          case CHANGE_DUE_DATE_ID:
        	  i = new Intent(_a, DateChooser.class);
        	  if (t.due_date>0)
        		  i.putExtra("default_date",t.due_date);
        	  i.putExtra("prompt",Util.getString(R.string.Select_a_Due_Date));
        	  startActivityForResult(i,ACTIVITY_GET_DUE_DATE);
        	  return(true);
        	  
          case TOGGLE_STAR_ID:
        	  if (t.star)
        	  {
        		  Util.popup(_a, R.string.star_removed);
        		  t.star = false;
        	  }
        	  else
        	  {
        		  Util.popup(_a, R.string.star_added);
        		  t.star = true;
                  if (!t.completed) featureUsage.record(FeatureUsage.STAR);
        	  }
              saveUpdateAndRefresh(t,db);
        	  return(true);
        	  
          case CHANGE_FOLDER_ID:
        	  // Create a list of folders to choose from.  Include the "no folder" option
        	  // if this is not a google account:
        	  ArrayList<String> nameList = new ArrayList<String>();
        	  _idList = new ArrayList<Long>();
        	  int checkedIndex = 0;
        	  UTLAccount acct = (new AccountsDbAdapter()).getAccount(t.account_id);
        	  if (acct!=null && (acct.sync_service==UTLAccount.SYNC_NONE ||
        		  acct.sync_service==UTLAccount.SYNC_TOODLEDO))
        	  {
        		  nameList.add(Util.getString(R.string.None));
        		  _idList.add(0L);
        	  }
        	  Cursor c = (new FoldersDbAdapter()).queryFolders("account_id="+t.account_id+
        		  " and archived=0", FoldersDbAdapter.bestSortOrder(t.account_id));
        	  c.moveToPosition(-1);
        	  while (c.moveToNext())
        	  {
        		  nameList.add(Util.cString(c, "title"));
        		  _idList.add(Util.cLong(c, "_id"));
        		  if (Util.cLong(c, "_id")==t.folder_id)
        		  {
        			  checkedIndex = _idList.size()-1;
        		  }
        	  }
        	  c.close();

        	  // Create a dialog with these choices:
              AlertDialog.Builder builder = new AlertDialog.Builder(_a);
        	  String[] nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.
        		  size());
        	  builder.setSingleChoiceItems(nameArray, checkedIndex, new DialogInterface.
        		  OnClickListener()
        	  {				
        		  @Override
        		  public void onClick(DialogInterface dialog, int which)
        		  {
        			  // Dismiss the dialog:
        			  dialog.dismiss();

        			  // Get the new folder ID, and the task we're modifying:
        			  long newFolderID = _idList.get(which);
        			  TasksDbAdapter tasksDB = new TasksDbAdapter();
        			  UTLTask task = tasksDB.getTask(_chosenTaskID);
        			  if (task==null)
        			  {
        				  Util.popup(_a, R.string.Item_no_longer_exists);
        				  return;
        			  }

        			  // If the task is linked to a Google account and the folder was
        			  // changed, we also need to change the folders of any subtasks:
        			  UTLAccount a = (new AccountsDbAdapter()).getAccount(task.account_id);
        			  if (a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE &&
        				  task.folder_id != newFolderID)
        			  {
        				  task.folder_id = newFolderID;
                          task.parent_id = 0; // Task has no parent in new folder.
                          task.setSortOrderToBottom();  // Task is at bottom of list in folder.
        				  task.mod_date = System.currentTimeMillis();
        				  if (!tasksDB.modifyTask(task))
        			      {
        			          Util.log("DB modification failed for task ID "+task._id+
        			              " when changing the folder.");
        			          Util.popup(_a, R.string.DbModifyFailed);
        			      }
        			      else
        			      {
        			    	  // Change the folders:
        			    	  changeSubtaskFolders(task);
        			    	  
        			    	  // Start a full sync to upload all of the changes:
        			    	  Intent i = new Intent(_a, Synchronizer.class);
        		              i.putExtra("command", "full_sync");
        		              i.putExtra("is_scheduled", true);
        		              Synchronizer.enqueueWork(_a,i);
        		              
        		              // Refresh the display and change the mode back.
        		              handleModeChange(MODE_NORMAL);
        		              
        		              // Refresh other panes if necessary:
        		              refreshOtherPanes();

                              if (!task.completed) (new FeatureUsage(_a)).record(FeatureUsage.FOLDERS_FOR_TASKS);
        			      }
        			  }
        			  else
        			  {
            			  // Update the folder in the database:
            			  task.folder_id = newFolderID;            			  
        				  saveUpdateAndRefresh(task,tasksDB);
        			  }
        		  }
        	  });
        	  builder.setTitle(Util.getString(R.string.New_Folder_for)+" \""+t.title+"\"");
        	  builder.show();
        	  return (true);
        	  
          case CHANGE_CONTEXT_ID:
        	  // Create a list of contexts to choose from.
        	  nameList = new ArrayList<String>();
        	  _idList = new ArrayList<Long>();
        	  checkedIndex = 0;
        	  acct = (new AccountsDbAdapter()).getAccount(t.account_id);
    		  nameList.add(Util.getString(R.string.None));
    		  _idList.add(0L);
        	  c = (new ContextsDbAdapter()).queryContexts("account_id="+t.account_id, "title");
        	  c.moveToPosition(-1);
        	  while (c.moveToNext())
        	  {
        		  nameList.add(Util.cString(c, "title"));
        		  _idList.add(Util.cLong(c, "_id"));
        		  if (Util.cLong(c, "_id")==t.context_id)
        		  {
        			  checkedIndex = _idList.size()-1;
        		  }
        	  }
        	  c.close();

        	  // Create a dialog with these choices:
        	  builder = new AlertDialog.Builder(_a);
        	  nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.
        		  size());
        	  builder.setSingleChoiceItems(nameArray, checkedIndex, new DialogInterface.
        		  OnClickListener()
        	  {				
        		  @Override
        		  public void onClick(DialogInterface dialog, int which)
        		  {
        			  // Dismiss the dialog:
        			  dialog.dismiss();

        			  // Get the new folder ID, and the task we're modifying:
        			  long newContextID = _idList.get(which);
        			  TasksDbAdapter tasksDB = new TasksDbAdapter();
        			  UTLTask task = tasksDB.getTask(_chosenTaskID);
        			  if (task==null)
        			  {
        				  Util.popup(_a, R.string.Item_no_longer_exists);
        				  return;
        			  }
        			  
        			  // Update the context in the database:
        			  task.context_id = newContextID;            			  
    				  saveUpdateAndRefresh(task,tasksDB);

                      if (!task.completed) (new FeatureUsage(_a)).record(FeatureUsage.CONTEXTS);
        		  }
        	  });
        	  builder.setTitle(Util.getString(R.string.New_Context_for)+" \""+t.title+"\"");
        	  builder.show();
        	  return (true);

          case CHANGE_STATUS_ID:
        	  // Create a dialog with status options:
        	  builder = new AlertDialog.Builder(_a);
        	  builder.setSingleChoiceItems(this.getResources().getStringArray(R.array.statuses), 
        		  t.status, new DialogInterface.OnClickListener()
        	  {				
        		  @Override
        		  public void onClick(DialogInterface dialog, int which)
        		  {
        			  // Dismiss the dialog:
        			  dialog.dismiss();

        			  // Get the task we're modifying:
        			  TasksDbAdapter tasksDB = new TasksDbAdapter();
        			  UTLTask task = tasksDB.getTask(_chosenTaskID);
        			  if (task==null)
        			  {
        				  Util.popup(_a, R.string.Item_no_longer_exists);
        				  return;
        			  }

        			  // Update the task in the database:
        			  task.status = which;
        			  saveUpdateAndRefresh(task,tasksDB);

                      if (!task.completed) (new FeatureUsage(_a)).record(FeatureUsage.STATUS);
        		  }
        	  });
        	  builder.setTitle(Util.getString(R.string.New_Status_for)+" \""+t.title+"\"");
        	  builder.show();
        	  return (true);
        	  
          case CHANGE_PRIORITY_ID:
        	  // Create a dialog with priority options:
        	  builder = new AlertDialog.Builder(_a);
        	  builder.setSingleChoiceItems(this.getResources().getStringArray(R.array.priorities), 
        		  t.priority, new DialogInterface.OnClickListener()
        	  {				
        		  @Override
        		  public void onClick(DialogInterface dialog, int which)
        		  {
        			  // Dismiss the dialog:
        			  dialog.dismiss();

        			  // Get the task we're modifying:
        			  TasksDbAdapter tasksDB = new TasksDbAdapter();
        			  UTLTask task = tasksDB.getTask(_chosenTaskID);
        			  if (task==null)
        			  {
        				  Util.popup(_a, R.string.Item_no_longer_exists);
        				  return;
        			  }

        			  if (which==0)
        			  {
        				  // This is the "none" priority.  This is not allowed for Toodledo 
        				  // accounts.
        				  UTLAccount acct = (new AccountsDbAdapter()).getAccount(task.account_id);
        				  if (acct!=null && acct.sync_service==UTLAccount.SYNC_TOODLEDO)
        				  {
        					  Util.longerPopup(_a, "", Util.getString(
        						  R.string.Priority_is_required));
        		              return;
        				  }
        			  }
        			  
        			  // Update the task in the database:
        			  task.priority = which;
        			  saveUpdateAndRefresh(task,tasksDB);

                      if (!task.completed) (new FeatureUsage(_a)).record(FeatureUsage.PRIORITY);
        		  }
        	  });
        	  builder.setTitle(Util.getString(R.string.New_Priority_for)+" \""+t.title+"\"");
        	  builder.show();
        	  return (true);
        	  
          case OPEN_CONTACT_ID:
        	  handleModeChange(MODE_NORMAL);
        	  i = new Intent(Intent.ACTION_VIEW);
        	  Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.
        		  CONTENT_LOOKUP_URI,t.contactLookupKey);
        	  i.setData(uri);
        	  try
        	  {
        		  _a.startActivity(i);
        	  }
        	  catch (ActivityNotFoundException e)
        	  {
        		  Util.popup(_a, R.string.Not_Supported_By_Device);
        	  }
        	  return(true);
        	  
          case REASSIGN_ID:
    		// Make sure the task can be reassigned.  Shared tasks cannot be reassigned.  
    		if (t.is_joint)
    		{
    			Util.longerPopup(_a, null, Util.getString(R.string.shared_cannot_be_reassigned));
    			return(true);
    		}
    		
    		// Create a list of collaborators to choose from:
    		nameList = new ArrayList<String>();
    		_collIdList = new ArrayList<String>();
    		UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);
    		CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
    		c = cdb.queryCollaborators("account_id="+a._id+" and reassignable=1 and remote_id!='"+
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
    			return(true);
    		}
    		
    		// Create a dialog with the choices:
    		builder = new AlertDialog.Builder(_a);
    		nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.size());
    		builder.setSingleChoiceItems(nameArray, 0, new DialogInterface.OnClickListener()
			{				
    			@Override
    			public void onClick(DialogInterface dialog, int which)
    			{
    				// Dismiss the dialog:
    				dialog.dismiss();
    				
    		        // Get the task object we're looking at:
    			  	TasksDbAdapter db = new TasksDbAdapter();
    				UTLTask t = db.getTask(_chosenTaskID);
    			    if (t==null)
    			    {
    			        // This could happen if the task was deleted from Toodledo or Google.
    			        Util.popup(_a, R.string.Task_no_longer_exists);
    			        return;
    			    }

    			    // Add an entry to the pending reassignments table:
    				PendingReassignmentsDbAdapter pr = new PendingReassignmentsDbAdapter();
    				long rowID = pr.addReassignment(t._id, _collIdList.get(which));
    				
    				if (rowID>-1)
    				{
	      				// Tell synchronizer to upload the change:
	      				Intent i = new Intent(_a,Synchronizer.class);
	      				i.putExtra("command", "sync_item");
	      				i.putExtra("item_type", Synchronizer.TYPE_REASSIGN);
	      				i.putExtra("item_id", rowID);
	      				i.putExtra("account_id",t.account_id);
	      				i.putExtra("operation",Synchronizer.REASSIGN);
                        Synchronizer.enqueueWork(_a,i);
	      				
	      				// Display a brief message saying the task will be removed after TD has 
	      				// received the reassignment:
	      				Util.popup(_a, R.string.reassign_wait);

                        if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.TOODLEDO_COLLABORATION);
    				}
    				
    			}
			});
    		builder.setTitle(Util.getString(R.string.assign_to));
    	    builder.show();
    	    return (true);    
    	    
          case SHOW_MAP_ID:
        	  UTLLocation loc = (new LocationsDbAdapter()).getLocation(t.location_id);
	    		String coords = Double.valueOf(loc.lat).toString()+","+
	    			Double.valueOf(loc.lon).toString();
	    		i = new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+coords+"?q="+coords));
				try
				{
					_a.startActivity(i);
				}
				catch (ActivityNotFoundException e)
				{
					Util.popup(_a, R.string.Maps_Not_Installed);
				}
        	  break;
        	  
          case NAVIGATE_ID:
        	  loc = (new LocationsDbAdapter()).getLocation(t.location_id);
				i = new Intent(Intent.ACTION_VIEW,
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
        	  break;
        }
        return super.onContextItemSelected(item);
    }
    
    // Populate the action buttons and menu items with multi-task actions:
    private void populateActionMenuForMultipleTasks(Menu bottomMenu, Menu topMenu)
    {
        if (bottomMenu!=null)
            bottomMenu.clear();
        topMenu.clear();
		showActionModeDoneButton(true);

		boolean isPortrait = _a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT;
        if (isPortrait)
        {
            // In portrait mode, the items are on the bottom toolbar. The toolbar's click
            // handler needs adjusted.
            _bottomToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
            {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem)
                {
                    handleMultiSelectCommand(menuItem);
                    return true;
                }
            });
        }

        // In portrait mode, overflow items are at the top. In landscape mode, all items are
        // at the top.
        Menu mainMenu;
        Menu overflowMenu;
        if (isPortrait)
        {
            mainMenu = bottomMenu;
            overflowMenu = topMenu;
        }
        else
        {
            mainMenu = topMenu;
            overflowMenu = topMenu;
        }

		// We always show the "select all" command, even if there are no tasks selected.
		MenuItemCompat.setShowAsAction(mainMenu.add(0,SELECT_ALL_ID,0,R.string.Select_All).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_select_all))
    		,MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
		if (_selectedTasks==null || _selectedTasks.size()==0)
			return;
		
		// Loop through all selected tasks and record some information which
		// affects which menu items display:
		Collection<UTLTask> col = _selectedTasks.values();
		Iterator<UTLTask> it = col.iterator();
		boolean allComplete = true;
		boolean allIncomplete = true;
		boolean allAtTopLevel = true;
		boolean allStarred = true;
		boolean allUnstarred = true;
		boolean inSameAccount = true;
		long firstTaskAccountID = -1;
		while (it.hasNext())
		{
			UTLTask td = it.next();
			if (td.completed)
				allIncomplete = false;
			else
				allComplete = false;
			if (td.parent_id>0)
				allAtTopLevel = false;
			if (td.star)
				allUnstarred = false;
			else
				allStarred = false;
			if (firstTaskAccountID==-1)
				firstTaskAccountID = td.account_id;
			else if (td.account_id != firstTaskAccountID)
				inSameAccount = false;
		}
		
		// Add items to the menu:
		
		int buttonCount = 1;
		Menu conditionalMenu;
		int showOption;
        int showOptionPositive = MenuItemCompat.SHOW_AS_ACTION_ALWAYS;
        int showOptionNegative = isPortrait ? MenuItemCompat.SHOW_AS_ACTION_NEVER :
            MenuItemCompat.SHOW_AS_ACTION_IF_ROOM;

        // Complete / incomplete:
		if (!allComplete)
		{
			MenuItemCompat.setShowAsAction(mainMenu.add(0,MARK_COMPLETE_ID,0,R.string.Mark_Complete).
				setIcon(R.drawable.checkbox_checked),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			buttonCount++;
		}
		if (!allIncomplete)
		{
			MenuItemCompat.setShowAsAction(mainMenu.add(0,MARK_INCOMPLETE_ID,0,R.string.Mark_Incomplete).
				setIcon(R.drawable.checkbox_medium_gray),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
			buttonCount++;
		}

		// Subtask options:
		if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED,true))
        {
			if (inSameAccount)
			{
				MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_PARENT_ID,0,R.string.Change_Parent)
            		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
			}
			if (!allAtTopLevel)
			{
				MenuItemCompat.setShowAsAction(overflowMenu.add(0,UNLINK_FROM_PARENT_ID,0,R.string.Unlink_From_Parent_Task)
            		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
			}
        }

		// Star on/off:
		if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
		{
			if (allStarred)
			{
				buttonCount++;
                conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
                showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                    showOptionNegative;
				MenuItemCompat.setShowAsAction(conditionalMenu.add(0,STAR_OFF_ID,0,R.string.Remove_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_off)),showOption);
			}
			else if (allUnstarred)
			{
				buttonCount++;
                conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
                showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                    showOptionNegative;
				MenuItemCompat.setShowAsAction(conditionalMenu.add(0,STAR_ON_ID,0,R.string.Add_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_on)),showOption);
			}
			else
			{
				buttonCount++;
                conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
                showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                    showOptionNegative;
				MenuItemCompat.setShowAsAction(conditionalMenu.add(0,STAR_ON_ID,0,R.string.Add_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_on)),showOption);
				
				buttonCount++;
                conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
                showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
                    showOptionNegative;
				MenuItemCompat.setShowAsAction(conditionalMenu.add(0,STAR_OFF_ID,0,R.string.Remove_Star).
    				setIcon(_a.resourceIdFromAttr(R.attr.ab_star_off)),showOption);
			}
		}

		// Other fields, always in the overflow menu:
		if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_DUE_DATE_ID,0,R.string.Change_Due_Date)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}
		if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_START_DATE_ID,0,R.string.Change_Start_Date)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}
		if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_PRIORITY_ID,0,R.string.Change_Priority)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}
		if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true) && inSameAccount)
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_FOLDER_ID,0,R.string.Change_Folder)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}
		if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true) && inSameAccount)
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_CONTEXT_ID,0,R.string.Change_Context)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}
		if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
		{
			MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_STATUS_ID,0,R.string.Change_Status)
        		,MenuItemCompat.SHOW_AS_ACTION_NEVER);
		}

		// Delete:
		buttonCount++;
        conditionalMenu = buttonCount<=MAX_ACTION_BAR_BUTTONS ? mainMenu : overflowMenu;
        showOption = buttonCount<=MAX_ACTION_BAR_BUTTONS ? showOptionPositive :
            showOptionNegative;
		MenuItemCompat.setShowAsAction(conditionalMenu.add(0,DELETE_TASK_ID,0,R.string.Delete_Tasks).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_delete)),showOption);
    }
    
    // Handle a multi-select command:
    private void handleMultiSelectCommand(MenuItem menuItem)
    {
    	handleMultiSelectCommand(menuItem.getItemId());
    }
    private void handleMultiSelectCommand(int commandID)
    {
    	UTLTask t;
        TasksDbAdapter db = new TasksDbAdapter();;
        Intent i;
        
        if (commandID==SELECT_ALL_ID)
        {
        	// Mark all tasks as selected:
    		if (_selectedTasks==null)
    			_selectedTasks = new HashMap<Long,UTLTask>();
    		else
    			_selectedTasks.clear();
        	Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        	while (it.hasNext())
        	{
        		HashMap<String,UTLTaskDisplay> hash = it.next();
        		UTLTaskDisplay td = hash.get("task");
        		_selectedTasks.put(td.task._id, td.task);
        	}
        	
        	// Refresh the display:
        	saveCurrentPosition();
        	refreshData();
        	restorePosition();
        	
        	// Notify the user and display the available commands:
        	Util.popup(_a, R.string.All_Tasks_Selected);
        	_actionMode.invalidate();
        	return;
        }

		// Loop through all selected tasks and perform the chosen action (where possible):
		_uploadCount = 0;
        boolean fullSyncNeeded = false;
        boolean popupShown = false;
        AccountsDbAdapter adb = new AccountsDbAdapter();
        String commaString = Joiner.on(',').join(_selectedTasks.keySet());
        Cursor taskCursor = db.queryTasks("_id in ("+commaString+")","parent_id asc, sort_order "+
            "desc");
        taskCursor.moveToPosition(-1);
		while (taskCursor.moveToNext())
		{
			t = db.getUTLTask(taskCursor);
			if (t==null)
			{
				// It is possible for tasks to get deleted due to a sync, or a prior 
				// delete operation that caused subtasks to get deleted.
				continue;
			}
			
			switch(commandID)
			{
			case MARK_COMPLETE_ID:
				Util.markTaskComplete(t._id, false);
				if (_uploadCount<=MAX_UPLOADS)
	        	{
	        		Util.instantTaskUpload(_a, t);
	        		_uploadCount++;
	        	}
				break;
				
			case MARK_INCOMPLETE_ID:
				t.completed = false;
				t.completion_date = 0;
				saveUpdate(t,db);
				break;

			case DELETE_TASK_ID:
				if (!_authorizedMultiTaskDelete)
				{
					// The user must confirm this choice.
					// Handler for the yes and no options:
					DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
			    		OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							switch (which)
			                {
			                case DialogInterface.BUTTON_POSITIVE:
			                	// Yes tapped:
			                	_authorizedMultiTaskDelete = true;
			                	handleMultiSelectCommand(DELETE_TASK_ID);
			                	break;
			                case DialogInterface.BUTTON_NEGATIVE:
			                    // No clicked;
			                    break;
			                }
						}
					};
					
					// Build and display the dialog:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					builder.setMessage(R.string.Delete_Tasks_Confirmation);
					builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
			        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
			        builder.show();
			        return;
				}
				
				// If we get here, the user has authorized the deletion of the tasks.
				Util.deleteTask(t._id);
				break;
				
			case CHANGE_PARENT_ID:
				if (_chosenMultiSelectParent==-1)
				{
					// We need to choose the parent task:
					handleModeChange(MODE_SELECTING_NEW_PARENT);
					return;
				}
				
				// Get collaboration attributes from the parent, and copy:
				UTLTask parent = (new TasksDbAdapter()).getTask(_chosenMultiSelectParent);
				if (parent!=null)
				{
					t.is_joint = parent.is_joint;
					if (!parent.is_joint)
					{
						t.owner_remote_id = "";
						t.shared_with = "";
						t.added_by = "";
					}
					else
					{
						if (parent.owner_remote_id.length()>0 && t.owner_remote_id.length()==0)
							t.owner_remote_id = parent.owner_remote_id;
						t.shared_with = parent.shared_with;
						if (parent.owner_remote_id.length()>0)
						{
							if (t.shared_with.length()>0)
								t.shared_with += "\n";
							t.shared_with += parent.owner_remote_id;
						}
						if (parent.added_by.length()>0 && t.added_by.length()==0)
							t.added_by = parent.added_by;
					}
				}
				
				t.parent_id = _chosenMultiSelectParent;
                t.setSortOrderToBottom(); // Moves to bottom of the parent's list of children.
                t.is_moved = true;
                t.prev_task_id = -1;

                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.SUBTASKS);

				saveUpdate(t,db);
				break;
				
			case UNLINK_FROM_PARENT_ID:
				t.parent_id = 0;
                t.setSortOrderToBottom(); // Move it to the the bottom of list, if using manual sort.
                t.is_moved = true;
                t.prev_task_id = -1;
				saveUpdate(t,db);
				break;
				
			case CHANGE_START_DATE_ID:
				if (_chosenMultiSelectStartDate==0)
				{
					i = new Intent(_a, DateChooser.class);
					i.putExtra("prompt",Util.getString(R.string.Select_a_Start_Date));
		        	startActivityForResult(i,ACTIVITY_GET_START_DATE);
		        	return;
				}
				
				// We have the start date. Update the task:
				if (!t.uses_start_time)
					t.start_date = _chosenMultiSelectStartDate;
				else
				{
					// Need to combine the prior start TIME with the new start DATE.
        			// This function will do this:
        			t.start_date = Util.getBaseCompletionDate(_chosenMultiSelectStartDate,
        				t.start_date, true);
				}

                // If the task is linked to a calendar item, then the calendar item needs an
                // update.
                if (t.calEventUri!=null && t.calEventUri.length()>0)
                {
                    CalendarInterface ci = new CalendarInterface(_a);
                    String uri = ci.linkTaskWithCalendar(t);
                    if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
                    {
                        t.calEventUri = uri;
                    }
                    else
                    {
                        String errorMsg = uri.substring(CalendarInterface.ERROR_INDICATOR.length());
                        Util.longerPopup(_a, "", errorMsg);
                        return;
                    }
                }

                saveUpdate(t, db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.START_DATE);

        		break;
	        	  
			case CHANGE_DUE_DATE_ID:
				if (_chosenMultiSelectDueDate==0)
				{
					i = new Intent(_a, DateChooser.class);
					i.putExtra("prompt",Util.getString(R.string.Select_a_Due_Date));
		        	startActivityForResult(i,ACTIVITY_GET_DUE_DATE);
		        	return;
				}
				
				// We have the due date. Update the task:
				long priorDueDate = t.due_date;
				if (!t.uses_due_time)
					t.due_date = _chosenMultiSelectDueDate;
				else
				{
					// Need to combine the prior due TIME with the new due DATE.
        			// This function will do this:
        			t.due_date = Util.getBaseCompletionDate(_chosenMultiSelectDueDate,
        				t.due_date, true);
				}

                // If the task has a reminder, it is set to the exact same amount of
                // time prior to the due date:
                if (t.reminder>0 && priorDueDate>0)
                {
                    long oldReminderTime = t.reminder;
                    t.reminder = t.due_date - (priorDueDate-t.reminder);
                    if (t.reminder>System.currentTimeMillis() && oldReminderTime<
                        System.currentTimeMillis())
                    {
                        // Cancel any pending alarms:
                        Util.cancelReminderNotification(t._id);

                        // Remove the notification if it is displaying:
                        Util.removeTaskNotification(t._id);
                    }
                }

                // If the task is linked to a calendar item, then the calendar item needs an
                // update.
                if (t.calEventUri!=null && t.calEventUri.length()>0)
                {
                    CalendarInterface ci = new CalendarInterface(_a);
                    String uri = ci.linkTaskWithCalendar(t);
                    if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
                    {
                        t.calEventUri = uri;
                    }
                    else
                    {
                        String errorMsg = uri.substring(CalendarInterface.ERROR_INDICATOR.length());
                        Util.longerPopup(_a, "", errorMsg);
                        return;
                    }
                }

                saveUpdate(t,db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.DUE_DATE);

        		break;
	        	
			case STAR_ON_ID:
				if (!popupShown)
				{
					Util.popup(_a, R.string.star_added);
					popupShown = true;
				}
				t.star = true;
				saveUpdate(t,db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.STAR);
				break;
				
			case STAR_OFF_ID:
				if (!popupShown)
				{
					Util.popup(_a, R.string.star_removed);
					popupShown = true;
				}
				t.star = false;
				saveUpdate(t,db);
				break;
				
			case CHANGE_FOLDER_ID:
				if (_chosenMultiSelectFolderID==-1)
				{
					// We need to choose a folder.
					// Create a list of folders to choose from.  Include the "no folder" option
					// if this is not a google account:
					ArrayList<String> nameList = new ArrayList<String>();
					_idList = new ArrayList<Long>();
					UTLAccount acct = (new AccountsDbAdapter()).getAccount(t.account_id);
					if (acct!=null && (acct.sync_service==UTLAccount.SYNC_NONE ||
						acct.sync_service==UTLAccount.SYNC_TOODLEDO))
					{
						nameList.add(Util.getString(R.string.None));
						_idList.add(0L);
					}
					Cursor c = (new FoldersDbAdapter()).queryFolders("account_id="+t.account_id+
						" and archived=0", FoldersDbAdapter.bestSortOrder(t.account_id));
					c.moveToPosition(-1);
					while (c.moveToNext())
					{
						nameList.add(Util.cString(c, "title"));
						_idList.add(Util.cLong(c, "_id"));
					}
					c.close();
					
					// Create a dialog with these choices:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					String[] nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.
						size());
					builder.setItems(nameArray, new DialogInterface.OnClickListener()
					{				
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							// Dismiss the dialog:
							dialog.dismiss();

							// Get the new folder ID, and the task we're modifying:
							_chosenMultiSelectFolderID = _idList.get(which);
							
							// Call this function again now that the folder is chosen:
							handleMultiSelectCommand(CHANGE_FOLDER_ID);
						}
					});
					builder.setTitle(Util.getString(R.string.New_Folder));
					builder.show();
					return;
				}
				
				// The folder is known, so update the task.
				// If the task is linked to a Google account and the folder was
				// changed, we also need to change the folders of any subtasks:
				UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);
				if (a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE &&
					t.folder_id != _chosenMultiSelectFolderID)
				{
					t.folder_id = _chosenMultiSelectFolderID;
                    if (!_selectedTasks.containsKey(t.parent_id))
                    {
                        // Task has no parent in new folder, since the parent is not moving.
                        t.parent_id = 0;
                    }

                    t.setSortOrderToBottom();  // Task is at bottom of list in folder.
					t.mod_date = System.currentTimeMillis();
					if (!db.modifyTask(t))
					{
						Util.log("DB modification failed for task ID "+t._id+
							" when changing the folder.");
						Util.popup(_a, R.string.DbModifyFailed);
					}
					else
					{
						// Change the folders:
						changeSubtaskFolders(t);

						// Start a full sync to upload all of the changes:
						fullSyncNeeded = true;
					}
				}
				else
				{
					// Update the folder in the database:
					t.folder_id = _chosenMultiSelectFolderID;            			  
					saveUpdate(t,db);
                    if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.FOLDERS_FOR_TASKS);
				}
				break;
				
			case CHANGE_CONTEXT_ID:
				if (_chosenMultiSelectContextID==-1)
				{
					// We need to choose a context.
					// Create a list of contexts to choose from.
					ArrayList<String> nameList = new ArrayList<String>();
					_idList = new ArrayList<Long>();
					UTLAccount acct = (new AccountsDbAdapter()).getAccount(t.account_id);
					nameList.add(Util.getString(R.string.None));
					_idList.add(0L);
					Cursor c = (new ContextsDbAdapter()).queryContexts("account_id="+t.account_id, 
						"title");
					c.moveToPosition(-1);
					while (c.moveToNext())
					{
						nameList.add(Util.cString(c, "title"));
						_idList.add(Util.cLong(c, "_id"));
					}
					c.close();
					
					// Create a dialog with these choices:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					String[] nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.
						size());
					builder.setItems(nameArray, new DialogInterface.OnClickListener()
					{				
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							// Dismiss the dialog:
							dialog.dismiss();

							// Get the new folder ID, and the task we're modifying:
							_chosenMultiSelectContextID = _idList.get(which);
							
							// Call this function again now that the folder is chosen:
							handleMultiSelectCommand(CHANGE_CONTEXT_ID);
						}
					});
					builder.setTitle(Util.getString(R.string.New_Context));
					builder.show();
					return;
				}
				
				// The context is known, so update the task.
				t.context_id = _chosenMultiSelectContextID;            			  
				saveUpdate(t,db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.CONTEXTS);
				break;
				
			case CHANGE_STATUS_ID:
				if (_chosenMultiSelectStatus==-1)
				{
					// The user has not yet picked the new status.
					// Create a dialog with status options:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					builder.setItems(this.getResources().getStringArray(R.array.statuses), 
						new DialogInterface.OnClickListener()
					{				
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							// Dismiss the dialog:
							dialog.dismiss();

							// Get the new status, and the task we're modifying:
							_chosenMultiSelectStatus = which;

							// Call this function again now that the folder is chosen:
							handleMultiSelectCommand(CHANGE_STATUS_ID);
						}
					});
					builder.setTitle(Util.getString(R.string.New_Status));
					builder.show();
					return;
				}
				
				t.status = _chosenMultiSelectStatus;
				saveUpdate(t,db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.STATUS);
				break;

			case CHANGE_PRIORITY_ID:
				if (_chosenMultiSelectPriority==-1)
				{
					// The user has not yet picked the new priority.
					// Create a dialog with priority options:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					builder.setItems(this.getResources().getStringArray(R.array.priorities), 
						new DialogInterface.OnClickListener()
					{				
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							// Dismiss the dialog:
							dialog.dismiss();

							// Get the new priority, and the task we're modifying:
							_chosenMultiSelectPriority = which;

							// Call this function again now that the folder is chosen:
							handleMultiSelectCommand(CHANGE_PRIORITY_ID);
						}
					});
					builder.setTitle(Util.getString(R.string.New_Priority));
					builder.show();
					return;
				}
				
				// Make sure we're not trying to set a priority of <none> for a Toodledo task:
				if (_chosenMultiSelectPriority==0)
				{
					UTLAccount acc = adb.getAccount(t.account_id);
					if (acc.sync_service==UTLAccount.SYNC_TOODLEDO)
					{
						if (_multiSelectErrorCount==0)
	        			{
	        				_multiSelectErrorCount++;
	        				Util.longerPopup(_a, "", Util.getString(R.string.Priority_is_required));
	        			}
						break;
					}
				}
				t.priority = _chosenMultiSelectPriority;
				saveUpdate(t,db);
                if (!t.completed) (new FeatureUsage(_a)).record(FeatureUsage.PRIORITY);
				break;
			}
		}
		
        // Switch back to normal mode:
		_authorizedMultiTaskDelete = false;
		_chosenMultiSelectFolderID = -1;
		_chosenMultiSelectContextID = -1;
		_chosenMultiSelectStatus = -1;
		_chosenMultiSelectPriority = -1;
		_chosenMultiSelectDueDate=0;
		_chosenMultiSelectStartDate=0;
		_chosenMultiSelectParent = -1;
		_multiSelectErrorCount = 0;
		
		// Start a full sync if the changes are significant enough:
		if (fullSyncNeeded)
		{
			i = new Intent(_a, Synchronizer.class);
			i.putExtra("command", "full_sync");
			i.putExtra("is_scheduled", true);
            Synchronizer.enqueueWork(_a,i);
		}
		
		// Refresh the display and switch to normal mode:
		handleModeChange(MODE_NORMAL);
		
		// Refresh other panes if necessary:
        refreshOtherPanes();
    }
  
    // Save a task update in the database:
    private void saveUpdate(UTLTask t, TasksDbAdapter db)
    {
    	t.mod_date = System.currentTimeMillis();
        if (!db.modifyTask(t))
        {
            Util.log("DB modification failed for task ID "+t._id+
                " when saving a task change.");
            Util.popup(_a, R.string.DbModifyFailed);
        }
        else
        {
        	if (_uploadCount<=MAX_UPLOADS)
        	{
        		Util.instantTaskUpload(_a, t);
        		_uploadCount++;
        	}
        }
    }
    
    // Update a task that has been modified from the action mode menu, and refresh:
    public void saveUpdateAndRefresh(UTLTask t, TasksDbAdapter db)
    {
    	t.mod_date = System.currentTimeMillis();
        if (!db.modifyTask(t))
        {
            Util.log("DB modification failed for task ID "+t._id+
                " when saving a task change.");
            Util.popup(_a, R.string.DbModifyFailed);
        }
        else
        {
            // Upload the change (if enabled):
            Util.instantTaskUpload(_a, t);
			  
            // After a change is made, we switch back to normal mode:
            handleModeChange(MODE_NORMAL);
            
            // Refresh other panes if necessary:
            refreshOtherPanes();
        }
    }
    
    // Populate the options menu when it is invoked:
    @Override
    public void onCreateOptionsMenu(Menu topMenu, MenuInflater inflater)
    {
        if (_a.getOrientation()==UtlActivity.ORIENTATION_LANDSCAPE)
        {
            // In landscape mode, we use the top action bar for the menu, regardless of whether
            // we're in split-screen mode.
            topMenu.clear();
            inflater.inflate(R.menu.task_list, topMenu);

            if (!SpeechRecognizer.isRecognitionAvailable(_a))
            {
                // No speech recognition services.  Need to hide the menu item.
                topMenu.removeItem(R.id.menu_task_list_voice_mode);
            }

            // There's always room for the menu item at index 5 in landscape view:
            topMenu.getItem(4).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            topMenu.getItem(5).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            if (!_firstSortField.equals("tasks.sort_order"))
            {
                // The "Sort Order..." menu item is redundant if the manual sort order is
                // not the first sort field.
                topMenu.removeItem(R.id.menu_task_list_sort_order);
            }

            // If we're using split-screen views, then add in an option to resize the panes:
            if (_ssMode != Util.SS_NONE)
            {
                topMenu.add(0,RESIZE_PANES_ID,0,R.string.Resize_Panes)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            // Make sure the bottom toolbar and floating action button are invisible:
            _bottomToolbar.setVisibility(View.GONE);
            _fab.setVisibility(View.GONE);
            _fab2.setVisibility(View.VISIBLE);
            _listView.setPadding(0,0,0,0);
        }
        else
        {
            // In portrait mode, we use the bottom toolbar for the first 4 items, and
            // an overflow menu in the top toolbar for the rest.  We also need to add some
            // padding to the list view to make sure the bottom toolbar does not hide the
            // last task.

            _listView.setPadding(0,0,0,_bottomToolbar.getHeight());

            // Clear all menu items and inflate everything to the top to start:
            if (_bottomToolbar.getMenu()!=null)
                _bottomToolbar.getMenu().clear();
            topMenu.clear();
            inflater.inflate(R.menu.task_list, topMenu);

            if (!SpeechRecognizer.isRecognitionAvailable(_a))
            {
                // No speech recognition services.  Need to hide the menu item.
                topMenu.removeItem(R.id.menu_task_list_voice_mode);
            }

            if (!_firstSortField.equals("tasks.sort_order"))
            {
                // The "Sort Order..." menu item is redundant if the manual sort order is
                // not the first sort field.
                topMenu.removeItem(R.id.menu_task_list_sort_order);
            }

            // If we're using split-screen views, then add in an option to resize the panes:
            if (_ssMode != Util.SS_NONE)
            {
                topMenu.add(0,RESIZE_PANES_ID,0,R.string.Resize_Panes)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }

            // Add the first four items from the top menu to the bottom, while removing them
            // from the top:
            Menu bottomMenu = _bottomToolbar.getMenu();
            for (int i=0; i<4; i++)
            {
                MenuItem menuItem = topMenu.getItem(i);
                bottomMenu.add(0,menuItem.getItemId(),0,menuItem.getTitle())
                    .setIcon(menuItem.getIcon())
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            for (int i=0; i<4; i++)
                topMenu.removeItem(topMenu.getItem(0).getItemId());

            // All items in the top menu must show in the overflow menu:
            for (int i=0; i<topMenu.size(); i++)
                topMenu.getItem(i).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            // Taps on menu items in the bottom bar will be handled just like the top action bar:
            _bottomToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
            {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem)
                {
                    return onOptionsItemSelected(menuItem);
                }
            });

            // Make sure the landscape floating action button is not visible.
            _fab2.setVisibility(View.GONE);
        }
    }
    
    // Handlers for an options menu choices:
    @SuppressLint("InlinedApi")
	@Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        Intent i;
        
        switch(item.getItemId()) {
        case R.id.menu_task_list_view_refresh:
            refreshData();
            return true;
        case R.id.menu_task_list_sync_now:
            startSync();
          	return true;
        case R.id.menu_task_list_save_view:
        	i = new Intent(_a,SaveView.class);
        	i.putExtra("view_id",viewID);
        	this.startActivity(i);
            return true;
        case R.id.menu_task_list_start_multi_select:
        	if (_mode==MODE_NORMAL)
        	{
        		if (_selectedTasks==null)
        			_selectedTasks = new HashMap<Long,UTLTask>();
        		else
        			_selectedTasks.clear();
        		handleModeChange(MODE_MULTI_SELECT);
        	}
        	return true;
        	
        case R.id.menu_task_list_search:
        	i = new Intent(_a,QuickSearch.class);
        	i.putExtra("base_view_id", viewID);
        	_a.startActivity(i);
        	return true;
        	
        case R.id.menu_task_list_voice_mode:
        	i = new Intent(_a,VoiceCommand.class);
            if (Build.VERSION.SDK_INT>=11)
            	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        	_a.startActivity(i);
        	return true;
        	
        case R.id.menu_task_list_filter:
        	i = new Intent(_a,ViewRulesList.class);
        	i.putExtra("view_id", viewID);
        	_a.startActivityForResult(i, ACTIVITY_FILTER);
        	return true;
        	
        case R.id.menu_task_list_sort:
            if (_firstSortField.equals("tasks.sort_order"))
            {
                // In this case, we enter action mode to allow the user to drag tasks around.
                handleManualSortCommand();
            }
            else
            {
        	    i = new Intent(_a,SortOrder.class);
        	    i.putExtra("view_id", viewID);
        	    _a.startActivityForResult(i, ACTIVITY_SORT);
            }
        	return true;

        case R.id.menu_task_list_start_manual_sort:
            handleManualSortCommand();
            return true;

        case R.id.menu_task_list_sort_order:
            i = new Intent(_a,SortOrder.class);
            i.putExtra("view_id", viewID);
            _a.startActivityForResult(i, ACTIVITY_SORT);
            return true;

        case R.id.menu_task_list_display:
        	if (topLevel.equals("widget"))
        	{
        		// In this case, the user must choose between editing the display
        		// options for the full-screen list, and for the widget.
        		handleWidgetDisplayOptions();
        		return true;
        	}
        	i = new Intent(_a,EditDisplayOptions.class);
        	i.putExtra("view_id", viewID);
        	_a.startActivityForResult(i, ACTIVITY_DISPLAY);
        	return true;
        	
        case RESIZE_PANES_ID:
        	_a.enterResizeMode();
        	return true;
        }
       
        return super.onOptionsItemSelected(item);
    }

    /** Handler for the button to add a new task: */
    public void startAddingTask()
    {
        // Can't add a task in multi-select mode.
        if (_mode==MODE_MULTI_SELECT)
            handleModeChange(MODE_NORMAL);

        Intent i = new Intent(_a, EditTask.class);
        i.putExtra("action", EditTaskFragment.ADD);
        
        // If this is a folder, context, goal, or location view, then we can set a default
        // for the new task:
        if (topLevel.equals("folders"))
        	i.putExtra("default_folder_id", Long.parseLong(viewName));
        if (topLevel.equals("contexts"))
        	i.putExtra("default_context_id", Long.parseLong(viewName));
        if (topLevel.equals("goals"))
        	i.putExtra("default_goal_id", Long.parseLong(viewName));
        if (topLevel.equals("locations"))
        	i.putExtra("default_loc_id", Long.parseLong(viewName));
        
        // If we're showing a view containing subtasks, then offer the user the 
        // option to create a regular task or a subtask:
        if (_isSubtaskView)
        {
			// Create a dialog with the 2 choices (new regular task, or new subtask):
	    	AlertDialog.Builder builder = new AlertDialog.Builder(_a);
	    	String[] choices = { 
	    		Util.getString(R.string.Add_new_regular_task),
	    		Util.getString(R.string.Add_another_subtask)
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
						i.putExtra("parent_id", _parentID);
						tag = "/add_sub/"+_parentID;
					}
					if (_a.useNewActivityForDetails())
						_a.startActivity(i);
					else
					{
						EditTaskFragment frag = new EditTaskFragment();
						frag.setArguments(i.getExtras());
						_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.
							FRAG_TAG + tag);
					}	
		        	dialog.dismiss();
				}
			});
	    	builder.setTitle(R.string.Select_One_);

	    	// If this is a Google account, then we can only allow a new subtask under certain
            // conditions.
            UTLTask parentTask = (new TasksDbAdapter()).getTask(_parentID);
            UTLAccount a = (new AccountsDbAdapter()).getAccount(parentTask.account_id);
            if (a.sync_service==UTLAccount.SYNC_GOOGLE)
            {
                if (System.currentTimeMillis()<Util.GOOGLE_SUB_SUB_TASKS_EXPIRY ||
                    parentTask.parent_id==0)
                {
                    builder.show();
                    return;
                }
            }
            else
            {
                builder.show();
                return;
            }
        }
        
        if (_a.useNewActivityForDetails())
		{
			// No split-screen in use.  Just start a new activity:
        	startActivityForResult(i, ACTIVITY_ADD_TASK);
		}
		else
		{
			// In split-screen mode, display the task editor in a separate fragment:
			EditTaskFragment frag = new EditTaskFragment();
			frag.setArguments(i.getExtras());
			_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditTaskFragment.FRAG_TAG +
                "/add/"+System.currentTimeMillis());
		}    	
    }

    /** Handler for the command to enter manual sort mode. Returns true if manual sort was entered. */
    public boolean handleManualSortCommand()
    {
        if (!_manualSortPurchased)
        {
            // The manual sort add-on is not purchased. Offer it using a dialog.
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
                OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    switch (which)
                    {
                        case DialogInterface.BUTTON_POSITIVE:
                            // "Buy Now" tapped:
                            _pm.startPurchase(PurchaseManager.SKU_MANUAL_SORT, new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    _manualSortPurchased = _pm.isPurchased(PurchaseManager.
                                        SKU_MANUAL_SORT);
                                }
                            });
                            break;
                        case DialogInterface.BUTTON_NEUTRAL:
                            // "Learn More" tapped.
                            Intent i = new Intent(_a,StoreItemDetail.class);
                            i.putExtra("sku",PurchaseManager.SKU_MANUAL_SORT);
                            startActivity(i);
                            break;
                    }
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(_a);
            builder.setMessage(R.string.manual_sort_not_purchased);
            builder.setTitle(R.string.add_on_missing);
            builder.setPositiveButton(getString(R.string.buy)+" ("+_pm.getPrice(PurchaseManager.
                SKU_MANUAL_SORT)+")", dialogClickListener);
            builder.setNegativeButton(R.string.Cancel, dialogClickListener);
            builder.setNeutralButton(R.string.learn_more, dialogClickListener);
            builder.show();
            return false;
        }
        else if (!_firstSortField.equals("tasks.sort_order"))
        {
            // The first sort order is not the manual sort order.  Give the user an easy way to
            // fix this in a dialog.
            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
                OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    switch (which)
                    {
                        case DialogInterface.BUTTON_POSITIVE:
                            // "Yes" tapped. Change the sort order:
                            (new ViewsDbAdapter()).makeManualSortFirst(viewID);

                            // Refresh the display:
                            saveCurrentPosition();
                            refreshData();
                            restorePosition();

                            // Switch to manual sort mode.
                            handleModeChange(MODE_MANUAL_SORT);

                            _a.invalidateOptionsMenu();
                            break;
                    }
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(_a);
            builder.setMessage(R.string.sort_order_set_incorrectly);
            builder.setTitle(R.string.incorrect_sort_order);
            builder.setPositiveButton(R.string.Yes, dialogClickListener);
            builder.setNegativeButton(R.string.No, dialogClickListener);
            builder.show();
            return false;
        }
        else
        {
            handleModeChange(MODE_MANUAL_SORT);
            return true;
        }
    }

    // If we return here after leaving, we must refresh the data:
    // Called when: activity first started
    @Override
    public void onResume()
    {
        super.onResume();
        refreshData();

    	restorePosition();

        if (Synchronizer.isSyncing() && waitingOnManualSync)
        {
        	_syncProgressContainer.setVisibility(View.VISIBLE);
        }
        else
        {
        	waitingOnManualSync = false;
        	_syncProgressContainer.setVisibility(View.GONE);
        }
        
        // Establish a link to the Synchronizer service:
        doBindService();
        
        // Check to see if we're displaying a view that is showing subtasks of a particular
        // task:
        Cursor c = (new ViewRulesDbAdapter()).getAllRules(viewID);
        while (c.moveToNext())
        {
        	if (Util.cString(c, "field_name").equals("parent_id"))
        	{
        		// We have a rule that filters on parent ID.
        		_parentID = Long.parseLong(Util.cString(c,"filter_string"));
        		_isSubtaskView = true;
        		break;
        	}
        }
        c.close();

        // The menu options may change if the sort order was changed, so refresh the options menu:
        _a.invalidateOptionsMenu();

        // Show the GDPR consent if necessary.
        if (ConsentUtil.getConsentStatus()==ConsentInformation.ConsentStatus.REQUIRED &&
            !_gdprConsentDialogShown && _pm.stat()==PurchaseManager.SHOW_ADS)
        {
            _gdprConsentDialogShown = true;
            ConsentUtil.showConsentForm(_a,null);
        }
        else if (Util.checkExactAlarmPermission(_a))
        {
           Log.v(TAG,"User has been prompted for exact alarm permission.");
        }
        else if (Util.showNotificationRationaleIfNeeded(_a))
        {
            Log.v(TAG,"User has been prompted to enable notifications.");
        }
    }
    
    // Called before the activity is destroyed due to orientation change:
    @Override
	public void onSaveInstanceState(Bundle b)
    {
    	b.putBoolean("waiting_on_manual_sync", waitingOnManualSync);
    	b.putLong("context_menu_task_id", _chosenTaskID);
    	b.putInt("mode", _mode);
    	if (_selectedTasks!=null && _selectedTasks.size()>0)
    	{
    		b.putLongArray("selected_tasks", Util.iteratorToLongArray(_selectedTasks.keySet().
    			iterator(), _selectedTasks.keySet().size()));
    	}
    	
    	// Save the info on which tasks we're displaying:
    	b.putString("top_level", topLevel);
    	b.putString("view_name", viewName);
    	b.putString("title", _title);
    }
        
    // Handler for pausing the activity (when we leave):
    @Override
    public void onPause()
    {
        super.onPause();
        
        saveCurrentPosition();
        
        // Remove the link to the synchronizer service:
        doUnbindService();
        
        // If we are viewing the "widget" list, then update the widgets when we exit:
        if (topLevel.equals("widget"))
        	Util.updateWidgets();

        // If instant upload is on, then upload any moved tasks.
        if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD,true))
            uploadMovedTasks();
    }
    
    // Save the current position that we're scrolled to in the list:
    public void saveCurrentPosition()
    {
        _lastSelection = _listView.getFirstVisiblePosition();
    }
    
    // Restore the position that was saved:
    public void restorePosition()
    {
        if (_lastSelection>-1 && _lastSelection<_listView.getCount())
        {
        	_listView.setSelection(_lastSelection);
        }

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
          case ACTIVITY_EDIT_TASK:
            handleModeChange(MODE_NORMAL);
            break;
            
          case ACTIVITY_ADD_SUBTASK:
        	  handleModeChange(MODE_NORMAL);
            break;
            
          case ACTIVITY_ADD_TASK:
            // Nothing to do.  The display will refresh due to the automatic call to onResume().
            break;
          
          case ACTIVITY_FILTER:
        	// If we're changing the filter for the widget, then we need to broadcast the
        	// update:
        	if (this.topLevel.equals("widget"))
        		Util.updateWidgets();
            break;

          case ACTIVITY_SORT:
          	// If we're changing the filter for the widget, then we need to broadcast the
          	// update:
          	if (this.topLevel.equals("widget"))
          		Util.updateWidgets();
            break;

          case ACTIVITY_DISPLAY:
          	// If we're changing the filter for the widget, then we need to broadcast the
          	// update:
          	if (this.topLevel.equals("widget"))
          		Util.updateWidgets();
            break;
            
          case ACTIVITY_GET_START_DATE:
        	if (resultCode==Activity.RESULT_OK && extras.containsKey("chosen_date"))
            {
      			// Record the new start date and call the handler function:
      			_chosenMultiSelectStartDate = extras.getLong("chosen_date");
      			handleMultiSelectCommand(CHANGE_START_DATE_ID);
      			return;
            }
        	break;
        	
          case ACTIVITY_GET_DUE_DATE:
          	if (resultCode==Activity.RESULT_OK && extras.containsKey("chosen_date"))
            {
      			// Record the new due date and call the handler function:
      			_chosenMultiSelectDueDate = extras.getLong("chosen_date");
      			handleMultiSelectCommand(CHANGE_DUE_DATE_ID);
      			return;
            }
          	break;
        }
    }

    /** Start a manual synchronization: */
    public void startSync()
    {
        waitingOnManualSync = true;
        _syncProgressContainer.setVisibility(View.VISIBLE);
    	if (!Synchronizer.isSyncing())
    	{
    		Intent i = new Intent(_a, Synchronizer.class);
    		i.putExtra("command", "full_sync");
    		i.putExtra("send_percent_complete", true);
            Synchronizer.enqueueWork(_a,i);
            _syncProgressBar.setProgress(0);
            Util.popup(_a,R.string.Sync_Started);     
    	}
    	else
    		Util.popup(_a, R.string.Sync_is_currently_running);
    }
    
    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Synchronizer.SYNC_RESULT_MSG:
                    int result = msg.arg1;
                    int itemsDownloaded = msg.arg2;
                    _syncProgressContainer.setVisibility(View.GONE);
                    if (result==GTasksInterface.NEEDS_NEW_SIGN_IN)
                    {
                        // This can happen after the user restores from backup. It affects
                        // Google accounts that were listed on the device.
                        String prompt = _a.getString(R.string.google_sign_in_needed,
                            GTasksInterface._accountToSignInto.username);
                        new AlertDialog.Builder(_a)
                            .setMessage(prompt)
                            .setPositiveButton(R.string.sign_in,null)
                            .setOnDismissListener(new DialogInterface.OnDismissListener()
                            {
                                @Override
                                public void onDismiss(DialogInterface dialog)
                                {
                                    Intent signInIntent = new Intent(_a,GTasksSetup.class);
                                    signInIntent.putExtra("sync_mode",GTasksSetup.MERGE);
                                    signInIntent.putExtra("account_id",GTasksInterface.
                                        _accountToSignInto._id);
                                    startActivity(signInIntent);
                                }
                            })
                            .show();
                    }
                    else if (waitingOnManualSync)
                    {
                        waitingOnManualSync = false;
                        if (result == Synchronizer.SUCCESS)
                        {
                            Util.popup(_a, R.string.Sync_Successful);
                        }
                        else
                        {
                            Util.popup(_a, Util.getString(R.string.Sync_Failed_)+Synchronizer.getFailureString(result));
                            Bundle msgBundle = msg.getData();
                            if (msgBundle!=null && msgBundle.containsKey("intent"))
                            {
                                // An intent was passed in that calls an Activity to allow the user
                                // to fix the issue.  Launch that Activity.
                                Util.log("TaskListFragment: Received auth fix Intent.");
                                Intent authFixIntent = (Intent)msgBundle.getParcelable("intent");
                                _a.startActivity(authFixIntent);
                            }
                        }
                        if (itemsDownloaded==1)
                        {
                        	saveCurrentPosition();
                            refreshData();
                            restorePosition();
                            _a.refreshWholeNavDrawer();
                        }
                    }
                    else
                    {
                        if (itemsDownloaded==1)
                        {
                            Util.popup(_a, R.string.Sync_Completed_Refreshing);
                        	saveCurrentPosition();
                            refreshData();
                            restorePosition();
                            _a.refreshWholeNavDrawer();
                        }
                    }
                    break;
                    
                case Synchronizer.PERCENT_COMPLETE_MSG:
                	// Update the progress bar:
                	int percentComplete = msg.arg1;
                	_syncProgressBar.setProgress((int) (_syncProgressBar.getMax()
    					* percentComplete / 100.0));
    				break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try 
            {
                Message msg = Message.obtain(null,
                        Synchronizer.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    };

    void doBindService() 
    {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
    	if (!mIsBound)
    	{
    	    Intent i = new Intent(_a,Synchronizer.class);
    		_a.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
    		mIsBound = true;
    	}
    }

    void doUnbindService() 
    {
        if (mIsBound) 
        {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) 
            {
                try 
                {
                    Message msg = Message.obtain(null,
                        Synchronizer.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } 
                catch (RemoteException e) 
                {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            _a.unbindService(mConnection);
            mIsBound = false;
        }
    }

    // Handler for the display options menu item, when we're viewing tasks in a widget:
    private void handleWidgetDisplayOptions()
    {
    	// Create a dialog with the 2 choices (full screen display or widget display):
    	AlertDialog.Builder builder = new AlertDialog.Builder(_a);
    	String[] choices = { 
    		Util.getString(R.string.Widget_Display),
    		Util.getString(R.string.Full_Screen_View)
    	};
    	builder.setSingleChoiceItems(choices, 0, new DialogInterface.OnClickListener()
		{			
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				Intent i;
				if (which==0)
					i = new Intent(_a,WidgetDisplayOptions.class);
				else
					i = new Intent(_a,EditDisplayOptions.class);
	        	i.putExtra("view_id", viewID);
	        	dialog.dismiss();
	        	_a.startActivityForResult(i, ACTIVITY_DISPLAY);
			}
		});
    	builder.setTitle(R.string.Adjust_Display_For_);
    	builder.show();
    }
    
    // Update the display for a mode change. The input is the new mode:
    public void handleModeChange(int newMode)
    {
    	int oldMode = _mode;
    	_mode = newMode;
    	switch (newMode)
    	{
    	case MODE_NORMAL:
    		if (_selectedTasks!=null)
    			_selectedTasks.clear();
    		if (_inActionMode)
    		{
    			_actionMode.finish();
                _a.invalidateOptionsMenu();
    		}
            _autoScroll = 0;
    		break;
    		
    	case MODE_SELECTING_NEW_PARENT:
    		_actionMode.invalidate();
    		break;

        case MODE_MANUAL_SORT:
            if (newMode != oldMode)
            {
                _actionMode = _a.startSupportActionMode(new ActionMode.Callback()
                {
                    @Override
                    public boolean onPrepareActionMode(ActionMode am, Menu menu)
                    {
                        _inActionMode = true;
                        return true;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode am)
                    {
                        _inActionMode = false;
                        _a.recordActionModeEnded();
                        handleModeChange(MODE_NORMAL);
                        saveCurrentPosition();
                        refreshData();
                        restorePosition();
                        _manualSortFromLongPress = false;
                    }

                    @Override
                    public boolean onCreateActionMode(ActionMode am, Menu menu)
                    {
                        _inActionMode = true;

                        // Add menu items for the regular sort settings, as well as finishing the
                        // sort.
                        menu.clear();
                        MenuItemCompat.setShowAsAction(menu.add(0, AM_MANUAL_SORT_SETTINGS, 0, R.string.Settings).
                            setIcon(_a.resourceIdFromAttr(R.attr.ab_settings))
                            , MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

                        // Set the title at the top:
                        am.setTitle(R.string.manual_sort_instructions);
                        return true;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode am, MenuItem menuItem)
                    {
                        if (menuItem.getItemId()==AM_MANUAL_SORT_SETTINGS)
                        {
                            am.finish();
                            Intent i = new Intent(_a,SortOrder.class);
                            i.putExtra("view_id", viewID);
                            _a.startActivityForResult(i, ACTIVITY_SORT);
                        }
                        return true;
                    }
                });
                _a.recordActionModeStarted(_actionMode);
                showActionModeDoneButton(true);
            }
            break;

       	case MODE_MULTI_SELECT:
    		if (_selectedTasks!=null && _selectedTasks.size()==1 && _chosenTaskID==0)
    		{
    			// If only one task is selected, we should make sure that _chosenTaskID is set to
    			// that task.  The code to select the appropriate menu items to display uses this.
    			_chosenTaskID = _selectedTasks.keySet().iterator().next();
    		}
    		
    		if (newMode != oldMode)
    		{
	    		// Start action mode, with a special action bar at the top.
				_actionMode = _a.startSupportActionMode(new ActionMode.Callback()
				{
					@Override
					public boolean onPrepareActionMode(ActionMode am, Menu topMenu)
					{
                        Menu bottomMenu = null;
                        Menu cancelMenu = topMenu;
					    if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
                        {
                            // In portrait mode, the affected menu is the bottom toolbar.
                            bottomMenu = _bottomToolbar.getMenu();
                            cancelMenu = bottomMenu;
                        }
						_inActionMode = true;
					    updateBottomToolbarAppearance();
						if (_mode==MODE_SELECTING_NEW_PARENT)
						{
							// The only menu item is a cancel button:
                            cancelMenu.clear();
                            cancelMenu.add(0,CANCEL_PARENT_SELECTION_ID,0,R.string.Cancel)
                                .setIcon(_a.resourceIdFromAttr(R.attr.ab_cancel))
                                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
							showActionModeDoneButton(false);
							if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
                            {
                                _bottomToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
                                {
                                    @Override
                                    public boolean onMenuItemClick(MenuItem menuItem)
                                    {
                                        // The only menu item is the cancel button.
                                        _actionMode.finish();
                                        return true;
                                    }
                                });
                            }
						}
						else if (_selectedTasks!=null && _selectedTasks.size()>1)
						{
							// Populate the action buttons and menus with actions that can be taken
							// on multiple tasks.
							populateActionMenuForMultipleTasks(bottomMenu,topMenu);
							
							// With mutiple tasks selected, we don't display any task details on the 
							// right or bottom:
							_a.showDetailPaneMessage(NumberFormat.getInstance().format(
								_selectedTasks.size())+" "+getString(R.string.tasks_selected));
						}
						else if (_selectedTasks==null || _selectedTasks.size()==0)
						{
							// No tasks selected.  Treat this as though multiple tasks are selected.
							// This will include the "select all" command.
							populateActionMenuForMultipleTasks(bottomMenu,topMenu);
							
							// In certain split-screen modes, we display a message on the right
							// saying the user can select a task to see it.
							_a.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
						}
						else
						{
							// Populate the action buttons and menus with actions that can be taken
							// on a single task.
							_chosenTaskID = _selectedTasks.keySet().iterator().next();
							populateActionMenuForSingleTask(bottomMenu,topMenu);

			        		// In certain split-screen modes, we also want to display the task 
			        		// on the right or bottom:
			        		if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
			        		{
			        			ViewTaskFragment frag = new ViewTaskFragment();
			            		Bundle args = new Bundle();
			            		args.putLong("_id", _chosenTaskID);
			            		frag.setArguments(args);
			            		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
			            			ViewTaskFragment.FRAG_TAG + "/" + _chosenTaskID);
			        		}
						}
						
						if (_mode==MODE_SELECTING_NEW_PARENT)
							am.setTitle(getString(R.string.Tap_on_new_parent));
						else if (_selectedTasks==null || _selectedTasks.size()==0)
						{
							am.setTitle(NumberFormat.getInstance().format(0)+" "+
				    			getString(R.string.tasks_selected));
						}
						else if (_selectedTasks.size()==1)
							am.setTitle(getString(R.string.one_task_selected));
				    	else
				    	{
				    		am.setTitle(NumberFormat.getInstance().format(_selectedTasks.size())+" "+
				    			getString(R.string.tasks_selected));
				    	}
						
						return true;
					}
					
					@Override
					public void onDestroyActionMode(ActionMode am)
					{
						_inActionMode = false;
						_a.recordActionModeEnded();
                        _a.invalidateOptionsMenu();
						handleModeChange(MODE_NORMAL);
		    			saveCurrentPosition();
		    			refreshData();
		    			restorePosition();
		    			_a.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
		    			updateBottomToolbarAppearance();
					}
					
					@Override
					public boolean onCreateActionMode(ActionMode am, Menu menu)
					{
						_inActionMode = true;
						return true;
					}
					
					@Override
					public boolean onActionItemClicked(ActionMode am, MenuItem menuItem)
					{
						if (_mode==MODE_SELECTING_NEW_PARENT)
						{
							// The only button here is the cancel button.
							am.finish();
						}
						else if (_selectedTasks!=null && _selectedTasks.size()>1)
							handleMultiSelectCommand(menuItem);
						else if (_selectedTasks==null || _selectedTasks.size()==0)
							handleMultiSelectCommand(menuItem);
						else if (_selectedTasks!=null && _selectedTasks.size()==1)
							handleSingleTaskAction(menuItem);
						return true;
					}
				});
				_a.recordActionModeStarted(_actionMode);
                if (_actionMode!=null)
                {
                    // Works around an Android Bug (https://code.google.com/p/android/issues/detail?id=159527)
                    _actionMode.invalidate();
                }
    		}
	
    		break;
    	}
    }

    /** Update the bottom toolbar appearance, based on whether we're in action mode and what the
     * theme is. */
    private void updateBottomToolbarAppearance()
    {
        if (_bottomToolbar==null || _a.getOrientation()==UtlActivity.ORIENTATION_LANDSCAPE)
            return;

        TypedValue tv = new TypedValue();
        _a.getTheme().resolveAttribute(R.attr.bottom_toolbar_uses_cradle, tv, true);
        if (tv.data==0)
        {
            // False. The bottom toolbar holds drawables and doesn't use the cradle for the
            // floating action button.
            _bottomToolbar.setBackgroundTint(null);
            if (_inActionMode)
            {
                _bottomToolbar.setBackgroundResource(Util.resourceIdFromAttr(_a,
                    R.attr.bottom_toolbar_action_mode_background));
            }
            else
            {
                _bottomToolbar.setBackgroundResource(Util.resourceIdFromAttr(_a,
                    R.attr.bottom_toolbar_background));
            }
        }
        else
        {
            if (_inActionMode)
            {
                _bottomToolbar.setBackgroundTint(Util.getColorStateList(Util.colorFromAttr(_a,
                    R.attr.bottom_toolbar_action_mode_background)));
            }
            else
            {
                _bottomToolbar.setBackgroundTint(Util.getColorStateList(Util.colorFromAttr(_a,
                    R.attr.bottom_toolbar_background)));
            }
        }
    }

    /** Show or hide the "done" button in ActionMode: */
    private void showActionModeDoneButton(boolean showIt)
    {
    	int newViewState;
    	if (showIt)
    		newViewState = View.VISIBLE;
    	else
    		newViewState = View.GONE;
    	
    	// This code code does not work on Android 2.x, and is not guaranteed to work in the future.
    	// However, Android provides no other way to hide and show the button at run-time.
    	int doneButtonId = Resources.getSystem().getIdentifier("action_mode_close_button", "id", "android");
		if (doneButtonId!=0)
		{
			if (_amDoneButton==null)
				_amDoneButton = (LinearLayout) _a.findViewById(doneButtonId);
			if (_amDoneButton!=null)
				_amDoneButton.setVisibility(newViewState);
		}
		else
		{
			doneButtonId = _res.getIdentifier("action_mode_close_button", "id", _a.getPackageName());
			if (doneButtonId!=0)
			{
				if (_amDoneButton==null)
					_amDoneButton = (LinearLayout) _a.findViewById(doneButtonId);
				if (_amDoneButton!=null)
					_amDoneButton.setVisibility(newViewState);
			}
		}
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
    
    // Highlight a row.  Inputs are the top level viewgroup for the row, along with the
    // task ID:
    private void highlightRow(ViewGroup vg, long taskID)
    {
    	if (_taskHash.containsKey(taskID))
    	{
    		UTLTaskDisplay td = _taskHash.get(taskID);
	    	vg.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(
	    		R.attr.list_highlight_bg_color)));
	    	ViewBinder binder = adapter.getViewBinder();
			binder.setViewValue(vg.findViewById(R.id.task_list_title),td, "");
			binder.setViewValue(vg.findViewById(R.id.task_list_upper_right), td, "");
            binder.setViewValue(vg.findViewById(R.id.task_list_lower_left),td, "");
			binder.setViewValue(vg.findViewById(R.id.task_list_lower_right),td, "");
    	}
    }
    
    // Unhighlight a row.  Inputs are the top level viewgroup for the row, along with the
    // task ID:
    private void unhighlightRow(final ViewGroup vg, long taskID)
    {
    	if (_taskHash.containsKey(taskID))
    	{
    		UTLTaskDisplay td = _taskHash.get(taskID);
	    	vg.setBackgroundColor(Util.colorFromAttr(_a,R.attr.main_background_color));
	    	ViewBinder binder = adapter.getViewBinder();
			binder.setViewValue(vg.findViewById(R.id.task_list_title),td, "");
			binder.setViewValue(vg.findViewById(R.id.task_list_upper_right),td, "");
			binder.setViewValue(vg.findViewById(R.id.task_list_lower_left),td, "");
			binder.setViewValue(vg.findViewById(R.id.task_list_lower_right),td, "");
    	}
    }
    
    // Unselect the last selected task:
    private void unselectLastSelected(long lastChosenTaskID)
    {
    	if (lastChosenTaskID>0)
    	{
    		ViewGroup vg = (ViewGroup)_listView.findViewWithTag(Long.valueOf(lastChosenTaskID));
    		if (vg!=null)
    		{
    			ViewGroup highlightedRow = (ViewGroup)vg.findViewById(R.id.task_list_top_layout);
    			unhighlightRow(highlightedRow,lastChosenTaskID);
    		}
    		return;
    	}
    }

    /** Handle the a drag and drop operation for manual sort.  This is called after the drag and
     * drop is complete. */
    private void handleManualSort(long droppedOnTaskID)
    {
        if (droppedOnTaskID ==_draggedTaskID)
        {
            // The user dragged the task on top of itself.  Nothing to do.
            Log.i(TAG,"Task dragged on top of itself. Cancelling manual sort.");
            return;
        }

        // Get a reference to the task that was dragged:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        UTLTask draggedTask = tasksDB.getTask(_draggedTaskID);

        // Get the index of the droppedTaskID and the task before it (if any)
        int prevTaskIndex = -1;
        UTLTask prevTask = null;
        int droppedOnTaskIndex = -1;
        UTLTask droppedOnTask = null;
        for (int i=0; i<_taskList.size(); i++)
        {
            long currTaskID = (Long)_taskList.get(i).get("task").task._id;
            if (currTaskID== droppedOnTaskID)
            {
                droppedOnTaskIndex = i;
                droppedOnTask = _taskList.get(i).get("task").task;
                if (droppedOnTaskIndex>0)
                {
                    // Now search backwards for the previous task that has the same parent.
                    int j=i-1;
                    while (j>=0)
                    {
                        UTLTask t = _taskList.get(j).get("task").task;
                        if (t.parent_id==droppedOnTask.parent_id)
                        {
                            prevTaskIndex = j;
                            prevTask = t;
                            break;
                        }
                        j--;
                    }
                }
            }
        }
        if (droppedOnTaskIndex==-1)
        {
            // Should not happen!
            Util.log("TaskListFragment: WARNING: droppedOnTaskIndex is -1. Aborting manual sort.");
            return;
        }
        Log.i(TAG,"prevTaskIndex: "+prevTaskIndex+"; droppedOnTaskIndex: "+droppedOnTaskIndex);

        // The dragged task, the dropped-on task, and the previous task must all be within the
        // same account.
        if (prevTask!=null && prevTask.account_id!=draggedTask.account_id)
        {
            Util.longerPopup(_a,_a.getString(R.string.unable_to_sort),_a.getString(R.string.
                must_be_in_same_account));
            return;
        }
        if (droppedOnTask.account_id!=draggedTask.account_id)
        {
            Util.longerPopup(_a,_a.getString(R.string.unable_to_sort),_a.getString(R.string.
                must_be_in_same_account));
            return;
        }

        // For Google, we need to make sure the sort operation doesn't end up breaking their
        // rule about not allowing sub-sub-tasks.
        UTLAccount a = (new AccountsDbAdapter()).getAccount(droppedOnTask.account_id);
        if (System.currentTimeMillis()>Util.GOOGLE_SUB_SUB_TASKS_EXPIRY &&
            droppedOnTask.parent_id>0 &&
            a.sync_service==UTLAccount.SYNC_GOOGLE)
        {
            UTLTask droppedOnParent = tasksDB.getTask(droppedOnTask.parent_id);
            if (droppedOnParent.parent_id>0)
            {
                Util.popup(_a, R.string.no_subtasks_in_subtasks2);
                return;
            }
        }

        // Now that we know there are no errors, we can record the usage of the manual sort
        // feature.
        FeatureUsage f = new FeatureUsage(_a);
        f.record(FeatureUsage.MANUAL_SORT);

        // The dragged task always has the same parent as the task it is dropped on.
        draggedTask.parent_id = droppedOnTask.parent_id;
        draggedTask.mod_date = System.currentTimeMillis();
        draggedTask.is_moved = true;
        if (prevTask!=null)
            draggedTask.prev_task_id = -1; // was prevTask._id;
        else
            draggedTask.prev_task_id = -1; // was 0

        if (prevTaskIndex==-1)
        {
            // We're inserting at the top.
            draggedTask.sort_order = Long.MAX_VALUE - (Long.MAX_VALUE-droppedOnTask.sort_order)/2;
            tasksDB.modifyTask(draggedTask);

            if (droppedOnTask.sort_order==0)
            {
                // So that the dropped on task will appear right below the dragged task, set a
                // value for the dropped on task.
                droppedOnTask.sort_order = Long.MAX_VALUE/4;
                droppedOnTask.mod_date = System.currentTimeMillis();
                droppedOnTask.is_moved = true;
                droppedOnTask.prev_task_id = -1;  // was draggedTask._id;
                tasksDB.modifyTask(droppedOnTask);
            }

            Log.i(TAG,"Insertion at the top: dragged task sort order: "+draggedTask.sort_order+
                "; dropped on task sort order: "+droppedOnTask.sort_order);

            // Refresh the screen.
            finishManualSortOp();
            return;
        }

        if (prevTask.sort_order>0 && droppedOnTask.sort_order>0 && prevTask.sort_order>droppedOnTask.
            sort_order)
        {
            // A simple scenario.  No zero values and no equal values.
            draggedTask.sort_order = prevTask.sort_order - (prevTask.sort_order-droppedOnTask.
                sort_order)/2;
            tasksDB.modifyTask(draggedTask);

            droppedOnTask.mod_date = System.currentTimeMillis();
            droppedOnTask.is_moved = true;
            droppedOnTask.prev_task_id = -1; // was draggedTask._id;
            tasksDB.modifyTask(droppedOnTask);

            Log.i(TAG,"Simple sort: dragged task sort order: "+draggedTask.sort_order+
                "; dropped on task sort order: "+droppedOnTask.sort_order+
                "; previous task sort order: "+prevTask.sort_order);

            // Save the changes and refresh.
            finishManualSortOp();
            return;
        }

        if (droppedOnTask.sort_order==0)
        {
            // This occurs when performing manual sort on tasks that have not yet been sorted.
            long priorNonZeroSortOrder = findNonMatchingValue(prevTaskIndex,-1,0,droppedOnTask.
                parent_id,_draggedTaskID);
            draggedTask.sort_order = priorNonZeroSortOrder/2;
            tasksDB.modifyTask(draggedTask);
            int i = prevTaskIndex;
            while (i>=0)
            {
                UTLTask t = _taskList.get(i).get("task").task;
                if (t.parent_id==droppedOnTask.parent_id && t._id!=draggedTask._id)
                {
                    if (t.sort_order == priorNonZeroSortOrder)
                        break;
                    else
                    {
                        t.sort_order = draggedTask.sort_order + priorNonZeroSortOrder / 4;
                        t.mod_date = System.currentTimeMillis();
                        t.is_moved = true;
                        t.prev_task_id = -1;
                        tasksDB.modifyTask(t);
                    }
                }
                i--;
            }
            droppedOnTask.sort_order = priorNonZeroSortOrder/4;
            droppedOnTask.mod_date = System.currentTimeMillis();
            droppedOnTask.is_moved = true;
            droppedOnTask.prev_task_id = -1; // was draggedTask._id;
            tasksDB.modifyTask(droppedOnTask);

            Log.i(TAG,"Dropped on task not previously sorted: dragged task sort order: "+
                draggedTask.sort_order+
                "; dropped on task sort order: "+droppedOnTask.sort_order+
                "; previous task sort order: "+prevTask.sort_order+
                "; priorNonZeroSortOrder: "+priorNonZeroSortOrder);

            // Refresh the screen:
            finishManualSortOp();
            return;
        }

        if (prevTask.sort_order==droppedOnTask.sort_order)
        {
            // The previous and next tasks have equal non-zero sort_order values, so they will need
            // to be adjusted.

            // Find the previous and next values of sort_order that don't match the current
            // value.
            long highValue = findNonMatchingValue(prevTaskIndex,-1,prevTask.sort_order,
                droppedOnTask.parent_id,_draggedTaskID);
            long lowValue = findNonMatchingValue(droppedOnTaskIndex,1,droppedOnTask.sort_order,
                droppedOnTask.parent_id,_draggedTaskID);

            // Update the sort order values:
            draggedTask.sort_order = prevTask.sort_order;
            tasksDB.modifyTask(draggedTask);
            int i = prevTaskIndex;
            while (i>=0)
            {
                UTLTask t = _taskList.get(i).get("task").task;
                if (t.parent_id==droppedOnTask.parent_id && t._id!=draggedTask._id)
                {
                    if (t.sort_order == highValue)
                        break;
                    else
                    {
                        t.sort_order = draggedTask.sort_order + (highValue - draggedTask.sort_order) / 2;
                        t.mod_date = System.currentTimeMillis();
                        t.is_moved = true;
                        t.prev_task_id = -1;
                        tasksDB.modifyTask(t);
                    }
                }
                i--;
            }
            i = droppedOnTaskIndex;
            while (i<_taskList.size())
            {
                UTLTask t = _taskList.get(i).get("task").task;
                if (t.parent_id==droppedOnTask.parent_id && t._id!=draggedTask._id)
                {
                    if (t.sort_order == lowValue)
                        break;
                    else
                    {
                        t.sort_order = draggedTask.sort_order - (draggedTask.sort_order - lowValue) / 2;
                        t.mod_date = System.currentTimeMillis();
                        t.is_moved = true;
                        if (i==droppedOnTaskIndex)
                            t.prev_task_id = -1; // was draggedTask._id;
                        else
                            t.prev_task_id = -1;
                        tasksDB.modifyTask(t);
                    }
                }
                i++;
            }

            Log.i(TAG,"Equal non-zero sort orders: dragged task sort order: "+
                draggedTask.sort_order+
                "; dropped on task sort order: "+droppedOnTask.sort_order+
                "; previous task sort order: "+prevTask.sort_order+
                "; highValue: "+highValue+"; lowValue: "+lowValue);

            // Refresh the screen:
            finishManualSortOp();
            return;
        }

        // We should not get here.
        Util.log("TaskListFragment: WARNING: No changes could be made after a manual sort "+
            "operation: "+prevTask.sort_order+", "+droppedOnTask.sort_order);
    }

    /** Search the task list for a sort order value that does not match the input.
     * @param startIndex - The index to start searching
     * @param direction - The direction to search. -1 for up, 1 for down.
     * @param value - The value to compare against.
     * @param parentID - The parent ID to use.  Tasks that don't have this parent are skipped.
     * @param draggedTaskID - The ID of the task that was dragged. This is skipped when searching.
     * @return The first value that doesn't match the 'value' input, or Long.MAX_VALUE or zero. */
    private Long findNonMatchingValue(int startIndex, int direction, long value, long parentID,
        long draggedTaskID)
    {
        int i = startIndex;
        while (i>=0 && i<_taskList.size())
        {
            if (_taskList.get(i).get("task").task.sort_order!=value &&
                _taskList.get(i).get("task").task.parent_id==parentID &&
                _taskList.get(i).get("task").task._id!=draggedTaskID)
            {
                return _taskList.get(i).get("task").task.sort_order;
            }
            i+=direction;
        }
        if (direction==-1)
            return Long.MAX_VALUE;
        else
            return 0L;
    }

    /** Perform an instant upload (if enabled) of all tasks that have been moved. Call this after
     * a manual sort operation has been completed. */
    private void uploadMovedTasks()
    {
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = tasksDB.queryTasks("is_moved=1", "sort_order desc");
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);
            Util.instantTaskUpload(_a, t);
        }
        c.close();
    }

    /** Final operations to perform after finishing a successful manual sort operation. */
    private void finishManualSortOp()
    {
        // If long-presses are used to enter manual sort mode, then leave manual sort mode now.
        // The user can sort again through another long press.
        if (_settings.getBoolean(PrefNames.MANUAL_SORT_ON_LONG_PRESS,false) &&
            _manualSortFromLongPress)
        {
            handleModeChange(MODE_NORMAL);
            _manualSortFromLongPress = false;
        }
        else
        {
            saveCurrentPosition();
            refreshData();
            restorePosition();
        }
    }

    // Refresh the other panes, such as the nav drawer and task viewer:
    private void refreshOtherPanes()
    {
    	_a.refreshNavDrawerCounts();
    	
    	if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
    	{
    		Fragment detailFrag = _a.getFragmentByType(UtlNavDrawerActivity.FRAG_DETAILS);
    		if (detailFrag!=null && detailFrag instanceof ViewTaskFragment)
    		{
	    		ViewTaskFragment vtFrag = (ViewTaskFragment)detailFrag;
	    		vtFrag.refreshDisplay();
    		}
    	}
    }
    
    // Handle a task deletion that occurs from outside this fragment:
    public void handleDeletion()
    {
    	if (_mode!=MODE_NORMAL)
    		handleModeChange(MODE_NORMAL);
    	else
    		refreshData();
    	
    	NavDrawerFragment navFragment = (NavDrawerFragment)_a.getFragmentByTag(
    		UtlNavDrawerActivity.NAV_DRAWER_TAG);
    	if (navFragment!=null)
    		navFragment.refreshCounts();
    	
    	// If the task is being displayed in a sliding drawer, then close the drawer:
    	if (_ssMode==Util.SS_2_PANE_NAV_LIST && _a.getOrientation()==UtlActivity.ORIENTATION_LANDSCAPE)
    	{
    		_a.closeDrawer();
    	}
    	
    	_a.showDetailPaneMessage(_a.getString(R.string.Select_a_task_to_display));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
      	_pm.unlinkFromBillingService();
    }
}
