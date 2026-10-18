/* Real entry geometry, including Android/iOS Messenger. No authentication requests. */
const {chromium}=require('playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
const BASE='http://127.0.0.1:8765/';
(async()=>{
 const [{discoverHtml},{authHtml},{messengerExternalBrowserHtml}]=await Promise.all([import('./discover.mjs'),import('./onboarding.mjs'),import('./messenger-browser.mjs')]);
 const styles=[...fs.readFileSync('couple-web/index.html','utf8').matchAll(/<link rel="stylesheet" href="\.\/([^"?]+)(?:\?[^"]*)?"/g)].map(m=>'<link rel="stylesheet" href="'+BASE+m[1]+'">').join('');
 const browser=await chromium.launch({headless:true});let checks=0;
 fs.mkdirSync('design-captures',{recursive:true});
 try{
 for(const [width,height] of [[412,824],[393,760],[360,640],[375,520],[320,568],[820,1180],[1180,820],[1440,900]]){
  const page=await browser.newPage({viewport:{width,height},isMobile:true,hasTouch:true});
  await page.goto(BASE+'__premium-couple-pages-test.html');
  for(const help of ['',messengerExternalBrowserHtml('https://fredvolks.github.io/mon-wallet-android/?invite_id=8a6689bd-7b5d-45a9-8948-3f36c2809df7'),messengerExternalBrowserHtml('https://fredvolks.github.io/mon-wallet-android/?invite_id=8a6689bd-7b5d-45a9-8948-3f36c2809df7',{ios:true})]){
   for(const mode of ['invite','public','signup','signin']){
    const html=mode==='invite'?discoverHtml(true,help):mode==='public'?discoverHtml():authHtml(true,mode,'','',false,{google:true,browserHelp:help});
    await page.setContent('<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'+styles+'</head><body><main id="app">'+html+'</main></body></html>',{waitUntil:'load'});
    await page.evaluate(()=>document.fonts.ready);
    const fit=await page.evaluate(()=>({height:innerHeight,scrollHeight:document.documentElement.scrollHeight,width:innerWidth,scrollWidth:document.documentElement.scrollWidth,controls:[...document.querySelectorAll('button,a,summary')].filter(e=>e.getClientRects().length&&!(e.closest('details:not([open])')&&!e.closest('summary'))).map(e=>{const b=e.getBoundingClientRect();return {action:e.dataset.action||e.textContent.trim().slice(0,25),top:b.top,bottom:b.bottom,height:b.height,width:b.width};})}));
    if(width===412&&height===824&&(!help||help.includes('Ouvrir dans Chrome')))await page.screenshot({path:'design-captures/entry139-'+mode+(help?'-messenger':'')+'.png'});
    assert.ok(fit.scrollWidth<=width+1,mode+' horizontal overflow '+JSON.stringify(fit));
    assert.ok(fit.scrollHeight<=height+2,mode+' '+width+'x'+height+(help?' Messenger':'')+' should fit one screen '+JSON.stringify(fit));
    for(const c of fit.controls){assert.ok(c.top>=-1&&c.bottom<=height+1,mode+' clipped '+JSON.stringify(c));assert.ok(c.height>=43,mode+' touch target '+JSON.stringify(c));}
    if(help&&mode!=='public'){const color=await page.locator('.messenger-browser-primary').evaluate(e=>getComputedStyle(e).backgroundColor);assert.equal(color,'rgb(73, 32, 52)');}
    checks++;
   }
  }
  await page.close();
 }
 // Additional help and validation remain reachable; keyboard-sized windows may scroll.
 const page=await browser.newPage({viewport:{width:393,height:360},isMobile:true});
 await page.goto(BASE+'__premium-couple-pages-test.html');
 await page.setContent('<html><head><meta name="viewport" content="width=device-width,initial-scale=1">'+styles+'</head><body><main id="app">'+authHtml(true,'signin')+'</main></body></html>');
 await page.locator('#signin-email').focus();await page.locator('[data-action="auth-signin-password"]').scrollIntoViewIfNeeded();
 const submit=await page.locator('[data-action="auth-signin-password"]').boundingBox();assert.ok(submit.y>=0&&submit.y+submit.height<=360);
 await page.close();console.log('ENTRY_RENDEZVOUS_139',checks,'one-screen mobile/tablet/desktop and Messenger checks passed');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
