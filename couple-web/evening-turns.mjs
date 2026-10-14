// Same deterministic rotation on both partners' devices.
// Each person rolls challenges they OFFER to the other; receiver answers.
// Completed challenges advance the turn; passed challenges may be redrawn
// by the same giver. The opener goes first, then turns alternate until
// one quota is exhausted, and the other partner finishes the remaining turns.
export function sharedEveningTurns(session,members,rolls,challenges){
 if(!session||!Array.isArray(members)||members.length!==2) return {ready:false,reason:'missing-partner'};
 const first=members.find(m=>m.user_id===session.opened_by)||members[0];
 const second=members.find(m=>m.user_id!==first.user_id);
 const targets=Object.fromEntries(rolls.map(r=>[r.user_id,r.face]));
 const ready=members.every(m=>Number.isInteger(targets[m.user_id])&&targets[m.user_id]>=1&&targets[m.user_id]<=6);
 if(!ready)return {ready:false,reason:'personal-rolls'};
 const completed=Object.fromEntries(members.map(m=>[m.user_id,
  challenges.filter(c=>c.offered_by===m.user_id&&c.status==='completed').length]));
 const pending=challenges.find(c=>c.status==='proposed'||c.status==='accepted')||null;
 const a=first.user_id,b=second.user_id;
 let next=null;
 if(completed[a]>=targets[a]&&completed[b]>=targets[b])next=null;
 else if(completed[a]>=targets[a])next=b;
 else if(completed[b]>=targets[b])next=a;
 else next=completed[a]<=completed[b]?a:b;
 return {ready:true,first:a,next,pending,targets,completed,
  finished:completed[a]>=targets[a]&&completed[b]>=targets[b]};
}
export const evenLabels=Object.freeze({
 weekly:'Notre rituel de la semaine',
 count:'Combien de défis chacun ?',
 library:'Nos 70 défis',
 challenge:'Notre défi surprise',
 monthly:'Notre rendez-vous surprise'
});
