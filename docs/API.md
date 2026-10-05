# Données de marché v0.2.1

`MarketDataProvider` fournit cours, historique, recherche et nouvelles. `Router` utilise Yahoo Finance pour les cours et l'historique (source non officielle), et conserve l'adaptateur Twelve Data d'anciennes installations. Aucun utilisateur n'a de clé à saisir.

## Finances

`ProviderSymbolResolver` exige ticker, marché, devise et type d'actif. Pour une action américaine de même ticker/marché, `SecFilingsProvider` télécharge l'index officiel ticker/CIK/exchange puis `/api/xbrl/companyfacts/CIK##########.json`. `SecFacts` lit les dépôts US GAAP ou IFRS et les unités USD, choisit les périodes annuelles/trimestrielles réellement déposées et écarte les comparatifs futurs ou mal datés. Revenus, bénéfice, BPA, cash, cash flow, dette et historique ne sont affichés que si leurs faits existent. Dette nette, FCF, marge et croissance ne sont calculés que si chaque composant et la période correspondante sont présents. Les valeurs annuelles portent leur date, sans être annoncées comme TTM.

`NasdaqSummaryProvider` complète les valeurs de marché (capitalisation, volume, 52 semaines, dividende) lorsque disponibles. En cas d'échec d'un provider, l'autre peut encore alimenter la fiche. L'ancien adaptateur Finnhub demeure un secours uniquement pour une installation déjà configurée. Le cache Room des données Finances est valable six heures et le bouton Réessayer force le réseau. Un échec reste une erreur visible; aucune réponse vide n'est mise en cache.

## Analystes

`NasdaqAnalystProvider` charge le consensus et les objectifs de prix de Nasdaq. Les nombres Buy/Hold/Sell sont ceux de la réponse, le total affiché est leur somme; une catégorie Strong Buy/Strong Sell absente n'est pas inventée. La date vient du dernier consensus historique. Le cours courant est celui du fournisseur de cours déjà affiché, et le potentiel est calculé seulement si cours et objectif existent. Cache Room une heure; états chargement, absence de couverture et erreur distincts. Les routes publiques Nasdaq utilisées ici ne constituent pas une API contractuelle : elles doivent être surveillées et pourront nécessiter un backend/fournisseur sous licence pour une diffusion large.

## Logos

`OfficialDomains` associe uniquement des identités composées connues à des domaines d'émetteurs contrôlés. `OfficialLogoProvider` récupère l'icône de ce domaine par le service de favicon Google, la conserve en mémoire et sur disque, puis l'affiche sans l'étirer. Le composant `Logo` est partagé entre Portefeuille, Watchlist, recherche, fiche, Découvrir et historique. Le fallback initiales est utilisé si la résolution est incertaine ou le téléchargement échoue. Un ticker seul ne donne jamais droit au logo d'une société homonyme.

## Couverture et limites

La recherche externe Seeking Alpha est un lien de navigation vers des fiches canadiennes vérifiées : DOL:CA, GURU:CA, BLDP:CA et XEQT:CA. Elle ne lit, ne copie, ne met en cache et ne présente aucune donnée Seeking Alpha dans Mon Wallet. PHOS/CSE n’a pas de fiche canadienne vérifiée dans ce mapping. Les conditions de Seeking Alpha interdisent l’extraction automatisée du site; une intégration des chiffres demanderait une licence de données/API distincte.

- AAPL, MSFT, NVDA et MCD : dépôts US GAAP; TSM peut avoir des dépôts IFRS libellés en USD par l'émetteur. Les dates et unités exactes restent visibles.
- GURU.TO, DOL.TO, PHOS.CN, BLDP.TO et XEQT.TO : logos d'émetteurs connus; les données SEC/Nasdaq américaines ne sont pas attribuées à leurs instruments canadiens. Les ETF n'affichent jamais une fiche financière d'entreprise.
- L'accès SEC respecte une fréquence faible et le cache; sa politique de déclaration d'agent utilisateur doit être adaptée à un contact de production. Les données analystes publiques peuvent être modifiées, retardées ou indisponibles.
- `scripts/check-live-providers.py` exécute une vérification réseau de référence; les tests Kotlin couvrent aussi les collisions de symboles et le mapping sans nombres codés en dur dans l'APK.

Le look-through sectoriel reçoit un dictionnaire de pondérations réelles par fonds dans `Allocation.bySector`. Aucun fournisseur d'allocations ETF vérifiées n'est connecté; XEQT et les autres fonds restent « répartition détaillée indisponible ».
