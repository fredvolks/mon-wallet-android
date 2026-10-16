// LoveRoll evening archive. Private challenge titles are rendered ONLY from
// the authenticated user's own RLS-filtered challenge rows.
import {resolvedCard} from './evening-challenges.mjs';
const safe=x=>String(x??'').replace(/[&<>"']/g,k=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[k]));
const isoMonth=v=>/^\d{4}-(0[1-9]|1[0-2])$/.test(v||'')?v:null;
export function eveningLocalDay(iso){
 if(!iso||!Number.isFinite(Date.parse(iso)))return null;
 const parts=new Intl.DateTimeFormat('en-US',{timeZone:'America/Toronto',
  year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(iso));
 const get=t=>parts.find(p=>p.type===t)?.value;
 return get('year')+'-'+get('month')+'-'+get('day');
}
const statusLabel=s=>({
 completed:'✓ Réalisé',accepted:'◷ Commencé, non réalisé',
 proposed:'○ À faire, non réalisé',passed:'↷ Passé, non réalisé'
})[s]||'○ Non réalisé';
const monthLabel=mo=>new Intl.DateTimeFormat('fr-CA',{month:'long',year:'numeric',timeZone:'UTC'})
 .format(new Date(mo+'-15T12:00:00Z'));
export function eveningHistoryMarkup({sessions=[],members=[],stats=[],ownChallenges=[],me,month,selectedDate}={}){
 const current=isoMonth(month)||'2026-10';
 const [year,m]=current.split('-').map(Number);
 const validDate=selectedDate&&/^\d{4}-\d\d-\d\d$/.test(selectedDate)?selectedDate:current+'-01';
 const firstDay=new Date(Date.UTC(year,m-1,1)).getUTCDay(),padding=(firstDay+6)%7;
 const days=new Date(Date.UTC(year,m,0)).getUTCDate();
 const archives=(sessions||[]).filter(s=>!!s.started_at).map(s=>({...s,localDay:eveningLocalDay(s.started_at)}));
 const forDay=archives.filter(s=>s.localDay===validDate).sort((a,b)=>Date.parse(b.started_at)-Date.parse(a.started_at));
 const history=archives.filter(s=>s.finished_at).length;
 const done=stats.filter(r=>archives.some(s=>s.id===r.session_id&&!!s.finished_at))
  .reduce((n,r)=>n+Number(r.completed||0),0);
 const incomplete=stats.filter(r=>archives.some(s=>s.id===r.session_id&&!!s.finished_at))
  .reduce((n,r)=>n+Math.max(0,Number(r.target==null?r.incomplete:(r.target-r.completed))||0),0);
 const byDay=new Map();
 for(const row of archives){byDay.set(row.localDay,(byDay.get(row.localDay)||0)+1);}
 let cells=Array.from({length:padding},()=>'<span class="evening-cal-blank"></span>').join('');
 for(let d=1;d<=days;d++){
  const date=current+'-'+String(d).padStart(2,'0'),has=byDay.has(date);
  cells+='<button type="button" class="evening-cal-day'+(has?' has-evening':'')+
   (date===validDate?' selected':'')+'" data-action="evening-date:'+date+
   '" aria-label="'+safe(date)+(has?' : '+byDay.get(date)+' soirée(s)':' : aucune soirée')+
   '" aria-pressed="'+(date===validDate)+'"><span>'+d+'</span>'+
   (has?'<span class="evening-cal-dot" aria-hidden="true">♥</span>':'')+'</button>';
 }
 const content=forDay.length?forDay.map((s,i)=>{
  const rolls=stats.filter(r=>r.session_id===s.id);
  const own=ownChallenges.filter(c=>c.session_id===s.id&&c.recipient===me)
   .sort((a,b)=>Date.parse(a.created_at||'')-Date.parse(b.created_at||''));
  const people=members.map(m=>{
   const r=rolls.find(row=>row.user_id===m.user_id);
   if(!r)return '<div class="evening-cal-person"><strong>'+safe(m.name?.split(' ')[0]||'Partenaire')+
    '</strong><span>Données indisponibles</span></div>';
   const completed=Number(r.completed)||0;
   const outstanding=r.target==null?Number(r.incomplete)||0:Math.max(0,Number(r.target)-completed);
   return '<div class="evening-cal-person"><strong>'+safe(m.name?.split(' ')[0]||'Partenaire')+
    (m.user_id===me?' (moi)':'')+'</strong><span><b>'+completed+'</b> réalisé'+(completed>1?'s':'')+
    ' · <b>'+outstanding+'</b> non réalisé'+(outstanding>1?'s':'')+
    (r.target!=null?' · '+Number(r.target)+' prévu'+(Number(r.target)>1?'s':''):'')+
    '</span></div>';
  }).join('');
  const ownItems=own.map(c=>{
   const card=resolvedCard(c);
   return '<li class="evening-cal-challenge '+(c.status==='completed'?'is-done':'is-open')+'">'+
    '<span>'+safe(statusLabel(c.status))+'</span><strong>'+safe(card.title)+'</strong>'+
    '<small>'+safe(c.category==='SEXUEL'?'Sexuel':'Sensuel')+'</small></li>';
  }).join('');
  return '<article class="evening-cal-session"><header><strong>♡ Soirée '+(forDay.length>1?(i+1):'')+
   '</strong><span>'+(s.finished_at?'Clôturée':'En cours')+'</span></header>'+
   '<div class="evening-cal-people">'+people+'</div>'+
   '<h3>Mes défis pigés</h3>'+
   (own.length?'<ul>'+ownItems+'</ul>':'<p>Aucun défi pigé sur ce compte pendant cette soirée.</p>')+
   '<p class="evening-cal-privacy">Les cartes de ton partenaire restent privées; son bilan est partagé.</p>'+
   '</article>';
 }).join(''):'<p class="evening-cal-empty">Aucune soirée enregistrée pour cette journée.</p>';
 return '<div class="evening-calendar-shell">'+
  '<div class="evening-cal-head"><span>♡ NOS SOIRÉES</span><h2>Notre calendrier</h2>'+
   '<p>Toutes les soirées, avec les défis réalisés et non réalisés.</p></div>'+
  '<div class="evening-cal-stats"><span><strong>'+history+'</strong> soirées clôturées</span>'+
   '<span><strong>'+done+'</strong> défis réalisés</span>'+
   '<span><strong>'+incomplete+'</strong> non réalisés</span></div>'+
  '<section class="evening-cal-month"><div class="evening-cal-toolbar">'+
   '<button data-action="evening-month-prev" aria-label="Mois précédent">‹</button>'+
   '<h3>'+safe(monthLabel(current))+'</h3>'+
   '<button data-action="evening-month-next" aria-label="Mois suivant">›</button></div>'+
   '<div class="evening-cal-grid">'+['L','M','M','J','V','S','D'].map((v,i)=>
    '<span class="evening-cal-weekday" aria-label="'+['Lundi','Mardi','Mercredi','Jeudi','Vendredi','Samedi','Dimanche'][i]+'">'+v+'</span>').join('')+
    cells+'</div></section>'+
  '<section class="evening-cal-history"><h3>'+safe(validDate)+' · Historique</h3>'+content+'</section>'+
  '<button type="button" class="evening-cal-return" data-action="tab:session">← Retour à Soirée</button>'+
 '</div>';
}
