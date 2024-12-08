package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class VoiceCommandRead extends VoiceActivity implements OnUtteranceCompletedListener
{
	// UTTERANCE IDs, passed to onUtteranceCompleted:
	private static final String UTTERANCE_READ_MORE_YES_NO = "read_more_yes_no";
	private static final String UTTERANCE_ERROR_YES_NO = "error_yes_no";
	private static final String UTTERANCE_SINGLE_TASK = "single_task";
	private static final String UTTERANCE_LAST_TASK = "last_task";
	
	// Views we need to keep track of:
	private TextView _whatWasSaid;
	private TextView _taskListDisplay;
	private Button _yesButton;
	private Button _noButton;
	private LinearLayout _progressBar;

	// The SpeechRecognizer objects, used for voice recognition:
	private SpeechRecognizer _speech;
	private RecognitionListener _yesNoListener;
	
	private SharedPreferences _settings;
	
	/** This holds the Text-To-Speech functionality */
	private TextToSpeech _tts;
	
	/** Is text to speech available? */
	private boolean _ttsAvailable;
	
	/** Candidate text that was passed in from the speech recognizer. */
	private ArrayList<String> _candidates;

	/** Handles the parsing of the speech */
	private SpeechParser _speechParser = null;
	
	/** Flag which specifies whether the speech was successfully parsed and a task can be created. */
	private boolean _successfulParsing;
	
	/** The view ID being read.  This is zero when parsing was unsuccessful. */
	private long _viewID;
	
	/** This holds the task data: */
	protected ArrayList<HashMap<String,UTLTaskDisplay>> _taskList;

    /** This keeps track of orphaned subtasks that should not be indented. */
	protected HashSet<Long> orphanedSubtaskIDs;
    
	protected DisplayOptions _displayOptions;
	
    // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
    // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
	protected HashMap<Long,ArrayList<UTLTaskDisplay>> _subLists = new HashMap<Long,
        ArrayList<UTLTaskDisplay>>();

	/** The index of the next task to read. */
	private int _nextTaskIndex;
	
	private Handler _mainHandler;

	/** Flag indicating if the onReadyForSpeech() callback has been called. This is used to work
	 * around Google bugs. */
	private boolean _onReadyForSpeechCalled = false;

	/** The start time of speech recognition.  Used to work around Google bugs. */
	private long _recognitionStartTime;

	/** Flag indicating if the user has tapped on the yes or no button. This will block any
	 * attempts to start speech recognition. */
	private boolean _buttonTapped = false;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
		_tag = "VoiceCommandRead";
    	super.onCreate(savedInstanceState);
        log("Showing read screen.");
        
        setContentView(R.layout.voice_command_read);
        
        // Set the title and icon at the top:
        getSupportActionBar().setTitle(R.string.utl_voice_mode);
        
        // Allows the user to control TTS volume using volume keys:
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        // Link to key views:
        _whatWasSaid = (TextView)findViewById(R.id.vm_read_what_was_said);
        _taskListDisplay = (TextView)findViewById(R.id.vm_read_list);
        _yesButton = (Button)findViewById(R.id.vm_read_yes);
        _noButton = (Button)findViewById(R.id.vm_read_no);
        _progressBar = (LinearLayout) findViewById(R.id.vm_read_progress_bar);
        
        _settings = this.getSharedPreferences(Util.PREFS_NAME, 0);
        _mainHandler = new Handler(this.getMainLooper());
        orphanedSubtaskIDs = new HashSet<Long>();
        
        // Check for the speech input:
        Bundle b = this.getIntent().getExtras();
        if (b==null || !b.containsKey("speech_output"))
        {
        	log("Missing speech_output field.");
        	finish();
        }
        _candidates = b.getStringArrayList("speech_output");
        
        // Show the progress bar while the text is being processed, and nothing else.
        _progressBar.setVisibility(View.VISIBLE);
        _taskListDisplay.setVisibility(View.GONE);
        _whatWasSaid.setVisibility(View.GONE);
        _yesButton.setVisibility(View.GONE);
        _noButton.setVisibility(View.GONE);
        
        // Create a Text To Speech object so the feedback can be spoken.
        _tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() 
        {
    	   @SuppressLint("NewApi")
    	   @Override
    	   public void onInit(int status) 
    	   {
    		   if (status==TextToSpeech.SUCCESS)
    		   {
    		        _tts.setLanguage(SpeechParser.getLocale());
    		        _tts.setOnUtteranceCompletedListener(VoiceCommandRead.this);
    			   _ttsAvailable = true;
    		   }
    		   else
    		   {
    			   Util.popup(VoiceCommandRead.this, R.string.Not_Allowing_TTS);
    			   _ttsAvailable = false;
    		   }
    		   
    		   // After TTS is ready, we start processing the text that was passed in.
    		   ProcessSpeech ps = new ProcessSpeech();
    		   if (Build.VERSION.SDK_INT >= 11)
    			   ps.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    		   else
    			   ps.execute();
    		   
    	   }
        });
        
        // On small devices, we lock the orientation in portrait mode.  On larger devices, we lock
        // the orientation at the starting orientation:
        float diagonalScreenSize = _settings.getFloat(PrefNames.DIAGONAL_SCREEN_SIZE, 5.0f);
        if (diagonalScreenSize<6.1)
        {
        	// Lock in portrait orientation:
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        else
        {
        	// Lock in the current orientation:
        	lockScreenOrientation();
        }

        // Button handlers for the yes and no buttons:
        _yesButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				log("User tapped on \"Yes\".");
				_buttonTapped = true;
				if (_speech!=null)
					_speech.destroy();
				handleYes();
			}
		});
        _noButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				log("User tapped on \"No\".");
				_buttonTapped = true;
				if (_speech!=null)
					_speech.destroy();
				handleNo();
			}
		});
        
        // A listener for "yes" and "no":
        _yesNoListener = new RecognitionListener()
        {
			@Override
			public void onBeginningOfSpeech()
			{
				log("onBeginningOfSpeech() called.");
			}

			@Override
			public void onBufferReceived(byte[] buffer)
			{
			}

			@Override
			public void onEndOfSpeech()
			{
				log("onEndOfSpeech() called.");
				stopBluetoothSpeechRecognition(null);
			}

			@Override
			public void onError(int error)
			{
                switch (error)
				{
					case SpeechRecognizer.ERROR_NO_MATCH:
						if (!_onReadyForSpeechCalled)
						{
							// A glitch on Google's side we can ignore.
							log("Ignoring ERROR_NO_MATCH");
							return;
						}
						if ((System.currentTimeMillis()-_recognitionStartTime)<5000)
						{
							// Android sometimes calls this too early, before the user has had a genuine
							// chance to say anything. When this happens, start listening again.
							log("ERROR_NO_MATCH called too soon. Restarting recognition.");
							startListeningForYesNo();
						}
						break;

					// With these errors, we just stop listening and make the user press a button:
					case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
					case SpeechRecognizer.ERROR_CLIENT:
					case SpeechRecognizer.ERROR_AUDIO:
					case SpeechRecognizer.ERROR_NETWORK:
					case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
					case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
					case SpeechRecognizer.ERROR_SERVER:
					case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
						log("Got error of type "+error);
						stopBluetoothSpeechRecognition(null);
						break;
				}
			}

			@Override
			public void onEvent(int eventType, Bundle params)
			{
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onPartialResults(Bundle partialResults)
			{
				// TODO Auto-generated method stub
				
			}

			@Override
			public void onReadyForSpeech(Bundle params)
			{
				_onReadyForSpeechCalled = true;
			}

			@Override
			public void onResults(Bundle results)
			{
				stopBluetoothSpeechRecognition(null);
				// Check to see if the received speech is a yes or no.
				ArrayList<String> candidates = results.getStringArrayList(SpeechRecognizer.
					RESULTS_RECOGNITION);
				Iterator<String> it = candidates.iterator();
				while (it.hasNext())
				{
					String text = it.next();
					if (text.toLowerCase().equals(VoiceCommandRead.this.getString(R.string.VM_Yes).
						toLowerCase()))
					{
						handleYes();
						return;
					}
					if (text.toLowerCase().equals(VoiceCommandRead.this.getString(R.string.VM_No).
						toLowerCase()))
					{
						handleNo();
						return;
					}
				}

				// If we didn't hear a yes or no, then try listening some more.
				startListeningForYesNo();
			}

			@Override
			public void onRmsChanged(float rmsdB)
			{
			}
        };
    }

    /** This Async task processes the speech we received. */
    public class ProcessSpeech extends AsyncTask<Void,Void,String>
    {
    	protected String doInBackground(Void... v)
    	{
	    	// Go through all of the candidates.  Accept the first one that can be parsed.
	    	if (_speechParser==null)
				_speechParser = SpeechParser.getSpeechParser(VoiceCommandRead.this);
	    	String firstError = "";
	    	Iterator<String> it = _candidates.iterator();
	    	int acceptedCandidate = -1;
	    	while (it.hasNext())
	    	{
	    		acceptedCandidate++;
	    		String speech = it.next();
	    		String response = _speechParser.parseRead(speech);
	    		if (response.matches("\\d+"))
	    		{
	    			// A view ID was returned.  This indicates success.
	    			_viewID = Long.parseLong(response);
	    			_successfulParsing = true;
	    			return Integer.valueOf(acceptedCandidate).toString();
	    		}
	    		else
	    		{
	    			// Save the first error message, but try the other candidates.
	    			if (firstError.length()==0)
        				firstError = response;
	    			continue;
	    		}
	    	}
	    	
	    	// If we get there, all of the candidates failed to identify a view ID.  Return the error
	    	// message for the first candidate.
	    	_viewID = 0;
	    	_successfulParsing = false;
	    	return firstError;
    	}
    	
    	// This function received either an error message, or the accepted candidate index.
    	protected void onPostExecute(String response)
    	{
    		// Remove the progress bar and display what the app heard.
    		_progressBar.setVisibility(View.GONE);
    		_whatWasSaid.setVisibility(View.VISIBLE);
    		_taskListDisplay.setVisibility(View.VISIBLE);
    		
     		if (response.matches("\\d+") && _viewID>0)
    		{
    			// An accepted candidate index was passed in.  Start by displaying the text of what was
    			// said:
    			int candidateIndex = Integer.parseInt(response);
    			_whatWasSaid.setText("\""+_candidates.get(candidateIndex)+"\"");
    			
    			// Fetch the view and initialize the Display Options (which do have an effect on the 
    			// task order if subtasks are used.)
    			Cursor viewCursor = (new ViewsDbAdapter()).getView(_viewID);
    			_displayOptions = new DisplayOptions(Util.cString(viewCursor, "display_string"));
    			
    			// Run the database query and get the task list to read:
                String query = Util.getTaskSqlQuery(_viewID, viewCursor, VoiceCommandRead.this);
                runQuery(query);
    			
    			viewCursor.close();
    			
    			// Make sure we actually have some tasks:
    			if (_taskList.size()==0)
    			{
    				String textToDisplay = VoiceCommandRead.this.getString(R.string.No_Tasks_Found)+" "+
    					getString(R.string.VM_Try_Again);
        			_taskListDisplay.setText(textToDisplay);
        			_yesButton.setVisibility(View.VISIBLE);
        			_noButton.setVisibility(View.VISIBLE);
        			_successfulParsing = false;
        			speak(textToDisplay,UTTERANCE_ERROR_YES_NO);
    				return;
    			}

                // Record usage of the voice mode feature:
                FeatureUsage featureUsage = new FeatureUsage(VoiceCommandRead.this);
                featureUsage.record(FeatureUsage.VOICE_MODE);

    			// Start reading the tasks:
    			_nextTaskIndex = 0;
				_taskListDisplay.setText(_taskList.get(0).get("task").task.title);
				speak(_taskList.get(0).get("task").task.title,UTTERANCE_SINGLE_TASK);
				
				// the yes and no buttons are not visible during reading.
				_yesButton.setVisibility(View.GONE);
				_noButton.setVisibility(View.GONE);

    		}
    		else
    		{
    			// Display the error message along with the "try again" prompt.  Note that items in
            	// parenthesis are not spoken.
    			String textToDisplay = response+" "+getString(R.string.VM_Try_Again);
    			String textToSay = textToDisplay.replaceAll("\\s*\\(.*?\\)\\s*", "");
    			_whatWasSaid.setText("\""+_candidates.get(0)+"\"");
    			_taskListDisplay.setText(response+" "+getString(R.string.VM_Try_Again));
    			_yesButton.setVisibility(View.VISIBLE);
    			_noButton.setVisibility(View.VISIBLE);
    			speak(textToSay,UTTERANCE_ERROR_YES_NO);
    		}
    	}
    }
    
	// Given a cursor, get an instance of UTLTaskDisplay:
    private UTLTaskDisplay cursorToUTLTaskDisplay(Cursor c)
    {
        UTLTaskDisplay td = new UTLTaskDisplay();
        
        // The cursor fields are in the same order that TasksDbAdapter expects, so use 
        // the TasksDbAdapter function to get the UTLTask object:
        td.task = (new TasksDbAdapter()).getUTLTask(c);
        
        td.firstTagName = Util.cString(c, "tag_name");
        td.accountName = Util.cString(c, "account");
        td.folderName = Util.cString(c,"folder");
        td.contextName = Util.cString(c,"context");
        td.goalName = Util.cString(c, "goal");
        td.locationName = Util.cString(c, "location");
        td.numTags = Util.cLong(c, "num_tags");
        
        // Collaboration - owner:
        td.ownerName = Util.cString(c,"owner_name");
        CollaboratorsDbAdapter cdb = new CollaboratorsDbAdapter();
        
        // Collaboration - Assignor / Added By:
        td.assignorName = Util.cString(c, "assignor_name");
        
        // Collaboration - Shared With:
        if (td.task.shared_with.length()>0)
        {
        	UTLAccount a = (new AccountsDbAdapter()).getAccount(td.task.account_id);
        	String[] collIDs = td.task.shared_with.split("\n");
        	td.sharedWith = "";
        	for (int i=0; i<collIDs.length; i++)
        	{
        		if (td.sharedWith.length()>0)
        			td.sharedWith += ", ";
        		if (collIDs[i].equals(a.td_userid))
        			td.sharedWith += Util.getString(R.string.Myself);
        		else
        		{
        			UTLCollaborator co = cdb.getCollaborator(td.task.account_id, collIDs[i]);
        			if (co!=null)
        				td.sharedWith += co.name;        				
        		}
        	}
        }
        else
        	td.sharedWith = Util.getString(R.string.None);

        return(td);
    }
    
    /** Run the database query that fetches the list of tasks for the view being read. */
    private void runQuery(String query)
    {
    	Cursor c = Util.db().rawQuery(query, null);    
    	
    	// Convert the database query results into a structure that can be re-ordered:
    	if (_taskList==null)
    		_taskList = new ArrayList<HashMap<String,UTLTaskDisplay>>();
    	else
    		_taskList.clear();
    	c.moveToPosition(-1);
        while(c.moveToNext())
        {
        	HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
        	UTLTaskDisplay td = this.cursorToUTLTaskDisplay(c);
            hash.put("task", td);
            _taskList.add(hash);
        }
        c.close();
    	
        // If necessary, reorder the task list to put subtasks below their parents.
        // This function also generates a list of parent tasks with subtasks.
        reorder_task_list();
    }
    
    // If the task list indents subtasks, we need to reorder the list:
    private void reorder_task_list()
    {
        // This is a HashMap of ArrayList objects.  The key is a parent task ID, the ArrayList
        // is a list of UTLTaskDisplay objects that are subtasks of a particular parent.
    	if (_subLists==null)
    		_subLists = new HashMap<Long,ArrayList<UTLTaskDisplay>>();
    	else
    		_subLists.clear();
        
        // This is an ArrayList of UTLTaskDisplay objects that are NOT subtasks:
        ArrayList<UTLTaskDisplay> parentList = new ArrayList<UTLTaskDisplay>();
        
        // We also need a hash of all task IDs:
        HashSet<Long> allIDs = new HashSet<Long>();
        
        // Populate the 3 lists described above:
        Iterator<HashMap<String,UTLTaskDisplay>> it = _taskList.iterator();
        while (it.hasNext())
        {
            UTLTaskDisplay td = it.next().get("task");
            allIDs.add(td.task._id);
            if (td.task.parent_id==0)
            {
                // Not a subtask:
                parentList.add(td);
            }
            else
            {
                if (!_subLists.containsKey(td.task.parent_id))
                {
                    _subLists.put(td.task.parent_id, new ArrayList<UTLTaskDisplay>());
                }
                _subLists.get(td.task.parent_id).add(td);
            }
        }
        
        if (_displayOptions.subtaskOption.equals("indented") && _settings.
        	getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	// Subtasks are indented:
        	if (_displayOptions.parentOption==1)
        	{
        		// Orphaned subtasks will not be displayed.
	            // Clear out and repopulate the main list for this class:
	            _taskList.clear();
	            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
	            while (it2.hasNext())
	            {
	                // Add in the non-subtask:
	                UTLTaskDisplay td = it2.next();
	                td.level = 0;
	                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
	                hash.put("task", td);
	                _taskList.add(hash);
	                
	                // If this task has any children, then add them in next:
	                if (_subLists.containsKey(td.task._id))
	                	addChildTasksToList(td.task._id, 0);
	            }
        	}
        	else
        	{
        		// Orphaned subtasks will be displayed at the same level as parent
        		// tasks.
         		
        		ArrayList<HashMap<String,UTLTaskDisplay>> taskList2 = 
        			(ArrayList<HashMap<String,UTLTaskDisplay>>)_taskList.clone();
        		_taskList.clear();
        		Iterator<HashMap<String,UTLTaskDisplay>> it2 = taskList2.iterator();
        		while (it2.hasNext())
        		{
        			UTLTaskDisplay td = it2.next().get("task");
        			if (td.task.parent_id==0)
        			{
        				// It's not a subtask.  Add it into the final task list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
        					UTLTaskDisplay>();
        				td.level = 0;
    	                hash.put("task", td);
    	                _taskList.add(hash);
    	                
    	                // If this task has any children, then add them in next:
    	                if (_subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        			else if (!allIDs.contains(td.task.parent_id))
        			{
        				// It's an orphaned subtask.  At it into the final list:
        				HashMap<String,UTLTaskDisplay> hash = new HashMap<String,
    						UTLTaskDisplay>();
        				td.level = 0;
        				hash.put("task", td);
        				_taskList.add(hash);
        				orphanedSubtaskIDs.add(td.task._id);
        				
        				// If this task has any children, then add them in next:
    	                if (_subLists.containsKey(td.task._id))
    	                	addChildTasksToList(td.task._id, 0);
        			}
        		}        		
        	}
        }
        else if (_displayOptions.subtaskOption.equals("separate_screen") && _settings.
        	getBoolean(PrefNames.SUBTASKS_ENABLED, true))
        {
        	// For this option, subtasks are not displayed at all here, so 
        	// we need to repopulate the main list with only parent tasks:
        	_taskList.clear();
            Iterator<UTLTaskDisplay> it2 = parentList.iterator();
            while (it2.hasNext())
            {
                // Add in the non-subtask:
                UTLTaskDisplay td = it2.next();
                HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
                td.level = 0;
                hash.put("task", td);
                _taskList.add(hash);                
            }
        }
    }

    // Add child tasks to the list of tasks to display:
    void addChildTasksToList(long taskID, int parentLevel)
    {
    	ArrayList<UTLTaskDisplay> childList = _subLists.get(taskID);
        Iterator<UTLTaskDisplay> it3 = childList.iterator();
        while (it3.hasNext())
        {
            HashMap<String,UTLTaskDisplay> hash = new HashMap<String,UTLTaskDisplay>();
            UTLTaskDisplay child = it3.next();
            child.level = parentLevel+1;
            hash.put("task", child);
            _taskList.add(hash);
            
            if (_subLists.containsKey(child.task._id))
            	addChildTasksToList(child.task._id, parentLevel+1);
        }
    }

    /** Speak some text.
     * @param text - What to say
     * @param utteranceID - One of the utterance IDs defined in this class.  This will be passed to
     *     onUtteranceCompleted().
     */
    private void speak(String text, String utteranceID)
    {
    	// Tells the text to speech engine to call onUtteranceCompleted() when done:
    	HashMap<String, String> myHashAlarm = new HashMap<String, String>();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceID);
    	if (_ttsAvailable)
    		_tts.speak(text,TextToSpeech.QUEUE_FLUSH,myHashAlarm);
    	else
    		onUtteranceCompleted(utteranceID);
    }
    
    @Override
	public void onUtteranceCompleted(String utteranceId)
	{
		if (utteranceId.equals(UTTERANCE_SINGLE_TASK))
		{
			// Regardless of what action we take, we always have a brief pause between utterances.
			try
			{
				Thread.sleep(750);
			} 
			catch (InterruptedException e) { }

			int tasksPerPage = _settings.getInt(PrefNames.VM_TASKS_TO_READ_PER_PAGE, 5);
			_nextTaskIndex++;
			if (_nextTaskIndex>=_taskList.size())
			{
				// We just read the last task.
				speak(getString(R.string.End_of_List),UTTERANCE_LAST_TASK);
			}
			else if (_nextTaskIndex % tasksPerPage == 0)
			{
				_mainHandler.post(new Runnable() {
					@Override
					public void run()
					{
						String currentText = _taskListDisplay.getText().toString();
						currentText = currentText + "\n\n" + getString(R.string.Read_More);
						_taskListDisplay.setText(currentText);
							
						// Offer to go the next page.
						speak(getString(R.string.Read_More),UTTERANCE_READ_MORE_YES_NO);
						
						// Show the yes and no buttons:
						_yesButton.setVisibility(View.VISIBLE);
						_noButton.setVisibility(View.VISIBLE);
					}
				});
			}
			else
			{
				_mainHandler.post(new Runnable() {
					@Override
					public void run()
					{
						// Show the next task:
						String currentText = _taskListDisplay.getText().toString();
						currentText = currentText + "\n" + _taskList.get(_nextTaskIndex).get("task").task.title;
						_taskListDisplay.setText(currentText);
						
						// Read the next task.
						speak(_taskList.get(_nextTaskIndex).get("task").task.title,UTTERANCE_SINGLE_TASK);
					}
				});
			}
		}
		
		if (utteranceId.equals(UTTERANCE_READ_MORE_YES_NO))
		{
			_mainHandler.post(new Runnable() {
				@Override
				public void run()
				{
					// Listen for a yes or no:
					startListeningForYesNo();
				}
			});
		}
		
		if (utteranceId.equals(UTTERANCE_LAST_TASK))
		{
			// After the last task, we simply return to the voice command entry.
			Intent result = new Intent();
    		result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_TRIGGER);
    		this.setResult(RESULT_OK, result);
    		finish();
    		return;
		}
		
		if (utteranceId.equals(UTTERANCE_ERROR_YES_NO))
		{
			_mainHandler.post(new Runnable() {
				@Override
				public void run()
				{
					// Listen for a yes or no:
					startListeningForYesNo();
				}
			});
		}
	}

    // Start listening for a "yes" or "no:
    private void startListeningForYesNo()
	{
		if (_buttonTapped)
		{
			log("Not listening for yes/no since the user has tapped a button.");
			return;
		}

		if (_speech == null)
		{
			_speech = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
			if (_speech == null)
			{
				Util.popup(this, R.string.No_Speech_Recognition);
				finish();
				return;
			}
			_speech.setRecognitionListener(_yesNoListener);
		}

        // Enable speech recognition through the bluetooth headset, if one is connected.
		startBluetoothSpeechRecognition();

        Intent recognizerIntent = new Intent();
    	recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.
    		LANGUAGE_MODEL_WEB_SEARCH);
    	recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechParser.getLocale().toString());
    	recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SpeechParser.getLocale().toString());
    	recognizerIntent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{});
		_onReadyForSpeechCalled = false;
		_recognitionStartTime = System.currentTimeMillis();
		_state = STATE_LISTENING_FOR_COMMAND;
		_speech.startListening(recognizerIntent);
    }
    
    private void handleYes()
    {
    	if (!_successfulParsing)
    	{
    		// This means the user wants to try again after a failure to parse the speech.
    		Intent result = new Intent();
    		result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_COMMAND);
    		this.setResult(RESULT_OK, result);
    		finish();
    		return;
    	}
    	
    	// If parsing was successful, a "yes" means that the user wants to read more of the list.
		_taskListDisplay.setText(_taskList.get(_nextTaskIndex).get("task").task.title);
		speak(_taskList.get(_nextTaskIndex).get("task").task.title,UTTERANCE_SINGLE_TASK);
		
		// the yes and no buttons are not visible during reading.
		_yesButton.setVisibility(View.GONE);
		_noButton.setVisibility(View.GONE);    	
    }
    
    private void handleNo()
    {
    	// Regardless of when a "no" is said, the user goes back to the voice command screen, which
    	// listens for the trigger phrase.
    	Intent result = new Intent();
		result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_TRIGGER);
		this.setResult(RESULT_OK, result);
		finish();
		return;
    }

    @Override
    public void onDestroy()
    {
    	if (_tts!=null)
    		_tts.shutdown();

		if (_speech!=null)
		{
			_speech.destroy();
			log("Recognizer destroyed.");
		}

		super.onDestroy();
    }
}
