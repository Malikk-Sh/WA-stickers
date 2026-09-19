from pathlib import Path

ROOT = Path('app/src')
TARGET = 'MainActivityRuntimeAccess.'

METHODS = {
    'packName': 'runtimePackName',
    'photoModeButton': 'runtimePhotoModeButton',
    'animatedModeButton': 'runtimeAnimatedModeButton',
    'isAnimatedMode': 'runtimeIsAnimatedMode',
    'isProcessing': 'runtimeIsProcessing',
    'coverUri': 'runtimeCoverUri',
    'selectedUrisSnapshot': 'runtimeSelectedUrisSnapshot',
    'editorStateController': 'runtimeEditorStateController',
    'enteredPackName': 'runtimeEnteredPackName',
    'buildSession': 'runtimeBuildSession',
    'currentPack': 'runtimeCurrentPack',
    'replaceCurrentPackIfId': 'runtimeReplaceCurrentPackIfId',
    'clearCurrentPackIfId': 'runtimeClearCurrentPackIfId',
    'statusText': 'runtimeStatusText',
    'progressText': 'runtimeProgressText',
    'fileProgressLabelsSnapshot': 'runtimeFileProgressLabelsSnapshot',
    'fileProgressBarsSnapshot': 'runtimeFileProgressBarsSnapshot',
    'fileProgressNamesSnapshot': 'runtimeFileProgressNamesSnapshot',
    'installRuntimeControls': 'runtimeInstallControls',
    'receivePickerResult': 'runtimeReceivePickerResult',
    'setGalleryClickListener': 'runtimeSetGalleryClickListener',
    'setAnimatedMode': 'runtimeSetAnimatedMode',
    'moveSticker': 'runtimeMoveSticker',
    'selectCover': 'runtimeSelectCover',
    'removeMediaAt': 'runtimeRemoveMediaAt',
    'clearMedia': 'runtimeClearMedia',
    'clearEditorDraft': 'runtimeClearEditorDraft',
    'restoreEditorState': 'runtimeRestoreEditorState',
    'resetDiagnostics': 'runtimeResetDiagnostics',
    'invalidateCurrentPack': 'runtimeInvalidateCurrentPack',
    'startBatch': 'runtimeStartBatch',
    'updateUiState': 'runtimeUpdateUiState',
    'cancelProcessing': 'runtimeCancelProcessing',
    'finalizePendingPack': 'runtimeFinalizePendingPack',
    'discardPendingBuild': 'runtimeDiscardPendingBuild',
    'addCurrentPackToWhatsApp': 'runtimeAddCurrentPackToWhatsApp',
}


def split_args(source: str):
    args = []
    start = 0
    depth = 0
    quote = None
    escape = False
    for i, ch in enumerate(source):
        if quote is not None:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
        elif ch in '([{':
            depth += 1
        elif ch in ')]}':
            depth -= 1
        elif ch == ',' and depth == 0:
            args.append(source[start:i].strip())
            start = i + 1
    tail = source[start:].strip()
    if tail:
        args.append(tail)
    return args


def find_close(text: str, open_index: int):
    depth = 0
    quote = None
    escape = False
    for i in range(open_index, len(text)):
        ch = text[i]
        if quote is not None:
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == quote:
                quote = None
            continue
        if ch in ('"', "'"):
            quote = ch
            continue
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0:
                return i
    raise SystemExit('Unbalanced MainActivityRuntimeAccess call')


def migrate_calls(text: str, path: Path):
    cursor = 0
    while True:
        start = text.find(TARGET, cursor)
        if start < 0:
            break
        method_start = start + len(TARGET)
        open_index = text.find('(', method_start)
        if open_index < 0:
            raise SystemExit(f'{path}: malformed runtime access reference')
        method = text[method_start:open_index].strip()
        if method == 'refreshAppShell':
            mapped = None
        else:
            mapped = METHODS.get(method)
            if mapped is None:
                raise SystemExit(f'{path}: unknown runtime access method {method}')
        close_index = find_close(text, open_index)
        args = split_args(text[open_index + 1:close_index])
        if not args:
            raise SystemExit(f'{path}: {method} has no receiver')
        receiver = args[0]
        rest = args[1:]
        if method == 'refreshAppShell':
            if rest:
                raise SystemExit(f'{path}: refreshAppShell unexpectedly has extra arguments')
            replacement = f'{receiver}.refreshShellState()'
        else:
            replacement = f'{receiver}.{mapped}(' + ', '.join(rest) + ')'
        text = text[:start] + replacement + text[close_index + 1:]
        cursor = start + len(replacement)
    return text


changed = []
remaining = []
for path in ROOT.rglob('*.java'):
    if path.name == 'MainActivityRuntimeAccess.java':
        continue
    original = path.read_text()
    text = migrate_calls(original, path)
    # Update migration-era prose only after all executable references have been rewritten.
    text = text.replace(
        'Runtime access is delegated to MainActivityRuntimeAccess so persistence owns serialization only;',
        'Runtime access uses MainActivity\'s package-private typed surface so persistence owns serialization only;'
    )
    text = text.replace(
        'MainActivityRuntimeAccess',
        'MainActivity typed runtime surface'
    )
    if 'MainActivityRuntimeAccess' in text:
        remaining.append(path)
    if text != original:
        path.write_text(text)
        changed.append(path)

if remaining:
    raise SystemExit('Unmigrated facade references: ' + ', '.join(map(str, remaining)))
if not changed:
    raise SystemExit('No runtime facade callers were migrated')

print(f'Migrated {len(changed)} Java files away from MainActivityRuntimeAccess')
for path in changed:
    print(path)
