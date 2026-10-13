package ca.desadeux.jeu;

/** A single shared 25-day advent calendar for the couple, with an optional role swap per day. */
public final class AdventData {
 public static final String FRED="FRED", EMILIE="EMILIE", TOGETHER="ENSEMBLE";
 /** Each day: title, original giver role, instruction, example. */
 public static final String[][] days = new String[][] {
  {"L'ouverture en douceur", "ENSEMBLE", "Prenez dix minutes à deux pour vous retrouver sans écrans.", "Exemple : mettez une chanson, donnez-vous un long câlin et choisissez une petite attention pour demain."},
  {"Massage pour Émilie", "FRED", "Fred offre cinq minutes de massage des épaules et de la nuque à Émilie.", "Exemple : Émilie choisit la pression et peut demander d'arrêter quand elle veut."},
  {"Trois mots pour Fred", "EMILIE", "Émilie offre à Fred trois compliments précis et un baiser.", "Exemple : décris un geste, une qualité et un souvenir que tu apprécies."},
  {"Danse de décembre", "ENSEMBLE", "Choisissez une chanson et dansez ensemble jusqu'à la fin.", "Exemple : dans le salon, lumières tamisées et téléphones rangés."},
  {"Un baiser surprise", "FRED", "Fred demande à Émilie où elle aimerait recevoir trois baisers.", "Exemple : main, joue et nuque si cela lui plaît."},
  {"Massage pour Fred", "EMILIE", "Émilie offre cinq minutes de massage à Fred.", "Exemple : commence par les épaules et demande si la pression lui convient."},
  {"Souvenir préféré", "ENSEMBLE", "Racontez chacun un souvenir drôle ou romantique de votre couple.", "Exemple : reparlez de votre première sortie ensemble."},
  {"Strip-tease léger de Fred", "FRED", "Fred propose à Émilie une petite danse de déshabillage, à son rythme.", "Exemple : une chanson et un vêtement en moins, si les deux sont à l'aise."},
  {"Déclaration coquine", "EMILIE", "Émilie confie à Fred une envie intime qu'elle aimerait partager.", "Exemple : « Ce soir, j'aimerais qu'on prenne plus de temps pour… »."},
  {"Douche à deux", "ENSEMBLE", "Proposez une douche chaude partagée, sans attente pour la suite.", "Exemple : installez vos serviettes et prenez un moment ensemble."},
  {"Baisers au ralenti", "FRED", "Fred offre à Émilie une minute de baisers lents, si elle en a envie.", "Exemple : commence par lui demander si elle préfère un baiser ou un câlin."},
  {"Strip-tease léger d'Émilie", "EMILIE", "Émilie propose à Fred une danse de séduction, avec ou sans déshabillage.", "Exemple : une chanson qu'elle choisit et quelques mouvements devant lui."},
  {"Choix des fantasmes", "ENSEMBLE", "Partagez chacun une idée coquine, sans obligation de la réaliser.", "Exemple : parlez d'une ambiance ou d'un scénario qui vous intrigue."},
  {"Une attention sexuelle", "FRED", "Fred propose à Émilie du sexe oral, uniquement si elle en a envie.", "Exemple : une simple proposition; si elle préfère autre chose, choisissez une activité différente."},
  {"Une attention sexuelle", "EMILIE", "Émilie propose à Fred du sexe oral, uniquement si lui et elle le souhaitent.", "Exemple : demande d'abord ce qui lui ferait plaisir; vous pouvez remplacer le défi."},
  {"Cinq minutes rien qu'à vous", "ENSEMBLE", "Prenez cinq minutes pour des baisers, des câlins ou une autre attention commune.", "Exemple : chacun choisit une chose qu'il aimerait recevoir."},
  {"L'invitation secrète", "FRED", "Fred prépare une petite invitation romantique ou coquine pour Émilie.", "Exemple : une note « Rendez-vous dans le salon à 21 h »."},
  {"Le choix du jouet", "EMILIE", "Émilie propose, si vous en possédez, un jouet intime que vous connaissez.", "Exemple : choisissez ensemble de l'utiliser ou de remplacer cette activité."},
  {"La soirée sans pression", "ENSEMBLE", "Choisissez ensemble une façon agréable d'être proches, avec ou sans sexe.", "Exemple : un film collés, un long massage ou un moment intime partagé."},
  {"Bain ou massage", "FRED", "Fred offre à Émilie le choix entre un bain relaxant et un massage.", "Exemple : prépare une serviette chaude et laisse-la décider."},
  {"Rendez-vous improvisé", "EMILIE", "Émilie imagine un petit rendez-vous pour Fred à la maison.", "Exemple : une musique, une collation et un moment à deux."},
  {"Préliminaires au choix", "ENSEMBLE", "Parlez de vos préliminaires préférés et choisissez une idée commune.", "Exemple : chacun dit ce qui lui plaît et ce qu'il veut éviter."},
  {"L'ambiance parfaite", "FRED", "Fred prépare pour Émilie une ambiance sensuelle qu'elle apprécie.", "Exemple : bougies LED, musique et lumières tamisées."},
  {"Le souhait de Noël", "EMILIE", "Émilie offre à Fred une attention intime de leur choix, avec son accord.", "Exemple : il peut choisir entre un massage, une danse ou un moment à deux."},
  {"Le grand cadeau à deux", "ENSEMBLE", "Chacun choisit une activité qu'il aimerait vivre ensemble pour terminer le calendrier.", "Exemple : une soirée câlins et baisers, un massage partagé ou une activité sexuelle voulue par les deux."}
 };
 public static String giver(int day,boolean swapped){
  String code=days[day-1][1];
  if(code.equals(TOGETHER))return "Fred et Émilie";
  boolean fred=code.equals(FRED);
  return fred!=swapped?"Fred":"Émilie";
 }
 public static String recipient(int day,boolean swapped){
  String code=days[day-1][1];
  if(code.equals(TOGETHER))return "Fred et Émilie";
  return giver(day,swapped).equals("Fred")?"Émilie":"Fred";
 }
 private static String swapNames(String value){
  return value.replace("Fred","__PERSON_SWAP__")
   .replace("Émilie","Fred").replace("__PERSON_SWAP__","Émilie");
 }
 public static String instruction(int day,boolean swapped){
  String value=days[day-1][2];
  return swapped&&!isTogether(day)?swapNames(value):value;
 }
 public static String example(int day,boolean swapped){
  String value=days[day-1][3];
  return swapped&&!isTogether(day)?swapNames(value):value;
 }
 public static boolean isTogether(int day){
  return days[day-1][1].equals(TOGETHER);
 }
}
