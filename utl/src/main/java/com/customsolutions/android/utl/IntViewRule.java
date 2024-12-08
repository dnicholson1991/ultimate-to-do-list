package com.customsolutions.android.utl;

// This is a subclass of ViewRule that handles integer comparisons.

public class IntViewRule extends ViewRule 
{
    // The value we're comparing to:
    public long value;
    
    // The comparison operator. One of: <, >, <=, >=, =, !=
    public String operator;
    
    // Construct the class from user inputs:
    public IntViewRule(String databaseField, long value1, String operator1)
    {
        dbField = databaseField;
        value = value1;
        operator = operator1;
    }
    
    // Construct the class from a database string:
    public IntViewRule(String databaseField, String dbRecord)
    {
        dbField = databaseField;
        
        // The value and operator are separated by a comma:
        String[] values = dbRecord.split(",");
        value = Long.parseLong(values[0]);
        operator = values[1];
    }
    
    @Override
    public String getDatabaseString() 
    {
        // The value and operator are separated by a comma:
        return String.valueOf(value)+","+operator;
    }

    @Override
    public String getReadableString() 
    {
        String result;
        if (dbField.equals("length"))
        {
            result = Util.getString(R.string.IntViewRule1)+" "+getOperatorString();
            result += " "+value+" "+Util.getString(R.string.minutes);
            return result;
        }
        else if (dbField.equals("importance"))
        {
        	result = Util.getString(R.string.Importance)+" "+getOperatorString();
            result += " "+value+" ";
            return result;
        }
        else if (dbField.equals("timer"))
        {
            result = Util.getString(R.string.IntViewRule2)+" "+getOperatorString();
            long minutes = value / 60;
            long seconds = value % 60;
            if (seconds>=30)
            {
                minutes++;
            }
            result += " "+minutes+" "+Util.getString(R.string.minutes);
            return result;
        }
        else if (dbField.equals("timer_start_time"))
        {
            if (operator.equals(">") && value==0)
            {
                return(Util.getString(R.string.IntViewRule3));
            }
            else
            {
                return(Util.getString(R.string.IntViewRule4));
            }
        }
        else
        {
            // This should not happen, but we'll handle it anyway.
            result = dbField+" "+getOperatorString();
            result += " "+value;
            return result;
        }
    }

    @Override
    public String getSQLWhere()
    {
        return ("("+dbField+operator+value+")");
    }

    private String getOperatorString()
    {
        String result;
        if (operator.equals("<"))
        {
            result = Util.getString(R.string.isLessThan);
        }
        else if (operator.equals("<="))
        {
            result = Util.getString(R.string.isLessThanOrEqualTo);
        }
        else if (operator.equals("="))
        {
            result = Util.getString(R.string.isEqualTo);
        }
        else if (operator.equals("!="))
        {
            result = Util.getString(R.string.isNotEqualTo);
        }
        else if (operator.equals(">="))
        {
            result = Util.getString(R.string.isGreaterThanOrEqualTo);
        }
        else
        {
            result = Util.getString(R.string.isGreaterThan);
        }
        return result;
    }
}
