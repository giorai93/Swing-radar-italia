/* Swing Radar Italia · offline chart renderer. No network requests, no external libraries. */
'use strict';
let chartState=null;

function chartSma(bars,period){
  let sum=0;const out=[];
  for(let i=0;i<bars.length;i++){
    sum+=bars[i].c;
    if(i>=period)sum-=bars[i-period].c;
    out.push(i>=period-1?sum/period:null);
  }
  return out;
}
function chartLevels(bars){
  const prior=bars.slice(0,-1),last20=prior.slice(-20),last120=prior.slice(-120);
  return {
    resistance:last20.length?Math.max(...last20.map(x=>x.h)):null,
    support:last120.length?Math.min(...last120.map(x=>x.l)):null
  };
}
function chartHtml(){
  const st=chartState;if(!st)return '';
  const chip=(id,label,selected,action)=>'<button class="chip '+(selected?'sel':'')+'" onclick="'+action+'(\''+id+'\')">'+label+'</button>';
  const layer=(name,label)=>'<label class="chart-toggle"><input type="checkbox" '+(st.layers[name]?'checked':'')+' onchange="toggleChartLayer(\''+name+'\')">'+label+'</label>';
  return '<div class="modal-backdrop" onclick="closeChart(event)"><div class="modal chart-modal" role="dialog" aria-modal="true" aria-label="Grafico '+esc(st.ticker)+'">'+
    '<div class="modal-header"><h2>📈 '+esc(st.ticker)+' · Grafico EOD</h2><button class="xbtn" onclick="closeChart()" aria-label="Chiudi grafico">×</button></div>'+
    '<p class="chart-note">'+esc(st.source)+' · Ultima seduta '+esc(st.bars[st.bars.length-1].day)+'. Prezzi e livelli indicativi, non ordini.</p>'+
    '<div class="chips chart-period">'+[
      chip('1m','1 mese',st.period==='1m','chartPeriod'),chip('3m','3 mesi',st.period==='3m','chartPeriod'),
      chip('6m','6 mesi',st.period==='6m','chartPeriod'),chip('1y','1 anno',st.period==='1y','chartPeriod'),
      chip('2y','2 anni',st.period==='2y','chartPeriod')].join('')+'</div>'+
    '<div class="chart-scroll"><button class="action" onclick="chartShift(20)">← Sedute precedenti</button><span>Scorri il grafico orizzontalmente</span><button class="action" onclick="chartShift(-20)">Successive →</button></div>'+
    '<div class="chips chart-period">'+chip('candle','Candele',st.type==='candle','chartType')+
      chip('bar','Barre OHLC',st.type==='bar','chartType')+'</div>'+
    '<div class="chart-layerrow">'+layer('sma20','SMA20')+layer('sma50','SMA50')+layer('sma200','SMA200')+
      layer('support','Supporti / resistenze')+layer('trade','Livelli setup')+'</div>'+
    '<div class="chart-shell"><canvas id="priceChart" height="420" aria-label="Grafico storico di '+esc(st.ticker)+'"></canvas></div>'+
    '<div id="chartInfo" class="chart-info">Tocca una candela per leggere i valori.</div>'+
    '<div class="chart-key"><span style="color:#18a585">● Rialzo</span><span style="color:#dc5064">● Ribasso</span><span style="color:#3389da">● SMA20</span><span style="color:#d19a20">● SMA50</span><span style="color:#9759bf">● SMA200</span></div>'+
    '<p class="chart-note">Resistenza: massimo delle 20 sedute precedenti. Supporto: minimo delle 120 sedute precedenti. Medie mobili calcolate sull’intero storico disponibile, senza usare dati futuri. Se entrata, stop o target sono fuori dall’intervallo visibile vengono indicati come fuori scala.</p>'+
    '</div></div>';
}
function openChart(ticker,asof='',kind=''){
  if(!window.Android||typeof Android.getChartData!=='function'){
    alert('Aggiorna alla versione con grafici e riprova.');return;
  }
  let source;
  try{source=JSON.parse(Android.getChartData(ticker));}
  catch(ex){alert('Impossibile leggere i dati del grafico');return;}
  if(source.error){alert(source.error);return;}
  const bars=(source.bars||[]).filter(a=>Array.isArray(a)&&a.length===6&&
    /^\d{4}-\d{2}-\d{2}$/.test(a[0])&&a.slice(1).every(x=>typeof x==='number'&&Number.isFinite(x))&&
    a[1]>0&&a[2]>=a[3]&&a[4]>0&&a[5]>=0)
    .map(a=>({day:a[0],o:a[1],h:a[2],l:a[3],c:a[4],v:a[5]}));
  if(bars.length<12){alert('Dati OHLC insufficienti per questo titolo. Esegui una scansione completa.');return;}
  const signal=(snapshot.signals||[]).find(x=>x.ticker===ticker&&(!asof||x.asof===asof)&&(!kind||x.kind===kind));
  const zone=!signal?(snapshot.status?.recoveryZones||[]).find(x=>x.ticker===ticker&&(!asof||x.asof===asof)):null;
  chartState={ticker,bars,source:source.source||'Cache locale',signal,zone,
    period:'3m',offset:0,type:'candle',hover:-1,layers:{sma20:true,sma50:true,sma200:true,support:true,trade:true},
    sma20:chartSma(bars,20),sma50:chartSma(bars,50),sma200:chartSma(bars,200)};
  document.getElementById('overlay').innerHTML=chartHtml();
  const canvas=document.getElementById('priceChart');
  bindChartPointer(canvas);
  drawChart();
}
function closeChart(event){
  if(!event||event.target.classList.contains('modal-backdrop')){
    chartState=null;document.getElementById('overlay').innerHTML='';
  }
}
function chartRefresh(){document.getElementById('overlay').innerHTML=chartHtml();bindChartPointer(document.getElementById('priceChart'));drawChart();}
function chartPeriod(period){if(!chartState)return;chartState.period=period;chartState.hover=-1;chartRefresh();}
function chartType(type){if(!chartState)return;chartState.type=type;chartRefresh();}
function chartShift(sessions){
  if(!chartState)return;
  chartState.offset=Math.max(0,Math.min(chartState.bars.length-12,chartState.offset+sessions));
  chartState.hover=-1;drawChart();
}
function toggleChartLayer(layer){if(!chartState||!(layer in chartState.layers))return;chartState.layers[layer]=!chartState.layers[layer];drawChart();}

function bindChartPointer(canvas){
  if(!canvas||typeof canvas.addEventListener!=='function')return;
  let start=null;
  canvas.addEventListener('pointerdown',e=>{start={x:e.clientX,offset:chartState?.offset||0};});
  canvas.addEventListener('pointerup',e=>{
    if(!start)return;
    const shift=e.clientX-start.x;
    if(Math.abs(shift)>25){
      const layout=chartLayout(),width=Math.max(100,canvas.getBoundingClientRect().width-80);
      chartState.offset=Math.max(0,Math.min(chartState.bars.length-12,
        start.offset+Math.round(shift/(width/Math.max(1,layout.visible.length)))));
      chartState.hover=-1;drawChart();
    }else chartPointer(e);
    start=null;
  });
  canvas.addEventListener('pointercancel',()=>{start=null;});
}

function chartLayout(){
  if(!chartState)return null;
  const st=chartState,limits={'1m':22,'3m':65,'6m':130,'1y':250,'2y':510};
  const end=Math.max(12,st.bars.length-(st.offset||0));
  const first=Math.max(0,end-(limits[st.period]||65));
  return {first,end,visible:st.bars.slice(first,end)};
}
function chartPointer(event){
  if(!chartState)return;
  const canvas=document.getElementById('priceChart');if(!canvas)return;
  const rect=canvas.getBoundingClientRect(),{visible}=chartLayout();
  const width=rect.width||320,left=18,right=60;
  const x=event.clientX-rect.left;
  chartState.hover=Math.max(0,Math.min(visible.length-1,Math.floor((x-left)/(width-left-right)*visible.length)));
  drawChart();
}
function drawChart(){
  const st=chartState,canvas=document.getElementById('priceChart');
  if(!st||!canvas||typeof canvas.getContext!=='function')return;
  const ctx=canvas.getContext('2d');if(!ctx)return;
  const rect=canvas.getBoundingClientRect();
  const w=Math.max(230,rect.width||330),h=420,dpr=Math.min(3,window.devicePixelRatio||1);
  canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);
  if(ctx.setTransform)ctx.setTransform(dpr,0,0,dpr,0,0);
  ctx.clearRect(0,0,w,h);ctx.fillStyle='#ffffff';ctx.fillRect(0,0,w,h);
  const layout=chartLayout(),bars=layout.visible,first=layout.first,n=bars.length;
  if(!n)return;
  const left=19,right=w-62,top=20,priceBottom=302,volTop=327,volBottom=389;
  const lv=chartLevels(st.bars.slice(0,layout.end));let min=Math.min(...bars.map(b=>b.l)),max=Math.max(...bars.map(b=>b.h));
  const levels=[];
  if(st.layers.support){if(lv.support>0)levels.push({v:lv.support,label:'S120',color:'#16a178'});
    if(lv.resistance>0)levels.push({v:lv.resistance,label:'R20',color:'#bc771f'});}
  if(st.layers.trade){
    const s=st.signal||st.zone;
    if(s){
      if(Number(s.entry)>0)levels.push({v:Number(s.entry),label:'Entrata',color:'#008f70',dash:[3,2]});
      if(Number(s.stop)>0)levels.push({v:Number(s.stop),label:'Stop',color:'#d43e59',dash:[3,2]});
      if(Number(s.target1)>0)levels.push({v:Number(s.target1),label:'T1',color:'#1784ab',dash:[4,3]});
      if(Number(s.target2)>0)levels.push({v:Number(s.target2),label:'T2',color:'#4e71bd',dash:[4,3]});
      if(Number(s.zoneLow)>0)levels.push({v:Number(s.zoneLow),label:'Zona ↓',color:'#8d6bd2',dash:[4,3]});
      if(Number(s.zoneHigh)>0)levels.push({v:Number(s.zoneHigh),label:'Zona ↑',color:'#8d6bd2',dash:[4,3]});
    }
  }
  // A distant historical target must not flatten current candles into a tiny line.
  const margin=(max-min)*.06||Math.max(.05,max*.015);min-=margin;max+=margin;
  for(const series of [st.layers.sma20?st.sma20:null,st.layers.sma50?st.sma50:null,st.layers.sma200?st.sma200:null]){
    if(!series)continue;
    for(let i=first;i<layout.end;i++){const v=series[i];if(v!==null&&v>=min-.2*(max-min)&&v<=max+.2*(max-min)){min=Math.min(min,v);max=Math.max(max,v);}}
  }
  const y=v=>top+(max-v)/(max-min)*(priceBottom-top),x=i=>left+(i+.5)*(right-left)/n;
  ctx.font='10px sans-serif';ctx.textAlign='left';
  for(let i=0;i<5;i++){
    const val=min+(max-min)*i/4,yy=y(val);
    ctx.beginPath();ctx.strokeStyle='#e9eef5';ctx.lineWidth=1;ctx.moveTo(left,yy);ctx.lineTo(right,yy);ctx.stroke();
    ctx.fillStyle='#6a8097';ctx.fillText(val.toFixed(val<10?3:2),right+5,yy+3);
  }
  const maxVolume=Math.max(1,...bars.map(b=>b.v));
  ctx.fillStyle='#ecf2f8';ctx.fillRect(left,volTop,right-left,volBottom-volTop);
  for(let i=0;i<n;i++){
    const b=bars[i],xx=x(i),step=(right-left)/n,bw=Math.max(1,Math.min(10,step*.64)),up=b.c>=b.o;
    const color=up?'#16a085':'#d64b60';ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineWidth=Math.max(1,Math.min(1.5,step*.18));
    ctx.beginPath();ctx.moveTo(xx,y(b.h));ctx.lineTo(xx,y(b.l));ctx.stroke();
    if(st.type==='candle'){
      const yy=Math.min(y(b.o),y(b.c)),body=Math.max(1.2,Math.abs(y(b.c)-y(b.o)));
      ctx.fillRect(xx-bw/2,yy,bw,body);
    }else{
      ctx.beginPath();ctx.moveTo(xx-bw/2,y(b.o));ctx.lineTo(xx,y(b.o));ctx.moveTo(xx,y(b.c));ctx.lineTo(xx+bw/2,y(b.c));ctx.stroke();
    }
    ctx.globalAlpha=.55;ctx.fillRect(xx-bw/2,volBottom-(b.v/maxVolume)*(volBottom-volTop),bw,(b.v/maxVolume)*(volBottom-volTop));ctx.globalAlpha=1;
  }
  function line(values,color){
    ctx.save();ctx.beginPath();ctx.rect(left,top,right-left,priceBottom-top);ctx.clip();
    ctx.beginPath();ctx.strokeStyle=color;ctx.lineWidth=1.5;let started=false;
    for(let i=0;i<n;i++){
      const val=values[first+i];if(!Number.isFinite(val)){started=false;continue;}
      const xx=x(i),yy=y(val);if(!started)ctx.moveTo(xx,yy);else ctx.lineTo(xx,yy);started=true;
    }ctx.stroke();ctx.restore();
  }
  if(st.layers.sma20)line(st.sma20,'#328bd1');
  if(st.layers.sma50)line(st.sma50,'#d59b22');
  if(st.layers.sma200)line(st.sma200,'#8b60bb');
  const outside=[];
  for(const l of levels){
    if(l.v<min||l.v>max){outside.push(l.label+' '+l.v.toFixed(2)+' €');continue;}
    const yy=y(l.v);
    ctx.beginPath();ctx.strokeStyle=l.color;ctx.lineWidth=1.2;ctx.setLineDash(l.dash||[6,3]);ctx.moveTo(left,yy);ctx.lineTo(right,yy);ctx.stroke();ctx.setLineDash([]);
    ctx.fillStyle=l.color;ctx.font='bold 9px sans-serif';ctx.fillText(l.label,right+5,yy-3);
  }
  if(st.signal&&st.signal.asof){
    const at=bars.findIndex(b=>b.day===st.signal.asof);
    if(at>=0){const xx=x(at);ctx.setLineDash([2,5]);ctx.strokeStyle='#1b6178';ctx.beginPath();ctx.moveTo(xx,top);ctx.lineTo(xx,priceBottom);ctx.stroke();ctx.setLineDash([]);ctx.fillStyle='#1b6178';ctx.font='bold 10px sans-serif';ctx.fillText('Segnale',Math.max(left,xx-14),top+11);}
  }
  ctx.fillStyle='#6f8296';ctx.font='10px sans-serif';
  const tickIdx=[0,Math.floor((n-1)/3),Math.floor(2*(n-1)/3),n-1];
  for(const i of [...new Set(tickIdx)])ctx.fillText(bars[i].day.slice(5).replace('-','/'),Math.min(right-27,Math.max(left,x(i)-13)),412);
  const info=document.getElementById('chartInfo');
  if(info){
    if(st.hover>=0&&st.hover<n){
      const b=bars[st.hover],xx=x(st.hover);
      ctx.setLineDash([2,2]);ctx.strokeStyle='#4d6d8b';ctx.beginPath();ctx.moveTo(xx,top);ctx.lineTo(xx,volBottom);ctx.stroke();ctx.setLineDash([]);
      info.textContent=b.day+' · O '+b.o.toFixed(3)+' € · H '+b.h.toFixed(3)+' € · L '+b.l.toFixed(3)+' € · C '+b.c.toFixed(3)+' € · Vol. '+Math.round(b.v).toLocaleString('it-IT');
    }else info.textContent='Tocca una candela per leggere apertura, massimo, minimo, chiusura e volume.';
    if(outside.length)info.textContent+=' | Fuori scala: '+outside.join(' · ');
  }
}
if(window&&typeof window.addEventListener==='function')window.addEventListener('resize',()=>{if(chartState)drawChart();});
