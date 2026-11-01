"""Package the exact validated JSON for offline WebView fallback; no extra data."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=json.loads((root/'assets/fundamentals.json').read_text())
assert data['schemaVersion']==1 and data['values']['SOL']['dataStatus']=='verified'
(root/'assets/fundamentals-bundle.js').write_text('window.SWING_FUNDAMENTALS = '+json.dumps(data,ensure_ascii=True)+';\n')
