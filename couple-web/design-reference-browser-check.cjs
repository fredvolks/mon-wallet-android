// Production DOM and real WebGL, synthetic couple data only. No live account writes.
const {chromium}=require('playwright');
const fs=require('node:fs');const assert=require('node:assert/strict');
const fake=fs.readFileSync('couple-web/boot-smoke.cjs','utf8').match(/const fakeSupabase=`([\s\S]*?)`;/)[1];
(async()=>{
 fs.mkdirSync('design-captures',{recursive:true});
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const failures=[];const report=[];
 try{
 for(const [name,width,height] of [['S25',412,915],['S25-short',412,824],['Android',360,740],['iPhone',393,852],['iPad',820,1180],['PC',1440,1000]]){
  const page=await browser.newPage({viewport:{width,height},serviceWorkers:'block',isMobile:width<768,hasTouch:width<900});
  const errors=[];page.on('pageerror',err=>errors.push(err.message));
  await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',r=>r.fulfill({contentType:'application/javascript',body:fake}));
  await page.goto('http://127.0.0.1:8765/?boot_case=paired-active');
  await page.locator('.real-home-shell').waitFor();
  for(const tab of ['home','session','special','advent','settings']){
   try{
    if(tab!=='home')await page.locator('.nav [data-action="tab:'+tab+'"]').click();
    await page.waitForTimeout(700);
    if(await page.locator('canvas').count())await page.waitForFunction(()=>[...document.querySelectorAll('canvas[data-dice-sides]')].every(c=>c.dataset.webglReady==='true'),null,{timeout:20000});
    await page.screenshot({path:'design-captures/'+name+'-'+tab+'.png',fullPage:true});
    const geometry=await page.evaluate(()=>{
     const main=document.querySelector('main'),nav=document.querySelector('.nav').getBoundingClientRect();
     return {scrollWidth:document.documentElement.scrollWidth,mainScroll:main.scrollHeight,mainClient:main.clientHeight,navTop:nav.top,navBottom:nav.bottom,
      dice:[...document.querySelectorAll('canvas[data-dice-sides]')].map(c=>({side:c.dataset.diceSides,face:c.dataset.face,width:c.clientWidth,height:c.clientHeight,webgl:c.dataset.webglReady})),
      stats:[...document.querySelectorAll('.home-stat-card strong')].map(n=>n.textContent)};
    });
    report.push({name,tab,width,height,...geometry});
    assert.ok(geometry.scrollWidth<=width+1,'horizontal overflow');
    assert.ok(geometry.navBottom<=height+1&&geometry.navTop>=0,'navigation outside viewport');
    assert.equal(await page.locator('.nav button').count(),5);
    if(tab==='home'){
     assert.equal(await page.locator('.ritual-brand').count(),1);
     assert.equal(await page.locator('.home-weights button').count(),0);
     assert.equal(await page.locator('.home-weight').count(),6);
     assert.deepEqual(geometry.stats,['0','0 / 0','—','0']);
     assert.ok(geometry.dice[0].width>=(height<=860?160:175),'weekly die too small');
     if(name.startsWith('S25'))assert.ok(await page.evaluate(()=>document.querySelector('.home-stats-grid').getBoundingClientRect().bottom<=document.querySelector('.nav').getBoundingClientRect().top+2),'Home stats under bottom navigation');
     await page.locator('.home-next-month').click();await page.locator('.surprise-premium-view').waitFor();
     await page.locator('.nav [data-action="tab:home"]').click();
    }
    if(tab==='session'){
     const c=page.locator('.evening-d35-col canvas');await c.waitFor();
     await page.evaluate(()=>{window.__keptDie=document.querySelector('.evening-d35-col canvas')});
     for(const category of ['HASARD','SEXUEL','SENSUEL']){
      await page.locator('[data-action="category:'+category+'"]').click();
      assert.ok(await page.evaluate(()=>window.__keptDie===document.querySelector('.evening-d35-col canvas')),'category replaced D100');
     }
    }
    if(tab==='advent')assert.equal(await page.locator('.advent-box').count(),25);
    assert.deepEqual(errors,[],'page errors');
   }catch(error){failures.push(name+'/'+tab+': '+error.message);await page.screenshot({path:'design-captures/'+name+'-'+tab+'-FAIL.png',fullPage:true}).catch(()=>{});}
  }
  await page.close();
 }
 }finally{await browser.close();fs.writeFileSync('design-captures/report.json',JSON.stringify({report,failures},null,2))}
 console.log(JSON.stringify({report,failures},null,2));assert.deepEqual(failures,[]);
})().catch(e=>{console.error(e);process.exit(1)});
