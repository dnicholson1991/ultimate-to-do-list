package com.customsolutions.android.utl;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.wearable.view.WearableRecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/** Displays a list of tasks. */
public class TaskList extends Activity
{
    private static final String TAG = "TaskList";

    private static final int PERMISSION_REQUEST_CODE = 8734;

    private boolean _finishOnPause;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.task_list);
        _finishOnPause = true;

        // Log the version code:
        String pkg = this.getPackageName();
        try
        {
            int versionCode = this.getPackageManager().getPackageInfo(pkg, 0).versionCode;
            Util.log("TaskList: Starting Up.  Version Code: "+versionCode+
                "; API Level: "+ Build.VERSION.SDK_INT,this);
        }
        catch (PackageManager.NameNotFoundException e) { }  // Not gonna happen.

        // Configure the list view:
        WearableRecyclerView listView = (WearableRecyclerView) findViewById(R.id.task_list);
        listView.setCenterEdgeItems(true);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        listView.setOffsettingHelper(new ScalingOffsettingHelper(Math.round(52f*metrics.density)));

        // Populate the list of tasks:
        Bundle extras = this.getIntent().getExtras();
        ArrayList<TaskInfo> tasks = new ArrayList<>();
        String[] titleArray = extras.getStringArray("title_array");
        long[] completedArray = extras.getLongArray("completed_array");
        long[] taskIdArray = extras.getLongArray("task_id_array");
        for (int i = 0; i<titleArray.length; i++)
        {
            tasks.add(new TaskInfo(titleArray[i],completedArray[i]==1,taskIdArray[i]));
        }

        // Create the adapter to map the task list to views:
        TaskListAdapter mAdapter = new TaskListAdapter(this, tasks);
        listView.setAdapter(mAdapter);

        // If the user has not approved notification permission, then prompt:
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.
            POST_NOTIFICATIONS);
        if (permissionCheck!=PackageManager.PERMISSION_GRANTED)
        {
            _finishOnPause = false;
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                PERMISSION_REQUEST_CODE);

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (grantResults!=null && grantResults.length>0)
            Log.i(TAG,"Result of permissions request:"+grantResults[0]);
        else
            Log.e(TAG,"onRequestPermissionsResult called with empty results.");
    }

    @Override
    public void onPause()
    {
        super.onPause();

        if (!isFinishing() && _finishOnPause)
        {
            // The Activity should finish when paused. This will ensure the task list is refreshed
            // when the app is viewed again.
            finish();
        }
        _finishOnPause = true;
    }
}
