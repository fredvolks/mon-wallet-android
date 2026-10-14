// Écran vitrine public : aucune authentification et aucune donnée de couple.
export function discoverHtml(hasInvitation=false){
 const back=hasInvitation?'Revenir à mon invitation':'Retour';
 return `<main class="love-discover" aria-label="Découvrir LoveRoll, aperçu de l’application">
  <header class="discover-header">
   <div class="discover-brand"><span>Love</span><strong>Roll</strong><i>♡</i></div>
   <span class="discover-kicker">LA COMPLICITÉ SE JOUE À DEUX</span>
   <h1>Faites de chaque moment <em>une surprise.</em></h1>
   <p>Des dés, des défis et des attentions pour créer des souvenirs, à votre rythme.</p>
  </header>
  <section class="discover-showcase" aria-label="Aperçu des dés et des défis">
   <div class="discover-preview-top"><span>♡ VOTRE SOIRÉE</span><span class="discover-live">DÉMO VISUELLE</span></div>
   <div class="discover-preview-center">
    <div class="discover-dice" aria-hidden="true"><span>♥</span><i>••</i><i>••</i></div>
    <div class="discover-preview-copy"><small>Votre prochain instant</small><strong>Un dé.<br>Une surprise.<br><em>Deux sourires.</em></strong><span class="discover-category">♡ SENSUEL <b>·</b> ♡ COQUIN</span></div>
   </div>
   <div class="discover-dice-bar"><span><strong>D6</strong> Soirée</span><span><strong>D20</strong> Rendez-vous</span><span><strong>D35</strong> Défis</span></div>
  </section>
  <div class="discover-section-label">♡ UN APERÇU DE VOTRE UNIVERS</div>
  <section class="discover-gallery" aria-label="Fonctions principales">
   <article class="discover-tile discover-tile-challenge">
    <div class="discover-tile-heading"><span>🎲</span><strong>Défis à deux</strong></div>
    <div class="discover-challenge-demo"><small>CARTE SURPRISE</small><b>Le moment<br>complice</b><span>Un défi à découvrir ensemble</span><i>♡ 7 MIN</i></div>
    <p>Des cartes sensuelles ou coquines, avec des règles qui respectent vos envies.</p>
   </article>
   <article class="discover-tile discover-tile-calendar">
    <div class="discover-tile-heading"><span>♡</span><strong>Dates et surprises</strong></div>
    <div class="discover-calendar-demo"><div>OCTOBRE <strong>2026</strong></div><div class="discover-calendar-grid"><span>12</span><span>13</span><span class="glow">14♡</span><span>15</span><span>16</span><span class="glow">17♥</span><span>18</span></div></div>
    <p>Un calendrier, des occasions spéciales et vos petits rendez-vous.</p>
   </article>
   <article class="discover-tile discover-tile-private">
    <div class="discover-tile-heading"><span>♥</span><strong>Votre match privé</strong></div>
    <div class="discover-match-demo"><span>♡</span><span class="discover-line"></span><span>♡</span></div>
    <p>Deux comptes liés, chacun garde son espace et ses préférences.</p>
   </article>
   <article class="discover-tile discover-tile-advent">
    <div class="discover-tile-heading"><span>✦</span><strong>Fêtes et calendrier de l’Avent</strong></div>
    <div class="discover-advent-demo"><span>01</span><span>♡</span><span>03</span><span>04</span><span>♥</span></div>
    <p>Des attentions pour les grands jours, et 25 surprises à découvrir.</p>
   </article>
  </section>
  <div class="discover-footer-note">♡ Votre rythme · Votre complicité · Votre intimité</div>
  <button class="discover-back" type="button" data-action="discover-back">♡ ${back} ←</button>
  <p class="discover-privacy">Aucune connexion nécessaire pour consulter cet aperçu. Pour adultes de 18 ans et plus.</p>
 </main>`;
}
