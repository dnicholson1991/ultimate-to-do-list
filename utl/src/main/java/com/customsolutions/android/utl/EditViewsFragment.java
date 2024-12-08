package com.customsolutions.android.utl;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** This fragment displays a list of custom views for editing. */
public class EditViewsFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditViewsFragment";
    
    private ViewsDbAdapter _viewsDB;
    private Cursor _c;
    
    /** The currently selected item ID */
	private long _selectedItemID = -1;
	
	/** Temporary storage of an item ID: */
	private long _tempItemID = -1;
	
	private EditText _newNameInput;
	
	@Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditViewssFragment");
    	
    	_viewsDB = new ViewsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Edit_My_Views);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_custom_views_defined);
    	
    	// Get the saved item ID, if it was passed in:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("selected_item_id"))
    		_selectedItemID = savedInstanceState.getLong("selected_item_id");
    	else
    		_a.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));

		// Handle the "add" button which is a floating action button:
		_a.findViewById(R.id.generic_list_fab).setOnClickListener((View v) -> {
			// Ask the user for a title:
			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
				OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which)
					{
						case DialogInterface.BUTTON_POSITIVE:
							// Yes clicked:
							String newName = _newNameInput.getText().toString().trim();
							if (newName.length()>0)
							{
								// Create a new temporary view with no rules, and some
								// default sort and display options.
								long newViewID = _viewsDB.addView(ViewNames.TEMP,newName,
									_viewsDB.getDefaultSortOrder(ViewNames.ALL_TASKS),
									DisplayOptions.getDefaultDisplayOptions(ViewNames.ALL_TASKS));
								if (newViewID==-1)
								{
									Util.popup(_a, R.string.DbInsertFailed);
									return;
								}

								// Call the activity to edit the view's rules:
								Intent i = new Intent(_a,ViewRulesList.class);
								i.putExtra("view_id", newViewID);
								startActivity(i);
							}
							break;
						case DialogInterface.BUTTON_NEGATIVE:
							// No clicked:
							break;
					}
				}
			};
			AlertDialog.Builder builder = new AlertDialog.Builder(_a);
			_newNameInput = new EditText(_a);
			_newNameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
			builder.setView(_newNameInput);
			builder.setTitle(Util.getString(R.string.Enter_New_View_Name));
			builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
			builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
			builder.show();
		});
    }
	
	@Override
    public void refreshData()
    {
    	super.refreshData();
    	
    	// Query the database to get the view data:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	_c = _viewsDB.getViewsByLevel("my_views");
    	
    	// This array lists the columns we're interested in:
    	String from[] = new String[] {"_id"};
    	
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
        		String itemName = Util.cString(c,"view_name");
        		name.setText(itemName);
        		
        		// Place tags, so that the handler functions know what ID has been tapped on.
        		deleteButton.setTag(Long.valueOf(Util.cLong(c, "_id")));
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
							// Get the item's ID:
							_tempItemID = (Long)v.getTag();
							
							// Button handlers for the dialog asking for confirmation:
							DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
								OnClickListener() 
				            {           
				                @Override
				                public void onClick(DialogInterface dialog, int which) {
				                    switch (which)
				                    {
				                    case DialogInterface.BUTTON_POSITIVE:
				                        // Yes clicked:
				                		// Delete the view:
				                		if (!_viewsDB.deleteView(_tempItemID))
				                		{
				                			Util.popup(_a, R.string.DbModifyFailed);
				                			break;
				                		}
				                		
				                		// We also have to delete the rules for the view in the database:
				                		ViewRulesDbAdapter rulesDB = new ViewRulesDbAdapter();
				                		rulesDB.clearAllRules(_tempItemID);
				                		
				                		refreshData();
				                		_a.refreshWholeNavDrawer();
				                        break;
				                        
				                    case DialogInterface.BUTTON_NEGATIVE:
				                        // No clicked:
				                        break;
				                    }                    
				                }
				            };
				            
				            // Create and show the confirmation dialog:
				            AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				            builder.setMessage(R.string.View_delete_confirmation);
				            builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
				            builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
				            Cursor c = _viewsDB.getView(_tempItemID);
				            if (c.moveToFirst())
				            	builder.setTitle(Util.cString(c, "view_name"));
				            c.close();
				            builder.show();
						}
					}
				});
        		
        		// Handler for a tap on the name:
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
								Intent i = new Intent(_a,EditViewActivity.class);
								i.putExtra("mode", EditViewFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditViewFragment frag = new EditViewFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditViewFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditViewFragment.FRAG_TAG+"/"+id);
							}
						}
					}
				});
        		
        		return true;
            }
        });
        
        setListAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	return super.onOptionsItemSelected(item);
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
