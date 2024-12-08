package com.customsolutions.android.utl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import androidx.core.app.UtlJobIntentService;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * This uploads an Activity log in the background.
 */

public class LogUploaderService extends UtlJobIntentService
{
    /** Use this Action in the Intent to trigger a log upload. */
    public static final String UPLOAD_ACTION = "com.customsolutions.android.phonelink.upload_log";

    /** The maximum number of logs we can upload per day. */
    private static final int MAX_UPLOADS_PER_DAY = 10;

    private static final String TAG = "LogUploaderService";
    private static final int JOB_ID = 562902;

    /**
     * Convenience method for enqueuing work to this service.
     */
    static void enqueueWork(Context context, Intent work) {
        enqueueWork(context, LogUploaderService.class, JOB_ID, work);
    }

    @Override
    protected void onHandleWork(Intent intent)
    {
        Util.appInit(this);

        if (intent==null || intent.getAction()==null || !intent.getAction().equals(UPLOAD_ACTION))
        {
            // This isn't the intent we're looking for.
            return;
        }

        // Check the count of errors logged today and make sure we don't go over.
        SharedPreferences prefs = getSharedPreferences(Util.PREFS_NAME,0);
        DateFormat dateFormat = DateFormat.getDateInstance();
        String currentDate = dateFormat.format(new Date(System.currentTimeMillis()));
        String lastUploadDate = prefs.getString(PrefNames.LAST_LOG_UPLOAD_DATE,currentDate);
        int numUploads = prefs.getInt(PrefNames.NUM_LOG_UPLOADS,0);
        if (currentDate.equals(lastUploadDate))
        {
            if (numUploads>=MAX_UPLOADS_PER_DAY && !BuildConfig.DEBUG)
            {
                Log.d(TAG,"Reached the maximum number of log uploads for today.");
                return;
            }
        }
        else
        {
            // This is the first error on a new date.
            numUploads = 0;
            lastUploadDate = currentDate;
        }
        numUploads++;
        prefs.edit()
            .putInt(PrefNames.NUM_LOG_UPLOADS,numUploads)
            .putString(PrefNames.LAST_LOG_UPLOAD_DATE,lastUploadDate)
            .apply();

        try
        {
            // Fetch and compress the data using G-Zip:
            JSONObject toUpload = Util.getLogData(this);
            byte[] bytesToCompress = toUpload.toString().getBytes();
            ByteArrayOutputStream gZipBytes = new ByteArrayOutputStream();
            GZIPOutputStream zos = new GZIPOutputStream(new BufferedOutputStream(gZipBytes));
            zos.write(bytesToCompress);
            zos.close();

            // When running in debug mode, the server base URL changes.
            String serverBaseUrl = Api.SERVER_BASE_URL;
            if (BuildConfig.DEBUG)
                serverBaseUrl = Api.SERVER_BASE_URL_TEST;
            String finalUrl = serverBaseUrl+"handle_log";

            // Prepare and send the data:
            RequestBody body = RequestBody.create(MediaType.parse("application/x-gzip"),
                gZipBytes.toByteArray());
            Request request = new Request.Builder()
                .url(finalUrl)
                .header("Authorization", "Basic "+ Base64.encodeToString((Api.USERNAME+":"+
                    Api.PASSWORD).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                .post(body)
                .build();
            final Response response = Util.client().newCall(request).execute();
            final String bodyString = response.body().string();
            Log.v(TAG,"Log upload response: "+bodyString);

            JSONObject j = new JSONObject(bodyString);
            long logNumber = j.getLong("log_number");
            Log.v(TAG,"Successfully uploaded log number "+logNumber);
        }
        catch (IOException|NullPointerException|JSONException e)
        {
            Log.d(TAG,"Got Exception when trying to upload log file. ",e);
        }
    }
}
