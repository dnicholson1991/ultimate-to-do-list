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

/** This fragment displays a list of folders for editing. */
public class EditFoldersFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditFoldersFragment";
    
    private FoldersDbAdapter _foldersDB;
    private AccountsDbAdapter _accountsDB;
    private int _numAccounts;
    private Cursor _c;
    
    /** The currently selected item ID */
	private long _selectedItemID = -1;
    
	/** Temporary storage of an item ID: */
	private long _tempItemID = -1;
	
	/** Whether or not we are showing archived folders: */
	private boolean _showArchived;
	
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditFoldersFragment");
    	
    	_foldersDB = new FoldersDbAdapter();
    	_accountsDB = new AccountsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Edit_Folders);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_Folders_Defined);
    	
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
				Intent i = new Intent(_a,EditFolderActivity.class);
				i.putExtra("mode", EditFolderFragment.ADD);
				_a.startActivity(i);
			}
			else
			{
				// Start the fragment:
				EditFolderFragment frag = new EditFolderFragment();
				Bundle args = new Bundle();
				args.putInt("mode", EditFolderFragment.ADD);
				frag.setArguments(args);
				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag,
					EditFolderFragment.FRAG_TAG);
			}
		});
    }
    
    @Override
    public void refreshData()
    {
    	super.refreshData();
    	
    	// Query the database to get the folder data:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	if (!_showArchived)
    	{
    		// To get the best sort order, we count the accounts in the system.
    		// Unsynced account should be sorted by name, since we don't have
    		// the ability to reorder folders.  Synced accounts should be sorted
    		// by order, since folders can be rearranged in Toodledo.
    		Cursor c = _accountsDB.getAllAccounts();
    		c.moveToPosition(-1);
    		int numToodledo = 0;
    		int numOther = 0;
    		while (c.moveToNext())
    		{
    			UTLAccount a = _accountsDB.getAccount(Util.cLong(c, "_id"));
    			if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    				numToodledo++;
    			else
    				numOther++;
       		}
    		c.close();
			if (numToodledo>0 && numOther==0)
				_c = _foldersDB.getFoldersByOrder();
			else 
				_c = _foldersDB.getFoldersByNameNoCase();
    	}
    	else
    	{
    		// This query will show archived folders:
    		_c = _foldersDB.queryFolders(null, "account_id,lower(title)");
    	}
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
        				name.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
        			else
        				name.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
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
							
					    	// If this is a Google folder, handle it elsewhere:
				            Cursor c = _foldersDB.getFolder(_tempItemID);
				            if (c.moveToFirst())
				            {
				            	UTLAccount a = _accountsDB.getAccount(Util.cLong(c, "account_id"));
				            	if (a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE)
				            	{
				            		c.close();
				            		googleDeletionHandler();
				            		return;
				            	}
				            }
				            c.close();

				            // Button handler for the confirmation dialog:
							DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
				        	  	  OnClickListener()
							{				
							    @Override
							    public void onClick(DialogInterface dialog, int which)
							    {
								    if (which == DialogInterface.BUTTON_POSITIVE)
								    {
								    	// Clear the folder field from any notes linked to the folder:
								    	int numNotesUpdated = 0;
								    	NotesDbAdapter notesDB = new NotesDbAdapter();
								    	Cursor c = notesDB.queryNotes("folder_id="+_tempItemID, null);
								    	while (c.moveToNext())
								    	{
								    		UTLNote note = notesDB.cursorToUTLNote(c);
								    		note.folder_id = 0;
								    		note.mod_date = System.currentTimeMillis();
								    		if (!notesDB.modifyNote(note))
								    		{
								    			Util.popup(_a,R.string.DbModifyFailed);
								    			Util.log("Could not clear folder for note ID "+note._id);
								    			c.close();
								    			return;
								    		}
								    		numNotesUpdated++;
								    	}
								    	c.close();
								    	
								    	// Clear the folder field from any tasks linked to the folder:
				                    	int numTasksUpdated = 0;
				                    	TasksDbAdapter tasksDB = new TasksDbAdapter();
				                    	c = tasksDB.queryTasks("folder_id="+_tempItemID, null);
				                        c.moveToPosition(-1);
				                        while (c.moveToNext())
				                        {
				                            UTLTask task = tasksDB.getUTLTask(c);
				                            task.folder_id = 0;
				                            task.mod_date = System.currentTimeMillis();
				                            boolean isSuccessful = tasksDB.modifyTask(task);
				                            if (!isSuccessful)
				                            {
				                            	Util.popup(_a,R.string.DbModifyFailed);
				                                Util.log("Could not clear folder for task ID "+task._id);
				                                c.close();
				                                return;
				                            }
				                            numTasksUpdated++;
				                        }
				                        c.close();
				                        
				                        c = _foldersDB.getFolder(_tempItemID);
				                        if (c.moveToFirst())
				                        {
				                        	if (Util.cLong(c, "td_id")>-1)
				                            {
				                            	// The item has a Toodledo ID, so the deletion needs
				                            	// to be uploaded.
				                            	// Update the pending deletes table:
				                            	PendingDeletesDbAdapter deletes = new 
				                            		PendingDeletesDbAdapter();
				                            	if (-1==deletes.addPendingDelete("folder", Util.cLong(c,"td_id"),
				                            		Util.cLong(c,"account_id")))
				                            	{
				                            		Util.popup(_a, R.string.DbInsertFailed);
				                            		Util.log("Cannot add pending delete in FolderList.java.");
				                            		return;
				                            	}
				                            	
				                        		// If no tasks were updated, we can upload the deletion to
				                        		// Toodledo:
				                        		if (numTasksUpdated==0 && numNotesUpdated==0)
				                        		{
				                            		Intent i = new Intent(_a, Synchronizer.class);
				                                	i.putExtra("command", "sync_item");
				                                	i.putExtra("item_type",Synchronizer.FOLDER);
				                                	i.putExtra("item_id", Util.cLong(c, "td_id"));
				                                	i.putExtra("account_id", Util.cLong(c, "account_id"));
				                                	i.putExtra("operation",Synchronizer.DELETE);
													Synchronizer.enqueueWork(_a,i);
				                        		}
				                            }
				                            
				                    		// Delete the folder:
				                    		if (!_foldersDB.deleteFolder(_tempItemID))
				                    		{
				                    			Util.popup(_a, R.string.DbModifyFailed);
				                    			Util.log("Could not delete folder from database.");
				                    			return;
				                    		}
				                    		
				                    		// If we're in 3 pane mode, get rid of the fragment showing
				    					    // the folder's details.
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
							builder.setMessage(R.string.Folder_delete_confirmation);
							builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
					        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
					        c = _foldersDB.getFolder(_tempItemID);
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
								Intent i = new Intent(_a,EditFolderActivity.class);
								i.putExtra("mode", EditFolderFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditFolderFragment frag = new EditFolderFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditFolderFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditFolderFragment.FRAG_TAG+"/"+id);
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
    
    /** Handler to call when the user wants to delete a Google folder: */
    private void googleDeletionHandler()
    {
    	Cursor c = _foldersDB.getFolder(_tempItemID);
    	if (!c.moveToFirst())
    	{
    		Util.popup(_a, R.string.Item_no_longer_exists);
    		return;
    	}
    	String folderTitle = Util.cString(c, "title");
    	c.close();
    	
    	// Check to see if any tasks are linked to this folder.  If so, block the user:
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	c = tasksDB.queryTasks("folder_id="+_tempItemID, null);
    	if (c.getCount()>0)
    	{
    		c.close();
    		Util.longerPopup(_a, folderTitle, Util.getString(R.string.
    			Folder_Contains_Tasks));
    		return;
    	}
    	c.close();
    	
    	// Button handlers for the dialog asking for confirmation:
    	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() 
        {           
            @Override
            public void onClick(DialogInterface dialog, int which) 
            {
                switch (which)
                {
                case DialogInterface.BUTTON_POSITIVE:
                    // Yes clicked:
                    // We need the folder's Google ID:
                    Cursor c = _foldersDB.getFolder(_tempItemID);
                    if (c.moveToFirst())
                    {
				    	// Clear the folder field from any notes linked to the folder:
				    	NotesDbAdapter notesDB = new NotesDbAdapter();
				    	Cursor c2 = notesDB.queryNotes("folder_id="+_tempItemID, null);
				    	while (c2.moveToNext())
				    	{
				    		UTLNote note = notesDB.cursorToUTLNote(c2);
				    		note.folder_id = 0;
				    		note.mod_date = System.currentTimeMillis();
				    		if (!notesDB.modifyNote(note))
				    		{
				    			Util.popup(_a,R.string.DbModifyFailed);
				    			Util.log("Could not clear folder for note ID "+note._id);
				    			c2.close();
				    			return;
				    		}
				    	}
				    	c2.close();

				    	if (Util.cString(c, "remote_id").length()>0)
                        {
                        	// The item has a Google ID, so the deletion needs
                        	// to be uploaded.
                        	// Update the pending deletes table:
                        	PendingDeletesDbAdapter deletes = new 
                        		PendingDeletesDbAdapter();
                        	if (-1==deletes.addPendingDelete("folder", "", Util.cString(c, 
                        		"remote_id"),Util.cLong(c,"account_id")))
                        	{
                        		Util.popup(_a, R.string.DbInsertFailed);
                        		Util.log("Cannot add pending delete in FolderList.java.");
                        		return;
                        	}
                        	
                    		// Perform the instant upload:
                    		Intent i = new Intent(_a, Synchronizer.class);
                        	i.putExtra("command", "sync_item");
                        	i.putExtra("item_type",Synchronizer.FOLDER);
                        	i.putExtra("item_id", -1);
                        	i.putExtra("account_id", Util.cLong(c, "account_id"));
                        	i.putExtra("operation",Synchronizer.DELETE);
                        	i.putExtra("remote_id", "");
                        	i.putExtra("remote_tasklist_id", Util.cString(c, "remote_id"));
							Synchronizer.enqueueWork(_a,i);
                        }
                        
                		// Delete the folder:
                		if (!_foldersDB.deleteFolder(_tempItemID))
                		{
                			Util.popup(_a, R.string.DbModifyFailed);
                			Util.log("Could not delete folder from database.");
                			return;
                		}
                		
                		// If we're in 3 pane mode, get rid of the fragment showing
					    // the folder's details.
                		if (_selectedItemID==_tempItemID)
                		{
                			_a.showDetailPaneMessage(_a.getString(R.string.
                				Select_an_item_to_display));
                		}
                    }
                    c.close();
            		
            		// Refresh the display:
                    refreshData();
					_a.refreshWholeNavDrawer();
                    break;
                    
                case DialogInterface.BUTTON_NEGATIVE:
                    // No clicked:
                    break;
                }                    
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(_a);
        builder.setMessage(R.string.Folder_delete_confirmation2);
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        c = _foldersDB.getFolder(_tempItemID);
        if (c.moveToFirst())
        {
        	builder.setTitle(Util.cString(c, "title"));
        }
        c.close();
        builder.show();
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
		TextView tv = (TextView)row.findViewById(R.id.generic_list_row_text);
		Boolean isArchived = (Boolean)tv.getTag();
		if (!isArchived)
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		else
			tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_archive_text_color)));
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
