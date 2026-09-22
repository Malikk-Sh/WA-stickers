package com.malikksh.wastickers;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

/** A single moving selection pill; labels remain stationary. */
final class SegmentRow extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private View selected;
    private float left, right;
    private ValueAnimator animator;
    SegmentRow(Context context) { super(context); setOrientation(HORIZONTAL); }
    void select(View child) {
        if (selected == child) return;
        boolean animate = selected != null && isLaidOut() && Motion.enabled(getContext());
        selected = child;
        if (animator != null) animator.cancel();
        if (!animate) { left = child.getLeft(); right = child.getRight(); invalidate(); return; }
        float fromLeft = left, fromRight = right;
        animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            float t = (float) value.getAnimatedValue();
            left = fromLeft + (child.getLeft() - fromLeft) * t;
            right = fromRight + (child.getRight() - fromRight) * t;
            invalidate();
        });
        animator.start();
    }
    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (selected != null && (animator == null || !animator.isRunning())) {
            left = selected.getLeft(); right = selected.getRight();
        }
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        if (selected != null) {
            paint.setColor(getContext().getColor(R.color.app_primary));
            float radius = UiComponents.dp(getContext(), 24);
            canvas.drawRoundRect(left, selected.getTop(), right, selected.getBottom(), radius, radius, paint);
        }
        super.dispatchDraw(canvas);
    }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
