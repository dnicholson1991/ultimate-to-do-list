package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by Nicholson on 12/17/2014.
 */
public class VoiceCommandProcessing extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Util.log("VoiceCommandProcessing: Starting up.",this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice_command_processing);

        Intent i = getIntent();
        Bundle b = i.getExtras();
        if (b.containsKey(android.content.Intent.EXTRA_TEXT))
        {
            // Send the Text to UTL on the handset for processing.
            i = new Intent(this,HandsetService.class);
            i.setAction(HandsetService.ACTION_SPEECH_TO_PROCESS);
            i.putExtra("speech",b.getString(android.content.Intent.EXTRA_TEXT));
            i.putExtra("queue",true);
            startService(i);
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        finish();
    }
}
