import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.57.0';
import { CONFIG } from './config.js';


const root=document.querySelector('#app'),toastEl=document.querySelector('#toast');
const enc=new TextEncoder(),dec=new TextDecoder();
let diceCleanup=[],renderToken=0;
const opts={auth:{flowType:'pkce',autoRefreshToken:true,persistSession:true,detectSessionInUrl:true}};
const client=CONFIG.supabaseUrl&&CONFIG.supabasePublishableKey?
 createClient(CONFIG.supabaseUrl,CONFIG.supabasePublishableKey,opts):null;
const state={user:null,profile:null,members:[],coupleId:null,couple:null,
 tab:'home',weekly:null,monthly:{},session:null,personal:[],challenges:[],
 events:[],journal:[],feedback:[],advent:[],selectedDay:1,unlockKey:null,channel:null,
 diceStatus:null,category:'COQUIN',randomMode:false,chosenSession:null,confirmCancel:false};
const names={SENSUEL:'Sensuel',COQUIN:'Coquin',SEXUEL:'Sexuel'};
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
function frame(body,title='DÉS À DEUX',subtitle='Un jeu intime, juste pour vous deux'){
 const connected=!!state.coupleId;
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
 '<button data-action="tab:'+key+'" class="'+(state.tab===key?'active':'')+'"><span>'+icon+
 '</span>'+safe(label)+'</button>').join('')+'</div></nav>';
}
function bindActions(){document.querySelectorAll('[data-action]').forEach(el=>{
 el.addEventListener('click',()=>handle(el.dataset.action,el).catch(fail));
});}
function render(){
 if(!client){frame('<section class="card"><h2>Connexion cloud à configurer</h2>'+
  '<p>Un projet Supabase dédié et son adresse HTTPS sont requis avant d’activer les comptes et le partage.</p></section>');return;}
 if(!state.user){frame('<section class="card"><div class="eyebrow">Votre espace privé</div>'+
 '<h2>Connectez-vous pour créer ou rejoindre votre couple</h2>'+
 '<div class="actions">'+button('google','Continuer avec Google')+button('apple','Continuer avec Apple')+
 '</div><p>Les noms viennent de votre inscription. Chaque partenaire possède son propre compte.</p></section>');return;}
 if(!state.profile){const proposed=state.user.user_metadata?.full_name||state.user.user_metadata?.name||'';
  frame('<section class="card"><h2>Votre nom dans le jeu</h2><p>Ce nom apparaîtra sur les dés et les invitations. Il provient de votre inscription et peut être corrigé ici.</p>'+
  '<label>Nom à afficher<input id="profile-name" autocomplete="name" maxlength="80" value="'+safe(proposed)+'"></label>'+
  button('profile','Continuer')+'</section>');return;}
 if(!state.coupleId){frame('<section class="card"><h2>Bienvenue '+safe(state.profile.display_name)+'</h2>'+
  (sessionStorage.getItem('pendingInvite')?
  '<p>Vous avez une invitation. Acceptez-la pour retrouver votre espace commun.</p>'+
   button('accept-invite','Accepter le match'):'<p>Créez votre espace de couple, puis invitez votre partenaire par courriel.</p>'+
   button('create-couple','Créer mon couple'))+'</section>');return;}
 if(state.members.length<2){frame('<section class="card"><h2>Votre match</h2><p>Bonjour '+safe(state.profile.display_name)+
  ' ! Invitez votre partenaire avec son courriel de connexion.</p>'+
 '<label>Courriel de votre partenaire<input id="invite-email" type="email" autocomplete="email" placeholder="partenaire@exemple.com"></label>'+
 button('invite','Préparer l’invitation par courriel')+
 '<p class="muted">L’envoi s’ouvre dans votre application courriel. Votre partenaire doit confirmer l’envoi du courriel d’invitation.</p></section>');
 return;}
 if(state.tab==='home')renderHome();
 else if(state.tab==='session')renderSession();
 else if(state.tab==='special')renderSpecial();
 else if(state.tab==='advent')renderAdvent();
 else if(state.tab==='journal')renderJournal().catch(fail);
 else if(state.tab==='feedback')renderFeedback().catch(fail);
 else if(state.tab==='settings')renderSettings();
}
async function reload(){
 if(!state.user)return;
 const profiles=await query('couple_profiles',{eq:{id:state.user.id}});
 state.profile=profiles[0]||null;
 if(!state.profile)return render();
 const membership=await query('couple_members',{eq:{user_id:state.user.id}});
 state.coupleId=membership[0]?.couple_id||null;
 if(!state.coupleId)return render();
 const [couple,members]=await Promise.all([
 query('couples',{eq:{id:state.coupleId}}),
 query('couple_members',{eq:{couple_id:state.coupleId}})
 ]);
 state.couple=couple[0]||null;
 const profilesAll=await query('couple_profiles');
 state.members=members.sort((a,b)=>a.joined_at.localeCompare(b.joined_at)).map(m=>({...m,name:profilesAll.find(p=>p.id===m.user_id)?.display_name||'Profil à compléter'}));
 if(members.length===2)await refreshShared();
 subscribe();
 render();
}
async function refreshShared(){
 const c=state.coupleId;
 const [rolls,sessions,advents,events,diary]=await Promise.all([
 query('couple_rolls',{eq:{couple_id:c}}),
 query('couple_sessions',{eq:{couple_id:c},order:'started_at'}),
 query('couple_advent',{eq:{couple_id:c}}),
 query('couple_events',{eq:{couple_id:c},order:'happened_at'}),
 query('couple_intimacy',{eq:{couple_id:c}})
 ]);
 state.weekly=rolls.find(r=>r.kind==='weekly'&&r.key===week());
 state.monthly={};
 for(const r of rolls.filter(r=>r.key===month()))state.monthly[r.kind]=r;
 state.session=sessions.find(s=>s.finished_at===null)||null;
 state.sessions=sessions;state.events=events;state.journal=diary;state.advent=advents;
 if(state.session){
  const [personal,challenges]=await Promise.all([
    query('couple_personal_rolls',{eq:{session_id:state.session.id}}),
    query('couple_challenges',{eq:{session_id:state.session.id},order:'created_at'})
  ]);
  state.personal=personal;state.challenges=challenges;
 }else{state.personal=[];state.challenges=[];}
 state.feedback=await query('couple_feedback',{eq:{couple_id:c}});
}
function subscribe(){
 if(!state.coupleId)return;
 if(state.channel)client.removeChannel(state.channel);
 let channel=client.channel('shared-couple-'+state.coupleId);
 channel=channel.on('postgres_changes',{event:'INSERT',schema:'public',table:'couple_members',
  filter:'couple_id=eq.'+state.coupleId},async()=>{
   try{await reload();notify('♡ Votre partenaire a accepté le match !');}catch(e){fail(e);}
 });
 if(state.members.length<2){state.channel=channel.subscribe();return;}
 for(const table of ['couple_rolls','couple_sessions','couple_challenges',
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
function renderHome(){
 const name=state.profile.display_name,partner=other()?.name;
 frame('<div class="eyebrow">Votre couple</div>'+
 '<section class="card"><h2>'+safe(name)+' ♡ '+safe(partner)+'</h2>'+
 '<p>Votre rituel de la semaine · un seul dé pour votre couple.</p>'+
 heroDice(state.weekly?.face,6,'Dé hebdomadaire')+
 (state.weekly?'<div class="result">'+state.weekly.face+' soirée(s) cette semaine</div>'+
 '<p>Ce résultat a été brassé par '+safe(person(state.weekly.actor))+'. Prochain tirage lundi.</p>':
 button('roll:weekly','🎲 Brasser notre D6 hebdomadaire'))+'</section>'+
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
function renderSession(){
 let s=state.session; const p=other();
 let body='<section class="card"><h2>Une soirée, deux D6 individuels</h2>'+
 '<p>Chacun brasse uniquement son propre dé. Le résultat est immédiatement visible par l’autre.</p>';
 if(!s)body+=button('new-session','Commencer une soirée commune');
 else{
  const mine=state.personal.find(x=>x.user_id===state.user.id);
  const theirs=state.personal.find(x=>x.user_id===p.user_id);
  body+=heroDice(mine?.face,6,'Mon dé personnel')+
  '<div class="two"><div class="stat"><strong>'+safe(mine?.face||'—')+'</strong><small>'+safe(state.profile.display_name)+'</small></div>'+
  '<div class="stat"><strong>'+safe(theirs?.face||'—')+'</strong><small>'+safe(p.name)+'</small></div></div>';
  if(!mine)body+=button('my-d6','🎲 Brasser MON D6');
  else if(!theirs)body+='<p>En attente du D6 de '+safe(p.name)+'.</p>';
  else{
   const proposed=state.challenges.filter(x=>x.recipient===state.user.id);
   const active=proposed.filter(x=>['proposed','accepted'].includes(x.status)).at(-1);
   const completed=proposed.filter(x=>x.status==='completed').length;
   body+='<p>Mes défis terminés : '+completed+' / '+mine.face+
    ' · '+safe(p.name)+' donne mes défis.</p>';
   if(active){
    body+='<div class="card"><div class="eyebrow">'+safe(names[active.category])+
     ' · Face '+active.face+'</div><h3>'+safe(ideas[active.category][active.face-1])+'</h3>'+
     '<p>'+safe(p.name)+' propose → '+safe(state.profile.display_name)+' reçoit</p>'+
     '<div class="actions">'+
     (active.status==='proposed'?button('answer:'+active.id+':accepted','Accepter'):'')+
     (active.status==='accepted'?button('answer:'+active.id+':completed','Terminé'):'')+
     button('answer:'+active.id+':passed','Passer')+'</div></div>';
   }else if(completed<mine.face)body+='<p>Votre partenaire brasse le D20 pour vous proposer le prochain défi.</p>';
   const otherActive=state.challenges.filter(x=>x.recipient===p.user_id&&['proposed','accepted'].includes(x.status)).at(-1);
   const otherCompleted=state.challenges.filter(x=>x.recipient===p.user_id&&x.status==='completed').length;
   if(!otherActive&&otherCompleted<theirs.face){
    body+='<div class="card"><h3>Offrir un défi à '+safe(p.name)+'</h3>'+
     '<div class="chipline">'+['SENSUEL','COQUIN','SEXUEL'].map(c=>
     '<button class="pill '+(!state.randomMode&&state.category===c?'selected':'')+'" data-action="category:'+c+'">'+names[c]+'</button>').join('')+
     '<button class="pill '+(state.randomMode?'selected':'')+'" data-action="random">Aléatoire</button></div>'+
     '<p>'+(state.randomMode?'30 % Sensuel · 30 % Coquin · 40 % Sexuel':
       '70 % catégorie choisie · 15 % chacun des deux autres')+'</p>'+
     button('new-challenge','🎲 Brasser le D20 pour '+safe(p.name))+'</div>';
   }else if(otherActive){body+='<p>Défi proposé à '+safe(p.name)+
    ' : '+safe(ideas[otherActive.category][otherActive.face-1])+'</p>';}
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
 body+='</section><section class="card"><p>Gardienne à prévoir, environ 2 à 6 heures. Réservations et frais restent à organiser entre vous.</p></section>';
 frame(body,'DÉFI SPÉCIAL','L’un ou l’autre brasse, jamais deux résultats différents');
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
async function createCipherKey(passphrase){
 if(!passphrase||passphrase.trim().length<12)throw Error('Utilisez une phrase secrète de 12 caractères ou plus.');
 const base=await crypto.subtle.importKey('raw',enc.encode(passphrase),'PBKDF2',false,['deriveKey']);
 return crypto.subtle.deriveKey({name:'PBKDF2',salt:enc.encode(state.couple.journal_salt),
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
async function decryptJson(ciphertext){
 if(!state.unlockKey)throw Error('Journal verrouillé');
 const sealed=JSON.parse(ciphertext);
 const clear=await crypto.subtle.decrypt({name:'AES-GCM',iv:decode64(sealed.iv)},
  state.unlockKey,decode64(sealed.payload));
 return JSON.parse(dec.decode(clear));
}
function ratingChoice(idName){return '<div class="chipline">'+[1,2,3,4,5].map(v=>
 '<label class="pill"><input type="radio" name="'+idName+'" value="'+v+'" style="width:auto;display:inline;min-height:0;margin:0" '+
 ''+'> '+v+'★</label>').join('')+'</div>';}
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
function renderSettings(){
 frame('<section class="card"><h2>Mon compte</h2><p>'+
  safe(state.profile.display_name)+' · '+safe(state.user.email||'Courriel privé')+'</p>'+
  '<label>Mon nom à afficher<input id="profile-name" value="'+safe(state.profile.display_name)+'" maxlength="80"></label>'+
  button('profile','Mettre à jour mon nom')+'</section>'+
  '<section class="card"><h3>♡ Notre journal commun</h3><p>Calendrier des relations et statistiques partagées, chiffrées.</p>'+
  button('tab:journal','Ouvrir le calendrier et les statistiques')+'<div class="muted-line"></div>'+
  button('tab:feedback','Nos questionnaires de soirées','class="subtle"')+'</section>'+
  '<section class="card"><h3>🔐 Vie privée</h3><p>Les entrées et questionnaires sont chiffrés dans votre navigateur avant envoi au serveur. Les dates et événements de tirage restent des métadonnées partagées.</p>'+
  button('lock','Verrouiller le journal','class="subtle"')+'</section>'+
  '<section class="card"><h3>Invitations et partenaire</h3><p>Votre match : '+
  safe(state.members.map(m=>m.name).join(' ♡ '))+
  '</p><p>Une invitation ne fonctionne que pour le courriel de connexion de votre partenaire.</p></section>'+
  '<section class="card"><h3>Connexion iPhone</h3><p>Sur iPhone, ouvrez le site sécurisé dans Safari puis ajoutez-le à l’écran d’accueil. Les notifications de tirage apparaissent tant que l’application est ouverte.</p></section>',
 'RÉGLAGES','Profil, confidentialité et connexion');
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
 if(verb==='google'||verb==='apple'){
  sessionStorage.setItem('authStarted','1');
  const redirectTo=CONFIG.siteUrl||location.origin+location.pathname;
  const {error}=await client.auth.signInWithOAuth({provider:verb,options:{redirectTo}});
  if(error)throw error;return;
 }
 if(verb==='signout'){
  state.unlockKey=null;
  if(state.channel){await client.removeChannel(state.channel);state.channel=null;}
  const {error}=await client.auth.signOut();if(error)throw error;
  state.user=null;state.profile=null;state.coupleId=null;render();return;
 }
 if(verb==='profile'){
  const newName=id('profile-name')?.value.trim();
  if(!newName||newName.length>80)throw Error('Choisissez votre nom à afficher.');
  await rpc('save_my_profile',{p_name:newName});await reload();return;
 }
 if(verb==='create-couple'){
  await rpc('create_my_couple');await reload();return;
 }
 if(verb==='invite'){
  const email=id('invite-email')?.value.trim().toLowerCase();
  if(!email||!email.includes('@'))throw Error('Saisissez un courriel valide.');
  if(!CONFIG.siteUrl)throw Error('L’adresse publique HTTPS doit être configurée pour envoyer des invitations.');
  const token=randomInviteToken(),digest=await hashToken(token);
  await rpc('create_pair_invite',{p_couple:state.coupleId,p_email:email,p_token_hash:digest});
  const url=new URL(CONFIG.siteUrl);url.hash='invite='+token;
  const text='Je t’invite à rejoindre notre espace Dés à Deux ♡\n\n'+url.toString()+
   '\n\nConnecte-toi avec le même courriel que celui qui reçoit cette invitation.';
  const mail='mailto:'+encodeURIComponent(email)+'?subject='+encodeURIComponent('Invitation Dés à Deux')+
   '&body='+encodeURIComponent(text);
  window.location.href=mail;
  notify('Votre courriel est prêt : confirmez son envoi dans votre application de messagerie.');
  return;
 }
 if(verb==='accept-invite'){
  const token=sessionStorage.getItem('pendingInvite');
  if(!token)throw Error('Invitation introuvable.');
  await rpc('accept_pair_invite',{p_token_hash:await hashToken(token)});
  sessionStorage.removeItem('pendingInvite');await reload();return;
 }
 if(verb==='tab'){state.tab=parts[0];state.confirmCancel=false;render();return;}
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
  const result=await animateRoll(()=>rpc('roll_my_evening_d6',{p_session:state.session.id}));
  notify('Votre D6 : '+result.face);return;
 }
 if(verb==='category'){state.category=parts[0];state.randomMode=false;render();return;}
 if(verb==='random'){state.randomMode=true;render();return;}
 if(verb==='new-challenge'){
  if(!state.session)throw Error('Aucune soirée active.');
  await animateRoll(()=>rpc('draw_for_partner',{p_session:state.session.id,
   p_preference:state.category,p_random:state.randomMode}));
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
  const {error}=await client.from('couple_intimacy').insert({
   couple_id:state.coupleId,author_id:state.user.id,happened_on:date,ciphertext});
  if(error)throw error;
  state.selectedDate=date;await refreshAndShow();notify('Moment ajouté au calendrier commun');return;
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
  if(event==='SIGNED_OUT'){state.user=null;state.profile=null;state.coupleId=null;render();}
 });
}
document.addEventListener('visibilitychange',()=>{
 if(!document.hidden&&state.user&&state.coupleId){
  reload().catch(fail);
 }
});
start().catch(fail);
