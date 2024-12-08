package com.customsolutions.android.utl;

// An EditTextPreference that saves an Integer.  Make sure android:inputType='number' is included
// in the XML file.

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class IntEditTextPreference extends EditTextPreference
{
	TextView _value;
	
	public IntEditTextPreference(Context context) {
        super(context);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected View onCreateView(ViewGroup parent) 
    {
        View v = super.onCreateView(parent);

    	// Get a reference to the value's TextView and set the value:
    	_value = (TextView)v.findViewById(R.id.pref_value);
    	if (_value!=null)
    		_value.setText(this.getText());
    	
    	return v;
    }

    @Override
    protected void onBindView(View view)
    {
    	super.onBindView(view);
    	
    	_value = (TextView)view.findViewById(R.id.pref_value);
		if (_value!=null)
		{
			_value.setText(getText());
		}

        // Allow the title to occupy 2 lines:
        TextView titleView = (TextView) view.findViewById(android.R.id.title);
        if (titleView != null)
        {
            titleView.setSingleLine(false);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
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
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(getPersistedInt(-1));
    }

    @Override
    protected boolean persistString(String value) {
        return persistInt(Integer.valueOf(value));
    }
}
