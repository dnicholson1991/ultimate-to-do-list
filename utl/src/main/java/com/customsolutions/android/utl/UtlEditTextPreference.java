package com.customsolutions.android.utl;

// An EditTextPreference that saves an Integer.  Make sure android:inputType='number' is included
// in the XML file.

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class UtlEditTextPreference extends EditTextPreference
{
	TextView _value;
	
	public UtlEditTextPreference(Context context) {
        super(context);
    }

    public UtlEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UtlEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
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

            // The value can not be too wide:
            DisplayMetrics metrics = new DisplayMetrics();
            android.app.Activity a = (android.app.Activity)this.getContext();
            a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            _value.setMaxWidth(Long.valueOf(Math.round(metrics.widthPixels * 0.4)).intValue());
		}

        // Allow the title to occupy 2 lines:
        TextView textView = (TextView) view.findViewById(android.R.id.title);
        if (textView != null)
        {
            textView.setSingleLine(false);
            textView.setMaxLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
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
}
