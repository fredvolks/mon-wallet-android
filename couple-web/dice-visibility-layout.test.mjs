import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {premiumEveningHtml} from './premium-evening.mjs';
import {premiumHomeHtml} from './premium-home.mjs';
const read=p=>readFileSync(new URL('./'+p,import.meta.url),'utf8');
const homeCss=read('premium-home.css'),eveningCss=read('premium-couple-pages.css'),app=read('app.js'),engine=read('dice3d.js');
test('Home D6 stays centered inside the satin theater, clear of the roll button',()=>{
 const css=homeCss.slice(homeCss.lastIndexOf('/* S25 Ultra: center the real WebGL die'));
 assert.match(css,/\.home-die-platform\{[\s\S]*?transform:translate3d\(0,-18%,0\)/);
 const page=premiumHomeHtml({weekly:{face:1},members:[{name:'Moi'},{name:'Partenaire'}]});
 const theater=page.indexOf('class="home-die-theater"'),canvas=page.indexOf('canvas class="dice-canvas"',theater);
 const control=page.indexOf('class="home-roll-area"',theater);
 assert.ok(theater>=0&&canvas>theater&&control>canvas);
 assert.match(page,/data-dice-sides="6"/);
 assert.match(page,/data-face="1"/);
});
test('Before any evening D6 roll the TWO dice are shown above their buttons',()=>{
 const people=[{user_id:'a',name:'Camille'},{user_id:'b',name:'Noémie'}];
 const html=premiumEveningHtml({session:{id:'session'},members:people,me:'a',rolls:[],status:{},turn:{}});
 const start=html.indexOf('class="evening-d6-grid"'),end=html.indexOf('class="evening-ready"',start);
 assert.ok(start>=0&&end>start);
 const step=html.slice(start,end);
 assert.equal((step.match(/data-dice-unrolled="true"/g)||[]).length,2);
 assert.equal((step.match(/data-dice-markers="hearts"/g)||[]).length,2);
 assert.ok(step.indexOf('canvas class="dice-canvas"')<step.indexOf('data-action="my-d6"'));
 assert.match(step,/data-action="my-d6"/);
 assert.doesNotMatch(step,/aria-label="Total actuel \d+ défis/);
 assert.match(eveningCss,/\.love3d-stage\.is-unrolled \.dice-canvas\{[\s\S]*?visibility:visible!important/);
 assert.doesNotMatch(app,/canvas\.dataset\.diceUnrolled!=='true'/);
});
test('Decorative 3D D6 has identical heart on every face, not fake official pips',()=>{
 assert.match(engine,/const neutral=canvas\.dataset\.diceUnrolled==='true'&&sides===6/);
 assert.match(engine,/if\(neutral\)\{[\s\S]*?fillText\('♥',192,198\)/);
 assert.match(engine,/map:digits\(value,sides===6,pips,neutral\)/);
 const people=[{user_id:'a',name:'Camille'},{user_id:'b',name:'Noémie'}];
 const page=premiumEveningHtml({session:{id:'session'},members:people,me:'a',
  rolls:[{user_id:'a',face:4}],status:{my_rolled:true},turn:{ready:false}});
 assert.match(page,/data-face="4"/);
 assert.equal((page.match(/data-dice-unrolled="true"/g)||[]).length,1);
 assert.match(page,/aria-label="Total actuel 4 défis; dé initial 4"/);
});
