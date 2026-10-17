// Measure the actual content and each rendered text line, not the full-width
// label rectangle. The latter passed while the seven Home groups looked uneven.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
const {createWorld,setupEditorial}=require('./editorial-fixture.cjs');
const sizes=[['small',360,640],['Android',360,740],['S25',412,824],['S25-tall',412,915],['iPhone',393,852],['iPad',820,1180],['PC',1366,768]];
function homeWorld(kind){
 const world=createWorld(),now=new Date(),date=now.toISOString();
 const toronto=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Toronto',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);
 const monday=new Date(toronto+'T12:00:00Z');monday.setUTCDate(monday.getUTCDate()-(monday.getUTCDay()+6)%7);
 world.profiles[0].display_name=kind==='long-names'?'Jean-Christophe Martin':'Fred';
 world.profiles[1].display_name=kind==='long-names'?'Jean-Christophe Tremblay':'Fred Messely';
 world.rolls=[{id:'week',couple_id:'c',kind:'weekly',key:monday.toISOString().slice(0,10),face:2,created_at:date},
  {id:'month',couple_id:'c',kind:'monthly_activity',key:toronto.slice(0,7),face:19,activity_title:'Activité officielle de test',activity_description:'Description conservée.'}];
 if(kind==='unrolled')world.rolls=world.rolls.filter(r=>r.kind!=='weekly');
 if(kind!=='empty'&&kind!=='unrolled'){
  world.eveningSessions=[{id:'finished',couple_id:'c',started_at:date,finished_at:date,counted:true}];
  world.challenges=[{id:'done',couple_id:'c',session_id:'finished',recipient:'a',category:'SEXUEL',status:'completed',created_at:date}];
  world.reviews=['a','b'].map(author_id=>({session_id:'finished',author_id}));
 }
 if(kind==='assigned'||kind==='long-names'){
  world.eveningSessions.push({id:'active',couple_id:'c',week_key:monday.toISOString().slice(0,10),started_at:date,finished_at:null,paired_turns:true,self_draws:true,opened_by:'a'});
  world.personalRolls=[{session_id:'active',user_id:'a',face:4},{session_id:'active',user_id:'b',face:6}];
  world.board={ready:true,targets:{a:4,b:6},remaining:{a:3,b:6},completed:{a:1,b:0}};
 }
 return world;
}
async function measure(page){
 return page.evaluate(()=>{
  const round=n=>Math.round(n*100)/100;
  const rect=n=>n.getBoundingClientRect();
  const union=nodes=>{const a=nodes.map(rect);return {left:Math.min(...a.map(r=>r.left)),right:Math.max(...a.map(r=>r.right)),top:Math.min(...a.map(r=>r.top)),bottom:Math.max(...a.map(r=>r.bottom))};};
  const cards=[...document.querySelectorAll('.home-challenge-stats>div,.home-stat-card')].map((card,index)=>{
   const ownBox=rect(card),summary=index<3,parentBox=rect(card.parentElement);
   const box=summary?{left:ownBox.left,right:ownBox.right,top:parentBox.top,bottom:parentBox.bottom,height:parentBox.height}:ownBox;
   const leaves=[...card.querySelectorAll(summary?'span,strong,small':'i,strong,span,small')];
   const content=union(leaves);
   const lines=leaves.filter(n=>n.matches('small')||!summary&&n.matches('strong,span')).flatMap(n=>{
    const range=document.createRange();range.selectNodeContents(n);
    return [...range.getClientRects()].filter(r=>r.width>1).map(r=>({text:n.textContent,dx:round((r.left+r.right-box.left-box.right)/2),inside:r.left>=box.left-1&&r.right<=box.right+1&&r.top>=box.top-1&&r.bottom<=box.bottom+1}));
   });
   const value=summary?union([...card.querySelectorAll('span,strong')]):null;
   return {index,text:card.textContent.trim(),dx:round((content.left+content.right-box.left-box.right)/2),dy:round((content.top+content.bottom-box.top-box.bottom)/2),valueDx:value?round((value.left+value.right-box.left-box.right)/2):null,
    top:round(content.top-box.top),bottom:round(box.bottom-content.bottom),left:round(content.left-box.left),right:round(box.right-content.right),height:round(box.height),lines};
  });
  const nav=rect(document.querySelector('.nav')),last=rect(document.querySelector('.home-stats-grid'));
  const summary=rect(document.querySelector('.home-challenge-stats')),monthly=rect(document.querySelector('.home-next-month'));
  return {cards,navTop:nav.top,lastBottom:last.bottom,height:innerHeight,scroll:document.documentElement.scrollHeight-innerHeight,horizontal:document.documentElement.scrollWidth-innerWidth,
   gaps:[round(monthly.top-summary.bottom),round(last.top-monthly.bottom)],values:[...document.querySelectorAll('.home-stat-card strong')].map(n=>n.textContent)};
 });
}
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const failures=[],measurements=[];fs.mkdirSync('design-captures',{recursive:true});
 try{
  for(const [name,width,height] of sizes)for(const kind of ['mixed','empty','unrolled','assigned',...(height>=824||width>=900?['long-names']:[])]){
   const {page,context,errors}=await setupEditorial(browser,homeWorld(kind),{width,height,query:'open=home'});
   try{
    await page.waitForFunction(()=>document.querySelector('.home-die-platform canvas')?.loveRollDie?.projectedBounds);
    await page.evaluate(()=>document.fonts.ready);const result=await measure(page);measurements.push({name,kind,...result});
    // Keep captures even when a measurement fails, for a visual comparison.
    await page.screenshot({path:'design-captures/home124-centering-'+name+'-'+kind+'.png'});
    assert.equal(result.cards.length,7);
    assert.deepEqual(result.values,['empty','unrolled'].includes(kind)?['0','0 / 0','—','0']:['1','1 / 0','0 / 100 %','1']);
    if(kind==='assigned'||kind==='long-names')assert.deepEqual(await page.locator('.home-challenge-stats strong').allTextContents(),['10','4','6']);
    for(const card of result.cards){
     assert.ok(Math.abs(card.dx)<=1&&Math.abs(card.dy)<=1,'Content group is not centered: '+JSON.stringify(card));
     if(card.valueDx!==null)assert.ok(Math.abs(card.valueDx)<=1,'Icon and value row is off-center: '+JSON.stringify(card));
     for(const line of card.lines)assert.ok(Math.abs(line.dx)<=1.5&&line.inside,'Text line is not centered or does not fit: '+JSON.stringify(line));
     assert.ok(card.top>=5&&card.bottom>=5&&card.left>=1&&card.right>=1,'Uneven or missing content margins: '+JSON.stringify(card));
    }
    assert.ok(result.lastBottom<=result.navTop-3,'Cards overlap the navigation');
    assert.ok(result.scroll<=2&&result.horizontal<=1,'Home does not fit: '+JSON.stringify(result));
    if(width<768)assert.ok(Math.abs(result.gaps[0]-result.gaps[1])<=1&&result.gaps[0]>=6,'Bottom section gaps differ');
    assert.deepEqual(errors,[]);console.log('HOME_CARDS_CENTERED',name,kind,JSON.stringify({
     groupError:Math.max(...result.cards.flatMap(c=>[Math.abs(c.dx),Math.abs(c.dy),Math.abs(c.valueDx||0)])),
     lineError:Math.max(...result.cards.flatMap(c=>c.lines.map(l=>Math.abs(l.dx)))),
     margins:result.cards.map(c=>[c.top,c.bottom]),values:result.values,scroll:result.scroll,navigationGap:result.navTop-result.lastBottom
    }));
   }catch(error){failures.push({name,kind,error:error.message});}
   finally{await context.close();}
  }
 }finally{await browser.close();fs.writeFileSync('design-captures/home124-centering-measurements.json',JSON.stringify(measurements,null,2));}
 assert.deepEqual(failures,[]);
})().catch(error=>{console.error(error);process.exit(1)});
