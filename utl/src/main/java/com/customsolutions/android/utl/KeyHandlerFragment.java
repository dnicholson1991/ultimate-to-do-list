package com.customsolutions.android.utl;

import android.view.KeyEvent;

/** This interface can be added to a Fragment to indicate that it handles keystrokes. */
public interface KeyHandlerFragment
{
	/** The key handler.  Returns true if handled and no further processing should occur, else false. */
	public boolean onKeyDown(int keyCode, KeyEvent event);
}
