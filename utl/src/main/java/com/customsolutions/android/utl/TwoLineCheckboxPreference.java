package com.customsolutions.android.utl;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * This implements a standard SwitchPreference, but allows for a second line of text in the
 * preference title.
 */
public class TwoLineCheckboxPreference extends SwitchPreference
{
    public TwoLineCheckboxPreference (Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context,attrs,defStyleAttr);
    }

    public TwoLineCheckboxPreference (Context context, AttributeSet attrs)
    {
        super(context,attrs);
    }

    public TwoLineCheckboxPreference (Context context)
    {
        super(context);
    }

    @Override
    protected void onBindView(View view)
    {
        super.onBindView(view);

        TextView textView = (TextView) view.findViewById(android.R.id.title);
        if (textView != null)
        {
            textView.setSingleLine(false);
            textView.setMaxLines(2);
            textView.setEllipsize(TextUtils.TruncateAt.END);
        }
    }
}
