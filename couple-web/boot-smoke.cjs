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
 const rows={
  couple_profiles:mode==='paired'?
   [{id:user.id,display_name:'Test 1',birthday_md:'04-12'},{id:second.id,display_name:'Test 2',birthday_md:'04-13'}]:
   mode==='existing'?[{id:user.id,display_name:'Test 1',birthday_md:'04-12'}]:[],
  love_wellbeing:mode==='paired'||mode==='existing'?[{user_id:user.id,relationship:'well',intimacy:'well'}]:[],
  couple_members:mode==='paired'?[{couple_id:'couple-test',user_id:user.id,joined_at:'2026-10-01T00:00:00Z'},
    {couple_id:'couple-test',user_id:second.id,joined_at:'2026-10-02T00:00:00Z'}]:[],
  couples:mode==='paired'?[{id:'couple-test',owner_user_id:user.id,journal_salt:'test-journal-salt'}]:[]
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
   name==='my_love_evening_card_totals'?[{sensual:25,sexual:25,total:50}]:
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
  ['existing-paired','?boot_case=paired','.loveroll-real-home']
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
  if(text.includes('Ouverture de votre espace privé')||errors.length)
   throw Error(name+' failed: '+JSON.stringify({text:text.slice(0,250),errors}));
  if(name==='anonymous'){
   await page.locator('.discover-login').click();
   await page.locator('[data-action="google"]').first().click();
   const args=await page.evaluate(()=>window.__loveOauthArgs);
   if(args?.provider!=='google'||args.options?.queryParams?.prompt!=='select_account')
    throw Error('Google OAuth selector missing '+JSON.stringify(args));
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
