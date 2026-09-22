package com.malikksh.wastickers;

import android.content.Context;
import android.provider.Settings;
import android.view.View;

final class Motion {
    static boolean enabled(Context context) {
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }
    static void enter(View view) {
        if (!enabled(view.getContext())) return;
        view.setAlpha(0f);
        view.setTranslationY(UiComponents.dp(view.getContext(), 8));
        view.animate().alpha(1f).translationY(0f).setDuration(200).start();
    }
    static void sheet(View view) {
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        view.requestApplyInsets();
        if (!enabled(view.getContext())) return;
        view.setTranslationY(UiComponents.dp(view.getContext(), 48));
        view.setAlpha(0f);
        view.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }
    static void reflow(android.view.ViewGroup group, boolean spring) {
        if (!enabled(group.getContext()) || !group.isLaidOut()) return;
        android.transition.ChangeBounds bounds = new android.transition.ChangeBounds();
        bounds.setDuration(240);
        bounds.setInterpolator(spring ? new android.view.animation.OvershootInterpolator(.5f)
                : new android.view.animation.DecelerateInterpolator());
        android.transition.TransitionSet transition = new android.transition.TransitionSet()
                .addTransition(bounds).addTransition(new android.transition.Fade().setDuration(180));
        android.transition.TransitionManager.beginDelayedTransition(group, transition);
    }
    static void search(View view) {
        if (!enabled(view.getContext())) return;
        view.post(() -> {
            if (!view.isShown()) return;
            android.graphics.Rect from = new android.graphics.Rect(Math.max(0, view.getWidth() - UiComponents.dp(view.getContext(), 48)), 0, view.getWidth(), view.getHeight());
            android.graphics.Rect to = new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight());
            android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofObject(
                    view, "clipBounds", new android.animation.RectEvaluator(), from, to);
            animator.setDuration(240); animator.start();
        });
    }
    static void cover(View view) {
        if (!enabled(view.getContext())) return;
        view.setScaleX(.85f); view.setScaleY(.85f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
    }
    static void tileEnter(View view, long delay) {
        if (!enabled(view.getContext())) return;
        view.setAlpha(0f); view.setScaleX(.92f); view.setScaleY(.92f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay)
                .setDuration(180).withEndAction(() -> view.animate().setStartDelay(0)).start();
    }
    static void dialog(View view) {
        if (!enabled(view.getContext())) return;
        view.setAlpha(0f); view.setScaleX(.96f); view.setScaleY(.96f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
    }
    static void crossfade(View view) {
        if (!enabled(view.getContext())) return;
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(200).start();
    }
    static void tick(View view) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }
}
