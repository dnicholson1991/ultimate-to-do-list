package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

// This is a subclass of ViewRule that handles date comparisons for UTL Views.

public class DateViewRule extends ViewRule 
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
    
    // Are we including tasks with no date specified?
    public boolean includeEmptyDates;
    
    // Construct the class from user inputs.  The isRelative1 input below determines whether
    // the value is an offset from today, or an absolute timestamp in ms.
    public DateViewRule(String databaseField, boolean isRelative1, long value, 
        String operator1, boolean includeEmpty)
    {
        dbField = databaseField;
        isRelative = isRelative1;
        operator = operator1;
        if (isRelative)
        {
            daysFromToday = value;
            absoluteDate = 0;
        }
        else
        {
            absoluteDate = Util.getMidnight(value);
            daysFromToday = 0;
        }
        includeEmptyDates = includeEmpty;
    }
    
    // Construct the class from a database string:
    public DateViewRule(String databaseField, String dbRecord)
    {
        dbField = databaseField;
        
        // The parameters are separated by commas:
        String[] values = dbRecord.split(",");
        isRelative = Util.stringToBoolean(values[0]);
        operator = values[1];
        if (isRelative)
        {
            daysFromToday = Long.parseLong(values[2]);
            absoluteDate = 0;
        }
        else
        {
            absoluteDate = Long.parseLong(values[2]);
            daysFromToday = 0;
        }
        includeEmptyDates = Util.stringToBoolean(values[3]);
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
        result += ","+Util.booleanToString(includeEmptyDates);
        return result;
    }

    @Override
    public String getReadableString() 
    {
        String result;
        if (dbField.equals("start_date"))
        {
            result = Util.getString(R.string.StartDate);
        }
        else if (dbField.equals("completion_date"))
        {
            result = Util.getString(R.string.CompletionDate);
        }
        else if (dbField.equals("mod_date"))
        {
        	result = Util.getString(R.string.Modification_Date);
        }
        else if (dbField.equals("reminder"))
        {
        	result = Util.getString(R.string.Reminder);
        }
        else
        {
            result = dbField;
        }
        
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
        long queryDate2;  // This will be midnight on the following day.
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
                Util.settings.getString("home_time_zone","")));  // Set to current time.
        
        // Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long zoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	long baseTime = System.currentTimeMillis()+zoneOffset;
    	
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
        if (operator.equals(">="))
        {
            if (includeEmptyDates)
            {
                return ("(tasks."+dbField+">="+queryDate+" or tasks."+dbField+"=0)");
            }
            else
            {
                return ("(tasks."+dbField+">="+queryDate+")");
            }
        }
        else if (operator.equals("="))
        {
            if (includeEmptyDates)
            {
                return("((tasks."+dbField+">="+queryDate+" and tasks."+dbField+"<"+
                    queryDate2+") or tasks."+dbField+"=0)");
            }
            else
            {
                return("(tasks."+dbField+">="+queryDate+" and tasks."+dbField+"<"+queryDate2+
                    ")");
            }
        }
        else
        {
            if (includeEmptyDates)
            {
                return ("(tasks."+dbField+"<"+queryDate2+")");
            }
            else
            {
                return ("(tasks."+dbField+"<"+queryDate2+" and tasks."+dbField+">0)");
            }
        }
    }
}
