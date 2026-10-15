// Behavioral Chromium tests against the REAL LoveRoll app.js and rendered DOM.
// Synthetic Supabase only. NEVER sends email, changes invitations or uses real accounts.
const {chromium}=require('playwright');
const site='http://127.0.0.1:8765/';
const fake=String.raw`
const id='owner-test',invite='123e4567-e89b-42d3-a456-426614174003';
export function createClient(){
 const mode=new URL(location.href).searchParams.get('testmode')||'anonymous';
 const email=mode==='wrong'?'wrong@example.test':'invited@example.test';
 const identity={id:mode==='owner'?'owner-test':'recipient-test',email:mode==='owner'?'owner@example.test':email,email_confirmed_at:'2026-10-15T00:00:00Z'};
 const authenticated=mode!=='anonymous';
 const rows={
  couple_profiles:[{id:identity.id,display_name:'Compte de test',birthday_md:'04-12'}],
  love_wellbeing:[{user_id:identity.id,relationship_state:'good',intimacy_state:'fulfilled'}],
  love_personal_intimacy:[],love_push_preferences:[],
  couple_members:mode==='owner'?[{user_id:id,couple_id:'couple-test',joined_at:'2026-10-01T00:00:00Z'}]:[],
  couples:mode==='owner'?[{id:'couple-test',owner_user_id:id}]:[],
  couple_invitations:[{id:invite,couple_id:'couple-test',invited_email:'invited@example.test',
   created_at:'2026-10-14T09:00:00Z',expires_at:'2027-10-15T09:00:00Z',accepted_at:null,cancelled_at:null}]
 };
 window.__fakeCalls={resend:[],reset:[],signin:[],accept:[],createInvite:[],markSent:[]};
 let authCallback=()=>{};
 return {
  auth:{
   getUser:async()=>({data:{user:authenticated?identity:null},error:null}),
   onAuthStateChange:fn=>{authCallback=fn;return {data:{subscription:{unsubscribe(){}}}}},
   signInWithPassword:async args=>{window.__fakeCalls.signin.push(args);
    return {data:{user:null},error:{code:'email_not_confirmed',message:'Email not confirmed'}};},
   resend:async args=>{window.__fakeCalls.resend.push(args);return {data:{},error:null};},
   resetPasswordForEmail:async(email,options)=>{window.__fakeCalls.reset.push({email,options});return {data:{},error:null};},
   signInWithOAuth:async args=>{window.__fakeCalls.oauth=args;return {data:{},error:null};}
  },
  from(table){return {select(){return this},eq(){return this},order(){return this},limit(){return this},
   then(ok,fail){return Promise.resolve({data:rows[table]||[],error:null}).then(ok,fail)}};},
  rpc:async(name,args)=>{
   if(name==='accept_love_match_by_id'){
    window.__fakeCalls.accept.push(args);
    if(mode==='wrong')return {data:null,error:{code:'P0001',message:'Invitation destinée à un autre courriel'}};
    return {data:null,error:null};
   }
   if(name==='create_pair_invite'){window.__fakeCalls.createInvite.push(args);return {data:invite,error:null}}
   if(name==='mark_pair_invite_sent'){window.__fakeCalls.markSent.push(args);return {data:null,error:null}}
   return {data:name==='get_love_guidance'?'gentle':name==='love_advent_server_date'?'2026-10-15':[],error:null};
  },
  channel(){return {on(){return this},subscribe(){return this}}},
  removeChannel:async()=>{}
 };
}
`;
async function open(browser,mode,extra=''){
 const page=await browser.newPage({viewport:{width:412,height:824},serviceWorkers:'block'});
 const errors=[];
 page.on('pageerror',err=>errors.push(err.message));
 await page.route('https://esm.sh/@supabase/supabase-js@2.57.0*',
  route=>route.fulfill({status:200,contentType:'application/javascript',body:fake}));
 await page.goto(site+'?testmode='+mode+extra,{waitUntil:'domcontentloaded'});
 await page.locator('#app').waitFor();
 return {page,errors};
}
(async()=>{
 const browser=await chromium.launch({headless:true});
 // Public CTAs must land in distinct modes and survive navigation.
 {
  const {page,errors}=await open(browser,'anonymous');
  await page.locator('[data-action="show-auth:signin"]').click();
  await page.locator('[data-action="auth-signin-password"]').waitFor();
  if(!(await page.locator('[data-action="auth-reset-request"]').isVisible()))throw Error('Sign-in does not expose Forgot password');
  await page.locator('[data-action="show-landing"]').click();
  await page.locator('[data-action="show-auth:signup"]').click();
  await page.locator('[data-action="auth-signup-password"]').waitFor();
  // Unconfirmed email must present a real resend control.
  await page.locator('[data-action="auth-mode:signin"]').click();
  await page.locator('#signin-email').fill('new@example.test');
  await page.locator('#signin-password').fill('Abcdefg8');
  await page.locator('[data-action="auth-signin-password"]').click();
  await page.locator('[data-action="auth-resend-confirmation"]').waitFor();
  await page.locator('[data-action="auth-resend-confirmation"]').click();
  await page.waitForFunction(()=>window.__fakeCalls.resend.length===1);
  const calls=await page.evaluate(()=>window.__fakeCalls);
  if(calls.resend[0]?.email!=='new@example.test'||calls.resend.length!==1||
      !calls.resend[0].options.emailRedirectTo)throw Error('Unconfirmed resend lost email/redirect');
  await page.locator('[data-action="auth-mode:signin"]').click();
  await page.locator('[data-action="auth-reset-request"]').click();
  await page.locator('[data-action="auth-send-reset"]').waitFor();
  await page.locator('#signin-email').fill('new@example.test');
  await page.locator('[data-action="auth-send-reset"]').click();
  await page.waitForFunction(()=>window.__fakeCalls.reset.length===1);
  if(!(await page.locator('#auth-form-feedback').innerText()).includes('Demande de récupération acceptée'))
   throw Error('Password reset claims actual email receipt or hides response');
  if(errors.length)throw Error('Auth errors '+JSON.stringify(errors));
  console.log('BEHAVIOR_OK', 'landing modes, unconfirmed resend, reset request');
  await page.close();
 }
 // An existing valid invite must retain its UUID through reload without creating another.
 {
  const {page,errors}=await open(browser,'owner');
  await page.locator('[data-action="copy-invite"]').waitFor({timeout:20000});
  const link='https://fredvolks.github.io/mon-wallet-android/?invite_id=123e4567-e89b-42d3-a456-426614174003';
  await page.evaluate(()=>{
   Object.defineProperty(navigator,'clipboard',{configurable:true,value:{writeText:async()=>{throw Error('Clipboard disabled for test')}}});
  });
  await page.locator('[data-action="copy-invite"]').click();
  await page.locator('#invite-manual-link').waitFor();
  const first=await page.locator('#invite-manual-link').inputValue();
  if(!first.includes('123e4567-e89b-42d3-a456-426614174003'))throw Error('Missing persisted invite ID');
  const calls=await page.evaluate(()=>window.__fakeCalls);
  if(calls.createInvite.length||calls.markSent.length)throw Error('Copy mutated invite/delivery state');
  await page.reload({waitUntil:'domcontentloaded'});
  await page.locator('[data-action="copy-invite"]').waitFor();
  await page.locator('[data-action="copy-invite"]').click();
  await page.locator('#invite-manual-link').waitFor();
  const second=await page.locator('#invite-manual-link').inputValue();
  if(second!==first)throw Error('Reload changed invitation link');
  const latest=await page.evaluate(()=>window.__fakeCalls);
  if(latest.createInvite.length||latest.markSent.length)throw Error('Reload/copy reissued invitation');
  if(errors.length)throw Error('Invite JS errors '+JSON.stringify(errors));
  console.log('BEHAVIOR_OK','same invitation ID, copy failed -> selectable URL, reload retains link');
  await page.close();
 }
 // Server-side destination protection: wrong verified email rejected.
 {
  const {page,errors}=await open(browser,'wrong','&invite_id=123e4567-e89b-42d3-a456-426614174003');
  await page.locator('[data-action="invite-start"]').waitFor();
  await page.locator('[data-action="invite-start"]').click();
  await page.locator('.invite-error-card').waitFor({timeout:20000});
  const calls=await page.evaluate(()=>window.__fakeCalls);
  if(calls.accept.length!==1)throw Error('Wrong recipient did not reach server guard');
  if(errors.length)throw Error('Wrong recipient JS errors '+JSON.stringify(errors));
  console.log('BEHAVIOR_OK','wrong recipient refused without changing DB');
  await page.close();
 }
 await browser.close();
})().catch(e=>{console.error('AUTH_BEHAVIOR_FAILURE',e);process.exitCode=1});
