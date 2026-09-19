package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class ReleaseContractUiTest {
    private static final String PACK_ID = "release_contract_pack";
    private Context context;
    private String authority;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        authority = context.getPackageName() + ".stickercontentprovider";
        clearTestPack();

        File packDir = PackStore.getPackDir(context, PACK_ID);
        if (!packDir.mkdirs() && !packDir.isDirectory()) {
            throw new IllegalStateException("Could not create test pack directory");
        }
        writeBytes(new File(packDir, "tray.png"), new byte[]{1, 2, 3, 4});
        for (int i = 1; i <= 3; i++) {
            writeBytes(new File(packDir, i + ".webp"), new byte[]{(byte) i, 9, 8, 7});
        }
        PackStore.addPack(context, new PackStore.Pack(PACK_ID, "Release contract", 3, "42", false));
    }

    @After
    public void tearDown() {
        clearTestPack();
    }

    @Test
    public void manifestContractMatchesProductionIntegration() throws Exception {
        PackageManager packageManager = context.getPackageManager();
        PackageInfo info = packageManager.getPackageInfo(
                context.getPackageName(),
                PackageManager.GET_PROVIDERS | PackageManager.GET_PERMISSIONS
        );

        ProviderInfo stickerProvider = null;
        if (info.providers != null) {
            for (ProviderInfo provider : info.providers) {
                if (authority.equals(provider.authority)) {
                    stickerProvider = provider;
                    break;
                }
            }
        }
        assertNotNull("Sticker provider must be declared", stickerProvider);
        assertTrue("Sticker provider must be exported for WhatsApp", stickerProvider.exported);
        assertEquals("com.whatsapp.sticker.READ", stickerProvider.readPermission);

        String[] requestedPermissions = info.requestedPermissions;
        assertFalse(
                "The app must remain offline and not request INTERNET",
                requestedPermissions != null && Arrays.asList(requestedPermissions).contains(Manifest.permission.INTERNET)
        );

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.getPackageName());
        ResolveInfo launcher = packageManager.resolveActivity(launcherIntent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull("Launcher activity must resolve", launcher);
        assertNotNull(launcher.activityInfo);
        assertEquals(SettingsShellActivity.class.getName(), launcher.activityInfo.name);
    }

    @Test
    public void providerExposesMetadataStickerListAndAssets() throws Exception {
        Uri metadata = Uri.parse("content://" + authority + "/metadata/" + PACK_ID);
        try (Cursor cursor = context.getContentResolver().query(metadata, null, null, null, null)) {
            assertNotNull(cursor);
            assertTrue(cursor.moveToFirst());
            assertEquals(PACK_ID, cursor.getString(cursor.getColumnIndexOrThrow("sticker_pack_identifier")));
            assertEquals("Release contract", cursor.getString(cursor.getColumnIndexOrThrow("sticker_pack_name")));
            assertEquals("tray.png", cursor.getString(cursor.getColumnIndexOrThrow("sticker_pack_icon")));
            assertEquals("42", cursor.getString(cursor.getColumnIndexOrThrow("image_data_version")));
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("whatsapp_will_not_cache_stickers")));
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("animated_sticker_pack")));
        }

        Uri stickers = Uri.parse("content://" + authority + "/stickers/" + PACK_ID);
        try (Cursor cursor = context.getContentResolver().query(stickers, null, null, null, null)) {
            assertNotNull(cursor);
            assertEquals(3, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals("1.webp", cursor.getString(cursor.getColumnIndexOrThrow("sticker_file_name")));
            assertNotNull(cursor.getString(cursor.getColumnIndexOrThrow("sticker_emoji")));
            assertNotNull(cursor.getString(cursor.getColumnIndexOrThrow("sticker_accessibility_text")));
        }

        Uri asset = Uri.parse("content://" + authority + "/stickers_asset/" + PACK_ID + "/1.webp");
        try (AssetFileDescriptor descriptor = context.getContentResolver().openAssetFileDescriptor(asset, "r")) {
            assertNotNull(descriptor);
            assertTrue(descriptor.getParcelFileDescriptor().getStatSize() > 0L);
        }

        Uri invalid = Uri.parse("content://" + authority + "/stickers_asset/" + PACK_ID + "/99.webp");
        try (AssetFileDescriptor ignored = context.getContentResolver().openAssetFileDescriptor(invalid, "r")) {
            fail("Provider must reject files outside the declared sticker count");
        } catch (FileNotFoundException expected) {
            // Expected: the provider only exposes declared tray/sticker files.
        }
    }

    private void clearTestPack() {
        PackStore.deletePack(context, PACK_ID);
        deleteRecursively(PackStore.getPackDir(context, PACK_ID));
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
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
