package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/** This is a simple Fragment that displays one page of voice command help information. */
public class VoiceCommandHelpFragment extends Fragment
{
	int _stringResource;
	
	@SuppressLint("NewApi")
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) 
	{
		Bundle b = this.getArguments();
		_stringResource = b.getInt("string");

		// Get the HTML string to display, and tweak it for the dark theme if necessary:
        String html = getString(_stringResource);
        SharedPreferences settings = getActivity().getSharedPreferences(Util.PREFS_NAME, 0);
        int textColor = Util.colorFromAttr(getActivity(),R.attr.utl_text_color);
        textColor = textColor & 0x00FFFFFF;
		html = html.replace("color: #000000", "color: #"+Integer.toHexString(textColor));
        
        WebView webView = (WebView) inflater.inflate(R.layout.voice_command_help, container, false);        
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        webView.setBackgroundColor(0x00000000);
        if (Build.VERSION.SDK_INT>=11)
        	webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
        return webView;
    }
	
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		Bundle b = this.getArguments();
		_stringResource = b.getInt("string");
	}
}
