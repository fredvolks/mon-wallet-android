import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {premiumSurpriseHtml} from './premium-surprise.mjs';
const read=path=>readFileSync(new URL('./'+path,import.meta.url),'utf8');
const app=read('app.js'),index=read('index.html'),css=read('surprise-responsive-v86.css');
const state={
 monthly:{monthly_activity:{face:11},monthly_host:{face:2}},
 activity:{title:'Massage surprise',description:'Prenez un moment ensemble.',timer_minutes:10},
 members:[{user_id:'a',name:'Fred'},{user_id:'b',name:'Marie'}],
 showActivity:true,timerHtml:'<div class="timer">10:00</div>'
};
test('Confirmed D20 opens the activity modal after the official roll animation',()=>{
 const start=app.indexOf("const result=await animateRoll(()=>rpc('roll_couple_once'");
 const branch=app.slice(start,start+800);
 assert.match(branch,/kind==='monthly_activity'/);
 assert.match(branch,/state\.showSurpriseActivity=!!state\.monthly\?\.monthly_activity/);
 assert.match(branch,/render\(\)/);
 assert.doesNotMatch(branch,/notify\('♡ Le dé s’est arrêté sur le résultat officiel/);
 assert.match(app,/if\(verb==='surprise-activity'\)\{state\.showSurpriseActivity=!state\.showSurpriseActivity/);
});
test('Activity modal shows the server-confirmed title, description and timer and closes',()=>{
 const html=premiumSurpriseHtml(state);
 assert.match(html,/role="dialog" aria-modal="true" aria-label="Notre activité du mois"/);
 assert.match(html,/<h2>Massage surprise<\/h2>/);
 assert.match(html,/Prenez un moment ensemble/);
 assert.match(html,/10:00/);
 assert.match(html,/data-action="surprise-activity"/);
 assert.doesNotMatch(premiumSurpriseHtml({...state,showActivity:false}),/aria-modal="true"/);
});
test('v104 refines v86, is cached offline and preserves desktop scroll constraints',()=>{
 const sw=read('service-worker.js'),ver=JSON.parse(read('web-version.json'));
 assert.equal(ver.build,'2026-10-16.112');
 assert.match(sw,/loveroll-static-v112/);
 assert.match(sw,/\.\/surprise-responsive-v86\.css/);
 assert.match(index,/surprise-responsive-v86\.css\?v=20261015-no-toast87/);
 assert.ok(index.lastIndexOf('surprise-responsive-v86.css')>index.lastIndexOf('evening-focused-v84.css'));
 assert.ok(index.lastIndexOf('surprise-refined-v104.css')>index.lastIndexOf('surprise-responsive-v86.css'));
 assert.match(sw,/surprise-refined-v104\.css/);
 assert.match(css,/height:100dvh!important/);
 assert.match(css,/overflow:hidden!important/);
 assert.match(css,/\.evening-summary-die/);
 assert.match(css,/\.surprise-special-grid/);
 assert.match(css,/surprise-modal-content/);
});
