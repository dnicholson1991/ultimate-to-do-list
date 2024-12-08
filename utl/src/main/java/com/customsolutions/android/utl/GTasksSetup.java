package com.customsolutions.android.utl;

// This Activity asks the user to choose a Google account and then sets up a UTL 
// account linked to it.

// To call the activity, put a Bundle in the Intent with the following keys/values:
// sync_mode: One of: INITIAL_SYNC or MERGE.  INITIAL_SYNC is for a new UTL account.
//     MERGE will take an existing unsynced UTL account and linking it to a Google account.
// account_id: The ID of an existing account.  Used if sync_mode==MERGE.
// name: Required if sync_mode==INITIAL_SYNC. This is the new account's name.

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.tasks.TasksScopes;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

public class GTasksSetup extends UtlActivity
{
	private static final String TAG = "GTasksSetup";

	// IDs for the sync mode:
	public static final int INITIAL_SYNC = 1;
	public static final int MERGE = 2;

	// Variables for the state of this activity:
	private static final int NEEDS_ACCOUNT_CHOSEN = 1;
	private static final int SYNC_IN_PROGRESS = 2;
	private static final int SYNC_DONE = 3;

	private static String REDIRECT_URI="com.googleusercontent.apps.138989701453:/auth_redirect";
	private static String GRANT_TYPE="authorization_code";
	private static String OAUTH_URL ="https://accounts.google.com/o/oauth2/auth";
	private static String OAUTH_SCOPE="https://www.googleapis.com/auth/tasks https://www.googleapis.com/auth/userinfo.email";

	// Code for the activity called to get permission from the user to use
	// Google account info:
	private static final int REQUEST_AUTHENTICATE = 1;

	/** Code for the activity called get access to Google Play Services: */
	private static final int REQUEST_GOOGLE_PLAY_SERVICES = 2;

	/** Code for onActivityResult indicating results of signin and authorization for a device
	 * account. */
	private static final int RESULT_CODE_DEVICE_ACCOUNT_AUTH = 12355;

	/** Code for onActivityResult indicating that the user has chosen an account (or cancelled
	 * choosing an account). */
	public static final int RESULT_CODE_ACCOUNT_CHOSEN = 2687;

	private int _syncMode;
	private long _accountID;
	private UTLAccount _existingAccount;
	private AccountsDbAdapter _db;
	private boolean _isFirstAccount;
	private int _state;
	private ProgressDialog _progressDialog;
	private String _name;

	// Views we need to keep track of:
	private LinearLayout _choiceContainer;
	private LinearLayout _resultContainer;
	private TextView _setupResultMsg;
	private Button _continueButton;
	private Button _tryAgainButton;
	private Button _cancelButton;
	private TextView _syncProgressMsg;
	private ProgressBar _progressBar;

	/** This is used for authentication and fetching of account info. */
	private GoogleAccountCredential _googleCredential;

	// Variables for communicating with the Synchronizer service:
	Messenger mService = null;
	boolean mIsBound;
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	// Called when activity is first created:
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Link this activity with a layout resource:
		setContentView(R.layout.gtasks_setup);

		// Set the default title and icon:
		getSupportActionBar().setIcon(R.drawable.google_logo);
		getSupportActionBar().setTitle(R.string.Link_To_Google);

		// Extract the parameters from the Bundle passed in. Make sure required
		// ones are present.
		Bundle extras = getIntent().getExtras();
		if (extras == null)
		{
			Util.log("GTasksSetup: Null Bundle passed into GTasksSetup.onCreate()");
			finish();
			return;
		}
		if (!extras.containsKey("sync_mode"))
		{
			Util.log("GTasksSetup: Missing sync_mode in GTasksSetup.onCreate().");
			finish();
			return;
		}
		_syncMode = extras.getInt("sync_mode");
		_db = new AccountsDbAdapter();
		Util.log("GTasksSetup: Starting GTasksSetup activity.");
		if (_syncMode == MERGE)
		{
			Util.log("GTasksSetup: sync_mode is MERGE. "+_syncMode);
			if (!extras.containsKey("account_id"))
			{
				Util.log("GTasksSetup: Missing account_id in GTasksSetup.onCreate().");
				finish();
				return;
			}
			_accountID = extras.getLong("account_id");
			_existingAccount = _db.getAccount(_accountID);
			if (_existingAccount == null)
			{
				Util.log("GTasksSetup: Bad account ID " + _accountID
					+ " passed into GTasksSetup.onCreate()");
				finish();
				return;
			}
		}
		else if (_syncMode == INITIAL_SYNC)
		{
			Util.log("GTasksSetup: sync_mode is INITIAL_SYNC");
			// There had better be a name passed in:
			if (!extras.containsKey("name"))
			{
				Util.log("GTasksSetup: Missing name input in GTasksSetup.onCreate().");
				finish();
				return;
			}
			_name = extras.getString("name");
		}

		// We need to know if this is the first account. This affects the
		// display:
		_isFirstAccount = false;
		Cursor c = _db.getAllAccounts();
		if (!c.moveToFirst())
			_isFirstAccount = true;

		// Link to key views:
		_choiceContainer = (LinearLayout) findViewById(R.id.gtasks_setup_account_choice_container);
		_resultContainer = (LinearLayout) findViewById(R.id.gtasks_setup_result_container);
		_setupResultMsg = (TextView) findViewById(R.id.gtasks_setup_result_msg);
		_continueButton = (Button) findViewById(R.id.gtasks_setup_result_continue);
		_tryAgainButton = (Button) findViewById(R.id.gtasks_setup_result_try_again);
		_cancelButton = (Button) findViewById(R.id.gtasks_setup_result_cancel);
		_syncProgressMsg = (TextView) findViewById(R.id.gtasks_setup_sync_progress_txt);
		_progressBar = (ProgressBar) findViewById(R.id.gtasks_setup_sync_progress_bar);

		_googleCredential = GoogleAccountCredential.usingOAuth2(this, Collections.
			singleton(TasksScopes.TASKS)).setBackOff(new ExponentialBackOff());

		// Run a check to see if a license has been purchased in-app:
		new PurchaseManager(this).verifyLicensePurchase(false);

		// Button Handlers:

		// Continue Button:
		_continueButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (_isFirstAccount)
				{
					// Initial app setup.  Go to the fields/functions used screen:
					Intent i = new Intent(GTasksSetup.this, FeatureSelection.class);
					i.putExtra(FeatureSelection.EXTRA_INITIAL_SETUP,true);
					startActivity(i);
				}
				else
				{
					// Not the first account, go to the task list:
					Util.openStartupView(GTasksSetup.this);
				}
			}
		});

		// Try Again Button:
		_tryAgainButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				chooseAccount();
			}
		});

		// Cancel Button:
		_cancelButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				finish();
			}
		});
	}

	@Override
	public void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		if (savedInstanceState == null)
		{
			_state = NEEDS_ACCOUNT_CHOSEN;
			chooseAccount();
		}
		else if (!savedInstanceState.containsKey("state"))
		{
			_state = NEEDS_ACCOUNT_CHOSEN;
			chooseAccount();
		}
		else
		{
			_state = savedInstanceState.getInt("state");
			if (savedInstanceState.containsKey("is_first_account"))
				_isFirstAccount = savedInstanceState.getBoolean("is_first_account");

			switch (_state)
			{
				case NEEDS_ACCOUNT_CHOSEN:
					chooseAccount();
					break;

				case SYNC_IN_PROGRESS:
					// Establish a link to the Synchronizer service:
					doBindService();

					// Initialize the progress bar:
					if (savedInstanceState.containsKey("progress_bar"))
						_progressBar.setProgress(savedInstanceState.getInt("progress_bar"));

				case SYNC_DONE:  // and SYNC_IN_PROGRESS
					// Show the account setup result views:
					_resultContainer.setVisibility(View.VISIBLE);
					_choiceContainer.setVisibility(View.GONE);

					if (_state==SYNC_DONE && savedInstanceState.containsKey("result_msg"))
					{
						_syncProgressMsg.setText(savedInstanceState.getString("result_msg"));
						_progressBar.setVisibility(View.INVISIBLE);
					}

					// Assemble the message to display:
					String successMsg = getString(R.string.Synchronization_will_run_in_background)
						+ "\n\n" + Util.getString(R.string.GTasks_Folder_Mapping) + "\n";
					_setupResultMsg.setText(successMsg);

					// Display the buttons I want to see:
					_continueButton.setVisibility(View.VISIBLE);
					_tryAgainButton.setVisibility(View.GONE);
					_cancelButton.setVisibility(View.GONE);

					// Show an Ad:
					initBannerAd();
			}
		}
	}

	/** This will be called after the user signs in via a browser. */
	@Override
	protected void onNewIntent(Intent i)
	{
		super.onNewIntent(i);
		Log.v(TAG,"New Intent Received : "+Log.intentToString(i,2));
		if (i.getData()!=null && Util.isValid(i.getData().toString()))
		{
			String authCode = i.getData().getQueryParameter("code");
			if (Util.isValid(authCode))
			{
				Log.v(TAG,"Got this auth code from Google: "+authCode);
				new OAuth2TokenGetTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					authCode);

			}
			else
			{
				Log.e(TAG,"Missing Auth Code","onNewIntent() was called but no authorization " +
					"code was received. Data: "+i.getData().toString());
			}
		}
	}

	// Handler for resuming the activity (when we return):
	@Override
	public void onResume()
	{
		super.onResume();

		// Establish the link to the synchronizer service:
		doBindService();
	}

	// Choose an account and then try to link to it:
	void chooseAccount()
	{
		// Hide the account setup result:
		_resultContainer.setVisibility(View.GONE);
		_choiceContainer.setVisibility(View.VISIBLE);

		// Amazon users must use browser sign-in:
		if (Util.IS_AMAZON)
		{
			browserSignIn();
			return;
		}

		Intent accountChooser = AccountPicker.newChooseAccountIntent(
			null,
			null,
			new String[] {"com.google"},
			true,
			getString(R.string.Link_To_Google),
			null,
			null,
			null
		);
		if (accountChooser.resolveActivity(getPackageManager()) != null)
			startActivityForResult(accountChooser,RESULT_CODE_ACCOUNT_CHOSEN);
		else
		{
			// This means Google Play services is not installed. Use the browser sign in.
			Util.log("GTasksSetup: Need to switch to browser sign in.");
			browserSignIn();
		}
	}

	/** Sign in from a browser. This is needed for Kindle devices which don't have linked Google
	 * accounts. */
	private void browserSignIn()
	{
		// Don't show anything on the screen:
		_resultContainer.setVisibility(View.GONE);
		_choiceContainer.setVisibility(View.GONE);

		// Generate the Google URL to use for sign-in:
		try
		{
			Uri uri = Uri.parse(OAUTH_URL+
				"?redirect_uri="+
				URLEncoder.encode(REDIRECT_URI,"UTF-8")+
				"&response_type=code&client_id="+GTasksInterface.CLIENT_ID+
				"&scope="+URLEncoder.encode(OAUTH_SCOPE,"UTF-8")
			);

			// Launch the browser:
			Intent i = new Intent(Intent.ACTION_VIEW,uri);
			startActivity(i);
		}
		catch (UnsupportedEncodingException e)
		{
			Util.handleException(TAG,this,e);
		}
	}

	// Handle the response to the request to the user for permission:
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		Util.log("GTasksSetup: onActivityResult called for request code "+requestCode+
			"; resultCode: "+resultCode+"; intent: "+Util.intentToString(intent,2));
		super.onActivityResult(requestCode, resultCode, intent);
		switch (requestCode)
		{
			case RESULT_CODE_ACCOUNT_CHOSEN:
				if (resultCode==RESULT_OK  && intent.hasExtra(AccountManager.KEY_ACCOUNT_NAME))
				{
					String selectedAccount = intent.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
					Util.log("GTasksSetup: Chosen account: "+selectedAccount);
					GoogleSignInClient client = Util.getGoogleSignInClient(this,selectedAccount);
					startActivityForResult(client.getSignInIntent(),
						RESULT_CODE_DEVICE_ACCOUNT_AUTH);
				}
				else
				{
					// The user must have cancelled account setup.
					Util.log("GTasksSetup: Account selection cancelled. Exiting activity.");
					finish();
				}
				break;

			case RESULT_CODE_DEVICE_ACCOUNT_AUTH:
				Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(
					intent);
				task.addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>()
				{
					@Override
					public void onComplete(@NonNull Task<GoogleSignInAccount> task)
					{
						if (task.isSuccessful())
						{
							GoogleSignInAccount googleAccount = task.getResult();
							Util.log("GTasksSetup: googleAccount: " + googleAccount.
								getDisplayName() + "; " +googleAccount.getEmail());
							_googleCredential.setSelectedAccount(googleAccount.getAccount());
							new FetchTokenForDeviceAccount().executeOnExecutor(AsyncTask.
								THREAD_POOL_EXECUTOR, null);
						}
						else
						{
							Exception exception = task.getException();
							String errorMessage = exception.getClass().getName()+" / "+
								exception.getMessage();
							Log.w(TAG,"Sign-In Failure","Device account sign-in failure",exception);
							if (exception instanceof ApiException)
							{
								ApiException e = (ApiException) exception;
								int statusCode = e.getStatusCode();
								Util.log("GTasksSetup: Status code: "+statusCode);
								if (statusCode==GoogleSignInStatusCodes.SIGN_IN_CANCELLED)
								{
									finish();
									return;
								}
								else if (statusCode==GoogleSignInStatusCodes.SIGN_IN_FAILED)
								{
									Util.log("GTasksSetup: Trying browser sign in.");
									browserSignIn();
									return;
								}
							}
							displaySetupFailure(errorMessage);
						}
					}
				});
				break;

			case REQUEST_AUTHENTICATE:
				if (resultCode != RESULT_OK)
				{
					Util.log("GTasksSetup: User denied permission.");
					return;
				}

				Util.log("GTasksSetup: Permission activity completed.  Permission granted.");
				new FetchTokenForDeviceAccount().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					null);
				break;

			case REQUEST_GOOGLE_PLAY_SERVICES:
				if (resultCode != RESULT_OK)
				{
					Util.log("GTasksSetup: User did not set up Google Play Services.");
					return;
				}

				Util.log("GTasksSetup: Google Play Services set up.  Trying to get token again.");
				new FetchTokenForDeviceAccount().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
					null);
				break;
		}
	}

	/** Data structure used by FetchTokenForDeviceAccount */
	private class DeviceAccountTokenData
	{
		public boolean isSuccessful;
		public String token;
		public Exception exception;
	}

	// AsyncTask to fetch a new token using a device account:
	private class FetchTokenForDeviceAccount extends AsyncTask<Void,Void,DeviceAccountTokenData>
	{
		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			_progressDialog = ProgressDialog.show(GTasksSetup.this, null,
				Util.getString(R.string.Communicating_With_Google), false);
		}

		@Override
		protected DeviceAccountTokenData doInBackground(Void... args)
		{
			DeviceAccountTokenData result = new DeviceAccountTokenData();
			try
			{
				String newToken = _googleCredential.getToken();
				result.isSuccessful = true;
				result.token = newToken;
				Util.log("GTasksSetup: Got token for new device account link: " + newToken);
				return result;
			}
			catch (Exception e)
			{
				Log.d(TAG,"Unable to get initial token.",e);
				result.isSuccessful = false;
				result.exception = e;
				return result;
			}
		}

		@Override
		protected void onPostExecute(DeviceAccountTokenData tokenData)
		{
			dismissProgressDialog();
			if (tokenData.isSuccessful)
			{
				finishAccountSetup(tokenData.token);
			}
			else
			{
				if (tokenData.exception instanceof UserRecoverableAuthIOException)
				{
					UserRecoverableAuthIOException e = (UserRecoverableAuthIOException)tokenData.exception;
					startActivityForResult(e.getIntent(), REQUEST_AUTHENTICATE);
				}
				else if (tokenData.exception instanceof UserRecoverableAuthException)
				{
					UserRecoverableAuthException e = (UserRecoverableAuthException)tokenData.exception;
					startActivityForResult(e.getIntent(), REQUEST_AUTHENTICATE);
				}
				else if (tokenData.exception instanceof GooglePlayServicesAvailabilityIOException)
				{
					GooglePlayServicesAvailabilityIOException e = (GooglePlayServicesAvailabilityIOException) tokenData.exception;
					Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
						e.getConnectionStatusCode(),GTasksSetup.this,REQUEST_GOOGLE_PLAY_SERVICES);
					dialog.show();
				}
				else if (tokenData.exception instanceof GooglePlayServicesAvailabilityException)
				{
					GooglePlayServicesAvailabilityException e = (GooglePlayServicesAvailabilityException) tokenData.exception;
					Dialog dialog = GooglePlayServicesUtil.getErrorDialog(
						e.getConnectionStatusCode(),GTasksSetup.this,REQUEST_GOOGLE_PLAY_SERVICES);
					dialog.show();
				}
				else
				{
					String errorMsg = GTasksInterface.getErrorMessage(GTasksInterface.
						parseException(tokenData.exception));
					Log.e(TAG,"Can't Get Initial Token","Error from Google in getting token. "+
						errorMsg,tokenData.exception);
					displaySetupFailure(errorMsg);
				}
			}
		}
	}

	// After the user enters the user ID and password, we receive an access Code from Google.  This
	// uses that code to get a token, which is passed to Google for further API calls.  Returns null
	// on a network or Google error.
	private JSONObject getOAuth2Token(String address,String accessCode,String clientID,String clientSecret,String redirectUri,String grantType)
	{
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		DefaultHttpClient httpClient;
		HttpPost httpPost;
		InputStream is = null;
		JSONObject jObj = null;
		String json = "";

		// Making HTTP request
		try {
			// DefaultHttpClient
			httpClient = new DefaultHttpClient();
			httpPost = new HttpPost(address);
			params.add(new BasicNameValuePair("code", accessCode));
			params.add(new BasicNameValuePair("client_id", clientID));
			params.add(new BasicNameValuePair("client_secret", clientSecret));
			params.add(new BasicNameValuePair("redirect_uri", redirectUri));
			params.add(new BasicNameValuePair("grant_type", grantType));
			httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.setEntity(new UrlEncodedFormEntity(params));
			org.apache.http.HttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity httpEntity = httpResponse.getEntity();
			is = httpEntity.getContent();
		}
		catch (UnsupportedEncodingException e)
		{
			Log.e(TAG,"Can't Get OAUTH2 Token","Can't get initial OAUTH2 token from code.",e);
			return null;
		}
		catch (ClientProtocolException e)
		{
			Log.e(TAG,"Can't Get OAUTH2 Token","Can't get initial OAUTH2 token from code.",e);
			return null;
		}
		catch (IOException e)
		{
			Util.log("GTasksSetup: getOAuth2Token Error: "+e.getMessage());
			return null;
		}
		try
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 100);
			StringBuilder sb = new StringBuilder();
			String line = null;
			while ((line = reader.readLine()) != null) {
				sb.append(line + "\n");
			}
			is.close();
			json = sb.toString();
			Util.log("GTasksSetup: getOAuth2Token JSON response: "+json);
		}
		catch (Exception e)
		{
			Log.e(TAG,"Can't Get OAUTH2 Token","Can't get initial OAUTH2 token from code.",e);
			return null;
		}

		// Parse the String to a JSON Object
		try
		{
			jObj = new JSONObject(json);
		}
		catch (JSONException e)
		{
			Log.e(TAG,"Can't Get OAUTH2 Token","Can't get initial OAUTH2 token from code.",e);
			return null;
		}

		// Return JSON Object:
		return jObj;
	}

	private class OAuth2TokenGetTask extends AsyncTask<String, String, JSONObject> {
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			_progressDialog = new ProgressDialog(GTasksSetup.this);
			_progressDialog.setMessage(Util.getString(R.string.Communicating_With_Google));
			_progressDialog.setIndeterminate(false);
			_progressDialog.setCancelable(false);
			_progressDialog.show();
		}

		/** The first argument is the access code. */
		@Override
		protected JSONObject doInBackground(String... args)
		{
			JSONObject json = getOAuth2Token(GTasksInterface.TOKEN_URL,args[0],GTasksInterface.CLIENT_ID,GTasksInterface.CLIENT_SECRET,REDIRECT_URI,GRANT_TYPE);
			if (json==null) return null;

			try
			{
				String tok = json.getString("access_token");

				// Fetch the user's email address through another HTTP call.
				DefaultHttpClient httpClient = new DefaultHttpClient();
				HttpGet httpGet = new HttpGet("https://openidconnect.googleapis.com/v1/userinfo?alt=json&access_token="+tok);
				org.apache.http.HttpResponse httpResponse = httpClient.execute(httpGet);
				String responseBody = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
				Util.log("GTasksSetup: Response from email query: "+responseBody);
				JSONObject emailJson = new JSONObject(responseBody);
				if (emailJson.has("data") && !emailJson.has("email"))
					emailJson = emailJson.getJSONObject("data");
				String email = emailJson.getString("email");
				json.put("email",email);
				return json;
			}
			catch (JSONException e)
			{
				Log.e(TAG,"JSONException on email query","JSONException on email query",e);
				return null;
			}
			catch (IOException e)
			{
				Util.log("GTasksSetup: IOException on email query. "+e.getLocalizedMessage());
				return null;
			}
		}

		@Override
		protected void onPostExecute(JSONObject json) {
			_progressDialog.dismiss();
			if (json != null)
			{
				try
				{
					String tok = json.getString("access_token");
					String expire = json.getString("expires_in");
					String refresh = json.getString("refresh_token");
					String email = json.getString("email");
					Util.log("GTasksSetup: Access Token: "+tok);
					Util.log("GTasksSetup: Expires In: "+expire);
					Util.log("GTasksSetup: Refresh Token: "+refresh);
					Util.log("GTasksSetup: Email: "+email);

					// Update the database in order to finish setting up the account:
					if (_syncMode == INITIAL_SYNC)
					{
						// Set up the account in the database:
						UTLAccount acc = new UTLAccount();
						acc.name = _name;
						acc.current_token = tok;
						acc.sync_service = UTLAccount.SYNC_GOOGLE;
						acc.username = email;
						acc.refresh_token = refresh;
						acc.token_expiry = System.currentTimeMillis()+1000*Long.parseLong(expire);
						acc.protocol = GTasksInterface.PROTOCOL_OAUTH2;
						long id = _db.addAccount(acc);
						if (id == -1)
						{
							Util.popup(GTasksSetup.this, R.string.DbInsertFailed);
							return;
						}

						// Log this event:
						Util.logOneTimeEvent(GTasksSetup.this, "account_setup", 0, new String[] {"google","oauth2","other_account"});
					}
					else
					{
						// Sync mode is MERGE:
						_existingAccount.current_token = tok;
						_existingAccount.sync_service = UTLAccount.SYNC_GOOGLE;
						_existingAccount.username = email;
						_existingAccount.refresh_token = refresh;
						_existingAccount.token_expiry = System.currentTimeMillis()+1000*Long.parseLong(expire);
						_existingAccount.protocol = GTasksInterface.PROTOCOL_OAUTH2;
						if (!_db.modifyAccount(_existingAccount))
						{
							Util.popup(GTasksSetup.this, R.string.DbModifyFailed);
							return;
						}
					}

					displaySetupResult();
				}
				catch (JSONException e)
				{
					displaySetupFailure(getString(R.string.GTasks_not_responding));
				}
			}
			else
			{
				displaySetupFailure(getString(R.string.GTasks_not_responding));
			}
		}
	}

	private void dismissProgressDialog()
	{
		if (_progressDialog == null || !_progressDialog.isShowing())
			return;
		else
			_progressDialog.dismiss();
	}

	// Display a message saying account setup was successful, and finish account
	// setup processing.  This is for Google accounts that were set up at the system
	// level:
	@SuppressLint("NewApi")
	private void finishAccountSetup(String token)
	{
		if (_syncMode == INITIAL_SYNC)
		{
			// Set up the account in the database:
			UTLAccount acc = new UTLAccount();
			acc.name = _name;
			acc.current_token = token;
			acc.sync_service = UTLAccount.SYNC_GOOGLE;
			acc.username = _googleCredential.getSelectedAccountName();
			acc.protocol = GTasksInterface.PROTOCOL_DEVICE_ACCOUNT;
			long id = _db.addAccount(acc);
			if (id == -1)
			{
				Util.popup(this, R.string.DbInsertFailed);
				return;
			}
			Util.log("Google account setup complete. Name: "+_googleCredential.
                getSelectedAccountName());

			// Log this event:
			Util.logOneTimeEvent(this, "account_setup", 0, new String[] {"google","oauth2","device_account"});
		}
		else
		{
			// Sync mode is MERGE:
			_existingAccount.current_token = token;
			_existingAccount.sync_service = UTLAccount.SYNC_GOOGLE;
			_existingAccount.username = _googleCredential.getSelectedAccountName();
			_existingAccount.protocol = GTasksInterface.PROTOCOL_DEVICE_ACCOUNT;
			if (!_db.modifyAccount(_existingAccount))
			{
				Util.popup(this, R.string.DbModifyFailed);
				return;
			}
		}

		displaySetupResult();
	}

	private void displaySetupFailure(String message)
	{
		// Show the account setup result views:
		_resultContainer.setVisibility(View.VISIBLE);
		_choiceContainer.setVisibility(View.GONE);
		_setupResultMsg.setText(message+"\n");

		// Display the options to try again or cancel.
		_continueButton.setVisibility(View.GONE);
		_tryAgainButton.setVisibility(View.VISIBLE);
		_cancelButton.setVisibility(View.VISIBLE);

		// Show an Ad:
		initBannerAd();
	}

	private void displaySetupResult()
	{
		// Show the account setup result views:
		_resultContainer.setVisibility(View.VISIBLE);
		_choiceContainer.setVisibility(View.GONE);

		// Assemble the message to display:
		String successMsg = Util
			.getString(R.string.Synchronization_will_run_in_background)
			+ "\n\n" + Util.getString(R.string.GTasks_Folder_Mapping) + "\n";
		_setupResultMsg.setText(successMsg);

		// When a google account is in place, folders must be enabled:
		Util.updatePref(PrefNames.FOLDERS_ENABLED, true);

		// Display the buttons I want to see:
		_continueButton.setVisibility(View.VISIBLE);
		_tryAgainButton.setVisibility(View.GONE);
		_cancelButton.setVisibility(View.GONE);

		// Establish a link to the Synchronizer service:
		doBindService();

		// Start the initial sync:
		Intent i = new Intent(GTasksSetup.this, Synchronizer.class);
		i.putExtra("command", "full_sync");
		i.putExtra("send_percent_complete", true);
		Synchronizer.enqueueWork(this,i);

		// Show an Ad:
		initBannerAd();

		_state = SYNC_IN_PROGRESS;

		// Google requires that we have folders enabled:
		_settings.edit().putBoolean(PrefNames.FOLDERS_ENABLED,true).apply();
	}

	// Handler for pausing the activity (when we leave):
	@Override
	public void onPause()
	{
		Stats.uploadStats(this,null);

		super.onPause();

		// Remove the link to the synchronizer service:
		doUnbindService();
	}

	// Called before the activity is destroyed due to orientation change:
	@Override
	protected void onSaveInstanceState(Bundle b)
	{
		b.putInt("state", _state);
		b.putBoolean("is_first_account", _isFirstAccount);
		if (_state==SYNC_IN_PROGRESS)
			b.putInt("progress_bar", _progressBar.getProgress());
		if (_state==SYNC_DONE)
			b.putString("result_msg", _syncProgressMsg.getText().toString());
		super.onSaveInstanceState(b);
	}

	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler
	{
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
				case Synchronizer.SYNC_RESULT_MSG:
					int result = msg.arg1;
					if (result == Synchronizer.SUCCESS)
					{
						_syncProgressMsg.setText(R.string.Sync_Successful);
						_progressBar.setVisibility(View.INVISIBLE);
					}
					else
					{
						_syncProgressMsg.setText(Synchronizer
							.getFailureString(result));
						_progressBar.setVisibility(View.INVISIBLE);
					}
					_state = SYNC_DONE;
					break;

				case Synchronizer.PERCENT_COMPLETE_MSG:
					// Update the progress bar:
					int percentComplete = msg.arg1;
					_progressBar.setProgress((int) (_progressBar.getMax()
						* percentComplete / 100.0));
					break;

				default:
					super.handleMessage(msg);
			}
		}
	}

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection()
	{
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
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
			} catch (RemoteException e)
			{
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
			}
		}

		public void onServiceDisconnected(ComponentName className)
		{
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
		}
	};

	void doBindService()
	{
		// Establish a connection with the service. We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		if (!mIsBound)
		{
			bindService(new Intent(GTasksSetup.this, Synchronizer.class),
				mConnection, Context.BIND_AUTO_CREATE);
			mIsBound = true;
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
				} catch (RemoteException e)
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
}