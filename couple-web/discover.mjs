// LoveRoll : vitrine premium unique, avant toute connexion.
// Invitation -> présentation complète -> consentement explicite -> connexion.
const diceArt=`<svg class="discover-premium-die" viewBox="0 0 290 298" role="img" aria-label="Dé noir en verre avec cœurs roses lumineux">
 <defs>
  <linearGradient id="lr-top" x1="0" x2=".9" y1="0" y2="1"><stop stop-color="#8f587c"/><stop offset=".26" stop-color="#36202f"/><stop offset=".64" stop-color="#0b0812"/><stop offset="1" stop-color="#5d254f"/></linearGradient>
  <linearGradient id="lr-left" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#32162a"/><stop offset=".49" stop-color="#07050a"/><stop offset=".84" stop-color="#220920"/><stop offset="1" stop-color="#5d1748"/></linearGradient>
  <linearGradient id="lr-right" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#57203f"/><stop offset=".19" stop-color="#1b0d1c"/><stop offset=".68" stop-color="#08050c"/><stop offset="1" stop-color="#40132d"/></linearGradient>
  <radialGradient id="lr-heart" cx=".3" cy=".26" r="1"><stop stop-color="#ffe2ef"/><stop offset=".3" stop-color="#ff79b5"/><stop offset=".75" stop-color="#ff168c"/><stop offset="1" stop-color="#96003e"/></radialGradient>
  <filter id="lr-glow"><feGaussianBlur stdDeviation="6"/></filter>
  <filter id="lr-shadow"><feGaussianBlur stdDeviation="10"/></filter>
 </defs>
 <ellipse cx="146" cy="260" rx="112" ry="24" fill="#ff207b" opacity=".35" filter="url(#lr-shadow)"/>
 <path d="M30 76 Q28 60 45 49 L125 12 Q141 5 155 12 L245 54 Q262 63 261 81 L258 200 Q258 218 244 226 L157 276 Q141 286 125 276 L36 224 Q23 217 23 197Z" fill="#0a0710" stroke="#ff87c3" stroke-width="3"/>
 <path d="M31 75 Q29 62 46 52 L127 15 Q141 9 154 15 L243 57 Q257 64 257 80 L142 144 Z" fill="url(#lr-top)" stroke="#ea7eb2" stroke-width="2"/>
 <path d="M31 76 L142 144 L141 278 L37 221 Q24 212 25 196Z" fill="url(#lr-left)" stroke="#a74a88" stroke-width="2"/>
 <path d="M142 144 L257 80 L256 196 Q256 214 244 223 L142 278Z" fill="url(#lr-right)" stroke="#b44386" stroke-width="2"/>
 <path d="M39 69 Q82 38 135 19" stroke="#fff4ff" stroke-width="7" stroke-linecap="round" opacity=".3" fill="none"/>
 <path d="M40 88 L129 143 L129 253" stroke="#ffaad2" stroke-width="3" opacity=".2" fill="none"/>
 <path d="M153 148 L244 100 L242 182" stroke="#ffd1e9" stroke-width="5" opacity=".17" fill="none"/>
 <g fill="url(#lr-heart)" stroke="#ffbddd" stroke-width="1.4">
  <path d="M137 82 C121 64 96 75 99 96 C102 116 137 137 137 137 C137 137 175 108 174 90 C172 66 148 62 137 82Z"/>
  <path d="M83 179 C76 169 62 173 64 186 Q68 197 83 204 Q100 190 100 182 Q99 168 83 179Z"/>
  <path d="M202 168 C194 158 183 164 185 175 Q189 185 201 191 Q214 182 215 173 Q214 158 202 168Z"/>
  <path d="M229 207 C220 197 209 202 211 213 Q214 223 226 228 Q239 219 240 211 Q240 198 229 207Z"/>
 </g>
 <g fill="#ff238b" opacity=".65" filter="url(#lr-glow)">
  <circle cx="136" cy="97" r="22"/><circle cx="82" cy="187" r="12"/><circle cx="201" cy="173" r="11"/><circle cx="227" cy="214" r="11"/>
 </g>
 <path d="M106 91 Q111 80 119 81" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" opacity=".62"/>
 <path d="M44 207 Q82 239 124 258" fill="none" stroke="#fe71b6" stroke-width="3" opacity=".24"/>
 </svg>`;
const feature=(icon,title,description,klass)=>`<article class="discover-feature ${klass}">
 <div class="discover-feature-symbol" aria-hidden="true">${icon}</div>
 <div class="discover-feature-copy"><h2>${title}</h2><p>${description}</p></div>
 </article>`;

export function discoverHtml(hasInvitation=false,externalBrowserHelp=''){
 return `<div class="love-discover ${hasInvitation?'discover-invite':'discover-public'}" aria-label="${hasInvitation?'Invitation LoveRoll':'Présentation LoveRoll'}">
 <header class="discover-header">
  <div class="discover-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>
  <span class="discover-kicker">LA COMPLICITÉ SE JOUE À DEUX</span>
  <h1>Faites de chaque moment <em>une surprise.</em></h1>
  <p>Des jeux, des défis et des attentions pour créer des souvenirs inoubliables, à votre rythme.</p>
 </header>
 <section class="discover-showcase" aria-label="Un aperçu des surprises LoveRoll">
  <img class="discover-hero-premium" src="./assets/loveroll-invitation-hero-hd.webp" width="1630" height="870" alt="Un dé. Une surprise. Deux sourires. Deux dés noirs brillants aux cœurs roses lumineux sur du satin bordeaux. Des instants complices pour vous rapprocher encore plus." fetchpriority="high" loading="eager" decoding="async">
  <div class="discover-die-stage" aria-hidden="true"><span class="discover-heart-float">♡</span>${diceArt}<img class="discover-approved-die" src="./assets/loveroll-die-approved.webp" alt="" loading="eager"></div>
  <div class="discover-preview-copy">
   <small>♡ VOTRE PROCHAIN INSTANT</small>
   <h2>Un dé.<span>Une surprise.</span><em>Deux sourires.</em></h2>
   <p>Des instants complices pour vous rapprocher encore plus.</p>
  </div>
 </section>
 <section class="discover-gallery" aria-label="Votre univers LoveRoll">
  ${feature('<span class="discover-icon-dice">🎲</span>','Jeux','Des jeux ludiques pour pimenter votre quotidien.','discover-games')}
  ${feature('♡','Défis','Des défis sensuels ou coquins, à votre rythme.','discover-challenges')}
  ${feature('▦','Rendez-vous','Des idées de rendez-vous et des occasions spéciales.','discover-dates')}
  ${feature('🎁','Surprises','Des attentions uniques et des souvenirs à partager.','discover-surprises')}
 </section>
 <section class="discover-finale"><span class="discover-finale-art" aria-hidden="true">♡</span>
  <div><strong>UN ESPACE RIEN QU'À NOUS</strong><p>Rires, complicité, désir… et tellement plus encore.</p></div>
  <span class="discover-finale-heart" aria-hidden="true">♡</span>
 </section>
 <div class="discover-action-area">
  ${hasInvitation?'<button class="discover-accept" type="button" data-action="invite-start">♡ ACCEPTER L’INVITATION <span aria-hidden="true">›</span></button>':
  '<button class="discover-accept" type="button" data-action="show-auth">♡ COMMENCER NOTRE AVENTURE <span aria-hidden="true">›</span></button><button class="discover-login" type="button" data-action="show-auth">J’ai déjà un compte · Me connecter</button>'}
  ${hasInvitation?externalBrowserHelp:''}
  <p class="discover-privacy">♡ Votre rythme · Votre intimité · Réservé aux adultes de 18 ans et plus.</p>
 </div>
 </div>`;
}
