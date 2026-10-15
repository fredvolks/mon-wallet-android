const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});
 for(const [name,width,height] of [['S25-Ultra',412,915],['Compact-Android',360,700]]){
  const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8765/__premium-dialogs-test.html',{waitUntil:'load'});
  await page.click('#gift');
  const d=page.getByRole('alertdialog');
  await d.waitFor();
  if(!await d.getByText('Offrir 1 défi ?').isVisible())throw Error('Gift dialog missing');
  const dims=await d.evaluate(el=>{
   const r=el.getBoundingClientRect();
   return {left:r.left,right:r.right,top:r.top,bottom:r.bottom,viewportWidth:innerWidth,viewportHeight:innerHeight};
  });
  if(dims.left<0||dims.right>width+1||dims.top<0||dims.bottom>height+1)throw Error('Dialog clipped '+name+' '+JSON.stringify(dims));
  await page.screenshot({path:'landing-captures/'+name+'-CONFIRMATION-CADEAU-v52.png'});
  await d.getByRole('button',{name:'Annuler'}).click();
  if(await page.locator('#test-result').innerText()!=='cadeau annulé')throw Error('Cancel should not gift');
  await page.click('#couple');
  await page.getByRole('alertdialog').getByText('Dissocier notre couple ?').waitFor();
  await page.keyboard.press('Escape');
  if(await page.locator('#test-result').innerText()!=='dissociation annulée')throw Error('Escape should cancel unlink');
  await page.click('#result');
  const toast=page.locator('#toast.visible');
  await toast.waitFor();
  const placement=await toast.evaluate(el=>{
   const r=el.getBoundingClientRect();
   return {top:r.top,bottom:r.bottom,right:r.right,width:r.width,height:r.height,innerWidth,innerHeight};
  });
  if(placement.top<49||placement.right>width+1||placement.bottom>height+1)
   throw Error('Toast outside safe area: '+JSON.stringify(placement));
  await page.screenshot({path:'landing-captures/'+name+'-NOTIFICATION-RESULTAT-v52.png'});
  if(errors.length)throw Error(name+' JS errors '+errors.join(','));
  console.log(name+' dialog and toast OK',JSON.stringify({dims,placement}));
  await page.close();
 }
 await browser.close();
})().catch(error=>{console.error(error);process.exit(1);});
