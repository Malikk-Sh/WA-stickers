package com.malikksh.wastickers;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sequential application shell for Library -> Editor -> Build.
 *
 * MainActivity remains the owner of conversion/editor state. The shell presents one editor and uses
 * PackCompatibilityPlanner only to choose the existing build type safely before entering Build.
 */
public class AppShellActivity extends LauncherActivity {
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final int MAX_PACK_NAME = 60;

    private enum Route {
        EDITOR,
        BUILD,
        PACKS
    }

    private final ExecutorService plannerExecutor = Executors.newSingleThreadExecutor();
    private SourceAnimationDetector sourceDetector;
    private PreviewLoader shellPreviewLoader;
    private FrameLayout screenHost;
    private View editorScreen;
    private View legacyMediaScreen;
    private View buildScreen;
    private View packsScreen;
    private Route activeRoute = Route.EDITOR;
    private String pausedTrimSignature;

    private EditText packNameField;
    private TextView createNameCounter;
    private TextView editorTitle;
    private TextView editorSubtitle;
    private TextView editorCount;
    private Button createPickCompat;
    private Button createContinueCompat;
    private MediaGridPanel mediaPanel;
    private Button photoModeButton;
    private Button animatedModeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sourceDetector = new SourceAnimationDetector(this);
        shellPreviewLoader = new PreviewLoader(this);
        try {
            installShell();
            if (savedInstanceState != null) {
                try { showRoute(Route.valueOf(savedInstanceState.getString("shell.route", "PACKS"))); }
                catch (IllegalArgumentException ignored) { showLibraryScreen(); }
            }
            refreshShellState();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install sequential app shell: " + error);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("shell.route", activeRoute.name());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        pausedTrimSignature = trimSignature();
        super.onPause();
    }

    private String trimSignature() {
        StringBuilder value = new StringBuilder(runtimeProjectId());
        for (Uri uri : runtimeSelectedUrisSnapshot()) value.append('|').append(uri).append(':').append(VideoTrimStore.getStartOffsetMs(uri)).append(':').append(VideoTrimStore.getEndOffsetMs(uri));
        return value.toString();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pausedTrimSignature != null && !pausedTrimSignature.equals(trimSignature())) {
            runtimeInvalidateCurrentPack();
            EditorInstanceStateBridge.savePersistent(this);
        }
        pausedTrimSignature = null;
        postShellRefresh();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        postShellRefresh();
    }

    @Override
    protected void onDestroy() {
        plannerExecutor.shutdownNow();
        if (sourceDetector != null) sourceDetector.clear();
        if (shellPreviewLoader != null) shellPreviewLoader.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (activeRoute == Route.BUILD) {
            if (runtimeIsProcessing()) {
                TransientFeedback.show(this, "Сначала остановите обработку");
                return;
            }
            showEditorScreen();
            return;
        }
        if (activeRoute == Route.EDITOR) {
            showLibraryScreen();
            return;
        }
        super.onBackPressed();
    }

    private void installShell() {
        packNameField = this.runtimePackName();
        photoModeButton = this.runtimePhotoModeButton();
        animatedModeButton = this.runtimeAnimatedModeButton();
        if (packNameField == null || photoModeButton == null || animatedModeButton == null) {
            throw new IllegalStateException("Editor runtime controls are missing");
        }

        // MainActivity still owns these exact controls because the real pipeline reads their state.
        // The mode controls are detached and intentionally not exposed by the unified editor.
        detach(packNameField);
        detach(photoModeButton);
        detach(animatedModeButton);

        editorScreen = buildEditorScreen(buildNameCard(packNameField));
        // Keep historical child indexes stable while Build/Packs presentation subclasses migrate.
        legacyMediaScreen = emptyLegacyScreen();
        buildScreen = buildBuildScreen();
        packsScreen = buildPacksScreen();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.app_background));

        screenHost = new FrameLayout(this);
        screenHost.setId(R.id.app_screen_host);
        screenHost.addView(editorScreen, fillParent());
        screenHost.addView(legacyMediaScreen, fillParent());
        screenHost.addView(buildScreen, fillParent());
        screenHost.addView(packsScreen, fillParent());
        shell.addView(screenHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Zero-size compatibility hooks keep older instrumentation helpers working. They are not UI.
        shell.addView(buildCompatibilityRoutes(), new LinearLayout.LayoutParams(0, 0));

        setContentView(shell);
        showEditorScreen();
    }

    private View buildEditorScreen(View nameCard) {
        LinearLayout body = newScreenBody();
        body.addView(buildEditorHeader(), matchWrap());

        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(12);
        nameCard.setVisibility(View.GONE);
        body.addView(nameCard, nameParams);

        mediaPanel = new MediaGridPanel(this, shellPreviewLoader, new MediaGridPanel.Host() {
            @Override
            public void onModeChanged(boolean animated) {
                // Legacy MediaGridPanel callback. Unified Editor has no user-selectable pack mode.
            }

            @Override
            public void onAddMedia() {
                showAddSourceSheet();
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
                showBuildScreen();
            }
        });
        configureUnifiedMediaPanel();
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(12);
        panelParams.bottomMargin = dp(12);
        body.addView(mediaPanel, panelParams);
        return wrap(body);
    }

    private void configureUnifiedMediaPanel() {
        View photo = mediaPanel.findViewById(R.id.media_mode_photo);
        if (photo != null && photo.getParent() instanceof View) {
            ((View) photo.getParent()).setVisibility(View.GONE);
        }
        Button add = mediaPanel.findViewById(R.id.media_add_more);
        if (add != null) add.setText("+ Добавить");
        Button next = mediaPanel.findViewById(R.id.media_continue);
        if (next != null) next.setText("Продолжить");
    }

    private View buildEditorHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(color(R.color.app_text_primary));
        back.setContentDescription("Назад к наборам");
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackground(UiComponents.rounded(
                this, R.color.app_surface_variant, R.dimen.radius_card));
        back.setOnClickListener(v -> showLibraryScreen());
        row.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.leftMargin = dp(12);
        labelsParams.rightMargin = dp(8);
        row.addView(labels, labelsParams);

        editorTitle = text("Мои стикеры", 22,
                color(R.color.app_text_primary), Typeface.BOLD);
        editorTitle.setSingleLine(true);
        editorTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        editorTitle.setContentDescription("Переименовать набор");
        editorTitle.setMinHeight(dp(48));
        editorTitle.setOnClickListener(v -> {
            if (runtimeIsProcessing()) return;
            PackNameDialog.show(this, "Переименовать набор",
                runtimeEnteredPackName(), "Сохранить", runtimeProjectId(), name -> {
                    packNameField.setText(name);
                    runtimeInvalidateCurrentPack();
                    EditorInstanceStateBridge.savePersistent(this);
                    refreshShellState();
                });
        });
        labels.addView(editorTitle, matchWrap());

        editorSubtitle = text("0 элементов", 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(1);
        labels.addView(editorSubtitle, subtitleParams);

        editorCount = text("0 / 30", 12, color(R.color.app_primary), Typeface.BOLD);
        editorCount.setId(R.id.create_media_counter);
        editorCount.setGravity(Gravity.CENTER);
        editorCount.setPadding(dp(10), 0, dp(10), 0);
        editorCount.setBackground(rounded(color(R.color.app_primary_container), 999));
        row.addView(editorCount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        return row;
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
        updateNamePresentation();

        packName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                updateNamePresentation();
            }
        });
        return card;
    }

    private View emptyLegacyScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVisibility(View.GONE);
        return scroll;
    }

    private View buildBuildScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Сборка", "Подготовка набора"), matchWrap());
        LinearLayout placeholder = card();
        placeholder.addView(text("Подготовка сборки", 18,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());
        TextView hint = text("Параметры и прогресс сборки появятся здесь.", 13,
                color(R.color.app_text_secondary), Typeface.NORMAL);
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
        labels.addView(text(titleValue, 24,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());
        TextView subtitle = text(subtitleValue, 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(1);
        labels.addView(subtitle, subtitleParams);
        return row;
    }

    private View buildCompatibilityRoutes() {
        FrameLayout hooks = new FrameLayout(this);
        hooks.setVisibility(View.GONE);
        addRouteHook(hooks, R.id.nav_create, this::showEditorScreen);
        addRouteHook(hooks, R.id.nav_media, this::showEditorScreen);
        addRouteHook(hooks, R.id.nav_build, this::showBuildScreenUnchecked);
        addRouteHook(hooks, R.id.nav_packs, this::showLibraryScreen);

        createPickCompat = new Button(this);
        createPickCompat.setId(R.id.create_pick_media);
        createPickCompat.setVisibility(View.GONE);
        createPickCompat.setOnClickListener(v -> showAddSourceSheet());
        hooks.addView(createPickCompat, new FrameLayout.LayoutParams(0, 0));

        createContinueCompat = new Button(this);
        createContinueCompat.setId(R.id.create_continue);
        createContinueCompat.setVisibility(View.GONE);
        createContinueCompat.setOnClickListener(v -> showBuildScreenUnchecked());
        hooks.addView(createContinueCompat, new FrameLayout.LayoutParams(0, 0));
        return hooks;
    }

    private void addRouteHook(FrameLayout hooks, int id, Runnable route) {
        View hook = new View(this);
        hook.setId(id);
        hook.setVisibility(View.GONE);
        hook.setClickable(true);
        hook.setOnClickListener(v -> route.run());
        hooks.addView(hook, new FrameLayout.LayoutParams(0, 0));
    }

    protected final void showEditorScreen() {
        showRoute(Route.EDITOR);
    }

    protected final void showBuildScreen() {
        prepareAndShowBuild();
    }

    private void showBuildScreenUnchecked() {
        showRoute(Route.BUILD);
    }

    protected final void showLibraryScreen() {
        EditorInstanceStateBridge.savePersistent(this);
        showRoute(Route.PACKS);
        if (this instanceof PacksShellActivity) ((PacksShellActivity) this).refreshPacksPanelForTest();
    }

    private void prepareAndShowBuild() {
        if (isProcessing() || sourceDetector == null) return;
        List<Uri> snapshot = selectedUrisSnapshot();
        Uri coverSnapshot = coverUriSnapshot();
        String nameSnapshot = runtimeEnteredPackName();
        plannerExecutor.execute(() -> {
            List<PackCompatibilityPlanner.Item<Uri>> sources = new ArrayList<>();
            for (Uri uri : snapshot) {
                sources.add(new PackCompatibilityPlanner.Item<>(uri, sourceDetector.classify(uri)));
            }
            PackCompatibilityPlanner<Uri> planner = new PackCompatibilityPlanner<>();
            PackCompatibilityPlanner.ExportPlan<Uri> plan = planner.plan(sources, coverSnapshot);
            runOnUiThread(() -> applyExportPlan(snapshot, coverSnapshot, nameSnapshot, plan));
        });
    }

    private void applyExportPlan(
            List<Uri> plannedItems,
            Uri plannedCover,
            String plannedName,
            PackCompatibilityPlanner.ExportPlan<Uri> plan
    ) {
        if (isFinishing() || !plannedItems.equals(selectedUrisSnapshot())) return;
        if (!plan.isValid()) {
            String message = plan.validationWarnings.isEmpty()
                    ? "Проект пока нельзя собрать"
                    : plan.validationWarnings.get(0);
            TransientFeedback.show(this, message);
            return;
        }

        boolean animated = plan.targetPackType == PackCompatibilityPlanner.TargetPackType.ANIMATED_PACK;
        Uri cover = plannedCover != null && plannedItems.contains(plannedCover)
                ? plannedCover
                : (plannedItems.isEmpty() ? null : plannedItems.get(0));
        if (!runtimeRestoreEditorState(animated, plannedItems, cover, plannedName, runtimeCurrentPack())) {
            TransientFeedback.show(this, "Не удалось подготовить проект к сборке");
            return;
        }
        EditorInstanceStateBridge.savePersistent(this);
        showBuildScreenUnchecked();
    }

    private void showRoute(Route route) {
        if (screenHost == null) return;
        boolean changed = activeRoute != route;
        activeRoute = route;
        editorScreen.setVisibility(route == Route.EDITOR ? View.VISIBLE : View.GONE);
        legacyMediaScreen.setVisibility(View.GONE);
        buildScreen.setVisibility(route == Route.BUILD ? View.VISIBLE : View.GONE);
        packsScreen.setVisibility(route == Route.PACKS ? View.VISIBLE : View.GONE);
        if (changed) Motion.enter(route == Route.PACKS ? packsScreen : route == Route.BUILD ? buildScreen : editorScreen);
        refreshShellState();
    }

    void refreshShellState() {
        if (editorCount == null) return;
        List<Uri> items = selectedUrisSnapshot();
        boolean animated = this.runtimeIsAnimatedMode();
        int count = items.size();
        editorCount.setText(count + " / " + MAX_STICKERS);
        editorSubtitle.setText(count == 1 ? "1 элемент" : count + " элементов");
        if (createPickCompat != null) createPickCompat.setText("Выбрать файлы");
        if (createContinueCompat != null) {
            boolean ready = count >= MIN_STICKERS && count <= MAX_STICKERS;
            createContinueCompat.setEnabled(ready);
        }
        updateNamePresentation();
        if (mediaPanel != null) {
            mediaPanel.render(items, coverUriSnapshot(), animated);
            TextView summary = mediaPanel.findViewById(R.id.media_summary);
            if (summary != null) summary.setText("Стикеры: " + count);
        }
    }

    private void updateNamePresentation() {
        if (packNameField == null) return;
        if (createNameCounter != null) {
            createNameCounter.setText(packNameField.length() + " / " + MAX_PACK_NAME);
        }
        if (editorTitle != null) {
            String name = packNameField.getText().toString().trim();
            editorTitle.setText(name.isEmpty() ? "Мои стикеры" : name);
        }
    }

    private void showAddSourceSheet() {
        if (isProcessing()) return;
        AddSourceSheet.show(this, new AddSourceSheet.Host() {
            @Override
            public void onGallery() {
                openGallerySourcePicker();
            }

            @Override
            public void onFile() {
                openFileSourcePicker();
            }
        });
    }

    private void moveMediaFromShell(int fromIndex, int toIndex) {
        if (isProcessing()) return;
        this.runtimeMoveSticker(fromIndex, toIndex);
        persistAndRefreshShell();
    }

    private void selectCoverFromShell(Uri uri) {
        if (this.runtimeSelectCover(uri)) persistAndRefreshShell();
    }

    private void removeMediaFromShell(int index) {
        Uri removed = uriAt(index);
        if (this.runtimeRemoveMediaAt(index)) {
            if (sourceDetector != null) sourceDetector.invalidate(removed);
            persistAndRefreshShell();
        }
    }

    private Uri uriAt(int index) {
        List<Uri> current = selectedUrisSnapshot();
        return index >= 0 && index < current.size() ? current.get(index) : null;
    }

    private void clearMediaFromShell() {
        if (this.runtimeClearMedia()) {
            if (sourceDetector != null) sourceDetector.clear();
            persistAndRefreshShell();
        }
    }

    private void persistAndRefreshShell() {
        try {
            EditorInstanceStateBridge.savePersistent(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not persist editor media edit: " + error);
        }
        refreshShellState();
    }

    private boolean isProcessing() {
        return this.runtimeIsProcessing();
    }

    private Uri coverUriSnapshot() {
        return this.runtimeCoverUri();
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
