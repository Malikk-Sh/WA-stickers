package com.malikksh.wastickers;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;

public class StickerContentProvider extends ContentProvider {
    public static final String METADATA = "metadata";
    public static final String STICKERS = "stickers";
    public static final String STICKERS_ASSET = "stickers_asset";

    private static final int METADATA_ALL = 1;
    private static final int METADATA_ONE = 2;
    private static final int STICKERS_FOR_PACK = 3;
    private static final int STICKER_ASSET = 4;

    private UriMatcher matcher;
    private String authority;

    @Override
    public boolean onCreate() {
        authority = getContext().getPackageName() + ".stickercontentprovider";
        matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(authority, METADATA, METADATA_ALL);
        matcher.addURI(authority, METADATA + "/*", METADATA_ONE);
        matcher.addURI(authority, STICKERS + "/*", STICKERS_FOR_PACK);
        matcher.addURI(authority, STICKERS_ASSET + "/*/*", STICKER_ASSET);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int code = matcher.match(uri);
        if (code == METADATA_ALL) {
            return metadataCursor(uri, PackStore.getPacks(getContext()));
        }
        if (code == METADATA_ONE) {
            PackStore.Pack pack = PackStore.getPack(getContext(), uri.getLastPathSegment());
            return metadataCursor(uri, pack == null ? Collections.emptyList() : Collections.singletonList(pack));
        }
        if (code == STICKERS_FOR_PACK) {
            return stickerCursor(uri, uri.getLastPathSegment());
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor metadataCursor(Uri uri, List<PackStore.Pack> packs) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_pack_identifier",
                "sticker_pack_name",
                "sticker_pack_publisher",
                "sticker_pack_icon",
                "android_play_store_link",
                "ios_app_download_link",
                "sticker_pack_publisher_email",
                "sticker_pack_publisher_website",
                "sticker_pack_privacy_policy_website",
                "sticker_pack_license_agreement_website",
                "image_data_version",
                "whatsapp_will_not_cache_stickers",
                "animated_sticker_pack"
        });

        for (PackStore.Pack pack : packs) {
            cursor.addRow(new Object[]{
                    pack.id,
                    pack.name,
                    "WA Stickers",
                    "tray.png",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    pack.imageDataVersion,
                    1,
                    0
            });
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private Cursor stickerCursor(Uri uri, String packId) {
        MatrixCursor cursor = new MatrixCursor(new String[]{
                "sticker_file_name",
                "sticker_emoji",
                "sticker_accessibility_text"
        });
        PackStore.Pack pack = PackStore.getPack(getContext(), packId);
        if (pack != null) {
            for (int i = 1; i <= pack.stickerCount; i++) {
                cursor.addRow(new Object[]{i + ".webp", "🙂", "Photo sticker " + i});
            }
        }
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (matcher.match(uri) != STICKER_ASSET) {
            throw new FileNotFoundException("Unsupported URI: " + uri);
        }
        List<String> parts = uri.getPathSegments();
        if (parts.size() != 3) throw new FileNotFoundException("Invalid sticker URI");

        String packId = parts.get(1);
        String fileName = parts.get(2);
        PackStore.Pack pack = PackStore.getPack(getContext(), packId);
        if (pack == null || !isAllowedFile(pack, fileName)) {
            throw new FileNotFoundException("Sticker not found");
        }

        File file = PackStore.getStickerFile(getContext(), packId, fileName);
        if (!file.isFile()) throw new FileNotFoundException(file.getAbsolutePath());
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(descriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private boolean isAllowedFile(PackStore.Pack pack, String fileName) {
        if ("tray.png".equals(fileName)) return true;
        if (!fileName.endsWith(".webp")) return false;
        try {
            int number = Integer.parseInt(fileName.substring(0, fileName.length() - 5));
            return number >= 1 && number <= pack.stickerCount;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (matcher.match(uri)) {
            case METADATA_ALL:
                return "vnd.android.cursor.dir/vnd." + authority + ".metadata";
            case METADATA_ONE:
                return "vnd.android.cursor.item/vnd." + authority + ".metadata";
            case STICKERS_FOR_PACK:
                return "vnd.android.cursor.dir/vnd." + authority + ".stickers";
            case STICKER_ASSET:
                return uri.getLastPathSegment().endsWith(".png") ? "image/png" : "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
