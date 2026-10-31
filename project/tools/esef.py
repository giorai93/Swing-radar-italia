"""Strict, deliberately small ESEF reader. No inferred extension mappings or financial data.
Supported scope: annual consolidated IFRS, dimensionless contexts, EUR, EUR/share.
Unsupported/ambiguous facts remain missing; malformed documents fail closed.
"""
import argparse, datetime as dt, hashlib, io, json, re, urllib.request, zipfile
from decimal import Decimal
from pathlib import Path
from lxml import etree as ET

ROOT = Path(__file__).resolve().parents[1]
X = 'http://www.xbrl.org/2003/instance'
IX = 'http://www.xbrl.org/2013/inlineXBRL'
D = 'http://xbrl.org/2006/xbrldi'
TAGS = {
 'revenue': 'Revenue', 'profit': 'ProfitLoss', 'parentProfit': 'ProfitLossAttributableToOwnersOfParent',
 'equity': 'Equity', 'parentEquity': 'EquityAttributableToOwnersOfParent',
 'assets': 'Assets', 'operatingCashFlow': 'CashFlowsFromUsedInOperatingActivities',
 'eps': 'BasicEarningsLossPerShare'
}

def numeric(e):
    if e.get('{http://www.w3.org/2001/XMLSchema-instance}nil') in ('true','1'): return None
    text = ''.join(e.itertext()).strip()
    text = re.sub(r'[\s\u00a0\u202f]', '', text)
    fmt = e.get('format','').split(':')[-1]
    if fmt in ('fixed-zero','zerodash','numdash'):
        if re.search(r'[1-9]',text): raise ValueError('Zero transform contradicts visible nonzero value')
        text = '0'
    elif fmt in ('num-comma-decimal','numcommadecimal'): text = text.replace('.','').replace(',', '.')
    elif fmt in ('num-dot-decimal','numdotdecimal'): text = text.replace(',','')
    elif fmt: raise ValueError('Unsupported transform '+fmt)
    if not re.fullmatch(r'[+-]?\d+(\.\d+)?',text): raise ValueError('Invalid number '+text[:30])
    v = Decimal(text) * Decimal(10) ** int(e.get('scale','0'))
    if e.get('sign') == '-': v = -v
    if not v.is_finite(): raise ValueError('Nonfinite number')
    return float(v)

def parse(blob, source):
    if blob.startswith(b'PK'):
        z = zipfile.ZipFile(io.BytesIO(blob))
        if sum(n.file_size for n in z.infolist()) > 150_000_000: raise ValueError('Oversized ZIP')
        docs = [n for n in z.namelist() if n.lower().endswith(('.xhtml','.html'))]
        if len(docs)!=1: raise ValueError('Expected one report, found '+str(len(docs)))
        xml=z.read(docs[0])
    else:
        docs=[source['url'].rsplit('/',1)[-1]]
        xml=blob
    root = ET.fromstring(xml, ET.XMLParser(resolve_entities=False,no_network=True))
    contexts={}; units={}
    for c in root.iter('{'+X+'}context'):
        if c.find('.//{'+D+'}explicitMember') is not None or c.find('.//{'+D+'}typedMember') is not None: continue
        if c.find('.//{'+X+'}scenario') is not None or c.find('.//{'+X+'}segment') is not None: continue
        if c.findtext('.//{'+X+'}identifier') != source['lei']: continue
        end=c.findtext('.//{'+X+'}endDate') or c.findtext('.//{'+X+'}instant')
        start=c.findtext('.//{'+X+'}startDate')
        contexts[c.get('id')] = (start,end)
    for u in root.iter('{'+X+'}unit'):
        # Preserve qualified measures; reject unrelated currencies and unit structures.
        measures=[(m.text or '').split(':')[-1] for m in u.iter('{'+X+'}measure')]
        units[u.get('id')] = 'EUR' if measures==['EUR'] else 'EUR/share' if measures==['EUR','shares'] and u.find('{'+X+'}divide') is not None else None
    facts=[]; warnings=[]
    for f in root.iter('{'+IX+'}nonFraction'):
        name=f.get('name',''); prefix,_,local=name.partition(':')
        if local not in TAGS.values() or 'xbrl.ifrs.org' not in f.nsmap.get(prefix,''): continue
        period=contexts.get(f.get('contextRef'));unit=units.get(f.get('unitRef'))
        if not period or not unit: continue
        start,end=period
        if not end: continue
        year=int(end[:4]); instant=local in ('Equity','EquityAttributableToOwnersOfParent','Assets')
        if end!=f'{year}-12-31' or (start is not None if instant else start!=f'{year}-01-01'): continue
        if unit!=('EUR/share' if local=='BasicEarningsLossPerShare' else 'EUR'): continue
        try: value=numeric(f)
        except ValueError as e:
            warnings.append(name+': '+str(e));continue
        if value is None: continue
        facts.append(dict(concept=name,value=value,unit=unit,start=start,end=end,context=f.get('contextRef'),id=f.get('id'),decimals=f.get('decimals'),document=docs[0]))
    selected={}
    for key,tag in TAGS.items():
        for year in (source['year'],source['year']-1):
            candidates=[f for f in facts if f['concept'].split(':')[-1]==tag and f['end']==f'{year}-12-31']
            if not candidates: continue
            if len({f['value'] for f in candidates})!=1: raise ValueError(f'Conflicting facts {key} {year}')
            selected[f'{key}_{year}']=candidates[0]
    y=source['year']
    def val(k,year=y): return selected.get(f'{k}_{year}',{}).get('value')
    if any(val(k) is None for k in ('revenue','profit','equity','assets')): raise ValueError('Missing core consolidated IFRS facts: '+str(list(selected)))
    if val('revenue')<=0 or val('assets')<=0: raise ValueError('Invalid core balance values')
    def ratio(a,b,scale=1): return a/b*scale if a is not None and b is not None and b>0 else None
    prev=val('parentEquity',y-1); cur=val('parentEquity')
    avg=(prev+cur)/2 if prev is not None and cur is not None and prev>0 and cur>0 else None
    metrics=dict(revenue_growth_pct=ratio(val('revenue')-val('revenue',y-1),val('revenue',y-1),100) if val('revenue',y-1) is not None else None,
        roe_pct=ratio(val('parentProfit'),avg,100),net_margin_pct=ratio(val('profit'),val('revenue'),100),
        equity_ratio_pct=ratio(val('equity'),val('assets'),100),cash_conversion=ratio(val('operatingCashFlow'),val('profit')),eps=val('eps'))
    if val('revenue',y-1) is None: warnings.append('Previous-year revenue context missing; no YoY inferred')
    if warnings:
        metrics={k:None for k in metrics}
    return dict(dataStatus='needs_review' if warnings else 'verified',warnings=warnings,name=source['name'],sourceUrl=source['url'],sourcePage=source['listing'],publishedAt=source['publishedAt'],
        periodEnd=f'{y}-12-31',basis='Annual consolidated IFRS; not TTM',lei=source['lei'],sha256=hashlib.sha256(blob).hexdigest(),
        retrievedAt=dt.datetime.now(dt.timezone.utc).isoformat(),facts=selected,**metrics)

def download(url):
    req=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0 SwingRadar/3.1 public financial research'})
    with urllib.request.urlopen(req,timeout=60) as r:
        b=r.read(40_000_001)
        if len(b)>40_000_000: raise ValueError('Oversized download')
        return b

def quote(ticker):
    url=f'https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.MI?range=10d&interval=1d'
    raw=json.loads(download(url));r=raw['chart']['result'][0]
    if r['meta']['currency']!='EUR': raise ValueError('Non-EUR quote')
    cutoff=dt.datetime.now(dt.timezone.utc).date()
    pairs=[(t,c) for t,c in zip(r['timestamp'],r['indicators']['quote'][0]['close']) if c and c>0 and dt.datetime.fromtimestamp(t,dt.timezone.utc).date()<cutoff]
    t,c=pairs[-1];return dict(price=c,priceDate=dt.datetime.fromtimestamp(t,dt.timezone.utc).date().isoformat(),priceSource=url)

def main():
    p=argparse.ArgumentParser();p.add_argument('--local-dir');p.add_argument('--strict',action='store_true');a=p.parse_args()
    sources=json.loads((ROOT/'data/esef_sources.json').read_text());values={};errors={}
    for ticker,source in sources.items():
        try:
            b=(Path(a.local_dir)/(ticker+'.zip')).read_bytes() if a.local_dir else download(source['url'])
            m=parse(b,source)
            try: m.update(quote(ticker))
            except Exception as e: m['priceError']=str(e)
            values[ticker]=m;print(ticker,json.dumps({k:v for k,v in m.items() if k!='facts'}),flush=True)
        except Exception as e: errors[ticker]=str(e);print(ticker,'ERROR',str(e),flush=True)
    out=dict(schemaVersion=1,updatedAt=dt.datetime.now(dt.timezone.utc).isoformat(),values=values,errors=errors)
    (ROOT/'assets/fundamentals.json').write_text(json.dumps(out,indent=2,ensure_ascii=False)+'\n')
    if a.strict and errors: raise SystemExit('Live verification failed: '+str(errors))
if __name__=='__main__': main()
