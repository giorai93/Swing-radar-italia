"""Exploratory Italian equity ranking, chronological and pooled across tickers.
Not an Android signal engine, Websim replica, or survivorship-free backtest.
"""
from __future__ import annotations
import argparse, concurrent.futures, datetime as dt, hashlib, json, pathlib, urllib.request
import numpy as np
import pandas as pd
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import Ridge
from sklearn.ensemble import HistGradientBoostingRegressor

ROOT = pathlib.Path(__file__).resolve().parents[1]
FEATURES = ['r5','r20','r60','r120','r250','vol20','trend200','rvol','range20']
END = '2026-01-01'  # 2026 deliberately reserved; never scored in this experiment.
COST = 2 * 2.95 / 1000 + 2 * .0005

def download(ticker, cache):
    symbol = ticker if ticker.endswith('.MI') else ticker + '.MI'
    url = f'https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=10y&interval=1d&events=div%2Csplits'
    path = cache / (symbol + '.json')
    try:
        if path.exists():
            raw = path.read_bytes()
        else:
            req = urllib.request.Request(url, headers={'User-Agent':'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=25) as response: raw = response.read()
            path.write_bytes(raw)
        result = json.loads(raw)['chart']['result'][0]
        q = result['indicators']['quote'][0]
        frame = pd.DataFrame(q, index=pd.to_datetime(result['timestamp'],unit='s',utc=True).tz_convert('Europe/Rome').date)
        frame.index = pd.to_datetime(frame.index)
        frame['adjclose'] = result['indicators']['adjclose'][0]['adjclose']
        frame = frame.loc[frame.index < END].sort_index()
        if frame.index.duplicated().any(): raise ValueError('duplicate sessions')
        frame = frame.dropna(subset=['open','high','low','close','volume','adjclose'])
        frame = frame[(frame[['open','high','low','close','adjclose']] > 0).all(axis=1) & (frame.volume > 0)]
        frame = frame[(frame.high >= frame[['open','close','low']].max(axis=1)) & (frame.low <= frame[['open','close']].min(axis=1))]
        if len(frame) < 500: raise ValueError(f'only {len(frame)} valid bars')
        return ticker, frame, {'ticker':ticker,'status':'ok','url':url,'sha256':hashlib.sha256(raw).hexdigest(),'cache_mtime_utc':dt.datetime.fromtimestamp(path.stat().st_mtime,dt.timezone.utc).isoformat(),'bars':len(frame),'first':str(frame.index.min().date()),'last':str(frame.index.max().date())}
    except Exception as exc:
        return ticker, None, {'ticker':ticker,'status':'error','url':url,'error':str(exc)}

def features(frame):
    c = frame.adjclose
    f = pd.DataFrame(index=frame.index)
    for n in [5,20,60,120,250]: f[f'r{n}'] = c.pct_change(n,fill_method=None)
    f['vol20'] = c.pct_change(fill_method=None).rolling(20).std()
    f['trend200'] = c / c.rolling(200).mean() - 1
    f['rvol'] = frame.volume / frame.volume.shift(1).rolling(20).median()
    factor = c / frame.close
    hi, lo = frame.high * factor, frame.low * factor
    f['range20'] = hi.rolling(20).max() / lo.rolling(20).min() - 1
    f['turnover'] = (frame.close * frame.volume).rolling(20).median()
    f['price'] = frame.close
    return f.replace([np.inf,-np.inf],np.nan)

def samples(frames):
    # Actual ENI sessions are the common calendar; no per-stock random splits.
    calendar = frames['ENI'].index
    signals = calendar[calendar >= '2018-01-01'][::21]
    rows = []
    for ticker, frame in frames.items():
        f = features(frame)
        adjusted_open = frame.open * frame.adjclose / frame.close
        for signal, next_signal in zip(signals[:-1],signals[1:]):
            a = calendar.searchsorted(signal) + 1
            b = calendar.searchsorted(next_signal) + 1
            if b >= len(calendar): continue
            entry, exit_day = calendar[a], calendar[b]
            # Missing bars are excluded, never forward-filled as executable prices.
            if any(day not in frame.index for day in [signal,entry,exit_day]): continue
            x = f.loc[signal]
            if x[FEATURES].isna().any() or x.turnover < 500000 or x.price < 1: continue
            gross = float(adjusted_open.loc[exit_day] / adjusted_open.loc[entry] - 1)
            rows.append({'ticker':ticker,'signal':signal,'entry':entry,'exit':exit_day,'gross':gross,'net':gross-COST,**x[FEATURES].to_dict()})
    return pd.DataFrame(rows)

def training_rows(data, cutoff):
    # Outcome must be fully observed BEFORE the prediction year starts.
    return data[(data.signal < cutoff) & (data.exit < cutoff)]

def summary(trades):
    if not len(trades): return {'trades':0}
    r = trades.net
    periods = trades.groupby('signal').net.mean().sort_index()
    rng = np.random.default_rng(42)
    # Block by whole holding period, not correlated individual-stock observations.
    means = np.mean(rng.choice(periods.to_numpy(), (2000,len(periods)),replace=True),axis=1)
    losses = -r[r<0].sum()
    return {'trades':len(trades),'periods':len(periods),'mean_net_pct':float(r.mean()*100),'mean_gross_pct':float(trades.gross.mean()*100),'win_rate_pct':float((r>0).mean()*100),'profit_factor':float(r[r>0].sum()/losses) if losses else None,'period_mean_95ci_pct':(np.quantile(means,[.025,.975])*100).tolist()}

def run(data):
    outputs, folds = [], []
    for year in range(2021,2026):
        cutoff = pd.Timestamp(f'{year}-01-01')
        train = training_rows(data,cutoff)
        test = data[(data.signal >= cutoff)&(data.signal < pd.Timestamp(f'{year+1}-01-01'))].copy()
        if len(train)<500 or test.empty: continue
        models = {'ridge':make_pipeline(StandardScaler(),Ridge(alpha=100)),
                  'boosting':HistGradientBoostingRegressor(max_iter=100,max_leaf_nodes=7,min_samples_leaf=100,l2_regularization=10,learning_rate=.05,random_state=42)}
        for name,model in models.items():
            model.fit(train[FEATURES],train.net)
            test[name] = model.predict(test[FEATURES])
        folds.append({'year':year,'training_rows':len(train),'last_training_exit':str(train.exit.max().date()),'first_test_signal':str(test.signal.min().date()),'test_rows':len(test)})
        for signal, group in test.groupby('signal'):
            if len(group)<20: continue
            for name in ['equal_universe','momentum60','reversal5','ridge','boosting']:
                if name=='equal_universe': selected=group.copy()
                elif name=='momentum60': selected=group.nlargest(10,'r60').copy()
                elif name=='reversal5': selected=group.nsmallest(10,'r5').copy()
                else: selected=group.nlargest(10,name).copy()
                selected['method']=name
                outputs.append(selected)
    if not outputs: raise ValueError('Insufficient data for chronological evaluation')
    trades=pd.concat(outputs,ignore_index=True)
    report={name:summary(g) for name,g in trades.groupby('method')}
    by_year={f'{name}/{year}':summary(g) for (name,year),g in trades.groupby(['method',trades.signal.dt.year])}
    periods = trades.groupby(['signal','method']).net.mean().unstack()
    comparisons = {}
    for baseline in ['equal_universe','momentum60']:
        for name in ['ridge','boosting','momentum60','reversal5']:
            delta = (periods[name]-periods[baseline]).dropna().to_numpy()
            rng = np.random.default_rng(42)
            boot = rng.choice(delta,(2000,len(delta)),replace=True).mean(axis=1)
            comparisons[name + '_vs_' + baseline] = {'mean_excess_per_period_pct':float(delta.mean()*100),
                                 'paired_period_95ci_pct':(np.quantile(boot,[.025,.975])*100).tolist()}
    focal = {f'{name}/{ticker}':summary(g) for (name,ticker),g in trades[trades.ticker.isin(['STLAM','REY','TGYM','MAIRE'])].groupby(['method','ticker'])}
    return trades, {'overall':report,'by_year':by_year,'folds':folds,'paired_comparisons':comparisons,'focal_tickers':focal}

def main():
    p=argparse.ArgumentParser(); p.add_argument('--out',default='research/output'); p.add_argument('--limit',type=int); p.add_argument('--replay',help='Saved samples CSV or CSV.gz, no download'); args=p.parse_args()
    out=pathlib.Path(args.out); cache=out/'raw'; cache.mkdir(parents=True,exist_ok=True)
    if args.replay:
        data=pd.read_csv(args.replay,parse_dates=['signal','entry','exit'])
        if (data.exit >= pd.Timestamp(END)).any(): raise ValueError('Replay would access reserved period')
        trades,report=run(data)
        trades.to_csv(out/'oos_trades.csv',index=False)
        (out/'replay_report.json').write_text(json.dumps(report,indent=2,allow_nan=False))
        print(json.dumps(report['overall'],indent=2))
        return
    tickers=set()
    for name in ['italian_stocks.csv','italian_expanded.csv']:
        tickers.update(pd.read_csv(ROOT/'assets'/name).ticker.dropna().astype(str))
    tickers=sorted(tickers)
    if args.limit: tickers=['ENI']+[t for t in tickers if t!='ENI'][:args.limit-1]
    frames={}; coverage=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for ticker,frame,meta in pool.map(lambda t: download(t,cache),tickers):
            coverage.append(meta)
            if frame is not None: frames[ticker]=frame
            print(f'{ticker}: {meta["status"]}',flush=True)
    (out/'coverage.json').write_text(json.dumps(coverage,indent=2))
    if 'ENI' not in frames: raise RuntimeError('ENI calendar unavailable; coverage written, no invented results')
    data=samples(frames); data.to_csv(out/'samples.csv',index=False)
    trades,report=run(data); trades.to_csv(out/'oos_trades.csv',index=False)
    report.update({'created_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'requested_tickers':len(tickers),'usable_tickers':len(frames),'sample_rows':len(data),'cost_per_trade_pct':COST*100,'cutoff_exclusive':END,'status':'exploratory_not_validated','limitations':['Current supplied universe, missing delisted companies: survivorship bias.','Adjusted open returns are a total-return proxy, not audited cash executions.','Missing endpoint prices excluded; suspended/delisted losses can be understated.','Fixed 1000 EUR notional per position, fractional units; not a 1000 EUR portfolio.','Equal-universe baseline has more positions/capital than top-ten methods.','No fundamentals, no news, no Websim inputs.','Models refit annually; no tuning, no random split. 2026 reserved.','Confidence interval resamples holding periods; does not remove survivorship or model-selection bias.','This is a 21-session ranking study, not the app stop/target backtest.']})
    (out/'report.json').write_text(json.dumps(report,indent=2,allow_nan=False))
    print(json.dumps(report['overall'],indent=2))

if __name__=='__main__': main()
