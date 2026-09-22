"""Reassemble instrumented screenshots from UTP's retained per-test logcat."""
import base64
import re
from pathlib import Path

root = Path('app/build/outputs/androidTest-results/connected')
chunks = {}
counts = {}
pattern = re.compile(r'V6_SCREEN\s*:\s*([a-z0-9-]+) (\d+) (\d+) ([A-Za-z0-9+/=]+)')
for log in root.rglob('logcat-*.txt'):
    for name, index, count, payload in pattern.findall(log.read_text(errors='replace')):
        chunks.setdefault(name, {})[int(index)] = payload
        counts[name] = int(count)
output = root / 'v6-screenshots'
output.mkdir(exist_ok=True)
for name in ('editor-30', 'build-overview', 'build-files'):
    parts = chunks.get(name, {})
    count = counts.get(name, 0)
    if not count or set(parts) != set(range(count)):
        raise RuntimeError(f'Incomplete screenshot {name}: {len(parts)}/{count} chunks')
    png = base64.b64decode(''.join(parts[i] for i in range(count)), validate=True)
    if not png.startswith(b'\x89PNG\r\n\x1a\n'):
        raise RuntimeError(f'Invalid PNG: {name}')
    (output / f'{name}.png').write_bytes(png)
    print(f'Saved {name}.png ({len(png)} bytes)')
