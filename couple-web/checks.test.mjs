import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
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
 assert.match(app,/Continuer avec Google/);
 assert.match(app,/Continuer avec Apple/);
 assert.match(app,/client\.auth\.signInWithOtp/);
 assert.match(app,/emailRedirectTo:redirectTo/);
 assert.match(app,/if\(verb==='email-login'\)/);
});
