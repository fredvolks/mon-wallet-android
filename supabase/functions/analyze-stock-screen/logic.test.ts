import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validRequest,validateOpinions} from './logic.ts';
const row={listing:'NASDAQ:DEMO',name:'Demo',industry:'Industrial Machinery',sector:'Producer Manufacturing',price:12,currency:'USD',
 values:{price_earnings_ttm:10,return_on_equity:15,debt_to_equity:.3,total_revenue_yoy_growth_ttm:12,earnings_per_share_diluted_yoy_growth_ttm:20,net_margin:8,'Perf.6M':9},flags:[],sourceUrl:'https://www.tradingview.com/symbols/NASDAQ-DEMO/'};
const request=()=>({version:1,generatedAt:Date.now(),budgetCad:500,horizon:'3 à 5 ans',risk:'Modéré',stocks:[structuredClone(row)]});
const opinion=()=>({listing:row.listing,priority:75,verdict:'À approfondir',reason:'Croissance des revenus et faible dette.',risk:'Cycle industriel.',nextCheck:'Vérifier profits normalisés.'});
test('accepts finite dated evidence; rejects invalid or stale payloads',()=>{
 assert.equal(validRequest(request()),true);
 assert.equal(validRequest({...request(),generatedAt:Date.now()-32*86400000}),false);
 assert.equal(validRequest({...request(),budgetCad:-1}),false);
 assert.equal(validRequest({...request(),version:2}),false);
});
test('hard rules cannot be bypassed by a client',()=>{
 for(const patch of [{listing:'OTC:DEMO'},{price:.99},{industry:'Biotechnology'},{industry:'Pharmaceuticals: Major'}, {sourceUrl:'https://evil.example/'},{currency:'EUR'},{values:{x:Infinity}}]){
  assert.equal(validRequest({...request(),stocks:[{...row,...patch}]}),false);
 }
 assert.equal(validRequest({...request(),stocks:[row,row]}),false);
 assert.equal(validRequest({...request(),stocks:Array.from({length:21},(_,i)=>({...row,listing:'NASDAQ:X'+i,sourceUrl:'https://www.tradingview.com/symbols/NASDAQ-X'+i+'/'}))}),false);
});
test('refuses omissions, duplicates, extra identities and out-of-range opinions',()=>{
 const r=request();assert.ok(validateOpinions({opinions:[opinion()]},r));
 for(const a of [[],[opinion(),opinion()],[{...opinion(),listing:'NASDAQ:FAKE'}],[{...opinion(),priority:101}],[{...opinion(),priority:NaN}],[{...opinion(),verdict:'Achat urgent'}]])assert.equal(validateOpinions({opinions:a},r),null);
});
test('missing fundamentals or insufficient verdict never receive a ranked score',()=>{
 const r=request();delete (r.stocks[0].values as Record<string,number>).price_earnings_ttm;
 const got=validateOpinions({opinions:[opinion()]},r)!;assert.equal(got[0].priority,null);assert.equal(got[0].verdict,'Données insuffisantes');
 assert.equal(validateOpinions({opinions:[{...opinion(),verdict:'Données insuffisantes'}]},request())![0].priority,null);
});
