package com.customsolutions.android.utl;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

@SuppressLint("NewApi")
public class ScrollableWidgetServiceCompact extends RemoteViewsService
{
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent)
	{
		return new ScrollableRemoteViewsFactoryCompact(this.getApplicationContext(), intent);
	}
}

@SuppressLint("NewApi")
class ScrollableRemoteViewsFactoryCompact extends ScrollableRemoteViewsFactoryBase implements 
	RemoteViewsService.RemoteViewsFactory
{
	// This array is used for displaying priorities:
	private int[] _priorityArray = {
		R.id.widget_task_list_color_white,
		R.id.widget_task_list_color_green,
		R.id.widget_task_list_color_blue,
		R.id.widget_task_list_color_yellow,
		R.id.widget_task_list_color_orange,
		R.id.widget_task_list_color_red
	};


	public ScrollableRemoteViewsFactoryCompact(Context context, Intent intent)
	{
		super(context, intent);
	}
	
	@Override
	public RemoteViews getViewAt(int position)
	{
		RemoteViews rv;
		rv = new RemoteViews(_c.getPackageName(), R.layout.scrollable_widget_row);
		
		if (position>=_taskList.size())
		{
			// I have seen this happen once, so it must be handled.  Just return a blank row.
			rv.setViewVisibility(R.id.widget_task_list_color_white, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_green, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_blue, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_yellow, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_orange, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_red, View.GONE);
			rv.setViewVisibility(R.id.widget_task_list_color_cyan, View.GONE);
			Util.log("OS requested view position that was too high.");
			return rv;
		}
		
		UTLTaskDisplay td = _taskList.get(position).get("task");
		UTLTask t = td.task;
		int theme = _displayOptions.widgetTheme;
		Resources r = _c.getResources();
		
		// Color-Coded Bars:
		rv.setViewVisibility(R.id.widget_task_list_color_white, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_green, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_blue, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_yellow, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_orange, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_red, View.GONE);
		rv.setViewVisibility(R.id.widget_task_list_color_cyan, View.GONE);
		if (_colorCodeEnabled)
		{
			if (_displayOptions.widgetColorCodedField.equals("star"))
			{
				if (t.star)
					rv.setViewVisibility(R.id.widget_task_list_color_cyan, View.VISIBLE);
				else
					rv.setViewVisibility(R.id.widget_task_list_color_cyan, View.INVISIBLE);
			}
			else
				rv.setViewVisibility(_priorityArray[t.priority], View.VISIBLE);
		}
		
		// Extra Field, if used:
		if (_extraFieldEnabled)
		{
			if (_displayOptions.widgetExtraField.equals("due_date") || _displayOptions.
        		widgetExtraField.equals("due_date_time"))
			{
				String extraValue = getExtraFieldValue(td);
				rv.setTextViewText(R.id.widget_task_list_extra, Html.fromHtml(extraValue));
				if (!extraValue.contains("red"))
				{
					rv.setTextColor(R.id.widget_task_list_extra,r.getColor(TaskListWidget.
						TEXT_COLORS[theme]));
				}
			}
			else
			{
				rv.setTextViewText(R.id.widget_task_list_extra, getExtraFieldValue(td));
				rv.setTextColor(R.id.widget_task_list_extra,r.getColor(TaskListWidget.TEXT_COLORS[
					theme]));
			}
			rv.setViewVisibility(R.id.widget_task_list_extra, View.VISIBLE);
			
			// Set the width, based on the user's preference:
			rv.setInt(R.id.widget_task_list_extra, "setWidth", _displayOptions.
				widgetExtraFieldWidth);
		}
		else
			rv.setViewVisibility(R.id.widget_task_list_extra, View.GONE);
		
		// Task Title:
		if (td.task.parent_id>0 && _settings.getBoolean("subtasks_enabled",
    		true) && _displayOptions.widgetSubtaskOption.equals("indented") &&
    		!orphanedSubtaskIDs.contains(td.task._id))
    	{
			String indent = "";
    		for (int j=1; j<td.level; j++)
    			indent += "   ";
    		rv.setTextViewText(R.id.widget_task_list_title, indent+"- "+t.title);
    	}
		else
			rv.setTextViewText(R.id.widget_task_list_title, t.title);
		rv.setTextColor(R.id.widget_task_list_title,r.getColor(TaskListWidget.TEXT_COLORS[theme]));
		
		// The intent to call when the task is tapped on:
		Intent i = new Intent();
		rv.setOnClickFillInIntent(R.id.widget_task_list_scrollable_row, i);
		
		return rv;
	}
	
	// Get the string to put in the extra field:
	protected String getExtraFieldValue(UTLTaskDisplay td)
    {
		UTLTask t = td.task;
		String[] statuses = _c.getResources().getStringArray(R.array.statuses);
        String[] priorities = _c.getResources().getStringArray(R.array.priorities);

    	if (_displayOptions.widgetExtraField.equals("account"))
    	{
    		return td.accountName;
    	}
    	if (_displayOptions.widgetExtraField.equals("folder"))
    	{
    		return td.folderName;
    	}
    	if (_displayOptions.widgetExtraField.equals("context"))
    	{
    		return td.contextName;
    	}
    	if (_displayOptions.widgetExtraField.equals("goal"))
    	{
    		return td.goalName;
    	}
    	if (_displayOptions.widgetExtraField.equals("location"))
    	{
    		return td.locationName;
    	}
    	if (_displayOptions.widgetExtraField.equals("tags"))
    	{
            if (td.numTags==0)
            {
                return "";
            }
            else if (td.numTags==1)
            {
                return td.firstTagName;
            }
            else
            {
                // We need to query the DB for tags.
                String[] tagList;
                tagList = (new TagsDbAdapter()).getTagsInDbOrder(td.task._id);
                String tagText = tagList[0];
                for (int i=1; i<tagList.length; i++)
                {
                    tagText += ","+tagList[i];
                }
                return tagText;
            }
    	}
    	if (_displayOptions.widgetExtraField.equals("due_date"))
    	{
    		// For the due date, we highlight overdue in red:
    		if (t.uses_due_time && t.due_date<System.currentTimeMillis()+
            	_timeZoneOffset)
    		{
    			return "<font color=red>"+getCompactDateString(t.due_date)+"</font>";
    		}
    		else if (!t.uses_due_time && t.due_date<_midnightToday)
    		{
    			return "<font color=red>"+getCompactDateString(t.due_date)+"</font>";
    		}
    		else
    		{
    			return (getCompactDateString(t.due_date));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("due_date_time"))
    	{
    		// For the due date and time, we highlight overdue in red:
    		if (t.uses_due_time && t.due_date<System.currentTimeMillis()+
            	_timeZoneOffset)
    		{
    			return "<font color=red>"+getCompactDateString(t.due_date)+" "+
    				Util.getTimeString(t.due_date)+"</font>";
    		}
    		else if (!t.uses_due_time && t.due_date<_midnightToday)
    		{
    			return "<font color=red>"+getCompactDateString(t.due_date)+"</font>";
    		}
    		else if (!t.uses_due_time)
    		{
    			return (getCompactDateString(t.due_date));
    		}
    		else
    		{
    			return (getCompactDateString(t.due_date)+" "+
    				Util.getTimeString(t.due_date));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("due_time"))
        {
        	if (!t.uses_due_time)
        		return "";
        	else
        		return(Util.getTimeString(t.due_date));
        }
    	if (_displayOptions.widgetExtraField.equals("start_date"))
    		return (getCompactDateString(t.start_date));
    	if (_displayOptions.widgetExtraField.equals("start_date_time"))
    	{
    		if (t.uses_start_time)
    		{
    			return (getCompactDateString(t.start_date)+" "+Util.getTimeString(
    				t.start_date));
    		}
    		else
    		{
    			return (getCompactDateString(t.start_date));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("start_time"))
    	{
    		if (!t.uses_start_time)
    			return "";
    		else
    			return (Util.getTimeString(t.start_date));
    	}
    	if (_displayOptions.widgetExtraField.equals("reminder"))
    	{
    		if (t.reminder==0)
    			return "";
    		else
    		{
    			return (getCompactDateString(t.reminder)+" "+Util.getTimeString(
    				t.reminder));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("completion_date"))
    		return (getCompactDateString(t.completion_date));
    	if (_displayOptions.widgetExtraField.equals("mod_date"))
    		return (getCompactDateString(t.mod_date));
    	if (_displayOptions.widgetExtraField.equals("status"))
    		return (statuses[t.status]);
    	if (_displayOptions.widgetExtraField.equals("length"))
    		return (t.length+" "+Util.getString(R.string.minutes_abbreviation));
    	if (_displayOptions.widgetExtraField.equals("importance"))
    	{
    		if (t.importance>=10)
    			return (Integer.valueOf(t.importance).toString());
    		else
    			return ("0"+Integer.valueOf(t.importance).toString());
    	}
    	if (_displayOptions.widgetExtraField.equals("priority"))
    		return (priorities[t.priority]);
    	if (_displayOptions.widgetExtraField.equals("timer"))
    	{
    		long minutes = t.timer / 60;
    		return (minutes + " " + Util.getString(R.string.minutes_abbreviation));    		
    	}
    	if (_displayOptions.widgetExtraField.equals("contact"))
    	{
	    	if (t.contactLookupKey!=null && t.contactLookupKey.length()>0)
	    	{
	        	Uri contactUri = Uri.withAppendedPath(ContactsContract.Contacts.
	        		CONTENT_LOOKUP_URI,t.contactLookupKey);
	        	Cursor c = _c.getContentResolver().query(contactUri, new String[] 
	        	    {Contacts.DISPLAY_NAME}, null, null, null);
	        	if (c!=null && c.moveToFirst())
	        		return Util.cString(c,ContactsContract.Contacts.DISPLAY_NAME);
	        	else
	        		return Util.getString(R.string.Missing_Contact);
	    	}
	    	else
	    		return "";
    	}
    	if (_displayOptions.widgetExtraField.equals("is_joint"))
    	{
    		if (td.task.is_joint)
    			return Util.getString(R.string.Shared);
    		else
    			return "";
    	}
    	if (_displayOptions.widgetExtraField.equals("owner_name"))
    	{
    		return td.ownerName;
    	}
    	if (_displayOptions.widgetExtraField.equals("shared_with"))
    	{
    		return td.sharedWith;
    	}
    	if (_displayOptions.widgetExtraField.equals("assignor_name"))
    	{
    		return td.assignorName;
    	}

   		return (getCompactDateString(t.due_date));    	
    }
	
    // Format a date string in a compact way for the widget:
    private String getCompactDateString(long millis)
    {
    	if (millis==0)
    		return "";
    	
    	GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            _settings.getString("home_time_zone","")));
        c.setTimeInMillis(millis);
        
        // Format based on the system date format:
        String result = "";
        int month = c.get(Calendar.MONTH);
        month++;
        switch (Util.currentDateFormat)
        {
        case Util.DATE_FORMAT_DAY_FIRST:
        	result += c.get(Calendar.DAY_OF_MONTH)+"/";
        	result += Integer.toString(month);
        	break;
        default:
        	result += Integer.toString(month)+"/";
        	result += c.get(Calendar.DAY_OF_MONTH);
        	break;
        }
        return result;
    }

}
