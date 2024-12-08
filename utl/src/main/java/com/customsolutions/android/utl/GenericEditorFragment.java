package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

/** Base class that can be used for many editor fragments (folder, context, etc.).  Pass in a Bundle
 * with key "mode and a value of ADD or EDIT.  When editing, pass in a key of "id", containing the
 * database ID of the item being edited.<br><br>
 * The layout must include a FrameLayout with id save_cancel_bar_placeholder, for the Save/Cancel bar.
 * 
 * @author Nicholson
 *
 */
public class GenericEditorFragment extends UtlFragment
{
	// Codes to specify whether we are adding or editing:
    public static final int ADD = 1;
    public static final int EDIT = 2;

    // The operation we're performing (ADD or EDIT)
    protected int _op;
    
    // The database ID, if applicable:
    protected long _id;
   
    // Quick reference to key items:
    protected UtlActivity _a;
    protected Resources _res;
    protected int _ssMode;
    protected ViewGroup _rootView;
    protected SharedPreferences _settings;

    // Records if we're the only fragment in the activity.
    protected boolean _isOnlyFragment;
    
    // The save/cancel bar (if it's in use):
    protected SaveCancelTopBar _saveCancelBar;
    
    /** Called when Activity is started.  This processes the Intent and/or fragment arguments and
     * sets the values of key protected variables. */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        super.onActivityCreated(savedInstanceState);
        
        _a = (UtlActivity) getActivity();
        _res = _a.getResources();
        _settings = _a._settings;
        _ssMode = _a.getSplitScreenOption();
        _rootView = (ViewGroup)getView();
        
        // Get the mode and existing database ID if applicable.  Look for Fragment arguments first.
        _op = ADD;
        _isOnlyFragment = true;
        _saveCancelBar = null;
        Bundle fragArgs = getArguments();
        if (fragArgs!=null && fragArgs.containsKey("mode"))
        {
        	// Since fragment arguments were passed in, we know that this was called with other
        	// fragments on the screen (otherwise, this would be a new Activity and have Intent args).
        	_isOnlyFragment = false;
        	
        	// We will use a separate save/cancel bar:
        	_saveCancelBar = new SaveCancelTopBar(_a,_rootView);
        	_saveCancelBar.setSaveHandler(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					// Do nothing if resize mode is on:
					if (inResizeMode()) return;
					
					handleSave();
				}
			});
        	_saveCancelBar.setCancelHandler(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					// Do nothing if resize mode is on:
					if (inResizeMode()) return;
					
					handleCancel();
				}
			});
        	
        	// Get the mode:
        	_op = fragArgs.getInt("mode");
        	if (_op==EDIT)
        	{
        		// We need an ID as well:
        		if (!fragArgs.containsKey("id"))
        		{
        			Util.log("No ID passed in when editing.");
        			handleCancel();
        			return;
        		}
        		_id = fragArgs.getLong("id");
        	}
        }
        else
        {
        	// The mode must be in the Intent
            Intent i = _a.getIntent();
            Bundle extras = i.getExtras();
            if (extras!=null && extras.containsKey("mode"))
            	_op = extras.getInt("mode");
            if (_op==EDIT)
            {
            	// We need an ID as well:
            	if (!extras.containsKey("id"))
            	{
            		Util.log("No ID passed in when editing.");
        			handleCancel();
        			return;
            	}
            	_id = extras.getLong("id");
            }
        }
        
        if (_isOnlyFragment)
        {
        	// This fragment will be updating the Action Bar:
        	setHasOptionsMenu(true);

        	// Show an ad if applicable:
			initBannerAd(_rootView);
        }
    }
    
    /** Set the header title at the top of the screen or fragment: */
    protected void setTitle(int resID)
    {
    	if (_isOnlyFragment)
    	{
        	_a.getSupportActionBar().setTitle(resID);
        	_a.getSupportActionBar().setIcon(_a.resourceIdFromAttr(R.attr.ab_edit));
    	}
        else
        {
        	_saveCancelBar.setTitle(_a.getString(resID));
        }
    }

    // Populate the action bar buttons and menu:
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	if (!_isOnlyFragment)
    	{
    		// If there are other fragments on the screen, we do not populate the action bar.
    		return;
    	}
    	
    	menu.clear();
    	inflater.inflate(R.menu.save_cancel, menu);
    }

    /** Check to see if the parent activity is is resize mode.  In resize mode, we can't execute any 
		commands: */
	protected boolean inResizeMode()
	{
		if (_isOnlyFragment)
			return false;
		
		if (_a instanceof TaskList && _ssMode!=Util.SS_NONE)
		{
			UtlNavDrawerActivity n = (UtlNavDrawerActivity)_a;
			return n.inResizeMode();
		}
		else
			return false;
	}
	
    // Handlers for action bar items:
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	// Do nothing if resize mode is on:
    	if (inResizeMode())
    		return super.onOptionsItemSelected(item);
    	
    	switch (item.getItemId())
    	{
    	case R.id.menu_save:
    		handleSave();
    		return true;
    		
    	case R.id.menu_cancel:
    		handleCancel();
    		return true;
    	}
    	return super.onOptionsItemSelected(item);
    }
    	
    /** Briefly flash the field that was just pressed on by the user: */
    @SuppressLint("NewApi")
	protected void flashField(int viewID)
    {
    	/* TODO: Delete this function when no longer used.
    	View v = _rootView.findViewById(viewID);
    	Drawable bg = v.getBackground();
    	v.setBackgroundColor(_res.getColor(_a.resourceIdFromAttr(R.attr.list_highlight_bg_color_inv)));
    	FlashField ff = new FlashField();
    	if (Build.VERSION.SDK_INT >= 11)
    		ff.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,new Object[] {v,bg});
    	else
    		ff.execute(new Object[] {v,bg});

    	 */
    }
    
    /** Handle the "save" command.  Must be overwritten by subclass. */
	protected void handleSave()
	{
		
	}
	
	/** Handle a "cancel" command.  Can be overwritten if needed. */
	protected void handleCancel()
	{
		if (_isOnlyFragment)
    	{
    		// Just stop the parent activity:
    		_a.setResult(Activity.RESULT_CANCELED);
    		_a.finish();
    	}
    	else
    	{
    		UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
    		if (_ssMode==Util.SS_2_PANE_NAV_LIST)
    		{
    			// This is displayed in a sliding drawer, so close it:
    			nav.closeDrawer();
    		}
    		else
    		{
    			nav.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));
    		}
    	}
	}

 	/** Refresh the list view and nav drawer, and remove the fragment.  Call this after a successful
 	 * save. */
 	protected void refreshAndEnd()
 	{
		if (!_isOnlyFragment)
    	{
        	// Refresh the tag list and the navigation drawer:
    		GenericListActivity list = (GenericListActivity)_a;
    		list.refreshWholeNavDrawer();
    		list.refreshList();
    	}
    	
    	if (_isOnlyFragment)
    	{
    		// Just stop the parent activity:
    		_a.setResult(Activity.RESULT_OK);
    		_a.finish();
    	}
    	else
    	{
    		UtlNavDrawerActivity nav = (UtlNavDrawerActivity)_a;
    		if (_ssMode==Util.SS_2_PANE_NAV_LIST)
    		{
    			// This is displayed in a sliding drawer, so close it:
    			nav.closeDrawer();
    		}
    		else
    		{
    			nav.showDetailPaneMessage(_a.getString(R.string.Select_an_item_to_display));
    		}
    	}
 	}
}
