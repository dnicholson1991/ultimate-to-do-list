package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** This Activity initiates a quick search, or allows the user to start an advanced search.
 * The optional input is base_view_id - the ID of a view to get the sort and display settings from.
 */
public class QuickSearch extends UtlPopupActivity
{
	// Views we need to keep track of:
	private EditText _searchStr;
	private RadioGroup _whatToSearch;
	private RadioButton _allTasks;
	private RadioButton _incompleteTasks;
	private RadioButton _completeTasks;
	private RadioButton _notes;
	
	// View ID to use as a base.  -1 = none.
	private long _baseViewID;
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
    {
		super.onCreate(savedInstanceState);
        Util.log("Begin quick search.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.quick_search);
        
        // Set the title at the top:
        getSupportActionBar().setTitle(R.string.Quick_Search);
        
        // Fetch the view ID to use for the sort and display settings, if any.
        _baseViewID = -1;
        Bundle extras = getIntent().getExtras();
        if (extras!=null && extras.containsKey("base_view_id"))
        {
        	_baseViewID = extras.getLong("base_view_id");
        }
        
        // Link to views we're interested in:
        _searchStr = (EditText)findViewById(R.id.search_edit_text);
        _whatToSearch = (RadioGroup)findViewById(R.id.search_radio_group);
        _allTasks = (RadioButton)findViewById(R.id.search_radio_all_tasks);
        _incompleteTasks = (RadioButton)findViewById(R.id.search_radio_incomplete_tasks);
        _completeTasks = (RadioButton)findViewById(R.id.search_radio_complete_tasks);
        _notes = (RadioButton)findViewById(R.id.search_radio_notes);
        
        // Set the default radio selection:
        _whatToSearch.check(R.id.search_radio_all_tasks);
        
        // This function will hide the keyboard if the user taps on something that is not an EditText:
        setupAutoKeyboardHiding(findViewById(R.id.search_scrollview));

        initBannerAd();

        // Make the keyboard appear:
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        _searchStr.setOnFocusChangeListener(new View.OnFocusChangeListener() {
		    @Override
		    public void onFocusChange(View view, boolean focused)
		    {
		        if (focused)
		        {
		        	try
		        	{
		        		InputMethodManager imm = (InputMethodManager)getSystemService(
			        		Context.INPUT_METHOD_SERVICE);
		        		imm.showSoftInput(_searchStr, 0);
		        		
		        	}
		        	catch (IllegalArgumentException e)
		        	{
		        		// For reasons unknown, we get a "view not attached to window manager" failure
		        		// here on rare occasions.  Since popping up the keyboard is nonessential,
		        		// we can handle this exception by ignoring it.  This ensures the user
		        		// does not see an error message.
		        	}
		        }
		        else
		        {
		        	// When the title is defocused, hide the keyboard.
		        	InputMethodManager imm = (InputMethodManager)getSystemService(
		        		Context.INPUT_METHOD_SERVICE);
		        	imm.hideSoftInputFromWindow(_searchStr.getWindowToken(), 0);
		        }
		    }
		});
        _searchStr.setFocusableInTouchMode(true);
        _searchStr.requestFocus();
        
        // Pressing of the ENTER key:
        EditText editor = (EditText)this.findViewById(R.id.search_edit_text);
        editor.setOnKeyListener(new View.OnKeyListener() 
        {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if (event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.
                    KEYCODE_ENTER)
                {
                    runSearch();
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
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.y = _screenHeight*7/10;
				return size;
			}
    	}
    	else
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
				size.y = _screenHeight/2;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.y = _screenHeight/3;
				return size;
			}
    	}
    	
    	return size;
    }

    /** Place the search and advanced search buttons in the action bar: */
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.quick_search, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	/** Handlers for the action bar buttons: */
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem)
	{
		switch (menuItem.getItemId())
		{
		case R.id.menu_search:
			runSearch();
			return true;
			
		case R.id.menu_advanced:
			// Create a new temporary view with no rules and some default sort and 
			// display options:
			ViewsDbAdapter viewsDB = new ViewsDbAdapter();
			String viewName = Long.valueOf(System.currentTimeMillis()).toString();
			long newViewID = viewsDB.addView("search",viewName,viewsDB.
				getDefaultSortOrder("all_tasks"),
        		DisplayOptions.getDefaultDisplayOptions("all_tasks"));
			if (newViewID==-1)
        	{
        		Util.popup(QuickSearch.this, R.string.DbInsertFailed);
        		return true;
        	}
			
			// If a base view ID was passed in, then replace the default sort and display options with 
			// the ones in the base:
			if (_baseViewID>-1)
				viewsDB.copySortAndDisplay(_baseViewID, newViewID);
			
			// Call the activity to edit the view's rules:
        	Intent i = new Intent(QuickSearch.this,ViewRulesList.class);
        	i.putExtra("view_id", newViewID);
        	i.putExtra("start_new_task_list", true);
        	startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(menuItem);
	}
	
	@SuppressLint("InlinedApi")
	private void runSearch()
	{
		// Make sure a search string was actually typed:
		if (_searchStr.getText().toString().trim().length()==0)
		{
			Util.popup(QuickSearch.this, R.string.Must_enter_something_to_search_for);
			return;
		}
		String searchText = _searchStr.getText().toString().trim();
		
		if (_notes.isChecked())
		{
			// We're searching notes.  This is handled differently than tasks.
			String sql = "title like '%"+Util.makeSafeForDatabase(searchText)+"%' or note like '%"+
                Util.makeSafeForDatabase(searchText)+"%'";
			Intent i = new Intent(this,NoteList.class);
			i.putExtra("sql", sql);
			if (Build.VERSION.SDK_INT >= 11) 
			{
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			}
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
			return;
		}
		
		// Create a new temporary view with some default sort and display options:
		ViewsDbAdapter viewsDB = new ViewsDbAdapter();
		String viewName = Long.valueOf(System.currentTimeMillis()).toString();
		DisplayOptions displayOptions = DisplayOptions.getDefaultDisplayOptions("all_tasks");
		displayOptions.subtaskOption = "indented";
		displayOptions.parentOption = 0;
		long newViewID = viewsDB.addView("search",viewName,viewsDB.
			getDefaultSortOrder("all_tasks"),displayOptions);
		if (newViewID==-1)
    	{
    		Util.popup(QuickSearch.this, R.string.DbInsertFailed);
    		return;
    	}
		
		// If a base view ID was passed in, then replace the default sort and display options with 
		// the ones in the base:
		if (_baseViewID>-1)
			viewsDB.copySortAndDisplay(_baseViewID, newViewID);
		
		// Add in rules for title and note search:
		ViewRulesDbAdapter rulesDB = new ViewRulesDbAdapter();
		TextViewRule rule = new TextViewRule("title",_searchStr.getText().toString(),
			false);
		rulesDB.addRule(newViewID, 0, rule, 0, false);
		rule = new TextViewRule("note",_searchStr.getText().toString(),
			false);
		rulesDB.addRule(newViewID, 0, rule, 1, true);
		
		if (_incompleteTasks.isChecked())
		{
			// We're showing incomplete tasks only:
			BooleanViewRule completedRule = new BooleanViewRule("completed",false);
			rulesDB.addRule(newViewID, 0, completedRule, 2, false);
		}
		
		if (_completeTasks.isChecked())
		{
			// We're showing complete tasks only:
			BooleanViewRule completedRule = new BooleanViewRule("completed",true);
			rulesDB.addRule(newViewID, 0, completedRule, 2, false);
		}
		
		// Launch the task list activity to show the results:
		Intent i = new Intent(QuickSearch.this,TaskList.class);
		i.putExtra("top_level","search");
		i.putExtra("view_name", viewName);
		i.putExtra("title", Util.getString(R.string.Search_Results));
		if (Build.VERSION.SDK_INT >= 11) 
		{
			i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		}
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);		
	}
}
