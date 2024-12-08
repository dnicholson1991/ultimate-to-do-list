package com.customsolutions.android.utl;

// This class handles messages from the Pure Calendar widget for marking a task as complete
// and for deleting a task.

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AndroidAgendaReceiver extends BroadcastReceiver
{

	@Override
	public void onReceive(Context context, Intent intent)
	{
		// Initialize the app if it has not been:
		Util.appInit(context);
		
		// If the intent does not have valid data, then record the error and exit:
		if (intent.getData()==null || !intent.getData().getScheme().equals("content"))
		{
			Util.log("AndroidAgendaReceiver got a bad URI: "+intent.getDataString());
			return;
		}
		String action = intent.getAction();
		if (action==null)
		{
			Util.log("AndroidAgendaReceiver got a null action.");
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
    		Util.log("AndroidAgendaReceiver got a bad URI: "+intent.getDataString());
    		return;
    	}
    	
    	// Look at the action part of the intent and do what it says:
    	if (action.endsWith("ACTION_COMPLETE"))
    	{
    		Util.markTaskComplete(taskID);
    	}
    	else if (action.endsWith("ACTION_DELETE"))
    	{
    		Util.deleteTask(taskID);
    	}
    	else
    	{
    		Util.log("AndroidAgendaReceiver got bad action: "+action);
    		return;
    	}
	}

}
