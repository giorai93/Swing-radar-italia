# Four-strategy signal study (research v1)

The statistics screen now includes FORZA, DEBOLEZZA, RECUPERO and EVENT_MOMENTUM.
These are independent research rules, not proprietary Websim rules or a validated
portfolio strategy. The user-provided Rai Way screenshot establishes only a long
idea on a weak stock near support; entry/stop/target levels are not visible.

Recovery: use the existing recovery detector, but require all four optional
conditions (distance to previous 120-session low <=12%, 60-session return <=-12%,
RSI2 <=70, closing position >=45%). Price must be below SMA200. The existing
minimum turnover and price filters apply. Stop is below both support and the
signal-day low; risk at signal <=18%. Targets are max(SMA20, close+1.5R) and
max(SMA50, close+2.5R), frozen on the signal date.

Momentum: existing price/volume detector: daily return >=3.5%, or >=1.5% with
2-day return >=6%; relative volume >=1.15; closing position >=60%. Stop is
signal low -0.25 ATR14. Risk at signal <=12%. Targets close+2R and close+3R.
This does not establish a historical earnings/news catalyst.

All four: next-session open with configured slippage; exclude opening already
outside stop/target1; integer shares within per-trade budget; commission both
sides; stop first when daily OHLC cannot determine touch order; exit entirely at
T1 or after selected 5/10/20 sessions. T2 is a separate hypothetical path.
Repeated same-ticker/same-strategy observations while a trade is open are not
new independent trades. Strategies remain separate even on the same ticker:
the combined summary is not a feasible capital-constrained portfolio.

Persistence: DB version 2 adds research_signals without dropping quotes, trades
or existing signals. Each research observation is immutable and stored separately
from notification/radar signals. A new scan replays the recent 35-session window
using only bar prefixes and populates these observations. Stats read all saved
signals; the radar retains its existing display limit. Old latest-only watch
snapshots are deliberately not treated as historical signals.

Validation: synthetic execution/regression tests exercise all four strategies,
matching costs, duplicate observations, incomplete histories and missing levels.
These tests do not measure profitability. Historical returns appear only after
an actual scan has populated signal records and subsequent market bars. Existing
survivorship bias, limited history, informal calibrated thresholds and sequential
DD proxy restrictions remain; no out-of-sample claim. No historical fundamental
filter or current earnings-calendar data is used to manufacture past catalysts.
