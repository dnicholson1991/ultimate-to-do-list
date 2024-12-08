package com.customsolutions.android.utl;

// This fragment displays a list of notes.

// The bundle in the Intent (or fragment options) must contain ONE the following:
// folder_id: The ID of the folder we are displaying notes for.  This can be zero to
//     display notes with no folder, or -1 to show all notes.
// sql: an SQL where clause to get the notes list.

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.google.android.gms.ads.formats.UnifiedNativeAd;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class NoteListFragment extends UtlListFragment
{
	private static final String TAG = "NoteListFragment";

	// IDs for menu items that appear when no tasks are selected:
	private static final int RESIZE_PANES_ID = Menu.FIRST+1;
	
	// IDs for action mode menu items - that appear when selecting one or more notes:
	private static final int VIEW_NOTE_ID = Menu.FIRST+2;
	private static final int EDIT_NOTE_ID = Menu.FIRST+3;
	private static final int RENAME_ID = Menu.FIRST+4;
	private static final int CHANGE_FOLDER_ID = Menu.FIRST+5;
	private static final int DELETE_ID = Menu.FIRST+6;
	private static final int SELECT_ALL_ID = Menu.FIRST+7;
	
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "NoteListFragment";
    
    // Available modes this fragment can be in:
    public static final int MODE_NORMAL = 1;
    public static final int MODE_MULTI_SELECT = 3;

	/** The index in the list of the note that precedes the first native ad. */
	public static final int FIRST_NATIVE_AD_INDEX = 5;

	/** The number of notes in between native ads, after the first ad. */
	public static final int NATIVE_AD_INTERVAL = 11;

	private NotesDbAdapter _notesDB;
	private Cursor _c;
	private long _chosenNoteID = 0;
	private EditText _renameInput;
	public long _folderID;
	private FoldersDbAdapter _foldersDB;
	public boolean _usingSQL;
	public String _sqlQuery;
	
	// Variables for communicating with the Synchronizer service:
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    // State variable.  If true, we have initiated a manual sync and are waiting on a response:
    private boolean waitingOnManualSync;
    
    // Stores a list of IDs temporarily:
    ArrayList<Long> _idList;
    
    // The index of the last selected item:
    private int _lastSelection = -1;
    
    // Links to key views:
    LinearLayout _syncProgressContainer;
    ProgressBar _syncProgressBar;
    private ListView _listView;
    private SwipeRefreshLayout _swipeRefreshLayout;
	private BottomAppBar _bottomToolbar;

	private SharedPreferences _settings;
    private UtlNavDrawerActivity _a;
    private Resources _res;
    
    // Quick reference to this Fragment's root view:
    private ViewGroup _rootView;
    
    /** The current mode: */
    public int _mode;
    
    /** Quick reference to the current split-screen mode: */
    private int _ssMode;
    
    /** This holds the IDs all selected notes in multi-select mode: */
    private HashSet<Long> _selectedNotes;
    
    // These keep track of action mode / multi-select information:
    private ActionMode _actionMode;
    private boolean _inActionMode = false;
    
    private OnLongClickListener _longClickListener;
    
    public boolean _isFirstRefresh;

	/** A unique ID, used to identify this fragment to the native ad utilities. */
	private long _nativeAdID;

	// Used for in-app billing and licensing status:
	private PurchaseManager _pm;

	/** Keeos track of ads that have been loaded and displayed. */
	private HashMap<Long, UnifiedNativeAd> _displayedAds;

	/** The Floating action button. */
	private FloatingActionButton _fab;

	/** The floating action button for landscape mode. */
	private FloatingActionButton _fab2;

	/** This returns the view being used by this fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.note_list, container, false);
    }
    
    // Called when activity is first created:
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);

		_notesDB = new NotesDbAdapter();
        _foldersDB = new FoldersDbAdapter();
        _a = (UtlNavDrawerActivity)getActivity();
        _settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
        _rootView = (ViewGroup)getView();
        _res = _a.getResources();
        _selectedNotes = new HashSet<Long>();
        _isFirstRefresh = true;
        _ssMode = _a.getSplitScreenOption();
		_pm = new PurchaseManager(_a);
		_displayedAds = new HashMap<>();
		_nativeAdID = System.currentTimeMillis();
        
        // Verify required input is received.  Check fragment arguments first, followed by the Intent
        // for the parent activity.
        Bundle b = getArguments();
        if (b==null)
        	b = _a.getIntent().getExtras();
        if (b==null)
        {
        	Util.log("null Bundle passed into NoteList.java.");
        	_a.finish();
        	return;
        }
        if (b.containsKey("folder_id"))
        {
        	_usingSQL = false;
        	_folderID = b.getLong("folder_id");
        }
        else if (b.containsKey("sql"))
        {
        	_usingSQL = true;
        	_sqlQuery = b.getString("sql");
        }
        else
        {
        	Util.log("No folder_id or sql passed into NoteList.java.");
        	_a.finish();
        	return;
        }
        
        // Set the title:
        if (_usingSQL)
        {
        	// We're searching notes.
        	_a.getSupportActionBar().setTitle(R.string.Note_Search_Results);
        }
        else if (!_settings.getBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true))
        {
        	// Note folders are disabled, so we don't need to display a folder name at
        	// the top:
        	_a.getSupportActionBar().setTitle(R.string.All_Notes);
        }
        else
        {
        	if (_folderID==0)
        	{
        		// Displaying notes with no folder:
        		_a.getSupportActionBar().setTitle(R.string.Notes_With_No_Folder);
        	}
        	else if (_folderID==-1)
        	{
        		// Showing all notes.
        		_a.getSupportActionBar().setTitle(R.string.All_Notes);
        	}
        	else
        	{
        		Cursor c = _foldersDB.getFolder(_folderID);
        		if (c.moveToFirst())
        		{
        			_a.getSupportActionBar().setTitle(Util.getString(R.string.Notes_in_Folder)+" \""+
        				Util.cString(c, "title")+"\"");
        		}
        		else
        		{
        			_a.getSupportActionBar().setTitle(R.string.Notes);
        		}
        		c.close();
        	}
        }
        
    	// Link to key views:
    	_syncProgressContainer = (LinearLayout)_rootView.findViewById(R.id.sync_status_progress_bar_container);
    	_syncProgressBar = (ProgressBar)_rootView.findViewById(R.id.sync_status_progress_bar);
        _listView = (ListView)getView().findViewById(android.R.id.list);
        _swipeRefreshLayout = (SwipeRefreshLayout)getView().findViewById(R.id.note_list_swipe_refresh);
		_bottomToolbar = (BottomAppBar) getView().findViewById(R.id.note_list_bottom_toolbar);

        Util.pingServer(_a);

        // We start off in normal mode (not selecting a task):
        if (!_inActionMode)
        {
        	handleModeChange(MODE_NORMAL);
        }
        
        // Check to see if the screen was rotated while in multi-select mode:
        if (savedInstanceState!=null && savedInstanceState.containsKey("mode") &&
        	savedInstanceState.containsKey("selected_notes"))
        {
        	int mode = savedInstanceState.getInt("mode");
        	
        	// If a single tap on a note would launch a new Activity, then we don't highlight/select
        	// any notes now.
        	if (mode==MODE_MULTI_SELECT || !_a.useNewActivityForDetails())
        	{
        		// Populate the list of selected notes:
        		long[] noteIDs = savedInstanceState.getLongArray("selected_notes");
        		if (noteIDs!=null && noteIDs.length>0)
        		{
        			for (int i=0; i<noteIDs.length; i++)
        				_selectedNotes.add(noteIDs[i]);
        		}
	    		
	    		if (_selectedNotes.size()==1)
	    		{
	    			_chosenNoteID = _selectedNotes.iterator().next();
	    		}
	    		
	        	if (mode==MODE_MULTI_SELECT)
	        	{
	        		handleModeChange(mode);
	        		_inActionMode = true;
	        	}
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

		// Add a footer view to the list. This ensures the + sign at the bottom doesn't hide
		// crucial task details.
		_a.addListViewFooter(_listView,_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT);

        // This fragment will be updating the Action Bar:
        setHasOptionsMenu(true);
                
        if (savedInstanceState != null)
        {
	        if (savedInstanceState.containsKey("waiting_on_manual_sync"))
	    		waitingOnManualSync = savedInstanceState.getBoolean("waiting_on_manual_sync");
	        if (savedInstanceState.containsKey("chosen_note_id"))
	        	_chosenNoteID = savedInstanceState.getLong("chosen_note_id");
        }

        // A scroll handler for the listview, which enables the swipe refresh control only when needed:
        _listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

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

        // This long-click handler is called when a long-press is performed on a note:
        _longClickListener = new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v)
			{
		        // Do nothing if we're in resize mode:
		        if (_a.inResizeMode())
		        	return true;
		        
		        long oldChosenNoteID = _chosenNoteID;
		        _chosenNoteID = (Long)v.getTag();
		        
		        if (_mode==MODE_MULTI_SELECT)
		        {
		        	// Since we're already in multi-select mode, treat this as a normal tap:
		        	NoteListFragment.this.handleNoteClick(v);
		        }
		        else
		        {
		        	// When moving from normal mode to multi-select, make sure the selected notes
		        	// are cleared.
		        	if (_selectedNotes==null)
		        		_selectedNotes = new HashSet<Long>();
		        	else
		        		_selectedNotes.clear();
		        	
		        	// Unhighlight any currently-selected note.
		        	if (oldChosenNoteID>0)
		        		setHighlighting(oldChosenNoteID,false);
		        	
		        	// Switch to multi-select mode, then process this as a regular tap:
		        	_selectedNotes.add(_chosenNoteID);
		        	handleModeChange(MODE_MULTI_SELECT);
		        	setHighlighting(_chosenNoteID,true);
		        }
		        
		        return true;
			}
        };

        // Handle a tap on the add button:
		_fab = _rootView.findViewById(R.id.note_list_fab);
		_fab.setOnClickListener((View v) -> {
			startAddingNote();
		});
		_fab2 = _rootView.findViewById(R.id.note_list_fab2);
		_fab2.setOnClickListener((View v) -> {
			startAddingNote();
		});
    }
    
    // If we return here after leaving, we must refresh the data:
    @Override
    public void onResume()
    {
        super.onResume();
        refreshData();
        
        restorePosition();
        
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
        if (_lastSelection>-1 && _lastSelection<lv.getCount())
        {
        	lv.setSelection(_lastSelection);
        }
        
        // Establish a link to the Synchronizer service:
        doBindService();
        
        if (Synchronizer.isSyncing() && waitingOnManualSync)
        	_syncProgressContainer.setVisibility(View.VISIBLE);
        else
        {
        	waitingOnManualSync = false;
        	_syncProgressContainer.setVisibility(View.GONE);
        }
        
        // Run a sync if needed:
        Util.doMinimalSync(_a);
    }

    /** Get a Cursor into the database for the notes to list here. */
    private Cursor getCursor()
	{
		Cursor c;
		if (!_usingSQL)
		{
			if (_folderID>=0)
				c = _notesDB.getNotesInFolder(_folderID);
			else
				c = _notesDB.queryNotes("folder_id>=0", "lower(title)");  // Gets all notes.
		}
		else
			c = _notesDB.queryNotes(_sqlQuery, "lower(title)");
		c.moveToPosition(-1);
		return c;
	}

    public void refreshData()
    {
    	// Query the database to get the notes:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	_c = getCursor();
    	
    	// This array lists the columns we're interested in:
    	String from[] = new String[] {"_id"};
    	
    	// The IDs of views that are affected by the columns:
    	int to[] = new int[] {R.id.note_row_container};
    	
    	// Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(_a, R.layout.note_list_row,
        	_c, from, to);

		// Show the banner ad if the number of tasks is below a threshold or if there are no
		// native ads ready to display.
		if (_pm.stat()==PurchaseManager.SHOW_ADS)
		{
			initBannerAd(_rootView);
			_bannerAd.setIsAtTop(true);
		}
		else
			hideBannerAd();

        // This function binds data in the Cursor to the Views we're displaying:
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
        {
        	@Override
            public boolean setViewValue(View view, Cursor c, int columnIndex)
            {
        		// Get references to the views we need to work with:
        		ViewGroup row = (ViewGroup)view;
        		ViewGroup main = row.findViewById(R.id.note_list_row_main);
        		TextView name = (TextView)view.findViewById(R.id.note_row_title);
        		TextView body = (TextView)view.findViewById(R.id.note_row_note_body);
        		ViewGroup adContainer = view.findViewById(R.id.note_ad_container);
        		ViewGroup adPlaceholder = view.findViewById(R.id.note_native_ad_placeholder);
        		
        		// Store the note ID as a tag:
        		row.setTag(Long.valueOf(Util.cLong(c, "_id")));
        		main.setTag(Long.valueOf(Util.cLong(c, "_id")));
        		
        		// Display the name and set the font size if needed:
       			name.setText(Util.cString(c,"title"));
       			
       			// Display the first line of the note contents:
       			body.setText(Util.cString(c, "note"));
       			
       			// Adjust background and colors, depending on whether the note is selected:
       			if (_selectedNotes.contains(Util.cLong(c,"_id")) || _chosenNoteID==Util.cLong(c,"_id"))
       				setHighlighting(main,true);
       			else
       				setHighlighting(main,false);
       			
       			// Set up a click handler for the row:
        		main.setOnClickListener(new View.OnClickListener()
				{
        			@Override
					public void onClick(View v)
					{
        				handleNoteClick(v);
					}
				});
        		
        		// Set up the long-click listener:
        		main.setOnLongClickListener(_longClickListener);

        		// 9/30/23: Native ad code is removed due to lack of use adn poor revenue.
				adPlaceholder.removeAllViews();
				adContainer.setVisibility(View.GONE);

        		return true;
            }
        });
        
        setListAdapter(adapter);
        _displayedAds.clear();
        
        // If nothing is selected, display a message to the user in the detail pane (if we're
        // in a compatible split-screen mode).  We only due this on the first refresh since the user
        // may be doing something like adding a new task.
        if (_a.getSplitScreenOption()!=Util.SS_NONE && (_selectedNotes==null ||
        	_selectedNotes.size()==0) && _isFirstRefresh && _chosenNoteID<1)
        {
        	_a.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
        }
        _isFirstRefresh = false;
    }
    
    // Populate the options menu when it is invoked.
    @Override
    public void onCreateOptionsMenu(Menu topMenu, MenuInflater inflater)
    {
		if (_a.getOrientation()==UtlActivity.ORIENTATION_LANDSCAPE)
		{
			// In landscape mode, we use the top action bar for the menu, regardless of whether
			// we're in split-screen mode.
			topMenu.clear();
			inflater.inflate(R.menu.note_list, topMenu);

			if (!SpeechRecognizer.isRecognitionAvailable(_a))
			{
				// No speech recognition services.  Need to hide the menu item.
				topMenu.removeItem(R.id.menu_task_list_voice_mode);
			}

			// There's always room for the menu item at index 5 in landscape view:
			if (topMenu.size()>=5)
				topMenu.getItem(4).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
			if (topMenu.size()>=6)
				topMenu.getItem(5).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

			// If we're using split-screen views, then add in an option to resize the panes:
			if (_ssMode != Util.SS_NONE)
			{
				topMenu.add(0, RESIZE_PANES_ID, 0, R.string.Resize_Panes)
					.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
			}

			// Make sure the bottom toolbar is invisible:
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
			inflater.inflate(R.menu.note_list, topMenu);

			if (!SpeechRecognizer.isRecognitionAvailable(_a))
			{
				// No speech recognition services.  Need to hide the menu item.
				topMenu.removeItem(R.id.menu_task_list_voice_mode);
			}

			// If we're using split-screen views, then add in an option to resize the panes:
			if (_ssMode != Util.SS_NONE)
			{
				topMenu.add(0, RESIZE_PANES_ID, 0, R.string.Resize_Panes)
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

			// Taps on menu items will be handled just like the top action bar:
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
    
    // Handlers for options menu choices:
    @SuppressLint("InlinedApi")
	@Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        Intent i;
        
        switch(item.getItemId()) 
        {
		case R.id.menu_note_list_view_refresh:
			refreshData();
			return true;

        case R.id.menu_note_list_sync_now:
        	startSync();
          	return true;
        	
        case R.id.menu_task_list_voice_mode:
        	i = new Intent(_a,VoiceCommand.class);
            if (Build.VERSION.SDK_INT>=11)
            	i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        	_a.startActivity(i);
        	return true;
          	
        case R.id.menu_note_list_start_multi_select:
        	if (_mode==MODE_NORMAL)
        	{
        		if (_selectedNotes==null)
        			_selectedNotes = new HashSet<Long>();
        		else
        			_selectedNotes.clear();
        		Util.popup(_a, R.string.Multi_Select_On);
        		handleModeChange(MODE_MULTI_SELECT);
        	}
        	return true;
        	
        case R.id.menu_note_list_search:
        	i = new Intent(_a,QuickSearch.class);
        	_a.startActivity(i);
        	return true;
        	
        case RESIZE_PANES_ID:
        	_a.enterResizeMode();
        	return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    /** Handler for the button to add a note. */
    public void startAddingNote()
    {
    	// Can't add a note in mult-select mode.
    	if (_mode==MODE_MULTI_SELECT)
    		handleModeChange(MODE_NORMAL);

		if (_a.useNewActivityForDetails())
    	{
    		// No split-screen in use.  Just start a new activity:
			Intent i = new Intent(_a,EditNoteActivity.class);
			i.putExtra("action", EditNoteFragment.ADD);
			if (!_usingSQL)
			{
        		i.putExtra("default_folder_id", _folderID);
        		Cursor c = _foldersDB.getFolder(_folderID);
        		if (c.moveToFirst())
        			i.putExtra("default_account_id", Util.cLong(c, "account_id"));
        		c.close();
			}
    		this.startActivity(i);
    	}
		else
		{
			// Display the note editor in a separate fragment:
			EditNoteFragment frag = new EditNoteFragment();
    		Bundle args = new Bundle();
    		args.putInt("action", EditNoteFragment.ADD);
    		if (!_usingSQL)
			{
    			args.putLong("default_folder_id", _folderID);
    			Cursor c = _foldersDB.getFolder(_folderID);
        		if (c.moveToFirst())
        			args.putLong("default_account_id", Util.cLong(c, "account_id"));
        		c.close();
			}
    		frag.setArguments(args);
    		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditNoteFragment.FRAG_TAG+
				"/add/"+System.currentTimeMillis());
		}
    }
    
    /** Handle a click/tap on a note.  The input is the topmost view of the row. */
    private void handleNoteClick(View v)
    {
		long noteID = (Long)v.getTag();
		UTLNote note = _notesDB.getNote(noteID);
		if (note==null)
		{
			Util.popup(_a, R.string.Item_no_longer_exists);
			return;
		}
		
		// Do nothing if we're in resize mode:
        if (_a.inResizeMode())
        	return;
        
        // Keep track of the last note that was tapped on:
        long lastChosenNoteID = _chosenNoteID;
        _chosenNoteID = noteID;
        
        if (_mode==MODE_MULTI_SELECT)
        {
        	if (_selectedNotes==null || !_selectedNotes.contains(noteID))
        	{
        		_selectedNotes.add(noteID);
        		
        		// Highlight the note's row:
        		setHighlighting((ViewGroup)v,true);
        	}
        	else
        	{
        		// Unselect the note and unhighlight its row:
        		_selectedNotes.remove(noteID);
        		setHighlighting((ViewGroup)v,false);
        	}
        	
        	// With every note selection or unselection, the action buttons and menus may change.
        	_actionMode.invalidate();
        }
        else
        {
    		if (_settings.getBoolean(PrefNames.OPEN_NOTE_EDITOR_ON_TAP, false))
    		{
    			if (_a.useNewActivityForDetails())
            	{
            		// No split-screen in use.  Just start a new activity:
    				Intent i = new Intent(_a,EditNoteActivity.class);
    				i.putExtra("action", EditNoteFragment.EDIT);
            		i.putExtra("id", _chosenNoteID);
            		this.startActivity(i);
            		_chosenNoteID = 0;
            	}
    			else
    			{
    				// Highlight the note that was just selected.
    				setHighlighting((ViewGroup)v,true);
    				
    				// Unhighlight the last note that was highlighted.
    				if (lastChosenNoteID!=_chosenNoteID && lastChosenNoteID>0)
    					setHighlighting(lastChosenNoteID,false);
    				
    				// Display the note editor in a separate fragment:
    				EditNoteFragment frag = new EditNoteFragment();
            		Bundle args = new Bundle();
            		args.putInt("action", EditNoteFragment.EDIT);
            		args.putLong("id", _chosenNoteID);
            		frag.setArguments(args);
            		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditNoteFragment.FRAG_TAG + 
            			"/" + _chosenNoteID);
    			}
    		}
    		else
    		{
    			if (_a.useNewActivityForDetails())
            	{
            		// No split-screen in use.  Just start a new activity:
    				Intent i = new Intent(_a,ViewNote.class);
            		i.putExtra("id", _chosenNoteID);
            		this.startActivity(i);
            		_chosenNoteID = 0;
            	}
    			else
    			{
    				// Highlight the note that was just selected.
    				setHighlighting((ViewGroup)v,true);
    				
    				// Unhighlight the last note that was highlighted.
    				if (lastChosenNoteID!=_chosenNoteID && lastChosenNoteID>0)
    					setHighlighting(lastChosenNoteID,false);
    				
    				// Display the note viewer in a separate fragment:
    				ViewNoteFragment frag = new ViewNoteFragment();
            		Bundle args = new Bundle();
            		args.putLong("id", _chosenNoteID);
            		frag.setArguments(args);
            		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewNoteFragment.FRAG_TAG + 
            			"/" + _chosenNoteID);
    			}
    		}
        }
    }
    
    /** Update the display for a mode change. */
    public void handleModeChange(int newMode)
    {
    	int oldMode = _mode;
    	_mode = newMode;
    	
    	switch (newMode)
    	{
    	case MODE_NORMAL:
    		if (_selectedNotes!=null)
    			_selectedNotes.clear();
    		if (_inActionMode)
			{
				_actionMode.finish();
				_a.invalidateOptionsMenu();
			}
    		_chosenNoteID = 0;
    		break;
    		
    	case MODE_MULTI_SELECT:
    		if (_selectedNotes!=null && _selectedNotes.size()==1 && _chosenNoteID==0)
    		{
    			// If only one note is selected, we should make sure that _chosenNoteID is set to
    			// that note.  The code to selected the appropriate menu items to display uses this.
    			_chosenNoteID = _selectedNotes.iterator().next();
    		}
    		
    		if (newMode!=oldMode)
    		{
    			// Start action mode, with a special action bar at the top.
    			_actionMode = _a.startSupportActionMode(new ActionMode.Callback()
				{
					@Override
					public boolean onPrepareActionMode(ActionMode am, Menu topMenu)
					{
						Menu bottomMenu = null;
						if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
						{
							// In portrait mode, the affected menu is the bottom toolbar.
							bottomMenu = _bottomToolbar.getMenu();
						}
						_inActionMode = true;
						updateBottomToolbarAppearance();
						if (_selectedNotes!=null && _selectedNotes.size()>1)
						{
							// Populate the action buttons and menus with actions that can be taken
							// on multiple notes.
							populateActionMenuForMultipleNotes(bottomMenu,topMenu);
							
							// With multiple notes selected, we can't display any note details on
							// the right or bottom:
							_a.showDetailPaneMessage(NumberFormat.getInstance().format(
								_selectedNotes.size())+" "+getString(R.string.notes_selected));
							
							am.setTitle(NumberFormat.getInstance().format(_selectedNotes.size())+" "+
				    			getString(R.string.notes_selected));
						}
						else if (_selectedNotes==null || _selectedNotes.size()==0)
						{
							// No notes selected.  Treat this as though multiple notes are selected.
							// A "select all" command will be visible.
							populateActionMenuForMultipleNotes(bottomMenu,topMenu);
							
							// In certain split-screen modes, we display a message on the right
							// saying the user can select a task to see it.
							_a.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
							
							am.setTitle(NumberFormat.getInstance().format(0)+" "+
				    			getString(R.string.notes_selected));
						}
						else
						{
							// Populate the action buttons and menu with actions that can be taken
							// on a single note.
							_chosenNoteID = _selectedNotes.iterator().next();
							populateActionMenuForSingleNote(bottomMenu,topMenu);
							
							am.setTitle(getString(R.string.one_note_selected));
							
							// In certain split-screen modes, we also want to display the note 
			        		// on the right or bottom:
			        		if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
			        		{
			        			ViewNoteFragment frag = new ViewNoteFragment();
			            		Bundle args = new Bundle();
			            		args.putLong("id", _chosenNoteID);
			            		frag.setArguments(args);
			            		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewNoteFragment.FRAG_TAG + 
			            			"/" + _chosenNoteID);
			        		}
						}
						
						return true;
					}
					
					@Override
					public void onDestroyActionMode(ActionMode arg0)
					{
						_inActionMode = false;
						_a.recordActionModeEnded();
						_a.invalidateOptionsMenu();
						handleModeChange(MODE_NORMAL);
		    			saveCurrentPosition();
		    			refreshData();
		    			restorePosition();
		    			_a.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
						updateBottomToolbarAppearance();
					}
					
					@Override
					public boolean onCreateActionMode(ActionMode arg0, Menu arg1)
					{
						_inActionMode = true;
						return true;
					}
					
					@Override
					public boolean onActionItemClicked(ActionMode arg0, MenuItem menuItem)
					{
						if (_selectedNotes!=null && _selectedNotes.size()>1)
							handleMultiSelectCommand(menuItem);
						else if (_selectedNotes==null || _selectedNotes.size()==0)
							handleMultiSelectCommand(menuItem);
						else if (_selectedNotes!=null && _selectedNotes.size()==1)
							handleSingleNoteAction(menuItem);
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

    /** Populate the action menu for a single note. The bottomMenu argument is ignored if we're
	 * in landscape mode. */
    private void populateActionMenuForSingleNote(Menu bottomMenu, Menu topMenu)
    {
		if (bottomMenu!=null)
			bottomMenu.clear();
		topMenu.clear();
    	
    	// Some items will not be shown in the contextual action bar because they will already be 
        // visible in the right or bottom pane.
        boolean viewerShowing = false;
        if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
        {
       		viewerShowing = true;
        }

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

        // Add items to the menu:

		// Select All:
        MenuItemCompat.setShowAsAction(mainMenu.add(0,SELECT_ALL_ID,0,R.string.Select_All).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_select_all))
    		,MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        if (!viewerShowing)
        {
        	// View or edit:
        	if (_settings.getBoolean(PrefNames.OPEN_NOTE_EDITOR_ON_TAP, false))
        	{
	        	MenuItemCompat.setShowAsAction(mainMenu.add(0,VIEW_NOTE_ID,0,R.string.View_Details).
					setIcon(_a.resourceIdFromAttr(R.attr.ab_view)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        	}
        	else
        	{
	        	MenuItemCompat.setShowAsAction(mainMenu.add(0,EDIT_NOTE_ID,0,R.string.Edit_Note).
					setIcon(_a.resourceIdFromAttr(R.attr.ab_edit)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        	}

        	// Delete:
            MenuItemCompat.setShowAsAction(mainMenu.add(0,DELETE_ID,0,R.string.Delete).
    			setIcon(_a.resourceIdFromAttr(R.attr.ab_delete)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }

        // Change folder. Always in overflow since we don't have an icon.
        MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_FOLDER_ID,0,R.string.Change_Folder),
        	MenuItemCompat.SHOW_AS_ACTION_NEVER);

        // Rename. Always in overflow since we don't have an icon.
        MenuItemCompat.setShowAsAction(overflowMenu.add(0,RENAME_ID,0,R.string.Rename),MenuItemCompat.
        	SHOW_AS_ACTION_NEVER);

		if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
		{
			// In portrait mode, the items are on the bottom toolbar. The toolbar's click
			// handler needs adjusted.
			_bottomToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener()
			{
				@Override
				public boolean onMenuItemClick(MenuItem menuItem)
				{
					return handleSingleNoteAction(menuItem);
				}
			});
		}
    }
    
    /** Handle an action for a single note. */
    private boolean handleSingleNoteAction(MenuItem item)
    {
    	DialogInterface.OnClickListener dialogClickListener;
    	AlertDialog.Builder builder;
    	
    	if (item.getItemId()==SELECT_ALL_ID)
        {
        	// The select all command can be handle by the multi-select code:
        	handleMultiSelectCommand(SELECT_ALL_ID);
        	return(true);
        }
    	
    	// Get the note object we're looking at:
    	UTLNote note = _notesDB.getNote(_chosenNoteID);
    	if (note==null)
    	{
    		Util.popup(_a, R.string.Item_no_longer_exists);
    		return true;
    	}
    	
    	switch (item.getItemId())
    	{
    	case EDIT_NOTE_ID:
			if (_a.useNewActivityForDetails())
        	{
        		// No split-screen in use.  Just start a new activity:
				Intent i = new Intent(_a,EditNoteActivity.class);
				i.putExtra("action", EditNoteFragment.EDIT);
        		i.putExtra("id", _chosenNoteID);
        		this.startActivity(i);
        	}
			else
			{
				// Display the note editor in a separate fragment:
				EditNoteFragment frag = new EditNoteFragment();
        		Bundle args = new Bundle();
        		args.putInt("action", EditNoteFragment.EDIT);
        		args.putLong("id", _chosenNoteID);
        		frag.setArguments(args);
        		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditNoteFragment.FRAG_TAG + 
        			"/" + _chosenNoteID);
			}
    		return(true);
    		
    	case VIEW_NOTE_ID:
			if (_a.useNewActivityForDetails())
        	{
        		// No split-screen in use.  Just start a new activity:
				Intent i = new Intent(_a,ViewNote.class);
        		i.putExtra("id", _chosenNoteID);
        		this.startActivity(i);
        	}
			else
			{
				// Display the note viewer in a separate fragment:
				ViewNoteFragment frag = new ViewNoteFragment();
        		Bundle args = new Bundle();
        		args.putLong("id", _chosenNoteID);
        		frag.setArguments(args);
        		_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewNoteFragment.FRAG_TAG + 
        			"/" + _chosenNoteID);
			}
    		return(true);
    		
    	case RENAME_ID:
    		// Button handlers for the dialog asking for the new name:
    		dialogClickListener = new DialogInterface.OnClickListener() 
            {           
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which)
                    {
                    case DialogInterface.BUTTON_POSITIVE:
                        // Yes clicked:
                        String newName = _renameInput.getText().toString().trim();
                        if (newName.length()>0)
                        {
                        	// Get a note object:
                        	UTLNote note = _notesDB.getNote(_chosenNoteID);
                        	if (note==null)
                        	{
                        		Util.popup(_a, R.string.Item_no_longer_exists);
                        		return;
                        	}
                        	
                        	// Update the database:
                        	note.title = newName;
                        	note.mod_date = System.currentTimeMillis();
                        	if (!_notesDB.modifyNote(note))
                        	{
                        		Util.popup(_a, R.string.DbModifyFailed);
                        		Util.log("Cannot modify note name.");
                        	}
                        	else
                        	{
                        		// Upload the note to Toodledo and refresh data:
                        		uploadEditedNote(_chosenNoteID);
                        		saveCurrentPosition();
                        		refreshData();
                        		handleModeChange(MODE_NORMAL);                    	
                            	refreshOtherPanes();
                            	restorePosition();
                        	}
                        }
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        // No clicked:
                        break;
                    }                    
                }
            };
            builder = new AlertDialog.Builder(_a);
            _renameInput = new EditText(_a);
            _renameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
            	TYPE_TEXT_FLAG_CAP_SENTENCES);
            Util.limitNumChars(_renameInput, Util.MAX_NOTE_TITLE_LENGTH);
            builder.setView(_renameInput);
            builder.setTitle(Util.getString(R.string.Enter_New_Name));
            builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
            builder.show();
    		return(true);
    		
    	case CHANGE_FOLDER_ID:
    		// Create a list of folders to choose from, including the "no folder" folder:
        	ArrayList<String> nameList = new ArrayList<String>();
        	_idList = new ArrayList<Long>();
        	int checkedIndex = 0;
        	nameList.add(Util.getString(R.string.None));
        	_idList.add(0L);
        	Cursor c = _foldersDB.queryFolders("account_id="+note.account_id+" and "+
        		"archived=0", FoldersDbAdapter.bestSortOrder(note.account_id));
        	c.moveToPosition(-1);
        	while (c.moveToNext())
        	{
        		nameList.add(Util.cString(c, "title"));
        		_idList.add(Util.cLong(c, "_id"));
        		if (Util.cLong(c, "_id")==note.folder_id)
        		{
        			checkedIndex = _idList.size()-1;
        		}
        	}
        	c.close();
        	
        	// Create a dialog with these choices:
        	builder = new AlertDialog.Builder(_a);
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
					
					// Get the new folder ID, and the note we're modifying:
					long newFolderID = _idList.get(which);
					UTLNote note = _notesDB.getNote(_chosenNoteID);
					if (note==null)
					{
						Util.popup(_a, R.string.Item_no_longer_exists);
						return;
					}
					
					// Update the folder in the database:
					note.folder_id = newFolderID;
					note.mod_date = System.currentTimeMillis();
					if (!_notesDB.modifyNote(note))
					{
						Util.popup(_a, R.string.DbModifyFailed);
						return;
					}
					
					// Upload the note to Toodledo and refresh data:
            		uploadEditedNote(_chosenNoteID);
            		saveCurrentPosition();
            		refreshData();
            		handleModeChange(MODE_NORMAL);                    	
                	refreshOtherPanes();
                	restorePosition();
				}
			});
        	builder.setTitle(Util.getString(R.string.New_Folder_for)+"\""+note.title+"\"");
        	builder.show();
        	return(true);
        	
    	case DELETE_ID:
        	// Button handlers for the dialog asking for confirmation:
    		dialogClickListener = new DialogInterface.OnClickListener() 
            {           
                @Override
                public void onClick(DialogInterface dialog, int which) 
                {
                    switch (which)
                    {
                    case DialogInterface.BUTTON_POSITIVE:
                        // Yes clicked:
                        // We need the note's toodledo ID:
                        UTLNote note = _notesDB.getNote(_chosenNoteID);
                        if (note==null)
                        {
                        	Util.popup(_a, R.string.Item_no_longer_exists);
                    		return;
                        }

                        if (note.td_id>-1)
                        {
                        	// The item has a Toodledo ID, so the deletion needs
                        	// to be uploaded.
                        	// Update the pending deletes table:
                        	PendingDeletesDbAdapter deletes = new 
                        		PendingDeletesDbAdapter();
                        	if (-1==deletes.addPendingDelete("note", note.td_id,
                        		note.account_id))
                        	{
                        		Util.popup(_a, R.string.DbInsertFailed);
                        		Util.log("Cannot add pending delete in NoteList.java.");
                        		return;
                        	}
                        	
                    		Intent i = new Intent(_a, Synchronizer.class);
                        	i.putExtra("command", "sync_item");
                        	i.putExtra("item_type",Synchronizer.NOTE);
                        	i.putExtra("item_id", note.td_id);
                        	i.putExtra("account_id", note.account_id);
                        	i.putExtra("operation",Synchronizer.DELETE);
							Synchronizer.enqueueWork(_a,i);
                        }
                        
                		// Delete the note:
                		if (!_notesDB.deleteNote(note._id))
                		{
                			Util.popup(_a, R.string.DbModifyFailed);
                			Util.log("Could not delete note from database.");
                			return;
                		}
                		
                		// Refresh the display:
                		refreshData();
                    	handleModeChange(MODE_NORMAL);                    	
                    	refreshOtherPanes();
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        // No clicked:
                        break;
                    }                    
                }
            };
            builder = new AlertDialog.Builder(_a);
            builder.setMessage(R.string.Note_delete_confirmation);
            builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
            builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
            builder.setTitle(note.title);
            builder.show();
    		return(true);	
    	}
    	
    	return(true);
    }
    
    /** Populate the action menu for multiple notes. The bottomMenu argument is ignored if we're
	 * in landscape mode. */
    private void populateActionMenuForMultipleNotes(Menu bottomMenu, Menu topMenu)
    {
    	if (bottomMenu!=null)
    		bottomMenu.clear();
    	topMenu.clear();

		if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT)
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

    	// We always show the "select all" command, even if there are no notes selected:
    	MenuItemCompat.setShowAsAction(mainMenu.add(0,SELECT_ALL_ID,0,R.string.Select_All).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_select_all))
    		,MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
    	if (_selectedNotes==null || _selectedNotes.size()==0)
    		return;

    	// The delete action. Always visible.
		MenuItemCompat.setShowAsAction(mainMenu.add(0,DELETE_ID,0,R.string.Delete).
			setIcon(_a.resourceIdFromAttr(R.attr.ab_delete)),MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

		// The option to change the folder. Always in the overflow menu since we don't have an
		// icon.
    	MenuItemCompat.setShowAsAction(overflowMenu.add(0,CHANGE_FOLDER_ID,0,R.string.Change_Folder),
    		MenuItemCompat.SHOW_AS_ACTION_NEVER);

    }
    
    // Handle a command issued on multiple notes:
    private void handleMultiSelectCommand(MenuItem menuItem)
    {
    	handleMultiSelectCommand(menuItem.getItemId());
    }
    private void handleMultiSelectCommand(int commandID)
    {
    	DialogInterface.OnClickListener dialogClickListener;
    	AlertDialog.Builder builder;
    	
    	if (commandID==SELECT_ALL_ID)
        {
        	// Clear the current list of selected notes:
    		if (_selectedNotes==null)
    			_selectedNotes = new HashSet<Long>();
    		else
    			_selectedNotes.clear();

    		// Iterate through all notes listed here and mark them as selected.
			Cursor c = getCursor();
			while (c.moveToNext())
				_selectedNotes.add(Util.cLong(c,"_id"));
			c.close();
        	
        	// Refresh the display:
        	saveCurrentPosition();
        	refreshData();
        	restorePosition();
        	
        	// Notify the user and display the available commands:
        	Util.popup(_a, R.string.All_Notes_Selected);
        	_actionMode.invalidate();
        	return;
        }
    	
    	switch (commandID)
    	{
    	case CHANGE_FOLDER_ID:
    		// Verify all selected folders are in the same account:
    		long accountID = -1;
    		Iterator<Long> it = _selectedNotes.iterator();
    		while (it.hasNext())
    		{
    			long noteID = it.next();
    			UTLNote note = _notesDB.getNote(noteID);
    			if (accountID==-1)
    				accountID = note.account_id;
    			else
    			{
    				if (note.account_id!=accountID)
    				{
    					Util.popup(_a, _a.getString(R.string.All_notes_must_be_in_same_account));
    					return;
    				}
    			}
    		}
    		
    		// Create a list of folders to choose from, including the "no folder" folder:
        	ArrayList<String> nameList = new ArrayList<String>();
        	_idList = new ArrayList<Long>();
        	nameList.add(Util.getString(R.string.None));
        	_idList.add(0L);
        	Cursor c = _foldersDB.queryFolders("account_id="+accountID+" and "+
        		"archived=0", FoldersDbAdapter.bestSortOrder(accountID));
        	c.moveToPosition(-1);
        	while (c.moveToNext())
        	{
        		nameList.add(Util.cString(c, "title"));
        		_idList.add(Util.cLong(c, "_id"));
        	}
        	c.close();
        	
        	// Create a dialog with these choices:
        	builder = new AlertDialog.Builder(_a);
        	String[] nameArray = Util.iteratorToStringArray(nameList.iterator(), nameList.
        		size());
        	builder.setItems(nameArray, new DialogInterface.OnClickListener()
			{				
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					// Dismiss the dialog:
					dialog.dismiss();
					
					// Get the new folder ID:
					long newFolderID = _idList.get(which);
					
					// Loop through all notes being modified and update the folder:
					Iterator<Long> it = _selectedNotes.iterator();
		    		while (it.hasNext())
		    		{
		    			long noteID = it.next();
		    			UTLNote note = _notesDB.getNote(noteID);
		    			if (note==null)
		    				continue;
		    			note.folder_id = newFolderID;
						note.mod_date = System.currentTimeMillis();
						if (!_notesDB.modifyNote(note))
						{
							Util.popup(_a, R.string.DbModifyFailed);
							return;
						}
						uploadEditedNote(noteID);
		    		}
										
					// Refresh data:
            		saveCurrentPosition();
            		refreshData();
            		handleModeChange(MODE_NORMAL);                    	
                	refreshOtherPanes();
                	restorePosition();
				}
			});
        	builder.setTitle(_a.getString(R.string.New_Folder));
        	builder.show();
    		return;
    		
    	case DELETE_ID:
        	// Button handlers for the dialog asking for confirmation:
    		dialogClickListener = new DialogInterface.OnClickListener() 
            {           
                @Override
                public void onClick(DialogInterface dialog, int which) 
                {
                    switch (which)
                    {
                    case DialogInterface.BUTTON_POSITIVE:
                        // Yes clicked.  Loop through all notes being deleted:
                    	Iterator<Long> it = _selectedNotes.iterator();
                    	while (it.hasNext())
                    	{
                    		UTLNote note = _notesDB.getNote(it.next());
                    		if (note==null)
                    			continue;
                    		
                    		if (note.td_id>-1)
                            {
                    			// The item has a Toodledo ID, so the deletion needs
                            	// to be uploaded.
                            	// Update the pending deletes table:
                            	PendingDeletesDbAdapter deletes = new PendingDeletesDbAdapter();
                            	if (-1==deletes.addPendingDelete("note", note.td_id,
                            		note.account_id))
                            	{
                            		Util.popup(_a, R.string.DbInsertFailed);
                            		Util.log("Cannot add pending delete in NoteList.java (multiple).");
                            		return;
                            	}
                            	
                        		Intent i = new Intent(_a, Synchronizer.class);
                            	i.putExtra("command", "sync_item");
                            	i.putExtra("item_type",Synchronizer.NOTE);
                            	i.putExtra("item_id", note.td_id);
                            	i.putExtra("account_id", note.account_id);
                            	i.putExtra("operation",Synchronizer.DELETE);
								Synchronizer.enqueueWork(_a,i);
                            }

                    		// Delete the note:
                    		if (!_notesDB.deleteNote(note._id))
                    		{
                    			Util.popup(_a, R.string.DbModifyFailed);
                    			Util.log("Could not delete note from database (multiple).");
                    			return;
                    		}
                    	}
                        
                		// Refresh the display:
                		refreshData();
                    	handleModeChange(MODE_NORMAL);                    	
                    	refreshOtherPanes();
                        break;
                        
                    case DialogInterface.BUTTON_NEGATIVE:
                        // No clicked:
                        break;
                    }                    
                }
            };
            builder = new AlertDialog.Builder(_a);
            builder.setMessage(R.string.Notes_delete_confirmation);
            builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
            builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
            builder.setTitle(NumberFormat.getInstance().format(_selectedNotes.size())+" "+
            	getString(R.string.notes_selected));
            builder.show();
    		return;
    	}
    }
    
    /** Set the highlighting for a note on or off: */
    private void setHighlighting(final ViewGroup row, boolean isHighlighted)
    {
    	TextView name = (TextView) row.findViewById(R.id.note_row_title);
    	TextView body = (TextView) row.findViewById(R.id.note_row_note_body);
    	
    	if (isHighlighted)
    	{
    		row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr( 
			    R.attr.list_highlight_bg_color)));
			name.setTextColor(_res.getColor(_a.resourceIdFromAttr(
				R.attr.list_highlight_text_color)));
			body.setTextColor(_res.getColor(_a.resourceIdFromAttr(
				R.attr.list_highlight_text_color)));
    	}
    	else
    	{
    		row.setBackgroundColor(Util.colorFromAttr(_a,R.attr.main_background_color));
    		new Handler().postDelayed(() -> {
    			// The short delay is needed to prevent the ripple from showing.
    			row.setBackgroundResource(Util.resourceIdFromAttr(_a,android.R.attr.
					selectableItemBackground));
			},500);
			name.setTextColor(_res.getColor(_a.resourceIdFromAttr(
        		R.attr.utl_text_color)));
			body.setTextColor(_res.getColor(_a.resourceIdFromAttr(
        		R.attr.utl_text_color)));
    	}
    }
    
    /** Set the highlighting when only the note ID is known. */
    private void setHighlighting(long noteID, boolean isHighlighted)
    {
    	// Search for the correct view:
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
    	ViewGroup row = (ViewGroup)lv.findViewWithTag(Long.valueOf(noteID));
    	if (row!=null)
		{
			if (row.getId()==R.id.note_row_container)
				row = row.findViewById(R.id.note_list_row_main);
			setHighlighting(row, isHighlighted);
		}
    }
    
    // Through the instant upload feature, upload a note that has been edited:
    private void uploadEditedNote(long noteID)
    {
    	UTLNote note = _notesDB.getNote(noteID);
    	if (note==null)
    		return;
    	    	
    	Intent i = new Intent(_a, Synchronizer.class);
    	i.putExtra("command", "sync_item");
    	i.putExtra("item_type",Synchronizer.NOTE);
    	i.putExtra("item_id", noteID);
    	i.putExtra("account_id", note.account_id);
    	i.putExtra("operation",Synchronizer.MODIFY);
		Synchronizer.enqueueWork(_a,i);
    }

    // Save the current position that we're scrolled to in the list:
    public void saveCurrentPosition()
    {
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
        _lastSelection = lv.getFirstVisiblePosition();
    }
    
    // Restore the position that was saved:
    public void restorePosition()
    {
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
        if (_lastSelection>-1 && _lastSelection<lv.getCount())
        {
        	lv.setSelection(_lastSelection);
        }

    }
    
    // Refresh the other panes, such as the nav drawer and task viewer:
    private void refreshOtherPanes()
    {
    	_a.refreshNavDrawerCounts();
    	
    	if (_ssMode==Util.SS_2_PANE_LIST_DETAILS || _ssMode==Util.SS_3_PANE)
    	{
    		Fragment detailFrag = _a.getFragmentByType(UtlNavDrawerActivity.FRAG_DETAILS);
    		if (detailFrag!=null && detailFrag instanceof ViewNoteFragment)
    		{
    			ViewNoteFragment vnFrag = (ViewNoteFragment)detailFrag;
	    		vnFrag.refreshDisplay();
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
    	
    	_a.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
    }
    
    // Called before the activity is destroyed due to orientation change:
    @Override
	public void onSaveInstanceState(Bundle b)
    {
    	b.putBoolean("waiting_on_manual_sync", waitingOnManualSync);
    	if (_chosenNoteID>0)
    		b.putLong("chosen_note_id", _chosenNoteID);
    	b.putInt("mode",_mode);
    	if (_selectedNotes!=null && _selectedNotes.size()>0)
    	{
    		b.putLongArray("selected_notes", Util.iteratorToLongArray(_selectedNotes.iterator(), 
    			_selectedNotes.size()));
    	}
    	
    	// Save the info on the notes we're viewing:
    	if (!_usingSQL)
    		b.putLong("folder_id", _folderID);
    	else
    		b.putString("sql", _sqlQuery);
    }
    
    // Handler for pausing the activity (when we leave):
    @Override
    public void onPause()
    {
        super.onPause();
        
        saveCurrentPosition();
        
        // Remove the link to the synchronizer service:
        doUnbindService();
    }

    /** Start a synchronization: */
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
                    if (waitingOnManualSync)
                    {
                        waitingOnManualSync = false;
                        if (result == Synchronizer.SUCCESS)
                        {
                            Util.popup(_a, R.string.Sync_Successful);
                        }
                        else
                        {
                            Util.popup(_a, Util.getString(R.string.Sync_Failed_)+
                                Synchronizer.getFailureString(result));
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
                    _syncProgressContainer.setVisibility(View.GONE);
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
    		_a.bindService(new Intent(_a, 
    			Synchronizer.class), mConnection, Context.BIND_AUTO_CREATE);
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
    
    @Override
    public void onDestroy()
    {
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	super.onDestroy();
    }
}
