package com.customsolutions.android.utl;

import android.database.Cursor;

import java.util.TimeZone;

// This class holds information on a specific task / to-do item.
public class UTLTask 
{
    // Variables for the task.  These will match the corresponding database fields 
    // whenever possible:
    public long _id;
    public long td_id;              // -1 = not yet synced
    public long account_id;
    public long mod_date;           // UNIX timestamp in milliseconds
    public long sync_date;          // UNIX timestamp in ms.  0 if not yet synced.
    public String title;
    public Boolean completed;
    public long folder_id;          // 0 = none
    public long context_id;         // 0 = none
    public long goal_id;            // 0 = none
    public long location_id;        // 0 = none
    public long parent_id;          // Set to 0 if there is no parent. Set to 0-toodledo_parent_ID if we're waiting to download the parent.
    public long due_date;           // ms
    public String due_modifier;     // Either blank, "due_by", "due_on", or "optionally_on"
    public boolean uses_due_time;
    public long reminder;           // ms
    public long start_date;         // ms
    public boolean uses_start_time;
    public int repeat;              // Same as TD's interface (version 1)
    public boolean nag;
    public String rep_advanced;
    public int status;              // Same as TD's interface.  0 = none
    public int length;              // expected minutes.  Use 0 for none.
    public int priority;            // = TD's interface + 2 (i.e., -1 becomes 1).  0 = none
    public boolean star;
    public String note;
    public long timer;             // elapsed time in seconds. zero if none
    public long timer_start_time;  // ms timestamp.  zero if none
    public long completion_date;   // ms timestamp.  zero if not completed
    public boolean location_reminder; // true if user will be notified upon reaching location
    public boolean location_nag;      // nag option for location reminder
    public String remote_id;       // Identifier at remote server (e.g., gTasks ID)
    public String position; // Google task's position field.  Sort alphabetically.
    public boolean new_task_generated; // True if this is completed and the new task has
                                       // been generated based on the repeating pattern.
    public long prev_folder_id;
    public long prev_parent_id;
    public int importance;  // Calculated using Toodledo's formula.
    public String contactLookupKey; // References the contact for the task. Use "" for empty.
    public String calEventUri;  // The Uri of a linked calendar event ("" = none")
    
    // Toodledo Collaboration fields:
    public boolean is_joint;  // Boolean indicating if the task is shared as a joint task.
    public String owner_remote_id;  // The Toodledo user ID of the task's owner.  May be the current user. "" = none
    public String shared_with;  // A newline separated list of Toodledo user IDs that the task is shared with. "" = none
    public String added_by;  // The Toodledo user ID of the assignor. "" = none
    public boolean shared_with_changed;  // true if the shared_with field has changed and an upload is needed

    public long sort_order;  // An int representing the manual sort order.  Range = 0 (unsorted) to Long.MAX_VALUE
    public boolean is_moved; // true if the task has been moved and the move is not uploaded.
    public long prev_task_id; // The previous task ID. Is valid if is_moved==true. 0=Top of the list.
        // -1=Use sort_order field to order it upon upload.
        // 12/12/2015: Currently, prev_task_id is always set to -1.  If this continues to be the case,
        // then this task field should be deleted to clean up the code.

    /** An optional universally unique identifier. If used, this will be the same across devices. */
    public String uuid;

    public UTLTask()
    {
        _id = 0;
        td_id = -1;
        account_id = 0;
        mod_date = 0;
        sync_date = 0;
        title = "";
        completed = false;
        folder_id = 0;
        context_id = 0;
        goal_id = 0;
        location_id = 0;
        parent_id = 0;
        due_date = 0;
        due_modifier = "";
        uses_due_time = false;
        reminder = 0;
        nag = false;
        start_date = 0;
        uses_start_time = false;
        repeat = 0;
        rep_advanced = "";
        status = 0;
        length = 0;
        priority = 0;
        star = false;
        note = "";
        timer = 0;
        timer_start_time = 0;
        completion_date = 0;
        location_reminder = false;
        location_nag = false;
        remote_id = "";
        position = "";
        new_task_generated = false;
        prev_folder_id = 0;
        prev_parent_id = 0;
        importance = 0;
        contactLookupKey = "";
        calEventUri = "";
        is_joint = false;
        owner_remote_id = "";
        shared_with = "";
        added_by = "";
        shared_with_changed = false;
        sort_order = 0;
        is_moved = false;
        prev_task_id = 0;
        uuid = "";
    }
    
    // Clone a task.  This sets the initial values of _id, td_id, and sync_date to the same as a 
    // new task instance.
    public UTLTask clone()
    {
        UTLTask t = new UTLTask();
        t._id = 0;
        t.td_id = -1;
        t.account_id = account_id;
        t.mod_date = mod_date;
        t.sync_date = 0;
        t.title = title;
        t.completed = completed;
        t.folder_id = folder_id;
        t.context_id = context_id;
        t.goal_id = goal_id;
        t.location_id = location_id;
        t.parent_id = parent_id;
        t.due_date = due_date;
        t.due_modifier = due_modifier;
        t.uses_due_time = uses_due_time;
        t.reminder = reminder;
        t.nag = nag;
        t.start_date = start_date;
        t.uses_start_time = uses_start_time;
        t.repeat = repeat;
        t.rep_advanced = rep_advanced;
        t.status = status;
        t.length = length;
        t.priority = priority;
        t.star = star;
        t.note = note;
        t.timer = timer;
        t.timer_start_time = timer_start_time;
        t.completion_date = completion_date;
        t.location_reminder = location_reminder;
        t.location_nag = location_nag;
        t.remote_id = remote_id;
        t.position = position;
        t.new_task_generated = new_task_generated;
        t.prev_folder_id = prev_folder_id;
        t.prev_parent_id = prev_parent_id;
        t.importance = importance;
        t.contactLookupKey = contactLookupKey;
        t.calEventUri = calEventUri;
        t.is_joint = is_joint;
        t.owner_remote_id = owner_remote_id;
        t.shared_with = shared_with;
        t.added_by = added_by;
        t.shared_with_changed = shared_with_changed;
        t.sort_order = sort_order;
        t.is_moved = is_moved;
        t.prev_task_id = prev_task_id;
        t.uuid = "";
        return t;
    }
    
    // Update the importance field:
    public void updateImportance()
    {
    	// Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long zoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	long baseTime = Util.getMidnight((System.currentTimeMillis()+zoneOffset))/1000;
    	
    	importance = priority + (star ? 1 : 0);
    	if (due_date==0 || due_date/1000-baseTime>=1209600)
    		importance += 0;
    	else if (due_date/1000-baseTime>=604800)
    		importance += 1;
    	else if (due_date/1000-baseTime>=172800)
    		importance += 2;
    	else if (due_date/1000-baseTime>=86400)
    		importance += 3;
    	else if (due_date/1000-baseTime>=0)
    		importance += 5;
    	else
    		importance += 6;
    }

    /** Set a value of sort_order so that the task is at the bottom of the list. This does not
     * save the change in the database. If the parent or folder is being moved, make sure to
     * update these values before calling this method. */
    public void setSortOrderToBottom()
    {
        // For Toodledo and Unsynced accounts, we sort at the account level.  For Google, we sort
        // at the folder level.
        AccountsDbAdapter adb = new AccountsDbAdapter();
        UTLAccount a = adb.getAccount(account_id);
        Cursor c;
        if (a.sync_service==UTLAccount.SYNC_GOOGLE)
        {
            // 7/19/19: Due to a bug on Google's side, the task cannot have an completed task
            // as it's predecessor. This complicates the calulation.
            setSortOrderToBottomForGoogle();
            return;
        }
        else
        {
            c = Util.db().rawQuery("select sort_order from tasks where account_id=? and " +
                "parent_id=? order by sort_order asc limit 1", new String[]{Long.valueOf(account_id).
                toString(), Long.valueOf(parent_id).toString()});
        }
        if (c.moveToFirst())
        {
            long minSortOrder = c.getLong(0);
            if (minSortOrder == 0)
            {
                // Since we have some unsorted tasks, this task must also be unsorted.
                sort_order = 0;
            }
            else
                sort_order = minSortOrder / 2;
        }
        else
        {
            // There are no other tasks.  Set the sort order to a default value.
            sort_order = Long.MAX_VALUE/2;
        }

        c.close();
    }

    public void setSortOrderToBottomForGoogle()
    {
        // Find the lowest incomplete task:
        Cursor c = Util.db().rawQuery("select sort_order from tasks where folder_id=? and " +
            "parent_id=? and completed=0 order by sort_order asc limit 1", new String[]{String.
            valueOf(folder_id),String.valueOf(parent_id)});
        long sortOrder1;
        if (!c.moveToFirst())
        {
            // There are no incomplete tasks in the list.
            sortOrder1 = Long.MAX_VALUE;
        }
        else
            sortOrder1 = Util.cLong(c,"sort_order");
        c.close();

        // Check to see if there is a complete task further down the list.
        c = Util.db().rawQuery("select sort_order from tasks where folder_id=? and " +
            "parent_id=? and sort_order<? order by sort_order desc limit 1", new String[]{String.
            valueOf(folder_id),String.valueOf(parent_id),String.valueOf(sortOrder1)});
        long sortOrder2;
        if (!c.moveToFirst())
        {
            // There are no completed tasks further down the list.
            sortOrder2 = 0;
        }
        else
            sortOrder2 = Util.cLong(c,"sort_order");

        // The sort_order of this task falls in between the 2 values we just found.
        sort_order = sortOrder2 + (sortOrder1-sortOrder2)/2;
    }

    /** Set a value of sort_order so that the task is at the top of the list. This does not
     * save the change in the database. If the parent or folder is being moved, make sure to
     * update these values before calling this method. */
    public void setSortOrderToTop()
    {
        // For Toodledo and Unsynced accounts, we sort at the account level.  For Google, we sort
        // at the folder leve.
        AccountsDbAdapter adb = new AccountsDbAdapter();
        UTLAccount a = adb.getAccount(account_id);
        Cursor c;
        if (a.sync_service==UTLAccount.SYNC_GOOGLE)
        {
            c = Util.db().rawQuery("select sort_order from tasks where folder_id=? and " +
                "parent_id=? order by sort_order desc limit 1", new String[]{Long.valueOf(folder_id).
                toString(), Long.valueOf(parent_id).toString()});
        }
        else
        {
            c = Util.db().rawQuery("select sort_order from tasks where account_id=? and " +
                "parent_id=? order by sort_order desc limit 1", new String[]{Long.valueOf(account_id).
                toString(), Long.valueOf(parent_id).toString()});
        }
        if (c.moveToFirst())
        {
            long maxSortOrder = c.getLong(0);
            if (maxSortOrder == 0)
            {
                // This would only happen if all tasks are unsorted. Set the sort order to a
                // default value.
                sort_order = Long.MAX_VALUE/2;
            }
            else
                sort_order = maxSortOrder + (Long.MAX_VALUE-maxSortOrder)/2;
        }
        else
        {
            // There are no other tasks.  Set the sort order to a default value.
            sort_order = Long.MAX_VALUE/2;
        }

        c.close();
    }
}
