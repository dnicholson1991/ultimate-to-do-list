package com.customsolutions.android.utl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * This receives requests from Tasker to see if an event or condition has been satisfied.
 */

public class TaskerQueryReceiver extends BroadcastReceiver
{
    /** Intent Action that we expect to receive from tasker. */
    final static public String ACTION_TASKER_QUERY = "com.twofortyfouram.locale.intent.action.QUERY_CONDITION";

    /** Result code indicating that the condition is satisfield. */
    final static public int RESULT_CODE_SATISFIED = 16;

    /** Result code indicating that the condition is unsatisfied. */
    final static public int RESULT_CODE_UNSATISFIED = 17;

    /** Result code indicating that the condition is unkown. */
    final static public int RESULT_CODE_UNKNOWN = 18;

    final static private String TAG = "TaskerQueryReceiver";

    private void log(String msg)
    {
        Log.v(TAG,msg);
    }

    public final void onReceive(final Context context, Intent intent)
    {
        Util.appInit(context);

        if (!ACTION_TASKER_QUERY.equals(intent.getAction()))
        {
            log("Unexpected Action received: "+intent.getAction());
            return;
        }

        log("Bundle Received: "+Util.bundleToString(intent.getExtras(),2));

        // Get the event's task title from the Bundle passed in:
        Bundle extras = intent.getExtras();
        if (extras==null || !extras.containsKey(TaskerReceiver.EXTRA_BUNDLE))
        {
            log("Missing required input: "+TaskerReceiver.EXTRA_BUNDLE);
            return;
        }
        Bundle dataFromTasker = extras.getBundle(TaskerReceiver.EXTRA_BUNDLE);
        if (!dataFromTasker.containsKey("title"))
        {
            log("Missing the event's task title.");
            return;
        }

        // Get the title of the task that was actually completed:
        Bundle taskData = TaskerPlugin.Event.retrievePassThroughData(intent);
        if (taskData==null || !taskData.containsKey("completed_task_title") || !taskData.
            containsKey("completed_task_id"))
        {
            log("No task data found.");
            return;
        }

        // The title of the completed task must match the title of the task specified in the event.
        if (taskData.getString("completed_task_title").equals(dataFromTasker.getString("title")))
        {
            log("Completed task's title does match event's task title.");
            setResultCode(RESULT_CODE_SATISFIED);
            return;
        }

        log("Completed task's title does not match event's task title.");
        setResultCode(RESULT_CODE_UNSATISFIED);
    }
}
