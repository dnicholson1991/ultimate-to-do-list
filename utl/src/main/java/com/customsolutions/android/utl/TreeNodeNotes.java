package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeNotes extends TreeNode
{
	private SharedPreferences _settings;
	
	public TreeNodeNotes(UtlNavDrawerActivity c)
	{
		super(c);
		_settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		ArrayList<TreeNode> result = new ArrayList<TreeNode>();
		
		// If note folders are not enabled, then we have a single "folder" that holds everything.
		if (!_settings.getBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true))
		{
			result.add(new TreeNodeNoteList(_a,-1,_a.getString(R.string.All_Notes)));
			return result;
		}
		
		// To get the best sort order, we count the accounts in the system.
		// Unsynced accounts should be sorted by name, since we don't have
		// the ability to reorder folders.  Synced accounts should be sorted
		// by order, since folders can be rearranged in Toodledo.
		AccountsDbAdapter accountsDB = new AccountsDbAdapter();
		Cursor c = accountsDB.getAllAccounts();
		c.moveToPosition(-1);
		int numToodledo = 0;
		int numOther = 0;
		while (c.moveToNext())
		{
			UTLAccount a = accountsDB.getAccount(Util.cLong(c, "_id"));
			if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
				numToodledo++;
			else
				numOther++;
   		}
		c.close();
		FoldersDbAdapter foldersDB = new FoldersDbAdapter();
		SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
		if (settings.getBoolean(PrefNames.SHOW_ARCHIVED_FOLDERS, false))
		{
			c = foldersDB.queryFolders(null, "account_id,lower(title)");
		}
		else
		{
			if (numToodledo > 0 && numOther == 0)
				c = foldersDB.getFoldersByOrder();
			else
				c = foldersDB.getFoldersByNameNoCase();
		}
		c.moveToPosition(-1);
		
		// We need to know how many accounts we have, since this affects the display:
    	Cursor c2 = accountsDB.getAllAccounts();
    	int numAccounts = c2.getCount();
    	c2.close();
    	
    	// Insert the "no folder" item:
    	result.add(new TreeNodeNoteList(_a,0,_a.getString(R.string.No_Folder)));
    	
    	// Populate the result Array:
    	while (c.moveToNext())
    	{
    		String nameToDisplay = Util.cString(c, "title");
    		if (numAccounts>1)
    		{
    			UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    			if (a!=null)
    				nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
    		}
    		result.add(new TreeNodeNoteList(_a,Util.cLong(c, "_id"),nameToDisplay));
    	}
    	c.close();
		
		return result;
	}
	
	@Override
	public View getView()
	{
		parseXmlLayout();
		
		_spacer2.setVisibility(View.GONE);
		_spacer1.setVisibility(View.GONE);
		_title.setText(_a.getString(R.string.Notes));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_button.setImageResource(_a.resourceIdFromAttr(R.attr.nav_add));
		_icon.setImageResource(_a.resourceIdFromAttr(R.attr.nav_notes));

		_button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Get a reference to the list fragment being displayed.  If this is a note list,
				// then call the note list's function for handling the new note button.
				Fragment listFrag = _a.getFragmentByType(UtlNavDrawerActivity.FRAG_LIST);
				if (listFrag!=null && listFrag instanceof NoteListFragment)
				{
					((NoteListFragment) listFrag).startAddingNote();
				}
				else
				{
					// Just call the new note popup activity:
					Intent i = new Intent(_a,EditNotePopup.class);
					i.putExtra("action", EditNoteFragment.ADD);
					_a.startActivity(i);
				}
				_a.closeNavDrawer();
			}
		});
		return (_layout);
	}

	@Override
	public String getTitle()
	{
		return _a.getString(R.string.Tasks);
	}

	@Override
	public View getExpanderView()
	{
		parseXmlLayout();
		return _layout.findViewById(R.id.nav_drawer_hit_area);
	}
	
	@Override
	public String getUniqueID()
	{
		return "notes";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_notes_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_notes)));
		}
	}

	@Override
	public void setButtonInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_add_inv)));
		}
		else
		{
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_add)));
		}
	}
}
