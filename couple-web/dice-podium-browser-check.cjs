// Exercise original WebGL geometry, including its idle orientation and photo contact.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
const {createWorld,setupEditorial}=require('./editorial-fixture.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']}),world=createWorld();fs.mkdirSync('design-captures',{recursive:true});
 const fake=fs.readFileSync('couple-web/boot-smoke.cjs','utf8').match(/const fakeSupabase=`([\s\S]*?)`;/)[1];
 try{
  for(const [name,width,height] of [['small',360,640],['S25',412,824],['S25-tall',412,915],['iPhone',393,852],['iPad',820,1180],['PC',1366,768]]){
   for(const phase of ['d20','host','d6','d100','intro']){
    world.rolls=phase==='host'?[{id:'activity',couple_id:'c',kind:'monthly_activity',key:new Intl.DateTimeFormat('en-CA',{timeZone:'America/Toronto',year:'numeric',month:'2-digit'}).format(new Date()).slice(0,7),face:19,activity_title:'Activité officielle',activity_description:'Texte officiel conservé.'}]:[];
    const {page,context,errors}=await setupEditorial(browser,world,{width,height,query:'open=special',motion:'no-preference'});
    if(['d6','d100','intro'].includes(phase)){
     await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',r=>r.fulfill({contentType:'application/javascript',body:fake}));
     await page.goto('http://127.0.0.1:8765/?boot_case='+(phase==='d6'?'paired-pending':phase==='intro'?'paired':'paired-active')+'&open=session');
    }
    await page.waitForFunction(()=>{const cs=[...document.querySelectorAll('canvas[data-dice-sides]')];return cs.length&&cs.every(c=>c.loveRollDie?.projectedBounds)});
    await page.waitForTimeout(150);
    const before=await page.evaluate(()=>[...document.querySelectorAll('canvas[data-dice-sides]')].filter(c=>c.getBoundingClientRect().height>0).map(c=>({q:c.loveRollDie.readState().quaternion,frames:c.loveRollDie.readState().renderedFrames})));
    await page.waitForTimeout(500);
    const boxes=await page.evaluate(()=>[...document.querySelectorAll('canvas[data-dice-sides]')].filter(c=>c.getBoundingClientRect().height>0).map(c=>{
     const scene=c.closest('.surprise-main-die-scene,.evening-user-die>.love3d,.evening-die-row,.evening-finale-hero'),r=c.getBoundingClientRect(),s=scene.getBoundingClientRect(),b=c.loveRollDie.projectedBounds(),contact=Number(c.dataset.podiumContact);
     return {sides:c.dataset.diceSides,q:c.loveRollDie.readState().quaternion,frames:c.loveRollDie.readState().renderedFrames,contact,meshBottom:r.top+b.bottom*r.height-s.top,meshTop:r.top+b.top*r.height-s.top,sceneHeight:s.height,sceneWidth:s.width};
    }));
    assert.ok(boxes.length);for(let i=0;i<boxes.length;i++){const box=boxes[i];assert.deepEqual(box.q,before[i].q,name+' '+phase+' idle die moved');assert.ok(Math.abs(box.meshBottom-box.contact)<=2,JSON.stringify({name,phase,box}));assert.ok(box.meshTop>=-2&&box.meshBottom<=box.sceneHeight+2,JSON.stringify({name,phase,box}));}
    await page.evaluate(()=>{window.__stableCanvases=[...document.querySelectorAll('canvas[data-dice-sides]')].filter(c=>c.getBoundingClientRect().height>0);});
    await page.locator('.nav [data-action="tab:'+(['d20','host'].includes(phase)?'special':'session')+'"]').click();await page.waitForTimeout(80);
    assert.ok(await page.evaluate(()=>window.__stableCanvases.every(c=>c.isConnected&&Math.abs(c.getBoundingClientRect().top+c.loveRollDie.projectedBounds().bottom*c.getBoundingClientRect().height-c.closest('.surprise-main-die-scene,.evening-user-die>.love3d,.evening-die-row,.evening-finale-hero').getBoundingClientRect().top-Number(c.dataset.podiumContact))<2)),'Refresh lost die or podium '+name+' '+phase);
    if(phase==='host')assert.equal(await page.locator('.surprise-host-legend').count(),1);
    assert.deepEqual(errors,[]);await page.screenshot({path:'design-captures/podium123-'+name+'-'+phase+'.png'});console.log('DICE_PODIUM_OK',name,phase,JSON.stringify(boxes));await context.close();
   }
  }
  const {page,context}=await setupEditorial(browser,world,{query:'open=home'});await page.locator('.home-ritual').waitFor();
  const overlap=await page.evaluate(()=>({text:document.querySelector('.home-ritual').getBoundingClientRect().bottom,weights:document.querySelector('.home-weights').getBoundingClientRect().top,info:document.querySelector('.home-roll-area p')?.textContent,crayons:document.querySelectorAll('.surprise-event-chevron').length}));
  assert.ok(overlap.text<=overlap.weights);assert.equal(overlap.info,undefined);await context.close();
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
