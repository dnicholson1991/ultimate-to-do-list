package com.customsolutions.android.utl;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.droidparts.widget.ClearableEditText;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/** Fragment for adding or editing a location.  Pass in a Bundle with key "mode" and value of ADD or
 * EDIT.  When editing, pass in a key of "id", containing the database ID.
 * @author Nicholson
 *
 */
public class EditLocationFragment extends GenericEditorFragment
{
	// Codes to track responses to activities:
    public static final int GET_ACCOUNTS = 1;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditLocationFragment";
    
    // The maximum locations allowed by Toodledo:
    public static final int MAX_LOCATIONS = 100;
    
    // Keeps track of the IDs of selected accounts:
    HashSet<Long> _selectedAccountIDs;
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private ClearableEditText _description;
    private TextView _latitudeText;
    private TextView _longitudeText;
    private TextView _address;
    private TextView _accounts;
    
    // The number accounts affects the display:
    private int _numAccounts;
    
    // Quick access to database tables:
    private AccountsDbAdapter _accountsDB;
    private LocationsDbAdapter _locDB;
    
    // The Location we're editing, if applicable:
    private UTLLocation _loc;
    
    // The coordinates that were selected:
    private double _lat;
    private double _lon;
    
    // An EditText for dialogs:
    private EditText _dialogEditText;
    private EditText _editText;
    
    // The last address entered:
    private String _lastAddress;
    
    // A progress dialog for general use:
    private ProgressDialog _progressDialog;
    
    // Objects for location tracking:
    private LocationManager _locationManager;
    private LocationListener _locationListener;
	private Location _bestLocation;
	private long _locationFixStartTime;
	private LocationWait _locationWait;
    
	// Retry count for using Google's location services:
	private int _numGeocodeAttempts;
	private int _numReverseGeocodeAttempts;
	
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_location2, container, false);
    }
    
    /** Called when Activity is started: */
    @SuppressLint("NewApi")
	@Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening Location Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _accountsDB = new AccountsDbAdapter();
        _locDB = new LocationsDbAdapter();
        _selectedAccountIDs = new HashSet<Long>();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_location_title);
        _description = (ClearableEditText)_rootView.findViewById(R.id.edit_location_description);
        _latitudeText = (TextView)_rootView.findViewById(R.id.edit_location_latitude_value);
        _longitudeText = (TextView)_rootView.findViewById(R.id.edit_location_longitude_value);
        _address = (TextView)_rootView.findViewById(R.id.edit_location_address_value);
        _accounts = (TextView)_rootView.findViewById(R.id.edit_location_account_value);
        
        if (_op==EDIT)
        	setTitle(R.string.Edit_Location);
        else
        	setTitle(R.string.Add_Location);

        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
    	_numAccounts = c1.getCount();
    	c1.close();
    	
    	// Get the location being edited, if applicable:
    	if (_op==EDIT)
    	{
    		_loc = _locDB.getLocation(_id);
    		if (_loc==null)
    		{
    			Util.log("Missing location in EditLocationFragment.java");
    			refreshAndEnd();
    			return;
    		}
    		_lat = _loc.lat;
    		_lon = _loc.lon;
    	}
    	else
    	{
    		_lat = 0;
    		_lon = 0;
    	}
    	
    	refreshVisibility();
    	
    	// Initialize the information in the views:
    	if (_op==ADD)
    	{
    		// Account:
    		if (_numAccounts>1)
    		{
    			// Use the default account for task creation:
    			if (_settings.contains(PrefNames.DEFAULT_ACCOUNT))
    			{
    				UTLAccount acc = _accountsDB.getAccount(_settings.getLong(
                    	PrefNames.DEFAULT_ACCOUNT, 0));
    				if (acc != null)
                    {
                        _accounts.setText(acc.name);
                        _selectedAccountIDs.add(acc._id);
                    }             
                    else
                    {
                    	// Get the first account:
                        Cursor c = _accountsDB.getAllAccounts();
                        if (c.moveToFirst())
                        {
                            acc = _accountsDB.getUTLAccount(c);
                            _accounts.setText(acc.name);
                            _selectedAccountIDs.add(acc._id);
                        }
                        c.close();
                    }
    			}
    			else
    			{
    				// No default account specified.  Get the first account.
                    Cursor c = _accountsDB.getAllAccounts();
                    if (c.moveToFirst())
                    {
                        UTLAccount acc = _accountsDB.getUTLAccount(c);
                        _accounts.setText(acc.name);
                        _selectedAccountIDs.add(acc._id);
                    }
                    c.close();
    			}
    		}
    		else
    		{
    			// We have one account only, so it is selected:
                Cursor c = _accountsDB.getAllAccounts();
                if (c.moveToFirst())
                {
                	UTLAccount acc = _accountsDB.getUTLAccount(c);
                	_selectedAccountIDs.add(acc._id);
                }
                c.close();
    		}
    	}
    	else
    	{
    		// The values come from the location being edited:
    		_title.setText(_loc.title);
    		_latitudeText.setText(Double.valueOf(roundDouble(_loc.lat)).toString());
    		_longitudeText.setText(Double.valueOf(roundDouble(_loc.lon)).toString());
    		_description.setText(_loc.description);
    		_selectedAccountIDs.add(_loc.account_id);
    		
    		// Start looking up the location's address:
    		if (Build.VERSION.SDK_INT >= 11)
    			new GetAddress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
    		else
    			new GetAddress().execute(new Void[] { });
    	}
    	
    	// Handler for the Accounts button:
    	if (_op==ADD && _numAccounts>1)
    	{
	    	_rootView.findViewById(R.id.edit_location_account_container).setOnClickListener(new 
	    		View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					flashField(R.id.edit_location_account_container);
					
                    // We will start up the list item picker.  Begin by getting the current
                    // selected accounts:
                    Intent i = new Intent(_a, ItemPicker.class);
                    Iterator<Long> it = _selectedAccountIDs.iterator();
                    i.putExtra("selected_item_ids", Util.iteratorToLongArray(it, _selectedAccountIDs.
                    	size()));
                    
                    // Get an array of all account IDs and names, to put into the chooser:
                    Cursor c = (new AccountsDbAdapter()).getAllAccounts();
                    i.putExtra("item_ids", Util.cursorToLongArray(c, "_id"));
                    i.putExtra("item_names", Util.cursorToStringArray(c, "name"));
                    
                    // The title for the item selector activity:
                    i.putExtra("title",Util.getString(R.string.Select_Accounts));
                    startActivityForResult(i,GET_ACCOUNTS);
				}
			});
    	}
    	
    	// Handler for the button to use current location:
    	_rootView.findViewById(R.id.edit_location_use_current).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				useCurrentLocation();
			}
		});
    	
    	// Handler for the button to enter an address:
    	_rootView.findViewById(R.id.edit_location_enter_address).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
					OnClickListener()
				{		
					@SuppressLint({ "InlinedApi", "NewApi" })
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						dialog.dismiss();
						switch (which)
						{
						case DialogInterface.BUTTON_POSITIVE:
							// OK tapped:
							String address = _dialogEditText.getText().toString().trim();
							if (address.length()>0)
							{
								_progressDialog = ProgressDialog.show(_a, null,
									Util.getString(R.string.Looking_Up_Address),false);
								_numGeocodeAttempts = 0;
								if (Build.VERSION.SDK_INT >= 11)
								{
									new getLatLon().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
										new String[] { address });
								}
								else
									new getLatLon().execute(new String[] { address });
							}
						}
					}
				};
				
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				_dialogEditText = new EditText(_a);
				_dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.
					TYPE_TEXT_FLAG_MULTI_LINE);
				if (_lastAddress != null)
					_dialogEditText.setText(_lastAddress);
				builder.setView(_dialogEditText);
				builder.setTitle(Util.getString(R.string.Enter_An_Address_));
				builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();
			}
		});
    	
    	// Driving Navigation button:
    	_rootView.findViewById(R.id.edit_location_navigation_container).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (_lat!=0 && _lon!=0)
				{
					flashField(R.id.edit_location_navigation_container);
					Intent i = new Intent(Intent.ACTION_VIEW,
					Uri.parse("google.navigation:q="+Double.valueOf(_lat).toString()+","+
						Double.valueOf(_lon).toString()));
					try
					{
						_a.startActivity(i);
					}
					catch (ActivityNotFoundException e)
					{
						Util.popup(_a, R.string.Navigation_Not_Installed);
					}
				}
			}
		});
    	
    	// Open in Maps:
    	_rootView.findViewById(R.id.edit_location_map_container).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (_lat!=0 && _lon!=0)
				{
					flashField(R.id.edit_location_map_container);
					String coords = Double.valueOf(_lat).toString()+","+Double.valueOf(_lon).toString();
					Intent i = new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+coords+"?q="+coords));
					try
					{
						_a.startActivity(i);
					}
					catch (ActivityNotFoundException e)
					{
						Util.popup(_a, R.string.Maps_Not_Installed);
					}
				}
			}
		});
    	
    	// Latitude update:
    	_rootView.findViewById(R.id.edit_location_latitude_container).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Define a callback to execute when the user press the OK or Cancel
				// buttons on the dialog:
				DialogInterface.OnClickListener dialogClickListener = new 
					DialogInterface.OnClickListener()
				{					
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (which==DialogInterface.BUTTON_POSITIVE)
						{
							String newText = _editText.getText().toString().trim();
							if (newText.length()>0)
							{
								try
								{
									_lat = Double.parseDouble(newText);
									_latitudeText.setText(Double.valueOf(roundDouble(_lat)).toString());
									
									// Start looking up the location's address:
									_address.setText(R.string.Looking_Up);
						    		if (Build.VERSION.SDK_INT >= 11)
						    			new GetAddress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
						    		else
						    			new GetAddress().execute(new Void[] { });
								}
								catch (NumberFormatException e)
								{
									
								}
							}
						}
					}
				};
				
				flashField(R.id.edit_location_latitude_container);
					            
				// Define and display the actual dialog:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				_editText = new EditText(_a);
				_editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
					InputType.TYPE_NUMBER_FLAG_SIGNED);
				_editText.setText(Double.valueOf(_lat).toString());
				builder.setView(_editText);
				builder.setTitle(R.string.Latitude_);
	            builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();						
			}
		});
    	
    	// Longitude update:
    	_rootView.findViewById(R.id.edit_location_longitude_container).setOnClickListener(new View.
    		OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Define a callback to execute when the user press the OK or Cancel
				// buttons on the dialog:
				DialogInterface.OnClickListener dialogClickListener = new 
					DialogInterface.OnClickListener()
				{					
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (which==DialogInterface.BUTTON_POSITIVE)
						{
							String newText = _editText.getText().toString().trim();
							if (newText.length()>0)
							{
								try
								{
									_lon = Double.parseDouble(newText);
									_longitudeText.setText(Double.valueOf(roundDouble(_lon)).toString());
									
									// Start looking up the location's address:
									_address.setText(R.string.Looking_Up);
						    		if (Build.VERSION.SDK_INT >= 11)
						    			new GetAddress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
						    		else
						    			new GetAddress().execute(new Void[] { });
								}
								catch (NumberFormatException e)
								{
									
								}
							}
						}
					}
				};
				
				flashField(R.id.edit_location_longitude_container);
					            
				// Define and display the actual dialog:
				AlertDialog.Builder builder = new AlertDialog.Builder(_a);
				_editText = new EditText(_a);
				_editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
					InputType.TYPE_NUMBER_FLAG_SIGNED);
				_editText.setText(Double.valueOf(_lon).toString());
				builder.setView(_editText);
				builder.setTitle(R.string.Longitude_);
	            builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
	            builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
	            builder.show();						
			}
		});
    }
    
    /** Refresh visibility of views: */
    private void refreshVisibility()
    {
    	// Account Selector
    	if (_numAccounts==1 || _op==EDIT)
    		_rootView.findViewById(R.id.edit_location_account_section).setVisibility(View.GONE);
    	else
    		_rootView.findViewById(R.id.edit_location_account_section).setVisibility(View.VISIBLE);
    	
    	// Coordinates and address:
    	if (_op==ADD && _lat==0 && _lon==0)
    		_rootView.findViewById(R.id.edit_location_info_section).setVisibility(View.GONE);
    	else
    		_rootView.findViewById(R.id.edit_location_info_section).setVisibility(View.VISIBLE);
    	
    	// Navigation and maps section:
    	if (_lat==0 && _lon==0)
    	{
    		_rootView.findViewById(R.id.edit_location_map_section).setVisibility(View.GONE);
    	}
    	else
    	{
    		_rootView.findViewById(R.id.edit_location_map_section).setVisibility(View.VISIBLE);
    		
    		// The navigation button is available only when editing:
    		if (_op==ADD)
    			_rootView.findViewById(R.id.edit_location_navigation_container).setVisibility(View.GONE);
    		else
    			_rootView.findViewById(R.id.edit_location_navigation_container).setVisibility(View.VISIBLE);
    	}
    }
    
    // Round and format a latitude or longitude:
    private double roundDouble(double d) 
    {
    	DecimalFormat twoDForm = new DecimalFormat("#.######");
    	Number result = twoDForm.parse(twoDForm.format(d),new ParsePosition(0));
    	if (result!=null)
    		return result.doubleValue();
    	else
    		return Double.valueOf(d).intValue();
    }

    // Use the current coordinates for the location:
    private int MIN_LOCATION_WAIT = 10000;
    private int MAX_LOCATION_WAIT = 30000;
    private int DESIRED_ACCURACY = 30;  // Meters
    @SuppressLint("NewApi")
	private void useCurrentLocation()
    {
    	// This object responds to location updates:
    	_locationListener = new LocationListener() 
    	{
	    	// Called when a new location is found by the network location provider:
    	    public void onLocationChanged(Location location) 
    	    {
    	    	if (Util.isBetterLocation(location, _bestLocation))
    	    	{
    	    		_bestLocation = new Location(location);
    	    		
    	    		long timeDiff = System.currentTimeMillis()-_locationFixStartTime;
    	    		if (timeDiff>=MIN_LOCATION_WAIT && _bestLocation.getAccuracy()<
    	    			DESIRED_ACCURACY)
    	    		{
    	    			// We've waited at least 10 seconds and we have an accuracy of
    	    			// less than DESIRED_ACCURACY meters, so we're done.
    	    			_locationManager.removeUpdates(_locationListener);
    	    			
    	    			// Stop the AsyncTask that implements the timeout:
    	    			_locationWait.cancel(true);
    	    			
    	    			Util.log("Got good location after 10 secs.");
    	    			
    	    			// Update the displayed latitude and longitude:
    	    			handleCompletedLocationFix();
    	    		}
    	    	}
    	    }

    	    public void onStatusChanged(String provider, int status, Bundle extras) {}

    	    public void onProviderEnabled(String provider) {}

    	    public void onProviderDisabled(String provider) {}
    	 };
    	 
    	 // Start receiving location updates:
    	 _locationManager = (LocationManager) _a.getSystemService(Context.LOCATION_SERVICE);
    	 int numProviders = 2;
    	 try
    	 {
    		 _locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, 
    			 _locationListener);
    	 }
    	 catch (IllegalArgumentException e)
    	 {
    		 // No network location services are available.
    		 numProviders--;
    	 }
    	 try
    	 {
	    	 _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, 
	    		 _locationListener);
    	 }
    	 catch (IllegalArgumentException e)
    	 {
    		 // No GPS is available.
    		 numProviders--;
    	 }
    	 if (numProviders==0)
    	 {
    		 Util.longerPopup(_a, "", _a.getString(R.string.Unable_to_get_location));
    		 return;
    	 }
    	 _locationFixStartTime = System.currentTimeMillis();
    	 
    	 // Display a progress dialog asking the user to wait:
		 _progressDialog = ProgressDialog.show(_a, null,Util.getString(R.string.Getting_Location),false);

		 // Start a background task that stop the location fixes after a delay:
		 _locationWait = new LocationWait();
		 if (Build.VERSION.SDK_INT >= 11)
			 _locationWait.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
		 else
			 _locationWait.execute(new Void[] { });
    }
    
    // An AsyncTask instance whose job is to stop getting location fixes after several
    // seconds:
    private class LocationWait extends AsyncTask<Void,Void,Void>
    {
    	protected Void doInBackground(Void... voids)
    	{
    		try
    		{
    			Thread.sleep(MAX_LOCATION_WAIT);
    		}
    		catch (InterruptedException e)
    		{
    		}
    		return null;
    	}
    	
    	protected void onPostExecute(Void v)
    	{
    		Util.log("Timed out waiting for good location.");
    		handleCompletedLocationFix();
    	}
    }

    // Handle the completion of a location fix:
    @SuppressLint({ "InlinedApi", "NewApi" })
	private void handleCompletedLocationFix()
    {
    	// Dismiss the progress dialog:
    	if (_progressDialog!=null && _progressDialog.isShowing())
    	{
    		try
    		{
    			_progressDialog.dismiss();
    		}
    		catch (IllegalArgumentException e)
    		{
    			// Just ignore this.  It's only a progress dialog.
    		}
    	}
		
		_locationManager.removeUpdates(_locationListener);
		if (_bestLocation==null)
		{
			Util.longerPopup(_a, "", _a.getString(R.string.Unable_to_get_location));
			return;
		}
		
		// Update the displayed latitude and longitude:
		_lat = _bestLocation.getLatitude();
		_lon = _bestLocation.getLongitude();
		_latitudeText.setText(Double.valueOf(roundDouble(_lat)).toString());
		_longitudeText.setText(Double.valueOf(roundDouble(_lon)).toString());
		refreshVisibility();
		
		Util.popup(_a,_a.getString(R.string.Coords_Located));
		
		// Start another background task to get the approximate address:
		_address.setText(R.string.Looking_Up);
		_numReverseGeocodeAttempts = 0;
		if (Build.VERSION.SDK_INT >= 11)
			new GetAddress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
		else
			new GetAddress().execute(new Void[] { });
    }
    
    // An AsyncTask instance which looks up an address given the current latitude and
    // longitude.
    private class GetAddress extends AsyncTask<Void,Void,String>
    {
    	@SuppressLint("NewApi")
		protected String doInBackground(Void...voids)
    	{
    		if (Build.VERSION.SDK_INT >= 9)
    		{
    			// Check to see if the device supports geocoding:
    			if (!Geocoder.isPresent())
    			{
    				return _a.getString(R.string.Unavailable_Not_Supported);
    			}
    		}
    		
    		Geocoder geo = new Geocoder(_a);
    		try
    		{
    			List<Address> addressList = geo.getFromLocation(_lat,_lon, 1);
    			if (addressList==null)
    			{
    				Util.log("Reverse Geocoder returned null.");
    				return null;
    			}
    			else if (addressList.size()==0)
    			{
    				Util.log("Reverse Geocoder returned empty array.");
    				return null;
    			}
    			else
    			{
    				String addressStr = "";
    				int numLines = 1+addressList.get(0).getMaxAddressLineIndex();
    				for (int i=0; i<numLines; i++)
    				{
    					if (i>0)
    						addressStr += ", ";
    					addressStr += addressList.get(0).getAddressLine(i);
    				}
    				return addressStr;
    			}
    		}
    		catch (IllegalArgumentException e)
    		{
    			Util.log("Reverse Geocode IllegalArgumentException: "+e.getMessage());
    			return _a.getString(R.string.Unavailable_Not_Supported);
    		}
    		catch (IOException e)
    		{
    			Util.log("Reverse Geocode IOexception: "+e.getMessage());
    			return _a.getString(R.string.No_Network_Connection);
    		}
    	}
    	
    	@SuppressLint({ "InlinedApi", "NewApi" })
		protected void onPostExecute(String address)
    	{
    		try
			{
				if (address==null || address.length()==0)
				{
					if (_numReverseGeocodeAttempts<3)
					{
						_numReverseGeocodeAttempts++;
						new GetAddress().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new
							Void[] { });
					}
					else
					{
						_address.setText(R.string.Cannot_find_address);
					}
					return;
				}

				_address.setText(address);
			}
    		catch (Exception e)
			{
				// Exceptions can occur if the screen is rotated or the Activity is dismissed
				// while the AsyncTask is running. Ignore them.
			}
    	}
    }
    
    // An AsyncTask instance which gets a latitude and longitude given a street address:
    private class getLatLon extends AsyncTask<String,Void,Address>
    {
    	protected Address doInBackground(String... addresses)
    	{
    		Geocoder geo = new Geocoder(_a);
    		try
    		{
    			List<Address> addressList = geo.getFromLocationName(addresses[0], 1);
    			if (addressList==null)
    			{
    				Util.log("Geocoder returned null.");
    				return null;
    			}
    			else if (addressList.size()==0)
    			{
    				Util.log("Geocoder returned empty array.");
    				return null;
    			}
    			else
    				return addressList.get(0);
    		}
    		catch (IllegalArgumentException e)
    		{
    			Util.log("Geocoder IllegalArgumentException exception: "+e.getMessage());
    			return null;
    		}
    		catch (IOException e)
    		{
    			Util.log("Geocoder IOException exception: "+e.getMessage());
    			return null;
    		}
    	}
    	
    	@SuppressLint({ "InlinedApi", "NewApi" })
		protected void onPostExecute(Address address)
    	{
    		try
			{
				_lastAddress = _dialogEditText.getText().toString();
				if (address!=null)
				{
					// Dismiss progress dialog:
					if (_progressDialog!=null && _progressDialog.isShowing())
						_progressDialog.dismiss();

					_lat = address.getLatitude();
					_lon = address.getLongitude();
					_latitudeText.setText(Double.valueOf(roundDouble(_lat)).toString());
					_longitudeText.setText(Double.valueOf(roundDouble(_lon)).toString());
					_address.setText(_lastAddress);
					refreshVisibility();

					Util.popup(_a, R.string.Coords_Located);
				}
				else
				{
					if (_numGeocodeAttempts<3)
					{
						_numGeocodeAttempts++;
						if (Build.VERSION.SDK_INT >= 11)
							new getLatLon().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_lastAddress);
						else
							new getLatLon().execute(_lastAddress);
						return;
					}

					// Dismiss progress dialog:
					_progressDialog.dismiss();

					// Display a dialog with the error message:
					AlertDialog.Builder builder = new AlertDialog.Builder(_a);
					builder.setTitle(R.string.Unable_to_get_coords);
					builder.setMessage(R.string.Address_or_Internet_Issue);
					builder.setNeutralButton(R.string.OK, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialog, int which)
						{
							// Nothing to do.
						}
					});
					builder.show();
				}
			}
			catch (Exception e)
			{
				// Exceptions can occur if the screen is rotated or the Activity is dismissed
				// while the AsyncTask is running. Ignore them.
			}
    	}
    }

    /** Handlers for Activity results: */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        
        // Get extras in the response, if any:
        Bundle extras = new Bundle();
        if (intent != null)
        {
            extras = intent.getExtras();
        }
        
        if (requestCode==GET_ACCOUNTS && resultCode==Activity.RESULT_OK && extras.containsKey(
        	"selected_item_ids"))
        {
        	// Get the account IDs from the response and update the button text:
            long[] accountIDs = extras.getLongArray("selected_item_ids");
            if (accountIDs.length==0)
            {
                // This should not happen.
                Util.log("ERROR: item picker returned an empty array");
            }
            else
            {
                UTLAccount a = _accountsDB.getAccount(accountIDs[0]);
                String buttonText = "";
                if (a==null)
                {
                    Util.log("ERROR: Bad account with ID of "+accountIDs[0]+
                        " passed back.");
                    return;
                }
                else
                {
                    buttonText = a.name;
                }
                for (int i=1; i<accountIDs.length; i++)
                {
                    a = _accountsDB.getAccount(accountIDs[i]);
                    if (a!=null)
                    {
                        buttonText += ","+a.name;
                    }
                }
                _accounts.setText(buttonText);
                
                // Store the selected account IDs:
                _selectedAccountIDs.clear();
                for (int i=0; i<accountIDs.length; i++)
                {
                    _selectedAccountIDs.add(accountIDs[i]);
                }
            }
        }
    }
    
    /** Check for valid values, when save and exit if possible */
    @Override
    protected void handleSave()
    {
    	// Make sure there is a title:
    	if (_title.getText().toString().length()==0)
    	{
    		Util.popup(_a,R.string.Please_enter_a_name);
    		return;
    	}
    	String title = _title.getText().toString();
    	
    	// Make sure the title is not too long:
    	if (title.length()>Util.MAX_LOCATION_TITLE_LENGTH)
    	{
    		Util.popup(_a,R.string.Name_too_long);
    		return;
    	}
    	
    	// Make sure the description is not too long:
    	if (_description.getText().toString().length()>Util.MAX_LOCATION_DESCRIPTION_LENGTH)
    	{
    		Util.popup(_a,R.string.Description_too_long);
    		return;
    	}
    	
    	// Make sure at least one account is selected:
    	if (_selectedAccountIDs.size()==0)
    	{
    		Util.popup(_a, R.string.Please_select_an_account);
    		return;
    	}
    	
    	// Make sure the location title does not match any location in any of the selected
    	// accounts:
    	String where = "lower(title)='"+Util.makeSafeForDatabase(_title.getText().toString().
    		toLowerCase())+"' and account_id in (";
    	Iterator<Long> it = _selectedAccountIDs.iterator();
    	int j = 0;
    	while (it.hasNext())
    	{
    		long accountID = it.next();    		
    		if (j>0)
    			where+=","+accountID;
    		else
    			where += accountID;
   			j++;
    	}
    	where += ")";
    	Cursor c = _locDB.queryLocations(where, null);
    	if (c.moveToFirst())
    	{
    		if (_op==ADD || (_op==EDIT && Util.cLong(c, "_id")!=_id))
    		{
        		Util.popup(_a,R.string.Name_already_exists);
        		c.close();
        		return;
    		}
    	}
    	c.close();
    	
    	// Some coordinates must be given:
    	if (_lat==0 && _lon==0)
    	{
    		Util.popup(_a, R.string.No_coordinates_given);
    		return;
    	}
    	
    	if (_op==ADD)
    	{
    		// Verify that the user doesn't create more locations than Toodledo allows:
    		it = _selectedAccountIDs.iterator();
	    	while (it.hasNext())
	    	{
	    		UTLAccount a = _accountsDB.getAccount(it.next());
	    		c = _locDB.queryLocations("account_id="+a._id, null);
	    		if (a.sync_service==UTLAccount.SYNC_TOODLEDO && c.getCount()>=
	    			MAX_LOCATIONS)
	    		{
	    			Util.popup(_a, R.string.Location_Limit_Reached);
	    			c.close();
	    			return;
	    		}
	        	c.close();
	    	}
	    	
	    	// We need to create one location per account:
	    	it = _selectedAccountIDs.iterator();
	    	while (it.hasNext())
	    	{
	    		// Create the location object to put the data into:
	    		UTLLocation newLoc = new UTLLocation();
	    		newLoc.td_id = -1;
	    		newLoc.account_id = it.next();
	    		newLoc.mod_date = System.currentTimeMillis();
	    		newLoc.title = title;
	    		newLoc.lat = _lat;
	    		newLoc.lon = _lon;
	    		newLoc.description = _description.getText().toString();
	    		
	    		// Add the new location to the database:
	    		long locID = _locDB.addLocation(newLoc);
	    		if (locID==-1)
	    		{
	    			Util.popup(_a, R.string.DbInsertFailed);
	    			return;
	    		}
	    		
	    		// Instantly upload the location (if enabled):
	    		Intent i = new Intent(_a, Synchronizer.class);
	        	i.putExtra("command", "sync_item");
	        	i.putExtra("item_type",Synchronizer.LOCATION);
	        	i.putExtra("item_id", newLoc._id);
	        	i.putExtra("account_id", newLoc.account_id);
	        	i.putExtra("operation",Synchronizer.ADD);
				Synchronizer.enqueueWork(_a,i);
	    	}
    	}
    	else
    	{
    		String oldTitle = _loc.title;
    		
    		// Update the passed-in location object:
    		_loc.mod_date = System.currentTimeMillis();
    		_loc.title = title;
    		_loc.lat = _lat;
    		_loc.lon = _lon;
    		_loc.description = _description.getText().toString();
    		
    		// Modify the entry in the database:
    		if (!_locDB.modifyLocation(_loc))
			{
				Util.popup(_a,R.string.DbModifyFailed);
    			return;
			}
    		
    		// Instantly upload the location (if enabled):
    		Intent i = new Intent(_a, Synchronizer.class);
        	i.putExtra("command", "sync_item");
        	i.putExtra("item_type",Synchronizer.LOCATION);
        	i.putExtra("item_id", _loc._id);
        	i.putExtra("account_id", _loc.account_id);
        	i.putExtra("operation",Synchronizer.MODIFY);
			Synchronizer.enqueueWork(_a,i);
        	
        	// If this is an edit operation and the location is tied to a Google account then we 
            // need to re-upload all tasks that use it if the name changes.  This is done by marking 
        	// the tasks as modified and triggering a sync:
        	UTLAccount a = _accountsDB.getAccount(_loc.account_id);
        	if (_op==EDIT && a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE && !_loc.title.
        		equals(oldTitle))
        	{
        		// Mark tasks that use the location as modified:
            	SQLiteDatabase db = Util.db();
            	db.execSQL("update tasks set mod_date="+System.currentTimeMillis()+
            		" where location_id="+_loc._id);
            	
            	// Trigger a sync:
            	i = new Intent(_a, Synchronizer.class);
        		i.putExtra("command", "full_sync");
				Synchronizer.enqueueWork(_a,i);
        	}
    	}

		// Update the geofencing:
		Util.setupGeofencing(_a);

    	refreshAndEnd();
    }
}
