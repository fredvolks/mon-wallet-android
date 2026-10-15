// Pure cross-device state comparison. Never expose the other partner's
// personal D6 face before the server reports both_ready.
const keyOf=row=>row?.id||null;
const stableStatus=status=>status?
 [Boolean(status.my_rolled),Boolean(status.partner_rolled),Boolean(status.both_ready)].join(':'):'none';
const challengeSignature=rows=>(rows||[]).map(r=>r.id+':'+r.status)
 .sort().join('|');
export function coupleSyncChanged(local,remote,{week,month,tab}={}){
 const rolls=remote.rolls||[];
 const weekly=rolls.find(r=>r.kind==='weekly'&&r.key===week)||null;
 if(keyOf(weekly)!==keyOf(local.weekly)||weekly?.face!==local.weekly?.face)return true;
 if(tab==='special'){
  const r=rolls.filter(v=>v.key===month);
  const kinds=['monthly_activity','monthly_host','monthly_restaurant'];
  if(kinds.some(kind=>{
   const newest=r.find(x=>x.kind===kind)||null,old=local.monthly?.[kind]||null;
   return keyOf(newest)!==keyOf(old)||newest?.face!==old?.face;
  }))return true;
 }
 const session=(remote.sessions||[]).find(s=>s.finished_at==null)||null;
 if(keyOf(session)!==keyOf(local.session))return true;
 if(tab==='session'&&session){
  // Only public yes/no statuses; NEVER query private partner faces here.
  if(stableStatus(remote.rollStatus)!==stableStatus(local.rollStatus))return true;
  if(remote.challenges&&challengeSignature(remote.challenges)!==challengeSignature(local.challenges))return true;
 }
 return false;
}
export const LIVE_SYNC_INTERVALS=Object.freeze({session:3200,other:10000});
