package com.peekandpop.shalskar.peekandpop.model;

import android.view.View;

/**
 * Created by Vincent on 9/01/2016.
 */
public class LongHoldView {

    private View view;

    private long holdStart;

    public LongHoldView(View view, int holdStart) {
        this.view = view;
        this.holdStart = holdStart;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public long getHoldStart() {
        return holdStart;
    }

    public void setHoldStart(long holdStart) {
        this.holdStart = holdStart;
    }
}
