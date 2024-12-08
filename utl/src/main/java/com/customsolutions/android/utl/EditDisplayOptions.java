package com.customsolutions.android.utl;


import java.util.ArrayList;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.Spinner;

/**<pre>
 * This activity is used for updating the display options.
 * A Bundle is passed in with the following inputs:
 *   view_id: The ID of the view we are editing.
 * This should be started with a startActivityForResult().  A resultCode of RESULT_CANCELED
 * or RESULT_OK is returned.  No Bundle is passed back (because it is not needed).
 * This activity updates the View in the database.
 </pre> */
public class EditDisplayOptions extends UtlSaveCancelPopupActivity
{
	private static final String TAG = "EditDisplayOptions";

	// Views we need to keep track of:
	private Spinner _upperRightField;
	private Spinner _lowerLeftField;
	private Spinner _lowerRightField;
	private Spinner _colorCodeField;
	private Spinner _subtaskOption;
	private Spinner _parentOption;
	private CheckedTextView _dividers;
	
	// Handle into the database:
	private ViewsDbAdapter _viewsDB;
	
	// The view ID passed in:
	private long _viewID;
	
	// The DisplayOptions we are working with:
	private DisplayOptions _displayOptions;
	
	private SharedPreferences _settings;
	
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
		super.onCreate(savedInstanceState);
        Util.log("Start the Display Options chooser.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.display_options);
        
        // Pull out the data in the Intent, and make sure the required view ID is passed in:
        Bundle extras = this.getIntent().getExtras();
        if (!extras.containsKey("view_id"))
        {
        	Util.log("Missing view_id in DisplayOptions.OnCreate().");
        	finish();
        	return;
        }
        _viewID = extras.getLong("view_id");
        _viewsDB = new ViewsDbAdapter();
        
        // Set the title and icon at the top of the screen:
        getSupportActionBar().setTitle(R.string.Display_Options);
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_display_options));
        
        // Link local variables to layout items:
        _upperRightField = (Spinner)findViewById(R.id.display_options_upper_right_spinner);
        _lowerLeftField = (Spinner)findViewById(R.id.display_options_lower_left_spinner);
        _lowerRightField = (Spinner)findViewById(R.id.display_options_lower_right_spinner);
        _colorCodeField = (Spinner)findViewById(R.id.display_options_color_bar_spinner);
        _subtaskOption = (Spinner)findViewById(R.id.display_options_subtask_format_spinner);
        _parentOption = (Spinner)findViewById(R.id.display_options_parent_options_spinner);
        _dividers = (CheckedTextView)findViewById(R.id.display_options_dividers);
        
        // Initialize the choices in the Spinners:
        initSpinner(_upperRightField,DisplayOptions.getFieldDescriptions());
        initSpinner(_lowerLeftField,DisplayOptions.getFieldDescriptions());
        initSpinner(_lowerRightField,DisplayOptions.getFieldDescriptions());
        initSpinner(_colorCodeField,DisplayOptions.getColorCodeDescriptions());
        
        // In order to initialize the spinner selections, we need an ArrayList of the available
        // database codes:
        ArrayList<String> dbCodes = new ArrayList<String>();
        String[] dbCodes2 = DisplayOptions.getDatabaseCodes();
        for (int i=0; i<dbCodes2.length; i++)
        {
        	dbCodes.add(dbCodes2[i]);
        }
        ArrayList<String> colorCodes = new ArrayList<String>();
        String[] colorCodes2 = DisplayOptions.getColorCodeDbCodes();
        for (int i=0; i<colorCodes2.length; i++)
        {
        	colorCodes.add(colorCodes2[i]);
        }
        
        // Read the view passed in, and initialize the spinner selections:
        Cursor c = _viewsDB.getView(_viewID);
        if (!c.moveToFirst())
        {
        	Util.log("Invalid view ID "+_viewID+" passed to EditDisplayOptions.java.");
        	finish();
        	return;
        }
        _displayOptions = new DisplayOptions(Util.cString(c, "display_string"));
        _upperRightField.setSelection(dbCodes.indexOf(_displayOptions.upperRightField));
        _lowerLeftField.setSelection(dbCodes.indexOf(_displayOptions.lowerLeftField));
        _lowerRightField.setSelection(dbCodes.indexOf(_displayOptions.lowerRightField));
        _colorCodeField.setSelection(colorCodes.indexOf(_displayOptions.leftColorCodedField));
        _dividers.setChecked(_displayOptions.showDividers);
        if (_displayOptions.subtaskOption.equals("indented"))
        {
        	_subtaskOption.setSelection(0);
        	showParentOptions();
        	_parentOption.setSelection(_displayOptions.parentOption);
        }
        if (_displayOptions.subtaskOption.equals("flattened"))
        {
        	_subtaskOption.setSelection(1);
        	hideParentOptions();
        }
        if (_displayOptions.subtaskOption.equals("separate_screen"))
        {
        	_subtaskOption.setSelection(2);
        	hideParentOptions();
        }
        
        // Set a title for the spinner menus:
        _upperRightField.setPromptId(R.string.Choose_a_field_to_display_here);
        _lowerLeftField.setPromptId(R.string.Choose_a_field_to_display_here);
        _lowerRightField.setPromptId(R.string.Choose_a_field_to_display_here);
        _colorCodeField.setPromptId(R.string.Choose_a_field_to_display_here);
        _subtaskOption.setPromptId(R.string.Adjust_subtask_display);
        _parentOption.setPromptId(R.string.If_parent_is_filtered_out_);

        // If subtasks are disabled, then hide the subtask option:
        _settings = this.getSharedPreferences(Util.PREFS_NAME, 0);
        if (!_settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	findViewById(R.id.display_options_subtask_format_text).setVisibility(
        		View.GONE);
        	findViewById(R.id.display_options_subtask_format_spinner).setVisibility(
        		View.GONE);
        	hideParentOptions();
        }

		showOrHideWarnings();
        initBannerAd();

        // Handle changes in the subtask option.  The subtask option affects the visibility
        // of the parent option:
        _subtaskOption.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?> parent, View  view,
                int position, long id)
            {
        		if (position==0)
        			showParentOptions();
        		else
        			hideParentOptions();
				showOrHideWarnings();
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		});

		_parentOption.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			public void onItemSelected(AdapterView<?> parent, View  view, int position, long id)
			{
				showOrHideWarnings();
			}

			public void onNothingSelected(AdapterView<?>  parent)
			{
				// Nothing to do.
			}
		});
        
        // Handles changes in the divider display option:
        _dividers.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_dividers.toggle();
			}
		});
    }
	
    /** Overrides the default size function, taking into account the small size of this popup: */
    @Override
    protected Point getPopupSize()
    {
    	// Start with default size:
    	Point size = super.getPopupSize();
    	
    	int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
    	if (getOrientation()==ORIENTATION_LANDSCAPE)
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth/2;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				return size;
			}
    	}
    	else
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth*9/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*8/10;
				return size;
			}
    	}
    	
    	return size;
    }

    // Hide the parent options (when the subtasks are not indented):
	void hideParentOptions()
	{
		findViewById(R.id.display_options_parent_options_text).setVisibility(View.GONE);
		_parentOption.setVisibility(View.GONE);
	}
	
	// Show the parent options (when the subtasks are indented):
	void showParentOptions()
	{
		findViewById(R.id.display_options_parent_options_text).setVisibility(View.VISIBLE);
		_parentOption.setVisibility(View.VISIBLE);
	}

	/** Show warnings if needed: */
	void showOrHideWarnings()
	{
		Cursor viewCursor = _viewsDB.getView(_viewID);
		String[] sortFields = Util.cString(viewCursor, "sort_string").split(",");
		viewCursor.close();
		if (sortFields[0].equals("tasks.sort_order"))
		{
			if (_subtaskOption.getSelectedItemPosition()==1)
			{
				// This is the "not indented" option, which is not compatible with manual sorting.
				findViewById(R.id.display_options_manual_sort_warning_1).setVisibility(View.VISIBLE);
				findViewById(R.id.display_options_manual_sort_warning_2).setVisibility(View.GONE);
			}
			else if (_subtaskOption.getSelectedItemPosition()==0 && _parentOption.
				getSelectedItemPosition()==0)
			{
				// Subtasks are indended, but orphaned subtasks are set to show at the top level.
				// This is also not compatible with manual sort.
				findViewById(R.id.display_options_manual_sort_warning_1).setVisibility(View.GONE);
				findViewById(R.id.display_options_manual_sort_warning_2).setVisibility(View.VISIBLE);
			}
			else
			{
				findViewById(R.id.display_options_manual_sort_warning_1).setVisibility(View.GONE);
				findViewById(R.id.display_options_manual_sort_warning_2).setVisibility(View.GONE);
			}
		}
		else
		{
			// There are no warnings to display if the first sort order is not manual sort.
			findViewById(R.id.display_options_manual_sort_warning_1).setVisibility(View.GONE);
			findViewById(R.id.display_options_manual_sort_warning_2).setVisibility(View.GONE);
		}
	}

	@Override
	public void handleSave()
	{
		String[] dbCodes = DisplayOptions.getDatabaseCodes();
		String[] colorCodes = DisplayOptions.getColorCodeDbCodes();
		
		// Update the DisplayOptions instance to match the Spinners:
		_displayOptions.upperRightField = dbCodes[_upperRightField.getSelectedItemPosition()];
		_displayOptions.lowerLeftField = dbCodes[_lowerLeftField.getSelectedItemPosition()];
		_displayOptions.lowerRightField = dbCodes[_lowerRightField.getSelectedItemPosition()];
		_displayOptions.leftColorCodedField = colorCodes[_colorCodeField.getSelectedItemPosition()];
		_displayOptions.showDividers = _dividers.isChecked();
		if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
			switch (_subtaskOption.getSelectedItemPosition())
			{
			  case 0:
				_displayOptions.subtaskOption = "indented";
				_displayOptions.parentOption = _parentOption.getSelectedItemPosition();
				break;
			  case 1:
				_displayOptions.subtaskOption = "flattened";
				break;
			  case 2:
				_displayOptions.subtaskOption = "separate_screen";
				break;
			}
        }

        // Record usage of the "importance" feature, if needed.
        if (_displayOptions.upperRightField.equals("importance") || _displayOptions.lowerLeftField.equals("importance")
            || _displayOptions.lowerRightField.equals("importance"))
        {
            FeatureUsage fu = new FeatureUsage(this);
            fu.record(FeatureUsage.TOODLEDO_IMPORTANCE);
        }

		// Update the display options in the database:
		if (!_viewsDB.changeDisplayOptions(_viewID, _displayOptions))
		{
			Util.popup(EditDisplayOptions.this,R.string.DbModifyFailed);
			Util.log("Unable to change display options in database.");
			return;
		}
		
		// Close the activity:
		setResult(RESULT_OK);
		finish();
	}
}
