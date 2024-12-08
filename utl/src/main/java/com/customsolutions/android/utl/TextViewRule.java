package com.customsolutions.android.utl;

import android.os.Bundle;

public class TextViewRule extends ViewRule 
{
    // This is true if the text must match exact.  Otherwise, the text only need to contain
    // the search string.
    public boolean exactMatch;
    
    // The string to search on:
    public String searchString;
    
    // Construct the class from user inputs:
    public TextViewRule(String databaseField, String search, boolean isExact)
    {
        exactMatch = isExact;
        searchString = search;
        dbField = databaseField;
    }
    
    // Construct a class from a database string:
    public TextViewRule(String databaseField, String dbRecord)
    {
        Bundle b = Util.parseURLString(dbRecord);
        dbField = databaseField;
        searchString = b.getString("search_string");
        exactMatch = Util.stringToBoolean(b.getString("exact_match"));
    }
    
    @Override
    public String getDatabaseString() 
    {
        Bundle b = new Bundle();
        b.putString("search_string", searchString);
        b.putString("exact_match", Util.booleanToString(exactMatch));
        return (Util.createURLString(b));
    }

    @Override
    public String getReadableString() 
    {
        String fieldName;
        if (dbField.equals("title"))
        {
            fieldName = Util.getString(R.string.Title);
        }
        else if (dbField.equals("note"))
        {
            fieldName = Util.getString(R.string.Note);
        }
        else
        {
            // Should not happen.
            fieldName = dbField;
        }
        
        if (exactMatch)
        {
            return fieldName+" "+Util.getString(R.string.is)+" \""+searchString+"\"";
        }
        else
        {
            return fieldName+" "+Util.getString(R.string.contains)+" \""+searchString+"\"";
        }
    }

    @Override
    public String getSQLWhere() 
    {
        if (exactMatch)
        {
            return "(tasks."+dbField+" like '"+Util.makeSafeForDatabase(searchString)+"')";
        }
        else
        {
            return "(tasks."+dbField+" like '%"+Util.makeSafeForDatabase(searchString)+"%')";
        }
    }
}
