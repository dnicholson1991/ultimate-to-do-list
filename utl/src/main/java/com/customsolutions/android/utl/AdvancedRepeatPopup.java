package com.customsolutions.android.utl;

// Popup activity to display the advanced repeat options.  The optional input is a "text" field,
// which contains a standard string describing the repeating pattern.  The field passed in must NOT
// be a localized string.

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;

public class AdvancedRepeatPopup extends UtlSaveCancelPopupActivity
{
	// Quick reference to key views:
	RadioButton _format1Radio;
	RadioButton _format2Radio;
	RadioButton _format3Radio;
	EditText _format1Increment;
	Spinner _format1Unit;
	Spinner _format2Location;
	Spinner _format2DayOfWeek;
	CheckBox _format3Sun;
	CheckBox _format3Mon;
	CheckBox _format3Tue;
	CheckBox _format3Wed;
	CheckBox _format3Thu;
	CheckBox _format3Fri;
	CheckBox _format3Sat;
	CheckBox _format3Weekday;
	CheckBox _format3Weekend;
	
	// Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        Util.log("Launched advanced repeat popup.");
        
        setContentView(R.layout.advanced_repeat);
        
        // Set the title and top icon:
        getSupportActionBar().setTitle(Util.getString(R.string.Advanced_Repeat_Pattern));
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_repeating_large));
        
        // Link to key views:
        _format1Radio = (RadioButton)findViewById(R.id.advanced_rep_format_1_radio);
        _format2Radio = (RadioButton)findViewById(R.id.advanced_rep_format_2_radio);
        _format3Radio = (RadioButton)findViewById(R.id.advanced_rep_format_3_radio);
        _format1Increment = (EditText)findViewById(R.id.advanced_rep_format_1_increment);
        _format1Unit = (Spinner)findViewById(R.id.advanced_rep_format_1_unit);
        _format2Location = (Spinner)findViewById(R.id.advanced_rep_format_2_location);
        _format2DayOfWeek = (Spinner)findViewById(R.id.advanced_rep_format_2_dow);
        _format3Sun = (CheckBox)findViewById(R.id.advanced_rep_format_3_sun);
        _format3Mon = (CheckBox)findViewById(R.id.advanced_rep_format_3_mon);
        _format3Tue = (CheckBox)findViewById(R.id.advanced_rep_format_3_tue);
        _format3Wed = (CheckBox)findViewById(R.id.advanced_rep_format_3_wed);
        _format3Thu = (CheckBox)findViewById(R.id.advanced_rep_format_3_thu);
        _format3Fri = (CheckBox)findViewById(R.id.advanced_rep_format_3_fri);
        _format3Sat = (CheckBox)findViewById(R.id.advanced_rep_format_3_sat);
        _format3Weekday = (CheckBox)findViewById(R.id.advanced_rep_format_3_weekday);
        _format3Weekend = (CheckBox)findViewById(R.id.advanced_rep_format_3_weekend);
    }
    
    // Overrides the default size function, taking into account the small size of this popup:
    @Override
    protected Point getPopupSize()
    {
    	Point size = super.getPopupSize();
    	int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
    	if (getOrientation()==ORIENTATION_LANDSCAPE)
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
				size.x = _screenWidth/2;
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				size.y = _screenHeight*7/10;
				return size;
			}
    	}
    	
    	return size;
    }
    
    @SuppressLint("NewApi")
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
           
    	// Tapping on a radio button unchecks the others.  We have to implement this ourselves due 
        // to layout requirements and Android limitations.
        _format1Radio.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_format1Radio.setChecked(true);
				_format2Radio.setChecked(false);
				_format3Radio.setChecked(false);
			}
		});
        _format2Radio.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_format1Radio.setChecked(false);
				_format2Radio.setChecked(true);
				_format3Radio.setChecked(false);
			}
		});
        _format3Radio.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_format1Radio.setChecked(false);
				_format2Radio.setChecked(false);
				_format3Radio.setChecked(true);
			}
		});
        
        // Updating values causes the appropriate radio button to be checked:
        _format1Increment.addTextChangedListener(new TextWatcher() {
        	public void afterTextChanged(Editable s) {
        		_format1Radio.performClick();
        	}
        	public void beforeTextChanged(CharSequence s, int start, int count, int after){}
        	public void onTextChanged(CharSequence s, int start, int before, int count){}
        });
        _format1Unit.setOnItemSelectedListener(new OnItemSelectedListener() {	
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		_format1Radio.performClick();
            }
        	public void onNothingSelected(AdapterView<?>  parent) { }
        });
        _format2Location.setOnItemSelectedListener(new OnItemSelectedListener() {	
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		_format2Radio.performClick();
            }
        	public void onNothingSelected(AdapterView<?>  parent) { }
        });
        _format2DayOfWeek.setOnItemSelectedListener(new OnItemSelectedListener() {	
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		_format2Radio.performClick();
            }
        	public void onNothingSelected(AdapterView<?>  parent) { }
        });
        View.OnClickListener _format3CheckboxListener = new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_format3Radio.performClick();
			}
		};
		_format3Sun.setOnClickListener(_format3CheckboxListener);
		_format3Mon.setOnClickListener(_format3CheckboxListener);
		_format3Tue.setOnClickListener(_format3CheckboxListener);
		_format3Wed.setOnClickListener(_format3CheckboxListener);
		_format3Thu.setOnClickListener(_format3CheckboxListener);
		_format3Fri.setOnClickListener(_format3CheckboxListener);
		_format3Sat.setOnClickListener(_format3CheckboxListener);
		_format3Weekday.setOnClickListener(_format3CheckboxListener);
		_format3Weekend.setOnClickListener(_format3CheckboxListener);
		
		// Checking a day of the week should uncheck the weekday and weekend options:
		CompoundButton.OnCheckedChangeListener dowListener = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (isChecked)
				{
					_format3Weekday.setChecked(false);
					_format3Weekend.setChecked(false);
				}
			}
		};
		_format3Sun.setOnCheckedChangeListener(dowListener);
		_format3Mon.setOnCheckedChangeListener(dowListener);
		_format3Tue.setOnCheckedChangeListener(dowListener);
		_format3Wed.setOnCheckedChangeListener(dowListener);
		_format3Thu.setOnCheckedChangeListener(dowListener);
		_format3Fri.setOnCheckedChangeListener(dowListener);
		_format3Sat.setOnCheckedChangeListener(dowListener);
		
		// Checking a weekend or weekday button should uncheck everything else.
		_format3Weekday.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (isChecked)
				{
					_format3Sun.setChecked(false);
					_format3Mon.setChecked(false);
					_format3Tue.setChecked(false);
					_format3Wed.setChecked(false);
					_format3Thu.setChecked(false);
					_format3Fri.setChecked(false);
					_format3Sat.setChecked(false);
					_format3Weekend.setChecked(false);
				}
			}
		});
		_format3Weekend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
			{
				if (isChecked)
				{
					_format3Sun.setChecked(false);
					_format3Mon.setChecked(false);
					_format3Tue.setChecked(false);
					_format3Wed.setChecked(false);
					_format3Thu.setChecked(false);
					_format3Fri.setChecked(false);
					_format3Sat.setChecked(false);
					_format3Weekday.setChecked(false);
				}
			}
		});
		
		// Set the initial values for the views if a default was passed in:
		Bundle extras = getIntent().getExtras();
        if (extras!=null && extras.containsKey("text"))
        {
        	AdvancedRepeat ar = new AdvancedRepeat();
        	if (ar.initFromString(extras.getString("text")))
        	{
        		if (ar.formatNum==1)
        		{
        			_format1Increment.setText(Integer.valueOf(ar.increment).toString());
        			_format1Unit.setSelection(getIndex(ar.unit,R.array.repeat_intervals_db));
        			if (Build.VERSION.SDK_INT >= 11)
        				new SetRadioDelay().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_format1Radio);
        			else
        				new SetRadioDelay().execute(_format1Radio);
        		}
        		else if (ar.formatNum==2)
        		{
        			_format2Location.setSelection(getIndex(ar.monthLocation,
        				R.array.month_locations_db));
        			_format2DayOfWeek.setSelection(getIndex(ar.dayOfWeek,R.array.days_of_week_db));
        			if (Build.VERSION.SDK_INT >= 11)
        				new SetRadioDelay().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_format2Radio);
        			else
        				new SetRadioDelay().execute(_format2Radio);
        		}
        		else if (ar.formatNum==3)
        		{
        			for (int i=0; i<ar.daysOfWeek.length; i++)
        			{
        				if (ar.daysOfWeek[i].equals("sun"))
        					_format3Sun.setChecked(true);
        				if (ar.daysOfWeek[i].equals("mon"))
        					_format3Mon.setChecked(true);
        				if (ar.daysOfWeek[i].equals("tue"))
        					_format3Tue.setChecked(true);
        				if (ar.daysOfWeek[i].equals("wed"))
        					_format3Wed.setChecked(true);
        				if (ar.daysOfWeek[i].equals("thu"))
        					_format3Thu.setChecked(true);
        				if (ar.daysOfWeek[i].equals("fri"))
        					_format3Fri.setChecked(true);
        				if (ar.daysOfWeek[i].equals("sat"))
        					_format3Sat.setChecked(true);
        				if (ar.daysOfWeek[i].equals("weekday"))
        					_format3Weekday.setChecked(true);
        				if (ar.daysOfWeek[i].equals("weekend"))
        					_format3Weekend.setChecked(true);
        			}
        			if (Build.VERSION.SDK_INT >= 11)
        				new SetRadioDelay().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_format3Radio); 
        			else
        				new SetRadioDelay().execute(_format3Radio); 
        		}
        	}
        }
    }
    
    // This sets the radio for an option after a delay, compensating for Android calling the spinner
    // change listeners after onPostCreate().
    private class SetRadioDelay extends AsyncTask<RadioButton,Void,Void>
    {
    	private RadioButton _radio;
    	
    	protected Void doInBackground(RadioButton... r)
    	{
    		_radio = r[0];
    		try
    		{
    			Thread.sleep(100);
    		}
	    	catch (InterruptedException e)
	    	{
	    	}
	    	return null;
    	}
    	
    	protected void onPostExecute(Void v)
    	{
    		_radio.performClick();
    	}
    }   
    
    // Get the index of a specific string within an array.  The inputs are the string and the array
    // ID.
    private int getIndex(String s, int arrayID)
    {
    	String[] array = getResources().getStringArray(arrayID);
    	for (int i=0; i<array.length; i++)
    	{
    		if (array[i].equals(s))
    			return i;
    	}
    	return 0;
    }
    
    // Handle a save.  A standard (non-localized) string is passed back to the caller.
	@Override
	public void handleSave()
	{
		// Generate a standard advanced repeat string from the inputs:
		String result = "";
		if (_format1Radio.isChecked())
		{
			if (_format1Increment.getText()==null || _format1Increment.getText().toString().length()==0)
			{
				Util.popup(this, R.string.Please_enter_a_number);
				return;
			}
			String[] units = getResources().getStringArray(R.array.repeat_intervals_db);
			result = "every "+_format1Increment.getText().toString()+" "+
				units[_format1Unit.getSelectedItemPosition()];
		}
		else if (_format2Radio.isChecked())
		{
			String[] locations = getResources().getStringArray(R.array.month_locations_db);
			String[] daysOfWeek = getResources().getStringArray(R.array.days_of_week_db);
			result = "the "+locations[_format2Location.getSelectedItemPosition()]+" "+
				daysOfWeek[_format2DayOfWeek.getSelectedItemPosition()]+" of each month";
		}
		else if (_format3Radio.isChecked())
		{
			// Get a string listing the days of week selected:
			result = "every ";
			ArrayList<String> items = new ArrayList<String>();
			if (_format3Weekend.isChecked())
			{
				items.add("weekend");
			}
			else
			{
				if (_format3Sat.isChecked())
				{
					items.add("sat");
				}
				if (_format3Sun.isChecked())
				{
					items.add("sun");
				}
			}
			if (_format3Weekday.isChecked())
			{
				items.add("weekday");
			}
			else
			{
				if (_format3Mon.isChecked())
				{
					items.add("mon");
				}
				if (_format3Tue.isChecked())
				{
					items.add("tue");
				}
				if (_format3Wed.isChecked())
				{
					items.add("wed");
				}
				if (_format3Thu.isChecked())
				{
					items.add("thu");
				}
				if (_format3Fri.isChecked())
				{
					items.add("fri");
				}
			}
			for (int i=0; i<items.size(); i++)
			{
				if (i>0)
					result += ",";
				result += items.get(i);
			}
		}
		else
		{
			Util.popup(this, R.string.Please_select_a_format);
			return;
		}
		
		// Try to create and initialize an AdvancedRepeat object.  If it fails,
        // then the user typed something incorrectly.
        AdvancedRepeat ar = new AdvancedRepeat();
        if (!ar.initFromString(result))
        {
            Util.popup(this, R.string.This_is_not_properly_formatted);
            return;
        }
        
        // If we get here, the string is valid.  Return it back to the caller:
        // Send the response back with the new name.
        Bundle b = new Bundle();
        b.putString("text",ar.getStandardString());
        Intent i = new Intent();
        i.putExtras(b);
        setResult(RESULT_OK,i);
        finish();
	}

}
