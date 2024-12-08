package com.customsolutions.android.utl;

/**
 * Created by Nicholson on 12/14/2014.
 */
public class SnoozeItemInfo
{
    public int iconRes;
    public String title;
    public int numMinutes;
    public boolean isLocation;
    public long taskID;

    public SnoozeItemInfo(int iconRes, String title, int numMinutes, boolean isLocation, long taskID)
    {
        this.iconRes = iconRes;
        this.title = title;
        this.numMinutes = numMinutes;
        this.isLocation = isLocation;
        this.taskID = taskID;
    }
}
