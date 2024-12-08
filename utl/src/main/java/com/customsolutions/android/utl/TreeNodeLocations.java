package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.Intent;
import android.database.Cursor;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeLocations extends TreeNode
{
	public TreeNodeLocations(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		LocationsDbAdapter locDB = new LocationsDbAdapter();
		Cursor c = locDB.queryLocations(null, "account_id,lower(title)");
		
		// We need to know how many accounts we have, since this affects the display:
		AccountsDbAdapter accountsDB = new AccountsDbAdapter();
    	Cursor c2 = accountsDB.getAllAccounts();
    	int numAccounts = c2.getCount();
    	c2.close();
    	
    	// Add in the task map first:
    	ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    	result.add(new TreeNodeTaskMap(_a));
    	
    	// Add in the "no location" next:
    	result.add(new TreeNodeTaskList(_a,ViewNames.LOCATIONS,"0",_a.getString(
    		R.string.No_Location), true, _a.getString(R.string.No_Location)));
    	
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
    		result.add(new TreeNodeTaskList(_a,ViewNames.LOCATIONS,Long.valueOf(Util.cLong(c, "_id")).
    			toString(),_a.getString(R.string.Locations)+" / "+Util.cString(c, "title"),
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
		_title.setText(_a.getString(R.string.Locations));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_locations)));
		
		_button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(_a,GenericListActivity.class);
				i.putExtra("type", GenericListActivity.TYPE_LOCATIONS);
				Fragment frag = new EditLocationsFragment();
				_a.launchFragmentOrActivity(frag, EditLocationsFragment.FRAG_TAG, i,
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
		return _a.getString(R.string.Locations);
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
		return "locations";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_locations_inv)));
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_locations)));
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
