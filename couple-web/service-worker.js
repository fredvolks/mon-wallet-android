const CACHE='loveroll-static-v8';
const SHELL=['./','./index.html','./app.js','./styles.css','./dice3d.js',
 './special-occasions.mjs','./evening-challenges.mjs','./onboarding.mjs','./wellbeing.mjs','./solo-mode.mjs',
 './config.js','./manifest.webmanifest','./assets/loveroll-hero.webp'];
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