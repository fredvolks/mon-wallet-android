import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {premiumEveningHtml} from './premium-evening.mjs';
const read=name=>readFileSync(new URL(name,import.meta.url),'utf8');
const users=[{user_id:'p1',name:'Fred'},{user_id:'p2',name:'Fred'}];
const ready={session:{id:'sess1',self_draws:true,paired_turns:true},members:users,me:'p1',
 rolls:[{user_id:'p1',face:3},{user_id:'p2',face:2}],
 status:{my_rolled:true,partner_rolled:true,both_ready:true},
 turn:{ready:true,drawsFinished:false,finished:false,next:'p1',
  remaining:{p1:3,p2:2},targets:{p1:3,p2:2},completed:{p1:0,p2:0}}};
test('Soirée shows only one unified D100 and no category controls',()=>{
 const html=premiumEveningHtml(ready);
 assert.match(html,/NOTRE RITUEL DE LA SOIRÉE/);
 assert.match(html,/Une seule bibliothèque/);
 assert.equal((html.match(/data-dice-sides="100"/g)||[]).length,1);
 assert.doesNotMatch(html,/data-action="category:|88 %|Sensuel \/ Sexuel/);
 assert.match(html,/data-action="new-challenge"/);
 assert.doesNotMatch(html,/data-action="new-challenge"[^>]*disabled/);
});
test('The D100 remains locked until both initial D6 rolls',()=>{
 const html=premiumEveningHtml({...ready,status:{both_ready:false},turn:{ready:false}});
 assert.match(html,/Compléter l’étape 1 pour brasser le dé/);
 assert.match(html,/data-action="new-challenge"[^>]*disabled/);
});
test('Old WebGL die and server-confirmed result stay unchanged',()=>{
 const html=premiumEveningHtml({...ready,rolling:true,rollingKind:'new-challenge',rollFace:13});
 assert.match(html,/data-dice-sides="100" data-face="13"/);
 assert.match(html,/data-spinning="true"/);
 assert.doesNotMatch(html,/evening-d100-result/);
 assert.match(read('dice3d.js'),/manySidedGeometry/);
});
test('Supabase uses one MIXTE library with protected history and gender roles',()=>{
 const app=read('app.js'),sql=read('migrations/20261016_unified_d100_112.sql');
 assert.match(app,/p_preference:'MIXTE'/);
 assert.doesNotMatch(app,/if\(verb==='category'\)/);
 assert.match(sql,/if p_preference<>'MIXTE'/);
 assert.match(sql,/category='MIXTE'/);
 assert.match(sql,/love_role_allows/);
 assert.match(sql,/generate_series\(1,100\)/);
 assert.doesNotMatch(sql,/DELETE FROM public\.couple_challenges/i);
});
