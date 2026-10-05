# Versions

## Mon Wallet v0.2.8 — 2026-10-05 (Découvrir, Watchlist, cours et widget)

- Widget 4x2 : nombre de titres configurable par instance (Auto ou 1 à 6), cinq par défaut, ordre manuel, densité et aperçu adaptés à la taille réelle.
- Découvrir : six périodes, tableau compact, colonnes sélectionnables et réordonnables, filtres et presets, idées, métriques de régularité, drawdown, concentration et score momentum. Le mode analystes croise objectifs, couverture, consensus, dispersion et momentum.
- Watchlist : lignes compactes, période mémorisée par liste ou partagée, presets de colonnes, ordre et tri, sparkline liée à la période, cours hors séance discrets quand fournis.
- Cours au premier plan : actualisation ciblée avec cache partagé, arrêt au second plan, provenance et fraîcheur explicites; aucun cours inconnu n'est déclaré temps réel. Valorisation hors séance en option.
- Sources de données : clés FMP et Twelve Data configurables dans Profil → Source des données. Découvrir requiert un accès FMP autorisé; le classement est limité aux candidats renvoyés par bourse et à la disponibilité des historiques et données analystes. Le polling reste prudent faute de droit streaming démontré.
- Tests du classement anti-pump, de l'historique cinq ans, du PRE/AFTER, de la fraîcheur et de la valorisation. Validation Android et production d'un APK dans la CI.

## Mon Wallet v0.2.6 — 2026-10-05 (widget premium 4x2)

- Nouveau layout 4x2 à deux colonnes avec rendement total dominant, P&L du jour en capsule, trois titres sélectionnés et mini graphique lorsque des points réels existent.
- Prix, variation, vrais logos vérifiés et repli ticker; les titres restent visibles avec le graphique et un cours absent reste indiqué comme indisponible.
- Historique réel 1S puis 1M utilisé si les points intrajournaliers manquent; sans série valide, le graphique disparaît et la place revient au contenu.
- Style « Mixte premium », aperçu du portefeuille réel, paramètres et ordre conservés par widget, confidentialité et heure de mise à jour.
- Actualisation et réglages ciblent l'instance touchée; toucher le fond ouvre son portefeuille.
- Le build de validation s'installe séparément (`ca.monwallet.app.preview`) pour tester le 4x2 sans toucher aux données de la version installée. Il ne remplace pas un APK release signé avec la clé d'origine.

## Mon Wallet v0.2.5 — 2026-10-05 (widgets et routage Finances)

- Réglages indépendants par widget, aperçu, rendement total du moteur comptable, titres détenus sélectionnables et réordonnables, confidentialité et rafraîchissement.
- Graphique masqué quand les points manquent et lignes de titres ouvrant leur fiche.
- Route Canada/USA par symbole, place, devise et type; SEC/Nasdaq restent les sources américaines actives.
- Une source canadienne ou FINVIZ Elite doit disposer d’un accès autorisé côté backend. Aucun accès n’est configuré pour DOL.TO : ses chiffres Finances restent indisponibles dans cette version.

## Mon Wallet v0.2.4 — 2026-10-05 (recherche des titres canadiens)

- Liens directs vers les fiches Seeking Alpha vérifiées pour DOL, GURU, BLDP et XEQT (cotation canadienne), depuis Finances et Analystes. Les pages s’ouvrent dans le navigateur; aucune donnée Seeking Alpha n’est importée dans l’app.
- Résolution stricte du symbole, de la place, de la devise et du type; PHOS/CSE et les homonymes non vérifiés n’ouvrent pas une mauvaise fiche.
- Workflow GitHub de validation corrigé pour le SDK Android préinstallé. La connexion Google sur Samsung reste à vérifier.

## Mon Wallet v0.2.3 — 2026-10-04 (Google OAuth)

- ID public du client OAuth Google Web intégré à l'APK. Clients Web et Android créés pour `ca.monwallet.app` et le certificat release; fournisseur Google activé dans Supabase par le propriétaire.
- APK et AAB signés avec le certificat des versions précédentes; `versionCode=2003`.
- **Essai réel à terminer** : ajouter le compte à la liste des utilisateurs test dans Google Cloud, vérifier la connexion et la session sur Samsung, puis la migration du mode invité et la synchronisation. La configuration seule ne prouve pas encore que la connexion fonctionne.

## Mon Wallet v0.2.2 — 2026-10-04 (activation du backend personnel)

- Projet Supabase Mon Wallet dédié au Canada créé dans Fred forge, schéma de synchronisation appliqué, profils automatiques et fonction de suppression du compte déployés.
- Audit de sécurité distant sans alerte : la fonction privilégiée de profil est passée dans un schéma non exposé, les droits anonymes des profils ont été retirés.
- URL et clé publique du projet incluses dans la configuration du build; aucune clé privée n'est embarquée. Le courriel peut joindre le projet, sous réserve du modèle OTP et des limites d'envoi à vérifier avec un compte réel.
- **Google encore en attente** : le client OAuth Google Cloud Web/Android et le fournisseur Google Supabase ne sont pas configurés. Le parcours de connexion, la migration invité et la restauration ne sont pas validés sur un Samsung. Cette livraison ne prétend pas satisfaire le critère Google.

## Mon Wallet v0.2.1 — 2026-10-04 (correctifs partiels)

- Finances : dépôt SEC EDGAR et résumé Nasdaq par identité de marché, historique annuel/trimestriel, champs réellement présents, cache et bouton Réessayer. AAPL vérifié sur les réponses réseau.
- Analystes : consensus Buy/Hold/Sell et objectifs Nasdaq, source/date, états distincts d'absence et d'erreur.
- Logos : domaines émetteurs vérifiés, icônes réelles dans les composants partagés, cache mémoire/disque et initiales si la correspondance est incertaine.
- Connexion Google : sélecteur Android Credential Manager et échange du jeton ID avec Supabase, transfert invité proposé avant connexion. Les réglages Supabase techniques ont été retirés de l'interface utilisateur.
- **Activation Google en attente** : aucun projet Supabase Mon Wallet ni client OAuth Google n'est encore configuré; le parcours complet sur Samsung n'a pas été vérifié. Cette version ne prétend pas satisfaire le critère Google du prompt.

## Mon Wallet v0.2.0 — 2026-10-04

- Bouton direct de création du premier portefeuille; onglets adaptés au nombre de comptes.
- Vente depuis la fiche titre, vente totale, parts disponibles à la date choisie, aperçu produit/coût/gain réalisé et blocage des ventes rétroactives impossibles.
- Poids calculé sur la valeur actuelle, répartition en donut et concentration; sectorisation des ETF uniquement si leurs allocations réelles sont fournies.
- Graphiques TradingView Lightweight Charts™ en assets locaux avec OHLC et volumes fournis par le marché, sans données inventées.
- Choix de portefeuille du widget adapté; messages de source sans demande de clés utilisateur.
- Script de build incrémental, tests, APK/AAB signés et workflow GitHub Actions.


## Mon Wallet v0.1.1 — 2026-10-04

- Nouvel écran de bienvenue sombre, logo portefeuille, aperçu de portefeuille explicitement marqué comme tel, sélecteur de langue dès l'ouverture et dans Profil → Paramètres.
- Ressources Android françaises et anglaises pour l'accueil, la navigation et les libellés statiques des principaux écrans; présentation des nombres et dates selon la langue.
- Nouveaux invités sans portefeuille ni watchlist automatique. Les données des installations existantes sont conservées; aucun schéma Room n'a changé.
- Code de version 1001, signé avec le certificat de v0.1.0 pour installation par-dessus.

## Mon Wallet v0.1.0 — 2026-10-03

Première version Android native : portefeuilles, transactions datées, calculs CAD/FX, historique, watchlists, marchés, fiches titre, screener, alertes, widgets, sauvegarde/import et mise à jour signée. Schéma Supabase et client de synchronisation inclus, mais cloud non déployé dans cette livraison. Voir `docs/LIMITATIONS.md`.
