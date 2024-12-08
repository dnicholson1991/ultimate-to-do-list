package com.customsolutions.android.utl;

import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Displays a list of new features */
public class WhatsNew extends UtlPopupActivity
{
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
        Util.log("Show what's new.");
        
        setContentView(R.layout.whats_new);
        getSupportActionBar().setTitle(R.string.Whats_New);
        
        WebView tv = (WebView) findViewById(R.id.whats_new_text);
        tv.setWebViewClient(new WebViewClient() {
        	@Override
			public void onPageFinished(WebView view, String url)
			{
				view.scrollTo(0,0);
			}
		});
        tv.loadDataWithBaseURL(null, getString(R.string.whats_new_details), "text/html", "utf-8", null);
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
