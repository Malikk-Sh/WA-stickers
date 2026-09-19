package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual primitives for the handoff redesign. Business state must stay outside this class. */
final class UiComponents {
    private UiComponents() {}

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dimen(context, R.dimen.space_16);
        card.setPadding(padding, padding, padding, padding);
        GradientDrawable background = rounded(
                context,
                R.color.app_surface,
                R.dimen.radius_card
        );
        background.setStroke(dp(context, 1), color(context, R.color.app_border));
        card.setBackground(background);
        return card;
    }

    static TextView screenTitle(Context context, String value) {
        return text(context, value, 28, R.color.app_text_primary, Typeface.BOLD);
    }

    static TextView sectionTitle(Context context, String value) {
        return text(context, value, 22, R.color.app_text_primary, Typeface.BOLD);
    }

    static TextView cardTitle(Context context, String value) {
        return text(context, value, 18, R.color.app_text_primary, Typeface.BOLD);
    }

    static TextView body(Context context, String value) {
        return text(context, value, 16, R.color.app_text_primary, Typeface.NORMAL);
    }

    static TextView metadata(Context context, String value) {
        return text(context, value, 13, R.color.app_text_secondary, Typeface.NORMAL);
    }

    static void stylePrimaryButton(Button button, boolean enabled) {
        Context context = button.getContext();
        button.setEnabled(enabled);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dimen(context, R.dimen.touch_target));
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(context,
                enabled ? R.color.app_on_primary : R.color.app_disabled_text));
        button.setBackground(rounded(
                context,
                enabled ? R.color.app_primary : R.color.app_disabled_surface,
                R.dimen.radius_card
        ));
        button.setAlpha(enabled ? 1f : 0.82f);
    }

    static void styleTonalButton(Button button, boolean enabled) {
        Context context = button.getContext();
        button.setEnabled(enabled);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dimen(context, R.dimen.touch_target));
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(context,
                enabled ? R.color.app_primary : R.color.app_disabled_text));
        button.setBackground(rounded(
                context,
                enabled ? R.color.app_primary_container : R.color.app_disabled_surface,
                R.dimen.radius_card
        ));
        button.setAlpha(enabled ? 1f : 0.82f);
    }

    static void styleOutlineButton(Button button, boolean enabled) {
        Context context = button.getContext();
        button.setEnabled(enabled);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dimen(context, R.dimen.touch_target));
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(context,
                enabled ? R.color.app_primary : R.color.app_disabled_text));
        GradientDrawable background = rounded(
                context,
                R.color.app_surface,
                R.dimen.radius_card
        );
        background.setStroke(dp(context, 1), color(context,
                enabled ? R.color.app_border : R.color.app_disabled_surface));
        button.setBackground(background);
        button.setAlpha(enabled ? 1f : 0.82f);
    }

    static void styleDestructiveButton(Button button, boolean enabled) {
        Context context = button.getContext();
        button.setEnabled(enabled);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dimen(context, R.dimen.touch_target));
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(context,
                enabled ? R.color.app_error : R.color.app_disabled_text));
        GradientDrawable background = rounded(
                context,
                enabled ? R.color.app_error_container : R.color.app_disabled_surface,
                R.dimen.radius_card
        );
        background.setStroke(dp(context, 1), color(context,
                enabled ? R.color.app_error : R.color.app_disabled_surface));
        button.setBackground(background);
        button.setAlpha(enabled ? 1f : 0.82f);
    }

    static void styleSegment(Button button, boolean selected) {
        Context context = button.getContext();
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dimen(context, R.dimen.touch_target));
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(color(context,
                selected ? R.color.app_on_primary : R.color.app_text_secondary));
        button.setBackground(rounded(
                context,
                selected ? R.color.app_primary : R.color.app_transparent,
                R.dimen.radius_pill
        ));
    }

    static GradientDrawable rounded(Context context, int colorRes, int radiusRes) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(context, colorRes));
        drawable.setCornerRadius(dimen(context, radiusRes));
        return drawable;
    }

    static TextView text(Context context, String value, int sizeSp, int colorRes, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color(context, colorRes));
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static int color(Context context, int resourceId) {
        return context.getResources().getColor(resourceId, context.getTheme());
    }

    static int dimen(Context context, int resourceId) {
        return Math.round(context.getResources().getDimension(resourceId));
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
