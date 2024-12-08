package com.customsolutions.android.utl;

// This activity is used for displaying the list of collaborators.
// To call this activity, put a Bundle in the intent with the following keys/values:
// account_id: The UTL id of the account we're viewing the collaborators for

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class CollaboratorsList extends UtlPopupActivity
{
	private CollaboratorsDbAdapter _collDb;
	private Cursor _cursor;
	private long _accountID;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Launched CollaboratorsList activity");
        setContentView(R.layout.collaborators_list);
        _collDb = new CollaboratorsDbAdapter();
        
        // Set the title for this screen:
        getSupportActionBar().setTitle(Util.getString(R.string.Collaborators));
        getSupportActionBar().setIcon(R.drawable.toodledo_logo);
        
        // Get the account ID from the Bundle:
        Bundle extras = getIntent().getExtras();
        if (extras!=null)
        {
        	if (extras.containsKey("account_id"))
        	{
        		_accountID = extras.getLong("account_id");
        	}
        	else
        	{
        		Util.log("Missing account_id in CollaboratorsList.java.");
            	finish();
            	return;
        	}
        }
        else
        {
        	Util.log("Null Bundle passed into CollaboratorsList.java.");
        	finish();
        	return;
        }
    }
    
    @Override
    public void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
    	fillData();
    }
    
    // Fill in the data in the list:
    private void fillData()
    {
    	// Query the database to get the collaborators (excluding myself):
    	AccountsDbAdapter adb = new AccountsDbAdapter();
    	UTLAccount acct = adb.getAccount(_accountID);
    	_cursor = _collDb.queryCollaborators("account_id="+_accountID+" and remote_id!='"+
    		Util.makeSafeForDatabase(acct.td_userid)+"'",null);
    	startManagingCursor(_cursor);
    	
    	// This array lists the columns we're interested in:
        String[] from = new String[]{"name"};
        
        // The IDs of views that are affected by the columns:
        int[] to = new int[]{R.id.coll_item_collaborator_row};
        
        // Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,R.layout.collaborators_list_item,
        	_cursor, from, to);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
		{
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex)
			{
				// Fetch the data from the database:
				String name = cursor.getString(columnIndex);
				boolean isReassignable = cursor.getInt(4) == 1 ? true : false;
				boolean isSharable = cursor.getInt(5) == 1 ? true : false;
				
				// Fetch the views we want to update:
				ViewGroup vg = (ViewGroup) view;
				TextView nameView = (TextView) vg.findViewById(R.id.coll_item_collaborator_name);
				CheckBox reassignableCheckbox = (CheckBox) vg.findViewById(R.id.coll_item_reassignable_cb);
				CheckBox sharableCheckbox = (CheckBox) vg.findViewById(R.id.coll_item_sharable_cb);
				
				// Update the views:
				nameView.setText(name);
				reassignableCheckbox.setChecked(isReassignable);
				sharableCheckbox.setChecked(isSharable);
				
				return true;
			}
		});
        
        setListAdapter(adapter);
    }
}

