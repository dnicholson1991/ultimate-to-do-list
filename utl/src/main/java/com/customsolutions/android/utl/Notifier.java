package com.customsolutions.android.utl;

// This class receives scheduled Intents that correspond to task reminders/alarms.
// It then displays a notification to the user.

// The intent passed in has the following data:
// action: Set this to Intent.ACTION_VIEW
// data: This is an opaque URI defined as follows:
//     viewtask:<UTL task ID>
//         scheme is "viewtask"
//         opaque part is the task ID

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.WearableExtender;

import java.util.List;
import java.util.TimeZone;

public class Notifier extends BroadcastReceiver
{
	private static final String TAG = "Notifier";

	@SuppressLint("InlinedApi")
	@Override
	public void onReceive(Context c, Intent i)
	{
		// Initialize the app if it has not been:
		Util.appInit(c);		
		
		// We only respond to the view action:
		if (i.getAction().equals(Intent.ACTION_VIEW))
		{
			// We only respond to the "viewtask" URI scheme.
			Uri uri = i.getData();
			if (uri.getScheme().equals("viewtask"))
			{
				// Pull out the task ID from the Intent
				String taskIdString = uri.getEncodedSchemeSpecificPart();
				long taskID = Long.parseLong(taskIdString);
				Util.log("Notifier: onReceive() called for task ID: "+taskID);
				if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
				{
					Log.v(TAG, "Has notification permission? "+Util.arePermissionsGranted(c,
						new String[] {Manifest.permission.POST_NOTIFICATIONS}));
				}
				
				// Determine if this is a location based reminder (versus a time based
				// one):
				boolean isLocation = false;
				Bundle b = i.getExtras();
				if (b!=null && b.containsKey(LocationManager.KEY_PROXIMITY_ENTERING))
				{
					if (b.getBoolean(LocationManager.KEY_PROXIMITY_ENTERING)==false)
					{
						// The user is leaving a location.  We don't do anything here.
						return;
					}
					isLocation = true;
				}
				
				// Define an Intent that will be passed to the task viewer if the user taps on a
				// notification:
				Class<?> viewerClass = ViewTaskPopup.class;
				int screenSize = c.getResources().getConfiguration().screenLayout & Configuration.
					SCREENLAYOUT_SIZE_MASK;
				Util.log("Screen size: "+screenSize);
				if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
					screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL ||
					screenSize==Configuration.SCREENLAYOUT_SIZE_UNDEFINED)
				{
					// On smaller screens, don't use the ViewTaskPopup class, since it can cause
					// some display issues on certain devices.
					viewerClass = ViewTask.class;
				}
				Intent viewerIntent = new Intent(Intent.ACTION_VIEW,uri,c,viewerClass);
				viewerIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
				viewerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				viewerIntent.putExtra("notification_time", System.currentTimeMillis());
				viewerIntent.putExtra("is_location", isLocation);
				
				// Wrap the intent in the required PendingIntent object:
				PendingIntent pi = PendingIntent.getActivity(c, 0, viewerIntent, PendingIntent
					.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
				
				boolean hasSem = Util.acquireSemaphore("Notifier", c);
				try {
                    TasksDbAdapter tasksDB = new TasksDbAdapter();
                    UTLTask t = tasksDB.getTask(taskID);
                    if (t == null) {
                        Util.log("Notifier was called for nonexisting task ID " + taskID);
                        return;
                    }
                    Util.log("Notifier: Task Title: " + t.title);

                    if (t.completed) {
                        // This is backup code, to absolutely ensure that we don't display
                        // a notification for a completed task.
                        return;
                    }

                    // If this account has notifications turned off, then we do nothing:
                    UTLAccount acct = (new AccountsDbAdapter()).getAccount(t.account_id);
                    if (!acct.enable_alarms && !isLocation)
                        return;
                    if (isLocation && !acct.enable_loc_alarms)
                        return;

                    // If the user has the feature disabled, then do nothing:
                    SharedPreferences settings = c.getSharedPreferences(Util.PREFS_NAME, 0);
                    if (!isLocation && !settings.getBoolean("reminder_enabled", true)) {
                        return;
                    }
                    if (isLocation && !settings.getBoolean("locations_enabled", true)) {
                        return;
                    }

                    // If this is a location reminder and it has a start date in the future,
                    // then do not display anything:
                    if (isLocation) {
                        TimeZone sysTimeZone = TimeZone.getDefault();
                        TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
                                "home_time_zone", "America/Los_Angeles"));
                        long timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) -
                                appTimeZone.getOffset(System.currentTimeMillis());
                        if (t.start_date > System.currentTimeMillis() + timeZoneOffset)
                            return;
                    }

                    // Set the contents of the notification's 2nd line (due and nag setting),
                    // along with the small icon resource.
                    String extraLine = "";
                    int smallIconResource;
                    if (!isLocation) {
                        smallIconResource = R.drawable.notification_alarm;
                        if (t.due_date > 0 && t.uses_due_time && settings.getBoolean(
                                "due_time_enabled", true)) {
                            extraLine += Util.getString(R.string.Due_) + " " + Util.getDateTimeString
                                    (t.due_date);
                        } else if (t.due_date > 0) {
                            extraLine += Util.getString(R.string.Due_) + " " + Util.getDateString
                                    (t.due_date);
                        }
                        if (t.nag)
                            extraLine += " " + Util.getString(R.string.Nagging_Reminder) + " ";
                    } else {
                        smallIconResource = R.drawable.notification_location;
                        UTLLocation loc = (new LocationsDbAdapter()).getLocation(t.location_id);
                        if (loc != null)
                            extraLine += Util.getString(R.string.Location_) + " " + loc.title;
                        if (t.location_nag)
                            extraLine += " " + Util.getString(R.string.Nagging_Reminder) + " ";
                    }

                    // Get the notification channel ID to use:
					String channelID = "";
					NotificationManager nm = (NotificationManager)c.getSystemService(Context.
						NOTIFICATION_SERVICE);
                    if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
					{
						List<NotificationChannel> channels = nm.getNotificationChannels();
						if (channels!=null && channels.size()>0)
						{
							for (NotificationChannel channel : channels)
							{
								if (channel.getId().startsWith(Util.NOTIF_CHANNEL_REMINDER_PREFIX))
								{
									channelID = channel.getId();
									break;
								}
							}
						}
						else
							Util.log("WARNING: No notification channels found.");
					}

                    // Create a Notification that will be displayed:
                    NotificationCompat.Builder n = new NotificationCompat.Builder(c,channelID);
                    n.setContentTitle(t.title).setContentText(extraLine).setContentIntent(pi);
                    n.setTicker(t.title);
                    n.setSmallIcon(smallIconResource);
                    n.setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.drawable.icon));
                    n.setCategory(NotificationCompat.CATEGORY_REMINDER);
                    int notificationDefaults = 0;

                    // Set the notification priority and the ongoing option based on the user's preference:
                    n.setPriority(settings.getInt(PrefNames.NOTIFICATION_PRIORITY, 2) - 2);
                    n.setOngoing(settings.getBoolean(PrefNames.USE_ONGOING_NOTIFICATIONS, false));

                    // Add an action button for snoozing the reminder:
                    Intent snoozeIntent = new Intent("com.customsolutions.android.utl.snooze", uri, c,
                            SnoozePopup.class);
                    if (isLocation)
                        snoozeIntent.putExtra("is_location", true);
                    snoozeIntent.putExtra("task_id", t._id);
                    snoozeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    snoozeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    n.addAction(R.drawable.ab_timer_off_dark, c.getString(R.string.Snooze), PendingIntent.
                        getActivity(c, 0, snoozeIntent, PendingIntent.FLAG_CANCEL_CURRENT
						| PendingIntent.FLAG_IMMUTABLE));

                    // Add an action button for marking the task complete:
                    Intent completionIntent = new Intent(c,TaskUpdateReceiver.class);
                    completionIntent.setAction("com.customsolutions.android.utl.TaskUpdateReceiver." +
                        "ACTION_COMPLETE");
                    completionIntent.setData(Uri.parse(
                        "content://com.customsolutions.android.utl.purecalendarprovider/tasks/" + t._id));
                    completionIntent.putExtra("from_notification", true);
                    PendingIntent completionPendingIntent = PendingIntent.
                        getBroadcast(c, 0, completionIntent, PendingIntent.FLAG_CANCEL_CURRENT
						| PendingIntent.FLAG_IMMUTABLE);
                    n.addAction(R.drawable.ab_save_dark, c.getString(R.string.Mark_Complete), completionPendingIntent);

                    // Add wearable only actions:
                    PurchaseManager pm = new PurchaseManager(c);
                    if (Util.canUseAndroidWear() && pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
                    {
                        WearableExtender wearableExtender = new WearableExtender();

                        // For snoozing:
                        Intent wearableSnoozeIntent = new Intent(WearService.ACTION_OPEN_SNOOZER,
							uri, c, WearService.class);
                        wearableSnoozeIntent.putExtra("task_id", t._id);
                        wearableSnoozeIntent.putExtra("is_location", isLocation);
                        PendingIntent wearableSnoozePI = PendingIntent.getService(c,
							Long.valueOf(t._id).intValue(), wearableSnoozeIntent,
							PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        NotificationCompat.Action snoozeAction = new NotificationCompat.Action.
							Builder(R.drawable.ab_timer_off_dark, c.getString(R.string.Snooze), wearableSnoozePI).
							build();
                        // Note that different request codes are used to ensure a new PendingIntent is generated every time.
                        wearableExtender.addAction(snoozeAction);

                        // For marking the task as complete:
                        NotificationCompat.Action completeAction = new NotificationCompat.Action.Builder(R.drawable.ab_save_dark,
                            c.getString(R.string.Mark_Complete), completionPendingIntent).build();
                        wearableExtender.addAction(completeAction);

                        n.extend(wearableExtender);
                    }

                    // Set the light color:
					if (settings.getInt("reminder_light", 0)>0)
					{
						// Get the ARGB pattern and update the notification:
						int[] argbArray = c.getResources().getIntArray(R.array.
							argb_light_colors);
						n.setLights(argbArray[settings.getInt(PrefNames.REMINDER_LIGHT, 1)], 1, 0);
					}
					
					// Set the ringtone:
					String ringtoneString = settings.getString(PrefNames.RINGTONE, "Default");
					if (ringtoneString.equals("Default"))
					{
						Uri ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
						if (ringtoneUri==null)
						{
							// We must be running on the emulator, which has no default, and which
							// causes a crash if we try to play the default sound.
							// Don't add the default sound here.
						}
						else
						{
							notificationDefaults = notificationDefaults | Notification.DEFAULT_SOUND;
						}
					}
					else if (ringtoneString.length()>0)
					{
						// An explicit ringtone was specified.
						n.setSound(Uri.parse(ringtoneString));
					}
					
					// Set the vibrate pattern:
					String vibeString = settings.getString("vibe_pattern", "Default");				
					if (vibeString.equals("Default"))
					{
						// Use the default vibe pattern:
						notificationDefaults = notificationDefaults | Notification.DEFAULT_VIBRATE;
					}
					else if (vibeString.length()>0)
					{
						String[] vibeItems = vibeString.split(",");
						long[] vibePattern = new long[vibeItems.length];
						for (int j=0; j<vibeItems.length; j++)
						{
							vibePattern[j] = Long.parseLong(vibeItems[j]);
						}
						n.setVibrate(vibePattern);
					}
					
					// Set the notification defaults:
					if (notificationDefaults!=0)
						n.setDefaults(notificationDefaults);
					
					// Actually display the notification:

					nm.notify(Integer.parseInt(Long.valueOf(taskID).toString()), n.build());
					
					// If the nag setting is in place, then we schedule another reminder:
					if ((!isLocation && t.nag) || (isLocation && t.location_nag))
					{
						pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_CANCEL_CURRENT
							| PendingIntent.FLAG_IMMUTABLE);
						Util.setExactAlarm(c,pi,System.currentTimeMillis()+
							settings.getInt(PrefNames.NAG_INTERVAL,15)*60*1000);
						Util.log("Notifier: Next Nagging Reminder: "+Util.getDateTimeString(
							System.currentTimeMillis()+settings.getInt("nag_interval",15)*60*1000));
					}
				}
				finally
				{
					if (hasSem)
						Util._semaphore.release();
				}
			}
		}
	}

}
