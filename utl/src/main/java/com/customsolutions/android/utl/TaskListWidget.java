package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

public class TaskListWidget extends AppWidgetProvider
{
	private static final String TAG = "TaskListWidget";

	/** Background colors for each theme. These are resource IDs. */
	public static final int[] BACKGROUND_COLORS = new int[] {
		R.color.white,
		R.color.white,
		R.color.white,
		R.color.almost_black,
		R.color.black,
		R.color.black
	};

	/** Resource IDs for each theme to use for the widget's action bar background. */
	public static final int[] ACTION_BAR_BACKGROUNDS = new int[] {
		R.color.cyan_dark,
		R.color.boston_blue,
		R.drawable.tb_background_light,
		R.color.cyan_light,
		R.color.cyan_light,
		R.drawable.tb_background_dark
	};

	/** Drawable IDs to use for each theme for the top settings button. */
	public static final int[] SETTINGS_ICONS = new int[] {
		R.drawable.nav_settings_dark,
		R.drawable.nav_settings_dark,
		R.drawable.nav_settings_light,
		R.drawable.nav_settings_dark,
		R.drawable.nav_settings_dark,
		R.drawable.nav_settings_dark
	};

	/** Drawable IDs to use for each theme for the new task button. */
	public static final int[] ADD_TASK_ICONS = new int[] {
		R.drawable.nav_add_dark,
		R.drawable.nav_add_dark,
		R.drawable.nav_add_light,
		R.drawable.nav_add_dark,
		R.drawable.nav_add_dark,
		R.drawable.nav_add_dark
	};

	/** Drawable IDs to use for each theme for the refresh button. */
	public static final int[] REFRESH_ICONS = new int[] {
		R.drawable.nav_refresh_dark,
		R.drawable.nav_refresh_dark,
		R.drawable.nav_refresh_light,
		R.drawable.nav_refresh_dark,
		R.drawable.nav_refresh_dark,
		R.drawable.nav_refresh_dark
	};

	/** The text color to use for each theme for the action bar. */
	public static final int[] ACTION_BAR_TEXT_COLORS = new int[] {
		R.color.white,
		R.color.white,
		R.color.black,
		R.color.white,
		R.color.white,
		R.color.white
	};

	/** The main text color to use for each theme: */
	public static final int[] TEXT_COLORS = new int[] {
		R.color.black,
		R.color.black,
		R.color.black,
		R.color.white,
		R.color.white,
		R.color.white
	};

	// Gives us access to the Views in our layout:
	private RemoteViews _views;
	
	// Info on the view we're working with:
	private Cursor _viewCursor;
	private long _viewID;
    
    private DisplayOptions _displayOptions;

    // The maximum number of tasks the layout supports:
    private static final int MAX_TASKS = 40;
    
    // Stores midnight on the current day:
    private long _midnightToday;
    
    // A reference to the settings/preferences.  We can't use the reference in Util
    // due to separate threads accessing this.
    private SharedPreferences settings;
        
    // Stores the offset between the current time zone and home time zone:
    private long _timeZoneOffset;
    
    @SuppressLint("InlinedApi")
	@Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
        int[] appWidgetIds) 
	{
        Util.appInit(context);
        
        boolean hasSem = false;
        if (!Synchronizer.isSyncing())
			hasSem = Util.acquireSemaphore("TaskListWidget", context);
        try
        {
	        settings = context.getSharedPreferences("UTL_Prefs",0);
	        
	        Util.log("Updating "+appWidgetIds.length+" Task List Widgets.  Class: "+
	        	this.getClass().getName());
	
	        // Run a sync if needed:
	        Util.doMinimalSync(context);
	        
	    	// Get the offset in ms between the home time zone and the local one:
	    	TimeZone sysTimeZone = TimeZone.getDefault();
	    	TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
	    		"home_time_zone", "America/Los_Angeles"));
	    	_timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) - 
				appTimeZone.getOffset(System.currentTimeMillis());
	    	
	    	// Get the timestamp in ms at midnight today.  This is needed for due date
	    	// color-coding.
	    	_midnightToday = Util.getMidnight(System.currentTimeMillis()+_timeZoneOffset);
	    	
	        // Perform this loop procedure for each App Widget that belongs to this provider
	        for (int i=0; i<appWidgetIds.length; i++) 
	        {
	        	int appWidgetId = appWidgetIds[i];
	        	
	        	// Get the data on the view to display:
	            _viewCursor = (new ViewsDbAdapter()).getView("widget",new Integer(appWidgetId).toString());
	            if (!_viewCursor.moveToFirst())
	            {
	                Util.log("View is not defined in TaskListWidget.OnUpdate().");
	                return;
	            }
	            _viewID = Util.cLong(_viewCursor, "_id");
	            
	            // Initialize the Display Options:
	            _displayOptions = new DisplayOptions(Util.cString(_viewCursor, "display_string"));
	            
	        	// Get the layout for the widget, based on the display options:
				if (_displayOptions.widgetFormat==0)
				{
					// Compact:
					_views = new RemoteViews(context.getPackageName(), R.layout.
						widget_task_list_scrollable);
				}
				else
				{
					// Normal:
					_views = new RemoteViews(context.getPackageName(), R.layout.
						widget_task_list_scrollable2);
				}

				// Set some colors and icons based on the theme:
				Resources r = context.getResources();
				int t = _displayOptions.widgetTheme;
				_views.setInt(R.id.widget_task_list_container,"setBackgroundColor",
					r.getColor(BACKGROUND_COLORS[t]));
				_views.setInt(R.id.widget_task_list_top_bar_container,"setBackgroundResource",
					ACTION_BAR_BACKGROUNDS[t]);
				_views.setTextColor(R.id.widget_task_list_title,r.getColor(ACTION_BAR_TEXT_COLORS
					[t]));
				_views.setImageViewResource(R.id.widget_task_list_settings, SETTINGS_ICONS[t]);
				_views.setImageViewResource(R.id.widget_task_list_add, ADD_TASK_ICONS[t]);
				_views.setImageViewResource(R.id.widget_task_list_refresh, REFRESH_ICONS[t]);
				_views.setTextColor(R.id.widget_task_list_empty,r.getColor(TEXT_COLORS[t]));
	        	
	            // Create an Intent that triggers a refresh of the widget:
	            Uri.Builder uriBuilder = new Uri.Builder();
	            Intent intent2 = new Intent(context,this.getClass());
	            intent2.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intent2.putExtra("appWidgetIds",new int[] {appWidgetId});
				uriBuilder.scheme("widget_refresh");
				uriBuilder.opaquePart(Integer.valueOf(appWidgetId).toString());
				intent2.setData(uriBuilder.build());
				PendingIntent pendingIntent2 = PendingIntent.getBroadcast(context, 0, intent2,
					PendingIntent.FLAG_IMMUTABLE);
				
				// When the user taps on the refresh image, send the refresh Intent:
				_views.setOnClickPendingIntent(R.id.widget_task_list_refresh, pendingIntent2);
				
				// Create an Intent that opens the widget options:
				Uri.Builder uriBuilder3 = new Uri.Builder();
	        	uriBuilder3.scheme("widget_options");
	        	uriBuilder3.opaquePart(Integer.valueOf(appWidgetId).toString());
	        	Intent intent3 = new Intent(Intent.ACTION_VIEW,uriBuilder3.build(),context,
	        		WidgetOptions.class);
	            intent3.putExtra("top_level", "widget");
	            intent3.putExtra("view_name",Integer.valueOf(appWidgetId).toString());
	            intent3.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	            intent3.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
	            PendingIntent pendingIntent3 = PendingIntent.getActivity(context, 0, intent3,
					PendingIntent.FLAG_IMMUTABLE);
				
				// When the user taps on the settings image, send the settings Intent:
				_views.setOnClickPendingIntent(R.id.widget_task_list_settings, pendingIntent3);
				_views.setOnClickPendingIntent(R.id.widget_task_list_title,pendingIntent3);
	
				// Create an Intent for creating a new task:
				int screenSize = context.getResources().getConfiguration().screenLayout & Configuration.
					SCREENLAYOUT_SIZE_MASK;
				Intent newTaskIntent;
				newTaskIntent = new Intent(context,EditTaskPopup.class);
				newTaskIntent.putExtra("action", EditTaskFragment.ADD);
				newTaskIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
				newTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				PendingIntent newTaskPendingIntent = PendingIntent.getActivity(context, 0, newTaskIntent, 
					PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
				
	            // Display the Title at the top of the widget:
	            if (_displayOptions.widgetTitle.length()>0)
	            {
	            	_views.setTextViewText(R.id.widget_task_list_title,Html.fromHtml("<b>"+
	            		_displayOptions.widgetTitle+"</b>"));
	            }
	            else
	            {
	            	_views.setTextViewText(R.id.widget_task_list_title,Html.fromHtml("<b>"+
	            		Util.getString(R.string.To_Do_List_Underline)+"</b>"));
	            }

				// Set the Intent that is sent when tapping on the New Task button:
				_views.setOnClickPendingIntent(R.id.widget_task_list_add, newTaskPendingIntent);

				// We need to make sure the database records this as a scrollable widget
				_displayOptions.widgetIsScrollable = 1;
				(new ViewsDbAdapter()).changeDisplayOptions(_viewID, _displayOptions);

				scrollableWidgetHandler(context, appWidgetId, appWidgetManager);
	        }
	        Util.log("Done with widget update.  Class: "+this.getClass().getName());
        }
        finally
        {
        	if (hasSem)
        		Util._semaphore.release();
        }
	}
                                                                                                              
    // Handler for the scrollable widget for Android 3+:
    @SuppressLint("NewApi")
	private void scrollableWidgetHandler(Context context, int appWidgetId, 
    	AppWidgetManager appWidgetManager)
    {
    	Intent intent;
    	
    	// Set up the Intent which starts the ScrollableWidgetService, which provides the
    	// views to display:
        if (_displayOptions.widgetFormat==1)
        {
        	// Create a blank intent template as the action to execute when tapping on a row:
        	PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(
        		context,TaskUpdateReceiver.class), PendingIntent.FLAG_MUTABLE);
        	_views.setPendingIntentTemplate(R.id.widget_task_list_listview, pendingIntent);
        	
        	// This intent specifies which service will be providing the views for the rows in the
            // list:
			Log.v(TAG,"Using Normal view");
        	intent = new Intent(context,ScrollableWidgetServiceNormal.class);
        }
        else
        {
            // Create an Intent that launches the app's full size task list:
        	Uri.Builder uriBuilder = new Uri.Builder();
        	uriBuilder.scheme("tasklist");
        	uriBuilder.opaquePart(Integer.valueOf(appWidgetId).toString());
        	intent = new Intent(Intent.ACTION_VIEW,uriBuilder.build(),context,
        		TaskList.class);
            intent.putExtra("top_level", "widget");
            intent.putExtra("view_name", Integer.valueOf(appWidgetId).toString());
            intent.putExtra("title", Util.getString(R.string.Tasks_in_Widget2));
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
				PendingIntent.FLAG_IMMUTABLE);
            
            // Launch the full size task list when the user taps on the list:
            _views.setPendingIntentTemplate(R.id.widget_task_list_listview, pendingIntent);
            
            // This intent specifies which service will be providing the views for the rows in the
            // list:
			Log.v(TAG,"Using compact view");
        	intent = new Intent(context,ScrollableWidgetServiceCompact.class);
        }
    	intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        
        // Set up the RemoteViews object to use a RemoteViews adapter. 
        // This adapter connects
        // to a RemoteViewsService  through the specified intent.
        // This is how you populate the data.
        _views.setRemoteAdapter(R.id.widget_task_list_listview, intent);
        _views.setEmptyView(R.id.widget_task_list_listview, R.id.widget_task_list_empty);
        
        appWidgetManager.updateAppWidget(appWidgetId, _views);
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_task_list_listview);
    }
}
