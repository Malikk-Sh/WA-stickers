package com.malikksh.wastickers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaPickerIntentFactoryTest {

    @Test
    public void animatedOpenDocumentPickerAllowsMultipleFiles() {
        Intent intent = MediaPickerIntentFactory.createOpenDocumentIntent(true);

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.getAction());
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertEquals("*/*", intent.getType());
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0);
        assertArrayEquals(
                new String[]{
                        "image/gif",
                        "image/webp",
                        "video/mp4",
                        "video/webm",
                        "video/quicktime",
                        "video/x-matroska",
                        "video/*"
                },
                intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        );
    }

    @Test
    public void animatedFallbackAlsoAllowsMultipleFiles() {
        Intent intent = MediaPickerIntentFactory.createGetContentFallback(true);

        assertEquals(Intent.ACTION_GET_CONTENT, intent.getAction());
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertEquals("*/*", intent.getType());
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
    }
}
