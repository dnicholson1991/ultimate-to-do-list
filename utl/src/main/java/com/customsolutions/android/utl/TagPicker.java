package com.customsolutions.android.utl;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import android.app.ListActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TableRow;
import android.widget.TextView;

// This activity is used for picking out tags.

// To call this activity, put a Bundle in the intent with the following keys/values:
// selected_tags: Tags that should be selected by default.  This is a string array.
//     This may be omitted to have none selected.

// IMPORTANT: All tags in the selected_tags item must be in the recently used tags list,
// or else the user will not have the option to unselect certain tags.  Call 
// CurrentTagsDbAdapter.addToRecent() prior to calling this activity.

// The response sent back to the caller includes the following:
// resultCode: either RESULT_CANCELED or RESULT_OK
// Intent object extras:
//   selected_tags: The actual tags selected, as a string array.  If resultCode is 
//       RESULT_OK, then this will be an empty array if no tags were selected.

public class TagPicker extends UtlSaveCancelPopupActivity 
{
    private CurrentTagsDbAdapter mDbHelper;
    private Cursor mCursor;
    private TagPicker myself;
    private HashSet<String> SelectedTagNames;
    
    // Called when activity is first created:
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Util.log("Launched Tag Picker");
        setContentView(R.layout.tag_picker);
        mDbHelper = new CurrentTagsDbAdapter();
        
        // Set the title for this screen:
        getSupportActionBar().setTitle(Util.getString(R.string.Select_Tags));
        
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_tags_large));
        
        // Extract the selected tags, if they were provided:
        Bundle extras = getIntent().getExtras();
        SelectedTagNames = new HashSet<String>();
        if (extras!=null)
        {
            if (extras.containsKey("selected_tags"))
            {
                String[] tagArray = extras.getStringArray("selected_tags");
                for (int i=0; i<tagArray.length; i++)
                {
                    SelectedTagNames.add(tagArray[i]);
                }
            }
        }
        myself = this;
        
        // Pressing of the ENTER key:
        EditText editor = (EditText)this.findViewById(R.id.tags_manual_entry);
        editor.setOnKeyListener(new View.OnKeyListener() 
        {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if (event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.
                    KEYCODE_ENTER)
                {
                    handleSave();
                    return true;
                }
                return false;
            }
        });
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
    	super.onPostCreate(savedInstanceState);
        fillData();        
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
				size.x = _screenWidth/2;
				size.y = _screenHeight;
				return size;
			}
			else if (screenSize>Configuration.SCREENLAYOUT_SIZE_LARGE)
			{
				// It must be extra large.
				size.x = _screenWidth*1/3;
				size.y = _screenHeight*9/10;
				return size;
			}
    	}
    	
    	return size;
    }

    // Fill in the data in the list:
    private void fillData()
    {
        // Query the database to get the tags:
        mCursor = mDbHelper.getTags();
        startManagingCursor(mCursor);
        
        // This array lists the columns we're interested in:
        String[] from = new String[]{"name"};
        
        // The IDs of views that are affected by the columns:
        int[] to = new int[]{R.id.tag_picker_row};
        
        // Initialize the simple cursor adapter:
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.tag_picker_row,
            mCursor, from, to);
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder()
        {
            private boolean viewsUpdated = false;
            
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex)
            {
                String name = cursor.getString(columnIndex);
                TableRow row = (TableRow)view;
                TextView nameView = (TextView)row.getChildAt(0);
                CheckBox cb = (CheckBox)row.getChildAt(1);
                nameView.setText(name);
                if (nameView.getContentDescription()==null || nameView.
                	getContentDescription().length()==0)
                {
                    nameView.setContentDescription("x");
                }
                if (SelectedTagNames.contains(nameView.getText().toString()))
                {
                    cb.setChecked(true);
                }
                else
                {
                    cb.setChecked(false);
                }
                
                row.setOnClickListener(new View.OnClickListener() 
                {                    
                    @Override
                    public void onClick(View v) 
                    {
                        TableRow row = (TableRow)v;
                        TextView nameView = (TextView)row.getChildAt(0);
                        CheckBox cb = (CheckBox)row.getChildAt(1);
                        String name = nameView.getText().toString();
                        if (cb.isChecked())
                        {
                            cb.setChecked(false);
                            SelectedTagNames.remove(name);
                        }
                        else
                        {
                            cb.setChecked(true);
                            SelectedTagNames.add(name);
                        }
                    }
                });
                
                if (!viewsUpdated)
                {
                    // Since we're actually displaying some tags, we need to adjust some
                    // of the on-screen text.
                    TextView tv = (TextView)myself.findViewById(R.id.tags_enter_statement);
                    tv.setText(Util.getString(R.string.Enter_Other_Tags));
                    viewsUpdated = true;
                }
                return true;
            }
        });
        
        setListAdapter(adapter);
    }
    
    // Save the tags and return to the calling activity:
    @Override
    public void handleSave()
    {
        TreeSet<String> tags = new TreeSet<String>();
        
        // Get the tags selected via checkboxes:
        Iterator<String> it = SelectedTagNames.iterator();
        while (it.hasNext())
        {
            tags.add(it.next());
        }
        
        // Add in the tags typed in:
        EditText edit = (EditText)myself.findViewById(R.id.tags_manual_entry);
        if (edit.getText().length()>0)
        {
            String[] tagArray = edit.getText().toString().split(",");
            for (int i=0; i<tagArray.length; i++)
            {
                tagArray[i] = tagArray[i].trim();
                tags.add(tagArray[i]);
            }
        }
        
        // Go back to the caller:
        Intent i = new Intent();
        String[] tagNameArray;
        if (tags.size()>0)
        {
            Iterator<String> t = tags.iterator();
            tagNameArray = new String[tags.size()];
            int j = 0;
            while (t.hasNext())
            {
                tagNameArray[j] = t.next();
                j++;
            }
        }
        else
        {
            // Return a blank array:
            tagNameArray = new String[0];
        }
        Bundle b = new Bundle();
        b.putStringArray("selected_tags",tagNameArray);
        i.putExtras(b);
        setResult(RESULT_OK,i);
        finish();        
    }
}
