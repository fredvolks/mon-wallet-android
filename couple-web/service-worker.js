// LoveRoll v63 accessibility hotfix: refresh Surprise dice button hit areas.
const CACHE='loveroll-static-v72';
const SHELL=['./','./index.html','./app.js','./boot-recovery.js','./styles.css','./premium-theme.css','./premium-home.css','./premium-couple-pages.css','./premium-dialogs.css','./premium-dialogs.mjs','./premium-advent.css','./premium-advent.mjs','./settings-premium.css','./invite-account.css','./invitation-poster.css','./responsive-fullscreen.css','./responsive-tablet-desktop.css','./premium-evening.mjs','./premium-surprise.mjs','./satin-reference.css','./premium-home.mjs','./discover.css','./approved-artwork.css','./demo-couple.css','./demo-couple.mjs','./assets/loveroll-die-approved.webp','./assets/loveroll-invitation-hero-hd.webp','./discover.mjs','./invite-links.mjs','./email-auth.mjs','./dice3d.js','./dice-roll-motion.mjs','./couple-live-sync.mjs','./couple-reset-view.mjs','./evening-private-queue.mjs','./feedback-status.mjs','./evening-private-queue.css','./dice-no-overlay.css',
 './special-occasions.mjs','./evening-challenges.mjs','./monthly-activities.mjs','./advent-calendar.mjs','./private-special-events.mjs','./onboarding.mjs','./evening-turns.mjs','./wellbeing.mjs',
 './config.js','./invite-email.mjs','./push-notifications.mjs','./home-install.mjs','./messenger-browser.mjs','./couple-dashboard.mjs','./web-version.json','./manifest.webmanifest','./assets/loveroll-icon-192.png','./assets/loveroll-icon-512.png','./assets/loveroll-hero.webp','./assets/loveroll-messenger-og-v43.jpg'];
const root=new URL('./',self.registration.scope);
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(names=>
 Promise.all(names.filter(n=>n!==CACHE).map(n=>caches.delete(n)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
 const req=e.request,u=new URL(req.url);
 if(req.method!=='GET'||u.origin!==root.origin)return;
 const asset=SHELL.some(path=>new URL(path,root).pathname===u.pathname);
 const isHome=u.pathname===root.pathname&&req.mode==='navigate';
 if(!asset&&!isHome)return;
 // Always fetch current content from the network rather than Android WebView's
 // stale HTTP cache; app assets are pre-cached strictly for offline fallback.
 e.respondWith(fetch(req,{cache:'no-store'}).then(response=>{
  if(!response.ok)throw Error('Asset temporarily unavailable: '+response.status);
  return response;
 }).catch(async()=>{
  const fallback=new URL(isHome?'./index.html':u.pathname,root);
  return (await caches.match(fallback.href))||Response.error();
 }));
});
// Incoming Web Push notifications (iOS/iPadOS home-screen PWA and supported browsers).
self.addEventListener('push',event=>{
 let message={};
 try{message=event.data?.json()||{};}catch{}
 const title=typeof message.title==='string'&&message.title.length<120?
  message.title:'LoveRoll ❤️';
 const body=typeof message.body==='string'&&message.body.length<300?
  message.body:'Votre espace à deux vous attend.';
 const kind=['weekly','invitation','monthly','special'].includes(message.tag)?message.tag:'home';
 const tab=kind==='weekly'?'session':kind==='monthly'||kind==='special'?'special':'home';
 const url=new URL('./?open='+tab,self.registration.scope).href;
 event.waitUntil(self.registration.showNotification(title,{
  body,tag:String(message.tag||'loveroll').slice(0,60),
  icon:new URL('./assets/loveroll-hero.webp',self.registration.scope).href,
  badge:new URL('./assets/loveroll-hero.webp',self.registration.scope).href,
  data:{url}
 }));
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 const candidate=event.notification.data?.url||'./';
 const parsed=new URL(candidate,self.registration.scope);
 const origin=new URL(self.registration.scope).origin;
 const destination=parsed.origin===origin&&parsed.pathname.startsWith(new URL(self.registration.scope).pathname)?parsed.href:new URL('./',self.registration.scope).href;
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  for(const client of windows){
   if(new URL(client.url).origin===new URL(destination).origin){
    await client.focus();if('navigate' in client)await client.navigate(destination);return;
   }
  }
  if(self.clients.openWindow)await self.clients.openWindow(destination);
 })());
});
