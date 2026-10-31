"""Reproduce Swing Radar EOD filters on Stellantis, from public Yahoo daily bars."""
import datetime as dt
import json
import statistics as st
import urllib.request

u = "https://query1.finance.yahoo.com/v8/finance/chart/STLAM.MI?range=2y&interval=1d"
req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req, timeout=25) as response:
    result = json.load(response)["chart"]["result"][0]
q = result["indicators"]["quote"][0]
bars = []
for i, timestamp in enumerate(result["timestamp"]):
    fields = [q[k][i] for k in ("open", "high", "low", "close", "volume")]
    if any(x is None for x in fields) or fields[4] <= 0:
        continue
    bars.append((dt.datetime.fromtimestamp(timestamp, dt.timezone.utc).date().isoformat(), *fields))

for asof in ("2026-10-27", "2026-10-28", "2026-10-29"):
    try:
        end = next(i+1 for i, b in enumerate(bars) if b[0] == asof)
    except StopIteration:
        continue
    subset = bars[:end]
    n = len(subset)
    if n < 210:
        continue
    today, prev = subset[-1], subset[-2]
    c = today[4]
    sma20 = st.mean(b[4] for b in subset[-20:])
    sma50 = st.mean(b[4] for b in subset[-50:])
    sma200 = st.mean(b[4] for b in subset[-200:])
    base = subset[-21:-1]
    hi20 = max(b[2] for b in base)
    lo20 = min(b[3] for b in base)
    turn = st.median([b[4]*b[5] for b in base])
    medvol = st.median([b[5] for b in base])
    rvol = today[5]/medvol
    pos = (c-today[3])/(today[2]-today[3]) if today[2]>today[3] else .5
    hi_prev = max(b[2] for b in subset[-22:-2])
    depth = (hi20-lo20)/hi20
    low120 = min(b[3] for b in subset[-121:-1])
    highs120 = max(b[2] for b in subset[-121:-1])
    g = max(subset[1][4]-subset[0][4],0)
    l = max(subset[0][4]-subset[1][4],0)
    for i in range(2,n):
        diff=subset[i][4]-subset[i-1][4]
        g=(g+max(0,diff))/2
        l=(l+max(0,-diff))/2
    rsi2=100 if l==0 else 100-100/(1+g/l)
    print(json.dumps({
        "date":asof,"close":round(c,3),"sma50":round(sma50,3),"sma200":round(sma200,3),
        "above200":c>sma200,"sma50_above200":sma50>sma200,"turnover_med_eur":round(turn),
        "high20":round(hi20,3),"breakout":c>=hi20*1.005,"base_depth":round(depth,3),
        "rvol":round(rvol,2),"position":round(pos,3),"prior_extended":prev[4]>hi_prev*1.015,
        "pullback":round(1-c/hi20,3),"return60":round(c/subset[-61][4]-1,3),
        "low120":round(low120,3),"distLow120":round(c/low120-1,3),"high120":round(highs120,3),
        "dailyPct":round((c/prev[4]-1)*100,2),"rsi2":round(rsi2,1)
    }, ensure_ascii=False))
