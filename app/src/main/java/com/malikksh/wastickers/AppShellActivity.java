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

import java.util.ArrayList;
import java.util.List;

/**
 * Four-tab application shell for the redesigned flow.
 *
 * Conversion state still lives in MainActivity for now, but the shell no longer discovers its UI
 * by walking the legacy long-scroll hierarchy. Only the live name/mode controls are re-parented;
 * Build and Packs own their redesigned presentation in the later shell layers.
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

    private void installShell() {
        EditText packName = MainActivityRuntimeAccess.packName(this);
        photoModeButton = MainActivityRuntimeAccess.photoModeButton(this);
        animatedModeButton = MainActivityRuntimeAccess.animatedModeButton(this);
        if (packName == null || photoModeButton == null || animatedModeButton == null) {
            throw new IllegalStateException("Editor controls are missing");
        }

        // MainActivity still owns these live controls because its build pipeline reads their state.
        // Re-parent them directly instead of locating cards by traversing the old view hierarchy.
        detach(packName);
        detach(photoModeButton);
        detach(animatedModeButton);
        installModeListeners();

        createScreen = buildCreateScreen(buildModeSelector(), buildNameCard(packName));
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

    private View buildModeSelector() {
        LinearLayout card = card();
        card.addView(text("Тип набора", 17,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());

        TextView hint = text(
                "Фото и анимированные стикеры сохраняются как отдельные наборы.",
                12, color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(4);
        card.addView(hint, hintParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = matchWrap();
        rowParams.topMargin = dp(12);
        card.addView(row, rowParams);

        row.addView(photoModeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(8);
        row.addView(animatedModeButton, animatedParams);
        return card;
    }

    private View buildNameCard(EditText packName) {
        LinearLayout card = card();
        card.addView(text("Название набора", 17,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        nameParams.topMargin = dp(12);
        card.addView(packName, nameParams);
        return card;
    }

    private View buildCreateScreen(View modeSelector, View nameCard) {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("WA Stickers", "Ваши идеи в стикерах"), matchWrap());

        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(16);
        body.addView(modeSelector, modeParams);

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

        LinearLayout placeholder = card();
        placeholder.addView(text("Подготовка сборки", 18,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());
        TextView hint = text(
                "Параметры и прогресс сборки появятся здесь.",
                13, color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(5);
        placeholder.addView(hint, hintParams);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(16);
        body.addView(placeholder, params);
        return wrap(body);
    }

    private View buildPacksScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Мои наборы", "Сохранённые наборы"), matchWrap());
        // PacksShellActivity replaces this body with the redesigned PacksPanel immediately after
        // the base shell is installed. Keeping the base screen empty avoids retaining any route
        // to the removed standalone saved-packs activity.
        return wrap(body);
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
        List<Uri> items = selectedUrisSnapshot();
        boolean animated = MainActivityRuntimeAccess.isAnimatedMode(this);
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
        openMediaPicker();
    }

    private void moveMediaFromShell(int fromIndex, int toIndex) {
        if (isProcessing()) return;
        MainActivityRuntimeAccess.moveSticker(this, fromIndex, toIndex);
        persistAndRefreshShell();
    }

    private void selectCoverFromShell(Uri uri) {
        if (MainActivityRuntimeAccess.selectCover(this, uri)) {
            persistAndRefreshShell();
        }
    }

    private void removeMediaFromShell(int index) {
        if (MainActivityRuntimeAccess.removeMediaAt(this, index)) {
            persistAndRefreshShell();
        }
    }

    private void clearMediaFromShell() {
        if (MainActivityRuntimeAccess.clearMedia(this)) {
            persistAndRefreshShell();
        }
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
        return MainActivityRuntimeAccess.isProcessing(this);
    }

    private Uri coverUriSnapshot() {
        return MainActivityRuntimeAccess.coverUri(this);
    }

    private void installModeListeners() {
        photoModeButton.setOnClickListener(v -> {
            invokeSetAnimatedMode(false);
            refreshShellState();
        });
        animatedModeButton.setOnClickListener(v -> {
            invokeSetAnimatedMode(true);
            refreshShellState();
        });
    }

    private void invokeSetAnimatedMode(boolean animated) {
        MainActivityRuntimeAccess.setAnimatedMode(this, animated);
    }

    private List<Uri> selectedUrisSnapshot() {
        return MainActivityRuntimeAccess.selectedUrisSnapshot(this);
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) view.getParent()).removeView(view);
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
