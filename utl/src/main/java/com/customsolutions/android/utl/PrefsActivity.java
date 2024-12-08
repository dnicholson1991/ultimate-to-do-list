package com.customsolutions.android.utl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.gms.common.Feature;
import com.google.android.ump.ConsentInformation;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

public class PrefsActivity extends AppCompatPreferenceActivity
{
	private static final String TAG = "PrefsActivity";

	/** Request code for permissions requests. */
	private static final int PERMISSION_REQUEST_CODE = 4712945;

	public static final int GET_TAGS = 1;
	
	private ProgressDialog _progressDialog;
	public String _section;
	SharedPreferences _settings;
	private Preference _tempPref;
	private PreferenceScreen _tempPrefScreen;
	private boolean _hasHeaders = false;
	
	// Do we have a Google account (this affects what can/cannot be disabled):
	boolean _googleAccountExists;

	boolean _useTabletBackup;

	private PurchaseManager _pm;

	/** The permissions the app is currently asking for. */
	private String[] _permissionsRequested;

	/** The runnable to execute after a set of permissions has been approved. */
	private Runnable _permissionAcceptanceRunnable;

	/** Flag indicating if the current permissions request can be denied by the user. */
	private boolean _permissionDenialAllowed;

	/** The message to display if the user denies a permission. */
	private String _permissionDenialMessage;

	/** The runnable to execute if the user denies a permission. */
	private Runnable _permissionDenialRunnable;

	/** The index of the fiels/functions used header, which requires special handling. */
	private static int _fieldsFunctionsIndex;

	/** The URI of the backup file to restore. */
	private Uri _backupUri;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		Util.setTheme(this);

		// _settings needs initialized immediately, since it may be used during fragment setup
		// during the super.onCreate() call.
		_settings = getSharedPreferences(Util.PREFS_NAME,0);

		super.onCreate(savedInstanceState);
		Util.appInit(this);
		
		_useTabletBackup = false;

		// Query Google or Amazon for an up-to-date list of in-app items, including price and
		// purchase status.
		_pm = new PurchaseManager(this);
		_pm.link();
		_pm.startFetchingInAppItems();

		_section = "headers";  // Will be changed by Fragment if not displaying headers.

		ActionBar ab = getSupportActionBar();
		if (ab!=null)
		{
			ab.setDisplayHomeAsUpEnabled(true);
			ab.setTitle(R.string.Settings);
			// ab.setIcon(Util.resourceIdFromAttr(this, R.attr.ab_settings));
		}
		else
			Util.log("Action bar is null.");
	}
	
	// Called only on Honeycomb and later
	@SuppressLint("NewApi")
	@Override
	public void onBuildHeaders(List<Header> headerList)
	{
		_hasHeaders = true;
		loadHeadersFromResource(R.xml.preference_headers, headerList);

		ActionBar ab = getSupportActionBar();
		if (ab!=null)
			ab.setTitle(getString(R.string.Settings));
		
		// Remove the Location Tracking header if locations are disabled:
		_settings = getSharedPreferences(Util.PREFS_NAME,0);
		if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
		{
			int locationIndex = -1;
			for (int i=0; i<headerList.size(); i++)
			{
				PreferenceActivity.Header header = headerList.get(i);
				if (header.id==R.id.prefs_header_location_tracking)
				{
					locationIndex = i;
					break;
				}

			}
			if (locationIndex>=0)
				headerList.remove(locationIndex);
		}
		
		// Remove the voice mode header if speech recognition is not supported by the device:
		if (!SpeechRecognizer.isRecognitionAvailable(this))
		{
			int voiceIndex = -1;
			for (int i=0; i<headerList.size(); i++)
			{
				PreferenceActivity.Header header = headerList.get(i);
				if (header.id==R.id.prefs_header_voice_mode)
				{
					voiceIndex = i;
					break;
				}
			}
			if (voiceIndex>=0)
				headerList.remove(voiceIndex);
		}

        // Remove the Android Wear section if the device does not support it.
        if (!Util.canUseAndroidWear())
        {
            int wearIndex = -1;
            for (int i=0; i<headerList.size(); i++)
            {
                PreferenceActivity.Header header = headerList.get(i);
                if (header.id==R.id.prefs_header_android_wear)
                {
                    wearIndex = i;
                    break;
                }
            }
            if (wearIndex>=0)
                headerList.remove(wearIndex);
        }

		// Special handling for the fields/function item. This must be at the end of this
		// function in order to get an accurate value for _fieldsFunctionsIndex.
		for (int i=0; i<headerList.size(); i++)
		{
			PreferenceActivity.Header header = headerList.get(i);
			if (header.id==R.id.prefs_header_fields_functions)
			{
				_fieldsFunctionsIndex = i;
				Log.v(TAG,"Fields/functions index: "+i);
				break;
			}
		}

		// By default, we don't reboot the app after leaving here:
		Util.updatePref(PrefNames.PREF_REBOOT_NEEDED, false, this);

		// Get an array of section titles. These are needed so we can search for views by text.
		final ArrayList<String> titles = new ArrayList<>();
		Log.v(TAG,"HeaderList size: "+headerList.size());
		for (int i=0; i<headerList.size(); i++)
		{
			String title = getString(headerList.get(i).titleRes);
			Log.v(TAG,"Title: "+title);
			titles.add(title);
		}

		// On Android 5 devices, the header list displays in the wrong color on dark themes.
		// Add a view listener to find and fix the text views:
		final ListView lv = findViewById(android.R.id.list);
		if (lv==null)
		{
			Log.e(TAG,"No ListView in Prefs","The header ListView is missing from PrefsActivity."+
				" Cannot adjust colors.");
		}
		else
		{
			if (_settings.getInt(PrefNames.THEME,0)>2 && Build.VERSION.SDK_INT<Build.
				VERSION_CODES.M)
			{
				lv.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.
					OnGlobalLayoutListener()
				{
					@Override
					public void onGlobalLayout()
					{
						for (int i = 0; i < titles.size(); i++)
						{
							ArrayList<View> outViews = new ArrayList<>();
							lv.findViewsWithText(outViews, titles.get(i), View.FIND_VIEWS_WITH_TEXT);
							if (outViews.size() > 0)
							{
								((TextView) outViews.get(0)).setTextColor(Util.colorFromAttr(PrefsActivity.
									this, android.R.attr.textColorPrimary));
							}
						}
					}
				});
			}
		}

		// On tablets, we need to adjust the text color of the header at the top of the
		// right pane (the "breadcrumb").  For the dark themes, Android refuses to display the
		// proper color otherwise.

		final View breadcrumb = findViewById(android.R.id.title);
		if (breadcrumb == null)
		{
			// Single pane layout. Nothing to adjust.
			return;
		}

		// Add a listener which adjusts the header text color whenever the layout changes.
		breadcrumb.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.
			OnGlobalLayoutListener()
		{
			@Override
			public void onGlobalLayout()
			{
				for (int i=0; i<titles.size(); i++)
				{
					ArrayList<View> outViews = new ArrayList<>();
					breadcrumb.findViewsWithText(outViews, titles.get(i),View.FIND_VIEWS_WITH_TEXT);
					if (outViews.size()>0)
					{
						((TextView) outViews.get(0)).setTextColor(Util.colorFromAttr(PrefsActivity.
							this, android.R.attr.textColorPrimary));
						((TextView) outViews.get(0)).setTypeface(null,Typeface.BOLD);
					}
				}
			}
		});


	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		Log.v(TAG,"onListItemClick(). Position: "+position);
		if (position==_fieldsFunctionsIndex)
		{
			Intent featureIntent = new Intent(this, FeatureSelection.class);
			featureIntent.putExtra(FeatureSelection.EXTRA_INITIAL_SETUP,false);
			startActivity(featureIntent);
		}
		else
			super.onListItemClick(l,v,position,id);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
		@NonNull int[] grantResults)
	{
		int numDenials = 0;
		Util.log("Result of permissions request:");
		for (int i = 0; i < permissions.length; i++)
		{
			Util.log(permissions[i] + ": " + grantResults[i]);
			if (grantResults[i]!= PackageManager.PERMISSION_GRANTED)
				numDenials++;
		}

		if (numDenials==0)
		{
			if (_permissionAcceptanceRunnable!=null)
				_permissionAcceptanceRunnable.run();
			else
				Util.log("WARNING: _permissionAcceptanceRunnable is null.");
		}
		else
		{
			if (_permissionDenialAllowed)
			{
				if (_permissionDenialMessage==null)
				{
					if (_permissionDenialRunnable!=null)
						_permissionDenialRunnable.run();
					return;
				}

				new AlertDialog.Builder(this)
					.setMessage(_permissionDenialMessage)
					.setPositiveButton(R.string.allow_permission, new DialogInterface.
						OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							requestPermissions(_permissionsRequested,
								_permissionAcceptanceRunnable, _permissionDenialAllowed,
								_permissionDenialMessage,_permissionAcceptanceRunnable);
						}
					})
					.setNegativeButton(R.string.reject_permission, new DialogInterface.OnClickListener()
					{
						@Override
						public void onClick(DialogInterface dialogInterface, int i)
						{
							if (_permissionDenialRunnable!=null)
								_permissionDenialRunnable.run();
						}
					})
					.setOnCancelListener(new DialogInterface.OnCancelListener()
					{
						@Override
						public void onCancel(DialogInterface dialogInterface)
						{
							if (_permissionDenialRunnable!=null)
								_permissionDenialRunnable.run();
						}
					})
					.show();
			}
			else
			{
				new AlertDialog.Builder(this)
					.setMessage(_permissionDenialMessage)
					.setPositiveButton(R.string.OK, null)
					.setOnDismissListener(new DialogInterface.OnDismissListener()
					{
						@Override
						public void onDismiss(DialogInterface dialogInterface)
						{
							requestPermissions(_permissionsRequested,
								_permissionAcceptanceRunnable, _permissionDenialAllowed,
								_permissionDenialMessage,_permissionAcceptanceRunnable);
						}
					})
					.show();
			}
		}
	}

	@Override
	protected boolean isValidFragment(String fragmentName)
	{
		return true;
	}

	/** Ask the user to approve some permissions.
	 * @param permissions - The list of permission to approve. Example: Manifest.permission.ACCESS_FINE_LOCATION
	 * @param successRunnable - Execute this code after the permission is approved. Cannot be null.
	 * @param allowDenial - Determines if the permission can be denied.
	 * @param denialMessage - Show this if the permission is denied. If denial is disallowed, then
	 *     the permission will be requested again after the user sees the message. If denial is
	 *     allowed, the user will be given an option to be asked again or continue without the
	 *     permission. Set this to null to just execute denialRunnable on denial.
	 * @param denialRunnable - If denial is allowed and the user denies the permission, execute
	 *     this. */
	public void requestPermissions(String[] permissions, Runnable successRunnable,
		boolean allowDenial, String denialMessage, Runnable denialRunnable)
	{
		if (successRunnable==null)
		{
			Util.log("WARNING: null runnable passed to requestPermissions.");
			return;
		}

		// If permission have already been approved, just execute the success runnable.
		ArrayList<String> unapprovedPermissions = new ArrayList<>();
		for (String permission : permissions)
		{
			int permissionCheck = ContextCompat.checkSelfPermission(this,permission);
			if (permissionCheck!= PackageManager.PERMISSION_GRANTED)
			{
				unapprovedPermissions.add(permission);
			}
		}
		if (unapprovedPermissions.size()==0)
		{
			successRunnable.run();
			return;
		}

		_permissionsRequested = permissions;
		_permissionAcceptanceRunnable = successRunnable;
		_permissionDenialAllowed = allowDenial;
		_permissionDenialMessage = denialMessage;
		_permissionDenialRunnable= denialRunnable;

		ActivityCompat.requestPermissions(this,
			unapprovedPermissions.toArray(new String[unapprovedPermissions.size()]),
			PERMISSION_REQUEST_CODE);
	}

    //
    // Preference Initialization Methods that are used both here and in PrefsFragement.java.
    //
    
    // Remove preferences that are not applicable due to disabled fields/functions:
    public void removeUnusedPrefs(PreferenceScreen prefScreen)
    {
    	if (_section.equals("new_task_defaults"))
        {
        	if (!_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_START_DATE));
        	if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_DUE_DATE));
        	if (!_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_PRIORITY));
        	if (!_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_STATUS));
        	if (!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_FOLDER));
        	if (!_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_CONTEXT));
        	if (!_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_GOAL));
        	if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_LOCATION));
        	if (!_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_TAGS));
        	if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        	{
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DEFAULT_ADD_TO_CAL));
        		prefScreen.removePreference(prefScreen.findPreference(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL));
        	}
        }
    	else if (_section.equals("voice_mode"))
    	{
    		PreferenceGroup group = (PreferenceGroup)prefScreen.findPreference(
				"pref_voice_mode_defaults_category");
    		if (!_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_START_DATE));
        	if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_DUE_DATE));
        	if (!_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_PRIORITY));
        	if (!_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_STATUS));
        	if (!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_FOLDER));
        	if (!_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_CONTEXT));
        	if (!_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_GOAL));
        	if (!_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_LOCATION));
        	if (!_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_TAGS));
        	if (!_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        		group.removePreference(group.findPreference(PrefNames.VM_DEFAULT_ADD_TO_CAL));
    	}
    	else if (_section.equals("nav_drawer"))
    	{
			PreferenceGroup group = (PreferenceGroup)prefScreen.findPreference(
				"pref_nav_drawer_show_category");
    		if (!_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
    		{
    			group.removePreference(group.findPreference(PrefNames.SHOW_DUE_TODAY_TOMORROW));
    			group.removePreference(prefScreen.findPreference(PrefNames.SHOW_OVERDUE));
    		}
    		if (!_settings.getBoolean(PrefNames.STAR_ENABLED, true))
    			group.removePreference(group.findPreference(PrefNames.SHOW_STARRED));
    		if (!_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    			group.removePreference(group.findPreference(PrefNames.SHOW_ARCHIVED_FOLDERS));
    		if (!_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    			group.removePreference(group.findPreference(PrefNames.SHOW_ARCHIVED_GOALS));
    	}
    }
    
    // Add in a "preference" for each account.  The prefScreen input must be a blank PreferenceScreen.
    public void addAccountPrefs(PreferenceScreen prefScreen)
    {
    	// Preferences are added dynamically based on the current accounts in the DB.
    	AccountsDbAdapter adb = new AccountsDbAdapter();
    	Cursor c = adb.getAllAccounts();
    	while (c.moveToNext())
    	{
    		UTLAccount a = adb.getUTLAccount(c);
    		Preference p = new Preference(this);
    		p.setKey("pref_account_"+a._id);
    		p.setTitle(a.name);
    		if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
    		{
    			p.setSummary(getString(R.string.Linked_to_Toodledo_account_)+" "+
    				a.td_email);
    		}
    		else if (a.sync_service==UTLAccount.SYNC_GOOGLE)
    		{
    			p.setSummary(getString(R.string.Linked_to_Google_account_)+" "+
    				a.username);
    		}
    		else
    		{
    			p.setSummary(getString(R.string.Account_Status_No_Sync));
    		}
    		Intent i = new Intent(this, AccountOps.class);
			i.putExtra("account_id", a._id);
			p.setIntent(i);
     		prefScreen.addPreference(p);
    	}
    	c.close();
    	
    	// Add in the option for a new account:
    	Preference p = new Preference(this);
    	p.setKey("pref_account_add");
    	p.setTitle(getString(R.string.Link_to_Another_Account));
    	p.setIntent(new Intent(this,BeginSetup.class));
    	prefScreen.addPreference(p);
    }
    
    // Initialize Display Settings:
    public void initDisplaySettings(PreferenceScreen prefScreen)
    {
    	Util.log("initDisplaySettings called. ");

    	Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				Util.updatePref(PrefNames.PREF_REBOOT_NEEDED, true);
				Util.popup(PrefsActivity.this, R.string.Change_will_take_effect_later);
				if (_settings.getBoolean(PrefNames.THEME_CHANGED,false))
				{
					int oldTheme = _settings.getInt(PrefNames.THEME,0);
					String oldThemeName = getResources().getStringArray(R.array.themes)[oldTheme];
					Stats.recordStat(PrefsActivity.this,"unselected_theme",new String[] {
						oldThemeName});
					Stats.recordStat(PrefsActivity.this,"selected_theme",new String[] {
						String.valueOf(newValue)});
				}
				else
					_settings.edit().putBoolean(PrefNames.THEME_CHANGED,true).apply();
				return true;
			}
		};
		
    	prefScreen.findPreference(PrefNames.FONT_SIZE).setOnPreferenceChangeListener(listener);
    	prefScreen.findPreference(PrefNames.THEME).setOnPreferenceChangeListener(listener);
    	prefScreen.findPreference(PrefNames.SS_LANDSCAPE).setOnPreferenceChangeListener(listener);
    	prefScreen.findPreference(PrefNames.SS_PORTRAIT).setOnPreferenceChangeListener(listener);
    }
    
    // Initialize New Task Defaults Preferences:
    public void initNewTaskDefaults(PreferenceScreen prefScreen)
    {
    	// Many of the preferences need initialized from a database query:
    	DatabaseListPreference dbPref;
    	
    	if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.DEFAULT_FOLDER);
    		dbPref.initEntries("select _id,title from folders where archived=0 order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.DEFAULT_CONTEXT);
    		dbPref.initEntries("select _id,title from contexts order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.DEFAULT_GOAL);
    		dbPref.initEntries("select _id,title from goals where archived=0 order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.DEFAULT_LOCATION);
    		dbPref.initEntries("select _id,title from locations order by title");
    	}
    	
    	dbPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.DEFAULT_ACCOUNT);
    	dbPref.initEntries("select _id,name from account_info order by name");
    	
    	if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
        	Preference tagsPref = (Preference) prefScreen.findPreference(PrefNames.DEFAULT_TAGS);
        	tagsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					// Get current value of preference and store as a String array:
					Intent i = new Intent(PrefsActivity.this, TagPicker.class);
					TagsPreference tagsPref = (TagsPreference)preference;
					_tempPref = preference;
					if (tagsPref.getText().length()>0 && !tagsPref.getText().equals(PrefsActivity.this.
						getString(R.string.None)))
					{
						String[] tagArray = tagsPref.getText().split(",");
						i.putExtra("selected_tags", tagArray);
						
						// We also need to update the current tags in the DB:
                        CurrentTagsDbAdapter db = new CurrentTagsDbAdapter();
                        db.addToRecent(tagArray);
					}
					startActivityForResult(i,GET_TAGS);
					return true;
				}
			});
    	}
    }
    
    // Initialize New Task Defaults Preferences for voice mode:
    public void initVoiceModeNewTaskDefaults(PreferenceScreen prefScreen)
    {
    	// Many of the preferences need initialized from a database query:
    	DatabaseListPreference dbPref;
    	int numAccounts = (new AccountsDbAdapter()).getNumAccounts();
    	
    	PreferenceGroup group = (PreferenceGroup)prefScreen.findPreference(
			"pref_voice_mode_defaults_category");
    	
    	if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)group.findPreference(PrefNames.VM_DEFAULT_FOLDER);
    		if (numAccounts>1)
    		{
    			dbPref.initEntries("select f._id, f.title || ' (' || a.name || ')' from folders f, "+
    				"account_info a where f.account_id=a._id and f.archived=0 order by f.title,a.name");
    		}
    		else
    			dbPref.initEntries("select _id,title from folders where archived=0 order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)group.findPreference(PrefNames.VM_DEFAULT_CONTEXT);
    		if (numAccounts>1)
    		{
    			dbPref.initEntries("select c._id, c.title || ' (' || a.name || ')' from contexts c, "+
    				"account_info a where c.account_id=a._id order by c.title,a.name");
    		}
    		else
    			dbPref.initEntries("select _id,title from contexts order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)group.findPreference(PrefNames.VM_DEFAULT_GOAL);
    		if (numAccounts>1)
    		{
    			dbPref.initEntries("select g._id, g.title || ' (' || a.name || ')' from goals g, "+
    				"account_info a where g.account_id=a._id and g.archived=0 order by g.title,a.name");
    		}
    		else
    			dbPref.initEntries("select _id,title from goals where archived=0 order by title");
    	}
    	
    	if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
    	{
    		dbPref = (DatabaseListPreference)group.findPreference(PrefNames.VM_DEFAULT_LOCATION);
    		if (numAccounts>1)
    		{
    			dbPref.initEntries("select l._id, l.title || ' (' || a.name || ')' from locations l, "+
    				"account_info a where l.account_id=a._id order by l.title,a.name");
    		}
    		else
    			dbPref.initEntries("select _id,title from locations order by title");
    	}
    	
    	dbPref = (DatabaseListPreference)group.findPreference(PrefNames.VM_DEFAULT_ACCOUNT);
    	dbPref.initEntries("select _id,name from account_info order by name");
    	
    	if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
    	{
        	Preference tagsPref = (Preference) group.findPreference(PrefNames.VM_DEFAULT_TAGS);
        	tagsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
			{
				@Override
				public boolean onPreferenceClick(Preference preference)
				{
					// Get current value of preference and store as a String array:
					Intent i = new Intent(PrefsActivity.this, TagPicker.class);
					TagsPreference tagsPref = (TagsPreference)preference;
					_tempPref = preference;
					if (tagsPref.getText().length()>0 && !tagsPref.getText().equals(PrefsActivity.this.
						getString(R.string.None)))
					{
						String[] tagArray = tagsPref.getText().split(",");
						i.putExtra("selected_tags", tagArray);
						
						// We also need to update the current tags in the DB:
                        CurrentTagsDbAdapter db = new CurrentTagsDbAdapter();
                        db.addToRecent(tagArray);
					}
					startActivityForResult(i,GET_TAGS);
					return true;
				}
			});
    	}
    }

	// Initialize voice mode preferences:
	public void initVoiceModePrefs(PreferenceScreen prefScreen)
	{
		// Changing of the option to use a Bluetooth mic requires permission on Android 31+
		prefScreen.findPreference(PrefNames.VM_USE_BLUETOOTH).setOnPreferenceChangeListener((preference, newValue) -> {
			if (Build.VERSION.SDK_INT>=31)
			{
				boolean isEnabled = (Boolean) newValue;
				if (!isEnabled || Util.arePermissionsGranted(this,new String[] {Manifest.
					permission.BLUETOOTH_CONNECT}))
				{
					return true;
				}
				requestPermissions(new String[] { Manifest.permission.BLUETOOTH_CONNECT},
					() -> {
						Log.i(TAG,"User granted bluetooth connect permission.");
					},
					true,
					null,
					() -> {
						Log.i(TAG,"User rejected bluetooht connection permission.");
						_settings.edit().putBoolean(PrefNames.VM_USE_BLUETOOTH,false).apply();
					}
				);
			}
			return true;
		});
	}

    // Initialize Miscellaneous Preferences:
    public void initMiscPrefs(PreferenceScreen prefScreen)
    {
    	// Initialize the linked calendar ID:
    	if (_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    	{
        	DatabaseListPreference calPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.
        		LINKED_CALENDAR_ID);
        	CalendarInterface ci = new CalendarInterface(this);
			ArrayList<CalendarInfo> cals = ci.getAvailableCalendars();
			if (cals.size()==0)
			{
				calPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
				{
					@Override
					public boolean onPreferenceClick(Preference preference)
					{
						Util.longerPopup(PrefsActivity.this, null, PrefsActivity.this.getString(
							R.string.No_calendars_error_msg));
						return true;
					}
				});
			}
			Iterator<CalendarInfo> it = cals.iterator();
			while (it.hasNext())
			{
				CalendarInfo calInfo = it.next();
				calPref.addEntry(calInfo.id, calInfo.name);
			}
			calPref.finalizeEntries();
    	}
    	else
    	{
    		prefScreen.removePreference(prefScreen.findPreference(PrefNames.LINKED_CALENDAR_ID));
    	}
    	
    	// Initialize the startup screen preferences, from the available views in the database:
    	DatabaseListPreference startupPref = (DatabaseListPreference)prefScreen.findPreference(PrefNames.
    		STARTUP_VIEW_ID);
        initTaskListPreference(startupPref);
    	startupPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				String viewIdString = (String)newValue;
				Long viewID = Long.parseLong(viewIdString);
				DatabaseListPreference startupPref = (DatabaseListPreference) preference;
				Util.updatePref(PrefNames.STARTUP_VIEW_TITLE, startupPref.getValueForKey(viewID));
				return true;
			}
		});

    	// Hide or show the personal data consent option. Show the GDPR consent form on tap if
		// it's visible.
		if (ConsentUtil.getConsentStatus()== ConsentInformation.ConsentStatus.OBTAINED ||
			ConsentUtil.getConsentStatus()== ConsentInformation.ConsentStatus.REQUIRED)
		{
			prefScreen.findPreference(PrefNames.PERSONAL_DATA_CONSENT).
				setOnPreferenceClickListener(preference -> {
					ConsentUtil.showConsentForm(PrefsActivity.this,null);
					return false;
				});
		}
		else
		{
			prefScreen.removePreference(prefScreen.findPreference(PrefNames.
				PERSONAL_DATA_CONSENT));
		}
    }
    
    // Initialize backup and restore options:
    public void initBackupAndRestore(PreferenceScreen prefScreen)
    {
    	// Implement handlers for the backup and restore functions:
    	prefScreen.findPreference("run_backup_now").setOnPreferenceClickListener(arg0 ->
		{
			checkBackupPermission(() -> {
				runBackup();
			});
			return true;
		});
    	prefScreen.findPreference("run_restore_now").setOnPreferenceClickListener(arg0 -> {
    		checkBackupPermission(() -> {
				runRestore();
			});
			return true;
		});

    	// If the user tries to turn on automatic backups, we need to check permissions.
		prefScreen.findPreference(PrefNames.BACKUP_DAILY).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				Boolean isEnabled = (Boolean) newValue;
				if (!isEnabled)
				{
					// We always allow this to be turned off.
					return true;
				}

				// Only allow the preference to be turned on if the user has permission.
				// Prompt the user if permission isn't granted.
				if (Util.hasPermissionForBackup(PrefsActivity.this))
					return true;
				checkBackupPermission(null);
				return false;
			}
		});

    	// Display the last backup date and time:
    	prefScreen.findPreference("run_backup_now").setSummary(getString(R.string.Last_Backup_Date_)+" "+
    		getLastBackupTime());
    }

	/** Check for the permissions needed to make backups. If they're granted, execute the
	 * provided runnable.  Otherwise, prompt for permission. */
	private void checkBackupPermission(final Runnable successRunnable)
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
		{
			if (successRunnable!=null)
				successRunnable.run();
			return;
		}

		// Check the basic permissions to read and write external storage.
		if (!Util.arePermissionsGranted(this,new String[]{Manifest.permission.
			READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE}))
		{
			requestPermissions(new String[] { Manifest.permission.READ_EXTERNAL_STORAGE,
				Manifest.permission.WRITE_EXTERNAL_STORAGE },
				() -> {
					checkBackupPermission(successRunnable);
				},
				true,
				null,
				null
			);
			return;
		}

		if (successRunnable!=null)
			successRunnable.run();
	}

	// Initialize sync preferences
    public void initSyncPreferences(PreferenceScreen prefScreen)
    {
    	prefScreen.findPreference(PrefNames.SYNC_INTERVAL).setOnPreferenceChangeListener(new Preference.
    		OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				int newSyncInterval = Integer.parseInt((String)newValue);
				int minSyncInterval = Util.getMinSyncInterval(PrefsActivity.this);
				if (newSyncInterval<minSyncInterval)
				{
					Util.longerPopup(PrefsActivity.this, null, PrefsActivity.this.getString(R.string.
						Online_Service_Sync_Interval_Limit)+" "+minSyncInterval);
					return false;
				}
				return true;
			}
		});
    }
    
    //
    // End of preference initialization methods used both here and in PrefsFragment.java
    //
    
	@Override
	public void onResume()
	{
		super.onResume();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (item.getItemId()==android.R.id.home)
		{
			// This button acts just like the device's back button.
			onBackPressed();
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	// Public method to run a backup:
	public void runBackup()
	{
		// Handlers for the "yes" and "no" options on the confirmation dialog:
		DialogInterface.OnClickListener dialogClickListener = new 
			DialogInterface.OnClickListener() 
        {
			@SuppressLint("NewApi")
			@Override
            public void onClick(DialogInterface dialog, int which) 
            {
				switch (which)
				{
				case DialogInterface.BUTTON_POSITIVE:
                    // Yes clicked:
					dialog.dismiss();
					_progressDialog = ProgressDialog.show(PrefsActivity.this, null, 
						Util.getString(R.string.Backup_Msg),false);
					if (Build.VERSION.SDK_INT >= 11)
						new PerformBackup().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					else
						new PerformBackup().execute();
					return;
				}
            }
        };
        
        // Create and show the confirmation dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(PrefsActivity.this);
        builder.setMessage(R.string.Backup_Confirmation);
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.show();	
    }
	
    private boolean _isOrientationLocked = false;

    /** Prevent the activity from dying and regenerating after an orientation change.  This keeps
     * the activity at the same orientation even if the user moves the device. */
    public void lockScreenOrienation()
    {
    	if (_isOrientationLocked)
    		return;

    	try
		{
			int currentOrientation = getResources().getConfiguration().orientation;
			if (currentOrientation == Configuration.ORIENTATION_PORTRAIT)
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			else
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

			_isOrientationLocked = true;
		}
    	catch (Exception e) { }
    }
    
    /** Unlock the orientation, restoring normal orientation change handling. */
    public void unlockScreenOrientation()
    {
    	try
		{
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			_isOrientationLocked = false;
		}
    	catch (Exception e) { }
    }

    // An AsyncTask which performs a backup:
    private class PerformBackup extends AsyncTask<Void,Void,String>
    {
    	@Override
    	protected void onPreExecute()
    	{
    		PrefsActivity.this.lockScreenOrienation();
    	}
    	
    	protected String doInBackground(Void... v)
    	{
    		return Util.performBackup(PrefsActivity.this);
    	}
    	
    	protected void onPostExecute(String errorMsg)
    	{
    		if (_progressDialog!=null && _progressDialog.isShowing())
    			_progressDialog.dismiss();
    		PrefsActivity.this.unlockScreenOrientation();
    		
    		if (errorMsg!=null && errorMsg.length()>0)
    		{
    			Util.longerPopup(PrefsActivity.this, "", errorMsg);
    			return;
    		}
    		
    		Util.longerPopup(PrefsActivity.this, "", getString(R.string.backup_saved_to_downloads));
    	}
    }

    // Public method to run a restore:
    public void runRestore()
    {
    	// Handlers for the "yes" and "no" options on the confirmation dialog:
		DialogInterface.OnClickListener dialogClickListener = new 
			DialogInterface.OnClickListener() 
        {
			@SuppressLint("NewApi")
			@Override
            public void onClick(DialogInterface dialog, int which) 
            {
				switch (which)
				{
				case DialogInterface.BUTTON_POSITIVE:
                    // Yes clicked:
					dialog.dismiss();
					Util.openBackupFilePicker(PrefsActivity.this);
					return;
				}
            }
        };
        
        // Create and show the confirmation dialog:
        AlertDialog.Builder builder = new AlertDialog.Builder(PrefsActivity.this);
        builder.setMessage(R.string.Restore_Confirmation);
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        builder.show();
    }
    
    // An AsyncTask which performs the restore:
    private class PerformRestore extends AsyncTask<Void,Void,String>
    {
    	@Override
    	protected void onPreExecute()
    	{
    		PrefsActivity.this.lockScreenOrienation();
    	}
    	
    	protected String doInBackground(Void... v)
    	{
    		return Util.restoreDataFromBackup(PrefsActivity.this,_backupUri);
    	}
    	
    	protected void onPostExecute(String errorMsg)
    	{
    		if (_progressDialog!=null && _progressDialog.isShowing())
    			_progressDialog.dismiss();
    		PrefsActivity.this.unlockScreenOrientation();
    		
    		if (errorMsg!=null && errorMsg.length()>0)
    		{
    			Util.longerPopup(PrefsActivity.this, "", errorMsg);
    			return;
    		}
    		
    		// Display a message indicating success, and restart the app:
    		// Display a simple popup message letting the user know that
	        // the app needs to be restarted.
	        DialogInterface.OnClickListener dialogClickListener = new 
				DialogInterface.OnClickListener()
			{					
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
		    		rebootApp();
				}
			};
			AlertDialog.Builder builder = new AlertDialog.Builder(PrefsActivity.this);
			builder.setMessage(R.string.Restore_Successful);
			builder.setPositiveButton(Util.getString(R.string.OK), 
				dialogClickListener);
			builder.setCancelable(false);
			builder.show();		
    	}
    }
    
    // Get the date and time of the last backup.  Returns "none" if there is no backup:
    public String getLastBackupTime()
    {
    	long backupModTime = Util.getBackupFileModTime(this);
    	if (backupModTime>0)
		{
			String dateStr = Util.getDateTimeString(backupModTime, this);
			if (!Util.settings.getString("home_time_zone", "").equals(TimeZone.
				getDefault().getID()))
			{
				// Let the user know what time zone the timestamp is in.
				dateStr += " ("+Util.getString(R.string.Time_Zone_)+" "+Util.settings.
					getString("home_time_zone", "")+")";
			}
			return dateStr;
		}
        else
        {
        	return getString(R.string.None);
        }
    }
    
	// Handler for activity results:
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        
        // Get extras in the response, if any:
        Bundle extras = new Bundle();
        if (intent != null)
        {
            extras = intent.getExtras();
        }
        
        switch(requestCode)
        {
			case Util.BACKUP_FILE_PICKER_CODE:
				if (resultCode==Activity.RESULT_OK && intent.getData()!=null)
				{
					// The user just chose the backup file to restore.
					Log.v(TAG,"Got the following URI: "+intent.getData().toString());
					_backupUri = intent.getData();
					_progressDialog = ProgressDialog.show(PrefsActivity.this, null,
						Util.getString(R.string.Restoring_Msg),false);
					new PerformRestore().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				}
				break;

			case GET_TAGS:
				if (resultCode==Activity.RESULT_OK && extras.containsKey("selected_tags"))
				{
					String[] tags = extras.getStringArray("selected_tags");
					if (tags.length>0)
					{
						// Create a comma-separated string to store:
						String prefStr = tags[0];
						for (int i=1; i<tags.length; i++)
						{
							prefStr += ","+tags[i];
						}

						// Store the new preference:
						TagsPreference tagsPref = (TagsPreference)_tempPref;
						tagsPref.setNewValue(prefStr);
					}
					else
					{
						// The default is no tags:
						TagsPreference tagsPref = (TagsPreference)_tempPref;
						tagsPref.setNewValue("");
					}
				}
				break;
        }
    }

    @Override
	public void onBackPressed()
	{
		if (getSupportActionBar()!=null)
			getSupportActionBar().setTitle(R.string.Settings);
		super.onBackPressed();
	}

	// When leaving this Activity, perform some consistency checks:
	@SuppressLint("NewApi")
	@Override
	public void onPause()
	{
		SharedPreferences settings = this.getSharedPreferences("UTL_Prefs",0);
		if (!settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) &&
			settings.getBoolean(PrefNames.DUE_TIME_ENABLED, true))
		{
			// Due date is disabled.  Due time must also be disabled.
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(PrefNames.DUE_TIME_ENABLED, false);
			editor.commit();
		}
		if (!settings.getBoolean(PrefNames.START_DATE_ENABLED, true) &&
			settings.getBoolean(PrefNames.START_TIME_ENABLED, true))
		{
			// Start date is disabled.  Start time must also be disabled.
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean(PrefNames.START_TIME_ENABLED, false);
			editor.commit();
		}
				
		// The Util class has some variables that hold the current date and time format.  These 
		// need to be updated.
		
    	if (settings.getInt(PrefNames.DATE_FORMAT,Util.DATE_FORMAT_FROM_SYS)==Util.DATE_FORMAT_FROM_SYS)
    	{
    		Util.updateDateFormatFromSystem(this);
    	}
    	else
    	{
    		// The date format is defined explicitly in a user preference:
    		Util.currentDateFormat = settings.getInt(PrefNames.DATE_FORMAT,Util.DATE_FORMAT_MONTH_FIRST);
    	}   
    	
    	// If the user is getting the time format from the system, then read it in:
    	if (settings.getInt(PrefNames.TIME_FORMAT, Util.TIME_PREF_12H)==Util.TIME_PREF_SYSTEM)
    	{
    		Util.updateTimeFormatFromSystem(this);
    	}
    	else
    	{
    		// The time format is explicitly defined in the user's preference:
    		Util.currentTimeFormat = settings.getInt(PrefNames.TIME_FORMAT, Util.TIME_PREF_12H);
    	}
		
		super.onPause();
		
		// Reboot the app if needed:
		if (_settings.getBoolean(PrefNames.PREF_REBOOT_NEEDED, false) && isFinishing() &&
			(_section.equals("headers") || _hasHeaders))
		{
			Util.updatePref(PrefNames.PREF_REBOOT_NEEDED, false);
			Util.openStartupView(this);
		}
	}

    // Reboot the app:
	public void rebootApp()
	{
		Intent restartIntent = new Intent(PrefsActivity.this,main.class);
		PendingIntent pi = PendingIntent.getActivity(PrefsActivity.this, 0, 
			restartIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		Util.setExactAlarm(PrefsActivity.this,pi,System.currentTimeMillis() + 1000);
		System.runFinalization();
		System.exit(2);
	}

    // Initialize a preference that references a specific view / task list:
    public void initTaskListPreference(DatabaseListPreference pref)
    {
        ViewsDbAdapter vdb = new ViewsDbAdapter();
        int i;

        // All Tasks:
        Cursor c = vdb.getView(ViewNames.ALL_TASKS, "");
        if (c.moveToFirst())
            pref.addEntry(c.getLong(0), getString(R.string.AllTasks));
        c.close();

        // Hotlist:
        c = vdb.getView(ViewNames.HOTLIST, "");
        if (c.moveToFirst())
            pref.addEntry(c.getLong(0), getString(R.string.Hotlist));
        c.close();

        // Due Today/Tomorrow:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            c = vdb.getView(ViewNames.DUE_TODAY_TOMORROW, "");
            if (c.moveToFirst())
                pref.addEntry(c.getLong(0), getString(R.string.DueTodayTomorrow));
            c.close();
        }

        // Overdue:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            c = vdb.getView(ViewNames.OVERDUE, "");
            if (c.moveToFirst())
                pref.addEntry(c.getLong(0), getString(R.string.Overdue));
            c.close();
        }

        // Starred:
        if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
        {
            c = vdb.getView(ViewNames.STARRED, "");
            if (c.moveToFirst())
                pref.addEntry(c.getLong(0), getString(R.string.Starred));
            c.close();
        }

        // Recently Completed:
        c = vdb.getView(ViewNames.RECENTLY_COMPLETED, "");
        if (c.moveToFirst())
            pref.addEntry(c.getLong(0), getString(R.string.RecentlyCompleted));
        c.close();

        // My Views:
        c = vdb.getViewsByLevel(ViewNames.MY_VIEWS);
        if (c!=null && c.getCount()>0)
        {
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                pref.addEntry(c.getLong(0), getString(R.string.MyViews)+" / "+
                        Util.cString(c, "view_name"));
            }
        }
        if (c!=null) c.close();

        // By Status:
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
            String[] statuses = getResources().getStringArray(R.array.statuses);
            for (i=0; i<statuses.length; i++)
            {
                c = vdb.getView(ViewNames.BY_STATUS, Integer.valueOf(i).toString());
                if (c.moveToFirst())
                {
                    pref.addEntry(c.getLong(0), getString(R.string.ByStatus)+" / "+
                            statuses[i]);
                }
            }
        }

        // We need to know how many accounts we have, since this affects the display:
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        Cursor c2 = accountsDB.getAllAccounts();
        int numAccounts = c2.getCount();
        c2.close();

        // Folders:
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
			// Put the "no folder" view in.
			c = vdb.getView(ViewNames.FOLDERS,"0");
			if (c.moveToFirst())
			{
				pref.addEntry(c.getLong(0),getString(R.string.Folders)+" / "+getString(R.string.
					No_Folder));
			}
			c.close();

            // Count the number of Toodledo versus other accounts.  This affects the sort order.
            c = accountsDB.getAllAccounts();
            c.moveToPosition(-1);
            int numToodledo = 0;
            int numOther = 0;
            while (c.moveToNext())
            {
                UTLAccount a = accountsDB.getAccount(Util.cLong(c, "_id"));
                if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
                    numToodledo++;
                else
                    numOther++;
            }
            c.close();

            // Start a query for the folders, using the chosen sort order:
            FoldersDbAdapter foldersDB = new FoldersDbAdapter();
            if (numToodledo>0 && numOther==0)
                c = foldersDB.getFoldersByOrder();
            else
                c = foldersDB.getFoldersByNameNoCase();
            c.moveToPosition(-1);

            // Populate the options:
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.FOLDERS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    pref.addEntry(c2.getLong(0), getString(R.string.Folders)+" / "+
                        nameToDisplay);
                }
                c2.close();
            }
            c.close();
        }

        // Contexts:
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
			// Put the "no context" view in.
			c = vdb.getView(ViewNames.CONTEXTS,"0");
			if (c.moveToFirst())
			{
				pref.addEntry(c.getLong(0),getString(R.string.Contexts)+" / "+getString(R.string.
					No_Context));
			}
			c.close();

			ContextsDbAdapter contextsDB = new ContextsDbAdapter();
            c = contextsDB.getContextsByNameNoCase();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.CONTEXTS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    pref.addEntry(c2.getLong(0), getString(R.string.Contexts)+" / "+
                            nameToDisplay);
                }
                c2.close();
            }
            c.close();
        }

        // Goals:
        if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
			// Put the "no goal" view in.
			c = vdb.getView(ViewNames.GOALS,"0");
			if (c.moveToFirst())
			{
				pref.addEntry(c.getLong(0),getString(R.string.Goals)+" / "+getString(R.string.
					No_Goal));
			}
			c.close();

			GoalsDbAdapter goalsDB = new GoalsDbAdapter();
            c = goalsDB.getAllGoalsNoCase();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.GOALS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    pref.addEntry(c2.getLong(0), getString(R.string.Goals)+" / "+
                            nameToDisplay);
                }
                c2.close();
            }
            c.close();
        }

        // Tags:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            CurrentTagsDbAdapter currentTagsDB = new CurrentTagsDbAdapter();
            c = currentTagsDB.getTags();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "name");
                c2 = vdb.getView(ViewNames.TAGS, nameToDisplay);
                if (c2.moveToFirst())
                {
                    pref.addEntry(c2.getLong(0), getString(R.string.Tags)+" / "+
                            nameToDisplay);
                }
                c2.close();
            }
            c.close();
        }

        // Locations:
        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        {
			// Put the "no location" view in.
			c = vdb.getView(ViewNames.LOCATIONS,"0");
			if (c.moveToFirst())
			{
				pref.addEntry(c.getLong(0),getString(R.string.Locations)+" / "+getString(R.string.
					No_Location));
			}
			c.close();

			LocationsDbAdapter locDB = new LocationsDbAdapter();
            c = locDB.getAllLocations();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.LOCATIONS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    pref.addEntry(c2.getLong(0), getString(R.string.Locations)+" / "+
                            nameToDisplay);
                }
                c2.close();
            }
            c.close();
        }

        pref.finalizeEntries();
    }

	@Override
	public void onDestroy()
	{
		if (_pm!=null)
			_pm.unlinkFromBillingService();
		super.onDestroy();
	}
}
