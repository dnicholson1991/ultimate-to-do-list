package com.customsolutions.android.utl;

/**
 * Holds information about a task being displayed in a list.
 */
public class TaskInfo
{
    public int iconRes;
    public String title;
    public boolean isCompleted;
    public long taskID;

    public TaskInfo(String title, boolean isCompleted, long taskID)
    {
        this.title = title;
        this.isCompleted = isCompleted;
        this.taskID = taskID;
        if (isCompleted)
            iconRes = R.drawable.checkbox_checked;
        else
            iconRes = R.drawable.checkbox_cyan;
    }
}
