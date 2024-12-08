package com.customsolutions.android.utl;

import android.database.Cursor;
import android.util.Log;

public class LocationViewRule extends ViewRule 
{
    // Location IDs we will be searching for.  A value of -1 indicates the current location.
    public int[] searchInts;
    
    // Construct the class from user inputs:
    public LocationViewRule(String databaseField, int[] ints)
    {
        searchInts = new int[ints.length];
        System.arraycopy(ints,0,searchInts,0,ints.length);
        dbField = databaseField;
    }
    
    // Construct a class from a database string.  The databaseField input is ignored.
    public LocationViewRule(String databaseField, String dbRecord)
    {
        dbField = "location_id";

        if (dbRecord.equals(""))
    	{
    		searchInts = new int[0];
    		return;
    	}
        
        // The integers are stored as comma-separated values.
        String[] items = dbRecord.split(",");
        searchInts = new int[items.length];
        int i;
        for (i=0; i<items.length; i++)
        {
            searchInts[i] = Integer.parseInt(items[i]);
        }
    }
    
    @Override
    public String getDatabaseString() 
    {
    	if (searchInts.length==0)
    	{
    		// Empty list.  Return nothing:
    		return "";
    	}
    	
        // The integers are stored as comma-separated values.
        String result = String.valueOf(searchInts[0]);
        int i;
        for (i=1; i<searchInts.length; i++)
        {
            result += ","+String.valueOf(searchInts[i]);
        }
        return result;
    }

    @Override
    public String getReadableString() 
    {
        int i;
        String result;
        Cursor c;
        if (searchInts.length==0)
        {
        	return Util.getString(R.string.Empty_Rule);
        }
        LocationsDbAdapter db = new LocationsDbAdapter();
        if (searchInts.length==1)
        {
        	if (searchInts[0]==-1)
        	{
        		result = Util.getString(R.string.MultChoiceViewRule14)+" "+
        			Util.getString(R.string.current_location);
                return result;
        	}
        	UTLLocation loc = db.getLocation(searchInts[0]);
            if (loc != null)
            {
            	result = Util.getString(R.string.MultChoiceViewRule14)+" \""+loc.title+"\"";
                return result;
            }
            else if (searchInts[0]==0)
            {
            	return Util.getString(R.string.MultChoiceViewRule14)+" "+
                	Util.getString(R.string.None);
            }
            else
            {
                return Util.getString(R.string.MultChoiceViewRule14)+" "+
                    Util.getString(R.string.deleted);
            }
        }
        else
        {
            result = Util.getString(R.string.MultChoiceViewRule15)+" ";
            for (i=0; i<searchInts.length; i++)
            {
            	UTLLocation loc = db.getLocation(searchInts[i]);
                if (loc != null)
                {
                    result += "\""+loc.title+"\"";
                }
                else if (searchInts[i]==-1)
                {
                	result += Util.getString(R.string.Current_Location);
                }
                else if (searchInts[i]==0)
                {
                	result += Util.getString(R.string.None);
                }
                else
                {
                    result += Util.getString(R.string.deleted);
                }
                if (i<(searchInts.length-1))
                {
                    result += ", ";
                }
            }
            return result;
        }
    }

    @Override
    public String getSQLWhere() 
    {
    	if (searchInts.length==0)
    	{
    		// Return a where clause that will always be true:
    		return "(tasks.account_id!=-1)";
    	}
    	else if (searchInts.length==1)
        {
    		if (searchInts[0]==0)
    			return "(tasks.location_id=0 or tasks.location_id is NULL)";
    		else if (searchInts[0]==-1)
    		{
    			// Current location:
    			LocationsDbAdapter locDB = new LocationsDbAdapter();
    			Cursor c = locDB.queryLocations("at_location="+UTLLocation.YES, 
    				null);
    			if (c.getCount()==0)
    			{
    				// We are not at any locations.  Return a where clause that will always be false:
    				return "(tasks.location_id=-1)";
    			}
    			else
    			{
	    			String result = "(tasks."+dbField+" in (";
	    			while (c.moveToNext())
	    			{
	    				UTLLocation loc = locDB.cursorToUTLLocation(c);
	    				if (c.getPosition()>0)
	    				{
	    					result += ",";
	    				}
	    				result += loc._id;
	    			}
	    			c.close();
					result += "))";
		            return result;
    			}
    		}
    		else
    			return "(tasks."+dbField+"="+searchInts[0]+")";
        }
        else
        {
            String result = "(tasks."+dbField+" in (";
            boolean isFirst = true;
            for (int i=0; i<searchInts.length; i++)
            {
            	if (!isFirst)
            		result += ",";
            	else
            		isFirst = false;
            	if (searchInts[i]!=-1)
            		result += searchInts[i];
            	else
            	{
            		// Filtering on current location:
            		LocationsDbAdapter locDB = new LocationsDbAdapter();
        			Cursor c = locDB.queryLocations("at_location="+UTLLocation.YES, 
        				null);
        			if (c.getCount()==0)
        			{
        				// We are not at any locations.  Add an invalid location to ensure the
        				// where string is formatted correctly.
        				result += "-1";
        			}
        			else
        			{
        				while (c.moveToNext())
    	    			{
    	    				UTLLocation loc = locDB.cursorToUTLLocation(c);
    	    				if (c.getPosition()>0)
    	    				{
    	    					result += ",";
    	    				}
    	    				result += loc._id;
    	    			}
        				c.close();
        			}
            	}
            }
            result += "))";
            return result;
        }
    }
}
