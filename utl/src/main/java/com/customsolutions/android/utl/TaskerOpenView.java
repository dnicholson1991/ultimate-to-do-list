package com.customsolutions.android.utl;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import static com.customsolutions.android.utl.Util.log;


/**
 * This displays a list of task lists / views.  The user selects one to be opened by tasker.
 */

public class TaskerOpenView extends UtlTransparentActivity
{
    private static final String TAG = "TaskerOpenView";

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        log(TAG+": Choosing a view to display.");

        if (!Util.isTaskerPluginAvailable(this))
        {
            log(TAG+": Exiting because plugin is not available.");
            return;
        }

        // Display a dialog asking for the view to display:
        ViewPicker viewPicker = new ViewPicker(this);
        viewPicker.chooseView(getString(R.string.select_task_list),
            new ViewPicker.Callback()
        {
            @Override
            public void onViewSelected(long viewID, String viewName)
            {
                // Prepare a Bundle to send back to Tasker:
                Bundle dataForTasker = new Bundle();
                dataForTasker.putString(TaskerReceiver.KEY_BUNDLE_TYPE,TaskerReceiver.
                    BUNDLE_TYPE_TASK_LIST);
                dataForTasker.putLong("view_id",viewID);
                dataForTasker.putString("list_title",viewName);
                try
                {
                    // Put the version code of the app into the Bundle.  This can be useful if future
                    // versions of the app change the Bundle format.
                    PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(),0);
                    dataForTasker.putInt("version_code",packageInfo.versionCode);
                }
                catch (Exception e) { }

                Intent resultIntent = new Intent();
                resultIntent.putExtra(TaskerReceiver.EXTRA_BUNDLE,dataForTasker);
                resultIntent.putExtra(TaskerReceiver.EXTRA_BLURB,viewName);
                log(TAG+": Bundle sent to Tasker: "+Util.bundleToString(resultIntent.
                    getExtras(),2));
                setResult(RESULT_OK,resultIntent);
                finish();
            }

            @Override
            public void onCancel()
            {
                // Nothing to do.
                setResult(RESULT_CANCELED);
                finish();
            }
        });
    }
}
