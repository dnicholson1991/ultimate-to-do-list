package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

// Database interface for ToodleDo folders:

public class FoldersDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "folders";

    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+
        "account_id integer not null, "+
        "title text not null, "+
        "archived integer not null, "+
        "ordering integer not null, "+
        "mod_date integer not null, "+
        "sync_date integer, "+
        "remote_id text, "+  // Is a blank string if not yet synced with remote server
        "is_private integer"+
        ")";
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","td_id","account_id","title","archived",
        "ordering","mod_date","sync_date","remote_id","is_private"
    };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public FoldersDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }
    
    // Add a new folder.  If successful, the ID of the folder is returned, else -1.
    // The ordering and mod_date fields are automatically filled in.  The ordering is
    // set to the last item.
    public long addFolder(long TDID, long accountID, String title, boolean archived, boolean isPrivate)
    {
        // Get the maximum value for the ordering field:
        Cursor c = Util.db().rawQuery("select max(ordering) from "+TABLE, null);
        int ordering;
        if (c.getCount()>0)
        {
            c.moveToFirst();
            ordering = c.getInt(0)+1;
        }
        else
        {
            ordering = 1;
        }
        
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("archived",archived ? 1 : 0);
        values.put("ordering",ordering);
        values.put("mod_date", System.currentTimeMillis());
        values.put("sync_date", 0);
        values.put("remote_id", "");
        values.put("is_private",isPrivate ? 1 : 0);
        return Util.db().insert(TABLE, null, values);
    }
    
    // Add a new folder, with the new remote ID field.  If successful, the ID of the 
    // folder is returned, else -1.
    // The ordering and mod_date fields are automatically filled in.  The ordering is
    // set to the last item.
    public long addFolder(long TDID, long accountID, String title, boolean archived,
    	String remoteID)
    {
        // Get the maximum value for the ordering field:
        Cursor c = Util.db().rawQuery("select max(ordering) from "+TABLE, null);
        int ordering;
        if (c.getCount()>0)
        {
            c.moveToFirst();
            ordering = c.getInt(0)+1;
        }
        else
        {
            ordering = 1;
        }
        
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("archived",archived ? 1 : 0);
        values.put("ordering",ordering);
        values.put("mod_date", System.currentTimeMillis());
        values.put("sync_date", 0);
        values.put("remote_id", remoteID);
        values.put("is_private",0);
        return Util.db().insert(TABLE, null, values);
    }
    
    // Remove a folder.  Returns true on success, else false.  This does not update
    // any tasks that are within the folder.
    public boolean deleteFolder(long id)
    {
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Modify a folder.  Returns true if updated, false otherwise.  This function does
    // not modify the ordering or sync_date fields.
    public boolean modifyFolder(long id, long TDID, long accountID, String title, 
        boolean archived, boolean isPrivate)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("archived",archived ? 1 : 0);
        values.put("mod_date", System.currentTimeMillis());
        values.put("is_private",isPrivate ? 1 : 0);
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Set the is_private field only:
    public boolean setPrivateField(long id, boolean isPrivate)
    {
    	ContentValues values = new ContentValues();
    	values.put("is_private",isPrivate ? 1 : 0);
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Modify a folder's remote ID:
    public boolean modifyRemoteID(long id, String remoteID)
    {
    	ContentValues values = new ContentValues();
    	values.put("remote_id",remoteID);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Modify the TD ID:
    public boolean modifyTDID(long id, long TDID)
    {
    	ContentValues values = new ContentValues();
    	values.put("td_id",TDID);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Update the ordering for a folder:
    public void updateOrdering(long id, int ordering)
    {
        ContentValues values = new ContentValues();
        values.put("ordering", ordering);
        Util.db().update(TABLE, values, "_id="+id, null);
    }
    
    public boolean renameFolder(long id, String newName)
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
    
    // Update the archive status for a folder:
    public boolean setArchiveStatus(long id, boolean isArchived)
    {
    	ContentValues values = new ContentValues();
        values.put("archived", isArchived ? 1 : 0);
        values.put("mod_date", System.currentTimeMillis());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Get a specific folder.  Returns a cursor:
    public Cursor getFolder(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Given a Toodledo account and folder ID, get the folder information.  Returns a
    // database cursor:
    public Cursor getFolder(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and td_id="+TDID,
            null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Given a Google account and folder ID, get the folder information.  Returns a
    // database cursor:
    public Cursor getFolder(long accountID, String gID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and remote_id='"+
        	Util.makeSafeForDatabase(gID)+"'",null, null, null, null);
        return c;
    }
    
    // Get a list of folders, sorted by the ordering field.  Returns a database cursor.
    // This does NOT return archived folders:
    public Cursor getFoldersByOrder()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "archived=0", null, null, null,
            "account_id,ordering");
        c.moveToFirst();
        return c;
    }
    
    // Get a list of folders, sorted by name/title.  Returns a database cursor.
    // This does NOT return archived folders:
    public Cursor getFoldersByName()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "archived=0", null, null, null,
            "account_id,title");
        c.moveToFirst();
        return c;
    }
    
    // Same as above - case insentitive:
    public Cursor getFoldersByNameNoCase()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "archived=0", null, null, null,
            "account_id,lower(title)");
        c.moveToFirst();
        return c;
    }

    // Run a query for specific folders.  Inputs are the SQL "where" clause (without the
    // "where" statement) and the SQL "order by" clause (without the "order by" statement).
    // Returns a cursor.
    public Cursor queryFolders(String sqlWhere, String sqlOrderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        return c;
    }

    // Given an account, choose the best sort order for the above queryFoldersFunction:
    static String bestSortOrder(long accountID)
    {
    	UTLAccount a = (new AccountsDbAdapter()).getAccount(accountID);
    	if (a==null || a.sync_service==UTLAccount.SYNC_NONE || a.sync_service==UTLAccount.SYNC_GOOGLE)
    		return "lower(title)";
    	else
    		return "ordering";
    }
}
