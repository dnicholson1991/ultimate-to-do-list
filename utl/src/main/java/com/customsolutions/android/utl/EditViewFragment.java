package com.customsolutions.android.utl;

import org.droidparts.widget.ClearableEditText;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

/** Fragment for editing a view.  Pass in a Bundle with a key of "mode" and a value of EDIT.  Also pass
 * in a key of "id" and a database ID for the value.
 * @author Nicholson
 *
 */
public class EditViewFragment extends GenericEditorFragment
{
	// Identifies this type of fragment:
    public static final String FRAG_TAG = "EditViewFragment";
    
    // Quick reference to key views:
    private ClearableEditText _title;
    private CheckBox _showAtTop;
    
    // Quick access to database tables:
    private ViewsDbAdapter _viewsDB;
    
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_view, container, false);
    }
    
    /** Called when Activity is started: */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening View Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _viewsDB = new ViewsDbAdapter();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_view_title);
        _showAtTop = (CheckBox)_rootView.findViewById(R.id.edit_view_show_at_top);
        
        setTitle(R.string.Edit_View);
        
        Cursor c = _viewsDB.getView(_id);
        if (c.moveToFirst())
        {
        	_title.setText(Util.cString(c, "view_name"));
            _showAtTop.setChecked(Util.cInt(c,"on_home_page")==1 ? true : false);
        }
        c.close();
        
        // Handler for the filter button:
        _rootView.findViewById(R.id.edit_view_filter_container).setOnClickListener(new View.
        	OnClickListener()
        {
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_view_filter_container);
				Intent i = new Intent(_a,ViewRulesList.class);
				i.putExtra("view_id", _id);
				_a.startActivity(i);
			}
		});
        
        // Handler for sort:
        _rootView.findViewById(R.id.edit_view_sort_container).setOnClickListener(new View.
        	OnClickListener()
        {
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_view_sort_container);
				Intent i = new Intent(_a,SortOrder.class);
				i.putExtra("view_id", _id);
				_a.startActivity(i);
			}
		});
        
        // Handler for display:
        _rootView.findViewById(R.id.edit_view_display_container).setOnClickListener(new View.
        	OnClickListener()
        {
			@Override
			public void onClick(View v)
			{
				flashField(R.id.edit_view_display_container);
				Intent i = new Intent(_a,EditDisplayOptions.class);
				i.putExtra("view_id", _id);
				_a.startActivity(i);
			}
		});

        // Handler for the option to show at the top:
        _rootView.findViewById(R.id.edit_view_show_at_top_container).setOnClickListener(new
           View.OnClickListener()
           {
               @Override
               public void onClick(View v)
               {
                   flashField(R.id.edit_view_show_at_top_container);
                   _showAtTop.toggle();
               }
           });
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
    	
    	// Rename the view in the database:
    	_viewsDB.renameView(_id, title);

        // Set the option to show at the top:
        _viewsDB.setShowAtTop(_id,_showAtTop.isChecked());

    	refreshAndEnd();
    }
}
