# Stockbee-inspired pilot: four families, Italian daily bars

1 November 2026. Exploratory price study, NOT a faithful implementation of discretionary Stockbee trading. No fitted model, parameter search, or claim of independent out-of-sample validation. The rules below were coded before inspecting this run's results. The existing 2021–2025 data were already used in other research; 2026 remains inaccessible to this experiment.

## Sources and operational choices

- Momentum/qualitative selection: https://stockbee.blogspot.com/2016/07/guidelines-to-find-good-momentum-burst.html . This pilot operationalizes daily +4%, volume greater than yesterday, range wider than the preceding three sessions, close in upper quarter, previous day narrow (at/below ten-day median) or negative, no -4% day in previous five, and no three consecutive positive days before signal. The numeric definitions of narrow range/close position are OUR choices, not a certified transcription of discretionary selection.
- Anticipation: https://stockbee.blogspot.com/2014/08/how-i-generate-my-breakout-anticipation.html . Uses price/252-day low >=1.8 OR SMA7/SMA65 >1.05, daily change within +/-1%. Adds range at/below ten-day median, volume below previous 20-day mean, orderly prior five days, no three consecutive positive closes. This tests a next-open anticipatory purchase, NOT an intraday breakout-confirmation order. Historical source includes a share-volume floor; this Italian pilot replaces it with the common turnover filter below.
- EP families: https://impact.traders4acause.org/wp-content/uploads/2024/11/02-Pradeep-Trade-Factory-2.pdf . EP9 volume >8.9 million is tested with our 20-day breakout and upper-quarter close filters. Italian alternative replaces the absolute volume threshold with >=3 times prior 50-day average. Original and adapted variants are reported separately, not selected by performance. Their news/story classification is absent.
- EP classic: no complete historical catalyst archive exists in this dataset. A gap >=4%, close-to-close gain >=4%, RVOL >=3 and upper-quarter close defines ONLY technical candidates. These numeric thresholds are our proxy. The confirmed EP backtest remains BLOCKED. A positive price-proxy result would not validate EP classic.

## Common eligibility and execution

Available Yahoo cache, 124 tickers with bars before 2026; current-list universe with survivorship bias. Each date requires 252 valid bars, nominal price >=EUR1, and previous 20-day median nominal price times provider share volume >=EUR500,000. Provider volume and corporate-action adjustments are not independently certified. No claim of coverage of all Italian securities, historic delisted names, or all share classes being ordinary shares.

Signal formed after close. Buy next ENI-calendar trading session's open, no substitution for a missing open. Each independent trade has EUR1,000 budget, integer initial shares, both commissions reserved. Stops are frozen at signal low minus 0.1 ATR14; invalid/nonpositive stop or entry below stop rejected. Target = actual raw next-open price +3 times its distance to stop. Stop-first on ambiguous daily bars; gap below stop executes at open; no fill invented during a missing session. Hold up to five common sessions for Momentum/Anticipation, twenty for EP candidates, then close; delayed time exit waits for a valid quote. Late positions still open at data cutoff remain unresolved and excluded from closed metrics, with counts disclosed.

Cost scenarios fixed in advance: no-cost diagnosis; EUR2.95 per side plus 5, 15, or 30 basis points per side. 15 bp is the reference schedule for deduplicating same-ticker/same-method signals while an operation remains open. Corporate splits change quantities and stop/target denomination; gross provider dividends credited only to positions held before ex-date. Missing bars on corporate-action dates remain an unvalidated data-quality risk. No taxes, borrowing, book impact model, or actual dividend payment-date modeling.

No market breadth filter, subjective chart review, intraday selection, historical fundamental screen, or catalyst verification is reproduced. Comparisons therefore test operational hypotheses, not the full Stockbee method. There is NO portfolio simulation: trades across tickers/strategies may overlap. Do not add P&Ls and call them account returns; no CAGR or portfolio drawdown is provided.

## Reproduce

Python 3 with pandas/numpy. No broker connection or trading orders.

```
python -m unittest discover -s research/stockbee -p 'test_*.py' -v
python research/stockbee/study.py --raw /path/to/yahoo-chart-json --out stockbee-results
```

Each input file must be named SYMBOL.MI.json with Yahoo chart response structure. The report records source URLs and SHA256 hashes, per-year and named-stock results, unresolved counts and cost sensitivity. The raw cached files are not bundled in this code-only addition; exact replay requires the matching hashes. Eight synthetic execution/regression checks were actually run and passed; they do not establish profitability.
