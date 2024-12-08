package com.customsolutions.android.utl;

// This class is used for storage of account information.

public class UTLAccount 
{
	// Specifies which sync service (if any) is being used.
	public static final int SYNC_NONE = 0;
	public static final int SYNC_TOODLEDO = 1;
	public static final int SYNC_GOOGLE = 2;
	
    // Variables for the account, which will match the corresponding database fields
    // whenever possible:
    public long _id;
    public String name;
    public String td_email;  // Will be "" if no TD account is used
    public String td_password;
    public String td_userid;
    public long last_sync;  // UNIX timestamp in ms (last FULL sync)
    public long token_expiry;  // UNIX timestamp in ms
    public String current_key;
    public long time_zone;  // Number of half hours that zone is offset from TD server
    public String conflict_policy; // keep_mobile, keep_toodledo, keep_latest
    public boolean pro;  // true if this is a pro account
    public int hide_months;  // Hide tasks due this many months into the future
    public int hotlist_priority;  // Matches TD's interface, but is 2 higher (like DB)
    public int hotlist_due_date;  // Number of days in the future (for due date) to display
    public String current_token;
    public boolean enable_alarms; // If true, alarms and notifications occur (time based)
    public boolean enable_loc_alarms;  // For location alarms
    public int sync_service; // One of the values above
    public String username; // Username, for sync service other than Toodledo
    public String password; // Password, for sync service other than Toodledo
    public boolean use_note_for_extras;  // If true, use the note field for UTL data not 
                                         // supported by sync service
    public String etag; // Used by Google to determine if any changes are made to the account.
    public String refresh_token;  // Used for OAuth2 to get a new token.
    public int protocol;  // Integer that specifies what protocol is used. Values depend on sync service.
    public boolean sign_in_needed;  // If true, the user needs to sign into the account again in order to sync.
    
    public UTLAccount()
    {
        name = "";
        td_email = "";
        td_password = "";
        td_userid = "";
        last_sync = 0;
        token_expiry = 0;
        current_key = "";
        time_zone = 0;
        conflict_policy = "keep_latest";
        pro = false;
        hide_months = 3;
        hotlist_priority = 3;
        hotlist_due_date = 0;
        current_token = "";
        enable_alarms = true;
        enable_loc_alarms = true;
        sync_service = SYNC_NONE;
        username = "";
        password = "";
        use_note_for_extras = true;
        etag = "";
        refresh_token = "";
        protocol = 0;
        sign_in_needed = false;
    }    
}
