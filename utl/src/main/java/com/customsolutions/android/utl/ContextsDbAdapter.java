package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

// Database interface for ToodleDo contexts:

public class ContextsDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "contexts";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+                             // -1 = not synced yet
        "account_id integer not null, "+
        "title text not null, "+
        "mod_date integer not null, "+
        "sync_date integer"+
        ")";
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","td_id","account_id","title",
    	"mod_date","sync_date"
        };
    
    private static boolean tablesCreated = false;

    // The constructor.  This opens the database for access:
    public ContextsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }
    
    // Add a new context.  If successful the ID of the new context is returned, else -1.
    // The mod_date field is automatically filled in.
    public long addContext(long TDID, long accountID, String title)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("mod_date", System.currentTimeMillis());
        values.put("sync_date", 0);
        return Util.db().insert(TABLE, null, values);
    }
    
    // Remove a context.  Returns true of success, else false.  This does not update
    // any tasks that are associated with the context:
    public boolean deleteContext(long id)
    {
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Modify a context.  Returns true if updated, false otherwise.  The mod_date field
    // is filled in automatically.  sync_date is not modified here.
    public boolean modifyContext(long id, long TDID, long accountID, String title)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("mod_date", System.currentTimeMillis());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Modify the TD ID:
    public boolean modifyTDID(long id, long TDID)
    {
    	ContentValues values = new ContentValues();
    	values.put("td_id",TDID);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Rename a context:
    public boolean renameContext(long id, String newName)
    {
    	ContentValues values = new ContentValues();
    	values.put("title", newName);
    	values.put("mod_date", System.currentTimeMillis());
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Set the sync date:
    public boolean setSyncDate(long id, long newSyncDate)
    {
    	ContentValues values = new ContentValues();
    	values.put("sync_date", newSyncDate);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Get a specific context.  Returns a database cursor:
    public Cursor getContext(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Given a Toodledo account and context ID, get the context information.  Returns a
    // database cursor:
    public Cursor getContext(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and td_id="+TDID,
            null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Get a list of contexts, sorted by name.  Returns a database cursor.
    public Cursor getContextsByName()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, null, null, null, null,
            "account_id,title");
        c.moveToFirst();
        return c;
    }
    
    // Same as above - case insensitive:
    public Cursor getContextsByNameNoCase()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, null, null, null, null,
            "account_id,lower(title)");
        c.moveToFirst();
        return c;
    }
    
    // Run a query for specific contexts.  Inputs are the SQL "where" clause (without the
    // "where" statement) and the SQL "order by" clause (without the "order by" statement).
    // Returns a cursor.
    public Cursor queryContexts(String sqlWhere, String sqlOrderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        return c;
    }
}
