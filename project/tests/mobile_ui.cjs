// Real bundled financial data; empty on-device database fixture, no fake returns.
const {chromium}=require('playwright');
const {pathToFileURL}=require('url');
const path=require('path');
const fs=require('fs');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:393,height:852},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{
   const state={signals:[],researchSignals:[],backtestSignals:[],trades:[],status:{lastRun:'2026-10-30T18:00:00Z',report:{lastSession:'2026-10-30'},fundamentals:{values:{},lastError:'Offline test'}}};
   window.Android={getSnapshot:()=>JSON.stringify(state),getChartData:()=>JSON.stringify({bars:[]}),getStatsSettings:()=>JSON.stringify({capital:1000,fee:2.95,slippageBps:5,horizon:20}),refreshIfDue:()=>false};
 });
 await page.route('https://**',route=>route.abort());
 await page.goto(pathToFileURL(path.resolve('project/assets/index.html')).href);
 await page.locator('.nav [data-tab="fund"]').click();
 await page.getByText('1 verificati / 2 nel registro',{exact:true}).waitFor();
 await page.getByText('Ricavi in crescita',{exact:false}).waitFor();
 // Registry order is not a UI contract; select SOL explicitly.
 await page.evaluate(()=>{closeModal();openFund('SOL');});
 await page.getByText('ROE su patrimonio medio del gruppo',{exact:true}).waitFor();
 if(!(await page.locator('.modal').innerText()).includes('15,43'))throw Error('SOL actual ROE missing');
 await page.evaluate(()=>closeModal());
 fs.mkdirSync('project/ui-checks',{recursive:true});
 await page.screenshot({path:'project/ui-checks/bilanci.png',fullPage:true});
 for(const name of ['radar','strategies','stats','journal']){
   await page.locator('.nav [data-tab="'+name+'"]').click();
   const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1);
   if(overflow)throw Error('Horizontal overflow: '+name);
 }
 await page.locator('.nav [data-tab="stats"]').click();
 await page.getByText('Risultati non ancora disponibili',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Archivio simulazioni',exact:true}).click();
 await page.getByRole('heading',{name:'Archivio simulazioni',exact:true}).waitFor();
 if(errors.length)throw Error(errors.join('\n'));
 await browser.close();console.log('PASS mobile 393px: SOL offline, navigation, archive, no JS errors or horizontal overflow');
})().catch(e=>{console.error(e);process.exit(1)});
