package com.customsolutions.android.utl;

import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class TreeNodeTaskList extends TreeNode
{
	// Should the list in the tree view be indented. Set to true for folders, context,
	// custom views, etc.  Set to false for All Tasks, Hotlist, etc.
	private boolean _isIndented;
	
	// The the name of the folder, context, goal, etc.
	private String _itemName;
	
	// Info the view this represents:
	private String _topLevel;
	private String _viewName;
	private String _header;
	private long _viewID;	
    
	private Cursor _viewCursor;

	private int _iconResourceID;
	private int _iconInvertedResourceID;

    private boolean _atTopLevel;
	
	public TreeNodeTaskList(UtlNavDrawerActivity c, String topLevel, String viewName, String header, boolean
		isIndented, String itemName)
	{
		super(c);

		_isExpanded = false;
		_topLevel = topLevel;
		_viewName = viewName;
		_header = header;
		_isIndented = isIndented;
		_itemName = itemName;
        _atTopLevel = false;

		// Get the view ID based on the top level and view name passed in:
		_viewCursor = (new ViewsDbAdapter()).getView(_topLevel,_viewName);
        if (!_viewCursor.moveToFirst())
        {
            Util.log("View is not defined in TreeNodeTaskList().");
            return;
        }
        _viewID = Util.cLong(_viewCursor, "_id");
        _viewCursor.close(); 
	}
	
	@Override
	public String getTitle()
	{
		// If the header is subdivided using "/" characters, we need to just return the 
		// the text after the last "/":
		if (_header.lastIndexOf("/ ")>-1)
		{
			return _header.substring(_header.lastIndexOf("/ ")+2);
		}
		return _header;
	}

    /** Override normal indentation and display at the top level */
    public void showAtTopLevel()
    {
        _atTopLevel = true;
    }

	@Override
	public View getView()
	{
		parseXmlLayout();
        if (_atTopLevel)
        {
            _spacer2.setVisibility(View.GONE);
            _spacer1.setVisibility(View.GONE);
        }
		else if (_isIndented)
		{
			_spacer2.setVisibility(View.VISIBLE);
			_spacer1.setVisibility(View.VISIBLE);
		}
		else
		{
			_spacer2.setVisibility(View.GONE);
			_spacer1.setVisibility(View.VISIBLE);
		}
		_title.setText(_itemName);
		_expander.setVisibility(View.GONE);
		_counter.setVisibility(View.VISIBLE);
		_button.setVisibility(View.GONE);
		
		// The icon we're displaying varies by view:
		_iconResourceID = R.attr.nav_all_tasks;
		_iconInvertedResourceID = R.attr.nav_all_tasks_inv;
		if (_topLevel.equals(ViewNames.ALL_TASKS))
		{
			_iconResourceID = R.attr.nav_all_tasks;
			_iconInvertedResourceID = R.attr.nav_all_tasks_inv;
		}
		else if (_topLevel.equals(ViewNames.HOTLIST))
		{
			_iconResourceID = R.attr.nav_hotlist;
			_iconInvertedResourceID = R.attr.nav_hotlist_inv;
		}
		else if (_topLevel.equals(ViewNames.MY_VIEWS))
		{
			_iconResourceID = R.attr.nav_my_view;
			_iconInvertedResourceID = R.attr.nav_my_view_inv;
		}
		else if (_topLevel.equals(ViewNames.DUE_TODAY_TOMORROW))
		{
			_iconResourceID = R.attr.nav_due_today_tomorrow;
			_iconInvertedResourceID = R.attr.nav_due_today_tomorrow_inv;
		}
		else if (_topLevel.equals(ViewNames.OVERDUE))
		{
			_iconResourceID = R.attr.nav_overdue;
			_iconInvertedResourceID = R.attr.nav_overdue_inv;
		}
		else if (_topLevel.equals(ViewNames.STARRED))
		{
			_iconResourceID = R.attr.nav_starred;
			_iconInvertedResourceID = R.attr.nav_starred_inv;
		}
		else if (_topLevel.equals(ViewNames.RECENTLY_COMPLETED))
		{
			_iconResourceID = R.attr.nav_recently_completed;
			_iconInvertedResourceID = R.attr.nav_recently_completed_inv;
		}
		else if (_topLevel.equals(ViewNames.FOLDERS))
		{
			_iconResourceID = R.attr.nav_folder;
			_iconInvertedResourceID = R.attr.nav_folder_inv;
		}
		else if (_topLevel.equals(ViewNames.CONTEXTS))
		{
			_iconResourceID = R.attr.nav_context;
			_iconInvertedResourceID = R.attr.nav_context_inv;
		}
		else if (_topLevel.equals(ViewNames.TAGS))
		{
			_iconResourceID = R.attr.nav_tag;
			_iconInvertedResourceID = R.attr.nav_tag_inv;
		}
		else if (_topLevel.equals(ViewNames.GOALS))
		{
			_iconResourceID = R.attr.nav_goal;
			_iconInvertedResourceID = R.attr.nav_goal_inv;
		}
		else if (_topLevel.equals(ViewNames.LOCATIONS))
		{
			_iconResourceID = R.attr.nav_location;
			_iconInvertedResourceID = R.attr.nav_location_inv;
		}
		else if (_topLevel.equals(ViewNames.BY_STATUS))
		{
			_iconResourceID = R.attr.nav_by_status;
			_iconInvertedResourceID = R.attr.nav_by_status_inv;
			if (_viewName.equals("0"))
			{
				_iconResourceID = R.attr.nav_status_none;
				_iconInvertedResourceID = R.attr.nav_status_none_inv;
			}
			if (_viewName.equals("1"))
			{
				_iconResourceID = R.attr.nav_status_next_action;
				_iconInvertedResourceID = R.attr.nav_status_next_action_inv;
			}
			if (_viewName.equals("2"))
			{
				_iconResourceID = R.attr.nav_status_active;
				_iconInvertedResourceID = R.attr.nav_status_active_inv;
			}
			if (_viewName.equals("3"))
			{
				_iconResourceID = R.attr.nav_status_planning;
				_iconInvertedResourceID = R.attr.nav_status_planning_inv;
			}
			if (_viewName.equals("4"))
			{
				_iconResourceID = R.attr.nav_status_delegated;
				_iconInvertedResourceID = R.attr.nav_status_delegated_inv;
			}
			if (_viewName.equals("5"))
			{
				_iconResourceID = R.attr.nav_status_waiting;
				_iconInvertedResourceID = R.attr.nav_status_waiting_inv;
			}
			if (_viewName.equals("6"))
			{
				_iconResourceID = R.attr.nav_status_hold;
				_iconInvertedResourceID = R.attr.nav_status_hold_inv;
			}
			if (_viewName.equals("7"))
			{
				_iconResourceID = R.attr.nav_status_postponed;
				_iconInvertedResourceID = R.attr.nav_status_postponed_inv;
			}
			if (_viewName.equals("8"))
			{
				_iconResourceID = R.attr.nav_status_someday;
				_iconInvertedResourceID = R.attr.nav_status_someday_inv;
			}
			if (_viewName.equals("9"))
			{
				_iconResourceID = R.attr.nav_status_canceled;
				_iconInvertedResourceID = R.attr.nav_status_canceled_inv;
			}
			if (_viewName.equals("10"))
			{
				_iconResourceID = R.attr.nav_status_reference;
				_iconInvertedResourceID = R.attr.nav_status_reference_inv;
			}
		}
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			_iconResourceID)));
		
		if (_topLevel.equals("my_views"))
		{
			if (_isIndented)
			{
				_title.setContentDescription("custom_view:"+Long.valueOf(_viewID).toString());
			}
			else
			{
				_title.setContentDescription("custom_view_at_top:"+Long.valueOf(_viewID).
					toString());
			}
		}
		
		// After tapping on the hit area, display the appropriate task list:
		_layout.findViewById(R.id.nav_drawer_hit_area).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Create a new fragment for the new task list:
				TaskListFragment frag = new TaskListFragment();
				Bundle b = new Bundle();
				b.putString("title", _header);
				b.putString("top_level", _topLevel);
				b.putString("view_name", _viewName);
				frag.setArguments(b);
				
				// Also create an Intent.  The Intent will be used if we need to start a new
				// Activity.
				Intent i = new Intent(_a,TaskList.class);
				i.putExtra("title", _header);
	            i.putExtra("top_level", _topLevel);
	            i.putExtra("view_name", _viewName);
	    		// i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    		
	    		_a.launchFragmentOrActivity(frag, TaskListFragment.FRAG_TAG + "/" + 
	    			_topLevel + "/" + _viewName, i, TaskList.class.getName(), true);
				
				// The navigation drawer needs to be manually closed.
				_a.closeDrawer();
				
				NavDrawerFragment._selectedNodeIndex = TreeNodeTaskList.this._index;
			}
		});
		
		return (_layout);
	}

	@Override
	public TextView getCounterTextView()
	{
		parseXmlLayout();
		return _counter;
	}
	
	@Override
	public String getCounterQuery()
	{
		return Util.getTaskCountQuery(_viewID);
	}

	@Override
	public String getUniqueID()
	{
		return _topLevel+"/"+_viewName+"/"+(_isIndented ? "1" : "0");
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				_iconInvertedResourceID)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				_iconResourceID)));
		}
	}
	
	// Determine whether we should unhighlight this node when the user moves his finger off.  It
	// depends on the split-screen mode:
	@Override
	public boolean unhighlightOnRelease()
	{
		switch (_a.getSplitScreenOption())
		{
		case Util.SS_NONE:
			return true;
		case Util.SS_2_PANE_LIST_DETAILS:
			return true;
		case Util.SS_2_PANE_NAV_LIST:
			return false;
		case Util.SS_3_PANE:
			return false;
		default:
			return true;
		}
	}
}
