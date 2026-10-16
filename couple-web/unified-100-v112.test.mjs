import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {EVENING_CATEGORIES,EVENING_CHALLENGES,editableCardSlots,eveningCard,playableEveningCount,resolvedCard} from './evening-challenges.mjs';
import {buildEveningLibrary,validateEveningLibrary,normalizeLibraryData} from './json-libraries.mjs';
import {premiumEveningHtml} from './premium-evening.mjs';
const read=(name)=>readFileSync(new URL('./'+name,import.meta.url),'utf8');

test('Exactly one library of 100, with 50 seeded and 50 editable empty slots',()=>{
 assert.deepEqual(EVENING_CATEGORIES,['MIXTE']);
 assert.equal(EVENING_CHALLENGES.MIXTE.length,50);
 const cards=editableCardSlots('MIXTE');
 assert.equal(cards.length,100);
 assert.equal(cards.filter(c=>c.isOriginal).length,50);
 assert.equal(cards.filter(c=>c.filled).length,50);
 assert.equal(cards.filter(c=>!c.filled).length,50);
 assert.equal(playableEveningCount().total,50);
 assert.equal(eveningCard('MIXTE',1).title,eveningCard('SENSUEL',1).title);
 assert.equal(eveningCard('MIXTE',26).title,eveningCard('SEXUEL',1).title);
 assert.equal(eveningCard('SEXUEL',1).title,'Strip-tease privé');
 assert.equal(resolvedCard({category:'SENSUEL',card_id:1,face:16}).title,eveningCard('SENSUEL',1).title);
});
test('Export has 100 unified cards',()=>{
 const exported=buildEveningLibrary('MIXTE');
 assert.equal(exported.version,2);
 assert.equal(exported.category,'MIXTE');
 assert.equal(exported.cards.length,100);
 assert.equal(validateEveningLibrary(exported).cards.length,100);
});
test('Import the provided version-2 sexual 100-card file, with him / her / both',()=>{
 const file={format:'loveroll-evening',version:2,category:'SEXUEL',
  cards:Array.from({length:100},(_,i)=>({card_id:i+1,
   title:'Défi personnalisé '+(i+1),
   description:'Une activité consentie entre les deux personnes, numéro '+(i+1)+'.',
   timer_minutes:i===40?5:null,
   drawer_role:i===39?'her':i===40?'him':'both',
   recipient_role:i===39?'him':i===40?'her':'both'
  }))};
 const result=normalizeLibraryData(file,'evening');
 assert.equal(result.category,'MIXTE');
 assert.equal(result.version,2);
 assert.equal(result.cards.length,100);
 assert.equal(result.cards[39].drawer_role,'her');
 assert.equal(result.cards[39].recipient_role,'him');
 assert.equal(result.cards[40].timer_minutes,5);
});
test('Partial old files update only their numbered cards; other 100 positions persist',()=>{
 const file={format:'loveroll-evening',version:1,category:'SENSUEL',
   cards:[{card_id:40,title:'Ancienne carte compatible',
    description:'Consigne personnalisée et consentie.',
    timer_minutes:null,drawer_role:'both',recipient_role:'both'}]};
 const parsed=normalizeLibraryData(file,'evening');
 assert.equal(parsed.cards.length,1);
 assert.equal(parsed.cards[0].card_id,40);
 assert.equal(parsed.category,'MIXTE');
});
test('Duplicate or invalid gender rules do not pass validation',()=>{
 const bad=buildEveningLibrary('MIXTE');
 bad.cards[4].recipient_role='invalid';
 assert.throws(()=>validateEveningLibrary(bad),/Restrictions/);
 bad.cards[4].recipient_role='both';
 bad.cards[5].card_id=5;
 assert.throws(()=>validateEveningLibrary(bad),/Numérotation/);
});
test('No category buttons remain in Soirée UI or admin editor',()=>{
 const app=read('app.js'),evening=read('premium-evening.mjs');
 assert.ok(evening.includes('Une seule bibliothèque'));
 assert.ok(!evening.includes('data-action="category:SENSUEL"'));
 assert.ok(!evening.includes("bt('category:SENSUEL'"));
 assert.ok(!evening.includes("bt('category:SEXUEL'"));
 assert.ok(app.includes("p_preference:'MIXTE'"));
 assert.ok(app.includes("jsonToolsPanel('evening','100 cartes'"));
 assert.ok(!app.includes("data-action=\"custom-category:"));
 assert.ok(!app.includes("if(verb==='category'){"));
 assert.ok(app.includes('50 cartes prêtes'));
 assert.ok(app.includes('50 emplacements personnalisables'));
});
test('Database keeps historical categories while all future rolls use unified 100',()=>{
 const sql=read('migrations/20261016_unified_d100_112.sql');
 assert.ok(sql.includes('ON CONFLICT(couple_id,category,card_id) DO NOTHING'));
 assert.ok(sql.includes("category IN ('SENSUEL','SEXUEL')"));
 assert.ok(sql.includes("v_cat:='MIXTE'"));
 assert.ok(sql.includes("if p_preference<>'MIXTE'"));
 assert.ok(sql.includes("generate_series(1,100)"));
 assert.ok(sql.includes("cid<=50"));
 assert.ok(sql.includes("p_library->>'version' not in ('1','2')"));
 assert.ok(sql.includes("card_id in (select (value->>'card_id')::integer"));
 assert.ok(sql.includes("couple_private.love_role_allows"));
 assert.ok(sql.includes("love_card_draw_history"));
});
