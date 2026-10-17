import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=path=>readFileSync(new URL('./'+path,import.meta.url),'utf8');
const web=read('index.html'),css=read('loveroll-splash.css');
const native=read('../app/src/main/java/ca/desadeux/jeu/LoveRollHeartSplashView.java');
const activity=read('../app/src/main/java/ca/desadeux/jeu/ConnectedLoveRollActivity.java');
const gradle=read('../app/build.gradle');
test('Native heart is the EXACT same Bézier shape as the web SVG',()=>{
 const webD=/class="lr-splash__heart"[\s\S]*?<path d="([^"]+)"/.exec(web)?.[1];
 assert.ok(webD,'Web SVG path not found');
 assert.equal(webD,
  'M100 179 C80 161 12 116 12 70 C12 39 33 15 65 15 C82 15 94 23 100 38 C106 23 118 15 135 15 C167 15 188 39 188 70 C188 116 120 161 100 179 Z');
 for(const part of [
  'moveTo(100,179)','cubicTo(80,161,12,116,12,70)',
  'cubicTo(12,39,33,15,65,15)','cubicTo(82,15,94,23,100,38)',
  'cubicTo(106,23,118,15,135,15)',
  'cubicTo(167,15,188,39,188,70)',
  'cubicTo(188,116,120,161,100,179)'
 ])assert.ok(native.includes(part),'Missing native Bézier segment '+part);
});
test('Native glossy pink gradient and white reflections match published web SVG',()=>{
 for(const hex of ['ffcced','ff82bb','ff3f94','e81b79','b7004d']){
  assert.ok(web.includes('#'+hex),'Web missing '+hex);
  assert.ok(native.includes('0xff'+hex),'Native missing '+hex);
 }
 assert.match(web,/M34 68 C34 47 47 35 65 35 C78 35 85 40 89 50/);
 assert.match(web,/M115 48 C125 33 154 33 165 60/);
 assert.match(native,/reflectionLeft\.cubicTo\(34,47,47,35,65,35\)/);
 assert.match(native,/reflectionRight\.cubicTo\(125,33,154,33,165,60\)/);
 assert.match(native,/ovalGradient\(canvas/);
 assert.match(native,/backdropHearts\(canvas/);
 assert.match(native,/particles\(canvas/);
 assert.match(css,/@keyframes lr-splash-beat/);
 assert.match(native,/BEAT_MS=1250L/);
 assert.match(native,/WAVE_MS=2600L/);
});
test('Native loading screen never flashes an unfinished WebView frame',()=>{
 assert.doesNotMatch(activity,/private static final class BeatingHeartSplash/);
 assert.match(activity,/new LoveRollHeartSplashView\(this\)/);
 assert.match(activity,/postVisualStateCallback/);
 assert.match(activity,/nativeSplash\.animate\(\)\.alpha\(0f\)\.setDuration\(200\)/);
 assert.match(activity,/nativeSplash\.resumeHeart\(\)/);
 assert.match(activity,/nativeSplash\.pauseHeart\(\)/);
 assert.doesNotMatch(activity,/nativeSplash\.postDelayed/);
 assert.match(activity,/settings\.setCacheMode\(WebSettings\.LOAD_NO_CACHE\)/);
 assert.match(activity,/web\.addJavascriptInterface\(new NativeAuthBridge\(\),"LoveRollNativeAuth"\)/);
});
test('Signed Android update version is 0.13.9',()=>{
 assert.match(gradle,/versionCode 130009; versionName '0\.13\.9'/);
 assert.equal((web.match(/lr-splash__heart/g)||[]).length>=1,true);
});
