package com.customsolutions.android.utl;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/** Adds Save/Cancel buttons to a popup dialog activity. */
public abstract class UtlSaveCancelPopupActivity extends UtlPopupActivity
{
	// Called when activity is first created:
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}
	
	// Place the Save and Cancel buttons in the action bar:
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.save_cancel, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	// Handlers for Save and Cancel buttons:
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem)
	{
		switch (menuItem.getItemId())
		{
		case R.id.menu_save:
			handleSave();
			return true;
			
		case R.id.menu_cancel:
			handleCancel();
			return true;
		}
		return super.onOptionsItemSelected(menuItem);
	}
	
	// Save handler.  Must be overwritten by subclass:
	public abstract void handleSave();
	
	// Cancel handler.  The default implementation just sets the activity result and exits the activity:
	public void handleCancel()
	{
		setResult(Activity.RESULT_CANCELED);
		finish();
	}
}
