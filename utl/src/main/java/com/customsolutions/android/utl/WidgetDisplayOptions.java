package com.customsolutions.android.utl;

// This class is used for editing the display options for the widget.

// A Bundle is passed in with the following inputs:
// view_id: The ID of the view we are editing.

// This should be started with a startActivityForResult().  A resultCode of RESULT_CANCELED
// or RESULT_OK is returned.  No Bundle is passed back (because it is not needed).
// This activity updates the View in the database.

import java.util.ArrayList;

import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class WidgetDisplayOptions extends UtlSaveCancelPopupActivity
{
	// Views we need to keep track of:
	private Spinner _extraField;
	private Spinner _colorCodeField;
	private Spinner _subtaskOption;
	private Spinner _parentOption;
	private EditText _title;
	private Spinner _theme;
	private Spinner _format;
	private EditText _leftFieldWidth;
	
	// Handle into the database:
	private ViewsDbAdapter _viewsDB;
	
	// The view ID passed in:
	private long _viewID;
	
	// The DisplayOptions we are working with:
	private DisplayOptions _displayOptions;
	
	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
		super.onCreate(savedInstanceState);
        Util.log("Start the Widget Display Options chooser.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.widget_display_options);
        
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
        
        // Read the view passed in, and get the current display options:
        Cursor c = _viewsDB.getView(_viewID);
        if (!c.moveToFirst())
        {
        	Util.log("Invalid view ID "+_viewID+" passed to WidgetDisplayOptions.java.");
        	finish();
        	return;
        }
        _displayOptions = new DisplayOptions(Util.cString(c, "display_string"));
        c.close();
        
        // Set the icon at the top:
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_display_options));
        
        // If a name was specified, display it in the title bar:
        if (_displayOptions.widgetTitle.length()>0)
        {
        	getSupportActionBar().setTitle(Util.getString(R.string.Display_Options_for_Widget)+" \""+
        		_displayOptions.widgetTitle+"\"");
        }
        else
        {
        	getSupportActionBar().setTitle(R.string.Display_Options_for_Widget);
        }

        // Link local variables to layout items:
        _extraField = (Spinner)findViewById(R.id.widget_display_extra_spinner);
        _colorCodeField = (Spinner)findViewById(R.id.widget_display_color_code_spinner);
        _subtaskOption = (Spinner)findViewById(R.id.widget_display_subtask_format_spinner);
        _parentOption = (Spinner)findViewById(R.id.widget_display_parent_options_spinner);
        _title = (EditText)findViewById(R.id.widget_display_title_edittext);
        _theme = (Spinner)findViewById(R.id.widget_display_theme_spinner);
        _format = findViewById(R.id.widget_display_format_spinner);
        _leftFieldWidth = (EditText)findViewById(R.id.widget_display_left_field_width2);
        
        // Initialize the spinner choices:
        initSpinner(_extraField, DisplayOptions.getWidgetFieldDescriptions());
        initSpinner(_colorCodeField, DisplayOptions.getWidgetColorCodeDescriptions());
        
        // In order to initialize the Spinner values, we need an ArrayList of the available
        // database codes:
        ArrayList<String> dbCodes = new ArrayList<String>();
        String[] dbCodes2 = DisplayOptions.getWidgetDatabaseCodes();
        for (int i=0; i<dbCodes2.length; i++)
        {
        	dbCodes.add(dbCodes2[i]);
        }
        ArrayList<String> colorCodes = new ArrayList<String>();
        String[] colorCodes2 = DisplayOptions.getWidgetColorCodeDbCodes();
        for (int i=0; i<colorCodes2.length; i++)
        {
        	colorCodes.add(colorCodes2[i]);
        }
        
        // Initialize the Spinners and the Title EditText:
        _extraField.setSelection(dbCodes.indexOf(_displayOptions.widgetExtraField));
        _colorCodeField.setSelection(colorCodes.indexOf(_displayOptions.
        	widgetColorCodedField));
        if (_displayOptions.widgetSubtaskOption.equals("indented"))
        {
        	_subtaskOption.setSelection(0);
        	showParentOptions();
        	_parentOption.setSelection(_displayOptions.widgetParentOption);
        }
        if (_displayOptions.widgetSubtaskOption.equals("flattened"))
        {
        	_subtaskOption.setSelection(1);
        	hideParentOptions();
        }
        if (_displayOptions.widgetSubtaskOption.equals("separate_screen"))
        {
        	_subtaskOption.setSelection(2);
        	hideParentOptions();
        }
        _theme.setSelection(_displayOptions.widgetTheme);
        _format.setSelection(_displayOptions.widgetFormat);
        _title.setText(_displayOptions.widgetTitle);
        
        // Initialize the extra field width display:
    	_leftFieldWidth.setText(Integer.valueOf(_displayOptions.widgetExtraFieldWidth).
    		toString());
        
        // Set a title for the spinners:
        _extraField.setPromptId(R.string.Choose_a_field_to_display_here);
        _colorCodeField.setPromptId(R.string.Choose_a_field_to_display_here);
        _subtaskOption.setPromptId(R.string.Adjust_subtask_display);
        _parentOption.setPromptId(R.string.If_parent_is_filtered_out_);
        _theme.setPromptId(R.string.Theme);
        _format.setPromptId(R.string.format_);
        
        refreshFieldVisibility();
        
        // Handle changes in the widget format spinner:
        _format.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
				int arg2, long arg3)
			{
				refreshFieldVisibility();
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0)
			{
			}
		});
        
        // Handle changes in the subtask option.  The subtask option affects the visibility
        // of the parent option:
        _subtaskOption.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		refreshFieldVisibility();
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		});
        
        // Handle changes in the left field:
        _extraField.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		refreshFieldVisibility();
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		});

        initBannerAd();
    }

	// Refresh field visibility:
	void refreshFieldVisibility()
	{
		if (_format.getSelectedItemPosition()==0)
		{
			// The compact format:
			findViewById(R.id.widget_display_normal_diagram_wrapper).setVisibility(View.GONE);
			findViewById(R.id.widget_display_compact_diagram_wrapper).setVisibility(View.VISIBLE);
			
			// Update the text for the color-coded field:
			TextView tv = (TextView)findViewById(R.id.widget_display_color_code_text);
			tv.setText(R.string._Field_for_Color_Coded_Bar_);
			
			// The extra field width is hidden if no extra field is chosen, or this is not a scrollable
			// widget.
			if (_extraField.getSelectedItemPosition()>0 && _displayOptions.widgetIsScrollable==1)
				findViewById(R.id.widget_display_left_field_width_container).setVisibility(View.VISIBLE);
			else
				findViewById(R.id.widget_display_left_field_width_container).setVisibility(View.GONE);
			
			// Update the widget information TextView to show information on the compact themes:
			tv = (TextView)findViewById(R.id.widget_display_theme_info);
			tv.setText(R.string.Widget_Theme_Info_Compact);
		}
		else
		{
			// The normal format:
			findViewById(R.id.widget_display_normal_diagram_wrapper).setVisibility(View.VISIBLE);
			findViewById(R.id.widget_display_compact_diagram_wrapper).setVisibility(View.GONE);
			findViewById(R.id.widget_display_left_field_width_container).setVisibility(View.GONE);
			
			// Update the text for the color-coded field:
			TextView tv = (TextView)findViewById(R.id.widget_display_color_code_text);
			tv.setText(R.string._Field_for_Colored_Checkbox_);
			
			// Update the widget information TextView to show information on the compact themes:
			tv = (TextView)findViewById(R.id.widget_display_theme_info);
			if (Build.VERSION.SDK_INT<11)
				tv.setText(R.string.Widget_Requires_Android_3);
			else
				tv.setText(R.string.Widget_Theme_Info_Normal);
		}
		
		// Hide subtask options if subtasks are disabled:
		if (!Util.settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	findViewById(R.id.widget_display_subtask_format_text).setVisibility(
        		View.GONE);
        	findViewById(R.id.widget_display_subtask_format_spinner).setVisibility(
        		View.GONE);
        	hideParentOptions();
        }
		else
		{
			if (_subtaskOption.getSelectedItemPosition()==0)
				showParentOptions();
			else
				hideParentOptions();
		}
	}
	
	// Hide the parent options (when the subtasks are not indented):
	void hideParentOptions()
	{
		findViewById(R.id.widget_display_parent_options_text).setVisibility(View.GONE);
		_parentOption.setVisibility(View.GONE);
	}
	
	// Show the parent options (when the subtasks are indented):
	void showParentOptions()
	{
		findViewById(R.id.widget_display_parent_options_text).setVisibility(View.VISIBLE);
		_parentOption.setVisibility(View.VISIBLE);
	}

	@Override
	public void handleSave()
	{
		String[] dbCodes = DisplayOptions.getWidgetDatabaseCodes();
		String[] colorCodes = DisplayOptions.getWidgetColorCodeDbCodes();
		
		// Update the DisplayOptions to match the Spinners and EditText:
		_displayOptions.widgetExtraField = dbCodes[_extraField.
	        getSelectedItemPosition()];
		_displayOptions.widgetColorCodedField = colorCodes[_colorCodeField.
		    getSelectedItemPosition()];
		if (Util.settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
			switch (_subtaskOption.getSelectedItemPosition())
			{
			  case 0:
				_displayOptions.widgetSubtaskOption = "indented";
				_displayOptions.widgetParentOption = _parentOption.
					getSelectedItemPosition();
				break;
			  case 1:
				_displayOptions.widgetSubtaskOption = "flattened";
				break;
			  case 2:
				_displayOptions.widgetSubtaskOption = "separate_screen";
				break;
			}
        }
		_displayOptions.widgetTheme = _theme.getSelectedItemPosition();
		_displayOptions.widgetFormat = _format.getSelectedItemPosition();
		_displayOptions.widgetTitle = _title.getText().toString();
		if (_extraField.getSelectedItemPosition()>0 && _displayOptions.
			widgetIsScrollable==1)
		{
			try
			{
				_displayOptions.widgetExtraFieldWidth = Integer.parseInt(
					_leftFieldWidth.getText().toString());
			}
			catch (NumberFormatException e)
			{
				// Don't set it if the value is not valid.
			}
		}
		
		// Update the display options in the database:
		if (!_viewsDB.changeDisplayOptions(_viewID, _displayOptions))
		{
			Util.popup(WidgetDisplayOptions.this,R.string.DbModifyFailed);
			Util.log("Unable to change display options in database.");
			return;
		}
						
		// Close the activity:
		WidgetDisplayOptions.this.setResult(RESULT_OK);
		WidgetDisplayOptions.this.finish();				
	}
}
