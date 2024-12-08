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
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class EditDateViewRule extends UtlSaveCancelPopupActivity
{
	// An instance of DateViewRule, that we will be working on:
	private DateViewRule _dateViewRule;
	
	private int _operation;
	private int _index;
	private int _lockLevel;
	
	// Views we need to keep track of:
	CheckedTextView _isAnd;
	TextView _introText;
	Spinner _comparisonOperators;
	EditText _numDays;
	Spinner _pastFuture;
	Button _chooseExactDate;
	Button _exactDate;
	Button _chooseRelativeDate;
	CheckedTextView _includeEmptyDate;
	
	// Request code for the date picker:
	static private final int CHOOSE_DATE = 1;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
        
        Util.log("Begin editing a date view rule");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.edit_date_view_rule);
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras;
        if (savedInstanceState==null)
        	extras = this.getIntent().getExtras();
        else
        	extras = savedInstanceState;
        if (extras==null)
        {
            Util.log("Null Bundle passed into EditDateViewRule.onCreate()");
            finish();
            return;
        }
        
        // Verify required parameters are passed in:
        if (!extras.containsKey("operation") || !extras.containsKey("field"))
        {
        	Util.log("Missing inputs in EditDateViewRule.onCreate().");
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
        
        // Construct the DateViewRule object, which will hold the options we are working on:
        if (!extras.containsKey("db_string"))
        {
        	_dateViewRule = new DateViewRule(extras.getString("field"),true,0,"<=",false);
        }
        else
        {
        	_dateViewRule = new DateViewRule(extras.getString("field"),
        		extras.getString("db_string"));
        }
        
        // Set the title:
        getSupportActionBar().setTitle(Util.getString(R.string.Filter_on)+" "+Util.dbFieldToDescription.get(
        	extras.getString("field")));
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));
                
        // Link local variables to layout items:
        _isAnd = (CheckedTextView)findViewById(R.id.edit_date_vr_is_and_checkbox);
        _introText = (TextView)findViewById(R.id.edit_date_vr_intro_text);
        _comparisonOperators = (Spinner)findViewById(R.id.edit_date_vr_comparison_operators);
        _numDays = (EditText)findViewById(R.id.edit_date_vr_num_days);
        _pastFuture = (Spinner)findViewById(R.id.edit_date_vr_past_future);
        _chooseExactDate = (Button)findViewById(R.id.edit_date_vr_exact_date_switch);
        _exactDate = (Button)findViewById(R.id.edit_date_vr_exact_date_button);
        _chooseRelativeDate = (Button)findViewById(R.id.edit_date_vr_relative_date_button);
        _includeEmptyDate = (CheckedTextView)findViewById(R.id.edit_date_vr_include_empty_checkbox);

        initBannerAd();

        //
        // Initialize the views based on the data passed in...
        //
        
        // Previous rule must also match:
    	_isAnd = (CheckedTextView)findViewById(R.id.edit_date_vr_is_and_checkbox);
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	_isAnd.setChecked(!extras.getBoolean("is_or"));
        }
        else
        {
        	_isAnd.setVisibility(View.GONE);
        	this.findViewById(R.id.edit_date_vr_separator).setVisibility(View.GONE);
        }
        
        // Introductory text:
        _introText.setText(Util.getString(R.string.Find_tasks_whose)+" "+
        	Util.dbFieldToDescription.get(extras.getString("field")));
        
        // Operator (<=, =, >=)
        if (_dateViewRule.operator.equals("<="))
        {
        	_comparisonOperators.setSelection(0);
        }
        else if (_dateViewRule.operator.equals("="))
        {
        	_comparisonOperators.setSelection(1);
        }
        else if (_dateViewRule.operator.equals(">="))
        {
        	_comparisonOperators.setSelection(2);
        }
    	
        // Include empty dates:
        _includeEmptyDate.setText(Util.getString(R.string.Include_tasks_with_no_date)+" "+
        	Util.dbFieldToDescription.get(extras.getString("field")));
        _includeEmptyDate.setChecked(_dateViewRule.includeEmptyDates);
        
        // Initialize the views that are dependent on isRelative...
        refreshData();
 
        //
        // Button handlers...
        //
        
        // And/or button:
        _isAnd.setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				CheckedTextView ctv = (CheckedTextView)v;
				ctv.toggle();
			}
		});

        // Switch to exact date:
        _chooseExactDate.setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				// Update some of the parameters of _dateViewRule, in case we come back:
				_dateViewRule.isRelative = false;
				try
				{
					if (_pastFuture.getSelectedItemPosition()==0)
					{
						_dateViewRule.daysFromToday = -1 * Long.parseLong(_numDays.
							getText().toString());
					}
					else
					{
						_dateViewRule.daysFromToday = Long.parseLong(_numDays.getText().
							toString());
					}
				}
				catch (NumberFormatException e)
				{					
				}
				refreshData();
			}
		});
        
        // Call the exact date chooser:
        _exactDate.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(EditDateViewRule.this,DateChooser.class);
				i.putExtra("default_date", _dateViewRule.absoluteDate);
				i.putExtra("prompt", Util.getString(R.string.Select_a_Date));
				startActivityForResult(i,CHOOSE_DATE);
			}
		});
        
        // Switch to relative date:
        _chooseRelativeDate.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Update some of the parameters of _dateViewRule, in case we come back:
				_dateViewRule.isRelative = true;
				_dateViewRule.absoluteDate = Util.dateToMillis(_exactDate.getText().
					toString());
				refreshData();
			}
		});
        
        // Include empty dates:
        _includeEmptyDate.setOnClickListener(new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				CheckedTextView ctv = (CheckedTextView)v;
				ctv.toggle();
			}
		});
    }
    
    // Refresh the display:
    void refreshData()
    {    	
    	if (_dateViewRule.isRelative)
    	{
    		// Date is relative to today.  Hide views we don't want to see:
    		findViewById(R.id.edit_date_vr_exact_date_container).setVisibility(View.GONE);
    		_chooseRelativeDate.setVisibility(View.GONE);
    		findViewById(R.id.edit_date_vr_num_days_container).setVisibility(View.VISIBLE);
    		_chooseExactDate.setVisibility(View.VISIBLE);
    		
    		// Initialize the data in the views:
    		_numDays.setText(Long.valueOf(Math.abs(_dateViewRule.daysFromToday)).toString());
    		if (_dateViewRule.daysFromToday>=0)
    		{
    			_pastFuture.setSelection(1);
    		}
    		else
    		{
    			_pastFuture.setSelection(0);
    		}
    	}
    	else
    	{
    		// Date is absolute.  Hide views we don't want to see: 
    		findViewById(R.id.edit_date_vr_exact_date_container).setVisibility(View.VISIBLE);
    		_chooseRelativeDate.setVisibility(View.VISIBLE);
    		findViewById(R.id.edit_date_vr_num_days_container).setVisibility(View.GONE);
    		_chooseExactDate.setVisibility(View.GONE);
    		
    		// Initialize the data in the views:
    		if (_dateViewRule.absoluteDate==0)
    		{
    			_dateViewRule.absoluteDate = System.currentTimeMillis();
    		}
    		_exactDate.setText(Util.getDateString(_dateViewRule.absoluteDate));
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
    	
    	if (requestCode!=CHOOSE_DATE)
    	{
    		// We only call one type of activity.
    		return;
    	}
    	
    	Bundle b = intent.getExtras();
    	if (!b.containsKey("chosen_date"))
    	{
    		Util.log("Missing chosen_date in EditDateViewRule.java");
    		return;
    	}
    	
    	_dateViewRule.absoluteDate = b.getLong("chosen_date");
    	refreshData();
    }

    // Update the view rule object based on the data in the views:
    protected void updateViewRule()
    {
		if (_comparisonOperators.getSelectedItemPosition()==0)
		{
			_dateViewRule.operator = "<=";
		}
		else if (_comparisonOperators.getSelectedItemPosition()==1)
		{
			_dateViewRule.operator = "=";
		}
		else
		{
			_dateViewRule.operator = ">=";
		}
		if (_dateViewRule.isRelative)
		{
			try
			{
				if (_pastFuture.getSelectedItemPosition()==0)
				{
					_dateViewRule.daysFromToday = -1 * Long.parseLong(_numDays.
						getText().toString());
				}
				else
				{
					_dateViewRule.daysFromToday = Long.parseLong(_numDays.getText().
						toString());
				}	
			}
			catch (NumberFormatException e)
			{
				_dateViewRule.daysFromToday = 0;
			}
		}
		else
		{
			_dateViewRule.absoluteDate = Util.dateToMillis(_exactDate.getText().
				toString());
		}
		_dateViewRule.includeEmptyDates = _includeEmptyDate.isChecked();
    }

    // Called before the activity is destroyed due to orientation change:
    @Override
    protected void onSaveInstanceState(Bundle b)
    {
    	updateViewRule();
    	b.putInt("operation", _operation);
    	b.putBoolean("is_or", !_isAnd.isChecked());
    	b.putString("field", _dateViewRule.dbField);
    	b.putString("db_string", _dateViewRule.getDatabaseString());
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
		
		if (_dateViewRule.isRelative)
		{
			// Make sure something was specified:
			if (_numDays.getText().length()==0)
			{
				Util.popup(EditDateViewRule.this,R.string.Please_enter_a_value);
				return;
			}
			
			// Extract the value entered:
			try
			{
				valueEntered = Long.parseLong(_numDays.getText().toString());
			}
			catch (NumberFormatException e)
			{
				Util.popup(EditDateViewRule.this,R.string.Please_enter_a_value);
				return;
			}
		}
		
		// Update the _dateViewRule object to match the data in the views:
		updateViewRule();
		
		// Generate the Intent and extras to pass back:
		Intent i = new Intent();
        Bundle b = new Bundle();
        b.putInt("operation", _operation);
        b.putString("field", _dateViewRule.getDbField());
        b.putString("db_string", _dateViewRule.getDatabaseString());
        if (_operation==ViewRulesList.ADD || _lockLevel==0)
        {
        	b.putBoolean("is_or", !_isAnd.isChecked());
        }
        if (_operation==ViewRulesList.EDIT)
        {
        	b.putInt("index",_index);
        }
        i.putExtras(b);
		EditDateViewRule.this.setResult(RESULT_OK,i);		        
		finish();
	}
}
