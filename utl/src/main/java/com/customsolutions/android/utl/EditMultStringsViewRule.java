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
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class EditMultStringsViewRule extends UtlSaveCancelPopupActivity
{
	// Request code for getting items:
	static private final int GET_CONTACT = 1;
	
	// An instance of MultStringsViewRule, that we will be working on:
	private MultStringsViewRule _multStringsViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;
	
	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	private TextView _description;
	
	// The default item IDs:
    private HashSet<String> _defaultItemIDs;
    
    // This array holds the item IDs:
    private String[] _itemIDs;
    
    // This array holds the item names:
    private String[] _itemNames;
    
    // The ListView used by this ListActivity:
    private ListView _listView;
    
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a multiple strings view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_mult_strings_view_rule);
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras;
        extras = this.getIntent().getExtras();
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditMultStringsViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditMultStringsViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditMultStringsViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the MultStringsViewRule object, which will hold the options we are
        // working on.
        if (!extras.containsKey("db_string"))
        {
        	_multStringsViewRule = new MultStringsViewRule(extras.getString("field"),
        		new String[] {});
        }
        else
        {
        	_multStringsViewRule = new MultStringsViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the top icon:
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));
        
        // Get a HashSet of selected item IDs:
        _defaultItemIDs = new HashSet<String>();
        for (int i=0; i<_multStringsViewRule.searchStrings.length; i++)
        	_defaultItemIDs.add(_multStringsViewRule.searchStrings[i]);
        
        //
        // Initialize the views based on the data passed in...
        //

        // Previous rule must also match:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_mult_strings_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_mult_strings_vr_separator).setVisibility(View.GONE);
        }
        
        // Views are initialized based on the task field:
        if (_multStringsViewRule.getDbField().equals("tags.name"))
        {
        	// Set the title:
        	getSupportActionBar().setTitle(getString(R.string.Select_one_or_more)+" "+
        		getString(R.string.Tags));
        	
        	// Hide views we're not using:
        	findViewById(R.id.edit_mult_strings_vr_text).setVisibility(View.GONE);
        	findViewById(R.id.edit_mult_strings_vr_edit_button).setVisibility(View.GONE);
			findViewById(R.id.edit_mult_strings_contact_spacer).setVisibility(View.GONE);
        	
        	CurrentTagsDbAdapter db = new CurrentTagsDbAdapter();
        	if (_multStringsViewRule.searchStrings.length>0)
    		{
                // Make sure any selected tags are in the current tags list:
                db.addToRecent(_multStringsViewRule.searchStrings);
    		}
        	
        	// Fill in the IDs and names for this list:
        	Cursor c = db.getTags();
        	if (c.getCount()>0)
        	{
        		_itemIDs = new String[c.getCount()];
        		_itemNames = new String[c.getCount()];
        		
        		// Also alter some text to be consistent with the fact that we do have some 
        		// preexisting tags.
        		TextView tv = (TextView)findViewById(R.id.edit_mult_strings_vr_enter_statement);
        		tv.setText(R.string.Enter_Other_Tags);
        	}
        	int i=0;
        	while (c.moveToNext())
        	{
        		_itemIDs[i] = Util.cString(c, "name");
        		_itemNames[i] = Util.cString(c, "name");
        		i++;
        	}
        	c.close();
        }
        else if (_multStringsViewRule.getDbField().equals("contact_lookup_key"))
        {
            // Set the title:
            getSupportActionBar().setTitle(Util.getString(R.string.Filter_on)+" "+Util.dbFieldToDescription.get(
            	extras.getString("field")));
            
            // Describe the rule:
            _description = (TextView)findViewById(R.id.edit_mult_strings_vr_text);
            if (_multStringsViewRule.searchStrings.length==0)
            {
            	_description.setText(Util.getString(R.string.Nothing_has_been_chosen));
            }
            else
            {
            	_description.setText(Util.getString(R.string.Find_tasks_whose)+" "+
            		_multStringsViewRule.getReadableString());
            }
            
            // Hide views we're not using:
            findViewById(R.id.edit_mult_strings_vr_other_tags).setVisibility(View.GONE);
            findViewById(android.R.id.list).setVisibility(View.GONE);
        }
        else if (_multStringsViewRule.getDbField().equals("owner_remote_id") ||
        	_multStringsViewRule.getDbField().equals("added_by"))
        {
        	// Set the title:
        	getSupportActionBar().setTitle(getString(R.string.Select_one_or_more)+" "+
        		getString(R.string.Collaborators));
        	
        	// Hide views we're not using:
        	findViewById(R.id.edit_mult_strings_vr_text).setVisibility(View.GONE);
        	findViewById(R.id.edit_mult_strings_vr_edit_button).setVisibility(View.GONE);
        	findViewById(R.id.edit_mult_strings_vr_other_tags).setVisibility(View.GONE);
        	
        	// Get a list of possible collaborators, from all accounts:
    		HashSet<String> collIdHash = new HashSet<String>();
    		CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
    		Cursor c = cdb.queryCollaborators(null, "name");
    		if (c.getCount()>0)
        	{
        		_itemIDs = new String[c.getCount()];
        		_itemNames = new String[c.getCount()];
        	}
    		int i=0;
			while (c.moveToNext())
			{
				UTLCollaborator co = cdb.cursorToCollaborator(c);
				if (!collIdHash.contains(co.remote_id))
				{
					_itemIDs[i] = co.remote_id;
					_itemNames[i] = co.name;
					collIdHash.add(co.remote_id);
				}
				i++;
			}
			c.close();
        }
        else
    	{
    		Util.log("Invalid field ("+_multStringsViewRule.getDbField()+") passed to "+
    			"EditMultStringsViewRule.java");
    		this.setResult(RESULT_CANCELED);
    		finish();
    		return;
    	}

        initBannerAd();
        
        //
        // Button handlers...
        //
        
        // Modify button:
        this.findViewById(R.id.edit_mult_strings_vr_edit_button).setOnClickListener(new
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				handleModifyButton();
			}
		});
        
        // And/or button:
        this.findViewById(R.id.edit_mult_strings_vr_is_and_checkbox).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				CheckedTextView ctv = (CheckedTextView)v;
				ctv.toggle();
			}
		});
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
    	
    	if (!_multStringsViewRule.getDbField().equals("contact_lookup_key"))
    	{
    		// If we don't have any items to filter on, then create blank arrays to prevent crashes.
    		if (_itemNames==null)
    			_itemNames = new String[] { };
    		if (_itemIDs==null)
    			_itemIDs = new String[] { };

    		// Set up the ListAdapter:
	        setListAdapter(new ArrayAdapter<String>(this,R.layout.item_picker_row, _itemNames));
	        _listView = this.getListView();
	        _listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	        
	        // Check the items that should be checked by default:
	        if (savedInstanceState!=null && savedInstanceState.containsKey("selected_items"))
	        {
	        	int i;
	        	HashSet<String> selectedItemIDsHash = new HashSet<String>();
	        	String[] selectedItemIDsArray = savedInstanceState.getStringArray("selected_items");
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
		            String id = _itemIDs[i];
		            if (_defaultItemIDs.contains(id))
		            {
		                _listView.setItemChecked(i,true);
		            }
		        } 
	        }
    	}
    }
    
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	if (!_multStringsViewRule.getDbField().equals("contact_lookup_key"))
    	{
	    	// Assemble an array of currently selected IDs:
	    	HashSet<String> selectedItemIDs = new HashSet<String>();
	        int j;
	        for (j=0; j<_listView.getCount(); j++)
	        {
	            if (_listView.isItemChecked(j))
	            {
	                selectedItemIDs.add(_itemIDs[j]);
	            }
	        }
	        b.putStringArray("selected_items", Util.iteratorToStringArray(selectedItemIDs.iterator(), 
	        	selectedItemIDs.size()));
    	}
    }
    
    // Handle the tap on the modify button:
    void handleModifyButton()
    {
    	if (_multStringsViewRule.getDbField().equals("contact_lookup_key"))
    	{
    		Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.
				Contacts.CONTENT_URI);
		    startActivityForResult(intent, GET_CONTACT);
    	}
    }
    
    // Handlers for activity results:
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
    	if (resultCode==RESULT_CANCELED)
    	{
    		// If the activity was canceled, there is nothing to do.
    		return;
    	}

    	if (requestCode==GET_CONTACT)
    	{
    		if (intent!=null && intent.getData()!=null)
    		{
    			Uri contactData = intent.getData();
        		Cursor c = managedQuery(contactData,null,null,null,null);
        		if (c!=null && c.moveToFirst())
        		{
        			_multStringsViewRule.searchStrings = new String[] { Util.cString(c, 
        				ContactsContract.Contacts.LOOKUP_KEY) };
        			
        			// Refresh the display:
                	_description.setText(Util.getString(R.string.Find_tasks_whose)+" "+
                		_multStringsViewRule.getReadableString());
        		}
    		}
    		return;
    	}
    }
    
	@Override
	public void handleSave()
	{
		if (_multStringsViewRule.getDbField().equals("contact_lookup_key"))
		{
			// Make sure some items are chosen:
			if (_multStringsViewRule.searchStrings.length==0)
			{
				Util.popup(EditMultStringsViewRule.this, R.string.Nothing_has_been_chosen);
				return;
			}
		}
		else
		{
			// Get the selected item IDs:
	        HashSet<String> selectedItemIDs = new HashSet<String>();
	        int j;
	        for (j=0; j<_listView.getCount(); j++)
	        {
	            if (_listView.isItemChecked(j))
	            {
	                selectedItemIDs.add(_itemIDs[j]);
	            }
	        }
	        
	        // If this is a tags rule, check the text box for other tags:
	        if (_multStringsViewRule.getDbField().equals("tags.name"))
	        {
	        	EditText text = (EditText)findViewById(R.id.edit_mult_strings_vr_manual_entry);
	        	if (text.getText().length()>0)
	        	{
	        		String[] tagArray = text.getText().toString().split(",");
	                for (int i=0; i<tagArray.length; i++)
	                {
	                	selectedItemIDs.add(tagArray[i].trim());
	                }
	        	}
	        }
	        
	        if (selectedItemIDs.size()==0)
	        {
	            // The user has selected nothing.
	            Util.popup(this, R.string.Please_select_at_least_one);
	            return;
	        }
	        
	        // Update the ViewRule object:
	        _multStringsViewRule.searchStrings = new String[selectedItemIDs.size()];
	        Iterator<String> it = selectedItemIDs.iterator();
	        j=0;
	    	while (it.hasNext())
	    	{
	    		String itemID = it.next();
	    		_multStringsViewRule.searchStrings[j] = itemID;
	    		j++;
	    	}
		} 
						
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _multStringsViewRule.getDbField());
        b.putString("db_string", _multStringsViewRule.getDatabaseString());
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
