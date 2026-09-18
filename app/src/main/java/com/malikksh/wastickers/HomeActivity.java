package com.malikksh.wastickers;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class HomeActivity extends MainActivity {
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int SOFT = 0xFFEAF5EF;

    private TextView packsMeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        injectSavedPacksCard();
        updateSavedPacksSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSavedPacksSummary();
    }

    private void injectSavedPacksCard() {
        FrameLayout content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View first = content.getChildAt(0);
        if (!(first instanceof ScrollView)) return;

        ScrollView scroll = (ScrollView) first;
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout root = (LinearLayout) scroll.getChildAt(0);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpHome(18), dpHome(16), dpHome(18), dpHome(16));
        card.setBackground(roundedHome(CARD, 20));
        if (android.os.Build.VERSION.SDK_INT >= 21) card.setElevation(dpHome(2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, matchWrapHome());

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        header.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = textHome("Мои наборы", 17, TEXT, Typeface.BOLD);
        texts.addView(title, matchWrapHome());
        packsMeta = textHome("", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrapHome();
        metaParams.topMargin = dpHome(3);
        texts.addView(packsMeta, metaParams);

        Button open = new Button(this);
        open.setText("Открыть");
        open.setAllCaps(false);
        open.setTextSize(14);
        open.setTextColor(Color.WHITE);
        open.setTypeface(Typeface.create("sans", Typeface.BOLD));
        open.setBackground(roundedHome(PRIMARY, 13));
        open.setOnClickListener(v -> startActivity(new Intent(this, SavedPacksActivity.class)));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(dpHome(92), dpHome(46));
        openParams.leftMargin = dpHome(10);
        header.addView(open, openParams);

        TextView hint = textHome(
                "Повторно добавляйте наборы в WhatsApp, переименовывайте или удаляйте их.",
                12, MUTED, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams hintParams = matchWrapHome();
        hintParams.topMargin = dpHome(8);
        card.addView(hint, hintParams);

        LinearLayout.LayoutParams cardParams = matchWrapHome();
        cardParams.topMargin = dpHome(12);
        int insertIndex = Math.min(1, root.getChildCount());
        root.addView(card, insertIndex, cardParams);
    }

    private void updateSavedPacksSummary() {
        if (packsMeta == null) return;
        List<PackStore.Pack> packs = PackStore.getPacks(this);
        if (packs.isEmpty()) {
            packsMeta.setText("Пока нет сохранённых наборов");
        } else {
            int animated = 0;
            for (PackStore.Pack pack : packs) {
                if (pack.animated) animated++;
            }
            int staticCount = packs.size() - animated;
            StringBuilder text = new StringBuilder().append(packs.size()).append(" сохранено");
            if (staticCount > 0) text.append(" · фото ").append(staticCount);
            if (animated > 0) text.append(" · анимация ").append(animated);
            packsMeta.setText(text.toString());
        }
    }

    private TextView textHome(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable roundedHome(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpHome(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrapHome() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dpHome(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
