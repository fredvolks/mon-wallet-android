// LoveRoll — Rendez-vous Surprise, connected to real monthly and occasion draws.
import {occasionDate,dueOccasions} from './special-occasions.mjs';
import {premiumPolyDie} from './premium-evening.mjs';
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[c]));
const button=(a,s,cls='',disabled=false)=>'<button type="button" class="'+cls+'" data-action="'+esc(a)+'"'+(disabled?' disabled':'')+'>'+s+'</button>';
const eventSymbol=(key)=>{
 const paths={
  anniversary:'<path d="M21 17c-10-10-22 0-12 13L31 51 48 33c13-14 0-26-10-16" fill="none" stroke="#ff7cbb" stroke-width="4"/><path d="M33 22c-7-11-20-5-18 7 1 7 11 15 18 20 6-5 17-13 18-20 2-12-11-18-18-7z" fill="#ff2f8361" stroke="#ffa4cf" stroke-width="2"/>',
  birthday:'<rect x="12" y="27" width="40" height="23" rx="6" fill="#4c1338" stroke="#ff9bc7" stroke-width="3"/><path d="M12 35q7 8 13 0t13 0t14 0" fill="none" stroke="#ff6ea9" stroke-width="2"/><path d="M20 27v-9m12 9v-9m12 9v-9" stroke="#ffd0e7" stroke-width="3"/><path d="M20 12q-5 5 0 7 5-3 0-7m12 0q-5 5 0 7 5-3 0-7m12 0q-5 5 0 7 5-3 0-7" fill="#ff459b" stroke="#ff9acb"/>',
  valentine:'<path d="M29 50c-23-14-23-29-13-33 7-3 12 1 15 7 7-12 24-11 24 3 0 11-13 18-26 23Z" fill="#ad0c50" stroke="#ff9cc8" stroke-width="2"/><path d="M22 19c3-5 10-5 12 2" fill="none" stroke="#ffd0e6" stroke-width="2"/><path d="M13 52c8-3 11-9 15-19" stroke="#ff5c9f" stroke-width="2"/>',
  mothers_day:'<path d="M32 30c-19-26-31-7-12 5-17-7-21 17-1 13-4 11 16 22 18 2 15 9 24-7 9-14 15-9 3-25-10-6Z" fill="#bc145f" stroke="#ff8ec5" stroke-width="2"/><circle cx="32" cy="38" r="6" fill="#ffd2e6"/><path d="M32 44v17m-1-7Q12 42 15 55" fill="none" stroke="#ff7fac" stroke-width="3"/>',
  fathers_day:'<path d="M30 30L9 19v26l21-9 4 0 21 9V19L34 30Z" fill="#9e0f51" stroke="#ffa0ca" stroke-width="3"/><rect x="27" y="28" width="10" height="11" rx="3" fill="#ff57a0" stroke="#ffd1e6" stroke-width="2"/>'
 };
 return '<svg viewBox="0 0 64 64" class="surprise-event-svg" aria-hidden="true" focusable="false">'+paths[key]+'</svg>';
};

export const SIX_SPECIAL_KEYS=['anniversary','birthday','birthday','valentine','mothers_day','fathers_day'];
export function sixSpecialEvents(now,settings={},members=[]){
 const day=new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'America/Toronto'}).format(now);
 const year=Number(day.slice(0,4));
 const available=dueOccasions(day,settings,members);
 const defs=[
  {key:'anniversary',title:'Anniversaire couple',emoji:'♡♡',owner:null},
  ...members.slice(0,2).map((m,i)=>({key:'birthday',title:'Anniversaire '+(m.name?.split(' ')[0]||'Partenaire '+(i+1)),emoji:'♕',owner:m.user_id})),
  {key:'valentine',title:'Saint-Valentin',emoji:'♥',owner:null},
  {key:'mothers_day',title:'Fête des Mères',emoji:'✿',owner:null},
  {key:'fathers_day',title:'Fête des Pères',emoji:'♧',owner:null}
 ];
 return defs.map(entry=>{
  const possible=[year,year+1,year+2].map(y=>({date:occasionDate(entry.key,y,settings,entry.owner),year:y}))
   .filter(x=>x.date);
  const active=available.find(x=>x.key===entry.key&&x.actorId===entry.owner);
  const upcoming=possible.find(x=>x.date>=day)||null;
  const selected=active||upcoming;
  const days=selected?.daysLeft??(selected?Math.round(
    (Date.parse(selected.date+'T12:00:00Z')-Date.parse(day+'T12:00:00Z'))/86400000):null);
  const availability=active?(days<0?'Disponible encore '+(7+days)+' jour'+(7+days===1?'':'s'):
    days===0?'Disponible aujourd’hui':'Disponible · dans '+days+' jour'+(days===1?'':'s')):null;
  return {...entry,date:selected?.date||null,daysLeft:days,year:selected?.year||null,availability};
 });
}
export function premiumSurpriseHtml({monthly={},members=[],settings={},now=new Date(),activity=null,rolling=false,rollingKind=null,rollFace=1,showActivity=false}={}){
 const a=monthly.monthly_activity,h=monthly.monthly_host,r=monthly.monthly_restaurant;
 const rollingActivity=rolling&&rollingKind==='monthly_activity';
 const rollingHost=rolling&&rollingKind==='monthly_host';
 const host=h?members[h.face<=3?0:1]:null;
 const cards=sixSpecialEvents(now,settings,members);
 const drawText=a?'Voir notre activité':'Brasser le dé';
 return '<div class="surprise-premium-view">'+
 '<header class="surprise-header"><div><h1>♡ RENDEZ-VOUS<br><em>SURPRISE</em></h1>'+
 '<p>Un mois. Un tirage. Une expérience à deux.</p></div>'+
 '<div class="surprise-banner" aria-hidden="true">♥ ✧</div></header>'+
 '<section class="surprise-month"><div class="surprise-headline"><strong>▦ &nbsp; ACTIVITÉ DU MOIS</strong><span>20 idées uniques</span></div>'+
 '<div class="surprise-month-title">'+(a?'<strong>♡ '+esc(activity?.title||'Notre rendez-vous')+'</strong>'+
  '<small>Activité n° '+esc(a.face)+' · déjà tirée ce mois</small>':
  '<strong>Quel sera notre prochain rendez-vous ?</strong>'+
  '<small>Brasse le dé pour découvrir une des 20 activités du mois.</small>')+'</div>'+
 '<div class="surprise-d20-scene"><span class="surprise-scene-heart">♡</span>'+
  premiumPolyDie(20,rollingActivity?rollFace:a?.face||null,'Dé 20 faces, activité du mois',rollingActivity)+'</div>'+
 (a?button('surprise-activity','♡ Voir notre activité','surprise-primary'):
  button('roll:monthly_activity',drawText,'surprise-primary',rolling))+'</section>'+
 '<section class="surprise-host"><div class="surprise-headline"><strong>♡ QUI INVITE ?</strong></div>'+
 '<p>Le dé choisit qui organise le rendez-vous et paie l’addition.</p>'+
 '<div class="surprise-host-grid" aria-label="Répartition du tirage de l’addition"><div class="surprise-host-partner"><b>1 · 2 · 3</b>'+
  '<span>♡ '+esc(members[0]?.name?.split(' ')[0]||'Partenaire 1')+'</span></div>'+
  '<div class="surprise-host-die">'+premiumPolyDie(6,rollingHost?rollFace:h?.face||null,'Dé 6 faces pour l’addition',rollingHost)+'</div>'+
  '<div class="surprise-host-partner"><b>4 · 5 · 6</b>'+
  '<span>♡ '+esc(members[1]?.name?.split(' ')[0]||'Partenaire 2')+'</span></div></div>'+
 (h?'<div class="surprise-winner">♥ '+esc(host?.name?.split(' ')[0]||'Partenaire')+' prépare notre rendez-vous !</div>':
  button('roll:monthly_host','Brasser le dé','surprise-primary',!a||rolling)+
 (!a?'<small>Commencez par tirer votre activité du mois.</small>':''))+
 (r?'<small class="surprise-restaurant">Restaurant : '+(r.face<=3?'un endroit connu':'une nouvelle adresse')+'</small>':
  (h&&activity?.is_restaurant?button('roll:monthly_restaurant','🎲 Restaurant connu ou nouveau ?','surprise-restaurant-button',rolling):''))+
 '</section>'+
 '<section class="surprise-special"><div class="surprise-headline"><strong>🎁 ÉVÉNEMENTS SPÉCIAUX</strong><span>6 événements · 6 choix chacun</span></div>'+
 '<p>Des moments uniques à vivre ensemble.</p>'+
 '<div class="surprise-special-grid">'+cards.map((e,i)=>
  button('open-occasion:'+e.key+':'+(e.owner||'-'),
   '<span class="surprise-event-symbol" aria-hidden="true">'+eventSymbol(e.key)+'</span>'+
   '<span class="surprise-event-title">'+esc(e.title)+'</span>'+
   '<span class="surprise-event-days">'+(e.daysLeft===null?'Configurer la date':e.availability||e.daysLeft+' jours')+'</span>'+
   '<span class="surprise-event-chevron" aria-hidden="true">›</span>',
   'surprise-event-card '+(i===0?'couple':'')+
    (e.daysLeft===null?' no-date':''))).join('')+'</div>'+
 '<div class="surprise-bottom-info">🎲 &nbsp; Chaque événement comporte 6 activités et 6 variantes.'+
  button('surprise-how','›','surprise-info-button')+'</div></section>'+
 (showActivity&&a?'<div class="surprise-modal" role="dialog" aria-label="Notre activité">'+
  '<article class="surprise-modal-content"><strong>♡ ACTIVITÉ DU MOIS · '+esc(a.face)+'</strong>'+
  '<h2>'+esc(activity?.title||'Notre sortie')+'</h2><p>'+esc(activity?.description||'')+'</p>'+
  '<p>Le résultat est commun aux deux comptes.</p>'+
  button('surprise-activity','Fermer','surprise-primary')+'</article></div>':'')+
 '</div>';
}
