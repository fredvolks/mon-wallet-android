/* Uses the actual discoverHtml and authHtml exports, not an invented screen. */
const {chromium}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
(async()=>{
 const fixture=path.join(__dirname,'__invite-account-test.html');
 fs.writeFileSync(fixture,`<!doctype html><html lang="fr-CA"><head><meta charset="utf-8">
 <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
 <link rel="stylesheet" href="./styles.css"><link rel="stylesheet" href="./premium-theme.css"><link rel="stylesheet" href="./discover.css"><link rel="stylesheet" href="./approved-artwork.css"><link rel="stylesheet" href="./invite-account.css">
 </head><body><main id="app"></main><script type="module">
 import {discoverHtml} from './discover.mjs';
 import {authHtml} from './onboarding.mjs';
 let mode='signup';
 const root=document.querySelector('#app');
 function draw(){root.innerHTML=mode==='invitation'?discoverHtml(true):authHtml(true,mode)}
 mode='invitation';draw();
 document.addEventListener('click',e=>{
  const action=e.target.closest('[data-action]')?.dataset.action;
  if(!action)return;
  e.preventDefault();
  if(action==='invite-start'){mode='signup';draw();}
  if(action==='auth-mode:signin'){mode='signin';draw();}
  if(action==='auth-mode:signup'){mode='signup';draw();}
  if(action==='auth-eye'){
   const field=document.querySelector('#signin-password');
   field.type=field.type==='password'?'text':'password';
  }
 });
 </script></body></html>`);
 const captures=path.join(__dirname,'..','landing-captures');fs.mkdirSync(captures,{recursive:true});
 const browser=await chromium.launch({headless:true});
 for(const view of [{name:'S25-Ultra',width:412,height:915},{name:'S25-Ultra-short',width:412,height:824},{name:'iPhone',width:393,height:852}]){
  const page=await browser.newPage({viewport:{width:view.width,height:view.height},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8765/__invite-account-test.html',{waitUntil:'load'});
  await page.locator('[data-action="invite-start"]').waitFor();
  const first=await page.evaluate(()=>({maxWidth:document.documentElement.scrollWidth,inner:innerWidth,sections:document.querySelectorAll('.discover-feature').length,hasSubmit:!!document.querySelector('.discover-accept')}));
  const hero=await page.evaluate(()=>{
   const img=document.querySelector('.discover-hero-premium');
   const outer=document.querySelector('.discover-showcase').getBoundingClientRect();
   const inner=img?.getBoundingClientRect();
   return {naturalWidth:img?.naturalWidth||0,naturalHeight:img?.naturalHeight||0,
    complete:!!img?.complete,top:inner?.top||0,width:inner?.width||0,
    outerWidth:outer.width,outerHeight:outer.height,visible:!!inner?.width};
  });
  if(hero.naturalWidth<1400||hero.naturalHeight<750||!hero.complete||
   !hero.visible||hero.width<hero.outerWidth-2)
   throw Error('Hero HD missing, not loaded or cropped unexpectedly '+view.name+' '+JSON.stringify(hero));
  if(errors.length||first.maxWidth>first.inner+2||first.sections!==4||!first.hasSubmit)
   throw Error('Invite first page invalid '+view.name+JSON.stringify({first,errors}));
  if(view.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-INVITATION-PAGE1.png')});
  await page.locator('[data-action="invite-start"]').click();
  await page.locator('[data-action="auth-signup-password"]').waitFor();
  let data=await page.evaluate(()=>{
   const card=document.querySelector('.invite-auth-card').getBoundingClientRect();
   return {width:document.documentElement.scrollWidth,screen:innerWidth,height:document.documentElement.scrollHeight,viewport:innerHeight,
    submit:document.querySelector('[data-action="auth-signup-password"]').getBoundingClientRect().bottom,
    passwordType:document.querySelector('#signin-password').type,
    cardBottom:card.bottom};
  });
  if(errors.length||data.width>data.screen+2||data.passwordType!=='password')
   throw Error('Signup page invalid '+view.name+JSON.stringify({data,errors}));
  if(view.name==='S25-Ultra'&&data.height>data.viewport+2)throw Error('Signup requires scrolling '+JSON.stringify(data));
  if(view.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-INVITATION-PAGE2-CREER.png')});
  await page.locator('[data-action="auth-eye"]').click();
  if(await page.locator('#signin-password').getAttribute('type')!=='text')throw Error('Eye visibility toggle broken');
  await page.locator('[data-action="auth-mode:signin"]').click();
  await page.locator('[data-action="auth-signin-password"]').waitFor();
  if(view.name==='S25-Ultra')await page.screenshot({path:path.join(captures,'S25-Ultra-INVITATION-PAGE2-CONNEXION.png')});
  if(await page.locator('[data-action="auth-reset-request"]').count()!==1)throw Error('Recovery button missing');
  console.log(view.name,JSON.stringify({first,hero,data,errors}));
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
