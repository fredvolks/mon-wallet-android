import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const sql=readFileSync(new URL('./migrations/20261014_allow_force_admin_weekly_reset.sql',import.meta.url),'utf8');
const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');

test('admin weekly reset works after a started session',()=>{
 assert.match(sql,/create or replace function public\.reset_love_weekly_roll_admin\(p_couple uuid\)/);
 assert.doesNotMatch(sql,/if exists\s*\(\s*select 1 from public\.couple_sessions/i);
 assert.doesNotMatch(sql,/raise exception 'Impossible de réinitialiser/i);
 assert.match(app,/Reset admin forcé : fonctionne même si une soirée est commencée/);
 assert.match(app,/Même si une soirée a déjà commencé, elle et ses défis resteront enregistrés/);
});
test('reset preserves all historical sessions, challenges and accounts',()=>{
 assert.match(sql,/delete from public\.couple_rolls where id=old\.id;/);
 assert.doesNotMatch(sql,/delete\s+from\s+(?:public\.)?(?:couple_sessions|couple_challenges|couple_personal_rolls|couple_members|couples|auth\.users)/i);
 assert.match(sql,/insert into public\.love_weekly_reset_audit/);
 assert.match(sql,/insert into public\.couple_events/);
 assert.match(sql,/r\.kind='weekly'/);
 assert.match(sql,/r\.key=to_char\(v_week,'YYYY-MM-DD'\)/);
});
test('only the authenticated couple owner may force reset',()=>{
 assert.match(sql,/where c\.id=p_couple and c\.owner_user_id=auth\.uid\(\)/);
 assert.match(sql,/not couple_private\.is_member\(p_couple\)/);
 assert.match(sql,/for update/);
 assert.match(sql,/revoke all on function public\.reset_love_weekly_roll_admin\(uuid\)/);
 assert.match(sql,/grant execute on function public\.reset_love_weekly_roll_admin\(uuid\)/);
 assert.match(app,/if\(!isLoveAdmin\(\)\|\|!isSharedCoupleReady/);
 assert.match(app,/if\(!\(await premiumConfirm\(message,\{kind:'reset'/);
});
test('reset only affects current Toronto week and permits a fresh roll',()=>{
 assert.match(sql,/time zone 'America\/Toronto'/);
 assert.match(sql,/extract\(isodow from local_day\)/);
 assert.match(app,/rpc\('reset_love_weekly_roll_admin',\{p_couple:state\.coupleId\}\)/);
 assert.match(app,/await refreshAndShow\(\)/);
});
