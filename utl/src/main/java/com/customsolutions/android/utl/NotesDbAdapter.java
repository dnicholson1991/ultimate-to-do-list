package com.customsolutions.android.utl;

// Database interface for Toodledo Notes

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class NotesDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "notes";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "td_id integer, "+
        "account_id integer not null, "+
        "mod_date integer not null, "+
        "title text not null, "+
        "folder_id integer, "+
        "note text not null, "+
        "sync_date integer"+
        ")";
    
    public static final String[] INDEXES = {
    	"create index if not exists td_id_index on notes(td_id)",
    	"create index if not exists account_id_index on notes(account_id)",
    	"create index if not exists folder_id_index on notes(folder_id)",
    };

    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","td_id","account_id","mod_date",
        "title","folder_id","note","sync_date"
    };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public NotesDbAdapter() throws SQLException
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
    
    // Insert a note into the database.  If successful, the ID of the note is returned,
    // else -1.  This method automatically fills in the mod_date field,
    // and also updates the _id field for the note object passed in.
    public long addNote(UTLNote note)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", note.td_id);
        values.put("account_id", note.account_id);
        values.put("title", note.title);
        values.put("folder_id",note.folder_id);
        values.put("note", note.note);
        if (note.mod_date==0)
        {
        	values.put("mod_date", System.currentTimeMillis());
        }
        else
        {
        	values.put("mod_date", note.mod_date);
        }
        values.put("sync_date",note.sync_date);
        
        Util.checkDatabaseLock(Util.db());
        long id = Util.db().insert(TABLE, null, values);
        if (id==-1)
        {
        	return -1;
        }
        else
        {
        	note._id = id;
        	return id;
        }
    }
    
    // Delete a note.  Returns true on success, else false.
    public boolean deleteNote(long id)
    {
        Util.checkDatabaseLock(Util.db());
        return Util.db().delete(TABLE, "_id="+id, null) > 0;
    }
    
    public boolean deleteNote(long accountID, long TDID)
    {
        Util.checkDatabaseLock(Util.db());
    	return Util.db().delete(TABLE, "account_id="+accountID+" and td_id="+TDID, null) > 0;
    }
    
    // Modify a note.  Returns true if updated, false otherwise.
    // This does not automatically fill in any fields.
    public boolean modifyNote(UTLNote note)
    {
        ContentValues values = new ContentValues();
        values.put("td_id", note.td_id);
        values.put("account_id", note.account_id);
        values.put("title", note.title);
        values.put("folder_id",note.folder_id);
        values.put("note", note.note);
        values.put("mod_date", note.mod_date);
        values.put("sync_date", note.sync_date);
        
        Util.checkDatabaseLock(Util.db());
        return Util.db().update(TABLE, values, "_id="+note._id, null) > 0;
    }
    
    // Get a specific note.  Returns a UTLNote object, or null if the note cannot be found.
    public UTLNote getNote(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        if (c.moveToFirst())
        	return cursorToUTLNote(c);
        else
        	return null;
    }
    
    // Given a Toodledo account and note ID, get the note information.
    // Returns a UTLNote object, or null if the note cannot be found.
    public UTLNote getNote(long accountID, long TDID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "account_id="+accountID+" and td_id="+TDID,
                null, null, null, null);
        if (c.moveToFirst())
        	return cursorToUTLNote(c);
        else
        	return null;
    }
    
    // Get notes that exist in a specific folder.  The notes will be sorted by title.
    // Returns a cursor:
    public Cursor getNotesInFolder(long folderID)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "folder_id="+folderID, null, null, null,
            "lower(title)");
        return c;
    }
    
    // A general notes query.  Returns a Cursor:
    public Cursor queryNotes(String sqlWhere, String sqlOrderBy)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, sqlWhere, null, null, null, sqlOrderBy);
        return c;
    }

    // Given a database cursor, get a UTL Note object:
    public UTLNote cursorToUTLNote(Cursor c)
    {
    	UTLNote note = new UTLNote();
    	note._id = c.getLong(0);
    	note.td_id = c.getLong(1);
    	note.account_id = c.getLong(2);
    	note.mod_date = c.getLong(3);
    	note.title = c.getString(4);
    	note.folder_id = c.getLong(5);
    	note.note = c.getString(6);
    	note.sync_date = c.getLong(7);
    	return note;
    }
}
