from pathlib import Path

path = Path("app/src/main/java/com/malikksh/wastickers/MainActivity.java")
text = path.read_text()


def replace_once(old, new):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one occurrence, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private PackStore.Pack currentPack;\n    private boolean processing;",
    "    private final PackRuntimeState packState = new PackRuntimeState();\n    private boolean processing;",
)
replace_once(
    "        currentPack = null;\n        updateModeUi();",
    "        packState.clear();\n        updateModeUi();",
)
replace_once(
    "        currentPack = null;\n        lastOperationFailed = false;",
    "        packState.clear();\n        lastOperationFailed = false;",
)
replace_once(
    "                boolean packMatchesMode = currentPack != null && currentPack.animated == editorState.isAnimated();",
    "                boolean packMatchesMode = packState.matchesMode(editorState.isAnimated());",
)
replace_once(
    "        if (statusText != null && !processing && !lastOperationFailed) {\n            if (currentPack != null && currentPack.animated == editorState.isAnimated()) {",
    "        if (statusText != null && !processing && !lastOperationFailed) {\n            PackStore.Pack currentPack = packState.current();\n            if (currentPack != null && currentPack.animated == editorState.isAnimated()) {",
)
replace_once(
    "        PackStore.addPack(this, pack);\n        currentPack = pack;",
    "        PackStore.addPack(this, pack);\n        packState.set(pack);",
)
replace_once(
    "    private void addCurrentPackToWhatsApp() {\n        if (currentPack == null) {",
    "    private void addCurrentPackToWhatsApp() {\n        PackStore.Pack currentPack = packState.current();\n        if (currentPack == null) {",
)
# The two invalidation branches in addCurrentPackToWhatsApp both clear and immediately return.
if text.count("            currentPack = null;\n            updateUiState();") != 2:
    raise SystemExit("Expected two current-pack invalidation branches in WhatsApp action")
text = text.replace(
    "            currentPack = null;\n            updateUiState();",
    "            packState.clear();\n            updateUiState();",
    2,
)
replace_once(
    "    PackStore.Pack runtimeCurrentPack() {\n        return currentPack;\n    }\n\n    boolean runtimeReplaceCurrentPackIfId(String expectedId, PackStore.Pack replacement) {\n        if (expectedId == null || replacement == null || currentPack == null\n                || !expectedId.equals(currentPack.id)) {\n            return false;\n        }\n        currentPack = replacement;\n        return true;\n    }\n\n    boolean runtimeClearCurrentPackIfId(String expectedId) {\n        if (expectedId == null || currentPack == null || !expectedId.equals(currentPack.id)) {\n            return false;\n        }\n        currentPack = null;\n        return true;\n    }",
    "    PackStore.Pack runtimeCurrentPack() {\n        return packState.current();\n    }\n\n    boolean runtimeReplaceCurrentPackIfId(String expectedId, PackStore.Pack replacement) {\n        return packState.replaceIfId(expectedId, replacement);\n    }\n\n    boolean runtimeClearCurrentPackIfId(String expectedId) {\n        return packState.clearIfId(expectedId);\n    }",
)
replace_once(
    "            currentPack = restoredPack;\n            renderPreviews();",
    "            packState.set(restoredPack);\n            renderPreviews();",
)

if "private PackStore.Pack currentPack;" in text:
    raise SystemExit("MainActivity still declares currentPack")

path.write_text(text)
print("MainActivity current-pack ownership migrated to PackRuntimeState")
