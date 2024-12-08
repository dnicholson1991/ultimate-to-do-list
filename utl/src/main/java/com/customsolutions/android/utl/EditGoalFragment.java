package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.HashSet;

import org.droidparts.widget.ClearableEditText;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

/** Fragment for adding or editing a goal.  Pass in a Bundle with key "mode" and value of ADD or
 * EDIT.  When editing, pass in a key of "id", containing the database ID.
 * @author Nicholson
 *
 */
public class EditGoalFragment extends GenericEditorFragment
{
	// Codes to track responses to activities:
    public static final int GET_ACCOUNTS = 1;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditGoalFragment";
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private Spinner _accounts;
    private CheckBox _archived;
    private Spinner _level;
    private Spinner _contributes;
    
    // The number accounts affects the display:
    private int _numAccounts;
    
    // Quick access to database tables:
    private AccountsDbAdapter _accountsDB;
    private GoalsDbAdapter _goalsDB;
    
    // Stores the account IDs in the accounts spinner:
    private ArrayList<Long> _accountIDs;
    
    // Stores the options in the contributes spinner:
    private ArrayList<Long> _contributesGoalIDs;
    private ArrayList<String> _contributesGoalNames;
    
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_goal, container, false);
    }
    
    /** Called when Activity is started: */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening Goal Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _accountsDB = new AccountsDbAdapter();
        _goalsDB = new GoalsDbAdapter();
        _accountIDs = new ArrayList<Long>();
        _contributesGoalIDs = new ArrayList<Long>();
        _contributesGoalNames = new ArrayList<String>();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_goal_title);
        _accounts = (Spinner)_rootView.findViewById(R.id.edit_goal_account_value);
        _archived = (CheckBox)_rootView.findViewById(R.id.edit_goal_archived_checkbox);
        _level = (Spinner)_rootView.findViewById(R.id.edit_goal_level_value);
        _contributes = (Spinner)_rootView.findViewById(R.id.edit_goal_contributes_value);
        
        if (_op==EDIT)
        	setTitle(R.string.Edit_Goal);
        else
        	setTitle(R.string.Add_a_Goal);
        
        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
    	_numAccounts = c1.getCount();
    	c1.close();
    	
        // Hide the accounts selector if necessary:
    	if (_numAccounts==1 || _op==EDIT)
    	{
    		_rootView.findViewById(R.id.edit_goal_account_section).setVisibility(View.GONE);
    	}
    	
    	// Initialize the accounts spinner.  We do this even if it is hidden, because we will access
    	// the value of this spinner later.
    	ArrayList<String> accountNames = new ArrayList<String>();
    	Cursor c = _accountsDB.getAllAccounts();
    	c.moveToPosition(-1);
    	while (c.moveToNext())
    	{
    		_accountIDs.add(Util.cLong(c, "_id"));
    		accountNames.add(Util.cString(c, "name"));
    	}
    	c.close();
    	_a.initSpinner(_accounts, accountNames);
    	
    	// Set values for the views:
    	if (_op==ADD)
    	{
    		// An added goal cannot be archived:
    		_archived.setChecked(false);
    		
    		// Toodledo won't let us add an archived goal.  Given that users will virtually never
    		// want to do this, we will just disable adding an archived goal globally.
    		_rootView.findViewById(R.id.edit_goal_archived_container).setVisibility(View.GONE);
    		
    		// Set the default account:
    		if (_numAccounts>1)
    		{
    			// Use the default account for task creation:
    			if (_settings.contains(PrefNames.DEFAULT_ACCOUNT))
    			{
    				UTLAccount acc = _accountsDB.getAccount(_settings.getLong(
                    	PrefNames.DEFAULT_ACCOUNT, 0));
    				if (acc!=null)
    				{
    					_accounts.setSelection(_accountIDs.indexOf(acc._id));
    				}
    				else
    					_accounts.setSelection(0);
    			}
    			else
    			{
    				// Just use the first account.
    				_accounts.setSelection(0);
    			}
    		}
    		else
    		{
    			// With only one account, the selection is obvious.
    			_accounts.setSelection(0);
    		}
    		
    		// Set the default level to short-term:
    		_level.setSelection(2);
    		
    		// Update the "contributes" spinner with a valid set of goals:
    		refreshContributesSpinner();
    		
    		// by default, the goal does not contribute to a higher-level goal:
    		_contributes.setSelection(0);
    	}
    	else
    	{
    		// In modify mode, get the values from the database:
    		Cursor goalCursor = _goalsDB.getGoal(_id);
    		if (goalCursor.moveToFirst())
    		{
    			_title.setText(Util.cString(goalCursor, "title"));
    			_level.setSelection(Util.cInt(goalCursor, "level"));
    			_archived.setChecked(Util.cInt(goalCursor, "archived")==1 ? true : false);
    			_accounts.setSelection(_accountIDs.indexOf(Util.cLong(goalCursor, "account_id")));
    			refreshContributesSpinner();
    			_contributes.setSelection(_contributesGoalIDs.indexOf(Util.cLong(goalCursor, "contributes")
    				));
    		}
    		goalCursor.close();
    	}
    	
    	// Handler for the archived row:
    	_rootView.findViewById(R.id.edit_goal_archived_container).setOnClickListener(new 
    		View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_goal_archived_container);
				_archived.toggle();
			}
		});
    	
    	// The contributes spinner needs adjusted whenever the level or account changes:
    	AdapterView.OnItemSelectedListener itemSelectedListener = new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		if (_contributesGoalIDs==null || _contributesGoalIDs.size()==0)
        		{
        			// Contributes spinner has not had an initial set-up yet.
        			return;
        		}
        		
        		// Save the ID of the currently selected contributes item:
        		long contributesGoalID = _contributesGoalIDs.get(_contributes.getSelectedItemPosition());
        		
        		// Update the contents of the contributes spinner:
        		refreshContributesSpinner();
        		
        		// If possible, set the contributes spinner to the same item as before:
        		if (_contributesGoalIDs.indexOf(contributesGoalID)>-1)
        			_contributes.setSelection(_contributesGoalIDs.indexOf(contributesGoalID));
        		else
        			_contributes.setSelection(0);
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		};
		
		_level.setOnItemSelectedListener(itemSelectedListener);
		_accounts.setOnItemSelectedListener(itemSelectedListener);
    }
    
    /** Initialize or refresh the "contributes" spinner.  Include goals at a higher level. */
    private void refreshContributesSpinner()
    {
    	// Clear the list and add a "none" item:
    	_contributesGoalIDs.clear();
    	_contributesGoalNames.clear();
    	_contributesGoalIDs.add(Long.valueOf(0));
    	_contributesGoalNames.add(_a.getString(R.string.None));
    	
    	// Get the currently selected level and the ID of the account the goal is in:
    	int baseLevel = _level.getSelectedItemPosition();
    	long accountID;
    	if (_op==ADD)
    		accountID = _accountIDs.get(_accounts.getSelectedItemPosition());
    	else
    	{
    		Cursor goalCursor = _goalsDB.getGoal(_id);
    		if (goalCursor.moveToFirst())
    			accountID = Util.cLong(goalCursor, "account_id");
    		else
    			return;  // Probably wont happen.
    		goalCursor.close();
    	}
    	
    	// Query the database for a list of possible goals that can be contributed to.
    	// The goals must be at a higher level and in the same account.
    	Cursor candidates = _goalsDB.queryGoals("account_id="+accountID+" and level<"+baseLevel+
    		" and archived=0","title");
    	candidates.moveToPosition(-1);
		while (candidates.moveToNext())
		{
			_contributesGoalIDs.add(Util.cLong(candidates, "_id"));
			_contributesGoalNames.add(Util.cString(candidates, "title"));
		}
		candidates.close();
    	
		// Update the spinner items:
		_a.initSpinner(_contributes,_contributesGoalNames);
    }
    
    /** Check for valid values, when save and exit if possible */
    @Override
    protected void handleSave()
    {
    	Intent i;
    	
    	// Make sure there is a title:
    	if (_title.getText().toString().length()==0)
    	{
    		Util.popup(_a,R.string.Please_enter_a_name);
    		return;
    	}
    	String title = _title.getText().toString();
    	
    	// Make sure the title is not too long:
    	if (_title.getText().toString().length()>Util.MAX_GOAL_TITLE_LENGTH)
    	{
    		Util.popup(_a, R.string.Title_is_too_long);
    		return;
    	}
    	
    	// The name cannot match the name of another goal in the same account:
    	long accountID = _accountIDs.get(_accounts.getSelectedItemPosition());
    	Cursor c = _goalsDB.queryGoals("lower(title)='"+Util.makeSafeForDatabase(title)+"' and "+
    		"account_id="+accountID, null);
    	if (c.moveToFirst())
    	{
    		if (_op==ADD || (_op==EDIT && Util.cLong(c, "_id")!=_id))
    		{
        		Util.popup(_a,R.string.Name_already_exists);
        		c.close();
        		return;
    		}
    	}
    	c.close();
    	
    	// Double-check to make sure the goal is contributing to a valid higher-level goal:
    	long contributesToID = _contributesGoalIDs.get(_contributes.getSelectedItemPosition());
    	int level = _level.getSelectedItemPosition();
    	if (contributesToID>0)
    	{
    		Cursor contributesCursor = _goalsDB.getGoal(contributesToID);
    		if (contributesCursor.moveToFirst())
    		{
    			if (level<=Util.cInt(contributesCursor, "level"))
    			{
    				Util.popup(_a, R.string.Goal_must_contribute_to_higher_level);
    				contributesCursor.close();
    				return;
    			}
    		}
    		contributesCursor.close();
    	}
    	
    	if (_op==ADD)
    	{
    		// Add the Goal to the database:
    		long goalID = _goalsDB.addGoal(-1, accountID, title, _archived.isChecked(), contributesToID, 
    			level);
    		if (goalID==-1)
    		{
    			Util.popup(_a, R.string.DbInsertFailed);
    			Util.log("Unable to add goal to DB in EditGoalFragment.java.");
    			return;
    		}
    		
    		// Upload the goal to Toodledo or google:
        	i = new Intent(_a, Synchronizer.class);
        	i.putExtra("command", "sync_item");
        	i.putExtra("item_type",Synchronizer.GOAL);
        	i.putExtra("item_id", goalID);
        	i.putExtra("account_id", accountID);
        	i.putExtra("operation",Synchronizer.ADD);
			Synchronizer.enqueueWork(_a,i);
    	}
    	else
    	{
    		// We cannot archive a Toodledo goal before it syncs:
    		c = _goalsDB.getGoal(_id);
    		if (!c.moveToFirst())
    		{
    			Util.popup(_a, R.string.Item_no_longer_exists);
    			return;
    		}
    		UTLAccount acct = _accountsDB.getAccount(Util.cLong(c, "account_id"));
    		if (acct==null)
    		{
    			Util.popup(_a, R.string.Item_no_longer_exists);
    			return;
    		}
    		if (Util.cLong(c,"td_id")==-1 && acct.sync_service==UTLAccount.SYNC_TOODLEDO &&
    			_archived.isChecked())
    		{
    			c.close();
    			Util.popup(_a, R.string.Must_be_Synchronized);
    			return;
    		}
    		long toodledoID = Util.cLong(c,"td_id");
    		c.close();
    		
    		// Check other goals that contribute to this one.  They must be at a lower level:
    		c = _goalsDB.queryGoals("contributes="+_id, null);
    		c.moveToPosition(-1);
    		while (c.moveToNext())
    		{
    			if (Util.cInt(c, "level")<=level)
    			{
    				Util.longerPopup(_a, _a.getString(R.string.Level_Needs_Adjusted), _a.getString(
    					R.string.Level_Issue_Description)+" "+Util.cString(c, "title"));
    				c.close();
    				return;
    			}
    		}
    		c.close();
    		
    		// Modify the goal in the database:
    		if (!_goalsDB.modifyGoal(_id, toodledoID, accountID, title, _archived.isChecked(), 
    			contributesToID, level))
    		{
    			Util.popup(_a, R.string.DbModifyFailed);
        		Util.log("Cannot modify goal.");
        		return;
    		}
    		
    		// Upload the goal to Toodledo or Google:
    		i = new Intent(_a, Synchronizer.class);
        	i.putExtra("command", "sync_item");
        	i.putExtra("item_type",Synchronizer.GOAL);
        	i.putExtra("item_id", _id);
        	i.putExtra("account_id", accountID);
        	i.putExtra("operation",Synchronizer.MODIFY);
			Synchronizer.enqueueWork(_a,i);

        	// If the goal belongs to a Google account, we need to re-upload
        	// all tasks that use it.  This is done by marking the tasks as modified
        	// and triggering a sync:
        	UTLAccount a = _accountsDB.getAccount(accountID);
        	if (a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE)
        	{
        		// Mark tasks that use the goal as modified:
            	SQLiteDatabase db = Util.db();
            	db.execSQL("update tasks set mod_date="+System.currentTimeMillis()+
            		" where goal_id="+_id);
            	
            	// Trigger a sync:
            	i = new Intent(_a, Synchronizer.class);
        		i.putExtra("command", "full_sync");
        		i.putExtra("send_percent_complete", false);
				Synchronizer.enqueueWork(_a,i);
        	}
    	}
    	
    	refreshAndEnd();
    }
}
