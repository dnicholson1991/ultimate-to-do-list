package com.customsolutions.android.utl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;

import android.os.Handler;
import android.support.wearable.activity.ConfirmationActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by Nicholson on 12/15/2014.
 */
public class HandsetService extends WearableListenerService {
    // Actions that this service supports.  These must match the corresponding service on the handset:
    final static public String ACTION_OPEN_SNOOZER = "com.customsolutions.android.utl.open_snoozer";
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

    // This string must match res/values/wear.xml in the handset app.
    final static public String UTL_ANDROID_WEAR_SERVICES = "utl_android_wear_services";

    /** The notification channel for the daily summary. */
    public static final String NOTIF_CHANNEL_DAILY_SUMMARY = "notif_channel_daily_summary";

    /** Notification channel for miscellaneous notifications. */
    public static final String NOTIF_CHANNEL_MISC = "notif_channel_misc";

    private static final int NOTIFICATION_ID = 5483;

    // Implements communication:
    GoogleApiClient _googleApiClient;

    boolean _isConnected;

    private Node _connectedNode;

    // Queued message to send when the connection is established.
    Intent _queuedMessage;

    private String _savedAction;
    private Bundle _savedData;

    private boolean _isInForeground = false;
    private Runnable _moveToBackgroundRunnable;
    private Handler _moveToBackgroundHandler;

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.i("Test", "HandsetService: onCreate().");
        _isConnected = false;
        _connectedNode = null;
        _queuedMessage = null;
        initGoogleApiClient();
        createNotificationChannels();

        _moveToBackgroundRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                stopForeground(true);
                _isInForeground = false;
                Log.i("Test","Service is in background.");
            }
        };

        moveToForeground();
    }

    /** Move this service to the foreground. */
    private void moveToForeground()
    {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
            return;

        // This service should not be in the foreground long. Set a timer to move it to the
        // background unless moveToForeground() is called again.
        if (_moveToBackgroundHandler==null)
        {
            _moveToBackgroundHandler = new Handler();
            Log.i("Test","Starting new timer.");
        }
        else
        {
            // Stop the existing timer.
            _moveToBackgroundHandler.removeCallbacks(_moveToBackgroundRunnable);
            Log.i("Test","resetting timer.");
        }
        _moveToBackgroundHandler.postDelayed(_moveToBackgroundRunnable,3000);

        if (_isInForeground)
            return;

        // Create the notification and set some options:
        NotificationCompat.Builder n = new NotificationCompat.Builder(this,
            NOTIF_CHANNEL_MISC)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.drawable.icon))
            .setPriority(Notification.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentTitle(getString(R.string.syncing_with_phone))
            ;

        // Move the service to the foreground:
        startForeground(NOTIFICATION_ID,n.build());
        Log.v("Test","Service Moved to Foreground.");
        _isInForeground = true;
    }

    @Override
    public void onPeerConnected(Node peer)
    {
        Log.i("Test","onPeerConnected() called.");

        // Make sure the node that was connected is the handset.  This is done by checking the node's
        // capabilities.
        CapabilityApi.GetCapabilityResult result = Wearable.CapabilityApi.getCapability(_googleApiClient,
            UTL_ANDROID_WEAR_SERVICES,CapabilityApi.FILTER_REACHABLE).await();
        Set<Node> capableNodes = result.getCapability().getNodes();
        if (capableNodes==null || capableNodes.size()==0)
        {
            Log.i("Test","No nodes found that support UTL Wear services.");
        }
        else if (capableNodes.size()==1)
        {
            _connectedNode = capableNodes.iterator().next();
            Log.i("Test","Found a node that supports UTL Wear services.");
            Util.log("HandsetService: Found a node that supports UTL Wear services: "+_connectedNode.getId(),this);
        }
        else
        {
            // More than one node that supports UTL Wear services.  Very strange.
            Log.i("Test","WARNING: Found "+capableNodes.size()+" nodes that support UTL Wear services. "+
                "Picking the first one: "+_connectedNode.getId());
            _connectedNode = capableNodes.iterator().next();
            Util.log("HandsetService: WARNING: Found "+capableNodes.size()+" nodes that support UTL Wear services. "+
                "Picking the first one: "+_connectedNode.getId(),this);
            Iterator it = capableNodes.iterator();
            while (it.hasNext())
            {
                Util.log("HandsetService:   - Node ID: "+it.next(),HandsetService.this);
            }
        }

        if (_connectedNode!=null)
        {
            // Log my Node ID:
            Wearable.NodeApi.getLocalNode(_googleApiClient).setResultCallback(new ResultCallback
                <NodeApi.GetLocalNodeResult>()
            {
                @Override
                public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult)
                {
                    Node n = getLocalNodeResult.getNode();
                    Util.log("HandsetService: My Node ID: " + n.getId(),HandsetService.this);

                    // Also log the version code:
                    String pkg = HandsetService.this.getPackageName();
                    try
                    {
                        int versionCode = HandsetService.this.getPackageManager().getPackageInfo(pkg, 0).versionCode;
                        Util.log("HandsetService: Version Code: "+versionCode,HandsetService.this);
                    }
                    catch (PackageManager.NameNotFoundException e) { }  // Not gonna happen.
                }
            });
        }
    }

    public void initGoogleApiClient()
    {
        if (!_isConnected)
        {
            _googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks()
                {
                    @Override
                    public void onConnected(Bundle connectionHint)
                    {
                        Util.dlog("HandsetService: onConnected: " + connectionHint);
                        // Now you can use the Data Layer API
                        _isConnected = true;

                        if (_queuedMessage != null)
                        {
                            sendMessage(_queuedMessage.getAction(), _queuedMessage.getExtras());
                            _queuedMessage = null;
                        }
                        else
                        {
                            // For logging purposes, send the handset a message.
                            Bundle b = new Bundle();
                            b.putString("message", "HandsetService: Connection Established");
                            sendMessage(ACTION_LOG, b);
                        }
                    }

                    @Override
                    public void onConnectionSuspended(int cause)
                    {
                        Util.dlog("HandsetService: onConnectionSuspended: " + cause);
                        _isConnected = false;
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener()
                {
                    @Override
                    public void onConnectionFailed(ConnectionResult result)
                    {
                        Util.dlog("HandsetService: onConnectionFailed: " + result.getErrorCode() + " / "+
                            result.toString());
                        _isConnected = false;

                        if (result.getErrorCode()==ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED)
                        {
                            // The device is using an out of date version of Google Play Services
                            Intent i = new Intent(HandsetService.this,VoiceCommandError.class);
                            Bundle b = new Bundle();
                            b.putString("error_message",HandsetService.this.getString(R.string.play_services_out_of_date));
                            b.putBoolean("hide_try_again",true);
                            i.putExtras(b);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        }
                    }
                })
                .addApi(Wearable.API) // Request access only to the Wearable API
                .build();
            _googleApiClient.connect();
        }
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        if (intent==null || intent.getAction()==null || intent.getAction().length()==0)
        {
            return super.onStartCommand(intent, flags, startId);
        }

        Util.dlog("HandsetService: onStartCommand: "+intent.getAction());
        moveToForeground();

        try
        {
            if (!_isConnected) {
                Bundle b = intent.getExtras();
                if (b.containsKey("queue") && b.getBoolean("queue"))
                {
                    // The caller wants to send the message after a connection is established.
                    Log.i("Test", "HandsetService: Queuing message due to no connection.");
                    _queuedMessage = intent;
                }
                else
                {
                    Log.i("Test", "HandsetService: Cannot send message to device.  No connection.");
                }
            }
            else
                sendMessage(intent.getAction(),intent.getExtras());
        }
        finally
        {
            return super.onStartCommand(intent, flags, startId);
        }
    }

    /** Create the needed notification channels. Does nothing if the channels exist. */
    private void createNotificationChannels()
    {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
            return;

        // Daily summary channel:

        NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_DAILY_SUMMARY,
            getString(R.string.daily_task_summary),NotificationManager.IMPORTANCE_DEFAULT);

        // Vibration Pattern:
        String vibeString = "0,200,100,200,100,200";
        String[] vibeItems = vibeString.split(",");
        long[] vibePattern = new long[vibeItems.length];
        for (int j=0; j<vibeItems.length; j++)
        {
            vibePattern[j] = Long.parseLong(vibeItems[j]);
        }
        channel.enableVibration(true);
        channel.setVibrationPattern(vibePattern);

        // Sound (none):
        channel.setSound(null,null);

        // Other options:
        channel.enableLights(false);
        channel.setShowBadge(false);
        NotificationManager notifManager = getSystemService(NotificationManager.class);
        if (notifManager==null)
        {
            Log.i("Test","WARNING: NotificationManager is null.");
            return;
        }
        notifManager.createNotificationChannel(channel);


        // Miscellaneous:

        channel = new NotificationChannel(NOTIF_CHANNEL_MISC,
            getString(R.string.misc),NotificationManager.IMPORTANCE_MIN);

        // Vibration Pattern (none):
        channel.enableVibration(false);

        // Sound (none):
        channel.setSound(null,null);

        // Other options:
        channel.enableLights(false);
        channel.setShowBadge(false);
        if (notifManager==null)
        {
            Log.i("Test","WARNING: NotificationManager is null.");
            return;
        }
        notifManager.createNotificationChannel(channel);

        Log.i("Test","Notification channels created.");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent)
    {
        // Get the action and the data from the message.
        String action = messageEvent.getPath();
        Log.i("Test","Got a message: "+action);
        byte[] data = messageEvent.getData();
        Bundle b = new Bundle();
        if (data!=null)
        {
            DataMap dataMap = DataMap.fromByteArray(data);
            b = dataMap.toBundle();
        }
        moveToForeground();

        if (action.equals(ACTION_OPEN_SNOOZER))
        {
            Intent snoozeIntent = new Intent(this,Snoozer.class);
            snoozeIntent.putExtras(b);
            snoozeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(snoozeIntent);
            return;
        }

        if (action.equals(ACTION_SPEECH_RECOGNIZER_RESPONSE))
        {
            if (b.getBoolean("is_successful"))
            {
                // Check to see if this was a read command.  If so, open the task list.
                if (b.containsKey("title_array"))
                {
                    Intent taskListIntent = new Intent(this,TaskList.class);
                    taskListIntent.putExtras(b);
                    taskListIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(taskListIntent);
                    return;
                }

                // All we need to do is display a confirmation animation.
                Intent confirmIntent = new Intent(this, ConfirmationActivity.class);
                confirmIntent.putExtra(ConfirmationActivity.EXTRA_MESSAGE,getString(R.string.success));
                confirmIntent.putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE,ConfirmationActivity.SUCCESS_ANIMATION);
                confirmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(confirmIntent);
            }
            else
            {
                // Call the error handler activity:
                Intent i = new Intent(this,VoiceCommandError.class);
                i.putExtras(b);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            }
            return;
        }

        if (action.equals(ACTION_OPEN_TASK_LIST))
        {
            Intent taskListIntent = new Intent(this,TaskList.class);
            taskListIntent.putExtras(b);
            taskListIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(taskListIntent);
            return;
        }

        if (action.equals(ACTION_SHOW_DAILY_SUMMARY))
        {
            // Generate a notification containing the daily summary info.
            Log.i("Test","Action is daily summary.");
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                NOTIF_CHANNEL_DAILY_SUMMARY)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(b.getString("line_1"))
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setCategory(Notification.CATEGORY_REMINDER);
            if (b.containsKey("line_2"))
                builder.setContentText(b.getString("line_2"));

            // This Intent launches an activity that is responsible for fetching the task list.
            Intent getListIntent = new Intent(this,HandsetService.class);
            getListIntent.setAction(ACTION_GET_DEFAULT_LIST);
            getListIntent.putExtra("queue",true);
            PendingIntent getListPendingIntent = PendingIntent.getService(this, 0, getListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Add an action to the notification for opening the list:
            builder.setContentIntent(getListPendingIntent);

            // Add in vibration:
            builder.setDefaults(Notification.DEFAULT_VIBRATE);

            // Show the notification:
            NotificationManager nm = (NotificationManager)getSystemService(Context.
                NOTIFICATION_SERVICE);
            nm.notify(100,builder.build());
            Log.i("Test","Displayed daily summary.");
        }
    }

    private void sendMessage(String action, Bundle data)
    {
        if (_connectedNode!=null)
        {
            sendMessage2(action,data);
            return;
        }

        _savedAction = action;
        _savedData = data;

        // We haven't yet been notified about a connected node.  Try explicitly searching for one.
        Wearable.CapabilityApi.getCapability(_googleApiClient,UTL_ANDROID_WEAR_SERVICES,CapabilityApi.
            FILTER_REACHABLE).setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>()
        {
            @Override
            public void onResult(CapabilityApi.GetCapabilityResult result)
            {
                Set<Node> capableNodes = result.getCapability().getNodes();
                if (capableNodes==null || capableNodes.size()==0)
                {
                    Log.i("Test","No nodes found that support UTL Wear services.");
                    if (!_savedAction.equals(ACTION_LOG))
                    {
                        Intent i = new Intent(HandsetService.this, VoiceCommandError.class);
                        Bundle b = new Bundle();
                        b.putString("error_message", HandsetService.this.getString(R.string.utl_not_installed));
                        b.putBoolean("hide_try_again", true);
                        i.putExtras(b);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                }
                else if (capableNodes.size()==1)
                {
                    _connectedNode = capableNodes.iterator().next();
                    sendMessage2(_savedAction,_savedData);
                    Util.log("HandsetService: Found a node that supports UTL Wear services: " + _connectedNode.getId(),
                        HandsetService.this);
                }
                else
                {
                    // More than one node that supports UTL Wear services.  Very strange.
                    Log.i("Test","WARNING: Found "+capableNodes.size()+" nodes that support UTL Wear services. "+
                        "Picking the first one.");
                    _connectedNode = capableNodes.iterator().next();
                    sendMessage2(_savedAction,_savedData);
                    Util.log("HandsetService: WARNING: Found "+capableNodes.size()+" nodes that support UTL Wear services. "+
                        "Picking the first one: "+_connectedNode.getId(),HandsetService.this);
                    Iterator it = capableNodes.iterator();
                    while (it.hasNext())
                    {
                        Util.log("HandsetService:   - Node ID: "+it.next(),HandsetService.this);
                    }
                }
            }
        });

    }

    private void sendMessage2(String action, Bundle data)
    {
        // Convert the Bundle to a byte array:
        DataMap dataMap = DataMap.fromBundle(data);

        Wearable.MessageApi.sendMessage(_googleApiClient, _connectedNode.getId(), action, dataMap.toByteArray()).
            setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                @Override
                public void onResult(MessageApi.SendMessageResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Util.dlog("ERROR: failed to send Message: " + result.getStatus());
                    } else
                        Util.dlog("HandsetService: Message Sent");
                }
            });
    }
}
