package com.customsolutions.android.utl;

// Fragment to Implement the Navigation Drawer.  Suitable for use within the navigation drawer, or
// as a separate fragment on the top or left of the screen.

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class NavDrawerFragment extends Fragment
{
	// References the LinearLayout holding the tree view:
	private LinearLayout _treeWrapper;
	
	// The parent Activity:
	private UtlNavDrawerActivity _a;
	
	// This list holds all of the TreeNode objects being displayed.  A TreeNode represents
	// a task, folder, context, view, etc.
	static private ArrayList<TreeNode> _nodes = new ArrayList<TreeNode>();

	// This is the node last tapped on (-1 if none):
	static public int _selectedNodeIndex = -1;
	
	// These ArrayList instances keep track of views that we need to get a count for:
	private ArrayList<TextView> _textViewList;
	private ArrayList<String> _queryList;
	
	// Keeps track of the last highlighted node:
	private TreeNode _lastHighlightedNode = null;
	private String _highlightedNodeID = null;
	
	// Quick reference to the root view of this fragment:
	private ViewGroup _rootView;
	
	static public int _scrollY = -1;
	
	// This returns the view being used by this fragment:
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	ViewGroup root = (ViewGroup)inflater.inflate(R.layout.nav_drawer_fragment, container, false);
    	UtlNavDrawerActivity a = (UtlNavDrawerActivity) getActivity();
    	if (a.getOrientation()==UtlActivity.ORIENTATION_PORTRAIT && (a.getSplitScreenOption()==
    		Util.SS_3_PANE || a.getSplitScreenOption()==Util.SS_2_PANE_NAV_LIST))
    	{
    		// In portrait orientation, we don't need the right shadow, and the 3 buttons at the bottom
    		// need stretched to the full screen width.
    		
    		LinearLayout ll = (LinearLayout)root.findViewById(R.id.nav_drawer_shadow_right_holder);
    		ll.setBackgroundResource(0);
    	}
    	return root;
    }

    // Called when the corresponding Activity is first created:
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	// Link to views we're interested in:
    	_a = (UtlNavDrawerActivity) this.getActivity();
    	_rootView = (ViewGroup)getView();
    	_treeWrapper = (LinearLayout)_rootView.findViewById(R.id.nav_drawer_tree_wrapper);
    	
		_textViewList = new ArrayList<TextView>();
		_queryList = new ArrayList<String>();

		if (savedInstanceState!=null && savedInstanceState.containsKey("highlighted_node"))
		{
			_highlightedNodeID = savedInstanceState.getString("highlighted_node");
		}
		
		// For certain languages, change the "sync now" button to "sync" in order to avoid
		// cutting off text.
		Locale l = Locale.getDefault();
		if (new Locale("de").getLanguage().equals(l.getLanguage()) ||
			new Locale("ru").getLanguage().equals(l.getLanguage()) ||
			new Locale("es").getLanguage().equals(l.getLanguage()))
		{
			TextView syncNow = (TextView)_rootView.findViewById(R.id.nav_drawer_sync_now);
			syncNow.setText(R.string.Sync);
		}
		
		// Detect a touch outside of the nav drawer:
		_rootView.findViewById(R.id.nav_drawer_shadow_right_holder).setOnTouchListener(new View.OnTouchListener()
		{
			@Override
			public boolean onTouch(View v, MotionEvent event)
			{
				return false;
			}
		});

		// Handlers for the buttons in the nav drawer that are always visible:
		_rootView.findViewById(R.id.nav_drawer_add_ons_wrap).setOnClickListener((View v) -> {
			// Launch the add-ons Activity:
			_a.startActivity(new Intent(_a, AddOnsActivity.class));
		});
		_rootView.findViewById(R.id.nav_drawer_settings_wrap).setOnClickListener((View v) -> {
			// Launch the settings activity:
			_a.startActivity(new Intent(_a, PrefsActivity.class));
		});
		_rootView.findViewById(R.id.nav_drawer_help_wrap).setOnClickListener((View v) -> {
			// Launch the Help Activity:
			_a.startActivity(new Intent(_a, Help.class));
		});
		_rootView.findViewById(R.id.nav_drawer_sync_now_wrap).setOnClickListener((View v) -> {
			// Get a reference to the list fragment being displayed.  If it supports
			// synchronization with a progress bar, then start a sync in the fragment:
			Fragment listFrag = _a.getFragmentByType(UtlNavDrawerActivity.FRAG_LIST);
			if (listFrag!=null)
			{
				if (listFrag instanceof TaskListFragment)
				{
					((TaskListFragment) listFrag).startSync();
				}
				else if (listFrag instanceof NoteListFragment)
				{
					((NoteListFragment) listFrag).startSync();
				}
				else if (listFrag instanceof GenericListFragment)
				{
					((GenericListFragment) listFrag).startSync();
				}
				else
				{
					if (!Synchronizer.isSyncing())
					{
						Intent i = new Intent(_a, Synchronizer.class);
						i.putExtra("command", "full_sync");
						i.putExtra("send_percent_complete", false);
						Synchronizer.enqueueWork(_a,i);
						Util.popup(_a,R.string.Sync_Started);
					}
					else
						Util.popup(_a, R.string.Sync_is_currently_running);
				}
				_a.closeDrawer();
			}
		});
    	super.onActivityCreated(savedInstanceState);
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
		regenerateNodes(); 

        if (_scrollY==-1)
        {
            // The app is starting up.  Get the Y scroll from storage:
            SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
            _scrollY = settings.getInt(PrefNames.NAV_DRAWER_SCROLL_Y,0);
        }

		if (_scrollY>0)
		{
			ScrollView sv = (ScrollView)_rootView.findViewById(R.id.nav_drawer_scrollview);
			sv.post(new Runnable() {
				@Override
				public void run()
				{
					ScrollView sv = (ScrollView)_rootView.findViewById(R.id.nav_drawer_scrollview);
					sv.scrollTo(0, _scrollY);
				}
			});
		}
    }
    
    // Redraw Nodes in the Tree of Views:
    public void redrawNodes()
    {
    	if (!_a.keepNodesHighlighted())
    	{
    		// Clear any existing highlighted node information.
    		_highlightedNodeID = null;
    		_lastHighlightedNode = null;
    	}
    	
    	_treeWrapper.removeAllViews();
		Iterator<TreeNode> it = _nodes.iterator();
		int i=0;
		while (it.hasNext())
		{
			TreeNode t = it.next();
			try
			{
				_treeWrapper.addView(t.getView());
			}
			catch (IllegalStateException e)
			{
				// This could happen if the activity is restarted and the tree views 
				// are still linked to the parent from the previous instance.
				ViewGroup vg = (ViewGroup)t.getView().getParent();
				vg.removeAllViews();
				_treeWrapper.addView(t.getView());
			}
			View expander = t.getExpanderView();
			if (expander!=null)
			{
				expander.setContentDescription(new Integer(i).toString());
				if (!t._isExpanded)
				{
					// The node is not expanded. Set up a click listener to expand it:
					setExpansionCallback(expander);
				}
				else
				{
					// The node is expanded.  Set up a listener to unexpand it:
					setContractionCallback(expander);
					
					// Update the node's expander graphic:
					t.setToExpanded();
				}
			}
			queueItemCount(t);
			if ((_highlightedNodeID!=null && t.getUniqueID().equals(_highlightedNodeID)) ||
				(_lastHighlightedNode!=null && t.getUniqueID().equals(_lastHighlightedNode.getUniqueID())))
			{
				if (_a.keepNodesHighlighted())
				{
					t.highlightNode();
					_highlightedNodeID = null;
					_lastHighlightedNode = t;
				}
			}
			i++;
		}
		
		// Make sure the current selected item is valid:
		if (_selectedNodeIndex>=_nodes.size())
			_selectedNodeIndex = _nodes.size()-1;
		
		// Scroll to the currently selected item and highlight it:
		if (_selectedNodeIndex>-1)
		{
			View v = _treeWrapper.getChildAt(_selectedNodeIndex);
			ScrollView sv = (ScrollView)_rootView.findViewById(R.id.nav_drawer_scrollview);
			// sv.requestChildFocus(_treeWrapper, v);
			if (_a.keepNodesHighlighted())
			{
				_nodes.get(_selectedNodeIndex).highlightNode();
				_highlightedNodeID = null;
				_lastHighlightedNode = _nodes.get(_selectedNodeIndex);
			}
		}
		else
		{
			// Scroll to the top node:
			View v = _treeWrapper.getChildAt(0);
			ScrollView sv = (ScrollView)_rootView.findViewById(R.id.nav_drawer_scrollview);
			// sv.requestChildFocus(_treeWrapper, v);
		}
    }
    
    // Reset the nodes, so that only the base nodes (all tasks, hotlist, etc) are in place:
    public void resetNodes()
    {
    	_nodes.clear();

        // Add in any Views that the user wants to show at the top:
        ViewsDbAdapter viewsDB = new ViewsDbAdapter();
        Cursor c = viewsDB.getTopLevelViews();
        while (c.moveToNext())
        {
            TreeNodeTaskList treeNodeTaskList = new TreeNodeTaskList(_a,ViewNames.MY_VIEWS,Util.cString(c, "view_name"),
                _a.getString(R.string.MyViews)+" / "+Util.cString(c, "view_name"),
                false, Util.cString(c, "view_name"));
            _nodes.add(treeNodeTaskList);
            treeNodeTaskList.showAtTopLevel();
        }
        c.close();

    	_nodes.add(new TreeNodeTasks(_a));
    	_nodes.add(new TreeNodeNotes(_a));
    	
		// All of the top level nodes have no parent.  Also set their indexes:
		Iterator<TreeNode> it = _nodes.iterator();
		int i = 0;
		while (it.hasNext())
		{
			TreeNode t = it.next();
			t._parentIndex = -1;
			t._index = i;
			i++;
		}		
    }

    // Regenerate nodes.  This should be called after any changes have been made to tasks,
 	// notes, folders, etc.  This regenerates the _nodes array and redraws the tree view.
    @TargetApi(11)
 	public void regenerateNodes()
 	{
        SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
 		HashSet<String> expandedNodes = new HashSet<String>();

        // If the app is starting, and we have expanded nodes in storage, get them:
        if (_nodes.size()==0 && settings.contains(PrefNames.NAV_DRAWER_EXPANDED_NODES) &&
            Build.VERSION.SDK_INT>=11)
        {
            // The app is starting, and we have expanded nodes in storage. Get them:
            expandedNodes = (HashSet<String>)settings.getStringSet(PrefNames.NAV_DRAWER_EXPANDED_NODES,null);
        }
        else
        {
            // Go through all existing nodes and make a hash to record which ones are expanded:
            Iterator<TreeNode> it = _nodes.iterator();
            while (it.hasNext()) {
                TreeNode t = it.next();
                if (t._isExpanded)
                    expandedNodes.add(t.getUniqueID());
            }
        }
 		
 		// Start with the initial top-level nodes:
 		resetNodes();
 		
 		// Go through all of the top level nodes, and perform expansions as needed:
 		for (int i=0; i<_nodes.size(); i++)
 		{
 			TreeNode t = _nodes.get(i);
 			if (expandedNodes.contains(t.getUniqueID()))
 				expandNode(t,expandedNodes);
 		}
 		
 		// Draw the nodes:
 		redrawNodes();
 	}

    // Refresh the task and note counts:
    public void refreshCounts()
    {
    	Iterator<TreeNode> it = _nodes.iterator();
		while (it.hasNext())
		{
			TreeNode t = it.next();
			queueItemCount(t);
		}
    }
    
	// Expand a node.  This operates on the _nodes array only.  It does not affect 
	// the display:
	private void expandNode(TreeNode t, HashSet<String> expandedNodes)
	{
		int i = t._index;
		
		// Get a list of new nodes:
		ArrayList<TreeNode> newNodes = t.getTreeNodesBelow();
		
		// Set the node to "expanded" (adjust the image, etc)
		t.setToExpanded();
		
		// Update the indexes stored in the node views below:
		int numNewNodes = newNodes.size();
		for (int j=i+1; j<_nodes.size(); j++)
		{
			_nodes.get(j)._index += numNewNodes;
			if (_nodes.get(j)._parentIndex>i)
				_nodes.get(j)._parentIndex += numNewNodes;
		}
		
		// Update the indexes stored in the new nodes, and link the new nodes to their
		// parent:
		for (int j=0; j<numNewNodes; j++)
		{
			newNodes.get(j)._index = i+1+j;
			newNodes.get(j)._parentIndex = i;
			newNodes.get(j)._isExpanded = false;			
		}
		
		// Insert the nodes into the node list:
		_nodes.addAll(i+1,newNodes);
	}

	private void setExpansionCallback(View expander)
	{
		expander.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				// Get a list of new nodes:
				int i = Integer.parseInt(v.getContentDescription().toString());
				ArrayList<TreeNode> newNodes = _nodes.get(i).getTreeNodesBelow();
				
				// Set the node to "expanded" (adjust the image, etc)
				_nodes.get(i).setToExpanded();
				
				// Update the indexes stored in the node views below:
				int numNewNodes = newNodes.size();
				for (int j=i+1; j<_nodes.size(); j++)
				{
					_nodes.get(j)._index += numNewNodes;
					View expander = _nodes.get(j).getExpanderView();
					if (expander!=null)
					{
						expander.setContentDescription(Integer.valueOf(j+numNewNodes).
							toString());
					}
					if (_nodes.get(j)._parentIndex>i)
					{
						_nodes.get(j)._parentIndex += numNewNodes;
					}
				}
				
				// Update the selected item, if necessary:
				if (_selectedNodeIndex>i)
					_selectedNodeIndex += numNewNodes;
				
				// Update the indexes stored in the new nodes, and set up the callbacks:
				for (int j=0; j<numNewNodes; j++)
				{
					newNodes.get(j)._index = i+1+j;
					View expander = newNodes.get(j).getExpanderView();
					if (expander!=null)
					{
						expander.setContentDescription(Integer.valueOf(i+1+j).
							toString());
						setExpansionCallback(expander);
					}
					
					// Link the new node to its parent:
					newNodes.get(j)._parentIndex = i;
					newNodes.get(j)._isExpanded = false;
				}
				
				// Insert the nodes into the node list:
				_nodes.addAll(i+1,newNodes);
				
				// Display the nodes:
				for (int j=i+1; j<i+1+numNewNodes; j++)
				{
					_treeWrapper.addView(_nodes.get(j).getView(), j);
					queueItemCount(_nodes.get(j));
				}
				
				// Set the contraction/close callback:
				setContractionCallback(_nodes.get(i).getExpanderView());				
			}
		});		
	}
	
	private void setContractionCallback(View expander)
	{
		expander.setOnClickListener(new View.OnClickListener()
		{	
			@Override
			public void onClick(View v)
			{
				int i = Integer.parseInt(v.getContentDescription().toString());
				contractNode(i);
			}
		});
	}
	
	// Contract a node at a specific index:
	private void contractNode(int i)
	{	
		int j=i+1;
		while (j<_nodes.size() && _nodes.get(j)._parentIndex==i)
		{
			if (_nodes.get(j)._isExpanded)
			{
				contractNode(j);
			}
			_nodes.remove(j);
			_treeWrapper.removeViewAt(j);
			for (int k=j; k<_nodes.size(); k++)
			{
				_nodes.get(k)._index = k;
				if (_nodes.get(k)._parentIndex>j)
					_nodes.get(k)._parentIndex--;
			}
			if (_selectedNodeIndex==j)
				_selectedNodeIndex = -1;
			if (_selectedNodeIndex>j)
				_selectedNodeIndex--;
		}
		_nodes.get(i).setToClosed();
		setExpansionCallback(_nodes.get(i).getExpanderView());
		for (j=i+1; j<_nodes.size(); j++)
		{
			View expander = _nodes.get(j).getExpanderView();
			if (expander !=null)
				expander.setContentDescription(Integer.valueOf(j).toString());
		}
	}

    // Queue up an item count (to display on right side of tree nodes):
    @SuppressLint("NewApi")
	private void queueItemCount(TreeNode t)
    {
    	TextView tv = t.getCounterTextView();
    	String query = t.getCounterQuery();
    	if (tv!=null && query!=null && query.length()>0)
    	{
    		_textViewList.add(tv);
    		_queryList.add(query);
    		
    		if (_textViewList.size()==1)
    		{
    			// Just added the first one, so we need to start the background jobs
    			// to run the query:
    			GetItemCountTask task = new GetItemCountTask();
    			if (Build.VERSION.SDK_INT >= 11)
    				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_queryList.get(0));
    			else
    				task.execute(_queryList.get(0));
    		}
    	}
    }
    
    // The AsyncTask instance, which gets the number of items for a node in the background.
    private class GetItemCountTask extends AsyncTask<String,Void,Integer>
    {
    	protected Integer doInBackground(String... queries)
    	{
    		Cursor c = Util.db().rawQuery(queries[0],null);
    		if (Util.regularExpressionMatch(queries[0], "select count"))
    		{
    			// Query is getting a count in the first row of the result:
    			c.moveToFirst();
    			int count = c.getInt(0);
        		c.close();
        		return count;
    		}
    		else
    		{
    			// The count to return is the number of rows.
    			int count = c.getCount();
    			c.close();
    			return count;
    		}
    	}
    	
    	@SuppressLint("NewApi")
		protected void onPostExecute(Integer count)
    	{
    		// Display the count in the appropriate TextView instance:
    		TextView tv = _textViewList.get(0);
    		_textViewList.remove(0);
    		_queryList.remove(0);
    		tv.setText(Integer.valueOf(count).toString());
    		tv.setVisibility(View.VISIBLE);
    		
    		// If we have another one, then start counting again:
    		if (_textViewList.size()>0)
    		{
    			GetItemCountTask task = new GetItemCountTask();
    			if (Build.VERSION.SDK_INT >= 11)
    				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_queryList.get(0));
    			else
    				task.execute(_queryList.get(0));
    		}
    	}
    }

    // Move the current highlighted node.  This is used when certain nodes remain highlighted after
    // being selected. The caller is responsible for performing the highlighting of the new node.
    public void moveHighlightedNode(TreeNode newNode)
    {
    	if (_lastHighlightedNode==null || !newNode.getUniqueID().equals(_lastHighlightedNode.
    		getUniqueID()))
    	{
    		if (_lastHighlightedNode!=null)
    			_lastHighlightedNode.unhighlightNode();
    		_lastHighlightedNode = newNode;
    	}
    }
    
    @Override
    public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	if (_lastHighlightedNode!=null)
    		b.putString("highlighted_node", _lastHighlightedNode.getUniqueID());
    }   
    
    @Override
    public void onPause()
    {
    	ScrollView sv = (ScrollView)_rootView.findViewById(R.id.nav_drawer_scrollview);
    	_scrollY = sv.getScrollY();
    	
    	super.onPause();
    }

    @Override
    public void onDestroy()
    {
        SharedPreferences settings = _a.getSharedPreferences(Util.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();

        // Save the expanded nodes, to use when the app is started again.
        if (Build.VERSION.SDK_INT>=11)
        {
            HashSet<String> expandedNodeUniqueIDs = new HashSet<String>();
            Iterator<TreeNode> it = _nodes.iterator();
            while (it.hasNext()) {
                TreeNode t = it.next();
                if (t._isExpanded)
                    expandedNodeUniqueIDs.add(t.getUniqueID());
            }
            editor.putStringSet(PrefNames.NAV_DRAWER_EXPANDED_NODES, expandedNodeUniqueIDs);
        }

        // Save the scroll position:
        editor.putInt(PrefNames.NAV_DRAWER_SCROLL_Y,_scrollY);
        editor.commit();

        super.onDestroy();
    }
}
