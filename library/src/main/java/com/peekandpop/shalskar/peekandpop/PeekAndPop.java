package com.peekandpop.shalskar.peekandpop;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import com.peekandpop.shalskar.peekandpop.model.LongHoldView;

import java.util.ArrayList;

public class PeekAndPop {

    // These static values will be converted from dp to px
    protected static final int DRAG_AMOUNT = 96;
    protected static final int DRAG_TO_ACTION_THRESHOLD = 32;
    protected static final int DRAG_TO_ACTION_VIEW_MARGIN = 8;
    protected static final int DRAG_TO_ACTION_MOVE_AMOUNT = 128;

    protected static final float DRAG_TO_ACTION_START_SCALE = 0.25f;

    protected static final long LONG_HOLD_DURATION = 500;

    protected int dragAmount;
    protected int dragToActionThreshold;
    protected int dragToActionViewMargin;
    protected int dragToActionMoveAmount;
    protected int maxDrag;

    protected Builder builder;
    protected ViewGroup peekLayout;
    protected View peekView;

    protected View dragToActionViewLayout;

    protected ArrayList<LongHoldView> longHoldViews;

    protected OnDragToActionListener onDragToActionListener;
    protected OnGeneralActionListener onGeneralActionListener;
    protected OnLongHoldListener onLongHoldListener;

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
        this.onDragToActionListener = builder.onDragToActionListener;
        this.onGeneralActionListener = builder.onGeneralActionListener;
        this.onLongHoldListener = builder.onLongHoldListener;

        this.longHoldViews = new ArrayList<>();

        orientation = builder.activity.getResources().getConfiguration().orientation;

        initialiseValues();
        createPeekView();
        initialiseGestureListeners();
    }

    /**
     * Initialise all static values, converting them from dp to px.
     */
    protected void initialiseValues() {
        dragAmount = convertDpToPx(DRAG_AMOUNT);
        dragToActionThreshold = convertDpToPx(DRAG_TO_ACTION_THRESHOLD);
        dragToActionViewMargin = convertDpToPx(DRAG_TO_ACTION_VIEW_MARGIN);
        dragToActionMoveAmount = convertDpToPx(DRAG_TO_ACTION_MOVE_AMOUNT);
    }

    /**
     * Inflate the peekView, add it to the peekLayout with a shaded/blurred background,
     * bring it to the front and set the peekLayout to have an alpha of 0. Get the peekView's
     * original Y position for use when dragging.
     * <p/>
     * If a dragToActionViewLayout is supplied, inflate the dragToActionViewLayout
     */
    protected void createPeekView() {
        LayoutInflater inflater = LayoutInflater.from(builder.activity);
        ViewGroup containerView = (ViewGroup) builder.activity.findViewById(android.R.id.content);

        // Center onPeek view in the onPeek layout and add to the container view group
        peekLayout = (RelativeLayout) inflater.inflate(R.layout.peek_background, containerView, false);

        peekView = inflater.inflate(builder.peekLayoutId, peekLayout, false);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) peekView.getLayoutParams();
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        peekLayout.addView(peekView, layoutParams);

        containerView.addView(peekLayout);

        // If lollipop or above, use elevation to bring onPeek layout to the front
        if (Build.VERSION.SDK_INT >= 21) {
            peekLayout.setElevation(10f);
        } else {
            peekLayout.bringToFront();
            containerView.requestLayout();
            containerView.invalidate();
        }
        peekLayout.setAlpha(0);

        // Once the onPeek view has inflated fully, this will also update if the view changes in size change
        peekView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                initialisePeekViewOriginalPosition();
                initialiseMaxDrag();
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

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            layoutParams.bottomMargin = dragToActionViewMargin;
        } else {
            layoutParams.addRule(RelativeLayout.CENTER_VERTICAL);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            layoutParams.rightMargin = dragToActionViewMargin;
        }

        peekLayout.addView(dragToActionViewLayout, layoutParams);
        dragToActionViewLayout.setAlpha(0);
        dragToActionViewLayout.setScaleX(DRAG_TO_ACTION_START_SCALE);
        dragToActionViewLayout.setScaleY(DRAG_TO_ACTION_START_SCALE);

    }

    /**
     * Set an onLongClick, onClick and onTouch listener for each long click view
     */
    protected void initialiseGestureListeners() {
        for (int i = 0; i < builder.longClickViews.size(); i++) {
            initialiseGestureListener(builder.longClickViews.get(i), -1);
        }
    }

    protected void initialiseGestureListener(View view, int position) {
        view.setOnLongClickListener(new PeekAndPopOnLongClickListener(position));
        view.setOnTouchListener(new PeekAndPopOnTouchListener(position));
    }

    /**
     * Check if user has moved or lifted their finger.
     * <p/>
     * If lifted, onPop the view and check if their is a drag to action listener, check
     * if it had been dragged enough and send an event if so.
     * <p/>
     * If moved, check if the user has entered the bounds of the onPeek view.
     * If the user is within the bounds, and is at the edges of the view, then
     * move it appropriately
     */

    private void respondToTouch(View v, MotionEvent event, int position) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            pop(v, position);
            if (onDragToActionListener != null)
                checkIfDraggedToAction(v, position);

        } else if (event.getAction() == MotionEvent.ACTION_MOVE && onDragToActionListener != null) {
            int touchX = (int) event.getRawX();
            int touchY = (int) event.getRawY();

            if (hasEnteredPeekViewBounds) {
                setOffset(touchX, touchY);
                movePeekView(touchX, touchY);
                if(onLongHoldListener != null)
                    checkLongHoldViews(touchX, touchY, position);
            } else if (pointInViewBounds(peekView, touchX, touchY)) {
                hasEnteredPeekViewBounds = true;
            }
        }
    }

    private void setOffset(int x, int y) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (initialTouchOffset < peekView.getHeight() / 2)
                initialTouchOffset = Math.max(calculateOffset(x, y), initialTouchOffset);
        } else {
            if (initialTouchOffset < peekView.getWidth() / 2)
                initialTouchOffset = Math.max(calculateOffset(x, y), initialTouchOffset);
        }
    }

    /**
     * Check all the long hold views to see if they are being held and if so for how long
     * they have been held and send a long hold event if > 500ms.
     *
     * @param x
     * @param y
     * @param position
     */
    private void checkLongHoldViews(int x, int y, int position){
        for (int i = 0; i < longHoldViews.size(); i++) {
            LongHoldView longHoldView = longHoldViews.get(i);

            if(pointInViewBounds(longHoldView.getView(), x, y)){
                long currentTime = System.currentTimeMillis();
                if(longHoldView.getHoldStart() == -1){
                    longHoldView.setHoldStart(currentTime);
                } else if(longHoldView.getHoldStart() == 0){
                    // Has already sent an event
                } else if(currentTime - longHoldView.getHoldStart() > LONG_HOLD_DURATION) {
                    onLongHoldListener.onLongHold(longHoldView.getView(), position);

                    if(longHoldView.isReceiveMultipleEvents())
                        longHoldView.setHoldStart(-1);
                    else
                        longHoldView.setHoldStart(0);
                }
            } else {
                longHoldView.setHoldStart(-1);
            }
        }
    }

    /**
     * Check if the onPeek view has been dragged passed the drag to action threshold and
     * send a dragged to action event if it has.
     */
    private void checkIfDraggedToAction(View longClickView, int index) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (peekView.getY() < maxDrag + dragToActionThreshold)
                onDragToActionListener.onDraggedToAction(longClickView, index);
        } else {
            if (peekView.getX() < maxDrag + dragToActionThreshold)
                onDragToActionListener.onDraggedToAction(longClickView, index);
        }
    }

    private void initialisePeekViewOriginalPosition() {
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
    private void initialiseMaxDrag() {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            maxDrag = (int) peekView.getY() - dragAmount;
        } else {
            maxDrag = (int) peekView.getX() - dragAmount;
        }

        // todo change to exception
        if (maxDrag < 0) {
            maxDrag = 0;
        }
    }

    private int calculateOffset(int touchX, int touchY) {
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            return touchY - (int) peekViewOriginalRawPosition[1];
        } else {
            return touchX - (int) peekViewOriginalRawPosition[0];
        }
    }

    private void peek(View longClickView, int index) {
        if (this.onGeneralActionListener != null) {
            this.onGeneralActionListener.onPeek(longClickView, index);
        }
        peekLayout.setAlpha(1f);

        Animation peekViewAnimation = AnimationUtils.loadAnimation(builder.activity, R.anim.peek);
        peekView.startAnimation(peekViewAnimation);

        Animation peekBackgroundAnimation = AnimationUtils.loadAnimation(builder.activity, R.anim.fade_in);
        peekLayout.startAnimation(peekBackgroundAnimation);

        if (builder.parentViewGroup != null) {
            builder.parentViewGroup.requestDisallowInterceptTouchEvent(true);
        }
    }

    private void pop(View longClickView, int index) {
        if (this.onGeneralActionListener != null) {
            this.onGeneralActionListener.onPop(longClickView, index);
        }
        resetViews();

        Animation popAnimation = AnimationUtils.loadAnimation(builder.activity, R.anim.pop);
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

        Animation fadeOutAnimation = AnimationUtils.loadAnimation(builder.activity, R.anim.fade_out);
        peekLayout.startAnimation(fadeOutAnimation);

        if (dragToActionViewLayout != null) {
            dragToActionViewLayout.startAnimation(fadeOutAnimation);
        }
    }

    /**
     * Reset all views back to their initial values, this done after the onPeek has popped.
     */
    private void resetViews() {
        hasEnteredPeekViewBounds = false;
        initialTouchOffset = -1;

        if (dragToActionViewLayout != null) {
            dragToActionViewLayout.setTranslationY(0);
            dragToActionViewLayout.setScaleX(DRAG_TO_ACTION_START_SCALE);
            dragToActionViewLayout.setScaleY(DRAG_TO_ACTION_START_SCALE);
            dragToActionViewLayout.setAlpha(0);
        }

        for (int i = 0; i < longHoldViews.size(); i++) {
            longHoldViews.get(i).setHoldStart(-1);
        }

    }

    /**
     * Move the onPeek view based on the x and y touch coordinates.
     * The further the view is pulled from it's original position, the less
     * it moves, creating an 'elastic' effect.
     *
     * @param touchX
     * @param touchY
     */
    private void movePeekView(int touchX, int touchY) {
        int[] l = new int[2];
        peekView.getLocationOnScreen(l);
        int peekViewRawX = l[0];
        int peekViewRawY = l[1];

        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            int adjust = (int) peekView.getY() - peekViewRawY;
            float adjustedPosition = touchY + adjust - initialTouchOffset;
            float totalDistanceTravelled = 0 - (adjustedPosition - peekViewOriginalPosition[1]);
            float amountToMove = peekViewOriginalPosition[1] - (totalDistanceTravelled / 3f + (float) Math.sqrt(totalDistanceTravelled) * 4);

            if (touchY > peekViewOriginalRawPosition[1] + initialTouchOffset) {
                peekView.setY(peekViewOriginalPosition[1]);
            } else if (amountToMove < 0) {
                peekView.setY(0);
            } else {
                peekView.setY(amountToMove);
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            int adjust = (int) peekView.getX() - peekViewRawX;
            float adjustedPosition = touchX + adjust - initialTouchOffset;
            float totalDistanceTravelled = 0 - (adjustedPosition - peekViewOriginalPosition[0]);
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
            dragToActionViewLayout.setTranslationY((-ratio * ratio / 3f) * dragToActionMoveAmount);
        } else {
            ratio = (peekViewOriginalPosition[0] - peekView.getX()) / (peekViewOriginalPosition[0] - maxDrag);
            dragToActionViewLayout.setTranslationX((-ratio * ratio / 3f) * dragToActionMoveAmount);
        }

        ratio = Math.min(1f, ratio - 0.25f);

        dragToActionViewLayout.setScaleX(Math.min(1f, 0.25f + ratio));
        dragToActionViewLayout.setScaleY(Math.min(1f, 0.25f + ratio));
        dragToActionViewLayout.setAlpha(ratio * 2);
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

    public void setOnDragToActionListener(OnDragToActionListener onDragToActionListener) {
        this.onDragToActionListener = onDragToActionListener;
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
    public void addLongHoldView(int longHoldViewId, boolean receiveMultipleEvents){
        this.longHoldViews.add(new LongHoldView(peekView.findViewById(longHoldViewId), -1, receiveMultipleEvents));
    }

    public View getPeekView() {
        return peekView;
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
        protected int dragToActionViewLayout = -1;
        protected OnDragToActionListener onDragToActionListener;
        protected OnGeneralActionListener onGeneralActionListener;
        protected OnLongHoldListener onLongHoldListener;

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
         * A view to be transitioned in while dragging the onPeek view.
         * This can be used to indicate to the user what the dragToAction will be.
         *
         * @param dragToActionViewLayout
         * @return
         */
        public Builder dragToActionViewLayout(int dragToActionViewLayout) {
            this.dragToActionViewLayout = dragToActionViewLayout;
            return this;
        }

        /**
         * A listener for when the onPeek view is dragged enough.
         *
         * @param onDragToActionListener
         * @return
         */
        public Builder onDragToActionListener(@NonNull OnDragToActionListener onDragToActionListener) {
            this.onDragToActionListener = onDragToActionListener;
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
         * @return The Builder instance so you can chain calls.
         */
        public Builder parentViewGroupToDisallowTouchEvents(@NonNull ViewGroup parentViewGroup) {
            this.parentViewGroup = parentViewGroup;
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

    /**
     * Listeners
     */

    public class PeekAndPopOnLongClickListener implements View.OnLongClickListener {

        private int position;

        public PeekAndPopOnLongClickListener(int position) {
            this.position = position;
        }

        @Override
        public boolean onLongClick(View v) {
            peek(v, position);
            return false;
        }
    }

    public class PeekAndPopOnTouchListener implements View.OnTouchListener {

        private int position;

        public PeekAndPopOnTouchListener(int position) {
            this.position = position;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            respondToTouch(v, event, position);
            return false;
        }
    }

    public interface OnDragToActionListener {
        public void onDraggedToAction(View longClickView, int position);
    }

    public interface OnGeneralActionListener {
        public void onPeek(View longClickView, int position);

        public void onPop(View longClickView, int position);
    }

    public interface OnLongHoldListener {
        public void onLongHold(View view, int position);
    }

}
