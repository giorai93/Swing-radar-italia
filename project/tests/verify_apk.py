"""Inspect the actual built APK, not just the source tree."""
import json, zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
with zipfile.ZipFile(root/'Swing_Radar_Italia_3.0.apk') as z:
    data=json.loads(z.read('assets/fundamentals.json'))
    bundle=z.read('assets/fundamentals-bundle.js').decode()
    bundled=json.loads(bundle.removeprefix('window.SWING_FUNDAMENTALS = ').rstrip().removesuffix(';'))
    assert bundled==data, 'Offline JS must exactly match the real dataset'
    assert data['values']['SOL']['dataStatus']=='verified'
    html=z.read('assets/index.html').decode()
    assert 'Build 3.2' in html and 'fundamentals-bundle.js' in html
    assert 'Titolo non ancora coperto' in html
    assert 'Metriche non disponibili per questo titolo; nessun punteggio inventato.' not in html
    assert 'classes.dex' in z.namelist()
print('APK verified: SOL financial data and offline bundle match, current UI and dex packaged')
