package com.customsolutions.android.utl;

// This activity displays a date picker that uses a real calendar.

// To call this activity, put a Bundle in the Intent with the following keys/values:
// default_date: The default date, in ms. Leave blank to use the current date.
// prompt: The prompt to display at the top of the screen.

// The response sent back to the caller includes the following:
// resultCode: either RESULT_CANCELED or RESULT_OK
// Intent object extras:
//  chosen_date: The date chosen (if applicable), in ms

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.TextView;

public class DateChooser extends UtlPopupActivity 
{
    GridView grid;
    DateChooserAdapter adapter;
    
    // This calendar object keeps track of the month we're in:
    GregorianCalendar mCalendar;
    
    // The default date:
    long defaultDate;
    
    // A reference to myself, needed in button handlers:
    DateChooser myself;
    
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        Util.log("Start the date picker");
        
        setContentView(R.layout.date_picker);
        
        getSupportActionBar().setIcon(resourceIdFromAttr(R.attr.ab_calendar_large));
        
        // Pull out the data in the Intent:
        Bundle extras = this.getIntent().getExtras();

		if (extras==null)
		{
			Util.log("DateChooser: No extras passed in.");
			finish();
			return;
		}

        // Set the title on the title bar:
        if (extras.containsKey("prompt"))
        {
            this.getSupportActionBar().setTitle(extras.getString("prompt"));
        }
        
        // Pull out the month and year from the default date passed in:
        defaultDate = Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null);
        if (extras.containsKey("default_date"))
        {
            defaultDate = extras.getLong("default_date");
        }
        GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        c.setTimeInMillis(defaultDate);
        mCalendar = (GregorianCalendar)c.clone();
        mCalendar.set(Calendar.DATE, 1);
        
        // Display the Month at the Top of the Screen:
        TextView title = (TextView)this.findViewById(R.id.date_chooser_title);
        String[] monthArray = this.getResources().getStringArray(R.array.months);
        title.setText(monthArray[c.get(Calendar.MONTH)]+" "+c.get(Calendar.YEAR));
        
        grid = (GridView)this.findViewById(R.id.date_chooser_grid);
        adapter = new DateChooserAdapter(this);
        adapter.init(c.get(Calendar.MONTH), c.get(Calendar.YEAR), defaultDate);
        grid.setAdapter(adapter);
        
        // Label the quick date buttons:
        String[] dowArray = new String[7];
        dowArray[0] = Util.getString(R.string.Sunday);
        dowArray[1] = Util.getString(R.string.Monday);
        dowArray[2] = Util.getString(R.string.Tuesday);
        dowArray[3] = Util.getString(R.string.Wednesday);
        dowArray[4] = Util.getString(R.string.Thursday);
        dowArray[5] = Util.getString(R.string.Friday);
        dowArray[6] = Util.getString(R.string.Saturday);
        GregorianCalendar c2 = new GregorianCalendar(TimeZone.getTimeZone(
            Util.settings.getString("home_time_zone","")));
        c2.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
        c2.add(Calendar.DATE, 2);
        Button b = (Button)this.findViewById(R.id.date_chooser_quick_date_dow0);
        b.setText(dowArray[c2.get(Calendar.DAY_OF_WEEK)-1]);
        c2.add(Calendar.DATE, 1);
        b = (Button)this.findViewById(R.id.date_chooser_quick_date_dow1);
        b.setText(dowArray[c2.get(Calendar.DAY_OF_WEEK)-1]);
        c2.add(Calendar.DATE, 1);
        b = (Button)this.findViewById(R.id.date_chooser_quick_date_dow2);
        b.setText(dowArray[c2.get(Calendar.DAY_OF_WEEK)-1]);
        c2.add(Calendar.DATE, 1);
        b = (Button)this.findViewById(R.id.date_chooser_quick_date_dow3);
        b.setText(dowArray[c2.get(Calendar.DAY_OF_WEEK)-1]);
        c2.add(Calendar.DATE, 1);
        b = (Button)this.findViewById(R.id.date_chooser_quick_date_dow4);
        b.setText(dowArray[c2.get(Calendar.DAY_OF_WEEK)-1]);

        //
        // Button Handlers:
        //
        
        myself = this;
        
        // Quick date buttons:
        
        ((Button)findViewById(R.id.date_chooser_quick_date_today)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});
        
        ((Button)findViewById(R.id.date_chooser_quick_date_tomorrow)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 1);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});
        
        ((Button)findViewById(R.id.date_chooser_quick_date_dow0)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 2);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_dow1)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 3);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_dow2)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 4);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_dow3)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 5);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_dow4)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 6);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_one_week)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.DATE, 7);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_one_month)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.MONTH, 1);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        ((Button)findViewById(R.id.date_chooser_quick_date_one_year)).setOnClickListener(new View.
        	OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
		            Util.settings.getString("home_time_zone","")));
		        c.setTimeInMillis(Util.timeShift(System.currentTimeMillis(),TimeZone.getDefault(),null));
		        c.add(Calendar.YEAR, 1);
		        Bundle b = new Bundle();
		        b.putLong("chosen_date",Util.getMidnight(c.getTimeInMillis(), DateChooser.this));
		        Intent i = new Intent();
		        i.putExtras(b);
		        setResult(RESULT_OK,i);
		        finish();
			}
		});

        // Next Button:
        findViewById(R.id.date_chooser_next_month).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                mCalendar.add(Calendar.MONTH,1);
                refresh();
            }
        });

        // Previous Button:
        findViewById(R.id.date_chooser_prev_month).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                mCalendar.add(Calendar.MONTH,-1);
                refresh();
            }
        });
    
        // Next Year Button:
        findViewById(R.id.date_chooser_next_year).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                mCalendar.add(Calendar.YEAR,1);
                refresh();
            }
        });

        // Previous Year Button:
        findViewById(R.id.date_chooser_prev_year).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                mCalendar.add(Calendar.YEAR,-1);
                refresh();
            }
        });
    }
    
    // Refresh the display:
    void refresh()
    {
        adapter.init(mCalendar.get(Calendar.MONTH),mCalendar.get(Calendar.YEAR),
            defaultDate);
        grid.setAdapter(adapter);
        
        // Set the title
        TextView title = (TextView)myself.findViewById(R.id.date_chooser_title);
        String[] monthArray = myself.getResources().getStringArray(R.array.months);
        title.setText(monthArray[mCalendar.get(Calendar.MONTH)]+" "+
            mCalendar.get(Calendar.YEAR));
    }
    
    // Handle a tap on a date button:
    void handle_date_selected(int position)
    {
        long returnedDate = adapter.getDateForPosition(position);
        Bundle b = new Bundle();
        b.putLong("chosen_date",returnedDate);
        Intent i = new Intent();
        i.putExtras(b);
        setResult(RESULT_OK,i);
        finish();
    }
}
