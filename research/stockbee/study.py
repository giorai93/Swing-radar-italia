"""Exploratory daily-bar Stockbee-inspired study, not a faithful discretionary replica."""
import argparse, hashlib, json, math
from pathlib import Path
import numpy as np
import pandas as pd

END = pd.Timestamp('2026-01-01')
METHODS = ['momentum_burst', 'anticipation', 'ep_classic_price_proxy', 'ep9_original', 'ep9_italy_proxy']

def load(path):
    raw = path.read_bytes()
    r = json.loads(raw)['chart']['result'][0]
    if not r or not r.get('timestamp'): return None
    dates = pd.to_datetime(r['timestamp'],unit='s',utc=True).tz_convert('Europe/Rome').tz_localize(None).normalize()
    f = pd.DataFrame(r['indicators']['quote'][0], index=dates).sort_index()
    if f.index.duplicated().any(): raise ValueError('Duplicate dates')
    factor = pd.Series(1.,index=f.index)
    splits={}
    for e in r.get('events',{}).get('splits',{}).values():
        d=pd.to_datetime(e['date'],unit='s',utc=True).tz_convert('Europe/Rome').tz_localize(None).normalize()
        ratio=float(e['numerator'])/float(e['denominator'])
        factor.loc[f.index<d]*=ratio; splits[d]=ratio
    f['split']=1.; f['dividend']=0.
    for d,v in splits.items():
        if d in f.index: f.loc[d,'split']=v
    for e in r.get('events',{}).get('dividends',{}).values():
        d=pd.to_datetime(e['date'],unit='s',utc=True).tz_convert('Europe/Rome').tz_localize(None).normalize()
        if d in f.index: f.loc[d,'dividend']+=float(e['amount'])*factor.loc[d]
    # Signals use continuous split-adjusted quotes; executions use historical nominal units.
    for k in ['open','high','low','close']: f['nominal_'+k]=f[k]*factor
    f['factor']=factor
    f=f.loc[f.index<END].copy()
    ok=(f[['open','high','low','close','volume']]>0).all(axis=1)
    ok &= f.high.ge(f[['open','close','low']].max(axis=1)) & f.low.le(f[['open','close']].min(axis=1))
    f.loc[~ok,['open','high','low','close','volume']]=np.nan
    return f

def signals(f):
    c,h,l,v=f.close,f.high,f.low,f.volume
    ret=c.pct_change(fill_method=None)
    span=h-l
    atr=pd.concat([span,(h-c.shift()).abs(),(l-c.shift()).abs()],axis=1).max(axis=1).rolling(14).mean()
    turnover=(f.nominal_close*v).shift().rolling(20).median()
    eligible=(f.nominal_close>=1)&(turnover>=500000)&(c.rolling(252).count()==252)
    nearhigh=(c-l)/span.replace(0,np.nan)>=.75
    quiet=(span.shift()<=span.shift().rolling(10).median())|(ret.shift()<=0)
    orderly=ret.shift().rolling(5).min()>-.04
    rising3=(ret>0).rolling(3).sum()==3
    mb=eligible&(ret>=.04)&(v>v.shift())&(span>span.shift().rolling(3).max())&nearhigh&quiet&orderly&(~rising3.shift(fill_value=False))
    trend=(c/l.rolling(252).min()>=1.8)|(c.rolling(7).mean()/c.rolling(65).mean()>1.05)
    ants=eligible&trend&(ret.abs()<=.01)&(span<=span.rolling(10).median())&(v<=v.shift().rolling(20).mean())&orderly&(~rising3)
    rv=v/v.shift().rolling(50).mean()
    # No news is inferred from a price gap. This is ONLY a technical candidate screen.
    ep=eligible&(f.open/c.shift()-1>=.04)&(ret>=.04)&(rv>=3)&nearhigh
    ep9=eligible&(v>8900000)&(c>h.shift().rolling(20).max())&nearhigh
    local=eligible&(rv>=3)&(c>h.shift().rolling(20).max())&nearhigh
    return pd.DataFrame({'momentum_burst':mb,'anticipation':ants,'ep_classic_price_proxy':ep,'ep9_original':ep9,'ep9_italy_proxy':local,'atr':atr},index=f.index).fillna(False)

def outcome(f,calendar,signal,method,fee=2.95,bps=15,budget=1000):
    """Signal at close, entry NEXT common session; no future-based candidate selection."""
    at=calendar.get_indexer([signal])[0]+1
    if at>=len(calendar): return {'status':'no_next_session'}
    entry=calendar[at]
    if entry not in f.index or pd.isna(f.loc[entry,'open']): return {'status':'missing_entry','entry':str(entry.date())}
    s=f.loc[signal]; b=f.loc[entry]; slip=bps/10000
    # Stop frozen at signal; split on next session changes denomination before entry.
    split=float(b['split'])
    stop=(s.nominal_low-.1*float(s.atr)*s.factor)/split
    price=float(b.nominal_open)
    if stop<=0 or price<=stop: return {'status':'invalid_entry_stop','entry':str(entry.date())}
    paid=price*(1+slip); qty=math.floor((budget-2*fee)/paid)
    if qty<1: return {'status':'insufficient_budget','entry':str(entry.date())}
    cost=qty*paid+fee; initial_qty=qty; target=price+3*(price-stop); dividends=0.
    hold=5 if method in ['momentum_burst','anticipation'] else 20
    last_mark=price; stale=0
    for n,d in enumerate(calendar[at:]):
        if d not in f.index or pd.isna(f.loc[d,'open']):
            stale+=1;continue
        row=f.loc[d]
        if n>0:
            ratio=float(row['split']);qty*=ratio;stop/=ratio;target/=ratio
            dividends+=qty*row.dividend
        last_mark=float(row.nominal_close)
        reason=None; exit_price=None
        if row.nominal_open<=stop: reason='gap_stop';exit_price=float(row.nominal_open)
        elif row.nominal_low<=stop: reason='stop';exit_price=stop
        elif row.nominal_high>=target: reason='target';exit_price=target # conservative when open above target
        elif n>=hold-1: reason='time';exit_price=last_mark
        if reason:
            proceeds=qty*exit_price*(1-slip)-fee+dividends
            return {'status':'closed','entry':str(entry.date()),'exit':str(d.date()),'reason':reason,'pnl_eur':proceeds-cost,'return_on_budget_pct':100*(proceeds-cost)/budget,'entry_price':paid,'initial_qty':initial_qty,'fees_eur':2*fee,'dividends_eur':dividends,'missing_bar_days':stale}
    return {'status':'open_at_cutoff','entry':str(entry.date()),'last_mark_value':qty*last_mark,'missing_bar_days':stale}

def summarize(rows):
    closed=[r for r in rows if r['status']=='closed'];pn=np.array([r['pnl_eur'] for r in closed])
    losses=-pn[pn<0].sum()
    return {'attempts':len(rows),'closed':len(closed),'unresolved':sum(r['status']=='open_at_cutoff' for r in rows),'win_rate_pct':float((pn>0).mean()*100) if len(pn) else None,'mean_pnl_eur':float(pn.mean()) if len(pn) else None,'mean_return_on_1000_pct':float(pn.mean()/10) if len(pn) else None,'profit_factor':float(pn[pn>0].sum()/losses) if losses else None,'status_counts':{s:sum(r['status']==s for r in rows) for s in sorted(set(r['status'] for r in rows))}}

def run(raw,out):
    out.mkdir(parents=True,exist_ok=True); frames={};audit=[]
    for p in sorted(raw.glob('*.json')):
        f=load(p)
        if f is None or f.empty: continue
        frames[p.stem]=f
        audit.append({'ticker':p.stem,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'first':str(f.index.min().date()),'last_used':str(f.index.max().date()),'source_url':f'https://query1.finance.yahoo.com/v8/finance/chart/{p.stem}?range=10y&interval=1d&events=div%2Csplits'})
    calendar=frames['ENI.MI'].index
    assert calendar.max()<END
    rows=[];screen_counts={m:0 for m in METHODS}
    for ticker,f in frames.items():
        scan=signals(f); f=f.join(scan[['atr']])
        for method in METHODS:
            dates=scan.index[scan[method]&(scan.index>='2021-01-01')]
            screen_counts[method]+=len(dates);busy_until=None
            for d in dates:
                if d not in calendar: continue
                if busy_until is not None and d<=busy_until: continue
                # Same eligibility/trade path for each cost scenario except rounding/target depends on fill.
                for bps in [0,5,15,30]:
                    fee=0 if bps==0 else 2.95
                    result=outcome(f,calendar,d,method,fee,bps)
                    rows.append(dict(ticker=ticker,method=method,signal=str(d.date()),bps=bps,**result))
                    if bps==15:
                        base=result
                if base['status']=='closed': busy_until=pd.Timestamp(base['exit'])
                elif base['status']=='open_at_cutoff': busy_until=END
    report={'status':'exploratory_not_validated','period':'2021-2025; 2026 not evaluated','tickers':len(frames),'screen_counts':screen_counts,'ep_classic_verified_trades':None,'ep_classic_gate':'Historical catalyst archive absent; technical proxies only','portfolio_simulated':False,'budget_per_independent_trade_eur':1000,'costs':'0 diagnostic, or EUR2.95/order plus 5/15/30 bps per side','limitations':['Current-list survivorship bias','Corporate actions not independently certified','Raw Yahoo volume units not independently certified','Daily-bar execution cannot recover intraday ordering: stop first','Historical context filter and discretionary selection not reproduced','Results not out-of-sample; no parameter optimization performed','No CAGR or portfolio drawdown calculated from overlapping trades'],'overall':{},'by_year':{},'focal':{},'sources':audit}
    for method in METHODS:
        for bps in [0,5,15,30]: report['overall'][f'{method}/{bps}']=summarize([r for r in rows if r['method']==method and r['bps']==bps])
        for year in range(2021,2026):report['by_year'][f'{method}/{year}']=summarize([r for r in rows if r['method']==method and r['bps']==15 and r['signal'].startswith(str(year))])
        for t in ['SOL.MI','GVS.MI','STLAM.MI','REY.MI','TGYM.MI','MAIRE.MI']:report['focal'][f'{method}/{t}']=summarize([r for r in rows if r['method']==method and r['bps']==15 and r['ticker']==t])
    (out/'report.json').write_text(json.dumps(report,indent=2,allow_nan=False))
    pd.DataFrame(rows).to_csv(out/'trades.csv',index=False)
    print(json.dumps({k:v for k,v in report['overall'].items() if k.endswith('/15')},indent=2))
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--raw',type=Path,required=True);p.add_argument('--out',type=Path,default=Path('stockbee-results'));a=p.parse_args();run(a.raw,a.out)
