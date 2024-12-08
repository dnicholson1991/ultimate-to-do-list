package com.customsolutions.android.utl;

import android.support.wearable.view.DefaultOffsettingHelper;
import android.support.wearable.view.WearableRecyclerView;
import android.view.View;

/**
 * This is responsible for scaling items in a list based on how far off center they are.
 */

public class ScalingOffsettingHelper extends DefaultOffsettingHelper
{
    /** How much should we scale the view at most. */
    private static final float MAX_ICON_PROGRESS = 0.65f;

    /** The X pivot value for scaling.*/
    private int _pivotX;

    public ScalingOffsettingHelper(int pivotX)
    {
        _pivotX = pivotX;
    }

    @Override

    public void updateChild(View child,  WearableRecyclerView parent)
    {
        super.updateChild(child, parent);

        // Figure out % progress from top to bottom
        float centerOffset = ((float) child.getHeight() / 2.0f) /  (float) parent.getHeight();
        float yRelativeToCenterOffset = (child.getY() / parent.getHeight()) + centerOffset;

        // Normalize for center
        float progressToCenter = Math.abs(0.5f - yRelativeToCenterOffset);

        // Adjust to the maximum scale
        progressToCenter = Math.min(progressToCenter, MAX_ICON_PROGRESS);

        child.setPivotX(_pivotX);
        child.setScaleX(1 - progressToCenter);
        child.setScaleY(1 - progressToCenter);
    }
}
