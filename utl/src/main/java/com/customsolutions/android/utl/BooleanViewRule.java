package com.customsolutions.android.utl;

public class BooleanViewRule extends ViewRule 
{
    public Boolean myValue;
    
    // Construct the class from user inputs:
    public BooleanViewRule(String databaseField, Boolean value)
    {
        dbField = databaseField;
        myValue = value;
    }
    
    // Construct from an encoded string in the database:
    public BooleanViewRule(String databaseField, String dbRecord)
    {
        dbField = databaseField;
        if (dbRecord.equals("1"))
        {
            myValue = true;
        }
        else
        {
            myValue = false;
        }
    }
    
    @Override
    public String getDatabaseString() 
    {
        if (myValue)
        {
            return "1";
        }
        else
        {
            return ("0");
        }
    }

    @Override
    public String getReadableString() 
    {
        if (dbField.equals("completed"))
        {
            if (myValue)
            {
                return Util.getString(R.string.Task_is_completed);
            }
            else
            {
            	return Util.getString(R.string.Task_is_not_completed);
            }
        }
        else if (dbField.equals("star"))
        {
            if (myValue)
            {
            	return Util.getString(R.string.Task_is_starred);                
            }
            else
            {
            	return Util.getString(R.string.Task_is_not_starred);
            }
        }
        else if (dbField.equals("is_joint"))
        {
            if (myValue)
            {
            	return Util.getString(R.string.Task_is_shared);                
            }
            else
            {
            	return Util.getString(R.string.Task_is_not_shared);
            }
        }
        else
        {
            // This should not happen.
            return (dbField+" is "+myValue);
        }
    }

    @Override
    public String getSQLWhere() 
    {
        if (myValue)
        {
            return "(tasks."+dbField+"=1)";
        }
        else
        {
            return "(tasks."+dbField+"=0)";
        }
    }

}
