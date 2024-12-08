package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Fragment to display (not not edit) a note.  Inputs are:<br>
 * id: The note ID in the database
 * is_only_fragment: Overrides the default code which specifies whether this is the only fragment on the
 *     screen.  By default, the existence of fragment arguments means that this is not the only fragment
 *     (we are in split-screen mode).  Set this to true if fragment arguments are being passed in
 *     and we are not using split-screen mode.
 * @author Nicholson
 *
 */
public class ViewNoteFragment extends UtlFragment implements KeyHandlerFragment
{
	// IDs for menu items:
    private static final int EDIT_ID = Menu.FIRST+3;
    private static final int DELETE_ID = Menu.FIRST+4;
    private static final int COPY_TEXT_ID = Menu.FIRST+5;
    
    // The tag to use when placing this fragment:
    public static final String FRAG_TAG = "ViewNoteFragment";
    
    // The note ID we are displaying:
    private long _id;
    
    // The note itself:
    private UTLNote _note;
    
    // Quick reference to the Fragment's activity:
    private UtlActivity _a;
    
    // Quick reference to settings:
    private SharedPreferences _settings;
    
    // Quick reference to resources:
    private Resources _res;
    
    // Quick reference to split_screen setting:
    private int _splitScreen;
    
    // Reference to the title bar (if used)
    private TitleBar _tb = null;
    
    // Records if we're the only fragment in the activity.
    private boolean _isOnlyFragment;
    
    // Quick reference to the root view of this fragment:
    private ViewGroup _rootView;
    
    /** This returns the view being used by this fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.view_note, container, false);
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
        
        // Get the ID of the note being displayed:
        _id = -1;
        Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.containsKey("id"))
        {
        	_id = fragArgs.getLong("id");
        	
        	if (fragArgs.containsKey("is_only_fragment"))
        		_isOnlyFragment = fragArgs.getBoolean("is_only_fragment");
        	else
        		_isOnlyFragment = false;
        }
        else
        {
        	// Check the Intent passed to the parent Activity:
        	Bundle extras = _a.getIntent().getExtras();
        	if (extras==null)
        	{
        		Util.log("Missing id argument in ViewNoteFragment.java.");
        		_a.finish();
        		return;
        	}
        	
        	if (extras.containsKey("id"))
        	{
        		_id = extras.getLong("id");
        	}
        	
        	// In this, we know that this is the only Fragment, and we're not using split-screen.
        	_isOnlyFragment = true;
        }
        
        if (_id==-1)
        {
        	Util.log("Missing id argument in ViewNoteFragment.java.");
    		_a.finish();
    		return;
        }
        
        if (_isOnlyFragment)
        {
        	// This fragment will be updating the Action Bar:
        	setHasOptionsMenu(true);
        }

        // In portrait mode on a small screen, hide the icon to show more of the title:
        if (_a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT && _settings.getFloat(
                PrefNames.DIAGONAL_SCREEN_SIZE, 5.0f)<5.2f && _isOnlyFragment)
        {
            _a.getSupportActionBar().setIcon(android.R.color.transparent);
        }

		if (_isOnlyFragment)
			initBannerAd(_rootView);

        refreshDisplay();
    }
    
    // Refresh the display:
    public void refreshDisplay()
    {
    	// Get references to key views:
    	TextView noteBody = (TextView)_rootView.findViewById(R.id.view_note_body);
    	
    	// Fetch the note being viewed:
    	_note = (new NotesDbAdapter()).getNote(_id);
    	if (_note==null)
    	{
    		noteBody.setText(R.string.Item_no_longer_exists);
    		return;
    	}

    	// Display the note body and title:
    	noteBody.setText(_note.note);
    	Linkify.addLinks(noteBody, Linkify.ALL);
    	if (_isOnlyFragment)
    		_a.getSupportActionBar().setTitle(_note.title);
    	
    	// In split-screen mode, we have a title bar to populate:
    	if (!_isOnlyFragment)
        	populateTitleBar();
    }
    
    // Populate the title bar (either create a new bar or refresh the exiting one)
    public void populateTitleBar()
    {
    	if (_tb==null)
    		_tb = new TitleBar(_a,(ViewGroup)getView());
    	else
    		_tb.reset();
    	_tb.setTitle(_note.title);
    	
    	// Add the Edit command:
    	_tb.addButton(EDIT_ID, _a.resourceIdFromAttr(R.attr.ab_edit_inv), R.string.Edit_Note, new
    		View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				handleTopCommand(EDIT_ID);
			}
		});
    	
    	// Add the Delete command:
    	_tb.addButton(DELETE_ID, _a.resourceIdFromAttr(R.attr.ab_delete_inv), R.string.Delete, new
    		View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				handleTopCommand(DELETE_ID);
			}
		});
    	
    	// Add the Copy Note command:
    	_tb.addButton(COPY_TEXT_ID, _a.resourceIdFromAttr(R.attr.ab_clone_inv), R.string.Copy_note_to_clipboard, new
    		View.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				handleTopCommand(COPY_TEXT_ID);
			}
		});
    }
    
    // Populate the action bar items:
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	if (!_isOnlyFragment)
    	{
    		// If there are other fragments on the screen, we do not populate the action bar.
    		return;
    	}
    	
    	MenuUtil.init(menu);
    	MenuUtil.add(EDIT_ID, R.string.Edit_Note,_a.resourceIdFromAttr(R.attr.ab_edit));
    	MenuUtil.add(DELETE_ID, R.string.Delete, _a.resourceIdFromAttr(R.attr.ab_delete));
    	MenuUtil.add(COPY_TEXT_ID, R.string.Copy_note_to_clipboard, _a.resourceIdFromAttr(
    		R.attr.ab_clone));
    }
    
    // Handlers for commands at the top:
    @SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public void handleTopCommand(int commandID)
    {
    	DialogInterface.OnClickListener dialogClickListener;
    	AlertDialog.Builder builder;
    	
    	if (inResizeMode())
    		return;
    	
    	switch(commandID)
    	{
    	case EDIT_ID:
    		// Create a fragment for the editor:
    		EditNoteFragment frag = new EditNoteFragment();
    		Bundle args = new Bundle();
    		args.putInt("action", EditNoteFragment.EDIT);
    		args.putLong("id", _note._id);
    		args.putBoolean("from_viewer_fragment", true);
    		args.putBoolean("is_only_fragment", _isOnlyFragment);
    		frag.setArguments(args);
    		
			if (_isOnlyFragment)
        	{
        		// No split-screen in use.  Launch the note editing Activity.
				Intent i = new Intent(_a,EditNoteActivity.class);
				i.putExtra("action", EditNoteFragment.EDIT);
				i.putExtra("id", _id);
				this.startActivity(i);
				_a.finish();
        	}
			else
			{
				// Display the note editor in this same fragment:
        		UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
        		nav.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, EditNoteFragment.FRAG_TAG + 
        			"/" + _note._id);
			}    		
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
                        // Yes clicked:
                        // We need the note's toodledo ID:
                    	NotesDbAdapter notesDB = new NotesDbAdapter();
                        UTLNote note = notesDB.getNote(_id);
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
                		if (!notesDB.deleteNote(note._id))
                		{
                			Util.popup(_a, R.string.DbModifyFailed);
                			Util.log("Could not delete note from database.");
                			return;
                		}
                		
                		// Refresh the display:
                		if (!_isOnlyFragment)
                		{
	                		// This must be wrapped in a UtlNavDrawerActivity.  Remove this
							// fragment and display a message saying the user can tap on 
							// another note to see its info.
							UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
							NoteListFragment listFrag = (NoteListFragment)nav.getFragmentByType(
							   UtlNavDrawerActivity.FRAG_LIST);
							if (listFrag!=null)
							   listFrag.handleDeletion();
                		}
                		else
                		{
                			// Note is deleted.  Don't keep the Activity around.
                			_a.finish();
                		}
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
            builder.setTitle(_note.title);
            builder.show();
    		return;
    			
    	case COPY_TEXT_ID:
    		String text = _note.note;
    		
    		int sdk = android.os.Build.VERSION.SDK_INT;
            if (sdk < 11) 
            {
				android.text.ClipboardManager clipboard = (android.text.ClipboardManager)_a
                    .getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(text);
            } 
            else 
            {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) _a
                    .getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText(_res.getString(
                    R.string.Note), text);
                clipboard.setPrimaryClip(clip);
            }
            
            Util.popup(_a,getString(R.string.Note_copied_to_clipboard));
    	}
    }
    
    // Handlers for an options menu choices:
    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
    	handleTopCommand(item.getItemId());
    	
    	return true;
    }
    
    // Refresh the note list (needed when the note changes and a split screen view is in use):
    private void refreshNoteList()
    {
    	NoteList tl = (NoteList)_a;
    	tl.handleNoteChange();
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
    
    // If we return here after leaving, we must refresh the data:
    @Override
    public void onResume()
    {
        super.onResume();
        refreshDisplay();
        
        // Run a sync if needed:
        Util.doMinimalSync(_a);
    }

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		// This never handles keys.
		return false;
	}
}
