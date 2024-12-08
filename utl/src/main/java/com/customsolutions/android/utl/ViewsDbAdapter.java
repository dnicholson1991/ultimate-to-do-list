package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.common.base.Joiner;

// Provides the database interface for UTL views:

public class ViewsDbAdapter 
{
    // The table we're working with:
    private static final String TABLE = "views";
    
    // Table creation statement:
    private static final String TABLE_CREATE = "create table if not exists "+TABLE+" ("+
        "_id integer primary key autoincrement, "+
        "top_level text not null, "+
        "view_name text not null, "+  // Set to empty string if not used.
        "sort_string text not null, "+
        "display_string text not null, "+
        "sort_order_string text, "+  // 0 for normal sort order, 1 for reversed, separated by ,
                                     // If blank, it's assumed to be all 0
        "on_home_page integer"+    // 1 if view should appear at top of nav drawer, 0 otherwise
        ")";
    
    public static final String[] INDEXES = {
    	"create index if not exists top_level_index on views(top_level)"
    };
    
    // The order in which the columns are queried:
    private static final String[] COLUMNS = { "_id","top_level","view_name","sort_string",
        "display_string","sort_order_string","on_home_page" };
    
    private static boolean tablesCreated = false;
    
    // The constructor.  This opens the database for access:
    public ViewsDbAdapter() throws SQLException
    {
        if (!tablesCreated)
        {
        	Util.db().execSQL(TABLE_CREATE);
        	for (int i=0; i<INDEXES.length; i++)
        	{
        		Util.db().execSQL(INDEXES[i]);
        	}
        	tablesCreated = true;
        }
    }
    
    // Add a new view.  If successful, the ID of the view is returned, else -1.
    public long addView(String topLevel, String viewName, String sortString, 
        DisplayOptions displayOptions)
    {
        ContentValues values = new ContentValues();
        values.put("top_level", topLevel);
        values.put("view_name", viewName);
        values.put("sort_string", sortString);
        values.put("display_string",displayOptions.getDatabaseString());
        values.put("sort_order_string", "");
        return Util.db().insert(TABLE, null, values);
    }
    
    /** Copy the sort and display settings of one view into another view.
     * Returns true if updated, else false.
     */
    public boolean copySortAndDisplay(long sourceViewID, long destinationViewID)
    {
    	Cursor c = getView(sourceViewID);
    	if (c.moveToFirst())
    	{
    		ContentValues values = new ContentValues();
    		values.put("sort_string", Util.cString(c, "sort_string"));
    		values.put("display_string", Util.cString(c, "display_string"));
    		values.put("sort_order_string", Util.cString(c, "sort_order_string"));
    		c.close();
    		return Util.db().update(TABLE, values, "_id="+destinationViewID, null) > 0;
    	}
    	c.close();
    	return false;
    }
    
    /** Copy the sort settings of one view into another view.
     * Returns true if updated, else false.
     */
    public boolean copySort(long sourceViewID, long destinationViewID)
    {
    	Cursor c = getView(sourceViewID);
    	if (c.moveToFirst())
    	{
    		ContentValues values = new ContentValues();
    		values.put("sort_string", Util.cString(c, "sort_string"));
    		values.put("sort_order_string", Util.cString(c, "sort_order_string"));
    		c.close();
    		return Util.db().update(TABLE, values, "_id="+destinationViewID, null) > 0;
    	}
    	c.close();
    	return false;
    }
    
    // Remove a view.  Returns true on success, else false.
    public boolean deleteView(long id)
    {
        return Util.db().delete(TABLE, "_id=" + id, null) > 0;
    }
    
    // Modify a view.  Returns true if updated, false otherwise.
    public boolean modifyView(long id, String topLevel, String viewName, String sortString,
        DisplayOptions displayOptions)
    {
        ContentValues values = new ContentValues();
        values.put("top_level", topLevel);
        values.put("view_name", viewName);
        values.put("sort_string", sortString);
        values.put("display_string",displayOptions.getDatabaseString());
        return Util.db().update(TABLE, values, "_id=" + id, null) > 0;
    }
    
    // Change a view's sort order (not taking reversed sorting into account):
    public boolean changeSortOrder(long id, String newSortString)
    {
    	ContentValues values = new ContentValues();
    	values.put("sort_string", newSortString);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Change the sort order normal/reverse sort options:
    // The input is 1 to 3 numbers, separated by a comma.  0=normal sort, 1=reverse
    // The order of the numbers matches the sort_string for the view.
    public boolean changeReverseSortOptions(long id, String newSortOrderString)
    {
    	ContentValues values = new ContentValues();
    	values.put("sort_order_string", newSortOrderString);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }

    /** Set the manual sort order to the first sort order. Also moves the 1st sort order to the 2nd,
     * and the second to the 3rd.  The 3rd sort order is deleted. */
    public void makeManualSortFirst(long viewID)
    {
        ContentValues values = new ContentValues();
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+viewID, null, null, null, null);
        if (c.moveToFirst())
        {
            String[] sortFields = Util.cString(c, "sort_string").split(",");
            String[] sortOrders = Util.cString(c, "sort_order_string").split(",");
            if (sortFields.length>2)
            {
                sortFields[2] = new String(sortFields[1]);
                if (sortOrders.length>2)
                    sortOrders[2] = new String(sortOrders[1]);
            }
            if (sortFields.length>1)
            {
                sortFields[1] = new String(sortFields[0]);
                if (sortOrders.length>1)
                    sortOrders[1] = new String(sortOrders[0]);
            }
            if (sortFields.length>0)
            {
                sortFields[0] = "tasks.sort_order";
                if (sortOrders.length>0 && sortOrders[0].length()>0)
                    sortOrders[0] = "0";
                else
                    sortOrders = new String[] { "0","0","0" };
                values.put("sort_string", Joiner.on(",").join(sortFields));
                values.put("sort_order_string", Joiner.on(",").join(sortOrders));
            }
            else
            {
                // For some reason, this view has no sort order at all.
                values.put("sort_string", "tasks.sort_order");
                values.put("sort_order_string", "");
            }
            Util.db().update(TABLE, values, "_id="+viewID, null);
            c.close();
            return;
        }
        c.close();
    }

    // Change a view's display options:
    public boolean changeDisplayOptions(long id, DisplayOptions displayOptions)
    {
    	ContentValues values = new ContentValues();
    	values.put("display_string",displayOptions.getDatabaseString());
        return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Rename a view.  Returns true if updated, false otherwise.
    public boolean renameView(long id, String newViewName)
    {
    	ContentValues values = new ContentValues();
    	values.put("view_name",newViewName);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Change the option to display/not display on home page:
    public boolean setShowAtTop(long id, boolean showAtTop)
    {
    	ContentValues values = new ContentValues();
    	values.put("on_home_page", showAtTop ? 1 : 0);
    	return Util.db().update(TABLE, values, "_id="+id, null) > 0;
    }
    
    // Convert a temporary view to a view in the my views area:
    public void moveTemporaryToMyViews(long id)
    {
    	ContentValues values = new ContentValues();
    	values.put("top_level","my_views");
    	Util.db().update(TABLE, values, "_id=" + id, null);
    }
    
    // Get a specific view.  Returns a database cursor.
    public Cursor getView(long id)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "_id="+id, null, null, null, null);
        c.moveToFirst();
        return c;
    }
    
    // Get all views within a certain top level.  Returns a database cursor pointed to
    // the first result (if any):
    public Cursor getViewsByLevel(String topLevel)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "top_level='"+topLevel+"'", null, null, null, 
            "view_name");
        c.moveToFirst();
        return c;
    }
    
    // Get all custom views that should be displayed on the home page:
    public Cursor getTopLevelViews()
    {
    	Cursor c = Util.db().query(TABLE, COLUMNS, "top_level='my_views' and on_home_page=1", null, 
    		null, null, "view_name");
    	return c;
    }
    
    // Given a top level and view name, get a cursor containing the view data.  If the view
    // does not exist, it is created with default values.  On failure, a blank cursor is
    // returned.  viewName must be an empty string if not used.
    public Cursor getView(String topLevel, String viewName)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "top_level='"+topLevel+"' and view_name='"+
            Util.makeSafeForDatabase(viewName)+"'", null, null, null, null);
        if (!c.moveToFirst() && !topLevel.equals("my_views"))
        {
            // No results found.  If this is for a widget, check to see if there is
        	// a view with "widget" as the top level, but no widget ID.
        	c.close();
        	if (topLevel.equals("widget"))
        	{
        		c = Util.db().query(TABLE, COLUMNS, "top_level='"+topLevel+"' and view_name=''",
        			null, null, null, null);
        		if (c.moveToFirst())
        		{
        			// Update the view name field in the view, and return it.
        			renameView(Util.cLong(c,"_id"),viewName);
        			c.close();
        			return Util.db().query(TABLE, COLUMNS, "top_level='"+topLevel+
        				"' and view_name='"+viewName+"'",null, null, null, null);
        		}
        		else
        			c.close();
        	}
        	
        	// Create a new view:
            String sortString = getDefaultSortOrder(topLevel);
            DisplayOptions displayOptions = DisplayOptions.getDefaultDisplayOptions(topLevel);
            long rowID = addView(topLevel,viewName,sortString,displayOptions);
            if (rowID==-1)
            {
                Util.log("Unable to add new view ("+topLevel+","+viewName+") to database.");
                return c;
            }
            
            // Add some default rules to the view:
            boolean result = generateDefaultViewRules(topLevel,viewName,rowID);
            if (result==false)
            {
                Util.log("Unable to generate default view rules for "+topLevel+","+viewName);
                return c;
            }
            
            return getView(rowID);
        }
        
        return c;
    }
    
    /** Query the database for a view by name.  This performs a case-insensitive search.
     * @param topLevel - One of the predefined values for top level.
     * @param viewName - A string believed to contain a view name (such as a folder name)
     * @return - The view ID.
     */
    public Cursor queryByViewName(String topLevel, String viewName)
    {
        Cursor c = Util.db().query(TABLE, COLUMNS, "top_level='"+topLevel+"' and lower"+
        	"(view_name)='"+Util.makeSafeForDatabase(viewName.toLowerCase())+"'", null, null, 
        	null, null);
    	return c;
    }
    
    // Get the default sort order for a new view:
    public String getDefaultSortOrder(String topLevel)
    {
        // Determine whether start date or due date will be the basis for date sorting,
        // based on the user's features enabled:
        String dateField;
        if (Util.settings.getBoolean("due_date_enabled", true))
        {
            dateField = "tasks.due_date";
        }
        else if (Util.settings.getBoolean("start_date_enabled",true))
        {
            dateField = "tasks.start_date";
        }
        else
        {
            dateField = "none";
        }
        
        String priorityFirst = "tasks.priority,"+dateField+",tasks.title";
        String dateFirst = dateField+",tasks.priority,tasks.title";
        if (dateField.equals("none"))
        {
            if (Util.settings.getBoolean("priority_enabled", true))
            {
                priorityFirst = "tasks.priority,tasks.title";
                dateFirst = "tasks.priority,tasks.title";
            }
            else
            {
                priorityFirst = "tasks.title";
                dateFirst = "tasks.title";
            }
        }
        else
        {
        	if (!Util.settings.getBoolean("priority_enabled", true))
            {
                priorityFirst = dateField+",tasks.title";
                dateFirst = dateField+",tasks.title";
            }
        }
        
        if (topLevel.equals("all_tasks"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("hotlist"))
        {
            return(priorityFirst);
        }
        else if (topLevel.equals("due_today_tomorrow"))
        {
            return(priorityFirst);
        }
        else if (topLevel.equals("overdue"))
        {
            return(priorityFirst);
        }
        else if (topLevel.equals("starred"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("by_status"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("folders"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("contexts"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("goals"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("tags"))
        {
            return(dateFirst);
        }
        else if (topLevel.equals("recently_completed"))
        {
        	if (Util.settings.getBoolean("priority_enabled", true))
        		return("tasks.completion_date,tasks.priority,tasks.title");
        	else
        		return("tasks.completion_date,tasks.title");
        }
        else
        {
            return(dateFirst);
        }
    }
    
    // Generate the default rules for a particular view.  This takes 3 inputs, 
    // an ArrayList of ViewRule objects, an ArrayList of lock integers (each with value 0-2),
    // and an ArrayList of booleans containing the "or" state (true=or, false=and).
    // These 3 ArrayLists will be modified by the function.
    // Returns false if the rules could not be generated.
    public boolean generateDefaultViewRules(String topLevel, String viewName, 
    	ArrayList<ViewRule> rules, ArrayList<Integer> lockList, ArrayList<Boolean> isOrList)
    {
    	rules.clear();
    	lockList.clear();
    	isOrList.clear();
        if (topLevel.equals("all_tasks"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("hotlist"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            if (Util.settings.getBoolean("start_date_enabled", true))
            {
                rules.add(new DateViewRule("start_date",true,0,"<=",true));
                lockList.add(ViewRulesDbAdapter.EDIT_ONLY);
                isOrList.add(false);
            }
            if (Util.settings.getBoolean("priority_enabled", true))
            {
                int minPriority = Util.settings.getInt("hotlist_priority", 4);
                int[] priorityList = new int[6-minPriority];
                for (int i=0; i<priorityList.length; i++)
                {
                    priorityList[i] = minPriority+i;
                }
                rules.add(new MultChoiceViewRule("priority",priorityList));
                lockList.add(ViewRulesDbAdapter.EDIT_ONLY);
                isOrList.add(false);
            }
            if (Util.settings.getBoolean("due_date_enabled", true))
            {
                int numDays = Util.settings.getInt("hotlist_due_date", 0);
                rules.add(new DueDateViewRule(true,numDays,"<=",false,false,false));
                lockList.add(ViewRulesDbAdapter.EDIT_ONLY);
                if (Util.settings.getBoolean("priority_enabled", true))
                	isOrList.add(true);
                else
                	isOrList.add(false);
            }
        }
        else if (topLevel.equals("due_today_tomorrow"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new DueDateViewRule(true,1,"<=",true,true,false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new DueDateViewRule(true,0,">=",true,true,false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("overdue"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new DueDateViewRule(true,-1,"<=",true,false,false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("starred"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new BooleanViewRule("star",true));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("by_status"))
        {
            int statusNum = Integer.parseInt(viewName);
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new MultChoiceViewRule("status",new int[] {statusNum}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("folders"))
        {
            int folderNum = Integer.parseInt(viewName);
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new MultChoiceViewRule("folder_id",new int[] {folderNum}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("contexts"))
        {
            int contextNum = Integer.parseInt(viewName);
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new MultChoiceViewRule("context_id",new int[] {contextNum}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("goals"))
        {
            int goalNum = Integer.parseInt(viewName);
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new MultChoiceViewRule("goal_id",new int[] {goalNum}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("locations"))
        {
            int locNum = Integer.parseInt(viewName);
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new LocationViewRule("location_id",new int[] {locNum}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("tags"))
        {
            String tagName = viewName;
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new MultStringsViewRule("tags.name",new String[] {tagName}));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
        }
        else if (topLevel.equals("recently_completed"))
        {
            rules.add(new BooleanViewRule("completed",true));
            lockList.add(ViewRulesDbAdapter.FULL_LOCK);
            isOrList.add(false);
            rules.add(new DateViewRule("completion_date",true,-14,">=",false));
            lockList.add(ViewRulesDbAdapter.EDIT_ONLY);
            isOrList.add(false);
        }
        else if (topLevel.equals("widget"))
        {
            rules.add(new BooleanViewRule("completed",false));
            lockList.add(ViewRulesDbAdapter.NO_LOCK);
            isOrList.add(false);
            if (Util.settings.getBoolean("start_date_enabled", true))
            {
                rules.add(new DateViewRule("start_date",true,0,"<=",true));
                lockList.add(ViewRulesDbAdapter.NO_LOCK);
                isOrList.add(false);
            }
            if (Util.settings.getBoolean("priority_enabled", true))
            {
                int minPriority = Util.settings.getInt("hotlist_priority", 4);
                int[] priorityList = new int[6-minPriority];
                for (int i=0; i<priorityList.length; i++)
                {
                    priorityList[i] = minPriority+i;
                }
                rules.add(new MultChoiceViewRule("priority",priorityList));
                lockList.add(ViewRulesDbAdapter.NO_LOCK);
                isOrList.add(false);
            }
            if (Util.settings.getBoolean("due_date_enabled", true))
            {
                int numDays = Util.settings.getInt("hotlist_due_date", 0);
                rules.add(new DueDateViewRule(true,numDays,"<=",false,false,false));
                lockList.add(ViewRulesDbAdapter.NO_LOCK);
                if (Util.settings.getBoolean("priority_enabled", true))
                	isOrList.add(true);
                else
                	isOrList.add(false);
            }
        }
        else
        {
            Util.log("Invalid top level passed in: "+topLevel);
            return(false);
        }    
        return true;
    }
    
    // Generate the default rules for a particular view.  This stores the settings in
    // the database and returns true on success or false on failure.  
    public boolean generateDefaultViewRules(String topLevel, String viewName, long viewID)
    {
        ArrayList<ViewRule> rules = new ArrayList<ViewRule>();
        ArrayList<Integer> lockList = new ArrayList<Integer>();
        ArrayList<Boolean> isOrList = new ArrayList<Boolean>();
        if (!generateDefaultViewRules(topLevel,viewName,rules,lockList,isOrList))
        {
        	return false;
        }
        
        // Update the database:
        ViewRulesDbAdapter db = new ViewRulesDbAdapter();
        db.clearAllRules(viewID);
        for (int i=0; i<rules.size(); i++)
        {
            long result = db.addRule(viewID, lockList.get(i), rules.get(i),i,isOrList.get(i));
            if (result==-1)
            {
                return(false);
            }
        }
        return (true);
    }
}
