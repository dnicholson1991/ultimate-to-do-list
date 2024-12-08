package com.customsolutions.android.utl;

// This activity is used for picking out one or more items from a list.

// To call this activity, put a Bundle in the intent with the following keys/values:
// selected_item_ids: An array of longs containing items that should be selected by
//     default.  This may be omitted or set to an empty array to have none selected.
// item_ids: A long array of all available item IDs.  These are in the order they should appear in the list.
// item_names: A matching array of strings for each ID.
// allow_no_selection: Boolean.  If true, then the user can select no items.  Default: false
// title: A string containing the title to put at the top.

// The response sent back to the caller includes the following:
// resultCode: either RESULT_CANCELED or RESULT_OK
// Intent object extras:
// selected_item_ids: The actual item IDs selected, as a long array.  This will be a zero
//     length array if nothing is selected.  It is only returned for RESULT_OK.

import java.util.HashSet;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public class ItemPicker extends UtlSaveCancelPopupActivity 
{
    // The default item IDs:
    private HashSet<Long> defaultItemIDs;
    
    // This array holds the item IDs:
    private long[] itemIDs;
    
    // This array holds the item names:
    private String[] itemNames;
    
    // Allow empty selection?
    private boolean allowEmptySelection;
    
    // The ListView used by this ListActivity:
    private ListView listView;
    
    // Reference to myself, used in button callbacks:
    private ItemPicker myself;
    
    // Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Util.log("Launched Generic Item Picker");
        setContentView(R.layout.item_picker);
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_multi_select_large));
        
        Bundle extras = getIntent().getExtras();
        if (extras==null)
        {
            Util.log("ERROR: No extras passed in.");
            finish();
        }
        
        // Put the default selected items (if any) into a HashSet:
        defaultItemIDs = new HashSet<Long>();
        if (extras.containsKey("selected_item_ids"))
        {
            long[] idArray = extras.getLongArray("selected_item_ids");
            for (int i=0; i<idArray.length; i++)
            {
                defaultItemIDs.add(idArray[i]);
            }
        }
        
        // Get an array of item names, to plug into the ListAdapter.  Also set up 
        // mappings of position to name and ID:
        if (extras.containsKey("item_ids"))
        {
            itemIDs = extras.getLongArray("item_ids");
        }
        else
        {
            Util.log("ERROR: No item IDs passed in.");
            finish();
        }
        if (extras.containsKey("item_names"))
        {
            itemNames = extras.getStringArray("item_names");            
        }
        else
        {
            Util.log("ERROR: No item names passed in.");
            finish();
        }
        
        if (extras.containsKey("allow_no_selection") && extras.getBoolean("allow_no_selection")
            ==true)
        {
            allowEmptySelection = true;
        }
        else
        {
            allowEmptySelection = false;
        }
        
        if (extras.containsKey("title"))
        {
            // Set the title for this screen:
        	getSupportActionBar().setTitle(extras.getString("title"));            
        }
        
        myself = this;        
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
    	
        // Set up the ListAdapter:
        setListAdapter(new ArrayAdapter<String>(this,R.layout.item_picker_row, itemNames));
        listView = this.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        
        // Check the items that should be checked by default:
        for (int i=0; i<itemIDs.length; i++)
        {
            long id = itemIDs[i];
            if (defaultItemIDs.contains(id))
            {
                listView.setItemChecked(i,true);
            }
        }        
    }
    
    // Overrides the default size function, taking into account the small size of this popup:
    @Override
    protected Point getPopupSize()
    {
    	Point size = super.getPopupSize();
    	int screenSize = getResources().getConfiguration().screenLayout & Configuration.
			SCREENLAYOUT_SIZE_MASK;
    	if (getOrientation()==ORIENTATION_LANDSCAPE)
    	{
    		if (screenSize==Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// Smaller tablet.
				size.x = _screenWidth*6/10;
				size.y = _screenHeight;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*4/10;
				size.y = _screenHeight*8/10;
				return size;
			}
    	}
    	
    	return size;
    }

    @Override
    public void handleSave()
    {
    	// Get the selected item IDs:
        HashSet<Long> selectedItemIDs = new HashSet<Long>();
        for (int j=0; j<listView.getCount(); j++)
        {
            if (listView.isItemChecked(j))
            {
                selectedItemIDs.add(itemIDs[j]);
            }
        }
        
        if (selectedItemIDs.size()==0 && !allowEmptySelection)
        {
            // The user has selected nothing and we're not allowing nothing:
            Util.popup(myself, R.string.Please_select_at_least_one);
            return;
        }
        
        // Return the IDs back to the caller:
        Intent i = new Intent();
        Bundle b = new Bundle();
        b.putLongArray("selected_item_ids", Util.iteratorToLongArray(
            selectedItemIDs.iterator(), selectedItemIDs.size()));
        i.putExtras(b);
        setResult(RESULT_OK,i);
        Util.log("Leaving item picker with "+selectedItemIDs.size()+" items chosen.");
        finish();
    }
}
