package androidx.core.app;

public abstract class UtlJobIntentService extends JobIntentService
{
    @Override
    GenericWorkItem dequeueWork()
    {
        try {
            return super.dequeueWork();
        } catch (SecurityException exception) {
            // the exception will be ignored here.
        }
        return null;
    }
}
