// LoveRoll: concise, non-graphic suggestions and optional time limits.
// The couple can always stop, adapt or decline; a timer isn't a consent signal.
export const EVENING_CATEGORIES=['SENSUEL','SEXUEL'];
export const EVENING_CHALLENGES={
 SENSUEL:[
 ['Regard complice',1,'Regardez-vous dans les yeux, sans distraction.'],
 ['Baiser prolongé',2,'Offrez un long baiser si vous en avez tous les deux envie.'],
 ['Massage des épaules',10,'Un massage relaxant, en demandant quelle pression convient.'],
 ['Danse rapprochée',4,'Choisissez une chanson et dansez à votre rythme.'],
 ['Trois compliments',3,'Dites trois choses précises que vous admirez chez l’autre.'],
 ['Câlin sans attente',5,'Un moment de proximité, simplement pour être ensemble.'],
 ['Massage des mains',5,'Prenez soin des mains de votre partenaire.'],
 ['Playlist du couple',5,'Choisissez deux chansons qui vous rappellent un beau souvenir.'],
 ['Baisers choisis',3,'L’autre choisit où recevoir de doux baisers, si cela lui plaît.'],
 ['Massage du dos',10,'Préparez un espace confortable pour un massage du dos.'],
 ['Un souvenir à deux',4,'Racontez chacun un souvenir heureux de votre couple.'],
 ['Un mot doux',2,'Chuchotez une attention sincère à votre partenaire.'],
 ['Danse les yeux fermés',3,'Une danse tendre où chacun garde le contrôle de son confort.'],
 ['Caresse des cheveux',3,'Un moment calme de douceur et de détente.'],
 ['Pause sans écran',10,'Éloignez les téléphones et prenez dix minutes juste pour vous.'],
 ['Petit rituel de thé',5,'Préparez une boisson et partagez un moment tranquille.'],
 ['Gratitude en duo',4,'Dites-vous ce qui a été précieux cette semaine.'],
 ['Un câlin sur le divan',5,'Installez-vous confortablement sans objectif particulier.'],
 ['Promesse douce',2,'Choisissez une petite attention à refaire dans la semaine.'],
 ['Notre meilleure soirée',5,'Inventez ensemble votre prochaine sortie romantique.']
 ],
 SEXUEL:[
 ['Le jeu des envies',5,'Exprimez chacun une envie intime et une limite à respecter.'],
 ['Séduction au ralenti',5,'Prolongez le jeu de séduction en vous accordant des pauses.'],
 ['Danse privée',4,'Offrez une danse séduisante si vous êtes à l’aise.'],
 ['Tenue surprise',5,'Choisissez ensemble une tenue dans laquelle vous vous sentez désirables.'],
 ['Massage sensuel',10,'Offrez un massage avec une ambiance plus intime, en demandant vos préférences.'],
 ['Baisers et pauses',5,'Alternez moments de rapprochement et pauses pour garder le jeu léger.'],
 ['Parler de désirs',7,'Partagez librement une préférence intime que vous aimeriez explorer.'],
 ['Jeu de rôles léger',8,'Imaginez une rencontre romantique et fixez ensemble les limites.'],
 ['Un choix audacieux',4,'Chacun propose une attention intime; ne gardez que ce qui plaît aux deux.'],
 ['Musique de séduction',5,'Créez un moment de séduction avec votre chanson préférée.'],
 ['Une surprise consentie',5,'Préparez une surprise romantique ou sensuelle en respectant les limites de l’autre.'],
 ['Ralentir le rythme',6,'Explorez un moment de proximité avec des pauses et beaucoup de communication.'],
 ['Un fantasme à raconter',5,'Racontez une envie imaginaire sans vous sentir obligés de la réaliser.'],
 ['Massage à tour de rôle',10,'Offrez chacun votre tour cinq minutes de massage.'],
 ['Le défi du regard',3,'Faites monter la complicité par le regard et les compliments.'],
 ['Une danse en duo',5,'Alternez l’initiative pendant une danse de séduction.'],
 ['Cartes de préférences',6,'Dites chacun ce que vous aimez, ce que vous ne voulez pas et ce qui vous intrigue.'],
 ['Soirée à lumière douce',8,'Préparez une ambiance intime et laissez le rythme se décider à deux.'],
 ['Une nouvelle idée',5,'Choisissez une nouveauté consensuelle, ou gardez-la pour une autre fois.'],
 ['Finale à notre rythme',10,'Réservez un moment privé sans contrainte ni attente de résultat.']
 ]
};
export function eveningCard(category,face){
 if(!EVENING_CATEGORIES.includes(category)||!Number.isInteger(face)||face<1||face>20)
   throw new Error('Carte de défi invalide');
 const [title,minutes,description]=EVENING_CHALLENGES[category][face-1];
 return {category,face,title,minutes,description};
}
export function availableEveningCategories(preferred){
 if(!EVENING_CATEGORIES.includes(preferred))throw new Error('Catégorie non reconnue');
 return EVENING_CATEGORIES.map(category=>({category,weight:category===preferred?80:20}));
}
export function formatCountdown(milliseconds){
 const seconds=Math.max(0,Math.ceil(milliseconds/1000));
 return String(Math.floor(seconds/60)).padStart(2,'0')+':'+String(seconds%60).padStart(2,'0');
}
