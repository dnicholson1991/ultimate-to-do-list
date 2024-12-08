package com.customsolutions.android.utl;

import android.app.Application;

import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.MRAIDPolicy;
import com.facebook.ads.AdSettings;

public class UltimateToDoList extends Application
{
    @Override
    public void onCreate()
    {
        super.onCreate();

        // Amazon Publisher Services initialization:
        AdRegistration.getInstance("8e216ee3-4a0c-43b0-a0e4-79d3d96fed9c",getApplicationContext());
        AdRegistration.setMRAIDSupportedVersions(new String[] {"1.0", "2.0", "3.0"});
        AdRegistration.setMRAIDPolicy(MRAIDPolicy.CUSTOM);
        AdRegistration.useGeoLocation(true);
        if (BuildConfig.DEBUG)
        {
            AdRegistration.enableLogging(true);
            AdRegistration.enableTesting(true);
        }

        if (BuildConfig.DEBUG)
        {
            // Set up Facebook test ads for Galaxy S8+:
            AdSettings.addTestDevice("5cd27bf2-1eee-45ac-9f94-aa8930d270f8");
        }
    }
}
