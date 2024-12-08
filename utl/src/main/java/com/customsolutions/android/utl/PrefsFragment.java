package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

@SuppressLint("NewApi")
public class PrefsFragment extends PreferenceFragment
{
    private static final String TAG = "PrefsFragment";

	String _section;
	PrefsActivity _a;

    PurchaseManager _pm;

    /** Flag indicating if the user changed any reminder settings. */
    boolean _remindersChanged;

    /** Flag indicating if a location preference has changed that requires a geofence update. */
    boolean _locationsChanged;

    /** Broadcast receiver for detecting a new purchase. */
    private BroadcastReceiver _purchaseReceiver;

	@Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        _a = (PrefsActivity) getActivity();
        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(Util.PREFS_NAME);

        Bundle args = getArguments();
        _section = args.getString("section");
        _a._section = _section;
        Util.log("Viewing preference section: " + _section);
        _remindersChanged = false;
        _locationsChanged = false;

        // Load the preferences from an XML resource, based on the Bundle passed in:
        if (args.getString("section").equals("account_management"))
        {
            // Need to inflate some XML file to get a default preference screen.  We will then clear
            // it and adjust in the onResume() method.
            addPreferencesFromResource(R.xml.prefs_sync_options);
            setTitle(getString(R.string.Account_Management));
        }
        else if (args.getString("section").equals("sync_options"))
        {
            addPreferencesFromResource(R.xml.prefs_sync_options);
            setTitle(getString(R.string.Sync_Options));
        }
        else if (args.getString("section").equals("display"))
        {
            addPreferencesFromResource(R.xml.prefs_display_options);
            setTitle(getString(R.string.Display_Options));
        }
        else if (args.getString("section").equals("reminder_options"))
        {
            _pm = new PurchaseManager(_a);
            addPreferencesFromResource(R.xml.prefs_reminder_options);
            setTitle(getString(R.string.Reminder_Options));
        }
        else if (args.getString("section").equals("new_task_defaults"))
        {
            addPreferencesFromResource(R.xml.prefs_new_task_defaults);
            setTitle(getString(R.string.New_Task_Defaults));
        }
        else if (args.getString("section").equals("date_and_time"))
        {
            addPreferencesFromResource(R.xml.prefs_date_time);
            setTitle(getString(R.string.Date_and_Time));
        }
        else if (args.getString("section").equals("viewing_and_editing"))
        {
            addPreferencesFromResource(R.xml.prefs_viewing_editing);
            _pm = new PurchaseManager(_a);
            setTitle(getString(R.string.View_Edit_Options));
        }
        else if (args.getString("section").equals("backup_and_restore"))
        {
            addPreferencesFromResource(R.xml.prefs_backup_restore);
            setTitle(getString(R.string.Backup_And_Restore));
        }
        else if (args.getString("section").equals("location_tracking"))
        {
            addPreferencesFromResource(R.xml.prefs_location_tracking);
            setTitle(getString(R.string.Location_Tracking));
        }
        else if (args.getString("section").equals("nav_drawer"))
        {
            addPreferencesFromResource(R.xml.prefs_nav_drawer);
            setTitle(getString(R.string.Navigation_Drawer));
        }
        else if (args.getString("section").equals("voice_mode"))
        {
            addPreferencesFromResource(R.xml.prefs_voice_mode);
            setTitle(getString(R.string.Voice_Mode));
        }
        else if (args.getString("section").equals("android_wear"))
        {
            addPreferencesFromResource(R.xml.prefs_android_wear);
            _pm = new PurchaseManager(_a);
            setTitle(getString(R.string.android_wear));
        }
        else
        {
            addPreferencesFromResource(R.xml.prefs_misc);
            setTitle(getString(R.string.Miscellaneous));
        }
        
        // Remove any preferences that are not applicable due to disabled fields/functions:
        _a.removeUnusedPrefs(getPreferenceScreen());

        // Set up the broadcast receiver to handle new purchases:
        _purchaseReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String sku = intent.getStringExtra("sku");
                Log.v(TAG,"Received broadcast message for purchase of SKU "+sku);
                if (PurchaseManager.SKU_ANDROID_WEAR.equals(sku))
                {
                    try
                    {
                        _a.startService(new Intent(_a, WearService.class));
                    }
                    catch (IllegalStateException e)
                    {
                        // This will fail on Oreo or later if the app is in the background.
                        Util.log("WARNING: Exception when starting WearService. "+e.getClass().
                            getName()+" / "+e.getMessage());
                    }
                }
            }
        };
        IntentFilter purchaseFilter = new IntentFilter(PurchaseManager.ACTION_ITEMS_PURCHASED);
        _a.registerReceiver(_purchaseReceiver,purchaseFilter, Context.RECEIVER_NOT_EXPORTED);
    }
	
	@Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        
        if (_section.equals("miscellaneous"))
        {
        	_a.initMiscPrefs(getPreferenceScreen());
        }
        
        if (_section.equals("new_task_defaults"))
        {
        	_a.initNewTaskDefaults(getPreferenceScreen());
        }
        
        if (_section.equals("voice_mode"))
        {
            _a.initVoiceModePrefs(getPreferenceScreen());
        	_a.initVoiceModeNewTaskDefaults(getPreferenceScreen());
        }
        
        if (_section.equals("backup_and_restore"))
        {
        	_a.initBackupAndRestore(getPreferenceScreen());
        }
        
        if (_section.equals("display"))
        	_a.initDisplaySettings(getPreferenceScreen());
        
        if (_section.equals("sync_options"))
        	_a.initSyncPreferences(getPreferenceScreen());

        if (_section.equals("android_wear"))
        {
            // Initialize the list of views for the default Android Wear task list:
            DatabaseListPreference startupPref = (DatabaseListPreference)getPreferenceScreen().findPreference(PrefNames.
                WEAR_DEFAULT_VIEW_ID);
            _a.initTaskListPreference(startupPref);
        }

        if (_section.equals("reminder_options"))
        {
            findPreference(PrefNames.USE_ONGOING_NOTIFICATIONS).setOnPreferenceChangeListener(
                new Preference.OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    Boolean isSet = (Boolean)newValue;
                    if (isSet && _pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
                    {
                        Util.longerPopup(_a,getString(R.string.warning),getString(R.string.
                            ongoing_notifications_warning));
                    }
                    return true;
                }
            });

            // If the user changes certain reminder preferences, then we need to set a flag
            // indicating this, so that the corresponding notification channel can be updated
            // later.
            Preference.OnPreferenceChangeListener listener = new Preference.
                OnPreferenceChangeListener()
            {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue)
                {
                    Util.log("Preference changed: "+preference.getKey());
                    _remindersChanged = true;
                    return true;
                }
            };
            findPreference(PrefNames.RINGTONE).setOnPreferenceChangeListener(listener);
            findPreference(PrefNames.VIBE_PATTERN).setOnPreferenceChangeListener(listener);
            findPreference(PrefNames.REMINDER_LIGHT).setOnPreferenceChangeListener(listener);
            findPreference(PrefNames.NOTIFICATION_PRIORITY).setOnPreferenceChangeListener(listener);
        }

        if (_section.equals("location_tracking"))
        {
            initLocationPrefs();
        }
    }
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		// We need to refresh the account list after returning from editing an account:
		if (_section.equals("account_management"))
		{
			PreferenceScreen prefScreen = this.getPreferenceScreen();
        	prefScreen.removeAll();
        	
        	_a.addAccountPrefs(prefScreen);
		}

        if (_section.equals("viewing_and_editing"))
        {
            // Hide the setting for starting a manual sort on long press if the manual sort
            // add on is not purchased.
            if (!_pm.isPurchased(PurchaseManager.SKU_MANUAL_SORT))
            {
                PreferenceScreen prefScreen = getPreferenceScreen();
                if (prefScreen.findPreference("manual_sort_on_long_press")!=null)
                    prefScreen.removePreference(prefScreen.findPreference("manual_sort_on_long_press"));
            }
        }

        // Refresh the Android Wear section if we're in it.  We do it in onResume to ensure the
        // screen is refreshed after the user has made a purchase.
        if (_section.equals("android_wear"))
        {
            _pm.link();
            PreferenceScreen prefScreen = getPreferenceScreen();
            if (_pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
            {
                if (prefScreen.findPreference("android_wear_purchase")!=null)
                    prefScreen.removePreference(prefScreen.findPreference("android_wear_purchase"));
            }
            else
            {
                if (prefScreen.findPreference("android_wear_purchased")!=null)
                    prefScreen.removePreference(prefScreen.findPreference("android_wear_purchased"));
                Preference wearPref = prefScreen.findPreference("android_wear_purchase");
                wearPref.setTitle(getString(R.string.purchase_android_wear)+" ("+_pm.getPrice(
                    PurchaseManager.SKU_ANDROID_WEAR)+")");
                wearPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
                {
                    @Override
                    public boolean onPreferenceClick(Preference preference)
                    {
                        try
                        {
                            // Make sure the device has an up-to-date version of Google Play Services installed:
                            GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
                            int result = availability.isGooglePlayServicesAvailable(_a);
                            if (result != ConnectionResult.SUCCESS)
                            {
                                Dialog errorDialog = availability.getErrorDialog(_a, result, 0);
                                errorDialog.show();
                                return true;
                            }
                        }
                        catch (Exception e)
                        {
                            // Working around a bug on Huawei devices.
                        }

                        _pm.startPurchase(PurchaseManager.SKU_ANDROID_WEAR, null);
                        return true;
                    }
                });
            }
        }
	}

    /** Set the action bar title. */
    private void setTitle(String title)
    {
        if (_a.getSupportActionBar()!=null)
            _a.getSupportActionBar().setTitle(title);
    }

    /** Initialize the location preferenes screen. */
    private void initLocationPrefs()
    {
        PreferenceScreen prefScreen = getPreferenceScreen();

        if (Util.IS_GOOGLE)
        {
            // Remove the preferences for location methods and how often to check. They
            // don't apply due to changes in Android.
            prefScreen.removePreference(findPreference(PrefNames.LOCATION_PROVIDERS));
            prefScreen.removePreference(findPreference(PrefNames.LOCATION_CHECK_INTERVAL));
        }

        findPreference(PrefNames.LOC_ALARM_RADIUS).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
        {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o)
            {
                _locationsChanged = true;
                return true;
            }
        });
    }

    @Override
    public void onStop()
    {
        Util.log("onStop() called. Section: "+_section);

        if (_section.equals("android_wear"))
        {
            // When leaving this screen, we schedule or unschedule the daily summary notification.
            Util.scheduleWearDailySummaries(_a);
        }

        if (_remindersChanged)
        {
            Util.log("Reminder settings were changed. Replacing the notification channel.");
            Util.setupReminderNotificationChannel(_a,_a.getSharedPreferences(Util.PREFS_NAME,0),
                true);
            _remindersChanged = false;
        }

        if (_section.equals("sync_options"))
        {
            SharedPreferences prefs = _a.getSharedPreferences(Util.PREFS_NAME,0);
            if (prefs.getInt(PrefNames.SYNC_INTERVAL,60)>0 && prefs.getBoolean(PrefNames.AUTO_SYNC,
                true) && Util.getSyncPolicy()==Util.SCHEDULED_SYNC)
            {
                Util.scheduleAutomaticSync(_a,true);
            }
            else
                Util.cancelAutomaticSync(_a);
        }

        if (_locationsChanged)
            Util.setupGeofencing(_a);

        super.onStop();
    }

    @Override
    public void onDestroy() {
        Util.log("onDestroy(). Section: "+_section);
        super.onDestroy();
        if (_pm!=null)
            _pm.unlinkFromBillingService();
        if (_purchaseReceiver !=null)
            _a.unregisterReceiver(_purchaseReceiver);
    }
}
