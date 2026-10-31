"""Read-only verification of historical watch zones on expanded Italian stocks.

This is NOT a return backtest and does not imply the zones are profitable.
"""
import concurrent.futures
import datetime
import json
import pathlib
import statistics
import urllib.request
import zoneinfo
from live_history_probe import rsi2

ROOT = pathlib.Path(__file__).resolve().parents[1]
rows = {}
for file in ('italian_stocks.csv', 'italian_expanded.csv'):
    for line in (ROOT / 'assets' / file).read_text().splitlines()[1:]:
        fields = line.split(',')
        if len(fields) >= 2:
            rows[fields[0]] = fields[2] if len(fields) > 2 else 'BASE'
ROME = zoneinfo.ZoneInfo('Europe/Rome')


def zone(b, segment):
    n = len(b)
    if n < 210:
        return None
    bar = b[-1]
    c = bar[4]
    if c < 1 or statistics.median(x[4]*x[5] for x in b[-21:-1]) < (250_000 if segment == 'SMALL' else 500_000):
        return None
    sma200 = statistics.mean(x[4] for x in b[-200:])
    ret60 = c/b[-61][4]-1
    if c >= sma200 or ret60 > -.08:
        return None
    support = min(x[3] for x in b[-121:-1])
    if not support or not .995*support <= c <= 1.2*support:
        return None
    def true_range(i):
        x,prev=b[i],b[i-1]
        return max(x[2]-x[3],abs(x[2]-prev[4]),abs(x[3]-prev[4]))
    atr=statistics.mean(true_range(i) for i in range(1,15))
    for i in range(15,n):atr=(atr*13+true_range(i))/14
    stop=support-max(.25*atr,.015*support)
    if c-stop <= 0 or (c-stop)/c > .18:
        return None
    dist = c/support - 1
    rsi = rsi2(b)
    position = (c-bar[3])/(bar[2]-bar[3]) if bar[2] > bar[3] else .5
    checks = [dist <= .12, ret60 <= -.12, rsi <= 70, position >= .45]
    if sum(checks) < 3:
        return None
    return (str(bar[0]), sum(checks), round(c,3), round(support,3), round(ret60*100,1))


def inspect(ticker, segment):
    url = f'https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.MI?range=2y&interval=1d'
    req = urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
    with urllib.request.urlopen(req,timeout=18) as response:
        result=json.load(response)['chart']['result'][0]
    q=result['indicators']['quote'][0]
    bars=[]
    for i, timestamp in enumerate(result['timestamp']):
        vals=[q[key][i] for key in ('open','high','low','close','volume')]
        if any(x is None for x in vals) or vals[4] <= 0 or vals[3] <= 0 or vals[0] <= 0:
            continue
        bars.append((datetime.datetime.fromtimestamp(timestamp,tz=ROME).date(),*vals))
    now=datetime.datetime.now(tz=ROME)
    if bars and bars[-1][0]==now.date() and now.time()<datetime.time(18,15):
        bars.pop()
    latest=None
    for end in range(max(210,len(bars)-35),len(bars)+1):
        result=zone(bars[:end],segment)
        if result is not None:
            latest=(ticker,*result)
    return latest


def main():
    ok=[];errors=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as workers:
        futures={workers.submit(inspect,ticker,segment):ticker for ticker,segment in rows.items()}
        for f in concurrent.futures.as_completed(futures):
            try:
                result=f.result()
                if result:ok.append(result)
            except Exception as error:
                errors.append((futures[f],str(error)[:50]))
    ok.sort(key=lambda x:(x[1],x[2]),reverse=True)
    print('Total:',len(rows),'Watch candidates:',len(ok),'Errors:',len(errors))
    print('STLAM:',next((x for x in ok if x[0]=='STLAM'),'not found'))
    for x in ok[:25]:print(*x)
    if errors:print('Error sample:',errors[:5])


if __name__=='__main__':main()
