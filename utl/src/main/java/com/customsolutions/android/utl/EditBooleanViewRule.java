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

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;

public class EditBooleanViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of BooleanViewRule, that we will be working on:
	private BooleanViewRule _booleanViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;
	
	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	private CheckedTextView _boolean;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a boolean view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_boolean_view_rule);
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras;
        if (savedInstanceState==null)
        	extras = this.getIntent().getExtras();
        else
        	extras = savedInstanceState;
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditBooleanViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditBooleanViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditBooleanViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the BooleanViewRule object, which will hold the options we are working on:
        if (!extras.containsKey("db_string"))
        {
        	if (extras.getString("field").equals("completed"))
        		_booleanViewRule = new BooleanViewRule(extras.getString("field"),false);
        	else
        		_booleanViewRule = new BooleanViewRule(extras.getString("field"),true);
        }
        else
        {
        	_booleanViewRule = new BooleanViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the title and icon:
        getSupportActionBar().setTitle(Util.getString(R.string.Filter_on)+" "+Util.dbFieldToDescription.
        	get(extras.getString("field")));
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));

        initBannerAd();

        //
        // Initialize the views based on the data passed in...
        //
        
        // Previous rule must also match:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_boolean_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        }
        
        // Checkbox:
        _boolean = (CheckedTextView)findViewById(R.id.edit_boolean_vr_checkbox);
        _boolean.setChecked(_booleanViewRule.myValue);
                
        // Descriptive text:
        if (_booleanViewRule.getDbField().equals("completed"))
        {
        	_boolean.setText(R.string.Show_completed_tasks);
        }
        else if (_booleanViewRule.getDbField().equals("star"))
        {
        	_boolean.setText(R.string.Show_starred);
        }
        else if (_booleanViewRule.getDbField().equals("is_joint"))
        {
        	_boolean.setText(R.string.Show_shared);
        }
        else
        {
        	// This should not happen!
        	_boolean.setText(_booleanViewRule.getReadableString());
        }
        
        //
        // Button handlers...
        //
        
        // And/or checkbox:
        this.findViewById(R.id.edit_boolean_vr_is_and_checkbox).setOnClickListener(new 
    	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				CheckedTextView ctv = (CheckedTextView)v;
				ctv.toggle();
			}
		});
        
        // Boolean checkbox:
        this.findViewById(R.id.edit_boolean_vr_checkbox).setOnClickListener(new 
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
    
    // Called before the activity is destroyed due to orientation change:
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	_booleanViewRule.myValue = _boolean.isChecked();
    	b.putInt("operation", _operation);
    	b.putBoolean("is_or", !_isAnd.isChecked());
    	b.putString("field", _booleanViewRule.dbField);
    	b.putString("db_string", _booleanViewRule.getDatabaseString());
    	if (_operation==ViewRulesList.EDIT)
    	{
    		b.putInt("index", _index);
    		b.putInt("lock_level", _lockLevel);
    	}
    }

	@Override
	public void handleSave()
	{
		// Update the BooleanViewRule object to match the data in the views:
		_booleanViewRule.myValue = _boolean.isChecked();
		
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _booleanViewRule.getDbField());
        b.putString("db_string", _booleanViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		EditBooleanViewRule.this.setResult(RESULT_OK,i);		        
		finish();
	}
}
	
