from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/malikksh/wastickers/MainActivity.java"

text = MAIN.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one occurrence, found {count}: {old[:90]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private final List<Uri> selectedUris = new ArrayList<>();\n",
    "    private final EditorRuntimeState<Uri> editorState = new EditorRuntimeState<>();\n",
)
replace_once(
    "    private final EditorStateController<Uri> editorStateController = new EditorStateController<>();\n",
    "",
)
replace_once("    private boolean animatedMode;\n", "")
replace_once("    private Uri coverUri;\n", "")

replace_once(
'''    private void setAnimatedMode(boolean animated) {
        if (processing || animatedMode == animated) return;
        String currentName = packName == null ? "" : packName.getText().toString();
        EditorStateController.Snapshot<Uri> next = editorStateController.switchMode(
                animatedMode,
                animated,
                selectedUris,
                coverUri,
                currentName
        );
        invalidateCurrentPack();
        animatedMode = animated;
        selectedUris.clear();
        selectedUris.addAll(next.items());
        coverUri = next.cover();
        if (packName != null) packName.setText(next.name());
        renderPreviews();
        updateModeUi();
        updateUiState();
    }
''',
'''    private void setAnimatedMode(boolean animated) {
        if (processing || editorState.isAnimated() == animated) return;
        String currentName = packName == null ? "" : packName.getText().toString();
        EditorStateController.Snapshot<Uri> next = editorState.switchMode(animated, currentName);
        invalidateCurrentPack();
        if (packName != null) packName.setText(next.name());
        renderPreviews();
        updateModeUi();
        updateUiState();
    }
''')

replace_once(
'''        boolean changed = false;
        for (Uri uri : incoming) {
            if (selectedUris.size() >= MAX_STICKERS) break;
            if (!selectedUris.contains(uri)) {
                selectedUris.add(uri);
                changed = true;
            }
            if (persistPermission) {
''',
'''        boolean changed = false;
        for (Uri uri : incoming) {
            if (editorState.items().size() >= MAX_STICKERS) break;
            if (editorState.addUnique(uri, MAX_STICKERS)) changed = true;
            if (persistPermission) {
''')
replace_once(
    "        if (coverUri == null && !selectedUris.isEmpty()) coverUri = selectedUris.get(0);\n",
    "        editorState.ensureCover();\n",
)
replace_once(
'''        if (selectedUris.isEmpty()) {
            coverUri = null;
''',
'''        if (editorState.items().isEmpty()) {
            editorState.ensureCover();
''')
replace_once(
    "        if (coverUri == null || !selectedUris.contains(coverUri)) coverUri = selectedUris.get(0);\n",
    "        editorState.ensureCover();\n",
)
replace_once(
'''            cover.setOnClickListener(v -> {
                if (processing || uri.equals(coverUri)) return;
                coverUri = uri;
                invalidateCurrentPack();
                renderPreviews();
                updateUiState();
            });
''',
'''            cover.setOnClickListener(v -> {
                if (processing || uri.equals(editorState.cover()) || !editorState.selectCover(uri)) return;
                invalidateCurrentPack();
                renderPreviews();
                updateUiState();
            });
''')
replace_once(
'''            remove.setOnClickListener(v -> {
                if (!processing && index >= 0 && index < selectedUris.size()) {
                    Uri removed = selectedUris.remove(index);
                    if (removed.equals(coverUri)) coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
                    invalidateCurrentPack();
                    renderPreviews();
                    updateUiState();
                }
            });
''',
'''            remove.setOnClickListener(v -> {
                if (!processing && editorState.removeAt(index)) {
                    invalidateCurrentPack();
                    renderPreviews();
                    updateUiState();
                }
            });
''')
replace_once(
    "        if (processing || !StickerOrderPolicy.move(selectedUris, fromIndex, toIndex)) return;\n",
    "        if (processing || !editorState.move(fromIndex, toIndex)) return;\n",
)

# Typed migration surface introduced in the previous architecture slice.
replace_once("        return animatedMode;\n", "        return editorState.isAnimated();\n")
replace_once("        return coverUri;\n", "        return editorState.cover();\n")
replace_once("        return new ArrayList<>(selectedUris);\n", "        return editorState.snapshotItems();\n")
replace_once("        return editorStateController;\n", "        return editorState.drafts();\n")

replace_once(
'''    boolean runtimeSelectCover(Uri uri) {
        if (uri == null || processing || !selectedUris.contains(uri)) return false;
        coverUri = uri;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''',
'''    boolean runtimeSelectCover(Uri uri) {
        if (processing || !editorState.selectCover(uri)) return false;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''')
replace_once(
'''    boolean runtimeRemoveMediaAt(int index) {
        if (processing || index < 0 || index >= selectedUris.size()) return false;
        Uri removed = selectedUris.remove(index);
        if (removed != null && removed.equals(coverUri)) {
            coverUri = selectedUris.isEmpty() ? null : selectedUris.get(0);
        }
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''',
'''    boolean runtimeRemoveMediaAt(int index) {
        if (processing || !editorState.removeAt(index)) return false;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''')
replace_once(
'''    boolean runtimeClearMedia() {
        if (processing || selectedUris.isEmpty()) return false;
        selectedUris.clear();
        coverUri = null;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''',
'''    boolean runtimeClearMedia() {
        if (processing || !editorState.clearSelection()) return false;
        invalidateCurrentPack();
        renderPreviews();
        updateUiState();
        return true;
    }
''')
replace_once(
'''        try {
            selectedUris.clear();
            coverUri = null;
            if (packName != null) packName.setText("");
            editorStateController.capture(false, new ArrayList<>(), null, "");
            editorStateController.capture(true, new ArrayList<>(), null, "");
''',
'''        try {
            editorState.clearSelection();
            if (packName != null) packName.setText("");
            editorState.drafts().capture(false, new ArrayList<>(), null, "");
            editorState.drafts().capture(true, new ArrayList<>(), null, "");
''')
replace_once(
'''        try {
            animatedMode = animated;
            selectedUris.clear();
            if (items != null) selectedUris.addAll(items);
            coverUri = cover;
            if (packName != null) packName.setText(name == null ? "" : name);
''',
'''        try {
            editorState.restoreActive(animated, items, cover);
            if (packName != null) packName.setText(name == null ? "" : name);
''')

# All remaining references are reads; route them through the state owner.
text = re.sub(r"\bselectedUris\b", "editorState.items()", text)
text = re.sub(r"\banimatedMode\b", "editorState.isAnimated()", text)
text = re.sub(r"\bcoverUri\b", "editorState.cover()", text)
text = re.sub(r"\beditorStateController\b", "editorState.drafts()", text)

invalid_patterns = [
    r"editorState\.items\(\)\.(?:add|addAll|clear|remove)\(",
    r"editorState\.isAnimated\(\)\s*=",
    r"editorState\.cover\(\)\s*=",
    r"editorState\.drafts\(\)\s*=",
    r"\bselectedUris\b",
    r"\banimatedMode\b(?!Button)",
    r"\bcoverUri\b",
    r"\beditorStateController\b",
]
for pattern in invalid_patterns:
    if re.search(pattern, text):
        raise SystemExit(f"Unsafe/unmigrated editor-state pattern remains: {pattern}")

MAIN.write_text(text)
print("MainActivity now uses EditorRuntimeState")
