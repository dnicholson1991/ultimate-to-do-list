package com.customsolutions.android.utl;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import static com.customsolutions.android.utl.Util.getString;

/**
 * When triggered by Tasker, this will create a task from the template passed in or it will
 * mark a task as complete.
 */

public class TaskerReceiver extends BroadcastReceiver
{
    private static final String TAG = "TaskerReceiver";

    /** This action specifies that the Intent is from Tasker. */
    public static final String ACTION_FIRE_SETTING = "com.twofortyfouram.locale.intent.action.FIRE_SETTING";

    /** This intent extra holds a bundle containing information passed between UTL and Tasker. */
    public static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";

    /** This Bundle key is used to identify the type of Bundle. */
    public static final String KEY_BUNDLE_TYPE = "bundle_type";

    /** This value is inserted into the EXTRA_BUNDLE bundle, under the key KEY_BUNDLE_TYPE.
     * This is used by UTL to specify that the Bundle contains a task template. */
    public static final String BUNDLE_TYPE_TEMPLATE = "bundle_type_template";

    /** This value is inserted into the EXTRA_BUNDLE bundle, under the key KEY_BUNDLE_TYPE.
     * This is used by UTL to specify that the Bundle contains a view / task list to show. */
    public static final String BUNDLE_TYPE_TASK_LIST = "bundle_type_task_list";

    /** This value is inserted into the EXTRA_BUNDLE bundle, under the key KEY_BUNDLE_TYPE.
     * This is used by UTL to specify that hte Bundle contains a task to be marked as complete. */
    public static final String BUNDLE_TYPE_MARK_COMPLETE = "bundle_type_mark_complete";

    /** This intent extra holds a summary message to display in Tasker. */
    public static final String EXTRA_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";

    private void log(String s)
    {
        Log.v(TAG,s);
    }

    @Override
    public void onReceive(final Context context, final Intent intent)
    {
        Util.appInit(context);

        // Make sure the action is what is expected.
        if (!ACTION_FIRE_SETTING.equals(intent.getAction()))
        {
            log("Invalid action received: "+intent.getAction());
            return;
        }

        // Make sure required extras are present.
        Bundle extras = intent.getExtras();
        if (extras==null || !extras.containsKey(EXTRA_BUNDLE))
        {
            log("Missing required input: "+EXTRA_BUNDLE);
            return;
        }
        log("Bundle Received: "+Util.bundleToString(extras,2));

        // Check to see what type of Bundle we received:
        Bundle dataFromTasker = extras.getBundle(EXTRA_BUNDLE);
        if (dataFromTasker==null || !dataFromTasker.containsKey(KEY_BUNDLE_TYPE))
        {
            log("Missing bundle or bundle type.");
            return;
        }
        String bundleType = dataFromTasker.getString(KEY_BUNDLE_TYPE);

        if (bundleType.equals(BUNDLE_TYPE_TEMPLATE))
        {
            // Extract the template from the intent's bundle:
            TaskTemplate template = TaskTemplate.fromBundle(extras.getBundle(EXTRA_BUNDLE));

            // Attempt to create the task, and handle any errors.
            try
            {
                template.createTask(context);
            }
            catch (IllegalArgumentException e)
            {
                // Display the error to the user:
                String message = context.getString(R.string.cannot_create_task) + " " +
                    e.getMessage();
                Util.popup(context, message);
            }
        }
        else if (bundleType.equals(BUNDLE_TYPE_MARK_COMPLETE))
        {
            // Extract the title:
            if (!dataFromTasker.containsKey("title"))
            {
                log("Title is missing.");
                return;
            }
            String title = dataFromTasker.getString("title");

            // Search for an incomplete task with a matching title:
            TasksDbAdapter tasksDB = new TasksDbAdapter();
            Cursor c = tasksDB.queryTasks("completed=0 and (lower(title)='"+Util.
                makeSafeForDatabase(title)+"' or title='"+Util.makeSafeForDatabase(title)+"')",
                null);
            if (c.moveToFirst())
            {
                UTLTask t = tasksDB.getUTLTask(c);
                Util.markTaskComplete(t._id);
                Util.updateWidgets();
            }
            else
            {
                Util.popup(context,context.getString(R.string.no_task_with_name).replace(
                    "[task_name]",title));
                log("No task found named "+title);
            }
            c.close();
        }
        else if (bundleType.equals(BUNDLE_TYPE_TASK_LIST))
        {
            // On Android 10, we need to check permissions:
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q &&
                !android.provider.Settings.canDrawOverlays(context.getApplicationContext()))
            {
                // Permission not given. Display a notification:
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                i.setData(Uri.parse("package:" + context.getPackageName()));
                PendingIntent pi = PendingIntent.getActivity(context,0,i,PendingIntent.
                    FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                NotificationCompat.Builder n = new NotificationCompat.Builder(context,Util.
                    NOTIF_CHANNEL_MISC)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),R.drawable.
                        icon))
                    .setSmallIcon(R.drawable.nav_all_tasks_dark)
                    .setPriority(Notification.PRIORITY_MIN)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setContentTitle(getString(R.string.unable_to_open_list))
                    .setContentText(getString(R.string.overlay_permission_needed))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.
                        overlay_permission_needed)))
                    .setContentIntent(pi)
                    ;
                NotificationManager nm = (NotificationManager)context.getSystemService(Context.
                    NOTIFICATION_SERVICE);
                nm.notify(1,n.build());
                return;
            }

            // Extract the view ID and view information:
            if (!dataFromTasker.containsKey("view_id") || !dataFromTasker.containsKey("list_title"))
            {
                log("Inputs are missing.");
                return;
            }
            long viewID = dataFromTasker.getLong("view_id");
            String listTitle = dataFromTasker.getString("list_title");
            ViewsDbAdapter vdb = new ViewsDbAdapter();
            String topLevel;
            String viewName;
            Cursor cu = vdb.getView(viewID);
            if (cu.moveToFirst())
            {
                topLevel = Util.cString(cu,"top_level");
                viewName = Util.cString(cu,"view_name");
                log("Top Level: "+topLevel);
                log("View Name: "+viewName);
                cu.close();
            }
            else
            {
                cu.close();
                log("View ID "+viewID+" no longer exists in the database.");
                Util.popup(context,getString(R.string.task_list_deleted));
                return;
            }

            // Create an Intent that opens the task list:
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.scheme("tasklist");
            uriBuilder.opaquePart(String.valueOf(viewID));
            Intent listIntent = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,
                TaskList.class);
            listIntent.putExtra("top_level", topLevel);
            listIntent.putExtra("view_name", viewName);
            listIntent.putExtra("title", listTitle);
            listIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            // Open the list:
            log("Opening Task List...");
            context.startActivity(listIntent);
        }
        else
        {
            log("Unknown bundle type: "+bundleType);
        }
    }
}
