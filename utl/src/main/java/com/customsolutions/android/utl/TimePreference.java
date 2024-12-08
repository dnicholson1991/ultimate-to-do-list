package com.customsolutions.android.utl;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.TimePicker;

/**
 * Created by Nicholson on 5/17/2015.
 */
public class TimePreference extends DialogPreference
{
    private static final String TAG = "TimePreference";

    private long millisSinceMidnight;
    private TimePicker picker = null;
    TextView _value;

    public TimePreference(Context ctxt)
    {
        super(ctxt);
        setPositiveButtonText(R.string.Save);
        setNegativeButtonText(R.string.Cancel);
    }

    public TimePreference(Context ctxt, AttributeSet attrs)
    {
        super(ctxt,attrs);
        setPositiveButtonText(R.string.Save);
        setNegativeButtonText(R.string.Cancel);
    }

    public TimePreference(Context ctxt, AttributeSet attrs, int defStyle) {
        super(ctxt, attrs, defStyle);
        setPositiveButtonText(R.string.Save);
        setNegativeButtonText(R.string.Cancel);
    }

    @Override
    protected View onCreateView(ViewGroup parent)
    {
        View v = super.onCreateView(parent);

        // Get a reference to the value's TextView and set the value:
        _value = (TextView)v.findViewById(R.id.pref_value);

        return v;
    }

    @Override
    protected View onCreateDialogView() {
        picker = new TimePicker(getContext());
        return (picker);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        picker.setCurrentHour(Long.valueOf(millisSinceMidnight/3600000).intValue());
        picker.setCurrentMinute(Long.valueOf((millisSinceMidnight%3600000)/60000).intValue());
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            millisSinceMidnight = picker.getCurrentHour()*3600000 + picker.getCurrentMinute()*60000;

            setSummary(getSummary());
            if (callChangeListener(millisSinceMidnight)) {
                persistLong(millisSinceMidnight);
                notifyChanged();
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return (a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {

        if (restoreValue) {
            if (defaultValue == null) {
                millisSinceMidnight = getPersistedLong(288000000);
            } else {
                millisSinceMidnight = Long.parseLong(getPersistedString((String) defaultValue));
            }
        } else {
            if (defaultValue == null) {
                millisSinceMidnight = 288000000;
            } else {
                millisSinceMidnight = Long.parseLong((String) defaultValue);
            }
        }
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        long millis = Util.getMidnight(System.currentTimeMillis())+millisSinceMidnight;

        if (_value!=null)
            _value.setText(Util.getTimeString(millis,getContext()));

        // We don't use the summary with this preference.  This function is mainly to refresh
        // the value.
        return null;
    }
}
