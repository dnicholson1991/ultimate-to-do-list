package com.customsolutions.android.utl;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.View;

public class TreeNodeTaskMap extends TreeNode
{

	public TreeNodeTaskMap(UtlNavDrawerActivity c)
	{
		super(c);
	}

	@Override
	public View getView()
	{
		parseXmlLayout();
		
		_spacer2.setVisibility(View.VISIBLE);
		_spacer1.setVisibility(View.VISIBLE);
		_title.setText(_a.getString(R.string.Show_Map));
		_expander.setVisibility(View.GONE);
		_counter.setVisibility(View.GONE);
		_button.setVisibility(View.GONE);
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_show_map)));
		
		// Open the task map after tapping on the hit area:
		_layout.findViewById(R.id.nav_drawer_hit_area).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				try
				{
					Intent i = new Intent(_a,TaskMap.class);
					_a.startActivity(i);
					
					NavDrawerFragment._selectedNodeIndex = -1;
				}
				catch (NoClassDefFoundError e)
				{
					Util.popup(_a, R.string.Maps_Not_Installed);
				}
				catch (ActivityNotFoundException e)
				{
					Util.popup(_a, R.string.Maps_Not_Installed);
				}
			}
		});
		
		return (_layout);
	}

	@Override
	public String getTitle()
	{
		return _a.getString(R.string.Show_Map);
	}

	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_show_map_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_show_map)));
		}
	}

	@Override
	public String getUniqueID()
	{
		return "task_map";
	}
}
