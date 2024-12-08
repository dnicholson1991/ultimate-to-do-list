package com.customsolutions.android.utl;

// Special Preference Class just for the default tags.  While it's based on an EditTextPreference,
// the EditText dialog is never actually called.  The preference is modified from the parent Fragment
// or Activity.

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class TagsPreference extends EditTextPreference
{
	TextView _value;
	
	public TagsPreference(Context context) {
        super(context);
    }

    public TagsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TagsPreference(Context context, AttributeSet attrs, int defStyle) {
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
    }
    
    // Block the dialog from showing, since it is not used.
    @Override
    protected void showDialog(Bundle state) 
    {
    	
    }
    
    // Call this when the value is changed from the outside:
    public void setNewValue(String newValue)
    {
    	this.setText(newValue);
    	this.notifyChanged();
    }
}
