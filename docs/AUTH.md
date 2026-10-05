# Authentification

Le mode invité garde les objets dans Room sous `guest`; aucune connexion n'est exigée. Lors d'une connexion, la case « Sauvegarder et synchroniser mes données locales » copie les objets du mode invité avec leurs IDs avant la première synchronisation. Conserver un export JSON avant cette migration.

Le client utilise Supabase Auth : Google ouvre le sélecteur natif Android Credential Manager, obtient un jeton ID avec nonce et l'échange auprès de Supabase (`grant_type=id_token`). Apple et Microsoft (`azure`) conservent une Custom Tab avec PKCE S256 et vérification du `state` à `monwallet://auth/callback`; le courriel envoie un code à vérifier. La session Supabase est conservée avec AES-GCM via Android Keystore. La biométrie verrouille l'accès visuel selon la configuration du téléphone; les données Room ne sont pas intégralement chiffrées.

Pour achever la connexion dans le projet Supabase Mon Wallet (`prwlnxbqttxfjxpehmok`) :

1. Projet dédié, trois migrations et fonction `delete-account` : **déployés et audités**. L'ancien projet nommé « fredvolks's Project » n'a pas été touché.
2. L'écran de consentement externe et les clients Google Web et Android pour le certificat release ont été créés. Dans Google Auth Platform → Audience → Test users, ajouter le compte Google utilisé sur le Samsung pendant le mode test. Dans Data Access, ajouter `openid` si absent. Un client Android debug séparé serait nécessaire pour essayer les APK debug.
3. Le propriétaire a activé Google dans Supabase → Authentication → Sign In / Providers. Les Client IDs sont l'ID Web **en premier**, puis une virgule et l'ID Android : `158439565502-oa8ksq1phh6mcrq60ftbqhm0diotcdv0.apps.googleusercontent.com,158439565502-5ubf9lojapgcigjt4bnkqjbgts8j5g6q.apps.googleusercontent.com`. Le secret du client Web est saisi uniquement dans le tableau de bord Supabase. L'option « Skip nonce checks » reste désactivée. Ne jamais transmettre le secret dans le chat ou Git.
4. L'URL `https://prwlnxbqttxfjxpehmok.supabase.co`, la clé publique `sb_publishable_…` et l'ID Web Google sont intégrés au build personnel, avec possibilité de les remplacer par `SUPABASE_URL`, `SUPABASE_ANON_KEY` et `GOOGLE_WEB_CLIENT_ID`. L'app ne demande jamais ces valeurs à l'utilisateur. Le secret OAuth Google et toute clé service role restent exclusivement sur le backend.
5. Auth → URL Configuration → autoriser `monwallet://auth/callback**` dans Additional Redirect URLs pour les autres fournisseurs en Custom Tab. Le manifeste Android déclare `monwallet://auth/callback`.
6. Auth → Providers → configurer séparément Apple et Azure/Microsoft si ces boutons doivent être activés.
7. Configurer Email OTP pour que le modèle de courriel affiche le code (`{{ .Token }}`) et, pour un usage réel, un service SMTP adapté. Vérifier la limite d'envois du projet.
8. Tester la nouvelle installation, l'identification Google sur un Samsung avec certificat release, une seconde connexion, la session persistante, les données invitées migrées, RLS avec deux comptes, la déconnexion et la suppression.

Empreintes du build local actuel pour le client Android Google (`ca.monwallet.app`) :

| Certificat | SHA-1 | SHA-256 |
| --- | --- | --- |
| Release Mon Wallet | `01:92:E7:EA:62:F6:0B:8C:06:09:94:DA:97:48:61:D2:05:63:D1:87` | `9F:A4:23:26:13:C4:CC:CF:61:1E:81:14:FD:12:1D:A6:30:BB:E0:F8:F2:E6:88:68:14:FD:E6:22:5B:10:75:0D` |
| Debug de ce workspace | `C2:3C:22:28:59:C6:C1:5A:81:38:8D:4A:2C:20:B6:62:AC:81:D0:C3` | `2D:17:52:F0:C4:2F:AF:1B:2F:B2:8E:1C:5E:07:92:A3:8A:1E:D1:0E:3E:FF:1E:E4:F0:72:47:81:CD:8E:FC:78` |

Le certificat debug peut changer si un autre poste régénère sa clé; vérifier l'empreinte effective avant de l'ajouter dans Google Cloud.

Si Google OAuth n'est pas configuré, l'accueil montre une erreur de connexion simple avec Réessayer; la page Synchronisation ne présente aucune entrée technique. Le logout supprime la session locale et efface l'état Credential Manager. La clé `service_role` ne doit pas exister dans le build. Une URL scheme personnalisée peut être interceptée par une autre app; PKCE et l'état aléatoire limitent ce risque pour Apple/Azure. Un App Link vérifié serait préférable lors d'une distribution publique ultérieure. Le parcours Google réel reste à tester sur un Samsung.
