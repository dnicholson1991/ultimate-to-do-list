package com.customsolutions.android.utl;

import java.util.HashSet;
import java.util.Iterator;

import org.droidparts.widget.ClearableEditText;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Fragment for adding or editing a context.  Pass in a Bundle with key "mode" and value of ADD or
 * EDIT.  When editing, pass in a key of "id", containing the database ID.
 * @author Nicholson
 *
 */
public class EditContextFragment extends GenericEditorFragment
{
    // Codes to track responses to activities:
    public static final int GET_ACCOUNTS = 1;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditContextFragment";
    
    // Keeps track of the IDs of selected accounts:
    HashSet<Long> _selectedAccountIDs;
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private TextView _accounts;
    
    // The number accounts affects the display:
    private int _numAccounts;
    
    // Quick access to database tables:
    private AccountsDbAdapter _accountsDB;
    private ContextsDbAdapter _contextsDB;
    
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_context, container, false);
    }
    
    /** Called when Activity is started: */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening Context Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _accountsDB = new AccountsDbAdapter();
        _contextsDB = new ContextsDbAdapter();
        _selectedAccountIDs = new HashSet<Long>();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_context_title);
        _accounts = (TextView)_rootView.findViewById(R.id.edit_context_account_value);
        
        if (_op==EDIT)
        	setTitle(R.string.Edit_Context);
        else
        	setTitle(R.string.Add_a_Context);

        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
    	_numAccounts = c1.getCount();
    	c1.close();
        
    	// Hide the accounts selector if necessary:
    	if (_numAccounts==1 || _op==EDIT)
    	{
    		_rootView.findViewById(R.id.edit_context_account_section).setVisibility(View.GONE);
    	}
    	
    	// Set values for the views:
    	if (_op==ADD)
    	{
    		if (_numAccounts>1)
    		{
    			// Use the default account for task creation:
    			if (_settings.contains(PrefNames.DEFAULT_ACCOUNT))
    			{
    				UTLAccount acc = _accountsDB.getAccount(_settings.getLong(
                    	PrefNames.DEFAULT_ACCOUNT, 0));
    				if (acc != null)
                    {
                        _accounts.setText(acc.name);
                        _selectedAccountIDs.add(acc._id);
                    }             
                    else
                    {
                    	// Get the first account:
                        Cursor c = _accountsDB.getAllAccounts();
                        if (c.moveToFirst())
                        {
                            acc = _accountsDB.getUTLAccount(c);
                            _accounts.setText(acc.name);
                            _selectedAccountIDs.add(acc._id);
                        }
                        c.close();
                    }
    			}
    			else
    			{
    				// No default account specified.  Get the first account.
                    Cursor c = _accountsDB.getAllAccounts();
                    if (c.moveToFirst())
                    {
                        UTLAccount acc = _accountsDB.getUTLAccount(c);
                        _accounts.setText(acc.name);
                        _selectedAccountIDs.add(acc._id);
                    }
                    c.close();
    			}
    		}
    		else
    		{
    			// We have one account only, so it is selected:
                Cursor c = _accountsDB.getAllAccounts();
                if (c.moveToFirst())
                {
                	UTLAccount acc = _accountsDB.getUTLAccount(c);
                	_selectedAccountIDs.add(acc._id);
                }
                c.close();
    		}
    	}
    	else
    	{
    		// In modify mode, we just set the title:
    		Cursor c = _contextsDB.getContext(_id);
    		if (c.moveToFirst())
    		{
    			_title.setText(Util.cString(c, "title"));
        		_selectedAccountIDs.add(Util.cLong(c, "account_id"));
    		}
    		c.close();
    	}
    	
    	// Handler for the Accounts button:
    	if (_op==ADD && _numAccounts>1)
    	{
	    	_rootView.findViewById(R.id.edit_context_account_container).setOnClickListener(new 
	    		View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					flashField(R.id.edit_context_account_container);
					
                    // We will start up the list item picker.  Begin by getting the current
                    // selected accounts:
                    Intent i = new Intent(_a, ItemPicker.class);
                    Iterator<Long> it = _selectedAccountIDs.iterator();
                    i.putExtra("selected_item_ids", Util.iteratorToLongArray(it, _selectedAccountIDs.
                    	size()));
                    
                    // Get an array of all account IDs and names, to put into the chooser:
                    Cursor c = (new AccountsDbAdapter()).getAllAccounts();
                    i.putExtra("item_ids", Util.cursorToLongArray(c, "_id"));
                    i.putExtra("item_names", Util.cursorToStringArray(c, "name"));
                    
                    // The title for the item selector activity:
                    i.putExtra("title",Util.getString(R.string.Select_Accounts));
                    startActivityForResult(i,GET_ACCOUNTS);
				}
			});
    	}
    }
    
    /** Handlers for Activity results: */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        super.onActivityResult(requestCode, resultCode, intent);
        
        // Get extras in the response, if any:
        Bundle extras = new Bundle();
        if (intent != null)
        {
            extras = intent.getExtras();
        }
        
        if (requestCode==GET_ACCOUNTS && resultCode==Activity.RESULT_OK && extras.containsKey(
        	"selected_item_ids"))
        {
        	// Get the account IDs from the response and update the button text:
            long[] accountIDs = extras.getLongArray("selected_item_ids");
            if (accountIDs.length==0)
            {
                // This should not happen.
                Util.log("ERROR: item picker returned an empty array");
            }
            else
            {
                UTLAccount a = _accountsDB.getAccount(accountIDs[0]);
                String buttonText = "";
                if (a==null)
                {
                    Util.log("ERROR: Bad account with ID of "+accountIDs[0]+
                        " passed back.");
                    return;
                }
                else
                {
                    buttonText = a.name;
                }
                for (int i=1; i<accountIDs.length; i++)
                {
                    a = _accountsDB.getAccount(accountIDs[i]);
                    if (a!=null)
                    {
                        buttonText += ","+a.name;
                    }
                }
                _accounts.setText(buttonText);
                
                // Store the selected account IDs:
                _selectedAccountIDs.clear();
                for (int i=0; i<accountIDs.length; i++)
                {
                    _selectedAccountIDs.add(accountIDs[i]);
                }
            }
        }
    }
    
    /** Check for valid values, then save and exit if possible */
    @Override
    protected void handleSave()
    {
    	// Make sure there is a title:
    	if (_title.getText().toString().length()==0)
    	{
    		Util.popup(_a,R.string.Please_enter_a_name);
    		return;
    	}
    	
    	// Make sure the title is not too long:
    	if (_title.getText().toString().length()>Util.MAX_CONTEXT_TITLE_LENGTH)
    	{
    		Util.popup(_a, R.string.Title_is_too_long);
    		return;
    	}
    	
    	// Make sure at least one account is selected:
    	if (_selectedAccountIDs.size()==0)
    	{
    		Util.popup(_a, R.string.Please_select_an_account);
    		return;
    	}
    	
		// Make sure the context title does not match any context in any of the selected
    	// accounts:
    	String where = "lower(title)='"+Util.makeSafeForDatabase(_title.getText().toString().
    		toLowerCase())+"' and account_id in (";
    	Iterator<Long> it = _selectedAccountIDs.iterator();
    	int j = 0;
    	while (it.hasNext())
    	{
    		long accountID = it.next();    		
    		if (j>0)
    			where+=","+accountID;
    		else
    			where += accountID;
   			j++;
    	}
    	where += ")";
    	Cursor c = _contextsDB.queryContexts(where, null);
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
    	
    	if (_op==ADD)
    	{
    		// Go through the selected accounts and add the context:
        	it = _selectedAccountIDs.iterator();
        	while (it.hasNext())
        	{
        		long accountID = it.next();
        		long contextID = _contextsDB.addContext(-1, accountID, _title.getText().toString());
        		if (contextID==-1)
        		{
        			Util.popup(_a, R.string.DbInsertFailed);
        			Util.log("Unable to add context to DB in EditContextFragment.java.");
        			return;
        		}
        		
        		// Upload the context to Toodledo or Google:
            	Intent i = new Intent(_a, Synchronizer.class);
            	i.putExtra("command", "sync_item");
            	i.putExtra("item_type",Synchronizer.CONTEXT);
            	i.putExtra("item_id", contextID);
            	i.putExtra("account_id", accountID);
            	i.putExtra("operation",Synchronizer.ADD);
				Synchronizer.enqueueWork(_a,i);
        	}
    	}
    	else
    	{
    		// Rename the context in the database:
    		if (!_contextsDB.renameContext(_id, _title.getText().toString()))
        	{
        		Util.popup(_a, R.string.DbModifyFailed);
        		Util.log("Cannot modify context name.");
        		return;
        	}
    		
    		// Upload the context to Toodledo or Google:
    		Intent i = new Intent(_a, Synchronizer.class);
        	i.putExtra("command", "sync_item");
        	i.putExtra("item_type",Synchronizer.CONTEXT);
        	i.putExtra("item_id", _id);
        	i.putExtra("account_id", _selectedAccountIDs.iterator().next());
        	i.putExtra("operation",Synchronizer.MODIFY);
			Synchronizer.enqueueWork(_a,i);
        	
        	// If the context belongs to a Google account, we need to re-upload
        	// all tasks that use it.  This is done by marking the tasks as modified
        	// and triggering a sync:
        	UTLAccount a = _accountsDB.getAccount(_selectedAccountIDs.iterator().next());
        	if (a!=null && a.sync_service==UTLAccount.SYNC_GOOGLE)
        	{
        		// Mark tasks that use the context as modified:
            	SQLiteDatabase db = Util.db();
            	db.execSQL("update tasks set mod_date="+System.currentTimeMillis()+
            		" where context_id="+_id);
            	
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
