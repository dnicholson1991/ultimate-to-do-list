package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

// This class provide the database interface for tags.  Each task can have zero, one, or
// multiple tags associated with it.

public class TagsDbAdapter 
{    
    // The table we're working with:
    private static final String TABLE = "tags";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "name text not null, "+
        "utl_id long not null"+
        ")";
    
    public static final String[] INDEXES = {
    	"create index if not exists tag_name_index on tags(name)",
    	"create index if not exists tag_id_index on tags(utl_id)"
    };

    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public TagsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	for (int i=0; i<INDEXES.length; i++)
        	{
        		Util.db().execSQL(INDEXES[i]);
        	}
        	tablesCreated = true;
        }
    }
    
    // Attach the specified tags to the specified task.  Any existing tags are removed, 
    // and then replaced with the ones listed here.  Tags can be an empty array, in which
    // case the tags are removed and no new ones are created.
    public void linkTags(long taskID, String[] tags)
    {
        ContentValues values = new ContentValues();
        Util.db().delete(TABLE,"utl_id=?",new String[] { new Long(taskID).toString() });
        for (int i=tags.length-1; i>=0; i--)
        {
        	if (tags[i].length()>0)
        	{
	            values.put("name",tags[i]);
	            values.put("utl_id",taskID);
	            Util.db().insert(TABLE, null, values);
	            values.clear();
        	}
        }
    }
    
    // Get the tags for a particular task.  Returns an empty string array if none.
    public String[] getTags(long taskID)
    {
        String[] result;
        Cursor c = Util.db().query(TABLE, new String[] {"name"}, "utl_id="+taskID, null, null, null, "name");
        result = new String[c.getCount()];
        Boolean moreData = c.moveToFirst();
        int i = 0;
        while (moreData)
        {
            result[i] = c.getString(0);
            i++;
            moreData = c.moveToNext();
        }
        return result;
    }
    
    // Copy the tags from one task to another (used for handling repeated tasks):
    public void copyTags(long sourceTaskID, long destTaskID)
    {
    	String[] sourceTags = getTags(sourceTaskID);
    	linkTags(destTaskID,sourceTags);
    }
    
    // Get the tags in the order they appear in the database (later task first):
    public String[] getTagsInDbOrder(long taskID)
    {
        String[] result;
        Cursor c = Util.db().query(TABLE, new String[] {"name"}, "utl_id="+taskID, null, null, null, "_id desc");
        result = new String[c.getCount()];
        Boolean moreData = c.moveToFirst();
        int i = 0;
        while (moreData)
        {
            result[i] = c.getString(0);
            i++;
            moreData = c.moveToNext();
        }
        return result;
    }
    
    // Delete a tag from all tasks that contain it:
    public void deleteTag(String tagName)
    {
    	// We need to update the modification dates of all affected tasks:
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
		ContentValues values = new ContentValues();
		values.put("mod_date", System.currentTimeMillis());
    	Cursor c = Util.db().rawQuery("select tasks._id, tags.name from tasks "+
    		"left outer join tags on tasks._id=tags.utl_id where tags.name=?", 
    		new String[] { tagName });
    	while (c.moveToNext())
    	{
    		Util.db().update("tasks", values, "_id=?", new String[] { new Long(c.getLong(0)).
    			toString() });
    	}
    	c.close();
    	
    	Util.db().delete(TABLE,"name=?",new String[] { tagName });
    }
    
    // Rename a tag. This updates all tasks that use that tag:
    public void renameTag(String oldName, String newName)
    {
    	// We need to update the modification dates of all affected tasks:
		ContentValues values = new ContentValues();
		values.put("mod_date", System.currentTimeMillis());
    	Cursor c = Util.db().rawQuery("select tasks._id, tags.name from tasks "+
    		"left outer join tags on tasks._id=tags.utl_id where tags.name=?", 
    		new String[] { oldName });
    	while (c.moveToNext())
    	{
    		Util.db().update("tasks", values, "_id=?", new String[] { Long.valueOf(c.getLong(0)).
    			toString() });
    	}
    	c.close();
    	
    	values.clear();
    	values.put("name", newName);
    	Util.db().update(TABLE, values, "name=?", new String[] { oldName });
    }
}
