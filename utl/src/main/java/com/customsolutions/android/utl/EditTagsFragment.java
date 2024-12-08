package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.core.view.MenuItemCompat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** This fragment displays a list of recently used tags for editing. */
public class EditTagsFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditTagsFragment";
    
    // Constants for sort order:
    public static final int SORT_BY_NAME = 1;
	public static final int SORT_BY_USAGE = 2;
	
    private CurrentTagsDbAdapter _tagsDB;
    private Cursor _c;
    
    /** The currently selected item ID */
	private long _selectedItemID = -1;
	
	/** Temporary storage of tag name: */
	private String _tempTagName = "";
	
	// The current sort order:
	private int _sortOrder;
	
    // A progress dialog, for operations that may take some time:
	private ProgressDialog _progressDialog;

	@Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditTagsFragment");
    	
    	_tagsDB = new CurrentTagsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Recently_Used_Tags);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_tags));
    	
    	// Get the current sort order:
    	_sortOrder = _settings.getInt(PrefNames.TAG_LIST_SORT_ORDER, SORT_BY_NAME);
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_Tags_Defined);
    	
    	// Get the saved item ID, if it was passed in:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("selected_item_id"))
    		_selectedItemID = savedInstanceState.getLong("selected_item_id");
    	else
    		_a.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));

    	// Tags are only added as tasks are created or edited, so we hide the floating action
		// button.
		_a.findViewById(R.id.generic_list_fab).setVisibility(View.GONE);
    }
	
	@Override
	public void refreshData()
	{
		super.refreshData();
		
		// Query the database to get tag information:
		if (_c!=null && !_c.isClosed())
    		_c.close();
		if (_sortOrder==SORT_BY_NAME)
			_c = _tagsDB.getTags();
		else
			_c = _tagsDB.getTagsByUsage();
		
		// This array lists the columns we're interested in:
    	String from[] = new String[] {"name"};
    	
    	// The IDs of views that are affected by the columns:
    	int to[] = new int[] {R.id.generic_list_row_container};
    	
    	// Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(_a, R.layout.generic_list_row,
        	_c, from, to);
        
        // This function binds data in the Cursor to the Views we're displaying:
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
        {
        	public boolean setViewValue(View view, Cursor c, int columnIndex)
            {
        		// Get references to the views we need to work with:
        		ViewGroup row = (ViewGroup)view;
        		TextView name = (TextView)view.findViewById(R.id.generic_list_row_text);
        		ImageButton deleteButton = (ImageButton)view.findViewById(R.id.generic_list_row_delete);
        		
        		// Display the name:
        		String itemName = Util.cString(c,"name");
        		name.setText(itemName);
        		
        		// Place tags, so that the handler functions know what ID has been tapped on.
        		deleteButton.setTag(itemName);
        		row.setTag(Long.valueOf(Util.cLong(c, "_id")));
        		
        		// Highlight the row if it's selected:
        		if (!_a.useNewActivityForDetails())
        		{
	        		if (_selectedItemID==Util.cLong(c, "_id"))
	        			highlightRow(row,_selectedItemID);
	        		else
	        			unhighlightRow(row);
        		}
        		
                // Handler for the delete button:
                deleteButton.setOnClickListener(new View.OnClickListener()
        		{
        			@Override
        			public void onClick(View v)
        			{
        				if (!_a.inResizeMode())
        				{
        					// Get the name of the corresponding tag:
        					_tempTagName = (String) v.getTag();
        					
        					// Create a dialog with the deletion options:
        					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
        		    		builder.setItems(new String[] {_a.getString(R.string.
        		    			Tag_Delete_Option_1),_a.getString(R.string.Tag_Delete_Option_2),
        		    			_a.getString(R.string.Cancel)}, new DialogInterface.OnClickListener()
        					{
        						
        						@Override
        						public void onClick(DialogInterface dialog, int which)
        						{
        							dialog.dismiss();
        							if (which==0)
        							{
        								// Delete from this list only:
        								performDelete(false);
        							}
        							else if (which==1)
        							{
        								// Delete from this list and update all tasks:
        								performDelete(true);
        							}
        						}
        					});
        		    		builder.setTitle(_tempTagName);
        		    		builder.show();
        				}
        			}
        		});
                
                // Handler for tapping on a tag's name:
                row.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						if (!_a.inResizeMode())
						{
							// Get the item's ID:
							long id = (Long)v.getTag();
							
							// The editor is either opened as a new activity or as a fragment in
							// the details pane:
							if (_a.useNewActivityForDetails())
				        	{
								// No split-screen in use.  Just start a new activity:
								Intent i = new Intent(_a,EditTagActivity.class);
								i.putExtra("mode", EditTagFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditTagFragment frag = new EditTagFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditTagFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditTagFragment.FRAG_TAG+"/"+id);
							}
						}
					}
				});
        		
        		return true;
            }
        });
        
        setListAdapter(adapter);
	}

	/** Populate the action bar menu. */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	menu.clear();
    	inflater.inflate(R.menu.tag_list, menu);
    	
    	// If we're using split-screen views, then add in an option to resize the panes:
    	if (_ssMode != Util.SS_NONE && android.os.Build.VERSION.SDK_INT>=11)
    	{
    		MenuItemCompat.setShowAsAction(menu.add(0,RESIZE_PANES_ID,0,R.string.Resize_Panes),
    			MenuItemCompat.SHOW_AS_ACTION_NEVER);
    	}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	switch (item.getItemId())
    	{
    	case R.id.menu_tag_list_toggle_sort:
    		if (_sortOrder==SORT_BY_USAGE)
    		{
    			_sortOrder = SORT_BY_NAME;
    			Util.popup(_a,R.string.Tags_Sorted_by_Name);
    		}
    		else
    		{
    			_sortOrder = SORT_BY_USAGE;
    			Util.popup(_a,R.string.Most_recent_at_top);
    		}
    		Util.updatePref(PrefNames.TAG_LIST_SORT_ORDER, _sortOrder);
    		refreshData();
    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    // Perform a delete operation. The input specifies whether or not tasks containing the
    // tag are also updated:
    @SuppressLint("NewApi")
	private void performDelete(boolean updateTasks)
    {
    	_tagsDB.removeFromRecent(_tempTagName);
    	if (updateTasks)
    	{
    		_progressDialog = ProgressDialog.show(_a, null,Util.getString(R.string.
    			Deleting_Tag), false);
    		if (Build.VERSION.SDK_INT >= 11)
    		{
    			new DeleteTagFromTasks().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_tempTagName);
    		}
    		else
    			new DeleteTagFromTasks().execute(_tempTagName);
    	}
    	else
    	{
    		refreshData();
			_a.refreshWholeNavDrawer();
    	}
    }
    
	// An AsyncTask to delete a tag from all tasks containing it:
	private class DeleteTagFromTasks extends AsyncTask<String,Void,Void>
	{
		@Override
    	protected void onPreExecute()
    	{
    		_a.lockScreenOrientation();
    	}
		
		protected Void doInBackground(String...strings)
		{
			(new TagsDbAdapter()).deleteTag(strings[0]);
			return null;
		}
		
		protected void onPostExecute(Void v)
		{
			if (_progressDialog != null && _progressDialog.isShowing())
				_progressDialog.dismiss();
			_a.unlockScreenOrientation();
			refreshData();
			_a.refreshWholeNavDrawer();
		}
	}
	
    /** Change the highlighted row. */
    private void changeHighlightedRow(ViewGroup row, long itemID)
    {
    	// Loop through all current views and unselect the currently-selected row:
    	if (_selectedItemID!=-1)
    	{
	    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
	    	int count = lv.getChildCount();
			for (int i=0; i<count; i++)
			{
				ViewGroup vg = (ViewGroup)lv.getChildAt(i);
				Long rowItemID = (Long)vg.getTag();
				if (rowItemID!=null && rowItemID==_selectedItemID)
				{
					// Unhighlight the row:
					unhighlightRow(vg);
				}
			}
    	}
    	
    	// Highlight the selected row:
    	highlightRow(row,itemID);
    }
    
    /** Unhighlight a row: */
    private void unhighlightRow(ViewGroup row)
    {
    	row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(
			R.attr.main_background_color)));
		TextView tv = (TextView)row.findViewById(R.id.generic_list_row_text);
		tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		ImageButton deleteButton = (ImageButton)row.findViewById(R.id.generic_list_row_delete);
		deleteButton.setImageResource(_a.resourceIdFromAttr(R.attr.ab_delete_inv));
    }
    
    /** Highlight a row: */
    private void highlightRow(ViewGroup row, long itemID)
    {
    	row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_bg_color)));
    	TextView tv = (TextView)row.findViewById(R.id.generic_list_row_text);
    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_text_color)));
    	ImageButton deleteButton = (ImageButton)row.findViewById(R.id.generic_list_row_delete);
		deleteButton.setImageResource(_a.resourceIdFromAttr(R.attr.delete_row_highlight));
		_selectedItemID = itemID;
    }

    /** Save some items when the orientation changes: */
    @Override
    public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	b.putLong("selected_item_id", _selectedItemID);
    }
    
    @Override
    public void onDestroy()
    {
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	super.onDestroy();
    }
}
