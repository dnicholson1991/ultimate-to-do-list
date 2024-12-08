package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Base64;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GooglePlayInterface
{
    private static final String TAG = "GooglePlayInterface";

    // Product SKUs:
    public static final String MAIN = "com.customsolutions.android.utl.license";
    public static final String UPGRADE = "com.customsolutions.android.utl.upgrade_license";

    /** Unique string used for validating purchases. */
    public static final String DP = "10abd459f32fd6c725dc9a";

    /** Quick reference to the parent Activity. */
    private Activity _a;

    /** Quick reference to the SharedPreferences. */
    private SharedPreferences _prefs;

    /** References the PurchaseManager, which is used by other app components to query purchase
     * status. */
    private PurchaseManager _pm;

    /** Keeps track of tokens from Google for each purchased item. The key is the SKU. */
    private static HashMap<String, String> _purchaseTokens = new HashMap<>();

    /** Maps SKUs to their details. */
    private static HashMap<String, SkuDetails> _skuDetails = new HashMap<>();

    /** References the billing client (the connection to Google's billing service). */
    private static BillingClient _billingClient;

    /** The number of disconnects logged. */
    private static int _numLoggedDisconnects = 0;

    /** Flag indicating if we're in the process of connecting to the billing service. */
    private static boolean _isConnecting = false;

    /** Constructor, which establishes the connection to Google's billing service. */
    public GooglePlayInterface(Activity a)
    {
        Log.v(TAG,"Constructor called.");
        _a = a;
        _prefs = _a.getSharedPreferences(Util.PREFS_NAME, 0);
        _pm = new PurchaseManager(_a);

        // If the billing client is already set up, there is nothing to do.
        if (_billingClient!=null && _billingClient.isReady())
        {
            return;
        }

        // If another instance is in the process of connecting, go no further.
        if (_isConnecting)
            return;

        // Establish a link to the in-app billing service:
        _isConnecting = true;
        _billingClient = BillingClient.newBuilder(a)
            .enablePendingPurchases()
            .setListener((BillingResult billingResult, @Nullable List<Purchase> purchases) -> {
                handlePurchaseUpdates(billingResult, purchases);
            })
            .build();
        _billingClient.startConnection(new BillingClientStateListener()
        {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult)
            {
                _isConnecting = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                {
                    Log.v(TAG,"Billing setup finished successfully.");
                }
                else
                {
                    Log.d(TAG,"Billing setup failed: "+billingResult.getResponseCode()+" / "+
                        billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected()
            {
                // The documentaiton for this says that no retries are needed here because
                // a call to onBillingSetupFinished() will be made when the connection is
                // restored. See:
                // https://developer.android.com/reference/com/android/billingclient/api/BillingClientStateListener
                _numLoggedDisconnects++;
                if (_numLoggedDisconnects<20)
                    Log.d(TAG,"onBillingServiceDisconnected() disconnected called.");
            }
        });
    }

    /** Query Google for a list of in-app items, along with prices.  The information is stored in
     * the list maintained by the PurchaseManager instance. Pass in null to fetch all in-app
     * items. */
    public void getInAppItems(final ArrayList<String> googleSet)
    {
        Runnable r = () -> {
            // Make sure we're in a state where we can fetch in-app items:
            if (!_billingClient.isReady())
            {
                Log.d(TAG,"Aborting fetch of in-app items since the client is not ready.");
                return;
            }

            // Fetch the product info:
            ArrayList<String> querySet = googleSet;
            if (querySet==null)
            {
                querySet = new ArrayList<>();
                for (int i=0; i<PurchaseManager.GOOGLE_SKUS.length; i++)
                    querySet.add(PurchaseManager.GOOGLE_SKUS[i]);
            }
            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(querySet).setType(BillingClient.SkuType.INAPP);
            _billingClient.querySkuDetailsAsync(params.build(), (billingResult, skuDetailsList) -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                {
                    for (SkuDetails skuDetails : skuDetailsList)
                    {
                        Log.v(TAG, "SKU: " + skuDetails.getSku() + "; Price: "+skuDetails.
                            getPrice());
                        _skuDetails.put(skuDetails.getSku(),skuDetails);
                        _pm.setPrice(skuDetails.getSku(),skuDetails.getPrice());
                    }

                    // Also get a list of purchased items:
                    getPurchasedItems();
                }
                else
                {
                    Log.d(TAG,"Can't get SKU details for in-ap billing: "+billingResult.
                        getResponseCode()+" / "+billingResult.getDebugMessage()+"; Is Ready? "+
                        _billingClient.isReady());
                }
            });
        };

        // Perform the fetch after a delay if we don't yet have a connection to the Google Play
        // service.
        if (!_billingClient.isReady())
        {
            Log.v(TAG,"Delaying fetch of in-app items since _billingClient is not ready.");
            new Handler().postDelayed(r,5000);
        }
        else
            r.run();
    }

    /** Get a list of purchased items. */
    private void getPurchasedItems()
    {
        // Get all one-time in-app purchases:
        _billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP,(billingResult, purchases) ->
        {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
            {
                boolean licensePurchased = false;
                HashSet<String> purchasedSKUs = new HashSet<String>();
                if (purchases!=null)
                {
                    for (Purchase purchase : purchases)
                    {
                        String sku = purchase.getSkus().get(0);
                        Log.v(TAG,"Purchased SKU: "+sku);
                        Log.v(TAG,"    State: "+purchase.getPurchaseState());
                        Log.v(TAG,"    Time: "+Util.getDateTimeString(purchase.getPurchaseTime()));
                        Log.v(TAG,"    Token: "+purchase.getPurchaseToken());
                        if (purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED)
                        {
                            _purchaseTokens.put(sku,purchase.getPurchaseToken());
                            if (sku.equals(MAIN) || sku.equals(UPGRADE))
                            {
                                // The license / ad remover has been purchased.
                                _pm.handleCompletedLicensePurchase();
                                _pm.logLicensePurchase(true,sku);
                                _pm.setVerifyTime(System.currentTimeMillis()+7*Util.ONE_DAY_MS);
                                licensePurchased = true;
                            }
                            else
                            {
                                // This is another item:
                                purchasedSKUs.add(sku);
                                _pm.setVerifyTime(System.currentTimeMillis()+7*Util.ONE_DAY_MS);
                            }

                            // Acknowledge the purchase if necessary:
                            if (!purchase.isAcknowledged())
                            {
                                Log.d(TAG, "Need to acknowledge purchase during purchase fetch.");
                                ackPurchase(purchase);
                                broadcastPurchase(sku);
                            }
                        }
                        else
                        {
                            // Item has been cancelled or refunded. PurchaseManager needs
                            // notified if it's not the license.
                            Log.v(TAG,"Got cancellation for "+sku);
                            _pm.cancelPurchase(sku);
                            _pm.setVerifyTime(System.currentTimeMillis()+7*Util.ONE_DAY_MS);
                        }
                    }
                }

                // Sync the purchased items with our local database:
                _pm.syncPurchasedItems(purchasedSKUs,"google");

                if (!licensePurchased)
                {
                    // The license / ad remover is not purchased.
                    Log.v(TAG,"License / ad remover is not purchased.");
                    _pm.invalidateLicensePurchase();
                    _pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
                }
            }
            else
            {
                Log.d(TAG,"Unable to get a list of purchased items: "+billingResult.
                    getResponseCode()+" / "+billingResult.getDebugMessage()+"; Is Ready? "+
                    _billingClient.isReady());
            }
        });
    }

    /** Start the purchase process. Pass in null to automatically choose the correct license /
     * ad remover SKU. */
    public void start(String sku)
    {
        if (!_billingClient.isReady())
        {
            Log.e(TAG,"Billing Client Not Ready","The Billing Client is not ready when "+
                "the user wants to make a purchase.");
            Util.longerPopup(_a, null, _a.getString(R.string.Google_Play_Unresponsive));
            return;
        }

        if (sku==null)
        {
            // Determine the correct license (regular or upgrade) to use.  If the app is already
            // purchased through another method, then use the upgrade license.
            if (_pm.licenseType()==PurchaseManager.LIC_TYPE_EXT_APP || _pm.licenseType()==
                PurchaseManager.LIC_TYPE_REG_CODE)
            {
                sku = UPGRADE;
            }
            else
                sku = MAIN;
        }

        Log.i(TAG,"Starting purchase of SKU "+sku);
        BillingFlowParams.Builder flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(_skuDetails.get(sku));
        BillingResult billingResult = _billingClient.launchBillingFlow(_a,flowParams.build());
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK)
        {
            Log.e(TAG,"Can't Launch Billing Flow","Got this error when launching billowing flow: "+
                billingResult.getResponseCode()+" / "+billingResult.getDebugMessage()+
                "; Is Ready? "+_billingClient.isReady());
            Util.longerPopup(_a, null, _a.getString(R.string.Google_Play_Unresponsive));
        }
    }

    /** Handle purchase updates sent by Google Play Billing. */
    private void handlePurchaseUpdates(BillingResult billingResult, @Nullable List
        <Purchase> purchases)
    {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
        {
            Log.v(TAG, "Received purchase update from Google Play.");
            if (purchases == null)
            {
                Log.v(TAG, "No purchases detected.");
                return;
            }
            for (Purchase purchase : purchases)
            {
                String sku = purchase.getSkus().get(0);
                Log.v(TAG, "SKU: " + sku + "; State: " + purchase.
                    getPurchaseState() + "; Acknowledged? " + purchase.isAcknowledged() +
                    "; JSON: " + purchase.getOriginalJson());
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
                {
                    _purchaseTokens.put(sku, purchase.getPurchaseToken());
                    if (!purchase.isAcknowledged())
                        ackPurchase(purchase);
                    if (sku.equals(MAIN) || sku.equals(UPGRADE))
                    {
                        _pm.handleCompletedLicensePurchase();
                        _pm.logLicensePurchase(false,sku);
                        _pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
                    }
                    else
                    {
                        _pm.recordPurchase(sku,"google");
                        _pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
                    }
                    broadcastPurchase(sku);
                }
                else
                {
                    // An item is no longer purchased. If it's the license / ad remover,
                    // notify PurchaseManager.
                    if (sku.equals(MAIN) || sku.equals(UPGRADE))
                        _pm.invalidateLicensePurchase();
                }
            }
        }
        else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.
            USER_CANCELED)
        {
            Log.i(TAG,"User cancelled purchase.");
        }
        else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.
            ITEM_ALREADY_OWNED)
        {
            // Log the error, but give the user access:
            if (purchases == null || purchases.size() == 0)
            {
                Log.w(TAG, "Item Already Owned", "onPurchasesUpdated() called with error " +
                    "saying item was already owned. Response had no purchases.");
                getInAppItems(null);
                return;
            }
            Log.w(TAG,"Undetected Purchase","In-app item was already owned when user tried to " +
                "buy.");
            for (Purchase purchase : purchases)
            {
                String sku = purchase.getSkus().get(0);
                Log.v(TAG, "SKU: " + sku + "; State: " + purchase.
                    getPurchaseState() + "; Acknowledged? " + purchase.isAcknowledged() +
                    "; JSON: " + purchase.getOriginalJson());
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED)
                {
                    _purchaseTokens.put(sku, purchase.getPurchaseToken());
                    if (!purchase.isAcknowledged())
                        ackPurchase(purchase);
                    if (sku.equals(MAIN) || sku.equals(UPGRADE))
                    {
                        _pm.handleCompletedLicensePurchase();
                        _pm.logLicensePurchase(false,sku);
                        _pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
                    }
                    else
                    {
                        _pm.recordPurchase(sku,"google");
                        _pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
                    }
                    broadcastPurchase(sku);
                }
            }
        }
        else
        {
            Log.e(TAG,"Purchase Update Failure","onPurchasesUpdated() failed with code "+
                billingResult.getResponseCode()+" / "+billingResult.getDebugMessage());
            getInAppItems(null);
        }
    }

    /** Acknowledge a purchase. */
    private void ackPurchase(Purchase purchase)
    {
        if (!purchase.isAcknowledged())
        {
            AcknowledgePurchaseParams ackParams = AcknowledgePurchaseParams.
                newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();
            _billingClient.acknowledgePurchase(ackParams, (BillingResult ackResult) ->
            {
                if (ackResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                {
                    Log.v(TAG, "Purchase acknowledgement successful.");
                    recordOneTimePurchase(_a,purchase);
                }
                else
                {
                    Log.e(TAG, "Purchase Ack Failure", "Purchase acknowledgement " +
                        "failed with code " + ackResult.getResponseCode() + " / " +
                        ackResult.getDebugMessage());
                }
            });
        }
    }

    /** Record a one-time purchase in the Custom Solutions transactions database. This must
     * be done here because's Google's built-in system only supports subscriptions. */
    private void recordOneTimePurchase(Context c, Purchase purchase)
    {
        String username = "djeiogdjeufycl";
        String password = "38436292902749";
        String url = "https://customsolutions.us/api/a2s/play_transactions/record_one_time_purchase";
        Util.UTL_EXECUTOR.execute(() -> {
            try
            {
                // Construct the body of the request to send:
                JSONObject toSend = new JSONObject()
                    .put("package", c.getPackageName())
                    .put("token", purchase.getPurchaseToken())
                    .put("product_id", purchase.getSkus().get(0));
                RequestBody body = RequestBody.create(MediaType.parse(
                    "application/json; charset=utf-8"), toSend.toString());
                Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Basic " + Base64.encodeToString((username + ":" +
                        password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                    .post(body)
                    .build();

                // Create the HTTP Client that weill perform the call:
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();

                // Perform the actual call:
                Log.v(TAG, "Notifying server of one-time purchase...");
                Response response = client.newCall(request).execute();

                // Analyze the response:
                ResponseBody responseBody = response.body();
                if (responseBody == null)
                    Log.d(TAG, "No response body.");
                else
                {
                    Log.v(TAG,"Response code "+response.code()+"; Body: "+response.body().
                        string());
                }
            }
            catch (JSONException e)
            {
                Util.handleException(TAG,e);
            }
            catch (IOException e)
            {
                Log.d(TAG,"Failed to record one-time purcahse at server.",e);
            }
        });

    }

    /** Send a broadcast Intent when a new purchase is detected.  This will allow other
     * components in the app to update themselves upon a purchase. */
    private void broadcastPurchase(String sku)
    {
        Intent i = new Intent(PurchaseManager.ACTION_ITEMS_PURCHASED);
        i.putExtra("sku",sku);
        _a.sendBroadcast(i);
    }

    /** Consume a purchase, which removes it from Google Play's list of purchased items. */
    public void consumePurchase(final String sku)
    {
        if (!_billingClient.isReady())
        {
            Log.e(TAG,"Invalid consumePurchase() call","consumePurchase() called while Google "+
                "Play billing is not connected.");
            return;
        }
        if (!_purchaseTokens.containsKey(sku))
        {
            Log.d(TAG,"consumePurchase() called with SKU that has no purchase token.");
            return;
        }

        // Notify Google Play:
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(_purchaseTokens.get(sku))
            .build();
        _billingClient.consumeAsync(consumeParams, new ConsumeResponseListener()
        {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken)
            {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                {
                    Log.v(TAG,"SKU "+sku+" successfully consumed.");
                    _purchaseTokens.remove(sku);
                }
                else
                {
                    Log.e(TAG,"Purchase Consume Failure","Consume for SKU "+sku+" failed with " +
                        "code "+billingResult.getResponseCode()+" / "+billingResult.
                        getDebugMessage());
                }
            }
        });
    }

    /** Clean up when done.  Call this from the onDestroy() method of the Activity. */
    public void cleanup()
    {
        // Nothing to do here.
    }
}
