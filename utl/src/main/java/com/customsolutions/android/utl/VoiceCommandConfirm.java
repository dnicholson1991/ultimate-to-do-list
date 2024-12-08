package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.content.Context;
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TimeZone;
import java.util.UUID;

/** Handles task adding and editing.  Parses the speech and displays a confirmation or error message. 
 * pass an extra in the Intent with the key speech_output, which is an ArrayList<String> containing
 * the possible speech recognition results. */
public class VoiceCommandConfirm extends VoiceActivity implements OnUtteranceCompletedListener
{
	// UTTERANCE IDs, passed to onUtteranceCompleted:
	private static final String UTTERANCE_ASK_FOR_YES_NO = "ask_for_yes_no";
	private static final String UTTERANCE_SUCCESSFUL_SAVE = "successful_save";
	
	// Views we need to keep track of:
	private TextView _whatWasSaid;
	private TextView _imReady;
	private LinearLayout _fieldsList;
	private Button _yesButton;
	private Button _noButton;
	private LinearLayout _progressBar;
	
	// The SpeechRecognizer objects, used for voice recognition:
	private SpeechRecognizer _speech;
	private RecognitionListener _yesNoListener;
	
	private SharedPreferences _settings;
	
	/** The operation (add or modify) */
	private int _op;
	
	/** This holds information on the task being added or modified. */
	private UTLTaskSpeech _ts;
	ArrayList<UTLTaskSpeech> _taskArray;
	
	/** This holds the Text-To-Speech functionality */
	private TextToSpeech _tts;
	
	/** Is text to speech available? */
	private boolean _ttsAvailable;
	
	/** Candidate text that was passed in from the speech recognizer. */
	private ArrayList<String> _candidates;
	
	/** Flag which specifies whether the speech was successfully parsed and a task can be created. */
	private boolean _successfulParsing;
	
	/** Handles the parsing of the speech */
	private SpeechParser _speechParser = null;

	/** Flag indicating if the onReadyForSpeech() callback has been called. This is used to work
	 * around Google bugs. */
	private boolean _onReadyForSpeechCalled = false;

	/** The start time of speech recognition.  Used to work around Google bugs. */
	private long _recognitionStartTime;

	/** Flag indicating if the user has tapped on the yes or no button. This will block any
	 * attempts to start speech recognition. */
	private boolean _buttonTapped = false;

	/** This works around a new Google bug that started around March 2020. See:
	 * https://issuetracker.google.com/issues/152628934 */
	private boolean _onResultsCalled;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
		_tag = "VoiceCommandConfirm";
    	super.onCreate(savedInstanceState);
        log("Showing confirmation.");
		_onResultsCalled = false;
        
        setContentView(R.layout.voice_command_confirm);
        
        // Set the title and icon at the top:
        getSupportActionBar().setTitle(R.string.utl_voice_mode);
        
        // Allows the user to control TTS volume using volume keys:
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        // Link to Key Views:
        _whatWasSaid = (TextView) findViewById(R.id.vm_confirm_what_was_said);
        _imReady = (TextView) findViewById(R.id.vm_confirm_im_ready);
        _fieldsList = (LinearLayout) findViewById(R.id.vm_confirm_fields_list);
        _yesButton = (Button) findViewById(R.id.vm_confirm_yes);
        _noButton = (Button) findViewById(R.id.vm_confirm_no);
        _progressBar = (LinearLayout) findViewById(R.id.vm_confirm_progress_bar);
        
        _settings = this.getSharedPreferences(Util.PREFS_NAME, 0);
        
        // Check for the speech input:
        Bundle b = this.getIntent().getExtras();
        if (b==null || !b.containsKey("speech_output"))
        {
        	log("Missing speech_output field.");
        	finish();
        }
        _candidates = b.getStringArrayList("speech_output");
        
        // Show the progress bar while the text is being processed, and hide the "I'm ready" message.
        _progressBar.setVisibility(View.VISIBLE);
        _imReady.setVisibility(View.GONE);
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
    		        _tts.setOnUtteranceCompletedListener(VoiceCommandConfirm.this);
    			   _ttsAvailable = true;
    		   }
    		   else
    		   {
    			   Util.popup(VoiceCommandConfirm.this, R.string.Not_Allowing_TTS);
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
				_onResultsCalled = false;
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
			}

			@Override
			public void onPartialResults(Bundle partialResults)
			{
			}

			@Override
			public void onReadyForSpeech(Bundle params)
			{
				_onReadyForSpeechCalled = true;
			}

			@Override
			public void onResults(Bundle results)
			{
				if (_onResultsCalled)
				{
					Log.d(_tag,"Blocking extra call to onResults().");
					return;
				}
				_onResultsCalled = true;

				stopBluetoothSpeechRecognition(null);
				// Check to see if the received speech is a yes or no.
				ArrayList<String> candidates = results.getStringArrayList(SpeechRecognizer.
					RESULTS_RECOGNITION);
				Iterator<String> it = candidates.iterator();
				while (it.hasNext())
				{
					String text = it.next();
					log("Spoken text candidate: "+text);
					if (text.toLowerCase().equals(VoiceCommandConfirm.this.getString(R.string.VM_Yes).
						toLowerCase()))
                    {
                        handleYes();
                        return;
                    }
					if (text.toLowerCase().equals(VoiceCommandConfirm.this.getString(R.string.VM_No).
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
    
    /** Add a field and value to the list of fields and values. */
    private void addFieldAndValue(String fieldName, String fieldValue)
    {
    	LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	LinearLayout row = (LinearLayout)inflater.inflate(R.layout.voice_command_confirm_row, null);
		TextView fieldNameTV = (TextView) row.findViewById(R.id.vm_confirm_field_name);
		TextView fieldValueTV = (TextView) row.findViewById(R.id.vm_confirm_field_value);
		fieldNameTV.setText(fieldName);
		fieldValueTV.setText(fieldValue);
		_fieldsList.addView(row);
		
    }
    
    /** This Async task processes the speech we received. */
    public class ProcessSpeech extends AsyncTask<Void,Void,String>
    {
    	protected String doInBackground(Void... v)
    	{
            // Go through all of the candidates.  Accept the first one that can be parsed.
    		if (_speechParser==null)
    			_speechParser= SpeechParser.getSpeechParser(VoiceCommandConfirm.this);
            Iterator<String> it = _candidates.iterator();
            String firstError = "";
            int currentCandidate = -1;
            boolean isSuccessful = false;
            int bestCandidate = 0;
            int bestCandidateFieldCount = -1;
            _taskArray = new ArrayList<UTLTaskSpeech>();
            while (it.hasNext())
            {
            	currentCandidate++;
            	String speech = it.next();
            	_op = _speechParser.getOperationType(speech);
            	if (_op==SpeechParser.OP_ADD)
            	{
                    _taskArray.add(new UTLTaskSpeech());
            		String responseMessage = _speechParser.parseNewTask(_taskArray.get(currentCandidate), 
            			speech);
            		if (responseMessage.matches("\\d+"))
            		{
            			// A successful response that didn't match any fields does not override
            			// a prior unsuccessful response.  This just means that the parser thinks the
            			// whole thing is a task title.
            			if (responseMessage.equals("0") && firstError.length()>0)
            				continue;
            			
            			// A successful match occurred.  The parser returned the number of fields
            			// it could identify.  Candidates with higher numbers of fields are more likely
            			// to be what the user intended.
            			isSuccessful = true;
        				int numFields = Integer.parseInt(responseMessage);
        				if (numFields>bestCandidateFieldCount)
        				{
        					bestCandidateFieldCount = numFields;
        					bestCandidate = currentCandidate;
        				}
            			continue;
            		}
            		else
            		{
            			// An error occurred.  Keep track of the first error, since this will be displayed
            			// if none of the candidates can be parsed.
            			if (firstError.length()==0)
            				firstError = responseMessage;
            			continue;
            		}
            	}
            	else
            	{
            		// An edit operation.
            		UTLTaskSpeech response = _speechParser.parseTaskUpdate(speech);
            		_taskArray.add(response);
            		if (response.error.length()==0)
            		{
            			// Because edit operations need to match existing tasks (making the parsing more
            			// strict), we can assume that the first candidate that is parsed successfully
            			// is the one to use.
            			return Integer.valueOf(currentCandidate).toString();
            		}
            		else
            		{
            			if (firstError.length()==0)
            				firstError = response.error;
            			continue;
            		}
            	}
            }
            
            if (!isSuccessful)
            	return firstError;
            else
            {
            	// On success, we pass back the index of the accepted text candidate.
            	return Integer.valueOf(bestCandidate).toString();
            }
    	}
    	
    	protected void onPostExecute(String response)
    	{
    		// Display the controls we need:
        	_progressBar.setVisibility(View.GONE);
        	_imReady.setVisibility(View.VISIBLE);
        	_whatWasSaid.setVisibility(View.VISIBLE);
        	_yesButton.setVisibility(View.VISIBLE);
        	_noButton.setVisibility(View.VISIBLE);
    		
        	// Tells the text to speech engine to call onUtteranceCompleted() when done:
        	HashMap<String, String> myHashAlarm = new HashMap<String, String>();
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
            myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ASK_FOR_YES_NO);

            if (!response.matches("^\\d+$"))
    		{
    			// Display the error message along with the "try again" prompt.  Note that items in
            	// parenthesis are not spoken.
    			String textToDisplay = response+" "+getString(R.string.VM_Try_Again);
    			String textToSay = textToDisplay.replaceAll("\\s*\\(.*?\\)\\s*", "");
    			_whatWasSaid.setText("\""+_candidates.get(0)+"\"");
            	_imReady.setText(response+" "+getString(R.string.VM_Try_Again));
            	if (_ttsAvailable)
            		_tts.speak(textToSay,TextToSpeech.QUEUE_FLUSH,myHashAlarm);
            	else
            		onUtteranceCompleted(UTTERANCE_ASK_FOR_YES_NO);
            	_successfulParsing = false;
    		}
    		else
    		{
    			int candidateNum = Integer.parseInt(response);
    			_ts = _taskArray.get(candidateNum);
    			
            	// Display the confirmation message.
    			_whatWasSaid.setText("\""+_candidates.get(candidateNum)+"\"");
            	_successfulParsing = true;
            	String text;
            	if (_op==SpeechParser.OP_ADD)
            	{
            		text = getString(R.string.Ready_to_Create)+" \""+_ts.task.title+"\". "+
            			getString(R.string.Should_I_Go_Ahead);
            	}
            	else if (_ts.titleSet && _ts.priorTitle.length()>0)
            	{
            		text = getString(R.string.Ready_to_Update)+" \""+_ts.priorTitle+"\". "+
            			getString(R.string.Should_I_Go_Ahead);
            	}
            	else
            	{
            		text = getString(R.string.Ready_to_Update)+" \""+_ts.task.title+"\". "+
            			getString(R.string.Should_I_Go_Ahead);
            	}
        		_imReady.setText(text);
        		if (_ttsAvailable)
        			_tts.speak(text,TextToSpeech.QUEUE_FLUSH,myHashAlarm);
            	else
            		onUtteranceCompleted(UTTERANCE_ASK_FOR_YES_NO);
            	
        		if (_ts.titleSet)
        		{
        			addFieldAndValue(getString(R.string.Title_),_ts.task.title);
        		}
        		
            	// Start Date/Time:
            	if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true) && _ts.startDateSet)
            	{
             		if (_ts.task.start_date==0)
             			addFieldAndValue(getString(R.string.Start_Date_),getString(R.string.None));
            		else
            		{
            			if (_ts.task.uses_start_time)
            			{
            				addFieldAndValue(getString(R.string.Start_Date_),Util.getDateTimeString(
            					_ts.task.start_date, VoiceCommandConfirm.this));
            			}
            			else
            			{
            				addFieldAndValue(getString(R.string.Start_Date_),Util.getDateString(_ts.task.
            					start_date));
            			}
            		}
            	}
            	
            	// Due Date/Time:
            	if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) && _ts.dueDateSet)
            	{
             		if (_ts.task.due_date==0)
             			addFieldAndValue(getString(R.string.Due_Date_),getString(R.string.None));
            		else
            		{
            			if (_ts.task.uses_due_time)
            			{
            				addFieldAndValue(getString(R.string.Due_Date_),Util.getDateTimeString(
            					_ts.task.due_date, VoiceCommandConfirm.this));
            			}
            			else
            			{
            				addFieldAndValue(getString(R.string.Due_Date_),Util.getDateString(_ts.task.
            					due_date));
            			}
            		}
            	}
            	
            	// Reminder:
            	if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true) && _ts.reminderSet)
            	{
            		if (_ts.task.reminder==0)
            			addFieldAndValue(getString(R.string.Reminder_),getString(R.string.None));
            		else
            		{
            			String value = Util.getDateTimeString(_ts.task.reminder,VoiceCommandConfirm.this);
            			if (_ts.task.nag)
            				value += " ("+getString(R.string.Nagging_On)+") ";
            			addFieldAndValue(getString(R.string.Reminder_),value);
            		}
            	}
            	
            	// Repeat:
            	if (_settings.getBoolean(PrefNames.REPEAT_ENABLED,true) && _ts.repeatSet)
            	{
            		if (_ts.task.repeat==0)
            			addFieldAndValue(getString(R.string.Repeat_),getString(R.string.None));
            		else
            		{
            			String value = "";
            			boolean fromCompletion = false;
            			if (_ts.task.repeat>100)
            			{
            				fromCompletion = true;
            				_ts.task.repeat -= 100;
            			}
            			if (_ts.task.repeat<50)
            			{
            				// Ordinary Repeat
            				String repeatArray[] = VoiceCommandConfirm.this.getResources().getStringArray(
                                R.array.repeat_options);
            				value = repeatArray[_ts.task.repeat];
            			}
            			else if (_ts.task.repeat==50)
            			{
            				// Advanced Repeat:
            				AdvancedRepeat ar = new AdvancedRepeat();
            				if (ar.initFromString(_ts.task.rep_advanced))
            					value = ar.getLocalizedString(VoiceCommandConfirm.this);
            				else
            					value = getString(R.string.None);
            			}
            			if (fromCompletion)
            			{
            				_ts.task.repeat += 100;
            				value += " "+getString(R.string.from_completion_date);
            			}
            			addFieldAndValue(getString(R.string.Repeat_),value);
            		}
            	}
            	
            	// Add to calendar:
            	if (_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true) && _ts.addToCalendar)
            	{
           			addFieldAndValue(getString(R.string.Add_To_Calendar),getString(R.string.Yes));
            	}
            	
            	// Priority:
            	if (_settings.getBoolean(PrefNames.PRIORITY_ENABLED,true) && _ts.prioritySet)
            	{
            		String priorityArray[] = VoiceCommandConfirm.this.getResources().getStringArray(R.array.priorities);
            		addFieldAndValue(getString(R.string.Priority_),priorityArray[_ts.task.priority]);
            	}
            	
            	// Status:
            	if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true) && _ts.statusSet)
            	{
            		String[] statuses = VoiceCommandConfirm.this.getResources().getStringArray(R.array.statuses);
            		addFieldAndValue(getString(R.string.Status_),statuses[_ts.task.status]);
            	}
            	
            	// Folder:
            	if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED,true) && _ts.folderSet)
            	{
            		if (_ts.task.folder_id==0)
            			addFieldAndValue(getString(R.string.Folder_),getString(R.string.None));
            		else
            		{
            			Cursor c = (new FoldersDbAdapter()).getFolder(_ts.task.folder_id);
            			try
            			{
            				if (c.moveToFirst())
            					addFieldAndValue(getString(R.string.Folder_),Util.cString(c, "title"));
            				else
            					addFieldAndValue(getString(R.string.Folder_),getString(R.string.None));
            			}
            			finally { c.close(); }
            		}
            	}

            	// Context:
            	if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED,true) && _ts.contextSet)
            	{
            		if (_ts.task.context_id==0)
            			addFieldAndValue(getString(R.string.Context_),getString(R.string.None));
            		else
            		{
            			Cursor c = (new ContextsDbAdapter()).getContext(_ts.task.context_id);
            			try
            			{
            				if (c.moveToFirst())
            					addFieldAndValue(getString(R.string.Context_),Util.cString(c, "title"));
            				else
            					addFieldAndValue(getString(R.string.Context_),getString(R.string.None));
            			}
            			finally { c.close(); }
            		}
            	}

            	// Tags:
            	if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && _ts.tagSet)
            	{
            		if (_ts.tags.size()==0)
            			addFieldAndValue(getString(R.string.Tags_),getString(R.string.None));
            		else
            		{
            			boolean isFirst = true;
            			String value = "";
            			Iterator<String> it = _ts.tags.iterator();
            			while (it.hasNext())
            			{
            				if (!isFirst)
            					value += ", ";
            				isFirst = false;
            				value += it.next();
            			}
            			addFieldAndValue(getString(R.string.Tags_),value);
            		}
            	}
            	
            	// Goal:
            	if (_settings.getBoolean(PrefNames.GOALS_ENABLED,true) && _ts.goalSet)
            	{
            		if (_ts.task.goal_id==0)
            			addFieldAndValue(getString(R.string.Goal_),getString(R.string.None));
            		else
            		{
            			Cursor c = (new GoalsDbAdapter()).getGoal(_ts.task.goal_id);
            			try
            			{
            				if (c.moveToFirst())
            					addFieldAndValue(getString(R.string.Goal_),Util.cString(c, "title"));
            				else
            					addFieldAndValue(getString(R.string.Goal_),getString(R.string.None));
            			}
            			finally { c.close(); }
            		}
            	}

            	// Location:
            	if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED,true) && _ts.locationSet)
            	{
            		if (_ts.task.location_id==0)
            			addFieldAndValue(getString(R.string.Location_),getString(R.string.None));
            		else
            		{
            			UTLLocation loc = (new LocationsDbAdapter()).getLocation(_ts.task.location_id);
            			if (loc!=null)
            			{
            				String value = loc.title;
            				if (_ts.task.location_reminder && !_ts.task.location_nag)
            					value += " "+getString(R.string.Reminder_Enabled);
            				if (_ts.task.location_reminder && _ts.task.location_nag)
            					value += " "+getString(R.string.Nagging_Reminder_Enabled);
            				addFieldAndValue(getString(R.string.Location_),value);
            			}
            			else
            				addFieldAndValue(getString(R.string.Location_),getString(R.string.None));
            		}
            	}
            	
            	// Timer:
            	if (_settings.getBoolean(PrefNames.TIMER_ENABLED,true) && _ts.timerSet)
            	{
            		if (_ts.willStartTimer)
            			addFieldAndValue(getString(R.string.Timer_),getString(R.string.Start_the_Timer));
            		else
            			addFieldAndValue(getString(R.string.Timer_),getString(R.string.Stop_the_Timer));
            	}

            	// Parent task:
            	if (_settings.getBoolean(PrefNames.SUBTASKS_ENABLED, true) && _ts.parentSet &&
            		_ts.task.parent_id>0)
            	{
            		UTLTask parent = (new TasksDbAdapter()).getTask(_ts.task.parent_id);
            		if (parent!=null)
            		{
            			addFieldAndValue(getString(R.string.Parent_Task_),parent.title);
            		}
            	}
            	
            	// Starred:
            	if (_ts.starSet && _settings.getBoolean(PrefNames.STAR_ENABLED, true))
            	{
            		if (_ts.task.star)
            			addFieldAndValue(getString(R.string.Starred_),getString(R.string.Yes));
            		else
            			addFieldAndValue(getString(R.string.Starred_),getString(R.string.No));
            	}
            	
            	// Completed Status:
            	if (_ts.completedSet)
            	{
            		if (_ts.task.completed)
            			addFieldAndValue(getString(R.string.Completed_),getString(R.string.Yes));
            		else
            			addFieldAndValue(getString(R.string.Completed_),getString(R.string.No));
            	}
            	
            	// Account.  We need to display it with add operations if we have more than one.
            	AccountsDbAdapter adb = new AccountsDbAdapter();
            	if (adb.getNumAccounts()>1 && _op==SpeechParser.OP_ADD)
            	{
            		UTLAccount a = adb.getAccount(_ts.task.account_id);
            		if (a!=null)
            			addFieldAndValue(getString(R.string.Account_),a.name);
            	}
            	
    		}
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

    	if (_speech==null)
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

        final Intent recognizerIntent = new Intent();
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
    
    // This is called after the TTS engine has confirmed that a task has been saved.
	@Override
	public void onUtteranceCompleted(String utteranceID)
	{
		if (utteranceID.equals(UTTERANCE_SUCCESSFUL_SAVE))
		{
			// After the task is successfully saved, return to the voice command prompt, and wait for the user
	    	// to enter another command.
	    	Intent result = new Intent();
			result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_TRIGGER);
			this.setResult(RESULT_OK, result);
			finish();
		}
		if (utteranceID.equals(UTTERANCE_ASK_FOR_YES_NO))
		{
			Handler mainHandler = new Handler(this.getMainLooper());
			Runnable listenRunnable = new Runnable() 
			{
				@Override
				public void run()
				{
					startListeningForYesNo();
				}
			};
			mainHandler.post(listenRunnable);
		}
	}

    // Handler for a "yes" - either due to a button push or by speech.
    private void handleYes()
    {
        FeatureUsage featureUsage = new FeatureUsage(this);

    	if (!_successfulParsing)
    	{
    		// This means the user wants to try again after a failure to parse the speech.
    		Intent result = new Intent();
    		result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_COMMAND);
    		this.setResult(RESULT_OK, result);
    		finish();
    		return;
    	}
    	
    	// If we get here, the speech was successfully parsed.  We can go ahead and save the task.
    	if (_op==SpeechParser.OP_ADD)
    	{
    		_ts.task.td_id = -1;
    		_ts.task.mod_date = System.currentTimeMillis();
    		_ts.task.prev_folder_id = _ts.task.folder_id;
    		
    		// Make sure the parent task has not been deleted in a recent sync:
    		TasksDbAdapter tasksDB = new TasksDbAdapter();
            if (_ts.task.parent_id>0)
            {
            	UTLTask parent = tasksDB.getTask(_ts.task.parent_id);
            	if (parent==null)
            	{
            		log("The task's parent was deleted while the task was being "+
            			"edited. The task will have no parent.");
            		_ts.task.parent_id = 0;
            	}
            }
            
            // Link the task with a calendar entry if needed:
            if (_ts.addToCalendar && (_ts.task.due_date>0 || _ts.task.start_date>0) &&
            	_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            {
            	CalendarInterface ci = new CalendarInterface(this);
            	String uri = ci.linkTaskWithCalendar(_ts.task);
            	if (!uri.startsWith(CalendarInterface.ERROR_INDICATOR))
            	{
            		_ts.task.calEventUri = uri;
            	}
            }
            
            // If the timer has been set, then start it.
            if (_ts.timerSet && _ts.willStartTimer)
            {
            	_ts.task.timer_start_time = System.currentTimeMillis();
            	_ts.task.timer = 0;
            }
            
            // Add the task to the database:
			_ts.task.uuid = UUID.randomUUID().toString();
            long taskID = tasksDB.addTask(_ts.task);
            if (taskID==-1)
            {
                Util.popup(this,R.string.Cannot_add_task);
                return;
            }
            _ts.task._id = taskID;

            // Record feature usage for the task.
            featureUsage.recordForTask(_ts.task);

            // For an add operation, we need to update the calendar event's note
            // to include a link to the newly created task:
            if (_ts.addToCalendar && (_ts.task.due_date>0 || _ts.task.start_date>0) &&
            	_settings.getBoolean(PrefNames.CALENDAR_ENABLED, true))
            {
            	CalendarInterface ci = new CalendarInterface(this);
            	ci.addTaskLinkToEvent(_ts.task);
            }
            
            // Add or update the tags for the task:
            if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && _ts.tags!=null &&
            	_ts.tags.size()>0)
            {
            	TagsDbAdapter tagsDB = new TagsDbAdapter();
            	String[] tagArray = Util.iteratorToStringArray(_ts.tags.iterator(), _ts.tags.size());
            	tagsDB.linkTags(_ts.task._id, tagArray);
            	
            	// Make sure the tags are on the recently used tags list:
            	CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
            	currentTags.addToRecent(tagArray);

                // Record usage of the tags feature.
                if (!_ts.task.completed)
                    featureUsage.record(FeatureUsage.TAGS);
            }
            
         	// If the current time zone is different than the home time zone, the
        	// reminder time needs to be offset when comparing it to the current time.
            TimeZone currentTimeZone = TimeZone.getDefault();
        	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
        		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
        	long reminderTime = _ts.task.reminder;
        	long oldReminderTime = 0;
        	if (!currentTimeZone.equals(defaultTimeZone))
        	{
        		long difference = currentTimeZone.getOffset(System.currentTimeMillis()) - 
        			defaultTimeZone.getOffset(System.currentTimeMillis());
        		reminderTime = _ts.task.reminder - difference;
        	}
        	
        	// If a reminder was set up, then schedule it:
            if (reminderTime>System.currentTimeMillis() && !_ts.task.completed)
            {
            	Util.scheduleReminderNotification(_ts.task);
            }
            
            if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true))
            {
            	// The instant upload feature is turned on.
            	Util.instantTaskUpload(this, _ts.task);
            }
    	}
    	else
    	{
    		// The task has been edited.
    		
    		_ts.task.mod_date = System.currentTimeMillis();
    		
    		// Make sure the parent task has not been deleted in a recent sync:
    		TasksDbAdapter tasksDB = new TasksDbAdapter();
            if (_ts.task.parent_id>0)
            {
            	UTLTask parent = tasksDB.getTask(_ts.task.parent_id);
            	if (parent==null)
            	{
            		log("The task's parent was deleted while the task was being "+
            			"edited. The task will have no parent.");
            		_ts.task.parent_id = 0;
            	}
            }
            
            // If the timer has been set, then start it.
            if (_ts.timerSet)
            {
            	if (_ts.willStartTimer)
            	{
            		_ts.task.timer_start_time = System.currentTimeMillis();
            	}
            	else
            	{
            		// The timer is being stopped:
            		if (_ts.task.timer_start_time>0)
            		{
            			long elapsedTimeMillis = System.currentTimeMillis() - _ts.task.timer_start_time;
            			if (elapsedTimeMillis<0) elapsedTimeMillis=0;
            			_ts.task.timer_start_time = 0;
            			_ts.task.timer += (elapsedTimeMillis/1000);
            		}
            	}
            }
            
            // Modify the task in the database:
            boolean isSuccessful = tasksDB.modifyTask(_ts.task);
            if (!isSuccessful)
            {
                Util.popup(this,R.string.Cannot_modify_task);
                return;
            }

            // Record feature usage for the task.
            featureUsage.recordForTask(_ts.task);

            // Add or update the tags for the task:
            if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && _ts.tags!=null &&
            	_ts.tags.size()>0)
            {
            	TagsDbAdapter tagsDB = new TagsDbAdapter();
            	String[] tagArray = Util.iteratorToStringArray(_ts.tags.iterator(), _ts.tags.size());
            	tagsDB.linkTags(_ts.task._id, tagArray);
            	
            	// Make sure the tags are on the recently used tags list:
            	CurrentTagsDbAdapter currentTags = new CurrentTagsDbAdapter();
            	currentTags.addToRecent(tagArray);

                // Record use of the tags feature.
                if (!_ts.task.completed)
                    featureUsage.record(FeatureUsage.TAGS);
            }
            else if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true) && _ts.tagSet &&
            	_ts.tags!=null && _ts.tags.size()==0)
            {
            	// This occurs if the last tag has been deleted.
            	TagsDbAdapter tagsDB = new TagsDbAdapter();
            	tagsDB.linkTags(_ts.task._id, new String[] { });
            }
            
            if (_ts.completedSet && _ts.task.completed)
            {
            	// The task was just marked as complete.  We need to make additional database updates
            	// as well, such as marking subtasks complete and generating new recurring tasks.
            	Util.markTaskComplete(_ts.task._id);
            }
            
            if (_ts.reminderSet)
            {
	         	// If the current time zone is different than the home time zone, the
	        	// reminder time needs to be offset when comparing it to the current time.
	            TimeZone currentTimeZone = TimeZone.getDefault();
	        	TimeZone defaultTimeZone = TimeZone.getTimeZone(_settings.getString(
	        		PrefNames.HOME_TIME_ZONE, "America/Los_Angeles"));
	        	long reminderTime = _ts.task.reminder;
	        	long oldReminderTime = _ts.priorReminder;
	        	if (!currentTimeZone.equals(defaultTimeZone))
	        	{
	        		long difference = currentTimeZone.getOffset(System.currentTimeMillis()) - 
	        			defaultTimeZone.getOffset(System.currentTimeMillis());
	        		if (reminderTime>0)
	        			reminderTime = _ts.task.reminder - difference;
	        		if (oldReminderTime>0)
	        			oldReminderTime = oldReminderTime - difference;
	        	}
	        	
	        	// If a reminder was moved from the past to the future, then remove any 
	            // notifications that are displaying, and cancel any nagging alarms:
	        	if (reminderTime>System.currentTimeMillis() && oldReminderTime<System.currentTimeMillis())
	        	{
	        		// Cancel any pending notifications (such as those for nagging):
	        		Util.cancelReminderNotification(_ts.task._id);
	        		
	        		// Remove the notification if it is displaying:
	            	Util.removeTaskNotification(_ts.task._id);
	        	}
	        	
	        	// If a reminder was set up, then schedule it:
	            if (reminderTime>System.currentTimeMillis() && !_ts.task.completed)
	            {
	            	Util.scheduleReminderNotification(_ts.task);
	            }
	            
	            if (_ts.task.reminder==0 && _ts.priorReminder>0)
	            {
	            	// We just removed a reminder.  Cancel the notification:
	            	Util.cancelReminderNotification(_ts.task._id);
	            }
	        }
            
            // If this is a Google account and the folder was changed, we also need to change the
            // folder of any subtasks.
            boolean fullSyncStarted = false;
            UTLAccount acct = (new AccountsDbAdapter()).getAccount(_ts.task.account_id);
            if (acct.sync_service==UTLAccount.SYNC_GOOGLE && _ts.folderSet)
            {
            	changeSubtaskFolders(_ts.task);
            	fullSyncStarted = true;
            	Intent i = new Intent(this, Synchronizer.class);
                i.putExtra("command", "full_sync");
                i.putExtra("is_scheduled", true);
				Synchronizer.enqueueWork(this,i);
            }
            
            if (_settings.getBoolean(PrefNames.INSTANT_UPLOAD, true) && !fullSyncStarted)
            {
            	// The instant upload feature is turned on.
            	Util.instantTaskUpload(this, _ts.task);
            }
    	}
    	
        Util.logOneTimeEvent(this, "finish_editing_task_by_voice", 0, new String[] { Integer.
        	valueOf(_op).toString() });

        // Record usage of the voice mode feature.
        featureUsage.record(FeatureUsage.VOICE_MODE);

        // Update any widgets that are on display:
        Util.updateWidgets();

        // Tell the user the task has been saved.
    	HashMap<String, String> myHashAlarm = new HashMap<String, String>();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_SUCCESSFUL_SAVE);
        if (_ttsAvailable)
        	_tts.speak(getString(R.string.Task_Saved), TextToSpeech.QUEUE_FLUSH, myHashAlarm);
        else
        	onUtteranceCompleted(UTTERANCE_SUCCESSFUL_SAVE);
    }    

    // Handler for a "no" - either due to button push or by speech.
    private void handleNo()
    {
    	// Either an error occurred and the user does not want to try again, or an error did not occur
    	// and the user is canceling task creation.  In either case, we go back and listen for the
    	// trigger phrase.
    	Intent result = new Intent();
		result.putExtra("response",VoiceCommand.RESPONSE_LISTEN_FOR_TRIGGER);
		this.setResult(RESULT_OK, result);
		finish();
    }
    
    // Change the folders of child tasks to match the parent task:
    private void changeSubtaskFolders(UTLTask parent)
    {
    	TasksDbAdapter tasksDB = new TasksDbAdapter();
    	Cursor c = tasksDB.queryTasks("parent_id="+parent._id, null);
    	c.moveToPosition(-1);
    	while (c.moveToNext())
    	{
    		UTLTask child = tasksDB.getUTLTask(c);
    		child.folder_id = parent.folder_id;
    		child.mod_date = System.currentTimeMillis();
    		tasksDB.modifyTask(child);
    		changeSubtaskFolders(child);
    	}
    	c.close();
    }

    @Override
    public void onDestroy()
    {
    	if (_tts!=null)
    		_tts.shutdown();

		if (_speech!=null)
		{
			_speech.destroy();
			Util.log("VoiceCommandConfirm: Recognizer Destroyed.");
		}

    	super.onDestroy();
    }
}
