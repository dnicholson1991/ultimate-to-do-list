package com.customsolutions.android.utl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by Nicholson on 12/15/2014.
 */
public class Util {
    static public void dlog(String string)
    {
        Log.i("Test", string);
    }

    /** Log a message to the database on the handheld: */
    static public void log(String message, Context c)
    {
        Intent i = new Intent(c,HandsetService.class);
        i.setAction(HandsetService.ACTION_LOG);
        Bundle b = new Bundle();
        b.putString("message",message);
        i.putExtras(b);
        c.startService(i);

        Util.dlog(message);
    }

    /** Log a message to the database on the handheld: */
    static public void log(String tag, String message, Context c)
    {
        Intent i = new Intent(c,HandsetService.class);
        i.setAction(HandsetService.ACTION_LOG);
        Bundle b = new Bundle();
        b.putString("message",tag+": "+message);
        i.putExtras(b);
        c.startService(i);

        Util.dlog(tag+": "+message);
    }
}
