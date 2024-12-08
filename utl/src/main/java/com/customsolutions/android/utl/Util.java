package com.customsolutions.android.utl;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.BadParcelableException;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.speech.SpeechRecognizer;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.AdapterStatus;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.api.services.tasks.TasksScopes;
import com.google.firebase.FirebaseApp;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import net.lingala.zip4j.ZipFile;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import okhttp3.OkHttpClient;

// This class provides various utility functions.  It includes only static members and 
// functions, and is never instantiated.

public class Util
{
    private static final String TAG = "Util";

    /** Set this to true to disable ads and make all add-ons free. */
    public static final boolean IS_FREE = true;

	// Location of backup files (within the SD Card or Media directory):
	public static final String BACKUP_DIR = "/backup";
	public static final String DATABASE_BACKUP_DIR = "/database_files";
	public static final String PREFS_BACKUP_FILE = "/preferences";
	public static final String BACKUP_ZIP_FILE = "Ultimate To-Do List Backup.zip";

	/** An array containing the main theme styles based on the user's preferences. The first
     * level of the array is the theme index. The second is the font size. */
	public static final int[][] MAIN_THEMES = new int[][] {
	    new int[] {
	        R.style.UtlStylePacific_SmallFont,
            R.style.UtlStylePacific_MediumFont,
            R.style.UtlStylePacific_LargeFont
        },
        new int[] {
            R.style.UtlStyleBoston_SmallFont,
            R.style.UtlStyleBoston_MediumFont,
            R.style.UtlStyleBoston_LargeFont
	    },
        new int[] {
            R.style.UtlStyleClassic_SmallFont,
            R.style.UtlStyleClassic_MediumFont,
            R.style.UtlStyleClassic_LargeFont
        },
        new int[] {
            R.style.UtlStyleDark_SmallFont,
            R.style.UtlStyleDark_MediumFont,
            R.style.UtlStyleDark_LargeFont
        },
        new int[] {
            R.style.UtlStyleDarker_SmallFont,
            R.style.UtlStyleDarker_MediumFont,
            R.style.UtlStyleDarker_LargeFont
        },
        new int[] {
            R.style.UtlStyleDarkest_SmallFont,
            R.style.UtlStyleDarkest_MediumFont,
            R.style.UtlStyleDarkest_LargeFont
        }
    };

    /** An array containing the popup window theme styles based on the user's preferences. The first
     * level of the array is the theme index. The second is the font size. */
    public static final int[][] POPUP_THEMES = new int[][] {
        new int[] {
            R.style.UtlStylePacific_Popup_SmallFont,
            R.style.UtlStylePacific_Popup_MediumFont,
            R.style.UtlStylePacific_Popup_LargeFont
        },
        new int[] {
            R.style.UtlStyleBoston_Popup_SmallFont,
            R.style.UtlStyleBoston_Popup_MediumFont,
            R.style.UtlStyleBoston_Popup_LargeFont
        },
        new int[] {
            R.style.UtlStyleClassic_Popup_SmallFont,
            R.style.UtlStyleClassic_Popup_MediumFont,
            R.style.UtlStyleClassic_Popup_LargeFont
        },
        new int[] {
            R.style.UtlStyleDark_Popup_SmallFont,
            R.style.UtlStyleDark_Popup_MediumFont,
            R.style.UtlStyleDark_Popup_LargeFont

        },
        new int[] {
            R.style.UtlStyleDarker_Popup_SmallFont,
            R.style.UtlStyleDarker_Popup_MediumFont,
            R.style.UtlStyleDarker_Popup_LargeFont
        },
        new int[] {
            R.style.UtlStyleDarkest_Popup_SmallFont,
            R.style.UtlStyleDarkest_Popup_MediumFont,
            R.style.UtlStyleDarkest_Popup_LargeFont
        }
    };

	/** This is a prefix used in the task reminder notification channel. */
	public static final String NOTIF_CHANNEL_REMINDER_PREFIX = "reminders";

	/** The notification channel for miscellaneous low priority notifications. */
	public static final String NOTIF_CHANNEL_MISC = "utl_miscellaneous";

	// This array maps status codes to strings.  This matches Toodledo's interface.
    public static final String[] statuses = {
        "None",
        "Next Action",
        "Active",
        "Planning",
        "Delegated",
        "Waiting",
        "Hold",
        "Postponed",
        "Someday",
        "Canceled",
        "Reference"
    };
    
    // This array maps priority codes to strings.  This is offset by 2 from Toodledo.
    public static final String[] priorities = {
        "None",
        "Negative",
        "Low",
        "Medium",
        "High",
        "Top"
    };
        
    // Maximum length of various strings:
    public static final int MAX_FOLDER_TITLE_LENGTH = 32;
    public static final int MAX_CONTEXT_TITLE_LENGTH = 32;
    public static final int MAX_GOAL_TITLE_LENGTH = 255;
    public static final int MAX_LOCATION_TITLE_LENGTH = 32;
    public static final int MAX_LOCATION_DESCRIPTION_LENGTH = 255;
    public static final int MAX_TASK_TITLE_LENGTH = 255;
    public static final int MAX_TASK_NOTE_LENGTH = 32000;
    public static final int MAX_NOTE_TITLE_LENGTH = 255;
    public static final int MAX_TAG_STRING_LENGTH = 250;
    public static final int MAX_NOTE_BODY_LENGTH = 62000;
    
    // Distance measures.  Must match arrays in the res directory:
    public static final int MILES = 0;
    public static final int KILOS = 1;
    
    // Policies for synchronization.  When syncing with Google, we need to sync as little
    // as possible due to their limits:
    public static final int SCHEDULED_SYNC = 0;
    public static final int MINIMAL_SYNC = 1;
    
    // Values for time preference.  These must match the array in arrays.xml:
 	static public final int TIME_PREF_SYSTEM = 0;
 	static public final int TIME_PREF_12H = 1;
 	static public final int TIME_PREF_24H = 2;
 	        
    // Constants for the location provider preference:
    public static final int LOCATION_PROVIDER_NETWORK_ONLY = 0;  // Must match arrays.xml
    public static final int LOCATION_PROVIDER_GPS_ONLY = 1;
    public static final int LOCATION_PROVIDER_BOTH = 2;
    public static final int DEFAULT_LOCATION_PROVIDERS = LOCATION_PROVIDER_BOTH;
    
    // Default location check interval (minutes):
    public static final int DEFAULT_LOCATION_CHECK_INTERVAL = 8;
    
    // Constants for the split-screen (SS) options:
    public static final int SS_NONE = 0;
    public static final int SS_2_PANE_NAV_LIST = 1; // Nav drawer and task list
    public static final int SS_2_PANE_LIST_DETAILS = 2;  // Task list and details
    public static final int SS_3_PANE = 3;  // All 3 panes
    
    // Constants for the date format.  These MUST match the array defined in arrays.xml
    public static final int DATE_FORMAT_FROM_SYS = 0;
    public static final int DATE_FORMAT_DAY_FIRST = 1;
    public static final int DATE_FORMAT_MONTH_FIRST = 2;
    public static final int DATE_FORMAT_YEAR_FIRST = 3;
    
    // The default sync interval, and the minimum sync intervals for Google and Toodledo:
    public static final int DEFAULT_SYNC_INTERVAL = 60;
    public static final int MIN_SYNC_INTERVAL_TOODLEDO = 15;
    public static final int MIN_SYNC_INTERVAL_GOOGLE = 60;

    /** The Job ID for automatic background synchronization. */
    public static final int JOB_ID_AUTO_SYNC = 1535381658;

    /** The Job ID for one-time sync commands sent to synchronizer. */
    public static final int JOB_ID_SYNC_COMMANDS = 1000;

    /** Request code used when prompting the user to fix location settings issues. */
    public static final int REQUEST_CODE_LOCATION_SETTINGS = 831;

    // These preferences are NOT restored when restoring from a backup file.  These are 
    // device dependent and bad values can cause problems.  For example, when restoring a backup
    // made on a tablet into a smartphone.
    public static final String[] PREFS_NOT_RESTORED = new String[] {
    	PrefNames.PANE_SIZE_NAV_LIST_LANDSCAPE,
    	PrefNames.PANE_SIZE_NAV_LIST_PORTRAIT,
    	PrefNames.PANE_SIZE_LIST_DETAIL_LANDSCAPE,
    	PrefNames.PANE_SIZE_LIST_DETAIL_PORTRAIT,
    	PrefNames.PANE_SIZE_3_PANE_LEFT_LANDSCAPE,
    	PrefNames.PANE_SIZE_3_PANE_RIGHT_LANDSCAPE,
    	PrefNames.PANE_SIZE_3_PANE_TOP_PORTRAIT,
    	PrefNames.PANE_SIZE_3_PANE_BOTTOM_PORTRAIT,
    	PrefNames.SS_LANDSCAPE,
    	PrefNames.SS_PORTRAIT,
        PrefNames.SUCCESSFUL_EXTERNAL_LICENSE_CHECK,
        PrefNames.BACKUP_ROW_ID
    };

    /** Code returned in onActivityResult() when the user picks a backup file to restore. */
    public static final int BACKUP_FILE_PICKER_CODE = 666;

    /** An executor to use for AsyncTask instances that is guaranteed to actually start the task. */
    public static ThreadPoolExecutor UTL_EXECUTOR;

	// Set to true if this is the Google version:
	public static boolean IS_GOOGLE = false;
	
	// Set to true if this is the Amazon version:
	public static boolean IS_AMAZON = false;
	
	// Set to true to allow the sort_order field to be displayed in a task list.  This is only
    // used for debugging.  The user should not need to see it.
    public static boolean ENABLE_SORT_ORDER = false;

    // A context, used to access global data:
    public static Context context;
    
    // Provides an interface into the database:
    public static DatabaseHelper dbHelper;

    // A semaphore for managing access to the database by background threads.  This is needed
    // because database backups require the database to be closed.
    public static Semaphore _semaphore;
    
    // Has initialization been completed:
    private static boolean initDone = false;
    
    // Global user settings:
    public static SharedPreferences settings;
    
    // Maps to convert from database fields to descriptions, and vice versa:
    public static HashMap<String,String> descriptionToDbField;
    public static HashMap<String,String> dbFieldToDescription;
    
    // Number of milliseconds in a day:
    public static final long ONE_DAY_MS = 24*60*60*1000;
    
    // The date format in use:
    public static int currentDateFormat;
    
    // The time format in use.  One of the 12 or 24 hour constants in DateTimePrefs.java:
    public static int currentTimeFormat;
    
    // Android IDs that belong to this set are not valid.
    public static HashSet<String> invalidAndroidIDs;
    
    // This hash maps database fields to strings containing possible sort orders. If
    // a field does not appear here, then it only has one sort order.
    public static HashMap<String,String[]> sortOrderMap;
    
    // The name associated with the preference settings:
    public static final String PREFS_NAME = "UTL_Prefs";
    
    // A hash that maps attribute IDs to resource IDs (for colors, drawables, etc).  This is 
    // used to reduce lookups from the system and improve performance.
    public static HashMap<Integer,Integer> resourceIdHash;
    
    // These values are used to help obfuscate the code:
    public static String LICENSE_CONFIG = "license_config";
    public static String LICENSE_APP = "license_app";
    public static String PURCHASE = "purchase";
    public static long LICENSE_RECEIVER_SECRET_CODE = 3742894239051739L;

    /** At this date and time (5am 8/30/2019 GMT), we can no longer create sub-sub-tasks in
     * Google Tasks. */
    public static final long GOOGLE_SUB_SUB_TASKS_EXPIRY = 1567141200000l;
    // public static final long GOOGLE_SUB_SUB_TASKS_EXPIRY = 1563598800000l;

    /** A standard HTTP client, that will work for most use cases in the app. */
    private static OkHttpClient _httpClient;

    /** This broadcast receiver is used for detecting changes in the permission to set exact alarms. */
    private static BroadcastReceiver _alarmPermReceiver = null;

    // Main initialization function for this app.  This should be called from any 
    // activity that may be an entry point.
	@SuppressLint("NewApi")
	static void appInit(Context c)
    {
        if (!initDone)
        {
        	// Create the semaphore for managing database access:
        	_semaphore = new Semaphore(1,true);
        	
            context = c;
            Util.DatabaseInit(c);
            Log.init(c);
            initDone = true;

            UTL_EXECUTOR = (ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR;
            UTL_EXECUTOR.setCorePoolSize(UTL_EXECUTOR.getCorePoolSize() * 10);
            UTL_EXECUTOR.setMaximumPoolSize(Integer.MAX_VALUE);
            UTL_EXECUTOR.setRejectedExecutionHandler(new RejectedExecutionHandler()
            {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor)
                {
                    log("A new task was rejected. Active Count: " + executor.
                        getActiveCount() + "; Completed Count: " + executor.getCompletedTaskCount() +
                        "; Largest Pool Size: " + executor.getLargestPoolSize() + "; Pool Size: " +
                        executor.getPoolSize() + "; Queue Capacity: " + executor.getQueue().
                        remainingCapacity());
                }
            });

        	// We need to explicitly enable multi-threaded access to the database:
        	SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.enableWriteAheadLogging();
            Util.log("Write Ahead Logging enabled for database.");

            // Initialize stats collection:
            Stats.init(context);

            // The following android IDs are not valid:
            invalidAndroidIDs = new HashSet<String>();
            invalidAndroidIDs.add("dead00beef");
            
            // Make sure all essential tables exist in the DB:
            AccountsDbAdapter accountsDB = new AccountsDbAdapter();
            TasksDbAdapter taskDB = new TasksDbAdapter();
            FoldersDbAdapter foldersDB = new FoldersDbAdapter();
            ContextsDbAdapter contextsDB = new ContextsDbAdapter();
            GoalsDbAdapter goalsDB = new GoalsDbAdapter();
            TagsDbAdapter tagsDB = new TagsDbAdapter();
            ViewsDbAdapter viewsDB = new ViewsDbAdapter();
            ViewRulesDbAdapter rulesDB = new ViewRulesDbAdapter();
            CurrentTagsDbAdapter tags2DB = new CurrentTagsDbAdapter();
            NotesDbAdapter notesDB = new NotesDbAdapter();
            PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
            LocationsDbAdapter locationsDB = new LocationsDbAdapter();
            CollaboratorsDbAdapter collDB = new CollaboratorsDbAdapter();
            PendingReassignmentsDbAdapter pdb = new PendingReassignmentsDbAdapter();
            
        	// Determine which app store we got the app from.  This is based on the Flavor class,
            // which changes for each Flavor defined in Build.gradle.
            IS_GOOGLE = false;
            IS_AMAZON = false;
            if (FlavorInfo.FLAVOR==0)
                IS_GOOGLE = true;
            else if (FlavorInfo.FLAVOR==1)
                IS_AMAZON = true;

            // Catch any uncaught exceptions:
        	Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(c));  
        	
            // Get the preferences (if they exist):
            settings = context.getSharedPreferences(PREFS_NAME,0);
            if (!settings.contains(PrefNames.HOME_TIME_ZONE))
            {
                // If the home time zone is not defined, then this is the first time we 
            	// have run.  Set up some default preferences:
                setDefaultPreferenceValues(context);

            	// Set some administrative preferences that vary depending on whether this is the first
            	// run.
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean(PrefNames.LOCATION_DOWNLOAD_NEEDED, false);
                editor.putBoolean(PrefNames.COLLABORATION_DOWNLOAD_NEEDED, false);
                editor.commit();
            }
            else
            {
            	// This is not the first time we have run.
            	
                // If we haven't yet converted preferences to version 3, then do so:
                if (!settings.contains(PrefNames.CONVERSION_TO_V3_COMPLETED))
                	Util.convertPrefsToVersion3(settings);
                
            	// Set default values for preferences.  This must be always be called in case the app has 
            	// been upgraded with new preference.
            	setDefaultPreferenceValues(context);

            	if (!settings.contains(PrefNames.LOCATION_DOWNLOAD_NEEDED))
            	{
            		// This means that we have upgraded from a version that does 
            		// not support locations to one that does.  Record that we need
            		// to download location info from Toodledo:
            		Util.updatePref(PrefNames.LOCATION_DOWNLOAD_NEEDED, true);
            	}            	
            	
            	if (!settings.contains(PrefNames.COLLABORATION_DOWNLOAD_NEEDED))
            	{
            		// This means that we have upgraded from a version that does not support
            		// collaboration to one that does.  Record that we need to download
            		// collaboration information from Toodledo:
            		Util.updatePref(PrefNames.COLLABORATION_DOWNLOAD_NEEDED, true);
            	}
            }

            // Make sure we have a notification channels set up:
            setupReminderNotificationChannel(context,settings,false);
            setupMiscNotificationChannel(context);

            if (dbHelper.isManualSortInitNeeded)
            {
                // Set initial values of the sort_order field based on Google's position field:
                dbHelper.isManualSortInitNeeded = false;
                distrbuteSortOrderOnUpgrade();
            }

            log("UTL Initialization: Home Time Zone: "+Util.settings.getString("home_time_zone",
            	"unknown"));

            log("Flavor: "+FlavorInfo.FLAVOR);

            try
            {
                Bundle metadata = c.getPackageManager().getApplicationInfo(c.getPackageName(),
                    PackageManager.GET_META_DATA).metaData;
                log("com.google.android.gms.version: "+metadata.getInt(
                    "com.google.android.gms.version"));
            }
            catch (Exception e)
            {
                Util.handleException(TAG,c,e);
            }

            // Set up the HashMaps which map database fields to/from their descriptions:
            dbFieldToDescription = new HashMap<String,String>();
            dbFieldToDescription.put("title", Util.getString(R.string.Title));
            dbFieldToDescription.put("completed", Util.getString(R.string.Completed));
            dbFieldToDescription.put("start_date", Util.getString(R.string.Start_Date));
            dbFieldToDescription.put("due_date", Util.getString(R.string.Due_Date));
            dbFieldToDescription.put("reminder", Util.getString(R.string.Reminder));
            dbFieldToDescription.put("priority", Util.getString(R.string.Priority));
            dbFieldToDescription.put("star", Util.getString(R.string.Star));
            dbFieldToDescription.put("status", Util.getString(R.string.Status));
            dbFieldToDescription.put("folder_id", Util.getString(R.string.Folder));
            dbFieldToDescription.put("context_id", Util.getString(R.string.Context));
            dbFieldToDescription.put("goal_id", Util.getString(R.string.Goal));
            dbFieldToDescription.put("location_id", Util.getString(R.string.Location));
            dbFieldToDescription.put("note", Util.getString(R.string.Note));
            dbFieldToDescription.put("length", Util.getString(R.string.Expected_Length));
            dbFieldToDescription.put("timer", Util.getString(R.string.Actual_Length));
            dbFieldToDescription.put("timer_start_time", Util.getString(R.string.Timer_Status));
            dbFieldToDescription.put("importance", Util.getString(R.string.Importance));
            dbFieldToDescription.put("mod_date", Util.getString(R.string.Mod_Date));
            dbFieldToDescription.put("completion_date", Util.getString(R.string.CompletionDate));
            dbFieldToDescription.put("account_id", Util.getString(R.string.Account));  
            dbFieldToDescription.put("tags.name", Util.getString(R.string.Tags));
            dbFieldToDescription.put("contact_lookup_key", Util.getString(R.string.Contact));
            dbFieldToDescription.put("is_joint", Util.getString(R.string.Is_Shared));
            dbFieldToDescription.put("owner_remote_id", Util.getString(R.string.Owner));
            dbFieldToDescription.put("added_by", Util.getString(R.string.Assignor));
            descriptionToDbField = new HashMap<String,String>();
            descriptionToDbField.put(Util.getString(R.string.Title),"title");
            descriptionToDbField.put(Util.getString(R.string.Completed),"completed");
            descriptionToDbField.put(Util.getString(R.string.Start_Date),"start_date");
            descriptionToDbField.put(Util.getString(R.string.Reminder),"reminder");
            descriptionToDbField.put(Util.getString(R.string.Due_Date),"due_date");
            descriptionToDbField.put(Util.getString(R.string.Priority),"priority");
            descriptionToDbField.put(Util.getString(R.string.Star),"star");
            descriptionToDbField.put(Util.getString(R.string.Status),"status");
            descriptionToDbField.put(Util.getString(R.string.Folder),"folder_id");
            descriptionToDbField.put(Util.getString(R.string.Context),"context_id");
            descriptionToDbField.put(Util.getString(R.string.Goal),"goal_id");
            descriptionToDbField.put(Util.getString(R.string.Location),"location_id");
            descriptionToDbField.put(Util.getString(R.string.Note),"note");
            descriptionToDbField.put(Util.getString(R.string.Expected_Length),"length");
            descriptionToDbField.put(Util.getString(R.string.Actual_Length),"timer");
            descriptionToDbField.put(Util.getString(R.string.Timer_Status),"timer_start_time");
            descriptionToDbField.put(Util.getString(R.string.Importance),"importance");
            descriptionToDbField.put(Util.getString(R.string.Mod_Date),"mod_date");
            descriptionToDbField.put(Util.getString(R.string.CompletionDate),"completion_date");
            descriptionToDbField.put(Util.getString(R.string.Account),"account_id");
            descriptionToDbField.put(Util.getString(R.string.Tags),"tags.name");
            descriptionToDbField.put(Util.getString(R.string.Contact), "contact_lookup_key");
            descriptionToDbField.put(Util.getString(R.string.Is_Shared), "is_joint");
            descriptionToDbField.put(Util.getString(R.string.Owner), "owner_remote_id");
            descriptionToDbField.put(Util.getString(R.string.Assignor), "added_by");

        	// If the date format is set to the system format, then read
        	// it from the system:
        	if (settings.getInt(PrefNames.DATE_FORMAT,DATE_FORMAT_FROM_SYS)==DATE_FORMAT_FROM_SYS)
        	{
        		updateDateFormatFromSystem(c);
        	}
        	else
        	{
        		// The date format is defined explicitly in a user preference:
        		currentDateFormat = settings.getInt(PrefNames.DATE_FORMAT,DATE_FORMAT_MONTH_FIRST);
        		log("Setting date format to "+currentDateFormat);
        	}   
        	
        	// If the user is getting the time format from the system, then read it in:
        	if (settings.getInt(PrefNames.TIME_FORMAT, Util.TIME_PREF_12H)==
        		Util.TIME_PREF_SYSTEM)
        	{
        		updateTimeFormatFromSystem(c);
        	}
        	else
        	{
        		// The time format is explicitly defined in the user's preference:
        		currentTimeFormat = settings.getInt(PrefNames.TIME_FORMAT, Util.
        			TIME_PREF_12H);
        	}
        	
        	// Set up the hash containing the possible sort orders for various fields:
        	String[] dateSortOrder = c.getResources().getStringArray(R.array.sort_order_date);
        	String[] textSortOrder = c.getResources().getStringArray(R.array.sort_order_text);
        	String[] prioritySortOrder = c.getResources().getStringArray(R.array.sort_order_priority);
        	String[] starSortOrder = c.getResources().getStringArray(R.array.sort_order_star);
        	String[] numericSortOrder = c.getResources().getStringArray(R.array.sort_order_numeric);
        	String[] completedSortOrder = c.getResources().getStringArray(R.array.sort_order_completed);
        	String[] sharedSortOrder = c.getResources().getStringArray(R.array.sort_order_shared);
        	sortOrderMap = new HashMap<String,String[]>();
        	sortOrderMap.put("tasks.title", textSortOrder);
        	sortOrderMap.put("tasks.start_date", dateSortOrder);
        	sortOrderMap.put("tasks.due_date", dateSortOrder);
        	sortOrderMap.put("tasks.reminder", dateSortOrder);
        	sortOrderMap.put("tasks.priority", prioritySortOrder);
        	sortOrderMap.put("tasks.star", starSortOrder);
        	sortOrderMap.put("folder", textSortOrder);
        	sortOrderMap.put("context", textSortOrder);
        	sortOrderMap.put("tag_name", textSortOrder);
        	sortOrderMap.put("goal", textSortOrder);
        	sortOrderMap.put("location", textSortOrder);
        	sortOrderMap.put("tasks.note", textSortOrder);
        	sortOrderMap.put("tasks.length", numericSortOrder);
        	sortOrderMap.put("tasks.timer", numericSortOrder);
        	sortOrderMap.put("tasks.mod_date", dateSortOrder);
        	sortOrderMap.put("tasks.completed", completedSortOrder);
        	sortOrderMap.put("tasks.completion_date", dateSortOrder);
        	sortOrderMap.put("account", textSortOrder);
        	sortOrderMap.put("importance", prioritySortOrder);
        	sortOrderMap.put("tasks.is_joint", sharedSortOrder);
        	sortOrderMap.put("owner_name", textSortOrder);
        	sortOrderMap.put("assignor_name", textSortOrder);

            if (!Util.IS_FREE)
            {
                // Initialize AdMob:
                try
                {
                    if (BuildConfig.DEBUG)
                    {
                        RequestConfiguration config = new RequestConfiguration.Builder()
                            .setTestDeviceIds((Arrays.asList(Util.getAdmobTestID(c)))).build();
                        MobileAds.setRequestConfiguration(config);
                    }
                    MobileAds.initialize(c, (InitializationStatus stat) -> {
                        Log.v(TAG, "AdMob Initialized.");
                        Map<String, AdapterStatus> statusMap = stat.getAdapterStatusMap();
                        if (statusMap == null)
                        {
                            Log.e(Log.className(c), "No Admob Status", "Admob status is null.");
                            return;
                        }
                        for (String adapter : statusMap.keySet())
                        {
                            AdapterStatus adapterStatus = statusMap.get(adapter);
                            Log.v(Log.className(c), "Admob Adapter " + adapter + " status: " + adapterStatus.
                                getInitializationState() + " / " + adapterStatus.getDescription());
                        }
                    });
                }
                catch (Exception e)
                {
                    // 6/11/2020: Ignoring an exception that occurs on some Huawei devices.
                    Log.e("Util", "Exception Initializing AdMob", "Got this exception when " +
                        "initializing AdMob.", e);
                }
            }

        	// Make sure the current sync interval is not too low:
        	int minSyncInterval = getMinSyncInterval(context);
        	if (settings.getInt(PrefNames.SYNC_INTERVAL, 60)<minSyncInterval)
        	{
        		updatePref(PrefNames.SYNC_INTERVAL,minSyncInterval);
        	}
        	
            // If the user is upgrading from a beta release, now is the time to move them to
            // the standard free trial mode.
            PurchaseManager pm = new PurchaseManager(context);
            pm.transitionFromBetaToTrial();

            // Start the service that connects to Android Wear if needed:
            if (Util.canUseAndroidWear() && pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
            {
                try
                {
                    context.startService(new Intent(context, WearService.class));
                }
                catch (IllegalStateException e)
                {
                    // This will fail on Oreo or later if the app is initializing in the
                    // background.
                    log("WARNING: Exception when starting WearService. "+e.getClass().getName()+
                        " / "+e.getMessage());
                }
            }


        }
    }
    
	// Set default values for all preferences that are not already set:
	public static void setDefaultPreferenceValues(Context c)
	{
		SharedPreferences settings = c.getSharedPreferences(Util.PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		
		// Sync Options:
		if (!settings.contains(PrefNames.AUTO_SYNC))
			editor.putBoolean(PrefNames.AUTO_SYNC, true);
		if (!settings.contains(PrefNames.SYNC_INTERVAL))
			editor.putInt(PrefNames.SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL);
		if (settings.getInt(PrefNames.SYNC_INTERVAL, 60)==0)
		{
			// May occur during transition to version 3.
			editor.putInt(PrefNames.SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL);
			editor.putBoolean(PrefNames.AUTO_SYNC, false);
		}
		if (!settings.contains(PrefNames.INSTANT_UPLOAD))
			editor.putBoolean(PrefNames.INSTANT_UPLOAD, true);
		if (!settings.contains(PrefNames.NO_SYNC_WARNING))
			editor.putBoolean(PrefNames.NO_SYNC_WARNING, true);
		
		// Display Options:
		if (!settings.contains(PrefNames.FONT_SIZE))
			editor.putString(PrefNames.FONT_SIZE, "medium");
		if (!settings.contains(PrefNames.THEME))
			editor.putInt(PrefNames.THEME, 0);  // Pacific
		setDefaultSplitScreenPrefs(c);
		
		// Reminder options:
		if (!settings.contains(PrefNames.RINGTONE))
			editor.putString(PrefNames.RINGTONE, "Default");
		if (!settings.contains(PrefNames.VIBE_PATTERN))
			editor.putString(PrefNames.VIBE_PATTERN, "0,200,100,1000,100,200,100,1000");
		if (!settings.contains(PrefNames.REMINDER_LIGHT))
			editor.putInt(PrefNames.REMINDER_LIGHT, 1);
		if (!settings.contains(PrefNames.NAG_INTERVAL))
			editor.putInt(PrefNames.NAG_INTERVAL, 15);
		if (!settings.contains(PrefNames.NOTIFICATION_PRIORITY))
			editor.putInt(PrefNames.NOTIFICATION_PRIORITY, 3);
		if (!settings.contains(PrefNames.USE_ONGOING_NOTIFICATIONS))
			editor.putBoolean(PrefNames.USE_ONGOING_NOTIFICATIONS, false);
		
		// Fields/Functions Used.
		String[] enabledFunctions = {
			PrefNames.REMINDER_ENABLED,
			PrefNames.FOLDERS_ENABLED,
            PrefNames.START_DATE_ENABLED,
            PrefNames.DUE_DATE_ENABLED,
            PrefNames.REPEAT_ENABLED,
            PrefNames.PRIORITY_ENABLED,
			PrefNames.STAR_ENABLED,
			PrefNames.SUBTASKS_ENABLED,
			PrefNames.NOTE_FOLDERS_ENABLED,
		};
		String[] disabledFunctions = {
            PrefNames.COLLABORATORS_ENABLED,
            PrefNames.CONTEXTS_ENABLED,
            PrefNames.GOALS_ENABLED,
            PrefNames.LOCATIONS_ENABLED,
            PrefNames.DUE_TIME_ENABLED,
            PrefNames.START_TIME_ENABLED,
            PrefNames.CALENDAR_ENABLED,
            PrefNames.TAGS_ENABLED,
            PrefNames.LENGTH_ENABLED,
            PrefNames.TIMER_ENABLED,
            PrefNames.STATUS_ENABLED,
            PrefNames.CONTACTS_ENABLED
        };
		for (int i=0; i<enabledFunctions.length; i++)
		{
			if (!settings.contains(enabledFunctions[i]))
				editor.putBoolean(enabledFunctions[i], true);
		}
        for (int i=0; i<disabledFunctions.length; i++)
        {
            if (!settings.contains(disabledFunctions[i]))
                editor.putBoolean(disabledFunctions[i], false);
        }
		
		// New Task Defaults:
		if (!settings.contains(PrefNames.DEFAULT_ADD_TO_CAL))
			editor.putBoolean(PrefNames.DEFAULT_ADD_TO_CAL, false);
		if (!settings.contains(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL))
			editor.putBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false);
		if (!settings.contains(PrefNames.DEFAULT_FOLDER))
			editor.putLong(PrefNames.DEFAULT_FOLDER, 0);
		if (!settings.contains(PrefNames.DEFAULT_CONTEXT))
			editor.putLong(PrefNames.DEFAULT_CONTEXT, 0);
		if (!settings.contains(PrefNames.DEFAULT_GOAL))
			editor.putLong(PrefNames.DEFAULT_GOAL, 0);
		if (!settings.contains(PrefNames.DEFAULT_LOCATION))
			editor.putLong(PrefNames.DEFAULT_LOCATION, 0);
		if (!settings.contains(PrefNames.DEFAULT_ACCOUNT))
			editor.putLong(PrefNames.DEFAULT_ACCOUNT, 0);
		if (!settings.contains(PrefNames.DEFAULT_TAGS))
			editor.putString(PrefNames.DEFAULT_TAGS, "");
		if (!settings.contains(PrefNames.DEFAULT_START_DATE))
			editor.putString(PrefNames.DEFAULT_START_DATE, "today");
		if (!settings.contains(PrefNames.DEFAULT_DUE_DATE))
			editor.putString(PrefNames.DEFAULT_DUE_DATE, "tomorrow");
		if (!settings.contains(PrefNames.DEFAULT_PRIORITY))
			editor.putInt(PrefNames.DEFAULT_PRIORITY, 4);
		if (!settings.contains(PrefNames.DEFAULT_STATUS))
			editor.putInt(PrefNames.DEFAULT_STATUS, 0);
		
		// Voice Mode: New Task Defaults:
		if (!settings.contains(PrefNames.VM_DEFAULT_ADD_TO_CAL))
			editor.putBoolean(PrefNames.VM_DEFAULT_ADD_TO_CAL, false);
		if (!settings.contains(PrefNames.VM_DEFAULT_FOLDER))
			editor.putLong(PrefNames.VM_DEFAULT_FOLDER, 0);
		if (!settings.contains(PrefNames.VM_DEFAULT_CONTEXT))
			editor.putLong(PrefNames.VM_DEFAULT_CONTEXT, 0);
		if (!settings.contains(PrefNames.VM_DEFAULT_GOAL))
			editor.putLong(PrefNames.VM_DEFAULT_GOAL, 0);
		if (!settings.contains(PrefNames.VM_DEFAULT_LOCATION))
			editor.putLong(PrefNames.VM_DEFAULT_LOCATION, 0);
		if (!settings.contains(PrefNames.VM_DEFAULT_ACCOUNT))
			editor.putLong(PrefNames.VM_DEFAULT_ACCOUNT, 0);
		if (!settings.contains(PrefNames.VM_DEFAULT_TAGS))
			editor.putString(PrefNames.VM_DEFAULT_TAGS, "");
		if (!settings.contains(PrefNames.VM_DEFAULT_START_DATE))
			editor.putString(PrefNames.VM_DEFAULT_START_DATE, "today");
		if (!settings.contains(PrefNames.VM_DEFAULT_DUE_DATE))
			editor.putString(PrefNames.VM_DEFAULT_DUE_DATE, "tomorrow");
		if (!settings.contains(PrefNames.VM_DEFAULT_PRIORITY))
			editor.putInt(PrefNames.VM_DEFAULT_PRIORITY, 4);
		if (!settings.contains(PrefNames.VM_DEFAULT_STATUS))
			editor.putInt(PrefNames.VM_DEFAULT_STATUS, 0);

		// Voice Mode: Other Settings:
		if (!settings.contains(PrefNames.VM_KEEP_SCREEN_ON))
			editor.putBoolean(PrefNames.VM_KEEP_SCREEN_ON, true);
		if (!settings.contains(PrefNames.VM_LISTEN_IMMEDIATELY))
			editor.putBoolean(PrefNames.VM_LISTEN_IMMEDIATELY, false);
		if (!settings.contains(PrefNames.VM_TRIGGER_PHRASE))
			editor.putString(PrefNames.VM_TRIGGER_PHRASE, c.getString(R.string.trigger_phrase));
		if (!settings.contains(PrefNames.VM_TASKS_TO_READ_PER_PAGE))
			editor.putInt(PrefNames.VM_TASKS_TO_READ_PER_PAGE, 8);
		if (!settings.contains(PrefNames.VM_USE_BLUETOOTH))
		    editor.putBoolean(PrefNames.VM_USE_BLUETOOTH,false);
		
		// Date and Time:
		if (!settings.contains(PrefNames.WEEK_START))
			editor.putString(PrefNames.WEEK_START,"Sunday");
		if (!settings.contains(PrefNames.DATE_FORMAT))
			editor.putInt(PrefNames.DATE_FORMAT,Util.DATE_FORMAT_FROM_SYS);
		if (!settings.contains(PrefNames.TIME_FORMAT))
			editor.putInt(PrefNames.TIME_FORMAT,Util.TIME_PREF_SYSTEM);
		
		// Viewing and Editing:
		if (!settings.contains(PrefNames.OPEN_EDITOR_ON_TAP))
			editor.putBoolean(PrefNames.OPEN_EDITOR_ON_TAP, true);
		if (!settings.contains(PrefNames.OPEN_NOTE_EDITOR_ON_TAP))
			editor.putBoolean(PrefNames.OPEN_NOTE_EDITOR_ON_TAP, false);
		if (!settings.contains(PrefNames.HIDE_COMPLETED_SUBTASKS))
			editor.putBoolean(PrefNames.HIDE_COMPLETED_SUBTASKS, false);
		if (!settings.contains(PrefNames.UNDATED_SORT_ORDER))
			editor.putInt(PrefNames.UNDATED_SORT_ORDER, 1);
		
		// Location Tracking:
		if (!settings.contains(PrefNames.LOCATION_PROVIDERS))
			editor.putInt(PrefNames.LOCATION_PROVIDERS, LOCATION_PROVIDER_BOTH);
		if (!settings.contains(PrefNames.LOCATION_CHECK_INTERVAL))
			editor.putInt(PrefNames.LOCATION_CHECK_INTERVAL, DEFAULT_LOCATION_CHECK_INTERVAL);
		if (!settings.contains(PrefNames.LOC_ALARM_RADIUS))
			editor.putInt(PrefNames.LOC_ALARM_RADIUS, 100);
		if (!settings.contains(PrefNames.DISTANCE_MEASURE))
		{
			Locale currentLocale = Locale.getDefault();
            if (currentLocale.equals(Locale.US))
            	editor.putInt(PrefNames.DISTANCE_MEASURE, MILES);
            else
            	editor.putInt(PrefNames.DISTANCE_MEASURE, KILOS);
		}
		if (!settings.contains(PrefNames.LOCATION_REMINDERS_BLOCKED))
		    editor.putBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED,false);
		
		// Backup and Restore:
		if (!settings.contains(PrefNames.BACKUP_DAILY))
			editor.putBoolean(PrefNames.BACKUP_DAILY, false);

		// If the user is upgrading to version 4.1.0, we need to adjust the theme:
        if (!settings.contains(PrefNames.UPGRADED_TO_V410))
        {
            editor.putBoolean(PrefNames.UPGRADED_TO_V410,true);
            if (settings.contains(PrefNames.THEME))
            {
                // If the theme preference exists at this point, this is not the first run and we
                // need to update the theme.
                if (settings.getInt(PrefNames.THEME, 0) == 0)
                {
                    // The old light theme. The closest match is the "classic" theme.
                    editor.putInt(PrefNames.THEME, 2);
                }
                else
                {
                    // The old dark them.  The closest match is "Ultimate Dark".
                    editor.putInt(PrefNames.THEME, 5);
                }
            }
        }

		// Settings so far need applied, so they're available for the display options which will
        // be set below.
        editor.apply();

		// Miscellaneous:
		if (!settings.contains(PrefNames.LINKED_CALENDAR_ID))
			editor.putLong(PrefNames.LINKED_CALENDAR_ID, -1);
		if (!settings.contains(PrefNames.STARTUP_VIEW_ID))
		{
			ViewsDbAdapter vdb = new ViewsDbAdapter();
    		Cursor c2 = vdb.getView(ViewNames.HOTLIST,"");
    		if (c2.moveToFirst())
    		{
    			editor.putLong(PrefNames.STARTUP_VIEW_ID, Util.cLong(c2, "_id"));
    			editor.putString(PrefNames.STARTUP_VIEW_TITLE, getString(R.string.Hotlist));
    		}
    		else
    		{
    			editor.putLong(PrefNames.STARTUP_VIEW_ID, -1);
    			editor.putString(PrefNames.STARTUP_VIEW_TITLE, "");
    		}
    		c2.close();
		}
		if (!settings.contains(PrefNames.PURGE_COMPLETED))
			editor.putInt(PrefNames.PURGE_COMPLETED, 365);

        // Android Wear:
        if (!settings.contains(PrefNames.WEAR_DEFAULT_VIEW_ID))
        {
            ViewsDbAdapter vdb = new ViewsDbAdapter();
            Cursor c2 = vdb.getView(ViewNames.HOTLIST,"");
            if (c2.moveToFirst())
            {
                editor.putLong(PrefNames.WEAR_DEFAULT_VIEW_ID, Util.cLong(c2, "_id"));
            }
            else
            {
                editor.putLong(PrefNames.WEAR_DEFAULT_VIEW_ID, -1);
            }
            c2.close();
        }
        if (!settings.contains(PrefNames.WEAR_SHOW_DAILY_SUMMARY))
            editor.putBoolean(PrefNames.WEAR_SHOW_DAILY_SUMMARY,true);
        if (!settings.contains(PrefNames.WEAR_SUMMARY_TIME))
            editor.putLong(PrefNames.WEAR_SUMMARY_TIME,28800000);  // 8:00 am

		// Navigation Drawer:
		String[] navDrawerPrefs = new String[] {
			PrefNames.SHOW_ALL_TASKS,
			PrefNames.SHOW_HOTLIST,
			PrefNames.SHOW_DUE_TODAY_TOMORROW,
			PrefNames.SHOW_OVERDUE,
			PrefNames.SHOW_RECENTLY_COMPLETED
		};
		for (int i=0; i<navDrawerPrefs.length; i++)
		{
			if (!settings.contains(navDrawerPrefs[i]))
				editor.putBoolean(navDrawerPrefs[i], true);
		}
		if (!settings.contains(PrefNames.SHOW_ARCHIVED_FOLDERS))
			editor.putBoolean(PrefNames.SHOW_ARCHIVED_FOLDERS, false);
		if (!settings.contains(PrefNames.SHOW_ARCHIVED_GOALS))
			editor.putBoolean(PrefNames.SHOW_ARCHIVED_GOALS, false);
		
		// Administrative:
		boolean isFirstRun = false;
		if (!settings.contains(PrefNames.HOME_TIME_ZONE))
		{
			isFirstRun = true;
			editor.putString(PrefNames.HOME_TIME_ZONE, TimeZone.getDefault().getID());
		}
		if (!settings.contains(PrefNames.WHATS_NEW_VERSION_SEEN))
		{
			if (isFirstRun)
			{
				// We don't need to see a "what's new" message if the user is running the app for
				// the first time.  Setting the version seen to the current version will prevent the
				// message from appearing.
				editor.putInt(PrefNames.WHATS_NEW_VERSION_SEEN, context.getResources().getInteger(
					R.integer.whats_new_version));
			}
			else
			{
				// The user is upgrading from an earlier version that didn't support the whats new
				// message.  We definitely want to show it to him.
				editor.putInt(PrefNames.WHATS_NEW_VERSION_SEEN, 0);
			}
		}
		
		// Licensing:
		if (!settings.contains(PrefNames.UPGRADE_NOTIFICATION_TIME))
		{
			PurchaseManager pm = new PurchaseManager(c);
			if (pm.stat()==PurchaseManager.DONT_SHOW_ADS && pm.licenseType()==PurchaseManager.LIC_TYPE_EXT_APP)
			{
				// User has upgraded to version 3+ and has a license under the old system.
				editor.putLong(PrefNames.UPGRADE_NOTIFICATION_TIME, System.currentTimeMillis()+
					ONE_DAY_MS);
				Util.log("User will get license upgrade notice on "+Util.getDateTimeString(
					System.currentTimeMillis()+ONE_DAY_MS));
			}
		}

        // Survey:
        if (!settings.contains(PrefNames.SHUTDOWN_SHOW_TIME))
            editor.putLong(PrefNames.SHUTDOWN_SHOW_TIME,0);
        if (!settings.contains(PrefNames.SHUTDOWN_BLOCKED))
            editor.putBoolean(PrefNames.SHUTDOWN_BLOCKED,false);
        if (!settings.contains(PrefNames.SHUTDOWN_COMPLETE))
            editor.putBoolean(PrefNames.SHUTDOWN_COMPLETE,false);
		
		// Create a sample task?  This should be done on the first run.
		if (!settings.contains(PrefNames.CREATE_SAMPLE_TASK))
			editor.putBoolean(PrefNames.CREATE_SAMPLE_TASK, true);
		
		if (isFirstRun)
		{
			// We are running the app for the first time - and not upgrading from an older version.
			// In this case, we don't want to run any conversion of preferences to version 3.
			editor.putBoolean(PrefNames.CONVERSION_TO_V3_COMPLETED, true);
		}

        if (!settings.contains(PrefNames.LAST_DB_CLEANUP))
        {
            // This is either a new installation, or the user has just upgraded to version 3.3.
            // Set the date that the database was cleaned up and backed up to the current date.
            // When Util.refereshSystemAlarms() is called, this will ensure that the tasks run
            // within the next 24 hours.
            editor.putLong(PrefNames.LAST_DB_CLEANUP,System.currentTimeMillis());
        }
		
		editor.commit();
	}

	/** Set up the notification channel for miscellaneous, low priority, notifications. */
	public static void setupMiscNotificationChannel(Context c)
    {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
            return;

        NotificationManager notifManager = c.getSystemService(NotificationManager.class);
        if (notifManager==null)
            return;

        @SuppressLint("WrongConstant") NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_MISC,
            c.getString(R.string.Miscellaneous),NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null,null);
        notifManager.createNotificationChannel(channel);
    }

	/** Set up the notification channel for task reminders. Call this when the app is run for the
     * first time, and any time the user's reminder preferences change. */
	public static void setupReminderNotificationChannel(Context c, SharedPreferences prefs,
        boolean replaceExisting)
    {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
            return;

        NotificationManager notifManager = c.getSystemService(NotificationManager.class);
        if (notifManager==null)
        {
            log("WARNING: NotificationManager is null.");
            return;
        }

        // First, remove the existing reminder notification channel, if present.
        boolean existingFound = false;
        List<NotificationChannel> channels = notifManager.getNotificationChannels();
        if (channels!=null && channels.size()>0)
        {
            for (NotificationChannel channel : channels)
            {
                if (channel.getId().startsWith(NOTIF_CHANNEL_REMINDER_PREFIX))
                {
                    existingFound = true;
                    if (replaceExisting)
                    {
                        Util.log("Deleting notification channel " + channel.getId());
                        notifManager.deleteNotificationChannel(channel.getId());
                    }
                }
            }
        }

        if (!replaceExisting && existingFound)
        {
            // A notification channel was found, and we're not replacing an existing one, so we're
            // done.
            return;
        }

        // Create the new channel, based on the user's settings:

        // Get the channel ID. We have to use a new ID each time.
        Long secondTimestamp = System.currentTimeMillis()/1000;
        String channelID = NOTIF_CHANNEL_REMINDER_PREFIX+secondTimestamp;
        Util.log("Creating notification channel "+channelID);

        // Set the importance based on the user's preference:
        int importance;
        switch (prefs.getInt(PrefNames.NOTIFICATION_PRIORITY,2))
        {
            case 0:
                importance = NotificationManager.IMPORTANCE_MIN;
                break;

            case 1:
                importance = NotificationManager.IMPORTANCE_LOW;
                break;

            case 3:
                importance = NotificationManager.IMPORTANCE_HIGH;
                break;

            case 4:
                importance = NotificationManager.IMPORTANCE_MAX;
                break;

            default:
                importance = NotificationManager.IMPORTANCE_DEFAULT;
        }
        NotificationChannel channel = new NotificationChannel(channelID,
            c.getString(R.string.task_reminders),importance);

        // Vibration settings, based on the user's preference:
        String vibeString = prefs.getString(PrefNames.VIBE_PATTERN,"Default");
        if (vibeString.equals("Default"))
        {
            // We don't have access to the default vibration pattern as of Oreo, so I just need
            // to pick one.
            vibeString = "0,200,100,1000,100,200,100,1000";
        }
        String[] vibeItems = vibeString.split(",");
        long[] vibePattern = new long[vibeItems.length];
        for (int j=0; j<vibeItems.length; j++)
        {
            vibePattern[j] = Long.parseLong(vibeItems[j]);
        }
        channel.enableVibration(true);
        channel.setVibrationPattern(vibePattern);

        // Sound, based on the user's preference:
        String ringtoneString = settings.getString(PrefNames.RINGTONE, "Default");
        Uri ringtoneUri;
        if (ringtoneString.equals("Default"))
        {
            ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
        else
            ringtoneUri = Uri.parse(ringtoneString);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
            .build();
        channel.setSound(ringtoneUri,audioAttributes);

        // Light color from user's preferences:
        if (prefs.getInt(PrefNames.REMINDER_LIGHT,0)==0)
            channel.enableLights(false);
        else
        {
            channel.enableLights(true);
            int[] argbArray = c.getResources().getIntArray(R.array.argb_light_colors);
            channel.setLightColor(argbArray[prefs.getInt(PrefNames.REMINDER_LIGHT, 1)]);
        }

        // Set some other options and create the channel:
        channel.setShowBadge(true);
        channel.setDescription(c.getString(R.string.notification_category_for_reminders));
        notifManager.createNotificationChannel(channel);
    }

	// Set the default split-screen preferences:
	public static void setDefaultSplitScreenPrefs(Context c)
	{
		if (!settings.contains(PrefNames.SS_LANDSCAPE) || !settings.contains(PrefNames.SS_PORTRAIT))
		{
			if (c instanceof Activity)
			{
				SharedPreferences.Editor editor = settings.edit();
				Activity a = (Activity)c;
				DisplayMetrics metrics = new DisplayMetrics();
				a.getWindowManager().getDefaultDisplay().getMetrics(metrics);
				double width = metrics.widthPixels/metrics.xdpi;
		    	double height = metrics.heightPixels/metrics.ydpi; 
		    	double diagonalScreenSize = Math.hypot(width, height);
		    	if (!settings.contains(PrefNames.SS_LANDSCAPE))
		    	{
		    		if (diagonalScreenSize<6.25)
		    			editor.putInt(PrefNames.SS_LANDSCAPE, Util.SS_NONE);
		    		else if (diagonalScreenSize<8.5)
		    			editor.putInt(PrefNames.SS_LANDSCAPE, Util.SS_2_PANE_NAV_LIST);
		    		else
		    			editor.putInt(PrefNames.SS_LANDSCAPE, Util.SS_3_PANE);
		    	}
		    	if (!settings.contains(PrefNames.SS_PORTRAIT))
		    	{
		    		if (diagonalScreenSize<8.5)
		    			editor.putInt(PrefNames.SS_PORTRAIT, Util.SS_NONE);
		    		else
		    			editor.putInt(PrefNames.SS_PORTRAIT, Util.SS_2_PANE_NAV_LIST);
		    	}
		    	editor.commit();
			}
		}
	}
	
    // Write logging information to the database:
    public static void log (String text)
    {
        Log.v("UTL",text);
    }
    
    // Convenience function to write debug log output:
    public static void dlog(String text)
    {
    	log(text);
    }
    
    static void DatabaseInit(Context ctx) throws SQLException
    {
        dbHelper = new DatabaseHelper(ctx);
    }
    
    /** Acquire the semaphore used to temporarily block database access during a backup. 
     * @param logHeader - Text to log if the semaphore cannot be acquired.  Example: "Synchronizer" 
     * @param c - The current Context instance
     * @return - true if the semaphore was acquired.  Else false.
     * */
    static boolean acquireSemaphore(String logHeader, Context c)
    {
    	try
		{
			boolean isSuccessful = _semaphore.tryAcquire(20,TimeUnit.SECONDS);
			if (isSuccessful)
				return true;
			else
			{
				Util.log(logHeader+": WARNING: timed out trying to acquire semaphore. Proceeding anyway.");
			}
		} 
    	catch (InterruptedException e)
		{
			Util.log(logHeader+": WARNING: got InterruptedException while waiting on semaphore. "+
				e.getMessage());
		}
		return false;
    }
    
    // Make a string safe to insert or query to/from the database.  The adjusts all 
    // single quotes.
    static String makeSafeForDatabase(String input)
    {
        String newStr = input.replaceAll("'", "''");
        return newStr;
    }
    
    // Create a URL string of the form "key=value&key=value...".  The Bundle input must
    // consist of String items only.
    static String createURLString(Bundle b)
    {
        String[] keys = (String[])(b.keySet().toArray(new String[b.keySet().size()]));
        int i;
        String result = "";
        boolean isFirst = true;
        for (i=0; i<keys.length; i++)
        {
            try 
            {
                if (!isFirst)
                {
                    result += "&";
                }
                result += keys[i]+"="+URLEncoder.encode(b.getString(keys[i]), "UTF-8");
                isFirst = false;
            } 
            catch (UnsupportedEncodingException e) 
            {
                // This won't happen
            }
        }
        return result;
    }
    
    // Decode a URL string of the form "key=value&key=value...".  The returned Bundle
    // contains string items only.
    static Bundle parseURLString(String input)
    {
        String[] items = input.split("&");
        int i;
        Bundle b = new Bundle();
        for (i=0; i<items.length; i++)
        {
            String[] keyValue = items[i].split("=");
            try 
            {
                b.putString(keyValue[0], URLDecoder.decode(keyValue[1],"UTF-8"));
            } 
            catch (UnsupportedEncodingException e) 
            {
                // This won't happen.
            }
        }
        return b;
    }
    
    // A convenience function to convert a "1" or "0" string to a boolean:
    static boolean stringToBoolean(String s)
    {
        if (s.equals("1"))
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    // A convenience function to convert a boolean to a string containing "1" or "0":
    static String booleanToString(boolean b)
    {
        if (b)
        {
            return "1";
        }
        else
        {
            return "0";
        }
    }
    
    // Convenience function to get a string from the resources defined in XML:
    static String getString(int res)
    {
        return context.getString(res);
    }
    
    // Convenience functions to get data from a cursor using the column name.
    // This function assumes that the field name exists:
    static String cString(Cursor c, String fieldName)
    {
        int colIndex = c.getColumnIndex(fieldName);
        if (c.isNull(colIndex))
        {
            return("");
        }
        else
        {
            return (c.getString(colIndex));
        }
    }
    
    // Convenience function for getting a long int from a cursor, using the column name.
    // This function assumes that the field name exists:
    @SuppressLint("Range")
    static long cLong(Cursor c, String fieldName)
    {
        return (c.getLong(c.getColumnIndex(fieldName)));
    }
    
    // Convenience function for getting an int from a cursor, using the column name.
    // This function assumes that the field name exists:
    @SuppressLint("Range")
    static int cInt(Cursor c, String fieldName)
    {
        return (c.getInt(c.getColumnIndex(fieldName)));
    }
  
    // Given a timestamp in milliseconds, get the timestamp at midnight on the same
    // day.
    static long getMidnight(long ms)
    {
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            settings.getString(PrefNames.HOME_TIME_ZONE,"")));
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.AM_PM,Calendar.AM);
        return c.getTimeInMillis();
    }
    
    // Same as above, but using a passed in context to avoid issues with parallel access:
    static long getMidnight(long ms, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            prefs.getString("home_time_zone","")));
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.AM_PM,Calendar.AM);
        return c.getTimeInMillis();
    }
    
    // Given a timestamp in milliseconds, get a String containing the day of the week
    // and date.  This does not return a time.
    static String getDateString(long ms)
    {
        return getDateString(ms,null);       
    }
    
    // Same as above, but using a passed in context to avoid issues with parallel access.
    // The context may be null, in which case the static variable here is used.
    static String getDateString(long ms, Context con)
    {
    	GregorianCalendar c;
    	if (con==null)
    	{
    		c = new GregorianCalendar(TimeZone.getTimeZone(
    			settings.getString(PrefNames.HOME_TIME_ZONE,"")));
    	}
    	else
    	{
    		SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    		c = new GregorianCalendar(TimeZone.getTimeZone(
    			prefs.getString(PrefNames.HOME_TIME_ZONE,"")));
    	}
        c.setTimeInMillis(ms);
        String result = "";
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow==Calendar.SUNDAY) { result = Util.getString(R.string.Sunday); };
        if (dow==Calendar.MONDAY) { result = Util.getString(R.string.Monday); };
        if (dow==Calendar.TUESDAY) { result = Util.getString(R.string.Tuesday); };
        if (dow==Calendar.WEDNESDAY) { result = Util.getString(R.string.Wednesday); };
        if (dow==Calendar.THURSDAY) { result = Util.getString(R.string.Thursday); };
        if (dow==Calendar.FRIDAY) { result = Util.getString(R.string.Friday); };
        if (dow==Calendar.SATURDAY) { result = Util.getString(R.string.Saturday); };
        int month = c.get(Calendar.MONTH);
        month++;
        String year = Integer.toString(c.get(Calendar.YEAR));

        // Format based on the system date format:
        result += " ";
        switch (currentDateFormat)
        {
        case DATE_FORMAT_DAY_FIRST:
            if (year.length()==4)
                year = year.substring(2,4);
        	result += c.get(Calendar.DAY_OF_MONTH)+"/";
        	result += Integer.toString(month)+"/";
        	result += year;
        	break;
        case DATE_FORMAT_YEAR_FIRST:
        	result += year+"/";
        	result += Integer.toString(month)+"/";
        	result += c.get(Calendar.DAY_OF_MONTH);
        	break;
        default:
            if (year.length()==4)
                year = year.substring(2,4);
        	result += Integer.toString(month)+"/";
        	result += c.get(Calendar.DAY_OF_MONTH)+"/";
        	result += year;
        	break;
        }
        return result;        
    }
    
    // Given a date string (as returned above), get the timestamp in ms:
    static long dateToMillis(String dateStr)
    {
        String result;
        
        // Strip off the the day of week and a space.
        String[] split = dateStr.split(" ");
        result = split[1];
        
        // Get a string array with the month, day, and year:
        String[] array = result.split("/");

        // Decode based on the system date format:
        int month;
        int day;
        int year;
        switch (currentDateFormat)
        {
        case DATE_FORMAT_DAY_FIRST:
            day = Integer.parseInt(array[0]);
            month = Integer.parseInt(array[1]);
            year = Integer.parseInt(array[2]);
        	break;
        case DATE_FORMAT_YEAR_FIRST:
            year = Integer.parseInt(array[0]);
            month = Integer.parseInt(array[1]);
            day = Integer.parseInt(array[2]);
        	break;
        default:
            month = Integer.parseInt(array[0]);
            day = Integer.parseInt(array[1]);
            year = Integer.parseInt(array[2]);
        	break;
        }
        if (year<100)
        {
            // 2 digit year
            year += 2000;
        }
        
        // Use the month, day, and year to create a Calendar object:
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            settings.getString("home_time_zone","")));
        c.set(Calendar.MONTH, month-1);
        c.set(Calendar.DATE, day);
        c.set(Calendar.YEAR, year);
        c.set(Calendar.HOUR, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.AM_PM, Calendar.AM);
        return (c.getTimeInMillis());
    }
    
    // Given a date and time string, get the timestamp in ms:
    static long dateTimeToMillis(String dateStr, String timeStr)
    {
    	String result;
    	
        // Strip off the day of week and a space.
        String[] split = dateStr.split(" ");
        result = split[1];
        
        // Get a string array with the month, day, and year:
        String[] array = result.split("/");

        // Decode based on the system date format:
        int month;
        int day;
        int year;
        switch (currentDateFormat)
        {
        case DATE_FORMAT_DAY_FIRST:
            day = Integer.parseInt(array[0]);
            month = Integer.parseInt(array[1]);
            year = Integer.parseInt(array[2]);
        	break;
        case DATE_FORMAT_YEAR_FIRST:
            year = Integer.parseInt(array[0]);
            month = Integer.parseInt(array[1]);
            day = Integer.parseInt(array[2]);
        	break;
        default:
            month = Integer.parseInt(array[0]);
            day = Integer.parseInt(array[1]);
            year = Integer.parseInt(array[2]);
        	break;
        }
        if (year<100)
        {
            // 2 digit year
            year += 2000;
        }
        
        // Use the month, day, and year to create a Calendar object:
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            settings.getString("home_time_zone","")));
        c.set(Calendar.MONTH, month-1);
        c.set(Calendar.DATE, day);
        c.set(Calendar.YEAR, year);

        // Parse the time string:
        int hour;
        int minute;
        if (currentTimeFormat==Util.TIME_PREF_12H)
        {
	        String amOrPm = timeStr.substring(timeStr.length()-2, timeStr.length());
	        array = timeStr.split(":");
	        hour = Integer.parseInt(array[0]);
	        minute = Integer.parseInt(array[1].substring(0,2));
	        
	        // Account for am/pm:
	        if (amOrPm.equals("PM"))
	        {
	        	if (hour!=12)
	        		hour += 12;
	        }
	        else
	        {
	        	if (hour==12)
	        		hour = 0;
	        }
        }
        else
        {
        	array = timeStr.split(":");
        	hour = Integer.parseInt(array[0]);
	        minute = Integer.parseInt(array[1]);
        }
        
        // Update the Calendar object:
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        
        return(c.getTimeInMillis());
    }  
    
    // Given a timestamp in ms, get a String containing the time, with a format determined
    // by the user's preference.  The context arguement may be null, to use the static
    // variable here.
    static String getTimeString(long ms)
    {
    	return getTimeString(ms,null);
    }
    
    // Same as above, with a passed in context to avoid parallel access issues:
    static String getTimeString(long ms, Context con)
    {
    	GregorianCalendar c;
    	if (con==null)
    	{
    		c = new GregorianCalendar(TimeZone.getTimeZone(
    			settings.getString("home_time_zone","")));
    	}
    	else
    	{
    		SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    		c = new GregorianCalendar(TimeZone.getTimeZone(
    			prefs.getString("home_time_zone","")));
    	}
        c.setTimeInMillis(ms);
        String result;
        if (currentTimeFormat==Util.TIME_PREF_12H)
        {
	        int hour = c.get(Calendar.HOUR);
	        if (hour==0)
	        {
	            hour = 12;
	        }
	        if (c.get(Calendar.MINUTE)>9)
	        {
	            result = hour + ":"+
	                Integer.toString(c.get(Calendar.MINUTE))+ " ";
	        }
	        else
	        {
	            result = hour + ":0"+
	                Integer.toString(c.get(Calendar.MINUTE))+ " ";
	        }
	        
	        if (c.get(Calendar.AM_PM)==Calendar.AM)
	        {
	            result += "AM";
	        }
	        else
	        {
	            result += "PM";
	        }
        }
        else
        {
        	int hour = c.get(Calendar.HOUR_OF_DAY);
        	if (c.get(Calendar.MINUTE)>9)
	        {
	            result = hour + ":"+Integer.toString(c.get(Calendar.MINUTE));
	        }
	        else
	        {
	            result = hour + ":0"+Integer.toString(c.get(Calendar.MINUTE));
	        }
        }
        return (result);
    }
    
    // Get a string containing both date and time:
    public static String getDateTimeString(long ms)
    {
        return (Util.getDateString(ms)+" "+Util.getTimeString(ms));
    }
    
    // Get a string containing both date and time:
    public static String getDateTimeString(long ms, Context con)
    {
        return (Util.getDateString(ms,con)+" "+Util.getTimeString(ms,con));
    }
    
    // Given a time string (like the one returned above), convert to a timestamp in millis:
    static long getMillisFromTimeString(String timeStr)
    {
    	int hour;
    	int minute;
    	String[] array = timeStr.split(":");
    	if (currentTimeFormat==Util.TIME_PREF_12H)
    	{
	        String amOrPm = timeStr.substring(timeStr.length()-2, timeStr.length());
	        hour = Integer.parseInt(array[0]);
	        if (amOrPm.equals("PM") && hour<12)
	        {
	            hour += 12;
	        }
	        if (amOrPm.equals("AM") && hour==12)
	        {
	            hour = 0;
	        }
	        minute = Integer.parseInt(array[1].substring(0,2));
    	}
    	else
    	{
	        hour = Integer.parseInt(array[0]);
	        minute = Integer.parseInt(array[1]);
    	}
        long result = minute*60*1000 + hour*60*60*1000;
        return result;
    }
    
    // Given a hour (24 hour format) and a minute, generate a time string in the same
    // format as above:
    static String getTimeString(int hour, int min)
    {
    	if (currentTimeFormat==Util.TIME_PREF_12H)
    	{
	        int hour2 = hour;
	        boolean isPM = false;
	        if (hour2>11)
	        {
	            isPM = true;
	            if (hour2>12)
	            {
	                hour2-=12;
	            }
	        }
	        if (hour2==0)
	        {
	            hour2 = 12;
	        }
	        String result = Integer.toString(hour2)+":";
	        if (min<10)
	        {
	            result += "0"+Integer.toString(min);
	        }
	        else
	        {
	            result += Integer.toString(min);
	        }
	        result += " ";
	        if (isPM)
	        {
	            result += "PM";
	        }
	        else
	        {
	            result += "AM";
	        }
	        return result;
    	}
    	else
    	{
    		String result;
        	if (min>9)
	        {
	            result = hour + ":"+min;
	        }
	        else
	        {
	            result = hour + ":0"+min;
	        }
        	return result;

    	}
    }
    
    // Given a time string (as generated above), get the hour (24 hour time):
    static int getHourFromString(String timeStr)
    {
    	if (currentTimeFormat==Util.TIME_PREF_12H)
    	{
	        String amOrPm = timeStr.substring(timeStr.length() - 2, timeStr.length());
	        String[] array = timeStr.split(":");
	        int hour = Integer.parseInt(array[0]);
	        if (amOrPm.equals("PM") && hour<12)
	        {
	            hour += 12;
	        }
	        if (amOrPm.equals("AM") && hour==12)
	        {
	            hour = 0;
	        }
	        return hour;
    	}
    	else
    	{
    		String[] array = timeStr.split(":");
    		return Integer.parseInt(array[0]);
    	}
    }
    
    // Given a time string (as generated above), get the minute:
    static int getMinuteFromString(String timeStr)
    {
    	if (currentTimeFormat==Util.TIME_PREF_12H)
    	{
    		String[] array = timeStr.split(" ");
    		String[] array2 = array[0].split(":");
    		return Integer.parseInt(array2[1]);
    	}
    	else
    	{
    		String[] array = timeStr.split(":");
    		return Integer.parseInt(array[1]);
    	}
    }
    
    // Given a time in millis, add a certain number of days, taking DST and time zones
    // into account.
    static long addDays(long startTime, int numDays, String timeZoneID)
    {
    	GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(timeZoneID));
    	c.setTimeInMillis(startTime);
    	c.add(Calendar.DATE, numDays);
    	return c.getTimeInMillis();
    }

    /** Given a date/time that is as expected in one time zone, generate a new time with the
     * same hour and minute in another time zone.  For example, if the millis passed in corresponds
     * to 8am Eastern Time, this can return a millis value that corresponds to 8am Pacific time.
     * @param millis The timestamp to be adjusted
     * @param sourceTimeZone The millis value is as expected in this time zone. Pass in
     *        null to use the home time zone (at installation)
     * @param destTimeZone The time zone to shift the input time into.  Example: TimeZone.getDefault().
     *        pass in null to use the home time zone (at installation)
     * @return A millisecond timestamp, having the same hour and minute as the input, but in the
     *        destTimeZone
     */
    static long timeShift(long millis, TimeZone sourceTimeZone, TimeZone destTimeZone)
    {
        if (sourceTimeZone==null)
        {
            sourceTimeZone = TimeZone.getTimeZone(settings.getString(PrefNames.HOME_TIME_ZONE,
                "America/Los_Angeles"));
        }
        if (destTimeZone==null)
        {
            destTimeZone = TimeZone.getTimeZone(settings.getString(PrefNames.HOME_TIME_ZONE,
                "America/Los_Angeles"));
        }
        long difference = destTimeZone.getOffset(millis) - sourceTimeZone.getOffset(millis);
        return millis - difference;
    }

    // Get a number string using the current locale:
    static String getNumberString(int n)
    {
    	return NumberFormat.getInstance().format(n);
    }
    static String getNumberString(long n)
    {
    	return NumberFormat.getInstance().format(n);
    }
        
    /** Display a brief popup message that lasts for a couple of seconds and then disappmarears: */
    static void popup(Context c, int stringID)
    {
    	popup(c,c.getString(stringID));
    }

    /** Display a brief popup message that lasts for a couple of seconds and then disappears: */
    static void popup(Context c, String message)
    {
        Log.i("Popup","Popup: "+message);

    	if (Build.MANUFACTURER.toLowerCase().equals("amazon") && c instanceof android.app.Activity)
    	{
    		// Use a custom toast.  Kindle Fires have an issue with black on black text.
    		Activity a = (Activity)c;
    		TextView tv = (TextView)a.getLayoutInflater().inflate(R.layout.custom_toast, null);
    		tv.setText(message);
    		Toast toast = new Toast(c);
    		toast.setView(tv);
    		toast.setDuration(Toast.LENGTH_LONG);
    		toast.show();
    	}
    	else
    		Toast.makeText(c, message, Toast.LENGTH_LONG).show();
    }

    /** Display a brief popup (a toast message) */
    static void shortPopup(Context c, String message)
    {
        Log.i("Popup","Short Popup: "+message);

    	if (Build.MANUFACTURER.toLowerCase().equals("amazon") && c instanceof android.app.Activity)
    	{
    		// Use a custom toast.  Kindle Fires have an issue with black on black text.
    		Activity a = (Activity)c;
    		TextView tv = (TextView)a.getLayoutInflater().inflate(R.layout.custom_toast, null);
    		tv.setText(message);
    		Toast toast = new Toast(c);
    		toast.setView(tv);
    		toast.setDuration(Toast.LENGTH_SHORT);
    		toast.show();
    	}
    	else
    		Toast.makeText(c, message, Toast.LENGTH_SHORT).show();
    }
    
    // Display a longer popup message that a user must tap on to acknowledge.
    // The title argument may be null or an empty string.
    static void longerPopup(Context c, String title, String message)
    {
        Log.i("Popup","Longer Popup: "+message);

    	// Handler for pressing the OK button in the dialog (nothing to do):
		DialogInterface.OnClickListener dialogClickListener = new 
			DialogInterface.OnClickListener() 
        {
			@Override
            public void onClick(DialogInterface dialog, int which) 
            {
            }
        };
        
        // Create and show the message dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setMessage(message);
        if (title!=null && title.length()>0)
        	builder.setTitle(title);
        builder.setPositiveButton(Util.getString(R.string.OK), dialogClickListener);
        builder.show();
    }
    
    // Given a reminder time in minutes, convert to a descriptive string:
    static String getReminderString(Context c, long minutes)
    {
        int minuteArray[] = c.getResources().getIntArray(R.array.reminder_minutes);
        String reminderArray[] = c.getResources().getStringArray(R.array.reminder_options);
        for (int i=0; i<minuteArray.length; i++)
        {
            if (minuteArray[i]==minutes)
            {
                return reminderArray[i];
            }
        }
        // If we get no match (highly unlikely), then return the "none" item.
        return reminderArray[0];
    }
    
    // Given a Long iterator, return a long array:
    static long[] iteratorToLongArray(Iterator<Long> it, int size)
    {
        long[] result = new long[size];
        int i=0;
        while (it.hasNext())
        {
            result[i] = it.next();
            i++;
        }
        return result;
    }
    
    // Given an int iterator, return a long array:
    static int[] iteratorToIntArray(Iterator<Integer> it, int size)
    {
        int[] result = new int[size];
        int i=0;
        while (it.hasNext())
        {
            result[i] = it.next();
            i++;
        }
        return result;
    }
    
    // Given a String iterator, return a String array:
    static String[] iteratorToStringArray(Iterator<String> it, int size)
    {
        String[] result = new String[size];
        int i=0;
        while (it.hasNext())
        {
            result[i] = it.next();
            i++;
        }
        return result;
    }
    
    // Given a database cursor, get a long array containing items from a specific field:
    static long[] cursorToLongArray(Cursor c, String field)
    {
        long[] result = new long[c.getCount()];
        c.moveToFirst();
        while (!c.isAfterLast())
        {
            result[c.getPosition()] = Util.cLong(c, field);
            c.moveToNext();
        }
        return result;
    }

    // Given a database cursor, get a string array containing items from a specific field:
    static String[] cursorToStringArray(Cursor c, String field)
    {
        String[] result = new String[c.getCount()];
        c.moveToFirst();
        while (!c.isAfterLast())
        {
            result[c.getPosition()] = Util.cString(c, field);
            c.moveToNext();
        }
        return result;
    }
    
    // Delete a task:
    static void deleteTask(long id)
    {
        TasksDbAdapter db = new TasksDbAdapter();
        PendingDeletesDbAdapter pendingDeletes = new PendingDeletesDbAdapter();
        UTLTask t = db.getTask(id);
        if (t==null)
        {
            // Already deleted.  Nothing to do.
            return;
        }
        
        // Remove any scheduled reminder notifications:
        if (t.reminder>System.currentTimeMillis())
        {
        	Util.cancelReminderNotification(id);
        }
        
        // If the task has any notifications displayed, remove them:
        Util.removeTaskNotification(id);
        
        // Delete any linked calendar entries if necessary:
        if (t.calEventUri!=null && t.calEventUri.length()>0 &&
        	settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        {
        	if (context!=null)
        	{
        		CalendarInterface ci = new CalendarInterface(context);
        		ci.unlinkTaskFromCalendar(t);
        	}
        }
        
        // Remove it from the tasks table:
        db.deleteTask(id);
        if (t.td_id>-1)
        {
            // This has a Toodledo ID, so we need to store the deletion info for uploading
            // later.
            pendingDeletes.addPendingDelete("task", t.td_id, t.account_id);
        }
        else if (t.remote_id!=null && t.remote_id.length()>0)
        {
        	// This has a Google ID.  Store the deletion info so it can be uploaded later.
        	Cursor c = (new FoldersDbAdapter()).getFolder(t.folder_id);
        	if (c.moveToFirst())
        	{
        		pendingDeletes.addPendingDelete("task", t.remote_id, Util.cString(c, 
        			"remote_id"), t.account_id);
        	}
        	c.close();
        }
        
        // Next, we need to delete any corresponding subtasks:
        Cursor c = db.queryTasks("parent_id="+id, "_id");
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
        	deleteTask(Util.cLong(c,"_id"));
        }

        if (Util.settings.getBoolean(PrefNames.INSTANT_UPLOAD, true))
        {
        	if (t.td_id>-1)
        	{
	        	// Send the delete command for the task to TD now:
	        	Intent i = new Intent(context, Synchronizer.class);
	        	i.putExtra("command", "sync_item");
	        	i.putExtra("operation",Synchronizer.DELETE);
	        	i.putExtra("item_type",Synchronizer.TASK);
	        	i.putExtra("item_id", t.td_id);
	        	i.putExtra("account_id", t.account_id);
	        	Synchronizer.enqueueWork(context,i);
        	}
        	else if (t.remote_id!=null && t.remote_id.length()>0)
        	{
        		// We need the remote folder ID:
        		Cursor c2 = (new FoldersDbAdapter()).getFolder(t.folder_id);
        		String gFolderID;
            	if (c2.moveToFirst())
            		gFolderID = Util.cString(c2, "remote_id");
            	else
            	{
            		c2.close();
            		return;
            	}
            	c2.close();
            	
        		// Send the delete command for the task to Google now:
	        	Intent i = new Intent(context, Synchronizer.class);
	        	i.putExtra("command", "sync_item");
	        	i.putExtra("operation",Synchronizer.DELETE);
	        	i.putExtra("item_type",Synchronizer.TASK);
	        	i.putExtra("item_id", -1);
	        	i.putExtra("account_id", t.account_id);
	        	i.putExtra("remote_id", t.remote_id);
	        	i.putExtra("remote_tasklist_id", gFolderID);
                Synchronizer.enqueueWork(context,i);
        	}
        }
        
        // Widgets need to be updated whenever a delete occurs, so that the user doesn't try to open
        // the deleted task.
        Util.updateWidgets();
    }
    
    // Mark a task as complete.  This marks any subtasks as complete as well, and also 
    // creates new tasks based on repeating patterns:
    static void markTaskComplete(long id)
    {
    	markTaskComplete(id,false);
    }
    
    // Same as above, but with a flag that specifies if it was marked complete 
    // remotely (which affects the processing):
    static void markTaskComplete(long id, boolean completedRemotely)
    {
        TasksDbAdapter db = new TasksDbAdapter();
        UTLTask task = db.getTask(id);
        UTLTask newTask = null;
        if (task==null)
        {
            // Task doesn't exist.  Very strange.
            Util.log("Invalid task ID ("+id+") passed to Util.markTaskComplete().");
            return;
        }
        
        task.completed = true;
        if (!completedRemotely)
        {
        	task.mod_date = System.currentTimeMillis();
        	task.completion_date = task.mod_date;
        }
        
        // We also need to stop the timer if it is running:
        if (task.timer_start_time>0)
        {
        	long elapsedTimeMillis = System.currentTimeMillis() - 
            	task.timer_start_time;
        	task.timer_start_time = 0;
            task.timer = task.timer + (elapsedTimeMillis/1000);
        }
        
        // If the task is a repeating task, we will be generating a new task here.
        // Set the new_task_generated flag:
        boolean generateNewTask = false;
        if (((task.repeat>0 && task.repeat<9) || (task.repeat>100 && task.repeat<109) ||
            task.repeat==50 || task.repeat==150 || task.repeat==10 || task.repeat==110) &&
            !task.new_task_generated)
        {
        	task.new_task_generated = true;
        	task.mod_date = System.currentTimeMillis();
        	generateNewTask = true;        	
        }
        
        // When we generate a new task, the completed version of the task cannot keep
        // the link to the calendar item.  This is needed to prevent duplicate tasks and
        // unwanted links from the event to the task.
        String savedCalEventUri = task.calEventUri;
        if (generateNewTask)
        	task.calEventUri = "";
        
        if (!db.modifyTask(task))
        {
            Util.log("Database modification failed when trying to update completed "+
                "for task ID "+task._id);
            return;
        }
        
        // Remove any scheduled reminder notifications:
        if (task.reminder>System.currentTimeMillis())
        {
        	Util.cancelReminderNotification(id);
        }
        
        // If the task has any notifications displayed, remove them:
        Util.removeTaskNotification(id);

        // Notify Tasker about the task's completion if the plugin is installed:
        try
        {
            PurchaseManager pm = new PurchaseManager(context.getApplicationContext());
            if (pm.isPurchased(PurchaseManager.SKU_TASKER))
            {
                Intent requestQuery = new Intent("com.twofortyfouram.locale.intent.action.REQUEST_QUERY");
                requestQuery.putExtra("com.twofortyfouram.locale.intent.extra.ACTIVITY",
                    TaskerCompletedEvent.class.getName());
                Bundle taskData = new Bundle();
                taskData.putString("completed_task_title", task.title);
                taskData.putLong("completed_task_id", task._id);
                TaskerPlugin.Event.addPassThroughData(requestQuery, taskData);
                context.getApplicationContext().sendBroadcast(requestQuery);
            }
        }
        catch (Exception e)
        {
            // Catch any exception that might be thrown due to Tasker being uninstalled.
            Util.log("Got exception when sending Tasker broadcast Intent. "+e.getClass().getName()+
                " / "+e.getMessage());
        }

        // If the task repeats, we need to create a new one:
        if (((task.repeat>0 && task.repeat<9) || (task.repeat>100 && task.repeat<109) ||
            task.repeat==50 || task.repeat==150 || task.repeat==10 || task.repeat==110) &&
            generateNewTask)
        {
            newTask = task.clone();
            newTask.completed = false;
            newTask.completion_date = 0;
        	newTask.timer_start_time = 0;
            newTask.timer = 0;
            newTask.new_task_generated = false;
            newTask.remote_id = "";
            newTask.position = "";
            newTask.prev_folder_id = newTask.folder_id;
            newTask.calEventUri = savedCalEventUri;
            newTask.uuid = UUID.randomUUID().toString();
            long startDueDiff = newTask.due_date - newTask.start_date;
                        
            if (newTask.due_date==0 && newTask.start_date==0)
            {
            	// The task has no dates.  Set the due date to today and use that as 
            	// the basis for the next date.
            	newTask.due_date = Util.getMidnight(System.currentTimeMillis());
            	newTask.uses_due_time = false;
            }
            if (newTask.due_date>0)
            {
                if (task.repeat<100)
                {
                    newTask.due_date = Util.getNextDate(newTask.due_date, newTask.repeat, 
                        newTask.rep_advanced);
                }
                else
                {   
                	// Get the base completion date.  It is based on the completion DATE,
                	// and the due TIME, if any.
                	long baseCompletionDateTime = Util.getBaseCompletionDate(
                		task.completion_date, task.due_date, task.uses_due_time);
                    
                    newTask.due_date = Util.getNextDate(baseCompletionDateTime, 
                    	newTask.repeat, newTask.rep_advanced);
                }
            }
            if (newTask.reminder>0)
            {
                if (task.repeat<100)
                {
                    newTask.reminder = Util.getNextDate(newTask.reminder, newTask.repeat, 
                        newTask.rep_advanced);
                }
                else
                {   
                	// Get the base completion date.  It is based on the completion DATE,
                	// and the reminder TIME, if any.
                	long baseCompletionDateTime = Util.getBaseCompletionDate(
                		task.completion_date, task.reminder, true);
                    
                    newTask.reminder = Util.getNextDate(baseCompletionDateTime, newTask.repeat, 
                        newTask.rep_advanced);
                }
            }
            if (newTask.start_date>0)
            {
                if (newTask.due_date>0)
                {
                    // The new start date/time has the same offset from the due date as the
                    // original task:
                    newTask.start_date = newTask.due_date - startDueDiff;
                }
                else if (task.repeat<100)
                {
                    newTask.start_date = Util.getNextDate(newTask.start_date, newTask.repeat, 
                        newTask.rep_advanced);
                }
                else
                {
                    // Repeat is from completion date instead of due date.
                    // Get the base completion date.  It is based on the completion DATE,
                    // and the start TIME, if any.
                    long baseCompletionDateTime = Util.getBaseCompletionDate(
                        task.completion_date, task.start_date, task.uses_start_time);

                    newTask.start_date = Util.getNextDate(baseCompletionDateTime,
                        newTask.repeat, newTask.rep_advanced);
                }
            }
            long newID = db.addTask(newTask);
            if (-1==newID)
            {
                Util.log("Could not add new task based on repeating pattern.");
                return;
            }
            else
            {
                newTask._id = newID;
                new TagsDbAdapter().copyTags(id, newID);
            }
            
            if (newTask.reminder>System.currentTimeMillis())
            {
            	// We need to schedule a reminder notification:
            	Util.scheduleReminderNotification(newTask);
            }
            
            if (newTask.calEventUri!=null && newTask.calEventUri.length()>0 && context!=null)
            {
            	// The new instance links to a calendar event.  Update that event so
            	// that it points to this new instance:
            	CalendarInterface ci = new CalendarInterface(context);
            	ci.addTaskLinkToEvent(newTask);
            }
        }
        
        updateCompletedTasksChildren(task,newTask);
    }
    
    // If a completed task has subtasks, this will update them.  Set newTask to null for
    // parents that don't repeat.
    public static void updateCompletedTasksChildren(UTLTask task, UTLTask newTask)
    {    
        TasksDbAdapter db = new TasksDbAdapter();
        long id = task._id;
        
        Cursor c = db.queryTasks("parent_id="+id, "_id");
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask sub = db.getUTLTask(c);
            UTLTask newSub = null;
            boolean parentRepeats = false;
            boolean subRepeatsIndependently = false;
            
            if (sub.note.length()>13 && sub.note.substring(0, 13).equals("Needs Parent:"))
            {
            	// This subtask is only temporarily assigned to this parent, so we 
            	// ignore it.
            	continue;
            }
            	
            if ((sub.repeat>0 && sub.repeat<9) || (sub.repeat>100 && sub.repeat<109)
            	|| sub.repeat==50 || sub.repeat==150 || sub.repeat==10 || sub.repeat==110)
            {
            	// The subtasks repeats, and doesn't repeat with the parent:
            	subRepeatsIndependently = true;
            }
            
            if (((task.repeat>0 && task.repeat<9) || (task.repeat>100 && task.repeat<109) ||
                task.repeat==50 || task.repeat==150 || task.repeat==10 || task.repeat==110) &&
                newTask!=null)
            {
            	parentRepeats = true;
            }
            
            if (!sub.completed && parentRepeats && subRepeatsIndependently)
            {
            	// The subtask is not completed and has a repeating pattern other
            	// than "with parent".  This is a special case.  Do not generate
            	// a new subtask based on the repeating pattern.  Instead
            	// link the incomplete subtask to the new parent.
            	sub.prev_parent_id = sub.parent_id;
            	sub.parent_id = newTask._id;
            	sub.mod_date = System.currentTimeMillis();
                if (!db.modifyTask(sub))
                {
                    Util.log("Database modification failed when updating subtask "+
                        ", in Util.markTaskComplete().");
                }
                
                continue;
            }
            
            // The subtask must be marked as complete:
            boolean subMarkedComplete = false;
            if (!sub.completed)
            {
                sub.completed = true;
                sub.mod_date = System.currentTimeMillis();
                sub.completion_date = sub.mod_date;

                // We also need to stop the timer if it is running:
                if (sub.timer_start_time>0)
                {
                	long elapsedTimeMillis = System.currentTimeMillis() - 
                		sub.timer_start_time;
                	sub.timer_start_time = 0;
                	sub.timer = sub.timer + (elapsedTimeMillis/1000);
                }

                if (!db.modifyTask(sub))
                {
                    Util.log("Database modification failed when marking subtask "+
                        sub._id+" as complete, in Util.markTaskComplete().");
                }
                
                // Remove any reminder notifications and any displayed notifications:
                if (sub.reminder>System.currentTimeMillis())
                {
                	Util.cancelReminderNotification(sub._id);
                }
                Util.removeTaskNotification(sub._id);
                
                subMarkedComplete = true;
            }                    

            if (parentRepeats && (sub.repeat==9 || sub.repeat==109) && 
            	!sub.new_task_generated)
            {
                // The subtask repeats with the parent, and a new subtask has not
            	// yet been generated.
            	
            	sub.new_task_generated = true;
            	sub.mod_date = System.currentTimeMillis();
            	String savedCalEventUri = sub.calEventUri;
            	sub.calEventUri = "";
            	if (!db.modifyTask(sub))
                {
                    Util.log("Database modification failed when marking subtask "+
                        sub._id+" as complete, in Util.markTaskComplete().");
                }
            	
            	// Make a clone of the subtask and link it with the new parent task:
                newSub = sub.clone();
                newSub.completed = false;
                newSub.completion_date = 0;
                newSub.parent_id = newTask._id;
            	newSub.timer_start_time = 0;
                newSub.timer = 0;
                newSub.new_task_generated = false;
                newSub.position = "";
                newSub.remote_id = "";
                newSub.prev_folder_id = newSub.folder_id;
                newSub.calEventUri = savedCalEventUri;
                newSub.uuid = UUID.randomUUID().toString();
                long startDueDiff = newSub.due_date - newSub.start_date;
                
                if (newSub.due_date==0 && newSub.start_date==0)
                {
                	// The task has no dates.  Set the due date to today and use that as 
                	// the basis for the next date.
                	newSub.due_date = Util.getMidnight(System.currentTimeMillis());
                	newSub.uses_due_time = false;
                }

                // Move the dates forward in the new subtask.  The repeat pattern is based on
                // the parent.
                if (newSub.due_date>0)
                {
                    if (newTask.repeat<100)
                    {
                        newSub.due_date = Util.getNextDate(newSub.due_date, newTask.repeat, 
                            newTask.rep_advanced);
                    }
                    else
                    {   
                    	// Get the base completion date.  It is based on the completion DATE,
                    	// and the due TIME, if any.
                    	long baseCompletionDateTime = Util.getBaseCompletionDate(
                    		sub.completion_date, sub.due_date, sub.uses_due_time);
                        newSub.due_date = Util.getNextDate(baseCompletionDateTime, 
                        	newTask.repeat, newTask.rep_advanced);
                    }
                }
                if (newSub.reminder>0)
                {
                    if (newTask.repeat<100)
                    {
                        newSub.reminder = Util.getNextDate(newSub.reminder, newTask.repeat, 
                            newTask.rep_advanced);
                    }
                    else
                    {   
                    	// Get the base completion date.  It is based on the completion DATE,
                    	// and the reminder TIME, if any.
                    	long baseCompletionDateTime = Util.getBaseCompletionDate(
                    		sub.completion_date, sub.reminder, true);
                        newSub.reminder = Util.getNextDate(baseCompletionDateTime, 
                        	newTask.repeat, newTask.rep_advanced);
                    }
                }
                if (newSub.start_date>0)
                {
                    if (newTask.repeat<100)
                    {
                        newSub.start_date = Util.getNextDate(newSub.start_date, newTask.repeat, 
                            newTask.rep_advanced);
                    }
                    else
                    {   
                    	if (newSub.due_date>0)
                    	{
                    		// When repeating from completion date, the new start date/time has
                    		// the same offset from the due date as the original task:
                    		newSub.start_date = newSub.due_date - startDueDiff;
                    	}
                    	else
                    	{
                    		// Get the base completion date.  It is based on the completion DATE,
                        	// and the start TIME, if any.
                        	long baseCompletionDateTime = Util.getBaseCompletionDate(
                        		sub.completion_date, sub.start_date, sub.uses_start_time);
                            newSub.start_date = Util.getNextDate(baseCompletionDateTime, 
                            	newTask.repeat, newTask.rep_advanced);
                    	}
                    }
                }
                
                if (-1==db.addTask(newSub))
                {
                    Util.log("Could not add new subtask based on parent's repeating pattern.");
                }                                                
                else 
                {
                	new TagsDbAdapter().copyTags(sub._id, newSub._id);
                	if (newSub.reminder>System.currentTimeMillis())
                	{
                		// Schedule a reminder notification:
                		Util.scheduleReminderNotification(newSub);
                	}
                	
                    if (newTask.calEventUri!=null && newTask.calEventUri.length()>0 && context!=null)
                    {
                    	// The new instance links to a calendar event.  Update that event so
                    	// that it points to this new instance:
                    	CalendarInterface ci = new CalendarInterface(context);
                    	ci.addTaskLinkToEvent(newTask);
                    }
                }
            }
            
            if (subMarkedComplete)
            {
            	// If the subtask also has subtasks, then we need to call this function
            	// recursively:
            	updateCompletedTasksChildren(sub,newSub);
            }
        }
    }
    
    // Give a base date in ms, get the next date based on the repeating pattern.
    // This returns 0 if the task does not repeat or it repeats with parent.
    static long getNextDate(long baseDate, int repeat, String advancedRepeat)
    {
        // Don't care about whether we're repeating from the completion date or not.
        // The caller deals with that.
        if (repeat>100)
        {
            repeat = repeat-100;
        }
        
        if (repeat==0 || repeat==9)
        {
            // Doesn't repeat or repeats with parent:
            return(0);
        }
        
        // Convert baseDate into a Calendar object:
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            settings.getString("home_time_zone","")));
        c.setTimeInMillis(baseDate);
        
        if (repeat==1)
        {
            // Weekly:
            c.add(Calendar.WEEK_OF_YEAR,1);
        }
        else if (repeat==2)
        {
            // Monthly:
            c.add(Calendar.MONTH, 1);
        }
        else if (repeat==3)
        {
            // Yearly:
            c.add(Calendar.YEAR, 1);
        }
        else if (repeat==4)
        {
            // Daily:
            c.add(Calendar.DATE, 1);
        }
        else if (repeat==5)
        {
            // Biweekly:
            c.add(Calendar.WEEK_OF_YEAR,2);
        }
        else if (repeat==6)
        {
            // Bimonthly:
            c.add(Calendar.MONTH, 2);
        }
        else if (repeat==7)
        {
            // Semiannually:
            c.add(Calendar.MONTH, 6);
        }
        else if (repeat==8)
        {
            // Quarterly:
            c.add(Calendar.MONTH,3);
        }
        else if (repeat==10)
        {
            // End of the month. First, see if the base date is at the end of the month.
            int lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
            if (c.get(Calendar.DAY_OF_MONTH)==lastDay)
            {
                // We're at the end of the current month. Move to the start of the next month,
                // then set the date to the end of that month.
                c.add(Calendar.DATE, 1);
                lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
                c.set(Calendar.DAY_OF_MONTH,lastDay);
            }
            else
            {
                // We're not at the end of the current month, so just move the date there.
                c.set(Calendar.DAY_OF_MONTH,lastDay);
            }
        }
        else if (repeat==50)
        {
            // Advanced:
            AdvancedRepeat adv = new AdvancedRepeat();
            if (!adv.initFromString(advancedRepeat))
            {
                // This should not happen!
                Util.log("Invalid string ("+advancedRepeat+") passed into Util.getNextDate().");
                return(baseDate);
            }
            return adv.getNextDate(baseDate);
        }
        else
        {
            Util.log("Invalid repeat value ("+repeat+") passed into Util.getNextDate()");
            return(baseDate);
        }
        
        return c.getTimeInMillis();
    }
    
    // When repeating from completion date, the base date for the repeat is based on the 
    // completion DATE, and the start/due/reminder TIME.  This function returns a 
    // base completion date in ms.  The usesTime flag specifies whether the task
    // uses the appropriate time field.
    // This function is also useful whenever you want to merge a date from one timestamp
    // with the time from another.
    static long getBaseCompletionDate(long baseDateMS, long baseTimeMS, boolean usesTime)
    {
    	GregorianCalendar baseDate = new GregorianCalendar(TimeZone.
        	getTimeZone(settings.getString("home_time_zone",""))); 
        baseDate.setTimeInMillis(baseDateMS);
        GregorianCalendar taskDate = new GregorianCalendar(TimeZone.
        	getTimeZone(settings.getString("home_time_zone","")));
        taskDate.setTimeInMillis(baseTimeMS);
        if (usesTime)
        {
        	baseDate.set(Calendar.HOUR_OF_DAY,taskDate.get(Calendar.HOUR_OF_DAY));
        	baseDate.getTimeInMillis(); // Needed to keep Calendar data consistent.
        	baseDate.set(Calendar.MINUTE,taskDate.get(Calendar.MINUTE));
        	baseDate.getTimeInMillis();
        	// Note that the AM_PM setting is not set here, because this is taken care
        	// of by HOUR_OF_DAY, and doing so tends to mess up the hour for no 
        	// discernible reason.
        }
        else
        {
        	baseDate.set(Calendar.HOUR_OF_DAY,0);
        	baseDate.getTimeInMillis(); // Needed to keep Calendar data consistent.
        	baseDate.set(Calendar.MINUTE, 0);
        	baseDate.getTimeInMillis();
        	baseDate.set(Calendar.AM_PM,Calendar.AM);
        	baseDate.getTimeInMillis();
        }
        baseDate.set(Calendar.SECOND,0);
        baseDate.getTimeInMillis();
        baseDate.set(Calendar.MILLISECOND, 0);
        return baseDate.getTimeInMillis();    	
    }
    
    // Refresh the system alarms that fire regularly to perform synchronization
    // and database cleanup.
    static void refreshSystemAlarms(Context context)
    {
        // Set up a scheduled job for DatabaseCleaner that performs daily tasks such as backing
        // up and cleaning the database.
        scheduleDailyJobs(context);

        Intent i;
        if (settings.getInt(PrefNames.SYNC_INTERVAL,60)>0 && settings.getBoolean(PrefNames.AUTO_SYNC, 
        	true))
        {
        	if (getSyncPolicy()==SCHEDULED_SYNC)
        	{
        	    scheduleAutomaticSync(context,false);

        	    // If the last sync occurred more than one sync interval ago, run a sync now.
                long intervalMillis = settings.getInt(PrefNames.SYNC_INTERVAL, 60) * 60000;
                long nextSync = Util.settings.getLong(PrefNames.LAST_AUTO_SYNC_TIME, 0)+intervalMillis;
                if (System.currentTimeMillis()>nextSync)
                {
                    i = new Intent(context, Synchronizer.class);
                    i.putExtra("command", "full_sync");
                    i.putExtra("is_scheduled", true);
                    Synchronizer.enqueueWork(context,i);
                }
        	}
        	else
        	{
        		syncIfNeeded(context);
        	}
        }
        else
        {
            cancelAutomaticSync(context);
        }

        PendingIntent pi;
        AlarmManager a = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Util.IS_AMAZON)
        {
            // Kindle devices don't have access to Google play services. Call the location
            // checker repeatedly:
            @SuppressLint("WrongConstant")
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.
                JOB_SCHEDULER_SERVICE);
            if (scheduler==null)
            {
                log("WARNING: JobScheduler is null.");
                return;
            }

            // See if the job already exists. If it exists with the expected interval, there's nothing
            // to do.
            long intervalInMillis = settings.getInt(PrefNames.LOCATION_CHECK_INTERVAL,
                Util.DEFAULT_LOCATION_CHECK_INTERVAL) * 60000;
            boolean jobFound = false;
            List<JobInfo> jobs = scheduler.getAllPendingJobs();
            for (JobInfo jobInfo : jobs)
            {
                if (jobInfo.getId()==LocationChecker.JOB_ID)
                {
                    log("LocationChecker job found. Interval is: "+jobInfo.getIntervalMillis());
                    if (jobInfo.getIntervalMillis()==intervalInMillis)
                    {
                        log("Interval is same as current setting. Not rescheduling.");
                        jobFound = true;
                    }
                    break;
                }
            }

            if (!jobFound)
            {
                // No matching job found, so we need to schedule.
                JobInfo.Builder jobInfo = new JobInfo.Builder(LocationChecker.JOB_ID,new
                    ComponentName(context,LocationChecker.class))
                    .setPeriodic(intervalInMillis)
                    .setPersisted(true);
                int result = scheduler.schedule(jobInfo.build());
                if (result==JobScheduler.RESULT_SUCCESS)
                    Util.log("Job for LocationChecker scheduled successfully.");
                else
                {
                    Util.log("WARNING: The LocationChecker job could not be scheduled. Result: " +
                        result);
                }
            }
        }
        else
        {
            i = new Intent(context, LocationChecker.class);
            pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);
            a.cancel(pi);
            setupGeofencing(context);
        }

        // Schedule Android Wear notifications if needed:
        Util.scheduleWearDailySummaries(context);

        // Force a widget refresh at 1 am every day.  This will help to ensure the widgets refresh
        // properly.
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = awm.getAppWidgetIds(new ComponentName(context,TaskListWidgetScrollable.
            class));
        if (appWidgetIds.length>0)
        {
            Intent intent = new Intent(context,TaskListWidgetScrollable.class);
            intent.putExtra("appWidgetIds",appWidgetIds);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            pi = PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);
            long nextRun = Util.getMidnight(System.currentTimeMillis())+Util.ONE_DAY_MS+60*60000;
            Util.log("Setting daily forced widget refresh, starting at "+Util.getDateTimeString(
                nextRun));
            a.setInexactRepeating(AlarmManager.RTC,nextRun,AlarmManager.INTERVAL_DAY, pi);
        }
    }

    /** Schedule daily jobs performed by the DatabaseCleaner class. */
    static private void scheduleDailyJobs(Context c)
    {
        // Check to see if a job already exists:
        long expectedInterval = 60*60*1000;
        @SuppressLint("WrongConstant")
        JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler==null)
        {
            Log.e(Log.className(c),"JobScheduler null","JobScheduler is null.");
            return;
        }
        boolean jobFound = false;
        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        for (JobInfo jobInfo : jobs)
        {
            if (jobInfo.getId()==DatabaseCleaner.JOB_ID && jobInfo.getIntervalMillis()==
                expectedInterval)
            {
                jobFound = true;
                break;
            }
        }

        if (jobFound)
        {
            // The job already exists and we're not replacing the current one.
            log("DatabaseCleaner job already exists. Not rescheduling.");
            return;
        }

        // Define and schedule the job.
        JobInfo.Builder jobInfo = new JobInfo.Builder(DatabaseCleaner.JOB_ID,new ComponentName(c,
            DatabaseCleaner.class))
            .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_LINEAR)
            .setPeriodic(expectedInterval)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresDeviceIdle(false);
        int result = scheduler.schedule(jobInfo.build());
        if (result==JobScheduler.RESULT_SUCCESS)
            Util.log("Job for DatabaseCleaner scheduled successfully.");
        else
        {
            Util.log("WARNING: The DatabaseCleaner job could not be scheduled. Result: " +
                result);
        }
    }

    /** Schedule automatic background synchronization. The input determines if the existing
     * job info should be replaced (say, after a settings change.) */
    static void scheduleAutomaticSync(Context c, boolean replaceCurrent)
    {
        // Check to see if a job already exists:
        @SuppressLint("WrongConstant")
        JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.
            JOB_SCHEDULER_SERVICE);
        if (scheduler==null)
        {
            log("WARNING: JobScheduler is null.");
            return;
        }
        boolean jobFound = false;
        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        for (JobInfo jobInfo : jobs)
        {
            if (jobInfo.getId()==JOB_ID_AUTO_SYNC)
            {
                jobFound = true;
                break;
            }
        }

        if (jobFound && !replaceCurrent)
        {
            // The job already exists and we're not replacing the current one.
            return;
        }

        // We're replacing the current job. Delete the old one:
        if (jobFound)
            scheduler.cancel(JOB_ID_AUTO_SYNC);

        // Define and schedule the new job, based on the user's preferences:
        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
        long intervalMillis = prefs.getInt(PrefNames.SYNC_INTERVAL, 60) * 60000;
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID_AUTO_SYNC,new
            ComponentName(c, SynchronizerJobService.class))
            .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_LINEAR)
            .setPeriodic(intervalMillis)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .build();
        int result = scheduler.schedule(jobInfo);
        if (result==JobScheduler.RESULT_SUCCESS)
            Util.log("Job for background sync scheduled successfully.");
        else
        {
            Util.log("WARNING: The background job for sync could not be scheduled. Result: " +
                result);
        }
    }

    /** Cancel automatic background synchronization. */
    static void cancelAutomaticSync(Context c)
    {
        // Check to see if a job already exists. Cancel it if it does.
        @SuppressLint("WrongConstant")
        JobScheduler scheduler = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler==null)
        {
            log("WARNING: JobScheduler is null.");
            return;
        }
        boolean jobFound = false;
        List<JobInfo> jobs = scheduler.getAllPendingJobs();
        for (JobInfo jobInfo : jobs)
        {
            if (jobInfo.getId()==JOB_ID_AUTO_SYNC)
            {
                jobFound = true;
                break;
            }
        }
        if (jobFound)
        {
            scheduler.cancel(JOB_ID_AUTO_SYNC);
            Util.log("Cancelled the job for automatic background sync.");
        }
    }

    // Schedule the reminder notification for a task:
    static void scheduleReminderNotification(UTLTask t)
    {
    	if (t.reminder==0)
    	{
    		// Task doesn't have a reminder.  Nothing to do.
    		return;
    	}
    	
    	// If the current time zone is different than the home time zone, the
    	// reminder time needs to be offset.  The home time zone is the time zone
    	// that was in effect when the app was installed.
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long difference = currentTimeZone.getOffset(System.currentTimeMillis()) - 
    		defaultTimeZone.getOffset(System.currentTimeMillis());
    	long reminderTime = t.reminder - difference;
    	
    	scheduleReminderNotification(t, reminderTime);
    }
    
    // Schedule a reminder notification with no time zone adjustment.
    static void scheduleReminderNotification(UTLTask t, long reminderTime)
    {
    	// The URI to pass in the Intent to the Notifier class:
    	Uri.Builder uriBuilder = new Uri.Builder();
    	uriBuilder.scheme("viewtask");
    	uriBuilder.opaquePart(new Long(t._id).toString());
    	
    	// The Intent to pass to the Notifier class:
    	Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,Notifier.class);
    	
    	// Wrap the intent in the required PendingIntent object:
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.
			FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		
		// Set the alarm:
        Util.setExactAlarm(context,pi,reminderTime);
    }
    
    // Cancel the reminder notification for a task:
    static void cancelReminderNotification(long taskID)
    {
    	// The URI that would be passed in the Intent to the Notifier class:
    	Uri.Builder uriBuilder = new Uri.Builder();
    	uriBuilder.scheme("viewtask");
    	uriBuilder.opaquePart(new Long(taskID).toString());
    	
    	// The Intent that would be passed to the Notifier class:
    	Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,Notifier.class);
    	
    	// Wrap the intent in the required PendingIntent object:
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.
			FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		
		// Issue the cancellation:
		AlarmManager a = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		a.cancel(pi);
    }
    
    // Snooze a location reminder.  This sets a time based reminder that appears like
    // a location reminder to the rest of the software:
    static void snoozeLocationReminder(UTLTask t, long reminderTime)
    {
    	if (t.location_id==0)
    		return;
    	
    	// The URI to pass in the Intent to the Notifier class:
    	Uri.Builder uriBuilder = new Uri.Builder();
    	uriBuilder.scheme("viewtask");
    	uriBuilder.opaquePart(new Long(t._id).toString());
    	
    	// The Intent to pass to the Notifier class:
    	Intent i = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,Notifier.class);
    	
    	// This extra field makes it look like a location alarm:
    	i.putExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);
    	
    	// Wrap the intent in the required PendingIntent object:
		PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, PendingIntent.
            FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		
		// Set the alarm:
        Util.setExactAlarm(context,pi,reminderTime);
    }
    
    // Refresh all task reminders.  Typically called at system startup:
    static void refreshTaskReminders()
    {
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	Cursor c = tasksDB.queryTasks("reminder>"+System.currentTimeMillis()+
    		" and completed=0", null);
    	c.moveToPosition(-1);
    	while (c.moveToNext())
    	{
    		UTLTask t = tasksDB.getUTLTask(c);
    		Util.scheduleReminderNotification(t);
    	}
    	c.close();    	
    }
    
    // Remove any notification that a task may be displaying:
    static void removeTaskNotification(long taskID)
    {
    	NotificationManager nm = (NotificationManager)context.getSystemService(Context.
			NOTIFICATION_SERVICE);
    	nm.cancel(Integer.parseInt(new Long(taskID).toString()));
    }

    /** Set up Geofencing, so we can know when the user enters a defined location. */
    static void setupGeofencing(Context c)
    {
        // This only works devices with Google Play services:
        if (!Util.IS_GOOGLE)
            return;

        // Check to see if the feature is enabled:
        final SharedPreferences prefs = c.getSharedPreferences(PREFS_NAME,0);
        if (!prefs.getBoolean(PrefNames.LOCATIONS_ENABLED,true))
            return;

        if (PackageManager.PERMISSION_DENIED==ContextCompat.checkSelfPermission(c,Manifest.
            permission.ACCESS_FINE_LOCATION))
        {
            Util.log("Can't set up geofencing due to lack of permission.");
            return;
        }

        if (Build.VERSION.SDK_INT>Build.VERSION_CODES.P && PackageManager.PERMISSION_DENIED==
            ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        {
            Util.log("Can't set up geofencing due to lack of background location permission.");
            return;
        }

        // Go through locations and set up the GeoFence config for each:

        float reminderRadius = Integer.valueOf(prefs.getInt(PrefNames.LOC_ALARM_RADIUS,200)).
            floatValue();
        if (reminderRadius==0) reminderRadius=1;
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        Cursor c2 = locDB.getAllLocations();
        ArrayList<Geofence> geofences = new ArrayList<>();
        while (c2.moveToNext())
        {
            UTLLocation l = locDB.cursorToUTLLocation(c2);
            Geofence geofence = new Geofence.Builder()
                .setRequestId(Long.valueOf(l._id).toString())
                .setCircularRegion(l.lat,l.lon,reminderRadius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER |
                    Geofence.GEOFENCE_TRANSITION_EXIT | Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(60000)
                .build();
            geofences.add(geofence);
            Util.log("Adding geofence for "+l.title+": "+l.lat+", "+l.lon);
        }
        c2.close();

        if (geofences.size()>0)
        {
            // Set up a geofencing request that includes all locations:
            GeofencingRequest request = new GeofencingRequest.Builder()
                .addGeofences(geofences)
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL |
                    GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .build();
            PendingIntent geofencePI = PendingIntent.getBroadcast(c,0,new Intent(c,
                GeofenceReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_MUTABLE);
            GeofencingClient geofencingClient = LocationServices.getGeofencingClient(c.
                getApplicationContext());
            geofencingClient.addGeofences(request,geofencePI)
                .addOnSuccessListener(new OnSuccessListener<Void>()
                {
                    @Override
                    public void onSuccess(Void aVoid)
                    {
                        Util.log("Geofences successfully added.");
                        prefs.edit().putBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED,false)
                            .apply();
                    }
                })
                .addOnFailureListener(new OnFailureListener()
                {
                    @Override
                    public void onFailure(@NonNull Exception e)
                    {
                        if (e instanceof ApiException)
                        {
                            ApiException a = (ApiException) e;
                            Util.log("WARNING: Could not add geofences: " + a.getClass().getName()+
                                " / "+a.getStatusCode()+" / "+a.getMessage());
                            if (a.getStatusCode()== GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE)
                            {
                                prefs.edit().putBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED,true)
                                    .apply();
                            }
                        }
                        else
                        {
                            Util.log("WARNING: Could not add geofences: " + e.getClass().getName() + "; " +
                                e.getMessage());
                        }
                    }
                });
        }
    }

    // Delete a Geofence. Call this after a location has been deleted.
    static void deleteGeofence(Context c, final long locID)
    {
        if (!Util.IS_GOOGLE)
            return;

        if (PackageManager.PERMISSION_DENIED==ContextCompat.checkSelfPermission(c,Manifest.
            permission.ACCESS_FINE_LOCATION))
        {
            Util.log("Can't remove geofence due to lack of permission.");
            return;
        }

        if (Build.VERSION.SDK_INT>Build.VERSION_CODES.P && PackageManager.PERMISSION_DENIED==
            ContextCompat.checkSelfPermission(c,Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        {
            Util.log("Can't remove geofence due to lack of background permission.");
            return;
        }

        String requestID = Long.valueOf(locID).toString();
        GeofencingClient geofencingClient = LocationServices.getGeofencingClient(c.
            getApplicationContext());
        ArrayList<String> requestIDs = new ArrayList<>();
        requestIDs.add(requestID);
        geofencingClient.removeGeofences(requestIDs)
            .addOnSuccessListener(new OnSuccessListener<Void>()
            {
                @Override
                public void onSuccess(Void aVoid)
                {
                    Util.log("Geofence for location "+locID+" removed.");
                }
            })
            .addOnFailureListener(new OnFailureListener()
            {
                @Override
                public void onFailure(@NonNull Exception e)
                {
                    Util.log("WARNING: Could not remove geofence for location "+locID+". "+
                        e.getClass()+" / "+e.getMessage());
                }
            });
    }

    /** If necessary, prompt the user to enable the high accuracy location needed for Geofencing.
     * Returns true if a prompt is displayed. */
    static boolean promptForHighLocationAccuracy(final Activity a)
    {
        final SharedPreferences prefs = a.getSharedPreferences(Util.PREFS_NAME,0);
        if (!prefs.getBoolean(PrefNames.LOCATIONS_ENABLED,true) ||
            !prefs.getBoolean(PrefNames.LOCATION_REMINDERS_BLOCKED,false))
        {
            // Locations are not enabled or there are no issues with with user's location
            // settings.
            Util.log("promptForHighLocationAccuracy: Locations disabled or no geofencing issues.");
            return false;
        }

        // Make sure the user doesn't see more than one prompt per day:
        long timeSincePrompt = System.currentTimeMillis()-prefs.getLong(PrefNames.
            LOCATION_SETTINGS_LAST_PROMPT,0);
        if (timeSincePrompt<Util.ONE_DAY_MS)
        {
            Util.log("promptForHighLocationAccuracy: Location settings prompt was shown recently.");
            return false;
        }

        // If there are no incomplete tasks with location reminders, then there's no need to
        // display the message.
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        Cursor c = locDB.getAllLocations();
        if (c.getCount()==0)
        {
            c.close();
            Util.log("promptForHighLocationAccuracy: No locations");
            return false;
        }
        String[] locIDs = new String[c.getCount()];
        while (c.moveToNext())
        {
            UTLLocation l = locDB.cursorToUTLLocation(c);
            locIDs[c.getPosition()] = Long.valueOf(l._id).toString();
        }
        c.close();
        c = new TasksDbAdapter().queryTasks("location_id in ("+ TextUtils.join(",",locIDs)+") "+
            "and completed=0 and location_reminder=1",null);
        if (c.getCount()==0)
        {
            // No tasks with location reminders.
            Util.log("promptForHighLocationAccuracy: No tasks with location reminders.");
            c.close();
            return false;
        }
        c.close();

        // Double check to make sure the user's settings really don't allow for high accuracy
        // location.
        LocationRequest locRequest = new LocationRequest();
        locRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
            .addLocationRequest(locRequest)
            .build();
        SettingsClient settingsClient = LocationServices.getSettingsClient(a);
        settingsClient.checkLocationSettings(settingsRequest)
            .addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>()
            {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse)
                {
                    Util.log("promptForHighLocationAccuracy: WARNING: High accuracy location " +
                        "settings are satisfied, "+
                        "but an error in geofencing was reported earlier.");
                }
            })
            .addOnFailureListener(new OnFailureListener()
            {
                @Override
                public void onFailure(@NonNull Exception e)
                {
                    Util.log("promptForHighLocationAccuracy: High accuracy location settings are " +
                        "not satisfied. Showing prompt. "+
                        e.getClass().getName()+" / "+e.getMessage());
                    if (e instanceof ResolvableApiException)
                    {
                        ResolvableApiException r = (ResolvableApiException) e;
                        try
                        {
                            r.startResolutionForResult(a,REQUEST_CODE_LOCATION_SETTINGS);
                            prefs.edit().putLong(PrefNames.LOCATION_SETTINGS_LAST_PROMPT,
                                System.currentTimeMillis()).apply();
                        }
                        catch (IntentSender.SendIntentException e2) { }
                    }
                }
            });
        return true;
    }

    // Given a view, get the SQL "where" and "having" clauses.  The caller must provide
    // references to non-null empty StringBuilder objects, which will be modified.  If a 
    // clause is not used, the StringBuilder objects passed in will not be modified.
    // Note that the words "where" and "having" are not included in the results.
    // The includeHiddenSubtasks option determines if subtasks that are hidden due to 
    // the display settings should be included.
    static void getWhereAndHavingClauses(long viewID, StringBuilder whereClause,  
    	StringBuilder havingClause, boolean includeHiddenSubtasks)
    {
    	// Get the data on the view to count for:
    	Cursor viewCursor = (new ViewsDbAdapter()).getView(viewID);
    	if (!viewCursor.moveToFirst())
        {
            log("Internal Error: Can't get view for view ID "+viewID+".");
            viewCursor.close();
            return;
        }
    	
    	// Get the Display Options.  The subtask option affects the count.
        DisplayOptions displayOptions = new DisplayOptions(Util.cString(viewCursor, 
        	"display_string"));
        viewCursor.close();
    	
        ArrayList<ViewRule> whereClauses = new ArrayList<ViewRule>();
        ArrayList<ViewRule> havingClauses = new ArrayList<ViewRule>();
        ArrayList<Integer> whereIsOrList = new ArrayList<Integer>();
        ArrayList<Integer> havingIsOrList = new ArrayList<Integer>();        
        ViewRulesDbAdapter viewRules = new ViewRulesDbAdapter();
        
        // Make sure the view has some rules (if it doesn't, we've got a problem):
        Cursor c = viewRules.getAllRules(viewID);
        if (!c.moveToFirst())
        {
        	// View has no rules:
        	c.close();
            return;
        }
        
        // Go through the rules and make a note of which ones must appear in a having clause.
        // For the query to be properly formed, if any item within a set of "or"s is in a having
        // clause, then all items in the set need to be in a having clause.  This section marks
        // rules that appear after a rule that must be in the having clause.  The next section
        // of code handles rules that appear before the rule that must be in the having clause.
        boolean[] putInHaving = new boolean[c.getCount()];
        c.moveToPosition(-1);
        int i=0;
        ViewRule vr;
        boolean placeInHaving = false;
        while (c.moveToNext())
        {
        	vr = viewRules.getViewRule(c);
        	putInHaving[i] = false;
        	if (Util.cInt(c,"is_or")==1 && placeInHaving)
        		putInHaving[i] = true;
        	if (Util.cInt(c,"is_or")==0)
        		placeInHaving = false;
        	if (vr.isHavingClause())
        		placeInHaving = true;
        	i++;
        }
        
        // Loop through the rules and create a list of where and having rules:
        c.moveToPosition(c.getCount());
        boolean addNextToHaving = false;
        i = c.getCount()-1;
        while (c.moveToPrevious())
        {
        	vr = viewRules.getViewRule(c);
        	if (vr.isHavingClause() || addNextToHaving || putInHaving[i])
        	{
        		havingClauses.add(0, vr);
        		havingIsOrList.add(0,Util.cInt(c,"is_or"));
        		if (Util.cInt(c,"is_or")==1)
        			addNextToHaving = true;
        		else
        			addNextToHaving = false;
        	}
        	else
        	{
        		whereClauses.add(0,vr);
        		whereIsOrList.add(0,Util.cInt(c,"is_or"));
        	}
        	i--;
        }
        c.close();
        
        // "Where" clauses:
        if (whereClauses.size()>0)
        {
            String whereString = "";
        	boolean inOr = false;
        	for (i=whereClauses.size()-1; i>=0; i--)
        	{
        		vr = whereClauses.get(i);
        		if (i==0)
        		{
        			if (inOr)
                    {
                        whereString = "("+vr.getSQLWhere() + whereString;
                    }
                    else
                    {
                        whereString = vr.getSQLWhere() + whereString;
                    }
        		}
        		else
                {
                    if (inOr)
                    {
                        if (whereIsOrList.get(i)==1)
                        {
                            whereString = " or "+vr.getSQLWhere()+whereString;
                        }
                        else
                        {
                            whereString = " and ("+vr.getSQLWhere()+whereString;
                            inOr = false;
                        }
                    }
                    else
                    {
                        if (whereIsOrList.get(i)==1)
                        {
                            whereString = " or "+vr.getSQLWhere() + ")"+whereString;
                            inOr = true;
                        }
                        else
                        {
                            whereString = " and "+vr.getSQLWhere()+whereString;
                        }                    
                    }
                }
        	}
        	
            // Don't query subtasks for a certain display option:
            if (displayOptions.subtaskOption.equals("separate_screen") && 
            	!includeHiddenSubtasks)
            {
                whereString += " and tasks.parent_id=0";
            }
            whereClause.append(whereString);
        }
        else
        {
        	// No "where" in this query, but for a certain display option we still
        	// need one:
            if (displayOptions.subtaskOption.equals("separate_screen") && 
            	!includeHiddenSubtasks)
            {
                whereClause.append(" tasks.parent_id=0");
            }
        }     
        
        // "Having" clauses:
        if (havingClauses.size()>0)
        {
            String havingString = "";
        	boolean inOr = false;
        	for (i=havingClauses.size()-1; i>=0; i--)
        	{
        		vr = havingClauses.get(i);
        		if (i==0)
        		{
        			if (inOr)
                    {
                        havingString = "("+vr.getSQLWhere() + havingString;
                    }
                    else
                    {
                        havingString = vr.getSQLWhere() + havingString;
                    }
        		}
        		else
                {
                    if (inOr)
                    {
                        if (havingIsOrList.get(i)==1)
                        {
                            havingString = " or "+vr.getSQLWhere()+havingString;
                        }
                        else
                        {
                            havingString = " and ("+vr.getSQLWhere()+havingString;
                            inOr = false;
                        }
                    }
                    else
                    {
                        if (havingIsOrList.get(i)==1)
                        {
                            havingString = " or "+vr.getSQLWhere() + ")"+havingString;
                            inOr = true;
                        }
                        else
                        {
                            havingString = " and "+vr.getSQLWhere()+havingString;
                        }                    
                    }
                }
        	}
        	havingClause.append(havingString);
        }       
    }
    
    // Get the SQL sort order string (everything following the "order by" statement).
    // Inputs are the sort_string field from the views table and the sort_order_string
    // from the views table (as defined in ViewsDbAdapter.java).  A SharedPreferences
    // instance must also be passed in, because certain preferences affect the sort order
    static String getSortOrderSQL(String sortFieldString, String sortOrderString,
    	SharedPreferences settings)
    {
    	if (sortFieldString==null || sortFieldString.length()==0 || sortFieldString.
    		equals("null"))
    	{
    		// No sort string.  Just use the title by default.
    		return ("tasks.title");
    	}
    	
    	// Parse the fields and sort orders:
    	String[] sortFields = sortFieldString.split(",");
    	int[] sortOrders;
    	if (sortOrderString.length()>0)
    	{
    		String[] splitString = sortOrderString.split(",");
    		sortOrders = new int[] {
    			Integer.parseInt(splitString[0]),
    			Integer.parseInt(splitString[1]),
    			Integer.parseInt(splitString[2])
    		};
    	}
    	else
    	{
    		sortOrders = new int[] { 0,0,0 };
    	}
    	
    	String result = "";
    	for (int i=0; i<sortFields.length; i++)
    	{
    		// 2nd or 3rd items need a comma in front:
    		if (i>0) result += ",";
    		
    		// Specify that text fields have a case-insensitive sort, and also add in
    		// the "desc" string if the sort order is reversed.
    		if (sortFields[i].equals("folder") || sortFields[i].equals("context") ||
    			sortFields[i].equals("goal") || sortFields[i].equals("location") ||
    			sortFields[i].equals("tag_name") || sortFields[i].equals("tasks.title") ||
    			sortFields[i].equals("tasks.note") || sortFields[i].equals("account") ||
    			sortFields[i].equals("owner_name") || sortFields[i].equals("assignor_name"))
    		{
    			result += "lower("+sortFields[i]+")";
    			
    			if (sortOrders[i]==1)
    			{
    				// Reverse sort:
    				result += " desc";
    			}
    		}
    		
    		// For other fields, add in the "desc" string when needed, based on the sort 
    		// order preferences.
    		
    		// Start Date:
    		else if (sortFields[i].equals("tasks.start_date"))
    		{
    			if (settings.getInt("undated_sort_order", 0)==1)
    				result += "has_start_date desc,tasks.start_date";
    			else
    				result += "has_start_date,tasks.start_date";
    			if (sortOrders[i]==1)
    				result += " desc";
    		}
    		
    		// Due Date:
    		else if (sortFields[i].equals("tasks.due_date"))
    		{	
    			if (settings.getInt("undated_sort_order", 0)==1)
    				result += "has_due_date desc,tasks.due_date";
    			else
    				result += "has_due_date,tasks.due_date";
    			if (sortOrders[i]==1)
    				result += " desc";
    		}
    		
    		// Reminder:
    		else if (sortFields[i].equals("tasks.reminder"))
    		{	
    			if (settings.getInt("undated_sort_order", 0)==1)
    				result += "has_reminder desc,tasks.reminder";
    			else
    				result += "has_reminder,tasks.reminder";
    			if (sortOrders[i]==1)
    				result += " desc";
    		}

    		// Priority, Star, and Importance (descending order by default):
    		else if (sortFields[i].equals("tasks.priority") || sortFields[i].equals(
    			"tasks.star") || sortFields[i].equals("importance") || 
    			sortFields[i].equals("tasks.is_joint"))
    		{
    			if (sortOrders[i]==0)
    				result += sortFields[i]+" desc";
    			else
    				result += sortFields[i];
    		}

            // Manual Sort.  Always Descending.
            else if (sortFields[i].equals("tasks.sort_order"))
            {
                result += sortFields[i]+" desc";
            }

    		// Everything else:
    		else
    		{
    			result += sortFields[i];
    			if (sortOrders[i]==1)
    				result += " desc";
    		}
    	}
    	
    	return result;
    }
    
    // Get the SQL code that calculates Toodledo's importance field.  The resulting
    // string will create a column named "importance" in the query result.
    static String getImportanceSQL()
    {
    	// Get the offset in ms between the home time zone and the local one:
    	TimeZone currentTimeZone = TimeZone.getDefault();
    	TimeZone defaultTimeZone = TimeZone.getTimeZone(Util.settings.getString(
    		"home_time_zone", "America/Los_Angeles"));
    	long zoneOffset = currentTimeZone.getOffset(System.currentTimeMillis()) - 
			defaultTimeZone.getOffset(System.currentTimeMillis());
    	long baseTime = Util.getMidnight((System.currentTimeMillis()+zoneOffset))/1000;
    	
    	return "(tasks.priority + tasks.star + (case "+
    		"when tasks.due_date=0 then 0 " +
    		"when tasks.due_date/1000-"+baseTime+">=1209600 then 0 "+
    		"when tasks.due_date/1000-"+baseTime+">=604800 then 1 "+
    		"when tasks.due_date/1000-"+baseTime+">=172800 then 2 "+
    		"when tasks.due_date/1000-"+baseTime+">=86400 then 3 "+
    		"when tasks.due_date/1000-"+baseTime+">=0 then 5 "+
    		"else 6 end)) as importance";
    }
    
    // Get the query needed to get a task count:
    static String getTaskCountQuery(long viewID)
    {
        // Select portion:
        String q = "select group_concat(tags.name,',') as tag_list, tasks._id, ";
        
        // Select portion - calculated columns:
        q += Util.getImportanceSQL()+" ";
                        
        // From and join portions:
        q += "from tasks "+
            "left outer join tags on tasks._id=tags.utl_id "+
            "left outer join account_info on tasks.account_id=account_info._id "+
            "left outer join folders on tasks.folder_id=folders._id "+
            "left outer join contexts on tasks.context_id=contexts._id "+
            "left outer join goals on tasks.goal_id=goals._id "+
            "left outer join locations on tasks.location_id=locations._id";
        
        // Get "where" and "having" clauses:
        StringBuilder whereClause = new StringBuilder();
        StringBuilder havingClause = new StringBuilder();
        getWhereAndHavingClauses(viewID,whereClause,havingClause,false);
        
        // Add in the "where" clause:
        if (whereClause.length()>0)
        	q += " where "+whereClause.toString();
        
        // Grouping:
        q += " group by tasks._id";
        
        // Add in the "having" clause:
        if (havingClause.length()>0)
        	q += " having "+havingClause.toString();
        
        return q;
    }
    
    // Given a top level and view name, get the number of tasks that would display
    // in the list view.
    static long firstTaskID;
    static int getNumTasksForView(long viewID)
    {
        // Generate the SQL query:
        String q = getTaskCountQuery(viewID);
        
        // Note that no ordering is done, since we are just getting a
        // count.
        
        // Run the query and get the count:
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.rawQuery(q,null);
        int count = c.getCount();
        if (count>0)
        {
        	c.moveToFirst();
        	firstTaskID = c.getLong(1);
        }
        c.close();
        return count;
    }
    
    // Generate the massively complex SQL statement to use in Activity instances and 
    // widgets that display a task list.  Inputs are a View ID, a Cursor containing data 
    // on the view, and a Context:
    static public String getTaskSqlQuery(long viewID, Cursor viewCursor, Context con)
    {
        String q;
        
        // Select portion - tasks:
        q = "select tasks._id, tasks.td_id, tasks.account_id, tasks.mod_date, tasks.sync_date, "+
            "tasks.title, tasks.completed, tasks.folder_id, tasks.context_id, "+
            "tasks.goal_id, tasks.parent_id, tasks.due_date, tasks.due_modifier, "+
            "tasks.uses_due_time, tasks.reminder, tasks.nag, tasks.start_date, "+
            "tasks.uses_start_time, tasks.repeat, tasks.rep_advanced, tasks.status, "+
            "tasks.length, tasks.priority, tasks.star, tasks.note, tasks.timer, "+
            "tasks.timer_start_time, tasks.completion_date, tasks.location_id, "+
            "tasks.location_reminder, tasks.location_nag, tasks.remote_id, tasks.position,"+
            " tasks.new_task_generated, tasks.prev_folder_id, tasks.prev_parent_id, "+
            "tasks.contact_lookup_key, tasks.cal_event_uri, tasks.is_joint, tasks.owner_remote_id, "+
            "tasks.shared_with, tasks.added_by as added_by, tasks.shared_with_changed, "+
            "tasks.sort_order, tasks.is_moved, tasks.prev_task_id";
        
        // Select portion - other table items:
        q += ", tags.name as tag_name, account_info.name as account, folders.title as folder, "+
            "contexts.title as context, goals.title as goal, locations.title as location, "+
        	"(select collaborators.name from collaborators where collaborators.remote_id=tasks."+
            	"owner_remote_id and collaborators.account_id=tasks.account_id) as owner_name, "+
            "(select collaborators.name from collaborators where collaborators.remote_id=tasks."+
            	"added_by and collaborators.account_id=tasks.account_id) as assignor_name, "+
            "count(tags.name) as num_tags, group_concat(tags.name,',') as tag_list";
        
        // Select portion - calculated columns:
        q += ", tasks.due_date>0 as has_due_date, tasks.start_date>0 as has_start_date, ";
        q += "tasks.reminder>0 as has_reminder, ";
        q += Util.getImportanceSQL()+" ";
        
        // From and join portions:
        q += "from tasks "+
            "left outer join tags on tasks._id=tags.utl_id "+
            "left outer join account_info on tasks.account_id=account_info._id "+
            "left outer join folders on tasks.folder_id=folders._id "+
            "left outer join contexts on tasks.context_id=contexts._id "+
            "left outer join goals on tasks.goal_id=goals._id "+
            "left outer join locations on tasks.location_id=locations._id";
        
        // Get "where" and "having" clauses:
        StringBuilder whereClause = new StringBuilder();
        StringBuilder havingClause = new StringBuilder();
        Util.getWhereAndHavingClauses(viewID,whereClause,havingClause,true);
        
        // Add in the "where" clause:
        if (whereClause.length()>0)
        	q += " where "+whereClause.toString();
        
        // Grouping:
        q += " group by tasks._id";

        // Add in the "having" clause:
        if (havingClause.length()>0)
        	q += " having "+havingClause.toString();
        
        // Ordering:
        q += " order by "+Util.getSortOrderSQL(Util.cString(viewCursor, "sort_string"), 
        	Util.cString(viewCursor, "sort_order_string"), con.getSharedPreferences(
        	"UTL_Prefs", 0));
        		
        return(q);    	
    }
    
    // Convenience functions to update preferences for the app:
    static public void updatePref(String prefName, String value)
    {
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putString(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, long value)
    {
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putLong(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, int value)
    {
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putInt(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, boolean value)
    {
    	SharedPreferences.Editor editor = settings.edit();
    	editor.putBoolean(prefName, value);
    	editor.commit();
    }
    
    // Same as above, but with a passed in context to avoid parallel access issues:
    static public void updatePref(String prefName, String value, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putString(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, long value, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putLong(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, int value, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putInt(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, boolean value, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putBoolean(prefName, value);
    	editor.commit();
    }
    static public void updatePref(String prefName, float value, Context con)
    {
    	SharedPreferences prefs = con.getSharedPreferences("UTL_Prefs",0);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putFloat(prefName, value);
    	editor.commit();
    }
    
    // Given a chosen fields/function preset, make the corresponding updates to the SharedPreferences:
    // prefName is one of the preset strings defined in PrefNames.java.
    static void set_fields_functions_preset(Context con, String prefName)
    {
    	SharedPreferences prefs = con.getSharedPreferences(Util.PREFS_NAME, 0);
    	SharedPreferences.Editor editor = prefs.edit();
    	if (prefName.equals(PrefNames.PRESET_BASIC))
    	{
    		editor.putBoolean(PrefNames.PRESET_BASIC, true);
    		editor.putBoolean(PrefNames.PRESET_INTERMEDIATE, false);
    		editor.putBoolean(PrefNames.PRESET_ADVANCED, false);
    		editor.putBoolean(PrefNames.PRESET_POWER_USER, false);
    		
    		editor.putBoolean(PrefNames.CALENDAR_ENABLED, false);
    		editor.putBoolean(PrefNames.COLLABORATORS_ENABLED, false);
    		editor.putBoolean(PrefNames.REMINDER_ENABLED, false);
    		editor.putBoolean(PrefNames.FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTEXTS_ENABLED, false);
    		editor.putBoolean(PrefNames.GOALS_ENABLED, false);
    		editor.putBoolean(PrefNames.LOCATIONS_ENABLED, false);
    		editor.putBoolean(PrefNames.START_DATE_ENABLED, false);
    		editor.putBoolean(PrefNames.START_TIME_ENABLED, false);
    		editor.putBoolean(PrefNames.DUE_DATE_ENABLED, true);
    		editor.putBoolean(PrefNames.DUE_TIME_ENABLED, false);
    		editor.putBoolean(PrefNames.REPEAT_ENABLED, false);
    		editor.putBoolean(PrefNames.LENGTH_ENABLED, false);
    		editor.putBoolean(PrefNames.TIMER_ENABLED, false);
    		editor.putBoolean(PrefNames.PRIORITY_ENABLED, false);
    		editor.putBoolean(PrefNames.TAGS_ENABLED, false);
    		editor.putBoolean(PrefNames.STATUS_ENABLED, false);
    		editor.putBoolean(PrefNames.STAR_ENABLED, false);
    		editor.putBoolean(PrefNames.SUBTASKS_ENABLED, true);
    		editor.putBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTACTS_ENABLED, false);
    	}
    	else if (prefName.equals(PrefNames.PRESET_INTERMEDIATE))
    	{
    		editor.putBoolean(PrefNames.PRESET_BASIC, false);
    		editor.putBoolean(PrefNames.PRESET_INTERMEDIATE, true);
    		editor.putBoolean(PrefNames.PRESET_ADVANCED, false);
    		editor.putBoolean(PrefNames.PRESET_POWER_USER, false);
    		
    		editor.putBoolean(PrefNames.CALENDAR_ENABLED, false);
    		editor.putBoolean(PrefNames.COLLABORATORS_ENABLED, false);
    		editor.putBoolean(PrefNames.REMINDER_ENABLED, true);
    		editor.putBoolean(PrefNames.FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTEXTS_ENABLED, false);
    		editor.putBoolean(PrefNames.GOALS_ENABLED, false);
    		editor.putBoolean(PrefNames.LOCATIONS_ENABLED, false);
    		editor.putBoolean(PrefNames.START_DATE_ENABLED, false);
    		editor.putBoolean(PrefNames.START_TIME_ENABLED, false);
    		editor.putBoolean(PrefNames.DUE_DATE_ENABLED, true);
    		editor.putBoolean(PrefNames.DUE_TIME_ENABLED, false);
    		editor.putBoolean(PrefNames.REPEAT_ENABLED, true);
    		editor.putBoolean(PrefNames.LENGTH_ENABLED, false);
    		editor.putBoolean(PrefNames.TIMER_ENABLED, false);
    		editor.putBoolean(PrefNames.PRIORITY_ENABLED, true);
    		editor.putBoolean(PrefNames.TAGS_ENABLED, false);
    		editor.putBoolean(PrefNames.STATUS_ENABLED, false);
    		editor.putBoolean(PrefNames.STAR_ENABLED, false);
    		editor.putBoolean(PrefNames.SUBTASKS_ENABLED, true);
    		editor.putBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTACTS_ENABLED, false);
    	}
    	else if (prefName.equals(PrefNames.PRESET_ADVANCED))
    	{
    		editor.putBoolean(PrefNames.PRESET_BASIC, false);
    		editor.putBoolean(PrefNames.PRESET_INTERMEDIATE, false);
    		editor.putBoolean(PrefNames.PRESET_ADVANCED, true);
    		editor.putBoolean(PrefNames.PRESET_POWER_USER, false);

            editor.putBoolean(PrefNames.CALENDAR_ENABLED, true);
    		editor.putBoolean(PrefNames.COLLABORATORS_ENABLED, true);
    		editor.putBoolean(PrefNames.REMINDER_ENABLED, true);
    		editor.putBoolean(PrefNames.FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTEXTS_ENABLED, true);
    		editor.putBoolean(PrefNames.GOALS_ENABLED, true);
    		editor.putBoolean(PrefNames.LOCATIONS_ENABLED, true);
    		editor.putBoolean(PrefNames.START_DATE_ENABLED, true);
    		editor.putBoolean(PrefNames.START_TIME_ENABLED, true);
    		editor.putBoolean(PrefNames.DUE_DATE_ENABLED, true);
    		editor.putBoolean(PrefNames.DUE_TIME_ENABLED, true);
    		editor.putBoolean(PrefNames.REPEAT_ENABLED, true);
    		editor.putBoolean(PrefNames.LENGTH_ENABLED, true);
    		editor.putBoolean(PrefNames.TIMER_ENABLED, true);
    		editor.putBoolean(PrefNames.PRIORITY_ENABLED, true);
    		editor.putBoolean(PrefNames.TAGS_ENABLED, true);
    		editor.putBoolean(PrefNames.STATUS_ENABLED, true);
    		editor.putBoolean(PrefNames.STAR_ENABLED, true);
    		editor.putBoolean(PrefNames.SUBTASKS_ENABLED, true);
    		editor.putBoolean(PrefNames.NOTE_FOLDERS_ENABLED, true);
    		editor.putBoolean(PrefNames.CONTACTS_ENABLED, true);
    	}
    	editor.commit();
    }
    
    // Update any task list widgets that may be displaying, including the Pure Calendar
    // widget if it's installed.
    static void updateWidgets()
    {
    	AppWidgetManager awm = AppWidgetManager.getInstance(context);
    	
    	ComponentName[] componentNames = new ComponentName[] {
    		new ComponentName(context,TaskListWidgetScrollable.class)
    	};
    	
    	Intent[] intents = new Intent[] {
    		new Intent(context,TaskListWidgetScrollable.class)
    	};
    	
    	for (int i=0; i<componentNames.length; i++)
    	{
    		int[] appWidgetIds = awm.getAppWidgetIds(componentNames[i]);
    		if (appWidgetIds.length>0)
    		{
    			Intent intent = intents[i];
    			intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
    			intent.putExtra("appWidgetIds",appWidgetIds);
    			context.sendBroadcast(intent);
    		}
    	}
    	
    	// Broadcast an intent that will be picked up by the Pure Calendar Widget if it is
    	// installed.
    	Intent pureCalIntent = new Intent();
    	pureCalIntent.setAction("com.customsolutions.android.utl.purecalendarprovider"+
    		".ACTION_UPDATE_TASKS");
    	if (context!=null)
    		context.sendBroadcast(pureCalIntent);
    }
    
    // Given a set of resource IDs (each of which is a subclass of TextView), store the data
    // in a Bundle.  This is used for saving state when an Activity has to be stopped and
    // restarted:
    static public void saveInstanceState(Activity a, int[] resIDs, Bundle b)
    {
    	for (int i=0; i<resIDs.length; i++)
    	{
    		TextView tv = (TextView)a.findViewById(resIDs[i]);
    		if (tv!=null)
    		{
    			b.putString(Integer.valueOf(resIDs[i]).toString(), tv.getText().toString());
    		}
    	}
    }
    
    // Given a set of resource IDs (each of which is a subclass of TextView), retreive
    // the data that was stored by the previous function.  This will update the Activity's
    // display.
    static public void restoreInstanceState(Activity a, int[] resIDs, Bundle b)
    {
    	if (b==null) return;
    	
    	for (int i=0; i<resIDs.length; i++)
    	{
    		TextView tv = (TextView)a.findViewById(resIDs[i]);
    		if (tv!=null)
    		{
    			if (b.containsKey(Integer.valueOf(resIDs[i]).toString()))
    				tv.setText(b.getString(Integer.valueOf(resIDs[i]).toString()));
    		}
    	}
    }

    // Given an EditText object, limit the number of characters:
    static public void limitNumChars(EditText et, int numChars)
    {
    	InputFilter[] filterArray = new InputFilter[1];
    	filterArray[0] = new InputFilter.LengthFilter(numChars);
    	et.setFilters(filterArray);
    }
        
    // Get a unique ID for the device (the android ID, or the WiFi MAC address if 
    // the android ID is not available).  Returns "" if not found.
    static public String getAndroidID()
    {
	    String androidID = Secure.getString(context.getContentResolver(), 
			android.provider.Settings.Secure.ANDROID_ID); 
	    if (androidID==null || invalidAndroidIDs.contains(androidID))
	    {
	    	// Try the Wi-Fi Mac Address:
	    	WifiManager manager = (WifiManager)context.getSystemService(Context.
	    		WIFI_SERVICE);
	    	if (manager!=null)
	    	{
	    		WifiInfo wifiInfo = manager.getConnectionInfo(); 
	    		if (wifiInfo!=null)
	    		{
	    			if (wifiInfo.getMacAddress()!=null)
	    				return(wifiInfo.getMacAddress());
	    		}
	    	}

	    	// Are we on the emulator?
	    	if ("google_sdk".equals(android.os.Build.PRODUCT) ||
	    		"sdk".equals(android.os.Build.PRODUCT) ||
	    		"generic".equals(android.os.Build.PRODUCT))
	    	{
	    		Log.i("Test","Emulator detected when getting Android ID");
	    		return "emulator";
	    	}
	    	
	    	// If we get here, we can't get anything.
    		return "";
	    }
	    return androidID;
    }
    
    // Convenience function to do regular expression matching (case insensitive):
    static public boolean regularExpressionMatch(String string, String pattern)
    {
    	Pattern pat = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE);
    	Matcher matcher = pat.matcher(string);
    	return matcher.find();
    }

    /** Perform a HTTPS get uing the HttpsURLonnection class
     * @param url - The web address
     * @param username - Username to use for logging in.  Set to null if none.
     * @param password - Password to use for logging in.  Set to null if none.
     * @return An instance of HttpResponseInfo, containing text from the server and other useful info.
     */
    static public HttpResponseInfo httpsGet(String url, String username, String password) throws IOException
    {
        HttpsURLConnection conn = null;
        try
        {
            // Open the connection to the server:
            conn = (HttpsURLConnection) (new URL(url)).openConnection();
            if (username!=null && password !=null)
            {
                conn.setRequestProperty("Authorization", "Basic " +
                    Base64.encodeToString((username+":"+password).getBytes("UTF-8"),Base64.NO_WRAP));
            }
            if (Build.VERSION.SDK_INT>=15 && Build.VERSION.SDK_INT<=18)
            {
                // There is a bug in these Android versions that can cause an EOFException to be thrown
                // if a POST follows this GET. This works around it.
                conn.setRequestProperty("Connection", "close");
            }
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setReadTimeout(60000);
            conn.setConnectTimeout(60000);
            conn.connect();

            // Read the response from the server:
            InputStream in;
            try
            {
                in = new BufferedInputStream(conn.getInputStream());
            }
            catch (java.io.FileNotFoundException e)
            {
                in = new BufferedInputStream(conn.getErrorStream());
            }
            Writer strWriter = new StringWriter();
            char[] buffer = new char[1024];
            try
            {
                Reader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1)
                {
                    strWriter.write(buffer, 0, n);
                }
            }
            finally
            {
                in.close();
            }

            HttpResponseInfo rInfo = new HttpResponseInfo();
            rInfo.text = strWriter.toString();
            rInfo.responseCode = conn.getResponseCode();
            rInfo.responseMessage = conn.getResponseMessage();
            rInfo.httpUrlConnection = conn;
            return rInfo;
        }
        finally
        {
            if (conn!=null)
                conn.disconnect();
        }
    }

    /** Perform an HTTPS post using the HttpsURLConnection class (listed as the preferred method
     * by Google).
     * @param url - The web address
     * @param params - The parameters to pass to the remote server.
     * @param username - Username to use for logging in.  Set to null if none.
     * @param password - Password to use for logging in.  Set to null if none.
     * @return An instance of HttpResponseInfo, containing text from the server and other useful info.
    */
    static public HttpResponseInfo httpsPost(String url, HashMap<String,String> params,
        String username, String password) throws IOException
    {
        HttpsURLConnection conn = null;
        try
        {
            // Open the connection to the server:
            conn = (HttpsURLConnection) (new URL(url)).openConnection();
            if (username!=null && password !=null)
            {
                conn.setRequestProperty("Authorization", "Basic " +
                    Base64.encodeToString((username+":"+password).getBytes("UTF-8"),Base64.NO_WRAP));
            }
            if (Build.VERSION.SDK_INT>=15 && Build.VERSION.SDK_INT<=18)
            {
                // There is a bug in these Android versions that can cause an EOFException to be thrown.
                // This works around it.
                conn.setRequestProperty("Connection", "close");
            }
            conn.setRequestMethod("POST");
            conn.setUseCaches(false);
            conn.setReadTimeout(60000);
            conn.setConnectTimeout(60000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setChunkedStreamingMode(0);

            // Convert the name/value pairs in the params argument into a string that can be sent
            // to the server.
            StringBuilder uploadString = new StringBuilder();
            boolean first = true;
            for (String key : params.keySet())
            {
                if (first)
                    first = false;
                else
                    uploadString.append("&");

                uploadString.append(URLEncoder.encode(key, "UTF-8"));
                uploadString.append("=");
                uploadString.append(URLEncoder.encode(params.get(key), "UTF-8"));
            }

            // Send the parameters:
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(uploadString.toString());
            writer.flush();
            writer.close();
            os.close();

            // Read the response from the server:
            InputStream in;
            try
            {
                in = new BufferedInputStream(conn.getInputStream());
            }
            catch (java.io.FileNotFoundException e)
            {
                in = new BufferedInputStream(conn.getErrorStream());
            }
            Writer strWriter = new StringWriter();
            char[] buffer = new char[1024];
            try
            {
                Reader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1)
                {
                    strWriter.write(buffer, 0, n);
                }
            }
            finally
            {
                in.close();
            }

            HttpResponseInfo rInfo = new HttpResponseInfo();
            rInfo.text = strWriter.toString();
            rInfo.responseCode = conn.getResponseCode();
            rInfo.responseMessage = conn.getResponseMessage();
            rInfo.httpUrlConnection = conn;
            return rInfo;
        }
        finally
        {
            if (conn!=null)
                conn.disconnect();
        }
    }
    
    /** Log an event to our server.
     * @param c - Current Context
     * @param statName - Required.  A short string representing the stat.
     * @param isError - Set to 1 if this is due to an error condition.  Else 0. 5/22/2020: This
     *                is no longer used.
     * @param extraData - Optional extra data to log. Up to 8 elements.  Set to null if unused.
     */
    static public void logEvent(Context c, String statName, int isError, String[] extraData)
    {
        Stats.recordStat(c,statName,extraData);
    }
    
    /** Log a one-time event to our server.
     * @param c - Current Context
     * @param statName - Required.  A short string representing the stat.
     * @param isError - Set to 1 if this is due to an error condition.  Else 0. 5/22/2020: This
     *                is no longer used.
     * @param extraData - Optional extra data to log. Up to 8 elements.  Set to null if unused.
     */
    static public void logOneTimeEvent(Context c, String statName, int isError, String[] extraData)
    {
    	String prefName = statName+"_logged";
    	if (!settings.contains(prefName))
    	{
    		Util.updatePref(prefName, true);
    		logEvent(c,statName,isError,extraData);
    	}
    }
    
    static private HashSet<String> eventHash = new HashSet<String>();
    /** Log an event that can be uploaded to our server one-time per run of the application.
     * @param c - Current Context
     * @param statName - Required.  A short string representing the stat.
     * @param isError - Set to 1 if this is due to an error condition.  Else 0
     * @param extraData - Optional extra data to log. Up to 8 elements.  Set to null if unused.
     */
    static public void logOneTimeEventPerRun(Context c, String statName, int isError, String[] extraData)
    {
    	if (eventHash.contains(statName))
    		return;
    	
    	eventHash.add(statName);
    	logEvent(c,statName,isError,extraData);
    }

    /** Ping the server.  This will record an event at the server once per day, and no more.
     * For compatibility with previous versions, 2 notifications are sent to the server: one
     * for the events table, and the other for the pings table. */
    static public void pingServer(Context c)
    {
        if (Api.DISABLE_BACKEND)
            return;

        SharedPreferences settings = c.getSharedPreferences(PREFS_NAME,0);
        long lastPingMS = settings.getLong(PrefNames.LAST_PING_TIME,0);
        if (lastPingMS==0)
        {
            recordPing2(c);
        }
        else
        {
            long midnightToday = Util.getMidnight(System.currentTimeMillis());
            long midnightPing = Util.getMidnight(lastPingMS);
            if (midnightToday>midnightPing)
                recordPing2(c);
        }
    }

    /** Send a ping after determining that one is needed. */
    static private void recordPing2(Context c)
    {
        logEvent(c,"ping",0,null);
        SharedPreferences prefs = c.getSharedPreferences(PREFS_NAME,0);
        prefs.edit().putLong(PrefNames.LAST_PING_TIME, System.currentTimeMillis()).apply();
        try
        {
            PackageInfo packageInfo = c.getPackageManager().getPackageInfo(
                c.getPackageName(),0);
            JSONObject toSend = new JSONObject()
                .put("install_id",prefs.getLong(PrefNames.INSTALL_ID,0))
                .put("version_code",packageInfo.versionCode);
            Api.postWithRetries("ping", toSend,(JSONObject j2) -> {
            });
        }
        catch (JSONException | PackageManager.NameNotFoundException e)
        {
            Util.handleException(Log.className(c),c,e);
        }
    }

    // Update the date format by looking at the system preference:
    static public void updateDateFormatFromSystem(Context c)
    {
        char[] formatItems;

        try
        {
            formatItems = android.text.format.DateFormat.getDateFormatOrder(c);
        }
        catch (IllegalArgumentException e)
        {
            // Some devices don't recognize an "E" in the system format.  Set a default rather
            // than crash.
            log("Got exception when reading date format from system: "+e.getLocalizedMessage());
            currentDateFormat = DATE_FORMAT_MONTH_FIRST;
            return;
        }

		if (formatItems!=null && formatItems.length>0)
		{
    		switch (formatItems[0])
    		{
    		case 'd': // date
    			currentDateFormat = DATE_FORMAT_DAY_FIRST;
    			log("Setting date format to day first.");
    			break;
    		case 'M':  // month
    			currentDateFormat = DATE_FORMAT_MONTH_FIRST;
    			log("Setting date format to month first.");
    			break;
    		case 'y':  // year
    			currentDateFormat = DATE_FORMAT_YEAR_FIRST;
    			log("Setting date format to year first.");
    			break;
    		default:
    			// The system is not cooperating.  Just set it to month first:
    			log("Invalid date format item.");
    			currentDateFormat = DATE_FORMAT_MONTH_FIRST;
    		}
		}
		else
		{
			// The system is not cooperating.  Just set it to month first:
			log("Got empty date format items array.");
			currentDateFormat = DATE_FORMAT_MONTH_FIRST;
		}    	
    }
    
    // Update the time format from the system:
    static public void updateTimeFormatFromSystem(Context c)
    {
    	if (android.text.format.DateFormat.is24HourFormat(c))
    		Util.currentTimeFormat = Util.TIME_PREF_24H;
    	else
    		Util.currentTimeFormat = Util.TIME_PREF_12H;
    }
    
    // Convenience function to set the theme at the beginning of an Activity:
    static void setTheme(Activity a)
    {
    	String s = settings.getString(PrefNames.FONT_SIZE, "medium").toLowerCase();
    	int fontIndex = 1;
        if (s.equals("tiny") || s.equals("small"))
            fontIndex = 0;
        if (s.equals("large") || s.equals("huge"))
            fontIndex = 2;
    	if (a instanceof UtlPopupActivity)
        {
            a.setTheme(POPUP_THEMES[settings.getInt(PrefNames.THEME, 0)][fontIndex]);
        }
    	else
        {
            a.setTheme(MAIN_THEMES[settings.getInt(PrefNames.THEME, 0)][fontIndex]);
        }
    	
    	// Whenever the theme is changed, the resources in use change, so the resource ID HashMap
    	// needs to be cleared.
    	if (resourceIdHash!=null)
    		resourceIdHash.clear();
    }

    /** Get a resource ID from an attribute in the current theme.  The attribute ID is like:
     * R.attr.xxxx. */
    static int resourceIdFromAttr(Context c, int attributeID)
    {
    	if (resourceIdHash!=null && resourceIdHash.containsKey(attributeID))
    		return resourceIdHash.get(attributeID);
    	
    	int[] attrs = new int[] { attributeID };
    	TypedArray ta = c.obtainStyledAttributes(attrs);
    	int result = ta.getResourceId(0, 0);
    	ta.recycle();
    	
    	// Maintain a HashMap to improve performance in future calls:
    	if (resourceIdHash==null)
    		resourceIdHash = new HashMap<Integer,Integer>();
    	resourceIdHash.put(attributeID, result);
    	
    	return result;
    }

    /** Convenience function to get a color from an attribute. */
    static int colorFromAttr(Context c, int attributeID)
    {
        /* This code sometimes gives the wrong color.
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(attributeID,typedValue,true);
        Util.log("Type: "+typedValue.type);
        return typedValue.data; */
        return c.getResources().getColor(Util.resourceIdFromAttr(c,attributeID));
    }
    
    // Check to see if one location fix is better than another:
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    public static boolean isBetterLocation(Location location, Location currentBestLocation) 
    {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
        // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }
    private static boolean isSameProvider(String provider1, String provider2) 
    {
        if (provider1 == null) {
            return provider2 == null;
          }
          return provider1.equals(provider2);
      }
    
    // Check to see if the database is locked by another thread.  If so, then wait
    // until it's free:
    public static void checkDatabaseLock(SQLiteDatabase db)
    {
    	int attempts = 50;
    	while (db.isDbLockedByOtherThreads() && attempts>0)
    	{
    		try
    		{
    			Thread.sleep(200);
    			attempts--;
    		}
    		catch (InterruptedException e) { }
    	}
    }
    
    // Get the current sync policy:
    public static int getSyncPolicy()
    {
    	AccountsDbAdapter db = new AccountsDbAdapter();
    	Cursor c = db.getAllAccounts();
    	while (c.moveToNext())
    	{
    		UTLAccount a = db.getUTLAccount(c);
    		if (a.sync_service==UTLAccount.SYNC_GOOGLE)
    		{
    			c.close();
    			return MINIMAL_SYNC;
    		}
    	}
    	c.close();
    	return SCHEDULED_SYNC;
    }
    
    // Start a sync if the minimum interval between syncs has passed:
    public static void syncIfNeeded(Context c)
    {
    	// Get the time of the next expected sync:
    	SharedPreferences settings = c.getSharedPreferences(Util.PREFS_NAME,0);
    	long intervalMillis = settings.getInt(PrefNames.SYNC_INTERVAL, 60) * 60000;
    	long nextSync = settings.getLong(PrefNames.LAST_AUTO_SYNC_TIME, 0)+intervalMillis;
    	
    	if (System.currentTimeMillis()>nextSync && settings.getInt(PrefNames.SYNC_INTERVAL,60)>0 &&
    		settings.getBoolean(PrefNames.AUTO_SYNC, true))
    	{
    		// We need to sync.  Create and send the intent to Synchronizer:
    		Intent i = new Intent(c, Synchronizer.class);
    		i.putExtra("command", "full_sync");
    		i.putExtra("is_scheduled", true);
            Synchronizer.enqueueWork(c,i);
    	}
    }
    
    // Call this in the onResume() function of key activities to run a sync when we
    // know the user is using the software.
    public static void doMinimalSync(Context c)
    {
    	if (getSyncPolicy()==MINIMAL_SYNC)
    		syncIfNeeded(c);
    }
    
    // Perform an instant upload of a task:
    public static void instantTaskUpload(Context c, UTLTask t, UTLAccount a)
    {
    	Intent i = new Intent(c, Synchronizer.class);
    	i.putExtra("command", "sync_item");
    	i.putExtra("item_type",Synchronizer.TASK);
    	i.putExtra("item_id",t._id);
    	i.putExtra("account_id", t.account_id);
    	if (a.sync_service==UTLAccount.SYNC_TOODLEDO && t.td_id>-1)
			i.putExtra("operation",Synchronizer.MODIFY);
		else if (a.sync_service==UTLAccount.SYNC_GOOGLE && t.remote_id!=null && 
			t.remote_id.length()>0)
			i.putExtra("operation",Synchronizer.MODIFY);
		else
			i.putExtra("operation",Synchronizer.ADD);
        Synchronizer.enqueueWork(c,i);
    }
    
    // Another variant of the instant upload:
    public static void instantTaskUpload(Context c, UTLTask t)
    {
    	UTLAccount a = (new AccountsDbAdapter()).getAccount(t.account_id);
    	if (a!=null)
    		instantTaskUpload(c,t,a);
    }

    /** Check to see if any accounts need the user to sign in again.  If so, prompt.
     * Returns true if a sign-in is needed.  Else false.*/
    public static boolean accountSignInCheck(Context con)
    {
        AccountsDbAdapter adb = new AccountsDbAdapter();
        Cursor c = adb.getAllAccounts();
        boolean signInNeeded = false;
        while (c.moveToNext())
        {
            UTLAccount a = adb.getUTLAccount(c);
            if (a.sync_service==UTLAccount.SYNC_TOODLEDO && a.sign_in_needed)
            {
                // Handler for pressing the OK button in the dialog (nothing to do):
                DialogInterface.OnClickListener dialogClickListener = new
                    DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                            if (which==DialogInterface.BUTTON_POSITIVE)
                            {
                                AlertDialog ad = (AlertDialog)dialog;
                                Intent i = new Intent(ad.getContext(),ToodledoLoginInfoV3.class);
                                i.putExtra("mode",ToodledoLoginInfoV3.SIGN_IN);
                                Long accountID = (Long) ad.getButton(DialogInterface.BUTTON_POSITIVE).getTag();
                                i.putExtra("account_id",accountID);
                                dialog.dismiss();
                                ad.getContext().startActivity(i);
                            }
                        }
                    };

                // Create and show the message dialog:
                AlertDialog.Builder builder = new AlertDialog.Builder(con);
                builder.setMessage(Util.getString(R.string.toodledo_sign_in_needed2)+" "+
                    a.td_email);
                builder.setPositiveButton(Util.getString(R.string.sign_in), dialogClickListener);
                builder.setNegativeButton(Util.getString(R.string.Cancel), dialogClickListener);
                AlertDialog ad = builder.show();
                ad.getButton(DialogInterface.BUTTON_POSITIVE).setTag(Long.valueOf(a._id));
                signInNeeded = true;
                Util.log("Displaying sign-in notice for account "+a.td_email);
                break;
            }
        }
        c.close();
        return signInNeeded;
    }

    // This function returns a valid reference to the database:
    public static SQLiteDatabase db()
    {	
    	// Wait for the semaphore allowing access to the database.  We will only have to wait
    	// if a backup is running.
		SQLiteDatabase db = dbHelper.getWritableDatabase();
		return db;
    }

    /** Open a file picker to choose a backup file. */
    static void openBackupFilePicker(Activity a)
    {
        new AlertDialog.Builder(a)
            .setMessage(R.string.choose_backup_file)
            .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    a.startActivityForResult(intent,BACKUP_FILE_PICKER_CODE);
                }
            })
            .show();
    }

    /** Check to see if the user hss the needed permissions to perform a backup and
     * restore.*/
    static boolean hasPermissionForBackup(Context c)
    {
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
            return true;

        // Check the basic permissions to read and write external storage.
        if (!Util.arePermissionsGranted(c,new String[]{Manifest.permission.
            READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}))
        {
            return false;
        }

        return true;
    }

    /** Get the modication time of the backup file. Returns 0 if none. */
    public static long getBackupFileModTime(Context co)
    {
        try
        {
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
            {
                SharedPreferences prefs = co.getSharedPreferences(Util.PREFS_NAME,0);
                if (prefs.contains(PrefNames.BACKUP_ROW_ID))
                {
                    // A prior backup has been made. Get its info:
                    Cursor cu = co.getContentResolver().query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[] { MediaStore.Downloads.DATE_MODIFIED },
                        MediaStore.Downloads._ID+"=?",
                        new String[] {String.valueOf(prefs.getInt(PrefNames.BACKUP_ROW_ID,0))},
                        null
                    );
                    if (cu==null || !cu.moveToFirst())
                    {
                        if (cu!=null) cu.close();
                        return 0;
                    }
                    else
                    {
                        long time = cu.getLong(0)*1000;
                        cu.close();
                        return time;
                    }
                }
                else
                    return 0;
            }
            else
            {
                File backupFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"/"+BACKUP_ZIP_FILE);
                if (!backupFile.exists())
                    return 0;
                else
                    return backupFile.lastModified();
            }
        }
        catch (Exception e)
        {
            // Likely a permissions issue.
            Log.d(TAG,"Can't get backup file time.",e);
            return 0;
        }
    }

    // Make a backup of all application data.  Returns an error message on failure,
    // or "" on success.
    private static boolean hasSem = false;
    public static String performBackup(Context c)
    {
    	// Close the connection to the database so all recent changes are saved:
    	Util.log("About to run backup.  Closing Database.  Permission to write external "+
            "storage? "+ContextCompat.checkSelfPermission(c,
            "android.permission.WRITE_EXTERNAL_STORAGE"));
    	hasSem = Util.acquireSemaphore("performBackup", c);
    	dbHelper.close();
    	
    	File backupDir = new File(getBackupRoot(c)+Util.BACKUP_DIR);
		if (!backupDir.isDirectory())
		{
			if (!backupDir.mkdirs())
			{
				// Could not create the backup directory.
				reopenDatabase(c);
				return Util.getString(R.string.Cannot_Create_Directory)+" "+
					backupDir.getAbsolutePath();
			}
		}
		
		// Make sure the destination is writable:
		if (!backupDir.canWrite())
		{
			reopenDatabase(c);
			return Util.getString(R.string.SDCard_Blocked);
		}
		
		// Create a directory to hold all database files (as of API 16, there may be more than 1):
		File databaseBackupDir = new File(backupDir.getAbsolutePath()+Util.
            DATABASE_BACKUP_DIR);
		if (!databaseBackupDir.isDirectory())
		{
			if (!databaseBackupDir.mkdirs())
			{
				// Could not create the backup directory.
				reopenDatabase(c);
				return Util.getString(R.string.Cannot_Create_Directory)+" "+
					databaseBackupDir.getAbsolutePath();
			}
		}
		
		try
		{
			// Loop through all existing files in the database backup directory and delete them:
			File[] fileArray = databaseBackupDir.listFiles();
			for (int i=0; i<fileArray.length; i++)
			{
				File oldFile = new File(databaseBackupDir.getAbsolutePath()+"/"+
					fileArray[i].getName());
				if (!oldFile.isDirectory())
				{
					oldFile.delete();
				}
			}
			
			// Loop through all files in the database directory and make copies:
			File databaseDir = c.getDatabasePath("utl").getParentFile();
			fileArray = databaseDir.listFiles();
			for (int i=0; i<fileArray.length; i++)
			{
                try
                {
                    File databaseFile = new File(databaseDir.getAbsolutePath() + "/" +
                        fileArray[i].getName());
                    if (!databaseFile.isDirectory())
                    {
                        File databaseBackupFile = new File(databaseBackupDir.getAbsolutePath() +
                            "/" + fileArray[i].getName());
                        FileInputStream input = new FileInputStream(databaseFile);
                        FileOutputStream output = new FileOutputStream(databaseBackupFile);
                        FileChannel src = input.getChannel();
                        FileChannel dst = output.getChannel();
                        dst.transferFrom(src, 0, src.size());
                        input.close();
                        output.close();
                    }
                }
                catch (FileNotFoundException e)
                {
                    // It is possible for a temporary database file to appear and disappear.  So, we just
                    // log a warning only.
                    Util.log("Daily Backup Warning: Got FileNotFoundException for "+fileArray[i].getName()+
                        ". "+e.getLocalizedMessage());
                }
			}
		}
		catch (Exception e)
		{
			reopenDatabase(c);
			return (Util.getString(R.string.Cannot_Copy_Database)+" "+e.getClass().
				getName()+": "+e.getLocalizedMessage());
		}

		// Database saving is done.  Even though we still have to copy preferences, we can still
		// update the pref to state that the backup is no longer running, and can reopen the database.
		reopenDatabase(c);

		// Copy the preferences:
		try
		{
			File prefsBackupFile = new File(backupDir.getAbsolutePath()+Util.PREFS_BACKUP_FILE);
			ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(
				prefsBackupFile));
			SharedPreferences prefs = c.getSharedPreferences("UTL_Prefs", 0);
			output.writeObject(prefs.getAll());
			output.close();
		}
		catch (Exception e)
		{
			return (Util.getString(R.string.Cannot_Copy_Prefs)+" "+e.getClass().
				getName()+": "+e.getLocalizedMessage());
		}

		try
        {
            // Zip the backup files, removing any zip file that may already exit:
            File zipFile = new File(getBackupRoot(c)+"/"+BACKUP_ZIP_FILE);
            zipFile.delete();
            new ZipFile(zipFile).addFolder(backupDir);
            Log.v(TAG,"Zip created with length "+zipFile.length());

            // Copy the file to the downloads directory:
            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
            {
                // Remove the existing backup in the download directory if it exists:
                SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
                if (prefs.contains(PrefNames.BACKUP_ROW_ID))
                {
                    int numDeleted = c.getContentResolver().delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.Downloads._ID+"=?",
                        new String[] {String.valueOf(prefs.getInt(PrefNames.BACKUP_ROW_ID,0))}
                    );
                    if (numDeleted==0)
                        Log.d(TAG,"Failed to delete old backup file.");
                    else
                        Log.v(TAG,"Old backup file delted.");
                }

                // Perform the copy:
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_ZIP_FILE);
                contentValues.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                contentValues.put(MediaStore.Downloads.IS_PENDING, true);
                Uri uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri itemUri = c.getContentResolver().insert(uri, contentValues);
                Log.v(TAG,"itemUri: "+itemUri);
                if (itemUri != null)
                {
                    OutputStream outputStream = c.getContentResolver().openOutputStream(
                        itemUri);
                    FileInputStream inputStream = new FileInputStream(zipFile);
                    byte[] buf = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buf)) > 0)
                        outputStream.write(buf,0,length);
                    outputStream.close();
                    inputStream.close();
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, false);
                    c.getContentResolver().update(itemUri, contentValues, null, null);
                    int newRowID = Integer.parseInt(itemUri. getLastPathSegment());
                    prefs.edit().putInt(PrefNames.BACKUP_ROW_ID,newRowID).apply();
                    Log.v(TAG,"New row ID of backup file: "+newRowID);
                }
                else
                    return c.getString(R.string.no_download_folder);
            }
            else
            {
                File fileInDownloads = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()+"/"+BACKUP_ZIP_FILE);
                if (fileInDownloads.exists())
                    fileInDownloads.delete();
                FileOutputStream output = new FileOutputStream(fileInDownloads);
                FileInputStream input = new FileInputStream(zipFile);
                FileChannel src = input.getChannel();
                FileChannel dst = output.getChannel();
                dst.transferFrom(src, 0, src.size());
                input.close();
                output.close();
            }
        }
		catch (Exception e)
        {
            Log.e(TAG,"Cannot create backup zip file","Cannot create backup zip file. ",e);
            return c.getString(R.string.unable_to_copy)+" "+e.getLocalizedMessage();
        }

		// If we get here, the backup was successful.
		return "";
    }
    
    // Reopen the database after it has been closed:
    public static void reopenDatabase(Context c)
    {
    	dbHelper = new DatabaseHelper(c);
    	if (hasSem)
    		_semaphore.release();
    	dbHelper.getWritableDatabase();
    	Util.log("Database has been re-opened.");
    }

    /** Get the root directory for backups.  The Util.BACKUP_DIR will be placed here. */
    public static String getBackupRoot(Context c)
    {
        return c.getExternalFilesDir(null).getAbsolutePath();
    }

    // Restore all application data from the backup. Returns an error message on failure,
    // or "" on success.  On success, the application should be restarted.
    public static String restoreDataFromBackup(Context c, Uri backupZipUri)
    {
        // If the zip file exists in the app's directory, remove it:
        File zipFile = new File(getBackupRoot(c)+"/"+BACKUP_ZIP_FILE);
        if (zipFile.exists())
            zipFile.delete();

        // Open the zip file chosen by the user anc copy it to the app's directory:
        try
        {
            // Copy the zip file to our app's directory:
            InputStream inputStream = c.getContentResolver().openInputStream(backupZipUri);
            FileOutputStream outputStream = new FileOutputStream(zipFile);
            byte[] buf = new byte[1024];
            int length;
            while ((length = inputStream.read(buf)) > 0)
                outputStream.write(buf,0,length);
            outputStream.close();
            inputStream.close();
            Log.v(TAG,"Successfully copied zip file to app directory.");
        }
        catch (Exception e)
        {
            Log.e(TAG,"Can't read from downloads","Can't read from downloads " +
                "media store.",e);
            return (c.getString(R.string.cant_access_backup)+" "+e.getLocalizedMessage());
        }

        // Remove any prior unzipped backup files:
        File backupDir = new File(getBackupRoot(c)+BACKUP_DIR);
        File prefsBackup = new File(backupDir.getAbsolutePath()+PREFS_BACKUP_FILE);
        if (prefsBackup.exists())
            prefsBackup.delete();
        File databaseBackupDir = new File(backupDir+DATABASE_BACKUP_DIR);
        if (databaseBackupDir.exists())
        {
            File[] fileArray = databaseBackupDir.listFiles();
            for (int i = 0; i < fileArray.length; i++)
            {
                File oldFile = new File(databaseBackupDir.getAbsolutePath() + "/" +
                    fileArray[i].getName());
                if (!oldFile.isDirectory())
                {
                    oldFile.delete();
                }
            }
        }

        // Unzip the backup zip file:
        try
        {
            new ZipFile(zipFile).extractAll(getBackupRoot(c));
        }
        catch (Exception e)
        {
            // Should not happen!
            Log.e(TAG,"Zip extraction failed","Couldn not extract zip contents when restoring.",
                e);
            return (Util.getString(R.string.unable_to_uncompress)+" "+e.getLocalizedMessage());
        }

    	// Make sure the preferences backup exists:
        prefsBackup = new File(backupDir.getAbsolutePath()+PREFS_BACKUP_FILE);
    	if (!prefsBackup.exists())
    	{
    	    Log.d(TAG,"preferences file does not exist.");
    		return (Util.getString(R.string.unable_to_uncompress));
    	}

        databaseBackupDir = new File(backupDir+DATABASE_BACKUP_DIR);
    	if (databaseBackupDir.exists() && databaseBackupDir.isDirectory())
    	{
			// Loop through all existing files in the app's database directory and delete them:
    		File appDatabaseDir = c.getDatabasePath("utl").getParentFile();
			File[] fileArray = appDatabaseDir.listFiles();
			for (int i=0; i<fileArray.length; i++)
			{
				File oldFile = new File(appDatabaseDir.getAbsolutePath()+"/"+
					fileArray[i].getName());
				if (!oldFile.isDirectory())
				{
					oldFile.delete();
				}
			}

			// Loop through all files in the database backup directory and make copies:
    		try
    		{
				fileArray = databaseBackupDir.listFiles();
				for (int i=0; i<fileArray.length; i++)
				{
					File sourceFile = new File(databaseBackupDir.getAbsolutePath()+"/"+
						fileArray[i].getName());
					if (!sourceFile.isDirectory())
					{
						File destFile = new File(c.getDatabasePath("utl").getParent()+"/"+
							fileArray[i].getName());
						FileChannel src = new FileInputStream(sourceFile).getChannel();
						FileChannel dst = new FileOutputStream(destFile).getChannel();
						dst.transferFrom(src, 0, src.size());
					}
				}
    		}
    		catch (Exception e)
			{
				return (Util.getString(R.string.Cannot_Copy_Database)+" "+e.getClass().
					getName()+": "+e.getMessage());
			}
    	}
    	else
    	{
            return (Util.getString(R.string.unable_to_uncompress));
    	}
		
    	// Get a hash containing preference values that should not be copied, because they could
    	// cause problems when restoring a backup made on a tablet.
    	HashSet<String> notRestored = new HashSet<String>();
    	for (int i=0; i<PREFS_NOT_RESTORED.length; i++)
    	{
    		notRestored.add(PREFS_NOT_RESTORED[i]);
    	}
    	
    	// Copy the preferences file:
		try
		{
			ObjectInputStream input = new ObjectInputStream(new FileInputStream(
				prefsBackup));
			Editor prefEdit = c.getSharedPreferences("UTL_Prefs", 0).edit();
			prefEdit.clear();
			Map<String, ?> entries = (Map<String, ?>) input.readObject();
			for (Entry<String, ?> entry : entries.entrySet())
			{
				Object v = entry.getValue();
                String key = entry.getKey();
                
                if (notRestored.contains(key))
                	continue;
                
                if (v instanceof Boolean)
                    prefEdit.putBoolean(key, ((Boolean) v).booleanValue());
                else if (v instanceof Float)
                    prefEdit.putFloat(key, ((Float) v).floatValue());
                else if (v instanceof Integer)
                    prefEdit.putInt(key, ((Integer) v).intValue());
                else if (v instanceof Long)
                    prefEdit.putLong(key, ((Long) v).longValue());
                else if (v instanceof String)
                    prefEdit.putString(key, ((String) v));
			}
			prefEdit.commit();
			input.close();
		}
		catch (Exception e)
		{
			return (Util.getString(R.string.Cannot_Copy_Prefs)+" "+e.getClass().
				getName()+": "+e.getMessage());
		}
				
		// If we get here, the restore was successful:
		return "";
    }
    
    // Get a list of email addresses corresponding to accounts on the phone.  This returns
    // a string containing the addresses separated by ; characters.  "" is returned
    // if no accounts are found.
    static String getDeviceEmails(Context c)
    {
    	AccountManager am = AccountManager.get(c);
    	Account[] accounts = am.getAccounts();
    	String result = "";
    	for (int i=0; i<accounts.length; i++)
    	{
    		String name = accounts[i].name;
    		if (Util.regularExpressionMatch(name, "\\@"))
    		{
    			// The name is probably an email address.
    			if (i>0) result += ";";
    			result += name;
    		}
    	}
    	return result;
    }
    
    // Convert preferences to version 3.0 (run one-time after an upgrade):
    static void convertPrefsToVersion3(SharedPreferences prefs)
    {
    	SharedPreferences.Editor editor = prefs.edit();
    	
    	// Auto Sync:
    	if (!prefs.contains(PrefNames.AUTO_SYNC))
    	{
    		if (prefs.getInt(PrefNames.SYNC_INTERVAL, 0)>0)
    			editor.putBoolean(PrefNames.AUTO_SYNC, true);
    		else
    		{
    			editor.putBoolean(PrefNames.AUTO_SYNC, false);
    			editor.putInt(PrefNames.SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL); 
    				// Set to valid value.  0 not allowed.
    		}
    	}
    	
    	// Font Size:
    	if (prefs.getString(PrefNames.FONT_SIZE, "medium").toLowerCase().equals("huge"))
    	{
    		editor.putString(PrefNames.FONT_SIZE, "large");
    	}
    	if (prefs.getString(PrefNames.FONT_SIZE, "medium").toLowerCase().equals("tiny"))
    	{
    		editor.putString(PrefNames.FONT_SIZE, "small");
    	}
    	
    	// Startup Screen:
    	if (!prefs.contains(PrefNames.STARTUP_VIEW_ID))
    	{
    		String topLevel = ViewNames.HOTLIST;
    		String viewTitle = Util.getString(R.string.Hotlist);
    		if (prefs.getString(PrefNames.STARTUP_SCREEN, "hotlist").equals("all_tasks"))
    		{
    			topLevel = ViewNames.ALL_TASKS;
    			viewTitle = Util.getString(R.string.AllTasks);
    		}
    		if (prefs.getString(PrefNames.STARTUP_SCREEN, "hotlist").equals("due_today_tomorrow"))
    		{
    			topLevel = ViewNames.DUE_TODAY_TOMORROW;
    			viewTitle = Util.getString(R.string.DueTodayTomorrow);
    		}
    		if (prefs.getString(PrefNames.STARTUP_SCREEN, "hotlist").equals("overdue"))
    		{
    			topLevel = ViewNames.OVERDUE;
    			viewTitle = Util.getString(R.string.Overdue);
    		}
    		if (prefs.getString(PrefNames.STARTUP_SCREEN, "hotlist").equals("starred"))
    		{
    			topLevel = ViewNames.STARRED;
    			viewTitle = Util.getString(R.string.Starred);
    		}
    		if (prefs.getString(PrefNames.STARTUP_SCREEN, "hotlist").equals("recently_completed"))
    		{
    			topLevel = ViewNames.RECENTLY_COMPLETED;
    			viewTitle = Util.getString(R.string.RecentlyCompleted);
    		}
    		
    		ViewsDbAdapter vdb = new ViewsDbAdapter();
    		Cursor c = vdb.getView(topLevel,"");
    		if (c.moveToFirst())
    		{
    			editor.putLong(PrefNames.STARTUP_VIEW_ID, Util.cLong(c, "_id"));
    			editor.putString(PrefNames.STARTUP_VIEW_TITLE, viewTitle);
    		}
    		else
    		{
    			editor.putLong(PrefNames.STARTUP_VIEW_ID, -1);
    			editor.putString(PrefNames.STARTUP_VIEW_TITLE, "");
    		}
    	}
    	
    	// When upgrading, we don't create a sample task since the user is familiar with the app:
    	editor.putBoolean(PrefNames.CREATE_SAMPLE_TASK, false);
    	
    	editor.putBoolean(PrefNames.CONVERSION_TO_V3_COMPLETED, true);
    	
    	editor.commit();
    	
    	PurchaseManager pm = new PurchaseManager(context);
    	int stat = pm.stat();
    	if (stat==PurchaseManager.SHOW_ADS)
    	{
    		// When converting to version 3, the free trial is reset:
    		pm.recordInstallDate(System.currentTimeMillis());
    	}
    }
    
    /** Go to the task list that should run at startup (assumes there are no licensing issues). */
    @SuppressLint("InlinedApi")
	static void openStartupView(Context con)
    {
    	// Get an Intent to take us to the startup screen, based on the user's preference:
        Long startupViewID = Util.settings.getLong(PrefNames.STARTUP_VIEW_ID, -1);
    	Intent i = new Intent(con,TaskList.class);
    	if (startupViewID==-1)
    	{
    		// Not set yet.  Use the hotlist by default.
    		i.putExtra("title", Util.getString(R.string.Hotlist));
            i.putExtra("top_level", "hotlist");
            i.putExtra("view_name", "");
    	}
    	else
    	{
    		ViewsDbAdapter vdb = new ViewsDbAdapter();
    		Cursor c = vdb.getView(startupViewID);
    		if (c.moveToFirst())
    		{
    			i.putExtra("title", Util.settings.getString(PrefNames.STARTUP_VIEW_TITLE, ""));
    			i.putExtra("top_level",Util.cString(c, "top_level"));
    			i.putExtra("view_name",Util.cString(c, "view_name"));
    		}
    		else
    		{
    			// The view must have been deleted.  Just start at the hotlist.
    			i.putExtra("title", Util.getString(R.string.Hotlist));
                i.putExtra("top_level", "hotlist");
                i.putExtra("view_name", "");
    		}
    		c.close();
    	}
    	
    	// Start the Activity as a new task, which means pressing back from the Activity will cause the
    	// app to exit.
    	if (Build.VERSION.SDK_INT >= 11) 
		{
			i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		}
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		// Reset the selected node and scroll position in the navigation drawer:
		NavDrawerFragment._selectedNodeIndex = -1;

    	con.startActivity(i);
    }
    
    /** Get the minimum sync interval allowed (minutes).  This is based on the accounts in use. */
    @SuppressWarnings("unused")
	public static int getMinSyncInterval(Context con)
    {
    	AccountsDbAdapter adb = new AccountsDbAdapter();
    	SharedPreferences settings = con.getSharedPreferences(Util.PREFS_NAME, 0);
    	
    	if (settings.getBoolean(PrefNames.REDUCED_SYNC_INTERVAL, false))
    	{
    		// The secret code for a reduced sync interval has been entered.
    		return 5;
    	}
    	
    	Cursor c = adb.getAllAccounts();
    	c.moveToPosition(-1);
    	boolean hasToodledo = false;
    	boolean hasGoogle = false;
    	while (c.moveToNext())
    	{
    		UTLAccount a = adb.getUTLAccount(c);
    		if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    			hasToodledo = true;
    		if (a.sync_service==UTLAccount.SYNC_GOOGLE)
    			hasGoogle = true;
    	}
    	c.close();
    	
    	if (hasToodledo && !hasGoogle)
    		return MIN_SYNC_INTERVAL_TOODLEDO;
    	else if (!hasToodledo && hasGoogle)
    		return MIN_SYNC_INTERVAL_GOOGLE;
    	else
    	{
    		// The minimum sync interval is the maximum of Toodledo and Google:
    		if (MIN_SYNC_INTERVAL_GOOGLE>MIN_SYNC_INTERVAL_TOODLEDO)
    			return MIN_SYNC_INTERVAL_GOOGLE;
    		else
    			return MIN_SYNC_INTERVAL_TOODLEDO;
    	}
    }
    
    /** A saved instance of SpeechRecognizer. */
    static private SpeechRecognizer _speechRecognizer;
    
    /** Get a SpeechRecognizer instance: */
    static public SpeechRecognizer getSpeechRecognizer()
    {
    	if (_speechRecognizer==null)
    	{
    		_speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.
    			getApplicationContext());
    		return _speechRecognizer;
    	}
    	else
    		return _speechRecognizer;
    }
    
    static public void destroySpeechRecognizer()
    {
    	if (_speechRecognizer!=null)
    	{
    		try
    		{
    			_speechRecognizer.destroy();
    		}
    		catch (IllegalArgumentException e) 
    		{ 
    			// This should not happen, but I saw it in our logs once.  It might have been due to
    			// a poor internet connection.
    			Util.log("VoiceCommand: Got IllegalArgumentException when trying to destroy SpeechRecognizer. "+
    				e.getMessage());
    		}
    	}
    	_speechRecognizer = null;
    }

    /** Determine if Android Wear is available.  This is used to avoid executing code that should not be
     * run when there is no smartwatch connection.  This will return true if the Wear add-on is not
     * purchased */
    static public boolean canUseAndroidWear()
    {
        if (!Util.IS_GOOGLE)
            return false;

        if (Build.VERSION.SDK_INT<18)
            return false;

        return true;
    }

    /** Schedule the android wear daily summary notifications. */
    static public void scheduleWearDailySummaries(Context c)
    {
        SharedPreferences settings = c.getSharedPreferences(Util.PREFS_NAME,0);
        AlarmManager a = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        PurchaseManager pm = new PurchaseManager(c);

        // This Intent triggers the showing of the summary:
        Intent summaryIntent = new Intent(c, WearService.class);
        summaryIntent.setAction(WearService.ACTION_SHOW_DAILY_SUMMARY);
        PendingIntent summaryPendingIntent;
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O)
        {
            summaryPendingIntent = PendingIntent.getService(c, 0, summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        else
        {
            summaryPendingIntent = PendingIntent.getForegroundService(c, 0, summaryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        if (settings.getBoolean(PrefNames.WEAR_SHOW_DAILY_SUMMARY, false) &&
            pm.isPurchased(PurchaseManager.SKU_ANDROID_WEAR))
        {
            long nextTime = Util.getMidnight(System.currentTimeMillis())+settings.getLong(PrefNames.
                WEAR_SUMMARY_TIME,0);
            if (nextTime<System.currentTimeMillis())
            {
                // Show the first one tomorrow instead.
                nextTime = Util.getMidnight(System.currentTimeMillis()+ONE_DAY_MS)+settings.getLong(
                    PrefNames.WEAR_SUMMARY_TIME,0);
            }

            // If the current time zone is different than the home time zone, the
            // notification time needs to be offset.  The home time zone is the time zone
            // that was in effect when the app was installed.
            nextTime = timeShift(nextTime,null,TimeZone.getDefault());

            // Schedule the notifications:
            a.setRepeating(AlarmManager.RTC_WAKEUP,nextTime,Util.ONE_DAY_MS,summaryPendingIntent);
            log("Next daily wear summary scheduled for "+Util.getDateTimeString(nextTime));
        }
        else
        {
            // Cancel any pending notifications, if they exist.
            a.cancel(summaryPendingIntent);
        }
    }

    /** Distribute values of the sort_order field within a parent task in a Google account.
     * @param parentID The UTL task ID of the parent task. */
    static void distributeChildSortOrder(long parentID)
    {
        TasksDbAdapter tasksDB = new TasksDbAdapter();

        // Get the number of children:
        Cursor c = db().rawQuery("select count(*) from "+TasksDbAdapter.TABLE+" where parent_id=?",
            new String[] {String.valueOf(parentID)});
        c.moveToFirst();
        int numChildren = c.getInt(0);
        c.close();
        if (numChildren==0) return;

        // Calculate the difference between sort_order values:
        long diff = Long.MAX_VALUE/(numChildren+1);
        long nextValue = 0;

        c = tasksDB.queryTasks("parent_id="+parentID,"position desc");
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);
            nextValue += diff;
            t.sort_order = nextValue;
            tasksDB.modifyTask(t);
        }
        c.close();
    }

    /** Distribute the values of the sort_order field for all Google tasks in a folder.
     * @param folderID The UTL folder ID. */
    static void distributeSortOrderInFolder(long folderID)
    {
        TasksDbAdapter tasksDB = new TasksDbAdapter();

        // Loop through all tasks in the folder. Get the first sort_order, last sort_order,
        // minimum sort_order, and maximum sort_order.  We loop according to Google's
        // position field.
        long firstSortOrder = -1;
        long lastSortOrder = -1;
        long maxSortOrder = 0;
        long minSortOrder = Long.MAX_VALUE;
        int maxSortOrderIndex = -1;
        int numTasks = 0;
        Util.log("Distribute: Distributing tasks in folder ID "+folderID);
        Cursor c = tasksDB.queryTasks("folder_id="+folderID+" and parent_id=0","position asc");
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);
            Util.log("Distribute: "+t.completed+" "+t.position+" "+t.title);

            if (firstSortOrder==-1)
                firstSortOrder = t.sort_order;

            lastSortOrder = t.sort_order;

            if (t.sort_order>maxSortOrder)
            {
                maxSortOrder = t.sort_order;
                maxSortOrderIndex = numTasks;
            }

            if (t.sort_order<minSortOrder)
            {
                minSortOrder = t.sort_order;
            }

            numTasks++;
        }
        c.close();

        if (numTasks==0) return;

        // These values will determine the range we distribute sort_order over.
        long distributeTop = Long.MAX_VALUE;
        long distributeBottom = 0;

        if (firstSortOrder > lastSortOrder)
        {
            // We will distribute between the first and last values.
            distributeTop = firstSortOrder;
            distributeBottom = lastSortOrder;
        }
        else
        {
            if (maxSortOrder>minSortOrder)
            {
                // We will distribute between the minimum and maximum, inclusive.
                distributeTop = maxSortOrder;
                distributeBottom = minSortOrder;
            }
            else if (maxSortOrder==minSortOrder && maxSortOrder>0)
            {
                // We will distribute between this value and either zero or Long.MAX_VALUE,
                // depending on the location in the list.
                if (maxSortOrderIndex<(numTasks/2))
                {
                    distributeTop = maxSortOrder;
                    distributeBottom = 0;
                }
                else
                {
                    distributeTop = Long.MAX_VALUE;
                    distributeBottom = maxSortOrder;
                }
            }
        }

        // Calculate the difference between the sort_order values.  We need to set this up so
        // that no task has a value of Long.MAX_VALUE or zero.
        long firstValue;
        long increment;
        if (distributeTop<Long.MAX_VALUE && distributeBottom>0)
        {
            increment = (distributeTop-distributeBottom)/(numTasks-1);
            firstValue = distributeTop;
        }
        else if (distributeTop==Long.MAX_VALUE && distributeBottom>0)
        {
            increment = (distributeTop-distributeBottom)/numTasks;
            firstValue = Long.MAX_VALUE-increment;
        }
        else if (distributeTop<Long.MAX_VALUE && distributeBottom==0)
        {
            increment = (distributeTop-distributeBottom)/numTasks;
            firstValue = distributeTop;
        }
        else
        {
            // distributeTop==Long.MAX_VALUE && distributeBottom==0
            increment = Long.MAX_VALUE/(numTasks+1);
            firstValue = Long.MAX_VALUE-increment;
        }

        // Update the tasks:
        c = tasksDB.queryTasks("folder_id="+folderID+" and parent_id=0","position asc");
        c.moveToPosition(-1);
        long currentValue = firstValue;
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);
            t.sort_order = currentValue;
            tasksDB.modifyTask(t);
            currentValue -= increment;
        }
        c.close();
    }

    /** Distribute the values of the sort_order field for an entire account. This can be called
     * to resolve an issue in which it's possible for this field to reach Long.MAX_VALUE, which
     * breaks any further sorting. */
    static public void distributeSortOrderInAccount(long accountID)
    {
        // Get a count of the number of tasks in the account that aren't subtasks.
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = tasksDB.queryTasks("account_id="+accountID+" and parent_id=0 and sort_order>0",
            "sort_order desc");
        c.moveToPosition(-1);
        long numTasks = c.getCount();
        Log.v(TAG,"Updating sort order for this many tasks: "+numTasks);

        // We will distribute values evenly. Use the number of tasks to calculate the interval
        // between values.
        long interval = Long.MAX_VALUE / (numTasks+1);

        // Update all tasks with the new values:
        long sortOrder = Long.MAX_VALUE;
        while (c.moveToNext())
        {
            UTLTask t = tasksDB.getUTLTask(c);
            sortOrder -= interval;
            if (sortOrder < 0)
                sortOrder = 0;
            t.sort_order = sortOrder;
            t.mod_date = System.currentTimeMillis();
            tasksDB.modifyTask(t);
        }
        c.close();
    }

    /** This is run one time during an upgrade to version 3.5.  It sets the initial values for
     * the sort_order field based on the values in Google's position field.  This can only be done
     * for Google accounts.
     */
    static public void distrbuteSortOrderOnUpgrade()
    {
        AccountsDbAdapter adb = new AccountsDbAdapter();
        TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = adb.getAllAccounts();
        while (c.moveToNext())
        {
            UTLAccount a = adb.getUTLAccount(c);
            if (a.sync_service == UTLAccount.SYNC_GOOGLE)
            {
                // Set sort_order values for children within parent tasks.
                Cursor c2 = tasksDB.queryTasks("account_id=" + a._id, null + " and parent_id>0");
                HashSet<Long> parents = new HashSet<Long>();
                c2.moveToPosition(-1);
                while (c2.moveToNext())
                {
                    UTLTask t = tasksDB.getUTLTask(c2);
                    parents.add(t.parent_id);
                }
                c2.close();
                Iterator<Long> it = parents.iterator();
                while (it.hasNext())
                {
                    Util.distributeChildSortOrder(it.next());
                }

                // Set the sort_order for tasks at the top level within folders:
                c2 = (new FoldersDbAdapter()).queryFolders("account_id=" + a._id, null);
                while (c2.moveToNext())
                {
                    Util.distributeSortOrderInFolder(Util.cLong(c2, "_id"));
                }
                c2.close();
            }
        }
        c.close();
    }

    /** Pause the current thread, without worrying about interruptions. */
    static public void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) { }
    }

    /** Get a String that shows keys and values in a Bundle. */
    public static String bundleToString(Bundle b, int indentSpaces)
    {
        String indent = "";
        for (int i=0; i<indentSpaces; i++)
            indent += " ";

        if (b==null) return "";
        String result = "";

        Set<String> keySet = b.keySet();
        for (String key : keySet)
        {
            if (b.get(key)!=null && b.get(key).getClass()!=null)
            {
                result += "\n" + indent + "- " + key + ": " + b.get(key) + " (" +
                    b.get(key).getClass().getName() + ")";
            }
            else
                result += "\n" + indent + "- " + key + ": " + b.get(key);
            if (b.get(key) != null)
            {
                if (b.get(key).getClass().getName().equals("android.os.Bundle"))
                {
                    result += bundleToString(b.getBundle(key), indentSpaces + 2);
                }
                if (b.get(key).getClass().getName().equals("[Ljava.lang.String;"))
                {
                    String[] strings = b.getStringArray(key);
                    for (String s : strings)
                    {
                        result += "\n"+indent+"  - "+s;
                    }
                }
            }
        }
        return result;
    }

    /** Get a String that contains all attributes of an Intent. */
    public static String intentToString(Intent intent, int indentSpaces)
    {
        String indent = "";
        for (int i=0; i<indentSpaces; i++)
            indent += " ";

        if (intent==null) return "";
        String result = "";

        if (intent.getComponent()!=null)
            result += "\n"+indent+"- Component: "+intent.getComponent().flattenToString();

        if (intent.getAction()!=null && intent.getAction().length()>0)
            result += "\n"+indent+"- Action: "+intent.getAction();

        if (intent.getDataString()!=null && intent.getDataString().length()>0)
            result += "\n"+indent+"- Uri: "+intent.getDataString();

        if (intent.getFlags()!=0)
            result += "\n"+indent+"- Flags: "+String.format("0x%08X",intent.getFlags());

        if (intent.getType()!=null && intent.getType().length()>0)
            result += "\n"+indent+"- Type: "+intent.getType();

        if (intent.getCategories()!=null)
        {
            result += "\n"+indent+"- Categories:";
            for (String c : intent.getCategories())
            {
                result += "\n"+indent+"  - "+c;
            }
        }

        try
        {
            if (intent.getExtras() != null && intent.getExtras().keySet().size() > 0)
            {
                result += "\n" + indent + "- Extras:";
                result += bundleToString(intent.getExtras(), indentSpaces + 2);
            }
        }
        catch (BadParcelableException e)
        {
            // I saw this getting thrown once.  I don't know why but the best solution is to
            // just ignore this since this is only a logging function.
        }

        return result;
    }

    /** Check to see if the app is in a state that allows for the tasker plugin to be used. Returns
     * true if the plugin is available, else false. If the plugin is not available, a dialog
     * explaining why is displayed. Once that dialog is closed, the Activity will have finish()
     * called.*/
    static public boolean isTaskerPluginAvailable(final UtlActivity a)
    {
        // Make sure at least one account is set up.
        AccountsDbAdapter adb = new AccountsDbAdapter();
        if (adb.getNumAccounts()==0)
        {
            new AlertDialog.Builder(a)
                .setMessage(R.string.please_run_utl_first)
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        a.finish();
                    }
                })
                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        a.finish();
                    }
                })
                .show();
            return false;
        }

        // Make sure the plugin has been purchased.
        PurchaseManager pm = new PurchaseManager(a);
        if (!pm.isPurchased(PurchaseManager.SKU_TASKER))
        {
            new AlertDialog.Builder(a)
                .setMessage(R.string.tasker_plugin_needed)
                .setOnCancelListener(new DialogInterface.OnCancelListener()
                {
                    @Override
                    public void onCancel(DialogInterface dialog)
                    {
                        a.finish();
                    }
                })
                .setPositiveButton(R.string.learn_more, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // Start the Activity that shows more information and allows the user
                        // to purchase.
                        Intent infoIntent = new Intent(a,StoreItemDetail.class);
                        infoIntent.putExtra("sku",PurchaseManager.SKU_TASKER);
                        a.startActivity(infoIntent);
                        a.finish();
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        a.finish();
                    }
                })
                .show();
            return false;
        }

        return true;
    }

    /** Convert a list of old Apache BasicNameValuePair objects to a HashMap of key/value
     * strings. */
    public static HashMap<String,String> nameValuePairToHashMap(ArrayList<BasicNameValuePair> pairs)
    {
        HashMap<String,String> map = new HashMap<>();
        for (BasicNameValuePair pair : pairs)
        {
            map.put(pair.getName(),pair.getValue());
        }
        return map;
    }

	/** Get an array containing all dangerous permissions that the user must approve at run time.
	 * This will exclude permissions such as
	 * SYSTEM_ALERT_WINDOW which require the user to go to a separate screen provided by the
	 * system, and will exclude permissions that are not appropriate for the device's API level.
	 */
	static public ArrayList<String> getAllPermissions()
	{
		ArrayList<String> result = new ArrayList<>();
		try
		{
			PackageManager pm = context.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(context.getPackageName(),PackageManager.GET_PERMISSIONS);
			if (pi.requestedPermissions!=null)
			{
				for (String permission : pi.requestedPermissions)
				{
					PermissionInfo permInfo;
					try
					{
						permInfo = pm.getPermissionInfo(permission,0);
					}
					catch (PackageManager.NameNotFoundException e)
					{
						continue;
					}
					if ((permInfo.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)!=
						PermissionInfo.PROTECTION_DANGEROUS)
					{
						// Normal permission that is granted automatically.
						continue;
					}
					if (permission.equals(Manifest.permission.SYSTEM_ALERT_WINDOW))
					{
						// Requires the app to launch a separate activity to approve this.
						continue;
					}
					if (Build.VERSION.SDK_INT<Build.VERSION_CODES.O && permission.equals(
						Manifest.permission.ANSWER_PHONE_CALLS))
					{
						// Only applicable to Oreo (API 26) and up.
						continue;
					}
					result.add(permission);
				}
			}
		}
		catch (PackageManager.NameNotFoundException e)
		{
			Util.log("Can't find my own package!");
		}
		return result;
	}

    /** Set an exact Alarm. This calls the appropriate function based on API level. */
    static void setExactAlarm(Context c, PendingIntent pi, long timestamp)
    {
        AlarmManager a = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            a.setExact(AlarmManager.RTC_WAKEUP, timestamp, pi);
        else
        {
            try
            {
                a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pi);
            }
            catch (SecurityException e)
            {
                // This can happen on Android 12 and up if the user hasn't granted the permission
                // for exact alarms.
                a.set(AlarmManager.RTC_WAKEUP,timestamp,pi);
            }
        }
    }

    /** Get the maximum number of icons allowed on a toolbar displayed in portrait mode.
     * Assumes that each icon uses 60dp of space. */
    static int getMaxToolbarIcons(Activity a)
    {
        Display display = a.getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        float widthInDp = metrics.widthPixels / metrics.density;
        float widthInInches = metrics.widthPixels/metrics.xdpi;
        float pixelsPerIcon = 60*metrics.density;
        float inchesPerIcon = pixelsPerIcon/metrics.xdpi;
        double maxIcons = widthInInches/inchesPerIcon;
        Util.log("Screen width in pixels: "+metrics.widthPixels+"; Logical Density: "+metrics.
            density+ "; Width in dp: "+widthInDp+"; Actual Density: "+metrics.xdpi+
            "; Width in inches: "+widthInInches+"; Max Icons: "+maxIcons);
        return Double.valueOf(Math.floor(maxIcons)).intValue();
    }

    /** Get a GoogleSignInClient for a particular Google email address. */
    static GoogleSignInClient getGoogleSignInClient(Context c, String email)
    {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.
            DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(new Scope(TasksScopes.TASKS))
            .setAccountName(email)
            .build();
        return GoogleSignIn.getClient(c,gso);
    }

    /** Convenience function to determine if a String is not null and has non-zero length. */
    public static boolean isValid(String s)
    {
        if (s!=null && s.length()>0)
            return true;
        else
            return false;
    }

    /** Get an instance of the OkHttp client, creating it if necessary. */
    public static OkHttpClient client()
    {
        if (_httpClient==null)
        {
            _httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        }
        return _httpClient;
    }

    /** Handle an Exception that should never occur. */
    public static void handleException(String tag, Context c, Exception e)
    {
        Log.e(tag,tag+" - Unexpected "+e.getClass().getSimpleName(),"Got an Exception that should "+
            "never occur.",e);
    }

    /** Handle an Exception that should never occur. */
    public static void handleException(String tag, Exception e)
    {
        Log.e(tag,tag+" - Unexpected "+e.getClass().getSimpleName(),"Got an Exception that should "+
            "never occur.",e);
    }

    /** Get the country the user is located in, if possible. Returns a 2 digit country code or
     * the word "unknown". */
    public static String getUserCountry(Context c)
    {
        String country;

        // Start by checking the telephony data:
        try
        {
            TelephonyManager telephonyManager = (TelephonyManager)c.getSystemService(Context.
                TELEPHONY_SERVICE);
            country = telephonyManager.getNetworkCountryIso();
            if (!Util.isValid(country))
                country = telephonyManager.getSimCountryIso();
            if (!Util.isValid(country))
                country = "unknown";
        }
        catch (Exception e)
        {
            country = "unknown";
        }

        if (country.equals("unknown"))
        {
            // The telephony data wasn't helpful. Try checking the locale:
            Log.d(Log.className(c),"Can't get country from TelephonyManager. Using Locale.");
            Locale locale = Locale.getDefault();
            if (locale!=null)
            {
                country = locale.getCountry();
                if (!Util.isValid(country))
                    country = "unknown";
            }
        }

        Log.v(Log.className(c),"User's Country: "+country);
        return country.toLowerCase();
    }

    /** Get the JSONObject containing log information for uploading. */
    public static JSONObject getLogData(Context c)
    {
        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
        try
        {
            // Construct a JSON object containing information about the user's device:
            JSONObject deviceInfo = new JSONObject();
            deviceInfo.put("install_id", prefs.getLong(PrefNames.INSTALL_ID, 0));
            try
            {
                PackageInfo packageInfo = c.getPackageManager().getPackageInfo(c.getPackageName(),
                    0);
                deviceInfo.put("version_name",packageInfo.versionName);
                deviceInfo.put("version_code",packageInfo.versionCode);
            }
            catch (PackageManager.NameNotFoundException e)
            {
                Util.handleException(Log.className(c),c,e);
                return null;
            }
            deviceInfo.put("android_id",Util.getAndroidID());
            deviceInfo.put("model",Build.MODEL);
            deviceInfo.put("api_level",Build.VERSION.SDK_INT);
            deviceInfo.put("android_version",Build.VERSION.RELEASE);

            // Construct a JSON object containing information about the user's settings:
            JSONObject settingsInfo = new JSONObject();
            Map<String, ?> entries = prefs.getAll();
            SortedSet<String> keys = new TreeSet<String>(entries.keySet());
            for (String key : keys)
            {
                Object val = entries.get(key);
                settingsInfo.put(key,val.toString());
            }

            // Add items to the settings containing the user's Android version and API
            // level.
            settingsInfo.put("android_api_level",Integer.valueOf(Build.VERSION.SDK_INT).
                toString());
            settingsInfo.put("android_version",Build.VERSION.RELEASE);

            // Construct a JSON object containing the log information.
            Log.v(TAG,"Getting log entries.");
            int count=0;
            JSONArray logArray = new JSONArray();
            Cursor c2 = Util.db().rawQuery("select timestamp, priority, tag, message from "+
                "log_data order by timestamp asc",null);
            StringBuilder builder = null;
            while (c2.moveToNext())
            {
                count++;
                if (c2.getPosition() % 100 == 0)
                {
                    if (builder!=null && builder.length()>0)
                        logArray.put(builder.toString());
                    builder = new StringBuilder(1024);
                    builder.append("insert into activity_log_entries (log_id,timestamp,"+
                        "level,tag,message) values ");
                }
                else
                    builder.append(",");

                builder.append("(LOG_ID,");
                builder.append(c2.getLong(0));
                builder.append(",");
                builder.append(c2.getInt(1));
                builder.append(",'");
                builder.append(c2.getString(2).replaceAll(Pattern.quote("\\"),"\\\\")
                    .replaceAll("'","\\\\'"));
                builder.append("','");
                builder.append(c2.getString(3).replaceAll(Pattern.quote("\\"),"\\\\")
                    .replaceAll("'","\\\\'"));
                builder.append("')");
            }
            c2.close();
            if (builder!=null && builder.length()>0)
                logArray.put(builder.toString());
            Log.v(TAG,"Entries found: "+count);

            // Put the object and array created so far into a single JSON object.
            JSONObject uploadObject = new JSONObject();
            uploadObject.put("log_info",deviceInfo);
            uploadObject.put("prefs_info",settingsInfo);
            uploadObject.put("log_data",logArray);
            return uploadObject;
        }
        catch (JSONException e)
        {
            Util.handleException(Log.className(c),c,e);
            return null;
        }
    }

    /** Get an ID of this device, which can be used to pass to AdMob to get test ads. */
    public static String getAdmobTestID(Context c)
    {
        String androidID = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.
            ANDROID_ID);
        return md5(androidID).toUpperCase();
    }

    public static String md5(String md5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes("UTF-8"));
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        } catch(UnsupportedEncodingException ex){
        }
        return null;
    }

    /** Get a ColorStateList object with only one color. */
    static public ColorStateList getColorStateList(int color)
    {
        int[][] states = new int[][] {
            new int[] { android.R.attr.state_enabled}, // enabled
            new int[] {-android.R.attr.state_enabled}, // disabled
            new int[] {-android.R.attr.state_checked}, // unchecked
            new int[] { android.R.attr.state_pressed}  // pressed
        };
        int[] colors = new int[] {
            color,
            color,
            color,
            color
        };
        return new ColorStateList(states,colors);
    }

    /** Check to see if a group of permissions has been granted. */
    public static boolean arePermissionsGranted(Context c, String[] permissions)
    {
        PackageManager pm = c.getPackageManager();
        for (String permission : permissions)
        {
            try
            {
                pm.getPermissionInfo(permission,0);
            }
            catch (PackageManager.NameNotFoundException e)
            {
                // Permission is not recognized. This could be a permission not available for
                // the current API level.
                Log.d(Log.className(c),"Don't recognize permission "+permission);
                continue;
            }

            int permissionCheck = ContextCompat.checkSelfPermission(c,permission);
            if (permissionCheck!=PackageManager.PERMISSION_GRANTED)
            {
                return false;
            }
        }
        return true;
    }

    /** Check to see if we need to request permission to set exact alarms, and display the
     * prompt if so. Returns true if the prompt was displayed, else false. */
    public static boolean checkExactAlarmPermission(Activity a)
    {
        AlarmManager am = (AlarmManager)a.getSystemService(Context.ALARM_SERVICE);
        long lastPrompt = settings.getLong(PrefNames.EXACT_ALARM_REQUEST_TIME,0);
        long timeSincePrompt = System.currentTimeMillis()-lastPrompt;
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.S && !am.canScheduleExactAlarms() &&
            timeSincePrompt>ONE_DAY_MS && settings.getBoolean(PrefNames.REMINDER_ENABLED,true))
        {
            // We need to prompt the user for permission. Before displaying the dialog,
            // set up a broadcast receiver to inform us when the state of the permission changes:
            if (_alarmPermReceiver == null)
            {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(AlarmManager.
                    ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
                _alarmPermReceiver = new BroadcastReceiver()
                {
                    @Override
                    public void onReceive(Context context, Intent intent)
                    {
                        AlarmManager am = (AlarmManager) context.getSystemService(Context.
                            ALARM_SERVICE);
                        if (am.canScheduleExactAlarms())
                        {
                            Log.i(TAG, "Permission to set exact alarms has been granted. " +
                                "Rescheduling reminder notifications.");
                            refreshTaskReminders();
                        }
                        else
                        {
                            Log.i(TAG, "Permission to schedule exact alarms has been " +
                                "revoked.");
                        }
                    }
                };
                if (Build.VERSION.SDK_INT >= 33)
                {
                    a.getApplicationContext().registerReceiver(_alarmPermReceiver, intentFilter,
                        Context.RECEIVER_EXPORTED);
                }
                else
                {
                    a.getApplicationContext().registerReceiver(_alarmPermReceiver, intentFilter);
                }
            }

            // Show the prompt:
            settings.edit().putLong(PrefNames.EXACT_ALARM_REQUEST_TIME,System.currentTimeMillis())
                .apply();
            new AlertDialog.Builder(a)
                .setMessage(R.string.need_exact_alarm_permission)
                .setNegativeButton(R.string.Cancel, null)
                .setPositiveButton(R.string.allow_permission, (dialogInterface, i) -> {
                    Intent exactAlarmRequestor = new Intent(Settings.
                        ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    exactAlarmRequestor.setData(Uri.parse("package:" + a.getPackageName()));
                    a.startActivity(exactAlarmRequestor);
                })
                .show();
            return true;
        }
        return false;
    }

    /** Determine if the user needs to be prompted for permission for the app to post notifications.
     * This is needed if notification permission is denied and the user eitehr has reminders or
     * locations enabled. */
    public static boolean needsNotificationPermission(Context c)
    {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.TIRAMISU)
        {
            // Earlier version of Android. It's not needed.
            return false;
        }

        SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
        if (!prefs.getBoolean(PrefNames.REMINDER_ENABLED,true) &&
            !prefs.getBoolean(PrefNames.LOCATIONS_ENABLED,false))
        {
            // Niether of the features that need this is enabled.
            return false;
        }

        return !Util.arePermissionsGranted(c,new String[] {Manifest.permission.
            POST_NOTIFICATIONS});
    }

    /** Display a dialog if necessary that provides the rationale for needing notification
     * permission. The dialog is displayed if notification permission is not granted and the user
     * has the reminder or location field enabled. Returns true if the prompt is displayed. */
    public static boolean showNotificationRationaleIfNeeded(UtlActivity a)
    {
        if (!needsNotificationPermission(a))
            return false;

        Log.v(TAG,"Showing notification permission rationale.");
        new AlertDialog.Builder(a)
            .setMessage(R.string.notif_permission_needed)
            .setCancelable(false)
            .setPositiveButton(R.string.allow_permission, (dialogInterface, i) -> {
                // Show the system prompt:
                promptForNotificationPermIfNeeded(a,false);
            })
            .setNegativeButton(R.string.deny_disable_reminders, (dialogInterface, i) -> {
                // Disable affected features:
                Log.v(TAG,"Notification permission denied. Disabling reminders.");
                SharedPreferences prefs = a.getSharedPreferences(Util.PREFS_NAME,0);
                prefs.edit()
                    .putBoolean(PrefNames.REMINDER_ENABLED,false)
                    .putBoolean(PrefNames.LOCATIONS_ENABLED,false)
                    .apply();
            })
            .show();

        return true;
    }

    /** Show the system prompt to allow notification permission if needed. Retruns true if the
     * prompt is displayed. */
    public static boolean promptForNotificationPermIfNeeded(UtlActivity a, boolean
        showRationaleOnDenial)
    {
        if (!needsNotificationPermission(a))
            return false;

        Log.v(TAG,"Showing system prompt for notification permission.");
        a.requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},
            () -> {
                // Permission granted. There's nothing else to do.
                Log.v(TAG,"Notificatiohn permission granted.");
            },true,null,
            () -> {
                // Permission denied. Disable related features unless the option to show the
                // rationale has been set.
                if (showRationaleOnDenial)
                {
                    Log.v(TAG,"Notification permission denied. Showing reationale.");
                    showNotificationRationaleIfNeeded(a);
                    return;
                }
                Log.v(TAG,"Notification permission denied. Disabling reminders.");
                SharedPreferences prefs = a.getSharedPreferences(Util.PREFS_NAME,0);
                prefs.edit()
                    .putBoolean(PrefNames.REMINDER_ENABLED,false)
                    .putBoolean(PrefNames.LOCATIONS_ENABLED,false)
                    .apply();
            });

        return true;
    }
}


