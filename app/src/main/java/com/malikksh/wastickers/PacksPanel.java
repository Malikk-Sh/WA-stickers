package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Task-focused local pack manager used by the Packs tab. */
final class PacksPanel extends LinearLayout {
    enum Filter {
        ALL,
        PHOTO,
        ANIMATED
    }

    interface Host {
        void onAddToWhatsApp(PackStore.Pack pack);
        void onShowPackActions(PackStore.Pack pack);
        void onCreateFirstPack();
        void onSearchRequested();
        void onOverflowRequested(View anchor);
    }

    private final Host host;
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    private final Button allFilter;
    private final Button photoFilter;
    private final Button animatedFilter;
    private final TextView packsStat;
    private final TextView stickersStat;
    private final LinearLayout list;

    private Filter filter = Filter.ALL;
    private List<PackStore.Pack> packs = new ArrayList<>();
    private String query = "";
    private long renderGeneration;

    PacksPanel(Context context, Host host) {
        super(context);
        this.host = host;
        setId(R.id.packs_panel);
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(18), dp(20), dp(22));
        setBackgroundColor(color(R.color.app_background));

        addView(buildHeader(), matchWrap());

        LinearLayout filters = new LinearLayout(context);
        filters.setOrientation(HORIZONTAL);
        filters.setPadding(dp(4), dp(4), dp(4), dp(4));
        filters.setBackground(rounded(color(R.color.app_disabled_surface), 18));
        LinearLayout.LayoutParams filtersParams = matchWrap();
        filtersParams.topMargin = dp(16);
        addView(filters, filtersParams);

        allFilter = filterButton("Все", R.id.packs_filter_all, Filter.ALL);
        filters.addView(allFilter, new LinearLayout.LayoutParams(0, dp(48), 1f));
        photoFilter = filterButton("Фото", R.id.packs_filter_photo, Filter.PHOTO);
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        photoParams.leftMargin = dp(6);
        filters.addView(photoFilter, photoParams);
        animatedFilter = filterButton("Анимация", R.id.packs_filter_animated, Filter.ANIMATED);
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(6);
        filters.addView(animatedFilter, animatedParams);

        LinearLayout stats = new LinearLayout(context);
        stats.setId(R.id.packs_stats);
        stats.setOrientation(HORIZONTAL);
        stats.setPadding(dp(16), dp(14), dp(16), dp(14));
        stats.setBackground(rounded(color(R.color.app_surface), 20));
        if (Build.VERSION.SDK_INT >= 21) stats.setElevation(dp(1));
        LinearLayout.LayoutParams statsParams = matchWrap();
        statsParams.topMargin = dp(14);
        addView(stats, statsParams);

        packsStat = statistic("0 наборов", "Сохранено");
        stats.addView((View) packsStat.getParent(), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        stickersStat = statistic("0 стикеров", "Всего");
        LinearLayout stickersBlock = (LinearLayout) stickersStat.getParent();
        stats.removeView(stickersBlock);
        stats.addView(stickersBlock, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        list = new LinearLayout(context);
        list.setId(R.id.packs_list);
        list.setOrientation(VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(14);
        addView(list, listParams);

        updateFilterStyles();
    }

    void render(List<PackStore.Pack> source) {
        packs = new ArrayList<>(source == null ? Collections.emptyList() : source);
        Collections.reverse(packs);
        renderGeneration++;
        renderStats();
        renderList();
    }

    Filter filter() {
        return filter;
    }

    void setFilter(Filter next) {
        filter = next == null ? Filter.ALL : next;
        updateFilterStyles();
        renderGeneration++;
        renderList();
    }

    String query() {
        return query;
    }

    void setQuery(String value) {
        query = value == null ? "" : value.trim();
        renderGeneration++;
        renderList();
    }

    void close() {
        renderGeneration++;
        previewExecutor.shutdownNow();
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(getContext());
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        logo.setBackground(rounded(color(R.color.app_primary), 18));
        row.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        TextView title = text("Мои наборы", 28, color(R.color.app_text_primary), Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView subtitle = text("Сохранённые наборы", 14,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);

        TextView search = headerAction("⌕", "Поиск наборов");
        search.setId(R.id.packs_search);
        search.setOnClickListener(v -> host.onSearchRequested());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        searchParams.rightMargin = dp(6);
        row.addView(search, searchParams);

        TextView overflow = headerAction("⋮", "Меню наборов");
        overflow.setId(R.id.packs_overflow);
        overflow.setOnClickListener(v -> host.onOverflowRequested(v));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private TextView headerAction(String glyph, String description) {
        TextView action = text(glyph, 24, color(R.color.app_primary), Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(description);
        action.setClickable(true);
        action.setFocusable(true);
        action.setBackground(rounded(color(R.color.app_primary_container), 16));
        return action;
    }

    private Button filterButton(String label, int id, Filter value) {
        Button button = new Button(getContext());
        button.setId(id);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> setFilter(value));
        return button;
    }

    private TextView statistic(String value, String label) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(VERTICAL);
        block.setGravity(Gravity.CENTER);
        TextView number = text(value, 17, color(R.color.app_text_primary), Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        block.addView(number, matchWrap());
        TextView caption = text(label, 12, color(R.color.app_text_secondary), Typeface.NORMAL);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = matchWrap();
        captionParams.topMargin = dp(2);
        block.addView(caption, captionParams);
        return number;
    }

    private void renderStats() {
        int stickers = 0;
        for (PackStore.Pack pack : packs) stickers += Math.max(0, pack.stickerCount);
        packsStat.setText(packCountLabel(packs.size()));
        stickersStat.setText(stickerCountLabel(stickers));
    }

    private void renderList() {
        list.removeAllViews();
        List<PackStore.Pack> filtered = filteredPacks();
        if (filtered.isEmpty()) {
            list.addView(buildEmptyState(), matchWrap());
            return;
        }
        for (int i = 0; i < filtered.size(); i++) {
            LinearLayout.LayoutParams params = matchWrap();
            if (i > 0) params.topMargin = dp(10);
            list.addView(buildPackCard(filtered.get(i)), params);
        }
    }

    private List<PackStore.Pack> filteredPacks() {
        List<PackStore.Pack> result = new ArrayList<>();
        String normalized = query.toLowerCase(Locale.ROOT);
        for (PackStore.Pack pack : packs) {
            if (filter == Filter.PHOTO && pack.animated) continue;
            if (filter == Filter.ANIMATED && !pack.animated) continue;
            if (!normalized.isEmpty()
                    && !pack.name.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            result.add(pack);
        }
        return result;
    }

    private View buildEmptyState() {
        LinearLayout card = card();
        card.setId(R.id.packs_empty);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean searching = !query.isEmpty();
        TextView title = text(
                searching
                        ? "Ничего не найдено"
                        : (filter == Filter.ALL ? "Пока нет наборов" : "Нет наборов этого типа"),
                18, color(R.color.app_text_primary), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView body = text(
                searching
                        ? "Измените запрос или сбросьте поиск"
                        : (filter == Filter.ALL
                                ? "Созданные наборы появятся здесь"
                                : "Выберите другой фильтр или создайте новый набор"),
                13, color(R.color.app_text_secondary), Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);

        if (!searching && filter == Filter.ALL) {
            Button create = new Button(getContext());
            create.setId(R.id.packs_create_first);
            create.setText("Создать первый набор");
            create.setAllCaps(false);
            stylePrimaryButton(create);
            create.setOnClickListener(v -> host.onCreateFirstPack());
            LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            createParams.topMargin = dp(14);
            card.addView(create, createParams);
        }
        return card;
    }

    private View buildPackCard(PackStore.Pack pack) {
        LinearLayout card = card();
        card.setContentDescription("Набор " + pack.name);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, matchWrap());

        ImageView tray = new ImageView(getContext());
        tray.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tray.setBackground(rounded(color(R.color.app_surface_variant), 16));
        tray.setContentDescription("Иконка набора " + pack.name);
        if (Build.VERSION.SDK_INT >= 21) tray.setClipToOutline(true);
        row.addView(tray, new LinearLayout.LayoutParams(dp(76), dp(76)));
        loadTray(pack, tray, renderGeneration);

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = dp(12);
        row.addView(info, infoParams);

        TextView name = text(pack.name, 17, color(R.color.app_text_primary), Typeface.BOLD);
        name.setMaxLines(2);
        info.addView(name, matchWrap());

        TextView count = text(stickerCountLabel(pack.stickerCount), 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(4);
        info.addView(count, countParams);

        TextView type = text(pack.animated ? "Анимация" : "Фото", 12,
                color(R.color.app_primary), Typeface.BOLD);
        type.setGravity(Gravity.CENTER);
        type.setPadding(dp(9), 0, dp(9), 0);
        type.setBackground(rounded(color(R.color.app_primary_container), 13));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
        typeParams.topMargin = dp(7);
        info.addView(type, typeParams);

        Button overflow = new Button(getContext());
        overflow.setText("⋯");
        overflow.setAllCaps(false);
        overflow.setTextSize(22);
        overflow.setContentDescription("Действия с набором " + pack.name);
        styleOverflowButton(overflow);
        overflow.setOnClickListener(v -> host.onShowPackActions(pack));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(50), dp(48)));

        Button whatsapp = new Button(getContext());
        whatsapp.setText("В WhatsApp");
        whatsapp.setAllCaps(false);
        stylePrimaryButton(whatsapp);
        whatsapp.setContentDescription("Добавить набор " + pack.name + " в WhatsApp");
        whatsapp.setOnClickListener(v -> host.onAddToWhatsApp(pack));
        LinearLayout.LayoutParams whatsappParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        whatsappParams.topMargin = dp(12);
        card.addView(whatsapp, whatsappParams);
        return card;
    }

    private void loadTray(PackStore.Pack pack, ImageView target, long generation) {
        target.setTag(pack.id + ":" + generation);
        String expected = pack.id + ":" + generation;
        previewExecutor.execute(() -> {
            File trayFile = PackStore.getStickerFile(getContext(), pack.id, "tray.png");
            Bitmap bitmap = trayFile.isFile() ? BitmapFactory.decodeFile(trayFile.getAbsolutePath()) : null;
            post(() -> {
                if (!expected.equals(target.getTag())) {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                    return;
                }
                if (bitmap != null) target.setImageBitmap(bitmap);
            });
        });
    }

    private void updateFilterStyles() {
        styleFilterButton(allFilter, filter == Filter.ALL);
        styleFilterButton(photoFilter, filter == Filter.PHOTO);
        styleFilterButton(animatedFilter, filter == Filter.ANIMATED);
    }

    private void styleFilterButton(Button button, boolean active) {
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(active
                ? color(R.color.app_on_primary)
                : color(R.color.app_primary));
        button.setBackground(rounded(
                active ? color(R.color.app_primary) : color(R.color.app_disabled_surface), 14));
    }

    private void stylePrimaryButton(Button button) {
        button.setTextSize(15);
        button.setTextColor(color(R.color.app_on_primary));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setBackground(rounded(color(R.color.app_primary), 16));
    }

    private void styleOverflowButton(Button button) {
        button.setTextColor(color(R.color.app_primary));
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setBackground(rounded(color(R.color.app_primary_container), 14));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(color(R.color.app_surface), 22));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private String packCountLabel(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return count + " набор";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return count + " набора";
        return count + " наборов";
    }

    private String stickerCountLabel(int count) {
        int mod10 = count % 10;
        int mod100 = count % 100;
        if (mod10 == 1 && mod100 != 11) return count + " стикер";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return count + " стикера";
        return count + " стикеров";
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @SuppressWarnings("deprecation")
    private int color(int resourceId) {
        return getResources().getColor(resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
