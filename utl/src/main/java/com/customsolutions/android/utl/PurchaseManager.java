package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import androidx.fragment.app.Fragment;

import com.amazon.device.iap.PurchasingService;
import com.google.android.ads.mediationtestsuite.MediationTestSuite;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

// PM = purchase manager
// This class is a central location for all code related to the free trial and licensing.

public class PurchaseManager
{
	// Licensing models the app can run in:
	public static final int MODEL_AD_SUPPORTED = 1;   // User can pay to remove ads.
	public static final int MODEL_BETA = 2;  // Beta.  App expires on a specific date.  User must download
	                                         // latest version after this date.
	public static final int MODEL_NO_LIMIT = 3;  // Unlimited usage forever.  No free trial or expiration.

    // Available SKUs:
	public static final String SKU_LICENSE = "com.customsolutions.android.utl.license";
	public static final String SKU_UPGRADE_LICENSE = "com.customsolutions.android.utl.upgrade_license";
    public static final String SKU_ANDROID_WEAR = "com.customsolutions.android.utl.wear";
	public static final String SKU_MANUAL_SORT = "com.customsolutions.android.utl.manual_sort";
	public static final String SKU_TASKER = "com.customsolutions.android.utl.tasker";

	// SKU's available to Amazon customers:
	public static final String[] AMAZON_SKUS = {
		SKU_LICENSE,
		SKU_UPGRADE_LICENSE,
		SKU_MANUAL_SORT,
		SKU_TASKER
	};

	// SKU's available to Google customers:
	public static final String[] GOOGLE_SKUS = {
		SKU_LICENSE,
		SKU_UPGRADE_LICENSE,
		SKU_ANDROID_WEAR,
		SKU_MANUAL_SORT,
		SKU_TASKER,
		"com.customsolutions.utl.survey_plan1",
		"com.customsolutions.utl.survey_plan2",
		"com.customsolutions.utl.survey_plan3",
		"com.customsolutions.utl.survey_plan4"
	};

	/** Intent action for notifying other app components that items have been purchased. */
	public static final String ACTION_ITEMS_PURCHASED = "com.customsolutions.android.utl."+
		"items_purchased";

    // Length of the free trial (ms):
	public static final long TRIAL_LENGTH = 14*24*60*60*1000;

    // The table we're working with, which holds purchases other than the license.  Any SKUs not in
    // the table are not purchased.
    private static final String TABLE = "purchases";
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+ "("+
        "_id integer primary key autoincrement, "+
        "sku text not null"+
        ")";

	// This table holds information on all available products, including the license.  This is
	// obtained from Google Play or Amazon:
	private static final String ITEM_TABLE = "in_app_items";
	private static final String ITEM_TABLE_CREATE = "create table if not exists "+ITEM_TABLE+" ("+
		"_id integer primary key autoincrement, "+
		"sku text not null, "+
		"price text not null"+
		")";

    private static boolean _tablesCreated = false;

	private int _model;
	private Context _c;
	private SharedPreferences _settings;
	
	// Beta expiration, if applicable:
	private static final long BF1 = 32003L;
	private static final long BF2 = 44533L;
	private static long BETA_EXPIRY = BF1*BF2*1000;  // Feb 28th, 2015, end of day
	
	// Used for Google in-app billing:
	private GooglePlayInterface _googleInterface;
	
	private ProgressDialog _progressDialog;
	
	// An instance of the Amazon Purchase Listener:
	private AmazonPurchaseListener _apl;
	
	// A Fragment to refresh if a purchase succeeds.
	private Fragment _refreshFragment;

    // Run this piece of code upon a successful in-app purchase (not the free trial unlock).
    public Runnable _purchaseRunnable;

	/** Maps each SKU to a title. */
	private HashMap<String,String> _skuToTitle;

	/** Maps each SKU to a description. */
	private HashMap<String,String> _skuToDescription;

	/** Maps each SKU to a long description. */
	private HashMap<String,String> _skuToLongDescription;

    public static void createTable(SQLiteDatabase db)
    {
        db.execSQL(TABLE_CREATE);
		db.execSQL(ITEM_TABLE_CREATE);
        _tablesCreated = true;
    }

	@SuppressLint("SuspiciousIndentation")
	PurchaseManager(Context c)
	{
		_c = c;
		_settings = _c.getSharedPreferences(Util.PREFS_NAME, 0);
		
		// Derive the licensing model.  Alter this code if needed to set the beta.
		_model = MODEL_AD_SUPPORTED;

        if (!_tablesCreated)
        {
            Util.db().execSQL(TABLE_CREATE);
			Util.db().execSQL(ITEM_TABLE_CREATE);
            _tablesCreated = true;
        }

		// Set up the Hash Maps that map the SKUs to titles and descriptions:

		_skuToTitle = new HashMap<String,String>();
		_skuToDescription = new HashMap<String,String>();
		_skuToLongDescription = new HashMap<String,String>();

		_skuToTitle.put(SKU_LICENSE,c.getString(R.string.ad_remover));
		_skuToDescription.put(SKU_LICENSE,c.getString(R.string.ad_remover_summary));
		_skuToLongDescription.put(SKU_LICENSE,c.getString(R.string.ad_remover_long_description));

		_skuToTitle.put(SKU_UPGRADE_LICENSE,c.getString(R.string.upgraded_license));
		_skuToDescription.put(SKU_UPGRADE_LICENSE,c.getString(R.string.ad_remover_upgrade_summary));
		_skuToLongDescription.put(SKU_UPGRADE_LICENSE,c.getString(R.string.upgrade_license_long_description));

		_skuToTitle.put(SKU_ANDROID_WEAR,c.getString(R.string.android_wear_add_on));
		_skuToDescription.put(SKU_ANDROID_WEAR,c.getString(R.string.android_wear_features));
		_skuToLongDescription.put(SKU_ANDROID_WEAR,c.getString(R.string.android_wear_long_description));

		_skuToTitle.put(SKU_MANUAL_SORT,c.getString(R.string.manual_sort_add_on));
		_skuToDescription.put(SKU_MANUAL_SORT,c.getString(R.string.manual_sort_description));
		_skuToLongDescription.put(SKU_MANUAL_SORT,c.getString(R.string.manual_sort_long_description));

		_skuToTitle.put(SKU_TASKER,c.getString(R.string.tasker_plugin));
		_skuToDescription.put(SKU_TASKER,c.getString(R.string.tasker_plugin_description));
		_skuToLongDescription.put(SKU_TASKER,c.getString(R.string.tasker_plugin_long_description));
	}
	
	// Begin the process of fetching the installation date.  This does nothing if the install date
	// is already fetched.  This starts the service which checks our server.
	public void fetchInstallDate()
	{
		if (!getInstallDateConfirmationStatus() || !_settings.contains(PrefNames.USER_ID))
		{
			// We haven't yet confirmed the install date and received other needed info, such as 
			// the user ID.  Start the service to do so:
			// Set up a repeating alarms that trigger a check.  These will repeat until the date is 
			// verified.
			Intent i = new Intent(_c,ServerRegistration.class);
			_c.startService(i);
		}
		if (getStoredInstallDate()==0)
		{
			// Set the installation date to the current date, to be verified later.
			setInstallDate(System.currentTimeMillis());
		}
	}
	
	// Record the install date, once it is known.  Called from a separate service.
	public void recordInstallDate(long timestamp)
	{
		setInstallDate(timestamp);
		setInstallDateConfirmationStatus(true);
	}
	
	// Get the install date:
	public long getInstallDate()
	{
		return getStoredInstallDate();
	}
	
	// Get the number of days left in the free trial:
	public long getDaysLeftInTrial()
	{
		long timeDiff = getStoredInstallDate()+TRIAL_LENGTH-System.currentTimeMillis();
		if (timeDiff<0)
			timeDiff = 0;
		timeDiff += 12*60*60*1000; // For rounding to the nearest day.
    	return (timeDiff/(24*60*60*1000));
	}
	
	// Perform the transition from beta to free trial mode:
	public void transitionFromBetaToTrial()
	{
		if (!_settings.contains(PrefNames.LAST_BETA_REMINDER))
		{
			// The user never used the beta, or has already transitioned, so there's nothing to do.
			return;
		}
		
		// Set the installation date to today (even though our server will have a different date).
		// This will give the user the full trial period.
		setInstallDate(System.currentTimeMillis());
		setInstallDateConfirmationStatus(true);
		
		// Remove the SharedPreference for the beta:
		SharedPreferences.Editor editor = _settings.edit();
		editor.remove(PrefNames.LAST_BETA_REMINDER);
		editor.commit();
	}
	
	/** Enforce the beta.  This displays a popup if the user hasn't seen a popup in a while. It also
	 * starts the BetaExpired activity if the beta has expired. 
	 * @param a - The calling Activity. 
	 * @return - true if the calling Activity is being finished.  On a true return value, the caller
	 *      should return from the onCreate() - or similar - method. */
	public boolean enforceBeta(Activity a)
	{
		if (stat()==BETA_EXPIRED)
		{
			Intent i = new Intent(a,BetaExpired.class);
			a.startActivity(i);
			a.finish();
			return true;
		}
		
		if (stat()==IN_BETA)
		{
			long timeDiff = System.currentTimeMillis()-_settings.getLong(PrefNames.LAST_BETA_REMINDER,0);
			if (timeDiff>(7*Util.ONE_DAY_MS))
			{
				Util.updatePref(PrefNames.LAST_BETA_REMINDER, System.currentTimeMillis());
				DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
	        	    OnClickListener()
				{				
				    @Override
				    public void onClick(DialogInterface dialog, int which)
				    {
					    if (which == DialogInterface.BUTTON_POSITIVE)
					    {
					    	Uri uri = Uri.parse("market://details?id=com.customsolutions.android.utl");
							Intent intent = new Intent(Intent.ACTION_VIEW, uri);
							try
					    	{
					    		_c.startActivity(intent);
					    	}
					    	catch (ActivityNotFoundException e)
					    	{
					    		Util.popup(_c,R.string.Google_Play_Not_Installed);
					    	}
					    }
				    }
				};
				AlertDialog.Builder builder = new AlertDialog.Builder(a);
				builder.setTitle(R.string.Beta_Info_Header);
				String betaInfo = Util.getString(R.string.Beta_Info);
				betaInfo = betaInfo.replace("2/28/2015", Util.getDateString(BETA_EXPIRY));
				builder.setMessage(betaInfo);
				builder.setPositiveButton(R.string.Check_Google_Play2, dialogClickListener);
				builder.setNegativeButton(R.string.Remind_Me_Later, dialogClickListener);
				builder.show();
			}
		}
		return false;
	}
	
	// Get overall status.  Return one of the following:
	public static final int SHOW_ADS = 1;   // Free access. Ad-supported.
	public static final int DONT_SHOW_ADS = 3;    // User has paid to remove ads.
	public static final int IN_BETA = 4;     // In beta.  Beta has not expired yet.
	public static final int BETA_EXPIRED = 5;    // Beta has expired.  User is locked out.
	public int stat()
	{
		if (Util.IS_FREE)
			return DONT_SHOW_ADS;

		switch(_model)
		{
		case MODEL_AD_SUPPORTED:
			if (isRegisteredByCode() || isPurchasedViaExternalLicense() || isLicensePurchasedInApp())
			{
				// User has paid through one of the payment methods.
				return DONT_SHOW_ADS;
			}
			else
				return SHOW_ADS;
			
		case MODEL_BETA:
			if (System.currentTimeMillis()>BETA_EXPIRY)
				return BETA_EXPIRED;
			else
				return IN_BETA;
			
		case MODEL_NO_LIMIT:
			// No free trial / unlimited usage.
			return DONT_SHOW_ADS;
			
		default:
			return DONT_SHOW_ADS;
		}
	}
	
	// Get the type of license the user has (if any).  Returns one of the following:
	public static final int LIC_TYPE_IN_APP = 1;   // In-app purchase
	public static final int LIC_TYPE_EXT_APP = 2;  // License app
	public static final int LIC_TYPE_REG_CODE = 3; // Registration code
	public static final int LIC_TYPE_PREPAID = 4;  // Payment occurred at time of download.
	public static final int LIC_TYPE_NONE = 5;     // No purchase
	public int licenseType()
	{
		if (isLicensePurchasedInApp())
			return LIC_TYPE_IN_APP;
		if (isPurchasedViaExternalLicense())
			return LIC_TYPE_EXT_APP;
		if (isRegisteredByCode())
			return LIC_TYPE_REG_CODE;
		return LIC_TYPE_NONE;
	}
	
	/** Link to the in-app billing service.  This can safely be called more than one time.  It does
	  * nothing if the link is already established.  In an Activity or Fragment that starts or checks
	  * billing status, this should be called in onCreate() or in onActivityCreated(). */
	public void link()
	{
		// We're in the trial, or the trial has expired.
		if (Util.IS_GOOGLE)
		{
			if (_googleInterface==null)
				_googleInterface = new GooglePlayInterface((Activity)_c);
		}
		else if (Util.IS_AMAZON)
		{
			if (_apl==null)
			{
				_apl = new AmazonPurchaseListener();
				_apl.setPM(this);
				PurchasingService.registerListener(_c.getApplicationContext(), _apl);
			    Util.log("PM: Sandbox mode is:" + PurchasingService.IS_SANDBOX_MODE);
			    if (!_apl.hasUserID())
			    	PurchasingService.getUserData();
			}
		}
	}

	/** Unlink from the in-app billing service.  Typically called in the Activity's onDestroy()
	 *  method. */
	public void unlinkFromBillingService()
	{
		// We're in the trial, or the trial has expired.
		if (Util.IS_GOOGLE)
		{
			if (_googleInterface!=null)
				_googleInterface.cleanup();
		}
		else if (Util.IS_AMAZON)
		{
			// Not applicable.
		}
	}

	//
	// Public methods for license purchase handling:
	//

	/** Initiate the license purchase process. Does nothing if the purchase has already been made. */
	public void startLicensePurchase()
	{
		int stat = stat();
		int type = licenseType();
		if (stat== SHOW_ADS || type==LIC_TYPE_EXT_APP || type==LIC_TYPE_REG_CODE)
		{
			link();
			if (Util.IS_GOOGLE)
				_googleInterface.start(null);
			else if (Util.IS_AMAZON)
			{
				// Determine the correct license (regular or upgrade) to use.  If the app is already
				// purchased through another method, then use the upgrade license.
				String itemString;
				if (licenseType()==PurchaseManager.LIC_TYPE_EXT_APP || licenseType()==PurchaseManager.LIC_TYPE_REG_CODE)
					itemString = AmazonPurchaseListener.UPGRADE;
				else
					itemString = AmazonPurchaseListener.MAIN;
				String reqID = PurchasingService.purchase(itemString).toString();
				Util.log("PM: Starting.  Request ID: "+reqID);
				_apl.setRequestID(reqID);
			}
		}
	}

	/** Initiate the license purchase process, forcing the cheaper license upgrade to be chosen. Does nothing
	 * if the purchase has already been made. */
	public void startUpgradeLicensePurchase()
	{
		int stat = stat();
		int type = licenseType();
		if (stat== SHOW_ADS || type==LIC_TYPE_EXT_APP || type==LIC_TYPE_REG_CODE)
		{
			link();
			if (Util.IS_GOOGLE)
				_googleInterface.start(GooglePlayInterface.UPGRADE);
			else if (Util.IS_AMAZON)
			{
				String reqID = PurchasingService.purchase(AmazonPurchaseListener.UPGRADE).
					toString();
				Util.log("PM: Starting.  Request ID: "+reqID);
				_apl.setRequestID(reqID);
			}
		}
	}
	
	/** Start the license purchase process, passing in a Fragment to be refreshed if the purchase is
	  * successful. */
	public void startLicensePurchase(Fragment refreshFragment)
	{
		_refreshFragment = refreshFragment;
		startLicensePurchase();
	}

	/** Start the license purchese processing, including a Runnable to execute after a successful
	 * purchase. */
	public void startLicensePurchase(Runnable r)
	{
		_purchaseRunnable = r;
		startLicensePurchase();
	}

	/** Call this when the in-app purchase process has been completed successfully. */
	public void handleCompletedLicensePurchase()
	{
		setInAppLicensePurchaseStatus(true);

		if (_refreshFragment!=null && _refreshFragment.isVisible())
			_refreshFragment.onResume();

		if (_purchaseRunnable!=null)
		{
			_purchaseRunnable.run();
			_purchaseRunnable = null;
		}
	}

	/** Invalidate a previous purchase of the free trial unlock.  Call this if a payment has been refunded. */
	public void invalidateLicensePurchase()
	{
		setInAppLicensePurchaseStatus(false);
	}

	/** Verify the purchase (free trial unlock) is still valid.  This does nothing if it's not yet
	 * time to verify (Unless a force check is done.) */
	public void verifyLicensePurchase(boolean forceCheck)
	{
		if (Util.IS_FREE)
			return;

		if (forceCheck || (System.currentTimeMillis()>_settings.getLong(PrefNames.IN_APP_RECHECK_TIME, 0)))
		{
			Util.log("PM: Checking for Purchase.");
			link();
			if (Util.IS_GOOGLE)
			{
				_googleInterface.getInAppItems(null);
			}
			else if (Util.IS_AMAZON)
			{
				PurchasingService.getPurchaseUpdates(true);
			}
		}
	}

	/** Set the next time verification should occur that the trial has been unlocked: */
	public void setVerifyTime(long ts)
	{
		Util.updatePref(PrefNames.IN_APP_RECHECK_TIME, ts, _c);
	}

	/** Record a free trial unlock purchase, making note of whether this is a new purchase or a
	 * verification of an old purchase. This does not update the license purchase status in the app.
	 * @param alreadyDone - true if a purchase was made previously (say, in another install).
	 * @param sku - What was purchased.  Put "unknown" if this cannot be determined.
	 */
	public void logLicensePurchase(boolean alreadyDone, String sku)
	{
		String stat = "new";
		if (alreadyDone)
			stat = "existing";

		if (Util.IS_GOOGLE)
			Util.logOneTimeEvent(_c, "purchase", 0, new String[] {"in-app","google",stat,sku});
		else if (Util.IS_AMAZON)
			Util.logOneTimeEvent(_c, "purchase", 0, new String[] {"in-app","amazon",stat,sku});
		else
			Util.logOneTimeEvent(_c, "purchase", 0, new String[] {"in-app","web",stat,sku});
	}

	//
	// Methods for processing of purchases other than the license, or that apply to both license
	// and non-license items.
	//

	/** Start fetching a list of in-app items that are available from Google or Amazon.  Note that
	 * this requires a network connection.  If a network connection is unavailable, then the
	 * existing list is unaffected.  This will obtain both the price and purchase status of each
	 * item. */
	public void startFetchingInAppItems()
	{
		if (Util.IS_AMAZON)
		{
			HashSet<String> amazonSet = new HashSet<String>();
			for (int i=0; i<AMAZON_SKUS.length; i++)
				amazonSet.add(AMAZON_SKUS[i]);
			PurchasingService.getProductData(amazonSet);
		}
		if (Util.IS_GOOGLE)
		{
			ArrayList<String> googleSet = new ArrayList<String>();
			for (int i=0; i<GOOGLE_SKUS.length; i++)
				googleSet.add(GOOGLE_SKUS[i]);
			_googleInterface.getInAppItems(googleSet);
		}
	}

	/** Set the price for an item. The items is added to the in-app item list if it doesn't yet
	 * exist.
	 * @param sku The SKU of the item.
	 * @param price A string containing the price, with the currency symbol.
	 */
	public void setPrice(String sku, String price)
	{
		ContentValues values = new ContentValues();
		values.put("sku",sku);
		values.put("price",price);
		Cursor c = Util.db().query(ITEM_TABLE,new String[] {"_id"},"sku=?",new String[] {sku},null,
			null,null);
		if (c.moveToFirst())
		{
			Util.db().update(ITEM_TABLE,values,"_id=?",new String[] {Long.valueOf(c.getLong(0)).
				toString()});
		}
		else
		{
			Util.db().insert(ITEM_TABLE, null, values);
		}
		c.close();
	}

	/** Get the price for an SKU.  Assumes the price has been previously fetched. */
	public String getPrice(String sku)
	{
		Cursor c = Util.db().query(ITEM_TABLE,new String[] {"price"},"sku=?",new String[] {sku},null,
			null,null);
		String price = "";
		if (c.moveToFirst())
			price = c.getString(0);
		c.close();
		return price;
	}

	/** Clear the list of in-app items.  Call this just before the list is refreshed with data
	 * from Google or Amazon. */
	public void clearInAppItemsList()
	{
		Util.db().delete(ITEM_TABLE, null, null);
	}

	/** Get information on a single in-app item, given its SKU. */
	public InAppItem getInAppItem(String sku)
	{
		InAppItem item = new InAppItem();
		item.sku = sku;
		item.title = _skuToTitle.get(sku);
		item.description = _skuToDescription.get(sku);
		item.long_description = _skuToLongDescription.get(sku);
		item.price = getPrice(sku);
		if (sku.equals(SKU_LICENSE))
		{
			if (licenseType()==LIC_TYPE_IN_APP || licenseType()==LIC_TYPE_EXT_APP ||
				licenseType()==LIC_TYPE_REG_CODE)
			{
				item.is_purchased = true;
			}
			else
				item.is_purchased = false;
		}
		else if (sku.equals(SKU_UPGRADE_LICENSE))
		{
			if (licenseType()==LIC_TYPE_IN_APP)
				item.is_purchased = true;
			else
				item.is_purchased = false;
		}
		else
		{
			item.is_purchased = isPurchased(sku);
		}
		return item;
	}

	/** Get a list of all in-app items, including price and purchase status. */
	public ArrayList<InAppItem> getInAppItems()
	{
		ArrayList<InAppItem> list = new ArrayList<InAppItem>();
		InAppItem item;

		// Add an appropriate license item to the list.  What we add depends on whether the license
		// is purchased and what type of license the user has, if any.
		if (stat()!= DONT_SHOW_ADS)
		{
			// License not purchased.  Add an item to purchase it.
			item = new InAppItem();
			item.sku = SKU_LICENSE;
			item.title = _skuToTitle.get(SKU_LICENSE);
			item.description = _skuToDescription.get(SKU_LICENSE);
			item.long_description = _skuToLongDescription.get(SKU_LICENSE);
			item.price = getPrice(SKU_LICENSE);
			item.is_purchased = false;
			list.add(item);
		}
		else
		{
			if (licenseType()==LIC_TYPE_IN_APP)
			{
				// License is purchased in-app.  Add an item confirming this.
				item = new InAppItem();
				item.sku = SKU_LICENSE;
				item.title = _skuToTitle.get(SKU_LICENSE);
				item.description = _skuToDescription.get(SKU_LICENSE);
				item.long_description = _skuToLongDescription.get(SKU_LICENSE);
				item.price = getPrice(SKU_LICENSE);
				item.is_purchased = true;
				list.add(item);
			}
			else if (licenseType()==LIC_TYPE_EXT_APP || licenseType()==LIC_TYPE_REG_CODE)
			{
				// License is purchased through external app or registration code.  Offer the
				// user a chance to upgrade.
				item = new InAppItem();
				item.sku = SKU_UPGRADE_LICENSE;
				item.title = _skuToTitle.get(SKU_UPGRADE_LICENSE);
				item.description = _skuToDescription.get(SKU_UPGRADE_LICENSE);
				item.long_description = _skuToLongDescription.get(SKU_UPGRADE_LICENSE);
				item.price = getPrice(SKU_UPGRADE_LICENSE);
				item.is_purchased = false;
				list.add(item);
			}
		}

		// Add in the Android Wear add-on if this is the Google version.
		if (Util.IS_GOOGLE)
		{
			item = new InAppItem();
			item.sku = SKU_ANDROID_WEAR;
			item.title = _skuToTitle.get(SKU_ANDROID_WEAR);
			item.description = _skuToDescription.get(SKU_ANDROID_WEAR);
			item.long_description = _skuToLongDescription.get(SKU_ANDROID_WEAR);
			item.price = getPrice(SKU_ANDROID_WEAR);
			item.is_purchased = isPurchased(SKU_ANDROID_WEAR);
			list.add(item);
		}

		// Add in the manual sort add-on:
		item = new InAppItem();
		item.sku = SKU_MANUAL_SORT;
		item.title = _skuToTitle.get(SKU_MANUAL_SORT);
		item.description = _skuToDescription.get(SKU_MANUAL_SORT);
		item.long_description = _skuToLongDescription.get(SKU_MANUAL_SORT);
		item.price = getPrice(SKU_MANUAL_SORT);
		item.is_purchased = isPurchased(SKU_MANUAL_SORT);
		list.add(item);

		// Add in the tasker plugin:
		item = new InAppItem();
		item.sku = SKU_TASKER;
		item.title = _skuToTitle.get(SKU_TASKER);
		item.description = _skuToDescription.get(SKU_TASKER);
		item.long_description = _skuToLongDescription.get(SKU_TASKER);
		item.price = getPrice(SKU_TASKER);
		item.is_purchased = isPurchased(SKU_TASKER);
		list.add(item);

		return list;
	}

	/** Start the in-app purchase process for an item other than the free trial unlock.
	 * @param sku
	 * @param purchaseSuccessRunnable - Run after successful purchase. May be null.
	 */
	public void startPurchase(String sku, Runnable purchaseSuccessRunnable)
	{
		_purchaseRunnable = purchaseSuccessRunnable;
		link();
		if (Util.IS_GOOGLE)
			_googleInterface.start(sku);
		else if (Util.IS_AMAZON)
		{
			String reqID = PurchasingService.purchase(sku).toString();
			Util.log("PM: Starting purchase of "+sku+".  Request ID: "+reqID);
			_apl.setRequestID(reqID);
		}
	}

	/** Record a purchase other than the free trial unlock.  This does nothing if the purchase has
	 * already been made.
	 * @param sku - The SKU of the purchased item.
	 * @param appStore - The store it was purchased in.  Either "amazon" or "google". */
    public void recordPurchase(String sku, String appStore)
    {
        Cursor c = Util.db().query(TABLE,new String[] {"_id"},"sku='"+sku+"'",null, null, null ,null);
        if (!c.moveToFirst())
        {
            Util.log("PM: Purchase recorded for : "+sku);
            ContentValues values = new ContentValues();
            values.put("sku",sku);
            Util.db().insert(TABLE, null, values);

			Util.logEvent(_c, "add_on_purchase", 0, new String[]{sku, appStore});

			if (_purchaseRunnable!=null)
			{
				_purchaseRunnable.run();
				_purchaseRunnable = null;
			}
		}
        c.close();
    }

	/** Record the cancellation of a purchase other than the license. */
	public void cancelPurchase(String sku)
	{
		Util.log("PM: Recording cancellation of purchase: "+sku);
		Util.db().delete(TABLE, "sku='"+sku+"'", null);
	}

	/** Sync a list of purchased items (other than the license) downloaded from Google or Amazon
	 * with the local list.  Any items not in the passed-in SKU list will be marked as not
	 * purchased.
	 * @param purchasedSKUs List of SKUs
	 * @param appStore Either "amazon" or "google" */
	public void syncPurchasedItems(HashSet<String> purchasedSKUs, String appStore)
	{
		// If any SKUs are not in our local table, then add them.
		Iterator<String> it = purchasedSKUs.iterator();
		while (it.hasNext())
		{
			recordPurchase(it.next(),appStore);
		}

		// If any SKUs exist in the local table but are not in the list, then remove them.
		ArrayList<String> toRemove = new ArrayList<String>();
		Cursor c = Util.db().query(TABLE,new String[] {"sku"},null, null, null, null ,null);
		while (c.moveToNext())
		{
			String sku = c.getString(0);
			if (!purchasedSKUs.contains(sku))
			{
				toRemove.add(sku);
			}
		}
		c.close();
		for (int i=0; i<toRemove.size(); i++)
		{
			cancelPurchase(toRemove.get(i));
		}
	}

    /** Check to see if a particular SKU has been purchased. */
    public boolean isPurchased(String sku)
    {
		if (Util.IS_FREE)
			return true;

		if (sku.equals(SKU_LICENSE))
		{
			if (stat()== DONT_SHOW_ADS)
				return true;
			else
				return false;
		}
		else if (sku.equals(SKU_UPGRADE_LICENSE))
		{
			if (licenseType()==LIC_TYPE_EXT_APP || licenseType()==LIC_TYPE_REG_CODE)
				return false;
			else
				return true;
		}

        Cursor c = Util.db().query(TABLE,new String[] {"_id"},"sku='"+sku+"'",null, null, null ,null);
        if (c.moveToFirst())
        {
            c.close();
            return true;
        }
        c.close();

        // Check to see if I have added any SKUs using my secret code:
        if (_settings.contains(PrefNames.INAPP_PURCHASE_OVERRIDES))
        {
            String[] skus = _settings.getString(PrefNames.INAPP_PURCHASE_OVERRIDES,"").split("\n");
            for (int i=0; i<skus.length; i++)
            {
                if (skus[i].equals(sku))
                    return true;
            }
        }

        return false;
    }

	//
	// Functions for handling older payment methods (license app and reg code)
	//
	
	/** Set payment status when using license app: */
	public void setAppStatus(boolean stat)
	{
	    if (stat)
        {
            setExternalLicensePurchase(true);
            _settings.edit().putBoolean(PrefNames.SUCCESSFUL_EXTERNAL_LICENSE_CHECK,true).apply();
        }
        else
        {
            if (_settings.getBoolean(PrefNames.SUCCESSFUL_EXTERNAL_LICENSE_CHECK,false))
            {
                // A successful license check occurred previously. Ignore all failures from
                // this point.
            }
            else
                setExternalLicensePurchase(false);
        }
	}
		
	/** Check to see if a previously entered code is invalid, and update the status if not. */
	public void validatePriorCode()
	{
		if (!isRegCodeValid())
			setIsRegisteredByCode(false);
	}
	
	/** Check a code that was just entered.  This launches a AsyncTask in the background.
	  * The Runnable passed in is executed on success. */
	@SuppressLint("NewApi")
	public void serverCodeCheck(String code, Runnable runnable)
	{
		Util.log("Code: "+code);

		if (code.equals("stat"))
		{
			// Display license debugging information:
			String debug = "User ID: "+_settings.getLong(PrefNames.USER_ID, 0)+"\n";
			debug += "Device ID: "+_settings.getLong(PrefNames.DEVICE_ID, 0)+"\n";
			debug += "Install ID: "+_settings.getLong(PrefNames.INSTALL_ID, 0)+"\n";

			try
			{
				PackageInfo packageInfo = _c.getPackageManager().getPackageInfo(
					_c.getPackageName(),0);
				debug += "Version Code: "+packageInfo.versionCode+"\n";
			}
			catch (Exception e) { }

			debug += "License Model: ";
			switch (_model)
			{
			case MODEL_AD_SUPPORTED:
				debug += "Free Trial\n";
				break;
			case MODEL_BETA:
				debug += "Beta\n";
				break;
			case MODEL_NO_LIMIT:
				debug += "No Limits\n";
				break;
			}
			
			debug += "Install Date: "+Util.getDateTimeString(getInstallDate(), _c)+"\n";
			debug += "Install Date Confirmed: "+(getInstallDateConfirmationStatus() ? "Yes" : "No")+"\n";
			
			if (_model== MODEL_AD_SUPPORTED)
				debug += "Days Left In Trial: "+ getDaysLeftInTrial()+"\n";
			
			debug += "Overall Status: ";
			switch (stat())
			{
			case SHOW_ADS:
				debug += "Show Ads\n";
				break;
			case DONT_SHOW_ADS:
				debug += "Don't Show Ads\n";
				break;
			case IN_BETA:
				debug += "In Beta\n";
				break;
			case BETA_EXPIRED:
				debug += "Beta Expired\n";
				break;
			}
			
			debug += "License Type: ";
			switch (licenseType())
			{
			case LIC_TYPE_IN_APP:
				debug += "In-App\n";
				break;
			case LIC_TYPE_EXT_APP:
				debug += "External App\n";
				break;
			case LIC_TYPE_REG_CODE:
				debug += "Reg Code\n";
				break;
			case LIC_TYPE_PREPAID:
				debug += "Prepaid\n";
				break;
			case LIC_TYPE_NONE:
				debug += "None\n";
				break;
			}
			
			debug += "Purchased In-App? "+(isLicensePurchasedInApp() ? "Yes" : "No")+"\n";
			debug += "Purchased Externally? "+(isPurchasedViaExternalLicense() ? "Yes" : "No")+"\n";
			debug += "Purchased Via Code? "+(isRegisteredByCode() ? "Yes" : "No")+"\n";
			
			debug += "In-App Verify Time: ";
			if (_settings.getLong(PrefNames.IN_APP_RECHECK_TIME, 0)==0)
				debug += "None\n";
			else
				debug += Util.getDateTimeString(_settings.getLong(PrefNames.IN_APP_RECHECK_TIME, 0), _c)+"\n";
			
			debug += "Upgrade Notice Time: ";
			if (_settings.getLong(PrefNames.UPGRADE_NOTIFICATION_TIME, 0)==0)
				debug += "None\n";
			else
				debug += Util.getDateTimeString(_settings.getLong(PrefNames.UPGRADE_NOTIFICATION_TIME, 0), _c)+"\n";
			
			debug += "Amazon in Sandbox Mode? "+(PurchasingService.IS_SANDBOX_MODE ? "Yes" : "No")+"\n";
			debug += "App is debug version? "+BuildConfig.DEBUG;

			Util.longerPopup(_c, "Status", debug);
			return;
		}

		if (code.equals("ad remover"))
		{
			code = "purchase "+PurchaseManager.SKU_LICENSE;
		}

        if (code.startsWith("purchase "))
        {
            String sku = code.substring(9);
            String currentSKUs = _settings.getString(PrefNames.INAPP_PURCHASE_OVERRIDES,"");
            currentSKUs += sku+"\n";
            Util.updatePref(PrefNames.INAPP_PURCHASE_OVERRIDES,currentSKUs);
            Util.log("Override: "+sku);
            return;
        }

		if (code.startsWith("unpurchase "))
		{
			String sku = code.substring(11);
			String[] skuList = _settings.getString(PrefNames.INAPP_PURCHASE_OVERRIDES,"").split(
				"\n");
			String newSkuList = "";
			for (int i=0; i<skuList.length; i++)
			{
				if (!skuList[i].equals(sku) && skuList[i].length()>0)
				{
					newSkuList += skuList[i]+"\n";
				}
			}
			Util.updatePref(PrefNames.INAPP_PURCHASE_OVERRIDES,newSkuList);
			return;
		}

		if (code.equals("clear purchases") || code.equals("cp"))
		{
			if (Util.IS_GOOGLE)
			{
				for (String sku : GOOGLE_SKUS)
					_googleInterface.consumePurchase(sku);
			}
			Util.db().execSQL("delete from "+TABLE);
			_settings.edit().putString(PrefNames.INAPP_PURCHASE_OVERRIDES,"").apply();
			return;
		}

        if (code.equals("summary test"))
        {
            Intent summaryIntent = new Intent(_c, WearService.class);
            summaryIntent.setAction(WearService.ACTION_SHOW_DAILY_SUMMARY);
            _c.startService(summaryIntent);
            return;
        }

        if (code.equals("admob"))
		{
			MediationTestSuite.launch(_c);
			return;
		}

		if (code.equals("log"))
		{
			_settings.edit().putBoolean(PrefNames.LOG_TO_LOGCAT,true).apply();
			Util.popup(_c,"Acknowledged.");
			return;
		}

		if (code.toLowerCase().equals("disable amazon"))
		{
			Util.popup(_c,"Amazon ads disabled.");
			_settings.edit().putBoolean(PrefNames.DISABLE_AMAZON_ADS,true).apply();
			return;
		}

		if (code.toLowerCase().equals("enable amazon"))
		{
			Util.popup(_c,"Amazon ads enabled.");
			_settings.edit().putBoolean(PrefNames.DISABLE_AMAZON_ADS,false).apply();
			return;
		}

		if (code.equals("upgrade notice"))
		{
			_settings.edit().putLong(PrefNames.UPGRADE_NOTIFICATION_TIME,System.currentTimeMillis()-
				60000).apply();
			Util.popup(_c,"License upgrade notice will show.");
			return;
		}

		if (code.equalsIgnoreCase("survey reset"))
		{
			_settings.edit()
				.putBoolean(PrefNames.SHUTDOWN_COMPLETE,false)
				.putBoolean(PrefNames.SHUTDOWN_BLOCKED,false)
				.putLong(PrefNames.SHUTDOWN_SHOW_TIME,0)
				.apply();
			Util.popup(_c,"Survey settings reset.");
			return;
		}

		/* This functionality has been discontinued.
		// Show a progress dialog 
		_progressDialog = ProgressDialog.show(_c, null, Util.
			getString(R.string.Checking_Code),false);
		
		if (Build.VERSION.SDK_INT>11)
			new CheckRegCode().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,code,runnable);
		else
			new CheckRegCode().execute(code,runnable);
		 */
	}
	

	//
	// Private Methods
	//
	
	//
	// Installation date functions (private):
	//
	
	static private long configMask = 708930807000L;
	
	// Get the software installation date, as stored locally.  Returns 0 if not found.
    private long getStoredInstallDate()
    {
    	long installDate = _settings.getLong("general_config1",0);
    	if (installDate==0)
    		return 0;
    	
    	// Unencrypt the install date and return:
    	installDate += configMask;
    	return installDate;
    }
    
    // Set the software installation date:
    private void setInstallDate(long cDate)
    {
    	Util.updatePref("general_config1", cDate-configMask, _c);
    }
    
    // Get a boolean that determines if the install date has been confirmed at our
    // server.  Returns true if confirmed:
    private boolean getInstallDateConfirmationStatus()
    {
    	if (_settings.getInt("general_config2",73)==73)
    		return false;
    	else if (_settings.getInt("general_config2",73)==57)
    		return true;
    	else
    		return false;
    }
    
    // Set the confirmation status of the install date:
    private void setInstallDateConfirmationStatus(boolean status)
    {
    	if (status)
    		Util.updatePref("general_config2", 57, _c);
    	else
    		Util.updatePref("general_config2", 73, _c);
    }
    
    //
    // Purchase status functions using in-app purchase (private):
    //
    
    /** Get whether an in-app license purchase has occurred: */
    private boolean isLicensePurchasedInApp()
    {
    	if (_settings.getString(PrefNames.IN_APP_STATUS, "").equals(GooglePlayInterface.DP))
    		return true;
    	else
    		return false;
    }
    
    // Set whether an in-app purchase has occurred:
    private void setInAppLicensePurchaseStatus(boolean isEnabled)
    {
    	if (isEnabled)
    		Util.updatePref(PrefNames.IN_APP_STATUS, GooglePlayInterface.DP, _c);
    	else
    		Util.updatePref(PrefNames.IN_APP_STATUS,"", _c);
    }
    
    //
    // Purchase status function using external license app (private):
    //
    
    // Get purchase status:
    private boolean isPurchasedViaExternalLicense()
    {
    	return (_settings.getBoolean("general_config3",false));
    }
    
    // Set purchase status:
    private void setExternalLicensePurchase(boolean isPurchased)
    {
    	Util.updatePref("general_config3",isPurchased, _c);
    }
    
    //
    // Purchase status functions involving a registration code:
    //
    
    // If the user has entered a registration code, is it valid?
    private boolean isRegCodeValid()
    {
    	if (!_settings.contains("general_config4") || !_settings.contains("general_config5"))
    		return false;
    	
    	// Get the expected key value:
    	String regCode = _settings.getString("general_config4", "0");
    	int i;
    	ArrayList<Integer> charCodeArray = new ArrayList<Integer>();
    	for (i=0; i<regCode.length(); i++)
    		charCodeArray.add(regCode.codePointAt(i));
    	String androidID = Util.getAndroidID();
    	if (androidID.length()==0) 
    		return false;
    	for (i=0; i<androidID.length(); i++)
    		charCodeArray.add(androidID.codePointAt(i));
    	String expectedCode = "";
    	for (i=0; i<charCodeArray.size(); i++)
    		expectedCode += Integer.valueOf(charCodeArray.get(i)).toString();
    	
    	// Compare to the actual value:
    	if (expectedCode.equals(_settings.getString("general_config5", "0")))
    		return true;
    	else
    		return false;
    }
    
    // Check to see if this is registered via a registration code:
    private boolean isRegisteredByCode()
    {
    	return _settings.getBoolean("general_config6", false);
    }
    
    // Update whether this is registered via a registration code:
    private void setIsRegisteredByCode(boolean isRegistered)
    {
    	Util.updatePref("general_config6", isRegistered, _c);
    }
}
