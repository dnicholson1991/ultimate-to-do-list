package com.customsolutions.android.utl;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/** Call this activity to snooze a task.  Pass in a Bundle with key "task_id" */
public class SnoozePopup extends UtlPopupActivity
{
	// The task passed in:
	private UTLTask _t;
	
	private TasksDbAdapter _tasksDB;
	
	private boolean _isLocation;
	
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Util.log("Starting SnoozePopup.java");
		
		this.setContentView(R.layout.snooze_popup);
				
		getSupportActionBar().setTitle(R.string.Remind_me_in_);
		getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_timer_off_inv));
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
    	
    	// Set up the ListAdapter:
        setListAdapter(new ArrayAdapter<String>(this,R.layout.snooze_popup_row, getResources().
        	getStringArray(R.array.snooze_strings)));
        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        
        // Click handler for the list.  This performs the actual snooze operation:
        getListView().setOnItemClickListener(new ListView.OnItemClickListener() 
        {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
				long id)
			{
				int[] minutesArray = SnoozePopup.this.getResources().getIntArray(R.array.snooze_minutes);
				int numMinutes = minutesArray[position];
				
				// Clear the current notification:
				Util.removeTaskNotification(_t._id);
				
				// Calculate the new reminder time:
				long newReminderTime = System.currentTimeMillis() + numMinutes*60000;
				
				// Set a new reminder notification based on the snooze time:
				if (_isLocation)
					Util.snoozeLocationReminder(_t, newReminderTime);
				else
					Util.scheduleReminderNotification(_t,newReminderTime);
				
				finish();
			}
        });
    }
	
	/** Called when first started, or when resumed after being in the background: */
	@Override
	public void onResume()
	{
		super.onResume();
		
		Bundle extras = getIntent().getExtras();
		if (extras==null || !extras.containsKey("task_id"))
		{
			Util.log("Missing inputs in SnoozePopup.java");
			finish();
		}
		_isLocation = false;
		if (extras!=null && extras.containsKey("is_location"))
			_isLocation = extras.getBoolean("is_location");
		
		_tasksDB = new TasksDbAdapter();
		_t = _tasksDB.getTask(extras.getLong("task_id"));
		if (_t==null)
		{
			Util.log("Missing tasks in SnoozePopup.java");
			finish();
		}	
	}
	
	/** Called when a new Intent is received for a different task: */
	@Override
	public void onNewIntent(Intent intent)
	{
		setIntent(intent);
	}
	
	
	@Override
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
				// Smartphone sized screen:
				size.x = _screenWidth/2;
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
				size.x = _screenWidth/3;
				size.y = _screenHeight*8/10;
				return size;
			}
			else
			{
				// It must be extra large.  (Can't test directly since the XLARGE constant isn't supported
				// before api level 9).
				size.x = _screenWidth/5;
				size.y = _screenHeight*7/10;
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
				// Smartphone sized screen:
				size.x = _screenWidth*9/10;
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet:
				size.x = _screenWidth*8/10;
				size.y = _screenHeight*7/10;
				return size;
			}
			else
			{
				// It must be extra large.  (Can't test directly since the XLARGE constant isn't supported
				// before api level 9). 
				size.x = _screenWidth*7/10;
				size.y = _screenHeight*6/10;
				return size;
			}
		}
	}
}
