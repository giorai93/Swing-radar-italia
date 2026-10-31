# ESEF integration — published; APK build verified

31 October 2026. Based on giorai93/Swing-radar-italia main bef06d1f633970339ce4ab524857607da4e1faba.

Published in commit 9c2caf9f297b07d2c8fbc725df11cdddfd2d0ae5: editable Android source tree; ESEF/XBRL extractor; explicit issuer registry (GVS, SOL only); raw fact provenance (source URL, SHA256, context, concept, unit, dates); annual Quality/Growth/earnings-yield Valuation; strict missing numeric handling in Trade; Opportunity requires all components and does not backdate newly retrieved facts. Scores are heuristics, not probabilities or validated trading signals. No TTM claims.

Live evidence: SOL official 2025 archive downloaded and parsed; 16 primary/current-comparative facts, four reference values checked. GVS inline report downloaded from filings.xbrl.org; detects nonzero EPS text with fixed-zero transform and a revenue comparative tagged 2023 rather than 2024. All derived GVS financial metrics quarantined pending a corrected or independently reconciled filing. Do not call GVS financially validated. Raw facts remain inspectable with warnings. No figures manually substituted.

Tests actually run: six Python numeric regression tests PASS; fundamentals JS regression suite PASS; chart, stats and universe smoke suites PASS; live verification script PASS for SOL reference values and GVS quarantine behavior. Existing ui_smoke.js fails its first watchlist assertion in BOTH untouched baseline and modified source; unresolved legacy test mismatch. GitHub write access was restored. GitHub Actions run 41241506169 completed successfully: live downloads and checks, Java compilation, APK packaging, v2/v3 signature verification, artifact upload and dataset publication all passed. Run: https://github.com/giorai93/Swing-radar-italia/actions/runs/41241506169 . Device installation and UI interaction have not been tested.

CI workflow prepared at repository root .github/workflows/build.yml. It extracts real reports, runs checks, builds from project/, uploads the stable APK filename Swing_Radar_Italia_3.0.apk, and publishes the verified dataset after a successful build. CI successfully executed on 31 October 2026 at 21:00 UTC. Existing signing script creates a new local key per clean build: update-in-place over a previous APK is not guaranteed; a persistent signing setup remains necessary.

Commands from repository root:
python -m pip install lxml==6.0.2
python -m unittest discover -s project/tests -p test_esef.py -v
python project/tools/esef.py --strict
python project/tests/verify_live.py
(cd project && node tests/fundamentals_smoke.js)
bash project/build_apk.sh

Scope limitations: only two registered issuers; annual 2025, not interim 2026. Source discovery and custom extension mappings are deliberately not inferred. Unsupported/missing financial tags stay unavailable. GVS requires further source reconciliation. APK artifact: https://github.com/giorai93/Swing-radar-italia/actions/runs/41241506169/artifacts/13057945963 .

