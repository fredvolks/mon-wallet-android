import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import { marketingHtml,authHtml,profileHtml,wellbeingFields,matchHtml,monthDayInputs,parseMonthDay } from './onboarding.mjs';
import { EVENING_CATEGORIES,EVENING_CHALLENGES,eveningCard,availableEveningCategories,formatCountdown,customCardSlots,editableCardSlots,resolvedCard } from './evening-challenges.mjs';
import {MONTHLY_ACTIVITIES,editableMonthlyActivities,monthlyActivityForRoll} from './monthly-activities.mjs';
import { SPECIAL_SURPRISES,OCCASIONS,occasionDate,dueOccasions,specialSurprise,nthSunday,occasionRoles,TARGETED_OCCASIONS } from './special-occasions.mjs';
const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');
const sql=readFileSync(new URL('./schema.sql',import.meta.url),'utf8');
test('User names originate from the authenticated profile',()=>{
 assert.match(app,/state\.profile\.display_name/);
 assert.match(sql,/references auth\.users\(id\)/);
 assert.match(sql,/save_my_profile\(p_name text\)/);
 assert.doesNotMatch(app,/['"]Fred['"]|['"]Émilie['"]/);
});
test('Every person draws only their own D6',()=>{
 assert.match(sql,/function public\.roll_my_evening_d6/);
 assert.match(sql,/values\(p_session,auth\.uid\(\),floor\(random\(\)\*6\)::int\+1\)/);
 assert.match(sql,/primary key\(session_id,user_id\)/);
});
test('Shared weekly monthly dice outcomes are unique and atomic',()=>{
 assert.match(sql,/unique\(couple_id,kind,key\)/);
 assert.match(sql,/perform 1 from public\.couples where id=p_couple for update/);
 assert.match(sql,/when a<50 then 1 when a<75 then 2 when a<87 then 3/);
 assert.match(sql,/if p_kind='monthly_restaurant' and a not in \(1,3,7,13,18\)/);
});
test('Invitations require the intended verified email, and enforce two members',()=>{
 assert.match(sql,/email_confirmed_at is not null/);
 assert.match(sql,/verified_email<>inv\.invited_email/);
 assert.match(sql,/if n<>1 then raise exception/);
 assert.match(sql,/token_hash text not null unique/);
 assert.match(app,/randomInviteToken\(/);
});
test('One questionnaire per authenticated partner and encrypted shared journal',()=>{
 assert.match(sql,/primary key\(session_id,author_id\)/);
 assert.match(sql,/feedback_author_update/);
 assert.match(sql,/author_id=\(select auth\.uid\(\)\)/);
 assert.match(app,/crypto\.subtle\.encrypt/);
 assert.match(app,/iterations:240000/);
 assert.match(app,/if\(!state\.unlockKey\)/);
});
test('Backend explicitly rejects opening future advent days',()=>{
 assert.match(sql,/p_day>extract\(day from d\)::int/);
 assert.match(sql,/primary key\(couple_id,year,day\)/);
});
test('No cross-project configuration or privileged key in client',()=>{
 const cfg=readFileSync(new URL('./config.js',import.meta.url),'utf8');
 assert.doesNotMatch(cfg,/prwlnxbqttxfjxpehmok|kvejpsgeypxmlhqzioig/);
 assert.doesNotMatch(cfg,/service_role|sb_secret_/);
 assert.match(cfg,/supabasePublishableKey/);
});

test('WebGL dice do not determine official random results',()=>{
 const webgl=readFileSync(new URL('./dice3d.js',import.meta.url),'utf8');
 assert.match(webgl,/new THREE\.WebGLRenderer/);
 assert.match(webgl,/IcosahedronGeometry\(1\.16,0\)/);
 assert.match(webgl,/RoundedBoxGeometry/);
 assert.match(webgl,/const target=new THREE\.Quaternion/);
 assert.doesNotMatch(webgl,/Math\.random\(/);
 assert.match(app,/roll_couple_once/);
 assert.match(app,/data-dice-sides/);
});

test('A partner cannot flood the other with simultaneous challenges',()=>{
 assert.match(sql,/status in \('proposed','accepted'\)/);
 assert.match(sql,/Tous les défis de votre partenaire sont terminés/);
 assert.match(sql,/for update;\s*select user_id into target/s);
});
test('Cancelled evenings never consume a weekly game and completion needs both D6 targets',()=>{
 assert.match(sql,/counted boolean not null default false/);
 assert.match(sql,/and counted=true/);
 assert.match(sql,/finish_couple_session\(p_session uuid,p_count boolean default true\)/);
 assert.match(sql,/if done<target then raise exception/);
 assert.match(app,/p_count:true/);
 assert.match(app,/p_count:false/);
 assert.match(app,/confirm-cancel/);
});
test('Feedback can only be submitted after a completed or cancelled session',()=>{
 assert.match(sql,/couple_feedback\.couple_id and s.finished_at is not null/);
});
test('Invitation secret belongs in the URL fragment and is removed from address bar',()=>{
 assert.match(app,/url.hash='invite='\+token/);
 assert.match(app,/currentUrl.hash=fragment.toString\(\)/);
 assert.match(app,/history.replaceState/);
 assert.doesNotMatch(app,/url.searchParams.set\('invite',token\)/);
});
test('No default opinion: every rating requires an actual user choice',()=>{
 assert.doesNotMatch(app,/\(v===4\?'checked':''\)/);
 assert.match(app,/if\(!\(rating>=1&&rating<=5\)\)/);
});

test('A member waiting for their match is notified when the partner accepts',()=>{
 assert.match(app,/event:'\*',schema:'public',table:'couple_members'/);
 assert.match(app,/Votre partenaire a accepté le match/);
 assert.match(app,/if\(state.members.length<2\)\{state.channel=channel.subscribe\(\);return;\}/);
});
test('The five bottom tabs keep Settings, with shared Journal inside',()=>{
 assert.match(app,/\['settings','⚙','Réglages'\]/);
 assert.match(app,/Ouvrir le calendrier et les statistiques/);
 assert.match(app,/Nos questionnaires de soirées/);
});

test('Only the dedicated Supabase project and its publishable client key are configured',()=>{
 const cfg=readFileSync(new URL('./config.js',import.meta.url),'utf8');
 assert.match(cfg,/ejsrqfpkgwraorrznjpm\.supabase\.co/);
 assert.match(cfg,/sb_publishable_/);
 assert.doesNotMatch(cfg,/service_role|sb_secret_/);
 assert.match(cfg,/fredvolks\.github\.io\/mon-wallet-android/);
});
test('Email magic link fallback works in addition to Google and Apple login options',()=>{
 assert.match(authHtml(),/Continuer avec Google/);
 assert.match(authHtml(),/Continuer avec Apple/);
 assert.match(app,/client\.auth\.signInWithOtp/);
 assert.match(app,/emailRedirectTo:redirectTo/);
 assert.match(app,/if\(verb==='email-login'\)/);
});

test('LoveRoll special events have twenty separate fully-worded surprises',()=>{
 assert.equal(SPECIAL_SURPRISES.length,20);
 assert.equal(new Set(SPECIAL_SURPRISES.map(x=>x.title)).size,20);
 assert.ok(SPECIAL_SURPRISES.every(x=>x.title.length>6&&x.detail.length>36));
 assert.equal(OCCASIONS.length,7);
 for(let i=1;i<=20;i++)assert.equal(specialSurprise(i),SPECIAL_SURPRISES[i-1]);
 assert.throws(()=>specialSurprise(0));
 assert.throws(()=>specialSurprise(21));
});
test('2026 Canadian Mothers and Fathers days are calculated correctly',()=>{
 assert.equal(nthSunday(2026,5,2),10);
 assert.equal(nthSunday(2026,6,3),21);
 assert.equal(occasionDate('mothers_day',2026,{},null),'2026-05-10');
 assert.equal(occasionDate('fathers_day',2026,{},null),'2026-06-21');
 assert.equal(occasionDate('valentine',2026,{},null),'2026-02-14');
});
test('Occasion setting dates originate from the couple rather than hardcoded names',()=>{
 const a='11111111-1111-4111-8111-111111111111',b='22222222-2222-4222-8222-222222222222';
 const settings={anniversary_md:'10-20',birthday_by_user:{[a]:'02-29',[b]:'06-07'}};
 assert.equal(occasionDate('anniversary',2026,settings,null),'2026-10-20');
 assert.equal(occasionDate('birthday',2026,settings,a),'2026-02-28');
 assert.equal(occasionDate('birthday',2028,settings,a),'2028-02-29');
 assert.equal(occasionDate('birthday',2026,settings,b),'2026-06-07');
 assert.equal(dueOccasions('2026-10-13',settings,[{user_id:a},{user_id:b}]).find(x=>x.key==='anniversary')?.date,'2026-10-20');
 assert.equal(occasionDate('anniversary',2026,{anniversary_md:null},null),null);
});
test('Special events have server enforced unique draw and host sequencing',()=>{
 const sql=readFileSync(new URL('./special-occasions.sql',import.meta.url),'utf8');
 assert.match(sql,/primary key\(couple_id,event_year,occasion_key,roll_type\)/);
 assert.match(sql,/perform 1 from public.couples where id=p_couple for update/);
 assert.match(sql,/Brassez le D20 de la surprise avant le D6/);
 assert.match(sql,/couple_private.is_member\(p_couple\)/);
 assert.match(sql,/alter table public.couple_occasion_rolls enable row level security/);
 assert.match(app,/roll_love_occasion_once/);
 assert.match(app,/function renderOccasions\(\)/);
 assert.match(app,/set_love_occasion_date/);
});
test('Brand LoveRoll is applied to PWA',()=>{
 const manifest=readFileSync(new URL('./manifest.webmanifest',import.meta.url),'utf8');
 assert.match(manifest,/"name": "LoveRoll"/);
 assert.match(app,/LoveRoll/);
});

test('The evening shows exactly two categories and per-draw 80/20 odds',()=>{
 assert.deepEqual(EVENING_CATEGORIES,['SENSUEL','SEXUEL']);
 assert.deepEqual(availableEveningCategories('SENSUEL'),[{category:'SENSUEL',weight:80},{category:'SEXUEL',weight:20}]);
 assert.deepEqual(availableEveningCategories('SEXUEL'),[{category:'SENSUEL',weight:20},{category:'SEXUEL',weight:80}]);
 assert.match(app,/EVENING_CATEGORIES\.map/);
 assert.match(app,/80 % de chances/);
 assert.doesNotMatch(app,/Aléatoire : Sexuel 40/);
});
test('50 built-in cards and 20 optional creator-only slots',()=>{
 for(const category of EVENING_CATEGORIES){
  assert.equal(EVENING_CHALLENGES[category].length,25);
  for(let cardId=1;cardId<=25;cardId++){
   const card=eveningCard(category,cardId);
   assert.ok(card.title.length>=5);
   assert.ok(card.description.length>35);
   assert.ok(Number.isInteger(card.minutes)&&card.minutes>=0&&card.minutes<=15);
  }
  const slots=customCardSlots(category);
  assert.equal(slots.length,10);
  assert.ok(slots.every(s=>s.cardId>=26&&s.cardId<=35&&!s.filled));
 }
 assert.equal(eveningCard('SENSUEL',3).minutes,4);
 assert.equal(eveningCard('SEXUEL',5).minutes,12);
 assert.equal(eveningCard('SEXUEL',16).minutes,0);
 assert.throws(()=>eveningCard('COQUIN',1));
 assert.throws(()=>eveningCard('SEXUEL',26));
 const snapshot={category:'SEXUEL',card_id:29,face:5,card_title:'Une carte libre',
  card_description:'Une proposition personnalisée sans durée.',card_timer_minutes:null};
 assert.equal(resolvedCard(snapshot).minutes,null);
 assert.equal(resolvedCard(snapshot).cardId,29);
});
test('Countdown uses a monotonic wall deadline, pause reset and optional signals',()=>{
 assert.equal(formatCountdown(600_000),'10:00');
 assert.equal(formatCountdown(61_200),'01:02');
 assert.equal(formatCountdown(-500),'00:00');
 assert.match(app,/challengeTimer\.deadline-Date\.now\(\)/);
 assert.match(app,/timer-reset/);
 assert.match(app,/timer-toggle/);
 assert.match(app,/navigator\.vibrate/);
 assert.match(app,/AudioContext/);
 assert.match(app,/Le minuteur n’oblige jamais/);
});

test('Marketing page before OAuth or email signup',()=>{
 const h=marketingHtml();
 assert.match(h,/Votre aventure à deux/);
 assert.match(h,/CRÉER MON COMPTE GRATUITEMENT/);
 assert.match(h,/Android et iPhone/);
 assert.match(app,/root\.innerHTML=marketingHtml\(\)/);
 assert.match(app,/if\(!state\.showAuth\)/);
 assert.match(app,/if\(verb==='show-auth'\)/);
});
test('Registration obtains the name from identity and birthday in the initial form',()=>{
 const h=profileHtml({user_metadata:{full_name:'Camille Martin'}},null);
 assert.match(h,/Camille Martin/);
 assert.match(h,/signup-birth-month/);
 assert.match(h,/signup-birth-day/);
 assert.match(h,/age-confirm/);
 assert.doesNotMatch(h,/Fred|Émilie/);
 assert.match(app,/complete_love_registration/);
 assert.match(app,/p_birthday_md:birthday/);
});
test('Birthday inputs reject invalid dates, and support leap day',()=>{
 const fake=(m,d)=>({getElementById:k=>({value:k.endsWith('month')?String(m):String(d)})});
 assert.equal(parseMonthDay('signup-birth',true,fake(2,29)),'02-29');
 assert.equal(parseMonthDay('signup-birth',true,fake(10,13)),'10-13');
 assert.throws(()=>parseMonthDay('signup-birth',true,fake(2,30)));
 assert.throws(()=>parseMonthDay('signup-birth',true,fake(13,10)));
 assert.equal(parseMonthDay('signup-couple',false,fake('','')),null);
 assert.match(monthDayInputs('signup-birth','06-23'),/value="6" selected/);
});
test('New couple match uses two individual sign-ups',()=>{
 assert.match(matchHtml('Camille',false),/signup-couple-month/);
 assert.match(matchHtml('Camille',false),/Créer notre espace à deux/);
 assert.match(matchHtml('Alex',true),/Accepter ce match/);
 assert.match(app,/create_love_match/);
 assert.match(app,/accept_love_match/);
 assert.match(app,/parseMonthDay\('signup-couple',false\)/);
 assert.doesNotMatch(app,/rpc\('create_my_couple'/);
});
test('Anniversary and dates are not re-entered in Settings',()=>{
 const fragment=app.slice(app.indexOf('function renderSettings()'),app.indexOf('async function refreshAndShow()',app.indexOf('function renderSettings()')));
 assert.match(fragment,/enregistré à mon inscription/);
 assert.doesNotMatch(fragment,/loveroll-birthday|save-occasion-date:birthday/);
});
test('Database onboarding enforces profile birthday and revokes old bypasses',()=>{
 const schema=readFileSync(new URL('./registration-onboarding.sql',import.meta.url),'utf8');
 assert.match(schema,/create or replace function public\.complete_love_registration/);
 assert.match(schema,/birthday_md text/);
 assert.match(schema,/public\.create_love_match/);
 assert.match(schema,/public\.accept_love_match/);
 assert.match(schema,/revoke execute on function public\.save_my_profile/);
 assert.match(schema,/couple_private\.validate_month_day/);
});

test('Premium LoveRoll landing has exactly one create and one login CTA',()=>{
 const source=marketingHtml();
 const actions=[...source.matchAll(/data-action="show-auth"/g)];
 assert.equal(actions.length,2,'No duplicate login/sign-up buttons');
 assert.equal((source.match(/CRÉER MON COMPTE GRATUITEMENT/g)||[]).length,1);
 assert.equal((source.match(/J’AI DÉJÀ UN COMPTE/g)||[]).length,1);
 assert.ok(source.indexOf('CRÉER MON COMPTE')<source.indexOf('love-features'));
 assert.ok(source.indexOf('J’AI DÉJÀ UN COMPTE')<source.indexOf('love-features'));
 const footer=source.slice(source.indexOf('<footer'));
 assert.doesNotMatch(footer,/data-action=/);
});
test('LoveRoll landing uses real artwork, not emoji substitutes or screenshot text',()=>{
 const source=marketingHtml();
 assert.match(source,/Love<\/span><b>Roll<\/b>/);
 assert.match(source,/JEUX · DÉFIS · DATES · NOUS/);
 assert.match(source,/assets\/loveroll-hero\.webp/);
 assert.match(source,/alt="Cœur en verre rose/);
 assert.match(source,/Votre aventure à deux/);
 assert.match(source,/commence ici\./);
 assert.match(source,/Connexion sécurisée pour Android et iPhone/);
 assert.equal((source.match(/class="love-feature"/g)||[]).length,4);
 assert.equal((source.match(/class="love-benefit"/g)||[]).length,0);
 assert.match(source,/Plus qu’un jeu/);
 assert.match(source,/Complicité, surprises et frissons/);
 assert.match(source,/Chaque activité est facultative/);
 assert.doesNotMatch(source,/🎲|💌|🔒/);
});
test('Premade art and CSS are available, web cache contains the artwork',()=>{
 const image=readFileSync(new URL('./assets/loveroll-hero.webp',import.meta.url));
 assert.ok(image.byteLength>1000);
 assert.equal(image.subarray(0,4).toString(),'RIFF');
 assert.equal(image.subarray(8,12).toString(),'WEBP');
 const stylesheet=readFileSync(new URL('./styles.css',import.meta.url),'utf8');
 assert.match(stylesheet,/\.love-hero-art/);
 assert.match(stylesheet,/\.love-mini-finale/);
 assert.match(stylesheet,/grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
 const sw=readFileSync(new URL('./service-worker.js',import.meta.url),'utf8');
 assert.match(sw,/assets\/loveroll-hero\.webp/);
 assert.match(sw,/loveroll-static-v14/);
});

test('Compact v2 keeps exactly six zones and all essential content',()=>{
 const h=marketingHtml();
 for(const x of ['love-brand','love-scene','love-cta-group','love-features','love-mini-finale','love-compact-footer']){
  assert.match(h,new RegExp('class="[^"]*'+x));
 }
 assert.match(h,/Dés à deux/);
 assert.match(h,/Votre match/);
 assert.match(h,/Calendrier/);
 assert.match(h,/Espace privé/);
 assert.match(h,/Plus qu’un jeu, <em>votre histoire à deux/);
 assert.match(h,/Réservé aux adultes de 18 ans et plus/);
 assert.match(h,/Chaque activité est facultative/);
 assert.match(h,/href="loveroll:\/\/offline"/);
 assert.doesNotMatch(h,/Pourquoi vous allez aimer/);
});


test('Two per-person wellbeing questions offer non-disclosing responses',async()=>{
 const {RELATIONSHIP_OPTIONS,INTIMACY_OPTIONS,guidanceFor,guidedIdeas}=await import('./wellbeing.mjs');
 assert.equal(RELATIONSHIP_OPTIONS.length,5);
 assert.equal(INTIMACY_OPTIONS.length,6);
 assert.ok(RELATIONSHIP_OPTIONS.some(([v])=>v==='skip'));
 assert.ok(INTIMACY_OPTIONS.some(([v])=>v==='skip'));
 assert.equal(guidanceFor('difficult','fulfilled'),'gentle');
 assert.equal(guidanceFor('great','sensitive'),'gentle');
 assert.equal(guidanceFor('distant','different'),'reconnect');
 assert.equal(guidanceFor('good','fulfilled'),'quality');
 assert.equal(guidanceFor('great','fulfilled'),'playful');
 assert.equal(guidedIdeas('gentle').ideas.length,3);
 assert.match(profileHtml({user_metadata:{name:'Camille'}},null),/wellbeing-relationship/);
 assert.match(profileHtml({user_metadata:{name:'Camille'}},null),/wellbeing-intimacy/);
 assert.match(app,/save_my_wellbeing/);
 assert.match(app,/get_love_guidance/);
});
test('Removing a partner retains their own diary and removes access at the server',()=>{
 const sql=readFileSync(new URL('./partner-privacy-wellbeing.sql',import.meta.url),'utf8');
 assert.match(sql,/create table if not exists public\.love_personal_intimacy/);
 assert.match(sql,/user_id=\(select auth\.uid\(\)\)/);
 assert.match(sql,/create or replace function public\.end_love_match/);
 assert.match(sql,/delete from public\.couple_members where couple_id=p_couple/);
 assert.doesNotMatch(sql,/delete from public\.couple_intimacy/);
 assert.match(sql,/on conflict\s*\(user_id,source_event_id\) do nothing/);
 assert.match(sql,/create or replace function public\.add_my_love_event/);
 assert.match(app,/rpc\('end_love_match'/);
 assert.match(app,/rpc\('add_my_love_event'/);
 assert.match(app,/function renderPersonalJournal\(/);
 assert.match(app,/state\.personalPass=null/);
 assert.match(app,/data-action="personal-date/);
});
test('Optional partner linking, settings management and individualized tips',()=>{
 assert.doesNotMatch(matchHtml('Camille',false),/skip-couple|Explorer sans partenaire/);
 assert.match(matchHtml('Camille',false),/prepare-cards/);
 assert.match(app,/function renderWaitingPartner\(/);
 assert.doesNotMatch(app,/function renderSolo\(/);
 assert.match(app,/Retirer mon conjoint ou ma conjointe/);
 assert.match(app,/Ajouter un conjoint ou une conjointe/);
 assert.match(app,/function ideaHtml\(/);
 assert.match(app,/Chacun peut dire non/);
});

test('Embedded Android account screen uses platform Google and email code',()=>{
 const html=authHtml();
 assert.match(html,/signin-code/);
 assert.match(html,/verify-email-code/);
 assert.match(app,/LoveRollNativeAuth\?\.googleSignIn/);
 assert.match(app,/signInWithIdToken/);
 assert.match(app,/verifyOtp/);
 assert.match(app,/launchParams\.has\('auth'\)/);
 assert.match(app,/launchParams\.has\('settings'\)/);
});


test('Wellbeing selectors use dark custom dialogs, not white Android select popups',()=>{
 const html=wellbeingFields();
 const css=readFileSync(new URL('./styles.css',import.meta.url),'utf8');
 const manifest=readFileSync(new URL('../app/src/main/AndroidManifest.xml',import.meta.url),'utf8');
 const theme=readFileSync(new URL('../app/src/main/res/values/styles.xml',import.meta.url),'utf8');
 assert.equal(html.split('class="love-answer-dialog"').length-1,2);
 assert.equal(html.split('class="love-answer-trigger"').length-1,2);
 assert.equal(html.split('type="hidden" id="wellbeing-').length-1,2);
 assert.equal(html.includes('<select id="wellbeing-'),false);
 assert.ok(html.includes('role="radiogroup"'));
 assert.ok(css.includes('dialog.love-answer-dialog::backdrop'));
 assert.ok(css.includes('button.love-answer-option[aria-checked="true"]'));
 assert.ok(app.includes('bindWellbeingMenus()'));
 assert.ok(theme.includes('name="LoveRollConnectedTheme"'));
 assert.ok(theme.includes('parent="android:style/Theme.Material.NoActionBar"'));
 assert.ok(manifest.includes('android:theme="@style/LoveRollConnectedTheme"'));
});

test('Unpaired members only see invitation, personal account, calendar and private card editing',()=>{
 const match=matchHtml('Camille',false);
 assert.ok(match.includes('prepare-cards'));
 assert.ok(match.includes('create-couple'));
 assert.ok(!match.includes('Explorer sans partenaire'));
 assert.ok(!match.includes('skip-couple'));
 assert.ok(app.includes('function renderWaitingPartner(){'));
 assert.ok(app.includes('function renderCardLibrary(){'));
 assert.ok(app.includes('function renderPersonalJournal(){'));
 assert.ok(app.includes('function renderSettings(){'));
 assert.ok(app.includes('function isSharedCoupleReady('));
 assert.ok(app.includes("if(state.tab==='library'&&isLoveAdmin())renderCardLibrary()")||
           app.includes("state.tab==='library'&&isLoveAdmin()"));
 assert.ok(app.includes("if(verb==='prepare-cards')"));
 assert.ok(app.includes("state.tab='library'"));
 assert.ok(app.includes("['home','settings','library','personal-journal']"));
 for(const forbidden of ['renderSoloSession','renderSoloSpecial','renderSoloOccasions',
  'renderSoloAdvent','renderSolo()','solo-roll','solo-category','solo-advent',
  'SOLO_MESSAGE','drawSoloFace','state.solo','loveroll-skip']){
  assert.ok(!app.includes(forbidden),'Stale solo exploration code: '+forbidden);
 }
 for(const forbidden of ['Essayer le D35','Découvrir un défi D35',
  'Découverte libre en mode solo','D6 individuel · essai']){
  assert.ok(!app.includes(forbidden),'Stale solo UI: '+forbidden);
 }
 assert.ok(app.includes("Les jeux exigent deux comptes associés."));
 assert.ok(app.includes("Cette action nécessite deux comptes associés."));
 assert.ok(!readFileSync(new URL('./service-worker.js',import.meta.url),'utf8').includes('solo-mode.mjs'));
});
test('Before invitation the account retains its private cards and notes',()=>{
 assert.ok(app.includes("p_anniversary_md:null"));
 assert.ok(app.includes("if(!state.coupleId)await rpc('create_love_match'"));
 assert.ok(app.includes("isLoveAdmin()"));
 assert.ok(app.includes("state.customCards=state.couple?.owner_user_id===state.user.id"));
 assert.ok(app.includes("save_love_card_with_roles"));
 assert.ok(app.includes("button('tab:library','♡ Gérer mes 70 cartes')"));
 assert.ok(app.includes("button('invite','♡ Préparer l’invitation')"));
 assert.ok(app.includes("renderPersonalJournal().catch(fail)"));
 assert.ok(app.includes("button('tab:personal-journal','Ouvrir mon calendrier personnel')"));
 assert.ok(!app.includes("if(verb==='skip-couple')"));
});
test('Creator-only access and private draws are enforced server side',()=>{
 const sql=readFileSync(new URL('./creator-only-custom-challenges.sql',import.meta.url),'utf8');
 assert.ok(sql.includes('owner_user_id'));
 assert.ok(sql.includes('creator-only'));
 assert.ok(sql.includes('love_custom_owner_read'));
 assert.ok(sql.includes('save_love_custom_card'));
 assert.ok(sql.includes('delete_love_custom_card'));
 assert.ok(sql.includes('my_love_roll_status'));
 assert.ok(sql.includes('both_rolls_ready'));
 assert.ok(sql.includes('card_id between 26 and 35'));
 assert.ok(sql.includes('card<=25'));
 assert.ok(sql.includes('card_title,card_description,card_timer_minutes'));
 assert.ok(app.includes("function isLoveAdmin()"));
 assert.ok(app.includes("function renderCardLibrary()"));
 assert.ok(app.includes("button('tab:library'"));
 assert.ok(app.includes("Seul l’administrateur")||app.includes("Accès réservé à l’administrateur"));
 assert.ok(app.includes('state.rollStatus?.both_ready'));
 assert.ok(app.includes("p_card_id:state.customSlot"));
 assert.ok(app.includes("state.couple?.owner_user_id===state.user?.id"));
 assert.ok(app.includes("state.tab==='library')renderCardLibrary()"));
 assert.ok(app.includes("' · D35 : '+safe(active.face)"));
 assert.equal(app.includes("' · CARTE '+"),false);
 assert.ok(app.includes("REÇOIT"));
 assert.ok(app.includes("OFFRE"));
 assert.ok(app.includes('Sans minuteur'));
});


test('Fixed celebration roles and shared couple anniversary',()=>{
 const couple=[{user_id:'a',name:'A'},{user_id:'b',name:'B'}];
 const conf={celebrants:{mothers_day:'b',womens_day:'b',fathers_day:'a',mens_day:'a'}};
 for(const key of TARGETED_OCCASIONS){
  const role=occasionRoles({key},conf,couple);
  assert.equal(role.shared,false);
  assert.ok(role.receiver&&role.giver&&role.receiver.user_id!==role.giver.user_id);
 }
 assert.equal(occasionRoles({key:'mothers_day'},conf,couple).giver.user_id,'a');
 assert.equal(occasionRoles({key:'fathers_day'},conf,couple).giver.user_id,'b');
 assert.equal(occasionRoles({key:'birthday',actorId:'a'},conf,couple).giver.user_id,'b');
 assert.equal(occasionRoles({key:'anniversary'},conf,couple).shared,true);
 assert.equal(occasionRoles({key:'valentine'},conf,couple).shared,true);
 assert.equal(occasionRoles({key:'mothers_day'},null,couple).receiver,null);
 assert.equal(occasionDate('womens_day',2026,{},null),'2026-03-08');
 assert.equal(occasionDate('mens_day',2026,{},null),'2026-11-19');
 const sql=readFileSync(new URL('./celebration-roles.sql',import.meta.url),'utf8');
 assert.ok(sql.includes("set_love_celebrant"));
 assert.ok(sql.includes("owner_user_id=auth.uid()"));
 assert.ok(sql.includes("p_kind in ('mothers_day','fathers_day','womens_day','mens_day','birthday','anniversary','valentine')"));
 assert.ok(app.includes("data-action=\"set-celebrant:"));
});

test('Neither partner sees whether a drawn card was created by the admin',()=>{
 const custom=resolvedCard({category:'SEXUEL',card_id:29,face:7,
  card_title:'Surprise à deux',card_description:'Une proposition choisie par votre partenaire.',
  card_timer_minutes:null});
 const official=resolvedCard({category:'SEXUEL',card_id:5,face:7});
 assert.equal(Object.hasOwn(custom,'custom'),false);
 assert.equal(Object.hasOwn(official,'custom'),false);
 assert.equal(custom.title,'Surprise à deux');
 const missing=resolvedCard({category:'SENSUEL',card_id:28,face:4});
 assert.equal(missing.title,'Défi surprise');
 assert.equal(missing.description,'Consigne momentanément indisponible.');
 const gameScreen=app.slice(app.indexOf('function renderSession(){'),app.indexOf('function renderSpecial(){'));
 assert.ok(gameScreen.includes("' · D35 : '+safe(active.face)"));
 assert.ok(!gameScreen.includes('challenge.cardId'));
 assert.ok(!gameScreen.includes('active.card_id'));
 assert.ok(!gameScreen.includes('défis personnalisés'));
 assert.ok(!gameScreen.includes('Carte personnalisée'));
 assert.ok(!gameScreen.includes('carte personnalisée'));
 assert.ok(gameScreen.includes('OFFRE → '));
 assert.ok(gameScreen.includes('REÇOIT'));
 // The existing admin-only catalogue can retain its internal slot numbers.
 assert.ok(app.includes('function isLoveAdmin()'));
});

test('The administrator can edit all 70 cards without replacing the fixed originals',()=>{
 for(const category of EVENING_CATEGORIES){
  const original=editableCardSlots(category);
  assert.equal(original.length,35);
  assert.equal(original.filter(c=>c.isOriginal).length,25);
  assert.equal(original.filter(c=>!c.isOriginal).length,10);
  assert.equal(original.filter(c=>c.filled).length,25);
  assert.equal(original.filter(c=>c.saved).length,0);
  const edited=editableCardSlots(category,[
   {category,card_id:3,title:'Une version adaptée',description:'Des consignes pour un nouveau défi.',
    timer_minutes:null,recipient_user_id:'recipient',drawer_user_id:'drawer'},
   {category,card_id:30,title:'Une nouvelle carte',description:'Description d’une carte.',
    timer_minutes:9,recipient_user_id:null,drawer_user_id:null}
  ]);
  assert.equal(edited[2].title,'Une version adaptée');
  assert.equal(edited[2].minutes,null);
  assert.equal(edited[2].recipientId,'recipient');
  assert.equal(edited[2].drawerId,'drawer');
  assert.equal(edited[2].saved,true);
  assert.equal(edited[29].minutes,9);
  assert.equal(edited[29].filled,true);
  assert.equal(edited[30].filled,false);
  const snapshot=resolvedCard({category,card_id:3,face:19,
   card_title:'Une version adaptée',card_description:'Des consignes pour un nouveau défi.',
   card_timer_minutes:null});
  assert.equal(snapshot.title,'Une version adaptée');
  assert.equal(snapshot.minutes,null);
  assert.equal(Object.hasOwn(snapshot,'custom'),false);
 }
});
test('All card draws respect drawer eligibility and persistent no-repeat cycles',()=>{
 const sql=readFileSync(new URL('./edit-all-cards-shufflebag.sql',import.meta.url),'utf8');
 for(const fragment of [
  'add constraint love_custom_challenges_card_id_check check(card_id between 1 and 35)',
  'add column if not exists drawer_user_id uuid',
  'no_repeat_challenges boolean not null default true',
  'create table if not exists public.love_card_draw_history',
  'primary key(couple_id,recipient_user_id,category,card_id)',
  'create or replace function public.set_love_draw_rule(',
  'owner_user_id=auth.uid()',
  'select coalesce(no_repeat_challenges,true) into v_no_repeat',
  "where cc.couple_id=s.couple_id and cc.category=cats.category",
  'cc.drawer_user_id<>auth.uid()',
  'cc.recipient_user_id<>target',
  'and not exists(',
  'delete from public.love_card_draw_history',
  'and history.card_id=card',
  'if v_no_repeat then',
  'insert into public.love_card_draw_history',
  'case when chosen.card_id is not null'
 ])assert.ok(sql.includes(fragment),'Missing draw or permission rule: '+fragment);
 assert.ok(app.includes("function isLoveAdmin()"));
 assert.ok(app.includes("button('save-draw-rule'"));
 assert.ok(app.includes("id=\"no-repeat-cards\""));
 assert.ok(app.includes("rpc('set_love_draw_rule'"));
 assert.ok(app.includes("p_drawer_role:drawer"));
 assert.ok(app.includes("custom-drawer"));
 assert.ok(app.includes("custom-recipient"));
 assert.ok(app.includes("state.customSlot=slot"));
 assert.ok(app.includes("slot<1||slot>35"));
 assert.ok(app.includes("Restaurer cette carte d’origine"));
 assert.ok(!app.includes("CARTE '+safe(challenge.cardId"));
});

test('Challenge D35 gives 35 physical numbered faces without changing monthly D20s',()=>{
 const webgl=readFileSync(new URL('./dice3d.js',import.meta.url),'utf8');
 const db=readFileSync(new URL('./d35-challenge-dice.sql',import.meta.url),'utf8');
 assert.ok(webgl.includes('function d35Geometry()'));
 assert.ok(webgl.includes('new ConvexGeometry(points)'));
 assert.ok(webgl.includes('const count=35'));
 assert.ok(webgl.includes('sideNormals.set(i+1,outward)'));
 assert.ok(db.includes('check(face between 1 and 35)'));
 assert.ok(db.includes('d35:=floor(random()*35)::integer+1'));
 assert.ok(db.includes('v_cat,d35,cid'));
 assert.ok(db.includes("cc.drawer_role='owner'"));
 assert.ok(db.includes("cc.recipient_role='partner'"));
 assert.ok(db.includes('love_card_draw_history'));
 assert.ok(!app.includes("heroDice(demo.challenge,35,'D35"));
 assert.ok(app.includes('Brasser le D35 pour'));
 assert.ok(app.includes('D35 :'));
 assert.ok(app.includes("heroDice(a.face,20,'Activité officielle')"));
 assert.ok(app.includes("heroDice(prize.face,20,'Résultat officiel')"));
 assert.ok(!app.includes('solo-roll:challenge'));
 assert.ok(!app.includes('heroDice(demo.challenge,35'));
});
test('Prepare cards privately before inviting a partner with future partner roles',()=>{
 const view=matchHtml('Test',false);
 const roles=readFileSync(new URL('./preinvite-card-roles.sql',import.meta.url),'utf8');
 assert.ok(view.includes('prepare-cards'));
 assert.ok(app.includes("if(verb==='prepare-cards')"));
 assert.ok(app.includes("create_love_match',{p_anniversary_md:null}"));
 assert.ok(app.includes("state.tab='library'"));
 assert.ok(app.includes('Créer mes défis avant l’invitation'));
 assert.ok(app.includes('Ma conjointe (à inviter)'));
 assert.ok(app.includes('save_love_card_with_roles'));
 assert.ok(app.includes('p_recipient_role:recipient'));
 assert.ok(app.includes('p_drawer_role:drawer'));
 assert.ok(roles.includes("drawer_role text not null default 'both'"));
 assert.ok(roles.includes("recipient_role text not null default 'both'"));
 assert.ok(roles.includes("owner_user_id=auth.uid()"));
 assert.ok(roles.includes("p_drawer_role=p_recipient_role"));
});

test('All 50 original cards are varied, ready to play and individually editable',()=>{
 const all=new Set();
 for(const category of EVENING_CATEGORIES){
  const cards=Array.from({length:25},(_,i)=>eveningCard(category,i+1));
  assert.equal(cards.length,25);
  assert.ok(cards.filter(c=>c.minutes>0).length>=9,'Not enough playable timed challenges in '+category);
  assert.ok(cards.filter(c=>c.minutes===0).length>=9,'Open-ended surprises should never force a countdown in '+category);
  for(const [i,card] of cards.entries()){
   assert.ok(card.title.length>=8&&card.title.length<=120,'Missing card title '+category+' '+i);
   assert.ok(card.description.length>=90&&card.description.length<=900,'Incomplete instructions '+category+' '+i);
   assert.ok(Number.isInteger(card.minutes)&&card.minutes>=0&&card.minutes<=15,'Invalid timer');
   assert.ok(!/À écrire|Titre à remplir|Carte personnalisée|lorem ipsum/i.test(card.title+' '+card.description),'Placeholder '+category+' '+i);
   assert.ok(!all.has(category+'-'+card.title.toLowerCase()),'Duplicate title '+card.title);
   all.add(category+'-'+card.title.toLowerCase());
  }
  const overrides=editableCardSlots(category,[
   {category,card_id:1,title:'Ma version personnelle',description:'Une consigne modifiée qui demeure prioritaire dans cet espace.',
    timer_minutes:null,recipient_role:'partner',drawer_role:'owner'}
  ]);
  assert.equal(overrides[0].title,'Ma version personnelle');
  assert.equal(overrides[0].minutes,null,'Timer switch-off must override any factory timer');
  assert.equal(overrides[0].recipientRole,'partner');
  assert.equal(overrides[0].drawerRole,'owner');
  assert.equal(overrides[1].title,eveningCard(category,2).title);
 }
 assert.equal(all.size,50);
 assert.equal(eveningCard('SEXUEL',1).title,'Strip-tease privé');
 assert.equal(eveningCard('SEXUEL',8).title,'Le Kamasutra à deux');
 assert.equal(eveningCard('SENSUEL',4).title,'Douche à deux');
});
test('Database snapshots and server defaults match all 50 client cards without changing private edits',()=>{
 const sql=readFileSync(new URL('./base-cards-v2-snapshots.sql',import.meta.url),'utf8');
 for(const token of [
 'update public.couple_challenges ch',
 'and ch.card_title is null',
 'create table if not exists public.love_base_challenges',
 'check(timer_minutes between 0 and 15)',
 'alter table public.love_base_challenges enable row level security',
 'revoke all on public.love_base_challenges from public,anon,authenticated',
 'insert into public.love_base_challenges(category,card_id,title,description,timer_minutes)',
 'on conflict(category,card_id) do update set',
 'original public.love_base_challenges%rowtype',
 'select * into original from public.love_base_challenges',
 'coalesce(chosen.title,original.title)',
 'coalesce(chosen.description,original.description)',
 'case when chosen.card_id is not null then chosen.timer_minutes',
 'love_card_draw_history',
 "cc.recipient_role='owner'",
 "cc.drawer_role='partner'",
 'd35:=floor(random()*35)::integer+1'
 ])assert.ok(sql.includes(token),'Missing important server behaviour: '+token);
 const protectedEntries=sql.split('update public.couple_challenges ch')[1].split('create table')[0];
 const seededEntries=sql.split('insert into public.love_base_challenges(category,card_id,title,description,timer_minutes)')[1].split('on conflict(category,card_id)')[0];
 for(const category of EVENING_CATEGORIES){
  for(let id=1;id<=25;id++){
   const c=eveningCard(category,id);
   const escapedTitle=c.title.replaceAll("'","''");
   const escapedDesc=c.description.replaceAll("'","''");
   assert.ok(seededEntries.includes(escapedTitle),'Server missing title '+category+' '+id);
   assert.ok(seededEntries.includes(escapedDesc),'Server missing description '+category+' '+id);
  }
 }
 assert.ok(protectedEntries.includes('card_timer_minutes=historical.minutes'));
 assert.ok(!sql.includes('delete from public.love_custom_challenges'));
 assert.ok(!sql.includes('delete from public.love_personal_intimacy'));
});

test('All 20 monthly D20 activities are editable without changing their face identifiers',()=>{
 assert.equal(MONTHLY_ACTIVITIES.length,20);
 assert.deepEqual(MONTHLY_ACTIVITIES.map(a=>a.face),Array.from({length:20},(_,i)=>i+1));
 for(const item of MONTHLY_ACTIVITIES){
  assert.ok(item.title.length>=3&&item.title.length<=120);
  assert.ok(item.description.length>=10&&item.description.length<=900);
  assert.equal(typeof item.is_restaurant,'boolean');
 }
 const edits=[{face:1,title:'Notre sortie surprise',description:'Une sortie personnalisée et encore inconnue.',is_restaurant:false}];
 const result=editableMonthlyActivities(edits);
 assert.equal(result.length,20);
 assert.equal(result[0].title,'Notre sortie surprise');
 assert.equal(result[0].edited,true);
 assert.equal(result[0].is_restaurant,false);
 assert.equal(result[1].edited,false);
 assert.equal(result[1].title,MONTHLY_ACTIVITIES[1].title);
 assert.equal(editableMonthlyActivities([])[0].title,MONTHLY_ACTIVITIES[0].title);
 assert.deepEqual(monthlyActivityForRoll({face:1}),{
  face:1,title:MONTHLY_ACTIVITIES[0].title,
  description:MONTHLY_ACTIVITIES[0].description,is_restaurant:true
 });
 const historic={face:1,activity_title:'Ancienne activité du mois',
  activity_description:'Le souvenir de notre ancienne sortie à deux.',
  activity_is_restaurant:false};
 assert.equal(monthlyActivityForRoll(historic).title,'Ancienne activité du mois');
 assert.equal(monthlyActivityForRoll(historic).is_restaurant,false);
 assert.throws(()=>monthlyActivityForRoll({face:21}),/invalide/);
});
test('Only LoveRoll couple owner edits monthly activities, including before partner invitation',()=>{
 const sql=readFileSync(new URL('./edit-monthly-activities.sql',import.meta.url),'utf8');
 const serviceWorker=readFileSync(new URL('./service-worker.js',import.meta.url),'utf8');
 for(const clause of [
  'create table if not exists public.love_monthly_activity_overrides',
  'alter table public.love_monthly_activity_overrides enable row level security',
  'love_monthly_activity_owner_read',
  'owner_user_id=(select auth.uid())',
  'create or replace function public.save_love_monthly_activity(',
  'create or replace function public.reset_love_monthly_activity(',
  'where c.id=p_couple and c.owner_user_id=auth.uid()',
  'perform 1 from public.couples where id=p_couple for update',
  'primary key(couple_id,face)',
  'r.activity_title is null',
  'activity_title,activity_description,activity_is_restaurant',
  'coalesce(edited.title,selected.title)',
  'coalesce(edited.description,selected.description)',
  'coalesce(edited.is_restaurant,selected.is_restaurant)',
  "if p_kind='monthly_restaurant' and not coalesce(is_restaurant,false)"
 ])assert.ok(sql.includes(clause),'Missing owner-only or snapshot rule: '+clause);
 assert.ok(!sql.includes('delete from public.couple_rolls'));
 assert.ok(!sql.includes('delete from public.couple_intimacy'));
 assert.ok(app.includes("function renderMonthlyLibrary(){"));
 assert.ok(app.includes("state.tab==='monthly-library'&&isLoveAdmin()"));
 assert.ok(app.includes("['home','settings','library','personal-journal','monthly-library']"));
 assert.ok(app.includes("query('love_monthly_activity_overrides',{eq:{couple_id:state.coupleId}})"));
 assert.ok(app.includes("rpc('save_love_monthly_activity'"));
 assert.ok(app.includes("rpc('reset_love_monthly_activity'"));
 assert.ok(app.includes("button('tab:monthly-library','Modifier les 20 activités')"));
 assert.ok(app.includes("monthlyActivityForRoll(state.monthly.monthly_activity)"));
 assert.ok(app.includes("const activity=monthlyActivityForRoll(a)"));
 assert.ok(app.includes("if(activity.is_restaurant){"));
 assert.ok(serviceWorker.includes("'./monthly-activities.mjs'"));
 assert.ok(!app.includes("safe(outings[a.face-1])"));
});
