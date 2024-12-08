package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/** This is the base class for implementations of RemoteViewsFactory for task list widgets.  This is 
 * responsible for providing the rows of the widget listviews. */
@SuppressLint("NewApi")
public class ScrollableRemoteViewsFactoryBase implements RemoteViewsService.RemoteViewsFactory
{
	protected Context _c;
	protected int _appWidgetId;
	protected long _viewID;
	protected DisplayOptions _displayOptions;
	protected boolean _colorCodeEnabled;
	protected boolean _extraFieldEnabled;
	protected long _timeZoneOffset;
	protected SharedPreferences _settings;
	protected RemoteViews _loadingRow;
	
	// Stores midnight on the current day:
	protected long _midnightToday;
    
	// This holds the task data:
	protected ArrayList<HashMap<String,UTLTaskDisplay>> _taskList;

	// This maps task IDs to UTLTaskDisplay objects within _taskList;
	protected HashMap<Long,UTLTaskDisplay> _taskHash;
	
    // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
    // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
	protected HashMap<Long,ArrayList<UTLTaskDisplay>> _subLists = new HashMap<Long,
        ArrayList<UTLTaskDisplay>>();

    // This keeps track of orphaned subtasks that should not be indented.
	protected HashSet<Long> orphanedSubtaskIDs;
    
	public ScrollableRemoteViewsFactoryBase(Context context, Intent intent)
	{
		_c = context;
		_appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        _settings = _c.getSharedPreferences(Util.PREFS_NAME,0);
        _loadingRow = new RemoteViews(_c.getPackageName(), R.layout.
        	scrollable_widget_row_loading);
        
        // Initialize the orphaned subtask list:
        if (orphanedSubtaskIDs == null)
        	orphanedSubtaskIDs = new HashSet<Long>();
        else
        	orphanedSubtaskIDs.clear();
        
        boolean hasSem = Util.acquireSemaphore("ScrollableRemoteViewsFactory", _c);
        Cursor viewCursor = null;
		try
		{
			// Get the data on the view to display:
	        viewCursor = (new ViewsDbAdapter()).getView("widget",Integer.valueOf(_appWidgetId).
	        	toString());
	        if (!viewCursor.moveToFirst())
	        {
	            Util.log("View is not defined in ScrollableRemoteViewsFactory().");
	            return;
	        }
	        _viewID = Util.cLong(viewCursor, "_id");
	        
	        // Initialize the Display Options:
	        _displayOptions = new DisplayOptions(Util.cString(viewCursor, "display_string"));
	        _colorCodeEnabled = true;
	        if (_displayOptions.widgetColorCodedField.length()==0) _colorCodeEnabled = false;
	        _extraFieldEnabled = true;
	        if (_displayOptions.widgetExtraField.length()==0) _extraFieldEnabled = false;
	        
	        // Get a cursor to the task data:
	        String query = Util.getTaskSqlQuery(_viewID, viewCursor, _c);
	        if (query==null || query.equals(""))
	        {
	        	Util.log("Query is blank in ScrollableRemoteViewsFactory().");
	        	return;
	        }
	        runQuery(query);
	        
	        // Get the offset in ms between the home time zone and the local one:
	    	TimeZone sysTimeZone = TimeZone.getDefault();
	    	TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
	    		"home_time_zone", "America/Los_Angeles"));
	    	_timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) - 
				appTimeZone.getOffset(System.currentTimeMillis());
	    	
	    	// Get the timestamp in ms at midnight today.  This is needed for due date
	    	// color-coding.
	    	_midnightToday = Util.getMidnight(System.currentTimeMillis()+_timeZoneOffset);
		}
		finally
		{
			if (hasSem)
				Util._semaphore.release();
			if (viewCursor!=null) viewCursor.close();
		}
	}
	
	@Override
	public void onDataSetChanged()
	{
		// Get the offset in ms between the home time zone and the local one:
		TimeZone sysTimeZone = TimeZone.getDefault();
		TimeZone appTimeZone = TimeZone.getTimeZone(Util.settings.getString(
			"home_time_zone", "America/Los_Angeles"));
		_timeZoneOffset = sysTimeZone.getOffset(System.currentTimeMillis()) -
			appTimeZone.getOffset(System.currentTimeMillis());

		// Get the timestamp in ms at midnight today.  This is needed for due date
		// color-coding.
		_midnightToday = Util.getMidnight(System.currentTimeMillis()+_timeZoneOffset);

		boolean hasSem = false;
		if (!Synchronizer.isSyncing())
			hasSem = Util.acquireSemaphore("ScrollableRemoteViewsFactory", _c);
		Cursor viewCursor = null;
		try
		{
	        // Initialize the orphaned subtask list:
	        if (orphanedSubtaskIDs == null)
	        	orphanedSubtaskIDs = new HashSet<Long>();
	        else
	        	orphanedSubtaskIDs.clear();
	        
			// Get the data on the view to display:
	        viewCursor = (new ViewsDbAdapter()).getView("widget",Integer.valueOf(_appWidgetId).
	        	toString());
	        if (!viewCursor.moveToFirst())
	        {
	            Util.log("View is not defined in ScrollableRemoteViewsFactory.onDataSetChanged()");
	            return;
	        }
	        _viewID = Util.cLong(viewCursor, "_id");
	        
	        // Initialize the Display Options:
	        _displayOptions = new DisplayOptions(Util.cString(viewCursor, "display_string"));
	        _colorCodeEnabled = true;
	        if (_displayOptions.widgetColorCodedField.length()==0) _colorCodeEnabled = false;
	        _extraFieldEnabled = true;
	        if (_displayOptions.widgetExtraField.length()==0) _extraFieldEnabled = false;
	        
			String query = Util.getTaskSqlQuery(_viewID, viewCursor, _c);
	        if (query==null || query.equals(""))
	        {
	        	Util.log("Query is blank in ScrollableRemoteViewsFactory.onDataSetChanged().");
	        	return;
	        }
			
	        // If an option was set to not requery the database, then stop here:
	        if (Build.VERSION.SDK_INT>=16 && _taskList!=null && _taskList.size()>0)
	        {
	        	AppWidgetManager awm = AppWidgetManager.getInstance(_c);
	        	Bundle widgetOptions = awm.getAppWidgetOptions(_appWidgetId);
	        	if (widgetOptions.containsKey("dont_requery_database") && widgetOptions.getBoolean(
	        		"dont_requery_database"))
	        	{
	        		// If we also have a task ID with new completion status, update the local array:
	        		if (widgetOptions.containsKey("task_id") && widgetOptions.containsKey("is_completed"))
	        		{
	        			long taskID = widgetOptions.getLong("task_id");
	        			if (_taskHash.containsKey(taskID))
	        			{
	        				_taskHash.get(taskID).task.completed = widgetOptions.getBoolean("is_completed");
	        				widgetOptions.remove("task_id");
	        				widgetOptions.remove("is_completed");
	        			}
	        		}
	        		
	        		// Reset the option and stop here without requerying.
	        		widgetOptions.putBoolean("dont_requery_database", false);
	        		awm.updateAppWidgetOptions(_appWidgetId, widgetOptions);
	        		return;
	        	}
	        }
	        
	        runQuery(query);
		}
		finally
		{
			if (hasSem)
				Util._semaphore.release();
			if (viewCursor!=null) viewCursor.close();
		}
	}

	public void onDestroy()
	{
	}
	
	public int getCount()
	{
		return _taskList.size();
	}
	
	@Override
	public long getItemId(int position)
	{
		if (position>=_taskList.size())
		{
			// I have seen this happen, even though it shouldn't.
			return 0;
		}
		UTLTaskDisplay td = _taskList.get(position).get("task");
		return td.task._id;
	}

	@Override
	public RemoteViews getLoadingView()
	{
		// TODO Auto-generated method stub
		return _loadingRow;
	}

	@Override
	public int getViewTypeCount()
	{
		return 1;
	}

	@Override
	public boolean hasStableIds()
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onCreate()
	{
		// TODO Auto-generated method stub
		
	}

    // This MUST be overwritten by subclasses:
	@Override
	public RemoteViews getViewAt(int position)
	{
		return null;
	}
	
	// Given a cursor, get an instance of UTLTaskDisplay:
    private UTLTaskDisplay cursorToUTLTaskDisplay(Cursor c)
    {
        UTLTaskDisplay td = new UTLTaskDisplay();
        
        // The cursor fields are in the same order that TasksDbAdapter expects, so use 
        // the TasksDbAdapter function to get the UTLTask object:
        td.task = (new TasksDbAdapter()).getUTLTask(c);
        
        td.firstTagName = Util.cString(c, "tag_name");
        td.accountName = Util.cString(c, "account");
        td.folderName = Util.cString(c,"folder");
        td.contextName = Util.cString(c,"context");
        td.goalName = Util.cString(c, "goal");
        td.locationName = Util.cString(c, "location");
        td.numTags = Util.cLong(c, "num_tags");
        
        // Collaboration - owner:
        td.ownerName = Util.cString(c,"owner_name");
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        
        // Collaboration - Assignor / Added By:
        td.assignorName = Util.cString(c, "assignor_name");
        
        // Collaboration - Shared With:
        if (td.task.shared_with.length()>0)
        {
        	UTLAccount a = (new AccountsDbAdapter()).getAccount(td.task.account_id);
        	String[] collIDs = td.task.shared_with.split("\n");
        	td.sharedWith = "";
        	for (int i=0; i<collIDs.length; i++)
        	{
        		if (td.sharedWith.length()>0)
        			td.sharedWith += ", ";
        		if (collIDs[i].equals(a.td_userid))
        			td.sharedWith += Util.getString(R.string.Myself);
        		else
        		{
        			UTLCollaborator co = cdb.getCollaborator(td.task.account_id, collIDs[i]);
        			if (co!=null)
        				td.sharedWith += co.name;        				
        		}
        	}
        }
        else
        	td.sharedWith = Util.getString(R.string.None);

        return(td);
    }
    
    // Run the SQL query and generate the task list for the widget:
    private void runQuery(String query)
    {
    	Cursor c = Util.db().rawQuery(query, null);
    	if (!c.moveToFirst())
    	{
    		// TBD
    	}
    	
    	// Convert the database query results into a structure that can be re-ordered:
    	if (_taskList==null)
    	{
    		_taskList = new ArrayList<HashMap<String,UTLTaskDisplay>>();
    		_taskHash = new HashMap<Long,UTLTaskDisplay>();
    	}
    	else
    	{
    		_taskList.clear();
    		_taskHash.clear();
    	}
        c.moveToPosition(-1);
        while(c.moveToNext())
        {
        	HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
        	UTLTaskDisplay td = this.cursorToUTLTaskDisplay(c);
            hash.put("task", td);
            _taskList.add(hash);
            _taskHash.put(td.task._id, td);
        }
        c.close();
        
        // If necessary, reorder the task list to put subtasks below their parents.
        // This function also generates a list of parent tasks with subtasks.
        reorder_task_list();
    }
    
    // If the task list indents subtasks, we need to reorder the list:
    private void reorder_task_list()
    {
        // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
        // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
    	if (_subLists==null)
    	{
    		_subLists = new HashMap<Long,ArrayList<UTLTaskDisplay>>();
    	}
    	else
    	{
    		_subLists.clear();
    	}
        
        // This is an ArrayList of UTLTaskDisplay objects that are NOT subtasks:
        ArrayList<UTLTaskDisplay> parentList = new ArrayList<UTLTaskDisplay>();
        
        // We also need a hash of all task IDs:
        HashSet<Long> allIDs = new HashSet<Long>();
        
        // Populate the 3 lists described above:
        Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
            UTLTaskDisplay td = it.next().get("task");
            allIDs.add(td.task._id);
            if (td.task.parent_id==0)
            {
                // Not a subtask:
                parentList.add(td);
            }
            else
            {
                if (!_subLists.containsKey(td.task.parent_id))
                {
                    _subLists.put(td.task.parent_id, new ArrayList<UTLTaskDisplay>());
                }
                _subLists.get(td.task.parent_id).add(td);
            }
        }
        
        if (_displayOptions.widgetSubtaskOption.equals("indented") && _settings.
        	getBoolean("subtasks_enabled", true))
        {
        	// Subtasks are indented:
        	if (_displayOptions.widgetParentOption==1)
        	{
        		// Orphaned subtasks will not be displayed.
	            // Clear out and repopulate the main list for this class:
	            _taskList.clear();
	            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
	            while (it2.hasNext())
	            {
	                // Add in the non-subtask:
	                UTLTaskDisplay td = it2.next();
	                td.level = 0;
	                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
	                hash.put("task", td);
	                _taskList.add(hash);
	                
	                // If this task has any children, then add them in next:
	                if (_subLists.containsKey(td.task._id))
	                	addChildTasksToList(td.task._id, 0);
	            }
        	}
        	else
        	{
        		// Orphaned subtasks will be displayed at the same level as parent
        		// tasks.
         		
        		ArrayList<HashMap<String,UTLTaskDisplay>> taskList2 = 
        			(ArrayList<HashMap<String,UTLTaskDisplay>>)_taskList.clone();
        		_taskList.clear();
        		Iterator<HashMap<String,UTLTaskDisplay>> it2 = taskList2.iterator();
        		while (it2.hasNext())
        		{
        			UTLTaskDisplay td = it2.next().get("task");
        			if (td.task.parent_id==0)
        			{
        				// It's not a subtask.  Add it into the final task list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
        					UTLTaskDisplay>();
        				td.level = 0;
    	                hash.put("task", td);
    	                _taskList.add(hash);
    	                
    	                // If this task has any children, then add them in next:
    	                if (_subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        			else if (!allIDs.contains(td.task.parent_id))
        			{
        				// It's an orphaned subtask.  At it into the final list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
    						UTLTaskDisplay>();
        				td.level = 0;
        				hash.put("task", td);
        				_taskList.add(hash);
        				orphanedSubtaskIDs.add(td.task._id);
        				
        				// If this task has any children, then add them in next:
    	                if (_subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        		}        		
        	}
        }
        else if (_displayOptions.widgetSubtaskOption.equals("separate_screen") && _settings.
        	getBoolean("subtasks_enabled", true))
        {
        	// For this option, subtasks are not displayed at all here, so 
        	// we need to repopulate the main list with only parent tasks:
        	_taskList.clear();
            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
            while (it2.hasNext())
            {
                // Add in the non-subtask:
                UTLTaskDisplay td = it2.next();
                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
                td.level = 0;
                hash.put("task", td);
                _taskList.add(hash);                
            }
        }
    }

    // Add child tasks to the list of tasks to display:
    void addChildTasksToList(long taskID, int parentLevel)
    {
    	ArrayList<UTLTaskDisplay> childList = _subLists.get(taskID);
        Iterator<UTLTaskDisplay> it3 = childList.iterator();
        while (it3.hasNext())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay child = it3.next();
            child.level = parentLevel+1;
            hash.put("task", child);
            _taskList.add(hash);
            
            if (_subLists.containsKey(child.task._id))
            	addChildTasksToList(child.task._id, parentLevel+1);
        }
    }
}
