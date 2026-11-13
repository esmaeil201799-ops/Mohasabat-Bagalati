#!/usr/bin/env python3
from pathlib import Path
import re, sys, zipfile
root = Path(__file__).resolve().parent
html = root / 'app/src/main/assets/index.html'
assert html.is_file(), 'index.html missing'
s = html.read_text(encoding='utf-8')
assert 'محاسبة بقالتي' in s
assert "AndroidBridge.exportBackup" in s, 'Android JSON backup bridge not connected to HTML'
assert 'localStorage' in s, 'local storage not found'
ids = re.findall(r'\bid=["\']([^"\']+)["\']', s)
dupes = sorted({x for x in ids if ids.count(x) > 1})
if dupes: print('Duplicate HTML ids:', dupes); sys.exit(2)
assert (root / 'app/src/main/AndroidManifest.xml').is_file()
assert (root / '.github/workflows/build-apk.yml').is_file()
print('PASS: index.html exists')
print('PASS: app label/source marker present')
print('PASS: Android JSON backup bridge is wired')
print('PASS: localStorage marker present')
print(f'PASS: {len(ids)} HTML IDs, no duplicates')
print('NOTE: This is a source preflight only; it does not compile Android or validate runtime behavior.')
