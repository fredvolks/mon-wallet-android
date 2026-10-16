// LoveRoll connected couple — production UI, not a demo. All results are provided by Supabase.
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&quot;',"'":'&#39;'}[c]));
const bt=(action,text,cls='',disabled=false)=>'<button type="button" class="'+cls+'" data-action="'+esc(action)+'"'+
 (action.startsWith('category:')?' aria-pressed="'+(cls.includes('selected')?'true':'false')+'"':'')+
 (disabled?' disabled':'')+'>'+text+'</button>';
export function premiumPolyDie(sides,face=null,caption='',spinning=false,preview=false){
 const valid=Number.isInteger(face)&&face>=1&&face<=sides;
 // A missing D6 value is NOT 1. Until the official result is known,
 // show a neutral heart rather than a real-looking but fake 'one pip'.
 if(sides===6&&!valid&&!preview){
  return '<div class="love3d love3d-6" role="group" aria-label="'+esc(caption)+' · dé en attente">'+
   '<div class="love3d-stage is-unrolled"><strong class="dice-fallback">♡</strong>'+
   '<canvas class="dice-canvas" width="250" height="250" data-dice-sides="6" '+
   'data-face="1" data-dice-unrolled="true" data-dice-markers="hearts" '+
   'data-spinning="false" aria-label="Dé 3D décoratif avec des cœurs, en attente du résultat officiel"></canvas></div></div>';
 }
 const target=valid?face:1;
 return '<div class="love3d love3d-'+sides+'" role="group" aria-label="'+esc(caption)+'">'+
  '<div class="love3d-stage '+(spinning?'is-rolling':'')+'">'+
   '<strong class="dice-fallback"'+(spinning?' aria-hidden="true"':'')+'>'+'♡'+'</strong>'+
   '<canvas class="dice-canvas" width="250" height="250" data-dice-sides="'+sides+
   '" data-face="'+target+'" data-dice-zoom="'+(sides===20?'1.30':sides===35||sides===100?'1.07':preview&&!valid?'1.14':'1')+
   '"'+(preview&&!valid?' data-dice-preview="true"':'')+' data-dice-markers="'+(sides===6?'pips':'numbers')+'" data-spinning="'+(spinning?'true':'false')+
   '" aria-label="'+(spinning?'Dé en cours de brassage':valid?'Dé arrêté, résultat '+face:
     preview?'Dé 3D décoratif en attente du tirage officiel':'Dé en attente')+'"></canvas>'+
  '</div></div>';
}
// Changing the category must not remount a live WebGL canvas. The shared
// result, 3D geometry and dice engine stay untouched until an actual roll.
export function updateEveningCategoryUi(root,category){
 if(!['SENSUEL','HASARD','SEXUEL'].includes(category))return false;
 const group=root?.querySelector('.evening-step.two .evening-choice');
 const buttons=[...(group?.querySelectorAll('button[data-action^="category:"]')||[])];
 if(buttons.length!==3||buttons.every(button=>button.disabled))return false;
 const names={SENSUEL:'Sensuel',HASARD:'Au hasard',SEXUEL:'Sexuel'};
 for(const button of buttons){
  const type=button.dataset.action?.slice('category:'.length);
  if(!(type in names))return false;
  const selected=type===category;
  button.classList.toggle('selected',selected);
  button.setAttribute('aria-pressed',String(selected));
  button.textContent=(selected?'✓ ':type==='HASARD'?'🎲 ':'♡ ')+names[type];
  const option=button.closest('.evening-choice-option');
  if(!option)continue;
  let odds=option.querySelector('.evening-choice-odds');
  if(selected&&category!=='HASARD'){
   if(!odds){
    odds=button.ownerDocument.createElement('small');
    odds.className='evening-choice-odds';
    option.appendChild(odds);
   }
   if(odds.textContent!=='88 % de chancesd’avoir cette catégorie'){
    odds.textContent='88 % de chances';
    const detail=button.ownerDocument.createElement('span');
    detail.textContent='d’avoir cette catégorie';
    odds.appendChild(detail);
   }
  }else odds?.remove();
 }
 const roll=group.closest('.evening-step.two')?.querySelector('button[data-action="new-challenge"]');
 if(roll)roll.disabled=false; // Choices are enabled only when this player may draw.
 return true;
}
const d6=(m,roll,localUser,canRoll,ready,rolling,rollFace,effectiveTarget=null)=>{
 // Reveal a player's real server-confirmed result as soon as THEY roll.
 // Their face is immutable; the second player's roll cannot modify it.
 const has=Number.isInteger(roll?.face)&&roll.face>=1&&roll.face<=6;
 // Only gift transfers modify the separate challenge total, not D6 history.
 const officialTarget=Number(effectiveTarget);
 const reveal=has&&!(localUser&&rolling);
 const adjusted=reveal&&effectiveTarget!=null&&Number.isInteger(officialTarget)&&
  officialTarget>=0&&officialTarget!==roll.face;
 const displayCount=adjusted?officialTarget:reveal?roll.face:null;
 return '<article class="evening-user-die evening-my-die" aria-label="Mon dé personnel">'+
  premiumPolyDie(6,rolling&&localUser?rollFace:has?roll.face:null,'Mon dé',rolling&&localUser&&Number.isInteger(rollFace))+
  (reveal?'<div class="evening-face-result" aria-label="Total actuel '+displayCount+' défis; dé initial '+roll.face+'"><strong>'+esc(displayCount)+'</strong>'+
   '<small>défi'+(displayCount>1?'s':'')+' ce soir</small>'+
   (adjusted?'<span class="evening-original-d6">Dé initial : '+esc(roll.face)+'</span>':'')+'</div>':
   '<div class="evening-face-result is-empty" aria-hidden="true"></div>')+
  (canRoll?bt('my-d6','Brasser mon dé','evening-small-roll'):'')+
  '</article>';
};
const partnerStatusCard=(partner,roll)=>{
 const face=Number(roll?.face);
 const confirmed=roll?.face!=null&&Number.isInteger(face)&&face>=1&&face<=6;
 const name=esc(partner?.name?.split(' ')[0]||'Partenaire');
 const statusText=confirmed?
  name+' a obtenu '+esc(face)+' défi'+(face===1?'':'s'):
  'En attente du lancer de '+name;
 return '<aside class="evening-partner-card '+(confirmed?'is-confirmed':'is-pending')+'" aria-live="polite" aria-label="Dé de '+name+'">'+
   '<span class="evening-partner-mobile-status">'+statusText+'</span>'+
   '<span class="evening-partner-caption">'+name+'</span>'+
   (confirmed?'<div class="evening-partner-die">'+premiumPolyDie(6,face,'Dé confirmé de '+name)+'</div>'+
     '<div class="evening-partner-result" aria-label="Résultat de '+name+' : '+face+'"><strong>'+esc(face)+'</strong>'+
     '<small>défi'+(face>1?'s':'')+' ce soir</small></div>':
     '<div class="evening-partner-pending"><span class="evening-partner-medallion" aria-hidden="true">♡</span>'+
     '<strong class="evening-partner-message">En attente</strong><small>du lancer</small></div>')+
  '</aside>';
};
const step1Summary=(mine,partner,myRoll,theirRoll,totals)=>{
 const item=(member,roll)=>'<div class="evening-summary-person">'+
  '<span>'+esc(member.name?.split(' ')[0]||'Partenaire')+'</span>'+
  '<span class="evening-summary-die">'+premiumPolyDie(6,roll?.face??null,'Dé de '+esc(member.name||'partenaire'))+'</span>'+
  '<b>'+esc(totals[member.user_id]??roll?.face??'—')+'</b></div>';
 return '<div class="evening-step1-summary">'+item(mine,myRoll)+item(partner,theirRoll)+'</div>';
};
export function premiumEveningHtml({
 session,members=[],me,rolls=[],status={},turn={},category=null,
 giftCount=0,receivedGifts=0,pending=null,rolling=false,
 challengeOverlay='',showMore=false,confirmCancel=false,rollFace=null,rollingKind=null,
 lastEvening=null,lastD35Face=null,playable=null,ownChallengeCount=0,canStartEvening=true,
 weeklyReady=true,weeklyCount=0,weeklyLimit=1,dailyLocked=false
}={}){
 const mine=members.find(m=>m.user_id===me)||members[0]||{},
 partner=members.find(m=>m.user_id!==me)||members[1]||{},
 ready=!!status?.both_ready,
 myRoll=rolls.find(r=>r.user_id===me),
 theirRoll=rolls.find(r=>r.user_id===partner.user_id),
 canMyRoll=!!session&&!status?.my_rolled&&!rolling;
 const first=turn?.first?members.find(m=>m.user_id===turn.first)?.name?.split(' ')[0]:'';
 const totals=turn.targets||{},done=turn.completed||{};
 const canDraw=ready&&turn.ready&&!turn.drawsFinished&&
  Number(turn.remaining?.[me]||0)>0&&turn.next===me;
 const canGive=ready&&turn.ready&&giftCount<3&&Number(turn.remaining?.[me])>0;
 const challengeText=pending?'♡ Voir notre défi en cours':turn.finished?'Tous les défis terminés':!ready?'En attente des deux D6':canDraw?'À toi de jouer':'En attente de '+esc(partner.name?.split(' ')[0]||'ton partenaire');
 const help='100 Sensuel + 100 Sexuel = 200 cartes · '+(playable?playable.total+' idées jouables':'idées personnalisables')+'; résultats synchronisés';
 const stateText=ready?'✓ Étape 2 débloquée':status?.my_rolled?'Ton lancer est enregistré. Attends ton partenaire.':status?.partner_rolled?'Le résultat de '+esc(partner.name?.split(' ')[0]||'ton partenaire')+' est prêt. À toi de brasser le dé.':'Chaque partenaire brasse son propre dé.';
 const currentStatus=turn.finished?'♡ Tous les défis sont réalisés':turn.drawsFinished?'♡ Tous les défis sont pigés':canDraw?'♥ C’est ton tour !':ready?'Au tour de '+esc(partner.name?.split(' ')[0]||'ton partenaire'):'Étape 2 verrouillée';
 // An official challenge is persisted in Supabase; never show a provisional
 // animation face or guess a result while a D100 is being rolled.
 const d35Candidate=pending?.face??turn?.pending?.face??lastD35Face;
 const d35Number=Number(d35Candidate);
 const officialD100=ready&&!rolling&&d35Candidate!=null&&
  Number.isInteger(d35Number)&&d35Number>=1&&d35Number<=100?d35Number:null;
 // The official result is already visible on the actual 3D face.
 // Never render a duplicate text number underneath or behind the die.
 const count=(uid)=>esc((done[uid]||0)+' / '+(totals[uid]??'—'));
 return '<div class="evening-premium-view '+(session?(ready?'is-ready':'is-waiting'):'is-inactive is-finale')+'">'+
 '<div class="evening-intro"><span>♥ NOTRE RITUEL DE LA SOIRÉE</span><h2>Nos défis disponibles</h2><p>'+help+'</p></div>'+
 (session?
  '<section class="evening-step one '+(ready?'is-complete':'is-incomplete')+'"><div class="evening-step-label"><span class="step-number">1</span><div>'+
  '<strong>ÉTAPE 1 · NOMBRE DE DÉFIS</strong><small>Chacun lance son dé depuis son propre compte.</small></div></div>'+
  '<div class="evening-d6-grid">'+
   d6(mine,myRoll,true,canMyRoll,ready,rollingKind==='my-d6'&&rolling,rollFace,turn?.ready?turn.targets?.[mine.user_id]:null)+
   partnerStatusCard(partner,theirRoll)+'</div>'+
  (ready?step1Summary(mine,partner,myRoll,theirRoll,totals):'')+
  '<div class="evening-ready '+(ready?'yes':'')+'">'+stateText+'</div></section>'+
  '<section class="evening-step two"><div class="evening-step-label"><span class="step-number">2</span><div>'+
  '<strong>ÉTAPE 2 · DÉFI DU SOIR</strong>'+
  '<small>'+(!ready?'Compléter l’étape 1 pour brasser le dé':first?'♥ '+esc(first)+' commence !':'Premier joueur désigné au hasard')+'</small></div>'+
  '<button type="button" class="evening-manage" data-action="evening-more" aria-label="Gérer notre soirée" title="Gérer notre soirée">⋯</button></div>'+ 
  '<div class="evening-progress" aria-label="Progression de nos défis"><span title="'+esc(mine.name||'Moi')+'">'+esc(mine.name||'Moi')+'<b>'+count(me)+'</b></span>'+
  '<span title="'+esc(partner.name||'Partenaire')+'">'+esc(partner.name||'Partenaire')+'<b>'+count(partner.user_id)+'</b></span></div>'+
  '<div class="evening-play">'+
    '<div class="evening-choice" role="group" aria-label="Choisir la catégorie de ce défi">'+
       '<div class="evening-choice-option">'+
         bt('category:SENSUEL',(category==='SENSUEL'?'✓ ':'♡ ')+'Sensuel','evening-choice-btn '+(category==='SENSUEL'?'selected':''),!canDraw)+
         (category==='SENSUEL'?'<small class="evening-choice-odds">88 % de chances<span>d’avoir cette catégorie</span></small>':'')+'</div>'+
       '<div class="evening-choice-option">'+
         bt('category:HASARD',(category==='HASARD'?'✓ ':'🎲 ')+'Au hasard','evening-choice-btn '+(category==='HASARD'?'selected':''),!canDraw)+'</div>'+
       '<div class="evening-choice-option">'+
         bt('category:SEXUEL',(category==='SEXUEL'?'✓ ':'♡ ')+'Sexuel','evening-choice-btn '+(category==='SEXUEL'?'selected':''),!canDraw)+
         (category==='SEXUEL'?'<small class="evening-choice-odds">88 % de chances<span>d’avoir cette catégorie</span></small>':'')+'</div></div>'+
    '<div class="evening-die-row">'+
      '<div class="evening-d35-col">'+premiumPolyDie(100,rolling&&rollingKind==='new-challenge'?rollFace:officialD100,'Notre dé 100 faces',rolling&&rollingKind==='new-challenge')+
       '<div class="evening-play-state">'+currentStatus+'</div></div>'+

    '</div>'+
    '<div class="evening-roll-actions">'+
      (turn.finished?bt('finish-session','✓ Terminer la soirée','evening-primary'):
       turn.drawsFinished?'<p class="evening-draw-done">✓ Tous les tirages sont prêts !</p>':
        bt('new-challenge','🎲 Brasser le dé','evening-primary',!canDraw||!category||rolling))+
      bt('evening-list','♡ Voir mes défis ('+ownChallengeCount+')','evening-queue-open-btn')+
    '</div>'+
  '</div></section>'+

  '<section class="evening-gift"><div><strong>🎁 UN PETIT CADEAU</strong>'+
   '<p>Transfère un défi non commencé · '+giftCount+'/3 dons ce mois</p></div>'+
   bt('give-challenge','♡ Donner 1 défi à '+esc(partner.name?.split(' ')[0]||'mon partenaire'),
    'evening-gift-btn',!canGive||rolling)+'</section>'
  :
  '<section class="evening-empty is-finale">'+
  '<div class="evening-finale-hero" aria-hidden="true">'+
    '<svg class="evening-finale-heart" viewBox="0 0 220 195" focusable="false" aria-hidden="true">'+
      '<path d="M110 181 C87 160 17 118 17 72 C17 36 40 16 72 16 C91 16 103 27 110 42 C117 27 129 16 148 16 C180 16 203 36 203 72 C203 118 133 160 110 181Z"/></svg>'+
    '<span class="evening-finale-velvet"></span>'+
    '<div class="evening-finale-dice">'+premiumPolyDie(6,null,'Dé décoratif')+
      premiumPolyDie(6,null,'Dé décoratif')+'</div></div>'+
  '<div class="evening-aftercare">'+
    '<div class="evening-finale-ornament" aria-hidden="true"><span>♡</span></div>'+
    '<div class="evening-finale-weekly">♡ SOIRÉES CETTE SEMAINE · '+
       esc(weeklyCount)+' / '+(weeklyReady?esc(weeklyLimit):'—')+'</div>'+
    (lastEvening?
      '<h2>Notre soirée est terminée</h2>'+
      '<p class="evening-finale-description"><strong>'+esc(lastEvening.completed)+
       '</strong> défis réalisés ensemble.<span>Prenez le temps de donner votre avis.</span></p>'+
      '<div class="evening-finale-status" role="status">'+
       '<div class="evening-finale-person"><i aria-hidden="true">'+(lastEvening.myResponse?'✓':'◷')+'</i>'+
        '<span>Vos réponses :<strong>'+(lastEvening.myResponse?'Enregistrées':'En attente')+'</strong></span></div>'+
       '<div class="evening-finale-person"><i aria-hidden="true">'+(lastEvening.partnerResponse?'✓':'◷')+'</i>'+
        '<span>Votre partenaire :<strong>'+(lastEvening.partnerResponse===null?'Statut indisponible':lastEvening.partnerResponse?'Enregistrées':'En attente')+'</strong></span></div>'+
       '<div class="evening-finale-progress"><i aria-hidden="true">▤</i>'+
        '<span><strong>'+(lastEvening.responses===null?'—':esc(lastEvening.responses))+' / 2 bilans déposés</strong>'+
         '<small>Chacun est visible dans le calendrier après son enregistrement.</small></span></div>'+
      '</div>':
      '<h2>Votre soirée vous attend</h2>'+
      '<p class="evening-finale-description">'+
        (!weeklyReady?'Brassez d’abord le dé hebdomadaire depuis l’accueil.':
         dailyLocked?'Une soirée a déjà eu lieu aujourd’hui. Prochaine soirée au plus tôt demain.':
         canStartEvening?'Votre prochain moment à deux vous attend.':
         'Toutes les soirées de la semaine ont été utilisées.')+'</p>'+
      '<div class="evening-finale-tiles" role="group" aria-label="Informations des soirées">'+
       '<div><strong>'+esc(weeklyCount)+' / '+(weeklyReady?esc(weeklyLimit):'—')+
         '</strong><span>Soirées cette semaine</span></div>'+
       '<div><strong>100 + 100'+
         '</strong><span>Sensuel / Sexuel</span></div>'+
       '<div><strong>'+esc(weeklyCount)+'</strong><span>Soirées terminées</span></div></div>')+
    '<div class="evening-finale-actions">'+
      bt('new-session','♡ COMMENCER NOTRE SOIRÉE','evening-finale-start',!canStartEvening)+
      bt('evening-calendar','▦ Consulter notre calendrier','evening-finale-calendar')+
    '</div>'+
  '</div></section>')+
 (showMore&&session?'<aside class="evening-more-menu" aria-label="Options de la soirée">'+
  bt('evening-close-request','♡ Clore la soirée','evening-menu-close',!session)+
  bt('evening-calendar','▦ Calendrier','evening-menu-calendar')+'</aside>':'')+
 (confirmCancel&&session?'<aside class="evening-confirm" role="dialog" aria-modal="true" aria-label="Clore la soirée ?">'+
  '<h3>Clore la soirée ?</h3><p>Les défis réalisés et non réalisés seront conservés dans le calendrier et les statistiques du couple. Aucun résultat ne sera effacé.</p>'+
  bt('evening-close-confirm','♡ Clore et conserver','evening-primary')+
  bt('dismiss-cancel','Continuer la soirée','subtle')+'</aside>':'')+
 (session?challengeOverlay:'')+
 '</div>';
}
