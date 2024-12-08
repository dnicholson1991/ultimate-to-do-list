package com.customsolutions.android.utl;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;

import java.util.ArrayList;

import static com.customsolutions.android.utl.Util.getString;

/**
 * This class displays a popup list of available views and allows the user to choose one.
 */

public class ViewPicker
{
    private Context _c;
    private SharedPreferences _settings;

    private ArrayList<ViewInfo> _viewList;

    public ViewPicker(Context c)
    {
        _c = c;
        _settings = c.getSharedPreferences(Util.PREFS_NAME,0);
    }

    /** Display the dialog to choose a view.
     * @param prompt - The text to display at the top of the list.
     * @param callback - Provides callback functions for processing the user's response.
     */
    public void chooseView(String prompt, final Callback callback)
    {
        // Create the list of views:

        ViewsDbAdapter vdb = new ViewsDbAdapter();
        int i;
        _viewList = new ArrayList<ViewInfo>();

        // All Tasks:
        Cursor c = vdb.getView(ViewNames.ALL_TASKS, "");
        if (c.moveToFirst())
            _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.AllTasks)));
        c.close();

        // Hotlist:
        c = vdb.getView(ViewNames.HOTLIST, "");
        if (c.moveToFirst())
            _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.Hotlist)));
        c.close();

        // Due Today/Tomorrow:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            c = vdb.getView(ViewNames.DUE_TODAY_TOMORROW, "");
            if (c.moveToFirst())
                _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.DueTodayTomorrow)));
            c.close();
        }

        // Overdue:
        if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
        {
            c = vdb.getView(ViewNames.OVERDUE, "");
            if (c.moveToFirst())
                _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.Overdue)));
            c.close();
        }

        // Starred:
        if (_settings.getBoolean(PrefNames.STAR_ENABLED, true))
        {
            c = vdb.getView(ViewNames.STARRED, "");
            if (c.moveToFirst())
                _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.Starred)));
            c.close();
        }

        // Recently Completed:
        c = vdb.getView(ViewNames.RECENTLY_COMPLETED, "");
        if (c.moveToFirst())
            _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.RecentlyCompleted)));
        c.close();

        // My Views:
        c = vdb.getViewsByLevel(ViewNames.MY_VIEWS);
        if (c!=null && c.getCount()>0)
        {
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.MyViews)+" / "+
                    Util.cString(c, "view_name")));
            }
        }
        if (c!=null) c.close();

        // By Status:
        if (_settings.getBoolean(PrefNames.STATUS_ENABLED, true))
        {
            String[] statuses = _c.getResources().getStringArray(R.array.statuses);
            for (i=0; i<statuses.length; i++)
            {
                c = vdb.getView(ViewNames.BY_STATUS, Integer.valueOf(i).toString());
                if (c.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c.getLong(0), getString(R.string.ByStatus)+" / "+
                        statuses[i]));
                }
            }
        }

        // We need to know how many accounts we have, since this affects the display:
        AccountsDbAdapter accountsDB = new AccountsDbAdapter();
        Cursor c2 = accountsDB.getAllAccounts();
        int numAccounts = c2.getCount();
        c2.close();

        // Folders:
        if (_settings.getBoolean(PrefNames.FOLDERS_ENABLED, true))
        {
            // Put the "no folder" view in.
            c = vdb.getView(ViewNames.FOLDERS,"0");
            if (c.moveToFirst())
            {
                _viewList.add(new ViewInfo(c.getLong(0),getString(R.string.Folders)+
                    " / "+getString(R.string.No_Folder)));
            }
            c.close();

            // Count the number of Toodledo versus other accounts.  This affects the sort order.
            c = accountsDB.getAllAccounts();
            c.moveToPosition(-1);
            int numToodledo = 0;
            int numOther = 0;
            while (c.moveToNext())
            {
                UTLAccount a = accountsDB.getAccount(Util.cLong(c, "_id"));
                if (a.sync_service==UTLAccount.SYNC_TOODLEDO)
                    numToodledo++;
                else
                    numOther++;
            }
            c.close();

            // Start a query for the folders, using the chosen sort order:
            FoldersDbAdapter foldersDB = new FoldersDbAdapter();
            if (numToodledo>0 && numOther==0)
                c = foldersDB.getFoldersByOrder();
            else
                c = foldersDB.getFoldersByNameNoCase();
            c.moveToPosition(-1);

            // Populate the options:
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.FOLDERS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c2.getLong(0), getString(R.string.Folders)+" / "+
                        nameToDisplay));
                }
                c2.close();
            }
            c.close();
        }

        // Contexts:
        if (_settings.getBoolean(PrefNames.CONTEXTS_ENABLED, true))
        {
            // Put the "no context" view in.
            c = vdb.getView(ViewNames.CONTEXTS,"0");
            if (c.moveToFirst())
            {
                _viewList.add(new ViewInfo(c.getLong(0),getString(R.string.Contexts)+
                    " / "+getString(R.string.No_Context)));
            }
            c.close();

            ContextsDbAdapter contextsDB = new ContextsDbAdapter();
            c = contextsDB.getContextsByNameNoCase();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.CONTEXTS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c2.getLong(0), getString(R.string.Contexts)+" / "+
                        nameToDisplay));
                }
                c2.close();
            }
            c.close();
        }

        // Goals:
        if (_settings.getBoolean(PrefNames.GOALS_ENABLED, true))
        {
            // Put the "no goal" view in.
            c = vdb.getView(ViewNames.GOALS,"0");
            if (c.moveToFirst())
            {
                _viewList.add(new ViewInfo(c.getLong(0),getString(R.string.Goals)+
                    " / "+getString(R.string.No_Goal)));
            }
            c.close();

            GoalsDbAdapter goalsDB = new GoalsDbAdapter();
            c = goalsDB.getAllGoalsNoCase();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.GOALS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c2.getLong(0), getString(R.string.Goals)+" / "+
                        nameToDisplay));
                }
                c2.close();
            }
            c.close();
        }

        // Tags:
        if (_settings.getBoolean(PrefNames.TAGS_ENABLED, true))
        {
            CurrentTagsDbAdapter currentTagsDB = new CurrentTagsDbAdapter();
            c = currentTagsDB.getTags();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "name");
                c2 = vdb.getView(ViewNames.TAGS, nameToDisplay);
                if (c2.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c2.getLong(0), getString(R.string.Tags)+" / "+
                        nameToDisplay));
                }
                c2.close();
            }
            c.close();
        }

        // Locations:
        if (_settings.getBoolean(PrefNames.LOCATIONS_ENABLED, true))
        {
            // Put the "no location" view in.
            c = vdb.getView(ViewNames.LOCATIONS,"0");
            if (c.moveToFirst())
            {
                _viewList.add(new ViewInfo(c.getLong(0),getString(R.string.Locations)+
                    " / "+getString(R.string.No_Location)));
            }
            c.close();

            LocationsDbAdapter locDB = new LocationsDbAdapter();
            c = locDB.getAllLocations();
            c.moveToPosition(-1);
            while (c.moveToNext())
            {
                String nameToDisplay = Util.cString(c, "title");
                if (numAccounts>1)
                {
                    UTLAccount a = accountsDB.getAccount(Util.cLong(c, "account_id"));
                    if (a!=null)
                        nameToDisplay = Util.cString(c, "title")+" ("+a.name+")";
                }
                c2 = vdb.getView(ViewNames.LOCATIONS, Long.valueOf(c.getLong(0)).toString());
                if (c2.moveToFirst())
                {
                    _viewList.add(new ViewInfo(c2.getLong(0), getString(R.string.Locations)+" / "+
                        nameToDisplay));
                }
                c2.close();
            }
            c.close();
        }

        // Create and display a dialog with these choices:
        final String[] nameArray = new String[_viewList.size()];
        for (i=0; i<_viewList.size(); i++)
        {
            nameArray[i] = _viewList.get(i).name;
        }
        new AlertDialog.Builder(_c)
            .setTitle(prompt)
            .setItems(nameArray, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    callback.onViewSelected(_viewList.get(which).id, _viewList.get(which).name);
                }
            })
            .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    callback.onCancel();
                }
            })
            .setOnCancelListener(new DialogInterface.OnCancelListener()
            {
                @Override
                public void onCancel(DialogInterface dialog)
                {
                    callback.onCancel();
                }
            })
            .show();
    }

    /** This interface provides a callback that is used for passing the user's choice back to the
     * caller. */
    public interface Callback
    {
        void onViewSelected(long viewID, String viewName);

        void onCancel();
    }

    /** A class to hold information on the available views we can copy from. */
    private class ViewInfo
    {
        public String name;
        public long id;

        public ViewInfo(long newID, String newName)
        {
            name=newName;
            id=newID;
        }
    }
}
