package com.customsolutions.android.utl;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

/** Displays a list of features (fields/functions) which the user can enable or disable. */
public class FeatureSelection extends UtlActivity
{
    private static final String TAG = "FeatureSelection";

    /** Intent extra specifying if this is being called during initial setup. Set this to false
     * when calling this from the preferences screen. */
    public static final String EXTRA_INITIAL_SETUP = "initial_setup";

    /** The list of feature groups and features. */
    private FeatureGroup[] _featureGroups;

    /** Quick reference to shared preferences. */
    private SharedPreferences _prefs;

    /** The ExpandableListView containing the features. */
    private ExpandableListView _listView;

    /** The adapter to map data to views. */
    private ListAdapter _listAdapter;

    /** Do we have a Google account (this affects what can/cannot be disabled) */
    private boolean _googleAccountExists;

    /** Flag indicating if we have a Toodledo account. */
    private boolean _toodledoAccountExists;

    /** Flag indicating if this is being called as part of the initial setup. */
    private boolean _isInitialSetup;

    /** Get the feature information for use here. */
    private FeatureGroup[] getFeatures()
    {
        return new FeatureGroup[] {
            new FeatureGroup(getString(R.string.common_features),getString(R.string.
                almost_everyone),new Feature[] {
                new Feature(
                    PrefNames.DUE_DATE_ENABLED,
                    getString(R.string.DueDate),
                    getString(R.string.due_date_description)
                ),
                new Feature(
                    PrefNames.REMINDER_ENABLED,
                    getString(R.string.Reminders),
                    getString(R.string.reminders_description)
                ),
                new Feature(
                    PrefNames.REPEAT_ENABLED,
                    getString(R.string.Repeating_Tasks),
                    getString(R.string.repeating_description)
                ),
                new Feature(
                    PrefNames.PRIORITY_ENABLED,
                    getString(R.string.Priority),
                    getString(R.string.priority_description)
                ),
                new Feature(
                    PrefNames.FOLDERS_ENABLED,
                    getString(R.string.Folders_for_Tasks),
                    getString(R.string.folder_description)
                ),
                new Feature(
                    PrefNames.NOTE_FOLDERS_ENABLED,
                    getString(R.string.Folders_for_Notes),
                    getString(R.string.folder_description)
                )
            }),
            new FeatureGroup(getString(R.string.time_management),getString(R.string.precisely_plan),
                new Feature[] {
                new Feature(
                    PrefNames.DUE_TIME_ENABLED,
                    getString(R.string.Due_Time),
                    getString(R.string.due_time_description)
                ),
                new Feature(
                    PrefNames.START_DATE_ENABLED,
                    getString(R.string.StartDate),
                    getString(R.string.start_date_description)
                ),
                new Feature(
                    PrefNames.START_TIME_ENABLED,
                    getString(R.string.Start_Time),
                    getString(R.string.start_time_description)
                ),
                new Feature(
                    PrefNames.CALENDAR_ENABLED,
                    getString(R.string.Calendar_Integration),
                    getString(R.string.calendar_description)
                )
            }),
            new FeatureGroup(getString(R.string.task_organization),getString(R.string.
                group_and_arrange), new Feature[] {
                new Feature(
                    PrefNames.STAR_ENABLED,
                    getString(R.string.Star),
                    getString(R.string.star_description)
                ),
                new Feature(
                    PrefNames.SUBTASKS_ENABLED,
                    getString(R.string.Subtasks),
                    getString(R.string.subtasks_description)
                ),
                new Feature(
                    PrefNames.CONTEXTS_ENABLED,
                    getString(R.string.Contexts),
                    getString(R.string.contexts_description)
                ),
                new Feature(
                    PrefNames.GOALS_ENABLED,
                    getString(R.string.Goals),
                    getString(R.string.goals_description)
                ),
                new Feature(
                    PrefNames.LOCATIONS_ENABLED,
                    getString(R.string.Locations),
                    getString(R.string.locations_description)
                ),
                new Feature(
                    PrefNames.TAGS_ENABLED,
                    getString(R.string.Tags),
                    getString(R.string.tags_description)
                )
            }),
            new FeatureGroup(getString(R.string.project_management),getString(R.string.
                advanced_features), new Feature[] {
                new Feature(
                    PrefNames.LENGTH_ENABLED,
                    getString(R.string.Expected_Length),
                    getString(R.string.expected_length_description)
                ),
                new Feature(
                    PrefNames.TIMER_ENABLED,
                    getString(R.string.timer_actual_length),
                    getString(R.string.timer_description)
                ),
                new Feature(
                    PrefNames.STATUS_ENABLED,
                    getString(R.string.Status),
                    getString(R.string.status_description)
                ),
                new Feature(
                    PrefNames.CONTACTS_ENABLED,
                    getString(R.string.Contacts),
                    getString(R.string.contacts_description)
                ),
                new Feature(
                    PrefNames.COLLABORATORS_ENABLED,
                    getString(R.string.collaboration_for_td),
                    getString(R.string.collaboration_description)
                )
            })
        };
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.feature_selection);

        _featureGroups = getFeatures();
        _prefs = getSharedPreferences(Util.PREFS_NAME,0);

        _listView = findViewById(R.id.feature_list);
        _listAdapter = new ListAdapter();
        _listView.setAdapter(_listAdapter);

        // Check to see if we have a Google account:
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        Cursor c = accountsDB.getAllAccounts();
        _googleAccountExists = false;
        _toodledoAccountExists = false;
        while (c.moveToNext())
        {
            UTLAccount a = accountsDB.getUTLAccount(c);
            if (a.sync_service==UTLAccount.SYNC_GOOGLE)
                _googleAccountExists = true;
            if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
                _toodledoAccountExists = true;
        }
        c.close();

        // Check the extras passed in to see if this is the initial setup or if this has
        // been invoked from the settings screen. Set the introductory text accordingly.
        _isInitialSetup = true;
        Intent i = getIntent();
        if (i!=null)
            _isInitialSetup = i.getBooleanExtra(EXTRA_INITIAL_SETUP,true);
        String introText;
        if (_isInitialSetup)
        {
            introText = getString(R.string.Choose_Features)+"\n\n"+getString(R.string.
                tap_on_group);
        }
        else
            introText = getString(R.string.features_intro)+" "+getString(R.string.tap_on_group);
        TextView introView = (TextView) getLayoutInflater().inflate(R.layout.feature_header,null);
        introView.setText(introText);
        _listView.addHeaderView(introView);

        // Set the title at the top:
        ActionBar actionBar = getSupportActionBar();
        if (actionBar!=null)
        {
            if (_isInitialSetup)
                actionBar.setTitle(R.string.choose_your_features);
            else
                actionBar.setTitle(R.string.Fields_Functions_Used);
        }

        // Only show banner ads in portrait mode unless this is a tablet. There's not enough
        // vertical space in landscape on phones.
        if (getOrientation()==ORIENTATION_PORTRAIT || _prefs.getFloat(PrefNames.
            DIAGONAL_SCREEN_SIZE,5)>=7.0)
        {
            initBannerAd();
        }

        if (_isInitialSetup)
        {
            // Show the "Done" button and add a handler.
            findViewById(R.id.feature_done_button).setVisibility(View.VISIBLE);
            findViewById(R.id.feature_done_button).setOnClickListener((View v) -> {
                Util.logOneTimeEvent(FeatureSelection.this,"chose_features",0,null);
                exit();
            });
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    /** Handle the enabling or disabling of a feature. This takes care of needed permission and
     * dependency checks. */
    private void handleFeatureChange(String prefKey, boolean isEnabled)
    {
        Log.i(TAG,"Feature change: "+prefKey+" : "+isEnabled);
        if (!isEnabled)
        {
            // When disabling a feature, no permission checks are needed, but if due date and
            // or start date are disabled, due time or start time must also be disabled.
            _prefs.edit().putBoolean(prefKey,false).apply();
            if (prefKey.equals(PrefNames.DUE_DATE_ENABLED))
            {
                // Don't allow unselection of due dates if reminders are enabled if there's a
                // Toodledo account. Toodledo requires that reminders be stored relative to the
                // due date/time.
                if (_toodledoAccountExists && _prefs.getBoolean(PrefNames.REMINDER_ENABLED,true))
                {
                    _prefs.edit().putBoolean(PrefNames.DUE_DATE_ENABLED,true).apply();
                    Util.popup(this,R.string.due_date_required);
                }
                else
                    _prefs.edit().putBoolean(PrefNames.DUE_TIME_ENABLED, false).apply();
            }
            if (prefKey.equals(PrefNames.START_DATE_ENABLED))
                _prefs.edit().putBoolean(PrefNames.START_TIME_ENABLED,false).apply();

            // Don't allow unselection of folders if there's a google account.
            if (prefKey.equals(PrefNames.FOLDERS_ENABLED) && _googleAccountExists)
            {
                _prefs.edit().putBoolean(PrefNames.FOLDERS_ENABLED,true).apply();
                Util.popup(this,R.string.folders_required);
            }
        }
        else
        {
            // Check dependencies:
            boolean allowChange = true;
            if (prefKey.equals(PrefNames.DUE_TIME_ENABLED) && !_prefs.getBoolean(PrefNames.
                DUE_DATE_ENABLED,true))
            {
                // Trying to enable due time without due date.
                Util.popup(this,R.string.due_time_without_due_date);
                allowChange = false;
            }
            if (prefKey.equals(PrefNames.START_TIME_ENABLED) && !_prefs.getBoolean(PrefNames.
                START_DATE_ENABLED,true))
            {
                // Trying to enable due time without due date.
                Util.popup(this,R.string.start_time_without_start_date);
                allowChange = false;
            }

            // Check for preferences requiring permissions:
            if (prefKey.equals(PrefNames.CALENDAR_ENABLED))
            {
                allowChange = false;
                requestPermissions(
                    new String[]{Manifest.permission.READ_CALENDAR, Manifest.
                        permission.WRITE_CALENDAR},
                    () -> {
                        _prefs.edit().putBoolean(PrefNames.CALENDAR_ENABLED,true).apply();
                        _listAdapter.notifyDataSetChanged();
                    },
                    true,
                    null,
                    () -> {
                        Util.popup(FeatureSelection.this,R.string.calendar_cannot_be_enabled);
                        _prefs.edit().putBoolean(PrefNames.CALENDAR_ENABLED,false).apply();
                        _listAdapter.notifyDataSetChanged();
                    }
                );
            }
            if (prefKey.equals(PrefNames.CONTACTS_ENABLED))
            {
                allowChange = false;
                requestPermissions(
                    new String[]{Manifest.permission.READ_CONTACTS},
                    () -> {
                        _prefs.edit().putBoolean(PrefNames.CONTACTS_ENABLED,true).apply();
                        _listAdapter.notifyDataSetChanged();
                    },
                    true,
                    null,
                    () -> {
                        Util.popup(FeatureSelection.this,R.string.contacts_cannot_be_enabled);
                        _prefs.edit().putBoolean(PrefNames.CONTACTS_ENABLED,false).apply();
                        _listAdapter.notifyDataSetChanged();
                    }
                );
            }
            if (prefKey.equals(PrefNames.LOCATIONS_ENABLED))
            {
                allowChange = false;
                String[] locationPermissions;
                if (Build.VERSION.SDK_INT>Build.VERSION_CODES.Q)
                {
                    locationPermissions = new String[] {Manifest.permission.
                        ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
                }
                else if (Build.VERSION.SDK_INT>Build.VERSION_CODES.P)
                {
                    locationPermissions = new String[] {Manifest.permission.
                        ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION};
                }
                else
                {
                    locationPermissions = new String[] {Manifest.permission.
                        ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
                }
                if (Util.arePermissionsGranted(this,locationPermissions))
                {
                    if (Build.VERSION.SDK_INT<=Build.VERSION_CODES.Q)
                    {
                        // All permissions are already granted, so we can enable the features.
                        allowChange = true;
                    }
                    else
                    {
                        // On Android 11+, background location must be handled separately.
                        checkBackgroundLocation();
                    }
                }
                else
                {
                    // To appease Google, we need a prominent disclosure here:
                    new AlertDialog.Builder(this)
                        .setMessage(R.string.location_disclosure)
                        .setPositiveButton(R.string.OK, (dialog, which) -> {
                            requestPermissions(
                                locationPermissions,
                                () -> {
                                    checkBackgroundLocation();
                                },
                                true,
                                null,
                                () -> {
                                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P)
                                    {
                                        Util.popup(FeatureSelection.this, R.string.
                                            locations_cannot_be_enabled2);
                                    }
                                    else
                                    {
                                        Util.popup(FeatureSelection.this, R.string.
                                            locations_cannot_be_enabled);
                                    }
                                    _prefs.edit().putBoolean(PrefNames.LOCATIONS_ENABLED, false).
                                        apply();
                                    _listAdapter.notifyDataSetChanged();
                                }
                            );
                        })
                        .setNegativeButton(R.string.Cancel,null)
                        .show();
                }
            }

            if (allowChange)
                _prefs.edit().putBoolean(prefKey,true).apply();
        }

        _listAdapter.notifyDataSetChanged();
    }

    /** Check to see if we need to request access to background location and prompt if
     * necessary. Call this after verifying foreground location has been granted. */
    private void checkBackgroundLocation()
    {
        if (Build.VERSION.SDK_INT<=Build.VERSION_CODES.Q)
        {
            _prefs.edit().putBoolean(PrefNames.LOCATIONS_ENABLED, true).
                apply();
            _listAdapter.notifyDataSetChanged();
            return;
        }

        if (Util.arePermissionsGranted(this,new String[]{Manifest.permission.
            ACCESS_BACKGROUND_LOCATION}))
        {
            _prefs.edit().putBoolean(PrefNames.LOCATIONS_ENABLED, true).apply();
            _listAdapter.notifyDataSetChanged();
            return;
        }

        String label = getPackageManager().getBackgroundPermissionOptionLabel()
            .toString();
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.background_location_instructions).replace("[option]",
                label))
            .setNegativeButton(R.string.Cancel,null)
            .setPositiveButton(R.string.OK,(DialogInterface d, int which) -> {
                final long requestStart = System.currentTimeMillis();
                requestPermissions(new String[] {Manifest.permission.
                    ACCESS_BACKGROUND_LOCATION},
                    () -> {
                        // Permission granted. Enable the location preference and refresh.
                        _prefs.edit().putBoolean(PrefNames.LOCATIONS_ENABLED, true).
                            apply();
                        _listAdapter.notifyDataSetChanged();
                    },
                    true,
                    null,
                    () -> {
                        // Permission denied.
                        long timeDiff = System.currentTimeMillis() - requestStart;
                        if (_prefs.getBoolean(PrefNames.HAS_DENIED_BACKGROUND_LOCATION,
                            false) && timeDiff<500)
                        {
                            // The user has previously denied background location
                            // permission, and it looks like Android didn't display
                            // the dialog at all.  Take the user to the system's app
                            // settings page.  That's the best we can do.
                            Log.d(TAG,"Permissions dialog returned too quickly. "+
                                "Launch system's settings screen for app.");
                            Util.popup(this,R.string.go_to_permissions);
                            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            i.setData(Uri.fromParts(
                                "package",
                                getPackageName(),
                                null
                            ));
                            startActivity(i);
                        }
                        _prefs.edit().putBoolean(PrefNames.HAS_DENIED_BACKGROUND_LOCATION,
                            true).apply();
                    });
            })
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId()==android.R.id.home && _isInitialSetup)
        {
            // During initial setup, we need to open the startup Activity and exit.
            exit();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Special handling for the back button. */
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK && _isInitialSetup)
        {
            // On initial setup, we exit while opening the startup Activity.
            exit();
            return true;
        }
        else
            return super.onKeyDown(keyCode, event);
    }

    /** Exit this Activity, and open the initial task list if this is the initial setup. */
    private void exit()
    {
        if (_isInitialSetup)
            Util.openStartupView(this);
        finish();
    }

    @Override
    public void onPause()
    {
        if (_isInitialSetup)
            Stats.uploadStats(this,null);
        super.onPause();
    }

    /** Fills in the contents of the feature list. */
    private class ListAdapter extends BaseExpandableListAdapter
    {
        @Override
        public Object getChild(int listPosition, int expandedListPosition)
        {
            return _featureGroups[listPosition].features[expandedListPosition];
        }

        @Override
        public long getChildId(int listPosition, int expandedListPosition)
        {
            return listPosition*100 + expandedListPosition+1;
        }

        @Override
        public View getChildView(int listPosition, final int expandedListPosition,
            boolean isLastChild, View convertView, ViewGroup parent)
        {
            // Get a reference to the root view of this child:
            LayoutInflater inflater = getLayoutInflater();
            LinearLayout childRoot = (LinearLayout) inflater.inflate(R.layout.feature_item,null);
            Feature feature = _featureGroups[listPosition].features[expandedListPosition];
            childRoot.setTag(feature.key);

            // Set the checkbox based on whether the feature is enabled:
            CheckBox cb = (CheckBox)childRoot.findViewById(R.id.feature_item_checkbox);
            cb.setChecked(_prefs.getBoolean(feature.key,true));
            cb.setTag(feature.key);

            // Set the TextView containing the feature name:
            TextView tv = (TextView)childRoot.findViewById(R.id.feature_item_name);
            tv.setText(feature.name);

            // A tap on a child item enables or disables the feature:
            childRoot.setOnClickListener((View v) -> {
                String prefKey = (String)v.getTag();
                boolean newValue = !_prefs.getBoolean(prefKey,true);
                handleFeatureChange(prefKey,newValue);
            });

            // Handle a tap on the checkbox:
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    String prefKey = (String)buttonView.getTag();
                    handleFeatureChange(prefKey,isChecked);
                }
            });

            // Handle a tap on the info button:
            ImageView infoButton = childRoot.findViewById(R.id.feature_item_info_button);
            infoButton.setTag(feature);
            infoButton.setOnClickListener((View v) -> {
                Feature f = (Feature) v.getTag();
                Util.longerPopup(FeatureSelection.this,f.name,f.extraInfo);
            });

            // Show the separator if this is the last child:
            View separator = childRoot.findViewById(R.id.feature_item_separator);
            if (isLastChild)
                separator.setVisibility(View.VISIBLE);
            else
                separator.setVisibility(View.GONE);

            return childRoot;
        }

        @Override
        public int getChildrenCount(int listPosition)
        {
            return _featureGroups[listPosition].features.length;
        }

        @Override
        public Object getGroup(int listPosition)
        {
            return _featureGroups[listPosition];
        }

        @Override
        public int getGroupCount()
        {
            return _featureGroups.length;
        }

        @Override
        public long getGroupId(int listPosition)
        {
            return listPosition*100;
        }

        @Override
        public View getGroupView(int listPosition, boolean isExpanded, View convertView,
            ViewGroup parent)
        {
            // Get a reference to the root view of this child:
            LayoutInflater inflater = getLayoutInflater();
            LinearLayout groupRoot = (LinearLayout) inflater.inflate(R.layout.feature_group_header,null);

            // Set the name of the group and description:
            FeatureGroup featureGroup = _featureGroups[listPosition];
            TextView tv = groupRoot.findViewById(R.id.feature_group_title);
            tv.setText(featureGroup.name);
            tv = groupRoot.findViewById(R.id.feature_group_description);
            tv.setText(featureGroup.description);

            // Set the checkbox state based on how many features are enabled:
            TriStateCheckbox cb = groupRoot.findViewById(R.id.feature_group_checkbox);
            int numEnabled = 0;
            for (int i=0; i<featureGroup.features.length; i++)
            {
                if (_prefs.getBoolean(featureGroup.features[i].key,true))
                    numEnabled++;
            }
            if (numEnabled==0)
                cb.setState(TriStateCheckbox.UNCHECKED);
            else if (numEnabled<featureGroup.features.length)
                cb.setState(TriStateCheckbox.INDETERMINATE);
            else
                cb.setState(TriStateCheckbox.CHECKED);
            cb.setTag(featureGroup);

            // Handle clicks on the checkbox, when enable or disable groups of features.
            cb.setOnClickListener((View v) -> {
                FeatureGroup fg = (FeatureGroup) v.getTag();
                TriStateCheckbox tsc = (TriStateCheckbox) v;
                switch (tsc.getState())
                {
                    case TriStateCheckbox.CHECKED:
                        tsc.setState(TriStateCheckbox.UNCHECKED);
                        for (int i=0; i<fg.features.length; i++)
                            handleFeatureChange(fg.features[i].key,false);
                        break;

                    case TriStateCheckbox.INDETERMINATE:
                    case TriStateCheckbox.UNCHECKED:
                        tsc.setState(TriStateCheckbox.CHECKED);
                        for (int i=0; i<fg.features.length; i++)
                            handleFeatureChange(fg.features[i].key,true);
                        break;
                }
            });

            return groupRoot;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int listPosition, int expandedListPosition) {
            return false;
        }
    }

    /** Represents a single feature. */
    private class Feature
    {
        /** The SharedPreferences key. */
        public String key;

        /** The name, as displayed to the user. */
        public String name;

        /** The text to display if the user taps on the button for more information. */
        public String extraInfo;

        /** The constructor. */
        public Feature(String newKey, String newName, String newExtraInfo)
        {
            key = newKey;
            name = newName;
            extraInfo = newExtraInfo;
        }
    }

    /** Represents a group of features. */
    private class FeatureGroup
    {
        /** The name of the group. */
        public String name;

        /** The description of the group. */
        public String description;

        /** The features in the group. */
        public Feature[] features;

        /** The constructor. */
        public FeatureGroup(String newName, String newDescription, Feature[] newFeatures)
        {
            name = newName;
            description = newDescription;
            features = newFeatures;
        }
    }
}
