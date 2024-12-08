package com.customsolutions.android.utl;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

/**
 * Displays details about an in-app purchase, and allows the user to buy. Pass in a Bundle with
 * the key "sku", containing the item's sku.
 */
public class StoreItemDetail extends UtlPopupActivity
{
    /** Store the in-app item we're getting details for. */
    private InAppItem _inAppItem;

    /** Links to the PurchaseManager, for making purchases. */
    private PurchaseManager _pm;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.store_item_detail);

        // There had better be an extra containing the SKU:
        Bundle b = this.getIntent().getExtras();
        if (b==null)
        {
            Util.log("null Bundle passed to StoreItemDetail.");
            finish();
            return;
        }
        if (!b.containsKey("sku"))
        {
            Util.log("Missing sku when calling StoreItemDetail activity");
            finish();
            return;
        }
        String sku = b.getString("sku");
        Util.log("Getting details for SKU: "+sku);

        // Get the details of the in-app item:
        _pm = new PurchaseManager(this);
        _pm.link();
        _inAppItem = _pm.getInAppItem(sku);

        // Set the text of the item's description:
        WebView webView = (WebView) findViewById(R.id.store_item_long_description);
        webView.loadDataWithBaseURL(null, _inAppItem.long_description, "text/html", "utf-8", null);

        // Set the title at the top:
        getSupportActionBar().setTitle(_inAppItem.title);

        // At the bottom right, show either the price or the word "purchased" if it has been
        // purchased.
        TextView buyButton = (TextView)findViewById(R.id.store_item_detail_buy);
        if (_inAppItem.is_purchased)
            buyButton.setText(R.string.purchased_with_checkmark);
        else
        {
            buyButton.setText(getString(R.string.buy_now) + " (" + _inAppItem.price + ")");

            // If the user taps on this, then start the purchase process:
            buyButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    if (_inAppItem.sku.equals(PurchaseManager.SKU_LICENSE) || _inAppItem.sku.equals(
                        PurchaseManager.SKU_UPGRADE_LICENSE))
                    {
                        // Start the purchase of a license:
                        _pm.startLicensePurchase(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                // We have to refresh the display with Amazon's API:
                                if (Util.IS_AMAZON)
                                    StoreItemDetail.this.onResume();
                            }
                        });
                    }
                    else
                    {
                        // The purchase of something else:
                        _pm.startPurchase(_inAppItem.sku,new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                // We have to refresh the display with Amazon's API:
                                if (Util.IS_AMAZON)
                                    StoreItemDetail.this.onResume();
                            }
                        });
                    }
                }
            });
        }

        // Add a handler for the Cancel button:
        findViewById(R.id.store_item_detail_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });
    }

    @Override
    public void onNewPurchase(String sku)
    {
        if (!_inAppItem.is_purchased && _inAppItem.sku.equals(sku))
        {
            // The user just purchased the item. We can exit.
            if (_inAppItem.sku.equals(PurchaseManager.SKU_ANDROID_WEAR))
            {
                // When the Android Wear add-on is purchased, we need to start the corresponding
                // service.
                try
                {
                    startService(new Intent(this, WearService.class));
                }
                catch (IllegalStateException e)
                {
                    // This will fail on Oreo or later if the app is in the background.
                    Util.log("WARNING: Exception when starting WearService. "+e.getClass().
                        getName()+" / "+e.getMessage());
                }
            }
            finish();
        }
    }

    @Override
    public void onDestroy()
    {
        if (_pm!=null)
            _pm.unlinkFromBillingService();
        super.onDestroy();
    }
}
