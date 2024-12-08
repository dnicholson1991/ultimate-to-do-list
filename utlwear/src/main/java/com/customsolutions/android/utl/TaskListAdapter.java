package com.customsolutions.android.utl;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * This adapter maps task information in a list to the views that hold that information.
 */
public class TaskListAdapter extends RecyclerView.Adapter
{
    private final Context _context;
    private final List<TaskInfo> _taskInfoList;

    public TaskListAdapter(Context context, List<TaskInfo> items)
    {
        this._context = context;
        this._taskInfoList = items;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i)
    {
        return new TaskViewHolder(new TaskItemView(_context));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position)
    {
        TaskItemView taskItemView = (TaskItemView) viewHolder.itemView;
        final TaskInfo taskInfo = _taskInfoList.get(position);

        // Add a reference to the task information to the view:
        taskItemView.taskInfo = taskInfo;
        taskItemView.context = _context;

        // Set the text to display:
        TextView textView = (TextView) taskItemView.findViewById(R.id.task_list_item_text);
        textView.setText(taskInfo.title);

        // Set the checkbox image:
        ImageView imageView = (ImageView) taskItemView.findViewById(R.id.task_list_item_image);
        if (taskInfo.isCompleted)
            imageView.setImageResource(R.drawable.checkbox_checked);
        else
            imageView.setImageResource(R.drawable.checkbox_cyan);
    }

    @Override
    public int getItemCount() {
        return _taskInfoList.size();
    }
}
