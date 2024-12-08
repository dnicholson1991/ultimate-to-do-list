package com.customsolutions.android.utl;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Displays reference information on the priority and status color-coding. */
public class ColorCodeReference extends UtlPopupActivity
{
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
    	Util.log("Show ColorCodeReference");
    	
    	setContentView(R.layout.color_code_reference);
    	
    	getSupportActionBar().setTitle(R.string.Color_Code_Reference);
    	getSupportActionBar().setIcon(R.drawable.checkbox_red);

    	LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    	// Create views for priorities and add them to the layout:
    	String[] priorityNames = getResources().getStringArray(R.array.priorities);
    	LinearLayout list = (LinearLayout)findViewById(R.id.color_code_priority_list);
    	for (int i=0; i<priorityNames.length; i++)
    	{
    		ViewGroup row = (ViewGroup) inflater.inflate(R.layout.color_code_row, null);
    		ImageView checkbox = (ImageView) row.findViewById(R.id.color_code_checkbox);
    		TextView description = (TextView) row.findViewById(R.id.color_code_description);
    		
    		checkbox.setImageResource(TaskListFragment._priorityCheckboxDrawables[i]);
    		description.setText(priorityNames[i]);
    		list.addView(row);
    	}
    	
    	// Create views for statuses and add them to the layout:
    	String[] statusNames = getResources().getStringArray(R.array.statuses);
    	list = (LinearLayout)findViewById(R.id.color_code_status_list);
    	for (int i=0; i<statusNames.length; i++)
    	{
    		ViewGroup row = (ViewGroup) inflater.inflate(R.layout.color_code_row, null);
    		ImageView checkbox = (ImageView) row.findViewById(R.id.color_code_checkbox);
    		TextView description = (TextView) row.findViewById(R.id.color_code_description);
    		
    		checkbox.setImageResource(TaskListFragment._statusCheckboxDrawables[i]);
    		description.setText(statusNames[i]);
    		list.addView(row);
    	}
    }
}
