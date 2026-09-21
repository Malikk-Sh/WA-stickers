package com.malikksh.wastickers;

import android.content.*;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
public class HandoffV5UiTest {
    private Context context;
    @Before public void before() {
        context = ApplicationProvider.getApplicationContext();
        AppSettings.setKeepDrafts(context, true);
        EditorInstanceStateBridge.clearPersistent(context);
        for (PackStore.Pack pack : PackStore.getPacks(context)) PackStore.deletePack(context, pack.id);
        context.getSharedPreferences("whatsapp_sync", 0).edit().clear().commit();
    }
    @After public void after() { before(); }

    @Test public void newPackIsEmptyAndReopeningDraftRestoresOnlyItsSources() throws Exception {
        Uri source = Uri.fromFile(image(new File(context.getCacheDir(), "draft-a.png"), 96, Bitmap.CompressFormat.PNG));
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> {
                activity.runtimeNewProject("A");
                String id = activity.runtimeProjectId();
                activity.runtimeRestoreEditorState(false, Arrays.asList(source), source, "A", null);
                EditorInstanceStateBridge.savePersistent(activity);
                assertTrue(activity.runtimeNewProject("B"));
                assertNotEquals(id, activity.runtimeProjectId());
                assertTrue(activity.runtimeSelectedUrisSnapshot().isEmpty());
                assertNull(activity.runtimeCoverUri());
                assertFalse(activity.runtimeBuildSession().isActive());
                assertTrue(EditorInstanceStateBridge.restoreProject(activity, id));
                assertEquals(Arrays.asList(source), activity.runtimeSelectedUrisSnapshot());
                assertEquals(source, activity.runtimeCoverUri());
                assertEquals("A", activity.runtimeEnteredPackName());
            });
        }
    }

    @Test public void duplicateNamesAreRejectedInDialogAndStore() {
        PackStore.addPack(context, new PackStore.Pack("a", "Мои стикеры", 3, "1"));
        assertEquals("Мои стикеры 2", PackStore.nextName(context, "Мои стикеры"));
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            onView(withId(R.id.packs_create_first)).perform(click());
            onView(withId(R.id.pack_name_dialog_input)).check(matches(withText("Мои стикеры 2")));
            onView(withId(R.id.pack_name_dialog_input)).perform(replaceText(" мои  СТИКЕРЫ "));
            onView(withId(R.id.pack_name_dialog_confirm)).check(matches(not(isEnabled())));
            onView(withId(R.id.pack_name_dialog_input)).check(matches(hasErrorText("Набор с таким названием уже существует")));
        }
        PackStore.addPack(context, new PackStore.Pack("b", "Другой", 3, "1"));
        assertNull(PackStore.renamePack(context, "b", "МОИ СТИКЕРЫ"));
    }

    @Test public void recreationKeepsLibraryAndExactEditorProject() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.recreate();
            onView(withId(R.id.packs_panel)).check(matches(isDisplayed()));
            final String[] id = new String[1];
            scenario.onActivity(activity -> {
                activity.runtimeNewProject("Маршрут");
                id[0] = activity.runtimeProjectId();
                activity.enterCreateEditor();
            });
            scenario.recreate();
            onView(withId(R.id.media_grid)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertEquals(id[0], activity.runtimeProjectId()));
        }
    }

    @Test public void libraryReceivesStoreChangesWithoutResume() {
        try (ActivityScenario<SettingsShellActivity> scenario = ActivityScenario.launch(SettingsShellActivity.class)) {
            scenario.onActivity(activity -> PackStore.addPack(context, new PackStore.Pack("live", "Live A", 3, "1")));
            onView(withText("Live A")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> PackStore.renamePack(context, "live", "Live B"));
            onView(withText("Live B")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> PackStore.deletePack(context, "live"));
            onView(withId(R.id.packs_empty)).check(matches(isDisplayed()));
        }
    }

    @Test public void pickerContractsDifferAndSupportBothImagesAndVideo() {
        Intent media = MediaPickerIntentFactory.createUnifiedGetContentFallback();
        Intent files = MediaPickerIntentFactory.createUnifiedOpenDocumentIntent();
        assertEquals(Intent.ACTION_GET_CONTENT, media.getAction());
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, files.getAction());
        assertTrue(media.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
        assertTrue(files.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
        assertArrayEquals(new String[]{"image/*", "video/*"}, media.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));
        assertNull(media.getPackage());
    }

    @Test public void generationCommitKeepsIdentityVersionsAndOldContentOnFailure() throws Exception {
        File first = generation("stable", 0xff008800);
        PackStore.Pack a = PackStore.commitGeneration(context, "stable", "Versioned", 3, false, first, 3);
        assertEquals("1", a.imageDataVersion);
        WhatsAppSync.set(context, a.id, WhatsAppSync.State.ADDED_SYNCED, "1");
        assertEquals(WhatsAppSync.State.ADDED_SYNCED, WhatsAppSync.state(context, a));
        File broken = PackStore.stagingDir(context, a.id);
        broken.mkdirs();
        try {
            PackStore.commitGeneration(context, a.id, a.name, 3, false, broken, 3);
            fail("Broken generation was published");
        } catch (IOException expected) { }
        assertEquals("1", PackStore.getPack(context, a.id).imageDataVersion);
        assertTrue(new File(first, "1.webp").isFile());
        File second = generation(a.id, 0xff000088);
        PackStore.Pack b = PackStore.commitGeneration(context, a.id, a.name, 3, false, second, 3);
        assertEquals(a.id, b.id);
        assertEquals("2", b.imageDataVersion);
        assertEquals(1, PackStore.getPacks(context).size());
        assertEquals(WhatsAppSync.State.ADDED_LOCAL_CHANGES, WhatsAppSync.state(context, b));
        assertEquals("Обновить в WhatsApp", WhatsAppSync.label(context, b));
        assertEquals(new File(second, "1.webp"), PackStore.getStickerFile(context, b.id, "1.webp"));
        StickerContentProvider provider = new StickerContentProvider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = context.getPackageName() + ".stickercontentprovider";
        provider.attachInfo(context, info);
        Uri metadata = Uri.parse("content://" + info.authority + "/metadata/" + b.id);
        try (Cursor cursor = provider.query(metadata, null, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("2", cursor.getString(cursor.getColumnIndexOrThrow("image_data_version")));
        }
        PackStore.Pack renamed = PackStore.renamePack(context, b.id, "Renamed");
        assertEquals(b.id, renamed.id);
        assertEquals("3", renamed.imageDataVersion);
    }

    @Test public void mixedPngJpegAndMovingGifUseExistingAnimatedPipeline() throws Exception {
        File sources = new File(context.getCacheDir(), "v5-mixed-fixture");
        sources.mkdirs();
        File png = image(new File(sources, "photo.png"), 512, Bitmap.CompressFormat.PNG);
        File jpeg = image(new File(sources, "photo.jpg"), 512, Bitmap.CompressFormat.JPEG, 0xff2233aa);
        File gif = new File(sources, "motion.gif");
        Context testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext();
        try (InputStream input = testContext.getAssets().open("mixed-motion.gif"); FileOutputStream output = new FileOutputStream(gif)) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        }
        File generation = PackStore.stagingDir(context, "mixed-v5");
        generation.mkdirs();
        List<Uri> ordered = Arrays.asList(Uri.fromFile(png), Uri.fromFile(gif), Uri.fromFile(jpeg));
        StickerPackBuilder builder = new StickerPackBuilder(context, true);
        for (int i = 0; i < ordered.size(); i++) builder.convert(ordered.get(i), new File(generation, (i + 1) + ".webp"), null);
        builder.createTrayIcon(ordered.get(2), new File(generation, "tray.png"));
        ProjectSources.write(generation, ordered, ordered.get(2));
        PackStore.Pack pack = PackStore.commitGeneration(context, "mixed-v5", "Mixed fixture", 3, true, generation, 2);
        assertEquals(2, pack.photoCount);
        assertTrue(StaticAnimatedWebpWrapper.isValidWrappedWebp(context, PackStore.getStickerFile(context, pack.id, "1.webp")));
        assertTrue(StaticAnimatedWebpWrapper.isValidWrappedWebp(context, PackStore.getStickerFile(context, pack.id, "3.webp")));
        assertEquals(MediaAnimationInspector.AnimationKind.ANIMATED,
                new SourceAnimationDetector(context).classify(Uri.fromFile(PackStore.getStickerFile(context, pack.id, "2.webp"))));
        ProjectSources.Snapshot saved = ProjectSources.read(context, pack);
        assertNotNull(saved);
        assertEquals(ordered, saved.items);
        assertEquals(ordered.get(2), saved.cover);
        PackStore.deleteRecursively(sources);
    }

    private File generation(String id, int color) throws Exception {
        File dir = PackStore.stagingDir(context, id);
        assertTrue(dir.mkdirs());
        for (int i = 1; i <= 3; i++) image(new File(dir, i + ".webp"), 512, Bitmap.CompressFormat.WEBP, color + i);
        image(new File(dir, "tray.png"), 96, Bitmap.CompressFormat.PNG, color);
        return dir;
    }
    private File image(File file, int size, Bitmap.CompressFormat format) throws Exception {
        return image(file, size, format, 0xff008800);
    }
    private File image(File file, int size, Bitmap.CompressFormat format, int color) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        try (FileOutputStream output = new FileOutputStream(file)) { assertTrue(bitmap.compress(format, 90, output)); }
        bitmap.recycle();
        return file;
    }
}
