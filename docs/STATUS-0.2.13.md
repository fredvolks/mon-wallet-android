# État de la livraison 0.2.13

Cette version conserve le schéma Room v2 et les enregistrements existants. Les réglages Watchlist et Portefeuille ajoutés sont des `Setting` dans `wallet_records`, donc aucune migration SQL locale n'est nécessaire. Les ordres manuels sont les `WatchItem.order` existants; le réordonnement est désormais atomique. Les préférences du widget restent isolées par `appWidgetId`.

## Fonctionnel dans ce code

- Portefeuille : achats/ventes et P&L via `Engine`; vue compacte en tableau à ticker fixe, colonnes, presets et tri; cours hors séance appliqués à la valorisation selon le réglage existant.
- Watchlist : table compacte et colonnes alignées pour PRE/AFTER, période, sélection persistante, ordre manuel atomique, panneau rapide vers la fiche complète.
- Découvrir : classement historique multi-périodes, pénalités de concentration et drawdown et stratégie analyste hérités des versions précédentes. Aucun score de conviction IA n'est actuellement calculé.
- Quotes : `Repository.observeQuote(s)` et cache Room partagés; provider Yahoo non officiel avec étiquettes de délai/cache, rafraîchissement en premier plan et `WorkManager` périodique en arrière-plan. Le fournisseur n'offre pas de contrat de streaming garanti dans cette intégration.
- Widget : un vrai `AppWidgetProvider`, chiffres de la dernière séance et 3 indices via quotes mises en cache, cinq titres par défaut, graphique intrajournalier réel s'il est disponible, aucun montant de valeur totale du wallet dans le 4x2.

## Dépendances restantes pour le cahier des charges intégral

- **Google Auth** : client public configuré et Supabase Auth présent; essai release avec compte Google autorisé et deux utilisateurs réels non effectué. Le mode invité et la migration locale existent, mais la synchronisation entre deux appareils n'est pas prouvée.
- **Supabase / RLS** : projet `prwlnxbqttxfjxpehmok`; `wallet_records`, `wallet_devices`, `wallet_profiles` et policies utilisateur déployés. Aucun schéma `news_articles`, `news_analysis`, `convictions` n'est déployé à ce stade.
- **OpenAI backend** : pas de fonction `ingest-news` ou `analyze-news` déployée, ni de `OPENAI_API_KEY` accessible via les outils de ce projet. Aucune clé OpenAI n'est dans l'APK. La page News personnalisée, l'analyse et les notifications IA automatiques restent indisponibles.
- **News Canada / fondamentaux / analystes** : Yahoo news de la fiche titre et SEC/Nasdaq pour des émetteurs US existent. Le fournisseur canadien agréé n'est pas configuré; DOL/GURU/PHOS/XEQT peuvent manquer de financiers ou de news. Aucun chiffre n'est interpolé pour remplir ces écrans. Il faut un contrat/provider Canada + USA stable et ses identifiants côté backend.
- **Convictions IA** : nécessite les articles et dépôts datés avec preuves, l'historique financier et une analyse structurée côté backend; rien de tout cela ne peut être déclaré validé avec une liste de titres fictive.
- **Matériel** : pas de Galaxy S25 Ultra accessible aux tests; mesure Robolectric et capture émulateur ne certifient pas le placement exact One UI. Google release, PRE/AFTER aux heures réelles et notifications doivent être vérifiés sur appareil.

## Reproduction

CI `.github/workflows/build-apk.yml` : `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease`. `watchlist-visual.yml` capture un émulateur API 35. La signature d'une mise à jour exige le certificat original (SHA-256 `9fa4232613c4cccf611e8114fd121da630bbe0f8f2e6886814fde6225b10750d`) et ne doit pas exposer le keystore. L'APK release garde `ca.monwallet.app`, le debug utilise `ca.monwallet.app.preview2`.
