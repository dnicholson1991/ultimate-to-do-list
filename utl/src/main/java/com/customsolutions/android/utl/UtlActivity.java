package com.customsolutions.android.utl;

// Base class for all UTL activity instances.  This includes some common and utility functions.
// This also includes some extra functions to allow it to optionally function as a ListActivity.

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;

import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.HashMap;

public class UtlActivity extends AppCompatActivity
{
    /** Request code for permissions requests. */
    private static final int PERMISSION_REQUEST_CODE = 27649;

	public SharedPreferences _settings;
	
    // Some layout operations require access to the screen width and height.  Store those here.
	public int _screenWidth;
	public int _screenHeight;
    
	// A hash that maps attribute IDs to resource IDs (for colors, drawables, etc).  This is 
    // used to reduce lookups from the system and improve performance.
    private HashMap<Integer,Integer> _resourceIdHash;
    
    // Public constants that specify whether we are in portrait or landscape mode:
    static public int ORIENTATION_PORTRAIT = 1;
    static public int ORIENTATION_LANDSCAPE = 2;
    
    // The current orientation, refreshed with each call to onCreate();
    private int _orientation;
    
    private ActionMode _actionMode;
    
    // For emulating a ListActivity:
    private ListView _listView;
    private AdapterView.OnItemClickListener _onListClickListener;
    
    // Flag indicating if a specific fragment wants to handle the "home" button. Default is false.
    private boolean _fragmentHandlesHome;

    /** The permissions the app is currently asking for. */
    private String[] _permissionsRequested;

    /** The runnable to execute after a set of permissions has been approved. */
    private Runnable _permissionAcceptanceRunnable;

    /** Flag indicating if the current permissions request can be denied by the user. */
    private boolean _permissionDenialAllowed;

    /** The message to display if the user denies a permission. */
    private String _permissionDenialMessage;

    /** The runnable to execute if the user denies a permission. */
    private Runnable _permissionDenialRunnable;

    /** References the Banner ad view. */
    protected BannerAd _bannerAd;

    /** For getting current purchase status. */
    public PurchaseManager _purchaseManager;

    /** Broadcast receiver for detecting a new purchase. */
    private BroadcastReceiver _purchaseReceiver;

    // Called when activity is first created:
	@SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        Util.appInit(this);

        if (!(this instanceof UtlTransparentActivity))
        {
        	Util.setTheme(this);
        }
        
        _settings = this.getSharedPreferences(Util.PREFS_NAME,0);
        _fragmentHandlesHome = false;
        
        if (_resourceIdHash==null)
        	_resourceIdHash = new HashMap<Integer,Integer>();
        else
        	_resourceIdHash.clear();
        
        Display disp = getWindowManager().getDefaultDisplay();
        if (android.os.Build.VERSION.SDK_INT>=13)
        {
        	Point sizePoint = new Point();
        	disp.getSize(sizePoint);
        	_screenWidth = sizePoint.x;
        	_screenHeight = sizePoint.y;
        }
        else
        {
        	_screenWidth = disp.getWidth();
        	_screenHeight = disp.getHeight();
        }
        if (_screenWidth>_screenHeight)
        	_orientation = ORIENTATION_LANDSCAPE;
        else
        	_orientation = ORIENTATION_PORTRAIT;      

        _actionMode = null;
        
        // Display the "back" button at the top by default:
        try
        {
            ActionBar ab = getSupportActionBar();
            if (ab != null)
                ab.setDisplayHomeAsUpEnabled(true);
        }
        catch (NullPointerException e)
        {
            // This can happen when using a transparent background.
            Util.log("Got NullPointerException. "+e.getMessage());
        }
        
        //
        // For emulating a ListActivity:
        //
        
        _onListClickListener = new AdapterView.OnItemClickListener() 
        {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                onListItemClick((ListView) parent, v, position, id);
            }
        };
        
    	// Get current purchase status:
        _purchaseManager = new PurchaseManager(this);
        _purchaseManager.link();

        // Set up the broadcast receiver to handle new purchases:
        _purchaseReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                String sku = intent.getStringExtra("sku");
                Log.v(UtlActivity.this.getClass().getSimpleName(),"Received broadcast " +
                    "message for purchase of SKU "+sku);
                if (_bannerAd!=null && _purchaseManager.stat()==PurchaseManager.DONT_SHOW_ADS)
                {
                    // The user just purchased the ad remover, so remove the banner ad.
                    _bannerAd.setVisibility(View.GONE);
                    _bannerAd.destroy();
                    _bannerAd = null;
                }
                onNewPurchase(sku);
            }
        };
        IntentFilter purchaseFilter = new IntentFilter(PurchaseManager.ACTION_ITEMS_PURCHASED);
        registerReceiver(_purchaseReceiver,purchaseFilter, Context.RECEIVER_NOT_EXPORTED);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		
		_listView = (ListView) findViewById(android.R.id.list);
    	if (_listView!=null)
    	{
    		_listView.setOnItemClickListener(_onListClickListener);
    		View emptyView = findViewById(android.R.id.empty);
    		if (emptyView != null)
    			_listView.setEmptyView(emptyView);
    	}
	}

    @Override
    protected void onResume()
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

    /** Activities that display ads should call this from onCreate() to initialize AdMob and
     * start ad loading. */
    protected void initBannerAd()
    {
        Log.v(Log.className(this),"test 1");
        _bannerAd = findViewById(R.id.banner_ad);
        if (_bannerAd !=null)
        {
            Log.v(Log.className(this),"test 2");
            if (_purchaseManager.stat()==PurchaseManager.DONT_SHOW_ADS)
            {
                Log.v(Log.className(this),"test 3");
                _bannerAd.setVisibility(View.GONE);
                _bannerAd = null;
            }
            else
                _bannerAd.onResume();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);

        int numDenials = 0;
        Util.log("Result of permissions request:");
        for (int i = 0; i < permissions.length; i++)
        {
            Util.log(permissions[i] + ": " + grantResults[i]);
            if (grantResults[i]!=PackageManager.PERMISSION_GRANTED)
                numDenials++;
        }

        if (numDenials==0)
        {
            if (_permissionAcceptanceRunnable!=null)
                _permissionAcceptanceRunnable.run();
            else
                Util.log("WARNING: _permissionAcceptanceRunnable is null.");
        }
        else
        {
            if (_permissionDenialAllowed)
            {
                if (_permissionDenialMessage==null)
                {
                    if (_permissionDenialRunnable!=null)
                        _permissionDenialRunnable.run();
                    return;
                }

                new AlertDialog.Builder(this)
                    .setMessage(_permissionDenialMessage)
                    .setPositiveButton(R.string.allow_permission, new DialogInterface.
                        OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            requestPermissions(_permissionsRequested,
                                _permissionAcceptanceRunnable, _permissionDenialAllowed,
                                _permissionDenialMessage,_permissionAcceptanceRunnable);
                        }
                    })
                    .setNegativeButton(R.string.reject_permission, new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i)
                        {
                            if (_permissionDenialRunnable!=null)
                                _permissionDenialRunnable.run();
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialogInterface)
                        {
                            if (_permissionDenialRunnable!=null)
                                _permissionDenialRunnable.run();
                        }
                    })
                    .show();
            }
            else
            {
                new AlertDialog.Builder(this)
                    .setMessage(_permissionDenialMessage)
                    .setPositiveButton(R.string.OK, null)
                    .setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface)
                        {
                            requestPermissions(_permissionsRequested,
                                _permissionAcceptanceRunnable, _permissionDenialAllowed,
                                _permissionDenialMessage,_permissionAcceptanceRunnable);
                        }
                    })
                    .show();
            }
        }
    }

    /** Subclasses can override this method to handle new purchases. */
    public void onNewPurchase(String sku)
    {
        // The base implementaiton does not do anything.
    }

    // Handler for the "search" hardware button:
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_SEARCH)
        {
        	startActivity(new Intent(this,QuickSearch.class));
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }    
    
    /** Call this from a fragment to have the fragment handle the "home" button */
    public void fragmentHandlesHome(boolean newValue)
    {
    	_fragmentHandlesHome = newValue;
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (item.getItemId()==android.R.id.home && !_fragmentHandlesHome)
		{
			// By default, the "home" button finishes the activity.
			finish();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
    // Get a resource ID from an attribute in the current theme.  The attribute ID is like:
    // R.attr.xxxx
    public int resourceIdFromAttr(int attributeID)
    {
    	if (_resourceIdHash.containsKey(attributeID))
    		return _resourceIdHash.get(attributeID);
    	
    	int[] attrs = new int[] { attributeID };
    	TypedArray ta = this.obtainStyledAttributes(attrs);
    	int result = ta.getResourceId(0, 0);
    	ta.recycle();
    	_resourceIdHash.put(attributeID, result);
    	return result;
    }
    
    // Get the current orientation:
    public int getOrientation()
    {
    	return _orientation;
    }

    /** Override the orientation.  Needed for square blackberry devices. */
    public void overrideOrientation(int newOrientation)
    {
        _orientation = newOrientation;
    }
    
    // Get the current split-screen option.  This checks to see whether the device is in portrait or 
    // landscape mode.
    public int getSplitScreenOption()
    {
    	if (_orientation==ORIENTATION_LANDSCAPE)
    		return _settings.getInt(PrefNames.SS_LANDSCAPE, Util.SS_NONE);
    	else
    		return _settings.getInt(PrefNames.SS_PORTRAIT, Util.SS_NONE);
    }
    
    // Methods used for keeping track of whether we are in action mode or not:
    public void recordActionModeStarted(ActionMode am)
    {
    	_actionMode = am;
    }
    public void recordActionModeEnded()
    {
    	_actionMode = null;
    }
    public ActionMode getActionMode()
    {
    	return _actionMode;  // May be null
    }
    public boolean inActionMode()
    {
    	if (_actionMode==null)
    		return false;
    	else
    		return true;
    }
	
	// Hide the keyboard whenever the user taps on a view that is not an EditText.
    // The input is ViewGroup containing all views to alter in its hierarchy.
    // WARNING: This will remove any existing Touch Listeners attached to views.
    public void setupAutoKeyboardHiding(View view)
    {
    	// Set up a touch listener for views that are not EditTexts:
    	if (!(view instanceof EditText))
    	{
    		view.setOnTouchListener(new View.OnTouchListener()
			{
				@Override
				public boolean onTouch(View v, MotionEvent event)
				{
					InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(
						Activity.INPUT_METHOD_SERVICE);
					if (getCurrentFocus()!=null)
						inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
					return false;
				}
			});
    	}
    	
    	// If this view is a ViewGroup, we need to call this function recursively using all elements
    	// in the ViewGroup:
    	if (view instanceof ViewGroup)
    	{
    		for (int i=0; i<((ViewGroup) view).getChildCount(); i++)
    		{
    			View innerView = ((ViewGroup) view).getChildAt(i);
    			setupAutoKeyboardHiding(innerView);
    		}
    	}
    }
    
    /** Initialize a spinner for which the items have to be set in code.  Returns 
    the spinner adapter. */
    public ArrayAdapter<String> initSpinner(ViewGroup rootView, int spinnerID)
    {
        Spinner spinner = (Spinner)rootView.findViewById(spinnerID);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,R.layout.spinner_basic);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        return(spinnerAdapter);
    }
    
    /** Initialize a spinner using a string array */
    public void initSpinner(Spinner spinner, String[] stringArray)
    {
    	ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,R.layout.spinner_basic, 
    		stringArray);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);    	
    	spinner.setAdapter(spinnerAdapter);
    }
    
    /** Initialize a spinner using an ArrayList of strings */
    public void initSpinner(Spinner spinner, ArrayList<String> arrayList)
    {
    	initSpinner(spinner,Util.iteratorToStringArray(arrayList.iterator(), arrayList.size()));
    }
    
    // Given a string, set a spinner to have that string selected.  If the string is 
    // not one of the available selections, then do nothing.
    @SuppressWarnings("unchecked")
    public void setSpinnerSelection(Spinner spinner, String selection)
    {
        ArrayAdapter<String> spinnerAdapter = (ArrayAdapter<String>)spinner.getAdapter();
        for (int i=0; i<spinnerAdapter.getCount(); i++)
        {
            if (selection.equals(spinnerAdapter.getItem(i)))
            {
                spinner.setSelection(i);
            }
        }
    }

    /** Get the text of a spinner's selection.  If the spinner has nothing selected, return the
     * "none" string. */
    public String getSpinnerSelection(Spinner spinner)
    {
        if (spinner.getSelectedItem()==null)
            return getString(R.string.None);
        else
            return spinner.getSelectedItem().toString();
    }

    private boolean _isOrientationLocked = false;
    
    /** Prevent the activity from dying and regenerating after an orientation change.  This keeps
     * the activity at the same orientation even if the user moves the device. */
    @SuppressLint("SourceLockedOrientationActivity")
    public void lockScreenOrientation()
    {
    	if (_isOrientationLocked)
    		return;
    	
    	int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        _isOrientationLocked = true;
    }

    /** Set the screen orientation without crashing. */
    @Override
    public void setRequestedOrientation(int newOrientation)
    {
        try
        {
            super.setRequestedOrientation(newOrientation);
        }
        catch (Exception e)
        {
            // Some devices throw an invalid exception here, saying that only fullscreen
            // activities can set orientation.
            Log.w(Log.className(this),"Can't Lock Orientation","Got an exception when "+
                "trying to lock the orienation.",e);
        }
    }
    /** Unlock the orientation, restoring normal orientation change handling. */
    public void unlockScreenOrientation()
    {
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    	_isOrientationLocked = false;
    }

    /** Ask the user to approve some permissions.
     * @param permissions - The list of permission to approve. Example: Manifest.permission.ACCESS_FINE_LOCATION
     * @param successRunnable - Execute this code after the permission is approved. Cannot be null.
     * @param allowDenial - Determines if the permission can be denied.
     * @param denialMessage - Show this if the permission is denied. If denial is disallowed, then
     *     the permission will be requested again after the user sees the message. If denial is
     *     allowed, the user will be given an option to be asked again or continue without the
     *     permission. Set this to null to just execute denialRunnable on denial.
     * @param denialRunnable - If denial is allowed and the user denies the permission, execute
     *     this. */
    public void requestPermissions(String[] permissions, Runnable successRunnable,
        boolean allowDenial, String denialMessage, Runnable denialRunnable)
    {
        if (successRunnable==null)
        {
            Util.log("WARNING: null runnable passed to requestPermissions.");
            return;
        }

        // If permission have already been approved, just execute the success runnable.
        ArrayList<String> unapprovedPermissions = new ArrayList<>();
        for (String permission : permissions)
        {
            int permissionCheck = ContextCompat.checkSelfPermission(this,permission);
            if (permissionCheck!= PackageManager.PERMISSION_GRANTED)
            {
                unapprovedPermissions.add(permission);
            }
        }
        if (unapprovedPermissions.size()==0)
        {
            successRunnable.run();
            return;
        }

        _permissionsRequested = permissions;
        _permissionAcceptanceRunnable = successRunnable;
        _permissionDenialAllowed = allowDenial;
        _permissionDenialMessage = denialMessage;
        _permissionDenialRunnable= denialRunnable;

        ActivityCompat.requestPermissions(this,
            unapprovedPermissions.toArray(new String[unapprovedPermissions.size()]),
            PERMISSION_REQUEST_CODE);
    }

    /** Convenience function to check to see if a permission is granted. */
    public boolean isPermissionGranted(String permission)
    {
        return (ContextCompat.checkSelfPermission(this,permission)==PackageManager.
            PERMISSION_GRANTED);
    }

    //
    // Methods to allow this to mimic a ListActivity:
    //
    
    protected ListView getListView() 
    {
        return _listView;
    }

    protected void setListAdapter(ListAdapter adapter) 
    {
        getListView().setAdapter(adapter);
    }
    
    protected ListAdapter getListAdapter() 
    {
        ListAdapter adapter = getListView().getAdapter();
        if (adapter instanceof HeaderViewListAdapter) {
            return ((HeaderViewListAdapter)adapter).getWrappedAdapter();
        } else {
            return adapter;
        }
    }
    
    protected void onListItemClick(ListView lv, View v, int position, long id) 
    {
        // Default implementation is blank.
    }

    /** Add a footer view to a listview to ensure the last item can scroll above the + sign at
     * the bottom of the screen. */
    public void addListViewFooter(ListView listView, boolean screenHasBottomAppBar)
    {
        // Fetch the display density:
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;

        // Add a divider view:
        View divider = new View(this);
        divider.setBackgroundColor(Util.colorFromAttr(this,R.attr.list_divider_color));
        divider.setMinimumHeight(1);
        listView.addFooterView(divider);

        // Create the view and add it.  The size depends on whether a bottom app bar is present.
        View footer = new View(this);
        if (screenHasBottomAppBar)
            footer.setMinimumHeight(Math.round(30*density));
        else
            footer.setMinimumHeight(Math.round(74*density));
        listView.addFooterView(footer);
        listView.setFooterDividersEnabled(false);
    }

    @Override
    public void onPause()
    {
        Log.i(Log.className(this), "onPause() called. Is finishing? "+isFinishing());
        if (_bannerAd!=null && _purchaseManager.stat()==PurchaseManager.SHOW_ADS)
            _bannerAd.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy()
    {
        _purchaseManager.unlinkFromBillingService();
        if (_bannerAd !=null)
        {
            _bannerAd.destroy();
            _bannerAd = null;
        }
        if (_purchaseReceiver!=null)
            unregisterReceiver(_purchaseReceiver);
        super.onDestroy();
    }
}
