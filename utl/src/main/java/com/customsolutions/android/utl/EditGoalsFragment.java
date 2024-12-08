package com.customsolutions.android.utl;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
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

/** This fragment displays a list of goals for editing. */
public class EditGoalsFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditGoalsFragment";
    
    private GoalsDbAdapter _goalsDB;
    private AccountsDbAdapter _accountsDB;
    private int _numAccounts;
    private Cursor _c;
    
    /** The currently selected item ID */
	private long _selectedItemID = -1;
    
	/** Temporary storage of an item ID: */
	private long _tempItemID = -1;
	
	/** Whether or not we are showing archived goals: */
	private boolean _showArchived;
	
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditGoalsFragment");
    	
    	_goalsDB = new GoalsDbAdapter();
    	_accountsDB = new AccountsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Edit_Goals);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_Goals_Defined);
    	
    	// Get the saved item ID, if it was passed in:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("selected_item_id"))
    		_selectedItemID = savedInstanceState.getLong("selected_item_id");
    	else
    		_a.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));
    	
    	_showArchived = false;

		// Handle the "add" button which is a floating action button:
		_a.findViewById(R.id.generic_list_fab).setOnClickListener((View v) -> {
			// The editor is either opened as a new activity or as a fragment in
			// the details pane:
			if (_a.useNewActivityForDetails())
			{
				// No split-screen in use.  Just start a new activity:
				Intent i = new Intent(_a,EditGoalActivity.class);
				i.putExtra("mode", EditGoalFragment.ADD);
				_a.startActivity(i);
			}
			else
			{
				// Start the fragment:
				EditGoalFragment frag = new EditGoalFragment();
				Bundle args = new Bundle();
				args.putInt("mode", EditGoalFragment.ADD);
				frag.setArguments(args);
				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag,
					EditGoalFragment.FRAG_TAG);
			}
		});
    }
    
    @Override
    public void refreshData()
    {
    	super.refreshData();
    	
    	// Query the database to get the goal data:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	if (!_showArchived)
    	{
    		_c = _goalsDB.getAllGoalsNoCase();
    	}
    	else
    	{
    		_c = _goalsDB.queryGoals(null, "account_id,level,lower(title)");
    	}
    	_c.moveToPosition(-1);
    	
    	// We need to know how many accounts we have, since this affects the display:
    	Cursor c = _accountsDB.getAllAccounts();
    	_numAccounts = c.getCount();
    	c.close();
    	
    	// This array lists the columns we're interested in:
    	String from[] = new String[] {"_id"};
    	
    	// The IDs of views that are affected by the columns:
    	int to[] = new int[] {R.id.goals_list_row_container};
    	
    	// Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(_a, R.layout.goals_list_row,
        	_c, from, to);
        
        // This function binds data in the Cursor to the Views we're displaying:
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
        {
        	public boolean setViewValue(View view, Cursor c, int columnIndex)
            {
	        	// Get references to the views we need to work with:
	    		ViewGroup row = (ViewGroup)view;
	    		TextView name = (TextView)view.findViewById(R.id.goals_list_row_name);
	    		ImageButton deleteButton = (ImageButton)view.findViewById(R.id.goals_list_row_delete);
	    		TextView info = (TextView)view.findViewById(R.id.goals_list_row_additional_info);
	    		
	    		// Display the name.  Change the color if it's archived.
        		String itemName = Util.cString(c,"title");
        		if (_numAccounts>1)
        		{
        			UTLAccount a = _accountsDB.getAccount(Util.cLong(c, "account_id"));
        			if (a!=null)
        				itemName += " ("+a.name+")";
        		}
        		name.setText(itemName);
        		if (Util.cInt(c, "archived")==1)
        			name.setTag(Boolean.valueOf(true));
        		else
        			name.setTag(Boolean.valueOf(false));
        		
        		// Get a string describing the level:
        		String infoStr = "";
        		switch (Util.cInt(c, "level"))
        		{
        		case 0:
        			infoStr += _a.getString(R.string.Lifelong);
        			break;
        		case 1:
        			infoStr += _a.getString(R.string.Long_Term);
        			break;
        		case 2:
        			infoStr += _a.getString(R.string.Short_Term);
        			break;
        		}
        		
        		// Add in the contributes information if the goal has some:
        		if (Util.cLong(c, "contributes")>0)
        		{
        			Cursor c2 = _goalsDB.getGoal(Util.cLong(c, "contributes"));
        			if (c2.moveToFirst())
        			{
        				infoStr += " / "+_a.getString(R.string.Contributes_To_)+" "+Util.cString(c2, 
        					"title");
        			}
        			c2.close();
        		}
        		info.setText(infoStr);
        		
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
        		else
        		{
        			if (Util.cInt(c, "archived")==1)
        			{
        				name.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
        				info.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
        			}
        			else
        			{
        				name.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
        				info.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
        			}
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
								    	// Clear the goal field from any tasks linked to the goal:
				                    	int numTasksUpdated = 0;
				                    	TasksDbAdapter tasksDB = new TasksDbAdapter();
				                    	Cursor c = tasksDB.queryTasks("goal_id="+_tempItemID, 
				                    		null);
				                        c.moveToPosition(-1);
				                        while (c.moveToNext())
				                        {
				                            UTLTask task = tasksDB.getUTLTask(c);
				                            task.goal_id = 0;
				                            task.mod_date = System.currentTimeMillis();
				                            boolean isSuccessful = tasksDB.modifyTask(task);
				                            if (!isSuccessful)
				                            {
				                            	Util.popup(_a,R.string.DbModifyFailed);
				                                Util.log("Could not clear goal for task ID "+task._id);
				                                return;
				                            }
				                            numTasksUpdated++;
				                        }
				                        c.close();
				                        
				                        // Clear the contributes field for any goal that contributes to
				                        // this one:
				                        int numGoalsUpdated = 0;
				                        c = _goalsDB.queryGoals("contributes="+_tempItemID, null);
				                        c.moveToPosition(-1);
				                        while (c.moveToNext())
				                        {
				                        	if (!_goalsDB.setContributes(Util.cLong(c,"_id"), 0))
				                        	{
				                        		Util.popup(_a,R.string.DbModifyFailed);
				                                Util.log("Could not clear contributes for goal "+Util.
				                                	cLong(c,"_id"));
				                                return;
				                        	}
				                        	numGoalsUpdated++;
				                        }
				                        
				                        c = _goalsDB.getGoal(_tempItemID);
				                        if (c.moveToFirst())
				                        {
				                        	if (Util.cLong(c, "td_id")>-1)
				                            {
				                            	// The item has a Toodledo ID, so the deletion needs
				                            	// to be uploaded.
				                            	// Update the pending deletes table:
				                            	PendingDeletesDbAdapter deletes = new 
				                            		PendingDeletesDbAdapter();
				                            	if (-1==deletes.addPendingDelete("goal", Util.cLong(c,"td_id"),
				                            		Util.cLong(c,"account_id")))
				                            	{
				                            		Util.popup(_a, R.string.DbInsertFailed);
				                            		Util.log("Cannot add pending delete in EditGoalsFragment.java.");
				                            		return;
				                            	}
				                            	
				                        		// If no tasks were updated, we can upload the deletion to
				                        		// Toodledo:
				                        		if (numTasksUpdated==0 && numGoalsUpdated==0)
				                        		{
				                            		Intent i = new Intent(_a, Synchronizer.class);
				                                	i.putExtra("command", "sync_item");
				                                	i.putExtra("item_type",Synchronizer.GOAL);
				                                	i.putExtra("item_id", Util.cLong(c, "td_id"));
				                                	i.putExtra("account_id", Util.cLong(c, "account_id"));
				                                	i.putExtra("operation",Synchronizer.DELETE);
													Synchronizer.enqueueWork(_a,i);
				                        		}
				                            }
				                            
				                    		// Delete the goal:
				                    		if (!_goalsDB.deleteGoal(_tempItemID))
				                    		{
				                    			Util.popup(_a, R.string.DbModifyFailed);
				                    			Util.log("Could not delete goal from database.");
				                    			return;
				                    		}
				                    		
				                    		// If we're in 3 pane mode, get rid of the fragment showing
				    					    // the goal's details.
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
							builder.setMessage(R.string.Goal_delete_confirmation);
							builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
					        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
					        Cursor c = _goalsDB.getGoal(_tempItemID);
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
								Intent i = new Intent(_a,EditGoalActivity.class);
								i.putExtra("mode", EditGoalFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditGoalFragment frag = new EditGoalFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditGoalFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditGoalFragment.FRAG_TAG+"/"+id);
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
    	inflater.inflate(R.menu.generic_list_with_archive, menu);
    	
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
    	case R.id.menu_generic_list_show_archived:
    		_showArchived = true;
    		refreshData();
    		return true;
    		
    	case R.id.menu_generic_list_hide_archived:
    		_showArchived = false;
    		refreshData();
    		return true;
    	}
    	
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
    	
		TextView tv = (TextView)row.findViewById(R.id.goals_list_row_name);
		Boolean isArchived = (Boolean)tv.getTag();
		if (!isArchived)
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		else
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
		
		tv = (TextView)row.findViewById(R.id.goals_list_row_additional_info);
		if (!isArchived)
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		else
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
		
		ImageButton deleteButton = (ImageButton)row.findViewById(R.id.goals_list_row_delete);
		deleteButton.setImageResource(_a.resourceIdFromAttr(R.attr.ab_delete_inv));
    }
    
    /** Highlight a row: */
    private void highlightRow(ViewGroup row, long itemID)
    {
    	row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_bg_color)));
    	
    	TextView tv = (TextView)row.findViewById(R.id.goals_list_row_name);
    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_text_color)));
    	
    	tv = (TextView)row.findViewById(R.id.goals_list_row_additional_info);
    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_text_color)));
    	
    	ImageButton deleteButton = (ImageButton)row.findViewById(R.id.goals_list_row_delete);
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
