package com.customsolutions.android.utl;

import org.droidparts.widget.ClearableEditText;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Fragment for editing a tag.  Pass in a Bundle with key "mode" and value of
 * EDIT.  Also pass in a key of "id", containing the database ID.
 * @author Nicholson
 *
 */
public class EditTagFragment extends GenericEditorFragment
{
	// Identifies this type of fragment:
    public static final String FRAG_TAG = "EditTagFragment";
    
    // Quick reference to key views:
    private ClearableEditText _title;
    
    // Quick access to database tables:
    private CurrentTagsDbAdapter _tagsDB;
    
    // The original tag name:
    private String _originalTagName;
    
    /** Returns the view being used by this Fragment: */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
    	return inflater.inflate(R.layout.edit_tag, container, false);
    }

    // A progress dialog, for operations that may take some time:
 	private ProgressDialog _progressDialog;
 	
    /** Called when Activity is started: */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) 
    {
        Util.log("Opening Tag Editor.");
        super.onActivityCreated(savedInstanceState);
        
        _tagsDB = new CurrentTagsDbAdapter();
        
        // Get references to key views:
        _title = (ClearableEditText)_rootView.findViewById(R.id.edit_tag_title);
        
        setTitle(R.string.Rename_Tag);
        
        // Set values for the views:
        _originalTagName = _tagsDB.getTagByID(_id);
        if (_originalTagName!=null)
        	_title.setText(_originalTagName);
    }
    
    /** Check for valid values, then save and exit if possible. */
    @SuppressLint("NewApi")
	@Override
    protected void handleSave()
    {
    	// Make sure there is a title:
    	if (_title.getText().toString().length()==0)
    	{
    		Util.popup(_a,R.string.Please_enter_a_name);
    		return;
    	}
    	String title = _title.getText().toString();
    	
    	if (title.length()>Util.MAX_TAG_STRING_LENGTH)
    	{
    		Util.popup(_a,R.string.Name_too_long);
    	}
    	
    	if (!title.equals(_originalTagName))
    	{
	    	_tagsDB.renameRecent(_originalTagName, title);
	    	
	    	// Update the tasks in the background:
	    	_progressDialog = ProgressDialog.show(_a, null,Util.getString(R.string.
	    		Updating_Tags), false);
	    	if (Build.VERSION.SDK_INT >= 11)
	    	{
	    		new RenameTagInTasks().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,_originalTagName,
	    			title);
	    	}
	    	else
	    	{
	    		new RenameTagInTasks().execute(_originalTagName,title);
	    	}
    	}
    	else
    	{
    		refreshAndEnd();
    	}
    }
    
    // An AyncTask to rename a tag. Alters all tasks that use that tag.
 	// The first string input is the old name, the second is the new name.
 	private class RenameTagInTasks extends AsyncTask<String,Void,Void>
 	{
 		@Override
    	protected void onPreExecute()
    	{
    		_a.lockScreenOrientation();
    	}
 		
 		protected Void doInBackground(String...strings)
 		{
 			(new TagsDbAdapter()).renameTag(strings[0], strings[1]);
 			return null;
 		}
 		
 		protected void onPostExecute(Void v)
 		{
 			if (_progressDialog!=null && _progressDialog.isShowing())
 				_progressDialog.dismiss();
 			_a.unlockScreenOrientation();
 			refreshAndEnd();
 		}
 	} 	
}
