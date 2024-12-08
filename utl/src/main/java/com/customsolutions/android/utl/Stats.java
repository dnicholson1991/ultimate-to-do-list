package com.customsolutions.android.utl;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/** Utility functions for keeping track of usage stats locally and uploading the data to our
 * server. */
public class Stats
{
    private static final String TAG = "Stats";

    /** Name of the stats table. */
    private static final String STATS_TABLE = "stats";

    /** Table creation statement. */
    private static final String STATS_TABLE_CREATE = "create table if not exists stats ("+
        "id integer primary key autoincrement, "+ // A standard unique database ID.
        "timestamp integer, "+                    // Millisecond timestamp of stat event.
        "name text, "+                            // Name of event recorded.
        "param0 text, "+                          // Up to 8 parameters for the event.
        "param1 text, "+
        "param2 text, "+
        "param3 text, "+
        "param4 text, "+
        "param5 text, "+
        "param6 text, "+
        "param7 text "+
        ")";

    /** Initialization to perform on app startup. */
    public static void init(Context c)
    {
        Util.db().execSQL(STATS_TABLE_CREATE);
    }

    /** Record a stat. */
    public static void recordStat(Context c, String name, String[] params)
    {
        if (params!=null && params.length>0)
            Log.i(TAG,"Stat: "+name+" / "+params[0]);
        else
            Log.i(TAG,"Stat: "+name);

        if (Api.DISABLE_BACKEND)
            return;

        // Add the record to the database.
        ContentValues cv = new ContentValues();
        cv.put("timestamp",System.currentTimeMillis());
        cv.put("name",name);
        if (params!=null && params.length>0)
        {
            String[] fields = new String[]{"param0", "param1", "param2", "param3", "param4",
                "param5", "param6", "param7"};
            for (int i=0; i<params.length && i<8; i++)
            {
                cv.put(fields[i],params[i]);
            }
        }
        Util.db().insert(STATS_TABLE,null,cv);
    }

    /** Upload all stats to the server, then execute a Runnable (which may be null). */
    public static void uploadStats(Context c, final Runnable onCompletion)
    {
        if (Api.DISABLE_BACKEND)
            return;

        try
        {
            SharedPreferences prefs = c.getSharedPreferences(Util.PREFS_NAME,0);
            if (prefs.getLong(PrefNames.INSTALL_ID,0)==0)
            {
                Log.v(TAG,"Install ID is not yet set.  Waiting until later to upload stats.");
                if (onCompletion!=null)
                    onCompletion.run();
                return;
            }

            // Gather the stats into a JSON array:
            JSONArray jsonArray = new JSONArray();
            final ArrayList<Integer> rowIDs = new ArrayList<>();
            Cursor c2 = Util.db().query(STATS_TABLE,new String[] {"id","timestamp","name",
                "param0","param1","param2","param3","param4","param5","param6","param7"},null,
                null,null,null,null);
            while (c2.moveToNext())
            {
                JSONObject stat = new JSONObject();
                stat.put("timestamp",c2.getLong(1));
                stat.put("name",c2.getString(2));
                if (Util.isValid(c2.getString(3)))
                    stat.put("param0",c2.getString(3));
                if (Util.isValid(c2.getString(4)))
                    stat.put("param1",c2.getString(4));
                if (Util.isValid(c2.getString(5)))
                    stat.put("param2",c2.getString(5));
                if (Util.isValid(c2.getString(6)))
                    stat.put("param3",c2.getString(6));
                if (Util.isValid(c2.getString(7)))
                    stat.put("param4",c2.getString(7));
                if (Util.isValid(c2.getString(8)))
                    stat.put("param5",c2.getString(8));
                if (Util.isValid(c2.getString(9)))
                    stat.put("param6",c2.getString(9));
                if (Util.isValid(c2.getString(10)))
                    stat.put("param7",c2.getString(10));
                jsonArray.put(stat);
                rowIDs.add(c2.getInt(0));
            }
            c2.close();
            if (jsonArray.length()==0)
            {
                // No stats have been collected. We're done.
                if (onCompletion!=null)
                    onCompletion.run();
                return;
            }

            // Upload the stats to the server. Upon failure, we just try again later.
            JSONObject toSend = new JSONObject()
                .put("install_id",prefs.getLong(PrefNames.INSTALL_ID,0))
                .put("stats",jsonArray);
            Api.postToServer("record_stats", toSend, false, new Api.Callback()
            {
                @Override
                public void onSuccess(JSONObject jsonObject)
                {
                    // Upon success, we need to remove all rows that have been uploaded.
                    for (int id: rowIDs)
                    {
                        Util.db().delete(STATS_TABLE,"id=?",new String[] {String.valueOf(id)});
                    }
                    if (onCompletion!=null)
                        onCompletion.run();
                }

                @Override
                public void onFailure(JSONObject jsonObject)
                {
                    if (onCompletion!=null)
                        onCompletion.run();
                }

                @Override
                public void onNetworkError()
                {
                    if (onCompletion!=null)
                        onCompletion.run();
                }

                @Override
                public void onTechnicalError()
                {
                    if (onCompletion!=null)
                        onCompletion.run();
                }
            });
        }
        catch (JSONException e)
        {
            Util.handleException(TAG,c,e);
        }
    }
}
