package com.customsolutions.android.utl;

// This is a ListPreference in which the array of entry values is ignored.  An integer is stored in the 
// SharedPreferences corresponding to the index of the human-readable entries array.
// The default value should be set in code rather than use an XML file.

import android.content.Context;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class IntListPreference extends ListPreference
{
	TextView _value;
	
	public IntListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntListPreference(Context context) {
        super(context);
    }

    @Override
    protected View onCreateView(ViewGroup parent) 
    {
        View v = super.onCreateView(parent);

    	// Get a reference to the value's TextView and set the value:
    	_value = (TextView)v.findViewById(R.id.pref_value);
    	if (_value!=null)
    		_value.setText(this.getEntry());
    	
    	return v;
    }
    
    @Override
    public void setEntries(CharSequence[] entries)
    {
    	super.setEntries(entries);
    	setEntryValues(entries);
    }
    
    @Override
    protected void onBindView(View view)
    {
    	super.onBindView(view);
    	
    	_value = (TextView)view.findViewById(R.id.pref_value);
		if (_value!=null)
		{
			_value.setText(getEntry());

            // The value can not be too wide:
            DisplayMetrics metrics = new DisplayMetrics();
            android.app.Activity a = (android.app.Activity)this.getContext();
            a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            _value.setMaxWidth(Long.valueOf(Math.round(metrics.widthPixels * 0.4)).intValue());
		}

        // Allow the preference title to occupy multiple lines:
        TextView title = (TextView) view.findViewById(android.R.id.title);
        if (title!=null)
        {
            title.setSingleLine(false);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
        }
    }
    
    @Override
    protected void onDialogClosed(boolean positiveResult) 
    {
        super.onDialogClosed(positiveResult);

        if (positiveResult)
        {
        	// This will trigger onBindView to be called, to update the display:
        	notifyChanged();
        }
    }
    
    @Override
    protected boolean persistString(String value) {
        if(value == null) {
            return false;
        } else {
            return persistInt(findIndexOfValue(value));
        }
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        if(getSharedPreferences().contains(getKey())) {
            int intValue = getPersistedInt(0);
            if (intValue<0)
            	intValue=0;
            return getEntries()[intValue].toString();
        } else {
            return defaultReturnValue;
        }
    }
}
