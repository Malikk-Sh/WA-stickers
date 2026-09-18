package com.malikksh.wastickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SavedPacksActivity extends Activity {
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;
    private static final int BG = 0xFFF5F8F6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int SOFT = 0xFFEAF5EF;
    private static final int BORDER = 0xFFDCE7E1;

    private LinearLayout listContainer;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        setContentView(buildUi());
        renderPacks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listContainer != null) renderPacks();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(rounded(CARD, 20));
        if (Build.VERSION.SDK_INT >= 21) header.setElevation(dp(2));
        root.addView(header, matchWrap());

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow, matchWrap());

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(PRIMARY);
        back.setAllCaps(false);
        back.setBackground(rounded(SOFT, 13));
        back.setOnClickListener(v -> finish());
        titleRow.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titlesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesParams.leftMargin = dp(12);
        titleRow.addView(titles, titlesParams);

        titles.addView(text("Мои наборы", 22, TEXT, Typeface.BOLD));
        subtitle = text("", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(3);
        titles.addView(subtitle, subtitleParams);

        TextView hint = text(
                "Здесь хранятся последние наборы, созданные на этом устройстве.",
                13, MUTED, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(13);
        header.addView(hint, hintParams);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(12);
        root.addView(listContainer, listParams);

        return scroll;
    }

    private void renderPacks() {
        List<PackStore.Pack> packs = new ArrayList<>(PackStore.getPacks(this));
        Collections.reverse(packs);
        subtitle.setText(packs.isEmpty() ? "Пока пусто" : packs.size() + " из 10 сохранено");
        listContainer.removeAllViews();

        if (packs.isEmpty()) {
            LinearLayout empty = card();
            TextView title = text("Пока нет сохранённых наборов", 16, TEXT, Typeface.BOLD);
            empty.addView(title, matchWrap());
            TextView body = text(
                    "Создайте первый набор на главном экране — он автоматически появится здесь.",
                    13, MUTED, Typeface.NORMAL);
            LinearLayout.LayoutParams bodyParams = matchWrap();
            bodyParams.topMargin = dp(6);
            empty.addView(body, bodyParams);
            listContainer.addView(empty, matchWrap());
            return;
        }

        for (PackStore.Pack pack : packs) {
            listContainer.addView(buildPackCard(pack), withBottomMargin(dp(10)));
        }
    }

    private View buildPackCard(PackStore.Pack pack) {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, matchWrap());

        ImageView tray = new ImageView(this);
        tray.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tray.setBackground(rounded(SOFT, 14));
        tray.setContentDescription("Иконка набора " + pack.name);
        File trayFile = PackStore.getStickerFile(this, pack.id, "tray.png");
        Bitmap bitmap = BitmapFactory.decodeFile(trayFile.getAbsolutePath());
        if (bitmap != null) tray.setImageBitmap(bitmap);
        row.addView(tray, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = dp(12);
        row.addView(info, infoParams);

        TextView name = text(pack.name, 16, TEXT, Typeface.BOLD);
        name.setMaxLines(2);
        info.addView(name, matchWrap());

        String type = pack.animated ? "Анимированный" : "Фото";
        TextView meta = text(type + " · " + pack.stickerCount + " стикеров", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = matchWrap();
        metaParams.topMargin = dp(4);
        info.addView(meta, metaParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        card.addView(actions, actionsParams);

        Button add = new Button(this);
        add.setText("Добавить в WhatsApp");
        styleButton(add, PRIMARY, Color.WHITE, 14);
        add.setOnClickListener(v -> addPackToWhatsApp(pack));
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button menu = new Button(this);
        menu.setText("⋯");
        menu.setContentDescription("Действия с набором " + pack.name);
        styleButton(menu, SOFT, PRIMARY, 20);
        menu.setOnClickListener(v -> showPackActions(pack));
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(56), dp(48));
        menuParams.leftMargin = dp(8);
        actions.addView(menu, menuParams);

        return card;
    }

    private void showPackActions(PackStore.Pack pack) {
        String[] actions = {"Переименовать", "Удалить"};
        new AlertDialog.Builder(this)
                .setTitle(pack.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showRenameDialog(pack);
                    else confirmDelete(pack);
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showRenameDialog(PackStore.Pack pack) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(pack.name);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable background = rounded(0xFFFAFCFB, 14);
        background.setStroke(dp(1), BORDER);
        input.setBackground(background);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        new AlertDialog.Builder(this)
                .setTitle("Переименовать набор")
                .setView(wrapper)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    PackStore.Pack renamed = PackStore.renamePack(this, pack.id, name);
                    if (renamed != null) {
                        setResult(RESULT_OK);
                        notifyMetadataChanged();
                        renderPacks();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmDelete(PackStore.Pack pack) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить набор?")
                .setMessage("«" + pack.name + "» будет удалён из приложения вместе с локальными файлами.")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (PackStore.deletePack(this, pack.id)) {
                        setResult(RESULT_OK);
                        notifyMetadataChanged();
                        renderPacks();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void addPackToWhatsApp(PackStore.Pack pack) {
        File firstSticker = PackStore.getStickerFile(this, pack.id, "1.webp");
        if (!firstSticker.isFile()) {
            Toast.makeText(this, "Файлы набора не найдены", Toast.LENGTH_LONG).show();
            return;
        }

        String authority = getPackageName() + ".stickercontentprovider";
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", pack.id);
        intent.putExtra("sticker_pack_authority", authority);
        intent.putExtra("sticker_pack_name", pack.name);
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "WhatsApp не найден на телефоне", Toast.LENGTH_LONG).show();
        }
    }

    private void notifyMetadataChanged() {
        String authority = getPackageName() + ".stickercontentprovider";
        getContentResolver().notifyChange(Uri.parse("content://" + authority + "/metadata"), null);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(CARD, 18));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private void styleButton(Button button, int fillColor, int textColor, int textSize) {
        button.setAllCaps(false);
        button.setTextSize(textSize);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(rounded(fillColor, 13));
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams withBottomMargin(int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = bottom;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
