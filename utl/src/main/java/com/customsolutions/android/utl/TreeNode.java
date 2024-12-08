package com.customsolutions.android.utl;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

// This is the base class for nodes - or rows - in the navigation drawer.

public abstract class TreeNode
{
	// Is this node expanded (showing items underneath)
	public boolean _isExpanded;

	// The parent index of the Node, if applicable.  -1 = no parent
	public int _parentIndex;
	
	// The index of this node in the list maintained by SplitScreenWrapper:
	public int _index;
	
	// These variables point to elements in the TreeNode's row:
	protected LinearLayout _layout;
	protected View _spacer1;
	protected View _spacer2;
	protected ImageView _icon;
	protected ImageView _expander;
	protected TextView _title;
	protected TextView _counter;
	protected ImageView _button;
	
	// A Context, needed to access certain system functions.
	protected UtlNavDrawerActivity _a;
		
	// A reference to the nav drawer Fragment:
	protected NavDrawerFragment _navDrawerFragment;
	
	protected boolean _isHighlighted;

	/** Works around an issue in Android O and P in which it fails to resolve a color attribute in
	 * the normal way. This is saved when the node is highlighted, and used when the node is
	 * unhighlighted. */
	private int _savedNormalColor = 0;

	// Each TreeNode subclass must define a custom constructor with all needed arguments.

	// All subclasses must call this constructor:
	public TreeNode(UtlNavDrawerActivity a)
	{
		_a = a;
		
		// Get a reference to the nav drawer fragment:
		FragmentManager fragManager = _a.getSupportFragmentManager();
		_navDrawerFragment = (NavDrawerFragment) fragManager.findFragmentByTag(
			UtlNavDrawerActivity.NAV_DRAWER_TAG);
		
		_isHighlighted = false;
	}
	
	// Get a list of TreeNode objects that are below the current one (which show up if 
	// the TreeNode is expanded).  By default, this returns an empty array (for subclasses
	// that return no nodes below).
	public ArrayList<TreeNode> getTreeNodesBelow()
	{
		return new ArrayList<TreeNode>();
	}
	
	// Get the View for this TreeNode, to display on the left/top side.  This must be
	// overwritten by the subclass.  The View returned must include a listener function
	// for the TextView displayed (to show something on the right/bottom side).
	public abstract View getView();
	
	// Get the title of this node:
	public abstract String getTitle();
	
	// Set the node's icon to either inverted or not inverted.  Must be implemented by subclasses.
	public abstract void setIconInversion(boolean isInverted);
	
	// Get the View for the expander control.  This will NOT include a listener function,
	// because that must be implemented in the Activity that uses the TreeNode objects.
	// This returns null for TreeNodes that don't have this.
	public View getExpanderView()
	{
		// Return null by default (because some TreeNodes will not have this):
		return null;
	}
	
	// Get a TextView to display the task or note count for this TreeNode. Returns null
	// if not applicable:
	public TextView getCounterTextView()
	{
		return null;
	}
	
	// Get a SQL query to run to get the task or note count.  The query must return only a 
	// single number.  Returns null if not applicable.
	public String getCounterQuery()
	{
		return null;
	}
	
	// Set the state of this TreeNode to expanded.  This updates the state variable and 
	// alters the image:
	public void setToExpanded()
	{
		_isExpanded = true;
		parseXmlLayout();
		if (_expander!=null && _expander.getVisibility()!=View.GONE)
		{
			if (_isHighlighted)
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.
					resourceIdFromAttr(_a, R.attr.nav_expand_inv)));
			}
			else
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.
					resourceIdFromAttr(_a, R.attr.nav_expand)));
			}
		}
	}

	// Set the state of this TreeNode to closed.  This updates the state variable and 
	// alters the image:
	public void setToClosed()
	{
		_isExpanded = false;
		parseXmlLayout();
		if (_expander!=null && _expander.getVisibility()!=View.GONE)
		{
			if (_isHighlighted)
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.
					resourceIdFromAttr(_a, R.attr.nav_right_ptr_inv)));
			}
			else
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.
					resourceIdFromAttr(_a, R.attr.nav_right_ptr)));
			}
		}
	}

	// Utility function to parse the xml layout and update the pointers to the various
	// layout objects:
	protected void parseXmlLayout()
	{
		if (_layout==null)
		{
			LayoutInflater inflater = (LayoutInflater)_a.
				getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			
			_layout = (LinearLayout)inflater.inflate(R.layout.nav_drawer_row, null);
			_spacer1 = _layout.findViewById(R.id.nav_drawer_spacer1);
			_spacer2 = _layout.findViewById(R.id.nav_drawer_spacer2);
			_expander = (ImageView)_layout.findViewById(R.id.nav_drawer_expander);
			_title = (TextView)_layout.findViewById(R.id.nav_drawer_text);
			_counter = (TextView)_layout.findViewById(R.id.nav_drawer_counter);
			_button = (ImageView)_layout.findViewById(R.id.nav_drawer_button);
			_icon = (ImageView)_layout.findViewById(R.id.nav_drawer_icon);
			
			_expander.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(
				_a, R.attr.nav_right_ptr)));

			_layout.findViewById(R.id.nav_drawer_hit_area).setOnTouchListener(
				new View.OnTouchListener ()
			{
				@Override
				public boolean onTouch(View view, MotionEvent event)
				{
					// Do nothing if we're in resize mode:
					if (_a.inResizeMode())
						return true;

					if (_expander.getVisibility() == View.VISIBLE)
					{
						// We never highlight a row with children.  Allow the ripple to play.
						return false;
					}

					if ((_a.getSplitScreenOption()!=Util.SS_2_PANE_NAV_LIST && _a.
						getSplitScreenOption()!=Util.SS_3_PANE))
					{
						// The navigation drawer is not showing all of the time, so we don't
						// highlight the row, and allow the ripple to play.
						return false;
					}

					if (event.getAction() == android.view.MotionEvent.ACTION_DOWN)
					{
						// highlightNode();
					}
					else if (event.getAction() == android.view.MotionEvent.ACTION_UP)
					{
						if (unhighlightOnRelease())
							flashNode();
						else
						{
							// The node is staying highlighted.
							highlightNode();
							_navDrawerFragment.moveHighlightedNode(TreeNode.this);
						}
						view.performClick();
					}
					else if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE ||
						event.getAction() == android.view.MotionEvent.ACTION_CANCEL)
					{
						unhighlightNode();
					}
					return true;
				}
			});
			_layout.findViewById(R.id.nav_drawer_hit_area).setBackgroundResource(_a.
				resourceIdFromAttr(R.attr.cb_button_bg));
		}
	}

	// Call this when the title is tapped on:
	public void handleTitleTap()
	{
		// TBD
	}
	
	// Get a unique ID for this node, based on its properties.  This ID is used by
	// SplitScreenWrapper to record which nodes are expanded when redrawing the screen.
	// Nodes that aren't expandable should not override this. A return value of ""
	// means the node is not expandable.
	public String getUniqueID()
	{
		return "";
	}
	
	// Highlight the node when tapped on:
	public void highlightNode()
	{
		parseXmlLayout();
		if (!_isHighlighted)
			_savedNormalColor = _counter.getCurrentTextColor();
		_isHighlighted = true;
		// _layout.setBackgroundResource(R.drawable.nav_drawer_text_highlight_bg);
		// _title.setTextColor(Util.resourceIdFromAttr(_context, R.attr.nav_drawer_text_highlight_color));
		Resources r = _a.getResources();
		_title.setTextColor(r.getColor(Util.resourceIdFromAttr(_a,R.attr.
			nav_drawer_text_highlight_color)));
		_layout.findViewById(R.id.nav_drawer_row_highlight_area).setBackgroundColor(r.getColor(Util.resourceIdFromAttr(_a,
			R.attr.nav_drawer_highlight_bg_color)));
		_counter.setTextColor(r.getColor(Util.resourceIdFromAttr(_a,R.attr.
			nav_drawer_text_highlight_color)));
		View rightShadowHolder = _layout.findViewById(R.id.nav_drawer_shadow_right_holder);
		int bottomPadding = rightShadowHolder.getPaddingBottom();
		int rightPadding = rightShadowHolder.getPaddingRight();
		int topPadding = rightShadowHolder.getPaddingTop();
		int leftPadding = rightShadowHolder.getPaddingLeft();
		rightShadowHolder.setBackgroundResource(Util.resourceIdFromAttr(_a, 
			R.attr.nav_drawer_shadow_right));
		rightShadowHolder.setPadding(leftPadding, topPadding, rightPadding, bottomPadding);
		if (_expander.getVisibility()==View.VISIBLE)
		{
			if (_isExpanded)
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(
					_a, R.attr.nav_expand_inv)));
			}
			else
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(
					_a, R.attr.nav_right_ptr_inv)));
			}
		}
		setIconInversion(true);
		setButtonInversion(true);
	}
	
	public void unhighlightNode()
	{
		parseXmlLayout();
		_isHighlighted = false;
		Resources r = _a.getResources();
		_layout.findViewById(R.id.nav_drawer_row_highlight_area).setBackgroundResource(0);
		_title.setTextColor(r.getColor(Util.resourceIdFromAttr(_a,R.attr.nav_drawer_text_color)));
		if (_savedNormalColor!=0)
			_counter.setTextColor(_savedNormalColor);
		else
		{
			// A fallback, which doesn't work on all devices. Don't know why.
			_counter.setTextColor(r.getColor(Util.resourceIdFromAttr(_a, R.attr.
				nav_drawer_counter_text_color)));
		}

		_layout.findViewById(R.id.nav_drawer_shadow_right_holder).setBackgroundColor(0);
		if (_expander.getVisibility()==View.VISIBLE)
		{
			if (_isExpanded)
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(
					_a, R.attr.nav_expand)));
			}
			else
			{
				_expander.setImageDrawable(_a.getResources().getDrawable(Util.resourceIdFromAttr(
					_a, R.attr.nav_right_ptr)));
			}
		}
		setIconInversion(false);
		setButtonInversion(false);
	}
	
	// Set the inversion state of the button.  By default, this does nothing (for nodes that have no
	// button on the right):
	public void setButtonInversion (boolean isInverted)
	{
		return;
	}
	
	// This function is used by subclasses to specify whether the node is unhighlighted when the user
	// moves his finger off of it.  By default, the node is unhighlighted.
	public boolean unhighlightOnRelease()
	{
		return true;
	}
	
	/** Flash this node.  Highlight it, and then unhighlight if needed. */
	@SuppressLint("NewApi")
	private void flashNode()
	{
		highlightNode();
		if (unhighlightOnRelease())
		{
			if (Build.VERSION.SDK_INT >= 11)
				new FlashNode().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			else
				new FlashNode().execute();
		}
	}
	
	/** An async task whose purpose is to remove the highlighting from a row after a user has tapped on
	 * it.  This is used to generate a flash effect. */
	private class FlashNode extends AsyncTask<Void,Void,Void>
	{
		protected Void doInBackground(Void... v)
		{
			try
    		{
    			Thread.sleep(250);
    		}
	    	catch (InterruptedException e)
	    	{
	    	}
    		return null;
		}
		
		protected void onPostExecute(Void v)
    	{
    		unhighlightNode();
    	}
	}
}
