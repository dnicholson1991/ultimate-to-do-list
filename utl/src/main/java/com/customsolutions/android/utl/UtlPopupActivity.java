package com.customsolutions.android.utl;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

// An activity that appears as a popup dialog to the user.

public class UtlPopupActivity extends UtlActivity
{
	// Called when activity is first created:
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		SharedPreferences settings = getSharedPreferences("UTL_Prefs",0);
    	
	    super.onCreate(savedInstanceState);
		
	    // Set the size of the popup and other window attributes. Due to some odd display issues
		// that occur on small screens, we won't adjust any window parameters if this is a
		// full screen Activity.
	    Point size = getPopupSize();
	    if (size.x!=_screenWidth || size.y!=_screenHeight)
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
			LayoutParams params = getWindow().getAttributes();
			// 2024-06-24: Setting the width and height below is what causes the issue with
			// dropdown menu items overlapping system controls, making the last item
			// unselectable. I haven't been able to find a way to fix this issue.
			params.height = size.y;
			params.width = size.x;
			params.alpha = 1.0f;
			params.dimAmount = 0.5f;
			getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
		}

	    // Some themes use a different window background color for popups.
		if (size.x!=_screenWidth || size.y!=_screenHeight)
		{
			getWindow().setBackgroundDrawable(new ColorDrawable(Util.colorFromAttr(this,R.attr.
				popup_window_background_color)));
		}
	}
	
	// This function gets the width and height of the popup window based on the device's screen
	// size and orientation.  This may be overwritten if needed.
	protected Point getPopupSize()
	{
		int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
		Point size = new Point();
		
		if (getOrientation()==ORIENTATION_LANDSCAPE)
		{
			if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
				screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL ||
				screenSize==Configuration.SCREENLAYOUT_SIZE_UNDEFINED)
			{
				// On smaller screens, use full width and height:
				size.x = _screenWidth;
				size.y = _screenHeight;
				return size;
			}
			else if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
				size.x = _screenWidth*2/3;
				size.y = _screenHeight;
				return size;
			}
			else
			{
				// It must be extra large.  (Can't test directly since the XLARGE constant isn't supported
				// before api level 9).
				size.x = _screenWidth/2;
				size.y = _screenHeight*9/10;
				return size;
			}
		}
		else
		{
			// Portrait orientation.
			if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
				screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL ||
				screenSize==Configuration.SCREENLAYOUT_SIZE_UNDEFINED)
			{
				// On smaller screens, use full width and height:
				size.x = _screenWidth;
				size.y = _screenHeight;
				return size;
			}
			else if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet:
				size.x = _screenWidth;
				size.y = _screenHeight*2/3;
				return size;
			}
			else
			{
				// It must be extra large.  (Can't test directly since the XLARGE constant isn't supported
				// before api level 9). 
				size.x = _screenWidth*9/10;
				size.y = _screenHeight*6/10;
				return size;
			}
		}
	}
}
