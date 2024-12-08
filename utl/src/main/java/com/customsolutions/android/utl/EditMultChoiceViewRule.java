package com.customsolutions.android.utl;

//This activity is used for editing a filter rule.
//To call this activity, put a Bundle in the Intent with the following keys/values:
//operation: either ViewRulesList.ADD or ViewRulesList.EDIT  (required)
//is_or: default value for is_or field.  Either true or false
//field: the database field we are filtering on  (required)
//db_string: the encoded database string containing the settings for this rule
//index: The index of the rule in the list (included only for EDIT operations)
//lock_level: 0=no lock; 1=edit only (included only for EDIT operations)

//This passes back a Bundle to the caller with the following fields (if resultCode is
//RESULT_OK):
//operation
//field
//db_string
//index (EDIT only)
//is_or (if lock_level==0 or it's an ADD)

import java.util.HashSet;
import java.util.Iterator;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

public class EditMultChoiceViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of MultChoiceViewRule, that we will be working on:
	private MultChoiceViewRule _multChoiceViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;

	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	
	// The default item IDs:
    private HashSet<Long> _defaultItemIDs;
    
    // This array holds the item IDs:
    private long[] _itemIDs;
    
    // This array holds the item names:
    private String[] _itemNames;
    
    // The ListView used by this ListActivity:
    private ListView _listView;
    
	// Called when activity is first created:
    @SuppressWarnings("resource")
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a multiple choice view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_mult_choice_view_rule);
        
        Bundle extras;
        extras = this.getIntent().getExtras();
        
        // Extract the parameters from the Bundle passed in:
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditMultChoiceViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditMultChoiceViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditMultChoiceViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the MultChoiceViewRule object, which will hold the options we are
        // working on:
        if (!extras.containsKey("db_string"))
        {
        	_multChoiceViewRule = new MultChoiceViewRule(extras.getString("field"),
        		new int[] { });
        }
        else
        {
        	_multChoiceViewRule = new MultChoiceViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the icon at the top:
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));
        
        // Initialize the previous rule checkbox:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_mult_choice_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_mult_choice_vr_separator).setVisibility(View.GONE);
        }        
        
        // Get a HashSet of selected item IDs:
        _defaultItemIDs = new HashSet<Long>();
    	for (int i=0; i<_multChoiceViewRule.searchInts.length; i++)
    		_defaultItemIDs.add(Integer.valueOf(_multChoiceViewRule.searchInts[i]).longValue());
    	
    	// Get the number of accounts we have (which affects the display):
		AccountsDbAdapter accountsDB = new AccountsDbAdapter();
		Cursor c = accountsDB.getAllAccounts();
		int numAccounts = c.getCount();
		c.close();
			
		// From here, we have to get a list of all item IDs and names, based on the 
    	// type of field.  Also get a title for the item picker screen.
		int i = 0;
		String title = Util.getString(R.string.Select_one_or_more)+" ";
    	if (_multChoiceViewRule.getDbField().equals("account_id"))
    	{
    		c = accountsDB.getAllAccounts();
    		_itemIDs = new long[c.getCount()];
    		_itemNames = new String[c.getCount()];
    		while (c.moveToNext())
    		{
    			UTLAccount a = accountsDB.getUTLAccount(c);
    			_itemIDs[i] = a._id;
    			_itemNames[i] = a.name;
    			i++;
    		}
    		c.close();
    		title += Util.getString(R.string.Accounts);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("folder_id"))
    	{
    		// To get the best sort order, we count the accounts in the system.
    		// Unsynced account should be sorted by name, since we don't have
    		// the ability to reorder folders.  Synced accounts should be sorted
    		// by order, since folders can be rearranged in Toodledo.
    		c = accountsDB.getAllAccounts();
    		c.moveToPosition(-1);
    		int numToodledo = 0;
    		int numOther = 0;
    		while (c.moveToNext())
    		{
    			UTLAccount a = accountsDB.getAccount(Util.cLong(c, "_id"));
    			if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    				numToodledo++;
    			else
    				numOther++;
       		}
    		c.close();
    		
    		// Get an array of folder IDs and an array of folder names:
    		FoldersDbAdapter foldersDB = new FoldersDbAdapter();
    		if (numToodledo>0 && numOther==0)
    			c = foldersDB.getFoldersByOrder();
    		else
    			c = foldersDB.getFoldersByNameNoCase();
    		_itemIDs = new long[c.getCount()+1];
    		_itemNames = new String[c.getCount()+1];
    		_itemIDs[0] = 0;
    		_itemNames[0] = Util.getString(R.string.None);
    		c.moveToPosition(-1);
    		i=1;
    		while (c.moveToNext())
    		{
    			_itemIDs[i] = Util.cLong(c, "_id");
    			_itemNames[i] = Util.cString(c, "title");
    			if (numAccounts>1)
    			{
    				UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    				if (a!=null)
    				{
    					_itemNames[i] += " ("+a.name+")";
    				}
    			}
    			i++;
    		}
    		c.close();
    		title += Util.getString(R.string.Folders);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("context_id"))
    	{
    		// Get an array of context IDs and an array of context names:
    		ContextsDbAdapter contextsDB = new ContextsDbAdapter();
    		c = contextsDB.getContextsByName();
    		_itemIDs = new long[c.getCount()+1];
    		_itemNames = new String[c.getCount()+1];
    		_itemIDs[0] = 0;
    		_itemNames[0] = Util.getString(R.string.None);
    		c.moveToPosition(-1);
    		i=1;
    		while (c.moveToNext())
    		{
    			_itemIDs[i] = Util.cLong(c, "_id");
    			_itemNames[i] = Util.cString(c, "title");
    			if (numAccounts>1)
    			{
    				UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    				if (a!=null)
    				{
    					_itemNames[i] += " ("+a.name+")";
    				}
    			}
    			i++;
    		}
    		c.close();
    		title += Util.getString(R.string.Contexts);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("goal_id"))
    	{
    		// Get an array of goal IDs and an array of goal names:
    		GoalsDbAdapter goalsDB = new GoalsDbAdapter();
    		c = goalsDB.getAllGoals();
    		_itemIDs = new long[c.getCount()+1];
    		_itemNames = new String[c.getCount()+1];
    		_itemIDs[0] = 0;
    		_itemNames[0] = Util.getString(R.string.None);
    		c.moveToPosition(-1);
    		i=1;
    		while (c.moveToNext())
    		{
    			_itemIDs[i] = Util.cLong(c, "_id");
    			_itemNames[i] = Util.cString(c, "title");
    			if (numAccounts>1)
    			{
    				UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
    				if (a!=null)
    				{
    					_itemNames[i] += " ("+a.name+")";
    				}
    			}
    			i++;
    		}
    		c.close();
    		title += Util.getString(R.string.Goals);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("location_id"))
    	{
    		LocationsDbAdapter locationsDB = new LocationsDbAdapter();
    		c = locationsDB.getAllLocations();
    		_itemIDs = new long[c.getCount()+1];
    		_itemNames = new String[c.getCount()+1];
    		_itemIDs[0] = 0;
    		_itemNames[0] = Util.getString(R.string.None);
    		i = 1;
    		while (c.moveToNext())
    		{
    			UTLLocation loc = locationsDB.cursorToUTLLocation(c);
    			_itemIDs[i] = loc._id;
    			_itemNames[i] = loc.title;
    			i++;
    		}
    		c.close();
    		title += Util.getString(R.string.Locations);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("status"))
    	{
    		// Get an array of status integers and status names:
    		_itemNames = this.getResources().getStringArray(R.array.statuses);
    		_itemIDs = new long[_itemNames.length];
    		for (i=0; i<_itemNames.length; i++)
    		{
    			_itemIDs[i] = i;
    		}
    		title += Util.getString(R.string.Statuses);
    	}
    	else if (_multChoiceViewRule.getDbField().equals("priority"))
    	{
    		// Get an array of priority integers and priority names:
    		_itemNames = this.getResources().getStringArray(R.array.priorities);
    		_itemIDs = new long[_itemNames.length];
    		for (i=0; i<_itemNames.length; i++)
    		{
    			_itemIDs[i] = i;
    		}
    		title += Util.getString(R.string.Priorities);
    	}
    	else
    	{
    		Util.log("Invalid field ("+_multChoiceViewRule.getDbField()+") passed to "+
    			"EditMultChoiceViewRule.java");
    		this.setResult(RESULT_CANCELED);
    		finish();
    		return;
    	}
    	
    	// Set the title:
    	getSupportActionBar().setTitle(title);
    	    	    	
        // And/or tap handler:
        this.findViewById(R.id.edit_mult_choice_vr_is_and_checkbox).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				CheckedTextView ctv = (CheckedTextView)v;
				ctv.toggle();
			}
		});

        initBannerAd();
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
    	
    	// Set up the ListAdapter:
        setListAdapter(new ArrayAdapter<String>(this,R.layout.item_picker_row, _itemNames));
        _listView = this.getListView();
        _listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        
        // Check the items that should be checked by default:
        if (savedInstanceState!=null && savedInstanceState.containsKey("selected_items"))
        {
        	int i;
        	HashSet<Long> selectedItemIDsHash = new HashSet<Long>();
        	long[] selectedItemIDsArray = savedInstanceState.getLongArray("selected_items");
        	for (i=0; i<selectedItemIDsArray.length; i++)
        		selectedItemIDsHash.add(selectedItemIDsArray[i]);
        	for (i=0; i<_itemIDs.length; i++)
        	{
        		if (selectedItemIDsHash.contains(_itemIDs[i]))
        			_listView.setItemChecked(i,true);
        	}
        }
        else
        {
	        for (int i=0; i<_itemIDs.length; i++)
	        {
	            long id = _itemIDs[i];
	            if (_defaultItemIDs.contains(id))
	            {
	                _listView.setItemChecked(i,true);
	            }
	        } 
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	// Assemble an array of currently selected IDs:
    	HashSet<Long> selectedItemIDs = new HashSet<Long>();
        int j;
        for (j=0; j<_listView.getCount(); j++)
        {
            if (_listView.isItemChecked(j))
            {
                selectedItemIDs.add(_itemIDs[j]);
            }
        }
        b.putLongArray("selected_items", Util.iteratorToLongArray(selectedItemIDs.iterator(), 
        	selectedItemIDs.size()));
    }
    
	@Override
	public void handleSave()
	{
		// Get the selected item IDs:
        HashSet<Long> selectedItemIDs = new HashSet<Long>();
        int j;
        for (j=0; j<_listView.getCount(); j++)
        {
            if (_listView.isItemChecked(j))
            {
                selectedItemIDs.add(_itemIDs[j]);
            }
        }
        
        if (selectedItemIDs.size()==0)
        {
            // The user has selected nothing.
            Util.popup(this, R.string.Please_select_at_least_one);
            return;
        }
        
        // Update the ViewRule object:
        _multChoiceViewRule.searchInts = new int[selectedItemIDs.size()];
        Iterator<Long> it = selectedItemIDs.iterator();
        j=0;
    	while (it.hasNext())
    	{
    		Long itemID = it.next();
    		_multChoiceViewRule.searchInts[j] = Integer.parseInt(Long.valueOf(itemID).toString());
    		j++;
    	}
						
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _multChoiceViewRule.getDbField());
        b.putString("db_string", _multChoiceViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		setResult(RESULT_OK,i);		        
		finish();
	}
}

