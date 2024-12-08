package com.customsolutions.android.utl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.UUID;

/**
 * This stores information on a task template.  For example, for use by Tasker to create a new
 * task.
 */

public class TaskTemplate
{
    // Times in a template are relative to the current time. These constants determine the units
    // of the offset.  The values corresponding to minutes to years must be one plus the
    // corresponding index of the "time_units" array defined in arrays.xml.
    public static final int OFFSET_UNUSED = 0;
    public static final int OFFSET_MINUTES = 1;
    public static final int OFFSET_HOURS = 2;
    public static final int OFFSET_DAYS = 3;
    public static final int OFFSET_WEEKS = 4;
    public static final int OFFSET_MONTHS = 5;
    public static final int OFFSET_YEARS = 6;



    /** A database ID.  Not used in version 3.8, but may be used later. */
    public long _id;

    /** The account to store the task in, as a database ID. */
    public long account_id;

    /** The title.  Use "" if no title is part of the template. */
    public String title;

    /** Whether the task starts out in the completed state. */
    public boolean completed;

    /** The folder database ID. Use 0 for none. */
    public long folder_id;

    /** The context database ID.  Use 0 for none. */
    public long context_id;

    /** The goal ID in the database.  Use 0 for none. */
    public long goal_id;

    /** The location ID in the database.  Use 0 for none. */
    public long location_id;

    /** A list of tags to attach to the task. An empty array is used when there are none. */
    public ArrayList<String> tags;

    /** The parent task ID.  Not used in version 3.8, but may be used later. 0=none.*/
    public long parent_id;

    /** The due date modifier for the new task.  One of: "due_by", "due_on", or "optionally_on".
     * A blank string is the same as "due_by". */
    public String due_modifier;

    /** The units to use when calculating the new task's due date. One of the "offset" constants
     * defined here. If it's OFFSET_UNUSED, then no due date is specified and the other "due"
     * fields here have no meaning. */
    public int due_date_offset_units;

    /** The offset from the current date/time to use when calculating the new task's due date. */
    public int due_date_offset;

    /** If due_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a due
     * time. This is the hours component of that time, in the range 0-23.  -1 = unused. */
    public int due_time_hours;

    /** If due_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a due
     * time. This is the minutes component of that time, in the range 0-59.  -1 = unused. */
    public int due_time_minutes;

    /** The units to use when calculating the new task's start date. One of the "offset" constants
     * defined here. If it's OFFSET_UNUSED, then no start date is specified and the other "start"
     * fields here have no meaning. */
    public int start_date_offset_units;

    /** The offset from the current time to use when calculating the new task's start date. */
    public int start_date_offset;

    /** If start_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a start
     * time. This is the hours component of that time, in the range 0-23.  -1 = unused. */
    public int start_time_hours;

    /** If start_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a start
     * time. This is the minutes component of that time, in the range 0-59.  -1 = unused. */
    public int start_time_minutes;

    /** The units to use when calculating the new task's reminder date. One of the "offset" constants
     * defined here. If it's OFFSET_UNUSED, then no reminder date is specified and the other
     * "reminder" fields here have no meaning. */
    public int reminder_date_offset_units;

    /** The offset from the current time to use when calculating the new task's reminder date. */
    public int reminder_date_offset;

    /** If reminder_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a
     * reminder time. This is the hours component of that time, in the range 0-23.  -1 = unused. */
    public int reminder_time_hours;

    /** If reminder_date_offset_units is OFFSET_DAYS or greater, then this may optionally contain a
     * reminder time. This is the minutes component of that time, in the range 0-59.  -1 = unused. */
    public int reminder_time_minutes;

    /** Whether the task should use nagging reminders. */
    public boolean nag;

    /** The task's repeating pattern. Same value as UTLTask. */
    public int repeat;

    /** If the repeat option is an advanced repeating pattern, this holds a string that describes
     * it.  The format is the same as UTLTask. */
    public String rep_advanced;

    /** The status for the task.  Same as UTLTask.  0=none. */
    public int status;

    /** The expected length, in minutes. */
    public int length;

    /** The task's priority. Same as UTLTask. */
    public int priority;

    /** Whether the task is starred. */
    public boolean star;

    /** A note for the task.  This will be the empty string if unspecified. */
    public String note;

    /** Whether the task should use a location-based reminder. */
    public boolean location_reminder;

    /** Whether the location reminder should be nagging. */
    public boolean location_nag;

    /** This references a linked contact for the task in the database. The empty string = none. */
    public String contact_lookup_key;

    /** A newline-separated list of Toodledo user IDs that the task is to be shared with. ""=none.*/
    public String shared_with;

    /** Flag indicating if the task should be placed on the calendar. */
    public boolean add_to_calendar;

    /** Create a new blank template. */
    public TaskTemplate()
    {
        // Assign the template to the first account by default:
        AccountsDbAdapter adb = new AccountsDbAdapter();
        Cursor c = adb.getAllAccounts();
        if (c.moveToFirst())
        {
            UTLAccount a = adb.getUTLAccount(c);
            account_id = a._id;
        }
        else
            account_id = 0;
        c.close();

        _id = 0;
        title = "";
        completed = false;
        folder_id = 0;
        context_id = 0;
        goal_id = 0;
        location_id = 0;
        tags = new ArrayList<String>();
        parent_id = 0;
        due_modifier = "";
        due_date_offset_units = OFFSET_UNUSED;
        due_date_offset = 0;
        due_time_hours = -1;
        due_time_minutes = -1;
        start_date_offset_units = OFFSET_UNUSED;
        start_date_offset = 0;
        start_time_hours = -1;
        start_time_minutes = -1;
        reminder_date_offset_units = OFFSET_UNUSED;
        reminder_date_offset = 0;
        reminder_time_hours = -1;
        reminder_time_minutes = -1;
        nag = false;
        repeat = 0;
        rep_advanced = "";
        status = 0;
        length = 0;
        priority = 0;
        star = false;
        note = "";
        location_reminder = false;
        location_nag = false;
        contact_lookup_key = "";
        shared_with = "";
    }

    /** Create a TaskTemplate from a Bundle previously created with the toBundle() method. */
    static public TaskTemplate fromBundle(Bundle b)
    {
        TaskTemplate tt = new TaskTemplate();
        if (b.containsKey("_id"))
            tt._id = b.getLong("_id");
        if (b.containsKey("account_id"))
            tt.account_id = b.getLong("account_id");
        if (b.containsKey("title"))
            tt.title = b.getString("title");
        if (b.containsKey("completed"))
            tt.completed = b.getBoolean("completed");
        if (b.containsKey("folder_id"))
            tt.folder_id = b.getLong("folder_id");
        if (b.containsKey("context_id"))
            tt.context_id = b.getLong("context_id");
        if (b.containsKey("goal_id"))
            tt.goal_id = b.getLong("goal_id");
        if (b.containsKey("location_id"))
            tt.location_id = b.getLong("location_id");
        if (b.containsKey("tags"))
        {
            tt.tags = b.getStringArrayList("tags");
            if (tt.tags==null)
                tt.tags = new ArrayList<String>();
        }
        if (b.containsKey("parent_id"))
            tt.parent_id = b.getLong("parent_id");
        if (b.containsKey("due_modifier"))
        {
            tt.due_modifier = b.getString("due_modifier");
            if (tt.due_modifier==null)
                tt.due_modifier = "";
        }
        if (b.containsKey("due_date_offset_units"))
            tt.due_date_offset_units = b.getInt("due_date_offset_units");
        if (b.containsKey("due_date_offset"))
            tt.due_date_offset = b.getInt("due_date_offset");
        if (b.containsKey("due_time_hours"))
            tt.due_time_hours = b.getInt("due_time_hours");
        if (b.containsKey("due_time_minutes"))
            tt.due_time_minutes = b.getInt("due_time_minutes");
        if (b.containsKey("start_date_offset_units"))
            tt.start_date_offset_units = b.getInt("start_date_offset_units");
        if (b.containsKey("start_date_offset"))
            tt.start_date_offset = b.getInt("start_date_offset");
        if (b.containsKey("start_time_hours"))
            tt.start_time_hours = b.getInt("start_time_hours");
        if (b.containsKey("start_time_minutes"))
            tt.start_time_minutes = b.getInt("start_time_minutes");
        if (b.containsKey("reminder_date_offset_units"))
            tt.reminder_date_offset_units = b.getInt("reminder_date_offset_units");
        if (b.containsKey("reminder_date_offset"))
            tt.reminder_date_offset = b.getInt("reminder_date_offset");
        if (b.containsKey("reminder_time_hours"))
            tt.reminder_time_hours = b.getInt("reminder_time_hours");
        if (b.containsKey("reminder_time_minutes"))
            tt.reminder_time_minutes = b.getInt("reminder_time_minutes");
        if (b.containsKey("nag"))
            tt.nag = b.getBoolean("nag");
        if (b.containsKey("repeat"))
            tt.repeat = b.getInt("repeat");
        if (b.containsKey("rep_advanced"))
        {
            tt.rep_advanced = b.getString("rep_advanced");
            if (tt.rep_advanced==null)
                tt.rep_advanced = "";
        }
        if (b.containsKey("status"))
            tt.status = b.getInt("status");
        if (b.containsKey("length"))
            tt.length = b.getInt("length");
        if (b.containsKey("priority"))
            tt.priority = b.getInt("priority");
        if (b.containsKey("star"))
            tt.star = b.getBoolean("star");
        if (b.containsKey("note"))
            tt.note = b.getString("note");
        if (b.containsKey("location_reminder"))
            tt.location_reminder = b.getBoolean("location_reminder");
        if (b.containsKey("location_nag"))
            tt.location_nag = b.getBoolean("location_nag");
        if (b.containsKey("contact_lookup_key"))
        {
            tt.contact_lookup_key = b.getString("contact_lookup_key");
            if (tt.contact_lookup_key==null)
                tt.contact_lookup_key = "";
        }
        if (b.containsKey("shared_with"))
        {
            tt.shared_with = b.getString("shared_with");
            if (tt.shared_with==null)
                tt.shared_with = "";
        }
        if (b.containsKey("add_to_calendar"))
            tt.add_to_calendar = b.getBoolean("add_to_calendar");
        return tt;
    }

    /** Create a bundle that contains all information in the template. This can be used by Tasker. */
    public Bundle toBundle(Context c)
    {
        Bundle b = new Bundle();
        try
        {
            // Put the version code of the app into the Bundle.  This can be useful if future
            // versions of the app change the Bundle format.
            PackageInfo packageInfo = c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            b.putInt("version_code",packageInfo.versionCode);
        }
        catch (Exception e) { }
        b.putString(TaskerReceiver.KEY_BUNDLE_TYPE,TaskerReceiver.BUNDLE_TYPE_TEMPLATE);
        b.putLong("_id",_id);
        b.putLong("account_id",account_id);
        b.putString("title",title);
        b.putBoolean("completed",completed);
        b.putLong("folder_id",folder_id);
        b.putLong("context_id",context_id);
        b.putLong("goal_id",goal_id);
        b.putLong("location_id",location_id);
        b.putStringArrayList("tags",tags);
        b.putLong("parent_id",parent_id);
        b.putString("due_modifier",due_modifier);
        b.putInt("due_date_offset_units",due_date_offset_units);
        b.putInt("due_date_offset",due_date_offset);
        b.putInt("due_time_hours",due_time_hours);
        b.putInt("due_time_minutes",due_time_minutes);
        b.putInt("start_date_offset_units",start_date_offset_units);
        b.putInt("start_date_offset",start_date_offset);
        b.putInt("start_time_hours",start_time_hours);
        b.putInt("start_time_minutes",start_time_minutes);
        b.putInt("reminder_date_offset_units",reminder_date_offset_units);
        b.putInt("reminder_date_offset",reminder_date_offset);
        b.putInt("reminder_time_hours",reminder_time_hours);
        b.putInt("reminder_time_minutes",reminder_time_minutes);
        b.putBoolean("nag",nag);
        b.putInt("repeat",repeat);
        b.putString("rep_advanced",rep_advanced);
        b.putInt("status",status);
        b.putInt("length",length);
        b.putInt("priority",priority);
        b.putBoolean("star",star);
        b.putString("note",note);
        b.putBoolean("location_reminder",location_reminder);
        b.putBoolean("location_nag",location_nag);
        b.putString("contact_lookup_key",contact_lookup_key);
        b.putString("shared_with",shared_with);
        b.putBoolean("add_to_calendar",add_to_calendar);
        return b;
    }

    /** Create a task based on this template.  If the task cannot be created, an
     * IllegalArgumentException is thrown, with a message that contains a message for the user.
     * The task is stored in the database and any applicable reminders are set.
     * @return The instance of UTLTask that was created.
     */
    public UTLTask createTask(Context c) throws IllegalArgumentException
    {
        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);

        // Verify that the template does not have any problems.

        // Verify that a title is present:
        if (title==null || title.length()==0)
        {
            throw new IllegalArgumentException(c.getString(R.string.missing_title));
        }

        // Verify that the title is not too long:
        if (title.length()> Util.MAX_TASK_TITLE_LENGTH)
        {
            throw new IllegalArgumentException(c.getString(R.string.Title_is_too_long));
        }

        // Verify that a start or due date is set if a repeat option is chosen.
        if (prefs.getBoolean(PrefNames.REPEAT_ENABLED,true) && repeat>0 &&
            due_date_offset_units==OFFSET_UNUSED && start_date_offset_units==OFFSET_UNUSED)
        {
            throw new IllegalArgumentException(c.getString(R.string.Repeat_Without_Date));
        }

        // Make sure the account still exists.
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        UTLAccount selectedAccount = accountsDB.getAccount(account_id);
        if (selectedAccount==null)
        {
            throw new IllegalArgumentException(c.getString(R.string.task_account_deleted));
        }

        // If the task is linked to a Google account, it must have a valid folder:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        if (selectedAccount.sync_service==UTLAccount.SYNC_GOOGLE)
        {
            if (folder_id==0)
            {
                throw new IllegalArgumentException(c.getString(R.string.GTasks_Must_Have_Folder));
            }
            Cursor cu = foldersDB.getFolder(folder_id);
            if (!cu.moveToFirst())
            {
                cu.close();
                throw new IllegalArgumentException(c.getString(R.string.task_folder_deleted));
            }
            cu.close();
        }

        // Determine if the "none" priority should be allowed.  It is not allowed for Toodledo
        // accounts.
        if (selectedAccount.sync_service==UTLAccount.SYNC_TOODLEDO && priority==0)
        {
            throw new IllegalArgumentException(c.getString(R.string.Priority_is_required));
        }

        // Make sure the note is not too long:
        if (note.length()>Util.MAX_TASK_NOTE_LENGTH)
        {
            throw new IllegalArgumentException(c.getString(R.string.Note_is_too_long));
        }

        // At this point, all fields have been validated. Create a new instance of UTLTask,
        // set its fields, then add it to the database.
        UTLTask task = new UTLTask();
        task.td_id = -1;
        task.account_id = account_id;
        task.mod_date = System.currentTimeMillis();
        task.title = title.replace("\n"," ");

        // Due Date and Time:
        task.due_date = 0;
        task.uses_due_time = false;
        if (prefs.getBoolean(PrefNames.DUE_DATE_ENABLED,true) && due_date_offset_units!=
            OFFSET_UNUSED)
        {
            task.due_date = getTimeFromRelativeData(due_date_offset_units, due_date_offset,
                due_time_hours, due_time_minutes, c);
            if (prefs.getBoolean(PrefNames.DUE_TIME_ENABLED,true))
            {
                if (due_date_offset_units==OFFSET_MINUTES || due_date_offset_units==
                    OFFSET_HOURS)
                {
                    task.uses_due_time = true;
                }
                if (due_time_hours>=0 && due_time_minutes>=0)
                    task.uses_due_time = true;
            }
        }
        task.due_modifier = due_modifier;

        // Start Date and Time:
        task.start_date = 0;
        task.uses_start_time = false;
        if (prefs.getBoolean(PrefNames.START_DATE_ENABLED,true) && start_date_offset_units!=
            OFFSET_UNUSED)
        {
            task.start_date = getTimeFromRelativeData(start_date_offset_units, start_date_offset,
                start_time_hours, start_time_minutes, c);
            if (prefs.getBoolean(PrefNames.START_TIME_ENABLED,true))
            {
                if (start_date_offset_units==OFFSET_MINUTES || start_date_offset_units==
                    OFFSET_HOURS)
                {
                    task.uses_start_time = true;
                }
                if (start_time_hours>=0 && start_time_minutes>=0)
                    task.uses_start_time = true;
            }
        }

        if (prefs.getBoolean(PrefNames.STATUS_ENABLED,true))
            task.status = status;
        if (prefs.getBoolean(PrefNames.LENGTH_ENABLED,true))
            task.length = length;
        if (prefs.getBoolean(PrefNames.PRIORITY_ENABLED,true) || selectedAccount.sync_service==
            UTLAccount.SYNC_TOODLEDO)
        {
            task.priority = priority;
        }
        if (prefs.getBoolean(PrefNames.STAR_ENABLED,true))
            task.star = star;
        task.note = note;

        // Folder:
        task.folder_id = 0;
        task.prev_folder_id = 0;
        if (prefs.getBoolean(PrefNames.FOLDERS_ENABLED,true) && folder_id>0)
        {
            Cursor cu = foldersDB.getFolder(folder_id);
            if (cu.moveToFirst())
            {
                task.folder_id = folder_id;
                task.prev_folder_id = folder_id;
            }
            cu.close();
        }

        // Context:
        task.context_id = 0;
        ContextsDbAdapter contextsDB = new ContextsDbAdapter();
        if (prefs.getBoolean(PrefNames.CONTEXTS_ENABLED,true) && context_id>0)
        {
            Cursor cu = contextsDB.getContext(context_id);
            if (cu.moveToFirst())
                task.context_id = context_id;
            cu.close();
        }

        // Goal:
        task.goal_id = 0;
        GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        if (prefs.getBoolean(PrefNames.GOALS_ENABLED,true) && goal_id>0)
        {
            Cursor cu = goalsDB.getGoal(goal_id);
            if (cu.moveToFirst())
                task.goal_id = goal_id;
            cu.close();
        }

        // Location:
        task.location_id = 0;
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        if (prefs.getBoolean(PrefNames.LOCATIONS_ENABLED,true) && location_id>0)
        {
            UTLLocation loc = locDB.getLocation(location_id);
            if (loc!=null)
                task.location_id = location_id;
        }
        if (task.location_id>0)
        {
            task.location_reminder = location_reminder;
            task.location_nag = location_nag;
        }
        else
        {
            task.location_reminder = false;
            task.location_nag = false;
        }

        // Reminder Date and Time:
        task.reminder = 0;
        task.nag = false;
        if (prefs.getBoolean(PrefNames.REMINDER_ENABLED,true) && reminder_date_offset_units!=
            OFFSET_UNUSED)
        {
            task.reminder = getTimeFromRelativeData(reminder_date_offset_units, reminder_date_offset,
                reminder_time_hours, reminder_time_minutes, c);
            task.nag = nag;
        }

        // Repeat:
        task.repeat = 0;
        task.rep_advanced = "";
        if (prefs.getBoolean(PrefNames.REPEAT_ENABLED,true) && repeat>0)
        {
            task.repeat = repeat;
            task.rep_advanced = rep_advanced;
        }

        // Completed status and completion date:
        task.completed = completed;
        if (task.completed)
            task.completion_date = System.currentTimeMillis();

        // Linked Contact:
        if (prefs.getBoolean(PrefNames.CONTACTS_ENABLED,true) && contact_lookup_key!=null &&
            contact_lookup_key.length()>0)
        {
            task.contactLookupKey = contact_lookup_key;
        }

        // Collaboration / Sharing:
        task.owner_remote_id = selectedAccount.td_userid;
        if (prefs.getBoolean(PrefNames.COLLABORATORS_ENABLED,true) && selectedAccount.sync_service
            ==UTLAccount.SYNC_TOODLEDO && shared_with!=null && shared_with.length()>0)
        {
            task.is_joint = true;
            task.shared_with = shared_with;
        }

        // When adding a task, it is placed at the bottom of the list when sorting manually:
        task.setSortOrderToBottom();

        // Link the task with a calendar entry if needed:
        task.calEventUri = "";
        if (add_to_calendar && (task.due_date>0 || task.start_date>0) &&
            prefs.getBoolean(PrefNames.CALENDAR_ENABLED,true))
        {
            CalendarInterface ci = new CalendarInterface(c);
            String uri = ci.linkTaskWithCalendar(task);
            if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
            {
                task.calEventUri = uri;
            }
            else
            {
                String errorMsg = uri.substring(CalendarInterface.ERROR_INDICATOR.length());
                throw new IllegalArgumentException(errorMsg);
            }
        }

        // Add a global unique ID to the task:
        task.uuid = UUID.randomUUID().toString();

        // Record features used by the task:
        FeatureUsage featureUsage = new FeatureUsage(c);
        featureUsage.recordForTask(task);

        // Add the task to the database:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        long taskID = tasksDB.addTask(task);
        if (taskID==-1)
        {
            throw new IllegalArgumentException(c.getString(R.string.Cannot_add_task));
        }

        // If the task was added to the calendar, we need to update the calendar event's note
        // to include a link to the newly created task.
        if (add_to_calendar && (task.due_date>0 || task.start_date>0) &&
            prefs.getBoolean(PrefNames.CALENDAR_ENABLED,true))
        {
            CalendarInterface ci = new CalendarInterface(c);
            ci.addTaskLinkToEvent(task);
        }

        // Add the tags to the task in the database:
        if (prefs.getBoolean(PrefNames.TAGS_ENABLED, true) && tags!=null && tags.size()>0)
        {
            TagsDbAdapter tagsDB = new TagsDbAdapter();
            tagsDB.linkTags(taskID,tags.toArray(new String[tags.size()]));

            // Record usage of the tags feature:
            if (!task.completed)
                featureUsage.record(FeatureUsage.TAGS);
        }

        if (task.completed)
        {
            // The task was created in the completed state. Some additional database updates
            // may be needed which are handled by this function:
            Util.markTaskComplete(taskID);
        }

        // If the current time zone is different than the home time zone, the
        // reminder time needs to be offset when comparing it to the current time.
        TimeZone currentTimeZone = TimeZone.getDefault();
        TimeZone defaultTimeZone = TimeZone.getTimeZone(prefs.getString(PrefNames.HOME_TIME_ZONE,
            ""));
        long reminderTime = task.reminder;
        if (!currentTimeZone.equals(defaultTimeZone))
        {
            long difference = currentTimeZone.getOffset(System.currentTimeMillis()) -
                defaultTimeZone.getOffset(System.currentTimeMillis());
            reminderTime = task.reminder - difference;
        }

        // If a reminder was set up, then schedule it:
        if (reminderTime>System.currentTimeMillis() && !task.completed)
        {
            Util.scheduleReminderNotification(task);
        }

        // Perform an instant upload of the new task if the feature is enabled:
        if (prefs.getBoolean(PrefNames.INSTANT_UPLOAD,true))
            Util.instantTaskUpload(c, task);

        // If any tags were used, make sure they are on the recently used tags list:
        if (prefs.getBoolean(PrefNames.TAGS_ENABLED, true) && tags!=null && tags.size()>0)
        {
            CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
            currentTags.addToRecent(tags.toArray(new String[tags.size()]));
        }

        // Update any widgets that are on display:
        Util.updateWidgets();

        return task;
    }

    /** Given some time values relative to the current time, generate a timestamp. */
    public long getTimeFromRelativeData(int dateOffsetUnits, int dateOffset, int timeHours,
        int timeMinutes, Context c)
    {
        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone(prefs.getString(
            PrefNames.HOME_TIME_ZONE,"")));
        cal.setTimeInMillis(System.currentTimeMillis());
        switch (dateOffsetUnits)
        {
            case OFFSET_MINUTES:
                cal.add(Calendar.MINUTE,dateOffset);
                break;

            case OFFSET_HOURS:
                cal.add(Calendar.HOUR_OF_DAY,dateOffset);
                break;

            case OFFSET_DAYS:
                cal.add(Calendar.DATE,dateOffset);
                break;

            case OFFSET_WEEKS:
                cal.add(Calendar.WEEK_OF_YEAR,dateOffset);
                break;

            case OFFSET_MONTHS:
                cal.add(Calendar.MONTH,dateOffset);
                break;

            case OFFSET_YEARS:
                cal.add(Calendar.YEAR,dateOffset);
                break;
        }

        if (dateOffsetUnits==OFFSET_DAYS || dateOffsetUnits==OFFSET_WEEKS || dateOffsetUnits==
            OFFSET_MONTHS || dateOffsetUnits==OFFSET_YEARS)
        {
            // Check to see if the time needs set in addition to the date:
            if (timeHours>=0 && timeMinutes>=0)
            {
                cal.set(Calendar.HOUR_OF_DAY,timeHours);
                cal.set(Calendar.MINUTE,timeMinutes);
            }
        }

        return cal.getTimeInMillis();
    }
}
