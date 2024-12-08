package com.customsolutions.android.utl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.LocationManager;
import android.net.Uri;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;

import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

/** This receives notification about a user entering a defined location. It displays notifications
 * for relevant tasks if any of them are for that location and have location reminders enabled. */
public class GeofenceReceiver extends BroadcastReceiver
{
    private static final String TAG = "GeofenceReceiver";

    @Override
    public void onReceive(Context c, Intent i)
    {
        // Initialize the app if it has not been:
        Util.appInit(c);
        log("Intent received: "+Util.intentToString(i,2));
        if (i==null)
        {
            log("Ignoring null intent.");
            return;
        }
        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);

        GeofencingEvent event = GeofencingEvent.fromIntent(i);
        if (event.hasError())
        {
            // Note: code 1000 is sent when user turns off location services.
            log("WARNING: Geofencing error: "+event.getErrorCode());
            if (event.getErrorCode()== GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
            {
                prefs.edit().putBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED, true).apply();
            }
            return;
        }

        // A valid event means Geofencing is working. Make sure the corresponding preference is
        // set.
        prefs.edit().putBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED, false).apply();

        // Make sure the type is an entry event. That's what we're interested in.
        if (event.getGeofenceTransition()==Geofence.GEOFENCE_TRANSITION_ENTER)
        {
            List<Geofence> geofences = event.getTriggeringGeofences();
            LocationsDbAdapter locDB = new LocationsDbAdapter();
            for (Geofence geofence : geofences)
            {
                Long locID = Long.parseLong(geofence.getRequestId());
                UTLLocation l = locDB.getLocation(locID);
                if (l!=null)
                {
                    log("Entered location "+l._id+" / "+l.title);
                    l.at_location = UTLLocation.YES;
                    locDB.modifyLocation(l);
                    showNotificationsForLocationReminders(c,l);
                }
                else
                {
                    log("WARNING: The location with ID "+locID+" is not in the database.");
                }
            }
        }
        else if (event.getGeofenceTransition()== Geofence.GEOFENCE_TRANSITION_DWELL)
        {
            // The user has been at the same location for a while. This can also be triggered
            // when the app is initialized. Make sure to record that the user is at the
            // location.
            List<Geofence> geofences = event.getTriggeringGeofences();
            LocationsDbAdapter locDB = new LocationsDbAdapter();
            for (Geofence geofence : geofences)
            {
                Long locID = Long.parseLong(geofence.getRequestId());
                UTLLocation l = locDB.getLocation(locID);
                if (l!=null)
                {
                    log("Dwelled at location "+l._id+" / "+l.title);
                    l.at_location = UTLLocation.YES;
                    locDB.modifyLocation(l);
                }
                else
                {
                    log("WARNING: The location with ID "+locID+" is not in the database.");
                }
            }
        }
        else if (event.getGeofenceTransition()==Geofence.GEOFENCE_TRANSITION_EXIT)
        {
            List<Geofence> geofences = event.getTriggeringGeofences();
            LocationsDbAdapter locDB = new LocationsDbAdapter();
            for (Geofence geofence : geofences)
            {
                Long locID = Long.parseLong(geofence.getRequestId());
                UTLLocation l = locDB.getLocation(locID);
                if (l!=null)
                {
                    log("Exited location "+l._id+" / "+l.title);
                    l.at_location = UTLLocation.NO;
                    locDB.modifyLocation(l);
                }
                else
                {
                    log("WARNING: The location with ID "+locID+" is not in the database.");
                }
            }
        }
    }

    /** Show notifications for task reminders associated with a location. */
    private void showNotificationsForLocationReminders(Context context, UTLLocation l)
    {
        // Get a list of all incomplete tasks associated with the location:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = tasksDB.queryTasks("location_id="+l._id+" and completed=0 and "+
            "location_reminder=1",null);
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);

            // If the start date is in the future, do not display anything:
            TimeZone sysTimeZone = TimeZone.getDefault();
            TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
                PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
            long timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) -
                appTimeZone.getOffset(System.currentTimeMillis());
            if (t.start_date>System.currentTimeMillis()+timeZoneOffset)
                continue;

            // Create an intent that will be sent to the Notifier broadcast receiver:
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.scheme("viewtask");
            uriBuilder.opaquePart(Long.valueOf(t._id).toString());
            Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,Notifier.class);
            i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);

            // Send the intent:
            Util.log("LocationChecker: Displaying location reminder for task '"+t.title+"'");
            context.sendBroadcast(i);
        }
        c.close();
    }

    /** General logging function. */
    private void log(String msg)
    {
        Log.v(TAG,msg);
    }
}
