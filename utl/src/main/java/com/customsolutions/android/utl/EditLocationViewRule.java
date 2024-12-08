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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

public class EditLocationViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of LocationViewRule, that we will be working on:
	private LocationViewRule _locViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;

	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	private TextView _description;
	
	// The default item IDs:
    private HashSet<Long> _defaultItemIDs;
    
    // This array holds the item IDs:
    private long[] _itemIDs;
    
    // This array holds the item names:
    private String[] _itemNames;
    
    // The ListView used by this ListActivity:
    private ListView _listView;
    
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a location view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_mult_choice_view_rule);
        
        Bundle extras;
       	extras = this.getIntent().getExtras();
        
        // Extract the parameters from the Bundle passed in:
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditLocationViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditLocationViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditLocationViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the LocationViewRule object, which will hold the options we are
        // working on:
        if (!extras.containsKey("db_string"))
        {
        	_locViewRule = new LocationViewRule(extras.getString("field"),
        		new int[] { });
        }
        else
        {
        	_locViewRule = new LocationViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the title and icon:
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));
        getSupportActionBar().setTitle(getString(R.string.Select_one_or_more)+" "+
        	getString(R.string.Locations));
                
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
    	for (int i=0; i<_locViewRule.searchInts.length; i++)
    		_defaultItemIDs.add(Integer.valueOf(_locViewRule.searchInts[i]).longValue());
    	
		// From here, we have to get a list of all item IDs and names:
		int i = 0;
		LocationsDbAdapter locationsDB = new LocationsDbAdapter();
		Cursor c = locationsDB.getAllLocations();
		_itemIDs = new long[c.getCount()+2];
		_itemNames = new String[c.getCount()+2];
		_itemIDs[0] = -1;
		_itemNames[0] = Util.getString(R.string.Current_Location);
		_itemIDs[1] = 0;
		_itemNames[1] = Util.getString(R.string.None);
		i = 2;
		while (c.moveToNext())
		{
			UTLLocation loc = locationsDB.cursorToUTLLocation(c);
			_itemIDs[i] = loc._id;
			_itemNames[i] = loc.title;
			i++;
		}
		c.close();
		
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
        _locViewRule.searchInts = new int[selectedItemIDs.size()];
        Iterator<Long> it = selectedItemIDs.iterator();
        j=0;
    	while (it.hasNext())
    	{
    		Long itemID = it.next();
    		_locViewRule.searchInts[j] = Integer.parseInt(Long.valueOf(itemID).toString());
    		j++;
    	}
    	
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _locViewRule.getDbField());
        b.putString("db_string", _locViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		EditLocationViewRule.this.setResult(RESULT_OK,i);		        
		finish();
	}
}

