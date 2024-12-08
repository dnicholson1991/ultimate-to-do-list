package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

import android.content.Context;

// This class contains functions for handling advanced repeating options:

public class AdvancedRepeat 
{
    // Which format is used (1, 2, or 3, as defined by Toodledo):
    public int formatNum;
    
    // Format 1 variables:
    public int increment;
    public String unit;  // day, month, week, or year
    
    // Format 2 variables:
    public String monthLocation;  // An integer from 1 to 5, or the word "last"
    public String dayOfWeek;      // "mon", "tue", etc.  (first 3 letters of day)
    
    // Format 3 variables:
    public String[] daysOfWeek; // An array of days ("mon", "tue", etc) or "weekend" or "weekday"
    
    // Parse an advanced repeat string and initialize an instance.  Returns true on success 
    // or false on failure.
    public boolean initFromString(String input)
    {
        String in;
        
        // Convert to lowercase and trim leading/trailing spaces:
        in = input.toLowerCase();
        in = in.trim();
        
        // Convert one-word strings into something more standard:
        if (in.equals("weekly") || in.equals("week"))
        {
            in = "every 1 week";
        }
        if (in.equals("monthly") || in.equals("month"))
        {
            in = "every 1 month";
        }
        if (in.equals("daily") || in.equals("day"))
        {
            in = "every 1 day";
        }
        if (in.equals("yearly") || in.equals("year"))
        {
            in = "every 1 year";
        }

        // Split into an array of words:
        String[] words = in.split(" +");
        int end = words.length-1;
        
        // There must be at least 2 words:
        if (words.length<2)
        {
            return false;
        }
        
        if (words[0].equals("every") && (words[end].equals("day") || words[end].equals("days") ||
            words[end].equals("month") || words[end].equals("months") || 
            words[end].equals("week") || words[end].equals("weeks") ||
            words[end].equals("year") || words[end].equals("years")))
        {
            // Format 1 (every 2 months, etc):
            formatNum = 1;
            if (words.length==2)
            {
                // User typed in something like "every month":
                increment = 1;
            }
            else
            {
                try
                {
                    increment = Integer.parseInt(words[1]);
                    if (increment<1)
                    {
                        return false;
                    }
                }
                catch (Exception e)
                {
                    // Couldn't get an integer.
                    return false;
                }
            }
            String substr;
            try
            {
                substr = words[end].substring(0,3);
            }
            catch (Exception e)
            {
                return false;
            }
            if (substr.equals("day"))
            {
                unit = "day";
            }
            else if (substr.equals("mon"))
            {
                unit = "month";
            }
            else if (substr.equals("wee"))
            {
                unit = "week";
            }
            else
            {
                unit = "year";
            }
        }
        else if (words.length>2 && (words[end-2].equals("of") || words[end-1].equals("of")) && 
            words[end].equals("month"))
        {
            // Format 2 (on the second tuesday of each month, etc):
            formatNum = 2;
            
            // Pull out the location and day of week within the month:
            if (words[0].equals("on") && words[1].equals("the"))
            {
                monthLocation = words[2];
                dayOfWeek = words[3];
            }
            else if (words[0].equals("the"))
            {
                monthLocation = words[1];
                dayOfWeek = words[2];
            }
            else
            {
                monthLocation = words[0];
                dayOfWeek = words[1];
            }
            
            // Convert numerical words ("first", etc) to numbers:
            if (monthLocation.equals("first"))
            {
                monthLocation = "1";
            }
            else if (monthLocation.equals("second"))
            {
                monthLocation = "2";
            }
            else if (monthLocation.equals("third"))
            {
                monthLocation = "3";
            }
            else if (monthLocation.equals("fourth"))
            {
                monthLocation = "4";
            }
            else if (monthLocation.equals("fifth"))
            {
                monthLocation = "5";
            }
            
            // Strip out text like "th" or "rd", unless the user chose the "last" day:
            if (!monthLocation.equals("last"))
            {
                monthLocation = monthLocation.substring(0,1);
                try
                {
                    int loc = Integer.parseInt(monthLocation);
                    if (loc<1 || loc>5)
                    {
                        return false;
                    }
                }
                catch (Exception e)
                {
                    // Not a number:
                    return false;
                }
            }
           
            // Get the day of the week:
            dayOfWeek = parseDayOfWeek(dayOfWeek);
            if (dayOfWeek.equals(""))
            {
                return false;
            }
        }
        else if (words[0].equals("every"))
        {
            // Format 3 (every tue,thu, etc)
            formatNum = 3;
            if (words[1].equals("weekend") || words[1].equals("weekday"))
            {
                daysOfWeek = new String[] {words[1]};
            }
            else
            {
                // Extract the list of dates
                String dayListStr = in.substring(6,in.length());
                dayListStr = dayListStr.replaceAll("and", "");
                daysOfWeek = dayListStr.split("[, ]+");
                for (int i=0; i<daysOfWeek.length; i++)
                {
                    daysOfWeek[i] = parseDayOfWeek(daysOfWeek[i]);
                    if (daysOfWeek[i].equals(""))
                    {
                        // Bad day of week specified:
                        return false;
                    }
                }
            }
        }
        else
        {
            // Bad string
            return false;
        }
        
        return true;
    }
    
    // Get a localized string, only for display.  The standard string (below) can be stored in a 
    // database and used to instantiate this class.
    public String getLocalizedString(Context c)
    {
    	// For English speakers, return the standard string, since this is a better format for English:
    	if (Locale.getDefault().getLanguage().equals("en"))
    	{
    		return getStandardString();
    	}
    	
    	String result = "";
    	if (formatNum==1)
    	{
    		result = c.getString(R.string.Every)+" ";
    		result += Util.getNumberString(increment)+" ";
    		result += translate(c,unit,R.array.repeat_intervals_db,R.array.repeat_intervals_localized);
    	}
    	else if (formatNum==2)
    	{
    		result = c.getString(R.string.The)+" ";
    		result += translate(c,monthLocation,R.array.month_locations_db,
    			R.array.month_locations_localized)+" ";
    		result += translate(c,dayOfWeek,R.array.days_of_week_db,R.array.days_of_week_localized)+" ";
    		result += c.getString(R.string.of_each_month);
    	}
    	else if (formatNum==3)
    	{
    		if (daysOfWeek[0].equals("weekday"))
    			result = c.getString(R.string.Every)+" "+c.getString(R.string.Weekday);
    		else if (daysOfWeek[0].equals("weekend"))
    			result = c.getString(R.string.Every)+" "+c.getString(R.string.Weekend);
    		else
    		{
    			result = c.getString(R.string.Every)+" "+translate(c,daysOfWeek[0],R.array.days_of_week_db,
    				R.array.days_of_week_localized).substring(0,3);
                for (int i=1; i<daysOfWeek.length; i++)
                {
                    result += ", "+translate(c,daysOfWeek[i],R.array.days_of_week_db,
        				R.array.days_of_week_localized).substring(0,3);
                }
    		}
    	}
    	return result;
    }
    
    // Get an advanced repeat string formatted in a standard way.  Also suitable for English translation.
    public String getStandardString()
    {
        String result = "";
        if (formatNum==1)
        {
            result = "Every "+increment+" "+unit;
            if (increment>1)
            {
                result += "s";
            }
            return result;
        }
        else if (formatNum==2)
        {
            result = "The "+monthLocation;
            if (monthLocation.equals("1"))
            {
                result += "st";
            }
            if (monthLocation.equals("2"))
            {
                result += "nd";
            }
            if (monthLocation.equals("3"))
            {
                result += "rd";
            }
            if (monthLocation.equals("4") || monthLocation.equals("5"))
            {
                result += "th";
            }
            result += " "+dayOfWeek.substring(0,1).toUpperCase()+dayOfWeek.substring(1,3);
            result += " of each month";
        }
        else if (formatNum==3)
        {
            if (daysOfWeek[0].equals("weekday") || daysOfWeek[0].equals("weekend"))
            {
                result = "Every "+daysOfWeek[0];
            }
            else
            {
                result = "Every "+daysOfWeek[0].substring(0,1).toUpperCase()+
                    daysOfWeek[0].substring(1,3);
                for (int i=1; i<daysOfWeek.length; i++)
                {
                    result += ", "+daysOfWeek[i].substring(0,1).toUpperCase()+
                        daysOfWeek[i].substring(1,3);
                }
            }
        }
        else
        {
            // This had better not happen!
            result = "";
        }
        return result;
    }
    
    // Convert a standard string (an array item) to a localized one.
    public String translate(Context c, String s, int dbArrayID, int localizedArrayID)
    {
    	String[] dbArray = c.getResources().getStringArray(dbArrayID);
    	String[] localizedArray = c.getResources().getStringArray(localizedArrayID);
    	for (int i=0; i<dbArray.length; i++)
    	{
    		if (dbArray[i].equals(s))
    			return localizedArray[i];
    	}
    	return "";
    }
    
    // Given a date in ms, get the next date in the repeat pattern, in ms.
    public long getNextDate(long startDate)
    {
        // Get a Calendar instance tied to the start date:
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        c.setTimeInMillis(startDate);
        
        if (formatNum==1)
        {
            if (unit.equals("day"))
            {
                c.add(Calendar.DATE, increment);
            }
            else if (unit.equals("month"))
            {
                c.add(Calendar.MONTH, increment);
            }
            else if (unit.equals("week"))
            {
                c.add(Calendar.WEEK_OF_YEAR, increment);
            }
            else
            {
                c.add(Calendar.YEAR, increment);
            }
        }
        else if (formatNum==2)
        {
            // Move the date forward until we get the day of the week we're interested in:
            int targetDOW = getCalendarDayOfWeek(dayOfWeek);
            c.add(Calendar.DATE, 1);
            while (c.get(Calendar.DAY_OF_WEEK)!=targetDOW)
            {
                c.add(Calendar.DATE, 1);
            }
            
            // Add one week at a time until we get to the desired date:
            if (!monthLocation.equals("last"))
            {
                int weekNum = Integer.parseInt(monthLocation);
                while (c.get(Calendar.DAY_OF_WEEK_IN_MONTH) != weekNum)
                {
                    c.add(Calendar.WEEK_OF_YEAR, 1);
                }
            }
            else
            {
                while (c.get(Calendar.DAY_OF_WEEK_IN_MONTH) !=
                    c.getActualMaximum(Calendar.DAY_OF_WEEK_IN_MONTH))
                {
                    c.add(Calendar.WEEK_OF_YEAR, 1);
                }
            }
        }
        else if (formatNum==3)
        {
            // Create a HashSet with the days of the week we're interested in:
            HashSet<Integer> hash = new HashSet<Integer>();
            if (!daysOfWeek[0].equals("weekend") && !daysOfWeek[0].equals("weekday"))
            {
                for (int i=0; i<daysOfWeek.length; i++)
                {
                    hash.add(getCalendarDayOfWeek(daysOfWeek[i]));
                }
            }
            else if (daysOfWeek[0].equals("weekend"))
            {
                hash.add(getCalendarDayOfWeek("sun"));
                hash.add(getCalendarDayOfWeek("sat"));
            }
            else
            {
                hash.add(getCalendarDayOfWeek("mon"));
                hash.add(getCalendarDayOfWeek("tue"));
                hash.add(getCalendarDayOfWeek("wed"));
                hash.add(getCalendarDayOfWeek("thu"));
                hash.add(getCalendarDayOfWeek("fri"));   
            }
            
            // Increment the date until we get to a day of the week we want:
            c.add(Calendar.DATE, 1);
            while (!hash.contains(c.get(Calendar.DAY_OF_WEEK)))
            {
                c.add(Calendar.DATE, 1);
            }
        }
        
        return c.getTimeInMillis();
    }

    // Parse a day of the week.  Returns the first 3 letters, or "" if it's invalid.
    private String parseDayOfWeek(String dow)
    {
        // We will allow 2 letters days of the week:
        if (dow.toLowerCase().equals("su"))
        {
            dow = "sun";
        }
        if (dow.toLowerCase().equals("mo"))
        {
            dow = "mon";
        }
        if (dow.toLowerCase().equals("tu"))
        {
            dow = "tue";
        }
        if (dow.toLowerCase().equals("we"))
        {
            dow = "wed";
        }
        if (dow.toLowerCase().equals("th"))
        {
            dow = "thu";
        }
        if (dow.toLowerCase().equals("fr"))
        {
            dow = "fri";
        }
        if (dow.toLowerCase().equals("sa"))
        {
            dow = "sat";
        }
        if (dow.length()<3)
        {
            return "";
        }
        String result = dow.substring(0,3).toLowerCase();
        if (result.equals("sun") || result.equals("mon") || result.equals("tue") || 
            result.equals("wed") || result.equals("thu") || result.equals("fri") || 
            result.equals("sat"))
        {
            return result;
        }
        else
        {
            return "";
        }
    }
    
    // Given a day of the week (as returned above), get the code within the Calendar class:
    private int getCalendarDayOfWeek(String dow)
    {
        if (dow.equals("sun"))
        {
            return Calendar.SUNDAY;
        }
        else if (dow.equals("mon"))
        {
            return Calendar.MONDAY;
        }
        else if (dow.equals("tue"))
        {
            return Calendar.TUESDAY;
        }
        else if (dow.equals("wed"))
        {
            return Calendar.WEDNESDAY;
        }
        else if (dow.equals("thu"))
        {
            return Calendar.THURSDAY;
        }
        else if (dow.equals("fri"))
        {
            return Calendar.FRIDAY;
        }
        else
        {
            return Calendar.SATURDAY;
        }
    }
}
