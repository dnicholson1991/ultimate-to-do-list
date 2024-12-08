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
import android.widget.CheckBox;
import android.widget.TextView;

/** Fragment for adding or editing a folder.  Pass in a Bundle with key "mode" and value of ADD or
 * EDIT.  When editing, pass in a key of "id", containing the database ID.
 * @author Nicholson
 *
 */
public class EditFolderFragment extends GenericEditorFragment
{
	// Codes to track responses to activities:
    public static final int GET_ACCOUNTS = 1;
    
    // Identifies this type of fragment:
    public static final String FRAG_TAG = "EditFolderFragment";
    
    // Keeps track of the IDs of selected accounts:
    HashSet<Long> _selectedAccountIDs;
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private TextView _accounts;
    private CheckBox _archived;
    private CheckBox _private;
    
    // The number accounts affects the display:
    private int _numAccounts;
    
    // Quick access to database tables:
    private AccountsDbAdapter _accountsDB;
    private FoldersDbAdapter _foldersDB;
    
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_folder, container, false);
    }
    
    /** Called when Activity is started: */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening Folder Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _accountsDB = new AccountsDbAdapter();
        _foldersDB = new FoldersDbAdapter();
        _selectedAccountIDs = new HashSet<Long>();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_folder_title);
        _accounts = (TextView)_rootView.findViewById(R.id.edit_folder_account_value);
        _archived = (CheckBox)_rootView.findViewById(R.id.edit_folder_archived_checkbox);
        _private = (CheckBox)_rootView.findViewById(R.id.edit_folder_private_checkbox);
        
        if (_op==EDIT)
        	setTitle(R.string.Edit_Folder);
        else
        	setTitle(R.string.Add_a_Folder);
        
        // We need to know how many accounts the user has, since this affects the display:
        Cursor c1 = (new AccountsDbAdapter()).getAllAccounts();
    	_numAccounts = c1.getCount();
    	c1.close();
        
    	// Hide the accounts selector if necessary:
    	if (_numAccounts==1 || _op==EDIT)
    	{
    		_rootView.findViewById(R.id.edit_folder_account_section).setVisibility(View.GONE);
    	}
    	
    	// Hide the private setting if collaboration is not in use:
    	if (!_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
    	{
    		_rootView.findViewById(R.id.edit_folder_private_container).setVisibility(View.GONE);
    	}
    	
    	// Set values for the views:
    	if (_op==ADD)
    	{
    		// Private and archive are off by default:
    		_archived.setChecked(false);
    		_private.setChecked(false);
    		
    		// Toodledo won't let us add an archived folder.  Given that users will virtually never
    		// want to do this, we will just disable adding an archived folder globally.
    		_rootView.findViewById(R.id.edit_folder_archived_container).setVisibility(View.GONE);
    		
    		// Account:
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
    		// In modify mode, get the values from the database:
    		Cursor c = _foldersDB.getFolder(_id);
    		if (c.moveToFirst())
    		{
    			_title.setText(Util.cString(c, "title"));
        		_selectedAccountIDs.add(Util.cLong(c, "account_id"));
        		_archived.setChecked(Util.cInt(c, "archived")==1 ? true : false);
        		_private.setChecked(Util.cInt(c, "is_private")==1 ? true : false);
    		}
    		c.close();
    	}
    	
    	// Among the options views (archived and private), the bottom view should not have a line at the 
    	// bottom:
    	View archivedContainer = _rootView.findViewById(R.id.edit_folder_archived_container);
    	View privateContainer = _rootView.findViewById(R.id.edit_folder_private_container);
    	if (privateContainer.getVisibility()!=View.VISIBLE && archivedContainer.getVisibility()==
    		View.VISIBLE)
    	{
    		archivedContainer.setBackgroundResource(Util.resourceIdFromAttr(_a,android.R.attr.
				selectableItemBackground));
    	}
    	
    	// Handler for the Accounts button:
    	if (_op==ADD && _numAccounts>1)
    	{
	    	_rootView.findViewById(R.id.edit_folder_account_container).setOnClickListener(new 
	    		View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					flashField(R.id.edit_folder_account_container);
					
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
    	
    	// Handler for the archived row:
    	_rootView.findViewById(R.id.edit_folder_archived_container).setOnClickListener(new 
    		View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_folder_archived_container);
				_archived.toggle();
			}
		});
    	
    	// Handler for the private row:
    	_rootView.findViewById(R.id.edit_folder_private_container).setOnClickListener(new 
    		View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_folder_private_container);
				_private.toggle();
			}
		});
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

    /** Check for valid values, when save and exit if possible */
    @Override
    protected void handleSave()
    {
    	// Make sure there is a title:
    	if (_title.getText().toString().length()==0)
    	{
    		Util.popup(_a,R.string.Please_enter_a_name);
    		return;
    	}
    	String title = _title.getText().toString();
    	
    	// Make sure the title is not too long:
    	if (title.length()>Util.MAX_FOLDER_TITLE_LENGTH)
    	{
    		Util.popup(_a,R.string.Name_too_long);
    		return;
    	}
    	
    	// Make sure at least one account is selected:
    	if (_selectedAccountIDs.size()==0)
    	{
    		Util.popup(_a, R.string.Please_select_an_account);
    		return;
    	}
    	
		// Make sure the folder title does not match any folder in any of the selected
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
    	Cursor c = _foldersDB.queryFolders(where, null);
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
    		// Go through the selected accounts and add the folder:
        	it = _selectedAccountIDs.iterator();
        	while (it.hasNext())
        	{
        		long accountID = it.next();
        		long folderID = _foldersDB.addFolder(-1, accountID, _title.getText().toString(),
        			_archived.isChecked(), _private.isChecked());
        		if (folderID==-1)
        		{
        			Util.popup(_a, R.string.DbInsertFailed);
        			Util.log("Unable to add folder to DB in EditFolderFragment.java.");
        			return;
        		}
        		
        		// Upload the folder to Toodledo or Google:
            	Intent i = new Intent(_a, Synchronizer.class);
            	i.putExtra("command", "sync_item");
            	i.putExtra("item_type",Synchronizer.FOLDER);
            	i.putExtra("item_id", folderID);
            	i.putExtra("account_id", accountID);
            	i.putExtra("operation",Synchronizer.ADD);
            	Synchronizer.enqueueWork(_a,i);
        	}
    	}
    	else
    	{
    		// We cannot archive a Toodledo folder before it syncs:
    		c = _foldersDB.getFolder(_id);
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
    		c.close();
    		
    		// Rename the folder in the database:
    		if (!_foldersDB.renameFolder(_id, _title.getText().toString()))
        	{
        		Util.popup(_a, R.string.DbModifyFailed);
        		Util.log("Cannot modify folder name.");
        		return;
        	}
    		
    		// Set the archived field:
    		if (!_foldersDB.setArchiveStatus(_id, _archived.isChecked()))
    		{
    			Util.popup(_a, R.string.DbModifyFailed);
        		Util.log("Cannot set folder archived status.");
        		return;
    		}
    		
    		// Set the private field:
    		if (!_foldersDB.setPrivateField(_id, _private.isChecked()))
    		{
    			Util.popup(_a, R.string.DbModifyFailed);
        		Util.log("Cannot set folder private status.");
        		return;
    		}
    		
    		// Upload the folder to Toodledo or Google:
    		Intent i = new Intent(_a, Synchronizer.class);
        	i.putExtra("command", "sync_item");
        	i.putExtra("item_type",Synchronizer.FOLDER);
        	i.putExtra("item_id", _id);
        	i.putExtra("account_id", _selectedAccountIDs.iterator().next());
        	i.putExtra("operation",Synchronizer.MODIFY);
			Synchronizer.enqueueWork(_a,i);
    	}
    	
    	refreshAndEnd();
    }

}
