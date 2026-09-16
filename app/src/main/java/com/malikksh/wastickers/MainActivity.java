package com.malikksh.wastickers;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_PICK_PHOTOS = 1001;
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKERS = 30;
    private static final int MAX_STICKER_BYTES = 100 * 1024;

    private static final int BG = 0xFFF5F8F6;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF14221D;
    private static final int MUTED = 0xFF6A7872;
    private static final int PRIMARY = 0xFF075E54;
    private static final int TEAL = 0xFF128C7E;
    private static final int GREEN = 0xFF25D366;
    private static final int SOFT = 0xFFEAF5EF;
    private static final int BORDER = 0xFFDCE7E1;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText packName;
    private TextView countText;
    private TextView statusText;
    private LinearLayout previewContainer;
    private Button galleryButton;
    private Button createButton;
    private Button addButton;
    private PackStore.Pack currentPack;
    private boolean processing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        try {
            setContentView(buildUi());
        } catch (Throwable error) {
            showSafeFallback(error);
            return;
        }

        try {
            currentPack = PackStore.getLatestPack(this);
        } catch (Throwable ignored) {
            currentPack = null;
        }

        if (currentPack != null) {
            statusText.setText("Последний набор «" + currentPack.name + "» готов к добавлению в WhatsApp.");
        }
        updateUiState();
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
        TextView title = text("Стикеры из фото", 25, Color.WHITE, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(2);
        heroTexts.addView(title, titleParams);

        TextView subtitle = text(
                "Выберите фотографии из галереи и добавьте готовый набор в WhatsApp.",
                14, 0xFFE7F6F1, Typeface.NORMAL);
        subtitle.setLineSpacing(0, 1.08f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(15);
        hero.addView(subtitle, subtitleParams);

        LinearLayout nameCard = card();
        LinearLayout.LayoutParams nameCardParams = matchWrap();
        nameCardParams.topMargin = dp(14);
        root.addView(nameCard, nameCardParams);

        nameCard.addView(text("1  Название набора", 17, TEXT, Typeface.BOLD));
        TextView nameHint = text("Например: Отпуск 2026", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams nameHintParams = matchWrap();
        nameHintParams.topMargin = dp(5);
        nameCard.addView(nameHint, nameHintParams);

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
        nameParams.topMargin = dp(13);
        nameCard.addView(packName, nameParams);

        LinearLayout photosCard = card();
        LinearLayout.LayoutParams photosParams = matchWrap();
        photosParams.topMargin = dp(12);
        root.addView(photosCard, photosParams);

        LinearLayout photosHeader = new LinearLayout(this);
        photosHeader.setOrientation(LinearLayout.HORIZONTAL);
        photosHeader.setGravity(Gravity.CENTER_VERTICAL);
        photosCard.addView(photosHeader, matchWrap());

        TextView photosTitle = text("2  Фотографии", 17, TEXT, Typeface.BOLD);
        photosHeader.addView(photosTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        countText = text("0 / 30", 12, PRIMARY, Typeface.BOLD);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(dp(10), 0, dp(10), 0);
        countText.setBackground(rounded(SOFT, 14));
        photosHeader.addView(countText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));

        TextView photosHint = text(
                "Нужно выбрать от 3 до 30 фото. Фон и само изображение остаются без изменений.",
                13, MUTED, Typeface.NORMAL);
        photosHint.setLineSpacing(0, 1.07f);
        LinearLayout.LayoutParams photosHintParams = matchWrap();
        photosHintParams.topMargin = dp(8);
        photosCard.addView(photosHint, photosHintParams);

        galleryButton = new Button(this);
        galleryButton.setText("Открыть галерею");
        styleButton(galleryButton, PRIMARY, Color.WHITE);
        galleryButton.setOnClickListener(v -> openGallery());
        LinearLayout.LayoutParams galleryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        galleryParams.topMargin = dp(14);
        photosCard.addView(galleryButton, galleryParams);

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
        photosCard.addView(previewScroll, previewParams);
        renderPreviews();

        LinearLayout actionsCard = card();
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(12);
        root.addView(actionsCard, actionsParams);

        actionsCard.addView(text("3  Готовый набор", 17, TEXT, Typeface.BOLD));
        TextView actionHint = text(
                "Приложение приведёт фото к 512×512 WebP и сохранит их только на телефоне.",
                13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams actionHintParams = matchWrap();
        actionHintParams.topMargin = dp(7);
        actionsCard.addView(actionHint, actionHintParams);

        createButton = new Button(this);
        createButton.setText("Создать набор");
        styleButton(createButton, GREEN, 0xFF073B2B);
        createButton.setOnClickListener(v -> createPack());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        createParams.topMargin = dp(15);
        actionsCard.addView(createButton, createParams);

        addButton = new Button(this);
        addButton.setText("Добавить в WhatsApp");
        styleButton(addButton, PRIMARY, Color.WHITE);
        addButton.setOnClickListener(v -> addCurrentPackToWhatsApp());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        addParams.topMargin = dp(9);
        actionsCard.addView(addButton, addParams);

        statusText = text("Выберите минимум 3 фотографии.", 13, MUTED, Typeface.NORMAL);
        statusText.setPadding(dp(15), dp(14), dp(15), dp(14));
        statusText.setBackground(rounded(SOFT, 16));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(12);
        root.addView(statusText, statusParams);

        TextView privacy = text(
                "Фото обрабатываются локально и никуда не загружаются.",
                12, MUTED, Typeface.NORMAL);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.topMargin = dp(16);
        root.addView(privacy, privacyParams);

        return scroll;
    }

    private void showSafeFallback(Throwable error) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(BG);

        TextView title = text("WA Stickers", 24, TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView message = text(
                "Не удалось загрузить основной экран. Перезапустите приложение. Если ошибка повторится, установите свежую сборку.",
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

    private void openGallery() {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_PHOTOS && resultCode == RESULT_OK && data != null) {
            receiveGalleryResult(data);
            return;
        }

        if (requestCode == REQUEST_ADD_TO_WHATSAPP && resultCode == RESULT_OK && statusText != null) {
            statusText.setText("Набор успешно добавлен в WhatsApp.");
        }
    }

    private void receiveGalleryResult(Intent data) {
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

        for (Uri uri : incoming) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            if (!selectedUris.contains(uri)) selectedUris.add(uri);
        }

        renderPreviews();
        updateUiState();
    }

    private void renderPreviews() {
        if (previewContainer == null) return;
        previewContainer.removeAllViews();

        if (selectedUris.isEmpty()) {
            TextView empty = text("Выбранные фото появятся здесь", 13, MUTED, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), 0, dp(16), 0);
            empty.setBackground(rounded(0xFFF0F4F2, 14));
            previewContainer.addView(empty, new LinearLayout.LayoutParams(dp(250), dp(92)));
            return;
        }

        for (int i = 0; i < selectedUris.size(); i++) {
            final int index = i;
            Uri uri = selectedUris.get(i);

            FrameLayout frame = new FrameLayout(this);
            LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(dp(96), dp(96));
            frameParams.rightMargin = dp(9);
            previewContainer.addView(frame, frameParams);

            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(0xFFE9EFEC, 15));
            if (Build.VERSION.SDK_INT >= 21) image.setClipToOutline(true);
            try {
                image.setImageBitmap(decodeSampled(uri, 220));
            } catch (IOException ignored) {
            }
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            TextView remove = text("×", 18, Color.WHITE, Typeface.BOLD);
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(rounded(0xCC17231F, 12));
            remove.setOnClickListener(v -> {
                if (!processing && index >= 0 && index < selectedUris.size()) {
                    selectedUris.remove(index);
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

    private void updateUiState() {
        if (countText != null) countText.setText(selectedUris.size() + " / " + MAX_STICKERS);
        if (galleryButton != null) {
            galleryButton.setText(selectedUris.isEmpty() ? "Открыть галерею" : "Добавить ещё фото");
            galleryButton.setEnabled(!processing && selectedUris.size() < MAX_STICKERS);
            galleryButton.setAlpha(galleryButton.isEnabled() ? 1f : 0.45f);
        }
        if (createButton != null) {
            boolean enough = selectedUris.size() >= MIN_STICKERS && selectedUris.size() <= MAX_STICKERS;
            createButton.setEnabled(enough && !processing);
            createButton.setAlpha(createButton.isEnabled() ? 1f : 0.45f);
        }
        if (addButton != null) {
            addButton.setEnabled(currentPack != null && !processing);
            addButton.setAlpha(addButton.isEnabled() ? 1f : 0.45f);
        }
        if (statusText != null && !processing && currentPack == null) {
            if (selectedUris.isEmpty()) {
                statusText.setText("Выберите минимум 3 фотографии.");
            } else if (selectedUris.size() < MIN_STICKERS) {
                statusText.setText("Добавьте ещё " + (MIN_STICKERS - selectedUris.size()) + " фото.");
            } else {
                statusText.setText("Фото выбраны. Можно создавать набор.");
            }
        }
    }

    private void createPack() {
        if (selectedUris.size() < MIN_STICKERS || selectedUris.size() > MAX_STICKERS) {
            Toast.makeText(this, "Нужно выбрать от 3 до 30 фото", Toast.LENGTH_SHORT).show();
            return;
        }

        String enteredName = packName.getText().toString().trim();
        final String finalName = enteredName.isEmpty() ? "Мои стикеры" : enteredName;
        final List<Uri> work = new ArrayList<>(selectedUris);

        processing = true;
        statusText.setText("Создаю стикеры…");
        updateUiState();

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
                    final int done = i + 1;
                    runOnUiThread(() -> statusText.setText("Обработано " + done + " из " + work.size() + "…"));
                }

                createTrayIcon(new File(packDir, "1.webp"), new File(packDir, "tray.png"));
                PackStore.Pack pack = new PackStore.Pack(
                        id, finalName, work.size(), String.valueOf(System.currentTimeMillis()));
                PackStore.addPack(this, pack);
                currentPack = pack;

                String authority = getPackageName() + ".stickercontentprovider";
                getContentResolver().notifyChange(Uri.parse("content://" + authority + "/metadata"), null);

                runOnUiThread(() -> {
                    processing = false;
                    statusText.setText("Готово: «" + pack.name + "». Теперь добавьте набор в WhatsApp.");
                    updateUiState();
                });
            } catch (Throwable error) {
                deleteRecursively(packDir);
                runOnUiThread(() -> {
                    processing = false;
                    statusText.setText("Ошибка: " + (error.getMessage() == null ? "не удалось создать набор" : error.getMessage()));
                    updateUiState();
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

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Неподдерживаемое изображение");
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
        for (int quality = 92; quality >= 8; quality -= 6) {
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

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
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
