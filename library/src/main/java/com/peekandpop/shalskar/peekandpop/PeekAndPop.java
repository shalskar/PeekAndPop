package com.peekandpop.shalskar.peekandpop;

import android.animation.Animator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.view.GestureDetectorCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;

import com.peekandpop.shalskar.peekandpop.model.LongHoldView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PeekAndPop {

    // These static values will be converted from dp to px
    protected static final int DRAG_AMOUNT = 128;
    protected static final int PEEK_VIEW_MARGIN = 40;
    protected static final int FLING_TO_ACTION_VIEW_MARGIN = 32;
    protected static final int FLING_TO_ACTION_MOVE_AMOUNT = 128;

    protected static final long LONG_CLICK_DURATION = 200;
    protected static final long LONG_HOLD_DURATION = 750;
    protected static final long LONG_HOLD_REPEAT_DURATION = 1500;
    protected static final long LONG_HOLD_VIBRATE_DURATION = 20;

    protected static final int FLING_VELOCITY_THRESHOLD = -3000;
    private static final float FLING_VELOCITY_MAX = -350;

    protected static final int ANIMATION_PEEK_DURATION = 150;
    protected static final int ANIMATION_POP_DURATION = 150;

    protected int dragAmount;
    protected int peekViewMargin;
    protected int flingToActionThreshold;
    protected int flingToActionViewMargin;
    protected int flingToActionMoveAmount;
    protected int maxDrag;

    protected int downX, downY;

    protected Builder builder;
    protected ViewGroup contentView;
    protected ViewGroup peekLayout;
    protected View peekView;

    protected boolean blurBackground;
    protected boolean animateFling;

    protected View flingToActionViewLayout;

    protected ArrayList<LongHoldView> longHoldViews;

    protected OnFlingToActionListener onFlingToActionListener;
    protected OnGeneralActionListener onGeneralActionListener;
    protected OnLongHoldListener onLongHoldListener;
    protected GestureListener gestureListener;
    protected GestureDetector gestureDetector;

    protected float[] peekViewOriginalPosition;
    protected float initialTouchOffset = -1;
    protected long popTime;

    protected boolean hasEnteredPeekViewBounds = false;

    protected int orientation;

    public PeekAndPop(Builder builder) {
        this.builder = builder;
        init();
    }

    protected void init() {
        this.onFlingToActionListener = builder.onFlingToActionListener;
        this.onGeneralActionListener = builder.onGeneralActionListener;
        this.onLongHoldListener = builder.onLongHoldListener;
        this.gestureListener = new GestureListener();
        this.gestureDetector = new GestureDetector(builder.activity, this.gestureListener);
        this.animateFling = this.onFlingToActionListener != null && builder.animateFling;

        this.longHoldViews = new ArrayList<>();

        orientation = builder.activity.getResources().getConfiguration().orientation;

        this.blurBackground = builder.blurBackground;

        initialiseValues();
        createPeekView();
        initialiseGestureListeners();
    }

    /**
     * Initialise all static values, converting them from dp to px.
     */
    protected void initialiseValues() {
        dragAmount = convertDpToPx(DRAG_AMOUNT);
        peekViewMargin = convertDpToPx(PEEK_VIEW_MARGIN);
        flingToActionViewMargin = convertDpToPx(FLING_TO_ACTION_VIEW_MARGIN);
        flingToActionMoveAmount = convertDpToPx(FLING_TO_ACTION_MOVE_AMOUNT);
    }

    /**
     * Inflate the peekView, add it to the peekLayout with a shaded/blurred background,
     * bring it to the front and set the peekLayout to have an alpha of 0. Get the peekView's
     * original Y position for use when dragging.
     * <p/>
     * If a flingToActionViewLayout is supplied, inflate the flingToActionViewLayout.
     */
    protected void createPeekView() {
        LayoutInflater inflater = LayoutInflater.from(builder.activity);
        contentView = (ViewGroup) builder.activity.findViewById(android.R.id.content).getRootView();

        // Center onPeek view in the onPeek layout and add to the container view group
        peekLayout = (RelativeLayout) inflater.inflate(R.layout.peek_background, contentView, false);

        peekView = inflater.inflate(builder.peekLayoutId, peekLayout, false);
        peekView.setId(R.id.peek_view);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) peekView.getLayoutParams();
        layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            layoutParams.topMargin = peekViewMargin;

        peekLayout.addView(peekView, layoutParams);
        contentView.addView(peekLayout);
        peekLayout.setAlpha(0);

        // Once the onPeek view has inflated fully, this will also update if the view changes in size change
        peekView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                initialisePeekViewOriginalPosition();
                if (onFlingToActionListener != null) {
                    initialiseDragFields();
                    initialTouchOffset = -1;
                }
            }
        });

        if (builder.flingToActionViewLayout != -1) {
            addDragToActionLayout(inflater);
        }

        // If lollipop or above, use elevation to bring peek layout to the front
        if (Build.VERSION.SDK_INT >= 21) {
            peekLayout.setElevation(10f);
            peekView.setElevation(10f);
        } else {
            peekLayout.bringToFront();
            peekView.bringToFront();
            contentView.requestLayout();
            contentView.invalidate();
        }

        peekLayout.requestLayout();
        resetViews();
    }

    /**
     * Adds a flingToActionViewLayout centered at the bottom of the screen.
     * Also inflates the dragToActionView and revealView if applicable.
     *
     * @param inflater
     */
    private void addDragToActionLayout(LayoutInflater inflater) {
        flingToActionViewLayout = inflater.inflate(builder.flingToActionViewLayout, peekLayout, false);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) flingToActionViewLayout.getLayoutParams();

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            layoutParams.addRule(RelativeLayout.ABOVE, peekView.getId());
        } else {
            layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
            layoutParams.addRule(RelativeLayout.LEFT_OF, peekView.getId());
        }

        peekLayout.addView(flingToActionViewLayout, layoutParams);
    }

    /**
     * Set an onLongClick, onClick and onTouch listener for each long click view.
     */
    protected void initialiseGestureListeners() {
        for (int i = 0; i < builder.longClickViews.size(); i++) {
            initialiseGestureListener(builder.longClickViews.get(i), -1);
        }
    }

    protected void initialiseGestureListener(View view, int position) {
        view.setOnTouchListener(new PeekAndPopOnTouchListener(position));
        gestureDetector.setIsLongpressEnabled(false);
    }

    /**
     * Check if user has moved or lifted their finger.
     * <p/>
     * If lifted, onPop the view and check if their is a drag to action listener, check
     * if it had been dragged enough and send an event if so.
     * <p/>
     * If moved, check if the user has entered the bounds of the onPeek view.
     * If the user is within the bounds, and is at the edges of the view, then
     * move it appropriately.
     */

    private void respondToTouch(View v, MotionEvent event, int position) {
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            pop(v, position);

        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            downX = (int) event.getRawX();
            downY = (int) event.getRawY();

            if (onFlingToActionListener != null) {
                if (hasEnteredPeekViewBounds) {
                    updatePeekView();
                } else if (pointInViewBounds(peekView, downX, downY)) {
                    hasEnteredPeekViewBounds = true;
                }
            }

            if (onLongHoldListener != null)
                checkLongHoldViews(position);
        }
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
    }

    /**
     * Update the peek view's position if it is in the top 2 thirds of the peek view.
     */
    private void updatePeekView() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (downY > peekViewOriginalPosition[1] + (peekView.getHeight() / 2)) {
                peekView.setY(peekViewOriginalPosition[1]);
                return;
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (downX > peekViewOriginalPosition[0] + (peekView.getWidth() / 2)) {
                peekView.setX(peekViewOriginalPosition[0]);
                return;
            }
        }

        setOffset();
        movePeekView();
    }

    /**
     * Set the offset if the touch coordinates are in the top half of the view.
     */
    private void setOffset() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (initialTouchOffset < peekView.getHeight() / 2)
                initialTouchOffset = Math.max(calculateOffset(downX, downY), initialTouchOffset);
        } else {
            if (initialTouchOffset < peekView.getWidth() / 2)
                initialTouchOffset = Math.max(calculateOffset(downX, downY), initialTouchOffset);
        }
    }

    /**
     * Check all the long hold views to see if they are being held and if so for how long
     * they have been held and send a long hold event if > 750ms.
     *
     * @param position
     */
    private void checkLongHoldViews(final int position) {
        for (int i = 0; i < longHoldViews.size(); i++) {
            final LongHoldView longHoldView = longHoldViews.get(i);
            boolean viewInBounds = pointInViewBounds(longHoldView.getView(), downX, downY);

            if (viewInBounds && longHoldView.getLongHoldTimer() == null) {
                setLongHoldViewTimer(longHoldView, position, LONG_HOLD_DURATION);
            } else if (!viewInBounds && longHoldView.getLongHoldTimer() != null) {
                longHoldView.getLongHoldTimer().cancel();
                longHoldView.setLongHoldTimer(null);
            }
        }
    }

    /**
     * Sets a timer on the long hold view that will send a long hold event after 750ms
     * If receiveMultipleEvents is true, it will set another timer directly after for 1500ms
     *
     * @param longHoldView
     * @param position
     * @param duration
     */
    private void setLongHoldViewTimer(final LongHoldView longHoldView, final int position, long duration) {
        final Timer longHoldTimer = new Timer();
        longHoldTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendOnLongHoldEvent(longHoldView.getView(), position);
                if (longHoldView.isReceiveMultipleEvents())
                    setLongHoldViewTimer(longHoldView, position, LONG_HOLD_REPEAT_DURATION);
            }
        }, duration);

        longHoldView.setLongHoldTimer(longHoldTimer);
    }

    private void sendOnLongHoldEvent(final View view, final int position) {
        builder.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((Vibrator) builder.activity.getSystemService(Activity.VIBRATOR_SERVICE)).vibrate(LONG_HOLD_VIBRATE_DURATION);
                onLongHoldListener.onLongHold(view, position);
            }
        });

    }

    /**
     * Initialise the peek view original position to be centred in the middle of the screen.
     */
    private void initialisePeekViewOriginalPosition() {
        peekViewOriginalPosition = new float[2];
        peekViewOriginalPosition[0] = (peekLayout.getWidth() / 2) - (peekView.getWidth() / 2);
        peekViewOriginalPosition[1] = (peekLayout.getHeight() / 2) - (peekView.getHeight() / 2);
    }

    /**
     * Calculate the max drag position, using the x/y position and the amount of
     * drag.
     */
    private void initialiseDragFields() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxDrag = (int) peekView.getY() - dragAmount;
            flingToActionThreshold = (int) peekView.getY() / 8;
        } else {
            maxDrag = (int) peekView.getX() - dragAmount;
            flingToActionThreshold = (int) peekView.getX() / 8;
        }

        if (maxDrag < 0) {
            maxDrag = 0;
        }
    }

    /**
     * Calculate the distance of touch coordinates to the peek view's
     * original position. This is used to make peek view track the finger
     * accurately.
     *
     * @param touchX
     * @param touchY
     * @return
     */
    private int calculateOffset(int touchX, int touchY) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return touchY - (int) peekViewOriginalPosition[1];
        } else {
            return touchX - (int) peekViewOriginalPosition[0];
        }
    }

    /**
     * Animate the peek view in and send an on peek event
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    private void peek(View longClickView, int index) {
        if (this.onGeneralActionListener != null) {
            this.onGeneralActionListener.onPeek(longClickView, index);
        }

        if (Build.VERSION.SDK_INT >= 17 && blurBackground) {
            blurBackground();
        } else if (Build.VERSION.SDK_INT < 17 && blurBackground) {
            Log.d("PeekAndPop", "Unable to blur background, device version below 17");
        }

        PeekAnimationHelper.animatePeek(peekLayout, peekView, ANIMATION_PEEK_DURATION);

        if (builder.parentViewGroup != null) {
            builder.parentViewGroup.requestDisallowInterceptTouchEvent(true);
        }
        // Reset the touch coordinates to prevent accidental long hold actions on long hold views
        downX = 0;
        downY = 0;

        gestureListener.setView(longClickView);
        gestureListener.setPosition(index);
    }

    private void blurBackground() {
        peekLayout.setBackgroundDrawable(new BitmapDrawable(builder.activity.getResources(), BlurBuilder.blur(contentView)));
    }

    /**
     * Animate the peek view in and send a on pop event.
     * Reset all the views and after the peek view has animated out, reset it's position.
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    private void pop(View longClickView, int index) {
        if (this.onGeneralActionListener != null) {
            this.onGeneralActionListener.onPop(longClickView, index);
        }

        PeekAnimationHelper.animatePop(peekLayout, peekView, new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                resetViews();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        }, ANIMATION_POP_DURATION, orientation);

        if (flingToActionViewLayout != null)
            flingToActionViewLayout.animate().setDuration(ANIMATION_POP_DURATION).alpha(0).start();

        popTime = System.currentTimeMillis();
    }

    /**
     * Reset all views back to their initial values, this done after the onPeek has popped.
     */
    private void resetViews() {
        hasEnteredPeekViewBounds = false;
        initialTouchOffset = -1;
        downX = 0;
        downY = 0;

        if (flingToActionViewLayout != null) {
            flingToActionViewLayout.setTranslationY(0);
            flingToActionViewLayout.setTranslationX(0);
            flingToActionViewLayout.setScaleX(1f);
            flingToActionViewLayout.setScaleY(1f);
            flingToActionViewLayout.setAlpha(1);
        }

        for (int i = 0; i < longHoldViews.size(); i++) {
            Timer longHoldTimer = longHoldViews.get(i).getLongHoldTimer();
            if (longHoldTimer != null) {
                longHoldTimer.cancel();
                longHoldViews.get(i).setLongHoldTimer(null);
            }
        }

        peekView.setX(peekViewOriginalPosition[0]);
        peekView.setY(peekViewOriginalPosition[1]);
        peekView.setScaleX(0.5f);
        peekView.setScaleY(0.5f);
    }

    /**
     * Move the onPeek view based on the x and y touch coordinates.
     * The further the view is pulled from it's original position, the less
     * it moves, creating an 'elastic' effect.
     */
    private void movePeekView() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            float adjustedPosition = downY - initialTouchOffset;
            float totalDistanceTravelled = 0 - (adjustedPosition - peekViewOriginalPosition[1]);
            float amountToMove = peekViewOriginalPosition[1] - (totalDistanceTravelled / 3f + (float) Math.sqrt(totalDistanceTravelled) * 4);

            if (downY > peekViewOriginalPosition[1] + initialTouchOffset) {
                peekView.setY(peekViewOriginalPosition[1]);
            } else if (amountToMove < peekViewMargin) {
                peekView.setY(peekViewMargin);
            } else {
                peekView.setY(amountToMove);
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            float adjustedPosition = downX - initialTouchOffset;
            float totalDistanceTravelled = 0 - (adjustedPosition - peekViewOriginalPosition[0]);
            float amountToMove = peekViewOriginalPosition[0] - (totalDistanceTravelled / 3f + (float) Math.sqrt(totalDistanceTravelled) * 4);

            if (downX > peekViewOriginalPosition[0] + initialTouchOffset) {
                peekView.setX(peekViewOriginalPosition[0]);
            } else if (amountToMove < peekViewMargin) {
                peekView.setX(peekViewMargin);
            } else {
                peekView.setX(amountToMove);
            }
        }

        // If there is a flingToActionViewLayout, transition it
        if (flingToActionViewLayout != null) {
            transitionFlingToActionView();
        }
    }

    /**
     * Fades, moves and scales the flingToActionViewLayout in based on the peekView's
     * current position.
     **/
    private void transitionFlingToActionView() {
        float ratio;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // todo check if screen margin is correct
            ratio = (peekViewOriginalPosition[1] - peekView.getY()) / (peekViewOriginalPosition[1] - maxDrag - peekViewMargin / 2);
        } else {
            ratio = (peekViewOriginalPosition[0] - peekView.getX()) / (peekViewOriginalPosition[0] - maxDrag);
        }

        ratio = ratio * 2;
        ratio = (1 - ratio);

        flingToActionViewLayout.setScaleX(Math.min(ratio, 1));
        flingToActionViewLayout.setScaleY(Math.min(ratio, 1));
        flingToActionViewLayout.setAlpha(ratio);
    }

    private int convertDpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, builder.activity.getResources().getDisplayMetrics());
    }

    private boolean pointInViewBounds(View view, int x, int y) {
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

    public void setOnFlingToActionListener(OnFlingToActionListener onFlingToActionListener) {
        this.onFlingToActionListener = onFlingToActionListener;
    }

    public void setOnGeneralActionListener(OnGeneralActionListener onGeneralActionListener) {
        this.onGeneralActionListener = onGeneralActionListener;
    }

    public void setOnLongHoldListener(OnLongHoldListener onLongHoldListener) {
        this.onLongHoldListener = onLongHoldListener;
    }

    /**
     * Adds a view to receive long click and touch events
     *
     * @param view     view to receive events
     * @param position add position of view if in a list, this will be returned in the general action listener
     *                 and drag to action listener.
     */
    public void addLongClickView(View view, int position) {
        initialiseGestureListener(view, position);
    }

    /**
     * Specify id of view WITHIN the peek layout, this view will receive on long hold events.
     * You can add multiple on long hold views
     *
     * @param longHoldViewId id of the view to receive on long hold events
     * @return
     */
    public void addLongHoldView(int longHoldViewId, boolean receiveMultipleEvents) {
        this.longHoldViews.add(new LongHoldView(peekView.findViewById(longHoldViewId), receiveMultipleEvents));
    }

    public View getPeekView() {
        return peekView;
    }

    public boolean isBlurBackground() {
        return blurBackground;
    }

    public void setBlurBackground(boolean blurBackground) {
        this.blurBackground = blurBackground;
    }

    public boolean isAnimateFling() {
        return animateFling;
    }

    public void setAnimateFling(boolean animateFling) {
        this.animateFling = animateFling;
    }

    /**
     * Builder class used for creating the PeekAndPop view.
     */

    public static class Builder {

        // essentials
        protected final Activity activity;
        protected int peekLayoutId;

        // optional extras
        protected ViewGroup parentViewGroup;
        protected ArrayList<View> longClickViews;
        protected int flingToActionViewLayout = -1;

        protected OnFlingToActionListener onFlingToActionListener;
        protected OnGeneralActionListener onGeneralActionListener;
        protected OnLongHoldListener onLongHoldListener;

        protected boolean blurBackground = true;
        protected boolean animateFling = true;

        public Builder(@NonNull Activity activity) {
            this.activity = activity;
            this.longClickViews = new ArrayList<>();
        }

        /**
         * Peek layout resource id, which will be inflated into the onPeek view
         *
         * @param peekLayoutId id of the onPeek layout resource
         * @return
         */
        public Builder peekLayout(int peekLayoutId) {
            this.peekLayoutId = peekLayoutId;
            return this;
        }

        /**
         * Views which will open the onPeek view when long clicked
         *
         * @param longClickViews One or more views to handle on long click events
         * @return
         */
        public Builder longClickViews(@NonNull View... longClickViews) {
            for (int i = 0; i < longClickViews.length; i++) {
                this.longClickViews.add(longClickViews[i]);
            }
            return this;
        }

        /**
         * A view to be transitioned out while dragging the peek view or when the peek view is flung.
         * This can be used to indicate to the user what the flingToAction will be.
         *
         * @param flingToActionViewLayout
         * @return
         */
        public Builder flingToActionViewLayout(int flingToActionViewLayout) {
            this.flingToActionViewLayout = flingToActionViewLayout;
            return this;
        }

        /**
         * A listener for when the onPeek view is dragged enough.
         *
         * @param onFlingToActionListener
         * @return
         */
        public Builder onFlingToActionListener(@NonNull OnFlingToActionListener onFlingToActionListener) {
            this.onFlingToActionListener = onFlingToActionListener;
            return this;
        }

        /**
         * A listener for the onPeek and onPop actions.
         *
         * @param onGeneralActionListener
         * @return
         */
        public Builder generalActionListener(@NonNull OnGeneralActionListener onGeneralActionListener) {
            this.onGeneralActionListener = onGeneralActionListener;
            return this;
        }


        /**
         * A listener for the on long hold views to receive onLongHold actions.
         *
         * @param onLongHoldListener
         * @return
         */
        public Builder onLongHoldListener(@NonNull OnLongHoldListener onLongHoldListener) {
            this.onLongHoldListener = onLongHoldListener;
            return this;
        }

        /**
         * If the container view is situated within another view that receives touch events (like a scroll view),
         * the touch events required for the onPeek and onPop will not work correctly so use this method to disallow
         * touch events from the parent view.
         *
         * @param parentViewGroup The parentView that you wish to disallow touch events to (Usually a scroll view, recycler view etc.)
         * @return
         */
        public Builder parentViewGroupToDisallowTouchEvents(@NonNull ViewGroup parentViewGroup) {
            this.parentViewGroup = parentViewGroup;
            return this;
        }

        /**
         * Blur the background when showing the peek view, defaults to true.
         * Setting this to false may increase performance.
         *
         * @param blurBackground
         * @return
         */
        public Builder blurBackground(boolean blurBackground) {
            this.blurBackground = blurBackground;
            return this;
        }

        /**
         * Animate the peek view upwards when a it is flung, defaults to true.
         *
         * @param animateFling
         * @return
         */
        public Builder animateFling(boolean animateFling) {
            this.animateFling = animateFling;
            return this;
        }

        /**
         * Create the PeekAndPop object
         *
         * @return the PeekAndPop object
         */
        public PeekAndPop build() {
            return new PeekAndPop(this);
        }

    }

    private final class PeekAndPopOnTouchListener implements View.OnTouchListener {

        private int position;
        private Timer longHoldTimer;

        public PeekAndPopOnTouchListener(int position) {
            this.position = position;
            longHoldTimer = new Timer();
        }

        @Override
        public boolean onTouch(final View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                longHoldTimer = new Timer();
                longHoldTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        builder.activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                peek(view, position);
                            }
                        });
                    }
                }, LONG_CLICK_DURATION);

            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                longHoldTimer.cancel();
            }
            respondToTouch(view, event, position);
            return true;
        }
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {

        private int position;
        private View view;

        public void setView(View view) {
            this.view = view;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            Log.d("PeekAndPop", "Down");
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (onFlingToActionListener != null) {
                if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                    if (velocityY < FLING_VELOCITY_THRESHOLD) {
                        onFlingToActionListener.onFlingToAction(view, position);
                    }
                } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (velocityX < FLING_VELOCITY_THRESHOLD) {
                        onFlingToActionListener.onFlingToAction(view, position);
                    }
                }
            }
            Log.d("PeekAndPop", "Fling");
            if (animateFling) {
                PeekAnimationHelper.animateFling(peekView, flingToActionViewLayout, velocityX, velocityY,
                        ANIMATION_POP_DURATION, popTime, orientation, FLING_VELOCITY_THRESHOLD, FLING_VELOCITY_MAX);
                PeekAnimationHelper.animateExpand(peekView, ANIMATION_POP_DURATION, popTime);
            }


            return true;
        }
    }

    public interface OnFlingToActionListener {
        void onFlingToAction(View longClickView, int position);
    }

    public interface OnGeneralActionListener {
        void onPeek(View longClickView, int position);

        void onPop(View longClickView, int position);
    }

    public interface OnLongHoldListener {
        void onLongHold(View view, int position);
    }

}
