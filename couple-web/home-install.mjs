// Progressive Web App install recommendation shown once per account after onboarding.
// iOS Safari does not expose a programmatic Add-to-Home-Screen prompt.
let deferredInstall=null;
if(typeof window!=='undefined'){
 window.addEventListener('beforeinstallprompt',event=>{
  event.preventDefault();
  deferredInstall=event;
 });
 window.addEventListener('appinstalled',()=>{
  deferredInstall=null;
 });
}
export function isStandalone(){
 return (typeof window!=='undefined'&&window.matchMedia?.('(display-mode: standalone)')?.matches)||
  (typeof navigator!=='undefined'&&navigator.standalone===true);
}
export function isAndroidNative(){
 return typeof window!=='undefined'&&!!window.LoveRollNativeAuth;
}
const keyFor=userId=>'loveroll-install-prompt-'+userId;
export function shouldOfferInstall(userId,store=globalThis.localStorage){
 if(!userId||isStandalone()||isAndroidNative())return false;
 try{return store.getItem(keyFor(userId))!=='dismissed';}catch{return true;}
}
export function dismissInstall(userId,store=globalThis.localStorage){
 if(!userId)return;
 try{store.setItem(keyFor(userId),'dismissed');}catch{}
}
export function installSteps(){
 const agent=typeof navigator!=='undefined'?navigator.userAgent:'';
 if(/iPhone|iPad|iPod/i.test(agent))
  return {system:'ios',text:'Dans Safari, touchez Partager (carré avec une flèche), puis « Sur l’écran d’accueil » et « Ajouter ». Si vous utilisez un autre navigateur, ouvrez LoveRoll dans Safari.'};
 return {system:'android',text:'Dans Chrome, ouvrez le menu ⋮, puis « Installer l’application » ou « Ajouter à l’écran d’accueil ». Confirmez ensuite l’installation.'};
}
export async function requestInstall(){
 if(!deferredInstall)return {prompted:false,...installSteps()};
 const event=deferredInstall;
 deferredInstall=null;
 await event.prompt();
 const choice=await event.userChoice;
 return {prompted:true,accepted:choice?.outcome==='accepted'};
}
export function installOfferHtml(){
 return '<div class="install-overlay" role="dialog" aria-modal="true" aria-label="Ajouter LoveRoll à l’écran d’accueil">'+
 '<section class="install-modal"><div class="install-symbol">♥</div>'+
 '<h2>Gardez LoveRoll près de vous ❤️</h2>'+
 '<p>Vos dés, défis et surprises, accessibles en un seul toucher directement depuis votre écran d’accueil.</p>'+
 '<div class="install-recommend">✦ FORTEMENT RECOMMANDÉ</div>'+
 '<button data-action="install-home" class="install-primary">⊞ Ajouter à l’écran d’accueil</button>'+
 '<button data-action="install-later" class="install-later">Plus tard</button></section></div>';
}
export function installStepsHtml(){
 return '<div class="install-overlay" role="dialog" aria-modal="true" aria-label="Installer LoveRoll">'+
 '<section class="install-modal"><div class="install-symbol">♥</div>'+
 '<h2>Ajouter LoveRoll à votre écran d’accueil</h2>'+
 '<p>'+installSteps().text+'</p>'+
 '<button data-action="install-understood" class="install-primary">J’ai compris ❤️</button>'+
 '<button data-action="install-later" class="install-later">Plus tard</button></section></div>';
}
