// LoveRoll Web Push registration (Safari home-screen iOS/iPadOS and PWA browsers).
// Android WebView APK does not implement PushManager; Android uses native local reminders.
export function pushEnvironment(){
 const native=!!globalThis.window?.LoveRollNativeAuth;
 const supported=typeof navigator!=='undefined'&&'serviceWorker' in navigator&&
  typeof window!=='undefined'&&'PushManager' in window&&'Notification' in window;
 const apple=/iPhone|iPad|iPod/i.test(navigator?.userAgent||'')||
  (navigator?.platform==='MacIntel'&&navigator?.maxTouchPoints>1);
 const standalone=!!(navigator?.standalone||window?.matchMedia?.('(display-mode: standalone)')?.matches);
 return {native,supported,apple,standalone,permission:
  typeof Notification==='undefined'?'unsupported':Notification.permission};
}
const decodeBase64Url=(value)=>{
 const s=atob(value.replace(/-/g,'+').replace(/_/g,'/')+'='.repeat((4-value.length%4)%4));
 return Uint8Array.from(s,c=>c.charCodeAt(0));
};
const encodeB64=buffer=>btoa(String.fromCharCode(...new Uint8Array(buffer)));
export async function enablePush(client,userId){
 const e=pushEnvironment();
 if(e.native){
  if(!window.LoveRollNativeAuth?.nativePushConfigured?.())
   throw Error('FCM n’est pas configuré dans cette version APK. Les identifiants Firebase publics sont nécessaires.');
  const token=await new Promise((resolve,reject)=>{
   const listener=event=>{clearTimeout(timeout);window.removeEventListener('loveroll-native-push-token',listener);resolve(event.detail?.token);};
   const timeout=setTimeout(()=>{
    window.removeEventListener('loveroll-native-push-token',listener);
    reject(Error('Jeton Firebase non reçu. Vérifiez les autorisations et votre connexion.'));
   },25000);
   window.addEventListener('loveroll-native-push-token',listener,{once:true});
   window.LoveRollNativeAuth.enableRemotePush();
  });
  if(!token)throw Error('Firebase n’a pas fourni de jeton.');
  const {error:claimError}=await client.rpc('love_claim_native_push_token',{p_token:token});
  if(claimError)throw claimError;
  const {error:prefError}=await client.from('love_push_preferences')
   .upsert({user_id:userId,enabled:true},{onConflict:'user_id'});
  if(prefError)throw prefError;
  // The remote weekly push replaces the local Monday reminder: no doubles.
  window.LoveRollNativeAuth?.disableWeeklyReminder?.();
  return true;
 }
 if(!e.supported)throw Error('Ce navigateur ne prend pas en charge les notifications push.');
 if(e.apple&&!e.standalone)throw Error('Sur iPhone/iPad, ajoutez LoveRoll à l’écran d’accueil depuis Safari, puis rouvrez-le.');
 const permission=await Notification.requestPermission();
 if(permission!=='granted')throw Error('Notifications refusées. Autorisez LoveRoll dans les réglages de votre appareil.');
 const {data:publicKey,error:keyError}=await client.rpc('love_push_public_key');
 if(keyError||!publicKey)throw Error('Service push non disponible. Réessayez après quelques instants.');
 const registration=await navigator.serviceWorker.register('./service-worker.js');
 await navigator.serviceWorker.ready;
 let subscription=await registration.pushManager.getSubscription();
 if(!subscription)subscription=await registration.pushManager.subscribe({
  userVisibleOnly:true,applicationServerKey:decodeBase64Url(publicKey)
 });
 const json=subscription.toJSON();
 if(!json.endpoint||!json.keys?.p256dh||!json.keys?.auth)
  throw Error('Abonnement push incomplet : réessayez.');
 const {error:saveError}=await client.from('love_push_subscriptions').upsert({
  user_id:userId,endpoint:json.endpoint,p256dh:json.keys.p256dh,auth_key:json.keys.auth,
  user_agent:navigator.userAgent.slice(0,240)
 },{onConflict:'user_id,endpoint'});
 if(saveError)throw saveError;
 const {error:prefError}=await client.from('love_push_preferences')
  .upsert({user_id:userId,enabled:true},{onConflict:'user_id'});
 if(prefError)throw prefError;
 return true;
}
export async function disablePush(client,userId){
 const e=pushEnvironment();
 if(e.native){
  const token=window.LoveRollNativeAuth?.nativePushToken?.();
  if(token){
   const {error:rm}=await client.from('love_push_native_devices')
    .delete().eq('user_id',userId).eq('device_token',token);
   if(rm)throw rm;
  }
  window.LoveRollNativeAuth?.disableRemotePush?.();
 }else if(e.supported){
  try{const reg=await navigator.serviceWorker.ready;await (await reg.pushManager.getSubscription())?.unsubscribe();}catch{}
 }
 const {error:a}=await client.from('love_push_subscriptions').delete().eq('user_id',userId);
 if(a)throw a;
 const {error:b}=await client.from('love_push_preferences').upsert({user_id:userId,enabled:false},{onConflict:'user_id'});
 if(b)throw b;
 return true;
}
export async function updatePushPreference(client,userId,current,kind,enabled){
 if(!['weekly','invitation','monthly','special'].includes(kind))throw Error('Notification inconnue.');
 const {error}=await client.from('love_push_preferences').upsert({
  user_id:userId,enabled:current.enabled===true,[kind]:enabled
 },{onConflict:'user_id'});
 if(error)throw error;
}
export async function sendTestPush(client){
 const {data,error}=await client.rpc('love_push_test_me');
 if(error)throw error;
 return data;
}
export function notificationSettingsMarkup(prefs={}){
 const e=pushEnvironment(),enabled=!!prefs.enabled;
 const categories=[
  ['weekly','🎲 Dé hebdomadaire','Quand le dé est prêt à être brassé.'],
  ['invitation','♥ Invitation acceptée','Lorsque votre partenaire rejoint votre couple.'],
  ['monthly','✦ Activité du mois','Quand le nouveau mois commence.'],
  ['special','🎁 Événement spécial','Lorsqu’un événement de votre calendrier arrive.']
 ];
 let status='',actions='';
 if(e.native){
  const local=!!window.LoveRollNativeAuth?.weeklyReminderEnabled?.();
  const configured=!!window.LoveRollNativeAuth?.nativePushConfigured?.();
  const token=window.LoveRollNativeAuth?.nativePushToken?.()||'';
  const active=configured&&!!token&&enabled;
  status='<strong>'+(active?'✓ Notifications Android enregistrées':
   configured?'Autorisation ou inscription nécessaire':'Notifications distantes non disponibles')+'</strong>'+
   '<p>'+(configured?'Service Firebase disponible sur cet APK. ':
   'FCM nécessite la configuration Firebase native dans une prochaine version signée. ')+
   (local?'Rappel local du lundi 9 h actif.':'Rappel local du lundi 9 h inactif.')+'</p>';
  actions=(configured?(active?
   '<button class="subtle" data-action="push-disable">Désactiver les notifications</button>'+
   '<button data-action="push-test">Envoyer une notification de test</button>':
   '<button data-action="push-enable">Activer les notifications</button>'):'')+
   (local?'<button class="subtle" data-action="native-notify-disable">Désactiver le rappel local</button>':
   '<button data-action="native-notify-enable">Activer le rappel local</button>');
 }else if(e.apple&&!e.standalone){
  status='<strong>Installer LoveRoll pour les push iPhone/iPad</strong><p>Ouvrez le site dans Safari, puis Partager → Sur l’écran d’accueil. Rouvrez LoveRoll pour activer les notifications.</p>';
  actions='<button data-action="show-install-guide">Voir comment ajouter LoveRoll</button>';
 }else if(!e.supported){
  status='<strong>Push non pris en charge par ce navigateur</strong><p>Utilisez Safari installé sur l’écran d’accueil (iPhone/iPad) ou une PWA prise en charge.</p>';
 }else{
  status='<strong>'+(enabled&&e.permission==='granted'?'✓ Notifications activées':e.permission==='denied'?'Accès refusé dans les réglages de l’appareil':'Notifications désactivées')+'</strong>'+
   '<p>Sur cet appareil · '+(e.permission==='granted'?'Autorisation accordée':'Autorisation non accordée')+'</p>';
  actions=enabled?'<button class="subtle" data-action="push-disable">Désactiver les notifications</button>'+
    '<button data-action="push-test">M’envoyer une notification de test</button>':
    '<button data-action="push-enable">Activer gratuitement les notifications</button>';
 }
 return '<div class="invite-status push-status">'+status+'</div>'+
 '<div class="push-actions">'+actions+'</div>'+
 '<div class="push-options">'+categories.map(([key,title,desc])=>{
 const available=e.native?e.native&&!!window.LoveRollNativeAuth?.nativePushConfigured?.()&&enabled&&!!window.LoveRollNativeAuth?.nativePushToken?.():e.supported&&enabled&&e.permission==='granted';
 return '<label class="push-option"><span><strong>'+title+'</strong><small>'+desc+'</small></span>'+
 '<input type="checkbox" data-push-option="'+key+'"'+(available&&prefs[key]!==false?' checked':'')+
 (!available?' disabled':'')+' aria-label="'+title+'"></label>';
 }).join('')+'</div>'+
 '<p class="tiny-note">Les alertes ne dévoilent aucun contenu intime. Une demande de test acceptée ne garantit pas la réception sur votre appareil.</p>';
}
