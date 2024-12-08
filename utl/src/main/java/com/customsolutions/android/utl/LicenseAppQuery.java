package com.customsolutions.android.utl;

// This queries the license app to see if UTL has actually been purchased.

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.core.app.UtlJobIntentService;

public class LicenseAppQuery extends UtlJobIntentService
{
	// The maximum number of license check failures we will accept - in a row - from
	// the license server.  Once the number of failures goes above this, we lock out the
	// user.
	private static final int MAX_CHECK_FAILURES = 5;
	
	// The one and only message type sent from here to the license service:
	private static final int MSG_CHECK_LICENSE = 1;
	
	// The one and only message type sent back:
	private static final int MSG_LICENSE_CHECK_RESULT = 2;
	
	// Result codes:
	private static final int RESULT_LICENSED = 1;
	private static final int RESULT_UNLICENSED = 2;
	private static final int RESULT_APP_ERROR = 3;

	private static final int JOB_ID = 1536705158;

	// Messenger objects for communication with the License service.
	Messenger mService;
	final Messenger mMessenger = new Messenger(new IncomingHandler());
	
	// Are we bound to the remote license service?
	private boolean mIsBound;
	
	private PurchaseManager _pm;

	/** Convenience method for enqueuing work to this service. */
	static void enqueueWork(Context context, Intent work)
	{
		enqueueWork(context, LicenseAppQuery.class, JOB_ID, work);
	}

	public LicenseAppQuery()
	{
		super();
		mIsBound = false;
	}
	
	// This class is needed to interact with the license service:
	private ServiceConnection mConnection = new ServiceConnection() 
	{
        public void onServiceConnected(ComponentName className, IBinder service) 
        {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.
            mService = new Messenger(service);

            // Ask the service to check our license status:
            try 
            {
                Message msg = Message.obtain(null,MSG_CHECK_LICENSE);
                msg.replyTo = mMessenger;
                mService.send(msg);
                mIsBound = true;
                Util.log("LAQ: Message Sent");
            } 
            catch (RemoteException e) 
            {
            	Util.log("LAQ: "+"RemoteException: "+e.getMessage());
                // If the remote service doesn't exist, then the user does not have the
            	// license app installed.
            	_pm.setAppStatus(false);
            	mIsBound = false;
            }
        }

        public void onServiceDisconnected(ComponentName className) 
        {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;
            mIsBound = false;
        }
    };
    
	@Override
	protected void onHandleWork(Intent arg0)
	{
		// Initialize the app if needed:
    	Util.appInit(this);
    	
    	if (_pm==null)
    		_pm = new PurchaseManager(LicenseAppQuery.this);

		Util.log("LAQ: "+Util.getString(R.string.licCheck1));
		
		// Bind to the license service:
		Intent i = new Intent();
		i.setClassName("com.customsolutions.android.utl_license",
			"com.customsolutions.android.utl_license.LicenseChecker");		
		if (!bindService(i, mConnection, Context.BIND_AUTO_CREATE))
		{
			// Can't bind to the service, therefore the license app is not installed.
			Util.log("LAQ: "+Util.getString(R.string.licCheck2));
			
			// Try the tablet license service.
			Util.log("LAQ: "+"Trying tab");
			i = new Intent();
			i.setClassName("com.customsolutions.android.utl_tab_license",
				"com.customsolutions.android.utl_tab_license.LicenseChecker");
			if (!bindService(i, mConnection, Context.BIND_AUTO_CREATE))
			{
				// Tablet license not installed either.
				Util.log("LAQ: "+"No tab either.");
				_pm.setAppStatus(false);
			}
			else
			{
				Util.log("LAQ: "+"Tab connected.");
			}
		}
		else
			Util.log("LAQ: "+"Smartphone connected.");
	}

	// Handlers for message from license service:
    class IncomingHandler extends Handler 
    {
        @Override
        public void handleMessage(Message msg) 
        {
        	Util.log("LAQ: "+"Got Message: "+msg.what);
            switch (msg.what) 
            {
                case MSG_LICENSE_CHECK_RESULT:
                	int result = msg.arg1;
                	Util.log("LAQ: Result: "+result);
                	if (result==RESULT_LICENSED)
                	{
                		Util.log("LAQ: "+Util.getString(R.string.licCheck3));
                		Util.logOneTimeEvent(LicenseAppQuery.this, "purchase", 0, new String[] 
                			{"license_app"});
                		Util.updatePref("license_check_failures",0);
                		_pm.setAppStatus(true);
                	}
                	if (result==RESULT_UNLICENSED)
                	{
                		int numFailures = Util.settings.getInt("license_check_failures",0)
                			+1;
                		Util.log("LAQ: "+Util.getString(R.string.licCheck4)+numFailures);
                		if (numFailures>MAX_CHECK_FAILURES)
                		{
                			Util.log("LAQ: "+Util.getString(R.string.licCheck5));
                			_pm.setAppStatus(false);
                			Util.logOneTimeEvent(LicenseAppQuery.this, "license_check_failure", 0, 
                				new String[] {"license_app"});
                		}
                		else
                		{
                			Util.log("LAQ: "+Util.getString(R.string.licCheck6));
                			Util.updatePref("license_check_failures", numFailures);
                		}
                	}
                	if (result==RESULT_APP_ERROR)
                	{
                		Util.log("LAQ: "+Util.getString(R.string.licCheck7)+
                			msg.arg2);
                		// In this case, leave the purchase status unchanged.
                	}
                	stopSelf();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onDestroy()
    {
    	doUnbindService();
    }
    
    private void doUnbindService() 
    {
        if (mIsBound) 
        {
            // Detach our existing connection:
        	try
        	{
        		unbindService(mConnection);
        	}
        	catch (IllegalArgumentException e) 
        	{
            }
            mIsBound = false;
        }
    }
}
