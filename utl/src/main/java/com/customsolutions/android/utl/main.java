package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;


public class main extends Activity 
{ 
	private PurchaseManager _pm;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    { 
        super.onCreate(savedInstanceState);

        Util.appInit(this);

        // Catch any uncaught exceptions:
    	Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));

        // We need to know the diagonal screen size of the device we're on.  Calculate and store it.
		DisplayMetrics metrics = new DisplayMetrics();
		this.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		double width = metrics.widthPixels/metrics.xdpi;
    	double height = metrics.heightPixels/metrics.ydpi; 
    	Util.updatePref(PrefNames.DIAGONAL_SCREEN_SIZE,(float)Math.hypot(width, height),this);

        // This has to be called from a Context that is an Activity:
        Util.setDefaultSplitScreenPrefs(this);

        Util.log("UTL is starting up (main.java).  SDK Version: "+android.os.Build.VERSION.SDK_INT);
        Util.log("Model: "+android.os.Build.MODEL);
        Util.log("Screen Density Scale Factor: "+metrics.density);

        // Check/set the installation date and other needed info:
        _pm = new PurchaseManager(this);
    	_pm.fetchInstallDate();

        // Start fetching the available in-app items, along with purchase status:
        _pm.link();
        _pm.startFetchingInAppItems();

        // Log the version of Google Play Services installed on the device (if any)
        try
        {
            GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
            int result = availability.isGooglePlayServicesAvailable(this);
            if (result != ConnectionResult.SUCCESS)
            {
                Util.log("Google Play Services is Unavailable: "+availability.getErrorString(result));
            }
            else
            {
                PackageManager packageManager = this.getPackageManager();
                try
                {
                    PackageInfo packageInfo = packageManager.getPackageInfo(
                        GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE, 0);
                    Util.log("Google Play Services Version: "+packageInfo.versionName);
                }
                catch (PackageManager.NameNotFoundException e)
                {
                    Util.log("Google Play Services Version: Unavailable");
                }
            }
        }
        catch (Exception e) { }

        // Check to see if we're registered via a code:
        int stat = _pm.stat();
        if (_pm.stat()==PurchaseManager.DONT_SHOW_ADS && _pm.licenseType()==PurchaseManager.LIC_TYPE_REG_CODE)
        {
        	// At startup, we verify this to make sure the user is not trying to avoid
        	// a purchase:
        	_pm.validatePriorCode();
        }
        
       	// Start the service that checks to see if we are licensed:
        LicenseAppQuery.enqueueWork(this,new Intent(this,LicenseAppQuery.class));

        // Set up system alarms (for scheduled sync and database cleanup):
        Util.refreshSystemAlarms(this);

        if (stat==PurchaseManager.SHOW_ADS)
        {
        	String androidID = Util.getAndroidID();
        	if (androidID.length()>0)
        	{
	        	Util.log("Android ID: "+androidID);
        	}
        	else
        	{
        		Log.e("Main","Bad Android ID","Could not get Android ID.");
        	}
        }

        // Refresh GDPR consent information:
        ConsentUtil.updateConsentInformation(this);

        // Call the appropriate activity that should run at startup.
        
        // If we have no accounts, call the setup wizard:
        AccountsDbAdapter db = new AccountsDbAdapter();
        Cursor c = db.getAllAccounts();
        if (!c.moveToFirst())
        {
        	c.close();
            Intent i = new Intent(this,BeginSetup.class);
            startActivity(i);
            finish();
        }
        else
        {
        	c.close();
        	
            // Clean up any temporary views.  This is safe to do at startup.
            ViewRulesDbAdapter rules = new ViewRulesDbAdapter();
            SQLiteDatabase dbHandle = Util.dbHelper.getWritableDatabase();
            c = dbHandle.query("views", new String[] {"_id"}, "top_level in "+
            	"('temp','subtask','search')", null, null, null, null);
            while (c.moveToNext())
            {
            	long viewID = c.getLong(0);
            	rules.clearAllRules(viewID);
            }
            c.close();
            dbHandle.execSQL("delete from views where top_level in "+
            	"('temp','subtask','search')");

            // If we've just upgraded to a version that supports locations, run an initial
            // sync to download the locations from Toodledo:
            if (Util.settings.getBoolean("location_download_needed", false))
            {
            	Intent i2 = new Intent(this, Synchronizer.class);
                i2.putExtra("command", "full_sync");
                Synchronizer.enqueueWork(this,i2);
            }
            
            // Refresh any task reminders.  This is needed in case the app is installed on
            // the SD card (which prevents it from receiving a bootup notification to
            // trigger the refresh).
            Util.refreshTaskReminders();

            Util.openStartupView(this);
        	finish();
        }
    }
    
    /** If we return here after leaving, run the onCreate() function again to refresh: */
    @Override
    public void onResume()
    {
        super.onResume();
        this.onCreate(null);
    }

    @Override
    public void onDestroy()
    {
        _pm.unlinkFromBillingService();
        super.onDestroy();
    }
}