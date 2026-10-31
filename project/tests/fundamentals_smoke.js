const fs=require('fs'),vm=require('vm'),assert=require('assert');
const js=fs.readFileSync('assets/index.html','utf8').split('<script>')[1].split('</script>')[0];
const ctx={window:{},document:{getElementById:()=>({innerHTML:'',classList:{toggle(){}}}),querySelectorAll:()=>[]},setInterval(){},setTimeout(){},console};
vm.createContext(ctx);vm.runInContext(js,ctx);
function run(s){return vm.runInContext(s,ctx)}
run("snapshot={status:{fundamentals:{values:{TEST:{revenue_growth_pct:0.5}}}}}");
assert.equal(run("growthAssessment('TEST').rate"),0.5);
assert.equal(run("growthAssessment('TEST').score"),51);
run("snapshot.status.fundamentals.values.TEST.revenue_growth_pct=null");
assert.equal(run("growthAssessment('TEST').score"),null);
assert.equal(run("valueAssessment('TEST').score"),null);
assert.equal(run("tradeAssessment({entry:10,stop:null,target1:15}).score"),null);
assert.equal(run("opportunityAssessment({ticker:'TEST'}).score"),null);
run("snapshot.status.fundamentals.values.TEST={roe_pct:15,net_margin_pct:7,equity_ratio_pct:30,cash_conversion:1}");
assert.equal(run("qualityAssessment('TEST').score"),86);
assert.ok(run("fundPanel('TEST')").includes('XBRL'));
console.log('Fundamentals regression tests passed');
