package com.customsolutions.android.utl;

import androidx.core.view.MenuItemCompat;
import android.view.Menu;

// Utility functions for easier additions to menus:

public class MenuUtil
{
	// The maximum number of menu items we will force to display as action bar buttons. 
	private static final int MAX_ACTION_BAR_BUTTONS = 5;
	
	// The number of menu items that have been forced to show as buttons with icons:
	private static int _numForcedButtons;
	
	private static Menu _menu;
	
	// Initialize a new menu:
	public static void init(Menu menu)
	{
		_numForcedButtons = 0;
		_menu = menu;
	}
	
	// Add an item with an icon:
	public static void add(int commandID, int stringResourceID, int iconResourceID)
	{
		int showMode = MenuItemCompat.SHOW_AS_ACTION_IF_ROOM;
		_numForcedButtons++;
		if (_numForcedButtons<=MAX_ACTION_BAR_BUTTONS)
			showMode = MenuItemCompat.SHOW_AS_ACTION_ALWAYS;
		
		MenuItemCompat.setShowAsAction(_menu.add(0,commandID,0,stringResourceID).
			setIcon(iconResourceID),showMode);
	}
	
	// Add an item without an icon:
	public static void add(int commandID, int stringResourceID)
	{
		MenuItemCompat.setShowAsAction(_menu.add(0,commandID,0,stringResourceID),
			MenuItemCompat.SHOW_AS_ACTION_NEVER);
	}
}
