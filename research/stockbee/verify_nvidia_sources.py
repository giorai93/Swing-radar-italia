"""Live-source audit of TWO named NVIDIA releases. Not a universal extractor.
Reads exact summary tables; refuses unexpected structure. No news-time inference.
"""
import argparse, datetime as dt, hashlib, json, re, urllib.request
from pathlib import Path
from lxml import html
from fundamental_screen import evaluate
ROOT=Path(__file__).resolve().parent
SOURCES={
'q1':{'url':'https://investor.nvidia.com/news/press-release-details/2025/NVIDIA-Announces-Financial-Results-for-First-Quarter-Fiscal-2026/default.aspx','period':'Q1 FY26','previous_year':'Q1 FY25','published_date':'2025-05-28'},
'q2':{'url':'https://investor.nvidia.com/news/press-release-details/2025/NVIDIA-Announces-Financial-Results-for-Second-Quarter-Fiscal-2026/default.aspx','period':'Q2 FY26','previous_year':'Q2 FY25','published_date':'2025-08-27'}}

def parse(data,source):
    doc=html.fromstring(data)
    date=doc.xpath('//span[contains(@class,"module_date-text")]')
    if len(date)!=1:raise ValueError('Missing or ambiguous release date')
    published=dt.datetime.strptime(' '.join(date[0].text_content().split()),'%B %d, %Y').date().isoformat()
    if published!=source['published_date']:raise ValueError('Unexpected release date')
    result={}
    for table in doc.xpath('//table'):
        rows=[[s for s in [' '.join(c.text_content().split()) for c in tr.xpath('./th|./td')] if s] for tr in table.xpath('.//tr')]
        if not rows or rows[0] not in [['GAAP'],['Non-GAAP']]:continue
        basis=rows[0][0]
        if basis in result:raise ValueError('Duplicate summary table')
        header=rows[1]
        if len(header)!=6 or header[1]!=source['period'] or header[3]!=source['previous_year'] or header[-1]!='Y/Y':raise ValueError('Unexpected summary columns')
        fields={}
        for row in rows[2:]:
            if not row: continue
            label=row[0].rstrip('*')
            if label not in ['Revenue','Diluted earnings per share']:continue
            if len(row)!=6:raise ValueError('Unexpected metric columns')
            if label in fields:raise ValueError('Duplicate metric')
            def number(s):return float(s.replace('$','').replace(',',''))
            current,prior=number(row[1]),number(row[3])
            if prior<=0:raise ValueError('Nonpositive baseline: growth not automatically interpretable')
            rounded=float(row[-1].replace('%',''))
            fields[label]={'current':current,'previous_year':prior,'calculated_yoy_pct':100*(current/prior-1),'reported_rounded_yoy_pct':rounded,'unit':'USD millions' if label=='Revenue' else 'USD/share','row_label':row[0]}
        if set(fields)!={'Revenue','Diluted earnings per share'}:raise ValueError('Missing metrics')
        result[basis]=fields
    if set(result)!={'GAAP','Non-GAAP'}:raise ValueError('Missing accounting basis')
    return {'source_url':source['url'],'source_sha256':hashlib.sha256(data).hexdigest(),'period':source['period'],'published_date':published,'publication_time':None,'publication_time_precision':'date_only','metrics':result}

def audit(raw_dir,out,refresh=False):
    raw_dir.mkdir(parents=True,exist_ok=True);out.mkdir(parents=True,exist_ok=True);data={}
    for q,source in SOURCES.items():
        path=raw_dir/(q+'.html')
        if refresh or not path.exists():
            req=urllib.request.Request(source['url'],headers={'User-Agent':'Mozilla/5.0'})
            with urllib.request.urlopen(req,timeout=30) as response:body=response.read()
            parse(body,source) # Validate BEFORE replacing the source cache.
            path.write_bytes(body)
        data[q]=parse(path.read_bytes(),source)
    # Independently checked against the visible issuer tables during this research.
    expected={'q1':{'Revenue':(44062,26044),'Diluted earnings per share':(.76,.60)},'q2':{'Revenue':(46743,30040),'Diluted earnings per share':(1.08,.67)}}
    checks=[]
    for q,fields in expected.items():
        for name,values in fields.items():
            metric=data[q]['metrics']['GAAP'][name]
            if (metric['current'],metric['previous_year'])!=values:raise AssertionError(f'Live reference mismatch: {q}/{name}')
            checks.append(q+'/'+name)
    for q,values in {'q1':(.81,.61),'q2':(1.05,.68)}.items():
        m=data[q]['metrics']['Non-GAAP']['Diluted earnings per share']
        if (m['current'],m['previous_year'])!=values:raise AssertionError('Non-GAAP reference mismatch')
        checks.append(q+'/Non-GAAP EPS')
    growth=[data[q]['metrics']['GAAP']['Revenue']['calculated_yoy_pct'] for q in ['q1','q2']]
    # This arithmetic diagnostic is NOT a certified MarketSurge field.
    derived={'latest_revenue_yoy_pct':growth[1],'mean_two_revenue_yoy_pct':sum(growth)/2,'vendor_equivalence_verified':False}
    record={'issuer':'NVIDIA / NVDA','facts':{}}
    fieldmap={'sales_growth_last_q_pct':derived['latest_revenue_yoy_pct'],'avg_sales_growth_2q_pct':derived['mean_two_revenue_yoy_pct'],'eps_growth_last_q_pct':data['q2']['metrics']['GAAP']['Diluted earnings per share']['calculated_yoy_pct'],'eps_growth_prior_q_pct':data['q1']['metrics']['GAAP']['Diluted earnings per share']['calculated_yoy_pct']}
    for key,value in fieldmap.items():
        source=data['q1' if key=='eps_growth_prior_q_pct' else 'q2']
        record['facts'][key]={'value':value,'period':source['period'],'source_url':source['source_url'],'published_date':source['published_date'],'published_at':None,'available_at':None,'definition_verified':False,'basis':'GAAP; arithmetic diagnostic only','warning':'Vendor definition and exact release time unverified; never manufacture historical timestamps'}
    screen=evaluate(record,'2025-09-02T12:00:00Z')
    if any(s['status']!='unknown' for s in screen['profiles'].values()):raise AssertionError('Incomplete real case must not be classified as complete')
    report={'issuer':'NVIDIA','source_validation':'six metric pairs checked against issuer tables','reference_checks_passed':checks,'releases':data,'derived_diagnostics':derived,'screen':screen,'not_a_signal':True,'gaps':['MarketSurge field definitions/adjustments not certified','Exact historical publication times absent','Historical annual-sales definition, price and volume fields not supplied','No historical consensus comparison','No trade or return calculated']}
    (out/'nvda_source_audit.json').write_text(json.dumps(report,indent=2,allow_nan=False)+'\n')
    (out/'nvda_incomplete_input.json').write_text(json.dumps(record,indent=2,allow_nan=False)+'\n')
    print(json.dumps({'reference_checks':len(checks),'derived':derived,'profiles':{k:v['status'] for k,v in screen['profiles'].items()}},indent=2))
    return report
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--raw',type=Path,default=ROOT/'company_checks/raw');p.add_argument('--out',type=Path,default=ROOT/'company_checks');p.add_argument('--refresh',action='store_true');a=p.parse_args();audit(a.raw,a.out,a.refresh)
