package com.peekandpop.shalskar.peekandpop.model;

import android.view.View;

import java.util.Timer;

/**
 * Created by Vincent on 9/01/2016.
 */
public class HoldAndReleaseView {

    private View view;

    private int position;

    protected Timer longHoldTimer;

    public HoldAndReleaseView(View view) {
        this.view = view;
        this.position = -1;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public Timer getLongHoldTimer() {
        return longHoldTimer;
    }

    public void setLongHoldTimer(Timer longHoldTimer) {
        this.longHoldTimer = longHoldTimer;
    }
}
