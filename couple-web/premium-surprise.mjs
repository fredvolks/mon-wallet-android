// LoveRoll — Rendez-vous Surprise, connected to real monthly and occasion draws.
import {occasionDate} from './special-occasions.mjs';
import {premiumPolyDie} from './premium-evening.mjs';
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[c]));
const button=(a,s,cls='',disabled=false)=>'<button type="button" class="'+cls+'" data-action="'+esc(a)+'"'+(disabled?' disabled':'')+'>'+s+'</button>';
export const SIX_SPECIAL_KEYS=['anniversary','birthday','birthday','valentine','mothers_day','fathers_day'];
export function sixSpecialEvents(now,settings={},members=[]){
 const day=new Intl.DateTimeFormat('en-CA',{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'America/Toronto'}).format(now);
 const year=Number(day.slice(0,4));
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
  const upcoming=possible.find(x=>x.date>=day)||null;
  const days=upcoming?Math.max(0,Math.round((Date.parse(upcoming.date+'T12:00:00Z')-Date.parse(day+'T12:00:00Z'))/86400000)):null;
  return {...entry,date:upcoming?.date||null,daysLeft:days,year:upcoming?.year||null};
 });
}
export function premiumSurpriseHtml({monthly={},members=[],settings={},now=new Date(),activity=null,rolling=false,rollingKind=null,rollFace=1,showActivity=false}={}){
 const a=monthly.monthly_activity,h=monthly.monthly_host,r=monthly.monthly_restaurant;
 const rollingActivity=rolling&&rollingKind==='monthly_activity';
 const rollingHost=rolling&&rollingKind==='monthly_host';
 const host=h?members[h.face<=3?0:1]:null;
 const cards=sixSpecialEvents(now,settings,members);
 const drawText=a?'Voir notre activité':'Brasser le dé 20 faces';
 return '<div class="surprise-premium-view">'+
 '<header class="surprise-header"><div><h1>♡ RENDEZ-VOUS<br><em>SURPRISE</em></h1>'+
 '<p>Un mois. Un tirage. Une expérience à deux.</p></div>'+
 '<div class="surprise-banner" aria-hidden="true">♥ ✧</div></header>'+
 '<section class="surprise-month"><div class="surprise-headline"><strong>▦ &nbsp; ACTIVITÉ DU MOIS</strong><span>20 idées uniques</span></div>'+
 '<div class="surprise-month-title">'+(a?'<strong>♡ '+esc(activity?.title||'Notre rendez-vous')+'</strong>'+
  '<small>Activité n° '+esc(a.face)+' · déjà tirée ce mois</small>':
  '<strong>Quel sera notre prochain rendez-vous ?</strong>'+
  '<small>Brasse le dé 20 faces pour découvrir l’activité du mois.</small>')+'</div>'+
 '<div class="surprise-d20-scene"><span class="surprise-scene-heart">♡</span>'+
  premiumPolyDie(20,rollingActivity?rollFace:a?.face||null,'Dé 20 faces, activité du mois',rollingActivity)+'</div>'+
 (a?button('surprise-activity','♡ Voir notre activité','surprise-primary'):
  button('roll:monthly_activity','🎲 '+drawText,'surprise-primary',rolling))+'</section>'+
 '<section class="surprise-host"><div class="surprise-headline"><strong>🎲 QUI PRÉPARE L’ADDITION ?</strong></div>'+
 '<p>Le dé décide qui organise et règle notre rendez-vous.</p>'+
 '<div class="surprise-host-grid"><div class="surprise-host-partner"><b>1 · 2 · 3</b>'+
  '<span>♡ '+esc(members[0]?.name?.split(' ')[0]||'Partenaire 1')+'</span></div>'+
  premiumPolyDie(6,rollingHost?rollFace:h?.face||null,'D6 qui prépare notre rendez-vous',rollingHost)+
  '<div class="surprise-host-partner"><b>4 · 5 · 6</b>'+
  '<span>♡ '+esc(members[1]?.name?.split(' ')[0]||'Partenaire 2')+'</span></div></div>'+
 (h?'<div class="surprise-winner">♥ '+esc(host?.name?.split(' ')[0]||'Partenaire')+' prépare notre rendez-vous !</div>':
  button('roll:monthly_host','🎲 Brasser le dé 6 faces','surprise-primary',!a||rolling))+
 (r?'<small class="surprise-restaurant">Restaurant : '+(r.face<=3?'un endroit connu':'une nouvelle adresse')+'</small>':
  (h&&activity?.is_restaurant?button('roll:monthly_restaurant','🎲 Restaurant connu ou nouveau ?','surprise-restaurant-button',rolling):''))+
 '</section>'+
 '<section class="surprise-special"><div class="surprise-headline"><strong>🎁 ÉVÉNEMENTS SPÉCIAUX</strong><span>6 événements · 6 choix chacun</span></div>'+
 '<p>Des moments uniques à vivre ensemble.</p>'+
 '<div class="surprise-special-grid">'+cards.map((e,i)=>
  button('open-occasion:'+e.key+':'+(e.owner||'-'),
   '<span class="surprise-event-symbol" aria-hidden="true">'+e.emoji+'</span>'+
   '<span class="surprise-event-title">'+esc(e.title)+'</span>'+
   '<span class="surprise-event-days">'+(e.daysLeft===null?'Configurer la date':e.daysLeft===0?'Aujourd’hui ♥':e.daysLeft+' jours')+'</span>'+
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
