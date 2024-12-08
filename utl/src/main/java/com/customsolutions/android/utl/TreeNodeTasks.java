package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeTasks extends TreeNode
{
	public TreeNodeTasks(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		ArrayList<TreeNode> result = new ArrayList<TreeNode>();
		SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
		
		if (settings.getBoolean(PrefNames.SHOW_ALL_TASKS, true))
		{
			result.add(new TreeNodeTaskList(_a,ViewNames.ALL_TASKS,"",_a.getString(R.string.
				AllTasks),false,_a.getString(R.string.AllTasks)));
		}
		if (settings.getBoolean(PrefNames.SHOW_HOTLIST,true))
		{
			result.add(new TreeNodeTaskList(_a,ViewNames.HOTLIST,"",_a.getString(R.string.
				Hotlist),false,_a.getString(R.string.Hotlist)));
		}
    	if (settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		if (settings.getBoolean(PrefNames.SHOW_DUE_TODAY_TOMORROW, true))
    		{
    			result.add(new TreeNodeTaskList(_a,ViewNames.DUE_TODAY_TOMORROW,"",_a.getString(R.string.
    				DueTodayTomorrow),false,_a.getString(R.string.DueTodayTomorrow)));
    		}
    		if (settings.getBoolean(PrefNames.SHOW_OVERDUE, true))
    		{
    			result.add(new TreeNodeTaskList(_a,ViewNames.OVERDUE,"",_a.getString(R.string.
        			Overdue),false,_a.getString(R.string.Overdue)));
    		}
    	}
    	if (settings.getBoolean(PrefNames.STAR_ENABLED, true) && settings.getBoolean(PrefNames.
    		SHOW_STARRED, true))
    	{
    		result.add(new TreeNodeTaskList(_a,ViewNames.STARRED,"",_a.getString(R.string.
    			Starred),false,_a.getString(R.string.Starred)));
    	}
    	if (settings.getBoolean(PrefNames.SHOW_RECENTLY_COMPLETED, true))
    	{
    		result.add(new TreeNodeTaskList(_a,ViewNames.RECENTLY_COMPLETED,"",_a.getString(R.string.
    			RecentlyCompleted),false,_a.getString(R.string.RecentlyCompleted)));
    	}
    	result.add(new TreeNodeMyViews(_a));
    	if (settings.getBoolean(PrefNames.STATUS_ENABLED, true))
    		result.add(new TreeNodeStatuses(_a));
    	if (settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    		result.add(new TreeNodeFolders(_a));
    	if (settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    		result.add(new TreeNodeContexts(_a));
    	if (settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    		result.add(new TreeNodeTags(_a));
    	if (settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    		result.add(new TreeNodeGoals(_a));
    	if (settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    		result.add(new TreeNodeLocations(_a));
		
		return result;
	}
	
	@Override
	public View getView()
	{
		parseXmlLayout();
		
		_spacer2.setVisibility(View.GONE);
		_spacer1.setVisibility(View.GONE);
		_title.setText(_a.getString(R.string.Tasks));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_button.setImageResource(_a.resourceIdFromAttr(R.attr.nav_add));
		_icon.setImageResource(_a.resourceIdFromAttr(R.attr.nav_all_tasks));
		
		_button.setOnClickListener(new View.OnClickListener()
		{
			@SuppressLint("InlinedApi")
			@Override
			public void onClick(View v)
			{
				// Get a reference to the list fragment being displayed.  If this is a task list,
				// then call the task list's function for handling the new task button.
				Fragment listFrag = _a.getFragmentByType(UtlNavDrawerActivity.FRAG_LIST);
				if (listFrag!=null && listFrag instanceof TaskListFragment)
				{
					((TaskListFragment) listFrag).startAddingTask();
				}
				else
				{
					// Just call the new task popup activity.
					Intent newTaskIntent = new Intent(_a,EditTaskPopup.class);
					newTaskIntent.putExtra("action", EditTaskFragment.ADD);
					newTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					if (Build.VERSION.SDK_INT>=11)
						newTaskIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
					_a.startActivity(newTaskIntent);
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
		return "tasks";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_all_tasks_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_all_tasks)));
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
