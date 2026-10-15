// LoveRoll: one continuous forward 3-axis movement, shared by D6/D20/D35.
// Pure motion logic — the official outcome is ALWAYS supplied by Supabase.
// 3 seconds at full tumbling speed, then a smooth natural 1.5-second slowdown.
export const FAST_ROLL_MS=3000;
export const SLOW_ROLL_MS=1500;
export const ROLL_TOTAL_MS=FAST_ROLL_MS+SLOW_ROLL_MS;
const TWO_PI=2*Math.PI;
const FULL_TRAVEL_MS=FAST_ROLL_MS+SLOW_ROLL_MS/2;

// Integral of velocity (1-smoothstep(u)) on [0,1].
// v(0)=1, v(1)=0 and both derivatives are zero. Thus the initial rate,
// slowdown start and final resting point have no abrupt jumps.
export function rollProgress(elapsedMs){
 const elapsed=Math.max(0,Math.min(ROLL_TOTAL_MS,Number(elapsedMs)||0));
 if(elapsed<=FAST_ROLL_MS)return elapsed/FULL_TRAVEL_MS;
 const u=(elapsed-FAST_ROLL_MS)/SLOW_ROLL_MS;
 const slowdownTravel=SLOW_ROLL_MS*(u-u*u*u+0.5*u*u*u*u);
 return Math.min(1,(FAST_ROLL_MS+slowdownTravel)/FULL_TRAVEL_MS);
}
// Angles are intentionally NOT normalized modulo 2pi mid-flight: wrapping
// would visibly reverse or snap the die at the end. All three Euler axes
// increase monotonically to an orientation with the exact official face up.
export function makeForwardRoll(targetEuler,random=[.2,.4,.6]){
 const end=targetEuler.map((r,i)=>Number(r)||0);
 const start=random.map(n=>(Math.max(0,Math.min(.999999,Number(n)||0)))*TWO_PI);
 const turns=[8,11,7];
 const finalAngles=end.map((target,i)=>{
  let finish=target+TWO_PI*turns[i];
  while(finish<=start[i])finish+=TWO_PI;
  return finish;
 });
 return {start,finish:finalAngles};
}
export function rollAngles(trajectory,elapsedMs){
 const p=rollProgress(elapsedMs);
 return trajectory.start.map((initial,i)=>initial+(trajectory.finish[i]-initial)*p);
}
