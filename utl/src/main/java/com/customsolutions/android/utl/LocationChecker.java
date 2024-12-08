package com.customsolutions.android.utl;

// This class handles location reminders.  It checks the current location, checks to see if any 
// tasks with location reminders are close to that location, and then places the notifications on 
// the device's notification bar for any tasks within range.

import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;

import android.Manifest;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

public class LocationChecker extends JobService
{
	/** The Job ID used in job scheduling. */
	public static final int JOB_ID = 1539103447;

	// Constants that affect location fixes:
	private int MIN_LOCATION_WAIT = 10000;
	private int MAX_LOCATION_WAIT = 30000;
	private int DESIRED_ACCURACY = 30;  // Meters
	
    // Objects for location tracking:
    private LocationManager _locationManager;
    private LocationListener _locationListener;
	private Location _bestLocation;
	private long _locationFixStartTime;
	private LocationWait _locationWait;

    // A reference to the settings/preferences.  We can't use the reference in Util
    // due to separate threads accessing this.
    private SharedPreferences _settings;

    /** Keeps track of the JobParameters passed in from the system. */
    private JobParameters _jobParams;

    @Override
    public boolean onStartJob (JobParameters params)
    {
		// Initialize the app if needed:
		Util.appInit(this);

		Util.log("LocationChecker: onStartJob called. Job ID: "+params.getJobId());

    	// Don't do anything if permissions are not granted.
		int courseLocationCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.
			ACCESS_COARSE_LOCATION);
		int fineLocationCheck = ContextCompat.checkSelfPermission(this,Manifest.permission.
			ACCESS_FINE_LOCATION);
		if (courseLocationCheck== PackageManager.PERMISSION_DENIED && fineLocationCheck==
			PackageManager.PERMISSION_DENIED)
		{
			Util.log("LocationChecker: WARNING: Cannot start location checks due to lack of permission.");
			return false;
		}

		if (Build.VERSION.SDK_INT>Build.VERSION_CODES.P && PackageManager.PERMISSION_DENIED==
			ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION))
		{
			Util.log("LocationChecker: WARNING: Cannot start location checks due to lack of " +
				"background permission.");
			return false;
		}

		// Get a reference to the app's settings:
		_settings = this.getSharedPreferences("UTL_Prefs",0);

		_bestLocation = null;
		_jobParams = params;

		// If the location field is disabled, there's nothing to do here:
		if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
		{
			Util.log("LocationChecker: Skipping location check since locations are disabled.");
			return false;
		}

    	startLocationCheck();
    	return true;
    }
    
	private void startLocationCheck()
	{
    	_bestLocation = null;
    	
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
	    	    			// We've waited at least 10 seconds and we have a desired accuracy, so we're 
	    	    			// done.
	    	    			_locationManager.removeUpdates(_locationListener);
	    	    			
	    	    			// Stop the AsyncTask that implements the timeout:
	    	    			_locationWait.cancel(true);
	    	    			
	    	    			Util.log("LocationChecker: Got good location after 10 secs.");
	    	    			
	    	    			// Call the function to handle the location update:
	    	    			handleCompletedLocationFix();
	    	    		}
	    	    	}
	    	    }
	
	    	    public void onStatusChanged(String provider, int status, Bundle extras)
				{
					Util.log("LocationChecker: onStatusChanged: "+provider+" / "+status);
	    	    }
	
	    	    public void onProviderEnabled(String provider)
				{
					Util.log("LocationChecker: onProviderEnabled: "+provider);
	    	    }
	
	    	    public void onProviderDisabled(String provider)
				{
					Util.log("LocationChecker: onProviderDisabled: "+provider);
	    	    }
	    	 };
    	 }
    	 
    	 // Start receiving location updates:
    	 _locationManager = (LocationManager) this.getSystemService(LocationChecker.LOCATION_SERVICE);
    	 int numProviders = 2;
    	 try
    	 {
    		 if (_settings.getInt(PrefNames.LOCATION_PROVIDERS, Util.DEFAULT_LOCATION_PROVIDERS) ==
    			 	Util.LOCATION_PROVIDER_BOTH ||
    			 _settings.getInt(PrefNames.LOCATION_PROVIDERS, Util.DEFAULT_LOCATION_PROVIDERS) ==
    			 	Util.LOCATION_PROVIDER_NETWORK_ONLY)
    		 {
    		 	try
				{
					_locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
						_locationListener);
				}
				catch (SecurityException e)
				{
					Util.log("LocationChecker: WARNING: Got SecurityException when checking location.");
				}
    		 }
    		 else
    		 {
    			 numProviders--;
    			 Util.log("LocationChecker: Network provider disabled by user.");
    		 }
    	 }
    	 catch (IllegalArgumentException e)
    	 {
    		 // No network location services are available.
    		 numProviders--;
    	 }
    	 try
    	 {
    		 if (_settings.getInt(PrefNames.LOCATION_PROVIDERS, Util.DEFAULT_LOCATION_PROVIDERS) ==
 			 	Util.LOCATION_PROVIDER_BOTH ||
 			 _settings.getInt(PrefNames.LOCATION_PROVIDERS, Util.DEFAULT_LOCATION_PROVIDERS) ==
 			 	Util.LOCATION_PROVIDER_GPS_ONLY)
    		 {
				 try
				 {
					 _locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
						 _locationListener);
				 }
				 catch (SecurityException e)
				 {
					 Util.log("LocationChecker: WARNING: Got SecurityException when checking location.");
				 }
    		 }
    		 else
    		 {
    			 numProviders--;
    			 Util.log("LocationChecker: GPS provider disabled by user.");
    		 }
    	 }
    	 catch (IllegalArgumentException e)
    	 {
    		 // No GPS is available.
    		 numProviders--;
    	 }
    	 if (numProviders==0)
    	 {
    		 // Nothing we can do.  Location reminders will not work.
    		 Util.log("LocationChecker: No location providers exist.");
    		 jobFinished(_jobParams,false);
    		 return;
    	 }
    	 _locationFixStartTime = System.currentTimeMillis();
    	 
    	 // Start a background task that stop the location fixes after a delay:
    	 _locationWait = new LocationWait();
    	 _locationWait.execute(new Void[] { });
    	 Util.log("LocationChecker: Starting a location fix.");
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
    		Util.log("LocationChecker: Timed out waiting for good location.");
    		handleCompletedLocationFix();
    	}
    }

    // Handle the completion of a location fix:
    private void handleCompletedLocationFix()
    {
		_locationManager.removeUpdates(_locationListener);
		if (_bestLocation==null)
		{
			Util.log("LocationChecker: Could not get any location.");
			jobFinished(_jobParams,false);
			return;
		}

		boolean hasSem = Util.acquireSemaphore("LocationChecker", this);
		try
		{
			Util.log("LocationChecker: Current Location: Lat: "+_bestLocation.getLatitude()+"  Lon: "+
				_bestLocation.getLongitude()+"  Acc: "+_bestLocation.getAccuracy());
			
			// Go through all locations and update their status:
			LocationsDbAdapter locDB = new LocationsDbAdapter();
			int reminderRadius = _settings.getInt("loc_alarm_radius", 200);
			HashSet<Long> locationsEntered = new HashSet<Long>();
			Cursor c = locDB.getAllLocations();
			while (c.moveToNext())
			{
				UTLLocation l = locDB.cursorToUTLLocation(c);
				
				// Get the distance between our current location and this one:
				float[] distanceResults = new float[3];
				Location.distanceBetween(l.lat, l.lon, _bestLocation.getLatitude(), 
					_bestLocation.getLongitude(), distanceResults);
				
				if (distanceResults[0]<=reminderRadius)
				{
					// We ARE at the location:
					if (l.at_location != UTLLocation.YES)
					{
						// We just entered this location.  Make a note of this so we can display 
						// task reminders.
						locationsEntered.add(l._id);
						Util.log("LocationChecker: Entered location '"+l.title+"'");
						
						// Update the status of the location in the database:
						l.at_location = UTLLocation.YES;
						locDB.modifyLocation(l);
					}
					else
					{
						Util.log("LocationChecker: "+l.title+": Still at location");
					}
				}
				else
				{
					// We ARE NOT at the location:
					if (l.at_location != UTLLocation.NO)
					{
						// Update the status of the location in the database:
						l.at_location = UTLLocation.NO;
						locDB.modifyLocation(l);
						Util.log("LocationChecker: Exited location '"+l.title+"'");
					}
					else
					{
						Util.log("LocationChecker: "+l.title+": Still not at location");
					}
				}
			}	
			c.close();
			
			if (locationsEntered.size()>0)
			{
				// We have entered one or more locations since the last check, so we might have some
				// reminders to display.  Generate a list of locations for inclusion in the SQL query.
				String whereString = "";
				Iterator<Long> it = locationsEntered.iterator();
				boolean isFirst = true;
				while (it.hasNext())
				{
					long locID = it.next();
					if (!isFirst)
					{
						whereString += ",";
					}
					whereString += locID;
					isFirst = false;
				}
				
				// Get a list of tasks that have location reminders enabled:
				TasksDbAdapter tasksDB = new TasksDbAdapter();
				c = tasksDB.queryTasks("location_id in ("+whereString+") and completed=0 and "+
					"location_reminder=1",null);
				c.moveToPosition(-1);
				while (c.moveToNext())
				{
					UTLTask t = tasksDB.getUTLTask(c);
					
					// If the start date is in the future, do not display anything:
					TimeZone sysTimeZone = TimeZone.getDefault();
			    	TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
			    		"home_time_zone", "America/Los_Angeles"));
			    	long timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) - 
						appTimeZone.getOffset(System.currentTimeMillis());
					if (t.start_date>System.currentTimeMillis()+timeZoneOffset)
						continue;
				
					// Create an intent that will be sent to the Notifier broadcast receiver:
			    	Uri.Builder uriBuilder = new Uri.Builder();
			    	uriBuilder.scheme("viewtask");
			    	uriBuilder.opaquePart(Long.valueOf(t._id).toString());
			    	Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),this,Notifier.class);
			    	i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);
					
			    	// Send the intent:
			    	Util.log("LocationChecker: Displaying location reminder for task '"+t.title+"'");
			    	sendBroadcast(i);
				}
				c.close();
			}
		}
		finally
		{
			if (hasSem)
				Util._semaphore.release();
			jobFinished(_jobParams,false);
		}
    }

	@Override
	public boolean onStopJob(JobParameters params)
	{
		Util.log("LocationChecker: WARNING: onStopJob() called, but I can't stop it.");
		return true;
	}
}
