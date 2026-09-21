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
    static void tick(View view) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }
}
