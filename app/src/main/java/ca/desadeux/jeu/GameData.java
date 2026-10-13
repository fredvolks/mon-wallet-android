package ca.desadeux.jeu;
import ca.desadeux.jeu.GameLogic.Category;
public final class GameData {
 public static final String[][][] cards = new String[][][]{
  {
   {"Le regard","Regarde ton partenaire dans les yeux pendant une minute."},
   {"Long baiser","Embrasse ton partenaire pendant 45 secondes."},
   {"La nuque","Offre des petits baisers dans son cou."},
   {"Massage","Offre trois minutes de massage à ton partenaire."},
   {"Danse rapprochée","Propose une danse lente, corps contre corps."},
   {"Compliments","Révèle trois détails que tu trouves irrésistibles."},
   {"Yeux fermés","Ferme les yeux et reçois trois baisers surprises."},
   {"Souvenir","Raconte votre moment romantique le plus mémorable."},
   {"Caresse","Caresse doucement les épaules, le dos et les bras."},
   {"À l'oreille","Chuchote un désir tendre à son oreille."},
   {"Premier baiser","Recrée votre premier baiser."},
   {"Le parfum","Dis ce que tu aimes dans son odeur."},
   {"Câlin","Enlace ton partenaire pendant une minute."},
   {"Surprise","Ton partenaire choisit entre massage et baisers."},
   {"Frisson","Fais glisser tes doigts dans son dos."},
   {"Rendez-vous rêvé","Décris une soirée idéale à deux."},
   {"Au ralenti","Approche-toi lentement avant de l'embrasser."},
   {"Petit secret","Avoue un geste qui te séduit."},
   {"Le guide","Laisse ton partenaire guider tes mains pour un massage."},
   {"JACKPOT","Ton partenaire choisit deux défis sensuels."}
  },
  {
   {"Déshabillage","Retire un vêtement, si tu en as envie."},
   {"Baiser surprise","Ton partenaire choisit où recevoir un baiser."},
   {"Danse privée","Offre une petite danse de séduction."},
   {"Yeux bandés","Propose des baisers avec les yeux bandés."},
   {"Huile de massage","Offre cinq minutes de massage."},
   {"Tentation","Rapproche tes lèvres sans embrasser pendant 30 secondes."},
   {"Une envie cachée","Raconte une idée coquine qui t'intrigue."},
   {"Scénario","Imagine une première rencontre dans un bar."},
   {"À l'oreille","Murmure trois phrases de séduction."},
   {"Le choix","Choisissez entre un vêtement en moins ou un long baiser."},
   {"Enlacés","Restez enlacés deux minutes."},
   {"Miroir","Reproduis les gestes sensuels de ton partenaire."},
   {"Massage inversé","Échangez les rôles pour le prochain massage."},
   {"Trois endroits","Ton partenaire choisit trois endroits pour un baiser."},
   {"Le souhait","Complète : Ce soir, j'aimerais que tu..."},
   {"Danse muette","Séduis sans parler pendant une chanson."},
   {"Le bandeau","Propose une surprise avec une caresse ou un baiser."},
   {"Invitation","Invente une invitation coquine pour la soirée."},
   {"Deux minutes","Offre l'attention sensuelle choisie par ton partenaire."},
   {"DOUBLE DÉFI","Relance deux fois et choisissez votre défi préféré."}
  },
  {
   {"Déshabille-moi","Propose à ton partenaire de retirer un de tes vêtements."},
   {"Préliminaires","Propose de commencer un préliminaire que vous aimez."},
   {"Sexe oral","Propose d'en donner ou d'en recevoir, si vous le souhaitez."},
   {"Position surprise","Propose une position sexuelle nouvelle pour vous."},
   {"Plaisir mutuel","Propose de partager un moment de plaisir mutuel."},
   {"Sous la douche","Propose de poursuivre votre intimité sous la douche."},
   {"Prends l'initiative","Prends l'initiative du prochain moment intime."},
   {"Sans pénétration","Propose une activité intime sans pénétration."},
   {"Jouet intime","Propose un jouet intime si vous en avez envie."},
   {"Changement de décor","Propose un autre endroit privé de la maison."},
   {"Fantasme","Partage un fantasme que tu aimerais explorer."},
   {"Choisis pour nous","Ton partenaire propose une activité intime commune."},
   {"Rôles inversés","Inversez qui prend l'initiative cette fois."},
   {"Lumières","Choisissez entre lumière tamisée et lumière complète."},
   {"Le bandeau","Propose un jeu intime avec les yeux bandés."},
   {"Nouveauté","Propose une expérience intime à essayer ensemble."},
   {"Le rythme","Choisissez une ambiance lente ou plus passionnée."},
   {"Désir secret","Parle d'une activité que tu aimerais partager davantage."},
   {"Carte blanche","Ton partenaire propose une activité que tu peux accepter ou passer."},
   {"JACKPOT FINAL","Choisissez ensemble comment terminer la soirée."}
  }
 };
 public static String[] get(Category c,int face){return cards[c.ordinal()][face-1];}
}
