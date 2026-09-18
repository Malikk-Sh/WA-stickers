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
}
