package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

// This class stores display options for a view (what fields will be displayed in what
// areas, etc.

public class DisplayOptions 
{
	private static final String TAG = "DisplayOptions";

    // Field for the color-coded bar on the left.  Valid values are:
    // status, priority, star, none
    public String leftColorCodedField;
    
    // These variables hold the fields displayed in the lower-left, upper-right, and
    // lower-right.  (The upper-left always has the title.)  Valid values are:
    // account, folder, context, goal, location, tags, due_date, start_date, status,
    // length, priority, note, timer, icons (for status icons), folder_context (for both),
    // start_due (for both), completion_date, mod_date, contact, due_date_time,
    // start_date_time, due_time, start_time, reminder
    public String lowerLeftField;
    public String lowerRightField;
    public String upperRightField;
    
    // The subtask option.  One of: "indented", "flattened", "separate_screen":
    public String subtaskOption;
    
    // The parent option (if a subtask is visible but the parent is filtered out):
    // 0 = show subtask on parents' level
    // 1 = hide subtask
    public int parentOption;
    
    // Show dividers in the list?
    public boolean showDividers;
    
    // The following only apply to widget views.  These will default to "" or 0.
    public String widgetTitle;           // The title at the top of the widget
    public String widgetExtraField;      // All of above except for icons and folder_context
    public String widgetColorCodedField; // Save values as leftColorCodedField.
    public String widgetSubtaskOption;   // Same values as above.
    public int widgetTheme;              // 0-5. Same as main app.
	public int widgetFormat;             // 0 = compact, 1 = normal
    public int widgetParentOption;       // 0 = show without indenting, 1 = hide subtask
    public int widgetIsScrollable;       // 0 = no, 1 = yes
    public int widgetExtraFieldWidth;    // For scrollable only
    
    // Note: for widgetExtraField and widgetColorCodedField, setting these to "" will
    // cause them to be omitted from the widget.  A widgetTitle of "" will cause a display
    // of "To-Do List"
    
    // Default width for extra widget field (scrollable only)
    static private int DEFAULT_EXTRA_FIELD_WIDTH = 130;  // pixels
    
    // Create display options from user inputs:
    public DisplayOptions(String leftColorCode, String lowerLeft, String lowerRight,
        String upperRight, String subtask, boolean showDiv)
    {
        leftColorCodedField = leftColorCode;
        lowerLeftField = lowerLeft;
        lowerRightField = lowerRight;
        upperRightField = upperRight;
        subtaskOption = subtask;
        parentOption = 0;
        showDividers = showDiv;
        widgetTitle = "";
        widgetExtraField = "";
        widgetColorCodedField = "";
        widgetSubtaskOption = "";
        widgetTheme = 0;
        widgetFormat = 0;
        widgetParentOption = 0;
        widgetIsScrollable = 0;
        widgetExtraFieldWidth = DEFAULT_EXTRA_FIELD_WIDTH;
    }
    
    // Create display options from a record in the database:
    public DisplayOptions(String dbRecord)
    {
        Bundle b = Util.parseURLString(dbRecord);
        leftColorCodedField = b.getString("left_color_code");
        lowerLeftField = b.getString("lower_left");
        lowerRightField = b.getString("lower_right");
        upperRightField = b.getString("upper_right");
        subtaskOption = b.getString("subtask_option");
        
        if (b.containsKey("parent_option"))
        	parentOption = Integer.parseInt(b.getString("parent_option"));
        else
        	parentOption = 0;
        
        if (b.containsKey("show_dividers"))
        	showDividers = Integer.parseInt(b.getString("show_dividers"))==1 ? true : false;
        else
        	showDividers = true;
        
        if (b.containsKey("widget_title"))
        	widgetTitle = b.getString("widget_title");
        else
        	widgetTitle = "";
        
        if (b.containsKey("widget_extra_field"))
        	widgetExtraField = b.getString("widget_extra_field");
        else
        	widgetExtraField = "";
        
        if (b.containsKey("widget_color_coded_field"))
        	widgetColorCodedField = b.getString("widget_color_coded_field");
        else
        	widgetColorCodedField = "";
        
        if (b.containsKey("widget_subtask_option"))
        	widgetSubtaskOption = b.getString("widget_subtask_option");
        else
        	widgetSubtaskOption = "";
        
        if (b.containsKey("widget_theme"))
        	widgetTheme = Integer.parseInt(b.getString("widget_theme"));
        else
        	widgetTheme = 0; // Use light if not specified. Prevents changing existing widgets.

		if (b.containsKey("widget_format"))
			widgetFormat = Integer.parseInt(b.getString("widget_format"));
		else
			widgetFormat = 0;

		if (b.containsKey("widget_theme") && !b.containsKey("widget_format"))
		{
			// This occurs when the user hasn't yet updated the settings for version 4.1 of the
			// app. Set some defaults:
			switch (widgetTheme)
			{
				case 0:
					widgetTheme = 2;
					widgetFormat = 0;
					break;

				case 1:
					widgetTheme = 5;
					widgetFormat = 0;
					break;

				case 2:
					widgetTheme = 2;
					widgetFormat = 1;
					break;

				default: // 3
					widgetTheme = 5;
					widgetFormat = 1;
					break;
			}
		}

        if (b.containsKey("widget_parent_option"))
        	widgetParentOption = Integer.parseInt(b.getString("widget_parent_option"));
        else
        	widgetParentOption = 0;
        
        if (b.containsKey("widget_is_scrollable"))
        	widgetIsScrollable = Integer.parseInt(b.getString("widget_is_scrollable"));
        else
        	widgetIsScrollable = 0;
        
        if (b.containsKey("widget_extra_field_width"))
        	widgetExtraFieldWidth = Integer.parseInt(b.getString("widget_extra_field_width"));
        else
        	widgetExtraFieldWidth = DEFAULT_EXTRA_FIELD_WIDTH;
    }
    
    // Given a top-level view, construct the default display options:
    public static DisplayOptions getDefaultDisplayOptions(String topLevel)
    {
    	// The left side option:
        String left;
        if (Util.settings.getBoolean("priority_enabled", true))
        {
            left = "priority";
        }
        else if (Util.settings.getBoolean("status_enabled",true))
        {
            left = "status";
        }
        else if (Util.settings.getBoolean("star_enabled", true))
        {
            left = "star";
        }
        else
        {
            left = "none";
        }
        
        // The lower-left option that will be used most of the time:
        String defaultLowerLeft;
        if (Util.settings.getBoolean("folders_enabled",true) && 
            Util.settings.getBoolean("contexts_enabled",true))
        {
        	defaultLowerLeft = "folder_context";
        }
        else if (Util.settings.getBoolean("folders_enabled",true))
        {
            defaultLowerLeft = "folder";
        }
        else if (Util.settings.getBoolean("contexts_enabled",true))
        {
            defaultLowerLeft = "context";
        }
        else
        {
            defaultLowerLeft = "note";
        }
        
        // The lower-right option that will be used most of the time:
        String defaultLowerRight;
        if (Util.settings.getBoolean("due_date_enabled", true))
        {
            defaultLowerRight = "due_date";
        }
        else if (Util.settings.getBoolean("start_date_enabled", true))
        {
            defaultLowerRight = "start_date";
        }
        else
        {
            if (!defaultLowerLeft.equals("note"))
            {
                defaultLowerRight = "note";
            }
            else if (Util.settings.getBoolean("tags_enabled",true))
            {
                defaultLowerRight = "tags";
            }
            else if (Util.settings.getBoolean("status_enabled",true))
            {
                defaultLowerRight = "status";
            }
            else
            {
                defaultLowerRight = "account";
            }
        }
        
        // The default upper-right option:
        String defaultUpperRight = "icons";
        
        // We may need to tweak the defaults for certain views:
        if (topLevel.equals("folders"))
        {
            if (Util.settings.getBoolean("contexts_enabled",true))
            {
                defaultLowerLeft = "context";
            }
            else
            {
                defaultLowerLeft = "note";
            }
        }
        if (topLevel.equals("contexts"))
        {
            if (Util.settings.getBoolean("folders_enabled",true))
            {
                defaultLowerLeft = "folder";
            }
            else
            {
                defaultLowerLeft = "note";
            }
        }
        if (topLevel.equals("recently_completed"))
        {
            defaultLowerRight="completion_date";
        }
        
        DisplayOptions d = new DisplayOptions(left, defaultLowerLeft, defaultLowerRight,
            defaultUpperRight,"indented",true);
        d.parentOption = 0;
        
        if (topLevel.equals("widget"))
        {
        	// Set default widget display options:
        	if (Util.settings.getBoolean("due_date_enabled", true))
        		d.widgetExtraField = "due_date";
        	if (Util.settings.getBoolean("priority_enabled", true))
        		d.widgetColorCodedField = "priority";
        	d.widgetSubtaskOption = "indented";
			SharedPreferences prefs = Util.context.getSharedPreferences(Util.PREFS_NAME,0);
        	d.widgetTheme = prefs.getInt(PrefNames.THEME,0);
        	d.widgetFormat = 0;  // Compact
        	d.widgetParentOption = 0;
        	d.widgetIsScrollable = 0;
        	d.widgetExtraFieldWidth = DEFAULT_EXTRA_FIELD_WIDTH;
        }
        
        return (d);
    }
    
    // Get a string that can be inserted into the database:
    public String getDatabaseString()
    {
        Bundle b = new Bundle();
        b.putString("left_color_code", leftColorCodedField);
        b.putString("lower_left", lowerLeftField);
        b.putString("lower_right", lowerRightField);
        b.putString("upper_right", upperRightField);
        b.putString("subtask_option", subtaskOption);
        b.putString("parent_option", Integer.valueOf(parentOption).toString());
        b.putString("show_dividers", showDividers ? "1" : "0");
        if (widgetTitle.length()>0)
        	b.putString("widget_title", widgetTitle);
        if (widgetExtraField.length()>0)
        	b.putString("widget_extra_field", widgetExtraField);
        if (widgetColorCodedField.length()>0)
        	b.putString("widget_color_coded_field", widgetColorCodedField);
        if (widgetSubtaskOption.length()>0)
        	b.putString("widget_subtask_option", widgetSubtaskOption);
        b.putString("widget_theme", Integer.valueOf(widgetTheme).toString());
        b.putString("widget_format", String.valueOf(widgetFormat));
        b.putString("widget_parent_option", Integer.valueOf(widgetParentOption).toString());
        b.putString("widget_is_scrollable", Integer.valueOf(widgetIsScrollable).toString());
        b.putString("widget_extra_field_width", Integer.valueOf(widgetExtraFieldWidth).toString());
        return Util.createURLString(b);
    }
    
    // Get an array of strings containing the available database codes for the upper-right,
    // lower-left, or lower-right sections:
    static public String[] getDatabaseCodes()
    {
    	ArrayList<String> codeList = new ArrayList<String>();
    	
    	codeList.add("account");
    	if (Util.settings.getBoolean(PrefNames.TIMER_ENABLED, true))
    	{
    		codeList.add("timer");
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("assignor_name");
    	}
    	codeList.add("completion_date");
    	if (Util.settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
    		codeList.add("contact");
    	if (Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		codeList.add("context");
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		codeList.add("due_date");
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
    	{
    		codeList.add("due_date_time");
    		codeList.add("due_time");
    	}
    	if (Util.settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
    	{
    		codeList.add("length");
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		codeList.add("folder");
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true) &&
    		Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		codeList.add("folder_context");
    	}
    	if (Util.settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		codeList.add("goal");
    	}
    	codeList.add("icons");
    	codeList.add("importance");
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("is_joint");
    	}
    	if (Util.settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		codeList.add("location");
    	}
    	codeList.add("mod_date");
    	codeList.add("note");
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("owner_name");
    	}
    	if (Util.settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
    	{
    		codeList.add("priority");
    	}
    	if (Util.settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
    	{
    		codeList.add("reminder");
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("shared_with");
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true) &&
    		Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		codeList.add("start_due");
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
    	{
    		codeList.add("start_date");
    	}
    	if (Util.settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
    	{
    		codeList.add("start_date_time");
    		codeList.add("start_time");
    	}
    	if (Util.settings.getBoolean(PrefNames.STATUS_ENABLED, true))
    	{
    		codeList.add("status");
    	}
    	if (Util.settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
    		codeList.add("tags");
    	}
		if (Util.ENABLE_SORT_ORDER)
		{
			codeList.add("sort_order");
		}
    	
    	return Util.iteratorToStringArray(codeList.iterator(), codeList.size());
    }
    
    // Same as above, but get an array of human-readable descriptions:
    static public String[] getFieldDescriptions()
    {
    	ArrayList<String> descList = new ArrayList<String>();
    	descList.add(Util.getString(R.string.Account));
    	if (Util.settings.getBoolean(PrefNames.TIMER_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Actual_Length));
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Assignor));
    	}
    	descList.add(Util.getString(R.string.CompletionDate));
    	if (Util.settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
    		descList.add(Util.getString(R.string.Contact));
    	if (Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Context));
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Due_Date));
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Due_Date_Time2));
    		descList.add(Util.getString(R.string.Due_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Expected_Length));
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Folder));
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true) &&
    		Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Folder_and_Context));
    	}
    	if (Util.settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Goal));
    	}
    	descList.add(Util.getString(R.string.icons));
    	descList.add(Util.getString(R.string.Importance));
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Is_Shared));
    	}
    	if (Util.settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Location));
    	}
    	descList.add(Util.getString(R.string.Modification_Date));
    	descList.add(Util.getString(R.string.Note));
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Owner));
    	}
    	if (Util.settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Priority));
    	}
    	if (Util.settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Reminder_Date_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Shared_With));
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true) &&
    		Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Start_and_Due_Date));
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Start_Date));
    	}
    	if (Util.settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Start_Date_Time2));
    		descList.add(Util.getString(R.string.Start_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.STATUS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Status));
    	}
    	if (Util.settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Tags));
    	}
		if (Util.ENABLE_SORT_ORDER)
		{
			descList.add("Manual Sort Order");
		}
    	
    	return Util.iteratorToStringArray(descList.iterator(), descList.size());
    }
    
    // Get an array of strings containing the available database codes for the widget's
    // extra field:
    static public String[] getWidgetDatabaseCodes()
    {
    	ArrayList<String> codeList = new ArrayList<String>();
    	codeList.add("");  // For the "none" option.
    	codeList.add("account");
    	if (Util.settings.getBoolean(PrefNames.TIMER_ENABLED, true))
    	{
    		codeList.add("timer");
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("assignor_name");
    	}
    	codeList.add("completion_date");
    	if (Util.settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
    		codeList.add("contact");
    	if (Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		codeList.add("context");
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		codeList.add("due_date");
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
    	{
    		codeList.add("due_date_time");
    		codeList.add("due_time");
    	}
    	if (Util.settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
    	{
    		codeList.add("length");
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		codeList.add("folder");
    	}
    	if (Util.settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		codeList.add("goal");
    	}
    	codeList.add("importance");
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("is_joint");
    	}
    	if (Util.settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		codeList.add("location");
    	}
    	codeList.add("mod_date");
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("owner_name");
    	}
    	if (Util.settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
    	{
    		codeList.add("priority");
    	}
    	if (Util.settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
    	{
    		codeList.add("reminder");
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		codeList.add("shared_with");
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
    	{
    		codeList.add("start_date");
    	}
    	if (Util.settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
    	{
    		codeList.add("start_date_time");
    		codeList.add("start_time");
    	}
    	if (Util.settings.getBoolean(PrefNames.STATUS_ENABLED, true))
    	{
    		codeList.add("status");
    	}
    	if (Util.settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
    		codeList.add("tags");
    	}
   	
    	return Util.iteratorToStringArray(codeList.iterator(), codeList.size());
    }
    
    // Same as above, but get an array of human-readable descriptions:
    static public String[] getWidgetFieldDescriptions()
    {
    	ArrayList<String> descList = new ArrayList<String>();
    	descList.add(Util.getString(R.string.None));
    	descList.add(Util.getString(R.string.Account));
    	if (Util.settings.getBoolean(PrefNames.TIMER_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Actual_Length));
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Assignor));
    	}
    	descList.add(Util.getString(R.string.CompletionDate));
    	if (Util.settings.getBoolean(PrefNames.CONTACTS_ENABLED, true))
    		descList.add(Util.getString(R.string.Contact));
    	if (Util.settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Context));
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Due_Date));
    	}
    	if (Util.settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Due_Date_Time2));
    		descList.add(Util.getString(R.string.Due_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Expected_Length));
    	}
    	if (Util.settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Folder));
    	}
    	if (Util.settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Goal));
    	}
    	descList.add(Util.getString(R.string.Importance));
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Is_Shared));
    	}
    	if (Util.settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Location));
    	}
    	descList.add(Util.getString(R.string.Modification_Date));
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Owner));
    	}
    	if (Util.settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Priority));
    	}
    	if (Util.settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Reminder_Date_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Shared_With));
    	}
    	if (Util.settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Start_Date));
    	}
    	if (Util.settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Start_Date_Time2));
    		descList.add(Util.getString(R.string.Start_Time));
    	}
    	if (Util.settings.getBoolean(PrefNames.STATUS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Status));
    	}
    	if (Util.settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
    		descList.add(Util.getString(R.string.Tags));
    	}
    	
    	return Util.iteratorToStringArray(descList.iterator(), descList.size());
    }

    // Get an array of strings containing database code for the color-coded bar:
    static public String[] getColorCodeDbCodes()
    {
    	ArrayList<String> codeList = new ArrayList<String>();
    	codeList.add("none");
    	if (Util.settings.getBoolean("priority_enabled", true))
    	{
    		codeList.add("priority");
    	}
    	if (Util.settings.getBoolean("status_enabled", true))
    	{
    		codeList.add("status");
    	}
    	if (Util.settings.getBoolean("star_enabled", true))
    	{
    		codeList.add("star");
    	}
    	
    	return Util.iteratorToStringArray(codeList.iterator(), codeList.size());
    }
    
    // Get an array of strings containing human readable options for the color-coded bar:
    static public String[] getColorCodeDescriptions()
    {
    	ArrayList<String> descList = new ArrayList<String>();
    	descList.add(Util.getString(R.string.None));
    	if (Util.settings.getBoolean("priority_enabled", true))
    	{
    		descList.add(Util.getString(R.string.Priority));
    	}
    	if (Util.settings.getBoolean("status_enabled", true))
    	{
    		descList.add(Util.getString(R.string.Status));
    	}
    	if (Util.settings.getBoolean("star_enabled", true))
    	{
    		descList.add(Util.getString(R.string.Star));
    	}
    	
    	return Util.iteratorToStringArray(descList.iterator(), descList.size());
    }

    // Get an array of strings containing database code for the color-coded bar:
    static public String[] getWidgetColorCodeDbCodes()
    {
    	ArrayList<String> codeList = new ArrayList<String>();
    	codeList.add("");
    	if (Util.settings.getBoolean("priority_enabled", true))
    	{
    		codeList.add("priority");
    	}
    	if (Util.settings.getBoolean("star_enabled", true))
    	{
    		codeList.add("star");
    	}
    	
    	return Util.iteratorToStringArray(codeList.iterator(), codeList.size());
    }
    
    // Get an array of strings containing human readable options for the color-coded bar:
    static public String[] getWidgetColorCodeDescriptions()
    {
    	ArrayList<String> descList = new ArrayList<String>();
    	descList.add(Util.getString(R.string.None));
    	if (Util.settings.getBoolean("priority_enabled", true))
    	{
    		descList.add(Util.getString(R.string.Priority));
    	}
    	if (Util.settings.getBoolean("star_enabled", true))
    	{
    		descList.add(Util.getString(R.string.Star));
    	}
    	
    	return Util.iteratorToStringArray(descList.iterator(), descList.size());
    }
}
