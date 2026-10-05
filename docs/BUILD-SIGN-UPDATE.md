# Build, signature et mises à jour

Prérequis : JDK 17, Android SDK Platform 35 et Build Tools 35.0.0. Le Gradle Wrapper utilise Gradle 8.11.1, AGP 8.9.2 et Kotlin 2.1.20.

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
scripts/build-release.sh 0.2.4
```

L'APK debug est `app/build/outputs/apk/debug/app-debug.apk`; l'APK release signé est `app/build/outputs/apk/release/app-release.apk`. Pour signer localement, définir `MONWALLET_STORE_FILE` (chemin absolu du `.jks`), `MONWALLET_STORE_PASSWORD`, `MONWALLET_KEY_ALIAS`, `MONWALLET_KEY_PASSWORD` dans **l'environnement du processus**, jamais dans Git. Le paquet de clé livré séparément contient les informations nécessaires. Ne pas le publier.

Sur GitHub, ajouter les secrets `KEYSTORE` (JKS encodé en base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Variables publiques de connexion : `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `GOOGLE_WEB_CLIENT_ID`; `UPDATE_MANIFEST_URL` reste facultative. Le workflow de validation compile/tests chaque changement; un tag `v0.2.4` compile un APK et un AAB signés, les vérifie et joint `MonWallet-v0.2.4.apk` et `MonWallet-v0.2.4.aab` et `version.json` à une GitHub Release. La formule des codes est `majeur × 1 000 000 + mineur × 1 000 + correctif` : v0.1.0=1000, v0.2.3=2003. Garder le même certificat et un code toujours croissant.

L'app lit une adresse HTTPS stable de `version.json` sous Profil → Mises à jour. Le workflow publie `version.json` comme asset de chaque release; pour un **dépôt public**, l'URL stable peut être `https://github.com/OWNER/REPO/releases/latest/download/version.json`. Un dépôt privé requiert une autre distribution HTTPS authentifiée compatible avec l'app; les liens d'assets privés ne fonctionnent pas anonymement. Définir `UPDATE_MANIFEST_URL` lors d'un prochain build ou la saisir manuellement dans Profil.

Le manifeste décrit `versionCode`, `versionName`, `apkUrl`, `sha256`, `releaseNotes`, `mandatory`, `minimumSupportedVersion`. L'app vérifie HTTPS, empreinte SHA-256, ID Android, code supérieur et certificat identique avant de passer le fichier à l'installateur système. L'utilisateur confirme l'installation. La première autorisation « Installer des applications inconnues » peut demander de revenir dans l'app et toucher de nouveau Mettre à jour. Le schéma Room v1 → v2 est migré, sans `fallbackToDestructiveMigration`.
