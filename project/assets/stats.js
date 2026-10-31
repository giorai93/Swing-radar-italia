/* Swing Radar Italia 1.9 — local, reproducible signal-level study.
   No network access, no future prices at the signal date, no broker execution. */
'use strict';

let statsSettings={capital:1000,fee:2.95,slippageBps:5,horizon:20};
let statsInitialised=false,statsRunCache=null,statsPriceCache=new Map(),statsPriceCacheKey='';
let statsMode='all';
const statsFmt=(x,d=2)=>Number.isFinite(x)?x.toLocaleString('it-IT',{minimumFractionDigits:d,maximumFractionDigits:d}):'N/D';

function statsValidSettings(s){
  return s&&Number.isInteger(s.capital)&&s.capital>=100&&s.capital<=1000000&&
    Number.isFinite(s.fee)&&s.fee>=0&&s.fee<=100&&Number.isInteger(s.slippageBps)&&
    s.slippageBps>=0&&s.slippageBps<=300&&[5,10,20].includes(s.horizon);
}
function statsInit(){
  if(statsInitialised)return;
  statsInitialised=true;
  try{if(window.Android&&typeof Android.getStatsSettings==='function'){
    const config=JSON.parse(Android.getStatsSettings());
    if(statsValidSettings(config))statsSettings=config;
  }}catch(ex){}
}
function statsSetParam(field,value){
  statsInit();
  if(!Object.prototype.hasOwnProperty.call(statsSettings,field))return;
  const next={...statsSettings,[field]:Number(value)};
  if(!statsValidSettings(next)){
    alert('Parametri non validi. Capitale 100–1.000.000 €, commissioni 0–100 €, slippage 0–300 bps, durata 5/10/20 sedute.');
    render();return;
  }
  statsSettings=next;statsRunCache=null;
  try{if(window.Android&&typeof Android.saveStatsSettings==='function')Android.saveStatsSettings(JSON.stringify(next));}catch(ex){}
  render();
}
function statsRefresh(){statsRunCache=null;statsPriceCache.clear();render();}
function statsSetMode(value){statsMode=value;render();}

function statsBars(raw){
  if(!Array.isArray(raw))return [];
  let prev='';
  return raw.filter(a=>{
    if(!Array.isArray(a)||a.length!==6||typeof a[0]!=='string'||!/^\d{4}-\d\d-\d\d$/.test(a[0])||a[0]<=prev)return false;
    if(!a.slice(1).every(x=>typeof x==='number'&&Number.isFinite(x)))return false;
    if(a[1]<=0||a[3]<=0||a[4]<=0||a[2]<Math.max(a[1],a[4])||a[3]>Math.min(a[1],a[4])||a[5]<0)return false;
    prev=a[0];return true;
  }).map(a=>({day:a[0],o:a[1],h:a[2],l:a[3],c:a[4],v:a[5]}));
}
function statsSimulateOne(signal,bars,opts){
  const out={ticker:signal.ticker,kind:signal.kind,asof:signal.asof,
    segment:signal.segment||'BASE',state:'excluded',reason:'',netR:null,pnl:null,
    exitDay:'',entryDay:'',exitReason:'',t2:null,forward:{}};
  const stop=Number(signal.stop),t1=Number(signal.target1),t2=Number(signal.target2);
  if(!(stop>0&&t1>stop&&t2>t1)){
    out.reason='Livelli stop/target non coerenti';return out;
  }
  if(!bars.length){out.reason='Storico prezzi indisponibile';return out;}
  const idx=bars.findIndex(x=>x.day===signal.asof);
  if(idx<0){out.reason='Data segnale non presente nella cache';return out;}
  if(idx+1>=bars.length){out.state='pending';out.reason='Attesa prima apertura successiva';return out;}
  const start=idx+1,first=bars[start],slip=opts.slippageBps/10000;
  if(first.o<=stop||first.o>=t1){
    out.reason='Apertura successiva già oltre stop o target 1';return out;
  }
  const entry=first.o*(1+slip),quantity=Math.floor((opts.capital-opts.fee)/entry);
  if(!quantity){out.reason='Capitale insufficiente per almeno una azione';return out;}
  if(entry<=stop||entry>=t1){out.reason='Slippage d’ingresso oltre stop o target';return out;}
  const risk=(entry-stop)*quantity;
  if(risk<=0){out.reason='Rischio unitario non valido';return out;}
  out.entryDay=first.day;out.entry=entry;out.quantity=quantity;out.risk=risk;
  const last=Math.min(start+opts.horizon,bars.length);
  // T2 is a separate hypothetical path: the main trade exits entirely at T1.
  if(t2>0){
    for(let i=start;i<last;i++){
      const b=bars[i];
      if(b.o<=stop){out.t2=false;break;}
      if(b.o>=t2){out.t2=true;break;}
      if(b.l<=stop){out.t2=false;break;} // stop-first on ambiguous daily bars
      if(b.h>=t2){out.t2=true;break;}
    }
    if(out.t2===null&&last-start===opts.horizon)out.t2=false;
  }
  // Independent forward returns, holding the position without any stop or target.
  for(const h of [5,10,20]){
    if(start+h<=bars.length){
      const close=bars[start+h-1].c*(1-slip);
      out.forward[h]=100*((close-entry)*quantity-2*opts.fee)/(entry*quantity+opts.fee);
    }
  }
  for(let i=start;i<last;i++){
    const b=bars[i];let sell=null,why='';
    if(b.o<=stop){sell=b.o;why='STOP · gap';}
    else if(b.o>=t1){sell=b.o;why='TARGET 1 · gap';}
    else if(b.l<=stop){sell=stop;why='STOP';} // stop-first if both touched
    else if(b.h>=t1){sell=t1;why='TARGET 1';}
    else if(i===start+opts.horizon-1){sell=b.c;why='TEMPO';}
    if(sell!==null){
      const net=(sell*(1-slip)-entry)*quantity-2*opts.fee;
      out.state='resolved';out.exitReason=why;out.exitDay=b.day;
      out.pnl=net;out.netR=net/risk;
      out.exit=sell*(1-slip);out.reason='Chiuso teoricamente';
      return out;
    }
  }
  out.state='pending';out.reason='Meno di '+opts.horizon+' sedute; stop e target non ancora raggiunti';
  return out;
}
function statsCompute(signals,getBars,opts){
  const seen=new Set(),rows=[],byTicker=new Map(),occupiedUntil=new Map();
  let repeated=0;
  // Simulate each independent trade idea from its FIRST signal. A later
  // observation while that hypothetical position is still open is not a
  // second, independently executable trade.
  const ordered=[...(signals||[])].sort((a,b)=>String(a?.asof||'').localeCompare(String(b?.asof||'')));
  for(const s of ordered){
    if(!s||!['FORZA','DEBOLEZZA','RECUPERO','EVENT_MOMENTUM'].includes(s.kind)||!s.ticker||!s.asof)continue;
    const key=s.ticker+'|'+s.kind+'|'+s.asof;
    if(seen.has(key))continue;seen.add(key);
    const direction=s.ticker+'|'+s.kind;
    if(occupiedUntil.has(direction)&&s.asof<occupiedUntil.get(direction)){
      repeated++;continue;
    }
    if(!byTicker.has(s.ticker)){
      try{byTicker.set(s.ticker,statsBars(getBars(s.ticker)));}
      catch(ex){byTicker.set(s.ticker,[]);}
    }
    const simulated=statsSimulateOne(s,byTicker.get(s.ticker),opts);
    rows.push(simulated);
    if(simulated.state==='resolved')occupiedUntil.set(direction,simulated.exitDay);
    else if(simulated.state==='pending')occupiedUntil.set(direction,'9999-12-31');
  }
  rows.sort((a,b)=>b.asof.localeCompare(a.asof)||a.ticker.localeCompare(b.ticker));
  return {rows,repeated,all:statsSummarize(rows),force:statsSummarize(rows.filter(x=>x.kind==='FORZA')),
    weak:statsSummarize(rows.filter(x=>x.kind==='DEBOLEZZA')),
    recovery:statsSummarize(rows.filter(x=>x.kind==='RECUPERO')),
    momentum:statsSummarize(rows.filter(x=>x.kind==='EVENT_MOMENTUM')),
    segments:Object.fromEntries(['LARGE','MID','SMALL','BASE'].map(x=>[x,statsSummarize(rows.filter(r=>r.segment===x))]))};
}
function statsSummarize(rows){
  const resolved=rows.filter(x=>x.state==='resolved'),pending=rows.filter(x=>x.state==='pending');
  const gains=resolved.filter(x=>x.netR>0),losses=resolved.filter(x=>x.netR<0);
  const grossPlus=gains.reduce((a,x)=>a+x.pnl,0),grossMinus=-losses.reduce((a,x)=>a+x.pnl,0);
  const forward={};
  for(const h of [5,10,20]){
    const vals=rows.map(x=>x.forward[h]).filter(Number.isFinite);
    forward[h]={n:vals.length,avg:vals.length?vals.reduce((a,b)=>a+b,0)/vals.length:null};
  }
  const t2Known=rows.filter(x=>x.t2!==null),t1=resolved.filter(x=>x.exitReason.startsWith('TARGET 1'));
  // This drawdown is explicitly NOT portfolio DD: independent, sequential, fixed 1% R trades.
  let equity=1,peak=1,worstDD=0;
  for(const x of [...resolved].sort((a,b)=>a.exitDay.localeCompare(b.exitDay))){
    equity*=Math.max(.001,1+.01*x.netR);peak=Math.max(peak,equity);
    worstDD=Math.max(worstDD,(peak-equity)/peak);
  }
  return {signals:rows.length,closed:resolved.length,pending:pending.length,
    excluded:rows.length-resolved.length-pending.length,
    winRate:resolved.length?gains.length/resolved.length*100:null,
    avgR:resolved.length?resolved.reduce((a,x)=>a+x.netR,0)/resolved.length:null,
    avgPnl:resolved.length?resolved.reduce((a,x)=>a+x.pnl,0)/resolved.length:null,
    pf:grossMinus>0?grossPlus/grossMinus:null,
    stopCount:resolved.filter(x=>x.exitReason.startsWith('STOP')).length,
    timeCount:resolved.filter(x=>x.exitReason==='TEMPO').length,
    t1Count:t1.length,t1Rate:resolved.length?t1.length/resolved.length*100:null,
    t2Count:t2Known.filter(x=>x.t2).length,t2Known:t2Known.length,
    t2Rate:t2Known.length?100*t2Known.filter(x=>x.t2).length/t2Known.length:null,
    forward,drawdownProxy:resolved.length?worstDD*100:null};
}
function statsCurrentReport(){
  statsInit();
  const updateKey=(snapshot.status?.lastRun||'')+'|'+(snapshot.cachedStocks||'')+'|'+
    (snapshot.status?.running||'')+'|'+JSON.stringify([snapshot.backtestSignals||snapshot.signals||[],snapshot.researchSignals||[]]);
  if(updateKey!==statsPriceCacheKey){statsPriceCacheKey=updateKey;statsPriceCache.clear();statsRunCache=null;}
  const settingsKey=JSON.stringify(statsSettings);
  if(statsRunCache&&statsRunCache.settingsKey===settingsKey)return statsRunCache.result;
  const result=statsCompute([...(snapshot.backtestSignals||snapshot.signals||[]),...(snapshot.researchSignals||[])],ticker=>{
    if(!statsPriceCache.has(ticker)){
      let raw=[];
      try{if(window.Android&&typeof Android.getChartData==='function'){
        const data=JSON.parse(Android.getChartData(ticker));if(Array.isArray(data.bars))raw=data.bars;
      }}catch(ex){}
      statsPriceCache.set(ticker,raw);
    }
    return statsPriceCache.get(ticker);
  },statsSettings);
  statsRunCache={settingsKey,result};return result;
}
function statsPct(x){return Number.isFinite(x)?statsFmt(x,1)+'%':'N/D';}
function statsDetailBox(label,value,note=''){
  return '<div class="stats-metric"><span>'+esc(label)+'</span><strong>'+esc(value)+'</strong>'+ (note?'<small>'+esc(note)+'</small>':'')+'</div>';
}
function statsSummaryTable(name,a){
  return '<div class="stats-summary"><div><strong>'+esc(name)+'</strong><small>'+a.closed+'/'+a.signals+' esiti chiusi</small></div>'+
    '<div><span>Win rate</span><strong>'+statsPct(a.winRate)+'</strong></div>'+
    '<div><span>R medio netto</span><strong>'+(a.avgR===null?'N/D':statsFmt(a.avgR,2)+'R')+'</strong></div>'+
    '<div><span>Profit Factor</span><strong>'+(a.pf===null?'N/D':statsFmt(a.pf,2))+'</strong></div></div>';
}
function statsRender(){
  const r=statsCurrentReport(),a=r.all,s=statsSettings;
  let out='<div class="topline"><h2>📊 Statistiche setup</h2><small>Simulazione locale</small></div>'+
    '<div class="section"><p><strong>Esperimento, non rendimento reale.</strong> Entra alla <strong>prima apertura successiva</strong> al setup; esce a target 1, stop oppure dopo '+s.horizon+' sedute. Stop prima del target se entrambi sono toccati nella stessa barra. Dati EOD: sequenza intraday sconosciuta. Include quattro strategie; solo le zone che rispettano tutte e quattro le condizioni entrano nella simulazione Recupero. Momentum usa prezzi e volumi, non notizie storiche verificate.</p></div>'+
    '<div class="section"><h3>Parametri simulazione</h3><div class="stats-form">'+
      '<label>Importo per acquisto (€)<input inputmode="numeric" type="number" min="100" max="1000000" step="100" value="'+s.capital+'" onchange="statsSetParam(\'capital\',this.value)"></label>'+
      '<label>Commissione per lato (€)<input inputmode="decimal" type="number" min="0" max="100" step="0.01" value="'+s.fee+'" onchange="statsSetParam(\'fee\',this.value)"></label>'+
      '<label>Slippage per lato (bps)<input inputmode="numeric" type="number" min="0" max="300" step="1" value="'+s.slippageBps+'" onchange="statsSetParam(\'slippageBps\',this.value)"></label>'+
      '<label>Uscita massima (sedute)<select onchange="statsSetParam(\'horizon\',this.value)">'+[5,10,20].map(n=>'<option value="'+n+'" '+(n===s.horizon?'selected':'')+'>'+n+'</option>').join('')+'</select></label></div>'+
      '<button class="action" onclick="statsRefresh()">↻ Ricalcola dallo storico locale</button></div>';
  if(a.signals===0)out+='<div class="alert"><strong>Nessun setup confermato nel database</strong>Apri Radar e avvia una scansione. Le statistiche compariranno quando ci saranno segnali e prezzi successivi sufficienti; non verranno creati risultati fittizi.</div>';
  out+='<div class="stats-grid">'+statsDetailBox('Idee indipendenti',String(a.signals),'Quattro strategie indipendenti')+
    statsDetailBox('Rilevamenti ripetuti',String(r.repeated),'Esclusi come trade doppi')+
    statsDetailBox('Esiti chiusi',String(a.closed),'Stop, target 1 o tempo')+
    statsDetailBox('In osservazione',String(a.pending),'Dati futuri insufficienti')+
    statsDetailBox('Non simulabili',String(a.excluded),'Dettaglio sotto')+
    statsDetailBox('Win rate netto',statsPct(a.winRate),'Esiti chiusi')+
    statsDetailBox('R medio netto',a.avgR===null?'N/D':statsFmt(a.avgR,2)+' R','Commissioni e slippage inclusi')+
    statsDetailBox('Profit Factor',a.pf===null?'N/D':statsFmt(a.pf,2),'N/D se non esistono perdite')+
    statsDetailBox('DD sequenziale proxy',statsPct(a.drawdownProxy),'Non drawdown di portafoglio')+'</div>';
  out+='<div class="section"><h3>Regole delle nuove simulazioni</h3><p>Recupero: titolo sotto SMA200, calo 60 sedute almeno 12%, entro +12% dal supporto delle precedenti 120 sedute, RSI2 ≤70, chiusura nella parte alta almeno 45%. Stop sotto supporto e minimo della barra; target 1 massimo tra SMA20 e 1,5R; target 2 massimo tra SMA50 e 2,5R. Rischio al segnale massimo 18%.</p><p>Momentum: rialzo ≥3,5% in una seduta oppure ≥1,5% con rialzo su due sedute ≥6%; volume relativo ≥1,15 e chiusura almeno al 60% della barra. Stop sotto minimo della barra di 0,25 ATR14; target 1 a 2R e target 2 a 3R; rischio massimo 12%. Livelli fissati alla chiusura del segnale.</p><p>Entrata alla prima apertura successiva, stessi costi e durata per tutti. Nessuna replica certificata Websim. Ipotesi definite dopo gli esempi osservati, non test fuori campione. Le nuove simulazioni compaiono dopo una nuova scansione.</p></div>';
  out+='<div class="section"><h3>Confronto strategie</h3>'+statsSummaryTable('↗ Forza',r.force)+statsSummaryTable('↘ Debolezza',r.weak)+statsSummaryTable('⌁ Recupero su supporto',r.recovery)+statsSummaryTable('⚡ Momentum prezzo-volume',r.momentum)+
    '<p class="stats-foot">Le medie includono solo esiti definitivi. Se un gruppo ha pochi casi, i valori non sono conclusivi.</p></div>';
  out+='<div class="section"><h3>Target e stop · esiti della simulazione</h3>'+
    '<div class="detail-line"><span>Target 1 raggiunto per primo</span><strong>'+a.t1Count+' / '+a.closed+' ('+statsPct(a.t1Rate)+')</strong></div>'+
    '<div class="detail-line"><span>Stop raggiunto per primo</span><strong>'+a.stopCount+' / '+a.closed+'</strong></div>'+
    '<div class="detail-line"><span>Uscite per tempo</span><strong>'+a.timeCount+' / '+a.closed+'</strong></div>'+
    '<div class="detail-line"><span>Target 2 teorico prima dello stop</span><strong>'+a.t2Count+' / '+a.t2Known+' ('+statsPct(a.t2Rate)+')</strong></div>'+
    '<p class="stats-foot">Target 2 è un percorso <strong>alternativo</strong> ipotetico: non un guadagno realizzato dopo l’uscita a target 1.</p></div>';
  out+='<div class="section"><h3>Rendimento a 5, 10 e 20 sedute</h3><p>Simula mantenimento fino a quella chiusura, senza stop/target: % media netta sull’importo effettivamente investito.</p>'+
    [5,10,20].map(h=>'<div class="detail-line"><span>'+h+' sedute ('+a.forward[h].n+' osservazioni complete)</span><strong>'+statsPct(a.forward[h].avg)+'</strong></div>').join('')+
    '<p class="stats-foot">Le osservazioni non ancora mature non entrano nella media: ogni orizzonte ha un proprio denominatore.</p></div>';
  out+='<div class="section"><h3>Large, Mid e Small Cap</h3>'+['LARGE','MID','SMALL','BASE'].map(seg=>statsSummaryTable(segmentLabel(seg),r.segments[seg])).join('')+
    '<p class="stats-foot">Le fasce sono indicative e rispecchiano l’etichetta disponibile al momento del segnale.</p></div>';
  out+='<div class="section"><h3>Audit dei segnali</h3><div class="chips">'+
    [['all','Tutti'],['resolved','Chiusi'],['pending','In corso'],['excluded','Non simulabili']].map(([value,name])=>
      '<button class="chip '+(statsMode===value?'sel':'')+'" onclick="statsSetMode(\''+value+'\')">'+name+'</button>').join('')+'</div>';
  const selected=r.rows.filter(x=>statsMode==='all'||x.state===statsMode);
  out+='<p>'+selected.length+' segnali in questa vista. Gli esiti sono ipotetici; il diario registra separatamente gli acquisti reali.</p>';
  if(!selected.length)out+='<div class="empty">Nessun segnale per questo filtro. Aggiorna la scansione per accumulare uno storico più ampio.</div>';
  else for(const x of selected.slice(0,120)){
    const name=x.state==='resolved'?(x.exitReason+' · '+statsFmt(x.netR,2)+'R'):x.state==='pending'?'IN CORSO':'NON SIMULABILE';
    const tone=x.state==='pending'?'#bc7212':x.state==='excluded'?'#667f93':x.netR>=0?'#0d996e':'#c83c52';
    out+='<div class="stats-result"><div><strong>'+esc(x.ticker)+' · '+esc(x.kind)+'</strong><small>'+esc(x.asof)+' · '+esc(segmentLabel(x.segment))+'</small></div>'+
      '<div><strong style="color:'+tone+'">'+esc(name)+'</strong><small>'+esc(x.state==='resolved'?('P&L '+statsFmt(x.pnl,2)+' € · uscita '+x.exitDay):x.reason)+'</small></div>'+ 
      '<button class="action" onclick="openChart(\''+esc(x.ticker)+'\',\''+esc(x.asof)+'\',\''+esc(x.kind)+'\')">📈</button></div>';
  }
  out+='</div><div class="section"><h3>Quanto sono affidabili queste cifre?</h3><p>'+(
    a.closed<30?'<strong>Campione insufficiente ('+a.closed+' casi chiusi).</strong> Evita conclusioni sulla redditività delle strategie. ':'')+
    'Storico limitato ai segnali salvati dall’app (recupero retrospettivo recente), ai soli titoli oggi coperti e ai prezzi ancora in cache. Rilevamenti sullo stesso titolo/strategia sono esclusi finché il primo trade teorico è aperto. Questo non è un backtest completo né fuori campione: possono esistere <strong>survivorship bias, selezione del campione e trade sovrapposti fra titoli diversi</strong>. Le quotazioni Yahoo non ufficiali non simulano spread o esecuzioni reali. Il DD visualizzato concatena singoli trade a rischio fisso 1%, ignorando sovrapposizioni e vincoli di capitale: <strong>non è il MaxDD di un portafoglio</strong>.</p></div>';
  return out;
}
