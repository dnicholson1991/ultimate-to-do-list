package com.customsolutions.android.utl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

public class ShortcutMakerVoiceMode extends Activity
{
	@SuppressLint("InlinedApi")
	@Override
    protected void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
 
        // The meat of our shortcut
        Intent shortcutIntent = new Intent(this,VoiceCommand.class);
        if (Build.VERSION.SDK_INT>=11)
        	shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent.ShortcutIconResource iconResource = Intent.ShortcutIconResource.fromContext(this, 
        	R.drawable.voice_icon);
         
        // The result we are passing back from this activity
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, Util.getString(R.string.Voice_Mode));
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
        setResult(RESULT_OK, intent);
         
        finish(); // Must call finish for result to be returned immediately
    }
}
