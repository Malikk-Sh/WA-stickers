package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
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

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;
    private static final int MAX_STICKERS = 30;
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKER_BYTES = 100 * 1024;

    private static final int BG = 0xFFF6F9F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF11241E;
    private static final int MUTED = 0xFF687871;
    private static final int PRIMARY = 0xFF075E54;
    private static final int TEAL = 0xFF128C7E;
    private static final int GREEN = 0xFF25D366;
    private static final int PALE_GREEN = 0xFFE7F8EE;
    private static final int SOFT = 0xFFF0F5F2;
    private static final int BORDER = 0xFFDDE7E2;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<PickVisualMediaRequest> photoPickerFallback;
    private EditText packName;
    private TextView selectionStatus;
    private TextView processStatus;
    private LinearLayout previewContainer;
    private ProgressBar selectionProgress;
    private Button pickButton;
    private Button createButton;
    private Button addButton;
    private PackStore.Pack currentPack;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        receiveGalleryResult(result.getData());
                    }
                }
        );

        photoPickerFallback = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_STICKERS),
                this::mergeSelectedImages
        );

        setContentView(buildUi());

        currentPack = PackStore.getLatestPack(this);
        if (currentPack != null) {
            processStatus.setText("Последний набор «" + currentPack.name + "» готов к добавлению в WhatsApp.");
        }
        updateSelectionUi();
        updateActionButtons();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                );
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.setBackground(gradientRounded(PRIMARY, TEAL, 26));
        hero.setElevation(dp(4));
        root.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setOrientation(LinearLayout.HORIZONTAL);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(heroTop);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(10), dp(10), dp(10), dp(10));
        logo.setBackground(rounded(0x26FFFFFF, 18));
        heroTop.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingParams.leftMargin = dp(14);
        heroTop.addView(heading, headingParams);

        TextView brand = text("WA Stickers", 13, 0xFFD9FFF0, Typeface.BOLD);
        heading.addView(brand);
        TextView title = text("Стикеры из ваших фото", 24, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(2);
        heading.addView(title, titleParams);

        TextView heroText = text(
                "Выберите фотографии, создайте набор и добавьте его в WhatsApp — всё прямо на телефоне.",
                14, 0xFFE5F5F0, Typeface.NORMAL);
        heroText.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams heroTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroTextParams.topMargin = dp(16);
        hero.addView(heroText, heroTextParams);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.HORIZONTAL);
        steps.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsParams.topMargin = dp(16);
        hero.addView(steps, stepsParams);
        addStep(steps, "1", "Фото");
        addStepDivider(steps);
        addStep(steps, "2", "Набор");
        addStepDivider(steps);
        addStep(steps, "3", "WhatsApp");

        LinearLayout nameCard = makeCard();
        LinearLayout.LayoutParams nameCardParams = cardParams();
        nameCardParams.topMargin = dp(16);
        root.addView(nameCard, nameCardParams);

        TextView nameEyebrow = text("ШАГ 1", 11, TEAL, Typeface.BOLD);
        nameCard.addView(nameEyebrow);
        TextView nameTitle = sectionTitle("Название набора");
        LinearLayout.LayoutParams nameTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameTitleParams.topMargin = dp(4);
        nameCard.addView(nameTitle, nameTitleParams);
        TextView nameHint = text("Оно будет видно в списке стикеров WhatsApp.", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams nameHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameHintParams.topMargin = dp(4);
        nameCard.addView(nameHint, nameHintParams);

        packName = new EditText(this);
        packName.setHint("Например: Отпуск 2026");
        packName.setHintTextColor(0xFF9AA7A1);
        packName.setTextColor(TEXT);
        packName.setTextSize(16);
        packName.setSingleLine(true);
        packName.setPadding(dp(15), 0, dp(15), 0);
        packName.setBackground(inputBackground());
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        fieldParams.topMargin = dp(14);
        nameCard.addView(packName, fieldParams);

        LinearLayout photosCard = makeCard();
        LinearLayout.LayoutParams photosCardParams = cardParams();
        photosCardParams.topMargin = dp(12);
        root.addView(photosCard, photosCardParams);

        LinearLayout photosHeader = new LinearLayout(this);
        photosHeader.setOrientation(LinearLayout.HORIZONTAL);
        photosHeader.setGravity(Gravity.CENTER_VERTICAL);
        photosCard.addView(photosHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout photosHeading = new LinearLayout(this);
        photosHeading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams photosHeadingParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        photosHeader.addView(photosHeading, photosHeadingParams);
        photosHeading.addView(text("ШАГ 2", 11, TEAL, Typeface.BOLD));
        TextView photosTitle = sectionTitle("Выберите фотографии");
        LinearLayout.LayoutParams photosTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        photosTitleParams.topMargin = dp(4);
        photosHeading.addView(photosTitle, photosTitleParams);

        selectionStatus = text("0 / 30", 12, PRIMARY, Typeface.BOLD);
        selectionStatus.setGravity(Gravity.CENTER);
        selectionStatus.setPadding(dp(11), 0, dp(11), 0);
        selectionStatus.setBackground(rounded(PALE_GREEN, 14));
        photosHeader.addView(selectionStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        TextView photosHint = text(
                "Нужно от 3 до 30 фото. Нажмите кнопку — откроется галерея телефона.",
                13, MUTED, Typeface.NORMAL);
        photosHint.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams photosHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        photosHintParams.topMargin = dp(7);
        photosCard.addView(photosHint, photosHintParams);

        LinearLayout galleryPanel = new LinearLayout(this);
        galleryPanel.setOrientation(LinearLayout.VERTICAL);
        galleryPanel.setGravity(Gravity.CENTER);
        galleryPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable galleryBg = rounded(0xFFF8FBF9, 18);
        galleryBg.setStroke(dp(1), 0xFFBFD5CB, dp(6), dp(5));
        galleryPanel.setBackground(galleryBg);
        LinearLayout.LayoutParams galleryPanelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        galleryPanelParams.topMargin = dp(16);
        photosCard.addView(galleryPanel, galleryPanelParams);

        TextView galleryGlyph = text("＋", 30, PRIMARY, Typeface.NORMAL);
        galleryGlyph.setGravity(Gravity.CENTER);
        galleryGlyph.setBackground(rounded(PALE_GREEN, 22));
        galleryPanel.addView(galleryGlyph, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView galleryTitle = text("Добавьте фото из галереи", 15, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams galleryTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        galleryTitleParams.topMargin = dp(10);
        galleryPanel.addView(galleryTitle, galleryTitleParams);

        TextView gallerySub = text("JPG, PNG, HEIC и другие форматы Android", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams gallerySubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gallerySubParams.topMargin = dp(3);
        galleryPanel.addView(gallerySub, gallerySubParams);

        pickButton = new Button(this);
        pickButton.setText("Открыть галерею");
        styleButton(pickButton, PRIMARY, Color.WHITE);
        pickButton.setOnClickListener(v -> openGallery());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        pickParams.topMargin = dp(14);
        galleryPanel.addView(pickButton, pickParams);

        selectionProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        selectionProgress.setMax(MAX_STICKERS);
        selectionProgress.setProgress(0);
        selectionProgress.setProgressTintList(ColorStateList.valueOf(GREEN));
        selectionProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE8EFEB));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
        progressParams.topMargin = dp(14);
        photosCard.addView(selectionProgress, progressParams);

        TextView previewHint = text("Миниатюры выбранных фото · нажмите ×, чтобы удалить", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams previewHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewHintParams.topMargin = dp(14);
        photosCard.addView(previewHint, previewHintParams);

        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.setHorizontalScrollBarEnabled(false);
        previewScroll.setClipToPadding(false);
        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.HORIZONTAL);
        previewContainer.setGravity(Gravity.CENTER_VERTICAL);
        previewScroll.addView(previewContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(116));
        previewParams.topMargin = dp(8);
        photosCard.addView(previewScroll, previewParams);
        renderPreviews();

        LinearLayout actionsCard = makeCard();
        LinearLayout.LayoutParams actionsParams = cardParams();
        actionsParams.topMargin = dp(12);
        root.addView(actionsCard, actionsParams);

        actionsCard.addView(text("ШАГ 3", 11, TEAL, Typeface.BOLD));
        TextView actionsTitle = sectionTitle("Создайте и добавьте в WhatsApp");
        LinearLayout.LayoutParams actionsTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsTitleParams.topMargin = dp(4);
        actionsCard.addView(actionsTitle, actionsTitleParams);
        TextView actionHint = text(
                "Каждое фото помещается целиком в 512×512 без удаления фона и без белой обводки.",
                13, MUTED, Typeface.NORMAL);
        actionHint.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams actionHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionHintParams.topMargin = dp(6);
        actionsCard.addView(actionHint, actionHintParams);

        createButton = new Button(this);
        createButton.setText("Создать набор");
        styleButton(createButton, GREEN, 0xFF073B2B);
        createButton.setOnClickListener(v -> createPack());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        createParams.topMargin = dp(16);
        actionsCard.addView(createButton, createParams);

        addButton = new Button(this);
        addButton.setText("Добавить в WhatsApp");
        styleButton(addButton, PRIMARY, Color.WHITE);
        addButton.setOnClickListener(v -> addCurrentPackToWhatsApp());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        addParams.topMargin = dp(10);
        actionsCard.addView(addButton, addParams);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.TOP);
        statusCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        statusCard.setBackground(rounded(SOFT, 18));
        LinearLayout.LayoutParams statusCardParams = cardParams();
        statusCardParams.topMargin = dp(12);
        root.addView(statusCard, statusCardParams);

        TextView statusDot = text("●", 12, GREEN, Typeface.NORMAL);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotParams.topMargin = dp(1);
        statusCard.addView(statusDot, dotParams);

        processStatus = text("Выберите минимум 3 фотографии, чтобы создать набор.", 13, MUTED, Typeface.NORMAL);
        processStatus.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams processParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        processParams.leftMargin = dp(10);
        statusCard.addView(processStatus, processParams);

        TextView privacy = text(
                "Все фотографии обрабатываются только на вашем телефоне и никуда не загружаются.",
                12, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(18);
        root.addView(privacy, privacyParams);

        return scroll;
    }

    private void addStep(LinearLayout parent, String number, String label) {
        LinearLayout step = new LinearLayout(this);
        step.setOrientation(LinearLayout.HORIZONTAL);
        step.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(number, 11, PRIMARY, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(0xFFFFFFFF, 12));
        step.addView(badge, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView text = text(label, 12, 0xFFF2FFFA, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = dp(6);
        step.addView(text, textParams);
        parent.addView(step);
    }

    private void addStepDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(1), 1f);
        params.leftMargin = dp(8);
        params.rightMargin = dp(8);
        parent.addView(divider, params);
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, 22));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView sectionTitle(String value) {
        return text(value, 18, TEXT, Typeface.BOLD);
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

    private GradientDrawable gradientRounded(int startColor, int endColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = rounded(0xFFFAFCFB, 14);
        drawable.setStroke(dp(1), BORDER);
        return drawable;
    }

    private void styleButton(Button button, int fillColor, int textColor) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(textColor);
        button.setTypeface(Typeface.create("sans", Typeface.BOLD));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setStateListAnimator(null);

        GradientDrawable content = rounded(fillColor, 15);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x22000000), content, null);
        button.setBackground(ripple);
    }

    private void openGallery() {
        Intent gallery = new Intent(Intent.ACTION_PICK);
        gallery.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        gallery.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        gallery.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            galleryLauncher.launch(gallery);
        } catch (ActivityNotFoundException e) {
            openPhotoPickerFallback();
        }
    }

    private void openPhotoPickerFallback() {
        photoPickerFallback.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void receiveGalleryResult(Intent data) {
        List<Uri> uris = new ArrayList<>();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount() && uris.size() < MAX_STICKERS; i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        mergeSelectedImages(uris);
    }

    private void mergeSelectedImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        for (Uri uri : uris) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            if (selectedUris.contains(uri)) continue;
            selectedUris.add(uri);
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Gallery and Photo Picker grants are sufficient for immediate conversion.
            }
        }

        renderPreviews();
        updateSelectionUi();
        updateActionButtons();
    }

    private void updateSelectionUi() {
        if (selectionStatus != null) {
            selectionStatus.setText(selectedUris.size() + " / " + MAX_STICKERS);
        }
        if (selectionProgress != null) {
            selectionProgress.setProgress(selectedUris.size());
        }
        if (pickButton != null) {
            pickButton.setText(selectedUris.isEmpty() ? "Открыть галерею" : "Добавить ещё фото");
        }
        if (processStatus != null && !isProcessing) {
            if (selectedUris.isEmpty()) {
                processStatus.setText("Выберите минимум 3 фотографии, чтобы создать набор.");
            } else if (selectedUris.size() < MIN_STICKERS) {
                processStatus.setText("Добавьте ещё " + (MIN_STICKERS - selectedUris.size()) + " фото.");
            } else {
                processStatus.setText("Фото выбраны. Можно создавать набор.");
            }
        }
    }

    private void renderPreviews() {
        if (previewContainer == null) return;
        previewContainer.removeAllViews();

        if (selectedUris.isEmpty()) {
            TextView empty = text("Здесь появятся выбранные фотографии", 13, MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(rounded(SOFT, 14));
            empty.setPadding(dp(18), 0, dp(18), 0);
            previewContainer.addView(empty, new LinearLayout.LayoutParams(dp(270), dp(94)));
            return;
        }

        for (int i = 0; i < selectedUris.size(); i++) {
            final int index = i;
            Uri uri = selectedUris.get(i);

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout imageFrame = new FrameLayout(this);
            cell.addView(imageFrame, new LinearLayout.LayoutParams(dp(92), dp(92)));

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(0xFFE8EFEB, 16));
            image.setClipToOutline(true);
            try {
                image.setImageBitmap(decodeSampled(uri, 240));
            } catch (IOException ignored) {
            }
            imageFrame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            TextView remove = text("×", 18, Color.WHITE, Typeface.BOLD);
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(rounded(0xCC1A2722, 12));
            remove.setOnClickListener(v -> removeSelectedImage(index));
            FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(26), dp(26));
            removeParams.gravity = Gravity.TOP | Gravity.END;
            removeParams.topMargin = dp(4);
            removeParams.rightMargin = dp(4);
            imageFrame.addView(remove, removeParams);

            TextView number = text(String.valueOf(i + 1), 11, MUTED, Typeface.BOLD);
            number.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.topMargin = dp(3);
            cell.addView(number, numberParams);

            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(96), dp(112));
            cellParams.rightMargin = dp(8);
            previewContainer.addView(cell, cellParams);
        }
    }

    private void removeSelectedImage(int index) {
        if (index < 0 || index >= selectedUris.size() || isProcessing) return;
        selectedUris.remove(index);
        renderPreviews();
        updateSelectionUi();
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (createButton == null || addButton == null || pickButton == null) return;

        boolean enoughPhotos = selectedUris.size() >= MIN_STICKERS && selectedUris.size() <= MAX_STICKERS;
        createButton.setEnabled(enoughPhotos && !isProcessing);
        addButton.setEnabled(currentPack != null && !isProcessing);
        pickButton.setEnabled(!isProcessing && selectedUris.size() < MAX_STICKERS);

        createButton.setAlpha(createButton.isEnabled() ? 1f : 0.42f);
        addButton.setAlpha(addButton.isEnabled() ? 1f : 0.42f);
        pickButton.setAlpha(pickButton.isEnabled() ? 1f : 0.58f);
    }

    private void createPack() {
        if (selectedUris.size() < MIN_STICKERS || selectedUris.size() > MAX_STICKERS) {
            Toast.makeText(this, "Нужно выбрать от 3 до 30 фото", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = packName.getText().toString().trim();
        if (name.isEmpty()) name = "Мои стикеры";
        final String finalName = name;

        isProcessing = true;
        updateActionButtons();
        processStatus.setText("Создаю стикеры…");
        List<Uri> work = new ArrayList<>(selectedUris);

        executor.execute(() -> {
            String id = "pack_" + System.currentTimeMillis();
            File packDir = PackStore.getPackDir(this, id);
            try {
                if (!packDir.mkdirs() && !packDir.isDirectory()) {
                    throw new IOException("Не удалось создать папку набора");
                }

                for (int i = 0; i < work.size(); i++) {
                    Bitmap sticker = makeSticker(work.get(i));
                    File target = new File(packDir, (i + 1) + ".webp");
                    writeWebpUnderLimit(sticker, target);
                    sticker.recycle();
                    final int completed = i + 1;
                    runOnUiThread(() -> processStatus.setText(
                            "Обработано " + completed + " из " + work.size() + "…"));
                }

                createTrayIcon(new File(packDir, "1.webp"), new File(packDir, "tray.png"));
                PackStore.Pack pack = new PackStore.Pack(
                        id, finalName, work.size(), String.valueOf(System.currentTimeMillis()));
                PackStore.addPack(this, pack);

                String authority = getPackageName() + ".stickercontentprovider";
                getContentResolver().notifyChange(Uri.parse("content://" + authority + "/metadata"), null);

                currentPack = pack;
                runOnUiThread(() -> {
                    isProcessing = false;
                    updateActionButtons();
                    processStatus.setText("Готово: «" + pack.name + "». Нажмите «Добавить в WhatsApp».");
                });
            } catch (Exception e) {
                deleteRecursively(packDir);
                runOnUiThread(() -> {
                    isProcessing = false;
                    updateActionButtons();
                    processStatus.setText("Ошибка: " + (e.getMessage() == null
                            ? "не удалось создать набор" : e.getMessage()));
                });
            }
        });
    }

    private Bitmap makeSticker(Uri uri) throws IOException {
        Bitmap source = decodeSampled(uri, 1600);
        if (source == null) throw new IOException("Не удалось прочитать изображение");

        Bitmap output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.TRANSPARENT);

        float scale = Math.min(512f / source.getWidth(), 512f / source.getHeight());
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (512f - width) / 2f;
        float top = (512f - height) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);
        source.recycle();
        return output;
    }

    private Bitmap decodeSampled(Uri uri, int maxSide) throws IOException {
        ContentResolver resolver = getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Файл недоступен");
            BitmapFactory.decodeStream(input, null, bounds);
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > maxSide * 2) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Файл недоступен");
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Неподдерживаемое изображение");
            return bitmap;
        }
    }

    private void writeWebpUnderLimit(Bitmap bitmap, File target) throws IOException {
        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= 30
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;

        byte[] best = null;
        for (int quality = 92; quality >= 10; quality -= 6) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!bitmap.compress(format, quality, bytes)) {
                throw new IOException("Ошибка конвертации WebP");
            }
            best = bytes.toByteArray();
            if (best.length <= MAX_STICKER_BYTES) break;
        }
        if (best == null || best.length > MAX_STICKER_BYTES) {
            throw new IOException("Стикер не удалось сжать до 100 КБ");
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(best);
        }
    }

    private void createTrayIcon(File stickerFile, File trayFile) throws IOException {
        Bitmap source = BitmapFactory.decodeFile(stickerFile.getAbsolutePath());
        if (source == null) throw new IOException("Не удалось создать иконку набора");
        Bitmap icon = Bitmap.createScaledBitmap(source, 96, 96, true);
        try (FileOutputStream output = new FileOutputStream(trayFile)) {
            if (!icon.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Не удалось сохранить иконку набора");
            }
        }
        source.recycle();
        if (icon != source) icon.recycle();
        if (trayFile.length() > 50 * 1024) {
            throw new IOException("Иконка набора превышает 50 КБ");
        }
    }

    private void addCurrentPackToWhatsApp() {
        if (currentPack == null) return;
        String authority = getPackageName() + ".stickercontentprovider";
        Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        intent.putExtra("sticker_pack_id", currentPack.id);
        intent.putExtra("sticker_pack_authority", authority);
        intent.putExtra("sticker_pack_name", currentPack.name);
        try {
            startActivityForResult(intent, REQUEST_ADD_TO_WHATSAPP);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "WhatsApp не найден на телефоне", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_TO_WHATSAPP && resultCode == RESULT_OK) {
            processStatus.setText("Набор успешно добавлен в WhatsApp.");
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
