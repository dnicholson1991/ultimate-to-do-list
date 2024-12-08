package com.customsolutions.android.utl;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A special subclass of View that is used for displaying information on a single task.
 */
public class TaskItemView extends FrameLayout
{
    final public ImageView image;
    final public TextView text;
    final public LinearLayout wrapper;

    public TaskInfo taskInfo;
    public Context context;

    public TaskItemView(Context context)
    {
        super(context);
        View.inflate(context, R.layout.task_list_item, this);
        image = (ImageView) findViewById(R.id.task_list_item_image);
        text = (TextView) findViewById(R.id.task_list_item_text);
        wrapper = (LinearLayout) findViewById(R.id.task_list_linear_layout);
    }
}
