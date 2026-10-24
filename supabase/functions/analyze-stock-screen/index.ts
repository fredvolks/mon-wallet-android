import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import {instructions,schema,validRequest,validateOpinions,VERSION} from './logic.ts';
function keys(name:string):Record<string,string>{try{return JSON.parse(Deno.env.get(name)||'{}');}catch{return {};}}
const err=(error:string,status:number,code?:string)=>Response.json({error,code},{status});
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return err('POST requis',405);
 const authorization=req.headers.get('Authorization');
 if(!authorization?.startsWith('Bearer '))return err('Connecte-toi pour analyser les 200 candidats.',401);
 const project=Deno.env.get('SUPABASE_URL'), pub=keys('SUPABASE_PUBLISHABLE_KEYS').default||Deno.env.get('SUPABASE_ANON_KEY');
 const secret=keys('SUPABASE_SECRET_KEYS').default||Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 if(!project||!pub||!secret)return err('Service IA non configuré.',503);
 let claimKey:string|undefined,claimToken:string|undefined,owner:string|undefined;
 async function rpc(name:string,body:unknown){
  const r=await fetch(`${project}/rest/v1/rpc/${name}`,{method:'POST',headers:{apikey:secret!,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
  if(!r.ok)throw new Error('Stock screen storage unavailable');return await r.json();
 }
 async function finish(result:unknown){
  if(!owner||!claimKey||!claimToken)return false;
  return await rpc('finish_wallet_screen_batch',{owner_id:owner,batch_key:claimKey,lease_token:claimToken,batch_result:result});
 }
 try{
  const auth=await fetch(`${project}/auth/v1/user`,{headers:{apikey:pub,Authorization:authorization},signal:AbortSignal.timeout(8000)});
  if(!auth.ok)return err('Session expirée. Reconnecte-toi.',401);
  const user=await auth.json();if(typeof user.id!=='string'||user.is_anonymous===true)return err('Compte connecté requis.',401);owner=user.id;
  if(Number(req.headers.get('Content-Length')||0)>90000)return err('Groupe trop volumineux.',413);
  const raw=await req.text();if(new TextEncoder().encode(raw).length>90000)return err('Groupe trop volumineux.',413);
  let input;try{input=JSON.parse(raw);}catch{return err('Dossier invalide.',400);}
  if(!validRequest(input))return err('Actualise le préfiltre. Les groupes doivent contenir au plus 20 titres US/Canada, ≥1 $, hors biotech.',400);
  const key=Deno.env.get('OPENAI_API_KEY');if(!key)return err('Clé IA du service absente. Le préfiltre gratuit reste disponible.',503,'AI_UNAVAILABLE');
  // A server-chosen model: clients cannot select an expensive model or alter output budgets.
  const model=Deno.env.get('OPENAI_SCREEN_MODEL')||'gpt-6-luna';
  const bytes=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify({version:VERSION,model,input})));
  claimKey=Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
  const claim=await rpc('claim_wallet_screen_batch',{owner_id:owner,batch_key:claimKey});
  if(claim.status==='cached')return Response.json(claim.result);
  if(claim.status==='busy')return err('Ce groupe est déjà en cours. Attends deux minutes puis reprends.',409,'AI_BATCH_BUSY');
  if(claim.status==='quota')return err('Limite quotidienne IA atteinte. Les groupes terminés sont conservés; reprends demain.',429,'AI_QUOTA');
  if(claim.status!=='claimed'||typeof claim.token!=='string')throw new Error('Invalid claim');claimToken=claim.token;
  const api=await fetch('https://api.openai.com/v1/responses',{method:'POST',headers:{Authorization:`Bearer ${key}`,'Content-Type':'application/json'},
   body:JSON.stringify({model,store:false,max_output_tokens:6500,reasoning:{effort:'low'},instructions,input:JSON.stringify(input),
    text:{format:{type:'json_schema',name:'wallet_screen_opinions',strict:true,schema}}}),signal:AbortSignal.timeout(80000)});
  const payload=await api.json().catch(()=>({}));
  if(!api.ok){
   await finish(null);claimToken=undefined;
   if(['credit_balance_exhausted','insufficient_quota'].includes(payload?.error?.code))return err('Crédits IA du service épuisés. Le scan gratuit et les analyses terminées restent disponibles.',503,'AI_CREDITS_EXHAUSTED');
   return err('Service IA indisponible. Aucun classement inventé; reprends les groupes restants plus tard.',503,'AI_UNAVAILABLE');
  }
  if(payload.status!=='completed')throw new Error('Incomplete model response');
  const text=payload.output?.flatMap((p:{content?:Array<{type:string;text?:string}>})=>p.content||[]).filter((p:{type:string})=>p.type==='output_text').map((p:{text:string})=>p.text).join('');
  const opinions=validateOpinions(JSON.parse(text||'{}'),input);if(!opinions)throw new Error('Invalid model response');
  const result={opinions:opinions.map(s=>({...s,model,createdAt:Date.now()})),model,createdAt:Date.now(),usage:payload.usage||null};
  if(await finish(result)!==true)throw new Error('Cache write not confirmed');claimToken=undefined;
  return Response.json(result);
 }catch{
  // Failure releases the lease; completed groups survive a lost client response.
  if(claimToken)await finish(null).catch(()=>{});
  return err('Groupe non terminé. Les autres résultats sont conservés. Reprends plus tard.',503,'AI_UNAVAILABLE');
 }
});
