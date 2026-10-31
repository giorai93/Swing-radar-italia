import json
from pathlib import Path
p=Path(__file__).resolve().parents[1]
d=json.loads((p/'assets/fundamentals.json').read_text())
assert not d['errors'], d['errors']
assert set(d['values'])=={'GVS','SOL'}
for ticker,m in d['values'].items():
    assert m['periodEnd']=='2025-12-31'
    assert len(m['sha256'])==64
    assert m['facts']['revenue_2025']['unit']=='EUR'
    print(ticker, m['dataStatus'], m['warnings'])
    if ticker=='SOL':
        assert m['facts']['revenue_2024']['unit']=='EUR'
        assert m['revenue_growth_pct'] is not None and m['roe_pct'] is not None
    elif m['dataStatus']=='needs_review':
        assert m['eps'] is None and m['revenue_growth_pct'] is None
        assert m['warnings'], 'Quarantine must have an explicit reason'
s=d['values']['SOL']
# Independent transcription from SOL's tagged primary statements, EUR after scale=3.
assert s['facts']['revenue_2025']['value']==1776086000
assert s['facts']['revenue_2024']['value']==1610444000
assert s['facts']['parentProfit_2025']['value']==166993000
assert s['eps']==1.841
print('SOL primary-statement regression values passed')

print('GVS: inconsistent tags quarantined, NOT financially validated; no fabricated scores')
