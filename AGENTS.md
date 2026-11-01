# Instructions de développement — Mon Wallet

Lire **d'abord** `docs/WORK_START.md`, puis l'unique prompt maître `docs/MASTER_PROMPT.md` et l'état courant `docs/WORK_CONTINUATION.md`.

- Dépôt actuel : `fredvolks/mon-wallet-android` ; application Android Kotlin/Compose/Room ; version publiée inspectée `v0.2.71`.
- `main` diverge de la branche release; ne pas coder depuis `main` sans vérifier la base. Protéger les branches de PR historiques.
- Un seul intégrateur. Tests pertinents, code réel, pas de déclarations de succès sans preuves.
- Ne pas exposer de secrets, keystore, données réelles des portefeuilles; aucune modification Supabase de production sans autorisation ciblée.
- Si @Boss et agents sont demandés : véritable consultation Atelier, pas d'agent fictif ni de réussite inventée.
- Conserver `docs/MASTER_PROMPT.md` comme **unique** prompt maître.
