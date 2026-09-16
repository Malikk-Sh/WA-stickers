package com.malikksh.wastickers;

import android.content.ActivityNotFoundException;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    private static final int BG = 0xFFF4F7F6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF18201E;
    private static final int MUTED = 0xFF66736F;
    private static final int TEAL = 0xFF128C7E;
    private static final int DARK_TEAL = 0xFF075E54;
    private static final int GREEN = 0xFF25D366;
    private static final int SOFT_GREEN = 0xFFE7F8EE;
    private static final int BORDER = 0xFFE1E8E5;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;
    private EditText packName;
    private TextView selectionStatus;
    private TextView processStatus;
    private LinearLayout previewContainer;
    private Button pickButton;
    private Button createButton;
    private Button addButton;
    private PackStore.Pack currentPack;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();

        photoPicker = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_STICKERS),
                this::receiveSelectedImages
        );

        setContentView(buildUi());

        currentPack = PackStore.getLatestPack(this);
        if (currentPack != null) {
            processStatus.setText("Последний набор «" + currentPack.name + "» готов к добавлению в WhatsApp.");
        }
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
        root.setPadding(dp(20), dp(24), dp(20), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_app_logo_mark);
        logo.setPadding(dp(10), dp(10), dp(10), dp(10));
        logo.setBackground(rounded(TEAL, 18));
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        header.addView(logo, logoParams);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingParams.leftMargin = dp(14);
        header.addView(heading, headingParams);

        TextView title = text("WA Stickers", 26, TEXT, Typeface.BOLD);
        heading.addView(title);
        TextView tagline = text("Свои фото → набор для WhatsApp", 14, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams taglineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        taglineParams.topMargin = dp(2);
        heading.addView(tagline, taglineParams);

        root.addView(header);

        TextView intro = text(
                "Выберите от 3 до 30 фотографий. Мы оставим изображение как есть: без удаления фона и без белой обводки.",
                15, MUTED, Typeface.NORMAL);
        intro.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        introParams.topMargin = dp(16);
        root.addView(intro, introParams);

        LinearLayout setupCard = makeCard();
        LinearLayout.LayoutParams cardParams = cardParams();
        cardParams.topMargin = dp(20);
        root.addView(setupCard, cardParams);

        setupCard.addView(sectionTitle("Название набора"));
        TextView nameHint = text("Так набор будет называться в WhatsApp", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams nameHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameHintParams.topMargin = dp(3);
        setupCard.addView(nameHint, nameHintParams);

        packName = new EditText(this);
        packName.setHint("Например: Мои стикеры");
        packName.setHintTextColor(0xFF9AA5A1);
        packName.setTextColor(TEXT);
        packName.setTextSize(16);
        packName.setSingleLine(true);
        packName.setPadding(dp(14), 0, dp(14), 0);
        packName.setBackground(inputBackground());
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        fieldParams.topMargin = dp(12);
        setupCard.addView(packName, fieldParams);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(22);
        dividerParams.bottomMargin = dp(20);
        setupCard.addView(divider, dividerParams);

        setupCard.addView(sectionTitle("Фотографии"));
        TextView photosHint = text("Откроется системная галерея. Можно выбрать сразу несколько фото.", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams photosHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        photosHintParams.topMargin = dp(3);
        setupCard.addView(photosHint, photosHintParams);

        pickButton = new Button(this);
        pickButton.setText("Выбрать фото из галереи");
        styleButton(pickButton, TEAL, Color.WHITE);
        pickButton.setOnClickListener(v -> openPhotoPicker());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        pickParams.topMargin = dp(14);
        setupCard.addView(pickButton, pickParams);

        selectionStatus = text("Фото пока не выбраны", 13, DARK_TEAL, Typeface.BOLD);
        selectionStatus.setGravity(Gravity.CENTER_VERTICAL);
        selectionStatus.setPadding(dp(12), 0, dp(12), 0);
        selectionStatus.setBackground(rounded(SOFT_GREEN, 12));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        statusParams.topMargin = dp(12);
        setupCard.addView(selectionStatus, statusParams);

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
                ViewGroup.LayoutParams.MATCH_PARENT, dp(118));
        previewParams.topMargin = dp(10);
        setupCard.addView(previewScroll, previewParams);
        renderPreviews();

        LinearLayout actionsCard = makeCard();
        LinearLayout.LayoutParams actionsParams = cardParams();
        actionsParams.topMargin = dp(14);
        root.addView(actionsCard, actionsParams);

        actionsCard.addView(sectionTitle("Готово к созданию"));
        TextView actionHint = text(
                "Фото будут помещены целиком в 512×512 и автоматически сжаты под требования WhatsApp.",
                13, MUTED, Typeface.NORMAL);
        actionHint.setLineSpacing(0, 1.06f);
        LinearLayout.LayoutParams actionHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionHintParams.topMargin = dp(4);
        actionsCard.addView(actionHint, actionHintParams);

        createButton = new Button(this);
        createButton.setText("Создать набор стикеров");
        styleButton(createButton, GREEN, 0xFF073B2B);
        createButton.setOnClickListener(v -> createPack());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        createParams.topMargin = dp(16);
        actionsCard.addView(createButton, createParams);

        addButton = new Button(this);
        addButton.setText("Добавить в WhatsApp");
        styleButton(addButton, DARK_TEAL, Color.WHITE);
        addButton.setOnClickListener(v -> addCurrentPackToWhatsApp());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        addParams.topMargin = dp(10);
        actionsCard.addView(addButton, addParams);

        LinearLayout statusCard = makeCard();
        LinearLayout.LayoutParams statusCardParams = cardParams();
        statusCardParams.topMargin = dp(14);
        root.addView(statusCard, statusCardParams);

        TextView statusTitle = text("Статус", 14, TEXT, Typeface.BOLD);
        statusCard.addView(statusTitle);
        processStatus = text("Выберите минимум 3 фотографии, чтобы создать набор.", 14, MUTED, Typeface.NORMAL);
        processStatus.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams processParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        processParams.topMargin = dp(7);
        statusCard.addView(processStatus, processParams);

        TextView privacy = text(
                "🔒 Все фотографии обрабатываются только на вашем телефоне и никуда не загружаются.",
                12, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(20);
        root.addView(privacy, privacyParams);

        return scroll;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, 20));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView sectionTitle(String value) {
        return text(value, 17, TEXT, Typeface.BOLD);
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

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = rounded(0xFFF9FBFA, 14);
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

        GradientDrawable content = rounded(fillColor, 15);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x22000000), content, null);
        button.setBackground(ripple);
    }

    private void openPhotoPicker() {
        photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void receiveSelectedImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;

        selectedUris.clear();
        for (Uri uri : uris) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            selectedUris.add(uri);
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Photo Picker grants access even when a persistable grant is unavailable.
            }
        }

        selectionStatus.setText("Выбрано: " + selectedUris.size() + " из " + MAX_STICKERS);
        renderPreviews();
        if (selectedUris.size() < MIN_STICKERS) {
            processStatus.setText("Нужно выбрать ещё " + (MIN_STICKERS - selectedUris.size()) + " фото.");
        } else {
            processStatus.setText("Фото выбраны. Можно создавать набор.");
        }
        updateActionButtons();
    }

    private void renderPreviews() {
        if (previewContainer == null) return;
        previewContainer.removeAllViews();

        if (selectedUris.isEmpty()) {
            TextView empty = text("Здесь появится предпросмотр выбранных фото", 13, MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(rounded(0xFFF0F4F2, 14));
            empty.setPadding(dp(18), 0, dp(18), 0);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(dp(270), dp(92));
            previewContainer.addView(empty, emptyParams);
            return;
        }

        for (int i = 0; i < selectedUris.size(); i++) {
            Uri uri = selectedUris.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(0xFFE8EEEB, 16));
            image.setClipToOutline(true);
            try {
                image.setImageBitmap(decodeSampled(uri, 240));
            } catch (IOException ignored) {
            }
            cell.addView(image, new LinearLayout.LayoutParams(dp(88), dp(88)));

            TextView number = text(String.valueOf(i + 1), 11, MUTED, Typeface.BOLD);
            number.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.topMargin = dp(3);
            cell.addView(number, numberParams);

            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(92), dp(112));
            cellParams.rightMargin = dp(8);
            previewContainer.addView(cell, cellParams);
        }
    }

    private void updateActionButtons() {
        if (createButton == null || addButton == null || pickButton == null) return;

        boolean enoughPhotos = selectedUris.size() >= MIN_STICKERS && selectedUris.size() <= MAX_STICKERS;
        createButton.setEnabled(enoughPhotos && !isProcessing);
        addButton.setEnabled(currentPack != null && !isProcessing);
        pickButton.setEnabled(!isProcessing);

        createButton.setAlpha(createButton.isEnabled() ? 1f : 0.48f);
        addButton.setAlpha(addButton.isEnabled() ? 1f : 0.48f);
        pickButton.setAlpha(pickButton.isEnabled() ? 1f : 0.62f);
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
