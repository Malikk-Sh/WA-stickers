package com.malikksh.wastickers;

import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

@RunWith(AndroidJUnit4.class)
public class HandoffV6UiTest {
    @Test public void thirtyItemsHaveBoundedOverviewFullFilesHonestStatsAndStickyActions() {
        try (ActivityScenario<AppShellActivity> scenario = ActivityScenario.launch(AppShellActivity.class)) {
            scenario.onActivity(activity -> {
                BuildPanel panel = new BuildPanel(activity, null, new NoOpHost());
                activity.setContentView(panel);
                List<BuildPanel.Item> items = new ArrayList<>();
                for (int i = 0; i < 30; i++) items.add(new BuildPanel.Item(Uri.parse("file:///test" + i + ".webp"),
                        "technical_name_" + i + ".webp", i == 29 ? BuildPanel.ItemPhase.ERROR
                        : i == 28 ? BuildPanel.ItemPhase.PROCESSING : BuildPanel.ItemPhase.READY, i < 28 ? 100 : 0, "q92 FPS"));
                panel.render(new BuildPanel.Snapshot(BuildPanel.Phase.PROCESSING, true, "Тридцать стикеров", null,
                        30, 28, 1, 93, "", false, false, true, items));
                assertEquals(6, ((ViewGroup) activity.findViewById(R.id.build_file_list)).getChildCount());
                ViewGroup stats = panel.findViewWithTag("build_stats");
                int[] expected = {30, 28, 1, 1};
                for (int i = 0; i < 4; i++) assertEquals(String.valueOf(expected[i]),
                        ((TextView) ((ViewGroup) stats.getChildAt(i)).getChildAt(0)).getText().toString());
            });
            onView(withId(R.id.build_primary)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> capture(activity, "build-overview"));
            onView(withText("Файлы")).perform(click());
            scenario.onActivity(activity -> {
                ViewGroup list = activity.findViewById(R.id.build_file_list);
                assertEquals(30, list.getChildCount());
                View primary = activity.findViewById(R.id.build_primary);
                assertFalse(primary.getParent() instanceof ScrollView);
                Rect bounds = new Rect(); assertTrue(primary.getGlobalVisibleRect(bounds));
                assertEquals(primary.getHeight(), bounds.height());
                assertFalse(allText(list).contains("q92"));
                assertFalse(allText(list).contains("technical_name"));
                capture(activity, "build-files");
            });
            onView(withText("Журнал")).perform(click());
            scenario.onActivity(activity -> assertTrue(allText(activity.findViewById(R.id.build_file_list))
                    .contains("Стикер 30: не удалось обработать")));
        }
    }

    @Test public void editorUsesFiveColumnsWithoutHeaderOverlapAndKeepsFooterVisible() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.runtimeNewProject("Очень длинное название набора для проверки заголовка");
                List<Uri> items = new ArrayList<>();
                for (int i = 0; i < 30; i++) items.add(Uri.parse("file:///missing-v6-" + i + ".png"));
                activity.runtimeRestoreEditorState(false, items, items.get(0), "Тридцать стикеров", null);
                activity.enterCreateEditor();
                activity.refreshShellState();
            });
            onView(withId(R.id.media_continue)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                GridLayout grid = activity.findViewById(R.id.media_grid);
                float width = grid.getWidth() / activity.getResources().getDisplayMetrics().density;
                assertEquals(EditorGridPolicy.columns(width, activity.getResources().getConfiguration().fontScale), grid.getColumnCount());
                assertEquals(30, grid.getChildCount());
                for (int i = 0; i < grid.getColumnCount(); i++) assertTrue(grid.getChildAt(i).getRight() <= grid.getWidth());
                Rect count = new Rect(), settings = new Rect();
                activity.findViewById(R.id.create_media_counter).getGlobalVisibleRect(count);
                activity.findViewById(R.id.app_settings).getGlobalVisibleRect(settings);
                assertFalse(Rect.intersects(count, settings));
                View next = activity.findViewById(R.id.media_continue);
                Rect visible = new Rect(); assertTrue(next.getGlobalVisibleRect(visible));
                assertEquals(next.getHeight(), visible.height());
                capture(activity, "editor-30");
                grid.getChildAt(0).performClick();
            });
        }
    }

    @Test public void updatedPackRequiresNewAcknowledgementAndCanRetrySyncFailure() {
        android.content.Context context = androidx.test.core.app.ApplicationProvider.getApplicationContext();
        String id = "v6-sync-test";
        PackStore.Pack updated = new PackStore.Pack(id, "Обновление", 3, "2");
        try {
            WhatsAppSync.set(context, id, WhatsAppSync.State.ADDED_SYNCED, "1");
            assertEquals(WhatsAppSync.State.ADDED_LOCAL_CHANGES, WhatsAppSync.state(context, updated));
            WhatsAppSync.set(context, id, WhatsAppSync.State.SYNCING, null);
            assertEquals("Обновление…", WhatsAppSync.label(context, updated));
            WhatsAppSync.set(context, id, WhatsAppSync.State.SYNC_ERROR, null);
            assertEquals("Повторить в WhatsApp", WhatsAppSync.label(context, updated));
            WhatsAppSync.set(context, id, WhatsAppSync.State.SYNCING, null);
            WhatsAppSync.set(context, id, WhatsAppSync.State.ADDED_SYNCED, updated.imageDataVersion);
            assertEquals(WhatsAppSync.State.ADDED_SYNCED, WhatsAppSync.state(context, updated));
            assertEquals("✓ В WhatsApp", WhatsAppSync.label(context, updated));
        } finally {
            context.getSharedPreferences("whatsapp_sync", 0).edit().remove(id + ".state").remove(id + ".version").commit();
        }
    }

    private static void capture(android.app.Activity activity, String name) {
        View root = activity.getWindow().getDecorView();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bitmap));
        java.io.File directory = new java.io.File(activity.getExternalFilesDir(null), "v6-screenshots");
        directory.mkdirs();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(directory, name + ".png"))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
        // Gradle uninstalls the app after connected tests, removing its external-files directory.
        shell("mkdir -p /sdcard/Download/wa-v6");
        shell("cp '" + new java.io.File(directory, name + ".png").getAbsolutePath()
                + "' '/sdcard/Download/wa-v6/" + name + ".png'");
    }

    private static void shell(String command) {
        try (android.os.ParcelFileDescriptor descriptor = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation().executeShellCommand(command);
             java.io.FileInputStream output = new java.io.FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (output.read(buffer) != -1) { /* Drain to wait for the command to finish. */ }
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    private static String allText(View view) {
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText());
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            text.append(allText(((ViewGroup) view).getChildAt(i)));
        return text.toString();
    }
    private static class NoOpHost implements BuildPanel.Host {
        public void onStart() {} public void onCancel() {} public void onRetryAll() {}
        public void onRetryItem(Uri uri) {} public void onSkipItem(Uri uri) {} public void onFinalize() {}
        public void onDiscard() {} public void onAddToWhatsApp() {} public void onOpenPacks() {}
    }
}
