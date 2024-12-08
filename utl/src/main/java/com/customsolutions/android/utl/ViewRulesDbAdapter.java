package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

// This class provides the database interface for view rules, which are used to determine
// which tasks to show in a particular view.

public class ViewRulesDbAdapter 
{
    // Lock Levels for Rules:
    public static final int NO_LOCK = 0;
    public static final int EDIT_ONLY = 1;
    public static final int FULL_LOCK = 2;
    
    // The table we're working with:
    private static final String TABLE = "view_rules";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "view_id integer not null, "+
        "lock_level integer not null, "+  // 0 = none, 1 = edit only, 2 = full lock
        "field_name text not null, "+
        "filter_string text not null, "+
        "position integer not null, "+  // Starts at 0
        "is_or integer not null"+  // 1 = ORed with previous rule, 0 = AND
        ")";
    
    public static final String[] INDEXES = {
    	"create index if not exists view_id_index on view_rules(view_id)"
    };

    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","view_id","lock_level","field_name",
        "filter_string","position","is_or" };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public ViewRulesDbAdapter() throws SQLException
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
    
    // Add a rule to the specified view.  If successful, a row ID is returned, 
    // else -1.  The value of insertBefore must be in the range of 0 to the number of
    // rules.  Pass in the number of rules to append to the end.
    public long addRule(long viewID, int lock_level, ViewRule rule, int insertBefore, 
        boolean is_or)
    {
        ContentValues values = new ContentValues();
        values.put("view_id",viewID);
        values.put("lock_level",lock_level);
        values.put("field_name",rule.dbField);
        values.put("filter_string", rule.getDatabaseString());
        values.put("position",insertBefore);
        values.put("is_or",is_or ? 1 : 0);
        long rowID = Util.db().insert(TABLE, null, values);
        if (rowID>=0)
        {
            // We need to shift the index values:
            Cursor c = Util.db().query(TABLE, COLUMNS, "view_id="+viewID, null, null, null, null);
            while (c.moveToNext())
            {
                if (Util.cLong(c,"_id")!=rowID && Util.cLong(c, "position")>=insertBefore)
                {
                    values.clear();
                    values.put("position",Util.cLong(c, "position")+1);
                    Util.db().update(TABLE, values, "_id="+Util.cLong(c,"_id"), null);
                }
            }
            c.close();
        }
        return rowID;
    }
    
    // Remove a rule from the specified view.  Returns true on success, else false.
    public boolean deleteRule(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        if (c.moveToFirst())
        {
            long viewID = Util.cLong(c, "view_id");
            boolean isSuccessful = Util.db().delete(TABLE, "_id="+id, null) > 0;
            if (isSuccessful)
            {
                // Delete succeeded.  Update the indexes:
                c = Util.db().query(TABLE, COLUMNS, "view_id="+viewID, null, null, null, 
                    "position asc");
                int index = 0;
                ContentValues values = new ContentValues();
                while (c.moveToNext())
                {
                    values.put("position",index);
                    Util.db().update(TABLE, values, "_id="+Util.cLong(c,"_id"), null);
                    index++;
                }
                return true;
            }
            else
            {
                return false;
            }
        }
        return true;
    }
    
    // Clear all rules from a view:
    public void clearAllRules(long viewID)
    {
        Util.db().delete(TABLE, "view_id="+viewID, null);
    }
    
    // Get all rules for a view.  Returns a database cursor:
    public Cursor getAllRules(long viewID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "view_id="+viewID, null, null, null, 
            "position asc");
        return c;
    }
    
    // Copy the view rules from a source to a destination view.  All rules at the destination are
    // overwritten:
    public void copyRules(long sourceViewID, long destViewID)
    {
    	clearAllRules(destViewID);
    	Cursor c = getAllRules(sourceViewID);
    	while (c.moveToNext())
    	{
    		ContentValues values = new ContentValues();
            values.put("view_id",destViewID);
            values.put("lock_level",Util.cInt(c, "lock_level"));
            values.put("field_name",Util.cString(c, "field_name"));
            values.put("filter_string", Util.cString(c, "filter_string"));
            values.put("position",Util.cInt(c, "position"));
            values.put("is_or",Util.cInt(c, "is_or"));
            Util.db().insert(TABLE, null, values);
    	}
    	c.close();
    }
    
    // Get a specific rule, given the row ID of the rule:
    public Cursor getRule(long rowID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+rowID, null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Given a database cursor, generate a ViewRule object:
    public ViewRule getViewRule(Cursor c)
    {
        if (c.getString(3).equals("account_id"))
        {
            return new MultChoiceViewRule("account_id",c.getString(4));
        }
        else if (c.getString(3).equals("folder_id"))
        {
            return new MultChoiceViewRule("folder_id",c.getString(4));            
        }
        else if (c.getString(3).equals("context_id"))
        {
            return new MultChoiceViewRule("context_id",c.getString(4));            
        }
        else if (c.getString(3).equals("goal_id"))
        {
            return new MultChoiceViewRule("goal_id",c.getString(4));            
        }
        else if (c.getString(3).equals("location_id"))
        {
            return new LocationViewRule("location_id",c.getString(4));            
        }
        else if (c.getString(3).equals("status"))
        {
            return new MultChoiceViewRule("status",c.getString(4));            
        }
        else if (c.getString(3).equals("priority"))
        {
            return new MultChoiceViewRule("priority",c.getString(4));            
        }
        else if (c.getString(3).equals("parent_id"))
        {
            return new MultChoiceViewRule("parent_id",c.getString(4));            
        }
        else if (c.getString(3).equals("tags.name"))
        {
            return new MultStringsViewRule("tags.name",c.getString(4));            
        }
        else if (c.getString(3).equals("title"))
        {
            return new TextViewRule("title",c.getString(4));            
        }
        else if (c.getString(3).equals("note"))
        {
            return new TextViewRule("note",c.getString(4));            
        }
        else if (c.getString(3).equals("completed"))
        {
            return new BooleanViewRule("completed",c.getString(4));            
        }
        else if (c.getString(3).equals("star"))
        {
            return new BooleanViewRule("star",c.getString(4));            
        }
        else if (c.getString(3).equals("length"))
        {
            return new IntViewRule("length",c.getString(4));            
        }
        else if (c.getString(3).equals("timer"))
        {
            return new IntViewRule("timer",c.getString(4));            
        }
        else if (c.getString(3).equals("timer_start_time"))
        {
            return new IntViewRule("timer_start_time",c.getString(4));            
        }
        else if (c.getString(3).equals("start_date"))
        {
            return new DateViewRule("start_date",c.getString(4));            
        }
        else if (c.getString(3).equals("reminder"))
        {
            return new DateViewRule("reminder",c.getString(4));            
        }
        else if (c.getString(3).equals("completion_date"))
        {
            return new DateViewRule("completion_date",c.getString(4));
        }
        else if (c.getString(3).equals("mod_date"))
        {
            return new DateViewRule("mod_date",c.getString(4));
        }
        else if (c.getString(3).equals("importance"))
        {
        	return new IntViewRule("importance",c.getString(4));
        }
        else if (c.getString(3).equals("contact_lookup_key"))
        {
        	return new MultStringsViewRule("contact_lookup_key",c.getString(4));
        }
        else if (c.getString(3).equals("is_joint"))
        {
            return new BooleanViewRule("is_joint",c.getString(4));            
        }
        else if (c.getString(3).equals("owner_remote_id"))
        {
        	return new MultStringsViewRule("owner_remote_id",c.getString(4));
        }
        else if (c.getString(3).equals("added_by"))
        {
        	return new MultStringsViewRule("added_by",c.getString(4));
        }
        else
        {
            // Must be due_date
            return new DueDateViewRule(c.getString(4));            
        }
    }
}
