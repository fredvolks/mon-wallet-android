import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=p=>readFileSync(new URL('./'+p,import.meta.url),'utf8');
const html=read('index.html'),css=read('loveroll-splash.css'),js=read('loveroll-splash.js');
const boot=read('boot-recovery.js'),app=read('app.js'),sw=read('service-worker.js');
test('Immediate loading markup contains one accessible no-text heart and no old sentence',()=>{
 assert.match(html,/<main id="app"><div class="lr-splash lr-splash--boot" data-lr-boot/);
 assert.match(html,/class="lr-splash__heart"/);
 assert.equal((html.match(/class="lr-splash__heart"/g)||[]).length,1);
 assert.match(html,/class="lr-splash__back-heart/);
 assert.match(html,/class="lr-splash__ring"/);
 assert.doesNotMatch(html,/Ouverture de votre espace privé/);
 assert.doesNotMatch(html,/data-lr-boot[^>]*>[\s\S]*?Chargement\.\.\./);
});
test('Heart has realistic double-beat, pulsing rings, responsive viewport and reduced-motion support',()=>{
 assert.match(css,/@keyframes lr-splash-beat/);
 assert.match(css,/9%\{transform:scale\(1\.11\)\}/);
 assert.match(css,/29%\{transform:scale\(1\.17\)\}/);
 assert.match(css,/@keyframes lr-splash-wave/);
 assert.match(css,/height:100dvh/);
 assert.match(css,/overflow:hidden/);
 assert.match(css,/min-width:768px/);
 assert.match(css,/max-width:767px/);
 assert.match(css,/prefers-reduced-motion:reduce/);
});
test('Shared loader waits before covering real async work; boot errors still recover',()=>{
 assert.match(js,/const wait=Number\.isFinite/);
 assert.match(js,/function begin\(delay=150\)/);
 assert.match(js,/function end\(id\)/);
 assert.match(js,/finally\{end\(id\);\}/);
 assert.match(js,/window\.LoveRollLoader=\{begin,end,during\}/);
 assert.match(app,/window\.LoveRollLoader\?\.begin\(150\)/);
 assert.match(app,/window\.LoveRollLoader\?\.end\(loaderTicket\)/);
 assert.match(boot,/\[data-lr-boot\]/);
 assert.match(boot,/Réessayer/);
 assert.match(boot,/setTimeout\(show,18000\)/);
});
test('v80 caches all boot dependencies, retains mobile v79 and uses coherent version',()=>{
 assert.equal(JSON.parse(read('web-version.json')).build,'2026-10-16.107');
 for(const f of ['loveroll-splash.css','loveroll-splash.js','mobile-entry-v79.css'])
  assert.match(html,new RegExp(f.replaceAll('.','\\.')+'\\?v=20261015-no-toast87'));
 assert.match(sw,/loveroll-static-v107/);
 assert.match(sw,/\.\/loveroll-splash\.css/);
 assert.match(sw,/\.\/loveroll-splash\.js/);
 assert.doesNotMatch(html,/mobile-entry-v7[678]\.css|entry-viewport-v78\.js/);
 assert.match(html,/boot-recovery\.js\?v=20261015-no-toast87/);
});
