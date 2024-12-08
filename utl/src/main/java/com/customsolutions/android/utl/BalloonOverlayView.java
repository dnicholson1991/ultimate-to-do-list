package com.customsolutions.android.utl;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.osmdroid.views.overlay.OverlayItem;

public class BalloonOverlayView<Item extends OverlayItem> extends FrameLayout
{
	private LinearLayout layout;
	private TextView title;
	private TextView snippet;
	private UTLLocation _loc;
	private Context _context;

	/**
	 * Create a new BalloonOverlayView.
	 *
	 * @param context - The activity context.
	 * @param balloonBottomOffset - The bottom padding (in pixels) to be applied
	 * when rendering this view.
	 */
	public BalloonOverlayView(Context context, int balloonBottomOffset) {

		super(context);
		_context = context;

		setPadding(10, 0, 10, balloonBottomOffset);
		layout = new LinearLayout(context);
		layout.setVisibility(VISIBLE);

		LayoutInflater inflater = (LayoutInflater) context
		.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View v = inflater.inflate(R.layout.balloon_overlay, layout);
		title = (TextView) v.findViewById(R.id.balloon_item_title);
		snippet = (TextView) v.findViewById(R.id.balloon_item_snippet);

		// Tapping on the location text or the close button dismisses the location:
		v.findViewById(R.id.balloon_wrapper).setOnClickListener(new View.
			OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				layout.setVisibility(GONE);
			}
		});
		v.findViewById(R.id.close_img_button).setOnClickListener(new View.
			OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				layout.setVisibility(GONE);
			}
		});
		
		// Handler for navigate button:
		v.findViewById(R.id.ballon_navigate_button).setOnClickListener(new View.
			OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(Intent.ACTION_VIEW,
					Uri.parse("google.navigation:q="+new Double(_loc.lat).toString()+","+
					new Double(_loc.lon).toString()));
				try
				{
					_context.startActivity(i);
				}
				catch (ActivityNotFoundException e)
				{
					Util.popup(_context, R.string.Navigation_Not_Installed);
				}
			}
		});
		
		// Handler for details button (which opens the view):
		v.findViewById(R.id.ballon_details_button).setOnClickListener(new View.
			OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(_context,TaskList.class);
				i.putExtra("top_level","locations");
				i.putExtra("view_name",new Long(_loc._id).toString());
				i.putExtra("title", Util.getString(R.string.Locations)+" / "+
					_loc.title);
				_context.startActivity(i);
			}
		});
		
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
			LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.gravity = Gravity.NO_GRAVITY;

		addView(layout, params);
	}

	/**
	 * Sets the view data from a given overlay item.
	 *
	 * @param item - The overlay item containing the relevant view data
	 * (title and snippet).
	 */
	public void setData(Item item) {

		layout.setVisibility(VISIBLE);
		if (item.getTitle() != null) {
			title.setVisibility(VISIBLE);
			title.setText(item.getTitle());
		} else {
			title.setVisibility(GONE);
		}
		if (item.getSnippet() != null) {
			snippet.setVisibility(VISIBLE);
			snippet.setText(item.getSnippet());
		} else {
			snippet.setVisibility(GONE);
		}

	}
	
	// Set the location associated with the view:
	public void setLocation(UTLLocation loc)
	{
		_loc = loc;
	}
}
