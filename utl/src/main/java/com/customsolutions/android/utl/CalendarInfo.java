package com.customsolutions.android.utl;

// This class contains information about a calendar in the system:

public class CalendarInfo
{
	public String name;
	public long id; 
	public String uriBase;  // Example: content://com.android.calendar; not used in ICS
	
	public CalendarInfo()
	{
		name = "";
		id = 0;
		uriBase = "";
	}
	
	public CalendarInfo(long newID, String newName, String newUriBase)
	{
		name = newName;
		id = newID;
		uriBase = newUriBase;
	}
}
