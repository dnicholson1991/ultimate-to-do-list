package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.util.Log;

// This is a subclass of ViewRule that handles date comparisons for the due_date field.
// The due_date field has some special options that require it to be in a class of its
// own.

public class DueDateViewRule extends ViewRule 
{
    // This is true if the date comparison is relative to today, false if this is an
    // absolute date comparison
    public boolean isRelative;
 
    // The operator.  One of ">=", "<=", or "=".
    public String operator;
    
    // For a relative comparison, this is the number of days in the future.  Negative 
    // numbers refer to the past.  0 means today.
    public long daysFromToday;
    
    // For an absolute comparison, this is a date in milliseconds.  This must correspond
    // to midnight on the day of interest.
    public long absoluteDate;
    
    // For tasks that are due on an exact date.  This flag determines if they are only
    // visible if they are due today.
    public Boolean showAllDueOnExact;  // If true show all, else show only if due today.
    
    // For tasks that are due on an exact date but are optional, this flag determines
    // if they are only visible if they are due today.
    public Boolean showAllOptionalOnExact;  // If true show all, else show only if due today.
    
    // Are we including tasks with no date specified?
    public boolean includeEmptyDates;
    
    // Construct the class from user inputs.  The isRelative1 input below determines whether
    // the value is an offset from today, or an absolute timestamp in ms.
    public DueDateViewRule(boolean isRelative1, long value, String operator1, 
        boolean showAllDueOnExact1, boolean showAllOptionalOnExact1, boolean includeEmpty)
    {
        dbField = "due_date";
        isRelative = isRelative1;
        operator = operator1;
        if (isRelative)
        {
            daysFromToday = value;
        }
        else
        {
            absoluteDate = Util.getMidnight(value);
        }
        showAllDueOnExact = showAllDueOnExact1;
        showAllOptionalOnExact = showAllOptionalOnExact1;
        includeEmptyDates = includeEmpty;
    }
    
    // Construct the class from a database string:
    public DueDateViewRule(String dbRecord)
    {
        dbField = "due_date";
        
        // The parameters are separated by commas:
        String[] values = dbRecord.split(",");
        isRelative = Util.stringToBoolean(values[0]);
        operator = values[1];
        if (isRelative)
        {
            daysFromToday = Long.parseLong(values[2]);
        }
        else
        {
            absoluteDate = Long.parseLong(values[2]);
        }
        showAllDueOnExact = Util.stringToBoolean(values[3]);
        showAllOptionalOnExact = Util.stringToBoolean(values[4]);
        includeEmptyDates = Util.stringToBoolean(values[5]);
    }
    
    @Override
    public String getDatabaseString() 
    {
        // The parameters are separated by commas:
        String result = Util.booleanToString(isRelative) + "," + operator + ",";
        if (isRelative)
        {
            result += daysFromToday;
        }
        else
        {
            result += absoluteDate;
        }
        result += ","+Util.booleanToString(showAllDueOnExact)+","+
            Util.booleanToString(showAllOptionalOnExact)+","+
            Util.booleanToString(includeEmptyDates);
        
        return result;
    }

    @Override
    public String getReadableString() 
    {
        String result = Util.getString(R.string.DueDate);
        
        if (operator.equals("<="))
        {
            result += " "+Util.getString(R.string.OnOrBefore)+" ";
        }
        else if (operator.equals(">="))
        {
            result += " "+Util.getString(R.string.OnOrAfter)+" ";
        }
        else
        {
            result += " "+Util.getString(R.string.Is)+" ";;
        }
        
        if (!isRelative)
        {
            result += Util.getDateString(absoluteDate);
        }
        else
        {
            if (daysFromToday<0)
            {
                result += (daysFromToday*-1)+" "+Util.getString(R.string.DaysInPast);
            }
            else if (daysFromToday>0)
            {
                result += daysFromToday+" "+Util.getString(R.string.DaysInFuture);
            }
            else
            {
                result += Util.getString(R.string.Today);
            }
        }
        if (includeEmptyDates)
        {
        	result += Util.getString(R.string.or_is_blank);
        }
        return result;    
    }

    @Override
    public String getSQLWhere() 
    {
        long queryDate;
        long queryDate2;
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));  // Set to current time.
        
        // Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long zoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	long baseTime = System.currentTimeMillis()+zoneOffset;
    	
        // To begin, we need an expression that checks to see if the task is due today.
    	c.setTimeInMillis(baseTime);
        queryDate = Util.getMidnight(c.getTimeInMillis());
        c.add(Calendar.DATE, 1);
        queryDate2 = Util.getMidnight(c.getTimeInMillis());
        String taskDueToday = "(tasks.due_date>="+queryDate+" and tasks.due_date<"+
            queryDate2+")";
        
        // Next, we need an expression that checks to see if the task is within the 
        // date range specified.
        if (!isRelative)
        {
            queryDate = absoluteDate;
            c.setTimeInMillis(queryDate);
            c.add(Calendar.DATE, 1);
            queryDate2 = Util.getMidnight(c.getTimeInMillis());
        }
        else
        {
            c.setTimeInMillis(baseTime);
            c.add(Calendar.DATE,(int)daysFromToday);
            queryDate = Util.getMidnight(c.getTimeInMillis());
            c.add(Calendar.DATE, 1);
            queryDate2 = Util.getMidnight(c.getTimeInMillis());
        }
        String inDateRange;
        if (operator.equals(">="))
        {
            if (includeEmptyDates)
            {
                inDateRange = "((tasks.due_date=0 or tasks.due_date>="+queryDate+")";
            }
            else
            {
                inDateRange = "(tasks.due_date>="+queryDate;
            }
        }
        else if (operator.equals("="))
        {
            if (includeEmptyDates)
            {
                inDateRange = "(((tasks.due_date>="+queryDate+" and tasks.due_date<"+
                    queryDate2+") or tasks.due_date=0)";
            }
            else
            {
                inDateRange = "(tasks.due_date>="+queryDate+" and tasks.due_date<"+
                    queryDate2;
            }
        }
        else
        {
            if (includeEmptyDates)
            {
                inDateRange = "(tasks.due_date<"+queryDate2;
            }
            else
            {
                inDateRange = "(tasks.due_date<"+queryDate2+" and tasks.due_date>0";
            }
        }
        String inDateRange2 = new String(inDateRange);
        inDateRange += ")";
        if (!showAllDueOnExact)
        {
            inDateRange2 += " and tasks.due_modifier!='due_on'";
        }
        if (!showAllOptionalOnExact)
        {
            inDateRange2 += " and tasks.due_modifier!='optionally_on'";
        }
        inDateRange2 += ")";
        
        String result = "("+inDateRange2;
        if (!showAllDueOnExact)
        {
            result += " or ("+inDateRange+" and "+taskDueToday+" and tasks.due_modifier='due_on')";
        }
        if (!showAllOptionalOnExact)
        {
            result += " or ("+inDateRange+" and "+taskDueToday+" and tasks.due_modifier='optionally_on')";
        }
        result += ")";
        
        return result;
    }
}
