package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import androidx.appcompat.widget.PopupMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

// This class is used to generate and manipulate a title bar at the top of a fragment.
// Any fragment using this must have a FrameLayout with the ID title_bar_placeholder defined.

public class TitleBar
{
	// The activity the bar is being used in:
	private Activity _activity;
	
	// Resource IDs of the buttons:
	static private final int[] _buttonIDs = new int[]
    {
		R.id.title_bar_button_0,
		R.id.title_bar_button_1,
		R.id.title_bar_button_2,
		R.id.title_bar_button_3
    };
		
	// Number of buttons available (note that one may be an overflow button):
	static public final int MAX_BUTTONS = 4;
	
	// Links to the various buttons:
	ArrayList<ImageButton> _buttons;
	
	// Number of buttons or menus in use:
	private int _numButtons;
	
	// Maps command IDs to click listener objects:
	private HashMap<Integer,View.OnClickListener> _listeners;
	
	// A popup menu that is used if needed:
	PopupMenu _menu;
	
	// An array of command IDs:
	private ArrayList<Integer> _commandIDs;
	
	// The root view of the parent fragment:
	private ViewGroup _fragmentRootView;
	
	// Constructor:
	public TitleBar(Activity a, ViewGroup fragmentRootView)
	{
		_activity = a;
		_fragmentRootView = fragmentRootView;
		
		// Get a reference to the FrameLayout placeholder:
		FrameLayout placeholder = (FrameLayout)fragmentRootView.findViewById(R.id.title_bar_placeholder);
		
		// Get a reference to the title bar layout by inflating the layout in the XML definition:
		LayoutInflater inflater = (LayoutInflater)_activity.getSystemService(
			Activity.LAYOUT_INFLATER_SERVICE);
		LinearLayout wrapper = (LinearLayout)inflater.inflate(R.layout.title_bar, null);
		
		// Get references to all of the buttons:
		_buttons = new ArrayList<ImageButton>();
		for (int i=0; i<_buttonIDs.length; i++)
		{
			_buttons.add((ImageButton)wrapper.findViewById(_buttonIDs[i]));
		}
		
		// Initially, all buttons are hidden:
		for (int i=0; i<_buttons.size(); i++)
		{
			_buttons.get(i).setVisibility(View.GONE);
		}
		
		// Add the command bar layout to the placeholder:
		placeholder.addView(wrapper);
		
		// Initialize the list of listener objects:
		_listeners = new HashMap<Integer,View.OnClickListener>();
		
		_numButtons = 0;
		_commandIDs = new ArrayList<Integer>();
	}
	
	// Reset the title bar.  Clear all menus and commands:
	public void reset()
	{
		// Hide all buttons:
		for (int i=0; i<_buttons.size(); i++)
		{
			_buttons.get(i).setVisibility(View.GONE);
		}
		
		// Clear all click listeners:
		_listeners.clear();
		
		// Remote all buttons and command IDs:
		_numButtons = 0;
		_commandIDs.clear();
	}
	
	// Set the title displayed at the top of the fragment:
	public void setTitle(String newTitle)
	{
		TextView title = (TextView)_fragmentRootView.findViewById(R.id.title_bar_title);
		title.setText(newTitle);
	}
	
	// Add a button at the next index.  Places this in an overflow menu if needed:
	public void addButton(int commandID, int drawableID, int stringResourceID, View.OnClickListener 
		clickListener)
	{
		if (_numButtons<MAX_BUTTONS)
		{
			// Set the image:
			_buttons.get(_numButtons).setImageDrawable(_activity.getResources().getDrawable(
				drawableID));
			
			// Link in the code to execute when the button is tapped:
			_buttons.get(_numButtons).setOnClickListener(clickListener);
			
			// Make sure the button is visible:
			_buttons.get(_numButtons).setVisibility(View.VISIBLE);
			
			// Store the command title on the contentDescription attribute:
			_buttons.get(_numButtons).setContentDescription(_activity.getString(stringResourceID));
			
			// Display the content description whenever a long press occurs:
			_buttons.get(_numButtons).setOnLongClickListener(new View.OnLongClickListener()
			{
				@Override
				public boolean onLongClick(View v)
				{
					Util.shortPopup(_activity, v.getContentDescription().toString());
					return true;
				}
			});
		}
		else if (_numButtons==MAX_BUTTONS)
		{
			// Need to add in the overflow button.
			int overflowIndex = MAX_BUTTONS-1;
			
			// Set the overflow image:
			_buttons.get(overflowIndex).setImageDrawable(_activity.getResources().getDrawable(
				Util.resourceIdFromAttr(_activity,R.attr.ab_overflow)));
			
			// Remove any existing click listener:
			_buttons.get(overflowIndex).setOnClickListener(null);
			_buttons.get(overflowIndex).setOnLongClickListener(null);
			
			// Construct the PopupMenu object:
			_menu = new PopupMenu(_activity,_buttons.get(overflowIndex));
			
			// Add in both the previous and current commands to the overflow menu:
			Menu menu = _menu.getMenu();
			menu.add(0,_commandIDs.get(overflowIndex),0,_buttons.get(overflowIndex).
				getContentDescription());
			menu.add(0, commandID, 0, stringResourceID);
			
			// Remove the content description from the overflow button:
			_buttons.get(overflowIndex).setContentDescription(null);
			
			_buttons.get(overflowIndex).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					_menu.show();
				}
			});
			
			// Set the callback for when a user picks a menu item:
			_menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
			{
				@Override
				public boolean onMenuItemClick(MenuItem menuItem)
				{
					_listeners.get(menuItem.getItemId()).onClick(_buttons.get(MAX_BUTTONS-1));
					return true;
				}
			});
		}
		else
		{
			// Add this to the menu:
			Menu menu = _menu.getMenu();
			menu.add(0, commandID, 0, stringResourceID);
		}

		// Save the callback and command ID for this command:
		_listeners.put(commandID, clickListener);
		_commandIDs.add(commandID);
		
		_numButtons++;
	}	
}
