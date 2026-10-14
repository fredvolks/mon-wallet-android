// Real shared-couple home UI. Only server-sourced weekly rolls and session cards
// are shown here; the weights are labels matching roll_couple_once() on Supabase.
export const WEEKLY_ODDS=Object.freeze([50,25,12,8,4,1]);
export const PIP_POSITIONS=Object.freeze({
  1:[[50,50]],2:[[29,29],[71,71]],3:[[29,29],[50,50],[71,71]],
  4:[[29,29],[71,29],[29,71],[71,71]],
  5:[[29,29],[71,29],[50,50],[29,71],[71,71]],
  6:[[29,22],[71,22],[29,50],[71,50],[29,78],[71,78]]
});
const escape=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[c]));
export function pipFace(n){
  if(!Number.isInteger(n)||n<1||n>6)throw new Error('Face de dé inconnue');
  return '<svg class="home-pip-svg" viewBox="0 0 100 100" role="img" aria-label="Dé face '+n+'">'+
    '<rect x="3" y="3" width="94" height="94" rx="18" fill="#ffecf4" stroke="#f7a5c5" stroke-width="4"/>'+
    PIP_POSITIONS[n].map(([x,y])=>'<circle cx="'+x+'" cy="'+y+'" r="10.6" fill="#271527"/>').join('')+'</svg>';
}
const inToronto=date=>new Intl.DateTimeFormat('en-CA',{
 timeZone:'America/Toronto',year:'numeric',month:'2-digit',day:'2-digit'
}).format(date);
export function nextMonday(now=new Date()){
 const current=inToronto(now);
 const anchor=new Date(current+'T12:00:00Z'),day=(anchor.getUTCDay()+6)%7;
 anchor.setUTCDate(anchor.getUTCDate()+7-day);
 return anchor.toISOString().slice(0,10);
}
export function nextMonthly(now=new Date()){
 const day=inToronto(now),[y,m]=day.slice(0,7).split('-').map(Number);
 const first=new Date(Date.UTC(y,m,1));
 const diff=Math.max(0,Math.round((first-new Date(day+'T00:00:00Z'))/86400000));
 return {
   date:first.toISOString().slice(0,10),
   days:diff,
   label:new Intl.DateTimeFormat('fr-CA',{day:'numeric',month:'long',year:'numeric',timeZone:'UTC'}).format(first)
 };
}
export function assignedTonight(challenges,members){
 const present=Array.isArray(challenges)?challenges.filter(c=>c&&c.recipient&&
   !['cancelled','deleted'].includes(c.status)):[];
 const [first,second]=members||[];
 const forFirst=present.filter(c=>c.recipient===first?.user_id).length;
 const forSecond=present.filter(c=>c.recipient===second?.user_id).length;
 return {total:forFirst+forSecond,forFirst,forSecond};
}

const iconSvg=(kind)=>{
 const paths={
  games:'<g transform="translate(7 6) rotate(-12 15 17)"><rect x="2" y="6" width="31" height="31" rx="7" fill="#240919" stroke="#ff82bd" stroke-width="2.4"/><circle cx="11" cy="15" r="3" fill="#ff60ac"/><circle cx="25" cy="28" r="3" fill="#ff60ac"/></g><g transform="translate(10 8) rotate(13 30 29)"><rect x="18" y="18" width="30" height="30" rx="7" fill="#110712" stroke="#ff8dbc" stroke-width="2.4"/><path d="M33 27c-5-7-13 0 0 11 13-11 5-18 0-11z" fill="#ff5ba3"/></g>',
  challenges:'<path d="M27 48C7 32 3 22 9 14c7-9 18-5 21 1 7-12 25-8 25 5 0 12-13 22-25 32z" fill="#f52b8444" stroke="#ff78bc" stroke-width="3.5"/><path d="M17 14Q24 9 29 21" stroke="#ffdaee" stroke-width="2.3" fill="none"/>',
  dates:'<rect x="9" y="11" width="44" height="43" rx="6" fill="#26091c" stroke="#ff78bc" stroke-width="3"/><path d="M10 23H52M21 5V17M41 5V17" stroke="#ff7dba" stroke-width="4" stroke-linecap="round"/><path d="M31 37c-6-9-15 0 0 11 15-11 6-20 0-11z" fill="#ff5f9e"/>',
  surprises:'<rect x="9" y="27" width="45" height="27" rx="4" fill="#28071a" stroke="#ff82bb" stroke-width="3"/><rect x="6" y="20" width="50" height="11" rx="3" fill="#5a1139" stroke="#ff89c1" stroke-width="3"/><path d="M32 20V54" stroke="#ff459d" stroke-width="8"/><path d="M31 20c-18-2-19-20-8-17 7 1 8 11 8 17zm2 0c18-2 19-20 8-17-7 1-8 11-8 17z" fill="#fa58a4" stroke="#ffa8d4" stroke-width="2"/>'
 };
 return '<svg viewBox="0 0 62 62" aria-hidden="true" focusable="false">'+paths[kind]+'</svg>';
};

export function premiumHomeHtml({weekly=null,rolling=false,disabled=false,members=[],
  challenges=[],events=[],now=new Date()}={}){
 const weights=WEEKLY_ODDS.map((pct,i)=>
  '<span class="home-weight" title="'+(i+1)+' soirée(s) : '+pct+' %">'+pipFace(i+1)+
  '<b>'+(i+1)+'x</b><span>'+pct+'%</span></span>').join('');
 const stats=assignedTonight(challenges,members),m=nextMonthly(now);
 const ready=weekly&&Number.isInteger(weekly.face),next=nextMonday(now);
 const memberA=escape(members[0]?.name||'Partenaire 1');
 const memberB=escape(members[1]?.name||'Partenaire 2');
 const weeklyTitle=ready?weekly.face+' soirée(s) de défi':'Prêts pour votre prochain rituel ?';
 const weeklySubtitle=ready?'cette semaine':'Brassez votre dé hebdomadaire';
 const shortcut=(ico,title,subtitle,target,cls='')=>
  '<button class="home-shortcut '+cls+'" type="button" data-action="tab:'+target+'">'+
    '<span class="home-shortcut-icon" aria-hidden="true">'+ico+'</span>'+
    '<span class="home-shortcut-name">'+title+'</span>'+
    '<small>'+subtitle+'</small></button>';
 const pending=rolling||disabled;
 return '<section class="real-home-shell" aria-label="Tableau de bord du couple">'+
   '<div class="home-ritual"><span>NOTRE RITUEL DE LA SEMAINE</span>'+
   '<h2>'+escape(weeklyTitle)+'</h2><p>'+weeklySubtitle+'</p></div>'+
   '<div class="home-weights" aria-label="Pondération des six faces du dé">'+weights+'</div>'+
   '<div class="home-die-theater" aria-label="Dé à six faces en trois dimensions">'+
   '<div class="home-fabric-photo" aria-hidden="true"></div>'+
   '<div class="home-satin-orbit" aria-hidden="true"></div>'+
   '<div class="home-die-platform"><strong class="dice-fallback">'+(ready?weekly.face:'♡')+'</strong>'+
   '<canvas class="dice-canvas" width="300" height="300" data-dice-sides="6" '+
   'data-face="'+(ready?weekly.face:1)+'" data-spinning="'+(rolling?'true':'false')+'" '+
   'aria-label="Dé 3D, '+(ready?'face '+weekly.face:'en attente du tirage')+'"></canvas></div></div>'+
   '<div class="home-roll-area">'+
   (ready?
    '<button class="home-roll-button is-used" type="button" disabled>♡ Dé déjà brassé cette semaine</button>':
    '<button class="home-roll-button" type="button" data-action="roll:weekly" '+(pending?'disabled':'')+'>'+
    (rolling?'Brassage 3D en cours…':'🎲 BRASSER LE DÉ')+'</button>')+
   '<p>Info : '+(ready?'Par '+escape(members.find(p=>p.user_id===weekly.actor)?.name||'un partenaire')+'. ':
   'Brassage disponible 1x par semaine et par couple. ')+
   'Prochain tirage : lundi '+escape(next.slice(8,10)+'/'+next.slice(5,7))+' à 00 h (Québec)</p>'+
   '</div>'+
   '<div class="home-challenge-stats">'+
   '<div><span>🎲</span><strong>'+stats.total+'</strong><small>Défis ce soir<br>pour vous deux</small></div>'+
   '<div><span>♡</span><strong>'+stats.forFirst+'</strong><small>Défis pour '+memberA+'<br>ce soir</small></div>'+
   '<div><span>♡</span><strong>'+stats.forSecond+'</strong><small>Défis pour '+memberB+'<br>ce soir</small></div></div>'+
   '<button class="home-next-month" data-action="tab:special" type="button">'+
   '<span class="home-calendar-icon" aria-hidden="true">'+iconSvg('dates')+'</span>'+ 
   '<span><small>Prochain défi mensuel</small><strong>'+m.days+' jours</strong>'+
   '<em>'+escape(m.label)+'</em></span>'+
   '<span class="home-gift-icon" aria-hidden="true">'+iconSvg('surprises')+'</span></button>'+ 
   '<div class="home-shortcuts" aria-label="Raccourcis">'+
   shortcut(iconSvg('games'),'Jeux','Jouez ensemble','session','games')+
   shortcut(iconSvg('challenges'),'Défis','Sensuels ou coquins','session','challenges')+
   shortcut(iconSvg('dates'),'Dates','Votre rendez-vous','special','dates')+
   shortcut(iconSvg('surprises'),'Surprises','Fêtes et attentions','occasions','surprises')+
   '</div></section>';
}
