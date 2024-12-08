package com.customsolutions.android.utl;

// This class controls the dates that display in the DateChooser activity.

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

public class DateChooserAdapter extends BaseAdapter 
{
    private Context context;
    private UtlActivity _a;
    private Resources _res;

    // Stores the first day we will display:
    GregorianCalendar mCalendar;
    
    // Stores the currently selected date:
    GregorianCalendar mDefaultDate;
    
    // This stores the current date:
    GregorianCalendar mCurrentDate;
    
    // The number of days we will display:
    int daysToDisplay;
    
    // The month we're looking at:
    int currentMonth;
    
    public DateChooserAdapter(Context c)
    {
        context = c;
        _a = (UtlActivity)c;
        _res = _a.getResources();
    }
    
    // Initialize the data needed to display the calendar:
    public void init(int monthNum, int year, long defaultDate)
    {
        // Store the default date:
        mDefaultDate = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        mDefaultDate.setTimeInMillis(defaultDate);
        mDefaultDate.set(Calendar.HOUR, 0);
        mDefaultDate.set(Calendar.MINUTE, 0);
        mDefaultDate.set(Calendar.SECOND, 0);
        mDefaultDate.set(Calendar.MILLISECOND, 0);
        mDefaultDate.set(Calendar.AM_PM, Calendar.AM);
        
        // Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long timeZoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	
        // Store the current Date:
        mCurrentDate = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        mCurrentDate.setTimeInMillis(System.currentTimeMillis()+timeZoneOffset);
        mCurrentDate.set(Calendar.HOUR, 0);
        mCurrentDate.set(Calendar.MINUTE, 0);
        mCurrentDate.set(Calendar.SECOND, 0);
        mCurrentDate.set(Calendar.MILLISECOND, 0);
        mCurrentDate.set(Calendar.AM_PM, Calendar.AM);
        
        // Get a calendar object that is set to the first day we're interested in
        // (a Sunday or Monday)
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.AM_PM, Calendar.AM);
        c.set(Calendar.DATE, 1);
        c.set(Calendar.MONTH, monthNum);
        c.set(Calendar.YEAR, year);
        int daysInMonth = c.getActualMaximum(Calendar.DATE);
        int dow = c.get(Calendar.DAY_OF_WEEK);
        currentMonth = c.get(Calendar.MONTH);
        int rollBackDays;
        if (Util.settings.getString("week_start", "Sunday").equals("Sunday"))
        {
            rollBackDays = dow-Calendar.SUNDAY;
        }
        else
        {
            // Week starts on Monday:
            rollBackDays = dow-Calendar.MONDAY;
            if (rollBackDays<0)
            {
                rollBackDays = 6;
            }
        }
        c.add(Calendar.DATE, 0-rollBackDays);
        mCalendar = c;
        
        // Get the number of days that we need to display.  This will always be a multiple
        // of 7.
        daysToDisplay = rollBackDays+daysInMonth;
        while (daysToDisplay % 7 != 0)
        {
            daysToDisplay++;
        }
    }
    
    @Override
    public int getCount() {
        return (daysToDisplay+7);
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    // Given the position, get the date in ms.  Returns 0 if the position doesn't have
    // a date.
    public long getDateForPosition(int position)
    {
        if (position<7)
        {
            return 0;
        }
        else
        {
            GregorianCalendar c = (GregorianCalendar)mCalendar.clone();
            int daysToIncrement = position-7;
            c.add(Calendar.DATE, daysToIncrement);
            return c.getTimeInMillis();
        }
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) 
    {
        if (position<7)
        {
            // Return a the day of the week:
            TextView t;
            if (convertView == null) 
            {
                t = new TextView(context);
                t.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
                t.setGravity(Gravity.CENTER);
            }
            else
            {
            	if (convertView.getClass().getName().equals("android.widget.TextView"))
            	{
            		t = (TextView)convertView;
            	}
            	else
            	{
            		t = new TextView(context);
                    t.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                    t.setGravity(Gravity.CENTER);
            	}
            }
            if (Util.settings.getString("week_start", "Sunday").equals("Sunday"))
            {
                if (position==0)
                {
                    t.setText(Util.getString(R.string.Sunday));                
                }
                else if (position==1)
                {
                    t.setText(Util.getString(R.string.Monday));                
                }
                else if (position==2)
                {
                    t.setText(Util.getString(R.string.Tuesday));                
                }
                else if (position==3)
                {
                    t.setText(Util.getString(R.string.Wednesday));                
                }
                else if (position==4)
                {
                    t.setText(Util.getString(R.string.Thursday));                
                }
                else if (position==5)
                {
                    t.setText(Util.getString(R.string.Friday));                
                }
                else if (position==6)
                {
                    t.setText(Util.getString(R.string.Saturday));                
                }
            }
            else
            {
                if (position==0)
                {
                    t.setText(Util.getString(R.string.Monday));                
                }
                else if (position==1)
                {
                    t.setText(Util.getString(R.string.Tuesday));                
                }
                else if (position==2)
                {
                    t.setText(Util.getString(R.string.Wednesday));                
                }
                else if (position==3)
                {
                    t.setText(Util.getString(R.string.Thursday));                
                }
                else if (position==4)
                {
                    t.setText(Util.getString(R.string.Friday));                
                }
                else if (position==5)
                {
                    t.setText(Util.getString(R.string.Saturday));                
                }
                else if (position==6)
                {
                    t.setText(Util.getString(R.string.Sunday));                
                }
            }
            return t;
        }
        else
        {
            // Return the actual day as a button:
            GregorianCalendar c = (GregorianCalendar)mCalendar.clone();
            int daysToIncrement = position-7;
            c.add(Calendar.DATE, daysToIncrement);
            
            Button b;
            if (convertView == null) 
            {
                b = new Button(context);
                b.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            }
            else
            {
            	if (convertView.getClass().getName().equals("android.widget.Button"))
            	{
            		b = (Button)convertView;
            	}
            	else
            	{
            		b = new Button(context);
                    b.setLayoutParams(new GridView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            	}
            }
            b.setText(Integer.toString(c.get(Calendar.DATE)));
            
            // Set colors and styles based on whether the date is part of the current month:
            if (c.get(Calendar.MONTH) == currentMonth)
            {
           		b.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.date_picker_text_color)));
           		b.setTypeface(null,Typeface.NORMAL);
                
                // Set the background and text based on whether this is the default date:
                if (c.equals(mDefaultDate))
                {
                	b.setBackgroundResource(_a.resourceIdFromAttr(R.attr.button_press));
                	b.setTextColor(Util.colorFromAttr(_a,R.attr.date_picker_text_color_inv));
                }
                
                // Ditto if this is the current date.
                if (c.equals(mCurrentDate))
                {
               		b.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.date_picker_current_date_color)));
               		b.setTypeface(null,Typeface.BOLD);
                }
            }
            else
            {
            	// Set the background differently if the date is the default date.
                if (c.equals(mDefaultDate))
                {
                	b.setBackgroundResource(_a.resourceIdFromAttr(R.attr.button_press));
                    b.setTextColor(Util.colorFromAttr(_a,R.attr.date_picker_text_color_inv));
                }
                else
                {
                    b.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.
                        date_picker_out_of_month_color)));
                }
                b.setTypeface(null,Typeface.NORMAL);
            }
            
            // Store the position in the Button's view.  We will need to extract it 
            // if the button is clicked:
            b.setId(position);
            
            b.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick (View v)
                {
                    DateChooser dc = (DateChooser)context;
                    dc.handle_date_selected(v.getId());
                }
            });
            
            return b;
        }
    }

}
