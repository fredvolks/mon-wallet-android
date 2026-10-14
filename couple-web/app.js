import { marketingHtml,authHtml,profileHtml,wellbeingHtml,wellbeingFields,bindWellbeingMenus,matchHtml,parseMonthDay } from './onboarding.mjs';
import { eveningCard, EVENING_CATEGORIES, formatCountdown,customCardSlots,resolvedCard } from './evening-challenges.mjs';
import {guidedIdeas,guidanceFor,RELATIONSHIP_OPTIONS,INTIMACY_OPTIONS} from './wellbeing.mjs';
import {drawSoloFace,isSharedCoupleReady,SOLO_MESSAGE} from './solo-mode.mjs';
import { SPECIAL_SURPRISES,OCCASIONS,occasionDate,dueOccasions,specialSurprise,occasionRoles,TARGETED_OCCASIONS } from './special-occasions.mjs';
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.57.0';
import { CONFIG } from './config.js';


const root=document.querySelector('#app'),toastEl=document.querySelector('#toast');
const enc=new TextEncoder(),dec=new TextDecoder();
let diceCleanup=[],renderToken=0;
const opts={auth:{flowType:'pkce',autoRefreshToken:true,persistSession:true,detectSessionInUrl:true}};
const client=CONFIG.supabaseUrl&&CONFIG.supabasePublishableKey?
 createClient(CONFIG.supabaseUrl,CONFIG.supabasePublishableKey,opts):null;
const launchParams=new URLSearchParams(location.search);
const state={user:null,profile:null,members:[],coupleId:null,couple:null,
 tab:launchParams.has('settings')?'settings':'home',weekly:null,monthly:{},session:null,personal:[],challenges:[],rollStatus:null,customCards:[],customCategory:'SENSUEL',customSlot:26,
 events:[],journal:[],feedback:[],advent:[],selectedDay:1,unlockKey:null,channel:null,
 occasionSettings:null,occasionRolls:[],wellbeing:null,guidance:'playful',personalJournal:[],personalPass:null,showAuth:launchParams.has('auth'),diceStatus:null,category:'COQUIN',randomMode:false,chosenSession:null,confirmCancel:false,
 solo:{weekly:null,session:null,challenge:null,challengeCategory:'SENSUEL',special:null,host:null,occasion:null,occasionDay:null,adventDay:null}};
const names={SENSUEL:'Sensuel',COQUIN:'Coquin',SEXUEL:'Sexuel'};
let timerCtx=null;
const challengeTimer={id:null,durationMs:0,remainingMs:0,deadline:0,running:false,notified:false,mode:'sound'};
try{const saved=localStorage.getItem('loveroll-timer-mode');if(saved==='vibrate'||saved==='sound')challengeTimer.mode=saved;}catch(e){}

const kinds={weekly:'Dé hebdomadaire',monthly_activity:'Sortie du mois',
 monthly_host:'Qui invite',monthly_restaurant:'Type de restaurant'};
const outings=['Souper en amoureux','Spa nordique','Brunch prolongé','Spectacle ou théâtre',
 'Soirée humour','Cinéma et dessert','Restaurant dégustation','Cours de cuisine',
 'Jeu d’évasion en duo','Musée ou exposition','Massage en duo','Café chic',
 'Balade Vieux-Québec et resto','Bistro et jeu de société','Cocktails ou mocktails',
 'Quilles ou billard','Traversier Québec–Lévis','Fondue ou tapas','Mini-escapade locale',
 'Rendez-vous de saison'];
const restaurantFaces=new Set([1,3,7,13,18]);
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
 root.innerHTML='<header class="header"><div><h1>♡ '+safe(title)+'</h1><p>'+safe(subtitle)+'</p></div>'+
  (state.user?'<button class="subtle" data-action="signout" aria-label="Se déconnecter">↪</button>':'')+
  '</header>'+body+(connected?nav():'');
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
 const tabs=[['home','⌂','Accueil'],['session','🎲','Soirée'],['special','✦','Défi spécial'],
 ['advent','🎁','Avent'],['settings','⚙','Réglages']];
 return '<nav class="nav"><div class="nav-inner">'+tabs.map(([key,icon,label])=>
 '<button data-action="tab:'+key+'" class="'+((state.tab===key||(key==='special'&&state.tab==='occasions'))?'active':'')+'"><span>'+icon+
 '</span>'+safe(label)+'</button>').join('')+'</div></nav>';
}
function bindActions(){
 document.querySelectorAll('[data-action]').forEach(el=>{
  el.addEventListener('click',()=>handle(el.dataset.action,el).catch(fail));
 });
 bindWellbeingMenus();
}
function render(){
 if(!client){frame('<section class="card"><h2>Connexion cloud à configurer</h2>'+
  '<p>Un projet Supabase dédié et son adresse HTTPS sont requis avant d’activer les comptes et le partage.</p></section>');return;}
 if(!state.user){
  if(!state.showAuth){
   root.innerHTML=marketingHtml();bindActions();
  }else frame(authHtml(),'♡ LOVE ROLL','Créer ou retrouver votre espace amoureux');
  return;
 }
 if(!state.profile||!state.profile.birthday_md){
  frame(profileHtml(state.user,state.profile),'♡ MON PROFIL','Première connexion : inscription');
  return;
 }
 if(!state.wellbeing){
  frame(wellbeingHtml(),'♡ MON RESSENTI','Vos réponses sont personnelles');return;
 }
 // Full app navigation is available without a partner. Shared RPC actions remain gated.
 if(!isSharedCoupleReady(state.coupleId,state.members)){
  if(!state.coupleId&&sessionStorage.getItem('pendingInvite')&&!localStorage.getItem('loveroll-skip-'+state.user.id)){
   frame(matchHtml(state.profile.display_name,true),
    '♡ NOTRE MATCH','Inviter un partenaire reste facultatif');return;
  }
  if(!state.coupleId&&!localStorage.getItem('loveroll-skip-'+state.user.id)&&state.tab==='home'){
   frame(matchHtml(state.profile.display_name,false),
    '♡ NOTRE MATCH','Vous pouvez aussi découvrir LoveRoll sans partenaire');return;
  }
  if(state.tab==='settings')renderSettings();
  else if(state.tab==='personal-journal'||state.tab==='journal')
   renderPersonalJournal().catch(fail);
  else if(state.tab==='ideas')renderIdeas();
  else if(state.tab==='session')renderSoloSession();
  else if(state.tab==='special')renderSoloSpecial();
  else if(state.tab==='occasions')renderSoloOccasions();
  else if(state.tab==='advent')renderSoloAdvent();
  else renderSolo();
  return;
 }
 if(state.tab==='home')renderHome();
 else if(state.tab==='personal-journal')renderPersonalJournal().catch(fail);
 else if(state.tab==='ideas')renderIdeas();
 else if(state.tab==='session')renderSession();
 else if(state.tab==='special')renderSpecial();
 else if(state.tab==='occasions')renderOccasions();
 else if(state.tab==='advent')renderAdvent();
 else if(state.tab==='journal')renderJournal().catch(fail);
 else if(state.tab==='feedback')renderFeedback().catch(fail);
 else if(state.tab==='settings')renderSettings();
 else if(state.tab==='library')renderCardLibrary();
}
async function reload(){
 if(!state.user)return;
 const [profiles,assessments,personal]=await Promise.all([
  query('couple_profiles',{eq:{id:state.user.id}}),
  query('love_wellbeing',{eq:{user_id:state.user.id}}),
  query('love_personal_intimacy',{eq:{user_id:state.user.id}})
 ]);
 state.profile=profiles[0]||null;
 state.wellbeing=assessments[0]||null;
 state.personalJournal=personal;
 const membership=await query('couple_members',{eq:{user_id:state.user.id}});
 const previousCouple=state.coupleId;
 state.coupleId=membership[0]?.couple_id||null;
 if(!state.coupleId){
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  state.couple=null;state.members=[];state.weekly=null;state.monthly={};state.customCards=[];state.rollStatus=null;
  state.session=null;state.sessions=[];state.events=[];state.journal=[];
  state.feedback=[];state.advent=[];state.unlockKey=null;
  state.tab=['settings','personal-journal','ideas','session','special','occasions','advent','journal'].includes(state.tab)?state.tab:'home';
  state.guidance=state.wellbeing?await rpc('get_love_guidance',{}):'gentle';
  render();return;
 }
 if(previousCouple&&previousCouple!==state.coupleId)state.unlockKey=null;
 const [couple,members]=await Promise.all([
  query('couples',{eq:{id:state.coupleId}}),
  query('couple_members',{eq:{couple_id:state.coupleId}})
 ]);
 state.couple=couple[0]||null;
 state.customCards=state.couple?.owner_user_id===state.user.id?
  await query('love_custom_challenges',{eq:{couple_id:state.coupleId}}):[];
 const profilesAll=await query('couple_profiles');
 state.members=members.sort((a,b)=>a.joined_at.localeCompare(b.joined_at)).map(m=>({...m,name:profilesAll.find(p=>p.id===m.user_id)?.display_name||'Profil à compléter'}));
 state.guidance=state.wellbeing?await rpc('get_love_guidance',{p_couple:state.coupleId}):'gentle';
 if(members.length===2)await refreshShared();
 subscribe();render();
}
async function refreshShared(){
 const c=state.coupleId;
 const [rolls,sessions,advents,events,diary,occasionSettings,occasionRolls]=await Promise.all([
 query('couple_rolls',{eq:{couple_id:c}}),
 query('couple_sessions',{eq:{couple_id:c},order:'started_at'}),
 query('couple_advent',{eq:{couple_id:c}}),
 query('couple_events',{eq:{couple_id:c},order:'happened_at'}),
 query('couple_intimacy',{eq:{couple_id:c}}),
 query('couple_occasion_settings',{eq:{couple_id:c}}),
 query('couple_occasion_rolls',{eq:{couple_id:c}})
 ]);
 state.occasionSettings=occasionSettings[0]||null;
 state.occasionRolls=occasionRolls;
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
 '<p>'+(!face?(isSharedCoupleReady(state.coupleId,state.members)?'En attente du tirage officiel.':'Prêt pour un essai.'):(isSharedCoupleReady(state.coupleId,state.members)?'Face gagnante : ':'Résultat de l’essai : ')+face)+
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
function soloNote(){
 return '<div class="solo-note">♡ '+safe(SOLO_MESSAGE)+'</div>';
}
function renderSoloSession(){
 const demo=state.solo,category=demo.challengeCategory;
 let body=soloNote()+
  '<section class="card"><div class="eyebrow">Découvrir les soirées</div>'+
  '<h2>Vos dés et vos défis</h2>'+
  '<p>Essayez la mécanique des D6 et du D20 avant d’inviter quelqu’un. '+
  'Aucun tirage ne sera enregistré comme une activité de couple.</p>'+
  heroDice(demo.session,6,'D6 individuel · essai')+
  button('solo-roll:session','🎲 Essayer le D6')+
  '</section><section class="card"><div class="eyebrow">Défi à découvrir</div>'+
  '<h3>Choisissez une ambiance</h3><div class="chipline">'+
  EVENING_CATEGORIES.map(c=>'<button class="pill '+(category===c?'selected':'')+
   '" data-action="solo-category:'+c+'">'+safe(names[c])+'</button>').join('')+
  '</div><p>Vous pouvez essayer des défis pour découvrir leur contenu. '+
  'Toute activité reste facultative et nécessite l’accord des personnes concernées.</p>'+
  heroDice(demo.challenge,20,'D20 · défi d’essai')+
  button('solo-roll:challenge','🎲 Découvrir un défi D20');
 if(demo.challenge){
  const card=eveningCard(category,demo.challenge);
  body+='<div class="solo-result"><div class="eyebrow">'+safe(names[category])+
   ' · N° '+demo.challenge+'</div><h3>'+safe(card.title)+'</h3>'+
   '<p>'+safe(card.description)+'</p>'+timerWidget({id:'solo-'+category+'-'+demo.challenge},card.minutes)+'</div>';
 }
 body+='</section><section class="card">'+
   button('start-couple','♡ Ajouter une personne pour jouer à deux','class="subtle"')+
  '</section>';
 frame(body,'VOTRE SOIRÉE','Découverte libre en mode solo');
}
function renderSoloSpecial(){
 const demo=state.solo;
 let body=soloNote()+
 '<section class="card"><div class="eyebrow">✦ Activité du mois</div>'+
 '<h2>Découvrez les idées de sorties</h2>'+
 '<p>Un D20 sélectionne une sortie parmi les 20 activités de LoveRoll. '+
 'Les tirages ci-dessous sont des essais.</p>'+
 heroDice(demo.special,20,'D20 · sortie')+
 button('solo-roll:special','🎲 Essayer le D20 des sorties');
 if(demo.special){
  body+='<div class="solo-result"><h3>'+safe(outings[demo.special-1])+'</h3>'+
  '<p>Cette idée est un aperçu que vous pourrez proposer plus tard.</p></div>'+
  heroDice(demo.host,6,'D6 · qui organise ?')+
  button('solo-roll:host','🎲 Découvrir le dé « qui invite ? »');
  if(demo.host)body+='<p class="tag">Face '+demo.host+' · '+
   (demo.host<=3?'Le premier partenaire organiserait':'Le deuxième partenaire organiserait')+'</p>';
 }
 body+='</section><section class="card"><h3>💘 Dates et événements spéciaux</h3>'+
 '<p>Explorez les fêtes et les idées-surprises, même sans partenaire connecté.</p>'+
 button('tab:occasions','Voir toutes les occasions')+'</section>';
 frame(body,'DÉFI SPÉCIAL','Essayez les tirages sans créer d’activité partagée');
}
function renderSoloOccasions(){
 const demo=state.solo;
 let body=soloNote()+
 '<section class="card"><div class="eyebrow">Vos occasions</div>'+
 '<h2>Les événements et leurs surprises</h2>'+
 '<p>Découvrez les célébrations proposées par LoveRoll. '+
 'Aucun historique de couple n’est créé dans ce mode.</p>'+
 OCCASIONS.map(o=>'<div class="muted-line"><strong>'+safe(o.emoji)+' '+
   safe(o.title)+'</strong><p>'+safe(o.description)+'</p></div>').join('')+
 '</section><section class="card"><h3>Le dé des 20 surprises</h3>'+
 heroDice(demo.occasion,20,'D20 · surprise')+
 button('solo-roll:occasion','🎲 Découvrir une surprise');
 if(demo.occasion){
  const surprise=specialSurprise(demo.occasion);
  body+='<div class="solo-result"><div class="eyebrow">'+safe(surprise.level)+
    '</div><h3>'+safe(surprise.title)+'</h3><p>'+safe(surprise.detail)+'</p></div>';
 }
 body+='</section>'+button('tab:special','← Défi spécial','class="subtle"');
 frame(body,'FÊTES ET ANNIVERSAIRES','Découvrir les occasions en solo');
}
function renderSoloAdvent(){
 const day=state.solo.adventDay;
 let body=soloNote()+
 '<section class="card"><div class="eyebrow">🎁 Calendrier de l’Avent</div>'+
 '<h2>25 surprises à découvrir</h2>'+
 '<p>En solo, vous pouvez ouvrir chaque case pour visualiser les activités. '+
 'Les cases officielles du couple ne sont pas modifiées.</p>'+
 '<div class="day-grid">'+
 Array.from({length:25},(_,i)=>'<button class="'+(day===i+1?'solo-day-active':'')+
   '" data-action="solo-advent:'+(i+1)+'" aria-label="Découvrir la case '+(i+1)+'">'+
   (day===i+1?'♥ ':'🎁 ')+(i+1)+'</button>').join('')+
 '</div>';
 if(day)body+='<div class="solo-result"><div class="eyebrow">Case '+day+' / 25 · Essai</div>'+
  '<h3>'+safe(advent[day-1])+'</h3><p>À proposer ou à adapter librement, sans obligation.</p></div>';
 body+='</section>';
 frame(body,'CALENDRIER DE L’AVENT','Toutes les cases visibles pour découvrir LoveRoll');
}
function renderSolo(){
 frame('<section class="card"><div class="eyebrow">♡ Découverte libre</div>'+
 '<h2>Bienvenue '+safe(state.profile.display_name)+' ♡</h2>'+
 '<p>Découvrez toutes les fonctionnalités de LoveRoll sans avoir à ajouter de partenaire. '+
 'Les essais sont privés et ne modifient pas votre calendrier.</p>'+
 '<div class="solo-shortcuts">'+
 button('tab:session','🎲 Soirée et dés D6 / D20')+
 button('tab:special','✦ Défis et sorties')+
 button('tab:advent','🎁 Calendrier de l’Avent')+
 button('tab:personal-journal','♥ Mon calendrier personnel')+
 '</div></section>'+ideaPreview()+
 '<section class="card"><h3>♡ Jouer à deux plus tard</h3>'+
 '<p>Vous pourrez lier une personne depuis les réglages lorsque vous serez prêt.</p>'+
 button('start-couple','Ajouter un conjoint ou une conjointe','class="subtle"')+
 '</section>','VOTRE ESPACE','Toute l’application, même en solo');
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
 (state.monthly.monthly_activity?safe(outings[state.monthly.monthly_activity.face-1]):'Surprise à découvrir')+
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
 if(!s)body+=button('new-session','Commencer une soirée commune');
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
     ' · CARTE '+safe(challenge.cardId||active.card_id||active.face)+' · D20 : '+active.face+'</div>'+
     '<div class="challenge-roles">♡ '+safe(p.name).toUpperCase()+' OFFRE → '+safe(state.profile.display_name).toUpperCase()+' REÇOIT</div>'+
     '<h3>'+safe(challenge.title)+'</h3>'+
     '<p>'+safe(challenge.description)+'</p>' +
     timerWidget(active,challenge.minutes)+
     '<div class="actions">'+
     (active.status==='proposed'?button('answer:'+active.id+':accepted','Accepter'):'')+
     (active.status==='accepted'?button('answer:'+active.id+':completed','Terminé'):'')+
     button('answer:'+active.id+':passed','Passer')+'</div></div>';
   }else if(completed<mine.face)body+='<p>Votre partenaire brasse le D20 pour vous proposer le prochain défi.</p>';
   const otherActive=state.challenges.filter(x=>x.recipient===p.user_id&&['proposed','accepted'].includes(x.status)).at(-1);
   const otherCompleted=state.challenges.filter(x=>x.recipient===p.user_id&&x.status==='completed').length;
   if(!otherActive&&otherCompleted<theirs.face){
    body+='<div class="card"><h3>Offrir un défi à '+safe(p.name)+'</h3>'+
     '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
     '<button class="pill '+(state.category===c?'selected':'')+'" data-action="category:'+c+'">'+names[c]+'</button>').join('')+'</div>'+
     '<p>Chaque défi : 80 % de chances de tomber dans la catégorie choisie, 20 % dans l’autre.</p>'+
     button('new-challenge','🎲 Brasser le D20 pour '+safe(p.name))+'</div>';
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
 body+='</section>';frame(body,'VOTRE SOIRÉE',s?'Étapes partagées et défis personnalisés':'Un nouveau moment à deux');
}
function renderSpecial(){
 const a=state.monthly.monthly_activity,h=state.monthly.monthly_host,r=state.monthly.monthly_restaurant;
 let body='<div class="eyebrow">Défi spécial · '+safe(month())+'</div>'+
 '<section class="card"><h2>Votre rendez-vous du mois</h2>';
 if(!a)body+=heroDice(null,20,'Une des 20 idées de sortie')+
  button('roll:monthly_activity','🎲 Brasser le D20 mensuel');
 else{
  body+=heroDice(a.face,20,'Activité officielle')+'<div class="result">'+safe(outings[a.face-1])+'</div>'+
  '<p>Tiré par '+safe(person(a.actor))+'.</p>';
  if(!h)body+=button('roll:monthly_host','🎲 Brasser le D6 : qui invite ?');
  else{
   const host=h.face<=3?state.members[0]:state.members[1]; // ordered by join time, identically on both devices
   body+='<p class="tag">♡ '+safe(host.name)+' invite '+safe(state.members.find(m=>m.user_id!==host.user_id).name)+
    ' · D6 face '+h.face+'</p>';
   if(restaurantFaces.has(a.face)){
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
  '<p>20 surprises romantiques, coquines et audacieuses. Un D20 officiel puis un D6 pour qui prépare la surprise, une seule fois par occasion.</p>'+
  '<p>Chaque idée est facultative : vous pouvez en discuter, la modifier ou la refuser.</p></section>';
 for(const ev of all.sort((a,b)=>(a.daysLeft===null?1:0)-(b.daysLeft===null?1:0))){
  const k=ev.key+(ev.actorId?':'+ev.actorId:'');
  const prize=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='surprise');
  const host=state.occasionRolls.find(r=>r.occasion_key===k&&r.event_year===ev.year&&r.roll_type==='host');
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
  else if(!prize){if(role.shared||role.receiver)body+=heroDice(null,20,'Dé de fête')+
    button('occasion-roll:'+ev.key+':'+(ev.actorId||'-')+':surprise','🎲 Brasser le D20 de la surprise');}
  else{
   const idea=specialSurprise(prize.face);
   body+=heroDice(prize.face,20,'Résultat officiel')+
     '<h3>'+safe(idea.title)+'</h3><p>'+safe(idea.detail)+'</p>'+
     '<p class="tag">'+safe(idea.level)+' · Tiré par '+safe(person(prize.actor))+'</p>';
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
function renderAdvent(){
 const now=today();const [year,mo,day]=now.split('-').map(Number),unlocked=mo===12?Math.min(day,25):0;
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
  body+='<div class="card"><h3>Case '+selected.day+' · '+safe(advent[selected.day-1])+'</h3>'+
   '<p>Ouverte par '+safe(person(selected.opened_by))+
   '. À proposer par '+safe(person(selected.performed_by))+
   ' (vous pouvez toujours modifier ou passer l’activité ensemble).</p></div>';
 }
 body+='</section>';
 frame(body,'CALENDRIER DE L’AVENT','Votre rituel commun en décembre');
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
 body+='<section class="card"><h3>★ Avis de nos soirées</h3>'+
  button('tab:feedback','Consulter et répondre aux questionnaires')+'</section>';
 frame(body,'NOTRE JOURNAL','Un calendrier partagé, du contenu chiffré');
}
async function renderFeedback(){
 let sessions=state.sessions||[];
 let session=sessions.find(x=>x.id===state.chosenSession)||
  sessions.find(x=>x.finished_at) || sessions[0];
 if(!session){
  frame('<section class="card"><p>Terminez une soirée pour partager vos avis respectifs.</p></section>',
   'NOS AVIS','Les deux réponses sont consultables ensemble');return;
 }
 state.chosenSession=session.id;
 const entries=state.feedback.filter(f=>f.session_id===session.id);
 let body='<section class="card"><h2>Soirée du '+safe(session.started_at.slice(0,10))+'</h2>'+
 '<p>Chacun répond sur son propre compte. Les deux partenaires peuvent ensuite lire les deux avis.</p>';
 if(!state.unlockKey){
  body+='<p>Déverrouillez le journal chiffré pour lire les réponses.</p>'+
   '<input type="password" id="journal-pass" placeholder="Phrase secrète commune">'+button('unlock','Déverrouiller');
 }else{
  for(const member of state.members){
   const entry=entries.find(x=>x.author_id===member.user_id);
   let answer;
   if(entry){try{answer=await decryptJson(entry.ciphertext)}catch{}}
   body+='<div class="muted-line"><h3>'+safe(member.name)+'</h3>'+
    (answer?'<p>'+formattedStars(answer.rating)+'<br>Préféré : '+safe(answer.liked)+
     '<br>Pour la prochaine fois : '+safe(answer.next)+'</p>':
     '<p>En attente de réponse ou phrase secrète incorrecte.</p>')+'</div>';
  }
  if(!entries.find(x=>x.author_id===state.user.id))
  body+='<h3>Mon questionnaire</h3><label>Ma note'+ratingChoice('rating')+'</label>'+
   '<label>J’ai préféré<select id="feedback-liked">'+
   ['La complicité','La tendresse','Les défis','L’ambiance','Les surprises'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label><label>Pour la prochaine fois<select id="feedback-next">'+
   ['Rien à changer','Plus de douceur','Plus de temps','Plus de surprises','Rythme différent'].map(x=>'<option>'+safe(x)+'</option>').join('')+
   '</select></label>'+button('save-feedback','Enregistrer MON avis');
 }
 body+='</section>';frame(body,'NOS QUESTIONNAIRES','Une réponse individuelle, visible par le couple');
}
function isLoveAdmin(){return !!(state.coupleId&&state.couple?.owner_user_id===state.user?.id);}
function renderCardLibrary(){
 if(!isLoveAdmin()){state.tab='settings';renderSettings();return;}
 const category=state.customCategory,slots=customCardSlots(category,state.customCards);
 const slot=slots.find(x=>x.cardId===state.customSlot)||slots[0];
 let body='<section class="card"><div class="eyebrow">ADMINISTRATEUR UNIQUEMENT</div>'+
  '<h2>Bibliothèque de défis</h2>'+
  '<p>25 cartes Sensuel + 25 cartes Sexuel prêtes à jouer, avec 10 emplacements libres par catégorie. Votre partenaire ne peut pas consulter ce catalogue.</p>'+
  '<div class="chipline">'+EVENING_CATEGORIES.map(c=>
  '<button class="pill '+(category===c?'selected':'')+
  '" data-action="custom-category:'+c+'">'+safe(names[c])+'</button>').join('')+'</div></section>'+
  '<section class="card"><h3>25 cartes · '+safe(names[category])+'</h3>'+
  Array.from({length:25},(_,i)=>{
   const card=eveningCard(category,i+1);
   return '<details class="custom-detail"><summary>'+String(i+1).padStart(2,'0')+
    ' · '+safe(card.title)+' · '+(card.minutes?card.minutes+' min':'Sans minuteur')+'</summary>'+
    '<p>'+safe(card.description)+'</p></details>';
  }).join('')+'</section>'+
  '<section class="card"><div class="eyebrow">VOS EMPLACEMENTS PRIVÉS</div>'+
  '<h3>10 cartes personnalisées</h3>'+
  '<p>Vous pouvez en remplir une, quelques-unes ou aucune. Les emplacements vides ne sont jamais tirés.</p>'+
  '<div class="custom-slot-grid">'+slots.map(c=>'<button type="button" class="'+
  (c.filled?'custom-filled ':'')+(slot.cardId===c.cardId?'custom-active':'')+
  '" data-action="custom-slot:'+c.cardId+'">N° '+(c.cardId-25)+
  (c.filled?' ✓':' · Vide')+'</button>').join('')+'</div>'+
  '<h3>Carte personnalisée N° '+(slot.cardId-25)+'</h3>'+
  '<label>Titre<input id="custom-title" maxlength="120" value="'+safe(slot.title)+
  '" placeholder="Le nom de votre carte"></label>'+
  '<label>Consigne<textarea id="custom-description" maxlength="900" rows="4"'+
  ' placeholder="Votre défi personnalisé, écrit librement.">'+safe(slot.description)+'</textarea></label>'+
  '<div class="custom-receiver"><strong>Qui peut recevoir cette carte ?</strong>'+
  '<label><input type="radio" name="custom-recipient" value=""'+(!slot.recipientId?' checked':'')+
  '> Les deux partenaires</label>'+
  state.members.map(m=>'<label><input type="radio" name="custom-recipient" value="'+safe(m.user_id)+'"'+
    (slot.recipientId===m.user_id?' checked':'')+'> Seulement '+safe(m.name)+'</label>').join('')+
  '<p class="tiny-note">Si une carte est réservée à une personne, elle ne peut jamais sortir pour l’autre.</p></div>'+
  '<label><input type="checkbox" id="custom-timer-enabled"'+
  (slot.minutes?' checked':'')+'> Ajouter un minuteur (facultatif)</label>'+
  '<label>Durée en minutes<input id="custom-minutes" type="number" min="1" max="60"'+
  ' value="'+(slot.minutes||5)+'"></label>'+
  '<p class="tiny-note">Sans case cochée, la carte est libre de durée.</p>'+
  button('save-custom-card','Enregistrer cette carte')+
  (slot.filled?button('delete-custom-card','Vider cet emplacement','class="subtle"'):'')+
  '</section>'+
  '<section class="card">'+button('tab:settings','← Réglages','class="subtle"')+'</section>';
 frame(body,'MES CARTES','Réservé au compte administrateur');
}
function renderSettings(){
 const linked=!!state.coupleId,hasPartner=linked&&state.members.length===2;
 let partner='<section class="card"><h3>♡ Gérer mon couple</h3>';
 if(hasPartner){
  partner+='<p>Partenaire actuel : '+safe(other()?.name||'Compte associé')+'</p>'+
  '<p class="tiny-note">Dissocier les comptes retire immédiatement aux deux personnes l’accès à l’ancien calendrier partagé. Chaque personne conserve ses propres entrées dans son calendrier privé.</p>'+
  button('end-couple','Retirer mon conjoint ou ma conjointe','class="subtle danger-action"');
 }else if(linked){
  partner+='<p>En attente d’un partenaire. Invitez-le ou laissez cette étape pour plus tard.</p>'+
  '<label>Courriel de votre partenaire<input id="invite-email" type="email" autocomplete="email" placeholder="partenaire@exemple.com"></label>'+
  button('invite','Inviter mon conjoint ou ma conjointe')+
  button('end-couple','Annuler cet espace à deux','class="subtle"');
 }else{
  partner+='<p>Vous n’avez pas de partenaire lié. Votre calendrier personnel reste disponible.</p>'+
  button('start-couple','Ajouter un conjoint ou une conjointe');
 }
 partner+='</section>';
 const libraryPanel=isLoveAdmin()?'<section class="card"><div class="eyebrow">ADMINISTRATEUR</div>'+ 
 '<h3>♡ Mes défis et cartes personnalisées</h3>'+ 
 '<p>25 Sensuel, 25 Sexuel et jusqu’à 10 cartes personnelles par catégorie.</p>'+ 
 button('tab:library','Gérer les défis')+'</section>':'';
 frame('<section class="card"><h2>Mon compte</h2><p>'+
  safe(state.profile.display_name)+' · '+safe(state.user.email||'Courriel privé')+'</p>'+
  '<label>Mon nom à afficher<input id="profile-name" value="'+safe(state.profile.display_name)+'" maxlength="80"></label>'+
  button('profile','Mettre à jour mon nom')+'</section>'+
  partner+libraryPanel+
  '<section class="card"><h3>♥ Mon calendrier personnel</h3>'+
  '<p>Vos propres moments demeurent dans votre espace privé, même après avoir retiré un partenaire. Une nouvelle personne ne peut pas consulter votre ancien historique.</p>'+
  button('tab:personal-journal','Ouvrir mon calendrier personnel')+'</section>'+
  (hasPartner?'<section class="card"><h3>♡ Notre journal commun</h3>'+
   button('tab:journal','Ouvrir le calendrier et les statistiques')+
   button('tab:feedback','Nos questionnaires de soirées','class="subtle"')+'</section>':'')+
  '<section class="card"><h3>Mon ressenti · confidentiel</h3>'+wellbeingFields(state.wellbeing)+
  button('save-wellbeing','Mettre à jour mes réponses')+
  '<p class="tiny-note">Votre partenaire ne voit pas vos réponses individuelles. Les propositions de soirées utilisent une orientation générale.</p></section>'+
  '<section class="card"><h3>🔐 Vie privée</h3><p>Les réponses restent privées. Votre ancien espace partagé est bloqué après une dissociation; les copies déjà conservées par quelqu’un ne peuvent pas être effacées à distance.</p>'+
  (linked?button('lock','Verrouiller le journal','class="subtle"'):'')+'</section>'+
  '<section class="card"><h3>🎂 Mes dates</h3><p>Mon anniversaire : '+
  safe(state.profile.birthday_md)+' (enregistré à mon inscription).</p>'+
  (linked?'<p>Anniversaire du couple : '+safe(state.occasionSettings?.anniversary_md||'Non renseigné')+'</p>':'')+
  '</section>'+
  (isLoveAdmin()&&hasPartner?'<section class="card"><div class="eyebrow">FÊTES · ADMINISTRATEUR</div>'+
   '<h3>À qui offrir la surprise ?</h3>'+
   '<p>Fête des Mères / Femmes : l’autre partenaire offre. Fête des Pères / Hommes : même règle inversée. On ne brasse pas pour changer ces rôles.</p>'+
   TARGETED_OCCASIONS.map(key=>{
    const entry=OCCASIONS.find(o=>o.key===key),chosen=state.occasionSettings?.celebrants?.[key];
    return '<div class="muted-line"><strong>'+safe(entry.title)+'</strong>'+
     '<div class="chipline">'+state.members.map(m=>
      '<button class="pill '+(chosen===m.user_id?'selected':'')+
      '" data-action="set-celebrant:'+key+':'+m.user_id+'">'+safe(m.name)+'</button>').join('')+'</div></div>';
   }).join('')+'</section>':''),

 'RÉGLAGES','Profil, confidentialité et gestion du partenaire');
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
async function handle(action,element){
 const [verb,...parts]=action.split(':');
 if(verb==='skip-couple'){
  if(sessionStorage.getItem('pendingInvite'))throw Error('Traitez ou fermez d’abord votre invitation en attente.');
  localStorage.setItem('loveroll-skip-'+state.user.id,'1');state.tab='home';render();return;
 }
 if(verb==='set-celebrant'){
  if(!isLoveAdmin())throw Error('Réglage réservé à l’administrateur.');
  if(!TARGETED_OCCASIONS.includes(parts[0])||
    !state.members.some(m=>m.user_id===parts[1]))throw Error('Personne célébrée invalide.');
  await rpc('set_love_celebrant',{p_couple:state.coupleId,p_kind:parts[0],p_user:parts[1]});
  await reload();notify('Personne célébrée enregistrée.');return;
 }
 if(verb==='custom-category'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  if(!EVENING_CATEGORIES.includes(parts[0]))throw Error('Catégorie invalide.');
  state.customCategory=parts[0];state.customSlot=26;render();return;
 }
 if(verb==='custom-slot'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  const slot=Number(parts[0]);if(!Number.isInteger(slot)||slot<26||slot>35)throw Error('Emplacement invalide.');
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
  const recipient=document.querySelector('[name="custom-recipient"]:checked')?.value||null;
  if(recipient&&!state.members.some(m=>m.user_id===recipient))throw Error('Destinataire inconnu.');
  await rpc('save_love_custom_card',{p_couple:state.coupleId,
   p_category:state.customCategory,p_card_id:state.customSlot,
   p_title:title,p_description:description,p_timer_minutes:minutes,p_recipient:recipient});
  await reload();notify('Carte enregistrée.');return;
 }
 if(verb==='delete-custom-card'){
  if(!isLoveAdmin())throw Error('Accès réservé à l’administrateur.');
  if(!confirm('Vider cet emplacement ? Les cartes déjà tirées restent dans l’historique.'))return;
  await rpc('delete_love_custom_card',{p_couple:state.coupleId,
   p_category:state.customCategory,p_card_id:state.customSlot});
  await reload();notify('Emplacement vidé, historique conservé.');return;
 }
 if(verb==='start-couple'){
  if(state.coupleId){state.tab='settings';render();return;}
  localStorage.removeItem('loveroll-skip-'+state.user.id);state.tab='home';render();return;
 }
 if(verb==='solo-roll'){
  if(isSharedCoupleReady(state.coupleId,state.members))throw Error('Utilisez les tirages partagés pour votre couple.');
  const kind=parts[0],sizes={weekly:6,session:6,challenge:20,special:20,host:6,occasion:20};
  if(!Object.hasOwn(sizes,kind))throw Error('Dé d’essai inconnu.');
  state.solo[kind]=drawSoloFace(sizes[kind]);
  if(kind==='special')state.solo.host=null;
  render();return;
 }
 if(verb==='solo-category'){
  if(isSharedCoupleReady(state.coupleId,state.members))throw Error('Le mode solo est indisponible dans un couple connecté.');
  if(!EVENING_CATEGORIES.includes(parts[0]))throw Error('Catégorie inconnue.');
  state.solo.challengeCategory=parts[0];state.solo.challenge=null;
  render();return;
 }
 if(verb==='solo-advent'){
  if(isSharedCoupleReady(state.coupleId,state.members))throw Error('Utilisez le calendrier partagé.');
  const day=Number(parts[0]);
  if(!Number.isInteger(day)||day<1||day>25)throw Error('Case invalide.');
  state.solo.adventDay=day;render();return;
 }
 if(verb==='end-couple'){
  if(!state.coupleId)return;
  if(!confirm('Retirer le partenaire et fermer cet espace partagé pour LES DEUX personnes ? Vos propres notes restent dans votre calendrier privé. Vous ne pourrez plus lire les anciennes données communes.'))return;
  await rpc('end_love_match',{p_couple:state.coupleId});
  localStorage.setItem('loveroll-skip-'+state.user.id,'1');
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
 if(verb==='create-couple'){
  const date=parseMonthDay('signup-couple',false);
  await rpc('create_love_match',{p_anniversary_md:date});
  await reload();return;
 }
 if(verb==='invite'){
  const email=id('invite-email')?.value.trim().toLowerCase();
  if(!email||!email.includes('@'))throw Error('Saisissez un courriel valide.');
  if(!CONFIG.siteUrl)throw Error('L’adresse publique HTTPS doit être configurée pour envoyer des invitations.');
  const token=randomInviteToken(),digest=await hashToken(token);
  await rpc('create_pair_invite',{p_couple:state.coupleId,p_email:email,p_token_hash:digest});
  const url=new URL(CONFIG.siteUrl);url.hash='invite='+token;
  const text='Je t’invite à rejoindre notre espace LoveRoll ♡\n\n'+url.toString()+
   '\n\nConnecte-toi avec le même courriel que celui qui reçoit cette invitation.';
  const mail='mailto:'+encodeURIComponent(email)+'?subject='+encodeURIComponent('Invitation LoveRoll')+
   '&body='+encodeURIComponent(text);
  window.location.href=mail;
  notify('Votre courriel est prêt : confirmez son envoi dans votre application de messagerie.');
  return;
 }
 if(verb==='accept-invite'){
  const token=sessionStorage.getItem('pendingInvite');
  if(!token)throw Error('Invitation introuvable.');
  await rpc('accept_love_match',{p_token_hash:await hashToken(token)});
  sessionStorage.removeItem('pendingInvite');await reload();return;
 }
 if(verb==='tab'){
  const allowed=['home','session','special','occasions','advent','settings','library',
   'personal-journal','journal','ideas','feedback'];
  if(parts[0]==='library'&&!isLoveAdmin())throw Error('Bibliothèque réservée à l’administrateur du couple.');
  if(!allowed.includes(parts[0]))throw Error('Onglet inconnu.');
  // The full discovery experience is available in solo mode. No shared writes.
  state.tab=parts[0]==='feedback'&&!isSharedCoupleReady(state.coupleId,state.members)?
   'session':parts[0];
  state.confirmCancel=false;render();return;
 }
 if(['save-occasion-date','occasion-roll','roll','new-session','my-d6',
    'new-challenge','answer','finish-session','cancel-session','confirm-cancel',
    'save-event','delete-event','save-feedback'].includes(verb)&&
    !isSharedCoupleReady(state.coupleId,state.members))
   throw Error('Cette action nécessite deux comptes associés. Les démonstrations solo ne modifient pas les données du couple.');
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
  await animateRoll(()=>rpc('draw_for_partner',{p_session:state.session.id,
   p_preference:state.category,p_random:false}));
  return;
 }
 if(verb==='answer'){await rpc('answer_challenge',{p_id:parts[0],p_status:parts[1]});
  await refreshAndShow();return;}
 if(verb==='finish-session'){
  if(!state.session)return;
  await rpc('finish_couple_session',{p_session:state.session.id,p_count:true});
  state.chosenSession=state.session.id;state.tab='feedback';await refreshAndShow();return;
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
  await refreshAndShow();notify('Votre avis est sauvegardé et visible par votre partenaire');return;
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
