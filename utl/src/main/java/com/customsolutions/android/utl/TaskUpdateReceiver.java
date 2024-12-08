package com.customsolutions.android.utl;

// This class handles messages from widgets for marking a task as complete
// and for deleting a task.

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

@SuppressLint("NewApi")
public class TaskUpdateReceiver extends BroadcastReceiver
{

	@Override
	public void onReceive(Context context, Intent intent)
	{
		// Initialize the app if it has not been:
		Util.appInit(context);

		Util.log("Got Intent: "+Util.intentToString(intent,2));

		// If the intent does not have valid data, then record the error and exit:
		if (intent.getData()==null || !intent.getData().getScheme().equals("content"))
		{
			Util.log("TaskUpdateReceiver got a bad URI: "+intent.getDataString());
			return;
		}
		String action = intent.getAction();
		if (action==null)
		{
			Util.log("TaskUpdateReceiver got a null action.");
			return;
		}
		
		// Everything after the final / is the task ID:
    	String lastSegment = intent.getData().getLastPathSegment();
    	Long taskID;
    	try
    	{
    		taskID = Long.parseLong(lastSegment);
    	}
    	catch (NumberFormatException e)
    	{
    		Util.log("TaskUpdateReceiver got a bad URI: "+intent.getDataString());
    		return;
    	}
    	
    	// Look at the action part of the intent and do what it says:
    	if (action.endsWith("ACTION_COMPLETE"))
    	{
    		TasksDbAdapter tasksDB = new TasksDbAdapter();
    		UTLTask t = tasksDB.getTask(taskID);
    		if (t!=null)
    		{
    			boolean newCompletionStatus = !t.completed;
    			if (!t.completed)
    				Util.markTaskComplete(taskID);
    			else
    			{
    				// Task is going from complete to incomplete:
    				t.completed = false;
    		        t.mod_date = System.currentTimeMillis();
    		        t.completion_date = 0;
    		        tasksDB.modifyTask(t);
    			}
    			
    			// Check to see if a widget ID was passed in.  If so, we set some widget options that
    			// tell the widget to keep the task visible at the next refresh.
    			Bundle extras = intent.getExtras();
    			if (extras!=null && extras.containsKey("app_widget_id") && Build.VERSION.SDK_INT>=16)
    			{
    				Bundle widgetOptions = new Bundle();
    				widgetOptions.putBoolean("dont_requery_database", true);
    				widgetOptions.putLong("task_id", t._id);
    				widgetOptions.putBoolean("is_completed",newCompletionStatus);
    				AppWidgetManager awm = AppWidgetManager.getInstance(context);
    				awm.updateAppWidgetOptions(extras.getInt("app_widget_id"), widgetOptions);
    			}
    			
    			// Check to see if this was from a notification.  If so, dismiss the notification:
    			if (extras!=null && extras.containsKey("from_notification") &&
    				extras.getBoolean("from_notification") && newCompletionStatus)
    			{
    				Util.cancelReminderNotification(t._id);
    			}
    			
    			Util.updateWidgets();
    			
    			Util.instantTaskUpload(context, t);
    		}
    	}
    	else if (action.endsWith("ACTION_EDIT"))
    	{
    		// Launch the task editor. On larger screens we launch the popup activity. On smaller
			// ones, we launch the regular editor.
			int screenSize = context.getResources().getConfiguration().screenLayout & Configuration.
				SCREENLAYOUT_SIZE_MASK;
			Util.log("Screen size: "+screenSize);
			Intent i;
			i = new Intent(context,EditTaskPopup.class);
    		i.putExtra("action", EditTaskFragment.EDIT);
    		i.putExtra("id", taskID);
    		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    		context.startActivity(i);
    	}
    	else if (action.endsWith("ACTION_DELETE"))
    	{
    		Util.deleteTask(taskID);
    		Util.updateWidgets();
    	}
    	else
    	{
    		Util.log("TaskUpdateReceiver got bad action: "+action);
    		return;
    	}
	}

}
