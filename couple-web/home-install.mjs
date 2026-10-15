// LoveRoll home-screen install: one optional invitation AFTER finishing signup,
// plus a permanent Settings → Application entry. No duplicate APK/PWA installs.
let deferredInstall=null;
if(typeof window!=='undefined'){
 window.addEventListener('beforeinstallprompt',event=>{
  event.preventDefault();
  deferredInstall=event;
 });
 window.addEventListener('appinstalled',()=>{deferredInstall=null;});
}
export function isStandalone(){
 return !!((typeof window!=='undefined'&&window.matchMedia?.('(display-mode: standalone)')?.matches)||
  (typeof navigator!=='undefined'&&navigator.standalone===true));
}
export function isAndroidNative(){
 return typeof window!=='undefined'&&!!window.LoveRollNativeAuth;
}
export function isEmbeddedBrowser(){
 return typeof navigator!=='undefined'&&/FBAN|FBAV|Messenger|Instagram/i.test(navigator.userAgent||'');
}
export function isIos(){
 if(typeof navigator==='undefined')return false;
 return /iPhone|iPad|iPod/i.test(navigator.userAgent||'')||
  (navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
}
const installKey=id=>'loveroll-install-prompt-'+id;
export function shouldOfferInstall(userId,store=globalThis.localStorage){
 if(!userId||isStandalone()||isAndroidNative())return false;
 try{return !store?.getItem(installKey(userId));}catch{return true;}
}
export function markInstallOffered(userId,store=globalThis.localStorage){
 if(!userId)return;
 try{store?.setItem(installKey(userId),'offered');}catch{}
}
export function dismissInstall(userId,store=globalThis.localStorage){
 if(!userId)return;
 try{store?.setItem(installKey(userId),'dismissed');}catch{}
}
export function installStatus(){
 if(isAndroidNative())return 'apk';
 if(isStandalone())return 'installed';
 if(isEmbeddedBrowser())return 'embedded';
 return 'browser';
}
export function installSteps(){
 if(isAndroidNative())return {system:'apk',
  text:'LoveRoll est déjà installé sur Android. Pour placer son icône sur l’écran d’accueil, ouvrez la liste des applications, maintenez le doigt sur LoveRoll et faites glisser son icône sur l’écran d’accueil.'};
 if(isStandalone())return {system:'installed',
  text:'LoveRoll est déjà ouvert depuis votre écran d’accueil, comme une application.'};
 if(isEmbeddedBrowser())return {system:'embedded',
  text:'Messenger ne permet pas d’installer directement LoveRoll. Copiez l’adresse de LoveRoll, ouvrez-la dans '+(isIos()?'Safari':'Chrome')+', puis choisissez '+(isIos()?'Partager → Sur l’écran d’accueil → Ajouter':'⋮ → Installer l’application ou Ajouter à l’écran d’accueil')+'.'};
 if(isIos())return {system:'ios',
  text:'Dans Safari, touchez Partager (carré avec une flèche), choisissez « Sur l’écran d’accueil », puis « Ajouter ». Si cette option n’est pas disponible, ouvrez LoveRoll dans Safari.'};
 return {system:'android',
  text:'Dans Chrome, touchez ⋮, puis « Installer l’application » ou « Ajouter à l’écran d’accueil ». Confirmez ensuite. Dans certains navigateurs, l’installation directe n’est pas disponible.'};
}
export async function requestInstall(){
 if(isStandalone()||isAndroidNative())return {prompted:false,alreadyInstalled:true,...installSteps()};
 if(!deferredInstall)return {prompted:false,...installSteps()};
 const event=deferredInstall;deferredInstall=null;
 await event.prompt();
 const choice=await event.userChoice;
 return {prompted:true,accepted:choice?.outcome==='accepted'};
}
export function installOfferHtml(){
 return '<div class="install-overlay" role="dialog" aria-modal="true" aria-label="Ajouter LoveRoll à l’écran d’accueil">'+
  '<section class="install-modal"><div class="install-symbol" aria-hidden="true">♥</div>'+
  '<div class="install-recommend">♡ VOTRE ESPACE EST PRÊT</div>'+
  '<h2>LoveRoll à portée de main</h2>'+
  '<p>Ajoutez LoveRoll à votre écran d’accueil pour retrouver vos jeux et vos surprises en un toucher. C’est gratuit et facultatif.</p>'+
  '<button type="button" data-action="install-home" class="install-primary">♡ Ajouter à l’écran d’accueil</button>'+
  '<button type="button" data-action="install-later" class="install-later">Plus tard</button></section></div>';
}
export function installStepsHtml(){
 const steps=installSteps();
 return '<div class="install-overlay" role="dialog" aria-modal="true" aria-label="Installer LoveRoll">'+
  '<section class="install-modal"><div class="install-symbol" aria-hidden="true">♥</div>'+
  '<h2>'+(steps.system==='installed'?'LoveRoll est déjà installé':steps.system==='apk'?'Placer l’icône LoveRoll':'Ajouter LoveRoll à votre écran d’accueil')+'</h2>'+
  '<p>'+steps.text+'</p>'+
  (steps.system==='embedded'?'<button type="button" data-action="install-copy-url" class="install-primary">Copier le lien LoveRoll</button>':'')+
  '<button type="button" data-action="install-understood" class="install-primary">J’ai compris ♡</button>'+
  '<button type="button" data-action="install-later" class="install-later">Fermer</button></section></div>';
}
