package com.customsolutions.android.utl;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;

import androidx.appcompat.app.AlertDialog;

/**
 * This Activity asks for Toodledo login information using Toodledo's OAUTH2 interface defined in
 * version 3 of their API.<br>
 * Pass in a Bundle to this Activity with the following attributes:<br>
 * mode: One of the following:<br>
 *     NEW_UTL_ACCOUNT: This is a new UTL account being linked to a Toodledo account.<br>
 *     SIGN_IN: This is an existing UTL account linked to Toodledo, and Toodledo wants the user to
 *         sign in again.  This is called if the Toodledo's refresh token expires.<br>
 *     MERGE: This is an existing UTL account that is not currently linked to Toodledo.  This activity
 *         is being called to establish the link.<br>
 * account_name: The name of the account.  Required if mode==NEW_UTL_ACCOUNT.<br>
 * account_id: The internal database ID of the account.  Required if mode==SIGN_IN or mode==MERGE.<br>
 */
public class ToodledoLoginInfoV3 extends UtlActivity
{
    private static final String TAG = "ToodledoLoginInfoV3";

    // IDs for modes:
    public static final int NEW_UTL_ACCOUNT = 1;
    public static final int SIGN_IN = 2;
    public static final int MERGE = 3;

    // Variables to hold extras passed in:
    private int _mode;
    private String _accountName;
    private long _accountID;

    // Links to key views:
    private TextView _statusMsg;
    private TextView _syncProgressMsg;
    private ProgressBar _progressBar;
    private WebView _oauth2WebView;

    // Variables for communicating with the Synchronizer service:
    Messenger mService = null;
    boolean mIsBound;
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    // Misc variables:
    private boolean _isFirstAccount;
    private ProgressDialog _progressDialog;
    private AccountsDbAdapter _db;
    private String _toodledoStateString;

    /** Called when the activity is first created: */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Link this activity with a layout resource:
        setContentView(R.layout.toodledo_login_info_v3);

        _db = new AccountsDbAdapter();

        // Set the title and icon:
        getSupportActionBar().setIcon(R.drawable.toodledo_logo);
        getSupportActionBar().setTitle(R.string.Link_to_Toodledo);

        // Extract the parameters from the Bundle passed in. Make sure required ones are
        // present.
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            Util.log("Null Bundle passed into ToodeldoLoginInfo.onCreate()");
            finish();
            return;
        }
        if (!extras.containsKey("mode")) {
            Util.log("Missing mode on ToodledoLoginInfo.onCreate().");
            finish();
            return;
        }
        _mode = extras.getInt("mode");
        Util.log("Starting ToodledoLoginInfo activity.  Mode=" + _mode);

        // Other parameters are required, based on the mode.
        if (_mode==NEW_UTL_ACCOUNT)
        {
            if (!extras.containsKey("account_name"))
            {
                Util.log("Missing account_name in ToodledoLoginInfo.onCreate().");
                finish();
                return;
            }
            _accountName = extras.getString("account_name");
        }
        if (_mode==SIGN_IN || _mode==MERGE)
        {
            if (!extras.containsKey("account_id"))
            {
                Util.log("Missing account_id in ToodledoLoginInfo.onCreate().");
                finish();
                return;
            }
            _accountID = extras.getLong("account_id");
        }

        // Link to Key Views:
        _statusMsg = (TextView) findViewById(R.id.td_login_result_message);
        _syncProgressMsg = (TextView) findViewById(R.id.td_login_sync_progress_txt);
        _progressBar = (ProgressBar) findViewById(R.id.td_login_progress_bar);
        _oauth2WebView = (WebView) findViewById(R.id.oauth2_webview);

        // We need to know if this is the first account, since it affects behavior:
        Cursor c = _db.getAllAccounts();
        if (!c.moveToFirst())
            _isFirstAccount = true;
        else
            _isFirstAccount = false;
        c.close();

        // 9/20/17: Removing e-mail collection at Google's request.
        // _emailOptIn.setChecked(true);

        // Run a check to see if a license has been purchased in-app.  This check is important if
        // the user is running the app on a new installation.
        new PurchaseManager(this).verifyLicensePurchase(false);

        // Handler for the continue button:
        findViewById(R.id.td_login_result_continue).setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                if (_isFirstAccount)
                {
                    // Go to the fields/functions used screen:
                    Intent i = new Intent(ToodledoLoginInfoV3.this, FeatureSelection.class);
                    i.putExtra(FeatureSelection.EXTRA_INITIAL_SETUP,true);
                    startActivity(i);
                }
                else
                {
                    if (_mode==SIGN_IN || _mode==MERGE)
                    {
                        // Just return to the last screen (either a task list or account ops screen).
                        ToodledoLoginInfoV3.this.finish();
                        return;
                    }

                    // New account, but not the first account.  Go to a task list.
                    Util.openStartupView(ToodledoLoginInfoV3.this);
                }
            }
        });

        // Load Toodledo's OAUTH2 authentication page:
        _oauth2WebView.getSettings().setJavaScriptEnabled(true);
        _toodledoStateString = Long.valueOf(Double.valueOf(Math.random()*1000000).longValue()).toString();
        _oauth2WebView.loadUrl("https://api.toodledo.com/3/account/authorize.php?response_type=code&client_id="+
            "UltimateToDoList3&state="+_toodledoStateString+"&scope=basic%20tasks%20notes%20outlines%20lists%20share%20write");
        _oauth2WebView.setWebViewClient(new WebViewClient() {
            boolean authComplete = false;
            Intent resultIntent = new Intent();
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon){
                super.onPageStarted(view, url, favicon);
            }
            String authCode;
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url.contains("?code=") && authComplete != true)
                {
                    // User has entered user ID and password.  The redirect URL contains
                    // the code from Toodledo.
                    Uri uri = Uri.parse(url);
                    authCode = uri.getQueryParameter("code");
                    Util.log("ToodledoLoginInfo: Got this auth code: "+authCode);
                    authComplete = true;
                    resultIntent.putExtra("code", authCode);
                    _oauth2WebView.setVisibility(View.GONE);
                    if (!uri.getQueryParameter("state").equals(_toodledoStateString))
                    {
                        // This mismatch indicates a hack of some kind.
                        Util.log("ToodledoLoginInfo: Got state of '"+uri.getQueryParameter("state")+"'"+
                            "  Was expecting '"+_toodledoStateString+"'");
                        ToodledoLoginInfoV3.this.finish();
                    }
                    if (Build.VERSION.SDK_INT >= 11)
                        new OAuth2TokenGetTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,authCode);
                    else
                        new OAuth2TokenGetTask().execute(authCode);
                }
                else if(url.contains("error="))
                {
                    Log.e(TAG,"Toodledo Sign-In Error","Got this error when signing in: "+url);
                    ToodledoLoginInfoV3.this.finish();
                }
            }
        });
    }

    /** This AsyncTask fetches the a token from Toodledo, after the user has authenticated using the WebView.
     * The auth code is passed in to the executeOnExecutor() method.
     */
    private class OAuth2TokenGetTask extends AsyncTask<String, String, JSONObject> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ToodledoLoginInfoV3.this.lockScreenOrientation();
            _progressDialog = new ProgressDialog(ToodledoLoginInfoV3.this);
            _progressDialog.setMessage(Util.getString(R.string.communicating_with_toodledo));
            _progressDialog.setIndeterminate(false);
            _progressDialog.setCancelable(false);
            try
            {
                _progressDialog.show();
            }
            catch (WindowManager.BadTokenException e) { }
        }

        /** The first argument is the access code. */
        @Override
        protected JSONObject doInBackground(String... args)
        {
            // Assemble arguments to pass to Toodledo, including the access code:
            HashMap<String,String> params = new HashMap<>();
            params.put("grant_type", "authorization_code");
            params.put("code",args[0]);
            try
            {
                PackageInfo packageInfo = ToodledoLoginInfoV3.this.getPackageManager().getPackageInfo(
                    "com.customsolutions.android.utl", 0);
                params.put("vers",Integer.valueOf(packageInfo.versionCode).
                    toString());
            }
            catch (Exception ex) { }
            params.put("device",android.os.Build.MODEL);
            params.put("os",Integer.valueOf(Build.VERSION.SDK_INT).toString());

            // Send the request for an access token and get Toodledo's response:
            try
            {
                HttpResponseInfo rInfo = Util.httpsPost("https://api.toodledo.com/3/account/token.php",
                    params, ToodledoInterface.CLIENT_ID, ToodledoInterface.CLIENT_SECRET);
                Util.log("ToodledoLoginInfo: Token response from server: "+rInfo.text);
                if (rInfo.responseCode==200)
                {
                    // The response should be a valid JSON object:
                    JSONObject jo = new JSONObject(rInfo.text);

                    // Fetch the user's email address and other info through another HTTP call.
                    HttpResponseInfo rInfo2 = Util.httpsGet("https://api.toodledo.com/3/account/get.php?access_token="+
                        jo.getString("access_token"),null,null);
                    Util.log("ToodledoLoginInfo: Account Info Response from server: "+rInfo2.text);
                    if (rInfo2.responseCode==200)
                    {
                        // Merge key information from the account with the JSONObject we already have:
                        JSONObject aInfo = new JSONObject(rInfo2.text);
                        jo.put("email",aInfo.getString("email"));
                        jo.put("userid",aInfo.getString("userid"));
                        jo.put("pro",aInfo.getInt("pro"));
                        jo.put("hotlistpriority",aInfo.getInt("hotlistpriority"));
                        jo.put("hotlistduedate",aInfo.getInt("hotlistduedate"));
                        return jo;
                    }
                    else
                    {
                        Log.e(TAG,"Account Info Error","Got HTTP error while getting account "+
                            "info: "+rInfo2.responseCode+" / "+rInfo2.responseMessage);
                        JSONObject jo2 = new JSONObject();
                        jo2.put("errorCode",0);
                        jo2.put("errorDesc",Util.getString(R.string.Cannot_connect_to_TD)+". HTTP Error "+
                            rInfo2.responseCode+": "+rInfo2.responseMessage);
                        return jo2;
                    }
                }
                else
                {
                    Log.e(TAG,"Account Info Error","Got HTTP error while getting account "+
                        "info: "+rInfo.responseCode+" / "+rInfo.responseMessage+" / "+rInfo.text);
                    JSONObject jo = new JSONObject();
                    jo.put("errorCode",0);
                    jo.put("errorDesc",Util.getString(R.string.Cannot_connect_to_TD)+". HTTP Error "+
                        rInfo.responseCode+": "+rInfo.responseMessage);
                    return jo;
                }
            }
            catch (JSONException e)
            {
                Log.e(TAG,"JSONExcpetion getting token","Got exception while getting token.",e);
                JSONObject jo = new JSONObject();
                try
                {
                    jo.put("errorCode", 0);
                    jo.put("errorDesc", Util.getString(R.string.Cannot_connect_to_TD) + ". "+
                        e.getLocalizedMessage());
                    return jo;
                }
                catch (JSONException e2) { return null; }
            }
            catch (IOException e)
            {
                Util.log("ToodledoLoginInfo: got IOException while getting token. "+e.getClass().getName()+": "
                    +e.getMessage());
                JSONObject jo = new JSONObject();
                try
                {
                    jo.put("errorCode", 0);
                    jo.put("errorDesc", Util.getString(R.string.TD_not_responding));
                    return jo;
                }
                catch (JSONException e2) { return null; }
            }
        }

        @Override
        protected void onPostExecute(JSONObject jo)
        {
            _progressDialog.dismiss();

            if (jo==null)
            {
                // This should not happen, but handle it anyway.
                handleFailure(Util.getString(R.string.TD_not_responding));
                return;
            }

            if (jo.has("errorCode"))
            {
                try
                {
                    handleFailure(jo.getString("errorDesc"));
                }
                catch (JSONException e)
                {
                }
                return;
            }

            // We now have enough information to create or update the account.
            try
            {
                if (_mode == NEW_UTL_ACCOUNT)
                {
                    // We need to make sure the user has not already linked with this toodledo account.
                    // Make sure no other UTL accounts link to this one:
                    UTLAccount dup = _db.getAccountByTDUserID(jo.getString("userid"));
                    if (dup!=null)
                    {
                        handleFailure(Util.getString(R.string.Duplicate_Account_Attempt));
                        return;
                    }

                    UTLAccount acc = new UTLAccount();
                    acc.name = _accountName;
                    acc.td_email = jo.getString("email");
                    acc.td_userid = jo.getString("userid");
                    acc.current_token = jo.getString("access_token");
                    acc.sync_service = UTLAccount.SYNC_TOODLEDO;
                    acc.username = jo.getString("email");
                    acc.refresh_token = jo.getString("refresh_token");
                    acc.token_expiry = System.currentTimeMillis() + 1000 * jo.getLong("expires_in");
                    acc.pro = jo.getInt("pro") > 0;
                    acc.hotlist_priority = jo.getInt("hotlistpriority")+2;
                    acc.hotlist_due_date = jo.getInt("hotlistduedate")-1;
                    if (acc.hotlist_due_date<0) acc.hotlist_due_date = 0;
                    acc.sign_in_needed = false;
                    long id = _db.addAccount(acc);
                    if (id == -1)
                    {
                        Util.popup(ToodledoLoginInfoV3.this, R.string.DbInsertFailed);
                        finish();
                        return;
                    }

                    // Log this event:
                    Util.logOneTimeEvent(ToodledoLoginInfoV3.this, "account_setup", 0, new String[] {
                        "toodledo", Integer.valueOf(_mode).toString()});
                    _accountID = id;

                    // If this is the first account, we set Toodledo hotlist preferences for use in generating
                    // the hotlist view rules.
                    if (_isFirstAccount)
                    {
                        ViewsDbAdapter vdb = new ViewsDbAdapter();
                        Cursor c2 = vdb.getView(ViewNames.HOTLIST,"");
                        if (c2.moveToFirst())
                        {
                            // Delete the existing hotlist view.  It will be regenerated with updated rules.
                            vdb.deleteView(Util.cLong(c2,"_id"));
                        }
                        c2.close();

                        Util.updatePref(PrefNames.HOTLIST_PRIORITY,acc.hotlist_priority,ToodledoLoginInfoV3.this);
                        Util.updatePref(PrefNames.HOTLIST_DUE_DATE,acc.hotlist_due_date,ToodledoLoginInfoV3.this);

                        // The startup view ID may have referenced the now deleted hotlist view.  If so, update the
                        // startup view ID.
                        Long startupViewID = Util.settings.getLong(PrefNames.STARTUP_VIEW_ID, -1);
                        c2 = vdb.getView(startupViewID);
                        if (!c2.moveToFirst())
                        {
                            Cursor c = vdb.getView(ViewNames.HOTLIST,""); // regenerates hotlist rules.
                            if (c.moveToFirst())
                            {
                                Util.updatePref(PrefNames.STARTUP_VIEW_ID,Util.cLong(c,"_id"),ToodledoLoginInfoV3.this);
                            }
                            c.close();
                        }
                        c2.close();
                    }

                    handleSuccess();
                }
                else if (_mode == SIGN_IN)
                {
                    // We need to make sure the user is signing into the account we expect:
                    UTLAccount acc = _db.getAccount(_accountID);
                    if (!acc.td_userid.equals(jo.getString("userid")))
                    {
                        UTLAccount expected = _db.getAccount(_accountID);
                        handleFailure(Util.getString(R.string.toodledo_signin_mismatch)+
                            " "+expected.td_email);
                        return;
                    }

                    // Update the account in the database.
                    acc.td_email = jo.getString("email");
                    acc.td_userid = jo.getString("userid");
                    acc.current_token = jo.getString("access_token");
                    acc.sync_service = UTLAccount.SYNC_TOODLEDO;
                    acc.username = jo.getString("email");
                    acc.refresh_token = jo.getString("refresh_token");
                    acc.token_expiry = System.currentTimeMillis() + 1000 * jo.getLong("expires_in");
                    acc.pro = jo.getInt("pro") > 0;
                    acc.sign_in_needed = false;
                    _db.modifyAccount(acc);

                    Util.log("TooldedoLoginInfo: User signed in again to account with email "+acc.td_email);
                    handleSuccess();
                }
                else if (_mode == MERGE)
                {
                    // We need to make sure the user has not already linked with this toodledo account.
                    // Make sure no other UTL accounts link to this one:
                    UTLAccount dup = _db.getAccountByTDUserID(jo.getString("userid"));
                    if (dup!=null)
                    {
                        handleFailure(Util.getString(R.string.Duplicate_Account_Attempt));
                        return;
                    }

                    // Update the account in the database.
                    UTLAccount acc = _db.getAccount(_accountID);
                    acc.td_email = jo.getString("email");
                    acc.td_userid = jo.getString("userid");
                    acc.current_token = jo.getString("access_token");
                    acc.sync_service = UTLAccount.SYNC_TOODLEDO;
                    acc.username = jo.getString("email");
                    acc.refresh_token = jo.getString("refresh_token");
                    acc.token_expiry = System.currentTimeMillis() + 1000 * jo.getLong("expires_in");
                    acc.pro = jo.getInt("pro") > 0;
                    acc.sign_in_needed = false;
                    _db.modifyAccount(acc);

                    Util.log("TooldedoLoginInfo: User merged unsynced account to TD account with email "+acc.td_email);
                    handleSuccess();
                }
            }
            catch (JSONException e)
            {
                Util.log("ToodledoLoginInfo: Received JSONException. "+e.getMessage());
                handleFailure(Util.getString(R.string.Bad_respone_from_TD)+" "+e.getMessage());
                return;
            }
        }
    }


    /** Display a popup on failure */
    private void handleFailure(String message)
    {
        // Handler for pressing the OK button in the dialog (nothing to do):
        DialogInterface.OnClickListener dialogClickListener = new
            DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    ToodledoLoginInfoV3.this.finish();
                }
            };

        // Create and show the message dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(ToodledoLoginInfoV3.this);
        builder.setMessage(message);
        builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
        builder.show();
    }

    /** Handle a successful link.  This shows the correct views, displays a confirmation message, and starts the sync. */
    private void handleSuccess()
    {
        // Show the correct views:
        _oauth2WebView.setVisibility(View.GONE);
        findViewById(R.id.td_login_result_wrapper).setVisibility(View.VISIBLE);
        UTLAccount a = _db.getAccount(_accountID);
        TextView freeLimits = (TextView)findViewById(R.id.td_login_result_free_limits);
        if (!a.pro)
        {
            // Show the limits imposed on free accounts.
            freeLimits.setText(R.string.Free_Account_Limits_2);
        }
        else
            freeLimits.setText("");

        // Display an appropriate confirmation message:
        if (_mode==NEW_UTL_ACCOUNT)
            _statusMsg.setText(R.string.Synchronization_will_run_in_background);
        else if (_mode==SIGN_IN)
            _statusMsg.setText(R.string.toodledo_re_sign_in);
        else
            _statusMsg.setText(R.string.toodledo_merge_confirmation);

        // Show an Ad:
        initBannerAd();

        // Start synchronizing:
        startSync();

        // Toodledo requires that due dates are enabled if reminders are enabled. This is because
        // Toodledo stores the reminder relative to the due date and time.
        SharedPreferences prefs = getSharedPreferences(Util.PREFS_NAME,0);
        if (prefs.getBoolean(PrefNames.REMINDER_ENABLED,true))
            prefs.edit().putBoolean(PrefNames.DUE_DATE_ENABLED,true).apply();
    }

    //
    // Methods for interacting with the Synchronization Service:
    //

    /** Start a synchronization: */
    private void startSync()
    {
        // Establish a link to the Synchronizer service:
        doBindService();

        // Start the sync.
        Intent i = new Intent(ToodledoLoginInfoV3.this, Synchronizer.class);
        i.putExtra("command", "full_sync");
        i.putExtra("send_percent_complete", true);
        Synchronizer.enqueueWork(ToodledoLoginInfoV3.this,i);
    }

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Synchronizer.SYNC_RESULT_MSG:
                    int result = msg.arg1;
                    if (result == Synchronizer.SUCCESS)
                    {
                        _syncProgressMsg.setText(R.string.Sync_Successful);
                        _progressBar.setVisibility(View.INVISIBLE);
                    }
                    else
                    {
                        _syncProgressMsg.setText(Synchronizer.getFailureString(result));
                        _progressBar.setVisibility(View.INVISIBLE);
                    }
                    break;

                case Synchronizer.PERCENT_COMPLETE_MSG:
                    // Update the progress bar:
                    int percentComplete = msg.arg1;
                    _progressBar.setProgress((int)(_progressBar.getMax()*percentComplete/100.0));
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try
            {
                Message msg = Message.obtain(null,
                        Synchronizer.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    };

    void doBindService()
    {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
        if (!mIsBound)
        {
            mIsBound = bindService(new Intent(ToodledoLoginInfoV3.this,
                Synchronizer.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    void doUnbindService()
    {
        if (mIsBound)
        {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null)
            {
                try
                {
                    Message msg = Message.obtain(null,
                            Synchronizer.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                }
                catch (RemoteException e)
                {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    public void onPause()
    {
        Stats.uploadStats(this,null);
        super.onPause();
    }

    @Override
    public void onDestroy()
    {
        doUnbindService();
        super.onDestroy();
    }
}
