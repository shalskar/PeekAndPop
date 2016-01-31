package com.peekandpop.shalskar.peekandpop.model;

import android.support.annotation.NonNull;
import android.view.View;

import com.peekandpop.shalskar.peekandpop.PeekAndPop;

import java.util.Timer;
import java.util.TimerTask;

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

    /**
     * Sets a timer on the long hold view that will send a long hold event after the duration
     * If receiveMultipleEvents is true, it will set another timer directly after for the duration * 1.5
     *
     * @param position
     * @param duration
     */
    public void startLongHoldViewTimer(@NonNull final PeekAndPop peekAndPop, final int position, final long duration) {
        final Timer longHoldTimer = new Timer();
        longHoldTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                peekAndPop.sendOnLongHoldEvent(view, position);
                if (receiveMultipleEvents) {
                    startLongHoldViewTimer(peekAndPop, position, (long) (duration));
                }
            }
        }, duration);

        this.longHoldTimer = longHoldTimer;
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
