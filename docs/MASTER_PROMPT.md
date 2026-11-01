# PROMPT MAÎTRE OFFICIEL — MON WALLET

> Projet Android existant : **Mon Wallet**. Date de référence : 2026-11-01.
> Ce document est l'unique prompt maître de ce dépôt. Ne pas le dupliquer.
> Le travail s'effectue dans le dépôt réel, pas dans une maquette ni dans un autre projet.

## 0. Mission permanente

Développer et stabiliser **Mon Wallet**, application Android native de suivi d'investissements, avec portefeuilles CAD/USD, transactions historiques, P&L cohérent avec la valeur totale du portefeuille, cours Canada/États-Unis, watchlists, rapports/calendrier, widgets Android et recherche financière/IA.

- Dépôt : `https://github.com/fredvolks/mon-wallet-android`
- Package : `ca.monwallet.app`
- Technologies vérifiées : Kotlin, Jetpack Compose, Room, WorkManager, OkHttp, Supabase et GitHub Actions.
- Plateforme prioritaire : Android, en particulier Samsung Galaxy S25 Ultra; vérifier différentes résolutions et tailles de police.
- Langue : français en priorité, anglais maintenu si déjà supporté; devise de référence du portefeuille : CAD, avec coûts historiques, cours et FX exacts.
- Distribution : APK signé / AAB, mise à jour sécurisée depuis le manifeste stable `version.json`.
- Dernière version **publiée et inspectée au 2026-11-01** : `v0.2.71`, commit `33ac1ff9737a2f1216e351d27c3e736903ec5315`.
- `main` **n'est pas** la base complète de `v0.2.71`; la comparaison GitHub est divergente. Ne jamais développer sur `main` aveuglément ni l'écraser.

**Objectif prioritaire** : avoir des résultats financiers fiables, des écrans fluides et des releases réellement installables, avant d'empiler de nouveaux modules.

## 1. Instructions de reprise

1. Lire ce document, `docs/WORK_START.md`, puis `docs/WORK_CONTINUATION.md`.
2. Examiner les références GitHub, commits, PR ouvertes, version distribuée et SHA réellement ciblé. Préserver tout travail non fusionné, y compris les PR `#139` à `#154`, qui forment une chaîne de branches.
3. Récupérer les décisions récentes du propriétaire; ses instructions explicites priment sur les suggestions du présent document.
4. Désigner **un seul intégrateur du code** par lot, avec branches séparées et fichiers réservés. Les autres expertises fournissent des revues/tests et ne font pas des modifications concurrentes.
5. Choisir un lot **utile à l'application entière**, implémenter, contrôler les chemins voisins, ouvrir une PR ciblée et mettre à jour `docs/WORK_CONTINUATION.md`.
6. Continuer par les prochains lots indépendants pendant la session active, sans multiplier les micro-PR ni relancer la même analyse en boucle.
7. Distinguer **codé / testé localement / CI verte / intégré / APK publié / installé sur appareil**. Une PR verte ne garantit pas une APK publiée. Ne jamais prétendre à un test physique sans résultat réel.

Aucun secret, jeton, mot de passe de signature ou clé privée ne doit être inscrit dans GitHub, une mission Atelier, une capture ou une réponse.

## 2. BOSS et experts Atelier — consultation réelle uniquement

Quand le propriétaire demande `@Boss`, `BOSS` ou `les agents`, appeler réellement le connecteur Atelier et `consult_boss` avec le niveau demandé : SOFT, MEDIUM ou GOD MODE. **Ne jamais simuler leur consultation ou leur travail**. Charger les méthodes des spécialistes pertinents (`get_agent`).

Pour la stabilisation courante en **GOD MODE**, spécialités utiles :
- **Architecture et revue de code** : dépendances des écrans, contrats, calculs, synchronisation et branches.
- **Qualité et tests** : scénarios comptables, congés US/Canada, réception de cours, cache, interruption et régressions.
- **Performance** : rapidité visible, pollution des caches, requêtes simultanées, batterie et taux d'erreur mesurés.
- **DevOps / CI/CD** : branche de release, tests par SHA, signature, APK, manifeste et retour arrière.
- **Expert métier / produit** : règles de portefeuille, devises, coût moyen, cash-flows, P&L et finance.
- **Sécurité** : Auth, sessions, RLS, données locales, permissions et secrets.
- **Mobile/UX**, au besoin : widgets `RemoteViews`, navigation, lisibilité et appareil physique.

Atelier n'avait **aucune fiche Mon Wallet** au contrôle du 2026-11-01; le BOSS et les méthodes ont été consultés sans mission autonome. Ne pas annoncer une mission Atelier enregistrée sans fiche et reçu; ne pas créer cette fiche sans demande explicite de liaison.

## 3. Règles financières indispensables

### 3.1 Transactions, valeur et P&L
- Portefeuilles multiples, vue globale, achats/ventes et ventes partielles, coût moyen, commissions, dividendes, dépôts/retraits, historique modifiable et opérations rétroactives.
- Calculs financiers en `BigDecimal`, pas en `Float` ou `Double`; conserver les opérations originales.
- **P&L total** : valeur complète du portefeuille (positions + liquidités, gains réalisés inclus) moins les apports externes nets suivant le grand livre, avec devises et frais corrects.
- **P&L quotidien en %** : utiliser comme dénominateur la base du **portefeuille complet**, y compris la valeur des titres sur des marchés fermés; les titres réellement en congé contribuent 0 au mouvement quotidien, mais ne disparaissent pas du capital de référence.
- Ne pas transformer cours manquants / FX manquant en 0 $ ou en rendement inventé. Préciser `indisponible` et pourquoi.
- Tenir compte des différences de fermeture et jours fériés **TSX/CSE vs NYSE/NASDAQ**. La devise USD ne suffit **jamais** à dire qu'un instrument est une action US : crypto, Forex et futures ont d'autres calendriers.
- PRE/AFTER hors séance doit rester distinct de la performance régulière du jour, avec référence horodatée et remise à zéro correcte; valider vendredi→lundi et jours fériés.
- Calendrier/reports : clôtures historiques (pas cours PRE/AFTER courants), apports et retraits neutralisés pour le rendement, taux USD/CAD de la période, achat rétroactif recalculé. Un TWR ajusté des flux sans heure de flux est une estimation, pas une performance certifiée de courtier.

### 3.2 Données boursières et fluidité
- Cours Canada/USA, prix et variation visibles sans recharger manuellement, watchlist, logos par identité de société; indicateurs de fraîcheur et disponibilité du fournisseur.
- Le rafraîchissement toutes ~5 s **ne garantit pas une nouvelle cotation toutes les 5 s** : afficher la vraie source, l'âge de la donnée, PRE/AFTER et l'état du réseau. Aucun faux label temps réel.
- Éviter la relecture complète des historiques JSON et le rendu intégral de l'écran à chaque tick; garder un cache valide à l'échec, stopper proprement les jobs en arrière-plan.
- Profilage sur Samsung physique : démarrage, écran portefeuille, watchlist 20/200 titres, widget 4×2, cache froid/chaud, 4G/Wi-Fi, batterie, erreurs HTTP 429/404, hors ligne. Donner avant/après mesurés, pas un gain imaginaire.
- Widgets indépendants, choix du portefeuille, mise en page 4×2, cours, P&L du jour, PRE/AFTER, graphique et bouton œil masquant conjointement les montants concernés. Tester la confidentialité et le redimensionnement.

### 3.3 Découvrir et analyste IA
- Préfiltre sur sociétés cotées **États-Unis et Canada**, toutes capitalisations; exclure OTC, cours en dessous de 1 CAD/USD de cotation, biotech/développeurs de médicaments et placements non pertinents; pas de liste statique substituée silencieusement.
- Retenir jusqu'à **200 titres**, noter les exclusions, métriques manquantes, bourse et date des chiffres. Une classification sectorielle n'est pas une garantie d'exclusion biotech parfaite.
- Analyse IA explicite par groupe de 20 : modèle/coûts et quotas contrôlés côté serveur, état reprise, pas de dépenses IA automatiques mensuelles non approuvées.
- Les priorités IA sont des **priorités de recherche**, pas des probabilités de rendement, ordres d'achat ou prix cibles. Les données non vérifiées (news, guidance, cash flow) restent non vérifiées.
- Séparer les données personnelles du portefeuille et les renseignements publics transmis au service; aucune clé privée dans l'APK.

## 4. Priorités de développement global — ordre

**P0 — Source de vérité & protection des finances**
1. Réconcilier la divergence `main` / `v0.2.71` et la chaîne de PR sans supprimer, rebaser destructivement ou fusionner des branches étrangères. Préparer une base canonique de release, puis décommissionner les workflows anciens après validation.
2. Scénarios déterministes d'achats, ventes, frais, FX, retrait/dépôt, calendrier, P&L portefeuille complet et marchés fermés. Vérifier les différences contre des relevés de courtier anonymisés ou fixtures connues.
3. Tester export JSON, réimport, migration Room et sync : aucune suppression ni perte de positions lors d'une mise à jour.

**P1 — Stabilité, données, écran et widget**
4. Profilage, corrigés sur les vrais goulots de performances, ralentissements et plantages; tests de cache incomplet, 404, backoff, recompositions et reprise réseau.
5. Parcours réels portefeuille / watchlist / détails / rapports / calendrier / widget, y compris états vides, erreurs, PRE/AFTER, jour férié, confidentialité, gros caractères.
6. Auditer le backend Supabase de production en lecture seule avant toute mutation; RLS entre deux utilisateurs, jetons et fonctions IA. Les observations de sécurité ne deviennent pas une certification.

**P2 — Recherche financière**
7. Valider scan réel 200 candidats, frontières $1, biotech, titres Canada/US, quotas, coûts et reprise de groupes IA sur compte de test.
8. Améliorer les fiches financières et les secteurs par données vérifiables, sans présenter un endpoint non contractuel comme garanti.
9. Nettoyer documentation, traductions et CI/CD après consolidation fonctionnelle.

## 5. Format de fin de lot et règles de vérification

Pour chaque lot, fournir :
- SHA de départ et branche, fichiers et effets réels, risques et scénarios de test.
- Tests exécutés et résultats vérifiables sur ce SHA, échecs/essais non faits, distinction appareil physique / émulateur.
- PR et CI, état release et URL exacte **uniquement si l'artefact existe**.
- État des tâches suivant : `FAIT`, `EN COURS`, `À TESTER`, `BLOQUÉ`.
- Mise à jour de `docs/WORK_CONTINUATION.md` (un seul état courant), sans ouvrir de nouveaux prompts maîtres.

**Interdits** : réécrire toute l'app, inventer des cours/rendements, faire passer une maquette pour une fonction, promettre du travail asynchrone, engager des dépenses non approuvées, mélanger des données LoveRoll dans Mon Wallet ou publier sans APK signée vérifiée.

## 6. Contexte du lot GOD MODE — 2026-11-01

Point de départ : commit publié `33ac1ff9737a2f1216e351d27c3e736903ec5315` (`v0.2.71`), reprise sur une branche de travail isolée, parent `feat/widget-pnl-privacy-20261027`. Audit confirmé : branche `main` divergente, projet Supabase actif, IA `analyze-stock-screen` déployée, PR liées non fusionnées, essais physiques et réconciliation courtier non certifiés. Premier correctif ciblé : ne pas appliquer le calendrier des actions US aux crypto, FX et futures simplement parce qu'ils sont libellés en USD. Ne pas dire qu'il est publié sans nouvelle release.

La suite concrète et les reçus sont maintenus **uniquement** dans `docs/WORK_CONTINUATION.md`.
