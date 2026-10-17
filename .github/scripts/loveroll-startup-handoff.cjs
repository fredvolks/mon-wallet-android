// Exercise the actual Android overlay and packaged observer through a debug
// WebView. The trusted page is fulfilled locally; no account or auth calls occur.
const {_android:android}=require('playwright');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const site='https://fredvolks.github.io/mon-wallet-android/';
const adb=(...args)=>execFileSync('adb',args,{encoding:'utf8',timeout:20000});
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const source=fs.readFileSync('couple-web/index.html','utf8');
const boot=source.match(/<main id="app">([\s\S]*?)<\/main>/)[1];
const fixture='<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'+
 '<link rel="stylesheet" href="./loveroll-splash.css"></head><body style="margin:0;background:#10070c;color:#f5dcd8">'+
 '<main id="app">'+boot+'</main><script src="./loveroll-splash.js"></script></body></html>';
const files=new Map([
 ['loveroll-splash.css','text/css'],['loveroll-splash.js','application/javascript'],
 ['assets/loveroll-glass-heart-v123.webp','image/webp'],['assets/loveroll-splash-satin-v123.webp','image/webp']
]);
async function capture(name){
 let error;
 for(let attempt=0;attempt<4;attempt++){
  try{
   adb('shell','uiautomator','dump','/sdcard/'+name+'.xml');
   adb('pull','/sdcard/'+name+'.xml','qa/'+name+'.xml');
   const xml=fs.readFileSync('qa/'+name+'.xml','utf8');
   fs.writeFileSync('qa/'+name+'.png',execFileSync('adb',['exec-out','screencap','-p'],{timeout:20000}));
   return xml;
  }catch(e){error=e;await pause(500);}
 }
 throw error;
}
(async()=>{
 fs.mkdirSync('qa',{recursive:true});
 let device;
 try{
  const devices=await android.devices();
  assert.equal(devices.length,1,'The regression requires one isolated Android emulator');
  device=devices[0];
  const webview=await device.webView({pkg:'ca.desadeux.jeu'});
  // Android's entry point avoids desktop-only browser-context commands that
  // the actual WebView protocol intentionally does not implement.
  const page=await webview.page();
  const context=page.context();
  assert.ok(page.url().startsWith(site),'The installed app must expose its trusted WebView in debug builds');
  const protocol=await context.newCDPSession(page);
  await protocol.send('Network.enable');
  await protocol.send('Network.setBypassServiceWorker',{bypass:true});
  // Restrict the fixture to the existing trusted origin. Android still executes
  // its real onPageStarted/onPageFinished, Java bridge and visual-state callback.
  await context.route('**/*',async route=>{
   const url=new URL(route.request().url());
   if(!url.href.startsWith(site))return route.abort();
   const relative=url.pathname.slice(new URL(site).pathname.length);
   if(!relative)return route.fulfill({contentType:'text/html',body:fixture});
   if(files.has(relative))return route.fulfill({contentType:files.get(relative),body:fs.readFileSync(path.join('couple-web',relative))});
   return route.abort();
  });
  for(const mode of ['destination','recovery']){
   await page.goto(site+'?startup_handoff_qa='+mode,{waitUntil:'load'});
   await page.waitForFunction(()=>document.readyState==='complete'&&window.__loveRollNativeStartup?.serial>0);
   assert.equal(await page.locator('[data-lr-boot]').count(),1);
   // Page load is finished but the asynchronous destination is still withheld.
   // The old implementation would already have swapped to the narrower Web logo.
   const held=await capture('loveroll-native-startup-held-'+mode);
   assert.ok(held.includes('content-desc="Chargement de votre espace…"'),'Native heart disappeared while the HTML loading screen was still present');
   await page.evaluate(mode=>{
    if(mode==='recovery')window.dispatchEvent(new Event('loveroll:boot-error'));
    document.querySelector('#app').innerHTML=mode==='recovery'?
     '<section role="alert" style="padding:48px"><h1>LoveRoll</h1><button>Réessayer</button></section>':
     '<section style="padding:48px"><h1>LoveRoll est prêt</h1><p>Votre espace</p></section>';
   },mode);
   await page.locator('.lr-splash').waitFor({state:'detached'});
   await pause(350);
   const ready=await capture('loveroll-native-startup-ready-'+mode);
   assert.ok(!ready.includes('content-desc="Chargement de votre espace…"'),'Native startup remained above the rendered destination');
   assert.ok(ready.includes('android.webkit.WebView'),'The painted WebView must be accessible after startup');
   assert.ok(ready.includes(mode==='recovery'?'Réessayer':'LoveRoll est prêt'),'The real destination must be visible, with no second loader');
   console.log('LOVEROLL_NATIVE_SINGLE_STARTUP_OK',mode);
  }
 }finally{
  if(device)await device.close();
 }
})().catch(error=>{console.error(error);process.exitCode=1;});
