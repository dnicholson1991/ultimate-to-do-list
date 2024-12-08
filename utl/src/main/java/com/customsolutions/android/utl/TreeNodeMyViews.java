package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.Intent;
import android.database.Cursor;
import androidx.fragment.app.Fragment;
import android.view.View;

public class TreeNodeMyViews extends TreeNode
{
	public TreeNodeMyViews(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		ViewsDbAdapter viewsDB = new ViewsDbAdapter();
		Cursor c = viewsDB.getViewsByLevel("my_views");
		c.moveToPosition(-1);
		ArrayList<TreeNode> result = new ArrayList<TreeNode>();
		while (c.moveToNext())
		{
			result.add(new TreeNodeTaskList(_a,ViewNames.MY_VIEWS,Util.cString(c, "view_name"),
				_a.getString(R.string.MyViews)+" / "+Util.cString(c, "view_name"),
				true, Util.cString(c, "view_name")));
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
		_title.setText(_a.getString(R.string.MyViews));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.VISIBLE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_my_views)));
		
		_button.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(_a,GenericListActivity.class);
				i.putExtra("type", GenericListActivity.TYPE_MY_VIEWS);
				Fragment frag = new EditViewsFragment();
				_a.launchFragmentOrActivity(frag, EditViewsFragment.FRAG_TAG, i,
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
		return _a.getString(R.string.MyViews);
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
		return "my_views";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_my_views_inv)));
			_button.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_edit_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_my_views)));
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
