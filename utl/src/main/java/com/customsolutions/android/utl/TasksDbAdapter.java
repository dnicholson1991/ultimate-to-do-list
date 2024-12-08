package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;

public class TasksDbAdapter 
{
    // The name of the table we're using:
    public static final String TABLE = "tasks";
    
    // Stores the last task that was attempted to be added or edited in the database.
    // This is used to debug issues with database modifications.
    public static UTLTask lastTaskEdited;
    
    private static final String TABLE_CREATE = "create table if not exists tasks ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+
        "account_id integer not null, "+
        "mod_date integer not null, "+
        "sync_date integer not null, "+
        "title text not null, "+
        "completed integer not null, "+
        "folder_id integer, "+
        "context_id integer, "+
        "goal_id integer, "+
        "parent_id integer, "+
        "due_date integer, "+
        "due_modifier text, "+
        "uses_due_time integer, "+
        "reminder integer, "+
        "nag integer, "+
        "start_date integer, "+
        "uses_start_time integer, "+
        "repeat integer, "+
        "rep_advanced text, "+
        "status integer, "+
        "length integer, "+
        "priority integer, "+
        "star integer, "+
        "note text, "+
        "timer integer, "+
        "timer_start_time integer, "+
        "completion_date integer, "+
        "location_id integer, "+
        "location_reminder integer, "+
        "location_nag integer, "+
        "remote_id text, "+
        "position text, "+
        "new_task_generated integer, "+
        "prev_folder_id text, "+
        "prev_parent_id text, "+
        "contact_lookup_key text, "+
        "cal_event_uri text, "+
        "is_joint integer, "+
        "owner_remote_id text, "+
        "shared_with text, "+
        "added_by text, "+
        "shared_with_changed integer, "+
        "sort_order integer, "+
        "is_moved integer, "+
        "prev_task_id integer, " +
        "uuid text"+
        ")";
    
    public static final String[] INDEXES = {
    	"create index if not exists td_id_index on tasks(td_id)",
    	"create index if not exists account_id_index on tasks(account_id)",
    	"create index if not exists completed_index on tasks(completed)",
    	"create index if not exists folder_id_index on tasks(folder_id)",
    	"create index if not exists context_id_index on tasks(context_id)",
    	"create index if not exists goal_id_index on tasks(goal_id)",
    	"create index if not exists location_id_index on tasks(location_id)",
    	"create index if not exists parent_id_index on tasks(parent_id)",
    	"create index if not exists star_index on tasks(star)",
    	"create index if not exists remote_id_index on tasks(remote_id)",
        "create index if not exists moved_index on tasks(is_moved)",
        "create index if not exists uuid_index on tasks(uuid)"
    };
    
    // The order in which the columns are queried:
    public static final String[] COLUMNS = { "_id","td_id","account_id","mod_date","sync_date",
        "title","completed","folder_id","context_id","goal_id","parent_id","due_date",
        "due_modifier","uses_due_time","reminder","nag","start_date","uses_start_time",
        "repeat","rep_advanced","status","length","priority","star","note","timer",
        "timer_start_time","completion_date","location_id","location_reminder",
        "location_nag","remote_id","position","new_task_generated","prev_folder_id",
        "prev_parent_id","contact_lookup_key","cal_event_uri","is_joint","owner_remote_id",
        "shared_with","added_by","shared_with_changed","sort_order","is_moved","prev_task_id",
        "uuid"
    };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public TasksDbAdapter() throws SQLException
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

    /** Insert a task into the database. If successful, the id of the task is returned, else -1.
     * This also updates teh UTLTask instance to include the new ID. It also updates the mod_time
     * field in the database. */
    public long addTask(UTLTask t)
    {
    	lastTaskEdited = t;
        ContentValues values = new ContentValues();
        values.put("td_id", t.td_id);
        values.put("account_id", t.account_id);
        if (t.mod_date==0)
        {
            values.put("mod_date",System.currentTimeMillis());
        }
        else
        {
            values.put("mod_date",t.mod_date);
        }
        values.put("sync_date",t.sync_date);
        values.put("title",t.title);
        values.put("completed",t.completed ? 1 : 0);
        values.put("folder_id",t.folder_id);
        values.put("context_id",t.context_id);
        values.put("goal_id",t.goal_id);
        values.put("location_id", t.location_id);
        values.put("parent_id",t.parent_id);
        values.put("due_date",t.due_date);
        values.put("due_modifier",t.due_modifier);
        values.put("uses_due_time",t.uses_due_time ? 1 : 0);
        values.put("reminder",t.reminder);
        values.put("nag",t.nag ? 1 : 0);
        values.put("start_date",t.start_date);
        values.put("uses_start_time",t.uses_start_time ? 1 : 0);
        values.put("repeat",t.repeat);
        values.put("rep_advanced",t.rep_advanced);
        values.put("status",t.status);
        values.put("length",t.length);
        values.put("priority",t.priority);
        values.put("star",t.star ? 1 : 0);
        values.put("note",t.note);
        values.put("timer",t.timer);
        values.put("timer_start_time",t.timer_start_time);
        values.put("completion_date",t.completion_date);
        values.put("location_reminder", t.location_reminder ? 1 : 0);
        values.put("location_nag", t.location_nag ? 1 : 0);
        values.put("remote_id", t.remote_id);
        values.put("position", t.position);
        values.put("new_task_generated",t.new_task_generated ? 1 : 0);
        values.put("prev_folder_id", t.prev_folder_id);
        values.put("prev_parent_id", t.prev_parent_id);
        values.put("contact_lookup_key", t.contactLookupKey);
        values.put("cal_event_uri", t.calEventUri);
        values.put("is_joint", t.is_joint ? 1 : 0);
        values.put("owner_remote_id", t.owner_remote_id);
        values.put("shared_with", t.shared_with);
        values.put("added_by", t.added_by);
        values.put("shared_with_changed", t.shared_with_changed ? 1 : 0);
        values.put("sort_order", t.sort_order);
        values.put("is_moved", t.is_moved ? 1 : 0);
        values.put("prev_task_id", t.prev_task_id);
        values.put("uuid",t.uuid);
        
        Util.checkDatabaseLock(Util.db());
        long id = Util.db().insert(TABLE, null, values);
        if (id>-1)
        {
            t._id = id;
        }
        return id;
    }
    
    // Delete a task.  Returns true on success, else false.
    public boolean deleteTask(long id)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Delete a task.  Returns true on success, else false.
    public boolean deleteTask(long TDID, long accountID)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "td_id="+TDID+" and account_id="+accountID, null) > 0;
    }

    // Delete a task.  Returns true on success, else false.
    public boolean deleteTask(String remoteID, long accountID)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "remote_id='"+remoteID+"' and account_id="+accountID, 
        	null) > 0;
    }

    /** Modify a task.  Returns true if updated, false otherwise.  Unlike the add function,
     * this does not set the modification date (since that may come from Toodledo). */
    public boolean modifyTask(UTLTask t)
    {
    	lastTaskEdited = t;
        ContentValues values = new ContentValues();
        values.put("td_id", t.td_id);
        values.put("account_id", t.account_id);
        values.put("mod_date",t.mod_date);
        values.put("sync_date",t.sync_date);
        values.put("title",t.title);
        values.put("completed",t.completed ? 1 : 0);
        values.put("folder_id",t.folder_id);
        values.put("context_id",t.context_id);
        values.put("goal_id",t.goal_id);
        values.put("location_id",t.location_id);
        values.put("parent_id",t.parent_id);
        values.put("due_date",t.due_date);
        values.put("due_modifier",t.due_modifier);
        values.put("uses_due_time",t.uses_due_time ? 1 : 0);
        values.put("reminder",t.reminder);
        values.put("nag",t.nag ? 1 : 0);
        values.put("start_date",t.start_date);
        values.put("uses_start_time",t.uses_start_time ? 1 : 0);
        values.put("repeat",t.repeat);
        values.put("rep_advanced",t.rep_advanced);
        values.put("status",t.status);
        values.put("length",t.length);
        values.put("priority",t.priority);
        values.put("star",t.star ? 1 : 0);
        values.put("note",t.note);
        values.put("timer",t.timer);
        values.put("timer_start_time",t.timer_start_time);
        values.put("completion_date",t.completion_date);
        values.put("location_reminder", t.location_reminder ? 1 : 0);
        values.put("location_nag", t.location_nag ? 1 : 0);
        values.put("remote_id", t.remote_id);
        values.put("position", t.position);
        values.put("new_task_generated",t.new_task_generated ? 1 : 0);
        values.put("prev_folder_id", t.prev_folder_id);
        values.put("prev_parent_id", t.prev_parent_id);
        values.put("contact_lookup_key", t.contactLookupKey);
        values.put("cal_event_uri",t.calEventUri);
        values.put("is_joint", t.is_joint ? 1 : 0);
        values.put("owner_remote_id", t.owner_remote_id);
        values.put("shared_with", t.shared_with);
        values.put("added_by", t.added_by);
        values.put("shared_with_changed", t.shared_with_changed ? 1 : 0);
        values.put("sort_order",t.sort_order);
        values.put("is_moved", t.is_moved ? 1 : 0);
        values.put("prev_task_id", t.prev_task_id);

        // Check the database to see if the task has a UUID.  If so, make sure not to clear it or
        // change it.
        Cursor c = Util.db().query(TABLE,new String[] {"uuid"},"_id=?",new String[] {
            String.valueOf(t._id)},null,null,null);
        if (c.moveToFirst())
        {
            String uuid = c.getString(0);
            if (Util.isValid(uuid))
                t.uuid = uuid;
            else
                values.put("uuid",t.uuid);
        }
        c.close();
        
        Util.checkDatabaseLock(Util.db());
        return Util.db().update(TABLE, values, "_id="+t._id, null) > 0;
    }
    
    // Update the sync date for a task.  Set it to the current date/time:
    public boolean updateSyncDate(long taskID)
    {
    	ContentValues values = new ContentValues();
    	values.put("sync_date", System.currentTimeMillis());
        Util.checkDatabaseLock(Util.db());
    	return Util.db().update(TABLE, values, "_id="+taskID, null) > 0;
    }
    
    // Get a specific task.  Returns a UTLTask object if the task exists, else null:
    public UTLTask getTask(long id)
    {
        // Query the DB:
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLTask t = getUTLTask(c);
        	c.close();
            return t;
        }
        else
        {
            return null;
        }
    }
    
    // Given a Toodledo account and ID, get the UTLTask object.  Returns null if it 
    // cannot be found.
    public UTLTask getTask(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "td_id=? and account_id=?",
            new String[] {Long.valueOf(TDID).toString(), Long.valueOf(accountID).toString() }, 
            null, null, null);
        if (c.moveToFirst())
        {
        	UTLTask t = getUTLTask(c);
        	c.close();
            return t;
        }
        else
        {
        	c.close();
            return null;
        }
    }
    
    // Given a Google account and ID, get the UTLTask object.  Returns null if it 
    // cannot be found.
    public UTLTask getTask(long accountID, String remoteID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "remote_id=? and account_id=?",
            new String[] {Util.makeSafeForDatabase(remoteID), Long.valueOf(accountID).toString() }, 
            null, null, null);
        if (c.moveToFirst())
        {
        	UTLTask t = getUTLTask(c);
        	c.close();
            return t;
        }
        else
        {
        	c.close();
            return null;
        }
    }

    /** Search for a Google task. The task may be identified by its Google generated ID or by
     * the UUID generated by UTL. Don't call this if the UUID is blank. */
    public UTLTask getTask(long accountID, String remoteID, String uuid)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "(remote_id=? or uuid=?) and account_id=?",
            new String[] {Util.makeSafeForDatabase(remoteID), uuid, String.valueOf(accountID) },
            null, null, null);
        if (c.moveToFirst())
        {
            UTLTask t = getUTLTask(c);
            c.close();
            return t;
        }
        else
        {
            c.close();
            return null;
        }
    }

    // Run a query for specific tasks.  Inputs are the SQL "where" clause (without the
    // "where" statement) and the SQL "order by" clause (without the "order by" statement).
    // Returns a cursor, which can be converted to a UTLTask object using the getUTLTask()
    // function.
    public Cursor queryTasks(String sqlWhere, String sqlOrderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        c.moveToFirst();
        return c;
    }

    /** Query tasks, using selection arguments. */
    public Cursor queryTasks(String selection, String[] selectionArgs, String orderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, selection, selectionArgs, null, null, orderBy);
        return c;
    }

    // Given a database cursor, get an instance of UTLTask:
    public UTLTask getUTLTask(Cursor c)
    {
        UTLTask t = new UTLTask();
        t._id = c.getLong(0);
        t.td_id = c.getLong(1);
        t.account_id = c.getLong(2);
        t.mod_date = c.getLong(3);
        t.sync_date = c.getLong(4);
        t.title = c.getString(5);
        t.completed = c.getInt(6)==1 ? true : false;
        t.folder_id = c.getLong(7);
        t.context_id = c.getLong(8);
        t.goal_id = c.getLong(9);
        t.parent_id = c.getLong(10);
        t.due_date = c.getLong(11);
        t.due_modifier = c.getString(12);
        t.uses_due_time = c.getInt(13)==1 ? true : false;
        t.reminder = c.getLong(14);
        t.nag = c.getInt(15)==1 ? true : false;
        t.start_date = c.getLong(16);
        t.uses_start_time = c.getInt(17)==1 ? true : false;
        t.repeat = c.getInt(18);
        t.rep_advanced = c.getString(19);
        t.status = c.getInt(20);
        t.length = c.getInt(21);
        t.priority = c.getInt(22);
        t.star = c.getInt(23)==1 ? true : false;
        t.note = c.getString(24);
        t.timer = c.getLong(25);
        t.timer_start_time = c.getLong(26);
        t.completion_date = c.getLong(27);
        t.location_id = c.getLong(28);
        t.location_reminder = c.getInt(29)==1 ? true : false;
        t.location_nag = c.getInt(30)==1 ? true : false;
        t.remote_id = c.getString(31);
        t.position = c.getString(32);
        t.new_task_generated = c.getInt(33)==1 ? true : false;
        t.prev_folder_id = c.getLong(34);
        t.prev_parent_id = c.getLong(35);
        t.contactLookupKey = c.getString(36);
        t.calEventUri = c.getString(37);
        t.is_joint = c.getInt(38)==1 ? true : false;
        t.owner_remote_id = c.getString(39);
        t.shared_with = c.getString(40);
        t.added_by = c.getString(41);      
        t.shared_with_changed = c.getInt(42)==1 ? true : false;
        t.sort_order = c.getLong(43);
        t.is_moved = c.getInt(44)==1 ? true : false;
        t.prev_task_id = c.getLong(45);
        t.uuid = c.getString(46);
        t.updateImportance();
        
        return t;
    }
    
    // Get a string containing all fields from the last task that was attempted to be 
    // added or edited in the database:
    static public String getLastTaskString()
    {
    	if (lastTaskEdited==null)
    	{
    		return("");
    	}
    	
    	String result = "";
    	result += "Last Task Added or Edited:\n";
    	result += "  ID: "+lastTaskEdited._id+"\n";
    	result += "  TD ID: "+lastTaskEdited.td_id+"\n";
    	result += "  Account ID: "+lastTaskEdited.account_id+"\n";
    	result += "  Mod Date: "+getTimestamp(lastTaskEdited.mod_date)+"\n";
    	result += "  Sync Date: "+getTimestamp(lastTaskEdited.sync_date)+"\n";
    	result += "  Title: "+lastTaskEdited.title+"\n";
    	result += "  Completed: "+lastTaskEdited.completed+"\n";
    	result += "  Folder ID: "+lastTaskEdited.folder_id+"\n";
    	result += "  Context ID: "+lastTaskEdited.context_id+"\n";
    	result += "  Goal ID: "+lastTaskEdited.goal_id+"\n";
    	result += "  Location ID: "+lastTaskEdited.location_id+"\n";
    	result += "  Location Reminder: "+lastTaskEdited.location_reminder+"\n";
    	result += "  Location Nag: "+lastTaskEdited.location_nag+"\n";
    	result += "  Parent ID: "+lastTaskEdited.parent_id+"\n";
    	result += "  Due Date: "+getTimestamp(lastTaskEdited.due_date)+"\n";
    	result += "  Due Modifier: "+lastTaskEdited.due_modifier+"\n";
    	result += "  Uses Due Time: "+lastTaskEdited.uses_due_time+"\n";
    	result += "  Reminder: "+getTimestamp(lastTaskEdited.reminder)+"\n";
    	result += "  Start Date: "+getTimestamp(lastTaskEdited.start_date)+"\n";
    	result += "  Uses Start Time: "+lastTaskEdited.uses_start_time+"\n";
    	result += "  Repeat: "+lastTaskEdited.repeat+"\n";
    	result += "  Nag: "+lastTaskEdited.nag+"\n";
    	result += "  Advanced Repeat: "+lastTaskEdited.rep_advanced+"\n";
    	result += "  Status: "+lastTaskEdited.status+"\n";
    	result += "  Expected Length: "+lastTaskEdited.length+"\n";
    	result += "  Priority: "+lastTaskEdited.priority+"\n";
    	result += "  Star: "+lastTaskEdited.star+"\n";
    	result += "  Timer: "+lastTaskEdited.timer+"\n";
    	result += "  Timer Start Time: "+getTimestamp(lastTaskEdited.timer_start_time)+"\n";
    	result += "  Completion Date: "+getTimestamp(lastTaskEdited.completion_date)+"\n";
    	result += "  Remote ID: "+lastTaskEdited.remote_id+"\n";
    	result += "  Position: "+lastTaskEdited.position+"\n";
    	result += "  New Task Generated: "+lastTaskEdited.new_task_generated+"\n";
    	result += "  Prev Folder ID: "+lastTaskEdited.prev_folder_id+"\n";
    	result += "  Prev Parent ID: "+lastTaskEdited.prev_parent_id+"\n";
    	result += "  Contact Lookup Key: "+lastTaskEdited.contactLookupKey+"\n";
    	result += "  Calendar Event URI: "+lastTaskEdited.calEventUri+"\n";
    	result += "  Is Joint? "+lastTaskEdited.is_joint+"\n";
    	result += "  Owner Remote ID: "+lastTaskEdited.owner_remote_id+"\n";
    	result += "  Shared With: "+lastTaskEdited.shared_with+"\n";
    	result += "  Added By: "+lastTaskEdited.added_by+"\n";
    	result += "  Shared With Changed? "+lastTaskEdited.shared_with_changed+"\n";
        result += "  Sort Order: "+lastTaskEdited.sort_order+"\n";
        result += "  Is Moved: "+lastTaskEdited.is_moved+"\n";
        result += "  Prev Task ID: "+lastTaskEdited.prev_task_id+"\n";
        result += "  UUID: "+lastTaskEdited.uuid+"\n";
    	result += "  Note: "+lastTaskEdited.note+"\n";
    	return(result);
    }
    
    // Utility function to log timestamps from the logTaskDetails() function:
    private static String getTimestamp(long ms)
    {
    	if (ms==0)
    		return "none";
    	else
    		return Util.getDateTimeString(ms);
    }

}
