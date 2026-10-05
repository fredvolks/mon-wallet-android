# Versions

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
