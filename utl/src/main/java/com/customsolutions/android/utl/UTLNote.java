package com.customsolutions.android.utl;

// This class holds information about a specific UTL note
public class UTLNote
{
	// Variables for the note.  These will match the corresponding database fields 
    // whenever possible:
	public long _id;
	public long td_id;  // -1 = not synced or not used
	public long account_id;
	public long mod_date;
	public String title;
	public long folder_id;  // 0 = no folder
	public String note;
	public long sync_date;
	
	public UTLNote()
	{
		_id = 0;
		td_id = -1;
		account_id = 0;
		mod_date = 0;
		title = "";
		folder_id = 0;
		note = "";
		sync_date = 0;
	}
}
