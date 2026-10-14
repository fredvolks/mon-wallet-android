// LoveRoll — private user-authored special occasion JSON.
export const SPECIAL_EVENT_KEYS=[
 'saint_valentin','anniversaire_de_couple','fete_des_meres',
 'fete_des_peres','mon_anniversaire','anniversaire_de_mon_partenaire'
];
export function validateSpecialEventsJson(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||
   !value.evenements_speciaux||typeof value.evenements_speciaux!=='object'||
   Array.isArray(value.evenements_speciaux))
  throw Error('Le JSON doit contenir « evenements_speciaux ».');
 const events=value.evenements_speciaux;
 const keys=Object.keys(events);
 if(keys.length!==6||SPECIAL_EVENT_KEYS.some(k=>!Object.hasOwn(events,k)))
  throw Error('Le fichier doit contenir exactement les six événements spéciaux.');
 for(const key of SPECIAL_EVENT_KEYS){
  const event=events[key],surprise=event?.surprise,d20=surprise?.D20_activite,d6=surprise?.D6_modificateur;
  if(!event||!surprise||!d20||!d6)throw Error('Données manquantes : '+key);
  for(const [name,str,min,max] of [
   ['Événement',event.evenement,3,120],
   ['Date',event.prochaine_fete,4,50],
   ['Titre D20',d20.titre,3,160],
   ['Description D20',d20.description,10,2000],
   ['Titre D6',d6.titre,3,160]
  ]){
   if(typeof str!=='string'||str.trim().length<min||str.length>max)
    throw Error(name+' invalide : '+key);
  }
  if(!Number.isInteger(d20.resultat)||d20.resultat<1||d20.resultat>20)
   throw Error('La face D20 doit être entre 1 et 20 : '+key);
  if(!Array.isArray(d6.options)||d6.options.length!==6)
   throw Error('Six options D6 requises : '+key);
  for(let face=1;face<=6;face++){
   const option=d6.options[face-1];
   if(!option||typeof option!=='object'||Array.isArray(option)||
     Object.keys(option).length!==1||!Object.hasOwn(option,String(face))||
     typeof option[face]!=='string'||option[face].trim().length<5||
     option[face].length>900)
    throw Error('Option D6 '+face+' invalide : '+key);
  }
 }
 return value;
}
export function specialForEvent(settings,roll){
 if(!roll)return null;
 return {
  title:roll.special_title??null,description:roll.special_description??null,
  modifierTitle:roll.modifier_title??null,modifierText:roll.modifier_text??null
 };
}
