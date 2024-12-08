package com.customsolutions.android.utl;

// Just like Android's ListPreference, except that it displays the preference value on the right.
// In the preferences xml file, include the line: android:widgetLayout='@layout/pref_value'

import android.content.Context;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class UtlListPreference extends ListPreference
{
	private TextView _value;
	
	public UtlListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UtlListPreference(Context context) {
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

        // Allow the title to be 2 lines:
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
}
