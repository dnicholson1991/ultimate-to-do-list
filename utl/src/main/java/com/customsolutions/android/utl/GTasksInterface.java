package com.customsolutions.android.utl;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.format.Time;
import android.util.Base64;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GooglePlayServicesAvailabilityException;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAuthIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksRequestInitializer;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;
import com.google.api.services.tasks.model.TaskLists;

import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public class GTasksInterface
{
	//
	// Static Variables and Methods:
	//

	private static final String TAG = "GTasksInterface";

	// The available protocols.  Used by the "protocol" field in UTLAccount objects and the
	// database.
	public static final int PROTOCOL_DEVICE_ACCOUNT = 0;
	public static final int PROTOCOL_LOGIN_METHOD = 1;   // Being phased out as of version 3.2.
	public static final int PROTOCOL_OAUTH2 = 2;         // OAuth2 for account not defined in device settings.

	// Credentials used to access the Google Tasks API on devices without Google Play Services,
	// such as Amazon's Kindle Fire. These credentials are obtained using Google Cloud
	// (cloud.google.com).
	public static final String CLIENT_ID = "my_client_id";
	public static final String CLIENT_SECRET ="my_client_secret";
	public static final String API_KEY = "my_api_key";

	// URLs used by Google's interface:
	public static final String TOKEN_URL ="https://www.googleapis.com/oauth2/v3/token";

	// Return codes from the various functions:
	public static final int SUCCESS = 1;
	public static final int NEEDS_USER_PERMISSION = 2;
	public static final int NEEDS_NEW_TOKEN = 3;
	public static final int CONNECTION_FAILURE = 4;
	public static final int INTERNAL_ERROR = 5;
	public static final int GOOGLE_ACCOUNT_GONE = 6;
	public static final int MISC_ERROR = 7;
	public static final int DB_FAILURE = 8;
	public static final int NEEDS_NEW_SIGN_IN = 9000;
		// Using large number to distinguish from Toodleo errors.

	// These strings are used to mark the beginning and end of the encoded string
	// containing unsupported fields (which is stored in the task's note):
	private static final String UNSUPPORTED_FIELDS_START = "ULTIMATE TO-DO LIST DATA - "+
		"DO NOT EDIT BELOW THIS LINE!  THE FOLLOWING TEXT CONTAINS TASK DATA NOT SUPPORTED "+
		"BY GOOGLE.\n";
	private static final String UNSUPPORTED_FIELDS_END = "\nEND OF TEXT INSERTED BY "+
		"ULTIMATE TO-DO LIST\n";

	// Holds a text description of the last failure that occurred:
	static private String _lastErrorMsg;

	// Holds the HTTP error code of the last failure that occurred:
	static public int _lastHttpErrorCode;

	/** Holds an Intent that can call an Activity to fix authorization failures. Will only be
	 * non-null if the current failure code is NEEDS_USER_PERMISSION */
	static private Intent _authFixIntent = null;

	/** When sync fails because a sign-in is needed, this holds the account the user must
	 * sign into. */
	static public UTLAccount _accountToSignInto;

	/** Get a description of the last error that occurred: */
	static public String getErrorMessage(int errorCode)
	{
		switch (errorCode)
		{
			case CONNECTION_FAILURE:
				return Util.getString(R.string.GTasks_not_responding);
			case INTERNAL_ERROR:
				return Util.getString(R.string.Internal_error);
			case GOOGLE_ACCOUNT_GONE:
				return Util.getString(R.string.Google_Account_Gone);
			case MISC_ERROR:
				return _lastErrorMsg;
			case NEEDS_USER_PERMISSION:
				return Util.getString(R.string.sign_in_needed);
			case NEEDS_NEW_SIGN_IN:
				return Util.getString(R.string.google_sign_in_needed);
			default:
				return "Error Code "+errorCode;
		}
	}

	/** Fetch an Intent that can call an Activity to fix authorization failures.  Only applicable
	 * if the current failure code is NEEDS_USER_PERMISSION. Callers should check to make sure
	 * the Intent from this method is not null. */
	static Intent getAuthFixIntent()
	{
		return _authFixIntent;
	}

	/** Convert an exception into an error code.  Also sets _lastErrorMsg if necessary: */
	static public int parseException(Exception e)
	{
		_authFixIntent = null;

		if (e instanceof HttpResponseException)
		{
			HttpResponseException response = (HttpResponseException) e;
			_lastHttpErrorCode = response.getStatusCode();
			if (response.getStatusCode()==401)
			{
				Util.log("GTasksInterface: HTTP Error "+response.getStatusCode()+": "+response.getStatusMessage());
				Util.log("GTasksInterface: "+response.getContent());
				return NEEDS_NEW_TOKEN;
			}
			else
			{
				_lastErrorMsg = "HTTP Error "+response.getStatusCode()+": "+response.
					getStatusMessage();
				String errorMsg = _lastErrorMsg;
				Util.log("GTasksInterface: "+_lastErrorMsg);
				try
				{
					String httpContent = response.getContent();
					if (httpContent==null)
					{
						Log.e(TAG,"Null HTTP Content","Got HttpResponseException with null "+
							"content.");
						return MISC_ERROR;
					}
					Util.log("GTasksInterface: "+httpContent);
					errorMsg = "HTTP Error "+response.getStatusCode()+": "+httpContent;
					JSONObject json= (JSONObject)new JSONTokener(httpContent).nextValue();
					if (json.has("error"))
					{
						json = json.getJSONObject("error");
						if (json.has("message"))
						{
							_lastErrorMsg = "HTTP Error "+response.getStatusCode()+": "+
								json.getString("message");
						}
					}
				}
				catch (JSONException e3) { }
				catch (ClassCastException e4) { }
				if (response.getStatusCode()!=404)
					Log.e(TAG,"HTTP Error "+response.getStatusCode(),errorMsg);
				else
					Log.d(TAG,errorMsg);
				return MISC_ERROR;
			}
		}
		else if (e instanceof GooglePlayServicesAvailabilityIOException)
		{
			GooglePlayServicesAvailabilityIOException e2 = (GooglePlayServicesAvailabilityIOException) e;
			_lastErrorMsg = GooglePlayServicesUtil.getErrorString(e2.getConnectionStatusCode());
			Log.e(TAG,"GooglePlayServicesAvailabilityIOException",_lastErrorMsg,e);
			return MISC_ERROR;
		}
		else if (e instanceof GooglePlayServicesAvailabilityException)
		{
			GooglePlayServicesAvailabilityException e2 = (GooglePlayServicesAvailabilityException) e;
			_lastErrorMsg = GooglePlayServicesUtil.getErrorString(e2.getConnectionStatusCode());
			Log.e(TAG,"GooglePlayServicesAvailabilityException",_lastErrorMsg,e);
			return MISC_ERROR;
		}
		else if (e instanceof UserRecoverableAuthException || e instanceof UserRecoverableAuthIOException)
		{
			Util.log("GTasksInterface: "+e.getClass().getName()+": "+e.getMessage());
			if (e instanceof UserRecoverableAuthException)
			{
				UserRecoverableAuthException ur = (UserRecoverableAuthException) e;
				_authFixIntent = ur.getIntent();
			}
			else
			{
				UserRecoverableAuthIOException ur = (UserRecoverableAuthIOException) e;
				_authFixIntent = ur.getIntent();
			}
			return NEEDS_USER_PERMISSION;
		}
		else if (e instanceof GoogleAuthIOException || e instanceof GoogleAuthException)
		{
			_lastErrorMsg = e.getClass().getName()+": "+e.getMessage();
			Log.e(TAG,e.getClass().getName(),_lastErrorMsg,e);
			return MISC_ERROR;
		}
		else if (e instanceof IOException)
		{
			Util.log("GTasksInterface: IOException: "+e.getMessage());
			return CONNECTION_FAILURE;
		}
		else if (e instanceof IllegalArgumentException)
		{
			// This is thrown after the user restores tasks from a backup. The user needs to log
			// in again.
			if (e.getMessage()!=null && !e.getMessage().toLowerCase().contains("name must not " +
				"be empty"))
			{
				// The expected error message didn't appear. Either Google changed something or
				// we're here by mistake. I can't tell which.
				Log.e(TAG,"Unexpected IllegalArgumentException","Got an IllegalArgumentException "+
					"without expected message.",e);
			}
			return NEEDS_NEW_SIGN_IN;
		}
		else
		{
			_lastErrorMsg = e.getClass().getName()+": "+e.getMessage();
			Log.e(TAG,e.getClass().getName(),_lastErrorMsg,e);
			return MISC_ERROR;
		}
	}

	// This private class is used to store the access token for the login method and for OAUTH2
	// accounts not linked to a device account.
	private class TokenInserter implements HttpRequestInitializer, HttpExecuteInterceptor
	{
		private String _accessToken;

		public void initialize(HttpRequest request)
		{
			request.setInterceptor(this);
		}

		public void intercept(HttpRequest request)
		{
			request.getHeaders().setAuthorization("Bearer " + _accessToken);
		}

		public void setAccessToken(String newToken)
		{
			Util.log("GTasksInterface: Storing new access token: "+newToken);
			_accessToken = newToken;
		}
	}

	//
	// Instance variables and methods:
	//

	public UTLAccount _account;

	// Database Access:
	private AccountsDbAdapter _accountsDB;
	private FoldersDbAdapter _foldersDB;
	private ContextsDbAdapter _contextsDB;
	private GoalsDbAdapter _goalsDB;
	private LocationsDbAdapter _locDB;
	private TasksDbAdapter _tasksDB;

	// This object is responsible for storing the access token that Google requires:
	private TokenInserter _tokenHolder;

	// This object takes care of OAUTH2 authorization and token management for accounts on the
	// device.
	private GoogleAccountCredential _googleCredential;

	// This object provides high-level access to the tasks API:
	private Tasks _service;

	// The Context object:
	private Context _context;

	// The home time zone:
	private String _homeTimeZone;

	// The constructor, which links the new instance with a UTL Account
	public GTasksInterface(Context context, UTLAccount acc)
	{
		_account = acc;

		HttpTransport transport = AndroidHttp.newCompatibleTransport();

		// Get a reference to the Google account:
		if (_account.protocol==PROTOCOL_DEVICE_ACCOUNT)
		{
			_googleCredential = GoogleAccountCredential.usingOAuth2(context, Collections.
				singleton(TasksScopes.TASKS)).setBackOff(new ExponentialBackOff());
			_service = new Tasks.Builder(transport,JacksonFactory.getDefaultInstance(),
				_googleCredential).build();
			Util.log("GTasksInterface: Using account name: "+acc.username);
			_googleCredential.setSelectedAccountName(acc.username);
		}
		else
		{
			// OAUTH2 Account.
			_tokenHolder = new TokenInserter();
			_tokenHolder.setAccessToken(acc.current_token);
			String apiKey = API_KEY;
			_service = new Tasks.Builder(transport, JacksonFactory.getDefaultInstance(), _tokenHolder)
				.setTasksRequestInitializer(new TasksRequestInitializer(apiKey))
				.build();
		}

		// Store the context for use later:
		_context = context;

		// Get references to the database tables:
		_accountsDB = new AccountsDbAdapter();
		_foldersDB = new FoldersDbAdapter();
		_contextsDB = new ContextsDbAdapter();
		_goalsDB = new GoalsDbAdapter();
		_locDB = new LocationsDbAdapter();
		_tasksDB = new TasksDbAdapter();

		// Get the home time zone:
		SharedPreferences settings = _context.getSharedPreferences("UTL_Prefs",0);
		_homeTimeZone = settings.getString("home_time_zone","America/Los_Angeles");

	}

	// Refresh the token.  Returns SUCCESS or a failure code.
	@SuppressWarnings("deprecation")
	private int refreshToken()
	{
		if (_account.protocol==PROTOCOL_DEVICE_ACCOUNT)
		{
			// Access the Google account to get a new token:
			try
			{
				String newToken = _googleCredential.getToken();
				Util.log("GTasksInterface: Got new Google account token: "+newToken);

				// Store the new token in the account:
				_account = _accountsDB.getAccount(_account._id);
				_account.current_token = newToken;
				_accountsDB.modifyAccount(_account);
			}
			catch (Exception e)
			{
				return parseException(e);
			}
		}
		else if (_account.protocol==PROTOCOL_OAUTH2)
		{
			// Use a HTTPS request to request a new token from Google:
			Util.log("GTasksInterface: Requesting new token for OAUTH2 account...");
			try
			{
				HashMap<String,String> params = new HashMap<>();
				params.put("refresh_token", _account.refresh_token);
				params.put("client_id", CLIENT_ID);
				params.put("client_secret", CLIENT_SECRET);
				params.put("grant_type", "refresh_token");

				HttpResponseInfo result = Util.httpsPost(TOKEN_URL,params,null,null);
				Util.log("GTasksInterface: Token Refresh Response: "+result.responseMessage);
				Util.log("GTasksInterface: Token Refresh Response: "+result.text);
				JSONObject json = new JSONObject(result.text);
				_account.current_token = json.getString("access_token");
				_account.token_expiry = System.currentTimeMillis() + 1000*json.getLong("expires_in");
				_accountsDB.modifyAccount(_account);

				// Update the token used by the service instance:
				_tokenHolder.setAccessToken(_account.current_token);
			}
			catch (Exception e)
			{
				return parseException(e);
			}
		}

		// If we make it here, it was successful:
		return SUCCESS;
	}

	/**
	 * Get folders (TaskLists).
	 * @param folders - This is populated with the parameters for each folder.  If no folders
	 *     are placed here, then there are no folders in the account.
	 * @param gTimestamp - This object is modified to hold the timestamp at Google's server.
	 * @param eTag - This StringBuilder object is altered to hold the eTag returned by Google.
	 *     The eTag can be used to determine if anything has changed in the account since the
	 *     last sync.  The passed-in StringBuilder object should have nothing in it.
	 * @return - SUCCESS or an error code.
	 */
	public int getFolders(ArrayList<HashMap<String,String>> folders, AtomicLong gTimestamp,
						  StringBuilder eTag)
	{
		TaskLists lists;
		long num = 100; // Google allows no more than 100 at once.
		String pageToken = "";
		boolean gotTimestamp = false;
		HttpResponse r;
		while (true)
		{
			int tryCount = 0;
			while (true)
			{
				try
				{
					Tasks.Tasklists.List op = _service.tasklists().list();
					op.setMaxResults(num);
					if (pageToken.length()>0)
						op.setPageToken(pageToken);
					r = op.executeUnparsed();
					Util.log("Status Code: "+r.getStatusCode()+"; Message: "+r.getStatusMessage());
					lists = r.parseAs(TaskLists.class);
					break;
				}
				catch (Exception e)
				{
					int errorCode = parseException(e);
					if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
					{
						// Get a new token and try the operation again:
						tryCount++;
						int refreshCode = refreshToken();
						if (refreshCode!=SUCCESS)
							return refreshCode;
						else
							continue;
					}
					else
						return errorCode;
				}
			}

			// If we get here, the operation was successful.

			// See if we can get a timestamp from the response:
			if (r.getHeaders()!=null)
			{
				HttpHeaders headers = r.getHeaders();
				if (headers.getDate() != null)
				{
					try
					{
						Date parsedDate = DateUtils.parseDate(headers.getDate(), new String[] {
							DateUtils.PATTERN_RFC1123, DateUtils.PATTERN_RFC1036,
							DateUtils.PATTERN_ASCTIME
						},	null);
						gTimestamp.set(parsedDate.getTime());
						gotTimestamp = true;
					}
					catch (DateParseException e)
					{
						Util.log("GTasksInterface: Got bad date from Google's server: "+headers.
							getDate());
					}
				}
			}

			// Append the eTag for this page of results to the overall eTag.
			if (pageToken.length()>0)
				eTag.append("/");
			eTag.append(lists.getEtag());

			// Convert the returned structure into an ArrayList of HashMaps.
			List<TaskList> items = lists.getItems();
			Iterator<TaskList> it = items.iterator();
			while (it.hasNext())
			{
				TaskList taskList = it.next();

				// Ignore taskslists / folders with no title:
				if (taskList.getTitle()==null || taskList.getTitle().length()==0)
					continue;

				HashMap<String,String> map = new HashMap<String,String>();
				map.put("id", taskList.getId());
				map.put("title", taskList.getTitle());
				folders.add(map);
			}

			// Download the next batch, or break out of the loop if we're done:
			if (lists.getNextPageToken()!=null && lists.getNextPageToken().length()>0)
			{
				// We have more to download:
				pageToken = lists.getNextPageToken();
				continue;
			}
			else
			{
				// We're done.  Move on to the next batch:
				break;
			}
		}

		if (!gotTimestamp)
		{
			Log.e(TAG,"Missing Timestamp","Could not get timestamp from Google's server.");
			gTimestamp.set(System.currentTimeMillis());
		}

		return SUCCESS;
	}

	// Add a folder (Tasklist). Input is a database cursor pointing to the folder.
	// Returns a result code.  This also updates the database with the folder's remote
	// ID.
	public int addFolder(Cursor c)
	{
		int tryCount = 0;
		TaskList result;
		while (true)
		{
			try
			{
				TaskList taskList = new TaskList();
				taskList.setKind("tasks#taskList");
				taskList.setTitle(Util.cString(c, "title"));
				Tasks.Tasklists.Insert op = _service.tasklists().insert(taskList);
				result = op.execute();
				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.

		// Store the Google ID of the TaskList:
		boolean isSuccessful = _foldersDB.modifyRemoteID(Util.cLong(c,"_id"),
			result.getId());
		if (!isSuccessful)
		{
			Util.log("GTasksInterface: DB modification failed when changing folder's remote ID.");
			_lastErrorMsg = Util.getString(R.string.DbModifyFailed);
			return MISC_ERROR;
		}

		return SUCCESS;
	}

	// Modify a folder (Tasklist). Input is a database cursor pointing to the folder.
	// Returns a result code.
	public int modifyFolder(Cursor c)
	{
		int tryCount = 0;
		while (true)
		{
			try
			{
				TaskList taskList = new TaskList();
				taskList.setKind("tasks#taskList");
				taskList.setTitle(Util.cString(c, "title"));
				taskList.setId(Util.cString(c, "remote_id"));
				Tasks.Tasklists.Update op = _service.tasklists().update(taskList.getId(), taskList);
				op.execute();
				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.
		return SUCCESS;
	}

	// Delete a folder (Tasklist).  Input is Google's tasklist ID:
	public int deleteFolder(String googleID)
	{
		int tryCount = 0;
		while (true)
		{
			try
			{
				Tasks.Tasklists.Delete op = _service.tasklists().delete(googleID);
				op.execute();
				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.
		return SUCCESS;
	}

	// Given a UTLTask object, generate a JSON string that contains all of the fields
	// not supported by Google Tasks:
	private String encodeUnsupportedFields(UTLTask t)
	{
		JSONObject json = new JSONObject();
		try
		{
			String name;
			Cursor c;
			if (t.context_id>0)
			{
				c = _contextsDB.getContext(t.context_id);
				if (c.moveToFirst())
					json.put("context_name", Util.cString(c, "title"));
			}
			if (t.goal_id>0)
			{
				c = _goalsDB.getGoal(t.goal_id);
				if (c.moveToFirst())
					json.put("goal_name", Util.cString(c, "title"));
			}
			if (t.location_id>0)
			{
				UTLLocation loc = _locDB.getLocation(t.location_id);
				if (loc!=null)
					json.put("location_name", loc.title);
			}
			json.put("due_modifier", t.due_modifier);
			json.put("uses_due_time", t.uses_due_time);
			json.put("due_time", t.due_date);
			json.put("reminder", t.reminder);
			json.put("start_date", t.start_date);
			json.put("uses_start_time",t.uses_start_time);
			json.put("repeat",t.repeat);
			json.put("nag",t.nag);
			json.put("rep_advanced", t.rep_advanced);
			json.put("status", t.status);
			json.put("length", t.length);
			json.put("priority", t.priority);
			json.put("star", t.star);
			json.put("timer", t.timer);
			json.put("timer_start_time", t.timer_start_time);
			json.put("location_reminder", t.location_reminder);
			json.put("location_nag", t.location_nag);
			json.put("new_task_generated",t.new_task_generated);
			json.put("sort_order",t.sort_order);
			if (t.contactLookupKey!=null)
				json.put("contact", t.contactLookupKey);
			if (Util.isValid(t.uuid))
				json.put("uuid",t.uuid);

			// Add in tags:
			String[] tagList = (new TagsDbAdapter()).getTags(t._id);
			String tagStr = "";
			for (int i=0; i<tagList.length; i++)
			{
				tagStr += ","+tagList[i];
			}
			if (tagStr.length()>0)
				json.put("tags", tagStr.substring(1));

			// Add in a time zone, to ensure proper time display when downloading later:
			json.put("time_zone", Util.settings.getString("home_time_zone",
				"America/Los_Angeles"));

			return json.toString();
		}
		catch (JSONException e)
		{
			// Huh?!
			Util.log("GTasksInterface: Got JSONException in encodeUnsupportedFields: "+e.getMessage());
			return "";
		}
	}

	// Given a UTLTask object and a JSON string, decode the string and modify the UTLTask
	// object.  This also modifies a GregorianCalendar object so that it contains
	// the stored due date/time (which may differ from Google's stored due date).
	private void decodeUnsupportedFields(UTLTask t, String jsonString,
										 GregorianCalendar cal, StringBuilder tags)
	{
		Cursor c;
		try
		{
			JSONObject json = new JSONObject(jsonString);

			// If the task has a context, see if we have a matching one on this device:
			if (json.has("context_name"))
			{
				c = _contextsDB.queryContexts("lower(title)='"+Util.
					makeSafeForDatabase(json.getString("context_name").toLowerCase())+
					"' and account_id="+_account._id, "title");
				if (c.moveToFirst())
					t.context_id = Util.cLong(c, "_id");
				c.close();
			}

			// If the task has a goal, see if we have a matching one on this device:
			if (json.has("goal_name"))
			{
				c = _goalsDB.queryGoals("lower(title)='"+Util.
					makeSafeForDatabase(json.getString("goal_name").toLowerCase())+
					"' and account_id="+_account._id, "title");
				if (c.moveToFirst())
					t.goal_id = Util.cLong(c, "_id");
				c.close();
			}

			// If the task has a location, see if we have a matching one on this device:
			if (json.has("location_name"))
			{
				c = _locDB.queryLocations("lower(title)='"+Util.
					makeSafeForDatabase(json.getString("location_name").toLowerCase())+
					"' and account_id="+_account._id, "title");
				if (c.moveToFirst())
					t.location_id = Util.cLong(c, "_id");
				c.close();
			}

			// Get the time zone used for the task's dates and times, and calculate an
			// offset to convert the times to our own time zone:
			long zoneOffset = 0;
			if (json.has("time_zone"))
			{
				TimeZone downloadedZone = TimeZone.getTimeZone(json.getString("time_zone"));
				TimeZone homeZone = TimeZone.getTimeZone(Util.settings.getString(
					"home_time_zone", "America/Los_Angeles"));
				zoneOffset = homeZone.getOffset(System.currentTimeMillis()) -
					downloadedZone.getOffset(System.currentTimeMillis());
			}

			// Get remaining fields:
			t.due_modifier = json.getString("due_modifier");
			t.uses_due_time = json.getBoolean("uses_due_time");
			if (json.getLong("due_time")>0)
				cal.setTimeInMillis(json.getLong("due_time")-zoneOffset);
			else
				cal.setTimeInMillis(0);
			if (json.getLong("reminder")>0)
				t.reminder = json.getLong("reminder")-zoneOffset;
			else
				t.reminder = 0;
			if (json.getLong("start_date")>0)
				t.start_date = json.getLong("start_date")-zoneOffset;
			else
				t.start_date = 0;
			t.uses_start_time = json.getBoolean("uses_start_time");
			t.repeat = json.getInt("repeat");
			t.nag = json.getBoolean("nag");
			t.rep_advanced = json.getString("rep_advanced");
			t.status = json.getInt("status");
			t.length = json.getInt("length");
			t.priority = json.getInt("priority");
			t.star = json.getBoolean("star");
			t.timer = json.getLong("timer");
			t.timer_start_time = json.getLong("timer_start_time");
			t.location_reminder = json.getBoolean("location_reminder");
			t.location_nag = json.getBoolean("location_nag");
			t.new_task_generated = json.getBoolean("new_task_generated");
			if (json.has("contact"))
				t.contactLookupKey = json.getString("contact");
			if (json.has("sort_order"))
				t.sort_order = json.getLong("sort_order");
			if (json.has("uuid"))
				t.uuid = json.getString("uuid");

			// Extract tags:
			if (json.has("tags"))
				tags.append(json.getString("tags"));
		}
		catch(JSONException e)
		{
			// The user may have messed with the string, so ignore it.
			Util.log("GTasksInterface: Got invalid JSON String: "+jsonString);
		}
	}

	// Convert a timestamp from Google to ms.
	private long gTimeToMillis(String gTime)
	{
		Time time = new Time(_homeTimeZone);
		time.parse3339(gTime);
		return time.toMillis(false);
	}

	// Same as above, but strip out any time component:
	private long gDateToMillis(String gTime)
	{
		String split[] = gTime.split("T");
		Time time = new Time(_homeTimeZone);
		time.parse3339(split[0]);
		return time.toMillis(false);
	}

	// Convert milliseconds to Google's time format:
	private DateTime millisToGTime(long millis)
	{
		return new DateTime(new Date(millis),TimeZone.getTimeZone(_homeTimeZone));
	}

	// Convert milliseconds to a Google date (no time component):
	private DateTime millisToGDate(long millis)
	{
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		dateFormat.setTimeZone(TimeZone.getTimeZone(_homeTimeZone));
		String formattedDate = dateFormat.format(new Date(millis));
		return DateTime.parseRfc3339(formattedDate + "T00:00:00.000Z");
	}

	// Get tasks.  Inputs are:
	// modAfter - get tasks modified after this time (in ms). Set to 0 to get all tasks.
	// taskList - An ArrayList of UTLTasks objects that will be filled in
	// tagList - An ArrayList of strings, in the same order as taskList
	// HashSet<String> deletedTaskHash - A HashSet of Strings.  Stores the remote ID of
	//     tasks that Google has reported as deleted.
	// sendPercentComplete - Set to true if percent complete messages should be sent
	// The remaining arguments are used for the percent complete messages.
	// A result code is returned.
	public int getTasks(long modAfter, ArrayList<UTLTask> taskList, ArrayList<String>
		tagList, HashSet<String> deletedTaskHash, boolean sendPercentComplete,
		int totalPercentToAdd, int basePercent, ArrayList<Messenger> clients)
	{
		// For some strange reason, Google does not allow us to download tasks from
		// more than one folder/tasklist at once.  So, we need to loop through all of
		// the folders for the account:
		Cursor c = _foldersDB.queryFolders("account_id="+_account._id, null);
		c.moveToPosition(-1);
		Float numTaskLists = new Float(c.getCount());
		Float percentPerTaskList = new Float(totalPercentToAdd) / numTaskLists;
		Float taskListNum = new Float(0);
		while (c.moveToNext())
		{
			String folderID = Util.cString(c, "remote_id");
			if (folderID==null || folderID.length()==0)
				continue;

			// Google has a nasty bug in which it won't update the modification date for all
			// reordered and updated tasks if a task is moved.  It only updates the modification
			// date of the task the user drug with the mouse, even though this can result in
			// the position field of other tasks being affected.  So, if any task in the folder
			// is updated, we need to download the entire folder.
			// See https://issuetracker.google.com/issues/137515143
			boolean needWholeFolder = false;

			long num = 100;  // Google allows no more than 100 at once.
			String pageToken = "";
			while (true)
			{
				// Download and process a batch of tasks:
				int tryCount = 0;
				com.google.api.services.tasks.model.Tasks tasks = null;
				boolean folderNotFound = false;
				while (true)
				{
					try
					{
						Tasks.TasksOperations.List op = _service.tasks().list(folderID);
						if (modAfter>0 && !needWholeFolder)
							op.setUpdatedMin(new DateTime(modAfter).toStringRfc3339());
						op.setMaxResults(num);
						op.setShowHidden(true);
						op.setShowDeleted(true);
						op.setShowCompleted(true);
						if (pageToken.length()>0)
							op.setPageToken(pageToken);
						tasks = op.execute();
						break;
					}
					catch (Exception e)
					{
						// If we get a 404 error (not found), this means that the tasklist
						// has been deleted from Google.  In this case, we just move on to
						// the next one.
						if (e instanceof HttpResponseException)
						{
							HttpResponseException re = (HttpResponseException) e;
							if (re.getStatusCode()==404)
							{
								folderNotFound = true;
								break;
							}

							// If we get a 503 error (Backend Error), this means that Google's
							// servers are having trouble.  Retry.
							if (re.getStatusCode()==503 && tryCount<3)
							{
								Util.log("GTasksInterface: Retrying this folder ID due to 503 " +
									"error: " + folderID + " / " + Util.cString(c, "title"));
								tryCount++;
								try
								{
									Thread.sleep(1000);
								}
								catch (InterruptedException e2)
								{

								}
								continue;
							}
						}

						int errorCode = parseException(e);
						if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
						{
							// Get a new token and try the operation again:
							tryCount++;
							int refreshCode = refreshToken();
							if (refreshCode!=SUCCESS)
								return refreshCode;
							else
								continue;
						}
						else
							return errorCode;
					}
				}

				if (folderNotFound)
					break;

				// If we downloaded any tasks, and weren't already downloading the whole folder,
				// then we need to download the whole folder, due to the Google bug mentioned
				// above.
				if (tasks.getItems()!=null && tasks.getItems().size()>0 && modAfter>0 &&
					!needWholeFolder)
				{
					Log.d(TAG,"A task was updated in the folder "+Util.cString(c, "title")+
						". Downloading entire folder due to Google bug.");
					needWholeFolder = true;
					continue;
				}

				// At this point, we successfully downloaded a batch.  Add them to the
				// list of tasks to return:
				if (tasks.getItems()==null)
					tasks.setItems(new ArrayList<Task>());
				Iterator<Task> it = tasks.getItems().iterator();
				while (it.hasNext())
				{
					Task gTask = it.next();

					// Ignore tasks with no title:
					if (gTask.getTitle()==null || gTask.getTitle().length()==0)
						continue;

					// If the task has been deleted, then store it in the hash of
					// deleted tasks:
					if (gTask.getDeleted()!=null && gTask.getDeleted()==true)
					{
						deletedTaskHash.add(gTask.getId());
						continue;
					}

					// Pull out the unsupported fields from the task's note (if they're
					// there):
					String unsupportedEncoded = "";
					if (gTask.getNotes()!=null)
					{
						int unsupportedStart = gTask.getNotes().indexOf(UNSUPPORTED_FIELDS_START);

						if (unsupportedStart>-1)
						{
							// The task has the unsupported fields (assuming we can find the
							// ending string).
							int trimStart = unsupportedStart;
							unsupportedStart += UNSUPPORTED_FIELDS_START.length();
							int unsupportedEnd = gTask.getNotes().indexOf(UNSUPPORTED_FIELDS_END,
								unsupportedStart);
							if (unsupportedEnd>-1)
							{
								// Store the string containing unsupported fields, and
								// strip it out of the notes:
								unsupportedEncoded = gTask.getNotes().substring(unsupportedStart,
									unsupportedEnd);
								gTask.setNotes(gTask.getNotes().substring(0, trimStart) + gTask.
									getNotes().substring(unsupportedEnd+UNSUPPORTED_FIELDS_END.length()));
								gTask.setNotes(gTask.getNotes().trim());
							}
						}
					}

					// Fill in the supported fields:
					UTLTask utlTask = new UTLTask();
					utlTask.account_id = _account._id;
					utlTask.mod_date = gTimeToMillis(gTask.getUpdated().toStringRfc3339());
					utlTask.sync_date = System.currentTimeMillis();
					utlTask.title = gTask.getTitle();
					if (gTask.getCompleted()==null)
						utlTask.completed = false;
					else
					{
						utlTask.completed = true;
						utlTask.completion_date = gTimeToMillis(gTask.getCompleted().toStringRfc3339());
					}
					utlTask.folder_id = Util.cLong(c, "_id");
					utlTask.prev_folder_id = utlTask.folder_id;
					if (gTask.getDue()!=null)
						utlTask.due_date = gDateToMillis(gTask.getDue().toStringRfc3339());
					else
						utlTask.due_date = 0;
					if (gTask.getNotes()!=null)
						utlTask.note = gTask.getNotes();
					else
						utlTask.note = "";
					utlTask.remote_id = gTask.getId();
					if (gTask.getPosition()!=null)
						utlTask.position = gTask.getPosition();
					else
						utlTask.position = "";

					// Link the task to its parent (if applicable):
					if (gTask.getParent()!=null && gTask.getParent().length()>0)
					{
						// Need to find the corresponding UTL parent task:
						Cursor c2 = _tasksDB.queryTasks("account_id="+_account._id+
								" and remote_id='"+Util.makeSafeForDatabase(gTask.getParent())+"'",
							null);
						if (c2.moveToFirst())
							utlTask.parent_id = Util.cLong(c2, "_id");
						else
						{
							// Parent hasn't yet been stored in the DB.  Modify the note
							// so that it can be linked later:
							utlTask.note = "Needs Parent: "+gTask.getParent()+"\n"+utlTask.note;
						}
					}
					utlTask.prev_parent_id = utlTask.parent_id;

					// Fill in the unsupported fields (if available):
					if (unsupportedEncoded.length()>0)
					{
						GregorianCalendar dueTime = new GregorianCalendar(TimeZone.
							getTimeZone(_homeTimeZone));
						StringBuilder tags = new StringBuilder();
						decodeUnsupportedFields(utlTask,unsupportedEncoded,dueTime,tags);

						// If the task has a due time, we need to combine the due time with
						// the due date:
						if (utlTask.uses_due_time)
						{
							GregorianCalendar due = new GregorianCalendar(TimeZone.
								getTimeZone(_homeTimeZone));
							due.setTimeInMillis(utlTask.due_date); // Midnight on due date:
							due.set(Calendar.HOUR_OF_DAY,dueTime.get(Calendar.HOUR_OF_DAY));
							due.set(Calendar.MINUTE,dueTime.get(Calendar.MINUTE));
							utlTask.due_date = due.getTimeInMillis();
						}

						// Add to the list of tags. Something must be added to keep
						// this list in sync with taskList.
						if (tags.length()>0)
							tagList.add(tags.toString());
						else
							tagList.add("");
					}
					else
						tagList.add("");

					// The UTLTask has been created.  Add it to the list:
					taskList.add(utlTask);
				}

				// Download the next batch, or break out of the loop if we're done:
				if (tasks.getItems().size()==num && tasks.getNextPageToken()!=null &&
					tasks.getNextPageToken().length()>0)
				{
					// We have more to download:
					pageToken = tasks.getNextPageToken();
					continue;
				}
				else
				{
					// We're done.  Move on to the next tasklist:
					break;
				}
			} // Ends loop which downloads batches

			if (sendPercentComplete)
			{
				// A tasklist is done.  Update the percent complete:
				taskListNum += new Float(1);
				Float newPercent = new Float(basePercent) + taskListNum*percentPerTaskList;
				for (int j=clients.size()-1; j>=0; j--)
				{
					try
					{
						clients.get(j).send(Message.obtain(null,Synchronizer.
							PERCENT_COMPLETE_MSG,newPercent.intValue(),0));
					}
					catch (RemoteException e)
					{
						// Just ignore
					}
				}
			}
		} // Ends loop which downloads for each tasklist/folder

		// If we get here, then we're successful:
		return SUCCESS;
	}

	// Add a task.  If the task is successfully added, then the UTLTask instance passed
	// in is updated to include the Google ID, and database is also updated.
	// A result code is returned.
	public int addTask(UTLTask utlTask)
	{
		// Create a Google Task Object and fill in the fields from the UTLTask:
		Task gTask = new Task();
		if (utlTask.completed)
		{
			gTask.setCompleted(millisToGDate(utlTask.completion_date));
			gTask.setStatus("completed");

		}
		else
			gTask.setStatus("needsAction");
		if (utlTask.due_date>0)
			gTask.setDue(millisToGDate(utlTask.due_date));
		gTask.setKind("tasks#task");
		gTask.setNotes(utlTask.note);
		if (utlTask.parent_id>0)
		{
			// Look up the UTLTask of the parent:
			UTLTask parent = _tasksDB.getTask(utlTask.parent_id);
			if (parent!=null && parent.remote_id!=null && parent.remote_id.length()>0)
				gTask.setParent(parent.remote_id);
			else
				Util.log("GTasksInterface: Tried to upload a task with missing parent in addTask().");
		}
		gTask.setTitle(utlTask.title);
		gTask.setUpdated(millisToGTime(utlTask.mod_date));
		String encoded = encodeUnsupportedFields(utlTask);
		gTask.setNotes(gTask.getNotes() + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n" +
			UNSUPPORTED_FIELDS_START + encoded + UNSUPPORTED_FIELDS_END);

		// Get the folder/tasklist of the task being uploaded:
		String tasklist = "@default";
		if (utlTask.folder_id==0)
		{
			Log.e(TAG,"Missing Folder in Upload","Uploaded task is missing folder in addTask()");
		}
		else
		{
			Cursor c = _foldersDB.getFolder(utlTask.folder_id);
			if (c.moveToFirst())
				tasklist = Util.cString(c, "remote_id");
			else
				Util.log("GTasksInterface: Uploaded task references nonexistent folder in addTask()");
		}

		int tryCount = 0;
		Task result = null;
		String savedParent = "";
		while (true)
		{
			try
			{
				if (result==null)
				{
					Tasks.TasksOperations.Insert op = _service.tasks().insert(tasklist,
						gTask);
					if (gTask.getParent() != null && gTask.getParent().length() > 0)
						op.setParent(gTask.getParent());
					result = op.execute();
				}

				// We also need to issue a move command to place the task in the correct position.
				// Start by initializing the move operation itself:
				Tasks.TasksOperations.Move moveOp = _service.tasks().move(tasklist,result.getId());
				if (utlTask.parent_id>0)
				{
					UTLTask parent = _tasksDB.getTask(utlTask.parent_id);
					if (parent!=null && parent.remote_id!=null && parent.remote_id.length()>0)
						moveOp.setParent(parent.remote_id);
				}

				// Look up which task is the previous one, if any.
				// 7/19/19: Due to a bug on Google's side, the previous task has to be an
				// incomplete one.
				Cursor c = _tasksDB.queryTasks("folder_id="+utlTask.folder_id+" and parent_id="+
					utlTask.parent_id+" and sort_order>"+utlTask.sort_order+" and completed=0",
					"sort_order asc");
				if (c.moveToFirst())
				{
					// A task was found in front of this one.  Upload the info to Google:
					UTLTask prevTask = _tasksDB.getUTLTask(c);
					c.close();
					if (prevTask.remote_id!=null && prevTask.remote_id.length()>0)
					{
						Util.log("GTasksInterface: Uploaded added task "+utlTask.title+" after "+
							prevTask.title);
						moveOp.setPrevious(prevTask.remote_id);
						utlTask.is_moved = false;
						try
						{
							result = moveOp.execute();
						}
						catch (Exception e)
						{
							Log.d(TAG,"Cannot move new task to designated location. Putting it " +
								"at the top of the list.",e);
							utlTask.setSortOrderToTop();
						}
					}
					else
					{
						// The previous task has not been uploaded to Google yet.  Set the
						// is_moved flag so that it can be tried at the next sync.
						utlTask.is_moved = true;
						utlTask.prev_task_id = -1;
					}
				}
				else
					c.close();

				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else if (errorCode==MISC_ERROR && _lastHttpErrorCode==404 && tryCount==0 &&
					gTask.getParent()!=null && gTask.getParent().length()>0)
				{
					// It is possible that the task is referencing a nonexistent parent.
					// Delete the parent reference and try again:
					Util.log("GTasksInterface: Removing parent from task and trying again.  Title: "+
						gTask.getTitle());
					savedParent = gTask.getParent();
					gTask.setParent("");
					tryCount++;
					continue;
				}
				else if (errorCode==MISC_ERROR && _lastHttpErrorCode==404 && (tryCount==0 ||
					tryCount==1) && (gTask.getParent()==null || gTask.getParent().length()==0))
				{
					// Perhaps the folder is nonexistent. Change it and try one more time.
					Util.log("GTasksInterface: Using the default folder and trying again.  Title: "+
						gTask.getTitle());
					tasklist = "@default";
					if (savedParent.length()>0) gTask.setParent(savedParent);
					tryCount++;
					continue;
				}
				else if (errorCode==MISC_ERROR && tryCount<20 && _lastErrorMsg.startsWith("HTTP " +
					"Error 403"))
				{
					// We are sending updates to Google faster than they would like.  Sleep for a bit and
					// try again.
					Util.log("GTasksInterface: Got a 'Quota Exceeded' message from Google.  Sleeping "+
						"for 2 seconds and then trying again. Try count: "+tryCount);
					try
					{
						Thread.sleep(2000);
					}
					catch (InterruptedException e2) { }
					tryCount++;
					continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.

		// Store the Google ID and position of the task:
		utlTask.remote_id = result.getId();
		utlTask.position = result.getPosition();
		boolean isSuccessful = _tasksDB.modifyTask(utlTask);
		if (!isSuccessful)
		{
			Util.log("GTasksInterface: DB modification failed when changing task's remote ID.");
			_lastErrorMsg = Util.getString(R.string.DbModifyFailed);
			return MISC_ERROR;
		}

		return SUCCESS;
	}

	// Edit a task.  A result code is returned.
	public int editTask(UTLTask utlTask)
	{
		// Create a Google Task Object and fill in the fields from the UTLTask:
		Task gTask = new Task();
		gTask.setId(utlTask.remote_id);
		if (utlTask.completed)
		{
			gTask.setCompleted(millisToGTime(utlTask.completion_date));
			gTask.setStatus("completed");
		}
		else
			gTask.setStatus("needsAction");
		if (utlTask.due_date>0)
			gTask.setDue(millisToGDate(utlTask.due_date));
		gTask.setKind("tasks#task");
		gTask.setNotes(utlTask.note);
		if (utlTask.parent_id>0)
		{
			// Look up the UTLTask of the parent:
			UTLTask parent = _tasksDB.getTask(utlTask.parent_id);
			if (parent!=null && parent.remote_id!=null && parent.remote_id.length()>0)
				gTask.setParent(parent.remote_id);
			else
			{
				logInternalError("Tried to upload a task with missing parent in editTask().");
				return INTERNAL_ERROR;
			}
		}
		gTask.setTitle(utlTask.title);
		gTask.setUpdated(millisToGTime(utlTask.mod_date));
		String encoded = encodeUnsupportedFields(utlTask);
		gTask.setNotes(gTask.getNotes() + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"+
			UNSUPPORTED_FIELDS_START+encoded+UNSUPPORTED_FIELDS_END);

		// Get the folder/tasklist of the task being uploaded:
		String tasklist = "@default";
		if (utlTask.folder_id==0)
		{
			Log.e(TAG,"Uploaded Task Missing Folder","Uploaded task is missing folder in " +
				"editTask()");
		}
		else
		{
			Cursor c = _foldersDB.getFolder(utlTask.folder_id);
			if (c.moveToFirst())
				tasklist = Util.cString(c, "remote_id");
			else
				Util.log("GTasksInterface: Uploaded task references nonexistent folder in editTask()");
		}

		int tryCount = 0;
		boolean deleteOpFailed = false;
		while (true)
		{
			try
			{
				// The exact operations we perform depends on what has changed in the task:
				boolean folderMoved = false;
				if (utlTask.folder_id != utlTask.prev_folder_id)
				{
					// The folder has changed, so we need to delete and recreate the task.
					folderMoved = true;

					// Begin by getting the old tasklist ID:
					Cursor c = _foldersDB.getFolder(utlTask.prev_folder_id);
					String oldTasklist = null;
					if (c.moveToFirst())
						oldTasklist = Util.cString(c, "remote_id");
					c.close();

					// Save the old task ID:
					String oldTaskID = gTask.getId();

					// Create a new task in the new tasklist:
					gTask.setId(null);
					Tasks.TasksOperations.Insert op = _service.tasks().insert(tasklist,
						gTask);
					if (gTask.getParent()!=null && gTask.getParent().length()>0)
						op.setParent(gTask.getParent());

					// Look up which task is the previous one, if any.
					c = _tasksDB.queryTasks("folder_id="+utlTask.folder_id+" and parent_id="+
						utlTask.parent_id+" and sort_order>"+utlTask.sort_order,"sort_order asc");
					if (c.moveToFirst())
					{
						// Tell Google what the previous task is.
						UTLTask prevTask = _tasksDB.getUTLTask(c);
						c.close();
						if (prevTask.remote_id!=null && prevTask.remote_id.length()>0)
						{
							Util.log("GTasksInterface: Uploading Edited Task " + utlTask.title +
								" after " + prevTask.title+"; Folder also changed.");
							op.setPrevious(prevTask.remote_id);
						}
					}
					else
						c.close();

					Task result = op.execute();

					// Store the Google ID of the new task, and also update the previous
					// folder ID.  If the task was marked as moved via manual sort, then clear
					// the flag because the move was handled here.
					utlTask.remote_id = result.getId();
					utlTask.prev_folder_id = utlTask.folder_id;
					utlTask.position = result.getPosition();
					utlTask.is_moved = false;
					utlTask.prev_task_id = -1;
					gTask.setId(result.getId());
					boolean isSuccessful = _tasksDB.modifyTask(utlTask);
					if (!isSuccessful)
					{
						Util.log("GTasksInterface: DB modification failed when changing task's "+
							"remote ID.");
						_lastErrorMsg = Util.getString(R.string.DbModifyFailed);
						return MISC_ERROR;
					}

					// Delete the copy of the task in the previous tasklist:
					if (oldTasklist!=null && oldTasklist.length()>0)
					{
						Tasks.TasksOperations.Delete delete = _service.tasks().delete(
							oldTasklist,oldTaskID);
						deleteOpFailed = true;
						delete.execute();
						deleteOpFailed = false;
					}
				}
				else
				{
					// Folders are the same, so we can execute a simple edit operation:
					Tasks.TasksOperations.Update op= _service.tasks().update(tasklist,
						gTask.getId(),gTask);
					op.execute();
				}

				if (utlTask.parent_id != utlTask.prev_parent_id && !folderMoved)
				{
					// The parent has changed.  Use a move operation:
					Tasks.TasksOperations.Move move= _service.tasks().move(tasklist, gTask.getId());
					if (utlTask.parent_id>0)
						move.setParent(gTask.getParent());

					// Look up which task is the previous one, if any.
					Cursor c = _tasksDB.queryTasks("folder_id=" + utlTask.folder_id + " and parent_id=" +
						utlTask.parent_id + " and sort_order>" + utlTask.sort_order, "sort_order asc");
					if (c.moveToFirst())
					{
						// Tell Google what the previous task is.
						UTLTask prevTask = _tasksDB.getUTLTask(c);
						c.close();
						if (prevTask.remote_id!=null && prevTask.remote_id.length()>0)
						{
							Util.log("GTasksInterface: Uploading Edited Task " + utlTask.title +
								" after " + prevTask.title + "; New parent set");
							move.setPrevious(prevTask.remote_id);
						}
					}
					else
						c.close();

					Task result = move.execute();

					// Update the task in the database.  If the task is flagged as moved via manual
					// sort, then unflag it, since the move was taken care of here.
					utlTask.prev_parent_id = utlTask.parent_id;
					utlTask.remote_id = result.getId();
					utlTask.position = result.getPosition();
					utlTask.is_moved = false;
					utlTask.prev_task_id = -1;
					boolean isSuccessful = _tasksDB.modifyTask(utlTask);
					if (!isSuccessful)
					{
						Util.log("GTasksInterface: DB modification failed when changing task's old parent ID.");
						_lastErrorMsg = Util.getString(R.string.DbModifyFailed);
						return MISC_ERROR;
					}
				}

				if (utlTask.is_moved)
				{
					// The task was reordered using manual sorting.  Set up a move operation:
					Tasks.TasksOperations.Move moveOp = _service.tasks().move(tasklist,gTask.getId());
					if (utlTask.parent_id>0)
					{
						UTLTask parent = _tasksDB.getTask(utlTask.parent_id);
						if (parent != null && parent.remote_id != null && parent.remote_id.length() > 0)
							moveOp.setParent(parent.remote_id);
					}

					// Look up which task is the previous one, if any.
					if (utlTask.prev_task_id>0)
					{
						// A task was found in front of this one.  Upload the info to Google:
						UTLTask prevTask = _tasksDB.getTask(utlTask.prev_task_id);
						if (prevTask!=null)
						{
							if (prevTask.remote_id != null && prevTask.remote_id.length() > 0)
							{
								Util.log("GTasksInterface: Uploading Edited Task " + utlTask.title +
									" after " + prevTask.title);
								moveOp.setPrevious(prevTask.remote_id);
								Task result = moveOp.execute();
								utlTask.position = result.getPosition();
								utlTask.is_moved = false;
								utlTask.prev_task_id = -1;
								_tasksDB.modifyTask(utlTask);
							}
							else
							{
								// The previous task has not been uploaded to Google yet.  Keep the
								// is_moved flag set (don't modify the task in the DB) so that another
								// attempt can be made later.
							}
						}
						else
						{
							// The previous task has been deleted somehow.  Put it at the top of
							// the list.
							Util.log("GTasksInterface: Previous task was deleted in editTask(). "+
								"Placing the task at the top.  Title: "+utlTask.title);
							Task result = moveOp.execute();
							utlTask.position = result.getPosition();
							utlTask.is_moved = false;
							utlTask.prev_task_id = -1;
							_tasksDB.modifyTask(utlTask);
						}
					}
					else if (utlTask.prev_task_id==-1)
					{
						// Use the sort_order field to look up the previous task.
						Cursor c = _tasksDB.queryTasks("folder_id="+utlTask.folder_id+" and parent_id="+
							utlTask.parent_id+" and sort_order>"+utlTask.sort_order,"sort_order asc");
						if (c.moveToFirst())
						{
							// A task was found in front of this one.  Upload the info to Google:
							UTLTask prevTask = _tasksDB.getUTLTask(c);
							c.close();
							Util.log("GTasksInterface: Uploading Edited Task " + utlTask.title +
								" after " + prevTask.title);
							if (prevTask.remote_id!=null && prevTask.remote_id.length()>0)
							{
								moveOp.setPrevious(prevTask.remote_id);
								Task result = moveOp.execute();
								utlTask.position = result.getPosition();
								utlTask.is_moved = false;
								utlTask.prev_task_id = -1;
								_tasksDB.modifyTask(utlTask);
							}
							else
							{
								// The previous task has not been uploaded to Google yet.  Set the
								// is_moved flag so that it can be tried at the next sync.
								utlTask.is_moved = true;
							}
						}
						else
						{
							c.close();
							// The task is at the top of the list. The "previous" field is omitted.
							Util.log("GTasksInterface: Uploading Edited Task " + utlTask.title +
								" at the top.");
							Task result = moveOp.execute();
							utlTask.position = result.getPosition();
							utlTask.is_moved = false;
							utlTask.prev_task_id = -1;
							_tasksDB.modifyTask(utlTask);
						}
					}
					else
					{
						// The task is at the top of the list. The "previous" field is omitted.
						Task result = moveOp.execute();
						utlTask.position = result.getPosition();
						utlTask.is_moved = false;
						utlTask.prev_task_id = -1;
						_tasksDB.modifyTask(utlTask);
					}
				}

				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else if (errorCode==MISC_ERROR && _lastHttpErrorCode==404 && tryCount==0 &&
					deleteOpFailed)
				{
					// Got a 404 error when trying to delete the old task after a folder
					// move.  We will ignore this, and try the operation again, in case
					// the parent task has changed.
					Util.log("GTasksInterface: Got 404 error when trying to delete old task after "+
						"a folder move. Ignoring this.  Title: "+utlTask.title);
					if (utlTask.parent_id != utlTask.prev_parent_id)
					{
						Util.log("GTasksInterface: Re-running the edit operation to handle the "+
							"move to a new parent task.");
						deleteOpFailed = false;
						tryCount++;
						continue;
					}
					else
						return SUCCESS;
				}
				else if (errorCode==MISC_ERROR && _lastErrorMsg.startsWith("HTTP Error 403") &&
					tryCount<20)
				{
					// We are sending updates to Google faster than they would like.  Sleep for a bit and
					// try again.
					Util.log("GTasksInterface: Got a 'Quota Exceeded' message from Google.  "+
						"Sleeping for 2 seconds and then trying again");
					try
					{
						Thread.sleep(2000);
					}
					catch (InterruptedException e2) { }
					tryCount++;
					continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.
		return SUCCESS;
	}

	// Delete a task.  Inputs are Google's tasklist ID and the task ID:
	public int deleteTask(String tasklistID, String taskID)
	{
		int tryCount = 0;
		while (true)
		{
			try
			{
				Tasks.TasksOperations.Delete op= _service.tasks().delete(tasklistID, taskID);
				op.execute();
				break;
			}
			catch (Exception e)
			{
				int errorCode = parseException(e);
				if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
				{
					// Get a new token and try the operation again:
					tryCount++;
					int refreshCode = refreshToken();
					if (refreshCode!=SUCCESS)
						return refreshCode;
					else
						continue;
				}
				else if (errorCode==MISC_ERROR && _lastErrorMsg.startsWith("HTTP Error 403") &&
					tryCount<20)
				{
					// We are sending updates to Google faster than they would like.  Sleep for a bit and
					// try again.
					Util.log("GTasksInterface: Got a 'Quota Exceeded' message from Google.  Sleeping for 2 seconds and then "+
						"trying again");
					try
					{
						Thread.sleep(2000);
					}
					catch (InterruptedException e2) { }
					tryCount++;
					continue;
				}
				else
					return errorCode;
			}
		}

		// If we get here, the operation was successful.
		return SUCCESS;
	}

	// Repair a task we received from Google that links to an invalid parent.  The input
	// is a UTLTask that was downloaded from Google and stored in our database.  This
	// function will return the SUCCESS code if an internal error occurs, in order to
	// prevent the sync operation from being blocked.
	public int repairBadParentLink(UTLTask task)
	{
		// Save the task's remote tasklist ID and the remote task ID.  These are needed
		// for the delete operation later:
		String gTaskID = task.remote_id;
		if (gTaskID.length()==0)
		{
			Util.log("GTasksInterface: Missing task ID in repairBadParentLink().");
			return SUCCESS;
		}
		Cursor c = (new FoldersDbAdapter()).getFolder(task.folder_id);
		if (!c.moveToFirst())
		{
			Util.log("GTasksInterface: Missing folder for task "+task.title);
			c.close();
			return SUCCESS;
		}
		String tasklistID = Util.cString(c,"remote_id");
		c.close();
		if (tasklistID.length()==0)
		{
			// Huh!?
			Util.log("GTasksInterface: Missing folder ID in repairBadParentLink().");
			return SUCCESS;
		}

		// Add a new task, which should not have the corrupt data:
		int result = addTask(task);
		if (result!=SUCCESS)
		{
			Util.log("GTasksInterface: Could not repair bad parent link. The addTask operation failed.");
			return result;
		}

		// Delete the corrupt task:
		result = deleteTask(tasklistID,gTaskID);
		if (result!=SUCCESS)
		{
			Util.log("GTasksInterface: Could not repair bad parent link. The deleteTask operation failed.");
			return result;
		}

		// Go through all child tasks and link them to the new parent task:
		c = _tasksDB.queryTasks("parent_id="+task._id, "position");
		c.moveToPosition(-1);
		while (c.moveToNext())
		{
			UTLTask child = _tasksDB.getUTLTask(c);
			if (child.remote_id.length()==0)
				continue;

			int tryCount = 0;
			while (true)
			{
				try
				{
					Tasks.TasksOperations.Move move = _service.tasks().move(tasklistID,
						child.remote_id);
					move.setParent(task.remote_id);
					Task moveResult = move.execute();
					child.remote_id = moveResult.getId();
					child.position = moveResult.getPosition();
					_tasksDB.modifyTask(child);
					break;
				}
				catch (Exception e)
				{
					int errorCode = parseException(e);
					if (errorCode==NEEDS_NEW_TOKEN && tryCount==0)
					{
						// Get a new token and try the operation again:
						tryCount++;
						int refreshCode = refreshToken();
						if (refreshCode!=SUCCESS)
							return refreshCode;
						else
							continue;
					}
					else
					{
						Util.log("GTasksInterface: Error when repairing bad parent link. The move "+
							"operation failed for task '"+child.title+"'");
						return errorCode;
					}
				}
			}
		}

		return SUCCESS;
	}

	// Log an internal sync error to our server:
	private void logInternalError(String msg)
	{
		Log.e("GTasksInterface","Internal Sync Failure",msg);
		Util.logOneTimeEvent(_context, "internal_sync_failure", 1, new String[] { "google", msg});
	}
}
