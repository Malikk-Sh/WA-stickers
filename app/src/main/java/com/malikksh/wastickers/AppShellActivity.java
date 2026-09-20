package com.malikksh.wastickers;

import android.content.Intent;
import android.graphics.Typeface;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Sequential application shell for Library -> Editor -> Build.
 *
 * MainActivity remains the only owner of conversion/editor state. This class only re-parents the
 * existing live name field and delegates media mutations to the existing runtime methods.
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

    private PreviewLoader shellPreviewLoader;
    private FrameLayout screenHost;
    private View editorScreen;
    private View legacyMediaScreen;
    private View buildScreen;
    private View packsScreen;
    private Route activeRoute = Route.EDITOR;

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
        shellPreviewLoader = new PreviewLoader(this);
        try {
            installShell();
            refreshShellState();
        } catch (Throwable error) {
            BugLogStore.appendApp("Could not install sequential app shell: " + error);
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

    @Override
    public void onBackPressed() {
        if (activeRoute == Route.BUILD) {
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
        detach(packNameField);
        detach(photoModeButton);
        detach(animatedModeButton);

        editorScreen = buildEditorScreen(buildNameCard(packNameField));
        // Keep the historical child indexes stable while subclasses migrate away from tab indexes.
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

        // Invisible compatibility hooks keep older instrumentation helpers working while production
        // navigation uses showEditorScreen/showBuildScreen/showLibraryScreen directly.
        shell.addView(buildCompatibilityRoutes(), new LinearLayout.LayoutParams(0, 0));

        setContentView(shell);
        showEditorScreen();
    }

    private View buildEditorScreen(View nameCard) {
        LinearLayout body = newScreenBody();
        body.addView(buildEditorHeader(), matchWrap());

        LinearLayout.LayoutParams nameParams = matchWrap();
        nameParams.topMargin = dp(12);
        body.addView(nameCard, nameParams);

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
                showBuildScreen();
            }
        });
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(12);
        panelParams.bottomMargin = dp(12);
        body.addView(mediaPanel, panelParams);
        return wrap(body);
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
        addRouteHook(hooks, R.id.nav_build, this::showBuildScreen);
        addRouteHook(hooks, R.id.nav_packs, this::showLibraryScreen);

        createPickCompat = new Button(this);
        createPickCompat.setId(R.id.create_pick_media);
        createPickCompat.setVisibility(View.GONE);
        createPickCompat.setOnClickListener(v -> openPickerFromShell());
        hooks.addView(createPickCompat, new FrameLayout.LayoutParams(0, 0));

        createContinueCompat = new Button(this);
        createContinueCompat.setId(R.id.create_continue);
        createContinueCompat.setVisibility(View.GONE);
        createContinueCompat.setOnClickListener(v -> showBuildScreen());
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
        showRoute(Route.BUILD);
    }

    protected final void showLibraryScreen() {
        showRoute(Route.PACKS);
    }

    private void showRoute(Route route) {
        if (screenHost == null) return;
        activeRoute = route;
        editorScreen.setVisibility(route == Route.EDITOR ? View.VISIBLE : View.GONE);
        legacyMediaScreen.setVisibility(View.GONE);
        buildScreen.setVisibility(route == Route.BUILD ? View.VISIBLE : View.GONE);
        packsScreen.setVisibility(route == Route.PACKS ? View.VISIBLE : View.GONE);
        refreshShellState();
    }

    void refreshShellState() {
        if (editorCount == null) return;
        List<Uri> items = selectedUrisSnapshot();
        boolean animated = this.runtimeIsAnimatedMode();
        int count = items.size();
        editorCount.setText(count + " / " + MAX_STICKERS);
        editorSubtitle.setText(count == 1 ? "1 элемент" : count + " элементов");
        if (createPickCompat != null) {
            createPickCompat.setText(animated ? "＋  Выбрать файлы" : "＋  Выбрать фото");
        }
        if (createContinueCompat != null) {
            boolean ready = count >= MIN_STICKERS && count <= MAX_STICKERS;
            createContinueCompat.setEnabled(ready);
        }
        updateNamePresentation();
        if (mediaPanel != null) mediaPanel.render(items, coverUriSnapshot(), animated);
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

    private void openPickerFromShell() {
        openMediaPicker();
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
