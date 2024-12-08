package com.customsolutions.android.utl;

// This class handles background syncing of local data with Toodledo and Google Tasks.

// Commands are issued via a Bundle in the Intent object, with the following keys/values:
// command: One of the following strings:
//     sync_item: Syncs a single item that the user has created, deleted, or edited.
//         This checks to see if the instant_upload preference is enabled, and does 
//         nothing if it is not.  It also ignores items for accounts that are not
//         synced with toodledo.
//     full_sync: Performs a full bidirectional sync for all accounts.
//     unlink_account: Unlinks an account from Toodledo

// Extra parameters if command is "sync_item":
//     operation: One of: ADD, DELETE, MODIFY, REASSIGN
//     item_type: One of: TASK, NOTE, FOLDER, CONTEXT, GOAL, TYPE_REASSIGN
//     item_id: The unique ID of the item in the local database
//         For a deletion, this is the Toodledo ID.  For others, it is the local DB ID.
//         Set this to -1 if this is a Google account and the op is a deletion.
//     account_id: Required for all items
//     remote_id: A string ID, which is used for Google accounts and gTask deletions. 
//         Not required for TD accounts or other ops.  If set, then set item_id=-1.
//     remote_tasklist_id: Included for deleting Google tasks and tasklists.
//
// Extra parameters if the command is "full_sync":
//     send_percent_complete: Boolean.  If true, send percent complete notifications to the
//         client.  Default is to send these messages.
//     is_scheduled: Boolean.  If true, then this is a scheduled sync.  If ommitted,
//         then this is a manual sync.
//
// Extra parameters if the command is "unlink_account":
//     account_id: The account ID to unlink.


import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PersistableBundle;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class Synchronizer extends IntentService
{
	private static final String TAG = "Synchronizer";

	// The maximum number of items we will display details for when syncing:
	public static final int MAX_DETAILED_ITEMS = 25;

	/** The unique Job ID for this service. */
	private static final int JOB_ID = 1535395262;

    // Codes for operations to perform:
    public static final int ADD = 1;
    public static final int DELETE = 2;
    public static final int MODIFY = 3;
    public static final int REASSIGN = 4;
    
    // Codes for item types:
    public static final int TASK = 1;
    public static final int NOTE = 2;
    public static final int FOLDER = 3;
    public static final int CONTEXT = 4;
    public static final int GOAL = 5;
    public static final int LOCATION = 6;
    public static final int TYPE_REASSIGN = 7;
    
    // Codes for sync results (the first 5 and last 4 must match the definitions in
    // ToodledoInterface)
    public static final int SUCCESS = 1;
    public static final int LOGIN_FAILURE = 2; // Username/password issue
    public static final int CONNECTION_FAILURE = 3; // No internet connection
    public static final int INTERFACE_ERROR = 4; // Toodledo gave an unexpected result
    public static final int INTERNAL_ERROR = 5; // Internal software failure
    public static final int DB_FAILURE = 6; // An error occurred when accessing the database from Synchronizer.
    // NOTE: INTERNAL_ERROR is returned if a database failure occurs in ToodledoInterface.
    //       DB_FAILURE occurs if a database failure occurs here.
    public static final int TOODLEDO_LOCK = 7; // Toodledo is locking out the user temporarily
    public static final int TOODLEDO_REJECT = 8; // Toodledo has rejected the sync operation
    public static final int INVALID_TOKEN = 10;  // Toodledo rejected the key.  Might be a password issue.
    public static final int TOO_MANY_REQUESTS_PER_TOKEN = 11;  // User issued too many API requests per token.  New token needed.
	public static final int MAX_TASKS_REACHED = 12; // TD's limit on the number of tasks has been reached.

	// Special sync result code that indicates there are no links to online accounts set up:
    public static final int NO_LINKS_ESTABLISHED = 10000;
    
    // Enumerations for the 2 directions of sync:
    private static final int UPLOAD = 1;
    private static final int DOWNLOAD =2;
    
    // How much to add to the percent complete, during each state of the sync process.
    // These must add up to 100%
    private static final int GET_ACCOUNT_INFO_PERCENT = 2;
    private static final int FOLDER_SYNC_PERCENT      = 2;
    private static final int CONTEXT_SYNC_PERCENT     = 2;
    private static final int GOAL_SYNC_PERCENT        = 2;
    private static final int TASK_DOWNLOAD_PERCENT    = 35;
    private static final int TASK_UPDATE_DB_PERCENT   = 35;
    private static final int TASK_UPLOAD_PERCENT      = 2;
    private static final int DELETED_TASKS_PERCENT    = 2;
    private static final int NOTES_DOWNLOAD_PERCENT   = 6;
    private static final int NOTES_UPDATE_DB_PERCENT  = 6;
    private static final int NOTES_UPLOAD_PERCENT      = 2;    
    private static final int DELETED_NOTES_PERCENT    = 2;
    private static final int OTHER_DELETED_PERCENT    = 2;
    
    // Google's percent complete values are different. These must add up to 100%:
    private static final int FOLDER_SYNC_PERCENT_GT    = 8;
    private static final int TASK_DOWNLOAD_PERCENT_GT  = 40;
    private static final int TASK_UPDATE_DB_PERCENT_GT = 40;
    private static final int TASK_UPLOAD_PERCENT_GT    = 4;
    private static final int DELETED_TASKS_PERCENT_GT  = 4;
    private static final int OTHER_DELETED_PERCENT_GT  = 4;
    
    // Enumerations for message types sent back to a client:
    public static final int SYNC_RESULT_MSG = 0;
    public static final int PERCENT_COMPLETE_MSG = 1;
    
    // We keep an instance of ToodledoInterface in place for each account:
    private HashMap<Long,ToodledoInterface> _interfaceHash;
    
    // We also keep an instance of GTasksInterface in place where needed:
    private HashMap<Long,GTasksInterface> _gTasksInterfaceHash;
    
    // This variable is true if the last full sync included items that were downloaded from
    // Toodledo:
    private boolean itemsDownloaded;
    
    // This keeps track of the time offset between the mobile and TD's server:
    private long localMinusTD;
    
    // A count of the number of items we have displayed details for:
    private int detailedItemCount;
    
    // The percent complete of a full sync operation:
    private int _percentComplete;
    
    // Flag indicating if a client is requesting percent complete notifications:
    private boolean _sendPercentComplete;
    
    // The number of accounts that sync:
    private int _numSyncedAccounts;
    
    // A reference to the settings/preferences.  We can't use the reference in Util
    // due to separate threads accessing this.
    private SharedPreferences settings;
    
    // These store temporary data on tags as tasks are downloading:
    ArrayList<String> _orderedTags;
    TreeSet<String> _tagSet;
    
    // This stores the sync service that was last accessed.  It is used for generating
    // an appropriate error message.  This is one of the values defined in UTLAccount:
    static private int _lastSyncService;
    
    // If a sync is in progress, this is the date/time it started.  If no sync is currently
    // occurring, then this is zero:
    static private long lastSyncStartTime = 0;

    /** This provides a reference to the current instance of the class. */
    static public Synchronizer currentInstance = null;

    /** A counter used for generating job IDs. */
    static public int _jobCounter = 0;

    /** A semaphore for controlling access to job scheduling. */
    static public SinglePermitSemaphore _jobSchedulingSemaphore = new SinglePermitSemaphore();

    /** Convenience method for enqueuing work to this service. */
    static void enqueueWork(final Context context, final Intent work)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    _jobSchedulingSemaphore.acquire();
                }
                catch (InterruptedException e) { }
                enqueueWork2(context, work);
            }
        }).start();
    }

	static private void enqueueWork2(Context context,Intent work)
	{
		Util.log("enqueueWork: "+Util.intentToString(work,2));

		// The extras that form the sync command need converted from a Bundle to a
		// Persistablebundle:
		PersistableBundle pBundle = new PersistableBundle();
		if (work.getExtras()!=null)
		{
			for (String key : work.getExtras().keySet())
			{
				switch (key)
				{
					// String types:
					case "command":
					case "remote_id":
					case "remote_tasklist_id":
						pBundle.putString(key,work.getStringExtra(key));
						break;

					// Integer types:
					case "operation":
					case "item_type":
						pBundle.putInt(key,work.getIntExtra(key,0));
						break;

					// Long types:
					case "account_id":
					case "item_id":
						pBundle.putLong(key,work.getLongExtra(key,0));
						break;

					// Boolean types:
					case "send_percent_complete":
					case "is_scheduled":
						pBundle.putInt(key,work.getBooleanExtra(key,false) ? 1 : 0);
						break;
				}
			}
		}

		// Create and schedule the job to run immediately:
		JobInfo.Builder builder = new JobInfo.Builder(Util.JOB_ID_SYNC_COMMANDS+_jobCounter,
			new ComponentName(context,SynchronizerJobService.class))
			.setOverrideDeadline(1)
			.setPersisted(false)
			.setExtras(pBundle)
			;
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.P)
			builder.setImportantWhileForeground(true);
		else
		    builder.setMinimumLatency(1);
		@SuppressLint("WrongConstant")
		JobScheduler scheduler = (JobScheduler)context.getSystemService(Context.
			JOB_SCHEDULER_SERVICE);
		if (scheduler==null)
		{
			Util.log("WARNING: JobScheduler is null.");
            _jobSchedulingSemaphore.release();
			return;
		}
		int result = scheduler.schedule(builder.build());
		if (result==JobScheduler.RESULT_SUCCESS)
		{
			Util.log("Job for synchronizer scheduled successfully.");
			_jobCounter++;
		}
		else
		{
			Util.log("WARNING: The synchronizer job could not be scheduled. Result: " +
				result);
			_jobSchedulingSemaphore.release();
		}
	}

    // Check to see if a sync is underway.
    static boolean isSyncing()
    {
    	if (lastSyncStartTime<(System.currentTimeMillis()-60*60*1000))
    		return false;
    	else
    		return true;
    }
    
    // Given an error code, get a string for the code:
    public static String getFailureString(int code)
    {
    	if (code==NO_LINKS_ESTABLISHED)
    		return Util.getString(R.string.Not_linked_to_online_account);
    	
    	if (_lastSyncService==UTLAccount.SYNC_TOODLEDO)
    	{
	        switch (code)
	        {
	          case LOGIN_FAILURE:
	            return Util.getString(R.string.Bad_username_or_password);
	          case CONNECTION_FAILURE:
	            return Util.getString(R.string.Cannot_connect_to_TD);
	          case INTERFACE_ERROR:
	            return Util.getString(R.string.Bad_respone_from_TD);
	          case INTERNAL_ERROR:
	            return Util.getString(R.string.Internal_error);
	          case DB_FAILURE:
	            return Util.getString(R.string.DB_failure);
	          case TOODLEDO_LOCK:
	        	return Util.getString(R.string.Toodledo_Lock);
	          case TOODLEDO_REJECT:
	        	  if (ToodledoInterface._lastRejectMessage!=null)
	        		  return ToodledoInterface._lastRejectMessage;
	        	  else
	        		  return Util.getString(R.string.Toodledo_Reject);
	          case INVALID_TOKEN:
	        	  return Util.getString(R.string.toodledo_sign_in_needed);
              case TOO_MANY_REQUESTS_PER_TOKEN:
                  return Util.getString(R.string.toodledo_rate_limit);
	        }
    	}
    	else if (_lastSyncService==UTLAccount.SYNC_GOOGLE)
    		return GTasksInterface.getErrorMessage(code);
    	
        return("");
    }
    
    public Synchronizer()
    {
		super("Synchronizer");

        _interfaceHash = new HashMap<Long,ToodledoInterface>(); 
        _gTasksInterfaceHash = new HashMap<Long,GTasksInterface>();
        localMinusTD = 0;
        _orderedTags = new ArrayList<String>();
        _tagSet = new TreeSet<String>();
    }
    
    @Override
    protected void onHandleIntent(Intent intent)
    {
    	// Initialize the app if needed:
    	Util.appInit(this);
		currentInstance = this;
		Util.log("Synchronizer: intent received: "+Util.intentToString(intent,2));
    	
    	// Catch any uncaught exceptions:
    	Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler(this));
    	
        // Get a reference to the user preferences/settings.  We can't use the
        // reference in Util due to access by separate threads.
        settings = this.getSharedPreferences("UTL_Prefs",0);

        Bundle b = intent.getExtras();
        if (!b.containsKey("command"))
        {
            Util.log("Synchronizer is ignoring Intent with no command.");
            return;
        }
        
        if (b.getString("command").equals("unlink_account"))
        {
        	// Clear out any data structures related to syncing:
        	if (b.containsKey("account_id"))
        	{
        		long accountID = b.getLong("account_id");
        		if (_interfaceHash.containsKey(accountID))
        		{
        			_interfaceHash.remove(accountID);
        		}
        		if (_gTasksInterfaceHash.containsKey(accountID))
        			_gTasksInterfaceHash.remove(accountID);
        	}
        }

        if (b.getString("command").equals("full_sync"))
        {
            boolean itemsDownloadedFromAnyAccount = false;

            boolean hasSem = Util.acquireSemaphore("Synchronizer", this);
            try
        	{
	        	// See if the caller wants to have percent complete messages sent:
	        	_sendPercentComplete = true;
	        	if (b.containsKey("send_percent_complete"))
	        	{
	        		_sendPercentComplete = b.getBoolean("send_percent_complete");
	        	}
	        	
	        	// In order to get an accurate percent complete, we need to know how many synced
	        	// accounts we have:
	        	_numSyncedAccounts = 0;
	            AccountsDbAdapter accountsDB = new AccountsDbAdapter();
	            Cursor c = accountsDB.getAllAccounts();
	            while (c.moveToNext())
	            {
	            	UTLAccount a = accountsDB.getUTLAccount(c);
	                if (a.sync_service==UTLAccount.SYNC_GOOGLE || a.sync_service==UTLAccount.
	                	SYNC_TOODLEDO)
	                {
	                	_numSyncedAccounts++;
	                }
	            }
	            
	            if (b.containsKey("is_scheduled") && b.getBoolean("is_scheduled"))
	        	{
	            	// This is a scheduled sync (not a manual one).  
	            	Util.log("Scheduled Sync Triggered");
	            	if (settings.getInt(PrefNames.SYNC_INTERVAL,60)>0 && settings.getBoolean(PrefNames.
	            		AUTO_SYNC, true))
	            	{
	                    // Record the sync time:
	                    Util.updatePref(PrefNames.LAST_AUTO_SYNC_TIME, System.currentTimeMillis(),
	                    	this);
	                	
	                    if (Util.getSyncPolicy()==Util.SCHEDULED_SYNC)
	                    {
			            	// We need to make sure the next sync is scheduled:
							Util.scheduleAutomaticSync(this,false);
	                    }
	                }
	            	else
	            	{
	            		// The user must have turned off scheduled syncing, so we quit
	            		// without doing anything.
	            		c.close();
	            		return;
	            	}
	        	}
	            
	            // Run a full sync for all accounts that link to Toodledo or Google:
	            lastSyncStartTime = System.currentTimeMillis();
	        	detailedItemCount = 0;
	            int result = NO_LINKS_ESTABLISHED;
	            _percentComplete = 0;
	            c.moveToPosition(-1);
	            int syncCount = 0;
				Intent authFixIntent = null;
	            while (c.moveToNext())
	            {
	                UTLAccount a = accountsDB.getUTLAccount(c);
	                
	                if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
	                {
	                	_lastSyncService = UTLAccount.SYNC_TOODLEDO;
	                    result = doFullSync(a._id);
	                    if (itemsDownloaded)
	                    {
	                        itemsDownloadedFromAnyAccount = true;
	                    }
	                    if (result != SUCCESS)
	                    {
	                        break;
	                    }
	                }  
	                else if (a.sync_service==UTLAccount.SYNC_GOOGLE)
	                {
	                	_lastSyncService = UTLAccount.SYNC_GOOGLE;
	                	result = doFullGoogleSync(a._id);
	                	if (itemsDownloaded)
	                        itemsDownloadedFromAnyAccount = true;
	                    if (result != GTasksInterface.SUCCESS)
						{
							if (result==GTasksInterface.NEEDS_USER_PERMISSION)
							{
								authFixIntent = GTasksInterface.getAuthFixIntent();
							}
							break;
						}
	                }
	                else
	                	continue;
	
	                // Set the percent complete:
	                syncCount++;
	                if (_numSyncedAccounts==0)
	                	_percentComplete = 100;
	                else
	                	_percentComplete = 100*syncCount/_numSyncedAccounts;
	                this.sendPercentComplete(0);
	            }
	            c.close();
	            lastSyncStartTime = 0;
	            
	            // If anyone is bound to this service, notify them of the sync
	            // result and whether or not any items were downloaded.
	            for (int i=mClients.size()-1; i>=0; i--)
	            {
	                try
	                {
						Message m = Message.obtain(null, SYNC_RESULT_MSG, result,
							itemsDownloadedFromAnyAccount ? 1 : 0);
						if (authFixIntent!=null)
						{
							Bundle bundle = new Bundle();
							bundle.putParcelable("intent",authFixIntent);
							m.setData(bundle);
						}
	                    mClients.get(i).send(m);
	                }
	                catch (RemoteException e)
	                {
	                    mClients.remove(i);
	                }
	            }
        	}
        	finally
        	{
        		if (hasSem)
        			Util._semaphore.release();
        	}
            
            if (itemsDownloadedFromAnyAccount)
            {
            	// Update any widgets if necessary:
            	Util.updateWidgets();
            }
        }
        
        if (b.getString("command").equals("sync_item"))
        {
        	if (!settings.getBoolean("instant_upload", true))
        	{
        		// Instant upload is off.  Nothing to do.
        		return;
        	}
        	
        	// Verify required inputs are present:
        	if (!b.containsKey("operation") || !b.containsKey("item_type") ||
        		!b.containsKey("item_id") || !b.containsKey("account_id"))
        	{
        		Util.log("Instant upload operation is missing required input(s).");
        		return;
        	}
        	
        	long accountID = b.getLong("account_id");
        	int operation = b.getInt("operation");
        	int itemType = b.getInt("item_type");
        	long itemID = b.getLong("item_id");
        	
        	// Get account info and verify this is a sync account:
        	UTLAccount a = (new AccountsDbAdapter()).getAccount(accountID);
        	if (a==null || a.sync_service==UTLAccount.SYNC_NONE)
        	{
        		return;
        	}

        	if (itemID==-1 && a.sync_service==UTLAccount.SYNC_TOODLEDO)
        	{
        		// The item never reached Toodledo.  Nothing to do.
        		return;
        	}
        	
        	Util.log("Begin Instant Upload: account: "+accountID+"  operation: "+operation+
        		"  itemType: "+itemType+"  itemID: "+itemID);
        	
        	if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
        	{
	        	// Get a reference to the ToodledoInterface:
	            if (!_interfaceHash.containsKey(accountID))
	            {
	                _interfaceHash.put(accountID, new ToodledoInterface(accountID,this));
	            }
	            ToodledoInterface tdInterface = _interfaceHash.get(accountID);
	                    	
	        	if (operation==ADD)
	        	{
	        		switch (itemType)
	        		{
	        		  case TASK:
	        			instantAddTask(itemID,tdInterface);	
	        			break;
	        			
	        		  case NOTE:
	        			instantAddNote(itemID,tdInterface);
	        			break;
	        			
	        		  case FOLDER:
	        			instantAddFolder(itemID,tdInterface);
	        			break;
	        		
	        		  case CONTEXT:
	        			  instantAddContext(itemID,tdInterface);
	        			break;
	        			
	        		  case GOAL:
	        			  instantAddGoal(itemID,tdInterface);
	        			break;
	        			
	        		  case LOCATION:
	        			  instantAddLocation(itemID,tdInterface);
	        			break;
	        		}
	        	}
	        	else if (operation==MODIFY)
	        	{
	        		switch (itemType)
	        		{
	        		  case TASK:
	        			instantEditTask(itemID,tdInterface);
	        			break;
	        			
	        		  case NOTE:
	        			instantEditNote(itemID,tdInterface);
	        			break;
	        			
	        		  case FOLDER:
	        			instantEditFolder(itemID,tdInterface);
	        			break;
	        		
	        		  case CONTEXT:
	        			instantEditContext(itemID,tdInterface);
	        			break;
	        			
	        		  case GOAL:
	          			instantEditGoal(itemID,tdInterface);
	        			break;
	        			
	        		  case LOCATION:
	          			instantEditLocation(itemID,tdInterface);
	        			break;
	        		}        		
	        	}
	        	else if (operation==DELETE)
	        	{
	        		switch (itemType)
	        		{
	        		  case TASK:
	        			instantDeleteTask(itemID,accountID,tdInterface);
	        			break;
	        			
	        		  case NOTE:
	        			instantDeleteNote(itemID,accountID,tdInterface);
	        			break;
	        			
	        		  case FOLDER:
	        			instantDeleteFolder(itemID,accountID,tdInterface);
	        			break;
	        		
	        		  case CONTEXT:
	        			instantDeleteContext(itemID,accountID,tdInterface);
	        			break;
	        			
	        		  case GOAL:
	          			instantDeleteGoal(itemID,accountID,tdInterface);
	        			break;
	        			
	        		  case LOCATION:
	            		instantDeleteLocation(itemID,accountID,tdInterface);
	          			break;
	        		}        		
	        	}
	        	else if (operation==REASSIGN)
	        	{
	        		instantReassignTask(itemID,tdInterface);
	        	}
        	}
        	else if (a.sync_service==UTLAccount.SYNC_GOOGLE)
        	{
        		// Get a reference to the GTasksInterface object:
        		if (!_gTasksInterfaceHash.containsKey(accountID))
        		{
        			_gTasksInterfaceHash.put(accountID, new GTasksInterface(this,a));
        		}
        		GTasksInterface gtInterface = _gTasksInterfaceHash.get(accountID);
        		
        		if (operation==ADD)
        		{
        			switch (itemType)
        			{
        			case TASK:
        				instantAddGoogleTask(itemID,gtInterface);
        				break;
        			case FOLDER:
        				instantAddGoogleFolder(itemID,gtInterface);
        				break;
        			}
        		}
        		else if (operation==MODIFY)
        		{
        			switch (itemType)
        			{
        			case TASK:
        				instantEditGoogleTask(itemID,gtInterface);
        				break;
        			case FOLDER:
        				instantEditGoogleFolder(itemID,gtInterface);
        				break;
        			}
        		}
        		else if (operation==DELETE)
        		{
        			if (!b.containsKey("remote_id") && itemType==TASK)
        			{
        				Util.log("Missing remote_id field.");
        				return;
        			}
        			String gID = b.getString("remote_id");
        			if (!b.containsKey("remote_tasklist_id"))
        			{
        				Util.log("Missing remote_tasklist_id.");
        				return;
        			}
        			String gFolderID = b.getString("remote_tasklist_id");
        			
        			switch (itemType)
        			{
        			case TASK:
        				instantDeleteGoogleTask(gID,gFolderID,gtInterface,accountID);
        				break;
        			case FOLDER:
        				instantDeleteGoogleFolder(gFolderID,gtInterface,accountID);
        				break;
        			}
        		}
        	}
        }
    }

    //
    // Interface to allow Activity instances to communicate with this:
    //

    @Override
    public IBinder onBind(Intent i) {
    	Util.log("onBind() called.");
		currentInstance = this;
		return mMessenger.getBinder();
    }

    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();

    /**
     * Command to the service to register a client, receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client where callbacks should be sent.
     */
    static final int MSG_REGISTER_CLIENT = 1;

    /**
     * Command to the service to unregister a client, to stop receiving callbacks
     * from the service.  The Message's replyTo field must be a Messenger of
     * the client as previously given with MSG_REGISTER_CLIENT.
     */
    static final int MSG_UNREGISTER_CLIENT = 2;
    
    // Incoming message handler:
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    
    // ------------------------
    
    // Perform a full synchronization for a specific account:
    private int doFullSync(long accountID)
    {
        TasksDbAdapter tasksDB = new TasksDbAdapter();

        // Get the account information from the database:
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        UTLAccount utlAccount = accountsDB.getAccount(accountID);
        if (utlAccount==null)
        {
            Util.log("Bad account ID passed into Synchronizer.doFullSync");
            return INTERNAL_ERROR;
        }
        Util.log("Starting full sync for account "+accountID+" ("+utlAccount.name+") "+
        	"with e-mail "+utlAccount.td_email);
        
        // Get a reference to the ToodledoInterface:
        if (!_interfaceHash.containsKey(accountID))
        {
            _interfaceHash.put(accountID, new ToodledoInterface(accountID,this));
        }
        ToodledoInterface tdInterface = _interfaceHash.get(accountID);
        
        // Get information on the Toodledo account:
        HashMap<String,String> accountInfo = new HashMap<String,String>();
        int result = tdInterface.getAccountInfo(accountInfo);
        if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Got the following error when retrieving account info: "+
                ToodledoInterface.getFailureString(result));
            return result;
        }
        
        // Record the time difference between us and Toodledo's server:
        long serverTime = Long.parseLong(accountInfo.get("server_time"));
        if (serverTime>0)
        {
        	localMinusTD = (System.currentTimeMillis() - serverTime)/1000;
        	Util.log("Local time is "+localMinusTD+" seconds ahead of Toodledo.");
        }
        else
        {
        	localMinusTD = 0;  // TD didn't send the time.  We're taking a chance here.
        }
        
        // Update the account's info in the database:
        utlAccount = accountsDB.getAccount(accountID);  // Need to refresh.
        if (!accountInfo.get("pro").equals("0"))
        {
        	Util.log("Pro account detected.");
            utlAccount.pro = true;
        }
        else
        {
            utlAccount.pro = false;
        }
        utlAccount.time_zone = Long.parseLong(accountInfo.get("timezone"));
        utlAccount.hide_months = Integer.parseInt(accountInfo.get("hidemonths"));
        utlAccount.hotlist_priority = Integer.parseInt(accountInfo.get("hotlistpriority"))+2;
        utlAccount.hotlist_due_date = Integer.parseInt(accountInfo.get("hotlistduedate"))-1;
        boolean isSuccessful = accountsDB.modifyAccount(utlAccount);
        if (!isSuccessful)
        {
            Util.log("Could not update account info in database.");
            return DB_FAILURE;
        }
        
        // If the hotlist preferences have not been set, then set them based on 
        // the account information:
        if (!settings.contains("hotlist_priority"))
        	Util.updatePref("hotlist_priority", utlAccount.hotlist_priority,this);
        if (!settings.contains("hotlist_due_date"))
        	Util.updatePref("hotlist_due_date", utlAccount.hotlist_due_date,this);
        
        // Record the times of the last add/edit for tasks, folders, etc.  These are SECOND
        // timestamps (not millisecond).  Also, convert this to local time.
        long lastTaskAddEdit = Long.parseLong(accountInfo.get("lastedit_task"))+localMinusTD;
        long lastTaskDelete = Long.parseLong(accountInfo.get("lastdelete_task"))+localMinusTD;
        long lastFolderEdit = Long.parseLong(accountInfo.get("lastedit_folder"))+localMinusTD;
        long lastContextEdit = Long.parseLong(accountInfo.get("lastedit_context"))+localMinusTD;
        long lastGoalEdit = Long.parseLong(accountInfo.get("lastedit_goal"))+localMinusTD;
        long lastLocEdit = Long.parseLong(accountInfo.get("lastedit_location"))+localMinusTD;
        
        // Unless we determine otherwise, no items have been downloaded from Toodledo:
        itemsDownloaded = false;

        // Fetch information on any collaborators, and update the database:
        ArrayList<HashMap<String,String>> collaborators = new ArrayList<HashMap<String,String>>();
        result = tdInterface.getCollaborators(collaborators);
        if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Got the following error when retrieving collaborator info: "+
                ToodledoInterface.getFailureString(result));
            return result;
        }
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        cdb.deleteAll(accountID);
        Iterator<HashMap<String,String>> cIterator = collaborators.iterator();
        while (cIterator.hasNext())
        {
        	HashMap<String,String> cHash = cIterator.next();
        	UTLCollaborator uc = new UTLCollaborator();
        	uc.account_id = utlAccount._id;
        	uc.remote_id = cHash.get("id");
        	uc.name = cHash.get("name");
        	uc.reassignable = cHash.get("reassignable").equals("1") ? true : false;
        	uc.sharable = cHash.get("sharable").equals("1") ? true : false;
        	cdb.addCollaborator(uc);
        }
        
        // If any collaborators were fetched, when we also need to include myself as a collaborator.
        // This makes future database lookups much easier.
        if (collaborators.size()>0)
        {
        	UTLCollaborator uc = new UTLCollaborator();
        	uc.account_id = utlAccount._id;
        	uc.remote_id = utlAccount.td_userid;
        	uc.name = Util.getString(R.string.Myself);
        	uc.reassignable = true;
        	uc.sharable = true;
        	cdb.addCollaborator(uc);
        }
        
        sendPercentComplete(GET_ACCOUNT_INFO_PERCENT/_numSyncedAccounts);
        
        //
        // Folder Sync:
        //
        
        // This keeps track of folders that were sent to toodledo via an add or edit:
        HashSet<Long> foldersUploaded = new HashSet<Long>();
        
        // This keeps track of folders that were deleted from Toodledo:
        HashSet<Long> foldersDeletedFromTD = new HashSet<Long>();

        // Check the modification dates for the folders to see if any changes need to be
        // uploaded.
        FoldersDbAdapter foldersDB = new FoldersDbAdapter();
        long syncThreshold = utlAccount.last_sync - (10*60*1000);
        if (syncThreshold<0)
        {
            syncThreshold=0;
        }
        Cursor c = null;
        int count;
        try
        {
	        c = foldersDB.queryFolders("td_id>-1 and mod_date>sync_date"+
	            " and account_id="+accountID,null);
	        count = 0;
	        while (c.moveToNext())
	        {
	            result = tdInterface.editFolder(c);
	            if (result != ToodledoInterface.SUCCESS)
	            {
	                Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
	                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
	                    ToodledoInterface.getFailureString(result));
	                if (ToodledoInterface._lastErrorCode==4)
	                {
	                	// This means that Toodledo thinks the folder does not exist.
	                	// Remove the TD ID so that it will be added in the code below.
	                	Util.log("We will try adding this instead.");
	                	foldersDB.modifyTDID(Util.cLong(c, "_id"), -1);
	                	continue;
	                }
	                else
	                	return result;
	            }
	            count++;
	            foldersUploaded.add(Util.cLong(c,"_id"));
	            this.logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
	            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
	        }
        }
        finally
        {
        	c.close();
        }
        Util.log("Uploaded "+count+" edited folders to Toodledo.");

        // Look for folders that have not been uploaded to Toodledo, and upload them.
        c = foldersDB.queryFolders("td_id=-1 and account_id="+accountID, null);
        count = 0;
        while (c.moveToNext())
        {   
            result = tdInterface.addFolder(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the folder because it already exists.  Delete the folder 
                	// locally and update any tasks that have it.
                	long rejectedFolder = Util.cLong(c, "_id");
                	Util.log("Removing the rejected folder and updating tasks.");                	
                	Cursor c2 = tasksDB.queryTasks("folder_id="+rejectedFolder,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.folder_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	foldersDB.deleteFolder(rejectedFolder);
                }
                else
                {
                	c.close();
                	return result;
                }
            }
            count++;
            foldersUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, ADD, FOLDER, Util.cString(c, "title"));
            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" new folders to Toodledo.");
        
        // If a change occurred on Toodledo recently, then we need to download folders:
        PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
        if (lastFolderEdit>syncThreshold/1000)
        {
            Util.log("Toodledo is reporting folder changes.  Downloading folders...");
            ArrayList<HashMap<String,String>> tdFolders = new ArrayList<HashMap<String,String>>();
            result = tdInterface.getFolders(tdFolders);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not get folder list from Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                return result;
            }
            
            // Look for changed folders, and folders that were added from Toodledo:
            Iterator<HashMap<String,String>> it = tdFolders.iterator();
            int addedCount = 0;
            int modifiedCount = 0;
            HashSet<Long> downloadedFolderIDs = new HashSet<Long>();
            while (it.hasNext())
            {
                HashMap<String,String> folderHash = it.next();
                long TDID = Long.parseLong(folderHash.get("id"));
                downloadedFolderIDs.add(TDID);
                try
                {
	                c = foldersDB.getFolder(accountID, TDID);
	                if (!c.moveToFirst())
	                {
	                    // We have a folder that exists in Toodledo, but not locally.
	                    if (0==deletesDB.isDeletePending("folder", accountID, TDID))
	                    {
	                        // The folder is not being deleted locally, so this was added 
	                        // from Toodledo.  Add it locally also.
	                        long folderID = foldersDB.addFolder(TDID, accountID, folderHash.get
	                            ("name"), Util.stringToBoolean(folderHash.get("archived")),
	                            Util.stringToBoolean(folderHash.get("private")));
	                        if (folderID==-1)
	                        {
	                            Util.log("Could not insert new folder from Toodledo into database.");
	                            return DB_FAILURE;
	                        }
	                        else
	                        {
	                            foldersDB.updateOrdering(folderID, Integer.parseInt(folderHash.get
	                                ("ord")));
	                            addedCount++;
	                            this.logSyncedItem(DOWNLOAD, ADD, FOLDER, folderHash.get
	                                ("name"));
	                            foldersDB.setSyncDate(folderID,System.currentTimeMillis());
	                        }
	                    }
	                }
	                else
	                {
	                    // The folder exists in both places.  Check for differences:
	                    if (!foldersUploaded.contains(Util.cLong(c, "_id")) && (
	                        !Util.cString(c, "title").equals(folderHash.get("name")) ||
	                        Util.cInt(c, "archived")!=Integer.parseInt(folderHash.get("archived")) ||
	                        Util.cInt(c, "ordering")!=Integer.parseInt(folderHash.get("ord")) ||
	                        Util.cInt(c, "is_private")!=Integer.parseInt(folderHash.get("private"))))
	                    {
	                        if (Util.cLong(c, "mod_date")>Util.cLong(c, "sync_date"))
	                        {
	                            // The folders differ and the local copy was modified since the
	                            // last sync.  This is unlikely to happen, but it can happen.
	                            // if the user makes a folder change in between the uploading
	                            // of edits and this code.
	                            result = tdInterface.editFolder(c);
	                            if (result != ToodledoInterface.SUCCESS)
	                            {
	                                Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
	                                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
	                                    ToodledoInterface.getFailureString(result));
	                                return result;
	                            }
	                            foldersUploaded.add(Util.cLong(c,"_id"));   
	                            this.logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
	                            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
	                        }
	                        else
	                        {
	                            // The folder has not been modified since the last sync, so
	                            // it must have changed from Toodledo.  Update the local copy.
	                            isSuccessful = foldersDB.modifyFolder(Util.cLong(c,"_id"), 
	                                Util.cLong(c,"td_id"), accountID, folderHash.get("name"),
	                                Util.stringToBoolean(folderHash.get("archived")),
	                                Util.stringToBoolean(folderHash.get("private")));
	                            if (!isSuccessful)
	                            {
	                                Util.log("Could not modify folder that was downloaded "+
	                                    "from Toodledo.  DB Update failed.");
	                                return DB_FAILURE;
	                            }
	                            else
	                            {
	                                foldersDB.updateOrdering(Util.cLong(c,"_id"), Integer.
	                                    parseInt(folderHash.get("ord")));
	                                modifiedCount++;
	                                this.logSyncedItem(DOWNLOAD, MODIFY, FOLDER, folderHash.get
	                                    ("name"));
	                                foldersDB.setSyncDate(Util.cLong(c,"_id"),System.
	                                	currentTimeMillis());
	                            }
	                        }
	                    }
	                }
                }
                finally
                {
                	c.close();
                }
            }
            Util.log("Folders from Toodledo added locally: "+addedCount);
            Util.log("Folders from Toodledo modified locally: "+modifiedCount);      
            if (addedCount>0 || modifiedCount>0)
            {
                itemsDownloaded = true;
            }
            
            // Look for folders that exist locally but aren't on Toodledo.
            c = foldersDB.queryFolders("account_id="+accountID+" and td_id>-1", null);
            count = 0;
            while (c.moveToNext())
            {
                if (!downloadedFolderIDs.contains(Util.cLong(c, "td_id")))
                {
                    // The folder exists locally but is not on Toodledo.  We also know that
                    // it has been previously sent to Toodledo because the td_id field is
                    // greater than -1.  In this case, we know that the folder has been
                    // deleted.  Add this to a list of deleted folders for later processing.
                    foldersDeletedFromTD.add(Util.cLong(c, "_id"));
                    count++;
                    this.logSyncedItem(DOWNLOAD, DELETE, FOLDER, Util.cString(c,"title"));
                }
            }
            c.close();
            Util.log("Folders deleted from Toodledo: "+count);
            if (count>0)
            {
                itemsDownloaded = true;
            }
        }
        sendPercentComplete(FOLDER_SYNC_PERCENT/_numSyncedAccounts);

        //
        // Context Sync:
        //
        
        // This keeps track of contexts that were sent to toodledo via an add or edit:
        HashSet<Long> contextsUploaded = new HashSet<Long>();
        
        // This keeps track of contexts that were deleted from Toodledo:
        HashSet<Long> contextsDeletedFromTD = new HashSet<Long>();

        // Check the modification dates for the contexts to see if any changes need to be
        // uploaded.
        ContextsDbAdapter contextsDB = new ContextsDbAdapter();
        c = contextsDB.queryContexts("td_id>-1 and mod_date>sync_date"+
            " and account_id="+accountID,null);
        count = 0;
        while (c.moveToNext())
        {
            result = tdInterface.editContext(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not edit context ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (ToodledoInterface._lastErrorCode==4)
                {
                	// This means that Toodledo thinks the context does not exist.
                	// Remove the TD ID so that it will be added in the code below.
                	Util.log("We will try adding this instead.");
                	contextsDB.modifyTDID(Util.cLong(c, "_id"), -1);
                	continue;
                }
                else
                {
                	c.close();
                	return result;
                }
            }
            count++;
            contextsUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, MODIFY, CONTEXT, Util.cString(c,"title"));
            contextsDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" edited contexts to Toodledo.");

        // Look for contexts that have not been uploaded to Toodledo, and upload them.
        c = contextsDB.queryContexts("td_id=-1 and account_id="+accountID, null);
        count = 0;
        while (c.moveToNext())
        {   
            result = tdInterface.addContext(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add context ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the context because it already exists.  Delete the context 
                	// locally and update any tasks that have it.
                	long rejectedContext = Util.cLong(c, "_id");
                	Util.log("Removing the rejected context and updating tasks.");                	
                	Cursor c2 = tasksDB.queryTasks("context_id="+rejectedContext,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.context_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	contextsDB.deleteContext(rejectedContext);
                }
                else
                {
                	c.close();
                	return result;
                }                
            }
            count++;
            contextsUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, ADD, CONTEXT, Util.cString(c,"title"));
            contextsDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" new contexts to Toodledo.");
        
        // If a change occurred on Toodledo recently, then we need to download contexts:
        if (lastContextEdit>syncThreshold/1000)
        {
            Util.log("Toodledo is reporting context changes.  Downloading contexts...");
            ArrayList<HashMap<String,String>> tdContexts = new ArrayList<HashMap<String,String>>();
            result = tdInterface.getContexts(tdContexts);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not get context list from Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                return result;
            }
            
            // Look for changed contexts, and contexts that were added from Toodledo:
            Iterator<HashMap<String,String>> it = tdContexts.iterator();
            int addedCount = 0;
            int modifiedCount = 0;
            HashSet<Long> downloadedContextIDs = new HashSet<Long>();
            while (it.hasNext())
            {
                HashMap<String,String> contextHash = it.next();
                long TDID = Long.parseLong(contextHash.get("id"));
                downloadedContextIDs.add(TDID);
                try
                {
	                c = contextsDB.getContext(accountID, TDID);
	                if (!c.moveToFirst())
	                {
	                    // We have a context that exists in Toodledo, but not locally.
	                    if (0==deletesDB.isDeletePending("context", accountID, TDID))
	                    {
	                        // The context is not being deleted locally, so this was added 
	                        // from Toodledo.  Add it locally also.
	                        long contextID = contextsDB.addContext(TDID, accountID, contextHash.get
	                            ("name"));
	                        if (contextID==-1)
	                        {
	                            Util.log("Could not insert new context from Toodledo into database.");
	                            return DB_FAILURE;
	                        }
	                        addedCount++;
	                        this.logSyncedItem(DOWNLOAD, ADD, CONTEXT, contextHash.get
	                            ("name"));
	                        contextsDB.setSyncDate(contextID,System.currentTimeMillis());
	                    }
	                }
	                else
	                {
	                    // The context exists in both places.  Check for differences:
	                    if (!contextsUploaded.contains(Util.cLong(c, "_id")) && (
	                        !Util.cString(c, "title").equals(contextHash.get("name"))))
	                    {
	                        if (Util.cLong(c, "mod_date")>Util.cLong(c, "sync_date"))
	                        {
	                            // The contexts differ and the local copy was modified since the
	                            // last sync.  This is unlikely to happen, but it can happen.
	                            // if the user makes a context change in between the uploading
	                            // of edits and this code.
	                            result = tdInterface.editContext(c);
	                            if (result != ToodledoInterface.SUCCESS)
	                            {
	                                Util.log("Could not edit context ID "+Util.cLong(c, "_id")+" ("+
	                                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
	                                    ToodledoInterface.getFailureString(result));
	                                return result;
	                            }
	                            contextsUploaded.add(Util.cLong(c,"_id"));
	                            this.logSyncedItem(UPLOAD, MODIFY, CONTEXT, Util.cString(c,"title"));
	                            contextsDB.setSyncDate(Util.cLong(c,"_id"),System.
	                            	currentTimeMillis());
	                        }
	                        else
	                        {
	                            // The context has not been modified since the last sync, so
	                            // it must have changed from Toodledo.  Update the local copy.
	                            isSuccessful = contextsDB.modifyContext(Util.cLong(c,"_id"), 
	                                Util.cLong(c,"td_id"), accountID, contextHash.get("name"));
	                            if (!isSuccessful)
	                            {
	                                Util.log("Could not modify context that was downloaded "+
	                                    "from Toodledo.  DB Update failed.");
	                                return DB_FAILURE;
	                            }
	                            modifiedCount++;
	                            this.logSyncedItem(DOWNLOAD, MODIFY, CONTEXT, contextHash.get
	                            	("name"));
	                            contextsDB.setSyncDate(Util.cLong(c,"_id"),System.
	                            	currentTimeMillis());
	                        }
	                    }
	                }
                }
                finally
                {
                	c.close();
                }
            }
            Util.log("Contexts from Toodledo added locally: "+addedCount);
            Util.log("Contexts from Toodledo modified locally: "+modifiedCount);           
            if (addedCount>0 || modifiedCount>0)
            {
                itemsDownloaded = true;
            }
            
            // Look for contexts that exist locally but aren't on Toodledo.
            c = contextsDB.queryContexts("account_id="+accountID+" and td_id>-1", null);
            count = 0;
            while (c.moveToNext())
            {
                if (!downloadedContextIDs.contains(Util.cLong(c, "td_id")))
                {
                    // The context exists locally but is not on Toodledo.  We also know that
                    // it has been previously sent to Toodledo because the td_id field is
                    // greater than -1.  In this case, we know that the context has been
                    // deleted.  Add this to a list of deleted contexts for later processing.
                    contextsDeletedFromTD.add(Util.cLong(c, "_id"));
                    count++;
                    this.logSyncedItem(DOWNLOAD, DELETE, CONTEXT, Util.cString(c,"title"));
                }
            }
            c.close();
            Util.log("Contexts deleted from Toodledo: "+count);
            if (count>0)
            {
                itemsDownloaded = true;
            }
        }
        sendPercentComplete(CONTEXT_SYNC_PERCENT/_numSyncedAccounts);

        //
        // Goal Sync:
        //
        
        // This keeps track of goals that were sent to toodledo via an add or edit:
        HashSet<Long> goalsUploaded = new HashSet<Long>();
        
        // This keeps track of goals that were deleted from Toodledo:
        HashSet<Long> goalsDeletedFromTD = new HashSet<Long>();

        // Check the modification dates for the goals to see if any changes need to be
        // uploaded.
        GoalsDbAdapter goalsDB = new GoalsDbAdapter();
        c = goalsDB.queryGoals("td_id>-1 and mod_date>sync_date"+
            " and account_id="+accountID,null);
        count = 0;
        while (c.moveToNext())
        {
            result = tdInterface.editGoal(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not edit goal ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (ToodledoInterface._lastErrorCode==4)
                {
                	// This means that Toodledo thinks the goal does not exist.
                	// Remove the TD ID so that it will be added in the code below.
                	Util.log("We will try adding this instead.");
                	goalsDB.modifyTDID(Util.cLong(c, "_id"), -1);
                	continue;
                }
                else
                {
                	c.close();
                	return result;
                }
            }
            count++;
            goalsUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, MODIFY, GOAL, Util.cString(c,"title"));
            goalsDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" edited goals to Toodledo.");

        // Look for goals that have not been uploaded to Toodledo, and upload them.
        c = goalsDB.queryGoals("td_id=-1 and account_id="+accountID,"level asc");
        count = 0;
        while (c.moveToNext())
        {   
            result = tdInterface.addGoal(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add goal ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the goal because it already exists.  Delete the goal 
                	// locally and update any tasks that have it.
                	long rejectedGoal = Util.cLong(c, "_id");
                	Util.log("Removing the rejected goal and updating tasks.");                	
                	Cursor c2 = tasksDB.queryTasks("goal_id="+rejectedGoal,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.goal_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	goalsDB.deleteGoal(rejectedGoal);
                	SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
                	db.execSQL("update goals set contributes=0 where contributes="+
                		rejectedGoal);
                }
                else
                {
                	c.close();
                	return result;
                }
            }
            count++;
            goalsUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, ADD, GOAL, Util.cString(c,"title"));
            goalsDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" new goals to Toodledo.");
        
        // If a change occurred on Toodledo recently, then we need to download goals:
        if (lastGoalEdit>syncThreshold/1000)
        {
            Util.log("Toodledo is reporting goal changes.  Downloading goals...");
            ArrayList<HashMap<String,String>> tdGoals = new ArrayList<HashMap<String,String>>();
            result = tdInterface.getGoals(tdGoals);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not get goal list from Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                return result;
            }
            
            // Look for changed goals, and goals that were added from Toodledo:
            Iterator<HashMap<String,String>> it = tdGoals.iterator();
            int addedCount = 0;
            int modifiedCount = 0;
            HashSet<Long> downloadedGoalIDs = new HashSet<Long>();
            HashMap<Long,Long> goalsNeedingContributes = new HashMap<Long,Long>();
            while (it.hasNext())
            {
                HashMap<String,String> goalHash = it.next();
                long TDID = Long.parseLong(goalHash.get("id"));
                downloadedGoalIDs.add(TDID);
                try
                {
	                c = goalsDB.getGoal(accountID, TDID);
	                if (!c.moveToFirst())
	                {
	                    // We have a goal that exists in Toodledo, but not locally.
	                    if (0==deletesDB.isDeletePending("goal", accountID, TDID))
	                    {
	                        // The goal is not being deleted locally, so this was added 
	                        // from Toodledo.  When adding it locally, we need to check the
	                        // "contributes" field to make sure we can put in a valid UTL ID.
	                        // If multiple goals are being downloaded, it is possible that 
	                        // we could receive a contributes value that refers to a goal
	                        // that has not yet been downloaded.
	                        if (goalHash.get("contributes").equals("0"))
	                        {
	                            // Goal doesn't contribute to anything, so we can add it in
	                            // without any further checking.
	                            long goalID = goalsDB.addGoal(TDID, accountID, goalHash.get
	                                ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                0,Integer.parseInt(goalHash.get("level")));
	                            if (goalID==-1)
	                            {
	                                Util.log("Could not insert new goal from Toodledo into database.");
	                                return DB_FAILURE;
	                            }
	                            this.logSyncedItem(DOWNLOAD, ADD, GOAL, goalHash.get
	                                ("name"));
	                            goalsDB.setSyncDate(goalID,System.currentTimeMillis());
	                        }
	                        else
	                        {
	                            // The goal contributes to something.  Look it up in the DB,
	                            // if possible:
	                            c = goalsDB.getGoal(accountID,Long.parseLong(goalHash.get(
	                                "contributes")));
	                            if (c.moveToFirst())
	                            {
	                                // We found a match, so we can go ahead and add the goal.
	                                long goalID = goalsDB.addGoal(TDID, accountID, goalHash.get
	                                    ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                    Util.cLong(c, "_id"),Integer.parseInt(goalHash.get(
	                                    "level")));
	                                if (goalID==-1)
	                                {
	                                    Util.log("Could not insert new goal from Toodledo into database.");
	                                    return DB_FAILURE;
	                                }
	                                this.logSyncedItem(DOWNLOAD, ADD, GOAL, goalHash.get
	                                    ("name"));
	                                goalsDB.setSyncDate(goalID,System.currentTimeMillis());
	                            }
	                            else
	                            {
	                                // The goal contributes to a goal that we have not 
	                                // downloaded yet.  For now, add the goal with no 
	                                // contributes field:
	                                long goalID = goalsDB.addGoal(TDID, accountID, goalHash.get
	                                    ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                    0,Integer.parseInt(goalHash.get("level")));
	                                if (goalID==-1)
	                                {
	                                    Util.log("Could not insert new goal from Toodledo into database.");
	                                    return DB_FAILURE;
	                                }
	                                this.logSyncedItem(DOWNLOAD, ADD, GOAL, goalHash.get
	                                    ("name"));
	                                goalsDB.setSyncDate(goalID,System.currentTimeMillis());
	                                
	                                // Add this to a list of goals we need to update later:
	                                goalsNeedingContributes.put(goalID, Long.parseLong(goalHash.
	                                    get("contributes")));
	                            }
	                        }
	                        addedCount++;
	                    }
	                }
	                else
	                {
	                    // The goal exists in both places, so we must check for differences.  
	                    // To begin, we need the Toodledo ID of the goal the local copy 
	                    // contributes to (if any):
	                    long localTDID = 0;
	                    if (Util.cLong(c, "contributes")>0)
	                    {
	                        Cursor c2 = goalsDB.getGoal(Util.cLong(c, "contributes"));
	                        if (c2.moveToFirst())
	                        {
	                            localTDID = Util.cLong(c2, "td_id");
	                            if (localTDID==-1)
	                            {
	                                localTDID=0;
	                            }
	                        }
	                    }
	                    
	                    // Compare the 2 goals:
	                    if (!goalsUploaded.contains(Util.cLong(c, "_id")) && (
	                        !Util.cString(c, "title").equals(goalHash.get("name")) ||
	                        Util.cInt(c, "archived")!=Integer.parseInt(goalHash.get(
	                        "archived")) || 
	                        Util.cInt(c, "level")!=Integer.parseInt(goalHash.get("level")) ||
	                        localTDID!=Long.parseLong(goalHash.get("contributes"))
	                        ))
	                    {
	                        // The goals differ.
	                        if (Util.cLong(c, "mod_date")>Util.cLong(c, "sync_date"))
	                        {
	                            // The goals differ and the local copy was modified since the
	                            // last sync.  This is unlikely to happen, but it can happen
	                            // if the user makes a goal change in between the uploading
	                            // of edits and this code.
	                            result = tdInterface.editGoal(c);
	                            if (result != ToodledoInterface.SUCCESS)
	                            {
	                                Util.log("Could not edit goal ID "+Util.cLong(c, "_id")+" ("+
	                                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
	                                    ToodledoInterface.getFailureString(result));
	                                return result;
	                            }
	                            goalsUploaded.add(Util.cLong(c,"_id"));   
	                            this.logSyncedItem(UPLOAD, MODIFY, GOAL, Util.cString(c,
	                            	"title"));
	                            goalsDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
	                        }
	                        else
	                        {
	                            // The goal was modified from Toodledo.  When modifying it 
	                            // locally, we need to check the
	                            // "contributes" field to make sure we can put in a valid UTL ID.
	                            // If multiple goals are being downloaded, it is possible that 
	                            // we could receive a contributes value that refers to a goal
	                            // that has not yet been downloaded.
	                            if (goalHash.get("contributes").equals("0"))
	                            {
	                                // Goal doesn't contribute to anything, so we can modify it
	                                // without any further checking.
	                                isSuccessful = goalsDB.modifyGoal(Util.cLong(c, "_id"),
	                                    Util.cLong(c,"td_id"), accountID, goalHash.get
	                                    ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                    0,Integer.parseInt(goalHash.get("level")));
	                                if (!isSuccessful)
	                                {
	                                    Util.log("Could not modify goal from Toodledo in database.");
	                                    return DB_FAILURE;
	                                }
	                                this.logSyncedItem(DOWNLOAD, MODIFY, GOAL, goalHash.get
	                                    ("name"));
	                                goalsDB.setSyncDate(Util.cLong(c,"_id"),System.
	                                	currentTimeMillis());
	                            }
	                            else
	                            {
	                                // The goal contributes to something.  Look it up in the DB,
	                                // if possible:
	                                Cursor c2 = goalsDB.getGoal(accountID,Long.parseLong(
	                                    goalHash.get("contributes")));
	                                if (c2.moveToFirst())
	                                {
	                                    // We found a match, so we can go ahead and modify the goal.
	                                    isSuccessful = goalsDB.modifyGoal(Util.cLong(c, "_id"),
	                                        Util.cLong(c,"td_id"), accountID, goalHash.get
	                                        ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                        Util.cLong(c2, "_id"),Integer.parseInt(goalHash.get("level")));
	                                    if (!isSuccessful)
	                                    {
	                                        Util.log("Could not modify goal from Toodledo in database.");
	                                        return DB_FAILURE;
	                                    }
	                                    this.logSyncedItem(DOWNLOAD, MODIFY, GOAL, goalHash.get
	                                        ("name"));
	                                    goalsDB.setSyncDate(Util.cLong(c,"_id"),System.
	                                    	currentTimeMillis());
	                                }
	                                else
	                                {
	                                    // The goal contributes to a goal that we have not 
	                                    // downloaded yet.  For now, modify the goal with no 
	                                    // contributes field:
	                                    isSuccessful = goalsDB.modifyGoal(Util.cLong(c, "_id"),
	                                        Util.cLong(c,"td_id"), accountID, goalHash.get
	                                        ("name"),Util.stringToBoolean(goalHash.get("archived")),
	                                        0,Integer.parseInt(goalHash.get("level")));
	                                    if (!isSuccessful)
	                                    {
	                                        Util.log("Could not modify goal from Toodledo in database.");
	                                        return DB_FAILURE;
	                                    }
	                                    this.logSyncedItem(DOWNLOAD, MODIFY, GOAL, goalHash.get
	                                        ("name"));
	                                    goalsDB.setSyncDate(Util.cLong(c,"_id"),System.
	                                    	currentTimeMillis());
	                                    
	                                    // Add this to a list of goals we need to update later:
	                                    goalsNeedingContributes.put(Util.cLong(c, "_id"), Long.parseLong(
	                                        goalHash.get("contributes")));
	                                }
	                            }
	                            modifiedCount++;
	                        }
	                    }
	                }
                }
                finally
                {
                	c.close();
                }
            }
            Util.log("Goals from Toodledo added locally: "+addedCount);
            Util.log("Goals from Toodledo modified locally: "+modifiedCount);           
            if (addedCount>0 || modifiedCount>0)
            {
                itemsDownloaded = true;
            }
            
            // Look for goals that exist locally but aren't on Toodledo.
            c = goalsDB.queryGoals("account_id="+accountID+" and td_id>-1", null);
            count = 0;
            while (c.moveToNext())
            {
                if (!downloadedGoalIDs.contains(Util.cLong(c, "td_id")))
                {
                    // The goal exists locally but is not on Toodledo.  We also know that
                    // it has been previously sent to Toodledo because the td_id field is
                    // greater than -1.  In this case, we know that the goal has been
                    // deleted.  Add this to a list of deleted goals for later processing.
                    goalsDeletedFromTD.add(Util.cLong(c, "_id"));
                    count++;
                    this.logSyncedItem(DOWNLOAD, DELETE, GOAL, Util.cString(c,"title"));
                }
            }
            c.close();
            Util.log("Goals deleted from Toodledo: "+count);
            if (count>0)
            {
                itemsDownloaded = true;
            }
            
            // When downloading, it is possible that we received goals that contribute to
            // other goals that, at the time, were not downloaded.  All Toodledo goals,
            // should have been downloaded at this point, so we can go ahead and update
            // all of the contributes fields.
            Iterator<Long> it2 = goalsNeedingContributes.keySet().iterator();
            while (it2.hasNext())
            {
                // Get the UTL ID of the goal, and the Toodledo ID of the goal it 
                // contributes to:
                Long utlID = it2.next();
                Long TDID = goalsNeedingContributes.get(utlID);
                
                // Look up the goal:
                Cursor c2 = goalsDB.getGoal(accountID, TDID);
                if (!c2.moveToFirst())
                {
                    // Argh!  For some mysterious reason, we don't have the goal that this
                    // goal contributes to.  Leave the contributes field at "0" since we
                    // can't do anything else.  (It was set to 0 earlier.)
                    Util.log("Got a goal from Toodledo (UTL ID "+utlID+") that contributes "+
                        "to nonexistent Toodledo ID "+TDID);
                }
                else
                {
                    isSuccessful = goalsDB.setContributes(utlID, Util.cLong(c2, "_id"));
                    if (!isSuccessful)
                    {
                        Util.log("DB modification failed when trying to update contributes "+
                            "value.");
                        return DB_FAILURE;
                    }
                }
            }
        }
        sendPercentComplete(GOAL_SYNC_PERCENT/_numSyncedAccounts);
        
        //
        // Locations Sync:
        //
        
        // This keeps track of locations that were sent to TD via an add or edit:
        HashSet<Long> locationsUploaded = new HashSet<Long>();
        
        // This keeps track of locations that were deleted from TD:
        HashSet<Long> locationsDeletedFromTD = new HashSet<Long>();
        
        // Check the modification dates for locations to see if any changes need to be 
        // uploaded:
        LocationsDbAdapter locDB = new LocationsDbAdapter();
        c = locDB.queryLocations("td_id>-1 and mod_date>sync_date and account_id="+
        	accountID, null);
        count = 0;
        while (c.moveToNext())
        {
        	UTLLocation loc = locDB.cursorToUTLLocation(c);
        	result = tdInterface.editLocation(loc);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Could not edit location ID "+loc._id+" ("+loc.title+") in "+
        			"Toodledo.  Error message: "+ToodledoInterface.getFailureString(result));
                if (ToodledoInterface._lastErrorCode==4)
                {
                	// This means that Toodledo thinks the location does not exist.
                	// Remove the TD ID so that it will be added in the code below.
                	Util.log("We will try adding this instead.");
                	locDB.modifyTDID(Util.cLong(c, "_id"), -1);
                	continue;
                }
                else
                {
                	c.close();
                	return result;
                }
        	}
        	count++;
        	locationsUploaded.add(loc._id);
        	this.logSyncedItem(UPLOAD, MODIFY, LOCATION, loc.title);
        	loc.sync_date = System.currentTimeMillis();
        	locDB.modifyLocation(loc);
        }
        c.close();
        Util.log("Uploaded "+count+" edited locations to Toodledo.");
        
        // Look for locations that have not been uploaded to TD and upload them:
        c = locDB.queryLocations("td_id=-1 and account_id="+accountID, null);
        count = 0;
        while (c.moveToNext())
        {
        	UTLLocation loc = locDB.cursorToUTLLocation(c);
        	result = tdInterface.addLocation(loc);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Could not add location ID "+loc._id+" ("+loc.title+") to "+
        			"Toodledo.  Error message: "+ToodledoInterface.getFailureString(result));
        		if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the location because it already exists.  Delete the location 
                	// locally and update any tasks that have it.
        			long rejectedLoc = loc._id;
        			Util.log("Removing the rejected location and updating tasks.");
        			Cursor c2 = tasksDB.queryTasks("location_id="+rejectedLoc, "_id");
        			c2.moveToPosition(-1);
        			while (c2.moveToNext())
        			{
        				UTLTask t2 = tasksDB.getUTLTask(c2);
        				t2.location_id = 0;
        				t2.location_reminder = false;
        				t2.location_nag = false;
        				t2.mod_date = System.currentTimeMillis();
        				tasksDB.modifyTask(t2);
        			}
        			c2.close();
        			locDB.deleteLocation(rejectedLoc);
                }
        		else
        		{
        			c.close();
        			return result;
        		}
        	}
            count++;
            locationsUploaded.add(loc._id);
            this.logSyncedItem(UPLOAD, ADD, LOCATION, loc.title);
            loc.sync_date = System.currentTimeMillis();
            locDB.modifyLocation(loc);
        }
        c.close();
        Util.log("Uploaded "+count+" new locations to Toodledo.");
        
        // If a change occurred at Toodledo recently, then we need to download locations:
        String accountKey = "initial_locations_downloaded_for_"+accountID;
        if (lastLocEdit>syncThreshold/1000 || !settings.contains(accountKey))
        {
        	Util.log("Toodledo is reporting location changes.  Downloading locations...");
        	ArrayList<UTLLocation> locations = new ArrayList<UTLLocation>();
        	result = tdInterface.getLocations(locations);
        	if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not get location list from Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                return result;
            }
        	
        	// Look for changed locations, and locations that were added from Toodledo:
        	Iterator<UTLLocation> it = locations.iterator();
        	int addedCount = 0;
        	int modifiedCount = 0;
        	HashSet<Long> downloadedLocationIDs = new HashSet<Long>();
        	while (it.hasNext())
        	{
        		UTLLocation tdLoc = it.next();
        		long TDID = tdLoc.td_id;
        		downloadedLocationIDs.add(TDID);
        		UTLLocation utlLoc = locDB.getLocation(accountID, TDID);
        		if (utlLoc==null)
        		{
        			// We have a location that exists in Toodledo, but not locally.
        			if (0==deletesDB.isDeletePending("location", accountID, TDID))
        			{
        				// The location is not being deleted locally, so this was added from
        				// Toodledo.  Add it locally also.
        				tdLoc.sync_date = System.currentTimeMillis();
        				long locID = locDB.addLocation(tdLoc);
        				if (locID==-1)
        				{
        					Util.log("Could not insert new location from Toodledo into "+
        						"database.");
        					return DB_FAILURE;
        				}
        				addedCount++;
        				this.logSyncedItem(DOWNLOAD, ADD, LOCATION, tdLoc.title);
        			}
        		}
        		else
        		{
        			// The location exists in both places.  Check for differences:
        			if (!tdLoc.equals(utlLoc))
        			{
        				if (utlLoc.mod_date>utlLoc.sync_date)
        				{
        					// The locations differ and the local copy was modified since the
        					// last sync.  This is unlikely to happen, but it can happen
        					// if the user makes a location change in between the uploading
        					// of edits and this code.
        					result = tdInterface.editLocation(utlLoc);
        					if (result != ToodledoInterface.SUCCESS)
        					{
        						Util.log("Could not edit location ID "+utlLoc._id+" ("+
        							utlLoc.title+") in Toodledo.  Error message: "+
        							ToodledoInterface.getFailureString(result));
        						return result;
        					}
        					locationsUploaded.add(utlLoc._id);
        					this.logSyncedItem(UPLOAD, MODIFY, LOCATION, utlLoc.title);
        					utlLoc.sync_date = System.currentTimeMillis();
        					locDB.modifyLocation(utlLoc);
        				}
        				else
        				{
        					// The location has not been modified since the last sync, so
        					// it must have changed from Toodledo. Update the local copy:
        					tdLoc._id = utlLoc._id;
        					tdLoc.mod_date = System.currentTimeMillis();
        					tdLoc.sync_date = System.currentTimeMillis();
        					isSuccessful = locDB.modifyLocation(tdLoc);
        					if (!isSuccessful)
        					{
        						Util.log("Could not modify location that was downloaded "+
                                	"from Toodledo.  DB Update failed.");
        						return DB_FAILURE;
        					}
        					modifiedCount++;
        					this.logSyncedItem(DOWNLOAD, MODIFY, LOCATION, tdLoc.title);
        				}
        			}
        		}
        	}
            Util.log("Locations from Toodledo added locally: "+addedCount);
            Util.log("Locations from Toodledo modified locally: "+modifiedCount);           
            if (addedCount>0 || modifiedCount>0)
			{
				itemsDownloaded = true;
				Util.setupGeofencing(this);
			}
            Util.updatePref(accountKey, true, this);
            
            // Look for locations that exist locally but not in Toodledo:
            c = locDB.queryLocations("account_id="+accountID+" and td_id>-1", null);
            count = 0;
            while (c.moveToNext())
            {
            	UTLLocation loc = locDB.cursorToUTLLocation(c);
            	if (!downloadedLocationIDs.contains(loc.td_id))
            	{
            		// The location exists locally but is not in Toodledo. We also know that
            		// it has been previously sent to Toodledo because the td_id field is
            		// greater than -1.  In this case, we know that the location has been 
            		// deleted.  Add this to the list of deleted locations for later processing
            		locationsDeletedFromTD.add(loc._id);
            		count++;
            		this.logSyncedItem(DOWNLOAD, DELETE, LOCATION, loc.title);
            	}
            }
            c.close();
            Util.log("Locations deleted from Toodledo: "+count);
            if (count>0)
            	itemsDownloaded = true;
        }
        return doFullSync2(utlAccount,tdInterface,lastTaskDelete,lastTaskAddEdit,
        	foldersDeletedFromTD,contextsDeletedFromTD,goalsDeletedFromTD,
        	locationsDeletedFromTD);
    }
    
    private int doFullSync2(UTLAccount utlAccount, ToodledoInterface tdInterface, long
    	lastTaskDelete, long lastTaskAddEdit, HashSet<Long> foldersDeletedFromTD,
    	HashSet<Long> contextsDeletedFromTD, HashSet<Long> goalsDeletedFromTD,
    	HashSet<Long> locationsDeletedFromTD)
    {
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	long accountID = utlAccount._id;
    	boolean isSuccessful;
    	AccountsDbAdapter accountsDB = new AccountsDbAdapter();
    	Cursor c;
    	int count;
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	FoldersDbAdapter foldersDB = new FoldersDbAdapter();
    	ContextsDbAdapter contextsDB = new ContextsDbAdapter();
    	GoalsDbAdapter goalsDB = new GoalsDbAdapter();
    	LocationsDbAdapter locDB = new LocationsDbAdapter();
        int result;
        FeatureUsage featureUsage = new FeatureUsage(this);
    	
        //
        // Tasks Sync:
        //
        
        // We need to download collaboration and private folder data if the user has just 
        // upgraded:
        if (settings.getBoolean("collaboration_download_needed", false))
        {
        	String accountKey = "collaboration_downloaded_for_"+utlAccount._id;
        	if (!settings.contains(accountKey))
    		{
        		Util.log("Performing one-time collaboration download for account "+utlAccount.name);
        		
        		// We need to re-download all folders in order to get info on which ones are
        		// private:
        		ArrayList<HashMap<String,String>> tdFolders = new ArrayList<HashMap<String,String>>();
                result = tdInterface.getFolders(tdFolders);
                if (result != ToodledoInterface.SUCCESS)
                {
                    Util.log("Could not get folder list from Toodledo.  Error message: "+
                        ToodledoInterface.getFailureString(result));
                    return result;
                }
                Iterator<HashMap<String,String>> it = tdFolders.iterator();
                while (it.hasNext())
                {
                    HashMap<String,String> folderHash = it.next();
                    long TDID = Long.parseLong(folderHash.get("id"));
                    c = foldersDB.getFolder(accountID, TDID);
                    if (c.moveToFirst())
                    {
                    	// Just set the private field.  Other changes can be handled elsewhere.
                    	foldersDB.setPrivateField(Util.cLong(c,"_id"), Util.stringToBoolean(
                    		folderHash.get("private")));
                    }
                    c.close();
                }
                
                // All incomplete tasks are now downloaded in order to get collaboration
                // information:
                tdInterface.downloadIncompleteOnly = true;
                Util.log("Privacy information for folders downloaded.  Starting on tasks...");
                ArrayList<UTLTask> taskList = new ArrayList<UTLTask>();
            	ArrayList<String> tagList = new ArrayList<String>();
            	ArrayList<Boolean> hasMetaList = new ArrayList<Boolean>();
            	result = tdInterface.getTasks(0,taskList,tagList,hasMetaList,this,false,0,0,null);
                tdInterface.downloadIncompleteOnly = false;
                if (result != ToodledoInterface.SUCCESS)
            	{
            		Util.log("Failed to download added and edited tasks from Toodledo. "+
            			ToodledoInterface.getFailureString(result));
            		return result;
            	}
                Iterator<UTLTask> it4 = taskList.iterator();
                int taskCount = 0;
                int sharedTaskCount = 0;
                while (it4.hasNext())
                {
                	UTLTask tdTask = it4.next();
                	UTLTask localTask = tasksDB.getTask(utlAccount._id,tdTask.td_id);
                	if (localTask!=null)
                	{
                		// The task exists locally.  Update the collaboration information:
                		localTask.is_joint = tdTask.is_joint;
                		localTask.owner_remote_id = tdTask.owner_remote_id;
                		localTask.shared_with = tdTask.shared_with;
                		localTask.added_by = tdTask.added_by;
                		tasksDB.modifyTask(localTask);
                		
                		taskCount++;
                		if (localTask.is_joint)
                			sharedTaskCount++;
                	}
                }
                Util.log(sharedTaskCount+" of "+taskCount+" tasks were joint tasks.");
                
                // Save a preference that indicates the account has received collaboration info:
                Util.updatePref(accountKey, true, this);
                
				// See if all collaboration data has been downloaded:
				Cursor accCursor = accountsDB.getAllAccounts();
				accCursor.moveToPosition(-1);
				boolean newState = false;
				while (accCursor.moveToNext())
				{
					UTLAccount acct = accountsDB.getUTLAccount(accCursor);
					if (acct.sync_service==UTLAccount.SYNC_TOODLEDO)
					{
						String accountKey2 = "collaboration_downloaded_for_"+acct._id;
						if (!settings.contains(accountKey2))
						{
							newState = true;
							break;
						}
					}
				}
				accCursor.close();
				Util.updatePref("collaboration_download_needed", newState, this);
    		}
        }
        
        // We need to download location data if the user has upgraded:
    	if (settings.getBoolean("location_download_needed", false))
    	{
    		String accountKey = "locations_downloaded_for_"+utlAccount._id;
    		if (!settings.contains(accountKey))
    		{
    			// The user has upgraded to a version supporting locations, and 
    			// the initial download of location data for this account is not done.
    			Util.log("Performing one-time location download.");
    			result = tdInterface.downloadLocations(utlAccount);
    			if (result==ToodledoInterface.SUCCESS)
    			{
    				Util.updatePref(accountKey, true, this);
    				
    				// See if all location data has been downloaded:
    				Cursor accCursor = accountsDB.getAllAccounts();
    				accCursor.moveToPosition(-1);
    				boolean newState = false;
    				while (accCursor.moveToNext())
    				{
    					UTLAccount acct = accountsDB.getUTLAccount(accCursor);
    					if (acct.sync_service==UTLAccount.SYNC_TOODLEDO)
    					{
    						String accountKey2 = "locations_downloaded_for_"+acct._id;
    						if (!settings.contains(accountKey2))
    						{
    							newState = true;
    							break;
    						}
    					}
    				}
    				accCursor.close();
    				Util.updatePref("location_download_needed", newState, this);
    			}
    			else
    			{
    				Util.log("Failed to perform initial location download. "+
            			ToodledoInterface.getFailureString(result));
    				return result;
    			}
    		}
    	}
    	
        // If there are any tasks that were deleted from TD since the last sync, then
        // download and store them:
        long syncThreshold = utlAccount.last_sync - (5*60*1000);
        long tdDownloadThreshold = syncThreshold - localMinusTD*1000;
        if (syncThreshold<0)
        {
        	syncThreshold = 0;
        }
        ArrayList<Long> deletedIdList = new ArrayList<Long>();
        if (lastTaskDelete > syncThreshold/1000)
        {
        	result = tdInterface.getDeletedTasks(tdDownloadThreshold, deletedIdList);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Failed to download deleted tasks from Toodledo. "+
        			ToodledoInterface.getFailureString(result));
        		return result;
        	}
        	Util.log("Downloaded "+deletedIdList.size()+" deleted tasks.");
        }
        
        // Check to see if there are any task adds or edits we need to retrieve:
        TagsDbAdapter tagsDB = new TagsDbAdapter();
        CurrentTagsDbAdapter currentTagsDB = new CurrentTagsDbAdapter();
        ArrayList<UTLTask> tasksNeedingChildUpdates = new ArrayList<UTLTask>();
        ArrayList<UTLTask> addedTasks = new ArrayList<UTLTask>();
        Util.log("TD last task add/edit: "+Util.getDateTimeString(lastTaskAddEdit*1000,
        	this));
        Util.log("TD download threshold: "+Util.getDateTimeString(tdDownloadThreshold,
        	this));
        Util.log("Sync Threshold:        "+Util.getDateTimeString(syncThreshold,this));
        if (lastTaskAddEdit > syncThreshold/1000)
        {
        	// Toodledo is reporting changes.  Download the task list:
        	Util.log("Toodledo is reporting changes to tasks.");
        	ArrayList<UTLTask> taskList = new ArrayList<UTLTask>();
        	ArrayList<String> tagList = new ArrayList<String>();
        	ArrayList<Boolean> hasMetaList = new ArrayList<Boolean>();
        	result = tdInterface.getTasks(tdDownloadThreshold,taskList,tagList,hasMetaList,
        		this,_sendPercentComplete,TASK_DOWNLOAD_PERCENT/_numSyncedAccounts,
        		_percentComplete,mClients);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Failed to download added and edited tasks from Toodledo. "+
        			ToodledoInterface.getFailureString(result));
        		return result;
        	}
        	long tempLastSyncTime = System.currentTimeMillis();
        	Util.log("Downloaded "+taskList.size()+" added or edited tasks from Toodledo.");
            sendPercentComplete(TASK_DOWNLOAD_PERCENT/_numSyncedAccounts);
        	
            // Go through the downloaded tasks and update the database:
        	int i = -1;
        	Iterator<UTLTask> it3 = taskList.iterator();
        	int tasksAdded = 0;
        	int tasksEdited = 0;
        	int completedTasksIgnored = 0;
			long numDays = new Integer(settings.getInt("purge_completed", 365)).longValue();
			long ignoreThreshold = System.currentTimeMillis() - numDays*24*60*60*1000;
			Util.log("Ignore Threshold: "+Util.getDateTimeString(ignoreThreshold,this));
			long lastPercentCompleteUpdate = System.currentTimeMillis();
			int basePercentComplete = _percentComplete;
			float totalDbUpdateIncrement = (float)TASK_UPDATE_DB_PERCENT/(float)
				_numSyncedAccounts;
			boolean hasMeta;
			clearTempTags();
			CalendarInterface ci = new CalendarInterface(this);
        	while (it3.hasNext())
        	{
        		i++;
        		UTLTask tdTask = it3.next();
        		tdTask.mod_date += localMinusTD*1000; // Convert to local time.
        		
    			// If the task is an old, completed task, then do not store it here:
    			if (tdTask.completed && tdTask.completion_date>0 && 
    				tdTask.completion_date<ignoreThreshold)
    			{
            		if ((System.currentTimeMillis()-lastPercentCompleteUpdate)>1000)
            		{
            			float percentCompleteIncrement = totalDbUpdateIncrement*(float)i/
            				(float)taskList.size();
            			_percentComplete += (int)(basePercentComplete+percentCompleteIncrement);
            			sendPercentComplete(0);
            			lastPercentCompleteUpdate = System.currentTimeMillis();
            		}
            		completedTasksIgnored++;
    				continue;
    			}
    			
    			// This flag indicates if meta data was included in the downloaded task.
    			// This affects how certain fields not supported by Toodledo, such as nag, 
    			// are set.
    			hasMeta = hasMetaList.get(i);
    			
    			// Go through the note and check for special settings set by the user
    			// (such as nagging and location reminders):
    			boolean nagMatch = Util.regularExpressionMatch(tdTask.note, "^nag\\b");
    			int nagMatchLength = 3;
    			boolean locReminderMatch = Util.regularExpressionMatch(tdTask.note, 
    				"^loc reminder\\b");
    			int locReminderLength = 12;
    			boolean locNagMatch = Util.regularExpressionMatch(tdTask.note,"^loc nag\\b");
    			int locNagLength = 7;
    			while (nagMatch || locReminderMatch || locNagMatch)
    			{
    				// Strip out the matching text from the note:
    				if (nagMatch)
    					tdTask.note = tdTask.note.substring(nagMatchLength).trim();
    				if (locReminderMatch)
    					tdTask.note = tdTask.note.substring(locReminderLength).trim();
    				if (locNagMatch)
    					tdTask.note = tdTask.note.substring(locNagLength).trim();
    				
    				// Update the task based on the match that was found:
    				if (nagMatch && tdTask.reminder>0)
    					tdTask.nag = true;
    				if (locReminderMatch && tdTask.location_id>0)
    					tdTask.location_reminder = true;
    				if (locNagMatch && tdTask.location_id>0)
    				{
    					tdTask.location_reminder = true;
    					tdTask.location_nag = true;
    				}
    				
    				// Check for matches again:
    				nagMatch = Util.regularExpressionMatch(tdTask.note, "^nag\\b");
        			locReminderMatch = Util.regularExpressionMatch(tdTask.note, 
        				"^loc reminder\\b");
        			locNagMatch = Util.regularExpressionMatch(tdTask.note,"^loc nag\\b");
    			}
    			
        		// See if there is a task with the same TD ID in the database:
        		UTLTask localTask = tasksDB.getTask(accountID, tdTask.td_id);
        		if (localTask==null)
        		{
        			// The task must have been added, since it doesn't exist locally.
        			
        			// If the user wants this on the calendar, then add it:
        			if (settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false)
        				&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        			{
        				String eventUri = ci.linkTaskWithCalendar(tdTask);
        				if (!eventUri.startsWith(CalendarInterface.ERROR_INDICATOR))
        				{
        					tdTask.calEventUri = eventUri;
        				}
        				else
        				{
        					Util.log("Could not add downloaded task to calendar. "+
        						eventUri.substring(CalendarInterface.ERROR_INDICATOR.
        						length()));
        				}
        			}

                    // Record any features used by the task.
                    featureUsage.recordForTask(tdTask);

         			// Add the task to the DB:
        			long taskID = tasksDB.addTask(tdTask);
        			if (taskID==-1)
        			{
        				Util.log("Could not add new task to database.");
        				Util.log(TasksDbAdapter.getLastTaskString());
        				return DB_FAILURE;
        			}
        			this.logSyncedItem(DOWNLOAD, ADD, TASK, tdTask.title);
        			this.logTaskDetails(tdTask);
        			
        			// Now that we have the task's ID, we can add a link to the task
        			// in the corresponding calendar event:
        			if (settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false)
        				&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        			{
        				ci.addTaskLinkToEvent(tdTask);
        			}
        			
        			// If tags were included, then add them in as well:
        			String tagStr = tagList.get(i);
        			if (tagStr.length()>0)
        			{
        				String[] tagArray = tagStr.split(",");
        				for (int j=0; j<tagArray.length; j++)
        				{
        					tagArray[j] = tagArray[j].trim();
        				}
        				tagsDB.linkTags(taskID, tagArray);
        				addToTempTags(tagArray);

                        // Record that the tags feature was used.
                        if (!tdTask.completed)
                            featureUsage.record(FeatureUsage.TAGS);
        			}
        			
        			// Keep an array of tasks added.  We may need this later to link child
        			// tasks to new parent tasks:
        			tdTask._id = taskID;
        			addedTasks.add(tdTask);
        			
        			// Set a reminder if one was specified:
        			if (tdTask.reminder>System.currentTimeMillis() && !tdTask.completed)
        			{
        				Util.scheduleReminderNotification(tdTask);
        			}
        			tasksAdded++;
        		}
        		else
        		{
        			// The task exists locally.
        			boolean tdWinsConflict = false;
        			if (localTask.mod_date > localTask.sync_date)
        			{
        				// Got a sync conflict, because the task has changed both
        				// locally and in Toodledo.  We will use the one with the later
        				// modification date.
        				if (localTask.mod_date<tdTask.mod_date)
        				{
        					// Toodledo wins the conflict, because its modification date
        					// is later.
        					tdWinsConflict = true;
        					Util.log("Got sync conflict for task ID "+localTask._id+" ("+
        						localTask.title+").  Toodledo wins because it was "+
        						"modified later.");
        				}
        				else
        				{
        					Util.log("Got sync conflict for task ID "+localTask._id+" ("+
        						localTask.title+").  Local copy wins because it was "+
        						"modified later.");
        				}
        			}
        			if (localTask.sync_date >= localTask.mod_date || tdWinsConflict)
        			{
        				// No sync conflict or TD wins a conflict, so we update the 
        				// local copy.
        				tdTask._id = localTask._id;
        				            			
            			// Under certain circumstances, the due time and reminder do
            			// not sync.  The rules are different for pro and non-pro accounts.
            			if (utlAccount.pro && !hasMeta)
            			{
            				if (tdTask.uses_due_time && tdTask.reminder>0 &&
            					!localTask.uses_due_time)
            				{
            					// Get the reminder lead time, in number of days:
            					GregorianCalendar dueTime = new GregorianCalendar(TimeZone.
            						getTimeZone(settings.getString("home_time_zone", 
            						"")));
            					GregorianCalendar time = new GregorianCalendar(
            						TimeZone.getTimeZone(settings.getString(
            						"home_time_zone","")));
            					dueTime.setTimeInMillis(tdTask.due_date);
            					time.setTimeInMillis(tdTask.reminder);
            					time.add(Calendar.DATE, 7);
            					if (time.after(dueTime) || time.equals(dueTime))
            					{
            						// The reminder time is less than or equal to 7 days
            						// prior to the due time.
            						if (localTask.reminder==tdTask.reminder)
            						{
            							// The local task did not specify a due time, and
            							// did specify a reminder time.  The downloaded
            							// task has a due time and the reminder is the
            							// same.  In this case, do not use a due time
            							// for the task.  This will allow the user to 
            							// edit an exact reminder time rather than
            							// specify the number of minutes prior to due time.
            							tdTask.uses_due_time = false;
            						}
            						else if (localTask.reminder==tdTask.reminder+60000)
            						{
            							// Local reminder is 1 minute later than TD
            							// reminder.  Treat this as the same:
            							tdTask.uses_due_time = false;
            							tdTask.reminder = localTask.reminder;
            						}
            					}
            				}
            				
            				if (tdTask.repeat>0 && localTask.repeat>0 &&
            					tdTask.repeat==localTask.repeat &&
            					!localTask.uses_due_time && tdTask.uses_due_time &&
            					!tdTask.completed && !localTask.completed &&
            					tdTask.due_date>localTask.due_date && 
            					localTask.due_date>0 && localTask.reminder>0 &&
            					tdTask.reminder>localTask.reminder &&
            					(tdTask.due_date-tdTask.reminder)==60000
            					)
            				{
            					// Get the hours and minutes of the local and TD reminders:
            					GregorianCalendar cal = new GregorianCalendar(TimeZone.
            						getTimeZone(settings.getString("home_time_zone",
            						"")));
            					cal.setTimeInMillis(tdTask.reminder+60000);
            					int tdHour = cal.get(Calendar.HOUR);
            					int tdMin = cal.get(Calendar.MINUTE);
            					cal.setTimeInMillis(localTask.reminder);
            					int localHour = cal.get(Calendar.HOUR);
            					int localMin = cal.get(Calendar.MINUTE);
            					
            					if (localHour==tdHour && localMin==tdMin)
            					{
	            					// The task was edited at TD (likely marked complete)
	            					// and the newly generated task should not have a 
	            					// due time here.
	            					tdTask.uses_due_time = false;
	            					tdTask.reminder = tdTask.due_date;
            					}
            				}
            			}
            			else if (!hasMeta && !utlAccount.pro && localTask.reminder>0)
            			{
            				// A free account.  Reminders are limited to 1 hour 
            				// prior to the due time.  If the user sets the reminder
            				// to something else, then don't sync the due time and/or
            				// reminder.
            				if (localTask.uses_due_time)
            				{
            					if ((localTask.due_date - localTask.reminder) !=
            						(60*60*1000))
            					{
            						// The local task has a reminder that is not 1 hour
            						// before the due time.  So, we don't sync
            						// reminder info:
            						if (tdTask.reminder==0)
            						{
            							tdTask.reminder = localTask.reminder;
            						}
            					}
            				}
            				else
            				{
            					GregorianCalendar localDueDate = new GregorianCalendar(
            						TimeZone.getTimeZone(settings.getString(
            						"home_time_zone", "")));
            					localDueDate.setTimeInMillis(localTask.due_date);
            					GregorianCalendar localReminder = new GregorianCalendar(
            						TimeZone.getTimeZone(settings.getString(
            						"home_time_zone", "")));
            					localReminder.setTimeInMillis(localTask.reminder);
            					if (localDueDate.get(Calendar.YEAR)==localReminder.get(Calendar.YEAR) &&
            						localDueDate.get(Calendar.MONTH)==localReminder.get(Calendar.MONTH) &&
            						localDueDate.get(Calendar.DATE)==localReminder.get(Calendar.DATE) &&
            						localReminder.get(Calendar.HOUR_OF_DAY)<23)
            					{
            						// Local Reminder is on same date as local due date, 
            						// and is before 11 PM.
            						if (tdTask.reminder>0)
            						{
            							// The downloaded task contains a reminder.  See
            							// if it differs:
            							if (localTask.reminder==tdTask.reminder)
            							{
            								// The reminder did not change, so we need
            								// to keep the local due time blank.
            								tdTask.uses_due_time = false;
            							}
            						}
            						else
            						{
            							// The downloaded task does not contain a reminder
            							// meaning the user made a change.
            							if ((tdTask.due_date-60*60*1000) == 
            								localTask.reminder)
            							{
            								// The only change was to delete the reminder,
            								// so keep the due time blank.
            								tdTask.uses_due_time = false;
            							}
            						}
            					}
            					else
            					{
            						// The local reminder is another legal time, but
            						// the reminder was not previously sent to TD since
            						// it can't support it.
            						if (!tdTask.uses_due_time && tdTask.reminder==0)
            						{
            							// Since no due time or reminder was passed back,
            							// then we keep the original reminder info.
            							tdTask.reminder = localTask.reminder;
            						}
            					}
            				}
            				
            				if (tdTask.repeat>0 && localTask.repeat>0 &&
            					tdTask.repeat==localTask.repeat &&
            					!localTask.uses_due_time && tdTask.uses_due_time &&
            					!tdTask.completed && !localTask.completed &&
            					tdTask.due_date>localTask.due_date && 
            					localTask.due_date>0 &&
            					tdTask.reminder>localTask.reminder &&
            					(tdTask.due_date-tdTask.reminder)==(60*60000)
            					)
            				{
            					// Get the hours and minutes of the local and TD reminders:
            					GregorianCalendar cal = new GregorianCalendar(TimeZone.
            						getTimeZone(settings.getString("home_time_zone",
            						"")));
            					cal.setTimeInMillis(tdTask.reminder);
            					int tdHour = cal.get(Calendar.HOUR);
            					int tdMin = cal.get(Calendar.MINUTE);
            					cal.setTimeInMillis(localTask.reminder);
            					int localHour = cal.get(Calendar.HOUR);
            					int localMin = cal.get(Calendar.MINUTE);

            					if (tdHour==localHour && tdMin==localMin)
            					{
	            					// The task was edited at TD (likely marked complete)
	            					// and the newly generated task should not have a 
	            					// due time here.
	            					tdTask.uses_due_time = false;
            					}
            				}
            			}
            			
        				// Update the linked event if needed:
        				if (localTask.calEventUri!=null && localTask.calEventUri.length()>0
        					&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
        				{
        					tdTask.calEventUri = localTask.calEventUri;
        					if (!ci.hasIdenticalCalendarEntries(localTask, tdTask))
        					{
	        					String eventUri = ci.linkTaskWithCalendar(tdTask);
	            				if (!eventUri.startsWith(CalendarInterface.ERROR_INDICATOR))
	            				{
	            					tdTask.calEventUri = eventUri;
	            				}
	            				else
	            				{
	            					Util.log("Could not modify calendar event for downloaded task. "+
	            						eventUri.substring(CalendarInterface.ERROR_INDICATOR.
	            						length()));
	            				}
        					}
        				}

                        // Record any features used by the task.
                        featureUsage.recordForTask(tdTask);

            			// Modify the task in the database:
            			isSuccessful = tasksDB.modifyTask(tdTask);
            			if (!isSuccessful)
            			{
            				Util.log("Database modification failed for edited task.");
            				Util.log(TasksDbAdapter.getLastTaskString());
            				return DB_FAILURE;
            			}
            			this.logSyncedItem(DOWNLOAD, MODIFY, TASK, tdTask.title);
            			this.logTaskDetails(tdTask);
            			
            			// If tags were included, then add them in as well:
            			String tagStr = tagList.get(i);
            			if (tagStr.length()>0)
            			{
            				String[] tagArray = tagStr.split(",");
            				for (int j=0; j<tagArray.length; j++)
            				{
            					tagArray[j] = tagArray[j].trim();
            				}
            				tagsDB.linkTags(localTask._id, tagArray);
            				addToTempTags(tagArray);

                            // Record that the tags feature was used.
                            if (!tdTask.completed)
                                featureUsage.record(FeatureUsage.TAGS);
            			}
            			else
            			{
            			    // No tags included.  If any previously existed, they must
            			    // be removed.
            			    tagsDB.linkTags(localTask._id, new String[] { });
            			}
            			
            			if (!localTask.completed && tdTask.completed && !utlAccount.pro)
            			{
            			    // The task just transitioned from not completed to completed.
            			    // Since child tasks will not be updated in TD, we need to do that
            			    // here.
            			    tasksNeedingChildUpdates.add(tdTask);
            			}
            			    
            			if (!localTask.completed && tdTask.completed)
            			{
            				// The task has transitioned from not completed to completed.
                	        // Remove any scheduled reminder notifications:
                	        if (tdTask.reminder>System.currentTimeMillis())
                	        {
                	        	Util.cancelReminderNotification(tdTask._id);
                	        }
                	        
                	        // If the task has any notifications displayed, remove them:
                	        Util.removeTaskNotification(tdTask._id);
            			}
            			
            			// Set a reminder if one was specified:
            			if (tdTask.reminder>System.currentTimeMillis() && !tdTask.completed)
            			{
            				Util.scheduleReminderNotification(tdTask);
            				
            				// If the task previously had a reminder in the past, we 
            				// need to remove any notifications that could be displaying.
            				if (localTask.reminder<System.currentTimeMillis())
            				{
            					Util.removeTaskNotification(localTask._id);
            				}
            			}
            			else if (localTask.reminder>0 && tdTask.reminder==0)
            			{
            				// A reminder was removed, so get rid of the alarm/notification:
            				Util.cancelReminderNotification(tdTask._id);
            			}

            			tasksEdited++;
        			}
        		}
        		
        		if ((System.currentTimeMillis()-lastPercentCompleteUpdate)>1000)
        		{
        			lastPercentCompleteUpdate = System.currentTimeMillis();
        			float percentCompleteIncrement = totalDbUpdateIncrement*(float)i/
        				(float)taskList.size();
        			_percentComplete = (int)(basePercentComplete+percentCompleteIncrement);
        			sendPercentComplete(0);
        		}
        	}
        	Util.log("Processed "+tasksAdded+" tasks added in Toodledo.");
        	Util.log("Processed "+tasksEdited+" tasks edited in Toodledo.");
        	Util.log("Ignored "+completedTasksIgnored+" old completed tasks from Toodledo.");
        	if (tasksAdded>0 || tasksEdited>0)
        	{
        	    itemsDownloaded = true;
        	}

        	_percentComplete = basePercentComplete + TASK_UPDATE_DB_PERCENT/_numSyncedAccounts;
        	sendPercentComplete(0);

        	// At this point, we have downloaded all tasks from TD and updated our
        	// database accordingly.  We can now record the last sync date/time for
        	// the account:
        	utlAccount = accountsDB.getAccount(accountID);
        	utlAccount.last_sync = tempLastSyncTime;      
        	accountsDB.modifyAccount(utlAccount);
        	
        	// Update the table with recently used tags:
       		currentTagsDB.addToRecent(getTempTags());
        	
        	// There is a chance that we downloaded some child tasks before the 
        	// corresponding parent.  If this occurs, then we need to update the
        	// children to link to the parent:
        	c = tasksDB.queryTasks("parent_id<0 and account_id="+accountID, null);
        	c.moveToPosition(-1);
        	while (c.moveToNext())
        	{
        		UTLTask childTask = tasksDB.getUTLTask(c);
        		long parentTDID = childTask.parent_id * -1;
        		UTLTask parentTask = tasksDB.getTask(accountID, parentTDID);
        		if (parentTask==null)
        		{
        			Util.log("Received a child task from Toodledo, but did not receive "+
        				"the parent task.  Marking the child as not linked.");
        			childTask.parent_id = 0;
        		}
        		else
        		{
        			childTask.parent_id = parentTask._id;
        			if (!tasksDB.modifyTask(childTask))
        			{
        				Util.log("Could not link child task to parent.");
        				c.close();
        				return DB_FAILURE;
        			}
        		}
        	}
        	c.close();
        	
        	// If a parent task was completed on a non-pro account, we need to make updates
        	// to the children:
        	Iterator<UTLTask> it4 = tasksNeedingChildUpdates.iterator();
        	while (it4.hasNext())
        	{
        	    UTLTask completedParent = it4.next();
        	    
        	    // Search the tasks that were added to see if any of them are generated due 
        	    // to a repeating pattern at the parent.
        	    UTLTask newParent = null;
        	    if (completedParent.repeat>0)
        	    {
            	    Iterator<UTLTask> it5 = addedTasks.iterator();
            	    while (it5.hasNext())
            	    {
            	        UTLTask addedTask = it5.next();
            	        if (addedTask.title.equals(completedParent.title) &&
            	            addedTask.repeat==completedParent.repeat)
            	        {
            	            newParent = addedTask;
            	            break;
            	        }
            	    }
        	    }
        	    Util.updateCompletedTasksChildren(completedParent, newParent);
        	}
        	if (tasksNeedingChildUpdates.size()>0)
        	{
        	    Util.log("Processed "+tasksNeedingChildUpdates.size()+" completed tasks from "+
        	        "Toodledo that has children locally.");
        	}
        }
        else
        {
            // There are no tasks to download from TD, but we must still update the 
            // last sync date for the account:
            utlAccount = accountsDB.getAccount(accountID);
            utlAccount.last_sync = System.currentTimeMillis();      
            accountsDB.modifyAccount(utlAccount);            
            sendPercentComplete((TASK_DOWNLOAD_PERCENT+TASK_UPDATE_DB_PERCENT)/
            	_numSyncedAccounts);
        }
        
        // Upload tasks that have been added or edited locally:
        c = tasksDB.queryTasks("mod_date>=sync_date and account_id="+accountID,"parent_id asc");
        c.moveToPosition(-1);
        int addCount = 0;
        int modifyCount = 0;
        while (c.moveToNext())
        {
        	UTLTask localTask = tasksDB.getUTLTask(c);
        	
        	// If this is a subtask and the user is not on a pro TD account, then we 
        	// have to skip this.
        	if (!utlAccount.pro && localTask.parent_id!=0)
        	{
        		continue;
        	}
        	
        	// Determine if this is an add or edit:
        	if (localTask.td_id==-1)
        	{
        	    // This is an add operations, since we haven't yet got a toodledo ID
        	    // for the task.        	    
                result = tdInterface.addTask(localTask,this);
                if (result != ToodledoInterface.SUCCESS)
                {
                    Util.log("Could not add task '"+localTask.title+"' to Toodledo.  "+
                    	"Error: "+ToodledoInterface.getFailureString(result));
                    if (result==ToodledoInterface.TOODLEDO_REJECT)
                    {
                    	// TD rejected the task.  Rather than abort the sync,
                    	// continue trying other tasks. For example, we might be
                    	// trying to upload a task containing a deleted folder.
                    	// If this happens, the task will sync next time after the
                    	// deleted folder has been processed here.
                    	continue;
                    }
                    c.close();
                    return result;
                }
                this.logSyncedItem(UPLOAD, ADD, TASK, localTask.title);
                this.logTaskDetails(localTask);
                addCount++;
                
                // Update the sync date for the task:
                if (!tasksDB.updateSyncDate(localTask._id))
                {
                	Util.log("Database failure when updating task sync date.");
                	c.close();
                	return DB_FAILURE;
                }
        	}
        	else
        	{
        	    // Since the task has been previously synced, this is an edit operation.
        	    
        	    // Before sending the edit command, check to see if TD has reported 
        	    // this as a deleted item:
        	    if (deletedIdList.contains(localTask.td_id))
        	    {
        	        Util.log("Sync conflict for TD task ID "+localTask.td_id+" ("+
        	            localTask.title+").  TD has reported this as deleted, but it "+
        	            "has also been edited on the handset.  Because TD no longer has "+
        	            "a record of the task, it must be deleted locally.");
        	    }
        	    else
        	    {
            	    result = tdInterface.editTask(localTask,this);
                    if (result != ToodledoInterface.SUCCESS)
                    {
                        Util.log("Could not edit task at Toodledo.  Error: "+
                            ToodledoInterface.getFailureString(result));
                        if (result==ToodledoInterface.TOODLEDO_REJECT)
                        {
                        	// TD rejected the task.  Rather than abort the sync,
                        	// continue trying other tasks. For example, we might be
                        	// trying to upload a task containing a deleted folder.
                        	// If this happens, the task will sync next time after the
                        	// deleted folder has been processed here.
                        	continue;
                        }
                        c.close();
                        return result;
                    }
                    modifyCount++;
                    
                    // Update the sync date for the task:
                    if (!tasksDB.updateSyncDate(localTask._id))
                    {
                    	Util.log("Database failure when updating task sync date.");
                    	c.close();
                    	return DB_FAILURE;
                    }
                    this.logSyncedItem(UPLOAD, MODIFY, TASK, localTask.title);
                    this.logTaskDetails(localTask);
        	    }
        	}
        }
        c.close();
        Util.log("Uploaded "+addCount+" new tasks to Toodledo.");
        Util.log("Uploaded "+modifyCount+" modified tasks to Toodledo.");
        sendPercentComplete(TASK_UPLOAD_PERCENT/_numSyncedAccounts);
        
        //
        // Pending Ressignments:
        //
        
        PendingReassignmentsDbAdapter prdb = new PendingReassignmentsDbAdapter();
        c = prdb.getReassignments();
        while (c.moveToNext())
        {
        	// Send the reassignment info to Toodledo:
        	result = tdInterface.reassignTask(Util.cLong(c,"task_id"), Util.cString(c,"new_owner_id"), 
        		this);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Could not reassign task ID "+Util.cLong(c,"task_id")+
        			".  "+ToodledoInterface.getFailureString(result));
            	
        		// Remove the reassignment info:
            	prdb.deleteReassignment(Util.cLong(c,"_id"));

            	c.close();
            	return result;
        	}
        	else
        	{
        		// A successfully reassigned task no longer appears on the current users list.
        		tasksDB.deleteTask(Util.cLong(c,"task_id"));
        	}
        	
        	// Remove the reassignment info:
        	prdb.deleteReassignment(Util.cLong(c,"_id"));
        }
        c.close();
        
        
        //
        // Deleted tasks:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("task",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Make sure the task is not located in the tasks table.  If it is, then
            // we have a conflict.  The task was modified in TD, but deleted locally.
            // In this situation, we don't send the delete operation to TD.
            UTLTask task = tasksDB.getTask(accountID, Util.cLong(c, "td_id"));
            if (task==null)
            {
                // We can proceed with the delete:
                result = tdInterface.deleteTask(Util.cLong(c,"td_id"));
                if (result!=ToodledoInterface.SUCCESS)
                {
            		Util.log("Could not delete Toodledo task ID "+Util.cLong(c,"td_id")+
            			".  "+ToodledoInterface.getFailureString(result));
                	if (result==ToodledoInterface.TOODLEDO_REJECT)
                	{
                		// Most likely, the task was already deleted.  Just move on.
                		deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                		continue;
                	}
                	else
                	{
                		c.close();
                		return result;
                	}
                }
                count++;
                this.logSyncedItem(UPLOAD, DELETE, TASK, new Long(Util.cLong(c,"td_id")).
                	toString());
            }
            
            // Remove the deletion info from the table:
            deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
        }
        c.close();
        Util.log("Uploaded "+count+" deleted tasks to Toodledo.");
        
        // Go through items that TD has reported deleted:
        Iterator<Long> it4 = deletedIdList.iterator();
        count = 0;
        while (it4.hasNext())
        {
            Long TDID = it4.next();
            
            // Look up the task info in the DB.  If this fails, then the task has already
            // been deleted:
            UTLTask deletedTask = tasksDB.getTask(accountID, TDID);
            if (deletedTask!=null)
            {
            	if (deletedTask.reminder>System.currentTimeMillis())
            	{
            		// Cancel the scheduled reminder notification:
            		Util.cancelReminderNotification(deletedTask._id);
            	}
            	
            	// Also remove any displayed notifications:
            	Util.removeTaskNotification(deletedTask._id);
            	
            	// Remove the linked calendar entry, if applicable:
            	if (deletedTask.calEventUri!=null && deletedTask.calEventUri.length()>0
            		&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            	{
            		CalendarInterface ci = new CalendarInterface(this);
            		ci.unlinkTaskFromCalendar(deletedTask);
            	}
            	
            	// It is possible for a sync conflict to occur, in which we have some
            	// UTL tasks that link to this deleted task.  Unlink them from the parent.
            	Cursor tasksCursor = tasksDB.queryTasks("parent_id="+deletedTask._id, null);
            	tasksCursor.moveToPosition(-1);
            	while (tasksCursor.moveToNext())
            	{
            		UTLTask child = tasksDB.getUTLTask(tasksCursor);
            		child.parent_id = 0;
            		child.mod_date = System.currentTimeMillis();
            		tasksDB.modifyTask(child);
            	}
            	tasksCursor.close();
            }
            
            // Delete the task from the DB.  This may fail, but that only means that
            // we have downloaded the deletion info twice, which is normal.
            tasksDB.deleteTask(TDID, accountID);
            count++;
            this.logSyncedItem(DOWNLOAD, DELETE, TASK, new Long(TDID).toString());
        }
        Util.log("Processed "+count+" deleted tasks from Toodledo.");
        if (count>0)
        {
            itemsDownloaded = true;
        }
        sendPercentComplete(DELETED_TASKS_PERCENT/_numSyncedAccounts);
        
        //
        // Notes Sync:
        //
        
        // If there are any notes that were deleted from TD since the last sync, then
        // download and store them:
        deletedIdList = new ArrayList<Long>();
        result = tdInterface.getDeletedNotes(tdDownloadThreshold, deletedIdList);
        if (result != ToodledoInterface.SUCCESS)
    	{
    		Util.log("Failed to download deleted notes from Toodledo. "+
    			ToodledoInterface.getFailureString(result));
    		return result;
    	}
    	Util.log("Downloaded "+deletedIdList.size()+" deleted notes.");
    	
        // Download added or edited notes (if any):
    	ArrayList<UTLNote> noteList = new ArrayList<UTLNote>();
    	result = tdInterface.getNotes(tdDownloadThreshold, noteList);
    	if (result != ToodledoInterface.SUCCESS)
    	{
    		Util.log("Failed to download added and edited notes from Toodledo. "+
    			ToodledoInterface.getFailureString(result));
    		return result;
    	}
    	Util.log("Downloaded "+noteList.size()+" added or edited notes.");
        sendPercentComplete(NOTES_DOWNLOAD_PERCENT/_numSyncedAccounts);
    	
    	// Process the downloaded notes and update our database:
    	Iterator<UTLNote> it5 = noteList.iterator();
    	int notesAdded = 0;
    	int notesEdited = 0;
    	NotesDbAdapter notesDB = new NotesDbAdapter();
    	while (it5.hasNext())
    	{
    		UTLNote tdNote = it5.next();
    		tdNote.mod_date += localMinusTD*1000; // Convert to local time.
    		
    		// See if there is a note with the name TD ID in the database:
    		UTLNote localNote = notesDB.getNote(accountID, tdNote.td_id);
    		if (localNote==null)
    		{
    			// The note must have been added, since it doesn't exist locally.
    			// Add the note to the DB:
    			long noteID = notesDB.addNote(tdNote);
    			if (noteID==-1)
    			{
    				Util.log("Could not add note from TD to database.");
    				return DB_FAILURE;
    			}
    			this.logSyncedItem(DOWNLOAD, ADD, NOTE, tdNote.title);
    			notesAdded++;

                // Record feature usage for the note.
                featureUsage.recordForNote(tdNote);
            }
    		else
    		{
    			// The note exists locally:
    			boolean tdWinsConflict = false;
    			if (localNote.mod_date > localNote.sync_date)
    			{
    				// Got a sync conflict, because the note has changed both
    				// locally and in Toodledo.  We will use the one with the later
    				// modification date.
    				if (localNote.mod_date<tdNote.mod_date)
    				{
    					// Toodledo wins the conflict, because its modification date
    					// is later.
    					tdWinsConflict = true;
    					Util.log("Got sync conflict for note ID "+localNote._id+" ("+
    						localNote.title+").  Toodledo wins because it was "+
    						"modified later.");
    				}
    				else
    				{
    					Util.log("Got sync conflict for note ID "+localNote._id+" ("+
    						localNote.title+").  Local copy wins because it was "+
    						"modified later.");
    				}
    			}
    			if (localNote.sync_date >= localNote.mod_date || tdWinsConflict)
    			{
    				// No sync conflict or TD wins conflict.  Update the local copy:
    				tdNote._id = localNote._id;
    				isSuccessful = notesDB.modifyNote(tdNote);
    				if (!isSuccessful)
        			{
        				Util.log("Database modification failed for edited note.");
        				return DB_FAILURE;
        			}
        			this.logSyncedItem(DOWNLOAD, MODIFY, NOTE, tdNote.title);
        			notesEdited++;

                    // Record feature usage for the note.
                    featureUsage.recordForNote(tdNote);
    			}
    		}
    	}
    	Util.log("Processed "+notesAdded+" notes added in Toodledo.");
    	Util.log("Processed "+notesEdited+" notes edited in Toodledo.");
    	if (notesAdded>0 || notesEdited>0)
    	{
    	    itemsDownloaded = true;
    	}
        sendPercentComplete(NOTES_UPDATE_DB_PERCENT/_numSyncedAccounts);
    	
    	// Upload notes that have been added or edited locally:
    	c = notesDB.queryNotes("mod_date>=sync_date and account_id="+accountID, "mod_date");
    	addCount = 0;
        modifyCount = 0;
        while (c.moveToNext())
        {
        	UTLNote localNote = notesDB.cursorToUTLNote(c);
        	
        	// Determine if this is an add or edit:
        	if (localNote.td_id==-1)
        	{
        		result = tdInterface.addNote(localNote);
        		if (result != ToodledoInterface.SUCCESS)
                {
                    Util.log("Could not add note '"+localNote.title+"' to Toodledo.  "+
                    	"Error: "+ToodledoInterface.getFailureString(result));
                    if (result==ToodledoInterface.TOODLEDO_REJECT)
                    {
                    	// TD rejected the note.  Rather than abort the sync,
                    	// continue trying other notes. For example, we might be
                    	// trying to upload a note containing a deleted folder.
                    	// If this happens, the note will sync next time after the
                    	// deleted folder has been processed here.
                    	continue;
                    }
                    c.close();
                    return result;
                }
        		this.logSyncedItem(UPLOAD, ADD, NOTE, localNote.title);
                addCount++;
                
                // Update the note's sync date:
                localNote.sync_date = System.currentTimeMillis();
                if (!notesDB.modifyNote(localNote))
                {
                	Util.log("Database failure when updating new note.");
                	c.close();
                	return DB_FAILURE;
                }
        	}
        	else
        	{
        		// Since the note has been previously synced, this is an edit operation.
        		// Before sending the edit command, check to see if TD has reported this 
        		// as a deleted item:
        		if (deletedIdList.contains(localNote.td_id))
        		{
        			Util.log("Sync conflict for TD note ID "+localNote.td_id+" ("+
        	            localNote.title+").  TD has reported this as deleted, but it "+
        	            "has also been edited on the handset.  Because TD no longer has "+
        	            "a record of the note, it must be deleted locally.");
        		}
        		else
        		{
        			result = tdInterface.editNote(localNote);
        			if (result != ToodledoInterface.SUCCESS)
                    {
                        Util.log("Could not edit note at Toodledo.  Error: "+
                            ToodledoInterface.getFailureString(result));
                        if (result==ToodledoInterface.TOODLEDO_REJECT)
                        {
                        	// TD rejected the note.  Rather than abort the sync,
                        	// continue trying other notes. For example, we might be
                        	// trying to upload a note containing a deleted folder.
                        	// If this happens, the note will sync next time after the
                        	// deleted folder has been processed here.
                        	continue;
                        }
                        c.close();
                        return result;
                    }
                    modifyCount++;
                    
                    // Update the sync date for the note:
                    localNote.sync_date = System.currentTimeMillis();
                    if (!notesDB.modifyNote(localNote))
                    {
                    	Util.log("Database failure when updating modified note.");
                    	c.close();
                    	return DB_FAILURE;
                    }
                    this.logSyncedItem(UPLOAD, MODIFY, NOTE, localNote.title);
        		}
        	}
        }
        c.close();
        Util.log("Uploaded "+addCount+" new notes to Toodledo.");
        Util.log("Uploaded "+modifyCount+" modified notes to Toodledo.");
        sendPercentComplete(NOTES_UPLOAD_PERCENT/_numSyncedAccounts);
    	
        //
        // Deleted notes:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("note",accountID);
        count = 0;
        while (c.moveToNext())
        {
        	// Make sure the note is not located in the notes table.  If it is, then 
        	// we have a conflict.  The note was modified in TD, but deleted locally.
        	// In this case, we don't send the delete operation to TD.
        	UTLNote note = notesDB.getNote(accountID,Util.cLong(c, "td_id"));
        	if (note==null)
        	{
        		// We can proceed with the delete:
        		result = tdInterface.deleteNote(Util.cLong(c,"td_id"));
        		if (result!=ToodledoInterface.SUCCESS)
                {
                    Util.log("Could not delete Toodledo note ID "+Util.cLong(c,"td_id")+
                        ".  "+ToodledoInterface.getFailureString(result));
                    if (result==ToodledoInterface.TOODLEDO_REJECT)
                	{
                		// Most likely, the note was already deleted.  Just move on.
                		deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                		continue;
                	}
                	else
                	{
                		c.close();
                		return result;
                	}
                }
        		count++;
        		this.logSyncedItem(UPLOAD, DELETE, NOTE, new Long(Util.cLong(c,"td_id")).
                	toString());
        	}
        	
        	// Remove the deletion info from the table:
            deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
        }
        c.close();
        Util.log("Uploaded "+count+" deleted notes to Toodledo.");
        
        // Go through items that TD has reported deleted:
        Iterator<Long> it6 = deletedIdList.iterator();
        count = 0;
        while (it6.hasNext())
        {
        	Long TDID = it6.next();
        	
        	// Delete the note from the DB.  This may fail, but that only means that
        	// we have downloaded the deletion info twice, which is normal.
        	notesDB.deleteNote(accountID,TDID);
        	count++;
        	this.logSyncedItem(DOWNLOAD, DELETE, NOTE, new Long(TDID).toString());
        }
        Util.log("Processed "+count+" deleted notes from Toodledo.");
        if (count>0)
        {
            itemsDownloaded = true;
        }
        sendPercentComplete(DELETED_NOTES_PERCENT/_numSyncedAccounts);
        
        //
        // Deleted folders:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("folder",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Send the deletion info to TD:
            result = tdInterface.deleteFolder(Util.cLong(c,"td_id"));
            if (result!=ToodledoInterface.SUCCESS &&
            	result!=ToodledoInterface.TOODLEDO_REJECT)
            {
                Util.log("Could not delete a folder in Toodledo.  "+
                    ToodledoInterface.getFailureString(result));
                c.close();
                return result;
            }
            else
            {
                // Remove the deletion info from the table:
                this.logSyncedItem(UPLOAD, DELETE, FOLDER, new Long(Util.cLong(c, "_id")).
                	toString());
                deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                count++;
            }
        }
        c.close();
        Util.log("Uploaded "+count+" deleted folders to Toodledo.");
        
        // Go through folders that were deleted from Toodledo:
        it4 = foldersDeletedFromTD.iterator();
        count = 0;
        while (it4.hasNext())
        {
            long folderID = it4.next();
            
            // If any tasks in our local database reference this folder, they need to
            // be updated.
            c = tasksDB.queryTasks("folder_id="+folderID, null);
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                UTLTask task = tasksDB.getUTLTask(c);
                task.folder_id = 0;
                task.mod_date = System.currentTimeMillis();
                isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.log("Could not clear folder for task ID "+task._id);
                    c.close();
                    return DB_FAILURE;
                }
            }
            c.close();
            
            // If any notes in our local database reference this folder, they need to 
            // be updated.
            c = notesDB.queryNotes("folder_id="+folderID, null);
            while (c.moveToNext())
            {
            	UTLNote note = notesDB.cursorToUTLNote(c);
            	note.folder_id = 0;
            	note.mod_date = System.currentTimeMillis();
            	notesDB.modifyNote(note);
            }
            c.close();
            
            // Delete the folder itself:
            isSuccessful = foldersDB.deleteFolder(folderID);
            if (!isSuccessful)
            {
                Util.log("Could not delete folder ID "+folderID);
            }
            count++;
        }
        Util.log("Processed "+count+" deleted folders from Toodledo.");
        if (count>0)
        {
            itemsDownloaded = true;
        }

        //
        // Deleted contexts:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("context",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Send the deletion info to TD:
            result = tdInterface.deleteContext(Util.cLong(c,"td_id"));
            if (result!=ToodledoInterface.SUCCESS &&
            	result!=ToodledoInterface.TOODLEDO_REJECT)
            {
                Util.log("Could not delete a context in Toodledo.  "+
                    ToodledoInterface.getFailureString(result));
                c.close();
                return result;
            }
            else
            {
                // Remove the deletion info from the table:
                this.logSyncedItem(UPLOAD, DELETE, CONTEXT, new Long(Util.cLong(c, "_id")).
                	toString());
                deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                count++;
            }
        }
        c.close();
        Util.log("Uploaded "+count+" deleted contexts to Toodledo.");
        
        // Go through contexts that were deleted from Toodledo:
        it4 = contextsDeletedFromTD.iterator();
        count = 0;
        while (it4.hasNext())
        {
            long contextID = it4.next();
            
            // If any tasks in our local database reference this context, they need to
            // be updated.
            c = tasksDB.queryTasks("context_id="+contextID, null);
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                UTLTask task = tasksDB.getUTLTask(c);
                task.context_id = 0;
                task.mod_date = System.currentTimeMillis();
                isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.log("Could not clear context for task ID "+task._id);
                    c.close();
                    return DB_FAILURE;
                }
            }
            c.close();
            
            // Delete the context itself:
            isSuccessful = contextsDB.deleteContext(contextID);
            if (!isSuccessful)
            {
                Util.log("Could not delete context ID "+contextID);
            }
            count++;
        }
        Util.log("Processed "+count+" deleted contexts from Toodledo.");
        if (count>0)
        {
            itemsDownloaded = true;
        }

        //
        // Deleted goals:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("goal",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Send the deletion info to TD:
            result = tdInterface.deleteGoal(Util.cLong(c,"td_id"));
            if (result!=ToodledoInterface.SUCCESS &&
            	result!=ToodledoInterface.TOODLEDO_REJECT)
            {
                Util.log("Could not delete a goal in Toodledo.  "+
                    ToodledoInterface.getFailureString(result));
                c.close();
                return result;
            }
            else
            {
                // Remove the deletion info from the table:
                this.logSyncedItem(UPLOAD, DELETE, GOAL, new Long(Util.cLong(c, "_id")).
                	toString());
                deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                count++;
            }
        }
        c.close();
        Util.log("Uploaded "+count+" deleted goals to Toodledo.");
        
        // Go through goals that were deleted from Toodledo:
        it4 = goalsDeletedFromTD.iterator();
        count = 0;
        while (it4.hasNext())
        {
            long goalID = it4.next();
            
            // If any tasks in our local database reference this goal, they need to
            // be updated.
            c = tasksDB.queryTasks("goal_id="+goalID, null);
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                UTLTask task = tasksDB.getUTLTask(c);
                task.goal_id = 0;
                task.mod_date = System.currentTimeMillis();
                isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.log("Could not clear goal for task ID "+task._id);
                    c.close();
                    return DB_FAILURE;
                }
            }
            c.close();
            
            // If any other goals contribute to this goal, their contributes field 
            // needs cleared:
            c = goalsDB.queryGoals("contributes="+goalID, null);
            while (c.moveToNext())
            {
                isSuccessful = goalsDB.setContributes(Util.cLong(c, "_id"), 0);
                if (!isSuccessful)
                {
                    Util.log("Could not update contributes field for goal ID "+
                        Util.cLong(c,"_id"));
                    c.close();
                    return DB_FAILURE;
                }
            }
            c.close();
            
            // Delete the goal itself:
            isSuccessful = goalsDB.deleteGoal(goalID);
            if (!isSuccessful)
            {
                Util.log("Could not delete goal ID "+goalID);
            }
            count++;
        }
        Util.log("Processed "+count+" deleted goals from Toodledo.");
        if (count>0)
        {
            itemsDownloaded = true;
        }
        
        //
        // Deleted Locations:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("location",accountID);
        count = 0;
        while (c.moveToNext())
        {
        	// Send the deletion info to TD:
        	result = tdInterface.deleteLocation(Util.cLong(c, "td_id"));
        	if (result!=ToodledoInterface.SUCCESS &&
            	result!=ToodledoInterface.TOODLEDO_REJECT)
        	{
        		Util.log("Could not delete a location in Toodledo.  "+
                    ToodledoInterface.getFailureString(result));
        		c.close();
                return result;
        	}
        	else
        	{
        		// Remove the deletion info from the table:
                this.logSyncedItem(UPLOAD, DELETE, LOCATION, new Long(Util.cLong(c, "_id")).
                	toString());
                deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                count++;
        	}
        }
        c.close();
        Util.log("Uploaded "+count+" deleted locations to Toodledo.");
        
        // Go through locations that were deleted from Toodledo:
        it4 = locationsDeletedFromTD.iterator();
        count = 0;
        while (it4.hasNext())
        {
        	long locID = it4.next();
        	
        	// If any tasks in our local database reference this location, they need to be
        	// updated.
        	c = tasksDB.queryTasks("location_id="+locID, null);
        	c.moveToPosition(-1);
        	while (c.moveToNext())
        	{
        		UTLTask task = tasksDB.getUTLTask(c);
                task.location_id = 0;
                task.location_reminder = false;
                task.location_nag = false;
                task.mod_date = System.currentTimeMillis();
                isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.log("Could not clear location for task ID "+task._id);
                    return DB_FAILURE;
                }
        	}
        	c.close();
        	
        	// Delete the location itself:
        	isSuccessful = locDB.deleteLocation(locID);
        	if (!isSuccessful)
        		Util.log("Could not delete location ID "+locID);
        	Util.deleteGeofence(this,locID);
        	count++;
        }
        Util.log("Processed "+count+" deleted locations from Toodledo.");
        if (count>0)
        	itemsDownloaded = true;
        
        sendPercentComplete(OTHER_DELETED_PERCENT/_numSyncedAccounts);

        Util.log("Full sync completed for account ID "+utlAccount._id+" ("+
            utlAccount.name+").");
        
        // Create a sample task if this is the first sync:
        if (settings.getBoolean(PrefNames.CREATE_SAMPLE_TASK, false))
        {
        	Util.updatePref(PrefNames.CREATE_SAMPLE_TASK, false, this);
        	
        	// Don't create the sample task if it's already in the database:
        	c = tasksDB.queryTasks("title='"+Util.makeSafeForDatabase(getString(R.string.
        		learn_more_about_utl))+"'", null);
        	if (!c.moveToFirst())
        	{
	        	UTLTask sample = new UTLTask();
	        	sample.account_id = utlAccount._id;
	        	sample.title = this.getString(R.string.learn_more_about_utl);
	        	sample.priority = 5;
	        	sample.star = true;
	        	sample.note = this.getString(R.string.First_Task_Note);
	        	sample.start_date = Util.getMidnight(System.currentTimeMillis(),this);
	        	sample.uses_start_time = false;
	        	sample.due_date = Util.getMidnight(System.currentTimeMillis(),this);
	        	sample.uses_due_time = false;
	        	sample.uuid = UUID.randomUUID().toString();
	        	if (tasksDB.addTask(sample)>=0)
	        	{
	        		tdInterface.addTask(sample, this);
	        	}
        	}
        	c.close();
        }
        
        return SUCCESS;
    }
    
    // Instant upload feature: add a task:
    private void instantAddTask(long taskID, ToodledoInterface tdInterface)
    {
    	// Get the task from the DB:
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	UTLTask localTask = tasksDB.getTask(taskID);
    	if (localTask==null)
    	{
    		Util.log("Nonexistent task ID "+taskID+" passed to instantAddTask().");
    		return;
    	}

    	if (localTask.mod_date<localTask.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the task.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	// Get a reference to the task's account:
    	UTLAccount utlAccount = (new AccountsDbAdapter()).getAccount(localTask.account_id);
    	if (utlAccount==null)
    	{
    		Util.log("Task ID "+taskID+" refers to a nonexistent account ("+
    			localTask.account_id+").");
    		return;
    	}
    	
    	// If this is a subtask and the user is not on a pro TD account, then we 
    	// have to skip this.
    	if (!utlAccount.pro && localTask.parent_id!=0)
    	{
    		return;
    	}
    	
		// If the task has a parent that has not been uploaded, we need to abort:
		if (localTask.parent_id>0)
		{
			UTLTask parent = (new TasksDbAdapter()).getTask(localTask.parent_id);
			if (parent==null || parent.td_id==-1)
			{
				Util.log("Aborting upload because parent has not been uploaded.");
				return;
			}
		}
		
    	// Upload it:
    	int result = tdInterface.addTask(localTask, this);
        if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not add task to Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
        
        // Finally, update the sync date for the task:
        if (!tasksDB.updateSyncDate(localTask._id))
        {
        	Util.log("Database failure when updating task sync date.");
        }
        
        this.logSyncedItem(UPLOAD, ADD, TASK, localTask.title);
        this.logTaskDetails(localTask);
        Util.log("New task was successfully uploaded.");
    }
    
    // Instant upload feature: edit a task:
    private void instantEditTask(long taskID, ToodledoInterface tdInterface)
    {
    	// Get the task from the DB:
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	UTLTask localTask = tasksDB.getTask(taskID);
    	if (localTask==null)
    	{
    		Util.log("Nonexistent task ID "+taskID+" passed to instantEditTask().");
    		return;
    	}

    	if (localTask.mod_date<localTask.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the task.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	// Get a reference to the task's account:
    	UTLAccount utlAccount = (new AccountsDbAdapter()).getAccount(localTask.account_id);
    	if (utlAccount==null)
    	{
    		Util.log("Task ID "+taskID+" refers to a nonexistent account ("+
    			localTask.account_id+").");
    		return;
    	}
    	
    	// If this is a subtask and the user is not on a pro TD account, then we 
    	// have to skip this.
    	if (!utlAccount.pro && localTask.parent_id!=0)
    	{
    		return;
    	}
    	
		// If the task has a parent that has not been uploaded, we need to abort:
		if (localTask.parent_id>0)
		{
			UTLTask parent = (new TasksDbAdapter()).getTask(localTask.parent_id);
			if (parent==null || parent.td_id==-1)
			{
				Util.log("Aborting upload because parent has not been uploaded.");
				return;
			}
		}
		
    	// Upload it:
	    int result = tdInterface.editTask(localTask,this);
        if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not edit task at Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
        
        // Finally, update the sync date for the task:
        if (!tasksDB.updateSyncDate(localTask._id))
        {
        	Util.log("Database failure when updating task sync date.");
        }
        
        this.logSyncedItem(UPLOAD, MODIFY, TASK, localTask.title);
        this.logTaskDetails(localTask);
        Util.log("Edited task was successfully uploaded.");
    }

    private void instantDeleteTask(long taskTDID, long accountID, 
    	ToodledoInterface tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("task",accountID,taskTDID);
    	if (rowID==0)
    	{
    		// Already Deleted (perhaps during a recent full sync).  Nothing to do.
    		return;
    	}
    	
    	// Make sure the task is not located in the tasks table.  If it is, then
        // we have a conflict.  The task was modified in TD, but deleted locally.
        // In this situation, we don't send the delete operation to TD.
        UTLTask task = (new TasksDbAdapter()).getTask(accountID, taskTDID);
        if (task==null)
        {
            // We can proceed with the delete:
            int result = tdInterface.deleteTask(taskTDID);
            if (result!=ToodledoInterface.SUCCESS)
            {
                Util.log("Could not delete Toodledo task ID "+taskTDID+
                    ".  "+ToodledoInterface.getFailureString(result));
                return;
            }
        }
        
        // Remove the deletion info from the table:
        deletesDB.deletePendingDelete(rowID);
        this.logSyncedItem(UPLOAD, DELETE, TASK, new Long(taskTDID).toString());
    }
    
    // Instant upload: add folder:
    private void instantAddFolder(long folderID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new FoldersDbAdapter()).getFolder(folderID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.addFolder(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the folder because it already exists.  Delete the folder 
                	// locally and update any tasks that have it.
                	long rejectedFolder = Util.cLong(c, "_id");
                	Util.log("Removing the rejected folder and updating tasks.");
                	TasksDbAdapter tasksDB = new TasksDbAdapter();
                	Cursor c2 = tasksDB.queryTasks("folder_id="+rejectedFolder,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.folder_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	new FoldersDbAdapter().deleteFolder(rejectedFolder);
                }
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, ADD, FOLDER, Util.cString(c, "title"));
            (new FoldersDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }
    
    // Instant upload: edit folder
    private void instantEditFolder(long folderID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new FoldersDbAdapter()).getFolder(folderID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.editFolder(c);
            if (result != ToodledoInterface.SUCCESS)
            {
            	Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
            (new FoldersDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }
    
    // Instant upload: delete folder
    private void instantDeleteFolder(long folderTDID, long accountID, ToodledoInterface
    	tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("folder", accountID, folderTDID);
    	if (rowID==0)
    	{
    		// Already deleted.  Nothing to do.
    		return;
    	}
    	
    	int result = tdInterface.deleteFolder(folderTDID);
    	if (result!=ToodledoInterface.SUCCESS)
        {
    		Util.log("Could not delete a folder in Toodledo.  "+
                ToodledoInterface.getFailureString(result));
        }
    	else
    	{
    		// Remove the deletion info from the table:
            this.logSyncedItem(UPLOAD, DELETE, FOLDER, new Long(folderTDID).
            	toString());
            deletesDB.deletePendingDelete(rowID);
    	}
    }
    
    // Instant upload: add a context:
    private void instantAddContext(long contextID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new ContextsDbAdapter()).getContext(contextID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.addContext(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add context ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the context because it already exists.  Delete the context 
                	// locally and update any tasks that have it.
                	long rejectedContext = Util.cLong(c, "_id");
                	Util.log("Removing the rejected context and updating tasks.");
                	TasksDbAdapter tasksDB = new TasksDbAdapter();
                	Cursor c2 = tasksDB.queryTasks("context_id="+rejectedContext,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.context_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	new ContextsDbAdapter().deleteContext(rejectedContext);
                }
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, ADD, CONTEXT, Util.cString(c, "title"));
            (new ContextsDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }
    
    // Instant upload: edit a context:
    private void instantEditContext(long contextID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new ContextsDbAdapter()).getContext(contextID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.editContext(c);
            if (result != ToodledoInterface.SUCCESS)
            {
            	Util.log("Could not edit context ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, MODIFY, CONTEXT, Util.cString(c, "title"));
            (new ContextsDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }
    
    // Instant upload: delete context
    private void instantDeleteContext(long contextTDID, long accountID, ToodledoInterface
    	tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("context", accountID, contextTDID);
    	if (rowID==0)
    	{
    		// Already deleted.  Nothing to do.
    		return;
    	}
    	
    	int result = tdInterface.deleteContext(contextTDID);
    	if (result!=ToodledoInterface.SUCCESS)
        {
    		Util.log("Could not delete a context in Toodledo.  "+
                ToodledoInterface.getFailureString(result));
        }
    	else
    	{
    		// Remove the deletion info from the table:
            this.logSyncedItem(UPLOAD, DELETE, CONTEXT, new Long(contextTDID).
            	toString());
            deletesDB.deletePendingDelete(rowID);
    	}
    }

    // Instant upload: add goal:
    private void instantAddGoal(long goalID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new GoalsDbAdapter()).getGoal(goalID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.addGoal(c);
            if (result != ToodledoInterface.SUCCESS)
            {
                Util.log("Could not add goal ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
                if (result==ToodledoInterface.TOODLEDO_REJECT)
                {
                	// Most likely, what happened was that Toodledo rejected the adding
                	// of the goal because it already exists.  Delete the goal 
                	// locally and update any tasks that have it.
                	TasksDbAdapter tasksDB = new TasksDbAdapter();
                	GoalsDbAdapter goalsDB = new GoalsDbAdapter();
                	long rejectedGoal = Util.cLong(c, "_id");
                	Util.log("Removing the rejected goal and updating tasks.");                	
                	Cursor c2 = tasksDB.queryTasks("goal_id="+rejectedGoal,"_id");
                	c2.moveToPosition(-1);
                	while (c2.moveToNext())
                	{
                		UTLTask t2 = tasksDB.getUTLTask(c2);
                		t2.goal_id = 0;
                		t2.mod_date = System.currentTimeMillis();
                		tasksDB.modifyTask(t2);
                	}
                	c2.close();
                	goalsDB.deleteGoal(rejectedGoal);
                	SQLiteDatabase db = Util.dbHelper.getWritableDatabase();
                	db.execSQL("update goals set contributes=0 where contributes="+
                		rejectedGoal);
                }
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, ADD, GOAL, Util.cString(c, "title"));
            (new GoalsDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }

    // Instant upload: edit goal
    private void instantEditGoal(long goalID, ToodledoInterface tdInterface)
    {
    	Cursor c = (new GoalsDbAdapter()).getGoal(goalID);
    	if (c.moveToFirst())
    	{
    		int result = tdInterface.editGoal(c);
            if (result != ToodledoInterface.SUCCESS)
            {
            	Util.log("Could not edit goal ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") in Toodledo.  Error message: "+
                    ToodledoInterface.getFailureString(result));
            	c.close();
            	return;
            }
            this.logSyncedItem(UPLOAD, MODIFY, GOAL, Util.cString(c, "title"));
            (new GoalsDbAdapter()).setSyncDate(Util.cLong(c, "_id"), System.
				currentTimeMillis());
    	}
    	c.close();
    }

    // Instant upload: delete goal
    private void instantDeleteGoal(long goalTDID, long accountID, ToodledoInterface
    	tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("goal", accountID, goalTDID);
    	if (rowID==0)
    	{
    		// Already deleted.  Nothing to do.
    		return;
    	}
    	
    	int result = tdInterface.deleteGoal(goalTDID);
    	if (result!=ToodledoInterface.SUCCESS)
        {
    		Util.log("Could not delete a goal in Toodledo.  "+
                ToodledoInterface.getFailureString(result));
        }
    	else
    	{
    		// Remove the deletion info from the table:
            this.logSyncedItem(UPLOAD, DELETE, GOAL, new Long(goalTDID).
            	toString());
            deletesDB.deletePendingDelete(rowID);
    	}
    }

    // Instant Upload: Add Location:
    private void instantAddLocation(long locID, ToodledoInterface tdInterface)
    {
    	// Get the location from the DB:
    	LocationsDbAdapter locDB = new LocationsDbAdapter();
    	UTLLocation localLoc = locDB.getLocation(locID);
    	if (localLoc==null)
    	{
    		Util.log("Nonexistent location ID "+locID+" passed to instantAddLocation().");
    		return;
    	}
    	
    	if (localLoc.mod_date<localLoc.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the location.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	// Upload it:
    	int result = tdInterface.addLocation(localLoc);
    	if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not add location to Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
    	
    	// Update the sync date for the location:
    	localLoc.sync_date = System.currentTimeMillis();
    	if (!locDB.modifyLocation(localLoc))
    	{
    		Util.log("Database failure when updating location's sync date.");
    	}
    	
    	this.logSyncedItem(UPLOAD, ADD, LOCATION, localLoc.title);
    	Util.log("New location uploaded successfully.");
    }
    
    // Instant Upload: edit location:
    private void instantEditLocation(long locID, ToodledoInterface tdInterface)
    {
    	// Get the location from the DB:
    	LocationsDbAdapter locDB = new LocationsDbAdapter();
    	UTLLocation localLoc = locDB.getLocation(locID);
    	if (localLoc==null)
    	{
    		Util.log("Nonexistent location ID "+locID+" passed to instantEditLocation().");
    		return;
    	}
    	
    	if (localLoc.mod_date<localLoc.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the location.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	int result = tdInterface.editLocation(localLoc);
    	if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not edit location at Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
    	
    	// Update the sync date for the location:
    	localLoc.sync_date = System.currentTimeMillis();
    	if (!locDB.modifyLocation(localLoc))
    	{
    		Util.log("Database failure when updating location's sync date.");
    	}
    	
    	this.logSyncedItem(UPLOAD, MODIFY, LOCATION, localLoc.title);
    	Util.log("Modified location uploaded successfully.");
    }

    // Instant upload: delete location:
    private void instantDeleteLocation(long locationTDID, long accountID, 
    	ToodledoInterface tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("location",accountID,locationTDID);
    	if (rowID==0)
    	{
    		// Already Deleted (perhaps during a recent full sync).  Nothing to do.
    		return;
    	}
    	
    	// Make sure the location is not located in the locations table.  If it is, then we
    	// have a conflict.  The location was modified in TD, but deleted locally.
    	// In this situation, we don't send the delete to TD.
    	UTLLocation location = (new LocationsDbAdapter()).getLocation(accountID,locationTDID);
    	if (location==null)
    	{
    		// We can proceed with the delete:
            int result = tdInterface.deleteLocation(locationTDID);
            if (result!=ToodledoInterface.SUCCESS)
            {
                Util.log("Could not delete Toodledo location ID "+locationTDID+
                    ".  "+ToodledoInterface.getFailureString(result));
                return;
            }
    	}
    	
    	// Remove the deletion info from the table:
        deletesDB.deletePendingDelete(rowID);
        this.logSyncedItem(UPLOAD, DELETE, LOCATION, new Long(locationTDID).toString());
    }

    // Instant Upload: Reassign Task
    private void instantReassignTask(long rowID, ToodledoInterface tdInterface)
    {
    	PendingReassignmentsDbAdapter prdb = new PendingReassignmentsDbAdapter();
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
        Cursor c = prdb.getReassignment(rowID);
        if (c.moveToFirst())
        {
        	int result = tdInterface.reassignTask(Util.cLong(c,"task_id"), Util.cString(c,
        		"new_owner_id"), this);
        	if (result != ToodledoInterface.SUCCESS)
        	{
        		Util.log("Could not reassign task ID "+Util.cLong(c,"task_id")+
        			".  "+ToodledoInterface.getFailureString(result));
        		c.close();
        		return;
        	}
        	else
        	{
        		// A successfully reassigned task no longer appears on the current users list.
        		tasksDB.deleteTask(Util.cLong(c,"task_id"));
        	}
        	
        	// Remove the reassignment info:
        	prdb.deleteReassignment(Util.cLong(c, "_id"));
        }
        c.close();
    }
    
    // Instant Upload: Add Note:
    private void instantAddNote(long noteID, ToodledoInterface tdInterface)
    {
    	// Get the note from the DB:
    	NotesDbAdapter notesDB = new NotesDbAdapter();
    	UTLNote localNote = notesDB.getNote(noteID);
    	if (localNote==null)
    	{
    		Util.log("Nonexistent note ID "+noteID+" passed to instantAddNote().");
    		return;
    	}
    	
    	if (localNote.mod_date<localNote.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the note.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	// Upload it:
    	int result = tdInterface.addNote(localNote);
    	if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not add note to Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
    	
    	// Update the sync date for the note:
    	localNote.sync_date = System.currentTimeMillis();
    	if (!notesDB.modifyNote(localNote))
    	{
    		Util.log("Database failure when updating note's sync date.");
    	}
    	
    	this.logSyncedItem(UPLOAD, ADD, NOTE, localNote.title);
    	Util.log("New note uploaded successfully.");
    }
    
    // Instant Upload: edit note:
    private void instantEditNote(long noteID, ToodledoInterface tdInterface)
    {
    	// Get the note from the DB:
    	NotesDbAdapter notesDB = new NotesDbAdapter();
    	UTLNote localNote = notesDB.getNote(noteID);
    	if (localNote==null)
    	{
    		Util.log("Nonexistent note ID "+noteID+" passed to instantEditNote().");
    		return;
    	}
    	
    	if (localNote.mod_date<localNote.sync_date)
    	{
    		// The modification date is prior to the sync date.  Most likely there was 
    		// a full sync running when the user created or modified the note.  So, there
    		// is nothing to do.
    		Util.log("Not uploading.  The mod date is earlier than the sync date.");
    		return;
    	}
    	
    	int result = tdInterface.editNote(localNote);
    	if (result != ToodledoInterface.SUCCESS)
        {
            Util.log("Could not edit note at Toodledo.  Error: "+
                ToodledoInterface.getFailureString(result));
            return;
        }
    	
    	// Update the sync date for the note:
    	localNote.sync_date = System.currentTimeMillis();
    	if (!notesDB.modifyNote(localNote))
    	{
    		Util.log("Database failure when updating note's sync date.");
    	}
    	
    	this.logSyncedItem(UPLOAD, MODIFY, NOTE, localNote.title);
    	Util.log("Modified note uploaded successfully.");
    }
    
    // Instant upload: delete note:
    private void instantDeleteNote(long noteTDID, long accountID, 
    	ToodledoInterface tdInterface)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("note",accountID,noteTDID);
    	if (rowID==0)
    	{
    		// Already Deleted (perhaps during a recent full sync).  Nothing to do.
    		return;
    	}
    	
    	// Make sure the note is not located in the notes table.  If it is, then we
    	// have a conflict.  The note was modified in TD, but deleted locally.
    	// In this situation, we don't send the delete to TD.
    	UTLNote note = (new NotesDbAdapter()).getNote(accountID,noteTDID);
    	if (note==null)
    	{
    		// We can proceed with the delete:
            int result = tdInterface.deleteNote(noteTDID);
            if (result!=ToodledoInterface.SUCCESS)
            {
                Util.log("Could not delete Toodledo note ID "+noteTDID+
                    ".  "+ToodledoInterface.getFailureString(result));
                return;
            }
    	}
    	
    	// Remove the deletion info from the table:
        deletesDB.deletePendingDelete(rowID);
        this.logSyncedItem(UPLOAD, DELETE, NOTE, new Long(noteTDID).toString());
    }
    
    // Perform a full sync of a Google account.  This returns an error or success code from 
    // the GTasksInterface class.
    private int doFullGoogleSync(long accountID)
    {
    	// Get handles into the database:
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	AccountsDbAdapter accountsDB = new AccountsDbAdapter();
    	FoldersDbAdapter foldersDB = new FoldersDbAdapter();
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	TagsDbAdapter tagsDB = new TagsDbAdapter();
    	CurrentTagsDbAdapter currentTagsDB = new CurrentTagsDbAdapter();
        FeatureUsage featureUsage = new FeatureUsage(this);

    	// Get the account information from the database:
    	UTLAccount utlAccount = accountsDB.getAccount(accountID);
    	if (utlAccount == null)
    	{
            Util.log("Bad account ID passed into Synchronizer.doFullGoogleSync");
            return GTasksInterface.INTERNAL_ERROR;
        }
    	Util.log("Starting full sync for Google account "+accountID+" ("+utlAccount.name+") "+
        	"with e-mail "+utlAccount.username);
    	
    	// Get a reference to the GTasksInterface:
    	if (!_gTasksInterfaceHash.containsKey(accountID))
    	{
    		_gTasksInterfaceHash.put(accountID, new GTasksInterface(this,utlAccount));
    	}
    	GTasksInterface gtInterface = _gTasksInterfaceHash.get(accountID);

    	// If a failure occurs, store the associated account.
		GTasksInterface._accountToSignInto = gtInterface._account;
    	
    	//
    	// Folder / Tasklist sync:
    	//
    	
    	// This keeps track of folders that were sent to Google via an add or edit:
        HashSet<Long> foldersUploaded = new HashSet<Long>();
        
        // This keeps track of folders that were deleted from Google:
        HashSet<Long> foldersDeletedFromGT = new HashSet<Long>();
        
        // Check the modification dates for the folders to see if any changes need to be
        // uploaded.
        Cursor c = foldersDB.queryFolders("remote_id!='' and mod_date>sync_date"+
            " and account_id="+accountID,null);
        int count = 0;
        int result;
        while (c.moveToNext())
        {
        	result = gtInterface.modifyFolder(c);
        	if (result != GTasksInterface.SUCCESS)
            {
                Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") at Google.  Error message: "+
                    GTasksInterface.getErrorMessage(result));
                if (result==GTasksInterface.MISC_ERROR && GTasksInterface.
                	_lastHttpErrorCode==404)
                {
                	// This means we are trying to modify a folder that Google thinks
                	// does not exist.  Try adding the folder instead.
                	Util.log("Trying to add the folder instead.");
                	result = gtInterface.addFolder(c);
                	if (result != GTasksInterface.SUCCESS)
                	{
                		// The add failed as well. We have no choice but to abandon the
                		// sync.
                		Util.log("Attempt to add the folder failed. Error Message: "+
                			GTasksInterface.getErrorMessage(result));
                		c.close();
                		return result;
                	}
                }
                else
                {
                	c.close();
                	return result;
                }
            }
        	count++;
        	foldersUploaded.add(Util.cLong(c,"_id"));
        	this.logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" edited folders to Google.");
        
        // Look for folders that have not been uploaded to Google, and upload them:
        c = foldersDB.queryFolders("remote_id='' and account_id="+accountID, null);
        count = 0;
        while (c.moveToNext())
        {
        	result = gtInterface.addFolder(c);
        	if (result != GTasksInterface.SUCCESS)
        	{
        		Util.log("Could not add folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Google.  Error message: "+
                    GTasksInterface.getErrorMessage(result));
        		c.close();
        		return result;
        	}
        	count++;
        	foldersUploaded.add(Util.cLong(c,"_id"));
            this.logSyncedItem(UPLOAD, ADD, FOLDER, Util.cString(c, "title"));
            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());
        }
        c.close();
        Util.log("Uploaded "+count+" new folders to Google.");
        
        // Download the list of folders from Google.  (Google has no method to check
        // to see if this is necessary, so we must do this every time.)
        ArrayList<HashMap<String,String>> gFolders = new ArrayList<HashMap<String,
        	String>>();
        AtomicLong gTimestamp = new AtomicLong();
        StringBuilder eTagBuilder = new StringBuilder();
        result = gtInterface.getFolders(gFolders,gTimestamp,eTagBuilder);
        if (result!=GTasksInterface.SUCCESS)
        {
        	Util.log("Could not get folder list from Google. "+GTasksInterface.
        		getErrorMessage(result));
        	return result;
        }
        
        // Record the time difference between us and Google:
        long localMinusGoogle = System.currentTimeMillis() - gTimestamp.get();
        Util.log("Local time is "+localMinusGoogle+" ms ahead of Google.");
        
        // Save the eTag returned from google:
		// 7/22/19: Google no longer updates etags reliabily, so we always have to check
		// for task updates.
        // String eTag = eTagBuilder.toString();
        String eTag = String.valueOf(System.currentTimeMillis());
        Util.log("eTag From Google: "+eTag);
        
        // Iterate through the folder list and perform add and modify ops:
        int addedCount = 0;
        int modifiedCount = 0;
        HashSet<String> downloadedFolderIDs = new HashSet<String>();
        Iterator<HashMap<String,String>> it = gFolders.iterator();
        boolean isSuccessful;
        while (it.hasNext())
        {
        	HashMap<String,String> folderHash = it.next();
        	String gID = folderHash.get("id");
        	String gTitle = folderHash.get("title");
        	downloadedFolderIDs.add(gID);
        	try
        	{
	        	c = foldersDB.getFolder(accountID, gID);
	        	if (!c.moveToFirst())
	        	{
	        		// We have a folder that exists at Google, but not locally.
	        		if (0==deletesDB.isDeletePending("folder", accountID, "", gID))
	                {
	        			// The folder is not being deleted locally, so this was added 
	                    // from Google.  Add it locally also.
	        			long folderID = foldersDB.addFolder(-1, accountID, gTitle, false,
	        				gID);
	        			if (folderID==-1)
	                    {
	                        Util.log("Could not insert new folder from Google into database.");
	                        return GTasksInterface.DB_FAILURE;
	                    }
	        			else
	        			{
	        				addedCount++;
	                        this.logSyncedItem(DOWNLOAD, ADD, FOLDER, gTitle);
	                        foldersDB.setSyncDate(folderID,System.currentTimeMillis());
	        			}
	                }
	        	}
	        	else
	        	{
	        		// The folder exists in both places.  Check for differences:
	        		if (!foldersUploaded.contains(Util.cLong(c, "_id")) && 
	        			!Util.cString(c, "title").equals(gTitle))
	        		{
	        			if (Util.cLong(c, "mod_date")>Util.cLong(c, "sync_date"))
	        			{
	                        // The folders differ and the local copy was modified since the
	                        // last sync.  This is unlikely to happen, but it can happen.
	                        // if the user makes a folder change in between the uploading
	                        // of edits and this code.
	        				result = gtInterface.modifyFolder(c);
	        				if (result != GTasksInterface.SUCCESS)
	        				{
	        					Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
	                                Util.cString(c, "title")+") at Google.  Error message: "+
	                                GTasksInterface.getErrorMessage(result));
	                            return result;
	        				}
	        				foldersUploaded.add(Util.cLong(c,"_id"));   
	                        this.logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
	                        foldersDB.setSyncDate(Util.cLong(c,"_id"),System.currentTimeMillis());        				
	        			}
	        			else
	        			{
	        				// The folder has not been modified since the last sync, so
	                        // it must have changed from Google.  Update the local copy.
	        				isSuccessful = foldersDB.modifyFolder(Util.cLong(c,"_id"), 
	        					-1, accountID, gTitle, Util.cInt(c, "archived")==1, false);
	        				if (!isSuccessful)
	                        {
	                            Util.log("Could not modify folder that was downloaded "+
	                                "from Google.  DB Update failed.");
	                            return GTasksInterface.DB_FAILURE;
	                        }
	        				else
	        				{
	        					modifiedCount++;
	                            this.logSyncedItem(DOWNLOAD, MODIFY, FOLDER, gTitle);
	                            foldersDB.setSyncDate(Util.cLong(c,"_id"),System.
	                            	currentTimeMillis());
	        				}
	        			}
	        		}
	        	}
        	}
        	finally
        	{
        		c.close();
        	}
        }        	
    	Util.log("Folders from Google added locally: "+addedCount);
        Util.log("Folders from Google modified locally: "+modifiedCount);      
        if (addedCount>0 || modifiedCount>0)
            itemsDownloaded = true;
        
        // Look for folders that exist locally but aren't on Google. (Folders 
        // deleted at Google).
        c = foldersDB.queryFolders("account_id="+accountID+" and remote_id!=''", null);
        count = 0;
        while (c.moveToNext())
        {
        	if (!downloadedFolderIDs.contains(Util.cString(c, "remote_id")))
        	{
                // The folder exists locally but is not on Google.  We also know that
                // it has been previously sent to Google because the remote_id field is
                // not blank.  In this case, we know that the folder has been
                // deleted.  Add this to a list of deleted folders for later processing.
        		foldersDeletedFromGT.add(Util.cLong(c, "_id"));
                count++;
                this.logSyncedItem(DOWNLOAD, DELETE, FOLDER, Util.cString(c,"title"));
        	}
        }
        c.close();
        
        // End of folder sync.  Update percent complete:
        sendPercentComplete(FOLDER_SYNC_PERCENT_GT/_numSyncedAccounts);
        
        //
        // Tasks Sync:
        //
        
        // This Hash will be used to keep track of tasks that exist locally but do not 
        // exist at Google.  These will be deleted tasks.
        HashSet<String> deletedTaskHash = new HashSet<String>();

        ArrayList<UTLTask> taskList = new ArrayList<UTLTask>();
    	ArrayList<String> tagList = new ArrayList<String>();

    	if (!eTag.equals(utlAccount.etag))
        {
	        // We know something changed in the account. Download the list of tasks from Google:
	    	Util.log("Looking for tasks updated after "+Util.getDateTimeString(utlAccount.
	    		last_sync)+" Google time.");
	    	result = gtInterface.getTasks(utlAccount.last_sync+1000, taskList, 
	    		tagList, deletedTaskHash, _sendPercentComplete, TASK_DOWNLOAD_PERCENT_GT/
	    		_numSyncedAccounts, _percentComplete, mClients);
	        if (result != GTasksInterface.SUCCESS)
	    	{
	    		Util.log("Failed to download added, edited, and deleted tasks from Google. "+
	    			GTasksInterface.getErrorMessage(result));
	    		return result;
	    	}
        }
    	else
    	{
    		// The eTags are the same, so nothing has changed on Google's side since the last sync.
    		Util.log("Skipping download of tasks since the eTag has not changed.");
    	}
        
        // Validate the tasks received.  If Google sent us bad data, due to bugs on
        // Google's side, then we need to download all tasks (not just those modified
        // recently).
        HashSet<String> remoteIdHash = new HashSet<String>();
        Iterator<UTLTask> it2 = taskList.iterator();
        boolean googleSentBadData = false;
        while (it2.hasNext())
        {
        	UTLTask t = it2.next();
        	if (remoteIdHash.contains(t.remote_id))
        	{
        		// We encountered the same task twice in Google's data, so we can't 
        		// trust what Google sent us.
        		Util.log("Google sent the same task twice ("+t.title+").  To clear up "+
        			"the discrepancy, all tasks will be downloaded.");
        		googleSentBadData = true;
        		break;
        	}
        	remoteIdHash.add(t.remote_id);
        }
        
        // If Google's data is bad, try downloading all tasks instead of just the ones
        // that have been updated.
        if (googleSentBadData)
        {
        	taskList.clear();
        	tagList.clear();
        	deletedTaskHash.clear();
        	result = gtInterface.getTasks(0, taskList, tagList, 
        		deletedTaskHash, _sendPercentComplete, TASK_DOWNLOAD_PERCENT_GT/
        		_numSyncedAccounts, _percentComplete, mClients);
            if (result != GTasksInterface.SUCCESS)
        	{
        		Util.log("Failed to download added, edited, and deleted tasks from Google. "+
        			GTasksInterface.getErrorMessage(result));
        		return result;
        	}
        }
        
        Util.log("Downloaded "+taskList.size()+" tasks from Google.");
        sendPercentComplete(TASK_DOWNLOAD_PERCENT_GT/_numSyncedAccounts);
        
        // Go through the task adds and edits:
        ArrayList<UTLTask> addedTasks = new ArrayList<UTLTask>();
        int i = -1;
        it2 = taskList.iterator();
        int tasksAdded = 0;
    	int tasksEdited = 0;
    	int completedTasksIgnored = 0;
    	long numDays = new Integer(settings.getInt("purge_completed", 365)).longValue();
    	long ignoreThreshold = System.currentTimeMillis() - numDays*24*60*60*1000;
		Util.log("Ignore Threshold: "+Util.getDateTimeString(ignoreThreshold,this));
		long lastPercentCompleteUpdate = System.currentTimeMillis();
		int basePercentComplete = _percentComplete;
		float totalDbUpdateIncrement = (float)TASK_UPDATE_DB_PERCENT_GT/(float)
			_numSyncedAccounts;
		clearTempTags();
		boolean tasksNeedParent = false;
		long latestModDate = utlAccount.last_sync;
		CalendarInterface ci = new CalendarInterface(this);
		HashSet<Long> foldersWithSortChange = new HashSet<Long>();
		HashSet<Long> parentsWithSortChange = new HashSet<Long>();
    	while (it2.hasNext())
    	{
    		i++;
    		UTLTask gTask = it2.next();
    		
    		// If the task is an old, completed task, then do not store it here:
			if (gTask.completed && gTask.completion_date>0 && 
				gTask.completion_date<ignoreThreshold)
			{
        		if ((System.currentTimeMillis()-lastPercentCompleteUpdate)>1000)
        		{
        			float percentCompleteIncrement = totalDbUpdateIncrement*(float)i/
        				(float)taskList.size();
        			_percentComplete += (int)(basePercentComplete+percentCompleteIncrement);
        			sendPercentComplete(0);
        			lastPercentCompleteUpdate = System.currentTimeMillis();
        		}
        		completedTasksIgnored++;
				continue;
			}
			
    		// Keep track of the latest modification date we receive (Google time).  This 
			// will be the basis for the following sync.
    		if (gTask.mod_date > latestModDate)
    			latestModDate = gTask.mod_date;
    		
			// We need to make sure that the mod date that is stored in the database is based
			// on our device's time, and not Google's
    		gTask.mod_date += localMinusGoogle;

    		// If a child was downloaded before its parent, record this so we can
			// establish the link later:
			boolean currentTaskNeedsParent = false;
			if (gTask.note.length()>13 && gTask.note.substring(0, 13).equals("Needs Parent:"))
			{
				tasksNeedParent = true;
				currentTaskNeedsParent = true;
			}
			
			// See if there is a task with the same Google ID in the database:
			UTLTask localTask;
			if (Util.isValid(gTask.uuid))
				localTask = tasksDB.getTask(accountID, gTask.remote_id, gTask.uuid);
			else
				localTask = tasksDB.getTask(accountID, gTask.remote_id);
    		if (localTask==null)
    		{
    			// The task must have been added, since it doesn't exist locally.
    			
    			// If the user wants this on the calendar, then add it:
    			if (settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false)
    				&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    			{
    				String eventUri = ci.linkTaskWithCalendar(gTask);
    				if (!eventUri.startsWith(CalendarInterface.ERROR_INDICATOR))
    				{
    					gTask.calEventUri = eventUri;
    				}
    				else
    				{
    					Util.log("Could not add downloaded task to calendar. "+
    						eventUri.substring(CalendarInterface.ERROR_INDICATOR.
    						length()));
    				}
    			}

                // Record any features that the task uses:
                featureUsage.recordForTask(gTask);

                // Add the task to the DB:
    			long taskID = tasksDB.addTask(gTask);
    			if (taskID==-1)
    			{
    				Util.log("Could not add new task to database.");
    				Util.log(TasksDbAdapter.getLastTaskString());
    				return GTasksInterface.DB_FAILURE;
    			}
    			this.logSyncedItem(DOWNLOAD, ADD, TASK, gTask.title);
    			this.logTaskDetails(gTask);
    			
    			// Now that we have the task's ID, we can add a link to the task
    			// in the corresponding calendar event:
    			if (settings.getBoolean(PrefNames.DOWNLOADED_TASK_ADD_TO_CAL, false)
    				&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    			{
    				ci.addTaskLinkToEvent(gTask);
    			}
    			
    			// If tags were included, then add them in as well:
    			String tagStr = tagList.get(i);
    			if (tagStr.length()>0)
    			{
    				String[] tagArray = tagStr.split(",");
    				for (int j=0; j<tagArray.length; j++)
    					tagArray[j] = tagArray[j].trim();
    				tagsDB.linkTags(taskID, tagArray);
    				addToTempTags(tagArray);

                    // Record the usage of the tags feature.
                    if (!gTask.completed)
                        featureUsage.record(FeatureUsage.TAGS);
    			}

				if (!currentTaskNeedsParent)
				{
					// The sort order for the corresponding folder or parent will need to be
					// updated after all tasks are downloaded.  This will ensure the new task
					// is put in the correct place.
					if (gTask.parent_id==0)
						foldersWithSortChange.add(gTask.folder_id);
					else
						parentsWithSortChange.add(gTask.parent_id);
				}

    			// Keep an array of tasks added.  We may need this later to link child
    			// tasks to new parent tasks:
    			gTask._id = taskID;
    			addedTasks.add(gTask);
    			
    			// Set a reminder if one was specified:
    			if (gTask.reminder>System.currentTimeMillis() && !gTask.completed)
    			{
    				Util.scheduleReminderNotification(gTask);
    			}
    			tasksAdded++;
    		}
    		else
    		{
    			// The task exists locally.
    			boolean gtWinsConflict = false;
    			if (localTask.mod_date > localTask.sync_date)
    			{
    				// Got a sync conflict, because the task has changed both
    				// locally and in Google.  We will use the one with the later
    				// modification date.
    				if (localTask.mod_date<gTask.mod_date)
    				{
    					// Google wins the conflict, because its modification date
    					// is later.
    					gtWinsConflict = true;
    					Util.log("Got sync conflict for task ID "+localTask._id+" ("+
    						localTask.title+").  Google wins because it was "+
    						"modified later.");
    				}
    				else
    				{
    					Util.log("Got sync conflict for task ID "+localTask._id+" ("+
    						localTask.title+").  Local copy wins because it was "+
    						"modified later.");
    				}
    			}
    			if (localTask.sync_date >= localTask.mod_date || gtWinsConflict)
    			{
    				// No sync conflict or Google wins a conflict, so we update the 
    				// local copy.
    				
    				// If the downloaded task still needs to be linked to a parent, then
    				// temporarily link it to the local copy's parent.  If Google has sent
    				// an invalid parent identifier, we can use this parent link to help
    				// in repairing the task.
    				if (gTask.note.length()>14 && gTask.note.substring(0, 14).equals(
    					"Needs Parent: "))
    				{
    					UTLTask parentTask = tasksDB.getTask(localTask.parent_id);
    					if (parentTask!=null)
    						gTask.parent_id = localTask.parent_id;
    				}
    				
    				// Update the linked event if needed:
    				gTask._id = localTask._id;
    				if (localTask.calEventUri!=null && localTask.calEventUri.length()>0
    					&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
    				{
    					gTask.calEventUri = localTask.calEventUri;
    					if (!ci.hasIdenticalCalendarEntries(localTask, gTask))
    					{
	    					String eventUri = ci.linkTaskWithCalendar(gTask);
	    					if (!eventUri.startsWith(CalendarInterface.ERROR_INDICATOR))
	        				{
	    						gTask.calEventUri = eventUri;
	        				}
	    					else
	    					{
	    						Util.log("Could not modify calendar event for downloaded task. "+
	        						eventUri.substring(CalendarInterface.ERROR_INDICATOR.
	        						length()));
	    					}
    					}
    				}

                    // Record any features used by the task.
                    featureUsage.recordForTask(gTask);

    				// Modify the task in the database:
    				isSuccessful = tasksDB.modifyTask(gTask);
        			if (!isSuccessful)
        			{
        				Util.log("Database modification failed for edited task.");
        				Util.log(TasksDbAdapter.getLastTaskString());
        				return GTasksInterface.DB_FAILURE;
        			}
        			this.logSyncedItem(DOWNLOAD, MODIFY, TASK, gTask.title);
        			this.logTaskDetails(gTask);
    				
        			// If tags were included, then add them in as well:
        			String tagStr = tagList.get(i);
        			if (tagStr.length()>0)
        			{
        				String[] tagArray = tagStr.split(",");
        				for (int j=0; j<tagArray.length; j++)
        					tagArray[j] = tagArray[j].trim();
        				tagsDB.linkTags(localTask._id, tagArray);
        				addToTempTags(tagArray);

                        // Record usage of tags.
                        if (!gTask.completed)
                            featureUsage.record(FeatureUsage.TAGS);
        			}
        			else
        			{
        			    // No tags included.  If any previously existed, they must
        			    // be removed.
        			    tagsDB.linkTags(localTask._id, new String[] { });
        			}
        			
        			if (!localTask.completed && gTask.completed)
        			{
        				// The task has transitioned from not completed to completed.
        				// Call the markTaskComplete function to ensure all proper 
        				// processing is done (such as generating new tasks if it
        				// repeats).
        				Util.markTaskComplete(gTask._id,true);
        			}
        			
        			// Set a reminder if one was specified:
        			if (gTask.reminder>System.currentTimeMillis() && !gTask.completed)
        			{
        				Util.scheduleReminderNotification(gTask);
        				
        				// If the task previously had a reminder in the past, we 
        				// need to remove any notifications that could be displaying.
        				if (localTask.reminder<System.currentTimeMillis())
        				{
        					Util.removeTaskNotification(localTask._id);
        				}
        			}
        			else if (localTask.reminder>0 && gTask.reminder==0)
        			{
        				// A reminder was removed, so get rid of the alarm/notification:
        				Util.cancelReminderNotification(gTask._id);
        			}

					if (!localTask.position.equals(gTask.position) && !currentTaskNeedsParent)
					{
						// The task's position in the list has changed. Make the corresponding
						// change here.
						if (gTask.parent_id==0)
							foldersWithSortChange.add(gTask.folder_id);
						else
							parentsWithSortChange.add(gTask.parent_id);
					}

        			tasksEdited++;
    			}
    		}
    		
    		// Update the percent complete:
    		if ((System.currentTimeMillis()-lastPercentCompleteUpdate)>1000)
    		{
    			lastPercentCompleteUpdate = System.currentTimeMillis();
    			float percentCompleteIncrement = totalDbUpdateIncrement*(float)i/
    				(float)taskList.size();
    			_percentComplete = (int)(basePercentComplete+percentCompleteIncrement);
    			sendPercentComplete(0);
    		}
    	}
    	Util.log("Processed "+tasksAdded+" tasks added in Google.");
    	Util.log("Processed "+tasksEdited+" tasks edited in Google.");
    	Util.log("Ignored "+completedTasksIgnored+" old completed tasks from Google.");
    	if (tasksAdded>0 || tasksEdited>0)
    	{
    	    itemsDownloaded = true;
    	}
    	
    	// Update the percent complete, to reflect the completion of task DB updates:
    	_percentComplete = basePercentComplete + TASK_UPDATE_DB_PERCENT_GT/_numSyncedAccounts;
    	sendPercentComplete(0);
    	
    	// Update the table with recently used tags:
   		currentTagsDB.addToRecent(getTempTags());
   		
   		// There is a chance that we downloaded some child tasks before the 
    	// corresponding parent.  If this occurs, then we need to update the
    	// children to link to the parent:
   		if (tasksNeedParent)
   		{
	   		c = tasksDB.queryTasks("account_id="+accountID+" and note like 'Needs "+
	   			"Parent:%'",null);
	   		c.moveToPosition(-1);
	    	while (c.moveToNext())
	    	{
	    		// Get the Google task ID of the parent:
	    		UTLTask childTask = tasksDB.getUTLTask(c);
	    		int firstNewlineIndex = childTask.note.indexOf("\n");
	    		if (firstNewlineIndex<15)
	    		{
	    			// This should not happen, but it does anyway so we must handle it.
	    			Util.log("Task note says it needs a parent, but note format is bad.\n"+
	    				"Task title: "+childTask.title+"\nTask Note: "+childTask.note);
	    			continue;
	    		}
	    		String gTaskID = childTask.note.substring(14, firstNewlineIndex);
	    		
	    		// Strip the child task of the extra data in the note:
	    		childTask.note = childTask.note.substring(firstNewlineIndex+1);
	    		
	    		// Lookup the parent task:
	    		UTLTask parentTask = tasksDB.getTask(accountID, gTaskID);
	    		if (parentTask!=null)
	    			childTask.parent_id = parentTask._id;
	    		else
	    		{
	    			Util.log("Unable to find parent task with Google ID "+gTaskID+". "+
	    				"Attempting to repair the task at Google. Task title: "+
	    				childTask.title);
	    			logTaskDetails(childTask);
	    			result = gtInterface.repairBadParentLink(childTask);
   					if (result!=GTasksInterface.SUCCESS)
   					{
   						Util.log("Could not repair bad parent link.  Error: "+
   							GTasksInterface.getErrorMessage(result));
   					}

					// The children of this task (if any) will need to be re-sorted locally.
					parentsWithSortChange.add(childTask._id);
	    		}
	    		
	    		// Update the database:
	    		tasksDB.modifyTask(childTask);

				// Add the parent to the list of parents that need their children sorted locally.
				if (childTask.parent_id>0)
					parentsWithSortChange.add(childTask.parent_id);
	    	}
	    	c.close();
   		}
   			
		// Google has been known to send us tasks with a link to an invalid
		// parent, that is in another folder.  If this occurs, we need 
		// repair the task from here:
   		long localSecs = utlAccount.last_sync+localMinusGoogle;
   		c = tasksDB.queryTasks("account_id="+accountID+" and parent_id>0 and mod_date>="+ 
   			localSecs,null);
   		c.moveToPosition(-1);
		while (c.moveToNext())
		{
			UTLTask gTask = tasksDB.getUTLTask(c);
			UTLTask parentTask = tasksDB.getTask(gTask.parent_id);
			if (parentTask!=null && parentTask.folder_id != gTask.folder_id)
			{
				Util.log("Received task with invalid parent. A repair "+
					"operation will be attempted.");
				logTaskDetails(gTask);
				gTask.parent_id = 0;
    			result = gtInterface.repairBadParentLink(gTask);
				if (result!=GTasksInterface.SUCCESS)
				{
					Util.log("Could not repair bad parent link.  Error: "+
						GTasksInterface.getErrorMessage(result));
					c.close();
					return result;
				}

				// The local sort order for the folder and the children of this task need
				// refreshed.
				foldersWithSortChange.add(gTask.folder_id);
				parentsWithSortChange.add(gTask._id);
			}
		}
		c.close();
		
    	// At this point, we have downloaded all tasks from TD and updated our
    	// database accordingly.  We can now record the last sync date/time for
    	// the account:
    	utlAccount = accountsDB.getAccount(accountID);
    	utlAccount.last_sync = latestModDate;
    	utlAccount.etag = eTag;
    	accountsDB.modifyAccount(utlAccount);

		// Go through all folders that have had changes to the task order, and update our internal
		// sort order:
		Iterator<Long> longIterator = foldersWithSortChange.iterator();
		while (longIterator.hasNext())
			Util.distributeSortOrderInFolder(longIterator.next());

		// Go through all task parents that have changes to their children's sort order and update
		// our internal sort order.
		longIterator = parentsWithSortChange.iterator();
		while (longIterator.hasNext())
			Util.distributeChildSortOrder(longIterator.next());

   		// Upload tasks that have been added or edited locally.  This must be done in
   		// multiple passes to ensure that no child task is uploaded before its parent:
   		int addCount = 0;
        int modifyCount = 0;
        HashSet<Long> uploadedIDs = new HashSet<Long>();
   		while (true)
   		{
   			int numUploaded = 0;
   			c = tasksDB.queryTasks("mod_date>=sync_date and account_id="+accountID,"sort_order desc");
   			if (c.getCount()==0)
   				break;
   			c.moveToPosition(-1);
   			while (c.moveToNext())
   			{
   				UTLTask localTask = tasksDB.getUTLTask(c);
   				
   				// If the task has already been uploaded, then ignore it:
   				if (uploadedIDs.contains(localTask._id))
   				{
   					Util.log("Attempted to upload this task twice: "+localTask.title);
   					continue;
   				}
   				
   				if (localTask.parent_id>0)
   				{
   					// Make sure the parent has been uploaded:
   					UTLTask parentTask = tasksDB.getTask(localTask.parent_id);
   					if (parentTask==null)
   					{
   						// This shouldn't happen, but to handle it we will remove the
   						// link to the parent and continue:
   						localTask.parent_id = 0;
   						tasksDB.modifyTask(localTask);
   					}
   					else
   					{
   						if (parentTask.remote_id==null || parentTask.remote_id.length()==0)
   						{
   							// Parent task has not been uploaded.  Skip this child until
   							// it is.
   							continue;
   						}
   					}
   				}

				if (localTask.is_moved)
				{
					// Look up which task is the previous one, if any.  In theory, the check being
					// being done here is not needed, since tasks with a higher value of sort_order
					// are uploaded first. This should, in theory, ensure that the previous task
					// has already been uploaded.
					Cursor c2=tasksDB.queryTasks("folder_id="+localTask.folder_id+" and parent_id="+
						localTask.parent_id+" and sort_order>"+localTask.sort_order,"sort_order asc");
					if (c2.moveToFirst())
					{
						UTLTask prevTask = tasksDB.getUTLTask(c2);
						if (prevTask.remote_id==null || prevTask.remote_id.length()==0)
						{
							// Previous task has not been uploaded.  Skip this task until it is.
							c2.close();
							continue;
						}
					}
					c2.close();
				}
   				
   				// Determine if this is an add or edit:
   				if (localTask.remote_id==null || localTask.remote_id.length()==0)
   				{
   					// This is an add operation, since we don't yet have a Google ID
   					// for the task.
   					result = gtInterface.addTask(localTask);
   					if (result!=GTasksInterface.SUCCESS)
   					{
   						Util.log("Could not add task \""+localTask.title+
   							"\" to Google.  Error: "+
   							GTasksInterface.getErrorMessage(result));
   						c.close();
   						return result;
   					}
   					this.logSyncedItem(UPLOAD, ADD, TASK, localTask.title);
   	                this.logTaskDetails(localTask);
   	                addCount++;
   	                
   	                // Update the sync date for the task:
   	                if (!tasksDB.updateSyncDate(localTask._id))
   	                {
   	                	Util.log("Database failure when updating task sync date.");
   	                	c.close();
   	                	return GTasksInterface.DB_FAILURE;
   	                }
   	                
   	                uploadedIDs.add(localTask._id);
   	                numUploaded++;
   				}
   				else
   				{
   					// Since the task has been previously synced, this is an edit 
   					// operation.
   					
   					// Before sending the edit command, check to see if TD has reported 
   	        	    // this as a deleted item:
   	        	    if (deletedTaskHash.contains(localTask.remote_id))
   	        	    {
   	        	        Util.log("Sync conflict for Google task ID "+localTask.remote_id+" ("+
   	        	            localTask.title+").  Google has reported this as deleted, but it "+
   	        	            "has also been edited on the handset.  Because Google no longer has "+
   	        	            "a record of the task, it must be deleted locally.");
   	        	    }
   	        	    else
   	        	    {
   	        	    	result = gtInterface.editTask(localTask);
   	        	    	if (result != GTasksInterface.SUCCESS)
   	                    {
   	        	    		Util.log("Could not edit task \""+localTask.title+
   	        	    			"\" at Google.  Error: "+
   	   							GTasksInterface.getErrorMessage(result));
   	        	    		if (result==GTasksInterface.MISC_ERROR && GTasksInterface.
   	        	    			_lastHttpErrorCode==404)
   	        	    		{
   	        	    			// The edit operation failed because Google thinks the 
   	        	    			// task does not exist. We will try adding it instead.
   	        	    			// This is done by setting the remote ID to an empty value
   	        	    			// which triggers an "add" operation on the next pass
   	        	    			// of this loop.
   	        	    			Util.log("Marking the task as new so that it will be "+
   	        	    				"added to Google.");
   	        	    			localTask.remote_id = "";
   	        	    			tasksDB.modifyTask(localTask);
   	        	    			numUploaded++;  // Ensures another pass of the loop.
   	        	    			continue;
   	        	    		}
   	        	    		c.close();
   	        	    		return result;
   	                    }
   	        	    	
   	        	    	modifyCount++;
   	        	    	this.logSyncedItem(UPLOAD, MODIFY, TASK, localTask.title);
   	                    this.logTaskDetails(localTask);
   	        	    }
   	        	    
        	    	// Update the sync date for the task:
                    if (!tasksDB.updateSyncDate(localTask._id))
                    {
                    	Util.log("Database failure when updating task sync date.");
                    	c.close();
                    	return GTasksInterface.DB_FAILURE;
                    }
                    
   	                uploadedIDs.add(localTask._id);
   	                numUploaded++;
   				}
   			}
   			c.close();
   			
   			if (numUploaded==0)
   			{
   				// This provides some extra protection against an infinite loop:
   				break;
   			}
   		}
   		Util.log("Uploaded "+addCount+" new tasks to Google.");
        Util.log("Uploaded "+modifyCount+" modified tasks to Google.");
		sendPercentComplete(TASK_UPLOAD_PERCENT_GT/_numSyncedAccounts);
        
        //
        // Deleted Tasks:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("task",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Make sure the task is not located in the tasks table.  If it is, then
            // we have a conflict.  The task was modified in Google, but deleted locally.
            // In this situation, we don't send the delete operation to Google.
        	UTLTask task = tasksDB.getTask(accountID, Util.cString(c, "remote_id"));
        	if (task==null)
        	{
        		// We can proceed with the delete:
        		result = gtInterface.deleteTask(Util.cString(c,"remote_tasklist_id"), 
        			Util.cString(c,"remote_id"));
        		if (result != GTasksInterface.SUCCESS)
        		{
        			Util.log("Could not delete Google task ID "+Util.cString(c, 
        				"remote_id")+". "+GTasksInterface.getErrorMessage(result));
        			
        			// Continue syncing if Google responds by saying that the 
        			// task doesn't exist.
        			if (result!=GTasksInterface.MISC_ERROR || GTasksInterface.
        				_lastHttpErrorCode!=404)
        			{
        				c.close();
        				return result;
        			}
        		}
        		count++;
        		this.logSyncedItem(UPLOAD, DELETE, TASK, Util.cString(c,"remote_id"));
        	}
        	
        	// Remove the deletion info from the table:
            deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
        }
        c.close();
        Util.log("Uploaded "+count+" deleted tasks to Google.");
        
        // Go through items that Google has reported deleted:
        Iterator<String> it3 = deletedTaskHash.iterator();
        count = 0;
        while (it3.hasNext())
        {
        	String gID = it3.next();
        	
        	// Look up the task info in the DB.  If this fails, then the task has already
            // been deleted:
        	UTLTask deletedTask = tasksDB.getTask(accountID, gID);
        	if (deletedTask!=null)
            {
            	if (deletedTask.reminder>System.currentTimeMillis())
            	{
            		// Cancel the scheduled reminder notification:
            		Util.cancelReminderNotification(deletedTask._id);
            	}
            	
            	// Also remove any displayed notifications:
            	Util.removeTaskNotification(deletedTask._id);
        	
            	// Remove the linked calendar entry, if applicable:
            	if (deletedTask.calEventUri!=null && deletedTask.calEventUri.length()>0
            		&& settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            	{
            		ci.unlinkTaskFromCalendar(deletedTask);
            	}
            	
            	// It is possible for a sync conflict to occur, in which we have some
            	// UTL tasks that link to this deleted task.  Unlink them from the parent.
            	Cursor tasksCursor = tasksDB.queryTasks("parent_id="+deletedTask._id, null);
            	tasksCursor.moveToPosition(-1);
            	while (tasksCursor.moveToNext())
            	{
            		UTLTask child = tasksDB.getUTLTask(tasksCursor);
            		child.parent_id = 0;
            		child.mod_date = System.currentTimeMillis();
            		tasksDB.modifyTask(child);
            	}
            	tasksCursor.close();
            	
            	// Delete the task from the DB:
	        	tasksDB.deleteTask(gID, accountID);
	        	count++;
	        	this.logSyncedItem(DOWNLOAD, DELETE, TASK, gID);
            }
        }
        Util.log("Processed "+count+" deleted tasks from Google.");
        if (count>0)
        {
            itemsDownloaded = true;
        }
        sendPercentComplete(DELETED_TASKS_PERCENT_GT/_numSyncedAccounts);
        
        //
        // Deleted Folders / Tasklists:
        //
        
        // Go through the deleted items table and upload the info to TD:
        c = deletesDB.getPendingDeletes("folder",accountID);
        count = 0;
        while (c.moveToNext())
        {
            // Send the deletion info to Google:
        	result = gtInterface.deleteFolder(Util.cString(c,"remote_id"));
        	if (result!=GTasksInterface.SUCCESS)
        	{
        		Util.log("Could not delete a folder at Google. "+GTasksInterface.
        			getErrorMessage(result));
        		if (result==GTasksInterface.MISC_ERROR)
        		{
        			// This was not a connection failure.  Google rejected the deletion.
        			// The most likely cause is that the user tried to delete the default
        			// tasklist, which Google does not allow.  Remove this from the
        			// pending deletes table, so the sync can continue.  The side effect
        			// will be that the folder reappears. Google does not give us any
        			// way to determine which folder is the default.
        			deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
        		}
        		else
        		{
        			c.close();
        			return result;
        		}
        	}
        	else
        	{
        		// Remove the deletion info from the table:
                this.logSyncedItem(UPLOAD, DELETE, FOLDER, Util.cString(c,"remote_id"));
                deletesDB.deletePendingDelete(Util.cLong(c, "_id"));
                count++;
        	}
        }
        c.close();
        Util.log("Uploaded "+count+" deleted folders to Google.");
        
        // Go through folders that were deleted from Google:
        Iterator<Long> it4 = foldersDeletedFromGT.iterator();
        count = 0;
        while (it4.hasNext())
        {
            long folderID = it4.next();
            
            // If any tasks in our local database reference this folder, they need to
            // be updated.
            c = tasksDB.queryTasks("folder_id="+folderID, null);
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                UTLTask task = tasksDB.getUTLTask(c);
                task.folder_id = 0;
                task.mod_date = System.currentTimeMillis();
                isSuccessful = tasksDB.modifyTask(task);
                if (!isSuccessful)
                {
                    Util.log("Could not clear folder for task ID "+task._id);
                    c.close();
                    return GTasksInterface.DB_FAILURE;
                }
            }
            c.close();
            
            // If any notes in our local database reference this folder, they need to 
            // be updated.
            NotesDbAdapter notesDB = new NotesDbAdapter();
            c = notesDB.queryNotes("folder_id="+folderID, null);
            while (c.moveToNext())
            {
            	UTLNote note = notesDB.cursorToUTLNote(c);
            	note.folder_id = 0;
            	note.mod_date = System.currentTimeMillis();
            	notesDB.modifyNote(note);
            }
            c.close();

            // Delete the folder itself:
            isSuccessful = foldersDB.deleteFolder(folderID);
            if (!isSuccessful)
            {
                Util.log("Could not delete folder ID "+folderID);
            }
            count++;        	
        }
        Util.log("Processed "+count+" deleted folders from Google.");
        if (count>0)
            itemsDownloaded = true;
        
        sendPercentComplete(OTHER_DELETED_PERCENT_GT/_numSyncedAccounts);

        Util.log("Full sync completed for account ID "+utlAccount._id+" ("+
            utlAccount.name+").");
        
        // Regardless of whether this is an manual or automatic sync, we update the last
        // sync time.  If we have a successful manual sync, we want the next automatic
        // sync to occur at least an hour later.
        Util.updatePref("last_auto_sync_time", System.currentTimeMillis(),this);
        
        // Create a sample task if this is the first sync:
        if (settings.getBoolean(PrefNames.CREATE_SAMPLE_TASK, false))
        {
        	Util.updatePref(PrefNames.CREATE_SAMPLE_TASK, false, this);
        	
        	// Don't create the sample task if it's already in the database:
        	c = tasksDB.queryTasks("title='"+Util.makeSafeForDatabase(getString(R.string.
        		learn_more_about_utl))+"'", null);
        	if (!c.moveToFirst())
        	{
        		c.close();
	        	UTLTask sample = new UTLTask();
	        	sample.account_id = accountID;
	        	sample.title = this.getString(R.string.learn_more_about_utl);
	        	c = foldersDB.queryFolders("account_id="+accountID, null);
	        	c.moveToFirst();
	        	if (c.getCount()==1)
	        		sample.folder_id = Util.cLong(c, "_id");
	        	else
	        	{
	        		// Setting the folder_id to zero will cause the default folder to be chosen.
	        		sample.folder_id = 0;
	        	}
	        	c.close();
	        	sample.priority=5;
	        	sample.star = true;
	        	sample.note = this.getString(R.string.First_Task_Note);
	        	sample.uuid = UUID.randomUUID().toString();
	        	if (tasksDB.addTask(sample)>=0)
	        	{
	        		gtInterface.addTask(sample);
	        	}
        	}
        	else
        		c.close();
        }
        
    	return GTasksInterface.SUCCESS;
    }
    
    // Log the syncing of an item:
    private void logSyncedItem(int direction, int operation, int type, String description)
    {
    	if (detailedItemCount>=MAX_DETAILED_ITEMS)
    	{
    		return;
    	}
    	String result = "";
    	
    	if (direction==UPLOAD)
    		result += "Uploaded ";
    	else
    		result += "Downloaded ";
    	
    	if (operation==ADD)
    		result += "added ";
    	else if (operation==DELETE)
    		result += "deleted ";
    	else
    		result += "modified ";
    	
    	if (type==TASK)
    		result += "task ";
    	else if (type==NOTE)
    		result += "note ";
    	else if (type==FOLDER)
    		result += "folder ";
    	else if (type==CONTEXT)
    		result += "context ";
    	else if (type==GOAL)
    		result += "goal ";
    	else if (type==LOCATION)
    		result += "location ";
    	
    	result += "\""+description+"\"";
    	Util.log(result);
    	detailedItemCount++;
    }
    
    // Instant Upload: Add a Google Task:
    private void instantAddGoogleTask(long itemID, GTasksInterface gtInterface)
    {
    	TasksDbAdapter tasksDb = new TasksDbAdapter();
    	UTLTask t = tasksDb.getTask(itemID);
    	if (t!=null)
    	{
			if (t.mod_date<t.sync_date)
			{
				// The modification date is prior to the sync date.  Most likely there was
				// a full sync running when the user created or modified the task.  So, there
				// is nothing to do.
				Util.log("Not uploading.  The mod date is earlier than the sync date.");
				return;
			}

			if (t.remote_id!=null && t.remote_id.length()>0)
			{
				// The task has a remote ID, which doesn't make sense for a task being added.
				// Most likely there was a full sync running when the user created or modified
				// the task, so there is nothing to do.
				Util.log("Not uploading.  The task already has a remote ID: "+t.remote_id);
				return;
			}

    		// If the task has a parent that has not been uploaded, we need to abort:
    		if (t.parent_id>0)
    		{
    			UTLTask parent = (new TasksDbAdapter()).getTask(t.parent_id);
    			if (parent==null || parent.remote_id==null || parent.remote_id.length()==0)
    			{
    				Util.log("Aborting upload because parent has not been uploaded.");
    				return;
    			}
    		}
    		
    		int result = gtInterface.addTask(t);
    		if (result!=GTasksInterface.SUCCESS)
			{
				Util.log("Could not add task to Google.  Error: "+
					GTasksInterface.getErrorMessage(result));
			}
    		else
    		{
    			tasksDb.updateSyncDate(t._id);
    			this.logSyncedItem(UPLOAD, ADD, TASK, t.title);
                this.logTaskDetails(t);
    		}
    	}
    }
    
    // Instant Upload: Edit a Google Task:
    private void instantEditGoogleTask(long itemID, GTasksInterface gtInterface)
    {
    	TasksDbAdapter tasksDb = new TasksDbAdapter();
    	UTLTask t = tasksDb.getTask(itemID);
    	if (t!=null)
    	{
    		// If the task has a parent that has not been uploaded, we need to abort:
    		if (t.parent_id>0)
    		{
    			UTLTask parent = (new TasksDbAdapter()).getTask(t.parent_id);
    			if (parent==null || parent.remote_id==null || parent.remote_id.length()==0)
    			{
    				Util.log("Aborting upload because parent has not been uploaded.");
    				return;
    			}
    		}
    		
    		int result = gtInterface.editTask(t);
    		if (result!=GTasksInterface.SUCCESS)
			{
    			Util.log("Could not edit task at Google.  Error: "+
					GTasksInterface.getErrorMessage(result));
			}
    		else
    		{
    			tasksDb.updateSyncDate(t._id);
    			this.logSyncedItem(UPLOAD, MODIFY, TASK, t.title);
                this.logTaskDetails(t);
    		}
    	}    	
    }
    
    // Instant Upload: Delete a Google Task:
    private void instantDeleteGoogleTask(String gID, String gFolderID, GTasksInterface 
    	gtInterface, long accountID)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("task", accountID, gID, gFolderID);
    	if (rowID==0)
    	{
    		// Already deleted.  Nothing to do.
    		return;
    	}
    	
    	int result = gtInterface.deleteTask(gFolderID, gID);
    	if (result != GTasksInterface.SUCCESS)
		{
			Util.log("Could not delete Google task ID "+gID+". "+GTasksInterface.
				getErrorMessage(result));
		}
    	else
    	{
    		this.logSyncedItem(UPLOAD, DELETE, TASK, gID);
    		deletesDB.deletePendingDelete(rowID);
    	}
    }
    
    // Instant Upload: Add a Google Folder:
    private void instantAddGoogleFolder(long itemID, GTasksInterface gtInterface)
    {
    	Cursor c = (new FoldersDbAdapter()).getFolder(itemID);
    	if (c.moveToFirst())
    	{
    		int result = gtInterface.addFolder(c);
    		if (result != GTasksInterface.SUCCESS)
        	{
        		Util.log("Could not add folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") to Google.  Error message: "+
                    GTasksInterface.getErrorMessage(result));
        	}
    		else
    		{
    			logSyncedItem(UPLOAD, ADD, FOLDER, Util.cString(c, "title"));
    			(new FoldersDbAdapter()).setSyncDate(itemID, System.currentTimeMillis());
    		}
    	}
    	c.close();
    }
    
    // Instant Upload: Edit a Google Folder:
    private void instantEditGoogleFolder(long itemID, GTasksInterface gtInterface)
    {
    	Cursor c = (new FoldersDbAdapter()).getFolder(itemID);
    	if (c.moveToFirst())
    	{
    		int result = gtInterface.modifyFolder(c);
    		if (result != GTasksInterface.SUCCESS)
        	{
    			Util.log("Could not edit folder ID "+Util.cLong(c, "_id")+" ("+
                    Util.cString(c, "title")+") at Google.  Error message: "+
                    GTasksInterface.getErrorMessage(result));
        	}
    		else
    		{
    			logSyncedItem(UPLOAD, MODIFY, FOLDER, Util.cString(c, "title"));
    			(new FoldersDbAdapter()).setSyncDate(itemID, System.currentTimeMillis());
    		}
    	}
    	c.close();
    }
    
    // Instant Upload: Delete a Google Folder:
    private void instantDeleteGoogleFolder(String gFolderID, GTasksInterface gtInterface,
    	long accountID)
    {
    	PendingDeletesDbAdapter deletesDB = new PendingDeletesDbAdapter();
    	long rowID = deletesDB.isDeletePending("folder", accountID, "", gFolderID);
    	if (rowID==0)
    	{
    		// Already deleted.  Nothing to do.
    		return;
    	}
    	
    	int result = gtInterface.deleteFolder(gFolderID);
    	if (result!=GTasksInterface.SUCCESS)
    	{
    		Util.log("Could not delete a folder at Google. "+GTasksInterface.
    			getErrorMessage(result));
    	}
    	else
    	{
    		logSyncedItem(UPLOAD, DELETE, FOLDER, gFolderID);
    		deletesDB.deletePendingDelete(rowID);
    	}
    }
    
    // Log the details of a task sent or received.  Call this after the call to 
    // logSyncedItem()
    private void logTaskDetails(UTLTask t)
    {
    	if (detailedItemCount>=MAX_DETAILED_ITEMS)
    	{
    		return;
    	}
    	String result = "";
    	result += "  ID: "+t._id+"\n";
    	result += "  TD ID: "+t.td_id+"\n";
    	result += "  Remote ID: "+t.remote_id+"\n";
    	result += "  UUID: "+t.uuid+"\n";
    	result += "  Account ID: "+t.account_id+"\n";
    	result += "  Mod Date: "+getTimestamp(t.mod_date)+"\n";
    	result += "  Sync Date: "+getTimestamp(t.sync_date)+"\n";
    	result += "  Title: "+t.title+"\n";
    	result += "  Completed: "+t.completed+"\n";
    	result += "  Folder ID: "+t.folder_id+"\n";
    	result += "  Context ID: "+t.context_id+"\n";
    	result += "  Goal ID: "+t.goal_id+"\n";
    	result += "  Location ID: "+t.location_id+"\n";
    	result += "  Location Reminder: "+t.location_reminder+"\n";
    	result += "  Location Nag: "+t.location_nag+"\n";
    	result += "  Parent ID: "+t.parent_id+"\n";
    	result += "  Due Date: "+getTimestamp(t.due_date)+"\n";
    	result += "  Due Modifier: "+t.due_modifier+"\n";
    	result += "  Uses Due Time: "+t.uses_due_time+"\n";
    	result += "  Reminder: "+getTimestamp(t.reminder)+"\n";
    	result += "  Start Date: "+getTimestamp(t.start_date)+"\n";
    	result += "  Uses Start Time: "+t.uses_start_time+"\n";
    	result += "  Repeat: "+t.repeat+"\n";
    	result += "  Nag: "+t.nag+"\n";
    	result += "  Advanced Repeat: "+t.rep_advanced+"\n";
    	result += "  Status: "+t.status+"\n";
    	result += "  Expected Length: "+t.length+"\n";
    	result += "  Priority: "+t.priority+"\n";
    	result += "  Star: "+t.star+"\n";
    	result += "  Has a note: "+(t.note.length()>0)+"\n";
    	result += "  Timer: "+t.timer+"\n";
    	result += "  Timer Start Time: "+getTimestamp(t.timer_start_time)+"\n";
    	result += "  Completion Date: "+getTimestamp(t.completion_date)+"\n";
    	result += "  Position: "+t.position+"\n";
    	result += "  New Task Generated: "+t.new_task_generated+"\n";
    	result += "  Cal Event Uri: "+t.calEventUri+"\n";
		result += "  Sort Order: "+t.sort_order+"\n";
		result += "  Is Moved: "+t.is_moved+"\n";
    	Util.log(result);
    }
    
    // Utility function to log timestamps from the logTaskDetails() function:
    private String getTimestamp(long ms)
    {
    	if (ms==0)
    		return "none";
    	else
    		return Util.getDateTimeString(ms,this);
    }
    // Send a percent complete message to a client (if enabled).  The input is the amount
    // to add to the percent complete:
    private void sendPercentComplete(int amountToAdd)
    {
    	_percentComplete += amountToAdd;
    	
    	if (!_sendPercentComplete)
    		return;
    	
    	for (int i=mClients.size()-1; i>=0; i--)
        {
            try
            {
                mClients.get(i).send(Message.obtain(null,PERCENT_COMPLETE_MSG,
                	_percentComplete,0));
            }
            catch (RemoteException e)
            {
                mClients.remove(i);
            }
        }
    }
    
    // Clear the temporary tag list:
    private void clearTempTags()
    {
    	_tagSet.clear();
    	_orderedTags.clear();
    }
    
    // Add to the temporary tag list:
    private void addToTempTags(String[] tags)
    {
    	// Go through the new tags and add them in if they are not already there:
        int j;
        for (int i=0; i<tags.length; i++)
        {
            if (!_tagSet.contains(tags[i]))
            {
                // The tag is not one of the recent ones, so add it in:
                _orderedTags.add(tags[i]);
                _tagSet.add(tags[i]);
            }
            else
            {
                // The tag is there, but should be moved to the most recently used.
                j = _orderedTags.indexOf(tags[i]);
                _orderedTags.remove(j);
                _orderedTags.add(tags[i]);
            }
        }
        
        // If we have too many, then remove the least recently used:
        while (_orderedTags.size()>CurrentTagsDbAdapter.NUM_TAGS)
        {
            String tagName = _orderedTags.remove(0);
            _tagSet.remove(tagName);
        }
    }
    
    // Get a string array containing the temporary tags:
    private String[] getTempTags()
    {
    	return (String[])_orderedTags.toArray(new String[_orderedTags.size()]);
    }
}
