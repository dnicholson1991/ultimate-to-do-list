package com.customsolutions.android.utl;

// This class is used to catch exceptions that are not otherwise caught.  This will 
// write the exception info to the database, send information to our server, and display
// a popup to the user.

// IMPORTANT: This class cannot be registered as an exception handler until Util.appInit is
// called!

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.util.Date;

public class MyExceptionHandler implements UncaughtExceptionHandler
{
	/** The maximum number of errors or warnings we can log per day. */
	private static final int MAX_ERRORS_PER_DAY = 15;

	private static final String TAG = "ExceptionHandler";

	private Context _c;
	private static Thread.UncaughtExceptionHandler _defaultUEH = null;

	public MyExceptionHandler(Context c)
	{
		_c = c;
		if (_defaultUEH==null)
			_defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
	}

	/** This is called when an uncaught exception occurs. */
	@Override
	public void uncaughtException(final Thread arg0, final Throwable th)
	{
		Throwable e = th;

		// Get the stack trace of the exception:
		StringWriter stackTrace = new StringWriter();
		e.printStackTrace(new PrintWriter(stackTrace));

		// Log the exception to the database:
		Log.e(TAG,"Exception","An exception has occurred: "+stackTrace.toString());

		// Check the count of errors logged today and make sure we don't go over.
		SharedPreferences prefs = _c.getSharedPreferences(Util.PREFS_NAME,0);
		DateFormat dateFormat = DateFormat.getDateInstance();
		String currentDate = dateFormat.format(new Date(System.currentTimeMillis()));
		String lastErrorDate = prefs.getString(PrefNames.LAST_ERROR_DATE,currentDate);
		int numErrorsLogged = prefs.getInt(PrefNames.NUM_ERRORS_LOGGED,0);
		if (currentDate.equals(lastErrorDate))
		{
			if (numErrorsLogged>=MAX_ERRORS_PER_DAY && !BuildConfig.DEBUG)
			{
				Log.d(TAG,"Reached the maximum number of errors we can log for today.");

				// Call the default handler:
				_defaultUEH.uncaughtException(arg0, th);
				return;
			}
		}

		if (!Api.DISABLE_BACKEND)
		{
			// Upload the exception information to our server:
			try
			{
				JSONObject j = new JSONObject();
				j.put("install_id", prefs.getLong(PrefNames.INSTALL_ID, 0));
				j.put("android_id", Settings.Secure.getString(_c.getContentResolver(), Settings.Secure.
					ANDROID_ID));
				j.put("api_level", Build.VERSION.SDK_INT);
				j.put("android_version", Build.VERSION.RELEASE);
				j.put("model", Build.MODEL);
				try
				{
					PackageInfo packageInfo = _c.getPackageManager().getPackageInfo(_c.getPackageName(), 0);
					j.put("version_name", packageInfo.versionName);
					j.put("version_code", packageInfo.versionCode);
				}
				catch (PackageManager.NameNotFoundException ex)
				{
					_defaultUEH.uncaughtException(arg0, th);
					return;
				}
				j.put("class", e.getClass().getName());
				j.put("message", e.getMessage());
				j.put("stack_trace", stackTrace.toString());
				Api.postError("handle_exception", j);
			}
			catch (JSONException e2)
			{
				// Not gonna happen.
			}
		}

		// Call the default code.
		_defaultUEH.uncaughtException(arg0, th);
	}
}
