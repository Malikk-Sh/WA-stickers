package com.malikksh.wastickers;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Transitional application shell for the redesigned four-tab flow.
 *
 * Conversion and editor state still live in MainActivity/HomeActivity. The shell presents that
 * state as task-focused screens while the legacy UI is migrated incrementally.
 */
public class AppShellActivity extends LauncherActivity {
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;

    private enum AppTab {
        CREATE,
        MEDIA,
        BUILD,
        PACKS
    }

    private PreviewLoader shellPreviewLoader;
    private FrameLayout screenHost;
    private View createScreen;
    private View mediaScreen;
    private View buildScreen;
    private View packsScreen;
    private AppTab activeTab = AppTab.CREATE;

    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<AppTab> navTabs = new ArrayList<>();

    private TextView createMediaTitle;
    private TextView createMediaSubtitle;
    private TextView createMediaCounter;
    private LinearLayout createPreviewRow;
    private Button createPickButton;
    private Button createManageButton;
    private Button createContinueButton;

    private MediaGridPanel mediaPanel;
    private View legacyBuildCard;
    private View legacyPacksCard;
    private TextView legacyStatusText;
    private Button legacyGalleryButton;
    private Button photoModeButton;
    private Button animatedModeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shellPreviewLoader = new PreviewLoader(this);
        try {
            installShell();
            refreshShellState();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install redesigned app shell: " + error);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        postShellRefresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        postShellRefresh();
    }

    @Override
    protected void onDestroy() {
        if (shellPreviewLoader != null) shellPreviewLoader.close();
        super.onDestroy();
    }

    private void installShell() throws Exception {
        FrameLayout activityContent = findViewById(android.R.id.content);
        if (activityContent == null || activityContent.getChildCount() == 0) {
            throw new IllegalStateException("Legacy content is missing");
        }
        View legacyScreen = activityContent.getChildAt(0);
        if (!(legacyScreen instanceof ScrollView)) {
            throw new IllegalStateException("Unexpected legacy root: " + legacyScreen.getClass().getName());
        }
        ScrollView legacyScroll = (ScrollView) legacyScreen;
        if (legacyScroll.getChildCount() == 0 || !(legacyScroll.getChildAt(0) instanceof LinearLayout)) {
            throw new IllegalStateException("Legacy editor container is missing");
        }
        LinearLayout legacyRoot = (LinearLayout) legacyScroll.getChildAt(0);

        EditText packName = (EditText) readField(MainActivity.class, "packName");
        legacyGalleryButton = (Button) readField(MainActivity.class, "galleryButton");
        Button createButton = (Button) readField(MainActivity.class, "createButton");
        photoModeButton = (Button) readField(MainActivity.class, "photoModeButton");
        animatedModeButton = (Button) readField(MainActivity.class, "animatedModeButton");
        legacyStatusText = (TextView) readField(MainActivity.class, "statusText");
        TextView packsMeta = (TextView) readField(HomeActivity.class, "packsMeta");

        View nameCard = directChildContaining(legacyRoot, packName);
        View legacyMediaCard = directChildContaining(legacyRoot, legacyGalleryButton);
        legacyBuildCard = directChildContaining(legacyRoot, createButton);
        legacyPacksCard = directChildContaining(legacyRoot, packsMeta);
        View modeRow = parentView(photoModeButton);

        if (nameCard == null || legacyMediaCard == null || legacyBuildCard == null || modeRow == null) {
            throw new IllegalStateException("Could not locate legacy editor sections");
        }

        detach(modeRow);
        detach(nameCard);
        detach(legacyMediaCard);
        detach(legacyBuildCard);
        detach(legacyPacksCard);
        detach(legacyStatusText);

        normalizeNameCard(nameCard);
        normalizeBuildCard(legacyBuildCard);
        installModeListeners();

        createScreen = buildCreateScreen(modeRow, nameCard);
        mediaScreen = buildMediaScreen();
        buildScreen = buildBuildScreen();
        packsScreen = buildPacksScreen();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.app_background));

        screenHost = new FrameLayout(this);
        screenHost.setId(R.id.app_screen_host);
        screenHost.addView(createScreen, fillParent());
        screenHost.addView(mediaScreen, fillParent());
        screenHost.addView(buildScreen, fillParent());
        screenHost.addView(packsScreen, fillParent());
        shell.addView(screenHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88)));

        setContentView(shell);
        getWindow().setStatusBarColor(color(R.color.app_background));
        getWindow().setNavigationBarColor(color(R.color.app_surface));
        showTab(AppTab.CREATE);
    }

    private View buildCreateScreen(View modeRow, View nameCard) {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("WA Stickers", "Ваши идеи в стикерах"), matchWrap());

        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(16);
        body.addView(modeRow, modeParams);

        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(14);
        body.addView(nameCard, nameParams);

        View mediaCard = buildCreateMediaCard();
        LinearLayout.LayoutParams mediaParams = matchWrap();
        mediaParams.topMargin = dp(14);
        body.addView(mediaCard, mediaParams);

        createContinueButton = new Button(this);
        createContinueButton.setId(R.id.create_continue);
        createContinueButton.setText("Продолжить  →");
        stylePrimaryButton(createContinueButton, false);
        createContinueButton.setOnClickListener(v -> showTab(AppTab.MEDIA));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        continueParams.topMargin = dp(16);
        continueParams.bottomMargin = dp(12);
        body.addView(createContinueButton, continueParams);

        return wrap(body);
    }

    private View buildCreateMediaCard() {
        LinearLayout card = card();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        createMediaTitle = text("Добавьте фотографии", 18,
                color(R.color.app_text_primary), Typeface.BOLD);
        titleBlock.addView(createMediaTitle, matchWrap());
        createMediaSubtitle = text("От 3 до 30 фото", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(3);
        titleBlock.addView(createMediaSubtitle, subtitleParams);

        createMediaCounter = text("0 / 30", 13, color(R.color.app_primary), Typeface.BOLD);
        createMediaCounter.setId(R.id.create_media_counter);
        createMediaCounter.setGravity(Gravity.CENTER);
        createMediaCounter.setPadding(dp(12), 0, dp(12), 0);
        createMediaCounter.setBackground(rounded(color(R.color.app_primary_container), 16));
        header.addView(createMediaCounter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        LinearLayout dropZone = new LinearLayout(this);
        dropZone.setOrientation(LinearLayout.VERTICAL);
        dropZone.setGravity(Gravity.CENTER);
        dropZone.setPadding(dp(18), dp(20), dp(18), dp(18));
        GradientDrawable dropBackground = rounded(color(R.color.app_surface), 20);
        dropBackground.setStroke(dp(1), color(R.color.app_border), dp(7), dp(5));
        dropZone.setBackground(dropBackground);
        LinearLayout.LayoutParams dropParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(184));
        dropParams.topMargin = dp(16);
        card.addView(dropZone, dropParams);

        TextView imageGlyph = text("▧ +", 32, color(R.color.app_secondary), Typeface.BOLD);
        imageGlyph.setGravity(Gravity.CENTER);
        dropZone.addView(imageGlyph, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        createPickButton = new Button(this);
        createPickButton.setId(R.id.create_pick_media);
        createPickButton.setText("＋  Выбрать фото");
        stylePrimaryButton(createPickButton, true);
        createPickButton.setOnClickListener(v -> openPickerFromShell());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(250), dp(54));
        pickParams.topMargin = dp(14);
        dropZone.addView(createPickButton, pickParams);

        TextView helper = text("Файлы останутся на устройстве", 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        helper.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helperParams = matchWrap();
        helperParams.topMargin = dp(9);
        dropZone.addView(helper, helperParams);

        createPreviewRow = new LinearLayout(this);
        createPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
        createPreviewRow.setGravity(Gravity.CENTER_VERTICAL);
        createPreviewRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.topMargin = dp(14);
        card.addView(createPreviewRow, previewParams);

        createManageButton = new Button(this);
        createManageButton.setText("Управлять медиа");
        styleSecondaryButton(createManageButton);
        createManageButton.setVisibility(View.GONE);
        createManageButton.setOnClickListener(v -> showTab(AppTab.MEDIA));
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        manageParams.topMargin = dp(12);
        card.addView(createManageButton, manageParams);

        return card;
    }

    private View buildMediaScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Медиа", "Выбранные файлы"), matchWrap());

        mediaPanel = new MediaGridPanel(this, shellPreviewLoader, new MediaGridPanel.Host() {
            @Override
            public void onModeChanged(boolean animated) {
                invokeSetAnimatedMode(animated);
                refreshShellState();
            }

            @Override
            public void onAddMedia() {
                openPickerFromShell();
            }

            @Override
            public void onMoveMedia(int fromIndex, int toIndex) {
                moveMediaFromShell(fromIndex, toIndex);
            }

            @Override
            public void onSelectCover(Uri uri) {
                selectCoverFromShell(uri);
            }

            @Override
            public void onRemoveMedia(int index) {
                removeMediaFromShell(index);
            }

            @Override
            public void onClearMedia() {
                clearMediaFromShell();
            }

            @Override
            public void onContinue() {
                showTab(AppTab.BUILD);
            }
        });
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(16);
        body.addView(mediaPanel, panelParams);
        return wrap(body);
    }

    private View buildBuildScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Сборка", "Подготовка набора"), matchWrap());

        LinearLayout.LayoutParams buildParams = matchWrap();
        buildParams.topMargin = dp(16);
        body.addView(legacyBuildCard, buildParams);

        if (legacyStatusText != null) {
            LinearLayout.LayoutParams statusParams = matchWrap();
            statusParams.topMargin = dp(12);
            statusParams.bottomMargin = dp(12);
            body.addView(legacyStatusText, statusParams);
        }
        return wrap(body);
    }

    private View buildPacksScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Мои наборы", "Сохранённые наборы"), matchWrap());

        View content = legacyPacksCard != null ? legacyPacksCard : buildPacksFallback();
        LinearLayout.LayoutParams packsParams = matchWrap();
        packsParams.topMargin = dp(16);
        packsParams.bottomMargin = dp(12);
        body.addView(content, packsParams);
        return wrap(body);
    }

    private View buildPacksFallback() {
        LinearLayout card = card();
        card.addView(text("Пока нет наборов", 18,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());
        TextView hint = text("Созданные наборы появятся здесь", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        card.addView(hint, hintParams);

        Button open = new Button(this);
        open.setText("Открыть сохранённые наборы");
        stylePrimaryButton(open, true);
        open.setOnClickListener(v -> startActivity(new Intent(this, SavedPacksActivity.class)));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        openParams.topMargin = dp(14);
        card.addView(open, openParams);
        return card;
    }

    private View buildTopBar(String titleValue, String subtitleValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(7), dp(7), dp(7), dp(7));
        logo.setBackground(rounded(color(R.color.app_primary), 18));
        row.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(14);
        row.addView(labels, labelsParams);

        TextView title = text(titleValue, 28, color(R.color.app_text_primary), Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView subtitle = text(subtitleValue, 14,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(subtitle, subtitleParams);
        return row;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(12), dp(8), dp(12), dp(8));
        nav.setBackgroundColor(color(R.color.app_surface));
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(5));

        addNavItem(nav, AppTab.CREATE, "＋", "Создать", R.id.nav_create);
        addNavItem(nav, AppTab.MEDIA, "▧", "Медиа", R.id.nav_media);
        addNavItem(nav, AppTab.BUILD, "✦", "Сборка", R.id.nav_build);
        addNavItem(nav, AppTab.PACKS, "≡", "Наборы", R.id.nav_packs);
        return nav;
    }

    private void addNavItem(LinearLayout nav, AppTab tab, String glyph, String label, int id) {
        LinearLayout item = new LinearLayout(this);
        item.setId(id);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        item.setPadding(dp(4), dp(5), dp(4), dp(5));
        item.setOnClickListener(v -> showTab(tab));

        TextView icon = text(glyph, 26, color(R.color.app_text_secondary), Typeface.BOLD);
        icon.setGravity(Gravity.CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        TextView labelView = text(label, 12, color(R.color.app_text_secondary), Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        item.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        navItems.add(item);
        navTabs.add(tab);
        nav.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private void showTab(AppTab tab) {
        if (screenHost == null) return;
        activeTab = tab;
        createScreen.setVisibility(tab == AppTab.CREATE ? View.VISIBLE : View.GONE);
        mediaScreen.setVisibility(tab == AppTab.MEDIA ? View.VISIBLE : View.GONE);
        buildScreen.setVisibility(tab == AppTab.BUILD ? View.VISIBLE : View.GONE);
        packsScreen.setVisibility(tab == AppTab.PACKS ? View.VISIBLE : View.GONE);
        updateBottomNavigation();
        refreshShellState();
    }

    private void updateBottomNavigation() {
        for (int i = 0; i < navItems.size(); i++) {
            LinearLayout item = navItems.get(i);
            boolean selected = navTabs.get(i) == activeTab;
            item.setBackground(selected
                    ? rounded(color(R.color.app_primary_container), 22)
                    : rounded(Color.TRANSPARENT, 22));
            for (int child = 0; child < item.getChildCount(); child++) {
                View view = item.getChildAt(child);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(selected
                            ? color(R.color.app_primary)
                            : color(R.color.app_text_secondary));
                    text.setTypeface(Typeface.create("sans",
                            selected ? Typeface.BOLD : Typeface.NORMAL));
                }
            }
        }
    }

    private void refreshShellState() {
        if (createMediaCounter == null) return;
        normalizeLegacyLabels();
        List<Uri> items = selectedUrisSnapshot();
        boolean animated = EditorInstanceStateBridge.isAnimatedMode(this);
        refreshCreateMediaCard(items, animated);
        if (mediaPanel != null) mediaPanel.render(items, coverUriSnapshot(), animated);
    }

    private void refreshCreateMediaCard(List<Uri> items, boolean animated) {
        int count = items.size();
        createMediaTitle.setText(animated ? "Добавьте анимации" : "Добавьте фотографии");
        createMediaSubtitle.setText(animated
                ? "GIF, WebP и видео · до 10 секунд"
                : "От 3 до 30 фото");
        createMediaCounter.setText(count + " / " + MAX_STICKERS);
        createPickButton.setText(animated ? "＋  Выбрать файлы" : "＋  Выбрать фото");

        boolean ready = count >= MIN_STICKERS && count <= MAX_STICKERS;
        stylePrimaryButton(createContinueButton, ready);
        createManageButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        renderCreatePreviews(items);
    }

    private void renderCreatePreviews(List<Uri> items) {
        createPreviewRow.removeAllViews();
        if (items.isEmpty()) {
            createPreviewRow.setVisibility(View.GONE);
            return;
        }
        createPreviewRow.setVisibility(View.VISIBLE);

        int shown = Math.min(4, items.size());
        for (int i = 0; i < shown; i++) {
            Uri uri = items.get(i);
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(rounded(color(R.color.app_disabled_surface), 16));
            if (Build.VERSION.SDK_INT >= 21) preview.setClipToOutline(true);
            preview.setContentDescription("Превью файла " + (i + 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1f);
            if (i > 0) params.leftMargin = dp(8);
            createPreviewRow.addView(preview, params);
            loadPreview(uri, preview);
        }

        if (items.size() > shown) {
            TextView more = text("+" + (items.size() - shown), 16,
                    color(R.color.app_primary), Typeface.BOLD);
            more.setGravity(Gravity.CENTER);
            more.setBackground(rounded(color(R.color.app_primary_container), 16));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(76), dp(76));
            params.leftMargin = dp(8);
            createPreviewRow.addView(more, params);
        }
    }

    private void loadPreview(Uri uri, ImageView target) {
        if (shellPreviewLoader == null || uri == null) return;
        String expectedKey = shellPreviewLoader.requestKey(uri);
        target.setTag(expectedKey);
        shellPreviewLoader.load(uri, (requestKey, bitmap) -> runOnUiThread(() -> {
            if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            if (!requestKey.equals(target.getTag())) return;
            if (bitmap != null) target.setImageBitmap(bitmap);
        }));
    }

    private void openPickerFromShell() {
        if (legacyGalleryButton != null) legacyGalleryButton.performClick();
    }

    private void moveMediaFromShell(int fromIndex, int toIndex) {
        if (isProcessing()) return;
        invokeMainMethod("moveSticker", new Class<?>[]{int.class, int.class}, fromIndex, toIndex);
        persistAndRefreshShell();
    }

    private void selectCoverFromShell(Uri uri) {
        if (isProcessing() || uri == null || !selectedUrisSnapshot().contains(uri)) return;
        writeFieldQuietly(MainActivity.class, "coverUri", uri);
        syncLegacyEditorAfterMediaMutation();
    }

    @SuppressWarnings("unchecked")
    private void removeMediaFromShell(int index) {
        if (isProcessing()) return;
        Object value = readFieldQuietly(MainActivity.class, "selectedUris");
        if (!(value instanceof List<?>)) return;
        List<Uri> selected = (List<Uri>) value;
        if (index < 0 || index >= selected.size()) return;
        Uri removed = selected.remove(index);
        Uri cover = coverUriSnapshot();
        if (removed != null && removed.equals(cover)) {
            writeFieldQuietly(MainActivity.class, "coverUri",
                    selected.isEmpty() ? null : selected.get(0));
        }
        syncLegacyEditorAfterMediaMutation();
    }

    @SuppressWarnings("unchecked")
    private void clearMediaFromShell() {
        if (isProcessing()) return;
        Object value = readFieldQuietly(MainActivity.class, "selectedUris");
        if (!(value instanceof List<?>)) return;
        ((List<Uri>) value).clear();
        writeFieldQuietly(MainActivity.class, "coverUri", null);
        syncLegacyEditorAfterMediaMutation();
    }

    private void syncLegacyEditorAfterMediaMutation() {
        invokeMainMethod("invalidateCurrentPack", new Class<?>[0]);
        invokeMainMethod("renderPreviews", new Class<?>[0]);
        invokeMainMethod("updateUiState", new Class<?>[0]);
        persistAndRefreshShell();
    }

    private void persistAndRefreshShell() {
        try {
            EditorInstanceStateBridge.savePersistent(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not persist shell media edit: " + error);
        }
        refreshShellState();
    }

    private boolean isProcessing() {
        Object value = readFieldQuietly(MainActivity.class, "processing");
        return value instanceof Boolean && (Boolean) value;
    }

    private Uri coverUriSnapshot() {
        Object value = readFieldQuietly(MainActivity.class, "coverUri");
        return value instanceof Uri ? (Uri) value : null;
    }

    private void normalizeLegacyLabels() {
        boolean animated = EditorInstanceStateBridge.isAnimatedMode(this);
        TextView mediaTitle = (TextView) readFieldQuietly(MainActivity.class, "mediaTitle");
        if (mediaTitle != null) mediaTitle.setText(animated ? "Анимации и видео" : "Фотографии");

        TextView actionHint = (TextView) readFieldQuietly(MainActivity.class, "actionHint");
        if (actionHint != null) actionHint.setVisibility(View.GONE);
    }

    private void normalizeNameCard(View nameCard) {
        TextView label = findTextView(nameCard, "1  Название набора");
        if (label != null) label.setText("Название набора");
    }

    private void normalizeBuildCard(View buildCard) {
        TextView label = findTextView(buildCard, "3  Готовый набор");
        if (label != null) label.setText("Сборка набора");
    }

    private void installModeListeners() {
        if (photoModeButton != null) {
            photoModeButton.setOnClickListener(v -> {
                invokeSetAnimatedMode(false);
                refreshShellState();
            });
        }
        if (animatedModeButton != null) {
            animatedModeButton.setOnClickListener(v -> {
                invokeSetAnimatedMode(true);
                refreshShellState();
            });
        }
    }

    private void invokeSetAnimatedMode(boolean animated) {
        invokeMainMethod("setAnimatedMode", new Class<?>[]{boolean.class}, animated);
    }

    private Object invokeMainMethod(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(this, args);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not invoke " + name + " from app shell: " + error);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Uri> selectedUrisSnapshot() {
        Object value = readFieldQuietly(MainActivity.class, "selectedUris");
        if (!(value instanceof List<?>)) return new ArrayList<>();
        List<Uri> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Uri) result.add((Uri) item);
        }
        return result;
    }

    private Object readField(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(this);
    }

    private Object readFieldQuietly(Class<?> owner, String name) {
        try {
            return readField(owner, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void writeFieldQuietly(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not update " + name + " from app shell: " + error);
        }
    }

    private static View directChildContaining(ViewGroup root, View target) {
        if (root == null || target == null) return null;
        View current = target;
        while (current != null && current.getParent() instanceof View) {
            View parent = (View) current.getParent();
            if (parent == root) return current;
            current = parent;
        }
        return current != null && current.getParent() == root ? current : null;
    }

    private static View parentView(View view) {
        if (view == null || !(view.getParent() instanceof View)) return null;
        return (View) view.getParent();
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) view.getParent()).removeView(view);
    }

    private TextView findTextView(View root, String exactText) {
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && exactText.contentEquals(value)) return (TextView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextView(group.getChildAt(i), exactText);
                if (found != null) return found;
            }
        }
        return null;
    }

    private LinearLayout newScreenBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(18));
        body.setBackgroundColor(color(R.color.app_background));
        return body;
    }

    private ScrollView wrap(LinearLayout body) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(color(R.color.app_background));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(color(R.color.app_surface), 24));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private void stylePrimaryButton(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setTextColor(enabled ? Color.WHITE : color(R.color.app_disabled_text));
        button.setBackground(rounded(
                enabled ? color(R.color.app_primary) : color(R.color.app_disabled_surface),
                18));
    }

    private void styleSecondaryButton(Button button) {
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(color(R.color.app_primary));
        button.setBackground(rounded(color(R.color.app_primary_container), 18));
    }

    private TextView text(String value, int size, int textColor, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int color(int resource) {
        return getColor(resource);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams fillParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void postShellRefresh() {
        if (screenHost == null) return;
        screenHost.post(this::refreshShellState);
    }
}
