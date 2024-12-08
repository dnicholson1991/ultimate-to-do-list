package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.text.format.Time;
import android.util.Log;

// This class provides methods to add and remove tasks from the device's calendar.
// All code that depends on the device or OS version must be here.

public class CalendarInterface
{
	// The default event length, to use in the absence of full start/due time information:
	private static final long DEFAULT_EVENT_LENGTH = 30*60*1000;  // ms
	
	// This string is prepended to any error strings.  It is used to distinguish between
	// an error and success:
	public static final String ERROR_INDICATOR = "error:";
	
	// The context in use:
	private Context _c;
	
	// Current SDK version and device model.  Used for tweaking the code as needed.
	private int _sdkVersion;
	private String _deviceModel;

	// A reference to the settings/preferences.  We can't use the reference in Util
    // due to separate threads accessing this.
    private SharedPreferences _settings;
    
	// Constructor:
	public CalendarInterface(Context c)
	{
		_c = c;
		_sdkVersion = android.os.Build.VERSION.SDK_INT;
		_deviceModel = android.os.Build.MODEL;
		_settings = _c.getSharedPreferences("UTL_Prefs",0);
	}
	
	// Get a list of available calendars. Returns a zero element array if no calendars
	// exist.
	@SuppressLint("NewApi")
	public ArrayList<CalendarInfo> getAvailableCalendars()
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return new ArrayList<CalendarInfo>();
		
		ContentResolver cr = _c.getContentResolver();
		Cursor c;
		ArrayList<CalendarInfo> result = new ArrayList<CalendarInfo>();
		if (_sdkVersion>=14)
		{
			// Ice Cream Sandwich.  Calendars are officially supported.
			c = cr.query(Calendars.CONTENT_URI, new String[] { Calendars._ID, 
				Calendars.CALENDAR_DISPLAY_NAME }, null, null, Calendars.
				CALENDAR_DISPLAY_NAME);
			while (c!=null && c.moveToNext())
			{
				CalendarInfo ci = new CalendarInfo(c.getLong(0),c.getString(1),"");
				result.add(ci);
			}
			if (c!=null) c.close();
			Util.log("# Calendars found using ICS URI "+Calendars.CONTENT_URI+": "+
				result.size());
		}
		else
		{
			// Pre-ICS.  Begin with the regular calendars:
			String uriBase = "content://calendar";
			if (_sdkVersion>=8)
				uriBase = "content://com.android.calendar";
			try
			{
				c = cr.query(Uri.parse(uriBase+"/calendars"), new String[] {"_id", 
					"displayName", "name"}, null, null, "displayName");
				while (c!=null && c.moveToNext())
				{
					CalendarInfo ci;
					if (c.getString(1)==null || c.getString(1).length()==0)
						ci = new CalendarInfo(c.getLong(0),c.getString(2),uriBase);
					else
						ci = new CalendarInfo(c.getLong(0),c.getString(1),uriBase);
					result.add(ci);
				}
				if (c!=null) c.close();
			}
			catch (IllegalArgumentException e)
			{
				Util.log("Got IllegalArgumentException when trying to query calendar "+
					"provider ("+uriBase+"). "+e.getMessage());
			}
			Util.log("# Calendars found using URI "+uriBase+": "+result.size());
			
			// Now, try the corporate calendars:
			try
			{
				c = cr.query(Uri.parse("content://calendarEx/calendars"), new String[] {"_id",
					"displayName", "name"}, null, null, "displayName");
				while (c!=null && c.moveToNext())
				{
					CalendarInfo ci;
					if (c.getString(1)==null || c.getString(1).length()==0)
						ci = new CalendarInfo(c.getLong(0),c.getString(2),"content://calendarEx");
					else
						ci = new CalendarInfo(c.getLong(0),c.getString(1),"content://calendarEx");
					result.add(ci);
				}
				if (c!=null) c.close();
			}
			catch (IllegalArgumentException e)
			{
				Util.log("Got IllegalArgumentException when trying to query corporate "+
					"calendar provider. "+e.getMessage());
			}
			Util.log("Total calendars found after looking at corporate URI: "+result.size());
		}
		return result;
	}
		
	// Link a task to the calendar, removing the old calendar entry if it exists.
	// The calendar to link to is given in the user's preferences.
	// On success, it returns the calendar event URI (As a string). On failure, it 
	// returns a string beginning with the error indicator defined above:
	@SuppressLint("NewApi")
	public String linkTaskWithCalendar(UTLTask t)
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return ERROR_INDICATOR+Util.getString(R.string.Calendar_Integration_Disabled);
		
		ContentResolver cr = _c.getContentResolver();
		Cursor c;
		
		// Make sure the user has specified a calendar and it still exists:
		long calID = _settings.getLong(PrefNames.LINKED_CALENDAR_ID, -1);
		if (calID==-1)
		{
			return ERROR_INDICATOR+Util.getString(R.string.No_Calendar_Specified);
		}
		CalendarInfo ci = this.getCalendarInfo(calID);
		if (ci==null)
		{
			return ERROR_INDICATOR+Util.getString(R.string.Calendar_Doesnt_Exist);
		}
		
		if (t.calEventUri!=null && t.calEventUri.length()>0)
		{
			// Check to see if the calendar event already exists:
			if (_sdkVersion>=14)
			{
				c = cr.query(Uri.parse(t.calEventUri), new String[] {CalendarContract.Events.TITLE}, 
					null, null, null);
			}
			else
			{
				c = cr.query(Uri.parse(t.calEventUri), new String[] {"title"}, 
					null, null, null);
			}
			if (c!=null && c.getCount()>0)
			{
				// Found a matching calendar entry.  Delete it.
				c.close();
				int result = cr.delete(Uri.parse(t.calEventUri), null, null);
				Util.log("linkTaskWithCalendar(): Attempted to delete event "+t.calEventUri+
					", result was "+result+"; Task Name: "+t.title);
			}
		}
		
		// Make sure the task has a time that can be entered:
		if (t.start_date==0 && t.due_date==0)
		{
			return ERROR_INDICATOR+Util.getString(R.string.No_Start_Or_Due_Date);
		}
		
		// Pick a start and end time based on the task's start and due date fields:
		long startTime;
		long endTime;
		boolean isAllDay = false;
		String appTimeZone = _settings.getString("home_time_zone", "America/Los_Angeles");
		if (t.start_date>0 && t.due_date>0)
		{
			if (!t.uses_due_time && !t.uses_start_time)
			{
				// All day event, possibly spanning multiple days:
				isAllDay = true;
				startTime = getMidnightGMT(t.start_date, appTimeZone);
				endTime = Util.addDays(getMidnightGMT(t.due_date,appTimeZone),1,
					Time.TIMEZONE_UTC);
			}
			else if (t.uses_due_time && t.uses_start_time)
			{
				// Concrete start and end times:
				isAllDay = false;
				startTime = shiftTimeToCalendarZone(t.start_date);
				endTime = shiftTimeToCalendarZone(t.due_date);
				if (endTime<startTime)
				{
					// The user chose a start time later than the end time. Flip them.
					startTime = endTime;
					endTime = shiftTimeToCalendarZone(t.start_date);
				}
			}
			else if (t.uses_start_time)
			{
				// Has start time, but no due time:
				isAllDay = false;
				startTime = shiftTimeToCalendarZone(t.start_date);
				endTime = startTime + DEFAULT_EVENT_LENGTH;
			}
			else
			{
				// Has a due time, but no start time.
				isAllDay = false;
				endTime = shiftTimeToCalendarZone(t.due_date);
				startTime = endTime - DEFAULT_EVENT_LENGTH;
			}
		}
		else if (t.start_date>0)
		{
			// Start date only:
			if (t.uses_start_time)
			{
				isAllDay = false;
				startTime = shiftTimeToCalendarZone(t.start_date);
				endTime = startTime+DEFAULT_EVENT_LENGTH;
			}
			else
			{
				// All day event:
				isAllDay = true;
				startTime = getMidnightGMT(t.start_date,appTimeZone);
				endTime = Util.addDays(startTime,1,Time.TIMEZONE_UTC);
			}
		}
		else
		{
			// Due date only:
			if (t.uses_due_time)
			{
				isAllDay = false;
				endTime = shiftTimeToCalendarZone(t.due_date);
				startTime = endTime - DEFAULT_EVENT_LENGTH;
			}
			else
			{
				isAllDay = true;
				startTime = getMidnightGMT(t.due_date,appTimeZone);
				endTime = Util.addDays(startTime,1,Time.TIMEZONE_UTC);
			}
		}
		
		// The note will have a link prepended to open the task in UTL (if the task's ID
		// has been determined):
		String noteLink = "";
		if (t._id>0)
		{
			noteLink = Util.getString(R.string.Tap_to_open_in_UTL)+
				"\nhttps://edit.todolist.co/"+t._id+"\n\n";
		}
		
		// Add the event to the calendar:
		Uri insertedUri;
		if (_sdkVersion>=14)
		{
			ContentValues event = new ContentValues();
			event.put(Events.CALENDAR_ID,calID);
			event.put(Events.TITLE, t.title);
			event.put(Events.DESCRIPTION, noteLink+t.note);
			event.put(Events.DTSTART, startTime);
			event.put(Events.ALL_DAY, isAllDay ? 1 : 0);
			event.put(Events.STATUS, Events.STATUS_CONFIRMED);
			event.put(Events.HAS_ALARM, 0);
	        if (!isAllDay)
	        {
	        	event.put(Events.EVENT_TIMEZONE, appTimeZone);
	        }
	        else
	        {
	        	event.put(Events.EVENT_TIMEZONE, Time.TIMEZONE_UTC);
	        }
			if (t.repeat>0)
			{
				String rrule = getRecurrenceString(t);
				if (rrule!=null && rrule.length()>0)
					event.put(Events.RRULE, rrule);
				event.put(Events.DURATION, getDurationString(startTime, endTime, isAllDay));
			}
			else
				event.put(Events.DTEND, endTime);
			insertedUri = cr.insert(Events.CONTENT_URI,event);
		}
		else
		{
			ContentValues event = new ContentValues();
	        event.put("calendar_id", calID);
	        event.put("title", t.title);
	        event.put("description", noteLink+t.note);   
	        event.put("allDay", isAllDay ? 1 : 0); // 0 for false, 1 for true
	        event.put("eventStatus", 1);
	        event.put("visibility", 0);
	        event.put("transparency", 0);
	        event.put("hasAlarm", 0); // 0 for false, 1 for true
	        event.put("dtstart", startTime);
	        if (!isAllDay)
	        {
	        	event.put("eventTimezone", appTimeZone);
	        }
	        else
	        {
	        	event.put("eventTimezone", Time.TIMEZONE_UTC);
	        }
	        if (t.repeat>0)
			{
				String rrule = getRecurrenceString(t);
				if (rrule!=null && rrule.length()>0)
					event.put("rrule", rrule);
				event.put("duration", getDurationString(startTime, endTime, isAllDay));
			}
	        else
	        	event.put("dtend", endTime);
	        insertedUri = cr.insert(Uri.parse(ci.uriBase+"/events"), event);
		}
		
		if (insertedUri==null)
		{
			return ERROR_INDICATOR+Util.getString(R.string.Calendar_Event_Rejected);
		}
		
		Util.log("linkTaskWithCalendar(): Successfully linked task '"+t.title+"' to "+
			"calendar. Event URI is "+insertedUri.toString());
		
		return insertedUri.toString();
	}
	
	// Update an existing calendar event so that a link to the task in UTL is placed in
	// the event's note.
	@SuppressLint("InlinedApi")
	public void addTaskLinkToEvent(UTLTask t)
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return;
		
		if (t._id==0 || t.calEventUri==null | t.calEventUri.length()==0)
			return;
		
		ContentResolver cr = _c.getContentResolver();
		
		String noteLink = "";
		noteLink = Util.getString(R.string.Tap_to_open_in_UTL)+
			"\nhttps://edit.todolist.co/"+t._id+"\n\n";
		
		int result;
		if (_sdkVersion>=14)
		{
			ContentValues event = new ContentValues();
			event.put(Events.DESCRIPTION, noteLink+t.note);
			result = cr.update(Uri.parse(t.calEventUri), event, null, null);
		}
		else
		{
			ContentValues event = new ContentValues();
			event.put("description", noteLink+t.note);
			result = cr.update(Uri.parse(t.calEventUri), event, null, null);
		}
		
		Util.log("addTaskLinkToEvent(): Tried to add link for task '"+t.title+"'. Result "+
			"was "+result);
	}
	
	// Remove a calendar entry that is linked to a task.
	// The operation will fail without an error if the calendar or event has already
	// been deleted.
	public void unlinkTaskFromCalendar(UTLTask t)
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return;
		
		ContentResolver cr = _c.getContentResolver();
		Cursor c;
		
		if (t.calEventUri!=null && t.calEventUri.length()>0)
		{
			// Check to see if the calendar event already exists:
			if (_sdkVersion>=14)
			{
				c = cr.query(Uri.parse(t.calEventUri), new String[] {CalendarContract.Events.TITLE}, 
					null, null, null);
			}
			else
			{
				c = cr.query(Uri.parse(t.calEventUri), new String[] {"title"}, 
					null, null, null);
			}
			if (c!=null && c.getCount()>0)
			{
				// Found a matching calendar entry.  Delete it.
				c.close();
				int result = cr.delete(Uri.parse(t.calEventUri), null, null);
				Util.log("unlinkTaskFromCalendar(): Attempted to deleted event "+
					t.calEventUri+", result was "+result+"; Task Title: "+t.title);
			}
			else
			{
				Util.log("unlinkTaskFromCalendar(): Attempted to delete event for task '"+
					t.title+"', but the calendar event could not be found.");
			}
		}
	}
	
	// Move all tasks to the user's currently selected calendar:
	// Returns an empty String on success, or an error message on failure.
	public String moveTasksToCurrentCalendar()
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return Util.getString(R.string.Calendar_Integration_Disabled);
		
		Util.log("Moving tasks to new calendar.");
		
		TasksDbAdapter db = new TasksDbAdapter();
		Cursor c = db.queryTasks("cal_event_uri!=''", null);
		c.moveToPosition(-1);
		while (c.moveToNext())
		{
			UTLTask t = db.getUTLTask(c);
			if (t!=null)
			{
				String eventUri = linkTaskWithCalendar(db.getUTLTask(c));
				if (!eventUri.startsWith(this.ERROR_INDICATOR))
				{
					t.calEventUri = eventUri;
					db.modifyTask(t);
				}
			}
		}
		c.close();
		
		return "";
	}
	
	// Given a calendar ID, get the name of the calendar.  Returns "" if the calendar
	// is not found.
	public String getCalendarName(long calID)
	{
		ArrayList<CalendarInfo> calInfo = getAvailableCalendars();
		Iterator<CalendarInfo> it = calInfo.iterator();
		while (it.hasNext())
		{
			CalendarInfo ci = it.next();
			if (ci.id == calID)
			{
				return ci.name;
			}
		}
		return "";
	}
	
	// Given a calendar ID, get a CalendarInfo object. Returns null if the calendar 
	// does not exist:
	public CalendarInfo getCalendarInfo(long calID)
	{
		ArrayList<CalendarInfo> calInfo = getAvailableCalendars();
		Iterator<CalendarInfo> it = calInfo.iterator();
		while (it.hasNext())
		{
			CalendarInfo ci = it.next();
			if (ci.id == calID)
			{
				return ci;
			}
		}
		return null;
	}
	
	// Given a task, generate a recurrence rule string that can be inserted into the 
	// calendar.  Returns "" if the task does not recur.
	public String getRecurrenceString(UTLTask t)
	{
		int repeat = t.repeat;
		if (repeat>100) repeat-=100;
		
		if (repeat==9)
		{
			// With Parent - Get the parent's repeating pattern:
			if (t.parent_id>0)
			{
				TasksDbAdapter db = new TasksDbAdapter();
				UTLTask parent = db.getTask(t.parent_id);
				if (parent!=null)
					repeat = parent.repeat;
				else
					return "";
			}
			else
				return "";
		}
		
		switch (repeat)
		{
		case 0:
			// No repeat:
			return "";
		case 1:
			// Weekly:
			return "FREQ=WEEKLY";
		case 2:
			// Monthly:
			return "FREQ=MONTHLY";
		case 3:
			// Yearly:
			return "FREQ=YEARLY";
		case 4:
			// Daily:
			return "FREQ=DAILY;INTERVAL=1";
		case 5:
			// Biweekly:
			return "FREQ=WEEKLY;INTERVAL=2";
		case 6:
			// Bimonthly:
			return "FREQ=MONTHLY;INTERVAL=2";
		case 7:
			// Semiannually:
			return "FREQ=MONTHLY;INTERVAL=6";
		case 8:
			// Quarterly:
			return "FREQ=MONTHLY;INTERVAL=3";
		case 50:
			// Advanced options:
			return advancedRepeatToRecurrenceRule(t.rep_advanced);
		}
		return "";
	}
	
	// Convert an advanced repeat string to a recurrence rule:
	private String advancedRepeatToRecurrenceRule(String input)
	{
		AdvancedRepeat ar = new AdvancedRepeat();
		if (!ar.initFromString(input))
		{
			// Badly formatted advanced repeat string:
			return "";
		}
		
		if (ar.formatNum==1)
		{
			// Every 1 day, every 2 weeks, etc.
			if (ar.unit.equals("day"))
				return "FREQ=DAILY;INTERVAL="+ar.increment;
			else if (ar.unit.equals("month"))
				return "FREQ=MONTHLY;INTERVAL="+ar.increment;
			else if (ar.unit.equals("week"))
				return "FREQ=WEEKLY;INTERVAL="+ar.increment;
			else if (ar.unit.equals("year"))
				return "FREQ=YEARLY;INTERVAL="+ar.increment;
			else
				return "";
		}
		else if (ar.formatNum==2)
		{
			// Every 2nd tuesday, every 3rd friday, the last wednesday, etc.
			if (ar.monthLocation.equals("1") || ar.monthLocation.equals("2") || 
				ar.monthLocation.equals("3") || ar.monthLocation.equals("4") || 
				ar.monthLocation.equals("5"))
			{
				return "FREQ=MONTHLY;BYDAY="+ar.monthLocation+ar.dayOfWeek.substring(0,2).
					toUpperCase();
			}
			else if (ar.monthLocation.equals("last"))
			{
				return "FREQ=MONTHLY;BYDAY=-1"+ar.dayOfWeek.substring(0,2).toUpperCase();
			}
			else
				return "";
		}
		else if (ar.formatNum==3)
		{
			// Every monday and tuesday, every saturday and sunday, every weekday, etc.
			String result = "FREQ=WEEKLY;WKST=SU;BYDAY=";
			for (int i=0; i<ar.daysOfWeek.length; i++)
			{
				if (i>0) result += ",";
				if (ar.daysOfWeek[i].equals("weekend"))
					result += "SU,SA";
				else if (ar.daysOfWeek[i].equals("weekday"))
					result += "MO,TU,WE,TH,FR";
				else
					result += ar.daysOfWeek[i].substring(0,2).toUpperCase();
			}
			return result;
		}
		else
			return "";
	}
	
	// Get a duration string, given start and end times in ms:
	private String getDurationString(long startTime, long endTime, boolean isAllDay)
	{
		if (!isAllDay)
		{
			long diffSecs = (endTime-startTime)/1000;
			long hours = diffSecs/(60*60);
			long minutes = (diffSecs % (60*60))/60;
			long seconds = (diffSecs % (60*60))%60;
			String result = "";
			if (seconds>0)
				result = seconds + "S";
			if (minutes>0 || hours>0)
				result = minutes+"M"+result;
			if (hours>0)
				result = hours+"H"+result;
			if (result.length()==0)
				result = "0S";
			return "PT"+result;
		}
		else
		{
			long diffSecs = (endTime-startTime)/1000;
			long days = diffSecs/(24*60*60);
			long remainderSecs = diffSecs % (24*60*60);
			if (remainderSecs>(12*60*60))
				days++;
			if (days==0)
				days = 1;
			return "P"+days+"D";
		}
	}
	
	// Given a time in millis, and the associated time zone, move the time to midnight
	// GMT:
	private long getMidnightGMT(long time, String timeZoneID)
	{
		Util.log("getMidnightGMT(): Input Time Zone: "+timeZoneID);
		Util.log("getMidnightGMT(): Input Time: "+time+" / "+Util.getDateTimeString(time)+
			" (in app's time zone)");
		Util.log("getMidnightGMT(): Input Time in Millis: "+time);
		
		// Time shift the input time:
		TimeZone utcZone = TimeZone.getTimeZone(Time.TIMEZONE_UTC);
		TimeZone defaultZone = TimeZone.getTimeZone(timeZoneID);
		long zoneOffset = utcZone.getOffset(time) - defaultZone.getOffset(time);
		long adjustedTime = time-zoneOffset;
		
		// Strip off any hour and minute components to get midnight:
		GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
        c.setTimeInMillis(adjustedTime);
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.AM_PM,Calendar.AM);
        
        long result = c.getTimeInMillis();
        Util.log("getMidnightGMT(): Zone Offset: "+zoneOffset);
        Util.log("getMidnightGMT(): Time Returned: "+result);
        
        return result;
	}
	
	// Time shift date/times from the time zone used by the app to the time zone used by the
	// calendar:
	private long shiftTimeToCalendarZone(long time)
	{
		SharedPreferences settings = _c.getSharedPreferences("UTL_Prefs", 0);
		TimeZone appTimeZone = TimeZone.getTimeZone(settings.getString("home_time_zone", 
			"America/Los_Angeles"));
		TimeZone sysTimeZone = TimeZone.getDefault();
		Util.log("shiftTimeToCalendarZone(): input time (app's zone): "+time+" / " + Util.
			getDateTimeString(time));
		Util.log("shiftTimeToCalendarZone(): appTimeZone: "+settings.getString(
			"home_time_zone","America/Los_Angeles"));
		Util.log("shiftTimeToCalendarZone(): sysTimeZone: "+TimeZone.getDefault().getID());
		long zoneOffset = sysTimeZone.getOffset(time) - appTimeZone.getOffset(time);
		Util.log("shiftTimeToCalendarZone(): zoneOffset: "+zoneOffset);
		return time-zoneOffset;
	}
	
	// Delete all calendar events for an account:
	public void deleteAllCalendarEvents(UTLAccount a)
	{
		if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
			return;
		
		TasksDbAdapter db = new TasksDbAdapter();
		Cursor c = db.queryTasks("cal_event_uri!='' and account_id="+a._id, null);
		c.moveToPosition(-1);
		while (c.moveToNext())
		{
			unlinkTaskFromCalendar(db.getUTLTask(c));
		}
		c.close();
	}
	
	/** Check 2 tasks to determine if the matching calendar entry will be the same. */
	public boolean hasIdenticalCalendarEntries(UTLTask oldTask, UTLTask newTask)
	{
		// If the old task did not have a calendar link, there can be no match.
		if (oldTask.calEventUri==null || oldTask.calEventUri.length()==0)
			return false;
		
		// Perform some date checking:
		if (oldTask.start_date!=newTask.start_date || oldTask.due_date!=newTask.due_date)
			return false;
		
		// Repeating from completion or due date doesn't matter.
		int oldRepeat = oldTask.repeat;
		int newRepeat = newTask.repeat;
		if (oldRepeat>100) oldRepeat-=100;
		if (newRepeat>100) newRepeat-=100;
		
		// Check the basic repeat pattern:
		if (oldRepeat!=newRepeat)
			return false;
		
    	// Title and Note:
		if (!oldTask.title.equals(newTask.title) || !oldTask.note.equals(newTask.note))
			return false;
		
		// Advanced Repeat:
		if (newRepeat==50)
		{
			if (!oldTask.rep_advanced.equals(newTask.rep_advanced))
				return false;
		}
		
		return true;
	}
}

