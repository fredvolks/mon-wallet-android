// Real first paint, real app routing and recovery. No live users or auth writes.
const {chromium}=require('playwright'),fs=require('node:fs'),assert=require('node:assert/strict');
const origin='http://127.0.0.1:8765/';
const fake=fs.readFileSync('couple-web/boot-smoke.cjs','utf8').match(/const fakeSupabase=`([\s\S]*?)`;/)[1];
const gated=fake.replace('getUser:async()=>({data:{user:authenticated?user:null},error:null}),','getUser:async()=>{await window.__bootGate;return {data:{user:authenticated?user:null},error:null};},');
(async()=>{
 const site=fs.readFileSync('couple-web/index.html','utf8'),markup=site.match(/<main id="app">([\s\S]*?)<\/main>/)[1].replaceAll('src="./assets/','src="'+origin+'assets/');
 fs.mkdirSync('design-captures',{recursive:true});
 const browser=await chromium.launch({headless:true});
 try{
  for(const [name,width,height] of [['small',360,640],['S25',412,824],['S25-tall',412,915],['iPhone',393,852],['iPad',820,1180],['iPad-landscape',1180,820],['PC',1366,768],['landscape-phone',740,360]]){
   const page=await browser.newPage({viewport:{width,height},isMobile:width<768,hasTouch:width<768});
   await page.setContent('<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><link rel="stylesheet" href="'+origin+'loveroll-splash.css"></head><body style="margin:0"><main id="app">'+markup+'</main><script src="'+origin+'loveroll-splash.js"></script></body></html>'.replaceAll('src="./assets/','src="'+origin+'assets/'),{waitUntil:'load'});
   await page.locator('.lr-splash__heart').evaluate(e=>e.decode());
   const info=await page.evaluate(()=>{
    const bounds=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height};};
    return {width:innerWidth,height:innerHeight,scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,
     heart:bounds(document.querySelector('.lr-splash__heart')),logo:bounds(document.querySelector('.lr-splash__logo')),caption:bounds(document.querySelector('.lr-splash__caption')),
     text:document.querySelector('.lr-splash__caption').innerText,dots:document.querySelectorAll('.lr-splash__dots i').length,
     obsolete:document.querySelectorAll('.lr-splash__ring,.lr-splash__back-heart').length,buttons:document.querySelectorAll('button,nav').length,
     loaded:[...document.images].every(i=>i.complete&&i.naturalWidth>0),animation:getComputedStyle(document.querySelector('.lr-splash__heart')).animationName};
   });
   assert.equal(info.text,'Chargement de votre espace…');assert.equal(info.dots,3);assert.equal(info.obsolete,0);assert.equal(info.buttons,0);assert.ok(info.loaded);
   assert.equal(info.animation,'lr-splash-beat');assert.ok(info.scrollWidth<=width&&info.scrollHeight<=height);
   for(const element of [info.heart,info.logo,info.caption]){assert.ok(Math.abs(element.x+element.w/2-width/2)<2);assert.ok(element.y>=0&&element.y+element.h<=height);}
   await page.screenshot({path:'design-captures/glass123-'+name+'.png'});
   await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await page.locator('.lr-splash__heart').evaluate(e=>getComputedStyle(e).animationName),'none');
   await page.evaluate(()=>document.querySelector('#app').replaceChildren());await page.waitForTimeout(30);assert.equal(await page.locator('.lr-splash').count(),0);
   await page.emulateMedia({reducedMotion:'no-preference'});
   await page.evaluate(()=>{window.__ticket1=LoveRollLoader.begin(0);window.__ticket2=LoveRollLoader.begin(0);});await page.locator('.lr-splash--overlay').waitFor();
   await page.evaluate(()=>LoveRollLoader.end(window.__ticket1));assert.equal(await page.locator('.lr-splash--overlay').count(),1);
   await page.evaluate(()=>LoveRollLoader.end(window.__ticket2));assert.equal(await page.locator('.lr-splash--leaving').evaluate(e=>getComputedStyle(e).pointerEvents),'none');
   await page.locator('.lr-splash--overlay').waitFor({state:'detached'});console.log('GLASS_SPLASH_OK',name,JSON.stringify(info));await page.close();
  }
  for(const [name,query,expected] of [['no-session','boot_case=anonymous','.love-discover'],['saved-session','boot_case=paired','.loveroll-real-home'],['invitation','boot_case=anonymous&invite_id=72aa640b-2644-4d0d-bcf7-575587d27f31','[data-action="invite-start"]']]){
   const page=await browser.newPage({viewport:{width:412,height:824},serviceWorkers:'block'});
   await page.addInitScript(()=>{window.__bootGate=new Promise(resolve=>window.__releaseBoot=resolve);});
   await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',r=>r.fulfill({contentType:'application/javascript',body:gated}));
   await page.goto(origin+'?'+query,{waitUntil:'domcontentloaded'});await page.locator('[data-lr-boot]').waitFor();assert.equal(await page.locator('.nav').count(),0);
   await page.evaluate(()=>window.__releaseBoot());await page.locator(expected).waitFor();
   const leaving=page.locator('.lr-splash--leaving');if(await leaving.count())assert.equal(await leaving.evaluate(e=>getComputedStyle(e).pointerEvents),'none');
   await page.locator('.lr-splash').waitFor({state:'detached'});console.log('REAL_BOOT_ROUTE_OK',name);await page.close();
  }
  for(const failure of ['syntax','network']){
   const page=await browser.newPage({viewport:{width:412,height:824},serviceWorkers:'block'});await page.clock.install();
   await page.route('**/app.js?*',r=>failure==='syntax'?r.fulfill({contentType:'application/javascript',body:'export const broken = ;'}):r.abort('internetdisconnected'));
   const invite='72aa640b-2644-4d0d-bcf7-575587d27f31';await page.goto(origin+'?invite_id='+invite,{waitUntil:'domcontentloaded'});
   await page.clock.fastForward(18100);await page.locator('.boot-retry-button').waitFor();assert.equal(await page.locator('.lr-splash').count(),0);
   await page.screenshot({path:'design-captures/glass123-error-'+failure+'.png'});
   await page.locator('.boot-retry-button').click();await page.waitForURL(u=>u.searchParams.has('boot_retry'));assert.equal(new URL(page.url()).searchParams.get('invite_id'),invite);
   console.log('BOOT_RECOVERY_OK',failure,'retry keeps invitation');await page.close();
  }
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exit(1)});
