package com.customsolutions.android.utl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/** This class is responsible for listening to the user's voice command, processing it, and sending 
 * the results to a separate Activity to display a confirmation to the user.
 * @author Nicholson
 */
public class VoiceCommand extends VoiceActivity implements OnUtteranceCompletedListener,
	RecognitionListener
{
	private static final String TAG = "VoiceCommand";

	// UTTERANCE IDs, passed to onUtteranceCompleted:
	private static final String UTTERANCE_EXIT_CONFIRM = "exit_confirm";
	private static final String UTTERANCE_CANCEL_CONFIRM = "cancel_confirm";

	// Codes to track responses to activities being called.
	public static final int VOICE_COMMAND_CONFIRM = 1;
	public static final int RESPONSE_LISTEN_FOR_TRIGGER = 1;
	public static final int RESPONSE_LISTEN_FOR_COMMAND = 2;

	// Views we need to keep track of:
	private ImageButton _micButton;
	private ImageView _mic;
	private ImageView _ring1;
	private ImageView _ring2;
	private ImageView _ring3;
	private TextView _intro;
	private ViewPager _pager;
	private ImageView _bluetoothIndicator;

	/** The SpeechRecognizer object, used for recognition. */
	private SpeechRecognizer _speech;

	/** The number of rings around the microphone to display as the user is speaking.  This will be
	 * updated continuously. */
	private int _numRings;

	private Handler _handler;
	private Timer _reductionTimer;
	private SharedPreferences _settings;

	private boolean _isSoundMuted;

	private PagerAdapter _pagerAdapter;

	private boolean _isHandlingResult;
	private boolean _activityIsStarting;

	/** This holds the Text-To-Speech functionality */
	private TextToSpeech _tts;

	/** Is text to speech available? */
	private boolean _ttsAvailable;

	/** Used for in-app billing and licensing status: */
	private PurchaseManager _pm;

	/** Flag indicating if the onReadyForSpeech() callback has been called. This is used to work
	 * around Google bugs. */
	private boolean _onReadyForSpeechCalled = false;

	/** The start time of speech recognition.  Used to work around Google bugs. */
	private long _recognitionStartTime;

	/** Flag indicating if this Activity should close after the user has given a command. */
	private boolean _closeWhenDone;

	/** Flag indicating if we're currently asking for microphone permission. */
	private boolean _waitingOnPermission;

	/** A Runnable that is executed to listen for a command after Bluetooth device testing is
	 *  done. */
	private Runnable _listenForCommandRunnable;

	/** This works around a new Google bug that started around March 2020. See:
	 * https://issuetracker.google.com/issues/152628934 */
	private boolean _onResultsCalled;

	/** Keeps track of whether the notification volume was muted when the Activity started. */
	private boolean _notifVolumeMuted;

	/** Keeps track of the volume level for notifications when the Activity started. */
	private int _notifVolume;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		_tag = "VoiceCommand";
		super.onCreate(savedInstanceState);
		Util.log("VoiceCommand: starting Activity.");

		AccountsDbAdapter accountsDB = new AccountsDbAdapter();
		Cursor c = accountsDB.getAllAccounts();
		if (!c.moveToFirst())
		{
			// The user has managed to get here without setting up an account first.
			c.close();
			Intent i = new Intent(this,main.class);
			startActivity(i);
			finish();
			return;
		}
		c.close();

		setContentView(R.layout.voice_command);

		if (getSupportActionBar()!=null)
			getSupportActionBar().setTitle(R.string.utl_voice_mode);

		// Allows the user to control TTS volume using volume keys:
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// Determine if we need to close this Activity after the user says a command.
		_closeWhenDone = false;
		if (getIntent()!=null)
		{
			Bundle extras = getIntent().getExtras();
			if (extras!=null && extras.containsKey("close_when_done") && extras.getBoolean(
				"close_when_done"))
			{
				_closeWhenDone = true;
			}
		}

		// Link to key views:
		_micButton = (ImageButton)findViewById(R.id.voice_comm_mic_button);
		_mic = (ImageView)findViewById(R.id.voice_comm_mic);
		_ring1 = (ImageView)findViewById(R.id.voice_comm_mic_ring1);
		_ring2 = (ImageView)findViewById(R.id.voice_comm_mic_ring2);
		_ring3 = (ImageView)findViewById(R.id.voice_comm_mic_ring3);
		_intro = (TextView)findViewById(R.id.voice_comm_intro);
		_pager = (ViewPager)findViewById(R.id.voice_comm_pager);
		_bluetoothIndicator = (ImageView)findViewById(R.id.voice_comm_bluetooth);
		_bluetoothIndicator.setImageResource(android.R.color.transparent);

		_ring1.setVisibility(View.GONE);
		_ring2.setVisibility(View.GONE);
		_ring3.setVisibility(View.GONE);
		_mic.setVisibility(View.GONE);

		// Adjust the mic button image for the dark theme, if necessary:
		_settings = this.getSharedPreferences(Util.PREFS_NAME, 0);

		_handler = new Handler();
		_isSoundMuted = false;
		_isHandlingResult = false;
		_pm = new PurchaseManager(this);
		_waitingOnPermission = false;
		_onResultsCalled = false;
		_notifVolumeMuted = isMuted(AudioManager.STREAM_NOTIFICATION);
		_notifVolume = _audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);

		if (!SpeechRecognizer.isRecognitionAvailable(this))
		{
			Util.popup(this, R.string.No_Speech_Recognition);
			finish();
			return;
		}

		// Create the speech recognizer:
		resetRecognizer();

		// If the user has chosen to keep the screen on, then keep the screen on:
		if (_settings.getBoolean(PrefNames.VM_KEEP_SCREEN_ON, true))
		{
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else
		{
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		// Create the ViewPager for use in displaying help information:
		_pagerAdapter = new HelpPagerAdapter(getSupportFragmentManager());
		_pager.setAdapter(_pagerAdapter);
		findViewById(R.id.voice_comm_tab_create_underline).setVisibility(View.VISIBLE);
		findViewById(R.id.voice_comm_tab_modify_underline).setVisibility(View.GONE);
		findViewById(R.id.voice_comm_tab_read_underline).setVisibility(View.GONE);
		findViewById(R.id.voice_comm_tab_other_underline).setVisibility(View.GONE);

		// Create a Text To Speech object so the feedback can be spoken.
		_ttsAvailable = false;
		_tts = new TextToSpeech(this, new TextToSpeech.OnInitListener()
		{
			@SuppressLint("NewApi")
			@Override
			public void onInit(int status)
			{
				if (status==TextToSpeech.SUCCESS)
				{
					_tts.setLanguage(SpeechParser.getLocale());
					_tts.setOnUtteranceCompletedListener(VoiceCommand.this);
					_ttsAvailable = true;
				}
				else
				{
					Util.popup(VoiceCommand.this, R.string.Not_Allowing_TTS);
					_ttsAvailable = false;
				}
			}
		});

		// On small devices, we lock the orientation in portrait mode.  On larger devices, we lock
		// the orientation at the starting orientation:
		float diagonalScreenSize = _settings.getFloat(PrefNames.DIAGONAL_SCREEN_SIZE, 5.0f);
		if (diagonalScreenSize<6.1)
		{
			// Lock in portrait orientation:
			try
			{
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			}
			catch (IllegalStateException e)
			{
				// A bug in Android 8.0.
			}
		}
		else
		{
			// Lock in the current orientation:
			try
			{
				lockScreenOrientation();
			}
			catch (IllegalStateException e)
			{
				// A bug in Android 8.0.
			}
		}

		Util.logOneTimeEvent(this, "enter_voice_mode", 0, null);

		_micButton.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (_state==STATE_LISTENING_FOR_TRIGGER)
				{
					resetRecognizer();
					startListeningForCommand(false);
				}
			}
		});

		findViewById(R.id.voice_comm_tab_create_txt).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_pager.setCurrentItem(0);
			}
		});

		findViewById(R.id.voice_comm_tab_modify_txt).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_pager.setCurrentItem(1);
			}
		});

		findViewById(R.id.voice_comm_tab_read_txt).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_pager.setCurrentItem(2);
			}
		});

		findViewById(R.id.voice_comm_tab_other_txt).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				_pager.setCurrentItem(3);
			}
		});

		_pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener()
		{
			@Override
			public void onPageScrollStateChanged(int arg0)
			{}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2)
			{}

			@Override
			public void onPageSelected(int position)
			{
				findViewById(R.id.voice_comm_tab_create_underline).setVisibility(View.GONE);
				findViewById(R.id.voice_comm_tab_modify_underline).setVisibility(View.GONE);
				findViewById(R.id.voice_comm_tab_read_underline).setVisibility(View.GONE);
				findViewById(R.id.voice_comm_tab_other_underline).setVisibility(View.GONE);
				switch (position)
				{
					case 0:
						findViewById(R.id.voice_comm_tab_create_underline).setVisibility(View.VISIBLE);
						break;

					case 1:
						findViewById(R.id.voice_comm_tab_modify_underline).setVisibility(View.VISIBLE);
						break;

					case 2:
						findViewById(R.id.voice_comm_tab_read_underline).setVisibility(View.VISIBLE);
						break;

					case 3:
						findViewById(R.id.voice_comm_tab_other_underline).setVisibility(View.VISIBLE);
						break;
				}
			}
		});
	}

	/** Get the current mute state of an audio stream, taking into account API 21-22 limits. */
	private boolean isMuted(int stream)
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)
		{
			return _audioManager.isStreamMute(stream);
		}
		else
		{
			try
			{
				Method m = AudioManager.class.getMethod("isStreamMute", int.class);
				m.setAccessible(true);
				return (Boolean) m.invoke(_audioManager, AudioManager.STREAM_MUSIC);
			}
			catch (Exception e)
			{
				Log.e(TAG,"Can't get mute state","Can't get mute state.",e);
				return false;
			}
		}
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);

		if (!SpeechParser.isLanguageSupported() && !_settings.getBoolean(PrefNames.VM_LANGUAGE_OVERRIDE,
			false))
		{
			// Display a dialog asking the user if he wants to continue in English.
			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.
				OnClickListener()
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					switch (which)
					{
						case DialogInterface.BUTTON_POSITIVE:
							// Yes tapped:
							Util.updatePref(PrefNames.VM_LANGUAGE_OVERRIDE, true, VoiceCommand.this);
							break;
						case DialogInterface.BUTTON_NEGATIVE:
							// No clicked:
							VoiceCommand.this.finish();
							break;
					}
				}
			};
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.VM_Not_Supported);
			builder.setPositiveButton(Util.getString(R.string.Yes), dialogClickListener);
			builder.setNegativeButton(Util.getString(R.string.No), dialogClickListener);
			builder.show();
		}

		// Also enforce the beta, if applicable.
		if (_pm.enforceBeta(this))
			return;

		if (isPermissionGranted(Manifest.permission.RECORD_AUDIO))
		{
			// Depending on the user's setting, we start listening for the trigger phrase or the
			// command:
			if (!_settings.getBoolean(PrefNames.VM_LISTEN_IMMEDIATELY, false) && !_closeWhenDone)
				startListeningForTrigger();
			else
			{
				// Before we can start listening for a command, we need to make sure that we're
				// not busy testing Bluetooth devices for voice recognition support. Otherwise,
				// we may not hear the speech recognition beep.
				if (_btDevicesBeingTested)
					log("Waiting for bluetooth device testing to finish.");
				_listenForCommandRunnable= new Runnable()
				{
					@Override
					public void run()
					{
						if (!_btDevicesBeingTested)
							startListeningForCommand(false);
						else
							new Handler().postDelayed(_listenForCommandRunnable,100);
					}
				};
				new Handler().postDelayed(_listenForCommandRunnable,100);
			}
			_activityIsStarting = true;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent)
	{
		super.onActivityResult(requestCode, resultCode, intent);

		// Get extras in the response, if any:
		Bundle extras = new Bundle();
		if (intent != null)
		{
			extras = intent.getExtras();
		}

		switch(requestCode)
		{
			case VOICE_COMMAND_CONFIRM:
				_isHandlingResult = true;
				if (_closeWhenDone)
				{
					if (extras!=null && extras.containsKey("response") && extras.getInt("response")
						==RESPONSE_LISTEN_FOR_COMMAND)
					{
						// Normally the Activity would close in this case, but this response
						// means that the user wants to try again after an error.
						startListeningForCommand(false);
						return;
					}

					// The user doesn't want to enter another command, so finish.
					finish();
				}
				else if (resultCode== Activity.RESULT_CANCELED)
				{
					// The user was at the confirmation screen and tapped on the back button.
					// In this case, start listening for the trigger again.
					startListeningForTrigger();
				}
				else
				{
					// We get here if the user tapped on the "yes" or "no" button.  The action to take
					// will be passed back in the Intent extras.
					if (extras!=null && extras.containsKey("response"))
					{
						switch (extras.getInt("response"))
						{
							case RESPONSE_LISTEN_FOR_TRIGGER:
								startListeningForTrigger();
								break;

							case RESPONSE_LISTEN_FOR_COMMAND:
								startListeningForCommand(false);
								break;
						}
					}
					else
					{
						// Just listen for the trigger by default.
						startListeningForTrigger();
					}
				}
				break;
		}
	}

	@Override
	public void onResume()
	{
		Util.log("VoiceCommand: onResume() called.");
		super.onResume();

		if (_waitingOnPermission)
		{
			// Can't do anything yet.
			return;
		}

		if (!isPermissionGranted(Manifest.permission.RECORD_AUDIO) && !_waitingOnPermission)
		{
			_waitingOnPermission = true;
			requestPermissions(
				new String[]{Manifest.permission.RECORD_AUDIO},
				new Runnable()
				{
					@Override
					public void run()
					{
						_waitingOnPermission = false;
					}
				},
				true,
				null,
				new Runnable()
				{
					@Override
					public void run()
					{
						finish();
					}
				}
			);
			return;
		}

		if (!_isHandlingResult && !_activityIsStarting)
		{
			// Listen for the trigger phrase when we resume from being placed in the background.
			resetRecognizer();
			startListeningForTrigger();
		}
		_isHandlingResult = false;
		_activityIsStarting = false;
	}

	@Override
	protected void onBluetoothSpeechRecognitionReady()
	{
		_bluetoothIndicator.setImageResource(Util.resourceIdFromAttr(
			VoiceCommand.this,R.attr.bluetooth));
	}

	@Override
	public void onReadyForSpeech(Bundle params)
	{
		_onReadyForSpeechCalled = true;
		if (_state==STATE_LISTENING_FOR_COMMAND)
		{
			startBluetoothSpeechRecognition();
			log("onReadyForSpeech() called while listening for command.");
			_intro.setText(R.string.Im_Listening);
		}
		else
		{
			log("onReadyForSpeech() called while listening for trigger.");
			String introMessage = getString(R.string.voice_command_intro);
			introMessage = introMessage.replace("manage my list", _settings.getString(PrefNames.
				VM_TRIGGER_PHRASE, ""));
			_intro.setText(introMessage);
		}
	}

	@Override
	public void onBeginningOfSpeech()
	{
		Util.log("VoiceCommand: onBeginningOfSpeech. State: "+_state);
		_onResultsCalled = false;
	}

	@Override
	public void onError(int error)
	{
		switch (error)
		{
			case SpeechRecognizer.ERROR_CLIENT:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got ERROR_CLIENT while listening for trigger.");
				else
					log("Got ERROR_CLIENT while listening for command.");
				_intro.setText(R.string.Audio_Recording_Error);
				stopBluetoothSpeechRecognition(null);
				break;

			case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got ERROR_INSUFFICIENT_PERMISSIONS while listening for trigger.");
				else
					log("Got ERROR_INSUFFICIENT_PERMISSIONS while listening for command.");
				_intro.setText(R.string.Insufficient_Permissions);
				stopBluetoothSpeechRecognition(null);
				break;

			case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got ERROR_RECOGNIZER_BUSY while listening for trigger.");
				else
					log("Got ERROR_RECOGNIZER_BUSY while listening for command.");
				_intro.setText(R.string.Waiting_on_Speech_Recognizer);
				resetRecognizer();
				_handler.postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						if (_state==STATE_LISTENING_FOR_TRIGGER)
							startListeningForTrigger();
						else
							startListeningForCommand(true);
					}
				},250);
				break;

			case SpeechRecognizer.ERROR_AUDIO:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got ERROR_AUDIO while listening for trigger.");
				else
					log("Got ERROR_AUDIO while listening for command.");
				_intro.setText(R.string.Audio_Recording_Error);
				stopBluetoothSpeechRecognition(null);
				break;

			case SpeechRecognizer.ERROR_NETWORK:
			case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got network error while listening for trigger.");
				else
					log("Got network error while listening for command.");
				_intro.setText(R.string.No_Internet_Connection);
				new Handler().postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						if (_state==STATE_LISTENING_FOR_TRIGGER)
							startListeningForTrigger();
						else
							startListeningForCommand(true);
					}
				},250);
				break;

			case SpeechRecognizer.ERROR_SERVER:
				if (_state==STATE_LISTENING_FOR_TRIGGER)
					log("Got ERROR_SERVER while listening for trigger.");
				else
					log("Got ERROR_SERVER while listening for command.");
				_intro.setText(R.string.Server_Error);
				new Handler().postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						if (_state==STATE_LISTENING_FOR_TRIGGER)
							startListeningForTrigger();
						else
							startListeningForCommand(true);
					}
				},250);
				break;

			case SpeechRecognizer.ERROR_NO_MATCH:
				if (!_onReadyForSpeechCalled && _state==STATE_LISTENING_FOR_COMMAND)
				{
					// A glitch on Google's side we can ignore.
					log("Ignoring ERROR_NO_MATCH");
					return;
				}
				if (_state==STATE_LISTENING_FOR_TRIGGER)
				{
					// Just start listening again.  This is normal.
					log("Restarting due to ERROR_NO_MATCH.");
					startListeningForTrigger();
				}
				else if ((System.currentTimeMillis()-_recognitionStartTime)<5000)
				{
					// Android sometimes calls this too early, before the user has had a genuine
					// chance to say anything. When this happens, start listening again.
					log("ERROR_NO_MATCH called too soon. Restarting recognition.");
					startListeningForCommand(true);
				}
				else
				{
					// Treat this like a speech timeout.
					log("Got ERROR_NO_MATCH while waiting for a command.");
					startListeningForTrigger();
				}
				break;

			case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
				if (_state==STATE_LISTENING_FOR_COMMAND)
					log("Timed out waiting for a spoken command.");
				else
					log("Timed out. Still listening for trigger.");
				_handler.postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						startListeningForTrigger();
					}
				},100);
				break;
		}
	}

	@Override
	public void onBufferReceived(byte[] buffer)
	{
	}

	@Override
	public void onRmsChanged(float rmsdB)
	{
	}

	@Override
	public void onEvent(int eventType, Bundle params)
	{
		log("Got event "+eventType);
	}

	@Override
	public void onPartialResults(Bundle partialResults)
	{
		if (_state==STATE_LISTENING_FOR_COMMAND)
			updateRings(1);
	}

	@Override
	public void onEndOfSpeech()
	{
		Util.log("VoiceCommand: onEndOfSpeech. State: "+_state);
		if (_state==STATE_LISTENING_FOR_COMMAND)
		{
			stopBluetoothSpeechRecognition(null);
			unmuteSound();
		}
	}

	@Override
	public void onResults(Bundle results)
	{
		ArrayList<String> candidates = results.getStringArrayList(SpeechRecognizer.
			RESULTS_RECOGNITION);
		log("Spoken Text Candidates: "+ TextUtils.join("; ",candidates));

		if (_onResultsCalled)
		{
			Log.d(_tag,"Blocking extra call to onResults().");
			return;
		}
		_onResultsCalled = true;

		if (_state==STATE_LISTENING_FOR_TRIGGER)
		{
			// Check to see if the received speech is the trigger phrase.
			String triggerPhrase = _settings.getString(PrefNames.VM_TRIGGER_PHRASE, "");
			for (int i=0; i<candidates.size(); i++)
			{
				if (candidates.get(i).toLowerCase().equals(triggerPhrase.toLowerCase()))
				{
					stopBluetoothSpeechRecognition(new Runnable()
					{
						@Override
						public void run()
						{
							startListeningForCommand(false);
						}
					});
					return;
				}
			}

			// If we get here, the trigger phrase was not said.  Start listening again.
			startListeningForTrigger();
		}
		else
		{
			stopBluetoothSpeechRecognition(null);
			_reductionTimer.cancel();

			_ring1.setVisibility(View.GONE);
			_ring2.setVisibility(View.GONE);
			_ring3.setVisibility(View.GONE);

			// Check to see if any of the candidates are the cancel string:
			Iterator<String> it = candidates.iterator();
			while (it.hasNext())
			{
				String text = it.next();
				if (text.toLowerCase().equals(this.getString(R.string.VM_Exit).
					toLowerCase()))
				{
					speak(this.getString(R.string.VM_Exiting),UTTERANCE_EXIT_CONFIRM);
					return;
				}

				if (text.toLowerCase().equals(this.getString(R.string.VM_Cancel).
					toLowerCase()))
				{
					// User is canceling current command.
					speak(this.getString(R.string.VM_Cancelled),UTTERANCE_CANCEL_CONFIRM);
					return;
				}
			}

			// Check to see what operation is being performed (add, edit, or read):
			SpeechParser sp = SpeechParser.getSpeechParser(this);
			int operation = sp.getOperationType(candidates.get(0));

			log("Launching confirmation activity.");
			if (operation==SpeechParser.OP_READ)
			{
				Intent i = new Intent(this,VoiceCommandRead.class);
				Bundle b = new Bundle();
				b.putStringArrayList("speech_output", candidates);
				if (_speechRecBtDevice!=null)
					b.putParcelable(BluetoothDevice.EXTRA_DEVICE,_speechRecBtDevice);
				i.putExtras(b);
				startActivityForResult(i,VOICE_COMMAND_CONFIRM);
				return;
			}

			Intent i = new Intent(this,VoiceCommandConfirm.class);
			Bundle b = new Bundle();
			b.putStringArrayList("speech_output", candidates);
			if (_speechRecBtDevice!=null)
				b.putParcelable(BluetoothDevice.EXTRA_DEVICE,_speechRecBtDevice);
			i.putExtras(b);
			startActivityForResult(i,VOICE_COMMAND_CONFIRM);
		}
	}

	/** Destroy the speech recognizer if it has been created, and then create a new one. */
	public void resetRecognizer()
	{
		if (_speech!=null)
		{
			log("Destroying and recreating speech recognizer.");
			_speech.destroy();
		}
		_speech = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
		_speech.setRecognitionListener(this);
	}

	@Override
	public void onUtteranceCompleted(String utteranceID)
	{
		if (utteranceID.equals(UTTERANCE_EXIT_CONFIRM))
		{
			finish();
		}
		else if (utteranceID.equals(UTTERANCE_CANCEL_CONFIRM))
		{
			if (_closeWhenDone)
				finish();
			else
			{
				_handler.post(new Runnable()
				{
					@Override
					public void run()
					{
						startListeningForTrigger();
					}
				});
			}
		}
	}

	/** Mute the sound from Google's speech recognition. */
	private void muteSound()
	{
		if (!_isSoundMuted)
		{
			Util.log("VoiceCommand: Muting Sound.");
			try
			{
				_audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);

				// 7/8/2020: I discovered that Google decided to make more unannounced changes and
				// is now using the Ring volume instead of the music volume. So, both must be muted
				// to be safe.
				_audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION,true);
			}
			catch (Exception e)
			{
				// Might get an exception about not being able to change do not disturb state.
				Log.d(TAG,"Can't mute.",e);
			}

			_isSoundMuted = true;
		}
	}

	/** Unmute the speech recognition beeps. Call this when they must be heard. */
	private void unmuteSound()
	{
		if (_isSoundMuted)
		{
			Util.log("VoiceCommand: Unmuting Sound.");
			try
			{
				_audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
				_audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION,false);
				int maxVolume = _audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
				_audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION,maxVolume,0);
			}
			catch (Exception e)
			{
				Log.d(TAG,"Can't unmute sound",e);
			}
			_isSoundMuted = false;
		}
	}

	/** Restore the sound settings to their value prior to calling this Activity. */
	private void restoreSound()
	{
		Util.log("VoiceCommand: Restoring Sound.");
		try
		{
			_audioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
			_audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION,_notifVolumeMuted);
			_audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION,_notifVolume,0);
		}
		catch (Exception e)
		{
			Log.d(TAG,"Can't restore sound.");
		}
		_isSoundMuted = false;
	}

	/** Start listening for the trigger phrase. */
	private void startListeningForTrigger()
	{
		Util.log("VoiceCommand: Calling startListeningForTrigger()");
		_state = STATE_LISTENING_FOR_TRIGGER;
		muteSound();

		// Enable speech recognition through the bluetooth headset, if one is connected.
		startBluetoothSpeechRecognition();

		final Intent recognizerIntent = new Intent();
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.
			LANGUAGE_MODEL_FREE_FORM);
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechParser.getLocale().toString());
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SpeechParser.getLocale().toString());
		recognizerIntent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{});
		_onReadyForSpeechCalled = false;
		_recognitionStartTime = System.currentTimeMillis();
		_speech.startListening(recognizerIntent);

		// Show the button and hide the mic without a button and hide the rings:
		_micButton.setVisibility(View.VISIBLE);
		_mic.setVisibility(View.GONE);
		_ring1.setVisibility(View.GONE);
		_ring2.setVisibility(View.GONE);
		_ring3.setVisibility(View.GONE);
		Util.log("VoiceCommand: Completed startListeningForTrigger()");
	}

	/** Start listening for a command. */
	private void startListeningForCommand(boolean muteAudio)
	{
		Util.log("VoiceCommand: Calling startListeningForCommand()");
		_state = STATE_LISTENING_FOR_COMMAND;

		if (muteAudio)
			muteSound();
		else
			unmuteSound();

		// Start listening:
		final Intent recognizerIntent = new Intent();
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.
			LANGUAGE_MODEL_FREE_FORM);
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechParser.getLocale().toString());
		recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SpeechParser.getLocale().toString());
		recognizerIntent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{});
		_onReadyForSpeechCalled = false;
		_recognitionStartTime = System.currentTimeMillis();
		_speech.startListening(recognizerIntent);

		// Hide the button and show the microphone without a button.
		_micButton.setVisibility(View.GONE);
		_mic.setVisibility(View.VISIBLE);
		_ring1.setVisibility(View.GONE);
		_ring2.setVisibility(View.GONE);
		_ring3.setVisibility(View.GONE);

		// No rings are displayed around the microphone until the user starts speaking.
		_numRings = 0;

		// A regular timer task reduces the number of rings around the microphone that are displayed:
		_reductionTimer = new Timer();
		_reductionTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run()
			{
				_handler.post(new Runnable() {
					@Override
					public void run()
					{
						updateRings(-1);
					}
				});
			}
		}, 0, 150);

		Util.log("VoiceCommand: Completed startListeningForCommand()");
	}

	/** Update the display of rings.  The input is either -1 or 1 */
	private void updateRings(int increment)
	{
		if (increment==1)
		{
			switch (_numRings)
			{
				case 0:
					_ring1.setVisibility(View.VISIBLE);
					break;

				case 1:
					_ring2.setVisibility(View.VISIBLE);
					break;

				case 2:
					_ring3.setVisibility(View.VISIBLE);
			}
			_numRings += increment;
			if (_numRings>3)
				_numRings = 3;
		}
		else
		{
			switch(_numRings)
			{
				case 3:
					_ring3.setVisibility(View.GONE);
					break;

				case 2:
					_ring2.setVisibility(View.GONE);
					break;

				case 1:
					_ring1.setVisibility(View.GONE);
					break;
			}
			_numRings += increment;
			if (_numRings<0)
				_numRings = 0;
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
	public void onPause()
	{
		Util.log("VoiceCommand: onPause() called.");
		unmuteSound();
		super.onPause();
	}

	@Override
	public void onDestroy()
	{
		if (_tts!=null)
		{
			Util.log("VoiceCommand: shutting down TTS...");
			_tts.shutdown();
		}

		if (_speech!=null)
		{
			Util.log("VoiceCommand: destroying SpeechRecognizer...");
			try
			{
				_speech.destroy();
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		restoreSound();
		super.onDestroy();
	}

	/** This adapter represents the various help screens that can be displayed. */
	private class HelpPagerAdapter extends FragmentStatePagerAdapter
	{
		public HelpPagerAdapter(FragmentManager fm)
		{
			super(fm);
		}

		@Override
		public Fragment getItem(int position)
		{
			Fragment f;
			Bundle b;
			switch (position)
			{
				case 0:
					f = new VoiceCommandHelpFragment();
					b = new Bundle();
					b.putInt("string", R.string.task_creation_help);
					f.setArguments(b);
					return f;

				case 1:
					f = new VoiceCommandHelpFragment();
					b = new Bundle();
					b.putInt("string", R.string.task_modify_help);
					f.setArguments(b);
					return f;

				case 2:
					f = new VoiceCommandHelpFragment();
					b = new Bundle();
					b.putInt("string", R.string.reading_tasks_help);
					f.setArguments(b);
					return f;

				default:
					f = new VoiceCommandHelpFragment();
					b = new Bundle();
					b.putInt("string", R.string.other_help_info);
					f.setArguments(b);
					return f;
			}
		}

		@Override
		public int getCount()
		{
			return 4;
		}
	}
}
