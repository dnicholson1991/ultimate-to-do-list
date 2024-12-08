package com.customsolutions.android.utl;

// This class holds the information for a specific location

public class UTLLocation
{
	// Variables for the location.  These will match the corresponding database fields when
	// possible:
	public long _id;
	public long td_id;  // -1 = not synced or not used
	public long account_id;
	public String title;
	public long mod_date;
	public long sync_date;
	public String description;
	public double lat;      // 0 if undefined
	public double lon;      // 0 if undefined
	public int at_location; // One of the values defined in this file.
	
	// Values for the "at_location" field above:
	static final public int UNKNOWN = 0;
	static final public int YES = 1;
	static final public int NO = 2;
	
	public UTLLocation()
	{
		_id = 0;
		td_id = -1;
		account_id = 0;
		title = "";
		mod_date = 0;
		sync_date = 0;
		description = "";
		lat = 0;
		lon = 0;
		at_location = UNKNOWN;
	}
	
	// Returns true if the latitude and longitude are undefined:
	public boolean hasUndefinedCoordinates()
	{
		if (lat==0 && lon==0)
			return true;
		else
			return false;
	}
	
	// Returns true if this location and another are equivalent:
	public boolean equals(UTLLocation loc)
	{
		if (title.equals(loc.title) && description.equals(loc.description) && 
			lat==loc.lat && lon==loc.lon)
		{
			return true;
		}
		else
			return false;
	}
}
