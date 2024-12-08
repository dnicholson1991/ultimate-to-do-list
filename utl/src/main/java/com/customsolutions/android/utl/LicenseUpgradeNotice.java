package com.customsolutions.android.utl;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// Display a popup message asking the user to upgrade the licens to an in-app purchase.

public class LicenseUpgradeNotice extends UtlPopupActivity
{
	private PurchaseManager _pm;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Util.log("Show License Upgrade Notice");
		
		setContentView(R.layout.license_upgrade_notice);
		
		getSupportActionBar().setTitle(R.string.important_information);
		
		_pm = new PurchaseManager(this);
		_pm.link();
		
		// Set the message based on the app store and type of license we're holding:
		TextView msg = (TextView)findViewById(R.id.licence_upgrade_msg);
		if (_pm.licenseType()==PurchaseManager.LIC_TYPE_REG_CODE)
		{
			msg.setText(R.string.License_Upgrade_Info_Reg_Code);
		}
		else
		{
			if (Util.IS_AMAZON)
				msg.setText(R.string.License_Upgrade_Info_Amazon);
			else
				msg.setText(R.string.mandatory_upgrade_notice);
		}

		// Display the price on the "upgrade now" button.
		TextView upgradeNowButton = (TextView)findViewById(R.id.license_upgrade_upgrade_now);
		upgradeNowButton.setText(getString(R.string.Upgrade_Now)+" ("+_pm.getPrice(PurchaseManager.
			SKU_UPGRADE_LICENSE)+")");

		// Handler for upgrading now:
		upgradeNowButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_pm.startLicensePurchase(new Runnable()
				{
					@Override
					public void run()
					{
						// The Amazon API won't refresh this screen automatically, so we have to
						// do it here.
						if (Util.IS_AMAZON)
							LicenseUpgradeNotice.this.onResume();
					}
				});
			}
		});
		
		// Handler for being reminded later:
		findViewById(R.id.license_upgrade_remind_later).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Util.updatePref(PrefNames.UPGRADE_NOTIFICATION_TIME, System.currentTimeMillis()+7*
					Util.ONE_DAY_MS, LicenseUpgradeNotice.this);
				finish();
			}
		});
		
		// Handler to never be reminded again:
		findViewById(R.id.license_upgrade_never_again).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Util.updatePref(PrefNames.UPGRADE_NOTIFICATION_TIME, 0L, LicenseUpgradeNotice.this);
				finish();
			}
		});
	}
	
	/** Called when first started, or when resumed after being in the background: */
	@Override
	public void onResume()
	{
		super.onResume();
		
		// Check to see if the user has purchased the in-app license.
		if (_pm.stat()==PurchaseManager.DONT_SHOW_ADS && _pm.licenseType()==PurchaseManager.LIC_TYPE_IN_APP)
		{
			finish();
		}
	}

	@Override
	public void onNewPurchase(String sku)
	{
		// Check to see if the user has purchased the in-app license.
		if (_pm.stat()==PurchaseManager.DONT_SHOW_ADS && _pm.licenseType()==PurchaseManager.LIC_TYPE_IN_APP)
		{
			finish();
		}
	}

    /** Overrides the default size function, taking into account the small size of this popup: */
    @Override
    protected Point getPopupSize()
    {
    	// Start with default size:
    	Point size = super.getPopupSize();
    	
    	int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
    	if (getOrientation()==ORIENTATION_LANDSCAPE)
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth/2;
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				size.y = _screenHeight*7/10;
				return size;
			}
    	}
    	else
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth*9/10;
				size.y = _screenHeight/2;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*8/10;
				size.y = _screenHeight/3;
				return size;
			}
    	}
    	
    	return size;
    }

	@Override
	public void onDestroy()
	{
		if (_pm!=null)
			_pm.unlinkFromBillingService();
		super.onDestroy();
	}
}
