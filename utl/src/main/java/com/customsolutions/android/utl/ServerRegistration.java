package com.customsolutions.android.utl;

// This class is a background service that attempts to get the installation date of 
// the software from our server.  This should be called repeatedly (via Android system
// alarms) until the install date is successfully retrieved.  Once it has been retrieved,
// this class will cancel further alarms.

// In addition to the installation date, this also gets a user ID, device ID, and install ID.

import android.app.IntentService;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;

import com.android.installreferrer.api.InstallReferrerClient;
import com.android.installreferrer.api.InstallReferrerStateListener;
import com.android.installreferrer.api.ReferrerDetails;

import org.json.JSONException;
import org.json.JSONObject;

public class ServerRegistration extends IntentService
{
	private static final String TAG = "ServerRegistration";

	private PurchaseManager _pm;
	
	public ServerRegistration()
	{
		super("ConfigManager");
	}

	/** Flag indicating if we have received a response to the request for the install referrer. */
	private boolean _receivedInstallReferrer;

	@Override
	protected void onHandleIntent(Intent arg0)
	{
		Util.appInit(this);

		// Catch any uncaught exceptions:
		Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));

		Util.log("Checking our server for the user's installation date.");

		if (_pm == null)
			_pm = new PurchaseManager(this);

		// Call the Google Play install referrer API to get referrer data.
		final InstallReferrerClient referrerClient = InstallReferrerClient.newBuilder(this).build();
		referrerClient.startConnection(new InstallReferrerStateListener()
		{
			@Override
			public void onInstallReferrerSetupFinished(int responseCode)
			{
				// Block duplicate calls:
				if (_receivedInstallReferrer)
					return;
				_receivedInstallReferrer = true;

				switch (responseCode)
				{
					case InstallReferrerClient.InstallReferrerResponse.OK:
						// Connection established.
						try
						{
							ReferrerDetails response = referrerClient.getInstallReferrer();
							Log.v(TAG,"Got Install Referrer: "+response.getInstallReferrer());
							serverRegistration(response.getInstallReferrer());
						}
						catch (RemoteException e)
						{
							Log.e(TAG,"Install Referrer Error","Got install referrer error "+
								"RemoteException.",e);
							serverRegistration(null);
						}
						break;

					case InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED:
						// API not available on the current Play Store app.
						if (!Util.IS_AMAZON)
						{
							Log.e(TAG, "Install Referrer Error", "Got install referrer error " +
								"FEATURE_NOT_SUPPORTED.");
						}
						else
						{
							Log.d(TAG, "Got install referrer error FEATURE_NOT_SUPPORTED.");
						}
						serverRegistration(null);
						break;

					case InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE:
						// Connection couldn't be established.
						Log.e(TAG,"Install Referrer Error","Got install referrer error "+
							"SERVICE_UNAVAILABLE.");
						serverRegistration(null);
						break;

					default:
						Log.e(TAG,"Install Referrer Error","Got install referrer error "+
							responseCode);
						serverRegistration(null);
						break;
				}
				referrerClient.endConnection();
			}

			@Override
			public void onInstallReferrerServiceDisconnected()
			{
				// Block duplicate calls:
				if (_receivedInstallReferrer)
					return;
				_receivedInstallReferrer = true;
				Log.e(TAG,"Install Referrer Disconnected","The install referrer service was "+
					"disconnected.");
				serverRegistration(null);
			}
		});
	}

	private void serverRegistration(String referrer)
	{
		if (Api.DISABLE_BACKEND)
		{
			Util.updatePref(PrefNames.USER_ID, System.currentTimeMillis());
			Util.updatePref(PrefNames.DEVICE_ID, System.currentTimeMillis());
			Util.updatePref(PrefNames.INSTALL_ID, System.currentTimeMillis());
			return;
		}

		// Get the unique android ID, if possible:
		String androidID = Util.getAndroidID();
		if (androidID.equals(""))
		{
			// There is nothing we can do.
			Log.e(TAG,"No Android ID","A device had no ID.");
			return;
		}
		try
		{
			// Send device and user info to the server:
			JSONObject req = new JSONObject();
			req.put("android_id",androidID);
			req.put("api_level", Build.VERSION.SDK_INT);
			req.put("android_version",Build.VERSION.RELEASE);
			req.put("model",Build.MODEL);
			req.put("country",Util.getUserCountry(this));
			PackageInfo packageInfo = getPackageManager().getPackageInfo(
				getPackageName(),0);
			req.put("version_name", packageInfo.versionName);
			req.put("version_code", packageInfo.versionCode);
			if (Util.isValid(referrer))
				req.put("referrer_data",referrer);
			if (Util.IS_GOOGLE)
				req.put("app_store","google");
			else if (Util.IS_AMAZON)
				req.put("app_store","amazon");
			else
				req.put("app_store","web");
			Api.postWithRetries("new_install",req,(JSONObject res) -> {
				// Got the results successfully.  Record them.
				try
				{
					long installTime = res.getLong("install_time");
					_pm.recordInstallDate(installTime);
					Util.updatePref(PrefNames.USER_ID, res.getLong("user_id"));
					Util.updatePref(PrefNames.DEVICE_ID, res.getLong("device_id"));
					Util.updatePref(PrefNames.INSTALL_ID, res.getLong("install_id"));

					Util.log("ConfigManager: Got Installation Date From Server: "+installTime);
					Util.log("ConfigManager: User ID: "+res.getLong("user_id"));
					Util.log("ConfigManager: Device ID: "+res.getLong("device_id"));
					Util.log("ConfigManager: Install ID: "+res.getLong("install_id"));

					// Now that we're registered with our server, let's record the install event there.
					Util.logOneTimeEvent(this, "install", 0, null);
				}
				catch (JSONException e)
				{
					Log.e(TAG,"Invalid Server Registration Response","Got this response from "+
						"the server during registration: "+res.toString());
				}
			});
		}
		catch (JSONException e)
		{
			Util.handleException(TAG,this,e);
		}
		catch (PackageManager.NameNotFoundException e)
		{
			Util.handleException(TAG,this,e);
		}
	}
}
