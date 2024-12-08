package com.customsolutions.android.utl;

// This activity implements the uploading of the log file.

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadLog extends UtlPopupActivity
{
	private static final String TAG = "UploadLog";

	private ProgressDialog _progressDialog;
	
	// The temporary file to upload:
	private static final String LOG_FILE = "temp_log_data.txt";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
    	Util.log("Begin UploadLog");
    	
    	// Link this activity with a layout resource:
        setContentView(R.layout.upload_log);
        
        // Set the title of this screen:
        this.setTitle(R.string.Upload_Usage_Info);
        
        // Handler for the upload button:
        findViewById(R.id.upload_log_upload_button).setOnClickListener(new View.
        	OnClickListener()
		{		
			@SuppressLint("NewApi")
			@Override
			public void onClick(View v)
			{
				/*
				_progressDialog = ProgressDialog.show(UploadLog.this, null, Util.
					getString(R.string.Wait_For_Upload),false);
				if (Build.VERSION.SDK_INT >= 11)
					new PerformUpload().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
		    	else
		    		new PerformUpload().execute(new Void[] { });

				 */
				performUpload();
				
			}
		});
    }

	/** Perform the upload. */
	private void performUpload()
	{
		Log.v(TAG,"Starting log file upload.");
		UploadLog.this.lockScreenOrientation();
		_progressDialog = ProgressDialog.show(UploadLog.this, null, Util.
			getString(R.string.Wait_For_Upload),false);
		Util.UTL_EXECUTOR.execute(() -> {
			try
			{
				// Fetch and compress the data using G-Zip:
				JSONObject toUpload = Util.getLogData(UploadLog.this);
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

				// Analyze the response:
				runOnUiThread(() -> {
					try
					{
						_progressDialog.dismiss();
						UploadLog.this.unlockScreenOrientation();
						TextView tv = (TextView)findViewById(R.id.upload_log_msg);
						JSONObject j = new JSONObject(bodyString);
						long logNumber = j.getLong("log_number");
						tv.setText(Util.getString(R.string.Upload_Log_Msg_2)+" "+logNumber);
						findViewById(R.id.upload_log_upload_button).setVisibility(View.GONE);
					}
					catch (JSONException|ClassCastException e)
					{
						Util.handleException(TAG,UploadLog.this,e);
					}
				});
			}
			catch (IOException|NullPointerException e)
			{
				Log.d(TAG,"Got Exception when trying to upload log file. ",e);
				Api.showNetworkErrorMessage(UploadLog.this);
			}
		});
	}

    // Called when the user changes orientation
    // We don't want to do anything, in order to prevent the upload from being interrupted.
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
    	super.onConfigurationChanged(newConfig);
    	return;
    }
}
