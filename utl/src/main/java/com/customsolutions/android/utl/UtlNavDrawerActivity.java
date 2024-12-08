package com.customsolutions.android.utl;

// An activity that includes the navigation drawer.  This is used for all non-split-screen views
// that use a navigation drawer, as well as all split-screen views (which either display the nav
// drawer initially hidden, or display it on the top or left).

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.ActionMode;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.Locale;

public class UtlNavDrawerActivity extends UtlActivity
{
	// The minimum width of the right side sliding drawer, which is sometimes used for task and note
	// viewing and editing.
	private static final int RIGHT_SIDE_MIN_WIDTH = 326; // in dp
	
	// The size of the resize control, in dp:
	private static final int RESIZE_CONTROL_SIZE = 32;
	
    // Constants for swipe gesture detection.  Units are dp:
    private static final int SWIPE_MIN_DISTANCE = 100;
    private static final int SWIPE_MAX_OFF_PATH = 200;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    // Fragment types that this Activity can place:
    public static final int FRAG_LIST = 1;
    public static final int FRAG_DETAILS = 2;

    // The tag used to identify the nav drawer:
    public static final String NAV_DRAWER_TAG = "nav_drawer";
    
    // The tag used to identify a message displayed in the detail pane:
    public static final String DETAIL_PANE_MESSAGE_TAG = "pane_message";
    
	private DrawerLayout _drawerLayout;
	private ActionBarDrawerToggle _drawerToggle;
	private ActionBar _ab;
	private GestureDetector _gestureDetector;
	private View.OnTouchListener _gestureListener;
	 
    // The density of our display.  Used in gesture detection:
    private float _displayDensity;
    
    long _drawerOpenTime;
    
    // Flag specifying whether the drawer is being used:
    private boolean _usingLeftDrawerToggle;
    private boolean _usingRightDrawerToggle;
    
    // The split-screen option in use:
    private int _splitScreen;
    
    // Quick reference to the fragment manager:
    private FragmentManager _fragmentManager;
    
    // We have 4 drag listener objects - 2 for the horizontal controls and 2 for the vertical.
    // These are declared as generic objects to prevent a crash under Android 2.x.
    Object _navListDragListenerHorizontal;
    Object _listDetailDragListenerHorizontal;
    Object _navListDragListenerVertical;
    Object _listDetailDragListenerVertical;
    
    // Flag which determines if we are in resize mode:
    private boolean _inResizeMode;
    
    private boolean _shouldHideMenu;
    
    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		// The layout we use depends on the split-screen option:
        _splitScreen = getSplitScreenOption();
        if (_splitScreen==Util.SS_NONE)
        	setContentView(R.layout.nav_drawer);
        else if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
        	setContentView(R.layout.two_pane_nav_list);
        else if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
        	setContentView(R.layout.two_pane_list_details);
        else
        	setContentView(R.layout.nav_drawer_3_pane);
		
		_ab = getSupportActionBar();
		
		_inResizeMode = false;
		_shouldHideMenu = false;
		
		// Determine if we're using the drawer toggle functionality (to open and close the drawer
		// through a tap or swipe):
		_usingLeftDrawerToggle = true;
		_usingRightDrawerToggle = false;
		if (_splitScreen==Util.SS_3_PANE || _splitScreen==Util.SS_2_PANE_NAV_LIST)
		{
			// Navigation drawer is being displayed on-screen all the time.  No need for 
			// toggle functionality.
			_usingLeftDrawerToggle = false;
		}
		if (_splitScreen==Util.SS_2_PANE_NAV_LIST && getOrientation()==ORIENTATION_LANDSCAPE)
			_usingRightDrawerToggle = true;
		
		_drawerLayout = (DrawerLayout) findViewById(R.id.nav_drawer_layout);  // May be null.
		if (_usingRightDrawerToggle)
		{
            if (_drawerLayout==null)
            {
                // This can happen on Blackberry devices with square screens.  The call to
                // getOrientation() returns landscape, but Blackberry's device is loading the
                // portrait version of the layout.
                _usingRightDrawerToggle = false;
                overrideOrientation(ORIENTATION_PORTRAIT);
            }
            else
            {
                _drawerLayout.setDrawerShadow(resourceIdFromAttr(R.attr.nav_drawer_shadow_right),
                        GravityCompat.END);
                _drawerLayout.setFocusableInTouchMode(false);
            }
		}
		else if (_usingLeftDrawerToggle)
		{
			_drawerLayout.setDrawerShadow(resourceIdFromAttr(R.attr.nav_drawer_shadow), 
				GravityCompat.START);
			_drawerLayout.setFocusableInTouchMode(false);
		}

		if (_usingLeftDrawerToggle)
		{
			_ab.setDisplayHomeAsUpEnabled(true);
			_ab.setHomeButtonEnabled(true);
			
			_drawerToggle = new ActionBarDrawerToggle(this, _drawerLayout, R.string.
				nav_drawer_open, R.string.nav_drawer_close)
			{
				/** Called when a drawer has settled in a completely closed state. */
	            public void onDrawerClosed(View view) {
	                super.onDrawerClosed(view);
	                _shouldHideMenu = false;
	                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
	                _drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	            }
	
	            /** Called when a drawer has settled in a completely open state. */
				public void onDrawerOpened(View drawerView) {
	                super.onDrawerOpened(drawerView);
	                _shouldHideMenu = true;
	                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
	                _drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
	            }
				
				public void onDrawerStateChanged(int newState)
				{
					if (newState==DrawerLayout.STATE_DRAGGING)
					{
						// We get here only as the drawer is opening.  The timestamp we're setting prevents
						// accidental immediate closing due to a sensitive touchscreen.
		                _drawerOpenTime = System.currentTimeMillis();
					}
				}
			};
			
			// Set the drawer toggle as the DrawerListener
	        _drawerLayout.setDrawerListener(_drawerToggle);

			_drawerToggle.setDrawerIndicatorEnabled(true);
	        
	        _gestureDetector = new GestureDetector(this,new SwipeDetector());
	        _gestureListener = new View.OnTouchListener() 
	        {
	            public boolean onTouch(View v, MotionEvent event) 
	            {
	            	if (_drawerLayout.isDrawerOpen(GravityCompat.START) &&
	            		event.getAction() == android.view.MotionEvent.ACTION_UP)
	            	{
	            		if (event.getX() > _drawerLayout.findViewById(R.id.nav_drawer_wrapper).getRight())
	            		{
	            			// A tap occurred to the right of the drawer.  This means the drawer should
	            			// be closed.
	            			_drawerLayout.closeDrawers();
	        				_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	        				return true;
	            		}
	            	}
	                return _gestureDetector.onTouchEvent(event);
	            }
	        };
	        _drawerLayout.setOnTouchListener(_gestureListener);
		}
		else
		{
			// If we're not using the left drawer toggle, then the navigation bar is being displayed
			// constantly on the screen.  We don't want to display a "back" button in this case 
			// because this is a top-level Activity.
			_ab.setDisplayHomeAsUpEnabled(false);
		}
		
		if (_usingRightDrawerToggle)
		{
			_drawerToggle = new ActionBarDrawerToggle(this, _drawerLayout, R.string.
				nav_drawer_open, R.string.nav_drawer_close)
			{
				 /** Called when a drawer has settled in a completely closed state. */
	            public void onDrawerClosed(View view) {
	                super.onDrawerClosed(view);
	                _drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	                _shouldHideMenu = false;
	                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
	            }
	
	            /** Called when a drawer has settled in a completely open state. */
				public void onDrawerOpened(View drawerView) {
	                super.onDrawerOpened(drawerView);
	                _drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
	                _shouldHideMenu = true;
	                supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
	            }
				
				public void onDrawerStateChanged(int newState)
				{
				}
			};
			
			// Set the drawer toggle as the DrawerListener
	        _drawerLayout.setDrawerListener(_drawerToggle);
	        _drawerToggle.syncState();
	        
	        _gestureDetector = new GestureDetector(this,new SwipeDetector());
	        _gestureListener = new View.OnTouchListener() 
	        {
	            public boolean onTouch(View v, MotionEvent event) 
	            {
	            	if (_drawerLayout.isDrawerOpen(GravityCompat.END) &&
	            		event.getAction() == android.view.MotionEvent.ACTION_UP)
	            	{
	            		if (event.getX() < _drawerLayout.findViewById(R.id.ss_pane_detail).getLeft())
	            		{
	            			// A tap occurred to the left of the drawer.  This means the drawer should
	            			// be closed.
	            			_drawerLayout.closeDrawers();
	        				_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
	        				return true;
	            		}
	            	}
	                return _gestureDetector.onTouchEvent(event);
	            }
	        };
	        _drawerLayout.setOnTouchListener(_gestureListener);
		}
		
        // Fetch the display density:
        DisplayMetrics metrics = new DisplayMetrics();
    	this.getWindowManager().getDefaultDisplay().getMetrics(metrics);
    	_displayDensity = metrics.density;
    	
    	_drawerOpenTime = 0;     
    	
    	// Set up the drag listeners, which handle the resize operations:
    	if (android.os.Build.VERSION.SDK_INT>=11)
    	{
	    	_navListDragListenerHorizontal = new View.OnDragListener()
			{
				float _savedY;
				View _resizerContainer = findViewById(R.id.ss_nav_list_resizer_container);
				
				@Override
				public boolean onDrag(View v, DragEvent event)
				{
					switch (event.getAction())
					{
					case DragEvent.ACTION_DRAG_STARTED:
						_savedY = _resizerContainer.getTop();
						return true;
						
					case DragEvent.ACTION_DRAG_LOCATION:
						_resizerContainer.setY(_savedY);
						_resizerContainer.setX(event.getX()-(RESIZE_CONTROL_SIZE/2)*_displayDensity);
						
						return true;
						
					case DragEvent.ACTION_DROP:
						handleNavListHorizontalResize(event.getX());
						return true;
					}
					return true;
				}
			};
			
			_navListDragListenerVertical = new View.OnDragListener()
			{
				float _savedX;
				View _resizerContainer = findViewById(R.id.ss_nav_list_resizer_container);
				
				@Override
				public boolean onDrag(View v, DragEvent event)
				{
					switch (event.getAction())
					{
					case DragEvent.ACTION_DRAG_STARTED:
						_savedX = _resizerContainer.getLeft();
						return true;
						
					case DragEvent.ACTION_DRAG_LOCATION:
						_resizerContainer.setX(_savedX);
						_resizerContainer.setY(event.getY()-(RESIZE_CONTROL_SIZE/2)*_displayDensity);
						
						return true;
						
					case DragEvent.ACTION_DROP:
						handleNavListVerticalResize(event.getY());
						return true;
					}
					return true;
				}
			};
			
			_listDetailDragListenerHorizontal = new View.OnDragListener()
			{
				float _savedY;
				View _resizerContainer = findViewById(R.id.ss_list_detail_resizer_container);
				
				@Override
				public boolean onDrag(View v, DragEvent event)
				{
					switch (event.getAction())
					{
					case DragEvent.ACTION_DRAG_STARTED:
						_savedY = _resizerContainer.getTop();
						return true;
						
					case DragEvent.ACTION_DRAG_LOCATION:
						_resizerContainer.setY(_savedY);
						_resizerContainer.setX(event.getX()-(RESIZE_CONTROL_SIZE/2)*_displayDensity);
						
						return true;
						
					case DragEvent.ACTION_DROP:
						handleListDetailHorizontalResize(event.getX());
						return true;
					}
					return true;
				}
			};
			
			_listDetailDragListenerVertical = new View.OnDragListener()
			{
				float _savedX;
				View _resizerContainer = findViewById(R.id.ss_list_detail_resizer_container);
				
				@Override
				public boolean onDrag(View v, DragEvent event)
				{
					switch (event.getAction())
					{
					case DragEvent.ACTION_DRAG_STARTED:
						_savedX = _resizerContainer.getLeft();
						return true;
						
					case DragEvent.ACTION_DRAG_LOCATION:
						_resizerContainer.setX(_savedX);
						_resizerContainer.setY(event.getY()-(RESIZE_CONTROL_SIZE/2)*_displayDensity);
						
						return true;
						
					case DragEvent.ACTION_DROP:
						handleListDetailVerticalResize(event.getY());
						return true;
					}
					return true;
				}
			};
    	}
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        return super.onCreateOptionsMenu(menu);
    }

    /** Called whenever a drawer is open or closed. */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
    	if ((!_usingLeftDrawerToggle && !_usingRightDrawerToggle) || _drawerLayout==null)
    		return super.onPrepareOptionsMenu(menu);
    	
    	// See if the drawer is open:
    	boolean isDrawerOpen;
    	if (_usingLeftDrawerToggle)
    		isDrawerOpen = _drawerLayout.isDrawerOpen(GravityCompat.START);
    	else
    		isDrawerOpen = _drawerLayout.isDrawerOpen(GravityCompat.END);
    	
    	// Hide or show all menu items based on the state of the drawer:
    	for (int i=0; i<menu.size(); i++)
    	{
    		menu.getItem(i).setVisible(!_shouldHideMenu);
    	}
    	
    	return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) 
    {
        super.onPostCreate(savedInstanceState);
        
        _fragmentManager = getSupportFragmentManager();
        
        // Sync the toggle state after onRestoreInstanceState has occurred.
        if (_usingLeftDrawerToggle)
        {
        	_drawerToggle.syncState();
	        
	        // Place the Nav drawer itself, which is in a fragment:
        	Fragment navDrawer = _fragmentManager.findFragmentByTag(NAV_DRAWER_TAG);
        	if (navDrawer==null || navDrawer.getId()!=R.id.nav_drawer_wrapper)
        	{
        		navDrawer = new NavDrawerFragment();
            	_fragmentManager.beginTransaction().replace(R.id.nav_drawer_wrapper, navDrawer, 
        			NAV_DRAWER_TAG).commit();
        	}
        }
        if (_usingRightDrawerToggle)
        {
        	_drawerToggle.syncState();
        }
        
        if (_splitScreen==Util.SS_NONE && getOrientation()==ORIENTATION_PORTRAIT &&
        	_settings.getInt(PrefNames.SS_LANDSCAPE, 0)==Util.SS_2_PANE_NAV_LIST &&
        	savedInstanceState!=null)
        {
        	_drawerLayout.closeDrawer(GravityCompat.START);
        }
        
        // Place the nav drawer on the top or left if needed:
        if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
        {
        	// Navigation drawer on top/left, task list on bottom/right:
			
        	if (getOrientation()==ORIENTATION_LANDSCAPE)
        	{
        		// The right-side drawer always starts closed.
        		_drawerLayout.closeDrawer(GravityCompat.END);
        	}
        	
			// Place the navigation drawer:
			Fragment navDrawer = _fragmentManager.findFragmentByTag(NAV_DRAWER_TAG);
        	if (navDrawer==null || navDrawer.getId()!=R.id.nav_drawer_wrapper)
        	{
        		navDrawer = new NavDrawerFragment();
        		_fragmentManager.beginTransaction().replace(R.id.nav_drawer_wrapper, navDrawer, 
        			NAV_DRAWER_TAG).commit();
        	}
        	
        	if (getOrientation()==ORIENTATION_LANDSCAPE)
        	{
	        	// Make sure the right-side sliding drawer is not wider than half of the screen:
	    		FrameLayout slider = (FrameLayout)findViewById(R.id.ss_pane_detail);
	    		DrawerLayout.LayoutParams layoutParams =
	    			(DrawerLayout.LayoutParams) slider.getLayoutParams();
	    		if (layoutParams.width > _screenWidth/2)
	    		{
	    			layoutParams.width = _screenWidth/2;
	    			if (layoutParams.width < (RIGHT_SIDE_MIN_WIDTH*_displayDensity))
	    			{
	    				layoutParams.width = Math.round(RIGHT_SIDE_MIN_WIDTH*_displayDensity);
	    			}
	    			slider.setLayoutParams(layoutParams);
	    		}
        	}

        }
        else if (_splitScreen==Util.SS_3_PANE)
        {
			// Place the navigation drawer:
        	Fragment navDrawer = _fragmentManager.findFragmentByTag(NAV_DRAWER_TAG);
        	if (navDrawer==null || navDrawer.getId()!=R.id.nav_drawer_wrapper)
        	{
        		navDrawer = new NavDrawerFragment();
        		_fragmentManager.beginTransaction().replace(R.id.nav_drawer_wrapper, navDrawer, 
        			NAV_DRAWER_TAG).commit();
        	}        	
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
    	super.onWindowFocusChanged(hasFocus);
    	if (!hasFocus)
    		return;
    	
		if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
		{
			// Task list on top/left, task details on bottom/right.
			
			if (getOrientation()==ORIENTATION_LANDSCAPE)
        	{
        		// Set the width of the right pane.  The left pane will then take up the remaining
        		// space.
				if (!_settings.contains(PrefNames.PANE_SIZE_LIST_DETAIL_LANDSCAPE) ||
					_settings.getInt(PrefNames.PANE_SIZE_LIST_DETAIL_LANDSCAPE, 0)==0)
				{
					// Pane size has not been set.  Use the default.
	        		FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
	            	RelativeLayout.LayoutParams detailLayoutParams = (RelativeLayout.LayoutParams) 
	            		detailFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(
	            		detailLayoutParams);
	            	newLayoutParams.width = _screenWidth / 2;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
	            	detailFrameLayout.setLayoutParams(newLayoutParams);
				}
				else
				{
					// Pane size has been set.
					handleListDetailHorizontalResize(_settings.getInt(PrefNames.
						PANE_SIZE_LIST_DETAIL_LANDSCAPE, 0));
				}
        	}
			else
			{
				// Set the height of the bottom pane.  The top pane will then take up the remaining
				// space.
				if (!_settings.contains(PrefNames.PANE_SIZE_LIST_DETAIL_PORTRAIT) ||
					_settings.getInt(PrefNames.PANE_SIZE_LIST_DETAIL_PORTRAIT, 0)==0)
				{
					// Pane size has not been set.  Use the default.
					FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
	            	RelativeLayout.LayoutParams detailLayoutParams = (RelativeLayout.LayoutParams) 
	            		detailFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(
	            		detailLayoutParams);
	            	newLayoutParams.height = _screenHeight * 45 / 100;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
	            	detailFrameLayout.setLayoutParams(newLayoutParams);
				}
				else
				{
					// Pane size has been set.
					handleListDetailVerticalResize(_settings.getInt(PrefNames.
						PANE_SIZE_LIST_DETAIL_PORTRAIT, 0));
				}
			}
			
		}
		else if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
		{
        	if (getOrientation()==ORIENTATION_LANDSCAPE)
        	{
        		if (_settings.contains(PrefNames.PANE_SIZE_NAV_LIST_LANDSCAPE) &&
        			_settings.getInt(PrefNames.PANE_SIZE_NAV_LIST_LANDSCAPE, 0)>0)
        		{
        			// The size of the navigation drawer has been previously set by the user,
        			// so don't use the default size.
        			handleNavListHorizontalResize(_settings.getInt(PrefNames.
        				PANE_SIZE_NAV_LIST_LANDSCAPE, 0));
        		}
        	}
        	
        	if (getOrientation()==ORIENTATION_PORTRAIT)
        	{
        		// Set the height of the top pane, and the bottom pane should fill the rest of the 
        		// screen:
        		if (!_settings.contains(PrefNames.PANE_SIZE_NAV_LIST_PORTRAIT) ||
					_settings.getInt(PrefNames.PANE_SIZE_NAV_LIST_PORTRAIT, 0)==0)
				{
        			// Pane size has not been set.  Use the default.
	        		FrameLayout navFrameLayout = (FrameLayout)findViewById(R.id.nav_drawer_wrapper);
	            	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) 
	            		navFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(
	            		navLayoutParams);
	            	newLayoutParams.height = _screenHeight * 45 / 100;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
	            	navFrameLayout.setLayoutParams(newLayoutParams);
				}
        		else
        		{
        			// Pane size has been set.
					handleNavListVerticalResize(_settings.getInt(PrefNames.
						PANE_SIZE_NAV_LIST_PORTRAIT, 0));
        		}
        	}
		}
		else if (_splitScreen==Util.SS_3_PANE)
		{
        	if (getOrientation()==ORIENTATION_LANDSCAPE)
        	{
        		if (_settings.contains(PrefNames.PANE_SIZE_3_PANE_LEFT_LANDSCAPE) &&
        			_settings.getInt(PrefNames.PANE_SIZE_3_PANE_LEFT_LANDSCAPE, 0)>0)
        		{
        			// The size of the navigation drawer has been previously set by the user,
        			// so don't use the default size.
        			handleNavListHorizontalResize(_settings.getInt(PrefNames.
        				PANE_SIZE_3_PANE_LEFT_LANDSCAPE, 0));
        		}
        		
        		// Set the width of the right pane.  The middle pane will then take up the remaining
        		// space.
        		if (!_settings.contains(PrefNames.PANE_SIZE_3_PANE_RIGHT_LANDSCAPE) ||
					_settings.getInt(PrefNames.PANE_SIZE_3_PANE_RIGHT_LANDSCAPE, 0)==0)
				{
	        		FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
	            	RelativeLayout.LayoutParams detailLayoutParams = (RelativeLayout.LayoutParams) 
	            		detailFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(
	            		detailLayoutParams);
	            	newLayoutParams.width = _screenWidth / 3;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
	            	detailFrameLayout.setLayoutParams(newLayoutParams);
				}
        		else
        		{
        			// The size of the navigation drawer has been previously set by the user,
        			// so don't use the default size.
        			handleListDetailHorizontalResize(_settings.getInt(PrefNames.
        				PANE_SIZE_3_PANE_RIGHT_LANDSCAPE, 0));
        		}
        	}
        	else
        	{
        		// Set the height of the top and bottom panes, and the middle will take up the rest:
        		
        		if (!_settings.contains(PrefNames.PANE_SIZE_3_PANE_TOP_PORTRAIT) ||
					_settings.getInt(PrefNames.PANE_SIZE_3_PANE_TOP_PORTRAIT, 0)==0)
				{
	        		FrameLayout navFrameLayout = (FrameLayout)findViewById(R.id.nav_drawer_wrapper);
	            	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) 
	            		navFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(
	            		navLayoutParams);
	            	newLayoutParams.height = _screenHeight * 25 / 100;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
	            	navFrameLayout.setLayoutParams(newLayoutParams);
				}
        		else
        		{
        			handleNavListVerticalResize(_settings.getInt(PrefNames.PANE_SIZE_3_PANE_TOP_PORTRAIT,
        				0));
        		}
            	
        		if (!_settings.contains(PrefNames.PANE_SIZE_3_PANE_BOTTOM_PORTRAIT) ||
					_settings.getInt(PrefNames.PANE_SIZE_3_PANE_BOTTOM_PORTRAIT, 0)==0)
				{
					FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
	            	RelativeLayout.LayoutParams detailLayoutParams = (RelativeLayout.LayoutParams) 
	            		detailFrameLayout.getLayoutParams();
	            	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(detailLayoutParams);
	            	newLayoutParams.height = _screenHeight * 35 / 100;
	            	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
	            	detailFrameLayout.setLayoutParams(newLayoutParams);
				}
        		else
        		{
        			handleListDetailVerticalResize(_settings.getInt(PrefNames.
        				PANE_SIZE_3_PANE_BOTTOM_PORTRAIT,0));
        		}
        	}
		}
	}

	@Override
    public void onConfigurationChanged(Configuration newConfig) 
	{
        super.onConfigurationChanged(newConfig);
        if (_usingLeftDrawerToggle || _usingRightDrawerToggle)
        	_drawerToggle.onConfigurationChanged(newConfig);
    }
	
	@Override
	public void onResume()
	{
		// When we return here after leaving, make sure the navigation drawer is closed:
		if (_usingLeftDrawerToggle && _drawerLayout!=null)
		{
			_drawerLayout.closeDrawers();
			_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
		}
		super.onResume();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		if (_usingLeftDrawerToggle && item.getItemId()==android.R.id.home)
        {
            // The user might be closing the drawer, make sure it's unlocked.
            _drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

		// The action bar home/up action should open or close the drawer.
        // ActionBarDrawerToggle will take care of this.
		if (_usingLeftDrawerToggle && _drawerToggle.onOptionsItemSelected(item))
		{
            return true;
		}
        
		if (item.getItemId()==android.R.id.home && !_usingLeftDrawerToggle)
		{
			// When we're not using the left drawer toggle, this Activity is a top-level one.  
			// The back button does not display at the top and tapping the home button does nothing.
			// We need to intercept the button press here, so that UtlActivity (the super class)
			// does hot handle it and exit the activity.
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
    // Implements detection of a swipe gesture:
    private class SwipeDetector extends SimpleOnGestureListener
    {
    	@Override
    	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
    	{
    		if (e1==null || e2==null)
    			return false;
    		
    		// Convert values from pixels to dp:
    		float e1X = e1.getX() / _displayDensity;
    		float e1Y = e1.getY() / _displayDensity;
    		float e2X = e2.getX() / _displayDensity;
    		float e2Y = e2.getY() / _displayDensity;
    		velocityX = velocityX / _displayDensity;
    		velocityY = velocityY / _displayDensity;
    		
    		if (Math.abs(e1Y - e2Y) > SWIPE_MAX_OFF_PATH)
    		{
    			return false;
    		}
    		
    		float diff = e1X - e2X;
    		if (_usingRightDrawerToggle)
    			diff = e2X-e1X;
    		if (diff > SWIPE_MIN_DISTANCE && Math.abs(velocityX) >
    			SWIPE_THRESHOLD_VELOCITY)
    		{
    			long diff2 = System.currentTimeMillis() - _drawerOpenTime;
    			if (diff2 > 500)
    			{
    				_drawerLayout.closeDrawers();
    				_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
    			}
    		}
    		return false;
    	}
    }
    
    // Handler for the back button:
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if (_usingLeftDrawerToggle && keyCode==KeyEvent.KEYCODE_BACK && _drawerLayout.
        	isDrawerOpen(GravityCompat.START))
        {
        	// The 'back' button closes the drawer:
        	_drawerLayout.closeDrawers();
			_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
			return true;
        }
        if (_usingRightDrawerToggle && keyCode==KeyEvent.KEYCODE_BACK && _drawerLayout.
        	isDrawerOpen(GravityCompat.END))
        {
        	// The 'back' button closes the drawer:
        	_drawerLayout.closeDrawers();
			_drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
			return true;
        }
        return super.onKeyDown(keyCode,event);
    }
        
    /** Launch a Fragment or an new Activity, depending on the current Activity in use. 
     * This can only be used to start a list activity/fragment, from a list activity. */
    private static ArrayList<UtlNavDrawerActivity> _activityList = new ArrayList<UtlNavDrawerActivity>();
    public void launchFragmentOrActivity(Fragment fragment, String tag, Intent newActivityIntent,
    	String newActivityClassName, boolean terminateCurrentActivity)
    {
    	if (this.getClass().getName().equals(newActivityClassName))
    	{
    		// This list class matches the class of our destination.
    		placeFragment(FRAG_LIST,fragment,tag);
    	}
    	else
    	{
    		// Start a new Activity:
    		startActivity(newActivityIntent);
    		overridePendingTransition(0, 0);
    		
    		if (terminateCurrentActivity)
    		{
    			for (int i=0; i<_activityList.size(); i++)
    			{
    				try
    				{
    					_activityList.get(i).finish();
    				}
    				catch (Exception e) { }
    			}
    			_activityList.clear();
    			this.finish();
    		}
    		else
    		{
    			_activityList.add(this);
    		}
    	}
    }
    
    // Place a fragment, based on the current split-screen preference:
    public void placeFragment(int fragType, Fragment fragment, String tag)
    {
    	if (_fragmentManager==null)
    		_fragmentManager = getSupportFragmentManager();
    	
    	if (_splitScreen==Util.SS_NONE)
    	{
    		// No split-screen.  Just put the fragment within the FrameLayout that covers the whole
    		// screen:
    		Fragment search = _fragmentManager.findFragmentByTag(tag);
    		if (search==null || search.getId()!=R.id.ss_pane_list)
    		{
    			try
				{
					_fragmentManager.beginTransaction().replace(R.id.ss_pane_list, fragment, tag).
						commit();
				}
    			catch (IllegalStateException e)
				{
					Util.log("WARNING: Got exception when trying to place fragment. "+
						e.getClass().getName()+" / "+e.getMessage());
				}
    		}
    		
    		return;
    	}
    	
    	// Determine which pane to place the fragment into.  This depends on the type and the split-
    	// screen option.
    	int paneID = R.id.ss_pane_list;
    	if (fragType==FRAG_DETAILS)
    		paneID = R.id.ss_pane_detail;
    	
    	// Place the fragment:
    	Fragment search = _fragmentManager.findFragmentByTag(tag);
    	if (search==null || search.getId()!=paneID)
		{
			_fragmentManager.beginTransaction().replace(paneID, fragment, tag).commit();
		}

    	if (fragType==FRAG_DETAILS && _splitScreen==Util.SS_2_PANE_NAV_LIST &&
    		getOrientation()==ORIENTATION_LANDSCAPE)
    	{
    		// The detail view is in the right-side sliding drawer, so we need to make sure the 
    		// drawer is open.
    		_drawerLayout.openDrawer(GravityCompat.END);
    		return;
    	}
    }
    
    // Get the current fragment in use, based on it's type:
    public Fragment getFragmentByType(int fragType)
    {
    	if (_fragmentManager==null)
    		_fragmentManager = getSupportFragmentManager();
    	
    	if (_splitScreen==Util.SS_NONE)
    	{
    		// No split-screen, so the type doesn't matter since only one fragment can be in place.
    		return _fragmentManager.findFragmentById(R.id.ss_pane_list);
    	}
    	
    	// Determine which pane the fragment will be in:
    	int paneID = R.id.ss_pane_list;
    	if (fragType==FRAG_DETAILS)
    		paneID = R.id.ss_pane_detail;
    	
    	// Return the current fragment:
    	return _fragmentManager.findFragmentById(paneID);
    }
    
    // Get a reference to a fragment by its tag:
    public Fragment getFragmentByTag(String tag)
    {
    	if (_fragmentManager==null)
    		_fragmentManager = getSupportFragmentManager();
    	return _fragmentManager.findFragmentByTag(tag);
    }
    
    // Determine if a fragment containing details should show in its own activity:
    public boolean useNewActivityForDetails()
    {
    	if (_splitScreen==Util.SS_NONE)
    		return true;
    	if (_splitScreen==Util.SS_2_PANE_NAV_LIST && getOrientation()==ORIENTATION_PORTRAIT)
    		return true;
    	return false;
    }
    
    // Display a mesage in the detail pane.  This function does nothing if the split-screen mode
    // does not allow for this message.
    public void showDetailPaneMessage(String msg)
    {
    	if (_fragmentManager==null)
    		_fragmentManager = getSupportFragmentManager();
    	
    	// See if the split-screen mode support this:
    	if (_splitScreen!=Util.SS_2_PANE_LIST_DETAILS && _splitScreen!=Util.SS_3_PANE)
    		return;
    	
    	// Check to see if the message is already in place.  Only create a new fragment if
    	// we have to.
    	PaneMessage paneMessageFragment = (PaneMessage)_fragmentManager.findFragmentByTag(
    		DETAIL_PANE_MESSAGE_TAG);
    	if (paneMessageFragment!=null && paneMessageFragment.getView()!=null)
    	{
    		paneMessageFragment.setMessage(msg);
    	}
    	else
    	{
    		paneMessageFragment = new PaneMessage();
    		Bundle b = new Bundle();
    		b.putString("message", msg);
    		paneMessageFragment.setArguments(b);
    		_fragmentManager.beginTransaction().replace(R.id.ss_pane_detail, paneMessageFragment, DETAIL_PANE_MESSAGE_TAG).
    			commit();
    	}
    }
    
    /** Close any drawers that are open. */
    public void closeDrawer()
    {
    	if (_usingLeftDrawerToggle)
    		_drawerLayout.closeDrawer(GravityCompat.START);
    	if (_usingRightDrawerToggle)
    		_drawerLayout.closeDrawer(GravityCompat.END);
    }
    
    /** Close the navigation drawer only */
    public void closeNavDrawer()
    {
    	if (_usingLeftDrawerToggle)
    		_drawerLayout.closeDrawer(GravityCompat.START);
    }
    
    // Refresh the navigation drawer counts (needed when a task changes and a split screen view is
    // in use):
    public void refreshNavDrawerCounts()
    {
    	NavDrawerFragment navFragment = (NavDrawerFragment)this.getFragmentByTag(NAV_DRAWER_TAG);
    	if (navFragment!=null)
    		navFragment.refreshCounts();
    }
    
    // Refresh the entire navigation drawer.  This should be called if folders, contexts,
    // etc have been added or deleted.
    public void refreshWholeNavDrawer()
    {
    	NavDrawerFragment navFragment = (NavDrawerFragment)this.getFragmentByTag(NAV_DRAWER_TAG);
    	if (navFragment!=null)
    		navFragment.regenerateNodes();
    }
    
    // Enter resize mode:
    @SuppressLint("NewApi")
	public void enterResizeMode()
    {
    	if (_splitScreen==Util.SS_NONE || android.os.Build.VERSION.SDK_INT<11 || _inResizeMode)
    		return;
    	
    	_inResizeMode = true;
    	
    	if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
    	{
    		findViewById(R.id.ss_nav_list_resizer_container).setVisibility(View.VISIBLE);
    		
    		// Define the touch listener that starts the drag:
    		findViewById(R.id.ss_nav_list_resizer_container).setOnTouchListener(new View.OnTouchListener()
			{
				@SuppressLint("NewApi")
				@Override
				public boolean onTouch(View v, MotionEvent event)
				{
					if (event.getAction()==android.view.MotionEvent.ACTION_DOWN)
					{
			    		// The root view of the whole layout can receive drag events:
			    		if (getOrientation()==ORIENTATION_LANDSCAPE)
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_navListDragListenerHorizontal);
			    		}
			    		else
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_navListDragListenerVertical);
			    		}

						// Start the drag operation using a blank shadow, since we're drawing the shadow
						// ourselves.
						v.startDrag(null, new MyDragShadowBuilder(), null, 0);
						return true;
					}
					else
						return false;
				}
			});
    	}
    	
    	if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
    	{
    		findViewById(R.id.ss_list_detail_resizer_container).setVisibility(View.VISIBLE);
    		
    		// Define the touch listener that starts the drag:
    		findViewById(R.id.ss_list_detail_resizer_container).setOnTouchListener(new View.OnTouchListener()
			{
				@SuppressLint("NewApi")
				@Override
				public boolean onTouch(View v, MotionEvent event)
				{
					if (event.getAction()==android.view.MotionEvent.ACTION_DOWN)
					{
			    		// The root view of the whole layout can receive drag events:
			    		if (getOrientation()==ORIENTATION_LANDSCAPE)
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_listDetailDragListenerHorizontal);
			    		}
			    		else
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_listDetailDragListenerVertical);
			    		}
			    		
						// Start the drag operation using a blank shadow, since we're drawing the shadow
						// ourselves.
						v.startDrag(null, new MyDragShadowBuilder(), null, 0);
						return true;
					}
					else
						return false;
				}
			});
    	}
    	
    	if (_splitScreen==Util.SS_3_PANE)
    	{
    		findViewById(R.id.ss_nav_list_resizer_container).setVisibility(View.VISIBLE);
    		findViewById(R.id.ss_list_detail_resizer_container).setVisibility(View.VISIBLE);
    		
    		// Define the touch listeners that starts the drag:
    		
    		// Define the touch listener that starts the drag:
    		findViewById(R.id.ss_nav_list_resizer_container).setOnTouchListener(new View.OnTouchListener()
			{
				@SuppressLint("NewApi")
				@Override
				public boolean onTouch(View v, MotionEvent event)
				{
					if (event.getAction()==android.view.MotionEvent.ACTION_DOWN)
					{
			    		// The root view of the whole layout can receive drag events:
			    		if (getOrientation()==ORIENTATION_LANDSCAPE)
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_navListDragListenerHorizontal);
			    		}
			    		else
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_navListDragListenerVertical);
			    		}

						// Start the drag operation using a blank shadow, since we're drawing the shadow
						// ourselves.
						v.startDrag(null, new MyDragShadowBuilder(), null, 0);
						return true;
					}
					else
						return false;
				}
			});
    		
    		findViewById(R.id.ss_list_detail_resizer_container).setOnTouchListener(new View.OnTouchListener()
			{
				@SuppressLint("NewApi")
				@Override
				public boolean onTouch(View v, MotionEvent event)
				{
					if (event.getAction()==android.view.MotionEvent.ACTION_DOWN)
					{
			    		// The root view of the whole layout can receive drag events:
			    		if (getOrientation()==ORIENTATION_LANDSCAPE)
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_listDetailDragListenerHorizontal);
			    		}
			    		else
			    		{
			    			findViewById(R.id.ss_root).setOnDragListener((View.OnDragListener)
			    				_listDetailDragListenerVertical);
			    		}
			    		
						// Start the drag operation using a blank shadow, since we're drawing the shadow
						// ourselves.
						v.startDrag(null, new MyDragShadowBuilder(), null, 0);
						return true;
					}
					else
						return false;
				}
			});	
    	}
    	
    	// Set up the contextual action bar, to let the user know that resize mode has started.
    	startSupportActionMode(new ActionMode.Callback()
		{
			@Override
			public boolean onPrepareActionMode(ActionMode am, Menu arg1)
			{
				am.setTitle(getString(R.string.Drag_the_lines));
				return true;
			}
			
			@Override
			public void onDestroyActionMode(ActionMode arg0)
			{
				_inResizeMode = false;
				
				// Make the resize controls invisible.
				if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
					findViewById(R.id.ss_nav_list_resizer_container).setVisibility(View.GONE);
				else if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
					findViewById(R.id.ss_list_detail_resizer_container).setVisibility(View.GONE);
				else if (_splitScreen==Util.SS_3_PANE)
				{
					findViewById(R.id.ss_nav_list_resizer_container).setVisibility(View.GONE);
					findViewById(R.id.ss_list_detail_resizer_container).setVisibility(View.GONE);
				}
			}
			
			@Override
			public boolean onCreateActionMode(ActionMode arg0, Menu arg1)
			{
				return true;
			}
			
			@Override
			public boolean onActionItemClicked(ActionMode arg0, MenuItem arg1)
			{
				return true;
			}
		});
    }
    
    // Tell others if we're in resize mode:
    public boolean inResizeMode()
    {
    	return _inResizeMode;
    }
    
    /** Determines if some tree notes (such as those for task and note lists) should remain 
     * highlighted after being tapped on. */
    public boolean keepNodesHighlighted()
    {
    	return !_usingLeftDrawerToggle;
    }
    
    // Perform a resize that involves the navigation drawer and list view (horizontal).
    // The input is the X coordinate that the user ended the drag and drop on:
    @SuppressLint("NewApi")
	private void handleNavListHorizontalResize(float dropX)
    {
    	FrameLayout navFrameLayout = (FrameLayout)findViewById(R.id.nav_drawer_wrapper);
    	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) navFrameLayout.
    		getLayoutParams();
    	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(navLayoutParams);
    	newLayoutParams.width = Math.round(dropX);
    	navFrameLayout.setLayoutParams(newLayoutParams);
    	
    	// Save the preference:
		if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
			Util.updatePref(PrefNames.PANE_SIZE_NAV_LIST_LANDSCAPE, Math.round(dropX), this);
		else
			Util.updatePref(PrefNames.PANE_SIZE_3_PANE_LEFT_LANDSCAPE, Math.round(dropX), this);
    	
    	// The resizer needs reset, otherwise it will also adjust itself:
    	View _resizerContainer = findViewById(R.id.ss_nav_list_resizer_container);
    	_resizerContainer.setTranslationX(((RESIZE_CONTROL_SIZE/2)+1)*_displayDensity);
    }
    
    // Perform a resize that involves the navigation drawer and list view (vertical).
    // The input is the Y coordinate that the user ended the drag and drop on:
    @SuppressLint("NewApi")
	private void handleNavListVerticalResize(float dropY)
    {
    	FrameLayout navFrameLayout = (FrameLayout)findViewById(R.id.nav_drawer_wrapper);
    	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) navFrameLayout.
    		getLayoutParams();
    	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(navLayoutParams);
    	newLayoutParams.height = Math.round(dropY)-findViewById(R.id.ss_root).getTop();
    	navFrameLayout.setLayoutParams(newLayoutParams);
    	
    	// Save the preference:
		if (_splitScreen==Util.SS_2_PANE_NAV_LIST)
			Util.updatePref(PrefNames.PANE_SIZE_NAV_LIST_PORTRAIT, Math.round(dropY), this);
		else
			Util.updatePref(PrefNames.PANE_SIZE_3_PANE_TOP_PORTRAIT, Math.round(dropY), this);
    	
    	// The resizer needs reset, otherwise it will also adjust itself:
    	View _resizerContainer = findViewById(R.id.ss_nav_list_resizer_container);
    	_resizerContainer.setTranslationY(((RESIZE_CONTROL_SIZE/2)+1)*_displayDensity);
    }
    
    // Perform a resize that involves the list and detail views (horizontal).
    // The input is the X coordinate that the user ended the drag and drop on:
    @SuppressLint("NewApi")
	private void handleListDetailHorizontalResize(float dropX)
    {
    	FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
    	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) detailFrameLayout.
    		getLayoutParams();
    	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(navLayoutParams);
    	newLayoutParams.width = _screenWidth - Math.round(dropX);
    	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
    	detailFrameLayout.setLayoutParams(newLayoutParams);
    	
    	// Save the preference:
		if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
			Util.updatePref(PrefNames.PANE_SIZE_LIST_DETAIL_LANDSCAPE, Math.round(dropX), this);
		else
			Util.updatePref(PrefNames.PANE_SIZE_3_PANE_RIGHT_LANDSCAPE, Math.round(dropX), this);
    	
    	// The resizer needs reset, otherwise it will also adjust itself:
    	View _resizerContainer = findViewById(R.id.ss_list_detail_resizer_container);
    	_resizerContainer.setTranslationX(((RESIZE_CONTROL_SIZE/2)+1)*_displayDensity);
    }
    
    // Perform a resize that involves the list and detail views (horizonta).
    // The input is the X coordinate that the user ended the drag and drop on:
    @SuppressLint("NewApi")
	private void handleListDetailVerticalResize(float dropY)
    {
    	FrameLayout detailFrameLayout = (FrameLayout)findViewById(R.id.ss_pane_detail);
    	RelativeLayout.LayoutParams navLayoutParams = (RelativeLayout.LayoutParams) detailFrameLayout.
    		getLayoutParams();
    	RelativeLayout.LayoutParams newLayoutParams = new RelativeLayout.LayoutParams(navLayoutParams);
    	newLayoutParams.height = findViewById(R.id.ss_root).getBottom() - Math.round(dropY);
    	newLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
    	detailFrameLayout.setLayoutParams(newLayoutParams);
    	
    	// Save the preference:
		if (_splitScreen==Util.SS_2_PANE_LIST_DETAILS)
			Util.updatePref(PrefNames.PANE_SIZE_LIST_DETAIL_PORTRAIT, Math.round(dropY), this);
		else
			Util.updatePref(PrefNames.PANE_SIZE_3_PANE_BOTTOM_PORTRAIT, Math.round(dropY), this);
    	
    	// The resizer needs reset, otherwise it will also adjust itself:
    	View _resizerContainer = findViewById(R.id.ss_list_detail_resizer_container);
    	_resizerContainer.setTranslationY(((RESIZE_CONTROL_SIZE/2)+1)*_displayDensity);
    }

    private class MyDragShadowBuilder extends View.DragShadowBuilder
	{

		@Override
		public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint)
		{
			outShadowSize.set(1,1);
			outShadowTouchPoint.set(0,0);
		}
	}
}
