package com.malikksh.wastickers;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small semantic text-input dialog reused by create/rename flows. */
final class PackNameDialog {
    interface Callback {
        void onConfirmed(String name);
    }

    private static final int MAX_NAME = 60;

    private PackNameDialog() {}

    static void show(Context context,
                     String title,
                     String initialValue,
                     String confirmLabel,
                     Callback callback) {
        show(context, title, initialValue, confirmLabel, null, callback);
    }

    static void show(Context context, String title, String initialValue, String confirmLabel,
                     String excludeId, Callback callback) {
        if (context == null || callback == null) return;

        Dialog dialog = new Dialog(context);
        FrameLayout outer = new FrameLayout(context);
        int outerPadding = dp(context, 24);
        outer.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        LinearLayout card = new LinearLayout(context);
        card.setId(R.id.pack_name_dialog);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 20);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(UiComponents.rounded(
                context, R.color.app_surface, R.dimen.radius_card));
        outer.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        TextView heading = UiComponents.sectionTitle(context, title);
        heading.setTextSize(20);
        card.addView(heading, UiComponents.matchWrap());

        TextView label = UiComponents.metadata(context, "Название");
        label.setTextColor(UiComponents.color(context, R.color.app_text_primary));
        label.setTypeface(Typeface.create("sans", Typeface.BOLD));
        LinearLayout.LayoutParams labelParams = UiComponents.matchWrap();
        labelParams.topMargin = dp(context, 16);
        card.addView(label, labelParams);

        EditText input = new EditText(context);
        input.setId(R.id.pack_name_dialog_input);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME)});
        input.setText(initialValue == null ? "" : initialValue);
        input.setSelectAllOnFocus(true);
        input.setTextSize(16);
        input.setTextColor(UiComponents.color(context, R.color.app_text_primary));
        input.setHintTextColor(UiComponents.color(context, R.color.app_text_tertiary));
        input.setHint("Мои стикеры");
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        GradientDrawable inputBackground = UiComponents.rounded(
                context, R.color.app_surface_variant, R.dimen.radius_control);
        inputBackground.setStroke(dp(context, 1),
                UiComponents.color(context, R.color.app_border));
        input.setBackground(inputBackground);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 54));
        inputParams.topMargin = dp(context, 8);
        card.addView(input, inputParams);

        TextView counter = UiComponents.metadata(context, "0 / " + MAX_NAME);
        counter.setId(R.id.pack_name_dialog_counter);
        counter.setGravity(Gravity.END);
        LinearLayout.LayoutParams counterParams = UiComponents.matchWrap();
        counterParams.topMargin = dp(context, 4);
        card.addView(counter, counterParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = UiComponents.matchWrap();
        actionsParams.topMargin = dp(context, 16);
        card.addView(actions, actionsParams);

        Button cancel = new Button(context);
        cancel.setId(R.id.pack_name_dialog_cancel);
        cancel.setText("Отмена");
        UiComponents.styleOutlineButton(cancel, true);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(context, 50), 1f));

        Button confirm = new Button(context);
        confirm.setId(R.id.pack_name_dialog_confirm);
        confirm.setText(confirmLabel == null ? "Сохранить" : confirmLabel);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, dp(context, 50), 1f);
        confirmParams.leftMargin = dp(context, 8);
        actions.addView(confirm, confirmParams);

        Runnable updateState = () -> {
            String raw = input.getText() == null ? "" : input.getText().toString();
            counter.setText(raw.length() + " / " + MAX_NAME);
            boolean duplicate = !PackStore.isNameAvailable(context, raw, excludeId);
            input.setError(duplicate ? "Набор с таким названием уже существует" : null);
            UiComponents.stylePrimaryButton(confirm, PackNames.valid(raw) && !duplicate);
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateState.run(); }
        });
        updateState.run();

        cancel.setOnClickListener(v -> dialog.dismiss());
        Runnable confirmAction = () -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            name = PackNames.display(name);
            if (!PackNames.valid(name) || !PackStore.isNameAvailable(context, name, excludeId)) {
                updateState.run();
                return;
            }
            dialog.dismiss();
            callback.onConfirmed(name);
        };
        confirm.setOnClickListener(v -> confirmAction.run());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            if (!confirm.isEnabled()) return true;
            confirmAction.run();
            return true;
        });

        dialog.setContentView(outer);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.58f;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        Motion.dialog(card);
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        input.postDelayed(() -> {
            if (!dialog.isShowing()) return;
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, Motion.enabled(context) ? 180 : 0);
    }

    private static int dp(Context context, int value) {
        return UiComponents.dp(context, value);
    }
}
