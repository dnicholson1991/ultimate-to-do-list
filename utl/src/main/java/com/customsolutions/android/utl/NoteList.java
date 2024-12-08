package com.customsolutions.android.utl;

import java.util.zip.CRC32;

import android.os.Bundle;
import androidx.core.view.GravityCompat;

/** This activity displays a list of notes, and manages fragments for the navigation drawer and detail
 * views.  The Intent needs one of the following arguments:<br>
 * folder_id: The ID of the folder we are displaying notes for.  This can be zero to display notes with
 *     no folder.<br>
 * sql: an SQL where clause to get the notes list.
 * 
 * @author Nicholson
 *
 */
public class NoteList extends UtlNavDrawerActivity
{
	private boolean _usingSQL;
	private long _folderID;
	private String _sql;
		
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
		// Create the NoteListFragment that will be placed:
		NoteListFragment nl = new NoteListFragment();
		
		// Look for arguments in the savedInstanceState first, followed by the Intent extras:
		_usingSQL = false;
		boolean hasArguments = false;
		if (savedInstanceState!=null)
		{
			if (savedInstanceState.containsKey("sql"))
			{
				_usingSQL = true;
				_sql = savedInstanceState.getString("sql");
				hasArguments = true;
			}
			else if (savedInstanceState.containsKey("folder_id"))
			{
				_folderID = savedInstanceState.getLong("folder_id");
				hasArguments = true;
			}
		}
		if (!hasArguments)
		{
			Bundle extras = getIntent().getExtras();
			if (extras.containsKey("sql"))
			{
				_usingSQL = true;
				_sql = extras.getString("sql");
			}
			else if (extras.containsKey("folder_id"))
			{
				_folderID = extras.getLong("folder_id");
			}
			else
			{
				Util.log("No valid arguments passed to NoteList.java.");
				finish();
				return;
			}
		}
		
		// Get a unique tag to associate with the fragment:
		String tag;
		if (!_usingSQL)
		{
			tag = NoteListFragment.FRAG_TAG + "/" + _folderID;
		}
		else
		{
			CRC32 crc = new CRC32();
			crc.update(_sql.getBytes());
			tag = NoteListFragment.FRAG_TAG + "/" + Long.toHexString(crc.getValue());
		}
		
		// Set arguments for the fragment:
		Bundle args = new Bundle();
		if (_usingSQL)
			args.putString("sql", _sql);
		else
			args.putLong("folder_id", _folderID);
		nl.setArguments(args);
		
		// Place the Fragment:
		placeFragment(UtlNavDrawerActivity.FRAG_LIST,nl,tag);

        // Check to see if any online accounts need to be signed into:
        Util.accountSignInCheck(this);
	}
	
	// On an orientation change, save the arguments in the savedInstanceState:
	@Override
	public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	
    	NoteListFragment nl = (NoteListFragment)getFragmentByType(FRAG_LIST);
    	if (nl._usingSQL)
    		b.putString("sql", nl._sqlQuery);
    	else
    		b.putLong("folder_id",nl._folderID);
    }
	
	/** Switch to a new note list.  Call this from other fragments when the list needs to change. 
	 * @param usingSQL
	 * @param sql
	 * @param folderID
	 */
	public void changeNoteList(boolean usingSQL, String sql, long folderID)
	{
		String tag;
		NoteListFragment nl = new NoteListFragment();
		Bundle args = new Bundle();
		if (!usingSQL)
		{
			tag = NoteListFragment.FRAG_TAG + "/" + folderID;
			args.putLong("folder_id", folderID);
		}
		else
		{
			CRC32 crc = new CRC32();
			crc.update(sql.getBytes());
			tag = NoteListFragment.FRAG_TAG + "/" + Long.toHexString(crc.getValue());
			args.putString("sql", sql);
		}
		nl.setArguments(args);
		placeFragment(UtlNavDrawerActivity.FRAG_LIST,nl,tag);
		
		// We also need to clear the detail view and display a message saying the user can tap on a note
    	// to see its details.
    	showDetailPaneMessage(getString(R.string.Select_a_note_to_display));
	}
	
	/** Handle a change in a note when the note is altered from the viewer or editor. */
	public void handleNoteChange()
	{
		NoteListFragment nl = (NoteListFragment)getFragmentByType(FRAG_LIST);
		if (nl!=null)
		{
			// If we're in multi-select mode, then get out of it.
			if (nl._mode==TaskListFragment.MODE_MULTI_SELECT)
				nl.handleModeChange(TaskListFragment.MODE_NORMAL);  // Also refreshes.
			else
			{
				nl.saveCurrentPosition();
				nl.refreshData();
				nl.restorePosition();
			}
		}
	}
}
