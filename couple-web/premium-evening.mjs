// LoveRoll connected couple — production UI, not a demo. All results are provided by Supabase.
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[c]));
const bt=(action,text,cls='',disabled=false)=>'<button type="button" class="'+cls+'" data-action="'+esc(action)+'"'+(disabled?' disabled':'')+'>'+text+'</button>';
export function premiumPolyDie(sides,face=null,caption='',spinning=false){
 const valid=Number.isInteger(face)&&face>=1&&face<=sides;
 const target=valid?face:1;
 return '<div class="love3d love3d-'+sides+'" role="group" aria-label="'+esc(caption)+'">'+
  '<div class="love3d-stage '+(spinning?'is-rolling':'')+'">'+
   '<strong class="dice-fallback"'+(spinning?' aria-hidden="true"':'')+'>'+(spinning?'':valid?esc(face):'♡')+'</strong>'+
   '<canvas class="dice-canvas" width="250" height="250" data-dice-sides="'+sides+
   '" data-face="'+target+'" data-dice-zoom="'+(sides===20?'1.30':sides===35?'1.07':'1')+'" data-dice-markers="'+(sides===6?'pips':'numbers')+'" data-spinning="'+(spinning?'true':'false')+
   '" aria-label="'+(spinning?'Dé en cours de brassage':valid?'Dé arrêté, résultat '+face:'Dé en attente')+'"></canvas>'+
  '</div></div>';
}
const d6=(m,roll,localUser,canRoll,ready,rolling,rollFace)=>{
 const has=!!roll&&ready;
 return '<article class="evening-user-die">'+
  '<strong class="evening-person">'+esc(m?.name?.split(' ')[0]||'Partenaire')+'</strong>'+
  premiumPolyDie(6,rolling&&localUser?rollFace:has?roll.face:null,'Dé de '+(m?.name||'partenaire'),rolling&&localUser)+
  '<div class="evening-face-result"><strong>'+(has?roll.face:'♡')+'</strong>'+
  '<small>'+(has?'défi'+(roll.face>1?'s':'')+' ce soir':roll?'Résultat caché':'Pas encore lancé')+'</small></div>'+
  (canRoll?bt('my-d6','Brasser mon dé','evening-small-roll'):(!roll?'<small class="evening-wait">En attente</small>':''))+
  '</article>';
};
export function premiumEveningHtml({
 session,members=[],me,rolls=[],status={},turn={},category=null,
 giftCount=0,receivedGifts=0,pending=null,rolling=false,
 challengeOverlay='',showMore=false,confirmCancel=false,rollFace=null,rollingKind=null,
 lastEvening=null,lastD35Face=null
}={}){
 const mine=members.find(m=>m.user_id===me)||members[0]||{},
 partner=members.find(m=>m.user_id!==me)||members[1]||{},
 ready=!!status?.both_ready,
 myRoll=rolls.find(r=>r.user_id===me),
 theirRoll=rolls.find(r=>r.user_id===partner.user_id),
 canMyRoll=!!session&&!status?.my_rolled&&!rolling;
 const first=turn?.first?members.find(m=>m.user_id===turn.first)?.name?.split(' ')[0]:'';
 const totals=turn.targets||{},done=turn.completed||{};
 const canDraw=ready&&turn.ready&&!turn.pending&&!turn.finished&&turn.next===me;
 const canGive=ready&&turn.ready&&giftCount<3&&Number(turn.remaining?.[me])>0;
 const challengeText=pending?'♡ Voir notre défi en cours':turn.finished?'Tous les défis terminés':!ready?'En attente des deux D6':canDraw?'À toi de jouer':'En attente de '+esc(partner.name?.split(' ')[0]||'ton partenaire');
 const help='35 défis Sensuel et 35 défis Sexuel · résultats synchronisés';
 const stateText=ready?'✓ Étape 2 débloquée':(status?.my_rolled?'Ton lancer est enregistré. Attends ton partenaire.':'Chaque partenaire brasse son propre dé.');
 const currentStatus=turn.finished?'♡ Tous nos défis sont terminés':pending?'♡ Défi en cours · réponse attendue':canDraw?'♥ C’est ton tour !':ready?'Au tour de '+esc(partner.name?.split(' ')[0]||'ton partenaire'):'Étape 2 verrouillée';
 // An official challenge is persisted in Supabase; never show a provisional
 // animation face or guess a result while a D35 is being rolled.
 const d35Candidate=pending?.face??lastD35Face;
 const d35Number=Number(d35Candidate);
 const officialD35=ready&&!rolling&&d35Candidate!=null&&
  Number.isInteger(d35Number)&&d35Number>=1&&d35Number<=35?d35Number:null;
 const d35Result=rolling&&rollingKind==='new-challenge'?'Brassage en cours…':
  officialD35!==null?'Résultat D35 · N° '+officialD35:'Résultat D35 · —';
 const count=(uid)=>esc((done[uid]||0)+' / '+(totals[uid]??'—'));
 return '<div class="evening-premium-view">'+
 '<header class="evening-topline"><span class="heartline">♡</span><div><h1>NOTRE SOIRÉE</h1><p>Un jeu synchronisé sur nos deux comptes</p></div>'+
 bt('evening-more','♡','evening-more-button')+'</header>'+
 '<div class="evening-intro"><span>♥ NOTRE SOIRÉE PARTAGÉE</span><h2>Nos 70 défis</h2><p>'+help+'</p></div>'+
 (session?
  '<section class="evening-step one"><div class="evening-step-label"><span class="step-number">1</span><div>'+
  '<strong>ÉTAPE 1 · NOMBRE DE DÉFIS</strong><small>Chacun lance son dé depuis son propre compte.</small></div></div>'+
  '<div class="evening-d6-grid">'+d6(mine,myRoll,true,canMyRoll,ready,rollingKind==='my-d6'&&rolling,rollFace)+
  d6(partner,theirRoll,false,false,ready,false,null)+'</div>'+
  '<div class="evening-ready '+(ready?'yes':'')+'">'+stateText+'</div></section>'+
  '<section class="evening-step two"><div class="evening-step-label"><span class="step-number">2</span><div>'+
  '<strong>ÉTAPE 2 · DÉFI DU SOIR</strong>'+
  '<small>'+(first?'♥ '+esc(first)+' commence !':'Premier joueur désigné au hasard')+'</small></div>'+
  '<div class="evening-progress"><span>'+esc(mine.name?.split(' ')[0]||'Moi')+'<b>'+count(me)+'</b></span>'+
  '<span>'+esc(partner.name?.split(' ')[0]||'Partenaire')+'<b>'+count(partner.user_id)+'</b></span></div></div>'+
  '<div class="evening-play">'+
    '<div class="evening-d35-col">'+premiumPolyDie(35,rolling&&rollingKind==='new-challenge'?rollFace:officialD35,'Notre dé 35 faces',rolling&&rollingKind==='new-challenge')+
       '<div class="evening-d35-result" role="status" aria-live="polite">'+esc(d35Result)+'</div>'+
       '<div class="evening-play-state">'+currentStatus+'</div></div>'+
    '<div class="evening-play-controls"><p>Le D35 révèle un défi parmi 70 cartes, à tour de rôle.</p>'+
    '<div class="evening-choice">'+
       bt('category:SENSUEL','♡ Sensuel','evening-choice-btn '+(category==='SENSUEL'?'selected':''),!canDraw)+
       bt('category:SEXUEL','♨ Sexuel','evening-choice-btn '+(category==='SEXUEL'?'selected':''),!canDraw)+'</div>'+
    '<small>88 % catégorie choisie · 12 % autre<br>Défi cadeau : 100 % de ton choix</small>'+
    (pending?bt('evening-challenge','♡ Voir notre défi','evening-primary'):
     turn.finished?bt('finish-session','✓ Terminer la soirée','evening-primary'):
     bt('new-challenge','🎲 Lancer le dé','evening-primary',!canDraw||!category||rolling))+
    '</div></div></section>'+
  '<section class="evening-gift"><div><strong>🎁 UN PETIT CADEAU</strong>'+
   '<p>Transfère un défi non commencé · '+giftCount+'/3 dons ce mois</p></div>'+
   bt('give-challenge','♡ Donner 1 défi à '+esc(partner.name?.split(' ')[0]||'mon partenaire'),
    'evening-gift-btn',!canGive||rolling)+'</section>'
  :
  '<section class="evening-empty"><p>Un tirage choisit qui commence, puis les défis alternent.</p>'+
   (lastEvening?'<p>Dernière soirée : '+esc(lastEvening.completed)+' défis réalisés.</p>':'')+
   bt('new-session','♡ COMMENCER NOTRE SOIRÉE','evening-primary')+'</section>')+
 (showMore?'<aside class="evening-more-menu"><strong>♡ Options de notre soirée</strong>'+
  bt('tab:journal','Calendrier partagé','subtle')+
  bt('tab:personal-journal','Mon calendrier personnel','subtle')+
  (session?bt('cancel-session','Arrêter sans compter','subtle'):'')+
  bt('evening-more','Fermer','subtle')+'</aside>':'')+
 (confirmCancel?'<aside class="evening-confirm"><h3>Arrêter sans compter ?</h3><p>Votre soirée ne sera pas comptabilisée.</p>'+
  bt('confirm-cancel','Oui, arrêter')+bt('dismiss-cancel','Continuer','subtle')+'</aside>':'')+
 challengeOverlay+
 '</div>';
}
