// Live, shared LoveRoll Advent calendar. Never reveal unopened challenge contents.
export const ADVENT_ZONE='America/Toronto';
export const torontoDay=(when=new Date())=>
 new Intl.DateTimeFormat('en-CA',{timeZone:ADVENT_ZONE,year:'numeric',month:'2-digit',day:'2-digit'}).format(when);
const escape=s=>String(s??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[ch]));
export function sharedAdventState(openings=[],when=new Date(),year=null){
 const [currentYear,month,day]=torontoDay(when).split('-').map(Number);
 const shown=Number.isInteger(year)?year:currentYear;
 const opened=new Map((openings||[]).filter(x=>x.year===shown&&x.day>=1&&x.day<=25)
  .map(x=>[x.day,x]));
 const days=Array.from({length:25},(_,i)=>{
  const d=i+1,entry=opened.get(d)||null;
  const today=shown===currentYear&&month===12&&day===d;
  const missed=shown<currentYear||(shown===currentYear&&(month>12||(month===12&&d<day)));
  return {day:d,opened:!!entry,entry,today,canOpen:today&&!entry,
   status:entry?'opened':today?'available':missed?'missed':'locked'};
 });
 const note=shown!==currentYear?'Historique de décembre '+shown:
  month<12?'Notre Avent commence le 1er décembre ♥':
  month===12&&day<=25?'Une seule surprise officielle à découvrir aujourd’hui.':
  'Les 25 journées de décembre sont passées. Vos souvenirs restent disponibles ♥';
 return {year:shown,currentYear,month,day,days,note,
  totalOpened:days.filter(d=>d.opened).length,
  canReturnCurrent:shown!==currentYear};
}
function goldHeartLock(n){
 return '<svg viewBox="0 0 56 62" class="advent-gold-lock" aria-hidden="true">'+
 '<defs><linearGradient id="adv-gold-'+n+'" x1="0" y1="0" x2="1" y2="1">'+
 '<stop stop-color="#fff2a7"/><stop offset=".45" stop-color="#ffc947"/>'+
 '<stop offset="1" stop-color="#a96a16"/></linearGradient></defs>'+
 '<path d="M18 25v-8a10 10 0 0 1 20 0v8" stroke="url(#adv-gold-'+n+')" stroke-width="5" fill="none" stroke-linecap="round"/>'+
 '<rect x="12" y="24" width="32" height="30" rx="5" fill="url(#adv-gold-'+n+')" stroke="#ffe6a0" stroke-width="1.4"/>'+
 '<path d="M28 44c-7-5-8-9-4-11 2-1 4 0 4 2 1-4 7-4 8 0 1 4-5 8-8 9z" fill="#8a1245"/>'+
 '<path d="M14 26h27" stroke="#fff8ca" stroke-opacity=".55" stroke-width="2"/></svg>';
}
function openGift(){
 return '<svg viewBox="0 0 56 62" class="advent-open-gift" aria-hidden="true">'+
 '<path d="M12 29h33v27H12z" fill="#c11668" stroke="#ffacd4" stroke-width="2"/>'+
 '<path d="M9 25l26-13 16 13-7 9-24-2z" fill="#ff5e9e" stroke="#ffd2e7" stroke-width="2"/>'+
 '<path d="M28 25v30m-17-20h34" stroke="#ffca60" stroke-width="5"/>'+
 '<path d="M28 14c-15-12-19 5-2 10 14-18 24-9 3-4" fill="none" stroke="#ffcc70" stroke-width="3"/>'+
 '</svg>';
}
const button=(action,label,classes='',disabled=false)=>
 '<button type="button" class="'+classes+'" data-action="'+escape(action)+'"'+(disabled?' disabled':'')+'>'+label+'</button>';
export function adventPremiumHtml({openings=[],members=[],when=new Date(),year=null,selectedDay=null,openingDay=null,showHistory=false,timerHtml=''}={}){
 const cal=sharedAdventState(openings,when,year),opened=cal.days.find(x=>x.day===selectedDay)?.entry;
 const who=id=>members.find(m=>m.user_id===id)?.name?.split(' ')[0]||'Votre partenaire';
 const grid=cal.days.map(d=>{
  const act=d.opened?'advent-view:'+d.day:d.canOpen?'advent-open:'+d.day:'advent-locked:'+d.day;
  const locked=d.status==='locked'||d.status==='missed';
  const accessible=d.opened?'Consulter la surprise du '+d.day+' décembre':
   d.canOpen?'Ouvrir la surprise du '+d.day+' décembre':
   d.status==='missed'?'Jour '+d.day+' manqué · sans rattrapage':'Case '+d.day+' à venir';
  return '<button class="advent-box '+d.status+(openingDay===d.day?' opening':'')+
   '" type="button" data-action="'+act+'" aria-label="'+escape(accessible)+'"'+
   (locked?' disabled aria-disabled="true"':'')+'>'+
   (d.canOpen?'<span class="advent-today-tag">Aujourd’hui</span>':'')+
   '<span class="advent-box-shimmer" aria-hidden="true"></span>'+
   '<span class="advent-box-icon">'+(d.opened||d.canOpen?openGift():goldHeartLock(d.day))+'</span>'+
   '<span class="advent-box-number">'+d.day+'</span>'+
   (d.opened?'<span class="advent-opened-mark">✓ Ouvert</span>':
   d.status==='missed'?'<span class="advent-state-label">Jour manqué</span>':
   d.canOpen?'<span class="advent-state-label">À ouvrir</span>':'')+
   '</button>';
 }).join('');
 const historyYears=[...new Set(openings.map(x=>x.year).filter(y=>Number.isInteger(y)&&y!==cal.currentYear))].sort((a,b)=>b-a);
 return '<div class="advent-premium-view">'+
 '<header class="advent-top"><span class="advent-top-heart" aria-hidden="true">♡</span>'+
  '<div><h1>CALENDRIER DE<br>L’AVENT</h1><p>Votre rituel commun en décembre</p></div></header>'+
 '<div class="advent-intro"><div class="advent-overline">🎁 &nbsp; NOTRE AVENT</div>'+
 '<h2><em>25</em> moments à découvrir</h2>'+
 '<p>Chaque jour de décembre, une nouvelle surprise<br>rien que pour nous.</p></div>'+
 '<section class="advent-shell" aria-label="Les 25 jours de décembre '+cal.year+'">'+
 '<div class="advent-shell-head"><h3>Ouvrons une surprise à la fois · '+cal.totalOpened+' / 25</h3>'+
 '<p>Disponible aujourd’hui, ouverte ou manquée : chaque case ne peut être ouverte que le jour exact, une seule fois pour le couple.</p></div>'+
 '<div class="advent-tiles">'+grid+'</div>'+
 '<div class="advent-shell-bottom">'+
 '<span>'+escape(cal.note)+'</span>'+
 (historyYears.length||cal.canReturnCurrent?
  button('advent-history','Années ▾','advent-history-trigger'): '')+'</div>'+
 '</section>'+
 (showHistory?'<div class="advent-dialog-shade" role="dialog" aria-modal="true" aria-label="Historique des calendriers">'+
  '<article class="advent-dialog"><h2>♡ Nos calendriers</h2>'+
  '<p>Chaque année conserve ses propres cases déjà ouvertes.</p>'+
  button('advent-year:'+cal.currentYear,'Décembre '+cal.currentYear,'advent-dialog-action')+
  historyYears.map(y=>button('advent-year:'+y,'Décembre '+y,'advent-dialog-action')).join('')+
  button('advent-history','Fermer','advent-dialog-close')+'</article></div>':'')+
 (opened?'<div class="advent-dialog-shade" role="dialog" aria-modal="true" aria-label="Surprise du '+selectedDay+' décembre">'+
   '<article class="advent-dialog advent-reveal">'+
   '<div class="advent-reveal-heart" aria-hidden="true">♡</div>'+
   '<div class="advent-reveal-heading">♥ SURPRISE DU '+selectedDay+' DÉCEMBRE '+cal.year+'</div>'+
   '<p class="advent-opened-by">Ouverte par '+escape(who(opened.opened_by))+
   ' · '+escape(new Intl.DateTimeFormat('fr-CA',{timeZone:ADVENT_ZONE,dateStyle:'medium',timeStyle:'short'}).format(new Date(opened.opened_at)))+'</p>'+
   '<h2>'+escape(opened.advent_title||'Notre surprise du jour')+'</h2>'+
   (opened.advent_type?'<div class="advent-reveal-type">'+escape(opened.advent_type)+'</div>':'')+
   '<p class="advent-description">'+escape(opened.advent_description||'Un moment à partager ensemble, à votre rythme.')+'</p>'+
   (opened.advent_timer_minutes?timerHtml:'')+
   button('advent-close','Fermer','advent-dialog-action')+
   '</article></div>':'')+
 '</div>';
}
