# Architecture et données

`Services` assemble la UI Compose, `Repository`/Room, le domaine `Engine`, `AuthManager`, `SyncManager`, `Router` pour les cours, notifications et mises à jour. `WalletViewModel` expose les opérations. `RefreshWorker` actualise périodiquement le cache selon les contraintes Android.

Room contient `wallet_records` pour les objets privés JSON (`portfolio`, `security`, `transaction`, `watchlist`, `watch_item`, `alert`, `notification_preference`, `event`, `setting`) et `market_cache` hors synchronisation. Chaque objet a un ID stable, propriétaire, horodatages, drapeau de modification, version serveur et tombstone. La migration Room v1 → v2 ajoute la colonne de conflit sans effacer les lignes; le schéma est versionné dans `app/schemas`.

Les montants sont des `BigDecimal`. `Transaction` conserve le prix original, devise, taux FX, frais, dates effectives et compte. `Engine` rejoue les événements par date et portefeuille; le prix moyen est dérivé, les achats historiques restent séparés. Un achat rétroactif recalcule la série selon les cours historiques disponibles. Un dépôt finance l'encaisse; un achat sans encaisse est traité comme nouveau capital injecté.

Le P&L du jour tient compte de l'encaisse et des flux de la séance. Il exige le cours courant, la clôture précédente et, pour USD, le FX de la même séance; sinon il vaut `—`. Les snapshots sont recalculés à la demande, sans table matérialisée. `MarketDataProvider` permet de basculer Yahoo/Twelve Data; Finnhub et FMP complètent les fiches et le screener. Quatre `RemoteViews` configurables utilisent le cache local.

La v0.2.0 ajoute `domain/Allocation.kt` (poids en valeur de marché, pondérations fonds vérifiées), `ui/AllocationDonut.kt` et `ui/TradingViewChartView.kt`. La WebView locale rend le bundle JS sans pont natif ni accès réseau; les points réels proviennent de `marketdata/Market.kt`. `Engine.salePreview` calcule le coût et le gain de vente à la date choisie en réutilisant le grand livre chronologique.
