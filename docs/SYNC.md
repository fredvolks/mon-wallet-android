# Synchronisation et sauvegarde

Room est la source locale. Après connexion, `SyncManager` enregistre la session de l'appareil, pousse chaque objet modifié vers `push_record`, puis lit les pages de `pull_records`. Le serveur compare `expected_version`, protège la lecture par RLS et conserve les suppressions comme tombstones. Les cours et l'historique de marché restent dans le cache local, hors sync utilisateur.

Un conflit de version conserve les deux copies : Profil → Synchronisation montre le JSON local/cloud et demande de garder l'un ou l'autre. Un échec réseau garde les écritures `dirty` et affiche « Hors ligne · synchronisation en attente ». Les IDs stables évitent les doublons; la fonction de déconnexion des appareils révoque l'accès cloud d'une session, sans effacer une copie déjà stockée hors ligne sur cet appareil.

Un export JSON contient les enregistrements privés, y compris tombstones et préférences. L'import JSON fusionne par ID dans le propriétaire courant. Le CSV importe/exports les transactions et exige un FX explicite pour USD; une réimportation identique évite les doublons. Conserver un JSON indépendant avant changement de téléphone tant que la sync distante n'est pas validée sur un projet réel.

Scénario de validation requis après déploiement : appareil A crée un CELI et un achat daté; sync; appareil B se connecte et récupère les mêmes IDs et montants; modifications divergentes créent un conflit; résoudre; révoquer B; vérifier que son prochain appel cloud est refusé. Ceci n'a pas encore été exécuté contre un projet Supabase hébergé.
