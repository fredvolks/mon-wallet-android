import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import {sharedEveningTurns,evenLabels} from './evening-turns.mjs';

const a='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const b='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const members=[{user_id:a},{user_id:b}],id='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const session={id,opened_by:a,starting_user_id:a,paired_turns:true};
const rolls=(aCount,bCount)=>[{user_id:a,face:aCount},{user_id:b,face:bCount}];
const cards=(completedA,completedB)=>[
 ...Array.from({length:completedA},(_,i)=>({id:'a'+i,offered_by:a,recipient:b,status:'completed'})),
 ...Array.from({length:completedB},(_,i)=>({id:'b'+i,offered_by:b,recipient:a,status:'completed'}))
];

test('4 versus 2 alternates A B A B A A on both accounts',()=>{
 const history=[];
 for(let i=0;i<6;i++){
  const state=sharedEveningTurns(session,members,rolls(4,2),history);
  assert.equal(state.ready,true);
  assert.equal(state.pending,null);
  assert.equal(state.first,a);
  assert.deepEqual(state.targets,{[a]:4,[b]:2});
  history.push({id:String(i),offered_by:state.next,recipient:state.next===a?b:a,status:'completed'});
 }
 assert.deepEqual(history.map(x=>x.offered_by),[a,b,a,b,a,a]);
 assert.equal(sharedEveningTurns(session,members,rolls(4,2),history).finished,true);
});

test('starter is a 50/50 server decision; client honours whichever was picked',()=>{
 assert.equal(sharedEveningTurns({...session,starting_user_id:b},members,rolls(2,2),[]).next,b);
 assert.equal(sharedEveningTurns(session,members,rolls(2,2),[]).next,a);
});

test('cannot reveal either count or show turn before BOTH personal rolls',()=>{
 assert.equal(sharedEveningTurns(session,members,[{user_id:a,face:2}],[]).ready,false);
 assert.equal(sharedEveningTurns(session,members,[],[]).reason,'personal-rolls');
});

test('gift with TWO challenges moves one remaining turn to the other, no six minimum',()=>{
 const gift={session_id:id,donor_user_id:a,receiver_user_id:b};
 let turn=sharedEveningTurns(session,members,rolls(2,2),[],[gift]);
 assert.deepEqual(turn.targets,{[a]:1,[b]:3});
 assert.deepEqual(turn.remaining,{[a]:1,[b]:3});
 turn=sharedEveningTurns(session,members,rolls(2,2),cards(1,0),[gift]);
 assert.equal(turn.remaining[a],0);
 assert.equal(turn.next,b);
});

test('pending challenge blocks next roll and cannot itself be gifted',()=>{
 const pending={id:'open',offered_by:a,recipient:b,status:'proposed'};
 const state=sharedEveningTurns(session,members,rolls(1,2),[pending]);
 assert.equal(state.pending?.id,'open');
 assert.equal(state.remaining[a],0);
});

test('monthly 3 gift quota enforced in protected RPC and not tied to number 6',()=>{
 const schema=readFileSync(new URL('./migrations/20261014_random_starter_and_gifts.sql',import.meta.url),'utf8');
 const app=readFileSync(new URL('./app.js',import.meta.url),'utf8');
 assert.match(schema,/order by random\(\) limit 1/);
 assert.match(schema,/starting_user_id/);
 assert.match(schema,/if monthly_count>=3/);
 assert.match(schema,/if face_count\+gifts_in-gifts_out<=done_count\+pending_count/);
 assert.doesNotMatch(schema,/face_count\s*[!=><]+\s*6\s*then/);
 assert.match(schema,/auth\.uid\(\)/);
 assert.match(schema,/for update/);
 assert.match(app,/rpc\('give_love_challenge'/);
 assert.match(app,/turn\.remaining\[me\]>0/);
 assert.match(app,/Nos 70 défis/);
 assert.match(app,/turn\.pending/);
});

test('baseline weekly result remains unique to the couple for one week',()=>{
 const schema=readFileSync(new URL('./schema.sql',import.meta.url),'utf8');
 assert.match(schema,/unique\(couple_id,kind,key\)/);
 assert.match(schema,/when a<50 then 1 when a<75 then 2 when a<87 then 3/);
});
