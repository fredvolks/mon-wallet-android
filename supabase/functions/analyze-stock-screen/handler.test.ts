import {test} from 'node:test';
import assert from 'node:assert/strict';
import {handler} from './handler.ts';
const env:Record<string,string>={SUPABASE_URL:'https://project.example',SUPABASE_ANON_KEY:'public-fixture',SUPABASE_SERVICE_ROLE_KEY:'private-fixture',OPENAI_API_KEY:'openai-fixture'};
(globalThis as unknown as {Deno:unknown}).Deno={env:{get:(k:string)=>env[k]}};
const originalFetch=globalThis.fetch;
function input(start=0,count=20){return {version:1,generatedAt:Date.now(),budgetCad:500,horizon:'3 à 5 ans',risk:'Modéré',stocks:Array.from({length:count},(_,i)=>({listing:`NYSE:X${i+start}`,name:`Company ${i+start}`,sector:'Producer Manufacturing',industry:'Industrial Machinery',price:20,currency:'USD',values:{price_earnings_ttm:10,return_on_equity:15,debt_to_equity:.3,total_revenue_yoy_growth_ttm:12,earnings_per_share_diluted_yoy_growth_ttm:20,net_margin:8,'Perf.6M':9},flags:[],sourceUrl:`https://www.tradingview.com/symbols/NYSE-X${i+start}/`}))};}
function request(body:unknown,auth=true){return new Request('https://project.example/functions/v1/analyze-stock-screen',{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer user-fixture'}:{})},body:JSON.stringify(body)});}
test('authenticated 200-stock flow, cached retry and invalid evidence never double-spend',async()=>{
 let calls=0,claims=0;const cache=new Map<string,unknown>();let activeKey='';
 globalThis.fetch=(async(url:RequestInfo|URL,options?:RequestInit)=>{
  const u=String(url);const body=options?.body?JSON.parse(String(options.body)):{};
  if(u.endsWith('/auth/v1/user'))return Response.json({id:'user-a',is_anonymous:false});
  if(u.endsWith('/claim_wallet_screen_batch')){claims++;activeKey=body.batch_key;return Response.json(cache.has(activeKey)?{status:'cached',result:cache.get(activeKey)}:{status:'claimed',token:'lease-fixture'});}
  if(u.endsWith('/finish_wallet_screen_batch')){if(body.batch_result)cache.set(body.batch_key,body.batch_result);return Response.json(true);}
  if(u==='https://api.openai.com/v1/responses'){
   calls++;const evidence=JSON.parse(body.input);
   assert.ok(evidence.stocks.length<=20);assert.equal(body.store,false);assert.equal(body.max_output_tokens,6500);
   const opinions=evidence.stocks.map((s:{listing:string})=>({listing:s.listing,priority:70,verdict:'À approfondir',reason:'Croissance et dette.',risk:'Données non normalisées.',nextCheck:'Lire les résultats.'}));
   return Response.json({status:'completed',output:[{content:[{type:'output_text',text:JSON.stringify({opinions})}]}],usage:{input_tokens:300,output_tokens:200}});
  }
  throw Error('Unexpected request '+u);
 }) as typeof fetch;
 try{
  assert.equal((await handler(request(input(),false))).status,401);assert.equal(claims,0);
  const invalid=input();invalid.stocks[0].industry='Biotechnology';assert.equal((await handler(request(invalid))).status,400);assert.equal(calls,0);
  let total=0;
  for(let i=0;i<200;i+=20){const evidence=input(i);const response=await handler(request(evidence));assert.equal(response.status,200);const result=await response.json();total+=result.opinions.length;
   const retry=await handler(request(evidence));assert.equal(retry.status,200);
  }
  assert.equal(total,200);assert.equal(calls,10);assert.equal(cache.size,10);
 }finally{globalThis.fetch=originalFetch;}
});
test('credits exhausted releases lease and returns explicit error without fabricated opinions',async()=>{
 let released=false;
 globalThis.fetch=(async(url:RequestInfo|URL,options?:RequestInit)=>{
  const u=String(url);
  if(u.endsWith('/auth/v1/user'))return Response.json({id:'user-a'});
  if(u.endsWith('/claim_wallet_screen_batch'))return Response.json({status:'claimed',token:'lease-fixture'});
  if(u.endsWith('/finish_wallet_screen_batch')){released=JSON.parse(String(options?.body)).batch_result===null;return Response.json(true);}
  return Response.json({error:{code:'credit_balance_exhausted'}},{status:429});
 }) as typeof fetch;
 try{const r=await handler(request(input()));assert.equal(r.status,503);const b=await r.json();assert.equal(b.code,'AI_CREDITS_EXHAUSTED');assert.equal(b.opinions,undefined);assert.equal(released,true);}
 finally{globalThis.fetch=originalFetch;}
});
