package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeFolders extends TreeNode
{
	public TreeNodeFolders(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		// To get the best sort order, we count the accounts in the system.
		// Unsynced account should be sorted by name, since we don't have
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
			if (numToodledo>0 && numOther==0)
				c = foldersDB.getFoldersByOrder();
			else 
				c = foldersDB.getFoldersByNameNoCase();
		}
		c.moveToPosition(-1);
		
		// We need to know how many accounts we have, since this affects the display:
    	Cursor c2 = accountsDB.getAllAccounts();
    	int numAccounts = c2.getCount();
    	c2.close();
    	
    	// Populate the result Array, placing a "No Folder" item at the top.
    	ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    	result.add(new TreeNodeTaskList(_a,ViewNames.FOLDERS,"0",_a.getString(R.string.
    		No_Folder),true, _a.getString(R.string.No_Folder)));
    	while (c.moveToNext())
    	{
    		String nameToDisplay = Util.cString(c, "title");
    		if (numAccounts>1)
    		{
    			UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    			if (a!=null)
    				nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
    		}
    		result.add(new TreeNodeTaskList(_a,ViewNames.FOLDERS,Long.valueOf(Util.cLong(c, "_id")).
    			toString(),_a.getString(R.string.Folders)+" / "+Util.cString(c, "title"),
    			true, nameToDisplay));
    	}
    	c.close();
    	return result;
	}

	@Override
	public View getView()
	{
		parseXmlLayout();
		
		_spacer2.setVisibility(View.GONE);
		_spacer1.setVisibility(View.VISIBLE);
		_title.setText(_a.getString(R.string.Folders));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_folders)));
		
		_button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(_a,GenericListActivity.class);
				i.putExtra("type", GenericListActivity.TYPE_FOLDERS);
				Fragment frag = new EditFoldersFragment();
				_a.launchFragmentOrActivity(frag, EditFoldersFragment.FRAG_TAG, i,
					GenericListActivity.class.getName(),false);
				
				// The navigation drawer needs to be manually closed.
				_a.closeDrawer();
				
				NavDrawerFragment._selectedNodeIndex = -1;
			}
		});
		
		return (_layout);
	}

	@Override
	public String getTitle()
	{
		// TODO Auto-generated method stub
		return _a.getString(R.string.Folders);
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
		return "folders";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_folders_inv)));
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_folders)));
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit)));
		}
	}

	
	@Override
	public void setButtonInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit_inv)));
		}
		else
		{
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit)));
		}
	}
}
