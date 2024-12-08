package com.customsolutions.android.utl;

// This activity handles modification of account settings.  The caller must pass in a 
// Bundle with the following inputs:
// account_id: The ID of the account to edit.

// Nothing is returned to the caller.

import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckedTextView;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.services.tasks.TasksScopes;

import androidx.annotation.NonNull;

public class AccountOps extends UtlPopupActivity
{
	// Codes for activities that return a result back:
	private static final int RENAME = 2;
	
	private AccountsDbAdapter _accountsDB;
    private long _accountID;
	private UTLAccount _account;
	private ProgressDialog _progressDialog;
	
    private Intent _nextActivityIntent;

	/** Holds the number of tasks stored on the device. */
	private int _numTasks;

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
        Util.log("Started editing account settings.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.account_ops);
        
        // Verify the required account ID was passed in:
        Bundle b = this.getIntent().getExtras();
        if (b==null)
        {
        	Util.log("Null Bundle passed into AccountOps.java.");
        	finish();
        	return;
        }
        if (!b.containsKey("account_id"))
        {
        	Util.log("Missing account_id in AccountOps.java.");
        	finish();
        	return;
        }
        _accountsDB = new AccountsDbAdapter();
        _accountID = b.getLong("account_id");
        refreshData();
        
        //
        // Button Handlers:
        //
        
        // Show Timed Reminders:
        this.findViewById(R.id.account_ops_show_reminders).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				_account.enable_alarms = _account.enable_alarms ? false: true;
				_accountsDB.modifyAccount(_account);
				refreshData();
			}
		});
        
        // Show Location Reminders:
        this.findViewById(R.id.account_ops_show_loc_reminders).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				_account.enable_loc_alarms = _account.enable_loc_alarms ? false: true;
				_accountsDB.modifyAccount(_account);
				refreshData();
			}
		});
        
        // Change username/password:
        this.findViewById(R.id.account_ops_change_login).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		if (Synchronizer.isSyncing())
        		{
        			displayBlockMsg();
        			return;
        		}
			}
		});
        
        // Rename account:
        this.findViewById(R.id.account_ops_rename).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		Intent i = new Intent(AccountOps.this,GetAccountName.class);
        		i.putExtra("mode", GetAccountName.ACCOUNT_MODIFY);
        		AccountOps.this.startActivityForResult(i, RENAME);
			}
		});

        // Sign in Again:
        this.findViewById(R.id.account_ops_sign_in).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // This is only applicable for Toodledo accounts. Google accounts should never
                // need to be signed into again.
                if (_account.sync_service==UTLAccount.SYNC_TOODLEDO)
                {
                    if (Synchronizer.isSyncing())
                    {
                        displayBlockMsg();
                        return;
                    }

                    Intent i = new Intent(AccountOps.this,ToodledoLoginInfoV3.class);
                    i.putExtra("mode",ToodledoLoginInfoV3.SIGN_IN);
                    i.putExtra("account_id",_account._id);
                    AccountOps.this.startActivity(i);
                }
            }
        });

        // Reset synchronization:
        this.findViewById(R.id.account_ops_reset_sync).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		if (Synchronizer.isSyncing())
        		{
        			displayBlockMsg();
        			return;
        		}
        		
        		// Handlers for the "yes" and "no" options on the confirmation dialog:
        		DialogInterface.OnClickListener dialogClickListener = new 
        			DialogInterface.OnClickListener() 
                {
        			@Override
                    public void onClick(DialogInterface dialog, int which) 
                    {
        				switch (which)
        				{
        				case DialogInterface.BUTTON_POSITIVE:
                            // Yes clicked:
        					AccountOps.this.resetSync();
        				}
                    }
                };
                
                // Create and show the confirmation dialog:
                AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
                if (_account.sync_service==UTLAccount.SYNC_TOODLEDO)
                	builder.setMessage(R.string.Reset_Sync_Confirmation);
                else
                	builder.setMessage(R.string.Reset_Sync_Confirmation2);
                builder.setTitle(Util.getString(R.string.Reset_Sync));
                builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
                builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
                builder.show();
			}
		});

        // Link to a Toodledo Account:
        this.findViewById(R.id.account_ops_link_to_td).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@SuppressLint("NewApi")
			@Override
			public void onClick(View v)
			{
        		_progressDialog = ProgressDialog.show(AccountOps.this, null, Util.getString(R.
        			string.Checking_for_compatibility),false);
        		if (Build.VERSION.SDK_INT >= 11)
        			new LinkToToodledo().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        		else
        			new LinkToToodledo().execute();
			}
		});
        
        // Unlink from a Toodledo Account:
        this.findViewById(R.id.account_ops_unlink_from_td).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		if (Synchronizer.isSyncing())
        		{
        			displayBlockMsg();
        			return;
        		}
        		
        		// Handler for the "yes" option on the confirmation dialog:
        		DialogInterface.OnClickListener dialogClickListener = new 
        			DialogInterface.OnClickListener() 
                {
        			@Override
                    public void onClick(DialogInterface dialog, int which) 
                    {
        				switch (which)
        				{
        				case DialogInterface.BUTTON_POSITIVE:
                            // Yes clicked:
        			    	_account.td_email = "";
        					_account.td_password = "";
        					_account.td_userid = "";
        					_account.last_sync = 0;
        					_account.token_expiry = 0;
        					_account.current_key = "";
        					_account.pro = false;
        					_account.current_token = "";
        					_account.sync_service = UTLAccount.SYNC_NONE;
        					if (!_accountsDB.modifyAccount(_account))
        					{
        						Util.popup(AccountOps.this, R.string.DbModifyFailed);
        						return;
        					}
        					
        					// Tell Synchronizer that the account is no longer linked:
        		        	Intent i = new Intent(AccountOps.this, Synchronizer.class);
        		            i.putExtra("command", "unlink_account");
        		            i.putExtra("account_id", _account._id);
        		            Synchronizer.enqueueWork(AccountOps.this,i);

        		            // Set the Toodledo ID of all tasks, folders, etc. to -1:
        					SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
        					db.execSQL("update tasks set td_id=-1, sync_date=0 where account_id="+
        						_account._id);
        					db.execSQL("update notes set td_id=-1 where account_id="+
        						_account._id);
        					db.execSQL("update folders set td_id=-1 where account_id="+
        						_account._id);
        					db.execSQL("update contexts set td_id=-1 where account_id="+
        						_account._id);
        					db.execSQL("update goals set td_id=-1 where account_id="+
        						_account._id);
        					db.execSQL("update locations set td_id=-1 where account_id="+
        						_account._id);
        					db.execSQL("delete from pending_deletes where account_id="+
        						_account._id);
    						Util.popup(AccountOps.this, R.string.Update_Successful);
    						refreshData();
        				}
                    }
                };
                
                // Create and show the confirmation dialog:
                AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
                builder.setMessage(R.string.Unlink_TD_Confirmation);
                builder.setTitle(Util.getString(R.string.Unlink_From_TD));
                builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
                builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
                builder.show();
			}
        	
		});
        
        // Delete an Account:
        this.findViewById(R.id.account_ops_delete).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		if (Synchronizer.isSyncing())
        		{
        			displayBlockMsg();
        			return;
        		}
        		
        		// Handler for the "yes" option on the confirmation dialog:
        		DialogInterface.OnClickListener dialogClickListener = new 
        			DialogInterface.OnClickListener() 
                {
        			@Override
                    public void onClick(DialogInterface dialog, int which) 
                    {
        				switch (which)
        				{
        				case DialogInterface.BUTTON_POSITIVE:
                            // Yes clicked:

							// If this is a Google account, log the user out.
							if (_account.sync_service==UTLAccount.SYNC_GOOGLE &&
								_account.protocol==GTasksInterface.PROTOCOL_DEVICE_ACCOUNT)
							{
								googleLogOut();
							}

        					if (Util.settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        					{
        						// Get rid of any linked calendar events:
        						new CalendarInterface(AccountOps.this).deleteAllCalendarEvents(
        							_account);
        					}
        			    	
        			    	// Delete all tasks, notes, etc:
        			    	SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
        			    	Cursor c = (new TasksDbAdapter()).queryTasks("account_id="+
        			    		_account._id, null);
        			    	c.moveToPosition(-1);
        			    	while (c.moveToNext())
        			    	{
        			    		db.execSQL("delete from tags where utl_id="+Util.
        			    			cLong(c, "_id"));
        			    	}
        			    	c.close();
        			    	db.execSQL("delete from tasks where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from notes where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from folders where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from contexts where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from goals where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from locations where account_id="+
        			    		_account._id);
        			    	db.execSQL("delete from pending_deletes where account_id="+
        			    		_account._id);
        			    	
        			    	// Delete the account itself, and end this activity:
        			    	_accountsDB.deleteAccount(_account._id);
        			    	
        			    	// If the account is the default account, then update the
        			    	// default account:
        			    	if (Util.settings.contains("default_account"))
        			    	{
        			    		if (_account._id==Util.settings.getLong("default_account",
        			    			0))
        			    		{
        			    			// The default account was just deleted. Replace
        			    			// it with the first account.
        			    			c = _accountsDB.getAllAccounts();
        			            	if (c.moveToFirst())
        			            	{
        			            		UTLAccount a = _accountsDB.getUTLAccount(c);
        			            		Util.updatePref("default_account", a._id);
        			            	}
        			            	c.close();
        			    		}
        			    	}
        			    	
    						Util.popup(AccountOps.this, R.string.Update_Successful);
    						finish();
        				}
                    }
                };
                
                // Verify that we're not deleting the last account:
                Cursor c = _accountsDB.getAllAccounts();
                if (c.getCount()==1)
                {
                	c.close();
                	Util.popup(AccountOps.this, R.string.Last_Account_Delete);	
                	return;
                }
                c.close();
                
                // Create and show the confirmation dialog:
                AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
                if (_account.sync_service==UTLAccount.SYNC_TOODLEDO)
                	builder.setMessage(R.string.Account_Delete_Confirmation2);
                else if (_account.sync_service==UTLAccount.SYNC_GOOGLE)
                	builder.setMessage(R.string.Account_Delete_Confirmation3);
                else
                	builder.setMessage(R.string.Account_Delete_Confirmation);
                builder.setTitle(Util.getString(R.string.Delete_Account));
                builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
                builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
                builder.show();
			}
		});
        
        // Link to a Google Account:
        this.findViewById(R.id.account_ops_link_to_google).setOnClickListener(new View.
        	OnClickListener()
		{
			@SuppressLint("NewApi")
			@Override
			public void onClick(View v)
			{
				_progressDialog = ProgressDialog.show(AccountOps.this, null, Util.getString(R.
        			string.Checking_for_compatibility),false);
				if (Build.VERSION.SDK_INT >= 11)
					new LinkToGoogle().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				else
					new LinkToGoogle().execute();
			}
		});
        
        // Unlink from a Google Account:
        this.findViewById(R.id.account_ops_unlink_from_google).setOnClickListener(new 
        	View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				if (Synchronizer.isSyncing())
        		{
        			displayBlockMsg();
        			return;
        		}
				
				// Handler for the "yes" option on the confirmation dialog:
        		DialogInterface.OnClickListener dialogClickListener = new 
        			DialogInterface.OnClickListener() 
                {
        			@Override
                    public void onClick(DialogInterface dialog, int which) 
                    {
        				switch (which)
        				{
        				case DialogInterface.BUTTON_POSITIVE:
        					// Yes clicked:

							if (_account.protocol==GTasksInterface.PROTOCOL_DEVICE_ACCOUNT)
							{
								// Sign the user out:
								googleLogOut();
							}

        					// Update the account record:
        					_account.last_sync = 0;
        					_account.token_expiry = 0;
        					_account.current_key = "";
        					_account.current_token = "";
        					_account.sync_service = UTLAccount.SYNC_NONE;
        					_account.username = "";
        					_account.password = "";
                            _account.refresh_token = "";
                            _account.protocol = 0;
        					if (!_accountsDB.modifyAccount(_account))
        					{
        						Util.popup(AccountOps.this, R.string.DbModifyFailed);
        						return;
        					}
        					
        					// Tell Synchronizer that the account is no longer linked:
        		        	Intent i = new Intent(AccountOps.this, Synchronizer.class);
        		            i.putExtra("command", "unlink_account");
        		            i.putExtra("account_id", _account._id);
        		            Synchronizer.enqueueWork(AccountOps.this,i);
        		            
        		            // Set the remote ID of all tasks and folders to an empty string:
        		            SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
        		            db.execSQL("update tasks set remote_id='', sync_date=0 where account_id="+
        		            	_account._id);
        		            db.execSQL("update folders set remote_id='', sync_date=0 where account_id="+
        		            	_account._id);
        		            Util.popup(AccountOps.this, R.string.Update_Successful);
    						refreshData();
        				}
                    }
                };
                
                // Create and show the confirmation dialog:
                AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
                builder.setMessage(R.string.Unlink_GT_Confirmation);
                builder.setTitle(Util.getString(R.string.Unlink_From_GT));
                builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
                builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
                builder.show();
			}
		});
        
        // Show collaborators:
        this.findViewById(R.id.account_ops_view_collaborators).setOnClickListener(new 
        	View.OnClickListener()
		{
        	@Override
			public void onClick(View v)
			{
        		Intent i = new Intent(AccountOps.this,CollaboratorsList.class);
        		i.putExtra("account_id", _account._id);
        		AccountOps.this.startActivity(i);
			}
		});
    }
    
    private void refreshData()
    {
        _account = _accountsDB.getAccount(_accountID);
        if (_account==null)
        {
            Util.log("Invalid account ID ("+_accountID+") passed into "+
                "AccountOps.java.");
            finish();
            return;
        }

        // Set the title of this screen:
        getSupportActionBar().setTitle(Util.getString(R.string.Settings_for_Account)+" \""+_account.name+
        	"\"");
        
        // Set the icon at the top:
        if (_account.sync_service==UTLAccount.SYNC_GOOGLE)
        	getSupportActionBar().setIcon(R.drawable.google_logo);
        if (_account.sync_service==UTLAccount.SYNC_TOODLEDO)
        	getSupportActionBar().setIcon(R.drawable.toodledo_logo);
        
        // Set the checkbox for enabling reminders:
        CheckedTextView ctv = (CheckedTextView)this.findViewById(R.id.
        	account_ops_show_reminders);
        ctv.setChecked(_account.enable_alarms);
        ctv = (CheckedTextView)this.findViewById(R.id.account_ops_show_loc_reminders);
        ctv.setChecked(_account.enable_loc_alarms);

        TextView status = (TextView)findViewById(R.id.account_ops_status_txt);
        
        // Hide and show views based on account type:
        if (_account.sync_service==UTLAccount.SYNC_NONE)
        {
        	status.setText(Util.getString(R.string.Account_Status_No_Sync));
        	findViewById(R.id.account_ops_change_login).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_change_login_separator).setVisibility(View.GONE);
            findViewById(R.id.account_ops_sign_in).setVisibility(View.GONE);
            findViewById(R.id.account_ops_sign_in_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_reset_sync).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_reset_sync_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_td).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_link_to_td_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_unlink_from_td).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_td_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_google).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_link_to_google_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_unlink_from_google).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_google_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_view_collaborators).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_view_collaborators_separator).setVisibility(View.GONE);
        }
        else if (_account.sync_service==UTLAccount.SYNC_TOODLEDO)
        {
        	// Display the last sync time:
        	// If the current time zone is different than the home time zone, the
        	// displayed time needs to be offset.  The home time zone is the time zone
        	// that was in effect when the app was installed.
        	TimeZone currentTimeZone = TimeZone.getDefault();
        	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
        		"home_time_zone", "America/Los_Angeles"));
        	long difference = currentTimeZone.getOffset(_account.last_sync) - 
        		defaultTimeZone.getOffset(_account.last_sync);        	
        	status.setText(Util.getString(R.string.Account_Status_Toodledo)+" "+
        		_account.td_email+"\n"+Util.getString(R.string.Last_Full_Sync)+" "+
        		Util.getDateTimeString(_account.last_sync + difference));
        	
        	findViewById(R.id.account_ops_change_login).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_change_login_separator).setVisibility(View.GONE);
            findViewById(R.id.account_ops_sign_in).setVisibility(View.VISIBLE);
            findViewById(R.id.account_ops_sign_in_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_reset_sync).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_reset_sync_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_link_to_td).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_td_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_td).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_unlink_from_td_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_link_to_google).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_google_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_google).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_google_separator).setVisibility(View.GONE);
        	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        	{
	        	findViewById(R.id.account_ops_view_collaborators).setVisibility(View.VISIBLE);
	        	findViewById(R.id.account_ops_view_collaborators_separator).setVisibility(View.VISIBLE);
        	}
        	else
        	{
        		findViewById(R.id.account_ops_view_collaborators).setVisibility(View.GONE);
            	findViewById(R.id.account_ops_view_collaborators_separator).setVisibility(View.GONE);
        	}
        }
        else
        {
        	// It must link to Google:
        	status.setText(Util.getString(R.string.Account_Status_Google)+" "+
        		_account.username);
        	if (usingLoginMethod())
        	{
        		findViewById(R.id.account_ops_change_login).setVisibility(View.VISIBLE);
        		findViewById(R.id.account_ops_change_login_separator).setVisibility(View.VISIBLE);
        	}
        	else
        	{
        		findViewById(R.id.account_ops_change_login).setVisibility(View.GONE);
        		findViewById(R.id.account_ops_change_login_separator).setVisibility(View.GONE);
        	}
            findViewById(R.id.account_ops_sign_in).setVisibility(View.GONE);
            findViewById(R.id.account_ops_sign_in_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_reset_sync).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_reset_sync_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_link_to_td).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_td_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_td).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_td_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_google).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_link_to_google_separator).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_unlink_from_google).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_unlink_from_google_separator).setVisibility(View.VISIBLE);
        	findViewById(R.id.account_ops_view_collaborators).setVisibility(View.GONE);
        	findViewById(R.id.account_ops_view_collaborators_separator).setVisibility(View.GONE);
        }
    }
    
    // Handlers for activity results:
    @SuppressLint("NewApi")
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
    	super.onActivityResult(requestCode, resultCode, intent);
    	
    	if (resultCode==RESULT_CANCELED)
    	{
    		// Activity canceled.  Nothing to do:
    		return;
    	}
    	
    	// Get extras in the response, if any:
        Bundle extras = new Bundle();
        if (intent != null)
        {
            extras = intent.getExtras();
        }
        
        switch(requestCode)
        {
        case RENAME:
        	if (extras.containsKey("name") && extras.getString("name").length()>0)
        	{
        		_account.name = extras.getString("name");
        		_accountsDB.modifyAccount(_account);
        		Util.popup(this, R.string.Update_Successful);
        		refreshData();
        	}
        }
    }
    
    // Reset the synchronization for the account:
    private void resetSync()
    {
    	if (Util.settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    	{
    		// Get rid of any linked calendar events:
    		new CalendarInterface(this).deleteAllCalendarEvents(_account);
    	}
    	
    	// Get a handle into the database:
    	SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
    	
    	// Delete all tags for all tasks in this account:
    	Cursor c = (new TasksDbAdapter()).queryTasks("account_id="+_account._id, null);
    	c.moveToPosition(-1);
    	while (c.moveToNext())
    	{
    		db.execSQL("delete from tags where utl_id="+Util.cLong(c, "_id"));
    	}
    	c.close();
    	
    	// Delete all tasks for this account:
    	db.execSQL("delete from tasks where account_id="+_account._id);
    	
    	// Delete all notes for this account:
    	db.execSQL("delete from notes where account_id="+_account._id);
    	
    	// Delete folders, contexts, and goals:
    	db.execSQL("delete from folders where account_id="+_account._id);
    	db.execSQL("delete from contexts where account_id="+_account._id);
    	db.execSQL("delete from goals where account_id="+_account._id);
    	db.execSQL("delete from locations where account_id="+_account._id);
    	
    	// Delete any pending deletes:
    	db.execSQL("delete from pending_deletes where account_id="+_account._id);
    	
    	// Update the account to trigger a download of everything:
    	_account.etag = "";
    	_account.last_sync = 0;
    	_accountsDB.modifyAccount(_account);
    	
    	// Tell the synchronizer to start a manual sync:
    	Intent i = new Intent(this, Synchronizer.class);
        i.putExtra("command", "full_sync");
        Synchronizer.enqueueWork(this,i);
        
        // Notify the user what is happening next:
        Util.popup(this,R.string.Task_background_download);
    }

    // If we return here after leaving, we must refresh the data:
    @Override
    public void onResume()
    {
        super.onResume();
        refreshData();
    }

    // Called when the user changes orientation
    // We don't want to do anything if the user is in the middle of an account reset.
    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
    	super.onConfigurationChanged(newConfig);
    	return;
    }
    
    // Display a message telling the user that the operation cannot be formed because a
    // sync is in progress.
    private void displayBlockMsg()
    {
		// Handler for pressing the OK button in the dialog (nothing to do):
		DialogInterface.OnClickListener dialogClickListener = new 
			DialogInterface.OnClickListener() 
        {
			@Override
            public void onClick(DialogInterface dialog, int which) 
            {
            }
        };
        
        // Create and show the message dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
        builder.setMessage(R.string.Blocked_due_to_Sync);
        builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
        builder.show();
    }
    
    // An AsyncTask which checks an account for Toodledo compatibility and then starts the
    // linking process if it is:
    private class LinkToToodledo extends AsyncTask<Void,Void,String>
    {
    	@Override
    	protected void onPreExecute()
    	{
    		AccountOps.this.lockScreenOrientation();
    	}
    	
    	protected String doInBackground(Void... v)
    	{
    		// In order to link to Toodledo, the account cannot have any subtasks within
    		// subtasks.
    		TasksDbAdapter tasksDB = new TasksDbAdapter();
    		Cursor c = tasksDB.queryTasks("parent_id>0 and account_id="+_account._id, 
    			null);
    		c.moveToPosition(-1);
    		while (c.moveToNext())
    		{
    			UTLTask t = tasksDB.getTask(Util.cLong(c,"parent_id"));
    			if (t!=null && t.parent_id>0)
    			{
    				String title = Util.cString(c,"title");
    				c.close();
    				return title;
    			}
    		}
    		c.close();

			// No issues found so far.  Get the number of the tasks to display in the warning
			// message.
			c = Util.db().rawQuery("select count(*) from tasks where account_id=?",new String[] {
				Long.valueOf(_accountID).toString() });
			_numTasks = 0;
			if (c.moveToFirst())
			{
				_numTasks = c.getInt(0);
			}
			c.close();

    		return "";
    	}
    	
    	protected void onPostExecute(String conflictingTask)
    	{
    		if (_progressDialog.isShowing())
    			_progressDialog.dismiss();
    		
    		AccountOps.this.unlockScreenOrientation();
    		
    		if (conflictingTask.length()==0)
    		{
				// Display a warning message if necessary to make sure the user knows what will
				// happen.
				if (_numTasks>1)
				{
					String warning = getString(R.string.merge_warning).replace("[count]",
						Integer.valueOf(_numTasks).toString());
					new AlertDialog.Builder(AccountOps.this)
						.setTitle(R.string.warning)
						.setMessage(warning)
						.setNegativeButton(R.string.No,null)
						.setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								// Create an Intent that will launch the activity for logging into
								// Toodledo:
								Intent i = new Intent(AccountOps.this,ToodledoLoginInfoV3.class);
								i.putExtra("mode",ToodledoLoginInfoV3.MERGE);
								i.putExtra("account_id", _account._id);
								_nextActivityIntent = i;
								confirmAccountSetup();
							}
						})
						.show();
				}
				else
				{
					// Create an Intent that will launch the activity for logging into
					// Toodledo:
					Intent i = new Intent(AccountOps.this,ToodledoLoginInfoV3.class);
					i.putExtra("mode",ToodledoLoginInfoV3.MERGE);
					i.putExtra("account_id", _account._id);
					_nextActivityIntent = i;
					confirmAccountSetup();
				}
    		}
    		else
    		{
    			Util.longerPopup(AccountOps.this, "", Util.getString(R.string.
    				Toodledo_conflict)+" "+conflictingTask);
    		}
    	}
    }

    // An AsyncTask which checks an account for Google compatiblity and starts the linking
    // process if it is:
    private class LinkToGoogle extends AsyncTask<Void,Void,String>
    {
    	@Override
    	protected void onPreExecute()
    	{
    		AccountOps.this.lockScreenOrientation();
    	}
    	
    	protected String doInBackground(Void... v)
    	{
    		// In order to link to Google, the following conditions must be met:
    		// - All tasks have a folder assigned.
    		// - All subtasks are in the same folder as their parent.
			// - There are no subtasks within subtasks (after 8/30/2019)
    		
    		TasksDbAdapter tasksDB = new TasksDbAdapter();
    		Cursor c = tasksDB.queryTasks("folder_id=0 and account_id="+_account._id, 
    			null);
    		if (c.moveToFirst())
    		{
    			String title = Util.cString(c,"title");
    			c.close();
    			return "f:"+title;
    		}
    		c.close();
    		
    		c = tasksDB.queryTasks("parent_id>0 and account_id="+_account._id, 
    			null);
    		c.moveToPosition(-1);
    		while (c.moveToNext())
    		{
    			long childFolderID = Util.cLong(c,"folder_id");
    			UTLTask parent = tasksDB.getTask(Util.cLong(c,"parent_id"));
    			if (parent!=null && parent.folder_id!=childFolderID)
    			{
    				String title = Util.cString(c,"title");
    				c.close();
    				return "s:"+title;
    			}
    			if (parent!=null && parent.parent_id>0 && System.currentTimeMillis()>Util.
					GOOGLE_SUB_SUB_TASKS_EXPIRY)
				{
					String title = Util.cString(c,"title");
					c.close();
					return "p:"+title;
				}
    		}
    		c.close();

			// No issues found so far.  Get the number of the tasks to display in the warning
			// message.
			c = Util.db().rawQuery("select count(*) from tasks where account_id=?",new String[] {
				Long.valueOf(_accountID).toString() });
			_numTasks = 0;
			if (c.moveToFirst())
			{
				_numTasks = c.getInt(0);
			}
			c.close();

    		return "";
    	}
    	
    	protected void onPostExecute(String conflictingTask)
    	{
    		if (_progressDialog.isShowing())
    			_progressDialog.dismiss();
    		AccountOps.this.unlockScreenOrientation();
    		
    		if (conflictingTask.length()==0)
    		{
				// Display a warning message if necessary to make sure the user knows what will
				// happen.
				if (_numTasks>1)
				{
					String warning = getString(R.string.merge_warning).replace("[count]",
						Integer.valueOf(_numTasks).toString());
					new AlertDialog.Builder(AccountOps.this)
						.setTitle(R.string.warning)
						.setMessage(warning)
						.setNegativeButton(R.string.No,null)
						.setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener()
						{
							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								// Create an Intent that will launch the activity for logging into
								// Google:
								_nextActivityIntent = new Intent(AccountOps.this,GTasksSetup.class);
								_nextActivityIntent.putExtra("sync_mode", GTasksSetup.MERGE);
								_nextActivityIntent.putExtra("account_id", _account._id);
								confirmAccountSetup();
							}
						})
						.show();
				}
				else
				{
					// Create an Intent that will launch the activity for logging into
					// Google:
					_nextActivityIntent = new Intent(AccountOps.this,GTasksSetup.class);
					_nextActivityIntent.putExtra("sync_mode", GTasksSetup.MERGE);
					_nextActivityIntent.putExtra("account_id", _account._id);
					confirmAccountSetup();
				}
    		}
    		else
    		{
    			String type = conflictingTask.substring(0, 2);
    			String taskName = conflictingTask.substring(2);
    			if (type.equals("f:"))
    			{
    				// Folder issue:
    				Util.longerPopup(AccountOps.this, "", Util.getString(R.string.
    					Google_conflict_1)+" "+taskName);
    			}
    			else if (type.equals("p:"))
				{
					// An issue with the existence of a sub-sub-task.
					Util.longerPopup(AccountOps.this, "", Util.getString(R.string.
						google_tasks_conflict)+" "+taskName);
				}
    			else
    			{
    				// Parent/child task issue:
    				Util.longerPopup(AccountOps.this, "", Util.getString(R.string.
    					Google_conflict_2)+" "+taskName);
    			}
    		}
    	}
    }
    
    // Determine if the user is using Google's ClientLogin method (entering a username
    // and password instead of selecting a system account).
    private boolean usingLoginMethod()
    {
    	// 7/23/19: No longer supported.
    	return false;
    }
    
    // Confirm that the user wants to link to an account if new calendar entries are 
    // being automatically added. We need to warn the user that this could create a large
    // number of entries.
    private void confirmAccountSetup()
    {
    	if (!Util.settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false) ||
    		!Util.settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    	{
    		this.startActivity(_nextActivityIntent);
    		return;
    	}
    	
    	// Handler for the "yes" button in the confirmation dialog:
    	DialogInterface.OnClickListener dialogClickListener = new 
			DialogInterface.OnClickListener() 
	    {
			@Override
	        public void onClick(DialogInterface dialog, int which) 
	        {
				switch (which)
				{
				case DialogInterface.BUTTON_POSITIVE:
	                // Yes clicked:
					AccountOps.this.startActivity(_nextActivityIntent);
				}
	        }
	    };
	    
	    // Create and show the confirmation dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(AccountOps.this);
        builder.setMessage(R.string.Auto_Cal_Create_Warning);
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.show();
    }

    /** Log the user out of a Google account. */
    private void googleLogOut()
	{
		GoogleSignInClient client = Util.getGoogleSignInClient(AccountOps.this,
			_account.username);
		client.revokeAccess()
			.addOnSuccessListener(new OnSuccessListener<Void>()
			{
				@Override
				public void onSuccess(Void aVoid)
				{
					Util.log("AccountOps: Successful sign out.");
				}
			})
			.addOnFailureListener(new OnFailureListener()
			{
				@Override
				public void onFailure(@NonNull Exception e)
				{
					Util.log("AccountOps: Unsuccessful sign out. "+
						e.getClass().getName()+" / "+e.getMessage());
				}
			});
	}

}
