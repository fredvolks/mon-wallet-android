import { marketingHtml,authHtml,profileHtml,inviteProfileHtml,inviteWellbeingHtml,wellbeingHtml,wellbeingFields,bindWellbeingMenus,matchHtml,parseMonthDay,monthDayInputs } from './onboarding.mjs';
import { discoverHtml } from './discover.mjs';
import {premiumHomeHtml,nextMonday} from './premium-home.mjs?v=20261015-invite62';
import {premiumEveningHtml} from './premium-evening.mjs?v=20261015-invite62';
import {premiumSurpriseHtml} from './premium-surprise.mjs?v=20261015-invite62';
import {loginEmail,loginCode,emailAuthMessage,requireLovePassword,passwordQuality,verifiedLoveEmail,passwordAuthMessage,invitationAuthMessage} from './email-auth.mjs';
import {demoCoupleHtml,DEMO_TABS} from './demo-couple.mjs';
import {inviteLinkForId,parseInvitationFromUrl,pendingInviteReference,INVITATION_REVISION} from './invite-links.mjs';
import {sharedEveningTurns,lastOfficialChallengeFace,latestCountedSession} from './evening-turns.mjs';
import { eveningCard, EVENING_CATEGORIES, formatCountdown,customCardSlots,editableCardSlots,playableEveningCount,resolvedCard } from './evening-challenges.mjs';
import {MONTHLY_ACTIVITIES,editableMonthlyActivities,monthlyActivityForRoll} from './monthly-activities.mjs';
import {validateAdventJson,calendarFromSaved} from './advent-calendar.mjs';
import {adventPremiumHtml,sharedAdventState} from './premium-advent.mjs?v=20261015-invite62';
import {validateSpecialEventsJson,SPECIAL_EVENT_KEYS,privateSpecialKey} from './private-special-events.mjs';
import {buildEveningLibrary,buildMonthlyLibrary,librarySummary,normalizeLibraryData,MAX_IMPORT_BYTES} from './json-libraries.mjs';
import {jsonToolsPanel,jsonInputId,exportJsonFile} from './json-library-ui.mjs';
import {guidedIdeas,guidanceFor,RELATIONSHIP_OPTIONS,INTIMACY_OPTIONS} from './wellbeing.mjs';

import { SPECIAL_SURPRISES,OCCASIONS,occasionDate,dueOccasions,specialSurprise,occasionRoles,TARGETED_OCCASIONS } from './special-occasions.mjs';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.57.0';
import { CONFIG } from './config.js';
import {normalizeInviteEmail,composeInviteEmail} from './invite-email.mjs';
import {enablePush,disablePush,updatePushPreference,sendTestPush,notificationSettingsMarkup,safeNativePushToken} from './push-notifications.mjs';
import {shouldOfferInstall,markInstallOffered,dismissInstall,requestInstall,installOfferHtml,installStepsHtml,installStatus,isEmbeddedBrowser,isIos} from './home-install.mjs';
import {messengerExternalBrowserHtml} from './messenger-browser.mjs';
import {invitationStatus,coupleStats,formatCoupleDate,invitationEmailText,invitationMessengerText,invitationPreviewMarkup} from './couple-dashboard.mjs';
import {premiumConfirm,premiumCopyLink,focusInlineLoveDialog} from './premium-dialogs.mjs?v=20261015-invite62';


const WEB_RELEASE='2026-10-15.62';
const root=document.querySelector('#app'),toastEl=document.querySelector('#toast');
const enc=new TextEncoder(),dec=new TextDecoder();
let diceCleanup=[],renderToken=0;
const opts={auth:{flowType:'pkce',autoRefreshToken:true,persistSession:true,detectSessionInUrl:true}};
const client=CONFIG.supabaseUrl&&CONFIG.supabasePublishableKey?
 createClient(CONFIG.supabaseUrl,CONFIG.supabasePublishableKey,opts):null;
const launchParams=new URLSearchParams(location.search);
const requestedSettingsSection=launchParams.get('section')||'';
let settingsOpenSection=requestedSettingsSection||sessionStorage.getItem('loveroll-settings-section')||'';
const state={user:null,profile:null,members:[],coupleId:null,couple:null,
 tab:launchParams.has('settings')?'settings':(['home','session','special','advent'].includes(launchParams.get('open'))?launchParams.get('open'):'home'),weekly:null,monthly:{},session:null,personal:[],challenges:[],gifts:[],rollStatus:null,customCards:[],customCategory:'SENSUEL',customSlot:1,monthlyActivities:[],monthlySlot:1,
 events:[],journal:[],feedback:[],advent:[],advent2026Config:null,advent2026Days:[],selectedDay:1,adventYear:null,adventSelected:null,adventHistory:false,adventOpening:null,adventServerDate:null,unlockKey:null,channel:null,
 occasionSettings:null,occasionRolls:[],specialEventsConfig:null,specialEventKeys:[],wellbeing:null,guidance:'playful',personalJournal:[],personalPass:null,showAuth:launchParams.has('auth'),authNotice:null,authMode:['signin','request-reset'].includes(launchParams.get('auth'))?launchParams.get('auth'):'signup',authNeedsEmailConfirmation:false,emailVerificationPending:false,authDraftEmail:'',passwordRecovery:launchParams.get('auth')==='recover',inviteError:null,inviteAttemptKey:null,inviteWelcome:false,homeNotices:false,showDiscover:false,inviteAccepted:false,demoMode:false,demoTab:'home',demoRoll:null,demoSurprise:null,demoDay:1,demoChallengeComplete:false,showEveningChallenge:false,eveningMore:false,showSurpriseActivity:false,specialFocus:null,diceStatus:null,rollAnimationFace:null,rollAnimationKind:null,category:null,randomMode:false,chosenSession:null,confirmCancel:false,showInstallSteps:false,installAfterSignup:false,installModalOpen:false,forceInstallGuide:false,invitations:[],inviteDraft:null,inviteCopyFallback:false,challengesHistory:[],jsonPending:null,specialTimerKey:null,pushPrefs:null};
const names={SENSUEL:'Sensuel',COQUIN:'Coquin',SEXUEL:'Sexuel'};
function isSharedCoupleReady(coupleId,members){
 return !!coupleId&&Array.isArray(members)&&members.length===2;
}
let timerCtx=null;
const challengeTimer={id:null,durationMs:0,remainingMs:0,deadline:0,running:false,notified:false,mode:'sound'};
try{const saved=localStorage.getItem('loveroll-timer-mode');if(saved==='vibrate'||saved==='sound')challengeTimer.mode=saved;}catch(e){}

const kinds={weekly:'Rituel de la semaine',monthly_activity:'Sortie du mois',
 monthly_host:'Qui invite',monthly_restaurant:'Type de restaurant'};
const ideas={
 SENSUEL:['Baiser prolongé','Massage relaxant','Danse collée','Trois compliments','Baisers dans le cou','Câlin prolongé','Souvenir romantique','Baiser au choix','Massage des mains','Regard complice','Promenade main dans la main','Moment sans écran','Surprise tendre','Écoute à deux','Baiser surprise','Chanson romantique','Massage du dos','Déclaration douce','Lumière tamisée','Choix de douceur'],
 COQUIN:['Danse privée','Baiser à la carte','Jeu du regard','Massage coquin','Petit strip-tease','Défi du vêtement','Une envie secrète','Séduction au ralenti','Bandeau et baisers','Invitation discrète','Un compliment osé','Massage à l’huile','Une chanson à deux','Rôle de séduction','Cinq baisers','Un petit fantasme','Défi de la surprise','Ambiance de soirée','Deux choix coquins','Un jeu à deux'],
 SEXUEL:['Moment intime','Préliminaires préférés','Proposition de sexe oral','Position choisie ensemble','Plaisir partagé','Douche intime','Prendre l’initiative','Moment sans pénétration','Jouet intime consensuel','Changer de pièce','Partager un fantasme','L’autre choisit','Inverser les rôles','Changer l’éclairage','Bandeau intime','Une nouveauté consensuelle','Rythme préféré','Désir secret','Carte blanche encadrée','Finale à deux']
};
const advent=[
 'Dix minutes de tendresse','Massage relaxant','Trois baisers','Danse lente',
 'Petite attention','Baisers dans le cou','Souvenir à deux','Danse de séduction',
 'Chuchotement coquin','Strip-tease léger','Jeu de tentation','Douche à deux',
 'Massage sensuel','Confession d’une envie','Préliminaires à choisir','Choix d’un jouet',
 'Invitation intime','Attention sexuelle consensuelle','Soirée passionnée',
 'Strip-tease et surprise','Changer de décor','Plaisir partagé','Fantasme à raconter',
 'Soirée intime choisie','Cadeau final à deux'
];
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'America/Toronto',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const month=()=>today().slice(0,7);
const week=()=>{const d=new Date(today()+'T12:00:00Z');let n=(d.getUTCDay()+6)%7;d.setUTCDate(d.getUTCDate()-n);return d.toISOString().slice(0,10)};
const safe=v=>String(v??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const id=v=>document.getElementById(v);
const other=()=>state.members.find(m=>m.user_id!==state.user?.id);
const me=()=>state.members.find(m=>m.user_id===state.user?.id);
const person=u=>state.members.find(m=>m.user_id===u)?.name||'Partenaire';
const button=(action,label,extra='')=>'<button '+extra+' data-action="'+safe(action)+'">'+label+'</button>';
function inviteBrowserHelp(){
 const invite=pendingInviteReference(sessionStorage);
 if(!isEmbeddedBrowser()||!invite)return '';
 const base=CONFIG.siteUrl||location.origin+location.pathname;
 const href=invite.kind==='id'?inviteLinkForId(base,invite.value):
  base+'?v='+INVITATION_REVISION+'#invite='+encodeURIComponent(invite.value);
 return messengerExternalBrowserHtml(href,{ios:isIos()});
}
function invitationLandingHeader(showAction=true){
 return '<section class="premium-invite-hero" aria-label="Votre invitation LoveRoll">'+
 '<div class="premium-invite-photo"><img src="./assets/loveroll-premium-scene.png" alt="Cœur rose lumineux et deux dés noirs LoveRoll" loading="eager"></div>'+
 '<div class="premium-eyebrow">♡ LOVEROLL · INVITATION PRIVÉE</div>'+
 '<h2>Une surprise rien que pour vous deux ❤️</h2>'+
 '<p>Partagez des moments de complicité, des défis et de belles surprises à votre rythme.</p>'+
 '<p class="premium-invite-note">♡ Votre invitation est privée · Un compte par personne</p>'+ 
 (showAction?button('invite-start','♡ ACCEPTER L’INVITATION ❤️'):'')+'</section>';
}
function notify(message){
 const msg=String(message??'');
 const isError=/erreur|impossible|refusé|échoué|indisponible|invalide|non confirmé/i.test(msg);
 const isResult=/résultat officiel|résultat.*(?:dé|D6|D35)|tirage de|révélés/i.test(msg);
 toastEl.replaceChildren();
 toastEl.className='lr-premium-toast '+(isError?'lr-toast-error':isResult?'lr-toast-result':'lr-toast-info');
 const official=msg.match(/^Résultat(?: officiel)?\s*:?\s*(\d+)\s*[·-]\s*(.*)$/i);
 const isGift=/défi transféré|défi offert|cadeau/i.test(msg);
 const glyph=document.createElement('span');glyph.className='lr-toast-glyph';
 glyph.textContent=isError?'!':isGift?'🎁':isResult?'🎲':'♡';
 glyph.setAttribute('aria-hidden','true');
 const content=document.createElement('span');content.className='lr-toast-message';
 if(official){
  const title=document.createElement('span');title.className='lr-toast-kicker';
  title.textContent='RÉSULTAT OFFICIEL';
  const line=document.createElement('span');line.className='lr-toast-result-line';
  const value=document.createElement('strong');value.className='lr-toast-result-number';
  value.textContent=official[1];
  const detail=document.createElement('span');detail.textContent=official[2];
  line.append(value,detail);content.append(title,line);
 }else content.textContent=msg;
 const close=document.createElement('button');close.type='button';close.className='lr-toast-close';
 close.textContent='×';close.setAttribute('aria-label','Fermer la notification');
 close.addEventListener('click',()=>{clearTimeout(notify.timeout);toastEl.classList.remove('visible');});
 toastEl.append(glyph,content,close);
 toastEl.classList.add('visible');
 clearTimeout(notify.timeout);
 notify.timeout=setTimeout(()=>toastEl.classList.remove('visible'),isResult?5200:4400);
}
function fail(error){console.error(error);notify(error?.message||'Une erreur est survenue');}
async function rpc(name,args={}){const {data,error}=await client.rpc(name,args);if(error)throw error;return data;}
async function query(table,options={}){
 let q=client.from(table).select(options.columns||'*');
 for(const [column,value] of Object.entries(options.eq||{}))q=q.eq(column,value);
 if(options.order)q=q.order(options.order,{ascending:options.asc??false});
 const {data,error}=await q;if(error)throw error;return data;
}

// Unsaved form text lives ONLY in process memory: no LocalStorage, logs or cloud.
// It is restored after partner realtime refreshes and activity resume.
let lastFormTab=null;
let allowBackExit=false;
function snapshotDraft(){
 const inputs=[...root.querySelectorAll('input[id],select[id],textarea[id]')].filter(el=>
  !el.disabled&&!el.readOnly&&!['password','file','hidden'].includes(el.type));
 return inputs.filter(el=>{
  if(el.type==='checkbox'||el.type==='radio')return el.checked!==el.defaultChecked;
  if(el.tagName==='SELECT'){
   const options=[...el.options],initial=options.findIndex(o=>o.defaultSelected);
   return el.selectedIndex!==(initial<0?0:initial);
  }
  return el.value!==el.defaultValue;
 }).map(el=>({
    id:el.id,value:el.value,checked:el.checked,type:el.type
 }));
}
function hasUnsavedForm(){return snapshotDraft().length>0;}
function restoreDraft(draft){
 for(const field of draft||[]){
  const el=document.getElementById(field.id);
  if(!el||!root.contains(el)||['password','file','hidden'].includes(el.type))continue;
  if(field.type==='checkbox'||field.type==='radio')el.checked=field.checked;
  else el.value=field.value;
 }
}
function backFromLoveRoll(){
 if(document.querySelector('.lr-dialog-overlay')){
  document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));
  return true;
 }
 if(allowBackExit){allowBackExit=false;return false;}
 if(state.showEveningChallenge){state.showEveningChallenge=false;render();return true;}
 if(state.showSurpriseActivity){state.showSurpriseActivity=false;render();return true;}
 if(state.adventSelected!==null){state.adventSelected=null;render();return true;}
 if(state.adventHistory){state.adventHistory=false;render();return true;}
 if(state.eveningMore){state.eveningMore=false;render();return true;}
 if(state.homeNotices){state.homeNotices=false;render();return true;}
 if(state.confirmCancel){state.confirmCancel=false;render();return true;}
 if(state.specialFocus){state.specialFocus=null;state.tab='special';render();return true;}
 const parent={
  library:'settings','monthly-library':'settings','advent-editor':'settings',
  'special-editor':'settings','personal-journal':'settings',journal:'session',
  feedback:'session',occasions:'special',ideas:'home',invitation:'settings'
 }[state.tab];
 const destination=parent||(state.tab!=='home'?'home':null);
 if(destination){
  handle('tab:'+destination).catch(fail);
  return true;
 }
 if(hasUnsavedForm()){
  premiumConfirm('Vous avez une saisie non enregistrée. La quitter sans enregistrer ?',{
   kind:'library',title:'Quitter le formulaire ?',accept:'Quitter',cancel:'Continuer'
  }).then(ok=>{if(ok){allowBackExit=true;notify('Appuyez de nouveau sur Retour pour quitter.');}});
  return true;
 }
 return false;
}
window.loveRollHandleBack=backFromLoveRoll;
document.addEventListener('keydown',event=>{
 if(event.key==='Escape'&&!document.querySelector('.lr-dialog-overlay')){
  if(backFromLoveRoll())event.preventDefault();
 }
});

function frame(body,title='LOVEROLL',subtitle='Un jeu intime, juste pour vous deux'){
 const openerAction=document.activeElement?.dataset?.action||null;
 const draft=lastFormTab===state.tab?snapshotDraft():[];
 lastFormTab=state.tab;
 const connected=!!(state.user&&state.profile?.birthday_md&&state.wellbeing);
 const isCouple=connected&&isSharedCoupleReady(state.coupleId,state.members);
 const realHome=isCouple&&state.tab==='home';
 const realEvening=isCouple&&state.tab==='session'&&(!state.session||(state.session.paired_turns&&state.session.self_draws));
 const realSurprise=isCouple&&state.tab==='special';
 root.classList.toggle('loveroll-advent-page',isCouple&&state.tab==='advent');
 root.classList.toggle('loveroll-real-home',realHome);
 root.classList.toggle('loveroll-evening-page',realEvening);
 root.classList.toggle('loveroll-surprise-page',realSurprise);
 root.classList.toggle('loveroll-settings-page',connected&&state.tab==='settings');
 for(const dispose of diceCleanup)dispose();diceCleanup=[];
 const token=++renderToken;
 // Offer only after completing a NEW account. Never interrupt ordinary logins.
 if(connected&&state.installAfterSignup){
  state.installAfterSignup=false;
  if(shouldOfferInstall(state.user?.id)){
   state.installModalOpen=true;
   markInstallOffered(state.user?.id);
  }
 }
 const installation=connected&&(state.installModalOpen||state.forceInstallGuide)?
  (state.showInstallSteps?installStepsHtml():installOfferHtml()):'';
 const homeHeader='<header class="home-brandbar"><div class="home-brand"><b>♡ Love<em>Roll</em>♡</b>'+
  '<small>LA COMPLICITÉ SE JOUE À DEUX</small></div>'+
  '<div class="home-brand-actions">'+
  '<button type="button" data-action="home-notices" aria-label="Notifications du couple">'+
  '<svg viewBox="0 0 32 32" width="25" height="25" aria-hidden="true"><path d="M10 23V13c0-9 12-9 12 0v10l3 3H7zM13 28c1 3 5 3 6 0" fill="none" stroke="#ffd6e9" stroke-width="2.2" stroke-linejoin="round"/></svg>'+
  (state.events?.length?'<b aria-label="Nouvelles activités"></b>':'')+'</button>'+
  '<button type="button" data-action="tab:settings" aria-label="Ouvrir le profil du couple">'+
  '<svg viewBox="0 0 32 32" width="27" height="27" aria-hidden="true"><circle cx="12" cy="10" r="4" fill="none" stroke="#ffbcdb" stroke-width="2"/><circle cx="23" cy="11" r="3.5" fill="none" stroke="#ffbcdb" stroke-width="2"/><path d="M3 28c0-12 18-12 18 0m0-11c5 1 8 4 8 11" fill="none" stroke="#ffbcdb" stroke-width="2"/></svg>'+
  '</button></div></header>';
 const usualHeader='<header class="header"><div><h1>♡ '+safe(title)+'</h1><p>'+safe(subtitle)+'</p></div>'+
  (state.user&&state.tab!=='settings'?'<button class="subtle" data-action="signout" aria-label="Se déconnecter">↪</button>':'')+'</header>';
 const notifications=realHome&&state.homeNotices?'<div class="home-notification-popover" role="dialog" aria-label="Activités du couple">'+
  '<strong>♡ Activités récentes</strong>'+
  (state.events?.length?state.events.slice(0,4).map(e=>'<p>'+safe(e.title||'Une activité')+' · '+safe(e.happened_at?.slice(0,10)||'')+'</p>').join(''):
  '<p>Aucune notification pour le moment.</p>')+
  '<button data-action="home-notices" class="subtle">Fermer</button></div>':'';
 root.innerHTML=(realHome?homeHeader:usualHeader)+body+notifications+(connected?nav():'')+installation;
 restoreDraft(draft);
 bindActions();
 focusInlineLoveDialog(root,openerAction);
 // Render idle evening D6 in WebGL too: each side is a neutral heart,
 // never a fabricated pip result. Actual face comes from Supabase after rolling.
 const canvases=[...root.querySelectorAll('canvas[data-dice-sides]')];
 if(canvases.length){
  import('./dice3d.js?v=20261015-invite62').then(({dieOn})=>{
   if(token!==renderToken)return;
   for(const canvas of canvases){
    if(!canvas.isConnected)continue;
    try{
     const cleanup=dieOn(canvas,Number(canvas.dataset.diceSides),
       Number(canvas.dataset.face)||1,canvas.dataset.spinning==='true');
     diceCleanup.push(cleanup);
     canvas.closest('.die')?.classList.add('ready');
    }catch(error){console.warn('WebGL non disponible',error);}
   }
  }).catch(error=>console.warn('Animation 3D indisponible, résultat textuel maintenu',error));
 }
}
function nav(){
 const tabs=isSharedCoupleReady(state.coupleId,state.members)?
 [['home','⌂','Accueil'],['session','🎲','Soirée'],['special','✦','Surprise'],
  ['advent','🎁','Avent'],['settings','⚙','Réglages']]:
 [['home','⌂','Accueil'],...(isLoveAdmin()?[['library','♡','Mes cartes']]:[]),
  ['personal-journal','♥','Calendrier'],['settings','⚙','Réglages']];
 return '<nav class="nav"><div class="nav-inner" style="grid-template-columns:repeat('+tabs.length+',minmax(0,1fr))">'+tabs.map(([key,icon,label])=>
 '<button data-action="tab:'+key+'" class="'+((state.tab===key||(key==='special'&&state.tab==='occasions'))?'active':'')+'"><span>'+icon+
 '</span>'+safe(label)+'</button>').join('')+'</div></nav>';
}
function bindActions(){
 const librarySearch=root.querySelector('#custom-card-search');
 const libraryFilter=root.querySelector('#custom-card-filter');
 const updateLibraryFilter=()=>{
  const term=(librarySearch?.value||'').trim().toLocaleLowerCase('fr-CA');
  const choice=libraryFilter?.value||'all';
  root.querySelectorAll('.custom-card-tiles button[data-card-id]').forEach(card=>{
   const matchesText=(card.textContent||'').toLocaleLowerCase('fr-CA').includes(term);
   const matchesKind=choice==='all'||choice==='edited'&&card.dataset.saved==='true'||
    choice==='empty'&&card.dataset.empty==='true';
   card.hidden=!(matchesText&&matchesKind);
  });
 };
 librarySearch?.addEventListener('input',updateLibraryFilter);
 libraryFilter?.addEventListener('change',updateLibraryFilter);
 document.querySelectorAll('[data-action]').forEach(el=>{
  el.addEventListener('click',()=>handle(el.dataset.action,el).catch(fail));
 });
 document.querySelectorAll('input[data-json-file]').forEach(input=>input.addEventListener('change',async()=>{
  const file=input.files?.[0];if(!file)return;
  if(file.size>MAX_IMPORT_BYTES){fail(Error('Fichier trop volumineux : 250 Ko maximum.'));return;}
  const output=id(jsonInputId(input.dataset.jsonFile));if(!output)return;
  try{output.value=await file.text();state.jsonPending=null;notify('Fichier chargé. Vérifiez son aperçu.');}catch(e){fail(e);}
 }));
 document.querySelectorAll('[data-push-option]').forEach(input=>input.addEventListener('change',async()=>{
  const type=input.dataset.pushOption;
  try{
   await updatePushPreference(client,state.user.id,state.pushPrefs||{},type,input.checked);
   state.pushPrefs={...state.pushPrefs,[type]:input.checked};
   notify('Préférence enregistrée.');
  }catch(err){input.checked=!input.checked;fail(err);}
 }));
 document.querySelector('#invite-auth-form')?.addEventListener('submit',event=>event.preventDefault());
 const passwordField=id('signin-password');
 passwordField?.addEventListener('input',()=>{
  const quality=passwordQuality(passwordField.value),meter=document.querySelector('.invite-password-meter');
  if(!meter)return;
  meter.dataset.level=String(quality.met);
  const hint=id('auth-password-hint');
  if(hint)hint.textContent=quality.valid?'✓ Mot de passe prêt':'Au moins 8 caractères, avec une majuscule, une minuscule et un chiffre.';
 });
 bindWellbeingMenus();
}

let inviteAcceptanceBusy=false;
function pendingInviteUrl(recover=false){
 const url=new URL(CONFIG.siteUrl||location.href);
 const pending=pendingInviteReference(sessionStorage);
 if(pending?.kind==='id'){url.searchParams.set('invite_id',pending.value);url.searchParams.set('v',INVITATION_REVISION);}
 if(pending?.kind==='token')url.hash='invite='+pending.value;
 url.searchParams.set('auth',recover?'recover':'account');
 return url.toString();
}
function finishInviteReference(){
 sessionStorage.removeItem('pendingInvite');
 sessionStorage.removeItem('pendingInviteId');
 sessionStorage.removeItem('loverollInviteAccepted');
 const clean=new URL(location.href);
 for(const key of ['invite_id','v','auth','type','token'])clean.searchParams.delete(key);
 clean.hash='';
 history.replaceState(null,'',clean.pathname+clean.search);
 state.inviteAccepted=false;state.inviteError=null;state.inviteAttemptKey=null;
}
async function autoAcceptPendingInvite(){
 const invite=pendingInviteReference(sessionStorage);
 if(!invite||!state.inviteAccepted||!state.user||state.passwordRecovery||
    !state.profile?.birthday_md||!state.wellbeing)return;
 if(!verifiedLoveEmail(state.user)){
  state.inviteError='Confirmez votre adresse courriel pour terminer l’invitation.';return;
 }
 const key=state.user.id+':'+invite.kind+':'+invite.value;
 if(inviteAcceptanceBusy||state.inviteAttemptKey===key)return;
 state.inviteAttemptKey=key;inviteAcceptanceBusy=true;state.inviteError=null;
 try{
  if(invite.kind==='id')await rpc('accept_love_match_by_id',{p_invitation:invite.value});
  else await rpc('accept_love_match',{p_token_hash:await hashToken(invite.value)});
  finishInviteReference();
  state.inviteWelcome=true;state.tab='home';state.showAuth=false;
 }catch(error){state.inviteError=invitationAuthMessage(error);}
 finally{inviteAcceptanceBusy=false;}
}
function verificationPendingHtml(email){
 // A successful signUp/resend request does not prove delivery. Supabase may
 // deliberately return success for an address already registered with Google.
 const needsSignIn=!state.user;
 return '<div class="invite-account-screen email-confirm-screen">'+
 '<header class="invite-account-hero"><div class="invite-auth-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>'+
 '<p>LA COMPLICITÉ SE JOUE À DEUX</p></header>'+
 '<section class="invite-auth-card"><div class="email-confirm-envelope" aria-hidden="true">✉ ♡</div>'+
 '<h1>♡ Encore une petite étape !</h1>'+
 '<p>'+(needsSignIn?'Vérifiez votre adresse ou connectez-vous à votre compte existant.':
   'Votre courriel doit être confirmé pour entrer dans LoveRoll.')+'</p>'+
 '<p class="email-confirm-recipient">'+safe(email||'Votre adresse courriel')+'</p>'+
 '<p>'+(needsSignIn?
   'Si vous avez déjà utilisé Google avec cette adresse, aucun nouveau courriel ne sera nécessaire.':
   'Ouvrez le lien de confirmation reçu dans votre boîte courriel.')+'</p>'+
 '<p class="invite-small-note">Pour un nouveau compte, vérifiez aussi les indésirables. Un renvoi accepté ne garantit pas la livraison.</p>'+
 '<div id="email-confirm-status" role="status" aria-live="polite"></div>'+
 (needsSignIn?'<button type="button" data-action="google" class="invite-google"><span class="invite-google-mark" aria-hidden="true">G</span> Continuer avec Google</button>'+
  button('email-confirm-login','Se connecter avec un compte existant','class="subtle"'):
  button('email-confirm-recheck','J’ai confirmé mon courriel'))+
 button('email-confirm-resend','Renvoyer le courriel','class="subtle"')+
 button('email-confirm-edit','Modifier mon adresse','class="subtle"')+
 '</section></div>';
}
async function refreshVerifiedIdentity(){
 if(!state.user)return false;
 const {data,error}=await client.auth.getUser();
 if(error||!data?.user){state.user=null;return false;}
 state.user=data.user;
 return verifiedLoveEmail(data.user);
}
function authMessage(message){
 const feedback=id('auth-form-feedback');
 if(feedback){feedback.textContent=message;feedback.setAttribute('role','alert');}
 else{state.authNotice=message;notify(message);}
}
function render(){
 if(!client){frame('<section class="card"><h2>Connexion cloud à configurer</h2>'+
  '<p>Un projet Supabase dédié et son adresse HTTPS sont requis avant d’activer les comptes et le partage.</p></section>');return;}
 const pendingInvite=pendingInviteReference(sessionStorage);
 // Discover always shows a visual tour, never a second sign-in page.
 // The approved premium discovery IS the first invitation screen.
 // Consent is mandatory before the connection controls appear.
 if(!state.passwordRecovery&&(state.showDiscover||(pendingInvite&&!state.inviteAccepted))){
  root.innerHTML=discoverHtml(!!pendingInvite,inviteBrowserHelp());bindActions();return;
 }
 if(!state.passwordRecovery&&((state.emailVerificationPending&&!state.user)||
  (state.user&&!verifiedLoveEmail(state.user)))){
  root.innerHTML=verificationPendingHtml(state.authDraftEmail||state.user?.email);
  bindActions();return;
 }
 if(!state.user||state.passwordRecovery){
  if(!state.user&&!state.showAuth){root.innerHTML=discoverHtml(false);bindActions();}
  else{
   root.innerHTML=authHtml(!!pendingInvite,state.passwordRecovery?'recover':state.authMode,
    state.authNotice||'',state.authDraftEmail,state.authNeedsEmailConfirmation)+inviteBrowserHelp();
   bindActions();
  }
  return;
 }
 if(pendingInvite&&state.user&&state.coupleId){
  const soloOwner=state.members.length===1&&state.members[0]?.user_id===state.user.id&&
   state.couple?.owner_user_id===state.user.id;
  root.innerHTML='<header class="header"><div><h1>♡ LOVEROLL</h1><p>Votre invitation privée</p></div></header>'+
   '<section class="auth-panel invite-switch-account invite-existing-space">'+
   '<div class="eyebrow">♡ INVITATION ET COMPTE EXISTANT</div>'+
   '<h2>'+(soloOwner?'Votre compte possède déjà un espace LoveRoll ♡':
    'Ce compte fait déjà partie d’un couple ♡')+'</h2>'+
   '<p>Compte connecté : <strong>'+safe(state.user.email||'un compte existant')+'</strong>.</p>'+
   '<p>'+(soloOwner?
    'Cet espace ne contient que vous. Si vous êtes bien le destinataire de l’invitation, vous pouvez rejoindre votre partenaire sans créer un nouveau compte.':
    'Ce compte est déjà associé à un autre couple. Un changement automatique risquerait d’affecter votre partenaire actuel.')+'</p>'+
   (soloOwner?
    '<p class="invite-archive-note">Vos cartes personnelles et votre historique intime sont archivés avant le changement. Les cartes de l’ancien espace ne sont pas ajoutées au nouveau couple.</p>'+
    button('join-invite-from-solo','♡ REJOINDRE LE COUPLE INVITÉ'):'')+
   (state.inviteError?'<p class="invite-auth-feedback" role="alert">'+safe(state.inviteError)+'</p>':'')+
   button('switch-account-invite','Utiliser un autre compte','class="subtle"')+
   button('invite-return','← Revoir l’invitation','class="subtle"')+'</section>';
  bindActions();return;
 }
 if(!state.profile||!state.profile.birthday_md){
  if(pendingInvite&&state.inviteAccepted){root.innerHTML=inviteProfileHtml(state.user,state.profile);bindActions();}
  else frame(profileHtml(state.user,state.profile),'♡ MON PROFIL','Première connexion : inscription');
  return;
 }
 if(!state.wellbeing){
  if(pendingInvite&&state.inviteAccepted){root.innerHTML=inviteWellbeingHtml(null,state.user);bindActions();}
  else frame(wellbeingHtml(null,state.user),'♡ MON RESSENTI','Vos réponses sont personnelles');
  return;
 }
 if(state.inviteWelcome){
  root.innerHTML='<div class="invite-welcome" role="status"><div>♡</div><h1>Bienvenue dans LoveRoll</h1>'+
   '<p>Votre espace à deux est prêt !</p></div>';
  state.inviteWelcome=false;
  setTimeout(()=>{state.tab='home';render();},1300);return;
 }
 if(pendingInvite&&state.inviteError&&!state.coupleId){
  root.innerHTML='<div class="invite-account-screen"><section class="invite-auth-card invite-error-card">'+
   '<h1>Invitation à vérifier ♡</h1><p>'+safe(state.inviteError)+'</p>'+
   '<p>Compte connecté : '+safe(state.user.email||'')+'</p>'+
   button('switch-account-invite','Utiliser un autre compte')+
   button('retry-invite','Réessayer','class="subtle"')+
   button('invite-return','← Revoir l’invitation','class="subtle"')+'</section></div>';
  bindActions();return;
 }
 if(state.demoMode){
  for(const dispose of diceCleanup)dispose();diceCleanup=[];
  root.innerHTML=demoCoupleHtml({userName:state.profile.display_name,
   tab:state.demoTab,roll:state.demoRoll,surprise:state.demoSurprise,
   day:state.demoDay,challengeComplete:state.demoChallengeComplete});
  bindActions();return;
 }
 // No demonstration games without two linked accounts. The creator still
 // owns the private card editor and personal journal before any invitation.
 if(!isSharedCoupleReady(state.coupleId,state.members)){
  if(!state.coupleId&&pendingInvite){
   root.innerHTML='<div class="invite-account-screen"><section class="invite-auth-card invite-error-card">'+
    '<h1>Votre invitation ♡</h1><p>'+safe(state.inviteError||'Association en attente.')+'</p>'+
    button('retry-invite','Réessayer l’association')+
    button('switch-account-invite','Utiliser un autre compte','class="subtle"')+'</section></div>';
   bindActions();return;
  }
  if(state.tab==='invitation'&&state.coupleId)renderPartnerInvitation();
  else if(state.tab==='settings')renderSettings();
  else if(state.tab==='personal-journal')renderPersonalJournal().catch(fail);
  else if(state.tab==='library'&&isLoveAdmin())renderCardLibrary();
  else if(state.tab==='monthly-library'&&isLoveAdmin())renderMonthlyLibrary();
  else if(state.tab==='advent-editor'&&isLoveAdmin())renderAdventEditor();
  else if(state.tab==='special-editor'&&isLoveAdmin())renderSpecialEventsEditor();
  else if(!state.coupleId){
   state.tab='home';
   frame(matchHtml(state.profile.display_name,false),
    '♡ NOTRE MATCH','Préparer vos cartes avant de jouer à deux');
  }else{
   state.tab='home';renderWaitingPartner();
  }
  return;
 }
 if(state.tab==='home')renderHome();
 else if(state.tab==='personal-journal')renderPersonalJournal().catch(fail);
 else if(state.tab==='ideas')renderIdeas();
 else if(state.tab==='session')renderSession();
 else if(state.tab==='special')renderSpecial();
 else if(state.tab==='occasions')renderOccasions();
 else if(state.tab==='advent')renderAdvent();
 else if(state.tab==='advent-editor')renderAdventEditor();
 else if(state.tab==='special-editor')renderSpecialEventsEditor();
 else if(state.tab==='journal')renderJournal().catch(fail);
 else if(state.tab==='feedback')renderFeedback().catch(fail);
 else if(state.tab==='settings')renderSettings();
 else if(state.tab==='library')renderCardLibrary();
 else if(state.tab==='monthly-library')renderMonthlyLibrary();
}
async function reload(){
 if(!state.user)return;
 if(!(await refreshVerifiedIdentity())){
  state.profile=null;state.coupleId=null;state.members=[];state.wellbeing=null;
  state.personalJournal=[];state.pushPrefs=null;
  state.emailVerificationPending=true;state.showAuth=true;
  render();return;
 }
 state.emailVerificationPending=false;
 const [profiles,assessments,personal,pushSettings]=await Promise.all([
  query('couple_profiles',{eq:{id:state.user.id}}),
  query('love_wellbeing',{eq:{user_id:state.user.id}}),
  query('love_personal_intimacy',{eq:{user_id:state.user.id}}),
  query('love_push_preferences',{eq:{user_id:state.user.id}})
 ]);
 state.pushPrefs=pushSettings[0]||{enabled:false,weekly:true,invitation:true,monthly:true,special:true};
 const nativeToken=safeNativePushToken();
 if(state.pushPrefs.enabled&&nativeToken){
  client.rpc('love_claim_native_push_token',{p_token:nativeToken})
   .then(({error})=>{if(error)console.warn('Enregistrement FCM impossible',error.code||'');});
 }

 state.profile=profiles[0]||null;
 state.wellbeing=assessments[0]||null;
 state.personalJournal=personal;
 await autoAcceptPendingInvite();
 const membership=await query('couple_members',{eq:{user_id:state.user.id}});
 const previousCouple=state.coupleId;
 state.coupleId=membership[0]?.couple_id||null;
 if(!state.coupleId){
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  state.couple=null;state.members=[];state.weekly=null;state.monthly={};state.customCards=[];state.monthlyActivities=[];state.rollStatus=null;state.gifts=[];
  state.session=null;state.sessions=[];state.events=[];state.journal=[];
  state.feedback=[];state.advent=[];state.adventYear=null;state.adventSelected=null;state.adventServerDate=null;state.advent2026Config=null;state.advent2026Days=[];state.specialEventsConfig=null;state.specialEventKeys=[];state.unlockKey=null;
  state.invitations=[];state.inviteDraft=null;state.challengesHistory=[];
  state.tab=['settings','personal-journal'].includes(state.tab)?state.tab:'home';
  state.guidance=state.wellbeing?await rpc('get_love_guidance',{}):'gentle';
  render();return;
 }
 if(previousCouple&&previousCouple!==state.coupleId)state.unlockKey=null;
 const [couple,members,invitations]=await Promise.all([
  query('couples',{eq:{id:state.coupleId}}),
  query('couple_members',{eq:{couple_id:state.coupleId}}),
  query('couple_invitations',{eq:{couple_id:state.coupleId},order:'created_at'})
 ]);
 state.invitations=invitations;
 state.couple=couple[0]||null;
 if(state.couple?.owner_user_id===state.user.id){
  const [cards,activities,adventConfig,adventDays,specialSettings]=await Promise.all([
   query('love_custom_challenges',{eq:{couple_id:state.coupleId}}),
   query('love_monthly_activity_overrides',{eq:{couple_id:state.coupleId}}),
   query('love_advent_2026_config',{eq:{couple_id:state.coupleId}}),
   query('love_advent_2026_days',{eq:{couple_id:state.coupleId}}),
   query('love_special_event_catalogue',{eq:{couple_id:state.coupleId}})
  ]);
  state.customCards=cards;state.monthlyActivities=activities;
  state.advent2026Config=adventConfig[0]||null;state.advent2026Days=adventDays;
  state.specialEventsConfig=specialSettings[0]?.events||null;
 }else{
  state.customCards=[];state.monthlyActivities=[];
  state.advent2026Config=null;state.advent2026Days=[];state.specialEventsConfig=null;
 }
 const profilesAll=await query('couple_profiles');
 state.members=members.sort((a,b)=>a.joined_at.localeCompare(b.joined_at)).map(m=>({...m,name:profilesAll.find(p=>p.id===m.user_id)?.display_name||'Profil à compléter'}));
 state.guidance=state.wellbeing?await rpc('get_love_guidance',{p_couple:state.coupleId}):'gentle';
 if(members.length===2)await refreshShared();
 subscribe();render();
}
async function refreshShared(){
 const c=state.coupleId;
 const [rolls,sessions,advents,events,diary,occasionSettings,occasionRolls,keys,challengeHistory,gifts,adventServerDate]=await Promise.all([
 query('couple_rolls',{eq:{couple_id:c}}),
 query('couple_sessions',{eq:{couple_id:c},order:'started_at'}),
 query('couple_advent',{eq:{couple_id:c}}),
 query('couple_events',{eq:{couple_id:c},order:'happened_at'}),
 query('couple_intimacy',{eq:{couple_id:c}}),
 query('couple_occasion_settings',{eq:{couple_id:c}}),
 query('couple_occasion_rolls',{eq:{couple_id:c}}),
 rpc('my_love_special_event_keys',{p_couple:c}),
 query('couple_challenges',{eq:{couple_id:c}}),
 query('couple_challenge_gifts',{eq:{couple_id:c}}),
 rpc('love_advent_server_date')
 ]);
 state.gifts=gifts;
 state.challengesHistory=challengeHistory;
 state.occasionSettings=occasionSettings[0]||null;
 state.occasionRolls=occasionRolls;
 state.specialEventKeys=Array.isArray(keys)?keys:[];
 state.weekly=rolls.find(r=>r.kind==='weekly'&&r.key===week());
 state.monthly={};
 for(const r of rolls.filter(r=>r.key===month()))state.monthly[r.kind]=r;
 state.session=sessions.find(s=>s.finished_at===null)||null;
 state.sessions=sessions;state.events=events;state.journal=diary;state.advent=advents;state.adventServerDate=adventServerDate;
 if(state.session){
  const [personal,challenges,status]=await Promise.all([
    query('couple_personal_rolls',{eq:{session_id:state.session.id}}),
    query('couple_challenges',{eq:{session_id:state.session.id},order:'created_at'}),
    rpc('my_love_roll_status',{p_session:state.session.id})
  ]);
  state.personal=personal;state.challenges=challenges;state.rollStatus=status?.[0]||null;
 }else{state.personal=[];state.challenges=[];state.rollStatus=null;}
 state.feedback=await query('couple_feedback',{eq:{couple_id:c}});
 // Only counts are revealed to the invited partner; titles remain owner-private.
 try{
  const totals=await rpc('my_love_evening_card_totals',{p_couple:c});
  state.playableCounts=Array.isArray(totals)?totals[0]||null:null;
 }catch(error){state.playableCounts=null;}
 const lastCompleted=[...sessions].filter(x=>x.finished_at&&x.counted)
  .sort((a,b)=>Date.parse(b.finished_at)-Date.parse(a.finished_at))[0];
 state.feedbackStatus=null;
 if(lastCompleted){
  try{
   const items=await rpc('my_love_feedback_status',{p_session:lastCompleted.id});
   state.feedbackStatus={sessionId:lastCompleted.id,items:Array.isArray(items)?items:[]};
  }catch(error){console.warn('Statut des avis indisponible',error.code||'');}
 }
}
function subscribe(){
 if(!state.coupleId)return;
 if(state.channel)client.removeChannel(state.channel);
 let channel=client.channel('shared-couple-'+state.coupleId);
 channel=channel.on('postgres_changes',{event:'*',schema:'public',table:'couple_members',
  filter:'couple_id=eq.'+state.coupleId},async()=>{
   try{await reload();if(state.coupleId&&state.members.length===2)notify('♡ Votre partenaire a accepté le match !');}catch(e){fail(e);}
 });
 if(state.members.length<2){state.channel=channel.subscribe();return;}
 for(const table of ['couple_occasion_rolls','couple_occasion_settings','couple_rolls','couple_sessions','couple_challenges','couple_challenge_gifts',
  'couple_advent','couple_feedback','couple_intimacy','couple_events'])
  channel=channel.on('postgres_changes',{event:'*',schema:'public',table,
   filter:'couple_id=eq.'+state.coupleId},async payload=>{
    if(payload.new?.actor_id&&payload.new.actor_id!==state.user.id)
     notify(person(payload.new.actor_id)+' a effectué une action dans votre jeu');
    try{await refreshShared();render();}catch(e){fail(e);}
   });
 channel=channel.on('postgres_changes',{event:'INSERT',schema:'public',
  table:'couple_personal_rolls'},async()=>{try{await refreshShared();render();}catch(e){fail(e);}});
 state.channel=channel.subscribe();
}
function heroDice(face,sides,label){
 return '<div class="dice-row premium-dice-row"><div class="die premium-die premium-die-'+sides+'"><strong class="dice-fallback">'+safe(face||'?')+'</strong><canvas class="dice-canvas" width="190" height="190"'+
  ' data-dice-sides="'+sides+'" data-face="'+(face||1)+'" data-spinning="'+(!!state.diceStatus)+'"'+
  ' aria-label="Dé '+sides+' en 3D. Résultat '+(face||'pas encore tiré')+'"></canvas></div>'+
 '<div class="premium-die-caption"><div class="eyebrow">'+safe(label)+'</div>'+
 '<p>'+(!face?'En attente du tirage officiel.':'Résultat officiel : '+face)+
 '.</p></div></div>';
}
function statsRow(){return '<div class="stat-grid">'+[
 [state.weekly?.face||'—','Soirées semaine'],
 [state.personal.find(r=>r.user_id===state.user?.id)?.face||'—','Mes défis'],
 [state.personal.find(r=>r.user_id===other()?.user_id)?.face||'—',safe(other()?.name||'Partenaire')]
 ].map(([v,label])=>'<div class="stat"><strong>'+safe(v)+'</strong><small>'+label+'</small></div>').join('')+'</div>';}
function ideaHtml(){
 const content=guidedIdeas(state.guidance);
 return '<section class="card"><div class="eyebrow">Des idées selon vos envies</div><h2>'+safe(content.heading)+'</h2>'+
 '<p>Ces propositions tiennent compte du ressenti du couple sans dévoiler les réponses individuelles. Chacun peut dire non ou proposer autre chose.</p>'+
 content.ideas.map(([title,detail])=>'<div class="muted-line"><strong>'+safe(title)+'</strong><p>'+safe(detail)+'</p></div>').join('')+'</section>';
}
function ideaPreview(){
 const g=guidedIdeas(state.guidance);
 return '<section class="card"><div class="eyebrow">Votre inspiration</div>'+
 '<h3>♡ '+safe(g.heading)+'</h3>'+
 '<p>Des propositions adaptées, sans pression et toujours facultatives.</p>'+
 button('tab:ideas','Découvrir nos idées de soirées')+'</section>';
}
function renderIdeas(){
 frame(ideaHtml()+'<section class="card">'+button('tab:home','← Retour à l’accueil','class="subtle"')+'</section>',
  'NOS IDÉES DE SOIRÉE','Complicité et intimité selon votre rythme');
}
function renderInvitePanel(){
 const latest=state.invitations?.find(x=>!x.cancelled_at&&!x.accepted_at&&new Date(x.expires_at)>new Date())||
  state.invitations?.[0];
 const ready=state.members?.length===2;
 const status=invitationStatus(latest,ready);
 const active=latest&&!ready&&!latest.cancelled_at&&!latest.accepted_at&&
  new Date(latest.expires_at)>new Date();
 const draft=active?{id:latest.id,email:latest.invited_email,
  link:inviteLinkForId(CONFIG.siteUrl,latest.id)}:null;
 let html='<section class="card"><div class="eyebrow">VOTRE INVITATION</div><h3>♡ Statut de la demande</h3>'+
  '<div class="invite-status"><strong>'+safe(status.title)+'</strong><p>'+safe(status.hint)+'</p></div>';
 if(latest)html+='<p class="tiny-note">Destinataire : '+safe(latest.invited_email)+
  ' · Préparée le '+safe(new Date(latest.created_at).toLocaleDateString('fr-CA'))+'</p>';
 if(!ready){
  html+='<label>Adresse courriel du compte de votre partenaire<input id="invite-email" type="email" autocomplete="email" placeholder="partenaire@exemple.com" value="'+safe(draft?.email||latest?.invited_email||'')+'"></label>'+
   '<p class="tiny-note">Cette adresse sert à vérifier que la bonne personne accepte, même si vous partagez le lien par texto.</p>'+
   (active?
    '<p class="tiny-note">Votre lien d’invitation demeure valable et peut être renvoyé sans être remplacé.</p>'+
    button('invite','Remplacer le lien actuel','class="subtle"'):
    button('invite','Créer mon invitation'));
  if(active||!latest){
   html+='<div class="invite-actions">'+
    button('send-messenger','♡ Messenger')+
    button('send-sms','💬 Texto SMS','class="subtle"')+
    button('send-email','✉ Courriel','class="subtle"')+
    (active?button('copy-invite','Copier le lien','class="subtle"'):'')+'</div>';
  }
  if(latest&&!active)html+='<p class="tiny-note">Cette invitation est expirée, annulée ou déjà acceptée. Créez un nouveau lien seulement si le couple n’est pas encore associé.</p>';
  if(state.inviteCopyFallback&&draft)html+='<label class="invite-copy-manual">Lien à sélectionner et copier<input id="invite-manual-link" readonly aria-label="Lien d’invitation à copier" value="'+safe(draft.link)+'"></label>';
  if(draft)html+='<details class="invite-preview"><summary>♡ Aperçu de votre invitation</summary>'+
    invitationPreviewMarkup()+'</details>'+
    button('confirm-invite-sent','✓ J’ai envoyé l’invitation','class="subtle"')+
    '<p class="tiny-note">Choisissez Messenger, SMS ou courriel dans votre application, puis confirmez l’envoi ici.</p>';
  else html+='<p class="tiny-note">Le partage utilise le lien valide existant, s’il y en a un. Seul « Remplacer le lien actuel » invalide le précédent. Aucun message n’est envoyé sans votre confirmation.</p>';
  if(active)
   html+=button('cancel-invite:'+latest.id,'Annuler cette invitation','class="subtle danger-action"');
 }
 return html+'</section>';
}
function renderPartnerInvitation(){
 if(!state.coupleId){state.tab='settings';renderSettings();return;}
 frame('<section class="card invitation-intro"><div class="eyebrow">♡ NOTRE INVITATION</div>'+
  '<p>Invitez votre conjoint ou votre conjointe avec un lien privé via Messenger ou courriel.</p></section>'+
  renderInvitePanel()+
  '<section class="card">'+button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>',
  'INVITER MON PARTENAIRE','Votre invitation privée LoveRoll');
}
function renderCoupleOverview(){
 const started=state.occasionSettings?.relationship_started_on;
 const cs=coupleStats(state.sessions||[],state.challengesHistory||[]);
 const currentMonth=month();
 const intimate=state.journal.filter(e=>e.happened_on.startsWith(currentMonth)).length;
 return '<section class="card"><div class="eyebrow">♡ VOTRE COUPLE</div>'+
 '<h3>Notre histoire en chiffres</h3>'+
 '<p>En couple depuis le <strong>'+safe(formatCoupleDate(started))+'</strong></p>'+
 
 '<div class="stat-grid"><div class="stat"><strong>'+safe(cs.percentage===null?'—':cs.percentage+' %')+'</strong><small>Soirées terminées</small></div>'+
 '<div class="stat"><strong>'+cs.completed+' / '+cs.opened+'</strong><small>Soirées jouées</small></div>'+
 '<div class="stat"><strong>'+cs.challengesDone+'</strong><small>Défis réalisés</small></div></div>'+
 '<div class="couple-progress"><span style="width:'+safe(cs.percentage||0)+'%"></span></div>'+
 '<p class="tiny-note">Pourcentage = soirées entièrement terminées / soirées commencées.</p>'+
 '<div class="stat-grid"><div class="stat"><strong>'+intimate+'</strong><small>Rapports enregistrés ce mois</small></div>'+
 '<div class="stat"><strong>'+state.journal.length+'</strong><small>Rapports consignés au total</small></div></div>'+
 '<p class="tiny-note">Chiffres issus de votre journal de couple chiffré. Les notes détaillées restent privées.</p>'+
 button('tab:journal','Ouvrir nos statistiques et le calendrier','class="subtle"')+'</section>';
}
function renderWaitingPartner(){
 const name=safe(state.profile.display_name);
 const latest=state.invitations?.find(x=>!x.cancelled_at)||state.invitations?.[0];
 const invited=!!latest;
 frame('<div class="waiting-hero"><div class="waiting-heart">♡</div>'+
  '<div class="eyebrow">♡ VOTRE UNIVERS LOVEROLL</div>'+
  '<h2>Votre espace est prêt, '+name+' !</h2>'+
  '<p>Il ne reste qu’à inviter votre partenaire pour commencer vos premiers jeux à deux.</p>'+
  '<div class="waiting-avatars"><span>♥ '+name+'</span><span class="waiting-connection">·····</span><span>♡ Partenaire</span></div>'+
  '<div class="waiting-status">● '+(invited?'Invitation à compléter':'En attente de votre partenaire')+'</div>'+
  button('tab:invitation','♡ Inviter mon partenaire')+'</div>'+
  '<section class="card"><div class="eyebrow">LES 3 ÉTAPES</div>'+
  '<div class="waiting-steps"><p><strong>✓ 1.</strong> Votre espace personnel est créé</p>'+
  '<p><strong>2.</strong> Partager l’invitation par Messenger, SMS ou courriel</p>'+
  '<p><strong>3.</strong> Dès qu’elle est acceptée, vos dés et soirées seront activés</p></div></section>'+
  '<section class="card"><h3>♡ À préparer pendant l’attente</h3>'+
  '<div class="waiting-actions">'+
  button('tab:library','♡ Nos 70 défis')+
  button('tab:monthly-library','✦ 20 activités du mois','class="subtle"')+
  button('tab:special-editor','💘 Événements spéciaux','class="subtle"')+
  button('tab:advent-editor','🎁 Calendrier de l’Avent','class="subtle"')+'</div>'+
  '<p class="tiny-note">Importez ou exportez chaque bibliothèque en JSON pour renouveler vos idées.</p></section>'+
  renderInvitePanel(),'MON ESPACE','Votre aventure à deux commence ici');
}
function renderHome(){
 const members=state.members||[];
 const turn=state.session&&state.session.self_draws?
  sharedEveningTurns(state.session,members,state.personal,state.challenges,state.gifts):null;
 frame(premiumHomeHtml({
  weekly:state.weekly,
  rolling:state.diceStatus==='rolling',
  disabled:!!state.diceStatus,
  members,
  turn,monthly:state.monthly,
  history:state.challengesHistory||[],
  sessions:state.sessions||[],
  rollFace:state.rollAnimationFace||1,
  events:state.events||[]
 }),'LOVEROLL','La complicité se joue à deux');
}

function activeTimer(challenge,minutes){
 const millis=minutes*60000;
 if(challengeTimer.id!==challenge.id){
  challengeTimer.id=challenge.id;
  challengeTimer.durationMs=millis;
  challengeTimer.remainingMs=millis;
  challengeTimer.deadline=0;
  challengeTimer.running=false;
  challengeTimer.notified=false;
 }
}
function clockMs(){
 return challengeTimer.running?Math.max(0,challengeTimer.deadline-Date.now()):challengeTimer.remainingMs;
}
function endSignal(){
 if(challengeTimer.notified)return;
 challengeTimer.notified=true;
 if(challengeTimer.mode==='vibrate'){
  if(typeof navigator.vibrate==='function')navigator.vibrate([190,90,190,90,360]);
  else notify("Le minuteur est terminé. La vibration n'est pas offerte sur cet iPhone : utilisez Son.");
 }else{
  try{
   const context=timerCtx||new (window.AudioContext||window.webkitAudioContext)();
   timerCtx=context;
   context.resume().catch(()=>{});
   for(const [idx,pitch] of [392,494,587].entries()){
    const osc=context.createOscillator(),gain=context.createGain();
    osc.type='sine';osc.frequency.value=pitch;
    osc.connect(gain);gain.connect(context.destination);
    const when=context.currentTime+idx*.25;
    gain.gain.setValueAtTime(.0001,when);
    gain.gain.exponentialRampToValueAtTime(.13,when+.035);
    gain.gain.exponentialRampToValueAtTime(.0001,when+.6);
    osc.start(when);osc.stop(when+.61);
   }
  }catch(e){console.warn('Son indisponible',e);}
 }
 notify('♡ Minuteur terminé. À vous de décider si le défi continue.');
}
function updateChallengeClock(){
 if(!challengeTimer.id)return;
 if(challengeTimer.running&&Date.now()>=challengeTimer.deadline){
  challengeTimer.running=false;challengeTimer.remainingMs=0;endSignal();
 }
 const clock=document.getElementById('challenge-timer-clock');
 if(clock)clock.textContent=formatCountdown(clockMs());
 const button=document.getElementById('challenge-timer-toggle');
 if(button)button.textContent=challengeTimer.running?'Ⅱ Pause':'▶ '+(challengeTimer.remainingMs<challengeTimer.durationMs?'Reprendre':'Démarrer');
 const status=document.getElementById('challenge-timer-status');
 if(status)status.textContent=challengeTimer.running?'En cours':challengeTimer.remainingMs===0?'Terminé':'À votre rythme';
}
setInterval(updateChallengeClock,500);
function timerWidget(challenge,minutes){
 if(minutes===null||minutes===undefined||minutes===0)return '<p class="tiny-note">♡ Sans minuteur · l’activité se termine quand vous le souhaitez.</p>';
 activeTimer(challenge,minutes);
 return '<div class="timer-panel"><div class="eyebrow">⏱ Minuteur du défi · '+minutes+' min</div>'+
   '<div id="challenge-timer-clock" class="timer-clock">'+formatCountdown(clockMs())+'</div>'+
   '<p id="challenge-timer-status">'+(challengeTimer.running?'En cours':'À votre rythme')+'</p>'+
   '<div class="actions"><button id="challenge-timer-toggle" data-action="timer-toggle">'+
    (challengeTimer.running?'Ⅱ Pause':'▶ Démarrer')+'</button>'+
   button('timer-reset','↻ Recommencer','class="subtle"')+'</div>'+
   '<div class="chipline" style="margin-top:9px">'+
   '<button class="pill '+(challengeTimer.mode==='sound'?'selected':'')+'" data-action="timer-mode:sound">♫ Son doux</button>'+
   '<button class="pill '+(challengeTimer.mode==='vibrate'?'selected':'')+'" data-action="timer-mode:vibrate">〰 Vibration</button></div>'+
   '<small>Sonnerie discrète en fin de compte à rebours; sur iPhone, la vibration peut être indisponible. Le minuteur n’oblige jamais à poursuivre.</small></div>';
}
function renderLegacySession(){
 let s=state.session; const p=other();
 let body='<section class="premium-intro"><div class="premium-eyebrow">🎲 NOTRE SOIRÉE À DEUX</div>'+
 '<h2>Les défis de notre soirée</h2><p>70 défis à découvrir ensemble, parmi nos deux univers de 35 idées.</p></section>'+
 '<section class="card hero-game-card"><div class="premium-eyebrow">♡ LE DÉ DES DÉFIS</div>'+
 heroDice(null,35,'Nos 70 défis')+
 '<p class="tiny-note">Pour commencer, chacun lance son dé personnel pour découvrir combien de défis il recevra. Vos résultats sont révélés ensemble.</p>';
 if(!s){
  const latest=latestCountedSession(state.sessions);
  if(latest){
   const total=state.challengesHistory.filter(c=>c.session_id===latest.id);
   const done=total.filter(c=>c.status==='completed').length;
   const responses=state.feedback.filter(f=>f.session_id===latest.id).length;
   body+='<div class="aftercare-card"><h2>♥ Soirée terminée</h2>'+
    '<p>Un moment partagé à deux. Prenez quelques instants pour donner votre avis.</p>'+
    '<div class="stat-grid"><div class="stat"><strong>'+done+'</strong><small>Défis réalisés</small></div>'+
    '<div class="stat"><strong>'+responses+'/2</strong><small>Avis enregistrés</small></div></div>'+
    button('choose-feedback:'+latest.id,responses===2?'Voir nos avis ❤️':'Répondre à mes 3 questions')+
    button('tab:journal','Voir notre calendrier','class="subtle"')+'</div>';
  }
  body+=button('new-session','Commencer une nouvelle soirée');
 }
 else{
  const mine=state.personal.find(x=>x.user_id===state.user.id);
  const theirs=state.personal.find(x=>x.user_id===p.user_id);
  body+='<div class="premium-eyebrow">ÉTAPE 1 · COMBIEN DE DÉFIS ?</div>'+heroDice(mine?.face,6,'Mes défis à recevoir')+
  '<div class="two"><div class="stat"><strong>'+safe(mine?.face||'—')+'</strong><small>'+safe(state.profile.display_name)+'</small></div>'+
  '<div class="stat"><strong>'+safe(theirs?.face||'—')+'</strong><small>'+safe(p.name)+'</small></div></div>';
  if(!state.rollStatus?.both_ready){
   body+='<section class="card"><h3>Une révélation à deux</h3>'+
    '<p>Mon lancer : '+(state.rollStatus?.my_rolled?'✓ Lancé · résultat secret':'Pas encore lancé')+'</p>'+
    '<p>Son lancer · '+safe(p.name)+' : '+(state.rollStatus?.partner_rolled?'✓ Lancé · résultat secret':'En attente')+'</p>';
   if(!state.rollStatus?.my_rolled)body+=button('my-d6','🎲 LANCER MON DÉ');
   else body+='<p>Tu as joué. Le compte à rebours ne démarre pas : attends le lancer de '+safe(p.name)+'.</p>';
   body+='</section>';
  }
  else{
   const proposed=state.challenges.filter(x=>x.recipient===state.user.id);
   const active=proposed.filter(x=>['proposed','accepted'].includes(x.status)).at(-1);
   const completed=proposed.filter(x=>x.status==='completed').length;
   body+='<p>Mes défis terminés : '+completed+' / '+mine.face+
    ' · '+safe(p.name)+' donne mes défis.</p>';
   if(active){
    const challenge=resolvedCard(active);
    body+='<div class="card challenge-present"><div class="eyebrow">'+safe(names[active.category])+
     ' · CARTE N° '+safe(active.face)+'</div>'+heroDice(active.face,35,'Notre défi surprise')+
     '<div class="challenge-roles">♡ '+safe(p.name).toUpperCase()+' OFFRE → '+safe(state.profile.display_name).toUpperCase()+' REÇOIT</div>'+
     '<h3>'+safe(challenge.title)+'</h3>'+
     '<p>'+safe(challenge.description)+'</p>' +
     timerWidget(active,challenge.minutes)+
     '<div class="actions">'+
     (active.status==='proposed'?button('answer:'+active.id+':accepted','Accepter'):'')+
     (active.status==='accepted'?button('answer:'+active.id+':completed','Terminé'):'')+
     button('answer:'+active.id+':passed','Passer')+'</div></div>';
   }else if(completed<mine.face)body+='<p>Votre partenaire prépare le prochain défi surprise à vous offrir.</p>';
   const otherActive=state.challenges.filter(x=>x.recipient===p.user_id&&['proposed','accepted'].includes(x.status)).at(-1);
   const otherCompleted=state.challenges.filter(x=>x.recipient===p.user_id&&x.status==='completed').length;
   if(!otherActive&&otherCompleted<theirs.face){
    body+='<div class="card"><h3>Défi '+(otherCompleted+1)+' / '+theirs.face+
     ' à offrir à '+safe(p.name)+'</h3>'+
     '<p>Choisissez votre catégorie. LoveRoll sélectionnera le défi parmi nos 70 idées.</p>'+
     '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
     '<button class="pill '+(state.category===c?'selected':'')+'" data-action="category:'+c+'">'+names[c]+'</button>').join('')+'</div>'+
     '<p>88 % dans votre catégorie choisie · 12 % dans l’autre. Choisissez à chaque défi.</p>'+
     (EVENING_CATEGORIES.includes(state.category)?
      button('new-challenge','🎲 DÉCOUVRIR UN DÉFI POUR '+safe(p.name)):
      '<p class="tiny-note">Sélectionnez Sensuel ou Sexuel pour activer le tirage.</p>')+'</div>';
   }else if(otherActive){const giving=resolvedCard(otherActive);
    body+='<section class="card"><div class="challenge-roles">♡ '+safe(state.profile.display_name).toUpperCase()+' OFFRE → '+safe(p.name).toUpperCase()+' REÇOIT</div>'+
    '<h3>Mon défi à offrir : '+safe(giving.title)+'</h3><p>'+safe(giving.description)+'</p>'+
    (active?'<p class="tiny-note">Minuteur et contrôle principal accessibles dans le défi reçu.</p>':
     timerWidget(otherActive,giving.minutes))+'</section>';
   }
  }
  const myTarget=mine?.face||0,otherTarget=theirs?.face||0;
  const mineCompleted=state.challenges.filter(x=>x.recipient===state.user.id&&x.status==='completed').length;
  const theirCompleted=state.challenges.filter(x=>x.recipient===p.user_id&&x.status==='completed').length;
  const canFinish=myTarget>0&&otherTarget>0&&mineCompleted>=myTarget&&theirCompleted>=otherTarget;
  if(canFinish)body+=button('finish-session','✓ Terminer et compter la soirée');
  else body+='<p>La soirée sera comptée seulement quand les défis des deux personnes seront terminés.</p>';
  if(state.confirmCancel){
   body+='<div class="card"><h3>Arrêter sans compter ?</h3>'+
    '<p>La soirée sera fermée sans modifier la progression hebdomadaire.</p>'+
    '<div class="actions">'+button('confirm-cancel','Oui, arrêter')+
    button('dismiss-cancel','Continuer','class="subtle"')+'</div></div>';
  }else body+=button('cancel-session','Arrêter sans la compter','class="subtle"');
 }
 body+='</section>';
 body+='<section class="card"><h3>♡ Nos calendriers</h3>'+
  button('tab:journal','Ouvrir notre calendrier partagé','class="subtle"')+
  button('tab:personal-journal','Ouvrir mon calendrier personnel')+'</section>';
 if(!s&&state.sessions?.some(x=>x.finished_at&&x.counted))body+=renderCoupleOverview();
 frame(body,'VOTRE SOIRÉE',s?'Vos défis à offrir et à recevoir':'Un nouveau moment à deux');
}
function renderSession(){
 // The weekly roll unlocks Soirée. The monthly roll belongs to Surprise.
 // Never enter the evening/game UI without the server-confirmed weekly roll.
 if(!state.weekly){
  const locked='<section class="evening-locked-view" aria-label="Soirée en attente du dé hebdomadaire">'+
   '<div class="evening-locked-brand">♡ Love<em>Roll</em> ♡</div>'+
   '<div class="evening-locked-eyebrow">♡ SOIRÉE DE DÉFIS</div>'+
   '<div class="evening-locked-notice" role="status">♡ &nbsp; Brassez d’abord le dé hebdomadaire pour accéder à votre soirée.</div>'+
   '<div class="evening-locked-hero"><div class="evening-locked-dice" aria-hidden="true">🎲 ♡ 🎲</div>'+
   '<h2>Votre soirée vous attend</h2><p>Le tirage hebdomadaire détermine le nombre de défis à réaliser à deux.</p>'+
   button('tab:home','♡ Brasser le dé hebdomadaire','class="evening-locked-cta"')+'</div>'+
   '<div class="evening-locked-previews">'+
   '<div>♡ <strong>Défis</strong><small>À découvrir</small></div>'+
   '<div>♡ <strong>Catégories</strong><small>Sensuel / Sexuel</small></div>'+
   '<div>♡ <strong>Progression</strong><small>Ensemble</small></div></div></section>';
  frame(locked,'NOTRE SOIRÉE','Le dé hebdomadaire déverrouille vos défis.');
  return;
 }
 const session=state.session,partner=other(),uid=state.user.id;
 if(session&&!state.category){
  try{
   const saved=sessionStorage.getItem('loveroll-category-'+session.id);
   if(EVENING_CATEGORIES.includes(saved))state.category=saved;
  }catch{}
 }
 if(session&&(!session.paired_turns||!session.self_draws))return renderLegacySession();
 const turn=session?sharedEveningTurns(session,state.members,state.personal,state.challenges,state.gifts):{};
 const last=[...(state.sessions||[])].filter(s=>s.finished_at&&s.counted).sort((a,b)=>Date.parse(b.finished_at)-Date.parse(a.finished_at))[0];
 const official=last&&state.feedbackStatus?.sessionId===last.id&&Array.isArray(state.feedbackStatus.items)?state.feedbackStatus.items:null;
 const ownSubmitted=official?.find(x=>x.user_id===uid)?.submitted;
 const otherSubmitted=official?.find(x=>x.user_id!==uid)?.submitted;
 const lastEvening=last?{id:last.id,completed:state.challengesHistory.filter(
    c=>c.session_id===last.id&&c.status==='completed').length,
   responses:official?official.filter(x=>x.submitted).length:null,
   myResponse:ownSubmitted??state.feedback.some(f=>f.session_id===last.id&&f.author_id===uid),
   partnerResponse:otherSubmitted??null}:null;
 const gifts=state.gifts||[];
 const giftCount=gifts.filter(g=>g.donor_user_id===uid&&g.month_key?.slice(0,7)===month()).length;
 let overlay='';
 const pending=state.rollAnimationKind==='new-challenge'&&state.diceStatus?null:turn.pending;
 if(session&&pending&&state.showEveningChallenge){
  const card=resolvedCard(pending),receiver=pending.recipient;
  overlay='<div class="surprise-modal" role="dialog" aria-label="Notre défi en cours">'+
   '<article class="surprise-modal-content">'+
   '<div class="eyebrow">♥ '+safe(names[pending.category])+' · carte '+safe(pending.card_id||pending.face)+'/35</div>'+
   '<h2>'+safe(card.title)+'</h2><p>'+safe(card.description)+'</p>'+
   timerWidget(pending,card.minutes)+
   (receiver===uid?'<div class="actions">'+
    (pending.status==='proposed'?button('answer:'+pending.id+':accepted','♡ Accepter ce défi'):'')+
    (pending.status==='accepted'?button('answer:'+pending.id+':completed','✓ Défi réalisé'):'')+
    button('answer:'+pending.id+':passed','Passer ce défi','class="subtle"')+
    '</div>':'<p>En attente de '+safe(partner?.name||'ton partenaire')+'</p>')+
    button('evening-challenge','Fermer','class="subtle"')+
    '</article></div>';
 }
 frame(premiumEveningHtml({
   session,members:state.members,me:uid,rolls:state.personal,status:state.rollStatus,
   turn,category:state.category,giftCount,pending,rolling:!!state.diceStatus,rollFace:state.rollAnimationFace,
   playable:state.playableCounts||(isLoveAdmin()?playableEveningCount(state.customCards):null),
   rollingKind:state.rollAnimationKind,showMore:state.eveningMore,confirmCancel:state.confirmCancel,
   challengeOverlay:overlay,lastEvening,
   lastD35Face:lastOfficialChallengeFace(
    state.session?state.challenges:state.challengesHistory,state.session?.id||last?.id||null)
 }), 'NOTRE SOIRÉE','Un jeu synchronisé sur nos deux comptes');
}
function optionalRolledTimer(roll){
 if(!roll?.timer_minutes)return '';
 const key='special-'+roll.id;
 return state.specialTimerKey===key?timerWidget({id:key},roll.timer_minutes):
  button('special-timer:'+key,'⏱ Minuteur · '+roll.timer_minutes+' min','class="subtle"');
}
function renderSpecial(){
 const original=state.monthly||{},active=original.monthly_activity;
 const rollingKind=state.diceStatus?state.rollAnimationKind:null;
 const monthly={...original};
 const activity=active?monthlyActivityForRoll(active):null;
 if(rollingKind==='monthly_activity')monthly.monthly_activity=null;
 if(rollingKind==='monthly_host')monthly.monthly_host=null;
 frame(premiumSurpriseHtml({
   monthly,members:state.members,settings:state.occasionSettings||{},
   activity,
   timerHtml:state.showSurpriseActivity&&activity?.timer_minutes?
    timerWidget({id:'monthly-'+month()},activity.timer_minutes):'',
   rolling:!!state.diceStatus,rollingKind,rollFace:state.rollAnimationFace,
   showActivity:state.showSurpriseActivity
 }), 'RENDEZ-VOUS SURPRISE','Un mois, un tirage, une expérience à deux');
}
function renderOccasions(){
 const current=today(),year=Number(current.slice(0,4)),settings=state.occasionSettings||{};
 const eligible=dueOccasions(current,settings,state.members),all=[];
 for(const occ of OCCASIONS){
  const owners=occ.key==='birthday'?state.members.map(m=>m.user_id):[null];
  for(const owner of owners){
   const upcoming=[year,year+1].map(y=>({year:y,date:occasionDate(occ.key,y,settings,owner)}))
    .filter(x=>x.date);
   const due=eligible.find(e=>e.key===occ.key&&e.actorId===owner);
   const next=upcoming.find(e=>e.date>=current)||upcoming.at(-1);
   all.push({...occ,actorId:owner,date:due?.date||next?.date||null,
    year:due?.year||next?.year||year,daysLeft:due?.daysLeft??null});
  }
 }
 let body=(state.specialFocus?
  '<section class="occasion-back-top">'+button('occasion-back','← Retour à Surprise','class="subtle"')+'</section>':
  '<section class="premium-intro"><div class="premium-eyebrow">♥ NOS DATES IMPORTANTES</div><h2>Des occasions à célébrer</h2><p>Saint-Valentin, anniversaires et fêtes : chaque date réserve sa propre surprise.</p></section>')+ 
 '<section class="card"><h2>Les dés de nos événements</h2>'+
  '<p>Pour les fêtes classiques, découvrez une surprise parmi 20 idées. Pour vos occasions personnalisées, un lancer choisit l’activité et un second ajoute une touche de surprise.</p>'+
  '<p>Chaque idée est facultative : vous pouvez en discuter, la modifier ou la refuser.</p></section>';
 for(const ev of all.filter(e=>!state.specialFocus||(e.key===state.specialFocus.key&&e.actorId===state.specialFocus.owner)).sort((a,b)=>(a.daysLeft===null?1:0)-(b.daysLeft===null?1:0))){
  const k=ev.key+(ev.actorId?':'+ev.actorId:'');
  const presetKey=privateSpecialKey(ev.key,ev.actorId,state.couple?.owner_user_id);
  const prize=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='surprise');
  const custom=!!presetKey&&state.specialEventKeys.includes(presetKey)&&!prize;
  const activity=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='activity');
  const host=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='host');
  const modifier=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='modifier');
  const displayName=ev.actorId?person(ev.actorId):'';
  const role=occasionRoles(ev,settings,state.members);
  body+='<section class="card occasion-premium-card"><div class="eyebrow">♡ '+safe(ev.title)+
    (displayName?' · '+safe(displayName):'')+'</div>';
  const eventDate=ev.date?new Intl.DateTimeFormat('fr-CA',{
   day:'numeric',month:'long',year:'numeric',timeZone:'UTC'
  }).format(new Date(ev.date+'T12:00:00Z')):'Date non définie';
  body+='<p class="occasion-date">'+safe(eventDate)+
   (ev.daysLeft!==null?' · '+(ev.daysLeft<0?(ev.daysLeft<=-7?'Dernier jour de disponibilité':'Disponible encore '+(7+ev.daysLeft)+' jours'):
    ev.daysLeft===0?'Disponible aujourd’hui':'Dans '+ev.daysLeft+' jours'):'')+'</p>';
  if(!role.shared&&!role.receiver){
   body+='<p class="tag">Personne célébrée non renseignée. L’administrateur la désigne dans Réglages.</p>';
  }else if(role.shared){
   body+='<div class="challenge-roles">♡ DÉFI COMMUN · À ORGANISER ENSEMBLE</div>'+
    '<p>Un rendez-vous plus original que d’habitude, préparé ensemble et jamais imposé.</p>';
  }else{
   body+='<div class="challenge-roles">♡ '+safe(role.giver.name).toUpperCase()+
    ' OFFRE → '+safe(role.receiver.name).toUpperCase()+' REÇOIT</div>';
  }
  if(!ev.date)body+='<p>Ajoutez cette date dans Réglages pour activer le tirage.</p>';
  else if(ev.daysLeft===null)body+='<p>Ouverture 14 jours avant la fête, fermeture 7 jours après. Cela laisse le temps de trouver une gardienne.</p>';
  else if(custom){
   if(!activity){
    if(role.shared||role.receiver)
     body+='<div class="occasion-step-label">ÉTAPE 1 · NOTRE ACTIVITÉ</div>'+
       heroDice(null,6,'Activité surprise')+
       button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':activity','🎲 DÉCOUVRIR L’ACTIVITÉ');
   }else{
    body+=heroDice(activity.face,6,'Activité surprise')+
      '<div class="card"><div class="eyebrow">ACTIVITÉ · FACE '+activity.face+'</div>'+
      '<p>'+safe(activity.special_description||'')+'</p>'+
      '<p class="tiny-note">Une proposition à accepter, modifier ou passer ensemble.</p>'+optionalRolledTimer(activity)+'</div>';
    if(!modifier){
     body+='<div class="occasion-step-label">ÉTAPE 2 · TOUCHE SURPRISE</div>'+
       heroDice(null,6,'Touche surprise')+
       button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':modifier','🎲 RÉVÉLER LA TOUCHE SURPRISE');
    }else{
     body+=heroDice(modifier.face,6,'Touche surprise')+
       '<div class="card"><div class="eyebrow">RÈGLE · FACE '+modifier.face+'</div>'+
       '<p>'+safe(modifier.modifier_text||'')+'</p>'+optionalRolledTimer(modifier)+'</div>';
    }
   }
  }
  else if(!prize){if(role.shared||role.receiver)body+='<div class="occasion-step-label">NOTRE SURPRISE</div>'+
    heroDice(null,20,'Dé de fête')+
    button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':surprise','🎲 RÉVÉLER LA SURPRISE');}
  else{
   const idea=prize.special_title?
    {title:prize.special_title,detail:prize.special_description,level:'Votre surprise du jour'}:
    specialSurprise(prize.face);
   body+=heroDice(prize.face,20,'Résultat officiel')+
     '<h3>'+safe(idea.title)+'</h3><p>'+safe(idea.detail)+'</p>'+
     '<p class="tag">'+safe(idea.level)+' · Tiré par '+safe(person(prize.actor))+'</p>';
   if(prize.special_title){
    if(!modifier){
     body+='<p>Une deuxième surprise reste à révéler : révélez la touche surprise de ce moment.</p>'+
      button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':modifier','🎲 RÉVÉLER LA TOUCHE SURPRISE');
    }else{
     body+=heroDice(modifier.face,6,'Règle supplémentaire')+
      '<div class="card"><div class="eyebrow">SURPRISE · FACE '+modifier.face+'</div>'+
      '<h3>'+safe(modifier.modifier_title||'La règle du moment')+'</h3>'+
      '<p>'+safe(modifier.modifier_text||'')+'</p>'+optionalRolledTimer(modifier)+'</div>';
    }
   }
   if(!host&&role.shared)body+='<p class="tag">Surprise commune · vous l’organisez à deux.</p>';
   else if(!host&&role.receiver)body+='<p class="tag">L’offrant prépare la surprise pour la personne célébrée.</p>';
   else if(host){
    const giver=state.members[host.face<=3?0:1];
    body+='<div class="result">♡ '+safe(giver?.name||'Partenaire')+' organise</div>'+
     '<p>Tirage n° '+host.face+' · Ensemble, vous fixez vos limites, le budget et la gardienne.</p>';
   }
  }
  body+='</section>';
 }
 body+='<section class="card">'+button('tab:settings','⚙ Définir nos dates')+button('occasion-back','← Retour à Surprise','class="subtle"')+'</section>';
 frame(body,'FÊTES ET ANNIVERSAIRES','Un moment LoveRoll pensé pour chaque grande occasion');
}
function portableLibrary(kind){
 if(kind==='evening')return buildEveningLibrary(state.customCategory,state.customCards);
 if(kind==='monthly')return buildMonthlyLibrary(state.monthlyActivities);
 if(kind==='advent')return calendarFromSaved(state.advent2026Config,state.advent2026Days);
 if(kind==='special')return state.specialEventsConfig;
 throw Error('Type de bibliothèque inconnu.');
}
function exportLibraryFile(kind){
 const data=portableLibrary(kind);
 if(!data)throw Error('Aucun contenu enregistré dans cette liste.');
 librarySummary(data,kind);
 const label=kind==='evening'?'soiree-'+state.customCategory.toLowerCase():kind;
 return exportJsonFile('loveroll-'+label+'.json',JSON.stringify(data,null,2));
}
function previewPortableJson(kind){
 const text=id(jsonInputId(kind))?.value||'';
 if(!text.trim())throw Error('Choisissez un fichier JSON ou collez son contenu.');
 if(new Blob([text]).size>MAX_IMPORT_BYTES)throw Error('Le JSON ne doit pas dépasser 250 Ko.');
 let data;
 try{data=JSON.parse(text);}catch{throw Error('Syntaxe JSON invalide (virgules, guillemets, accolades).');}
 normalizeLibraryData(data,kind);
 if(kind==='evening'&&data.category!==state.customCategory)
  throw Error('Choisissez la catégorie '+data.category+' avant cet import.');
 state.jsonPending={kind,data,summary:librarySummary(data,kind)};
}
function renderSpecialEventsEditor(){
 if(!isLoveAdmin()){state.tab='settings';renderSettings();return;}
 const current=state.specialEventsConfig;
 let body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>Mes 6 événements spéciaux</h2>'+
  '<p>Collez ici votre fichier JSON complet pour enregistrer les 6 fêtes, leurs 6 activités et leurs 6 variantes surprises pour chaque fête. Les descriptions seront conservées comme vous les avez écrites.</p>'+
  '<p class="tiny-note">Votre partenaire ne voit pas cette bibliothèque. Il découvre une activité au premier lancer, puis une variation au deuxième.</p>'+
  (current?'<p>✓ Vos 6 événements sont enregistrés.</p>':
    '<p>Aucun calendrier spécial personnalisé enregistré.</p>')+
  '<label>JSON de mes événements spéciaux<textarea id="special-events-json" rows="11"'+
   ' spellcheck="false" placeholder="{ &quot;evenements_speciaux&quot;: { ... } }">'+
   safe(current?JSON.stringify(current,null,2):'')+'</textarea></label>'+
  button('import-special-events','♡ Prévisualiser avant remplacement')+
  '</section>'+jsonToolsPanel('special','6 événements spéciaux',state.jsonPending);
 if(current){
  body+='<section class="card"><h3>Mes surprises préparées</h3>'+
   '<p class="tiny-note">Aperçu réservé à l’administrateur.</p>'+
   '<div class="custom-card-tiles">'+SPECIAL_EVENT_KEYS.map(key=>{
    const item=current.evenements_speciaux[key];
    return '<div class="custom-card-tile"><span class="custom-tile-number">'+
      safe(item.evenement)+'</span><span class="custom-tile-name">'+
      safe('6 activités + 6 règles')+'</span></div>';
   }).join('')+'</div></section>';
 }
 body+='<section class="card">'+button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>';
 frame(body,'MES FÊTES','6 fêtes · deux surprises à révéler');
}
function renderAdvent(){
 // The database's Toronto clock determines the UI's active day; only the
 // authenticated RPC can open a case, after checking server time again.
 const serverDate=state.adventServerDate||today();
 const selected=state.advent.find(r=>r.day===state.adventSelected&&
  r.year===(state.adventYear||Number(serverDate.slice(0,4))));
 frame(adventPremiumHtml({
  openings:state.advent,members:state.members,
  when:new Date(serverDate+'T16:00:00Z'),year:state.adventYear,
  selectedDay:state.adventSelected,
  openingDay:state.adventOpening,showHistory:state.adventHistory,
  timerHtml:selected?.advent_timer_minutes?
   timerWidget({id:'advent-'+selected.year+'-'+selected.day},selected.advent_timer_minutes):''
 }), 'CALENDRIER DE L’AVENT','Votre rituel commun en décembre');
}

function renderAdventEditor(){
 if(!isLoveAdmin()){state.tab='settings';renderSettings();return;}
 const calendar=calendarFromSaved(state.advent2026Config,state.advent2026Days);
 const title='Calendrier de l’Avent 2026';
 let body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>Préparer les 25 cases de décembre</h2>'+
  '<p>Collez ici votre JSON complet (jours 1 à 25) pour remplacer le contenu du calendrier 2026. Le texte de chaque case est conservé exactement, sans reformulation.</p>'+
  '<p class="tiny-note">Vous pouvez importer les cases avant d’inviter votre partenaire. Les défis restent privés, et seul le jour déverrouillé est révélé aux deux comptes.</p>'+
  (calendar?'<p>✓ '+safe(calendar.calendar_title)+' · 25/25 cases enregistrées.</p>':
    '<p>Les 25 cases personnalisées ne sont pas encore enregistrées.</p>')+
  '<label>Votre calendrier JSON<textarea id="advent-2026-json" spellcheck="false" rows="10"'+
  ' placeholder="{ &quot;calendar_title&quot;: &quot;...&quot;, &quot;year&quot;: 2026, &quot;days&quot;: [...] }">'+
   safe(calendar?JSON.stringify(calendar,null,2):'')+'</textarea></label>'+
   button('import-advent-2026','♡ Prévisualiser avant remplacement')+
  '</section>'+jsonToolsPanel('advent','25 cases de l’Avent',state.jsonPending);
 if(calendar){
  const editing=calendar.days.find(x=>x.day===state.adventEditorDay);
  if(editing)body+='<section class="card advent-day-edit" id="advent-day-editor">'+
   '<h3>Modifier le '+editing.day+' décembre</h3>'+
   '<p>Cette modification concerne uniquement le contenu à découvrir. Les cases déjà ouvertes conservent leur surprise officielle.</p>'+
   '<label>Catégorie<input id="advent-day-type" maxlength="90" value="'+safe(editing.type)+'"></label>'+
   '<label>Titre<input id="advent-day-title" maxlength="200" value="'+safe(editing.title)+'"></label>'+
   '<label>Description<textarea id="advent-day-description" maxlength="1500" rows="5">'+safe(editing.description)+'</textarea></label>'+
   '<label>Minuterie facultative (minutes, de 1 à 60)<input id="advent-day-minutes" type="number" min="1" max="60" placeholder="Aucune minuterie" value="'+
    (editing.timer_minutes??'')+'"></label>'+
   button('save-advent-day','♡ Enregistrer uniquement cette journée')+
   button('close-advent-day','Annuler','class="subtle"')+'</section>';
  body+='<section class="card"><h3>Mes 25 journées enregistrées</h3>'+
   '<p class="tiny-note">Ce récapitulatif est privé. Votre partenaire ne voit les consignes qu’en ouvrant les cases autorisées.</p>'+
   '<div class="custom-card-tiles">'+calendar.days.map(entry=>
    '<button type="button" class="custom-card-tile" data-action="advent-editor-day:'+entry.day+'">'+
    '<span class="custom-tile-number">'+String(entry.day).padStart(2,'0')+'</span>'+
    '<span class="custom-tile-name">'+safe(entry.title)+'</span></button>').join('')+
   '</div></section>';
 }
 body+='<section class="card">'+button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>';
 frame(body,title,'Éditeur privé, année 2026');
}
function isoDate(d){return new Date(d).toISOString().slice(0,10);}
const weekStart=d=>{const x=new Date(d+'T12:00:00Z');x.setUTCDate(x.getUTCDate()-(x.getUTCDay()+6)%7);return isoDate(x);};
function formattedStars(r){return r?'★ '.repeat(r)+'☆ '.repeat(5-r):'Non noté';}
async function createCipherKey(passphrase,salt=state.couple?.journal_salt){
 if(!passphrase||passphrase.trim().length<12)throw Error('Utilisez une phrase secrète de 12 caractères ou plus.');
 const base=await crypto.subtle.importKey('raw',enc.encode(passphrase),'PBKDF2',false,['deriveKey']);
 return crypto.subtle.deriveKey({name:'PBKDF2',salt:enc.encode(salt),
  iterations:240000,hash:'SHA-256'},base,{name:'AES-GCM',length:256},false,['encrypt','decrypt']);
}
function encode64(buffer){return btoa(String.fromCharCode(...new Uint8Array(buffer)));}
function decode64(value){return Uint8Array.from(atob(value),x=>x.charCodeAt(0));}
async function encryptJson(object,key=state.unlockKey){
 if(!key)throw Error('Déverrouillez d’abord votre journal.');
 const iv=crypto.getRandomValues(new Uint8Array(12));
 const encrypted=await crypto.subtle.encrypt({name:'AES-GCM',iv},key,
  enc.encode(JSON.stringify(object)));
 return JSON.stringify({v:1,iv:encode64(iv),payload:encode64(encrypted)});
}
async function decryptJson(ciphertext,key=state.unlockKey){
 if(!key)throw Error('Journal verrouillé');
 const sealed=JSON.parse(ciphertext);
 const clear=await crypto.subtle.decrypt({name:'AES-GCM',iv:decode64(sealed.iv)},
  key,decode64(sealed.payload));
 return JSON.parse(dec.decode(clear));
}
function ratingChoice(idName){return '<div class="chipline">'+[1,2,3,4,5].map(v=>
 '<label class="pill"><input type="radio" name="'+idName+'" value="'+v+'" style="width:auto;display:inline;min-height:0;margin:0" '+
 ''+'> '+v+'★</label>').join('')+'</div>';}
function frenchCalendarMonth(yyyyMm){
 const date=new Date(yyyyMm+'-01T12:00:00Z');
 return new Intl.DateTimeFormat('fr-CA',{month:'long',year:'numeric',timeZone:'UTC'}).format(date);
}
const weekdayHeaders=()=>['lun','mar','mer','jeu','ven','sam','dim']
 .map(x=>'<span class="calendar-weekday">'+x+'</span>').join('');
async function renderPersonalJournal(){
 const items=state.personalJournal||[],mo=state.personalCalendarMonth||month(),day=state.personalSelectedDate||today();
 const [y,m]=mo.split('-').map(Number),length=new Date(y,m,0).getDate();
 const offset=(new Date(y,m-1,1).getDay()+6)%7;
 let calendar='<div class="calendar">'+weekdayHeaders()+
  Array.from({length:offset},()=>'<span></span>').join('');
 for(let n=1;n<=length;n++){
  const d=mo+'-'+String(n).padStart(2,'0'),count=items.filter(x=>x.happened_on===d).length;
  calendar+='<button class="'+(count?'marked ':'')+(d===today()?'today ':'')+
   (d===day?'selected':'')+'" data-action="personal-date:'+d+'">'+
   (count?'♥ ':'')+n+'</button>';
 }
 calendar+='</div>';
 let body='<section class="card"><h2>♥ Mon calendrier privé</h2>'+
 '<p>'+items.length+' moment(s) enregistré(s) dans votre historique personnel. Le changement de partenaire n’efface aucune entrée.</p>'+
 '<div class="actions">'+button('personal-month-prev','←','class="subtle"')+
 '<button class="subtle" disabled>'+safe(frenchCalendarMonth(mo))+'</button>'+
 button('personal-month-next','→','class="subtle"')+'</div>'+calendar+'</section>';
 if(!state.personalPass){
  body+='<section class="card"><h3>🔒 Déverrouiller mes notes</h3>'+
   '<p>Les anciennes notes utilisent leur phrase secrète d’origine, même si vous changez de partenaire. Les dates restent visibles.</p>'+
   '<input id="personal-pass" type="password" minlength="12" autocomplete="off" placeholder="Votre phrase secrète">'+
   button('unlock-personal','Déverrouiller mes notes')+'</section>';
 }else{
  body+='<section class="card"><h3>Mes notes</h3>'+
   button('lock-personal','Verrouiller','class="subtle"');
  for(const item of items.filter(x=>x.happened_on===day)){
   let clear=null;
   try{clear=await decryptJson(item.ciphertext,await createCipherKey(state.personalPass,item.journal_salt));}catch{}
   const editing=clear&&state.personalEditId===item.id;
   const options=(values,current)=>values.map(v=>'<option'+(current===v?' selected':'')+'>'+safe(v)+'</option>').join('');
   body+='<div class="muted-line"><strong>'+safe(item.happened_on)+'</strong>'+
    (clear?'<p>Orgasme : '+safe(clear.orgasm)+' · Point fort : '+safe(clear.good)+
    ' · À améliorer : '+safe(clear.weak)+'</p>'+
    (clear.note?'<p>'+safe(clear.note)+'</p>':''):'<p>Impossible de déchiffrer cette entrée avec cette phrase secrète.</p>')+
    (clear&&!editing?button('personal-edit:'+item.id,'Modifier ma note privée','class="subtle"')+
      button('personal-delete:'+item.id,'Supprimer ma note privée','class="subtle"'):'')+
    (editing?'<div class="personal-note-editor"><h4>Modifier uniquement ma copie personnelle</h4>'+
      '<p>Cette modification ne sera pas transmise à votre partenaire.</p>'+
      '<label>Date<input type="date" id="personal-edit-date" value="'+safe(item.happened_on)+'"></label>'+
      '<label>Orgasme<select id="personal-edit-orgasm">'+options(['Non précisé','Les deux','Moi','Mon partenaire','Aucun'],clear.orgasm)+'</select></label>'+
      '<label>Point fort<select id="personal-edit-good">'+options(['Complicité','Tendresse','Communication','Plaisir','Nouveauté'],clear.good)+'</select></label>'+
      '<label>À améliorer<select id="personal-edit-weak">'+options(['Rien','Plus de temps','Plus de douceur','Plus de communication'],clear.weak)+'</select></label>'+
      '<label>Ma note personnelle<textarea maxlength="1200" id="personal-edit-note">'+safe(clear.note||'')+'</textarea></label>'+
      button('personal-save:'+item.id,'Enregistrer ma note privée')+
      button('personal-cancel-edit','Annuler','class="subtle"')+'</div>':'')+'</div>';
  }
  body+='</section>';
 }
 body+='<section class="card">'+button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>';
 frame(body,'MON CALENDRIER','Personnel et invisible pour un nouveau partenaire');
}
async function renderJournal(){
 const now=today(),yr=now.slice(0,4),currentMo=month(),mo=state.calendarMonth||currentMo,ws=week();
 const dates=state.journal.map(x=>x.happened_on);
 const yearCount=dates.filter(x=>x.startsWith(yr)&&x<=now).length;
 const monthCount=dates.filter(x=>x.startsWith(currentMo)&&x<=now).length;
 const weekCount=dates.filter(x=>x>=ws&&x<=now).length;
 let grades=[];
 if(state.unlockKey){
  for(const item of state.feedback){
   try{let entry=await decryptJson(item.ciphertext);
    if(Number(entry.rating)>=1&&Number(entry.rating)<=5)grades.push(Number(entry.rating));
   }catch{ /* keep one unopenable entry from crashing the calendar */ }
  }
 }
 const average=grades.length?(grades.reduce((a,b)=>a+b,0)/grades.length).toFixed(1):'—';
 const [y,m]=mo.split('-').map(Number),length=new Date(y,m,0).getDate();
 const offset=(new Date(y,m-1,1).getDay()+6)%7;
 let cal='<div class="calendar">'+weekdayHeaders()+
  Array.from({length:offset},()=>'<span></span>').join('');
 for(let day=1;day<=length;day++){
  const date=mo+'-'+String(day).padStart(2,'0');
  const count=dates.filter(d=>d===date).length;
  cal+='<button class="'+(count?'marked ':'')+(date===now?'today ':'')+
   (date===(state.selectedDate||now)?'selected':'')+'" data-action="date:'+date+'">'+
   (count?'♥ ':'')+day+'</button>';
 }
 cal+='</div>';
 let body='<section class="card"><div class="eyebrow">Statistiques du couple</div>'+
 '<div class="stat-grid"><div class="stat"><strong>'+yearCount+'</strong><small>Cette année</small></div>'+
 '<div class="stat"><strong>'+monthCount+'</strong><small>Ce mois</small></div>'+
 '<div class="stat"><strong>'+weekCount+'</strong><small>Cette semaine</small></div></div>'+
 '<p class="result">'+average+' / 5 ★</p><p>Note moyenne des questionnaires enregistrés.</p></section>'+
 '<section class="card"><h2>♡ Calendrier des relations</h2>'+
 '<div class="actions">'+button('month-prev','←', 'class="subtle"')+
 '<button disabled class="subtle">'+safe(frenchCalendarMonth(mo))+'</button>'+
 button('month-next','→','class="subtle"')+'</div>'+cal+'</section>';
 if(!state.unlockKey){
  const exists=!!state.couple?.journal_verifier||state.journal.length>0||state.feedback.length>0;
  body+='<section class="card"><h2>🔒 '+(exists?'Déverrouiller notre journal':'Créer la phrase secrète de notre journal')+'</h2>'+
   '<p>'+(exists?'Saisissez votre phrase secrète commune pour accéder aux anciennes données.':
    'Choisissez une phrase secrète de 12 caractères ou plus et partagez-la en personne avec votre partenaire. Ni la phrase ni vos notes en clair ne sont envoyées au serveur.')+'</p>'+
   '<input id="journal-pass" type="password" minlength="12" autocomplete="off" placeholder="Phrase secrète commune">'+
   (exists?'':'<label>Confirmer la phrase secrète<input id="journal-pass-confirm" type="password" minlength="12" autocomplete="off" placeholder="Confirmez la même phrase"></label>')+
   button('unlock',exists?'Déverrouiller':'Créer et ouvrir notre journal')+
   '<p>Conservez cette phrase : une erreur de déchiffrement ne signifie jamais que votre journal est vide.</p></section>';
 }
 else{
  const edited=state.editEventId?state.journal.find(e=>e.id===state.editEventId&&e.author_id===state.user.id):null;
  const initial=edited?await decryptJson(edited.ciphertext):null;
  const selOpt=(choice,field)=>'<option'+(initial?.[field]===choice?' selected':'')+'>'+safe(choice)+'</option>';
  body+='<section class="card"><h3>'+(edited?'✎ Modifier mon entrée partagée':'♥ Enregistrer un moment')+'</h3>'+
   (edited?'<p>Cette correction sera visible de votre partenaire. Votre archive privée est préservée.</p>':'')+
   '<label>Date<input id="event-date" type="date" value="'+safe(edited?.happened_on||state.selectedDate||now)+'"></label>'+
   '<label>Qui a eu un orgasme ?<select id="event-orgasm">'+
   ['Non précisé','Les deux','Moi','Mon partenaire','Aucun'].map(x=>selOpt(x,'orgasm')).join('')+
   '</select></label><label>Point fort<select id="event-good">'+
   ['Complicité','Tendresse','Communication','Plaisir','Nouveauté'].map(x=>selOpt(x,'good')).join('')+
   '</select></label><label>À améliorer<select id="event-weak">'+
   ['Rien','Plus de temps','Plus de douceur','Plus de communication'].map(x=>selOpt(x,'weak')).join('')+
   '</select></label><label>Commentaire partagé avec votre partenaire<textarea id="event-note" maxlength="1200" placeholder="Facultatif">'+safe(initial?.note||'')+'</textarea></label>'+
   button('save-event',edited?'Enregistrer mes modifications':'Enregistrer dans notre calendrier')+
   (edited?button('cancel-edit-event','Annuler','class="subtle"'):'')+'</section>';
  const selected=state.selectedDate||now;
  const matched=state.journal.filter(e=>e.happened_on===selected);
  body+='<section class="card"><h3>'+safe(selected)+' · '+matched.length+' entrée(s)</h3>';
  for(const item of matched){
   let decrypted;
   try{decrypted=await decryptJson(item.ciphertext)}catch{decrypted=null}
   body+='<div class="muted-line"><div class="tag">'+safe(person(item.author_id))+'</div>'+
    (decrypted?'<p>Orgasme : '+safe(decrypted.orgasm)+'<br>Point fort : '+safe(decrypted.good)+
    '<br>À améliorer : '+safe(decrypted.weak)+'</p>'+
    (decrypted.note?'<p>'+safe(decrypted.note)+'</p>':''):'<p>Cette entrée n’a pas pu être déchiffrée.</p>')+
    (item.author_id===state.user.id?button('edit-event:'+item.id,'Modifier','class="subtle"')+
    button('delete-event:'+item.id,'Supprimer du journal partagé','class="subtle"'):'')+'</div>';
  }
  body+='</section>';
 }
 body+='<section class="card"><h3>Nos moments à deux</h3>'+
  button('tab:session','Retour à notre soirée','class="subtle"')+'</section>';
 frame(body,'NOTRE JOURNAL','Un calendrier partagé, du contenu chiffré');
}
async function renderFeedback(){
 const sessions=state.sessions||[];
 const session=sessions.find(x=>x.id===state.chosenSession&&x.finished_at&&x.counted)||
  latestCountedSession(sessions);
 if(!session){
  frame('<section class="card"><p>Terminez d’abord une soirée pour répondre à vos questions.</p>'+
   button('tab:session','Retour à notre soirée')+'</section>','NOS AVIS','Après une soirée terminée');return;
 }
 state.chosenSession=session.id;
 const entries=state.feedback.filter(f=>f.session_id===session.id);
 const yours=entries.find(x=>x.author_id===state.user.id);
 const complete=state.members.every(m=>entries.some(x=>x.author_id===m.user_id));
 const statuses=state.feedbackStatus?.sessionId===session.id?state.feedbackStatus.items:null;
 const submitted=statuses?statuses.filter(x=>x.submitted).length:entries.length;
 let body='<section class="card"><div class="eyebrow">♥ APRÈS LA SOIRÉE</div>'+
 '<h2>Soirée du '+safe(session.started_at.slice(0,10))+'</h2>'+
 '<p>Trois questions pour chacun. Votre partenaire ne pourra lire vos réponses qu’une fois que vous aurez répondu tous les deux.</p>'+
 '<div class="stat-grid"><div class="stat"><strong>'+submitted+'/2</strong><small>Avis déposés</small></div></div>';
 if(!state.unlockKey){
  body+='<p>Déverrouillez votre journal chiffré pour répondre ou voir les résultats.</p>'+
   '<input type="password" id="journal-pass" placeholder="Phrase secrète commune">'+button('unlock','Déverrouiller');
 }else if(!complete){
  if(yours)body+='<div class="invite-status"><strong>✓ Votre avis est enregistré</strong>'+
   '<p>En attente de la réponse de votre partenaire. Les avis ne sont pas encore visibles.</p></div>';
  else body+='<h3>Mes 3 questions</h3>'+
   '<label>1. Comment avez-vous aimé la soirée ?'+ratingChoice('rating')+'</label>'+
   '<label>2. Votre moment préféré<select id="feedback-liked">'+
   ['La complicité','La tendresse','Les défis','L’ambiance','Les surprises'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label><label>3. Pour la prochaine fois<select id="feedback-next">'+
   ['Rien à changer','Plus de douceur','Plus de temps','Plus de surprises','Rythme différent'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label>'+button('save-feedback','Enregistrer mon avis ❤️');
 }else{
  body+='<h3>♡ Vos deux avis</h3>';
  for(const member of state.members){
   const entry=entries.find(x=>x.author_id===member.user_id);
   let answer=null;
   try{answer=await decryptJson(entry.ciphertext);}catch{}
   body+='<div class="muted-line"><h3>'+safe(member.name)+'</h3>'+
    (answer?'<p>'+formattedStars(answer.rating)+'<br>Préféré : '+safe(answer.liked)+
      '<br>Pour la prochaine fois : '+safe(answer.next)+'</p>':
      '<p>Impossible de lire cette réponse. Vérifiez la phrase secrète.</p>')+'</div>';
  }
 }
 body+='</section><section class="card">'+
 button('tab:session','← Retour à Soirée','class="subtle"')+'</section>';
 frame(body,'NOS AVIS APRÈS LA SOIRÉE','Un moment pour partager vos ressentis');
}
function isLoveAdmin(){return !!(state.coupleId&&state.couple?.owner_user_id===state.user?.id);}
function renderCardLibrary(){
 if(!isLoveAdmin()){state.tab='settings';renderSettings();return;}
 const category=state.customCategory;
 const slots=editableCardSlots(category,state.customCards);
 const slot=slots.find(x=>x.cardId===state.customSlot)||slots[0];
 const roleNames={both:'Les deux partenaires',
  owner:'Moi · '+state.profile.display_name,
  partner:state.members.find(m=>m.user_id!==state.user.id)?.name||'Ma conjointe (à inviter)'};
 const chooser=(name,current)=>'<div class="custom-receiver">'+
   ['both','owner','partner'].map(role=>'<label class="custom-person-option">'+
     '<input type="radio" name="'+name+'" value="'+role+'"'+
     (current===role?' checked':'')+'> '+safe(roleNames[role])+'</label>').join('')+'</div>';
 const table=(range)=>range.map(card=>{
  const title=card.title||'Emplacement vide';
  return '<button type="button" class="custom-card-tile '+
   (card.saved?'custom-filled ':'')+
   (slot.cardId===card.cardId?'custom-active':'')+
   '" data-action="custom-slot:'+card.cardId+
   '" data-card-id="'+card.cardId+'" data-saved="'+card.saved+
   '" data-empty="'+(!card.filled)+'" aria-label="Modifier carte '+card.cardId+' : '+safe(title)+'">'+
   '<span class="custom-tile-number">'+String(card.cardId).padStart(2,'0')+'</span>'+
   '<span class="custom-tile-name">'+safe(title)+'</span></button>';
 }).join('');
 let body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>'+playableEveningCount(state.customCards).total+' cartes jouables</h2>'+
  '<p>'+playableEveningCount(state.customCards).sensual+' Sensuel · '+
   playableEveningCount(state.customCards).sexual+' Sexuel. Les 20 emplacements facultatifs vides ne sont jamais tirés.</p>'+
  '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
    '<button class="pill '+(category===c?'selected':'')+
    '" data-action="custom-category:'+c+'">'+safe(names[c])+'</button>').join('')+'</div>'+
  '</section>'+
  '<details class="card library-technical"'+(state.jsonPending?.kind==='evening'?' open':'')+
  '><summary>Importer / exporter les défis</summary>'+
  jsonToolsPanel('evening',names[category],state.jsonPending)+'</details>'+
  '<section class="card"><div class="eyebrow">RÈGLE DE PIGE</div>'+
  '<h3>Éviter les répétitions</h3>'+
  '<label class="custom-draw-setting"><input type="checkbox" id="no-repeat-cards"'+
    (state.couple?.no_repeat_challenges!==false?' checked':'')+'>'+
    '<span>Ne pas ressortir une carte tant que toutes les cartes admissibles n’ont pas été pigées</span></label>'+
  '<p class="tiny-note">Activé par défaut. Le cycle est conservé entre les soirées, séparément pour chaque personne qui reçoit, en tenant compte de qui est autorisé à piger et recevoir. Après épuisement, un nouveau cycle commence automatiquement.</p>'+
  button('save-draw-rule','Enregistrer cette règle','class="subtle"')+
  '</section>'+
  '<section class="card"><div class="eyebrow">CHOISIR UNE CARTE À MODIFIER</div>'+
  '<label>Rechercher une carte<input id="custom-card-search" type="search" placeholder="Titre, mot-clé ou numéro"></label>'+
  '<label>Afficher<select id="custom-card-filter"><option value="all">Toutes les cartes</option><option value="edited">Cartes personnalisées</option><option value="empty">Emplacements libres</option></select></label>'+
  '<h3>25 cartes prêtes</h3><div class="custom-card-tiles">'+table(slots.slice(0,25))+
  '</div><h3>10 emplacements facultatifs</h3><div class="custom-card-tiles">'+
  table(slots.slice(25))+'</div>'+
  '<p class="tiny-note">Les emplacements vides ne sont jamais pigés. Les cartes modifiées et les cartes officielles restent indiscernables pendant le jeu.</p></section>'+
  '<section class="card"><div class="eyebrow">ÉDITEUR PRIVÉ · CARTE '+String(slot.cardId).padStart(2,'0')+'</div>'+
  '<h2>'+safe(slot.title||'Nouvelle carte')+'</h2>'+
  '<label>Titre de la carte<input id="custom-title" maxlength="120" value="'+
   safe(slot.title)+'" placeholder="Titre du défi"></label>'+
  '<label>Consigne<textarea id="custom-description" maxlength="900" rows="4"'+
   ' placeholder="Décrivez librement le défi proposé">'+safe(slot.description)+'</textarea></label>'+
  '<strong>Qui peut piger cette carte ?</strong>'+
  chooser('custom-drawer',slot.drawerRole)+
  '<strong>Qui peut recevoir ce défi ?</strong>'+
  chooser('custom-recipient',slot.recipientRole)+
  '<p class="tiny-note">En Soirée actuelle, la personne désignée pige et réalise son propre défi. Les cadeaux transfèrent un défi disponible à l’autre partenaire. Qui peut piger et qui peut recevoir sont vérifiés par le serveur.</p>'+
  '<label class="custom-draw-setting"><input type="checkbox" id="custom-timer-enabled"'+
   (slot.minutes?' checked':'')+'> Ajouter un minuteur (facultatif)</label>'+
  '<label>Durée (minutes)<input id="custom-minutes" type="number" min="1" max="60" value="'+
   (slot.minutes||5)+'"></label>'+
  '<p class="tiny-note">Sans minuteur, aucun compte à rebours n’est affiché.</p>'+
  button('save-custom-card','♡ Enregistrer les modifications')+
  (slot.saved?button('delete-custom-card',
    slot.isOriginal?'Restaurer cette carte d’origine':'Vider cet emplacement',
    'class="subtle"'):'')+
  '</section><section class="card">'+
  button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>';
 frame(body,'MES CARTES','Gestion confidentielle, réservée à l’admin');
}
function renderMonthlyLibrary(){
 if(!isLoveAdmin()){state.tab='settings';renderSettings();return;}
 const items=editableMonthlyActivities(state.monthlyActivities);
 const entry=items.find(x=>x.face===state.monthlySlot)||items[0];
 const tiles=items.map(x=>'<button type="button" class="custom-card-tile '+
  (x.edited?'custom-filled ':'')+
  (x.face===entry.face?'custom-active':'')+
  '" data-action="monthly-slot:'+x.face+'">'+
  '<span class="custom-tile-number">'+String(x.face).padStart(2,'0')+'</span>'+
  '<span class="custom-tile-name">'+safe(x.title)+'</span></button>').join('');
 const body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>Mes 20 activités du mois</h2>'+
  '<p>Changez les 20 idées de sortie, même avant d’inviter votre partenaire. Vous pouvez remplacer complètement une idée de sortie et sa description.</p>'+
  '<div class="custom-card-tiles">'+tiles+'</div>'+
  '<p class="tiny-note">Les changements seront utilisés aux prochains tirages. Le résultat déjà obtenu ce mois-ci reste inchangé pour les deux partenaires.</p>'+
  '</section>'+
  '<details class="card library-technical"'+(state.jsonPending?.kind==='monthly'?' open':'')+
  '><summary>Importer / exporter les activités</summary>'+
  jsonToolsPanel('monthly','20 activités du mois',state.jsonPending)+'</details>'+
  '<section class="card"><div class="eyebrow">MODIFIER L’ACTIVITÉ '+String(entry.face).padStart(2,'0')+'</div>'+
  '<h3>'+safe(entry.title)+'</h3>'+
  '<label>Nom de l’activité<input id="monthly-title" maxlength="120" value="'+
    safe(entry.title)+'" placeholder="Ex. Soirée au chalet"></label>'+
  '<label>Description et consignes<textarea id="monthly-description" maxlength="900" rows="4"'+
    ' placeholder="Décrivez la sortie prévue">'+safe(entry.description)+'</textarea></label>'+
  '<label class="custom-draw-setting"><input id="monthly-restaurant" type="checkbox"'+
    (entry.is_restaurant?' checked':'')+'> Activité de restaurant / repas à réserver</label>'+
  '<p class="tiny-note">Si cette option est activée, LoveRoll propose également le dé « restaurant connu ou nouveau » après le tirage de cette activité.</p>'+
  '<label class="custom-draw-setting"><input type="checkbox" id="monthly-timer-enabled"'+(entry.timer_minutes?' checked':'')+'> Minuteur facultatif</label>'+
  '<label>Durée (minutes)<input id="monthly-minutes" type="number" min="1" max="60" value="'+(entry.timer_minutes||5)+'"></label>'+
  button('save-monthly-activity','♡ Enregistrer l’activité')+
  (entry.edited?button('reset-monthly-activity','Restaurer l’activité d’origine','class="subtle"'):'')+
  '</section><section class="card">'+
  button('tab:settings','← Retour aux réglages','class="subtle"')+'</section>';
 frame(body,'MES ACTIVITÉS','20 sorties personnalisables et privées');
}
function settingsIcon(key){
 const strokes={
  compte:'<circle cx="12" cy="7.5" r="3.5"/><path d="M5 21v-2c0-4 3-6 7-6s7 2 7 6v2"/>',
  couple:'<path d="M12 21s-8-5.2-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 5.8-8 11-8 11Z"/>',
  notifications:'<path d="M18 8a6 6 0 0 0-12 0c0 7-3 8-3 10h18c0-2-3-3-3-10ZM10 21h4"/>',
  jeu:'<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r=".8" fill="currentColor"/><circle cx="16" cy="16" r=".8" fill="currentColor"/><circle cx="12" cy="12" r=".8" fill="currentColor"/>',
  admin:'<path d="M12 2 20 5v6c0 5-3.3 8.6-8 11-4.7-2.4-8-6-8-11V5Z"/>',
  application:'<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M10 18h4"/>',
  confidentialite:'<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  aide:'<circle cx="12" cy="12" r="10"/><path d="M9.5 9a2.6 2.6 0 1 1 4.5 2c-1.4 1-2 1.6-2 3M12 18h.01"/>'
 };
 return '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+strokes[key]+'</svg>';
}
function settingGroup(title,icon,description,body,opened=false){
 const sections={'Compte':'compte','Couple':'couple','Notifications':'notifications','Jeu et personnalisation':'jeu','Administration':'admin','Application':'application','Confidentialité':'confidentialite','Aide':'aide'};
 const section=sections[title]||'';
 return '<details class="settings-group" data-settings-section="'+section+'"'+(settingsOpenSection===section?' open':'')+'><summary>'+
  '<span class="settings-group-icon">'+(sections[title]?settingsIcon(section):icon)+'</span>'+
  '<span class="settings-group-heading"><strong>'+safe(title)+'</strong><small>'+safe(description)+'</small></span>'+
  '<span aria-hidden="true" class="settings-chevron">⌄</span></summary>'+
  '<div class="settings-group-content">'+body+'</div></details>';
}
function settingRow(title,detail,action,label='Ouvrir'){
 return '<button type="button" class="settings-link-row" data-action="'+safe(action)+'">'+
  '<span class="settings-link-copy"><strong>'+safe(title)+'</strong><small>'+safe(detail)+'</small></span>'+
  '<span class="settings-link-affordance">'+safe(label)+' <b aria-hidden="true">›</b></span></button>';
}
function frenchMonthDay(md){
 if(!/^\d{2}-\d{2}$/.test(md||''))return 'Non renseigné';
 const date=new Date('2000-'+md+'T12:00:00Z');
 return Number.isNaN(date.valueOf())?'Non renseigné':
  new Intl.DateTimeFormat('fr-CA',{day:'numeric',month:'long',timeZone:'UTC'}).format(date);
}
function renderCoupleDateSetting(){
 const started=state.occasionSettings?.relationship_started_on||'';
 return (isLoveAdmin()?
  '<label class="settings-date-field"><span>Date de début</span><input id="couple-started-on" type="date" max="'+safe(today())+
  '" value="'+safe(started)+'"></label>':
  '<div class="settings-date-field"><strong>Date de début</strong><p>'+safe(formatCoupleDate(started))+'</p></div>')+
  '<p class="tiny-note settings-date-hint">Anniversaire : '+safe(frenchMonthDay(state.occasionSettings?.anniversary_md))+'</p>';
}
function initSettingsAccordion(){
 const groups=[...root.querySelectorAll('details.settings-group[data-settings-section]')];
 const saveOpen=section=>{
  try{
   if(section)sessionStorage.setItem('loveroll-settings-section',section);
   else sessionStorage.removeItem('loveroll-settings-section');
  }catch(error){console.warn('Préférence locale non disponible',error);}
 };
 for(const group of groups){
  group.querySelector('summary')?.addEventListener('click',event=>{
   event.preventDefault();
   const shouldOpen=!group.open;
   for(const other of groups)other.open=shouldOpen&&other===group;
   settingsOpenSection=shouldOpen?group.dataset.settingsSection:'';
   saveOpen(settingsOpenSection);
  });
 }
}
function renderSettings(){
 const linked=!!state.coupleId,hasPartner=linked&&state.members.length===2;
 const account='<div class="settings-split settings-account-top">'+
  '<div class="settings-identity"><strong>Compte connecté</strong><p>'+safe(state.user.email||'Courriel privé')+'</p></div>'+
  '<label class="settings-inline-name"><span>Prénom affiché</span>'+
  '<input id="profile-name" type="text" autocomplete="given-name" value="'+safe(state.profile.display_name)+'" maxlength="80" aria-label="Prénom affiché"></label></div>'+
  '<div class="settings-actions">'+
  button('profile','Enregistrer mon profil','class="settings-primary"')+
  button('signout','Déconnexion','class="subtle settings-secondary"')+'</div>'+
  '<details class="settings-birthday-edit"><summary>Modifier ma date d’anniversaire · '+
   safe(frenchMonthDay(state.profile.birthday_md))+'</summary>'+
  '<p>Cette date sera aussi corrigée dans les occasions du couple.</p>'+
  monthDayInputs('settings-birthday',state.profile.birthday_md)+'</details>';
 let partner='';
 if(hasPartner){
  partner='<div class="settings-split settings-couple-top"><div class="settings-couple-status">'+
   '<strong>♡ Couple connecté</strong><p>'+safe(state.profile.display_name)+' et '+safe(other()?.name||'Votre partenaire')+'</p>'+
   '<span class="settings-confirmation">✓ Invitation acceptée</span></div>'+
   '<div class="settings-date-outer">'+renderCoupleDateSetting()+'</div></div>'+
   '<div class="settings-actions">'+
   (isLoveAdmin()?button('save-couple-date','Enregistrer notre date','class="settings-primary"'):'')+'</div>'+
   '<div class="settings-danger-zone"><p>Gestion du couple · action sensible</p>'+
   button('end-couple','Dissocier mon partenaire','class="subtle danger-action settings-secondary"')+'</div>'+
   '<p class="tiny-note settings-footer-hint">La dissociation conserve les comptes et les défis.</p>';
 }else if(linked){
  partner=renderInvitePanel()+
   '<p class="tiny-note">Vous pouvez préparer vos défis avant que la demande soit acceptée.</p>'+
   button('end-couple','Annuler mon espace à deux','class="subtle danger-action"');
 }else partner='<div class="settings-couple-invite">'+
  '<p>Votre espace privé est prêt à être créé. Invitez votre partenaire pour commencer à deux.</p>'+
  button('start-couple','♡ Inviter un conjoint ou une conjointe','class="settings-primary settings-couple-cta"')+
  button('prepare-cards','Créer mes défis avant l’invitation','class="subtle settings-secondary settings-couple-prep"')+
  '</div>';
 const celebrants=isLoveAdmin()&&hasPartner?
  '<div class="settings-item"><div><strong>Surprises des fêtes</strong>'+
  '<p>Choisissez le partenaire concerné pour chaque occasion.</p></div></div>'+
  TARGETED_OCCASIONS.map(key=>{
   const entry=OCCASIONS.find(o=>o.key===key),chosen=state.occasionSettings?.celebrants?.[key];
   return '<div class="muted-line"><strong>'+safe(entry.title)+'</strong><div class="chipline">'+
    state.members.map(m=>'<button class="pill '+(chosen===m.user_id?'selected':'')+
      '" data-action="set-celebrant:'+key+':'+m.user_id+'">'+safe(m.name)+'</button>').join('')+
    '</div></div>';
  }).join(''):'';
 const game=isLoveAdmin()?
  settingRow('Mes cartes','70 défis · 35 Sensuel et 35 Sexuel.','tab:library','Modifier')+
  settingRow('Activités du mois','20 activités du dé D20.','tab:monthly-library','Modifier')+
  settingRow('Calendrier de l’Avent','25 surprises du calendrier partagé.','tab:advent-editor','Modifier')+
  settingRow('Événements spéciaux','Six événements, activités et variantes.','tab:special-editor','Modifier')+
  celebrants:
  '<p>Les réglages des cartes sont réservés au créateur du couple.</p>';
 const adminWeekly=isLoveAdmin()&&hasPartner?
  '<div class="settings-item"><div><strong>🎲 Administration — tirage hebdomadaire</strong>'+
  '<p>Réinitialiser le D6 de cette semaine, même si une soirée a commencé. Les défis, séances et comptes sont conservés.</p></div></div>'+
  '<div class="weekly-admin-status">'+
  '<p>Résultat : <strong>'+(state.weekly?state.weekly.face+' soirée(s)':'Non brassé')+'</strong></p>'+
  '<p>Brassé par : '+(state.weekly?safe(person(state.weekly.actor)):'—')+'</p>'+
  '<p>Date du brassage : '+(state.weekly?safe(new Date(state.weekly.created_at).toLocaleString('fr-CA',{timeZone:'America/Toronto'})):'—')+'</p>'+
  '<p>Prochain lundi : '+safe(nextMonday())+' à 00 h (Québec)</p></div>'+
  '<p class="tiny-note">Reset admin forcé : fonctionne même si une soirée est commencée. Les séances, défis, attributions et historiques sont conservés. Le nouveau tirage remplace seulement le D6 de cette semaine.</p>'+
  button('admin-reset-weekly','↺ Reset hebdomadaire','class="subtle danger-action" '+(!state.weekly?'disabled':'')):'';
 const notifications=notificationSettingsMarkup(state.pushPrefs||{});
 const nativeApp=typeof window!=='undefined'&&!!window.LoveRollNativeAuth;
 const nativeVersion=nativeApp&&typeof window.LoveRollNativeAuth.appVersion==='function'?
  window.LoveRollNativeAuth.appVersion():'';
 const homeInstall=nativeApp?
  settingRow('Placer LoveRoll sur l’écran d’accueil',
   'L’APK est déjà installé. Voir comment placer son icône.','show-install-guide','Voir'):
  installStatus()==='installed'?
  settingRow('LoveRoll sur l’écran d’accueil',
   'Déjà installé et prêt à être utilisé.','show-install-guide','Voir'):
  settingRow('Ajouter LoveRoll à l’écran d’accueil',
   'Accès rapide sur Android ou iPhone.','install-home','Ajouter');
 const appSettings=(nativeApp?
  '<div class="settings-item"><div><strong>LoveRoll est installé</strong><p>Version Android : '+
  safe(nativeVersion||'non fournie par cette version APK')+'</p></div></div>'+
  settingRow('Vérifier les mises à jour APK','Rechercher une version Android signée plus récente.','native-update','Vérifier'):'')+
  homeInstall+
  '<div class="settings-item"><div><strong>Version Web '+WEB_RELEASE+'</strong>'+
  '<p>Version du contenu Web actuellement chargé.</p></div></div>'+
  settingRow('Actualiser LoveRoll maintenant',
   'Force le chargement de la dernière version sans supprimer votre compte ni vos défis.',
   'reload-web','Actualiser')+
  '<p class="tiny-note">Thème LoveRoll : bourgogne, noir et rose. L’APK Android est mis à jour séparément.</p>';
 const privacy=(linked?settingRow('Journal partagé','Consulter le journal commun et son calendrier.','tab:journal','Ouvrir'):'')+
  (nativeApp?settingRow('Mes anciennes données hors ligne',
   'Ouvrir volontairement le journal Android local non synchronisé.','native-legacy','Ouvrir'):'')+
  (linked?button('lock','Verrouiller le journal partagé','class="subtle"'):'')+
  '<div class="settings-item"><div><strong>Mon ressenti</strong><p>Réglages personnels et confidentiels.</p></div></div>'+
  wellbeingFields(state.wellbeing)+button('save-wellbeing','Enregistrer mon ressenti','class="subtle"');
 const helpEntries=[
  ['Association des comptes','Une invitation privée relie deux comptes distincts après acceptation.'],
  ['Dé hebdomadaire','Un seul tirage officiel par couple et par semaine, renouvelé le lundi.'],
  ['Soirée et défis','Chacun joue les défis qui lui sont attribués et peut les refuser.'],
  ['Cadeaux','Les cadeaux restent dans l’historique commun prévu pour le couple.'],
  ['Rendez-vous surprise','Les dés vous proposent une activité et déterminent qui invite.'],
  ['Événements spéciaux','Les occasions et leurs variantes se gèrent dans Jeu et personnalisation.'],
  ['Calendrier de l’Avent','Une seule ouverture officielle par journée pour le couple.'],
  ['Synchronisation','Les données partagées se mettent à jour entre les comptes connectés.'],
  ['Confidentialité','Le ressenti de chaque personne demeure individuel.'],
  ['Mises à jour','Vérifiez les versions Android et actualisez le contenu Web dans Application.']
 ];
 const support='<div class="settings-help-list">'+helpEntries.map(([title,answer])=>
  '<details class="settings-help-item"><summary>'+safe(title)+'<span aria-hidden="true">⌄</span></summary>'+
  '<p>'+safe(answer)+'</p></details>').join('')+'</div>';
 frame('<div class="settings-grid">'+
   settingGroup('Compte','♙','Gérez votre compte et votre appareil',account,requestedSettingsSection==='compte')+
   settingGroup('Couple','♥',hasPartner?'Votre espace partagé et vos dates':'Invitations et état de la demande',partner,
     !requestedSettingsSection||requestedSettingsSection==='couple')+
   settingGroup('Notifications','♧','Rappels et préférences',notifications,requestedSettingsSection==='notifications')+
   settingGroup('Jeu et personnalisation','🎲','Cartes, activités et surprises',game,requestedSettingsSection==='jeu')+
   (isLoveAdmin()?settingGroup('Administration','♛','Gestion des tirages',adminWeekly,
     requestedSettingsSection==='admin'):'')+
   settingGroup('Application','▣','Version et mises à jour',appSettings,requestedSettingsSection==='application')+
   settingGroup('Confidentialité','♙','Journal, données et ressenti',privacy,requestedSettingsSection==='confidentialite')+
   settingGroup('Aide','?','Informations et fonctionnement',support,requestedSettingsSection==='aide')+'</div>',
  'RÉGLAGES','Compte, couple, notifications et confidentialité');
 initSettingsAccordion();
}
async function refreshAndShow(){await refreshShared();render();}
async function animateRoll(fn,kind='general'){
 if(state.diceStatus)throw Error('Un dé est déjà en cours de brassage.');
 state.rollAnimationKind=kind;state.diceStatus='pending';render();
 try{
  const result=await fn(); // Supabase determines and persists the actual result
  // Never fabricate a one-pip result if Supabase has not confirmed a face.
  const confirmedFace=Number(result?.face);
  if(!Number.isInteger(confirmedFace)||confirmedFace<1||
     (kind==='my-d6'&&confirmedFace>6))
   throw new Error('Le serveur n’a pas confirmé le résultat officiel du dé.');
  state.rollAnimationFace=confirmedFace;
  state.diceStatus='rolling';render();
  await new Promise(resolve=>setTimeout(resolve,3250));
  state.diceStatus=null;state.rollAnimationFace=null;state.rollAnimationKind=null;
  await refreshAndShow();return result;
 }catch(e){
  state.diceStatus=null;state.rollAnimationFace=null;state.rollAnimationKind=null;
  await refreshAndShow();throw e;
 }
}
async function hashToken(token){
 const digest=await crypto.subtle.digest('SHA-256',enc.encode(token));
 return [...new Uint8Array(digest)].map(b=>b.toString(16).padStart(2,'0')).join('');
}
function randomInviteToken(){
 const bytes=crypto.getRandomValues(new Uint8Array(32));
 return [...bytes].map(x=>x.toString(16).padStart(2,'0')).join('');
}
async function ensureInviteDraft(forceNew=false){
 if(!state.coupleId||state.members.length===2)throw Error('Le couple est déjà connecté.');
 const email=normalizeInviteEmail(id('invite-email')?.value);
 if(!CONFIG.siteUrl)throw Error('Adresse publique LoveRoll manquante.');
 const existing=state.invitations?.find(x=>!x.cancelled_at&&!x.accepted_at&&
  new Date(x.expires_at)>new Date()&&x.invited_email?.toLowerCase()===email);
 // ID links are recoverable from server-visible invitation metadata; the
 // secret token hash is never exposed. Re-sending does not consume/invalidate it.
 if(!forceNew&&existing){
  state.inviteDraft={id:existing.id,email,link:inviteLinkForId(CONFIG.siteUrl,existing.id)};
  return state.inviteDraft;
 }
 if(!forceNew&&state.inviteDraft&&state.inviteDraft.email===email)return state.inviteDraft;
 if(!forceNew&&state.invitations?.some(x=>!x.cancelled_at&&!x.accepted_at&&
   new Date(x.expires_at)>new Date()))
  throw Error('Une invitation active existe pour un autre courriel. Utilisez « Remplacer le lien actuel ».');
 const token=randomInviteToken(),digest=await hashToken(token);
 const invitationId=await rpc('create_pair_invite',{p_couple:state.coupleId,p_email:email,p_token_hash:digest});
 state.inviteDraft={id:invitationId,email,link:inviteLinkForId(CONFIG.siteUrl,invitationId)};
 state.invitations=await query('couple_invitations',{eq:{couple_id:state.coupleId},order:'created_at'});
 return state.inviteDraft;
}
function keepAuthModeInUrl(mode){
 const url=new URL(location.href);
 if(['signup','signin','request-reset'].includes(mode))url.searchParams.set('auth',mode);
 else url.searchParams.delete('auth');
 history.replaceState(null,'',url.pathname+url.search+url.hash);
}
async function handle(action,element){
 const [verb,...parts]=action.split(':');
 if(verb==='home-notices'){
  state.homeNotices=!state.homeNotices;render();return;
 }
 if(verb==='evening-more'){state.eveningMore=!state.eveningMore;render();return;}
 if(verb==='evening-challenge'){state.showEveningChallenge=!state.showEveningChallenge;render();return;}
 if(verb==='surprise-activity'){state.showSurpriseActivity=!state.showSurpriseActivity;render();return;}
 if(verb==='surprise-how'){
   notify('Chaque événement : un D6 choisit l’activité puis un second D6 choisit sa variante. Les résultats sont synchronisés.');
   return;
 }
 if(verb==='open-occasion'){
  const key=parts[0],owner=parts[1]==='-'?null:parts[1];
  if(!['anniversary','birthday','valentine','mothers_day','fathers_day'].includes(key))
   throw Error('Événement inconnu');
  if(owner&&!state.members.some(m=>m.user_id===owner))throw Error('Partenaire inconnu');
  state.specialFocus={key,owner};state.tab='occasions';render();return;
 }
 if(verb==='occasion-back'){state.specialFocus=null;state.tab='special';render();return;}

 if(verb==='reload-web'){
  await reloadWebAssets(true);return;
 }
 if(verb==='admin-reset-weekly'){
  if(!isLoveAdmin()||!isSharedCoupleReady(state.coupleId,state.members))
   throw Error('Réinitialisation réservée à l’administrateur du couple.');
  if(!state.weekly)throw Error('Aucun tirage hebdomadaire à réinitialiser.');
  const message='Réinitialiser le résultat hebdomadaire pour les DEUX partenaires ? '+
    'Cette action est enregistrée. Les défis et soirées restent intacts. '+
    'Même si une soirée a déjà commencé, elle et ses défis resteront enregistrés. ' +
    'Seul le résultat hebdomadaire sera effacé et vous pourrez relancer le D6.';
  if(!(await premiumConfirm(message,{kind:'reset',title:'Relancer le dé hebdomadaire ?'})))return;
  const changed=await rpc('reset_love_weekly_roll_admin',{p_couple:state.coupleId});
  await refreshAndShow();
  notify(changed?'Résultat hebdomadaire réinitialisé pour le couple.':'Aucun tirage hebdomadaire à réinitialiser.');
  return;
 }
 // All demo actions are local, never database writes or new memberships.
 if(verb==='demo-enter'){
  if(!state.user||!state.profile?.birthday_md||!state.wellbeing)
   throw Error('Complétez votre compte pour accéder à la démo.');
  state.demoMode=true;state.demoTab='home';render();return;
 }
 if(verb==='demo-exit'){state.demoMode=false;state.demoTab='home';render();return;}
 if(verb==='demo-tab'){
  if(!state.demoMode||!DEMO_TABS.includes(parts[0]))return;
  state.demoTab=parts[0];render();return;
 }
 if(verb==='demo-roll'){
  if(!state.demoMode)return;
  state.demoRoll=Math.floor(Math.random()*6)+1;render();return;
 }
 if(verb==='demo-surprise'){
  if(!state.demoMode)return;
  const ideas=['Souper romantique','Soirée cinéma à deux','Promenade au coucher du soleil',
   'Massage en duo','Petit-déjeuner surprise','Pique-nique improvisé'];
  state.demoSurprise=ideas[Math.floor(Math.random()*ideas.length)];render();return;
 }
 if(verb==='demo-day'){
  if(!state.demoMode)return;
  const day=Number(parts[0]);if(Number.isInteger(day)&&day>=1&&day<=25){state.demoDay=day;render();}
  return;
 }
 if(verb==='demo-complete'){
  if(!state.demoMode)return;
  state.demoChallengeComplete=!state.demoChallengeComplete;render();return;
 }
 if(verb==='push-enable'){
  await enablePush(client,state.user.id);
  state.pushPrefs={...(state.pushPrefs||{}),enabled:true};
  render();notify('Notifications activées sur cet appareil ❤️');return;
 }
 if(verb==='push-disable'){
  await disablePush(client,state.user.id);
  state.pushPrefs={...(state.pushPrefs||{}),enabled:false};
  render();notify('Notifications désactivées.');return;
 }
 if(verb==='push-test'){
  const result=await sendTestPush(client);
  notify(result?'Demande de test acceptée. L’envoi et la réception restent à vérifier sur votre appareil.':'Demande de test refusée par le serveur.');
  return;
 }
 if(verb==='native-notify-enable'){
  if(!window.LoveRollNativeAuth?.enableWeeklyReminder)
   throw Error('Mettez à jour l’APK pour activer le rappel Android.');
  window.LoveRollNativeAuth.enableWeeklyReminder();
  notify('Rappel hebdomadaire demandé à Android. Autorisez les notifications si nécessaire.');
  return;
 }
 if(verb==='native-notify-disable'){
  if(!window.LoveRollNativeAuth?.disableWeeklyReminder)
   throw Error('Mettez à jour l’APK pour désactiver le rappel Android.');
  window.LoveRollNativeAuth.disableWeeklyReminder();
  notify('Rappel local désactivé.');return;
 }
 if(verb==='invite-start'){
  const invite=pendingInviteReference(sessionStorage);
  if(!invite)throw Error('Invitation introuvable.');
  state.inviteAccepted=true;state.showAuth=true;state.showDiscover=false;
  sessionStorage.setItem('loverollInviteAccepted',invite.kind+':'+invite.value);
  if(state.user)await reload();else render();
  return;
 }
 if(verb==='discover-loveroll'){state.showDiscover=true;render();return;}
 if(verb==='discover-back'||verb==='invite-return'){
  keepAuthModeInUrl(null);state.showDiscover=false;
  if(pendingInviteReference(sessionStorage)){
   state.inviteAccepted=false;state.showAuth=false;
   sessionStorage.removeItem('loverollInviteAccepted');
  }else state.showAuth=verb==='discover-back';
  render();return;
 }
 if(verb==='copy-invite-for-browser'){
  const invite=pendingInviteReference(sessionStorage);
  if(!invite)throw Error('Lien d’invitation introuvable.');
  const url=invite.kind==='id'?inviteLinkForId(CONFIG.siteUrl,invite.value):
   CONFIG.siteUrl+'?v='+INVITATION_REVISION+'#invite='+invite.value;
  try{await navigator.clipboard.writeText(url);}
  catch(error){
   const manuallyCopied=await premiumCopyLink(url);
   if(!manuallyCopied)return;
  }
  notify('Lien personnel prêt. Collez-le dans Chrome ou Safari.');return;
 }
 if(verb==='use-existing-account'){
  // The profile step can also be reached with an old or wrong authenticated
  // account. Never force the user to overwrite that account's personal profile.
  // The pending invitation stays intact across this local sign-out.
  const hadInvite=!!pendingInviteReference(sessionStorage);
  const priorConsent=!!state.inviteAccepted;
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  state.unlockKey=null;state.personalPass=null;state.personalJournal=[];
  // No profile, couple, challenge, invitation or user row is deleted.
  const {error}=await client.auth.signOut({scope:'local'});
  if(error)throw error;
  state.user=null;state.profile=null;state.coupleId=null;state.couple=null;
  state.members=[];state.wellbeing=null;state.emailVerificationPending=false;
  state.passwordRecovery=false;state.showDiscover=false;state.showAuth=true;
  state.authMode='signin';state.authDraftEmail='';state.inviteError=null;
  state.inviteAttemptKey=null;
  state.inviteAccepted=hadInvite&&priorConsent;
  state.authNotice='Connectez-vous à votre compte existant avec votre mot de passe ou « Continuer avec Google ».';
  render();return;
 }
 if(verb==='join-invite-from-solo'){
  const invite=pendingInviteReference(sessionStorage);
  if(!invite||!state.user||!state.coupleId||
     state.members.length!==1||state.members[0]?.user_id!==state.user.id||
     state.couple?.owner_user_id!==state.user.id)
   throw Error('Votre espace personnel doit être le seul membre avant de rejoindre cette invitation.');
  const yes=await premiumConfirm(
   'Votre espace LoveRoll actuel (où vous êtes seul) sera dissocié. Vos cartes personnalisées et votre historique personnel seront sauvegardés dans votre archive. Ils ne seront pas automatiquement mélangés avec les cartes du couple invité. Voulez-vous rejoindre le nouveau couple ?',
   {kind:'couple',title:'Rejoindre votre partenaire ?',accept:'Oui, rejoindre ce couple',cancel:'Conserver mon espace'});
  if(!yes)return;
  try{
   const invitationId=invite.kind==='id'?invite.value:
    await rpc('resolve_love_invite_reference',{p_token_hash:await hashToken(invite.value)});
   if(!invitationId)throw Error('Invitation introuvable ou expirée.');
   // Server verifies recipient email and performs archive + membership move
   // ATOMICALLY; any failure rolls back the prior solo membership.
   await rpc('join_love_invitation_from_solo',{p_invitation:invitationId});
   state.unlockKey=null;state.personalPass=null;state.personalJournal=[];
   state.inviteError=null;
   finishInviteReference();
   state.showAuth=false;state.showDiscover=false;state.inviteWelcome=true;state.tab='home';
   await reload();
  }catch(error){
   state.inviteError=invitationAuthMessage(error);
   render();
  }
  return;
 }
 if(verb==='switch-account-invite'){
  if(state.user&&safeNativePushToken()){
   try{await disablePush(client,state.user.id);}catch(error){console.warn('Déconnexion FCM',error.code||'');}
  }
  if(!pendingInviteReference(sessionStorage))throw Error('Invitation introuvable.');
  state.unlockKey=null;state.personalPass=null;state.personalJournal=[];
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  const {error}=await client.auth.signOut();
  if(error)throw error;
  state.user=null;state.profile=null;state.coupleId=null;state.members=[];
  state.inviteAccepted=true;state.showAuth=true;state.inviteError=null;state.inviteAttemptKey=null;render();return;
 }
 if(verb==='native-update'){
  if(!window.LoveRollNativeAuth?.checkForUpdates)throw Error('Vérification des mises à jour disponible dans l’APK Android.');
  window.LoveRollNativeAuth.checkForUpdates();return;
 }
 if(verb==='native-legacy'){
  if(!window.LoveRollNativeAuth?.openLegacyOffline)throw Error('Anciennes données locales disponibles dans l’APK Android.');
  if(!(await premiumConfirm('Les anciennes données hors ligne ne sont pas synchronisées avec votre couple connecté.',{kind:'legacy'})))return;
  window.LoveRollNativeAuth.openLegacyOffline();return;
 }
 if(verb==='show-install-guide'){
  state.installModalOpen=false;state.showInstallSteps=true;state.forceInstallGuide=true;
  render();return;
 }
 if(verb==='install-home'){
  const result=await requestInstall();
  if(result.prompted){
   dismissInstall(state.user?.id);
   state.installModalOpen=false;state.showInstallSteps=false;state.forceInstallGuide=false;
   if(result.accepted)notify('LoveRoll a été ajouté à votre écran d’accueil ♡');
  }else{
   state.installModalOpen=false;state.showInstallSteps=true;state.forceInstallGuide=true;
  }
  render();return;
 }
 if(verb==='install-copy-url'){
  const link=new URL(CONFIG.siteUrl||location.origin+location.pathname);
  link.search='';link.hash='';
  await navigator.clipboard.writeText(link.toString());
  notify('Adresse LoveRoll copiée. Ouvrez-la dans Safari ou Chrome.');
  return;
 }
 if(verb==='install-later'||verb==='install-understood'){
  dismissInstall(state.user?.id);
  state.installModalOpen=false;state.showInstallSteps=false;state.forceInstallGuide=false;
  render();return;
 }
 if(verb==='set-celebrant'){
  if(!isLoveAdmin())throw Error('Réglage réservé à l’administrateur.');
  if(!TARGETED_OCCASIONS.includes(parts[0])||
    !state.members.some(m=>m.user_id===parts[1]))throw Error('Personne célébrée invalide.');
  await rpc('set_love_celebrant',{p_couple:state.coupleId,p_kind:parts[0],p_user:parts[1]});
  await reload();notify('Personne célébrée enregistrée.');return;
 }
 if(verb==='special-timer'){state.specialTimerKey=parts.join(':');render();return;}
 if(verb==='json-export'){
  if(!isLoveAdmin())throw Error('Export réservé à l’administrateur.');
  await exportLibraryFile(parts[0]);notify('Sauvegarde JSON préparée.');return;
 }
 if(verb==='json-preview'){
  if(!isLoveAdmin())throw Error('Import réservé à l’administrateur.');
  previewPortableJson(parts[0]);render();return;
 }
 if(verb==='json-cancel'){state.jsonPending=null;render();return;}
 if(verb==='json-apply'){
  if(!isLoveAdmin())throw Error('Import réservé à l’administrateur.');
  const preview=state.jsonPending;
  if(!preview||preview.kind!==parts[0])throw Error('Prévisualisez d’abord votre JSON.');
  normalizeLibraryData(preview.data,preview.kind);
  if(!(await premiumConfirm('Remplacer « '+preview.summary.label+' » avec '+
    preview.summary.count+' éléments et '+preview.summary.timers+
    ' minuteur(s) configuré(s) ? Les anciens tirages restent inchangés.',{kind:'library',title:'Importer la bibliothèque ?'})))return;
  if(preview.kind==='evening')
   await rpc('import_love_evening_library',{p_couple:state.coupleId,p_library:preview.data});
  else if(preview.kind==='monthly')
   await rpc('import_love_monthly_library',{p_couple:state.coupleId,p_library:preview.data});
  else if(preview.kind==='advent')
   await rpc('import_love_advent_2026',{p_couple:state.coupleId,p_calendar:preview.data});
  else if(preview.kind==='special')
   await rpc('import_love_special_events',{p_couple:state.coupleId,p_events:preview.data});
  state.jsonPending=null;await reload();
  notify('Bibliothèque importée : les minuteries JSON sont activées pour les prochains tirages.');return;
 }
 if(verb==='import-special-events'){
  if(!isLoveAdmin())throw Error('Import réservé à l’administrateur.');
  previewPortableJson('special');render();return;
 }
 if(verb==='import-advent-2026'){
  if(!isLoveAdmin())throw Error('Import réservé à l’administrateur.');
  previewPortableJson('advent');render();return;
 }
 if(verb==='advent-editor-day'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const day=Number(parts[0]);
  if(!Number.isInteger(day)||day<1||day>25)throw Error('Journée invalide.');
  if(hasUnsavedForm()&&!(await premiumConfirm('Un brouillon non enregistré pourrait être perdu en changeant de journée.',{
    kind:'library',title:'Changer de journée ?',accept:'Continuer',cancel:'Rester'})))return;
  state.adventEditorDay=day;render();
  id('advent-day-title')?.scrollIntoView({behavior:'smooth',block:'center'});
  return;
 }
 if(verb==='close-advent-day'){
  if(hasUnsavedForm()&&!(await premiumConfirm('Fermer sans enregistrer les modifications de cette journée ?',{
    kind:'library',title:'Fermer le brouillon ?',accept:'Fermer',cancel:'Rester'})))return;
  state.adventEditorDay=null;render();return;
 }
 if(verb==='save-advent-day'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const calendar=calendarFromSaved(state.advent2026Config,state.advent2026Days);
  const day=state.adventEditorDay;
  if(!calendar||!Number.isInteger(day)||day<1||day>25)
   throw Error('Aucune journée du calendrier à modifier.');
  const durationText=id('advent-day-minutes')?.value.trim()||'';
  const minutes=durationText===''?null:Number(durationText);
  const entry=calendar.days.find(x=>x.day===day);
  Object.assign(entry,{type:id('advent-day-type')?.value.trim(),
   title:id('advent-day-title')?.value.trim(),
   description:id('advent-day-description')?.value.trim(),timer_minutes:minutes});
  normalizeLibraryData(calendar,'advent'); // Validate all 25 days before mutation.
  if(!(await premiumConfirm('Enregistrer la modification du '+day+
    ' décembre ? Les autres journées et les résultats déjà ouverts restent identiques.',{
    kind:'library',title:'Enregistrer cette journée ?'})))return;
  await rpc('import_love_advent_2026',{p_couple:state.coupleId,p_calendar:calendar});
  state.adventEditorDay=null;state.jsonPending=null;
  await reload();notify('Journée '+day+' enregistrée. Le calendrier partagé reste intact.');return;
 }
 if(verb==='monthly-slot'){
  if(!isLoveAdmin())throw Error('Modification réservée à l’administrateur.');
  const face=Number(parts[0]);
  if(!Number.isInteger(face)||face<1||face>20)throw Error('Activité invalide.');
  state.monthlySlot=face;render();
  id('monthly-title')?.scrollIntoView({behavior:'smooth',block:'center'});return;
 }
 if(verb==='save-monthly-activity'){
  if(!isLoveAdmin())throw Error('Modification réservée à l’administrateur.');
  const title=id('monthly-title')?.value.trim();
  const description=id('monthly-description')?.value.trim();
  const restaurant=!!id('monthly-restaurant')?.checked;
  const duration=id('monthly-timer-enabled')?.checked?Number(id('monthly-minutes')?.value):null;
  if(duration!==null&&(!Number.isInteger(duration)||duration<1||duration>60))throw Error('Minuteur de 1 à 60 minutes.');
  if(!title||title.length<3||title.length>120||
    !description||description.length<10||description.length>900)
    throw Error('Titre de 3 à 120 caractères et description de 10 à 900 caractères requis.');
  await rpc('save_love_monthly_activity_with_timer',{
   p_couple:state.coupleId,p_face:state.monthlySlot,p_title:title,
   p_description:description,p_is_restaurant:restaurant,p_timer_minutes:duration});
  await reload();notify('Activité enregistrée pour les prochains tirages.');return;
 }
 if(verb==='reset-monthly-activity'){
  if(!isLoveAdmin())throw Error('Modification réservée à l’administrateur.');
  if(!(await premiumConfirm('Les anciens tirages resteront inchangés.',{kind:'reset',title:'Restaurer cette activité ?'})))return;
  await rpc('reset_love_monthly_activity',{p_couple:state.coupleId,p_face:state.monthlySlot});
  await reload();notify('Activité d’origine restaurée.');return;
 }
 if(verb==='save-draw-rule'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const enabled=!!id('no-repeat-cards')?.checked;
  await rpc('set_love_draw_rule',{p_couple:state.coupleId,p_no_repeat:enabled});
  await reload();
  notify(enabled?'Chaque carte doit être pigée avant de pouvoir revenir.':'Répétition libre activée.');
  return;
 }
 if(verb==='custom-category'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  if(!EVENING_CATEGORIES.includes(parts[0]))throw Error('Catégorie invalide.');
  state.customCategory=parts[0];state.customSlot=1;render();return;
 }
 if(verb==='custom-slot'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const slot=Number(parts[0]);if(!Number.isInteger(slot)||slot<1||slot>35)throw Error('Emplacement invalide.');
  state.customSlot=slot;render();
  id('custom-title')?.scrollIntoView({behavior:'smooth',block:'center'});return;
 }
 if(verb==='save-custom-card'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const title=id('custom-title')?.value.trim(),description=id('custom-description')?.value.trim();
  const timer=id('custom-timer-enabled')?.checked;
  const minutes=timer?Number(id('custom-minutes')?.value):null;
  if(!title||title.length<3||title.length>120||!description||description.length<10||
     description.length>900||(timer&&(!Number.isInteger(minutes)||minutes<1||minutes>60)))
   throw Error('Titre de 3 à 120 caractères, description de 10 à 900 caractères et minuteur facultatif de 1 à 60 minutes.');
  const recipient=document.querySelector('[name="custom-recipient"]:checked')?.value;
  const drawer=document.querySelector('[name="custom-drawer"]:checked')?.value;
  if(!['both','owner','partner'].includes(drawer)||!['both','owner','partner'].includes(recipient))
   throw Error('Choisissez qui peut piger et recevoir cette carte.');
  if(drawer===recipient&&drawer!=='both')throw Error('La même personne ne peut pas piger son propre défi.');
  await rpc('save_love_card_with_roles',{p_couple:state.coupleId,
   p_category:state.customCategory,p_card_id:state.customSlot,
   p_title:title,p_description:description,p_timer_minutes:minutes,
   p_recipient_role:recipient,p_drawer_role:drawer});
  await reload();notify('Carte enregistrée.');return;
 }
 if(verb==='delete-custom-card'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const original=state.customSlot<=25;
  if(!(await premiumConfirm(original?
   'La carte officielle et ses réglages seront restaurés. Les défis déjà tirés restent inchangés.':
   'Cet emplacement sera vidé. Les cartes déjà tirées restent dans l’historique.',
   {kind:original?'reset':'delete',title:original?'Rétablir la carte ?':'Vider cet emplacement ?'})))return;
  await rpc('delete_love_custom_card',{p_couple:state.coupleId,
   p_category:state.customCategory,p_card_id:state.customSlot});
  await reload();notify(original?'Carte officielle rétablie.':'Emplacement vidé, historique conservé.');return;
 }
 if(verb==='start-couple'){
  if(pendingInviteReference(sessionStorage))throw Error('Une invitation est en attente.');
  if(!state.coupleId)await rpc('create_love_match',{p_anniversary_md:null});
  state.tab='invitation';
  await reload();return;
 }
 if(verb==='end-couple'){
  if(!state.coupleId)return;
  if(!(await premiumConfirm('La dissociation s’appliquera aux deux personnes. Vos comptes, défis et cartes personnalisées seront conservés. Les anciennes données communes resteront privées et inaccessibles.',{kind:'couple'})))return;
  await rpc('end_love_match',{p_couple:state.coupleId});
  state.unlockKey=null;state.personalPass=null;
  state.inviteDraft=null;state.invitations=[];
  await reload();notify('Partenaire dissocié. Vos défis personnels sont conservés.');return;
 }
 if(verb==='save-wellbeing'){
  const relationship=id('wellbeing-relationship')?.value,intimacy=id('wellbeing-intimacy')?.value;
  if(!RELATIONSHIP_OPTIONS.some(([v])=>v===relationship)||!INTIMACY_OPTIONS.some(([v])=>v===intimacy))
   throw Error('Répondez aux deux questions ou choisissez « Je préfère ne pas répondre ».');
  const firstOnboarding=!state.wellbeing&&!!state.profile?.birthday_md;
  await rpc('save_my_wellbeing',{p_relationship:relationship,p_intimacy:intimacy});
  if(firstOnboarding)state.installAfterSignup=true;
  await reload();notify('Réponses enregistrées en privé.');return;
 }
 if(verb==='unlock-personal'){
  const pass=id('personal-pass')?.value||'';
  if(pass.trim().length<12)throw Error('La phrase secrète doit faire au moins 12 caractères.');
  const first=state.personalJournal[0];
  if(first){
   const key=await createCipherKey(pass,first.journal_salt);
   try{await decryptJson(first.ciphertext,key);}catch{throw Error('Phrase secrète incorrecte pour les premières entrées.');}
  }
  state.personalPass=pass;render();return;
 }
 if(verb==='personal-edit'){
  const entry=state.personalJournal?.find(x=>x.id===parts[0]);
  if(!entry||!state.personalPass)throw Error('Vous devez déverrouiller votre note personnelle.');
  const key=await createCipherKey(state.personalPass,entry.journal_salt);
  await decryptJson(entry.ciphertext,key);
  state.personalEditId=entry.id;state.personalSelectedDate=entry.happened_on;render();return;
 }
 if(verb==='personal-cancel-edit'){state.personalEditId=null;render();return;}
 if(verb==='personal-save'){
  const entry=state.personalJournal?.find(x=>x.id===parts[0]&&x.id===state.personalEditId);
  if(!entry||!state.personalPass)throw Error('Modification non autorisée.');
  const date=id('personal-edit-date')?.value||entry.happened_on;
  if(date>today())throw Error('La date ne peut pas être dans le futur.');
  const key=await createCipherKey(state.personalPass,entry.journal_salt);
  const payload=await decryptJson(entry.ciphertext,key);
  Object.assign(payload,{orgasm:id('personal-edit-orgasm')?.value,
   good:id('personal-edit-good')?.value,weak:id('personal-edit-weak')?.value,
   note:(id('personal-edit-note')?.value||'').slice(0,1200)});
  const ciphertext=await encryptJson(payload,key);
  await rpc('update_my_personal_love_event',{p_archive:entry.id,
   p_happened_on:date,p_ciphertext:ciphertext});
  state.personalEditId=null;state.personalSelectedDate=date;
  await reload();notify('Votre note privée a été modifiée sans changer le journal partagé.');return;
 }
 if(verb==='personal-delete'){
  const entry=state.personalJournal?.find(x=>x.id===parts[0]);
  if(!entry||!state.personalPass)throw Error('Accès refusé.');
  if(!(await premiumConfirm('Supprimer seulement votre archive personnelle ? Le journal partagé ne sera pas modifié.',
   {kind:'delete',title:'Supprimer ma note privée ?'})))return;
  await rpc('delete_my_personal_love_event',{p_archive:entry.id});
  state.personalEditId=null;await reload();notify('Note personnelle supprimée.');return;
 }
 if(verb==='lock-personal'){state.personalPass=null;state.personalEditId=null;render();return;}
 if(verb==='personal-date'){state.personalSelectedDate=parts[0];render();return;}
 if(verb==='personal-month-prev'||verb==='personal-month-next'){
  const d=new Date((state.personalCalendarMonth||month())+'-01T12:00:00Z');
  d.setUTCMonth(d.getUTCMonth()+(verb==='personal-month-next'?1:-1));
  state.personalCalendarMonth=d.toISOString().slice(0,7);render();return;
 }
 if(verb==='show-auth'){
  if(pendingInviteReference(sessionStorage)&&!state.inviteAccepted)
   throw Error('Acceptez d’abord l’invitation.');
  // Both landing buttons must enter their actual requested authentication flow.
  state.authMode=parts[0]==='signin'?'signin':'signup';
  keepAuthModeInUrl(state.authMode);
  state.authNeedsEmailConfirmation=false;
  state.authNotice=null;state.passwordRecovery=false;
  state.showDiscover=false;state.showAuth=true;render();return;
 }
 if(verb==='show-landing'){keepAuthModeInUrl(null);state.showDiscover=false;state.showAuth=false;render();return;}
 if(verb==='google'&&window.LoveRollNativeAuth?.googleSignIn){
  try{
   state.authNotice='Connexion Google en cours… Sélectionnez votre compte dans la fenêtre Android.';
   const message=id('auth-form-feedback');
   if(message)message.textContent=state.authNotice;
   window.LoveRollNativeAuth.googleSignIn();
  }catch(error){
   state.authNotice='Connexion Google Android indisponible. Réessayez, ou utilisez le courriel et le mot de passe de votre compte existant.';
   const message=id('auth-form-feedback');
   if(message)message.textContent=state.authNotice;
  }
  return;
 }
 if(verb==='google'||verb==='apple'){
  sessionStorage.setItem('authStarted','1');
  const redirectUrl=new URL(CONFIG.siteUrl||location.origin+location.pathname);
  const pendingId=sessionStorage.getItem('pendingInviteId');
  if(pendingId){redirectUrl.searchParams.set('invite_id',pendingId);redirectUrl.searchParams.set('v',INVITATION_REVISION);}
  const redirectTo=redirectUrl.toString();
  // Google officially supports prompt=select_account; do not clear global
  // Google cookies or silently reuse a different partner's identity.
  const options=verb==='google'?{redirectTo,queryParams:{prompt:'select_account'}}:{redirectTo};
  const {error}=await client.auth.signInWithOAuth({provider:verb,options});
  if(error)throw error;return;
 }
 if(verb==='auth-mode'){
  state.authDraftEmail=id('signin-email')?.value.trim()||state.authDraftEmail;
  state.authMode=parts[0]==='signin'?'signin':'signup';
  keepAuthModeInUrl(state.authMode);
  state.authNeedsEmailConfirmation=false;
  state.authNotice=null;state.passwordRecovery=false;render();return;
 }
 if(verb==='auth-eye'){
  const field=id('signin-password');if(!field)return;
  field.type=field.type==='password'?'text':'password';
  element?.setAttribute('aria-pressed',String(field.type==='text'));
  element?.setAttribute('aria-label',field.type==='password'?'Afficher le mot de passe':'Masquer le mot de passe');
  return;
 }
 if(verb==='auth-signup-password'||verb==='auth-signin-password'){
  let email,password;
  try{email=loginEmail(id('signin-email')?.value);
    password=id('signin-password')?.value||'';
    if(verb==='auth-signup-password')requireLovePassword(password);
    else if(!password)throw Error('Entrez votre mot de passe.');
  }catch(error){authMessage(error.message);return;}
  const btn=element;if(btn?.disabled)return;if(btn)btn.disabled=true;
  try{
   if(verb==='auth-signup-password'){
    const {data,error}=await client.auth.signUp({email,password,
     options:{emailRedirectTo:pendingInviteUrl()}});
    if(error){authMessage(passwordAuthMessage(error));return;}
    state.authDraftEmail=email;
    if(data?.session?.user&&verifiedLoveEmail(data.session.user)){
     state.user=data.session.user;state.showAuth=false;await reload();return;
    }
    // No session: this may be a new signup awaiting verification OR an
    // existing Google identity. Never claim that a message was delivered.
    state.emailVerificationPending=true;
    state.authNotice=null;render();
   }else{
    const {data,error}=await client.auth.signInWithPassword({email,password});
    if(error){
     state.authDraftEmail=email;
     state.authNeedsEmailConfirmation=error.code==='email_not_confirmed'||
      /email not confirmed|courriel non confirm/i.test(error.message||'');
     if(state.authNeedsEmailConfirmation){
      state.authNotice='Votre adresse courriel doit être confirmée avant de vous connecter. Vous pouvez demander un nouveau courriel ci-dessous.';
      render();
     }else authMessage(passwordAuthMessage(error));
     return;
    }
    if(!data?.user)throw Error('Aucune session ouverte.');
    state.authNeedsEmailConfirmation=false;
    state.user=data.user;state.authNotice=null;state.showAuth=false;
    await reload();
   }
  }catch(error){authMessage(passwordAuthMessage(error));}
  finally{if(btn)btn.disabled=false;}
  return;
 }
 if(verb==='email-confirm-login'){
  if(state.user&&!verifiedLoveEmail(state.user)){
   const {error}=await client.auth.signOut();
   if(error)throw error;
   state.user=null;
  }
  state.emailVerificationPending=false;state.showDiscover=false;state.showAuth=true;
  state.authMode='signin';keepAuthModeInUrl('signin');
  state.authNotice='Si vous avez déjà créé un compte avec Google, utilisez « Continuer avec Google ».';
  render();return;
 }
 if(verb==='email-confirm-recheck'){
  if(!state.user){
   state.emailVerificationPending=false;state.showDiscover=false;state.showAuth=true;
   state.authMode='signin';keepAuthModeInUrl('signin');
   state.authNotice='Connectez-vous pour que LoveRoll vérifie votre compte confirmé auprès de Supabase.';
   render();return;
  }
  if(await refreshVerifiedIdentity()){
   state.emailVerificationPending=false;state.authNotice=null;
   await reload();return;
  }
  const status=id('email-confirm-status');if(status)status.textContent=
   'Votre adresse n’est pas encore confirmée dans Supabase. Ouvrez le lien reçu.';
  return;
 }
 if(verb==='email-confirm-resend'||verb==='auth-resend-confirmation'){
  const btn=element;if(btn?.disabled)return;
  let email;
  try{email=loginEmail(id('signin-email')?.value||state.authDraftEmail||state.user?.email);}
  catch(error){authMessage(error.message);return;}
  const until=Number(sessionStorage.getItem('loverollConfirmResendAfter')||0);
  if(Date.now()<until){authMessage('Veuillez patienter avant de demander un autre courriel.');return;}
  if(btn)btn.disabled=true;
  try{
   const {error}=await client.auth.resend({type:'signup',email,
    options:{emailRedirectTo:pendingInviteUrl()}});
   if(error){authMessage(passwordAuthMessage(error));return;}
   state.authDraftEmail=email;
   sessionStorage.setItem('loverollConfirmResendAfter',String(Date.now()+60000));
   const status=id('auth-resend-status')||id('email-confirm-status');
   const msg='Demande reçue par le serveur. Cela ne garantit pas la réception du courriel. Vérifiez votre boîte et les indésirables. Si ce compte est déjà confirmé avec Google, utilisez « Continuer avec Google ».';
   if(status)status.textContent=msg;else authMessage(msg);
  }catch(error){authMessage(passwordAuthMessage(error));}
  finally{if(btn)btn.disabled=false;}
  return;
 }
 if(verb==='email-confirm-edit'){
  if(state.user)await client.auth.signOut();
  state.user=null;state.emailVerificationPending=false;state.authDraftEmail='';
  state.authMode='signup';state.showAuth=true;render();return;
 }
 if(verb==='auth-reset-request'){
  state.authDraftEmail=id('signin-email')?.value.trim()||state.authDraftEmail;
  state.authMode='request-reset';keepAuthModeInUrl('request-reset');state.authNeedsEmailConfirmation=false;
  state.authNotice=null;state.showAuth=true;render();return;
 }
 if(verb==='auth-send-reset'){
  let email;
  try{email=loginEmail(id('signin-email')?.value);}catch(error){authMessage(error.message);return;}
  const until=Number(sessionStorage.getItem('loverollResetAfter')||0);
  if(Date.now()<until){authMessage('Veuillez patienter avant une nouvelle demande.');return;}
  const btn=element;if(btn?.disabled)return;if(btn)btn.disabled=true;
  try{
   const {error}=await client.auth.resetPasswordForEmail(email,{redirectTo:pendingInviteUrl(true)});
   if(error){authMessage(passwordAuthMessage(error));return;}
   state.authDraftEmail=email;
   sessionStorage.setItem('loverollResetAfter',String(Date.now()+60000));
   authMessage('Demande de récupération acceptée. Si ce compte existe, le fournisseur pourra envoyer un lien. Vérifiez vos courriels; sa réception n’est pas encore confirmée.');
  }catch(error){authMessage(passwordAuthMessage(error));}
  finally{if(btn)btn.disabled=false;}
  return;
 }
 if(verb==='auth-update-password'){
  if(!state.passwordRecovery||!state.user){authMessage('Ouvrez le lien sécurisé reçu par courriel.');return;}
  let password;
  try{password=requireLovePassword(id('signin-password')?.value);}
  catch(error){authMessage(error.message);return;}
  const btn=element;if(btn?.disabled)return;if(btn)btn.disabled=true;
  try{
   const {error}=await client.auth.updateUser({password});
   if(error){authMessage(passwordAuthMessage(error));return;}
   state.passwordRecovery=false;state.authMode='signin';state.authNotice=null;
   const url=new URL(location.href);url.searchParams.delete('auth');url.searchParams.delete('type');
   history.replaceState(null,'',url.pathname+url.search+url.hash);
   await reload();
  }catch(error){authMessage(passwordAuthMessage(error));}
  finally{if(btn)btn.disabled=false;}
  return;
 }
 if(verb==='signout'){
  if(state.user&&safeNativePushToken()){
   try{await disablePush(client,state.user.id);}catch(err){console.warn('Désinscription FCM',err.code||'');}
  }
  state.unlockKey=null;state.personalPass=null;state.personalJournal=[];
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  const {error}=await client.auth.signOut();if(error)throw error;
  state.user=null;state.profile=null;state.coupleId=null;state.showAuth=false;state.passwordRecovery=false;
  keepAuthModeInUrl(null);
  state.installAfterSignup=false;state.installModalOpen=false;state.showInstallSteps=false;state.forceInstallGuide=false;
  render();return;
 }
 if(verb==='complete-registration'){
  const name=id('profile-name')?.value.trim();
  if(!name||name.length>80)throw Error('Choisis ton nom à afficher.');
  const birthday=parseMonthDay('signup-birth');
  if(!id('age-confirm')?.checked)throw Error('LoveRoll est réservé aux personnes de 18 ans et plus.');
  const relationship=id('wellbeing-relationship')?.value,intimacy=id('wellbeing-intimacy')?.value;
  if(!RELATIONSHIP_OPTIONS.some(([v])=>v===relationship)||!INTIMACY_OPTIONS.some(([v])=>v===intimacy))
   throw Error('Répondez aux deux questions ou choisissez « Je préfère ne pas répondre ».');
  await rpc('complete_love_registration',{p_name:name,p_birthday_md:birthday});
  await rpc('save_my_wellbeing',{p_relationship:relationship,p_intimacy:intimacy});
  state.installAfterSignup=true;
  await reload();return;
 }
 if(verb==='profile'){
  const name=id('profile-name')?.value.trim();
  if(!name||name.length>80)throw Error('Choisis ton nom à afficher.');
  const birthday=parseMonthDay('settings-birthday');
  await rpc('complete_love_registration',{p_name:name,p_birthday_md:birthday});
  await reload();notify('Votre profil et votre date sont enregistrés.');return;
 }
 if(verb==='prepare-cards'){
  if(pendingInviteReference(sessionStorage))throw Error('Une invitation est en attente.');
  if(!state.coupleId)await rpc('create_love_match',{p_anniversary_md:null});
  state.tab='library';
  await reload();
  notify('Votre bibliothèque privée est prête. Invitez votre conjointe plus tard.');
  return;
 }
 if(verb==='create-couple'){
  const date=parseMonthDay('signup-couple',false);
  await rpc('create_love_match',{p_anniversary_md:date});
  await reload();return;
 }
 if(verb==='invite'){
  if(state.invitations?.some(x=>!x.cancelled_at&&!x.accepted_at&&new Date(x.expires_at)>new Date())&&
     !(await premiumConfirm('L’ancien lien d’invitation cessera de fonctionner. Votre partenaire devra utiliser le nouveau lien.',{kind:'library',title:'Remplacer le lien sécurisé ?'})))return;
  await ensureInviteDraft(true);state.inviteCopyFallback=false;render();
  notify('Nouveau lien prêt. L’ancien lien a été remplacé.');return;
 }
 if(verb==='send-sms'||verb==='send-email'||verb==='send-messenger'||verb==='copy-invite'){
  // Reuse the unexpired invitation ID after reload. The server verifies email at acceptance.
  const d=await ensureInviteDraft(false);
  if(verb==='copy-invite'){
   try{
    if(!navigator.clipboard?.writeText)throw Error('Presse-papiers indisponible');
    await navigator.clipboard.writeText(d.link);
    state.inviteCopyFallback=false;notify('Lien copié, sans modifier votre invitation.');
   }catch(error){
    state.inviteCopyFallback=true;render();
    const field=id('invite-manual-link');
    field?.focus();field?.select();
    notify('Copie automatique indisponible : sélectionnez le lien affiché pour le copier.');
   }
   return;
  }
  const text=verb==='send-messenger'?invitationMessengerText(d.link):invitationEmailText(d.link);
  state.lastShareMethod=verb==='send-sms'?'sms':verb==='send-email'?'courriel':'messenger';
  if(verb==='send-messenger'){
   if(window.LoveRollNativeAuth?.openShare)window.LoveRollNativeAuth.openShare(text);
   else if(navigator.share){
    try{await navigator.share({url:d.link});}
    catch(error){if(error.name!=='AbortError')throw error;}
   }else{
    try{await navigator.clipboard.writeText(d.link);
     notify('Lien copié : collez-le dans une conversation Messenger.');}
    catch{state.inviteCopyFallback=true;render();id('invite-manual-link')?.select();
     notify('Sélectionnez le lien affiché pour le partager sur Messenger.');}
    return;
   }
  }else if(verb==='send-sms'){
   if(window.LoveRollNativeAuth?.openSms)window.LoveRollNativeAuth.openSms(text);
   else{
    const isApple=/iPhone|iPad|iPod/i.test(navigator.userAgent);
    window.location.href='sms:'+(isApple?'&':'?')+'body='+encodeURIComponent(text);
   }
  }else{
   const subject='LoveRoll ❤️ Une invitation juste pour nous deux';
   if(window.LoveRollNativeAuth?.openEmail)window.LoveRollNativeAuth.openEmail(d.email,subject,text);
   else window.location.href=composeInviteEmail(d.email,subject,text);
  }
  notify('Choisissez le destinataire et confirmez vous-même le partage.');
  return;
 }
 if(verb==='confirm-invite-sent'){
  const invitation=await ensureInviteDraft(false);
  await rpc('mark_pair_invite_sent',{p_invitation:invitation.id,p_method:state.lastShareMethod||'partage'});
  state.invitations=await query('couple_invitations',{eq:{couple_id:state.coupleId},order:'created_at'});
  render();notify('Envoi confirmé manuellement.');return;
 }
 if(verb==='cancel-invite'){
  await rpc('cancel_pair_invite',{p_invitation:parts[0]});
  state.inviteDraft=null;
  state.invitations=await query('couple_invitations',{eq:{couple_id:state.coupleId},order:'created_at'});
  render();notify('Invitation annulée.');return;
 }
 if(verb==='choose-feedback'){
  const sessionId=parts[0];
  if(!state.sessions.some(x=>x.id===sessionId&&x.finished_at&&x.counted))
   throw Error('Cette soirée terminée est introuvable.');
  state.chosenSession=sessionId;
  try{
   const items=await rpc('my_love_feedback_status',{p_session:sessionId});
   state.feedbackStatus={sessionId,items:Array.isArray(items)?items:[]};
  }catch(error){state.feedbackStatus=null;}
  state.tab='feedback';render();return;
 }
 if(verb==='save-couple-date'){
  const value=id('couple-started-on')?.value||null;
  await rpc('set_love_relationship_date',{p_couple:state.coupleId,p_date:value});
  await refreshShared();render();notify('Date du couple enregistrée.');return;
 }
 if(verb==='accept-invite'||verb==='retry-invite'){
  state.inviteAttemptKey=null;state.inviteError=null;
  await reload();return;
 }
 if(verb==='tab'){
  const ready=isSharedCoupleReady(state.coupleId,state.members);
  const allowed=ready?
   ['home','session','special','occasions','advent','settings','library',
    'personal-journal','journal','ideas','feedback','monthly-library','advent-editor','special-editor']:
   ['home','settings','invitation','library','personal-journal','monthly-library','advent-editor','special-editor'];
  if(!allowed.includes(parts[0]))throw Error('Les jeux exigent deux comptes associés.');
  if(parts[0]==='session'&&!state.weekly){
   state.tab='session';state.showEveningChallenge=false;state.eveningMore=false;
   render();
   notify('♡ Brassez le dé hebdomadaire à l’accueil pour débloquer votre soirée.');
   return;
  }
  if(['library','monthly-library','advent-editor','special-editor'].includes(parts[0])&&!isLoveAdmin())
   throw Error('Modification réservée à l’administrateur du couple.');
  if(parts[0]!==state.tab&&hasUnsavedForm()&&
    !(await premiumConfirm('Vos modifications non enregistrées seront perdues. Voulez-vous quitter cette page ?',{
      kind:'library',title:'Quitter sans enregistrer ?',accept:'Quitter',cancel:'Rester'
    })))return;
  if(parts[0]==='settings'&&state.tab==='occasions'){
   settingsOpenSection='couple';
   try{sessionStorage.setItem('loveroll-settings-section','couple');}catch{}
  }
  allowBackExit=false;state.tab=parts[0];state.confirmCancel=false;
  if(state.tab!=='occasions')state.specialFocus=null;
  if(state.tab!=='session'){state.showEveningChallenge=false;state.eveningMore=false;}
  if(state.tab!=='special')state.showSurpriseActivity=false;
  if(state.tab!=='advent'){state.adventSelected=null;state.adventHistory=false;state.adventOpening=null;}
  render();return;
 }
 if(['save-occasion-date','occasion-roll','roll','new-session','my-d6',
    'new-challenge','answer','finish-session','cancel-session','confirm-cancel',
    'save-event','delete-event','save-feedback'].includes(verb)&&
    !isSharedCoupleReady(state.coupleId,state.members))
   throw Error('Cette action nécessite deux comptes associés.');
 if(verb==='save-occasion-date'){
  const k=parts[0],d=id(k==='anniversary'?'loveroll-anniversary':'loveroll-birthday')?.value.trim();
  if(d&&!/^\\d{2}-\\d{2}$/.test(d))throw Error('Saisissez MM-JJ, par exemple 02-14.');
  await rpc('set_love_occasion_date',{p_couple:state.coupleId,p_kind:k,p_month_day:d||null});
  await refreshAndShow();notify('Date enregistrée.');return;
 }
 if(verb==='occasion-roll'){
  const [kind,owner,rollType]=parts;
  const x=await animateRoll(()=>rpc('roll_love_occasion_once',{p_couple:state.coupleId,
   p_kind:kind,p_owner:owner==='-'?null:owner,p_roll:rollType}),'occasion');
  notify('Résultat '+x.face+' · '+person(x.actor));return;
 }
 if(verb==='roll'){
  const kind=parts[0];
  if(kind==='weekly'){
   if(!isSharedCoupleReady(state.coupleId,state.members))
    throw Error('Associez deux comptes pour le dé hebdomadaire.');
   if(state.weekly)throw Error('Le dé a déjà été brassé cette semaine.');
   if(state.diceStatus)return;
   state.diceStatus='pending';render();
   try{
    // Supabase selects and persists the one true face under a couple row lock;
    // only after the response do we animate the ACTUAL server result.
    const result=await rpc('roll_couple_once',{p_couple:state.coupleId,p_kind:'weekly'});
    state.rollAnimationFace=result.face;
    state.diceStatus='rolling';render();
    await new Promise(resolve=>setTimeout(resolve,3250));
    state.diceStatus=null;state.rollAnimationFace=null;
    state.weekly=result;
    await refreshAndShow();
    notify('Résultat officiel : '+result.face+' · tirage de '+person(result.actor));
    }catch(error){state.diceStatus=null;state.rollAnimationFace=null;await refreshAndShow();throw error;}
   return;
  }
  const result=await animateRoll(()=>rpc('roll_couple_once',{p_couple:state.coupleId,p_kind:kind}),kind);
  notify('Résultat officiel : '+result.face+' · tirage de '+person(result.actor));return;
 }
 if(verb==='new-session'){
  if(!state.weekly)throw Error('Brassez d’abord le dé de la semaine.');
  await rpc('ensure_couple_session',{p_couple:state.coupleId});
  state.tab='session';await refreshAndShow();return;
 }
 if(verb==='my-d6'){
  if(!state.session)throw Error('Aucune soirée active.');
  await animateRoll(()=>rpc('roll_my_evening_d6',{p_session:state.session.id}),'my-d6');
  if(!state.rollStatus?.both_ready)notify('Dé lancé! Les deux résultats apparaîtront ensemble après le dernier tirage.');
  else notify('♡ Les deux résultats sont maintenant révélés!');return;
 }
 if(verb==='category'){
  if(!EVENING_CATEGORIES.includes(parts[0]))throw Error('Catégorie interdite');
  state.category=parts[0];state.randomMode=false;
  if(state.session)try{sessionStorage.setItem('loveroll-category-'+state.session.id,parts[0]);}catch{}
  render();return;
 }
  if(verb==='timer-mode'){
   if(parts[0]!=='vibrate'&&parts[0]!=='sound')throw Error('Signal inconnu');
   challengeTimer.mode=parts[0];localStorage.setItem('loveroll-timer-mode',parts[0]);
   if(parts[0]==='vibrate'&&typeof navigator.vibrate!=='function')
     notify('La vibration n’est pas disponible ici. Choisis Son doux pour une alerte audible.');
   render();return;
 }
 if(verb==='timer-reset'){
   challengeTimer.running=false;
   challengeTimer.deadline=0;
   challengeTimer.remainingMs=challengeTimer.durationMs;
   challengeTimer.notified=false;updateChallengeClock();return;
 }
 if(verb==='timer-toggle'){
   if(!challengeTimer.id)throw Error('Aucun défi avec minuteur');
   if(challengeTimer.running){
    challengeTimer.remainingMs=Math.max(0,challengeTimer.deadline-Date.now());
    challengeTimer.running=false;
   }else if(challengeTimer.remainingMs>0){
    if(challengeTimer.mode==='sound'){
      try{
       timerCtx=timerCtx||new (window.AudioContext||window.webkitAudioContext)();
       await timerCtx.resume();
      }catch(e){notify('Le navigateur ne permet pas encore la sonnerie.');}
    }
    challengeTimer.deadline=Date.now()+challengeTimer.remainingMs;
    challengeTimer.running=true;
    challengeTimer.notified=false;
   }
   updateChallengeClock();return;
 }
 if(verb==='give-challenge'){
  if(!state.session?.paired_turns||!state.session?.self_draws||!state.rollStatus?.both_ready)
   throw Error('Attendez vos deux tirages personnels pour donner un défi.');
  const me=state.user.id,otherPerson=other();
  const turn=sharedEveningTurns(state.session,state.members,state.personal,state.challenges,state.gifts);
  if(!turn.ready||turn.remaining[me]<=0)
   throw Error('Aucun défi non commencé à donner.');
  const monthCount=state.gifts.filter(g=>g.donor_user_id===me&&g.month_key?.slice(0,7)===month()).length;
  if(monthCount>=3)throw Error('Vos 3 dons du mois sont utilisés.');
  if(!(await premiumConfirm('Offrir un de tes défis restants à '+otherPerson.name+
   ' ? Son nombre de défis à recevoir augmentera de 1 et le tien diminuera de 1.',
   {kind:'gift'})))return;
  await rpc('give_love_challenge',{p_session:state.session.id});
  await refreshAndShow();
  notify('♥ Défi transféré ! '+otherPerson.name+' choisira la catégorie de son prochain défi.');
  return;
 }
 if(verb==='new-challenge'){
  if(!state.session)throw Error('Aucune soirée active.');
  if(!EVENING_CATEGORIES.includes(state.category))
   throw Error('Choisissez Sensuel ou Sexuel avant chaque nouveau défi.');
  await animateRoll(()=>rpc('draw_for_partner',{p_session:state.session.id,
   p_preference:state.category,p_random:false}),'new-challenge');
  try{sessionStorage.removeItem('loveroll-category-'+state.session.id);}catch{}
  state.category=null; // Require a fresh category choice for the next challenge.
  render();
  return;
 }
 if(verb==='answer'){await rpc('answer_challenge',{p_id:parts[0],p_status:parts[1]});
  state.showEveningChallenge=false;await refreshAndShow();return;}
 if(verb==='finish-session'){
  if(!state.session)return;
  const finishedId=state.session.id;
  await rpc('finish_couple_session',{p_session:finishedId,p_count:true});
  state.chosenSession=finishedId;state.tab='session';await refreshAndShow();return;
 }
 if(verb==='cancel-session'){state.confirmCancel=true;render();return;}
 if(verb==='dismiss-cancel'){state.confirmCancel=false;render();return;}
 if(verb==='confirm-cancel'){
  if(!state.session)return;
  await rpc('finish_couple_session',{p_session:state.session.id,p_count:false});
  state.confirmCancel=false;state.tab='home';await refreshAndShow();return;
 }
 if(verb==='advent-open'){
  if(!isSharedCoupleReady(state.coupleId,state.members))
   throw Error('Le calendrier appartient à votre couple associé.');
  if(state.adventOpening!==null)return;
  const day=Number(parts[0]),date=state.adventServerDate||today();
  const year=Number(date.slice(0,4));
  const cal=sharedAdventState(state.advent,new Date(date+'T16:00:00Z'),state.adventYear);
  if(!cal.days[day-1]?.canOpen)throw Error('Seule la case du jour peut être ouverte.');
  state.adventOpening=day;render();
  try{
   // Only the server commits an opening; concurrent partners receive one row.
   const opened=await rpc('open_advent_day',{p_couple:state.coupleId,p_day:day});
   if(!opened||opened.year!==year||opened.day!==day)
    throw Error('Ouverture non confirmée.');
   await refreshShared();
   await new Promise(resolve=>setTimeout(resolve,900));
   state.adventOpening=null;state.adventSelected=day;state.adventHistory=false;render();
  }catch(error){
   state.adventOpening=null;await refreshShared();render();throw error;
  }
  return;
 }
 if(verb==='advent-view'){
  const day=Number(parts[0]),year=state.adventYear||Number((state.adventServerDate||today()).slice(0,4));
  if(!state.advent.some(row=>row.year===year&&row.day===day))
   throw Error('Cette surprise n’a pas encore été ouverte.');
  state.adventSelected=day;render();return;
 }
 if(verb==='advent-close'){state.adventSelected=null;render();return;}
 if(verb==='advent-history'){state.adventHistory=!state.adventHistory;render();return;}
 if(verb==='advent-year'){
  const year=Number(parts[0]),current=Number((state.adventServerDate||today()).slice(0,4));
  if(!Number.isInteger(year)||(year!==current&&!state.advent.some(row=>row.year===year)))
   throw Error('Aucun calendrier enregistré pour cette année.');
  state.adventYear=year;state.adventHistory=false;state.adventSelected=null;render();return;
 }
 if(verb==='unlock'){
  const pass=id('journal-pass')?.value||'';
  const savedProbe=state.couple?.journal_verifier||null;
  const hasLegacy=state.journal.length>0||state.feedback.length>0;
  const creating=!savedProbe&&!hasLegacy;
  if(creating&&pass!==id('journal-pass-confirm')?.value)
   throw Error('Les deux phrases secrètes doivent être identiques.');
  const key=await createCipherKey(pass);
  if(creating){
   // Only a ciphertext proving the key can be opened is stored remotely.
   const envelope=await encryptJson({purpose:'loveroll-journal-check',version:1},key);
   await rpc('initialize_love_journal',{p_couple:state.coupleId,p_verifier:envelope});
   state.couple.journal_verifier=envelope;
  }else{
   try{
    const probe=savedProbe||state.journal[0]?.ciphertext||state.feedback[0]?.ciphertext;
    if(!probe)throw Error('Aucune preuve de clé disponible.');
    const clear=await decryptJson(probe,key);
    if(savedProbe&&clear?.purpose!=='loveroll-journal-check')
     throw Error('Vérification du journal invalide.');
   }catch{state.unlockKey=null;throw Error('Phrase secrète incorrecte pour les données existantes.');}
  }
  state.unlockKey=key;
  notify(creating?'Phrase secrète du journal créée. Partagez-la en personne avec votre partenaire.':
   'Journal déverrouillé sur cet appareil.');render();return;
 }
 if(verb==='lock'){state.unlockKey=null;render();return;}
 if(verb==='date'){state.selectedDate=parts[0];render();return;}
 if(verb==='month-prev'||verb==='month-next'){
  const offset=verb==='month-next'?1:-1;
  const d=new Date((state.calendarMonth||month())+'-01T12:00:00Z');
  d.setUTCMonth(d.getUTCMonth()+offset);
  state.calendarMonth=d.toISOString().slice(0,7);render();return;
 }
 if(verb==='edit-event'){
  const entry=state.journal.find(x=>x.id===parts[0]&&x.author_id===state.user.id);
  if(!entry||!state.unlockKey)throw Error('Vous ne pouvez modifier que votre entrée déverrouillée.');
  state.editEventId=entry.id;state.selectedDate=entry.happened_on;render();return;
 }
 if(verb==='cancel-edit-event'){state.editEventId=null;render();return;}
 if(verb==='save-event'){
  if(!state.unlockKey)throw Error('Journal verrouillé');
  const date=id('event-date')?.value||today();
  if(date>today())throw Error('La date ne peut pas être dans le futur.');
  const payload={orgasm:id('event-orgasm').value,good:id('event-good').value,
    weak:id('event-weak').value,note:id('event-note').value.slice(0,1200)};
  const ciphertext=await encryptJson(payload);
  const wasEdit=!!state.editEventId;
  if(wasEdit)await rpc('update_my_love_event',{p_event:state.editEventId,p_happened_on:date,p_ciphertext:ciphertext});
  else await rpc('add_my_love_event',{p_couple:state.coupleId,p_happened_on:date,p_ciphertext:ciphertext});
  state.editEventId=null;state.selectedDate=date;
  await reload();notify(wasEdit?'Entrée partagée modifiée.':'Moment ajouté au calendrier partagé et à votre calendrier privé');return;
 }
 if(verb==='delete-event'){
  if(!(await premiumConfirm('Cette entrée sera supprimée du journal partagé. Une copie personnelle peut demeurer dans votre calendrier privé.',{kind:'delete',title:'Supprimer du journal partagé ?'})))return;
  const {error}=await client.from('couple_intimacy').delete().eq('id',parts[0]).eq('author_id',state.user.id);
  if(error)throw error;await refreshAndShow();return;
 }
 if(verb==='save-feedback'){
  if(!state.chosenSession)throw Error('Aucune soirée sélectionnée.');
  const rating=Number(document.querySelector('[name=rating]:checked')?.value);
  if(!(rating>=1&&rating<=5))throw Error('Choisissez une note de 1 à 5.');
  const payload={rating,liked:id('feedback-liked').value,next:id('feedback-next').value};
  const ciphertext=await encryptJson(payload);
  const {error}=await client.from('couple_feedback').upsert({
    session_id:state.chosenSession,couple_id:state.coupleId,
    author_id:state.user.id,ciphertext},{onConflict:'session_id,author_id'});
  if(error)throw error;
  await refreshAndShow();notify('Votre avis est enregistré. Il sera visible quand vous aurez répondu tous les deux.');return;
 }
}
async function start(){
 const currentUrl=new URL(location.href);
 const frag=new URLSearchParams(currentUrl.hash.replace(/^#/,''));
 const errorCode=currentUrl.searchParams.get('error_code')||frag.get('error_code');
 const authError=currentUrl.searchParams.get('error')||frag.get('error');
 if(errorCode||authError){
  // An expired/used recovery link MUST return to sign-in, not a password form
  // that cannot submit without a valid Supabase recovery session.
  if(state.passwordRecovery){
   state.passwordRecovery=false;state.authMode='signin';
   currentUrl.searchParams.set('auth','signin');
  }
  state.authNotice=/expired|invalid|not_found/i.test(String(errorCode||''))?
   'Le lien est expiré ou a déjà été utilisé. Si ton courriel est confirmé, tu peux te connecter avec Google ou demander un nouveau lien lorsque la limite d’envoi sera levée.':
   'La confirmation n’a pas ouvert de session. Tu peux te connecter avec Google ou utiliser un nouveau lien de connexion plus tard.';
  state.showAuth=true;
  for(const key of ['error','error_code','error_description']){
   currentUrl.searchParams.delete(key);
   frag.delete(key);
  }
  currentUrl.hash=frag.toString();
  history.replaceState(null,'',currentUrl.pathname+currentUrl.search+currentUrl.hash);
 }
 let incoming=parseInvitationFromUrl(currentUrl.toString());
 // Backward compatibility: replace legacy secret fragments with a new URL
 // reference. Chrome can then open the SAME invitation from Messenger.
 if(incoming.token&&client){
  try{
   const {data,error}=await client.rpc('resolve_love_invite_reference',
    {p_token_hash:await hashToken(incoming.token)});
   if(!error&&data){
    const migrated=inviteLinkForId(CONFIG.siteUrl,data);
    const oldRef=pendingInviteReference(sessionStorage);
    const oldKey=oldRef?oldRef.kind+':'+oldRef.value:null;
    if(oldKey==='token:'+incoming.token&&
       sessionStorage.getItem('loverollInviteAccepted')===oldKey)
     sessionStorage.setItem('loverollInviteAccepted','id:'+data);
    const rewritten=new URL(migrated);
    currentUrl.search=rewritten.search;currentUrl.hash='';
    history.replaceState(null,'',currentUrl.pathname+currentUrl.search);
    incoming={id:data,token:null};
   }
  }catch(error){console.warn('Lien ancien conservé',error);}
 }
 if(incoming.id||incoming.token){
  const old=pendingInviteReference(sessionStorage);
  const kind=incoming.id?'id':'token',value=incoming.id||incoming.token;
  const key=kind+':'+value;
  if(!old||old.kind!==kind||old.value!==value)
   sessionStorage.removeItem('loverollInviteAccepted');
  if(kind==='id'){
   sessionStorage.setItem('pendingInviteId',value);
   sessionStorage.removeItem('pendingInvite');
   // Keep the non-secret reference in the URL for Messenger and OAuth handoffs.
  }else{
   sessionStorage.setItem('pendingInvite',value);
   sessionStorage.removeItem('pendingInviteId');
   const frag=new URLSearchParams(currentUrl.hash.replace(/^#/, ''));
   frag.delete('invite');currentUrl.searchParams.delete('invite');
   currentUrl.hash=frag.toString();
   history.replaceState(null,'',currentUrl.pathname+currentUrl.search+currentUrl.hash);
  }
  state.inviteAccepted=sessionStorage.getItem('loverollInviteAccepted')===key;
  state.showAuth=state.inviteAccepted;
 }else{
  const existing=pendingInviteReference(sessionStorage);
  state.inviteAccepted=!!existing&&sessionStorage.getItem('loverollInviteAccepted')===existing.kind+':'+existing.value;
 }
 if(!client){render();return;}
 const {data,error}=await client.auth.getUser();
 if(error)console.warn(error.message);
 state.user=data?.user||null;
 if(state.user)try{await reload();}catch(e){fail(e);render();}
 else render();
 client.auth.onAuthStateChange((event,session)=>{
  if(event==='SIGNED_IN'&&session?.user&&!state.user){
   state.user=session.user;
   setTimeout(()=>reload().catch(fail),0);
  }
  if(event==='PASSWORD_RECOVERY'){
    state.passwordRecovery=true;state.showAuth=true;
    state.user=session?.user||state.user;render();
   }
   if(event==='SIGNED_OUT'){state.user=null;state.profile=null;state.coupleId=null;state.wellbeing=null;state.personalPass=null;state.personalJournal=[];state.emailVerificationPending=false;render();}
 });
}
// Version probe bypasses WebView/Chrome and service worker HTTP cache.
// Keep account/session data intact; update the HTML URL, never storage or Supabase.
let versionProbeRunning=false;
async function reloadWebAssets(force=false){
 if(versionProbeRunning||(!force&&document.hidden))return;
 if(!force&&state.diceStatus)return;
 versionProbeRunning=true;
 try{
  const response=await fetch('./web-version.json?fresh='+Date.now(),{
   cache:'no-store',headers:{'Cache-Control':'no-cache'}
  });
  if(!response.ok){if(force)notify('Impossible de vérifier la version : réessaie avec Internet.');return;}
  const data=await response.json();
  const newest=String(data?.build||'');
  if(!/^\d{4}-\d{2}-\d{2}\.\d+$/.test(newest))return;
  if(!force&&newest===WEB_RELEASE)return;
  if(!force&&sessionStorage.getItem('loveroll-auto-loaded-'+newest))return;
  // Do not interrupt a live throw, evening or unsaved form.
  if(!force&&(state.session||
      document.activeElement?.matches('input,textarea,select,[contenteditable="true"]'))){
   notify('Une mise à jour LoveRoll est disponible dans Réglages → Application.');
   return;
  }
  sessionStorage.setItem('loveroll-auto-loaded-'+newest,'1');
  const latest=new URL(location.href);
  latest.searchParams.set('web_build',newest);
  if(force)latest.searchParams.set('reload',String(Date.now()));
  location.replace(latest.toString());
 }catch(error){
  console.warn('Vérification des mises à jour LoveRoll impossible',error);
  if(force)notify('Vérification impossible. Réessaie lorsque la connexion est disponible.');
 }finally{versionProbeRunning=false;}
}
window.addEventListener('load',()=>{reloadWebAssets().catch(console.warn);});
window.addEventListener('appinstalled',()=>{
 dismissInstall(state.user?.id);
 state.installModalOpen=false;state.showInstallSteps=false;state.forceInstallGuide=false;
 if(state.user)render();
});
// When left open across midnight, server time (not phone time) changes the
// current Advent door without requiring a restart or manual refresh.
setInterval(async()=>{
 if(!state.user||state.tab!=='advent'||document.hidden||state.adventOpening!==null)return;
 try{
  const todayOnServer=await rpc('love_advent_server_date');
  if(todayOnServer&&todayOnServer!==state.adventServerDate){
   state.adventServerDate=todayOnServer;
   state.adventYear=null;
   await refreshShared();
   render();
  }
 }catch(error){console.warn('Horloge Avent indisponible',error);}
},60_000);
document.addEventListener('visibilitychange',()=>{
 if(!document.hidden&&state.user){
  reload().catch(fail);
  reloadWebAssets().catch(console.warn);
 }
});
// Android native Google chooser passes the selected ID token to Supabase.
window.addEventListener('loveroll-native-google-result',async event=>{
 const {token,nonce,error}=event.detail||{};
 if(error){
  state.authNotice=error;
  authMessage(error);return;
 }
 if(!token){authMessage('Aucun identifiant Google reçu. Réessayez ou connectez-vous avec votre mot de passe.');return;}
 try{
  const result=await client.auth.signInWithIdToken({provider:'google',token,nonce});
  if(result.error)throw result.error;
  if(result.data?.user){
   state.user=result.data.user;state.showAuth=false;
   await reload();
  }
  }catch(err){
  authMessage('Google n’a pas pu ouvrir la session LoveRoll. Vérifiez le compte choisi et réessayez, ou utilisez votre mot de passe.');
  console.warn('Google Supabase auth failed',err?.code||'unknown');
 }
});
start().catch(fail);

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  window.addEventListener('load', () =>
    navigator.serviceWorker.register('./service-worker.js?v=20261015-invite62',
     {scope:'./',updateViaCache:'none'}).then(reg=>reg.update()).catch(console.warn));
}
