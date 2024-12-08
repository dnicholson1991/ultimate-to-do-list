package com.customsolutions.android.utl;

import android.os.AsyncTask;

/**
 * This performs a specific task at the end of a time interval unless stopped.
 */
public class GuardTimer
{
    /** The AsyncTask that implements the timer: */
    private AsyncTask<Void, Void, Boolean> _asyncTask;

    /** The timeout, in millis. */
    private int _timeout;

    /** The Runnable to execute if the timeout is reached. */
    private Runnable _runnable;

    /** Flag indicating if the timer is running. */
    private boolean _isRunning = false;

    /** Start the timer, executing the Runnable if it expires. If the timer is already running,
     * it will be stopped and restarted. */
    public GuardTimer start(final int timeout, final Runnable runnable)
    {
        // Stop the timer if it's already running.
        stop();

        _timeout = timeout;
        _runnable = runnable;
        _isRunning = true;
        _asyncTask = new AsyncTask<Void, Void, Boolean>()
        {
            @Override
            protected Boolean doInBackground(Void... params)
            {
                try
                {
                    Thread.sleep(_timeout);
                    return true;
                }
                catch (InterruptedException e)
                {
                    // If we're interrupted, then the timer has been stopped. Do not run the
                    // Runnable.
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean shouldRunRunnable)
            {
                _isRunning = false;
                if (shouldRunRunnable && _runnable!=null)
                    _runnable.run();
            }
        };
        _asyncTask.executeOnExecutor(Util.UTL_EXECUTOR);
        return this;
    }

    /** Check to see if the timer is running. */
    public boolean isRunning()
    {
        return _isRunning;
    }

    /** Stop the timer, preventing the Runnable from executing. */
    public void stop()
    {
        _isRunning = false;
        if (_asyncTask!=null)
            _asyncTask.cancel(true);
    }
}
