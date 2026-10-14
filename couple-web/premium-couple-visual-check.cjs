const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'
 ]});
 for(const device of [
   {name:'S25-compact',width:412,height:824},
   {name:'S25-long',width:412,height:915}
 ])for(const pageName of ['evening','surprise']){
  const page=await browser.newPage({viewport:{width:device.width,height:device.height},
   isMobile:true,hasTouch:true,deviceScaleFactor:2});
  const errors=[];page.on('pageerror',err=>errors.push(err.message));
  const url='http://127.0.0.1:8765/__premium-couple-pages-test.html?page='+pageName;
  await page.goto(url,{waitUntil:'load'});
  await page.locator(pageName==='evening'?'.evening-premium-view':'.surprise-premium-view').waitFor();
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
    menu:document.querySelectorAll('.nav button').length,
    sections:panel.querySelectorAll('section').length
   };
   return obj;
  });
  console.log(pageName+' '+device.name,JSON.stringify({result,errors}));
  if(errors.length||result.clipped||result.horizontal||!result.noScroll||
     result.menu!==5||result.dice.some(d=>!d.webgl||d.height<45)||
     (pageName==='evening'&&result.dice.length!==3)||
     (pageName==='surprise'&&result.dice.length!==2))throw Error('Rendu S25 incorrect : '+pageName+' '+device.name);
  await page.screenshot({path:'landing-captures/'+device.name+'-'+pageName+'-vrai-couple.png'});
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
