# MON WALLET — ÉTAT COURANT DU TRAVAIL

Dernière mise à jour : **2026-11-01** · Projet `fredvolks/mon-wallet-android`.

## Source de vérité
- Dernière APK officielle examinée : `v0.2.71` ; tag/commit `33ac1ff9737a2f1216e351d27c3e736903ec5315`, release publiée le 27 octobre 2026.
- Branche d'intégration de cette série de travaux : `feat/widget-pnl-privacy-20261027` (tête vérifiée : même SHA).
- Branche isolée de reprise : `work/monwallet-god-stabilization-20261101`, créée au SHA ci-dessus.
- `main` diverge de `v0.2.71` : **ne pas fusionner ni remplacer automatiquement**. Les PR #139–#154 sont en chaîne; consulter leurs bases avant toute opération.
- Les PR LoveRoll encore dans ce dépôt sont hors scope; ne pas les fermer/merger sans mandat distinct.

## Consultations réelles
- BOSS GOD MODE consulté via Atelier — MOON; 20 expertises disponibles.
- Méthodes chargées : Architecture, QA, DevOps, Performance, Métier/Produit, Sécurité.
- Aucune fiche `Mon Wallet` disponible dans `list_applications` Atelier à la date de contrôle. **Aucune mission Atelier autonome déclarée**; liaison future seulement si demandée explicitement.

## Travail du présent lot (branche de reprise)
- **FAIT (code)** : `MarketHoliday.calendar` ne considère plus CRYPTO, FX, COMMODITY/FUTURE comme des actions US du seul fait de leur devise USD, évitant un P&L quotidien artificiellement neutralisé.
- **FAIT (code)** : tests `nonEquityUsdInstrumentsDoNotInheritStockExchangeHolidays` et `wholeWalletPercentCountsClosedUsStocksWhileCryptoKeepsItsDailyMove` : Thanksgiving US, crypto, FX, matières premières/futures, P&L du jour et dénominateur du portefeuille global.
- **FAIT (docs)** : création de l'unique `docs/MASTER_PROMPT.md` et de `docs/WORK_START.md`.
- **À TESTER** : PR [#155](https://github.com/fredvolks/mon-wallet-android/pull/155) ouverte vers `feat/widget-pnl-privacy-20261027`; exécuter/contrôler les tests JUnit/Gradle sur le SHA final. Aucun test du code modifié n'a été exécuté localement dans cette session (pas d'accès réseau Gradle).
- **NON PUBLIÉ** : ce lot n'est ni fusionné ni inclus dans un nouvel APK. Aucune installation Samsung effectuée.

## Prochaines étapes (pas de doublons)
1. **P0 / QA** — vérifier la PR, voir les checks par SHA, corriger les échecs; tester les dates TSX/CSE/NYSE et le cas Bitcoin Thanksgiving. Si CI absente, décrire le blocage et exécuter dès environnement disponible.
2. **P0 / DevOps** — établir stratégie de réconciliation sûre de `main`, `v0.2.71` et des branches de PR; préparer une base canonique avec comparaison et rollback, sans merge destructif.
3. **P0 / Comptabilité** — jeu de fixtures P&L total/jour CAD/USD, ventes, frais, jours fériés, contributions, transactions rétroactives; comparer aux relevés anonymisés ou cas attendus.
4. **P1 / Performance mobile** — mesurer sur S25 Ultra : lancement, cours foreground 5 s, widget et cache historique; corriger causes mesurées et valider avant/après.
5. **P1 / IA** — tester 200 candidats US/Canada, exclusions hard, reprise par groupes de 20, authentification et quotas sans dépenses payantes non approuvées.
6. **P2 / UX** — vérifier les états critiques et la cohérence visuelle sur écrans déjà implémentés.

## Livrables et preuves à ajouter
- URL de PR et statut CI du SHA de tête.
- Résultats unitaires et instrumentés avec dispositif et date.
- Preuve de build/release/install distincte de la simple ouverture de PR.

**Principe** : privilégier le développement de l'application dans son ensemble et les chemins utilisateur complets; éviter les micro-corrections répétées, les tests sans fin avant avancement, et les nouveaux prompts maîtres concurrents.
