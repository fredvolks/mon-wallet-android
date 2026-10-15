import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {feedbackItemsFor} from './feedback-status.mjs';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
const app=read('app.js'),dice=read('dice3d.js'),style=read('surprise-dice-layout-v73.css');
const index=read('index.html'),worker=read('service-worker.js');
test('Soirée tolerates missing/null aftercare before AND after resets',()=>{
 for(const missing of [null,undefined,false,[],{}, {items:null}, {items:'invalid'}])
  assert.equal(feedbackItemsFor(missing,'session-id'),null);
 assert.match(app,/const official=last\?feedbackItemsFor\(state\.feedbackStatus,last.id\):null/);
 assert.match(app,/const statuses=feedbackItemsFor\(state\.feedbackStatus,session.id\)/);
 assert.doesNotMatch(app,/state\.feedbackStatus\.items\b/);
 assert.match(app,/loveroll-aftercare-auto-recover-/);
 assert.match(app,/reloadWebAssets\(true\)/);
});
test('Surprise monthly D20 and host D6 own separate centered 3D stages and never escape scene',()=>{
 assert.match(style,/surprise-d20-scene/);
 assert.match(style,/surprise-host-die/);
 assert.match(style,/display:grid;place-items:center/);
 assert.match(style,/\.dice-canvas\{/);
 assert.match(style,/position:absolute!important/);
 assert.match(style,/overflow:hidden!important/);
 assert.match(style,/grid-template-rows:minmax\(49px,auto\) minmax\(230px,auto\)/);
 assert.match(style,/max-width:767px/);
 assert.match(style,/min-width:768px/);
});
test('Renderer resizes to live scene with WebGL camera correctly, also after orientation switch',()=>{
 assert.match(dice,/const layoutSize=\(\)=>/);
 assert.match(dice,/canvas\.getBoundingClientRect\(\)/);
 assert.match(dice,/renderer\.setSize\(width,height,false\)/);
 assert.match(dice,/camera\.aspect=width\/height/);
 assert.match(dice,/new ResizeObserver\(resizeDie\)/);
 assert.match(dice,/resizeObserver\?\.disconnect\(\)/);
});
test('PWA and served HTML share v73 CSS and cache-busting tags',()=>{
 assert.match(index,/surprise-dice-layout-v73\.css\?v=20261015-heart80/);
 assert.match(worker,/loveroll-static-v80/);
 assert.match(worker,/surprise-dice-layout-v73\.css/);
 assert.match(app,/WEB_RELEASE='2026-10-15\.80'/);
});
