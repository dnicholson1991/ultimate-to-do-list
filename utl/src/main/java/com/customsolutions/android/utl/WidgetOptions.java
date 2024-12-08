package com.customsolutions.android.utl;

// Launcher for the various widget options screens.

// To call this activity, put a Bundle in the Intent with the following keys/values:
// top_level: A string containing the originating home screen option (all_tasks, hotlist...)
// view_name: A string containing the view name (such as a custom name or folder ID).
//   If this is not needed, set it to an empty string.

import java.util.ArrayList;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class WidgetOptions extends UtlPopupActivity
{
	private String _topLevel;
	private String _viewName;
	private long _viewID;
	
	// Codes for the activities we can launch:
	private static final int ACTIVITY_FILTER = 1;
	private static final int ACTIVITY_SORT = 2;
	private static final int ACTIVITY_DISPLAY = 3;
	
	// Holds the possible views we can copy from:
	private ArrayList<ViewInfo> _viewList;
	
	/** Called when first started, or when resumed after being in the background: */
	@Override
	public void onResume()
	{
		super.onResume();
		
        // Extract the parameters from the Bundle passed in:
        Bundle extras = getIntent().getExtras();
        if (extras==null)
        {
            Util.log("Null Bundle passed into WidgetOptions.onCreate().");
            finish();
            return;
        }
        if (!extras.containsKey("top_level"))
        {
            Util.log("No top_level passed into WidgetOptions.onCreate().");
            finish();
            return;
        }
        if (!extras.containsKey("view_name"))
        {
            Util.log("No view_name passed into WidgetOptions.onCreate().");
            finish();
            return;
        }
        _topLevel = extras.getString("top_level");
        _viewName = extras.getString("view_name");
        
        // Get the data on the view to display:
        Cursor viewCursor = (new ViewsDbAdapter()).getView(_topLevel,_viewName);
        if (!viewCursor.moveToFirst())
        {
            Util.popup(this, "Internal Error: View is not defined.");
            Util.log("View is not defined in TaskList.onCreate().");
            finish();
            return;
        }
        _viewID = Util.cLong(viewCursor, "_id");
        DisplayOptions displayOptions = new DisplayOptions(Util.cString(viewCursor, 
        	"display_string"));
        viewCursor.close();
        
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_settings));
        
        // Set the title at the top of the screen:
        if (displayOptions.widgetTitle.length()>0)
        {
        	getSupportActionBar().setTitle(Util.getString(R.string.Options_for_Widget)+" \""+
        		displayOptions.widgetTitle+"\"");
        }
        else
        	getSupportActionBar().setTitle(R.string.Options_for_Widget);

        initBannerAd();
	}
	
	/** Called when a new Intent is received for a different widget: */
	@Override
	public void onNewIntent(Intent intent)
	{
		setIntent(intent);
	}
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
        Util.log("Show Widget Options");
        
        // Link this activity to a layout resource:
        setContentView(R.layout.widget_options);
        
        //
        // Button Handlers:
        //
        
        // Filter:
        findViewById(R.id.widget_options_filter_text).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(WidgetOptions.this,ViewRulesList.class);
	        	i.putExtra("view_id", _viewID);
	        	startActivityForResult(i,ACTIVITY_FILTER);
			}
		});

        // Sort:
        findViewById(R.id.widget_options_sort_text).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(WidgetOptions.this,SortOrder.class);
	        	i.putExtra("view_id", _viewID);
	        	startActivityForResult(i,ACTIVITY_SORT);
			}
		});
        
        // Display:
        findViewById(R.id.widget_options_display_text).setOnClickListener(new 
        	View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				Intent i = new Intent(WidgetOptions.this,WidgetDisplayOptions.class);
	        	i.putExtra("view_id", _viewID);
	        	startActivityForResult(i,ACTIVITY_DISPLAY);
			}
		});
        
        // Copy settings:
        findViewById(R.id.widget_options_copy).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				// Show the view picker dialog:
				ViewPicker viewPicker = new ViewPicker(WidgetOptions.this);
				viewPicker.chooseView(getString(R.string.Copy_from_Another_View_Info),
					new ViewPicker.Callback()
				{
					@Override
					public void onViewSelected(long sourceViewID, String viewName)
					{
						// Perform the copying:
						ViewsDbAdapter vdb = new ViewsDbAdapter();
						ViewRulesDbAdapter rdb = new ViewRulesDbAdapter();
						vdb.copySort(sourceViewID, _viewID);
						rdb.copyRules(sourceViewID, _viewID);
						Util.popup(WidgetOptions.this, R.string.Rules_Copied_Into_Widget);
						Util.updateWidgets();
					}

					@Override
					public void onCancel()
					{
						// Nothing to do.
					}
				});
			}
		});
    }
    
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.done, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/** Handlers for the action bar buttons: */
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem)
	{
		switch (menuItem.getItemId())
		{
		case R.id.menu_done:
			finish();
			return true;
		}
		return super.onOptionsItemSelected(menuItem);
	}
	
	// Handlers for activity results.  In general, all we need to do is update the widget.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        
        switch(requestCode)
        {
        case ACTIVITY_FILTER:
        	Util.updateWidgets();
        	break;
        	
        case ACTIVITY_SORT:
        	Util.updateWidgets();
        	break;
        	
        case ACTIVITY_DISPLAY:
        	Util.updateWidgets();
        	break;
        }
    }   
    
    // A class to hold information on the available views we can copy from:
    private class ViewInfo
    {
    	public String name;
    	public long id;
    	
    	public ViewInfo(long newID, String newName)
    	{
    		name=newName;
    		id=newID;
    	}
    }
}
