import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {premiumEveningHtml} from './premium-evening.mjs';
const read=path=>readFileSync(new URL('./'+path,import.meta.url),'utf8');
const members=[{user_id:'one',name:'Fred Messely'},{user_id:'two',name:'Alex Fred'}];
const base={session:{id:'s1'},members,me:'one',giftCount:0,
 turn:{ready:false,remaining:{one:2,two:3},targets:{one:2,two:3},completed:{one:0,two:0}},
 playable:{sensual:35,sexual:35,total:70}};
test('Soirée removes oversized header and old top Options, but retains contextual session management',()=>{
 const html=premiumEveningHtml({...base,status:{both_ready:false}});
 assert.doesNotMatch(html,/<header class="evening-topline">/);
 assert.doesNotMatch(html,/NOTRE SOIRÉE PARTAGÉE|⋯ Options/);
 assert.match(html,/<h2>Nos défis disponibles<\/h2>/);
 assert.match(html,/class="evening-manage" data-action="evening-more" aria-label="Gérer notre soirée"/);
 assert.match(html,/data-action="evening-list"/);
 assert.match(html,/data-action="give-challenge"/);
});
test('Partner has no 3D D6 until server confirms their actual pip face',()=>{
 for(const roll of [undefined,null,{user_id:'two',face:null},{user_id:'two',face:0}]){
  const html=premiumEveningHtml({...base,rolls:roll?[roll]:[],status:{my_rolled:false,partner_rolled:false,both_ready:false}});
  assert.match(html,/evening-partner-card is-pending/);
  assert.match(html,/En attente/);
  assert.equal((html.match(/data-dice-sides="6"/g)||[]).length,1,'Only our own 3D D6 is rendered');
  assert.match(html,/data-action="my-d6"/);
 }
});
test('Partner numeric result is immutable and separate from our own D6',()=>{
 const html=premiumEveningHtml({...base,rolls:[{user_id:'one',face:2},{user_id:'two',face:5}],
  status:{my_rolled:true,partner_rolled:true,both_ready:true},
  turn:{ready:true,next:'one',first:'two',remaining:{one:2,two:5},targets:{one:2,two:5},completed:{one:0,two:0}},
  category:'SENSUEL',ownChallengeCount:2});
 assert.match(html,/evening-partner-card is-confirmed/);
 assert.match(html,/evening-partner-die/);
 assert.match(html,/data-dice-sides="6" data-face="5"/);
 assert.match(html,/5 défis ce soir/);
 assert.equal((html.match(/data-dice-sides="6"/g)||[]).length,4);
 assert.match(html,/data-dice-sides="35"/);
 assert.match(html,/Voir mes défis \(2\)/);
 assert.match(html,/data-action="new-challenge"/);
});
test('v83 final responsive CSS reserves a fixed single-screen D6 and partner result',()=>{
 const css=read('evening-clean-v83.css');
 const index=read('index.html'),worker=read('service-worker.js');
 assert.match(css,/\.evening-partner-card\{/);
 assert.match(css,/\.evening-user-die \.love3d-stage\{/);
 assert.match(css,/aspect-ratio:1/);
 assert.match(css,/max-width:154px!important/);
 assert.match(css,/grid-template-rows:minmax\(51px,\.7fr\)/);
 assert.match(css,/\.evening-premium-view\.is-ready\{/);
 assert.match(index,/evening-clean-v83\.css\?v=20261015-no-toast87/);
 assert.match(index,/evening-focused-v84\.css\?v=20261015-no-toast87/);
 assert.ok(index.lastIndexOf('evening-clean-v83.css')>index.lastIndexOf('mobile-one-screen-v74.css'));
 assert.match(worker,/loveroll-static-v87/);
 assert.match(worker,/\.\/evening-clean-v83\.css/);
 assert.equal(JSON.parse(read('web-version.json')).build,'2026-10-15.87');
});

test('Two-step design locks challenges until both D6 and retains the ORIGINAL 3D engines',()=>{
 const wait=premiumEveningHtml({...base,status:{both_ready:false}});
 assert.match(wait,/Compléter l’étape 1 pour brasser le dé/);
 assert.match(wait,/data-action="new-challenge"[^>]*disabled/);
 assert.equal((wait.match(/data-dice-sides="6"/g)||[]).length,1);
 const ready=premiumEveningHtml({...base,status:{both_ready:true,my_rolled:true,partner_rolled:true},
  rolls:[{user_id:'one',face:3},{user_id:'two',face:6}],
  turn:{...base.turn,ready:true,next:'one'},category:'SEXUEL'});
 assert.match(ready,/evening-summary-die/);
 assert.match(ready,/data-dice-sides="35"/);
 assert.match(ready,/88 %/);
 assert.match(ready,/des chances d’obtenir la catégorie souhaitée/);
 assert.doesNotMatch(ready,/12 % autre|Défi cadeau : 100 %/);
 assert.match(ready,/data-action="new-challenge"/);
 assert.match(ready,/data-action="evening-list"/);
 const unchanged=read('dice3d.js');
 assert.match(unchanged,/const count=35/);
 assert.match(unchanged,/canvas\.dataset\.diceMarkers==='pips'/);
});
