package com.customsolutions.android.utl;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

/**
 * Created by Nicholson on 12/19/2014.
 */
public class AndroidWearHelp extends UtlPopupActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Util.log("Show android wear help.");

        setContentView(R.layout.whats_new);
        getSupportActionBar().setTitle(R.string.android_wear_help_title);

        WebView tv = (WebView) findViewById(R.id.whats_new_text);
        tv.loadDataWithBaseURL(null, getString(R.string.android_wear_long_description), "text/html", "utf-8", null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.done, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /** Handlers for the action bar buttons: */
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem)
    {
        switch (menuItem.getItemId())
        {
            case R.id.menu_done:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
