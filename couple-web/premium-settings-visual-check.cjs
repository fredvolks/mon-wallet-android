/* CI screenshot regression: render the actual renderSettings() function from app.js.
   Synthetic accounts are fixture data and are never transmitted. */
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
(async()=>{
 const dir=__dirname;
 const source=fs.readFileSync(path.join(dir,'app.js'),'utf8');
 const start=source.indexOf('function settingsIcon(');
 const end=source.indexOf('\nasync function refreshAndShow()',start);
 const accStart=source.indexOf('function initSettingsAccordion(){',start);
 const accEnd=source.indexOf('\nfunction renderSettings(){',accStart);
 if(start<0||end<start||accStart<0||accEnd<0)throw Error('Production settings functions missing');
 const slice=source.slice(start,end);
 const accordion=source.slice(accStart,accEnd);
 global.window={LoveRollNativeAuth:{weeklyReminderEnabled:()=>true,appVersion:()=>'0.13.3'}};
 const [{notificationSettingsMarkup},{wellbeingFields,monthDayInputs},{OCCASIONS,TARGETED_OCCASIONS}]=await Promise.all([
  import(pathToFileURL(path.join(dir,'push-notifications.mjs')).href),
  import(pathToFileURL(path.join(dir,'onboarding.mjs')).href),
  import(pathToFileURL(path.join(dir,'special-occasions.mjs')).href)
 ]);
 const escape=s=>String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
 const state={
  user:{email:'exemple@loveroll.test'},profile:{display_name:'Partenaire A',birthday_md:'04-12'},
  members:[{user_id:'a',name:'Partenaire A'},{user_id:'b',name:'Partenaire B'}],
  coupleId:'demo-couple',occasionSettings:{relationship_started_on:'2020-10-14',anniversary_md:'10-14',celebrants:{}},
  weekly:{face:3,actor:'a',created_at:'2026-10-12T12:00:00Z'},
  pushPrefs:{enabled:false,weekly:true,invitation:true,monthly:true,special:true},
  wellbeing:{relationship_state:'',intimacy_state:''}
 };
 let content='';
 const ctx={window:global.window,sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
  settingsOpenSection:'',requestedSettingsSection:'',WEB_RELEASE:'2026-10-14.55',
  state,root:{querySelectorAll:()=>[]},console,Date,
  safe:escape,button:(action,title,attrs='')=>'<button data-action="'+escape(action)+'" '+attrs+'>'+escape(title)+'</button>',
  isLoveAdmin:()=>true,other:()=>({name:'Partenaire B'}),person:user=>user==='a'?'Partenaire A':'Partenaire B',
  today:()=>'2026-10-14',nextMonday:()=>'19 octobre',
  formatCoupleDate:date=>date,
  TARGETED_OCCASIONS,OCCASIONS,
  notificationSettingsMarkup,wellbeingFields,monthDayInputs,
  frame:(html,title,sub)=>{content='<header class="header"><div><h1>♡ '+escape(title)+'</h1><p>'+escape(sub)+'</p></div></header>'+html;}
 };
 vm.createContext(ctx);vm.runInContext(slice,ctx,{timeout:2000});
 vm.runInContext('renderSettings()',ctx,{timeout:2000});
 const nav='<nav class="nav"><div class="nav-inner">'+[
  ['⌂','Accueil'],['🎲','Soirée'],['✦','Surprise'],['🎁','Avent'],['⚙','Réglages']
 ].map(([symbol,label],i)=>'<button class="'+(i===4?'active':'')+'"><span>'+symbol+'</span>'+label+'</button>').join('')+'</div></nav>';
 const links=['styles.css','premium-theme.css','premium-home.css','premium-couple-pages.css','premium-advent.css','settings-premium.css']
  .map(file=>'<link rel="stylesheet" href="./'+file+'">').join('');
 const fixture='<!doctype html><html lang="fr-CA"><head><meta charset="utf-8">'+
  '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'+links+
  '</head><body><main id="app" class="loveroll-settings-page">'+content+nav+
  '</main><script>const root=document.querySelector("#app");let settingsOpenSection="";'+accordion+
  '\ninitSettingsAccordion();</script></body></html>';
 const file=path.join(dir,'__settings-test.html');fs.writeFileSync(file,fixture);
 const captures=path.join(dir,'..','landing-captures');fs.mkdirSync(captures,{recursive:true});
 const browser=await chromium.launch({headless:true});
 for(const item of [{name:'S25-Ultra',width:412,height:915},{name:'S25-Ultra-compact',width:412,height:824},{name:'iPhone',width:393,height:852}]){
  const page=await browser.newPage({viewport:{width:item.width,height:item.height},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8765/__settings-test.html',{waitUntil:'load'});
  await page.locator('details.settings-group').first().waitFor();
  let result=await page.evaluate(()=>({
   groups:document.querySelectorAll('details.settings-group').length,
   opened:document.querySelectorAll('details.settings-group[open]').length,
   scrollWidth:document.documentElement.scrollWidth,
   viewportWidth:innerWidth,
   bodyHeight:document.documentElement.scrollHeight,
   viewportHeight:innerHeight,
   navCount:document.querySelectorAll('.nav button').length
  }));
  if(errors.length||result.groups!==8||result.opened||result.scrollWidth>result.viewportWidth+2||result.navCount!==5)
   throw Error('Collapsed settings wrong '+item.name+' '+JSON.stringify({result,errors}));
  if(item.name.startsWith('S25-Ultra')&&result.bodyHeight>result.viewportHeight+2)
   throw Error('Collapsed settings do not fit screen '+item.name+' '+JSON.stringify(result));
  if(item.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-REGLAGES-FERMES.png')});
  await page.locator('details.settings-group[data-settings-section="compte"] > summary').click();
  if(await page.locator('details.settings-group[open]').count()!==1)throw Error('Cannot open account');
  if(item.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-REGLAGES-COMPTE.png')});
  await page.locator('details.settings-group[data-settings-section="couple"] > summary').click();
  if(await page.locator('details.settings-group[open]').count()!==1||
   !(await page.locator('details.settings-group[data-settings-section="couple"]').evaluate(el=>el.open)))
   throw Error('Accordion did not close previous section');
  if(item.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-REGLAGES-COUPLE.png')});
  const overlays=await page.evaluate(()=>{
   const nav=document.querySelector('.nav').getBoundingClientRect();
   const btn=document.querySelector('details.settings-group[open] button');
   const rect=btn?.getBoundingClientRect();return {navTop:nav.top,buttonTop:rect?.top||0};
  });
  console.log(item.name,JSON.stringify({result,errors,overlays}));
  await page.close();
 }
 await browser.close();
})().catch(error=>{console.error(error);process.exit(1)});
