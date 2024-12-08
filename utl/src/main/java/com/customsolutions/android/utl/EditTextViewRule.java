package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

/** <pre>
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
public class EditTextViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of TextViewRule, that we will be working on:
	private TextViewRule _textViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;
	
	// Views we need to keep track of:
	private CheckedTextView _isAnd;
	private Spinner _containsOrExact;
	private EditText _searchText;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a text view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_text_view_rule);
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras;
        if (savedInstanceState==null)
        	extras = this.getIntent().getExtras();
        else
        	extras = savedInstanceState;
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditTaskViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditTaskViewRule.onCreate().");
        	finish();
        	return;
        }
        _operation = extras.getInt("operation");
        
        if (_operation==ViewRulesList.EDIT)
        {
        	if (!extras.containsKey("index") || !extras.containsKey("lock_level"))
        	{
        		Util.log("Missing inputs in EditTaskViewRule.onCreate().");
            	finish();
            	return;
        	}
        	_index = extras.getInt("index");
        	_lockLevel = extras.getInt("lock_level");
        }
        
        // Construct the TextViewRule object, which will hold the options we are working on:
        if (!extras.containsKey("db_string"))
        {
        	_textViewRule = new TextViewRule(extras.getString("field"),"",false);
        }
        else
        {
        	_textViewRule = new TextViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the title:
        getSupportActionBar().setTitle(Util.getString(R.string.Filter_on)+" "+Util.dbFieldToDescription.get(
        	extras.getString("field")));
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));

        initBannerAd();

        //
        // Initialize the views based on the data passed in...
        //
        
        // Previous rule must also match:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_text_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_text_vr_separator).setVisibility(View.GONE);
        }
        
        
        // "contains" or "exact"
        _containsOrExact = (Spinner)findViewById(R.id.edit_text_vr_contains_or_exact);
        _containsOrExact.setSelection(_textViewRule.exactMatch ? 1 : 0);
        
        // Text to search for:
        _searchText = (EditText)findViewById(R.id.edit_text_vr_text);
        _searchText.setText(_textViewRule.searchString);
        
        // Intro text:
        TextView introText = (TextView)findViewById(R.id.edit_text_vr_intro);
        introText.setText(Util.getString(R.string.Find_tasks_whose)+" "+Util.
        	dbFieldToDescription.get(_textViewRule.getDbField())+"...");
        
        //
        // Button handlers...
        //
        
        // And/or button:
        this.findViewById(R.id.edit_text_vr_is_and_checkbox).setOnClickListener(new 
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
    	_textViewRule.exactMatch = _containsOrExact.getSelectedItemPosition()==0 ?
			false : true;
		if (_searchText.getText().toString().length()==0)
		{
			_textViewRule.searchString = " ";
		}
		else
		{
			_textViewRule.searchString = _searchText.getText().toString();
		}
    }
    
    // Called before the activity is destroyed due to orientation change:
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	updateViewRule();
    	b.putInt("operation", _operation);
    	b.putBoolean("is_or", !_isAnd.isChecked());
    	b.putString("field", _textViewRule.dbField);
    	b.putString("db_string", _textViewRule.getDatabaseString());
    	if (_operation==ViewRulesList.EDIT)
    	{
    		b.putInt("index", _index);
    		b.putInt("lock_level", _lockLevel);
    	}
    }

	@Override
	public void handleSave()
	{
		// Make sure some text is entered:
		if (_searchText.getText().toString().length()==0)
		{
			Util.popup(EditTextViewRule.this,R.string.You_must_enter_some_text);
			return;
		}
		
		// Update the _textViewRule object to match the data in the views:
    	updateViewRule();
		
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _textViewRule.getDbField());
        b.putString("db_string", _textViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		EditTextViewRule.this.setResult(RESULT_OK,i);		        
		finish();
	}
}
