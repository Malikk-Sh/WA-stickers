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

/** Bottom-aligned action sheet for one saved pack. Business actions are supplied by the host. */
final class PackActionsSheet {
    interface Host {
        void onRename();
        void onDuplicate();
        void onDelete();
        void onDetails();
        default void onEdit() {}
    }

    private PackActionsSheet() {}

    static void show(Context context, PackStore.Pack pack, Host host) {
        if (context == null || pack == null || host == null) return;

        Dialog dialog = new Dialog(context);
        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 20));

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

        TextView title = UiComponents.cardTitle(context, pack.name);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = UiComponents.matchWrap();
        titleParams.topMargin = dp(context, 4);
        sheet.addView(title, titleParams);

        TextView meta = UiComponents.metadata(
                context,
                (pack.animated ? "Анимация" : "Фото") + " · " + pack.stickerCount + " стикеров"
        );
        LinearLayout.LayoutParams metaParams = UiComponents.matchWrap();
        metaParams.topMargin = dp(context, 3);
        sheet.addView(meta, metaParams);

        addAction(sheet, "Редактировать", false, () -> { dialog.dismiss(); host.onEdit(); });
        addAction(sheet, "Переименовать", false, () -> {
            dialog.dismiss();
            host.onRename();
        });
        addAction(sheet, "Дублировать", false, () -> {
            dialog.dismiss();
            host.onDuplicate();
        });
        addAction(sheet, "Детали", false, () -> {
            dialog.dismiss();
            host.onDetails();
        });
        addAction(sheet, "Удалить", true, () -> {
            dialog.dismiss();
            host.onDelete();
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
        Motion.sheet(sheet);
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private static void addAction(LinearLayout parent,
                                  String label,
                                  boolean destructive,
                                  Runnable action) {
        Button button = new Button(parent.getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(parent.getContext(), 16), 0, dp(parent.getContext(), 16), 0);
        if (destructive) {
            UiComponents.styleDestructiveButton(button, true);
        } else {
            UiComponents.styleOutlineButton(button, true);
        }
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
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
