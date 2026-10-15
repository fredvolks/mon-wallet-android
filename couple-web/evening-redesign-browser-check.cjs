// Browser geometry regression: No two huge overlapping D6s on mobile.
const {chromium}=require('playwright');
const fs=require('node:fs');
const assert=require('node:assert/strict');
const origin='http://127.0.0.1:8765/';
async function main(){
 const {premiumEveningHtml}=await import('./premium-evening.mjs');
 const index=fs.readFileSync('couple-web/index.html','utf8');
 const styles=[...index.matchAll(/<link rel="stylesheet" href="\.\/([^"]+)"/g)]
   .map(x=>'<link rel="stylesheet" href="'+origin+x[1]+'">').join('');
 const browser=await chromium.launch({headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 try{
  for(const [width,height] of [[393,852],[360,640],[412,915],[820,1180],[1180,820]]){
   for(const ready of [false,true]){
    const me='one',other='two',members=[{user_id:me,name:'Fred Messely'},{user_id:other,name:'Partenaire'}];
    const rolls=ready?[{user_id:me,face:2},{user_id:other,face:5}]:[];
    const html=premiumEveningHtml({session:{id:'s1'},members,me,rolls,
      status:{my_rolled:ready,partner_rolled:ready,both_ready:ready},
      turn:{ready,first:me,next:me,targets:{one:2,two:5},remaining:{one:2,two:5},completed:{one:0,two:0}},
      category:'SENSUEL',ownChallengeCount:2,
      playable:{sensual:35,sexual:35,total:70}});
    const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:1,isMobile:width<768,hasTouch:width<768});
    await page.setContent('<!doctype html><html lang="fr"><head><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'+styles+
      '</head><body style="margin:0"><main id="app" class="loveroll-evening-page">'+html+
      '<nav class="nav"><div class="nav-inner"><button type="button">Soirée</button></div></nav></main></body></html>',
      {waitUntil:'load'});
    const geometry=await page.evaluate(()=>{
      const main=document.querySelector('main'),evening=document.querySelector('.evening-premium-view'),
       step=document.querySelector('.evening-step.one'),stage=document.querySelector('.evening-my-die .love3d-stage, .evening-user-die .love3d-stage'),
       partner=document.querySelector('.evening-partner-card'),gift=document.querySelector('.evening-gift'),
       nav=document.querySelector('.nav'),manage=document.querySelector('.evening-manage');
      const rect=e=>{let r=e?.getBoundingClientRect();return r&&{x:r.x,y:r.y,width:r.width,height:r.height,right:r.right,bottom:r.bottom}};
      return {viewport:{width:innerWidth,height:innerHeight},docH:document.documentElement.scrollHeight,
        main:rect(main),content:rect(evening),step:rect(step),stage:rect(stage),
        partner:rect(partner),gift:rect(gift),nav:rect(nav),manage:rect(manage),
        canvases:step.querySelectorAll('canvas[data-dice-sides="6"]').length};
    });
    assert.equal(geometry.canvases,1, 'Two D6 renderers still present');
    if(width<768){
     assert.ok(geometry.gift&&geometry.gift.bottom<=(geometry.nav?.top??height)+2,
       'Gift hidden behind mobile nav '+JSON.stringify({width,height,ready,geometry}));
    }else{
     // Existing tablet/desktop navigation may sit above the page. Their
     // longer landscape layouts are intentionally not mobile no-scroll mode.
     assert.ok(geometry.gift&&geometry.gift.height>45&&geometry.gift.bottom<=geometry.docH+2,
       'Gift missing from desktop/tablet '+JSON.stringify({width,height,ready,geometry}));
    }
    assert.ok(geometry.manage?.width>=28,'Manage not reachable');
    if(!ready){
     assert.ok(geometry.partner&&geometry.partner.width>90,'Partner status missing');
     assert.ok(geometry.stage&&geometry.stage.width>=70&&geometry.stage.height>=70,
      'Personal die too small '+JSON.stringify({width,height,geometry}));
     assert.ok(geometry.stage.x>=geometry.step.x-1&&geometry.stage.right<=geometry.step.right+1,
      'D6 stage clipped horizontally '+JSON.stringify({width,height,geometry}));
     assert.ok(Math.abs(geometry.stage.width-geometry.stage.height)<4,
      'D6 canvas is not square '+JSON.stringify({width,height,geometry}));
    }
    if(width<768)assert.ok(geometry.docH<=height+3,
      'Mobile page scrolls '+JSON.stringify({width,height,ready,geometry}));
    console.log('EVENING_DESIGN_OK',width,height,ready?'ready':'waiting');
    await page.close();
   }
  }
 }finally{await browser.close()}
}
main().catch(err=>{console.error(err.stack||err);process.exit(1)});
