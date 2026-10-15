// The real app.js as an ESM module, real HTML/CSS/JS assets, Chromium parser
// and safe synthetic Supabase responses. No live users, quotas or DB writes.
const {chromium}=require('playwright');
const site=process.env.LOVEROLL_TEST_URL||'http://127.0.0.1:8765/';
const fakeSupabase=`
const user={id:'test-user-1',email:'example1@test.invalid',email_confirmed_at:'2026-10-12T14:00:00Z'};
const second={id:'test-user-2',email:'example2@test.invalid',email_confirmed_at:'2026-10-12T14:00:00Z'};
export function createClient(){
 const mode=new URL(location.href).searchParams.get('boot_case')||'anonymous';
 window.__loveOauthArgs=null;
 const authenticated=mode!=='anonymous';
 const paired=mode==='paired'||mode==='paired-active';
 const active=mode==='paired-active';
 const localDay=new Intl.DateTimeFormat('en-CA',{timeZone:'America/Toronto',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
 const monday=new Date(localDay+'T12:00:00Z');
 monday.setUTCDate(monday.getUTCDate()-((monday.getUTCDay()+6)%7));
 const weeklyKey=monday.toISOString().slice(0,10);
 const now=new Date();
 const earlier=new Date(now.getTime()-2*60000).toISOString();
 const later=new Date(now.getTime()-60000).toISOString();
 const rows={
  couple_profiles:paired?
   [{id:user.id,display_name:'Test 1',birthday_md:'04-12'},{id:second.id,display_name:'Test 2',birthday_md:'04-13'}]:
   mode==='existing'?[{id:user.id,display_name:'Test 1',birthday_md:'04-12'}]:[],
  love_wellbeing:paired||mode==='existing'?[{user_id:user.id,relationship:'well',intimacy:'well'}]:[],
  couple_members:paired?[{couple_id:'couple-test',user_id:user.id,joined_at:'2026-10-01T00:00:00Z'},
    {couple_id:'couple-test',user_id:second.id,joined_at:'2026-10-02T00:00:00Z'}]:[],
  couples:paired?[{id:'couple-test',owner_user_id:user.id,journal_salt:'test-journal-salt'}]:[],
  couple_rolls:active?[{id:'weekly-synthetic',couple_id:'couple-test',kind:'weekly',key:weeklyKey,face:2,created_at:earlier}]:[],
  couple_sessions:active?[{id:'active-session-synthetic',couple_id:'couple-test',started_at:later,finished_at:null,paired_turns:true,self_draws:true}]:[],
  couple_personal_rolls:active?[{id:'d6-user',session_id:'active-session-synthetic',user_id:user.id,face:2},
   {id:'d6-second',session_id:'active-session-synthetic',user_id:second.id,face:3}]:[]
 };
 return {
  auth:{
   getUser:async()=>({data:{user:authenticated?user:null},error:null}),
   onAuthStateChange:()=>({data:{subscription:{unsubscribe(){}}}}),
   signInWithOAuth:async args=>{window.__loveOauthArgs=args;return {data:{url:'https://accounts.google.com/o/oauth2/auth'},error:null};}
  },
  from(table){
   const query={select(){return this},eq(){return this},order(){return this},limit(){return this},
    then(ok,fail){return Promise.resolve({data:rows[table]||[],error:null}).then(ok,fail)}};
   return query;
  },
  rpc:async(name)=>({data:name==='get_love_guidance'?'gentle':
   name==='love_advent_server_date'?'2026-10-15':
   name==='my_love_evening_card_totals'?[{sensual:35,sexual:35,total:70}]:
   name==='my_love_roll_status'&&active?[{my_rolled:true,partner_rolled:true,both_ready:true}]:
   name==='my_love_evening_board'&&active?{
    ready:true,first:user.id,next:user.id,
    targets:{[user.id]:2,[second.id]:3},remaining:{[user.id]:2,[second.id]:3},
    drawn:{[user.id]:0,[second.id]:0},
    completed:{[user.id]:0,[second.id]:0},
    drawsFinished:false,finished:false
   }:
   [],error:null}),
  channel(){return {on(){return this},subscribe(){return this}}},
  removeChannel:async()=>{}
 };
}
`;
(async()=>{
 const browser=await chromium.launch({headless:true});
 for(const [name,query,expected] of [
  ['anonymous','?boot_case=anonymous','.love-discover'],
  ['google-return','?boot_case=existing&code=simulated-oauth-code','.auth-panel'],
  ['existing-paired','?boot_case=paired','.loveroll-real-home'],
  ['active-evening','?boot_case=paired-active','.loveroll-real-home']
 ]){
  const page=await browser.newPage({viewport:{width:412,height:824},serviceWorkers:'block'});
  const errors=[];
  page.on('pageerror',err=>errors.push(err.message));
  page.on('console',msg=>{if(msg.type()==='error')errors.push('console: '+msg.text().slice(0,250));});
  await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',
    route=>route.fulfill({status:200,contentType:'application/javascript',body:fakeSupabase}));
  await page.goto(site+query,{waitUntil:'domcontentloaded'});
  await page.locator(expected).waitFor({timeout:20000});
  const text=await page.locator('#app').innerText();
  if(await page.locator('[data-lr-boot]').count()||await page.locator('.lr-splash--overlay').count()||errors.length)
   throw Error(name+' failed: '+JSON.stringify({text:text.slice(0,250),errors}));
  if(name==='anonymous'){
   await page.locator('.discover-login').click();
   await page.locator('[data-action="google"]').first().click();
   const args=await page.evaluate(()=>window.__loveOauthArgs);
   if(args?.provider!=='google'||args.options?.queryParams?.prompt!=='select_account')
    throw Error('Google OAuth selector missing '+JSON.stringify(args));
  }
  if(name==='existing-paired'){
   // The WebGL canvas is centered horizontally, but its visible cube sits
   // lower within it. Verify that the platform is optically lifted on phone.
   const center=await page.evaluate(()=>{
    const t=document.querySelector('.home-die-theater')?.getBoundingClientRect();
    const p=document.querySelector('.home-die-platform')?.getBoundingClientRect();
    return t&&p?{x:(p.left+p.right)/2-(t.left+t.right)/2,
      platformY:(p.top+p.bottom)/2, theaterY:(t.top+t.bottom)/2,
      translate:getComputedStyle(document.querySelector('.home-die-platform')).transform}:null;
   });
   if(!center||Math.abs(center.x)>3||center.platformY>=center.theaterY-8||
      center.translate==='none')throw Error('HOME_DIE_NOT_CENTERED '+JSON.stringify(center));
   console.log('HOME_DIE_CENTERED_OK',JSON.stringify(center));
   // Regression: tap Soirée after weekly reset. This used to throw
   // Cannot read properties of null (reading 'items') on Samsung.
   await page.locator('.nav [data-action="tab:session"]').click();
   await page.locator('.evening-locked-view').waitFor({timeout:10000});
   const copy=await page.locator('.evening-locked-view').innerText();
   if(!copy.includes('Brassez')||errors.length)
    throw Error('S25 Soirée access after reset broke '+JSON.stringify({copy:copy.slice(0,200),errors}));
   if((await page.locator('.toast.visible').allInnerTexts()).some(t=>/reading ['"]items['"]/.test(t)))
    throw Error('S25 user still sees null items error');
   await page.locator('[data-action="tab:home"]').last().click();
   await page.locator('.real-home-shell').waitFor({timeout:10000});
   console.log('SOIREE_LOCKED_ACCESS_OK','Samsung viewport: weekly reset, no feedback items, no technical error');
  }
  if(name==='active-evening'){
   // This is the essential regression: last tests stopped at locked Soirée
   // and never executed renderSession's real 70-card active branch.
   await page.locator('.nav [data-action="tab:session"]').click();
   await page.locator('.evening-premium-view').waitFor({timeout:10000});
   const evening=await page.locator('.evening-premium-view').innerText();
   if(!evening.includes('Nos défis disponibles')||!evening.includes('Voir mes défis')||errors.length)
    throw Error('S25 ACTIVE SOIRÉE failed '+JSON.stringify({evening:evening.slice(0,350),errors}));
   const warnings=await page.locator('.toast.visible').allInnerTexts();
   if(warnings.some(t=>/pending is not defined|not defined|impossible/i.test(t)))
    throw Error('S25 active Soirée still shows JS exception '+JSON.stringify(warnings));
   await page.locator('.nav [data-action="tab:special"]').click();
   await page.locator('.surprise-premium-view').waitFor({timeout:10000});
   await page.locator('.nav [data-action="tab:session"]').click();
   await page.locator('.evening-premium-view').waitFor({timeout:10000});
   if(errors.length)throw Error('Returning from Surprise to Soirée caused exception '+JSON.stringify(errors));
   console.log('SOIREE_ACTIVE_ACCESS_OK','two confirmed dice, shared live board, queue button, tab revisit');
  }
  console.log('BOOT_OK',name,text.slice(0,180).replace(/\\s+/g,' '));
  await page.close();
 }
 // Real production navigation in a fake-data browser: no Supabase writes.
 // Responsive smoke for the five connected tabs and auth entry points.
 for(const v of [
  {name:'iPad portrait',width:820,height:1180},
  {name:'iPad landscape',width:1180,height:820},
  {name:'PC laptop',width:1366,height:768},
  {name:'PC widescreen',width:1920,height:1080}
 ]){
  const page=await browser.newPage({viewport:{width:v.width,height:v.height},serviceWorkers:'block'});
  const errors=[];page.on('pageerror',err=>errors.push(err.message));
  await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',
   route=>route.fulfill({status:200,contentType:'application/javascript',body:fakeSupabase}));
  await page.goto(site+'?boot_case=paired',{waitUntil:'domcontentloaded'});
  await page.locator('.loveroll-real-home .real-home-shell').waitFor({timeout:20000});
  const inspect=async(tab,selector)=>{
   await page.locator('.nav [data-action="tab:'+tab+'"]').click();
   await page.locator(selector).waitFor({timeout:12000});
   const result=await page.evaluate(sel=>{
    const main=document.querySelector('#app'),nav=document.querySelector('.nav');
    const node=document.querySelector(sel),n=nav.getBoundingClientRect(),el=node.getBoundingClientRect();
    return {page:document.documentElement.scrollWidth,screen:innerWidth,navTop:n.top,
     tabs:nav.querySelectorAll('[data-action^="tab:"]').length,
     visible:el.width>200&&el.height>60,
     navigation:!!nav.querySelector('.active[data-action="tab:'+sel.replace(/^\\./,'')+'"]')};
   },selector);
   if(result.page>result.screen+2||result.navTop>2||result.tabs!==5||!result.visible)
    throw Error('Adaptive tab error '+v.name+' '+tab+' '+JSON.stringify(result));
   return result;
  };
  const initial=await page.evaluate(()=>{
   const box=document.querySelector('.real-home-shell').getBoundingClientRect();
   return {width:box.width,screen:innerWidth,overflow:document.documentElement.scrollWidth>innerWidth+2};
  });
  if(initial.overflow||initial.width<Math.min(v.width*.75,1000))
   throw Error('Home not responsive '+v.name+' '+JSON.stringify(initial));
  await inspect('session','.evening-locked-view');
  await inspect('special','.surprise-premium-view');
  const cards=await page.locator('.surprise-event-card').count();
  if(cards!==6)throw Error('Surprise events missing '+v.name+' '+cards);
  await inspect('advent','.advent-premium-view');
  if(await page.locator('.advent-box').count()!==25)throw Error('Advent tiles missing '+v.name);
  await inspect('settings','.settings-grid');
  await page.locator('details.settings-group summary').first().click();
  if(errors.length)throw Error('Browser console errors '+v.name+' '+JSON.stringify(errors));
  console.log('RESPONSIVE_NAV_OK',v.name,JSON.stringify({homeWidth:initial.width,events:cards,tabs:5}));
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error('BOOT_FAILURE',e);process.exitCode=1});
