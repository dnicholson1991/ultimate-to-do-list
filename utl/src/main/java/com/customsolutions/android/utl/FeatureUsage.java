package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Created by Nicholson on 2/14/2015.
 * This class is used to track feature usage.  It sends usage information to the database
 * on our server.
 */
public class FeatureUsage
{
    // The names of the features we will track:
    public static final String CALENDAR_INTEGRATION = "calendar integration";
    public static final String TOODLEDO_COLLABORATION = "toodledo collaboration";
    public static final String CONTACTS = "contacts";
    public static final String CONTEXTS = "contexts";
    public static final String DUE_DATE = "due date";
    public static final String DUE_DATE_MODIFIER = "due date modifier";
    public static final String DUE_TIME = "due time";
    public static final String EXPECTED_LENGTH = "expected length";
    public static final String FOLDERS_FOR_NOTES = "folders for notes";
    public static final String FOLDERS_FOR_TASKS = "folders for tasks";
    public static final String GOALS = "goals";
    public static final String LOCATIONS = "locations";
    public static final String LOCATION_REMINDERS = "location reminders";
    public static final String NAGGING_LOCATION_REMINDERS = "nagging location reminders";
    public static final String PRIORITY = "priority";
    public static final String TIMED_REMINDERS = "timed reminders";
    public static final String NAGGING_TIMED_REMINDERS = "nagging timed reminders";
    public static final String REPEATING_TASKS = "repeating tasks";
    public static final String ADVANCED_REPEAT = "advanced repeat patterns";
    public static final String STAR = "star";
    public static final String START_DATE = "start date";
    public static final String START_TIME = "start time";
    public static final String STATUS = "status";
    public static final String SUBTASKS = "subtasks";
    public static final String TAGS = "tags";
    public static final String TIMER = "timer";
    public static final String NOTES = "notes";
    public static final String VOICE_MODE = "voice mode";
    public static final String ANDROID_WEAR = "android wear";
    public static final String TOODLEDO_IMPORTANCE = "toodledo importance";
    public static final String MANUAL_SORT = "manual_sort";
    public static final String TASK_MAP = "task_map";

    // The table that stores feature info.
    private static final String TABLE = "feature_usage";
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "name text not null, "+
        "count integer not null"+
        ")";
    private static final String INDEX_CREATE = "create index if not exists feature_usage_index on "+
        TABLE+"(name)";
    private static boolean _tablesCreated = false;

    private Context _c;

    public static void createTable(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
        db.execSQL(INDEX_CREATE);
        _tablesCreated = true;
    }

    FeatureUsage(Context c)
    {
        _c = c;
        if (!_tablesCreated)
            createTable(Util.db());
    }

    /** Record the usage of a feature. */
    public void record(String featureName)
    {
        // Get the current count for this feature (if any):
        Cursor c = Util.db().query(TABLE,new String[] {"count"},"name='"+featureName+"'",null,null,null,
            null);
        try
        {
            if (!c.moveToFirst())
            {
                // No current entry.  Let's make one.
                ContentValues values = new ContentValues();
                values.put("name",featureName);
                values.put("count",1);
                Util.db().insert(TABLE, null, values);
            }
            else
            {
                // Update the current entry.
                long count = c.getLong(0);
                count++;
                ContentValues values = new ContentValues();
                values.put("count",count);
                Util.db().update(TABLE, values, "name='"+featureName+"'", null);

                // If the new count is a power of 2, then send the data to our server.
                if ((count & (count-1)) == 0)
                {
                    if (count>=4)
                        Util.logEvent(_c,"feature_"+featureName+"_used",0,new String[] { Long.valueOf(count).toString() });
                }
            }
        }
        finally
        {
            c.close();
        }
    }

    /** Record the feature usage for a task. */
    public void recordForTask(UTLTask t)
    {
        if (t.completed)
        {
            // We don't record anything for completed tasks.
            return;
        }

        if (t.folder_id!=0) record(FOLDERS_FOR_TASKS);
        if (t.context_id!=0) record(CONTEXTS);
        if (t.goal_id!=0) record(GOALS);
        if (t.location_id!=0) record(LOCATIONS);
        if (t.parent_id!=0) record(SUBTASKS);
        if (t.due_date>0) record(DUE_DATE);
        if (t.uses_due_time) record(DUE_TIME);
        if (t.due_modifier!=null && t.due_modifier.equals("due_on") || t.due_modifier.equals("optionally_on"))
            record(DUE_DATE_MODIFIER);
        if (t.reminder>0) record(TIMED_REMINDERS);
        if (t.start_date>0) record(START_DATE);
        if (t.uses_start_time) record(START_TIME);
        if (t.repeat>0) record(REPEATING_TASKS);
        if (t.rep_advanced!=null && t.rep_advanced.length()>0) record(ADVANCED_REPEAT);
        if (t.nag) record(NAGGING_TIMED_REMINDERS);
        if (t.status>0) record(STATUS);
        if (t.length>0) record(EXPECTED_LENGTH);
        if (t.priority>0) record(PRIORITY);
        if (t.star) record(STAR);
        if (t.timer>0 || t.timer_start_time>0) record(TIMER);
        if (t.location_reminder) record(LOCATION_REMINDERS);
        if (t.location_nag) record(NAGGING_LOCATION_REMINDERS);
        if (t.contactLookupKey!=null && t.contactLookupKey.length()>0) record(CONTACTS);
        if (t.calEventUri!=null && t.calEventUri.length()>0) record(CALENDAR_INTEGRATION);
        if (t.is_joint) record(TOODLEDO_COLLABORATION);
    }

    /** Record the feature usage for a note. */
    public void recordForNote(UTLNote n)
    {
        record(NOTES);
        if (n.folder_id!=0) record(FOLDERS_FOR_NOTES);
    }
}
