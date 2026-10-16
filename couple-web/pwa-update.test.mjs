import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
const html=read('index.html'),app=read('app.js'),sw=read('service-worker.js');
const release=JSON.parse(read('web-version.json'));
test('LoveRoll build versions agree between endpoint, app, HTML and imported modules',()=>{
 const build='2026-10-16.103',tag='20261015-no-toast87',appTag='20261016-completed103';
 assert.equal(release.build,build);
 assert.match(app,/const WEB_RELEASE='2026-10-16\.103'/);
 assert.match(html,new RegExp('app\\.js\\?v='+appTag));
 for(const css of ['styles.css','premium-theme.css','satin-reference.css','discover.css']){
  assert.ok(html.includes(css+'?v='+tag),css);
 }
 assert.match(html,/premium-home\.css\?v=20261016-center98/);
 assert.match(app,/premium-home\.mjs\?v=20261015-home92/);
 assert.match(app,new RegExp("dice3d\\.js\\?v="+tag));
 assert.ok(html.includes('evening-step1-mobile-v101.css?v=20261016-step101'));
 assert.ok(html.includes('evening-queue-fixed-v102.css?v=20261016-queue102'));
 assert.ok(html.includes('evening-private-queue.css?v=20261016-completed103'));
});
test('PWA bypasses stale Android network caches while preserving offline fallback',()=>{
 assert.match(sw,/loveroll-static-v103/);
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
