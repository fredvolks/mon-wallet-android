# Graphiques Lightweight Charts

La fiche titre charge TradingView Lightweight Charts™ 5.2.1 depuis `app/src/main/assets/lwc/` dans une WebView locale. Le JavaScript n'est pas chargé depuis un CDN. Le rendu est séparé des données : `TradingViewChartView.kt` sérialise uniquement les points fournis par `MarketDataProvider`, puis la page `chart.html` dessine ligne, aire, chandeliers ou volume. L'accès réseau de cette WebView est bloqué; aucune interface JavaScript native n'est exposée. Le texte d'attribution, LICENSE et NOTICE sont embarqués.

Les chandeliers demandent open/high/low réels et le volume demande des volumes réels. Quand ces champs manquent, l'app affiche « données indisponibles » au lieu de reconstruire des valeurs. Le zoom, le déplacement, le curseur et le reset utilisent les interactions du moteur de graphique.

Le bundle officiel 5.2.1 a été téléchargé depuis le paquet npm `lightweight-charts`, vérifié par son SHA-512 de publication, puis copié dans les assets. La licence du paquet reste dans `assets/lwc/LICENSE`. Les cotations historiques existantes proviennent de l'adaptateur Yahoo non officiel ou d'un fournisseur déjà configuré; aucune API TradingView de marché n'est utilisée.
