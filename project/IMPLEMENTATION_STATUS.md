# ESEF integration — pending publication

31 October 2026. Based on giorai93/Swing-radar-italia main bef06d1f633970339ce4ab524857607da4e1faba.

Implemented locally: editable Android source tree; ESEF/XBRL extractor; explicit issuer registry (GVS, SOL only); raw fact provenance (source URL, SHA256, context, concept, unit, dates); annual Quality/Growth/earnings-yield Valuation; strict missing numeric handling in Trade; Opportunity requires all components and does not backdate newly retrieved facts. Scores are heuristics, not probabilities or validated trading signals. No TTM claims.

Live evidence: SOL official 2025 archive downloaded and parsed; 16 primary/current-comparative facts, four reference values checked. GVS inline report downloaded from filings.xbrl.org; detects nonzero EPS text with fixed-zero transform and a revenue comparative tagged 2023 rather than 2024. All derived GVS financial metrics quarantined pending a corrected or independently reconciled filing. Do not call GVS financially validated. Raw facts remain inspectable with warnings. No figures manually substituted.

Tests actually run: six Python numeric regression tests PASS; fundamentals JS regression suite PASS; chart, stats and universe smoke suites PASS; live verification script PASS for SOL reference values and GVS quarantine behavior. Existing ui_smoke.js fails its first watchlist assertion in BOTH untouched baseline and modified source; unresolved legacy test mismatch. Java/Android build NOT run: SDK unavailable locally. GitHub Actions NOT started. Publication attempt (Git Trees API) rejected with HTTP 403 Resource not accessible by integration; no remote files or branch refs updated.

CI workflow prepared at repository root .github/workflows/build.yml. It extracts real reports, runs checks, builds from project/, uploads the stable APK filename Swing_Radar_Italia_3.0.apk, and publishes the verified dataset after a successful build. CI itself is unverified until write access is restored. Existing signing script creates a new local key per clean build: update-in-place over a previous APK is not guaranteed; a persistent signing setup remains necessary.

Commands from repository root:
python -m pip install lxml==6.0.2
python -m unittest discover -s project/tests -p test_esef.py -v
python project/tools/esef.py --strict
python project/tests/verify_live.py
(cd project && node tests/fundamentals_smoke.js)
bash project/build_apk.sh

Scope limitations: only two registered issuers; annual 2025, not interim 2026. Source discovery and custom extension mappings are deliberately not inferred. Unsupported/missing financial tags stay unavailable. GVS requires further source reconciliation. No APK is included.
