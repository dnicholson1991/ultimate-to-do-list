package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/** Convenience functions to manage GDPR consent. */
public class ConsentUtil
{
    private static final String TAG = "ConsentUtil";

    /** Contains the current consent information. */
    private static ConsentInformation _consentInformation;

    /** The consent form. Will be null if it's not yet ready. */
    private static ConsentForm _consentForm;

    /** Update the current consent information. Call this when the app starts showing a UI. */
    public static void updateConsentInformation(final Activity a)
    {
        ConsentRequestParameters params;
        if (BuildConfig.DEBUG)
        {
            // For testing, put us in a GDPR area.
            ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(a)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(Util.getAdmobTestID(a))
                .build();
            params = new ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings)
                .build();
        }
        else
        {
            params = new ConsentRequestParameters.Builder().build();
        }

        _consentInformation = UserMessagingPlatform.getConsentInformation(a);
        _consentInformation.requestConsentInfoUpdate(
            a,
            params,
            () -> {
                // The consent information state was updated.
                // Check to see if a form is available.
                int status = _consentInformation.getConsentStatus();
                Log.v(TAG,"GDPR consent state updated: "+status);
                if (_consentInformation.isConsentFormAvailable())
                    loadConsentForm(a);
                else
                {
                    if (status== ConsentInformation.ConsentStatus.OBTAINED)
                    {
                        // This happens when the user disables personalized ads in the system
                        // settings.
                        Log.d(TAG,"A consent form was not available. Status: "+status);
                    }
                    else if (status== ConsentInformation.ConsentStatus.REQUIRED)
                    {
                        Log.e(TAG, "No Consent Form Available", "A consent form was not " +
                            "available. Status: "+status);
                    }
                }
            },
            formError -> {
                // Failed to get consent information.
                Log.e(TAG,"Consent Update Failure","Can't get consent information: "+
                    formError.getErrorCode()+" / "+formError.getMessage());
            });
    }

    /** Load the consent form. */
    private static void loadConsentForm(final Activity a)
    {
        UserMessagingPlatform.loadConsentForm(
            a,
            consentForm -> {
                Log.v(TAG,"Consent form loaded. Current status: "+_consentInformation.
                    getConsentStatus());
                _consentForm = consentForm;
            },
            formError -> {
                Log.e(TAG,"Consent Form Load Failure","Can't load consent form: "+
                    formError.getErrorCode()+" / "+formError.getMessage());
            }
        );
    }

    /** Get the current consent status. Returns one of the following: ConsentStatus.UNKNOWN,
     * ConsentStatus.REQUIRED, ConsentStatus.NOT_REQUIRED, ConsentStatus.OBTAINED */
    public static int getConsentStatus()
    {
        if (_consentInformation==null)
            return ConsentInformation.ConsentStatus.UNKNOWN;
        return _consentInformation.getConsentStatus();
    }

    /** Execute a Runnable after we have a consent status that is not unknown. */
    public static void runWhenStatusAvailable(final Runnable r)
    {
        if (getConsentStatus()!= ConsentInformation.ConsentStatus.UNKNOWN)
            r.run();
        else
        {
            new Handler().postDelayed(() -> {
                runWhenStatusAvailable(r);
            },200);
        }
    }

    /** See if the consent form is ready to display. */
    public static boolean isConsentFormReady()
    {
        return (_consentForm!=null);
    }

    /** Show the consent form, and execute the specified Runnable when done. If the consent form
     * failed to load, then this just calls the Runnable. The Runnable may be null. */
    public static void showConsentForm(final Activity a, final Runnable r)
    {
        if (_consentForm==null)
        {
            Log.d(TAG,"Tried to show consent form but it's not ready.");
            if (r!=null) r.run();
            return;
        }
        _consentForm.show(a, formError -> {
            if (formError!=null)
            {
                Log.e(TAG,"Error Showing Consent Form","Got this error when the consent form "+
                    "was dismissed: "+formError.getErrorCode()+ " / "+formError.getMessage());
            }
            else
            {
                Log.i(TAG,"Consent form closed.");
            }
            _consentForm = null;
            loadConsentForm(a);
            if (r!=null) r.run();
        });
    }

    /** Remove all IAB GDPR keys from preferences. This works around a bug in Amazon's SDK
     * that prevents ads from loading. Call this when the user is not in a GDPR area. */
    private static void removeGdprKeys(Context co)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(co);
        Map<String, ?> entries = prefs.getAll();
        SortedSet<String> keys = new TreeSet<String>(entries.keySet());
        for (String key : keys)
        {
            if (key.startsWith("IABTCF_"))
            {
                Log.v(TAG,"Removing prefernce key "+key);
                prefs.edit().remove(key).apply();
            }
        }
    }

    /** Reset the the consent state, to simulate a first install. */
    public static void resetConsentState(Activity c)
    {
        _consentInformation.reset();
        removeGdprKeys(c);
        updateConsentInformation(c);
    }
}

