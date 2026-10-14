// Shared HTML controls for LoveRoll JSON library backup/import on phone and iPad.
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','"':'&#39;'}[c]));
export function jsonInputId(kind){
 return kind==='special'?'special-events-json':kind==='advent'?'advent-2026-json':
  kind==='monthly'?'json-input-monthly':'json-input-evening';
}
export function jsonToolsPanel(kind,label,pending=null){
 const manual=kind==='evening'||kind==='monthly';
 return '<section class="card json-tools"><div class="eyebrow">♡ BIBLIOTHÈQUE PRIVÉE</div>'+
 '<h3>Importer / exporter · '+esc(label)+'</h3>'+
 '<p>Enregistrez vos idées en JSON et renouvelez votre bibliothèque sans effacer les anciens tirages.</p>'+
 '<div class="json-tool-buttons">'+
 '<button type="button" data-action="json-export:'+kind+'">↧ Exporter JSON</button>'+
 '<label class="json-file-button">↥ Choisir un fichier'+
 '<input type="file" data-json-file="'+kind+'" accept=".json,application/json,text/plain"></label></div>'+
 (manual?'<label>Ou coller le JSON<textarea id="'+jsonInputId(kind)+
 '" rows="4" spellcheck="false" placeholder="Collez votre liste JSON ici"></textarea></label>':'')+
 '<p class="tiny-note">Durée facultative : <code>"timer_minutes": 5</code> active le minuteur 5 min. <code>null</code> le désactive.</p>'+
 '<button type="button" class="subtle" data-action="json-preview:'+kind+'">Prévisualiser avant import</button>'+
 (pending?.kind===kind?'<div class="json-preview"><strong>♡ '+esc(pending.summary.label)+'</strong>'+
 '<p>'+pending.summary.count+' idées · '+pending.summary.timers+' minuteur(s) configuré(s)</p>'+
 '<div class="json-tool-buttons"><button type="button" data-action="json-apply:'+kind+'">Confirmer l’import</button>'+
 '<button type="button" class="subtle" data-action="json-cancel">Annuler</button></div></div>':'')+
 '<p class="tiny-note">Seul l’administrateur peut importer. Aucun secret, compte ou journal personnel n’est exporté.</p>'+
 '</section>';
}
export async function exportJsonFile(filename,content){
 if(typeof window!=='undefined'&&window.LoveRollNativeAuth?.exportJson){
  window.LoveRollNativeAuth.exportJson(filename,content);return;
 }
 const url=URL.createObjectURL(new Blob([content],{type:'application/json;charset=utf-8'}));
 try{
  const link=document.createElement('a');
  link.href=url;link.download=filename;document.body.appendChild(link);
  link.click();link.remove();
 }finally{setTimeout(()=>URL.revokeObjectURL(url),10000);}
}
