package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.view.CardScrollView;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Nicholson on 12/17/2014.
 */
public class VoiceCommandError extends Activity
{
    private static final int SPEECH_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.voice_command_error);

        // CardScrollView cardScrollView =
                // (CardScrollView) findViewById(R.id.card_scroll_view);
        // cardScrollView.setCardGravity(Gravity.BOTTOM);

        Intent i = getIntent();
        if (i!=null)
        {
            Bundle b = i.getExtras();
            if (b!=null && b.containsKey("error_message"))
            {
                TextView msg = (TextView)findViewById(R.id.message);
                msg.setText(b.getString("error_message"));
            }
            if (b!=null && b.containsKey("hide_try_again") && b.getBoolean("hide_try_again"))
            {
                // The caller wants to hide the "try again" button.
                findViewById(R.id.try_again_button).setVisibility(View.GONE);
            }
            else
                findViewById(R.id.try_again_button).setVisibility(View.VISIBLE);
        }

        // Handler for the "Try Again" button:
        findViewById(R.id.try_again_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                startActivityForResult(i, SPEECH_REQUEST_CODE);
            }
        });

        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                finish();
            }
        });
    }

    /** Handles the speech response from Google. */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);

            // Strip out "note to self" or "take a note" if it's there:
            if (spokenText.toLowerCase().startsWith("note to self ")) {
                spokenText = spokenText.substring(13);
            }
            if (spokenText.toLowerCase().startsWith("take a note ")) {
                spokenText = spokenText.substring(12);
            }

            // Start the processing activity while we wait for the handset to process the speech.
            Bundle b = new Bundle();
            b.putString(android.content.Intent.EXTRA_TEXT, spokenText);
            Intent i = new Intent(this, VoiceCommandProcessing.class);
            i.putExtras(b);
            startActivity(i);
            finish();
        }
    }
}
