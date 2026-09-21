package com.malikksh.wastickers;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** Whitelist confirms installation; successful enable result acknowledges the offered generation. */
final class WhatsAppSync {
    enum State { NOT_ADDED, ADDED_SYNCED, ADDED_LOCAL_CHANGES, SYNCING, SYNC_ERROR }
    private static final String PREFS = "whatsapp_sync";
    private static final String[] PACKAGES = {"com.whatsapp", "com.whatsapp.w4b"};
    static State state(Context context, PackStore.Pack pack) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String value = prefs.getString(pack.id + ".state", State.NOT_ADDED.name());
        State state;
        try { state = State.valueOf(value); } catch (IllegalArgumentException error) { state = State.NOT_ADDED; }
        if (state == State.ADDED_SYNCED && !pack.imageDataVersion.equals(prefs.getString(pack.id + ".version", "")))
            return State.ADDED_LOCAL_CHANGES;
        return state;
    }
    static String label(Context context, PackStore.Pack pack) {
        switch (state(context, pack)) {
            case ADDED_SYNCED: return "✓ В WhatsApp";
            case ADDED_LOCAL_CHANGES: return "Обновить в WhatsApp";
            case SYNCING: return "Обновление…";
            case SYNC_ERROR: return "Повторить в WhatsApp";
            default: return "Добавить в WhatsApp";
        }
    }
    static void set(Context context, String id, State state, String version) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, 0).edit()
                .putString(id + ".state", state.name());
        if (version != null) editor.putString(id + ".version", version);
        editor.commit();
        PackStore.notifyChanged(context);
    }
    // Null means unsupported/unavailable, not proof that the pack was removed.
    private static Boolean whitelisted(Context context, String pkg, String id) {
        Uri uri = new Uri.Builder().scheme("content").authority(pkg + ".provider.sticker_whitelist_check")
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", context.getPackageName() + ".stickercontentprovider")
                .appendQueryParameter("identifier", id).build();
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            int index = cursor.getColumnIndex("result");
            return index < 0 ? null : cursor.getInt(index) == 1;
        } catch (RuntimeException error) { return null; }
    }
    static void reconcile(Context context, PackStore.Pack pack) {
        boolean added = false;
        boolean supported = false;
        for (String pkg : PACKAGES) {
            Boolean result = whitelisted(context, pkg, pack.id);
            if (result != null) { supported = true; added |= result; }
        }
        State before = state(context, pack);
        if (before == State.SYNCING || before == State.SYNC_ERROR) return;
        if (added && before == State.NOT_ADDED) set(context, pack.id, State.ADDED_LOCAL_CHANGES, null);
        else if (supported && !added && before != State.NOT_ADDED) set(context, pack.id, State.NOT_ADDED, null);
    }
}
