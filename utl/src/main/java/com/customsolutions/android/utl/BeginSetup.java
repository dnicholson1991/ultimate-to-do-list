package com.customsolutions.android.utl;

//Activity to begin account setup.

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.google.android.ump.ConsentInformation;

import java.io.File;
import java.util.UUID;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

public class BeginSetup extends UtlActivity 
{
	private static final String TAG = "BeginSetup";

    private boolean _isFirstAccount;
    
    private Intent _nextActivityIntent;
    
	private ProgressDialog _progressDialog;
	
	/** Used to get purchase status. */
	private PurchaseManager _pm;

	/** The URI of the backup file to restore. */
	private Uri _backupUri;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        // Log what we're doing:
        Log.v(TAG,"Begin new account setup");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.begin_setup);
        
        _pm = new PurchaseManager(this);

		for (String permission : Util.getAllPermissions())
		{
			int permissionCheck = ContextCompat.checkSelfPermission(this,permission);
			Util.log(permission+" status: "+permissionCheck);
		}

        // If we already have at least one account on the system, then adjust the message
        // at the top of the screen:
        Cursor c = (new AccountsDbAdapter()).getAllAccounts();
        _isFirstAccount = true;
        if (c.moveToFirst())
        {
        	// This is not the first time the app has run.  We will show the action bar and hide the 
        	// logo icon and app name.
            _isFirstAccount = false;
            getSupportActionBar().setTitle(R.string.Link_to_Another_Account);
            findViewById(R.id.welcome_logo_wrapper).setVisibility(View.GONE);
            findViewById(R.id.welcome_organize_simplify).setVisibility(View.GONE);
            
            // Change the welcome message:
            TextView tv = (TextView)this.findViewById(R.id.welcome_msg);
            tv.setText(Util.getString(R.string.Please_choose_sync_option));
        }
        else
        {
        	// This is the first time we have run.  Hide the Action Bar.
        	getSupportActionBar().hide();

        	// Show the option to restore from backup. We don't have the permissions we need
			// at this point, but they will be asked for if the user taps on the button.
			this.findViewById(R.id.welcome_restore).setVisibility(View.VISIBLE);
        }
        c.close();
        
        // Run a check to see if a license has been purchased in-app:
        new PurchaseManager(this).verifyLicensePurchase(false);

		initBannerAd();

		Util.logOneTimeEvent(BeginSetup.this,"view_account_types",0,null);

        //
        // Set the callback functions for buttons:
        //
        
        // Existing Google Account:
        this.findViewById(R.id.welcome_existing_gtasks).setOnClickListener(new 
        	View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				final Runnable nextActivityRunnable = new Runnable()
				{
					@Override
					public void run()
					{
						if (_isFirstAccount)
						{
							// For the first account, we skip the name screen and use a default name.
							_nextActivityIntent = new Intent(BeginSetup.this,GTasksSetup.class);
							_nextActivityIntent.putExtra("name", "Google");
							_nextActivityIntent.putExtra("sync_mode", GTasksSetup.INITIAL_SYNC);
							Util.logOneTimeEvent(BeginSetup.this,"account_type_selected",0,
								new String[] {"google"});
						}
						else
						{
							// Ask the user for a name on the 2nd and later accounts.
							_nextActivityIntent = new Intent(BeginSetup.this,GetAccountName.class);
							_nextActivityIntent.putExtra("mode", GetAccountName.ACCOUNT_SETUP);
							_nextActivityIntent.putExtra("sync_service", UTLAccount.SYNC_GOOGLE);
						}
						confirmAccountSetup();
					}
				};

				getPermissionsAndRun(new Runnable()
				{
					@Override
					public void run()
					{
						// In this case, we additionally need permission to read the accounts
						// on this device.
						requestPermissions(
							new String[] {Manifest.permission.GET_ACCOUNTS},
							nextActivityRunnable,
							true,
							getString(R.string.accounts_permission_required),
							null
						);
					}
				});
			}
		});
        
        // Toodledo account.
        findViewById(R.id.welcome_toodledo_account).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				getPermissionsAndRun(() -> {
					if (_isFirstAccount)
					{
						// For the first account, we skip the name screen and use a default name.
						_nextActivityIntent = new Intent(BeginSetup.this,ToodledoLoginInfoV3.class);
						_nextActivityIntent.putExtra("account_name", "Toodledo");
						_nextActivityIntent.putExtra("mode", ToodledoLoginInfoV3.NEW_UTL_ACCOUNT);
						Util.logOneTimeEvent(BeginSetup.this,"account_type_selected",0,
							new String[] {"toodledo"});
					}
					else
					{
						// Ask the user for a name on the 2nd and later accounts.
						_nextActivityIntent = new Intent(BeginSetup.this, GetAccountName.class);
						_nextActivityIntent.putExtra("mode", GetAccountName.ACCOUNT_SETUP);
						_nextActivityIntent.putExtra("td_status",GetAccountName.EXISTING_ACCOUNT);
					}
					confirmAccountSetup();
				});
			}
		});
        
        // No linked account:
        TextView noTDAccount = (TextView)findViewById(R.id.welcome_no_td);
        noTDAccount.setOnClickListener(new View.OnClickListener() 
        { 
            @Override
            public void onClick(View v) 
            {
            	getPermissionsAndRun(() -> {
					if (_isFirstAccount)
					{
						// Set up the account:
						UTLAccount a = new UTLAccount();
						a.name = getString(R.string.Task_List_on_Device);
						a.sync_service = UTLAccount.SYNC_NONE;
						long accountID = (new AccountsDbAdapter()).addAccount(a);
						if (accountID==-1)
						{
							Util.log("Could not insert new Account into Database.");
							Util.popup(BeginSetup.this, R.string.DbInsertFailed);
						}

						// Create a sample task if this is the first sync:
						SharedPreferences settings = BeginSetup.this.getSharedPreferences(Util.PREFS_NAME, 0);
						if (settings.getBoolean(PrefNames.CREATE_SAMPLE_TASK, false))
						{
							Util.updatePref(PrefNames.CREATE_SAMPLE_TASK, false, BeginSetup.this);
							UTLTask sample = new UTLTask();
							sample.account_id = accountID;
							sample.title = BeginSetup.this.getString(R.string.learn_more_about_utl);
							sample.priority = 5;
							sample.star = true;
							sample.note = BeginSetup.this.getString(R.string.First_Task_Note);
							sample.start_date = Util.getMidnight(System.currentTimeMillis());
							sample.uses_start_time = false;
							sample.due_date = Util.getMidnight(System.currentTimeMillis());
							sample.uses_due_time = false;
							sample.uuid = UUID.randomUUID().toString();
							(new TasksDbAdapter()).addTask(sample);
						}

						Util.logOneTimeEvent(BeginSetup.this,"account_type_selected",0,
							new String[] {"unsynced"});
						Util.logOneTimeEvent(BeginSetup.this, "account_setup", 0, new String[]
							{"unsynced"});

						// Go to the fields/function used screen:
						_nextActivityIntent = new Intent(BeginSetup.this,FeatureSelection.class);
						_nextActivityIntent.putExtra(FeatureSelection.EXTRA_INITIAL_SETUP, true);
						checkGdpr();
					}
					else
					{
						// Ask the user for a name on the 2nd and later accounts.
						_nextActivityIntent = new Intent(BeginSetup.this, GetAccountName.class);
						_nextActivityIntent.putExtra("mode", GetAccountName.ACCOUNT_SETUP);
						_nextActivityIntent.putExtra("td_status",GetAccountName.NONE);
						checkGdpr();
					}
				});
            }
        });
        
        // Restore From Backup:
        findViewById(R.id.welcome_restore).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				getPermissionsAndRun(() -> {
					// Handlers for the "yes" and "no" options on the confirmation dialog:
					DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
						switch (which)
						{
							case DialogInterface.BUTTON_POSITIVE:
								// Yes clicked:
								dialog.dismiss();
								Util.openBackupFilePicker(BeginSetup.this);
								return;
						}
					};

					// Create and show the confirmation dialog:
					AlertDialog.Builder builder = new AlertDialog.Builder(BeginSetup.this);
					builder.setMessage(R.string.Restore_Confirmation);
					builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
					builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
					builder.show();
				});
			}
		});
        
        // Help button:
        findViewById(R.id.welcome_help).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				Util.longerPopup(BeginSetup.this, getString(R.string.Which_Account_is_Right), 
					getString(R.string.Account_Choice_Help2));
			}
		});
    }

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		if (requestCode==Util.BACKUP_FILE_PICKER_CODE && resultCode== Activity.RESULT_OK &&
			intent.getData()!=null)
		{
			Log.v(TAG,"Got the following URI: "+intent.getData().toString());
			_backupUri = intent.getData();
			performRestore();
		}
		else
			super.onActivityResult(requestCode,resultCode,intent);
	}

    /** Request needed permissions for backup/restore, then execute the specified Runnable. */
    private void getPermissionsAndRun(final Runnable r)
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
		{
			// We don't need any additional permission to read and write to/from the downloads
			// directory.
			r.run();
			return;
		}

		// Ask for file access, which is required to continue.
		requestPermissions(
			new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE},
			() -> {
				if (Util.hasPermissionForBackup(this))
					_settings.edit().putBoolean(PrefNames.BACKUP_DAILY,true).apply();
				r.run();
			},
			true,
			getString(R.string.file_permission_reason),
			() -> {
				_settings.edit()
					.putBoolean(PrefNames.BACKUP_DAILY,false)
					.putBoolean(PrefNames.REJECTED_STORAGE_PERMISSION,true)
					.apply();
				r.run();
			});
	}

	/** Perform the restore from backup. */
	private void performRestore()
	{
		_progressDialog = ProgressDialog.show(BeginSetup.this, null,
			Util.getString(R.string.Restoring_Msg),false);
		new PerformRestore().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
	}

    // Confirm that the user wants to set up a new account if new calendar entries are 
    // being automatically added. We need to warn the user that this could create a large
    // number of entries.
    private void confirmAccountSetup()
    {
    	SharedPreferences settings = getSharedPreferences(Util.PREFS_NAME,0);
    	if (!settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false) ||
    		!settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    	{
    		checkGdpr();
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
					checkGdpr();
				}
	        }
	    };
	    
	    // Create and show the confirmation dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(BeginSetup.this);
        builder.setMessage(R.string.Auto_Cal_Create_Warning);
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.show();
    }

    /** Check to see if the GDPR consent dialog needs to be shown, and show it if needed. */
    private void checkGdpr()
	{
		if (ConsentUtil.getConsentStatus()==ConsentInformation.ConsentStatus.REQUIRED &&
			_pm.stat()==PurchaseManager.SHOW_ADS)
		{
			// Launch the consent dialog, and move on when finished:
			ConsentUtil.showConsentForm(this,() -> {
				startActivity(_nextActivityIntent);
			});
		}
		else
		{
			// The consent dialog either failed to load or is not needed. Move on.
			startActivity(_nextActivityIntent);
		}
	}

    // An AsyncTask which performs the restore:
    private class PerformRestore extends AsyncTask<Void,Void,String>
    {
    	@Override
    	protected void onPreExecute()
    	{
    		BeginSetup.this.lockScreenOrientation();
    	}
    	
    	protected String doInBackground(Void... v)
    	{
    		return Util.restoreDataFromBackup(BeginSetup.this,_backupUri);
    	}
    	
    	protected void onPostExecute(String errorMsg)
    	{
    		if (_progressDialog!=null && _progressDialog.isShowing())
    			_progressDialog.dismiss();
    		BeginSetup.this.unlockScreenOrientation();
    		
    		if (errorMsg!=null && errorMsg.length()>0)
    		{
    			Util.longerPopup(BeginSetup.this, "", errorMsg);
    			return;
    		}
    		
    		// Display a message indicating success, and restart the app:
    		// Display a simple popup message letting the user know that
	        // the app needs to be restarted.
	        DialogInterface.OnClickListener dialogClickListener = new 
				DialogInterface.OnClickListener()
			{					
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					PackageManager pm = getPackageManager();
		    		Intent restartIntent = pm.getLaunchIntentForPackage(getPackageName());
		    		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
		    			restartIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		    		Util.setExactAlarm(BeginSetup.this,pi,System.currentTimeMillis()+1000);
		    		System.runFinalization();
		    		System.exit(2);
				}
			};
			AlertDialog.Builder builder = new AlertDialog.Builder(BeginSetup.this);
			if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
				builder.setMessage(R.string.restore_successful_android10);
			else
				builder.setMessage(R.string.Restore_Successful_permissions_issue);
			builder.setPositiveButton(Util.getString(R.string.OK), 
				dialogClickListener);
			builder.setCancelable(false);
			builder.show();		
    	}
    }

    @Override
	public void onPause()
	{
		Stats.uploadStats(this,null);
		super.onPause();
	}
}
