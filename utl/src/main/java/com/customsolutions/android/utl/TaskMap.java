package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class TaskMap extends AppCompatMapActivity
{
	// Command IDs for action bar:
	public static final int SHOW_CURRENT_LOCATION = 1;

	MapView _mapView;
	List<Overlay> _mapOverlays;
	Drawable _drawable;
	TaskItemizedOverlay _itemizedOverlay;
	IMapController _controller;
	LocationsDbAdapter _locDB;
	ViewsDbAdapter _viewsDB;

	// This Overlay shows the current location:
	MyLocationNewOverlay _locationOverlay;
	
	// State of the animation:
	int _currentLevel = 0;
	int _currentDirection = 1;
	
	// This keeps track of locations we need to get a task count for:
	ArrayList<UTLLocation> _locList;

	/** Listens for zoom and scroll events on the map. */
	MapListener _mapListener;

	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
    	Util.log("Starting viewing task map.");
    	
    	// Initialization code normally in UtlActivity (which we cannot inherit from):
    	Util.appInit(this);
    	Util.setTheme(this);

		Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

    	// Link this activity with a layout resource:
        setContentView(R.layout.task_map);

		ActionBar ab = getSupportActionBar();
		if (ab!=null)
		{
			// Set the title and icon:
			getSupportActionBar().setTitle(R.string.Locations);
			getSupportActionBar().setIcon(Util.resourceIdFromAttr(this, R.attr.ab_show_map));

			// Home button goes back:
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}
        
        // Link to key views:
    	_mapView = (MapView)findViewById(R.id.mapview);
    	
    	// Enable user interaction with the map:
    	_mapView.setBuiltInZoomControls(true);
    	_mapView.setMultiTouchControls(true);
    	_mapListener = new MapListener()
		{
			@Override
			public boolean onScroll(ScrollEvent event)
			{
				_itemizedOverlay.handleZoomOrScroll();
				return false;
			}

			@Override
			public boolean onZoom(ZoomEvent event)
			{
				_itemizedOverlay.handleZoomOrScroll();
				return false;
			}
		};
		_mapView.addMapListener(_mapListener);
    	
    	_mapOverlays = _mapView.getOverlays();
    	_drawable = this.getResources().getDrawable(R.drawable.green_marker);
    	_itemizedOverlay = new TaskItemizedOverlay(_drawable,_mapView);
    	_controller = _mapView.getController();
    	_viewsDB = new ViewsDbAdapter();
    	_locList = new ArrayList<UTLLocation>();

		FeatureUsage featureUsage = new FeatureUsage(this);
		featureUsage.record(FeatureUsage.TASK_MAP);
    }
    
    @SuppressLint("NewApi")
	private void refreshDisplay()
    {
    	// Clear any existing overlays:
    	_mapOverlays.clear();
    	_itemizedOverlay.clearAll();
    	
    	// Create the overlay that shows the current location:
    	_locationOverlay = new MyLocationNewOverlay(_mapView);
    	_locationOverlay.enableMyLocation();
    	_mapOverlays.add(_locationOverlay);
    	
    	_mapOverlays.add(_itemizedOverlay);
    	
    	// Add in markers for each location:
    	_locDB = new LocationsDbAdapter();
    	Cursor c = _locDB.queryLocations(null, null);
    	if (c.getCount() == 0)
    	{
    		Util.popup(this, R.string.No_Locations_Defined2);
    	}
    	_locList.clear();
    	while (c.moveToNext())
    	{
    		UTLLocation loc = _locDB.cursorToUTLLocation(c);
    		if (!loc.hasUndefinedCoordinates())
    			_locList.add(loc);
    	}
    	if (_locList.size()>0)
    	{
    		// The task counting and marker placement is done in the background:
    		if (Build.VERSION.SDK_INT >= 11)
    			new GetTaskCountTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_locList.get(0));
    		else
    			new GetTaskCountTask().execute(_locList.get(0));
    	} 
    	
    	// By default, the map should not be zoomed out too much:
    	if (_mapView.getZoomLevel()<10)
    		_controller.setZoom(10);    	
    	
    	// Once we have a fix, center the map:
    	_locationOverlay.runOnFirstFix(new CenterMapRunnable());
    }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	MenuUtil.init(menu);
    	MenuUtil.add(SHOW_CURRENT_LOCATION, R.string.Current_Location, Util.resourceIdFromAttr(this, 
    		R.attr.ab_my_location));
		return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	switch(item.getItemId())
    	{
    	case android.R.id.home:
    		finish();
    		return true;
    		
    	case SHOW_CURRENT_LOCATION:
    		GeoPoint myLoc = _locationOverlay.getMyLocation();
			if (myLoc!=null)
			{
    			try
    			{    			
    				_controller.animateTo(myLoc);
    			}
    			catch (Exception e)
    			{
    				// On one occasion when testing, we got an ArrayIndexOutOfBoundsException
    				// that was not our fault.  Rather than allow a force-close message
    				// to display, we will just quietly allow the animation to fail.
    				Util.log("Got an exception when user pressed My Location button: "+
    					e.getClass().getName()+": "+e.getMessage());
					Util.popup(TaskMap.this, R.string.Location_Not_Determined);
    			}
			}
			else
				Util.popup(TaskMap.this, R.string.Location_Not_Determined);
    		return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    // This will center the map at the current location when the first fix is obtained:
    private class CenterMapRunnable implements Runnable
    {
    	@Override
    	public void run()
    	{
    		final GeoPoint geo = _locationOverlay.getMyLocation();
    		if (geo!=null)
    		{
    			try
    			{
    				new Handler(Looper.getMainLooper()).post(new Runnable()
					{
						@Override
						public void run()
						{
							_controller.animateTo(geo);
						}
					});
    			}
    			catch (Exception e)
    			{
    				// On one occasion when testing, we got an ArrayIndexOutOfBoundsException
    				// that was not our fault.  Rather than allow a force-close message
    				// to display, we will just quietly allow the animation to fail.
    				Util.log("Got an exception when animating to current location: "+
    					e.getClass().getName()+": "+e.getMessage());
    			}
    		}
    	}
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	refreshDisplay();
    }
    
    // Handler for pausing the activity (when we leave):
    @Override
    public void onPause()
    {
        super.onPause();
        
        _locationOverlay.disableMyLocation();
    }

    @Override
	public void onDestroy()
	{
		if (_mapListener!=null && _mapView!=null)
			_mapView.removeMapListener(_mapListener);
		super.onDestroy();
	}

    /*
	@Override
	protected boolean isRouteDisplayed()
	{
		return false;
	}
	*/

    // The AsyncTask instance, which gets the number of tasks for a view in the background.
    private class GetTaskCountTask extends AsyncTask<UTLLocation,Void,Integer>
    {
    	protected Integer doInBackground(UTLLocation... locs)
    	{
    		UTLLocation loc = locs[0];
    		
    		// Get the view that corresponds to the location:
    		Cursor c = _viewsDB.getView("locations", new Long(loc._id).toString());
    		if (!c.moveToFirst())
    		{
    			// Should not happen!
    			return 0;
    		}
    		long viewID = Util.cLong(c, "_id");    		
    		return Util.getNumTasksForView(viewID);
    	}
    	
    	@SuppressLint("NewApi")
		protected void onPostExecute(Integer count)
    	{
    		// Place the marker:
    		if (_locList.size()==0)
    			return;
    		UTLLocation loc = _locList.get(0);
    		GeoPoint point = new GeoPoint(new Double(loc.lat*1000000).intValue(),
    			new Double(loc.lon*1000000).intValue());
    		OverlayItem overlayItem;
    		if (count!=1)
    		{
    			overlayItem = new OverlayItem(loc.title,count.toString()+
					" "+Util.getString(R.string.Tasks),point);
    			/* overlayItem = new OverlayItem(point, loc.title, count.toString()+
    				" "+Util.getString(R.string.Tasks)); */
    		}
    		else
    		{
    			UTLTask task = (new TasksDbAdapter()).getTask(Util.firstTaskID);
    			if (task==null)
    			{
					overlayItem = new OverlayItem(loc.title,count.toString()+
						" "+Util.getString(R.string.Tasks),point);
    				/* overlayItem = new OverlayItem(point, loc.title, count.toString()+
    					" "+Util.getString(R.string.Task)); */
    			}
    			else
    			{
					overlayItem = new OverlayItem(loc.title,count.toString()+
						" "+Util.getString(R.string.Tasks),point);
    				/* overlayItem = new OverlayItem(point, loc.title, task.title); */
    			}
    		}
    		Drawable locationDot;
    		if (count==0)
    		{
    			locationDot = TaskMap.this.getResources().getDrawable(R.drawable.
    				green_marker);
    		}
    		else
    		{
    			locationDot = TaskMap.this.getResources().getDrawable(R.drawable.
    				red_marker);
    		}
    		overlayItem.setMarker(locationDot);
    		_itemizedOverlay.addOverlay(overlayItem,loc);
    		
    		// If we have more markers to place, start counting tasks again:
    		_locList.remove(0);
    		if (_locList.size()>0)
    		{
    			if (Build.VERSION.SDK_INT >= 11)
    				new GetTaskCountTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_locList.get(0));
    			else
    				new GetTaskCountTask().execute(_locList.get(0));
    		}
    		else
    			_mapView.invalidate();
    	}
    }
}
