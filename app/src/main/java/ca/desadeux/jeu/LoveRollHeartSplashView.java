package ca.desadeux.jeu;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Immediate offline first frame, sharing the exact Web artwork. Decode once,
 * animate only the glass heart and three dots; auth/session logic stays outside. */
final class LoveRollHeartSplashView extends View {
 private static final long BEAT_MS=3000L;
 private static final String CAPTION="Chargement de votre espace…";
 private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
 private final Bitmap heart,decor;
 private final RectF frame=new RectF(),sprite=new RectF();
 private final float density;
 private final Typeface logo=Typeface.create("serif",Typeface.ITALIC);
 private final Typeface text=Typeface.create("sans-serif",Typeface.NORMAL);
 private boolean visibleAnimation=true;
 private RadialGradient glow;

 LoveRollHeartSplashView(Context context){
  super(context);
  density=getResources().getDisplayMetrics().density;
  BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
  heart=BitmapFactory.decodeResource(getResources(),R.drawable.loveroll_glass_heart,options);
  decor=BitmapFactory.decodeResource(getResources(),R.drawable.loveroll_splash_satin,options);
  setContentDescription(CAPTION);
  setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
 }
 private void cover(Canvas canvas,Bitmap image,float left,float top,float width,float height){
  float scale=Math.max(width/image.getWidth(),height/image.getHeight());
  float bw=image.getWidth()*scale,bh=image.getHeight()*scale;
  canvas.save();canvas.clipRect(left,top,left+width,top+height);
  frame.set(left+(width-bw)/2f,top+height-bh,left+(width+bw)/2f,top+height);
  brush.setShader(null);brush.setAlpha(255);canvas.drawBitmap(image,null,frame,brush);canvas.restore();
 }
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
  super.onSizeChanged(w,h,oldw,oldh);
  glow=new RadialGradient(w*.5f,h*.43f,Math.min(w*.4f,h*.24f),
   new int[]{0x4d7b1d35,0x007b1d35},null,Shader.TileMode.CLAMP);
 }
 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
  canvas.drawColor(0xff10070c);
  boolean landscape=w>=h;
  cover(canvas,decor,0,landscape?h*.46f:0,w,landscape?h*.54f:h);
  brush.setAlpha(255);brush.setShader(glow);canvas.drawRect(0,0,w,h,brush);brush.setShader(null);
  boolean animate=visibleAnimation&&ValueAnimator.areAnimatorsEnabled();
  long now=SystemClock.uptimeMillis();
  float size=Math.min(w*.44f,Math.min(h*.23f,236*density));
  float font=Math.max(42*density,Math.min(w*.108f,62*density));
  if(h/density<=450){size=Math.min(h*.32f,145*density);font=34*density;}
  float total=size+12*density+font*1.15f+23*density+8*density+13*density+24*density;
  float top=h*(landscape?.47f:.48f)-total/2f;
  float beat=animate?1f+.018f*(1f-(float)Math.cos((now%BEAT_MS)*Math.PI*2/BEAT_MS))/2f:1f;
  float cx=w*.5f,cy=top+size*.5f;
  sprite.set(cx-size*beat/2f,cy-size*beat/2f,cx+size*beat/2f,cy+size*beat/2f);
  brush.setAlpha(255);canvas.drawBitmap(heart,null,sprite,brush);
  float logoTop=top+size+12*density;
  brush.setTypeface(logo);brush.setTextSize(font);brush.setTextAlign(Paint.Align.CENTER);brush.setColor(0xfff5dcd8);
  Paint.FontMetrics fm=brush.getFontMetrics();
  float baseline=logoTop+(font*1.15f-fm.bottom-fm.top)/2f;
  canvas.drawText("LoveRoll",cx,baseline,brush);
  float word=brush.measureText("LoveRoll"),gap=16*density,line=Math.max(28*density,Math.min(w*.1f,58*density));
  brush.setColor(0x99d58ba3);brush.setStrokeWidth(density);
  float rule=logoTop+font*.575f;
  canvas.drawLine(cx-word/2f-gap-line,rule,cx-word/2f-gap,rule,brush);
  canvas.drawLine(cx+word/2f+gap,rule,cx+word/2f+gap+line,rule,brush);
  float dots=logoTop+font*1.15f+27*density;
  for(int i=0;i<3;i++){
   float phase=((now+1800L-i*600L)%1800L)/1800f;
   float light=animate?.28f+.72f*Math.max(0f,1f-Math.abs(phase-.3f)/.3f):(i==1?1f:.28f);
   brush.setColor(0xffe8a0b5);brush.setAlpha(Math.round(light*255));
   canvas.drawCircle(cx+(i-1)*18*density,dots,4*density,brush);
  }
  brush.setAlpha(255);brush.setTypeface(text);brush.setTextSize(Math.max(14*density,Math.min(w*.028f,16*density)));
  brush.setColor(0xffccb3bf);canvas.drawText(CAPTION,cx,dots+34*density,brush);
  if(animate&&isShown())postInvalidateDelayed(33);
 }
 void resumeHeart(){visibleAnimation=true;setAlpha(1f);setVisibility(VISIBLE);invalidate();}
 void pauseHeart(){visibleAnimation=false;}
}
