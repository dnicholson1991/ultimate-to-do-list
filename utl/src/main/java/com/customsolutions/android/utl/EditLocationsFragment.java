package com.customsolutions.android.utl;

import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** This fragment displays a list of contexts for editing. */
public class EditLocationsFragment extends GenericListFragment
{
	// The tag to use when placing this fragment:
    public static final String FRAG_TAG = "EditLocationsFragment";
    
    private LocationsDbAdapter _locDB;
    private AccountsDbAdapter _accountsDB;
	private int _numAccounts;
	private Cursor _c;
	
	/** The currently selected item ID */
	private long _selectedItemID = -1;
    
	/** Temporary storage of an item ID: */
	private long _tempItemID = -1;
	
	// Objects for location tracking:
    private LocationManager _locationManager;
    private LocationListener _locationListener;
	private Location _bestLocation;
	private long _locationFixStartTime;
	private LocationWait _locationWait;
	private double _lat;
	private double _lon;

	// These ArrayList instances keep track of locations we need to get a distance on:
	ArrayList<UTLLocation> _locationList;
	ArrayList<TextView> _locationViewList;
	
	// Cached distances.  Key is the location ID.  Value is distance.
    private HashMap<Long,String> _cachedDistances;
    
	@Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	Util.log("Starting EditLocationsFragment");
    	
    	_locDB = new LocationsDbAdapter();
    	_accountsDB = new AccountsDbAdapter();
    	
    	// Set the title and icon for this list:
    	_a.getSupportActionBar().setTitle(R.string.Edit_Locations);
    	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	
    	// Set the message to display if there are no items:
    	TextView none = (TextView)_rootView.findViewById(android.R.id.empty);
    	none.setText(R.string.No_Locations_Defined);
    	
    	// Get the saved item ID, if it was passed in:
    	if (savedInstanceState!=null && savedInstanceState.containsKey("selected_item_id"))
    		_selectedItemID = savedInstanceState.getLong("selected_item_id");
    	else
    		_a.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));

		// Handle the "add" button which is a floating action button:
		_a.findViewById(R.id.generic_list_fab).setOnClickListener((View v) -> {
			// The editor is either opened as a new activity or as a fragment in
			// the details pane:
			if (_a.useNewActivityForDetails())
			{
				// No split-screen in use.  Just start a new activity:
				Intent i = new Intent(_a,EditLocationActivity.class);
				i.putExtra("mode", EditLocationFragment.ADD);
				_a.startActivity(i);
			}
			else
			{
				// Start the fragment:
				EditLocationFragment frag = new EditLocationFragment();
				Bundle args = new Bundle();
				args.putInt("mode", EditLocationFragment.ADD);
				frag.setArguments(args);
				_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag,
					EditLocationFragment.FRAG_TAG);
			}
		});
    }
	
	@Override
	public void refreshData()
	{
		// Initially, we have no location:
    	_lat = 0;
    	_lon = 0;
    	_cachedDistances = new HashMap<Long,String>();
    	_locationList = new ArrayList<UTLLocation>();
    	_locationViewList = new ArrayList<TextView>();
    	
    	// Query the database to get the location data:
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	_c = _locDB.queryLocations(null, "account_id,lower(title)");
    	_c.moveToPosition(-1);
    	
    	// We need to know how many accounts we have, since this affects the display:
    	Cursor c = _accountsDB.getAllAccounts();
    	_numAccounts = c.getCount();
    	c.close();
    	
    	// This array lists the columns we're interested in:
    	String from[] = new String[] {"_id"};
    	
    	// The IDs of views that are affected by the columns:
    	int to[] = new int[] {R.id.locations_list_row_container};
    	
    	// Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(_a, R.layout.locations_list_row,
        	_c, from, to);
        
        // This function binds data in the Cursor to the Views we're displaying:
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
        {
        	public boolean setViewValue(View view, Cursor c, int columnIndex)
            {
        		// Get references to the views we need to work with:
        		ViewGroup row = (ViewGroup)view;
        		TextView name = (TextView)view.findViewById(R.id.locations_list_row_name);
        		ImageButton deleteButton = (ImageButton)view.findViewById(R.id.locations_list_row_delete);
        		TextView distance = (TextView)view.findViewById(R.id.locations_list_row_distance);
        		
        		// Get the UTLLocation object:
        		UTLLocation loc = _locDB.cursorToUTLLocation(c);
        		
        		// Display the name:
        		String itemName = loc.title;
        		if (_numAccounts>1)
        		{
        			UTLAccount a = _accountsDB.getAccount(Util.cLong(c, "account_id"));
        			if (a!=null)
        				itemName += " ("+a.name+")";
        		}
        		name.setText(itemName);
        		
        		// Place tags, so that the handler functions know what ID has been tapped on.
        		deleteButton.setTag(Long.valueOf(loc._id));
        		row.setTag(Long.valueOf(loc._id));
        		
        		// Highlight the row if it's selected:
        		if (!_a.useNewActivityForDetails())
        		{
	        		if (_selectedItemID==loc._id)
	        			highlightRow(row,_selectedItemID);
	        		else
	        			unhighlightRow(row);
        		}
        		
        		// Update distances - using a background task when needed.
        		if (_cachedDistances.containsKey(loc._id))
        		{
            		// Use the distance previously calculated:
        			distance.setText(_cachedDistances.get(loc._id));
        		}
        		else if (_lat!=0 && _lon!=0)
        		{
        			// We have a location, so calculate the distance:
        			String distanceStr = getDistance(loc);
        			distance.setText(distanceStr);
        			_cachedDistances.put(loc._id, distanceStr);
        		}
        		else
        		{
        			// We don't have our location yet. Keep a list of TextViews to fill
        			// in later:
        			_locationList.add(loc);
        			_locationViewList.add(distance);
        		}
        		
        		// Handler for the delete button:
        		deleteButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						if (!_a.inResizeMode())
						{
							// Get the item's ID:
							_tempItemID = (Long)v.getTag();
							
							// Button handler for the confirmation dialog:
							DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
				        	  	  OnClickListener()
							{				
							    @Override
							    public void onClick(DialogInterface dialog, int which)
							    {
								    if (which == DialogInterface.BUTTON_POSITIVE)
								    {
								    	// Clear the location field from any tasks linked to the location:
				                    	int numTasksUpdated = 0;
				                    	TasksDbAdapter tasksDB = new TasksDbAdapter();
				                    	Cursor c = tasksDB.queryTasks("location_id="+_tempItemID, 
				                    		null);
				                        c.moveToPosition(-1);
				                        while (c.moveToNext())
				                        {
				                            UTLTask task = tasksDB.getUTLTask(c);
				                            task.location_id = 0;
				                            task.mod_date = System.currentTimeMillis();
				                            boolean isSuccessful = tasksDB.modifyTask(task);
				                            if (!isSuccessful)
				                            {
				                            	Util.popup(_a,R.string.DbModifyFailed);
				                                Util.log("Could not clear location for task ID "+task._id);
				                                return;
				                            }
				                            numTasksUpdated++;
				                        }
				                        c.close();
				                        
				                        // We need the location's toodledo ID:
				                        UTLLocation loc = _locDB.getLocation(_tempItemID);
				                        if (loc!=null)
				                        {
				                            if (loc.td_id>-1)
				                            {
				                            	// The item has a Toodledo ID, so the deletion needs
				                            	// to be uploaded.
				                            	// Update the pending deletes table:
				                            	PendingDeletesDbAdapter deletes = new 
				                            		PendingDeletesDbAdapter();
				                            	if (-1==deletes.addPendingDelete("location", loc.td_id,
				                            		loc.account_id))
				                            	{
				                            		Util.popup(_a, R.string.DbInsertFailed);
				                            		Util.log("Cannot add pending delete in LocationsList.java.");
				                            		return;
				                            	}
				                            	
				                        		// If no tasks were updated, we can upload the deletion to
				                        		// Toodledo:
				                        		if (numTasksUpdated==0)
				                        		{
				                            		Intent i = new Intent(_a, Synchronizer.class);
				                                	i.putExtra("command", "sync_item");
				                                	i.putExtra("item_type",Synchronizer.LOCATION);
				                                	i.putExtra("item_id", loc.td_id);
				                                	i.putExtra("account_id", loc.account_id);
				                                	i.putExtra("operation",Synchronizer.DELETE);
													Synchronizer.enqueueWork(_a,i);
				                        		}
				                            }
				                            
				                    		// Delete the location:
				                    		if (!_locDB.deleteLocation(_tempItemID))
				                    		{
				                    			Util.popup(_a, R.string.DbModifyFailed);
				                    			Util.log("Could not delete location from database.");
				                    			return;
				                    		}

				                    		// Remove the corresponding geofence:
											Util.deleteGeofence(_a,_tempItemID);

				                    		// If we're in 3 pane mode, get rid of the fragment showing
										    // the location's details.
										    if (_selectedItemID==_tempItemID)
										    {
										        _a.showDetailPaneMessage(_a.getString(R.string.
										        	Select_an_item_to_display));
										    }
				                        }
				                		
				                		// Refresh the display:
				                		refreshData();
				                		_a.refreshWholeNavDrawer();
								    }
							    }
							};
							
							// Display the confirmation dialog:
							AlertDialog.Builder builder = new AlertDialog.Builder(_a);
							builder.setMessage(R.string.Location_delete_confirmation);
							builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
					        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
					        UTLLocation loc = _locDB.getLocation(_tempItemID);
					        if (loc!=null)
					        	builder.setTitle(loc.title);
				            builder.show();
				            return;
						}
					}
				});
        		
        		// Handler for a tap on the name:
        		row.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						if (!_a.inResizeMode())
						{
							// Get the item's ID:
							long id = (Long)v.getTag();
							
							// The editor is either opened as a new activity or as a fragment in
							// the details pane:
							if (_a.useNewActivityForDetails())
				        	{
								// No split-screen in use.  Just start a new activity:
								Intent i = new Intent(_a,EditLocationActivity.class);
								i.putExtra("mode", EditLocationFragment.EDIT);
								i.putExtra("id", id);
								_a.startActivity(i);
				        	}
							else
							{
								// Highlight the item that was just selected:
								changeHighlightedRow((ViewGroup)v,id);
								
								// Start the fragment:
								EditLocationFragment frag = new EditLocationFragment();
								Bundle args = new Bundle();
								args.putInt("mode", EditLocationFragment.EDIT);
								args.putLong("id", id);
								frag.setArguments(args);
								_a.placeFragment(UtlNavDrawerActivity.FRAG_DETAILS, frag, 
									EditLocationFragment.FRAG_TAG+"/"+id);
							}
						}
					}
				});
        		
        		return true;
            }
        });
        
        setListAdapter(adapter);
    	
    	// Start waiting for a location fix:
    	startGettingLocation();
    }

	// If we return here after leaving, we must refresh the data:
    @Override
    public void onResume()
    {
    	super.onResume();
    }
    
	// Start getting our location:
    private int MIN_LOCATION_WAIT = 500;
    private int MAX_LOCATION_WAIT = 30000;
    private int DESIRED_ACCURACY = 60;  // Meters
    @SuppressLint("NewApi")
	private void startGettingLocation()
    {
    	// This object responds to location updates:
    	if (_locationListener==null)
    	{
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
	    	    			// We've waited at least the minimum seconds and have the 
	    	    			// desired accuracy, so we're done.
	    	    			_locationManager.removeUpdates(this);
	    	    			
	    	    			// Stop the AsyncTask that implements the timeout:
	    	    			_locationWait.cancel(true);
	    	    			
	    	    			Util.log("Got good location after "+MIN_LOCATION_WAIT+" ms.");
	    	    			
	    	    			// Update the distances to each location:
	    	    			handleCompletedLocationFix();
	    	    		}
	    	    	}
	    	    }
	
	    	    public void onStatusChanged(String provider, int status, Bundle extras) {}
	
	    	    public void onProviderEnabled(String provider) {}
	
	    	    public void onProviderDisabled(String provider) {}
	    	 };
    	 }
    	 
    	 // Start receiving location updates:
    	 _locationManager = (LocationManager) _a.getSystemService(Context.LOCATION_SERVICE);
    	 try
    	 {
	    	 _locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, 
	    		 _locationListener);
	    	 List<String> providerList = _locationManager.getProviders(true);
	    	 if (providerList.contains(LocationManager.GPS_PROVIDER))
	    	 {
	    		 // The OS is reporting that GPS is enabled.
	    		 try
	    		 {
	    			 _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, 
	    				 _locationListener);
	    		 }
	    		 catch (IllegalArgumentException e) { }
	    	 }
	    	 _locationFixStartTime = System.currentTimeMillis();
	    	 
			 // Start a background task that stop the location fixes after a delay:
			 _locationWait = new LocationWait();
			 if (Build.VERSION.SDK_INT >= 11)
				 _locationWait.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Void[] { });
			 else
				 _locationWait.execute(new Void[] { });
    	 }
    	 catch (IllegalArgumentException e)
    	 {
    		 // This happens if the device offers no location services.  In this case, we still
    		 // allow locations to be displayed but don't display any distances.
    	 }
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
    private void handleCompletedLocationFix()
    {
		_locationManager.removeUpdates(_locationListener);
		if (_bestLocation==null)
		{
			return;
		}
		_lat = _bestLocation.getLatitude();
		_lon = _bestLocation.getLongitude();
		
		// Go through all TextViews that need updated:
		for (int i=0; i<_locationList.size(); i++)
		{
			UTLLocation loc = _locationList.get(i);
			TextView tv = _locationViewList.get(i);
			String distanceStr = getDistance(loc);
			if (loc.at_location == UTLLocation.YES)
			{
				tv.setText(Html.fromHtml("<b>"+distanceStr+"</b>"));
			}
			else
			{
				tv.setText(distanceStr);
			}
			_cachedDistances.put(loc._id, distanceStr);
		}
    }
    
    // Get a string containing the distance to a location, along with the direction:
    private String getDistance(UTLLocation loc)
    {
    	String result = "  ";
    	float[] results = new float[2];
    	Location.distanceBetween(_lat, _lon, loc.lat, loc.lon, results);
    	int dMeasure = _settings.getInt(PrefNames.DISTANCE_MEASURE, Util.MILES);
    	if (dMeasure==Util.MILES)
    	{
    		float miles = results[0] * new Float(0.000621371192237334);
    		result = roundFloat(miles)+" "+_a.getResources().getStringArray(
    			R.array.distance_abbreviations)[dMeasure];
    	}
    	else
    	{
    		float kilos = results[0] / 1000;
    		result = roundFloat(kilos)+" "+_a.getResources().getStringArray(
    			R.array.distance_abbreviations)[dMeasure];
    	}
    	
    	result += " ";
    	float bearing = results[1];
    	String[] d = _a.getResources().getStringArray(R.array.direction_abbreviations);
    	if (bearing>-22.5 && bearing<=22.5)
    		result += d[0];
    	else if (bearing>22.5 && bearing<=67.5)
    		result += d[1];
    	else if (bearing>67.5 && bearing<=112.5)
    		result += d[2];
    	else if (bearing>112.5 && bearing<=157.5)
    		result += d[3];
    	else if (bearing>157.5 || bearing<=-157.5)
    		result += d[4];
    	else if (bearing>-157.5 && bearing<=-112.5)
    		result += d[5];
    	else if (bearing>-112.6 && bearing<=-67.7)
    		result += d[6];
    	else if (bearing>-67.7 && bearing<=-22.5)
    		result += d[7];
    	result += "  ";
    	return result;
    }
    
    // Round and format a float:
    float roundFloat(float d) 
    {
    	DecimalFormat twoDForm = new DecimalFormat("#.#");
    	Number result = twoDForm.parse(twoDForm.format(d),new ParsePosition(0));
    	if (result!=null)
    		return result.floatValue();
    	else
    		return Float.valueOf(d).intValue();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	return super.onOptionsItemSelected(item);
    }
	
    /** Change the highlighted row. */
    private void changeHighlightedRow(ViewGroup row, long itemID)
    {
    	// Loop through all current views and unselect the currently-selected row:
    	if (_selectedItemID!=-1)
    	{
	    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
	    	int count = lv.getChildCount();
			for (int i=0; i<count; i++)
			{
				ViewGroup vg = (ViewGroup)lv.getChildAt(i);
				Long rowItemID = (Long)vg.getTag();
				if (rowItemID!=null && rowItemID==_selectedItemID)
				{
					// Unhighlight the row:
					unhighlightRow(vg);
				}
			}
    	}
    	
    	// Highlight the selected row:
    	highlightRow(row,itemID);
    }
    
    /** Unhighlight a row: */
    private void unhighlightRow(ViewGroup row)
    {
    	row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(
			R.attr.main_background_color)));
    	
		TextView tv = (TextView)row.findViewById(R.id.locations_list_row_name);
		tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		
		tv = (TextView)row.findViewById(R.id.locations_list_row_distance);
		tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.utl_text_color)));
		
		ImageButton deleteButton = (ImageButton)row.findViewById(R.id.locations_list_row_delete);
		deleteButton.setImageResource(_a.resourceIdFromAttr(R.attr.ab_delete_inv));
    }
    
    /** Highlight a row: */
    private void highlightRow(ViewGroup row, long itemID)
    {
    	row.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_bg_color)));
    	
    	TextView tv = (TextView)row.findViewById(R.id.locations_list_row_name);
    	tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_text_color)));
    	
    	tv = (TextView)row.findViewById(R.id.locations_list_row_distance);
		tv.setTextColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_text_color)));
		
    	ImageButton deleteButton = (ImageButton)row.findViewById(R.id.locations_list_row_delete);
		deleteButton.setImageResource(_a.resourceIdFromAttr(R.attr.delete_row_highlight));
		_selectedItemID = itemID;
    }
    
    /** Save some items when the orientation changes: */
    @Override
    public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	b.putLong("selected_item_id", _selectedItemID);
    }
    
    @Override
    public void onDestroy()
    {
    	if (_c!=null && !_c.isClosed())
    		_c.close();
    	super.onDestroy();
    }
}
