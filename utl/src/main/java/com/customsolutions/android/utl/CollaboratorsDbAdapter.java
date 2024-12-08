package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class CollaboratorsDbAdapter
{
	// The name of the table we're using:
	private static final String TABLE = "collaborators";
	
	private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
		"_id integer primary key autoincrement, "+
		"account_id integer not null, "+
		"remote_id text not null, "+
		"name text not null, "+
		"reassignable integer not null, "+
		"sharable integer not null"+
	")";
	
	public static final String[] INDEXES = {
		"create index if not exists account_id_index on "+TABLE+"(account_id)",
		"create index if not exists remote_id_index on "+TABLE+"(remote_id)",
	};
	
	public static final String[] COLUMNS = {
		"_id",
		"account_id",
		"remote_id",
		"name",
		"reassignable",
		"sharable"
	};
	
	private static boolean tablesCreated = false;
	
    // The constructor.  This opens the database for access:
    public CollaboratorsDbAdapter() throws SQLException
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
    
    // Insert a collaborator into the database.  If successful, the ID of the collaborator
    // is returned, else -1.  The UTLColloborator instance passed in is updated to include
    // the ID.
    public long addCollaborator(UTLCollaborator c)
    {
    	ContentValues values = new ContentValues();
    	values.put("account_id", c.account_id);
    	values.put("remote_id", c.remote_id);
    	values.put("name",c.name);
    	values.put("reassignable", c.reassignable ? 1 : 0);
    	values.put("sharable", c.reassignable ? 1 : 0);
    	
    	Util.checkDatabaseLock(Util.db());
        long id = Util.db().insert(TABLE, null, values);
        if (id>-1)
        {
            c._id = id;
        }
        return id;
    }
    
    // Delete a collaborator:
    public boolean deleteCollaborator(long id)
    {
    	Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    // Delete all collaborators:
    public void deleteAll(long accountID)
    {
    	Util.checkDatabaseLock(Util.db());
    	Util.db().execSQL("delete from "+TABLE+" where account_id="+accountID);
    }
    
    // Delete a collaborator, given the account ID and remote ID:
    public boolean deleteCollaborator(String remoteID, long accountID)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "remote_id='"+Util.makeSafeForDatabase(remoteID)+
        	"' and account_id="+accountID, null) > 0;
    }
    
    // Modify a collaborator.  Returns true if updates, else false.
    public boolean modifyCollaborator(UTLCollaborator c)
    {
    	ContentValues values = new ContentValues();
    	values.put("account_id", c.account_id);
    	values.put("remote_id", c.remote_id);
    	values.put("name",c.name);
    	values.put("reassignable", c.reassignable ? 1 : 0);
    	values.put("sharable", c.reassignable ? 1 : 0);
    	
    	Util.checkDatabaseLock(Util.db());
        return Util.db().update(TABLE, values, "_id="+c._id, null) > 0;
    }
    
    // Convert a Cursor to a UTLCollaborator
    public UTLCollaborator cursorToCollaborator(Cursor c)
    {
    	UTLCollaborator co = new UTLCollaborator();
    	co._id = c.getLong(0);
    	co.account_id = c.getLong(1);
    	co.remote_id = c.getString(2);
    	co.name = c.getString(3);
    	co.reassignable = c.getInt(4)==1 ? true : false;
    	co.sharable = c.getInt(5)==1 ? true : false;
    	
    	return co;
    }
    
    // Get a specific collaborator.  Returns a cursor.
    public Cursor getCollaborator(long id)
    {
    	return Util.db().query(TABLE,COLUMNS, "id="+id, null, null, null ,null);
    }
    
    // Get a specific collaborator given a remote ID and account ID.  Returns null if not 
    // found:
    public UTLCollaborator getCollaborator(long accountID, String remoteID)
    {
    	Cursor c = this.queryCollaborators("account_id="+accountID+" and remote_id='"+
    		Util.makeSafeForDatabase(remoteID)+"'",null);
    	if (c!=null && c.getCount()>0)
    	{
    		c.moveToFirst();
    		return cursorToCollaborator(c);
    	}
    	else
    		return null;
    }
    
    // Get a specific collaborator given a name and account ID.  Returns null if not 
    // found:
    public UTLCollaborator getCollaboratorByName(long accountID, String name)
    {
    	Cursor c = this.queryCollaborators("account_id="+accountID+" and name='"+
    		Util.makeSafeForDatabase(name)+"'",null);
    	if (c!=null && c.getCount()>0)
    	{
    		c.moveToFirst();
    		return cursorToCollaborator(c);
    	}
    	else
    		return null;
    }

    // Get a collaborator when only the remote ID is known.  This assumes that the caller
    // does not care about which account the collaborator is in.  Toodledo uses unique hex IDs
    // for each account, so we can be certain that the name is the returned object will be
    // correct.
    public UTLCollaborator getCollaborator(String remoteID)
    {
    	Cursor c = this.queryCollaborators("remote_id='"+
    		Util.makeSafeForDatabase(remoteID)+"'",null);
    	if (c!=null && c.getCount()>0)
    	{
    		c.moveToFirst();
    		return cursorToCollaborator(c);
    	}
    	else
    		return null;
    }
    
    // Query for a list of collaborators. Returns a cursor:
    public Cursor queryCollaborators(String sqlWhere, String sqlOrderBy)
    {
    	return Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
    }
}
