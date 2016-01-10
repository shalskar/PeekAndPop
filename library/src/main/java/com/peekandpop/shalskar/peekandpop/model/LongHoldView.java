package com.peekandpop.shalskar.peekandpop.model;

import android.view.View;

import java.util.Timer;

/**
 * Created by Vincent on 9/01/2016.
 */
public class LongHoldView {

    private View view;

    protected Timer longHoldTimer;

    private boolean receiveMultipleEvents;

    public LongHoldView(View view, boolean receiveMultipleEvents) {
        this.view = view;
        this.receiveMultipleEvents = receiveMultipleEvents;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
    }

    public boolean isReceiveMultipleEvents() {
        return receiveMultipleEvents;
    }

    public void setReceiveMultipleEvents(boolean receiveMultipleEvents) {
        this.receiveMultipleEvents = receiveMultipleEvents;
    }

    public Timer getLongHoldTimer() {
        return longHoldTimer;
    }

    public void setLongHoldTimer(Timer longHoldTimer) {
        this.longHoldTimer = longHoldTimer;
    }
}
