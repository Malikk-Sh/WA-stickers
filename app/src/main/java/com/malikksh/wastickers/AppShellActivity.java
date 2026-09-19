package com.malikksh.wastickers;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
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
 * Conversion state still lives in MainActivity. The shell only presents and re-parents the existing
 * live editor controls so Create/Media can change visually without duplicating any editor, build,
 * storage or WhatsApp logic.
 */
public class AppShellActivity extends LauncherActivity {
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final int MAX_PACK_NAME = 60;

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
    private final List<FrameLayout> navIndicators = new ArrayList<>();
    private final List<ImageView> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private final List<AppTab> navTabs = new ArrayList<>();

    private EditText packNameField;
    private TextView createNameCounter;
    private TextView createMediaTitle;
    private TextView createMediaSubtitle;
    private TextView createMediaCounter;
    private TextView createDropHint;
    private ImageView createDropIcon;
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
        packNameField = this.runtimePackName();
        photoModeButton = this.runtimePhotoModeButton();
        animatedModeButton = this.runtimeAnimatedModeButton();
        if (packNameField == null || photoModeButton == null || animatedModeButton == null) {
            throw new IllegalStateException("Editor controls are missing");
        }

        // MainActivity owns these exact controls because the real pipeline reads their state.
        detach(packNameField);
        detach(photoModeButton);
        detach(animatedModeButton);
        installModeListeners();

        createScreen = buildCreateScreen(buildModeSelector(), buildNameCard(packNameField));
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                dimen(R.dimen.bottom_nav_content_height)));

        setContentView(shell);
        showTab(AppTab.CREATE);
    }

    private View buildModeSelector() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(dp(4), dp(4), dp(4), dp(4));
        container.setBackground(rounded(color(R.color.app_surface_variant), 18));

        photoModeButton.setCompoundDrawablePadding(dp(8));
        photoModeButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                getDrawable(R.drawable.ic_mode_photo), null, null, null);
        animatedModeButton.setCompoundDrawablePadding(dp(8));
        animatedModeButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                getDrawable(R.drawable.ic_mode_animation), null, null, null);

        container.addView(photoModeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams animatedParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedParams.leftMargin = dp(4);
        container.addView(animatedModeButton, animatedParams);
        return container;
    }

    private View buildNameCard(EditText packName) {
        LinearLayout card = card();
        card.addView(text("Название набора", 14,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());

        packName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_PACK_NAME)});
        packName.setSingleLine(true);
        packName.setTextSize(15);
        packName.setTextColor(color(R.color.app_text_primary));
        packName.setHintTextColor(color(R.color.app_text_tertiary));
        packName.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable inputBackground = rounded(color(R.color.app_surface_variant), 15);
        inputBackground.setStroke(dp(1), color(R.color.app_border));
        packName.setBackground(inputBackground);

        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        nameParams.topMargin = dp(10);
        card.addView(packName, nameParams);

        createNameCounter = text("", 12, color(R.color.app_text_secondary), Typeface.NORMAL);
        createNameCounter.setId(R.id.create_name_counter);
        createNameCounter.setGravity(Gravity.END);
        LinearLayout.LayoutParams counterParams = matchWrap();
        counterParams.topMargin = dp(5);
        card.addView(createNameCounter, counterParams);
        updateNameCounter();

        packName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updateNameCounter();
            }
        });
        return card;
    }

    private View buildCreateScreen(View modeSelector, View nameCard) {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("WA Stickers", "Ваши идеи в стикерах"), matchWrap());

        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(14);
        body.addView(modeSelector, modeParams);

        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(10);
        body.addView(nameCard, nameParams);

        View mediaCard = buildCreateMediaCard();
        LinearLayout.LayoutParams mediaParams = matchWrap();
        mediaParams.topMargin = dp(10);
        body.addView(mediaCard, mediaParams);

        createContinueButton = new Button(this);
        createContinueButton.setId(R.id.create_continue);
        createContinueButton.setText("Продолжить  →");
        stylePrimaryButton(createContinueButton, false);
        createContinueButton.setOnClickListener(v -> showTab(AppTab.MEDIA));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dimen(R.dimen.button_height));
        continueParams.topMargin = dp(14);
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
        subtitleParams.topMargin = dp(2);
        titleBlock.addView(createMediaSubtitle, subtitleParams);

        createMediaCounter = text("0 / 30", 12, color(R.color.app_primary), Typeface.BOLD);
        createMediaCounter.setId(R.id.create_media_counter);
        createMediaCounter.setGravity(Gravity.CENTER);
        createMediaCounter.setPadding(dp(10), 0, dp(10), 0);
        createMediaCounter.setBackground(rounded(color(R.color.app_primary_container), 999));
        header.addView(createMediaCounter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        LinearLayout dropZone = new LinearLayout(this);
        dropZone.setOrientation(LinearLayout.VERTICAL);
        dropZone.setGravity(Gravity.CENTER);
        dropZone.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable dropBackground = rounded(color(R.color.app_surface_variant), 18);
        dropBackground.setStroke(dp(1), color(R.color.app_border), dp(6), dp(4));
        dropZone.setBackground(dropBackground);
        LinearLayout.LayoutParams dropParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(158));
        dropParams.topMargin = dp(12);
        card.addView(dropZone, dropParams);

        createDropIcon = new ImageView(this);
        createDropIcon.setId(R.id.create_drop_icon);
        createDropIcon.setImageResource(R.drawable.ic_add_media);
        createDropIcon.setColorFilter(color(R.color.app_primary));
        createDropIcon.setContentDescription("Добавить медиа");
        dropZone.addView(createDropIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));

        createDropHint = text("Выберите фотографии\nдля вашего набора", 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        createDropHint.setId(R.id.create_drop_hint);
        createDropHint.setGravity(Gravity.CENTER);
        createDropHint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dropHintParams = matchWrap();
        dropHintParams.topMargin = dp(5);
        dropZone.addView(createDropHint, dropHintParams);

        createPickButton = new Button(this);
        createPickButton.setId(R.id.create_pick_media);
        createPickButton.setText("＋  Выбрать фото");
        stylePrimaryButton(createPickButton, true);
        createPickButton.setOnClickListener(v -> openPickerFromShell());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(220), dp(48));
        pickParams.topMargin = dp(10);
        dropZone.addView(createPickButton, pickParams);

        createPreviewRow = new LinearLayout(this);
        createPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
        createPreviewRow.setGravity(Gravity.CENTER_VERTICAL);
        createPreviewRow.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.topMargin = dp(10);
        card.addView(createPreviewRow, previewParams);

        createManageButton = new Button(this);
        createManageButton.setText("▧  Управлять медиа     ›");
        styleSecondaryButton(createManageButton);
        createManageButton.setVisibility(View.GONE);
        createManageButton.setOnClickListener(v -> showTab(AppTab.MEDIA));
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        manageParams.topMargin = dp(10);
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
        // PacksShellActivity replaces this body with PacksPanel after the base shell is installed.
        return wrap(body);
    }

    private View buildTopBar(String titleValue, String subtitleValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(6), dp(6), dp(6), dp(6));
        logo.setBackground(rounded(color(R.color.app_primary), 14));
        row.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(12);
        row.addView(labels, labelsParams);

        TextView title = text(titleValue, 24, color(R.color.app_text_primary), Typeface.BOLD);
        labels.addView(title, matchWrap());
        TextView subtitle = text(subtitleValue, 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(1);
        labels.addView(subtitle, subtitleParams);
        return row;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackgroundColor(color(R.color.app_surface));
        if (Build.VERSION.SDK_INT >= 21) nav.setElevation(dp(3));

        addNavItem(nav, AppTab.CREATE, R.drawable.ic_nav_create, "Создать", R.id.nav_create);
        addNavItem(nav, AppTab.MEDIA, R.drawable.ic_nav_media, "Медиа", R.id.nav_media);
        addNavItem(nav, AppTab.BUILD, R.drawable.ic_nav_build, "Сборка", R.id.nav_build);
        addNavItem(nav, AppTab.PACKS, R.drawable.ic_nav_packs, "Наборы", R.id.nav_packs);
        return nav;
    }

    private void addNavItem(LinearLayout nav, AppTab tab, int iconRes, String label, int id) {
        LinearLayout item = new LinearLayout(this);
        item.setId(id);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);
        item.setPadding(dp(2), dp(2), dp(2), dp(2));
        item.setOnClickListener(v -> showTab(tab));

        FrameLayout indicator = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color(R.color.app_text_secondary));
        icon.setContentDescription(null);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        indicator.addView(icon, iconParams);
        item.addView(indicator, new LinearLayout.LayoutParams(dp(44), dp(32)));

        TextView labelView = text(label, 11, color(R.color.app_text_secondary), Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        labelParams.topMargin = dp(1);
        item.addView(labelView, labelParams);

        navItems.add(item);
        navIndicators.add(indicator);
        navIcons.add(icon);
        navLabels.add(labelView);
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
            boolean selected = navTabs.get(i) == activeTab;
            navIndicators.get(i).setBackground(selected
                    ? rounded(color(R.color.app_primary_container), 999)
                    : rounded(color(R.color.app_transparent), 999));
            navIcons.get(i).setColorFilter(color(selected
                    ? R.color.app_primary
                    : R.color.app_text_secondary));
            TextView label = navLabels.get(i);
            label.setTextColor(color(selected
                    ? R.color.app_primary
                    : R.color.app_text_secondary));
            label.setTypeface(Typeface.create("sans",
                    selected ? Typeface.BOLD : Typeface.NORMAL));
        }
    }

    void refreshShellState() {
        if (createMediaCounter == null) return;
        List<Uri> items = selectedUrisSnapshot();
        boolean animated = this.runtimeIsAnimatedMode();
        refreshCreateMediaCard(items, animated);
        if (mediaPanel != null) mediaPanel.render(items, coverUriSnapshot(), animated);
    }

    private void refreshCreateMediaCard(List<Uri> items, boolean animated) {
        int count = items.size();
        styleModeButton(photoModeButton, !animated);
        styleModeButton(animatedModeButton, animated);
        createMediaTitle.setText(animated ? "Добавьте анимации" : "Добавьте фотографии");
        createMediaSubtitle.setText(animated
                ? "GIF, WebP и видео · до 10 секунд"
                : "От 3 до 30 фото");
        createMediaCounter.setText(count + " / " + MAX_STICKERS);
        createPickButton.setText(animated ? "＋  Выбрать файлы" : "＋  Выбрать фото");
        createDropHint.setText(animated
                ? "Выберите GIF, WebP или видео\nдля вашего набора"
                : "Выберите фотографии\nдля вашего набора");
        createDropIcon.setContentDescription(animated ? "Добавить анимации" : "Добавить фотографии");

        boolean ready = count >= MIN_STICKERS && count <= MAX_STICKERS;
        stylePrimaryButton(createContinueButton, ready);
        createManageButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        renderCreatePreviews(items);
        updateNameCounter();
    }

    private void styleModeButton(Button button, boolean selected) {
        UiComponents.styleSegment(button, selected);
        Drawable[] drawables = button.getCompoundDrawablesRelative();
        Drawable leading = drawables.length > 0 ? drawables[0] : null;
        if (leading != null) {
            leading.mutate().setTint(color(selected
                    ? R.color.app_on_primary
                    : R.color.app_text_secondary));
        }
    }

    private void updateNameCounter() {
        if (createNameCounter == null || packNameField == null) return;
        createNameCounter.setText(packNameField.length() + " / " + MAX_PACK_NAME);
    }

    private void renderCreatePreviews(List<Uri> items) {
        createPreviewRow.removeAllViews();
        if (items.isEmpty()) {
            createPreviewRow.setVisibility(View.GONE);
            return;
        }
        createPreviewRow.setVisibility(View.VISIBLE);

        int shown = Math.min(5, items.size());
        for (int i = 0; i < shown; i++) {
            Uri uri = items.get(i);
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setBackground(rounded(color(R.color.app_disabled_surface), 12));
            if (Build.VERSION.SDK_INT >= 21) preview.setClipToOutline(true);
            preview.setContentDescription("Превью файла " + (i + 1));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(62), 1f);
            if (i > 0) params.leftMargin = dp(6);
            createPreviewRow.addView(preview, params);
            loadPreview(uri, preview);
        }

        if (items.size() > shown) {
            TextView more = text("+" + (items.size() - shown), 14,
                    color(R.color.app_primary), Typeface.BOLD);
            more.setGravity(Gravity.CENTER);
            more.setBackground(rounded(color(R.color.app_primary_container), 12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(62), dp(62));
            params.leftMargin = dp(6);
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
        this.runtimeMoveSticker(fromIndex, toIndex);
        persistAndRefreshShell();
    }

    private void selectCoverFromShell(Uri uri) {
        if (this.runtimeSelectCover(uri)) {
            persistAndRefreshShell();
        }
    }

    private void removeMediaFromShell(int index) {
        if (this.runtimeRemoveMediaAt(index)) {
            persistAndRefreshShell();
        }
    }

    private void clearMediaFromShell() {
        if (this.runtimeClearMedia()) {
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
        return this.runtimeIsProcessing();
    }

    private Uri coverUriSnapshot() {
        return this.runtimeCoverUri();
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
        this.runtimeSetAnimatedMode(animated);
    }

    private List<Uri> selectedUrisSnapshot() {
        return this.runtimeSelectedUrisSnapshot();
    }

    private static void detach(View view) {
        if (view == null || !(view.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) view.getParent()).removeView(view);
    }

    private LinearLayout newScreenBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(
                dimen(R.dimen.screen_gutter_phone),
                dp(12),
                dimen(R.dimen.screen_gutter_phone),
                dp(16));
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
        return UiComponents.card(this);
    }

    private void stylePrimaryButton(Button button, boolean enabled) {
        UiComponents.stylePrimaryButton(button, enabled);
    }

    private void styleSecondaryButton(Button button) {
        UiComponents.styleOutlineButton(button, true);
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

    private int dimen(int resource) {
        return Math.round(getResources().getDimension(resource));
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
