package com.customsolutions.android.utl;

import java.util.ArrayList;
import java.util.HashSet;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

public class AndroidAgendaProvider extends ContentProvider
{
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri)
	{
		if (uri.getPath()!=null && uri.getPath().endsWith("tasks"))
		{
			return "vnd.android.cursor.dir/vnd.customsolutions.tasks";
		}
		else if (uri.getPath()!=null && uri.getPath().endsWith("folders"))
		{
			return "vnd.android.cursor.dir/vnd.customsolutions.tasks";
		}
		else
		{
			return "vnd.android.cursor.item/vnd.customsolutions.tasks";
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate()
	{
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
		String[] selectionArgs, String sortOrder)
	{
		// Initialize the app if it has not been:
		Util.appInit(this.getContext());
		
		// Initialize the app if it has not been:
		SQLiteDatabase db = new DatabaseHelper(this.getContext()).getWritableDatabase();
		
		if (uri.getPath()!=null && uri.getPath().endsWith("tasks"))
		{
			// We need a hash of fields supported by the database:
			HashSet<String> supportedFields = new HashSet<String>();
			for (int i=0; i<TasksDbAdapter.COLUMNS.length; i++)
			{
				supportedFields.add(TasksDbAdapter.COLUMNS[i]);
			}
			
			// Break up the projection into 2 segments: fields supported by the database
			// and those that are not:
			ArrayList<String> dbFields = new ArrayList<String>();
			ArrayList<String> nonDbFields = new ArrayList<String>();
			for (int i=0; i<projection.length; i++)
			{
				if (supportedFields.contains(projection[i]))
					dbFields.add(projection[i]);
				else
					nonDbFields.add(projection[i]);
			}
			
			// Run the query and get a Cursor with the results:
 			Cursor c = db.query("tasks", Util.iteratorToStringArray(dbFields.iterator(), 
 				dbFields.size()), selection, selectionArgs, null, null, 
				sortOrder);
 			
 			// Create a string array that has the database fields listed first, and the
 			// non-database fields listed last:
 			int arrayLength = dbFields.size() + nonDbFields.size();
 			String[] matrixCursorFields = new String[arrayLength];
 			for (int i=0; i<arrayLength; i++)
 			{
 				if (i<dbFields.size())
 				{
 					matrixCursorFields[i] = dbFields.get(i);
 				}
 				else
 				{
 					matrixCursorFields[i] = nonDbFields.get(i-dbFields.size());
 				}
 			}
 			
 			// This MatrixCursor will hold the results of the query:
 			int[] priorityColors = this.getContext().getResources().getIntArray(R.array.priority_colors);
 			MatrixCursor result = new MatrixCursor(matrixCursorFields);
 			while (c.moveToNext())
 			{
 				long taskID = Util.cLong(c, "_id");
 				Object values[] = new Object[matrixCursorFields.length];
 				
 				// Fill in the database fields:
 				for (int i=0; i<dbFields.size(); i++)
 				{
 					values[i] = c.getString(i);
 				}
 				
 				// Fill in the non-database fields, based on the type:
 				UTLTask t = (new TasksDbAdapter()).getTask(taskID);
 				for (int i=dbFields.size(); i<matrixCursorFields.length; i++)
 				{
 					if (result.getColumnName(i).equals("subtask_level"))
 					{
 						values[i] = getSubtaskLevel(t);
 					}
 					else if (result.getColumnName(i).equals("color"))
 					{
 						values[i] = priorityColors[t.priority];
 					}
 				}
 				
 				result.addRow(values);
 			}
 			
 			c.close();
 			return result;
		}
		else if (uri.getPath()!=null && uri.getPath().endsWith("folders"))
		{
			return db.query("folders", projection, selection, selectionArgs, null, null, 
				sortOrder);
		}
		else
		{
			Util.log("Bad URI from Android Agenda: "+uri.toString());
			return null;
		}
		
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
		String[] selectionArgs)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	// Given a task, get its subtask level (0=root):
	private int getSubtaskLevel(UTLTask t)
	{
		TasksDbAdapter tasksDB = new TasksDbAdapter();
		int level = 0;
		while (t!=null && t.parent_id>0)
		{
			t = tasksDB.getTask(t.parent_id);
			level++;
		}
		return level;
	}

}
