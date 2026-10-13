# Dés à Deux — Mode couple connecté (Android + iPhone)

## État exact
Le code de l'application web multiappareil et son schéma Supabase sont préparés dans `couple-web/`.
**Il n'est PAS déployé, aucun compte n'a été créé et aucune synchronisation réelle n'est encore active.**
L'application Android native v0.7.0 reste locale et utilise encore ses anciennes données.
Ce module connecté doit être provisionné dans un projet Supabase **dédié**, jamais Mon Wallet.

## Connexion et noms
- Google : OAuth Supabase, fournisseur à configurer pour le projet.
- Apple : OAuth Supabase, nécessite les identifiants du développeur Apple (Service ID, Key ID, Team ID, clé de signature) et leur rotation. Apple OAuth Web ne donne souvent pas le nom ; demander le nom une fois lors de l'inscription.
- Le champ `couple_profiles.display_name` ne contient que le nom issu de la connexion ou explicitement confirmé par son titulaire. Aucun nom de partenaire n'est codé en dur dans `app.js`.
- Chaque partenaire se connecte avec son propre compte. La personne invitée doit utiliser **le courriel exact** auquel l'invitation était destinée, y compris si Apple masque son courriel via relais.

## Installer une fois le projet dédié provisionné
1. Créer un nouveau projet Supabase (avec autorisation et vérification du coût).
2. Exécuter `schema.sql` sur ce seul projet. Tester les politiques de sécurité RLS en utilisant deux comptes réels + un troisième compte non autorisé.
3. Configurer Google et Apple dans Auth > Providers et autoriser le domaine HTTPS du site et son callback.
4. Renseigner uniquement l'URL publique du projet, la clé **publishable** et l'adresse HTTPS du site dans `config.js`. Ne jamais inclure de secret admin/service-role.
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
