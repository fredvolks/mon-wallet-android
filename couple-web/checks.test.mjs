import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import { marketingHtml,authHtml,profileHtml,matchHtml,monthDayInputs,parseMonthDay } from './onboarding.mjs';
import { EVENING_CATEGORIES,EVENING_CHALLENGES,eveningCard,availableEveningCategories,formatCountdown } from './evening-challenges.mjs';
import { SPECIAL_SURPRISES,OCCASIONS,occasionDate,dueOccasions,specialSurprise,nthSunday } from './special-occasions.mjs';
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
 assert.match(app,/event:'INSERT',schema:'public',table:'couple_members'/);
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
 assert.equal(OCCASIONS.length,5);
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
test('All forty live evening cards have a positive minute duration',()=>{
 for(const category of EVENING_CATEGORIES){
  assert.equal(EVENING_CHALLENGES[category].length,20);
  for(let face=1;face<=20;face++){
   const card=eveningCard(category,face);
   assert.ok(card.title.length>=5);
   assert.ok(card.description.length>25);
   assert.ok(card.minutes>=1&&card.minutes<=15);
  }
 }
 assert.equal(eveningCard('SENSUEL',3).minutes,10);
 assert.equal(eveningCard('SEXUEL',5).minutes,10);
 assert.throws(()=>eveningCard('COQUIN',1));
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
 assert.match(h,/Un dé. Deux cœurs/);
 assert.match(h,/Créer mon compte gratuitement/);
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
 assert.match(source,/alt="Grand cœur en verre rose/);
 assert.match(source,/Votre aventure à deux/);
 assert.match(source,/commence ici\./);
 assert.match(source,/Connexion sécurisée pour Android et iPhone/);
 assert.equal((source.match(/class="love-feature"/g)||[]).length,4);
 assert.equal((source.match(/class="love-benefit"/g)||[]).length,5);
 assert.match(source,/Plus qu’un jeu/);
 assert.match(source,/Même équipe/);
 assert.match(source,/Sans pression/);
 assert.doesNotMatch(source,/🎲|💌|🔒/);
});
test('Premade art and CSS are available, web cache contains the artwork',()=>{
 const image=readFileSync(new URL('./assets/loveroll-hero.webp',import.meta.url));
 assert.ok(image.byteLength>1000);
 assert.equal(image.subarray(0,4).toString(),'RIFF');
 assert.equal(image.subarray(8,12).toString(),'WEBP');
 const stylesheet=readFileSync(new URL('./styles.css',import.meta.url),'utf8');
 assert.match(stylesheet,/\.love-hero-art/);
 assert.match(stylesheet,/\.love-trust/);
 assert.match(stylesheet,/grid-template-columns:repeat\(2,minmax\(0,1fr\)\)/);
 const sw=readFileSync(new URL('./service-worker.js',import.meta.url),'utf8');
 assert.match(sw,/assets\/loveroll-hero\.webp/);
 assert.match(sw,/loveroll-static-v3/);
});
