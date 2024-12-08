package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import androidx.core.view.MenuItemCompat;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/**<pre>This activity edits a list of rules for a particular view.
To call this activity, put a Bundle in the Intent with the following keys/values:
view_id: The ID of the view, from the database.
start_new_task_list: if true, start up a new task list rather and calling finish()
    to return to the previous activity.  This may be omitted to not start a new
    task list.
The response sent back to the caller includes the following:
resultCode: either RESULT_CANCELED or RESULT_OK 
 * </pre>*/
public class ViewRulesList extends UtlSaveCancelPopupActivity
{
	// IDs for options menu items:
	private static final int RESTORE_DEFAULTS_ID = Menu.FIRST+4;
	
	// IDs for activities that send us results back:
	private static final int ACTIVITY_EDIT_TEXT_RULE = 1;
	private static final int ACTIVITY_EDIT_BOOLEAN_RULE = 2;
	private static final int ACTIVITY_EDIT_MULT_CHOICE_RULE = 3;
	private static final int ACTIVITY_EDIT_MULT_STRINGS_RULE = 4;
	private static final int ACTIVITY_EDIT_DATE_RULE = 5;
	private static final int ACTIVITY_EDIT_DUE_DATE_RULE = 6;
	private static final int ACTIVITY_EDIT_INT_RULE = 7;
	private static final int ACTIVITY_EDIT_LOCATION_RULE = 8;
	
	// Values for the operation field (passed to editor Activity instances):
	public static int ADD = 1;
	public static int EDIT = 2;
	
	// Database variables:
    private long _viewID;
    private ViewRulesDbAdapter _viewRulesDB;
    private ViewsDbAdapter _viewsDB;
    
    // The top level and view name we are displaying:
    private String _topLevel;
    private String _viewName;  // Will be "" if not used.
	
    // This holds a list of fields we can filter on:
    private ArrayList<String> dialogList;
    
    // This ArrayList of HashMap objects holds the rules while the user works on them:
    private static ArrayList<HashMap<String,ViewRuleHolder>> _ruleList;
    
    // Keeps track of the last View ID that was edited here, along with the time this
    // class was last instantiated.
    private static long _lastViewID = 0;
    private static long _lastTimeCalled = 0;
    
    private SimpleAdapter adapter;
    
    private boolean _startNewTaskList;
    
    // This holds the "add rule" button:
    private ViewGroup _footerRow;
    
    private int _tempIndex;
    
    private SharedPreferences _settings;
    
    private boolean _isSearch;
    
    // Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Util.log("Launched ViewRulesList");

        setContentView(R.layout.rules_list);
        
        _settings = this.getSharedPreferences(Util.PREFS_NAME, 0);
        initBannerAd();
        
        // Extract the parameters from the Bundle passed in:
        Bundle extras = getIntent().getExtras();
        if (extras==null)
        {
            Util.log("Null Bundle passed into ViewRulesList.onCreate().");
            finish();
            return;
        }
        if (!extras.containsKey("view_id"))
        {
        	Util.log("Missing view_id in ViewRulesList.onCreate().");
        }
        _viewID = extras.getLong("view_id");
        
        // See if we need to start a new task list when done.
        _startNewTaskList = false;
        if (extras.containsKey("start_new_task_list"))
        {
        	if (extras.getBoolean("start_new_task_list"))
        	{
        		_startNewTaskList = true;
        	}
        }
        // Pull out the top level and view name from the DB.  We need this to set the title.
        _viewsDB = new ViewsDbAdapter();
        Cursor c = _viewsDB.getView(_viewID);
        if (!c.moveToFirst())
        {
        	c.close();
        	Util.log("Bad view ID ("+_viewID+") passed to ViewRulesList.onCreate().");
        	finish();
        	return;
        }
        String topLevel = Util.cString(c,"top_level");
        String viewName = Util.cString(c,"view_name");
        _topLevel = topLevel;
        _viewName = viewName;
        DisplayOptions displayOptions = new DisplayOptions(Util.cString(c,"display_string"));
        c.close();
        
        // If a folder, context, or goal ID is used, we need to convert the ID to a name.
        // The name must appear in the title of this screen, not the ID.
        if (topLevel.equals(ViewNames.FOLDERS))
        {
			if (viewName.equals("0"))
			{
				// Showing tasks with no folder.
				viewName = getString(R.string.No_Folder);
			}
			else
			{
				c = (new FoldersDbAdapter()).getFolder(Long.parseLong(viewName));
				if (!c.moveToFirst())
				{
					c.close();
					Util.log("Bad folder ID (" + viewName + ") passed to ViewRulesList.onCreate().");
					finish();
					return;
				}
				viewName = Util.cString(c, "title");
				c.close();
			}
        }
        if (topLevel.equals(ViewNames.CONTEXTS))
        {
			if (viewName.equals("0"))
			{
				// Showing tasks with no context.
				viewName = getString(R.string.No_Context);
			}
			else
			{
				c = (new ContextsDbAdapter()).getContext(Long.parseLong(viewName));
				if (!c.moveToFirst())
				{
					c.close();
					Util.log("Bad context ID (" + viewName + ") passed to ViewRulesList.onCreate().");
					finish();
					return;
				}
				viewName = Util.cString(c, "title");
				c.close();
			}
        }
        if (topLevel.equals(ViewNames.GOALS))
        {
			if (viewName.equals("0"))
			{
				// Showing tasks with no goal.
				viewName = getString(R.string.No_Goal);
			}
			else
			{
				c = (new GoalsDbAdapter()).getGoal(Long.parseLong(viewName));
				if (!c.moveToFirst())
				{
					c.close();
					Util.log("Bad goal ID (" + viewName + ") passed to ViewRulesList.onCreate().");
					finish();
					return;
				}
				viewName = Util.cString(c, "title");
				c.close();
			}
        }
        if (topLevel.equals(ViewNames.LOCATIONS))
        {
        	UTLLocation loc = (new LocationsDbAdapter()).getLocation(Long.parseLong(viewName));
        	if (loc==null)
        	{
        		if (0==Long.parseLong(viewName))
        		{
        			viewName = Util.getString(R.string.No_Location);
        		}
        		else
        		{
        			Util.log("Bad location ID ("+viewName+") passed to ViewRulesList.onCreate().");
        			finish();
        			return;
        		}
        	}
        	else
        		viewName = loc.title;
        }
        if (topLevel.equals(ViewNames.BY_STATUS))
        {
        	// Convert the status value to a string:
        	String[] statusNames = this.getResources().getStringArray(R.array.statuses);
        	viewName = statusNames[Integer.parseInt(viewName)];
        }
        
        // Convert the top level to a more readable string:
        if (topLevel.equals(ViewNames.ALL_TASKS)) topLevel = Util.getString(R.string.AllTasks);
        if (topLevel.equals(ViewNames.HOTLIST)) topLevel = Util.getString(R.string.Hotlist);
        if (topLevel.equals(ViewNames.MY_VIEWS)) topLevel = Util.getString(R.string.MyViews);
        if (topLevel.equals(ViewNames.TEMP)) topLevel = Util.getString(R.string.MyViews);
        if (topLevel.equals(ViewNames.DUE_TODAY_TOMORROW)) topLevel = Util.getString(R.string.DueTodayTomorrow);
        if (topLevel.equals(ViewNames.OVERDUE)) topLevel = Util.getString(R.string.Overdue);
        if (topLevel.equals(ViewNames.STARRED)) topLevel = Util.getString(R.string.Starred);
        if (topLevel.equals(ViewNames.BY_STATUS)) topLevel = Util.getString(R.string.Status);
        if (topLevel.equals(ViewNames.FOLDERS)) topLevel = Util.getString(R.string.Folders);
        if (topLevel.equals(ViewNames.CONTEXTS)) topLevel = Util.getString(R.string.Contexts);
        if (topLevel.equals(ViewNames.TAGS)) topLevel = Util.getString(R.string.Tags);
        if (topLevel.equals(ViewNames.GOALS)) topLevel = Util.getString(R.string.Goals);
        if (topLevel.equals(ViewNames.LOCATIONS)) topLevel = Util.getString(R.string.Locations);
        if (topLevel.equals(ViewNames.RECENTLY_COMPLETED)) topLevel = Util.getString(R.string.RecentlyCompleted);
        if (topLevel.equals(ViewNames.SUBTASK)) 
        {
        	topLevel=Util.getString(R.string.Subtask_List);
        	viewName = "";
        }
        
        // Set the icon at the top:
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_filter));
        
        // Display the title for this screen:
        _isSearch = false;
        if (topLevel.equals(ViewNames.SEARCH))
        {
        	getSupportActionBar().setTitle(Util.getString(R.string.Filter_Rules_for_Search));
        	_isSearch = true;
        }
        else if (topLevel.equals(ViewNames.WIDGET))
        {
        	if (displayOptions.widgetTitle.length()>0)
        	{
        		getSupportActionBar().setTitle(Util.getString(R.string.Filter_Rules_For_Widget)+" \""+
        			displayOptions.widgetTitle+"\"");
        	}
        	else
        	{
        		getSupportActionBar().setTitle(R.string.Filter_Rules_For_Widget);
        	}
        }
        else if (viewName.length()==0)
        {
        	getSupportActionBar().setTitle(Util.getString(R.string.Filter_Rules_for_View)+" \""+
        		topLevel+"\"");
        }
        else
        {
        	getSupportActionBar().setTitle(Util.getString(R.string.Filter_Rules_for_View)+" \""+
        		topLevel+" / "+viewName+"\"");
        }
                
        // Read the database and populate the ArrayList of HashMap objects required by
        // the adapter:
        _viewRulesDB = new ViewRulesDbAdapter();
        if (_ruleList==null || _viewID!=_lastViewID || savedInstanceState==null)
        {
        	_ruleList = new ArrayList<HashMap<String,ViewRuleHolder>>();
        	_lastViewID = _viewID;
	        c = _viewRulesDB.getAllRules(_viewID);
	        while (c.moveToNext())
	        {
	        	HashMap<String,ViewRuleHolder> map = new HashMap<String,ViewRuleHolder>();
	        	ViewRuleHolder vrh = new ViewRuleHolder(c);
	        	map.put("rule", vrh);
	        	_ruleList.add(map);
	        }
	        c.close();
        }
        _lastTimeCalled = System.currentTimeMillis();
        
        // Create a footer view for the "add rule" button:
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(
        	Context.LAYOUT_INFLATER_SERVICE);
        _footerRow = (ViewGroup)inflater.inflate(R.layout.view_rules_list_add_rule, null);
        
        // Add the footer to the end of the list:
        ViewGroup vg = (ViewGroup)this.findViewById(R.id.rules_list_container);
        ListView listView = (ListView)vg.getChildAt(0);
        listView.addFooterView(_footerRow);
        
        // Handler for the "add rule" button:
        View.OnClickListener addRuleListener = new View.OnClickListener()
		{			
			@Override
			public void onClick(View v)
			{
				// Create a list of the possible fields we can filter on:
				dialogList = new ArrayList<String>();
				dialogList.add(Util.getString(R.string.Account));
				if (_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Actual_Length));
				}
				if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Assignor));
				}
				dialogList.add(Util.getString(R.string.Completed));
				dialogList.add(Util.getString(R.string.CompletionDate));
				if (_settings.getBoolean(PrefNames.CONTACTS_ENABLED,true))
				{
					dialogList.add(Util.getString(R.string.Contact));
				}
				if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Context));
				}
				if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Due_Date));
				}
				if (_settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Expected_Length));
				}
				if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Folder));
				}
				if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Goal));
				}
				dialogList.add(Util.getString(R.string.Importance));
				if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Is_Shared));
				}
				if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Location));
				}
				dialogList.add(Util.getString(R.string.Mod_Date));
				dialogList.add(Util.getString(R.string.Note));
				if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Owner));
				}
				if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Priority));
				}
				if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Reminder));
				}
				if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Star));
				}
				if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Start_Date));
				}
				if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Status));
				}
				if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Tags));
				}
				if (_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
				{
					dialogList.add(Util.getString(R.string.Timer_Status));
				}
				dialogList.add(Util.getString(R.string.Title));
				
				
				
				
				
				// Create a Dialog with these choices:
				AlertDialog.Builder builder = new AlertDialog.Builder(ViewRulesList.this);
				String[] fieldStrings = Util.iteratorToStringArray(dialogList.iterator(),
					dialogList.size());
				builder.setItems(fieldStrings, new DialogInterface.OnClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						// Get the database field that was selected:
						String fieldDescription = dialogList.get(which);
						String fieldName = Util.descriptionToDbField.get(fieldDescription);
						dialog.dismiss();
						addRule(fieldName);
					}
				});
				builder.setTitle(Util.getString(R.string.Select_field_to_filter));
				builder.show();
			}
		};
        
		// Set the handler for the add rule button in the footer:
		_footerRow.findViewById(R.id.rules_list_add_rule_button).setOnClickListener(addRuleListener);
		
		// We also need to set the same handler for the add rule button that displays
		// if there are no rules:
		this.findViewById(R.id.rules_list_empty_add_rule_button).setOnClickListener(
			addRuleListener);
    }
    
    @Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
    	super.onPostCreate(savedInstanceState);
    	
    	// Display the data:
    	refreshData();
	}
    
    // Populate the options menu when it is invoked:
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	if (!_isSearch)
    	{
    		// Add menu items from the parent class first:
    		super.onCreateOptionsMenu(menu);
    	}
    	else
    	{
    		getMenuInflater().inflate(R.menu.search_cancel, menu);
    	}
        
        if (_topLevel.equals(ViewNames.MY_VIEWS) || _topLevel.equals(ViewNames.TEMP) ||
    		_topLevel.equals(ViewNames.SEARCH) || _topLevel.equals(ViewNames.SUBTASK))
    	{
        	// These views do not have an option to restore the default filters.
        	return true;
    	}
        MenuItemCompat.setShowAsAction(menu.add(0,RESTORE_DEFAULTS_ID,0,R.string.Restore_Defaults),
			MenuItemCompat.SHOW_AS_ACTION_NEVER);
        return true;
    }
    
    private void refreshData()
    {
        // Create the SimpleAdapter for this ListActivity.  This maps the one key in
    	// _ruleList ("rule") to a ViewGroup containing the description and buttons.
        adapter = new SimpleAdapter(this,_ruleList,R.layout.rules_list_row,
        	new String[] {"rule","rule","rule"}, new int[] 
        	{R.id.rules_list_row_container,R.id.rules_list_and_line,R.id.rules_list_or_line});
        
    	// Define the function that maps the data in _ruleList to the display on-screen:
        adapter.setViewBinder(new SimpleAdapter.ViewBinder()
		{			
			@Override
			public boolean setViewValue(View view, Object data, String textRepresentation)
			{
				ViewRuleHolder vrh = (ViewRuleHolder)data;
				if (view.getId()==R.id.rules_list_row_container)
				{
    				// Get references to the data and views we want to modify:
    				ViewGroup viewGroup = (ViewGroup)view;
    				TextView description = (TextView)viewGroup.findViewById(R.id.rules_list_description);
    				ImageButton lockedImage = (ImageButton)viewGroup.findViewById(R.id.rules_list_locked);
    				ImageButton deleteImage = (ImageButton)viewGroup.findViewById(R.id.rules_list_delete);
    				ImageButton editImage = (ImageButton)viewGroup.findViewById(R.id.rules_list_edit);
    				ImageButton upButton = (ImageButton)viewGroup.findViewById(R.id.rules_list_up);
    				ImageButton downButton = (ImageButton)viewGroup.findViewById(R.id.rules_list_down);
    				
    				
    				// Update the description, and hide any images that don't apply:
    				description.setText(vrh.viewRule.getReadableString());
    				if (vrh.lock_level==0)
    				{
    					lockedImage.setVisibility(View.GONE);
    					deleteImage.setVisibility(View.VISIBLE);
    					editImage.setVisibility(View.VISIBLE);
    					if (vrh.index==0)
    					{
    						upButton.setVisibility(View.GONE);
        					downButton.setVisibility(View.VISIBLE);
    					}
    					else if (vrh.index==(_ruleList.size()-1))
    					{
    						downButton.setVisibility(View.GONE);
        					upButton.setVisibility(View.VISIBLE);
    					}
    					else
    					{
    						downButton.setVisibility(View.VISIBLE);
        					upButton.setVisibility(View.VISIBLE);
    					}
    				}
    				else if (vrh.lock_level==1)
    				{
    					lockedImage.setVisibility(View.GONE);
    					deleteImage.setVisibility(View.GONE);
    					editImage.setVisibility(View.VISIBLE);
    					upButton.setVisibility(View.GONE);
    					downButton.setVisibility(View.GONE);
    				}
    				else
    				{
    					lockedImage.setVisibility(View.VISIBLE);
    					deleteImage.setVisibility(View.GONE);
    					editImage.setVisibility(View.GONE);
    					upButton.setVisibility(View.GONE);
    					downButton.setVisibility(View.GONE);
    				}
    				
    				// Add tags to the buttons, which store the index of the rule.  The click handlers
    				// need to know which rule was tapped on.
    				deleteImage.setTag(Integer.valueOf(vrh.index));
    				editImage.setTag(Integer.valueOf(vrh.index));
    				upButton.setTag(Integer.valueOf(vrh.index));
    				downButton.setTag(Integer.valueOf(vrh.index));
    				
    				// Define handlers for tapping on the edit or delete buttons:
    				deleteImage.setOnClickListener(new View.OnClickListener()
					{						
						@Override
						public void onClick(View v)
						{
							handleDeleteTap(v);
						}
					});
    				editImage.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							Integer i = (Integer)v.getTag();
							handleEditTap(i);
						}
					});
    				
    				// Define handlers for the up and down buttons:
    				upButton.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							Integer i = (Integer)v.getTag();
							handleUpButton(i);
						}
					});
    				downButton.setOnClickListener(new View.OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							Integer i = (Integer)v.getTag();
							handleDownButton(i);
						}
					});
				}
				else if (view.getId()==R.id.rules_list_and_line)
				{
					if (!vrh.is_or && vrh.index>0)
					{
						view.setVisibility(View.VISIBLE);						
					}
					else
					{
						view.setVisibility(View.GONE);
					}
				}
				else if (view.getId()==R.id.rules_list_or_line)
				{
					if (vrh.is_or && vrh.index>0)
					{
						view.setVisibility(View.VISIBLE);						
					}
					else
					{
						view.setVisibility(View.GONE);
					}
				}
				return true;
			}
		});
        
        this.setListAdapter(adapter);        
    }
    
    // Handlers for the delete button:
    private void handleDeleteTap(View v)
    {
    	Integer index = (Integer)v.getTag();
    	handleDeleteTap(index);
    }
    
    private void handleDeleteTap(int index)
    {
    	_tempIndex = index;
    	
        // Display a dialog asking if the user really wants to delete:
        DialogInterface.OnClickListener dialogClickListener = new 
        	DialogInterface.OnClickListener() 
        {           
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which)
                {
                case DialogInterface.BUTTON_POSITIVE:
                    // Yes clicked:
                    _ruleList.remove(_tempIndex);
                    for (int i=_tempIndex; i<_ruleList.size(); i++)
                    {
                    	ViewRuleHolder vrh = _ruleList.get(i).get("rule");
                    	vrh.index--;
                    	vrh.position--;
                    }
                    refreshData();
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    // No clicked:
                    break;
                }                    
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(Util.getString(R.string.Are_you_sure_you_want_to_delete_rule));
        builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
        builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
        HashMap<String,ViewRuleHolder> temp = _ruleList.get(index);
        ViewRuleHolder vrh = temp.get("rule");
        builder.setTitle(Util.dbFieldToDescription.get(vrh.field_name));
        builder.show();
    }
    
    // Handler for the edit button:
    private void handleEditTap(int index)
    {
    	// Get the index of the item being edited, and the actual rule:
    	ViewRuleHolder vrh = _ruleList.get(index).get("rule");
    	
    	// Define the fields that must be passed in the Intent's Bundle:
    	Bundle b = new Bundle();
    	b.putInt("operation",EDIT);
    	b.putBoolean("is_or", vrh.is_or);
    	b.putString("field", vrh.field_name);
    	b.putString("db_string", vrh.viewRule.getDatabaseString());
    	b.putInt("index",vrh.index);
    	b.putInt("lock_level", vrh.lock_level);
    	Intent i;
    	if (vrh.field_name.equals("title") || vrh.field_name.equals("note"))
    	{
    		i = new Intent(this,EditTextViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_TEXT_RULE);
    	}
    	if (vrh.field_name.equals("completed") || vrh.field_name.equals("star") || 
    		vrh.field_name.equals("is_joint"))
    	{
    		i = new Intent(this,EditBooleanViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_BOOLEAN_RULE);
    	}
    	if (vrh.field_name.equals("length") || vrh.field_name.equals("timer") ||
    		vrh.field_name.equals("timer_start_time") || vrh.field_name.equals("importance"))
    	{
    		i = new Intent(this,EditIntViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_INT_RULE);
    	}
    	if (vrh.field_name.equals("account_id") || vrh.field_name.equals("folder_id") ||
    		vrh.field_name.equals("context_id") || vrh.field_name.equals("goal_id") ||
    		vrh.field_name.equals("status") || vrh.field_name.equals("priority"))
    	{
    		i = new Intent(this,EditMultChoiceViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_MULT_CHOICE_RULE);
    	}
    	if (vrh.field_name.equals("location_id"))
    	{
    		i = new Intent(this,EditLocationViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_LOCATION_RULE);
    	}
    	if (vrh.field_name.equals("tags.name") || vrh.field_name.equals("contact_lookup_key") ||
    		vrh.field_name.equals("owner_remote_id") || vrh.field_name.equals("added_by"))
    	{
    		i = new Intent(this,EditMultStringsViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_MULT_STRINGS_RULE);
    	}
    	if (vrh.field_name.equals("start_date") || vrh.field_name.equals("completion_date") ||
    		vrh.field_name.equals("mod_date") || vrh.field_name.equals("reminder"))
    	{
    		i = new Intent(this,EditDateViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_DATE_RULE);
    	}
    	if (vrh.field_name.equals("due_date"))
    	{
    		i = new Intent(this,EditDueDateViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_DUE_DATE_RULE);
    	}
    }
    
    // Handler for an "up" button
    private void handleUpButton(int index)
    {
    	// Get the ViewRuleHolder we have selected:
    	HashMap<String,ViewRuleHolder> temp = _ruleList.get(index);
    	ViewRuleHolder vrh = temp.get("rule");
    	
		if (index>0)
		{
			HashMap<String,ViewRuleHolder> mover = _ruleList.get(index);
			HashMap<String,ViewRuleHolder> prev = _ruleList.get(index-1);
			_ruleList.set(index-1,mover);
			_ruleList.set(index,prev);
			
			vrh = mover.get("rule");
			vrh.index--;
			vrh.position--;
			vrh = prev.get("rule");
			vrh.index++;
			vrh.position--;
			refreshData();
		}
    }
        
    // Handler for a "down" button:
    private void handleDownButton(int index)
    {
    	// Get the ViewRuleHolder we have selected:
    	HashMap<String,ViewRuleHolder> temp = _ruleList.get(index);
    	ViewRuleHolder vrh = temp.get("rule");
    	
    	if (index<(_ruleList.size()-1))
  		{
  			for (int i=0; i<_ruleList.size(); i++)
  			{
  				vrh = _ruleList.get(i).get("rule");
  			}

  			HashMap<String,ViewRuleHolder> mover = _ruleList.get(index);
  			HashMap<String,ViewRuleHolder> next = _ruleList.get(index+1);
  			_ruleList.set(index+1,mover);
  			_ruleList.set(index,next);
  			
  			vrh = mover.get("rule");
  			vrh.index++;
  			vrh.position++;
  			vrh = next.get("rule");
  			vrh.index--;
  			vrh.position--;
  			refreshData();
  		}
    }
    
    // Launch the appropriate editor screen to add a new rule:
    private void addRule(String fieldName)
    {
    	// Define the fields that must be passed in the Intent's Bundle:
    	Bundle b = new Bundle();
    	b.putInt("operation",ADD);
    	b.putString("field", fieldName);
    	Intent i;
    	if (fieldName.equals("title") || fieldName.equals("note"))
    	{
    		i = new Intent(this,EditTextViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_TEXT_RULE);
    	}
    	if (fieldName.equals("completed") || fieldName.equals("star") || fieldName.equals("is_joint"))
    	{
    		i = new Intent(this,EditBooleanViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_BOOLEAN_RULE);
    	}
    	if (fieldName.equals("length") || fieldName.equals("timer") ||
    		fieldName.equals("timer_start_time") || fieldName.equals("importance"))
    	{
    		i = new Intent(this,EditIntViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_INT_RULE);
    	}
    	if (fieldName.equals("account_id") || fieldName.equals("folder_id") ||
    		fieldName.equals("context_id") || fieldName.equals("goal_id") ||
    		fieldName.equals("status") || fieldName.equals("priority"))
    	{
    		i = new Intent(this,EditMultChoiceViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_MULT_CHOICE_RULE);
    	}
    	if (fieldName.equals("location_id"))
    	{
    		i = new Intent(this,EditLocationViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_LOCATION_RULE);
    	}
    	if (fieldName.equals("tags.name") || fieldName.equals("contact_lookup_key") ||
    		fieldName.equals("owner_remote_id") || fieldName.equals("added_by"))
    	{
    		i = new Intent(this,EditMultStringsViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_MULT_STRINGS_RULE);
    	}
    	if (fieldName.equals("start_date") || fieldName.equals("completion_date") ||
    		fieldName.equals("mod_date") || fieldName.equals("reminder"))
    	{
    		i = new Intent(this,EditDateViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_DATE_RULE);
    	}
    	if (fieldName.equals("due_date"))
    	{
    		i = new Intent(this,EditDueDateViewRule.class);
    		i.putExtras(b);
    		this.startActivityForResult(i, ACTIVITY_EDIT_DUE_DATE_RULE);
    	}
    }
    
    // Handlers for an options menu choice:
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) 
    {
        switch(menuItem.getItemId()) 
        {
          case RESTORE_DEFAULTS_ID:
        	// If this is not a built-in rule, then we have no defaults.
        	if (_topLevel.equals(ViewNames.MY_VIEWS) || _topLevel.equals(ViewNames.TEMP) ||
        		_topLevel.equals(ViewNames.SEARCH) || _topLevel.equals(ViewNames.SUBTASK))
        	{
        		Util.popup(this, R.string.Cannot_restore_defaults);
        		return true;
        	}
        	
        	// Confirm that the user wants to do this:
            DialogInterface.OnClickListener dialogClickListener = new 
            	DialogInterface.OnClickListener() 
            {           
                @Override
                public void onClick(DialogInterface dialog, int which) 
                {
                    switch (which)
                    {
                      case DialogInterface.BUTTON_POSITIVE:
                        // Yes clicked:
                    	// Get a list of default rules for this view:
                    	ArrayList<ViewRule> rules = new ArrayList<ViewRule>();
                        ArrayList<Integer> lockList = new ArrayList<Integer>();
                        ArrayList<Boolean> isOrList = new ArrayList<Boolean>();            
                        if (!_viewsDB.generateDefaultViewRules(_topLevel,_viewName,rules,lockList,isOrList))
                        {
                        	// The generateDefaultViewRules function logs the error.
                        	break;
                        }
                        
                        // Put the rules into the local array:
                        _ruleList.clear();
                        for (int i=0; i<rules.size(); i++)
                        {
                        	ViewRuleHolder vrh = new ViewRuleHolder();
                        	vrh.view_id = _viewID;
                        	vrh.lock_level = lockList.get(i);
                        	vrh.field_name = rules.get(i).getDbField();
                        	vrh.viewRule = rules.get(i);
                        	vrh.position = i;
                        	vrh.index = i;
                        	vrh.is_or = isOrList.get(i);
                        	HashMap<String,ViewRuleHolder> temp = new HashMap<String,
                        		ViewRuleHolder>();
                        	temp.put("rule", vrh);
                        	_ruleList.add(temp);
                        }
                        
                        // Refresh the display:
                        refreshData();
                        break;
                      case DialogInterface.BUTTON_NEGATIVE:
                        // No clicked.  Nothing happens.
                        break;
                    }                    
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(Util.getString(R.string.Restore_default_rules));
            builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
            builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
            builder.show();
        }
        
        return super.onOptionsItemSelected(menuItem);
    }

    // Handlers for activity results:
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
    	if (resultCode==RESULT_CANCELED)
    	{
    		// If the activity was canceled, there is nothing to do.
    		return;
    	}
    	
    	// At this point, we can assume the user hit the "save" button, and changes need
    	// to be displayed.  Pull out the data in the Bundle.  All rule editors return
    	// the same fields.
    	Bundle b = intent.getExtras();
    	if (!b.containsKey("operation") || !b.containsKey("field") || !b.containsKey(
    		"db_string"))
    	{
    		Util.log("Missing key in response Bundle, in ViewRulesList.java.");
    		return;
    	}
    	
    	// Begin construction of a ViewRuleHolder object, which holds the rule:
    	ViewRuleHolder vrh = new ViewRuleHolder();
    	vrh.view_id = this._viewID;
    	vrh.field_name = b.getString("field");    	
    	int operation = b.getInt("operation");
    	if (operation==EDIT)
    	{
    		// Get position, lock level, and is_or.  The is_or field can only be edited if
    		// the lock_level is 0.
    		vrh.index = b.getInt("index");
    		ViewRuleHolder original = _ruleList.get(vrh.index).get("rule");
    		vrh.lock_level = original.lock_level;
    		vrh.position = original.position;
    		if (original.lock_level>0)
    		{
    			// Rule is locked.  is_or does not change.
    			vrh.is_or = original.is_or;    			
    		}
    		else
    		{
    			// Rule is not locked.  User can set is_or:
    			vrh.is_or = b.getBoolean("is_or");
    		}
    		
    		// Replace the old ViewRuleHolder with the new one:
    		HashMap<String,ViewRuleHolder> temp = new HashMap<String,ViewRuleHolder>();
        	temp.put("rule", vrh);
        	_ruleList.set(vrh.index, temp);
    	}
    	else
    	{
    		vrh.is_or = b.getBoolean("is_or");
    		vrh.position = _ruleList.size();
    		vrh.index = _ruleList.size();
    		vrh.lock_level = 0;

    		// Since it's an add operation, add the ViewRuleHolder to the end of the array:
        	HashMap<String,ViewRuleHolder> temp = new HashMap<String,ViewRuleHolder>();
        	temp.put("rule", vrh);
        	_ruleList.add(temp);        	
    	}
    	    	
    	// Based on the rule type returned, finish creating the ViewRuleHolder:
    	String dbString = b.getString("db_string");
    	switch (requestCode)
    	{
    		case ACTIVITY_EDIT_TEXT_RULE:
    			vrh.viewRule = new TextViewRule(vrh.field_name,dbString);
    			break;
    		
    		case ACTIVITY_EDIT_BOOLEAN_RULE:
    			vrh.viewRule = new BooleanViewRule(vrh.field_name,dbString);
    			break;
    			
    		case ACTIVITY_EDIT_DATE_RULE:
    			vrh.viewRule = new DateViewRule(vrh.field_name,dbString);
    			break;
    			
    		case ACTIVITY_EDIT_DUE_DATE_RULE:
    			vrh.viewRule = new DueDateViewRule(dbString);
    			break;
    			
    		case ACTIVITY_EDIT_INT_RULE:
    			vrh.viewRule = new IntViewRule(vrh.field_name,dbString);
    			break;
    		
    		case ACTIVITY_EDIT_MULT_CHOICE_RULE:
    			vrh.viewRule = new MultChoiceViewRule(vrh.field_name,dbString);
    			break;
    		
    		case ACTIVITY_EDIT_LOCATION_RULE:
    			vrh.viewRule = new LocationViewRule(vrh.field_name,dbString);
    			break;
    		
    		case ACTIVITY_EDIT_MULT_STRINGS_RULE:
    			vrh.viewRule = new MultStringsViewRule(vrh.field_name,dbString);
    			break;
    	}
    	
    	// Update the display:
    	refreshData();
    }
    
    // Handler for the "back" hardware button:
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_BACK)
        {
        	// IF we're working with a temporary view, we need to delete it:
			if (_topLevel.equals("temp"))
			{
				_viewsDB.deleteView(_viewID);
			}
			
			_lastViewID = 0;
			setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }    
    
    // Handler for pausing the activity (when we leave):
    @Override
    public void onPause()
    {
        super.onPause();
        _lastTimeCalled = System.currentTimeMillis();
    }

    @Override
    public void handleCancel()
    {
		// IF we're working with a temporary view, we need to delete it:
		if (_topLevel.equals("temp"))
		{
			_viewsDB.deleteView(_viewID);
		}
		
		_lastViewID = 0;
		setResult(RESULT_CANCELED);
        finish();    	
    }
    
	@Override
	public void handleSave()
	{
		// Make sure at least one rule is given:
		if (_ruleList.size()==0)
		{
			Util.popup(ViewRulesList.this,R.string.At_least_one_rule);
			return;
		}
		
		// Clear all rules from the View:
		_viewRulesDB.clearAllRules(_viewID);
		
		// IF this is a temporary view (created for an add operation) then
		// move it to the my views area:
		if (_topLevel.equals("temp"))
		{
			_viewsDB.moveTemporaryToMyViews(_viewID);
		}
		
		// Go through the list items, and create new rules:
		Iterator<HashMap<String,ViewRuleHolder>> it = _ruleList.iterator();
		int count=0;
        FeatureUsage featureUsage = new FeatureUsage(this);
		while (it.hasNext())
		{
			HashMap<String,ViewRuleHolder> hash = it.next();
			ViewRuleHolder vrh = hash.get("rule");
			long rowID = _viewRulesDB.addRule(_viewID, vrh.lock_level, vrh.viewRule,
				count, vrh.is_or);
			if (rowID<0)
			{
				Util.log("Cannot insert ViewRule in ViewRulesList.java.");
			}
			else
			{
				count++;

                // Record usage of Toodledo's importance field if needed.
                if (vrh.field_name.equals("importance"))
                    featureUsage.record(FeatureUsage.TOODLEDO_IMPORTANCE);
			}
		}
		
		if (_startNewTaskList)
		{
			// Currently this is only set for searches.
			Intent i = new Intent(ViewRulesList.this,TaskList.class);
			i.putExtra("top_level", _topLevel);
			i.putExtra("view_name",_viewName);
			i.putExtra("title", Util.getString(R.string.Search_Results));
			startActivity(i);
		}
		else
		{
			setResult(RESULT_OK);
			finish();
		}
	}

}
