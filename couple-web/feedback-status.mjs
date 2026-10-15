// Guard against a half-loaded/null aftercare RPC response. A missing
// session or feedback object is ordinary during reset, reload and sync.
export function feedbackItemsFor(status,sessionId){
 if(!sessionId||!status||typeof status!=='object')return null;
 if(status.sessionId!==sessionId)return null;
 return Array.isArray(status.items)?status.items:null;
}
