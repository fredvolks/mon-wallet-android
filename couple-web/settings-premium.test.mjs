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
 assert.match(app,/isLoveAdmin\(\)\?settingGroup\('Administration'/);
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
 const groups=sections.map(section=>{
  const summary={addEventListener(_,callback){this.activate=callback;}};
  return {dataset:{settingsSection:section},open:false,summary,
   querySelector:key=>key==='summary'?summary:null};
 });
 const root={querySelectorAll:()=>groups};
 const module=new Function('root','sessionStorage','console','settingsOpenSection',
  source+'\nreturn {initSettingsAccordion,getOpen:()=>settingsOpenSection};'
 )(root,storage,console,'');
 module.initSettingsAccordion();
 const click=group=>group.summary.activate({preventDefault(){}});
 assert.equal(groups.filter(group=>group.open).length,0);
 click(groups[0]);
 assert.equal(groups[0].open,true);
 assert.equal(saved.get('loveroll-settings-section'),'compte');
 click(groups[1]);
 assert.equal(groups[0].open,false);
 assert.equal(groups[1].open,true);
 assert.equal(groups.filter(group=>group.open).length,1);
 assert.equal(saved.get('loveroll-settings-section'),'couple');
 click(groups[1]);
 assert.equal(groups.filter(group=>group.open).length,0);
 assert.equal(saved.has('loveroll-settings-section'),false);
});

test('All existing settings actions are still wired',()=>{
 for(const name of ['profile','signout','save-couple-date','end-couple',
  'admin-reset-all-dice','native-update','reload-web','save-wellbeing']){
  assert.ok(app.includes("if(verb==='"+name+"')"),name);
 }
 for(const name of ['tab:library','tab:monthly-library','tab:advent-editor',
  'tab:special-editor','tab:journal','native-legacy']){
  assert.ok(app.includes("'"+name+"'"),name);
 }
});

test('Settings theme is scoped and loaded last with cache revision',()=>{
 assert.match(index,/settings-premium\.css\?v=20261015-invite71/);
 assert.match(index,/app\.js\?v=20261015-invite71/);
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

test('Couple menu removes fictional preview and directs the invitation CTA to the real invite screen',()=>{
 const settings=app.slice(app.indexOf('function renderSettings(){'),app.indexOf('async function refreshAndShow()',app.indexOf('function renderSettings(){')));
 const waiting=app.slice(app.indexOf('function renderWaitingPartner(){'),app.indexOf('function renderHome(){'));
 assert.match(settings,/Inviter un conjoint ou une conjointe/);
 assert.match(settings,/button\('start-couple'/);
 assert.doesNotMatch(settings,/button\('demo-enter'/);
 assert.doesNotMatch(waiting,/button\('demo-enter'/);
 assert.match(app,/function renderPartnerInvitation\(\)/);
 assert.match(app,/if\(state\.tab==='invitation'&&state\.coupleId\)renderPartnerInvitation\(\)/);
 assert.match(app,/button\('tab:invitation','♡ Inviter mon partenaire'\)/);
 const action=app.slice(app.indexOf("if(verb==='start-couple'){"),app.indexOf("if(verb==='end-couple'){"));
 assert.match(action,/rpc\('create_love_match',\{p_anniversary_md:null\}\)/);
 assert.match(action,/state\.tab='invitation'/);
 assert.match(action,/await reload\(\)/);
});
