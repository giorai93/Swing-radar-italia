"""Availability probe; guessed provider symbols are NOT accepted into the universe."""
import concurrent.futures, datetime, hashlib, json, pathlib, urllib.request
ROOT=pathlib.Path(__file__).resolve().parent
PDF='https://www.borsaitaliana.it/borsaitaliana/statistiche/statistiche-storiche/revoche.en.pdf'
CANDIDATES=[('ATL','ATLANTIA','2022-12-09'),('AUT','AUTOGRILL','2023-07-24'),('CERV','CERVED GROUP','2022-02-09'),('TOD',"TOD'S",'2024-06-07'),('SRS','SARAS','2024-09-11'),('US','UNIPOLSAI','2024-07-03'),('SAL','SALCEF GROUP','2024-11-19'),('IMA','IMA','2021-01-28')]
def probe(item):
 ticker,name,end=item
 url=f'https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.MI?range=10y&interval=1d&events=div%2Csplits'
 row={'company':name,'delisting_date':end,'source':PDF,'provider_symbol_candidate':ticker+'.MI','url':url,'retrieved_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'included_in_backtest':False}
 try:
  with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=20) as r: raw=r.read()
  result=json.loads(raw)['chart']['result'][0]
  row.update(status='requires_identity_and_terminal_event_verification',bars=len(result.get('timestamp',[])),provider_name=result['meta'].get('longName'),sha256=hashlib.sha256(raw).hexdigest())
 except Exception as e: row.update(status='unavailable',error=str(e))
 return row
if __name__=='__main__':
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool: rows=list(pool.map(probe,CANDIDATES))
 (ROOT/'delisted_probe.json').write_text(json.dumps(rows,indent=2))
 print(json.dumps(rows,indent=2))
