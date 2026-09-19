package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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
        filters.setBackground(UiComponents.rounded(
                context, R.color.app_surface_variant, R.dimen.radius_pill));
        LinearLayout.LayoutParams filtersParams = matchWrap();
        filtersParams.topMargin = dp(16);
        addView(filters, filtersParams);

        allFilter = filterButton("Все", R.id.packs_filter_all, Filter.ALL);
        filters.addView(allFilter, new LinearLayout.LayoutParams(0, dp(48), 1f));
        photoFilter = filterButton("Фото", R.id.packs_filter_photo, Filter.PHOTO);
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        photoParams.leftMargin = dp(4);
        filters.addView(photoFilter, photoParams);
        animatedFilter = filterButton("Анимация", R.id.packs_filter_animated, Filter.ANIMATED);
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(4);
        filters.addView(animatedFilter, animatedParams);

        LinearLayout stats = UiComponents.card(context);
        stats.setId(R.id.packs_stats);
        stats.setOrientation(HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statsParams = matchWrap();
        statsParams.topMargin = dp(14);
        addView(stats, statsParams);

        LinearLayout packsBlock = statBlock("Сохранено");
        packsStat = (TextView) packsBlock.getChildAt(0);
        stats.addView(packsBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout stickersBlock = statBlock("Всего");
        stickersStat = (TextView) stickersBlock.getChildAt(0);
        stats.addView(stickersBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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
        logo.setBackground(UiComponents.rounded(
                getContext(), R.color.app_primary, R.dimen.radius_card));
        row.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        labels.addView(UiComponents.screenTitle(getContext(), "Мои наборы"), matchWrap());
        TextView subtitle = UiComponents.metadata(getContext(), "Сохранённые наборы");
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);

        ImageButton search = headerAction(R.drawable.ic_search, "Поиск наборов");
        search.setId(R.id.packs_search);
        search.setOnClickListener(v -> host.onSearchRequested());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        searchParams.rightMargin = dp(6);
        row.addView(search, searchParams);

        ImageButton overflow = headerAction(R.drawable.ic_more_vertical, "Меню наборов");
        overflow.setId(R.id.packs_overflow);
        overflow.setOnClickListener(v -> host.onOverflowRequested(v));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private ImageButton headerAction(int drawable, String description) {
        ImageButton action = new ImageButton(getContext());
        action.setImageResource(drawable);
        action.setContentDescription(description);
        action.setPadding(dp(12), dp(12), dp(12), dp(12));
        action.setBackground(UiComponents.rounded(
                getContext(), R.color.app_primary_container, R.dimen.radius_card));
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

    private LinearLayout statBlock(String label) {
        LinearLayout block = new LinearLayout(getContext());
        block.setOrientation(VERTICAL);
        block.setGravity(Gravity.CENTER);
        TextView number = text("0", 17, color(R.color.app_text_primary), Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        block.addView(number, matchWrap());
        TextView caption = UiComponents.metadata(getContext(), label);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = matchWrap();
        captionParams.topMargin = dp(2);
        block.addView(caption, captionParams);
        return block;
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
        LinearLayout card = UiComponents.card(getContext());
        card.setId(R.id.packs_empty);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean searching = !query.isEmpty();
        TextView title = UiComponents.cardTitle(
                getContext(),
                searching
                        ? "Ничего не найдено"
                        : (filter == Filter.ALL ? "Пока нет наборов" : "Нет наборов этого типа")
        );
        title.setGravity(Gravity.CENTER);
        card.addView(title, matchWrap());

        TextView body = UiComponents.metadata(
                getContext(),
                searching
                        ? "Измените запрос или сбросьте поиск"
                        : (filter == Filter.ALL
                                ? "Созданные наборы появятся здесь"
                                : "Выберите другой фильтр или создайте новый набор")
        );
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);

        if (!searching && filter == Filter.ALL) {
            Button create = new Button(getContext());
            create.setId(R.id.packs_create_first);
            create.setText("Создать первый набор");
            create.setAllCaps(false);
            UiComponents.stylePrimaryButton(create, true);
            create.setOnClickListener(v -> host.onCreateFirstPack());
            LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            createParams.topMargin = dp(14);
            card.addView(create, createParams);
        }
        return card;
    }

    private View buildPackCard(PackStore.Pack pack) {
        boolean available = isAvailable(pack);
        LinearLayout card = UiComponents.card(getContext());
        card.setContentDescription("Набор " + pack.name);

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, matchWrap());

        ImageView tray = new ImageView(getContext());
        tray.setScaleType(ImageView.ScaleType.CENTER_CROP);
        tray.setBackground(UiComponents.rounded(
                getContext(), R.color.app_surface_variant, R.dimen.radius_card));
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

        TextView name = UiComponents.cardTitle(getContext(), pack.name);
        name.setMaxLines(2);
        info.addView(name, matchWrap());

        TextView count = UiComponents.metadata(getContext(), stickerCountLabel(pack.stickerCount));
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(4);
        info.addView(count, countParams);

        LinearLayout chips = new LinearLayout(getContext());
        chips.setOrientation(HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipsParams = matchWrap();
        chipsParams.topMargin = dp(7);
        info.addView(chips, chipsParams);

        TextView type = chip(pack.animated ? "Анимация" : "Фото", true);
        chips.addView(type, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)));

        if (!available) {
            TextView unavailable = chip("Недоступен", false);
            LinearLayout.LayoutParams unavailableParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            unavailableParams.leftMargin = dp(6);
            chips.addView(unavailable, unavailableParams);
        }

        ImageButton overflow = headerAction(R.drawable.ic_more_vertical,
                "Действия с набором " + pack.name);
        overflow.setOnClickListener(v -> host.onShowPackActions(pack));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(48), dp(48)));

        if (!available) {
            TextView unavailableHint = UiComponents.metadata(
                    getContext(), "Файлы набора не найдены на устройстве");
            unavailableHint.setTextColor(color(R.color.app_error));
            LinearLayout.LayoutParams hintParams = matchWrap();
            hintParams.topMargin = dp(10);
            card.addView(unavailableHint, hintParams);
        }

        Button whatsapp = new Button(getContext());
        whatsapp.setText("В WhatsApp");
        whatsapp.setAllCaps(false);
        UiComponents.stylePrimaryButton(whatsapp, available);
        whatsapp.setContentDescription("Добавить набор " + pack.name + " в WhatsApp");
        if (available) whatsapp.setOnClickListener(v -> host.onAddToWhatsApp(pack));
        LinearLayout.LayoutParams whatsappParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        whatsappParams.topMargin = dp(12);
        card.addView(whatsapp, whatsappParams);
        return card;
    }

    private TextView chip(String value, boolean normal) {
        TextView chip = text(
                value,
                12,
                color(normal ? R.color.app_primary : R.color.app_error),
                Typeface.BOLD
        );
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(9), 0, dp(9), 0);
        chip.setBackground(UiComponents.rounded(
                getContext(),
                normal ? R.color.app_primary_container : R.color.app_error_container,
                R.dimen.radius_pill));
        return chip;
    }

    private boolean isAvailable(PackStore.Pack pack) {
        if (pack == null || pack.stickerCount <= 0) return false;
        File tray = PackStore.getStickerFile(getContext(), pack.id, "tray.png");
        if (!tray.isFile()) return false;
        for (int index = 1; index <= pack.stickerCount; index++) {
            if (!PackStore.getStickerFile(getContext(), pack.id, index + ".webp").isFile()) {
                return false;
            }
        }
        return true;
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
        UiComponents.styleSegment(allFilter, filter == Filter.ALL);
        UiComponents.styleSegment(photoFilter, filter == Filter.PHOTO);
        UiComponents.styleSegment(animatedFilter, filter == Filter.ANIMATED);
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

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int color(int resource) {
        return getResources().getColor(resource, getContext().getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
