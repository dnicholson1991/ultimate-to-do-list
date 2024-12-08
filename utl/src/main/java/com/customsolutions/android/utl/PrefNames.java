package com.customsolutions.android.utl;

// This class holds the names for each user preference:

public class PrefNames
{
	// Sync Options:
	public static final String AUTO_SYNC = "auto_sync";
	public static final String SYNC_INTERVAL = "sync_interval";       // Integer
	public static final String INSTANT_UPLOAD = "instant_upload";
	public static final String NO_SYNC_WARNING = "warn_on_no_sync";
	
	// Display Options:
	public static final String FONT_SIZE = "font_size";       // "small", "medium", or "large"
	public static final String THEME = "theme";               // Integer. 0=light, 1=dark.
	public static final String SS_LANDSCAPE = "ss_landscape"; // Integer from 0 to 3, defined in Uitl.java
	public static final String SS_PORTRAIT = "ss_portrait";   // Integer from 0 to 3, defined in Uitl.java
	
	// Reminder Options:
	public static final String RINGTONE = "ringtone";             // URI string or "Default"
	public static final String VIBE_PATTERN = "vibe_pattern";     // String describing pattern
	public static final String REMINDER_LIGHT = "reminder_light"; // Index into argb_light_colors array.
	public static final String NAG_INTERVAL = "nag_interval";     // Integer. Minutes.
	public static final String NOTIFICATION_PRIORITY = "notification_priority";
	    // Integer from 0 to 4.  The system should be sent this value minus 2 (range -2 to +2)  
	public static final String USE_ONGOING_NOTIFICATIONS = "use_ongoing_notifications";
	
	// Fields/Functions Used:
	public static final String CALENDAR_ENABLED = "calendar_enabled";
	public static final String COLLABORATORS_ENABLED = "collaborators_enabled";
	public static final String REMINDER_ENABLED = "reminder_enabled";
	public static final String FOLDERS_ENABLED = "folders_enabled";
	public static final String CONTEXTS_ENABLED = "contexts_enabled";
	public static final String GOALS_ENABLED = "goals_enabled";
	public static final String LOCATIONS_ENABLED = "locations_enabled";
	public static final String START_DATE_ENABLED = "start_date_enabled";
	public static final String START_TIME_ENABLED = "start_time_enabled";
	public static final String DUE_DATE_ENABLED = "due_date_enabled";
	public static final String DUE_TIME_ENABLED = "due_time_enabled";
	public static final String REPEAT_ENABLED = "repeat_enabled";
	public static final String LENGTH_ENABLED = "length_enabled";
	public static final String TIMER_ENABLED = "timer_enabled";
	public static final String PRIORITY_ENABLED = "priority_enabled";
	public static final String TAGS_ENABLED = "tags_enabled";
	public static final String STATUS_ENABLED = "status_enabled";
	public static final String STAR_ENABLED = "star_enabled";
	public static final String SUBTASKS_ENABLED = "subtasks_enabled";
	public static final String NOTE_FOLDERS_ENABLED = "note_folders_enabled";
	public static final String CONTACTS_ENABLED = "contacts_enabled";

	// Fields/Functions Presets (these are booleans and only one will be true):
	public static final String PRESET_BASIC = "preset_basic";
	public static final String PRESET_INTERMEDIATE = "preset_intermediate";
	public static final String PRESET_ADVANCED = "preset_advanced";
	public static final String PRESET_POWER_USER = "preset_power_user";
	
	// New Task Defaults:
	public static final String DEFAULT_ADD_TO_CAL = "default_add_to_calendar";
	public static final String DEFAULT_FOLDER = "default_folder";         // Database ID or 0
	public static final String DEFAULT_CONTEXT = "default_context";       // Database ID or 0
	public static final String DEFAULT_GOAL = "default_goal";             // Database ID or 0
	public static final String DEFAULT_START_DATE = "default_start_date"; // "", "today", or "tomorrow"
	public static final String DEFAULT_DUE_DATE = "default_due_date";     // "", "today", or "tomorrow"
	public static final String DEFAULT_PRIORITY = "default_priority";     // Integer. 0 to 5
	public static final String DEFAULT_STATUS = "default_status";         // Integer. 0 to 10
	public static final String DEFAULT_TAGS = "default_tags";             // Names (, separated)
	public static final String DEFAULT_LOCATION = "default_location";     // Database ID or 0
	public static final String DEFAULT_ACCOUNT = "default_account";       // Database ID or 0
	public static final String DOWNLOADED_TASK_ADD_TO_CAL = "add_downloaded_to_calendar";
	
	// Date and Time:
	public static final String WEEK_START = "week_start";   // "Sunday" or "Monday"
	public static final String DATE_FORMAT = "date_format"; // Integer defined in Util.java
	public static final String TIME_FORMAT = "time_format"; // Integer defined in DateTimePrefs.java
	
	// Viewing and Editing:
	public static final String OPEN_EDITOR_ON_TAP = "open_editor_on_tap";
	public static final String OPEN_NOTE_EDITOR_ON_TAP = "open_note_editor_on_tap";
	public static final String HIDE_COMPLETED_SUBTASKS = "hide_completed_subtasks";
	public static final String MANUAL_SORT_ON_LONG_PRESS = "manual_sort_on_long_press";
	public static final String UNDATED_SORT_ORDER = "undated_sort_order"; // 0 = Before tasks with a date, 1 = after
	
	// Location Tracking:
	public static final String LOCATION_PROVIDERS = "location_providers"; // Integer defined in Util.java
	public static final String LOCATION_CHECK_INTERVAL = "location_check_interval"; // Integer in minutes
	public static final String LOC_ALARM_RADIUS = "loc_alarm_radius"; // Integer in meters
	public static final String DISTANCE_MEASURE = "distance_measure"; // Int. 0=miles, 1=kilometers
	
	// Backup and Restore:
	public static final String BACKUP_DAILY = "run_backup_daily";
	
	// Navigation Drawer:
	public static final String SHOW_ALL_TASKS = "show_all_tasks";
	public static final String SHOW_HOTLIST = "show_hotlist";
	public static final String SHOW_DUE_TODAY_TOMORROW = "show_due_today_tomorrow";
	public static final String SHOW_OVERDUE = "show_overdue";
	public static final String SHOW_STARRED = "show_starred";
	public static final String SHOW_RECENTLY_COMPLETED = "show_recently_completed";
	public static final String SHOW_ARCHIVED_FOLDERS = "show_archived_folders";
	public static final String SHOW_ARCHIVED_GOALS = "show_archived_goals";
    public static final String NAV_DRAWER_EXPANDED_NODES = "nav_drawer_expanded_nodes"; // HashSet<String>
    public static final String NAV_DRAWER_SCROLL_Y = "nav_drawer_scroll_y";  // Int
	
	// Miscellaneous:
	public static final String LINKED_CALENDAR_ID = "linked_calendar_id"; // Long. ID from system or -1
	public static final String STARTUP_SCREEN = "startup_screen";   // Not used in version 3+
	public static final String STARTUP_VIEW_ID = "startup_view_id"; // Long. Database ID or -1
	public static final String STARTUP_VIEW_TITLE = "startup_view_title";  // A string, "" if not specified.
	public static final String PURGE_COMPLETED = "purge_completed"; // Integer. Num days.
	
	// Voice Mode: General Settings:
	public static final String VM_KEEP_SCREEN_ON = "vm_keep_screen_on";
	public static final String VM_LISTEN_IMMEDIATELY = "vm_listen_immediately";
	public static final String VM_TRIGGER_PHRASE = "vm_trigger_phrase";  // String
	public static final String VM_LANGUAGE_OVERRIDE = "vm_language_override";
		// true if user is not English but has chosen to use voice mode in English
	public static final String VM_TASKS_TO_READ_PER_PAGE = "vm_tasks_to_read_per_page"; // Integer
	public static final String VM_USE_BLUETOOTH = "vm_use_bluetooth"; // boolean
	
	// Voice Mode: New Task Defaults:
	public static final String VM_DEFAULT_ADD_TO_CAL = "vm_default_add_to_calendar";
	public static final String VM_DEFAULT_FOLDER = "vm_default_folder";         // Database ID or 0
	public static final String VM_DEFAULT_CONTEXT = "vm_default_context";       // Database ID or 0
	public static final String VM_DEFAULT_GOAL = "vm_default_goal";             // Database ID or 0
	public static final String VM_DEFAULT_START_DATE = "vm_default_start_date"; // "", "today", or "tomorrow"
	public static final String VM_DEFAULT_DUE_DATE = "vm_default_due_date";     // "", "today", or "tomorrow"
	public static final String VM_DEFAULT_PRIORITY = "vm_default_priority";     // Integer. 0 to 5
	public static final String VM_DEFAULT_STATUS = "vm_default_status";         // Integer. 0 to 10
	public static final String VM_DEFAULT_TAGS = "vm_default_tags";             // Names (, separated)
	public static final String VM_DEFAULT_LOCATION = "vm_default_location";     // Database ID or 0
	public static final String VM_DEFAULT_ACCOUNT = "vm_default_account";       // Database ID or 0

    // Android Wear Integration
    public static final String WEAR_DEFAULT_VIEW_ID = "wear_default_view_id"; // Long.  View ID.
    public static final String WEAR_SHOW_DAILY_SUMMARY = "wear_show_daily_summary"; // Boolean
    public static final String WEAR_SUMMARY_TIME = "wear_summary_time";  // ms since midnight

	// Set outside of preferences screens:
	public static final String TAG_LIST_SORT_ORDER = "tag_list_sort_order"; // Integer defined in EditTagsFragment.java
    public static final String HOTLIST_PRIORITY = "hotlist_priority"; // Integer from 1 to 5
    public static final String HOTLIST_DUE_DATE = "hotlist_due_date";  // Integer. number of days
	
	// Administrative / Not User Visible:
	public static final String HOME_TIME_ZONE = "home_time_zone"; // String ID of time zone.
	public static final String REDUCED_SYNC_INTERVAL = "reduced_sync_interval";
    public static final String INAPP_PURCHASE_OVERRIDES = "inapp_purchase_overrides"; // Newline separated list of SKUs.
	public static final String LOCATION_DOWNLOAD_NEEDED = "location_download_needed";
	public static final String COLLABORATION_DOWNLOAD_NEEDED = "collaboration_download_needed";
	public static final String CONVERSION_TO_V3_COMPLETED = "conversion_to_v3_completed";
	public static final String PREF_REBOOT_NEEDED = "pref_reboot_needed"; // If true, reboot after exiting settings
	public static final String WHATS_NEW_VERSION_SEEN = "whats_new_version_seen";
		// Integer.  Version code being used when the last "whats new" message was seen.
	public static final String LAST_AUTO_SYNC_TIME = "last_auto_sync_time"; // Long in millis.
	public static final String CREATE_SAMPLE_TASK = "create_sample_task";
	public static final String DIAGONAL_SCREEN_SIZE = "diagonal_screen_size";  // Float
    public static final String LAST_PING_TIME = "last_ping_time";  // ms timestamp
    public static final String LAST_DB_CLEANUP = "last_database_cleanup"; // ms timestamp
	public static final String LOG_TO_LOGCAT = "log_to_logcat"; // boolean

	/** A count of the number of errors/warnings logged at our server. */
	public static final String NUM_ERRORS_LOGGED = "num_errors_logged";

	/** The date the last error or warning was logged. A string date using the system's date
	 * format. */
	public static final String LAST_ERROR_DATE = "last_error_date";

	/** Flag indicating if the user has upgraded to version 4.1, which supports more themes. */
	public static final String UPGRADED_TO_V410 = "upgraded_to_v410";

	// Sizes of panes.  An nonexistent or 0 value means that the default is used.
	// Otherwise, these are integer pixel values, matching dropX and dropY values passed into
	// the handlers in UtlNavDrawerActivity.java.
	public static final String PANE_SIZE_NAV_LIST_LANDSCAPE = "pane_size_nav_list_landscape";
	public static final String PANE_SIZE_NAV_LIST_PORTRAIT = "pane_size_nav_list_portrait";
	public static final String PANE_SIZE_LIST_DETAIL_LANDSCAPE = "pane_size_list_detail_lanscape";
	public static final String PANE_SIZE_LIST_DETAIL_PORTRAIT = "pane_size_list_detail_portrait";
	public static final String PANE_SIZE_3_PANE_LEFT_LANDSCAPE = "pane_size_3_pane_left_landscape";
	public static final String PANE_SIZE_3_PANE_RIGHT_LANDSCAPE = "pane_size_3_pane_right_landscape";
	public static final String PANE_SIZE_3_PANE_TOP_PORTRAIT = "pane_size_3_pane_top_portrait";
	public static final String PANE_SIZE_3_PANE_BOTTOM_PORTRAIT = "pane_size_3_pane_bottom_portrait";
	
	// Installation information received from our server.  These will be nonexistent in the 
	// shared preferences if not known.  All values are long ints.
	public static final String USER_ID = "user_id";
	public static final String DEVICE_ID = "device_id";
	public static final String INSTALL_ID = "install_id";
	
	// Licensing:
	public static final String IN_APP_STATUS = "android_wear_enabled";
	    // String.  Set to GooglePlayInterface.DP if an in-app purchase has occurred.
	public static final String IN_APP_RECHECK_TIME = "android_wear_time";
		// Long.  On or after this time, in ms, recheck the in-app purchase status
	public static final String UPGRADE_NOTIFICATION_TIME = "upgrade_notification_time2";
		// Long.  On or after this time, in ms, prompt the user to upgrade their license to
	    // an in-app purchase.  0 = never notify
		// 10/1/23: Changing the name to ensure the mandatory upgrade message displays.
	public static final String LAST_BETA_REMINDER = "last_beta_reminder";
	    // Long.  The last time, in ms, that the user was notified that they are using the beta.
	public static final String LICENSE_CHECK_FAILURES = "license_check_failures"; // Integer

	/** Flag indicating if the license from the external app was detected at least once. As of
	 * 9/2018, once a single successful license check occurs, the app remains unlocked for as long
	 * as it is installed. */
	public static final String SUCCESSFUL_EXTERNAL_LICENSE_CHECK = "sync_config_success";

	/** Flag indicating if location reminders have been blocked by the user's location settings. */
	public static final String LOCATION_REMINDERS_BLOCKED = "location_reminders_blocked";

	/** The last time in millis the user was prompted about the issue with their location settings. */
	public static final String LOCATION_SETTINGS_LAST_PROMPT = "location_settings_last_prompt";

	/** The date the last log was automatically uploaded.  A string date using the system's date
	 * format. */
	public static final String LAST_LOG_UPLOAD_DATE = "last_log_upload_date";

	/** The number of log files uploaded to our server. */
	public static final String NUM_LOG_UPLOADS = "num_uploads";

	/** Flag indicating that the user has changed the theme at least once. */
	public static final String THEME_CHANGED = "theme_changed";

	/** Standard preference key that specifies if GDPR applies. */
	public static final String IABTCF_gdprApplies = "IABTCF_gdprApplies";

	/** Standard preference key that specifies the encoded GDPR consent string. */
	public static final String IABTCF_TCString = "IABTCF_TCString";

	/** Preference key for managing consent for personal data. */
	public static final String PERSONAL_DATA_CONSENT = "personal_data_consent";

	/** Flag indicating if loading of Amazon ads should be disabled. */
	public static final String DISABLE_AMAZON_ADS = "disable_amazon_ads";

	/** Flag indicating if the user has rejected the permission to manage external
	 * storage. This is needed to write the backup file. */
	public static final String REJECTED_STORAGE_PERMISSION = "rejected_storage_permission";

	/** Flag indicating that the user has previously denied access to background location. */
	public static final String HAS_DENIED_BACKGROUND_LOCATION = "has_denied_background_location";

	/** The row ID in Android's MediaStore corresonding to the most recent backup file. */
	public static final String BACKUP_ROW_ID = "backup_row_id";

	/** The time in millis in which the user was last prompted to allow permission for exact
	 * alarms/reminders. */
	public static final String EXACT_ALARM_REQUEST_TIME = "exact_alarm_request_time";

	/** The time to show the shutdown popup. */
	public static final String SHUTDOWN_SHOW_TIME = "shutdown_show_time";

	/** Flag indicating if the shutdown message popup is permanently blocked by the user. */
	public static final String SHUTDOWN_BLOCKED = "shutdown_blocked";

	/** Flag indicating if the shutdown message popup has been permanently dismissed. */
	public static final String SHUTDOWN_COMPLETE = "shutdown_completed";
}
