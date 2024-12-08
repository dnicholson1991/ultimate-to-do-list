package com.customsolutions.android.utl;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class EditTask extends UtlActivity
{
	private EditTaskFragment _editTask;
	
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.full_screen_fragment_wrapper);	
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
        // Display the task editor fragment.
		FragmentManager fragmentManager = getSupportFragmentManager();
		EditTaskFragment existing = (EditTaskFragment)fragmentManager.findFragmentByTag(
			EditTaskFragment.FRAG_TAG);
		if (existing!=null)
		{
			_editTask = existing;
			fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, existing).
				commit();
			return;
		}

		// If we get here, a new fragment must be created:
		_editTask = new EditTaskFragment();
		fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, _editTask,
			EditTaskFragment.FRAG_TAG).commit();
	}

	@Override
	public void onNewIntent(Intent i)
	{
		super.onNewIntent(i);

		// When opening this activity from a widget, there is a chance this could be called
		// because the activity is still running.
		Util.log("EditTask: onNewIntent() called with Intent: "+Util.intentToString(i,2));

		// Replace the Intent being used by this activity:
		this.setIntent(i);

		// Check to see if there is an existing fragment:
		FragmentManager fragmentManager = getSupportFragmentManager();
		EditTaskFragment existing = (EditTaskFragment)fragmentManager.findFragmentByTag(
			EditTaskFragment.FRAG_TAG);
		if (existing!=null)
		{
			// Kill it.
			fragmentManager.beginTransaction().remove(existing).commitAllowingStateLoss();
		}

		// Create and show a new fragment:
		_editTask = new EditTaskFragment();
		fragmentManager.beginTransaction().add(R.id.full_screen_fragment_wrapper, _editTask,
			EditTaskFragment.FRAG_TAG).commitAllowingStateLoss();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
       return super.onOptionsItemSelected(item);
	}
	
	// Send keystrokes into the EditTaskFragment, to see if it handles them:
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (_editTask.onKeyDown(keyCode, event))
        	return true;
        
        return super.onKeyDown(keyCode,event);
    }
}
