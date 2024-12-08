package com.customsolutions.android.utl;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;

public class EditGoalActivity extends UtlActivity
{
	private EditGoalFragment _editFragment;
	
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
		
        // Display the goal editor fragment.
		FragmentManager fragmentManager = getSupportFragmentManager();
		EditGoalFragment existing = (EditGoalFragment)fragmentManager.findFragmentByTag(
			EditGoalFragment.FRAG_TAG);
		if (existing!=null)
		{
			_editFragment = existing;
			fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, existing).
				commit();
			return;
		}

		// If we get here, a new fragment must be created:
		_editFragment = new EditGoalFragment();
		fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, _editFragment,
			EditGoalFragment.FRAG_TAG).commit();
	}
}
