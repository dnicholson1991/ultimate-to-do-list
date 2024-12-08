package com.customsolutions.android.utl;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Provides utility functions to access the API provided by the back-end server. */
public class Api
{
    /** A tag for Logging. */
    private static final String TAG = "API";

    /** From version 6.0.0 the app no longer communicates with a backend server. Setting this
     * value to true will disable server API calls throughout the app. The code for making these
     * calls has been left in place in case there is a need or desire to resurrect it in the
     * future. */
    public static final boolean DISABLE_BACKEND = true;

    /** Base URL for communicating with our server. Just append the script name to this. */
    public static final String SERVER_BASE_URL = "https://server.base.url/";

    /** Base URL for testing. This is used when the app is running in debug mode. */
    public static final String SERVER_BASE_URL_TEST = "https://test.server.url/";

    /** The username required by the server. */
    public static final String USERNAME = "username_required_by_server";

    /** The password required by the server. */
    public static final String PASSWORD = "password_required_by_server";

    /** A string specifying the content-type as JSON */
    private static final String JSON_TYPE = "application/json; charset=utf-8";

    /** This field name appears in returned JSON objects and contains a user-visible error
     * message. */
    public static final String USER_MESSAGE = "user_message";

    /** Post some data to the server, displaying standard error messages on failure. If the response
     * has success==false, it will display a Toast with the value of user_message in the response.
     * If the response does not have a user_message value, it will be treated as a technical error.
     */
    public static void postToServer(final Context c, final String url, final JSONObject data,
        final SuccessCallback callback)
    {
        postToServer(url, data, false, new Callback()
        {
            @Override
            public void onSuccess(JSONObject jsonObject)
            {
                callback.onSuccess(jsonObject);
            }

            @Override
            public void onFailure(JSONObject jsonObject)
            {
                if (jsonObject.has("user_message"))
                {
                    try
                    {
                        Toast.makeText(c,jsonObject.getString("user_message"), Toast.LENGTH_LONG).
                            show();
                    }
                    catch (JSONException e)
                    {
                        Util.handleException(TAG,c,e);
                    }
                    return;
                }
                showTechnicalErrorMessage(c);
            }

            @Override
            public void onNetworkError()
            {
                showNetworkErrorMessage(c);
            }

            @Override
            public void onTechnicalError()
            {
                showTechnicalErrorMessage(c);
            }
        });
    }

    /** Post some data to the server, displaying standard error messages on network or technical
     * failure. The callback handles success or a user error. */
    public static void postToServer(final Context c, final String url, final JSONObject data,
        final SuccessFailCallback callback)
    {
        postToServer(url, data, false, new Callback()
        {
            @Override
            public void onSuccess(JSONObject jsonObject)
            {
                callback.onSuccess(jsonObject);
            }

            @Override
            public void onFailure(JSONObject jsonObject)
            {
                callback.onFailure(jsonObject);
            }

            @Override
            public void onNetworkError()
            {
                showNetworkErrorMessage(c);
            }

            @Override
            public void onTechnicalError()
            {
                showTechnicalErrorMessage(c);
            }
        });
    }

    /** Post to the server with retries. This retries requests upon network failure, and keeps
     * trying until Android shuts down the process. For other types of failures, it aborts. */
    public static void postWithRetries(final String url, final JSONObject data)
    {
        postToServer(url, data, false, new Callback()
        {
            @Override
            public void onSuccess(JSONObject jsonObject)
            {
                // Nothing to do. We're done.
            }

            @Override
            public void onFailure(JSONObject jsonObject)
            {
                Log.d(TAG,"Aborting post to "+url+" due to failure response: "+jsonObject.
                    toString());
            }

            @Override
            public void onNetworkError()
            {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    postWithRetries(url,data);
                },2000);
            }

            @Override
            public void onTechnicalError()
            {
                // Nothing to do. The error will have been logged in postToServer().
            }
        });
    }

    /** Post to the server with retries. This retries requests upon network failure, and keeps
     * trying until Android shuts down the process. For other types of failures, it aborts.
     * When the request finally succeeds, the success callback is called. */
    public static void postWithRetries(final String url, final JSONObject data, final
        SuccessCallback callback)
    {
        postToServer(url, data, false, new Callback()
        {
            @Override
            public void onSuccess(JSONObject jsonObject)
            {
                callback.onSuccess(jsonObject);
            }

            @Override
            public void onFailure(JSONObject jsonObject)
            {
                Log.d(TAG,"Aborting post to "+url+" due to failure response: "+jsonObject.
                    toString());
            }

            @Override
            public void onNetworkError()
            {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    postWithRetries(url,data,callback);
                },2000);
            }

            @Override
            public void onTechnicalError()
            {
                // Nothing to do. The error will have been logged in postToServer().
            }
        });
    }

    /** Post error information. This retries requests upon network failure, and keeps
     * trying until Android shuts down the process. For other types of failures, it aborts. */
    public static void postError(final String url, final JSONObject data)
    {
        postToServer(url, data, true, new Callback()
        {
            @Override
            public void onSuccess(JSONObject jsonObject)
            {
                // Nothing to do. We're done.
            }

            @Override
            public void onFailure(JSONObject jsonObject)
            {
                Log.d(TAG,"Aborting post to "+url+" due to failure response: "+jsonObject.
                    toString());
            }

            @Override
            public void onNetworkError()
            {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    postError(url,data);
                },2000);
            }

            @Override
            public void onTechnicalError()
            {
                // Nothing to do. The error will have been logged in postToServer().
            }
        });
    }

    /** Display a standard Toast message upon network failure. */
    public static void showNetworkErrorMessage(Context c)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(c,c.getString(R.string.internet_failure), Toast.LENGTH_LONG).show();
        });
    }

    /** Display a standard Toast message upon an internal server failure. */
    public static void showTechnicalErrorMessage(Context c)
    {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(c,c.getString(R.string.technical_failure), Toast.LENGTH_LONG).show();
        });
    }

    /** Send some data to the server.
     * @param url  The url to post to, relative to {@link Api#SERVER_BASE_URL }. Example:
     *             "new_install"
     * @param data  The JSON data to send.
     * @param isError  Flag indicating if an app or server error is being reported. This should be
     *                 set to true from the Log.w or Log.e function.
     * @param callback  The callback to receive the results. */
    public static void postToServer(final String url, final JSONObject data, final boolean isError,
        final Callback callback)
    {
        Util.UTL_EXECUTOR.execute(() -> {
            try
            {
                // When running in debug mode, the server base URL changes.
                String serverBaseUrl = SERVER_BASE_URL;
                if (BuildConfig.DEBUG)
                    serverBaseUrl = SERVER_BASE_URL_TEST;

                // Prepare and send the request:
                String finalUrl = serverBaseUrl+url;
                if (!url.equals("handle_log"))
                    Log.v(TAG+"-Sending",finalUrl+":\n"+data.toString(2));
                RequestBody body = RequestBody.create(MediaType.parse(JSON_TYPE),data.
                    toString());
                Request request = new Request.Builder()
                    .url(finalUrl)
                    .header("Authorization", "Basic "+ Base64.encodeToString((USERNAME+":"+
                        PASSWORD).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP))
                    .post(body)
                    .build();
                final Response response = Util.client().newCall(request).execute();

                // Analyze the response:
                ResponseBody responseBody = response.body();
                if (responseBody==null)
                {
                    if (isError)
                        Log.d(TAG,"Got a null response from the server.");
                    else
                        Log.e(TAG,"Null Server Response","Got a null response from the server.");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onTechnicalError();
                    });
                    return;
                }
                final String bodyString = responseBody.string();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.code()==200)
                    {
                        // HTTP request was successful. Make sure the operation itself was
                        // successful.
                        JSONObject jsonResponse;
                        try
                        {
                            jsonResponse = (JSONObject) new JSONTokener(bodyString).
                                nextValue();
                        }
                        catch (ClassCastException | JSONException e)
                        {
                            if (isError)
                            {
                                Log.d(TAG, "Received non-JSON response from server: " +
                                    bodyString);
                            }
                            else
                            {
                                Log.e(TAG,"Non-JSON from Server","Received non-JSON " +
                                    "response from server: " + bodyString);
                            }
                            callback.onTechnicalError();
                            return;
                        }
                        try
                        {
                            Log.v(TAG+"-Received",jsonResponse.toString(2));
                            if (jsonResponse.has("success") && jsonResponse.getBoolean(
                                "success"))
                            {
                                callback.onSuccess(jsonResponse);
                            }
                            else
                                callback.onFailure(jsonResponse);
                        }
                        catch (JSONException e)
                        {
                            if (isError)
                            {
                                Log.d(TAG,"Received invalid JSON response from server: " +
                                    bodyString);
                            }
                            else
                            {
                                Log.e(TAG,"Invalid JSON Response","Received invalid JSON " +
                                    "response from server: " + bodyString);
                            }
                            callback.onTechnicalError();
                        }
                    }
                    else if (response.code()==500)
                    {
                        // Internal server error.
                        if (isError)
                            Log.d(TAG,"Got internal server error: "+bodyString);
                        else
                        {
                            Log.e(TAG,"Internal Server Error","Got internal server " +
                                "error: "+bodyString);
                        }
                        callback.onTechnicalError();
                    }
                    else
                    {
                        if (isError)
                        {
                            Log.d(TAG, "Got HTTP response code " + response.code() + ": "
                                + bodyString);
                        }
                        else
                        {
                            Log.e(TAG,"Invalid HTTP Response Code","Got HTTP response " +
                                "code " + response.code() + ": " + bodyString);
                        }
                        callback.onTechnicalError();
                    }
                });
            }
            catch (JSONException e)
            {
                // This should not happen. The toString() call requires it.
                if (isError)
                    Log.d(TAG,"Impossible JSONException when sending data.",e);
                else
                {
                    Log.e(TAG,"Impossible JSONException","Impossible JSONException when " +
                        "sending data.",e);
                }
                new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkError());
            }
            catch (IOException e)
            {
                Log.d(TAG,"IOException when sending to server.",e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onNetworkError());
            }
        });
    }

    /** The full callback interface used when accessing the API. */
    public interface Callback
    {
        /** Called upon success, when the returned JSONObject has the boolean success == true. */
        void onSuccess(JSONObject jsonObject);

        /** Called upon failure, when the returned JSONObject has the boolean success == false. */
        void onFailure(JSONObject jsonObject);

        /** Called when there are internet connection issues. */
        void onNetworkError();

        /** Called when there is a technical issue at the server. The server will return a http
         * response code of 500 when such errors occur. */
        void onTechnicalError();
    }

    /** The callback interface when we only response to success. */
    public interface SuccessCallback
    {
        /** Called upon success, when the returned JSONObject has the boolean success == true. */
        void onSuccess(JSONObject jsonObject);
    }

    /** The callback interface when we want to handle a success or user error, but use default
     * handling for network or technical errors. */
    public interface SuccessFailCallback
    {
        /** Called upon success, when the returned JSONObject has the boolean success == true. */
        void onSuccess(JSONObject jsonObject);

        /** Called on a user error, when the returned JSONObject has success == false. */
        void onFailure(JSONObject jsonObject);
    }
}
