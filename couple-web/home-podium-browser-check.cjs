// Real weekly die and production Home, with synthetic distinct partner names only.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
const {createWorld,setupEditorial}=require('./editorial-fixture.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const failures=[];fs.mkdirSync('design-captures',{recursive:true});
 try{
  for(const [name,width,height] of [['small',360,640],['Android',360,740],['S25',412,824],['S25-tall',412,915],['iPhone',393,852],['iPad',820,1180],['PC',1366,768]]){
   for(const face of [null,2,6]){
    const world=createWorld();world.profiles[0].display_name='Fred';world.profiles[1].display_name='Fred Messely';
    const now=new Date(),toronto=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Toronto',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
    const monday=new Date(toronto+'T12:00:00Z');monday.setUTCDate(monday.getUTCDate()-(monday.getUTCDay()+6)%7);
    world.rolls=[{id:'month',couple_id:'c',kind:'monthly_activity',key:toronto.slice(0,7),face:19,activity_title:'Activité officielle de test',activity_description:'Texte officiel conservé.'}];
    if(face)world.rolls.push({id:'week',couple_id:'c',kind:'weekly',key:monday.toISOString().slice(0,10),face,created_at:now.toISOString()});
    const {page,context,errors}=await setupEditorial(browser,world,{width,height,query:'open=home',motion:'no-preference'});
    try{
     await page.waitForFunction(()=>document.querySelector('.home-die-platform canvas')?.loveRollDie?.projectedBounds);
     const inspect=()=>page.evaluate(()=>{
      const c=document.querySelector('.home-die-platform canvas'),b=c.loveRollDie.projectedBounds(),r=c.getBoundingClientRect(),scene=c.closest('.home-die-theater').getBoundingClientRect();
      const nav=document.querySelector('.nav').getBoundingClientRect(),stats=document.querySelector('.home-stats-grid').getBoundingClientRect();
      return {face:c.dataset.face,q:c.loveRollDie.readState().quaternion,webgl:c.dataset.webglReady,contact:Number(c.dataset.podiumContact),meshBottom:r.top+b.bottom*r.height-scene.top,meshTop:r.top+b.top*r.height-scene.top,sceneHeight:scene.height,sceneWidth:scene.width,navTop:nav.top,statsBottom:stats.bottom,bodyHeight:document.documentElement.scrollHeight,horizontal:document.documentElement.scrollWidth-innerWidth,textFits:[...document.querySelectorAll('.home-stat-card')].every(card=>[...card.querySelectorAll('strong,span,small')].every(t=>t.getBoundingClientRect().bottom<=Math.min(card.getBoundingClientRect().bottom,nav.top)+1))};
     });
     const initial=await inspect();await page.waitForTimeout(500);const result=await inspect();
     assert.deepEqual(result.q,initial.q,'Idle weekly die moved');assert.equal(result.webgl,'true');
     assert.ok(Math.abs(result.meshBottom-result.contact)<=2,JSON.stringify(result));assert.ok(result.meshTop>=-2&&result.meshBottom<=result.sceneHeight+2,JSON.stringify(result));
     assert.ok(result.statsBottom<=result.navTop-3&&result.textFits,JSON.stringify(result));assert.ok(result.bodyHeight<=height+2&&result.horizontal<=1,JSON.stringify(result));
     if(face)assert.equal(result.face,String(face));assert.deepEqual(errors,[]);
     // Regression: a live text refresh used to retain WebGL but drop the stage
     // transform. A size-only check misses this because the canvas is unchanged.
     await page.evaluate(()=>{window.__beforeRefresh=document.querySelector('.home-die-platform canvas');});
     for(let refresh=0;refresh<3;refresh++){
      await page.locator('.nav [data-action="tab:home"]').click();await page.waitForTimeout(50);
      const after=await inspect();
      assert.ok(await page.evaluate(()=>window.__beforeRefresh===document.querySelector('.home-die-platform canvas')),'Refresh remounted weekly WebGL');
      assert.deepEqual(after.q,result.q);assert.ok(Math.abs(after.meshBottom-after.contact)<=2,'Refresh lost podium contact '+JSON.stringify(after));
     }
     if(name==='S25'&&face===2){
      await page.evaluate(()=>{window.__homeDie=document.querySelector('.home-die-platform canvas');});
      await page.setViewportSize({width,height:915});await page.waitForTimeout(100);const resized=await inspect();
      assert.deepEqual(resized.q,initial.q);assert.ok(Math.abs(resized.meshBottom-resized.contact)<=2);
      assert.ok(await page.evaluate(()=>window.__homeDie===document.querySelector('.home-die-platform canvas')),'Resize replaced WebGL');
      await page.setViewportSize({width,height});
     }
     await page.screenshot({path:'design-captures/home123-'+name+'-'+(face||'unrolled')+'.png'});console.log('HOME_PODIUM_OK',name,face,JSON.stringify(result));
    }catch(error){failures.push({name,face,error:error.message});await page.screenshot({path:'design-captures/home123-'+name+'-'+(face||'unrolled')+'-FAIL.png'}).catch(()=>{});}
    finally{await context.close();}
   }
  }
 }finally{await browser.close();}
 assert.deepEqual(failures,[]);
})().catch(e=>{console.error(e);process.exit(1)});
