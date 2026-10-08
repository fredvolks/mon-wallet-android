# News et analyse IA

Le fil Android lit `news_articles` et `news_analysis` via l'API publique Supabase et récupère aussi, pour les titres suivis, les nouvelles Yahoo déjà utilisées dans la fiche titre. Ces dernières sont non officielles et restent « Non analysée ». L'APK ne contient aucune clé OpenAI ou fournisseur payant.

## Backend

- Migration : `supabase/migrations/20261007103000_news_pipeline.sql` crée les articles et analyses, index, droits SELECT et politiques RLS. Aucun enregistrement de portefeuille n'est ajouté à ces tables partagées.
- `ingest-news` interroge Financial Modeling Prep, normalise source, date, symboles et URL, déduplique, puis écrit les nouvelles brutes. `FMP_API_KEY` doit être défini comme secret Supabase côté serveur et le forfait du fournisseur doit autoriser ces appels.
- `analyze-news` lit les articles en attente, utilise la Responses API avec un schéma JSON strict, écrit une analyse française et reprend les échecs après une heure. `OPENAI_API_KEY` doit être défini comme secret Supabase côté serveur. Les appels entrants aux deux fonctions exigent la clé backend dans l'en-tête `apikey`; ne jamais la placer dans l'APK ni dans ce dépôt.
- La migration `20261007111000_news_schedule.sql` installe `pg_net` et `pg_cron`, génère un jeton de tâche dans Vault et programme `ingest-news` aux 30 minutes et `analyze-news` aux 15 minutes. Les tâches restent **désactivées** jusqu'à la réussite des tests fournisseur. Les fonctions vérifient le jeton par un RPC accessible seulement à `service_role`; ni l'APK ni GitHub ne reçoivent le jeton.

## Vérification

1. Appeler sans autorisation chaque fonction et vérifier HTTP 401.
2. Configurer les secrets côté Supabase et déclencher l'ingestion; vérifier que les nouvelles ont une URL HTTPS, une date et une source réelle.
3. Déclencher l'analyse; vérifier les lignes `ANALYZED` et les champs structurés, puis l'absence de seconde analyse pour le même `content_hash`.
4. Vérifier un échec OpenAI : l'article brut reste lisible et `ERROR` est repris après délai.
5. Tester deux comptes et un invité : « Pour moi » suit seulement les titres détenus ou en Watchlist du compte actif; les analyses publiques ne donnent accès à aucune donnée personnelle.
6. Tester les news DOL, RY, SHOP, AAPL et MSFT avec le forfait réel. L'existence des fonctions ne garantit pas leur couverture par le fournisseur.

État au 7 octobre 2026 : les deux secrets ont été ajoutés par le propriétaire, et les migrations, fonctions et tâches sont déployées. L'appel réel aux deux flux FMP (`stock-latest` et `press-releases-latest`) retourne **HTTP 402**. La Responses API OpenAI retourne **HTTP 429** avec `credit_balance_exhausted`. Aucune nouvelle n'a été ingérée ni analysée. Les deux tâches sont désactivées, pour éviter les appels répétés. Après activation du forfait FMP nécessaire et ajout de crédits API OpenAI, refaire un test réel puis activer les tâches `monwallet-news-ingest` et `monwallet-news-analyze`. Les notifications sur appareil restent à vérifier avec une véritable analyse HIGH/CRITICAL.
