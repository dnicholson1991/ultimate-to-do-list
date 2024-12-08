package com.customsolutions.android.utl;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;

// An subclass of ViewRule that handles a search for one or more string fields.

public class MultStringsViewRule extends ViewRule 
{
    // The strings we will be searching for.
    public String[] searchStrings;
    
    // IMPORTANT: Since this subclass works on the tags table instead of the tasks table,
    // specify "tags.name" as the databaseField input below.
    
    // Construct the class from user inputs:
    public MultStringsViewRule(String databaseField, String[] strings)
    {
        searchStrings = new String[strings.length];
        System.arraycopy(strings,0,searchStrings,0,strings.length);
        dbField = databaseField;
    }
    
    // Construct the class from a database string:
    public MultStringsViewRule(String databaseField, String dbRecord)
    {
        dbField = databaseField;
        
        if (dbRecord.equals(""))
        {
        	searchStrings = new String[0];
        	return;
        }
        
        if (dbField.equals("contact_lookup_key"))
        {
        	// Only one item is supported:
        	searchStrings = new String[] { dbRecord };
        }
        else
        {
        	// Names are separated by commas.
        	searchStrings = dbRecord.split(",");
        }
    }
    
    @Override
    public String getDatabaseString() 
    {
    	if (searchStrings.length==0)
    	{
    		// Empty list.  Return nothing:
    		return "";
    	}
    	
        // Strings are separated by commas:
        String result = "";
        for (int i=0; i<searchStrings.length; i++)
        {
            result += searchStrings[i];
            if (i<(searchStrings.length-1))
            {
                result += ",";
            }
        }
        return result;
    }

    @Override
    public String getReadableString() 
    {
        int i;
        String result;
        if (searchStrings.length==0)
        {
        	return Util.getString(R.string.Empty_Rule);
        }
        
        if (dbField.equals("tags.name"))
        {
            if (searchStrings.length==1)
            {
                return Util.getString(R.string.MultStringsViewRule1)+" \""+searchStrings[0]+
                    "\"";
            }
            else
            {
                result = Util.getString(R.string.MultStringsViewRule2)+" ";
                for (i=0; i<searchStrings.length; i++)
                {
                    result += "\""+searchStrings[i]+"\"";
                    if (i<(searchStrings.length-1))
                    {
                        result += ",";
                    }
                }
                return result;
            }
        }
        else if (dbField.equals("contact_lookup_key"))
        {
        	Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
        		CONTENT_LOOKUP_URI,searchStrings[0]);
        	Cursor c = Util.context.getContentResolver().query(contactUri, new String[] 
        	    {Contacts.DISPLAY_NAME}, null, null, null);
        	if (c!=null && c.moveToFirst())
        	{
        		result = Util.getString(R.string.Contact_is)+" "+c.getString(0);
        		c.close();
        		return result;
        	}
        	else
        	{
        		if (c!=null)
        			c.close();
        		return (Util.getString(R.string.Contact_is)+" "+Util.getString(R.string.
        			Missing_Contact));
        	}
        }
        else if (dbField.equals("owner_remote_id"))
        {
        	CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        	String[] collNames = new String[searchStrings.length];
        	for (i=0; i<searchStrings.length; i++)
        	{
        		UTLCollaborator co = cdb.getCollaborator(searchStrings[i]);
        		if (co!=null)
        			collNames[i] = co.name;
        		else
        			collNames[i] = Util.getString(R.string.Missing_Contact);
        	}
            if (collNames.length==1)
            {
                return Util.getString(R.string.Owner_includes)+" \""+collNames[0]+
                    "\"";
            }
            else
            {
                result = Util.getString(R.string.Owner_includes_one_of)+" ";
                for (i=0; i<collNames.length; i++)
                {
                    result += "\""+collNames[i]+"\"";
                    if (i<(collNames.length-1))
                    {
                        result += ",";
                    }
                }
                return result;
            }        	
        }
        else if (dbField.equals("added_by"))
        {
        	CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        	String[] collNames = new String[searchStrings.length];
        	for (i=0; i<searchStrings.length; i++)
        	{
        		UTLCollaborator co = cdb.getCollaborator(searchStrings[i]);
        		if (co!=null)
        			collNames[i] = co.name;
        		else
        			collNames[i] = Util.getString(R.string.Missing_Contact);
        	}
            if (collNames.length==1)
            {
                return Util.getString(R.string.Assignor_includes)+" \""+collNames[0]+
                    "\"";
            }
            else
            {
                result = Util.getString(R.string.Assignor_includes_one_of)+" ";
                for (i=0; i<collNames.length; i++)
                {
                    result += "\""+collNames[i]+"\"";
                    if (i<(collNames.length-1))
                    {
                        result += ",";
                    }
                }
                return result;
            }        	
        }
        else
        {
            // This should not happen.
            result = dbField+" = ";
            for (i=0; i<searchStrings.length; i++)
            {
                result += searchStrings[i];
                if (i<(searchStrings.length-1))
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
    	if (searchStrings.length==0)
    	{
    		// Return a where clause that will always be true:
    		return ("tasks.account_id!=-1");
    	}
    	
    	if (dbField.equals("tags.name"))
        {
            if (searchStrings.length==1)
            {
            	return getHavingClause(searchStrings[0]);
            }
            else
            {
                String result = "(";
                for (int i=0; i<searchStrings.length; i++)
                {
                    result += getHavingClause(searchStrings[i]);
                    if (i<(searchStrings.length-1))
                    {
                        result += " or ";
                    }
                }
                result += ")";
                return result;
            }
        }
    	else if (dbField.equals("contact_lookup_key"))
    	{
    		String result = "(tasks.contact_lookup_key='"+Util.makeSafeForDatabase(
    			searchStrings[0])+"')";
    		return result;
    	}
        else
        {
            // Collaboration fields:
            String result = "(tasks."+dbField+" in (";
            for (int i=0; i<searchStrings.length; i++)
            {
                result += "'"+Util.makeSafeForDatabase(searchStrings[i])+"'";
                if (i<(searchStrings.length-1))
                {
                    result += ",";
                }
            }
            result += "))";
            return result;
        }
    }

    // The tag constraints appear in a Having clause instead of a where clause:
    @Override
    public boolean isHavingClause()
    {
    	if (dbField.equals("tags.name"))
    		return true;
    	else
    		return false;
    }
    
    // Get the specific having clause for one tag.  The clause will be surrounded by 
    // parentheses:
    private String getHavingClause(String tagName)
    {
    	String str = Util.makeSafeForDatabase(tagName);
    	return "(tag_list='"+str+"' or tag_list like '"+str+",%' or "+
    		"tag_list like '%,"+str+",%' or tag_list like '%,"+str+"')";
    }
}
