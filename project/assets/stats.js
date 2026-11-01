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
  out.mature=start+opts.horizon<=bars.length;
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
  const matured=resolved.filter(x=>x.mature);
  return {matureClosed:matured.length,matureAvgR:matured.length?matured.reduce((a,x)=>a+x.netR,0)/matured.length:null,signals:rows.length,closed:resolved.length,pending:pending.length,
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
let statsStrategy='all';
function statsSelectStrategy(value){statsStrategy=value;render();}
function statsNav(){return '<div class="chips"><button class="chip '+(tab==='stats'?'sel':'')+'" onclick="nav(\'stats\')">Confronto strategie</button><button class="chip '+(tab==='history'?'sel':'')+'" onclick="nav(\'history\')">Archivio simulazioni</button></div>';}
function statsRender(){
  const r=statsCurrentReport(),a=r.all,s=statsSettings;
  const groups=[['Forza / Breakout',r.force],['Debolezza / Pullback',r.weak],['Recupero su supporto',r.recovery],['Momentum prezzo-volume',r.momentum]];
  const measured=groups.filter(x=>x[1].closed>0),negative=measured.filter(x=>x[1].avgR<0);
  let out='<div class="topline"><h2>Risultati delle simulazioni</h2><small>Non operazioni reali</small></div>'+statsNav();
  out+='<div class="result-verdict"><h3>'+(measured.length===0?'Risultati non ancora disponibili':negative.length===measured.length?'Tutte le strategie misurate sono negative':negative.length+' strategie su '+measured.length+' con R medio negativo')+'</h3><p>'+a.closed+' operazioni concluse; '+a.pending+' ancora aperte; '+a.excluded+' non simulabili. '+(a.closed?'Un Profit Factor sotto 1 indica perdite superiori ai guadagni nel campione. ':'Avvia una scansione per raccogliere i dati. ')+'Non è una prova fuori campione: la finestra recente può favorire gli esiti che si chiudono prima.</p></div>';
  out+='<div class="section"><h3>Quale strategia ha funzionato nel campione?</h3>'+groups.map(([name,v])=>statsSummaryTable(name,v)).join('')+'<p>Per ciascuna strategia: operazioni chiuse, percentuale in profitto, R medio netto e Profit Factor. Un R è il rischio iniziale dell’operazione. N/D significa che il dato non è calcolabile.</p></div>';
  const mature=a.matureClosed||0;
  out+='<div class="section"><h3>Quanto è completo il campione?</h3><p>'+a.pending+' posizioni teoriche non hanno ancora un esito. Solo '+mature+' operazioni chiuse hanno anche '+s.horizon+' sedute successive disponibili. R medio di queste ultime: '+(a.matureAvgR===null?'N/D':statsFmt(a.matureAvgR,2)+'R')+'.</p><p>Non è un rendimento di portafoglio: posizioni e strategie possono sovrapporsi e non condividono un limite di capitale.</p></div>';
  out+='<details><summary>Costi e regole di esecuzione</summary><div class="stats-form">'+
    '<label>Euro per acquisto<input type="number" min="100" max="1000000" value="'+s.capital+'" onchange="statsSetParam(\'capital\',this.value)"></label>'+
    '<label>Commissione per lato<input type="number" min="0" step="0.01" value="'+s.fee+'" onchange="statsSetParam(\'fee\',this.value)"></label>'+
    '<label>Slippage (bps per lato)<input type="number" min="0" value="'+s.slippageBps+'" onchange="statsSetParam(\'slippageBps\',this.value)"></label>'+
    '<label>Durata massima<select onchange="statsSetParam(\'horizon\',this.value)">'+[5,10,20].map(n=>'<option '+(n===s.horizon?'selected':'')+'>'+n+'</option>').join('')+'</select></label></div><p>Ingresso all’apertura successiva; uscita intera a target 1, stop o scadenza. Stop prima del target se toccati nella stessa barra. Le soglie delle strategie non sono state ottimizzate per migliorare questi risultati.</p><button class="action" onclick="statsRefresh()">Ricalcola</button></details>';
  out+='<details><summary>Esiti e limiti del backtest</summary><p>Target 1: '+a.t1Count+' · Stop: '+a.stopCount+' · Scadenza: '+a.timeCount+'. Rilevamenti ripetuti esclusi: '+r.repeated+'.</p><p>Solo titoli oggi coperti e segnali salvati; replay recente di 35 sedute, non storico pluriennale. Possibili distorsioni da selezione dei titoli e prezzi mancanti. Momentum analizza prezzi e volumi, non eventi storici verificati. Le regole non replicano una strategia proprietaria Websim.</p></details>';
  return out;
}
function statsArchiveRender(){
  const r=statsCurrentReport();
  const names={all:'Tutte',FORZA:'Forza',DEBOLEZZA:'Debolezza',RECUPERO:'Recupero',EVENT_MOMENTUM:'Momentum'};
  const rows=r.rows.filter(x=>(statsStrategy==='all'||x.kind===statsStrategy)&&(statsMode==='all'||x.state===statsMode));
  let out='<div class="topline"><h2>Archivio simulazioni</h2><small>'+rows.length+' operazioni teoriche</small></div>'+statsNav();
  out+='<div class="section"><p>Ogni riga è un’operazione simulata. I tuoi acquisti reali sono esclusivamente nel Diario.</p></div>';
  out+='<div class="chips">'+Object.entries(names).map(([k,v])=>'<button class="chip '+(statsStrategy===k?'sel':'')+'" onclick="statsSelectStrategy(\''+k+'\')">'+v+'</button>').join('')+'</div>';
  out+='<div class="chips">'+[['all','Tutte'],['resolved','Concluse'],['pending','Aperte'],['excluded','Non simulabili']].map(([k,v])=>'<button class="chip '+(statsMode===k?'sel':'')+'" onclick="statsSetMode(\''+k+'\')">'+v+'</button>').join('')+'</div><div class="section">';
  for(const x of rows.slice(0,300))out+='<div class="archive-row"><strong>'+esc(x.ticker)+' · '+esc(names[x.kind]||x.kind)+'</strong><small>Segnale '+esc(x.asof)+' · Ingresso '+esc(x.entryDay||'—')+' · Uscita '+esc(x.exitDay||'—')+'</small><p>'+(x.state==='resolved'?esc(x.exitReason)+' · '+statsFmt(x.pnl,2)+' € · '+statsFmt(x.netR,2)+'R':esc(x.reason))+'</p></div>';
  if(!rows.length)out+='<p>Nessuna operazione per questi filtri.</p>';
  if(rows.length>300)out+='<p>Mostrate le 300 più recenti; il confronto include tutte le righe.</p>';
  return out+'</div>';
}
