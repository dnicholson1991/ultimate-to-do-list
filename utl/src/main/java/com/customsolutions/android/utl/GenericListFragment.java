package com.customsolutions.android.utl;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.fragment.app.ListFragment;
import androidx.core.view.MenuItemCompat;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

/** Base class for Fragments which display simple lists (such as contexts and folders) */
public class GenericListFragment extends UtlListFragment
{
	// Menu item ID for resizing panes:
	protected final int RESIZE_PANES_ID = Menu.FIRST;
	
	protected final static int CURSOR_LOADER = 0;
	
	// Variables for communicating with the Synchronizer service:
    protected Messenger mService = null;
    protected boolean mIsBound;
    protected final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    // Links to key views:
    protected LinearLayout _syncProgressContainer;
    protected ProgressBar _syncProgressBar;
    
    // Quick reference to key items:
    protected UtlNavDrawerActivity _a;
    protected Resources _res;
    protected SharedPreferences _settings;
    protected int _ssMode;
    protected ViewGroup _rootView;
    
    /** State variable.  If true, we have initiated a manual sync and are waiting on a response: */
    private boolean _waitingOnManualSync;
    
    /** The index of the last selected item: */
    private int _lastSelection = -1;
    
	/** This returns the view being used by this fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.generic_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
    	super.onActivityCreated(savedInstanceState);
    	
    	// Get quick references to key items:
    	_a = (UtlNavDrawerActivity)getActivity();
    	_res = _a.getResources();
        _settings = _a._settings;
        _ssMode = _a.getSplitScreenOption();
        _rootView = (ViewGroup)getView();

        initBannerAd(_rootView);
        
    	// Link to key views:
    	_syncProgressContainer = (LinearLayout)getView().findViewById(R.id.sync_status_progress_bar_container);
    	_syncProgressBar = (ProgressBar)getView().findViewById(R.id.sync_status_progress_bar);

    	// Process the savedInstanceState, if any:
    	if (savedInstanceState!=null)
    	{
    		if (savedInstanceState.containsKey("waiting_on_manual_sync"))
    			_waitingOnManualSync = savedInstanceState.getBoolean("waiting_on_manual_sync");
    		else
    			_waitingOnManualSync = false;
    	}
    	
    	AccountsDbAdapter db = new AccountsDbAdapter();
        Cursor c = db.getAllAccounts();
        if (!c.moveToFirst())
        {
        	// The user has managed to get here without setting up an account first.
        	c.close();
            Intent i = new Intent(_a,main.class);
            startActivity(i);
            _a.finish();
            return;
        }
        else
        	c.close();
        
        // This fragment will be updating the Action Bar:
        setHasOptionsMenu(true);

        // Add a footer view to the list, to ensure the + sign at the bottom doesn't hide the
        // last item's details.
        _a.addListViewFooter(getListView(),false);
    }
    
    /** Refresh the list.  This should be overwritten by subclasses. */
    public void refreshData()
    {
    	
    }
    
    /** Populate the action bar menu. */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
    	menu.clear();
    	inflater.inflate(R.menu.generic_list, menu);
    	
    	// If we're using split-screen views, then add in an option to resize the panes:
    	if (_ssMode != Util.SS_NONE && android.os.Build.VERSION.SDK_INT>=11)
    	{
    		MenuItemCompat.setShowAsAction(menu.add(0,RESIZE_PANES_ID,0,R.string.Resize_Panes),
    			MenuItemCompat.SHOW_AS_ACTION_NEVER);
    	}
    }
    
    /** Handlers for options menu choices.  Note that the "add" item must be handled by the subclass
     * The subclass method must include the line "return super.onOptionsItemSelected(item)" if it 
     * doesn't handle the menu item. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	Intent i;
    	
    	switch (item.getItemId())
    	{
    	case R.id.menu_generic_list_search:
    		i = new Intent(_a,QuickSearch.class);
    		i.putExtra("base_view_id", _settings.getLong(PrefNames.STARTUP_VIEW_ID, -1));
        	_a.startActivity(i);
    		return true;
    		
    	case R.id.menu_generic_list_sync_now:
    		startSync();
          	return true;
          	
    	case RESIZE_PANES_ID:
        	_a.enterResizeMode();
        	return true;
    	}
    	
    	return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	
    	refreshData();
    	restorePosition();
    	
    	if (Synchronizer.isSyncing() && _waitingOnManualSync)
        {
        	_syncProgressContainer.setVisibility(View.VISIBLE);
        }
        else
        {
        	_waitingOnManualSync = false;
        	_syncProgressContainer.setVisibility(View.GONE);
        }
    	
    	// Establish a link to the Synchronizer service:
        doBindService();
    }
    
    /** Subclasses that override this must call the superclass method */
    @Override
    public void onSaveInstanceState(Bundle b)
    {
    	super.onSaveInstanceState(b);
    	
    	b.putBoolean("waiting_on_manual_sync", _waitingOnManualSync);
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	
    	saveCurrentPosition();
    	
    	// Remove the link to the synchronizer service:
        doUnbindService();
    }
    
    /** Save the current position that we're scrolled to in the list */
    private void saveCurrentPosition()
    {
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
        _lastSelection = lv.getFirstVisiblePosition();
    }
    
    /** Restore the position that was saved: */
    private void restorePosition()
    {
    	ListView lv = (ListView)_rootView.findViewById(android.R.id.list);
        if (_lastSelection>-1 && _lastSelection<lv.getCount())
        {
        	lv.setSelection(_lastSelection);
        }
    }
    
    /** Start a manual sync: */
    public void startSync()
    {
		_waitingOnManualSync = true;
        _syncProgressContainer.setVisibility(View.VISIBLE);
    	if (!Synchronizer.isSyncing())
    	{
    		Intent i = new Intent(_a, Synchronizer.class);
    		i.putExtra("command", "full_sync");
    		i.putExtra("send_percent_complete", true);
            Synchronizer.enqueueWork(_a,i);
            _syncProgressBar.setProgress(0);
            Util.popup(_a,R.string.Sync_Started);     
    	}
    	else
    		Util.popup(_a, R.string.Sync_is_currently_running);
    }
    
    /**
     * Handler of incoming messages from service.
     */
    protected class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Synchronizer.SYNC_RESULT_MSG:
                    int result = msg.arg1;
                    int itemsDownloaded = msg.arg2;
                    if (_waitingOnManualSync)
                    {
                        _waitingOnManualSync = false;
                        if (result == Synchronizer.SUCCESS)
                        {
                            Util.popup(_a, R.string.Sync_Successful);
                        }
                        else
                        {
                            Util.popup(_a, Util.getString(R.string.Sync_Failed_)+
                                Synchronizer.getFailureString(result));
                        }
                        if (itemsDownloaded==1)
                        {
                        	saveCurrentPosition();
                            refreshData();
                            restorePosition();
                            _a.refreshWholeNavDrawer();
                        }
                    }
                    else
                    {
                        if (itemsDownloaded==1)
                        {
                            Util.popup(_a, R.string.Sync_Completed_Refreshing);
                            saveCurrentPosition();
                            refreshData();
                            restorePosition();
                            _a.refreshWholeNavDrawer();
                        }
                    }
                    _syncProgressContainer.setVisibility(View.GONE);
                    break;
                    
                case Synchronizer.PERCENT_COMPLETE_MSG:
                	// Update the progress bar:
                	int percentComplete = msg.arg1;
                	_syncProgressBar.setProgress((int) (_syncProgressBar.getMax()
    					* percentComplete / 100.0));
    				break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Class for interacting with the main interface of the service.
     */
    protected ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // We want to monitor the service for as long as we are
            // connected to it.
            try 
            {
                Message msg = Message.obtain(null,
                        Synchronizer.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
        }
    };

    protected void doBindService() 
    {
        // Establish a connection with the service.  We use an explicit
        // class name because there is no reason to be able to let other
        // applications replace our component.
    	if (!mIsBound)
    	{
    		_a.bindService(new Intent(_a, 
    			Synchronizer.class), mConnection, Context.BIND_AUTO_CREATE);
    		mIsBound = true;
    	}
    }

    protected void doUnbindService() 
    {
        if (mIsBound) 
        {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (mService != null) 
            {
                try 
                {
                    Message msg = Message.obtain(null,
                        Synchronizer.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } 
                catch (RemoteException e) 
                {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }

            // Detach our existing connection.
            _a.unbindService(mConnection);
            mIsBound = false;
        }
    }
}
