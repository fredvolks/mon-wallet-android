// LoveRoll • 20 special-occasion surprises.
// A suggestion is always optional. Boundaries and mutual enthusiasm come first.
export const SPECIAL_SURPRISES=[
 {title:"Lingerie surprise",level:"Osé",detail:"Préparer une tenue qui vous fait sentir désirables, puis demander à l'autre ce qui lui plaît."},
 {title:"Danse privée",level:"Osé",detail:"Choisir une chanson, tamiser la lumière et offrir une danse de séduction, si vous en avez envie."},
 {title:"Spa privé et peignoirs",level:"Sensuel",detail:"Organiser une parenthèse sans écran avec bain, serviettes chaudes et un moment de détente à deux."},
 {title:"La soirée des fantasmes",level:"Osé",detail:"Chacun partage une envie à explorer et une limite. Choisir ensemble une idée qui enthousiasme les deux."},
 {title:"Massage à l'huile",level:"Sensuel",detail:"Préparer une ambiance relaxante et offrir un massage à tour de rôle, en demandant les préférences de chacun."},
 {title:"Le rendez-vous secret",level:"Coquin",detail:"Envoyer une invitation mystérieuse avec l'heure, la tenue et une surprise adaptée à votre disponibilité."},
 {title:"Strip-tease léger",level:"Osé",detail:"Préparer une performance de séduction ludique, uniquement si la personne qui la propose se sent à l'aise."},
 {title:"Coffret de lingerie",level:"Osé",detail:"Choisir ensemble une pièce de lingerie ou une tenue élégante, sans pression de devoir la porter."},
 {title:"Plaisirs à discuter",level:"Osé",detail:"Parler ouvertement de vos préférences intimes, y compris des envies de plaisir oral, sans obligation de les réaliser."},
 {title:"Jeu de rôles",level:"Coquin",detail:"Imaginer deux personnages pour un rendez-vous complice, puis choisir ensemble jusqu'où va le jeu."},
 {title:"Une nuit rien qu'à nous",level:"Romantique",detail:"Réserver une gardienne et préparer une chambre ou une escapade confortable, sans horaire imposé."},
 {title:"L'enveloppe audacieuse",level:"Osé",detail:"Écrire chacun trois idées de surprises intimes et en tirer une que vous acceptez tous les deux."},
 {title:"Baisers au hasard",level:"Sensuel",detail:"Inventer un petit jeu de baisers et de compliments, au rythme choisi ensemble."},
 {title:"Lumières tamisées",level:"Romantique",detail:"Créer une ambiance raffinée avec musique douce, lumière rose et un dessert à partager."},
 {title:"Soirée sans téléphone",level:"Romantique",detail:"Mettre les écrans de côté pour un repas tranquille, puis choisir une activité à deux."},
 {title:"Une nouvelle tenue",level:"Coquin",detail:"Chacun choisit un style vestimentaire pour séduire l'autre, avec droit de refuser et de changer."},
 {title:"Le choix du partenaire",level:"Coquin",detail:"Proposer deux attentions coquines et laisser l'autre choisir celle qu'il ou elle préfère."},
 {title:"Le menu du désir",level:"Osé",detail:"Créer ensemble une liste de moments tendres, sensuels ou plus intimes, sans imposer de conclusion."},
 {title:"Matinée à deux",level:"Romantique",detail:"Prévoir une gardienne quelques heures et profiter d'un déjeuner prolongé avant un moment en amoureux."},
 {title:"Carte blanche consensuelle",level:"Osé",detail:"Offrir à votre partenaire la possibilité de proposer une surprise intime, avec des limites claires et le droit de dire non."}
];
export const OCCASIONS=[
 {key:"valentine",title:"Saint-Valentin",emoji:"💘",description:"Une fête des amoureux qui peut se préparer à l'avance."},
 {key:"anniversary",title:"Notre anniversaire de couple",emoji:"🥂",description:"La date importante de votre histoire, à choisir ensemble."},
 {key:"mothers_day",title:"Fête des Mères",emoji:"🌷",description:"Une attention pensée pour la personne célébrée."},
 {key:"fathers_day",title:"Fête des Pères",emoji:"🎩",description:"Un rendez-vous sans les obligations du quotidien."},
 {key:"birthday",title:"Mon anniversaire",emoji:"🎂",description:"Une surprise centrée sur la personne qui fête son anniversaire."}
];
export function nthSunday(year,month,n){
 const first=new Date(Date.UTC(year,month-1,1)).getUTCDay();
 return 1+(7-first)%7+7*(n-1);
}
export function occasionDate(kind,year,settings,userId){
 const full=(m,d)=>String(year).padStart(4,"0")+"-"+String(m).padStart(2,"0")+"-"+String(d).padStart(2,"0");
 if(kind==="valentine")return full(2,14);
 if(kind==="mothers_day")return full(5,nthSunday(year,5,2));
 if(kind==="fathers_day")return full(6,nthSunday(year,6,3));
 const md=kind==="anniversary"?settings?.anniversary_md:
    kind==="birthday"?settings?.birthday_by_user?.[userId]:null;
 if(!/^\d{2}-\d{2}$/.test(md||""))return null;
 const candidate=year+"-"+md;
 const parsed=new Date(candidate+"T12:00:00Z");
 if(Number.isNaN(parsed.valueOf())||parsed.toISOString().slice(0,10)!==candidate) {
  // A Feb 29 birthday can be celebrated on Feb 28 in other years.
  return md==="02-29"?full(2,28):null;
 }
 return candidate;
}
export function dueOccasions(now,settings,members){
 const current=new Date(now+"T12:00:00Z");
 const rows=[];
 for(const occasion of OCCASIONS){
  const owners=occasion.key==="birthday"?members.map(m=>m.user_id):[null];
  for(const owner of owners)for(const year of [current.getUTCFullYear()-1,current.getUTCFullYear(),current.getUTCFullYear()+1]){
   const date=occasionDate(occasion.key,year,settings,owner);
   if(!date)continue;
   const diff=Math.round((new Date(date+"T12:00:00Z")-current)/86400000);
   // Time to arrange a babysitter; booking remains possible for a week afterward.
   if(diff>14||diff< -7)continue;
   rows.push({...occasion,actorId:owner,year,date,daysLeft:diff,
    rollKey:occasion.key+(owner?":"+owner:"")+":"+year});
  }
 }
 return rows.sort((a,b)=>Math.abs(a.daysLeft)-Math.abs(b.daysLeft));
}
export function specialSurprise(face){
 if(!Number.isInteger(face)||face<1||face>20)throw Error("Dé spécial invalide");
 return SPECIAL_SURPRISES[face-1];
}
