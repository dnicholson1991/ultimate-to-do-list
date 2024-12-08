package com.customsolutions.android.utl;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

// This implements the global database helper object for the application:

public class DatabaseHelper extends SQLiteOpenHelper 
{
    public boolean isManualSortInitNeeded = false;

    DatabaseHelper(Context context) 
    {
    	// Change the version number (last argument) when database changes occur.
        super(context, "utl", null, 25);
		isManualSortInitNeeded = false;
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) 
    {
        // Don't do anything.  Tables are created as needed.
		isManualSortInitNeeded = false;
    }
    
    // Called when the database is upgraded:
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
		isManualSortInitNeeded = false;

        if (newVersion==3 && oldVersion==1)
        {
        	// Add indexes to the tasks table:
        	for (int i=0; i<TasksDbAdapter.INDEXES.length; i++)
        	{
        		db.execSQL(TasksDbAdapter.INDEXES[i]);
        	}
        	
        	// Add the sync date to folders, contexts, and goals:
        	db.execSQL("alter table folders add column sync_date integer");
        	db.execSQL("alter table contexts add column sync_date integer");
        	db.execSQL("alter table goals add column sync_date integer");
        	db.execSQL("alter table notes add column sync_date integer");
        	
        	// After we upgrade, we will sync everything, so set all sync_date fields to
        	// zero:
        	db.execSQL("update folders set sync_date=0");
        	db.execSQL("update contexts set sync_date=0");
        	db.execSQL("update goals set sync_date=0");
        	db.execSQL("update notes set sync_date=0");
        	
        	db.execSQL("alter table account_info add column enable_alarms integer");
        }
        if (newVersion==3 && oldVersion==2)
        {
        	db.execSQL("alter table account_info add column enable_alarms integer");
        }
        if (newVersion==4 && oldVersion==3)
        {
        	for (int i=0; i<NotesDbAdapter.INDEXES.length; i++)
        	{
        		db.execSQL(NotesDbAdapter.INDEXES[i]);
        	}        	
        }
        if (newVersion==5 && oldVersion==4)
        {
        	for (int i=0; i<ViewsDbAdapter.INDEXES.length; i++)
        	{
        		db.execSQL(ViewsDbAdapter.INDEXES[i]);
        	}
        	for (int i=0; i<ViewRulesDbAdapter.INDEXES.length; i++)
        	{
        		db.execSQL(ViewRulesDbAdapter.INDEXES[i]);
        	}
        	for (int i=0; i<TagsDbAdapter.INDEXES.length; i++)
        	{
        		db.execSQL(TagsDbAdapter.INDEXES[i]);
        	}
        }
        
        // UTL 1.4: Database changes to support locations:
        if (newVersion>=6 && oldVersion<6)
        {
        	// Add the location columns to tasks table:
        	db.execSQL("alter table tasks add column location_id integer");
        	db.execSQL("alter table tasks add column location_reminder integer");
        	db.execSQL("alter table tasks add column location_nag integer");
        	db.execSQL("alter table account_info add column enable_loc_alarms integer");
        	db.execSQL("update account_info set enable_loc_alarms=1");
        	
        	// Create the locations table:
        	LocationsDbAdapter.createTableAndIndexes(db);
        }
        
    	// Only for internal software release:
        if (newVersion==7 && oldVersion==6)
        {
        	db.execSQL("alter table account_info add column enable_loc_alarms integer");
        	db.execSQL("update account_info set enable_loc_alarms=1");
        }
        
        // UTL 1.5: Database changes to support Google tasks:
        if (newVersion>=8 && oldVersion<8)
        {
        	// Accounts Table - New Columns:
        	db.execSQL("alter table account_info add column sync_service integer");
        	db.execSQL("alter table account_info add column username text");
        	db.execSQL("alter table account_info add column password text");
        	db.execSQL("alter table account_info add column use_note_for_extras integer");
        	
        	// Accounts Table - Initialize Columns:
        	db.execSQL("update account_info set sync_service=0 where td_email is null");
        	db.execSQL("update account_info set sync_service=0 where td_email=''");
        	db.execSQL("update account_info set sync_service=1 where td_email is not null and td_email!=''");
        	db.execSQL("update account_info set username=''");
        	db.execSQL("update account_info set password=''");
        	db.execSQL("update account_info set use_note_for_extras=0");
        	
        	// Tasks table - new columns:
        	db.execSQL("alter table tasks add column remote_id text");
        	db.execSQL("alter table tasks add column position text");
        	db.execSQL("alter table tasks add column new_task_generated integer");
        	db.execSQL("alter table tasks add column prev_folder_id integer");
        	db.execSQL("alter table tasks add column prev_parent_id integer");
        	
        	// Tasks table - initialize columns:
        	db.execSQL("update tasks set remote_id=''");
        	db.execSQL("update tasks set position=''");
        	db.execSQL("update tasks set new_task_generated=0");
        	db.execSQL("update tasks set prev_folder_id=0");
        	db.execSQL("update tasks set prev_parent_id=0");
        	
        	// Tasks table - indexes:
        	db.execSQL("create index if not exists remote_id_index on tasks(remote_id)");
        	
        	// Folders table - new columns:
        	db.execSQL("alter table folders add column remote_id text");
        	
        	// Folders table - initialize columns:
        	db.execSQL("update folders set remote_id=''");
        	
        	// Pending Deletes table - new columns:
        	db.execSQL("alter table pending_deletes add column remote_id text");
        	db.execSQL("alter table pending_deletes add column remote_tasklist_id text");
        	
        	// Pending Deletes table - initialize columns:
        	db.execSQL("update pending_deletes set remote_id=''");
        	db.execSQL("update pending_deletes set remote_tasklist_id=''");
        }
        
        // UTL 1.6: Database changes to support reverse sorting:
        if (newVersion>=10 && oldVersion<10)
        {
        	db.execSQL("alter table views add column sort_order_string text");
        	db.execSQL("update views set sort_order_string=''");
        }
        
        // UTL 1.7: Database changes to support a linked contact:
        if (newVersion>=11 && oldVersion<11)
        {
        	db.execSQL("alter table tasks add column contact_lookup_key text");
        	db.execSQL("update tasks set contact_lookup_key=''");
        }
        
        // UTL 1.8: Calendar integration:
        if (newVersion>=12 && oldVersion<12)
        {
        	db.execSQL("alter table tasks add column cal_event_uri text");
        	db.execSQL("update tasks set cal_event_uri=''");
        }
        
        // UTL 1.8: Custom views on home page:
        if (newVersion>=13 && oldVersion<13)
        {
        	db.execSQL("alter table views add column on_home_page integer");
        	db.execSQL("update views set on_home_page=0");
        }
        
        // UTL 2.0: Collaboration:
        if (newVersion>=14 && oldVersion<14)
        {
        	// Create the new tables:
        	PendingReassignmentsDbAdapter.createTableAndIndexes(db);
        	CollaboratorsDbAdapter.createTableAndIndexes(db);
        	
        	// Add new fields to the folders table:
        	db.execSQL("alter table folders add column is_private integer");
        	db.execSQL("update folders set is_private=0");
        	
        	// Add new fields to the tasks table:
        	db.execSQL("alter table tasks add column is_joint integer");
        	db.execSQL("alter table tasks add column owner_remote_id text");
        	db.execSQL("alter table tasks add column shared_with text");
        	db.execSQL("alter table tasks add column added_by text");
        	db.execSQL("update tasks set is_joint=0");
        	db.execSQL("update tasks set owner_remote_id=''");
        	db.execSQL("update tasks set shared_with=''");
        	db.execSQL("update tasks set added_by=''");
        }
        
        // Version 3.0: The shared_with_changed field in tasks:
        if (newVersion>=15 && oldVersion<15)
        {
        	db.execSQL("alter table tasks add column shared_with_changed integer");
        	db.execSQL("update tasks set shared_with_changed=0");
        }
        
        // Version 3.0: The etag field for Google accounts:
        if (newVersion>=16 && oldVersion<16)
        {
        	db.execSQL("alter table account_info add column etag text");
        	db.execSQL("update account_info set etag=''");
        }

        // Version 3.1: The purchases table:
        if (newVersion>=17 && oldVersion<17)
        {
            PurchaseManager.createTable(db);
        }

        // Version 3.2: Google OAuth2 support for accounts not created in device settings.
        if (newVersion>=18 && oldVersion<18)
        {
            db.execSQL("alter table account_info add column refresh_token text");
            db.execSQL("alter table account_info add column protocol integer");
            db.execSQL("update account_info set refresh_token=''");
            db.execSQL("update account_info set protocol="+GTasksInterface.PROTOCOL_DEVICE_ACCOUNT+
                " where sync_service="+UTLAccount.SYNC_GOOGLE+" and password is null or password=''");
            db.execSQL("update account_info set protocol="+GTasksInterface.PROTOCOL_LOGIN_METHOD+
                " where sync_service="+UTLAccount.SYNC_GOOGLE+" and password is not null and length(password)>0");
        }

        // Version 3.2: Tracking of feature usage.
        if (newVersion>=19 && oldVersion<19)
        {
            FeatureUsage.createTable(db);
        }

        // Version 3.3: Switching to Toodledo's interface version 3.
        if (newVersion>=20 && oldVersion<20)
        {
            // Toodledo accounts cannot sync until the user signs in again.
            db.execSQL("alter table account_info add column sign_in_needed integer");
            db.execSQL("update account_info set sign_in_needed=1 where sync_service="+
                UTLAccount.SYNC_TOODLEDO);
            db.execSQL("update account_info set sign_in_needed=0 where sync_service!="+
                UTLAccount.SYNC_TOODLEDO);
        }

		// Version 3.5: Addition of manual sorting:
		if (newVersion>=21 && oldVersion<21)
		{
			db.execSQL("alter table tasks add column sort_order integer");
			db.execSQL("update tasks set sort_order=0");
			db.execSQL("alter table tasks add column is_moved integer");
			db.execSQL("update tasks set is_moved=0");
			db.execSQL("alter table tasks add column prev_task_id integer");
			db.execSQL("update tasks set prev_task_id=0");
			isManualSortInitNeeded = true;
		}


		// Version 4.0: Addition of the UUID field to tasks:
		if (newVersion>=23 && oldVersion<23)
		{
			db.execSQL("alter table tasks add column uuid text");
			db.execSQL("create index if not exists uuid_index on tasks(uuid)");
		}

		// Version 4.0: Additional logging data:
		if (newVersion>=25 && oldVersion<25)
		{
			// Remove the table.  It will be recreated later by the init function in Util.
			db.execSQL("drop table log_data");
		}
    }
}
