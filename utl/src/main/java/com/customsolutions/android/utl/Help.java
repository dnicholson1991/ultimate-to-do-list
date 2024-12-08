package com.customsolutions.android.utl;

// Activity for help and support options.

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class Help extends UtlPopupActivity
{
	private EditText _tempEditText;
	private PurchaseManager _pm;
	private ProgressDialog _progressDialog;
	private String _websiteRoot;
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
        Util.log("Show help and support information.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.help);
        
        _pm = new PurchaseManager(this);
		_pm.link();
        
        // Set the title of this screen:
        getSupportActionBar().setTitle(R.string.Help_and_Support);
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_help));
        
        // Display the version number:
        PackageManager packageManager = this.getPackageManager();
        try
        {
        	PackageInfo packageInfo = packageManager.getPackageInfo(
        		"com.customsolutions.android.utl", 0);
        	TextView tv = (TextView)findViewById(R.id.help_version_txt2);
        	if (Util.IS_AMAZON)
        		tv.setText(packageInfo.versionName+" (Amazon) ");
        	else
        		tv.setText(packageInfo.versionName+" ");
        }
        catch (NameNotFoundException e)
        {
        	
        }

		// Determine whether web links go to the test server or production:
		_websiteRoot = "https://customsolutions.us/";
		if (BuildConfig.DEBUG)
			_websiteRoot = "https://test.customsolutions.us/";

        initBannerAd();

		// Handler for usage upload:
		if (Api.DISABLE_BACKEND)
		{
			findViewById(R.id.help_upload_log).setVisibility(View.GONE);
			findViewById(R.id.help_upload_separator).setVisibility(View.GONE);
		}
		else
		{
			findViewById(R.id.help_upload_log).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					startActivity(new Intent(Help.this, UploadLog.class));
				}
			});
		}
        
        // Handler for download link:
		findViewById(R.id.help_download).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Uri uri = Uri.parse(_websiteRoot+"utl");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(intent);
			}
		});

		// Handler for email:
		findViewById(R.id.help_email).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Uri uri = Uri.parse("mailto:support@customsolutions.us");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(intent);
			}
		});

		// Handler for option to view source:
		findViewById(R.id.help_source).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Uri uri = Uri.parse(_websiteRoot+"utl/source");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(intent);
			}
		});

		// Handler for privacy policy:
		findViewById(R.id.help_privacy_policy).setOnClickListener(
			new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Uri uri = Uri.parse(_websiteRoot+"utl/privacy");
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(intent);
			}
		});

		// Close account / delete datqa:
		findViewById(R.id.help_close_and_delete).setOnClickListener(
			new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					Uri uri = Uri.parse("http://customsolutions.us/deletions.ejs");
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);
					intent.addCategory(Intent.CATEGORY_BROWSABLE);
					startActivity(intent);
				}
			});
        
        // Show color-code reference:
        findViewById(R.id.help_color_code).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				startActivity(new Intent(Help.this,ColorCodeReference.class));
			}
		});
        
        // Long-pressing on the upload log button provides some debugging functions:
        findViewById(R.id.help_upload_log).setOnLongClickListener(new View.OnLongClickListener()
		{
			@Override
			public boolean onLongClick(View v)
			{
	    		// Handler that is called after user enters code:
	    		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
	    			OnClickListener()
				{				
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						switch (which)
						{
						case DialogInterface.BUTTON_POSITIVE:
							String regCode = _tempEditText.getText().toString().trim();
							if (regCode.equals("short sync"))
							{
								Util.popup(Help.this, "Reduced sync interval enabled.");
								Util.updatePref(PrefNames.REDUCED_SYNC_INTERVAL, true, Help.this);
							}
							else if (regCode.equals("crash"))
							{
								int bottom = 0;
								float result = 1000 / bottom;
								return;
							}
							else if (regCode.length()>0)
							{
								_pm.serverCodeCheck(regCode,null);
							}
						}
					}
				};
				
				// Build and display the reg code dialog:
				AlertDialog.Builder builder = new AlertDialog.Builder(Help.this);
				_tempEditText = new EditText(Help.this);
				_tempEditText.setInputType(InputType.TYPE_CLASS_TEXT);
				builder.setView(_tempEditText);
				builder.setTitle(R.string.Enter_Reg_Code);
				builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();
	            return true;
			}
		});

        // Long-pressing on the version number used to bring up the option to enter a
		// registration code. This out of date system is no longer supported, but if the user
		// tries to use a code, give them a way to move to an in-app purchase for free or low
		// cost.
		findViewById(R.id.help_version).setOnLongClickListener((View v) -> {
			String buyNow = getString(R.string.buy_now)+" ("+_pm.getPrice(PurchaseManager.
				SKU_UPGRADE_LICENSE)+")";
			new AlertDialog.Builder(this)
				.setMessage(getString(R.string.reg_code_notice,_pm.getPrice(PurchaseManager.
					SKU_UPGRADE_LICENSE),getString(R.string.email_address)))
				.setPositiveButton(buyNow, (DialogInterface dialog, int which) -> {
					_pm.startUpgradeLicensePurchase();
				})
				.setNegativeButton(R.string.Cancel,null)
				.show();
			return true;
		});
    }

	@Override
	public void onDestroy()
	{
		if (_pm!=null)
			_pm.unlinkFromBillingService();
		super.onDestroy();
	}
}
