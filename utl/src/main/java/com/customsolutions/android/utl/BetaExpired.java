package com.customsolutions.android.utl;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class BetaExpired extends UtlActivity
{
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
    	super.onCreate(savedInstanceState);
    	
    	// Log what we're doing:
        Util.log("Showing the beta expired message.");
        Util.logOneTimeEvent(this, "show_beta_expiry_msg", 0, null);
        
        // Link this activity with a layout resource:
        setContentView(R.layout.beta_expired);
        
        // Clicking on the download buttons opens our listing in Google Play:
        findViewById(R.id.beta_expired_download_button).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Uri uri = Uri.parse("market://details?id=com.customsolutions.android.utl");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				try
		    	{
		    		BetaExpired.this.startActivity(intent);
		    	}
		    	catch (ActivityNotFoundException e)
		    	{
		    		Util.popup(BetaExpired.this,R.string.Google_Play_Not_Installed);
		    	}
			}
		});
    }
}
