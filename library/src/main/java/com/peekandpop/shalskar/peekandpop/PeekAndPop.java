package com.peekandpop.shalskar.peekandpop;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

public class PeekAndPop {

    //todo convert px to dpi
    protected static final int DRAG_AMOUNT = 256;
    protected static final int DRAG_TO_ACTION_THRESHOLD = 64;
    protected static final int DRAG_TO_ACTION_VIEW_MARGIN = 32;
    protected static final int DRAG_TO_ACTION_MOVE_AMOUNT = 256;

    protected int maxDrag;

    protected Builder builder;
    protected ViewGroup containerView;
    protected View baseView;
    protected ViewGroup peekLayout;
    protected View peekView;

    protected View dragToActionViewLayout;

    protected DragToActionListener dragToActionListener;
    protected GeneralActionListener generalActionListener;

    protected float[] peekViewOriginalPosition;
    protected float[] peekViewOriginalRawPosition;
    protected float initialTouchOffset = -1;

    protected boolean hasEnteredPeekViewBounds = false;

    protected int orientation;

    public PeekAndPop(Builder builder) {
        this.builder = builder;
        init();
    }

    protected void init() {
        this.baseView = builder.baseView;
        this.containerView = builder.containerView;

        this.dragToActionListener = builder.dragToActionListener;
        this.generalActionListener = builder.generalActionListener;

        orientation = builder.context.getResources().getConfiguration().orientation;

        createPeekView();
        initialiseGestureListeners();
    }

    /**
     * Inflate the peekView, add it to the peekLayout with a shaded/blurred background,
     * bring it to the front and set the peekLayout to have an alpha of 0. Get the peekView's
     * original Y position for use when dragging.
     * <p>
     * If a dragToActionViewLayout is supplied, inflate the dragToActionViewLayout
     */
    protected void createPeekView() {
        LayoutInflater inflater = LayoutInflater.from(builder.context);

        // Center peek view in the peek layout and add to the container view group
        peekLayout = (RelativeLayout) inflater.inflate(R.layout.peek_background, containerView, false);

        peekView = inflater.inflate(builder.peekLayoutId, peekLayout, false);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) peekView.getLayoutParams();
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        peekLayout.addView(peekView, layoutParams);

        containerView.addView(peekLayout);

        // If lollipop or above, use elevation to bring peek layout to the front
        if (Build.VERSION.SDK_INT >= 21) {
            peekLayout.setElevation(100f);
        } else {
            peekLayout.bringToFront();
            containerView.requestLayout();
            containerView.invalidate();
        }
        peekLayout.setAlpha(0);

        // Once the peek view has inflated fully, get it's original y position to be used later
        peekView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getPeekViewOriginalPosition();
                getMaxDrag();
            }
        });

        if (builder.dragToActionViewLayout != -1) {
            addDragToActionLayout(inflater);
        }

    }

    /**
     * Adds a dragToActionViewLayout centered at the bottom of the screen
     * Also inflates the dragToActionView and revealView if applicable
     *
     * @param inflater
     */
    private void addDragToActionLayout(LayoutInflater inflater) {
        dragToActionViewLayout = inflater.inflate(builder.dragToActionViewLayout, peekLayout, false);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) dragToActionViewLayout.getLayoutParams();
        // todo possibly change this R.id.peek value to a parameter in the builder
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            layoutParams.bottomMargin = DRAG_TO_ACTION_VIEW_MARGIN;
        } else {
            layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            layoutParams.rightMargin = DRAG_TO_ACTION_VIEW_MARGIN;
        }

        peekLayout.addView(dragToActionViewLayout, layoutParams);
        dragToActionViewLayout.setScaleX(0);
        dragToActionViewLayout.setScaleY(0);

    }

    /**
     * Set an onLongClick, onClick and onTouch listener for the click view
     */
    protected void initialiseGestureListeners() {
        baseView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                peek();
                return false;
            }
        });

        baseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        baseView.setOnTouchListener(clickViewOnTouchListener);
    }

    /**
     * Check if user has moved or lifted their finger.
     * <p>
     * If lifted, pop the view and check if their is a drag to action listener, check
     * if it had been dragged enough and send an event if so.
     * <p>
     * If moved, check if the user has entered the bounds of the peek view.
     * If the user is within the bounds, and is at the edges of the view, then
     * move it appropriately
     */
    private View.OnTouchListener clickViewOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                pop();
                if (dragToActionListener != null) {
                    checkIfDraggedToAction();
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && dragToActionListener != null) {
                if (hasEnteredPeekViewBounds) {
                    if (initialTouchOffset == -1)
                        initialTouchOffset = calculateOffset((int) event.getRawX(), (int) event.getRawY());
                    movePeekView((int) event.getRawX(), (int) event.getRawY());
                } else if (inViewBounds(peekView, (int) event.getRawX(), (int) event.getRawY())) {
                    hasEnteredPeekViewBounds = true;
                    Log.d("PeekAndPop", "has entered peek view bounds");
                }
            }
            return false;
        }
    };

    /**
     * Check if the peek view has been dragged passed the drag to action threshold and
     * send a dragged to action event if it has.
     */
    private void checkIfDraggedToAction() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (peekView.getY() < maxDrag + DRAG_TO_ACTION_THRESHOLD)
                dragToActionListener.draggedToAction();
        } else {
            if (peekView.getX() < maxDrag + DRAG_TO_ACTION_THRESHOLD)
                dragToActionListener.draggedToAction();
        }
    }

    private void getPeekViewOriginalPosition() {
        peekViewOriginalPosition = new float[2];
        peekViewOriginalPosition[0] = peekView.getX();
        peekViewOriginalPosition[1] = peekView.getY();
        int[] l = new int[2];
        peekView.getLocationOnScreen(l);
        peekViewOriginalRawPosition = new float[2];
        peekViewOriginalRawPosition[0] = l[0];
        peekViewOriginalRawPosition[1] = l[1];
    }

    /**
     * Calculate the max drag position, using the x/y position and the amount of
     * drag.
     */
    // todo clarify whether this is the best way to do it
    private void getMaxDrag() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxDrag = (int) peekView.getY() - DRAG_AMOUNT;
        } else {
            maxDrag = (int) peekView.getX() - DRAG_AMOUNT;
        }

        // todo change to exception
        if (maxDrag < 0) {
            maxDrag = 0;
            //throw new PeekViewTooLargeException();
        }
    }

    private int calculateOffset(int touchX, int touchY) {
        int[] l = new int[2];
        peekView.getLocationOnScreen(l);
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return touchY - l[1];
        } else {
            return touchX - l[0];
        }
    }

    private void peek() {
        this.generalActionListener.peek();
        // todo fix this and reimplement
//        if (Build.VERSION.SDK_INT >= 17) {
//            blurBackground();
//        }
        peekLayout.setAlpha(1f);

        Animation peekViewAnimation = AnimationUtils.loadAnimation(builder.context, R.anim.peek);
        peekView.startAnimation(peekViewAnimation);

        Animation peekBackgroundAnimation = AnimationUtils.loadAnimation(builder.context, R.anim.fade_in);
        peekLayout.startAnimation(peekBackgroundAnimation);

        if (builder.parentViewGroup != null) {
            builder.parentViewGroup.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void pop() {
        this.generalActionListener.pop();
        resetViews();

        Animation popAnimation = AnimationUtils.loadAnimation(builder.context, R.anim.pop);
        popAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                peekLayout.setAlpha(0f);
                peekView.setX(peekViewOriginalPosition[0]);
                peekView.setY(peekViewOriginalPosition[1]);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        peekView.startAnimation(popAnimation);

        Animation fadeOutAnimation = AnimationUtils.loadAnimation(builder.context, R.anim.fade_out);
        peekLayout.startAnimation(fadeOutAnimation);

        if (dragToActionViewLayout != null) {
            dragToActionViewLayout.startAnimation(fadeOutAnimation);
        }
    }

    /**
     * Reset all views back to their initial values, this done after the peek has popped.
     */
    private void resetViews() {
        hasEnteredPeekViewBounds = false;
        initialTouchOffset = -1;

        if (dragToActionViewLayout != null) {
            dragToActionViewLayout.setTranslationY(0);
            dragToActionViewLayout.setScaleX(0.25f);
            dragToActionViewLayout.setScaleY(0.25f);
            dragToActionViewLayout.setAlpha(0);
        }

    }

    /**
     * Move the peek view based on the x and y touch coordinates.
     * The further the view is pulled from it's original position, the less
     * it moves, creating an 'elastic' effect.
     *
     * @param touchX
     * @param touchY
     */
    private void movePeekView(int touchX, int touchY) {
        int[] l = new int[2];
        peekView.getLocationOnScreen(l);
        int x = l[0];
        int y = l[1];

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            int adjust = (int) peekView.getY() - y;
            float position = touchY + adjust - initialTouchOffset;
            float totalDistanceTravelled = 0 - (position - peekViewOriginalPosition[1]);
            float amountToMove = peekViewOriginalPosition[1] - (totalDistanceTravelled / 3f + (float) Math.sqrt(totalDistanceTravelled) * 4);

            if (touchY > peekViewOriginalRawPosition[1] + initialTouchOffset) {
                peekView.setY(peekViewOriginalPosition[1]);
            } else if (amountToMove < 0) {
                peekView.setY(0);
            } else {
                peekView.setY(amountToMove);
            }
        } else {
            int adjust = (int) peekView.getX() - x;
            float position = touchX + adjust - initialTouchOffset;
            float totalDistanceTravelled = 0 - (position - peekViewOriginalPosition[0]);
            float amountToMove = peekViewOriginalPosition[0] - (totalDistanceTravelled / 3f + (float) Math.sqrt(totalDistanceTravelled) * 4);

            if (touchX > peekViewOriginalRawPosition[0] + initialTouchOffset) {
                peekView.setX(peekViewOriginalPosition[0]);
            } else if (amountToMove < 0) {
                peekView.setX(0);
            } else {
                peekView.setX(amountToMove);
            }
        }

        // If there is a dragToActionViewLayout, transition it
        if (dragToActionViewLayout != null) {
            transitionDragToActionView();
        }
    }

    /**
     * Fades, moves and scales the dragToActionViewLayout in based on the peekView's
     * current position.
     **/
    private void transitionDragToActionView() {
        float ratio;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            ratio = (peekViewOriginalPosition[1] - peekView.getY()) / (peekViewOriginalPosition[1] - maxDrag);
            dragToActionViewLayout.setTranslationY((-ratio * ratio / 3f) * DRAG_TO_ACTION_MOVE_AMOUNT);
        } else {
            ratio = (peekViewOriginalPosition[0] - peekView.getX()) / (peekViewOriginalPosition[0] - maxDrag);
            dragToActionViewLayout.setTranslationX((-ratio * ratio / 3f) * DRAG_TO_ACTION_MOVE_AMOUNT);
        }

        ratio = Math.min(1f, ratio - 0.25f);

        dragToActionViewLayout.setScaleX(Math.min(1f, 0.25f + ratio));
        dragToActionViewLayout.setScaleY(Math.min(1f, 0.25f + ratio));
        dragToActionViewLayout.setAlpha(ratio * 2);

    }

    public View getPeekView() {
        return peekView;
    }

    private boolean inViewBounds(View view, int rx, int ry) {
        int[] l = new int[2];
        view.getLocationOnScreen(l);
        int x = l[0];
        int y = l[1];
        int w = view.getWidth();
        int h = view.getHeight();

        if (rx < x || rx > x + w || ry < y || ry > y + h) {
            return false;
        }
        return true;
    }


    /**
     * Builder class used for creating the PeekAndPop view.
     */

    public static class Builder {

        // essentials
        protected final Context context;
        protected int peekLayoutId;
        protected ViewGroup parentViewGroup;
        protected ViewGroup containerView;
        protected View baseView;

        // optional extras
        protected int dragToActionViewLayout = -1;
        protected DragToActionListener dragToActionListener;
        protected GeneralActionListener generalActionListener;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder peekLayout(int peekLayoutId) {
            this.peekLayoutId = peekLayoutId;
            return this;
        }

        public Builder containerView(ViewGroup containerView) {
            this.containerView = containerView;
            return this;
        }

        public Builder baseView(View baseView) {
            this.baseView = baseView;
            return this;
        }

        public Builder dragToActionViewLayout(int dragToActionViewLayout) {
            this.dragToActionViewLayout = dragToActionViewLayout;
            return this;
        }

        public Builder dragToActionListener(DragToActionListener dragToActionListener) {
            this.dragToActionListener = dragToActionListener;
            return this;
        }

        public Builder generalActionListener(GeneralActionListener generalActionListener) {
            this.generalActionListener = generalActionListener;
            return this;
        }

        /**
         * If the container view is situated within another view that receives touch events (like a scroll view),
         * the touch events required for the peek and pop will not work correctly so use this method to disallow
         * touch events from the parent view.
         *
         * @param parentViewGroup The parentView that you wish to disallow touch events to (Usually a scroll view, recycler view etc.)
         * @return The Builder instance so you can chain calls
         */
        public Builder parentViewGroupToDisallowTouchEvents(ViewGroup parentViewGroup) {
            this.parentViewGroup = parentViewGroup;
            return this;
        }

        public PeekAndPop build() {
            return new PeekAndPop(this);
        }

    }

    // todo possibly rename these
    public interface DragToActionListener {
        public void draggedToAction();
    }

    public interface GeneralActionListener {
        public void peek();

        public void pop();
    }

    // Exceptions

    public class PeekViewTooLargeException extends Exception {

    }

}
