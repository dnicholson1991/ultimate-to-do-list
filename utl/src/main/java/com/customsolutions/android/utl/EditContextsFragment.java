package com.customsolutions.android.utl;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** This fragment displays a list of contexts for editing. */
public class EditContextsFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditContextsFragment";

    private ContextsDbAdapter _contextsDB;
    private AccountsDbAdapter _accountsDB;
	private int _numAccounts;
	private Cursor _c;
	
	/** The currently selected item ID */
	private long _selectedItemID = -1;
    
	/** Temporary storage of an item ID: */
	private long _tempItemID = -1;
	
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditContextsFragment");
    	
    	_contextsDB = new ContextsDbAdapter();
    	_accountsDB = new AccountsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Edit_Contexts);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_Contexts_Defined);
    	
    	// Get the saved item ID, if it was passed in:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("selected_item_id"))
    		_selectedItemID = savedInstanceState.getLong("selected_item_id");
    	else
    		_a.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));

		// Handle the "add" button which is a floating action button:
		_a.findViewById(R.id.generic_list_fab).setOnClickListener((View v) -> {
			// The editor is either opened as a new activity or as a fragment in
			// the details pane:
			if (_a.useNewActivityForDetails())
			{
				// No split-screen in use.  Just start a new activity:
				Intent i = new Intent(_a,EditContextActivity.class);
				i.putExtra("mode", EditContextFragment.ADD);
				_a.startActivity(i);
			}
			else
			{
				// Start the fragment:
				EditContextFragment frag = new EditContextFragment();
				Bundle args = new Bundle();
				args.putInt("mode", EditContextFragment.ADD);
				frag.setArguments(args);
				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag,
					EditContextFragment.FRAG_TAG);
			}
		});
    }
    
    @Override
    public void refreshData()
    {
    	super.refreshData();
    	
    	// Query the database to get the context data:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	_c = _contextsDB.getContextsByNameNoCase();
    	_c.moveToPosition(-1);
    	
    	// We need to know how many accounts we have, since this affects the display:
    	Cursor c = _accountsDB.getAllAccounts();
    	_numAccounts = c.getCount();
    	c.close();
    	
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
        		String itemName = Util.cString(c,"title");
        		if (_numAccounts>1)
        		{
        			UTLAccount a = _accountsDB.getAccount(Util.cLong(c, "account_id"));
        			if (a!=null)
        				itemName += " ("+a.name+")";
        		}
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
							
							// Button handler for the confirmation dialog:
							DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
				        	  	  OnClickListener()
							{				
							  @Override
							  public void onClick(DialogInterface dialog, int which)
							  {
								  if (which == DialogInterface.BUTTON_POSITIVE)
								  {
									  // Yes clicked:
									  // Clear the context field from any tasks linked to the context:
									  int numTasksUpdated = 0;
									  TasksDbAdapter tasksDB = new TasksDbAdapter();
									  Cursor c = tasksDB.queryTasks("context_id="+_tempItemID, 
										  null);
									  c.moveToPosition(-1);
									  while (c.moveToNext())
									  {
									      UTLTask task = tasksDB.getUTLTask(c);
									      task.context_id = 0;
									      task.mod_date = System.currentTimeMillis();
									      boolean isSuccessful = tasksDB.modifyTask(task);
									      if (!isSuccessful)
									      {
									    	  Util.popup(_a,R.string.DbModifyFailed);
									          Util.log("Could not clear context for task ID "+task._id);
									          return;
									      }
									      numTasksUpdated++;
									  }
									  c.close();
									
									  // We need the context's toodledo ID:
									  c = _contextsDB.getContext(_tempItemID);
								 	  if (c.moveToFirst())
									  {
									      if (Util.cLong(c, "td_id")>-1)
									      {
										      // The item has a Toodledo ID, so the deletion needs
									          // to be uploaded.
									          // Update the pending deletes table:
									    	  PendingDeletesDbAdapter deletes = new 
									    		  PendingDeletesDbAdapter();
									    	  if (-1==deletes.addPendingDelete("context", Util.cLong(
									    		  c,"td_id"),Util.cLong(c,"account_id")))
									    	  {
									    		  Util.popup(_a, R.string.DbInsertFailed);
									    		  Util.log("Cannot add pending delete in ContextList.java.");
									    		  return;
									    	  }
									
									    	  // If no tasks were updated, we can upload the deletion to
									    	  // Toodledo:
									    	  if (numTasksUpdated==0)
									    	  {
									    		  Intent i = new Intent(_a, Synchronizer.class);
									    		  i.putExtra("command", "sync_item");
									    		  i.putExtra("item_type",Synchronizer.CONTEXT);
									    		  i.putExtra("item_id", Util.cLong(c, "td_id"));
									    		  i.putExtra("account_id", Util.cLong(c, "account_id"));
									    		  i.putExtra("operation",Synchronizer.DELETE);
												  Synchronizer.enqueueWork(_a,i);
									    	  }
									      }
									
									      // Delete the context:
									      if (!_contextsDB.deleteContext(_tempItemID))
									      {
									    	  Util.popup(_a, R.string.DbModifyFailed);
									    	  Util.log("Could not delete context from database.");
									    	  return;
									      }
									      
									      // If we're in 3 pane mode, get rid of the fragment showing
									      // the context's details.
									      if (_selectedItemID==_tempItemID)
									      {
									          _a.showDetailPaneMessage(_a.getString(R.string.
									    	      Select_an_item_to_display));
									      }
									  }
								 	  c.close();
									  
									  refreshData();
									  _a.refreshWholeNavDrawer();
				                  }					
							  }
							};
							
							// Display the confirmation dialog:
							AlertDialog.Builder builder = new AlertDialog.Builder(_a);
							builder.setMessage(R.string.Context_delete_confirmation);
							builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
					        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
					        Cursor c = _contextsDB.getContext(_tempItemID);
					        if (c.moveToFirst())
					        	builder.setTitle(Util.cString(c, "title"));
					        c.close();
				            builder.show();
				            return;
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
								Intent i = new Intent(_a,EditContextActivity.class);
								i.putExtra("mode", EditContextFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditContextFragment frag = new EditContextFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditContextFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditContextFragment.FRAG_TAG+"/"+id);
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
