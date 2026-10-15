package ca.desadeux.jeu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * The immediate Android loading frame matches couple-web/loveroll-splash.css
 * and the SVG heart in couple-web/index.html (same shape and stops).
 *
 * No WebView is needed for its first paint. The actual website has the same
 * heart and waves; ConnectedLoveRollActivity fades this layer only after a
 * WebView visual-state callback confirms the next frame has been drawn.
 */
final class LoveRollHeartSplashView extends View {
 private static final long BEAT_MS=1250L;
 private static final long WAVE_MS=2600L;
 private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Path heart=heartPath();
 private final Path reflectionLeft=new Path();
 private final Path reflectionRight=new Path();
 private final float density;
 private boolean visibleAnimation=true;

 LoveRollHeartSplashView(Context context){
  super(context);
  density=getResources().getDisplayMetrics().density;
  setContentDescription("Chargement LoveRoll");
  setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  reflectionLeft.moveTo(34,68);
  reflectionLeft.cubicTo(34,47,47,35,65,35);
  reflectionLeft.cubicTo(78,35,85,40,89,50);
  reflectionRight.moveTo(115,48);
  reflectionRight.cubicTo(125,33,154,33,165,60);
 }

 private static Path heartPath(){
  // EXACT SVG geometry of the web splash: M100 179 C80 161 ... Z.
  Path p=new Path();
  p.moveTo(100,179);
  p.cubicTo(80,161,12,116,12,70);
  p.cubicTo(12,39,33,15,65,15);
  p.cubicTo(82,15,94,23,100,38);
  p.cubicTo(106,23,118,15,135,15);
  p.cubicTo(167,15,188,39,188,70);
  p.cubicTo(188,116,120,161,100,179);
  p.close();
  return p;
 }

 private static float cubicSmooth(float start,float end,float x){
  if(x<=start)return 0;
  if(x>=end)return 1;
  float t=(x-start)/(end-start);
  return t*t*(3f-2f*t);
 }
 private static float blend(float start,float end,float x,float from,float to){
  return from+(to-from)*cubicSmooth(start,end,x);
 }
 private static float heartbeat(float phase){
  if(phase<.09f)return blend(0,.09f,phase,1f,1.11f);
  if(phase<.18f)return blend(.09f,.18f,phase,1.11f,.985f);
  if(phase<.29f)return blend(.18f,.29f,phase,.985f,1.17f);
  if(phase<.44f)return blend(.29f,.44f,phase,1.17f,1f);
  return 1f;
 }
 private static int alpha(int color,float amount){
  int a=Math.max(0,Math.min(255,Math.round(amount*255f)));
  return (color&0x00ffffff)|(a<<24);
 }
 private void reset(){
  brush.reset();
  brush.setAntiAlias(true);
  brush.setFilterBitmap(true);
 }

 private void ovalGradient(Canvas canvas,float cx,float cy,float rx,float ry,
                           int[] colors,float[] stops){
  if(rx<=0||ry<=0)return;
  canvas.save();
  canvas.translate(cx,cy);
  canvas.scale(1f,ry/rx);
  reset();
  brush.setShader(new RadialGradient(0,0,rx,colors,stops,Shader.TileMode.CLAMP));
  canvas.drawCircle(0,0,rx,brush);
  canvas.restore();
  brush.setShader(null);
 }

 private void background(Canvas canvas,float w,float h){
  reset();
  brush.setShader(new LinearGradient(0,0,w,h,
    new int[]{0xff10030e,0xff220619,0xff15030f,0xff090009},
    new float[]{0,.33f,.63f,1},Shader.TileMode.CLAMP));
  canvas.drawRect(0,0,w,h,brush);
  brush.setShader(null);

  // The same three soft wine-colored lighting zones as web CSS.
  ovalGradient(canvas,w*.54f,h*.38f,w*.50f,h*.40f,
    new int[]{0x2b67062f,0x1067062f,0x0067062f},new float[]{0,.35f,1});
  ovalGradient(canvas,w*.48f,h*.76f,w*.70f,h*.70f,
    new int[]{0x2b5f052a,0x115f052a,0x005f052a},new float[]{0,.35f,1});
  ovalGradient(canvas,w*.5f,h*.5f,w*.33f,h*.33f,
    new int[]{0x2bf41d6e,0x16f41d6e,0x00f41d6e},new float[]{0,.38f,1});
 }

 private void outlineHeart(Canvas canvas,float left,float top,float size,
                          float rotation,float scale,float opacity){
  canvas.save();
  canvas.translate(left+size*.5f,top+size*.4875f);
  canvas.rotate(rotation);
  canvas.scale(scale,scale);
  canvas.translate(-size*.5f,-size*.4875f);
  canvas.scale(size/200f,size/200f);
  reset();
  brush.setStyle(Paint.Style.STROKE);
  brush.setColor(alpha(0xffc31862,opacity));
  brush.setStrokeWidth(2.2f);
  canvas.drawPath(heart,brush);
  canvas.restore();
 }
 private void backdropHearts(Canvas canvas,float w,float h){
  float size=Math.min(.75f*w,650f*density);
  outlineHeart(canvas,w+.35f*w-size,h*.01f,size,-13f,1.22f,.19f);
  outlineHeart(canvas,-.29f*w,h*1.15f-size*.975f,size,13f,1.60f,.14f);
 }

 private void rings(Canvas canvas,float cx,float cy,float scene,long now){
  for(int i=0;i<4;i++){
   // Web rings: 42% of scene; each is delayed by 650 ms in 2.6 s.
   float phase=((now+(WAVE_MS-(i*650L)%WAVE_MS))%WAVE_MS)/(float)WAVE_MS;
   float radius=scene*.21f*(.82f+1.88f*phase);
   float opacity=phase<.08f?.58f*phase/.08f:.58f*(1-phase)/.92f;
   reset();
   brush.setStyle(Paint.Style.STROKE);
   brush.setColor(alpha(0xfffd418e,Math.max(0,opacity*.58f)));
   brush.setStrokeWidth(Math.max(1.2f,1.5f*density));
   canvas.drawCircle(cx,cy,radius,brush);
  }
 }

 private void drawHeart(Canvas canvas,float cx,float cy,float scene,float beat){
  float sprite=scene*.35f*beat;
  // Same CSS scene glow, followed by the same SVG color stops and highlights.
  ovalGradient(canvas,cx,cy,sprite*1.40f,sprite*1.40f,
    new int[]{0x9dff54a2,0x5bfd0d68,0x15fd0d68,0x00fd0d68},
    new float[]{0,.27f,.52f,1});
  ovalGradient(canvas,cx,cy,sprite*.80f,sprite*.80f,
    new int[]{0x69ff81bc,0x3bef1b79,0x00ef1b79},
    new float[]{0,.52f,1});

  canvas.save();
  canvas.translate(cx-sprite/2f,cy-sprite*.975f/2f);
  canvas.scale(sprite/200f,sprite/200f);

  // Render the brighter outer rim and soft magenta halo before the SVG fill.
  reset();
  brush.setColor(0x24ff3f94);
  brush.setStyle(Paint.Style.STROKE);
  brush.setStrokeWidth(24f);
  canvas.drawPath(heart,brush);
  brush.setColor(0x38ff489e);
  brush.setStrokeWidth(10f);
  canvas.drawPath(heart,brush);

  reset();
  brush.setStyle(Paint.Style.FILL);
  brush.setShader(new LinearGradient(28,15,172,179,
    new int[]{0xffffcced,0xffff82bb,0xffff3f94,0xffe81b79,0xffb7004d},
    new float[]{0,.18f,.5f,.8f,1},Shader.TileMode.CLAMP));
  canvas.drawPath(heart,brush);

  // Web SVG glossy fill overlay (radialGradient lr-heart-gloss).
  brush.setShader(new RadialGradient(68,37,162,
    new int[]{0xc7fff2fb,0x48ff9ccc,0x00c40d5b},
    new float[]{0,.25f,1},Shader.TileMode.CLAMP));
  canvas.drawPath(heart,brush);
  brush.setShader(null);

  brush.setStyle(Paint.Style.STROKE);
  brush.setStrokeWidth(3.2f);
  brush.setShader(new LinearGradient(20,0,165,195,
    new int[]{0xfffff6fc,0xffff9dcd,0xffffecfa},
    new float[]{0,.39f,1},Shader.TileMode.CLAMP));
  canvas.drawPath(heart,brush);
  brush.setShader(null);

  // Both reflected white arcs are copied from the same web SVG paths.
  brush.setStrokeCap(Paint.Cap.ROUND);
  brush.setStrokeWidth(9f);
  brush.setColor(0x6bfff7fd);
  canvas.drawPath(reflectionLeft,brush);
  brush.setStrokeWidth(8f);
  brush.setColor(0x5afff4fa);
  canvas.drawPath(reflectionRight,brush);
  canvas.restore();
 }

 private void particles(Canvas canvas,float cx,float cy,float scene,long now){
  float[] x={.20f,.87f,.29f,.76f,.54f,.52f};
  float[] y={.28f,.37f,.78f,.72f,.13f,.90f};
  float[] delays={.3f,1.4f,2.2f,.8f,1.8f,2.7f};
  for(int i=0;i<x.length;i++){
   float t=((now%3200L)/1000f+delays[i])*6.283185f/3.2f;
   float flicker=.45f+.25f*(float)Math.sin(t);
   float px=cx+(x[i]-.5f)*scene,py=cy+(y[i]-.5f)*scene-6*density*(float)Math.sin(t);
   ovalGradient(canvas,px,py,6*density,6*density,
     new int[]{alpha(0xffff80b9,flicker),alpha(0xfff12c7f,flicker*.25f),0x00ff80b9},
     new float[]{0,.25f,1});
   reset();brush.setColor(alpha(0xffff80b9,flicker));
   canvas.drawCircle(px,py,1.5f*density,brush);
  }
 }

 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float w=getWidth(),h=getHeight();
  if(w<=0||h<=0)return;
  long now=SystemClock.uptimeMillis();
  float cx=w*.5f,cy=h*.5f;
  float scene=Math.max(235f*density,Math.min(w*.94f,430f*density));
  background(canvas,w,h);
  backdropHearts(canvas,w,h);
  rings(canvas,cx,cy,scene,now);
  float beat=heartbeat((now%BEAT_MS)/(float)BEAT_MS);
  drawHeart(canvas,cx,cy,scene,beat);
  particles(canvas,cx,cy,scene,now);
  // Stops drawing immediately once native overlay fades away.
  if(visibleAnimation&&isShown())postInvalidateDelayed(40);
 }
 void resumeHeart(){visibleAnimation=true;setAlpha(1f);setVisibility(VISIBLE);invalidate();}
 void pauseHeart(){visibleAnimation=false;}
}
