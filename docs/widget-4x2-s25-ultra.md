# Mesure du widget 4×2 sur Galaxy S25 Ultra

La capture réelle `01-1791388330958.jpeg` reçue dans la conversation mesure
709 × 1536 pixels. La bordure du widget se trouve environ de x=47 à x=662
et de y=103 à y=435, soit 616 × 333 pixels **dans la capture transmise** :
rapport largeur/hauteur ≈ 1,85. Les captures de conversation sont redimensionnées;
ce nombre n'est pas la résolution native exacte du téléphone.

Le Galaxy S25 Ultra peut afficher 1440 × 3120 pixels en QHD+, mais sa résolution
configurée, le zoom, la grille One UI et les marges du lanceur changent l'espace
du widget. Une grille « 4×2 » ne garantit aucun nombre fixe de pixels. Le widget
lit désormais `OPTION_APPWIDGET_SIZES` sur Android 12+, et la configuration affiche
la surface annoncée par le lanceur en dp et son estimation en pixels de contenu.
Pour les anciens lanceurs, le mode portrait utilise `MIN_WIDTH` + `MAX_HEIGHT`.

Les tests de rendu utilisent 334 × 180 dp (rapport 1,86, proche de la capture)
avec cinq et six titres. Le précédent banc de test 360 × 160 dp (2,25) ne
représentait pas cette disposition. La validation finale du contour et du texte
sur One UI exige une capture ou une connexion de l'appareil après installation.
