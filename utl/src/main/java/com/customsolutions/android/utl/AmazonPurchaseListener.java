package com.customsolutions.android.utl;

// This class handles responses from Amazon's in-app purchasing system.

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.FulfillmentResult;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserDataResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

public class AmazonPurchaseListener implements PurchasingListener
{
	// Product SKUs:
	public static final String MAIN = "com.customsolutions.android.utl.license";
	public static final String UPGRADE = "com.customsolutions.android.utl.upgrade_license";

	static private String _currentUserID = null;
	
	private PurchaseManager _pm;
	
	// This is the expected request ID we should receive back after a purchase.  This is declared as
	// static as a precaution in case multiple instances of this class get created.
	private static String _reqID;
	
	public void setPM(PurchaseManager pm)
	{
		_pm = pm;
	}
	
	public void setRequestID(String rID)
	{
		_reqID = rID;
	}
	
	// Checks to see if we have a user ID:
	public boolean hasUserID()
	{
		if (_currentUserID!=null)
			return true;
		else
			return false;
	}

	/** Processes the list of in-app product data from Amazon. */
	@Override
	public void onProductDataResponse(ProductDataResponse response)
	{
		Util.log("AmazonPL: Received product data response.");
		switch (response.getRequestStatus())
		{
			case SUCCESSFUL:
				Map<String,Product> productData = response.getProductData();
				Iterator<String> it = productData.keySet().iterator();
				_pm.clearInAppItemsList();
				while (it.hasNext())
				{
					Product p = productData.get(it.next());
					Util.log("AmazonPL: "+p.getSku()+": "+p.getPrice());
					_pm.setPrice(p.getSku(),p.getPrice());
				}
				break;

			case FAILED:
				Util.log("AmazonPL: Received FAILED response in onProductDataResponse.");
				break;

			case NOT_SUPPORTED:
				Util.log("AmazonPL: Received NOT_SUPPORTED response in onProductDataResponse.");
				break;
		}

		// After getting product data, we always fetch ownership data.
		PurchasingService.getPurchaseUpdates(true);
	}

	@Override
	public void onPurchaseResponse(PurchaseResponse response)
	{
		if (response.getRequestId().toString().equals(_reqID))
		{
			PurchaseResponse.RequestStatus status = response.getRequestStatus();
			switch (status)
			{
				case ALREADY_PURCHASED:
					Util.log("AmazonPL: Received ALREADY_PURCHASED in onPurchaseResponse()");
					_pm.handleCompletedLicensePurchase();
					_pm.logLicensePurchase(true, "unknown");
					_pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
					break;
					
				case FAILED:
					Util.log("AmazonPL: Received FAILED in onPurchaseResponse()");
					break;
					
				case INVALID_SKU:
					Util.log("AmazonPL: Received INVALID_SKU in onPurchaseResponse()");
					break;
					
				case NOT_SUPPORTED:
					Util.log("AmazonPL: Received NOT_SUPPORTED in onPurchaseResponse()");
					break;
					
				case SUCCESSFUL:
					// Make sure the order is not canceled.
					Receipt r = response.getReceipt();
					if (r.isCanceled())
					{
						Util.log("AmazonPL: Received canceled purchase in onPurchaseResponse.  SKU: "+
							r.getSku());
						break;
					}
					Util.log("AmazonPL: Purchase completed successfully.  SKU: " + response.getReceipt().
						getSku());
					if (response.getReceipt().getSku().equals(PurchaseManager.SKU_LICENSE) ||
						response.getReceipt().getSku().equals(PurchaseManager.SKU_UPGRADE_LICENSE))
					{
						_pm.handleCompletedLicensePurchase();
						_pm.logLicensePurchase(false, response.getReceipt().getSku());
					}
					else
					{
						// A purchase other than the license:
						_pm.recordPurchase(response.getReceipt().getSku(),"amazon");
					}
					_pm.setVerifyTime(System.currentTimeMillis() + Util.ONE_DAY_MS);
                    PurchasingService.notifyFulfillment(response.getReceipt().getReceiptId(), FulfillmentResult.FULFILLED);
					break;
			}
		}
		else
		{
			Util.log("AmazonPL: Mismatched request ID.  Got: "+response.getRequestId().toString()+"; "+
				"expected: "+_reqID);
		}
	}

	@Override
	public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse response)
	{
		switch (response.getRequestStatus())
		{
		case SUCCESSFUL:
			boolean licenseFound = false;
			HashSet<String> purchasedSKUs = new HashSet<String>();
			for (Receipt receipt : response.getReceipts())
			{
				if (receipt.getSku().equals(MAIN) || receipt.getSku().equals(UPGRADE))
				{
					if (!receipt.isCanceled())
					{
						// The purchase has been verified.
						Util.log("AmazonPL: License Verification successful.  SKU: " + receipt.getSku());
						_pm.handleCompletedLicensePurchase();
						_pm.logLicensePurchase(true, receipt.getSku());
						_pm.setVerifyTime(System.currentTimeMillis()+7*Util.ONE_DAY_MS);
						licenseFound = true;
					}
					else
						Util.log("AmazonPL: Found canceled order.  SKU: "+receipt.getSku());
				}
				else
				{
					// This is a product other than the license.  Update our local database with
					// the status.
					if (receipt.isCanceled())
					{
						Util.log("AmazonPL: Found canceled purchase.  SKU: "+receipt.getSku());
						_pm.cancelPurchase(receipt.getSku());
					}
					else
						purchasedSKUs.add(receipt.getSku());
				}
			}

			// Sync the purchased items with our local database:
			_pm.syncPurchasedItems(purchasedSKUs,"amazon");

			if (!licenseFound)
			{
				// No license purchase detected.
				Util.log("AmazonPL: Item is no longer owned, or was never owned.");
				_pm.invalidateLicensePurchase();
				_pm.setVerifyTime(System.currentTimeMillis()+Util.ONE_DAY_MS);
			}
			break;
			
		case FAILED:
			Util.log("AmazonPL: Received FAILED response in onPurchaseUpdatesResponse.");
			break;
			
		case NOT_SUPPORTED:
			Util.log("AmazonPL: Received NOT_SUPPORTED response in onPurchaseUpdatesResponse.");
			break;
		}
	}

	@Override
	public void onUserDataResponse(UserDataResponse userDataResponse)
	{
		UserDataResponse.RequestStatus status = userDataResponse.getRequestStatus();
		
		switch(status)
		{
		case SUCCESSFUL:
			_currentUserID = userDataResponse.getUserData().getUserId();
			Util.log("AmazonPL: Received user ID: "+_currentUserID);
			break;
			
		case FAILED:
			Util.log("AmazonPL: Received FAILED response in onUserDataResponse(). ");
			break;
			
		case NOT_SUPPORTED:
			Util.log("AmazonPL: Received NOT_SUPPORTED response in onUserDataResponse(). ");
			break;
		}
	}

}
