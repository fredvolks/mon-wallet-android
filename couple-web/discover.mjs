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


const editorialIcons = {
 soiree: `<svg viewBox="0 0 76 76" aria-hidden="true" focusable="false"><defs><linearGradient id="lr-wine" x1="0" y1="0" x2="0.7" y2="1"><stop stop-color="#fff6fc"/><stop offset="1" stop-color="#fa559e"/></linearGradient><linearGradient id="lr-rose" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#e72e81"/><stop offset=".65" stop-color="#970f48"/><stop offset="1" stop-color="#410720"/></linearGradient></defs><g fill="none" stroke="url(#lr-wine)" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"><path d="M12 13h22c-.5 14-2 24-11 26-9-2-10.5-12-11-26Z"/><path d="M23 39v15M15 55h16M43 13h22c-.5 14-2 24-11 26-9-2-10.5-12-11-26Z"/><path d="M54 39v15M46 55h16"/></g><path d="M14 25h18c-1.5 8-4 12-9 12s-7.5-4-9-12ZM45 25h18c-1.5 8-4 12-9 12s-7.5-4-9-12Z" fill="url(#lr-rose)" opacity=".95"/><path d="m34 12 4-6 4 6 7 1-6 5" fill="none" stroke="#ff8cbb" stroke-width="1.8"/><path d="M35 19c4 1 6 0 8-2" stroke="#ffb7d8" stroke-width="1.5" fill="none"/></svg>`,
 surprise: `<svg viewBox="0 0 76 76" aria-hidden="true" focusable="false"><defs><linearGradient id="lr-box" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#ff90c7"/><stop offset=".49" stop-color="#be1f67"/><stop offset="1" stop-color="#56092b"/></linearGradient></defs><path d="M16 33h44v30H16Z" fill="url(#lr-box)" stroke="#ffb1d5" stroke-width="2"/><path d="M12 27h52v12H12Z" fill="#ea3485" stroke="#ffc6df" stroke-width="2"/><path d="M33 27h10v36H33Z" fill="#ffb8db"/><path d="M37 8c-15-10-21 4-12 11 6 5 13 8 13 8s-3-11-1-19Zm2 0c15-10 21 4 12 11-6 5-13 8-13 8s3-11 1-19Z" fill="#bc145c" stroke="#ffbddb" stroke-width="2"/><path d="m37 27 1-14" stroke="#fff1f8" stroke-width="2"/></svg>`,
 avent: `<svg viewBox="0 0 76 76" aria-hidden="true" focusable="false"><defs><linearGradient id="lr-day" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f14394"/><stop offset="1" stop-color="#47051e"/></linearGradient></defs><rect x="13" y="14" width="51" height="53" rx="5" fill="#2a071b" stroke="#ff6bab" stroke-width="2.4" transform="rotate(-8 38 40)"/><rect x="21" y="9" width="42" height="49" rx="4" fill="url(#lr-day)" stroke="#ffbddb" stroke-width="2" transform="rotate(6 42 34)"/><text x="41" y="43" text-anchor="middle" fill="#fff3fa" font-size="26" font-family="Georgia,serif" font-weight="bold" transform="rotate(6 41 34)">25</text><path d="m11 11 4-5 4 5M64 58l3-5 3 5" fill="none" stroke="#ff8bbe" stroke-width="2"/></svg>`,
 couple: `<svg viewBox="0 0 76 76" aria-hidden="true" focusable="false"><defs><filter id="lr-heart-halo" x="-80%" y="-80%" width="260%" height="260%"><feGaussianBlur stdDeviation="3"/></filter></defs><path d="M24 17C10 5-1 24 9 36l24 25 18-19C64 28 45 8 32 21l-8-4Z" fill="none" stroke="#ff2e97" stroke-width="5" filter="url(#lr-heart-halo)" opacity=".95"/><path d="M24 17C10 5-1 24 9 36l24 25 18-19C64 28 45 8 32 21l-8-4Z" fill="none" stroke="#ff8bc3" stroke-width="2.6"/><path d="M44 30C34 20 24 33 29 42l17 20 19-19c9-12-7-24-16-11l-5-2Z" fill="none" stroke="#ff8ac3" stroke-width="2.6"/><path d="M44 30C34 20 24 33 29 42l17 20 19-19c9-12-7-24-16-11l-5-2Z" fill="none" stroke="#ff2e97" stroke-width="4" filter="url(#lr-heart-halo)" opacity=".6"/></svg>`
};

export function discoverHtml(hasInvitation=false,externalBrowserHelp=''){
 const features=[
  ['soiree','Soirée','Défis à tour de rôle','soiree'],
  ['surprise','Surprise','Votre activité du mois','surprise'],
  ['avent','Avent','25 cases à ouvrir ensemble','avent'],
  ['couple','Couple','Un espace commun, discret et synchronisé','couple']
 ];
 return '<div class="love-discover discover-v58 discover-v59 '+(hasInvitation?'discover-invite':'discover-public')+'" aria-label="'+
 (hasInvitation?'Invitation LoveRoll':'Présentation LoveRoll')+'">'+
 '<header class="discover-header"><div class="discover-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>'+
 '<span class="discover-kicker">'+(hasInvitation?'UNE INVITATION POUR VOTRE COUPLE':'LA COMPLICITÉ SE JOUE À DEUX')+'</span>'+
 '<h1 class="discover-editorial-title"><span>Ce soir,</span><span><b>on</b> <em>joue</em></span><em>ensemble.</em></h1>'+
 '<p class="discover-editorial-intro">LoveRoll transforme vos envies en défis, surprises et rituels complices.</p></header>'+
 '<section class="discover-editorial-stage" aria-label="Les quatre expériences LoveRoll">'+
 '<div class="discover-editorial-die" aria-hidden="true"><div class="discover-editorial-satin"></div>'+
 '<img src="./assets/loveroll-invitation-hero-hd.webp" alt="" decoding="async" loading="eager" fetchpriority="high">'+
 '<span class="discover-editorial-sparkle discover-sparkle-one">♡</span>'+
 '<span class="discover-editorial-sparkle discover-sparkle-two">✧</span></div>'+
 '<div class="discover-editorial-list" aria-label="Découvrir les fonctions">'+
 features.map(([icon,title,description,kind])=>
  '<article class="discover-editorial-item discover-editorial-'+kind+'">'+
   '<span class="discover-editorial-icon" aria-hidden="true">'+editorialIcons[icon]+'</span>'+
   '<div><h2>'+title+'</h2><p>'+description+'</p></div></article>').join('')+
 '</div></section>'+
 '<section class="discover-editorial-bottom"><h2>Prêts à lancer le dé ?</h2>'+
 (hasInvitation?'<button class="discover-accept" type="button" data-action="invite-start">ACCEPTER L’INVITATION</button>':
  '<button class="discover-accept" type="button" data-action="show-auth:signup">COMMENCER NOTRE AVENTURE</button>'+
  '<button class="discover-login" type="button" data-action="show-auth:signin">J’ai déjà un compte · Me connecter</button>')+
 (hasInvitation?externalBrowserHelp:'')+
 '<p class="discover-privacy">'+(hasInvitation?'Invitation privée · LoveRoll':'Votre rythme · Votre intimité · 18 ans et plus')+'</p>'+
 '</section></div>';
}
