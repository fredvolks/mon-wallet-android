# Démarrage/reprise de Mon Wallet — une seule entrée

1. Lire `docs/MASTER_PROMPT.md` et `docs/WORK_CONTINUATION.md`.
2. Vérifier la branche courante, le commit parent, les PR actives, les fichiers modifiés et la dernière **APK effectivement publiée**. Ne pas considérer `main` comme la version distribuée : la référence confirmée du 2026-11-01 est `v0.2.71` SHA `33ac1ff9737a2f1216e351d27c3e736903ec5315`.
3. Si le propriétaire demande @Boss et ses agents, consulter réellement le connecteur, charger les méthodes utiles et noter les limites. Ne jamais simuler des agents ou des tests physiques.
4. Désigner un seul intégrateur et réserver un périmètre de fichiers. Préserver les branches concurrentes, en particulier les PR en chaîne, et les commits LoveRoll historiques sans les mélanger au code Mon Wallet.
5. Mettre en œuvre le prochain lot P0/P1 **réellement utile**, avec tests pertinents et impact sur le parcours complet. Utiliser un commit de référence vérifié.
6. Ouvrir une PR sur la bonne branche de base, observer les checks et ne revendiquer une release qu'avec une APK signée et un manifeste validés.
7. Mettre à jour `docs/WORK_CONTINUATION.md` après chaque lot, pas un autre prompt maître.

Commandes sur un poste avec Android SDK 35, JDK 17 et dépendances disponibles :
```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-build-cache --no-daemon
```

Avant release : vérifier les scénarios de porte-monnaie/FX, perte de réseau, widget 4×2 et installation de mise à jour **sur l'appareil ciblé**. Ne jamais joindre de keystore ni l'écrire dans les logs.
