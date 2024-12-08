package com.customsolutions.android.utl;

// This is a ListPreference in which the entries and entry values are expected to be retrieved from
// the database.  A long is stored in the SharedPreferences corresponding to the unique ID of the item.
// The special long value 0 is used to specify <None>.
// The default value defined in XML should be set to the string "0".

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.database.Cursor;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class DatabaseListPreference extends ListPreference
{
	private Context _context;
	private TextView _value;
	
	private ArrayList<String> _entryValues;
	private ArrayList<String> _entries;
	
	private HashMap<Long,String> _keysToValues;
	
	public DatabaseListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        _context = context;
        
        _entryValues = new ArrayList<String>();
    	_entries = new ArrayList<String>();
    	_keysToValues = new HashMap<Long,String>();
    }

    public DatabaseListPreference(Context context) {
        super(context);
        _context = context;
        
        _entryValues = new ArrayList<String>();
    	_entries = new ArrayList<String>();
    	_keysToValues = new HashMap<Long,String>();
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
    
    // Initialize this preference from a database query.  The query should return 2 columns: the unique ID
    // value and the item's name for display.
    public void initEntries(String query)
    {
    	ArrayList<String> entryValues = new ArrayList<String>();
    	ArrayList<String> entries = new ArrayList<String>();
    	entryValues.add("0");
    	entries.add(_context.getString(R.string.None));
    	
    	// Run the query and populate the 2 arrays:
    	Cursor c = Util.db().rawQuery(query, null);
    	while (c.moveToNext())
    	{
    		entryValues.add(Long.valueOf(c.getLong(0)).toString());
    		entries.add(c.getString(1));
    	}
    	c.close();
    	
    	setEntries(Util.iteratorToStringArray(entries.iterator(), entries.size()));
    	setEntryValues(Util.iteratorToStringArray(entryValues.iterator(), entries.size()));
    }
    
    // Add a single entry.  If this is used, then initEntries() cannot be used.
    public void addEntry(long key, String value)
    {
    	_entryValues.add(Long.valueOf(key).toString());
		_entries.add(value);
		_keysToValues.put(key, value);
    }
    
    // Finalize entries added using the addEntry method.  Note that addEntry and initEntries cannot both
    // be used.
    public void finalizeEntries()
    {
    	setEntries(Util.iteratorToStringArray(_entries.iterator(), _entries.size()));
    	setEntryValues(Util.iteratorToStringArray(_entryValues.iterator(), _entries.size()));
    }
    
    // Get the value for the specified key.  This only works for entries adding using the addEntry() method.
    public String getValueForKey(long key)
    {
    	return _keysToValues.get(key);
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
            android.app.Activity a = (android.app.Activity)_context;
            a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            _value.setMaxWidth(Long.valueOf(Math.round(metrics.widthPixels*0.4)).intValue());
		}

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
    protected boolean persistString(String value) {
        if(value == null) 
            return persistLong(0);
        else 
            return persistLong(Long.parseLong(value));
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
    	long longValue = getPersistedLong(0);
    	return Long.valueOf(longValue).toString();
    }
}
