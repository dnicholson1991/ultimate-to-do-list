package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.TreeSet;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

// Database interface for the list of current tags

public class CurrentTagsDbAdapter
{
    // The number of tags we will keep track of:
    public static final int NUM_TAGS = 30;
    
    // The table we're working with:
    private static final String TABLE = "current_tags";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "name text not null"+
        ")";
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public CurrentTagsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }
    
    // Update the list of tags:
    public void updateTags(String[] tags)
    {
        // Delete all existing records:
        Util.db().delete(TABLE,null,null);
        
        // Insert new records:
        ContentValues values = new ContentValues();
        for (int i=0; i<tags.length; i++)
        {
        	if (tags[i].length()>0)
        	{
	            values.clear();
	            values.put("name", tags[i]);
	            Util.db().insert(TABLE, null, values);
        	}
        }
    }
    
    // Make sure that certain tags on the recent list.  This removes tags that are lower
    // down if necessary.
    public void addToRecent(String[] tags)
    {
    	// Don't do anything if the array if empty:
    	if (tags.length==0)
    		return;
    	
        // Get 2 lists of tags, one sorted by ID (higher IDs are more recent), and the
        // other sorted by name:
        Cursor c = Util.db().query(TABLE, new String[] {"name"}, null, null, null, null, "_id");
        ArrayList<String> orderedTags = new ArrayList<String>();
        TreeSet<String> tagSet = new TreeSet<String>();
        Boolean moreData = c.moveToFirst();
        while (moreData)
        {
            orderedTags.add(Util.cString(c,"name"));
            tagSet.add(Util.cString(c,"name"));
            moreData = c.moveToNext();
        }
        c.close();
        
        // Go through the new tags and add them in if they are not already there:
        int j;
        for (int i=0; i<tags.length; i++)
        {
            if (!tagSet.contains(tags[i]))
            {
                // The tag is not one of the recent ones, so add it in:
                orderedTags.add(tags[i]);
                tagSet.add(tags[i]);
            }
            else
            {
                // The tag is there, but should be moved to the most recently used.
                j = orderedTags.indexOf(tags[i]);
                orderedTags.remove(j);
                orderedTags.add(tags[i]);
            }
        }
        
        // If we have too many, then remove the least recently used:
        String tagName;
        while (orderedTags.size()>NUM_TAGS)
        {
            tagName = orderedTags.remove(0);
            tagSet.remove(tagName);
        }
        
        // Update the database:
        updateTags((String[])orderedTags.toArray(new String[orderedTags.size()]));
    }
    
    // Retrieve the list of tags, as a Cursor.  This sorts by name:
    public Cursor getTags()
    {
        Cursor c = Util.db().query(TABLE, new String[] {"name","_id"}, null, null, null, null, "lower(name)");
        return c;
    }
    
    // Get a list sorted with the most recently used at the top:
    public Cursor getTagsByUsage()
    {
    	Cursor c = Util.db().query(TABLE, new String[] {"name","_id"}, null, null, null, null, "_id desc");
        return c;
    }
    
    // Remove a tag from the list of recently used tags:
    public void removeFromRecent(String tagName)
    {
    	Util.db().delete(TABLE, "name=?", new String[] { tagName });
    }
    
    // Rename a tag in the list:
    public void renameRecent(String oldName, String newName)
    {
    	ContentValues values = new ContentValues();
    	values.put("name", newName);
    	Util.db().update(TABLE, values, "name=?", new String[] { oldName });
    }
    
    // Get a tag by its database ID.  Returns null if not found.
    public String getTagByID(long id)
    {
        Cursor c = Util.db().query(TABLE, new String[] {"name"}, "_id="+id, null, null, 
        	null, null);
        if (c.moveToFirst())
        	return c.getString(0);
        else
        	return null;
    }
}
