import {RELATIONSHIP_OPTIONS,INTIMACY_OPTIONS} from './wellbeing.mjs';
// LoveRoll — public onboarding UI. Profile name and personal anniversary come from registration.
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const button=(id,label,cls='')=>'<button class="'+cls+'" data-action="'+esc(id)+'">'+label+'</button>';
const months=['Janvier','Février','Mars','Avril','Mai','Juin','Juillet','Août','Septembre','Octobre','Novembre','Décembre'];
export function monthDayInputs(prefix,initial='',optional=false){
 const v=/^\d{2}-\d{2}$/.test(initial||'')?initial.split('-').map(Number):[0,0];
 return '<div class="date-fields"><label>Mois<select id="'+prefix+'-month"><option value="">Choisir le mois</option>'+
  months.map((name,i)=>'<option value="'+(i+1)+'" '+(v[0]===i+1?'selected':'')+'>'+name+'</option>').join('')+
  '</select></label><label>Jour<select id="'+prefix+'-day"><option value="">Jour</option>'+
  Array.from({length:31},(_,i)=>'<option value="'+(i+1)+'" '+(v[1]===i+1?'selected':'')+'>'+(i+1)+'</option>').join('')+
  '</select></label></div>'+
  (optional?'<p class="tiny-note">Facultatif : vous pourrez déterminer cette date ensemble plus tard.</p>':'');
}
export function parseMonthDay(prefix,required=true,documentRef=globalThis.document){
 const month=documentRef.getElementById(prefix+'-month')?.value;
 const day=documentRef.getElementById(prefix+'-day')?.value;
 if(!month&&!day&&!required)return null;
 if(!month||!day)throw Error('Sélectionne le mois et le jour.');
 const m=Number(month),d=Number(day);
 const valid=new Date(Date.UTC(2000,m-1,d));
 if(valid.getUTCMonth()!==m-1||valid.getUTCDate()!==d)throw Error('Cette date n’existe pas.');
 return String(m).padStart(2,'0')+'-'+String(d).padStart(2,'0');
}
const lineIcon = (type) => {
 const common='width="36" height="36" viewBox="0 0 48 48" fill="none" stroke="#ff82bd" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"';
 const shapes={
  dice:'<rect x="6" y="15" width="24" height="24" rx="6"/><rect x="19" y="7" width="23" height="23" rx="6"/><path d="M12 22l3 3 3-3 3 3 3-3M26 12c1-3 6-3 7 0 0 0 3-3 5 0 2 3-3 7-7 9-4-2-9-6-7-9z"/>',
  couple:'<path d="M24 39C10 30 5 25 8 17c3-8 13-7 16 0 3-7 13-8 16 0 3 8-2 13-16 22z"/><path d="M24 18c-2-5-9-5-11 0"/>',
  calendar:'<rect x="7" y="11" width="34" height="30" rx="5"/><path d="M7 21h34M16 7v8M32 7v8"/><path d="M24 35c-7-5-9-8-6-11 2-2 5-1 6 1 2-3 5-3 7-1 3 4-2 8-7 11z"/>',
  lock:'<rect x="11" y="22" width="26" height="20" rx="5"/><path d="M16 22v-8a8 8 0 0 1 16 0v8"/><path d="M24 28c-4-4-9 1 0 8 9-7 4-12 0-8z"/>',
  flame:'<path d="M24 42c-9 0-15-6-15-15 0-9 8-15 11-22 1 7 4 11 7 14 2-4 2-6 2-9 9 8 10 14 10 20 0 7-6 12-15 12z"/><path d="M24 40c-5 0-7-3-7-7 0-3 3-7 6-10 0 4 5 6 5 10s-1 7-4 7z"/>',
  heart:'<path d="M24 40C11 31 5 26 7 17c2-9 13-10 17-2 4-8 15-7 17 2 2 9-4 14-17 23z"/>',
  clock:'<circle cx="24" cy="24" r="17"/><path d="M24 12v13l9 6"/>',
  users:'<circle cx="18" cy="17" r="6"/><path d="M5 40v-5c0-7 6-11 13-11s13 4 13 11v5M33 12c6 0 9 9 2 12M35 29c5 1 8 4 8 10"/>',
  shield:'<path d="M24 5l16 7v10c0 12-8 18-16 21C16 40 8 34 8 22V12z"/><path d="m17 24 5 5 9-10"/>'
 };
 return '<svg '+common+'>'+shapes[type]+'</svg>';
};
const feature = (icon,title,body) =>
 '<article class="love-feature"><div class="love-feature-icon">'+lineIcon(icon)+'</div>'+
 '<h3>'+title+'</h3><p>'+body+'</p></article>';
const benefit = (icon,title,caption) =>
 '<div class="love-benefit"><div class="love-benefit-icon">'+lineIcon(icon)+'</div>'+
 '<strong>'+title+'</strong><small>'+caption+'</small></div>';
export function marketingHtml(){
 return '<div class="marketing-shell love-onepage" aria-label="LoveRoll · présentation avant connexion">'+
 '<section class="love-hero love-compact-hero">'+
  '<div class="love-scene"><div class="love-brand">'+
   '<span class="love-brand-decoration">♡</span>'+
   '<div class="love-wordmark"><span>Love</span><b>Roll</b></div>'+
   '<div class="love-signature">JEUX · DÉFIS · DATES · NOUS</div></div>'+
   '<img class="love-hero-art" src="./assets/loveroll-premium-scene.png" alt="Cœur en verre rose, deux dés noirs brillants et satin bourgogne" fetchpriority="high" decoding="async" width="1200" height="630">'+
  '</div>'+
  '<div class="love-hero-copy">'+
   '<h1>Votre aventure à deux <em>commence ici.</em></h1>'+
   '<p>Défis sensuels, soirées surprises et moments complices à partager.</p>'+
   '<div class="love-cta-group">'+
    button('show-auth','♡ CRÉER MON COMPTE GRATUITEMENT →','love-cta-primary')+
    button('show-auth','J’AI DÉJÀ UN COMPTE · ME CONNECTER →','love-cta-secondary')+
   '</div>'+
   '<div class="love-auth-note">♢ Connexion sécurisée pour Android et iPhone.</div>'+
  '</div>'+
 '</section>'+
 '<section class="love-features" aria-label="Quatre fonctionnalités LoveRoll">'+
  feature('dice','Dés à deux','Vos défis synchronisés.')+
  feature('couple','Votre match','Connectez votre couple.')+
  feature('calendar','Calendrier','Vos dates importantes.')+
  feature('lock','Espace privé','Rien qu’à vous.')+
 '</section>'+
 '<section class="love-mini-finale" aria-label="Notre philosophie">'+
   '<h2>Plus qu’un jeu, <em>votre histoire à deux. ♡</em></h2>'+
   '<p>Complicité, surprises et frissons à votre rythme.</p>'+
 '</section>'+
 '<footer class="love-compact-footer">'+
   '<a href="loveroll://offline" class="love-old-data" aria-label="Ouvrir les anciennes données hors ligne dans LoveRoll Android">Retrouver mes anciennes données hors ligne ↗</a>'+
   '<p>Réservé aux adultes de 18 ans et plus. Chaque activité est facultative.</p>'+
 '</footer>'+
 '</div>';
}
export function authHtml(invitation=false,mode='signup',notice='',emailValue=''){
 const recovering=mode==='recover',signingIn=mode==='signin';
 const title=recovering?'Nouveau mot de passe':signingIn?'Bienvenue dans LoveRoll':'Créez votre compte';
 const helper=recovering?'Choisissez un nouveau mot de passe pour retrouver votre compte.':
  signingIn?'Retrouvez votre espace privé et vos surprises.':
  'Utilisez votre courriel et un mot de passe, ou connectez-vous avec Google.';
 const icon=kind=>kind==='mail'?'✉':kind==='lock'?'♙':'♡';
 const progress=invitation?'<div class="invite-account-progress" aria-label="Invitation acceptée · étape 2 sur 2">'+
  '<div class="invite-progress-item is-done"><b>✓</b><span>Invitation<br>acceptée</span></div>'+
  '<div class="invite-progress-line"><i></i></div>'+
  '<div class="invite-progress-item is-current"><b>2</b><span>Votre compte</span></div></div>':'';
 const segment=recovering?'':'<div class="invite-auth-segments" role="group" aria-label="Choisir un mode">'+
  '<button type="button" data-action="auth-mode:signup" class="'+(!signingIn?'is-active':'')+'" aria-pressed="'+(!signingIn)+'">Créer un compte</button>'+
  '<button type="button" data-action="auth-mode:signin" class="'+(signingIn?'is-active':'')+'" aria-pressed="'+signingIn+'">Se connecter</button></div>';
 const email='<label class="invite-auth-field"><span class="invite-field-icon" aria-hidden="true">✉</span>'+
  '<span class="invite-field-body"><span>Votre courriel</span><input id="signin-email" name="email" type="email" autocomplete="email" inputmode="email" spellcheck="false" placeholder="exemple@courriel.com" maxlength="254" value="'+esc(emailValue)+'" required></span></label>';
 const pass='<label class="invite-auth-field"><span class="invite-field-icon" aria-hidden="true">♙</span>'+
  '<span class="invite-field-body"><span>'+(recovering?'Nouveau mot de passe':signingIn?'Votre mot de passe':'Choisissez un mot de passe')+'</span>'+
  '<input id="signin-password" name="password" type="password" autocomplete="'+(signingIn?'current-password':'new-password')+'" placeholder="••••••••" minlength="8" required></span>'+
  '<button type="button" class="invite-password-eye" data-action="auth-eye" aria-label="Afficher le mot de passe" aria-pressed="false">'+
  '<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 12s3.8-6 10-6 10 6 10 6-3.8 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg></button></label>';
 const meter=!signingIn?'<div class="invite-password-meter" aria-label="Robustesse du mot de passe">'+
  '<div class="invite-meter-bars"><i></i><i></i><i></i><i></i></div>'+
  '<p id="auth-password-hint">'+(recovering?'Créez un nouveau mot de passe robuste.':'Au moins 8 caractères, avec une majuscule, une minuscule et un chiffre.')+'</p></div>':'';
 const actions=recovering?button('auth-update-password','Enregistrer mon nouveau mot de passe ♡','invite-auth-submit'):
  button(signingIn?'auth-signin-password':'auth-signup-password',
   signingIn?'Se connecter ♡':'Créer mon compte <small>et rejoindre notre univers ♡</small><span aria-hidden="true">›</span>',
   'invite-auth-submit');
 const misc=recovering?button('auth-mode:signin','← Retour à la connexion','invite-auth-link'):
  (signingIn?button('auth-reset-request','Mot de passe oublié ?','invite-auth-link'):'')+
  '<div class="invite-or"><span>ou</span></div>'+
  '<button type="button" data-action="google" class="invite-google"><svg viewBox="0 0 48 48" width="24" height="24" aria-hidden="true">'+
 '<path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5Z"/>'+
 '<path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.28 5.48-4.81 7.18l7.75 6.01C44.4 38.03 46.98 31.84 46.98 24.55Z"/>'+
 '<path fill="#FBBC05" d="M10.53 28.59A14.3 14.3 0 0 1 9.75 24c0-1.59.27-3.13.77-4.59l-7.98-6.2A23.84 23.84 0 0 0 0 24c0 3.87.93 7.52 2.56 10.78l7.97-6.19Z"/>'+
 '<path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.91-5.79l-7.75-6.01c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.97 6.19C6.51 42.62 14.62 48 24 48Z"/></svg> Continuer avec Google</button>';
 return '<div class="invite-account-screen'+(recovering?' invite-recover':'')+'" aria-label="Compte LoveRoll">'+
  '<header class="invite-account-hero"><div class="invite-decor-heart" aria-hidden="true">♡</div>'+
  '<div class="invite-auth-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>'+
  '<p>LA COMPLICITÉ SE JOUE À DEUX</p>'+progress+'</header>'+
  '<section class="invite-auth-card"><div class="invite-auth-title">'+
  '<span class="invite-account-icon" aria-hidden="true">♙</span><div><h1>'+title+'</h1>'+
  '<h2>'+(invitation?'et rejoignez votre partenaire ♡':'Votre espace privé ♡')+'</h2></div></div>'+
  '<p class="invite-auth-lead">'+helper+'</p>'+segment+
  '<form id="invite-auth-form" autocomplete="on"><div class="invite-auth-fields">'+(recovering?'':email)+pass+'</div>'+
  meter+'<div class="invite-auth-feedback" id="auth-form-feedback" role="status" aria-live="polite">'+esc(notice)+'</div>'+
  actions+'</form>'+misc+
  '<div class="invite-auth-trust"><span aria-hidden="true">♢</span><p>Vos données restent privées. Chaque personne utilise son propre compte.</p></div>'+
  (invitation?button('invite-return','← Revenir à l’invitation','invite-auth-back'):
   button('show-landing','← Retour à l’accueil','invite-auth-back'))+
  '</section></div>';
}
// Custom in-app chooser: Android WebView's native <select> popup uses the OS
// light theme and ignores CSS. Use a themed modal for both wellbeing questions.
function wellnessSelect(label,id,options,selected=''){
 const choice=options.find(([value])=>value===selected);
 return '<div class="love-answer-field">'+
 '<span id="label-'+id+'" class="love-answer-label">'+esc(label)+'</span>'+
 '<input type="hidden" id="'+id+'" value="'+esc(choice?.[0]||'')+'">'+
 '<button type="button" class="love-answer-trigger" data-love-answer-open="'+id+
 '" aria-haspopup="dialog" aria-expanded="false" aria-labelledby="label-'+id+' current-'+id+'">'+
 '<span id="current-'+id+'" class="love-answer-current">'+esc(choice?.[1]||'Choisir une réponse')+'</span>'+
 '<span class="love-answer-chevron" aria-hidden="true">⌄</span></button>'+
 '<dialog class="love-answer-dialog" id="dialog-'+id+'" aria-labelledby="title-'+id+'">'+
 '<div class="love-answer-top"><div class="love-answer-kicker">LOVEROLL · MON RESSENTI</div>'+
 '<h3 id="title-'+id+'">'+esc(label)+'</h3>'+
 '<p>Choisissez la réponse qui vous correspond. Vous pourrez la modifier plus tard.</p></div>'+
 '<div class="love-answer-choices" role="radiogroup" aria-labelledby="title-'+id+'">'+
 options.map(([value,text])=>'<button type="button" role="radio" class="love-answer-option"'+
 ' data-love-answer-value="'+esc(value)+'" aria-checked="'+(value===selected?'true':'false')+'">'+
 '<span class="love-answer-option-label">'+esc(text)+'</span>'+
 '<span class="love-answer-radio" aria-hidden="true"></span></button>').join('')+
 '</div><button type="button" class="love-answer-dismiss" data-love-answer-close>Annuler</button>'+
 '</dialog></div>';
}
export function wellbeingFields(current=null){
 return '<h3>♡ Comment vivez-vous votre relation ?</h3>'+
 '<p class="tiny-note">Deux réponses personnelles et privées pour proposer des soirées adaptées. Le partenaire ne verra pas vos réponses.</p>'+
 wellnessSelect('Comment va votre couple en ce moment ?','wellbeing-relationship',RELATIONSHIP_OPTIONS,current?.relationship_state)+
 wellnessSelect('Comment va votre sexualité dans votre couple ?','wellbeing-intimacy',INTIMACY_OPTIONS,current?.intimacy_state);
}
export function wellbeingHtml(current=null){
 return '<div class="auth-panel"><div class="eyebrow">MON RESSENTI · CONFIDENTIEL</div>'+
 '<h2>Des soirées à votre rythme ♡</h2>'+wellbeingFields(current)+
 button('save-wellbeing','Enregistrer mes réponses')+
 '<p class="tiny-note">Vos réponses sont modifiables à tout moment dans Réglages.</p></div>';
}

export function inviteProfileHtml(user,profile){
 const name=profile?.display_name||user?.user_metadata?.given_name||
  user?.user_metadata?.full_name||user?.user_metadata?.name||'';
 return '<div class="invite-account-screen invite-profile-screen">'+
  '<header class="invite-account-hero"><div class="invite-auth-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>'+
  '<p>LA COMPLICITÉ SE JOUE À DEUX</p>'+
  '<div class="invite-account-progress"><div class="invite-progress-item is-done"><b>✓</b><span>Invitation acceptée</span></div>'+
  '<div class="invite-progress-line"><i></i></div><div class="invite-progress-item is-current"><b>2</b><span>Votre profil</span></div></div></header>'+
  '<section class="invite-auth-card invite-profile-card"><h1>Un peu plus sur vous ♡</h1>'+
  '<p>Ces informations terminent votre inscription personnelle avant de rejoindre votre partenaire.</p>'+
  '<label>Votre prénom<input id="profile-name" autocomplete="given-name" maxlength="80" value="'+esc(name)+'"></label>'+
  '<h3>Votre anniversaire</h3>'+monthDayInputs('signup-birth',profile?.birthday_md||'')+
  wellbeingFields()+
  '<label class="checkline"><input type="checkbox" id="age-confirm"> Je confirme avoir 18 ans ou plus.</label>'+
  button('complete-registration','Terminer et rejoindre LoveRoll ♡','invite-auth-submit')+
  '<p class="invite-small-note">Le mot de passe du compte et la phrase secrète du journal sont distincts.</p>'+
  '</section></div>';
}
export function inviteWellbeingHtml(current=null){
 return '<div class="invite-account-screen invite-profile-screen"><header class="invite-account-hero">'+
  '<div class="invite-auth-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>'+
  '<p>LA COMPLICITÉ SE JOUE À DEUX</p></header>'+
  '<section class="invite-auth-card invite-profile-card"><h1>Votre ressenti ♡</h1>'+
  wellbeingFields(current)+button('save-wellbeing','Continuer vers LoveRoll ♡','invite-auth-submit')+
  '</section></div>';
}
export function profileHtml(user,profile){
 const name=profile?.display_name||user?.user_metadata?.full_name||
 user?.user_metadata?.name||'';
 return '<div class="auth-panel"><div class="eyebrow">ÉTAPE 1 SUR 2 · MON PROFIL</div>'+
 '<h2>Enchanté·e ♡</h2>'+
 '<p>Ton nom peut venir de Google ou Apple. Tu peux le corriger avant de continuer.</p>'+
 '<label>Mon nom à afficher<input id="profile-name" maxlength="80" autocomplete="given-name" value="'+esc(name)+'"></label>'+
 '<h3>🎂 Mon anniversaire</h3>'+
 '<p>Il est renseigné une seule fois, à l’inscription, pour les événements spéciaux. Nous ne conservons que le mois et le jour.</p>'+
 monthDayInputs('signup-birth',profile?.birthday_md||'')+
 wellbeingFields()+
 '<label class="checkline"><input type="checkbox" id="age-confirm"> Je confirme avoir 18 ans ou plus.</label>'+
 button('complete-registration','♡ Créer mon profil')+
 '<p class="tiny-note">Ton partenaire entrera lui-même son propre nom et son anniversaire.</p>'+
 '</div>';
}
export function matchHtml(name,invitation){
 return '<div class="auth-panel"><div class="eyebrow">ÉTAPE 2 SUR 2 · NOTRE MATCH</div>'+
 '<h2>Bienvenue '+esc(name)+' ♡</h2>'+
 (invitation?
 '<p>Votre invitation sera associée automatiquement après la vérification du compte.</p>':
 '<p>Créez votre duo, puis invitez votre partenaire par courriel. Elle ou il remplira son propre profil.</p>'+
 '<h3>🥂 Anniversaire de votre couple</h3>'+
 monthDayInputs('signup-couple','',true)+
 button('prepare-cards','♡ Préparer mes défis avant l’invitation')+
 button('create-couple','♡ Créer notre espace à deux'))+
 '</div>';
}

export function bindWellbeingMenus(container=document){
 container.querySelectorAll('.love-answer-field').forEach(field=>{
  const trigger=field.querySelector('[data-love-answer-open]');
  const dialog=field.querySelector('.love-answer-dialog');
  const valueField=field.querySelector('input[type="hidden"]');
  if(!trigger||!dialog||!valueField)return;
  const close=()=>{if(dialog.open)dialog.close();trigger.setAttribute('aria-expanded','false');trigger.focus();};
  trigger.addEventListener('click',()=>{
   if(dialog.open)return;
   dialog.showModal();
   trigger.setAttribute('aria-expanded','true');
   const selected=dialog.querySelector('[aria-checked="true"]');
   (selected||dialog.querySelector('.love-answer-option'))?.focus();
  });
  dialog.querySelectorAll('.love-answer-option').forEach(option=>{
   option.addEventListener('click',()=>{
    valueField.value=option.dataset.loveAnswerValue;
    trigger.querySelector('.love-answer-current').textContent=
     option.querySelector('.love-answer-option-label').textContent;
    dialog.querySelectorAll('.love-answer-option').forEach(item=>
     item.setAttribute('aria-checked',item===option?'true':'false'));
    close();
   });
  });
  dialog.querySelector('[data-love-answer-close]')?.addEventListener('click',close);
  dialog.addEventListener('cancel',()=>trigger.setAttribute('aria-expanded','false'));
  dialog.addEventListener('click',event=>{
   if(event.target!==dialog)return;
   const rect=dialog.getBoundingClientRect();
   if(event.clientX<rect.left||event.clientX>rect.right||
      event.clientY<rect.top||event.clientY>rect.bottom)close();
  });
 });
}
