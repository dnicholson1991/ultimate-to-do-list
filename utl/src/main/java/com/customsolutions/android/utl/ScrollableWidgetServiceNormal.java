package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

@SuppressLint("NewApi")
public class ScrollableWidgetServiceNormal extends RemoteViewsService
{
	@Override
	public RemoteViewsFactory onGetViewFactory(Intent intent)
	{
		return new ScrollableRemoteViewsFactoryNormal(this.getApplicationContext(), intent);
	}
}

@SuppressLint("NewApi")
class ScrollableRemoteViewsFactoryNormal extends ScrollableRemoteViewsFactoryBase implements 
	RemoteViewsService.RemoteViewsFactory
{
	private static final int DARK = 3;  // The dark theme, in DisplayOptions.java.
	
	// The number of dp to indent subtasks.  Indent by this much per level:
	private static final int SUBTASK_INDENTATION = 15;
	
	// A scale factor to use when converting dp to actual pixels:
	private float _viewScaleFactor;
	
	public ScrollableRemoteViewsFactoryNormal(Context context, Intent intent)
	{
		super(context, intent);		
		_viewScaleFactor = context.getResources().getDisplayMetrics().density;
	}
	
	@SuppressLint("InlinedApi")
	@Override
	public RemoteViews getViewAt(int position)
	{
		RemoteViews rv;
		rv = new RemoteViews(_c.getPackageName(), R.layout.scrollable_widget_row2);
		
		if (position>=_taskList.size())
		{
			// I have seen this happen once, so it must be handled.  Just return a blank row.
			Util.log("OS requested view position that was too high.");
			return rv;
		}
		
		UTLTaskDisplay td = _taskList.get(position).get("task");
		UTLTask t = td.task;
		int theme = _displayOptions.widgetTheme;
		Resources r = _c.getResources();
		
		if (_colorCodeEnabled)
		{
			if (_displayOptions.widgetColorCodedField.equals("star"))
			{
				if (td.task.completed)
				{
					rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox, R.drawable.
						checkbox_checked);
				}
				else if (td.task.star)
				{
					rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox,R.drawable.
						checkbox_cyan);
				}
				else
				{
					rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox,R.drawable.
						checkbox_medium_gray);
				}
			}
			else
			{
				// Completed checkbox:
				int[] checkboxDrawables = TaskListFragment._priorityCheckboxDrawables;
				if (td.task.completed)
				{
					rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox, R.drawable.
						checkbox_checked);
				}
				else
				{
					rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox,checkboxDrawables[td.task.
					    priority]);
				}
			}
		}
		else
		{
			if (td.task.completed)
			{
				rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox, R.drawable.
					checkbox_checked);
			}
			else
			{
				rv.setImageViewResource(R.id.widget_task_list_scrollable_checkbox,R.drawable.
					checkbox_medium_gray);
			}
		}
		
		// Extra field:
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
		}
		else
			rv.setTextViewText(R.id.widget_task_list_extra, "");
		
		// Title:
		rv.setTextViewText(R.id.widget_task_list_title, t.title);
		rv.setTextColor(R.id.widget_task_list_title,r.getColor(TaskListWidget.TEXT_COLORS[theme]));
		
		// Indentation:
		if (td.level==0)
			rv.setViewVisibility(R.id.widget_task_list_indenter, View.GONE);
		else
		{
			int indentation = Float.valueOf(SUBTASK_INDENTATION*td.level*_viewScaleFactor).intValue();
			rv.setInt(R.id.widget_task_list_indenter, "setWidth", indentation);
			rv.setViewVisibility(R.id.widget_task_list_indenter, View.VISIBLE);
		}
		
		// Intent to fire when tapping on a checkbox:
		Intent i = new Intent();
		i.setAction("com.customsolutions.android.utl.TaskUpdateReceiver.ACTION_COMPLETE");
		i.setData(Uri.parse(
			"content://com.customsolutions.android.utl.purecalendarprovider/tasks/"+td.task._id));
		i.putExtra("app_widget_id",_appWidgetId);
		rv.setOnClickFillInIntent(R.id.widget_task_list_scrollable_checkbox, i);
		
		// Intent to fire when tapping on the rest of the row:
		i = new Intent();
		i.setAction("com.customsolutions.android.utl.TaskUpdateReceiver.ACTION_EDIT");
		i.setData(Uri.parse(
			"content://com.customsolutions.android.utl.purecalendarprovider/tasks/"+td.task._id));
		rv.setOnClickFillInIntent(R.id.widget_task_list_scrollable_hit_area, i);
		
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
    			return "<font color=red>"+getDateString(t.due_date)+"</font>";
    		}
    		else if (!t.uses_due_time && t.due_date<_midnightToday)
    		{
    			return "<font color=red>"+getDateString(t.due_date)+"</font>";
    		}
    		else
    		{
    			return (getDateString(t.due_date));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("due_date_time"))
    	{
    		// For the due date and time, we highlight overdue in red:
    		if (t.uses_due_time && t.due_date<System.currentTimeMillis()+
            	_timeZoneOffset)
    		{
    			return "<font color=red>"+getDateString(t.due_date)+" "+
    				Util.getTimeString(t.due_date,_c)+"</font>";
    		}
    		else if (!t.uses_due_time && t.due_date<_midnightToday)
    		{
    			return "<font color=red>"+getDateString(t.due_date)+"</font>";
    		}
    		else if (!t.uses_due_time)
    		{
    			return (getDateString(t.due_date));
    		}
    		else
    		{
    			return (getDateString(t.due_date)+" "+
    				Util.getTimeString(t.due_date,_c));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("due_time"))
        {
        	if (!t.uses_due_time)
        		return "";
        	else
        		return(Util.getTimeString(t.due_date,_c));
        }
    	if (_displayOptions.widgetExtraField.equals("start_date"))
    		return (getDateString(t.start_date));
    	if (_displayOptions.widgetExtraField.equals("start_date_time"))
    	{
    		if (t.uses_start_time)
    		{
    			return (getDateString(t.start_date)+" "+Util.getTimeString(
    				t.start_date,_c));
    		}
    		else
    		{
    			return (getDateString(t.start_date));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("start_time"))
    	{
    		if (!t.uses_start_time)
    			return "";
    		else
    			return (Util.getTimeString(t.start_date,_c));
    	}
    	if (_displayOptions.widgetExtraField.equals("reminder"))
    	{
    		if (t.reminder==0)
    			return "";
    		else
    		{
    			return (getDateString(t.reminder)+" "+Util.getTimeString(
    				t.reminder,_c));
    		}
    	}
    	if (_displayOptions.widgetExtraField.equals("completion_date"))
    		return (getDateString(t.completion_date));
    	if (_displayOptions.widgetExtraField.equals("mod_date"))
    		return (getDateString(t.mod_date));
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

   		return (getDateString(t.due_date));    	
    }
	
	private String getDateString(long millis)
	{
		if (millis==0)
			return "";
		else
			return Util.getDateString(millis);
	}
}
