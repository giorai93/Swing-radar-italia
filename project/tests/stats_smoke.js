// Run: node tests/stats_smoke.js
const assert=require('assert'),fs=require('fs'),vm=require('vm');
const js=fs.readFileSync('assets/stats.js','utf8');new Function(js);
const context={window:{},Number,Math,String,Map,Set,Object,Array,JSON,Date,
  alert(m){throw Error(m)},console};vm.createContext(context);vm.runInContext(js,context);
const bars=[];
for(let i=0;i<41;i++){
  const d=new Date(Date.UTC(2026,0,1+i)).toISOString().slice(0,10);
  bars.push([d,10,11,9.8,10.5,1000]);
}
const sig=(date='2026-01-01',kind='FORZA')=>({ticker:'ENI',kind,asof:date,segment:'LARGE',stop:9,target1:11.5,target2:12.5});
const opts={capital:1000,fee:2.95,slippageBps:5,horizon:20};

let normal=vm.runInContext('statsSimulateOne',context)(sig(),vm.runInContext('statsBars',context)(bars),opts);
assert.strictEqual(normal.state,'resolved');
assert.strictEqual(normal.exitReason,'TEMPO');
assert(normal.forward[5]>0&&normal.forward[10]>0&&normal.forward[20]>0);

const stopBars=bars.map(x=>[...x]);stopBars[1]=[bars[1][0],10,12,8.9,10.5,1000];
const stopped=vm.runInContext('statsSimulateOne',context)(sig(),vm.runInContext('statsBars',context)(stopBars),opts);
assert.strictEqual(stopped.exitReason,'STOP','stop first on ambiguous daily candle');
assert(stopped.netR<0);
const noFee=vm.runInContext('statsSimulateOne',context)(sig(),vm.runInContext('statsBars',context)(stopBars),{...opts,fee:0,slippageBps:0});
assert(stopped.netR<noFee.netR,'fees and slippage reduce net R');

const tgtBars=bars.map(x=>[...x]);tgtBars[1]=[bars[1][0],10,11.6,9.7,11,1000];
tgtBars[2]=[bars[2][0],12.6,12.8,11.8,12.5,1000];
const tgt=vm.runInContext('statsSimulateOne',context)(sig(),vm.runInContext('statsBars',context)(tgtBars),opts);
assert.strictEqual(tgt.exitReason,'TARGET 1');
assert.strictEqual(tgt.t2,true,'target 2 tracked only as an alternative hold path');

const gapBars=bars.map(x=>[...x]);gapBars[1]=[bars[1][0],8.5,10,8.4,9,1000];
assert.strictEqual(vm.runInContext('statsSimulateOne',context)(sig(),vm.runInContext('statsBars',context)(gapBars),opts).state,'excluded');
const pending=vm.runInContext('statsSimulateOne',context)(sig(bars[39][0]),vm.runInContext('statsBars',context)(bars),opts);
assert.strictEqual(pending.state,'pending');

const report=vm.runInContext('statsCompute',context)([sig(),sig(),sig('2026-01-03','DEBOLEZZA')],ticker=>tgtBars,opts);
assert.strictEqual(report.all.signals,2,'duplicate signal unique by ticker-kind-date');
assert.strictEqual(report.force.signals,1);
assert.strictEqual(report.weak.signals,1);
assert.strictEqual(report.all.closed,2);
assert(report.all.pf===null||report.all.pf>0);
const repeatedReport=vm.runInContext('statsCompute',context)(
  [sig('2026-01-01'),sig('2026-01-02'),sig('2026-01-03')],ticker=>bars,opts);
assert.strictEqual(repeatedReport.all.signals,1,'no overlapping synthetic trades on same ticker/direction');
assert.strictEqual(repeatedReport.repeated,2,'repeat detections reported separately');
const afterExit=vm.runInContext('statsCompute',context)(
  [sig('2026-01-01'),sig('2026-01-22')],ticker=>bars,opts);
assert.strictEqual(afterExit.all.signals,2,'new setup after previous time exit can be simulated');

const source=fs.readFileSync('assets/index.html','utf8');
assert(source.includes('onclick="nav(\'stats\')"'),'stats screen reachable from app');
assert(source.includes('<script src="stats.js"></script>'),'stats bundled in WebView');
const nodes=new Map();const byId=id=>{
  if(!nodes.has(id))nodes.set(id,{innerHTML:'',textContent:'',disabled:false});return nodes.get(id);
};
const liveSnapshot={signals:[sig()],trades:[],cachedStocks:1,status:{running:false,lastRun:'2026-02-10',
  report:{lastSession:'2026-02-10'},candidates:[],eventMomentum:[],recoveryZones:[]}};
const ui={window:{Android:{getSnapshot:()=>JSON.stringify(liveSnapshot),
  getChartData:()=>JSON.stringify({bars}),getStatsSettings:()=>JSON.stringify(opts),saveStatsSettings:()=>true}},
  Android:null,document:{getElementById:byId,querySelectorAll:()=>[]},
  Date,Number,Math,String,Map,Set,Object,Array,JSON,Intl,
  alert(m){throw Error(m)},setInterval(){},setTimeout(){},console};
ui.Android=ui.window.Android;
vm.createContext(ui);
vm.runInContext(source.split('<script>')[1].split('</script>')[0],ui);
vm.runInContext(js,ui);
vm.runInContext("nav('stats')",ui);
assert(nodes.get('view').innerHTML.includes('Risultati delle simulazioni'),'stats view renders');
assert(nodes.get('view').innerHTML.includes('value="2.95"'),'stored fee displayed');
vm.runInContext("statsSetParam('fee',0)",ui);
assert(nodes.get('view').innerHTML.includes('Win rate'),'stats view recalculates after parameter change');
console.log('OK: next-open fill, costs, stop-first, target1 vs T2 alternative, expiry, pending, no-fill, dedupe, segment/kind split, UI link');

{
// Four independent strategies share the same execution engine and costs.
const four=['FORZA','DEBOLEZZA','RECUPERO','EVENT_MOMENTUM'].map(kind=>sig(bars[0][0],kind));
const compute=vm.runInContext('statsCompute',context);
const report=compute(four,()=>tgtBars,opts);
assert.equal(report.all.closed,4);
for(const key of ['force','weak','recovery','momentum']){
  assert.equal(report[key].closed,1);
  assert.equal(report[key].avgPnl,report.force.avgPnl,'identical levels must have identical fills/costs');
}
const repeatedFour=compute([...four,...four.map(s=>({...s,asof:bars[0][0]}))],()=>tgtBars,opts);
assert.equal(repeatedFour.all.signals,4,'duplicates must not inflate any strategy');
const unfinished=compute(four,()=>bars.slice(0,1),opts);
assert.equal(unfinished.all.closed,0);
assert.equal(unfinished.all.pending,4);
const noLevels=compute([{ticker:'RWAY',kind:'RECUPERO',asof:bars[0][0]}],()=>bars,opts);
assert.equal(noLevels.recovery.excluded,1,'watch without frozen trade levels is not a backtest trade');
const prefix=compute(four,()=>bars.slice(0,8),opts);
assert.equal(prefix.all.closed,0,'later bars cannot resolve a truncated experiment');
console.log('PASS: all four strategies, equal execution costs, duplicate exclusion, pending and missing-level cases');

}

vm.runInContext("nav('history')",ui);
assert(nodes.get('view').innerHTML.includes('Archivio simulazioni'));
assert(nodes.get('view').innerHTML.includes('Momentum'));
assert(nodes.get('view').innerHTML.includes('Diario'));
