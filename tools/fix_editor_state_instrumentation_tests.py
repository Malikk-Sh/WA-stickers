from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / "app/src/androidTest/java/com/malikksh/wastickers"


def rewrite(name, replacements):
    path = TESTS / name
    text = path.read_text()
    for old, new in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{name}: expected one occurrence of {old[:80]!r}, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)


rewrite("BuildShellActivityUiTest.java", [
    (
'''    @SuppressWarnings("unchecked")
    private static void seedSelection(BuildShellActivity activity,
                                      List<Uri> items,
                                      String name) throws Exception {
        List<Uri> selected = (List<Uri>) getMainField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setMainField(activity, "animatedMode", false);
        setMainField(activity, "coverUri", items.isEmpty() ? null : items.get(0));
        EditText packName = (EditText) getMainField(activity, "packName");
        packName.setText(name);
    }

    @SuppressWarnings("unchecked")
    private static PackBuildSession<Uri> buildSession(BuildShellActivity activity) throws Exception {
        return (PackBuildSession<Uri>) getMainField(activity, "buildSession");
    }

    private static Object getMainField(BuildShellActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }
''',
'''    private static void seedSelection(BuildShellActivity activity,
                                      List<Uri> items,
                                      String name) {
        MainActivityRuntimeAccess.restoreEditorState(
                activity,
                false,
                items,
                items.isEmpty() ? null : items.get(0),
                name,
                null
        );
    }

    private static PackBuildSession<Uri> buildSession(BuildShellActivity activity) {
        return MainActivityRuntimeAccess.buildSession(activity);
    }
'''),
    ("import android.widget.EditText;\n", ""),
])

rewrite("HandoffComplianceUiTest.java", [
    (
'''    @SuppressWarnings("unchecked")
    private static void seedSelection(SettingsShellActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      String name) throws Exception {
        List<Uri> selected = (List<Uri>) readMainField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        writeMainField(activity, "animatedMode", false);
        writeMainField(activity, "coverUri", cover);
        EditText packName = (EditText) readMainField(activity, "packName");
        packName.setText(name);
        MainActivityRuntimeAccess.updateUiState(activity);
    }

    private static Object readMainField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void writeMainField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }
''',
'''    private static void seedSelection(SettingsShellActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      String name) {
        MainActivityRuntimeAccess.restoreEditorState(activity, false, items, cover, name, null);
    }
'''),
    ("import android.widget.EditText;\n", ""),
    ("import java.lang.reflect.Field;\n", ""),
])

rewrite("LauncherActivityStateUiTest.java", [
    (
'''            scenario.onActivity(activity -> {
                try {
                    ((EditText) getField(activity, "packName")).setText("Фото-черновик");
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                    ((EditText) getField(activity, "packName")).setText("Анимация-черновик");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            scenario.onActivity(activity -> {
                MainActivityRuntimeAccess.packName(activity).setText("Фото-черновик");
                MainActivityRuntimeAccess.setAnimatedMode(activity, true);
                MainActivityRuntimeAccess.packName(activity).setText("Анимация-черновик");
            });
'''),
    (
'''            scenario.onActivity(activity -> {
                try {
                    assertTrue((Boolean) getField(activity, "animatedMode"));
                    assertEquals("Анимация-черновик", packName(activity));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertFalse((Boolean) getField(activity, "animatedMode"));
                    assertEquals("Фото-черновик", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            scenario.onActivity(activity -> {
                assertTrue(MainActivityRuntimeAccess.isAnimatedMode(activity));
                assertEquals("Анимация-черновик", packName(activity));
                MainActivityRuntimeAccess.setAnimatedMode(activity, false);
                assertFalse(MainActivityRuntimeAccess.isAnimatedMode(activity));
                assertEquals("Фото-черновик", packName(activity));
            });
'''),
    (
'''            scenario.onActivity(activity -> {
                try {
                    seedCurrentEditor(activity, photos, photos.get(1), "Фото-порядок");
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                    seedCurrentEditor(activity, animated, animated.get(2), "Анимация-порядок");
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            scenario.onActivity(activity -> {
                seedCurrentEditor(activity, photos, photos.get(1), "Фото-порядок");
                MainActivityRuntimeAccess.setAnimatedMode(activity, true);
                seedCurrentEditor(activity, animated, animated.get(2), "Анимация-порядок");
            });
'''),
    (
'''            scenario.onActivity(activity -> {
                try {
                    assertEquals(animated, selectionSnapshot(activity));
                    assertEquals(animated.get(2), getField(activity, "coverUri"));
                    assertEquals("Анимация-порядок", packName(activity));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertEquals(photos, selectionSnapshot(activity));
                    assertEquals(photos.get(1), getField(activity, "coverUri"));
                    assertEquals("Фото-порядок", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            scenario.onActivity(activity -> {
                assertEquals(animated, selectionSnapshot(activity));
                assertEquals(animated.get(2), MainActivityRuntimeAccess.coverUri(activity));
                assertEquals("Анимация-порядок", packName(activity));
                MainActivityRuntimeAccess.setAnimatedMode(activity, false);
                assertEquals(photos, selectionSnapshot(activity));
                assertEquals(photos.get(1), MainActivityRuntimeAccess.coverUri(activity));
                assertEquals("Фото-порядок", packName(activity));
            });
'''),
    (
'''        first.onActivity(activity -> {
            try {
                seedCurrentEditor(activity, photos, photos.get(1), "Фото после рестарта");
                invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, true);
                seedCurrentEditor(activity, animated, video, "Анимация после рестарта");
                VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                        video.toString(), video, "long-video.mp4", 30_000L, 7_000L)));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
''',
'''        first.onActivity(activity -> {
            seedCurrentEditor(activity, photos, photos.get(1), "Фото после рестарта");
            MainActivityRuntimeAccess.setAnimatedMode(activity, true);
            seedCurrentEditor(activity, animated, video, "Анимация после рестарта");
            VideoTrimStore.replaceEntries(Arrays.asList(new VideoTrimStore.Entry(
                    video.toString(), video, "long-video.mp4", 30_000L, 7_000L)));
        });
'''),
    (
'''            second.onActivity(activity -> {
                try {
                    assertTrue((Boolean) getField(activity, "animatedMode"));
                    assertEquals(animated, selectionSnapshot(activity));
                    assertEquals(video, getField(activity, "coverUri"));
                    assertEquals("Анимация после рестарта", packName(activity));
                    assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video));
                    invoke(activity, "setAnimatedMode", new Class<?>[]{boolean.class}, false);
                    assertEquals(photos, selectionSnapshot(activity));
                    assertEquals(photos.get(1), getField(activity, "coverUri"));
                    assertEquals("Фото после рестарта", packName(activity));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            second.onActivity(activity -> {
                assertTrue(MainActivityRuntimeAccess.isAnimatedMode(activity));
                assertEquals(animated, selectionSnapshot(activity));
                assertEquals(video, MainActivityRuntimeAccess.coverUri(activity));
                assertEquals("Анимация после рестарта", packName(activity));
                assertEquals(7_000L, VideoTrimStore.getStartOffsetMs(video));
                MainActivityRuntimeAccess.setAnimatedMode(activity, false);
                assertEquals(photos, selectionSnapshot(activity));
                assertEquals(photos.get(1), MainActivityRuntimeAccess.coverUri(activity));
                assertEquals("Фото после рестарта", packName(activity));
            });
'''),
    (
'''    @SuppressWarnings("unchecked")
    private static void seedCurrentEditor(
            MainActivity activity,
            List<Uri> items,
            Uri cover,
            String name
    ) throws Exception {
        List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setField(activity, "coverUri", cover);
        ((EditText) getField(activity, "packName")).setText(name);
        invoke(activity, "renderPreviews", new Class<?>[0]);
        invoke(activity, "updateUiState", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> selectionSnapshot(MainActivity activity) throws Exception {
        return new ArrayList<>((List<Uri>) getField(activity, "selectedUris"));
    }

    private static String packName(MainActivity activity) throws Exception {
        return ((EditText) getField(activity, "packName")).getText().toString();
    }
''',
'''    private static void seedCurrentEditor(
            MainActivity activity,
            List<Uri> items,
            Uri cover,
            String name
    ) {
        MainActivityRuntimeAccess.restoreEditorState(
                activity,
                MainActivityRuntimeAccess.isAnimatedMode(activity),
                items,
                cover,
                name,
                MainActivityRuntimeAccess.currentPack(activity)
        );
    }

    private static List<Uri> selectionSnapshot(MainActivity activity) {
        return MainActivityRuntimeAccess.selectedUrisSnapshot(activity);
    }

    private static String packName(MainActivity activity) {
        return MainActivityRuntimeAccess.packName(activity).getText().toString();
    }
'''),
    (
'''    private static Object getField(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static Object invoke(
            MainActivity activity,
            String name,
            Class<?>[] parameterTypes,
            Object... args
    ) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

''',
""),
    ("import android.widget.EditText;\n", ""),
    ("import java.lang.reflect.Field;\n", ""),
    ("import java.lang.reflect.Method;\n", ""),
    ("import java.util.ArrayList;\n", ""),
])

rewrite("MainActivityBatchUiTest.java", [
    (
'''                    seedSelection(activity, sources, sources.get(0), false);
                    setField(activity, "processing", true);
                    invoke(activity, "updateUiState");
''',
'''                    seedSelection(activity, sources, sources.get(0), false);
                    setField(activity, "processing", true);
                    MainActivityRuntimeAccess.updateUiState(activity);
'''),
    (
'''                    seedSelection(activity, sources, sources.get(0), false);
                    invoke(activity, "moveSticker", new Class<?>[]{int.class, int.class}, 0, 2);
''',
'''                    seedSelection(activity, sources, sources.get(0), false);
                    MainActivityRuntimeAccess.moveSticker(activity, 0, 2);
'''),
    (
'''                try {
                    assertEquals(original.get(2), getField(activity, "coverUri"));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
''',
'''                assertEquals(original.get(2), MainActivityRuntimeAccess.coverUri(activity));
'''),
    (
'''            scenario.onActivity(activity -> {
                try {
                    processing.set((Boolean) getField(activity, "processing"));
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
''',
'''            scenario.onActivity(activity -> processing.set(
                    MainActivityRuntimeAccess.isProcessing(activity)));
'''),
    (
'''    @SuppressWarnings("unchecked")
    private static void seedSelection(MainActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      boolean animated) throws Exception {
        invoke(activity, "invalidateCurrentPack");
        List<Uri> selected = (List<Uri>) getField(activity, "selectedUris");
        selected.clear();
        selected.addAll(items);
        setField(activity, "animatedMode", animated);
        setField(activity, "coverUri", cover);
        invoke(activity, "renderPreviews");
        invoke(activity, "updateModeUi");
        invoke(activity, "updateUiState");
    }

    @SuppressWarnings("unchecked")
    private static List<Uri> selectionSnapshot(MainActivity activity) throws Exception {
        return new ArrayList<>((List<Uri>) getField(activity, "selectedUris"));
    }

    private static PackBuildSession<?> buildSession(MainActivity activity) {
        try {
            return (PackBuildSession<?>) getField(activity, "buildSession");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }
''',
'''    private static void seedSelection(MainActivity activity,
                                      List<Uri> items,
                                      Uri cover,
                                      boolean animated) {
        MainActivityRuntimeAccess.invalidateCurrentPack(activity);
        MainActivityRuntimeAccess.restoreEditorState(activity, animated, items, cover, "", null);
    }

    private static List<Uri> selectionSnapshot(MainActivity activity) {
        return MainActivityRuntimeAccess.selectedUrisSnapshot(activity);
    }

    private static PackBuildSession<?> buildSession(MainActivity activity) {
        return MainActivityRuntimeAccess.buildSession(activity);
    }
'''),
    (
'''    private static Object invoke(MainActivity activity, String name) throws Exception {
        return invoke(activity, name, new Class<?>[0]);
    }

    private static Object invoke(MainActivity activity,
                                 String name,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

''',
""),
    ("import java.lang.reflect.Method;\n", ""),
    ("import java.util.ArrayList;\n", ""),
])

rewrite("SettingsShellActivityUiTest.java", [
    (
'''                    Uri item = writeImage(activity, "settings_shell_draft.png");
                    @SuppressWarnings("unchecked")
                    List<Uri> selected = (List<Uri>) getMainField(activity, "selectedUris");
                    selected.clear();
                    selected.add(item);
                    setMainField(activity, "coverUri", item);
                    EditorInstanceStateBridge.savePersistent(activity);
''',
'''                    Uri item = writeImage(activity, "settings_shell_draft.png");
                    MainActivityRuntimeAccess.restoreEditorState(
                            activity,
                            false,
                            java.util.Collections.singletonList(item),
                            item,
                            "",
                            null
                    );
                    EditorInstanceStateBridge.savePersistent(activity);
'''),
    (
'''                    @SuppressWarnings("unchecked")
                    List<Uri> cleared = (List<Uri>) getMainField(activity, "selectedUris");
                    assertTrue(cleared.isEmpty());
''',
'''                    assertTrue(MainActivityRuntimeAccess.selectedUrisSnapshot(activity).isEmpty());
'''),
    (
'''    private static Object getMainField(SettingsShellActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static void setMainField(SettingsShellActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static void invokeShellRefresh(SettingsShellActivity activity) throws Exception {
        Method method = AppShellActivity.class.getDeclaredMethod("refreshShellState");
        method.setAccessible(true);
        method.invoke(activity);
    }
''',
'''    private static void invokeShellRefresh(SettingsShellActivity activity) {
        activity.refreshShellState();
    }
'''),
    ("import java.lang.reflect.Field;\n", ""),
    ("import java.util.List;\n", ""),
])

print("Instrumentation tests migrated to typed editor-state access")
