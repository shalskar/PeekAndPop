package com.peekandpop.shalskar.peekandpop;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.View;

/**
 * Created by Vincent on 30/01/2016.
 */
public class DimensionUtil {

    public static int convertDpToPx(@NonNull Context context, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static boolean pointInViewBounds(@NonNull View view, int x, int y) {
        int[] l = new int[2];
        view.getLocationOnScreen(l);
        int viewX = l[0];
        int viewY = l[1];
        int viewWidth = view.getWidth();
        int viewHeight = view.getHeight();

        if (x < viewX || x > viewX + viewWidth || y < viewY || y > viewY + viewHeight) {
            return false;
        }
        return true;
    }

}
