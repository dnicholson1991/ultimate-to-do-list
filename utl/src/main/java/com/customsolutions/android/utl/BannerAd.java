package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.amazon.admob_adapter.APSAdMobCustomBannerEvent;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.DTBAdRequest;
import com.amazon.device.ads.DTBAdSize;
import com.amazon.device.ads.DTBAdView;
import com.amazon.device.ads.DTBFetchFactory;
import com.amazon.device.ads.DTBFetchManager;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import androidx.annotation.NonNull;

/** Holds a banner ad and ensures ads aren't loaded in the background. */
public class BannerAd extends FrameLayout
{
    private static String TAG = "BannerAd";

    // Ad Unit ID for Google AdMob:
    private static final String ADMOB_AD_UNIT_ID = "my_admob_banner_id";

    // Ad Unit IDs for Amazon APS:
    private static final String AMAZON_SMALL_ID = "my_amazon_small_banner_id";
    private static final String AMAZON_LARGE_ID = "my_amazon_large_banner_id";

    /** Use to specify which banner to use. Currently, we have only one. */
    private static final String GENERAL = "general";

    /** Our Admob Banner Ad Unit ID. */
    private String _adUnitID;

    /** Flag indicating if the parent Fragment or Activity is paused. */
    private boolean _isPaused;

    /** Flag indicating if initialization is done and the banner is serving ads. */
    private boolean _initDone;

    /** The Admob Adview. */
    private AdView _adView;

    /** The screen density. */
    private float _density;

    /** The height of the ad, chosen by AdMob, in dp. */
    private int _adHeight;

    /** Flag indicating if the banner is appearing at the top. Default is false, for a bottom ad. */
    private boolean _isAtTop;

    /** The Amazon ad ID for this banner. This is either an ad ID or a slot gtoup name. */
    private String _amazonID;

    /** Flags indicating if the Amazon slot group has been registered. */
    private static HashSet<String> _amazonSlotGroups = new HashSet<>();

    /** The Amazon fetch manager, used for auto refresh. */
    private DTBFetchManager _fetchManager;

    /** The label used for the Amazon fetch manager. */
    private String _fetchManagerLabel;

    /** Flag indicating if the first load call has been made. At first we call load(), then we
     * call resume() */
    private boolean _initialLoadCalled;

    /** Quick reference to SharedPreferences. */
    private SharedPreferences _prefs;

    public BannerAd(Context co)
    {
        super(co);
        _initDone = false;
        _isAtTop = false;
        _prefs = PreferenceManager.getDefaultSharedPreferences(co);
    }

    public BannerAd(Context co, AttributeSet attrs)
    {
        super(co,attrs);
        _initDone = false;
        _prefs = PreferenceManager.getDefaultSharedPreferences(co);
    }

    /** Perform initialization. */
    private void init()
    {
        _initDone = false;

        // Currently, we have only one banner ad unit.
        String location = GENERAL;

        // Fetch the Admob and Amazon ad unit IDs:
        _adUnitID = ADMOB_AD_UNIT_ID;

        // Adaptive ads must have multiple Amazon IDs. Set up a slog group containing
        // them if not already done.
        _amazonID = "slot_group_"+location;
        if (!_amazonSlotGroups.contains(_amazonID))
        {
            // Create the slot group:
            AdRegistration.SlotGroup group = new AdRegistration.SlotGroup(_amazonID);
            group.addSlot(new DTBAdSize(320,50,AMAZON_SMALL_ID));
            group.addSlot(new DTBAdSize(728,90,AMAZON_LARGE_ID));
            AdRegistration.addSlotGroup(group);
            _amazonSlotGroups.add(_amazonID);
        }

        if (!_prefs.getBoolean(PrefNames.DISABLE_AMAZON_ADS,false))
        {
            // Create the Amazon fetch manager:
            DTBAdRequest loader = new DTBAdRequest();
            loader.setSlotGroup(_amazonID);
            _fetchManagerLabel = String.valueOf(System.currentTimeMillis());
            _fetchManager = DTBFetchFactory.getInstance().createFetchManager(
                _fetchManagerLabel, loader, true);
            _fetchManager.start();
        }


        ViewTreeObserver vto = getViewTreeObserver();
        if (vto.isAlive())
        {
            vto.addOnGlobalLayoutListener(() -> {
                init2();
            });
        }
        else
        {
            Log.e(TAG,"ViewTreeObserver not Alive","The ViewTreeObserver is not alive in the "+
                "banner ad.");
            init2();
        }
    }

    /** Second stage of initialization, called after we have the ad's width. */
    private void init2()
    {
        if (_isPaused || _initDone)
            return;

        if (getChildCount()==0)
        {
            _adView = new AdView(getContext());
            _adView.setAdUnitId(_adUnitID);
            AdSize adSize = getAdSize();
            _adHeight = adSize.getHeight();
            Log.v(TAG,"Ad Height: "+_adHeight);
            _adView.setAdSize(adSize);
            addView(_adView);

            ViewGroup.LayoutParams params = getLayoutParams();
            params.height = Math.round(_adHeight*_density);
            setLayoutParams(params);

            Log.v(TAG,"Ad View width in dp: "+(getMeasuredWidth()/_density));
        }

        // Set up the callbacks to execute on ad loading or failure:
        _adView.setAdListener(new AdListener()
        {
            @Override
            public void onAdLoaded()
            {
                String adapterClass = "admob";
                if (_adView.getResponseInfo()!=null)
                {
                    if (_adView.getResponseInfo().getMediationAdapterClassName()!=null)
                    {
                        adapterClass = _adView.getResponseInfo().
                            getMediationAdapterClassName();
                    }
                }
                Log.v(TAG,"AdMob: Ad Loaded. Network: "+adapterClass);

                if (adapterClass.toLowerCase().contains("facebook") && !_isAtTop)
                {
                    // Google's adapter displays the ad too high. Shift it down.
                    int offset = Math.round((_adHeight - 50) * _density) / 2;
                    Log.v(TAG,"Will shift the ad down by this many pixels: "+offset);
                    _adView.getChildAt(0).setTranslationY((float)offset);
                }
                else if (adapterClass.toLowerCase().contains("facebook") && _isAtTop)
                {
                    // Google's adapter displays the ad centered Shift it up.
                    int offset = Math.round((_adHeight - 50) * _density) / 2;
                    Log.v(TAG,"Will shift the ad up by this many pixels: "+offset);
                    _adView.getChildAt(0).setTranslationY(-1*(float)offset);
                }
                else
                    _adView.getChildAt(0).setTranslationY(0);

                // Make sure further loading is stopped if we're in the background:
                if (_isPaused)
                {
                    Log.d(TAG,"Banner was loaded in background.");
                    pauseLoading();
                }
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError)
            {
                Log.d(TAG,"AdMob: Banner failed with error code "+loadAdError.getMessage());

                // Make sure further loading is stopped if we're in the background:
                if (_isPaused)
                {
                    Log.d(TAG,"Banner load was attempted in background.");
                    pauseLoading();
                }
            }

            @Override
            public void onAdOpened()
            {
                Log.d(TAG,"AdMob: onAdOpened()");
            }

            @Override
            public void onAdClicked()
            {
                Log.d(TAG,"AdMob: onAdClicked()");
            }

            @Override
            public void onAdClosed()
            {
                Log.d(TAG,"AdMob: onAdClosed()");
            }

            @Override
            public void onAdImpression()
            {
                Log.d(TAG,"AdMob: onAdImpression()");
            }
        });

        // Only start loading ads after a delay. Some Activities and Fragments may immediately
        // launch another Activity upon startup or resumption.
        new GuardTimer().start(500,() -> {
            if (!_isPaused && !_initDone)
            {
                setVisibility(View.VISIBLE);
                loadAd();
                _initDone = true;
            }
        });
    }

    /** Notify this instance whether is is appearing at the top or bottom of the screen. */
    public void setIsAtTop(boolean isAtTop)
    {
        _isAtTop = isAtTop;
    }

    /** Start loading ads, or refresh if the ad was previously paused. */
    private void loadAd()
    {
        if (_isPaused)
        {
            pauseLoading();
            return;
        }

        if (_initialLoadCalled)
        {
            _adView.resume();
            if (!_prefs.getBoolean(PrefNames.DISABLE_AMAZON_ADS,false))
                _fetchManager.start();
        }
        else
        {
            AdRequest.Builder builder = getBaseAdRequestBuilder();
            if (!_prefs.getBoolean(PrefNames.DISABLE_AMAZON_ADS,false))
            {
                Bundle bundle = new Bundle();
                bundle.putString(DTBAdView.REQUEST_QUEUE, _fetchManagerLabel);
                bundle.putBoolean(DTBAdView.SMARTBANNER_STATE, true);
                builder.addCustomEventExtrasBundle(APSAdMobCustomBannerEvent.class, bundle);
                _fetchManager.start();
            }
            _adView.loadAd(builder.build());
            _initialLoadCalled = true;
        }
    }

    /** Get a base AdRequest builder that includes parameters for all requests. */
    private AdRequest.Builder getBaseAdRequestBuilder()
    {
        AdRequest.Builder builder = new AdRequest.Builder();

        return builder;
    }

    /** Handle a resume of the Activity or Fragment. */
    public void onResume()
    {
        _isPaused = false;
        if (_initDone)
            loadAd();
        else
        {
            // Init hasn't completed yet. Do it now.
            init();
        }
    }

    /** Called when the screen orientation changes. */
    public void onOrientationChange()
    {
        Log.v(TAG,"Handling orientation change.");
        if (_initDone)
            _adView.destroy();
        removeAllViews();
        init();
    }

    /** Get the ad size to use. */
    private AdSize getAdSize()
    {
        if (!(getContext() instanceof Activity))
        {
            Log.e(TAG,"Banner not in Activity","getContext() did not return an Activity "+
                "instance. Class: "+getContext().getClass().getName());
            return AdSize.SMART_BANNER;
        }

        Activity a = (Activity) getContext();
        Display display = a.getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        _density = metrics.density;
        int adWidth = Math.round(getMeasuredWidth()/_density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a,adWidth);
    }

    /** Log a View Hierarchy. */
    public void logViewHeirarchy(ViewGroup vg, int indent)
    {
        String in = "";
        for (int i=0; i<indent; i++)
            in += " ";
        for (int i=0; i<vg.getChildCount(); i++)
        {
            Log.v(TAG,in+"- "+i+": "+vg.getChildAt(i).getClass().getName());
            if (vg.getChildAt(i) instanceof ViewGroup)
                logViewHeirarchy((ViewGroup)vg.getChildAt(i),indent+2);
        }
    }

    /** Handle a pause of the Activity or Fragment. */
    public void onPause()
    {
        _isPaused = true;
        if (_initDone)
            pauseLoading();
    }

    /** Pause loading of ads. Generally called when the Activity goes into the background. */
    private void pauseLoading()
    {
        _adView.pause();
        if (!_prefs.getBoolean(PrefNames.DISABLE_AMAZON_ADS,false))
            _fetchManager.stop();
    }

    /** Destroy the view. */
    public void destroy()
    {
        if (_initDone)
        {
            _adView.destroy();
            _initDone = false;
        }
    }
}
