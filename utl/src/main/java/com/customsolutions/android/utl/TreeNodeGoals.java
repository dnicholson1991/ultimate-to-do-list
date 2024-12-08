package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeGoals extends TreeNode
{
	public TreeNodeGoals(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		GoalsDbAdapter goalsDB = new GoalsDbAdapter();
		SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
		Cursor c;
		if (!settings.getBoolean(PrefNames.SHOW_ARCHIVED_GOALS, false))
			c = goalsDB.getAllGoalsNoCase();
		else
			c = goalsDB.queryGoals(null, "account_id,level,lower(title)");
    	c.moveToPosition(-1);
		
		// We need to know how many accounts we have, since this affects the display:
		AccountsDbAdapter accountsDB = new AccountsDbAdapter();
    	Cursor c2 = accountsDB.getAllAccounts();
    	int numAccounts = c2.getCount();
    	c2.close();
    	
    	// Populate the result Array:
    	ArrayList<TreeNode> result = new ArrayList<TreeNode>();
    	result.add(new TreeNodeTaskList(_a,ViewNames.GOALS,"0",_a.getString(
    		R.string.No_Goal),true, _a.getString(R.string.No_Goal)));
    	while (c.moveToNext())
    	{
    		String nameToDisplay = Util.cString(c, "title");
    		if (numAccounts>1)
    		{
    			UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    			if (a!=null)
    				nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
    		}
    		result.add(new TreeNodeTaskList(_a,ViewNames.GOALS,Long.valueOf(Util.cLong(c, "_id")).
    			toString(),_a.getString(R.string.Goals)+" / "+Util.cString(c, "title"),
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
		_title.setText(_a.getString(R.string.Goals));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_goals)));
		
		_button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(_a,GenericListActivity.class);
				i.putExtra("type", GenericListActivity.TYPE_GOALS);
				Fragment frag = new EditGoalsFragment();
				_a.launchFragmentOrActivity(frag, EditGoalsFragment.FRAG_TAG, i,
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
		return _a.getString(R.string.Goals);
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
		return "goals";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_goals_inv)));
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_goals)));
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
