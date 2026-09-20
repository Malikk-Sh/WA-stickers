package com.malikksh.wastickers;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Bottom sheet for the real media sources currently supported by the Android app. */
final class AddSourceSheet {
    interface Host {
        void onGallery();
        void onFile();
    }

    private AddSourceSheet() {}

    static void show(Context context, Host host) {
        if (context == null || host == null) return;

        Dialog dialog = new Dialog(context);
        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 22));

        GradientDrawable background = new GradientDrawable();
        background.setColor(UiComponents.color(context, R.color.app_surface));
        float radius = dp(context, 26);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        background.setStroke(dp(context, 1), UiComponents.color(context, R.color.app_border));
        sheet.setBackground(background);

        TextView handle = new TextView(context);
        handle.setText("—");
        handle.setGravity(Gravity.CENTER);
        handle.setTextSize(20);
        handle.setTextColor(UiComponents.color(context, R.color.app_text_secondary));
        sheet.addView(handle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 24)));

        TextView title = UiComponents.cardTitle(context, "Добавить стикер");
        LinearLayout.LayoutParams titleParams = UiComponents.matchWrap();
        titleParams.topMargin = dp(context, 4);
        sheet.addView(title, titleParams);

        TextView hint = UiComponents.metadata(
                context,
                "Выберите источник. Камера не показывается, потому что приложение её не использует."
        );
        LinearLayout.LayoutParams hintParams = UiComponents.matchWrap();
        hintParams.topMargin = dp(context, 4);
        sheet.addView(hint, hintParams);

        addAction(sheet, "Галерея", () -> {
            dialog.dismiss();
            host.onGallery();
        });
        addAction(sheet, "Файл", () -> {
            dialog.dismiss();
            host.onFile();
        });

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.38f;
            window.setAttributes(params);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private static void addAction(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(parent.getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(parent.getContext(), 16), 0, dp(parent.getContext(), 16), 0);
        UiComponents.styleOutlineButton(button, true);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(parent.getContext(), 52));
        params.topMargin = dp(parent.getContext(), 10);
        parent.addView(button, params);
    }

    private static int dp(Context context, int value) {
        return UiComponents.dp(context, value);
    }
}
