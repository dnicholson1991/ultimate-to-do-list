package com.customsolutions.android.utl;

import java.util.ArrayList;

/** This class holds information on a task that has been generated using speech recognition.  It combines
 * a UTLTask object with additional data needed. */
public class UTLTaskSpeech
{
	/** The task object that will ultimately be stored in the database. */
	public UTLTask task;
	
	/** Array of tags: */
	public ArrayList<String> tags;
	
	/** Add this task to the calendar? */
	public boolean addToCalendar;
	
	/** The prior reminder date/time.  This is used when editing. */
	public long priorReminder;
	
	/** True if the timer will start when the task is created.  For edits, determines if the timer
	 * is started or stopped. */
	public boolean willStartTimer;
	
	/** Boolean flags indicating which fields have been explicitly set so far during processing. 
	 * For task edit, these flag which fields are being changed. */
	public boolean reminderSet;
	public boolean startDateSet;
	public boolean dueDateSet;
	public boolean nagSet;
	public boolean folderSet;
	public boolean contextSet;
	public boolean goalSet;
	public boolean tagSet;
	public boolean accountSet;
	public boolean parentSet;
	public boolean locationSet;
	public boolean prioritySet;
	public boolean statusSet;
	public boolean timerSet;
	public boolean calendarSet;
	public boolean starSet;
	public boolean repeatSet;
	public boolean completedSet;
	public boolean titleSet;
	
	/** For task updates only.  This is the original title. */
	public String priorTitle;
	
	/** This is used for task updates only.  It contains an error message, and is set only if an error
	 * occurs.  Otherwise, this is an empty string. */
	public String error;
	
	/** The constructor creates a blank task. */
	public UTLTaskSpeech()
	{
		task = new UTLTask();
		tags = new ArrayList<String>();
		addToCalendar = false;
		
		reminderSet = false;
		startDateSet = false;
		dueDateSet = false;
		nagSet = false;
		folderSet = false;
		contextSet = false;
		goalSet = false;
		tagSet = false;
		accountSet = false;
		parentSet = false;
		locationSet = false;
		prioritySet = false;
		statusSet = false;
		timerSet = false;
		calendarSet = false;
		starSet = false;
		repeatSet = false;
		completedSet = false;
		
		error = "";
		
		priorReminder = 0;
		priorTitle = "";
	}
	
	/** Construct an instance that is purely for returning an error. */
	public UTLTaskSpeech(String errorMsg)
	{
		error = errorMsg;
	}
	 
}
