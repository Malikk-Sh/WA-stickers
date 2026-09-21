package com.malikksh.wastickers;

import android.content.Intent;

final class MediaPickerIntentFactory {
    private static final String[] ANIMATED_MIME_TYPES = new String[]{
            "image/gif",
            "image/webp",
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-matroska",
            "video/*"
    };

    private static final String[] UNIFIED_MIME_TYPES = new String[]{
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-matroska",
            "video/*"
    };

    private MediaPickerIntentFactory() {}

    static Intent createOpenDocumentIntent(boolean animated) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(animated ? "*/*" : "image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (animated) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, ANIMATED_MIME_TYPES.clone());
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    static Intent createGetContentFallback(boolean animated) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(animated ? "*/*" : "image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (animated) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, ANIMATED_MIME_TYPES.clone());
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    static Intent createUnifiedOpenDocumentIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, UNIFIED_MIME_TYPES.clone());
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    static Intent createSystemMediaPicker() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            intent.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, 30);
            return intent;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    static Intent createUnifiedGetContentFallback() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
}
