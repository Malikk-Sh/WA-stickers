package com.malikksh.wastickers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.arthenica.ffmpegkit.FFmpegKit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_PHOTOS = 1001;
    private static final int REQUEST_PICK_ANIMATED = 1002;
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;

    private static final int BG = 0xFFF5F8F6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int TEAL = 0xFF128C7E;
    private static final int GREEN = 0xFF25D366;
    private static final int SOFT = 0xFFEAF5EF;
    private static final int BORDER = 0xFFDCE7E1;
    private static final int ERROR = 0xFFB3261E;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final List<TextView> fileProgressLabels = new ArrayList<>();
    private final List<ProgressBar> fileProgressBars = new ArrayList<>();
    private final List<String> fileProgressNames = new ArrayList<>();
    private final EditorStateController<Uri> editorStateController = new EditorStateController<>();
    private final PackBuildSession<Uri> buildSession = new PackBuildSession<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText packName;
    private TextView countText;
    private TextView statusText;
    private TextView mediaTitle;
    private TextView mediaHint;
    private TextView actionHint;
    private TextView progressText;
    private ProgressBar progressBar;
    private LinearLayout previewContainer;
    private LinearLayout fileProgressContainer;
    private Button photoModeButton;
    private Button animatedModeButton;
    private Button galleryButton;
    private Button createButton;
    private Button addButton;
    private Button bugLogButton;
    private PreviewLoader previewLoader;

    private PackStore.Pack currentPack;
    private boolean processing;
    private boolean animatedMode;
    private boolean lastOperationFailed;
    private String lastBugLog = "";
    private int diagnosticItemIndex = -1;
    private Uri diagnosticItemUri;
    private Uri coverUri;
    private volatile boolean activityDestroyed;

    /**
     * Typed migration surface for the redesigned shell. It keeps MainActivity state private while
     * allowing the production shell to reuse the existing editor/build pipeline without reflection.
     */
    static final class RuntimeControls {
        final EditText packName;
        final TextView countText;
        final TextView statusText;
        final TextView mediaTitle;
        final TextView mediaHint;
        final TextView actionHint;
        final TextView progressText;
        final ProgressBar progressBar;
        final LinearLayout fileProgressContainer;
        final Button photoModeButton;
        final Button animatedModeButton;
        final Button galleryButton;
        final Button createButton;
        final Button addButton;
        final Button bugLogButton;

        RuntimeControls(
                EditText packName,
                TextView countText,
                TextView statusText,
                TextView mediaTitle,
                TextView mediaHint,
                TextView actionHint,
                TextView progressText,
                ProgressBar progressBar,
                LinearLayout fileProgressContainer,
                Button photoModeButton,
                Button animatedModeButton,
                Button galleryButton,
                Button createButton,
                Button addButton,
                Button bugLogButton
        ) {
            this.packName = packName;
            this.countText = countText;
            this.statusText = statusText;
            this.mediaTitle = mediaTitle;
            this.mediaHint = mediaHint;
            this.actionHint = actionHint;
            this.progressText = progressText;
            this.progressBar = progressBar;
            this.fileProgressContainer = fileProgressContainer;
            this.photoModeButton = photoModeButton;
            this.animatedModeButton = animatedModeButton;
            this.galleryButton = galleryButton;
            this.createButton = createButton;
            this.addButton = addButton;
            this.bugLogButton = bugLogButton;
        }
    }

    final void installRuntimeControls(RuntimeControls controls) {
        if (controls == null) throw new IllegalArgumentException("controls == null");
        packName = controls.packName;
        countText = controls.countText;
        statusText = controls.statusText;
        mediaTitle = controls.mediaTitle;
        mediaHint = controls.mediaHint;
        actionHint = controls.actionHint;
        progressText = controls.progressText;
        progressBar = controls.progressBar;
        previewContainer = null;
        fileProgressContainer = controls.fileProgressContainer;
        photoModeButton = controls.photoModeButton;
        animatedModeButton = controls.animatedModeButton;
        galleryButton = controls.galleryButton;
        createButton = controls.createButton;
        addButton = controls.addButton;
        bugLogButton = controls.bugLogButton;
    }

    final EditText runtimePackName() {
        return packName;
    }

    final Button runtimePhotoModeButton() {
        return photoModeButton;
    }

    final Button runtimeAnimatedModeButton() {
        return animatedModeButton;
    }

    final List<Uri> runtimeSelectedUrisSnapshot() {
        return new ArrayList<>(selectedUris);
    }

    final Uri runtimeCoverUri() {
        return coverUri;
    }

    final boolean runtimeIsProcessing() {
        return processing;
    }

    final boolean runtimeIsAnimatedMode() {
        return animatedMode;
    }

    final void runtimeSetAnimatedMode(boolean animated) {
        setAnimatedMode(animated);
    }

    final void runtimeReceivePickerResult(Intent data, boolean persistPermission) {
        receivePickerResult(data, persistPermission);
    }

    final void runtimeMoveSticker(int fromIndex, int toIndex) {
        moveSticker(fromIndex, toIndex);
    }

    final boolean runtimeSelectCover(Uri uri) {
        if (processing || uri == null || !selectedUris.contains(uri)) return false;
        coverUri = uri;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    final boolean runtimeRemoveMediaAt(int index) {
        if (processing || index < 0 || index >= selectedUris.size()) return false;
        Uri removed = selectedUris.remove(index);
        if (removed != null && removed.equals(coverUri)) {
            coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
        }
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    final boolean runtimeClearMedia() {
        if (processing || selectedUris.isEmpty()) return false;
        selectedUris.clear();
        coverUri = null;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BugLogStore.install();
        configureWindow();
        previewLoader = new PreviewLoader(this);

        try {
            setContentView(buildUi());
        } catch (Throwable error) {
            showSafeFallback();
            return;
        }

        currentPack = null;
        updateModeUi();
        updateUiState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (previewContainer != null) renderPreviews();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    protected View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(gradient(PRIMARY, TEAL, 24));
        root.addView(hero, matchWrap());

        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(heroRow, matchWrap());

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(9), dp(9), dp(9), dp(9));
        logo.setBackground(rounded(0x24FFFFFF, 17));
        heroRow.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout heroTexts = new LinearLayout(this);
        heroTexts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams heroTextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        heroTextParams.leftMargin = dp(14);
        heroRow.addView(heroTexts, heroTextParams);

        heroTexts.addView(text("WA Stickers", 13, 0xFFD9FFF0, Typeface.BOLD));
        TextView title = text("Стикеры из фото и видео", 24, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(2);
        heroTexts.addView(title, titleParams);

        TextView subtitle = text(
                "Обычные и анимированные наборы — прямо на телефоне.",
                14, 0xFFE7F6F1, Typeface.NORMAL);
        subtitle.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(15);
        hero.addView(subtitle, subtitleParams);

        LinearLayout modeCard = card();
        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(14);
        root.addView(modeCard, modeParams);
        modeCard.addView(text("Тип набора", 17, TEXT, Typeface.BOLD));

        TextView modeHint = text(
                "Статичные и анимированные стикеры должны быть в разных наборах WhatsApp. Выбор каждого режима сохраняется отдельно.",
                12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams modeHintParams = matchWrap();
        modeHintParams.topMargin = dp(5);
        modeCard.addView(modeHint, modeHintParams);

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowParams = matchWrap();
        modeRowParams.topMargin = dp(12);
        modeCard.addView(modeRow, modeRowParams);

        photoModeButton = new Button(this);
        photoModeButton.setText("Фото");
        photoModeButton.setAllCaps(false);
        photoModeButton.setOnClickListener(v -> setAnimatedMode(false));
        modeRow.addView(photoModeButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        animatedModeButton = new Button(this);
        animatedModeButton.setText("Анимация");
        animatedModeButton.setAllCaps(false);
        animatedModeButton.setOnClickListener(v -> setAnimatedMode(true));
        LinearLayout.LayoutParams animatedModeParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        animatedModeParams.leftMargin = dp(8);
        modeRow.addView(animatedModeButton, animatedModeParams);

        LinearLayout nameCard = card();
        LinearLayout.LayoutParams nameCardParams = matchWrap();
        nameCardParams.topMargin = dp(12);
        root.addView(nameCard, nameCardParams);
        nameCard.addView(text("1  Название набора", 17, TEXT, Typeface.BOLD));

        packName = new EditText(this);
        packName.setHint("Мои стикеры");
        packName.setSingleLine(true);
        packName.setTextSize(16);
        packName.setTextColor(TEXT);
        packName.setHintTextColor(0xFF9BA7A2);
        packName.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable input = rounded(0xFFFAFCFB, 14);
        input.setStroke(dp(1), BORDER);
        packName.setBackground(input);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        nameParams.topMargin = dp(12);
        nameCard.addView(packName, nameParams);

        LinearLayout mediaCard = card();
        LinearLayout.LayoutParams mediaParams = matchWrap();
        mediaParams.topMargin = dp(12);
        root.addView(mediaCard, mediaParams);

        LinearLayout mediaHeader = new LinearLayout(this);
        mediaHeader.setOrientation(LinearLayout.HORIZONTAL);
        mediaHeader.setGravity(Gravity.CENTER_VERTICAL);
        mediaCard.addView(mediaHeader, matchWrap());

        mediaTitle = text("", 17, TEXT, Typeface.BOLD);
        mediaHeader.addView(mediaTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        countText = text("0 / 30", 12, PRIMARY, Typeface.BOLD);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(dp(10), 0, dp(10), 0);
        countText.setBackground(rounded(SOFT, 14));
        mediaHeader.addView(countText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        mediaHint = text("", 13, MUTED, Typeface.NORMAL);
        mediaHint.setLineSpacing(0, 1.07f);
        LinearLayout.LayoutParams mediaHintParams = matchWrap();
        mediaHintParams.topMargin = dp(8);
        mediaCard.addView(mediaHint, mediaHintParams);

        galleryButton = new Button(this);
        styleButton(galleryButton, PRIMARY, Color.WHITE);
        galleryButton.setOnClickListener(v -> openMediaPicker());
        LinearLayout.LayoutParams galleryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        galleryParams.topMargin = dp(14);
        mediaCard.addView(galleryButton, galleryParams);

        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.setHorizontalScrollBarEnabled(false);
        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.HORIZONTAL);
        previewContainer.setGravity(Gravity.CENTER_VERTICAL);
        previewScroll.addView(previewContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
        previewParams.topMargin = dp(12);
        mediaCard.addView(previewScroll, previewParams);

        LinearLayout actionsCard = card();
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        root.addView(actionsCard, actionsParams);
        actionsCard.addView(text("3  Готовый набор", 17, TEXT, Typeface.BOLD));

        actionHint = text("", 13, MUTED, Typeface.NORMAL);
        actionHint.setLineSpacing(0, 1.07f);
        LinearLayout.LayoutParams actionHintParams = matchWrap();
        actionHintParams.topMargin = dp(7);
        actionsCard.addView(actionHint, actionHintParams);

        progressText = text("", 13, PRIMARY, Typeface.BOLD);
        progressText.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressTextParams = matchWrap();
        progressTextParams.topMargin = dp(13);
        actionsCard.addView(progressText, progressTextParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(MAX_STICKERS);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16));
        progressParams.topMargin = dp(7);
        actionsCard.addView(progressBar, progressParams);

        fileProgressContainer = new LinearLayout(this);
        fileProgressContainer.setOrientation(LinearLayout.VERTICAL);
        fileProgressContainer.setPadding(dp(12), dp(8), dp(12), dp(8));
        fileProgressContainer.setBackground(rounded(0xFFF7FAF8, 14));
        fileProgressContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams fileProgressParams = matchWrap();
        fileProgressParams.topMargin = dp(10);
        actionsCard.addView(fileProgressContainer, fileProgressParams);

        createButton = new Button(this);
        createButton.setText("Создать набор");
        styleButton(createButton, GREEN, 0xFF073B2B);
        createButton.setOnClickListener(v -> handleCreateAction());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        createParams.topMargin = dp(15);
        actionsCard.addView(createButton, createParams);

        bugLogButton = new Button(this);
        bugLogButton.setText("Показать баг-лог");
        styleButton(bugLogButton, SOFT, PRIMARY);
        bugLogButton.setVisibility(View.GONE);
        bugLogButton.setOnClickListener(v -> showBugLogDialog());
        LinearLayout.LayoutParams bugLogParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        bugLogParams.topMargin = dp(9);
        actionsCard.addView(bugLogButton, bugLogParams);

        addButton = new Button(this);
        addButton.setText("Добавить в WhatsApp");
        styleButton(addButton, PRIMARY, Color.WHITE);
        addButton.setOnClickListener(v -> handleAddAction());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        addParams.topMargin = dp(9);
        actionsCard.addView(addButton, addParams);

        statusText = text("", 13, MUTED, Typeface.NORMAL);
        statusText.setPadding(dp(15), dp(14), dp(15), dp(14));
        statusText.setBackground(rounded(SOFT, 16));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        TextView privacy = text(
                "Все файлы обрабатываются локально и никуда не загружаются.",
                12, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(16);
        root.addView(privacy, privacyParams);

        renderPreviews();
        return scroll;
    }

    private void showSafeFallback() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(BG);
        TextView title = text("WA Stickers", 24, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());
        TextView message = text(
                "Не удалось загрузить основной экран. Установите свежую сборку приложения.",
                14, MUTED, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(12);
        root.addView(message, params);
        setContentView(root);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, 20));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        return card;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.topMargin = dp(14);
        return params;
    }

    private TextView text(String value, int size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable gradient(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void styleButton(Button button, int bg, int fg) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(fg);
        button.setBackground(rounded(bg, 17));
        button.setPadding(dp(14), 0, dp(14), 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openMediaPicker() {
        int requestCode = animatedMode ? REQUEST_PICK_ANIMATED : REQUEST_PICK_PHOTOS;
        Intent intent = MediaPickerIntentFactory.createOpenDocumentIntent(animatedMode);
        String title = animatedMode ? "Выберите GIF, WebP или видео" : "Выберите фото";
        try {
            if (animatedMode) {
                Toast.makeText(
                        this,
                        "Можно выбрать несколько файлов: удерживайте первый, затем отметьте остальные",
                        Toast.LENGTH_LONG
                ).show();
                startActivityForResult(intent, requestCode);
            } else {
                startActivityForResult(Intent.createChooser(intent, title), requestCode);
            }
        } catch (ActivityNotFoundException first) {
            Intent fallback = MediaPickerIntentFactory.createGetContentFallback(animatedMode);
            try {
                startActivityForResult(Intent.createChooser(fallback, title), requestCode);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(
                        this,
                        animatedMode ? "Не найдено приложение для выбора файлов" : "Галерея не найдена",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_TO_WHATSAPP) {
            if (resultCode == RESULT_OK) {
                statusText.setText("WhatsApp подтвердил добавление набора.");
            } else {
                statusText.setText("WhatsApp не подтвердил добавление. Попробуйте ещё раз.");
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_PICK_PHOTOS) {
            receivePickerResult(data, false);
        } else if (requestCode == REQUEST_PICK_ANIMATED) {
            receivePickerResult(data, true);
        }
    }

    private void receivePickerResult(Intent data, boolean persistPermission) {
        List<Uri> incoming = collectUris(data);
        if (incoming.isEmpty()) return;
        if (persistPermission) persistIncomingPermissions(data, incoming);

        if (incoming.size() > MAX_STICKERS) {
            incoming = new ArrayList<>(incoming.subList(0, MAX_STICKERS));
            Toast.makeText(this, "Выбраны первые 30 файлов", Toast.LENGTH_LONG).show();
        }

        selectedUris.clear();
        selectedUris.addAll(incoming);
        coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
    }

    private List<Uri> collectUris(Intent data) {
        List<Uri> result = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !result.contains(uri)) result.add(uri);
            }
        }
        Uri single = data.getData();
        if (single != null && !result.contains(single)) result.add(single);
        return result;
    }

    private void persistIncomingPermissions(Intent data, List<Uri> uris) {
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
        }
    }

    private void setAnimatedMode(boolean animated) {
        if (processing || animatedMode == animated) return;
        String currentName = packName == null ? "" : packName.getText().toString();
        EditorStateController.Snapshot<Uri> next = editorStateController.switchMode(
                animatedMode,
                animated,
                selectedUris,
                coverUri,
                currentName
        );
        animatedMode = animated;
        currentPack = null;
        selectedUris.clear();
        selectedUris.addAll(next.items());
        coverUri = next.cover();
        if (packName != null) packName.setText(next.name());
        renderPreviews();
        updateModeUi();
        updateUiState();
    }

    private void renderPreviews() {
        if (previewContainer == null) return;
        previewContainer.removeAllViews();
        for (int i = 0; i < selectedUris.size(); i++) {
            Uri uri = selectedUris.get(i);
            int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(6), dp(6), dp(6), dp(6));
            card.setBackground(rounded(0xFFF7FAF8, 14));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(102), dp(112));
            cardParams.rightMargin = dp(8);
            previewContainer.addView(card, cardParams);

            FrameLayout frame = new FrameLayout(this);
            card.addView(frame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(82)));

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFFE6EEE9);
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            previewLoader.load(uri, (requestKey, bitmap) -> runOnUiThread(() -> {
                if (activityDestroyed || isFinishing()) return;
                if (bitmap != null && uri.equals(image.getTag())) image.setImageBitmap(bitmap);
            }));
            image.setTag(uri);

            TextView cover = text(uri.equals(coverUri) ? "★" : "☆", 21,
                    uri.equals(coverUri) ? 0xFFFFC107 : Color.WHITE, Typeface.BOLD);
            cover.setGravity(Gravity.CENTER);
            cover.setContentDescription(uri.equals(coverUri) ? "Обложка набора" : "Сделать обложкой");
            cover.setBackground(rounded(0xA8075E54, 12));
            cover.setOnClickListener(v -> {
                if (!processing && selectedUris.contains(uri)) {
                    coverUri = uri;
                    invalidateCurrentPack();
                    renderPreviews();
                    updateUiState();
                }
            });
            FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(dp(38), dp(38));
            coverParams.gravity = Gravity.START | Gravity.TOP;
            coverParams.leftMargin = dp(4);
            coverParams.topMargin = dp(4);
            frame.addView(cover, coverParams);

            frame.setOnLongClickListener(v -> {
                if (processing) return false;
                v.startDragAndDrop(null, new View.DragShadowBuilder(v), index, 0);
                return true;
            });
            frame.setOnDragListener((v, event) -> {
                if (event.getAction() == DragEvent.ACTION_DROP && event.getLocalState() instanceof Integer) {
                    moveSticker((Integer) event.getLocalState(), index);
                    return true;
                }
                return true;
            });

            TextView remove = text("×", 22, Color.WHITE, Typeface.BOLD);
            remove.setGravity(Gravity.CENTER);
            remove.setContentDescription("Удалить стикер " + (i + 1));
            remove.setBackground(rounded(0xCC17231F, 12));
            remove.setOnClickListener(v -> {
                if (!processing && index >= 0 && index < selectedUris.size()) {
                    Uri removed = selectedUris.remove(index);
                    if (removed.equals(coverUri)) coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
                    invalidateCurrentPack();
                    renderPreviews();
                    updateUiState();
                }
            });
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(38), dp(38));
            removeParams.gravity = Gravity.END | Gravity.TOP;
            removeParams.topMargin = dp(4);
            removeParams.rightMargin = dp(4);
            frame.addView(remove, removeParams);
        }
    }

    private void moveSticker(int fromIndex, int toIndex) {
        if (processing || !StickerOrderPolicy.move(selectedUris, fromIndex, toIndex)) return;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
    }

    private void handleCreateAction() {
        if (processing) {
            cancelProcessing();
            return;
        }
        if (buildSession.isActive()) {
            if (buildSession.hasFailures()) {
                retryFailedItems();
            } else {
                finalizePendingPackAsync();
            }
            return;
        }
        startFreshBuild();
    }

    private void startFreshBuild() {
        if (processing) return;
        invalidateCurrentPack();
        if (selectedUris.size() < MIN_STICKERS || selectedUris.size() > MAX_STICKERS) {
            Toast.makeText(this, "Выберите от 3 до 30 файлов", Toast.LENGTH_LONG).show();
            return;
        }
        boolean requiresTrim = false;
        try {
            MediaPreflightAnalyzer.SelectionReport report =
                    MediaPreflightAnalyzer.analyze(this, new ArrayList<>(selectedUris));
            requiresTrim = report != null && report.hasLongVideos;
        } catch (Throwable error) {
            BugLogStore.appendApp("Preflight before build failed: " + error);
        }
        if (requiresTrim) {
            try {
                List<VideoTrimStore.Entry> entries = VideoTrimStore.prepare(this, selectedUris);
                if (!entries.isEmpty()) {
                    ArrayList<String> keys = new ArrayList<>();
                    for (VideoTrimStore.Entry entry : entries) keys.add(entry.key);
                    Intent trimIntent = new Intent(this, VideoTrimActivity.class);
                    trimIntent.putStringArrayListExtra(VideoTrimActivity.EXTRA_KEYS, keys);
                    startActivity(trimIntent);
                    statusText.setText("Настройте фрагменты длинных видео и затем запустите сборку снова.");
                    return;
                }
            } catch (Throwable error) {
                BugLogStore.appendApp("Could not open trim before build: " + error);
            }
        }
        startBuildSession(selectedUris, animatedMode);
    }

    private void startBuildSession(List<Uri> uris, boolean animated) {
        if (uris == null || uris.size() < MIN_STICKERS || uris.size() > MAX_STICKERS) return;
        String name = packName.getText().toString().trim();
        if (name.isEmpty()) name = animated ? "Мои анимированные стикеры" : "Мои стикеры";
        String packId = (animated ? "animated_" : "pack_") + System.currentTimeMillis();
        File packDir = PackStore.getPackDir(this, packId);
        Uri sessionCover = coverUri != null && uris.contains(coverUri) ? coverUri : uris.get(0);
        buildSession.begin(packId, name, packDir, animated, sessionCover);
        startBatch(new ArrayList<>(uris), false);
    }

    private void startBatch(List<Uri> workItems, boolean resetFailures) {
        if (workItems == null || workItems.isEmpty() || processing || !buildSession.isActive()) return;
        if (resetFailures) buildSession.setFailures(new ArrayList<>());
        resetDiagnostics();
        BugLogStore.reset();
        processing = true;
        buildSession.clearCancelRequested();
        lastOperationFailed = false;
        currentPack = null;
        setBuildUiBusy(true);
        setupFileProgress(workItems);

        final List<Uri> batchItems = new ArrayList<>(workItems);
        final boolean modeAnimated = buildSession.isAnimated();
        executor.execute(() -> {
            try {
                List<Uri> failures = modeAnimated
                        ? processAnimatedBatch(batchItems)
                        : processStaticBatch(batchItems);
                if (failures == null) return;
                List<Uri> combinedFailures = new ArrayList<>(failures);
                if (!resetFailures) {
                    for (Uri previous : buildSession.failures()) {
                        if (!combinedFailures.contains(previous)) combinedFailures.add(previous);
                    }
                }
                buildSession.setFailures(combinedFailures);

                if (buildSession.isCancelRequested()) {
                    discardPendingBuild();
                    postStatus("Создание отменено. Можно выбрать другие файлы или запустить снова.");
                } else if (!buildSession.hasFailures()) {
                    if (buildSession.autoFinalizeAllowed()) {
                        finalizePendingPack();
                    } else {
                        postStatus("Обработка завершена. Нажмите «Готово», чтобы сохранить набор.");
                    }
                } else if (buildSession.canFinalize()) {
                    postStatus("Часть файлов не обработалась. Можно повторить ошибки или завершить набор без них.");
                } else {
                    postStatus("Слишком мало готовых стикеров. Повторите обработку ошибок.");
                }
            } catch (Throwable error) {
                if (buildSession.isCancelRequested()) {
                    discardPendingBuild();
                    postStatus("Создание отменено. Можно выбрать другие файлы или запустить снова.");
                } else {
                    lastOperationFailed = true;
                    recordException("Сборка набора", error);
                    postStatus("Ошибка сборки. Откройте баг-лог, чтобы увидеть детали.");
                }
            } finally {
                processing = false;
                runOnUiThread(() -> {
                    setBuildUiBusy(false);
                    updateUiState();
                });
            }
        });
    }

    private List<Uri> processStaticBatch(List<Uri> workItems) throws Exception {
        List<Uri> failed = new ArrayList<>();
        for (int i = 0; i < workItems.size(); i++) {
            if (buildSession.isCancelRequested()) break;
            diagnosticItemIndex = i;
            diagnosticItemUri = workItems.get(i);
            Uri uri = workItems.get(i);
            String name = queryDisplayName(uri);
            updateFileProgress(i, "Анализ", 5);
            try {
                StickerPreprocessCache.Result prepared = StickerPreprocessCache.getOrPrepare(
                        this,
                        uri,
                        buildSession.packDir(),
                        "photo_" + i,
                        null
                );
                updateFileProgress(i, "Конвертация в WebP", 30);
                StickerPackBuilder.BuildResult result = StickerPackBuilder.buildSticker(
                        this,
                        prepared.decodeUri,
                        buildSession.packDir(),
                        buildSession.outputPrefixFor(uri, "photo_" + i),
                        null,
                        new StickerPackBuilder.ProgressListener() {
                            @Override
                            public void onStage(String stage, int percent) {
                                updateFileProgress(i, stage, percent);
                            }
                        }
                );
                buildSession.markSuccess(uri, result.outputFile);
                updateFileProgress(i, "Готово", 100);
            } catch (Throwable error) {
                BugLogStore.appendApp("Static item failed: " + name + " -> " + error);
                buildSession.markFailure(uri);
                updateFileProgress(i, "Ошибка", 100);
                failed.add(uri);
            }
        }
        return failed;
    }

    private List<Uri> processAnimatedBatch(List<Uri> workItems) throws Exception {
        List<Uri> failed = new ArrayList<>();
        for (int i = 0; i < workItems.size(); i++) {
            if (buildSession.isCancelRequested()) break;
            diagnosticItemIndex = i;
            diagnosticItemUri = workItems.get(i);
            Uri uri = workItems.get(i);
            String name = queryDisplayName(uri);
            updateFileProgress(i, "Анализ", 5);
            try {
                MediaPreflightAnalyzer.ItemReport item = MediaPreflightAnalyzer.analyzeOne(this, uri);
                if (item == null || !item.supported || !item.animatedCandidate) {
                    throw new IOException(item == null ? "Не удалось определить тип файла" : item.issue);
                }
                String stableKey = buildSession.outputPrefixFor(uri, "anim_" + i);
                if (item.videoCandidate) {
                    long startOffsetMs = VideoTrimStore.getStartOffsetMs(uri);
                    AnimatedStickerConverter.convertVideo(
                            this,
                            uri,
                            buildSession.packDir(),
                            stableKey,
                            startOffsetMs,
                            new AnimatedStickerConverter.ProgressListener() {
                                @Override
                                public void onStage(String stage, int percent) {
                                    updateFileProgress(i, stage, percent);
                                }
                            }
                    );
                } else {
                    AnimatedStickerConverter.convertAnimatedImage(
                            this,
                            uri,
                            buildSession.packDir(),
                            stableKey,
                            new AnimatedStickerConverter.ProgressListener() {
                                @Override
                                public void onStage(String stage, int percent) {
                                    updateFileProgress(i, stage, percent);
                                }
                            }
                    );
                }
                buildSession.markSuccess(uri, new File(buildSession.packDir(), stableKey + ".webp"));
                updateFileProgress(i, "Готово", 100);
            } catch (Throwable error) {
                BugLogStore.appendApp("Animated item failed: " + name + " -> " + error);
                buildSession.markFailure(uri);
                updateFileProgress(i, "Ошибка", 100);
                failed.add(uri);
            }
        }
        return failed;
    }

    private void retryFailedItems() {
        if (processing || !buildSession.isActive() || !buildSession.hasFailures()) return;
        startBatch(new ArrayList<>(buildSession.failures()), true);
    }

    private void cancelProcessing() {
        if (!processing || !buildSession.isActive()) return;
        buildSession.requestCancel();
        progressText.setText("Останавливаем обработку…");
        createButton.setEnabled(false);
    }

    private void finalizePendingPackAsync() {
        if (processing || !buildSession.isActive() || !buildSession.canFinalize()) return;
        processing = true;
        lastOperationFailed = false;
        setBuildUiBusy(true);
        executor.execute(() -> {
            try {
                finalizePendingPack();
            } catch (Throwable error) {
                lastOperationFailed = true;
                recordException("Финализация набора", error);
                postStatus("Не удалось завершить набор. Откройте баг-лог для деталей.");
            } finally {
                processing = false;
                runOnUiThread(() -> {
                    setBuildUiBusy(false);
                    updateUiState();
                });
            }
        });
    }

    private void finalizePendingPack() throws Exception {
        if (!buildSession.isActive() || !buildSession.canFinalize()) return;
        List<Uri> successful = buildSession.successfulUris();
        if (successful.size() < MIN_STICKERS) return;

        List<File> stickers = new ArrayList<>();
        for (Uri uri : successful) {
            File output = buildSession.outputFor(uri);
            if (output != null && output.isFile()) stickers.add(output);
        }
        if (stickers.size() < MIN_STICKERS) return;

        Uri chosenCover = buildSession.coverUri();
        if (chosenCover == null || !successful.contains(chosenCover)) chosenCover = successful.get(0);
        File tray = buildSession.isAnimated()
                ? AnimatedStickerConverter.buildTrayIcon(this, chosenCover, buildSession.packDir(),
                        VideoTrimStore.getStartOffsetMs(chosenCover))
                : StickerPackBuilder.buildTrayIcon(this, chosenCover, buildSession.packDir());

        PackStore.Pack pack = PackStore.savePack(
                this,
                buildSession.packId(),
                buildSession.packName(),
                buildSession.packDir(),
                stickers,
                tray,
                buildSession.isAnimated()
        );
        currentPack = pack;
        buildSession.reset();
        postStatus("Набор готов: " + pack.name + ". Можно добавить в WhatsApp.");
    }

    private void discardPendingBuild() {
        buildSession.deletePackDir();
        buildSession.reset();
    }

    private void addCurrentPackToWhatsApp() {
        if (currentPack == null) return;
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", currentPack.id);
        intent.putExtra("sticker_pack_authority", getPackageName() + ".stickercontentprovider");
        intent.putExtra("sticker_pack_name", currentPack.name);
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "WhatsApp не найден", Toast.LENGTH_LONG).show();
        }
    }

    private void invalidateCurrentPack() {
        currentPack = null;
        clearPendingBuildState();
    }

    private void clearPendingBuildState() {
        buildSession.reset();
    }

    private void updateModeUi() {
        if (photoModeButton == null || animatedModeButton == null) return;
        photoModeButton.setBackground(rounded(animatedMode ? 0xFFF2F6F4 : PRIMARY, 14));
        photoModeButton.setTextColor(animatedMode ? PRIMARY : Color.WHITE);
        animatedModeButton.setBackground(rounded(animatedMode ? PRIMARY : 0xFFF2F6F4, 14));
        animatedModeButton.setTextColor(animatedMode ? Color.WHITE : PRIMARY);
        galleryButton.setText(animatedMode ? "Выбрать GIF / WebP / видео" : "Выбрать фото");
        mediaTitle.setText(animatedMode ? "2  Анимации" : "2  Фотографии");
        mediaHint.setText(animatedMode
                ? "Выберите от 3 до 30 GIF, анимированных WebP или видео. Видео длиннее 10 секунд можно обрезать перед сборкой."
                : "Выберите от 3 до 30 изображений. Первое станет обложкой, её можно поменять звездой.");
    }

    private void updateUiState() {
        int count = selectedUris.size();
        boolean enough = count >= MIN_STICKERS && count <= MAX_STICKERS;
        boolean pending = buildSession.isActive();
        countText.setText(count + " / " + MAX_STICKERS);

        if (processing) {
            createButton.setText("Остановить");
            createButton.setEnabled(true);
            galleryButton.setEnabled(false);
            photoModeButton.setEnabled(false);
            animatedModeButton.setEnabled(false);
            addButton.setEnabled(false);
            actionHint.setText("Идёт обработка. Можно остановить текущую сборку.");
        } else if (pending && buildSession.hasFailures()) {
            createButton.setText("Повторить ошибки");
            createButton.setEnabled(true);
            galleryButton.setEnabled(true);
            photoModeButton.setEnabled(true);
            animatedModeButton.setEnabled(true);
            addButton.setEnabled(false);
            actionHint.setText(buildSession.canFinalize()
                    ? "Есть готовые стикеры и ошибки. Повторите ошибки или завершите набор без них."
                    : "Недостаточно готовых стикеров. Повторите обработку ошибок.");
        } else if (pending && buildSession.canFinalize()) {
            createButton.setText("Готово");
            createButton.setEnabled(true);
            galleryButton.setEnabled(true);
            photoModeButton.setEnabled(true);
            animatedModeButton.setEnabled(true);
            addButton.setEnabled(false);
            actionHint.setText("Обработка завершена. Сохраните набор кнопкой «Готово».");
        } else {
            createButton.setText("Создать набор");
            createButton.setEnabled(enough);
            galleryButton.setEnabled(true);
            photoModeButton.setEnabled(true);
            animatedModeButton.setEnabled(true);
            addButton.setEnabled(currentPack != null);
            actionHint.setText(currentPack != null
                    ? "Набор готов. Добавьте его в WhatsApp."
                    : (enough ? "Файлы готовы к обработке." : "Нужно выбрать от 3 до 30 файлов."));
        }

        boolean showProgress = processing || pending;
        progressText.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        fileProgressContainer.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (pending && !processing) {
            progressBar.setProgress(buildSession.successCount());
            progressText.setText(buildSession.successCount() + " / " + Math.max(1, selectedUris.size()));
        }
        bugLogButton.setVisibility(lastOperationFailed ? View.VISIBLE : View.GONE);
    }

    private void setBuildUiBusy(boolean busy) {
        galleryButton.setEnabled(!busy);
        photoModeButton.setEnabled(!busy);
        animatedModeButton.setEnabled(!busy);
        addButton.setEnabled(!busy && currentPack != null);
        createButton.setEnabled(true);
        if (busy) createButton.setText("Остановить");
    }

    private void setupFileProgress(List<Uri> workItems) {
        fileProgressContainer.removeAllViews();
        fileProgressLabels.clear();
        fileProgressBars.clear();
        fileProgressNames.clear();
        for (int i = 0; i < workItems.size(); i++) {
            String name = queryDisplayName(workItems.get(i));
            fileProgressNames.add(name);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            fileProgressContainer.addView(row, matchWrap());

            TextView label = text((i + 1) + ". " + name + "\nВ очереди", 12, TEXT, Typeface.NORMAL);
            row.addView(label, matchWrap());
            fileProgressLabels.add(label);

            ProgressBar itemProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            itemProgress.setMax(100);
            itemProgress.setProgress(0);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
            progressParams.topMargin = dp(5);
            row.addView(itemProgress, progressParams);
            fileProgressBars.add(itemProgress);
        }
    }

    private void updateFileProgress(int index, String stage, int percent) {
        if (index < 0 || index >= fileProgressLabels.size()) return;
        runOnUiThread(() -> {
            if (index < 0 || index >= fileProgressLabels.size()) return;
            String name = fileProgressNames.get(index);
            fileProgressLabels.get(index).setText((index + 1) + ". " + name + "\n" + stage);
            fileProgressBars.get(index).setProgress(Math.max(0, Math.min(100, percent)));
            int completed = 0;
            for (ProgressBar bar : fileProgressBars) {
                if (bar.getProgress() >= 100) completed++;
            }
            progressBar.setProgress(completed);
            progressText.setText(completed + " / " + Math.max(1, fileProgressBars.size()));
        });
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) return "Файл";
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String value = cursor.getString(idx);
                        if (value != null && !value.trim().isEmpty()) return value;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String last = uri.getLastPathSegment();
        return last == null || last.trim().isEmpty() ? "Файл" : last;
    }

    private void postStatus(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }

    private void resetDiagnostics() {
        lastBugLog = "";
        diagnosticItemIndex = -1;
        diagnosticItemUri = null;
    }

    private void recordException(String stage, Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        error.printStackTrace(writer);
        writer.flush();
        lastBugLog = stage + "\n" + buffer;
        BugLogStore.appendApp(lastBugLog);
    }

    private void showBugLogDialog() {
        String content = BugLogStore.getAppLog();
        if (content == null || content.trim().isEmpty()) content = lastBugLog;
        if (content == null || content.trim().isEmpty()) content = "Лог пока пуст.";

        TextView logView = text(content, 12, TEXT, Typeface.NORMAL);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(16), dp(16), dp(16), dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Баг-лог")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .setNeutralButton("Копировать", (dialog, which) -> copyBugLog())
                .show();
    }

    private void copyBugLog() {
        String content = BugLogStore.getAppLog();
        if (content == null || content.trim().isEmpty()) content = lastBugLog;
        if (content == null || content.trim().isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("WA Stickers bug log", content));
        Toast.makeText(this, "Баг-лог скопирован", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        executor.shutdownNow();
        if (previewLoader != null) previewLoader.close();
        FFmpegKit.cancel();
        super.onDestroy();
    }
}
