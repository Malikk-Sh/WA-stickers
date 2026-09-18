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

    private View buildUi() {
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

    private GradientDrawable gradient(int first, int second, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{first, second});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void styleButton(Button button, int fillColor, int textColor) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(rounded(fillColor, 14));
    }

    private void styleModeButton(Button button, boolean active) {
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setTextColor(active ? Color.WHITE : PRIMARY);
        GradientDrawable background = rounded(active ? PRIMARY : 0xFFF2F7F4, 13);
        if (!active) background.setStroke(dp(1), BORDER);
        button.setBackground(background);
    }

    private void invalidateCurrentPack() {
        discardPendingBuild();
        currentPack = null;
        lastOperationFailed = false;
        lastBugLog = "";
        hideDiagnosticsUi();
    }

    private void discardPendingBuild() {
        File pendingDir = buildSession.packDir();
        if (buildSession.isActive() && pendingDir != null) {
            deleteRecursively(pendingDir);
        }
        clearPendingBuildState();
    }

    private void clearPendingBuildState() {
        buildSession.reset();
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
        invalidateCurrentPack();
        animatedMode = animated;
        selectedUris.clear();
        selectedUris.addAll(next.items());
        coverUri = next.cover();
        if (packName != null) packName.setText(next.name());
        renderPreviews();
        updateModeUi();
        updateUiState();
    }

    private void updateModeUi() {
        if (photoModeButton == null) return;
        styleModeButton(photoModeButton, !animatedMode);
        styleModeButton(animatedModeButton, animatedMode);

        if (animatedMode) {
            mediaTitle.setText("2  Анимации и видео");
            mediaHint.setText("Выберите 3–30 файлов. Удерживайте превью, чтобы поменять порядок; ★ выбирает обложку набора.");
            actionHint.setText("До 10 секунд на стикер. Ошибки отдельных файлов не сбрасывают уже готовые стикеры: их можно повторить отдельно.");
        } else {
            mediaTitle.setText("2  Фотографии");
            mediaHint.setText("Нужно 3–30 фото. Удерживайте превью, чтобы поменять порядок; ★ выбирает обложку набора.");
            actionHint.setText("Каждое фото помещается целиком в 512×512 WebP до 100 КБ. Ошибочный файл можно повторить без обработки готовых заново.");
        }
    }

    private void openMediaPicker() {
        if (animatedMode) openAnimatedPicker();
        else openPhotoGallery();
    }

    private void openPhotoGallery() {
        Intent gallery = new Intent(Intent.ACTION_PICK);
        gallery.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(gallery, REQUEST_PICK_PHOTOS);
        } catch (ActivityNotFoundException first) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(fallback, "Выберите фото"), REQUEST_PICK_PHOTOS);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Галерея не найдена", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAnimatedPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/gif",
                "image/webp",
                "video/mp4",
                "video/webm",
                "video/quicktime",
                "video/x-matroska",
                "video/*"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, "Выберите анимации или видео"), REQUEST_PICK_ANIMATED);
        } catch (ActivityNotFoundException first) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("*/*");
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(fallback, "Выберите GIF, WebP или видео"), REQUEST_PICK_ANIMATED);
            } catch (ActivityNotFoundException second) {
                Toast.makeText(this, "Не найдено приложение для выбора файлов", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_PICK_PHOTOS || requestCode == REQUEST_PICK_ANIMATED)
                && resultCode == RESULT_OK && data != null) {
            receivePickerResult(data, requestCode == REQUEST_PICK_ANIMATED);
            return;
        }

        if (requestCode == REQUEST_ADD_TO_WHATSAPP && resultCode == RESULT_OK && statusText != null) {
            statusText.setText("Набор успешно добавлен в WhatsApp.");
        }
    }

    private void receivePickerResult(Intent data, boolean persistPermission) {
        List<Uri> incoming = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount() && incoming.size() < MAX_STICKERS; i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) incoming.add(uri);
            }
        } else if (data.getData() != null) {
            incoming.add(data.getData());
        }

        boolean changed = false;
        for (Uri uri : incoming) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            if (!selectedUris.contains(uri)) {
                selectedUris.add(uri);
                changed = true;
            }
            if (persistPermission) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
        }

        if (coverUri == null && !selectedUris.isEmpty()) coverUri = selectedUris.get(0);
        if (changed) invalidateCurrentPack();
        renderPreviews();
        updateUiState();
    }

    private void renderPreviews() {
        if (previewContainer == null) return;
        previewContainer.removeAllViews();

        if (selectedUris.isEmpty()) {
            coverUri = null;
            TextView empty = text(
                    animatedMode ? "Выбранные анимации появятся здесь" : "Выбранные фото появятся здесь",
                    13, MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), 0, dp(16), 0);
            empty.setBackground(rounded(0xFFF0F4F2, 14));
            previewContainer.addView(empty, new LinearLayout.LayoutParams(dp(270), dp(92)));
            return;
        }

        if (coverUri == null || !selectedUris.contains(coverUri)) coverUri = selectedUris.get(0);

        for (int i = 0; i < selectedUris.size(); i++) {
            final int index = i;
            Uri uri = selectedUris.get(i);
            boolean isCover = uri.equals(coverUri);

            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(96), dp(96));
            frameParams.rightMargin = dp(9);
            previewContainer.addView(frame, frameParams);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(0xFFE9EFEC, 15));
            image.setContentDescription("Стикер " + (i + 1) + " из " + selectedUris.size()
                    + (isCover ? ", выбран как обложка" : "")
                    + ". Удерживайте для изменения порядка.");
            if (Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            loadPreviewAsync(uri, image);
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            frame.setOnLongClickListener(v -> {
                if (processing) return false;
                ClipData dragData = ClipData.newPlainText("sticker", String.valueOf(index));
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(frame);
                if (Build.VERSION.SDK_INT >= 24) {
                    frame.startDragAndDrop(dragData, shadow, Integer.valueOf(index), 0);
                } else {
                    //noinspection deprecation
                    frame.startDrag(dragData, shadow, Integer.valueOf(index), 0);
                }
                return true;
            });
            frame.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return !processing && event.getLocalState() instanceof Integer;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        frame.setAlpha(0.72f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        frame.setAlpha(1f);
                        return true;
                    case DragEvent.ACTION_DROP:
                        frame.setAlpha(1f);
                        Object state = event.getLocalState();
                        if (state instanceof Integer) moveSticker((Integer) state, index);
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        frame.setAlpha(1f);
                        return true;
                    default:
                        return true;
                }
            });

            if (animatedMode) {
                TextView play = text("▶", 13, Color.WHITE, Typeface.BOLD);
                play.setGravity(Gravity.CENTER);
                play.setContentDescription("Анимированный стикер");
                play.setBackground(rounded(0xAA075E54, 14));
                FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(dp(30), dp(30));
                playParams.gravity = Gravity.CENTER;
                frame.addView(play, playParams);
            }

            TextView cover = text(isCover ? "★" : "☆", 18, isCover ? 0xFF073B2B : PRIMARY, Typeface.BOLD);
            cover.setGravity(Gravity.CENTER);
            cover.setContentDescription(isCover ? "Текущая обложка набора" : "Сделать стикер " + (i + 1) + " обложкой");
            cover.setBackground(rounded(isCover ? 0xE625D366 : 0xEFFFFFFF, 12));
            cover.setOnClickListener(v -> {
                if (processing || uri.equals(coverUri)) return;
                coverUri = uri;
                invalidateCurrentPack();
                renderPreviews();
                updateUiState();
            });
            FrameLayout.LayoutParams coverParams = new FrameLayout.LayoutParams(dp(28), dp(28));
            coverParams.gravity = Gravity.BOTTOM | Gravity.START;
            coverParams.bottomMargin = dp(4);
            coverParams.leftMargin = dp(4);
            frame.addView(cover, coverParams);

            TextView remove = text("×", 18, Color.WHITE, Typeface.BOLD);
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
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(26), dp(26));
            removeParams.gravity = Gravity.TOP | Gravity.END;
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

    private void loadPreviewAsync(Uri uri, ImageView image) {
        if (previewLoader == null || uri == null || image == null) return;
        String requestKey = previewLoader.requestKey(uri);
        image.setTag(requestKey);
        image.setImageDrawable(null);
        previewLoader.load(uri, (loadedKey, bitmap) -> postUi(() -> {
            Object tag = image.getTag();
            if (!(tag instanceof String) || !loadedKey.equals(tag)) return;
            if (bitmap != null) image.setImageBitmap(bitmap);
        }));
    }

    private void handleCreateAction() {
        if (processing) {
            cancelProcessing();
            return;
        }
        if (buildSession.isActive() && buildSession.hasFailures()) {
            retryFailedItems();
            return;
        }
        createPack();
    }

    private void handleAddAction() {
        if (processing) return;
        if (buildSession.isActive() && buildSession.canFinalize()) {
            finalizePendingPackAsync();
            return;
        }
        addCurrentPackToWhatsApp();
    }

    private void updateUiState() {
        if (countText != null) countText.setText(selectedUris.size() + " / " + MAX_STICKERS);

        if (galleryButton != null) {
            String open = animatedMode ? "Выбрать GIF / WebP / видео" : "Открыть галерею";
            String more = animatedMode ? "Добавить ещё анимации" : "Добавить ещё фото";
            galleryButton.setText(selectedUris.isEmpty() ? open : more);
            galleryButton.setEnabled(!processing && selectedUris.size() < MAX_STICKERS);
            galleryButton.setAlpha(galleryButton.isEnabled() ? 1f : 0.45f);
        }

        if (photoModeButton != null) {
            photoModeButton.setEnabled(!processing);
            animatedModeButton.setEnabled(!processing);
        }

        if (createButton != null) {
            if (processing) {
                styleButton(createButton, ERROR, Color.WHITE);
                createButton.setText(buildSession.isCancelRequested() ? "Останавливаю…" : "Отменить обработку");
                createButton.setEnabled(!buildSession.isCancelRequested());
            } else if (buildSession.isActive() && buildSession.hasFailures()) {
                styleButton(createButton, GREEN, 0xFF073B2B);
                createButton.setText("Повторить ошибки (" + buildSession.failureCount() + ")");
                createButton.setEnabled(true);
            } else {
                styleButton(createButton, GREEN, 0xFF073B2B);
                boolean enough = selectedUris.size() >= MIN_STICKERS && selectedUris.size() <= MAX_STICKERS;
                createButton.setText("Создать набор");
                createButton.setEnabled(enough);
            }
            createButton.setAlpha(createButton.isEnabled() ? 1f : 0.45f);
        }

        if (addButton != null) {
            if (!processing && buildSession.isActive() && buildSession.canFinalize()) {
                addButton.setText("Создать из готовых (" + buildSession.successCount() + ")");
                addButton.setEnabled(true);
            } else {
                addButton.setText("Добавить в WhatsApp");
                boolean packMatchesMode = currentPack != null && currentPack.animated == animatedMode;
                addButton.setEnabled(packMatchesMode && !processing);
            }
            addButton.setAlpha(addButton.isEnabled() ? 1f : 0.45f);
        }

        if (bugLogButton != null) {
            boolean showBugLog = lastOperationFailed && lastBugLog != null && !lastBugLog.isEmpty();
            bugLogButton.setVisibility(showBugLog ? View.VISIBLE : View.GONE);
        }

        if (statusText != null && !processing && !lastOperationFailed) {
            if (currentPack != null && currentPack.animated == animatedMode) {
                statusText.setText("Набор «" + currentPack.name + "» готов к добавлению в WhatsApp.");
            } else if (selectedUris.isEmpty()) {
                statusText.setText(animatedMode
                        ? "Выберите минимум 3 анимации или видео. Фото-черновик сохранён отдельно."
                        : "Выберите минимум 3 фотографии. Черновик анимации сохранён отдельно.");
            } else if (selectedUris.size() < MIN_STICKERS) {
                statusText.setText("Добавьте ещё " + (MIN_STICKERS - selectedUris.size())
                        + (animatedMode ? " файла." : " фото."));
            } else {
                statusText.setText(animatedMode
                        ? "Анимации выбраны. Можно менять порядок, обложку и создавать набор."
                        : "Фото выбраны. Можно менять порядок, обложку и создавать набор.");
            }
        }
    }

    private void createPack() {
        if (selectedUris.size() < MIN_STICKERS || selectedUris.size() > MAX_STICKERS) {
            Toast.makeText(this, "Нужно выбрать от 3 до 30 файлов", Toast.LENGTH_SHORT).show();
            return;
        }

        String enteredName = packName.getText().toString().trim();
        String finalName = enteredName.isEmpty()
                ? (animatedMode ? "Мои анимированные стикеры" : "Мои стикеры")
                : enteredName;
        List<Uri> work = new ArrayList<>(selectedUris);
        Uri preferredCover = coverUri != null && work.contains(coverUri) ? coverUri : work.get(0);

        invalidateCurrentPack();
        String packId = (animatedMode ? "animated_" : "pack_") + System.currentTimeMillis();
        File packDir = PackStore.getPackDir(this, packId);
        buildSession.begin(packId, finalName, packDir, animatedMode, preferredCover);

        diagnosticItemIndex = -1;
        diagnosticItemUri = null;
        BugLogStore.reset();
        BugLogStore.appendApp("Starting draft pack. mode=" + (buildSession.isAnimated() ? "animated" : "static")
                + ", items=" + work.size()
                + ", customCover=" + describeUri(preferredCover));
        startBatch(work, false);
    }

    private void retryFailedItems() {
        if (processing || !buildSession.isActive() || !buildSession.hasFailures()) return;
        List<Uri> retry = buildSession.takeFailuresForRetry();
        BugLogStore.appendApp("Retrying failed/remaining items: " + retry.size());
        startBatch(retry, true);
    }

    private void startBatch(List<Uri> work, boolean retry) {
        if (!buildSession.isActive() || work.isEmpty()) return;
        buildSession.beginBatch();
        processing = true;
        lastOperationFailed = false;
        lastBugLog = "";
        statusText.setText(retry ? "Повторяю неудачные файлы…"
                : (buildSession.isAnimated() ? "Создаю анимированные стикеры…" : "Создаю стикеры…"));
        prepareFileProgress(work);
        startProgress(work.size(), buildSession.isAnimated());
        updateUiState();
        executor.execute(() -> processBatch(work));
    }

    private void processBatch(List<Uri> work) {
        StickerPackBuilder builder = new StickerPackBuilder(this, buildSession.isAnimated());
        PackBuildCoordinator<Uri> coordinator = new PackBuildCoordinator<>(buildSession);
        PackBuildCoordinator.RunResult<Uri> result = coordinator.run(
                work,
                (itemUri, target, progress) -> {
                    StickerPackBuilder.ItemResult converted = builder.convert(
                            itemUri,
                            target,
                            new StickerPackBuilder.ProgressListener() {
                                @Override
                                public void onStaticProgress(int percent, String detail) {
                                    progress.onStaticProgress(percent, detail);
                                }

                                @Override
                                public void onAnimatedProgress(AnimatedStickerConverter.Progress animatedProgress) {
                                    progress.onAnimatedProgress(animatedProgress);
                                }
                            }
                    );
                    return new PackBuildCoordinator.ItemResult(
                            converted.bytes,
                            converted.fps,
                            converted.quality
                    );
                },
                createBuildCoordinatorListener(work)
        );

        if (activityDestroyed) {
            discardPendingBuild();
            return;
        }
        if (result.isFatal()) {
            handleBatchFatal(result.fatalError, work);
            return;
        }
        if (result.autoFinalize) {
            try {
                finalizePendingPackOnWorker(0);
            } catch (Throwable finalizeError) {
                handleBatchFatal(finalizeError, work);
            }
            return;
        }
        if (result.lastItemError != null) {
            diagnosticItemIndex = result.lastFailureIndex;
            diagnosticItemUri = result.lastFailureItem;
            lastBugLog = buildBugLog(result.lastItemError, buildSession.isAnimated(), work);
            saveBugLog(lastBugLog);
        }
        postUi(() -> showPendingBatchResult(result.cancelled));
    }

    private PackBuildCoordinator.Listener<Uri> createBuildCoordinatorListener(List<Uri> work) {
        return new PackBuildCoordinator.Listener<Uri>() {
            @Override
            public void onItemStarted(int index, int total, Uri itemUri) {
                diagnosticItemIndex = index;
                diagnosticItemUri = itemUri;
                BugLogStore.appendApp("Item " + (index + 1) + "/" + total + ": " + describeUri(itemUri));
                postUi(() -> {
                    updateProgress(index, total,
                            (buildSession.isAnimated() ? "Конвертирую " : "Обрабатываю ")
                                    + (index + 1) + " из " + total + "…");
                    updateFileProgress(index, 0,
                            buildSession.isAnimated() ? "Подготовка к конвертации…" : "Чтение изображения…");
                });
            }

            @Override
            public void onStaticProgress(int index, int percent, String detail) {
                postUi(() -> updateFileProgress(index, percent, detail));
            }

            @Override
            public void onAnimatedProgress(int index, AnimatedStickerConverter.Progress progress) {
                postUi(() -> updateAnimatedFileProgress(index, progress));
            }

            @Override
            public void onItemSucceeded(int index, Uri itemUri, PackBuildCoordinator.ItemResult result) {
                BugLogStore.appendApp("Item converted: bytes=" + result.bytes
                        + (result.animated() ? ", fps=" + result.fps + ", quality=" + result.quality : ""));
                String detail = formatBytes(result.bytes)
                        + (result.animated() ? " · " + result.fps + " FPS · q" + result.quality : "");
                postUi(() -> completeFileProgress(index, detail));
            }

            @Override
            public void onItemFailed(int index, Uri itemUri, Throwable error) {
                String reason = error.getMessage() == null
                        ? "неизвестная ошибка конвертации"
                        : error.getMessage();
                BugLogStore.appendApp("Item failed: " + reason);
                postUi(() -> failFileProgress(index, reason));
            }

            @Override
            public void onItemCompleted(int completed, int total) {
                postUi(() -> updateProgress(
                        completed,
                        total,
                        completed == total
                                ? "Проверены все файлы."
                                : "Проверено " + completed + " из " + total + "."));
            }

            @Override
            public void onCancelledFrom(int startIndex) {
                postUi(() -> markCancelledFrom(startIndex));
            }
        };
    }

    private void handleBatchFatal(Throwable fatalError, List<Uri> work) {
        buildSession.setFailures(new ArrayList<>(work));
        lastBugLog = buildBugLog(fatalError, buildSession.isAnimated(), work);
        saveBugLog(lastBugLog);
        BugLogStore.appendApp("Draft creation failed: " + fatalError);
        discardPendingBuild();

        postUi(() -> {
            processing = false;
            lastOperationFailed = true;
            String message = fatalError.getMessage() == null
                    ? "не удалось создать набор"
                    : fatalError.getMessage();
            statusText.setText("Ошибка: " + message);
            showProgressError();
            updateUiState();
        });
    }

    private void cancelProcessing() {
        if (!processing || buildSession.isCancelRequested()) return;
        buildSession.requestCancel();
        BugLogStore.appendApp("Cancellation requested by user");
        try {
            FFmpegKit.cancel();
        } catch (Throwable error) {
            BugLogStore.appendApp("FFmpeg cancel failed: " + error);
        }
        if (statusText != null) statusText.setText("Останавливаю обработку. Уже готовые стикеры будут сохранены в черновике…");
        if (progressText != null) {
            progressText.setText("Остановка текущей операции…");
            progressText.setVisibility(View.VISIBLE);
        }
        updateUiState();
    }

    private void showPendingBatchResult(boolean cancelled) {
        processing = false;
        lastOperationFailed = true;
        int remaining = buildSession.failureCount();
        int successful = buildSession.successCount();
        if (cancelled) {
            statusText.setText("Обработка остановлена. Готово " + successful
                    + ", осталось " + remaining + ". Можно продолжить с оставшихся файлов.");
            progressText.setText("Остановлено · готово " + successful + " · осталось " + remaining);
        } else if (buildSession.canFinalize()) {
            statusText.setText("Готово " + successful + " стикеров, не удалось " + remaining
                    + ". Повторите ошибки или создайте набор из готовых.");
            progressText.setText("Частичный результат · готово " + successful
                    + " · ошибок " + remaining);
        } else {
            statusText.setText("Готово " + successful + ", не удалось " + remaining
                    + ". Для набора нужно минимум 3 стикера — повторите ошибки.");
            progressText.setText("Нужно ещё " + Math.max(0, MIN_STICKERS - successful)
                    + " успешных стикера.");
        }
        progressText.setVisibility(View.VISIBLE);
        updateUiState();
    }

    private void finalizePendingPackAsync() {
        if (processing || !buildSession.isActive() || !buildSession.canFinalize()) return;
        int skippedCount = buildSession.failureCount();
        processing = true;
        buildSession.beginBatch();
        lastOperationFailed = false;
        statusText.setText("Завершаю набор из " + buildSession.successCount() + " готовых стикеров…");
        if (progressText != null) {
            progressText.setText("Создаю иконку и metadata набора…");
            progressText.setVisibility(View.VISIBLE);
        }
        updateUiState();
        executor.execute(() -> {
            try {
                finalizePendingPackOnWorker(skippedCount);
            } catch (Throwable error) {
                lastBugLog = buildBugLog(error, buildSession.isAnimated(), new ArrayList<>(selectedUris));
                saveBugLog(lastBugLog);
                if (activityDestroyed) {
                    discardPendingBuild();
                    return;
                }
                postUi(() -> {
                    processing = false;
                    lastOperationFailed = true;
                    if (buildSession.isCancelRequested()) {
                        statusText.setText("Завершение набора остановлено. Готовые стикеры остаются в черновике.");
                        progressText.setText("Завершение остановлено.");
                    } else {
                        statusText.setText("Не удалось завершить набор: "
                                + (error.getMessage() == null ? "неизвестная ошибка" : error.getMessage()));
                        showProgressError();
                    }
                    updateUiState();
                });
            }
        });
    }

    private void finalizePendingPackOnWorker(int skippedCount) throws IOException {
        File packDir = buildSession.packDir();
        if (!buildSession.isActive() || packDir == null) throw new IOException("Черновик набора больше недоступен");
        if (!buildSession.canFinalize()) throw new IOException("Для набора нужно минимум 3 готовых стикера");
        if (activityDestroyed || buildSession.isCancelRequested()) throw new IOException("Завершение отменено");

        diagnosticItemIndex = -1;
        diagnosticItemUri = null;
        File tray = new File(packDir, "tray.png");
        Uri traySource = buildSession.traySource();
        if (traySource == null) throw new IOException("Не найден источник для иконки набора");
        new StickerPackBuilder(this, buildSession.isAnimated()).createTrayIcon(traySource, tray);
        if (activityDestroyed || buildSession.isCancelRequested()) {
            //noinspection ResultOfMethodCallIgnored
            tray.delete();
            throw new IOException("Завершение отменено");
        }
        BugLogStore.appendApp("Tray icon created: bytes=" + tray.length());

        int stickerCount = buildSession.successCount();
        PackStore.Pack pack = new PackStore.Pack(
                buildSession.packId(),
                buildSession.packName(),
                stickerCount,
                String.valueOf(System.currentTimeMillis()),
                buildSession.isAnimated()
        );
        if (activityDestroyed || buildSession.isCancelRequested()) throw new IOException("Завершение отменено");
        PackStore.addPack(this, pack);
        currentPack = pack;

        String authority = getPackageName() + ".stickercontentprovider";
        getContentResolver().notifyChange(Uri.parse("content://" + authority + "/metadata"), null);

        int finalFps = buildSession.lastFps();
        int finalQuality = buildSession.lastQuality();
        boolean wasAnimated = buildSession.isAnimated();
        clearPendingBuildState();

        postUi(() -> {
            processing = false;
            lastOperationFailed = false;
            finishProgressSuccess(pack.stickerCount, skippedCount);
            StringBuilder message = new StringBuilder("Готово: «")
                    .append(pack.name).append("» · ").append(pack.stickerCount).append(" стикеров");
            if (skippedCount > 0) message.append(" · пропущено ").append(skippedCount);
            if (wasAnimated && finalFps > 0) {
                message.append(" · последний профиль ").append(finalFps)
                        .append(" FPS, q").append(finalQuality);
            }
            statusText.setText(message.append('.').toString());
            updateUiState();
        });
    }

    private void startProgress(int total, boolean animated) {
        if (progressBar != null) {
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
        }
        if (progressText != null) {
            progressText.setText(animated
                    ? "Запускаю обработку анимированных стикеров…"
                    : "Запускаю обработку стикеров…");
            progressText.setVisibility(View.VISIBLE);
        }
        if (bugLogButton != null) bugLogButton.setVisibility(View.GONE);
    }

    private void updateProgress(int completed, int total, String message) {
        if (progressBar != null) {
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(Math.max(0, Math.min(completed, total)));
            progressBar.setVisibility(View.VISIBLE);
        }
        if (progressText != null) {
            progressText.setText(message);
            progressText.setVisibility(View.VISIBLE);
        }
    }

    private void prepareFileProgress(List<Uri> work) {
        fileProgressLabels.clear();
        fileProgressBars.clear();
        fileProgressNames.clear();
        if (fileProgressContainer == null) return;
        fileProgressContainer.removeAllViews();
        fileProgressContainer.setVisibility(View.VISIBLE);

        for (int i = 0; i < work.size(); i++) {
            String name = getDisplayName(work.get(i), i + 1);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));
            fileProgressContainer.addView(row, matchWrap());

            TextView label = text((i + 1) + ". " + name + " · В очереди", 12, MUTED, Typeface.NORMAL);
            label.setMaxLines(3);
            row.addView(label, matchWrap());

            ProgressBar itemBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            itemBar.setMax(100);
            itemBar.setProgress(0);
            LinearLayout.LayoutParams itemBarParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
            itemBarParams.topMargin = dp(5);
            row.addView(itemBar, itemBarParams);

            fileProgressNames.add(name);
            fileProgressLabels.add(label);
            fileProgressBars.add(itemBar);
        }
    }

    private void updateAnimatedFileProgress(int index, AnimatedStickerConverter.Progress progress) {
        String detail;
        switch (progress.stage) {
            case PREPARING: detail = "Подготовка файла"; break;
            case INSPECTING: detail = "Проверка WebP"; break;
            case PASSTHROUGH: detail = "Файл уже подходит WhatsApp"; break;
            case RESIZING: detail = "Изменение размера animated WebP"; break;
            case CHECKING:
                detail = "Проверка размера · " + formatBytes(progress.candidateBytes)
                        + " · " + progress.fps + " FPS · q" + progress.quality;
                break;
            case DONE: detail = "Завершение"; break;
            case ENCODING:
            default:
                detail = "Кодирование · попытка " + progress.attempt + "/" + progress.totalAttempts
                        + " · " + progress.fps + " FPS · q" + progress.quality;
                break;
        }
        updateFileProgress(index, progress.percent, detail);
    }

    private String progressName(int index) {
        if (index >= 0 && index < fileProgressNames.size()) return fileProgressNames.get(index);
        return "Файл " + (index + 1);
    }

    private void updateFileProgress(int index, int percent, String detail) {
        if (index < 0 || index >= fileProgressLabels.size() || index >= fileProgressBars.size()) return;
        int safePercent = Math.max(0, Math.min(100, percent));
        TextView label = fileProgressLabels.get(index);
        ProgressBar itemBar = fileProgressBars.get(index);
        itemBar.setProgress(safePercent);
        label.setText((index + 1) + ". " + progressName(index)
                + " · " + safePercent + "%\n" + detail);
        label.setTextColor(TEXT);
    }

    private void completeFileProgress(int index, String detail) {
        if (index < 0 || index >= fileProgressLabels.size() || index >= fileProgressBars.size()) return;
        ProgressBar itemBar = fileProgressBars.get(index);
        TextView label = fileProgressLabels.get(index);
        itemBar.setProgress(100);
        label.setText((index + 1) + ". " + progressName(index) + " · Готово\n" + detail);
        label.setTextColor(PRIMARY);
    }

    private void failFileProgress(int index, String reason) {
        if (index < 0 || index >= fileProgressLabels.size()) return;
        TextView label = fileProgressLabels.get(index);
        label.setText((index + 1) + ". " + progressName(index) + " · Ошибка\n" + reason);
        label.setTextColor(ERROR);
    }

    private void markCancelledFrom(int startIndex) {
        for (int i = Math.max(0, startIndex); i < fileProgressLabels.size(); i++) {
            TextView label = fileProgressLabels.get(i);
            label.setText((i + 1) + ". " + progressName(i) + " · Не обработано\nОстановлено пользователем");
            label.setTextColor(MUTED);
        }
    }

    private String getDisplayName(Uri uri, int fallbackNumber) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    String value = cursor.getString(nameIndex);
                    if (value != null && !value.trim().isEmpty()) return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return (animatedMode ? "Анимация " : "Фото ") + fallbackNumber;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return Math.round(bytes / 1024f) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private void finishProgressSuccess(int total, int skippedCount) {
        if (progressBar != null) {
            progressBar.setMax(Math.max(1, total));
            progressBar.setProgress(Math.max(1, total));
            progressBar.setVisibility(View.VISIBLE);
        }
        if (progressText != null) {
            progressText.setText(skippedCount > 0
                    ? "Набор создан из " + total + " готовых · пропущено " + skippedCount
                    : "Готово. Все стикеры обработаны.");
            progressText.setVisibility(View.VISIBLE);
        }
        if (bugLogButton != null) bugLogButton.setVisibility(View.GONE);
    }

    private void showProgressError() {
        if (progressText != null) {
            progressText.setText("Создание набора завершилось ошибкой. Откройте баг-лог ниже.");
            progressText.setVisibility(View.VISIBLE);
        }
        if (bugLogButton != null) bugLogButton.setVisibility(View.VISIBLE);
    }

    private void hideDiagnosticsUi() {
        if (progressText != null) progressText.setVisibility(View.GONE);
        if (progressBar != null) {
            progressBar.setProgress(0);
            progressBar.setVisibility(View.GONE);
        }
        fileProgressLabels.clear();
        fileProgressBars.clear();
        fileProgressNames.clear();
        if (fileProgressContainer != null) {
            fileProgressContainer.removeAllViews();
            fileProgressContainer.setVisibility(View.GONE);
        }
        if (bugLogButton != null) bugLogButton.setVisibility(View.GONE);
    }

    private String buildBugLog(Throwable error, boolean makeAnimated, List<Uri> work) {
        StringBuilder report = new StringBuilder();
        report.append("WA Stickers bug log\n");
        report.append("Time: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()))
                .append('\n');
        report.append("App: ").append(BuildConfig.VERSION_NAME)
                .append(" (versionCode ").append(BuildConfig.VERSION_CODE).append(")\n");
        report.append("Android SDK: ").append(Build.VERSION.SDK_INT).append('\n');
        report.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        report.append("Mode: ").append(makeAnimated ? "animated" : "static").append('\n');
        report.append("Selected files: ").append(work.size()).append('\n');
        report.append("Successful in draft: ").append(buildSession.successCount()).append('\n');
        report.append("Waiting for retry: ").append(buildSession.failureCount()).append('\n');

        if (diagnosticItemIndex >= 0) report.append("Failed item: ").append(diagnosticItemIndex + 1).append(" / ").append(work.size()).append('\n');
        if (diagnosticItemUri != null) report.append("Failed file: ").append(describeUri(diagnosticItemUri)).append('\n');

        report.append("\nFiles:\n");
        for (int i = 0; i < work.size(); i++) report.append(i + 1).append(". ").append(describeUri(work.get(i))).append('\n');

        report.append("\nException:\n").append(stackTrace(error));
        String ffmpeg = BugLogStore.snapshot();
        report.append("\nFFmpeg / app log:\n");
        report.append(ffmpeg.isEmpty() ? "<no FFmpeg log captured>\n" : ffmpeg);
        return report.toString();
    }

    private String describeUri(Uri uri) {
        if (uri == null) return "<null>";
        String displayName = "unknown";
        long size = -1;
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex);
            }
        } catch (Throwable ignored) {
        }

        String mime = null;
        try {
            mime = getContentResolver().getType(uri);
        } catch (Throwable ignored) {
        }
        return displayName + " | mime=" + (mime == null ? "unknown" : mime)
                + " | bytes=" + (size < 0 ? "unknown" : size);
    }

    private String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        error.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    private void saveBugLog(String log) {
        if (log == null || log.isEmpty()) return;
        File dir = new File(getFilesDir(), "buglogs");
        if (!dir.mkdirs() && !dir.isDirectory()) return;
        File file = new File(dir, "last_bug_log.txt");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(log.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private void showBugLogDialog() {
        if (lastBugLog == null || lastBugLog.isEmpty()) {
            Toast.makeText(this, "Баг-лог пока отсутствует", Toast.LENGTH_SHORT).show();
            return;
        }

        String preview = lastBugLog.length() > 8000
                ? "…\n" + lastBugLog.substring(lastBugLog.length() - 8000)
                : lastBugLog;

        new AlertDialog.Builder(this)
                .setTitle("Баг-лог")
                .setMessage(preview)
                .setPositiveButton("Копировать", (dialog, which) -> copyBugLog())
                .setNeutralButton("Поделиться", (dialog, which) -> shareBugLog())
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void copyBugLog() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WA Stickers bug log", lastBugLog));
            Toast.makeText(this, "Баг-лог скопирован", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareBugLog() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "WA Stickers bug log");
        share.putExtra(Intent.EXTRA_TEXT, lastBugLog);
        try {
            startActivity(Intent.createChooser(share, "Поделиться баг-логом"));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Не найдено приложение для отправки", Toast.LENGTH_SHORT).show();
        }
    }

    private void addCurrentPackToWhatsApp() {
        if (currentPack == null) {
            Toast.makeText(this, "Сначала успешно создайте новый набор", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPack.animated != animatedMode) {
            currentPack = null;
            updateUiState();
            Toast.makeText(this, "Текущий набор устарел. Создайте набор заново.", Toast.LENGTH_LONG).show();
            return;
        }
        File firstSticker = PackStore.getStickerFile(this, currentPack.id, "1.webp");
        if (!firstSticker.isFile()) {
            currentPack = null;
            updateUiState();
            Toast.makeText(this, "Файлы набора не найдены. Создайте набор заново.", Toast.LENGTH_LONG).show();
            return;
        }
        String authority = getPackageName() + ".stickercontentprovider";
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", currentPack.id);
        intent.putExtra("sticker_pack_authority", authority);
        intent.putExtra("sticker_pack_name", currentPack.name);
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "WhatsApp не найден на телефоне", Toast.LENGTH_LONG).show();
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void postUi(Runnable action) {
        if (action == null || activityDestroyed) return;
        runOnUiThread(() -> {
            if (activityDestroyed || isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
            action.run();
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        buildSession.requestCancel();
        try {
            FFmpegKit.cancel();
        } catch (Throwable ignored) {
        }
        if (previewLoader != null) previewLoader.close();
        if (!processing) discardPendingBuild();
        super.onDestroy();
        executor.shutdownNow();
    }
}
