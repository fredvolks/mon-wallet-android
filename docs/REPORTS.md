# Rapports historiques

La page Rapports reconstruit les clôtures depuis les transactions locales et les séries `Point` du cache Room. Elle ne remplace jamais une clôture passée par une cotation actuelle. Un prix ou un taux USD/CAD manquant rend la période partielle; aucune valeur zéro n'est inventée. Les historiques 5 ou 10 ans sont demandés au fournisseur configuré lors de l'ouverture de la période correspondante, puis conservés dans le cache existant. Un fournisseur sans historique autorisé laisse l'état indisponible.

## Grand livre et rendement

- Le capital net suit `Engine.calculate`: dépôts et retraits explicites, plus financement implicite d'achats ou de frais lorsque l'encaisse était insuffisante. Un achat déjà financé par l'encaisse n'est pas compté une deuxième fois.
- Pour chaque date valorisée : `P&L = valeur de clôture − valeur précédente − variation du capital net`. Une vente et un dividende restent dans l'encaisse; ils ne sont pas un retrait du portefeuille. Le P&L et la valeur sont en CAD au taux historique.
- Le rendement quotidien ajusté est `P&L / (valeur précédente + flux externe net)`. Les sous-périodes sont chaînées par multiplication des facteurs quotidiens et la courbe est remise à zéro à sa première valorisation. La date des transactions ne contient pas l'heure ni une valorisation au moment du flux : tout flux du jour est supposé intervenir avant la clôture. Ce rendement quotidien est donc une **estimation ajustée des flux**, et pas un TWR exact lorsqu'un apport ou retrait arrive en cours de séance.
- Le rendement personnel (XIRR), s'il existe assez de dates et de flux, utilise les apports négatifs, les retraits positifs et la valeur terminale positive. Il est annualisé et ne remplace pas le rendement principal.
- Un retrait complet, une séance sans cours exploitable, une devise sans FX historique ou une durée antérieure à l'existence du portefeuille ne génèrent pas de performance artificielle.

## Benchmarks et calendrier

Les séries S&P 500 (`^GSPC`), NASDAQ (`^IXIC`), TSX (`^GSPTSE`) et XEQT (`XEQT.TO`) utilisent des clôtures réelles. Les indices en USD sont convertis en CAD aux deux dates comparées. La base de chaque série est 0 % au début sélectionné; les courbes représentent les prix et n'intègrent pas les dividendes. Sans clôture ou FX de départ et d'arrivée, la comparaison est indisponible.

Le calendrier ne donne un P&L quotidien que pour une journée valorisée avec cours de marché. Les week-ends sans séance restent vides; un dépôt pendant le week-end modifie le capital, pas le rendement de marché. Toucher une date montre ses flux, sa valeur et les contributions des titres quand les deux valorisations sont présentes.

## Recalcul et données

Rapports dépend de la liste des transactions et des séries historiques observées par `StateFlow`. Une transaction rétroactive, une correction du cache de cours ou du FX déclenche un recalcul hors du thread UI. Les séries historiques restent en cache Room après fermeture et mise à jour de l'application. Aucun changement de schéma Room n'est nécessaire. Le CSV exporte seulement les dates calculées du portefeuille et de la période sélectionnés.

Tests unitaires : dépôt sans effet de rendement, dépôt/retrait, dividende, vente partielle, transaction rétroactive, FX historique, week-end, XIRR, benchmark et couverture insuffisante. Les essais visuels sur Samsung Galaxy S25 Ultra nécessitent un appareil connecté; les builds CI compilent et exécutent les tests JVM.
