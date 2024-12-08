package com.customsolutions.android.utl;

import android.app.Notification;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.preference.PreferenceManager;

import org.apache.commons.lang3.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

/**
 * This operates like Android's built-in Log class, but also writes log entries to a database so
 * that they can be later uploaded to our server for debugging.
 */
public class Log
{
    /** Database table creation statement. */
    private static final String TABLE_CREATE = "create table if not exists log_data ("+
        "timestamp integer not null, "+  // Milliseconds
        "priority integer not null, "+   // Same as constants in Android Log class
        "tag text not null, "+           // A String that typically matches the class or service name
        "message text not null"+         // The actual log message.
        ")";

    /** This prefix is placed in front of each tag.  It is helpful in filtering out logging from
     * other apps, services, and libraries. */
    private static final String PREFIX = "UTL-";

    /** A context, which is needed for certain functions. */
    public static Context _c;

    /** Quick reference to SharedPreferences. */
    private static SharedPreferences _prefs;

    /** Log an entry into the database. */
    private static void logToDatabase(int priority, String tag, String msg)
    {
        ContentValues values = new ContentValues();
        values.put("timestamp", System.currentTimeMillis());
        values.put("priority", priority);
        values.put("tag", tag);
        values.put("message", msg);
        try
        {
            Util.db().insert("log_data",null,values);
        }
        catch (Exception e)
        {
            // A safety measure in case Util.init() has not been called, or the database is
            // unreachable.
        }
    }

    /** Initialize logging.  This should be called when the app is first run. */
    public static void init(Context c)
    {
        _c = c.getApplicationContext();
        Util.db().execSQL(TABLE_CREATE);
        _prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
    }

    /** Log a verbose message. */
    public static void v(String tag, String msg)
    {
        if (BuildConfig.DEBUG || _prefs.getBoolean(PrefNames.LOG_TO_LOGCAT,false))
            android.util.Log.v(PREFIX + tag, msg);
        logToDatabase(android.util.Log.VERBOSE,tag,msg);
    }

    /** Log a debug message. */
    public static void d(String tag, String msg)
    {
        if (BuildConfig.DEBUG || _prefs.getBoolean(PrefNames.LOG_TO_LOGCAT,false))
            android.util.Log.d(PREFIX + tag, msg);
        logToDatabase(android.util.Log.DEBUG,tag,msg);
    }

    /** Log a debug message with an exception. */
    public static void d(String tag, String msg, Exception e)
    {
        d(tag, msg + " " + e.getMessage() + "\n" + exceptionToString(e));
    }

    /** Log a information message. */
    public static void i(String tag, String msg)
    {
        if (BuildConfig.DEBUG || _prefs.getBoolean(PrefNames.LOG_TO_LOGCAT,false))
            android.util.Log.i(PREFIX + tag, msg);
        logToDatabase(android.util.Log.INFO, tag, msg);
    }

    /** Log a warning message with a summary. */
    public static void w(String tag, String summary, String msg)
    {
        android.util.Log.w(PREFIX + tag, msg);
        logToDatabase(android.util.Log.WARN,tag,msg);

        Intent loggerIntent = new Intent(_c,ErrorLoggerService.class);
        loggerIntent.putExtra("tag",tag);
        loggerIntent.putExtra("message", msg);
        loggerIntent.putExtra("summary", summary);
        ErrorLoggerService.enqueueWork(_c,loggerIntent);

        /*
        // Record a firebase stat for the summary:
        if (Stats._firebaseAnalytics!=null)
        {
            String fbEventName = Stats.getFirebaseEventName(summary);
            Stats._firebaseAnalytics.logEvent(fbEventName,null);
        } */
    }

    /** Log a warning message with a summary and an exception. */
    public static void w(String tag, String summary, String msg, Exception e)
    {
        w(tag, summary, msg + " " + e.getMessage() + "\n" + exceptionToString(e));
    }

    /** Log an error message with a summary. */
    public static void e(String tag, String summary, String msg)
    {
        android.util.Log.e(PREFIX + tag, msg);
        logToDatabase(android.util.Log.ERROR,tag,msg);

        Intent loggerIntent = new Intent(_c,ErrorLoggerService.class);
        loggerIntent.putExtra("tag",tag);
        loggerIntent.putExtra("message", msg);
        loggerIntent.putExtra("summary", summary);
        ErrorLoggerService.enqueueWork(_c,loggerIntent);

        /*
        // Record a firebase stat for the summary:
        if (Stats._firebaseAnalytics!=null)
        {
            String fbEventName = Stats.getFirebaseEventName(summary);
            Stats._firebaseAnalytics.logEvent(fbEventName,null);
        }

         */
    }

    /** Log an error with a summary, and include exception information. */
    public static void e(String tag, String summary, String msg, Exception e)
    {
        e(tag, summary, msg + " " + e.getMessage() + "\n" + exceptionToString(e));
    }

    /** Upload a log file in the background. */
    private static void uploadLog()
    {
        Intent i = new Intent(_c,LogUploaderService.class);
        i.setAction(LogUploaderService.UPLOAD_ACTION);
        LogUploaderService.enqueueWork(_c,i);
    }

    /** Get the name of a class, without the package info. */
    public static String className(Object o)
    {
        String result = o.getClass().getSimpleName();
        if (result.equals(""))
            return "(anonymous)";
        else
            return result;
    }

    /** Get a String that shows keys and values in a Bundle. */
    public static String bundleToString(Bundle b, int indentSpaces)
    {
        String indent = "";
        for (int i=0; i<indentSpaces; i++)
            indent += " ";

        if (b==null) return "";
        String result = "";

        Set<String> keySet = b.keySet();
        for (String key : keySet)
        {
            // Unless this is the debug version, don't log the text of text messages in order
            // to preserve privacy.
            try
            {
                if (!BuildConfig.DEBUG && (key.equals(Notification.EXTRA_BIG_TEXT) ||
                    key.equals(Notification.EXTRA_TEXT)))
                {
                    result += "\n"+indent+"- "+key+": String of length "+b.get(key).toString().
                        length();
                    continue;
                }
                if (!BuildConfig.DEBUG && key.equals(Notification.EXTRA_TEXT_LINES))
                {
                    result += "\n"+indent+"- "+key+": "+b.getCharSequenceArray(key).length+
                        " lines of text";
                    continue;
                }
            }
            catch (NullPointerException e)
            {
                result += "\n"+indent+"- "+key+": null";
                continue;
            }

            if (b.get(key)!=null && b.get(key).getClass()!=null)
            {
                result += "\n" + indent + "- " + key + ": " + b.get(key) + " (" +
                    b.get(key).getClass().getName() + ")";
            }
            else
                result += "\n" + indent + "- " + key + ": " + b.get(key);
            if (b.get(key) != null)
            {
                if (b.get(key).getClass().getName().equals("android.os.Bundle"))
                {
                    result += bundleToString(b.getBundle(key), indentSpaces + 2);
                }
                if (b.get(key).getClass().getName().equals("[Ljava.lang.String;"))
                {
                    String[] strings = b.getStringArray(key);
                    for (String s : strings)
                    {
                        result += "\n"+indent+"  - "+s;
                    }
                }
            }
        }
        return result;
    }

    /** Get a String that contains all attributes of an Intent. */
    public static String intentToString(Intent intent, int indentSpaces)
    {
        String indent = "";
        for (int i=0; i<indentSpaces; i++)
            indent += " ";

        if (intent==null) return "";
        String result = "";

        if (intent.getComponent()!=null)
            result += "\n"+indent+"- Component: "+intent.getComponent().flattenToString();

        if (intent.getAction()!=null && intent.getAction().length()>0)
            result += "\n"+indent+"- Action: "+intent.getAction();

        if (intent.getClipData()!=null)
        {
            result += "\n" + indent + "- ClipData: ";
            for (int i=0; i<intent.getClipData().getItemCount(); i++)
            {
                ClipData.Item item = intent.getClipData().getItemAt(i);
                if (item.getHtmlText()!=null)
                    result += "\n" + indent + "  - "+item.getHtmlText();
                else if (item.getIntent()!=null)
                {
                    result += "\n" + indent + "  - Intent:" + Log.intentToString(item.getIntent(),
                        indentSpaces + 4);
                }
                else if (item.getText()!=null)
                    result += "\n" + indent + "  - "+item.getText();
                else if (item.getUri()!=null)
                    result += "\n" + indent + "  - "+item.getUri().toString();
            }
        }

        if (intent.getDataString()!=null && intent.getDataString().length()>0)
            result += "\n"+indent+"- Uri: "+intent.getDataString();

        if (intent.getFlags()!=0)
            result += "\n"+indent+"- Flags: "+ String.format("0x%08X",intent.getFlags());

        if (intent.getType()!=null && intent.getType().length()>0)
            result += "\n"+indent+"- Type: "+intent.getType();

        if (intent.getCategories()!=null)
        {
            result += "\n"+indent+"- Categories:";
            for (String c : intent.getCategories())
            {
                result += "\n"+indent+"  - "+c;
            }
        }

        try
        {
            if (intent.getExtras() != null && intent.getExtras().keySet().size() > 0)
            {
                result += "\n" + indent + "- Extras:";
                result += bundleToString(intent.getExtras(), indentSpaces + 2);
            }
        }
        catch (BadParcelableException e)
        {
            // I saw this getting thrown once.  I don't know why but the best solution is to
            // just ignore this since this is only a logging function.
        }

        return result;
    }

    /** Get a String that contains the full stack trace for an exception. */
    public static String exceptionToString(Exception e)
    {
        if (e==null)
            return "";
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }
}
