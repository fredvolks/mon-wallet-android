# Vérification v0.2.3 — 2026-10-04

- Client Google Web `158439565502-oa8ksq1phh6mcrq60ftbqhm0diotcdv0.apps.googleusercontent.com` intégré dans le `BuildConfig` release; ID du client Android distinct configuré dans Google Cloud pour `ca.monwallet.app` et le SHA-1 release.
- Le propriétaire a enregistré les deux IDs et le secret Web dans Supabase. La lecture publique de `/auth/v1/settings` renvoie `external.google=true` et `disable_signup=false`. Le secret n'est pas dans le dépôt ni dans l'APK.
- Tests JVM/Robolectric : 46 réussis, zéro échec. Build APK et AAB release signé réussi; `apksigner` et `jarsigner` vérifient les signatures.
- APK : `ca.monwallet.app`, `versionCode=2003`, `versionName=0.2.3`, certificat SHA-256 `9fa4232613c4cccf611e8114fd121da630bbe0f8f2e6886814fde6225b10750d` identique aux versions précédentes.
- SHA-256 APK : `ba2efbda2280b30ab7a81461d481fb5fe7e9c737a3b9f2c124393a47f6ed35a7`. AAB : `556e0cd24bd28a6aced7f0edbbc788b3f5779d1db991d395f58b028f6fc0ae1d`.

La connexion Google sur le Samsung, l'ajout éventuel du compte dans Google Cloud → Audience → utilisateurs test, la persistance de session, la migration invité et la restauration cloud **restent à vérifier avec le compte réel**. Le build et l'activation du fournisseur ne démontrent pas à eux seuls que ces scénarios fonctionnent.
