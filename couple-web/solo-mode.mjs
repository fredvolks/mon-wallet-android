// LoveRoll solo discovery mode: all results are local previews, never couple data.
// In particular, no Supabase write is permitted for a solo dice draw.
export function drawSoloFace(sides,random=Math.random){
 if(![6,20].includes(sides))throw Error('Dé non pris en charge');
 const n=random();
 if(!Number.isFinite(n)||n<0||n>=1)throw Error('Source aléatoire invalide');
 return Math.floor(n*sides)+1;
}
export function isSharedCoupleReady(coupleId,members){
 return !!coupleId&&Array.isArray(members)&&members.length===2;
}
export const SOLO_MESSAGE='Mode découverte · Tirages d’essai privés, sans résultat partagé';
