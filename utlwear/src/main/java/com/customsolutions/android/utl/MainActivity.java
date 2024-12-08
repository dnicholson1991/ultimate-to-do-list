package com.customsolutions.android.utl;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import androidx.core.splashscreen.SplashScreen;

public class MainActivity extends Activity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        Log.i("Test","Sanity Test");

        setContentView(R.layout.main_activity);

        Intent serviceIntent = new Intent(this,HandsetService.class);
        startService(serviceIntent);
        Util.dlog("Sanity Test 2");

        Util.log("Main Activity Started",this);

        // Check the version of Google Play Services installed on the device:
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        int result = availability.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS)
        {
            Util.dlog("Google Play Services Error Code: "+result);
            Intent i = new Intent(this,VoiceCommandError.class);
            Bundle b = new Bundle();
            if (result==ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED)
            {
                // The device is using an out of date version of Google Play Services
                b.putString("error_message", this.getString(R.string.play_services_out_of_date));
            }
            else
            {
                // Some other error.
                b.putString("error_message",availability.getErrorString(result));
            }
            b.putBoolean("hide_try_again", true);
            i.putExtras(b);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        }
        else
        {
            Intent getListIntent = new Intent(this, HandsetService.class);
            getListIntent.setAction(HandsetService.ACTION_GET_DEFAULT_LIST);
            getListIntent.putExtra("queue", true);
            startService(getListIntent);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        new Handler().postDelayed(()-> {
            finish();
        },3000);
    }
}
