package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

/** This parses the text we've received through speech recognition, and returns information on 
 * tasks to be added or modified, or on views to be read out.
 * @author Nicholson
 */
public class SpeechParserEnglish extends SpeechParser
{	
	public SpeechParserEnglish(Context c)
	{
		super(c);
	}
	
	/** Determine if the user is asking to add a task, edit a task, or read a task list.
	 * @param speech - The output of the speech recognizer.
	 * @return Either OP_ADD, OP_MODIFY, or OP_READ
	 */
	public int getOperationType(String speech)
	{
		speech = speech.toLowerCase();
		if (speech.startsWith("remind me to") || speech.startsWith("create task") || 
			speech.startsWith("create a task") || speech.startsWith("add a task") ||
			speech.startsWith("add task"))
		{
			return OP_ADD;
		}
		else if (speech.startsWith("mark") || speech.startsWith("start the timer") ||
			speech.startsWith("start timer") || speech.startsWith("stop the timer") ||
			speech.startsWith("stop timer") || speech.startsWith("postpone") ||
			speech.startsWith("change") || speech.startsWith("add a location reminder") ||
			speech.startsWith("add location reminder") || speech.startsWith("remove location reminder") ||
			speech.startsWith("remove the location reminder") ||
			speech.startsWith("rename") || speech.startsWith("add the tag") ||
			speech.startsWith("add tag") || speech.startsWith("remove the tag") ||
			speech.startsWith("remove tag") || speech.startsWith("add a star") ||
			speech.startsWith("add star") || speech.startsWith("remove the star") ||
			speech.startsWith("remove star"))
		{
			return OP_MODIFY;
		}
		else if (speech.startsWith("read"))		
		{
			return OP_READ;
		}
		else
		{
			// Assume the user is starting with the task name.
			return OP_ADD;
		}
	}
	
	/** Parse speech that corresponds to a new task.
	 * @param ts - A UTLTaskSpeech object that will be filled in with task information that
	 *     was retrieved.
	 * @param speech - The text of the speech.
	 * @return An error message in the language used by the subclass.  On success, the number of fields
	 *     that were successfully parsed is returned as a string.  This helps to determine which of the speech
	 *     recognition candidates is best.
	 */
	protected String parseNewTask(UTLTaskSpeech ts, String speech)
	{
		// These flags determine what task fields have been specified in the speech:
		boolean usingReminderFormat = false;  // speech begins with "remind me to".
		
		// The number of fields successfully parsed.
		int numFields = 0;
		
		// Look at the beginning to determine whether we're setting a reminder.  Also remove the 
		// beginning part of the string, so we know that the task title is at the front.
		String lowercase = speech.toLowerCase();
		if (lowercase.startsWith("remind me to "))
		{
			speech = speech.replaceFirst("(?i)remind me to ", "");
			usingReminderFormat = true;
		}
		else if (lowercase.startsWith("remind me two "))
		{
			speech = speech.replaceFirst("(?i)remind me two ", "");
			usingReminderFormat = true;
		}
		else
		{
			// Strings we accept at the start:
			String[] startStrings = { 
				"create a task named ",
				"create a task ",
				"create task named ",
				"create task ",
				"add a task named ",
				"add a task ",
				"add task named ",
				"add task ",
			};
			for (int i=0; i<startStrings.length; i++)
			{
				if (lowercase.startsWith(startStrings[i]))
				{
					speech = speech.replaceFirst("(?i)"+startStrings[i], "");
					break;
				}
			}
		}
		
		// At this point, the task name is at the front of the string.
		
		// This loop runs until we have extracted everything we can out of the speech.
		while (true)
		{
			// With each iteration, leading and trailing spaces must be removed.
			speech = speech.trim();
			
			// Look for an account name.
			Pattern pat = Pattern.compile("(?:.*)((?:in |with )(?:the )?(.+?) account)",Pattern.CASE_INSENSITIVE);
			Matcher mat = pat.matcher(speech);
			if (!ts.accountSet && mat.find())
			{
				long accountID = parseAccount(mat.group(2));
				if (accountID==0)
					return "I'm sorry, I can't find an account named \""+mat.group(2)+"\".";
				else
				{
					ts.accountSet = true;
					ts.task.account_id = accountID;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}
			
			// Look for a folder.  If found. remove the text that specified it.
			pat = Pattern.compile("(?:.*)((?:in |with )(?:the )?(.+?) folder)",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.folderSet && mat.find())
			{
				long folderID;
				if (ts.accountSet)
					folderID = parseFolder(mat.group(2),ts.task.account_id);
				else
					folderID = parseFolder(mat.group(2),0);
				if (folderID==0)
					return "I'm sorry, I can't find a folder named \""+mat.group(2)+"\".";
				else
				{
					ts.folderSet = true;
					ts.task.folder_id = folderID;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}
			
			// Look for a context.  If found. remove the text that specified it.
			pat = Pattern.compile("(?:.*)((?:in |with )(?:the )?(.+?) context)",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.contextSet && mat.find())
			{
				long contextID;
				if (ts.accountSet)
					contextID = parseContext(mat.group(2),ts.task.account_id);
				else
					contextID = parseContext(mat.group(2),0);
				if (contextID==0)
				{
					return "I'm sorry, I can't find a context named \""+mat.group(2)+"\".";
				}
				else
				{
					ts.contextSet = true;
					ts.task.context_id = contextID;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}

			// Look for a goal.  If found. remove the text that specified it.
			pat = Pattern.compile("(?:.*)((?:in |with )(?:the )?(.+?) goal)",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.goalSet && mat.find())
			{
				long goalID;
				if (ts.accountSet)
					goalID = parseGoal(mat.group(2),ts.task.account_id);
				else
					goalID = parseGoal(mat.group(2),0);
				if (goalID==0)
					return "I'm sorry, I can't find a goal named \""+mat.group(2)+"\".";
				else
				{
					ts.goalSet = true;
					ts.task.goal_id = goalID;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}

			// Location reminder with nagging:
			pat = Pattern.compile("(?:.*)(located (?:at|in) (.+?) with (?:a )?nagging reminder)",Pattern.
				CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.locationSet && mat.find())
			{
				long locID;
				if (ts.accountSet)
					locID = parseLocation(mat.group(2),ts.task.account_id);
				else
					locID = parseLocation(mat.group(2),0);
				if (locID==0)
					return "I'm sorry, I can't find a location named \""+mat.group(2)+"\".";
				else
				{
					ts.locationSet = true;
					ts.task.location_id = locID;
					ts.task.location_reminder = true;
					ts.task.location_nag = true;
					ts.locationSet = true;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}
			
			// Location reminder without nagging:
			pat = Pattern.compile("(?:.*)(located (?:at|in) (.+?) with (?:a )?reminder)",Pattern.
				CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.locationSet && mat.find())
			{
				long locID;
				if (ts.accountSet)
					locID = parseLocation(mat.group(2),ts.task.account_id);
				else
					locID = parseLocation(mat.group(2),0);
				if (locID==0)
					return "I'm sorry, I can't find a location named \""+mat.group(2)+"\".";
				else
				{
					ts.locationSet = true;
					ts.task.location_id = locID;
					ts.task.location_reminder = true;
					ts.locationSet = true;
					speech = speech.replace(mat.group(1), "");
					numFields++;
					continue;
				}
			}
			
			// Look for a Tag:
			pat = Pattern.compile("(?:.*)(with (?:the )?(.+?) tag(ged)?)",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.tagSet && mat.find())
			{
				ts.tags.add(mat.group(2));
				ts.tagSet = true;
				speech = speech.replace(mat.group(1), "");
				numFields++;
				continue;
			}
			
			// Nagging reminders:
			pat = Pattern.compile("and nag me",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.nagSet && mat.find())
			{
				ts.nagSet = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			pat = Pattern.compile("with nagging",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.nagSet && mat.find())
			{
				ts.nagSet = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Add a star:
			mat = Pattern.compile("(?:and )?add (?:a )?star", Pattern.CASE_INSENSITIVE).matcher(speech);
			if (!ts.starSet && mat.find())
			{
				ts.starSet = true;
				ts.task.star = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Timer:
			mat = Pattern.compile("(?:and )?start (?:the )timer",Pattern.CASE_INSENSITIVE).matcher(speech);
			if (!ts.timerSet && mat.find())
			{
				ts.timerSet = true;
				ts.willStartTimer = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Add to calendar:
			mat = Pattern.compile("(?:and )?add to calendar",Pattern.CASE_INSENSITIVE).matcher(speech);
			if (!ts.calendarSet && mat.find())
			{
				ts.calendarSet = true;
				ts.addToCalendar = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			mat = Pattern.compile("(?:and )?show in calendar",Pattern.CASE_INSENSITIVE).matcher(speech);
			if (!ts.calendarSet && mat.find())
			{
				ts.calendarSet = true;
				ts.addToCalendar = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Priority:
			pat = Pattern.compile("(?:and|with|and with) (no|none|nothing|negative|low|medium|high|top) priority",
				Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.prioritySet && mat.find())
			{
				String p = mat.group(1).toLowerCase();
				if (p.equals("none") || p.equals("no") || p.equals("nothing"))
					ts.task.priority = 0;
				if (p.equals("negative"))
					ts.task.priority = 1;
				if (p.equals("low"))
					ts.task.priority = 2;
				if (p.equals("medium"))
					ts.task.priority = 3;
				if (p.equals("high"))
					ts.task.priority = 4;
				if (p.equals("top"))
					ts.task.priority = 5;
				ts.prioritySet = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Status:
			pat = Pattern.compile("(?:and|with|and with) (no|none|nothing|next action|active|planning|delegated|waiting|hold|"+
				"postponed|someday|canceled|reference) status",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.statusSet && mat.find())
			{
				String s = mat.group(1).toLowerCase();
				if (s.equals("no") || s.equals("none") || s.equals("nothing"))
					ts.task.status = 0;
				if (s.equals("next action"))
					ts.task.status = 1;
				if (s.equals("active"))
					ts.task.status = 2;
				if (s.equals("planning"))
					ts.task.status = 3;
				if (s.equals("delegated"))
					ts.task.status = 4;
				if (s.equals("waiting"))
					ts.task.status = 5;
				if (s.equals("hold"))
					ts.task.status = 6;
				if (s.equals("postponed"))
					ts.task.status = 7;
				if (s.equals("someday"))
					ts.task.status = 8;
				if (s.equals("canceled"))
					ts.task.status = 9;
				if (s.equals("reference"))
					ts.task.status = 10;
				ts.statusSet = true;
				speech = speech.replace(mat.group(), "");
				numFields++;
				continue;
			}
			
			// Other fields (such as dates) don't have clear starting and ending points in the 
			// speech.  We need to search for keywords indicating what fields are specified, and
			// process the latest one.  We know that the field value for the latest one will end
			// at the end of the speech.
			
			int lastKeywordIndex = 0;
			String nextField = "";
			String nextValue = "";
			String matchingText = "";
			
			// Start Date - Pattern 1:
			Pattern startDatePattern1 = Pattern.compile("(?:and )?starting (?:on |in )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = startDatePattern1.matcher(speech);
			if (!ts.startDateSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "start date";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
				
			// Start Date - Pattern 2:
			Pattern startDatePattern2 = Pattern.compile("(?:and with|and|with) (?:a )?start date (?:of |in |on )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = startDatePattern2.matcher(speech);
			if (!ts.startDateSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "start date";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Start Date - Pattern 3:
			Pattern startDatePattern3 = Pattern.compile("(?:and with|and|with) (?:a )?start time (?:of |in |on )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = startDatePattern3.matcher(speech);
			if (!ts.startDateSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "start date";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Due Date - Pattern 2:
			Pattern dueDatePattern2 = Pattern.compile("(?:and with|and|with) (?:a )?due date (?:of |in |on )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = dueDatePattern2.matcher(speech);
			if (!ts.dueDateSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "due date";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			else
			{			
				// Due Date - Pattern 3:
				Pattern dueDatePattern3 = Pattern.compile("(?:and with|and|with) (?:a )?due time (?:of |in |on )?(.++)",Pattern.
					CASE_INSENSITIVE);
				mat = dueDatePattern3.matcher(speech);
				if (!ts.dueDateSet && mat.find() && mat.start()>lastKeywordIndex)
				{
					nextValue = mat.group(1);
					nextField = "due date";
					lastKeywordIndex = mat.start();
					matchingText = mat.group();
				}
				else
				{
					// Due Date - Pattern 1:
					Pattern dueDatePattern1 = Pattern.compile("(?:and )?due (?:on|in) (.++)",Pattern.
						CASE_INSENSITIVE);
					mat = dueDatePattern1.matcher(speech);
					if (!ts.dueDateSet && mat.find() && mat.start()>lastKeywordIndex)
					{
						nextValue = mat.group(1);
						nextField = "due date";
						lastKeywordIndex = mat.start();
						matchingText = mat.group();
					}
				}
			}
				
			// Reminder Date - Pattern 1:
			Pattern reminderPattern1 = Pattern.compile("(?:and )?remind me (?:on |in )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = reminderPattern1.matcher(speech);
			if (!ts.reminderSet && !usingReminderFormat && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "reminder";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Reminder Date - Pattern 2:
			Pattern reminderPattern2 = Pattern.compile("(?:and with|and|with) (?:a )?reminder (?:on |in |of )?(.++)",Pattern.
				CASE_INSENSITIVE);
			mat = reminderPattern2.matcher(speech);
			if (!ts.reminderSet && !usingReminderFormat && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "reminder";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Repeat:
			Pattern repeatPattern = Pattern.compile("(?:and )?repeat (.++)",Pattern.CASE_INSENSITIVE);
			mat = repeatPattern.matcher(speech);
			if (!ts.repeatSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "repeat";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Parent task:
			Pattern subtaskPattern = Pattern.compile("(?:as |a |as a )sub\\s?task of (.++)",
				Pattern.CASE_INSENSITIVE);
			mat = subtaskPattern.matcher(speech);
			if (!ts.repeatSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "parent";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			// Location:
			pat = Pattern.compile("located at (.++)",Pattern.CASE_INSENSITIVE);
			mat = pat.matcher(speech);
			if (!ts.locationSet && mat.find() && mat.start()>lastKeywordIndex)
			{
				nextValue = mat.group(1);
				nextField = "location";
				lastKeywordIndex = mat.start();
				matchingText = mat.group();
			}
			
			if (nextField.length()==0)
			{
				// No other fields and values were found.  What remains is either the title, or
				// the title with the reminder date and time.
				if (usingReminderFormat)
				{
					// These patterns represent time based or location based reminders
					Matcher ambiguousMatcher = Pattern.compile("^(.+?) (?:on|in|at) (.+)$",
						Pattern.CASE_INSENSITIVE).matcher(speech);
					Matcher locMatcher1 = Pattern.compile("^(.+?) when I arrived? (?:at )?(.+)$",
						Pattern.CASE_INSENSITIVE).matcher(speech);
					Matcher locMatcher2 = Pattern.compile("^(.+?) when I get to (.+)$",
						Pattern.CASE_INSENSITIVE).matcher(speech);
					Matcher dateMatcher1 = Pattern.compile("^(.+) ((today|tomorrow|sunday|monday|tuesday"+
						"|wednesday|thursday|friday|saturday) (?:at )?(.+))$",Pattern.CASE_INSENSITIVE).
						matcher(speech);
					
					// Search for matches:
					if (locMatcher1.find())
					{
						ts.task.title = locMatcher1.group(1).substring(0, 1).toUpperCase() + locMatcher1.group(1).
							substring(1);
						if (ts.accountSet)
							ts.task.location_id = parseLocation(locMatcher1.group(2),ts.task.account_id);
						else
							ts.task.location_id = parseLocation(locMatcher1.group(2),0);
						if (ts.task.location_id==0)
							return "I'm sorry, I can't find the location \""+locMatcher1.group(2)+"\".";
						ts.locationSet = true;
						ts.task.location_reminder = true;
						numFields++;
					}
					else if (locMatcher2.find())
					{
						ts.task.title = locMatcher2.group(1).substring(0, 1).toUpperCase() + locMatcher2.group(1).
							substring(1);
						if (ts.accountSet)
							ts.task.location_id = parseLocation(locMatcher2.group(2),ts.task.account_id);
						else
							ts.task.location_id = parseLocation(locMatcher2.group(2),0);
						if (ts.task.location_id==0)
							return "I'm sorry, I can't find the location \""+locMatcher2.group(2)+"\".";
						ts.locationSet = true;
						ts.task.location_reminder = true;
						numFields++;
					}
					else if (dateMatcher1.find() && !dateMatcher1.group(1).toLowerCase().endsWith(" on"))
					{
						ts.task.title = dateMatcher1.group(1).substring(0, 1).toUpperCase() + 
							dateMatcher1.group(1).substring(1);
						long possibleReminder = parseDateTime(dateMatcher1.group(2));
						if (possibleReminder==0)
						{
							return "I'm sorry, I don't understand when you want to be reminded ("+
								dateMatcher1.group(2)+").";
						}
						else
						{
							ts.task.reminder = possibleReminder;
							ts.reminderSet = true;
							numFields++;
							if (possibleReminder==Util.getMidnight(possibleReminder))
							{
								// Set the reminder time to 8 AM.
								ts.task.reminder += 8*60*60*1000;
							}
						}
					}
					else if (ambiguousMatcher.find())
					{
						// This could be a reminder date/time, or a location.  Need to try each.
						ts.task.title = ambiguousMatcher.group(1).substring(0, 1).toUpperCase() + 
							ambiguousMatcher.group(1).substring(1);
						long possibleReminder = parseDateTime(ambiguousMatcher.group(2));
						if (possibleReminder==0)
						{
							// Try a location search:
							if (ts.accountSet)
								ts.task.location_id = parseLocation(ambiguousMatcher.group(2),ts.task.account_id);
							else
								ts.task.location_id = parseLocation(ambiguousMatcher.group(2),0);
							if (ts.task.location_id==0)
							{
								return "I'm sorry, I don't understand when you want to be reminded ("+
									ambiguousMatcher.group(2)+").";
							}
							else
							{
								ts.locationSet = true;
								ts.task.location_reminder = true;
								numFields++;
							}
						}
						else
						{
							ts.task.reminder = possibleReminder;
							ts.reminderSet = true;
							numFields++;
							if (possibleReminder==Util.getMidnight(possibleReminder))
							{
								// Set the reminder time to 8 AM.
								ts.task.reminder += 8*60*60*1000;
							}
						}
					}
					else
					{
						return "I'm sorry, I don't understand your reminder.";
					}
				}
				else
				{
					// Store the task title, while capitalizing the first letter:
					ts.task.title = speech.substring(0, 1).toUpperCase() + speech.substring(1);
				}
				break;
			}
			else
			{
				if (nextField.equals("start date"))
				{
					long startDate = parseDateTime(nextValue);
					if (startDate>0)
					{
						ts.task.start_date = startDate;
						if (startDate!=Util.getMidnight(startDate))
							ts.task.uses_start_time = true;
						ts.startDateSet = true;
					}
					else
					{
						return "I'm sorry, I don't understand your start date ("+nextValue+").";
					}
				}
				else if (nextField.equals("due date"))
				{
					long dueDate = parseDateTime(nextValue);
					if (dueDate>0)
					{
						ts.task.due_date = dueDate;
						if (dueDate!=Util.getMidnight(dueDate))
							ts.task.uses_due_time = true;
						ts.dueDateSet = true;
					}
					else
					{
						return "I'm sorry, I don't understand your due date ("+nextValue+").";
					}
				}
				else if (nextField.equals("reminder"))
				{
					long reminder = parseDateTime(nextValue);
					if (reminder>0)
					{
						ts.task.reminder = reminder;
						ts.reminderSet = true;
					}
					else
					{
						return "I'm sorry, I don't understand your reminder date ("+nextValue+").";
					}
				}
				else if (nextField.equals("repeat"))
				{
					RepeatPattern repeatPattern2 = parseRepeatPattern(nextValue);
					if (repeatPattern2==null)
					{
						return "I'm sorry, I don't understand your repeat pattern ("+nextValue+").";
					}
					ts.task.repeat = repeatPattern2.standardRepeat;
					if (repeatPattern2.repeatFromCompletion && repeatPattern2.standardRepeat>0)
						ts.task.repeat += 100;
					if (repeatPattern2.standardRepeat==50)
						ts.task.rep_advanced = repeatPattern2.advancedRepeat;
					ts.repeatSet = true;
				}
				else if (nextField.equals("parent"))
				{
					long parentID;
					if (ts.accountSet)
						parentID = parseTaskTitle(nextValue,false,true,ts.task.account_id);
					else
						parentID = parseTaskTitle(nextValue,false,true,0);
					if (parentID==0)
						return "I'm sorry, I can't find a task named \""+nextValue+"\".";
					ts.parentSet = true;
					ts.task.parent_id = parentID;
				}
				else if (nextField.equals("location"))
				{
					long locID;
					if (ts.accountSet)
						locID = parseLocation(nextValue,ts.task.account_id);
					else
						locID = parseLocation(nextValue,0);
					if (locID==0)
						return "I'm sorry, I can't find a location named \""+nextValue+"\"";
					ts.locationSet = true;
					ts.task.location_id = locID;
				}
				
				// If we get here, we know there were no errors.  Record that a field was processed,
				// and remove the text for the field from the speech.
				speech = speech.replace(matchingText, "");
				numFields++;
				continue;
			}
		}
		
		// If we have a reminder but no start and no due date, then we need to set them.
		if (ts.reminderSet && !ts.startDateSet && !ts.dueDateSet)
		{
			if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true))
			{
				ts.task.start_date = Util.getMidnight(ts.task.reminder);
				ts.startDateSet = true;
			}
			if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true))
			{
				ts.task.due_date = Util.getMidnight(ts.task.reminder);
				ts.dueDateSet = true;
			}
		}
		
		// If nagging is turned on, we need to set either a time-based on location-based nag.
		if (ts.nagSet)
		{
			if (ts.reminderSet)
			{
				ts.task.nag = true;
			}
			else if (ts.locationSet && ts.task.location_reminder)
			{
				ts.task.location_nag = true;
			}
			else
			{
				return "To set up nagging, you need to set a time-based or location-based "+
					"reminder.";
			}
		}
		
		// The next step is to fill in the defaults for any fields that were not specified.
		fillInTaskDefaults(ts);
		
		// Check the task for errors and inconsistencies.  The rules are the same as the regular 
		// task editor.
		
		// Make sure there is a title:
		if (ts.task.title.length()==0)
			return("It looks like you didn't give the task a title.");
		
		// Make sure the title is too long.
		if (ts.task.title.length()>Util.MAX_TASK_TITLE_LENGTH)
			return("I'm sorry, the title of this task is too long.");
		
		// The due date/time must be later than the start date/time.
		if (ts.task.due_date>0 && ts.task.start_date>0 && ts.task.start_date>ts.task.due_date)
			return("The start date must be earlier than the due date.");
		
		// A repeating task must have a start and/or due date:
		if (ts.task.repeat>0 && ts.task.due_date==0 && ts.task.start_date==0)
			return("A repeating task must have a start or due date.");
		
		// The reminder must be before the due date (if a due date is given):
		if (ts.task.due_date>0 && ts.task.reminder>0 && ts.task.reminder>(Util.getMidnight(ts.task.
			due_date)+Util.ONE_DAY_MS))
		{
			return("The reminder must be on or before the due date.");
		}
		
		// For Toodledo compatibility, if a due time is not set then a reminder must be 7 days or
		// less prior to the due date.  This is necessary for syncing.
		if (ts.task.reminder>0 && ts.task.due_date>0 && !ts.task.uses_due_time)
		{
			GregorianCalendar due = new GregorianCalendar(TimeZone.getTimeZone(
                _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
            due.setTimeInMillis(ts.task.due_date);
            GregorianCalendar rem = new GregorianCalendar(TimeZone.getTimeZone(
                _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
            rem.setTimeInMillis(ts.task.reminder);
            int numDays = 0;
            while (rem.before(due))
            {
                rem.add(Calendar.DATE, 1);
                numDays++;
            }
            if (numDays>7)
                return "The reminder date must be within 7 days of the due date.";
		}
		
		// A Google task must be in a folder.
		UTLAccount acct = _accountsDB.getAccount(ts.task.account_id);
		if (acct==null)
			return "The account you specified no longer exists.";
		if (acct.sync_service==UTLAccount.SYNC_GOOGLE && ts.task.folder_id==0)
		{
			return "Google requires that the task be in a folder. You must either "+
				"set a folder in voice mode, or set up a default folder in the settings area.";
		}
		
		// If this is a subtask and this is a Google task, then the parent and child must be in the
		// same folder.
		if (acct.sync_service==UTLAccount.SYNC_GOOGLE && ts.task.parent_id>0)
		{
			UTLTask parent = _tasksDB.getTask(ts.task.parent_id);
			if (parent!=null && parent.folder_id!=ts.task.folder_id)
				return "This subtask must be in the same folder as the parent task.";
		}
		
		// For Toodledo, the "none" priority is not allowed.
		if (acct.sync_service==UTLAccount.SYNC_TOODLEDO && ts.task.priority==0)
			return "Toodledo requires that you give this task a priority.";
		
		// Make sure the chosen folder is in the correct account:
		if (ts.task.folder_id!=0)
		{
			Cursor c = _foldersDB.getFolder(ts.task.folder_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return "The folder you have chosen is not in the task's account.  Try specifying "+
							"the account name when you create the task.";
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen context is in the correct account:
		if (ts.task.context_id!=0)
		{
			Cursor c = _contextsDB.getContext(ts.task.context_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return "The context you have chosen is not in the task's account.  Try specifying "+
							"the account name when you create the task.";
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen goal is in the correct account:
		if (ts.task.goal_id!=0)
		{
			Cursor c = _goalsDB.getGoal(ts.task.goal_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return "The goal you have chosen is not in the task's account.  Try specifying "+
							"the account name when you create the task.";
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen location is in the correct account:
		if (ts.task.location_id!=0)
		{
			UTLLocation loc = _locDB.getLocation(ts.task.location_id);
			if (loc!=null)
			{
				if (loc.account_id!=ts.task.account_id)
				{
					return "The location you have chosen is not in the task's account.  Try specifying "+
						"the account name when you create the task.";
				}
			}
		}
		
		// If linking the task to a calendar entry, make sure the user has chosen a calendar:
		if (ts.addToCalendar && _settings.getLong(PrefNames.LINKED_CALENDAR_ID, -1)==-1)
		{
			return "Before you can show a task on the calendar, you need to choose a calendar in the "+
				"settings area of this app.";
		}
		
		// If adding to the calendar, at least one date must be chosen.
		if (ts.addToCalendar && ts.task.start_date==0 && ts.task.due_date==0)
		{
			return "To add a task to the calendar, you need to provide dates and times.";
		}
		
		// For Toodledo accounts, if the task has a parent specified, make sure the parent is not a 
		// subtask.
		if (acct.sync_service==UTLAccount.SYNC_TOODLEDO && ts.parentSet && ts.task.parent_id>0)
		{
			UTLTask parent = _tasksDB.getTask(ts.task.parent_id);
			if (parent==null)
				return "The parent task you specified no longer exists.";
			if (parent.parent_id>0)
				return "Toodledo accounts do not allow subtasks within subtasks.";
		}
				
		return Integer.valueOf(numFields).toString();
	}
	
	/** Parse speech that corresponds to a task update.
	 * @param speech - The text of the speech.
	 * @return - A UTLTaskSpeech object.  If an error occurs, the error field will be set to a non-empty
	 *    string.
	 */
	protected UTLTaskSpeech parseTaskUpdate(String speech)
	{
		// The result object to be returned:
		UTLTaskSpeech ts = new UTLTaskSpeech();
		
		// Everything must be parsed with case insensitivity.
		speech = speech.toLowerCase();
		
		// Determines if the user's speech matched one of the required patterns.
		boolean matchingPatternFound = false;
		
		// Marking a task complete:
		Matcher mat = Pattern.compile("^mark (?:the task |task |the )?(.+?) (?:task )?(?:as )?complete",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				ts.completedSet = true;
				ts.task.completed = true;
			}
		}
		
		// Starting the timer:
		mat = Pattern.compile("^start (?:the )?timer (?:for|of) (?:task |the task )?+(.+?)$",Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (ts.task.timer_start_time>0)
					ts.error = "The timer is already running for this task.";
				else
				{
					ts.timerSet = true;
					ts.willStartTimer = true;
				}
			}
		}
		
		// Stop the timer:
		mat = Pattern.compile("^stop (?:the )?timer (?:for|of) (?:task |the task )?+(.+?)$",Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (ts.task.timer_start_time==0)
					ts.error = "The timer is not running for this task.";
				else
				{
					ts.timerSet = true;
					ts.willStartTimer = false;
				}
			}
		}
		
		// Postponing tasks:
		mat = Pattern.compile("^postpone (?:task |the task )?+(.+?) (?:until|to|by|for|2) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				long newDate = parseDateTime(mat.group(2));
				if (newDate==0)
					ts.error = "I'm sorry, I don't understand your date ("+mat.group(2)+").";
				else
				{
					long midnight = Util.getMidnight(newDate, _c);
					boolean newDateHasTime = newDate != midnight;
					if (_settings.getBoolean(PrefNames.START_DATE_ENABLED, true) && ts.task.start_date>0)
					{
						if (newDateHasTime)
						{
							if (ts.task.uses_start_time)
								ts.task.start_date = newDate;
							else
								ts.task.start_date = midnight;
						}
						else
						{
							if (ts.task.uses_start_time)
							{
								ts.task.start_date = Util.getBaseCompletionDate(newDate, ts.task.start_date, 
									true);
							}
							else
								ts.task.start_date = midnight;
						}
						ts.startDateSet = true;
					}
					if (_settings.getBoolean(PrefNames.DUE_DATE_ENABLED, true) && ts.task.due_date>0)
					{
						if (newDateHasTime)
						{
							if (ts.task.uses_due_time)
								ts.task.due_date = newDate;
							else
								ts.task.due_date = midnight;
						}
						else
						{
							if (ts.task.uses_due_time)
							{
								ts.task.due_date = Util.getBaseCompletionDate(newDate, ts.task.due_date, 
									true);
							}
							else
								ts.task.due_date = midnight;
						}
						ts.dueDateSet = true;
					}
					if (_settings.getBoolean(PrefNames.REMINDER_ENABLED, true) && ts.task.reminder>0)
					{
						ts.priorReminder = ts.task.reminder;
						if (newDateHasTime)
							ts.task.reminder = newDate;
						else
							ts.task.reminder = Util.getBaseCompletionDate(newDate, ts.task.reminder, true);
						ts.reminderSet = true;
					}
				}
			}
		}
		
		// Altering due dates:
		mat = Pattern.compile("^change (?:the )?due date (?:for|of) (?:task |the task )?+(.+?) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).startsWith("none") || mat.group(2).startsWith("nothing"))
				{
					ts.dueDateSet = true;
					ts.task.due_date = 0;
					ts.task.uses_due_time = false;
				}
				else
				{
					long newDate = parseDateTime(mat.group(2));
					if (newDate==0)
						ts.error = "I'm sorry, I don't understand your date ("+mat.group(2)+").";
					else
					{
						ts.dueDateSet = true;
						long midnight = Util.getMidnight(newDate, _c);
						boolean newDateHasTime = newDate != midnight;
						if (newDateHasTime)
						{
							ts.task.due_date = newDate;
							ts.task.uses_due_time = true;
						}
						else
						{
							if (ts.task.uses_due_time)
							{
								ts.task.due_date = Util.getBaseCompletionDate(newDate, ts.task.due_date, 
									true);
							}
							else
								ts.task.due_date = midnight;
						}
					}
				}
			}
		}
		
		// Altering start dates:
		mat = Pattern.compile("^change (?:the )?start date (?:for|of) (?:task |the task )?+(.+?) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).startsWith("none") || mat.group(2).startsWith("nothing"))
				{
					ts.startDateSet = true;
					ts.task.start_date = 0;
					ts.task.uses_start_time = false;
				}
				else
				{
					long newDate = parseDateTime(mat.group(2));
					if (newDate==0)
						ts.error = "I'm sorry, I don't understand your date ("+mat.group(2)+").";
					else
					{
						ts.startDateSet = true;
						long midnight = Util.getMidnight(newDate, _c);
						boolean newDateHasTime = newDate != midnight;
						if (newDateHasTime)
						{
							ts.task.start_date = newDate;
							ts.task.uses_start_time = true;
						}
						else
						{
							if (ts.task.uses_start_time)
							{
								ts.task.start_date = Util.getBaseCompletionDate(newDate, ts.task.start_date, 
									true);
							}
							else
								ts.task.start_date = midnight;
						}
					}
				}
			}
		}
		
		// Altering reminders:
		mat = Pattern.compile("^change (?:the )?reminder (?:date )?(?:for|of) (?:task |the task )?+(.+?) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).startsWith("none") || mat.group(2).startsWith("nothing"))
				{
					ts.reminderSet = true;
					ts.task.reminder = 0;
					ts.task.nag = false;
				}
				else
				{
					long newDate = parseDateTime(mat.group(2));
					if (newDate==0)
						ts.error = "I'm sorry, I don't understand your date ("+mat.group(2)+").";
					else
					{
						ts.reminderSet = true;
						ts.task.reminder = newDate;
					}
				}
			}
		}
		
		// Repeat pattern:
		mat = Pattern.compile("^change (?:the )?repeat (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				RepeatPattern repeatPattern = parseRepeatPattern(mat.group(2));
				if (repeatPattern==null)
				{
					ts.error = "I'm sorry, I don't understand your repeat pattern ("+mat.group(2)+").";
				}
				else
				{
					ts.repeatSet = true;
					ts.task.repeat = repeatPattern.standardRepeat;
					if (repeatPattern.repeatFromCompletion && repeatPattern.standardRepeat>0)
						ts.task.repeat += 100;
					if (repeatPattern.standardRepeat==50)
						ts.task.rep_advanced = repeatPattern.advancedRepeat;
				}
			}
		}
		
		// Changing the title:
		mat = Pattern.compile("^rename (?:task |the task )?+(?:named )?+(.+?) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				ts.titleSet = true;
				ts.priorTitle = ts.task.title;
				ts.task.title = mat.group(2).substring(0,1).toUpperCase()+mat.group(2).substring(1);
			}
		}
		
		// Change the folder:
		mat = Pattern.compile("^change (?:the )?folder (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).equals("none") || mat.group(2).equals("nothing"))
				{
					ts.folderSet = true;
					ts.task.folder_id = 0;
				}
				else
				{
					long folderID = parseFolder(mat.group(2),ts.task.account_id);
					if (folderID==0)
						ts.error = "I'm sorry, I can't find a folder named \""+mat.group(2)+"\".";
					else
					{
						ts.folderSet = true;
						ts.task.folder_id = folderID;
					}
				}
			}
		}
		
		// Change the context:
		mat = Pattern.compile("^change (?:the )?context (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).equals("none") || mat.group(2).equals("nothing"))
				{
					ts.contextSet = true;
					ts.task.context_id = 0;
				}
				else
				{
					long contextID = parseContext(mat.group(2),ts.task.account_id);
					if (contextID==0)
						ts.error = "I'm sorry, I can't find a context named \""+mat.group(2)+"\".";
					else
					{
						ts.contextSet = true;
						ts.task.context_id = contextID;
					}
				}
			}
		}
		
		// Change the goal:
		mat = Pattern.compile("^change (?:the )?goal (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (.+?)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).equals("none") || mat.group(2).equals("nothing"))
				{
					ts.goalSet = true;
					ts.task.goal_id = 0;
				}
				else
				{
					long goalID = parseGoal(mat.group(2),ts.task.account_id);
					if (goalID==0)
						ts.error = "I'm sorry, I can't find a goal named \""+mat.group(2)+"\".";
					else
					{
						ts.goalSet = true;
						ts.task.goal_id = goalID;
					}
				}
			}
		}
		
		// Change the location:
		mat = Pattern.compile("^change (?:the )?location (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (.+?)$",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (mat.group(2).equals("none") || mat.group(2).equals("nothing"))
				{
					ts.locationSet = true;
					ts.task.location_id = 0;
				}
				else
				{
					long locationID = parseLocation(mat.group(2),ts.task.account_id);
					if (locationID==0)
						ts.error = "I'm sorry, I can't find a location named \""+mat.group(2)+"\".";
					else
					{
						ts.locationSet = true;
						ts.task.location_id = locationID;
					}
				}
			}
		}
		
		// Add a location reminder:
		mat = Pattern.compile("^add (?:a )?location reminder (?:to|2|too) (?:task |the task )?+(.+)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			String match = mat.group(1);
			boolean useNagging = false;
			if (match.contains("with nagging"))
			{
				useNagging = true;
				match = match.substring(0, match.indexOf("with nagging"));
				match = match.trim();
			}
			long taskID = parseTaskTitle(match,false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				if (ts.task.location_id==0)
					ts.error = "This task does not have a location.";
				else
				{
					ts.locationSet = true;
					ts.task.location_reminder = true;
					ts.task.location_nag = useNagging;
				}
			}
		}
		
		// Remove a location reminder:
		mat = Pattern.compile("^remove (?:the )?location reminder from (?:task |the task )?+(.+)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				ts.task.location_reminder = false;
				ts.task.location_nag = false;
				ts.locationSet = true;
			}
		}
		
		// Add a star:
		mat = Pattern.compile("^add (?:a )?star (?:to|2|too) (?:task |the task )?+(.+)$",Pattern.CASE_INSENSITIVE).
			matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				ts.starSet = true;
				ts.task.star = true;
			}
		}
		
		mat = Pattern.compile("^remove (?:the )?star from (?:task |the task )?+(.+)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				ts.starSet = true;
				ts.task.star = false;
			}
		}
		
		// Change Priority:
		mat = Pattern.compile("^change (?:the )?priority (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (no|none|"+
			"nothing|negative|low|medium|high|top)",Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				String p = mat.group(2).toLowerCase();
				if (p.equals("none") || p.equals("no") || p.equals("nothing"))
					ts.task.priority = 0;
				if (p.equals("negative"))
					ts.task.priority = 1;
				if (p.equals("low"))
					ts.task.priority = 2;
				if (p.equals("medium"))
					ts.task.priority = 3;
				if (p.equals("high"))
					ts.task.priority = 4;
				if (p.equals("top"))
					ts.task.priority = 5;
				ts.prioritySet = true;
			}
		}
		
		// Change Status:
		mat = Pattern.compile("^change (?:the )?status (?:for|of) (?:task |the task )?+(.+) (?:to|2|too) (no|none|"+
			"nothing|next action|active|planning|delegated|waiting|hold|"+
			"postponed|someday|canceled|reference)",Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(1),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				String s = mat.group(2).toLowerCase();
				if (s.equals("no") || s.equals("none") || s.equals("nothing"))
					ts.task.status = 0;
				if (s.equals("next action"))
					ts.task.status = 1;
				if (s.equals("active"))
					ts.task.status = 2;
				if (s.equals("planning"))
					ts.task.status = 3;
				if (s.equals("delegated"))
					ts.task.status = 4;
				if (s.equals("waiting"))
					ts.task.status = 5;
				if (s.equals("hold"))
					ts.task.status = 6;
				if (s.equals("postponed"))
					ts.task.status = 7;
				if (s.equals("someday"))
					ts.task.status = 8;
				if (s.equals("canceled"))
					ts.task.status = 9;
				if (s.equals("reference"))
					ts.task.status = 10;
				ts.statusSet = true;
			}
		}
		
		// Add a tag:
		mat = Pattern.compile("^add (?:the )?tag (.+) (?:to|2|too) (?:task |the task )?+(.+)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(2),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				String newTag = mat.group(1);
				String[] currentTags = _tagsDB.getTags(ts.task._id);
				boolean alreadyHasTag = false;
				for (int i=0; i<currentTags.length; i++)
				{
					if (newTag.toLowerCase().equals(currentTags[i].toLowerCase()))
					{
						// Even though the tag is already attached, we don't throw an error.
						// If we did, VoiceCommandConfirm would parse the next speech candidate,
						// which would be successful, but not be the tag the user intended.
						alreadyHasTag = true;
					}
					ts.tags.add(currentTags[i]);
				}
				if (!alreadyHasTag)
					ts.tags.add(newTag);
				ts.tagSet = true;
			}
		}
		
		// Remove a tag:
		mat = Pattern.compile("^remove (?:the )?tag (.+) from (?:task |the task )?+(.+)$",Pattern.
			CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			matchingPatternFound = true;
			long taskID = parseTaskTitle(mat.group(2),false,true,0);
			if (taskID==0)
			{
				ts.error = "I'm sorry, I can't find a task named \""+mat.group(1)+"\".";
			}	
			else
			{
				ts.task = _tasksDB.getTask(taskID);
				String toRemove = mat.group(1);
				String[] currentTags = _tagsDB.getTags(ts.task._id);
				for (int i=0; i<currentTags.length; i++)
				{
					if (toRemove.toLowerCase().equals(currentTags[i].toLowerCase()))
						continue;
					ts.tags.add(currentTags[i]);
				}
				ts.tagSet = true;
			}
		}
		
		if (!matchingPatternFound)
		{
			ts.error = "I'm sorry, I don't understand what you want to do.";
			return ts;
		}
		
		// If an error occured, return the details:
		if (ts.error.length()>0)
			return ts;
		
		// At this point, we know the user said something valid and the task to be modified was found.
		// We just need to run some consistency checks to make sure the task is valid.
		
		// Make sure there is a title:
		if (ts.task.title.length()==0)
			return new UTLTaskSpeech("It looks like you didn't give the task a title.");
		
		// Make sure the title is too long.
		if (ts.task.title.length()>Util.MAX_TASK_TITLE_LENGTH)
			return new UTLTaskSpeech("I'm sorry, the title of this task is too long.");
		
		// The due date/time must be later than the start date/time.
		if (ts.task.due_date>0 && ts.task.start_date>0 && ts.task.start_date>ts.task.due_date)
			return new UTLTaskSpeech("The start date must be earlier than the due date.");
		
		// A repeating task must have a start and/or due date:
		if (ts.task.repeat>0 && ts.task.due_date==0 && ts.task.start_date==0)
			return new UTLTaskSpeech("A repeating task must have a start or due date.");
		
		// The reminder must be before the due date (if a due date is given):
		if (ts.task.due_date>0 && ts.task.reminder>0 && ts.task.reminder>(Util.getMidnight(ts.task.
			due_date)+Util.ONE_DAY_MS))
		{
			return new UTLTaskSpeech("The reminder must be on or before the due date.");
		}
		
		// For Toodledo compatibility, if a due time is not set then a reminder must be 7 days or
		// less prior to the due date.  This is necessary for syncing.
		if (ts.task.reminder>0 && ts.task.due_date>0 && !ts.task.uses_due_time)
		{
			GregorianCalendar due = new GregorianCalendar(TimeZone.getTimeZone(
                _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
            due.setTimeInMillis(ts.task.due_date);
            GregorianCalendar rem = new GregorianCalendar(TimeZone.getTimeZone(
                _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
            rem.setTimeInMillis(ts.task.reminder);
            int numDays = 0;
            while (rem.before(due))
            {
                rem.add(Calendar.DATE, 1);
                numDays++;
            }
            if (numDays>7)
                return  new UTLTaskSpeech("The reminder date must be within 7 days of the due date.");
		}
		
		// A Google task must be in a folder.
		UTLAccount acct = _accountsDB.getAccount(ts.task.account_id);
		if (acct==null)
			return new UTLTaskSpeech("The account you specified no longer exists.");
		if (acct.sync_service==UTLAccount.SYNC_GOOGLE && ts.task.folder_id==0)
		{
			return new UTLTaskSpeech("Google requires that the task be in a folder. You must either "+
				"set a folder in voice mode, or set up a default folder in the settings area.");
		}
		
		// If this is a subtask and this is a Google task, then the parent and child must be in the
		// same folder.
		if (acct.sync_service==UTLAccount.SYNC_GOOGLE && ts.task.parent_id>0)
		{
			UTLTask parent = _tasksDB.getTask(ts.task.parent_id);
			if (parent!=null && parent.folder_id!=ts.task.folder_id)
				return new UTLTaskSpeech("This subtask must be in the same folder as the parent task.");
		}
		
		// For Toodledo, the "none" priority is not allowed.
		if (acct.sync_service==UTLAccount.SYNC_TOODLEDO && ts.task.priority==0)
			return new UTLTaskSpeech("Toodledo requires that you give this task a priority.");
		
		// Make sure the chosen folder is in the correct account:
		if (ts.task.folder_id!=0)
		{
			Cursor c = _foldersDB.getFolder(ts.task.folder_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return new UTLTaskSpeech("The folder you have chosen is not in the task's "+
							"account.");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen context is in the correct account:
		if (ts.task.context_id!=0)
		{
			Cursor c = _contextsDB.getContext(ts.task.context_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return new UTLTaskSpeech("The context you have chosen is not in the task's "+
							"account.");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen goal is in the correct account:
		if (ts.task.goal_id!=0)
		{
			Cursor c = _goalsDB.getGoal(ts.task.goal_id);
			try
			{
				if (c.moveToFirst())
				{
					if (ts.task.account_id!=Util.cLong(c,"account_id"))
					{
						return new UTLTaskSpeech("The goal you have chosen is not in the task's "+
							"account.");
					}
				}
			}
			finally
			{
				c.close();
			}
		}
		
		// Make sure the chosen location is in the correct account:
		if (ts.task.location_id!=0)
		{
			UTLLocation loc = _locDB.getLocation(ts.task.location_id);
			if (loc!=null)
			{
				if (loc.account_id!=ts.task.account_id)
				{
					return new UTLTaskSpeech("The location you have chosen is not in the task's "+
						"account.");
				}
			}
		}
		
		// If linking the task to a calendar entry, make sure the user has chosen a calendar:
		if (ts.addToCalendar && _settings.getLong(PrefNames.LINKED_CALENDAR_ID, -1)==-1)
		{
			return new UTLTaskSpeech("Before you can show a task on the calendar, you need to choose "+
				"a calendar in the settings area of this app.");
		}
		
		// If adding to the calendar, at least one date must be chosen.
		if (ts.addToCalendar && ts.task.start_date==0 && ts.task.due_date==0)
		{
			return new UTLTaskSpeech("To add a task to the calendar, you need to provide dates and "+
				"times.");
		}
		
		// For Toodledo accounts, if the task has a parent specified, make sure the parent is not a 
		// subtask.
		if (acct.sync_service==UTLAccount.SYNC_TOODLEDO && ts.parentSet && ts.task.parent_id>0)
		{
			UTLTask parent = _tasksDB.getTask(ts.task.parent_id);
			if (parent==null)
				return new UTLTaskSpeech("The parent task you specified no longer exists.");
			if (parent.parent_id>0)
				return new UTLTaskSpeech("Toodledo accounts do not allow subtasks within subtasks.");
		}
		
		return ts;
	}

	/** Parse speech that corresponds to a read operation.
	 * @param speech - The text of the speech.
	 * @return An error message in the language used by the subclass.  On success, the
	 *     database ID of the view to be read is returned (as a string).  */
	protected String parseRead(String speech)
	{
		// Everything must be parsed with case insensitivity.
		speech = speech.toLowerCase();
		
		Cursor c;
		
		// The "All Tasks" list:
		Matcher mat = Pattern.compile("^read all tasks", Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.ALL_TASKS, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// The hotlist:
		mat = Pattern.compile("^read (?:my )?hot\\s?list", Pattern.CASE_INSENSITIVE).matcher(
			speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.HOTLIST, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// Due Today/Tomorrow:
		mat = Pattern.compile("^read (?:tasks )?due today (?:and )?tomorrow", Pattern.CASE_INSENSITIVE).
			matcher(speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.DUE_TODAY_TOMORROW, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// Overdue:
		mat = Pattern.compile("^read overdue", Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.OVERDUE, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// Starred:
		mat = Pattern.compile("^read star", Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.STARRED, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// Recently Completed:
		mat = Pattern.compile("^read recently completed", Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			c = _viewsDB.getView(ViewNames.RECENTLY_COMPLETED, "");
			long viewID = Util.cLong(c, "_id");
			c.close();
			return Long.valueOf(viewID).toString();
		}
		
		// Status:
		mat = Pattern.compile("^read (?:tasks in |tasks with )?+(?:the )?+(.+?) status",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.BY_STATUS,mat.group(1));
			if (viewID==0)
				return "There is no status named \""+mat.group(1)+"\".";
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Folders:
		mat = Pattern.compile("^read (?:tasks in |tasks with )?+(?:the )?+(.+?) folder",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.FOLDERS,mat.group(1));
			if (viewID==0)
				return "I'm sorry, I can't find a folder named \""+mat.group(1)+"\".";
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Contexts:
		mat = Pattern.compile("^read (?:tasks in |tasks with )?+(?:the )?+(.+?) context",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.CONTEXTS,mat.group(1));
			if (viewID==0)
				return "I'm sorry, I can't find a context named \""+mat.group(1)+"\".";
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Goals:
		mat = Pattern.compile("^read (?:tasks in |tasks with )?+(?:the )?+(.+?) goal",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.GOALS,mat.group(1));
			if (viewID==0)
				return "I'm sorry, I can't find a goal named \""+mat.group(1)+"\".";
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Locations:
		mat = Pattern.compile("^read (?:tasks in |tasks with |tasks at )?+(?:the )?+(.+?) location",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.LOCATIONS,mat.group(1));
			if (viewID==0)
				return "I'm sorry, I can't find a location named \""+mat.group(1)+"\".";
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Tags:
		mat = Pattern.compile("^read (?:tasks in |tasks with )?+(?:the )?+(.+?) tag",
			Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.TAGS,mat.group(1));
			if (viewID==0)
			{
				return "I'm sorry, I can't find a tag named \""+mat.group(1)+"\" in your list of "+
					"recently used tags.";
			}
			else
				return Long.valueOf(viewID).toString();
		}
		
		// Custom views:
		mat = Pattern.compile("^read my (.+?) (?:view|list)",Pattern.CASE_INSENSITIVE).matcher(speech);
		if (mat.find())
		{
			long viewID = parseViewName(ViewNames.MY_VIEWS,mat.group(1));
			if (viewID==0)
			{
				return "I'm sorry, I can't find a custom view named \""+mat.group(1)+"\".";
			}
			else
				return Long.valueOf(viewID).toString();
		}
		
		// If we get here, then the user has said something we don't understand.
		return "I'm sorry, I don't understand what you want me to read.";
	}

	
	//
	// Methods to parse specific elements (dates, folders, etc.)
	//
	

	/** Parse a string believed to contain a date and/or time.
	 * @param dateStr - A string believed to have a date and/or time.
	 * @return - A timestamp in millis, or 0 if the string cannot be parsed as a date/time.
	 */
	private long parseDateTime(String dateStr)
	{
		dateStr = dateStr.toLowerCase();
		
		String[] dateTime = dateStr.split(" at ");
		if (dateTime.length==2)
		{
			// This is the easy case.  The date is before the "at" and the time is after.
			long dateMillis = parseDate(dateTime[0]);
			long timeMillis = parseTime(dateTime[1]);
			if (dateMillis==0 || timeMillis==-1)
				return 0;
			else
				return dateMillis+timeMillis;
		}
		
		dateTime = dateStr.split(" on ");
		if (dateTime.length==2)
		{
			// In this case the time is before the date.  For example: "4:00 on Tuesday".
			long timeMillis = parseTime(dateTime[0]);
			long dateMillis = parseDate(dateTime[1]);
			if (dateMillis==0 || timeMillis==-1)
				return 0;
			else
				return dateMillis+timeMillis;
		}
		
		Matcher mat = Pattern.compile("^(.+) (today|tomorrow|sunday|monday|tuesday|wednesday|"+
			"thursday|friday|saturday)$",Pattern.CASE_INSENSITIVE).matcher(dateStr);
		if (mat.find())
		{
			// Time is first, followed by date with no connecting word (such as "on").
			long timeMillis = parseTime(mat.group(1));
			long dateMillis = parseDate(mat.group(2));
			if (dateMillis==0 || timeMillis==-1)
				return 0;
			else
				return dateMillis+timeMillis;
		}

		// At this point, it seems that a date OR a time was specified.  Try parsing each, and
		// go with the one that works.
		long millis = parseDate(dateStr);
		if (millis>0)
			return millis;
		millis = parseTime(dateStr);
		if (millis>-1)
		{
			// A time only was specified.  Assume it is a time on today's date:
			return Util.getMidnight(System.currentTimeMillis(),_c)+millis;
		}
		
		// The only other possibility at this point is that the user said something like "in X minutes"
		// or "in X hours".  This will take the current date and time and add the appropriate amount.
		ArrayList<String> words = split(dateStr);
		GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone(
            _settings.getString(PrefNames.HOME_TIME_ZONE,"")));
		c.setTimeInMillis(System.currentTimeMillis());
		if (words.size()>1 && (words.get(1).equals("minutes") || words.get(1).equals("minute")))
		{
			try
			{
				int numMinutes = Integer.parseInt(convertSpelledNumber(words.get(0)));
				return System.currentTimeMillis()+numMinutes*60000;
			}
			catch (NumberFormatException e)
			{
				return 0;
			}
		}
		if (words.size()>1 && (words.get(1).equals("hours") || words.get(1).equals("hour")))
		{
			try
			{
				int numHours = Integer.parseInt(convertSpelledNumber(words.get(0)));
				return System.currentTimeMillis()+numHours*60*60000;
			}
			catch (NumberFormatException e)
			{
				return 0;
			}
		}
		
		// At this point, we have  no idea what the user said.
		return 0;
	}
	
	/** Parse a string believed to be a date.
	 * @param dateStr - A string believed to be the date.
	 * @return - A timestamp in millis, or 0 if the string cannot be parsed as a date.  The timestamp
	 *     will be at midnight on the day.
	 */
	private long parseDate(String dateStr)
	{
		// Everything is done with case insensitivity.
		dateStr = dateStr.toLowerCase();
		
		// We always start with a Calendar object set to today at midnight and then modify as needed.
		GregorianCalendar c = getCalendar();
		setToMidnight(c);
		
		if (dateStr.equals("today"))
			return c.getTimeInMillis();
		
		if (dateStr.equals("tomorrow"))
		{
			c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		
		// Days of the week:
		if (dateStr.equals("sunday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.SUNDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("monday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.MONDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("tuesday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.TUESDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("wednesday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.WEDNESDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("thursday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.THURSDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("friday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.FRIDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		if (dateStr.equals("saturday"))
		{
			c.add(Calendar.DATE, 1);
			while (c.get(Calendar.DAY_OF_WEEK)!=Calendar.SATURDAY)
				c.add(Calendar.DATE, 1);
			return c.getTimeInMillis();
		}
		
		// Month and day (example: June 23rd):
		ArrayList<String> words = split(dateStr);
		String possibleMonth = words.get(0);
		if (possibleMonth.equals("january") || possibleMonth.equals("february") || 
			possibleMonth.equals("march") || possibleMonth.equals("april") ||
			possibleMonth.equals("may") || possibleMonth.equals("june") ||
			possibleMonth.equals("july") || possibleMonth.equals("august") ||
			possibleMonth.equals("september") || possibleMonth.equals("october") ||
			possibleMonth.equals("november") || possibleMonth.equals("december") ||
			possibleMonth.equals("the"))
		{
			if (words.size()==1)
			{
				// We should have had a date here.
				return 0;
			}
			
			// The next word should be a number, but we need to convert sayings like "first", "second"
			// into digits.
			String day = convertSpelledNumber(words.get(1));
			
			// If the day ends with something like "st" (as in "1st") or "nd" (as in "2nd", then remove.
			if (day.endsWith("st"))
				day = day.replace("st","");
			if (day.endsWith("nd"))
				day = day.replace("nd", "");
			if (day.endsWith("rd"))
				day = day.replace("rd", "");
			if (day.endsWith("th"))
				day = day.replace("th", "");
			
			// If the user genuinely said a number, the string should now have a number we can parse.
			try 
			{
				Integer dayNum = Integer.parseInt(day);
				if (possibleMonth.equals("january"))
					c.set(Calendar.MONTH, Calendar.JANUARY);
				if (possibleMonth.equals("february"))
					c.set(Calendar.MONTH, Calendar.FEBRUARY);
				if (possibleMonth.equals("march"))
					c.set(Calendar.MONTH, Calendar.MARCH);
				if (possibleMonth.equals("april"))
					c.set(Calendar.MONTH, Calendar.APRIL);
				if (possibleMonth.equals("may"))
					c.set(Calendar.MONTH, Calendar.MAY);
				if (possibleMonth.equals("june"))
					c.set(Calendar.MONTH, Calendar.JUNE);
				if (possibleMonth.equals("july"))
					c.set(Calendar.MONTH, Calendar.JULY);
				if (possibleMonth.equals("august"))
					c.set(Calendar.MONTH, Calendar.AUGUST);
				if (possibleMonth.equals("september"))
					c.set(Calendar.MONTH, Calendar.SEPTEMBER);
				if (possibleMonth.equals("october"))
					c.set(Calendar.MONTH, Calendar.OCTOBER);
				if (possibleMonth.equals("november"))
					c.set(Calendar.MONTH, Calendar.NOVEMBER);
				if (possibleMonth.equals("december"))
					c.set(Calendar.MONTH, Calendar.DECEMBER);
				if (possibleMonth.equals("the"))
				{
					// Either this month or the next, depending on the current date.
					if (dayNum <= c.get(Calendar.DAY_OF_MONTH))
						c.add(Calendar.MONTH, 1);
				}
				c.set(Calendar.DAY_OF_MONTH, dayNum);
				
				if (words.size()>2 && words.get(2).matches("^\\d+$") && words.get(2).length()==4)
				{
					// There is another word here, which must be the year.
					Integer year = Integer.parseInt(words.get(2));
					c.set(Calendar.YEAR, year);
				}
				else
				{
					// We have a month and day.  If the resulting date is in the past, add a year.
					if (c.getTimeInMillis()<System.currentTimeMillis())
						c.add(Calendar.YEAR, 1);
				}
				
				return c.getTimeInMillis();
			}
			catch (NumberFormatException e)
			{
				// Since we can't recognize the number, we have to return a zero to the caller to indicate
				// we can't parse this date.
				return 0;
			}
		}
		
		// In X days, weeks, or months.  Note that the word "in" will not be passed in.
		if (words.size()>1 && (words.get(1).equals("days") || words.get(1).equals("day") ||
			words.get(1).equals("weeks") || words.get(1).equals("week") ||
			words.get(1).equals("months") || words.get(1).equals("month") ||
			words.get(1).equals("years") || words.get(1).equals("year")))
		{
			// Convert spelled out numbers to digits.
			String countStr = convertSpelledNumber(words.get(0));
			
			// We should have a count that can be parsed at this point.
			try
			{
				int count = Integer.parseInt(countStr);
				if (words.get(1).equals("days") || words.get(1).equals("day"))
					c.add(Calendar.DATE, count);
				if (words.get(1).equals("weeks") || words.get(1).equals("week"))
					c.add(Calendar.WEEK_OF_YEAR, count);
				if (words.get(1).equals("months") || words.get(1).equals("month"))
					c.add(Calendar.MONTH, count);
				if (words.get(1).equals("years") || words.get(1).equals("year"))
					c.add(Calendar.YEAR, count);
				return c.getTimeInMillis();
			}
			catch (NumberFormatException e)
			{
				// Since we can't recognize the number, we have to return a zero to the caller to indicate
				// we can't parse this date.
				return 0;
			}
		}
		
		// At this point, we have no idea what the user said.
		return 0;
	}
	
	/** Parse a string believed to be a time. 
	 * @param timeStr - A string believed to be the time.
	 * @return - The number of milliseconds since midnight, or -1 if the time cannot be parsed. */
	private long parseTime(String timeStr)
	{
		boolean amPmFound = false;
		boolean isPm = false;
		int hours;
		int mins;
		timeStr = timeStr.toLowerCase();
		
		if (timeStr.equals("midnight"))
			return 0;
		
		if (timeStr.equals("noon"))
		{
			return Util.ONE_DAY_MS/2;
		}
		
		// Strip the word "O'Clock" from the string:
		timeStr = timeStr.replace("o'clock", "");
		timeStr = timeStr.replace("oclock", "");
		
		// See if an AM or PM was specified.  If so, record this and strip it out of the string to make
		// parsing easier.
		if (timeStr.endsWith("am") || timeStr.endsWith("a.m.") || timeStr.endsWith("a_m.") ||
			timeStr.endsWith("a_m") || timeStr.endsWith("a.m"))
		{
			amPmFound = true;
			isPm = false;
			timeStr = timeStr.replace("am", "");
			timeStr = timeStr.replace("a.m.", "");
			timeStr = timeStr.replace("a_m.","");
			timeStr = timeStr.replace("a_m","");
			timeStr = timeStr.replace("a.m", "");
		}
		if (timeStr.endsWith("pm") || timeStr.endsWith("p.m.") || timeStr.endsWith("p_m.") ||
			timeStr.endsWith("p_m") || timeStr.endsWith("p.m"))
		{
			amPmFound = true;
			isPm = true;
			timeStr = timeStr.replace("pm", "");
			timeStr = timeStr.replace("p.m.", "");
			timeStr = timeStr.replace("p_m.","");
			timeStr = timeStr.replace("p_m","");
			timeStr = timeStr.replace("p.m", "");
		}
		timeStr = timeStr.trim();
		
		ArrayList<String> words = split(timeStr);
		if (Util.regularExpressionMatch(words.get(0),":"))
		{
			// Hours are before the colon, minutes are after.
			String[] hoursMins = words.get(0).split(":");
            if (hoursMins.length>1)
            {
                try
                {
                    hours = Integer.parseInt(hoursMins[0]);
                    mins = Integer.parseInt(hoursMins[1]);
                }
                catch (NumberFormatException e)
                {
                    return -1;
                }
            }
            else
                return -1;
		}
		else if (words.get(0).length()>2 && words.get(0).length()<=4 && Util.regularExpressionMatch(
			words.get(0), "^\\d+$"))
		{
			// This must be something like "0830" or "305".  Assume the last 2 digits are the minutes
			// and the first one or two are the hours.
			String minStr = words.get(0).substring(words.get(0).length()-2);
			String hourStr = words.get(0).substring(0, words.get(0).length()-2);
			try
			{
				hours = Integer.parseInt(hourStr);
				mins = Integer.parseInt(minStr);
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		else if (words.size()>=2 && Util.regularExpressionMatch(words.get(0), "^\\d+$") &&
			Util.regularExpressionMatch(words.get(1), "^\\d+$"))
		{
			// Hour and minute must be in 2 separate words.
			try
			{
				hours = Integer.parseInt(words.get(0));
				mins = Integer.parseInt(words.get(1));
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		else if (words.size()>=2)
		{
			// Perhaps the hours and/or minutes are spelled out.
			String hoursStr = convertSpelledNumber(words.get(0));
			String minsStr = convertSpelledNumber(words.get(1));
			try
			{
				hours = Integer.parseInt(hoursStr);
				mins = Integer.parseInt(minsStr);
			}
			catch (NumberFormatException e)
			{
				return -1;
			}
		}
		else if (words.size()==1 && words.get(0).matches("^\\d+$"))
		{
			// Just an hour was given.  Set the minutes to zero.
			hours = Integer.parseInt(words.get(0));
			mins = 0;
		}
		else
		{
			// The string is not understood.
			return -1;
		}
		
		// At this point, we have an hours and minutes as integers, along with information on whether
		// am/pm was specified.  This is all we need to return a result.  Start by converting the hours
		// to 24 hour time.
		if (hours>24)
			return -1;
		else if (hours==12)
		{
			if (amPmFound && isPm==false)
				hours = 0;
		}
		else if (hours==24 || hours==0)
			hours = 0;
		else if (hours<12)
		{
			if (amPmFound)
			{
				if (isPm)
					hours += 12;
			}
			else
			{
				// Need to make an intelligent guess.
				if (hours<7)
					hours += 12;
			}
		}
		return 60000*(hours*60+mins);
	}
	
	/** This object is returned by the parseRepeat method. It contains information on the repeating 
	 * pattern. */
	private class RepeatPattern
	{
		public int standardRepeat; // 0-9, or 50 for advanced repeat.
		public String advancedRepeat; // A string in a standard format.
		public boolean repeatFromCompletion; // true if we're repeating from the completion date instead of due.
	}
	
	/** Parse a string believed to be a repeating pattern.
	 * @param repeatStr - A string believed to be a repeating pattern.
	 * @return - An instance of RepeatPattern, or null if the string cannot be parsed.
	 */
	private RepeatPattern parseRepeatPattern(String repeatStr)
	{
		repeatStr = repeatStr.toLowerCase().trim();
		RepeatPattern repeatPattern = new RepeatPattern();
		repeatPattern.repeatFromCompletion = false;
		
		// Check to see if the user stated the pattern should be from the due date or completion date.
		if (repeatStr.endsWith("from due date"))
		{
			repeatPattern.repeatFromCompletion = false;
			repeatStr = repeatStr.replace("from due date", "");
		}
		if (repeatStr.endsWith("from completion date"))
		{
			repeatPattern.repeatFromCompletion = true;
			repeatStr = repeatStr.replace("from completion date", "");
		}
		repeatStr = repeatStr.trim();
		
		// Look for the basic options:
		
		if (repeatStr.equals("none") || repeatStr.equals("nothing"))
		{
			repeatPattern.standardRepeat = 0;
			return repeatPattern;
		}
		
		if (repeatStr.equals("weekly") || repeatStr.equals("every week"))
		{
			repeatPattern.standardRepeat = 1;
			return repeatPattern;
		}
		
		if (repeatStr.equals("monthly") || repeatStr.equals("every month"))
		{
			repeatPattern.standardRepeat = 2;
			return repeatPattern;
		}
		
		if (repeatStr.equals("yearly") || repeatStr.equals("every year"))
		{
			repeatPattern.standardRepeat = 3;
			return repeatPattern;
		}
		
		if (repeatStr.equals("daily") || repeatStr.equals("every day"))
		{
			repeatPattern.standardRepeat = 4;
			return repeatPattern;
		}
		
		if (repeatStr.equals("biweekly") || repeatStr.equals("bi-weekly"))
		{
			repeatPattern.standardRepeat = 5;
			return repeatPattern;
		}
		
		if (repeatStr.equals("bimonthly") || repeatStr.equals("bi-monthly"))
		{
			repeatPattern.standardRepeat = 6;
			return repeatPattern;
		}
		
		if (repeatStr.equals("semiannually") || repeatStr.equals("semi-annually"))
		{
			repeatPattern.standardRepeat = 7;
			return repeatPattern;
		}
		
		if (repeatStr.equals("quarterly"))
		{
			repeatPattern.standardRepeat = 8;
			return repeatPattern;
		}
		
		if (repeatStr.equals("with parent"))
		{
			repeatPattern.standardRepeat = 9;
			return repeatPattern;
		}
		
		// Look for advanced repeat options:
		
		Pattern pattern = Pattern.compile("every (\\d+) (day|days|week|weeks|month|months|year|years)",
			Pattern.CASE_INSENSITIVE);
		Matcher mat = pattern.matcher(repeatStr);
		if (mat.find())
		{
			repeatPattern.standardRepeat = 50;
			repeatPattern.advancedRepeat = mat.group();
			return repeatPattern;
		}
		
		pattern = Pattern.compile("the (\\d+|last|first|second|third|fourth|fifth) (sunday|monday|"+
			"tuesday|wednesday|thursday|friday|saturday) of (?:each|every) month",
			Pattern.CASE_INSENSITIVE);
		mat = pattern.matcher(repeatStr);
		if (mat.find())
		{
			String dayNum = convertSpelledNumber(mat.group(1));
			repeatPattern.standardRepeat = 50;
			repeatPattern.advancedRepeat = "the "+dayNum+" "+mat.group(2)+" of each month";
			return repeatPattern;
		}
		
		pattern = Pattern.compile("every (sunday|monday|tuesday|wednesday|thursday|friday|saturday|"+
			"weekday|weekend)",Pattern.CASE_INSENSITIVE);
		mat = pattern.matcher(repeatStr);
		if (mat.find())
		{
			String daysOfWeek = repeatStr.substring(mat.start(1));
			daysOfWeek = daysOfWeek.replace("and", "");
			String[] daysArray = daysOfWeek.split("[, ]+");
			repeatPattern.standardRepeat = 50;
			repeatPattern.advancedRepeat = "every ";
			pattern = Pattern.compile("(sunday|monday|tuesday|wednesday|thursday|friday|saturday|"+
				"weekday|weekend)",pattern.CASE_INSENSITIVE);
			for (int i=0; i<daysArray.length; i++)
			{
				mat = pattern.matcher(daysArray[i]);
				if (mat.find())
				{
					if (i>0)
						repeatPattern.advancedRepeat += ", ";
					repeatPattern.advancedRepeat += daysArray[i];
				}
			}
			return repeatPattern;
		}
		
		// No matching strings found that represent a repeat pattern.
		return null;
	}
	
	/** Convert a spelled out number into a digit. 
	 * @param numberStr - The string that may contain a spelled out number. 
	 * @return A string that was possibly converted to a digit.  If no conversion is done, the original
	 *     string is returned. */
	private String convertSpelledNumber(String numberStr)
	{
		if (numberStr.equals("first") || numberStr.equals("one"))
			numberStr ="1";
		else if (numberStr.equals("second") || numberStr.equals("two"))
			numberStr ="2";
		else if (numberStr.equals("third") || numberStr.equals("three"))
			numberStr ="3";
		else if (numberStr.equals("fourth") || numberStr.equals("four"))
			numberStr ="4";
		else if (numberStr.equals("fifth") || numberStr.equals("five"))
			numberStr ="5";
		else if (numberStr.equals("sixth") || numberStr.equals("six"))
			numberStr ="6";
		else if (numberStr.equals("seventh") || numberStr.equals("seven"))
			numberStr ="7";
		else if (numberStr.equals("eighth") || numberStr.equals("eight"))
			numberStr ="8";
		else if (numberStr.equals("ninth") || numberStr.equals("nine"))
			numberStr ="9";
		else if (numberStr.equals("tenth") || numberStr.equals("ten"))
			numberStr ="10";
		else if (numberStr.equals("eleventh") || numberStr.equals("eleven"))
			numberStr ="11";
		else if (numberStr.equals("twelfth") || numberStr.equals("twelve"))
			numberStr ="12";
		else if (numberStr.equals("thirteenth") || numberStr.equals("thirteen"))
			numberStr ="13";
		else if (numberStr.equals("fourteenth") || numberStr.equals("fourteen"))
			numberStr ="14";
		else if (numberStr.equals("fifteenth") || numberStr.equals("fifteen"))
			numberStr ="15";
		else if (numberStr.equals("sixteenth") || numberStr.equals("sixteen"))
			numberStr ="16";
		else if (numberStr.equals("seventeenth") || numberStr.equals("seventeen"))
			numberStr ="17";
		else if (numberStr.equals("eighteenth") || numberStr.equals("eighteen"))
			numberStr ="18";
		else if (numberStr.equals("nineteenth") || numberStr.equals("nineteen"))
			numberStr ="19";
		else if (numberStr.equals("twenty"))
			numberStr = "20";
		else if (numberStr.equals("thirty"))
			numberStr = "30";
		else if (numberStr.equals("forty"))
			numberStr = "40";
		else if (numberStr.equals("fifty"))
			numberStr = "50";
		else if (numberStr.equals("sixty"))
			numberStr = "60";
		else if (numberStr.equals("seventy"))
			numberStr = "70";
		else if (numberStr.equals("eighty"))
			numberStr = "80";
		else if (numberStr.equals("ninety"))
			numberStr = "90";
		return numberStr;
	}
}
