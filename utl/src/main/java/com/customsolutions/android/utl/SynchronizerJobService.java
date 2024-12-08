package com.customsolutions.android.utl;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;

/** This JobService is executed regularly in the background to launch background synchronization
 * via Synchronizer. */
public class SynchronizerJobService extends JobService
{
    private static final String TAG = "SynchronizerJobService";

    /** The connection to the Synchronizer service. */
    private ServiceConnection _serviceConnection;

    /** This semaphore prevents more than one sync operation from running at a time. */
    private static SinglePermitSemaphore _semaphore = new SinglePermitSemaphore();

    /** Start the job. */
    public boolean onStartJob(final JobParameters params)
    {
        Util.appInit(this);
        log("onStartJob() called. Job ID: "+params.getJobId()+"; Instance null? "+
            (Synchronizer.currentInstance==null));
        if (params.getJobId()!=Util.JOB_ID_AUTO_SYNC)
            Synchronizer._jobSchedulingSemaphore.release();

        // Before we can perform synchronization, we must bind to the Synchronizer service.
        // This ensures it's created and ready to work.
        _serviceConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder)
            {
                Thread t = new Thread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        log("Connected to Synchronizer service.");
                        boolean hasSemaphore = false;
                        try
                        {
                            if (!_semaphore.tryAcquire())
                            {
                                Log.d(TAG,"Could not get semaphore. Abandoning job.");
                                try
                                {
                                    unbindService(_serviceConnection);
                                }
                                catch (IllegalArgumentException e)
                                {
                                    // This can happen on rare occasions. Android thinks the service is
                                    // not registered.
                                    log("WARNING: Got IllegalArgumentException when unbinding. "+
                                        e.getMessage());
                                }
                                jobFinished(params,false);
                                return;
                            }
                            hasSemaphore = true;
                            Util.log("Got semaphore for job "+params.getJobId());
                            Synchronizer s = Synchronizer.currentInstance;
                            if (s==null)
                            {
                                log("WARNING: Synchronizer.currentInstance is null even though it's bound.");
                                return;
                            }
                            Intent i = new Intent(SynchronizerJobService.this, Synchronizer.class);
                            if (params.getJobId()==Util.JOB_ID_AUTO_SYNC)
                            {
                                // Set intent extras to the standard values for a regular auto sync.
                                i.putExtra("command","full_sync");
                                i.putExtra("is_scheduled",true);
                            }
                            else
                            {
                                // Set the intent extras for the specific command.
                                i.putExtras(convertToBundle(params.getExtras()));
                            }
                            s.onHandleIntent(i);
                            try
                            {
                                unbindService(_serviceConnection);
                            }
                            catch (IllegalArgumentException e)
                            {
                                // This can happen on rare occasions. Android thinks the service is
                                // not registered.
                                log("WARNING: Got IllegalArgumentException when unbinding. "+
                                    e.getMessage());
                            }
                            log("Telling Android job is done.");
                            jobFinished(params,false);
                        }
                        finally
                        {
                            if (hasSemaphore)
                                _semaphore.release();
                        }
                    }
                });
                t.start();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName)
            {
                log("Disconnected from Synchronizer service.");
            }
        };
        Intent i = new Intent(this,Synchronizer.class);
        bindService(i, _serviceConnection, Context.BIND_AUTO_CREATE);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params)
    {
        log("WARNING: onStopJob() called, but I can't stop it.");
        return true;
    }

    /** Convert the PersistableBundle in the JobParameters object to a Bundle Synchronizer will
     * recognize. */
    public Bundle convertToBundle(PersistableBundle pBundle)
    {
        Bundle bundle = new Bundle();
        if (pBundle!=null)
        {
            for (String key : pBundle.keySet())
            {
                switch (key)
                {
                    // String types:
                    case "command":
                    case "remote_id":
                    case "remote_tasklist_id":
                        bundle.putString(key,pBundle.getString(key));
                        break;

                    // Integer types:
                    case "operation":
                    case "item_type":
                        bundle.putInt(key,pBundle.getInt(key));
                        break;

                    // Long types:
                    case "account_id":
                    case "item_id":
                        bundle.putLong(key,pBundle.getLong(key));
                        break;

                    // Boolean types (stored as int in the PersistableBundle due to API 21 issue):
                    case "send_percent_complete":
                    case "is_scheduled":
                        bundle.putBoolean(key,(pBundle.getInt(key)==1));
                        break;
                }
            }
        }
        return bundle;
    }

    /** Basic logging function. */
    private void log(String msg)
    {
        Log.v(TAG,msg);
    }
}
