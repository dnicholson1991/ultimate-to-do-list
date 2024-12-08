package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

/** This parses the text we've received through speech recognition, and returns information on 
 * tasks to be added or modified, or on views to be read out.
 * This class is a base class for other language-specific classes.  In includes methods that are not
 * language-specific.
 * @author Nicholson
 */
public abstract class SpeechParser
{
	// Types of operations that are supported:
	public static final int OP_ADD = 1;
	public static final int OP_MODIFY = 2;
	public static final int OP_READ = 3;
		
	protected Context _c;
	protected SharedPreferences _settings;
	
	// Links to the database:
	protected AccountsDbAdapter _accountsDB;
	protected TasksDbAdapter _tasksDB;
	protected FoldersDbAdapter _foldersDB;
	protected ContextsDbAdapter _contextsDB;
	protected GoalsDbAdapter _goalsDB;
	protected LocationsDbAdapter _locDB;
	protected TagsDbAdapter _tagsDB;
	protected ViewsDbAdapter _viewsDB;
	
	public SpeechParser(Context c)
	{
		_c = c;
		_settings = _c.getSharedPreferences(Util.PREFS_NAME, 0);
		
		_accountsDB = new AccountsDbAdapter();
		_tasksDB = new TasksDbAdapter();
		_foldersDB = new FoldersDbAdapter();
		_contextsDB = new ContextsDbAdapter();
		_goalsDB = new GoalsDbAdapter();
		_locDB = new LocationsDbAdapter();
		_tagsDB = new TagsDbAdapter();
		_viewsDB = new ViewsDbAdapter();
	}
	
	/** Determine if the user's current language is supported. */
	public static boolean isLanguageSupported()
	{
		Locale l = Locale.getDefault();
		if (l.getLanguage().equals("en"))
			return true;
		else
			return false;
	}
	
	/** Get a specific SpeechParser instance based on the user's language.  If the language is not
	 * supported, an English version will be returned.
	 */
	public static SpeechParser getSpeechParser(Context c)
	{
		return new SpeechParserEnglish(c);
	}
	
	/** Get the Locale of the SpeechParser that will be used.  If the user's home language 
	 * is not supported, English is used. */
	public static Locale getLocale()
	{
		Locale l = Locale.getDefault();
		if (l.getLanguage().equals("en"))
			return l;
		else
			return Locale.US;
	}
	
	
	/** Determine if the user is asking to add a task, edit a task, or read a task list.
	 * @param speech - The output of the speech recognizer.
	 * @return Either OP_ADD, OP_MODIFY, or OP_READ
	 */
	public abstract int getOperationType(String speech);
	
	/** Parse speech that corresponds to a new task.
	 * @param ts - A UTLTaskSpeech object that will be filled in with task information that
	 *     was retrieved.
	 * @param speech - The text of the speech.
	 * @return An error message in the language used by the subclass.  On success, the number of fields
	 *     that were successfully parsed is returned as a string.  This helps to determine which of the speech
	 *     recognition candidates is best.
	 */
	protected abstract String parseNewTask(UTLTaskSpeech ts, String speech);
	
	/** Parse speech that corresponds to a task update.
	 * @param speech - The text of the speech.
	 * @return - A UTLTaskSpeech object.  If an error occurs, the error field will be set to a non-empty
	 *    string.
	 */
	protected abstract UTLTaskSpeech parseTaskUpdate(String speech);
	
	/** Parse speech that corresponds to a read operation.
	 * @param speech - The text of the speech.
	 * @return An error message in the language used by the subclass.  On success, the
	 *     database ID of the view to be read is returned (as a string).  */
	protected abstract String parseRead(String speech);
	
	/** Convenience function to get a GregorianCalendar object, and set it to today's date and
	 * time. */
	protected GregorianCalendar getCalendar()
	{
		GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone(_settings.getString(PrefNames.
			HOME_TIME_ZONE,"")));
		cal.setTimeInMillis(System.currentTimeMillis());
		return cal;
	}
	
	/** Convenience function to set a GregorianCalendar object to midnight. */
	protected void setToMidnight(GregorianCalendar cal)
	{
		cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.AM_PM,Calendar.AM);
	}
	
	/** Fill in the defaults for a UTLTaskSpeech object that has been partially created from the user's
	 * speech.
	 */
	protected void fillInTaskDefaults(UTLTaskSpeech ts)
	{
		// Default Start Date:
		if (!ts.startDateSet && _settings.getString(PrefNames.VM_DEFAULT_START_DATE, "").length()>0 &&
			_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
		{
			if (_settings.getString(PrefNames.VM_DEFAULT_START_DATE, "").equals("today"))
			{
				ts.task.start_date = Util.getMidnight(System.currentTimeMillis());
				ts.startDateSet = true;
			}
			if (_settings.getString(PrefNames.VM_DEFAULT_START_DATE, "").equals("tomorrow"))
			{
				GregorianCalendar c = getCalendar();
				setToMidnight(c);
		        c.add(Calendar.DATE, 1);				
				ts.task.start_date = c.getTimeInMillis();
				ts.startDateSet = true;
			}
		}
		
		// Default Due Date:
		if (!ts.dueDateSet && _settings.getString(PrefNames.VM_DEFAULT_DUE_DATE, "").length()>0 &&
			_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
		{
			if (_settings.getString(PrefNames.VM_DEFAULT_DUE_DATE, "").equals("today"))
			{
				ts.task.due_date = Util.getMidnight(System.currentTimeMillis());
				ts.dueDateSet = true;
			}
			if (_settings.getString(PrefNames.VM_DEFAULT_DUE_DATE, "").equals("tomorrow"))
			{
				GregorianCalendar c = getCalendar();
				setToMidnight(c);
		        c.add(Calendar.DATE, 1);				
				ts.task.due_date = c.getTimeInMillis();
				ts.dueDateSet = true;
			}
		}
		
		// Default Account.  An account must be chosen even if the user didn't set a default or speak
		// an account name.
		UTLAccount a = new UTLAccount();
		if (!ts.accountSet)
		{
			if (_settings.getLong(PrefNames.VM_DEFAULT_ACCOUNT, 0)>0)
			{
				// Make sure the default still exists.
				a = _accountsDB.getAccount(_settings.getLong(PrefNames.VM_DEFAULT_ACCOUNT,0));
				if (a!=null)
				{
					ts.accountSet = true;
					ts.task.account_id = _settings.getLong(PrefNames.VM_DEFAULT_ACCOUNT,0);
				}
			}
			if (!ts.accountSet)
			{
				// Use the first account in the database.
				Cursor c = _accountsDB.getAllAccounts();
				try
				{
					c.moveToFirst();
					a = _accountsDB.getUTLAccount(c);
					ts.accountSet = true;
					ts.task.account_id = a._id;
				}
				finally
				{
					c.close();
				}
			}
		}
		else
		{
			a = _accountsDB.getAccount(ts.task.account_id);
		}
		
		// Add the task to the calendar?
		if (!ts.calendarSet && _settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
		{
			ts.calendarSet = true;
			ts.addToCalendar = _settings.getBoolean(PrefNames.VM_DEFAULT_ADD_TO_CAL, false);
		}
		
		// Default Folder:
		if (!ts.folderSet && _settings.getLong(PrefNames.VM_DEFAULT_FOLDER, 0)>0 &&
			_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
		{
			// Make sure the folder still exists and it's in the same account.
			Cursor c = _foldersDB.getFolder(_settings.getLong(PrefNames.VM_DEFAULT_FOLDER, 0));
			try
			{
				if (c.moveToFirst())
				{
					if (a._id==Util.cLong(c, "account_id"))
					{
						ts.folderSet = true;
						ts.task.folder_id = Util.cLong(c, "_id");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Default Context:
		if (!ts.contextSet && _settings.getLong(PrefNames.VM_DEFAULT_CONTEXT, 0)>0 &&
			_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
		{
			// Make sure the context still exists and it's in the same account.
			Cursor c = _contextsDB.getContext(_settings.getLong(PrefNames.VM_DEFAULT_CONTEXT, 0));
			try
			{
				if (c.moveToFirst())
				{
					if (a._id==Util.cLong(c, "account_id"))
					{
						ts.contextSet = true;
						ts.task.context_id = Util.cLong(c, "_id");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Default Goal:
		if (!ts.goalSet && _settings.getLong(PrefNames.VM_DEFAULT_GOAL, 0)>0 &&
			_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
		{
			// Make sure the goal still exists and it's in the same account.
			Cursor c = _goalsDB.getGoal(_settings.getLong(PrefNames.VM_DEFAULT_GOAL, 0));
			try
			{
				if (c.moveToFirst())
				{
					if (a._id==Util.cLong(c, "account_id"))
					{
						ts.goalSet = true;
						ts.task.goal_id = Util.cLong(c, "_id");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Default Location:
		if (!ts.locationSet && _settings.getLong(PrefNames.VM_DEFAULT_LOCATION, 0)>0 &&
			_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
		{
			// Make sure the location still exists and it's in the same account.
			UTLLocation loc = _locDB.getLocation(_settings.getLong(PrefNames.VM_DEFAULT_LOCATION,0));
			if (loc!=null && loc.account_id==a._id)
			{
				ts.locationSet = true;
				ts.task.location_id = loc._id;
			}
		}
		
		// Default Priority:
		if (!ts.prioritySet && _settings.getBoolean(PrefNames.PRIORITY_ENABLED,true))
		{
			ts.prioritySet = true;
			ts.task.priority = _settings.getInt(PrefNames.VM_DEFAULT_PRIORITY, 0);
		}
		
		// Default Status:
		if (!ts.statusSet && _settings.getBoolean(PrefNames.STATUS_ENABLED, true))
		{
			ts.statusSet = true;
			ts.task.status = _settings.getInt(PrefNames.VM_DEFAULT_STATUS, 0);
		}
		
		// Default Tags:
		if (!ts.tagSet && _settings.getString(PrefNames.VM_DEFAULT_TAGS, "").length()>0 &&
			_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
		{
			String[] tags = _settings.getString(PrefNames.VM_DEFAULT_TAGS, "").split(",");
			for (int i=0; i<tags.length; i++)
			{
				ts.tags.add(tags[i]);
			}
			ts.tagSet = true;
		}
	}
	
	//
	// Parsing functions that don't need a separate implementation for each language.
	//
	
	/** Parse a string believed to be a folder.
	 * @param folderStr - A string believed to be a folder.
	 * @param accountID - An optional account ID to narrow the search. Pass in 0 to search all accounts.
	 * @return - The database folder ID, or 0 if the folder is not found.
	 */
	protected long parseFolder(String folderStr, long accountID)
	{
		folderStr = folderStr.toLowerCase();
		String queryStr = "lower(title)='"+Util.makeSafeForDatabase(folderStr)+"' and archived=0";
		if (accountID>0)
			queryStr += " and account_id="+accountID;
		Cursor c = (new FoldersDbAdapter()).queryFolders(queryStr, null);
		try
		{
			if (c.moveToFirst())
				return Util.cLong(c, "_id");
			else
				return 0;
		}
		finally
		{
			c.close();
		}
	}
	
	/** Parse a string believed to be a context.
	 * @param contextStr - A string believed to be a context.
	 * @param accountID - An optional account ID to narrow the search. Pass in 0 to search all accounts.
	 * @return - The database context ID, or 0 if the context is not found.
	 */
	protected long parseContext(String contextStr, long accountID)
	{
		contextStr = contextStr.toLowerCase();
		String queryStr = "lower(title)='"+Util.makeSafeForDatabase(contextStr)+"'";
		if (accountID>0)
			queryStr += " and account_id="+accountID;
		Cursor c = (new ContextsDbAdapter()).queryContexts(queryStr, null);
		try
		{
			if (c.moveToFirst())
				return Util.cLong(c, "_id");
			else
				return 0;
		}
		finally
		{
			c.close();
		}
	}
	
	/** Parse a string believed to be a goal.
	 * @param goalStr - A string believed to be a goal.
	 * @param accountID - An optional account ID to narrow the search. Pass in 0 to search all accounts.
	 * @return - The database goal ID, or 0 if the goal is not found.
	 */
	protected long parseGoal(String goalStr, long accountID)
	{
		goalStr = goalStr.toLowerCase();
		String queryStr = "lower(title)='"+Util.makeSafeForDatabase(goalStr)+"' and archived=0";
		if (accountID>0)
			queryStr += " and account_id="+accountID;
		Cursor c = (new GoalsDbAdapter()).queryGoals(queryStr, null);
		try
		{
			if (c.moveToFirst())
				return Util.cLong(c, "_id");
			else
				return 0;
		}
		finally
		{
			c.close();
		}
	}

	/** Parse a string believed to be a location.
	 * @param locStr - A string believed to be a location name.
	 * @param accountID - An optional account ID to narrow the search. Pass in 0 to search all accounts.
	 * @return - The database location ID, or 0 if not found.
	 */
	protected long parseLocation(String locStr, long accountID)
	{
		locStr = locStr.toLowerCase();
		String queryStr = "lower(title)='"+Util.makeSafeForDatabase(locStr)+"'";
		if (accountID>0)
			queryStr += " and account_id="+accountID;
		Cursor c = _locDB.queryLocations(queryStr, null);
		try
		{
			if (c.moveToFirst())
				return Util.cLong(c, "_id");
			else
				return 0;
		}
		finally
		{
			c.close();
		}
	}
	
	/** Parse a string believed to be an account.
	 * @param accountStr - A string believed to be an account.
	 * @return - The database ID for the account, or 0 if not found.
	 */
	protected long parseAccount(String accountStr)
	{
		accountStr = accountStr.toLowerCase();
		Cursor c = _accountsDB.getAllAccounts();
		try
		{
			c.moveToPosition(-1);
			while (c.moveToNext())
			{
				UTLAccount a = _accountsDB.getUTLAccount(c);
				if (a.name.toLowerCase().equals(accountStr))
					return a._id;
			}
		}
		finally
		{
			c.close();
		}
		return 0;
	}
	
	/** Parse a string believed to be a task title.
	 * @param titleStr - The string believed to be the title.
	 * @param searchComplete - true if completed tasks should be searched.
	 * @param searchIncomplete - true if incomplete tasks should be searched.
	 * @param accountID - An optional account ID to narrow the search. Pass in 0 to search all accounts.
	 * @return - The task ID, or 0 if not found.
	 */
	protected long parseTaskTitle(String titleStr, boolean searchComplete, boolean searchIncomplete,
		long accountID)
	{
		titleStr = titleStr.toLowerCase().trim();
		
		// Generate a query based on the arguments passed in:
		String query = "lower(title)='"+Util.makeSafeForDatabase(titleStr)+"'";
		if (searchComplete && !searchIncomplete)
			query += " and completed=1";
		if (searchIncomplete && !searchComplete)
			query += " and completed=0";
		if (accountID>0)
			query += " and account_id="+accountID;
		
		// Search for the task by exact name:
		Cursor c = _tasksDB.queryTasks(query, null);
		try
		{
			if (c.moveToFirst())
			{
				UTLTask t = _tasksDB.getUTLTask(c);

				return t._id;
			}
		}
		finally
		{
			c.close();
		}
		
		// If we get here, an exact match was not found.  Try searching again, but strip out unimportant
		// words.
		HashSet<String> unimportantWords = new HashSet<String>();
		unimportantWords.add("a");
		unimportantWords.add("an");
		unimportantWords.add("the");
		unimportantWords.add("and");
		unimportantWords.add("but");
		unimportantWords.add("or");
		unimportantWords.add("for");
		unimportantWords.add("on");
		unimportantWords.add("at");
		unimportantWords.add("to");
		unimportantWords.add("from");
		unimportantWords.add("by");
		unimportantWords.add("in");
		unimportantWords.add("of");
		unimportantWords.add("up");
		unimportantWords.add("as");
		unimportantWords.add("it");
		unimportantWords.add("nor");
		unimportantWords.add("am");
		unimportantWords.add("be");
		unimportantWords.add("do");
		unimportantWords.add("if");
		unimportantWords.add("is");
		unimportantWords.add("so");
		ArrayList<String> titleStrWords = split(titleStr);
		if (titleStrWords.size()==1)
		{
			// Looking for unimportant words does not make sense when we have only one word.
			return 0;
		}
		for (int i=0; i<titleStrWords.size(); i++)
		{
			if (unimportantWords.contains(titleStrWords.get(i)))
				titleStrWords.remove(i);
		}
		titleStr = join(titleStrWords);
		
		// Search the tasks, and perform comparisons with unimportant words stripped.
		if (searchComplete && !searchIncomplete)
			query = "completed=1";
		if (searchIncomplete && !searchComplete)
			query = "completed=0";
		if (accountID>0)
			query += " and account_id="+accountID;
		c = _tasksDB.queryTasks(query, null);
		try
		{
			c.moveToPosition(-1);
			while (c.moveToNext())
			{
				String taskTitle = Util.cString(c, "title").toLowerCase();
				ArrayList<String> taskWords = split(taskTitle);
				for (int i=0; i<taskWords.size(); i++)
				{
					if (unimportantWords.contains(taskWords.get(i)))
						taskWords.remove(i);
				}
				taskTitle = join(taskWords);
				if (titleStr.equals(taskTitle))
				{
					return Util.cLong(c,"_id");
				}
			}
		}
		finally
		{
			c.close();
		}
		
		// At this point, we haven't found anything.
		return 0;
	}
	
	/** Parse a string believed to be a view name.
	 * @param topLevel - The top level, such as ViewNames.FOLDERS.
	 * @param viewNameStr - The string believed to be a view name.
	 * @return The database view ID, or 0 if the view is not found.
	 */
	protected long parseViewName(String topLevel, String viewNameStr)
	{
		if (topLevel.equals(ViewNames.TAGS) || topLevel.equals(ViewNames.MY_VIEWS))
		{
			Cursor c = _viewsDB.queryByViewName(topLevel, viewNameStr);
			try
			{
				if (c.moveToFirst())
					return Util.cLong(c, "_id");
				else
					return 0;
			}
			finally
			{
				c.close();
			}
		}
		
		if (topLevel.equals(ViewNames.BY_STATUS))
		{
			String status = viewNameStr.toLowerCase();
			String statusNum = "";
			if (status.equals("none") || status.equals("no") || status.equals("nothing"))
				statusNum = "0";
			if (status.equals("next action"))
				statusNum = "1";
			if (status.equals("active"))
				statusNum = "2";
			if (status.equals("planning"))
				statusNum = "3";
			if (status.equals("delegated"))
				statusNum = "4";
			if (status.equals("waiting"))
				statusNum = "5";
			if (status.equals("hold"))
				statusNum = "6";
			if (status.equals("postponed"))
				statusNum = "7";
			if (status.equals("someday"))
				statusNum = "8";
			if (status.equals("canceled"))
				statusNum = "9";
			if (status.equals("reference"))
				statusNum = "10";
			
			if (statusNum.equals(""))
				return 0;
			
			Cursor c = _viewsDB.getView(topLevel, statusNum);
			try
			{
				if (c.moveToFirst())
					return Util.cLong(c, "_id");
				else
					return 0;
			}
			finally { c.close(); }
		}
		
		long id = 0;
		if (topLevel.equals(ViewNames.FOLDERS))
			id = this.parseFolder(viewNameStr, 0);
		
		if (topLevel.equals(ViewNames.CONTEXTS))
			id = this.parseContext(viewNameStr, 0);
		
		if (topLevel.equals(ViewNames.GOALS))
			id = this.parseGoal(viewNameStr, 0);
		
		if (topLevel.equals(ViewNames.LOCATIONS))
			id = this.parseLocation(viewNameStr, 0);
		
		if (id!=0)
		{
			Cursor c = _viewsDB.getView(topLevel, Long.valueOf(id).toString());
			try
			{
				if (c.moveToFirst())
					return Util.cLong(c, "_id");
				else
					return 0;
			}
			finally { c.close(); }
		}
		else
			return 0;
	}
	
	//
	// Utility Functions for Array and String Manipulation:
	//
	
	/** Split a string into an ArrayList<String> of words */
	protected ArrayList<String> split(String str)
	{
		String[] array = str.split("\\s+");
		ArrayList<String> result = new ArrayList<String>();
		for (int i=0; i<array.length; i++)
		{
			result.add(array[i]);
		}
		return result;
	}
	
	/** Combine words in an ArrayList<String> into a single String */
	protected String join(ArrayList<String> arrayList)
	{
		String result = "";
		for (int i=0; i<arrayList.size(); i++)
		{
			if (i>0) result += " ";
			result += arrayList.get(i);
		}
		return result;
	}
	
	/** Get a segment of an ArrayList<String>.  Returns a new ArrayList<String>.  Does not modify source.
	 * @param source - The ArrayList 
	 * @param startIndex - The starting index.  Must be 0 or greater. 
	 * @param lastIndex - The last index to extract.  Allowed to be greater than the array size. */
	protected ArrayList<String> getSegment(ArrayList<String> source, int startIndex, int lastIndex)
	{
		ArrayList<String> result = new ArrayList<String>();
		if (lastIndex >= source.size())
			lastIndex = source.size()-1;
		for (int i=startIndex; i<=lastIndex; i++)
			result.add(source.get(i));
		return result;
	}
	
	/** Slice an arrayList<String>.  Removes elements from the source and returns a new ArrayList
	 * containing those elements.
	 * @param source - The ArrayList 
	 * @param startIndex - The starting index.  Must be 0 or greater. 
	 * @param lastIndex - The last index to extract.  Allowed to be greater than the array size.
	 */
	protected ArrayList<String> slice(ArrayList<String> source, int startIndex, int lastIndex)
	{
		ArrayList<String> result = new ArrayList<String>();
		if (lastIndex >= source.size())
			lastIndex = source.size()-1;
		for (int i=startIndex; i<=lastIndex; i++)
			result.add(source.remove(startIndex));
		return result;
	}
	
}
