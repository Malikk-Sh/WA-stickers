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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Sequential Library -> Unified Editor -> Build shell.
 *
 * MainActivity remains the owner of conversion/editor state. This class only re-parents the exact
 * live pack-name control and presents the existing MediaGridPanel in one editor screen. The old
 * bottom workflow navigation is no longer rendered.
 */
public class AppShellActivity extends LauncherActivity {
    private static final int MAX_PACK_NAME = 60;

    private enum WorkflowScreen {
        EDITOR,
        BUILD,
        LIBRARY
    }

    private PreviewLoader shellPreviewLoader;
    private FrameLayout screenHost;
    private View editorScreen;
    private View legacyMediaPlaceholder;
    private View buildScreen;
    private View packsScreen;
    private WorkflowScreen activeScreen = WorkflowScreen.EDITOR;

    private EditText packNameField;
    private TextView createNameCounter;
    private TextView createMediaCounter;
    private TextView editorTitle;
    private TextView editorSubtitle;
    private MediaGridPanel mediaPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        shellPreviewLoader = new PreviewLoader(this);
        try {
            installShell();
            refreshShellState();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install unified editor shell: " + error);
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
    public void onBackPressed() {
        if (activeScreen == WorkflowScreen.BUILD) {
            showEditorScreen();
            return;
        }
        if (activeScreen == WorkflowScreen.EDITOR) {
            showLibraryScreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (shellPreviewLoader != null) shellPreviewLoader.close();
        super.onDestroy();
    }

    private void installShell() {
        packNameField = this.runtimePackName();
        Button photoModeButton = this.runtimePhotoModeButton();
        Button animatedModeButton = this.runtimeAnimatedModeButton();
        if (packNameField == null || photoModeButton == null || animatedModeButton == null) {
            throw new IllegalStateException("Editor controls are missing");
        }

        // Keep the runtime-owned controls themselves. Mode buttons are intentionally not exposed in
        // the unified editor; project type is detected from source contents before Build.
        detach(packNameField);
        detach(photoModeButton);
        detach(animatedModeButton);

        editorScreen = buildEditorScreen();
        legacyMediaPlaceholder = new FrameLayout(this);
        legacyMediaPlaceholder.setVisibility(View.GONE);
        buildScreen = buildBuildScreen();
        packsScreen = buildPacksScreen();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.app_background));

        screenHost = new FrameLayout(this);
        screenHost.setId(R.id.app_screen_host);
        // Keep the historical child indexes temporarily for BuildShell/PacksShell while their
        // presentation classes are migrated in later slices. No user-facing tab depends on them.
        screenHost.addView(editorScreen, fillParent());
        screenHost.addView(legacyMediaPlaceholder, fillParent());
        screenHost.addView(buildScreen, fillParent());
        screenHost.addView(packsScreen, fillParent());
        shell.addView(screenHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        installRoutingCompatibilityBridge(shell);

        setContentView(shell);
        showEditorScreen();
    }

    private View buildEditorScreen() {
        LinearLayout body = newScreenBody();
        ScrollView root = wrap(body);
        root.setId(R.id.editor_screen);

        body.addView(buildEditorHeader(), matchWrap());

        LinearLayout nameCard = card();
        nameCard.addView(text("Название набора", 14,
                color(R.color.app_text_primary), Typeface.BOLD), matchWrap());

        packNameField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_PACK_NAME)});
        packNameField.setSingleLine(true);
        packNameField.setTextSize(15);
        packNameField.setTextColor(color(R.color.app_text_primary));
        packNameField.setHintTextColor(color(R.color.app_text_tertiary));
        packNameField.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable inputBackground = rounded(color(R.color.app_surface_variant), 15);
        inputBackground.setStroke(dp(1), color(R.color.app_border));
        packNameField.setBackground(inputBackground);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        fieldParams.topMargin = dp(9);
        nameCard.addView(packNameField, fieldParams);

        createNameCounter = text("", 12, color(R.color.app_text_secondary), Typeface.NORMAL);
        createNameCounter.setId(R.id.create_name_counter);
        createNameCounter.setGravity(Gravity.END);
        LinearLayout.LayoutParams counterParams = matchWrap();
        counterParams.topMargin = dp(4);
        nameCard.addView(createNameCounter, counterParams);
        packNameField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                updateNamePresentation();
            }
        });

        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(12);
        body.addView(nameCard, nameParams);

        LinearLayout mediaHeading = new LinearLayout(this);
        mediaHeading.setOrientation(LinearLayout.HORIZONTAL);
        mediaHeading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingParams = matchWrap();
        headingParams.topMargin = dp(16);
        body.addView(mediaHeading, headingParams);

        TextView mediaTitle = text("Стикеры", 20, color(R.color.app_text_primary), Typeface.BOLD);
        mediaHeading.addView(mediaTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        createMediaCounter = text("0 / 30", 12, color(R.color.app_primary), Typeface.BOLD);
        createMediaCounter.setId(R.id.create_media_counter);
        createMediaCounter.setGravity(Gravity.CENTER);
        createMediaCounter.setPadding(dp(10), 0, dp(10), 0);
        createMediaCounter.setBackground(rounded(color(R.color.app_primary_container), 999));
        mediaHeading.addView(createMediaCounter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        mediaPanel = new MediaGridPanel(this, shellPreviewLoader, new MediaGridPanel.Host() {
            @Override
            public void onModeChanged(boolean animated) {
                // The segmented mode selector is removed from the unified UX.
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
        LinearLayout.LayoutParams mediaParams = matchWrap();
        mediaParams.topMargin = dp(8);
        mediaParams.bottomMargin = dp(12);
        body.addView(mediaPanel, mediaParams);

        return root;
    }

    private View buildEditorHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));

        ImageButton back = new ImageButton(this);
        back.setId(R.id.editor_back);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("Назад к наборам");
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackground(UiComponents.rounded(
                this, R.color.app_surface_variant, R.dimen.radius_card));
        back.setOnClickListener(v -> showLibraryScreen());
        row.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(12);
        // Leave room for task-specific overflow/settings actions installed by SettingsShellActivity.
        labelParams.rightMargin = dp(104);
        row.addView(labels, labelParams);

        editorTitle = text("Мои стикеры", 22, color(R.color.app_text_primary), Typeface.BOLD);
        editorTitle.setId(R.id.editor_title);
        editorTitle.setMaxLines(1);
        labels.addView(editorTitle, matchWrap());

        editorSubtitle = text("0 элементов · сохранено", 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        editorSubtitle.setId(R.id.editor_subtitle);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(2);
        labels.addView(editorSubtitle, subtitleParams);
        return row;
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
        ScrollView root = wrap(body);
        root.setId(R.id.build_screen_host);
        return root;
    }

    private View buildPacksScreen() {
        LinearLayout body = newScreenBody();
        body.addView(buildTopBar("Мои наборы", "Сохранённые наборы"), matchWrap());
        ScrollView root = wrap(body);
        root.setId(R.id.packs_screen_host);
        return root;
    }

    private View buildTopBar(String titleValue, String subtitleValue) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));

        TextView title = text(titleValue, 24, color(R.color.app_text_primary), Typeface.BOLD);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(title, matchWrap());
        TextView subtitle = text(subtitleValue, 12,
                color(R.color.app_text_secondary), Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(1);
        labels.addView(subtitle, subtitleParams);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Sequential navigation entry used by Library/Create actions. */
    protected final void showEditorScreen() {
        showWorkflowScreen(WorkflowScreen.EDITOR);
    }

    /** Sequential navigation entry used by the unified editor CTA. */
    protected final void showBuildScreen() {
        if (!prepareProjectForExistingBuildPipeline()) return;
        showWorkflowScreen(WorkflowScreen.BUILD);
    }

    /** Sequential navigation entry used by back and finalized-pack actions. */
    protected final void showLibraryScreen() {
        showWorkflowScreen(WorkflowScreen.LIBRARY);
    }

    private void showWorkflowScreen(WorkflowScreen screen) {
        if (screenHost == null) return;
        activeScreen = screen;
        editorScreen.setVisibility(screen == WorkflowScreen.EDITOR ? View.VISIBLE : View.GONE);
        legacyMediaPlaceholder.setVisibility(View.GONE);
        buildScreen.setVisibility(screen == WorkflowScreen.BUILD ? View.VISIBLE : View.GONE);
        packsScreen.setVisibility(screen == WorkflowScreen.LIBRARY ? View.VISIBLE : View.GONE);
        refreshShellState();
    }

    private boolean prepareProjectForExistingBuildPipeline() {
        List<Uri> items = selectedUrisSnapshot();
        SourceAnimationDetector.ProjectKind kind = SourceAnimationDetector.classifyProject(this, items);
        if (kind == SourceAnimationDetector.ProjectKind.MIXED) {
            EditorInstanceStateBridge.savePersistent(this);
            TransientFeedback.show(
                    this,
                    "Фото и анимации сохранены в одном проекте. Совместимый mixed-export подключается следующим этапом."
            );
            return false;
        }
        if (kind == SourceAnimationDetector.ProjectKind.UNKNOWN) {
            TransientFeedback.show(this, "Не удалось определить тип одного из файлов");
            return false;
        }
        if (kind == SourceAnimationDetector.ProjectKind.STATIC_ONLY
                || kind == SourceAnimationDetector.ProjectKind.ANIMATED_ONLY) {
            boolean animated = kind == SourceAnimationDetector.ProjectKind.ANIMATED_ONLY;
            Uri cover = coverUriSnapshot();
            String name = runtimeEnteredPackName();
            if (!runtimeRestoreEditorState(animated, items, cover, name, null)) {
                TransientFeedback.show(this, "Не удалось подготовить проект к сборке");
                return false;
            }
            EditorInstanceStateBridge.savePersistent(this);
        }
        return true;
    }

    void refreshShellState() {
        if (createMediaCounter == null || mediaPanel == null) return;
        List<Uri> items = selectedUrisSnapshot();
        createMediaCounter.setText(items.size() + " / 30");
        mediaPanel.render(items, coverUriSnapshot(), runtimeIsAnimatedMode());
        TextView summary = mediaPanel.findViewById(R.id.media_summary);
        if (summary != null) summary.setText("Стикеры: " + items.size());
        updateNamePresentation();
    }

    private void updateNamePresentation() {
        if (packNameField == null) return;
        if (createNameCounter != null) {
            createNameCounter.setText(packNameField.length() + " / " + MAX_PACK_NAME);
        }
        String value = packNameField.getText().toString().trim();
        if (editorTitle != null) editorTitle.setText(value.isEmpty() ? "Мои стикеры" : value);
        if (editorSubtitle != null) {
            int count = runtimeSelectedUrisSnapshot().size();
            editorSubtitle.setText(count + itemLabel(count) + " · сохранено");
        }
    }

    private String itemLabel(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) return " элементов";
        if (mod10 == 1) return " элемент";
        if (mod10 >= 2 && mod10 <= 4) return " элемента";
        return " элементов";
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
        if (this.runtimeRemoveMediaAt(index)) persistAndRefreshShell();
    }

    private void clearMediaFromShell() {
        if (this.runtimeClearMedia()) persistAndRefreshShell();
    }

    private void persistAndRefreshShell() {
        try {
            EditorInstanceStateBridge.savePersistent(this);
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not persist unified editor change: " + error);
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

    /**
     * Temporary non-visual compatibility bridge for presentation subclasses/tests still being
     * migrated away from the old nav IDs. It occupies no layout space and is not a navigation UI.
     */
    private void installRoutingCompatibilityBridge(LinearLayout shell) {
        FrameLayout bridge = new FrameLayout(this);
        bridge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addRoutingView(bridge, R.id.nav_create, this::showEditorScreen);
        addRoutingView(bridge, R.id.nav_media, this::showEditorScreen);
        addRoutingView(bridge, R.id.nav_build, this::showBuildScreen);
        addRoutingView(bridge, R.id.nav_packs, this::showLibraryScreen);
        shell.addView(bridge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0));
    }

    private void addRoutingView(FrameLayout bridge, int id, Runnable action) {
        View view = new View(this);
        view.setId(id);
        view.setClickable(true);
        view.setOnClickListener(v -> action.run());
        bridge.addView(view, new FrameLayout.LayoutParams(0, 0));
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
