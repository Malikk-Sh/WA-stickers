package com.malikksh.wastickers;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/** Presentation-only visibility policy for the 60-character pack-name counter. */
final class CreateNameCounterPolicy {
    private static final int SHOW_AFTER = 48; // 80% of 60.

    private CreateNameCounterPolicy() {}

    static void attach(EditText field, TextView counter) {
        if (field == null || counter == null) return;
        Runnable refresh = () -> counter.setVisibility(
                field.hasFocus() || field.length() >= SHOW_AFTER ? View.VISIBLE : View.GONE);

        field.setOnFocusChangeListener((view, hasFocus) -> refresh.run());
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                refresh.run();
            }
        });
        refresh.run();
    }
}
