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
