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

function sharedSignature(state){
 const pairs=a=>(a||[]).map(x=>String(x.id||x.user_id)+':'+String(x.status||x.face||x.finished_at||'')).sort();
 return JSON.stringify([
  state.weekly?.id,state.weekly?.face,state.session?.id,
  state.rollStatus?.my_rolled,state.rollStatus?.partner_rolled,state.rollStatus?.both_ready,
  pairs(state.personal),pairs(state.challenges),pairs(state.challengesHistory),
  pairs(state.gifts),pairs(state.sessions),pairs(state.events),pairs(state.occasionRolls),
  pairs(state.advent),pairs(state.feedback),
  state.monthly?.monthly_activity?.id,state.monthly?.monthly_host?.id,
  state.monthly?.monthly_restaurant?.id
 ]);
}
/** Realtime fast path + secure fallback for browsers with sleeping/blocked sockets.
 * Never queries unrevealed partner D6 faces; only my_love_roll_status booleans.
 */
export function createCoupleLiveSync({state,query,rpc,refresh,render,ready,week,month,formDirty,
  visible=()=>!document.hidden,clock=()=>Date.now(),online=()=>navigator.onLine}){
 let inRefresh=false,inProbe=false,refreshAgain=false,lastProbe=0,debounce=null;
 const eligible=()=>visible()&&!!state.user&&ready(state.coupleId,state.members);
 function request(delay=180){
  if(debounce!==null)clearTimeout(debounce);
  debounce=setTimeout(()=>{
   debounce=null;
   reconcile().catch(e=>{if(online())console.warn('Synchronisation LoveRoll',e?.code||e?.message);});
  },delay);
 }
 async function reconcile(){
  if(!eligible())return;
  if(inRefresh||state.diceStatus){refreshAgain=true;return;}
  if(['settings','library','monthly-library','advent-editor','special-editor'].includes(state.tab)&&formDirty()){
   refreshAgain=true;return;
  }
  inRefresh=true;
  const couple=state.coupleId,previous=sharedSignature(state);
  try{
   await refresh();
   if(state.coupleId===couple&&!state.diceStatus&&previous!==sharedSignature(state))render();
  }finally{
   inRefresh=false;
   if(refreshAgain&&!state.diceStatus&&eligible()&&!formDirty()){
    refreshAgain=false;request(350);
   }
  }
 }
 async function probe(force=false){
  if(inProbe||inRefresh||!eligible()||state.diceStatus)return;
  const now=clock(),interval=state.tab==='session'?LIVE_SYNC_INTERVALS.session:LIVE_SYNC_INTERVALS.other;
  if(!force&&now-lastProbe<interval)return;
  lastProbe=now;inProbe=true;
  const couple=state.coupleId,tab=state.tab;
  try{
   const [rolls,sessions]=await Promise.all([
    query('couple_rolls',{eq:{couple_id:couple},columns:'id,kind,key,face'}),
    query('couple_sessions',{eq:{couple_id:couple},columns:'id,finished_at'})
   ]);
   if(state.coupleId!==couple||state.diceStatus)return;
   const active=sessions.find(s=>s.finished_at==null)||null;
   let rollStatus=null,challenges=null;
   if(tab==='session'&&active){
    [rollStatus,challenges]=await Promise.all([
     rpc('my_love_roll_status',{p_session:active.id})
      .then(data=>Array.isArray(data)?data[0]||null:null),
     query('couple_challenges',{eq:{session_id:active.id},columns:'id,status'})
    ]);
   }
   if(state.coupleId!==couple||state.diceStatus)return;
   if(coupleSyncChanged(state,{rolls,sessions,rollStatus,challenges},
    {week:week(),month:month(),tab}))request(0);
  }catch(e){
   if(online())console.warn('Statut des dés en direct',e?.code||e?.message);
  }finally{inProbe=false;}
 }
 return {request,probe};
}
