package com.malikksh.wastickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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

/** Compact Library/Home presentation backed only by the existing PackStore. */
final class PacksPanel extends LinearLayout {
    enum Filter {
        ALL,
        PHOTO,
        ANIMATED
    }

    interface Host {
        void onAddToWhatsApp(PackStore.Pack pack);
        void onOpenPack(PackStore.Pack pack);
        void onShowPackActions(PackStore.Pack pack);
        void onCreatePack();
        default void onOpenDraft(String id) {}
        void onSettingsRequested();
        void onOverflowRequested(View anchor);
    }

    private final Host host;
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor();

    private final LinearLayout normalHeader;
    private final LinearLayout searchHeader;
    private final EditText searchInput;
    private final LinearLayout filters;
    private final Button allFilter;
    private final Button photoFilter;
    private final Button animatedFilter;
    private final TextView statsText;
    private final LinearLayout list;
    private final LinearLayout drafts;

    private Filter filter = Filter.ALL;
    private List<PackStore.Pack> packs = new ArrayList<>();
    private String query = "";
    private long renderGeneration;
    private final java.util.Map<String, View> cards = new java.util.HashMap<>();
    private final java.util.Map<String, String> cardKeys = new java.util.HashMap<>();

    PacksPanel(Context context, Host host) {
        super(context);
        this.host = host;
        setId(R.id.packs_panel);
        setOrientation(VERTICAL);
        setPadding(dp(20), dp(18), dp(20), dp(24));
        setBackgroundColor(color(R.color.app_background));

        normalHeader = buildHeader();
        addView(normalHeader, matchWrap());

        searchInput = new EditText(context);
        configureSearchInput();
        searchHeader = buildSearchHeader();
        searchHeader.setVisibility(GONE);
        addView(searchHeader, matchWrap());

        filters = new LinearLayout(context);
        filters.setOrientation(HORIZONTAL);
        filters.setPadding(dp(4), dp(4), dp(4), dp(4));
        filters.setBackground(UiComponents.rounded(
                context, R.color.app_surface_variant, R.dimen.radius_control));
        LinearLayout.LayoutParams filtersParams = matchWrap();
        filtersParams.topMargin = dp(14);
        addView(filters, filtersParams);

        allFilter = filterButton("Все", R.id.packs_filter_all, Filter.ALL);
        filters.addView(allFilter, new LinearLayout.LayoutParams(0, dp(48), 1f));
        photoFilter = filterButton("Статичные", R.id.packs_filter_photo, Filter.PHOTO);
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        photoParams.leftMargin = dp(4);
        filters.addView(photoFilter, photoParams);
        animatedFilter = filterButton("С анимацией", R.id.packs_filter_animated, Filter.ANIMATED);
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(4);
        filters.addView(animatedFilter, animatedParams);

        statsText = UiComponents.metadata(context, "");
        statsText.setId(R.id.packs_stats);
        LinearLayout.LayoutParams statsParams = matchWrap();
        statsParams.topMargin = dp(12);
        addView(statsText, statsParams);

        Button create = new Button(context);
        create.setId(R.id.packs_create_first);
        create.setText("+ Новый набор");
        create.setContentDescription("Создать новый набор");
        UiComponents.stylePrimaryButton(create, true);
        create.setOnClickListener(v -> host.onCreatePack());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        createParams.topMargin = dp(14);
        addView(create, createParams);

        drafts = new LinearLayout(context);
        drafts.setOrientation(VERTICAL);
        addView(drafts, matchWrap());
        list = new LinearLayout(context);
        list.setId(R.id.packs_list);
        list.setOrientation(VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(16);
        addView(list, listParams);

        updateFilterStyles();
    }

    void render(List<PackStore.Pack> source) {
        packs = new ArrayList<>(source == null ? Collections.emptyList() : source);
        Collections.reverse(packs);
        renderGeneration++;
        renderStats();
        drafts.removeAllViews();
        for (java.util.Map.Entry<String, String> draft : EditorInstanceStateBridge.draftProjects(getContext()).entrySet()) {
            if (PackStore.getPack(getContext(), draft.getKey()) != null || !PackNames.valid(draft.getValue())) continue;
            Button resume = new Button(getContext());
            resume.setText("Продолжить: " + draft.getValue());
            UiComponents.styleOutlineButton(resume, true);
            resume.setOnClickListener(v -> host.onOpenDraft(draft.getKey()));
            drafts.addView(resume, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }
        renderList();
    }

    void showLoadError() {
        list.removeAllViews();
        cards.clear();
        cardKeys.clear();
        TextView error = UiComponents.metadata(getContext(), "Не удалось загрузить наборы. Попробуйте открыть приложение снова.");
        error.setTextColor(color(R.color.app_error));
        list.addView(error, matchWrap());
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

    void openSearch() {
        if (searchHeader.getVisibility() == VISIBLE) return;
        normalHeader.setVisibility(GONE);
        searchHeader.setVisibility(VISIBLE);
        findViewById(R.id.packs_create_first).setVisibility(GONE);
        Motion.enter(searchHeader);
        searchInput.setText(query);
        searchInput.setSelection(searchInput.length());
        searchInput.post(() -> {
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    boolean closeSearchIfActive() {
        if (searchHeader.getVisibility() != VISIBLE) return false;
        closeSearch();
        return true;
    }

    void close() {
        renderGeneration++;
        previewExecutor.shutdownNow();
    }

    private LinearLayout buildHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(getContext());
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        logo.setBackground(UiComponents.rounded(
                getContext(), R.color.app_primary, R.dimen.radius_card));
        row.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);

        labels.addView(UiComponents.screenTitle(getContext(), "Мои наборы"), matchWrap());
        TextView subtitle = UiComponents.metadata(getContext(), "Сохранённые наборы");
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(1);
        labels.addView(subtitle, subtitleParams);

        ImageButton search = headerAction(R.drawable.ic_search, "Поиск наборов");
        search.setId(R.id.packs_search);
        search.setOnClickListener(v -> openSearch());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        searchParams.rightMargin = dp(4);
        row.addView(search, searchParams);

        ImageButton settings = headerAction(R.drawable.ic_settings, "Открыть настройки");
        settings.setId(R.id.packs_settings);
        settings.setOnClickListener(v -> host.onSettingsRequested());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        settingsParams.rightMargin = dp(4);
        row.addView(settings, settingsParams);

        ImageButton overflow = headerAction(R.drawable.ic_more_vertical, "Меню наборов");
        overflow.setId(R.id.packs_overflow);
        overflow.setOnClickListener(v -> host.onOverflowRequested(v));
        row.addView(overflow, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return row;
    }

    private void configureSearchInput() {
        searchInput.setId(R.id.packs_search_field);
        searchInput.setSingleLine(true);
        searchInput.setHint("Поиск наборов…");
        searchInput.setTextSize(16);
        searchInput.setTextColor(color(R.color.app_text_primary));
        searchInput.setHintTextColor(color(R.color.app_text_tertiary));
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        android.graphics.drawable.GradientDrawable background = UiComponents.rounded(
                getContext(), R.color.app_surface_variant, R.dimen.radius_control);
        background.setStroke(dp(1), color(R.color.app_border));
        searchInput.setBackground(background);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                setQuery(s == null ? "" : s.toString());
            }
        });
    }

    private LinearLayout buildSearchHeader() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        ImageButton back = headerAction(R.drawable.ic_back, "Закрыть поиск");
        back.setId(R.id.packs_search_back);
        back.setOnClickListener(v -> closeSearch());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        backParams.rightMargin = dp(8);
        row.addView(back, backParams);

        row.addView(searchInput, new LinearLayout.LayoutParams(
                0, dp(48), 1f));

        ImageButton close = headerAction(R.drawable.ic_close, "Очистить и закрыть поиск");
        close.setId(R.id.packs_search_close);
        close.setOnClickListener(v -> {
            if (searchInput.length() == 0) closeSearch();
            else searchInput.setText("");
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        closeParams.leftMargin = dp(8);
        row.addView(close, closeParams);
        return row;
    }

    private void closeSearch() {
        findViewById(R.id.packs_create_first).setVisibility(VISIBLE);
        setQuery("");
        searchInput.setText("");
        searchInput.clearFocus();
        searchHeader.setVisibility(GONE);
        normalHeader.setVisibility(VISIBLE);
        InputMethodManager imm = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
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

    private void renderStats() {
        int stickers = 0;
        for (PackStore.Pack pack : packs) stickers += Math.max(0, pack.stickerCount);
        boolean useful = packs.size() > 1;
        filters.setVisibility(useful ? VISIBLE : GONE);
        statsText.setVisibility(useful ? VISIBLE : GONE);
        if (useful) {
            statsText.setText(packCountLabel(packs.size()) + "  ·  " + stickerCountLabel(stickers));
        }
    }

    private void renderList() {
        List<PackStore.Pack> filtered = filteredPacks();
        java.util.Set<String> visible = new java.util.HashSet<>();
        for (PackStore.Pack pack : filtered) visible.add(pack.id);
        if (Motion.enabled(getContext()) && list.isLaidOut()) {
            android.transition.TransitionManager.beginDelayedTransition(list,
                    new android.transition.AutoTransition().setDuration(200));
        }
        for (int i = list.getChildCount() - 1; i >= 0; i--) {
            View child = list.getChildAt(i);
            if (!(child.getTag() instanceof String) || !visible.contains((String) child.getTag())) list.removeViewAt(i);
        }
        if (filtered.isEmpty()) {
            list.addView(buildEmptyState(), matchWrap());
            return;
        }
        for (int i = 0; i < filtered.size(); i++) {
            PackStore.Pack pack = filtered.get(i);
            String key = pack.name + ":" + pack.imageDataVersion + ":" + isAvailable(pack)
                    + ":" + WhatsAppSync.state(getContext(), pack);
            View card = cards.get(pack.id);
            if (card == null || !key.equals(cardKeys.get(pack.id))) {
                if (card != null) list.removeView(card);
                card = buildPackCard(pack);
                card.setTag(pack.id);
                cards.put(pack.id, card);
                cardKeys.put(pack.id, key);
            }
            if (list.indexOfChild(card) != i) {
                list.removeView(card);
                LinearLayout.LayoutParams params = matchWrap();
                if (i > 0) params.topMargin = dp(10);
                list.addView(card, Math.min(i, list.getChildCount()), params);
            }
        }
        cards.keySet().retainAll(visible);
        cardKeys.keySet().retainAll(visible);
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
        LinearLayout card = tonalCard();
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
                        ? "Попробуйте другой запрос"
                        : (filter == Filter.ALL
                                ? "Создайте первый набор и добавьте в него фото, GIF или видео."
                                : "Выберите другой фильтр")
        );
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);
        return card;
    }

    private View buildPackCard(PackStore.Pack pack) {
        boolean available = isAvailable(pack);
        LinearLayout card = tonalCard();
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription("Открыть набор " + pack.name);
        card.setOnClickListener(v -> host.onOpenPack(pack));

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
        row.addView(tray, new LinearLayout.LayoutParams(dp(72), dp(72)));
        loadTray(pack, tray, renderGeneration);

        LinearLayout info = new LinearLayout(getContext());
        info.setOrientation(VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = dp(12);
        row.addView(info, infoParams);

        TextView name = UiComponents.cardTitle(getContext(), pack.name);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name, matchWrap());

        String summary = pack.photoCount + " фото · " + (pack.stickerCount - pack.photoCount) + " с анимацией";
        TextView count = UiComponents.metadata(getContext(), summary);
        count.setMaxLines(1);
        count.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams countParams = matchWrap();
        countParams.topMargin = dp(4);
        info.addView(count, countParams);
        if (pack.updatedAt > 0) {
            String modified = android.text.format.DateUtils.isToday(pack.updatedAt) ? "Изменён сегодня"
                    : "Изменён " + android.text.format.DateFormat.getDateFormat(getContext()).format(new java.util.Date(pack.updatedAt));
            info.addView(UiComponents.metadata(getContext(), modified), matchWrap());
        }

        if (!available) {
            TextView unavailable = chip("Недоступен", false);
            LinearLayout.LayoutParams unavailableParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(28));
            unavailableParams.topMargin = dp(7);
            info.addView(unavailable, unavailableParams);
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
        whatsapp.setText(WhatsAppSync.label(getContext(), pack));
        whatsapp.setAllCaps(false);
        UiComponents.stylePrimaryButton(whatsapp, available && WhatsAppSync.state(getContext(), pack) != WhatsAppSync.State.SYNCING);
        whatsapp.setContentDescription("Добавить набор " + pack.name + " в WhatsApp");
        if (available) whatsapp.setOnClickListener(v -> host.onAddToWhatsApp(pack));
        LinearLayout.LayoutParams whatsappParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
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

    private LinearLayout tonalCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        int padding = dp(16);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(UiComponents.rounded(
                getContext(), R.color.app_surface, R.dimen.radius_card));
        return card;
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
