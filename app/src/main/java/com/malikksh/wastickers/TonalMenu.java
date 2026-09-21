package com.malikksh.wastickers;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;

final class TonalMenu extends PopupMenu {
    private final Context context;
    private OnMenuItemClickListener listener;
    TonalMenu(Context context, View anchor) { super(context, anchor); this.context = context; }
    @Override public void setOnMenuItemClickListener(OnMenuItemClickListener listener) { this.listener = listener; }
    @Override public void show() {
        Dialog dialog = new Dialog(context);
        LinearLayout sheet = UiComponents.card(context);
        for (int i = 0; i < getMenu().size(); i++) {
            MenuItem item = getMenu().getItem(i);
            if (!item.isVisible()) continue;
            Button button = new Button(context);
            button.setText(item.getTitle());
            UiComponents.styleOutlineButton(button, item.isEnabled());
            button.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) listener.onMenuItemClick(item);
            });
            sheet.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiComponents.dp(context, 48)));
        }
        dialog.setContentView(sheet);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        Motion.sheet(sheet);
    }
}
