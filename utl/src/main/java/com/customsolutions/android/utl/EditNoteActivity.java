package com.customsolutions.android.utl;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.KeyEvent;
import android.view.MenuItem;

public class EditNoteActivity extends UtlActivity
{
	private EditNoteFragment _editNote;
	
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
		
        // Display the note editor fragment.
		FragmentManager fragmentManager = getSupportFragmentManager();
		EditNoteFragment existing = (EditNoteFragment)fragmentManager.findFragmentByTag(
			EditNoteFragment.FRAG_TAG);
		if (existing!=null)
		{
			_editNote = existing;
			fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, existing).
				commit();
			return;
		}

		// If we get here, a new fragment must be created:
		_editNote = new EditNoteFragment();
		fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, _editNote,
			EditNoteFragment.FRAG_TAG).commit();
	}
	
	// Send keystrokes into the EditTaskFragment, to see if it handles them:
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
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (item.getItemId()==android.R.id.home)
		{
			if (_editNote.onOptionsItemSelected(item))
				return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
}
