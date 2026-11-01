"""Capital-constrained, past-only research. 2026 deliberately inaccessible here."""
from __future__ import annotations
import argparse, datetime as dt, gzip, hashlib, json, math, pathlib
import numpy as np
import pandas as pd
from italy_ml import features, FEATURES

ROOT=pathlib.Path(__file__).resolve().parent
END=pd.Timestamp('2026-01-01')
START=pd.Timestamp('2021-01-01')

def day(timestamp):
    return pd.Timestamp(timestamp,unit='s',tz='UTC').tz_convert('Europe/Rome').tz_localize(None).normalize()

def prepare_result(result):
    """Yahoo quotes use split-adjusted units; reconstruct nominal historical units.
    Future split metadata changes the denomination, not the predictor's future returns.
    """
    f=pd.DataFrame(result['indicators']['quote'][0], index=pd.DatetimeIndex([day(t) for t in result['timestamp']]))
    f['adjclose']=result['indicators']['adjclose'][0]['adjclose']
    f=f.loc[f.index<END].sort_index()
    if f.index.duplicated().any(): raise ValueError('duplicate market dates')
    f=f.dropna(subset=['open','high','low','close','volume','adjclose'])
    f=f[(f[['open','high','low','close','adjclose']]>0).all(axis=1)&(f.volume>0)]
    f=f[(f.high>=f[['open','close','low']].max(axis=1))&(f.low<=f[['open','close']].min(axis=1))]
    splits={day(e['date']):float(e['numerator'])/float(e['denominator']) for e in result.get('events',{}).get('splits',{}).values()}
    factor=pd.Series(1.,index=f.index)
    for d,ratio in splits.items(): factor.loc[f.index<d]*=ratio
    f['nominal_open']=f.open*factor; f['nominal_close']=f.close*factor
    f['split']=1.; f['dividend']=0.
    for d,ratio in splits.items():
        if d in f.index: f.loc[d,'split']=ratio
    for e in result.get('events',{}).get('dividends',{}).values():
        d=day(e['date'])
        if d in f.index: f.loc[d,'dividend']+=float(e['amount'])*factor.loc[d]
    return f

def candidates(frames,signal):
    rows=[]
    for ticker,f in frames.items():
        if signal not in f.index: continue
        # Only history up to signal; future availability cannot qualify/disqualify a name.
        history=f.loc[:signal]
        if len(history)<251: continue
        x=features(history).iloc[-1]
        if x[FEATURES].isna().any() or x.turnover<500000 or history.nominal_close.iloc[-1]<1: continue
        rows.append({'ticker':ticker,'r5':float(x.r5),'r60':float(x.r60)})
    return rows

def prepare(raw_dir,out):
    frames={}; audit=[]
    for path in sorted(pathlib.Path(raw_dir).glob('*.json')):
        result=json.loads(path.read_bytes())['chart']['result'][0]
        if not result or not result.get('timestamp'): continue
        ticker=path.name.removesuffix('.MI.json')
        f=prepare_result(result)
        if f.empty: continue
        frames[ticker]=f
        audit.append({'ticker':ticker,'provider_name':result['meta'].get('longName'),'source_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bars_before_2026':len(f)})
    calendar=frames['ENI'].index
    signals=calendar[calendar>='2018-01-01'][::21]
    entries={}
    for signal in signals:
        j=calendar.searchsorted(signal)+1
        if signal<START or j>=len(calendar): continue
        entries[str(calendar[j].date())]={'signal':str(signal.date()),'candidates':candidates(frames,signal)}
    days=calendar[(calendar>=START)&(calendar<END)]
    pack={'schema':2,'created_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'cutoff_exclusive':str(END.date()),'calendar':[str(d.date()) for d in days],'entries':entries,'bars':{},'sources':audit}
    for ticker,f in frames.items():
        z=f.loc[f.index>=START,['nominal_open','nominal_close','split','dividend']]
        pack['bars'][ticker]=[[str(d.date()),*[float(v) for v in r]] for d,r in z.iterrows()]
    out.write_bytes(gzip.compress(json.dumps(pack,separators=(',',':'),allow_nan=False).encode(),mtime=0))
    return pack

def buy_quantity(budget,price,fee,slip):
    # Entry and eventual exit fees reserved; never borrow from another position.
    return max(0,math.floor((budget-2*fee)/(price*(1+slip))))

def simulate(pack,method,capital,fee=2.95,bps=5,exclude=()):
    if pack['cutoff_exclusive']!='2026-01-01' or max(pack['calendar'])>='2026-01-01': raise ValueError('Reserved 2026 cannot be evaluated by this program')
    bars={t:{r[0]:r[1:] for r in rows} for t,rows in pack['bars'].items()}
    cash=float(capital); dividends=0.; fees=0.; slip_eur=0.; positions={}; trades=[]; orders=[]; curve=[]; stale_days=0; missing_buys=0; pending_sells=0
    slip=bps/10000
    def close(t,date,price,reason):
        nonlocal cash, fees, slip_eur
        p=positions.pop(t); value=p['qty']*price
        proceeds=value*(1-slip)-fee
        cash+=proceeds;fees+=fee;slip_eur+=value*slip
        trades.append({'ticker':t,'signal':p['signal'],'entry':p['entry'],'exit':date,'reason':reason,'entry_cost':p['entry_cost'],'exit_proceeds':proceeds,'dividends':p['dividends'],'pnl':proceeds+p['dividends']-p['entry_cost']})
    for date in pack['calendar']:
        # Existing holders receive ex-date dividend even if sold at today's open.
        for t,p in positions.items():
            bar=bars[t].get(date)
            if bar:
                p['qty']*=bar[2]
                p['mark']/=bar[2]
                credit=p['qty']*bar[3]
                p['dividends']+=credit;dividends+=credit
        rebalance=pack['entries'].get(date)
        if rebalance:
            for p in positions.values(): p['pending']=True
        for t in list(positions):
            if positions[t]['pending']:
                bar=bars[t].get(date)
                if bar: close(t,date,bar[0],'rebalance_or_delayed_exit')
                else: pending_sells+=1
        if rebalance:
            rows=rebalance['candidates']
            ranking=sorted(rows,key=lambda r:(r['r5'] if method=='reversal5' else -r['r60'],r['ticker']))[:10]
            # Max ten slots including positions whose sale could not be executed.
            free_slots=10-len(positions)
            budget=cash/max(1,free_slots)
            for r in ranking:
                t=r['ticker']
                if t in exclude:
                    orders.append({'date':date,'ticker':t,'status':'excluded_sensitivity_cash'});continue
                if t in positions or len(positions)>=10: continue
                bar=bars[t].get(date)
                if not bar:
                    missing_buys+=1; orders.append({'date':date,'ticker':t,'status':'missing_open_cash'});continue
                qty=buy_quantity(min(budget,cash),bar[0],fee,slip)
                if qty<=0:
                    orders.append({'date':date,'ticker':t,'status':'budget_insufficient'});continue
                value=qty*bar[0]; cost=value*(1+slip)+fee
                if cost>cash+1e-8: raise AssertionError('Cash violation')
                cash-=cost;fees+=fee;slip_eur+=value*slip
                positions[t]={'qty':qty,'entry':date,'signal':rebalance['signal'],'entry_cost':cost,'mark':bar[0],'mark_date':date,'dividends':0.,'pending':False}
                orders.append({'date':date,'ticker':t,'status':'buy','qty':qty,'price':bar[0],'cash_after':cash})
        stale=0
        for t,p in positions.items():
            bar=bars[t].get(date)
            if bar: p['mark']=bar[1];p['mark_date']=date
            else: stale+=1
        stale_days+=stale
        if date==pack['calendar'][-1]:
            for t in list(positions):
                bar=bars[t].get(date)
                if bar: close(t,date,bar[1],'test_end_close')
        equity=cash+dividends+sum(p['qty']*p['mark'] for p in positions.values())
        if cash < -1e-7: raise AssertionError('Negative cash')
        curve.append({'date':date,'equity':equity,'cash':cash,'dividend_reserve':dividends,'positions':len(positions),'stale_positions':stale})
    equity=np.array([capital]+[r['equity'] for r in curve]); peaks=np.maximum.accumulate(equity)
    years=(pd.Timestamp(pack['calendar'][-1])-pd.Timestamp(pack['calendar'][0])).days/365.25
    pnls=np.array([t['pnl'] for t in trades]);loss=-pnls[pnls<0].sum()
    by_year={}; previous=capital
    for year in range(2021,2026):
        rows=[r for r in curve if r['date'].startswith(str(year))]
        if rows: by_year[str(year)]=(rows[-1]['equity']/previous-1)*100;previous=rows[-1]['equity']
    contributions={}
    for t in trades: contributions[t['ticker']]=contributions.get(t['ticker'],0)+t['pnl']
    unresolved=sum(p['qty']*p['mark'] for p in positions.values())
    report={'initial_eur':capital,'final_equity_eur':float(equity[-1]),'return_pct':float((equity[-1]/capital-1)*100),'cagr_pct':float(((equity[-1]/capital)**(1/years)-1)*100),'max_daily_drawdown_pct':float((equity/peaks-1).min()*100),'trades':len(trades),'win_rate_pct':float((pnls>0).mean()*100) if len(pnls) else None,'profit_factor':float(pnls[pnls>0].sum()/loss) if loss else None,'fees_eur':fees,'slippage_eur':slip_eur,'dividends_reserved_eur':dividends,'annual_returns_pct':by_year,'stale_position_days':stale_days,'missing_buy_opens':missing_buys,'pending_sell_days':pending_sells,'unresolved_positions':list(positions),'unresolved_last_mark_eur':unresolved,'hypothetical_zero_unresolved_equity_eur':float(equity[-1]-unresolved),'contributions_eur':dict(sorted(contributions.items(),key=lambda item:-item[1])),'budget_skips':sum(o['status']=='budget_insufficient' for o in orders)}
    # Closed P&L includes dividends once. Open entry cost still remains invested.
    reconciled=capital+pnls.sum()+sum(p['qty']*p['mark']+p['dividends']-p['entry_cost'] for p in positions.values())
    if not math.isclose(equity[-1],reconciled,abs_tol=1e-6): raise AssertionError('P&L reconciliation')
    return report,curve,trades,orders

def evaluate(pack,out):
    out.mkdir(parents=True,exist_ok=True);reports={}
    for method in ['reversal5','momentum60']:
        for capital in [1000,10000]:
            for fee,bps in [(0,0),(2.95,5),(2.95,15),(2.95,30)]:
                key=f'{method}_{capital}_fee{fee}_bps{bps}'
                report,curve,trades,orders=simulate(pack,method,capital,fee,bps)
                reports[key]=report
                for label,rows in [('equity',curve),('trades',trades),('orders',orders)]:
                    pd.DataFrame(rows).to_csv(out/f'{key}_{label}.csv.gz',index=False,compression={'method':'gzip','mtime':0})
                print(key,round(report['return_pct'],2),round(report['max_daily_drawdown_pct'],2),flush=True)
    base=reports['reversal5_10000_fee2.95_bps5']
    top=list(base['contributions_eur'])[:3]
    report,curve,trades,orders=simulate(pack,'reversal5',10000,exclude=top)
    reports['reversal5_10000_exclude_top3_cash']=dict(report,excluded_tickers=top,diagnostic_only=True)
    gate={'historical_universe_complete':False,'corporate_actions_independently_verified':False,'final_2026_test_executed':False,'status':'blocked_by_data_quality_not_ready_for_production'}
    (out/'report.json').write_text(json.dumps({'protocol':'validation/PROTOCOL.md','reports':reports,'gate':gate},indent=2,allow_nan=False))
    return reports

def main():
    p=argparse.ArgumentParser();p.add_argument('--raw',default=str(ROOT/'output/raw'));p.add_argument('--snapshot',default=str(ROOT/'validation/market_2021_2025.json.gz'));p.add_argument('--prepare',action='store_true');p.add_argument('--out',default=str(ROOT/'validation/output'));args=p.parse_args()
    path=pathlib.Path(args.snapshot)
    pack=prepare(args.raw,path) if args.prepare else json.loads(gzip.decompress(path.read_bytes()))
    evaluate(pack,pathlib.Path(args.out))

if __name__=='__main__': main()
