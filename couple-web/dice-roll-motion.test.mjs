import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {FAST_ROLL_MS,SLOW_ROLL_MS,ROLL_TOTAL_MS,rollProgress,rollAngles,makeForwardRoll} from './dice-roll-motion.mjs';
const diceSource=readFileSync(new URL('./dice3d.js',import.meta.url),'utf8');
const appSource=readFileSync(new URL('./app.js',import.meta.url),'utf8');
const TWO_PI=2*Math.PI;
test('Roll rotates at full velocity for exactly 3 seconds, then gradually decelerates',()=>{
 assert.equal(FAST_ROLL_MS,3000);
 assert.equal(SLOW_ROLL_MS,1500);
 assert.equal(ROLL_TOTAL_MS,4500);
 assert.equal(rollProgress(0),0);
 assert.equal(rollProgress(ROLL_TOTAL_MS),1);
 assert.equal(rollProgress(FAST_ROLL_MS),.8);
 const speed=(t)=> (rollProgress(t+1)-rollProgress(t-1))/2;
 const during=[500,1500,2500].map(speed);
 assert.ok(Math.max(...during)-Math.min(...during)<1e-9,'Fast segment must not slow down');
 assert.ok(Math.abs(speed(FAST_ROLL_MS+1)-speed(FAST_ROLL_MS-1))<2e-8,'No velocity jump at slowdown');
 const sampled=[3050,3300,3700,4100,4450].map(speed);
 for(let i=1;i<sampled.length;i++)assert.ok(sampled[i]<sampled[i-1],'Slowdown must progressively lose speed');
 assert.ok(speed(4499)<speed(3500)/500,'Final motion must naturally come close to rest');
 assert.equal(rollProgress(4700),1);
});
test('All three axes move FORWARD continuously, never reverse or snap at the end',()=>{
 const faceQuaternions=[
  [0.16,-.18,-.02], // D6
  [-.95,.65,.04], // D20
  [.075,-.09,1.19], // D35
 ];
 for(const target of faceQuaternions){
  for(const random of [[.2,.4,.6],[.999,.05,.37],[0,.8,.1]]){
   const path=makeForwardRoll(target,random);
   for(let axis=0;axis<3;axis++){
    assert.ok(path.finish[axis]>path.start[axis]);
    // Endpoint equals the target Euler angle modulo full forward revolutions.
    const n=Math.round((path.finish[axis]-target[axis])/TWO_PI);
    assert.ok(n>=7);
    assert.ok(Math.abs(path.finish[axis]-target[axis]-n*TWO_PI)<1e-11);
   }
   let prior=rollAngles(path,0);
   for(let ms=16;ms<=ROLL_TOTAL_MS+16;ms+=16){
    const next=rollAngles(path,ms);
    for(let i=0;i<3;i++)assert.ok(next[i]>=prior[i]-1e-12,'Reverse direction found');
    prior=next;
   }
   const final=rollAngles(path,ROLL_TOTAL_MS);
   for(let i=0;i<3;i++)assert.ok(Math.abs(final[i]-path.finish[i])<1e-10);
  }
 }
});
test('No end-of-roll quaternion interpolation nor result-number overlay',()=>{
 assert.match(diceSource,/rollAngles\(trajectory,elapsed\)/);
 assert.match(diceSource,/built\.group\.quaternion\.setFromEuler\(euler\)/);
 assert.doesNotMatch(diceSource,/orientationAtSettle|settleStart|slerpQuaternions\(orientationAtSettle/);
 assert.doesNotMatch(diceSource,/result-number-overlay|rolling-result-overlay/);
 assert.match(appSource,/import \{ROLL_TOTAL_MS\} from '\.\/dice-roll-motion\.mjs'/);
 assert.equal((appSource.match(/setTimeout\(resolve,window\.matchMedia\?/g)||[]).length,2);
});
