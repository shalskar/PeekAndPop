package com.peekandpop.shalskar.peekandpop;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.res.Configuration;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Created by Vincent on 21/01/2016.
 *
 * Helper class for animating the PeekAndPop views
 */
public class PeekAnimationHelper {

    /**
     * Occurs on on long hold.
     *
     * Animates the peek view to fade in and scale to it's full size.
     * Also fades the peek background layout in.
     */
    public static void animatePeek(View peekLayout, View peekView, int duration) {
        peekView.setAlpha(1);
        ObjectAnimator animatorLayoutAlpha = ObjectAnimator.ofFloat(peekLayout, "alpha", 1);
        animatorLayoutAlpha.setDuration(duration);
        ObjectAnimator animatorScaleX = ObjectAnimator.ofFloat(peekView, "scaleX", 1);
        animatorScaleX.setDuration(duration);
        ObjectAnimator animatorScaleY = ObjectAnimator.ofFloat(peekView, "scaleY", 1);
        animatorScaleY.setDuration(duration);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.play(animatorScaleX).with(animatorScaleY);

        animatorSet.start();
        animatorLayoutAlpha.start();
    }

    /**
     * Occurs on touch up.
     *
     * Animates the peek view to return to it's original position and shrink.
     * Also animate the peek background layout to fade out.
     */
    public static void animatePop(View peekLayout, View peekView, Animator.AnimatorListener animatorListener, int duration, int orientation) {
        ObjectAnimator animatorLayoutAlpha = ObjectAnimator.ofFloat(peekLayout, "alpha", 0);
        animatorLayoutAlpha.setDuration(duration);
        animatorLayoutAlpha.addListener(animatorListener);
        animatorLayoutAlpha.start();
        animateReturn(peekView, duration, orientation);
    }

    /**
     * Occurs when the peek view is dragged but not flung.
     *
     * Animate the peek view back to it's original position and shrink it.
     */
    public static void animateReturn(View peekView, int duration, int orientation) {
        ObjectAnimator animatorTranslate = null;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            animatorTranslate = ObjectAnimator.ofFloat(peekView, "translationY", 0);
        } else {
            animatorTranslate = ObjectAnimator.ofFloat(peekView, "translationX", 0);
        }
        ObjectAnimator animatorShrinkY = ObjectAnimator.ofFloat(peekView, "scaleY", 0.6f);
        ObjectAnimator animatorShrinkX = ObjectAnimator.ofFloat(peekView, "scaleX", 0.6f);
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
     *
     * Animate the peek view to expand slightly.
     */
    public static void animateExpand(View peekView, int duration, long popTime) {
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
     *
     * Animate the peek view up towards the top of the screen.
     * The duration of the animation is the same as the pop animate, minus
     * the time since the pop occurred.
     * If there is a fling to action layout, animate this as well.
     **/
    public static void animateFling(View peekView, View flingToActionViewLayout, float velocityX, float velocityY,
                                    int duration, long popTime, int orientation, int flingVelocityThreshold, float flingVelocityMax) {
        long timeDifference = System.currentTimeMillis() - popTime;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (velocityY < flingVelocityThreshold) {
                float translationAmount = Math.max(velocityY / 16, flingVelocityMax);
                ObjectAnimator animatorTranslateY = ObjectAnimator.ofFloat(peekView, "translationY", translationAmount);
                animatorTranslateY.setInterpolator(new DecelerateInterpolator());
                animatorTranslateY.setDuration(Math.max(0, duration - timeDifference));
                animatorTranslateY.start();
                if (flingToActionViewLayout != null) {
                    flingToActionViewLayout.animate().setDuration(duration / 2).alpha(0).translationY(translationAmount / 3)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (velocityX < flingVelocityThreshold) {
                float translationAmount = Math.max(velocityX / 16, flingVelocityMax);
                ObjectAnimator animatorTranslateX = ObjectAnimator.ofFloat(peekView, "translationX", translationAmount);
                animatorTranslateX.setInterpolator(new DecelerateInterpolator());
                animatorTranslateX.setDuration(Math.max(0, duration - timeDifference));
                animatorTranslateX.start();
                if (flingToActionViewLayout != null) {
                    flingToActionViewLayout.animate().setDuration(duration / 2).alpha(0.5f).translationX(translationAmount / 3)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }
            }
        }
    }
}
