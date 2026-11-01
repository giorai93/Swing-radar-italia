# 3.2: navigation, offline fundamentals and honest results

Navigation: Oggi (latest session), Osserva (four watch lists), Bilanci (explicit
coverage), Risultati (four-strategy summary + filterable simulated archive),
Diario (real user-recorded trades). Large parameter/methodology blocks are
collapsed. Results lead with the observed negative/positive R verdict and show
pending/excluded cases and a completed-follow-up cohort to flag immature samples.
No entry/exit thresholds were changed or optimized against the supplied screenshot.

SOL's real ESEF dataset is packaged as both JSON and an identical JS fallback.
The financial screen works even if the Android status bridge has missing data or
remote refresh fails; corrupt/unproven vendor cache rows do not override it.
Ticker aliases SOL.MI/MIL:SOL normalize to SOL. Coverage explicitly remains one
verified issuer (SOL), one quarantined issuer (GVS), others unavailable.
Financial source links now open in the system browser. Version 3.2 is visible.
Opportunity is a CURRENT assessment, not a historical backtest input; previously
its retrieval-date condition made previous-session signals perpetually unavailable.

Tests: numeric/execution regression suites, offline SOL UI regression, mobile
Chromium navigation/overflow checks in CI, and verification of the actual APK
contents (JSON/JS equality, SOL data, HTML version, classes.dex). Real Android
installation and signing compatibility with older APKs are not tested. Existing
legacy ui_smoke assertions refer to the previous navigation and are superseded
for this change by mobile_ui.cjs and the current stats/fundamentals tests.
