# Widgets

Les quatre tailles de widget et leur configuration par `appWidgetId` proviennent de la version précédente. Le sélecteur de portefeuille affiche zéro option lorsqu'il n'existe aucun portefeuille, seulement le vrai portefeuille lorsqu'il n'en existe qu'un, puis « Tous » et la liste lorsqu'il en existe plusieurs. Les widgets utilisent les données locales et n'affichent pas les totaux globaux par défaut. Les parcours sur lanceur Samsung restent à vérifier sur appareil.

## 4x2 premium

Le fournisseur `Widget4x2` emploie `wallet_widget_wide.xml`, avec un rendement total du même `Wallet.result(portfolio)` que l'application à gauche et jusqu'à trois positions, dans l'ordre choisi pour cet `appWidgetId`, à droite. Le prix n'apparaît que si l'option est active, si l'écran est assez large et si les montants ne sont pas masqués. Les cours manquants restent inconnus (`—`). Les logos vérifiés viennent du cache mémoire/disque de `OfficialLogoProvider`; à défaut, les initiales du ticker sont visibles.

Le mini graphique tente les points intrajournaliers en cache. Si moins de deux points sont utilisables, il utilise l'historique calculé à partir des transactions et prix réels (1S, puis 1M). Sans série valide, il est masqué; aucun point artificiel n'est créé. Les préférences anciennes du style Mixte sont migrées une fois vers « Mixte premium » sans perdre le portefeuille, les titres sélectionnés, leur ordre ni le mode confidentialité. La roue dentée modifie l'instance et le rafraîchissement transmet son `appWidgetId` au worker.

Le build debug porte `ca.monwallet.app.preview` et le nom « Mon Wallet • Aperçu » pour coexister avec la version release précédente. Chaque installation possède ses propres données; l'utilisateur peut importer sa sauvegarde JSON dans l'aperçu. Le build release garde `ca.monwallet.app` et nécessite la clé originale pour remplacer une installation existante.
