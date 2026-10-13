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
 return '<div class="marketing-shell" aria-label="Présentation LoveRoll">'+
 '<section class="love-hero">'+
  '<div class="love-scene">'+
   '<div class="love-brand"><span class="love-brand-decoration">♡</span>'+
   '<div class="love-wordmark"><span>Love</span><b>Roll</b></div>'+
   '<div class="love-signature">JEUX · DÉFIS · DATES · NOUS</div></div>'+
   '<img class="love-hero-art" src="./assets/loveroll-hero.webp" alt="Grand cœur en verre rose et deux dés noirs brillants sur un satin bourgogne" fetchpriority="high" decoding="async" width="682" height="376">'+
  '</div>'+
  '<div class="love-hero-copy">'+
   '<h1>Votre aventure à deux <em>commence ici.</em></h1>'+
   '<p>Rapprochez-vous avec des défis sensuels, des jeux pour couple, des sorties surprises et un calendrier partagé.</p>'+
   '<div class="love-cta-group">'+
    button('show-auth','♡ CRÉER MON COMPTE GRATUITEMENT →','love-cta-primary')+
    button('show-auth','J’AI DÉJÀ UN COMPTE · ME CONNECTER →','love-cta-secondary')+
   '</div>'+
   '<div class="love-auth-note"><span aria-hidden="true">♢</span> Connexion sécurisée pour Android et iPhone.</div>'+
  '</div>'+
 '</section>'+
 '<section class="love-features" aria-label="Fonctionnalités">'+
  feature('dice','Des dés rien qu’à vous','Chacun lance ses dés et partage une expérience unique avec son partenaire.')+
  feature('couple','Créez votre match','Invitez votre partenaire et créez votre espace à deux.')+
  feature('calendar','Vos dates, automatiquement','Retrouvez anniversaires et moments importants dans un calendrier partagé.')+
  feature('lock','Privé et complice','Vos défis et vos moments partagés restent dans votre espace privé.')+
 '</section>'+
 '<section class="love-why" aria-labelledby="why-title"><h2 id="why-title"><span>♡</span> Pourquoi vous allez aimer <span>♡</span></h2>'+
 '<div class="love-benefits">'+
  benefit('flame','Défis sensuels','Ravivez la flamme')+
  benefit('heart','Moments à deux','Du spontané au mémorable')+
  benefit('clock','Rituel hebdomadaire','Des idées toute l’année')+
  benefit('calendar','Calendrier partagé','Ne manquez aucune date')+
  benefit('users','Espace privé','Rien qu’à vous')+
 '</div></section>'+
 '<footer class="love-finale"><h2>Plus qu’un jeu,<br><em>une histoire à écrire.</em></h2>'+
  '<p>Des petits défis aux grandes surprises, LoveRoll transforme votre quotidien en moments inoubliables.</p>'+
  '<div class="love-handwriting">Même équipe,<br>plus de frissons. ♡</div>'+
  '<div class="love-trust">'+
    '<span>'+lineIcon('shield')+'<small>Sécurisé et privé</small></span>'+
    '<span>'+lineIcon('couple')+'<small>Conçu pour les couples</small></span>'+
    '<span>'+lineIcon('heart')+'<small>Sans pression — jouez à votre rythme</small></span>'+
  '</div>'+
  '<p class="love-legal">Réservé aux adultes de 18 ans et plus. Chaque activité est facultative et se décide ensemble.</p>'+
 '</footer></div>';
}
export function authHtml(){
 return '<div class="auth-panel"><div class="eyebrow">VOTRE NOUVELLE HISTOIRE</div>'+
 '<h2>Créer mon compte ou me connecter</h2>'+
 '<p>Un compte par personne. Les noms et les anniversaires viennent de l’inscription.</p>'+
 button('google','◉ Continuer avec Google')+
 button('apple','Continuer avec Apple','subtle')+
 '<div class="login-divider">ou par courriel</div>'+
 '<label>Courriel<input id="signin-email" type="email" autocomplete="email" placeholder="vous@exemple.com"></label>'+
 button('email-login','Recevoir un lien sécurisé','subtle')+
 '<p class="tiny-note">Les connexions Google et Apple nécessitent leur activation auprès de leurs fournisseurs. Un lien par courriel est aussi proposé.</p>'+
 button('show-landing','← Retour à la présentation','text-link')+'</div>';
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
 '<label class="checkline"><input type="checkbox" id="age-confirm"> Je confirme avoir 18 ans ou plus.</label>'+
 button('complete-registration','♡ Créer mon profil')+
 '<p class="tiny-note">Ton partenaire entrera lui-même son propre nom et son anniversaire.</p>'+
 '</div>';
}
export function matchHtml(name,invitation){
 return '<div class="auth-panel"><div class="eyebrow">ÉTAPE 2 SUR 2 · NOTRE MATCH</div>'+
 '<h2>Bienvenue '+esc(name)+' ♡</h2>'+
 (invitation?
 '<p>Tu as une invitation personnelle. En l’acceptant, tu rejoins votre espace partagé avec ton propre compte.</p>'+
 button('accept-invite','♡ Accepter ce match'):
 '<p>Créez votre duo, puis invitez votre partenaire par courriel. Elle ou il remplira son propre profil.</p>'+
 '<h3>🥂 Anniversaire de votre couple</h3>'+
 monthDayInputs('signup-couple','',true)+
 button('create-couple','♡ Créer notre espace à deux'))+
 '</div>';
}
