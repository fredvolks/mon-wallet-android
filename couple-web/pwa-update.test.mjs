import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
const html=read('index.html'),app=read('app.js'),sw=read('service-worker.js');
const release=JSON.parse(read('web-version.json'));
test('LoveRoll build versions agree between endpoint, app, HTML and imported modules',()=>{
 const build='2026-10-17.142',tag='20261015-no-toast87',appTag='20261017-messenger142',controlsTag='20261017-controls141',diceTag='20261017-home134';
 assert.equal(release.build,build);
 assert.ok(app.includes('invite-links.mjs?v='+appTag));
 assert.ok(sw.includes('./assets/loveroll-messenger-og-v142.jpg'));
 assert.match(html,/og:image:type" content="image\/jpeg/);
 for(const css of ['controls-rose-v141.css','premium-theme.css','editorial-details.css','entry-ritual-v139.css']){
  assert.ok(html.includes(css+'?v='+controlsTag),css+' must ship with the new palette');
  assert.ok(sw.includes('./'+css),css+' must remain available offline');
 }
 assert.ok(html.includes('ritual-scenes.css?v=20261017-scenes138'));
 assert.ok(sw.includes('./ritual-scenes.css'));
 assert.ok(html.includes('ritual-titles.css?v=20261017-titles136'));
 assert.ok(sw.includes('./ritual-titles.css'));
 assert.ok(app.includes('premium-advent.mjs?v=20261017-titles136'));
 assert.ok(app.includes('couple-live-sync.mjs?v=20261017-advent119'));
 assert.ok(html.includes('premium-advent.css?v=20261017-ritual130'));
 assert.match(app,/const WEB_RELEASE='2026-10-17\.142'/);
 assert.match(html,new RegExp('app\\.js\\?v='+appTag));
 for(const css of ['satin-reference.css','discover.css']){
  assert.ok(html.includes(css+'?v='+tag),css);
 }
 assert.ok(html.includes('styles.css?v=20261017-glass123'));
 assert.match(html,/premium-home\.css\?v=20261017-stats135/);
 assert.match(app,/premium-home\.mjs\?v=20261017-titles136/);
 assert.match(app,new RegExp("dice3d\\.js\\?v="+diceTag));
 assert.ok(app.includes('premium-evening.mjs?v=20261017-scenes138'));
 assert.ok(html.includes('evening-editorial.css?v=20261017-ritual130'));
 assert.ok(app.includes('onboarding.mjs?v=20261017-entry140'));
 assert.ok(app.includes('json-libraries.mjs?v=20261016-restore113'));
 assert.ok(html.includes('settings-premium.css?v=20261017-ritual130'));
 assert.ok(!html.includes('restore-categories-v113.css'));
 assert.ok(!html.includes('evening-step1-mobile-v101.css'));
 assert.ok(!html.includes('evening-queue-fixed-v102.css'));
 assert.ok(!html.includes('evening-private-queue.css'));
 assert.ok(html.includes('surprise-refined-v104.css?v=20261017-ritual130'));
 assert.match(app,/surprise-event-config\.mjs\?v=20261016-surprise109/);
});
test('PWA bypasses stale Android network caches while preserving offline fallback',()=>{
 assert.match(sw,/loveroll-static-v142/);
 assert.match(sw,/fetch\(req,\{cache:'no-store'\}\)/);
 assert.match(sw,/cache\.match\(fallback\.href\)/);
 assert.match(sw,/const cache=await caches\.open\(CACHE\)/);
 assert.match(sw,/\.\/web-version\.json/);
 assert.match(app,/updateViaCache:'none'/);
 assert.match(app,/reloadWebAssets\(\)\.catch\(console\.warn\)/);
});
test('Updating LoveRoll retains caches owned by Mon Wallet and other applications',async()=>{
 const events={},deleted=[];
 const self={registration:{scope:'https://example.test/mon-wallet-android/'},clients:{claim:async()=>{}},addEventListener:(type,callback)=>{events[type]=callback;}};
 vm.runInNewContext(sw,{URL,self,caches:{keys:async()=>['loveroll-static-v121','loveroll-static-v122','loveroll-static-v123','loveroll-static-v124','loveroll-static-v125','loveroll-static-v126','loveroll-static-v127','loveroll-static-v128','loveroll-static-v130','loveroll-static-v131','loveroll-static-v132','loveroll-static-v133','loveroll-static-v134','loveroll-static-v135','loveroll-static-v136','loveroll-static-v137','loveroll-static-v138','loveroll-static-v139','loveroll-static-v140','loveroll-static-v141','loveroll-static-v142','mon-wallet-offline-v9','other-app-data'],delete:async key=>{deleted.push(key);}}});
 let work;events.activate({waitUntil:promise=>{work=promise;}});await work;
 assert.deepEqual(deleted,['loveroll-static-v121','loveroll-static-v122','loveroll-static-v123','loveroll-static-v124','loveroll-static-v125','loveroll-static-v126','loveroll-static-v127','loveroll-static-v128','loveroll-static-v130','loveroll-static-v131','loveroll-static-v132','loveroll-static-v133','loveroll-static-v134','loveroll-static-v135','loveroll-static-v136','loveroll-static-v137','loveroll-static-v138','loveroll-static-v139','loveroll-static-v140','loveroll-static-v141']);
});
test('LoveRoll can force reload from Settings without touching user or challenge data',()=>{
 assert.match(app,/Actualiser LoveRoll/);
 assert.match(app,/if\(verb==='reload-web'\)/);
 assert.match(app,/await reloadWebAssets\(true\)/);
 assert.match(app,/const newest=String\(data\?\.build/);
 assert.match(app,/latest\.searchParams\.set\('web_build',newest\)/);
 assert.match(app,/location\.replace\(latest\.toString\(\)\)/);
 assert.match(app,/state\.diceStatus/);
 assert.match(app,/state\.session/);
 assert.doesNotMatch(app,/localStorage\.clear\(\)|sessionStorage\.clear\(\)/);
});

test('Orphaned active evening NEVER blocks a corrected Web release from loading',()=>{
 const begin=app.indexOf('async function reloadWebAssets(force=false)');
 const end=app.indexOf("window.addEventListener('load'",begin);
 assert.ok(begin>=0&&end>begin,'Version checker must be present');
 const checker=app.slice(begin,end);
 assert.doesNotMatch(checker,/state\.session\s*\|\|/);
 assert.match(checker,/challengeTimer\.running/);
 assert.match(checker,/hasUnsavedForm\(\)/);
 assert.match(checker,/location\.replace\(latest\.toString\(\)\)/);
 assert.match(app,/setInterval\(\(\)=>\{if\(state\.user&&!document\.hidden\)/);
});
