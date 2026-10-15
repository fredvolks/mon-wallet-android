import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=f=>readFileSync(new URL('./'+f,import.meta.url),'utf8');
const app=read('app.js'),markup=read('premium-dialogs.mjs'),styles=read('premium-dialogs.css'),html=read('index.html'),sw=read('service-worker.js');
test('All eight old confirm() dialogs replaced; mutations still await explicit approval',()=>{
 const actions=['reset_love_all_dice_admin','openLegacyOffline','import_love_evening_library',
 'reset_love_monthly_activity','delete_love_custom_card','end_love_match','give_love_challenge','couple_intimacy'];
 assert.doesNotMatch(app,/(?:^|[^a-zA-Z])confirm\s*\(/m);
 assert.doesNotMatch(app,/(?:^|[^a-zA-Z])prompt\s*\(/m);
 assert.ok((app.match(/await premiumConfirm\(/g)||[]).length>=8);
 for(const action of actions)assert.ok(app.includes(action),action);
 const gift=app.slice(app.indexOf("if(verb==='give-challenge')"),app.indexOf("if(verb==='new-challenge')"));
 assert.ok(gift.indexOf("await premiumConfirm")<gift.indexOf("rpc('give_love_challenge'"));
 const unlink=app.slice(app.indexOf("if(verb==='end-couple')"),app.indexOf("if(verb==='save-wellbeing')"));
 assert.ok(unlink.indexOf("await premiumConfirm")<unlink.indexOf("rpc('end_love_match'"));
});
test('Modal promises always resolve on cancel/accept/Escape, without any backend mutation',()=>{
 assert.match(markup,/role="alertdialog"/);
 assert.match(markup,/aria-modal="true"/);
 assert.match(markup,/document\.addEventListener\('keydown'/);
 assert.match(markup,/event\.key==='Escape'/);
 assert.match(markup,/close\(false\)/);
 assert.match(markup,/close\(true\)/);
 assert.match(markup,/previous\?\.isConnected/);
 assert.doesNotMatch(markup,/client\.rpc\(|fetch\(/i);
 assert.match(markup,/textContent=message/);
});
test('Gift and dissociation are styled as premium accessible actions, not browser dialogs',()=>{
 for(const kind of ['gift','couple','reset','library','delete','legacy'])
  assert.ok(markup.includes(kind+':{title:'),kind);
 assert.match(markup,/Offrir 1 défi \?/);
 assert.match(markup,/Garder notre couple/);
 assert.match(markup,/activeDialog=null/);
 assert.match(styles,/\.lr-dialog-overlay/);
 assert.match(styles,/\.lr-dialog-card/);
 assert.match(styles,/\.lr-dialog-icon/);
 assert.match(styles,/\.lr-dialog-accept\.lr-dialog-danger/);
 assert.match(html,/premium-dialogs\.css\?v=20261015-heart80/);
 assert.match(sw,/premium-dialogs\.mjs/);
});
test('Result toast respects device safe area and displays only official number',()=>{
 assert.match(app,/isResult=/);
 assert.match(app,/RÉSULTAT OFFICIEL/);
 assert.match(app,/official\[1\]/);
 assert.match(app,/textContent=msg/);
 assert.match(styles,/env\(safe-area-inset-top,0px\)/);
 assert.match(styles,/#toast\.lr-premium-toast\.visible\{display:flex\}/);
 assert.match(styles,/top:max\(62px/);
 assert.match(styles,/max-width:min\(90vw,430px\)/);
});
