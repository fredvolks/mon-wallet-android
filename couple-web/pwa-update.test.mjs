import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
const html=read('index.html'),app=read('app.js'),sw=read('service-worker.js');
const release=JSON.parse(read('web-version.json'));
test('LoveRoll build versions agree between endpoint, app, HTML and imported modules',()=>{
 const build='2026-10-17.118',tag='20261015-no-toast87',appTag='20261017-entry118',diceTag='20261017-editorial117';
 assert.equal(release.build,build);
 assert.match(app,/const WEB_RELEASE='2026-10-17\.118'/);
 assert.match(html,new RegExp('app\\.js\\?v='+appTag));
 for(const css of ['styles.css','satin-reference.css','discover.css']){
  assert.ok(html.includes(css+'?v='+tag),css);
 }
 assert.match(html,/premium-home\.css\?v=20261017-editorial117/);
 assert.match(app,/premium-home\.mjs\?v=20261017-entry118/);
 assert.match(app,new RegExp("dice3d\\.js\\?v="+diceTag));
 assert.ok(app.includes('premium-evening.mjs?v=20261017-editorial117'));
 assert.ok(html.includes('evening-editorial.css?v=20261017-editorial117'));
 assert.ok(app.includes('onboarding.mjs?v=20261016-gender111'));
 assert.ok(app.includes('json-libraries.mjs?v=20261016-restore113'));
 assert.ok(html.includes('settings-premium.css?v=20261016-gender111'));
 assert.ok(!html.includes('restore-categories-v113.css'));
 assert.ok(!html.includes('evening-step1-mobile-v101.css'));
 assert.ok(!html.includes('evening-queue-fixed-v102.css'));
 assert.ok(!html.includes('evening-private-queue.css'));
 assert.ok(html.includes('surprise-refined-v104.css?v=20261016-surprise109'));
 assert.match(app,/surprise-event-config\.mjs\?v=20261016-surprise109/);
});
test('PWA bypasses stale Android network caches while preserving offline fallback',()=>{
 assert.match(sw,/loveroll-static-v118/);
 assert.match(sw,/fetch\(req,\{cache:'no-store'\}\)/);
 assert.match(sw,/caches\.match\(fallback\.href\)/);
 assert.match(sw,/\.\/web-version\.json/);
 assert.match(app,/updateViaCache:'none'/);
 assert.match(app,/reloadWebAssets\(\)\.catch\(console\.warn\)/);
});
test('LoveRoll can force reload from Settings without touching user or challenge data',()=>{
 assert.match(app,/Actualiser LoveRoll maintenant/);
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
