package com.customsolutions.android.utl;

// Names of top level views.  These are used to link to and identify views in the app.

public class ViewNames
{
	// These correspond with the "top_level" field passed to Activity instances.  A second field,
	//    view_name is also passed.  If not specified in the comments here, it is left blank.
	public static final String ALL_TASKS = "all_tasks";
	public static final String HOTLIST = "hotlist";
	public static final String MY_VIEWS = "my_views";
		// view_name field is the user's name for the view.
	public static final String DUE_TODAY_TOMORROW = "due_today_tomorrow";
	public static final String OVERDUE = "overdue";
	public static final String STARRED = "starred";
	public static final String BY_STATUS = "by_status";
		// view_name field is the status value as an integer
	public static final String FOLDERS = "folders";
		// view_name field is the folder ID in the UTL database
	public static final String CONTEXTS = "contexts";
		// view_name field is the context ID in the UTL database
	public static final String TAGS = "tags";
		// view_name field is the tag name
	public static final String GOALS = "goals";
		// view_name field is the goal ID in the UTL database
	public static final String LOCATIONS = "locations";
		// view_name field is the location ID in the UTL database
	public static final String RECENTLY_COMPLETED = "recently_completed";
	public static final String WIDGET = "widget";
	    // view_name field is the system's widget ID	
	public static final String SUBTASK = "subtask";
	    // temporary view which will be deleted by the app.  view_name is just the current timestamp.
	public static final String SEARCH = "search";
		// view_name is a string containing the current millisecond timestamp
	public static final String TEMP = "temp";
}
