package com.customsolutions.android.utl;

// A Simple Fragment which displays a message to the user.

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class PaneMessage extends Fragment
{
	// Quick reference to the Fragment's activity:
    private UtlActivity _a;
    
	// This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.pane_message, container, false);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	_a = (UtlActivity)getActivity();
    	
    	Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.containsKey("message"))
        {
        	// Message text passed in right away:
        	TextView tv = (TextView)getView().findViewById(R.id.pane_message);
        	tv.setText(fragArgs.getString("message"));
        }
    }
    
    public void setMessage(String msg)
    {
    	if (null==getView())
    	{
    		Log.i("Test","getView returned null.");
    		return;
    	}
    	TextView tv = (TextView)getView().findViewById(R.id.pane_message);
    	tv.setText(msg);	
    }
}
