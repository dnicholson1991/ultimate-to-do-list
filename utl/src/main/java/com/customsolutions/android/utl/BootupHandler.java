package com.customsolutions.android.utl;

// This class is designed to run when the system boots up.

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootupHandler extends BroadcastReceiver
{
	@Override
	public void onReceive(Context c, Intent i)
	{
		// Initialize the app if it has not been:
		Util.appInit(c);	
		boolean hasSem = Util.acquireSemaphore("BootupHandler", c);
		try
		{
			Util.log("BootupHandler called.  Task reminders are being refreshed. "+
				i.getAction());
			
			Util.refreshTaskReminders();
			Util.refreshSystemAlarms(c);
		}
		finally
		{
			if (hasSem)
				Util._semaphore.release();
		}
	}

}
