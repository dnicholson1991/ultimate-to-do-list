package com.customsolutions.android.utl;

import android.app.Activity;
import android.app.ListFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * This fragment lists in-app items available for purchase.  It displays pricing and purchase status.
 * tapping on an item will open a new Activity containing details along with the option to purchase.
 */
public class StoreItemListFragment extends ListFragment
{
    /** Links to the ListView in the corresponding layout */
    private ListView _listView;

    /** The parent Activity. An instance of either AppCompatActivity or AppCompatPreferenceActivity
     * (which extends the system PreferenceActivity) */
    private Activity _a;

    /** Quick reference to this Fragment's root view */
    private ViewGroup _rootView;

    /** Accesses information on available items and purchases. */
    private PurchaseManager _pm;

    /** The list of available in-app items: */
    private ArrayList<HashMap<String,InAppItem>> _inAppItems;

    /** This adapter is used for managing updates to the display: */
    private SimpleAdapter _adapter;

    private boolean _hasSeenOverlayPrompt;

    /** This returns the view being used by this fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.store_item_list, container, false);
    }

    /** Called when activity is first created. */
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        Util.log("Viewing the items in the store.");

        _a = getActivity();
        _rootView = (ViewGroup)getView();
        _listView = (ListView) _rootView.findViewById(android.R.id.list);
        _inAppItems = new ArrayList<HashMap<String,InAppItem>>();
        _hasSeenOverlayPrompt = false;

        // Link to the in-app billing service on the device.
        _pm = new PurchaseManager(_a);
        _pm.link();

        if (_a instanceof AppCompatActivity)
        {
            AppCompatActivity aca = (AppCompatActivity) _a;
            if (aca.getSupportActionBar()!=null)
                aca.getSupportActionBar().setTitle(R.string.add_ons);
        }
        else if (_a instanceof AppCompatPreferenceActivity)
        {
            AppCompatPreferenceActivity acpa = (AppCompatPreferenceActivity) _a;
            if (acpa.getSupportActionBar() != null)
                acpa.getSupportActionBar().setTitle(R.string.add_ons);
        }
    }

    /** Refresh the display if we return here after leaving. */
    @Override
    public void onResume()
    {
        super.onResume();
        refreshData();

        // If the Tasker plugin is purchased and the user doesn't have permission enabled to
        // draw over other apps, prompt for that permission.
        if (_pm.isPurchased(PurchaseManager.SKU_TASKER) &&
            Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q &&
            !android.provider.Settings.canDrawOverlays(getActivity().getApplicationContext()) &&
            !_hasSeenOverlayPrompt
        )
        {
            new AlertDialog.Builder(_a)
                .setMessage(R.string.overlay_permission_for_tasker)
                .setCancelable(false)
                .setPositiveButton(R.string.allow_permission, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i)
                    {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(Uri.parse("package:" + _a.getPackageName()));
                        startActivity(intent);
                    }
                })
                .setNegativeButton(R.string.cancel_and_disable,null)
                .show();
            _hasSeenOverlayPrompt = true;
        }
    }

    /** Refresh the store items being displayed. */
    public void refreshData()
    {
        // Assemble a detailed list of available items:
        _inAppItems.clear();
        ArrayList<InAppItem> inAppItems = _pm.getInAppItems();
        for (int i=0; i<inAppItems.size(); i++)
        {
            HashMap<String,InAppItem> temp = new HashMap<String, InAppItem>();
            temp.put("item",inAppItems.get(i));
            _inAppItems.add(temp);
        }

        _adapter = new SimpleAdapter(_a,_inAppItems,R.layout.store_row,new String[] {"item"},
            new int[] {R.id.store_row_top_level});

        // Define the function that maps the item data to the on-screen display:
        _adapter.setViewBinder(new SimpleAdapter.ViewBinder()
        {
            @Override
            public boolean setViewValue(View view, Object data, String textRepresentation)
            {
                InAppItem inAppItem = (InAppItem) data;

                // Set the item's title:
                TextView title = (TextView) view.findViewById(R.id.store_row_title);
                title.setText(inAppItem.title);

                // Set the item's description:
                TextView description = (TextView) view.findViewById(R.id.store_row_description);
                description.setText(inAppItem.description);

                // Set the item's price:
                TextView price = (TextView) view.findViewById(R.id.store_row_price);
                if (_pm.isPurchased(inAppItem.sku))
                    price.setText(R.string.purchased);
                else
                    price.setText(inAppItem.price);

                return true;
            }
        });

        this.setListAdapter(_adapter);
    }

    /** Called when an item in the list is clicked: */
    @Override
    public void onListItemClick(ListView listView, View v, int position, long id)
    {
        InAppItem inAppItem = _inAppItems.get(position).get("item");
        Intent i = new Intent(_a,StoreItemDetail.class);
        i.putExtra("sku", inAppItem.sku);
        startActivity(i);
    }

    @Override
    public void onDestroy() {
        if (_pm!=null)
            _pm.unlinkFromBillingService();
        super.onDestroy();
    }
}
