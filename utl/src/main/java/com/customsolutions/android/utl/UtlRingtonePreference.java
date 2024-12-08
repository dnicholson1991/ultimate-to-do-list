package com.customsolutions.android.utl;

import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.RingtonePreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class UtlRingtonePreference extends RingtonePreference
{
	private TextView _value;
	private Context _context;

	public UtlRingtonePreference(Context context, AttributeSet attrs, int defStyle)
	{
		super(context,attrs,defStyle);
		_context = context;
	}
	
	public UtlRingtonePreference(Context context, AttributeSet attrs)
	{
		super(context,attrs);
		_context = context;
	}
	
	public UtlRingtonePreference(Context context)
	{
		super(context);
		_context = context;
	}
	
	@Override
    protected View onCreateView(ViewGroup parent) 
    {
        View v = super.onCreateView(parent);

        // Get a reference to the value's TextView and set the value:
    	_value = (TextView)v.findViewById(R.id.pref_value);
    	
        // Fetch the title of the current ringtone, if any:
        String ringtonePath = getSharedPreferences().getString(getKey(), "Default");
        if (!ringtonePath.equals("Default"))
        {
        	Ringtone ringtone = RingtoneManager.getRingtone(_context,Uri.parse(ringtonePath));
        	if (_value!=null && ringtone!=null)
        		_value.setText(ringtone.getTitle(_context));
        }
        else
        {
        	if (_value!=null)
        		_value.setText(R.string.Default);
        }
    	
    	return v;
    }
	
	@Override
	protected void onBindView(View view)
	{
		super.onBindView(view);
		
		_value = (TextView)view.findViewById(R.id.pref_value);
		if (_value!=null)
		{
			String ringtonePath = getPersistedString("Default");
	        if (!ringtonePath.equals("Default"))
	        {
	        	Ringtone ringtone = RingtoneManager.getRingtone(_context,Uri.parse(ringtonePath));
	        	if (ringtone!=null)
	        		_value.setText(ringtone.getTitle(_context));
	        }
	        else
	        {
	        	_value.setText(R.string.Default);
	        }
		}
	}
	
	@Override
	protected void onSaveRingtone(Uri ringtoneUri)
	{
		super.onSaveRingtone(ringtoneUri);
		
		// This will trigger onBindView to be called, to update the display:
		notifyChanged();
	}
}
