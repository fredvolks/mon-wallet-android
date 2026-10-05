# Vérification v0.2.2 — 2026-10-04

## Backend distant

- Projet Mon Wallet `prwlnxbqttxfjxpehmok` créé dans Fred forge, région `ca-central-1`, distinct de « fredvolks's Project ».
- Trois migrations appliquées : `wallet_schema`, `user_profiles`, `secure_profile_trigger`. Tables `wallet_records`, `wallet_devices`, `wallet_profiles` : RLS activé, pas de privilège SELECT anonyme.
- Fonction Edge `delete-account` déployée et active avec vérification JWT.
- Audit de sécurité Supabase après le correctif : aucune alerte. Aucune donnée utilisateur ajoutée; zéro profil avant la première connexion.

## Android

- Le build intègre l'URL du projet et sa clé **publique** `sb_publishable_…`; aucun secret administrateur. Les variables d'environnement peuvent remplacer les valeurs publiques au besoin.
- Build incrémental signé réussi en 1 min 24 s, avec APK et AAB v0.2.2. Tests JVM/Robolectric : 46 réussis, aucun échec. APK : `ca.monwallet.app`, `versionCode=2002`, certificat SHA-256 `9fa4232613c4cccf611e8114fd121da630bbe0f8f2e6886814fde6225b10750d` identique à v0.2.1; vérifications `apksigner` et `jarsigner` réussies.
- La configuration OAuth Google (clients Web et Android, secret côté Supabase, activation du fournisseur) n'existe pas encore. Le bouton Google reste indisponible dans cette version. Une connexion réussie, la migration invité, la session persistante, le test RLS avec deux comptes et la restauration sur un autre appareil restent non vérifiés.
- L'installation et l'essai sur un Samsung physique ne sont pas réalisés dans cet environnement. Les données AAPL et les logos ont été testés côté réseau et parseurs en v0.2.1, sans contrôle visuel sur le téléphone.

Voir `docs/AUTH.md` pour les empreintes de signature et les étapes OAuth à terminer. Ne pas annoncer Google comme fonctionnel avant le test complet demandé.
