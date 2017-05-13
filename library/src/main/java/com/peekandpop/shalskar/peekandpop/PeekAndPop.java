package com.peekandpop.shalskar.peekandpop;

import android.animation.Animator;
import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.IdRes;
import android.support.annotation.IntDef;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.peekandpop.shalskar.peekandpop.model.HoldAndReleaseView;
import com.peekandpop.shalskar.peekandpop.model.LongHoldView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PeekAndPop {

    @IntDef({FLING_UPWARDS, FLING_DOWNWARDS})
    public @interface FlingDirections {
    }

    public static final int FLING_UPWARDS = 0;
    public static final int FLING_DOWNWARDS = 1;

    private static final int PEEK_VIEW_MARGIN = 12;

    protected static final long LONG_CLICK_DURATION = 200;
    protected static final long LONG_HOLD_DURATION = 850;
    protected static final long HOLD_AND_RELEASE_DURATION = 50;

    private static final int FLING_VELOCITY_THRESHOLD = 3000;
    private static final float FLING_VELOCITY_MAX = 1000;

    protected static final int ANIMATION_PEEK_DURATION = 275;
    protected static final int ANIMATION_POP_DURATION = 250;

    protected Builder builder;
    protected View peekView;
    protected ViewGroup contentView;
    protected ViewGroup peekLayout;
    protected PeekAnimationHelper peekAnimationHelper;

    private boolean blurBackground;
    private boolean animateFling;
    private boolean allowUpwardsFling;
    private boolean allowDownwardsFling;
    private int customLongHoldDuration = -1;
    private boolean enabled = true;

    protected ArrayList<LongHoldView> longHoldViews;
    protected ArrayList<HoldAndReleaseView> holdAndReleaseViews;
    protected HoldAndReleaseView currentHoldAndReleaseView;
    protected OnFlingToActionListener onFlingToActionListener;

    protected OnGeneralActionListener onGeneralActionListener;
    protected OnLongHoldListener onLongHoldListener;
    protected OnHoldAndReleaseListener onHoldAndReleaseListener;
    protected GestureListener gestureListener;
    protected GestureDetector gestureDetector;
    private Timer longHoldTimer = new Timer();

    protected int orientation;
    protected float[] peekViewOriginalPosition;
    protected int peekViewMargin;
    protected int downX, downY;
    protected long popTime;


    public PeekAndPop(Builder builder) {
        this.builder = builder;
        init();
    }

    protected void init() {
        this.onFlingToActionListener = builder.onFlingToActionListener;
        this.onGeneralActionListener = builder.onGeneralActionListener;
        this.onLongHoldListener = builder.onLongHoldListener;
        this.onHoldAndReleaseListener = builder.onHoldAndReleaseListener;
        this.gestureListener = new GestureListener();
        this.gestureDetector = new GestureDetector(builder.activity, this.gestureListener);
        initialiseGestureListeners();

        this.longHoldViews = new ArrayList<>();
        this.holdAndReleaseViews = new ArrayList<>();

        this.blurBackground = builder.blurBackground;
        this.animateFling = builder.animateFling;
        this.allowUpwardsFling = builder.allowUpwardsFling;
        this.allowDownwardsFling = builder.allowDownwardsFling;

        this.orientation = builder.activity.getResources().getConfiguration().orientation;
        this.peekViewMargin = DimensionUtil.convertDpToPx(builder.activity.getApplicationContext(), PEEK_VIEW_MARGIN);

        initialisePeekView();
    }

    /**
     * Inflate the peekView, add it to the peekLayout with a shaded/blurred background,
     * bring it to the front and set the peekLayout to have an alpha of 0. Get the peekView's
     * original Y position for use when dragging.
     * <p/>
     * If a flingToActionViewLayoutId is supplied, inflate the flingToActionViewLayoutId.
     */
    protected void initialisePeekView() {
        LayoutInflater inflater = LayoutInflater.from(builder.activity);
        contentView = (ViewGroup) builder.activity.findViewById(android.R.id.content).getRootView();

        // Center onPeek view in the onPeek layout and add to the container view group
        peekLayout = (FrameLayout) inflater.inflate(R.layout.peek_background, contentView, false);
        peekView = inflater.inflate(builder.peekLayoutId, peekLayout, false);
        peekView.setId(R.id.peek_view);

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) peekView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            layoutParams.topMargin = peekViewMargin;

        peekLayout.addView(peekView, layoutParams);
        contentView.addView(peekLayout);

        peekLayout.setVisibility(View.GONE);
        peekLayout.setAlpha(0);
        peekLayout.requestLayout();

        peekAnimationHelper = new PeekAnimationHelper(builder.activity.getApplicationContext(), peekLayout, peekView);

        bringViewsToFront();
        initialiseViewTreeObserver();
        resetViews();
    }

    /**
     * If lollipop or above, use elevation to bring peek views to the front
     */
    private void bringViewsToFront() {
        if (Build.VERSION.SDK_INT >= 21) {
            peekLayout.setElevation(10f);
            peekView.setElevation(10f);
        } else {
            peekLayout.bringToFront();
            peekView.bringToFront();
            contentView.requestLayout();
            contentView.invalidate();
        }
    }

    /**
     * Once the onPeek view has inflated fully, this will also update if the view changes in size change
     */
    private void initialiseViewTreeObserver() {
        peekView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                initialisePeekViewOriginalPosition();
            }
        });
    }

    /**
     * Set an onClick and onTouch listener for each long click view.
     */
    protected void initialiseGestureListeners() {
        for (int i = 0; i < builder.longClickViews.size(); i++) {
            initialiseGestureListener(builder.longClickViews.get(i), -1);
        }
        gestureDetector.setIsLongpressEnabled(false);
    }

    protected void initialiseGestureListener(@NonNull View view, int position) {
        view.setOnTouchListener(new PeekAndPopOnTouchListener(position));
        // onTouchListener will not work correctly if the view doesn't have an
        // onClickListener set, hence adding one if none has been added.
        if(!view.hasOnClickListeners()){
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });
        }
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
    protected void handleTouch(@NonNull View view, @NonNull MotionEvent event, int position) {
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            pop(view, position);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            downX = (int) event.getRawX();
            downY = (int) event.getRawY();

            if (onLongHoldListener != null)
                checkLongHoldViews(position);

            if (onHoldAndReleaseListener != null)
                checkHoldAndReleaseViews(position);
        }

        if (gestureDetector != null)
            gestureDetector.onTouchEvent(event);
    }

    /**
     * Check all the long hold views to see if they are being held and if so for how long
     * they have been held and send a long hold event if over the long hold duration.
     *
     * @param position
     */
    private void checkLongHoldViews(final int position) {
        for (int i = 0; i < longHoldViews.size(); i++) {
            final LongHoldView longHoldView = longHoldViews.get(i);
            boolean viewInBounds = DimensionUtil.pointInViewBounds(longHoldView.getView(), downX, downY);

            if (viewInBounds && longHoldView.getLongHoldTimer() == null) {
                long duration = customLongHoldDuration != -1 ? customLongHoldDuration : LONG_HOLD_DURATION;
                longHoldView.startLongHoldViewTimer(this, position, duration);
                onLongHoldListener.onEnter(longHoldView.getView(), position);
            } else if (!viewInBounds && longHoldView.getLongHoldTimer() != null) {
                longHoldView.getLongHoldTimer().cancel();
                longHoldView.setLongHoldTimer(null);
            }
        }
    }


    /**
     * Check all the HoldAndRelease views to see if they are being held and if so for how long
     * they have been held. If > 100ms then set that HoldAndReleaseView as the current.
     *
     * @param position
     */
    private void checkHoldAndReleaseViews(final int position) {
        for (int i = 0; i < holdAndReleaseViews.size(); i++) {
            final HoldAndReleaseView holdAndReleaseView = holdAndReleaseViews.get(i);
            boolean viewInBounds = DimensionUtil.pointInViewBounds(holdAndReleaseView.getView(), downX, downY);

            if (viewInBounds && holdAndReleaseView.getHoldAndReleaseTimer() == null) {
                holdAndReleaseView.startHoldAndReleaseTimer(this, position, HOLD_AND_RELEASE_DURATION);
            } else if (!viewInBounds && holdAndReleaseView.getHoldAndReleaseTimer() != null) {
                holdAndReleaseView.getHoldAndReleaseTimer().cancel();
                holdAndReleaseView.setHoldAndReleaseTimer(null);
                if (holdAndReleaseView == currentHoldAndReleaseView) {
                    triggerOnLeaveEvent(holdAndReleaseView.getView(), holdAndReleaseView.getPosition());
                    holdAndReleaseView.setPosition(-1);
                    currentHoldAndReleaseView = null;
                }
            }
        }
    }

    public void sendOnLongHoldEvent(final View view, final int position) {
        builder.activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
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
        peekViewOriginalPosition[1] = (peekLayout.getHeight() / 2) - (peekView.getHeight() / 2) + peekViewMargin;
    }

    /**
     * Animate the peek view in and send an on peek event
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    protected void peek(@NonNull View longClickView, int index) {
        if (onGeneralActionListener != null)
            onGeneralActionListener.onPeek(longClickView, index);

        peekLayout.setVisibility(View.VISIBLE);

        cancelClick(longClickView);

        if (Build.VERSION.SDK_INT >= 17 && blurBackground)
            blurBackground();
        else if (Build.VERSION.SDK_INT < 17 && blurBackground)
            Log.e("PeekAndPop", "Unable to blur background, device version below 17");

        peekAnimationHelper.animatePeek(ANIMATION_PEEK_DURATION);

        if (builder.parentViewGroup != null)
            builder.parentViewGroup.requestDisallowInterceptTouchEvent(true);

        // Reset the touch coordinates to prevent accidental long hold actions on long hold views
        downX = 0;
        downY = 0;

        gestureListener.setView(longClickView);
        gestureListener.setPosition(index);
    }

    /**
     * Once the peek view has been shown, send a cancel motion event to the long hold view so that
     * it isn't left in a pressed state
     *
     * @param longClickView the view that was long clicked
     */
    private void cancelClick(@NonNull View longClickView) {
        MotionEvent e = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL,
                0, 0, 0);
        longClickView.onTouchEvent(e);
        e.recycle();
    }

    private void blurBackground() {
        if (Build.VERSION.SDK_INT >= 16) {
            peekLayout.setBackground(null);
            peekLayout.setBackground(new BitmapDrawable(builder.activity.getResources(), BlurBuilder.blur(contentView)));
        }else {
            peekLayout.setBackgroundDrawable(null);
            peekLayout.setBackgroundDrawable(new BitmapDrawable(builder.activity.getResources(), BlurBuilder.blur(contentView)));
        }
    }

    /**
     * Animate the peek view in and send a on pop event.
     * Reset all the views and after the peek view has animated out, reset it's position.
     *
     * @param longClickView the view that was long clicked
     * @param index         the view that long clicked
     */
    protected void pop(@NonNull View longClickView, int index) {
        if (onGeneralActionListener != null)
            onGeneralActionListener.onPop(longClickView, index);

        if (currentHoldAndReleaseView != null && onHoldAndReleaseListener != null)
            onHoldAndReleaseListener.onRelease(currentHoldAndReleaseView.getView(), currentHoldAndReleaseView.getPosition());

        resetTimers();

        peekAnimationHelper.animatePop(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                resetViews();
                animation.cancel();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        }, ANIMATION_POP_DURATION);

        popTime = System.currentTimeMillis();
    }

    /**
     * Reset all views back to their initial values, this done after the onPeek has popped.
     */
    private void resetViews() {
        peekLayout.setVisibility(View.GONE);
        downX = 0;
        downY = 0;

        for (int i = 0; i < longHoldViews.size(); i++) {
            Timer longHoldTimer = longHoldViews.get(i).getLongHoldTimer();
            if (longHoldTimer != null) {
                longHoldTimer.cancel();
                longHoldViews.get(i).setLongHoldTimer(null);
            }
        }

        if (peekViewOriginalPosition != null) {
            peekView.setX(peekViewOriginalPosition[0]);
            peekView.setY(peekViewOriginalPosition[1]);
        }
        peekView.setScaleX(0.85f);
        peekView.setScaleY(0.85f);
    }

    private void resetTimers() {
        currentHoldAndReleaseView = null;
        for (HoldAndReleaseView holdAndReleaseView : holdAndReleaseViews) {
            if (holdAndReleaseView.getHoldAndReleaseTimer() != null)
                holdAndReleaseView.getHoldAndReleaseTimer().cancel();
        }
        for (LongHoldView longHoldView : longHoldViews) {
            if (longHoldView.getLongHoldTimer() != null)
                longHoldView.getLongHoldTimer().cancel();
        }
    }

    public void destroy() {
        if (currentHoldAndReleaseView != null && onHoldAndReleaseListener != null) {
            currentHoldAndReleaseView.getHoldAndReleaseTimer().cancel();
            currentHoldAndReleaseView = null;
        }

        for (int i = 0; i < longHoldViews.size(); i++) {
            Timer longHoldTimer = longHoldViews.get(i).getLongHoldTimer();
            if (longHoldTimer != null) {
                longHoldTimer.cancel();
                longHoldViews.get(i).setLongHoldTimer(null);
            }
        }

        builder = null;
    }

    public void setFlingTypes(boolean allowUpwardsFling, boolean allowDownwardsFling) {
        this.allowUpwardsFling = allowUpwardsFling;
        this.allowDownwardsFling = allowDownwardsFling;
    }

    public void setCurrentHoldAndReleaseView(@Nullable HoldAndReleaseView currentHoldAndReleaseView) {
        this.currentHoldAndReleaseView = currentHoldAndReleaseView;
    }

    public void setOnFlingToActionListener(@Nullable OnFlingToActionListener onFlingToActionListener) {
        this.onFlingToActionListener = onFlingToActionListener;
    }

    public void setOnGeneralActionListener(@Nullable OnGeneralActionListener onGeneralActionListener) {
        this.onGeneralActionListener = onGeneralActionListener;
    }

    public void setOnLongHoldListener(@Nullable OnLongHoldListener onLongHoldListener) {
        this.onLongHoldListener = onLongHoldListener;
    }

    public void setOnHoldAndReleaseListener(@Nullable OnHoldAndReleaseListener onHoldAndReleaseListener) {
        this.onHoldAndReleaseListener = onHoldAndReleaseListener;
    }

    /**
     * Adds a view to receive long click and touch events
     *
     * @param view     view to receive events
     * @param position add position of view if in a list, this will be returned in the general action listener
     *                 and drag to action listener.
     */
    public void addLongClickView(@NonNull View view, int position) {
        initialiseGestureListener(view, position);
    }

    /**
     * Specify id of view WITHIN the peek layout, this view will trigger on long hold events.
     * You can add multiple on long hold views
     *
     * @param longHoldViewId id of the view to receive on long hold events
     * @return
     */
    public void addLongHoldView(@IdRes int longHoldViewId, boolean receiveMultipleEvents) {
        longHoldViews.add(new LongHoldView(peekView.findViewById(longHoldViewId), receiveMultipleEvents));
    }

    /**
     * Specify id of view WITHIN the peek layout, this view will trigger the following events:
     * onHold() - when the view is held for a small amount of time
     * onLeave() - when the view is no longer held but the user is is still touching the screen
     * onRelease() - when the user releases after holding the view
     * <p/>
     * You can add multiple HoldAndRelease views
     *
     * @param holdAndReleaseViewId id of the view to receive on long hold events
     * @return
     */
    public void addHoldAndReleaseView(@IdRes int holdAndReleaseViewId) {
        holdAndReleaseViews.add(new HoldAndReleaseView(peekView.findViewById(holdAndReleaseViewId)));
    }

    public void triggerOnHoldEvent(@NonNull final View view, final int position) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                onHoldAndReleaseListener.onHold(view, position);
            }
        });
    }

    protected void triggerOnLeaveEvent(@NonNull final View view, final int position) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                onHoldAndReleaseListener.onLeave(view, position);
            }
        });
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

    public void setLongHoldDuration(int duration) {
        this.customLongHoldDuration = duration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Builder class used for creating the PeekAndPop view.
     */

    public static class Builder {

        // essentials
        protected final Activity activity;
        protected int peekLayoutId = -1;

        // optional extras
        protected ViewGroup parentViewGroup;
        protected ArrayList<View> longClickViews;

        protected OnFlingToActionListener onFlingToActionListener;
        protected OnGeneralActionListener onGeneralActionListener;
        protected OnLongHoldListener onLongHoldListener;
        protected OnHoldAndReleaseListener onHoldAndReleaseListener;

        protected boolean blurBackground = true;
        protected boolean animateFling = true;
        protected boolean allowUpwardsFling = true;
        protected boolean allowDownwardsFling = true;

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
        public Builder peekLayout(@LayoutRes int peekLayoutId) {
            this.peekLayoutId = peekLayoutId;
            return this;
        }

        /**
         * Views which will show the peek view when long clicked
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
        public Builder onGeneralActionListener(@NonNull OnGeneralActionListener onGeneralActionListener) {
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
         * A listener for the hold and release views to receive onRelease actions.
         *
         * @param onHoldAndReleaseListener
         * @return
         */
        public Builder onHoldAndReleaseListener(@NonNull OnHoldAndReleaseListener onHoldAndReleaseListener) {
            this.onHoldAndReleaseListener = onHoldAndReleaseListener;
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
         * Set the accepted fling types, defaults to both being true.
         */
        public Builder flingTypes(boolean allowUpwardsFling, boolean allowDownwardsFling) {
            this.allowUpwardsFling = allowUpwardsFling;
            this.allowDownwardsFling = allowDownwardsFling;
            return this;
        }

        /**
         * Create the PeekAndPop object
         *
         * @return the PeekAndPop object
         */
        public PeekAndPop build() {
            if (peekLayoutId == -1) {
                throw new IllegalArgumentException("No peekLayoutId specified.");
            }
            return new PeekAndPop(this);
        }
    }

    protected class PeekAndPopOnTouchListener implements View.OnTouchListener {

        private int position;
        private Runnable longHoldRunnable;
        private boolean peekShown;

        public PeekAndPopOnTouchListener(int position) {
            this.position = position;
        }

        @Override
        public boolean onTouch(final View view, MotionEvent event) {
            if (!enabled) return false;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                peekShown = false;
                cancelPendingTimer(view);
                startTimer(view);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                cancelPendingTimer(view);
            }

            if (peekShown)
                handleTouch(view, event, position);

            return peekShown;
        }

        /**
         * Cancel pending timer and if the timer has already activated, run another runnable to
         * pop the view.
         *
         * @param view
         */
        private void cancelPendingTimer(@NonNull final View view) {
            longHoldTimer.cancel();
            if (longHoldRunnable != null) {
                longHoldRunnable = new Runnable() {
                    @Override
                    public void run() {
                        peekShown = false;
                        pop(view, position);
                        longHoldRunnable = null;
                    }
                };
                builder.activity.runOnUiThread(longHoldRunnable);
            }
        }

        /**
         * Start the longHoldTimer, if it reaches the long hold duration, peek
         *
         * @param view
         */
        private void startTimer(@NonNull final View view) {
            longHoldTimer = new Timer();
            longHoldTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    peekShown = true;
                    longHoldRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (peekShown) {
                                peek(view, position);
                                longHoldRunnable = null;
                            }
                        }
                    };
                    builder.activity.runOnUiThread(longHoldRunnable);
                }
            }, LONG_CLICK_DURATION);
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }

    protected class GestureListener extends GestureDetector.SimpleOnGestureListener {

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
            return true;
        }

        @Override
        public boolean onFling(MotionEvent firstEvent, MotionEvent secondEvent, float velocityX, float velocityY) {
            if (onFlingToActionListener != null)
                return handleFling(velocityX, velocityY);
            else
                return true;
        }

        private boolean handleFling(float velocityX, float velocityY) {
            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                if (velocityY < -FLING_VELOCITY_THRESHOLD && allowUpwardsFling) {
                    flingToAction(FLING_UPWARDS, velocityX, velocityY);
                    return false;
                } else if (velocityY > FLING_VELOCITY_THRESHOLD && allowDownwardsFling) {
                    flingToAction(FLING_DOWNWARDS, velocityX, velocityY);
                    return false;
                }
            } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (velocityX < -FLING_VELOCITY_THRESHOLD && allowUpwardsFling) {
                    flingToAction(FLING_UPWARDS, velocityX, velocityY);
                    return false;
                } else if (velocityX > FLING_VELOCITY_THRESHOLD && allowDownwardsFling) {
                    flingToAction(FLING_DOWNWARDS, velocityX, velocityY);
                    return false;
                }
            }
            return true;
        }

        private void flingToAction(@FlingDirections int direction, float velocityX, float velocityY) {
            onFlingToActionListener.onFlingToAction(view, position, direction);
            if (animateFling) {
                if (direction == FLING_UPWARDS) {
                    peekAnimationHelper.animateExpand(ANIMATION_POP_DURATION, popTime);
                    peekAnimationHelper.animateFling(velocityX, velocityY, ANIMATION_POP_DURATION, popTime, -FLING_VELOCITY_MAX);
                } else {
                    peekAnimationHelper.animateFling(velocityX, velocityY, ANIMATION_POP_DURATION, popTime, FLING_VELOCITY_MAX);
                }
            }
        }
    }

    public interface OnFlingToActionListener {
        void onFlingToAction(View longClickView, int position, int direction);
    }

    public interface OnGeneralActionListener {
        void onPeek(View longClickView, int position);

        void onPop(View longClickView, int position);
    }

    public interface OnLongHoldListener {
        void onEnter(View view, int position);

        void onLongHold(View view, int position);
    }

    public interface OnHoldAndReleaseListener {
        void onHold(View view, int position);

        void onLeave(View view, int position);

        void onRelease(View view, int position);
    }

}
