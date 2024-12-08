package com.customsolutions.android.utl;

import android.content.Intent;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

/**
 * This is a "holder" for the view containing task information.
 */

public class TaskViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
{
    private static final String TAG = "TaskViewHolder";

    public TaskViewHolder(TaskItemView taskItemView)
    {
        super(taskItemView);
        taskItemView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v)
    {
        TaskItemView taskItemView = (TaskItemView) v;
        TaskInfo taskInfo = taskItemView.taskInfo;
        Util.log(TAG,"Task clicked on: "+taskInfo.title,taskItemView.context);

        taskInfo.isCompleted = !taskInfo.isCompleted;

        // Notify the handheld of the updated status:
        Intent i = new Intent(taskItemView.context,HandsetService.class);
        i.setAction(HandsetService.ACTION_UPDATE_COMPLETION_STATUS);
        i.putExtra("task_id",taskInfo.taskID);
        i.putExtra("is_completed",taskInfo.isCompleted);
        taskItemView.context.startService(i);

        // Update the display:
        if (taskInfo.isCompleted)
            taskItemView.image.setImageResource(R.drawable.checkbox_checked);
        else
            taskItemView.image.setImageResource(R.drawable.checkbox_cyan);
    }
}
