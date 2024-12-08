package com.customsolutions.android.utl;

import java.util.concurrent.Semaphore;

/**
 * A special semaphore that includes only one permit, and prevents the number of permits from
 * exceeding one, even if release() is called multiple times.
 */
public class SinglePermitSemaphore extends Semaphore
{
    /** An internal semaphore that guards access to key pieces of code. */
    private Semaphore _internalSemaphore;

    /** The number of permits available. Either 0 or 1. */
    private int _permitsAvailable;

    public SinglePermitSemaphore()
    {
        super(1);
        _internalSemaphore = new Semaphore(1,true);
        _permitsAvailable = 1;
    }

    public SinglePermitSemaphore(boolean fair)
    {
        super(1,fair);
        _internalSemaphore = new Semaphore(1,true);
        _permitsAvailable = 1;
    }

    @Override
    public void acquire() throws InterruptedException
    {
        super.acquire();
        _internalSemaphore.acquireUninterruptibly();
        _permitsAvailable = 0;
        _internalSemaphore.release();
    }

    @Override
    public void acquireUninterruptibly()
    {
        super.acquireUninterruptibly();
        _internalSemaphore.acquireUninterruptibly();
        _permitsAvailable = 0;
        _internalSemaphore.release();
    }

    @Override
    public boolean tryAcquire()
    {
        _internalSemaphore.acquireUninterruptibly();
        boolean isAcquired = super.tryAcquire();
        if (isAcquired)
            _permitsAvailable = 0;
        _internalSemaphore.release();
        return isAcquired;
    }

    @Override
    public void release()
    {
        _internalSemaphore.acquireUninterruptibly();
        if (_permitsAvailable==0)
            super.release();
        _permitsAvailable = 1;
        _internalSemaphore.release();
    }
}
