# Vérification v0.2.1 — 2026-10-04

## Exécuté

- Build Android après nettoyage, puis rebuild incrémental final : APK signé et AAB signé; `versionCode=2001`, `versionName=0.2.1`, package `ca.monwallet.app`.
- Empreinte SHA-256 du certificat APK : `9fa4232613c4cccf611e8114fd121da630bbe0f8f2e6886814fde6225b10750d`, identique à v0.2.0. `apksigner verify` et `jarsigner -verify` réussissent.
- Tests JVM/Robolectric : **46 tests, 0 échec**. Les nouveaux tests couvrent la résolution des marchés, le mapping SEC US GAAP/IFRS, l'absence de fausses valeurs et le consensus Nasdaq.
- Vérification réseau directe avec `scripts/check-live-providers.py` : AAPL, MSFT, NVDA, TSM, MCD, GURU.TO, PHOS.CN, BLDP.TO, XEQT.TO, DOL.TO. AAPL : dépôt SEC avec revenus, bénéfice, BPA, cash et cash flow; réponse Nasdaq actuelle Buy 15, Hold 9, Sell 4, objectif moyen 334,9 USD. Les icônes de domaines officiels renvoient des images pour tous ces exemples.

## Non vérifié / bloquant

- Le parcours AAPL **dans l'interface d'un Samsung physique** n'a pas été exercé. La vérification réseau et les tests de mapping prouvent la disponibilité et la transformation des données, pas l'affichage réel sur l'appareil.
- Aucun projet Supabase Mon Wallet dédié ni configuration Google Cloud OAuth n'était accessible. L'APK ne contient pas de paramètres cloud; le bouton Google affiche une erreur simple. La connexion, création de profil serveur, migration invité, reconnexion et isolation RLS entre deux comptes n'ont pas passé les tests bout en bout.
- L'installation APK sur Samsung et l'import AAB dans Google Play Console ne sont pas vérifiés ici. Les signatures et métadonnées de package sont vérifiées localement.
- Les routes publiques Nasdaq peuvent changer; plusieurs marchés canadiens et métriques ETF restent sans fournisseur financier vérifié.

Ce build est un correctif utilisable en mode invité pour tester Finances, Analystes et logos. **Il ne satisfait pas encore le critère de validation Google et ne clôt pas la tâche demandée.**
