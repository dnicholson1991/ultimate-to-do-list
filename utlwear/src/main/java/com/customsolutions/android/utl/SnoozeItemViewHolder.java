package com.customsolutions.android.utl;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

/**
 * This is a "holder" for the view containing snooze item information.
 */

public class SnoozeItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
{
    private static final String TAG = "SnoozeItemViewHolder";

    public SnoozeItemViewHolder(SnoozeItemView snoozeItemView)
    {
        super(snoozeItemView);
        snoozeItemView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v)
    {
        SnoozeItemView snoozeItemView = (SnoozeItemView) v;
        SnoozeItemInfo snoozeItemInfo = snoozeItemView.snoozeItemInfo;
        Util.log(TAG,"Snooze item clicked on: "+snoozeItemInfo.numMinutes,snoozeItemView.context);

        // Notify the handheld of the snooze action:
        Bundle b = new Bundle();
        b.putBoolean("is_location",snoozeItemInfo.isLocation);
        b.putLong("task_id",snoozeItemInfo.taskID);
        b.putInt("num_minutes",snoozeItemInfo.numMinutes);
        Intent i = new Intent(snoozeItemView.context,HandsetService.class);
        i.setAction(HandsetService.ACTION_PERFORM_SNOOZE);
        i.putExtras(b);
        snoozeItemView.context.startService(i);

        // Shut down the activity:
        snoozeItemView.context.sendBroadcast(new Intent(Snoozer.SHUTDOWN_ACTION));
    }
}
