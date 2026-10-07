# Widget Android 4x2

`Widget4x2` est un vrai `AppWidgetProvider` (`widget_4x2.xml`, `wallet_widget_wide.xml`). Chaque `appWidgetId` a ses réglages et son propriétaire dans les préférences `widget_config`; `onDeleted` les supprime. Le portefeuille, le mode privé, le nombre et l'ordre des titres, les actions de toucher et le rafraîchissement manuel restent distincts par instance.

`Wallet.result(portfolio)` calcule le montant et le pourcentage de la dernière séance ainsi que le rendement total. Ce moteur est partagé avec le portefeuille. Les cours hors séance US peuvent changer la valorisation si le réglage global est activé, sans mélanger la variation régulière avec PRE/AFTER. La fermeture du marché ne vide pas le cache de la dernière séance.

Les trois indices correspondent strictement à `^GSPC`, `^IXIC` et `^DJI` du catalogue. Ils sont rafraîchis par le worker existant, puis lus depuis le cache des quotes. Une valeur absente reste `—`. Le graphique du jour n'utilise que les points intrajournaliers réels en cache; s'il n'y en a pas assez ou si la hauteur manque, la vue le masque. Aucun graphique hebdomadaire n'est présenté comme intrajournalier.

Les prix des titres sont affichés sans suffixe CAD/USD, avec l'indicateur PRE/AFTER seulement si une impression réelle existe. Le widget ne montre jamais la valeur totale du portefeuille. Le footer d'actualisation est masqué en 4x2 aux tailles ordinaires pour laisser la place au graphique et aux indices. L'application n'ouvre pas de flux WebSocket pour le widget : cache, `WorkManager` et action ↻ seulement.

Le provider déclare `minWidth=250dp`, `minHeight=150dp` et le redimensionnement. Les tests Robolectric mesurent notamment 360×160 dp avec cinq titres et 360×172 dp avec six. Une validation sur One UI / Galaxy S25 Ultra reste nécessaire avant de prétendre à une équivalence exacte avec le visuel de référence.
