package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

// Database interface for Goals

public class GoalsDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "goals";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+
        "account_id integer not null, "+
        "title text not null, "+
        "archived integer not null, "+
        "contributes integer, "+             // UTL goal ID, not Toodledo ID.
        "level integer not null, "+          // 0=Lifelong, 1=Long-term, 2=Short-term
        "mod_date integer not null, "+
        "sync_date integer"+
        ")";
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","td_id","account_id","title","archived",
        "contributes","level","mod_date","sync_date"
    };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public GoalsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }

    // Adds a goal to the database.  The mode_date field is auto-filled.
    // If successful, the ID of the goal is returned, else -1.
    // Set contributes to 0 to not link to another goal.
    public long addGoal(long TDID, long accountID, String title, boolean archived, 
        long contributes, int level)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("archived",archived ? 1 : 0);
        values.put("contributes",contributes);
        values.put("level", level);
        values.put("mod_date", System.currentTimeMillis());
        values.put("sync_date", 0);
        return Util.db().insert(TABLE, null, values);
    }
    // Remove a goal.  Returns true on success, else false.  This does not update
    // any tasks that are within the goal.
    public boolean deleteGoal(long id)
    {
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }

    // Modify a Goal.  Returns true if updated, false otherwise.  The mod_date field is
    // automatically updated.  sync_date is not modified here.
    public boolean modifyGoal(long id, long TDID, long accountID, String title, 
        boolean archived, long contributes, int level)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", TDID);
        values.put("account_id",accountID);
        values.put("title", title);
        values.put("archived",archived ? 1 : 0);
        values.put("contributes",contributes);
        values.put("level", level);
        values.put("mod_date", System.currentTimeMillis());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Set the value of the "contributes" field for a goal:
    public boolean setContributes(long id, long contributes)
    {
        ContentValues values = new ContentValues();
        values.put("contributes",contributes);
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
    
    // Rename a goal:
    public boolean renameGoal(long id, String newName)
    {
    	ContentValues values = new ContentValues();
    	values.put("title", newName);
    	values.put("mod_date", System.currentTimeMillis());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Set the archived status:
    public boolean setArchiveStatus(long id, boolean isArchived)
    {
    	ContentValues values = new ContentValues();
    	values.put("archived",isArchived ? 1 : 0);
    	values.put("mod_date", System.currentTimeMillis());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Update the goal's level:
    public boolean setLevel(long id, int newLevel)
    {
    	ContentValues values = new ContentValues();
    	values.put("level", newLevel);
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
    
    // Get a specific goal.  Returns a cursor:
    public Cursor getGoal(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Given a Toodledo account and goal ID, get the goal information.  Returns a
    // database cursor:
    public Cursor getGoal(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and td_id="+TDID,
            null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Get all goals, sorted by: account, level, title.  Archived goals are not returned.
    public Cursor getAllGoals()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "archived=0", null, null, null, "account_id,level,title");
        c.moveToFirst();
        return c;
    }

    // Same as above - case insensitive:
    public Cursor getAllGoalsNoCase()
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "archived=0", null, null, null, "account_id,level,lower(title)");
        c.moveToFirst();
        return c;
    }
    
    // Run a query for specific goals.  Inputs are the SQL "where" clause (without the
    // "where" statement) and the SQL "order by" clause (without the "order by" statement).
    // Returns a cursor.
    public Cursor queryGoals(String sqlWhere, String sqlOrderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        return c;
    }
    
}
