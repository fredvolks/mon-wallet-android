# Mises à jour plus rapides

Pour une retouche du code Android, il faut recompiler et signer l'APK. Le téléphone conserve les données locales si l'identifiant `ca.monwallet.app`, le certificat, un `versionCode` croissant et les migrations Room restent compatibles.

## Boucle de travail

1. Garder le même dossier et les caches Gradle/SDK entre deux changements. Le premier build d'un environnement vide télécharge plusieurs centaines de Mo; ce téléchargement n'est pas une durée normale pour une retouche.
2. Modifier plusieurs petits éléments dans une même version correctif. Pour prévisualiser vite, lancer `./gradlew :app:assembleDebug --no-build-cache`.
3. Pour un APK/AAB installable : définir les quatre variables de signature décrites dans [BUILD-SIGN-UPDATE.md](BUILD-SIGN-UPDATE.md), puis lancer `scripts/build-release.sh 0.2.3 --quick`. Cela réutilise les sorties incrémentales, compile les deux formats, vérifie les signatures et place les fichiers dans `dist/`. Pour une release finale, enlever `--quick` afin de lancer aussi les tests.
4. La CI exécute tests et compilation APK/AAB en **un appel Gradle** sur le tag `vX.Y.Z`. Configurer les secrets du dépôt avant de pousser un tag; aucun dépôt distant n'est inclus dans cette livraison.
5. Installer le nouvel APK par-dessus l'ancien, sans désinstaller l'application. Sauvegarder les données JSON avant une évolution de schéma.

Les données de cours se rafraîchissent sans nouvel APK; modifier l'interface, la logique ou une ressource locale nécessite une nouvelle version. Le script supprime uniquement les sorties dex générées qui ont produit une erreur D8 lors des tests, puis réessaie proprement seulement si le build incrémental échoue. Les temps dépendent du matériel et de l'état du cache; il ne promet pas un délai fixe.

Un dépôt Git persistant avec CI évite de reconstituer le SDK et les dépendances à chaque session. Le projet est livré sous forme d'archive et de bundle Git; il n'est pas encore relié à un dépôt GitHub distant.
