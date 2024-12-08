package com.customsolutions.android.utl;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.util.Log;

public class HttpFileUploader
{
	public boolean isSuccessful;
	public String resultString;
	
	URL connectURL;
	String params;
	String fileName;
	byte[] dataToServer;

	HttpFileUploader(String urlString, String params, String fileName )
	{
		isSuccessful = false;
		resultString = "";
		try
		{
			connectURL = new URL(urlString);
		}
		catch(Exception ex)
		{
			resultString = ex.getMessage();
		}
		this.params = params+"=";
		this.fileName = fileName;
	}


	void doStart(FileInputStream stream)
	{ 
		fileInputStream = stream;
		thirdTry();
	} 

	FileInputStream fileInputStream = null;
	
	void thirdTry()
	{
		String exsistingFileName = "asdf.png";

		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "AaB03x";
		String Tag="test";
		try
		{
			//------------------ CLIENT REQUEST

			// Open a HTTP connection to the URL

			HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();

			// Allow Inputs
			conn.setDoInput(true);

			// Allow Outputs
			conn.setDoOutput(true);

			// Don't use a cached copy.
			conn.setUseCaches(false);

			// Use a post method.
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Connection", "Keep-Alive");

			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary="+boundary);

			DataOutputStream dos = new DataOutputStream( conn.getOutputStream() );

			dos.writeBytes(twoHyphens + boundary + lineEnd);
			dos.writeBytes("Content-Disposition: form-data; name=\"log\"; filename=\"file1.txt\""+lineEnd);
			dos.writeBytes("Content-Type: text/plain"+lineEnd);
			dos.writeBytes(lineEnd);

			// create a buffer of maximum size

			int bytesAvailable = fileInputStream.available();
			int maxBufferSize = 1024;
			int bufferSize = Math.min(bytesAvailable, maxBufferSize);
			byte[] buffer = new byte[bufferSize];

			// read file and write it into form...

			int bytesRead = fileInputStream.read(buffer, 0, bufferSize);

			while (bytesRead > 0)
			{
				dos.write(buffer, 0, bufferSize);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			}

			// send multipart form data necesssary after file data...

			dos.writeBytes(lineEnd);
			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

			// close streams
			fileInputStream.close();
			dos.flush();

			InputStream is = conn.getInputStream();
			// retrieve the response from server
			int ch;

			StringBuffer b =new StringBuffer();
			while( ( ch = is.read() ) != -1 ){
				b.append( (char)ch );
			}
			String s=b.toString(); 
			dos.close();
			
			isSuccessful = true;
			resultString = s;
		}
		catch (MalformedURLException ex)
		{
			resultString = "MalformedURLException: "+ex.getMessage();
		}

		catch (IOException ioe)
		{
			resultString = Util.getString(R.string.Unable_To_Upload);
		}
	}
}
