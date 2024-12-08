package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.view.View;

public class TreeNodeStatuses extends TreeNode
{
	public TreeNodeStatuses(UtlNavDrawerActivity c)
	{
		super(c);
	}
	
	@Override
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		// Get all of the status names:
		String[] statusNames = _a.getResources().getStringArray(R.array.statuses);
		
		// Populate the result array:
		ArrayList<TreeNode> result = new ArrayList<TreeNode>();
		for (int i=0; i<statusNames.length; i++)
		{
			result.add(new TreeNodeTaskList(_a,ViewNames.BY_STATUS,Integer.valueOf(i).toString(),
				_a.getString(R.string.ByStatus)+" / "+statusNames[i],
				true,statusNames[i]));
		}
		return result;
	}

	@Override
	public View getView()
	{
		parseXmlLayout();
		
		_spacer2.setVisibility(View.GONE);
		_spacer1.setVisibility(View.VISIBLE);
		_title.setText(_a.getString(R.string.ByStatus));
		_expander.setVisibility(View.VISIBLE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.GONE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_by_status)));
		
		return (_layout);
	}

	@Override
	public String getTitle()
	{
		// TODO Auto-generated method stub
		return _a.getString(R.string.ByStatus);
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
		return "statuses";
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_by_status_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_by_status)));
		}
	}
}
