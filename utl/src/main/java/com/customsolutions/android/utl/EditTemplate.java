package com.customsolutions.android.utl;


import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.KeyEvent;

/**
 * Activity for editing a task template.  This displays an instance of EditTemplateFragment.
 */

public class EditTemplate extends UtlActivity
{
    private EditTemplateFragment _editTemplate;

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

        // Display the template editor fragment.
        FragmentManager fragmentManager = getSupportFragmentManager();
        EditTemplateFragment existing = (EditTemplateFragment)fragmentManager.findFragmentByTag(
            EditTemplateFragment.FRAG_TAG);
        if (existing!=null)
        {
            _editTemplate = existing;
            fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, existing).
                commit();
            return;
        }

        // If we get here, a new fragment must be created:
        _editTemplate = new EditTemplateFragment();
        fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, _editTemplate,
            EditTemplateFragment.FRAG_TAG).commit();
    }

    // Send keystrokes into the EditTemplateFragment, to see if it handles them:
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (_editTemplate.onKeyDown(keyCode, event))
            return true;

        return super.onKeyDown(keyCode,event);
    }
}
