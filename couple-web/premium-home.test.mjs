import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
 WEEKLY_ODDS,PIP_POSITIONS,pipFace,nextMonday,nextMonthly,
 assignedTonight,premiumHomeHtml
} from './premium-home.mjs';

const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');
const sql=readFileSync(new URL('./migrations/20261014_weekly_admin_reset.sql',import.meta.url),'utf8');
const css=readFileSync(new URL('./premium-home.css',import.meta.url),'utf8');
const die=readFileSync(new URL('./dice3d.js',import.meta.url),'utf8');
test('Weekly faces 1-6 use true standard dice spot geometry and 100% total',()=>{
 assert.deepEqual(WEEKLY_ODDS,[50,25,12,8,4,1]);
 assert.equal(WEEKLY_ODDS.reduce((a,b)=>a+b,0),100);
 for(let n=1;n<=6;n++){
   assert.equal(PIP_POSITIONS[n].length,n);
   assert.equal((pipFace(n).match(/<circle /g)||[]).length,n);
 }
 assert.deepEqual(PIP_POSITIONS[1],[[50,50]]);
 assert.deepEqual(PIP_POSITIONS[2],[[29,29],[71,71]]);
 assert.deepEqual(PIP_POSITIONS[3],[[29,29],[50,50],[71,71]]);
 assert.deepEqual(PIP_POSITIONS[4],[[29,29],[71,29],[29,71],[71,71]]);
 assert.deepEqual(PIP_POSITIONS[5][2],[50,50]);
 assert.deepEqual(PIP_POSITIONS[6],[[29,22],[71,22],[29,50],[71,50],[29,78],[71,78]]);
 assert.throws(()=>pipFace(7));
});
test('Next weekly roll is Monday at Toronto midnight, including Sunday and DST',()=>{
 assert.equal(nextMonday(new Date('2026-10-14T18:00:00Z')),'2026-10-19');
 assert.equal(nextMonday(new Date('2026-10-18T14:00:00Z')),'2026-10-19');
 assert.equal(nextMonday(new Date('2026-10-19T11:00:00Z')),'2026-10-26');
 assert.equal(nextMonday(new Date('2026-03-08T22:00:00Z')),'2026-03-09');
});
test('Next first-of-month countdown updates, not hardcoded',()=>{
 assert.deepEqual(nextMonthly(new Date('2026-10-14T16:00:00Z')).date,'2026-11-01');
 assert.equal(nextMonthly(new Date('2026-10-14T16:00:00Z')).days,18);
 assert.equal(nextMonthly(new Date('2026-10-31T16:00:00Z')).days,1);
 assert.equal(nextMonthly(new Date('2026-11-01T16:00:00Z')).date,'2026-12-01');
});
test('Home counts only existing real assigned challenges per recipient',()=>{
 const members=[{user_id:'a',name:'Fred'},{user_id:'b',name:'Camille'}];
 const items=[
  {recipient:'a',status:'pending',category:'SENSUEL'},
  {recipient:'a',status:'accepted',category:'SEXUEL'},
  {recipient:'b',status:'pending',category:'SEXUEL'},
  {recipient:'b',status:'cancelled'}
 ];
 assert.deepEqual(assignedTonight(items,members),{total:3,forFirst:2,forSecond:1});
 assert.deepEqual(assignedTonight([],members),{total:0,forFirst:0,forSecond:0});
});
test('Production home uses actual saved weekly result, disabled state and live cards',()=>{
 const args={weekly:{face:2,actor:'a'},members:[{user_id:'a',name:'Fred'},{user_id:'b',name:'Camille'}],
 challenges:[{recipient:'a',status:'pending'},{recipient:'b',status:'accepted'}],now:new Date('2026-10-14T16:00:00Z')};
 const h=premiumHomeHtml(args);
 assert.match(h,/2 soirée\(s\) de défi/);
 assert.match(h,/Dé déjà brassé cette semaine/);
 assert.match(h,/data-dice-sides="6"/);
 assert.match(h,/data-face="2"/);
 assert.equal((h.match(/class="home-weight"/g)||[]).length,6);
 assert.equal((h.match(/class="home-shortcut /g)||[]).length,4);
 assert.match(h,/Fred/);assert.match(h,/Camille/);
 assert.match(h,/18 jours/);
 assert.match(h,/tab:special/);
 assert.doesNotMatch(h,/data-action="roll:weekly"/);
 const waiting=premiumHomeHtml({...args,weekly:null});
 assert.match(waiting,/data-action="roll:weekly"/);
 assert.match(waiting,/Brassage disponible 1x par semaine/);
 const rolling=premiumHomeHtml({...args,weekly:args.weekly,rolling:true});
 assert.match(rolling,/data-spinning="true"/);
});
test('True WebGL die retains 1/6, 2/5, 3/4 opposite sides and real 2.65s tumble',()=>{
 assert.match(die,/\[1,\[0,0,1\]\],\[6,\[0,0,-1\]\]/);
 assert.match(die,/\[2,\[0,1,0\]\]/);
 assert.match(die,/\[5,\[0,-1,0\]\]/);
 assert.match(die,/\[3,\[1,0,0\]\]/);
 assert.match(die,/\[4,\[-1,0,0\]\]/);
 assert.match(die,/duration=rolling\?2650:0/);
 assert.match(die,/new THREE\.WebGLRenderer/);
 assert.match(die,/built\.group\.quaternion\.copy\(target\)/);
 assert.match(app,/state\.weekly=result/);
 assert.match(app,/state\.diceStatus='rolling'/);
 assert.match(app,/setTimeout\(resolve,2750\)/);
});
test('Admin reset requires privileged server action and never touches custom challenges',()=>{
 assert.match(app,/isLoveAdmin\(\)&&hasPartner/);
 assert.match(app,/if\(verb==='admin-reset-weekly'\)/);
 assert.match(app,/confirm\(message\)/);
 assert.match(app,/rpc\('reset_love_weekly_roll_admin'/);
 assert.match(sql,/c\.owner_user_id=auth\.uid\(\)/);
 assert.match(sql,/for update/);
 assert.match(sql,/from public\.couple_sessions/);
 assert.match(sql,/delete from public\.couple_rolls where id=old\.id/);
 assert.match(sql,/love_weekly_reset_audit/);
 assert.doesNotMatch(sql,/delete from public\.couple_challenges/);
 assert.doesNotMatch(sql,/delete from auth\.users/);
 assert.match(css,/100dvh/);
 assert.match(css,/overflow:hidden/);
});
