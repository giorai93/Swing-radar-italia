// Run: node tests/chart_smoke.js
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const html=fs.readFileSync('assets/index.html','utf8');
const indexJs=html.split('<script>')[1].split('</script>')[0];
const chartJs=fs.readFileSync('assets/chart.js','utf8');
new Function(indexJs);new Function(chartJs);
const data=[];
for(let i=0;i<290;i++){
  const date=new Date(Date.UTC(2025,11,1+i)).toISOString().slice(0,10);
  const c=9.5+i*.01;
  data.push([date,c-.1,c+.3,c-.35,c,10000+i*20]);
}
const last=data.at(-1),date=last[0],price=last[4];
const signal={ticker:'MAIRE',kind:'DEBOLEZZA',asof:date,entry:price,stop:price-.3,
  target1:price+.15,target2:price+.25,resistance:price+.24};
const snapshot={status:{report:{lastSession:date},candidates:[],eventMomentum:[],recoveryZones:[]},signals:[signal],trades:[]};
const labels=[];
const ctx={
  setTransform(){},clearRect(){},fillRect(){},beginPath(){},moveTo(){},lineTo(){},stroke(){},
  fillText(t){labels.push(t);},setLineDash(){},save(){},restore(){},rect(){},clip(){},fillStyle:'',strokeStyle:'',font:'',lineWidth:1
};
const canvas={width:0,height:0,getContext:()=>ctx,getBoundingClientRect:()=>({width:365,left:0}),addEventListener(){}};
const nodes=new Map();
const byId=id=>{
  if(id==='priceChart')return canvas;
  if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',disabled:false});
  return nodes.get(id);
};
const sandbox={window:{devicePixelRatio:2,Android:{getSnapshot:()=>JSON.stringify(snapshot),
  getChartData:ticker=>JSON.stringify({ticker,bars:data,source:'cache locale'})},addEventListener(){}},
  document:{getElementById:byId,querySelectorAll:()=>[]},Date,Number,String,JSON,Object,Array,Set,Math,Intl,
  setInterval(){},setTimeout(){},alert:msg=>{throw Error(msg)}};
sandbox.Android=sandbox.window.Android;
vm.createContext(sandbox);
vm.runInContext(indexJs,sandbox);
vm.runInContext(chartJs,sandbox);
const chartButton={html:'',querySelector:()=>null,insertAdjacentHTML(where,html){this.html+=html;}};
const fakeCard={querySelector:s=>({'.ticker':{textContent:'MAIRE'},'.company span':{textContent:'Milano · '+date},
  '.badges':{querySelector:()=>null,insertAdjacentHTML(){}},'.signalactions':chartButton})[s]||null};
sandbox.document.querySelectorAll=selector=>selector==='article.signal'?[fakeCard]:[];
vm.runInContext('decorateSegments()',sandbox);
assert(chartButton.html.includes('openChart(\'MAIRE\'')&&chartButton.html.includes(date),
  'a chart action is inserted into setup cards');
vm.runInContext(`openChart('MAIRE','${date}','DEBOLEZZA')`,sandbox);
assert(nodes.get('overlay').innerHTML.includes('Grafico EOD'),'chart modal opens');
assert(labels.some(x=>x==='R20'),'20-session resistance drawn');
assert(labels.some(x=>x==='S120')||nodes.get('chartInfo').textContent.includes('S120'),
  '120-session support drawn or marked outside the visible price scale');
assert(labels.some(x=>x==='Entrata')&&labels.some(x=>x==='Stop'),'entry and stop drawn');
assert(labels.some(x=>x==='T1')&&labels.some(x=>x==='T2'),'targets drawn');
assert.strictEqual(vm.runInContext('chartLayout().visible.length',sandbox),65);
assert.strictEqual(vm.runInContext('chartSma(chartState.bars,20).length',sandbox),290);
vm.runInContext("chartPeriod('1m')",sandbox);
assert.strictEqual(vm.runInContext('chartLayout().visible.length',sandbox),22);
vm.runInContext("chartType('bar')",sandbox);
assert.strictEqual(vm.runInContext('chartState.type',sandbox),'bar');
vm.runInContext('chartShift(20)',sandbox);
assert.strictEqual(vm.runInContext('chartState.offset',sandbox),20);
vm.runInContext("toggleChartLayer('sma20')",sandbox);
assert.strictEqual(vm.runInContext('chartState.layers.sma20',sandbox),false);
vm.runInContext('closeChart()',sandbox);
assert.strictEqual(nodes.get('overlay').innerHTML,'');
console.log('OK: OHLC/candles, 1m/3m, pan, support/resistance, entry/stop/targets, toggles, offline cache');
