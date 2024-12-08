package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class PendingReassignmentsDbAdapter
{
	// The name of the table we're using:
	private static final String TABLE = "pending_reassignments";
	
	private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
		"_id integer primary key autoincrement, "+
		"task_id integer not null, "+
		"new_owner_id text not null"+
	")";
	
	public static final String[] INDEXES = {
		"create index if not exists task_id_index on "+TABLE+"(task_id)"
	};
	
	public static final String[] COLUMNS = {
		"_id",
		"task_id",
		"new_owner_id"
	};
	
	private static boolean tablesCreated = false;
	
	// The constructor.  This opens the database for access:
    public PendingReassignmentsDbAdapter() throws SQLException
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
    
    // Separate function to create the DB tables and indexes:
    static public void createTableAndIndexes(SQLiteDatabase db)
    {
    	db.execSQL(TABLE_CREATE);
    	for (int i=0; i<INDEXES.length; i++)
    	{
    		db.execSQL(INDEXES[i]);
    	}
    	tablesCreated = true;
    }
    
    // Add a pending reassignment to the database.  If successful, a row ID is returned, else -1:
    public long addReassignment(long taskID, String newOwnerID)
    {
    	ContentValues values = new ContentValues();
    	values.put("task_id", taskID);
    	values.put("new_owner_id", newOwnerID);
    	Util.checkDatabaseLock(Util.db());
        return Util.db().insert(TABLE, null, values);
    }
    
    // Delete a pending reassignment.  Returns true or false.
    public boolean deleteReassignment(long id)
    {
    	Util.checkDatabaseLock(Util.db());
    	return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Get a cursor containing all pending reassignments:
    public Cursor getReassignments()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, null, null, null, null, null);
        return c;
    }
    
    // Get a specific reassignment:
    public Cursor getReassignment(long rowID)
    {
    	return Util.db().query(TABLE, COLUMNS, "_id="+rowID, null, null, null, null);
    }
}
