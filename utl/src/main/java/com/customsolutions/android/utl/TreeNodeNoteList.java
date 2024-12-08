package com.customsolutions.android.utl;

// A TreeNode that displays a list of notes when tapped on, for a specific folder.

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class TreeNodeNoteList extends TreeNode
{
	// The the name of the folder
	private String _folderName;
	
	// The ID of the note folder (0 for none)
	private long _folderID;
	    
	public TreeNodeNoteList(UtlNavDrawerActivity c, long folderID, String folderName)
	{
		super(c);

		_folderName = folderName;
		_folderID = folderID;		
	}
	
	@Override
	public String getTitle()
	{
		return _folderName;
	}
	
	@Override
	public View getView()
	{
		parseXmlLayout();
		_spacer2.setVisibility(View.GONE);
		_spacer1.setVisibility(View.VISIBLE);
		_title.setText(_folderName);
		_expander.setVisibility(View.GONE);
		_counter.setVisibility(View.VISIBLE);
		_button.setVisibility(View.GONE);		
		_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
			R.attr.nav_folder)));
		
		// Display the note list after tapping on the folder name:
		_layout.findViewById(R.id.nav_drawer_hit_area).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Create a fragment for the note list:
				NoteListFragment frag = new NoteListFragment();
				Bundle b = new Bundle();
				b.putLong("folder_id", _folderID);
				frag.setArguments(b);
				
				// Also create an Intent.  The Intent will be used if we need to start a new
				// Activity.
				Intent i = new Intent(_a,NoteList.class);
				i.putExtra("folder_id", _folderID);
	    		
	    		_a.launchFragmentOrActivity(frag, NoteListFragment.FRAG_TAG + "/" + _folderID,
	    			i, NoteList.class.getName(),true);
				
	    		// The navigation drawer needs to be manually closed.
				_a.closeDrawer();
				
				NavDrawerFragment._selectedNodeIndex = TreeNodeNoteList.this._index;
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
		if (_folderID!=-1)
			return "select count(*) from notes where folder_id="+_folderID;
		else
			return "select count(*) from notes";
	}
	
	@Override
	public String getUniqueID()
	{
		return "note folder "+_folderID;
	}
	
	@Override
	public void setIconInversion(boolean isInverted)
	{
		if (isInverted)
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_folder_inv)));
		}
		else
		{
			_icon.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(_a, 
				R.attr.nav_folder)));
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
