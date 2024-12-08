package com.customsolutions.android.utl;

// This activity gets the name of an account.  This can either be called as part of the 
// account setup process, or to adjust an existing UTL account.

// To call this, pass in a Bundle with the following keys/values:
// mode: Either ACCOUNT_SETUP or ACCOUNT_MODIFY.  For ACCOUNT_SETUP, this will not return
//     a result to the caller.  Instead, it will call the next activity in the account 
//     setup chain.
// sync_service: One of the values in UTLAccount.java (e.g., UTLAccount.SYNC_GOOGLE)
//     If omitted, UTLAccount.SYNC_TOODLEDO is assumed.
// td_status: Required if mode is ACCOUNT_SETUP and sync_service is UTLAccount.SYNC_TOODLEDO.
//     One of: NEW_ACCOUNT, EXISTING_ACCOUNT, NONE
// NOTE: to specify an unsynced account, omit sync_service and set td_status=NONE

// For the ACCOUNT_MODIFY mode, the following items are returned to the caller:
// resultCode: either RESULT_CANCELED or RESULT_OK
// Intent object extras:
//     name

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class GetAccountName extends UtlActivity 
{
    // IDs for modes:
    public static final int ACCOUNT_SETUP = 0;
    public static final int ACCOUNT_MODIFY = 1;
    
    // IDs for Toodledo status:
    public static final int NONE = 2;
    public static final int NEW_ACCOUNT = 3;
    public static final int EXISTING_ACCOUNT = 4;
    
    // Variable to hold Bundle data:
    private int _mode;
    private int _tdStatus;
    private int _syncService;
    
    @Override
	public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        Util.log("Begin getting account name.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.get_account_name);
        
        // Extract the parameters from the Bundle passed in. Make sure required ones are
        // present.
        Bundle extras = getIntent().getExtras();
        if (extras==null)
        {
            Util.log("Null Bundle passed into GetAccountName.onCreate()");
            finish();
            return;
        }
        if (!extras.containsKey("mode"))
        {
            Util.log("Missing mode on ToodledoLoginInfo.onCreate().");
            finish();
            return;
        }
        _mode = extras.getInt("mode");
        if (_mode==ACCOUNT_SETUP)
        {
            if (extras.containsKey("sync_service"))
            	_syncService = extras.getInt("sync_service");
            else
            	_syncService = UTLAccount.SYNC_TOODLEDO;
            
            if (!extras.containsKey("td_status") && _syncService!=UTLAccount.SYNC_GOOGLE)
            {
                Util.log("Missing td_status in GetAccountName.onCreate()");
                finish();
                return;
            }
            if (extras.containsKey("td_status"))
            	_tdStatus = extras.getInt("td_status");
            
            // Set the title at the top of the screen:
            getSupportActionBar().setTitle(R.string.Account_Name);
            
            if (_syncService==UTLAccount.SYNC_GOOGLE)
            	getSupportActionBar().setIcon(R.drawable.google_logo);
            if (_syncService==UTLAccount.SYNC_TOODLEDO && _tdStatus!=NONE)
            	getSupportActionBar().setIcon(R.drawable.toodledo_logo);
        }
        else
        {
        	// Set the title at the top of the screen:
        	getSupportActionBar().setTitle(R.string.Rename_Account);
        }
        
        // Log what we're doing:
        Util.log("Create a new account");
        
        // Done Button:
        Button done = (Button)findViewById(R.id.new_account_done_button);
        done.setOnClickListener(new View.OnClickListener() 
        { 
            @Override
            public void onClick(View v) 
            {
                // Verify that a name has been entered.
                TextView t = (TextView)GetAccountName.this.findViewById(R.id.new_account_name);
                if (t.getText().length() == 0)
                {
                    Util.popup(GetAccountName.this, R.string.Please_enter_a_name);
                    return;
                }
                
            	// Verify that the name does not already exist:
            	AccountsDbAdapter db = new AccountsDbAdapter();
            	UTLAccount a = db.getAccount(t.getText().toString());
            	if (a!=null)
            	{
            		Util.popup(GetAccountName.this, R.string.Account_Name_Already_Exists);
            		return;
            	}

            	if (_mode==ACCOUNT_MODIFY)
                {
                    // Send the response back with the new name.
                    Bundle b = new Bundle();
                    b.putString("name",t.getText().toString());
                    Intent i = new Intent();
                    i.putExtras(b);
                    setResult(RESULT_OK,i);
                    finish();
                }
                else
                {
                    // Call the next activity:
                	if (_syncService==UTLAccount.SYNC_GOOGLE)
                	{
                		Intent i = new Intent(GetAccountName.this,GTasksSetup.class);
        				i.putExtra("sync_mode", GTasksSetup.INITIAL_SYNC);
        				i.putExtra("name", t.getText().toString());
        				startActivity(i);
                	}
                	else if (_tdStatus==NONE)
                    {
                		// Set up the account:
    	            	UTLAccount acct = new UTLAccount();
    	            	acct.name = t.getText().toString();
    	            	acct.sync_service = UTLAccount.SYNC_NONE;
    	            	long accountID = (new AccountsDbAdapter()).addAccount(acct);
    	            	if (accountID==-1)
    	            	{
    	            		Util.log("Could not insert new Account into Database.");
    	            		Util.popup(GetAccountName.this, R.string.DbInsertFailed);
    	            		return;
    	            	}
    	            	
    	            	// Display a confirmation dialog:
    	            	DialogInterface.OnClickListener dialogClickListener = new 
    	        			DialogInterface.OnClickListener() 
    	                {
    	        			@Override
    	                    public void onClick(DialogInterface dialog, int which) 
    	                    {
    	        				// Go to the startup task list when done.
    	        				Util.openStartupView(GetAccountName.this);
    	                    }
    	                };
    	                AlertDialog.Builder builder = new AlertDialog.Builder(GetAccountName.this);
    	                builder.setMessage(getString(R.string.The_account_hash_been_set_up));
    	                builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
    	                builder.show();
                    }
                    else
                    {
                        Intent i = new Intent(GetAccountName.this,ToodledoLoginInfoV3.class);
                        i.putExtra("mode", ToodledoLoginInfoV3.NEW_UTL_ACCOUNT);
                        i.putExtra("account_name", t.getText().toString());
                        startActivity(i);
                    }
                }
            }
        });
    }
}
