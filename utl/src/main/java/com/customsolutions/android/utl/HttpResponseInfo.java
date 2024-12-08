package com.customsolutions.android.utl;

import java.net.HttpURLConnection;

/**
 * This class holds information about a response to an HTTP Request.
 */
public class HttpResponseInfo
{
    /** The text returned from the server */
    public String text;

    /** The HTTP Response code */
    public int responseCode;

    /** The response message (or status line) */
    public String responseMessage;

    /** A reference to the original HttpUrlConnection, or HttpsUrlConnection object. This is
     * used for advanced access to response information. */
    public HttpURLConnection httpUrlConnection;
 }
