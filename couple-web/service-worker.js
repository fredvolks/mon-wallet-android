const CACHE='loveroll-static-v22';
const SHELL=['./','./index.html','./app.js','./styles.css','./dice3d.js',
 './special-occasions.mjs','./evening-challenges.mjs','./monthly-activities.mjs','./advent-calendar.mjs','./private-special-events.mjs','./onboarding.mjs','./evening-turns.mjs','./wellbeing.mjs',
 './config.js','./invite-email.mjs','./push-notifications.mjs','./home-install.mjs','./couple-dashboard.mjs','./manifest.webmanifest','./assets/loveroll-hero.webp','./assets/loveroll-premium-scene.png'];
const root=new URL('./',self.registration.scope);
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(names=>
 Promise.all(names.filter(n=>n!==CACHE).map(n=>caches.delete(n)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
 const req=e.request,u=new URL(req.url);
 if(req.method!=='GET'||u.origin!==root.origin||u.search)return;
 const asset=SHELL.some(path=>new URL(path,root).pathname===u.pathname);
 if(!asset)return;
 e.respondWith(fetch(req).catch(()=>caches.match(req)));
});
// Incoming Web Push notifications (iOS/iPadOS home-screen PWA and supported browsers).
self.addEventListener('push',event=>{
 let message={};
 try{message=event.data?.json()||{};}catch{}
 const title=typeof message.title==='string'&&message.title.length<120?
  message.title:'LoveRoll ❤️';
 const body=typeof message.body==='string'&&message.body.length<300?
  message.body:'Votre espace à deux vous attend.';
 const url=new URL('./',self.registration.scope).href;
 event.waitUntil(self.registration.showNotification(title,{
  body,tag:String(message.tag||'loveroll').slice(0,60),
  icon:new URL('./assets/loveroll-hero.webp',self.registration.scope).href,
  badge:new URL('./assets/loveroll-hero.webp',self.registration.scope).href,
  data:{url}
 }));
});
self.addEventListener('notificationclick',event=>{
 event.notification.close();
 const destination=new URL('./',self.registration.scope).href;
 event.waitUntil((async()=>{
  const windows=await self.clients.matchAll({type:'window',includeUncontrolled:true});
  for(const client of windows){
   if(new URL(client.url).origin===new URL(destination).origin){
    await client.focus();return;
   }
  }
  if(self.clients.openWindow)await self.clients.openWindow(destination);
 })());
});
