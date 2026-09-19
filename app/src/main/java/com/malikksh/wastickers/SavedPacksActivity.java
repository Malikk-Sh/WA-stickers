package com.malikksh.wastickers;

import android.app.Activity;
import android.os.Bundle;

/**
 * Temporary source-compatibility shim for the migration fallback in AppShellActivity.
 *
 * The standalone saved-packs screen is no longer registered in any manifest; the redesigned
 * Packs tab is the only user-facing route. Remove this class once the fallback reference is gone.
 */
public class SavedPacksActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
