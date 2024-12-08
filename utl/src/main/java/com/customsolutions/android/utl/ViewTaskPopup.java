package com.customsolutions.android.utl;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;

public class ViewTaskPopup extends UtlPopupActivity
{
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}
	
	@Override
    protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
		this.setContentView(R.layout.full_screen_fragment_wrapper);
		
        // Display the task viewer fragment:
		FragmentManager fragmentManager = getSupportFragmentManager();
		ViewTaskFragment viewTask = new ViewTaskFragment();
        fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper, viewTask).commit();			
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
       return super.onOptionsItemSelected(item);
	}
}
