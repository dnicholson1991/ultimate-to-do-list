package com.customsolutions.android.utl;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import android.app.Fragment;
import android.app.FragmentManager;

/** The Activity for displaying options to purchase add-ons. This just displays the corresponding
 * fragment. */
public class AddOnsActivity extends UtlActivity
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
		
        // Display the add-ons fragment:
		FragmentManager fragmentManager = getFragmentManager();
		StoreItemListFragment addOnsFragment = new StoreItemListFragment();
        fragmentManager.beginTransaction().replace(R.id.full_screen_fragment_wrapper,
			addOnsFragment).commit();
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
