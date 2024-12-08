package com.customsolutions.android.utl;

import android.content.Context;
import androidx.appcompat.widget.ActionMenuView;
import androidx.appcompat.widget.Toolbar;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.bottomappbar.BottomAppBar;

/** A toolbar that will space the icons out evenly. */
public class SplitToolbar extends BottomAppBar
{
    public SplitToolbar(Context context)
    {
        super(context);
    }

    public SplitToolbar(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    public SplitToolbar(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params)
    {
        if (child instanceof ActionMenuView)
        {
            params.width = LayoutParams.MATCH_PARENT;
        }
        super.addView(child, params);
    }
}
