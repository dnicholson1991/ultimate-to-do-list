package com.customsolutions.android.utl;

import android.database.Cursor;

// This class is used by the ViewRulesList class to hold a ViewRule.  It includes all data
// from the database (unlike the ViewRule class).
public class ViewRuleHolder
{
	public long _id;
	public long view_id;
	public int lock_level;  // 0 = none, 1 = edit only, 2 = full lock
	public String field_name;
	public ViewRule viewRule;
	public int position;
	public boolean is_or;
	public int index;  // When using this in an array or ListView, this is the index
	
	// Construct one of these from a database cursor, as defined in ViewRulesDbAdapter:
	public ViewRuleHolder(Cursor c)
	{
		_id = c.getLong(0);
		view_id = c.getLong(1);
		lock_level = c.getInt(2);
		field_name = c.getString(3);
		ViewRulesDbAdapter rulesDB = new ViewRulesDbAdapter();
		viewRule = rulesDB.getViewRule(c);
		position = c.getInt(5);
		is_or = c.getInt(6)==1 ? true : false;
		index = c.getPosition();
	}
	
	// Construct a blank one:
	public ViewRuleHolder()
	{
		_id = 0;
		view_id = 0;
		lock_level = 2;
		field_name = "";
		viewRule = null;
		position = -1;
		is_or = false;
		position = -1;
		index = -1;
	}
}
