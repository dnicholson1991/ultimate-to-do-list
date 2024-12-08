package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Iterator;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;

/** <pre>This activity is used for updating the sort order.
A Bundle is passed in with the following inputs:
view_id: The ID of the view we are editing

This should be started with a startActivityForResult().  A resultCode of RESULT_CANCELED
or RESULT_OK is returned.  No Bundle is passed back (because it is not needed).
This activity updates the View in the database.</pre>
*/
public class SortOrder extends UtlSaveCancelPopupActivity
{
	private SharedPreferences _settings;
	
	// Views we need to keep track of:
	private Spinner _field1;
	private Spinner _field2;
	private Spinner _field3;
	private Spinner _field1Rev;  // Reverse sort option spinners...
	private Spinner _field2Rev;
	private Spinner _field3Rev;
	
	// Handle into the database:
	private ViewsDbAdapter _viewsDB;
	
	// The view ID passed in:
	private long _viewID;
	
	// Arrays for holding fields names and human-readable versions of fields:
	ArrayList<String> _dbFields;
	ArrayList<String> _dbFields2;
	ArrayList<String> _readableFields;
	ArrayList<String> _readableFields2;
	
	// An array containing the reverse sort options.  Each element is 0 for normal or 1 for
	// reverse:
	private int[] _reverseOptions;
	
	// Keep track of indexes of selected fields:
	private int _field1Index;
	private int _field2Index;
	private int _field3Index;

	private PurchaseManager _pm;

	@Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        Util.log("Start the Sort Order chooser.");
        
        // Link this activity with a layout resource:
        setContentView(R.layout.sort_order);
        
        // Pull out the data in the Intent, and make sure the required view ID is passed in:
        Bundle extras = this.getIntent().getExtras();
        if (!extras.containsKey("view_id"))
        {
        	Util.log("Missing view_id in SortOrder.OnCreate().");
        	finish();
        	return;
        }
        _viewID = extras.getLong("view_id");
        
        // Set the title and icon at the top of the screen:
        getSupportActionBar().setTitle(R.string.Select_a_Sort_Order);
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_sort));

		_pm = new PurchaseManager(this);
		_pm.link();

		initBannerAd();

        // We need to determine if any Google accounts exist.  This affects which fields
        // are visible:
        boolean hasGoogleAccount = false;
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        Cursor c = accountsDB.getAllAccounts();
        c.moveToPosition(-1);
        while (c.moveToNext())
        {
        	UTLAccount a = accountsDB.getUTLAccount(c);
        	if (a.sync_service==UTLAccount.SYNC_GOOGLE)
        		hasGoogleAccount = true;
        }
        
        // Initialize the arrays containing the lists of fields, based on what is 
        // enabled and disabled.
        _settings = this.getSharedPreferences(Util.PREFS_NAME, 0);
        _dbFields = new ArrayList<String>();
        _readableFields = new ArrayList<String>();
        _dbFields.add("null");
        _readableFields.add(Util.getString(R.string.None));
        
        _dbFields.add("account");
        _readableFields.add(Util.getString(R.string.Account));
        
        if (_settings.getBoolean(PrefNames.TIMER_ENABLED, true))
        {
        	_dbFields.add("tasks.timer");
            _readableFields.add(Util.getString(R.string.Actual_Length));
        }
        
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        {
        	_dbFields.add("assignor_name");
        	_readableFields.add(Util.getString(R.string.Assignor));
        }
        
        _dbFields.add("tasks.completed");
        _readableFields.add(Util.getString(R.string.Completed_Status));
        
        _dbFields.add("tasks.completion_date");
        _readableFields.add(Util.getString(R.string.CompletionDate));
        
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
        	_dbFields.add("context");
            _readableFields.add(Util.getString(R.string.Context));
        }
        
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
        	_dbFields.add("tasks.due_date");
            _readableFields.add(Util.getString(R.string.Due_Date));
        }
        
        if (_settings.getBoolean(PrefNames.LENGTH_ENABLED, true))
        {
        	_dbFields.add("tasks.length");
            _readableFields.add(Util.getString(R.string.Expected_Length));
        }
        
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
        	_dbFields.add("folder");
            _readableFields.add(Util.getString(R.string.Folder));
        }
        
        if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
        	_dbFields.add("goal");
            _readableFields.add(Util.getString(R.string.Goal));
        }
        
        if (hasGoogleAccount)
        {
        	_dbFields.add("tasks.position");
            _readableFields.add(Util.getString(R.string.Google_Task_Order));
        }
        
        _dbFields.add("importance");
        _readableFields.add(Util.getString(R.string.Importance));
        
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        {
        	_dbFields.add("tasks.is_joint");
        	_readableFields.add(Util.getString(R.string.Is_Joint_));
        }

        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        {
        	_dbFields.add("location");
            _readableFields.add(Util.getString(R.string.Location));
        }

		_dbFields.add("tasks.sort_order");
		_readableFields.add(getString(R.string.manual_sort_order));

        _dbFields.add("tasks.mod_date");
        _readableFields.add(Util.getString(R.string.Mod_Date));
        
        _dbFields.add("tasks.note");
        _readableFields.add(Util.getString(R.string.Note));
        
        if (_settings.getBoolean(PrefNames.COLLABORATORS_ENABLED, true))
        {
        	_dbFields.add("owner_name");
        	_readableFields.add(Util.getString(R.string.Owner));
        }

        if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED, true))
        {
        	_dbFields.add("tasks.priority");
            _readableFields.add(Util.getString(R.string.Priority));
        }
        
        if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true))
        {
        	_dbFields.add("tasks.reminder");
            _readableFields.add(Util.getString(R.string.Reminder));
        }
        
        if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
        {
        	_dbFields.add("tasks.star");
            _readableFields.add(Util.getString(R.string.Star));
        }
        
        if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
        {
        	_dbFields.add("tasks.start_date");
            _readableFields.add(Util.getString(R.string.Start_Date));
        }
        
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
        	_dbFields.add("tasks.status");
            _readableFields.add(Util.getString(R.string.Status));
        }
        
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
        	_dbFields.add("tag_name");
            _readableFields.add(Util.getString(R.string.Tags));
        }
        
        _dbFields.add("tasks.title");
        _readableFields.add(Util.getString(R.string.Title));

		// All spinners need a blank line added. Due to Android bugs, tapping on the last item may
		// not be detected.
		_dbFields.add("tasks.title");
		_readableFields.add(" ");

		// A second set of fields is used for the 2nd and 3rd sort orders.  This is a subset of
		// the fields for the first sort order.
		_dbFields2 = new ArrayList<String>();
		_readableFields2 = new ArrayList<String>();
		Iterator<String> it = _dbFields.iterator();
		while (it.hasNext())
		{
			String field = it.next();
			if (!field.equals("tasks.sort_order"))
				_dbFields2.add(field);
		}
		it = _readableFields.iterator();
		while (it.hasNext())
		{
			String field = it.next();
			if (!field.equals(getString(R.string.manual_sort_order)))
				_readableFields2.add(field);
		}

        // Link local variables to layout items:
        _field1 = (Spinner)this.findViewById(R.id.sort_order_spinner_1);
        _field2 = (Spinner)this.findViewById(R.id.sort_order_spinner_2);
        _field3 = (Spinner)this.findViewById(R.id.sort_order_spinner_3);
        _field1Rev = (Spinner)findViewById(R.id.sort_order_spinner_1_reverse);
        _field2Rev = (Spinner)findViewById(R.id.sort_order_spinner_2_reverse);
        _field3Rev = (Spinner)findViewById(R.id.sort_order_spinner_3_reverse);
        
        // Initialize the choices in the Spinners:
        initSpinner(_field1,Util.iteratorToStringArray(_readableFields.iterator(), _readableFields.
			size()));
        initSpinner(_field2,Util.iteratorToStringArray(_readableFields2.iterator(), _readableFields2.
			size()));
        initSpinner(_field3,Util.iteratorToStringArray(_readableFields2.iterator(), _readableFields2.
			size()));
        
        // Read the view passed in, and initialize the spinner selections:
        _viewsDB = new ViewsDbAdapter();
        c = _viewsDB.getView(_viewID);
        if (!c.moveToFirst())
        {
        	Util.log("Invalid view ID "+_viewID+" passed to SortOrder.java.");
        	finish();
        	return;
        }
        String[] sortFields = Util.cString(c, "sort_string").split(",");  
    	_field1.setSelection(_dbFields.indexOf(sortFields[0]));
    	_field2.setSelection(0);
    	_field3.setSelection(0);
        if (sortFields.length>=2)
        {
        	_field2.setSelection(_dbFields2.indexOf(sortFields[1]));
        }
        if (sortFields.length==3)
        {
        	_field3.setSelection(_dbFields2.indexOf(sortFields[2]));
        }
        
        // Set a title for the spinner menus:
        _field1.setPromptId(R.string.Choose_a_sort_field);
        _field2.setPromptId(R.string.Choose_a_sort_field);
        _field3.setPromptId(R.string.Choose_a_sort_field);

        // Record the indexes of the selected fields:
        _field1Index = _field1.getSelectedItemPosition();
        _field2Index = _field2.getSelectedItemPosition();
        _field3Index = _field3.getSelectedItemPosition();
        
        // Get the reverse options:
        String sortOrderString = Util.cString(c, "sort_order_string");
        if (sortOrderString!=null && sortOrderString.length()>0)
        {
        	String[] splitString = sortOrderString.split(",");
        	_reverseOptions = new int[] {
        		Integer.parseInt(splitString[0]),
        		Integer.parseInt(splitString[1]),
        		Integer.parseInt(splitString[2])
        	};
        	if (_reverseOptions[0]>1) _reverseOptions[0] = 0;
        	if (_reverseOptions[1]>1) _reverseOptions[1] = 0;
        	if (_reverseOptions[2]>1) _reverseOptions[2] = 0;
        }
        else
        {
        	// No sort string.  Assume all zeroes:
        	_reverseOptions = new int[] {0,0,0};
        }
        c.close();

        // Refresh the reverse option spinners:
        refreshReverseOptions(true,true,true);

		if (_field1.getSelectedItemPosition()>=0)
		{
			String dbField = _dbFields.get(_field1.getSelectedItemPosition());
			if (dbField.equals("tasks.sort_order"))
			{
				SortOrder.this.findViewById(R.id.sort_order_manual_sort_ui_change).
					setVisibility(View.VISIBLE);
			}
			else
			{
				SortOrder.this.findViewById(R.id.sort_order_manual_sort_ui_change).
					setVisibility(View.GONE);
			}
		}

        // When the user chooses a new field to sort on, the reverse options need to change.
        _field1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			private int _priorSelection;

        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		if (position!=_field1Index)
        		{
        			_reverseOptions[0] = 0;
        			refreshReverseOptions(true,false,false);
					_priorSelection = _field1Index;
        			_field1Index = position;

        			// If the last item (a space) does happen to get selected, then switch to
					// the second to last item.
					if (position==_field1.getCount()-1)
					{
						_field1.setSelection(_field1.getCount()-2);
						return;
					}

					// See if the manual sort help info needs updated:
					String dbField = _dbFields.get(_field1.getSelectedItemPosition());
					if (dbField.equals("tasks.sort_order"))
					{
						// We need to make sure the user has purchased the manual sort add-on.
						// If not, display a popup message.
						if (_pm.isPurchased(PurchaseManager.SKU_MANUAL_SORT))
						{
							SortOrder.this.findViewById(R.id.sort_order_manual_sort_ui_change).
								setVisibility(View.VISIBLE);
						}
						else
						{
							// Revert the Spinner back to the previous selection.
							_field1.setSelection(_priorSelection,false);

							// Display the dialog giving the user options to buy or learn more.
							DialogInterface.OnClickListener dialogClickListener = new
								DialogInterface.OnClickListener()
							{
								@Override
								public void onClick(DialogInterface dialog, int which)
								{
									switch (which)
									{
										case DialogInterface.BUTTON_POSITIVE:
											// "Buy Now" tapped:
											_pm.startPurchase(PurchaseManager.SKU_MANUAL_SORT,null);
											break;
										case DialogInterface.BUTTON_NEUTRAL:
											// "Learn More" tapped.
											Intent i = new Intent(SortOrder.this,StoreItemDetail.
												class);
											i.putExtra("sku",PurchaseManager.SKU_MANUAL_SORT);
											startActivity(i);
											break;
									}
									dialog.cancel();
								}
							};
							AlertDialog.Builder builder = new AlertDialog.Builder(SortOrder.this);
							builder.setMessage(R.string.manual_sort_not_purchased);
							builder.setTitle(R.string.add_on_missing);
							builder.setPositiveButton(getString(R.string.buy)+" ("+_pm.getPrice(
								PurchaseManager.SKU_MANUAL_SORT)+")", dialogClickListener);
							builder.setNegativeButton(R.string.Cancel, dialogClickListener);
							builder.setNeutralButton(R.string.learn_more, dialogClickListener);
							builder.show();
						}
					}
					else
					{
						SortOrder.this.findViewById(R.id.sort_order_manual_sort_ui_change).
							setVisibility(View.GONE);
					}
        		}
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		});
        _field2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		if (position!=_field2Index)
        		{
        			_reverseOptions[1] = 0;
        			refreshReverseOptions(false,true,false);
        			_field2Index = position;

					// If the last item (a space) does happen to get selected, then switch to
					// the second to last item.
					if (position==_field2.getCount()-1)
					{
						_field2.setSelection(_field2.getCount()-2);
						return;
					}
        		}
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
            }
		});
        _field3.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
        	public void onItemSelected(AdapterView<?>  parent, View  view, 
                int position, long id)
            {
        		if (position!=_field3Index)
        		{
        			_reverseOptions[2] = 0;
        			refreshReverseOptions(false,false,true);
        			_field3Index = position;

					// If the last item (a space) does happen to get selected, then switch to
					// the second to last item.
					if (position==_field3.getCount()-1)
					{
						_field3.setSelection(_field3.getCount()-2);
						return;
					}
        		}
            }
        	
        	public void onNothingSelected(AdapterView<?>  parent)
            {
                // Nothing to do.
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
    			size.x = _screenWidth/2;
				size.y = _screenHeight*9/10;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				size.y = _screenHeight*7/10;
				return size;
			}
    	}
    	else
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
    			size.x = _screenWidth*9/10;
				size.y = _screenHeight/2;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*8/10;
				size.y = _screenHeight/3;
				return size;
			}
    	}
    	
    	return size;
    }

    // Refresh the reverse sort options.  Inputs are 3 boolean variables specifying which
	// ones to refresh.
	private void refreshReverseOptions(boolean refreshField1, boolean refreshField2,
		boolean refreshField3)
	{
		// Field 1:
		if (refreshField1)
		{
			if (_field1.getSelectedItemPosition()<0)
			{
				// This seems to be happening to some users for no apparent reason.
				_field1Rev.setVisibility(View.GONE);
				Util.log("Field 1 spinner has an index of "+_field1.getSelectedItemPosition()+
					" for no apparent reason.");
			}
			else
			{
				String dbField = _dbFields.get(_field1.getSelectedItemPosition());
				if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
				{
					// The database field has reverse sort options.  Display them and set the
					// spinner to the currently selected one.
					initSpinner(_field1Rev,Util.sortOrderMap.get(dbField));
					_field1Rev.setVisibility(View.VISIBLE);
					_field1Rev.setSelection(_reverseOptions[0]);
				}
				else
				{
					// This database field does not have reverse sort options.  Hide the reverse
					// options spinner.
					_field1Rev.setVisibility(View.GONE);
				}
			}
		}

		// Field 2:
		if (refreshField2)
		{
			if (_field2.getSelectedItemPosition()<0)
			{
				// This seems to be happening to some users for no apparent reason.
				_field2Rev.setVisibility(View.GONE);
				Util.log("Field 2 spinner has an index of "+_field2.getSelectedItemPosition()+
					" for no apparent reason.");
			}
			else
			{
				String dbField = _dbFields2.get(_field2.getSelectedItemPosition());
				if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
				{
					// The database field has reverse sort options.  Display them and set the
					// spinner to the currently selected one.
					initSpinner(_field2Rev,Util.sortOrderMap.get(dbField));
					_field2Rev.setVisibility(View.VISIBLE);
					_field2Rev.setSelection(_reverseOptions[1]);
				}
				else
				{
					// This database field does not have reverse sort options.  Hide the reverse
					// options spinner.
					_field2Rev.setVisibility(View.GONE);
				}
			}
		}
		
		// Field 3:
		if (refreshField3)
		{
			if (_field3.getSelectedItemPosition()<0)
			{
				// This seems to be happening to some users for no apparent reason.
				_field3Rev.setVisibility(View.GONE);
				Util.log("Field 3 spinner has an index of "+_field3.getSelectedItemPosition()+
					" for no apparent reason.");
			}
			else
			{
				String dbField = _dbFields2.get(_field3.getSelectedItemPosition());
				if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
				{
					// The database field has reverse sort options.  Display them and set the
					// spinner to the currently selected one.
					initSpinner(_field3Rev,Util.sortOrderMap.get(dbField));
					_field3Rev.setVisibility(View.VISIBLE);
					_field3Rev.setSelection(_reverseOptions[2]);
				}
				else
				{
					// This database field does not have reverse sort options.  Hide the reverse
					// options spinner.
					_field3Rev.setVisibility(View.GONE);
				}
			}
		}
	}

	@Override
	public void handleSave()
	{
		// Verify that the first spinner is not set to "none":
		if (_field1.getSelectedItemPosition()==0)
		{
			Util.popup(SortOrder.this,R.string.first_item_cannot_be_empty);
			return;
		}
		
		// Create a string for the database:
		String sortString = _dbFields.get(_field1.getSelectedItemPosition());
		if (_field2.getSelectedItemPosition()>0)
		{
			sortString += ","+_dbFields2.get(_field2.getSelectedItemPosition());
		}
		if (_field3.getSelectedItemPosition()>0)
		{
			sortString += ","+_dbFields2.get(_field3.getSelectedItemPosition());
		}
		
		// Update the view in the database:
		if (!_viewsDB.changeSortOrder(_viewID, sortString))
		{
			Util.popup(SortOrder.this, R.string.DbModifyFailed);
			Util.log("Could not update sort order in database.");
			return;
		}

        // Record usage of Toodledo's importance field, if needed.
        if (_dbFields.get(_field1.getSelectedItemPosition()).equals("importance") ||
            _dbFields2.get(_field2.getSelectedItemPosition()).equals("importance") ||
            _dbFields2.get(_field3.getSelectedItemPosition()).equals("importance"))
        {
            FeatureUsage featureUsage = new FeatureUsage(this);
            featureUsage.record(FeatureUsage.TOODLEDO_IMPORTANCE);
        }

		// Get and store the reverse options:
		
		// Field 1:
		String dbField = _dbFields.get(_field1.getSelectedItemPosition());
		String newSortOrderString = "";
		if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
		{
			newSortOrderString += Integer.valueOf(_field1Rev.getSelectedItemPosition()).
				toString()+",";
		}
		else
		{
			newSortOrderString += "0,";
		}
		
		// Field 2:
		dbField = _dbFields2.get(_field2.getSelectedItemPosition());
		if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
		{
			newSortOrderString += Integer.valueOf(_field2Rev.getSelectedItemPosition()).
				toString()+",";
		}
		else
		{
			newSortOrderString += "0,";
		}
		
		// Field 3:
		dbField = _dbFields2.get(_field3.getSelectedItemPosition());
		if (Util.sortOrderMap!=null && Util.sortOrderMap.containsKey(dbField))
		{
			newSortOrderString += Integer.valueOf(_field3Rev.getSelectedItemPosition()).
				toString();
		}
		else
		{
			newSortOrderString += "0";
		}
		
		// Update the view in the database:
		if (!_viewsDB.changeReverseSortOptions(_viewID, newSortOrderString))
		{
			Util.popup(SortOrder.this, R.string.DbModifyFailed);
			Util.log("Could not update sort order reverse option in database.");
			return;
		}
		
		setResult(RESULT_OK);
		finish();
	}

	@Override
	public void onDestroy()
	{
		if (_pm!=null)
			_pm.unlinkFromBillingService();
		super.onDestroy();
	}
}
