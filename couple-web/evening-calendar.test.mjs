import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {eveningHistoryMarkup,eveningLocalDay} from './evening-calendar.mjs';
const files=p=>readFileSync(new URL(p,import.meta.url),'utf8');
const sessions=[
 {id:'s1',started_at:'2026-10-16T01:00:00Z',finished_at:'2026-10-16T02:00:00Z',counted:true},
 {id:'s2',started_at:'2026-10-15T16:00:00Z',finished_at:null,counted:false},
 {id:'older',started_at:'2026-09-01T12:00:00Z',finished_at:'2026-09-01T13:00:00Z',counted:false}
];
const members=[{user_id:'a',name:'Fred'},{user_id:'b',name:'Partenaire'}];
const stats=[
 {session_id:'s1',user_id:'a',target:5,drawn:3,completed:2,incomplete:1,passed:0},
 {session_id:'s1',user_id:'b',target:3,drawn:2,completed:1,incomplete:1,passed:0},
 {session_id:'s2',user_id:'a',target:4,drawn:0,completed:0,incomplete:0,passed:0},
 {session_id:'s2',user_id:'b',target:null,drawn:0,completed:0,incomplete:0,passed:0}
];
const ownChallenges=[
 {id:'d1',session_id:'s1',recipient:'a',category:'SENSUEL',face:2,card_title:'Mon défi fait',
  status:'completed',created_at:'2026-10-16T01:01:00Z'},
 {id:'d2',session_id:'s1',recipient:'a',category:'SENSUEL',face:3,card_title:'Mon défi à faire',
  status:'proposed',created_at:'2026-10-16T01:02:00Z'},
 {id:'d3',session_id:'s1',recipient:'b',category:'SEXUEL',face:2,card_title:'SECRET PARTENAIRE',
  status:'completed',created_at:'2026-10-16T01:03:00Z'}
];
const render=(date='2026-10-15')=>eveningHistoryMarkup({
 sessions,members,stats,ownChallenges,me:'a',month:'2026-10',selectedDate:date});
test('Québec local calendar honors time zone rather than raw UTC day',()=>{
 assert.equal(eveningLocalDay('2026-10-16T01:00:00Z'),'2026-10-15');
 assert.equal(eveningLocalDay('2026-01-02T02:00:00Z'),'2026-01-01');
 assert.equal(eveningLocalDay('not-a-date'),null);
});
test('Historical day includes both completed and incomplete counts without leaking partner card',()=>{
 const html=render();
 assert.match(html,/Mon défi fait/);
 assert.match(html,/Mon défi à faire/);
 assert.match(html,/✓ Réalisé/);
 assert.match(html,/À faire, non réalisé/);
 assert.match(html,/2<\/b> réalisés/);
 assert.match(html,/3<\/b> non réalisés/);
 assert.match(html,/1<\/b> réalisé/);
 assert.match(html,/2<\/b> non réalisés/);
 assert.doesNotMatch(html,/SECRET PARTENAIRE/);
 assert.match(html,/data-action="evening-date:2026-10-15"/);
 assert.match(html,/data-action="evening-month-prev"/);
 assert.match(html,/data-action="evening-month-next"/);
});
test('Empty days stay navigable without fake activity',()=>{
 const html=render('2026-10-10');
 assert.match(html,/Aucune soirée enregistrée pour cette journée/);
 assert.doesNotMatch(html,/Mon défi fait/);
});
test('Calendar release closes incomplete sessions without changing results or personal-card RLS',()=>{
 const app=files('./app.js'),sql=files('./migrations/20261016_close_evening_and_history.sql');
 const menu=files('./premium-evening.mjs');
 assert.match(app,/rpc\('close_love_evening_preserve',\{p_session:ended\.id\}\)/);
 assert.match(app,/rpc\('love_evening_session_stats',\{p_couple:c\}\)/);
 assert.match(app,/state\.tab='evening-calendar';await refreshAndShow\(\)/);
 assert.match(sql,/counted=true/);
 assert.doesNotMatch(sql,/DELETE FROM public\.couple_challenges|DELETE FROM public\.couple_personal_rolls/i);
 assert.match(sql,/couple_private\.is_member\(v_couple\)/);
 assert.match(sql,/couple_private\.is_member\(p_couple\)/);
 assert.match(sql,/love_require_verified_identity\(\)/);
 const options=menu.slice(menu.indexOf("(showMore?"),menu.indexOf("(confirmCancel?"));
 assert.match(options,/Clore la soirée/);
 assert.match(options,/Calendrier/);
 assert.doesNotMatch(options,/tab:personal-journal|Calendrier partagé|Arrêter sans compter|Fermer/);
});
