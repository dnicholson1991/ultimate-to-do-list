package com.customsolutions.android.utl;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import java.util.Set;

import androidx.core.content.ContextCompat;

/**
 * The base class for any Activity classes that use voice input. This includes code that is used
 * by all of them.
 */

public class VoiceActivity extends UtlPopupActivity
{
    private static final String TAG = "VoiceActivity";

    // Codes which keep track of whether we're listening for the trigger, or for a command:
    protected static final int STATE_NONE = 0;
    protected static final int STATE_LISTENING_FOR_TRIGGER = 1;
    protected static final int STATE_LISTENING_FOR_COMMAND = 2;

    /** The current state.  One of the "STATE" constants here. */
    protected int _state = STATE_NONE;

    /** Used for muting the sound while we're listening for the trigger phrase. */
    protected AudioManager _audioManager;

    /** Provides access to bluetooth functions. */
    protected BluetoothAdapter _btAdapter;

    /** True if bluetooth speech recognition is currently occurring. */
    protected boolean _btSpeechRecInUse = false;

    /** Used for controlling bluetooth headsets. */
    protected BluetoothHeadset _btHeadsetService;

    /** Will be non-null if a bluetooth device is available for speech recognition. */
    protected BluetoothDevice _speechRecBtDevice = null;

    /** A Runnable to execute after Bluetooth speech recognition has stopped. */
    protected Runnable _btSpeechRecStopRunnable = null;

    /** This listens for changes in the status of voice recognition over bluetooth. */
    protected BroadcastReceiver _audioStateReceiver;

    /** The tag to use for logging. */
    protected String _tag = "VoiceActivity";

    /** A flag indicating if Bluetooth devices are currently being tested for speech
     * recognition support. */
    protected boolean _btDevicesBeingTested;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        _audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Bluetooth Setup:
        _btDevicesBeingTested = false;
        SharedPreferences prefs = getSharedPreferences(Util.PREFS_NAME,0);
        _btAdapter = null;
        if (prefs.getBoolean(PrefNames.VM_USE_BLUETOOTH,false))
        {
            // From API 31, the user must have granted the BLUETOOTH_CONNECT permission.
            if (Build.VERSION.SDK_INT>=31)
            {
                int btPermissionCheck = ContextCompat.checkSelfPermission(this, Manifest.
                    permission.BLUETOOTH_CONNECT);
                if (btPermissionCheck==PackageManager.PERMISSION_GRANTED)
                {
                    log("User wants to use bluetooth mic and permission is granted.");
                    _btAdapter = BluetoothAdapter.getDefaultAdapter();
                }
                else
                {
                    log("User wants to use bluetooth mic and permission is not granted.");
                    prefs.edit().putBoolean(PrefNames.VM_USE_BLUETOOTH, false).apply();
                }
            }
            else
            {
                log("User wants to use bluetooth mic and permission is not needed.");
                _btAdapter = BluetoothAdapter.getDefaultAdapter();
            }
        }

        BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener()
        {
            public void onServiceConnected(int profile, BluetoothProfile proxy)
            {
                if (profile == BluetoothProfile.HEADSET)
                {
                    log("Bluetooth Service Connected.");
                    _btHeadsetService = (BluetoothHeadset)proxy;

                    // Check to see if an existing Bluetooth device for speech recognition was
                    // passed in.
                    if (getIntent()!=null)
                    {
                        Bundle extras = getIntent().getExtras();
                        if (extras!=null && extras.containsKey(BluetoothDevice.EXTRA_DEVICE))
                        {
                            _speechRecBtDevice = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                            if (_speechRecBtDevice!=null)
                            {
                                log("Using passed-in Bluetooth device: " + _speechRecBtDevice.
                                    getName());
                                return;
                            }
                        }
                    }

                    // Check the available bluetooth devices to see if any support speech
                    // recognition.
                    Set<BluetoothDevice> btDevices = _btAdapter.getBondedDevices();
                    for (BluetoothDevice deviceToTry : btDevices)
                    {
                        try
                        {
                            Log.v(TAG,"Testing device for speech recognition: "+deviceToTry.
                                getName());
                            if (_btHeadsetService.startVoiceRecognition(deviceToTry))
                            {
                                // This devices supports speech recognition, so we'll use it.
                                _speechRecBtDevice = deviceToTry;
                                onBluetoothSpeechRecognitionReady();
                                log("Using this BT device for speech recognition: "+_speechRecBtDevice.
                                    getName());

                                if (_state==STATE_NONE)
                                {
                                    // Stop speech recognition for now. We'll start it when we are
                                    // actually listening.
                                    log("Not starting BT speech recognition yet since we're not "+
                                        "listening.");
                                    _btHeadsetService.stopVoiceRecognition(_speechRecBtDevice);
                                    _btSpeechRecInUse = false;
                                }
                                break;
                            }
                        }
                        catch (Exception e)
                        {
                            // A small number of devices throw an exception here regarding
                            // the BLUETOOTH_ADMIN permission.  If that happens, then don't
                            // try to use the device.
                        }
                    }
                    _btDevicesBeingTested = false;
                }
            }
            public void onServiceDisconnected(int profile)
            {
                if (profile == BluetoothProfile.HEADSET)
                {
                    log("Bluetooth Service Disconnected.");
                    _btHeadsetService = null;
                    _speechRecBtDevice = null;
                    _btSpeechRecInUse = false;
                    _btDevicesBeingTested = false;
                }
            }
        };
        if (_btAdapter!=null)
        {
            _btDevicesBeingTested = true;
            _btAdapter.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);
        }

        // Listen for changes in the status of voice recognition:
        _audioStateReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context context, Intent intent)
            {
                int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1);
                int prevState = intent.getIntExtra(BluetoothHeadset.EXTRA_PREVIOUS_STATE, -1);
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log("VR Audio State: " + state);
                if (_speechRecBtDevice!=null && btDevice!=null && btDevice.getAddress().equals(
                    _speechRecBtDevice.getAddress()))
                {
                    if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED &&
                        (prevState == BluetoothHeadset.STATE_AUDIO_CONNECTED ||
                        prevState == BluetoothHeadset.STATE_AUDIO_CONNECTING))
                    {
                        _btSpeechRecInUse = false;
                        log("Speech recognition turned off for bluetooth device. Runnable null? "+
                            (_btSpeechRecStopRunnable==null));
                        if (_btSpeechRecStopRunnable!=null)
                        {
                            _btSpeechRecStopRunnable.run();
                            _btSpeechRecStopRunnable = null;
                        }
                    }
                    else if (state==BluetoothHeadset.STATE_AUDIO_CONNECTED)
                    {
                        log("Speech recognition is active on Bluetooth device "+_speechRecBtDevice.
                            getName());
                        _btSpeechRecInUse = true;
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33)
        {
            registerReceiver(_audioStateReceiver, new IntentFilter(BluetoothHeadset.
                ACTION_AUDIO_STATE_CHANGED), Context.RECEIVER_EXPORTED);
        }
        else
        {
            registerReceiver(_audioStateReceiver, new IntentFilter(BluetoothHeadset.
                ACTION_AUDIO_STATE_CHANGED));
        }
    }

    /** Override this to get a callback when Bluetooth speech recognition is set up and ready
     * to use. */
    protected void onBluetoothSpeechRecognitionReady()
    {
    }

    /** Start speech recognition over Bluetooth if possible. */
    @SuppressLint("StaticFieldLeak")
    protected void startBluetoothSpeechRecognition()
    {
        if (_btSpeechRecInUse)
        {
            // Nothing to do, speech recognition over bluetooth is already running.
            return;
        }
        if (_btAdapter!=null && _btHeadsetService!=null && _speechRecBtDevice!=null &&
            !_btSpeechRecInUse)
        {
            // We can't start Bluetooth speech recognition while sound (such as the beep telling
            // the user to say something) is playing.  So, if sound is playing, wait for it to
            // stop.  Starting BT speech recognition has the effect of muting the sound.
            new AsyncTask<Void, Void, Void>()
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    int elapsedTime = 0;
                    while (_audioManager.isMusicActive() && elapsedTime < 2000)
                    {
                        Util.sleep(100);
                        elapsedTime += 100;
                    }
                    if (elapsedTime >= 2000)
                    {
                        log("startBluetoothSpeechRecognition() timed out while waiting for " +
                            "audio playback to stop. Starting speech recognition anyway.");
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void x)
                {
                    try
                    {
                        if (_btHeadsetService.startVoiceRecognition(_speechRecBtDevice))
                        {
                            log("Bluetooth speech recognition started on device "+_speechRecBtDevice.
                                getName());
                        }
                        else
                        {
                            log("Failed to start speech recognition on BT device "+_speechRecBtDevice.
                                getName());
                        }
                    }
                    catch (Exception e)
                    {
                        // A small number of devices throw an exception here regarding
                        // the BLUETOOTH_ADMIN permission.  If that happens, then don't
                        // try to use the device.
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    /** Stop speech recognition over bluetooth if it's enabled. The optional runnable will execute
     * after the Bluetooth device has confirmed it's disconnected. This can be null. */
    protected void stopBluetoothSpeechRecognition(Runnable onStopRunnable)
    {
        if (_btAdapter!=null && _btHeadsetService!=null && _speechRecBtDevice!=null &&
            _btSpeechRecInUse)
        {
            _btHeadsetService.stopVoiceRecognition(_speechRecBtDevice);
            _btSpeechRecStopRunnable = onStopRunnable;
            log("Stopping bluetooth speech recognition on "+_speechRecBtDevice.getName());
        }
        else
        {
            if (onStopRunnable!=null)
                onStopRunnable.run();
        }
        _btSpeechRecInUse = false;
    }

    /** Log some debugging info to the database. This puts a tag in front of the text passed in. */
    protected void log(String text)
    {
        Util.log(_tag+": "+text);
    }

    @Override
    public void onDestroy()
    {
        // Disable speech recognition through the bluetooth headset, if one is connected.
        stopBluetoothSpeechRecognition(null);

        if (_btAdapter !=null && _btHeadsetService !=null)
            _btAdapter.closeProfileProxy(BluetoothProfile.HEADSET, _btHeadsetService);

        if (_audioStateReceiver!=null)
            unregisterReceiver(_audioStateReceiver);

        super.onDestroy();
    }
}
