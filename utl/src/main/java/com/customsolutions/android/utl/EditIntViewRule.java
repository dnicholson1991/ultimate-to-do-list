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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/**<pre>
This activity is used for editing a filter rule.
To call this activity, put a Bundle in the Intent with the following keys/values:
operation: either ViewRulesList.ADD or ViewRulesList.EDIT  (required)
is_or: default value for is_or field.  Either true or false
field: the database field we are filtering on  (required)
db_string: the encoded database string containing the settings for this rule
index: The index of the rule in the list (included only for EDIT operations)
lock_level: 0=no lock; 1=edit only (included only for EDIT operations)

This passes back a Bundle to the caller with the following fields (if resultCode is
RESULT_OK):
  operation
  field
  db_string
  index (EDIT only)
  is_or (if lock_level==0 or it's an ADD)
 </pre> */
public class EditIntViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of IntViewRule, that we will be working on:
	private IntViewRule _intViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;
	
	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	private Spinner _timerSpinner;
	private Spinner _operationSpinner;
	private EditText _intValue;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing an int view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_int_view_rule);
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras;
        if (savedInstanceState==null)
        	extras = this.getIntent().getExtras();
        else
        	extras = savedInstanceState;
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditIntViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditIntViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditIntViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the IntViewRule object, which will hold the options we are working on:
        if (!extras.containsKey("db_string"))
        {
        	_intViewRule = new IntViewRule(extras.getString("field"),0,">");
        }
        else
        {
        	_intViewRule = new IntViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the title and icon:
        getSupportActionBar().setTitle(Util.getString(R.string.Filter_on)+" "+Util.dbFieldToDescription.get(
        	extras.getString("field")));
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));

        initBannerAd();

        //
        // Initialize the views based on the data passed in...
        //
        
        _timerSpinner = (Spinner)findViewById(R.id.edit_int_vr_timer_spinner);
        _operationSpinner = (Spinner)findViewById(R.id.edit_int_vr_other_int_operator);
        _intValue = (EditText)findViewById(R.id.edit_int_vr_other_int_number);

        // Previous rule must also match:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_int_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_int_vr_separator).setVisibility(View.GONE);
        }       
        
        // Timer option:
        if (_intViewRule.getDbField().equals("timer_start_time"))
        {
        	// Hide views we don't need:
        	this.findViewById(R.id.edit_int_vr_other_int_text).setVisibility(View.GONE);
        	_operationSpinner.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_int_vr_other_int_container).setVisibility(View.GONE);
        	
        	// Set the spinner:
        	if (_intViewRule.operator.equals(">") && _intViewRule.value==0)
        	{
        		_timerSpinner.setSelection(1);
        	}
        	else
        	{
        		_timerSpinner.setSelection(0);
        	}
        }
        else
        {
        	// Hide views we don't need:
        	this.findViewById(R.id.edit_int_vr_timer_text).setVisibility(View.GONE);
        	_timerSpinner.setVisibility(View.GONE);
        	if (_intViewRule.getDbField().equals("importance"))
        		findViewById(R.id.edit_int_vr_other_int_minutes).setVisibility(View.GONE);
        	
        	// Set the operation spinner and text box value:
        	if (_intViewRule.operator.equals("<"))
        	{
        		_operationSpinner.setSelection(0);
        	}
        	if (_intViewRule.operator.equals("<="))
        	{
        		_operationSpinner.setSelection(1);
        	}
        	if (_intViewRule.operator.equals("="))
        	{
        		_operationSpinner.setSelection(2);
        	}
        	if (_intViewRule.operator.equals(">="))
        	{
        		_operationSpinner.setSelection(3);
        	}
        	if (_intViewRule.operator.equals(">"))
        	{
        		_operationSpinner.setSelection(4);
        	}
        	if (_intViewRule.operator.equals("!="))
        	{
        		_operationSpinner.setSelection(5);
        	}
        	_intValue.setText(new Long(_intViewRule.value).toString());
        	
        	// Update the introductory text:
        	TextView tv = (TextView)this.findViewById(R.id.edit_int_vr_other_int_text);
        	tv.setText(Util.getString(R.string.Find_tasks_whose)+" "+
        		Util.dbFieldToDescription.get(_intViewRule.getDbField()));
        }
        
        //
        // Button handlers...
        //
        
        // And/or checkbox:
        this.findViewById(R.id.edit_int_vr_is_and_checkbox).setOnClickListener(new 
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

    // Update the view rule object based on the data in the views:
    protected void updateViewRule()
    {
    	long valueEntered = 0;
		if (!_intViewRule.getDbField().equals("timer_start_time"))
		{
			// Extract the value entered:
			try
			{
				valueEntered = Long.parseLong(_intValue.getText().toString());
			}
			catch (NumberFormatException e)
			{
				valueEntered = 0;
			}
		}
		
		// Update the _intViewRule to match the data in the views:
		if (_intViewRule.getDbField().equals("timer_start_time"))
		{
			if (_timerSpinner.getSelectedItemPosition()==0)
			{
				_intViewRule.operator = "=";
				_intViewRule.value = 0;
			}
			else
			{
				_intViewRule.operator = ">";
				_intViewRule.value = 0;
			}
		}
		else
		{
			_intViewRule.value = valueEntered;
			if (_intViewRule.getDbField().equals("timer"))
			{
				// Convert from minutes to seconds:
				_intViewRule.value = _intViewRule.value*60;
			}
			if (_operationSpinner.getSelectedItemPosition()==0)
			{
				_intViewRule.operator="<";
			}
			if (_operationSpinner.getSelectedItemPosition()==1)
			{
				_intViewRule.operator="<=";						
			}
			if (_operationSpinner.getSelectedItemPosition()==2)
			{
				_intViewRule.operator="=";						
			}
			if (_operationSpinner.getSelectedItemPosition()==3)
			{
				_intViewRule.operator=">=";
			}
			if (_operationSpinner.getSelectedItemPosition()==4)
			{
				_intViewRule.operator=">";
			}
			if (_operationSpinner.getSelectedItemPosition()==5)
			{
				_intViewRule.operator="!=";
			}
		}
    }
    
    // Called before the activity is destroyed due to orientation change:
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	updateViewRule();
    	b.putInt("operation", _operation);
    	b.putBoolean("is_or", !_isAnd.isChecked());
    	b.putString("field", _intViewRule.dbField);
    	b.putString("db_string", _intViewRule.getDatabaseString());
    	if (_operation==ViewRulesList.EDIT)
    	{
    		b.putInt("index", _index);
    		b.putInt("lock_level", _lockLevel);
    	}
    }

	@Override
	public void handleSave()
	{
		long valueEntered = 0;
		
		if (!_intViewRule.getDbField().equals("timer_start_time"))
		{
			// Make sure a valid value is entered:
			if (_intValue.getText().toString().length()==0)
			{
				Util.popup(EditIntViewRule.this,R.string.Please_enter_a_value);
				return;
			}
			
			// Extract the value entered:
			try
			{
				valueEntered = Long.parseLong(_intValue.getText().toString());
			}
			catch (NumberFormatException e)
			{
				Util.popup(EditIntViewRule.this,R.string.Please_enter_a_value);
				return;
			}
		}
		
		// Update the _intViewRule to match the data in the views:
		updateViewRule();
		
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _intViewRule.dbField);
        b.putString("db_string", _intViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		EditIntViewRule.this.setResult(RESULT_OK,i);		        
		finish();
	}
}
