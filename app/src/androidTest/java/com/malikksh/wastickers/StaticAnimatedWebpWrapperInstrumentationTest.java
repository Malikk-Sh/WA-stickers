package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class StaticAnimatedWebpWrapperInstrumentationTest {
    private Context context;
    private File workDir;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        workDir = new File(context.getCacheDir(), "static_wrapper_test");
        deleteRecursively(workDir);
        deleteRecursively(new File(context.getCacheDir(),
                "sticker_conversion_cache_v" + ConversionCache.CACHE_VERSION));
        assertTrue(workDir.mkdirs() || workDir.isDirectory());
    }

    @After
    public void tearDown() {
        deleteRecursively(workDir);
        deleteRecursively(new File(context.getCacheDir(),
                "sticker_conversion_cache_v" + ConversionCache.CACHE_VERSION));
    }

    @Test
    public void staticPngBecomesValidAnimatedWebp() throws Exception {
        File source = new File(workDir, "source.png");
        Bitmap bitmap = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(18, 140, 126));
        canvas.drawRect(12, 12, 168, 108, paint);
        try (FileOutputStream stream = new FileOutputStream(source)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }

        File output = new File(workDir, "wrapped.webp");
        AnimatedStickerConverter.Result result = StaticAnimatedWebpWrapper.convert(
                context,
                Uri.fromFile(source),
                output,
                null
        );

        assertTrue(output.isFile());
        assertTrue(output.length() > 0);
        assertTrue(output.length() <= AnimatedStickerConverter.MAX_ANIMATED_BYTES);
        assertEquals(2, result.fps);
        assertTrue(StaticAnimatedWebpWrapper.isValidWrappedWebp(context, output));
    }

    private static void deleteRecursively(File file) {
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
}
