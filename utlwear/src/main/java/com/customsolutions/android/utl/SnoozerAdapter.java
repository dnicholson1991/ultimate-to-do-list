package com.customsolutions.android.utl;

import android.content.Context;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * This adapter maps snoozer item information in a list to the views that hold that information.
 */
public class SnoozerAdapter extends RecyclerView.Adapter
{
    private final Context _context;
    private final List<SnoozeItemInfo> _snoozeItemList;

    public SnoozerAdapter(Context context, List<SnoozeItemInfo> items)
    {
        this._context = context;
        this._snoozeItemList = items;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i)
    {
        return new SnoozeItemViewHolder(new SnoozeItemView(_context));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position)
    {
        SnoozeItemView snoozerItemView = (SnoozeItemView) viewHolder.itemView;
        final SnoozeItemInfo snoozeItemInfo = _snoozeItemList.get(position);

        // Add a reference to the snooze information to the view:
        snoozerItemView.snoozeItemInfo = snoozeItemInfo;
        snoozerItemView.context = _context;

        // Set the text to display:
        TextView textView = (TextView) snoozerItemView.findViewById(R.id.snoozer_item_text);
        textView.setText(snoozeItemInfo.title);
    }

    @Override
    public int getItemCount()
    {
        return _snoozeItemList.size();
    }
}
