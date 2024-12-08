package com.customsolutions.android.utl;

// This gets the name of a custom view and then saves it to the My Views area.

// To call this, pass in a Bundle with the following keys/values:
// view_id: The ID of the view we are saving a copy of.

// This does not return anything to the caller.  If the view is created, then this
// calls the TaskList activity to display it.

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;

public class SaveView extends UtlSaveCancelPopupActivity
{
	private long _viewID;
	private ViewsDbAdapter _viewsDB;
	private Cursor _viewCursor;
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        Util.log("Begin saving a view.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.save_view);
        
        // Set the title at the top:
        getSupportActionBar().setTitle(R.string.Save_a_View);
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_save_view));
        
        // Extract the parameters from the Bundle passed in. Make sure required ones are
        // present.
        Bundle extras = getIntent().getExtras();
        if (extras==null)
        {
            Util.log("Null Bundle passed into SaveView.onCreate()");
            finish();
            return;
        }
        
        // Get the base view ID from the bundle:
        if (!extras.containsKey("view_id"))
        {
        	Util.log("No view_id passsed into SaveView.onCreate().");
        	finish();
        	return;
        }
        _viewID = extras.getLong("view_id");  
        _viewsDB = new ViewsDbAdapter();
        _viewCursor = _viewsDB.getView(_viewID);
        if (!_viewCursor.moveToFirst())
        {
        	Util.log("Cannot find view ID "+_viewID+" in database.");
        	finish();
        	return;
        }
        
        EditText editName = (EditText)findViewById(R.id.save_view_view_name);
        editName.setOnKeyListener(new View.OnKeyListener()
		{	
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				if (event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.
                    KEYCODE_ENTER)
                {
                    handleSave();
                    return true;
                }
				return false;
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
				size.y = _screenHeight*7/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				size.y = _screenHeight*6/10;
				return size;
			}
    	}
    	else
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth*9/10;
				size.y = _screenHeight*4/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*8/10;
				size.y = _screenHeight*3/10;
				return size;
			}
    	}
    	
    	return size;
    }

    // Save the result and move on:
	@Override
	public void handleSave()
	{
		// Verify that we have a view name:
		EditText name = (EditText)findViewById(R.id.save_view_view_name);
		if (name.getText().length()==0)
		{
			Util.popup(this, R.string.Please_enter_a_name);
			return;
		}
		
		// Create the new view in the database:
		long newViewID = _viewsDB.addView("my_views", name.getText().toString(), 
			Util.cString(_viewCursor,"sort_string"), new DisplayOptions(Util.cString(
			_viewCursor,"display_string")));
		if (newViewID==-1)
		{
			Util.popup(this, R.string.DbInsertFailed);
			return;
		}
		
		// Add in the rules for the view:
		ViewRulesDbAdapter viewRules = new ViewRulesDbAdapter();
		Cursor c = viewRules.getAllRules(_viewID);
		int lockLevel;
		while (c.moveToNext())
		{
			ViewRule viewRule = viewRules.getViewRule(c);
			lockLevel = 0;
			if (viewRule.dbField.equals("parent_id"))
			{
				lockLevel = 2;
			}
			long rowID = viewRules.addRule(newViewID, lockLevel, viewRule, Util.cInt(c, 
				"position"), Util.cInt(c, "is_or")==1 ? true : false);
			if (rowID==-1)
			{
				Util.popup(this, R.string.DbInsertFailed);
				return;
			}
		}
		
		// Launch the task viewer activity:
		Intent i = new Intent(this,TaskList.class);
		i.putExtra("top_level", "my_views");
		i.putExtra("view_name", name.getText().toString());
		i.putExtra("title", Util.getString(R.string.MyViews)+" / "+name.getText().
			toString());
		startActivity(i);
	}
}
