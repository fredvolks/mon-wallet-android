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
test('D35 always visible and rolling face not displayed as text before settling',()=>{
 const html=premiumEveningHtml({session,members:people,me:'first',turn:{...turns,pending:{face:21}},rollFace:11,
  rollingKind:'new-challenge',rolling:true,category:'SENSUEL'});
 assert.match(html,/data-dice-sides="35" data-face="11" data-dice-zoom="1.07" data-dice-markers="numbers" data-spinning="true"/);
 assert.match(html,/class="dice-fallback" aria-hidden="true"><\/strong>/);
 assert.match(css,/\.love3d-stage\.is-rolling \.dice-fallback/);
 assert.match(die, /canvas\.dataset\.diceMarkers==='pips'/);
 assert.match(die,/canvas\.dataset\.diceZoom/);
 assert.match(app,/await new Promise\(resolve=>setTimeout\(resolve,2750\)\)/);
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
test('Mobile full-height layout with safe area and fixed existing tabs',()=>{
 assert.match(css,/100dvh/);
 assert.match(css,/safe-area-inset-top/);
 assert.match(css,/loveroll-evening-page/);
 assert.match(css,/loveroll-surprise-page/);
 assert.match(css,/surprise-special-grid/);
 assert.match(css,/repeat\(3,minmax\(0,1fr\)\)/);
 assert.match(app,/if\(verb==='occasion-back'\)/);
});
