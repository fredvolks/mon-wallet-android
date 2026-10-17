// Genuine WebGL normals and quaternions, not DOM numbers or an image overlay.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const url='http://127.0.0.1:8765/__premium-couple-pages-test.html?page=evening&face=47';
 try{
  const reduced=await browser.newPage({viewport:{width:412,height:824},reducedMotion:'reduce'});
  await reduced.goto(url);await reduced.waitForFunction(()=>document.querySelector('canvas[data-dice-sides="100"]')?.loveRollDie);
  const result=await reduced.evaluate(()=>{
   const canvas=document.querySelector('canvas[data-dice-sides="100"]'),errors=[];
   for(let n=1;n<=100;n++){
    canvas.loveRollDie.rollTo(n);const state=canvas.loveRollDie.readState();
    if(state.face!==n||state.frontFace!==n||state.spinning)errors.push({n,state});
   }
   return errors;
  });assert.deepEqual(result,[],'Official numbered 3D face is not toward camera');
  await reduced.reload();await reduced.waitForFunction(()=>document.querySelector('canvas[data-dice-sides="100"]')?.loveRollDie);
  assert.equal(await reduced.locator('.evening-d100-result').textContent(),'Résultat : 47');
  assert.equal(await reduced.evaluate(()=>document.querySelector('canvas[data-dice-sides="100"]').loveRollDie.readState().frontFace),47);
  await reduced.close();
  const page=await browser.newPage({viewport:{width:412,height:824}});await page.goto(url);
  await page.waitForFunction(()=>document.querySelector('canvas[data-dice-sides="100"]')?.loveRollDie);
  await page.evaluate(()=>{window.__officialDie=document.querySelector('canvas[data-dice-sides="100"]');window.__officialDie.loveRollDie.rollTo(27);});
  for(let i=0;i<10;i++){
   assert.ok(await page.evaluate(()=>window.__officialDie===document.querySelector('canvas[data-dice-sides="100"]')&&window.__officialDie.isConnected&&window.__officialDie.getBoundingClientRect().height>100),'Die disappeared during animation');
   await page.waitForTimeout(500);
  }
  const settled=await page.evaluate(()=>window.__officialDie.loveRollDie.readState());
  assert.equal(settled.frontFace,27);assert.equal(settled.face,27);assert.equal(settled.spinning,false);
  await page.locator('.evening-d100-result').evaluate(e=>e.textContent='Résultat : 27');
  const refreshed=await page.evaluate(async()=>{const {updateEveningCategoryUi}=await import('/premium-evening.mjs');updateEveningCategoryUi(document.querySelector('#app'),'HASARD');
   const c=window.__officialDie,p=c.parentNode;c.remove();p.append(c);c.loveRollDie.refresh();window.dispatchEvent(new Event('resize'));return c.loveRollDie.readState();});
  assert.ok(refreshed.renderedFrames>settled.renderedFrames,'Reattached canvas was not redrawn');
  await page.waitForTimeout(1200);
  assert.deepEqual(await page.evaluate(()=>window.__officialDie.loveRollDie.readState().quaternion),settled.quaternion,'Die returned to its initial orientation');
  fs.mkdirSync('design-captures',{recursive:true});await page.screenshot({path:'design-captures/S25-editorial-official-D100.png'});
  await page.close();console.log('D100_REAL_GEOMETRY_OK 1..100, animation, final lock, reload, reduced motion');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
