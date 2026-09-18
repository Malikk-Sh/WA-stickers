package com.malikksh.wastickers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

import java.io.File;

final class AppSettings {
    static final String APPEARANCE_SYSTEM = "system";
    static final String APPEARANCE_LIGHT = "light";
    static final String APPEARANCE_DARK = "dark";

    static final String QUALITY_SMOOTHER = "smoother";
    static final String QUALITY_BALANCE = "balance";
    static final String QUALITY_SHARPER = "sharper";

    static final String TRIM_PERSISTENT_KEY = "home_video_trim";

    private static final String PREFS = "app_settings";
    private static final String KEY_APPEARANCE = "appearance";
    private static final String KEY_COMPACT = "compact";
    private static final String KEY_QUALITY = "animation_quality";
    private static final String KEY_KEEP_DRAFTS = "keep_drafts";
    private static final String KEY_AUTO_CLEANUP = "auto_cleanup";

    private AppSettings() {}

    static String appearance(Context context) {
        return prefs(context).getString(KEY_APPEARANCE, APPEARANCE_SYSTEM);
    }

    static void setAppearance(Context context, String value) {
        String safe = APPEARANCE_SYSTEM;
        if (APPEARANCE_LIGHT.equals(value)) safe = APPEARANCE_LIGHT;
        if (APPEARANCE_DARK.equals(value)) safe = APPEARANCE_DARK;
        prefs(context).edit().putString(KEY_APPEARANCE, safe).apply();
    }

    static boolean compactMode(Context context) {
        return prefs(context).getBoolean(KEY_COMPACT, false);
    }

    static void setCompactMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_COMPACT, enabled).apply();
    }

    static String qualityPreset(Context context) {
        return prefs(context).getString(KEY_QUALITY, QUALITY_BALANCE);
    }

    static void setQualityPreset(Context context, String value) {
        String safe = QUALITY_BALANCE;
        if (QUALITY_SMOOTHER.equals(value)) safe = QUALITY_SMOOTHER;
        if (QUALITY_SHARPER.equals(value)) safe = QUALITY_SHARPER;
        prefs(context).edit().putString(KEY_QUALITY, safe).apply();
        AnimatedStickerProfiles.setPreset(safe);
        clearConversionCache(context);
    }

    static boolean keepDrafts(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_DRAFTS, true);
    }

    static void setKeepDrafts(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_DRAFTS, enabled).apply();
        if (!enabled) {
            EditorInstanceStateBridge.clearPersistent(context);
            VideoTrimStore.clearPersistent(context, TRIM_PERSISTENT_KEY);
        }
    }

    static boolean autoCleanup(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CLEANUP, true);
    }

    static void setAutoCleanup(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLEANUP, enabled).apply();
        if (enabled) cleanupTemporaryFiles(context);
    }

    static Context wrapForAppearance(Context base) {
        if (base == null) return null;
        String appearance = appearance(base);
        if (APPEARANCE_SYSTEM.equals(appearance)) return base;

        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int night = APPEARANCE_DARK.equals(appearance)
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        return base.createConfigurationContext(configuration);
    }

    static boolean isDark(Context context) {
        if (context == null) return false;
        int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    static void configureRuntime(Context context) {
        AnimatedStickerProfiles.setPreset(qualityPreset(context));
        if (autoCleanup(context)) cleanupTemporaryFiles(context);
    }

    @SuppressWarnings("deprecation")
    static void applySystemBars(Activity activity) {
        if (activity == null) return;
        activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.app_background));
        activity.getWindow().setNavigationBarColor(activity.getResources().getColor(R.color.app_surface));
        int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
        boolean dark = isDark(activity);
        if (Build.VERSION.SDK_INT >= 23) {
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (dark) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    static long clearConversionCache(Context context) {
        if (context == null) return 0L;
        File root = context.getCacheDir();
        File[] children = root == null ? null : root.listFiles();
        if (children == null) return 0L;
        long freed = 0L;
        for (File child : children) {
            if (child == null || !child.getName().startsWith("sticker_conversion_cache_v")) continue;
            freed += sizeOf(child);
            deleteRecursively(child);
        }
        return freed;
    }

    static long cleanupTemporaryFiles(Context context) {
        if (context == null) return 0L;
        File root = context.getCacheDir();
        if (root == null) return 0L;
        long freed = 0L;
        String[] disposable = {"animated_sticker_work"};
        for (String name : disposable) {
            File item = new File(root, name);
            if (!item.exists()) continue;
            freed += sizeOf(item);
            deleteRecursively(item);
        }
        return freed;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += sizeOf(child);
        }
        return total;
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