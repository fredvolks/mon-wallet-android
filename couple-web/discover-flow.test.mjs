import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {discoverHtml} from './discover.mjs';
import {authHtml,marketingHtml} from './onboarding.mjs';

const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');
const css=readFileSync(new URL('./discover.css',import.meta.url),'utf8');
const sql=readFileSync(new URL('./migrations/20261014_safe_unpair_portable_library.sql',import.meta.url),'utf8');

test('Discover is a public premium visual tour, not another authentication page',()=>{
 const h=discoverHtml(true);
 for(const value of ['D6','D20','D35','Défis à deux','Dates et surprises','Votre match privé','calendrier de l’Avent','Revenir à mon invitation'])
  assert.ok(h.includes(value),value);
 assert.match(h,/data-action="discover-back"/);
 assert.doesNotMatch(h,/data-action="(show-auth|google|apple|email-login|verify-email-code)"/);
 assert.doesNotMatch(h,/CRÉER MON COMPTE|ME CONNECTER|Continuer avec Google|Continuer avec Apple/);
 assert.match(css,/\.discover-gallery/);
 assert.match(css,/\.discover-back/);
 assert.match(marketingHtml(),/love-onepage/);
});

test('Invitation ACCEPT action is gated before auth in both logged-out and logged-in paths',()=>{
 assert.ok(app.indexOf("if(pendingInvite&&!state.inviteAccepted)")<app.indexOf("if(!state.user){",app.indexOf("function render(){")));
 assert.match(app,/sessionStorage\.setItem\('loverollInviteAccepted',token\)/);
 assert.match(app,/state\.inviteAccepted=sessionStorage\.getItem\('loverollInviteAccepted'\)===invite/);
 assert.match(app,/frame\(invitationLandingHeader\(true\)/);
 assert.match(app,/frame\(authHtml\(!!pendingInvite\)/);
 assert.match(app,/if\(verb==='discover-loveroll'\)/);
 assert.match(app,/if\(verb==='discover-back'\|\|verb==='invite-return'\)/);
 assert.doesNotMatch(authHtml(true),/data-action="show-landing"/);
 assert.match(authHtml(true),/data-action="discover-loveroll"/);
 assert.match(authHtml(true),/data-action="invite-return"/);
});

test('Unpair keeps all challenges and settings in a user-owned archive',()=>{
 assert.match(app,/Dissocier mon partenaire/);
 assert.match(app,/rpc\('end_love_match'/);
 for(const table of [
  'love_custom_challenges','love_monthly_activity_overrides','love_advent_2026_config',
  'love_advent_2026_days','love_special_event_catalogue'])
  assert.match(sql,new RegExp(table));
 assert.match(sql,/snapshot_love_library\(p_couple\)/);
 assert.match(sql,/restore_love_library_for_new_owner/);
 assert.match(sql,/love_user_library_backup_owner/);
 assert.match(sql,/delete from public\.couple_members where couple_id=p_couple/);
 assert.doesNotMatch(sql,/delete from public\.love_custom_challenges/);
 assert.doesNotMatch(sql,/delete from public\.couple_challenges/);
 assert.doesNotMatch(sql,/delete from auth\.users/);
 assert.ok(sql.indexOf('perform couple_private.snapshot_love_library(p_couple);')<
  sql.indexOf('delete from public.couple_members where couple_id=p_couple;'));
});
