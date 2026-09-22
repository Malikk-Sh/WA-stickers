package com.malikksh.wastickers;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

final class ThemedDialogs {
    static void confirm(Context context, String title, String message, String action, Runnable confirmed) {
        Dialog dialog = new Dialog(context);
        LinearLayout content = UiComponents.card(context);
        content.addView(UiComponents.sectionTitle(context, title));
        TextView body = UiComponents.metadata(context, message);
        body.setPadding(0, UiComponents.dp(context, 16), 0, UiComponents.dp(context, 16));
        content.addView(body);
        LinearLayout actions = new LinearLayout(context);
        Button cancel = new Button(context);
        cancel.setText("Отмена");
        UiComponents.styleOutlineButton(cancel, true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, UiComponents.dp(context, 48), 1));
        Button remove = new Button(context);
        remove.setText(action);
        UiComponents.styleDestructiveButton(remove, true);
        remove.setOnClickListener(v -> { dialog.dismiss(); confirmed.run(); });
        actions.addView(remove, new LinearLayout.LayoutParams(0, UiComponents.dp(context, 48), 1));
        content.addView(actions);
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(context.getResources().getDisplayMetrics().widthPixels - UiComponents.dp(context, 40),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        Motion.dialog(content);
    }
}
