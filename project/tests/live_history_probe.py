"""Optional diagnostic: test the actual strict setup rules on recent Italian EOD history.

This is a read-only network diagnostic; the Android app does not require Python.
Run: python3 tests/live_history_probe.py
"""
import concurrent.futures
import datetime
import json
import pathlib
import statistics
import urllib.request
import zoneinfo

ROOT = pathlib.Path(__file__).resolve().parents[1]
STOCKS = [line.split(',', 1)[0] for line in (ROOT / 'assets/italian_stocks.csv').read_text().splitlines()[1:] if ',' in line]
ROME = zoneinfo.ZoneInfo('Europe/Rome')


def rsi2(bars):
    g = l = 0.0
    for i in (1, 2):
        delta = bars[i][4] - bars[i - 1][4]
        g += max(delta, 0)
        l += max(-delta, 0)
    g /= 2
    l /= 2
    for i in range(3, len(bars)):
        delta = bars[i][4] - bars[i - 1][4]
        g = (g + max(delta, 0)) / 2
        l = (l + max(-delta, 0)) / 2
    return 100 if not l else 100 - 100 / (1 + g / l)


def signal(b):
    if len(b) < 210:
        return []
    n = len(b)
    last, previous = b[-1], b[-2]
    price = last[4]
    if price < 1 or statistics.median(row[4] * row[5] for row in b[n - 21:n - 1]) < 500_000:
        return []
    ma20 = statistics.mean(row[4] for row in b[-20:])
    ma50 = statistics.mean(row[4] for row in b[-50:])
    ma200 = statistics.mean(row[4] for row in b[-200:])
    if not (price > ma200 and ma50 > ma200):
        return []
    hi20 = max(row[2] for row in b[n - 21:n - 1])
    lo20 = min(row[3] for row in b[n - 21:n - 1])
    hi_prev = max(row[2] for row in b[n - 22:n - 2])
    rvol = last[5] / statistics.median(row[5] for row in b[n - 21:n - 1])
    position = (price - last[3]) / (last[2] - last[3]) if last[2] > last[3] else 0.5
    recent60 = price / b[-61][4] - 1
    pullback = 1 - price / hi20
    near = min(abs(price / ma20 - 1), abs(price / ma50 - 1))
    force = (hi20 - lo20) / hi20 <= .22 and price >= hi20 * 1.005 and previous[4] <= hi_prev * 1.015 and rvol >= 1.3 and position >= .65
    weak = recent60 > .04 and .035 <= pullback <= .14 and rsi2(b) <= 25 and near <= .035
    return ['FORZA'] if force else ['DEBOLEZZA'] if weak else []


def inspect(ticker):
    url = f'https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.MI?range=2y&interval=1d&events=div%2Csplits'
    req = urllib.request.Request(url, headers={'User-Agent': 'SwingRadarItalia/1.1 (Android; EOD research)', 'Accept': 'application/json'})
    with urllib.request.urlopen(req, timeout=13) as response:
        result = json.loads(response.read())['chart']['result'][0]
    timestamps = result['timestamp']
    q = result['indicators']['quote'][0]
    bars = []
    for i, timestamp in enumerate(timestamps):
        values = [q[key][i] for key in ('open', 'high', 'low', 'close', 'volume')]
        if any(v is None for v in values) or values[4] <= 0 or values[3] <= 0 or values[0] <= 0:
            continue
        day = datetime.datetime.fromtimestamp(timestamp, tz=ROME).date()
        bars.append((day,) + tuple(values))
    now = datetime.datetime.now(tz=ROME)
    if bars and bars[-1][0] == now.date() and now.time() < datetime.time(18, 15):
        bars.pop()
    found = []
    for end in range(max(210, len(bars) - 35), len(bars) + 1):
        for kind in signal(bars[:end]):
            age = (now.date() - bars[end - 1][0]).days
            if age < 45:
                found.append((str(bars[end - 1][0]), ticker, kind))
    return found


def main():
    findings = []
    errors = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        jobs = {pool.submit(inspect, ticker): ticker for ticker in STOCKS}
        for future in concurrent.futures.as_completed(jobs):
            ticker = jobs[future]
            try:
                findings.extend(future.result())
            except Exception as exc:
                errors[ticker] = str(exc)[:80]
    print('Ticker in universe:', len(STOCKS))
    print('Ticker with errors:', len(errors))
    print('Confirmed historical signals in 45 days:', len(findings))
    print('Recent examples:')
    for item in sorted(findings, reverse=True)[:20]:
        print('  ', *item)
    if errors:
        print('Error sample:', list(errors.items())[:5])


if __name__ == '__main__':
    main()
