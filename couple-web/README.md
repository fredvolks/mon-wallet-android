# LoveRoll — Mode couple connecté (Android + iPhone)

## État exact
Le code de l'application web multiappareil et son schéma Supabase sont préparés dans `couple-web/`.
**La base Supabase dédiée est créée et le schéma SQL a été installé (12 tables avec RLS). La PWA n'est PAS encore publiée sur une URL vérifiée; aucun vrai compte ni aucune synchronisation n'ont encore été testés.**
L'application Android native v0.7.0 reste locale et utilise encore ses anciennes données.
Ce module connecté doit être provisionné dans un projet Supabase **dédié**, jamais Mon Wallet.

## Connexion et noms
- Google : OAuth Supabase, fournisseur à configurer pour le projet.
- Apple : OAuth Supabase, nécessite les identifiants du développeur Apple (Service ID, Key ID, Team ID, clé de signature) et leur rotation. Apple OAuth Web ne donne souvent pas le nom ; demander le nom une fois lors de l'inscription.
- Le champ `couple_profiles.display_name` ne contient que le nom issu de la connexion ou explicitement confirmé par son titulaire. Aucun nom de partenaire n'est codé en dur dans `app.js`.
- Chaque partenaire se connecte avec son propre compte. La personne invitée doit utiliser **le courriel exact** auquel l'invitation était destinée, y compris si Apple masque son courriel via relais.

## Installer une fois le projet dédié provisionné
1. ✅ Projet Supabase dédié `LoveRoll` créé dans l'organisation `Fred forge` en région `ca-central-1` ; coût annoncé 0 $/mois.
2. ✅ Schéma `schema.sql` installé par migration dans le projet dédié. ✅ Contrôle SQL : 12/12 tables sous RLS, aucun accès anonyme au journal, aucun `INSERT` direct des tirages avec le rôle authenticated. ⏳ Tester avec trois vrais comptes reste à faire.
3. Configurer Google et Apple dans Auth > Providers et autoriser le domaine HTTPS du site et son callback.
4. ✅ `config.js` contient uniquement l'URL publique du nouveau projet et sa clé **publishable**. Aucun secret admin/service-role n'est conservé dans le dépôt. L'URL HTTPS de GitHub Pages est prévue mais pas encore vérifiée.
5. Héberger `couple-web/` sur un site HTTPS privé côté contenu (code statique public, données protégées par Auth, RLS et chiffrement). PWA accessible via Chrome Android et Safari iPhone.
6. Faire une vraie invitation de test. L'invitant remplit l'adresse de son partenaire, puis son app courriel s'ouvre avec l'invitation : il doit appuyer sur **Envoyer**. Les envois automatiques nécessitent un fournisseur transactionnel distinct, non configuré.
7. Pour le journal intime et les questionnaires, définir ensemble une phrase secrète forte et la partager hors de l'app. Elle sert à chiffrer les données côté appareil (AES-GCM avec clé PBKDF2), n'est jamais envoyée au serveur et **ne peut pas être récupérée si perdue**. Les dates restent visibles comme métadonnées aux membres du couple.
8. Sur iPhone, ouvrir le site dans Safari, puis Partager > Sur l'écran d'accueil.
9. Vérifier le tirage simultané depuis Android et iPhone, un D6 personnel chacun, une soirée, les avis et le calendrier.
10. Activer séparément des notifications push de fond si nécessaire : **non implémentées**. Les nouveautés s'affichent en temps réel tant que la page est ouverte.

## Déploiement en attente
Ne pas annoncer une synchronisation iPhone-Android opérationnelle avant d'avoir déployé et validé ces étapes.
Le mode web relié au cloud et l'APK natif actuel sont deux modes distincts : les tirages et entrées du mode local ne se synchronisent pas automatiquement. Une migration volontaire et chiffrée des données locales sera nécessaire avant un basculement définitif.

## Confidentialité
- Supabase Row Level Security filtre les membres du couple.
- Deux membres maximum ; invitation vérifiée contre le courriel authentifié.
- Les tirages partagés passent par des fonctions serveur transactionnelles, non par des changements directs côté client.
- Les réponses et entrées intimes sont chiffrées dans le navigateur avant stockage.
- Aucun stockage tiers ne reçoit la phrase secrète.
- Les membres voient les questionnaires de l'autre après déverrouillage avec la phrase commune ; un partenaire ne peut pas écrire/modifier sous l'identité de l'autre.
- La suppression du compte, les consentements de partage et l'export/migration nécessitent encore une finition avant usage réel sur des données sensibles.

## Corrections de fiabilité du mode connecté (code prêt pour déploiement)
- Le même dé hebdomadaire et les trois étapes du défi spécial sont verrouillés atomiquement pour les deux comptes.
- Chaque D6 personnel est tiré par **l'identité authentifiée correspondante**.
- Un nouveau D20 ne peut pas être proposé à une personne tant que son défi précédent est en attente; le serveur respecte sa limite D6 de défis terminés.
- « Terminer et compter » exige que chacun ait terminé son nombre prévu de défis. « Arrêter sans compter » ferme la session sans épuiser le quota hebdomadaire.
- Les questionnaires sont liés à une soirée terminée, par `session_id` et compte authentifié.
- Les invitations utilisent désormais le **fragment** de l'adresse (non transmis comme paramètre de requête HTTP) et le retirent immédiatement de la barre d'adresse. Le destinataire doit néanmoins utiliser le courriel vérifié invité et le lien d'invitation reste un secret à ne pas transférer.
- Une souscription temps réel est active même pour une personne seule en attente d'acceptation du match, puis lors des tirages et actions du couple tant que l'application est ouverte.
- Le menu web comprend Accueil, Soirée, Défi spécial, Avent, Réglages. Le journal et les questionnaires sont accessibles depuis Réglages.

## Vérifications et limites de la mise en service
Les tests de structure JavaScript + SQL exécutés sur GitHub ne remplacent **pas** des tests de migration en base, ni des essais d'authentification de bout en bout à deux personnes.
Il faut un projet cloud dédié avec coût confirmé, puis tester les règles RLS et transactions avec trois comptes (deux partenaires et un tiers non autorisé) avant stockage de données intimes.
Les notifications temps réel de premier plan sont prévues; les notifications push quand le navigateur iPhone est fermé nécessitent encore un service de push et des autorisations.
L'invitation est préparée dans l'application de courriel (envoi manuel) jusqu'à l'installation d'un fournisseur de courriel transactionnel.
L'APK natif v0.7.0 n'est pas encore relié au mode web. La PWA est prévue pour les deux appareils une fois hébergée, avec migration volontaire des anciennes données.

## Configuration actuelle (2026-10-13)
- Nouveau projet : [LoveRoll — Supabase](https://supabase.com/dashboard/project/ejsrqfpkgwraorrznjpm), **séparé** de Mon Wallet.
- Région : Canada central. Coût de création annoncé : **0 $ par mois**. Les limites du plan gratuit demeurent applicables.
- Le serveur de données et les fonctions de tirage sont installés. Les avertissements de l'analyseur Supabase concernant les fonctions `SECURITY DEFINER` exposées aux utilisateurs authentifiés sont intentionnels pour les tirages ; chaque RPC doit obligatoirement vérifier l'appartenance au couple et l'auteur, et être testé avec un 3e compte non membre. Voir [l'avertissement](https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable).
- Authentification courriel : l'UI contient maintenant `signInWithOtp` (lien courriel) comme option de secours ; dépend du quota de courriels Supabase et des URL de redirection.
- **Action obligatoire à une seule reprise pour GitHub Pages (si le workflow échoue)** : aller à `github.com/fredvolks/mon-wallet-android/settings/pages`, puis sélectionner `GitHub Actions` comme source. Le workflow `.github/workflows/deploy-couple-web.yml` publiera alors exclusivement le répertoire `couple-web/`, normalement à `https://fredvolks.github.io/mon-wallet-android/`. Ne communiquer ce lien comme actif qu'après vérification de la publication.
- **Action obligatoire Supabase Auth > URL Configuration** : définir **Site URL** et **Redirect URLs** sur cette adresse HTTPS. Les liens OAuth et Magic Link peuvent échouer sinon.
- **Google** : créer un OAuth Client Web via Google Cloud Console, rediriger vers `https://ejsrqfpkgwraorrznjpm.supabase.co/auth/v1/callback`, puis installer le Client ID/Secret dans Supabase Auth > Providers > Google, sans jamais les mettre dans le code web.
- **Apple** : un compte Apple Developer, Service ID, Team ID, Key ID et clé privée sont nécessaires dans Supabase Auth > Providers > Apple. Apple peut masquer le courriel, auquel cas la personne doit se connecter avec le courriel exact de l'invitation ou choisir une adresse Google/courriel identique.
- L'application web peut fonctionner depuis Android/iPhone en PWA avec les **mêmes** comptes et données lorsque site + auth sont activés. L'APK Android natif existant n'est **pas automatiquement migré** vers ce système.
- Notifications en temps réel : bannières dans l'application lorsque la PWA est ouverte, pas encore de notifications push quand Safari/Chrome est fermé.

## LoveRoll 0.8.0 — Fêtes et anniversaires
- L'application visible s'appelle **LoveRoll**. L'identifiant APK reste `ca.desadeux.jeu` pour préserver les mises à jour et données existantes.
- Nouvel onglet interne `Défi spécial → Fêtes et anniversaires` avec Saint-Valentin (14 février), fête des Mères (2e dimanche de mai au Canada), fête des Pères (3e dimanche de juin), anniversaire de couple et deux anniversaires personnels.
- Les anniversaires configurés stockent seulement **mois et jour**, pas l'année de naissance.
- Fenêtre d'ouverture : 14 jours avant, jusqu'à 7 jours après. Un seul D20 et un seul D6 d'organisation par occasion/année. Les deux partenaires sont synchronisés sur le web par Supabase.
- 20 idées de surprises romantiques, coquines et audacieuses, facultatives et respectueuses des limites individuelles; ne jamais assimiler un tirage à un consentement.
- Le module Android natif contient une version **locale** des événements, sans comptes connectés. Le module web connecté doit être testé avec deux identités authentifiées une fois le site déployé. Les données Android locales ne sont pas synchronisées automatiquement.
- Pour conserver la mise à jour des APK v0.7.0, la publication LoveRoll v0.8.0 utilise le tag historique `des-a-deux-v0.8.0` et inclut **deux noms de fichier identiques** `LoveRoll-v0.8.0.apk` et `Des-a-Deux-v0.8.0.apk`.
- Supabase : migration `special-occasions.sql` déployée sur le projet LoveRoll séparé, avec RLS. Une seconde migration a retiré l'exécution directe de la fonction d'aide interne.
- Hébergement iPhone : le premier essai GitHub Pages n'a pas pu publier le site. L'URL `https://fredvolks.github.io/mon-wallet-android/` **n'est pas vérifiée comme active**. Se rendre dans GitHub → Settings → Pages, sélectionner **GitHub Actions**, puis relancer le workflow de déploiement.
- OAuth Apple/Google nécessite encore les identifiants configurés dans Supabase Auth. Les tests automatiques du code ne constituent pas un test mobile de bout en bout.
