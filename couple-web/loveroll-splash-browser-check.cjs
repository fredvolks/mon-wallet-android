// Screenshot-independent Chromium check: genuine loaded CSS/HTML on phone,
// tablet and desktop, real pulse animation, shared lifecycle and no scrolling.
const {chromium}=require('playwright');
const fs=require('node:fs');
const assert=require('node:assert/strict');
const origin='http://127.0.0.1:8765/';
(async()=>{
 const site=fs.readFileSync('couple-web/index.html','utf8');
 const match=site.match(/<main id="app">([\s\S]*?)<\/main>/);
 assert.ok(match&&match[1].includes('data-lr-boot'),'Static splash missing from HTML');
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  for(const [label,width,height] of [
   ['Galaxy S25',412,824],['Chrome with toolbar',360,640],
   ['iPhone',393,780],['iPad',820,1180],
   ['iPad landscape',1180,820],['Desktop',1440,900]
  ]){
   const page=await browser.newPage({viewport:{width,height},isMobile:width<768,hasTouch:width<768});
   await page.setContent('<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'+
    '<link rel="stylesheet" href="'+origin+'loveroll-splash.css"></head><body style="margin:0">'+
    '<main id="app">'+match[1]+'</main><script src="'+origin+'loveroll-splash.js"></script></body></html>',
    {waitUntil:'load'});
   const info=await page.evaluate(()=>{
    const heart=document.querySelector('.lr-splash__heart');
    const rect=heart.getBoundingClientRect(),status=document.querySelector('.lr-splash');
    const anim=getComputedStyle(heart).animationName;
    return {innerWidth,innerHeight,scrollW:document.documentElement.scrollWidth,
     scrollH:document.documentElement.scrollHeight,
     x:rect.x+rect.width/2,y:rect.y+rect.height/2,
     w:rect.width,h:rect.height,animation:anim,
     label:status?.getAttribute('aria-label'),text:status?.innerText.trim(),
     rings:status?.querySelectorAll('.lr-splash__ring').length};
   });
   assert.equal(info.text,'',label+' should have no visible loading text');
   assert.equal(info.rings,4,label+' must show concentric waves');
   assert.equal(info.animation,'lr-splash-beat',label+' heart not animated');
   assert.ok(info.w>40&&info.h>40,label+' heart missing');
   assert.ok(Math.abs(info.x-info.innerWidth/2)<5&&Math.abs(info.y-info.innerHeight/2)<5,
    label+' heart not centered '+JSON.stringify(info));
   assert.ok(info.scrollW<=width+2&&info.scrollH<=height+2,
    label+' scrollbars '+JSON.stringify(info));
   await page.evaluate(()=>{document.querySelector('#app').replaceChildren();window._ticket=LoveRollLoader.begin(0);});
   await page.locator('body>.lr-splash--overlay').waitFor({timeout:2500});
   await page.evaluate(()=>LoveRollLoader.end(window._ticket));
   await page.locator('body>.lr-splash--overlay').waitFor({state:'detached',timeout:2500});
   console.log('HEART_SPLASH_OK',label,width,height);
   await page.close();
  }
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
