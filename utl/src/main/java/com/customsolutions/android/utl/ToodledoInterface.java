package com.customsolutions.android.utl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

// This class implements the required functionality for Toodledo synchronization.
// In general once, instance of this class will exist for each synchronized account.

public class ToodledoInterface 
{
    private static final String TAG = "ToodledoInterface";

    // UTL's Client ID and Client Secret, registered with Toodledo. These credentials are
    // obtained by registering a new application here:
    // https://api.toodledo.com/3/account/doc_register.php
    public static final String CLIENT_ID = "my_client_id";
    public static final String CLIENT_SECRET = "my_client_secret";

	// The base URL for all API Calls:
	private static final String BASE_URL = "https://api.toodledo.com/3/";

	// The maximum length of Toodledo's "meta" field, which contains information that
	// Toodledo cannot store:
	private static final int MAX_META_LENGTH = 1024;
	
	// Toodledo limits reminders so that they must be a certain number of minutes prior to the due
	// time.  This array contains values that Toodledo will accept.  Each represents the number of
	// minutes in between the due time and reminder time.
	private static final long[] TOODLEDO_REMINDER_DIFFS = { 0, 1, 15, 30, 45, 60, 90, 120, 180, 240, 
		1440, 2880, 4320, 5760, 7200, 8640, 10080, 20160, 43200 };
	private HashSet<Long> _reminderDiffsAllowed;
	
    // Error/success codes returned by the various requests.
    // Must match the codes in Synchronizer.java
    public static final int SUCCESS = 1;
    public static final int LOGIN_FAILURE = 2; // Username/password issue
    public static final int CONNECTION_FAILURE = 3; // No internet connection
    public static final int INTERFACE_ERROR = 4; // Toodledo gave an unexpected result
    public static final int INTERNAL_ERROR = 5; // Internal software failure (such as database issue)
    public static final int TOODLEDO_LOCK = 7; // Toodledo is locking out the user temporarily
    public static final int TOODLEDO_REJECT = 8; // Toodledo has rejected the sync operation
    	// When TOODLEDO_REJECT is used, the error message from Toodledo is placed into
    	// _lastRejectMessage
    public static final int DUPLICATE_USER_ID = 9; // User tried to create duplicate account.
    public static final int INVALID_TOKEN = 10;  // Toodledo rejected the token.  User should log in again.
    public static final int TOO_MANY_REQUESTS_PER_TOKEN = 11;  // User issued too many API requests per token.  New token needed.
    public static final int MAX_TASKS_REACHED = 12; // TD's limit on the number of tasks has been reached.

    // The last message we got from Toodledo in which an operation was rejected.
    public static String _lastRejectMessage;
    
    // The last error code we received from Toodledo:
    public static int _lastErrorCode = 0;
    
    // The Account ID:
    private long _accountID;
    
    // The UTLAccount structure.  Refreshed with each call to refreshConnection().
    private UTLAccount _account;
    
    // A link into the database:
    private AccountsDbAdapter _db;
    
    // Have we established an initial connection?
    private boolean _initialConnectionEstablished;
    
    // Holds the last string received from Toodledo's server.  Used to help with
    // debugging if an exception occurs:
    public static String _lastStringReceived;
    
    // The default time zone that the app uses:
    private TimeZone _defaultTimeZone = null;
    
    // Do we have collaborators (refreshed with each call to getCollaborators):
    private boolean _hasCollaborators;

    private Context _c;

    /** This flag indicates if the last request resulted in an token error from Toodledo - either
     * too many requests per token, or the use of an expired token. */
    private boolean _gotTokenError = false;

    // Perform an HTTP request with Toodledo's server.  Returns the response from the
    // server as text, or returns "" if an HTTP error occurs.  The inputs are:
    // - The URL to call (without any query parameters)
    // - The method.  Either HttpMethod.GET or HttpMethod.POST
    // - A list of parameters
    // - An optional dateTime field, which will be filled in with the date and time (in ms)
    //   of the response.
    private static String httpRequest(String url, HttpMethod httpMethod, 
    	ArrayList<BasicNameValuePair> parameters)
    {
    	return httpRequest(url, httpMethod, parameters, null);
    }
    
    // Same as above, with the following extra field:
    // - An optional dateTime field, which will be filled in with the date and time (in ms)
    //   of the response.  Can be set to null.
    private static String httpRequest(String url, HttpMethod httpMethod, 
    	ArrayList<BasicNameValuePair> parameters, Timestamp dateTime)
    {
        // Perform the HTTP request.  The exact function to call depends on the method.
        HttpResponseInfo rInfo;
        try
        {
            if (httpMethod== HttpMethod.GET)
            {
                if (parameters != null)
                {
                    String encodedParams = URLEncodedUtils.format(parameters, "UTF-8");
                    rInfo = Util.httpsGet(url + "?" + encodedParams, null, null);
                }
                else
                    rInfo = Util.httpsGet(url,null,null);
            }
            else if (httpMethod==HttpMethod.GET_WITH_PASSWORD)
            {
                if (parameters != null)
                {
                    String encodedParams = URLEncodedUtils.format(parameters, "UTF-8");
                    rInfo = Util.httpsGet(url + "?" + encodedParams, CLIENT_ID, CLIENT_SECRET);
                }
                else
                    rInfo = Util.httpsGet(url,CLIENT_ID, CLIENT_SECRET);
            }
            else if (httpMethod==HttpMethod.POST_WITH_PASSWORD)
            {
                // This must be a POST.
                rInfo = Util.httpsPost(url,Util.nameValuePairToHashMap(parameters),CLIENT_ID,
                    CLIENT_SECRET);
            }
            else
            {
                // This must be a POST.
                rInfo = Util.httpsPost(url,Util.nameValuePairToHashMap(parameters),null,null);
            }

            if (dateTime!=null)
            {
                // Pull out the date from the response:
                dateTime.timestamp = rInfo.httpUrlConnection.getHeaderFieldDate("Date",0);
                if (dateTime.timestamp==0)
                {
                    Util.log("ToodledoInterface: Missing date header in Toodledo's response.");
                }
            }

            return rInfo.text;
        }
        catch (IOException e)
        {
            Util.log("ToodledoInterface: Got IOException "+e.getClass().getName()+": "+e.getMessage());
            return "";
        }
    }
    
    // Parse a response from the Account API.
    private ParseResult parseAccountApiResponse(String httpResponseString)
    {
    	if (httpResponseString.length()==0)
    	{
    		// This indicates a connection failure:
    		return new ParseResult(CONNECTION_FAILURE,null,null);
    	}
    	
    	_lastStringReceived = httpResponseString;
    	
    	try
    	{
    		JSONObject json;
    		try
    		{
    			json = (JSONObject) new JSONTokener(httpResponseString).nextValue();
    		}
    		catch (ClassCastException e)
    		{
    			// This appears to happen when access to Toodledo is blocked by some
    			// device on the local network, and that device sends a string back.
        		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
        		Util.log("ToodledoInterface: "+httpResponseString);
    			return new ParseResult(CONNECTION_FAILURE,null,null);
    		}
    		if (!json.isNull("errorCode"))
    		{
    			int errorCode = json.getInt("errorCode");
    			_lastErrorCode = errorCode;
    			Util.log("ToodledoInterface: "+httpResponseString);
    			Util.log("ToodledoInterface: Got error code "+errorCode+" in account API. "+json.getString(
    				"errorDesc"));
    			switch (errorCode)
    			{
    			case 2:
                    handleInvalidToken();
    				return new ParseResult(INVALID_TOKEN,null,null);
                case 3:
                    handleTooManyApiRequests();
                    return new ParseResult(TOO_MANY_REQUESTS_PER_TOKEN,null,null);
    			default:
    				_lastRejectMessage = json.getString("errorDesc");
    				return new ParseResult(TOODLEDO_REJECT,null,null);
    			}
    		}
    		return new ParseResult(SUCCESS,json,null);
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException in account API: "+e.getMessage());
    		Util.log("ToodledoInterface: "+httpResponseString);
    		return new ParseResult(INTERFACE_ERROR,null,null);
    	}
    }

    // Parse a response from the authentication API.
    private ParseResult parseAuthenticationResponse(String httpResponseString)
    {
        if (httpResponseString.length()==0)
        {
            // This indicates a connection failure:
            return new ParseResult(CONNECTION_FAILURE,null,null);
        }

        _lastStringReceived = httpResponseString;

        try
        {
            JSONObject json;
            try
            {
                json = (JSONObject) new JSONTokener(httpResponseString).nextValue();
            }
            catch (ClassCastException e)
            {
                // This appears to happen when access to Toodledo is blocked by some
                // device on the local network, and that device sends a string back.
                Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
                Util.log("ToodledoInterface: "+httpResponseString);
                return new ParseResult(CONNECTION_FAILURE,null,null);
            }
            if (!json.isNull("errorCode"))
            {
                int errorCode = json.getInt("errorCode");
                _lastErrorCode = errorCode;
                Util.log("ToodledoInterface: "+httpResponseString);
                Util.log("ToodledoInterface: Got error code "+errorCode+" in Authentication API. "+json.getString(
                    "errorDesc"));
                switch (errorCode)
                {
                    case 102:
                    case 103:
                        handleInvalidToken();
                        return new ParseResult(INVALID_TOKEN,null,null);
                    default:
                        _lastRejectMessage = json.getString("errorDesc");
                        return new ParseResult(TOODLEDO_REJECT,null,null);
                }
            }
            return new ParseResult(SUCCESS,json,null);
        }
        catch (JSONException e)
        {
            Util.log("ToodledoInterface: Got JSONException in authentication API: "+
                e.getMessage());
            Util.log("ToodledoInterface: "+httpResponseString);
            return new ParseResult(INTERFACE_ERROR,null,null);
        }
    }

    // Get a new token from Toodledo:
    private ParseResult getNewToken()
    {
        // Send the token request to Toodledo and get the result:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("grant_type","refresh_token"));
        parameters.add(new BasicNameValuePair("refresh_token",_account.refresh_token));
        try
        {
            PackageInfo packageInfo = _c.getPackageManager().getPackageInfo(
                "com.customsolutions.android.utl", 0);
            parameters.add(new BasicNameValuePair("vers",Integer.valueOf(packageInfo.versionCode).
                toString()));
        }
        catch (Exception ex) { }
        parameters.add(new BasicNameValuePair("device",android.os.Build.MODEL));
        parameters.add(new BasicNameValuePair("os",Integer.valueOf(Build.VERSION.SDK_INT).toString()));
        String result = httpRequest(BASE_URL+"account/token.php",HttpMethod.POST_WITH_PASSWORD,parameters);

    	// Parse the response and return an error code if necessary:
    	return parseAuthenticationResponse(result);
    }

    // Given a failure code (defined above), get a descriptive string.
    static public String getFailureString(int failureCode)
    {
        if (failureCode==LOGIN_FAILURE)
        {
            return Util.getString(R.string.Username_password_is_incorrect);
        }
        else if (failureCode==CONNECTION_FAILURE)
        {
            return Util.getString(R.string.TD_not_responding);
        }
        else if (failureCode==INTERFACE_ERROR)
        {
            return Util.getString(R.string.TD_gave_unexpected_data);
        }
        else if (failureCode==INTERNAL_ERROR)
        {
            return Util.getString(R.string.Internal_software_failure);
        }
        else if (failureCode==TOODLEDO_LOCK)
        {
        	return Util.getString(R.string.Toodledo_Lock);
        }
        else if (failureCode== INVALID_TOKEN)
        {
        	return Util.getString(R.string.toodledo_sign_in_needed);
        }
        else if (failureCode==TOODLEDO_REJECT)
        {
        	if (_lastRejectMessage!=null)
        		return _lastRejectMessage;
        	else
        		return Util.getString(R.string.Toodledo_Reject);
        }
        else if (failureCode==DUPLICATE_USER_ID)
        {
        	return Util.getString(R.string.Duplicate_Account_Attempt);
        }
        else if (failureCode==TOO_MANY_REQUESTS_PER_TOKEN)
            return Util.getString(R.string.toodledo_rate_limit);
        else if (failureCode==MAX_TASKS_REACHED)
            return Util.getString(R.string.td_max_tasks_reached);
        else
        {
            return "";
        }
    }
    
    // The constructor, which accepts an account ID.  This must be an account that
    // syncs with Toodledo.
    public ToodledoInterface(long accountID, Context c)
    {
        _accountID = accountID;
        _initialConnectionEstablished = false;
        _db = new AccountsDbAdapter();
        downloadIncompleteOnly = false;
        _c  = c;
        
        UTLAccount a = _db.getAccount(accountID);

        // Check the database to see if we have any collaborators:
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        Cursor c2 = cdb.queryCollaborators("account_id="+a._id+" and remote_id!='"+
        	Util.makeSafeForDatabase(a.td_userid)+"'", "name");
        if (c2.moveToFirst())
        	_hasCollaborators = true;
        else
        	_hasCollaborators = false;
        
        // Initialize the set of reminder differences a Toodledo subscriber can use.
        _reminderDiffsAllowed = new HashSet<Long>();
        for (int i=0; i<TOODLEDO_REMINDER_DIFFS.length; i++)
        {
        	_reminderDiffsAllowed.add(TOODLEDO_REMINDER_DIFFS[i]);
        }
    }
    
    // Convert a JSON object returned by Toodledo into a HashMap of strings.  This assumes
    // that the object only contains values that can be converted into strings.  It must
    // not include any arrays.  Returns SUCCESS or an error code
    private int JsonToHashMap(JSONObject json, HashMap<String,String> map)
    {
    	@SuppressWarnings("unchecked")
		Iterator<String> it = (Iterator<String>)json.keys();
    	while (it.hasNext())
    	{
    		String key = it.next();
    		try
    		{
    			map.put(key, json.getString(key));
    		}
    		catch (JSONException e)
    		{
    			Util.log("ToodledoInterface: JSONException when converting to HashMap: "+e.getMessage());
    			return INTERFACE_ERROR;
    		}
    	}
    	return SUCCESS;
    }
    
    // Convert a JSON array into an ArrayList of string HashMaps.  This assumes that the
    // top-level JSON object is an array, and than each item in the array can be converted
    // into a HashMap of Strings.
    private int JsonArrayToHashMapList(JSONArray json, ArrayList<HashMap<String,String>>
    	hashMapList)
    {
    	hashMapList.clear();
    	try
    	{
    		for (int i=0; i<json.length(); i++)
    		{
    			hashMapList.add(new HashMap<String,String>());
    			int result = JsonToHashMap(json.getJSONObject(i),hashMapList.get(i));
    			if (result != SUCCESS)
    				return result;
    		}
    		return SUCCESS;
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: JSONException when converting to Hash Map List: "+e.getMessage());
			return INTERFACE_ERROR;
    	}
    }

    // Each time we try to connect with Toodledo, we need to re-read the account 
    // information (in case of changes) and log in again if needed.  This function
    // must be called before processing any requests.  It returns a result code
    // as defined above.
    private int refreshConnection()
    {
        _account = _db.getAccount(_accountID);
        if (_account==null)
        {
            // Should not happen!
            return INTERNAL_ERROR;
        }

        if (_account.sign_in_needed)
        {
            // We can't sync until the user signs in again.
            return INVALID_TOKEN;
        }

        if (!_initialConnectionEstablished)
        {
            if (_account.td_userid.length()>0)
            {
                // We have a user ID.
                if (System.currentTimeMillis()<(_account.token_expiry-300000) &&
                	(System.currentTimeMillis()+360*60*1000)>_account.token_expiry &&
                	_account.current_token!=null && _account.current_token.length()>0)
                {
                    // The token we previously received is in effect.  There is nothing
                	// to do here.
                    Util.log("ToodledoInterface: Refreshing connection: Using existing token.");
                    _initialConnectionEstablished = true;
                    return SUCCESS;
                }
                else
                {
                    // We need to get a new token:
                	Util.log("ToodledoInterface: Refreshing connection: Getting new token "+
                		"(Token expired: "+Util.getDateTimeString(_account.
                		token_expiry)+")");
                	
                	Util.log("ToodledoInterface: TD Userid: "+_account.td_userid);
                	
                	// Query Toodledo's server to get a new token:

                	ParseResult parseResult = getNewToken();
                	if (parseResult.result != SUCCESS)
                		return parseResult.result;
                	try
                	{
                		_account.current_token = parseResult.json.getString("access_token");
                		_account.token_expiry = System.currentTimeMillis()+parseResult.json.getLong("expires_in")*1000;
                        _account.refresh_token = parseResult.json.getString("refresh_token");
                        Util.log("ToodledoInterface: New Token: "+_account.current_token);
                		Util.log("ToodledoInterface: New Refresh Token: "+_account.refresh_token);
                        Util.log("ToodledoInterface: New Token Expiry: "+Util.getDateTimeString(_account.token_expiry));
                		_db.modifyAccount(_account);
                		_initialConnectionEstablished = true;
                		return SUCCESS;
                	}
                	catch (JSONException e)
                	{
                		Util.log("ToodledoInterface: JSONException when getting new token: "+e.getMessage());
                		return INTERFACE_ERROR;
                	}                    	
                }
            }
            else
            {
            	Util.log("ToodledoInterface: Toodledo User ID missing from UTL account.");
                return INTERNAL_ERROR;
            }
        }
        
        if (_initialConnectionEstablished && 
        	(System.currentTimeMillis() > (_account.token_expiry-300000) ||
        	(System.currentTimeMillis()+360*60*1000)<_account.token_expiry ||
            _gotTokenError)
        	)
        {
        	// The token has expired (or expires within 5 minutes).  Get a new one.
        	Util.log("ToodledoInterface: Refreshing connection: Token expired on "+
        		Util.getDateTimeString(_account.token_expiry)+".  Getting new one.");
        	
        	// Query Toodledo's server to get a new token:
        	ParseResult parseResult = getNewToken();
        	if (parseResult.result != SUCCESS)
        		return parseResult.result;
        	try
        	{
                _account.current_token = parseResult.json.getString("access_token");
                _account.token_expiry = System.currentTimeMillis()+parseResult.json.getLong("expires_in")*1000;
                _account.refresh_token = parseResult.json.getString("refresh_token");
                Util.log("ToodledoInterface: New Token: "+_account.current_token);
                Util.log("ToodledoInterface: New Refresh Token: "+_account.refresh_token);
                Util.log("ToodledoInterface: New Token Expiry: "+Util.getDateTimeString(_account.token_expiry));
        		_db.modifyAccount(_account);
        		return SUCCESS;
        	}
        	catch (JSONException e)
        	{
        		Util.log("ToodledoInterface: JSONException when getting new token: "+e.getMessage());
        		return INTERFACE_ERROR;
        	}                    	 	
        }
        
        _initialConnectionEstablished = true;
        return SUCCESS;
    }

    /** Request a new token after the user issues too many API requests for a token. */
    private void handleTooManyApiRequests()
    {
        // The account is updated so that the current token is expired immediately.  The token will
        // then be refreshed at the next sync.
        Util.log("ToodledoInterface: Setting current token expiry to current time, to trigger a new token request.");
        _account.token_expiry = System.currentTimeMillis();
        _db.modifyAccount(_account);
    }

    /** Handle an "invalid token" response from Toodledo.  In this case, the user needs to sign in again. */
    private void handleInvalidToken()
    {
        Util.log("ToodledoInterface: The API call will be retried after getting a new token.");
        /*
        _account.sign_in_needed = true;
        _db.modifyAccount(_account);
        */
    }
    
    // Parse a response from the Folders, Contexts, Goals, or Locations API.
    private ParseResult parseApiResponse(String httpResponseString)
    {
    	if (httpResponseString.length()==0)
    	{
    		// This indicates a connection failure:
    		return new ParseResult(CONNECTION_FAILURE,null,null);
    	}
    	
    	_lastStringReceived = httpResponseString;
    	
    	try
    	{
    		// We need to determine if the top level item is an array or an object:
    		if (httpResponseString.substring(0, 1).equals("{"))
    		{
    			// It's an object:
        		JSONObject json;
        		try
        		{
        			json = (JSONObject) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
	    		if (!json.isNull("errorCode"))
	    		{
	    			int errorCode = json.getInt("errorCode");
	    			_lastErrorCode = errorCode;
	    			Util.log("ToodledoInterface: "+httpResponseString);
	    			Util.log("ToodledoInterface: Got error code "+errorCode+" in API. "+json.getString(
	    				"errorDesc"));
	    			switch (errorCode)
	    			{
                    case 206:
	    			case 306:
                    case 406:
                    case 506:
	    				// This error code indicates that we sent in values that already
	    				// matched the server.  Do not treat this as an error:
	    				return new ParseResult(SUCCESS,json,null);
                    case 2:
                        handleInvalidToken();
                        return new ParseResult(INVALID_TOKEN,null,null);
                    case 3:
                        handleTooManyApiRequests();
                        return new ParseResult(TOO_MANY_REQUESTS_PER_TOKEN,null,null);
	    			default:
	    				_lastRejectMessage = json.getString("errorDesc");
	    				return new ParseResult(TOODLEDO_REJECT,null,null);
	    			}
	    		}
	    		return new ParseResult(SUCCESS,json,null);
    		}
    		else
    		{
    			// Assume it's an array, since Toodledo only returns objects or arrays at
    			// the top level.
    			JSONArray array;
        		try
        		{
        			array = (JSONArray) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
    			return new ParseResult(SUCCESS,null,array);
    		}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException in folder/contexts/goals/locations API: "+
    			e.getMessage());
    		Util.log("ToodledoInterface: "+httpResponseString);
    		return new ParseResult(INTERFACE_ERROR,null,null);
    	}
    }

    // Parse a response from the task API:
    private ParseResult parseTaskApiResponse(String httpResponseString)
    {
    	if (httpResponseString.length()==0)
    	{
    		// This indicates a connection failure:
    		return new ParseResult(CONNECTION_FAILURE,null,null);
    	}
    	
    	_lastStringReceived = httpResponseString;
    	
    	try
    	{
    		// We need to determine if the top level item is an array or an object:
    		if (httpResponseString.substring(0, 1).equals("{"))
    		{
    			// It's an object:
        		JSONObject json;
        		try
        		{
        			json = (JSONObject) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
	    		if (!json.isNull("errorCode"))
	    		{
	    			int errorCode = json.getInt("errorCode");
	    			_lastErrorCode = errorCode;
	    			Util.log("ToodledoInterface: "+httpResponseString);
	    			Util.log("ToodledoInterface: Got error code "+errorCode+" in account API. "+json.getString(
	    				"errorDesc"));
	    			switch (errorCode)
	    			{
                        case 603:
                            // This indicates that Toodledo's limit on the maximum number of tasks.
                            // has been reached.
                            return new ParseResult(MAX_TASKS_REACHED,json,null);
                        case 606:
                            // This error code indicates that we sent in values that already
                            // matched the server.  Do not treat this as an error:
                            return new ParseResult(SUCCESS,json,null);
                        case 2:
                            handleInvalidToken();
                            return new ParseResult(INVALID_TOKEN,null,null);
                        case 3:
                            handleTooManyApiRequests();
                            return new ParseResult(TOO_MANY_REQUESTS_PER_TOKEN,null,null);
                        default:
                            _lastRejectMessage = json.getString("errorDesc");
                            return new ParseResult(TOODLEDO_REJECT,json,null);
	    			}
	    		}
	    		return new ParseResult(SUCCESS,json,null);
    		}
    		else
    		{
    			// Assume it's an array, since Toodledo only returns objects or arrays at
    			// the top level.
    			JSONArray array;
        		try
        		{
        			array = (JSONArray) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
    			return new ParseResult(SUCCESS,null,array);
    		}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException in tasks API: "+e.getMessage());
    		Util.log("ToodledoInterface: "+httpResponseString);
    		return new ParseResult(INTERFACE_ERROR,null,null);
    	}
    }

    // Tweak a task before uploading.  The modifies the UTLTask instance passed in.
    // Returns true if the UTL reminder field could be represented in Toodledo.  False
    // is returned if the UTL reminder field could not be represented.
    private boolean tweakTaskForUploading(UTLTask localTask, Context c)
    {
    	SharedPreferences settings = c.getSharedPreferences("UTL_Prefs",0);
    	
    	if (localTask.due_date==0 && localTask.reminder>0)
    	{
    		// Reminder without a due date.  The reminder is not sent since TD 
    		// cannot handle this situation.
    		localTask.reminder = 0;
    		return false;
    	}
    	
    	// Tweak the due time and reminder if needed:
    	if (_account.pro)
    	{
    		// If a due time is set and a reminder is set, we need to make sure the difference
    		// between the reminder time and the due time is one of Toodledo's accepted values.
    		// if not, then the reminder cannot sync.
    		if (localTask.uses_due_time && localTask.reminder>0)
    		{
    			long minuteDiff = (localTask.due_date-localTask.reminder)/1000/60;
    			if (!_reminderDiffsAllowed.contains(minuteDiff))
    			{
    				// We can't send the reminder data, since TD will reject it.
    				localTask.reminder = 0;
    				return false;
    			}
    		}
    		
    		// To allow for syncing reminders when a due time is not specified,
    		// the reminder must be set to 7 days or less prior to the due date.
    		// When uploading the task info, we calculate a due time:
    		if (!localTask.uses_due_time && localTask.reminder>0)
    		{
    			GregorianCalendar dueDate = new GregorianCalendar(TimeZone.
					getTimeZone(settings.getString("home_time_zone", 
					"")));
    			dueDate.setTimeInMillis(localTask.due_date);
				GregorianCalendar reminder = new GregorianCalendar(
					TimeZone.getTimeZone(settings.getString(
					"home_time_zone","")));
				reminder.setTimeInMillis(localTask.reminder);

                // See if the reminder is within 7 days of the due date.  If it's more than 7 days
                // prior, then the reminder cannot be viewed from Toodledo's web site.
                int numDays = 0;
                while (reminder.before(dueDate))
                {
                    reminder.add(Calendar.DATE,1);
                    numDays++;
                }
                if (numDays>7)
                {
                    Util.log("ToodledoInterface: Task \""+localTask.title+"\" has a reminder "+
                        "that is more than 7 days before the due date. The reminder will not "+
                        "appear in Toodledo.");
                    localTask.reminder = 0;
                    return false;
                }

				dueDate.set(Calendar.HOUR_OF_DAY, reminder.get(Calendar.HOUR_OF_DAY));
				dueDate.getTimeInMillis();  // Needed to keep Calendar data consistent.
				dueDate.set(Calendar.MINUTE, reminder.get(Calendar.MINUTE));
				dueDate.getTimeInMillis();
				dueDate.set(Calendar.SECOND, reminder.get(Calendar.SECOND));
				dueDate.getTimeInMillis();
				dueDate.set(Calendar.MILLISECOND, reminder.get(Calendar.MILLISECOND));
				dueDate.getTimeInMillis();				
				// dueDate.set(Calendar.AM_PM, reminder.get(Calendar.AM_PM));
				localTask.uses_due_time = true;
				localTask.due_date = dueDate.getTimeInMillis();
    		}
    	}
    	else
    	{
    		// We are limited to syncing reminders that are 1 hour from the due
    		// time.
    		if (localTask.uses_due_time && localTask.reminder>0)
    		{
    			long minuteDiff = (localTask.due_date-localTask.reminder)/1000/60;
    			if (minuteDiff != 60)
    			{
    				// We can't send the reminder data, since TD will reject it.
    				localTask.reminder = 0;
    				return false;
    			}
    		}
    		else if (!localTask.uses_due_time && localTask.reminder>0)
    		{
    			// No due time set, we might be able to work around TD's limits by 
    			// sending a due time that is 1 hour later than the reminder time.
    			GregorianCalendar dueDate = new GregorianCalendar(TimeZone.
					getTimeZone(settings.getString("home_time_zone","")));
    			dueDate.setTimeInMillis(localTask.due_date);
    			GregorianCalendar reminder = new GregorianCalendar(
					TimeZone.getTimeZone(settings.getString(
					"home_time_zone","")));
				reminder.setTimeInMillis(localTask.reminder);
				if (reminder.get(Calendar.HOUR_OF_DAY)<23 &&
					dueDate.get(Calendar.YEAR)==reminder.get(Calendar.YEAR) &&
					dueDate.get(Calendar.MONTH)==reminder.get(Calendar.MONTH) &&
					dueDate.get(Calendar.DATE)==reminder.get(Calendar.DATE))
				{
					// Reminder is on the same date as due date, and is before 11PM.
					// We can send a due time that is 1 hour ahead of the reminder.
					localTask.due_date = localTask.reminder + 60*60*1000;
					localTask.uses_due_time = true;
				}
				else
				{
					// We can't send any due time or reminder because we can't sync.
					localTask.reminder = 0;
					return false;
				}
    		}
    	}    	
    	return true;
    }

    // Parse a response from the notes API:
    private ParseResult parseNoteApiResponse(String httpResponseString)
    {
    	if (httpResponseString.length()==0)
    	{
    		// This indicates a connection failure:
    		return new ParseResult(CONNECTION_FAILURE,null,null);
    	}
    	
    	_lastStringReceived = httpResponseString;
    	
    	try
    	{
    		// We need to determine if the top level item is an array or an object:
    		if (httpResponseString.substring(0, 1).equals("{"))
    		{
    			// It's an object:
        		JSONObject json;
        		try
        		{
        			json = (JSONObject) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
	    		if (!json.isNull("errorCode"))
	    		{
	    			int errorCode = json.getInt("errorCode");
	    			_lastErrorCode = errorCode;
	    			Util.log("ToodledoInterface: "+httpResponseString);
	    			Util.log("ToodledoInterface: Got error code "+errorCode+" in account API. "+json.getString(
	    				"errorDesc"));
	    			switch (errorCode)
	    			{
	    			case 706:
	    				// This error code indicates that we sent in values that already
	    				// matched the server.  Do not treat this as an error:
	    				return new ParseResult(SUCCESS,json,null);
                    case 2:
                        handleInvalidToken();
                        return new ParseResult(INVALID_TOKEN,null,null);
                    case 3:
                        handleTooManyApiRequests();
                        return new ParseResult(TOO_MANY_REQUESTS_PER_TOKEN,null,null);
	    			default:
	    				_lastRejectMessage = json.getString("errorDesc");
	    				return new ParseResult(TOODLEDO_REJECT,null,null);
	    			}
	    		}
	    		return new ParseResult(SUCCESS,json,null);
    		}
    		else
    		{
    			// Assume it's an array, since Toodledo only returns objects or arrays at
    			// the top level.
    			JSONArray array;
        		try
        		{
        			array = (JSONArray) new JSONTokener(httpResponseString).nextValue();
        		}
        		catch (ClassCastException e)
        		{
        			// This appears to happen when access to Toodledo is blocked by some
        			// device on the local network, and that device sends a string back.
            		Util.log("ToodledoInterface: Got ClassCastException: "+e.getMessage());
            		Util.log("ToodledoInterface: "+httpResponseString);
        			return new ParseResult(CONNECTION_FAILURE,null,null);
        		}
    			return new ParseResult(SUCCESS,null,array);
    		}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException in notes API: "+e.getMessage());
    		Util.log("ToodledoInterface: "+httpResponseString);
    		return new ParseResult(INTERFACE_ERROR,null,null);
    	}
    }

    // Get folders.  This populates an ArrayList of HashMaps containing all of the
    // parameters for each folder.  If no folders are returned, then there are no 
    // folders in the account.  The code checks for erroneous responses from TD and
    // connection failures.
    public int getFolders(ArrayList<HashMap<String,String>> folders)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server for the folders:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result = httpRequest(getBaseURL()+"folders/get.php",HttpMethod.GET,parameters);
        
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
    	if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getFolders(folders);
        }
        _gotTokenError = false;
    	if (parseResult.result!=SUCCESS)
    		return parseResult.result;
    	
    	// Convert the JSON object in the response to an ArrayList of HashMap objects:
    	return JsonArrayToHashMapList(parseResult.array,folders);
    }

    // Get contexts.  This populates an ArrayList of HashMaps containing all of the
    // parameters for each context.  If no contexts are returned, then there are no 
    // contexts in the account.  The code checks for erroneous responses from TD and
    // connection failures.
    public int getContexts(ArrayList<HashMap<String,String>> contexts)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server for the contexts:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result = httpRequest(getBaseURL()+"contexts/get.php",HttpMethod.GET,parameters);
        
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getContexts(contexts);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Convert the JSON object in the response to an ArrayList of HashMap objects:
    	return JsonArrayToHashMapList(parseResult.array,contexts);
    }

    // Get goals.  This populates an ArrayList of HashMaps containing all of the
    // parameters for each goal.  If no goals are returned, then there are no 
    // goals in the account.  The code checks for erroneous responses from TD and
    // connection failures.
    public int getGoals(ArrayList<HashMap<String,String>> goals)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server for the goals:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result = httpRequest(getBaseURL()+"goals/get.php",HttpMethod.GET,parameters);
        
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getGoals(goals);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Convert the JSON object in the response to an ArrayList of HashMap objects:
    	return JsonArrayToHashMapList(parseResult.array,goals);
    }
    
    // Get locations.  Returns a result code.  The input is an ArrayList of UTLLocation
    // objects, which is populated with the location info:
    public int getLocations(ArrayList<UTLLocation> locations)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server for the locations:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result = httpRequest(getBaseURL()+"locations/get.php",HttpMethod.GET,parameters);
    	
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getLocations(locations);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Go through the JSON objects returned and convert them to UTLLocation objects:
    	try
    	{
    		int numReturned = parseResult.array.length();
    		for (int i=0; i<numReturned; i++)
    		{
    			JSONObject json = parseResult.array.getJSONObject(i);
    			UTLLocation loc = new UTLLocation();
    			loc.td_id = json.getLong("id");
    			loc.title = json.getString("name");
    			loc.description = json.getString("description");
    			loc.lat = json.getDouble("lat");
    			loc.lon = json.getDouble("lon");
    			loc.account_id = _account._id;
    			locations.add(loc);
    		}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when parsing downloaded locations. "+
    			e.getMessage());
    		try
    		{
    			Util.log("ToodledoInterface: "+parseResult.array.toString(4));
    		}
    		catch (JSONException e2)
    		{
    		}
    		return INTERFACE_ERROR;
    	}
    	
    	// If we get there, then no errors occurred:
    	return SUCCESS;
    }
    
    // Add a folder.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the folder's record.  If the add operation is successful,
    // then the database record (but not the Cursor) is updated to include Toodledo's ID.
    public int addFolder(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("name",Util.cString(c,"title")));
        parameters.add(new BasicNameValuePair("private",new Integer(Util.cInt(c, "is_private")).toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"folders/add.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addFolder(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
    	// Extract Toodledo's ID from the response and store it locally:
    	try
    	{
    		if (parseResult.array==null)
    		{
    			Util.log("ToodledoInterface: Got an object instead of an array when adding a folder.");
    			Util.log("ToodledoInterface: "+result);
    			return INTERFACE_ERROR;
    		}
    		JSONObject json = parseResult.array.getJSONObject(0);
    		boolean success = (new FoldersDbAdapter()).modifyFolder(Util.cLong(c, "_id"),
                json.getLong("id"), Util.cLong(c, "account_id"), Util.cString(c, "title"),
                Util.cInt(c, "archived") == 1 ? true : false,
                Util.cInt(c, "is_private") == 1 ? true : false);
            if (!success)
            {
                Util.log("ToodledoInterface: Couldn't modify folder in DB.");
                return INTERNAL_ERROR;
            }
            return SUCCESS;
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when adding folder. "+e.getMessage());
    		Util.log("ToodledoInterface: "+result);
    		return INTERFACE_ERROR;
    	}    	
    }

    // Add a context.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the context's record.  If the add operation is successful,
    // then the database record (but not the Cursor) is updated to include Toodledo's ID.
    public int addContext(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("name",Util.cString(c,"title")));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"contexts/add.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addContext(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
    	// Extract Toodledo's ID from the response and store it locally:
    	try
    	{
    		if (parseResult.array==null)
    		{
    			Util.log("ToodledoInterface: Got an object instead of an array when adding a context.");
    			Util.log("ToodledoInterface: "+result);
    			return INTERFACE_ERROR;
    		}
    		JSONObject json = parseResult.array.getJSONObject(0);
    		boolean success = (new ContextsDbAdapter()).modifyContext(Util.cLong(c, "_id"),
                json.getLong("id"), Util.cLong(c, "account_id"), Util.cString(c, "title"));
            if (!success)
            {
                Util.log("ToodledoInterface: Couldn't modify context in DB.");
                return INTERNAL_ERROR;
            }
            return SUCCESS;
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when adding context. "+e.getMessage());
    		Util.log("ToodledoInterface: "+result);
    		return INTERFACE_ERROR;
    	}        
    }

    // Add a goal.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the goal's record.  If the add operation is successful,
    // then the database record (but not the Cursor) is updated to include Toodledo's ID.
    // If the goal, through the contributes field, references another goal that has
    // not been uploaded, then the other goal is uploaded as well.
    public int addGoal(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Set up parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("name",Util.cString(c, "title")));
        parameters.add(new BasicNameValuePair("level",new Integer(Util.cInt(c, "level")).
        	toString()));
        GoalsDbAdapter db = new GoalsDbAdapter();
        if (Util.cLong(c,"contributes")>0)
        {
            // The goal contributes to another goal, so we need to get the TD ID of that
            // goal.
            Cursor c2 = db.getGoal(Util.cInt(c, "contributes"));
            if (!c2.moveToFirst())
            {
                // This is bad.  The goal references a nonexistent goal.  Log the failure
                // and update the goal so that it does not reference the nonexistent one.
                Util.log("ToodledoInterface: Goal ID "+Util.cLong(c, "_id")+" references nonexistent goal "+
                    Util.cLong(c, "contributes"));
                db.modifyGoal(Util.cLong(c,"_id"), Util.cLong(c,"td_id"), Util.cLong(c,
                    "account_id"), Util.cString(c,"title"), Util.cInt(c,"archived")==1 ?
                    true : false, 0, Util.cInt(c,"level"));
                c = db.getGoal(Util.cLong(c,"_id"));
            }
            else
            {
                if (Util.cLong(c2, "td_id")==-1)
                {
                    // The referenced goal has not been uploaded to Toodledo yet.
                    // Call this function recursively to do this.
                    int result = addGoal(c2);
                    if (result != SUCCESS)
                    {
                        // The add failed.  Return the failure code.
                        return result;
                    }
                    
                    // Get the Toodledo ID of the goal we just added, and update the 
                    // request to Toodledo:
                    c2 = db.getGoal(Util.cLong(c,"contributes"));
                    parameters.add(new BasicNameValuePair("contributes",new Long(Util.
                    	cLong(c2, "td_id")).toString()));
                }
                else
                {
                	parameters.add(new BasicNameValuePair("contributes",new Long(Util.
                    	cLong(c2, "td_id")).toString()));
                }
            }
        }
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"goals/add.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addGoal(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract Toodledo's ID from the response and store it locally:
    	try
    	{
    		if (parseResult.array==null)
    		{
    			Util.log("ToodledoInterface: Got an object instead of an array when adding a goal.");
    			Util.log("ToodledoInterface: "+result);
    			return INTERFACE_ERROR;
    		}
    		JSONObject json = parseResult.array.getJSONObject(0);
    		boolean success = db.modifyGoal(Util.cLong(c,"_id"), json.getLong("id"), 
    			Util.cLong(c,
            	"account_id"), Util.cString(c,"title"), Util.cInt(c,"archived")==1 ?
                true : false, Util.cLong(c, "contributes"), Util.cInt(c,"level"));
            if (!success)
            {
                Util.log("ToodledoInterface: Couldn't modify goal in DB.");
                return INTERNAL_ERROR;
            }
            return SUCCESS;
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when adding goal. "+e.getMessage());
    		Util.log("ToodledoInterface: "+result);
    		return INTERFACE_ERROR;
    	}
    }

    // Add a location.  Returns a result code.  If the location is successfully added, then 
    // the UTLLocation instance passed in is updated to include the Toodledo ID, and the 
    // database is also updated to include the Toodledo ID.
    public int addLocation(UTLLocation utlLoc)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("name",utlLoc.title));
        parameters.add(new BasicNameValuePair("description",utlLoc.description));
        parameters.add(new BasicNameValuePair("lat",new Double(utlLoc.lat).toString()));
        parameters.add(new BasicNameValuePair("lon",new Double(utlLoc.lon).toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"locations/add.php",HttpMethod.GET,parameters);

        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addLocation(utlLoc);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the new location's ID from the response:
    	long locID;
    	JSONObject json;
    	try
    	{
    		if (parseResult.array==null)
    		{
    			Util.log("ToodledoInterface: Got an object instead of an array when adding a location.");
    			Util.log("ToodledoInterface: "+result);
    			return INTERFACE_ERROR;
    		}
	    	json = parseResult.array.getJSONObject(0);
	    	locID = json.getLong("id");
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when adding "+
    			"location.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	
    	// If we get here, the add was successful.  Update the UTLNote object passed in 
        // to include the TD note ID, and also update the DB:
        utlLoc.td_id = locID;
        if (!locDB.modifyLocation(utlLoc))
        {
        	Util.log("ToodledoInterface: Could not modify location after adding to Toodledo.");
        	return INTERNAL_ERROR;
        }
        return SUCCESS;
    }

    // Edit a folder.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the folder's record.
    public int editFolder(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(Util.cLong(c,"td_id")).toString()));
        parameters.add(new BasicNameValuePair("name",Util.cString(c,"title")));
        parameters.add(new BasicNameValuePair("private",Integer.valueOf(Util.cInt(c, 
        	"is_private")).toString()));
        parameters.add(new BasicNameValuePair("archived",new Integer(Util.cInt(c,"archived")).
        	toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"folders/edit.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editFolder(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }

    // Edit a context.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the context's record.
    public int editContext(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(Util.cLong(c,"td_id")).toString()));
        parameters.add(new BasicNameValuePair("name",Util.cString(c,"title")));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"contexts/edit.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editContext(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }

    // Edit a goal.  Returns a result code.  The input is a database cursor that is 
    // currently pointing to the goal's record.
    // If the goal, through the contributes field, references another goal that has
    // not been uploaded, then the other goal is uploaded as well.
    public int editGoal(Cursor c)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Set up parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(Util.cLong(c,"td_id")).toString()));
        parameters.add(new BasicNameValuePair("name",Util.cString(c, "title")));
        parameters.add(new BasicNameValuePair("level",new Integer(Util.cInt(c, "level")).
        	toString()));
        parameters.add(new BasicNameValuePair("archived",Integer.valueOf(Util.cInt(c, "archived")).
            toString()));
        GoalsDbAdapter db = new GoalsDbAdapter();
        if (Util.cLong(c,"contributes")>0)
        {
            // The goal contributes to another goal, so we need to get the TD ID of that
            // goal.
            Cursor c2 = db.getGoal(Util.cInt(c, "contributes"));
            if (!c2.moveToFirst())
            {
                // This is bad.  The goal references a nonexistent goal.  Log the failure
                // and update the goal so that it does not reference the nonexistent one.
                Util.log("ToodledoInterface: Goal ID "+Util.cLong(c, "_id")+" references nonexistent goal "+
                    Util.cLong(c, "contributes"));
                db.modifyGoal(Util.cLong(c,"_id"), Util.cLong(c,"td_id"), Util.cLong(c,
                    "account_id"), Util.cString(c,"title"), Util.cInt(c,"archived")==1 ?
                    true : false, 0, Util.cInt(c,"level"));
                c = db.getGoal(Util.cLong(c,"_id"));
            }
            else
            {
                if (Util.cLong(c2, "td_id")==-1)
                {
                    // The referenced goal has not been uploaded to Toodledo yet.
                    // Call the addGoal function to do this.
                    int result = addGoal(c2);
                    if (result != SUCCESS)
                    {
                        // The add failed.  Return the failure code.
                        return result;
                    }
                    
                    // Get the Toodledo ID of the goal we just added, and update the 
                    // request to Toodledo:
                    c2 = db.getGoal(Util.cLong(c,"contributes"));
                    parameters.add(new BasicNameValuePair("contributes",new Long(Util.
                    	cLong(c2, "td_id")).toString()));
                }
                else
                {
                	parameters.add(new BasicNameValuePair("contributes",new Long(Util.
                    	cLong(c2, "td_id")).toString()));
                }
            }
        }
        else
        {
        	// The goal does not contribute to anything.  Remove any contributes field
        	// that may already exist on the server:
        	parameters.add(new BasicNameValuePair("contributes","0"));
        }
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"goals/edit.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editGoal(c);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// No errors.  Return the success code.
        return SUCCESS;
    }

    // Edit a location.  Returns a result code.
    public int editLocation(UTLLocation utlLoc)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(utlLoc.td_id).toString()));
        parameters.add(new BasicNameValuePair("name",utlLoc.title));
        parameters.add(new BasicNameValuePair("description",utlLoc.description));
        parameters.add(new BasicNameValuePair("lat",new Double(utlLoc.lat).toString()));
        parameters.add(new BasicNameValuePair("lon",new Double(utlLoc.lon).toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"locations/edit.php",HttpMethod.GET,parameters);

        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editLocation(utlLoc);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// No errors.  Return the success code:
        return SUCCESS;
    }

    // Delete a folder.  The input is Toodledo's folder ID.  Returns a result code.
    public int deleteFolder(long TDID)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(TDID).toString()));

        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"folders/delete.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteFolder(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }
    
    // Delete a context.  The input is Toodledo's context ID.  Returns a result code.
    public int deleteContext(long TDID)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(TDID).toString()));

        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"contexts/delete.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteContext(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }

    // Delete a goal.  The input is Toodledo's goal ID.  Returns a result code.
    public int deleteGoal(long TDID)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(TDID).toString()));

        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"goals/delete.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteGoal(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }
    
    // Delete a location.  The input is Toodledo's location ID.  Returns a result code.
    public int deleteLocation(long TDID)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",new Long(TDID).toString()));

        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"locations/delete.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteLocation(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
        
        // No errors.  Return the success code.
        return SUCCESS;
    }
    
    // Get account information.  Populates a HashMap of the various parameters returned by
    // Toodledo.  The keys in the hash match TD's response exactly, with one exception.
    // An additional hash key called "server_time" contains the millisecond timestamp at 
    // Toodledo (or 0 if Toodledo doesn't send it).  A result code is returned.
    public int getAccountInfo(HashMap<String,String> hash)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // This structure holds the Toodledo server's timestamp:
        Timestamp timestamp = new Timestamp();
        
        // Query the server - try 2 times in case Toodledo incorrectly rejects our key:
        ParseResult parseResult = new ParseResult(SUCCESS,null,null);
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result;
        result = httpRequest(BASE_URL+"account/get.php",HttpMethod.GET,parameters,
            timestamp);

        // Parse the response and return an error code if necessary:
        parseResult = parseAccountApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getAccountInfo(hash);
        }
        _gotTokenError = false;
        if (parseResult.result != SUCCESS)
        {
            if (parseResult.result== INVALID_TOKEN)
            {
                Util.log("ToodledoInterface: TD rejected this token: "+_account.current_token);
                Util.log("ToodledoInterface: The user will be asked to sign in again.");
                _account.sign_in_needed = true;
                _db.modifyAccount(_account);
            }
            return parseResult.result;
        }

    	// Store the timestamp from Toodledo's server:
    	hash.put("server_time", new Long(timestamp.timestamp).toString());

    	// Store the parameters in the response in the hash that was passed in:
    	return JsonToHashMap(parseResult.json,hash);
    }
    
    // Get collaborator information.  Populates an ArrayList of HashMap objects containing the
    // collaborator info from Toodledo.  The fields in the HashMap are identical to those returned
    // by Toodledo.  Boolean values are specified as a 1 or 0.  A result code is returned.
    public int getCollaborators(ArrayList<HashMap<String,String>> collaborators)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server:
        ParseResult parseResult = new ParseResult(SUCCESS,null,null);
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        String result;
    	result = httpRequest(BASE_URL+"account/collaborators.php",HttpMethod.GET,parameters);
    	
    	// Parse the response and return an error code if necessary:
    	parseResult = parseApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getCollaborators(collaborators);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    	{
    		Util.log("ToodledoInterface: TD returned this error code from collaborators call: "+parseResult.result);
    		return parseResult.result;
    	}
    	
    	// Fill in the collaborators list:
    	collaborators.clear();
    	_hasCollaborators = false;
    	for (int i=0; i<parseResult.array.length(); i++)
    	{
    		HashMap<String,String> hash = new HashMap<String,String>();
    		try
    		{
	    		if (SUCCESS != JsonToHashMap(parseResult.array.getJSONObject(i),hash))
	    		{
	    			return INTERFACE_ERROR;
	    		}
    		}
    		catch (JSONException e)
    		{
    			Util.log("ToodledoInterface: JSONException when converting to HashMap: "+e.getMessage());
    			return INTERFACE_ERROR;
    		}
    		
    		collaborators.add(hash);
    		_hasCollaborators = true;
    	}
    	
    	// No errors.  Return the success code.
        return SUCCESS;
    }
    
    // Set this value to true to only download incomplete tasks in the getTasks() function below.
    public boolean downloadIncompleteOnly = false;
    
    // Get tasks.  The input is a UNIX millisecond timestamp.  Any tasks modified after 
    // this time will be returned.  This populates an ArrayList of UTLTask objects 
    // (without the UTL internal DB ID).  This returns a result code.
    // To get all tasks, pass in a zero for the timestamp.
    // The tagList argument is an arrayList of Strings, in the same order as taskList.
    // The Strings are comma-separated lists of tags, as passed in by Toodledo.  Tasks
    // with no tags will have blank (not null) Strings in this ArrayList.
    // The hasMeta array contains boolean values which indicate whether meta data was
    // downloaded for the task.  If meta data was downloaded, certain fields (such as nag)
    // must be taken from it.
    public int getTasks(long modAfter, ArrayList<UTLTask> taskList, ArrayList<String> tagList,
    	ArrayList<Boolean> hasMeta, Context context, boolean sendPercentComplete, 
    	int totalPercentToAdd, int basePercent, ArrayList<Messenger> clients)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        ContextsDbAdapter contextsDB = new ContextsDbAdapter();
        GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c;
        
        int start = 0;
        int num = 1000;
        while (true)
        {
        	// Query the server for the tasks:
        	ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
            parameters.add(new BasicNameValuePair("access_token",_account.current_token));
            parameters.add(new BasicNameValuePair("after",new Long(modAfter/1000).toString()));
            parameters.add(new BasicNameValuePair("start",new Integer(start).toString()));
            parameters.add(new BasicNameValuePair("num",new Integer(num).toString()));
            if (downloadIncompleteOnly)
            	parameters.add(new BasicNameValuePair("comp","0"));
            parameters.add(new BasicNameValuePair("fields","folder,context,goal,location,"+
            	"tag,startdate,duedate,duedatemod,starttime,duetime,remind,repeat,status,"+
            	"star,priority,length,timer,note,parent,meta,shared,sharedowner,sharedwith,"+
            	"addedby"));
            String result = httpRequest(getBaseURL()+"tasks/get.php",HttpMethod.GET,parameters);
            
        	// Parse the response and return an error code if necessary:
        	ParseResult parseResult = parseApiResponse(result);
            if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
                && !_gotTokenError)
            {
                // Try again with a new token.
                _gotTokenError = true;
                taskList.clear();
                tagList.clear();
                hasMeta.clear();
                return getTasks(modAfter,taskList,tagList,hasMeta,context,sendPercentComplete,
                    totalPercentToAdd,basePercent,clients);
            }
            _gotTokenError = false;
        	if (parseResult.result != SUCCESS)
        		return parseResult.result;
        	
            SharedPreferences settings = context.getSharedPreferences("UTL_Prefs",0);
        	
        	int i = 0;
        	int numReturned = 0;
        	int totalTasks = 0;
        	try
        	{
	    		// The first item in the returned array contains information on the number
	        	// of tasks returned.
	        	numReturned = parseResult.array.getJSONObject(0).getInt("num");
	            totalTasks =  parseResult.array.getJSONObject(0).getInt("total");
	            
	            for (i=0; i<numReturned; i++)
	            {
	                JSONObject json = parseResult.array.getJSONObject(i+1);
	                UTLTask utlTask = new UTLTask();
	                utlTask.td_id = json.getLong("id");
	                utlTask.account_id = _accountID;
	                utlTask.mod_date = json.getLong("modified")*1000;
	                utlTask.sync_date = System.currentTimeMillis();
	                utlTask.title = json.getString("title");
	                utlTask.completed = json.getLong("completed")>0 ? true : false;
	                
	                // Task Folder:
	                if (!json.isNull("folder") && json.getLong("folder")>0)
	                {
	                    c = foldersDB.getFolder(_accountID, json.getLong("folder"));
	                    if (!c.moveToFirst())
	                    {
	                        // This should not happen, but we'll handle it gracefully just in
	                        // case.  Log the event and keep the folder blank.
	                        Util.log("ToodledoInterface: Received a task from Toodledo that references a folder "+
	                            "not in the UTL database (TD Task ID: "+json.getLong("id")+", "+
	                            "TD Folder ID: "+json.getLong("folder")+")");
	                    }
	                    else
	                    {
	                        utlTask.folder_id = Util.cLong(c, "_id");
	                    }
	                    c.close();
	                }
	                
	                // Task Context:
	                if (!json.isNull("context") && json.getLong("context")>0)
	                {
	                    c = contextsDB.getContext(_accountID, json.getLong("context"));
	                    if (!c.moveToFirst())
	                    {
	                        // This should not happen, but we'll handle it gracefully just in
	                        // case.  Log the event and keep the folder blank.
	                        Util.log("ToodledoInterface: Received a task from Toodledo that references a context "+
	                            "not in the UTL database.");
	                    }
	                    else
	                    {
	                        utlTask.context_id = Util.cLong(c, "_id");
	                    }
	                    c.close();
	                }
	    
	                // Task Goal:
	                if (!json.isNull("goal") && json.getLong("goal")>0)
	                {
	                    c = goalsDB.getGoal(_accountID, json.getLong("goal"));
	                    if (!c.moveToFirst())
	                    {
	                        // This should not happen, but we'll handle it gracefully just in
	                        // case.  Log the event and keep the goal blank.
	                        Util.log("ToodledoInterface: Received a task from Toodledo that references a goal "+
	                            "not in the UTL database.");
	                    }
	                    else
	                    {
	                        utlTask.goal_id = Util.cLong(c, "_id");
	                    }
	                    c.close();
	                }
	                
	                // Task Location:
	                if (!json.isNull("location") && json.getLong("location")>0)
	                {
	                	UTLLocation loc = locDB.getLocation(_accountID, json.getLong("location"));
	                	if (loc==null)
	                	{
	                		// This should not happen, but we'll handle it gracefully just in
	                        // case.  Log the event and keep the location blank.
	                        Util.log("ToodledoInterface: Received a task from Toodledo that references a location "+
	                            "not in the UTL database.");
	                	}
	                	else
	                		utlTask.location_id = loc._id;
	                }
	    
	                // Parent task:
	                if (!json.isNull("parent") && json.getLong("parent")>0)
	                {
	                    // The task has a parent.  Look up the parent's UTL ID:
	                    UTLTask temp = tasksDB.getTask(_accountID, json.getLong("parent"));            
	                    if (temp==null)
	                    {
	                        // Couldn't find the parent, meaning that it still needs to 
	                        // download.  Set the parent ID to a temporary value which will
	                        // be updated later.
	                        utlTask.parent_id = 0 - json.getLong("parent");
	                    }
	                    else
	                    {
	                        utlTask.parent_id = temp._id;
	                    }
	                }
	                
	                // Repeat options:
	                if (!json.isNull("repeat") && json.getString("repeat").length()>0)
	                {
	                	// We need to convert from the API version 3 format to the version 1
	                	// format, since our code uses version 1:
	                	parseToodledoRepeatString(utlTask,json.getString("repeat"));
	                }
	                
	                // Meta data, containing fields specific to UTL:
	                JSONObject meta = null;
	                if (!json.isNull("meta") && json.getString("meta").length()>0)
	                {
	                	meta = new JSONObject(json.getString("meta"));
	                	hasMeta.add(new Boolean(true));
	                }
	                else
	                	hasMeta.add(new Boolean(false));
	                
	                // Due date and due time:
	                if (!json.isNull("duedate") && json.getLong("duedate")>0)
	                {
	                	// Store the due date modifier (if any)
	                    if (!json.isNull("duedatemod"))
	                    {
	                        if (json.getInt("duedatemod")==1)
	                        {
	                            utlTask.due_modifier = "due_on";
	                        }
	                        else if (json.getInt("duedatemod")==3)
	                        {
	                            utlTask.due_modifier = "optionally_on";
	                        }
	                        else
	                        {
	                            utlTask.due_modifier = "due_by";
	                        }
	                    }
	                	
	                    if (!json.isNull("duetime") && json.getLong("duetime")>86400)
	                    {
	                    	// The task has both a due time and a due date
	                    	// (If the task has a due time with no due date, it is ignored)
	                    	utlTask.due_date = timeShift(context,json.getLong("duetime")*1000,false);
	                    	utlTask.uses_due_time = true;
	                    }
	                    else
	                    {
	                    	// The task has a due date with no due time.
	                    	utlTask.due_date = Util.getMidnight(timeShift(context,
	                    		json.getLong("duedate")*1000,false),context);
	                    	utlTask.uses_due_time = false;
	                    }
	                }
	                
	                // Get the rest of the meta data, if it exists:
	                boolean metaUsesDueTime = false;
	                long metaReminder = 0;
	                long metaRepeat = 0;
	                long metaDueDate = 0;
	                boolean metaIsPro = false;
	                boolean metaIsCompleted = false;
	                boolean metaReminderUploaded = true;
	                String metaContactLookupKey = "";
                    long metaSortOrder = 0;
                    if (meta!=null)
                    {
                    	metaUsesDueTime = meta.getBoolean("uses_due_time");
	                    metaReminder = timeShift(context,meta.getLong("reminder"),false);
	                    metaRepeat = meta.getInt("repeat");
	                    metaDueDate = timeShift(context,meta.getLong("due_date"),false);
	                    metaIsPro = meta.getBoolean("is_pro");
	                    metaIsCompleted = meta.getBoolean("is_completed");
	                    if (meta.has("contact"))
	                    	metaContactLookupKey = meta.getString("contact");
	                    if (meta.has("was_reminder_uploaded"))
	                    	metaReminderUploaded = meta.getBoolean("was_reminder_uploaded");
	                    if (meta.has("was_rem_uploaded"))
	                    	metaReminderUploaded = meta.getBoolean("was_rem_uploaded");
                        if (meta.has("sort_order"))
                            metaSortOrder = meta.getLong("sort_order");
                        if (!utlTask.completed)
                        {
                            Util.log("ToodledoInterface: Metadata received for Task '" + utlTask.title +
                                "': " + meta.toString());
                        }
                        if (meta.has("uuid"))
                            utlTask.uuid = meta.getString("uuid");
                    }

                    // Sort Order:
                    utlTask.sort_order = metaSortOrder;

	                // Reminder:
	                if (!json.isNull("duedate") && json.getLong("duedate")>0 && 
	                	!json.isNull("remind") && json.getLong("remind")>0)
	                {
	                    utlTask.reminder = utlTask.due_date - json.getLong("remind")*60*1000;
	                    
	                    if (meta!=null)
	                    {	
		                    if (utlTask.uses_due_time && !metaUsesDueTime)
		                    {
		                    	// Use the meta data to remove the due time field if the user is
		                    	// not using it. This applies when the user specified a date and
		                    	// time for the reminder (rather than specifying the reminder
		                    	// as the number of minutes prior to due time).
		                    	if ((metaReminder==utlTask.reminder || metaReminder==
		                    		utlTask.reminder+60000))
		                    	{
		                    		// The meta reminder (previously entered in UTL) matches the
		                    		// reminder received from TD's interface, so the user did
		                    		// not change the reminder.  Also, the user previously did
		                    		// not choose a due time when entering the task in UTL.
		                    		// So, we can remove the due time from the task.
		                        	utlTask.due_date = Util.getMidnight(timeShift(context,
		                        		json.getLong("duedate")*1000,false),context);
		                        	utlTask.uses_due_time = false;
		                        	utlTask.reminder = metaReminder;
		                    	}
		                    	
		                    	if (metaIsPro && utlTask.repeat>0 && metaRepeat>0 &&
		                    		utlTask.repeat==metaRepeat &&
		                    		!utlTask.completed && !metaIsCompleted &&
		                    		utlTask.due_date>metaDueDate &&
		                    		metaDueDate>0 && metaReminder>0 &&
		                    		utlTask.reminder>metaReminder &&
		                    		(utlTask.due_date-utlTask.reminder)==60000)
		                    	{
		                    		// Get the hours and minutes of the meta and TD reminders:
	            					GregorianCalendar cal = new GregorianCalendar(TimeZone.
	            						getTimeZone(settings.getString("home_time_zone",
	            						"")));
	            					cal.setTimeInMillis(utlTask.reminder+60000);
	            					int tdHour = cal.get(Calendar.HOUR);
	            					int tdMin = cal.get(Calendar.MINUTE);
	            					cal.setTimeInMillis(metaReminder);
	            					int localHour = cal.get(Calendar.HOUR);
	            					int localMin = cal.get(Calendar.MINUTE);
	            					
	            					if (localHour==tdHour && localMin==tdMin)
	            					{
		            					// The task was edited at TD (likely marked complete)
		            					// and the newly generated task should not have a 
		            					// due time here.
		            					utlTask.uses_due_time = false;
		            					utlTask.reminder = utlTask.due_date;
	            					}
		                    	}
		                    	
		                    	if (!metaIsPro && utlTask.repeat>0 && metaRepeat>0 &&
		                    		utlTask.repeat==metaRepeat &&
		                    		!utlTask.completed && !metaIsCompleted &&
		                    		utlTask.due_date>metaDueDate &&
		                    		metaDueDate>0 &&
		                    		utlTask.reminder>metaReminder &&
		                    		(utlTask.due_date-utlTask.reminder)==(60*60000))
		                    	{
		                    		// Get the hours and minutes of the meta and TD reminders:
	            					GregorianCalendar cal = new GregorianCalendar(TimeZone.
	            						getTimeZone(settings.getString("home_time_zone",
	            						"")));
	            					cal.setTimeInMillis(utlTask.reminder);
	            					int tdHour = cal.get(Calendar.HOUR);
	            					int tdMin = cal.get(Calendar.MINUTE);
	            					cal.setTimeInMillis(metaReminder);
	            					int localHour = cal.get(Calendar.HOUR);
	            					int localMin = cal.get(Calendar.MINUTE);
	            					
	            					if (localHour==tdHour && localMin==tdMin)
	            					{
		            					// The task was edited at TD (likely marked complete)
		            					// and the newly generated task should not have a 
		            					// due time here.
		            					utlTask.uses_due_time = false;
	            					}
		                    	}
		                    }
	                    }
	                }
	                if (meta!=null && (json.isNull("remind") || json.getLong("remind")==0)
	                	&& metaReminder>0 && !metaReminderUploaded)
	                {
	                	// The reminder could not be represented in TD, but it is in
	                	// the meta data.
	                	utlTask.reminder = metaReminder;
	                }
	                
	                if (!utlTask.uses_due_time && utlTask.due_date>0)
	                {
	                	// If, after processing the due date, due time, and reminder, the
	                	// task has no due time, we need to make sure the actual due time 
	                	// stored is at midnight.  This ensures correct sorting later.
	                	utlTask.due_date = Util.getMidnight(utlTask.due_date,context);
	                }
	                
	                // Nag settings (stored in the "meta" field):
	                if (meta!=null && meta.has("nag"))
	                	utlTask.nag = meta.getBoolean("nag");
	                
	                // Location reminder settings (stored in the "meta" field):
	                if (meta !=null && utlTask.location_id>0 && meta.has("location_reminder"))
	                	utlTask.location_reminder = meta.getBoolean("location_reminder");
	                if (meta !=null && utlTask.location_id>0 && meta.has("loc_rem"))
	                	utlTask.location_reminder = meta.getBoolean("loc_rem");
	                if (meta !=null && utlTask.location_id>0 && meta.has("location_nag"))
	                	utlTask.location_nag = meta.getBoolean("location_nag");
	                if (meta !=null && utlTask.location_id>0 && meta.has("loc_nag"))
	                	utlTask.location_nag = meta.getBoolean("loc_nag");
	                
	                
	                // Start Date and Time:
	                if (!json.isNull("startdate") && json.getLong("startdate")>0)
	                {
	                    if (!json.isNull("starttime") && json.getLong("starttime")>86400)
	                    {
	                    	// The task has both a start time and a start date
	                    	// (If the task has a start time with no start date, it is ignored)
	                    	utlTask.start_date = timeShift(context,json.getLong("starttime")
	                    		*1000,false);
	                    	utlTask.uses_start_time = true;
	                    }
	                    else
	                    {
	                    	// The task has a start date with no start time.
	                    	utlTask.start_date = Util.getMidnight(timeShift(context,
	                    		json.getLong("startdate")*1000,false),context);
	                    	utlTask.uses_start_time = false;
	                    }
	                }
	
	                utlTask.status = json.getInt("status");
	                utlTask.length = json.getInt("length");
	                utlTask.priority = json.getInt("priority")+2;
	                utlTask.star = json.getInt("star")>0 ? true : false;
	                if (!json.isNull("note") && json.getString("note").length()>0)
	                {
	                    utlTask.note = json.getString("note");
	                    
	                    // Work around a Toodledo bug that causes < to be sent as &lt;
	                    utlTask.note = utlTask.note.replace("&lt;", "<");
	                }
	                utlTask.timer = json.getInt("timer");
	                if (!json.isNull("timeron") && json.getLong("timeron")>0)
	                {
	                	utlTask.timer_start_time = json.getLong("timeron")*1000;
	                }
	                if (!json.isNull("completed") && json.getLong("completed")>0)
	                {
	                	utlTask.completion_date = timeShift(context,json.getLong(
                			"completed")*1000,false);     
	                }
	                
	                // Contact:
	                utlTask.contactLookupKey = metaContactLookupKey;
	                
	                // Collaboration Settings:
	                if (json.has("shared"))
	                	utlTask.is_joint = json.getInt("shared")==1 ? true : false;
	                else
	                	utlTask.is_joint = false;
	                if (json.has("sharedowner"))
	                	utlTask.owner_remote_id = json.getString("sharedowner");
	                else
	                	utlTask.owner_remote_id = "";
	                utlTask.shared_with = "";
	                try
	                {
		                JSONArray sharedWith = json.getJSONArray("sharedwith");
		                for (int k=0; k<sharedWith.length(); k++)
		                {
		                	if (k>0) utlTask.shared_with += "\n";
		                	utlTask.shared_with += sharedWith.getString(k);
		                }
	                }
	                catch (JSONException je)
	                {
	                	// Nothing to do.  We just assume the task is not shared with anyone.
	                }
	                if (json.has("addedby"))
	                	utlTask.added_by = json.getString("addedby");
	                else
	                	utlTask.added_by = "";
	                	
	                taskList.add(utlTask);
	                
	                // Task Tags:
	                if (!!json.isNull("tag") || json.getString("tag").length()==0)
	                {
	                    tagList.add("");
	                }
	                else
	                {
	                    tagList.add(json.getString("tag"));
	                }
	            }
        	}
        	catch (JSONException e)
        	{
        		Util.log("ToodledoInterface: Got JSONException when parsing a downloaded task. "+
        			e.getMessage());
        		try
        		{
        			Util.log("ToodledoInterface: "+parseResult.array.getJSONObject(i+1).toString(4));
        		}
        		catch (JSONException e2)
        		{
        		}
        		return INTERFACE_ERROR;
        	}
            
            // If we have more tasks, then we need to requery.  TD only allows a certain
            // number of tasks to be downloaded at a time:
            if (start+numReturned < totalTasks)            	
            {
            	start+=numReturned;
            	
            	if (sendPercentComplete)
            	{
            		Float startF = new Float(start);
            		Float totalF = new Float(totalTasks);
            		Float totalPerF = new Float(totalPercentToAdd);
            		Float percentF = new Float(basePercent) + totalPerF*(startF/totalF);
            		int percent = percentF.intValue();
            		for (int j=clients.size()-1; j>=0; j--)
                    {
                        try
                        {
                            clients.get(j).send(Message.obtain(null,Synchronizer.
                            	PERCENT_COMPLETE_MSG,percent,0));
                        }
                        catch (RemoteException e)
                        {
                        	// Just ignore
                        }
                    }
            	}
            }
            else
            {
            	if (sendPercentComplete)
            	{
            		int percent = basePercent+totalPercentToAdd;
            		for (int j=clients.size()-1; j>=0; j--)
                    {
                        try
                        {
                            clients.get(j).send(Message.obtain(null,Synchronizer.
                            	PERCENT_COMPLETE_MSG,percent,0));
                        }
                        catch (RemoteException e)
                        {
                        	// Just ignore
                        }
                    }
            	}
            	break;
            }
        }
        return SUCCESS;
    }
    
    // Get tasks, for the purpose of downloading location data.  This is run one time,
    // after the user upgrades from 1.3.1 to 1.4.  
    // This assumes that the locations themselves have already been synced with Toodledo.
    public int downloadLocations(UTLAccount a)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // We need some handles into the database:
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        
        int start = 0;
        int num = 500;
        while (true)
        {
        	// Query the server for the tasks:
        	ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
            parameters.add(new BasicNameValuePair("access_token",_account.current_token));
            parameters.add(new BasicNameValuePair("after",new Long(0).toString()));
            parameters.add(new BasicNameValuePair("start",new Integer(start).toString()));
            parameters.add(new BasicNameValuePair("num",new Integer(num).toString()));
            parameters.add(new BasicNameValuePair("fields","location"));
            parameters.add(new BasicNameValuePair("comp","0"));
            String result = httpRequest(getBaseURL()+"tasks/get.php",HttpMethod.GET,parameters);
            
            // Parse the response and return an error code if necessary:
        	ParseResult parseResult = parseApiResponse(result);
        	if (parseResult.result != SUCCESS)
        		return parseResult.result;
        	
        	int i = 0;
        	int numReturned = 0;
        	int totalTasks = 0;
        	try
        	{
	    		// The first item in the returned array contains information on the number
	        	// of tasks returned.
	        	numReturned = parseResult.array.getJSONObject(0).getInt("num");
	            totalTasks =  parseResult.array.getJSONObject(0).getInt("total");
	            
	            for (i=0; i<numReturned; i++)
	            {
	            	JSONObject json = parseResult.array.getJSONObject(i+1);
	            	
	            	// Look up the UTL task ID (if it exists):
	            	UTLTask task = tasksDB.getTask(a._id, json.getLong("id"));
	            	if (task!=null)
	            	{
	            		// The task matches one in our database, so we need to update
	            		// the location.  
	            		long tdLocID = json.getLong("location");
	            		if (tdLocID>0)
	            		{
	            			// The task has a location, so update the local copy of the
	            			// task.  Begin by looking up the UTL location ID:
	            			UTLLocation loc = locDB.getLocation(a._id, tdLocID);
	            			if (loc!=null)
	            			{
	            				// Update the task in the database:
	            				task.location_id = loc._id;
	            				if (!tasksDB.modifyTask(task))
	            				{
	            					Util.log("ToodledoInterface: Failed to add location to task in DB.");
	            					return INTERNAL_ERROR;
	            				}
	            			}
	            		}
	            	}
	            }
        	}
        	catch (JSONException e)
        	{
        		Util.log("ToodledoInterface: Got JSONException when parsing a downloaded task. "+
        			e.getMessage());
        		try
        		{
        			Util.log("ToodledoInterface: "+parseResult.array.getJSONObject(i+1).toString(4));
        		}
        		catch (JSONException e2)
        		{
        		}
        		return INTERFACE_ERROR;
        	}
        	
        	// If we have more tasks, then we need to requery.  TD only allows a certain
            // number of tasks to be downloaded at a time:
            if (start+numReturned < totalTasks)            	
            {
            	start+=numReturned;
            }
            else
            {
            	break;
            }
        }
        return SUCCESS;
    }
    
    // Add a task.  Returns a result code.  If the task is successfully added, then 
    // the UTLTask instance passed in is updated to include the Toodledo ID, and the 
    // database is also updated to include the Toodledo ID.
    // IMPORTANT: The caller has the responsibility to tweak the due time and reminder 
    // values of the passed-in UTLTask to compensate for Toodledo's limitations.
    public int addTask(UTLTask utlTask, android.content.Context context)
    {        
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        ContextsDbAdapter contextsDB = new ContextsDbAdapter();
        GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        TagsDbAdapter tagsDB = new TagsDbAdapter();
        Cursor c;

        SharedPreferences settings = context.getSharedPreferences("UTL_Prefs",0);

    	// This JSON object holds the task data that is being uploaded:
    	JSONObject json = new JSONObject();
    	String result;
    	try
    	{
        json.put("title",utlTask.title);
        
        // The "meta" field, which contains information not otherwise available in Toodledo.
        // This must be done first, because the tweakTaskForUploading() may alter the fields
        // stored here:
        JSONObject meta = new JSONObject();
        meta.put("nag", utlTask.nag);
        meta.put("uses_due_time", utlTask.uses_due_time);
        meta.put("reminder",timeShift(context,utlTask.reminder,true));
        meta.put("repeat", utlTask.repeat);
        meta.put("due_date", timeShift(context,utlTask.due_date,true));
        meta.put("is_pro", _account.pro);
        meta.put("is_completed",utlTask.completed);
        meta.put("loc_rem",utlTask.location_reminder);
        meta.put("loc_nag",utlTask.location_nag);
        meta.put("contact", utlTask.contactLookupKey);
        meta.put("sort_order", utlTask.sort_order);
        if (Util.isValid(utlTask.uuid))
            meta.put("uuid",utlTask.uuid);
        
        // The due time and reminder settings may need to be updated due to limits on 
        // free accounts and TD's inability to store an explicit reminder time:
        boolean isReminderRepresented = tweakTaskForUploading(utlTask,context);
        
        // The meta data also includes a field specifying whether reminder data could be 
        // uploaded to TD.  For free accounts, the reminder data cannot always be represented
        // due to free account limits.
        meta.put("was_rem_uploaded",isReminderRepresented);
        
        // Encode the meta data and place it in the JSON structure:
        String metaString = meta.toString();
        if (metaString.length()>MAX_META_LENGTH)
        {
            meta.remove("uuid");
            Util.log("ToodledoInterface: Removing uuid meta field due to length limits.");
            metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	// Trim location reminder info to reduce the size.
        	meta.remove("loc_rem");
        	meta.remove("loc_nag");
        	Util.log("ToodledoInterface: Removing location data due to length limits.");
        	metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	// Try removing the contact info to reduce the size.
        	Util.log("ToodledoInterface: Removing contact from meta data due to length limits.");
        	meta.remove("contact");
        	metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	meta.remove("was_rem_uploaded");
        	Util.log("ToodledoInterface: Removing was_rem_uploaded meta field due to length limits.");
        	metaString = meta.toString();
        }
        json.put("meta", metaString);
        Util.log("ToodledoInterface: Metadata sent with Task '"+utlTask.title+"': "+metaString);
        
        // Folder:
        if (utlTask.folder_id>0)
        {
            c = foldersDB.getFolder(utlTask.folder_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("folder", Util.cLong(c,"td_id"));
                }
                else
                {
                    logInternalError(context,"Folder TD ID is -1 in ToodledoInterface.addTask.");
                    c.close();
                    return INTERNAL_ERROR;
                }
            }
            else
            {
                logInternalError(context,"Task references nonexistent folder in ToodledoInterface.addTask");
                c.close();
                return INTERNAL_ERROR;
            }
            c.close();
        }
        
        // Context:
        if (utlTask.context_id>0)
        {
            c = contextsDB.getContext(utlTask.context_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("context", Util.cLong(c,"td_id"));
                }
                else
                {
                    logInternalError(context,"Context TD ID is -1 in ToodledoInterface.addTask.");
                    c.close();
                    return INTERNAL_ERROR;
                }
            }
            else
            {
            	logInternalError(context,"Task references nonexistent context in ToodledoInterface.addTask");
                c.close();
                return INTERNAL_ERROR;
            }
            c.close();
        }
        
        // Goal:
        if (utlTask.goal_id>0)
        {
            c = goalsDB.getGoal(utlTask.goal_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("goal", Util.cLong(c,"td_id"));
                }
                else
                {
                	logInternalError(context,"Goal TD ID is -1 in ToodledoInterface.addTask.");
                    c.close();
                    return INTERNAL_ERROR;
                }
            }
            else
            {
            	logInternalError(context,"Task references nonexistent goal in ToodledoInterface.addTask");
                c.close();
                return INTERNAL_ERROR;
            }
            c.close();
        }

        // Location:
        if (utlTask.location_id>0)
        {
        	LocationsDbAdapter locDB = new LocationsDbAdapter();
        	UTLLocation loc = locDB.getLocation(utlTask.location_id);
        	if (loc!=null)
        	{
        		if (loc.td_id>-1)
        			json.put("location", loc.td_id);
        		else
        		{
        			logInternalError(context,"Location TD ID is -1 in ToodledoInterface.addTask.");
        			return INTERNAL_ERROR;
        		}
        	}
        	else
        	{
        		logInternalError(context,"Task references nonexistent location in ToodledoInterface.addTask");
        		return INTERNAL_ERROR;
        	}
        }
        
        // Parent ID, if any:
        if (utlTask.parent_id>0)
        {
            // Retrieve the TD parent ID from the database:
            UTLTask parent = tasksDB.getTask(utlTask.parent_id);
            if (parent==null)
            {
                // This should not happen!
            	logInternalError(context,"Task references a nonexistent parent in ToodledoInterface.addTask.  Removing parent from task."+
                	utlTask.title);
                utlTask.parent_id = 0;
                (new TasksDbAdapter()).modifyTask(utlTask);
            }
            else
            {
                if (parent.td_id>-1)
                {
                	json.put("parent",parent.td_id);
                }
                else
                {
                	// This should not happen, but I have seen this in one log file.
                	// To fix, upload this as a parent task.
                	logInternalError(context,"Parent task has not yet been added to Toodledo in "+
                        "ToodledoInterface.addTask. Removing parent link.");
                	utlTask.parent_id = 0;
                    (new TasksDbAdapter()).modifyTask(utlTask);
                }
            }
        }
        
        // Due Date:
        if (utlTask.due_date>0)
        {
        	json.put("duedate", timeShift(context,utlTask.due_date,true)/1000);
        	
            // Due Time:
            if (utlTask.uses_due_time)
            {
            	json.put("duetime", timeShift(context,utlTask.due_date,true)/1000);
            }

            if (utlTask.due_modifier.equals("due_on"))
            {
            	json.put("duedatemod", 1);
            }
            if (utlTask.due_modifier.equals("optionally_on"))
            {
            	json.put("duedatemod", 3);
            }
        }
        
        
        // Reminder:
        if (utlTask.reminder>0)
        {
            long minuteDiff = (utlTask.due_date-utlTask.reminder)/1000/60;
            if (minuteDiff==0)
            {
            	minuteDiff = 1;
            }
            json.put("remind",minuteDiff);
        }
        
        // Repeat:
        json.put("repeat",getToodledoRepeatString(utlTask));

        // Start Date and Time:
        if (utlTask.start_date>0)
        {
        	json.put("startdate", timeShift(context,utlTask.start_date,true)/1000);
	        if (utlTask.uses_start_time)
	        	json.put("starttime", timeShift(context,utlTask.start_date,true)/1000);
        }
        
        // Other fields:
        json.put("status",utlTask.status);
        if (utlTask.length>0)
        {
            json.put("length", utlTask.length);
        }
        if (utlTask.priority>0)
        {
        	json.put("priority", utlTask.priority-2);
        }
        json.put("star",utlTask.star ? 1 : 0);
        if (utlTask.note.length()>0)
        {
        	json.put("note", utlTask.note);
        }
        
        // Completion info:
        if (utlTask.completed)
        	json.put("completed", timeShift(context,utlTask.completion_date,true)/1000);
        else
        	json.put("completed", 0);
        
        // Timer Info:
        if (utlTask.timer>0)
        	json.put("timer", utlTask.timer);
        if (utlTask.timer_start_time>0)
        	json.put("timeron", utlTask.timer_start_time/1000);

        // Collaboration Information:
        json.put("shared", utlTask.is_joint ? 1 : 0);
        if (utlTask.owner_remote_id.length()>0)
        	json.put("sharedowner", utlTask.owner_remote_id);
        if (utlTask.shared_with.length()>0)
        {
        	String[] remoteIDs = utlTask.shared_with.split("\n");
        	JSONArray jsonArray = new JSONArray();
        	for (int k=0; k<remoteIDs.length; k++)
        	{
        		jsonArray.put(remoteIDs[k]);
        	}
        	json.put("sharedwith", jsonArray);
        }
        if (utlTask.added_by.length()>0)
        	json.put("addedby",utlTask.added_by);
        
        // Tags:
        String[] tagArray = tagsDB.getTagsInDbOrder(utlTask._id);
        if (tagArray.length>0)
        {
            String tagString = tagArray[0];
            for (int i=1; i<tagArray.length; i++)
            {
            	if ((tagString.length()+tagArray[i].length()+1)<=Util.MAX_TAG_STRING_LENGTH)
            		tagString = tagString + ","+tagArray[i];
            }
            json.put("tag",tagString);
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        JSONArray array = new JSONArray();
        array.put(json);
        parameters.add(new BasicNameValuePair("tasks",array.toString()));
    	
        // Send the data to the server:
        result = httpRequest(getBaseURL()+"tasks/add.php",HttpMethod.POST,parameters);        
    	}
    	catch (JSONException e)
    	{
    		// The JSON object to pass to the server could not be created.
    		logInternalError(context,"Could not create JSON object when adding task. "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+json.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERNAL_ERROR;
    	}
    	
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addTask(utlTask,context);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
           
        // Extract the new task's ID from the response:
    	long taskID;
    	try
    	{
	    	json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode") && json.getInt("errorCode")!=12)
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
	    	taskID = json.getLong("id");
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when adding " +
                "task.  " + e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	        
        // If we get here, the add was successful.  Update the UTLTask object passed in 
        // to include the TD task ID, and also update the DB:
        utlTask.td_id = taskID;
        SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("td_id", taskID);
        int resultCode = db.update("tasks", values, "_id="+utlTask._id, null);
        if (resultCode==0)
        {
            Util.log("ToodledoInterface: Could not modify TD ID in tasks table, in ToodledoInterface.addTask.");
            return INTERNAL_ERROR;
        }

    	// If the task is shared, then we need an additional API call to inform 
    	// Toodledo of the users it's being shared with.
    	if (utlTask.owner_remote_id.equals(_account.td_userid) && _hasCollaborators &&
    		utlTask.shared_with.length()>0)
    	{
    		return updateSharing(utlTask,context);
    	}
    		
        return SUCCESS;
    }
    
    // Edit a task.  Returns a result code.
    // IMPORTANT: The caller has the responsibility to tweak the due time and reminder 
    // values of the passed-in UTLTask to compensate for Toodledo's limitations.
    // The serverOffset field is in half-hours, and is taken from the server info API call.
    public int editTask(UTLTask utlTask, android.content.Context context)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        ContextsDbAdapter contextsDB = new ContextsDbAdapter();
        GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        TagsDbAdapter tagsDB = new TagsDbAdapter();
        Cursor c;

        if (utlTask.td_id==-1)
        {
            // This should not happen!
        	logInternalError(context,"Unsynced task passed into ToodledoInterface.editTask");
            return INTERNAL_ERROR;
        }
        
        SharedPreferences settings = context.getSharedPreferences("UTL_Prefs",0);

    	// This JSON object holds the task data that is being uploaded:
    	JSONObject json = new JSONObject();
    	String result;
    	try
    	{
        json.put("title",utlTask.title);
        json.put("id",utlTask.td_id);
                
        // The "meta" field, which contains information not otherwise available in Toodledo.
        // This must be done first, because the tweakTaskForUploading() may alter the fields
        // stored here:
        JSONObject meta = new JSONObject();
        meta.put("nag", utlTask.nag);
        meta.put("uses_due_time", utlTask.uses_due_time);
        meta.put("reminder",timeShift(context,utlTask.reminder,true));
        meta.put("repeat", utlTask.repeat);
        meta.put("due_date", timeShift(context,utlTask.due_date,true));
        meta.put("is_pro", _account.pro);
        meta.put("is_completed",utlTask.completed);
        meta.put("loc_rem",utlTask.location_reminder);
        meta.put("loc_nag",utlTask.location_nag);
        meta.put("contact", utlTask.contactLookupKey);
        meta.put("sort_order", utlTask.sort_order);
        if (Util.isValid(utlTask.uuid))
            meta.put("uuid",utlTask.uuid);
        
        // The due time and reminder settings may need to be updated due to limits on 
        // free accounts and TD's inability to store an explicit reminder time:
        boolean isReminderRepresented = tweakTaskForUploading(utlTask,context);
        
        // The meta data also includes a field specifying whether reminder data could be 
        // uploaded to TD.  For free accounts, the reminder data cannot always be represented
        // due to free account limits.
        meta.put("was_rem_uploaded",isReminderRepresented);
        
        // Encode the meta data and place it in the JSON structure:
        String metaString = meta.toString();
        if (metaString.length()>MAX_META_LENGTH)
        {
            meta.remove("uuid");
            Util.log("ToodledoInterface: Removing uuid meta field due to length limits.");
            metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	// Trim location reminder info to reduce the size.
        	meta.remove("loc_rem");
        	meta.remove("loc_nag");
        	Util.log("ToodledoInterface: Removing location data due to length limits.");
        	metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	// Try removing the contact info to reduce the size.
        	Util.log("ToodledoInterface: Removing contact from meta data due to length limits.");
        	meta.remove("contact");
        	metaString = meta.toString();
        }
        if (metaString.length()>MAX_META_LENGTH)
        {
        	meta.remove("was_rem_uploaded");
        	Util.log("ToodledoInterface: Removing was_rem_uploaded meta field due to length limits.");
        	metaString = meta.toString();
        }
        json.put("meta", metaString);
        Util.log("ToodledoInterface: Metadata sent with Task '"+utlTask.title+"': "+metaString);
        
        // Folder:
        json.put("folder", 0);
        if (utlTask.folder_id>0)
        {
            c = foldersDB.getFolder(utlTask.folder_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("folder", Util.cLong(c,"td_id"));
                }
                else
                {
                	logInternalError(context,"Folder TD ID is -1 in ToodledoInterface.editTask.");
                    return INTERNAL_ERROR;
                }
            }
            else
            {
            	logInternalError(context,"Task references nonexistent folder in ToodledoInterface.editTask");
                return INTERNAL_ERROR;
            }
        }
        
        // Context:
        json.put("context", 0);
        if (utlTask.context_id>0)
        {
            c = contextsDB.getContext(utlTask.context_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("context", Util.cLong(c,"td_id"));;
                }
                else
                {
                	logInternalError(context,"Context TD ID is -1 in ToodledoInterface.editTask.");
                    return INTERNAL_ERROR;
                }
            }
            else
            {
            	logInternalError(context,"Task references nonexistent context in ToodledoInterface.editTask");
                return INTERNAL_ERROR;
            }
        }
        
        // Goal:
        json.put("goal", 0);
        if (utlTask.goal_id>0)
        {
            c = goalsDB.getGoal(utlTask.goal_id);
            if (c.moveToFirst())
            {
                if (Util.cLong(c, "td_id")>-1)
                {
                	json.put("goal", Util.cLong(c,"td_id"));
                }
                else
                {
                	logInternalError(context,"Goal TD ID is -1 in ToodledoInterface.editTask.");
                    return INTERNAL_ERROR;
                }
            }
            else
            {
            	logInternalError(context,"Task references nonexistent goal in ToodledoInterface.editTask");
                return INTERNAL_ERROR;
            }
        }

        // Location:
        json.put("location", 0);
        if (utlTask.location_id>0)
        {
        	LocationsDbAdapter locDB = new LocationsDbAdapter();
        	UTLLocation loc = locDB.getLocation(utlTask.location_id);
        	if (loc!=null)
        	{
        		if (loc.td_id>-1)
        			json.put("location", loc.td_id);
        		else
        		{
        			logInternalError(context,"Location TD ID is -1 in ToodledoInterface.editTask.");
        			return INTERNAL_ERROR;
        		}
        	}
        	else
        	{
        		logInternalError(context,"Task references nonexistent location in ToodledoInterface.editTask");
        		return INTERNAL_ERROR;
        	}
        }

        // Parent ID, if any:
        json.put("parent",0);
        if (utlTask.parent_id>0)
        {
            // Retrieve the TD parent ID from the database:
            UTLTask parent = tasksDB.getTask(utlTask.parent_id);
            if (parent==null)
            {
                // This should not happen!
            	logInternalError(context,"Task references a nonexistent parent in ToodledoInterface.editTask. Removing parent from task.");
                utlTask.parent_id = 0;
                (new TasksDbAdapter()).modifyTask(utlTask);
            }
            else
            {
                if (parent.td_id>-1)
                {
                	json.put("parent",parent.td_id);
                }
                else
                {
                	// This should not happen, but I have seen this in one log file.
                	// To fix, upload this as a parent task.
                	logInternalError(context,"Parent task has not yet been added to Toodledo in "+
                        "ToodledoInterface.editTask. Removing parent link.");
                	utlTask.parent_id = 0;
                    (new TasksDbAdapter()).modifyTask(utlTask);
                }
            }
        }

        // Reminder:
        if (utlTask.reminder>0)
        {
            long minuteDiff = (utlTask.due_date-utlTask.reminder)/1000/60;
            if (minuteDiff==0)
            {
            	minuteDiff = 1;
            }
            json.put("remind",minuteDiff);
        }
        else
        {
        	json.put("remind",0);
        }
        
        // Due Date:
        if (utlTask.due_date>0)
        {
        	json.put("duedate", timeShift(context,utlTask.due_date,true)/1000);
        	
        	// Due date modifier:
            if (utlTask.due_modifier.equals("due_on"))
            	json.put("duedatemod", 1);
            else if (utlTask.due_modifier.equals("optionally_on"))
            	json.put("duedatemod", 3);
            else
            	json.put("duedatemod", 0);
            
            // Due Time:
            if (utlTask.uses_due_time)
            {
            	json.put("duetime", timeShift(context,utlTask.due_date,true)/1000);
            }
            else
            	json.put("duetime", 0);
        }
        else
        {
        	json.put("duedate", 0);
        	json.put("duetime", 0);
        	json.put("duedatemod", 0);
        }

        // Repeat:
        json.put("repeat",getToodledoRepeatString(utlTask));

        // Start Date and Time:
        if (utlTask.start_date>0)
        {
        	json.put("startdate", timeShift(context,utlTask.start_date,true)/1000);
            if (utlTask.uses_start_time)
            	json.put("starttime", timeShift(context,utlTask.start_date,true)/1000);
            else
            	json.put("starttime", 0);
        }
        else
        {
        	json.put("startdate", 0);
        	json.put("starttime",0);
        }
        
        // Other fields:
        json.put("status",utlTask.status);
        json.put("length", utlTask.length);
        if (utlTask.priority>0)
        {
        	json.put("priority", utlTask.priority-2);
        }
        json.put("star",utlTask.star ? 1 : 0);
        json.put("note", utlTask.note);
        json.put("reschedule",0);
        
        // Completion info:
        if (utlTask.completed)
        	json.put("completed", timeShift(context,utlTask.completion_date,true)/1000);
        else
        	json.put("completed", 0);
        
        // Timer Info:
    	json.put("timer", utlTask.timer);
        if (utlTask.timer_start_time>0)
        	json.put("timeron", utlTask.timer_start_time/1000);
        else
            json.put("timeron", 0);

        // There is an issue in Toodledo in which the timer value is incorrect if the timer
        // is stopped at the same time the task is marked as completed.  Sending the task update
        // twice should fix this.
        boolean sendTwice = false;
        if (utlTask.completed && utlTask.timer>0 && utlTask.timer_start_time==0)
        	sendTwice = true;
        
        // Collaboration Information:
        json.put("shared", utlTask.is_joint ? 1 : 0);
        if (utlTask.owner_remote_id.length()>0)
        	json.put("sharedowner", utlTask.owner_remote_id);
        if (utlTask.shared_with.length()>0)
        {
        	String[] remoteIDs = utlTask.shared_with.split("\n");
        	JSONArray jsonArray = new JSONArray();
        	for (int k=0; k<remoteIDs.length; k++)
        	{
        		jsonArray.put(remoteIDs[k]);
        	}
        	json.put("sharedwith", jsonArray);
        }
        if (utlTask.added_by.length()>0)
        	json.put("addedby",utlTask.added_by);

        // Tags:
        String[] tagArray = tagsDB.getTagsInDbOrder(utlTask._id);
        if (tagArray.length>0)
        {
            String tagString = tagArray[0];
            for (int i=1; i<tagArray.length; i++)
            {
            	if ((tagString.length()+tagArray[i].length()+1)<=Util.MAX_TAG_STRING_LENGTH)
            		tagString = tagString + ","+tagArray[i];
            }
            json.put("tag",tagString);
        }
        else
        {
        	json.put("tag","");
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        JSONArray array = new JSONArray();
        array.put(json);
        parameters.add(new BasicNameValuePair("tasks",array.toString()));
    	
        // Send the data to the server:
        result = httpRequest(getBaseURL()+"tasks/edit.php",HttpMethod.POST,parameters); 
        if (sendTwice)
        	result = httpRequest(getBaseURL()+"tasks/edit.php",HttpMethod.POST,parameters);
    	}
    	catch (JSONException e)
    	{
    		// The JSON object to pass to the server could not be created.
    		logInternalError(context,"Could not create JSON object when editing task. "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+json.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERNAL_ERROR;
    	}
    	
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editTask(utlTask,context);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
        {
            // Check to see if error code 605 (invalid task) was received.  If this occurs,
            // add the task instead.  Otherwise, it will never sync and UTL will continuously
            // reattempt it.
            try
            {
                if (parseResult.json != null && parseResult.json.has("errorCode") && parseResult.json.
                    getInt("errorCode")==605)
                {
                    Util.log("ToodledoInterface: Got error code 605 (invalid task). Trying to "+
                        "add the task instead. Task Name: "+utlTask.title);
                    return addTask(utlTask, context);
                }
                else if (parseResult.json!=null && parseResult.json.has("errorDesc") &&
                    parseResult.json.getString("errorDesc").toLowerCase().contains("invalid task " +
                    "id"))
                {
                    Log.e(TAG,"Unexpected Invalid Task ID Error","Got an 'invalid task id' error "+
                        "without the 605 error code: "+parseResult.json.toString());
                    return addTask(utlTask, context);
                }
            }
            catch (JSONException je) { }
            return parseResult.result;
        }
    	
    	// Extract the first item from the response array. Make sure it's not an error.
    	try
    	{
	    	json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode") && json.getInt("errorCode")!=606)
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when editing " +
                "task.  " + e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	
    	// No errors. If the task is shared, then we need an additional API call to inform 
    	// Toodledo of the users it's being shared with.
    	if (utlTask.owner_remote_id.equals(_account.td_userid) && _hasCollaborators &&
    		utlTask.shared_with_changed)
    	{
    		Util.log("ToodledoInterface: Updating sharing for task: "+utlTask.title);
    		Util.log("ToodledoInterface: Collaborators: '"+utlTask.shared_with+"'");
    		return updateSharing(utlTask,context);
    	}
    		
        // No errors.  Return the success code.
        return SUCCESS;       
    }
    
    // Reassign a task to a collaborator.  Returns a result code.
    public int reassignTask(long taskID, String newOwnerID, android.content.Context context)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        
        // Fetch the task from the database:
        UTLTask t = tasksDB.getTask(taskID);
        if (t == null)
        {
        	// Return SUCCESS to keep the synchronization going, but log an error.
        	logInternalError(context,"Task ID passed into reassignTask does not exist in DB.");
        	return SUCCESS;
        }
        
        if (t.td_id==-1)
        {
        	// The task has not synced yet.  This should not happen, but if it does we will return
        	// SUCCESS to keep the sync going and log the error.
        	logInternalError(context,"Unsynced task passed to reassignTask()");
        	return SUCCESS;
        }
        
    	// Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",Long.toString(t.td_id)));
        parameters.add(new BasicNameValuePair("assign",newOwnerID));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"tasks/reassign.php",HttpMethod.POST,parameters);
        
       	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return reassignTask(taskID,newOwnerID,context);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the success or failure from the response.
    	try
    	{
    		JSONObject json = parseResult.json;
	    	if (!json.isNull("errorCode"))
	    	{
	    		// An object was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
	    	if (json.getInt("reassigned")>0)
	    		return SUCCESS;
	    	else
	    	{
	    		_lastRejectMessage = Util.getString(R.string.reassign_failure);
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when reassigning "+
    			"task.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    }
    
    // Adjust sharing for a task.  Returns a result code.
    public int updateSharing(UTLTask t, android.content.Context context)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        
        if (t.td_id==-1)
        {
        	// The task has not synced yet.  This should not happen, but if it does we will return
        	// SUCCESS to keep the sync going and log the error.
        	logInternalError(context,"Unsynced task passed to updateSharing()");
        	return SUCCESS;
        }
        
        // Convert the user ID list to a JSON Array:
        JSONArray jsonArray = new JSONArray();
        String shareString = "[]";
        if (t.shared_with.length()>0)
        {
            String[] userIDs = t.shared_with.split("\n");
            for (int i=0; i<userIDs.length; i++)
            {
                jsonArray.put(userIDs[i]);
            }
            shareString = jsonArray.toString();
        }

    	// Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("id",Long.toString(t.td_id)));
        parameters.add(new BasicNameValuePair("share",shareString));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"tasks/share.php",HttpMethod.POST,parameters);
        
       	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return updateSharing(t,context);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the success or failure from the response.
    	try
    	{
    		JSONObject json = parseResult.json;
	    	if (!json.isNull("errorCode"))
	    	{
	    		// An object was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
	    	if (json.getInt("shared")>0)
	    	{
	    		// Clear the shared_with_changed field.
	    		Util.log("ToodledoInterface: Sharing successfully changed.");
	    		Util.db().execSQL("update tasks set shared_with_changed=0 where _id="+t._id);
	    		return SUCCESS;
	    	}
	    	else
	    	{
	    		_lastRejectMessage = Util.getString(R.string.sharing_update_failure);
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when sharing "+
    			"task.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    }

    // Delete a task.  The input is Toodledo's task ID.  Returns a result code:
    public int deleteTask(long TDID)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        JSONArray array = new JSONArray();
        array.put(TDID);
        parameters.add(new BasicNameValuePair("tasks",array.toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"tasks/delete.php",HttpMethod.POST,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteTask(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the first item from the response array. Make sure it's not an error.
    	try
    	{
	    	JSONObject json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode") && json.getInt("errorCode")!=12)
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when deleting "+
    			"task.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	
        // No errors.  Return the success code.
        return SUCCESS;                
    }
    
    // Get deleted tasks from Toodledo.  The input is a timestamp in ms.  Deleted tasks 
    // after this date/time will be returned.  The function populates an ArrayList of 
    // Toodledo ID numbers.  A result code is returned.
    public int getDeletedTasks(long after, ArrayList<Long> idList)
    {
        int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // Query the server for the tasks:
    	ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("after",new Long(after/1000).toString()));
        String result = httpRequest(getBaseURL()+"tasks/deleted.php",HttpMethod.GET,parameters);
    	
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getDeletedTasks(after,idList);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	try
    	{
	    	for (int i=0; i<parseResult.array.length(); i++)
	    	{
	    		JSONObject json = parseResult.array.getJSONObject(i);
	    		if (!json.isNull("id"))
	    		{
	    			idList.add(json.getLong("id"));
	    		}
	    	}
	    	return SUCCESS;
    	}
    	catch (JSONException e)
    	{
			Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONArray when getting "+
				"deleted tasks.  "+e.getMessage());
			try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
			catch (JSONException e2) { }
			return INTERFACE_ERROR;
    	}    	
    }
    
    // Get notes.  The input is a UNIX millisecond timestamp.  Any notes modified after 
    // this time will be returned.  This populates an ArrayList of UTLNote objects 
    // (without the UTL internal DB ID).  This returns a result code.
    // To get all tasks, pass in a zero for the timestamp.
    public int getNotes(long modAfter, ArrayList<UTLNote> noteList)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        Cursor c;
        
        int start = 0;
        int num = 1000;
        while (true)
        {
        	// Query the server for the notes:
	    	ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
            parameters.add(new BasicNameValuePair("access_token",_account.current_token));
	        parameters.add(new BasicNameValuePair("after",new Long(modAfter/1000).toString()));
	        parameters.add(new BasicNameValuePair("start",new Integer(start).toString()));
	        parameters.add(new BasicNameValuePair("num",new Integer(num).toString()));
	        String result = httpRequest(getBaseURL()+"notes/get.php",HttpMethod.GET,parameters);
	        
	    	// Parse the response and return an error code if necessary:
	    	ParseResult parseResult = parseNoteApiResponse(result);
            if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
                && !_gotTokenError)
            {
                // Try again with a new token.
                _gotTokenError = true;
                noteList.clear();
                return getNotes(modAfter,noteList);
            }
            _gotTokenError = false;
	    	if (parseResult.result != SUCCESS)
	    		return parseResult.result;
        
        	int i = 0;
        	int numReturned = 0;
        	int totalNotes = 0;
        	try
        	{
	    		// The first item in the returned array contains information on the number
	        	// of notes returned.
	        	numReturned = parseResult.array.getJSONObject(0).getInt("num");
	            totalNotes =  parseResult.array.getJSONObject(0).getInt("total");
	            
	            for (i=0; i<numReturned; i++)
	            {
	                JSONObject json = parseResult.array.getJSONObject(i+1);
	                
	                // Convert the downloaded JSON object into a UTLNote object:
		        	UTLNote utlNote = new UTLNote();
		        	utlNote.td_id = json.getLong("id");
		        	utlNote.account_id = _accountID;
		        	utlNote.mod_date = json.getLong("modified")*1000;
		        	utlNote.sync_date = System.currentTimeMillis();
		        	utlNote.title = json.getString("title");
		        	utlNote.note = json.getString("text");
		        	
                    // Work around a Toodledo bug that causes < to be sent as &lt;
		        	if (utlNote.note != null)
		        		utlNote.note = utlNote.note.replace("&lt;", "<");

		        	// Folder:
		        	if (!json.isNull("folder") && json.getLong("folder")>0)
		        	{
		        		c = foldersDB.getFolder(_accountID, json.getLong("folder"));
		        		if (!c.moveToFirst())
		        		{
		        			// This should not happen, but we'll handle it gracefully just in
		                    // case.  Log the event and keep the folder blank.
		                    Util.log("ToodledoInterface: Received a note from Toodledo that references a folder "+
		                        "not in the UTL database (TD Note ID: "+utlNote.td_id+", "+
		                        "TD Folder ID: "+json.getLong("folder")+")");
		        		}
		        		else
		        		{
		        			utlNote.folder_id = Util.cLong(c, "_id");
		        		}
		        		c.close();
		        	}
		        	
		        	noteList.add(utlNote);
	            }
        	}
        	catch (JSONException e)
        	{
        		Util.log("ToodledoInterface: Got JSONException when parsing a downloaded note. "+
        			e.getMessage());
        		try
        		{
        			Util.log("ToodledoInterface: "+parseResult.array.getJSONObject(i+1).toString(4));
        		}
        		catch (JSONException e2)
        		{
        		}
        		return INTERFACE_ERROR;
        	}

            // If we have more notes, then we need to requery.  TD only allows a certain
            // number of notes to be downloaded at a time:
            if (start+numReturned < totalNotes)            	
            {
            	start+=numReturned;
            }
            else
            {
            	break;
            }
        }
       
        return SUCCESS;
    }
    
    // Add a note.  Returns a result code.  If the note is successfully added, then 
    // the UTLNote instance passed in is updated to include the Toodledo ID, and the 
    // database is also updated to include the Toodledo ID.
    public int addNote(UTLNote utlNote)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }

        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        NotesDbAdapter notesDB = new NotesDbAdapter();
        Cursor c;
        
        // This JSON object holds the note data that is being uploaded:
        JSONObject json = new JSONObject();
    	String result;
    	try
    	{
    		json.put("title",utlNote.title);
    		json.put("text", utlNote.note);
    		
            if (utlNote.folder_id>0)
            {
            	c = foldersDB.getFolder(utlNote.folder_id);
            	if (c.moveToFirst())
            	{
            		if (Util.cLong(c, "td_id")>-1)
            		{
            			json.put("folder",Util.cLong(c, "td_id"));
            		}
            		else
            		{
            			Util.log("ToodledoInterface: Folder TD ID is -1 in ToodledoInterface.addNote.");
            			c.close();
            			return INTERNAL_ERROR;
            		}
            	}
            	else
            	{
            		Util.log("ToodledoInterface: Folder cannot be found in ToodledoInterface.addNote."+
            			"  Removing the folder from the note.");
            		utlNote.folder_id = 0;
            		notesDB.modifyNote(utlNote);
            	}
            	c.close();
            }
    		
            // Parameters to pass to the server:
            ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
            parameters.add(new BasicNameValuePair("access_token",_account.current_token));
            JSONArray array = new JSONArray();
            array.put(json);
            parameters.add(new BasicNameValuePair("notes",array.toString()));
            
            // Send the data to the server:
            result = httpRequest(getBaseURL()+"notes/add.php",HttpMethod.POST,parameters);
    	}
    	catch (JSONException e)
    	{
    		// The JSON object to pass to the server could not be created.
    		Util.log("ToodledoInterface: Could not create JSON object when adding note. "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+json.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERNAL_ERROR;
    	}
    	
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseNoteApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return addNote(utlNote);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the new note's ID from the response:
    	long noteID;
    	try
    	{
	    	json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode"))
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
	    	noteID = json.getLong("id");
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when adding "+
    			"note.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	
    	// If we get here, the add was successful.  Update the UTLNote object passed in 
        // to include the TD note ID, and also update the DB:
        utlNote.td_id = noteID;
        if (!notesDB.modifyNote(utlNote))
        {
        	Util.log("ToodledoInterface: Could not modify note after adding to Toodledo.");
        	return INTERNAL_ERROR;
        }
        return SUCCESS;
    }
    
    // Edit a note.  Returns a result code:
    public int editNote(UTLNote utlNote)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // We need some handles into the database:
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        Cursor c;
        
        if (utlNote.td_id==-1)
        {
        	Util.log("ToodledoInterface: Unsynced note passed into ToodledoInterface.editNote");
        	return INTERNAL_ERROR;
        }
        
        // This JSON object holds the note data that is being uploaded:
        JSONObject json = new JSONObject();
    	String result;
    	try
    	{
    		json.put("title",utlNote.title);
    		json.put("text", utlNote.note);
    		json.put("id", utlNote.td_id);
    		
            if (utlNote.folder_id>0)
            {
            	c = foldersDB.getFolder(utlNote.folder_id);
            	if (c.moveToFirst())
            	{
            		if (Util.cLong(c, "td_id")>-1)
            		{
            			json.put("folder",Util.cLong(c, "td_id"));
            		}
            		else
            		{
            			Util.log("ToodledoInterface: Folder TD ID is -1 in ToodledoInterface.editNote.");
            			c.close();
            			return INTERNAL_ERROR;
            		}
            	}
            	else
            	{
            		Util.log("ToodledoInterface: Folder cannot be found in ToodledoInterface.editNote."+
            			"  Removing the folder from the note.");
            		utlNote.folder_id = 0;
            		(new NotesDbAdapter()).modifyNote(utlNote);
            		json.put("folder",0);
            	}
            	c.close();
            }
            else
            	json.put("folder",0);
    		
            // Parameters to pass to the server:
            ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
            parameters.add(new BasicNameValuePair("access_token",_account.current_token));
            JSONArray array = new JSONArray();
            array.put(json);
            parameters.add(new BasicNameValuePair("notes",array.toString()));
            
            // Send the data to the server:
            result = httpRequest(getBaseURL()+"notes/edit.php",HttpMethod.POST,parameters);
    	}
    	catch (JSONException e)
    	{
    		// The JSON object to pass to the server could not be created.
    		Util.log("ToodledoInterface: Could not create JSON object when editing note. "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+json.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERNAL_ERROR;
    	}
    	
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseNoteApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return editNote(utlNote);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the first item from the response array. Make sure it's not an error.
    	try
    	{
	    	json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode") && json.getInt("errorCode")!=706)
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONObject when editing "+
    			"note.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}

    	return SUCCESS;
    }
    
    // Delete a note.  The input is TD's note ID.  Returns a result code.
    public int deleteNote(long TDID)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Parameters to pass to the server:
        ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        JSONArray array = new JSONArray();
        array.put(TDID);
        parameters.add(new BasicNameValuePair("notes",array.toString()));
        
        // Send the data to the server:
        String result = httpRequest(getBaseURL()+"notes/delete.php",HttpMethod.GET,parameters);
        
        // Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseTaskApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return deleteNote(TDID);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	// Extract the first item from the response array. Make sure it's not an error.
    	try
    	{
	    	JSONObject json = parseResult.array.getJSONObject(0);
	    	if (!json.isNull("errorCode") && json.getInt("errorCode")!=12)
	    	{
	    		// An array was returned as expected, but the first item within was an error.
	    		_lastRejectMessage = json.getString("errorDesc");
	    		return TOODLEDO_REJECT;
	    	}
    	}
    	catch (JSONException e)
    	{
    		Util.log("Got JSONException when processing downloaded JSONObject when deleting "+
    			"note.  "+e.getMessage());
    		try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
    		catch (JSONException e2) { }
    		return INTERFACE_ERROR;
    	}
    	
        // No errors.  Return the success code.
        return SUCCESS;                
    }
    
    // Get deleted notes from Toodledo.  The input is a timestamp in ms.  Deleted notes
    // after this date/time will be returned.  The function populates an ArrayList of 
    // Toodledo ID numbers.  A result code is returned.
    public int getDeletedNotes(long after, ArrayList<Long> idList)
    {
    	int refreshCode = refreshConnection();
        if (refreshCode != SUCCESS)
        {
            return refreshCode;
        }
        
        // Query the server for the tasks:
    	ArrayList<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("access_token",_account.current_token));
        parameters.add(new BasicNameValuePair("after",new Long(after/1000).toString()));
        String result = httpRequest(getBaseURL()+"notes/deleted.php",HttpMethod.GET,parameters);
    	
    	// Parse the response and return an error code if necessary:
    	ParseResult parseResult = parseNoteApiResponse(result);
        if ((parseResult.result==INVALID_TOKEN || parseResult.result==TOO_MANY_REQUESTS_PER_TOKEN)
            && !_gotTokenError)
        {
            // Try again with a new token.
            _gotTokenError = true;
            return getDeletedNotes(after,idList);
        }
        _gotTokenError = false;
    	if (parseResult.result != SUCCESS)
    		return parseResult.result;
    	
    	try
    	{
	    	for (int i=0; i<parseResult.array.length(); i++)
	    	{
	    		JSONObject json = parseResult.array.getJSONObject(i);
	    		if (!json.isNull("id"))
	    		{
	    			idList.add(json.getLong("id"));
	    		}
	    	}
	    	return SUCCESS;
    	}
    	catch (JSONException e)
    	{
			Util.log("ToodledoInterface: Got JSONException when processing downloaded JSONArray when getting "+
				"deleted notes.  "+e.getMessage());
			try { Util.log("ToodledoInterface: "+parseResult.array.toString(4)); }
			catch (JSONException e2) { }
			return INTERFACE_ERROR;
    	}    	
    }
    
    // This class stores the result of an attempt to parse the response from Toodledo:
    static private class ParseResult
    {
    	ParseResult(int res, JSONObject jsonObject, JSONArray jsonArray)
    	{
    		result = res;
    		json = jsonObject;
    		array = jsonArray;
    	}
    	
    	public int result;
    	public JSONObject json;
    	public JSONArray array;
    }
    
    // This class holds a millisecond timestamp:
    static private class Timestamp
    {
    	public long timestamp;
    }
    
    // A private enum to hold HTTP Methods
    private enum HttpMethod
    {
    	GET,
    	POST,
        GET_WITH_PASSWORD,
        POST_WITH_PASSWORD;
    }
    
    // Because Toodledo assumes all times entered are in the GMT time zone
    // and UTL assumes all times entered are in the user's home time zone,
    // we need to shift the times we have received.  Calculate the amount 
    // of the shift:
    private long timeShift(Context context, long baseTime, boolean isUpload)
    {
    	if (_defaultTimeZone==null)
    	{
		    SharedPreferences settings = context.getSharedPreferences("UTL_Prefs",0);
		    _defaultTimeZone = TimeZone.getTimeZone(settings.getString(
				"home_time_zone", "America/Los_Angeles"));
			long timeZoneOffset = _defaultTimeZone.getOffset(System.currentTimeMillis());
				// # of ms TD is behind us
			Util.log("ToodledoInterface: Time Zone Offset at current time (minutes): "+timeZoneOffset/60000);
    	}
    	
    	if (isUpload)
    		return (baseTime + _defaultTimeZone.getOffset(baseTime));
    	else
    		return (baseTime - _defaultTimeZone.getOffset(baseTime));
    }
    
    // Get the base URL to use in calls to Toodledo, based on whether this is a pro
    // account and whether SSL is enabled.  Use this for API calls that must be 
    // non-SSL for free accounts.
    private String getBaseURL()
    {
        // As of version 3.3, we no longer support unencrypted connections, and Toodledo
        // supports encrypted connections for everyone.
    	return BASE_URL;
    }
    
    // Log an internal sync error to our server:
    private void logInternalError(android.content.Context context, String msg)
    {
        Log.e("ToodledoInterface","Internal Sync Failure",msg);
    	Util.logOneTimeEvent(context, "internal_sync_failure", 1, new String[] { "toodledo", msg });
    }

    /** Given a task, generate a recurrence rule (repeat) string that can be sent to Toodledo. */
    private String getToodledoRepeatString(UTLTask t)
    {
        int repeat = t.repeat;
        if (repeat>100) repeat-=100;

        if (repeat==9)
        {
            // This is the "with parent" option.
            String result = "PARENT";
            if (t.repeat>100)
            {
                // Repeat from completion date.
                result += ";FROMCOMP";
            }
            return result;
        }

        String result = "";
        switch (repeat)
        {
            case 0:
                // No repeat:
                return "";
            case 1:
                // Weekly:
                result = "FREQ=WEEKLY";
                break;
            case 2:
                // Monthly:
                result = "FREQ=MONTHLY";
                break;
            case 3:
                // Yearly:
                result = "FREQ=YEARLY";
                break;
            case 4:
                // Daily:
                result = "FREQ=DAILY;INTERVAL=1";
                break;
            case 5:
                // Biweekly:
                result = "FREQ=WEEKLY;INTERVAL=2";
                break;
            case 6:
                // Bimonthly:
                result = "FREQ=MONTHLY;INTERVAL=2";
                break;
            case 7:
                // Semiannually:
                result = "FREQ=MONTHLY;INTERVAL=6";
                break;
            case 8:
                // Quarterly:
                result = "FREQ=MONTHLY;INTERVAL=3";
                break;
            case 10:
                // End of the month:
                result = "FREQ=MONTHLY;BYMONTHDAY=-1";
                break;
            case 50:
                // Advanced options:
                result = advancedRepeatToRecurrenceRule(t.rep_advanced);
        }

        if (t.repeat>100)
        {
            // Repeat from completion date.  A special option is added to the end of the string.
            result += ";FROMCOMP";
        }

        return result;
    }

    /** Convert an advanced repeat string to a recurrence rule: */
    private String advancedRepeatToRecurrenceRule(String input)
    {
        AdvancedRepeat ar = new AdvancedRepeat();
        if (!ar.initFromString(input))
        {
            // Badly formatted advanced repeat string:
            logInternalError(_c,"Task has bad advanced repeat string: "+input);
            return "";
        }

        if (ar.formatNum==1)
        {
            // Every 1 day, every 2 weeks, etc.
            if (ar.unit.equals("day"))
                return "FREQ=DAILY;INTERVAL="+ar.increment;
            else if (ar.unit.equals("month"))
                return "FREQ=MONTHLY;INTERVAL="+ar.increment;
            else if (ar.unit.equals("week"))
                return "FREQ=WEEKLY;INTERVAL="+ar.increment;
            else if (ar.unit.equals("year"))
                return "FREQ=YEARLY;INTERVAL="+ar.increment;
            else
                return "";
        }
        else if (ar.formatNum==2)
        {
            // Every 2nd tuesday, every 3rd friday, the last wednesday, etc.
            if (ar.monthLocation.equals("1") || ar.monthLocation.equals("2") ||
                ar.monthLocation.equals("3") || ar.monthLocation.equals("4") ||
                ar.monthLocation.equals("5"))
            {
                return "FREQ=MONTHLY;BYDAY="+ar.monthLocation+ar.dayOfWeek.substring(0,2).
                    toUpperCase();
            }
            else if (ar.monthLocation.equals("last"))
            {
                return "FREQ=MONTHLY;BYDAY=-1"+ar.dayOfWeek.substring(0,2).toUpperCase();
            }
            else
                return "";
        }
        else if (ar.formatNum==3)
        {
            // Every monday and tuesday, every saturday and sunday, every weekday, etc.
            String result = "FREQ=WEEKLY;WKST=SU;BYDAY=";
            for (int i=0; i<ar.daysOfWeek.length; i++)
            {
                if (i>0) result += ",";
                if (ar.daysOfWeek[i].equals("weekend"))
                    result += "SU,SA";
                else if (ar.daysOfWeek[i].equals("weekday"))
                    result += "MO,TU,WE,TH,FR";
                else
                    result += ar.daysOfWeek[i].substring(0,2).toUpperCase();
            }
            return result;
        }
        else
            return "";
    }

    /** Parse a Toodledo repeat string, and update the task object passed in. */
    private void parseToodledoRepeatString(UTLTask t, String repeatStr)
    {
        if (repeatStr==null || repeatStr.length()==0)
        {
            // The task does not repeat.
            return;
        }

        // We don't care about case when parsing.
        repeatStr = repeatStr.toLowerCase();

        // The first step is to convert the string into a hash of keys and values.
        String[] ruleList = repeatStr.split("\\s*;\\s*");
        HashMap<String,String> rules = new HashMap<String,String>();
        for (int i=0; i<ruleList.length; i++)
        {
            String[] keyValue = ruleList[i].split("\\s*=\\s*");
            if (keyValue.length==2)
                rules.put(keyValue[0],keyValue[1]);
            else
                rules.put(keyValue[0],"");
        }

        // Check for the "with parent" option.
        if (rules.containsKey("parent"))
        {
            t.rep_advanced = "";
            if (rules.containsKey("fromcomp"))
                t.repeat = 109;
            else
                t.repeat = 9;
            return;
        }

        // If the rule is not "with parent", then a frequency must be specified.
        if (rules.containsKey("freq"))
        {
            if (rules.get("freq").equals("weekly"))
            {
                // Weekly repeat.  Check the number of weeks between repeats.
                if ((!rules.containsKey("interval") || rules.get("interval").equals("1")) &&
                    !rules.containsKey("byday"))
                {
                    // Basic weekly repeat.
                    t.rep_advanced = "";
                    t.repeat = 1;
                    if (rules.containsKey("fromcomp"))
                        t.repeat+=100;
                    return;
                }
                else if (rules.containsKey("interval"))
                {
                    if (rules.get("interval").equals("2"))
                    {
                        // Basic biweekly repeat.
                        t.rep_advanced = "";
                        t.repeat = 5;
                        if (rules.containsKey("fromcomp"))
                            t.repeat+=100;
                        return;
                    }

                    // Advanced weekly repeat.  Format 1 (e.g., "every 3 weeks"):
                    t.rep_advanced = "Every "+rules.get("interval")+" weeks";
                    t.repeat = 50;
                    if (rules.containsKey("fromcomp"))
                        t.repeat+=100;
                    return;
                }
                else if (rules.containsKey("byday"))
                {
                    // Advanced weekly repeat.  Format 3 (e.g., every Monday, Wednesday).

                    // Get a list of days:
                    String[] dayList = rules.get("byday").split("\\s*,\\s*");
                    HashSet<String> dayHash = new HashSet<String>();
                    for (int i=0; i<dayList.length; i++)
                        dayHash.add(dayList[i]);

                    // Check for weekend only:
                    if (dayHash.size()==2 && dayHash.contains("su") && dayHash.contains("sa"))
                    {
                        t.rep_advanced = "Every weekend";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }

                    // Check for weekday only:
                    if (dayHash.size()==5 && dayHash.contains("mo") && dayHash.contains("tu") && dayHash.contains("we") &&
                        dayHash.contains("th") && dayHash.contains("fr"))
                    {
                        t.rep_advanced = "Every weekday";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }

                    // At this point, it must be a custom list of days.
                    String result = "Every ";
                    for (int i=0; i<dayList.length; i++)
                    {
                        if (i>0)
                            result += ", ";
                        if (dayList[i].equals("su"))
                            result += "sun";
                        else if (dayList[i].equals("mo"))
                            result += "mon";
                        else if (dayList[i].equals("tu"))
                            result += "tue";
                        else if (dayList[i].equals("we"))
                            result += "wed";
                        else if (dayList[i].equals("th"))
                            result += "thu";
                        else if (dayList[i].equals("fr"))
                            result += "fri";
                        else if (dayList[i].equals("sa"))
                            result += "sat";
                    }
                    t.rep_advanced = result;
                    t.repeat = 50;
                    if (rules.containsKey("fromcomp"))
                        t.repeat += 100;
                    return;
                }
                else
                {
                    logInternalError(_c,"Unparseable weekly repeat string: "+repeatStr);
                    return;
                }
            }
            else if (rules.get("freq").equals("monthly"))
            {
                if (rules.containsKey("interval"))
                {
                    if (rules.get("interval").equals("1"))
                    {
                        // Basic monthly repeat.
                        t.rep_advanced = "";
                        t.repeat = 2;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                    if (rules.get("interval").equals("2"))
                    {
                        // Basic bimonthly repeat.
                        t.rep_advanced = "";
                        t.repeat = 6;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                    if (rules.get("interval").equals("6"))
                    {
                        // Basic semiannual repeat.
                        t.rep_advanced = "";
                        t.repeat = 7;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                    if (rules.get("interval").equals("3"))
                    {
                        // Basic quarterly repeat.
                        t.rep_advanced = "";
                        t.repeat = 8;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }

                    // If we get here, it's an advanced repeat pattern.
                    t.repeat = 50;
                    if (rules.containsKey("fromcomp"))
                        t.repeat += 100;
                    t.rep_advanced = "Every "+rules.get("interval")+" months";
                    return;
                }
                else if (rules.containsKey("byday"))
                {
                    // The value of byday should include a numeric part and a day of the week
                    // part.
                    Pattern pat = Pattern.compile("^(-*\\d+)\\s*(\\w+)$");
                    Matcher mat = pat.matcher(rules.get("byday"));
                    if (mat.find())
                    {
                        String monthPosition = mat.group(1);
                        String dayOfWeek = mat.group(2);
                        String result = "The "+monthPosition+" ";
                        if (monthPosition.equals("-1"))
                            result = "The last ";
                        if (dayOfWeek.equals("su"))
                            result += "Sun";
                        else if (dayOfWeek.equals("mo"))
                            result += "Mon";
                        else if (dayOfWeek.equals("tu"))
                            result += "Tue";
                        else if (dayOfWeek.equals("we"))
                            result += "Wed";
                        else if (dayOfWeek.equals("th"))
                            result += "Thu";
                        else if (dayOfWeek.equals("fr"))
                            result += "Fri";
                        else if (dayOfWeek.equals("sa"))
                            result += "Sat";
                        result += " of each month";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        t.rep_advanced = result;
                        return;
                    }
                    else if (rules.containsKey("bysetpos"))
                    {
                        // The "byday" value should be a day of the week, and the "bysetpos" value
                        // is the position within the month.
                        String monthPosition = rules.get("bysetpos");
                        String dayOfWeek = rules.get("byday");
                        String result = "The "+monthPosition+" ";
                        if (monthPosition.equals("-1"))
                            result = "The last ";
                        if (dayOfWeek.equals("su"))
                            result += "Sun";
                        else if (dayOfWeek.equals("mo"))
                            result += "Mon";
                        else if (dayOfWeek.equals("tu"))
                            result += "Tue";
                        else if (dayOfWeek.equals("we"))
                            result += "Wed";
                        else if (dayOfWeek.equals("th"))
                            result += "Thu";
                        else if (dayOfWeek.equals("fr"))
                            result += "Fri";
                        else if (dayOfWeek.equals("sa"))
                            result += "Sat";
                        else
                        {
                            logInternalError(_c,"Unparseable monthly repeat string: "+repeatStr);
                            return;
                        }
                        result += " of each month";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        t.rep_advanced = result;
                        return;
                    }
                    else
                    {
                        logInternalError(_c,"Unparseable monthly repeat string: "+repeatStr);
                        return;
                    }
                }
                else if (rules.containsKey("bymonthday") && rules.get("bymonthday").equals("-1"))
                {
                    // End of the month.
                    t.rep_advanced = "";
                    t.repeat = 10;
                    if (rules.containsKey("fromcomp"))
                        t.repeat+=100;
                    return;
                }
                else
                {
                    // Assume every month.
                    t.rep_advanced = "";
                    t.repeat = 2;
                    if (rules.containsKey("fromcomp"))
                        t.repeat += 100;
                    return;
                }
            }
            else if (rules.get("freq").equals("daily"))
            {
                if (rules.containsKey("interval"))
                {
                    if (rules.get("interval").equals("1"))
                    {
                        // Basic daily repeat.
                        t.rep_advanced = "";
                        t.repeat = 4;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                    else
                    {
                        t.rep_advanced = "Every "+rules.get("interval")+" days";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                }
                else
                {
                    // Assume once per day
                    t.rep_advanced = "";
                    t.repeat = 4;
                    if (rules.containsKey("fromcomp"))
                        t.repeat += 100;
                    return;
                }
            }
            else if (rules.get("freq").equals("yearly"))
            {
                if (rules.containsKey("interval"))
                {
                    if (rules.get("interval").equals("1"))
                    {
                        // Basic yearly repeat.
                        t.rep_advanced = "";
                        t.repeat = 3;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                    else
                    {
                        t.rep_advanced = "Every "+rules.get("interval")+" years";
                        t.repeat = 50;
                        if (rules.containsKey("fromcomp"))
                            t.repeat += 100;
                        return;
                    }
                }
                else
                {
                    // Assume once per year
                    t.rep_advanced = "";
                    t.repeat = 3;
                    if (rules.containsKey("fromcomp"))
                        t.repeat += 100;
                    return;
                }
            }
            else
            {
                // Unrecognized frequency.
                logInternalError(_c,"Unrecognized frequency in repeat string: "+repeatStr);
                return;
            }
        }
        else
        {
            // No frequency in string from Toodledo.
            logInternalError(_c,"No frequency in repeat string: "+repeatStr);
            return;
        }
    }
}
