package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.app.Activity;
import androidx.appcompat.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SaveCancelTopBar
{
	// The activity the bar is being used in:
	private Activity _activity;
	
	// A popup menu that is used if needed:
	PopupMenu _menu;
	
	// An array of menu command IDs:
	private ArrayList<Integer> _commandIDs;
	
	// The root view of the parent fragment:
	private ViewGroup _fragmentRootView;
	
	// References to Buttons:
	Button _saveButton;
	Button _cancelButton;
	ImageButton _overflowButton;
	
	// Constructor:
	public SaveCancelTopBar(Activity a, ViewGroup fragmentRootView)
	{
		_activity = a;
		_fragmentRootView = fragmentRootView;
		
		// Get a reference to the FrameLayout placeholder:
		FrameLayout placeholder = (FrameLayout)fragmentRootView.findViewById(
			R.id.save_cancel_bar_placeholder);
		
		// Get a reference to the bar layout by inflating the layout in the XML definition:
		LayoutInflater inflater = (LayoutInflater)_activity.getSystemService(
			Activity.LAYOUT_INFLATER_SERVICE);
		LinearLayout wrapper = (LinearLayout)inflater.inflate(R.layout.save_cancel_top_bar, null);
		
		// Get references to the buttons:
		_saveButton = (Button)wrapper.findViewById(R.id.save_cancel_bar_save_button);
		_cancelButton = (Button)wrapper.findViewById(R.id.save_cancel_bar_cancel_button);
		_overflowButton = (ImageButton)wrapper.findViewById(R.id.save_cancel_bar_overflow_button);
		
		// By default, the overflow menu/button is hidden:
		wrapper.findViewById(R.id.save_cancel_bar_overflow_group).setVisibility(View.GONE);
		
		// Add the bar layout to the placeholder:
		placeholder.addView(wrapper);
	}
	
	// Set the Title in the bar:
	public void setTitle(String newTitle)
	{
		TextView title = (TextView)_fragmentRootView.findViewById(R.id.save_cancel_bar_title);
		title.setText(newTitle);
	}
	
	// Set the handler for the Save button:
	public void setSaveHandler(View.OnClickListener saveHandler)
	{
		_saveButton.setOnClickListener(saveHandler);
	}
	
	// Set the handler for the Cancel button:
	public void setCancelHandler(View.OnClickListener cancelHandler)
	{
		_cancelButton.setOnClickListener(cancelHandler);
	}
	
	// Specify the listener function for when a menu item is selected:
	public void setMenuListener(PopupMenu.OnMenuItemClickListener newListener)
	{
		if (_menu == null)
		{
			// Need to initialize the menu:
			_menu = new PopupMenu(_activity,_overflowButton);
			_overflowButton.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					_menu.show();
				}
			});
		}
		
		_menu.setOnMenuItemClickListener(newListener);
	}
	
	// Add a command to the overflow menu:
	public void addToOverflow(int commandID, int stringResourceID)
	{
		// The overflow menu must be visible:
		_fragmentRootView.findViewById(R.id.save_cancel_bar_overflow_group).setVisibility(View.VISIBLE);
		
		if (_menu == null)
		{
			// Need to initialize the menu:
			_menu = new PopupMenu(_activity,_overflowButton);
			_overflowButton.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					_menu.show();
				}
			});
		}
		
		// Add the menu item to the menu:
		Menu menu = _menu.getMenu();
		menu.add(0,commandID, 0, stringResourceID);
	}
}
