package com.customsolutions.android.utl;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Receives Intents and sends commands to the Android Wear device.
 */
public class WearService extends WearableListenerService {

    // Actions this service supports.  These must match the corresponding service on the wearable:
    final static public String ACTION_OPEN_SNOOZER = "com.customsolutions.android.utl.open_snoozer";
        // Required extras "task_id","is_location"
    final static public String ACTION_PERFORM_SNOOZE = "com.customsolutions.android.utl.perform_snooze";
        // required extras are "task_id" and "num_minutes"
    final static public String ACTION_LOG = "com.customsolutions.android.utl.log";
        // required extra is "text"
    final static public String ACTION_SPEECH_TO_PROCESS = "com.customsolutions.android.utl.speech_to_process";
    final static public String ACTION_SPEECH_RECOGNIZER_RESPONSE = "com.customsolutions.android.utl.speech_recognizer_response";
    final static public String ACTION_UPDATE_COMPLETION_STATUS = "com.customsolutions.android.utl.update_completion";
    final static public String ACTION_GET_DEFAULT_LIST = "com.customsolutions.android.utl.get_default_list";
    final static public String ACTION_OPEN_TASK_LIST = "com.customsolutions.android.utl.open_task_list";
    final static public String ACTION_SHOW_DAILY_SUMMARY = "com.customsolutions.android.utl.show_daily_summary";

    /** Notification ID to use when running in the foreground. */
    private final static int NOTIFICATION_ID = 1536851847;

    // Implements communication:
    GoogleApiClient _googleApiClient;

    boolean _isConnected;

    private String _savedAction;
    private Bundle _savedData;

    private SharedPreferences _settings;

    /** The view ID being read.  This is zero when parsing was unsuccessful. */
    private long _viewID;

    /** This holds the task data: */
    protected ArrayList<HashMap<String,UTLTaskDisplay>> _taskList;

    /** This keeps track of orphaned subtasks that should not be indented. */
    protected HashSet<Long> _orphanedSubtaskIDs;

    protected DisplayOptions _displayOptions;

    // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
    // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
    protected HashMap<Long,ArrayList<UTLTaskDisplay>> _subLists = new HashMap<Long,
        ArrayList<UTLTaskDisplay>>();

    @Override
    public void onCreate()
    {
        super.onCreate();
        Util.appInit(this);

        Util.log("WearService: onCreate() called.");

        _settings = this.getSharedPreferences(Util.PREFS_NAME,0);

        _isConnected = false;
        initGoogleApiClient();

        _orphanedSubtaskIDs = new HashSet<Long>();
    }

    public void initGoogleApiClient()
    {
        if (!_isConnected)
        {
            _googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Util.log("WearService: onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
                        _isConnected = true;
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Util.log("WearService: onConnectionSuspended: " + cause);
                        _isConnected = false;
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Util.log("WearService: onConnectionFailed: " + result.getErrorCode()+ " / "+
                            result.toString());
                        _isConnected = false;
                    }
                })
                .addApi(Wearable.API) // Request access only to the Wearable API
                .build();
            _googleApiClient.connect();

            // Log my Node ID:
            Wearable.NodeApi.getLocalNode(_googleApiClient).setResultCallback(new ResultCallback
                <NodeApi.GetLocalNodeResult>()
            {
                @Override
                public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult)
                {
                    Node n = getLocalNodeResult.getNode();
                    Util.log("WearService: My Node ID: "+n.getId());
                }
            });
        }
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        Util.appInit(this);

        // This does nothing if the device cannot run Android Wear:
        PurchaseManager pm = new PurchaseManager(this);
        if (!Util.canUseAndroidWear() || !pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
            return super.onStartCommand(intent, flags, startId);

        if (intent==null || intent.getAction()==null || intent.getAction().length()==0)
        {
            return super.onStartCommand(intent, flags, startId);
        }

        try
        {
            if (!_isConnected) {
                Util.log("WearService: Warning: There doesn't seem to be a connection.  Trying anyway...");
            }

            _savedAction = intent.getAction();
            if (_savedAction.equals(ACTION_SHOW_DAILY_SUMMARY))
            {
                // Since the app will likely be in the background when this is called, we must
                // make this a foreground service on Android Oreo and up.
                /* 7/5/2024: I don't think this needs to be run in the foreground. Due to GBS,
                   extra review is needed by Google to allow foreground services, so I will only
                   use them if absolutely necessary.
                if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                    moveToForeground();
                */

                // We need to get a count of the number of tasks in the default android wear task list,
                // and generate a corresponding message for the wearable.
                long viewID = getDefaultViewID();
                if (viewID==-1)
                {
                    // This should not happen, but if it does we have to ignore this request.
                    return super.onStartCommand(intent, flags, startId);
                }
                String counterQuery = Util.getTaskCountQuery(viewID);
                Cursor c = Util.db().rawQuery(counterQuery,null);
                int count = 0;
                if (Util.regularExpressionMatch(counterQuery, "select count"))
                {
                    // Query is getting a count in the first row of the result:
                    c.moveToFirst();
                    count = c.getInt(0);
                }
                else
                {
                    // The count to return is the number of rows.
                    count = c.getCount();
                }
                c.close();

                // Generate a Bundle containing the message to display.  This will be sent to the
                // wearable.
                _savedData = new Bundle();
                if (count>0)
                    _savedData.putString("line_2",getString(R.string.View_Details));
                if (count==1)
                {
                    _savedData.putString("line_1",getString(R.string.one_task_today));
                }
                else
                {
                    // Get a localized string to start, and then modify the number in it.
                    _savedData.putString("line_1",getString(R.string.two_tasks_today).replaceFirst(Util.getNumberString(2),
                        Util.getNumberString(count)));
                }

                if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                {
                    // After a short delay to ensure the watch gets the message, move the
                    // service to the background.
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            stopForeground(true);
                        }
                    },5000);
                }
            }
            else
                _savedData = intent.getExtras();

            // We have no way of knowing which node the message is intended for.  Until a better solution
            // can be found, we will broadcast to all.  This works for the daily summary, but does not
            // make sense when opening the snoozer.
            Wearable.NodeApi.getConnectedNodes(_googleApiClient).setResultCallback(new ResultCallback
                <NodeApi.GetConnectedNodesResult>()
            {
                @Override
                public void onResult(NodeApi.GetConnectedNodesResult nodesResult)
                {
                    List<Node> nodes = nodesResult.getNodes();
                    Iterator<Node> it = nodes.iterator();
                    while (it.hasNext())
                    {
                        Node destination = it.next();
                        sendMessage(_savedAction,_savedData,destination.getId());
                    }
                }
            });
        }
        finally
        {
            return super.onStartCommand(intent, flags, startId);
        }
    }

    /** Move this service to the foreground. */
    /*
    private void moveToForeground()
    {
        // Create the notification and set some options:
        NotificationCompat.Builder n = new NotificationCompat.Builder(this,Util.
            NOTIF_CHANNEL_MISC)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.icon))
            .setSmallIcon(R.drawable.nav_all_tasks_dark)
            .setPriority(Notification.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(getString(R.string.syncing_with_watch))
            ;

        // Move the service to the foreground:
        startForeground(NOTIFICATION_ID,n.build());
    }
    */

    /** Receive a message: */
    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        FeatureUsage featureUsage = new FeatureUsage(this);
        String sourceNodeID = messageEvent.getSourceNodeId();

        // Get the action and the data from the message.
        String action = messageEvent.getPath();
        if (!action.equals(ACTION_LOG))
            Util.log("WearService: Received Message from "+sourceNodeID+": "+action);
        byte[] data = messageEvent.getData();
        Bundle b = new Bundle();
        if (data!=null)
        {
            DataMap dataMap = DataMap.fromByteArray(data);
            b = dataMap.toBundle();
        }

        PurchaseManager pm = new PurchaseManager(this);

        if (action.equals(ACTION_PERFORM_SNOOZE))
        {
            TasksDbAdapter tasksDB = new TasksDbAdapter();
            UTLTask t = tasksDB.getTask(b.getLong("task_id"));
            boolean isLocation = b.getBoolean("is_location");
            if (t!=null)
            {
                // Clear the current notification:
                Util.removeTaskNotification(t._id);

                // Calculate the new reminder time:
                long newReminderTime = System.currentTimeMillis() + b.getInt("num_minutes")*60000;

                if (isLocation)
                    Util.snoozeLocationReminder(t, newReminderTime);
                else
                    Util.scheduleReminderNotification(t,newReminderTime);
            }

            // Record usage of Android Wear:
            featureUsage.record(FeatureUsage.ANDROID_WEAR);
        }
        else if (action.equals(ACTION_LOG))
        {
            Util.log("From Wearable "+sourceNodeID+": "+b.getString("message"));
        }
        else if (action.equals(ACTION_UPDATE_COMPLETION_STATUS))
        {
            if (b.containsKey("task_id") && b.containsKey("is_completed"))
            {
                TasksDbAdapter tasksDB = new TasksDbAdapter();
                UTLTask t = tasksDB.getTask(b.getLong("task_id"));
                if (t!=null)
                {
                    if (t.completed == b.getBoolean("is_completed"))
                    {
                        // No change.
                        return;
                    }
                    if (b.getBoolean("is_completed"))
                    {
                        Util.markTaskComplete(b.getLong("task_id"));
                    }
                    else
                    {
                        t.completed = false;
                        t.mod_date = System.currentTimeMillis();
                        t.completion_date = 0;
                        tasksDB.modifyTask(t);
                    }
                    Util.instantTaskUpload(this,t);

                    // Record usage of Android Wear:
                    featureUsage.record(FeatureUsage.ANDROID_WEAR);
                }
            }
        }
        else if (action.equals(ACTION_SPEECH_TO_PROCESS))
        {
            // First, verify the user has actually purchased the add-on:
            if (!pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
            {
                b = new Bundle();
                b.putBoolean("is_successful",false);
                b.putString("error_message",getString(R.string.need_to_purchase_wear));
                sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b, sourceNodeID);
                return;
            }

            String speech = b.getString("speech");
            SpeechParser speechParser = SpeechParser.getSpeechParser(this);
            int op = speechParser.getOperationType(speech);
            switch (op)
            {
                case SpeechParser.OP_ADD:
                    UTLTaskSpeech ts = new UTLTaskSpeech();
                    String response = speechParser.parseNewTask(ts,speech);
                    if (response.matches("\\d+"))
                    {
                        // Successful response.  We can go ahead and save the task.

                        ts.task.td_id = -1;
                        ts.task.mod_date = System.currentTimeMillis();
                        ts.task.prev_folder_id = ts.task.folder_id;

                        // Make sure the parent task has not been deleted in a recent sync:
                        TasksDbAdapter tasksDB = new TasksDbAdapter();
                        if (ts.task.parent_id>0)
                        {
                            UTLTask parent = tasksDB.getTask(ts.task.parent_id);
                            if (parent==null)
                            {
                                Util.log("WearService: The task's parent was deleted while the task was being "+
                                        "edited. The task will have no parent.");
                                ts.task.parent_id = 0;
                            }
                        }

                        // Link the task with a calendar entry if needed:
                        if (ts.addToCalendar && (ts.task.due_date>0 || ts.task.start_date>0) &&
                                _settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
                        {
                            CalendarInterface ci = new CalendarInterface(this);
                            String uri = ci.linkTaskWithCalendar(ts.task);
                            if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
                            {
                                ts.task.calEventUri = uri;
                            }
                        }

                        // If the timer has been set, then start it.
                        if (ts.timerSet && ts.willStartTimer)
                        {
                            ts.task.timer_start_time = System.currentTimeMillis();
                            ts.task.timer = 0;
                        }

                        // Add the task to the database:
                        ts.task.uuid = UUID.randomUUID().toString();
                        long taskID = tasksDB.addTask(ts.task);
                        if (taskID==-1)
                        {
                            Util.popup(this,R.string.Cannot_add_task);
                            return;
                        }
                        ts.task._id = taskID;

                        // Record feature usage for the task.
                        featureUsage.recordForTask(ts.task);

                        // For an add operation, we need to update the calendar event's note
                        // to include a link to the newly created task:
                        if (ts.addToCalendar && (ts.task.due_date>0 || ts.task.start_date>0) &&
                                _settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
                        {
                            CalendarInterface ci = new CalendarInterface(this);
                            ci.addTaskLinkToEvent(ts.task);
                        }

                        // Add or update the tags for the task:
                        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && ts.tags!=null &&
                                ts.tags.size()>0)
                        {
                            TagsDbAdapter tagsDB = new TagsDbAdapter();
                            String[] tagArray = Util.iteratorToStringArray(ts.tags.iterator(), ts.tags.size());
                            tagsDB.linkTags(ts.task._id, tagArray);

                            // Make sure the tags are on the recently used tags list:
                            CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
                            currentTags.addToRecent(tagArray);

                            // Record usage of the tags feature:
                            if (!ts.task.completed)
                                featureUsage.record(FeatureUsage.TAGS);
                        }

                        // If the current time zone is different than the home time zone, the
                        // reminder time needs to be offset when comparing it to the current time.
                        TimeZone currentTimeZone = TimeZone.getDefault();
                        TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
                                PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
                        long reminderTime = ts.task.reminder;
                        long oldReminderTime = 0;
                        if (!currentTimeZone.equals(defaultTimeZone))
                        {
                            long difference = currentTimeZone.getOffset(System.currentTimeMillis()) -
                                    defaultTimeZone.getOffset(System.currentTimeMillis());
                            reminderTime = ts.task.reminder - difference;
                        }

                        // If a reminder was set up, then schedule it:
                        if (reminderTime>System.currentTimeMillis() && !ts.task.completed)
                        {
                            Util.scheduleReminderNotification(ts.task);
                        }

                        if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true))
                        {
                            // The instant upload feature is turned on.
                            Util.instantTaskUpload(this, ts.task);
                        }

                        // Record usage of Android Wear:
                        featureUsage.record(FeatureUsage.ANDROID_WEAR);

                        // Send a success message back to the wearable:
                        b = new Bundle();
                        b.putBoolean("is_successful",true);
                        sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b, sourceNodeID);
                    }
                    else
                    {
                        // Couldn't understand the speech, or user tried to create an invalid
                        // task.  Send an error message back to the wearable.
                        b = new Bundle();
                        b.putBoolean("is_successful",false);
                        b.putString("error_message", response);
                        sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b, sourceNodeID);
                    }
                    break;

                case SpeechParser.OP_MODIFY:
                    ts = speechParser.parseTaskUpdate(speech);
                    if (ts.error.length()==0)
                    {
                        // Successful response.

                        ts.task.mod_date = System.currentTimeMillis();

                        // Make sure the parent task has not been deleted in a recent sync:
                        TasksDbAdapter tasksDB = new TasksDbAdapter();
                        if (ts.task.parent_id>0)
                        {
                            UTLTask parent = tasksDB.getTask(ts.task.parent_id);
                            if (parent==null)
                            {
                                Util.log("WearService: The task's parent was deleted while the task was being "+
                                        "edited. The task will have no parent.");
                                ts.task.parent_id = 0;
                            }
                        }

                        // If the timer has been set, then start it.
                        if (ts.timerSet)
                        {
                            if (ts.willStartTimer)
                            {
                                ts.task.timer_start_time = System.currentTimeMillis();
                            }
                            else
                            {
                                // The timer is being stopped:
                                if (ts.task.timer_start_time>0)
                                {
                                    long elapsedTimeMillis = System.currentTimeMillis() - ts.task.timer_start_time;
                                    if (elapsedTimeMillis<0) elapsedTimeMillis=0;
                                    ts.task.timer_start_time = 0;
                                    ts.task.timer += (elapsedTimeMillis/1000);
                                }
                            }
                        }

                        // Modify the task in the database:
                        boolean isSuccessful = tasksDB.modifyTask(ts.task);
                        if (!isSuccessful)
                        {
                            Util.popup(this,R.string.Cannot_modify_task);
                            return;
                        }

                        // Record feature usage for the task.
                        featureUsage.recordForTask(ts.task);

                        // Add or update the tags for the task:
                        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && ts.tags!=null &&
                                ts.tags.size()>0)
                        {
                            TagsDbAdapter tagsDB = new TagsDbAdapter();
                            String[] tagArray = Util.iteratorToStringArray(ts.tags.iterator(), ts.tags.size());
                            tagsDB.linkTags(ts.task._id, tagArray);

                            // Make sure the tags are on the recently used tags list:
                            CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
                            currentTags.addToRecent(tagArray);

                            // Record usage of the tags feature.
                            if (!ts.task.completed)
                                featureUsage.record(FeatureUsage.TAGS);
                        }
                        else if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && ts.tagSet &&
                                ts.tags!=null && ts.tags.size()==0)
                        {
                            // This occurs if the last tag has been deleted.
                            TagsDbAdapter tagsDB = new TagsDbAdapter();
                            tagsDB.linkTags(ts.task._id, new String[] { });
                        }

                        if (ts.completedSet && ts.task.completed)
                        {
                            // The task was just marked as complete.  We need to make additional database updates
                            // as well, such as marking subtasks complete and generating new recurring tasks.
                            Util.markTaskComplete(ts.task._id);
                        }

                        if (ts.reminderSet)
                        {
                            // If the current time zone is different than the home time zone, the
                            // reminder time needs to be offset when comparing it to the current time.
                            TimeZone currentTimeZone = TimeZone.getDefault();
                            TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
                                    PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
                            long reminderTime = ts.task.reminder;
                            long oldReminderTime = ts.priorReminder;
                            if (!currentTimeZone.equals(defaultTimeZone))
                            {
                                long difference = currentTimeZone.getOffset(System.currentTimeMillis()) -
                                        defaultTimeZone.getOffset(System.currentTimeMillis());
                                if (reminderTime>0)
                                    reminderTime = ts.task.reminder - difference;
                                if (oldReminderTime>0)
                                    oldReminderTime = oldReminderTime - difference;
                            }

                            // If a reminder was moved from the past to the future, then remove any
                            // notifications that are displaying, and cancel any nagging alarms:
                            if (reminderTime>System.currentTimeMillis() && oldReminderTime<System.currentTimeMillis())
                            {
                                // Cancel any pending notifications (such as those for nagging):
                                Util.cancelReminderNotification(ts.task._id);

                                // Remove the notification if it is displaying:
                                Util.removeTaskNotification(ts.task._id);
                            }

                            // If a reminder was set up, then schedule it:
                            if (reminderTime>System.currentTimeMillis() && !ts.task.completed)
                            {
                                Util.scheduleReminderNotification(ts.task);
                            }

                            if (ts.task.reminder==0 && ts.priorReminder>0)
                            {
                                // We just removed a reminder.  Cancel the notification:
                                Util.cancelReminderNotification(ts.task._id);
                            }
                        }

                        // If this is a Google account and the folder was changed, we also need to change the
                        // folder of any subtasks.
                        boolean fullSyncStarted = false;
                        UTLAccount acct = (new AccountsDbAdapter()).getAccount(ts.task.account_id);
                        if (acct.sync_service==UTLAccount.SYNC_GOOGLE && ts.folderSet)
                        {
                            changeSubtaskFolders(ts.task);
                            fullSyncStarted = true;
                            Intent i = new Intent(this, Synchronizer.class);
                            i.putExtra("command", "full_sync");
                            i.putExtra("is_scheduled", true);
                            Synchronizer.enqueueWork(this,i);
                        }

                        if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true) && !fullSyncStarted)
                        {
                            // The instant upload feature is turned on.
                            Util.instantTaskUpload(this, ts.task);
                        }

                        // Record usage of Android Wear:
                        featureUsage.record(FeatureUsage.ANDROID_WEAR);

                        // Send a success message back to the wearable:
                        b = new Bundle();
                        b.putBoolean("is_successful",true);
                        sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b,sourceNodeID);
                    }
                    else
                    {
                        // Couldn't understand the speech, or user tried to create an invalid
                        // task.  Send an error message back to the wearable.
                        b = new Bundle();
                        b.putBoolean("is_successful",false);
                        b.putString("error_message",ts.error);
                        sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b,sourceNodeID);
                    }
                    break;

                case SpeechParser.OP_READ:
                    response = speechParser.parseRead(speech);
                    if (response.matches("\\d+"))
                    {
                        // A view ID was returned.  This indicates success.
                        sendTaskList(Long.parseLong(response),true,sourceNodeID);

                        // Record usage of Android Wear:
                        featureUsage.record(FeatureUsage.ANDROID_WEAR);
                    }
                    else
                    {
                        // Couldn't understand the speech, or user specified an invalid
                        // view.
                        b = new Bundle();
                        b.putBoolean("is_successful",false);
                        b.putString("error_message",response);
                        sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b,sourceNodeID);
                    }
                    break;
            }
        }
        else if (action.equals(ACTION_GET_DEFAULT_LIST))
        {
            // First, verify the user has actually purchased the add-on:
            if (!pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
            {
                b = new Bundle();
                b.putBoolean("is_successful",false);
                b.putString("error_message",getString(R.string.need_to_purchase_wear));
                sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE,b,sourceNodeID);
                return;
            }

            Long viewID = getDefaultViewID();
            if (viewID==-1)
            {
                // There's nothing we can do now.
                return;
            }

            // Record usage of Android Wear:
            featureUsage.record(FeatureUsage.ANDROID_WEAR);

            sendTaskList(viewID,false,sourceNodeID);
        }
    }

    /** Send a message. */
    private void sendMessage(String action, Bundle data, String destinationNodeID)
    {
        // Convert the Bundle to a byte array:
        DataMap dataMap = DataMap.fromBundle(data);

        _savedAction = action;

        Util.log("WearService: Sending message to "+destinationNodeID+": "+action);

        Wearable.MessageApi.sendMessage(_googleApiClient, destinationNodeID, action, dataMap.toByteArray()).
            setResultCallback(new ResultCallback<MessageApi.SendMessageResult>()
            {
                @Override
                public void onResult(MessageApi.SendMessageResult result)
                {
                    if (!result.getStatus().isSuccess())
                    {
                        Util.log("WearService: ERROR: failed to send Message: " + result.getStatus());
                    }
                }
            });
    }

    /** Send a task list. */
    private void sendTaskList(long viewID, boolean showTryAgainButton, String destinationNodeID)
    {
        // Fetch the view and initialize the Display Options (which do have an effect on the
        // task order if subtasks are used.)
        _viewID = viewID;
        Cursor viewCursor = (new ViewsDbAdapter()).getView(_viewID);
        if (viewCursor.getCount()==0)
        {
            // View has been deleted.
            viewCursor.close();
            Bundle b = new Bundle();
            b.putBoolean("is_successful", false);
            b.putString("error_message", getString(R.string.No_Tasks_Found));
            if (!showTryAgainButton)
                b.putBoolean("hide_try_again",true);
            sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE, b, destinationNodeID);
            return;
        }
        _displayOptions = new DisplayOptions(Util.cString(viewCursor, "display_string"));

        // Run the database query and get the task list to send back:
        String query = Util.getTaskSqlQuery(_viewID, viewCursor, this);
        runQuery(query);

        viewCursor.close();

        // Make sure we actually have some tasks:
        if (_taskList.size()==0)
        {
            Bundle b = new Bundle();
            b.putBoolean("is_successful", false);
            b.putString("error_message", getString(R.string.No_Tasks_Found));
            if (!showTryAgainButton)
                b.putBoolean("hide_try_again",true);
            sendMessage(ACTION_SPEECH_RECOGNIZER_RESPONSE, b, destinationNodeID);
            return;
        }

        // Populate some arrays containing task data to send to the watch:
        long[] completedArray = new long[_taskList.size()];
        String[] titleArray = new String[_taskList.size()];
        long[] taskIdArray =new long[_taskList.size()];
        for (int i=0; i<_taskList.size(); i++)
        {
            UTLTaskDisplay td = _taskList.get(i).get("task");
            completedArray[i] = td.task.completed ? 1 : 0;
            taskIdArray[i] = td.task._id;
            if (td.task.parent_id>0 && _settings.getBoolean(PrefNames.SUBTASKS_ENABLED,
                true) && _displayOptions.subtaskOption.equals("indented") &&
                !_orphanedSubtaskIDs.contains(td.task._id))
            {
                String indent = "";
                for (int j = 1; j < td.level; j++)
                    indent += "   ";
                titleArray[i] = indent+"- "+td.task.title;
            }
            else
                titleArray[i] = td.task.title;
        }

        // Convert to a Bundle and send:
        Bundle b = new Bundle();
        b.putLongArray("completed_array",completedArray);
        b.putStringArray("title_array",titleArray);
        b.putLongArray("task_id_array",taskIdArray);
        b.putBoolean("is_successful",true);
        sendMessage(ACTION_OPEN_TASK_LIST,b, destinationNodeID);
    }

    // Get the view ID of the task list to send to the wearable.  In the highly unlikely event
    // that a view cannot be found, -1 is returned.
    private long getDefaultViewID()
    {
        ViewsDbAdapter vdb = new ViewsDbAdapter();
        Long viewID = _settings.getLong(PrefNames.WEAR_DEFAULT_VIEW_ID,-1);
        if (viewID==-1)
        {
            // No Android Wear View chosen.  Try the startup view.
            viewID = _settings.getLong(PrefNames.STARTUP_VIEW_ID,-1);
            if (viewID==-1)
            {
                // Still no default view.  Choose the hotlist.
                Cursor c = vdb.getView(ViewNames.HOTLIST,"");
                if (c.moveToFirst())
                {
                    viewID = Util.cLong(c,"_id");
                    c.close();
                }
                else
                {
                    // Can't do anything except ignore the request.
                    c.close();
                    return -1;
                }
            }
        }

        // Make sure the chosen view actually exists.
        Cursor viewCursor = vdb.getView(viewID);
        if (viewCursor.getCount()==0)
        {
            // View has been deleted.  Use the Hotlist.
            Cursor c = vdb.getView(ViewNames.HOTLIST,"");
            if (c.moveToFirst())
            {
                viewID = Util.cLong(c,"_id");
                c.close();
            }
            else
            {
                // Can't do anything except ignore the request.
                c.close();
                return -1;
            }
        }
        viewCursor.close();

        return viewID;
    }

    // Change the folders of child tasks to match the parent task:
    private void changeSubtaskFolders(UTLTask parent)
    {
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = tasksDB.queryTasks("parent_id="+parent._id, null);
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask child = tasksDB.getUTLTask(c);
            child.folder_id = parent.folder_id;
            child.mod_date = System.currentTimeMillis();
            tasksDB.modifyTask(child);
            changeSubtaskFolders(child);
        }
        c.close();
    }

    /** Run the database query that fetches the list of tasks for the view being read. */
    private void runQuery(String query)
    {
        Cursor c = Util.db().rawQuery(query, null);

        // Convert the database query results into a structure that can be re-ordered:
        if (_taskList==null)
            _taskList = new ArrayList<HashMap<String,UTLTaskDisplay>>();
        else
            _taskList.clear();
        c.moveToPosition(-1);
        while(c.moveToNext())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay td = this.cursorToUTLTaskDisplay(c);
            hash.put("task", td);
            _taskList.add(hash);
        }
        c.close();

        // If necessary, reorder the task list to put subtasks below their parents.
        // This function also generates a list of parent tasks with subtasks.
        reorder_task_list();
    }

    // If the task list indents subtasks, we need to reorder the list:
    private void reorder_task_list()
    {
        // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
        // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
        if (_subLists==null)
            _subLists = new HashMap<Long,ArrayList<UTLTaskDisplay>>();
        else
            _subLists.clear();

        // This is an ArrayList of UTLTaskDisplay objects that are NOT subtasks:
        ArrayList<UTLTaskDisplay> parentList = new ArrayList<UTLTaskDisplay>();

        // We also need a hash of all task IDs:
        HashSet<Long> allIDs = new HashSet<Long>();

        // Populate the 3 lists described above:
        Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
            UTLTaskDisplay td = it.next().get("task");
            allIDs.add(td.task._id);
            if (td.task.parent_id==0)
            {
                // Not a subtask:
                parentList.add(td);
            }
            else
            {
                if (!_subLists.containsKey(td.task.parent_id))
                {
                    _subLists.put(td.task.parent_id, new ArrayList<UTLTaskDisplay>());
                }
                _subLists.get(td.task.parent_id).add(td);
            }
        }

        if (_displayOptions.subtaskOption.equals("indented") && _settings.
                getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
            // Subtasks are indented:
            if (_displayOptions.parentOption==1)
            {
                // Orphaned subtasks will not be displayed.
                // Clear out and repopulate the main list for this class:
                _taskList.clear();
                Iterator<UTLTaskDisplay> it2 = parentList.iterator();
                while (it2.hasNext())
                {
                    // Add in the non-subtask:
                    UTLTaskDisplay td = it2.next();
                    td.level = 0;
                    HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
                    hash.put("task", td);
                    _taskList.add(hash);

                    // If this task has any children, then add them in next:
                    if (_subLists.containsKey(td.task._id))
                        addChildTasksToList(td.task._id, 0);
                }
            }
            else
            {
                // Orphaned subtasks will be displayed at the same level as parent
                // tasks.

                ArrayList<HashMap<String,UTLTaskDisplay>> taskList2 =
                        (ArrayList<HashMap<String,UTLTaskDisplay>>)_taskList.clone();
                _taskList.clear();
                Iterator<HashMap<String,UTLTaskDisplay>> it2 = taskList2.iterator();
                while (it2.hasNext())
                {
                    UTLTaskDisplay td = it2.next().get("task");
                    if (td.task.parent_id==0)
                    {
                        // It's not a subtask.  Add it into the final task list:
                        HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
                                UTLTaskDisplay>();
                        td.level = 0;
                        hash.put("task", td);
                        _taskList.add(hash);

                        // If this task has any children, then add them in next:
                        if (_subLists.containsKey(td.task._id))
                            addChildTasksToList(td.task._id, 0);
                    }
                    else if (!allIDs.contains(td.task.parent_id))
                    {
                        // It's an orphaned subtask.  At it into the final list:
                        HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
                                UTLTaskDisplay>();
                        td.level = 0;
                        hash.put("task", td);
                        _taskList.add(hash);
                        _orphanedSubtaskIDs.add(td.task._id);

                        // If this task has any children, then add them in next:
                        if (_subLists.containsKey(td.task._id))
                            addChildTasksToList(td.task._id, 0);
                    }
                }
            }
        }
        else if (_displayOptions.subtaskOption.equals("separate_screen") && _settings.
                getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
            // For this option, subtasks are not displayed at all here, so
            // we need to repopulate the main list with only parent tasks:
            _taskList.clear();
            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
            while (it2.hasNext())
            {
                // Add in the non-subtask:
                UTLTaskDisplay td = it2.next();
                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
                td.level = 0;
                hash.put("task", td);
                _taskList.add(hash);
            }
        }
    }

    // Add child tasks to the list of tasks to display:
    void addChildTasksToList(long taskID, int parentLevel)
    {
        ArrayList<UTLTaskDisplay> childList = _subLists.get(taskID);
        Iterator<UTLTaskDisplay> it3 = childList.iterator();
        while (it3.hasNext())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay child = it3.next();
            child.level = parentLevel+1;
            hash.put("task", child);
            _taskList.add(hash);

            if (_subLists.containsKey(child.task._id))
                addChildTasksToList(child.task._id, parentLevel+1);
        }
    }

    // Given a cursor, get an instance of UTLTaskDisplay:
    private UTLTaskDisplay cursorToUTLTaskDisplay(Cursor c)
    {
        UTLTaskDisplay td = new UTLTaskDisplay();

        // The cursor fields are in the same order that TasksDbAdapter expects, so use
        // the TasksDbAdapter function to get the UTLTask object:
        td.task = (new TasksDbAdapter()).getUTLTask(c);

        td.firstTagName = Util.cString(c, "tag_name");
        td.accountName = Util.cString(c, "account");
        td.folderName = Util.cString(c,"folder");
        td.contextName = Util.cString(c,"context");
        td.goalName = Util.cString(c, "goal");
        td.locationName = Util.cString(c, "location");
        td.numTags = Util.cLong(c, "num_tags");

        // Collaboration - owner:
        td.ownerName = Util.cString(c,"owner_name");
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();

        // Collaboration - Assignor / Added By:
        td.assignorName = Util.cString(c, "assignor_name");

        // Collaboration - Shared With:
        if (td.task.shared_with.length()>0)
        {
            UTLAccount a = (new AccountsDbAdapter()).getAccount(td.task.account_id);
            String[] collIDs = td.task.shared_with.split("\n");
            td.sharedWith = "";
            for (int i=0; i<collIDs.length; i++)
            {
                if (td.sharedWith.length()>0)
                    td.sharedWith += ", ";
                if (collIDs[i].equals(a.td_userid))
                    td.sharedWith += Util.getString(R.string.Myself);
                else
                {
                    UTLCollaborator co = cdb.getCollaborator(td.task.account_id, collIDs[i]);
                    if (co!=null)
                        td.sharedWith += co.name;
                }
            }
        }
        else
            td.sharedWith = Util.getString(R.string.None);

        return(td);
    }

    /** Called when the service is destroyed. */
    public void onDestroy()
    {
        Util.log("WearService: onDestroy() called.");
    }
}
