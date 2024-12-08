package com.customsolutions.android.utl;

import android.content.Context;
import android.support.wearable.view.WearableListView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Nicholson on 12/14/2014.
 */
public class SnoozeItemView extends FrameLayout
{
    final ImageView image;
    final TextView text;
    final LinearLayout wrapper;

    public SnoozeItemInfo snoozeItemInfo;
    public Context context;

    public SnoozeItemView(Context context)
    {
        super(context);
        View.inflate(context, R.layout.snoozer_item, this);
        image = (ImageView) findViewById(R.id.snoozer_item_image);
        text = (TextView) findViewById(R.id.snoozer_item_text);
        wrapper = (LinearLayout) findViewById(R.id.snoozer_linear_layout);
    }
}
