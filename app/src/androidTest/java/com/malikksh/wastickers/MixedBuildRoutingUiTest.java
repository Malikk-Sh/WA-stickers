package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class MixedBuildRoutingUiTest {
    private Context context;
    private File workDir;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        EditorInstanceStateBridge.clearPersistent(context);
        workDir = new File(context.getCacheDir(), "mixed_build_route_test");
        deleteRecursively(workDir);
        deleteRecursively(new File(context.getCacheDir(),
                "sticker_conversion_cache_v" + ConversionCache.CACHE_VERSION));
        assertTrue(workDir.mkdirs() || workDir.isDirectory());
    }

    @After
    public void tearDown() {
        EditorInstanceStateBridge.clearPersistent(context);
        deleteRecursively(workDir);
        deleteRecursively(new File(context.getCacheDir(),
                "sticker_conversion_cache_v" + ConversionCache.CACHE_VERSION));
    }

    @Test
    public void mixedProjectRoutesToAnimatedBuildWithoutChangingOrderOrCover() throws Exception {
        Uri firstStatic = createPng("first.png", Color.rgb(30, 120, 190));
        Uri secondStatic = createPng("second.png", Color.rgb(190, 90, 40));
        File animatedFile = new File(workDir, "animated.webp");
        StaticAnimatedWebpWrapper.convert(context, firstStatic, animatedFile, null);
        Uri animated = Uri.fromFile(animatedFile);

        List<Uri> expected = new ArrayList<>();
        expected.add(firstStatic);
        expected.add(animated);
        expected.add(secondStatic);

        try (ActivityScenario<SettingsShellActivity> scenario =
                     ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.runtimeRestoreEditorState(
                        false,
                        expected,
                        secondStatic,
                        "Смешанный набор",
                        null
                ));
                assertFalse(activity.runtimeIsAnimatedMode());
                activity.showBuildScreen();
            });

            AtomicBoolean routed = new AtomicBoolean(false);
            for (int attempt = 0; attempt < 60 && !routed.get(); attempt++) {
                scenario.onActivity(activity -> {
                    View buildPanel = activity.findViewById(R.id.build_panel);
                    routed.set(activity.runtimeIsAnimatedMode()
                            && buildPanel != null
                            && buildPanel.isShown());
                });
                if (!routed.get()) SystemClock.sleep(100L);
            }
            assertTrue("Mixed planner did not route to animated Build", routed.get());

            scenario.onActivity(activity -> {
                assertEquals(expected, activity.runtimeSelectedUrisSnapshot());
                assertEquals(secondStatic, activity.runtimeCoverUri());
                assertEquals("Смешанный набор", activity.runtimeEnteredPackName());
            });
        }
    }

    private Uri createPng(String name, int fillColor) throws Exception {
        File source = new File(workDir, name);
        Bitmap bitmap = Bitmap.createBitmap(180, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(fillColor);
        canvas.drawRect(12, 12, 168, 108, paint);
        try (FileOutputStream stream = new FileOutputStream(source)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
        return Uri.fromFile(source);
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
