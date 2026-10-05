# Déploiement Supabase dédié

Le projet **Mon Wallet** (`prwlnxbqttxfjxpehmok`) est actif dans l'organisation Fred forge, région Canada Central. Le schéma est dans `supabase/migrations/20261003190958_wallet_schema.sql`, le profil minimal lié à `auth.users` dans `supabase/migrations/20261004180000_user_profiles.sql`, et le correctif des droits du déclencheur dans `supabase/migrations/20261004191500_secure_profile_trigger.sql`. Ces trois migrations ont été appliquées au projet distant. La fonction `supabase/functions/delete-account` est déployée avec vérification JWT. Le projet distinct « fredvolks's Project » n'a pas été modifié.

Pour déployer une copie du projet avec Supabase CLI, après avoir choisi explicitement le projet cible :

```bash
supabase login
supabase link --project-ref REF_DU_PROJET_MON_WALLET
supabase db push
supabase functions deploy delete-account
```

La fonction vérifie elle-même le bearer token, l'utilisateur et la session active avant suppression. `SUPABASE_SERVICE_ROLE_KEY` reste exclusivement dans l'environnement de la fonction. Ne la saisir **jamais** dans Android. Renseigner l'URL HTTPS, la clé `sb_publishable_…` ou `anon` et le client ID Web Google uniquement dans les variables publiques du build. Mettre les paramètres d'authentification comme dans [AUTH.md](AUTH.md).

Le serveur stocke `wallet_records` et `wallet_devices`, par `user_id`. RLS et privilèges limitent lectures/mutations au propriétaire connecté. Les fonctions `register_device`, `list_devices`, `revoke_device`, `push_record` et `pull_records` vérifient également la session JWT et l'état de révocation. Les écritures utilisent une version attendue (CAS); les suppressions restent des tombstones. L'effacement de `auth.users` supprime les lignes reliées par cascade.

L'audit de sécurité distant ne relève aucune alerte, les trois tables ont RLS activé et `anon` n'a pas le privilège SELECT. Aucun utilisateur réel n'a encore ouvert de session : le test de synchronisation et l'isolation entre deux comptes attendent la configuration OAuth et deux comptes distincts. Test local PostgreSQL **sur une base jetable uniquement** : appliquer `supabase/tests/bootstrap-local.sql`, puis les migrations dans l'ordre, puis `supabase/tests/security.sql` avec `psql -v ON_ERROR_STOP=1`. Le bootstrap crée des rôles fictifs et ne doit jamais être déployé dans Supabase.
