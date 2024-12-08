package com.customsolutions.android.utl;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

public class ViewNote extends UtlActivity
{
	private static final String TAG = "ViewNote";

	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		Log.v(TAG,"onPostCreate called.");

		super.onPostCreate(savedInstanceState);

		this.setContentView(R.layout.full_screen_fragment_wrapper);

		// Display the note viewer fragment:
		FragmentManager fragmentManager = getSupportFragmentManager();
		ViewNoteFragment viewNote = new ViewNoteFragment();
		fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper,
			viewNote).commit();
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
       return super.onOptionsItemSelected(item);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		FragmentManager fragmentManager = getSupportFragmentManager();
		Fragment frag = fragmentManager.findFragmentById(R.id.full_screen_fragment_wrapper);
		if (frag!=null)
		{
			KeyHandlerFragment keyHandlerFrag = (KeyHandlerFragment)frag;
			if (keyHandlerFrag.onKeyDown(keyCode, event))
				return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
}
