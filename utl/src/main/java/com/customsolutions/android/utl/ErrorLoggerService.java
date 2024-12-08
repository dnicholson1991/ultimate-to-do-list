package com.customsolutions.android.utl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;

import androidx.core.app.UtlJobIntentService;

/**
 * This IntentService is used for logging error and warning messages at our server. <br><br>
 * Intents passed in must have the following extras: <br>
 * tag: The tag <br>
 * summary: Brief error description<br>
 * message: The actual message to log
 */
public class ErrorLoggerService extends UtlJobIntentService
{
    /** The maximum number of errors or warnings we can log per day. */
    private static final int MAX_ERRORS_PER_DAY = 15;

    private static final String TAG = "ErrorLoggerService";
    private static final int JOB_ID = 8937390;

    /**
     * Convenience method for enqueuing work to this service.
     */
    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, ErrorLoggerService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent)
    {
        Util.appInit(this);

        Bundle b = intent.getExtras();
        if (b==null || !b.containsKey("tag") || !b.containsKey("summary") || !b.containsKey(
            "message"))
        {
            Log.d(TAG,"Missing extras in ErrorLoggerService.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(Util.PREFS_NAME,0);

        // Update the count of errors logged today and make sure we don't go over.
        DateFormat dateFormat = DateFormat.getDateInstance();
        String currentDate = dateFormat.format(new Date(System.currentTimeMillis()));
        String lastErrorDate = prefs.getString(PrefNames.LAST_ERROR_DATE,currentDate);
        int numErrorsLogged = prefs.getInt(PrefNames.NUM_ERRORS_LOGGED,0);
        if (currentDate.equals(lastErrorDate))
        {
            if (numErrorsLogged>=MAX_ERRORS_PER_DAY && !BuildConfig.DEBUG)
            {
                Log.d(TAG,"Reached the maximum number of errors we can log for today.");
                return;
            }
        }
        else
        {
            // This is the first error on a new date.
            numErrorsLogged = 0;
            lastErrorDate = currentDate;
        }
        numErrorsLogged++;
        prefs.edit()
            .putInt(PrefNames.NUM_ERRORS_LOGGED,numErrorsLogged)
            .putString(PrefNames.LAST_ERROR_DATE,lastErrorDate)
            .apply();

        if (Api.DISABLE_BACKEND)
            return;

        try
        {
            JSONObject j = new JSONObject();
            j.put("install_id",prefs.getLong(PrefNames.INSTALL_ID,0));
            try
            {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(this.getPackageName(),0);
                j.put("version_name", packageInfo.versionName);
                j.put("version_code", packageInfo.versionCode);
            }
            catch (PackageManager.NameNotFoundException e)
            {
                Log.v(TAG, "Got NameNotFoundException when getting package info.");
                return;
            }
            j.put("tag",b.getString("tag"));
            j.put("summary",b.getString("summary"));
            j.put("message",b.getString("message"));
            Api.postError("handle_error",j);
        }
        catch (JSONException e)
        {
            // Not Gonna happen.
        }
    }
}

