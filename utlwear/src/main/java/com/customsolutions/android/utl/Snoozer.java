package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.support.wearable.view.WearableRecyclerView;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

public class Snoozer extends Activity
{
    /** The intent action that's used for shutting down this activity after a snooze has been
     * performed. */
    public static final String SHUTDOWN_ACTION = "com.customsolutions.android.utl.snoozer_shutdown";

    /** This broadcast receiver is used for shutting down this activity after a snooze has been
     * performed. */
    private BroadcastReceiver _shutdownReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.snoozer);

        Bundle b = this.getIntent().getExtras();
        long taskID = b.getLong("task_id");
        boolean isLocation = b.getBoolean("is_location");
        Util.log("Snoozer: starting up with task ID: "+taskID,this);

        // Set up the broadcast receiver to shut down the Activity when a snooze has been performed:
        _shutdownReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                finish();
            }
        };
        registerReceiver(_shutdownReceiver, new IntentFilter(SHUTDOWN_ACTION));

        // Configure the list view:
        WearableRecyclerView listView = (WearableRecyclerView) findViewById(R.id.snoozer_list);
        listView.setCenterEdgeItems(true);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        listView.setOffsettingHelper(new ScalingOffsettingHelper(Math.round(52f*metrics.density)));

        // Populate the list of snoozer items:
        List<SnoozeItemInfo> items = new ArrayList<>();
        String[] snoozeArrayStrings = this.getResources().getStringArray(R.array.snooze_strings);
        int[] snoozeArrayMinutes = getResources().getIntArray(R.array.snooze_minutes);
        for (int i=0; i<snoozeArrayStrings.length; i++)
        {
            items.add(new SnoozeItemInfo(R.drawable.list_item_circle, snoozeArrayStrings[i],
                snoozeArrayMinutes[i], isLocation, taskID));
        }

        // Create the adapter to map the list to views:
        SnoozerAdapter mAdapter = new SnoozerAdapter(this, items);
        listView.setAdapter(mAdapter);
    }

    @Override
    public void onDestroy()
    {
        if (_shutdownReceiver!=null)
            unregisterReceiver(_shutdownReceiver);
        super.onDestroy();
    }
}
