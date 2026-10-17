# LoveRoll — chargement en verre (Web 123 / Android 0.13.10)

Deux illustrations ImageGen générées séparément : cœur en verre rose champagne sur fond transparent, et décor photographique bordeaux presque noir avec satin et marbre en bas. Référence de composition : visuel fourni par le propriétaire le 17 octobre 2026. Aucun mot, logo, bouton ni indicateur n’est intégré aux pixels.

- `couple-web/assets/loveroll-glass-heart-v123.webp` : 640 × 640 RGBA, 92 870 octets.
- `couple-web/assets/loveroll-splash-satin-v123.webp` : 941 × 1672 RGB, 47 064 octets.
- Les deux copies dans `app/src/main/res/drawable-nodpi/` sont identiques octet pour octet. Le chargement natif ne dépend pas du réseau. Les bitmaps sont décodés une seule fois.
- Les exports WebP utilisent Pillow/Lanczos et qualité 88 (cœur), 82 (fond), méthode 6 ; aucune retouche picturale par script.

Le cœur bat lentement (3 s, échelle 1 à 1,018). Le logo serif italique et la phrase exacte sont du texte HTML / Canvas Android, immobiles. Les trois points sont indépendants. Les animations sont désactivées avec `prefers-reduced-motion` et `ValueAnimator.areAnimatorsEnabled()`.

Le chargement se ferme au rendu de la vraie destination, sans durée minimale. La courte sortie de 200 ms n’intercepte pas les touches. Un échec de démarrage remplace le chargement par une erreur avec Réessayer ; la destination et les paramètres d’invitation restent conservés. Aucun compte, stockage de session ou historique n’est effacé.

## Intentions des prompts ImageGen

**Cœur — génération, transparence activée.** Photorealistic substantial polished rose crystal glass heart, front-facing, rounded lobes and pointed tip, clear translucent pink champagne glass, deep burgundy refracted center, ivory-pink luminous rim, soft warm realistic reflections. Isolated transparent cutout. No text, logo, pedestal, fabric, neon, particles or concentric circles.

**Décor — génération, fond opaque.** Minimal portrait 9:16 near-black warm burgundy (#10070c / #240b13), upper 68% empty negative space. Photorealistic burgundy satin broad folds in the lower portion, subdued rose champagne highlights, discreet black burgundy marble with fine rose veins at the very bottom. No heart, text, logo, dice, candles, buttons, purple or neon.

## Vérifications reproductibles

`node --test couple-web/loveroll-splash.test.mjs couple-web/native-splash-parity.test.mjs`

`node couple-web/loveroll-splash-browser-check.cjs` (serveur local `couple-web/` sur 8765 et Playwright Chromium) : huit formats, images décodées, contenu dans l’écran, animations réduites, chargements concurrents, destinations réelles sans/avec session et invitation, erreur de syntaxe et réseau, réessai préservant l’invitation.

`node couple-web/home-podium-browser-check.cjs` et `node couple-web/dice-podium-browser-check.cjs` : géométrie réelle, contact sur le marbre, orientation/canvas conservés après actualisation des données et redimensionnement.
