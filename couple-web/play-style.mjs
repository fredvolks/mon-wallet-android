// Future-only mixing rule: persist per-user preferences now, with no coupling
// to the current Sensuel/Sexuel cards or any active Supabase draw procedure.
export const PLAY_STYLES=Object.freeze(['soft','medium','intense']);
export function normalizePlayStyle(value){
 return PLAY_STYLES.includes(value)?value:'medium';
}
export function futureCoupleStyleMix(styleA,styleB){
 const a=normalizePlayStyle(styleA),b=normalizePlayStyle(styleB);
 return a===b?[{style:a,weight:100}]:[{style:a,weight:50},{style:b,weight:50}];
}
export const playStyleLabel=value=>({soft:'Soft',medium:'Medium',intense:'Intense'})[normalizePlayStyle(value)];
