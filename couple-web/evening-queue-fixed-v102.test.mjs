import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL('./'+p,import.meta.url),'utf8');
const app=read('app.js'),css=read('evening-queue-fixed-v102.css');
test('D35 official result is shown for one second after completing the roll animation',()=>{
 const start=app.indexOf('async function animateRoll('),end=app.indexOf('async function hashToken(',start);
 const flow=app.slice(start,end);
 assert.match(flow,/if\(kind==='new-challenge'\)/);
 assert.match(flow,/state\.diceStatus='settled'/);
 assert.match(flow,/await refreshShared\(\)/);
 assert.match(flow,/setTimeout\(resolve,1000\)/);
 assert.ok(flow.indexOf("state.diceStatus='settled'")<flow.indexOf('setTimeout(resolve,1000)'));
 assert.match(app,/state\.selectedEveningChallengeId=drawn\.id/);
});
test('Only selected detail locks scroll, not the queue list',()=>{
 assert.ok(app.includes("evening-queue-modal'+(view?' is-detail':'')"));
 assert.match(css,/@media\(max-width:767px\)/);
 assert.match(css,/\.evening-queue-modal\.is-detail/);
 assert.match(css,/\.evening-queue-content\.is-detail/);
 assert.match(css,/overflow:hidden!important/);
 assert.ok(read('index.html').includes('evening-queue-fixed-v102.css?v=20261016-queue102'));
 assert.ok(read('service-worker.js').includes('./evening-queue-fixed-v102.css'));
});
