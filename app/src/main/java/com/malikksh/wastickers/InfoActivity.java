package com.malikksh.wastickers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Lightweight secondary screen for Help, About and Privacy content. */
public class InfoActivity extends Activity {
    static final String EXTRA_TITLE = "info.title";
    static final String EXTRA_BODY = "info.body";

    static Intent intent(Context context, String title, String body) {
        return new Intent(context, InfoActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppSettings.wrapForAppearance(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings.configureRuntime(this);
        setContentView(buildUi());
        AppSettings.applySystemBars(this);
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(UiComponents.color(this, R.color.app_background));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, UiComponents.matchWrap());

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("Назад");
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackground(UiComponents.rounded(
                this, R.color.app_primary_container, R.dimen.radius_card));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        String titleValue = getIntent().getStringExtra(EXTRA_TITLE);
        if (titleValue == null || titleValue.trim().isEmpty()) titleValue = "WA Stickers";
        TextView title = UiComponents.screenTitle(this, titleValue);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(14);
        header.addView(title, titleParams);

        LinearLayout card = UiComponents.card(this);
        LinearLayout.LayoutParams cardParams = UiComponents.matchWrap();
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);

        String bodyValue = getIntent().getStringExtra(EXTRA_BODY);
        if (bodyValue == null) bodyValue = "";
        TextView body = UiComponents.body(this, bodyValue);
        body.setLineSpacing(dp(3), 1.06f);
        card.addView(body, UiComponents.matchWrap());
        return scroll;
    }

    private int dp(int value) {
        return UiComponents.dp(this, value);
    }
}
