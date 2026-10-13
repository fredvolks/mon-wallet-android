const CACHE='loveroll-static-v2';
const SHELL=['./','./index.html','./app.js','./styles.css','./dice3d.js','./special-occasions.mjs','./evening-challenges.mjs','./onboarding.mjs','./config.js','./manifest.webmanifest'];
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL))));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(names=>Promise.all(names.filter(n=>n!==CACHE).map(n=>caches.delete(n))))));
self.addEventListener('fetch',e=>{
 const u=new URL(e.request.url);
 if(e.request.method!=='GET'||u.origin!==self.location.origin||u.search)return;
 const allowed=u.pathname.endsWith('/loveroll-web/')||SHELL.some(p=>u.pathname.endsWith('/loveroll-web/'+p.replace(/^\.\//,'')));
 if(!allowed)return;
 e.respondWith(fetch(e.request).catch(()=>caches.match(e.request)));
});
