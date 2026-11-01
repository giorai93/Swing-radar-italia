"""Evaluate documented MarketSurge fields. No trade signals or invented imputation.
Input must already match vendor field semantics; raw ESEF facts are not interchangeable.
"""
import argparse, datetime as dt, json, math
from pathlib import Path
SOURCE='https://stockbee.blogspot.com/2025/09/scans-irun-in-marketsurge-to-find.html'
# Visual transcription of Screen Summary, first five images, 1 September 2025.
COMMON={'annual_sales_usd_m':25,'avg_volume_50d_shares':100000,'price_usd':1}
PROFILES={
 'bucket1_growth':dict(COMMON,sales_growth_last_q_pct=39,avg_sales_growth_2q_pct=39),
 'bucket1_fast_growth':dict(COMMON,sales_growth_last_q_pct=99),
 'bucket2_eps_sales':dict(COMMON,eps_growth_last_q_pct=39,eps_growth_prior_q_pct=39,sales_growth_last_q_pct=20,avg_sales_growth_2q_pct=20,avg_volume_50d_shares=200000,price_usd=3),
}

def timestamp(s):
    t=dt.datetime.fromisoformat(s.replace('Z','+00:00'))
    if t.tzinfo is None:raise ValueError('Timestamp must include timezone')
    return t

def evaluate(record,as_of):
    cutoff=timestamp(as_of);results={}
    for profile,criteria in PROFILES.items():
        checks=[]
        for field,minimum in criteria.items():
            fact=record.get('facts',{}).get(field)
            status='unknown';reason='missing_fact';value=None
            if fact is not None:
                value=fact.get('value')
                if isinstance(value,bool) or not isinstance(value,(float,int)) or not math.isfinite(value):reason='invalid_numeric_value'
                elif fact.get('definition_verified') is not True:reason='vendor_field_definition_not_verified'
                elif not fact.get('source_url') or not fact.get('period'):reason='missing_provenance'
                else:
                    try:
                        public=timestamp(fact['published_at']);available=timestamp(fact['available_at'])
                        if available<public:reason='availability_before_publication'
                        elif max(public,available)>cutoff:reason='not_available_as_of'
                        else:status='pass' if value>=minimum else 'fail';reason='threshold_comparison'
                    except (KeyError,ValueError,TypeError,AttributeError):reason='invalid_timestamp'
            if not isinstance(value,(int,float)) or isinstance(value,bool) or not math.isfinite(value): value=None
            checks.append({'field':field,'minimum':minimum,'value':value,'status':status,'reason':reason})
        statuses={c['status'] for c in checks}
        # A known failure is enough to reject, even if other fields are missing.
        state='fail' if 'fail' in statuses else ('unknown' if 'unknown' in statuses else 'pass')
        results[profile]={'status':state,'checks':checks,'source':SOURCE,'source_date':'2025-09-01','trade_signal':False}
    return {'issuer':record.get('issuer'),'as_of':as_of,'profiles':results,'scope':'Historical USA fundamental screen only; no entry, exit, ranking or profitability claim','bucket0_status':'blocked: date range truncated in published images; do not infer endpoint'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('input',type=Path);p.add_argument('--as-of',required=True);args=p.parse_args()
    print(json.dumps(evaluate(json.loads(args.input.read_text()),args.as_of),indent=2,allow_nan=False))
