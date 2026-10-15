// LoveRoll: accessible branded dialogs. This file has no Supabase dependency.
// The caller decides whether to commit a destructive mutation after confirmation.
const KINDS={
 gift:{title:'Offrir 1 défi ?',eyebrow:'UN PETIT CADEAU',icon:'🎁',accept:'♡ Offrir',cancel:'Annuler'},
 couple:{title:'Dissocier notre couple ?',eyebrow:'NOTRE ESPACE À DEUX',icon:'♡',accept:'Dissocier',cancel:'Garder notre couple',danger:true},
 reset:{title:'Confirmer la réinitialisation ?',eyebrow:'GESTION DU JEU',icon:'↺',accept:'Réinitialiser',cancel:'Annuler',danger:true},
 library:{title:'Appliquer les modifications ?',eyebrow:'PERSONNALISATION',icon:'✦',accept:'Enregistrer',cancel:'Annuler'},
 delete:{title:'Confirmer cette suppression ?',eyebrow:'MES DONNÉES',icon:'◇',accept:'Confirmer',cancel:'Annuler',danger:true},
 legacy:{title:'Ouvrir mes anciennes données ?',eyebrow:'MES SOUVENIRS',icon:'♡',accept:'Ouvrir',cancel:'Annuler'}
};
let activeDialog=null;
function createDialog({kind='library',message,title,accept,cancel,copyValue=null}){
 if(activeDialog) return Promise.resolve(false);
 const setting=KINDS[kind]||KINDS.library;
 const overlay=document.createElement('div');
 overlay.className='lr-dialog-overlay';
 overlay.setAttribute('role','presentation');
 overlay.innerHTML='<section class="lr-dialog-card" role="alertdialog" aria-modal="true" aria-labelledby="lr-dialog-title" aria-describedby="lr-dialog-description">'+
  '<button type="button" class="lr-dialog-close" aria-label="Fermer">×</button>'+
  '<div class="lr-dialog-icon" aria-hidden="true"></div>'+
  '<div class="lr-dialog-eyebrow"></div>'+
  '<h2 id="lr-dialog-title"></h2>'+
  '<p id="lr-dialog-description"></p>'+
  '<div class="lr-dialog-copy" hidden><textarea readonly aria-label="Lien à copier" rows="3"></textarea>'+
  '<p class="lr-dialog-copy-hint">Sélectionnez et copiez le lien si la copie automatique est bloquée.</p></div>'+
  '<div class="lr-dialog-actions"><button class="lr-dialog-cancel" type="button"></button>'+
  '<button class="lr-dialog-accept" type="button"></button></div></section>';
 overlay.querySelector('.lr-dialog-icon').textContent=setting.icon;
 overlay.querySelector('.lr-dialog-eyebrow').textContent=setting.eyebrow;
 overlay.querySelector('h2').textContent=title||setting.title;
 overlay.querySelector('#lr-dialog-description').textContent=message||'';
 overlay.querySelector('.lr-dialog-cancel').textContent=cancel||setting.cancel;
 const acceptButton=overlay.querySelector('.lr-dialog-accept');
 acceptButton.textContent=accept||setting.accept;
 if(setting.danger)acceptButton.classList.add('lr-dialog-danger');
 if(copyValue!==null){
  overlay.querySelector('.lr-dialog-copy').hidden=false;
  overlay.querySelector('textarea').value=copyValue;
 }
 const previous=document.activeElement;
 document.body.appendChild(overlay);
 const abort=new AbortController();
 return new Promise(resolve=>{
  const close=result=>{
   if(!overlay.isConnected)return;
   abort.abort();overlay.remove();activeDialog=null;
   if(previous?.isConnected&&typeof previous.focus==='function')previous.focus({preventScroll:true});
   resolve(result);
  };
  activeDialog={close,overlay};
  overlay.querySelector('.lr-dialog-close').addEventListener('click',()=>close(false),{signal:abort.signal});
  overlay.querySelector('.lr-dialog-cancel').addEventListener('click',()=>close(false),{signal:abort.signal});
  acceptButton.addEventListener('click',()=>close(true),{signal:abort.signal});
  overlay.addEventListener('click',event=>{if(event.target===overlay)close(false);},{signal:abort.signal});
  document.addEventListener('keydown',event=>{
   if(event.key==='Escape'){event.preventDefault();close(false);}
   if(event.key==='Tab'){
    const focusables=[...overlay.querySelectorAll('button,textarea:not([hidden])')].filter(el=>!el.closest('[hidden]'));
    const first=focusables[0],last=focusables[focusables.length-1];
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
   }
  },{signal:abort.signal});
  overlay.querySelector('.lr-dialog-close').focus({preventScroll:true});
 });
}
export function premiumConfirm(message,options={}){
 return createDialog({...options,message});
}
export async function premiumCopyLink(url){
 const confirmed=await createDialog({kind:'library',title:'Votre lien personnel ♡',
  message:'Vous pouvez copier ce lien et l’ouvrir dans Chrome ou Safari. Il contient votre invitation personnelle.',
  accept:'Copier le lien',cancel:'Fermer',copyValue:url});
 if(!confirmed)return false;
 try{await navigator.clipboard.writeText(url);return true;}
 catch{
  // Clipboard can be blocked in Messenger. Leave a selectable URL instead of fake success.
  await createDialog({kind:'library',title:'Copie manuelle',message:'La copie automatique est bloquée. Appuyez longuement sur le lien, puis choisissez Copier.',accept:'Compris',cancel:'Fermer',copyValue:url});
  return false;
 }
}
