package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.content.Context;

import com.google.android.gms.ads.formats.UnifiedNativeAd;

// This class holds task data for display in the task list view.

public class UTLTaskDisplay 
{
    // The task object being displayed:
    public UTLTask task;
    
    // Other attributes referenced by name instead of ID:
    public String firstTagName;
    public String accountName;
    public String folderName;
    public String contextName;
    public String goalName;
    public String locationName;
    public long numTags;
    public int level; // 0 = top level, 1 = first-level child, etc.
    public String header;
    public int headerIcon;  // Resource ID of header icon.  0 = not used.
    public boolean showHeader;  // Boolean
    
    // Collaboration fields:
    public String ownerName;
    public String sharedWith;  // Comma separated instead of newline separated.  Names instead of IDs.
    public String assignorName;  // Name corresponding to added_by field.
    
    // A calendar object, to use when generating headers:
    static private GregorianCalendar _cal = null;

    /** The index of this task in the list. */
    public int index;

    /** Flag indicating if this task should have a native ad displayed below it. */
    public boolean hasNativeAd;

	/** The native ad to show. This is null if no ads are available or an ad has not been fetched
	 * yet. */
	public UnifiedNativeAd nativeAd;

	public UTLTaskDisplay()
    {
        task = new UTLTask();
        firstTagName = "";
        accountName = "";
        folderName = "";
        contextName = "";
        goalName = "";
        locationName = "";
        numTags = 0;
        level = 0;
        ownerName = "";
        sharedWith = "";
        assignorName = "";
        header = "";
        headerIcon = 0;
        showHeader = false;
        index = 0;
        hasNativeAd = false;
        nativeAd = null;
        
        if (_cal==null)
        {
        	_cal = new GregorianCalendar(TimeZone.getTimeZone(Util.settings.getString(PrefNames.
        		HOME_TIME_ZONE, "")));
        }
    }
    
    // This is defined solely because the Android library may call it.
    @Override
    public String toString()
    {
        return("");
    }
    
    // Set the header.  The input is the a string representing the 1st sort order field for the view.
    // Valid values can be found in SortOrder.java.  (Look at the generation of the _dbFields array.)
    public void setHeader(String field, Context con)
    {
    	// Default is an unsupported field:
    	header = "";
		headerIcon = 0;
		
    	if (field.equals("account"))
    	{
    		header = accountName;
    		headerIcon = 0;
    	}
    	else if (field.equals("tasks.timer"))
    	{
    		// Not supported:
    		header = "";
    		headerIcon = 0;
    	}
    	else if (field.equals("assignor_name"))
    	{
    		header = con.getString(R.string.Added_By_)+" ";
    		if (assignorName.length()>0)
    			header += assignorName;
    		else
    			header += con.getString(R.string.None);
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_delegated);
    	}
    	else if (field.equals("tasks.completed"))
    	{
    		if (task.completed)
    		{
    			header = con.getString(R.string.Completed);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_recently_completed);
    		}
    		else
    		{
    			header = con.getString(R.string.Incomplete);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_all_tasks);
    		}
    	}
    	else if (field.equals("tasks.completion_date"))
    	{
    		if (task.completed)
    		{
    			header = con.getString(R.string.Completed)+" "+getDateHeader(task.completion_date,con);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_recently_completed);
    		}
    		else
    		{
    			header = con.getString(R.string.Incomplete);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_all_tasks);
    		}
    	}
    	else if (field.equals("context"))
    	{
    		header = contextName;
    		if (header.length()==0)
    			header = con.getString(R.string.No_Context);
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_context);
    	}
    	else if (field.equals("tasks.due_date"))
    	{
    		if (task.due_date>0)
    		{
    			header = con.getString(R.string.Due)+" "+getDateHeader(task.due_date,con);
    			if ((task.due_date-System.currentTimeMillis())<=Util.ONE_DAY_MS)
    				headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_overdue);
    			else
    				headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_due_today_tomorrow);
    		}
    		else
    		{
    			header = con.getString(R.string.No_Due_Date);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_due_today_tomorrow);
    		}
    	}
    	else if (field.equals("tasks.length"))
    	{
    		// Not supported:
    		header = "";
    		headerIcon = 0;
    	}
    	else if (field.equals("folder"))
    	{
    		header = folderName;
    		if (header.length()==0)
    			header = con.getString(R.string.No_Folder);
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_folder);
    	}
    	else if (field.equals("goal"))
    	{
    		header = goalName;
    		if (header.length()==0)
    			header = con.getString(R.string.No_Goal);
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_goal);
    	}
    	else if (field.equals("tasks.position"))
    	{
    		// Not supported:
    		header = "";
    		headerIcon = 0;
    	}
    	else if (field.equals("importance"))
    	{
    		header = con.getString(R.string.Importance_)+" "+task.importance;
    		headerIcon = 0;
    	}
    	else if (field.equals("tasks.is_joint"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_delegated);
    		if (task.is_joint)
    			header = con.getString(R.string.Shared);
    		else
    			header = con.getString(R.string.Not_Shared);
    	}
    	else if (field.equals("location"))
    	{
    		header = locationName;
    		if (header.length()==0)
    			header = con.getString(R.string.No_Location);
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_location);
    	}
    	else if (field.equals("tasks.mod_date"))
    	{
    		header = con.getString(R.string.Modified)+" "+getDateHeader(task.mod_date,con);
    		headerIcon = 0;
    	}
    	else if (field.equals("tasks.note"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_notes);
    		if (task.note.length()>0)
    			header = task.note.substring(0, 1).toUpperCase();
    		else
    			header = Util.getString(R.string.None);
    	}
    	else if (field.equals("owner_name"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_delegated);
    		if (ownerName.length()>0)
    			header = con.getString(R.string.Owner_)+" "+ownerName;
    		else
    			header = con.getString(R.string.Owner_)+" "+con.getString(R.string.None);
    	}
    	else if (field.equals("tasks.priority"))
    	{
    		if (task.priority>=4)
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_hotlist);
    		else
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_all_tasks);
    		String[] priorityArray = con.getResources().getStringArray(R.array.priorities);
    		header = priorityArray[task.priority];
    		if (task.priority>0)
    			header += " "+con.getString(R.string.Priority);
    	}
    	else if (field.equals("tasks.reminder"))
    	{
    		if (task.reminder>0)
    		{
    			header = con.getString(R.string.Reminder)+" "+getDateHeader(task.reminder,con);
    			if ((task.reminder-System.currentTimeMillis())<=Util.ONE_DAY_MS)
    				headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_overdue);
    			else
    				headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_due_today_tomorrow);
    		}
    		else
    		{
    			header = con.getString(R.string.No_Reminder);
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_due_today_tomorrow);
    		}
    	}
    	else if (field.equals("tasks.star"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_starred);
    		if (task.star)
    			header = con.getString(R.string.Starred);
    		else
    			header = con.getString(R.string.Not_Starred);
    	}
    	else if (field.equals("tasks.start_date"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_due_today_tomorrow);
    		if (task.start_date>0)
    		{
    			header = con.getString(R.string.Starts)+" "+getDateHeader(task.start_date,con);
    		}
    		else
    		{
    			header = con.getString(R.string.No_Start_Date);
    		}
    	}
    	else if (field.equals("tasks.status"))
    	{
    		String[] statusArray = con.getResources().getStringArray(R.array.statuses);
    		header = statusArray[task.status];
    		switch (task.status)
    		{
    		case 0:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_none);
    			break;
    		case 1:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_next_action);
    			break;
    		case 2:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_active);
    			break;
    		case 3:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_planning);
    			break;
    		case 4:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_delegated);
    			break;
    		case 5:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_waiting);
    			break;
    		case 6:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_hold);
    			break;
    		case 7:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_postponed);
    			break;
    		case 8:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_someday);
    			break;
    		case 9:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_canceled);
    			break;
    		case 10:
    			headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_status_reference);
    			break;
    		}
    	}
    	else if (field.equals("tag_name"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_tag);
    		if (numTags==0)
    			header = Util.getString(R.string.None);
    		else
    			header = firstTagName;
    	}
    	else if (field.equals("tasks.title"))
    	{
    		headerIcon = Util.resourceIdFromAttr(con, R.attr.nav_all_tasks);
    		if (task.title.length()>0)
    			header = task.title.substring(0, 1).toUpperCase();
    		else
    			header = Util.getString(R.string.None);
    	}
    }
    
    // Get a header string representing the date:
    private String getDateHeader(long timestamp, Context con)
    {
    	long systemMidnight = Util.getMidnight(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),
            null));
    	long tsMidnight = Util.getMidnight(timestamp);
    	float daysDiff = (float)(tsMidnight-systemMidnight)/Util.ONE_DAY_MS;
    	if (daysDiff<-30.0)
    		return con.getString(R.string.Over_30_Days_Ago);
    	else if (daysDiff<-7.0)
    		return con.getString(R.string.Within_Past_Month);
    	else if (daysDiff<-1)
    		return con.getString(R.string.Within_1_Week);
    	else if (daysDiff<0)
    		return con.getString(R.string.Yesterday);
    	else if (daysDiff==0)
    		return con.getString(R.string.Today2);
    	
    	// Past dates and today have been covered.  Now look into the future:
    	if (daysDiff>30)
    	{
    		// Return a month and year:
    		_cal.setTimeInMillis(tsMidnight);
    		int month = _cal.get(Calendar.MONTH)+1;
    		return month+"/"+_cal.get(Calendar.YEAR);
    	}
    	else if (daysDiff>7)
    		return con.getString(R.string.Within_a_Month);
    	else if (daysDiff>1)
    		return con.getString(R.string.Within_7_Days);
    	else
    		return con.getString(R.string.Tomorrow);
    }
}
