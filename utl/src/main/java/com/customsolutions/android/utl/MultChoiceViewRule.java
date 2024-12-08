package com.customsolutions.android.utl;

import android.database.Cursor;
import android.util.Log;

public class MultChoiceViewRule extends ViewRule 
{
    // Integers we will be searching for:
    public int[] searchInts;
    
    // Construct the class from user inputs:
    public MultChoiceViewRule(String databaseField, int[] ints)
    {
        searchInts = new int[ints.length];
        System.arraycopy(ints,0,searchInts,0,ints.length);
        dbField = databaseField;
    }
    
    // Construct a class from a database string:
    public MultChoiceViewRule(String databaseField, String dbRecord)
    {
        dbField = databaseField;

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
        if (dbField.equals("account_id"))
        {
            AccountsDbAdapter db = new AccountsDbAdapter();
            if (searchInts.length==1)
            {
                UTLAccount a = db.getAccount(searchInts[0]);
                if (a!=null)
                {
                    return Util.getString(R.string.MultChoiceViewRule1)+" \""+a.name+"\"";
                }
                else
                {
                    return Util.getString(R.string.MultChoiceViewRule1)+" "+
                        Util.getString(R.string.deleted);
                }
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule2)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    UTLAccount a = db.getAccount(searchInts[i]);
                    if (a!=null)
                    {
                        result += "\""+a.name+"\"";
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
        else if (dbField.equals("folder_id"))
        {
            FoldersDbAdapter db = new FoldersDbAdapter();
            if (searchInts.length==1)
            {
                c = db.getFolder(searchInts[0]);
                if (c.moveToFirst())
                {
                	result = Util.getString(R.string.MultChoiceViewRule3)+" \""+Util.cString(c,
                    	"title")+"\"";
                	c.close();
                    return result;
                }
                else if (searchInts[0]==0)
                {
                	c.close();
                	return Util.getString(R.string.MultChoiceViewRule3)+" "+
                    	Util.getString(R.string.None);
                }
                else
                {
                	c.close();
                    return Util.getString(R.string.MultChoiceViewRule3)+" "+
                        Util.getString(R.string.deleted);
                }
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule4)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    c = db.getFolder(searchInts[i]);
                    if (c.moveToFirst())
                    {
                        result += "\""+Util.cString(c,"title")+"\"";
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
                   	c.close();
                }
                return result;
            }
        }
        else if (dbField.equals("context_id"))
        {
            ContextsDbAdapter db = new ContextsDbAdapter();
            if (searchInts.length==1)
            {
                c = db.getContext(searchInts[0]);
                if (c.moveToFirst())
                {
                	result = Util.getString(R.string.MultChoiceViewRule5)+" \""+Util.cString(c,
                    	"title")+"\"";
                	c.close();
                    return result;
                }
                else if (searchInts[0]==0)
                {
                	c.close();
                	return Util.getString(R.string.MultChoiceViewRule5)+" "+
                    	Util.getString(R.string.None);
                }
                else
                {
                   	c.close();
                    return Util.getString(R.string.MultChoiceViewRule5)+" "+
                        Util.getString(R.string.deleted);
                }
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule6)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    c = db.getContext(searchInts[i]);
                    if (c.moveToFirst())
                    {
                        result += "\""+Util.cString(c,"title")+"\"";
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
                   	c.close();
                }
                return result;
            }
        }
        else if (dbField.equals("goal_id"))
        {
            GoalsDbAdapter db = new GoalsDbAdapter();
            if (searchInts.length==1)
            {
                c = db.getGoal(searchInts[0]);
                if (c.moveToFirst())
                {
                	result = Util.getString(R.string.MultChoiceViewRule7)+" \""+Util.cString(c,
                    	"title")+"\"";
                	c.close();
                    return result;
                }
                else if (searchInts[0]==0)
                {
                	c.close();
                	return Util.getString(R.string.MultChoiceViewRule7)+" "+
                    	Util.getString(R.string.None);
                }
                else
                {
                   	c.close();
                    return Util.getString(R.string.MultChoiceViewRule7)+" "+
                        Util.getString(R.string.deleted);
                }
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule8)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    c = db.getGoal(searchInts[i]);
                    if (c.moveToFirst())
                    {
                        result += "\""+Util.cString(c,"title")+"\"";
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
                   	c.close();
                }
                return result;
            }
        }
        else if (dbField.equals("location_id"))
        {
            LocationsDbAdapter db = new LocationsDbAdapter();
            if (searchInts.length==1)
            {
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
        else if (dbField.equals("status"))
        {
        	String[] statuses;
        	if (Util.context!=null)
        	{
        		statuses = Util.context.getResources().getStringArray(
        			R.array.statuses);
        	}
        	else
        		statuses = Util.statuses;
        	
            if (searchInts.length==1)
            {
                return Util.getString(R.string.MultChoiceViewRule9)+" "+
                    statuses[searchInts[0]];
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule10)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    result += statuses[searchInts[i]];
                    if (i<(searchInts.length-1))
                    {
                        result += ", ";
                    }
                }
                return result;
            }
        }
        else if (dbField.equals("priority"))
        {
        	String[] priorities;
        	if (Util.context!=null)
        	{
        		priorities = Util.context.getResources().getStringArray(
        			R.array.priorities);
        	}
        	else
        		priorities = Util.priorities;
        	
            if (searchInts.length==1)
            {
                return Util.getString(R.string.MultChoiceViewRule11)+" "+
                    priorities[searchInts[0]];
            }
            else
            {
                result = Util.getString(R.string.MultChoiceViewRule12)+" ";
                for (i=0; i<searchInts.length; i++)
                {
                    result += priorities[searchInts[i]];
                    if (i<(searchInts.length-1))
                    {
                        result += ", ";
                    }
                }
                return result;
            }
        }
        else if (dbField.equals("parent_id"))
        {
            result = Util.getString(R.string.MultChoiceViewRule13)+" ";
            TasksDbAdapter tasksDB = new TasksDbAdapter();
            for (i=0; i<searchInts.length; i++)
            {
            	UTLTask t = tasksDB.getTask(searchInts[i]);
            	if (t==null)
            	{
            		result += Util.getString(R.string.deleted);
            	}
            	else
            	{
            		result += "\""+t.title+"\"";
            	}
                if (i<(searchInts.length-1))
                {
                    result += ", ";
                }
            }
            return result;
        }
        else
        {
            // This should not happen.
            result = dbField+" = ";
            for (i=0; i<searchInts.length; i++)
            {
                result += searchInts[i];
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
    		if (searchInts[0]==0 && dbField.equals("location_id"))
    			return "(tasks.location_id=0 or tasks.location_id is NULL)";
    		else
    			return "(tasks."+dbField+"="+searchInts[0]+")";
        }
        else
        {
            String result = "(tasks."+dbField+" in (";
            for (int i=0; i<searchInts.length; i++)
            {
                result += searchInts[i];
                if (i<(searchInts.length-1))
                {
                    result += ",";
                }
            }
            result += "))";
            return result;
        }
    }
}
