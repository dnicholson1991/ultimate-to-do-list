package com.customsolutions.android.utl;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.MenuItem;

/** Displays a simple list view.  Pass in a bundle with the "type".  The value is an integer defined
 * here. */
public class GenericListActivity extends UtlNavDrawerActivity
{
	// Available types:
	public static final int TYPE_FOLDERS = 1;
	public static final int TYPE_CONTEXTS = 2;
	public static final int TYPE_GOALS = 3;
	public static final int TYPE_TAGS = 4;
	public static final int TYPE_LOCATIONS = 5;
	public static final int TYPE_MY_VIEWS = 6;
	
	private int _type;
		
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
		// Verify we have the required extras:
		Bundle extras = getIntent().getExtras();
		if (extras==null)
		{
			Util.log("Missing extras in GenericListActivity.java.");
			finish();
			return;
		}
		if (!extras.containsKey("type"))
		{
			Util.log("Missing 'type' key in GenericListActivity.java.");
			finish();
			return;
		}
		
		// Get the type of fragment we need to create.  It will be in the savedInstanceState if we
		// just rotated the screen.
		if (savedInstanceState!=null && savedInstanceState.containsKey("type"))
			_type = savedInstanceState.getInt("type");
		else
			_type = extras.getInt("type");
		
		// Create the correct Fragment instance based on the type passed in:
		Fragment frag = null;
		String tag = "";
		switch(_type)
		{
		case TYPE_FOLDERS:
			frag = new EditFoldersFragment();
			tag = EditFoldersFragment.FRAG_TAG;
			break;
			
		case TYPE_CONTEXTS:
			frag = new EditContextsFragment();
			tag = EditContextsFragment.FRAG_TAG;
			break;
			
		case TYPE_GOALS:
			frag = new EditGoalsFragment();
			tag = EditGoalsFragment.FRAG_TAG;
			break;
			
		case TYPE_TAGS:
			frag = new EditTagsFragment();
			tag = EditTagFragment.FRAG_TAG;
			break;
			
		case TYPE_LOCATIONS:
			frag = new EditLocationsFragment();
			tag = EditLocationsFragment.FRAG_TAG;
			break;
			
		default:
			// Assume it's my views
			frag = new EditViewsFragment();
			tag = EditViewsFragment.FRAG_TAG;
			break;
			
		}
		
		// The instance of UtlNavDrawerActivity knows where to place the task list fragment:
		placeFragment(UtlNavDrawerActivity.FRAG_LIST,frag,tag);
		
		if (getSplitScreenOption()!=Util.SS_NONE && getSplitScreenOption()!=Util.SS_2_PANE_LIST_DETAILS)
		{
			// The navigation drawer is constantly on the screen, so the "home" button functions as 
			// a back button.
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	Intent i;
    	
    	switch (item.getItemId())
    	{
    	case android.R.id.home:
    		if (getSplitScreenOption()!=Util.SS_NONE && getSplitScreenOption()!=Util.SS_2_PANE_LIST_DETAILS)
    		{
	    		// The navigation drawer is constantly on the screen, so the "home" button functions
    			// as a back button.
	    		finish();
	    		return true;
    		}
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
	
	@Override
	public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	
    	// Save the fragment type:
    	b.putInt("type", _type);
    }
	
	/** Refresh the list */
	public void refreshList()
	{
		GenericListFragment frag = (GenericListFragment)getFragmentByType(UtlNavDrawerActivity.FRAG_LIST);
		frag.refreshData();
	}
}
