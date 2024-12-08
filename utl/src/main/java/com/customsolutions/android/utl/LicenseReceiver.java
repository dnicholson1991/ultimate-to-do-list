package com.customsolutions.android.utl;

// This receives an Intent from the license app to mark the app as purchased.
// This must include a Bundle with the key "license_config" that has the secret code 
// below.

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class LicenseReceiver extends BroadcastReceiver
{
	PurchaseManager _pm;
	
	@Override
	public void onReceive(Context context, Intent intent)
	{
		Util.appInit(context);
		Util.log("Calling LicenseReceiver.onReceive().");
		
		if (_pm==null)
			_pm = new PurchaseManager(context);
		
		// Verify the required data was passed in:
		Bundle b = intent.getExtras();
		if (b==null)
		{
			Util.log("LicenseReceiver got null Bundle.");
			return;
		}
		
		if (b.containsKey(Util.LICENSE_CONFIG))
		{
			if (b.getLong(Util.LICENSE_CONFIG)!=Util.LICENSE_RECEIVER_SECRET_CODE)
			{
				Util.log(Util.getString(R.string.licCheck8));
				return;
			}
			
			// Unlock the app:
			_pm.setAppStatus(true);
			Util.logOneTimeEvent(context, Util.PURCHASE, 0, new String[] {Util.LICENSE_APP});
    		Util.updatePref(PrefNames.LICENSE_CHECK_FAILURES,0);
		}
		else
		{
			Util.log("Did not get any required keys in LicenseReceiver");
		}
	}

}
