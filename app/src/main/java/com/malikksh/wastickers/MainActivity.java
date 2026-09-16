package com.malikksh.wastickers;

import android.app.Activity;
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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private static final int REQUEST_PICK_IMAGES = 101;
    private static final int REQUEST_ADD_TO_WHATSAPP = 200;
    private static final int MAX_STICKERS = 30;
    private static final int MIN_STICKERS = 3;
    private static final int MAX_STICKER_BYTES = 100 * 1024;

    private final List<Uri> selectedUris = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText packName;
    private TextView selectionStatus;
    private TextView processStatus;
    private LinearLayout previewContainer;
    private Button createButton;
    private Button addButton;
    private PackStore.Pack currentPack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());

        currentPack = PackStore.getLatestPack(this);
        if (currentPack != null) {
            addButton.setEnabled(true);
            processStatus.setText("Последний набор: “" + currentPack.name + "” — можно добавить в WhatsApp.");
        }
    }

    private ScrollView buildUi() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(28), pad, dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("WA Sticker Maker");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(20, 30, 27));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Выберите 3–30 фото. Фон не удаляется, белая обводка не добавляется. Фото целиком помещается в стикер 512×512.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(8);
        root.addView(subtitle, subParams);

        packName = new EditText(this);
        packName.setHint("Название набора");
        packName.setSingleLine(true);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = dp(24);
        root.addView(packName, fieldParams);

        Button pickButton = new Button(this);
        pickButton.setText("Выбрать фото");
        tintButton(pickButton, 0xFF128C7E);
        pickButton.setOnClickListener(v -> openPhotoPicker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        buttonParams.topMargin = dp(16);
        root.addView(pickButton, buttonParams);

        selectionStatus = new TextView(this);
        selectionStatus.setText("Фото пока не выбраны");
        selectionStatus.setTextSize(14);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        root.addView(selectionStatus, statusParams);

        HorizontalScrollView previewScroll = new HorizontalScrollView(this);
        previewScroll.setHorizontalScrollBarEnabled(false);
        previewContainer = new LinearLayout(this);
        previewContainer.setOrientation(LinearLayout.HORIZONTAL);
        previewContainer.setGravity(Gravity.CENTER_VERTICAL);
        previewScroll.addView(previewContainer);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(100));
        previewParams.topMargin = dp(10);
        root.addView(previewScroll, previewParams);

        createButton = new Button(this);
        createButton.setText("Создать набор стикеров");
        tintButton(createButton, 0xFF25D366);
        createButton.setOnClickListener(v -> createPack());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        createParams.topMargin = dp(14);
        root.addView(createButton, createParams);

        addButton = new Button(this);
        addButton.setText("Добавить в WhatsApp");
        addButton.setEnabled(false);
        tintButton(addButton, 0xFF075E54);
        addButton.setOnClickListener(v -> addCurrentPackToWhatsApp());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        addParams.topMargin = dp(10);
        root.addView(addButton, addParams);

        processStatus = new TextView(this);
        processStatus.setTextSize(14);
        processStatus.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams processParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        processParams.topMargin = dp(14);
        root.addView(processStatus, processParams);

        TextView privacy = new TextView(this);
        privacy.setText("Обработка выполняется на телефоне. Приложение не загружает ваши фотографии на сервер.");
        privacy.setTextSize(12);
        privacy.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(28);
        root.addView(privacy, privacyParams);

        return scroll;
    }

    private void tintButton(Button button, int color) {
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_IMAGES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            receiveSelectedImages(data);
        } else if (requestCode == REQUEST_ADD_TO_WHATSAPP && resultCode == RESULT_OK) {
            processStatus.setText("Набор добавлен в WhatsApp.");
        }
    }

    private void receiveSelectedImages(Intent data) {
        selectedUris.clear();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount() && selectedUris.size() < MAX_STICKERS; i++) {
                selectedUris.add(clipData.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selectedUris.add(data.getData());
        }

        for (Uri uri : selectedUris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
        }

        selectionStatus.setText("Выбрано фото: " + selectedUris.size() + " из " + MAX_STICKERS);
        renderPreviews();
    }

    private void renderPreviews() {
        previewContainer.removeAllViews();
        for (Uri uri : selectedUris) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xFFECECEC);
            try {
                image.setImageBitmap(decodeSampled(uri, 240));
            } catch (IOException ignored) {
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(88));
            params.rightMargin = dp(8);
            previewContainer.addView(image, params);
        }
    }

    private void createPack() {
        if (selectedUris.size() < MIN_STICKERS || selectedUris.size() > MAX_STICKERS) {
            Toast.makeText(this, "Нужно выбрать от 3 до 30 фото", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = packName.getText().toString().trim();
        if (name.isEmpty()) name = "Мои стикеры";
        final String finalName = name;

        createButton.setEnabled(false);
        addButton.setEnabled(false);
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
                    createButton.setEnabled(true);
                    addButton.setEnabled(true);
                    processStatus.setText("Готово: “" + pack.name + "”. Теперь нажмите «Добавить в WhatsApp».");
                });
            } catch (Exception e) {
                deleteRecursively(packDir);
                runOnUiThread(() -> {
                    createButton.setEnabled(true);
                    addButton.setEnabled(currentPack != null);
                    processStatus.setText("Ошибка: " + (e.getMessage() == null ? "не удалось создать набор" : e.getMessage()));
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

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File child : files) deleteRecursively(child);
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
