package com.customsolutions.android.utl;

// This broadcast receiver received text from other apps (such as E-mail or Google now) and directs
// the user to the appropriate activity.

import java.util.Iterator;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

@SuppressLint("NewApi")
public class TextReceiver extends UtlPopupActivity
{
	/** Flag indicating if the voice input should close when done. */
	private boolean _closeWhenDone;

	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Intent in = getIntent();
		
		Util.dlog("Action: "+in.getAction());
		if (in.getCategories()!=null)
		{
			Set<String> categories = in.getCategories();
			Iterator<String> it = categories.iterator();
			while (it.hasNext())
			{
				Util.dlog("Category: "+it.next());
			}
		}
		Bundle extras = in.getExtras();
		if (extras!=null)
		{
			Set<String> keySet = extras.keySet();
			Iterator<String> it = keySet.iterator();
			while (it.hasNext())
			{
				String key = it.next();
				Util.dlog("Key: "+key+": "+extras.get(key));
			}
		}
		if (in.getDataString()!=null)
			Util.dlog("Data: "+in.getDataString());

		_closeWhenDone = false;
		if (extras!=null && extras.containsKey("close_when_done") && extras.getBoolean(
			"close_when_done"))
		{
			_closeWhenDone = true;
		}

		if (extras!=null && extras.containsKey(Intent.EXTRA_SUBJECT) && extras.getString(Intent.
			EXTRA_SUBJECT)!=null && extras.getString(Intent.EXTRA_SUBJECT).toLowerCase().
			equals("note to self"))
		{
			// A subject of "note to self" indicates this is from Google's voice search.
			
			if (extras!=null && extras.containsKey(Intent.EXTRA_TEXT) && extras.getString(Intent.EXTRA_TEXT).
				toLowerCase().equals("using voice mode"))
			{
				// The user has explicitly said "note to self using voice mode".  This means we need 
				// to start up voice mod.
				startVoiceMode();
				return;
			}
			else
			{
				// The user said "note to self" but included some other text.  This other text needs 
				// to be passed onto a new task Activity.
				int screenSize = getResources().getConfiguration().screenLayout & Configuration.
					SCREENLAYOUT_SIZE_MASK;
				Intent out;
				if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
					screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL ||
					screenSize==Configuration.SCREENLAYOUT_SIZE_UNDEFINED)
				{
					out = new Intent(this,EditTask.class);
				}
				else
					out = new Intent(this,EditTaskPopup.class);
				out.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
				out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				out.setAction(Intent.ACTION_SEND);
				out.putExtra(Intent.EXTRA_TEXT, extras.getString(Intent.EXTRA_TEXT));
				startActivity(out);
				finish();
				return;
			}
		}
		
		if (extras!=null && extras.containsKey(Intent.EXTRA_TEXT) && extras.getString(Intent.
			EXTRA_TEXT)!=null && extras.getString(Intent.EXTRA_TEXT).toLowerCase().equals(
			"using voice mode"))
		{
			// This is probably from Google voice search / google now using the note to self functionality.
			startVoiceMode();
			return;
		}
		
		// If we get here, we know the intent needs to go to the task editor.
		
		// Copy the intent and pass it on to the task editor:
		int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
		Intent out;
		if (screenSize==Configuration.SCREENLAYOUT_SIZE_SMALL ||
			screenSize==Configuration.SCREENLAYOUT_SIZE_NORMAL ||
			screenSize==Configuration.SCREENLAYOUT_SIZE_UNDEFINED)
		{
			out = new Intent(this,EditTask.class);
		}
		else
			out = new Intent(this,EditTaskPopup.class);
		out.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		out.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (in.getAction()!=null)
			out.setAction(in.getAction());
		if (in.getCategories()!=null)
		{
			Set<String> categories = in.getCategories();
			Iterator<String> it = categories.iterator();
			while (it.hasNext())
			{
				out.addCategory(it.next());
			}
		}
		if (in.getData()!=null)
			out.setData(in.getData());
		if (in.getExtras()!=null)
			out.putExtras(in.getExtras());
		startActivity(out);
		finish();
	}
	
	private void startVoiceMode()
	{
		Intent i = new Intent(this,VoiceCommand.class);
		i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		if (_closeWhenDone)
			i.putExtra("close_when_done",true);
		startActivity(i);
		finish();
	}
}
