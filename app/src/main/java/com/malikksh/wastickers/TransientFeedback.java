package com.malikksh.wastickers;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Lightweight in-app snackbar used without adding a Material dependency. */
final class TransientFeedback {
    private static final String TAG = "wa_transient_feedback";
    private static final long DURATION_MS = 3_500L;

    private TransientFeedback() {}

    static void show(Activity activity, String message) {
        show(activity, message, null, null);
    }

    static void show(Activity activity, String message, String actionLabel, Runnable action) {
        if (activity == null || message == null || message.trim().isEmpty()) return;
        FrameLayout root = activity.findViewById(android.R.id.content);
        if (root == null) return;

        View previous = root.findViewWithTag(TAG);
        if (previous != null) root.removeView(previous);

        LinearLayout bar = new LinearLayout(activity);
        bar.setTag(TAG);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 8), dp(activity, 10));
        bar.setBackground(rounded(
                activity.getResources().getColor(R.color.app_feedback_surface),
                dp(activity, 14)));
        if (android.os.Build.VERSION.SDK_INT >= 21) bar.setElevation(dp(activity, 6));

        TextView text = new TextView(activity);
        text.setText(message);
        text.setTextSize(14);
        text.setTextColor(activity.getResources().getColor(R.color.app_feedback_text));
        text.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        text.setMaxLines(2);
        bar.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (actionLabel != null && !actionLabel.trim().isEmpty() && action != null) {
            Button button = new Button(activity);
            button.setText(actionLabel);
            button.setAllCaps(false);
            button.setTextSize(13);
            button.setTypeface(Typeface.create("sans", Typeface.BOLD));
            button.setTextColor(activity.getResources().getColor(R.color.app_primary));
            button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            button.setMinWidth(dp(activity, 72));
            button.setMinHeight(dp(activity, 48));
            button.setOnClickListener(v -> {
                if (bar.getParent() instanceof ViewGroup) {
                    ((ViewGroup) bar.getParent()).removeView(bar);
                }
                action.run();
            });
            bar.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(activity, 48)));
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        params.leftMargin = dp(activity, 16);
        params.rightMargin = dp(activity, 16);
        params.bottomMargin = activity.findViewById(R.id.nav_create) != null
                ? dp(activity, 104)
                : dp(activity, 20);
        root.addView(bar, params);
        bar.postDelayed(() -> {
            if (bar.getParent() instanceof ViewGroup) {
                ((ViewGroup) bar.getParent()).removeView(bar);
            }
        }, DURATION_MS);
    }

    private static GradientDrawable rounded(int fillColor, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
