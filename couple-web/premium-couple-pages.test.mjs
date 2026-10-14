import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {premiumEveningHtml,premiumPolyDie} from './premium-evening.mjs';
import {premiumSurpriseHtml,sixSpecialEvents} from './premium-surprise.mjs';
const read=p=>readFileSync(new URL('./'+p,import.meta.url),'utf8');
const app=read('app.js'),css=read('premium-couple-pages.css'),die=read('dice3d.js'),sql=read('migrations/20261014_enable_existing_6x6_special_events.sql');
const people=[{user_id:'first',name:'Camille'}, {user_id:'second',name:'Noémie'}];
const session={id:'s',paired_turns:true,self_draws:true,starting_user_id:'first'};
const turns={ready:true,first:'first',targets:{first:2,second:1},completed:{first:1,second:0},remaining:{first:1,second:1},next:'first',finished:false,pending:null};
test('Evening reuses both real partner names, two D6, one D35 and existing action names',()=>{
 const html=premiumEveningHtml({session,members:people,me:'first',
   rolls:[{user_id:'first',face:2},{user_id:'second',face:1}],
   status:{both_ready:true,my_rolled:true,partner_rolled:true},turn:turns,category:'SENSUEL'});
 assert.match(html,/Camille/);
 assert.match(html,/Noémie/);
 assert.equal((html.match(/data-dice-sides="6"/g)||[]).length,2);
 assert.equal((html.match(/data-dice-sides="35"/g)||[]).length,1);
 assert.match(html,/data-action="category:SENSUEL"/);
 assert.match(html,/data-action="category:SEXUEL"/);
 assert.match(html,/data-action="new-challenge"/);
 assert.match(html,/data-action="give-challenge"/);
 assert.match(html,/0 \/ 1/);
 assert.match(html,/1 \/ 2/);
 assert.match(html,/88 % catégorie choisie/);
 assert.match(html,/12 % autre/);
 assert.doesNotMatch(html,/Fred|Émilie|Player 1/);
});
test('Private personal D6 results remain hidden until BOTH partner rolls are complete',()=>{
 const html=premiumEveningHtml({session,members:people,me:'first',
  rolls:[{user_id:'first',face:5}],status:{both_ready:false,my_rolled:true,partner_rolled:false},
  turn:{ready:false}});
 assert.doesNotMatch(html,/5 défis ce soir/);
 assert.match(html,/Résultat caché/);
 assert.match(html,/Étape 2 verrouillée/);
 assert.match(html,/En attente/);
 assert.match(html,/data-action="new-challenge"[^>]*disabled/);
 const otherUser=premiumEveningHtml({session,members:people,me:'second',
  rolls:[{user_id:'first',face:5}],status:{both_ready:false,my_rolled:false,partner_rolled:true},turn:{}});
 assert.match(otherUser,/data-action="my-d6"/);
});
test('Giving challenges updates both Step 1 totals while the dice show original faces',()=>{
 // Original dice: Camille 5, Noémie 2; two gifts transfer from Camille to Noémie.
 const gifted={...turns,targets:{first:3,second:4},remaining:{first:3,second:4}};
 const html=premiumEveningHtml({session,members:people,me:'first',
  rolls:[{user_id:'first',face:5},{user_id:'second',face:2}],
  status:{both_ready:true,my_rolled:true,partner_rolled:true},turn:gifted,category:'SENSUEL'});
 assert.match(html,/data-dice-sides="6" data-face="5"/);
 assert.match(html,/data-dice-sides="6" data-face="2"/);
 assert.match(html,/aria-label="Total actuel 3 défis; dé initial 5"><strong>3<\/strong>/);
 assert.match(html,/aria-label="Total actuel 4 défis; dé initial 2"><strong>4<\/strong>/);
 assert.match(html,/Dé initial : 5/);
 assert.match(html,/Dé initial : 2/);
 assert.match(html,/<b>1 \/ 3<\/b>/);
 assert.match(html,/<b>0 \/ 4<\/b>/);
 assert.match(css,/\.evening-original-d6\{/);
 // Without gifts, the cards still match the rolls, no redundant label.
 const normal=premiumEveningHtml({session,members:people,me:'first',
  rolls:[{user_id:'first',face:5},{user_id:'second',face:2}],
  status:{both_ready:true},turn:{...gifted,targets:{first:5,second:2}}});
 assert.doesNotMatch(normal,/Dé initial :/);
 assert.match(normal,/aria-label="Total actuel 5 défis; dé initial 5"><strong>5<\/strong>/);
 // When the second D6 is not rolled yet, no totals are revealed.
 const waiting=premiumEveningHtml({session,members:people,me:'first',
  rolls:[{user_id:'first',face:5}],status:{both_ready:false},
  turn:{...gifted,ready:false}});
 assert.doesNotMatch(waiting,/Total actuel/);
 assert.doesNotMatch(waiting,/Dé initial :/);
});
test('D35 always visible and rolling face not displayed as text before settling',()=>{
 const html=premiumEveningHtml({session,members:people,me:'first',turn:{...turns,pending:{face:21}},rollFace:11,
  rollingKind:'new-challenge',rolling:true,category:'SENSUEL'});
 assert.match(html,/data-dice-sides="35" data-face="11" data-dice-zoom="1.07" data-dice-markers="numbers" data-spinning="true"/);
 assert.match(html,/class="dice-fallback" aria-hidden="true"><\/strong>/);
 assert.match(css,/\.love3d-stage\.is-rolling \.dice-fallback/);
 assert.match(die, /canvas\.dataset\.diceMarkers==='pips'/);
 assert.match(die,/canvas\.dataset\.diceZoom/);
 assert.match(app,/await new Promise\(resolve=>setTimeout\(resolve,3250\)\)/);
});
test('D35 always shows the official saved result below the die after rolling',()=>{
 const live=premiumEveningHtml({session,members:people,me:'first',
  turn:{...turns,pending:{face:21}},status:{both_ready:true},category:'SENSUEL'});
 assert.match(live,/Résultat D35 · N° 21/);
 assert.match(live,/data-dice-sides="35" data-face="21"/);
 assert.match(live,/class="evening-d35-result" role="status" aria-live="polite"/);
 // After closing a challenge, the last officially stored face remains visible.
 const last=premiumEveningHtml({session,members:people,me:'first',
  turn:turns,status:{both_ready:true},lastD35Face:17});
 assert.match(last,/Résultat D35 · N° 17/);
 assert.match(last,/data-dice-sides="35" data-face="17"/);
 // Never reveal a number until the server draw has settled.
 const spinning=premiumEveningHtml({session,members:people,me:'first',
  turn:{...turns,pending:{face:21}},status:{both_ready:true},lastD35Face:17,
  rolling:true,rollingKind:'new-challenge',rollFace:11});
 assert.match(spinning,/Brassage en cours…/);
 assert.doesNotMatch(spinning,/Résultat D35 · N°/);
 const waiting=premiumEveningHtml({session,members:people,me:'first',
  turn:turns,status:{both_ready:true}});
 assert.match(waiting,/Résultat D35 · —/);
 assert.doesNotMatch(waiting,/Résultat D35 · N°/);
 const locked=premiumEveningHtml({session,members:people,me:'first',
  turn:{ready:false},status:{both_ready:false},lastD35Face:12});
 assert.doesNotMatch(locked,/Résultat D35 · N°/);
 assert.match(css,/\.evening-d35-result\{/);
 assert.match(app,/lastD35Face:/);
});
test('Gift transfer disabled when monthly 3/3 or zero own remaining',()=>{
 const html=premiumEveningHtml({session,members:people,me:'first',turn:turns,
   status:{both_ready:true},giftCount:3});
 assert.match(html,/data-action="give-challenge" disabled/);
 assert.match(html,/3\/3 dons ce mois/);
 assert.match(app,/rpc\('give_love_challenge'/);
});
test('Surprise page has official D20, D6, six custom event cards and correct partner splits',()=>{
 const html=premiumSurpriseHtml({members:people,settings:{anniversary_md:'08-20',
 birthday_by_user:{first:'01-10',second:'11-25'}},now:new Date('2026-10-14T17:00:00Z')});
 assert.equal((html.match(/class="surprise-event-card /g)||[]).length,6);
 assert.equal((html.match(/data-dice-sides="20"/g)||[]).length,1);
 assert.equal((html.match(/data-dice-sides="6"/g)||[]).length,1);
 assert.match(html,/1 · 2 · 3/);
 assert.match(html,/4 · 5 · 6/);
 assert.match(html,/Camille/);assert.match(html,/Noémie/);
 assert.match(html,/data-action="roll:monthly_activity"/);
 assert.match(html,/data-action="roll:monthly_host"[^>]*disabled/);
 assert.match(html,/Fête des Mères/);
 assert.match(html,/Fête des Pères/);
 assert.match(html,/data-action="open-occasion:birthday:first"/);
});
test('Special event Canadian date rollovers and missing anniversary remain correct',()=>{
 const settings={anniversary_md:'01-04',birthday_by_user:{first:'02-29',second:'12-25'}};
 const all=sixSpecialEvents(new Date('2026-10-14T13:00:00Z'),settings,people);
 assert.equal(all.length,6);
 assert.equal(all[0].date,'2027-01-04');
 assert.equal(all[1].date,'2027-02-28');
 assert.equal(all[2].date,'2026-12-25');
 assert.equal(all[3].date,'2027-02-14');
 assert.equal(all[4].date,'2027-05-09');
 assert.equal(all[5].date,'2027-06-20');
 assert.equal(sixSpecialEvents(new Date('2026-10-14T13:00:00Z'),{},people)[0].daysLeft,null);
});
test('Existing server rolls and cards untouched; migration makes configured 6x6 available in 2026',()=>{
 assert.match(sql,/if event_code is not null then/);
 assert.doesNotMatch(sql,/eventyear>=2027/);
 assert.match(sql,/select \* into rowout from public\.couple_occasion_rolls/);
 assert.match(sql,/if prior\.couple_id is null then raise exception/);
 assert.doesNotMatch(sql,/delete from public\.couple_challenges/);
 assert.match(app,/rpc\('roll_love_occasion_once'/);
 assert.match(app,/rpc\('roll_my_evening_d6'/);
 assert.match(app,/rpc\('draw_for_partner'/);
 assert.match(app,/sharedEveningTurns\(/);
});
test('D20 and D35 are true glossy rounded polyhedra without pink wireframe',()=>{
 assert.match(die,/roundedPolyhedron\(polygons/);
 assert.match(die,/bevel:sides===20\?\.13:\.095/);
 assert.match(die,/shrink:sides===20\?\.095:\.07/);
 assert.match(die,/const n0=arc\(na,nb,j\/steps\)/);
 assert.match(die,/tri\(apex,base\.clone\(\)\.addScaledVector\(n0,bevel\)/);
 assert.match(die,/normal:outward\.clone\(\),value:i\+1/);
 assert.match(die,/flatShading:false/);
 assert.match(die,/clearcoat:1,clearcoatRoughness:/);
 assert.doesNotMatch(die,/new THREE\.LineSegments|new THREE\.LineBasicMaterial|new THREE\.EdgesGeometry/);
 assert.match(die,/new THREE\.IcosahedronGeometry\(1\.16,0\)/);
 assert.match(die,/const count=35/);
 assert.match(die,/digits\(value,sides===6,pips\)/);
});
test('Mobile full-height layout with safe area and fixed existing tabs',()=>{
 assert.match(css,/100dvh/);
 assert.match(css,/safe-area-inset-top/);
 assert.match(css,/loveroll-evening-page/);
 assert.match(css,/loveroll-surprise-page/);
 assert.match(css,/surprise-special-grid/);
 assert.match(css,/repeat\(3,minmax\(0,1fr\)\)/);
 assert.match(app,/if\(verb==='occasion-back'\)/);
});
