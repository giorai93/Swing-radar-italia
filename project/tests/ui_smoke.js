// Run: node tests/ui_smoke.js
const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const html = fs.readFileSync('assets/index.html', 'utf8');
const js = html.split('<script>')[1].split('</script>')[0];
new Function(js);
const elements = new Map();
const byId = id => {
  if (!elements.has(id)) elements.set(id, { innerHTML: '', textContent: '', disabled: false });
  return elements.get(id);
};
const state = {
  status: {
    report: { usable: 81, withoutHistory: 3, incomplete: false, lastSession: '2026-10-28',
      diagnostics: [{ticker:'MAIRE',status:'MOMENTUM_DA_VERIFICARE',asof:'2026-10-28',price:12.95,sma50:12.404,sma200:12.471,turnover:9e6,turnoverRequired:5e5,hasEventMomentum:true,reason:'SMA50 non sopra SMA200'}] },
    lastInfo: '81 titoli analizzati', lastRun: '2026-10-29',
    candidates: [
      { ticker: 'ENI', name: 'Eni', kind: 'FORZA', asof: '2026-10-29', matched: 4, required: 5, missing: 'Volume sotto 1,3×', rsi2: 55, rvol: 1.1, price: 24, resistance: 24.8, pullbackPct: 3 },
      { ticker: 'CPR', name: 'Campari', kind: 'DEBOLEZZA', asof: '2026-10-29', matched: 3, required: 4, missing: 'Prezzo distante da SMA20', rsi2: 10, rvol: 1.1, price: 6, resistance: 6.8, pullbackPct: 12 }
    ],
    recoveryZones: [
      { ticker:'STLAM', name:'Stellantis', segment:'LARGE',kind:'RECUPERO',asof:'2026-10-27',
        price:3.71,support:3.501,zoneLow:3.55,zoneHigh:3.92,stop:3.45,target1:4.1,target2:4.4,
        riskPct:7,return60Pct:-25.7,distSupportPct:6,rsi2:57,matched:4,required:4,missing:'Zona tecnica idonea',
        phase:'OSSERVAZIONE',phaseAt:'2026-10-27',lastChecked:'2026-10-30',lastPrice:3.72,relevantAt:'2026-10-27' },
      { ticker:'RWAY', name:'Rai Way', segment:'SMALL',kind:'RECUPERO',asof:'2026-10-30',
        price:4.17,support:4.1,zoneLow:4.161,zoneHigh:4.592,stop:4.026,target1:4.367,target2:4.499,
        riskPct:3.5,return60Pct:-12.1,distSupportPct:1.7,rsi2:5.1,matched:4,required:4,
        phase:'RIMBALZO',phaseAt:'2026-10-30',lastChecked:'2026-10-30',lastPrice:4.17,relevantAt:'2026-10-30',
        phaseReason:'Minimo sotto il supporto, poi chiusura sopra' }
    ],
    eventMomentum: [{ticker:'MAIRE',name:'Maire',segment:'MID',kind:'EVENT_MOMENTUM',asof:'2026-10-27',price:13.02,dayPct:3.91,twoDayPct:9.5,rvol:3.1,position:.92,sma50Above200:false}],
    earnings: { values: { STLAM:{lastDate:'2026-10-28'}, ENI:{nextDate:'2026-11-02'}, MAIRE:{lastDate:'2026-10-27'} },
      upcoming:[{ticker:'ENI',nextDate:'2026-11-02',daysAway:4}],
      recent:[{ticker:'STLAM',lastDate:'2026-10-28',daysAgo:1}],updatedAt:'2026-10-29T12:00:00Z' },
    fundamentals: { count: 2, updatedAt: '2026-10-29T12:00:00Z', lastError: '', values: {
      ENI: { sector: 'Energy Minerals', price_earnings_ttm: 10.5, return_on_equity_fq: 7.4, total_revenue_yoy_growth_ttm: 3.9, market_cap_basic: 70e9 },
      CPR: { sector: 'Consumer Non-Durables', price_earnings_ttm: 27.5, return_on_equity_fq: 7, total_revenue_yoy_growth_ttm: -1.2, market_cap_basic: 8e9 }
    } }
  }, signals: [
    { ticker: 'ENI', name: 'Eni', kind: 'FORZA', asof: '2026-10-28', entry: 24, price: 24, stop: 22, target1: 28, target2: 30, rvol: 1.6, rsi2: 72, score: 81, riskPct: 7.1, dataAge: 1, lifecycle:'ATTIVO',stateAt:'2026-10-28' },
    { ticker: 'CPR', name: 'Campari', kind: 'DEBOLEZZA', asof: '2026-10-12', entry: 6, price: 6, stop: 5.5, target1: 7, target2: 8, rvol: 1.1, rsi2: 12, score: 75, riskPct: 8.1, dataAge: 17, lifecycle:'STOP_TOUCHED',stateAt:'2026-10-15' }
  ], trades: [], cachedStocks: 81
};
state.signals.push(
  { ticker:'SYS', name:'Sys-dat', kind:'DEBOLEZZA', asof:'2026-10-28', entry:8.42, stop:8.07,target1:9,target2:9.5,rvol:.53,rsi2:24,score:63,riskPct:4.16,lifecycle:'ATTIVO',stateAt:'2026-10-29' },
  { ticker:'SYS', name:'Sys-dat', kind:'DEBOLEZZA', asof:'2026-10-29', entry:8.5, stop:8.05,target1:9.1,target2:9.6,rvol:.7,rsi2:15.6,score:70,riskPct:5.3,lifecycle:'ATTIVO',stateAt:'2026-10-29' }
);
const sandbox = {
  window: { Android: { getSnapshot: () => JSON.stringify(state), startScan: () => 'Scansione avviata' } },
  document: { getElementById: byId, querySelectorAll: () => [] },
  Date, Number, String, JSON, Object, Array, Set, Math, Intl,
  setInterval: () => {}, setTimeout: () => {}, alert: () => {}
};
sandbox.Android = sandbox.window.Android;
vm.createContext(sandbox);
vm.runInContext(js, sandbox);

assert(byId('watchlist').innerHTML.includes('ENI'), 'radar includes strength watch');
assert(byId('watchlist').innerHTML.includes('CPR'), 'radar includes weakness watch');
assert(byId('view').innerHTML.includes('CPR'), 'historical setup visible in 45-day mode');
assert(byId('view').innerHTML.includes('MAIRE'), 'event momentum watch visible in radar separately');
assert.strictEqual((byId('view').innerHTML.match(/class="ticker">SYS/g)||[]).length,1,
  'consecutive SYS weakness observations are one visible idea');
assert(byId('view').innerHTML.includes('2 rilevamenti · dal 2026-10-28'),
  'merged card shows repeated observations transparently');
assert.strictEqual(vm.runInContext('consolidateSignals', sandbox)(state.signals)
  .filter(x=>x.ticker==='SYS')[0].asof,'2026-10-29','latest levels shown');
assert.strictEqual(vm.runInContext('consolidateSignals', sandbox)([
  {ticker:'AAA',kind:'FORZA',asof:'2026-10-20',lifecycle:'STOP_TOUCHED',stateAt:'2026-10-21'},
  {ticker:'AAA',kind:'FORZA',asof:'2026-10-22',lifecycle:'ATTIVO',stateAt:'2026-10-22'}
]).length,2,'a genuinely new idea after prior stop must stay separate');
vm.runInContext("setLifecycleMode('invalid')", sandbox);
assert(byId('view').innerHTML.includes('CPR') && !byId('view').innerHTML.includes('ENI'), 'expired/stopped setup filter works');
vm.runInContext("setLifecycleMode('all')", sandbox);
vm.runInContext("setPeriod('session')", sandbox);
assert(!byId('view').innerHTML.includes('CPR'), 'historical setup hidden in last-session mode');
assert(byId('view').innerHTML.includes('ENI'), 'last-session setup remains');
vm.runInContext("setPeriod('45d')", sandbox);
vm.runInContext("setFilter('FORZA')", sandbox);
assert(byId('watchlist').innerHTML.includes('ENI') && !byId('watchlist').innerHTML.includes('CPR'), 'radar chips filter watchers');
vm.runInContext("setFilter('all')", sandbox);
vm.runInContext("nav('force')", sandbox);
assert(byId('watchlist').innerHTML.includes('ENI'), 'strength shows ENI');
assert(!byId('watchlist').innerHTML.includes('CPR'), 'strength hides CPR');
vm.runInContext("nav('weak')", sandbox);
assert(!byId('watchlist').innerHTML.includes('ENI'), 'weakness hides ENI');
assert(byId('watchlist').innerHTML.includes('CPR'), 'weakness shows CPR');
vm.runInContext("nav('zones')",sandbox);
assert(byId('view').innerHTML.includes('STLAM'), 'recovery watch for pre-earnings Stellantis is separate');
assert(byId('view').innerHTML.includes('RWAY'), 'Rai Way support rebound appears in recovery watch');
assert(byId('view').innerHTML.includes('Rimbalzo in formazione'), 'Rai Way explicitly marked as an unconfirmed rebound');
assert(byId('view').innerHTML.includes("openChart('RWAY','2026-10-30')"),
  'recovery watch offers OHLC chart with technical levels');
assert(byId('view').innerHTML.includes('Fascia di osservazione'), 'broad range must not be described as a buy range');
assert(!byId('view').innerHTML.includes('Resistenza/target 1'), 'computed target is not misrepresented as actual resistance');
vm.runInContext("setZonePhase('rebound')",sandbox);
assert(byId('view').innerHTML.includes('RWAY')&&!byId('view').innerHTML.includes('STLAM'),
  'phase filter isolates rebounds');
vm.runInContext("setZonePhase('all')",sandbox);
assert(byId('view').innerHTML.includes('Risultati successivi'), 'subsequent results marked as retrospective');
assert(byId('events').innerHTML.includes('ENI'), 'upcoming earnings visible');
assert(!byId('view').innerHTML.includes('Setup confermati'), 'watch zone must not look like confirmed trade');
vm.runInContext("nav('fund')", sandbox);
assert(byId('view').innerHTML.includes('P/E 10,5×'), 'fundamentals shown on ENI');
assert(byId('view').innerHTML.includes('CPR'), 'fundamental ticker displayed');
vm.runInContext("setFundMode('watch')", sandbox);
assert(byId('view').innerHTML.includes('ENI') && byId('view').innerHTML.includes('CPR'));
vm.runInContext("openFund('ENI')", sandbox);
assert(byId('overlay').innerHTML.includes('ROE'), 'financial modal shows ROE');
assert(!byId('view').innerHTML.includes('NaN'), 'UI without NaN');
vm.runInContext("nav('data')", sandbox);
assert(byId('watchlist').innerHTML.includes('MAIRE'), 'ticker diagnosis contains MAIRE');
assert(byId('watchlist').innerHTML.includes('SMA50 non sopra SMA200'), 'ticker diagnostics explain missing strict setup');
vm.runInContext("nav('journal')", sandbox);
assert(byId('view').innerHTML.includes('Posizioni aperte'), 'journal has distinct open/closed states');
// A completed scan may still carry stale prices: never present it as current.
state.status.sessionStale=true;
state.status.expectedSession='2026-10-29';
state.status.report.staleQuoteCount=1;
state.status.report.staleQuotes=[{ticker:'ENI',asof:'2026-10-28'}];
vm.runInContext('update()',sandbox);
assert(byId('status').innerHTML.includes('Dati giornalieri arretrati'),'stale EOD explicitly warned');
assert(byId('status').innerHTML.includes('2026-10-29'),'expected session visible');
vm.runInContext("nav('data')", sandbox);
assert(byId('view').innerHTML.includes('Prezzi arretrati'),'stale ticker tally shown');
assert(byId('view').innerHTML.includes('2026-10-28 · atteso 2026-10-29'),'ticker-specific stale date shown');
console.log('OK: category filters, MAIRE event watch, technical lifecycle, diagnosis, journal, fundamentals');
