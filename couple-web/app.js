import { marketingHtml,authHtml,profileHtml,wellbeingHtml,wellbeingFields,bindWellbeingMenus,matchHtml,parseMonthDay } from './onboarding.mjs';
import { eveningCard, EVENING_CATEGORIES, formatCountdown,customCardSlots,editableCardSlots,resolvedCard } from './evening-challenges.mjs';
import {MONTHLY_ACTIVITIES,editableMonthlyActivities,monthlyActivityForRoll} from './monthly-activities.mjs';
import {validateAdventJson,calendarFromSaved} from './advent-calendar.mjs';
import {validateSpecialEventsJson,SPECIAL_EVENT_KEYS,privateSpecialKey} from './private-special-events.mjs';
import {buildEveningLibrary,buildMonthlyLibrary,librarySummary,normalizeLibraryData,MAX_IMPORT_BYTES} from './json-libraries.mjs';
import {jsonToolsPanel,jsonInputId,exportJsonFile} from './json-library-ui.mjs';
import {guidedIdeas,guidanceFor,RELATIONSHIP_OPTIONS,INTIMACY_OPTIONS} from './wellbeing.mjs';

import { SPECIAL_SURPRISES,OCCASIONS,occasionDate,dueOccasions,specialSurprise,occasionRoles,TARGETED_OCCASIONS } from './special-occasions.mjs';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.57.0';
import { CONFIG } from './config.js';
import {normalizeInviteEmail,composeInviteEmail} from './invite-email.mjs';
import {enablePush,disablePush,updatePushPreference,sendTestPush,notificationSettingsMarkup} from './push-notifications.mjs';
import {shouldOfferInstall,dismissInstall,requestInstall,installOfferHtml,installStepsHtml} from './home-install.mjs';
import {invitationStatus,coupleStats,formatCoupleDate,invitationEmailText,invitationMessengerText,invitationPreviewMarkup} from './couple-dashboard.mjs';


const root=document.querySelector('#app'),toastEl=document.querySelector('#toast');
const enc=new TextEncoder(),dec=new TextDecoder();
let diceCleanup=[],renderToken=0;
const opts={auth:{flowType:'pkce',autoRefreshToken:true,persistSession:true,detectSessionInUrl:true}};
const client=CONFIG.supabaseUrl&&CONFIG.supabasePublishableKey?
 createClient(CONFIG.supabaseUrl,CONFIG.supabasePublishableKey,opts):null;
const launchParams=new URLSearchParams(location.search);
const requestedSettingsSection=launchParams.get('section')||'';
const state={user:null,profile:null,members:[],coupleId:null,couple:null,
 tab:launchParams.has('settings')?'settings':'home',weekly:null,monthly:{},session:null,personal:[],challenges:[],rollStatus:null,customCards:[],customCategory:'SENSUEL',customSlot:1,monthlyActivities:[],monthlySlot:1,
 events:[],journal:[],feedback:[],advent:[],advent2026Config:null,advent2026Days:[],selectedDay:1,unlockKey:null,channel:null,
 occasionSettings:null,occasionRolls:[],specialEventsConfig:null,specialEventKeys:[],wellbeing:null,guidance:'playful',personalJournal:[],personalPass:null,showAuth:launchParams.has('auth'),diceStatus:null,category:null,randomMode:false,chosenSession:null,confirmCancel:false,showInstallSteps:false,invitations:[],inviteDraft:null,challengesHistory:[],jsonPending:null,specialTimerKey:null,pushPrefs:null};
const names={SENSUEL:'Sensuel',COQUIN:'Coquin',SEXUEL:'Sexuel'};
function isSharedCoupleReady(coupleId,members){
 return !!coupleId&&Array.isArray(members)&&members.length===2;
}
let timerCtx=null;
const challengeTimer={id:null,durationMs:0,remainingMs:0,deadline:0,running:false,notified:false,mode:'sound'};
try{const saved=localStorage.getItem('loveroll-timer-mode');if(saved==='vibrate'||saved==='sound')challengeTimer.mode=saved;}catch(e){}

const kinds={weekly:'Dé hebdomadaire',monthly_activity:'Sortie du mois',
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
 const inApp=/FBAN|FBAV|Messenger|Instagram/i.test(navigator.userAgent||'');
 if(!inApp||!sessionStorage.getItem('pendingInvite'))return '';
 return '<section class="card"><h3>❤️ Invitation reçue sur Messenger</h3>'+
  '<p>Sur iPhone, la connexion Google peut demander de quitter le navigateur de Messenger. Pour ne pas perdre votre invitation, copiez son lien puis ouvrez-le dans Safari.</p>'+
  button('copy-invite-for-browser','Copier mon lien pour Safari','class="subtle"')+'</section>';
}
function notify(message){toastEl.textContent=message;toastEl.classList.add('visible');
 clearTimeout(notify.timeout);notify.timeout=setTimeout(()=>toastEl.classList.remove('visible'),4500);}
function fail(error){console.error(error);notify(error?.message||'Une erreur est survenue');}
async function rpc(name,args={}){const {data,error}=await client.rpc(name,args);if(error)throw error;return data;}
async function query(table,options={}){
 let q=client.from(table).select(options.columns||'*');
 for(const [column,value] of Object.entries(options.eq||{}))q=q.eq(column,value);
 if(options.order)q=q.order(options.order,{ascending:options.asc??false});
 const {data,error}=await q;if(error)throw error;return data;
}
function frame(body,title='LOVEROLL',subtitle='Un jeu intime, juste pour vous deux'){
 const connected=!!(state.user&&state.profile?.birthday_md&&state.wellbeing);
 for(const dispose of diceCleanup)dispose();diceCleanup=[];
 const token=++renderToken;
 const installation=connected&&(state.forceInstallGuide||shouldOfferInstall(state.user?.id))?
  (state.showInstallSteps?installStepsHtml():installOfferHtml()):'';
 root.innerHTML='<header class="header"><div><h1>♡ '+safe(title)+'</h1><p>'+safe(subtitle)+'</p></div>'+
  (state.user?'<button class="subtle" data-action="signout" aria-label="Se déconnecter">↪</button>':'')+
  '</header>'+body+(connected?nav():'')+installation;
 bindActions();
 const canvases=[...root.querySelectorAll('canvas[data-dice-sides]')];
 if(canvases.length){
  import('./dice3d.js').then(({dieOn})=>{
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
 [['home','⌂','Accueil'],['session','🎲','Soirée'],['special','✦','Défi spécial'],
  ['advent','🎁','Avent'],['settings','⚙','Réglages']]:
 [['home','⌂','Accueil'],...(isLoveAdmin()?[['library','♡','Mes cartes']]:[]),
  ['personal-journal','♥','Calendrier'],['settings','⚙','Réglages']];
 return '<nav class="nav"><div class="nav-inner" style="grid-template-columns:repeat('+tabs.length+',minmax(0,1fr))">'+tabs.map(([key,icon,label])=>
 '<button data-action="tab:'+key+'" class="'+((state.tab===key||(key==='special'&&state.tab==='occasions'))?'active':'')+'"><span>'+icon+
 '</span>'+safe(label)+'</button>').join('')+'</div></nav>';
}
function bindActions(){
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
 bindWellbeingMenus();
}
function render(){
 if(!client){frame('<section class="card"><h2>Connexion cloud à configurer</h2>'+
  '<p>Un projet Supabase dédié et son adresse HTTPS sont requis avant d’activer les comptes et le partage.</p></section>');return;}
 if(!state.user){
  if(!state.showAuth){
   root.innerHTML=marketingHtml();bindActions();
  }else frame(inviteBrowserHelp()+authHtml(),'♡ LOVE ROLL','Créer ou retrouver votre espace amoureux');
  return;
 }
 if(!state.profile||!state.profile.birthday_md){
  frame(profileHtml(state.user,state.profile),'♡ MON PROFIL','Première connexion : inscription');
  return;
 }
 if(!state.wellbeing){
  frame(wellbeingHtml(),'♡ MON RESSENTI','Vos réponses sont personnelles');return;
 }
 // No demonstration games without two linked accounts. The creator still
 // owns the private card editor and personal journal before any invitation.
 if(!isSharedCoupleReady(state.coupleId,state.members)){
  if(!state.coupleId&&sessionStorage.getItem('pendingInvite')){
   frame(inviteBrowserHelp()+matchHtml(state.profile.display_name,true),
    '♡ NOTRE MATCH','Accepter votre invitation');return;
  }
  if(state.tab==='settings')renderSettings();
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
 const [profiles,assessments,personal,pushSettings]=await Promise.all([
  query('couple_profiles',{eq:{id:state.user.id}}),
  query('love_wellbeing',{eq:{user_id:state.user.id}}),
  query('love_personal_intimacy',{eq:{user_id:state.user.id}}),
  query('love_push_preferences',{eq:{user_id:state.user.id}})
 ]);
 state.pushPrefs=pushSettings[0]||{enabled:false,weekly:true,invitation:true,monthly:true,special:true};
 state.profile=profiles[0]||null;
 state.wellbeing=assessments[0]||null;
 state.personalJournal=personal;
 const membership=await query('couple_members',{eq:{user_id:state.user.id}});
 const previousCouple=state.coupleId;
 state.coupleId=membership[0]?.couple_id||null;
 if(!state.coupleId){
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  state.couple=null;state.members=[];state.weekly=null;state.monthly={};state.customCards=[];state.monthlyActivities=[];state.rollStatus=null;
  state.session=null;state.sessions=[];state.events=[];state.journal=[];
  state.feedback=[];state.advent=[];state.advent2026Config=null;state.advent2026Days=[];state.specialEventsConfig=null;state.specialEventKeys=[];state.unlockKey=null;
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
 const [rolls,sessions,advents,events,diary,occasionSettings,occasionRolls,keys,challengeHistory]=await Promise.all([
 query('couple_rolls',{eq:{couple_id:c}}),
 query('couple_sessions',{eq:{couple_id:c},order:'started_at'}),
 query('couple_advent',{eq:{couple_id:c}}),
 query('couple_events',{eq:{couple_id:c},order:'happened_at'}),
 query('couple_intimacy',{eq:{couple_id:c}}),
 query('couple_occasion_settings',{eq:{couple_id:c}}),
 query('couple_occasion_rolls',{eq:{couple_id:c}}),
 rpc('my_love_special_event_keys',{p_couple:c}),
 query('couple_challenges',{eq:{couple_id:c}})
 ]);
 state.challengesHistory=challengeHistory;
 state.occasionSettings=occasionSettings[0]||null;
 state.occasionRolls=occasionRolls;
 state.specialEventKeys=Array.isArray(keys)?keys:[];
 state.weekly=rolls.find(r=>r.kind==='weekly'&&r.key===week());
 state.monthly={};
 for(const r of rolls.filter(r=>r.key===month()))state.monthly[r.kind]=r;
 state.session=sessions.find(s=>s.finished_at===null)||null;
 state.sessions=sessions;state.events=events;state.journal=diary;state.advent=advents;
 if(state.session){
  const [personal,challenges,status]=await Promise.all([
    query('couple_personal_rolls',{eq:{session_id:state.session.id}}),
    query('couple_challenges',{eq:{session_id:state.session.id},order:'created_at'}),
    rpc('my_love_roll_status',{p_session:state.session.id})
  ]);
  state.personal=personal;state.challenges=challenges;state.rollStatus=status?.[0]||null;
 }else{state.personal=[];state.challenges=[];state.rollStatus=null;}
 state.feedback=await query('couple_feedback',{eq:{couple_id:c}});
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
 for(const table of ['couple_occasion_rolls','couple_occasion_settings','couple_rolls','couple_sessions','couple_challenges',
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
 return '<div class="dice-row"><div class="die"><strong class="dice-fallback">'+safe(face||'?')+'</strong><canvas class="dice-canvas" width="190" height="190"'+
  ' data-dice-sides="'+sides+'" data-face="'+(face||1)+'" data-spinning="'+(!!state.diceStatus)+'"'+
  ' aria-label="Dé '+sides+' en 3D. Résultat '+(face||'pas encore tiré')+'"></canvas></div>'+
 '<div><div class="eyebrow">D'+sides+' · '+safe(label)+'</div>'+
 '<p>'+(!face?'En attente du tirage officiel.':'Face gagnante : '+face)+
 '. Faites glisser le dé pour le tourner.</p></div></div>';
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
 const latest=state.invitations?.find(x=>!x.cancelled_at)||state.invitations?.[0];
 const ready=state.members?.length===2;
 const status=invitationStatus(latest,ready);
 const draft=state.inviteDraft&&state.inviteDraft.id===latest?.id?state.inviteDraft:null;
 let html='<section class="card"><div class="eyebrow">VOTRE INVITATION</div><h3>♡ Statut de la demande</h3>'+
  '<div class="invite-status"><strong>'+safe(status.title)+'</strong><p>'+safe(status.hint)+'</p></div>';
 if(latest)html+='<p class="tiny-note">Destinataire : '+safe(latest.invited_email)+
  ' · Préparée le '+safe(new Date(latest.created_at).toLocaleDateString('fr-CA'))+'</p>';
 if(!ready){
  html+='<label>Courriel Google de votre partenaire<input id="invite-email" type="email" autocomplete="email" placeholder="partenaire@exemple.com" value="'+safe(draft?.email||latest?.invited_email||'')+'"></label>'+
   '<p class="tiny-note">Cette adresse sert à vérifier que la bonne personne accepte, même si vous partagez le lien par texto.</p>'+
   button('invite',latest?'↻ Renouveler mon lien sécurisé':'Créer mon invitation');
  html+='<div class="invite-actions">'+
    button('send-messenger','♡ Messenger')+
    button('send-sms','💬 Texto SMS','class="subtle"')+
    button('send-email','✉ Courriel','class="subtle"')+
    (draft?button('copy-invite','Copier le lien','class="subtle"'):'')+'</div>';
  if(draft)html+='<details class="invite-preview"><summary>♡ Aperçu de votre invitation</summary>'+
    invitationPreviewMarkup()+'</details>'+
    button('confirm-invite-sent','✓ J’ai envoyé l’invitation','class="subtle"')+
    '<p class="tiny-note">Choisissez Messenger, SMS ou courriel dans votre application, puis confirmez l’envoi ici.</p>';
  else html+='<p class="tiny-note">Choisir un moyen de partage prépare un nouveau lien personnel. Cela invalide l’ancien lien d’invitation. Aucun message n’est envoyé sans votre confirmation.</p>';
  if(latest&&!latest.cancelled_at&&!latest.accepted_at&&new Date(latest.expires_at)>new Date())
   html+=button('cancel-invite:'+latest.id,'Annuler cette invitation','class="subtle danger-action"');
 }
 return html+'</section>';
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
  button('tab:settings','♡ Inviter mon partenaire')+'</div>'+
  '<section class="card"><div class="eyebrow">LES 3 ÉTAPES</div>'+
  '<div class="waiting-steps"><p><strong>✓ 1.</strong> Votre espace personnel est créé</p>'+
  '<p><strong>2.</strong> Partager l’invitation par Messenger, SMS ou courriel</p>'+
  '<p><strong>3.</strong> Dès qu’elle est acceptée, vos dés et soirées seront activés</p></div></section>'+
  '<section class="card"><h3>♡ À préparer pendant l’attente</h3>'+
  '<div class="waiting-actions">'+
  button('tab:library','♡ Mes 70 cartes')+
  button('tab:monthly-library','✦ 20 activités du mois','class="subtle"')+
  button('tab:special-editor','💘 Événements spéciaux','class="subtle"')+
  button('tab:advent-editor','🎁 Calendrier de l’Avent','class="subtle"')+'</div>'+
  '<p class="tiny-note">Importez ou exportez chaque bibliothèque en JSON pour renouveler vos idées.</p></section>'+
  renderInvitePanel(),'MON ESPACE','Votre aventure à deux commence ici');
}
function renderHome(){
 const name=state.profile.display_name,partner=other()?.name;
 frame('<div class="eyebrow">Votre couple</div>'+
 '<section class="card"><h2>'+safe(name)+' ♡ '+safe(partner)+'</h2>'+
 '<p>Votre rituel de la semaine · un seul dé pour votre couple.</p>'+
 heroDice(state.weekly?.face,6,'Dé hebdomadaire')+
 (state.weekly?'<div class="result">'+state.weekly.face+' soirée(s) cette semaine</div>'+
 '<p>Ce résultat a été brassé par '+safe(person(state.weekly.actor))+'. Prochain tirage lundi.</p>':
 button('roll:weekly','🎲 Brasser notre D6 hebdomadaire'))+'</section>'+
 ideaPreview()+
 '<section class="card"><h3>♥ Notre soirée</h3>'+statsRow()+
 button('tab:session','Ouvrir notre soirée')+'</section>'+
 '<section class="card"><h3>✦ Activité du mois</h3><p>'+
 (state.monthly.monthly_activity?safe(monthlyActivityForRoll(state.monthly.monthly_activity).title):'Surprise à découvrir')+
 '</p>'+button('tab:special','Voir le défi spécial')+'</section>'+
 '<section class="card"><h3>Vos notifications</h3>'+
 (state.events.length?state.events.slice(0,4).map(e=>'<p>'+safe(person(e.actor_id))+' · '+safe(e.title)+' · '+safe(e.happened_at.slice(0,10))+'</p>').join(''):
 '<p>Les tirages et les réponses de votre partenaire apparaîtront ici pendant que vous utilisez l’application.</p>')+
 '</section>');
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
function renderSession(){
 let s=state.session; const p=other();
 let body='<section class="card"><h2>Une soirée, deux D6 individuels</h2>'+
 '<p>Chacun lance son D6 sur son compte. Les deux résultats apparaissent ensemble dès que les deux ont joué.</p>';
 if(!s){
  const latest=(state.sessions||[]).find(x=>x.finished_at&&x.counted);
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
  body+=heroDice(mine?.face,6,'Mon dé personnel')+
  '<div class="two"><div class="stat"><strong>'+safe(mine?.face||'—')+'</strong><small>'+safe(state.profile.display_name)+'</small></div>'+
  '<div class="stat"><strong>'+safe(theirs?.face||'—')+'</strong><small>'+safe(p.name)+'</small></div></div>';
  if(!state.rollStatus?.both_ready){
   body+='<section class="card"><h3>Deux D6 · révélation commune</h3>'+
    '<p>Mon D6 : '+(state.rollStatus?.my_rolled?'✓ Lancé · résultat secret':'Pas encore lancé')+'</p>'+
    '<p>D6 de '+safe(p.name)+' : '+(state.rollStatus?.partner_rolled?'✓ Lancé · résultat secret':'En attente')+'</p>';
   if(!state.rollStatus?.my_rolled)body+=button('my-d6','🎲 Brasser MON D6');
   else body+='<p>Tu as joué. Le compte à rebours ne démarre pas : attends le D6 de '+safe(p.name)+'.</p>';
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
     ' · D35 : '+safe(active.face)+'</div>'+
     '<div class="challenge-roles">♡ '+safe(p.name).toUpperCase()+' OFFRE → '+safe(state.profile.display_name).toUpperCase()+' REÇOIT</div>'+
     '<h3>'+safe(challenge.title)+'</h3>'+
     '<p>'+safe(challenge.description)+'</p>' +
     timerWidget(active,challenge.minutes)+
     '<div class="actions">'+
     (active.status==='proposed'?button('answer:'+active.id+':accepted','Accepter'):'')+
     (active.status==='accepted'?button('answer:'+active.id+':completed','Terminé'):'')+
     button('answer:'+active.id+':passed','Passer')+'</div></div>';
   }else if(completed<mine.face)body+='<p>Votre partenaire brasse le D35 pour vous proposer le prochain défi.</p>';
   const otherActive=state.challenges.filter(x=>x.recipient===p.user_id&&['proposed','accepted'].includes(x.status)).at(-1);
   const otherCompleted=state.challenges.filter(x=>x.recipient===p.user_id&&x.status==='completed').length;
   if(!otherActive&&otherCompleted<theirs.face){
    body+='<div class="card"><h3>Défi '+(otherCompleted+1)+' / '+theirs.face+
     ' à offrir à '+safe(p.name)+'</h3>'+
     '<p>Choisissez la catégorie pour ce défi. Vous pourrez changer pour chaque nouveau tirage.</p>'+
     '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
     '<button class="pill '+(state.category===c?'selected':'')+'" data-action="category:'+c+'">'+names[c]+'</button>').join('')+'</div>'+
     '<p>88 % dans votre catégorie choisie · 12 % dans l’autre. Choisissez à chaque défi.</p>'+
     (EVENING_CATEGORIES.includes(state.category)?
      button('new-challenge','🎲 Brasser le D35 pour '+safe(p.name)):
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
function optionalRolledTimer(roll){
 if(!roll?.timer_minutes)return '';
 const key='special-'+roll.id;
 return state.specialTimerKey===key?timerWidget({id:key},roll.timer_minutes):
  button('special-timer:'+key,'⏱ Minuteur · '+roll.timer_minutes+' min','class="subtle"');
}
function renderSpecial(){
 const a=state.monthly.monthly_activity,h=state.monthly.monthly_host,r=state.monthly.monthly_restaurant;
 let body='<div class="eyebrow">Défi spécial · '+safe(month())+'</div>'+
 '<section class="card"><h2>Votre rendez-vous du mois</h2>';
 if(!a)body+=heroDice(null,20,'Une des 20 idées de sortie')+
  button('roll:monthly_activity','🎲 Brasser le D20 mensuel');
 else{
  const activity=monthlyActivityForRoll(a);
  body+=heroDice(a.face,20,'Activité officielle')+
  '<div class="result">'+safe(activity.title)+'</div>'+
  '<p>'+safe(activity.description)+'</p>'+
  '<p>Tiré par '+safe(person(a.actor))+'.</p>'+
  (activity.timer_minutes?timerWidget({id:'monthly-'+a.id},activity.timer_minutes):'');
  if(!h)body+=button('roll:monthly_host','🎲 Brasser le D6 : qui invite ?');
  else{
   const host=h.face<=3?state.members[0]:state.members[1]; // ordered by join time, identically on both devices
   body+='<p class="tag">♡ '+safe(host.name)+' invite '+safe(state.members.find(m=>m.user_id!==host.user_id).name)+
    ' · D6 face '+h.face+'</p>';
   if(activity.is_restaurant){
    if(!r)body+=button('roll:monthly_restaurant','🎲 Resto connu ou nouveau ?');
    else body+='<p>🎯 '+(r.face<=3?'Retourner dans un restaurant connu':'Découvrir une nouvelle adresse')+
      ' · D6 face '+r.face+'</p>';
   }else body+='<p>Choisissez un lieu adapté à votre budget et à votre gardienne.</p>';
  }
 }
 body+='</section><section class="card"><h3>💘 Fêtes et anniversaires</h3>'+
 '<p>Saint-Valentin, fête des Mères et des Pères, anniversaires personnels ou de couple : des surprises à deux.</p>'+
 button('tab:occasions','🎲 Découvrir les événements spéciaux')+'</section>'+
 '<section class="card"><p>Gardienne à prévoir, environ 2 à 6 heures. Réservations et frais restent à organiser entre vous.</p></section>';
 frame(body,'DÉFI SPÉCIAL','L’un ou l’autre brasse, jamais deux résultats différents');
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
 let body='<section class="card"><h2>Nos événements spéciaux</h2>'+
  '<p>Une surprise de fête au D20, et un D6 de variation quand elle a été personnalisée.</p>'+
  '<p>Chaque idée est facultative : vous pouvez en discuter, la modifier ou la refuser.</p></section>';
 for(const ev of all.sort((a,b)=>(a.daysLeft===null?1:0)-(b.daysLeft===null?1:0))){
  const k=ev.key+(ev.actorId?':'+ev.actorId:'');
  const presetKey=privateSpecialKey(ev.key,ev.actorId,state.couple?.owner_user_id);
  const prize=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='surprise');
  const custom=ev.year>=2027&&!!presetKey&&state.specialEventKeys.includes(presetKey)&&!prize;
  const activity=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='activity');
  const host=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='host');
  const modifier=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='modifier');
  const displayName=ev.actorId?person(ev.actorId):'';
  const role=occasionRoles(ev,settings,state.members);
  body+='<section class="card"><div class="eyebrow">'+safe(ev.emoji)+' '+safe(ev.title)+
    (displayName?' · '+safe(displayName):'')+'</div>';
  body+='<p>'+safe(ev.date||'Date non définie')+
    (ev.daysLeft!==null?' · '+(ev.daysLeft===0?"Aujourd’hui":ev.daysLeft>0?"Dans "+ev.daysLeft+" jours":"Il y a "+(-ev.daysLeft)+" jours"):'')+'</p>';
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
     body+=heroDice(null,6,'D6 · Activité spéciale')+
       button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':activity','🎲 Brasser le D6 de l’activité');
   }else{
    body+=heroDice(activity.face,6,'D6 · Activité spéciale')+
      '<div class="card"><div class="eyebrow">ACTIVITÉ · FACE '+activity.face+'</div>'+
      '<p>'+safe(activity.special_description||'')+'</p>'+
      '<p class="tiny-note">Une proposition à accepter, modifier ou passer ensemble.</p>'+optionalRolledTimer(activity)+'</div>';
    if(!modifier){
     body+=heroDice(null,6,'D6 · Règle supplémentaire')+
       button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':modifier','🎲 Brasser le D6 du modificateur');
    }else{
     body+=heroDice(modifier.face,6,'D6 · Règle supplémentaire')+
       '<div class="card"><div class="eyebrow">RÈGLE · FACE '+modifier.face+'</div>'+
       '<p>'+safe(modifier.modifier_text||'')+'</p>'+optionalRolledTimer(modifier)+'</div>';
    }
   }
  }
  else if(!prize){if(role.shared||role.receiver)body+=heroDice(null,20,'Dé de fête')+
    button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':surprise','🎲 Brasser le D20 de la surprise');}
  else{
   const idea=prize.special_title?
    {title:prize.special_title,detail:prize.special_description,level:'Votre surprise du jour'}:
    specialSurprise(prize.face);
   body+=heroDice(prize.face,20,'Résultat officiel')+
     '<h3>'+safe(idea.title)+'</h3><p>'+safe(idea.detail)+'</p>'+
     '<p class="tag">'+safe(idea.level)+' · Tiré par '+safe(person(prize.actor))+'</p>';
   if(prize.special_title){
    if(!modifier){
     body+='<p>Une deuxième surprise reste à révéler : brassez le D6 pour découvrir la règle du moment.</p>'+
      button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':modifier','🎲 Brasser le D6 du modificateur');
    }else{
     body+=heroDice(modifier.face,6,'Règle supplémentaire')+
      '<div class="card"><div class="eyebrow">D6 · FACE '+modifier.face+'</div>'+
      '<h3>'+safe(modifier.modifier_title||'La règle du moment')+'</h3>'+
      '<p>'+safe(modifier.modifier_text||'')+'</p>'+optionalRolledTimer(modifier)+'</div>';
    }
   }
   if(!host&&role.shared)body+='<p class="tag">Surprise commune · vous l’organisez à deux.</p>';
   else if(!host&&role.receiver)body+='<p class="tag">L’offrant prépare la surprise pour la personne célébrée.</p>';
   else if(host){
    const giver=state.members[host.face<=3?0:1];
    body+='<div class="result">♡ '+safe(giver?.name||'Partenaire')+' organise</div>'+
     '<p>D6 face '+host.face+' · Ensemble, vous fixez vos limites, le budget et la gardienne.</p>';
   }
  }
  body+='</section>';
 }
 body+='<section class="card">'+button('tab:settings','⚙ Définir nos dates')+'</section>';
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
  '<p>Collez ici votre fichier JSON complet pour enregistrer les 6 fêtes, leurs 6 activités D6 et les 6 règles D6 pour chaque fête. Les descriptions seront conservées comme vous les avez écrites.</p>'+
  '<p class="tiny-note">Votre partenaire ne voit pas cette bibliothèque. Il découvre une activité au premier D6, puis une règle supplémentaire au deuxième D6.</p>'+
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
 frame(body,'MES FÊTES','6 fêtes · deux D6 chacune');
}
function renderAdvent(){
 const now=today(),[year,mo,day]=now.split('-').map(Number);
 const unlocked=mo===12?Math.min(day,25):0;
 let body='<section class="card"><h2>25 surprises secrètes</h2>'+
  '<p>Une seule case officielle par jour de décembre, ouverte par l’un ou l’autre.</p>'+
  '<div class="day-grid">';
 for(let d=1;d<=25;d++){
  const allowed=d<=unlocked,opened=state.advent.find(x=>x.year===year&&x.day===d);
  body+='<button class="'+(!allowed?'locked':'')+'" data-action="advent:'+d+'" '+
    (!allowed?'disabled':'')+'>'+(opened?'♡ ':!allowed?'🔒 ':'🎁 ')+d+'</button>';
 }
 body+='</div>';
 const selected=state.advent.find(x=>x.year===year&&x.day===state.selectedDay);
 if(selected){
  const customized=!!selected.advent_title;
  body+='<div class="card"><div class="eyebrow">CASE '+selected.day+
   (selected.advent_type?' · '+safe(selected.advent_type):'')+'</div>'+
   '<h3>'+safe(selected.advent_title||advent[selected.day-1])+'</h3>'+
   (selected.advent_description?'<p>'+safe(selected.advent_description)+'</p>':'')+
   '<p>Ouverte par '+safe(person(selected.opened_by))+'. '+
   (customized?'Ce défi est une proposition à accepter, adapter ou passer librement.':
    'À proposer par '+safe(person(selected.performed_by))+
    ' (vous pouvez toujours modifier ou passer l’activité ensemble).')+'</p>'+
    (selected.advent_timer_minutes?timerWidget({id:'advent-'+selected.year+'-'+selected.day},selected.advent_timer_minutes):'')+'</div>';
 }
 body+='</section>';
 frame(body,'CALENDRIER DE L’AVENT','Votre rituel commun en décembre');
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
async function encryptJson(object){
 if(!state.unlockKey)throw Error('Déverrouillez d’abord votre journal.');
 const iv=crypto.getRandomValues(new Uint8Array(12));
 const encrypted=await crypto.subtle.encrypt({name:'AES-GCM',iv},state.unlockKey,
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
async function renderPersonalJournal(){
 const items=state.personalJournal||[],mo=state.personalCalendarMonth||month(),day=state.personalSelectedDate||today();
 const [y,m]=mo.split('-').map(Number),length=new Date(y,m,0).getDate();
 const offset=(new Date(y,m-1,1).getDay()+6)%7;
 let calendar='<div class="calendar">'+Array.from({length:offset},()=>'<span></span>').join('');
 for(let n=1;n<=length;n++){
  const d=mo+'-'+String(n).padStart(2,'0'),count=items.filter(x=>x.happened_on===d).length;
  calendar+='<button class="'+(count?'marked':'')+'" data-action="personal-date:'+d+'">'+
   (count?'♥ ':'')+n+'</button>';
 }
 calendar+='</div>';
 let body='<section class="card"><h2>♥ Mon calendrier privé</h2>'+
 '<p>'+items.length+' moment(s) enregistré(s) dans votre historique personnel. Le changement de partenaire n’efface aucune entrée.</p>'+
 '<div class="actions">'+button('personal-month-prev','←','class="subtle"')+
 '<button class="subtle" disabled>'+safe(mo)+'</button>'+
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
   body+='<div class="muted-line"><strong>'+safe(item.happened_on)+'</strong>'+
    (clear?'<p>Orgasme : '+safe(clear.orgasm)+' · Point fort : '+safe(clear.good)+
    ' · À améliorer : '+safe(clear.weak)+'</p>'+
    (clear.note?'<p>'+safe(clear.note)+'</p>':''):'<p>Impossible de déchiffrer cette entrée avec cette phrase secrète.</p>')+'</div>';
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
 let cal='<div class="calendar">'+Array.from({length:offset},()=>'<span></span>').join('');
 for(let day=1;day<=length;day++){
  const date=mo+'-'+String(day).padStart(2,'0');
  const count=dates.filter(d=>d===date).length;
  cal+='<button class="'+(count?'marked':'')+'" data-action="date:'+date+'">'+
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
 '<button disabled class="subtle">'+safe(mo)+'</button>'+
 button('month-next','→','class="subtle"')+'</div>'+cal+'</section>';
 if(!state.unlockKey)body+='<section class="card"><h2>🔒 Journal chiffré</h2>'+
  '<p>Entrez votre phrase secrète commune pour lire ou remplir vos entrées et vos avis. Elle n’est pas envoyée au serveur.</p>'+
  '<input id="journal-pass" type="password" minlength="12" autocomplete="off" placeholder="Phrase secrète à partager hors de l’application">'+
  button('unlock','Déverrouiller')+
  '<p>Sans cette phrase, les anciens messages chiffrés ne pourront pas être récupérés.</p></section>';
 else{
  body+='<section class="card"><h3>♥ Enregistrer un moment</h3>'+
   '<label>Date<input id="event-date" type="date" value="'+safe(state.selectedDate||now)+'"></label>'+
   '<label>Qui a eu un orgasme ?<select id="event-orgasm">'+
   ['Non précisé','Les deux','Moi','Mon partenaire','Aucun'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label><label>Point fort<select id="event-good">'+
   ['Complicité','Tendresse','Communication','Plaisir','Nouveauté'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label><label>À améliorer<select id="event-weak">'+
   ['Rien','Plus de temps','Plus de douceur','Plus de communication'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label><label>Commentaire privé<textarea id="event-note" maxlength="1200" placeholder="Facultatif"></textarea></label>'+
   button('save-event','Enregistrer dans notre calendrier')+'</section>';
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
    (item.author_id===state.user.id?button('delete-event:'+item.id,'Supprimer','class="subtle"'):'')+'</div>';
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
  sessions.find(x=>x.finished_at&&x.counted);
 if(!session){
  frame('<section class="card"><p>Terminez d’abord une soirée pour répondre à vos questions.</p>'+
   button('tab:session','Retour à notre soirée')+'</section>','NOS AVIS','Après une soirée terminée');return;
 }
 state.chosenSession=session.id;
 const entries=state.feedback.filter(f=>f.session_id===session.id);
 const yours=entries.find(x=>x.author_id===state.user.id);
 const complete=state.members.every(m=>entries.some(x=>x.author_id===m.user_id));
 let body='<section class="card"><div class="eyebrow">♥ APRÈS LA SOIRÉE</div>'+
 '<h2>Soirée du '+safe(session.started_at.slice(0,10))+'</h2>'+
 '<p>Trois questions pour chacun. Votre partenaire ne pourra lire vos réponses qu’une fois que vous aurez répondu tous les deux.</p>'+
 '<div class="stat-grid"><div class="stat"><strong>'+entries.length+'/2</strong><small>Avis déposés</small></div></div>';
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
   '" aria-label="Modifier carte '+card.cardId+' : '+safe(title)+'">'+
   '<span class="custom-tile-number">'+String(card.cardId).padStart(2,'0')+'</span>'+
   '<span class="custom-tile-name">'+safe(title)+'</span></button>';
 }).join('');
 let body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>Mes 70 cartes</h2>'+
  '<p>25 cartes de base + 10 emplacements libres par catégorie. Chaque carte est modifiable, y compris les 50 déjà fournies.</p>'+
  '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
    '<button class="pill '+(category===c?'selected':'')+
    '" data-action="custom-category:'+c+'">'+safe(names[c])+'</button>').join('')+'</div>'+
  '</section>'+jsonToolsPanel('evening',names[category],state.jsonPending)+
  '<section class="card"><div class="eyebrow">RÈGLE DE PIGE</div>'+
  '<h3>Éviter les répétitions</h3>'+
  '<label class="custom-draw-setting"><input type="checkbox" id="no-repeat-cards"'+
    (state.couple?.no_repeat_challenges!==false?' checked':'')+'>'+
    '<span>Ne pas ressortir une carte tant que toutes les cartes admissibles n’ont pas été pigées</span></label>'+
  '<p class="tiny-note">Activé par défaut. Le cycle est conservé entre les soirées, séparément pour chaque personne qui reçoit, en tenant compte de qui est autorisé à piger et recevoir. Après épuisement, un nouveau cycle commence automatiquement.</p>'+
  button('save-draw-rule','Enregistrer cette règle','class="subtle"')+
  '</section>'+
  '<section class="card"><div class="eyebrow">CHOISIR UNE CARTE À MODIFIER</div>'+
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
  '<p class="tiny-note">Celui qui pige OFFRE le défi à l’autre, qui le REÇOIT. Ces restrictions sont vérifiées dans la base de données avant chaque tirage.</p>'+
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
  '<p>Changez les 20 résultats du D20, même avant d’inviter votre partenaire. Vous pouvez remplacer complètement une idée de sortie et sa description.</p>'+
  '<div class="custom-card-tiles">'+tiles+'</div>'+
  '<p class="tiny-note">Les changements seront utilisés aux prochains tirages. Le résultat déjà obtenu ce mois-ci reste inchangé pour les deux partenaires.</p>'+
  '</section>'+jsonToolsPanel('monthly','20 activités du mois',state.jsonPending)+
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
function settingGroup(title,icon,description,body,opened=false){
 return '<details class="settings-group"'+(opened?' open':'')+'><summary>'+
  '<span class="settings-group-icon">'+icon+'</span>'+
  '<span class="settings-group-heading"><strong>'+safe(title)+'</strong><small>'+safe(description)+'</small></span>'+
  '<span aria-hidden="true" class="settings-chevron">⌄</span></summary>'+
  '<div class="settings-group-content">'+body+'</div></details>';
}
function settingRow(title,detail,action,label='Ouvrir'){
 return '<div class="settings-item"><div><strong>'+safe(title)+'</strong><p>'+safe(detail)+'</p></div>'+
   button(action,label,'class="subtle settings-mini-button"')+'</div>';
}
function renderCoupleDateSetting(){
 const started=state.occasionSettings?.relationship_started_on||'';
 return '<div class="settings-item"><div><strong>Notre date de couple</strong>'+
  '<p>La date à laquelle votre histoire a commencé.</p></div></div>'+
 (isLoveAdmin()?'<label>Date de début<input id="couple-started-on" type="date" max="'+safe(today())+
  '" value="'+safe(started)+'"></label>'+
  button('save-couple-date','Enregistrer notre date','class="subtle"'):
  '<p>'+safe(formatCoupleDate(started))+'</p>')+
 '<p class="tiny-note">Anniversaire du couple : '+safe(state.occasionSettings?.anniversary_md||'Non renseigné')+'</p>';
}
function renderSettings(){
 const linked=!!state.coupleId,hasPartner=linked&&state.members.length===2;
 const account='<div class="settings-item"><div><strong>Compte connecté</strong><p>'+
  safe(state.user.email||'Courriel privé')+'</p></div></div>'+
  '<label>Prénom affiché<input id="profile-name" value="'+safe(state.profile.display_name)+'" maxlength="80"></label>'+
  button('profile','Enregistrer mon prénom','class="subtle"')+
  '<p class="tiny-note">Anniversaire du profil : '+safe(state.profile.birthday_md)+
  ' · Connexion Google ou courriel liée à ce compte.</p>'+
  settingRow('Se déconnecter','Déconnecte uniquement cet appareil.','signout','Déconnexion');
 let partner='';
 if(hasPartner){
  partner='<div class="settings-item"><div><strong>♥ Couple connecté</strong>'+
  '<p>'+safe(state.profile.display_name)+' et '+safe(other()?.name||'Votre partenaire')+
  ' · Invitation acceptée</p></div></div>'+
  renderCoupleDateSetting()+
  '<p class="tiny-note">Un retrait coupe l’accès au journal partagé. Les entrées privées sont conservées.</p>'+
  button('end-couple','Retirer mon partenaire','class="subtle danger-action"');
 }else if(linked){
  partner=renderInvitePanel()+
   '<p class="tiny-note">Vous pouvez préparer vos défis avant que la demande soit acceptée.</p>'+
   button('end-couple','Annuler mon espace à deux','class="subtle danger-action"');
 }else partner='<p>Créez votre espace privé avant d’inviter votre partenaire.</p>'+
  button('prepare-cards','Créer mes défis avant l’invitation')+
  button('start-couple','Préparer mon couple','class="subtle"');
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
  settingRow('Cartes et défis','Personnaliser les 70 cartes et les règles de tirage.','tab:library','Mes cartes')+
  settingRow('Activités du mois','Modifier les 20 activités du D20.','tab:monthly-library','Modifier')+
  settingRow('Calendrier de l’Avent','Configurer les 25 journées privées.','tab:advent-editor','Modifier')+
  settingRow('Événements spéciaux','Gérer les fêtes et leurs deux D6.','tab:special-editor','Modifier')+
  celebrants:
  '<p>Les réglages des cartes sont réservés au créateur du couple.</p>';
 const notifications=notificationSettingsMarkup(state.pushPrefs||{});
 const nativeApp=typeof window!=='undefined'&&!!window.LoveRollNativeAuth;
 const appSettings=(nativeApp?
  '<div class="settings-item"><div><strong>LoveRoll est installé</strong><p>L’application Android utilise les mêmes écrans que les versions iPhone et iPad.</p></div></div>'+
  settingRow('Vérifier les mises à jour APK','Rechercher une version Android signée plus récente.','native-update','Vérifier'):
  settingRow('Ajouter LoveRoll à l’écran d’accueil',
    'Installation recommandée sur Safari iPhone et iPad.','show-install-guide','Afficher'))+
  '<div class="settings-item"><div><strong>Version Web</strong>'+
  '<p>Actualisation automatique du contenu à la réouverture avec connexion Internet.</p></div></div>'+
  '<p class="tiny-note">Thème LoveRoll : bourgogne, noir et rose. L’APK Android est mis à jour séparément.</p>';
 const privacy='<p>Votre journal commun est chiffré. Vos réponses individuelles sur le ressenti restent privées.</p>'+
  (nativeApp?settingRow('Mes anciennes données hors ligne',
   'Ouvrir volontairement le journal Android local non synchronisé.','native-legacy','Ouvrir'):'')+
  (linked?button('lock','Verrouiller le journal partagé','class="subtle"'):'')+
  '<div class="settings-item"><div><strong>Mon ressenti</strong><p>Réglages personnels et confidentiels.</p></div></div>'+
  wellbeingFields(state.wellbeing)+button('save-wellbeing','Enregistrer mon ressenti','class="subtle"');
 const support='<div class="settings-item"><div><strong>Aide et informations</strong>'+
  '<p>Invitation par SMS, Messenger ou courriel. Le lien est privé et expire après 7 jours.</p>'+
  '<p>Vous pouvez toujours refuser un défi et faire une pause.</p>'+
  '<p>Même expérience sur Android, iPhone et iPad. Les écrans s’adaptent à la taille de chaque appareil.</p></div></div>';
 frame('<div class="settings-head"><div class="eyebrow">♡ VOTRE ESPACE À DEUX</div>'+
   '<p>Vos réglages LoveRoll, identiques sur Android, iPhone et iPad.</p></div>'+
   '<div class="settings-grid">'+
   settingGroup('Compte','♙','Prénom, connexion et appareils',account,requestedSettingsSection==='compte')+
   settingGroup('Couple','♥',hasPartner?'Votre espace partagé et vos dates':'Invitations et état de la demande',partner,
     !requestedSettingsSection||requestedSettingsSection==='couple')+
   settingGroup('Notifications','♧','Rappels et préférences',notifications,requestedSettingsSection==='notifications')+
   settingGroup('Jeu et personnalisation','🎲','Modifier les cartes et activités',game,requestedSettingsSection==='jeu')+
   settingGroup('Application','▣','Installation et mises à jour',appSettings,requestedSettingsSection==='application')+
   settingGroup('Confidentialité','♙','Journal chiffré et données personnelles',privacy,requestedSettingsSection==='confidentialite')+
   settingGroup('Aide','?','Informations et fonctionnement',support,requestedSettingsSection==='aide')+'</div>',
  'RÉGLAGES','Compte, couple, notifications et confidentialité');
}
async function refreshAndShow(){await refreshShared();render();}
async function animateRoll(fn){
 state.diceStatus='rolling';render();
 const minDelay=new Promise(resolve=>setTimeout(resolve,1450));
 try{const result=await fn();await minDelay;
  state.diceStatus=null;await refreshAndShow();return result;
 }catch(e){state.diceStatus=null;render();throw e;}
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
 if(!forceNew&&state.inviteDraft)return state.inviteDraft;
 if(!state.coupleId||state.members.length===2)throw Error('Le couple est déjà connecté.');
 const email=normalizeInviteEmail(id('invite-email')?.value);
 if(!CONFIG.siteUrl)throw Error('Adresse publique LoveRoll manquante.');
 const token=randomInviteToken(),digest=await hashToken(token);
 const invitationId=await rpc('create_pair_invite',{p_couple:state.coupleId,p_email:email,p_token_hash:digest});
 const url=new URL(CONFIG.siteUrl);
 // Separate OG crawler cache from old pixelated previews; token stays in URL fragment.
 url.searchParams.set('share','2');url.hash='invite='+token;
 state.inviteDraft={id:invitationId,email,link:url.toString()};
 state.invitations=await query('couple_invitations',{eq:{couple_id:state.coupleId},order:'created_at'});
 return state.inviteDraft;
}
async function handle(action,element){
 const [verb,...parts]=action.split(':');
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
  notify(result?'Notification test planifiée.':'Impossible de préparer le test.');
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
 if(verb==='copy-invite-for-browser'){
  const token=sessionStorage.getItem('pendingInvite');
  if(!/^[0-9a-f]{64}$/i.test(token||''))throw Error('Lien d’invitation introuvable.');
  const u=new URL(CONFIG.siteUrl);u.searchParams.set('share','2');u.hash='invite='+token;
  await navigator.clipboard.writeText(u.toString());
  notify('Lien copié. Ouvrez Safari et collez ce lien dans la barre d’adresse.');return;
 }
 if(verb==='native-update'){
  if(!window.LoveRollNativeAuth?.checkForUpdates)throw Error('Vérification des mises à jour disponible dans l’APK Android.');
  window.LoveRollNativeAuth.checkForUpdates();return;
 }
 if(verb==='native-legacy'){
  if(!window.LoveRollNativeAuth?.openLegacyOffline)throw Error('Anciennes données locales disponibles dans l’APK Android.');
  if(!confirm('Ouvrir votre ancien espace local Android ? Les données hors ligne ne sont pas synchronisées avec votre couple connecté.'))return;
  window.LoveRollNativeAuth.openLegacyOffline();return;
 }
 if(verb==='show-install-guide'){
  state.showInstallSteps=true;
  state.forceInstallGuide=true;
  render();return;
 }
 if(verb==='install-home'){
  const result=await requestInstall();
  if(result.prompted&&result.accepted){dismissInstall(state.user?.id);state.showInstallSteps=false;}
  else state.showInstallSteps=true;
  render();return;
 }
 if(verb==='install-later'||verb==='install-understood'){
  dismissInstall(state.user?.id);state.showInstallSteps=false;state.forceInstallGuide=false;render();return;
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
  if(!confirm('Remplacer la bibliothèque « '+preview.summary.label+' » avec '+
    preview.summary.count+' éléments et '+preview.summary.timers+
    ' minuteur(s) configuré(s) ? Les anciens tirages restent inchangés.'))return;
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
  const contents=id('advent-2026-json');
  if(contents)contents.focus();
  notify('Jour '+day+' : modifiez sa section dans le JSON, puis enregistrez les 25 cases.');
  return;
 }
 if(verb==='monthly-slot'){
  if(!isLoveAdmin())throw Error('Modification réservée à l’administrateur.');
  const face=Number(parts[0]);
  if(!Number.isInteger(face)||face<1||face>20)throw Error('Activité invalide.');
  state.monthlySlot=face;render();return;
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
  if(!confirm('Restaurer cette activité comme à l’origine ? Les anciens tirages resteront inchangés.'))return;
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
  state.customSlot=slot;render();return;
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
  if(!confirm(original?
   'Rétablir la carte officielle et ses réglages par défaut ? Les défis déjà tirés restent inchangés.':
   'Vider cet emplacement ? Les cartes déjà tirées restent dans l’historique.'))return;
  await rpc('delete_love_custom_card',{p_couple:state.coupleId,
   p_category:state.customCategory,p_card_id:state.customSlot});
  await reload();notify(original?'Carte officielle rétablie.':'Emplacement vidé, historique conservé.');return;
 }
 if(verb==='start-couple'){
  state.tab=state.coupleId?'settings':'home';render();return;
 }
 if(verb==='end-couple'){
  if(!state.coupleId)return;
  if(!confirm('Retirer le partenaire et fermer cet espace partagé pour LES DEUX personnes ? Vos propres notes restent dans votre calendrier privé. Vous ne pourrez plus lire les anciennes données communes.'))return;
  await rpc('end_love_match',{p_couple:state.coupleId});
  state.unlockKey=null;state.personalPass=null;
  await reload();notify('Espace partagé fermé. Votre calendrier privé est conservé.');return;
 }
 if(verb==='save-wellbeing'){
  const relationship=id('wellbeing-relationship')?.value,intimacy=id('wellbeing-intimacy')?.value;
  if(!RELATIONSHIP_OPTIONS.some(([v])=>v===relationship)||!INTIMACY_OPTIONS.some(([v])=>v===intimacy))
   throw Error('Répondez aux deux questions ou choisissez « Je préfère ne pas répondre ».');
  await rpc('save_my_wellbeing',{p_relationship:relationship,p_intimacy:intimacy});
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
 if(verb==='lock-personal'){state.personalPass=null;render();return;}
 if(verb==='personal-date'){state.personalSelectedDate=parts[0];render();return;}
 if(verb==='personal-month-prev'||verb==='personal-month-next'){
  const d=new Date((state.personalCalendarMonth||month())+'-01T12:00:00Z');
  d.setUTCMonth(d.getUTCMonth()+(verb==='personal-month-next'?1:-1));
  state.personalCalendarMonth=d.toISOString().slice(0,7);render();return;
 }
 if(verb==='show-auth'){state.showAuth=true;render();return;}
 if(verb==='show-landing'){state.showAuth=false;render();return;}
 if(verb==='google'&&window.LoveRollNativeAuth?.googleSignIn){
  window.LoveRollNativeAuth.googleSignIn();return;
 }
 if(verb==='google'||verb==='apple'){
  sessionStorage.setItem('authStarted','1');
  const redirectTo=CONFIG.siteUrl||location.origin+location.pathname;
  const {error}=await client.auth.signInWithOAuth({provider:verb,options:{redirectTo}});
  if(error)throw error;return;
 }
 if(verb==='email-login'){
  const email=id('signin-email')?.value.trim().toLowerCase();
  if(!email||!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)){
   throw Error('Entre une adresse courriel valide.');
  }
  const redirectTo=CONFIG.siteUrl||location.origin+location.pathname;
  const {error}=await client.auth.signInWithOtp({email,options:{emailRedirectTo:redirectTo,shouldCreateUser:true}});
  if(error)throw error;
  notify('Courriel envoyé. Si un code à 6 chiffres est présent, entre-le ici sans quitter LoveRoll.');
  return;
 }
 if(verb==='verify-email-code'){
  const email=id('signin-email')?.value.trim().toLowerCase();
  const token=id('signin-code')?.value.trim();
  if(!email||!/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(email))
   throw Error('Entre le courriel avec lequel tu as demandé le code.');
  if(!/^\\d{6}$/.test(token||''))throw Error('Saisis le code à six chiffres de ton courriel.');
  const {data,error}=await client.auth.verifyOtp({email,token,type:'email'});
  if(error)throw error;
  if(data?.user){state.user=data.user;state.showAuth=false;await reload();}
  return;
 }
 if(verb==='signout'){
  state.unlockKey=null;state.personalPass=null;state.personalJournal=[];
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  const {error}=await client.auth.signOut();if(error)throw error;
  state.user=null;state.profile=null;state.coupleId=null;state.showAuth=false;render();return;
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
  await reload();return;
 }
 if(verb==='profile'){
  const name=id('profile-name')?.value.trim();
  if(!name||name.length>80)throw Error('Choisis ton nom à afficher.');
  await rpc('complete_love_registration',{p_name:name,p_birthday_md:state.profile.birthday_md});
  await reload();return;
 }
 if(verb==='prepare-cards'){
  if(sessionStorage.getItem('pendingInvite'))throw Error('Une invitation est en attente.');
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
  await ensureInviteDraft(true);render();
  notify('Nouveau lien personnel prêt. Choisissez une méthode de partage.');return;
 }
 if(verb==='send-sms'||verb==='send-email'||verb==='send-messenger'||verb==='copy-invite'){
  // A token cannot be recovered from its hash after closing the application.
  // Generate a fresh, single-use link when the user chooses a sharing method.
  const d=await ensureInviteDraft(false);
  if(verb==='copy-invite'){await navigator.clipboard.writeText(d.link);notify('Lien copié');return;}
  const text=verb==='send-messenger'?invitationMessengerText(d.link):invitationEmailText(d.link);
  state.lastShareMethod=verb==='send-sms'?'sms':verb==='send-email'?'courriel':'messenger';
  if(verb==='send-messenger'){
   if(window.LoveRollNativeAuth?.openShare)window.LoveRollNativeAuth.openShare(text);
   else if(navigator.share){
    try{await navigator.share({title:'LoveRoll ❤️ Invitation privée',text:'Une surprise à deux t’attend ❤️',url:d.link});}
    catch(error){if(error.name!=='AbortError')throw error;}
   }else{
    await navigator.clipboard.writeText(d.link);
    notify('Lien copié : collez-le dans une conversation Messenger.');return;
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
  if(!state.inviteDraft)throw Error('Aucun lien prêt à envoyer.');
  await rpc('mark_pair_invite_sent',{p_invitation:state.inviteDraft.id,p_method:state.lastShareMethod||'partage'});
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
  state.chosenSession=parts[0];state.tab='feedback';render();return;
 }
 if(verb==='save-couple-date'){
  const value=id('couple-started-on')?.value||null;
  await rpc('set_love_relationship_date',{p_couple:state.coupleId,p_date:value});
  await refreshShared();render();notify('Date du couple enregistrée.');return;
 }
 if(verb==='accept-invite'){
  const token=sessionStorage.getItem('pendingInvite');
  if(!token)throw Error('Invitation introuvable.');
  await rpc('accept_love_match',{p_token_hash:await hashToken(token)});
  sessionStorage.removeItem('pendingInvite');await reload();return;
 }
 if(verb==='tab'){
  const ready=isSharedCoupleReady(state.coupleId,state.members);
  const allowed=ready?
   ['home','session','special','occasions','advent','settings','library',
    'personal-journal','journal','ideas','feedback','monthly-library','advent-editor','special-editor']:
   ['home','settings','library','personal-journal','monthly-library','advent-editor','special-editor'];
  if(!allowed.includes(parts[0]))throw Error('Les jeux exigent deux comptes associés.');
  if(['library','monthly-library','advent-editor','special-editor'].includes(parts[0])&&!isLoveAdmin())
   throw Error('Modification réservée à l’administrateur du couple.');
  state.tab=parts[0];state.confirmCancel=false;render();return;
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
   p_kind:kind,p_owner:owner==='-'?null:owner,p_roll:rollType}));
  notify('Résultat '+x.face+' · '+person(x.actor));return;
 }
 if(verb==='roll'){
  const kind=parts[0];
  const result=await animateRoll(()=>rpc('roll_couple_once',{p_couple:state.coupleId,p_kind:kind}));
  notify('Résultat officiel : '+result.face+' · tirage de '+person(result.actor));return;
 }
 if(verb==='new-session'){
  if(!state.weekly)throw Error('Brassez d’abord le dé de la semaine.');
  await rpc('ensure_couple_session',{p_couple:state.coupleId});
  state.tab='session';await refreshAndShow();return;
 }
 if(verb==='my-d6'){
  if(!state.session)throw Error('Aucune soirée active.');
  await animateRoll(()=>rpc('roll_my_evening_d6',{p_session:state.session.id}));
  if(!state.rollStatus?.both_ready)notify('D6 lancé! Les deux résultats apparaîtront ensemble après le dernier tirage.');
  else notify('♡ Les deux résultats D6 sont maintenant révélés!');return;
 }
 if(verb==='category'){if(!EVENING_CATEGORIES.includes(parts[0]))throw Error('Catégorie interdite');state.category=parts[0];state.randomMode=false;render();return;}
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
 if(verb==='new-challenge'){
  if(!state.session)throw Error('Aucune soirée active.');
  if(!EVENING_CATEGORIES.includes(state.category))
   throw Error('Choisissez Sensuel ou Sexuel avant chaque nouveau défi.');
  await animateRoll(()=>rpc('draw_for_partner',{p_session:state.session.id,
   p_preference:state.category,p_random:false}));
  state.category=null; // Require a fresh category choice for the next challenge.
  render();
  return;
 }
 if(verb==='answer'){await rpc('answer_challenge',{p_id:parts[0],p_status:parts[1]});
  await refreshAndShow();return;}
 if(verb==='finish-session'){
  if(!state.session)return;
  await rpc('finish_couple_session',{p_session:state.session.id,p_count:true});
  state.chosenSession=state.session.id;state.tab='session';await refreshAndShow();return;
 }
 if(verb==='cancel-session'){state.confirmCancel=true;render();return;}
 if(verb==='dismiss-cancel'){state.confirmCancel=false;render();return;}
 if(verb==='confirm-cancel'){
  if(!state.session)return;
  await rpc('finish_couple_session',{p_session:state.session.id,p_count:false});
  state.confirmCancel=false;state.tab='home';await refreshAndShow();return;
 }
 if(verb==='advent'){
  const day=Number(parts[0]);state.selectedDay=day;
  await rpc('open_advent_day',{p_couple:state.coupleId,p_day:day});
  await refreshAndShow();return;
 }
 if(verb==='unlock'){
  const pass=id('journal-pass')?.value||'';
  const key=await createCipherKey(pass);
  state.unlockKey=key;
  // Test existing envelope if one exists, to catch wrong passphrase.
  try{
   const probe=state.journal[0]?.ciphertext||state.feedback[0]?.ciphertext;
   if(probe)await decryptJson(probe);
  }catch{state.unlockKey=null;throw Error('Phrase secrète incorrecte pour les données existantes.');}
  notify('Journal déverrouillé sur cet appareil.');render();return;
 }
 if(verb==='lock'){state.unlockKey=null;render();return;}
 if(verb==='date'){state.selectedDate=parts[0];render();return;}
 if(verb==='month-prev'||verb==='month-next'){
  const offset=verb==='month-next'?1:-1;
  const d=new Date((state.calendarMonth||month())+'-01T12:00:00Z');
  d.setUTCMonth(d.getUTCMonth()+offset);
  state.calendarMonth=d.toISOString().slice(0,7);render();return;
 }
 if(verb==='save-event'){
  if(!state.unlockKey)throw Error('Journal verrouillé');
  const date=id('event-date')?.value||today();
  if(date>today())throw Error('La date ne peut pas être dans le futur.');
  const payload={orgasm:id('event-orgasm').value,good:id('event-good').value,
    weak:id('event-weak').value,note:id('event-note').value.slice(0,1200)};
  const ciphertext=await encryptJson(payload);
  await rpc('add_my_love_event',{p_couple:state.coupleId,p_happened_on:date,p_ciphertext:ciphertext});
  state.selectedDate=date;await reload();notify('Moment ajouté au calendrier partagé et à votre calendrier privé');return;
 }
 if(verb==='delete-event'){
  if(!confirm('Supprimer définitivement cette entrée du journal ?'))return;
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
 const fragment=new URLSearchParams(currentUrl.hash.replace(/^#/, ''));
 const invite=fragment.get('invite')||currentUrl.searchParams.get('invite');
 if(invite){
  if(!/^[0-9a-f]{64}$/i.test(invite))throw Error('Ce lien d’invitation est invalide.');
  sessionStorage.setItem('pendingInvite',invite);
  state.showAuth=true;
  fragment.delete('invite');currentUrl.searchParams.delete('invite');
  currentUrl.hash=fragment.toString();
  history.replaceState(null,'',currentUrl.pathname+currentUrl.search+currentUrl.hash);
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
  if(event==='SIGNED_OUT'){state.user=null;state.profile=null;state.coupleId=null;state.wellbeing=null;state.personalPass=null;state.personalJournal=[];render();}
 });
}
document.addEventListener('visibilitychange',()=>{
 if(!document.hidden&&state.user){
  reload().catch(fail);
 }
});
// Android native Google chooser passes the selected ID token to Supabase.
window.addEventListener('loveroll-native-google-result',async event=>{
 const {token,nonce,error}=event.detail||{};
 if(error){notify(error);return;}
 if(!token){notify('Aucun identifiant Google reçu.');return;}
 try{
  const result=await client.auth.signInWithIdToken({provider:'google',token,nonce});
  if(result.error)throw result.error;
  if(result.data?.user){
   state.user=result.data.user;state.showAuth=false;
   await reload();
  }
 }catch(err){fail(err);}
});
start().catch(fail);

if ('serviceWorker' in navigator && location.protocol === 'https:') {
  window.addEventListener('load', () => navigator.serviceWorker.register('./service-worker.js', {scope:'./'}).catch(console.warn));
}
