import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {feedbackItemsFor} from './feedback-status.mjs';
const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');
test('No exception for null status while entering Soirée after all-dice reset',()=>{
 for(const bad of [null,undefined,false,[],{}, {sessionId:'s1',items:null}])
  assert.equal(feedbackItemsFor(bad,'s1'),null);
 assert.equal(feedbackItemsFor(null,undefined),null);
 assert.equal(feedbackItemsFor({items:[]},undefined),null);
});
test('Only matching session with real items can display aftercare',()=>{
 const data=[{user_id:'one',submitted:true}];
 assert.equal(feedbackItemsFor({sessionId:'s1',items:data},'s1'),data);
 assert.equal(feedbackItemsFor({sessionId:'s1',items:data},'s2'),null);
});
test('Soirée locked access always renders explanation and never reads null feedback.items',()=>{
 assert.match(app,/if\(!state.weekly\)\{/);
 assert.match(app,/frame\(locked,'NOTRE SOIRÉE'/);
 assert.match(app,/Brassez d’abord le dé hebdomadaire/);
 assert.match(app,/const recentReviews=last\?\(state.eveningReviews\|\|\[\]\)/);
 assert.match(app,/const statuses=feedbackItemsFor\(state.feedbackStatus,session.id\)/);
 assert.doesNotMatch(app,/state\.feedbackStatus\.items/);
});
