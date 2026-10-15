import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=file=>readFileSync(new URL('./'+file,import.meta.url),'utf8');
const html=read('index.html'),app=read('app.js'),sw=read('service-worker.js');
const release=JSON.parse(read('web-version.json'));
test('LoveRoll build versions agree between endpoint, app, HTML and imported modules',()=>{
 const build='2026-10-15.61',tag='20261015-invite61';
 assert.equal(release.build,build);
 assert.match(app,/const WEB_RELEASE='2026-10-15\.61'/);
 assert.match(html,new RegExp('app\\.js\\?v='+tag));
 for(const css of ['styles.css','premium-theme.css','premium-home.css','satin-reference.css','discover.css']){
  assert.ok(html.includes(css+'?v='+tag),css);
 }
 assert.match(app,new RegExp("premium-home\\.mjs\\?v="+tag));
 assert.match(app,new RegExp("dice3d\\.js\\?v="+tag));
});
test('PWA bypasses stale Android network caches while preserving offline fallback',()=>{
 assert.match(sw,/loveroll-static-v61/);
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
