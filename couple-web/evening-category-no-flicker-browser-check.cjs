// Browser regression: one D100 stays mounted, no category-switch transient.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const origin='http://127.0.0.1:8765';
(async()=>{
 const {premiumEveningHtml}=await import('./premium-evening.mjs');
 const css=[...fs.readFileSync('couple-web/index.html','utf8').matchAll(/<link rel="stylesheet" href="\.\/([^"]+)"/g)]
   .map(x=>'<link rel="stylesheet" href="'+origin+'/'+x[1]+'">').join('');
 const users=[{user_id:'one',name:'Fred'},{user_id:'two',name:'Fred'}];
 const html=premiumEveningHtml({session:{id:'one'},members:users,me:'one',
  rolls:[{user_id:'one',face:2},{user_id:'two',face:5}],
  status:{my_rolled:true,partner_rolled:true,both_ready:true},
  turn:{ready:true,next:'one',remaining:{one:2,two:5},
   completed:{one:0,two:0},targets:{one:2,two:5}}});
 const browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 try{
  for(const [width,height] of [[360,640],[393,852],[820,1180],[1180,820],[1440,900]]){
   const page=await browser.newPage({viewport:{width,height}});
   await page.route(origin+'/category-only-test.html',route=>route.fulfill({
    contentType:'text/html',body:'<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1">'+css+
    '</head><body><main id="app" class="loveroll-evening-page">'+html+'</main></body></html>'
   }));
   await page.goto(origin+'/category-only-test.html',{waitUntil:'load'});
   const result=await page.evaluate(async()=>{
    const root=document.querySelector('#app');
    const die=root.querySelector('canvas[data-dice-sides="100"]');
    let removals=0;
    const watcher=new MutationObserver(records=>{
     for(const record of records)for(const node of record.removedNodes)
      if(node===die||node.contains?.(die))removals++;
    });
    watcher.observe(root,{subtree:true,childList:true});
    root.querySelector('button[data-action="new-challenge"]')?.focus();
    await new Promise(resolve=>requestAnimationFrame(resolve));
    watcher.disconnect();
    return {dice:root.querySelectorAll('canvas[data-dice-sides="100"]').length,
     categories:root.querySelectorAll('button[data-action^="category:"]').length,
     primary:!!root.querySelector('button[data-action="new-challenge"]'),
     visible:!!die&&die.getBoundingClientRect().width>50,
     same:die===root.querySelector('canvas[data-dice-sides="100"]'),
     removals};
   });
   assert.ok(result.dice===1&&result.categories===0&&result.primary&&
     result.visible&&result.same&&result.removals===0,
     'Single D100 canvas should stay mounted '+JSON.stringify({width,height,result}));
   console.log('UNIFIED_D100_PERSIST_OK',width,height);
   await page.close();
  }
 }finally{await browser.close();}
})().catch(e=>{console.error(e.stack||e);process.exit(1);});
