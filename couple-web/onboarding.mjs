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
export function marketingHtml(){
 return '<div class="marketing-shell">'+
 '<section class="landing-hero">'+
 '<div class="brand-mark" aria-hidden="true"><span>♥</span><small>♥ ♥ ♥</small></div>'+
 '<div class="eyebrow">VOTRE HISTOIRE · VOS RÈGLES</div>'+
 '<h1>Un dé. Deux cœurs.<span>Des soirées inoubliables.</span></h1>'+
 '<p>Faites de vos moments à deux des rendez-vous complices, sensuels et spontanés. Un vrai jeu de couple, sur Android et iPhone.</p>'+
 button('show-auth','♡ Créer mon compte gratuitement','landing-cta')+
 '<p class="landing-small">Déjà inscrit ? '+button('show-auth','Se connecter →','text-link')+'</p>'+
 '</section>'+
 '<section class="landing-features">'+
 '<article class="feature-card"><div>🎲</div><h3>Des dés et des surprises</h3><p>Défis sensuels, soirées personnalisées, activités spéciales.</p></article>'+
 '<article class="feature-card"><div>💌</div><h3>Votre duo, vos comptes</h3><p>Un match par courriel, chacun son dé et ses préférences.</p></article>'+
 '<article class="feature-card"><div>✦</div><h3>Les dates qui comptent</h3><p>Saint-Valentin, anniversaires, calendrier et sorties.</p></article>'+
 '<article class="feature-card"><div>♡</div><h3>Votre espace privé</h3><p>Des souvenirs et un journal chiffré, accessibles au couple.</p></article>'+
 '</section><p class="landing-small">Réservé aux adultes de 18 ans et plus. Chaque activité est librement choisie.</p>'+
 '</div>';
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
