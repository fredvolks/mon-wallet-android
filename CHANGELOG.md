# Versions

## Mon Wallet v0.2.29 — 2026-10-08 (historique du CDR McDonald’s)

- Le fournisseur Yahoo traduit MCD-C et les anciens symboles MCD.NE/MCDS vers MCD.TO après le changement CIBC d’août 2026, sans modifier les transactions du portefeuille.
- Le prix et l’historique utilisent la même correspondance; les requêtes historiques vides réessaient la période de cours standard.
- Teste la migration du CDR, les clôtures réelles et le calcul P&L du calendrier.


## Mon Wallet v0.2.28 — 2026-10-08 (calendrier : couverture des séances récentes)

- Le calendrier vérifie les clôtures récentes séparément de l’historique total; plusieurs années de données ne peuvent plus masquer des séances manquantes dans le mois affiché.
- Une couverture récente incomplète déclenche une nouvelle récupération des clôtures.
- Test de régression ajouté pour le cas observé : milliers de clôtures anciennes et une seule clôture récente.


## Mon Wallet v0.2.24 — 2026-10-08 (calendrier USD/CAD et secteurs Découvrir)

- Calendrier : exige et signale les clôtures historiques USD/CAD nécessaires aux titres américains; un achat fait aujourd’hui n’est pas déclaré incomplet si son cours daté est présent.
- Découvrir : ajoute 11 secteurs et jusqu’à 30 grandes actions liquides par secteur, classées selon la période de rendement choisie; fonctionne avec les sources gratuites configurées.
- Tests : couvre les achats du jour et la couverture de l’historique requis.

## Mon Wallet v0.2.23 — 2026-10-08 (rechargement vérifiable des clôtures)

- Calendrier : les historiques qui ne couvrent qu’une seule journée déclenchent automatiquement une nouvelle tentative avec une période plus large.
- Données : un historique partiel n’est plus considéré comme chargé avec succès; les nouvelles tentatives sont limitées pour éviter les appels répétés.
- Rapports : affiche clairement les titres et le nombre de clôtures reçues, ou ceux dont l’historique reste incomplet.
- Tests : couverture de la détection des historiques clairsemés et du passage à une période plus large.

## Mon Wallet v0.2.22 — 2026-10-08 (historique complet du calendrier)

- Calendrier : le rafraîchissement ne considère plus deux clôtures isolées comme un historique complet; il recharge les clôtures nécessaires depuis la première transaction.
- Rapports : le bouton « Recharger les clôtures historiques » reste visible même si quelques journées ont déjà un rendement, pour corriger un calendrier partiel.
- Calendrier : la date de la première transaction est indiquée sous le compte des rendements quotidiens.

## Mon Wallet v0.2.21 — 2026-10-08 (rapports canadiens et Watchlists)

- Finances : lorsqu’aucun fournisseur autorisé ne couvre une action canadienne, accès en un toucher aux rapports officiels gratuits sur SEDAR+. La recherche du titre se fait sur le site officiel; Mon Wallet ne récupère ni ne stocke les documents SEDAR+.
- Watchlists : action de retrait libellée clairement « Retirer de cette Watchlist »; retirer enlève le titre de la liste sélectionnée sans toucher aux positions du portefeuille.
- Transactions : accès « Transactions · modifier » dans le portefeuille et bouton « Modifier » visible sur chaque ligne; l’édition met à jour l’opération sans créer d’achat ou de vente.
- Calendrier : compte les rendements chargés pour le mois et propose de recharger les clôtures historiques si aucun rendement n’est disponible.
- Fiabilité : les lignes JSON locales mal formées sont ignorées afin qu’un cache invalide ne bloque pas l’ouverture du portefeuille.

## Mon Wallet v0.2.20 — 2026-10-08 (P&L quotidien, édition et widget)

- Widget : le P&L conserve la dernière performance de séance disponible quand les marchés ferment. Les portefeuilles avec des séances décalées additionnent les variations calculables sans inventer de cours manquant; une clôture historique complète un cours précédent absent du fournisseur.
- Widget 4×2 : de 16 h à 9 h 30 (heure de Toronto), affiche les futures S&P 500, Nasdaq 100 et Dow Jones si les trois cours datent de moins de deux heures; sinon, affiche les indices au comptant.
- Positions : modifier une entrée conserve son identifiant et son type, avec quantité, prix et date modifiables sans créer d’achat ou de vente. Les clôtures historiques sont actualisées après modification pour reconstruire le calendrier depuis l’entrée corrigée.
- Données financières : couleurs vert/orange/rouge selon des seuils généraux de rentabilité, croissance, rendement des capitaux et frais de FNB. Les montants absolus et valorisations nécessitant un contexte sectoriel restent neutres.

## Mon Wallet v0.2.19 — 2026-10-08 (marchés, navigation et préférences)

- Marchés : ajout d’un onglet Futures avec S&P 500 E-mini, NASDAQ 100 E-mini, Dow Jones E-mini et Russell 2000 E-mini. Les cours proviennent du fournisseur configuré et restent soumis à sa fraîcheur réelle.
- Widgets : bouton d’actualisation agrandi pour être plus facile à toucher sur les formats 2×2 et 4×2.
- Navigation : chaque onglet de la barre inférieure revient à son écran principal lorsqu’on le sélectionne; une fiche titre ouverte dans un onglet ne reste pas affichée après le changement d’onglet.
- Tri : l’application mémorise le sens du tri des Watchlists, le tri de Découvrir et le tri de l’historique des transactions. Le tri compact du portefeuille était déjà enregistré.

## Mon Wallet v0.2.18 — 2026-10-07 (dimensions réelles du widget 4×2)

- Le widget utilise les dimensions exactes annoncées par Android 12+ pour chaque taille du lanceur, y compris après redimensionnement; sur les anciens lanceurs, la hauteur portrait provient de `MAX_HEIGHT` plutôt que de la hauteur minimale paysage.
- L’aperçu affiche les dimensions détectées en dp et l’estimation des pixels de contenu. La largeur des colonnes ticker et prix est ajustée aux titres effectivement choisis, ce qui permet d’agrandir leur texte sans couper les chiffres.
- Les tests de widget sont calibrés au rapport 1,85:1 mesuré dans une capture réelle One UI du S25 Ultra (environ 616 × 333 pixels dans l’image reçue), au lieu du précédent 2,25:1 théorique. La vérification finale sur le téléphone reste à faire après installation.
- Rapports : la période 1S charge un historique court au lieu de demander systématiquement cinq ans. Une réponse vide ou une erreur de source ne bloque plus la prochaine tentative pendant 24 heures; Twelve Data peut céder à une vraie clôture Yahoo lorsqu'une bourse ou un forfait n'est pas couvert. Le cache fusionne les clôtures par date pour conserver les anciennes journées.
- Si le portefeuille a commencé pendant une période courte, Rapports affiche explicitement une performance **partielle depuis le premier achat** lorsque les clôtures existent. Si les clôtures manquent encore, la page montre le profit total calculé au cours actuel, clairement distinct du rendement de la période. Le calendrier reconstruit les séances disponibles à partir des achats, ventes, liquidités et clôtures réelles.
- Une transaction rétroactive élargit automatiquement la fenêtre de téléchargement jusqu'à sa date et déclenche la reconstruction des journées suivantes; les clôtures déjà en cache sont réutilisées et les dates manquantes sont redemandées. Les prix d'achat restent le coût de revient et ne sont jamais présentés comme des clôtures.

## Mon Wallet v0.2.17 — 2026-10-07 (Rapports historiques)

- Rapports : sélecteur de portefeuille, vue d’ensemble, performance, calendrier mensuel avec détail par jour, allocation et flux; périodes 1S, 1M, 3M, 6M, YTD, 1A, 5A, 10A et total. Le graphique compare rendement ajusté des flux, gain en dollars et valeur au capital net investi; export CSV des journées disponibles.
- Valeurs quotidiennes reconstruites à partir des transactions, des clôtures historiques et du taux USD/CAD historique. Dépôts, retraits et financement direct des achats sont séparés des gains; ventes et dividendes restent des flux internes. Les périodes sans cours ou FX fiables sont explicitement incomplètes. Les rendements des jours avec flux utilisent l’hypothèse d’un flux en début de journée faute d’heure d’exécution.
- Comparaison S&P 500, NASDAQ, TSX et XEQT à base 0 %, en CAD à partir de vrais cours, sans dividendes; analyse des contributeurs, drawdown, rendement personnel annualisé, synthèses mensuelles et annuelles. Préférences Rapports mémorisées; tests de dépôts, retraits, dividendes, vente partielle, transaction rétroactive, FX et historique insuffisant.
- Les calculs de Rapports sont dérivés du cache de cours et des transactions existants : aucun schéma Room nouveau et aucune migration requise pour cette version.

## Mon Wallet v0.2.16 — 2026-10-07 (titres du widget 4x2)

- La liste à droite du widget répartit les lignes sur toute la hauteur disponible. Logos et texte sont agrandis, avec réduction automatique du texte si un prix long doit tenir dans la largeur. Les sections Daily, rendement total, indices et actions du widget conservent leur disposition.

## Mon Wallet v0.2.15 — 2026-10-07 (nouvelles canadiennes, alertes et densité)

- Le flux Canada ingère toutes les 30 minutes les communiqués GlobeNewswire Canada dont la place et le symbole canadiens sont explicitement identifiés (TSX, TSXV, CSE, NEO/Cboe Canada). Les anciens communiqués généraux sans ticker sont conservés en base, mais exclus de l'application. Pour les titres canadiens suivis, la recherche Yahoo par nom de société sert de repli si la recherche par symbole ne donne rien, seulement quand le résultat indique exactement le symbole coté. Chaque article affiche sa source et sa date; aucune analyse IA n'est inventée. La tâche IA reste inactive faute de crédits API.
- Une alerte News pertinente touche seulement un titre suivi. Une analyse backend HIGH/CRITICAL peut la déclencher; certains résultats, prévisions, contrats ou dividendes d'un communiqué canadien sourcé et récent peuvent aussi être signalés sans IA. Toucher la notification ouvre directement l'article HTTPS original; les alertes de prix ouvrent toujours la fiche titre.
- Portefeuille Compact : ticker seul dans la première colonne, sans quantité en seconde ligne; les parts restent accessibles par la colonne optionnelle et la fiche titre. Lignes du tableau resserrées.
- Actualisation au premier plan : nouvelle tentative rapide si l'analyse complète occupait déjà le fournisseur, puis vérification des cours des positions détenues toutes les 30 secondes durant la séance active. Le statut distingue une nouvelle cotation d'une cotation inchangée et ne présente jamais le fournisseur comme temps réel garanti. Arrêt de la boucle quand l'application quitte le premier plan.
- Widget 4x2 : prix et pourcentages utilisent des départs de colonne identiques pour tous les titres; l'icône PRE/AFTER ne déplace plus les chiffres. L'indisponibilité du P&L jour s'affiche sans texte coupé. Tests de grille et de redirection des notifications.

## Mon Wallet v0.2.14 — 2026-10-07 (fil News et pipeline IA)

- Nouvel onglet News interne avec « Pour moi », Canada, USA, Marchés et Toutes; articles sourcés des titres du portefeuille et des Watchlists, cache local, déduplication, filtre pour les nouvelles faibles, détails et résumés IA seulement lorsqu'une analyse backend existe.
- Tables Supabase `news_articles` et `news_analysis` avec lecture publique et écriture réservée au service; fonctions `ingest-news` (FMP) et `analyze-news` (OpenAI Responses) déployées. Les clés restent côté backend. L'analyse ne fabrique pas de nouvelles ni de recommandations d'achat.
- Les notifications News exigent une analyse HIGH/CRITICAL pertinente et un titre suivi; les titres bruts ne déclenchent plus d'alerte IA. Reprise des erreurs d'analyse avec délai et récupération des traitements interrompus.
- La configuration backend a ensuite reçu les deux secrets et une planification Supabase sécurisée par un jeton Vault. Les tests réels montrent FMP HTTP 402 sur les deux flux News et OpenAI `credit_balance_exhausted`; les tâches sont en pause jusqu'à l'accès aux données et aux crédits API. Les articles Yahoo des titres suivis restent identifiés comme source non officielle et ne sont jamais présentés comme analysés par l'IA.

## Mon Wallet v0.2.13 — 2026-10-07 (tableaux et widget 4x2)

- Watchlist : dernière liste choisie sauvegardée dans les paramètres synchronisables; ordre manuel et ajout d'un titre écrits atomiquement dans Room. Un toucher ouvre un panneau rapide avec graphique et métriques réelles; la fiche complète reste accessible depuis ce panneau ou le menu.
- Portefeuille : vue Compact sous forme de tableau dense, ticker fixe, colonnes personnalisables et réordonnables, presets, défilement horizontal, P&L jour et total visibles. Les préférences sont persistées; la sparkline n'est chargée que si sa colonne est activée. Le profit du portefeuille en % apparaît dans l'en-tête.
- Widget 4x2 : le P&L du jour reste dominant, avec rendement total à gauche; S&P 500, NASDAQ et Dow Jones lisent les cours mis en cache. Les cours des titres n'affichent plus CAD/USD. Le graphique utilise seulement les vrais points intrajournaliers lorsque la taille le permet; la séance terminée reste affichée après fermeture. La confidentialité et les paramètres par instance demeurent actifs.
- Aucune analyse IA ou donnée canadienne supplémentaire n'est simulée. Les services de news IA autonomes et leurs secrets backend exigent encore un déploiement et des tests avec sources autorisées.

## Mon Wallet v0.2.12 — 2026-10-06 (Daily, Watchlist et hors séance)

- Watchlist : ticker seul, sans place ni état « Cache » répété; ligne PRE/AFTER de 16 dp immédiatement sous le cours régulier avec les mêmes colonnes. Le tri reste accessible dans le menu.
- Widget 4x2 : P&L du jour en valeur et en pourcentage au premier plan, rendement total en capsule secondaire, cinq titres compacts par défaut. Le graphique réel est intégré dans la zone Daily si la hauteur le permet. Aperçu et configuration propres à chaque instance.
- Cours US hors séance : lorsqu’une quote de la séance courante contient une impression PRE/AFTER réelle, le widget indique ☀/☾ et affiche ce prix et sa variation distincte; le portefeuille peut valoriser les positions au dernier cours disponible et garde le P&L du jour régulier. Aucun cours hors séance n’est créé pour un titre canadien.
- Le widget continue de s’appuyer sur le cache, WorkManager et le rafraîchissement manuel, sans streaming en arrière-plan. La fraîcheur et la source des quotes restent traçables dans les vues détaillées.

## Mon Wallet v0.2.11 — 2026-10-06 (grille Watchlist)

- L'en-tête, la ligne régulière et la ligne PRE/AFTER utilisent désormais la même grille de colonnes et la même position de défilement. Prix, variation jour en % et en devise sont alignés à droite avec des chiffres tabulaires; aucun horaire ne surcharge la ligne secondaire.
- Le preset minimal affiche Ticker, Prix, Jour %, Jour $ et performance de la période active. La sparkline et les autres colonnes restent configurables. Les préférences de l'ancien preset minimal migrent vers ce nouveau tableau, sans remplacer les dispositions personnalisées.
- Ligne hors séance de 20 dp, badge bleu PRE ou violet AFTER; aucune seconde ligne sans véritable cours hors séance US. Le cours principal conserve son indication de fraîcheur.
- Capture instrumentée sur émulateur avec titres américains et canadiens, et comparaison des bords droits des valeurs affichées lorsque le fournisseur renvoie un cours hors séance.

## Mon Wallet v0.2.10 — 2026-10-06 (Watchlist hors séance)

- Cours US Yahoo : le chargement des quotes utilise le graphique 5 minutes avec `includePrePost=true`; une impression PRE/AFTER est retenue uniquement dans les bornes de la séance courante, depuis les champs du fournisseur ou une bougie réelle. Les titres canadiens et les séances sans impression n'affichent rien.
- Twelve Data : `prepost=true` est tenté pendant les séances étendues US si cette source est configurée; la réponse doit déclarer `is_extended_hours` et provenir de la séance du jour. La disponibilité dépend du forfait du fournisseur; le cours normal reste utilisable si cette option échoue.
- Watchlist : seconde ligne PRE/AFTER de 17 dp avec cours et variation de cette séance, sans grossir la ligne régulière; l'option se trouve dans « ⚙ Colonnes » et reste activée par défaut. La fraîcheur reste distincte, et une quote en cache n'est jamais qualifiée de temps réel.
- Tests du mapping AAPL/NVDA/TSM/META/MSFT/TSLA, cache, variation distincte, absence de donnée, titre canadien et expiration de la séance. Journal de diagnostic AAPL réservé au build debug, sans secret.
- Un test instrumenté sur émulateur tente la capture d'une vraie Watchlist AAPL/NVDA/TSM après requête réseau, sans injecter de cours. Son résultat dépend de la réponse du fournisseur pendant la séance.

## Mon Wallet v0.2.9 — 2026-10-06 (Mes titres Compact et sources intégrées)

- « Mes titres » Compact : synthèse raccourcie, lignes de 64 dp sans cartes, logo vérifié, ticker, quantité sans devise, valeur et P&L du jour en dollars et en pourcentage. Les graphiques intrajournaliers sont affichés seulement si leurs données existent; options 1S/1M/3M, tri et poids mémorisés. L’ordre manuel peut être déplacé par appui long.
- Finances : tableaux annuels et trimestriels chiffrés dans la fiche titre en plus des courbes, avec source et date. Les valeurs analystes restent dans l’onglet interne. Aucun lien externe n’est requis pour lire les données disponibles.
- Découvrir fonctionne sans clé FMP sur une sélection limitée du catalogue et des titres suivis : cours et historiques du fournisseur choisi, capitalisation/dividende/volume Nasdaq pour les titres US couverts, puis score anti-pump. Une clé FMP reste facultative pour élargir l’échantillon. Les filtres n’inventent aucune donnée manquante; la couverture canadienne des fondamentaux reste tributaire d’une source autorisée.
- Les données détaillées Yahoo Finance visibles sur son site ne sont pas importées automatiquement : l’application n’a pas d’accès documenté et autorisé pour ce contenu. Les cours Yahoo déjà présents restent identifiés comme source non officielle avec leur fraîcheur réelle.

## Mon Wallet v0.2.8 — 2026-10-06 (Découvrir, Watchlist, cours et widget)

- Widget 4x2 : nombre de titres configurable par instance (Auto ou 1 à 6), cinq par défaut, ordre manuel, densité et aperçu adaptés à la taille réelle.
- Découvrir : six périodes, tableau compact, colonnes sélectionnables et réordonnables, filtres et presets, idées, métriques de régularité, drawdown, concentration et score momentum. Le mode analystes croise objectifs, couverture, consensus, dispersion et momentum.
- Watchlist : lignes compactes, période mémorisée par liste ou partagée, presets de colonnes, ordre et tri, sparkline liée à la période, cours hors séance discrets quand fournis.
- Cours au premier plan : actualisation ciblée avec cache partagé, arrêt au second plan, provenance et fraîcheur explicites; aucun cours inconnu n'est déclaré temps réel. Valorisation hors séance en option.
- Sources de données : clés FMP et Twelve Data configurables dans Profil → Source des données. Découvrir requiert un accès FMP autorisé; le classement est limité aux candidats renvoyés par bourse et à la disponibilité des historiques et données analystes. Le polling reste prudent faute de droit streaming démontré.
- Tests du classement anti-pump, de l'historique cinq ans, du PRE/AFTER, de la fraîcheur et de la valorisation. Validation Android et production d'un APK dans la CI.

## Mon Wallet v0.2.6 — 2026-10-05 (widget premium 4x2)

- Nouveau layout 4x2 à deux colonnes avec rendement total dominant, P&L du jour en capsule, trois titres sélectionnés et mini graphique lorsque des points réels existent.
- Prix, variation, vrais logos vérifiés et repli ticker; les titres restent visibles avec le graphique et un cours absent reste indiqué comme indisponible.
- Historique réel 1S puis 1M utilisé si les points intrajournaliers manquent; sans série valide, le graphique disparaît et la place revient au contenu.
- Style « Mixte premium », aperçu du portefeuille réel, paramètres et ordre conservés par widget, confidentialité et heure de mise à jour.
- Actualisation et réglages ciblent l'instance touchée; toucher le fond ouvre son portefeuille.
- Le build de validation s'installe séparément (`ca.monwallet.app.preview`) pour tester le 4x2 sans toucher aux données de la version installée. Il ne remplace pas un APK release signé avec la clé d'origine.

## Mon Wallet v0.2.5 — 2026-10-05 (widgets et routage Finances)

- Réglages indépendants par widget, aperçu, rendement total du moteur comptable, titres détenus sélectionnables et réordonnables, confidentialité et rafraîchissement.
- Graphique masqué quand les points manquent et lignes de titres ouvrant leur fiche.
- Route Canada/USA par symbole, place, devise et type; SEC/Nasdaq restent les sources américaines actives.
- Une source canadienne ou FINVIZ Elite doit disposer d’un accès autorisé côté backend. Aucun accès n’est configuré pour DOL.TO : ses chiffres Finances restent indisponibles dans cette version.

## Mon Wallet v0.2.4 — 2026-10-05 (recherche des titres canadiens)

- Liens directs vers les fiches Seeking Alpha vérifiées pour DOL, GURU, BLDP et XEQT (cotation canadienne), depuis Finances et Analystes. Les pages s’ouvrent dans le navigateur; aucune donnée Seeking Alpha n’est importée dans l’app.
- Résolution stricte du symbole, de la place, de la devise et du type; PHOS/CSE et les homonymes non vérifiés n’ouvrent pas une mauvaise fiche.
- Workflow GitHub de validation corrigé pour le SDK Android préinstallé. La connexion Google sur Samsung reste à vérifier.

## Mon Wallet v0.2.3 — 2026-10-04 (Google OAuth)

- ID public du client OAuth Google Web intégré à l'APK. Clients Web et Android créés pour `ca.monwallet.app` et le certificat release; fournisseur Google activé dans Supabase par le propriétaire.
- APK et AAB signés avec le certificat des versions précédentes; `versionCode=2003`.
- **Essai réel à terminer** : ajouter le compte à la liste des utilisateurs test dans Google Cloud, vérifier la connexion et la session sur Samsung, puis la migration du mode invité et la synchronisation. La configuration seule ne prouve pas encore que la connexion fonctionne.

## Mon Wallet v0.2.2 — 2026-10-04 (activation du backend personnel)

- Projet Supabase Mon Wallet dédié au Canada créé dans Fred forge, schéma de synchronisation appliqué, profils automatiques et fonction de suppression du compte déployés.
- Audit de sécurité distant sans alerte : la fonction privilégiée de profil est passée dans un schéma non exposé, les droits anonymes des profils ont été retirés.
- URL et clé publique du projet incluses dans la configuration du build; aucune clé privée n'est embarquée. Le courriel peut joindre le projet, sous réserve du modèle OTP et des limites d'envoi à vérifier avec un compte réel.
- **Google encore en attente** : le client OAuth Google Cloud Web/Android et le fournisseur Google Supabase ne sont pas configurés. Le parcours de connexion, la migration invité et la restauration ne sont pas validés sur un Samsung. Cette livraison ne prétend pas satisfaire le critère Google.

## Mon Wallet v0.2.1 — 2026-10-04 (correctifs partiels)

- Finances : dépôt SEC EDGAR et résumé Nasdaq par identité de marché, historique annuel/trimestriel, champs réellement présents, cache et bouton Réessayer. AAPL vérifié sur les réponses réseau.
- Analystes : consensus Buy/Hold/Sell et objectifs Nasdaq, source/date, états distincts d'absence et d'erreur.
- Logos : domaines émetteurs vérifiés, icônes réelles dans les composants partagés, cache mémoire/disque et initiales si la correspondance est incertaine.
- Connexion Google : sélecteur Android Credential Manager et échange du jeton ID avec Supabase, transfert invité proposé avant connexion. Les réglages Supabase techniques ont été retirés de l'interface utilisateur.
- **Activation Google en attente** : aucun projet Supabase Mon Wallet ni client OAuth Google n'est encore configuré; le parcours complet sur Samsung n'a pas été vérifié. Cette version ne prétend pas satisfaire le critère Google du prompt.

## Mon Wallet v0.2.0 — 2026-10-04

- Bouton direct de création du premier portefeuille; onglets adaptés au nombre de comptes.
- Vente depuis la fiche titre, vente totale, parts disponibles à la date choisie, aperçu produit/coût/gain réalisé et blocage des ventes rétroactives impossibles.
- Poids calculé sur la valeur actuelle, répartition en donut et concentration; sectorisation des ETF uniquement si leurs allocations réelles sont fournies.
- Graphiques TradingView Lightweight Charts™ en assets locaux avec OHLC et volumes fournis par le marché, sans données inventées.
- Choix de portefeuille du widget adapté; messages de source sans demande de clés utilisateur.
- Script de build incrémental, tests, APK/AAB signés et workflow GitHub Actions.


## Mon Wallet v0.1.1 — 2026-10-04

- Nouvel écran de bienvenue sombre, logo portefeuille, aperçu de portefeuille explicitement marqué comme tel, sélecteur de langue dès l'ouverture et dans Profil → Paramètres.
- Ressources Android françaises et anglaises pour l'accueil, la navigation et les libellés statiques des principaux écrans; présentation des nombres et dates selon la langue.
- Nouveaux invités sans portefeuille ni watchlist automatique. Les données des installations existantes sont conservées; aucun schéma Room n'a changé.
- Code de version 1001, signé avec le certificat de v0.1.0 pour installation par-dessus.

## Mon Wallet v0.1.0 — 2026-10-03

Première version Android native : portefeuilles, transactions datées, calculs CAD/FX, historique, watchlists, marchés, fiches titre, screener, alertes, widgets, sauvegarde/import et mise à jour signée. Schéma Supabase et client de synchronisation inclus, mais cloud non déployé dans cette livraison. Voir `docs/LIMITATIONS.md`.
