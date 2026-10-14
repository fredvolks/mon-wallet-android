import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';

const read=name=>readFileSync(new URL(name,import.meta.url),'utf8');
const app=read('./app.js');
const index=read('./index.html');
const css=read('./settings-premium.css');
const push=read('./push-notifications.mjs');
const native=read('../app/src/main/java/ca/desadeux/jeu/ConnectedLoveRollActivity.java');

test('Eight settings groups remain present and collapsed unless remembered',()=>{
 for(const label of ['Compte','Couple','Notifications','Jeu et personnalisation',
  'Administration','Application','Confidentialité','Aide'])
  assert.ok(app.includes("settingGroup('"+label+"'"),label);
 assert.match(app,/adminWeekly\|\|'<p class="settings-limited">/);
 assert.match(app,/sessionStorage\.getItem\('loveroll-settings-section'\)/);
});

test('Opening a settings section closes the previous one and saves state',()=>{
 const start=app.indexOf('function initSettingsAccordion(){');
 const end=app.indexOf('\nfunction renderSettings(){',start);
 assert.ok(start!==-1&&end>start);
 const source=app.slice(start,end);
 const saved=new Map();
 const storage={setItem:(key,value)=>saved.set(key,value),
  removeItem:key=>saved.delete(key)};
 const sections=['compte','couple','notifications','jeu','admin','application','confidentialite','aide'];
 const groups=sections.map(section=>({dataset:{settingsSection:section},open:false,
  addEventListener(_,listener){this.onToggle=listener;}}));
 const root={querySelectorAll:()=>groups};
 const module=new Function('root','sessionStorage','console','settingsOpenSection',
  source+'\nreturn {initSettingsAccordion,getOpen:()=>settingsOpenSection};'
 )(root,storage,console,'');
 module.initSettingsAccordion();
 assert.equal(groups.filter(group=>group.open).length,0);
 groups[0].open=true;groups[0].onToggle();
 assert.equal(saved.get('loveroll-settings-section'),'compte');
 groups[1].open=true;groups[1].onToggle();
 assert.equal(groups[0].open,false);
 assert.equal(saved.get('loveroll-settings-section'),'couple');
 groups[0].onToggle();
 assert.equal(saved.get('loveroll-settings-section'),'couple');
 groups[1].open=false;groups[1].onToggle();
 assert.equal(saved.has('loveroll-settings-section'),false);
});

test('All existing settings actions are still wired',()=>{
 for(const name of ['profile','signout','save-couple-date','end-couple',
  'admin-reset-weekly','native-update','reload-web','save-wellbeing']){
  assert.ok(app.includes("if(verb==='"+name+"')"),name);
 }
 for(const name of ['tab:library','tab:monthly-library','tab:advent-editor',
  'tab:special-editor','tab:journal','native-legacy']){
  assert.ok(app.includes("'"+name+"'"),name);
 }
});

test('Settings theme is scoped and loaded last with cache revision',()=>{
 assert.match(index,/settings-premium\.css\?v=20261014-settings36/);
 assert.match(index,/app\.js\?v=20261014-settings36/);
 assert.match(css,/main#app\.loveroll-settings-page/);
 assert.match(css,/grid-template-columns:minmax\(0,1fr\)!important/);
 assert.match(css,/\.settings-group-icon/);
 assert.match(css,/\.settings-link-row/);
 assert.match(css,/\.settings-actions/);
 assert.match(css,/\.nav button\.active/);
});

test('Native version is real and unavailable notifications are not marked as on',()=>{
 assert.match(native,/getPackageManager\(\)\.getPackageInfo\(getPackageName\(\),0\)/);
 assert.match(app,/window\.LoveRollNativeAuth\.appVersion\(\)/);
 assert.match(push,/available&&prefs\[key\]!==false/);
 assert.match(push,/\(!available\?' disabled':''\)/);
 assert.match(push,/weeklyReminderEnabled/);
});
