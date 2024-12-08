package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.droidparts.widget.ClearableEditText;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.PopupMenu;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/** This handles editing of notes.  Arguments are:<br>
 * action: either EditNoteFragment.ADD or EditNoteFragment.EDIT<br>
 * id: The ID, for edit operations.<br>
 * default_folder_id: For add operations, the default folder ID.<br>
 * default_account_id: For add operations, the default account ID
 * from_viewer_fragment: Set this to true if this was launched from a task viewer fragment in 
 *     split-screen mode.
 * is_only_fragment: Overrides the default code which specifies whether this is the only fragment on the
 *     screen.  By default, the existence of fragment arguments means that this is not the only fragment
 *     (we are in split-screen mode).  Set this to true if fragment arguments are being passed in
 *     and we are not using split-screen mode.
 * */
public class EditNoteFragment extends UtlFragment implements KeyHandlerFragment
{
	private static final String TAG = "EditNoteFragment";

	// Codes to specify whether we are adding or editing:
    public static final int ADD = 1;
    public static final int EDIT = 2;
    
    // Codes for activities that return results:
 	public static final int GET_ACCOUNTS = 1;
 	
 	// Menu items:
    private static final int MENU_DELETE = Menu.FIRST+1;
    private static final int MENU_GOTO_VIEWER = Menu.FIRST+2;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditNoteFragment";
    
    // IDs of views whose contents are not automatically saved when the screen
    // changes orientation:
    static private int[] _viewIDsToSave = new int[] {
    	R.id.edit_note_account_value
    };
    
    // The operation we're performing (ADD or EDIT):
    private int _op;
    
    // The note we're editing, if applicable:
    private UTLNote _note;
    
    // Flag indicating if changes have been made:
    private boolean _changesMade;
    
    // The IDs of selected accounts:
    HashSet<Long> _selectedAccountIDs;
    
    // Quick reference to key items:
    private UtlActivity _a;
    private Resources _res;
    private int _ssMode;
    private ViewGroup _rootView;
    private SharedPreferences _settings;
    private NotesDbAdapter _notesDB;
    private AccountsDbAdapter _accountsDB;
    private FoldersDbAdapter _foldersDB;
    
    // Records if we're the only fragment in the activity.
    private boolean _isOnlyFragment;

    // The save/cancel bar (if it's in use):
    private SaveCancelTopBar _saveCancelBar;
    
    // Set this to true if the editor was launched from the viewer in split-screen mode:
    private boolean _launchedFromViewerInSS;
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private EditText _body;
    private TextView _accounts;
    private Spinner _folder;
    private EditText _tempEditText;
    
    // The number of accounts we have:
    private int _numAccounts;
    
    // This hash is used to determine if other spinners have changed their selection:
    private HashMap<Integer,Integer> _spinnerSelections;

    // Contains handlers to call if the user exits without saving:
    private ExitWithoutSaveHandler _tempExitWithoutSaveHandler;
    
    // This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	Log.v(TAG,"onCreateView called.");
		return inflater.inflate(R.layout.edit_note2, container, false);
    }
    
    /** Called when the fragment is started. */
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
        _notesDB = new NotesDbAdapter();
        _accountsDB = new AccountsDbAdapter();
        _foldersDB = new FoldersDbAdapter();
        _selectedAccountIDs = new HashSet<Long>();
        _spinnerSelections = new HashMap<Integer,Integer>();
        
        // Get references to views which hold task data:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_note_title);
        _body = (EditText)_rootView.findViewById(R.id.edit_note_body);
        _accounts = (TextView)_rootView.findViewById(R.id.edit_note_account_value);
        _folder = (Spinner)_rootView.findViewById(R.id.edit_note_folder_value);
        
        Intent intent = _a.getIntent();
        Bundle extras = intent.getExtras();
        
        // Determine whether we're adding or editing, and check for required inputs.
        _isOnlyFragment = true;
        _saveCancelBar = null;
        Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.size()>0)
        {
        	// Arguments were passed into the fragment.  These take precedence over the Intent.
        	extras = fragArgs;
        	
        	if (fragArgs.containsKey("is_only_fragment"))
        		_isOnlyFragment = fragArgs.getBoolean("is_only_fragment");
        	else
        		_isOnlyFragment = false;
        	
        	if (extras.containsKey("from_viewer_fragment") && extras.getBoolean("from_viewer_fragment"))
        		_launchedFromViewerInSS = true;
        	
        	if (!_isOnlyFragment)
        	{
	        	// We will use a separate save/cancel bar:
	        	_saveCancelBar = new SaveCancelTopBar(_a,_rootView);
	        	_saveCancelBar.setSaveHandler(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						// Do nothing if resize mode is on:
						if (inResizeMode()) return;
						
						saveAndReturn(true);
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
        }
        if (extras==null)
        {
        	// Treat this as an add operation.
        	Util.log("Missing arguments in EditNoteFragment.java.  Treating as an add operation.");
        	_op = ADD;
        	setTitle(R.string.New_Note);
        }
        else if (extras.containsKey("action") && extras.getInt("action")==ADD)
        {
        	Util.log("Adding a new note.");
        	_op = ADD;
        	setTitle(R.string.New_Note);
        }
        else if (extras.containsKey("action") && extras.containsKey("id") && 
        	extras.getInt("action")==EDIT && extras.getLong("id")>0)
        {
        	Util.log("Editing note ID: "+extras.getLong("id"));
        	_op = EDIT;
        	setTitle(R.string.Edit_Note);
        	
        	// When editing, we need to retrieve the note.
        	_note = _notesDB.getNote(extras.getLong("id"));
        	if (_note==null)
        	{
        		Util.log("Bad note ID passed to EditNoteFragment.");
        		_op = ADD;
        		setTitle(R.string.New_Note);
        	}
        }
        else
        {
        	Util.log("Adding a new note, but bad inputs were passed in.");
        	_op = ADD;
        	setTitle(R.string.New_Note);
        }
        
        if (_op==EDIT || (savedInstanceState!=null && savedInstanceState.containsKey(
        	"dont_show_keyboard")))
        {
        	// When editing, we don't want the keyboard to appear automatically.
        	_a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        else
        	_a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);

		if (_isOnlyFragment)
			initBannerAd(_rootView);

        if (_isOnlyFragment)
        {
        	// This fragment will be updating the Action Bar:
        	setHasOptionsMenu(true);
        	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.tab_task_notes));
        	
            // This fragment will handle presses on the Home button.
            _a.fragmentHandlesHome(true);
        }
        
        if (_saveCancelBar!=null && _op==EDIT)
    	{
    		// Add additional commands to the save/cancel bar (only applicable when editing):
    		_saveCancelBar.addToOverflow(MENU_DELETE, R.string.Delete);
    		_saveCancelBar.addToOverflow(MENU_GOTO_VIEWER, R.string.Goto_Viewer);
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
        	
    	// Hide layout items that should not appear:
    	if (_numAccounts<2 || _op==EDIT)
    	{
    		// Showing the accounts selector makes no sense if there is one account or if we're editing,
    		// since we don't support moving a note between accounts.
    		_rootView.findViewById(R.id.edit_note_account_container).setVisibility(View.GONE);
    		
    		// The folder row must not have a bottom border now.
    		_rootView.findViewById(R.id.edit_note_folder_container).setBackgroundResource(Util.
				resourceIdFromAttr(_a,android.R.attr.selectableItemBackground));
    	}
    	
    	// Initialize the folder spinner with the list of valid folder names:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("folders"))
        {
    		_a.initSpinner(_folder, savedInstanceState.getStringArray("folders"));
        }
    	else if (_op==ADD)
    	{
    		Cursor c = _accountsDB.getAllAccounts();
    		c.moveToFirst();
    		UTLAccount firstAccount = _accountsDB.getUTLAccount(c);
    		if (c.getCount()>1 || firstAccount.sync_service!=UTLAccount.SYNC_TOODLEDO)
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

	            // Insert the "no folder" and "add folder" options:
	            ArrayList<String> folderNamesList = new ArrayList<String>();
	            folderNamesList.add(getString(R.string.None));
	            for (int i=0; i<sortedNames.length; i++)
	            {
	            	folderNamesList.add(sortedNames[i]);
	            }
	            folderNamesList.add(getString(R.string.Add_Folder));
	            
	            // Initialize the spinner itself:
	            _a.initSpinner(_folder, folderNamesList);
        	}
        	else
        	{
        		// Only 1 account, which syncs with Toodledo. Use Toodledo ordering.
        		c.close();
        		ArrayList<String> folderNamesList = new ArrayList<String>();
        		folderNamesList.add(getString(R.string.None));  // "None" goes at the front.
        		c = _foldersDB.getFoldersByOrder();
        		c.moveToPosition(-1);
        		while (c.moveToNext())
        		{
        			folderNamesList.add(Util.cString(c, "title"));
        		}
        		c.close();
        		folderNamesList.add(getString(R.string.Add_Folder)); // "Add folder" goes at the end.
        		
        		// Initialize the spinner itself:
        		_a.initSpinner(_folder, folderNamesList);
        	}
    	}
    	else
    	{
    		// When editing, we only include folders from the task's account.  If the current 
    		// note's folder is archived, then put it at the top of the list.
    		ArrayList<String> folderNamesList = new ArrayList<String>();
    		folderNamesList.add(getString(R.string.None));  // "None" goes at the front.
    		Cursor c = _foldersDB.getFolder(_note.folder_id);
        	if (c.moveToFirst())
        	{
        		if (Util.cInt(c, "archived")==1)
        		{
        			folderNamesList.add(Util.cString(c, "title"));
        		}
        	}
        	c.close();
        	
        	// Insert all of the unarchived folders in the account:
        	UTLAccount a = _accountsDB.getAccount(_note.account_id);
        	if (a!=null && a.sync_service==UTLAccount.SYNC_TOODLEDO)
        		c = _foldersDB.getFoldersByOrder();
        	else
        		c = _foldersDB.getFoldersByNameNoCase();
        	c.moveToPosition(-1);
        	while (c.moveToNext())
        	{
        		if (Util.cLong(c, "account_id") == _note.account_id)
                {
        			folderNamesList.add(Util.cString(c,"title"));
                }
        	}
        	c.close();
        	folderNamesList.add(getString(R.string.Add_Folder));
        	
        	// Initialize the spinner itself:
    		_a.initSpinner(_folder, folderNamesList);
    	}
    	
    	// Set the title of the folder spinner:
    	_folder.setPromptId(R.string.Folder);
    	
    	if (!_settings.getBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true))
    	{
    		// The folder field is disabled.
    		_rootView.findViewById(R.id.edit_note_folder_container).setVisibility(View.GONE);
    	}
    	
    	//
        // Initialize the data in the views:
        //
    	
    	if (_op==ADD)
    	{
    		// Defaults come from user preferences.
    		
    		// Default folder:
    		if (extras!=null && extras.containsKey("default_folder_id"))
    		{
    			// Default was passed in to the Activity or Fragment.
    			Cursor c = _foldersDB.getFolder(extras.getLong("default_folder_id"));
    			if (c.moveToFirst())
    			{
    				_a.setSpinnerSelection(_folder, Util.cString(c, "title"));
    			}
    			c.close();
    		}
    		else if (_settings.contains(PrefNames.DEFAULT_FOLDER))
    		{
    			// Look at the user's settings for the default.
    			long folderID = _settings.getLong(PrefNames.DEFAULT_FOLDER,0);
                Cursor c = _foldersDB.getFolder(folderID);
                if (c.moveToFirst())
                {
                	_a.setSpinnerSelection(_folder, Util.cString(c, "title"));
                }  
                c.close();
    		}
    		
    		// Account:
    		if (extras!=null && extras.containsKey("default_account_id"))
    		{
    			// Default account was passed in to the Activity or Fragment.
    			UTLAccount acc = _accountsDB.getAccount(extras.getLong("default_account_id"));
    			if (acc!=null)
    			{
    				_accounts.setText(acc.name);
                    _selectedAccountIDs.add(acc._id);
    			}
    		}
    		else if (_settings.contains(PrefNames.DEFAULT_ACCOUNT))
            {
                UTLAccount acc = _accountsDB.getAccount(_settings.getLong(PrefNames.DEFAULT_ACCOUNT, 0));
                if (acc != null)
                {
                    _accounts.setText(acc.name);
                    _selectedAccountIDs.add(acc._id);
                }             
            }
            if (_selectedAccountIDs.size()==0)
            {
                // No default account specified.  Get the first account.
                Cursor c = _accountsDB.getAllAccounts();
                if (c.moveToFirst())
                {
                    UTLAccount acc = _accountsDB.getUTLAccount(c);
                    _accounts.setText(acc.name);
                    _selectedAccountIDs.add(acc._id);
                }
                c.close();
            }
    	}
    	else
    	{
    		// Defaults come from the note we're editing.
    		
    		// Title and note body:
    		_title.setText(_note.title);
    		_body.setText(_note.note);
    		
    		// Folder:
    		if (_note.folder_id>0)
    		{
    			Cursor c = _foldersDB.getFolder(_note.folder_id);
    			if (c.moveToFirst())
    			{
    				_a.setSpinnerSelection(_folder, Util.cString(c,"title"));
    			}
    			c.close();
    		}
    		
    		// Account:
    		UTLAccount acc = _accountsDB.getAccount(_note.account_id);
    		if (acc!=null)
    		{
    			_selectedAccountIDs.add(_note.account_id);
    			_accounts.setText(acc.name);
    		}
    	}

    	// If we're starting the fragment for the first time, then we know that no changes
        // have been made.  If it's not the first time (say, if the screen was rotated)
        // then we check the bundle passed in:
        _changesMade = false;
        if (savedInstanceState!=null && savedInstanceState.containsKey("changes_made"))
        	_changesMade = savedInstanceState.getBoolean("changes_made");
        
        if (savedInstanceState!=null)
        {
        	// The user likely rotated the screen.  Call the restore function to fill in
        	// the values for controls.
        	onRestoreInstanceState(savedInstanceState);
        }
        
        // This function will hide the keyboard if the user taps on something that is not an EditText:
        _a.setupAutoKeyboardHiding(_rootView.findViewById(R.id.edit_note_wrapper));
        
        
        //
        // Define handlers for buttons, spinners, etc...
        //
        
        // Adding a new folder:
        if (_settings.getBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true))
        {
            if (_folder.getSelectedItemPosition()<0)
            	_spinnerSelections.put(_folder.getId(),0);
            else
            	_spinnerSelections.put(_folder.getId(), _folder.getSelectedItemPosition());
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
                        _tempEditText = new EditText(_a);
                        _tempEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
                        	TYPE_TEXT_FLAG_CAP_SENTENCES);
                        _tempEditText.setId(0);
                        alert.setView(_tempEditText);
                        alert.setPositiveButton(Util.getString(R.string.Save), 
                            new DialogInterface.OnClickListener() 
                            {
                                @SuppressWarnings("unchecked")
                                @Override
                                public void onClick(DialogInterface dialog, int which) 
                                {
                                    // Add the new folder name to the spinner, and make
                                    // it the current selection.
                                	ArrayList<String> folderNames = new ArrayList<String>();
                                	for (int i=0; i<_folder.getCount(); i++)
                                	{
                                		folderNames.add((String)_folder.getItemAtPosition(i));
                                	}
                                	folderNames.add(folderNames.size()-1, _tempEditText.getText().
                                		toString());
                                	_a.initSpinner(_folder, folderNames);
                                	_folder.setSelection(folderNames.size()-2);
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
                    else if (position != _spinnerSelections.get(_folder.getId()))
                    {
                    	_changesMade = true;
                    }
                }
                
                public void onNothingSelected(AdapterView<?>  parent)
                {
                    // Nothing to do.
                }
            });

            // Tapping on the overall folder container triggers the spinner:
            _rootView.findViewById(R.id.edit_note_folder_container).setOnClickListener(
            	new View.OnClickListener()
        	{
        		@Override
        		public void onClick(View v)
        		{
        			_folder.performClick();
        		}
        	});
        }
    
        // Accounts button:
        _rootView.findViewById(R.id.edit_note_account_container).setOnClickListener(new 
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
                    startActivityForResult(i,GET_ACCOUNTS);
                }
                else
                {
                    Util.popup(_a, R.string.account_cannot_be_changed);
                }
            }
        });
        
        // Handler for note clear button:
        _rootView.findViewById(R.id.edit_note_body_clear).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_body.setText("");
				_changesMade = true;
			}
		});
        
        // Detect changes in the title.  We need to record when changes have been made:
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
        
        // Detect changes in the note body.  We need to record when changes have been made:
        _body.addTextChangedListener(new TextWatcher() {
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
        
        // Run a sync if needed:
        Util.doMinimalSync(_a);
        
        // If we're adding, prompt for a note title:
        if (_op==ADD && savedInstanceState==null)
        	promptForTitle();
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
    	
    	// Restore account IDs:
    	if (b.containsKey("account_ids"))
    	{
	    	_selectedAccountIDs.clear();
	    	long[] accountIDs = b.getLongArray("account_ids");
	    	for (int i=0; i<accountIDs.length; i++)
	    		_selectedAccountIDs.add(accountIDs[i]);
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
    		// Add in a command to go to the viewer:
    		MenuItemCompat.setShowAsAction(menu.add(0,MENU_GOTO_VIEWER,0,R.string.Goto_Viewer).setIcon(
    			_a.resourceIdFromAttr(R.attr.ab_view)),MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
    		
	    	// Add in a "delete" item.
	    	MenuItemCompat.setShowAsAction(menu.add(0,MENU_DELETE,0,R.string.Delete).setIcon(
    			_a.resourceIdFromAttr(R.attr.ab_delete)),MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
    	}
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
    		saveAndReturn(true);
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
						  if (_note.td_id>-1)
	                      {
	                      	  // The item has a Toodledo ID, so the deletion needs
	                          // to be uploaded.
	                          // Update the pending deletes table:
	                          PendingDeletesDbAdapter deletes = new PendingDeletesDbAdapter();
	                          if (-1==deletes.addPendingDelete("note", _note.td_id,
	                              _note.account_id))
	                          {
	                        	  Util.popup(_a, R.string.DbInsertFailed);
	                        	  Util.log("Cannot add pending delete in NoteList.java.");
	                        	  return;
	                          }
	                        	
	                    	  Intent i = new Intent(_a, Synchronizer.class);
	                          i.putExtra("command", "sync_item");
	                          i.putExtra("item_type",Synchronizer.NOTE);
	                          i.putExtra("item_id", _note.td_id);
	                          i.putExtra("account_id", _note.account_id);
	                          i.putExtra("operation",Synchronizer.DELETE);
							  Synchronizer.enqueueWork(_a,i);
	                      }
	                        
	                	  // Delete the note:
	                	  if (!_notesDB.deleteNote(_note._id))
	                	  {
	                		  Util.popup(_a, R.string.DbModifyFailed);
	                		  Util.log("Could not delete note from database.");
	                		  return;
	                	  }
						  
						  // Update the display:
						  if (_isOnlyFragment)
							  _a.finish();
						  else
						  {
							  // This must be wrapped in a UtlNavDrawerActivity.  Remove this
							  // fragment and display a message saying the user can tap on 
							  // another task to see its info.
							  UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
							  NoteListFragment listFrag = (NoteListFragment)nav.getFragmentByType(
								  UtlNavDrawerActivity.FRAG_LIST);
							  if (listFrag!=null)
								  listFrag.handleDeletion();
						  }
	                  }					
				  }
			  };
            
              // Display the confirmation dialog:
			  AlertDialog.Builder builder = new AlertDialog.Builder(_a);
			  builder.setMessage(R.string.Note_delete_confirmation);
			  builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
	          builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
	          TasksDbAdapter db = new TasksDbAdapter();
          	  builder.setTitle(_note.title);
          	  builder.show();
    		  return true;
    		  
    	case MENU_GOTO_VIEWER:
    		confirmExitWithoutSave(new ExitWithoutSaveHandler() 
        	{
    			public void switchToViewer()
    			{
    				// Create a Fragment for displaying the viewer:
    				ViewNoteFragment frag = new ViewNoteFragment();
	        		Bundle args = new Bundle();
	        		args.putLong("id", _note._id);
	        		args.putBoolean("is_only_fragment", _isOnlyFragment);
	        		frag.setArguments(args);
	        		
	        		if (_isOnlyFragment)
	        		{
	        			FragmentManager fragmentManager = _a.getSupportFragmentManager();
	        			fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, frag,
	        				ViewNoteFragment.FRAG_TAG+"/"+_note._id).commit();
	        		}
	        		else
	        		{
	        			UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
	        			nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewNoteFragment.
	        				FRAG_TAG + "/" + _note._id);
	        		}
    			}
    			
    			@Override
    			public void onExitWithoutSave()
    			{
    				switchToViewer();
    			}
    			
    			@Override
    			public void onSave()
    			{
    				saveAndReturn(false);
    				switchToViewer();
    			}
        	});
    		return true;
    		
    	case android.R.id.home:
    		// If changes have been made, ask the user if they should be saved.
    		if (_isOnlyFragment)
    		{
	    		confirmExitWithoutSave(new ExitWithoutSaveHandler() 
	        	{
	    			@Override
	    			public void onExitWithoutSave()
	    			{
	    				// Just stop the parent activity:
			    		_a.setResult(Activity.RESULT_CANCELED);
			    		_a.finish();
	    			}
	        	});
	    		return true;
    		}
    	}
    	return super.onOptionsItemSelected(item);
    }
    
    // Handlers for Activity results:
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
	            }
	        }
        }
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
    	
    	// Save items in the folder spinner.  The folder list may have changed due to the user
    	// adding items.
    	String[] list = new String[_folder.getCount()];
    	for (i=0; i<_folder.getCount(); i++)
    	{
    		list[i] = (String)_folder.getItemAtPosition(i);
    	}
    	b.putStringArray("folders", list);
    	
    	// Save the flag indicating if changes have been made:
    	b.putBoolean("changes_made", _changesMade);
    	
    	// Don't automatically pop up the keyboard after rotation:
    	b.putBoolean("dont_show_keyboard", true);
    	
    	// Call the superclass version to handle the views that are automatically saved:
    	super.onSaveInstanceState(b); 
    }
    
    // Handler for the back button.  Returns true if key handled, else false.
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_BACK)
        {
        	if (_op==ADD)
        	{
        		// For an add operation, we don't ask about saving changes unless the
        		// user has at least typed in a title:
        		if (_title.getText().length()==0)
        		{
        			_a.setResult(Activity.RESULT_CANCELED);
                    _a.finish(); 
                    return true;
        		}
        	}
        	
        	confirmExitWithoutSave(new ExitWithoutSaveHandler() 
        	{
        		@Override
    			public void onExitWithoutSave()
    			{
        			// The back button can only propagate here if we're not in split screen mode,
        			// so we just need to close the activity.
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
		    			// Go back to the note viewer:
		    			ViewNoteFragment frag = new ViewNoteFragment();
		        		Bundle args = new Bundle();
		        		args.putLong("id", _note._id);
		        		frag.setArguments(args);
		        		nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, ViewTaskFragment.FRAG_TAG + 
		        			"/" + _note._id);
		    		}
		    		else if (_ssMode==Util.SS_2_PANE_NAV_LIST)
		    		{
		    			// This is displayed in a sliding drawer, so close it:
		    			nav.closeDrawer();
		    		}
		    		else
		    		{
		    			nav.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
		    		}
		    	}
			}
    	});
    }
    
    // "Save" button handler:
    private void saveAndReturn(boolean exitWhenDone)
    {
    	// Verify that the title is not empty:
    	String newTitle = _title.getText().toString();
    	if (newTitle.length()==0)
    	{
    		Util.popup(_a, R.string.Please_enter_a_title);
            return;
    	}
    	
    	// Verify the title is not too long:
    	if (newTitle.length()>Util.MAX_NOTE_TITLE_LENGTH)
    	{
    		Util.popup(_a,R.string.Title_is_too_long);
    		return;
    	}
    	
    	// Make sure the note body is not too long:
    	String newBody = _body.getText().toString();
    	if (newBody.length()>Util.MAX_NOTE_BODY_LENGTH)
    	{
    		Util.popup(_a, R.string.Note_is_too_long);
    		return;
    	}
    	
    	// At least one account must be specified:
    	if (_selectedAccountIDs.size()==0)
    	{
    		Util.popup(_a, R.string.Please_select_an_account);
    		return;
    	}
    	
    	// We need to create/edit the note once per account:
    	boolean newFolderAddedForAnyAccount = false;
    	Iterator<Long> it = _selectedAccountIDs.iterator();
        FeatureUsage featureUsage = new FeatureUsage(_a);
    	while (it.hasNext())
    	{
        	// Create a UTLNote object to put the data into:
        	UTLNote newNote = new UTLNote();
        	
        	// Set the ID:
        	if (_op==EDIT)
        	{
        		newNote._id = _note._id;
        		newNote.td_id = _note.td_id;
        	}
        	else
        	{
        		newNote.td_id = -1;
        	}
        	
        	// Account:
        	newNote.account_id = it.next();
        	
        	// Modification date is now.  Sync date depends on whether we're adding or
        	// modifying:
        	newNote.mod_date = System.currentTimeMillis();
        	if (_op==EDIT)
        	{
        		newNote.sync_date = _note.sync_date;
        	}
        	
        	// Title:
        	newNote.title = newTitle.replace("\n"," ");

        	// Get the name of the chosen folder and assign the folder ID to the note:
        	TextView tv = (TextView)_folder.getSelectedView();
        	boolean newFolderAdded = false;
        	if (tv==null || !_settings.getBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true))
        	{
        		// This can happen if the folder spinner has been hidden, or if note
        		// folders are disabled.
        		if (_op==EDIT)
        			newNote.folder_id = _note.folder_id;
        		else
        			newNote.folder_id = 0;
        	}
        	else
        	{
	        	String folderName = tv.getText().toString();
	        	if (!folderName.equals(Util.getString(R.string.None)))
	            {
	        		Cursor c = _foldersDB.queryFolders("title='"+Util.makeSafeForDatabase(
	        			folderName)+"' and account_id="+newNote.account_id, "title");
	        		if (!c.moveToFirst())
	        		{
	        			// We need to add in the folder:
	        			long folderID = _foldersDB.addFolder(-1,newNote.account_id,folderName, 
	                        false,false);
	                    if (folderID==-1)
	                    {
	                        Util.popup(_a, R.string.Unable_to_add_folder);
	                        return;
	                    }
	                    newNote.folder_id = folderID;
	                    newFolderAdded = true;
	                    newFolderAddedForAnyAccount = true;
	        		}
	        		else
	        		{
	        			newNote.folder_id = Util.cLong(c, "_id");
	        		}
	        		c.close();
	            }
	        	else
	        	{
	        		// No folder specified:
	        		newNote.folder_id = 0;
	        	}
        	}
    		
        	// The note itself:
        	newNote.note = newBody;

            // Record feature usage for the note.
            featureUsage.recordForNote(newNote);

        	// Update the database:
        	long noteID = -1;
        	if (_op==EDIT)
        	{
        		if (!_notesDB.modifyNote(newNote))
        		{
        			Util.popup(_a,R.string.DbModifyFailed);
        			return;
        		}
        	}
        	else
        	{
        		noteID = _notesDB.addNote(newNote);
        		if (noteID==-1)
        		{
        			Util.popup(_a, R.string.DbInsertFailed);
        			return;
        		}
        	}
        	
        	// If no new folders were added, we can instantly upload the note:
        	if (!newFolderAdded)
        	{
        		Intent i = new Intent(_a, Synchronizer.class);
            	i.putExtra("command", "sync_item");
            	i.putExtra("item_type",Synchronizer.NOTE);
            	i.putExtra("item_id", newNote._id);
            	i.putExtra("account_id", newNote.account_id);
            	if (_op==EDIT)
            	{
            		i.putExtra("operation",Synchronizer.MODIFY);
            	}
            	else
            	{
            		i.putExtra("operation",Synchronizer.ADD);
            	}
				Synchronizer.enqueueWork(_a,i);
        	}
    	}
    	
    	if (exitWhenDone)
    	{
	    	// Update the display and exit this fragment.
	    	if (_isOnlyFragment)
	    	{
	    		// Just stop the parent activity:
	    		_a.setResult(Activity.RESULT_OK);
	    		_a.finish();
	    	}
	    	else
	    	{
	    		NoteList listActivity = (NoteList)_a;
	    		
	    		// The navigation drawer needs refreshed:
	    		if (!newFolderAddedForAnyAccount)
	    			listActivity.refreshNavDrawerCounts();
	    		else
	    			listActivity.refreshWholeNavDrawer();
	    		
	    		// Refresh the list view:
	    		listActivity.handleNoteChange();
	    		
	    		if (_op==EDIT)
	    		{
		    		if (_launchedFromViewerInSS && (_ssMode==Util.SS_2_PANE_LIST_DETAILS ||
		    			_ssMode==Util.SS_3_PANE))
		    		{
		    			// Go back to the note viewer:
		    			ViewNoteFragment frag = new ViewNoteFragment();
		    			Bundle args = new Bundle();
		    			args.putLong("id", _note._id);
		    			frag.setArguments(args);
		    			listActivity.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
		    				ViewTaskFragment.FRAG_TAG + "/" + _note._id);
		    		}
		    		else if (_ssMode==Util.SS_2_PANE_NAV_LIST)
		    		{
		    			// This is displayed in a sliding drawer, so close it:
		    			listActivity.closeDrawer();
		    		}
		    		else
		    		{
		    			listActivity.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
		    		}
	    		}
	    		else
	    		{
	    			if (_ssMode==Util.SS_2_PANE_NAV_LIST)
		    		{
		    			// This is displayed in a sliding drawer, so close it:
	    				listActivity.closeDrawer();
		    		}
	    			else
	    			{
	    				listActivity.showDetailPaneMessage(_a.getString(R.string.Select_a_note_to_display));
	    			}
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
    		EditNoteFragment.this.saveAndReturn(true);
    	}
    }
    
    @Override
    public void onDestroy()
    {
    	_a.fragmentHandlesHome(false);
    	super.onDestroy();
    }
}
