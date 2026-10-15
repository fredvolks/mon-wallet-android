import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {discoverHtml} from './discover.mjs';
import {authHtml} from './onboarding.mjs';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
test('Invitation and public welcome have their intended actions',()=>{
 const invitation=discoverHtml(true);
 assert.match(invitation,/data-action="invite-start"/);
 assert.doesNotMatch(invitation,/data-action="show-auth:signin"/);
 const welcome=discoverHtml(false);
 assert.match(welcome,/data-action="show-auth:signup"/);
 assert.match(welcome,/data-action="show-auth:signin"/);
});
test('Login, signup and recovery preserve in-app forms and actions',()=>{
 for(const [mode,action] of [['signin','auth-signin-password'],['signup','auth-signup-password'],
  ['request-reset','auth-send-reset'],['recover','auth-update-password']]){
  const html=authHtml(true,mode);
  assert.match(html,new RegExp('data-action="'+action+'"'));
  assert.match(html,/invite-auth-card/);
  assert.match(html,/data-action="invite-return"/);
  if(mode==='signin'||mode==='signup')assert.match(html,/data-action="google"/);
 }
 assert.match(authHtml(true,'signin','', '',true),/data-action="auth-resend-confirmation"/);
});
test('v76 invitation/auth layout has fixed viewport and focus handling',()=>{
 const css=read('mobile-entry-v76.css');
 assert.match(css,/main#app:has\(\.discover-v59\)/);
 assert.match(css,/main#app:has\(\.invite-account-screen:not\(\.invite-profile-screen\)\)/);
 assert.match(css,/height:100dvh!important/);
 assert.match(css,/overflow:hidden!important/);
 assert.match(css,/\.discover-v59 \.discover-editorial-stage\{/);
 assert.match(css,/\.discover-v59 \.discover-accept\{/);
 assert.match(css,/\.invite-account-screen:not\(\.invite-profile-screen\) \.invite-auth-submit\{/);
 assert.match(css,/max-height:420px/);
 assert.match(css,/:has\(input:focus\)/);
 const html=read('index.html');
 assert.match(html,/mobile-entry-v76\.css\?v=20261015-invite76/);
 assert.ok(html.lastIndexOf('mobile-entry-v76.css')>html.lastIndexOf('mobile-one-screen-v74.css'));
 const sw=read('service-worker.js');
 assert.match(sw,/loveroll-static-v76/);
 assert.match(sw,/\.\/mobile-entry-v76\.css/);
});
