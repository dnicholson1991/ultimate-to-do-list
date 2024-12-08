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

// This class is used to generate and manipulate a command bar at the bottom of the screen.
// Any activity using this must have a FrameLayout with the ID command_bar_placeholder defined.

public class CommandBar
{
	// The activity the bar is being used in:
	private Activity _activity;
	
	// Resource IDs of the buttons:
	static private final int[] _buttonIDs = new int[]
    {
		R.id.command_bar_button_0,
		R.id.command_bar_button_1,
		R.id.command_bar_button_2,
		R.id.command_bar_button_3,
		R.id.command_bar_button_4,
		R.id.command_bar_button_5
    };
	
	// Resource IDs of button containers:
	static private final int[] _containerIDs = new int[]
    {
		R.id.command_bar_button_0_container,
		R.id.command_bar_button_1_container,
		R.id.command_bar_button_2_container,
		R.id.command_bar_button_3_container,
		R.id.command_bar_button_4_container,
		R.id.command_bar_button_5_container
    };

	/** The root view of the bar. */
	private ViewGroup _root;

	// Number of buttons available (note that one may be an overflow button):
	static public final int MAX_BUTTONS = 6;
	
	// Links to the various buttons and containers:
	ArrayList<ImageButton> _buttons;
	ArrayList<View> _containers;
	
	// Number of buttons or menus in use:
	private int _numButtons;
	
	// Maps command IDs to click listener objects:
	private HashMap<Integer,View.OnClickListener> _listeners;
	
	// A popup menu that is used if needed:
	PopupMenu _menu;
	
	// An array of command IDs:
	private ArrayList<Integer> _commandIDs;

	/** Flag indicating whether the background color is the same or similar to the action bar. */
	private boolean _usingActionBarColor;

	// Constructor:
	public CommandBar(Activity a, ViewGroup fragmentRootView)
	{
		_activity = a;
		_usingActionBarColor = true;
		
		// Get a reference to the FrameLayout placeholder:
		FrameLayout placeholder = (FrameLayout)fragmentRootView.findViewById(R.id.command_bar_placeholder);
		
		// Get a reference to the command bar layout by inflating the layout in the XML definition:
		LayoutInflater inflater = (LayoutInflater)_activity.getSystemService(
			Activity.LAYOUT_INFLATER_SERVICE);
		LinearLayout wrapper = (LinearLayout)inflater.inflate(R.layout.command_bar, null);
		_root = wrapper;
		
		// Get references to all of the buttons:
		_buttons = new ArrayList<ImageButton>();
		for (int i=0; i<_buttonIDs.length; i++)
		{
			_buttons.add((ImageButton)wrapper.findViewById(_buttonIDs[i]));
		}
		_containers = new ArrayList<View>();
		for (int i=0; i<_containerIDs.length; i++)
		{
			_containers.add(wrapper.findViewById(_containerIDs[i]));
		}
		
		// Initially, all buttons are hidden:
		for (int i=0; i<_containers.size(); i++)
		{
			_containers.get(i).setVisibility(View.GONE);
		}
		
		// Add the command bar layout to the placeholder:
		placeholder.addView(wrapper);
		
		// Initialize the list of listener objects:
		_listeners = new HashMap<Integer,View.OnClickListener>();
		
		_numButtons = 0;
		_commandIDs = new ArrayList<Integer>();
	}

	/** Set the background drawable. */
	public void setBackground(int resourceID, boolean matchesActionBarColor)
	{
		_root.setBackgroundResource(resourceID);
		_usingActionBarColor = matchesActionBarColor;
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
			_containers.get(_numButtons).setVisibility(View.VISIBLE);
			
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
				Util.resourceIdFromAttr(_activity,_usingActionBarColor ? R.attr.ab_overflow :
				R.attr.ab_overflow_inv)));
			
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
