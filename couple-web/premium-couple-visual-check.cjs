const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'
 ]});
 for(const device of [
   {name:'S25-compact',width:412,height:824},
   {name:'S25-long',width:412,height:915}
 ])for(const pageName of ['evening','evening-idle','surprise']){
  const page=await browser.newPage({viewport:{width:device.width,height:device.height},
   isMobile:true,hasTouch:true,deviceScaleFactor:2});
  const errors=[];page.on('pageerror',err=>errors.push(err.message));
  const url='http://127.0.0.1:8765/__premium-couple-pages-test.html?page='+pageName;
  await page.goto(url,{waitUntil:'load'});
  await page.locator(pageName.startsWith('evening')?'.evening-premium-view':'.surprise-premium-view').waitFor();
  await page.waitForFunction(()=>[...document.querySelectorAll('canvas[data-dice-sides]')].every(x=>x.dataset.webglReady==='true'),null,{timeout:20000});
  await page.waitForTimeout(450);
  const result=await page.evaluate(()=>{
   const main=document.querySelector('main'),nav=document.querySelector('.nav').getBoundingClientRect();
   const panel=main.querySelector('.evening-premium-view,.surprise-premium-view');
   const last=panel.querySelector('.evening-gift,.surprise-special').getBoundingClientRect();
   const top=panel.querySelector('.evening-topline,.surprise-header').getBoundingClientRect();
   const canvases=[...panel.querySelectorAll('canvas[data-dice-sides]')];
   const obj={width:innerWidth,height:innerHeight,scrollHeight:document.documentElement.scrollHeight,
    mainHeight:main.scrollHeight,topY:top.top,navTop:nav.top,lastBottom:last.bottom,
    clipped: last.bottom>nav.top+2||top.top<22,
    horizontal:document.documentElement.scrollWidth>innerWidth+2,
    noScroll:document.documentElement.scrollHeight<=innerHeight+2 && main.scrollHeight<=innerHeight+2,
    dice:canvases.map(x=>({face:x.dataset.face,sides:x.dataset.diceSides,
      webgl:x.dataset.webglReady,height:Math.round(x.getBoundingClientRect().height)})),
    neutralDice:[...panel.querySelectorAll('canvas[data-dice-unrolled="true"]')].map(el=>({
      visible:getComputedStyle(el).visibility==='visible',
      hasWebGL:el.dataset.webglReady==='true',
      markers:el.dataset.diceMarkers,
      top:el.getBoundingClientRect().top,
      buttonTop:el.closest('.evening-user-die')?.querySelector('.evening-small-roll,.evening-wait')?.getBoundingClientRect().top
    })),
    menu:document.querySelectorAll('.nav button').length,
    eventCount:panel.querySelectorAll('.surprise-event-card').length,
    eventVisible:([...panel.querySelectorAll('.surprise-event-card')].every(el=>{const r=el.getBoundingClientRect();return r.width>70&&r.height>34&&r.bottom<=nav.top+2})),
    eventColumns:panel.querySelector('.surprise-special-grid')?getComputedStyle(panel.querySelector('.surprise-special-grid')).gridTemplateColumns.split(' ').length:0,
    sections:panel.querySelectorAll('section').length
   };
   return obj;
  });
  console.log(pageName+' '+device.name,JSON.stringify({result,errors}));
  if(errors.length||result.clipped||result.horizontal||!result.noScroll||
     result.menu!==5||result.dice.some(d=>!d.webgl||d.height<45)||
     (pageName.startsWith('evening')&&result.dice.length!==3)||
     (pageName==='evening-idle'&&(result.neutralDice.length!==2||
      result.neutralDice.some(d=>!d.visible||!d.hasWebGL||d.markers!=='hearts'||d.top>=d.buttonTop)))||
     (pageName==='surprise'&&(result.dice.length!==2||result.eventCount!==6||!result.eventVisible||result.eventColumns!==2)))throw Error('Rendu S25 incorrect : '+pageName+' '+device.name);
  await page.screenshot({path:'landing-captures/'+device.name+'-'+pageName+'-vrai-couple.png'});
  await page.close();
 }

 // Production styles on small Android, iPhone and large text. These are
 // browser emulations, not hardware/Safari tests.
 for(const device of [
  {name:'Android-360x700',width:360,height:700},
  {name:'iPhone-393x852',width:393,height:852},
  {name:'S25-enlarged-text',width:412,height:824,fontSize:'125%'}
 ])for(const pageName of ['evening-idle','surprise']){
  const page=await browser.newPage({viewport:{width:device.width,height:device.height},
   isMobile:true,hasTouch:true,deviceScaleFactor:2});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.goto('http://127.0.0.1:8765/__premium-couple-pages-test.html?page='+pageName,{waitUntil:'load'});
  if(device.fontSize)await page.addStyleTag({content:'body{font-size:'+device.fontSize+'!important}'});
  await page.waitForFunction(()=>[...document.querySelectorAll('canvas[data-dice-sides]')].every(
   el=>el.dataset.webglReady==='true'),null,{timeout:20000});
  const target=page.locator(pageName.startsWith('evening')?'.evening-small-roll':'.surprise-primary').first();
  await target.scrollIntoViewIfNeeded();
  const metrics=await page.evaluate(()=>{
   const target=document.querySelector('.evening-small-roll,.surprise-primary');
   const rect=target.getBoundingClientRect();
   return {width:innerWidth,scrollWidth:document.documentElement.scrollWidth,
    targetHeight:Math.round(rect.height),targetTop:Math.round(rect.top),
    targetBottom:Math.round(rect.bottom),navTop:Math.round(document.querySelector('.nav').getBoundingClientRect().top),
    navCount:document.querySelectorAll('.nav button').length};
  });
  console.log('Responsive '+device.name+' '+pageName,JSON.stringify({metrics,errors}));
  if(errors.length||metrics.scrollWidth>metrics.width+2||metrics.navCount!==5||
     metrics.targetHeight<44||metrics.targetBottom>metrics.navTop+2){
    throw Error('Commande inaccessible sur petit écran : '+device.name+' '+pageName);
  }
  await page.screenshot({path:'landing-captures/'+device.name+'-'+pageName+'-responsive.png',fullPage:true});
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
