package com.customsolutions.android.utl;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;

public class EditTagActivity extends UtlActivity
{
	private EditTagFragment _editFragment;
	
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
		EditTagFragment existing = (EditTagFragment)fragmentManager.findFragmentByTag(
			EditTagFragment.FRAG_TAG);
		if (existing!=null)
		{
			_editFragment = existing;
			fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, existing).
				commit();
			return;
		}

		// If we get here, a new fragment must be created:
		_editFragment = new EditTagFragment();
		fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, _editFragment,
			EditTagFragment.FRAG_TAG).commit();
	}
}
