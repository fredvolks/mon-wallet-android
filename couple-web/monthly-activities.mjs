// LoveRoll — 20 monthly D20 outings. Editable privately by the founding admin.
export const MONTHLY_ACTIVITIES=[
  {
    "face": 1,
    "title": "Souper en amoureux",
    "description": "Choisissez un restaurant qui vous plaît à tous les deux. Réservez une table et accordez-vous une soirée sans précipitation.",
    "is_restaurant": true
  },
  {
    "face": 2,
    "title": "Spa nordique",
    "description": "Planifiez une visite au spa, avec bains et moments de détente. Comparez les horaires et réservez ensemble.",
    "is_restaurant": false
  },
  {
    "face": 3,
    "title": "Brunch prolongé",
    "description": "Choisissez une belle adresse pour un brunch du week-end et prenez le temps de savourer le repas.",
    "is_restaurant": true
  },
  {
    "face": 4,
    "title": "Spectacle ou théâtre",
    "description": "Choisissez ensemble une pièce de théâtre ou un spectacle vivant, puis organisez la sortie selon les disponibilités.",
    "is_restaurant": false
  },
  {
    "face": 5,
    "title": "Soirée humour",
    "description": "Trouvez un spectacle d’humour qui vous tente, réservez deux places et profitez de la soirée à deux.",
    "is_restaurant": false
  },
  {
    "face": 6,
    "title": "Cinéma et dessert",
    "description": "Choisissez un film au cinéma et prolongez la sortie avec un dessert dans votre adresse préférée.",
    "is_restaurant": false
  },
  {
    "face": 7,
    "title": "Restaurant dégustation",
    "description": "Découvrez un restaurant offrant un menu dégustation ou plusieurs petits plats à partager.",
    "is_restaurant": true
  },
  {
    "face": 8,
    "title": "Cours de cuisine",
    "description": "Réservez un atelier de cuisine en duo et préparez ensemble une recette que vous n’avez jamais essayée.",
    "is_restaurant": false
  },
  {
    "face": 9,
    "title": "Jeu d’évasion en duo",
    "description": "Trouvez une salle d’évasion adaptée à deux personnes et relevez ensemble les énigmes.",
    "is_restaurant": false
  },
  {
    "face": 10,
    "title": "Musée ou exposition",
    "description": "Visitez une exposition ou un musée puis discutez de vos découvertes autour d’une pause gourmande.",
    "is_restaurant": false
  },
  {
    "face": 11,
    "title": "Massage en duo",
    "description": "Réservez un massage en duo dans un établissement de votre choix et gardez du temps pour vous détendre.",
    "is_restaurant": false
  },
  {
    "face": 12,
    "title": "Café chic",
    "description": "Choisissez un café agréable, commandez une gourmandise et prenez le temps de vous retrouver.",
    "is_restaurant": false
  },
  {
    "face": 13,
    "title": "Balade Vieux-Québec et resto",
    "description": "Organisez une promenade dans le Vieux-Québec et terminez par un souper dans un restaurant choisi ensemble.",
    "is_restaurant": true
  },
  {
    "face": 14,
    "title": "Bistro et jeu de société",
    "description": "Choisissez un bistro ou un café ludique et lancez-vous un défi amical autour d’un jeu de société.",
    "is_restaurant": false
  },
  {
    "face": 15,
    "title": "Cocktails ou mocktails",
    "description": "Découvrez un bar à cocktails ou un endroit proposant de bons mocktails et essayez une nouvelle saveur.",
    "is_restaurant": false
  },
  {
    "face": 16,
    "title": "Quilles ou billard",
    "description": "Réservez une piste de quilles ou une table de billard et transformez la sortie en petit tournoi amical.",
    "is_restaurant": false
  },
  {
    "face": 17,
    "title": "Traversier Québec–Lévis",
    "description": "Traversez le fleuve ensemble et profitez du panorama, puis explorez le quartier de l’autre rive.",
    "is_restaurant": false
  },
  {
    "face": 18,
    "title": "Fondue ou tapas",
    "description": "Réservez une table pour partager une fondue ou plusieurs tapas, et choisissez des saveurs inédites.",
    "is_restaurant": true
  },
  {
    "face": 19,
    "title": "Mini-escapade locale",
    "description": "Planifiez une courte escapade près de chez vous : promenade, visite et arrêt gourmand selon votre budget.",
    "is_restaurant": false
  },
  {
    "face": 20,
    "title": "Rendez-vous de saison",
    "description": "Choisissez une activité de saison, comme un marché, une terrasse, une balade ou une fête locale.",
    "is_restaurant": false
  }
];
export function editableMonthlyActivities(overrides=[]){
 return MONTHLY_ACTIVITIES.map(activity=>{
  const override=overrides.find(o=>Number(o.face)===activity.face);
  return {...activity,...(override?{
   title:override.title,description:override.description,
   is_restaurant:override.is_restaurant,timer_minutes:override.timer_minutes??null
  }:{}),edited:!!override};
 });
}
export function monthlyActivityForRoll(roll){
 if(!roll)return null;
 const initial=MONTHLY_ACTIVITIES[Number(roll.face)-1];
 if(!initial)throw Error('Résultat de sortie invalide');
 return {face:initial.face,
  title:roll.activity_title??initial.title,
  description:roll.activity_description??initial.description,
  is_restaurant:roll.activity_is_restaurant??initial.is_restaurant,
  timer_minutes:roll.activity_timer_minutes??null};
}
