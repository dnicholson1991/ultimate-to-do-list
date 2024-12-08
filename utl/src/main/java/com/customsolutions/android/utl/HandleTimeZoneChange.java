package com.customsolutions.android.utl;

//When the time zone changes, we need to reschedule all of the reminders.

import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class HandleTimeZoneChange extends BroadcastReceiver
{

	@Override
	public void onReceive(Context context, Intent intent)
	{
		// Initialize the app if it has not been:
		Util.appInit(context);
		Util.log("The time zone has changed.  Rescheduling reminders.  New Zone: "+
			TimeZone.getDefault().getID());
		
		// Reschedule the reminders:
		Util.refreshTaskReminders();
	}

}
