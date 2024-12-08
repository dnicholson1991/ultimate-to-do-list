package com.customsolutions.android.utl;

import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

public class UtlListFragment extends ListFragment
{
    /** References the banner ad view. */
    protected BannerAd _bannerAd;

    /** For getting current purchase status. */
    public PurchaseManager _purchaseManager;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        Log.i(Log.className(this),"onCreate() called.");
        super.onCreate(savedInstanceState);
        _purchaseManager = new PurchaseManager(getActivity());
        _purchaseManager.link();
    }

    @Override
    public void onResume()
    {
        Log.i(Log.className(this), "onResume() called.");
        super.onResume();
        if (_bannerAd!=null && _purchaseManager.stat()==PurchaseManager.DONT_SHOW_ADS)
        {
            // This can happen if the user just made a purchase, or upon the first run if a
            // purchase was made in a prior install.
            _bannerAd.setVisibility(View.GONE);
            _bannerAd.destroy();
            _bannerAd = null;
        }
        if (_bannerAd!=null && _purchaseManager.stat()==PurchaseManager.SHOW_ADS)
        {
            _bannerAd.onResume();
        }
    }

    /** Fragments that display ads should call this from onCreateView() to initialize AdMob and
     * start ad loading. */
    protected void initBannerAd(View rootView)
    {
        _bannerAd = rootView.findViewById(R.id.banner_ad);
        if (_bannerAd !=null)
        {
            if (_purchaseManager.stat()==PurchaseManager.DONT_SHOW_ADS)
            {
                _bannerAd.setVisibility(View.GONE);
                _bannerAd = null;
            }
            else
            {
                _bannerAd.onResume();
            }
        }
    }

    /** Hide the banner ad. */
    protected void hideBannerAd()
    {
        if (_bannerAd!=null)
        {
            _bannerAd.setVisibility(View.GONE);
            _bannerAd.onPause();
            _bannerAd.destroy();
            _bannerAd = null;
        }
    }

    @Override
    public void onPause()
    {
        Log.i(Log.className(this), "onPause() called.");
        if (_bannerAd!=null && _purchaseManager.stat()==PurchaseManager.SHOW_ADS)
            _bannerAd.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView()
    {
        Log.i(Log.className(this),"onDestroyView() called.");
        if (_bannerAd !=null)
        {
            _bannerAd.destroy();
            _bannerAd = null;
        }
        _purchaseManager.unlinkFromBillingService();
        super.onDestroyView();
    }
}
