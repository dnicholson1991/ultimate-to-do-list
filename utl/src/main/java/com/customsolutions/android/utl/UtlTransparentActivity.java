package com.customsolutions.android.utl;

import android.os.Bundle;

/**
 * An Activity with a transparent background.
 */

public class UtlTransparentActivity extends UtlActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        // Set the Theme:
        setTheme(R.style.UtlStylePacific_Transparent);

        super.onCreate(savedInstanceState);
    }
}
