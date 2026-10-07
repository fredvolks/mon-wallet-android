# News et analyse IA

Le fil Android lit `news_articles` et `news_analysis` via l'API publique Supabase et récupère aussi, pour les titres suivis, les nouvelles Yahoo déjà utilisées dans la fiche titre. Ces dernières sont non officielles et restent « Non analysée ». L'APK ne contient aucune clé OpenAI ou fournisseur payant.

## Backend

- Migration : `supabase/migrations/20261007103000_news_pipeline.sql` crée les articles et analyses, index, droits SELECT et politiques RLS. Aucun enregistrement de portefeuille n'est ajouté à ces tables partagées.
- `ingest-news` interroge Financial Modeling Prep, normalise source, date, symboles et URL, déduplique, puis écrit les nouvelles brutes. `FMP_API_KEY` doit être défini comme secret Supabase côté serveur et le forfait du fournisseur doit autoriser ces appels.
- `analyze-news` lit les articles en attente, utilise la Responses API avec un schéma JSON strict, écrit une analyse française et reprend les échecs après une heure. `OPENAI_API_KEY` doit être défini comme secret Supabase côté serveur. Les appels entrants aux deux fonctions exigent la clé backend dans l'en-tête `apikey`; ne jamais la placer dans l'APK ni dans ce dépôt.
- Pour une ingestion autonome, planifier un appel POST de `ingest-news` avec la clé backend gardée dans un secret du planificateur Supabase. Planifier également `analyze-news` (par exemple toutes les 15 minutes) pour la reprise des erreurs. Ne pas exposer la clé dans une requête SQL en clair, un log ou un workflow public. Sans ces deux secrets et la planification, le backend ne peut pas fournir de nouvelles IA.

## Vérification

1. Appeler sans autorisation chaque fonction et vérifier HTTP 401.
2. Configurer les secrets côté Supabase et déclencher l'ingestion; vérifier que les nouvelles ont une URL HTTPS, une date et une source réelle.
3. Déclencher l'analyse; vérifier les lignes `ANALYZED` et les champs structurés, puis l'absence de seconde analyse pour le même `content_hash`.
4. Vérifier un échec OpenAI : l'article brut reste lisible et `ERROR` est repris après délai.
5. Tester deux comptes et un invité : « Pour moi » suit seulement les titres détenus ou en Watchlist du compte actif; les analyses publiques ne donnent accès à aucune donnée personnelle.
6. Tester les news DOL, RY, SHOP, AAPL et MSFT avec le forfait réel. L'existence des fonctions ne garantit pas leur couverture par le fournisseur.

État au 7 octobre 2026 : migration et fonctions déployées; aucun secret FMP ou OpenAI ni tâche planifiée n'a été validé, donc l'analyse IA automatique ne peut pas être déclarée fonctionnelle. Les tests d'intégration fournisseur et notifications sur appareil restent nécessaires.
