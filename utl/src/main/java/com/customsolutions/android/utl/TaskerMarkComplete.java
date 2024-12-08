package com.customsolutions.android.utl;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import org.droidparts.widget.ClearableEditText;

/**
 * This is called by Tasker to specify a task that should be marked as complete.
 */

public class TaskerMarkComplete extends UtlTransparentActivity
{
    private static final String TAG = "TaskerMarkComplete";

    // Views we need to keep track of:
    private ClearableEditText _title;

    /** Log a string. */
    private void log(String s)
    {
        Log.v(TAG,s);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        log("Starting TaskerMarkComplete Activity.");

        // Get the layout to display in the dialog:
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogRoot = (ViewGroup) inflater.inflate(R.layout.tasker_mark_complete,null);
        _title = (ClearableEditText)dialogRoot.findViewById(R.id.tasker_task_title);

        // Check to see if a title was passed in from Tasker.  If so, set the EditText.
        Intent i = getIntent();
        if (i!=null && i.getExtras()!=null && i.getExtras().containsKey(TaskerReceiver.
            EXTRA_BUNDLE))
        {
            Bundle dataFromTasker = i.getExtras().getBundle(TaskerReceiver.EXTRA_BUNDLE);
            if (dataFromTasker.containsKey("title"))
            {
                String title = dataFromTasker.getString("title");
                log("Modifying a task title: "+title);
                _title.setText(title);
            }
            else
                log("Task title is missing from Bundle.");
        }
        else
            log("Entering a new task title.");

        if (!Util.isTaskerPluginAvailable(this))
        {
            log("Exiting because plugin is not available.");
            return;
        }

        // Configure and show the dialog:
        new AlertDialog.Builder(this)
            .setView(dialogRoot)
            .setTitle(R.string.mark_task_complete)
            .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    saveAndExit();
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            })
            .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            })
            .show();

        // Handle the ENTER key:
        _title.setOnKeyListener(new View.OnKeyListener()
        {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event)
            {
                if (event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.
                    KEYCODE_ENTER)
                {
                    saveAndExit();
                    return true;
                }
                return false;
            }
        });
    }

    /** Save the title and exit. */
    private void saveAndExit()
    {
        // Make sure a title is specified:
        String title = _title.getText().toString();
        if (title.length()==0)
        {
            Util.popup(this,R.string.missing_title);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Prepare a Bundle to send back to Tasker:
        Bundle dataForTasker = new Bundle();
        dataForTasker.putString(TaskerReceiver.KEY_BUNDLE_TYPE,TaskerReceiver.
            BUNDLE_TYPE_MARK_COMPLETE);
        dataForTasker.putString("title",title);
        try
        {
            // Put the version code of the app into the Bundle.  This can be useful if future
            // versions of the app change the Bundle format.
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(),0);
            dataForTasker.putInt("version_code",packageInfo.versionCode);
        }
        catch (Exception e) { }
        if (TaskerPlugin.Setting.hostSupportsOnFireVariableReplacement(this))
        {
            log("Calling setVariableReplaceKeys().");
            TaskerPlugin.Setting.setVariableReplaceKeys(dataForTasker,new String[] {"title"});
        }
        else
            log("NOT calling setVariableReplaceKeys().");

        Intent resultIntent = new Intent();
        resultIntent.putExtra(TaskerReceiver.EXTRA_BUNDLE,dataForTasker);
        resultIntent.putExtra(TaskerReceiver.EXTRA_BLURB,title);
        log("Bundle sent to Tasker: "+Util.bundleToString(resultIntent.getExtras(),2));
        setResult(RESULT_OK,resultIntent);
        finish();
    }
}
