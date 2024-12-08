package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class AccountsDbAdapter 
{
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists account_info ("+
        "_id integer primary key autoincrement, "+
        "name text not null, "+
        "td_email text, "+
        "td_password text, "+
        "td_userid text, "+
        "last_sync integer, "+
        "token_expiry integer, "+
        "current_key text, "+
        "time_zone integer, "+
        "conflict_policy text, "+
        "pro integer, "+
        "hide_months integer, "+
        "hotlist_priority integer, "+
        "hotlist_due_date integer, "+
        "current_token text, "+
        "enable_alarms integer, "+  // Set to 1 if time-based alarms are enabled for account.
        "enable_loc_alarms integer, "+  // Set to 1 if location alarms are enabled.
        "sync_service integer, "+  // One of the values in UTLAccount.java
        "username text, "+  // Non-toodledo only
        "password text, "+  // Non-toodledo only
        "use_note_for_extras integer, "+  // Set to 1 to use note field for unsupported sync data
        "etag text, "+  // Used by Google to determine if anything has changed in the account.
        "refresh_token text, "+ // Used for OAuth2 to get a new token.
        "protocol integer, "+ // Integer that specifies what protocol is used. Values depend on sync service.
        "sign_in_needed integer"+ // Set to 1 if the user needs to sign into the account again.
        ");";
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","name","td_email","td_password",
        "td_userid","last_sync","token_expiry","current_key","time_zone",
        "conflict_policy","pro","hide_months","hotlist_priority",
        "hotlist_due_date","current_token","enable_alarms","enable_loc_alarms",
        "sync_service","username","password","use_note_for_extras","etag","refresh_token",
        "protocol","sign_in_needed"
    };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public AccountsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	tablesCreated = true;
        }
    }
    
    // Insert an account into the database.  If successful, the id of the account is
    // returned, else -1.  This also updates the UTLAccount instance to include the
    // new ID.
    public long addAccount(UTLAccount a)
    {
        ContentValues values = new ContentValues();
        values.put("name", a.name);
        if (a.td_email != null)
        {
            values.put("td_email", a.td_email);
        }
        if (a.td_password != null)
        {
            values.put("td_password", a.td_password);
        }
        if (a.td_userid != null)
        {
            values.put("td_userid", a.td_userid);
        }
        values.put("last_sync", a.last_sync);
        values.put("token_expiry", a.token_expiry);
        if (a.current_key != null)
        {
            values.put("current_key", a.current_key);
        }
        values.put("time_zone", a.time_zone);
        if (a.conflict_policy != null)
        {
            values.put("conflict_policy", a.conflict_policy);
        }
        if (a.pro)
        {
            values.put("pro",1);
        }
        else
        {
            values.put("pro",0);
        }
        values.put("hide_months", a.hide_months);
        values.put("hotlist_priority", a.hotlist_priority);
        values.put("hotlist_due_date", a.hotlist_due_date);
        values.put("current_token",a.current_token);
        values.put("enable_alarms",a.enable_alarms ? 1 : 0);
        values.put("enable_loc_alarms",a.enable_loc_alarms ? 1 : 0);
        values.put("sync_service", a.sync_service);
        values.put("username", a.username);
        values.put("password", a.password);
        values.put("use_note_for_extras", a.use_note_for_extras ? 1 : 0);
        values.put("etag", a.etag);
        values.put("refresh_token", a.refresh_token);
        values.put("protocol", a.protocol);
        values.put("sign_in_needed", a.sign_in_needed ? 1: 0);
        
        Util.checkDatabaseLock(Util.db());
        long id = Util.db().insert("account_info", null, values);
        if (id>-1)
        {
            a._id = id;
        }
        return id;
    }
    
    // Get an account by name.  Returns null if not found:
    public UTLAccount getAccount(String name)
    {
    	// Query the DB:
        Cursor c = Util.db().query("account_info", COLUMNS, "name='"+Util.makeSafeForDatabase(
        	name)+"'", null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLAccount acct = getUTLAccount(c);
        	c.close();
            return acct;
        }
        else
        {
        	c.close();
            return null;
        }
    }
    
    // Get an account based on the Toodledo User ID:
    public UTLAccount getAccountByTDUserID(String userID)
    {
    	// Query the DB:
        Cursor c = Util.db().query("account_info", COLUMNS, "td_userid='"+Util.makeSafeForDatabase(
        	userID)+"'", null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLAccount acct = getUTLAccount(c);
        	c.close();
            return acct;
        }
        else
        {
        	c.close();
            return null;
        }
    }

    // Get an account based on the Google username:
    public UTLAccount getAccountByGoogleUsername(String username)
    {
    	// Query the DB:
        Cursor c = Util.db().query("account_info", COLUMNS, "username='"+Util.makeSafeForDatabase(
        	username)+"' and sync_service="+UTLAccount.SYNC_GOOGLE, null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLAccount acct = getUTLAccount(c);
        	c.close();
            return acct;
        }
        else
        {
        	c.close();
            return null;
        }
    }
    
    // Delete an account from the database.  The input is the account ID.
    // Returns true if deleted, false otherwise.
    public boolean deleteAccount(long id)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete("account_info", "_id="+id, null) > 0;
    }
    
    // Modify an account.  The input is the UTL account instance.  The _id value
    // from this instance is used to identify the DB record to update.
    // Returns true if updated, false otherwise.
    public boolean modifyAccount(UTLAccount a)
    {
        ContentValues values = new ContentValues();
        values.put("name", a.name);
        if (a.td_email != null)
        {
            values.put("td_email", a.td_email);
        }
        else
        {
            values.put("td_email", "");
        }
        if (a.td_password != null)
        {
            values.put("td_password", a.td_password);
        }
        else
        {
            values.put("td_password", "");
        }
        if (a.td_userid != null)
        {
            values.put("td_userid", a.td_userid);
        }
        else
        {
            values.put("td_userid", "");
        }
        values.put("last_sync", a.last_sync);
        values.put("token_expiry", a.token_expiry);
        if (a.current_key != null)
        {
            values.put("current_key", a.current_key);
        }
        else
        {
            values.put("current_key", "");
        }
        values.put("time_zone", a.time_zone);
        if (a.conflict_policy != null)
        {
            values.put("conflict_policy", a.conflict_policy);
        }
        else
        {
            values.put("conflict_policy","");
        }
        if (a.pro)
        {
            values.put("pro",1);
        }
        else
        {
            values.put("pro",0);
        }
        values.put("hide_months", a.hide_months);
        values.put("hotlist_priority", a.hotlist_priority);
        values.put("hotlist_due_date", a.hotlist_due_date);
        values.put("current_token",a.current_token);
        values.put("enable_alarms",a.enable_alarms ? 1 : 0);
        values.put("enable_loc_alarms",a.enable_loc_alarms ? 1 : 0);
        values.put("sync_service", a.sync_service);
        values.put("username", a.username);
        values.put("password", a.password);
        values.put("use_note_for_extras", a.use_note_for_extras ? 1 : 0);
        values.put("etag", a.etag);
        values.put("refresh_token", a.refresh_token);
        values.put("protocol", a.protocol);
        values.put("sign_in_needed", a.sign_in_needed ? 1 : 0);
        
        Util.checkDatabaseLock(Util.db());
        return Util.db().update("account_info", values, "_id="+a._id, null) > 0;   
    }
    
    // Get a specific account.  Returns null if the account cannot be found.
    public UTLAccount getAccount(long id)
    {
        // Query the DB:
        Cursor c = Util.db().query("account_info", COLUMNS, "_id="+id, null, null, null, 
            null);
        if (c.moveToFirst())
        {
        	UTLAccount acct = getUTLAccount(c);
        	c.close();
            return acct;
        }
        else
        {
        	c.close();
            return null;
        }
    }
    
    // Get a cursor that can be used to go through all accounts in the system:
    public Cursor getAllAccounts()
    {        
        return Util.db().query("account_info", COLUMNS, null, null, null, null, "name");
    }
    
    /** Get the number of accounts. */
    public int getNumAccounts()
    {
    	Cursor c = getAllAccounts();
    	int count = c.getCount();
    	c.close();
    	return count;
    }
    
    // Given a database cursor, get an instance of UTLAccount:
    public UTLAccount getUTLAccount(Cursor c)
    {
        UTLAccount a = new UTLAccount();
        a._id = c.getInt(0);
        a.name = c.getString(1);
        a.td_email = c.getString(2);
        a.td_password = c.getString(3);
        a.td_userid = c.getString(4);
        a.last_sync = c.getLong(5);
        a.token_expiry = c.getLong(6);
        a.current_key = c.getString(7);
        a.time_zone = c.getInt(8);
        a.conflict_policy = c.getString(9);
        if (c.getInt(10)==0)
        {
            a.pro = false;
        }
        else
        {
            a.pro = true;
        }
        a.hide_months = c.getInt(11);
        a.hotlist_priority = c.getInt(12);
        a.hotlist_due_date = c.getInt(13);
        a.current_token = c.getString(14);
        a.enable_alarms = c.getInt(15)==1 ? true : false;
        a.enable_loc_alarms	= c.getInt(16)==1 ? true : false;
        a.sync_service = c.getInt(17);
        a.username = c.getString(18);
        a.password = c.getString(19);
        a.use_note_for_extras = c.getInt(20)==1 ? true : false;
        a.etag = c.getString(21);
        a.refresh_token = c.getString(22);
        a.protocol = c.getInt(23);
        a.sign_in_needed = c.getInt(24)==1 ? true : false;
        
        return a;
    }
}
