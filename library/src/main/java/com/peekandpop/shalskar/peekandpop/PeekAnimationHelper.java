package com.peekandpop.shalskar.peekandpop;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Created by Vincent on 21/01/2016.
 * <p/>
 * Helper class for animating the PeekAndPop views
 */
public class PeekAnimationHelper {

    private View peekLayout;
    private View peekView;
    private View flingToActionLayout;
    private Context context;
    private DisplayMetrics displayMetrics;

    public PeekAnimationHelper(Context context, View flingToActionLayout, View peekLayout, View peekView) {
        this.context = context;
        this.flingToActionLayout = flingToActionLayout;
        this.peekLayout = peekLayout;
        this.peekView = peekView;
        this.displayMetrics = context.getResources().getDisplayMetrics();
    }

    public void setPeekLayout(View peekLayout) {
        this.peekLayout = peekLayout;
    }

    public void setPeekView(View peekView) {
        this.peekView = peekView;
    }

    public void setFlingToActionLayout(View flingToActionLayout) {
        this.flingToActionLayout = flingToActionLayout;
    }

    /**
     * Occurs on on long hold.
     * <p/>
     * Animates the peek view to fade in and scale to it's full size.
     * Also fades the peek background layout in.
     */
    public void animatePeek(int duration) {
        peekView.setAlpha(1);
        ObjectAnimator animatorLayoutAlpha = ObjectAnimator.ofFloat(peekLayout, "alpha", 1);
        animatorLayoutAlpha.setInterpolator(new OvershootInterpolator(1.2f));
        animatorLayoutAlpha.setDuration(duration);
        ObjectAnimator animatorScaleX = ObjectAnimator.ofFloat(peekView, "scaleX", 1);
        animatorScaleX.setDuration(duration);
        ObjectAnimator animatorScaleY = ObjectAnimator.ofFloat(peekView, "scaleY", 1);
        animatorScaleY.setDuration(duration);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new OvershootInterpolator(1.2f));
        animatorSet.play(animatorScaleX).with(animatorScaleY);

        if (flingToActionLayout != null) {
            if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                flingToActionLayout.setTranslationY((displayMetrics.heightPixels / 2) - (peekView.getHeight() / 2));
                flingToActionLayout.animate().translationY(0).setInterpolator(new OvershootInterpolator(1.1f)).setDuration(duration).start();
            } else {
                flingToActionLayout.setTranslationX((displayMetrics.widthPixels / 2) - (peekView.getWidth() / 2));
                flingToActionLayout.animate().translationX(0).setInterpolator(new OvershootInterpolator(1.1f)).setDuration(duration).start();
            }
        }

        animatorSet.start();
        animatorLayoutAlpha.start();
    }

    /**
     * Occurs on touch up.
     * <p/>
     * Animates the peek view to return to it's original position and shrink.
     * Also animate the peek background layout to fade out.
     */
    public void animatePop(Animator.AnimatorListener animatorListener, int duration) {
        ObjectAnimator animatorLayoutAlpha = ObjectAnimator.ofFloat(peekLayout, "alpha", 0);
        animatorLayoutAlpha.setDuration(duration);
        animatorLayoutAlpha.addListener(animatorListener);

        if (flingToActionLayout != null) {
            if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                float flingToActionLayoutDestination = (displayMetrics.heightPixels / 2) - (peekView.getHeight() / 3);
                flingToActionLayout.animate().translationY(flingToActionLayoutDestination)
                        .setInterpolator(new DecelerateInterpolator(1.2f)).setDuration(duration).start();
            } else {
                float flingToActionLayoutDestination = (displayMetrics.widthPixels / 2) - (peekView.getWidth() / 3);
                flingToActionLayout.animate().translationX(flingToActionLayoutDestination)
                        .setInterpolator(new DecelerateInterpolator(1.2f)).setDuration(duration).start();
            }
        }

        animatorLayoutAlpha.start();
        animateReturn(duration);
    }

    /**
     * Occurs when the peek view is dragged but not flung.
     * <p/>
     * Animate the peek view back to it's original position and shrink it.
     */
    public void animateReturn(int duration) {
        ObjectAnimator animatorTranslate;
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            animatorTranslate = ObjectAnimator.ofFloat(peekView, "translationY", 0);
        } else {
            animatorTranslate = ObjectAnimator.ofFloat(peekView, "translationX", 0);
        }
        ObjectAnimator animatorShrinkY = ObjectAnimator.ofFloat(peekView, "scaleY", 0.75f);
        ObjectAnimator animatorShrinkX = ObjectAnimator.ofFloat(peekView, "scaleX", 0.75f);
        animatorShrinkX.setInterpolator(new DecelerateInterpolator());
        animatorShrinkY.setInterpolator(new DecelerateInterpolator());
        animatorTranslate.setInterpolator(new DecelerateInterpolator());
        animatorShrinkX.setDuration(duration);
        animatorShrinkY.setDuration(duration);
        animatorTranslate.setDuration(duration);
        animatorShrinkX.start();
        animatorShrinkY.start();
        animatorTranslate.start();
    }

    /**
     * Occurs when the peek view is flung.
     * <p/>
     * Animate the peek view to expand slightly.
     */
    public void animateExpand(int duration, long popTime) {
        long timeDifference = System.currentTimeMillis() - popTime;
        ObjectAnimator animatorExpandY = ObjectAnimator.ofFloat(peekView, "scaleY", 1.025f);
        ObjectAnimator animatorExpandX = ObjectAnimator.ofFloat(peekView, "scaleX", 1.025f);
        animatorExpandX.setInterpolator(new DecelerateInterpolator());
        animatorExpandY.setInterpolator(new DecelerateInterpolator());
        animatorExpandX.setDuration(Math.max(0, duration - timeDifference));
        animatorExpandY.setDuration(Math.max(0, duration - timeDifference));
        animatorExpandX.start();
        animatorExpandY.start();
    }


    /**
     * Occurs when the peek view is flung.
     * <p/>
     * Animate the peek view up towards the top of the screen.
     * The duration of the animation is the same as the pop animate, minus
     * the time since the pop occurred.
     * If there is a fling to action layout, animate this as well.
     **/
    public void animateFling(float velocityX, float velocityY, int duration, long popTime, float flingVelocityMax) {
        long timeDifference = System.currentTimeMillis() - popTime;
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            float translationAmount = Math.max(velocityY / 8, flingVelocityMax);
            ObjectAnimator animatorTranslateY = ObjectAnimator.ofFloat(peekView, "translationY", translationAmount);
            animatorTranslateY.setInterpolator(new DecelerateInterpolator());
            animatorTranslateY.setDuration(Math.max(0, duration - timeDifference));
            animatorTranslateY.start();
            if (flingToActionLayout != null) {
                flingToActionLayout.animate().setDuration(duration / 2).alpha(0).translationY(translationAmount / 3)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        } else if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            float translationAmount = Math.max(velocityX / 8, flingVelocityMax);
            ObjectAnimator animatorTranslateX = ObjectAnimator.ofFloat(peekView, "translationX", translationAmount);
            animatorTranslateX.setInterpolator(new DecelerateInterpolator());
            animatorTranslateX.setDuration(Math.max(0, duration - timeDifference));
            animatorTranslateX.start();
            if (flingToActionLayout != null) {
                flingToActionLayout.animate().setDuration(duration / 2).alpha(0.5f).translationX(translationAmount / 3)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    }
}
