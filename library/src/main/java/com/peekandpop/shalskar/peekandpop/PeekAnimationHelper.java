package com.peekandpop.shalskar.peekandpop;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
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
    private Context context;

    public PeekAnimationHelper(Context context, View peekLayout, View peekView) {
        this.context = context;
        this.peekLayout = peekLayout;
        this.peekView = peekView;
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
        animatorLayoutAlpha.setInterpolator(new DecelerateInterpolator(1.5f));

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
     **/
    public void animateFling(float velocityX, float velocityY, int duration, long popTime, float flingVelocityMax) {
        long timeDifference = System.currentTimeMillis() - popTime;
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            float translationAmount = Math.max(velocityY / 8, flingVelocityMax);
            ObjectAnimator animatorTranslateY = ObjectAnimator.ofFloat(peekView, "translationY", translationAmount);
            animatorTranslateY.setInterpolator(new DecelerateInterpolator());
            animatorTranslateY.setDuration(Math.max(0, duration - timeDifference));
            animatorTranslateY.start();
        } else if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            float translationAmount = Math.max(velocityX / 8, flingVelocityMax);
            ObjectAnimator animatorTranslateX = ObjectAnimator.ofFloat(peekView, "translationX", translationAmount);
            animatorTranslateX.setInterpolator(new DecelerateInterpolator());
            animatorTranslateX.setDuration(Math.max(0, duration - timeDifference));
            animatorTranslateX.start();
        }
    }
}
