# Mon Wallet v0.2.4

Application **Android native** en Kotlin, Jetpack Compose et Room. Identifiant `ca.monwallet.app`, Android 8.0+ (API 26). Le mode invité permet de saisir des investissements réels localement. Les fonctions cloud et certaines données de marché nécessitent une configuration externe.

## Installer sur un Samsung

1. Transférer `MonWallet-v0.2.4.apk` sur le téléphone et l'ouvrir avec **Mes fichiers**.
2. Autoriser l'installation depuis cette source lorsque Samsung le demande, puis confirmer l'installation Android.
3. Choisir la langue, puis **Utiliser l’app sans compte**. Aucun portefeuille ni watchlist n'est créé par défaut pour une nouvelle installation. Créer le premier portefeuille directement depuis l’écran vide, puis ajouter les achats avec la date réelle, le prix, les frais et, pour USD, le taux CAD/USD de la transaction.
4. Exporter une sauvegarde via **Profil → Export et sauvegarde → Exporter JSON complet**. Le CSV n'exporte que les transactions. Android Backup est désactivé pour l'app.

Pour mettre à jour une installation existante vers v0.2.4, **ouvrir le nouvel APK et choisir Mettre à jour**. Ne pas désinstaller l'app : les portefeuilles déjà créés restent dans la base locale. Le même identifiant et la même clé de signature sont utilisés, avec un `versionCode` supérieur. Exporter une sauvegarde JSON avant la mise à jour reste prudent tant que la synchronisation réelle n'est pas validée.

## Fonctions intégrées

- Portefeuilles multiples et vue Tous; achats et ventes partielles, dividendes, frais, dépôts/retraits et transactions rétroactives; prix moyen, capital investi, P&L et historique. Les calculs utilisent `BigDecimal`.
- Interface sombre, nouvel accueil avec logo portefeuille, choix Français/English, vue compacte, watchlists denses, recherche, Marchés distincts des positions, fiches titre, rapports et graphiques quand les cours existent.
- Cache local; cours et historique par fournisseur remplaçable; alertes locales, nouvelles/earnings/analystes selon la couverture, quatre tailles de widgets configurables par portefeuille qui n'affichent que le P&L du jour.
- Mode invité, connexion OAuth/code courriel après configuration Supabase, synchronisation avec tombstones et conflits explicites, appareils, biométrie, import/export JSON et CSV, mise à jour APK avec contrôles SHA-256 et certificat.

## Configuration externe

Le projet Supabase **dédié à Mon Wallet est déployé** au Canada; le schéma et la fonction de suppression de compte y sont actifs. La build v0.2.4 intègre son URL, sa clé publique et l'ID public du client Google Web. Les clients OAuth Web et Android sont créés et le propriétaire a activé Google dans Supabase. Le mode invité fonctionne immédiatement. **La connexion Google doit encore être essayée sur Samsung**. Voir [backend](docs/BACKEND.md) et [connexion](docs/AUTH.md). L'utilisateur ne saisit aucune URL ou clé dans l'application. Ne jamais intégrer une clé `service_role` à l'APK.

Les cours par défaut viennent d'une interface Yahoo Finance non officielle. Finances utilise les dépôts SEC et le résumé Nasdaq pour les actions américaines identifiées; Analystes utilise le consensus Nasdaq lorsqu'il existe. Les titres canadiens non couverts affichent un état d'indisponibilité plutôt qu'un chiffre inventé. Pour DOL, GURU, BLDP et XEQT au Canada, un lien ouvre une page Seeking Alpha vérifiée dans le navigateur, sans importer ses données. Les logos vérifiés utilisent le domaine officiel et un cache mémoire/disque. Ces routes publiques peuvent changer ou limiter l'accès; consulter [API](docs/API.md) et [limites](docs/LIMITATIONS.md).

## Sources, tests et releases

Ouvrir ce dossier dans Android Studio avec JDK 17 et Android SDK 35. Exécuter `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Voir les documents [architecture](docs/ARCHITECTURE.md), [sync](docs/SYNC.md), [build, signature et mises à jour](docs/BUILD-SIGN-UPDATE.md), [mises à jour rapides](docs/FAST-UPDATES.md), et [changelog](CHANGELOG.md). La signature privée est fournie séparément et doit rester hors dépôt.

## v0.2.1

Vérification réseau en direct : AAPL dispose d'états SEC et d'un consensus Nasdaq; logos récupérés pour AAPL, MSFT, TSM, MCD, DOL, GURU, BLDP et XEQT. Le parcours Google sur un Samsung et la restauration cloud ne sont pas validés tant que les clients OAuth Google ne sont pas configurés. Voir [changelog](CHANGELOG.md).

## v0.2.0

Vente avec aperçu du coût et du gain réalisé, poids dynamique, donut par titre, calcul sectoriel lorsque la composition réelle est connue, graphiques Lightweight Charts™ locaux, sélection de widget adaptée au nombre de portefeuilles, APK et AAB signés. Voir [graphique](docs/TRADINGVIEW.md), [widgets](docs/WIDGETS.md), [Google Play](docs/GOOGLE-PLAY.md) et [mises à jour rapides](docs/FAST-UPDATES.md). Les allocations internes des ETF et les données financières détaillées ne sont pas inventées.
