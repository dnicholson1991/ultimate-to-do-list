package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

// Database interface for pending deletes.  This table holds information on items that
// have been deleted locally and the deletion information needs sent to Toodledo.

public class PendingDeletesDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "pending_deletes";
 
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "type text not null, "+   // One of: task, note, folder, context, goal, location
        "td_id long not null, "+  // Set to -1 for non-toodledo account
        "account_id long not null, "+
        "remote_id text, "+        // For google tasks only. Set to "" for tasklists.
        "remote_tasklist_id text"+ // Used for google tasklists and tasks.
        ")";
    // type is one of: task, note, folder, context, goal
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","type","td_id","account_id","remote_id",
    	"remote_tasklist_id" };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public PendingDeletesDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }
    
    // Add information on a new pending delete.  If successful the row ID of the item is 
    // returned, else -1.
    public long addPendingDelete(String type, long TDID, long accountID)
    {
        ContentValues values = new ContentValues();
        values.put("type",type);
        values.put("td_id", TDID);
        values.put("account_id", accountID);
        values.put("remote_id", "");         
        values.put("remote_tasklist_id",""); 
        return Util.db().insert(TABLE, null, values);
    }
    
    // Alternate function to add information on a new pending delete.  If successful the 
    // row ID of the item is returned, else -1.
    public long addPendingDelete(String type, String remoteID, String remoteTasklistID,
    	long accountID)
    {
    	ContentValues values = new ContentValues();
        values.put("type",type);
        values.put("td_id",-1);
        values.put("account_id", accountID);
        values.put("remote_id",remoteID);
        values.put("remote_tasklist_id",remoteTasklistID);
        return Util.db().insert(TABLE, null, values);
    }
    
    // Delete a pending delete.  Returns true on success, else false.
    public boolean deletePendingDelete(long id)
    {
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Get all pending deletes.
    public Cursor getPendingDeletes()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, null, null, null, null, null);
        return c;
    }
    
    // Get all pending deletes of a particular type and account:
    public Cursor getPendingDeletes(String type, long accountID)
    {
        return Util.db().query(TABLE, COLUMNS, "type='"+type+"' and account_id="+accountID, null, null, null, null);
    }
    
    // Check to see if an item exists in the pending deletes table.  Returns a row ID if it does,
    // else zero.
    public long isDeletePending(String type, long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "type='"+type+"' and account_id="+accountID+
            " and td_id="+TDID, null, null, null, null);
        if (c.moveToFirst())
        {
        	long rowID = c.getLong(0);
        	c.close();
            return rowID;
        }
        c.close();
        return 0;
    }

    // Check to see if an item exists in the pending deletes table.  Returns a row ID if it does,
    // else zero.
    public long isDeletePending(String type, long accountID, String remoteID, String remoteFolderID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "type='"+type+"' and account_id="+accountID+
            " and remote_id='"+Util.makeSafeForDatabase(remoteID)+"' and remote_tasklist_id='"+
            Util.makeSafeForDatabase(remoteFolderID)+"'", null, null, null, null);
        if (c.moveToFirst())
        {
        	long rowID = c.getLong(0);
        	c.close();
            return rowID;
        }
        c.close();
        return 0;
    }
}
