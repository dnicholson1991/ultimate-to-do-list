package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class LocationsDbAdapter
{
	// The table we're working with:
    private static final String TABLE = "locations";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+                             // -1 = not synced yet
        "account_id integer not null, "+
        "title text not null, "+
        "mod_date integer not null, "+
        "sync_date integer, "+
        "description text, "+
        "lat real, "+            // 0 = undefined
        "lon real, "+            // 0 = undefined
        "at_location integer"+   // Indicates if we're at this location. Values defined in UTLLocation.java
        ")";
    
    // The order in which columns are queried:
    private static final String[] COLUMNS = { "_id","td_id","account_id","title",
    	"mod_date","sync_date","description","lat","lon","at_location"
        };
    
    public static final String[] INDEXES = {
    	"create index if not exists td_id_index on locations(td_id)",
    	"create index if not exists account_id_index on locations(account_id)",
    };

    private static boolean tablesCreated = false;

    // The constructor.  This opens the database for access:
    public LocationsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	for (int i=0; i<INDEXES.length; i++)
        	{
        		Util.db().execSQL(INDEXES[i]);
        	}
        	tablesCreated = true;
        }
    }
    
    // Separate function to create the DB tables and indexes:
    static public void createTableAndIndexes(SQLiteDatabase db)
    {
    	db.execSQL(TABLE_CREATE);
    	for (int i=0; i<INDEXES.length; i++)
    	{
    		db.execSQL(INDEXES[i]);
    	}
    	tablesCreated = true;
    }
    
    // Add a location to the database.  If successful, the ID of the location is returned,
    // else -1.  This method automatically fills in the mod_date field,
    // and also updates the _id field for the note object passed in.
    public long addLocation(UTLLocation loc)
    {
    	ContentValues values = new ContentValues();
        values.put("td_id", loc.td_id);
        values.put("account_id", loc.account_id);
        values.put("title", loc.title);
        if (loc.mod_date==0)
        {
        	values.put("mod_date", System.currentTimeMillis());
        }
        else
        {
        	values.put("mod_date", loc.mod_date);
        }
        values.put("sync_date",loc.sync_date);
        values.put("description", loc.description);
        values.put("lat",loc.lat);
        values.put("lon", loc.lon);
        values.put("at_location", loc.at_location);
        
        long id = Util.db().insert(TABLE, null, values);
        if (id==-1)
        {
        	return -1;
        }
        else
        {
        	loc._id = id;
        	return id;
        }
    }
    
    // Delete a location.  Returns true on success, else false:
    public boolean deleteLocation(long id)
    {
    	return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    public boolean deleteLocation(long accountID, long TDID)
    {
    	return Util.db().delete(TABLE, "account_id="+accountID+" and td_id="+TDID, null) > 0;
    }
 
    // Modify a location.  Returns true if updated, false otherwise.
    // This does not automatically fill in any fields.
    public boolean modifyLocation(UTLLocation loc)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", loc.td_id);
        values.put("account_id", loc.account_id);
        values.put("title", loc.title);
        values.put("mod_date", loc.mod_date);
        values.put("sync_date", loc.sync_date);
        values.put("description", loc.description);
        values.put("lat",loc.lat);
        values.put("lon", loc.lon);
        values.put("at_location", loc.at_location);
       
        return Util.db().update(TABLE, values, "_id="+loc._id, null) > 0;
    }
    
    // Modify the TD ID:
    public boolean modifyTDID(long id, long TDID)
    {
    	ContentValues values = new ContentValues();
    	values.put("td_id",TDID);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Get a specific location.  Returns a UTLLocation object or null if it cannot be found.
    public UTLLocation getLocation(long id)
    {
    	Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLLocation loc = cursorToUTLLocation(c);
        	c.close();
        	return loc;
        }
        else
        {
        	c.close();
        	return null;
        }
    }
    public UTLLocation getLocation(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and td_id="+TDID,
                null, null, null, null);
        if (c.moveToFirst())
        {
        	UTLLocation loc = cursorToUTLLocation(c);
        	c.close();
        	return loc;
        }
        else
        {
        	c.close();
        	return null;
        }
    }
    
    // A general locations query.  Returns a cursor:
    public Cursor queryLocations(String sqlWhere, String sqlOrderBy)
    {
    	Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        return c;
    }
    
    // Get all locations.  Returns a cursor:
    public Cursor getAllLocations()
    {
    	return Util.db().query(TABLE, COLUMNS, null, null, null, null, "title");
    }
    
    // Convert a database cursor to a UTLLocation object:
    public UTLLocation cursorToUTLLocation(Cursor c)
    {
    	UTLLocation loc = new UTLLocation();
    	loc._id = c.getLong(0);
    	loc.td_id = c.getLong(1);
    	loc.account_id = c.getLong(2);
    	loc.title = c.getString(3);
    	loc.mod_date = c.getLong(4);
    	loc.sync_date = c.getLong(5);
    	loc.description = c.getString(6);
    	loc.lat = c.getDouble(7);
    	loc.lon = c.getDouble(8);
    	loc.at_location = c.getInt(9);
    	return loc;
    }
}
