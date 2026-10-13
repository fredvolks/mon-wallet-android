package ca.desadeux.jeu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Context;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import ca.desadeux.jeu.GameLogic.Category;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public final class MainActivity extends Activity {
 static final int BG=0xFF110B19,SURFACE=0xFF261527,CARD2=0xFF35182F;
 static final int PINK=0xFFFF4F9B,PALE=0xFFFFDDED,WHITE=0xFFFFF5FA,MUTED=0xFFBBA7B8;
 static final int GREEN=0xFF6BE0A7;
 SharedPreferences prefs;
 LinearLayout root,page,nav;
 ScrollView scroll;
 Dice3DView dice;
 PremiumDice3DView gpuDice;
 PremiumD20View gpuD20;
 String tab="home",player="Émilie";
 int adventSelected=1;
 Category selected=Category.COQUIN;
 boolean randomMode=false;
 boolean rolling=false;
 int width=0;
 int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);
  WindowCompat.setDecorFitsSystemWindows(getWindow(),false);
  getWindow().setStatusBarColor(Color.TRANSPARENT);
  getWindow().setNavigationBarColor(Color.TRANSPARENT);
  prefs=getSharedPreferences("des_a_deux_game",MODE_PRIVATE);
  if(!GameLogic.monday().equals(prefs.getString("week",""))){
   prefs.edit().putString("week",GameLogic.monday()).putInt("goal",-1).putInt("done",0)
    .putBoolean("active",false).putBoolean("pending",false)
    .putInt("fred_target",-1).putInt("emilie_target",-1)
     .putInt("fred_completed",0).putInt("emilie_completed",0)
    .putLong("fred_seen",0L).putLong("emilie_seen",0L).apply();
  }
  // One-time v0.2.1 migration: preserve weekly roll but replace the old single D6 session.
  if(prefs.getInt("duo_setup_version",0)!=1){
   prefs.edit().putInt("duo_setup_version",1)
    .putBoolean("active",false).putBoolean("pending",false)
    .putInt("fred_target",-1).putInt("emilie_target",-1)
    .putInt("fred_completed",0).putInt("emilie_completed",0).apply();
  }
  if(prefs.getBoolean("pending",false)){
   String previous=prefs.getString("card_player","Émilie");
   try{
    Category category=Category.valueOf(prefs.getString("actual","COQUIN"));
    int face=prefs.getInt("face",1);
    String key=previous.equals("Fred")?"fred_seen":"emilie_seen";
    prefs.edit().putLong(key,ChallengePicker.mark(prefs.getLong(key,0L),category,face)).apply();
   }catch(Exception ignored){}
  }
  LocalDate localToday=LocalDate.now(ZoneId.of("America/Montreal"));
  int defaultDay=localToday.getMonthValue()==12?Math.min(25,localToday.getDayOfMonth()):1;
  adventSelected=Math.max(1,Math.min(25,prefs.getInt("advent_selected",defaultDay)));
  player=prefs.getString("player","Émilie");
  if(!player.equals("Fred")&&!player.equals("Émilie")){
   player="Émilie";prefs.edit().putString("player",player).apply();
  }
  try{selected=Category.valueOf(prefs.getString("selected","COQUIN"));}catch(Exception ignored){}
  randomMode=prefs.getBoolean("random_mode",false);
  draw();
  if(prefs.getBoolean("auto_update",true))UpdateManager.check(this,true);
 }
 GradientDrawable shape(int top,int bottom,int radius,int stroke){
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
  g.setCornerRadius(dp(radius));if(stroke!=0)g.setStroke(dp(1),stroke);
  return g;
 }
 void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
 void addText(LinearLayout target,String value,int size,int color,boolean strong,int bottom){
  TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
  if(strong)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
  t.setLineSpacing(dp(1),1.0f);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.bottomMargin=dp(bottom);target.addView(t,lp);
 }
 TextView text(String value,int size,int color,boolean strong){
  TextView tv=new TextView(this);tv.setText(value);tv.setTextSize(size);tv.setTextColor(color);
  if(strong)tv.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  return tv;
 }
 void gap(LinearLayout target,int amount){
  View v=new View(this);target.addView(v,new LinearLayout.LayoutParams(1,dp(amount)));
 }
 LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 LinearLayout card(String title,String subtitle){
  LinearLayout c=vertical();c.setBackground(shape(SURFACE,CARD2,18,0x4CFF6FA4));pad(c,14,11);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.bottomMargin=dp(9);page.addView(c,lp);
  if(title!=null)addText(c,title,16,PALE,true,subtitle==null?0:3);
  if(subtitle!=null)addText(c,subtitle,12,MUTED,false,0);
  return c;
 }
 void heading(String big,String subtitle){
  addText(page,big,24,WHITE,true,2);
  if(subtitle!=null)addText(page,subtitle,13,MUTED,false,9);
 }
 void section(String title){gap(page,5);addText(page,title,16,PINK,true,7);}
 void addButton(LinearLayout dest,String title,boolean highlighted,Runnable action){
  Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);
  b.setTextColor(highlighted?WHITE:PALE);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  b.setBackground(shape(highlighted?0xFFFF4A9A:0xFF47243D,highlighted?0xFFC82870:0xFF2F1A31,16,highlighted?0x99FFB6D8:0x66F26B9C));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46));lp.topMargin=dp(7);
  dest.addView(b,lp);b.setOnClickListener(v->{if(!rolling)action.run();});
 }
 void chip(LinearLayout target,String title,boolean active,Runnable action,int weight){
  TextView v=text(title,13,active?WHITE:PALE,true);v.setGravity(Gravity.CENTER);
  v.setPadding(dp(5),dp(7),dp(5),dp(7));
  v.setBackground(shape(active?0xFFD02D77:0xFF3B263C,active?0xFF822353:0xFF241628,13,active?PINK:0x554B3A4C));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),weight);
  lp.rightMargin=dp(6);target.addView(v,lp);v.setOnClickListener(q->{if(!rolling)action.run();});
 }
 void showDice(int sides,int result){
  FrameLayout frame=new FrameLayout(this);
  frame.setBackground(shape(0xFF220D24,0xFF180E22,26,0x335A3558));
  float screenDp=getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density;
  int diceDp=Math.max(164,Math.min(213,Math.round(screenDp*.235f)));
  LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(diceDp));
  fl.bottomMargin=dp(8);
  page.addView(frame,fl);
  dice=null;gpuDice=null;gpuD20=null;
  if(sides==20&&PremiumDice3DView.supported(this)){
   gpuD20=new PremiumD20View(this);
   gpuD20.setResult(result);
   frame.addView(gpuD20,new FrameLayout.LayoutParams(-1,-1));
  }else if(sides==6&&PremiumDice3DView.supported(this)){
   gpuDice=new PremiumDice3DView(this);
   gpuDice.setResult(result);
   frame.addView(gpuDice,new FrameLayout.LayoutParams(-1,-1));
  }else{
   // Fallback only for devices without OpenGL ES 2.0, or the existing D20.
   dice=new Dice3DView(this);dice.setSides(sides);dice.setResult(result);
   frame.addView(dice,new FrameLayout.LayoutParams(-1,-1));
  }
  TextView caption=text("GLISSE POUR TOURNER LE DÉ",10,0xFFFFC4DF,true);
  caption.setGravity(Gravity.CENTER);
  caption.setBackgroundColor(0x5516071A);
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM);
  frame.addView(caption,cp);
  caption.setOnClickListener(v->{
   Toast.makeText(this,"Brasse le dé avec le bouton pour obtenir un résultat officiel.",Toast.LENGTH_SHORT).show();
  });
 }
 void roll(int value,Runnable end){
  if(rolling||(dice==null&&gpuDice==null&&gpuD20==null))return;
  rolling=true;
  vibrate(36);
  Runnable finished=()->{
   rolling=false;vibrate(85);end.run();
  };
  if(gpuD20!=null)gpuD20.roll(value,finished);
  else if(gpuDice!=null)gpuDice.roll(value,finished);
  else dice.roll(value,finished);
 }
 void showResultDialog(String symbol,String title,String description,String buttonTitle){
  LinearLayout popup=vertical();
  popup.setPadding(dp(24),dp(23),dp(24),dp(20));
  popup.setBackground(shape(0xFF39182F,0xFF190F25,26,0x99FF83BD));
  TextView badge=text(symbol,32,PINK,true);
  badge.setGravity(Gravity.CENTER);
  popup.addView(badge,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView result=text(title,25,WHITE,true);
  result.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,-2);t.bottomMargin=dp(12);
  popup.addView(result,t);
  TextView details=text(description,14,PALE,false);
  details.setGravity(Gravity.CENTER);
  details.setLineSpacing(dp(3),1f);
  popup.addView(details,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog d=new AlertDialog.Builder(this).setView(popup).create();
  Button confirm=new Button(this);
  confirm.setAllCaps(false);
  confirm.setText(buttonTitle);
  confirm.setTextSize(15);
  confirm.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  confirm.setTextColor(WHITE);
  confirm.setBackground(shape(0xFFFF4E9C,0xFFBE276D,16,0x99FFC3DC));
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(49));
  bp.topMargin=dp(20);popup.addView(confirm,bp);
  confirm.setOnClickListener(v->d.dismiss());
  d.setOnShowListener(ignore->{
   if(d.getWindow()!=null){
    d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    d.getWindow().setDimAmount(.72f);
    d.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.89f),
      ViewGroup.LayoutParams.WRAP_CONTENT);
   }
   popup.setAlpha(0f);popup.setScaleX(.93f);popup.setScaleY(.93f);
   popup.animate().alpha(1).scaleX(1).scaleY(1).setDuration(260).start();
  });
  d.show();
 }
 void vibrate(long ms){
  try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
   if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
  }catch(Exception ignored){}
 }
 void draw(){
  if(gpuDice!=null){gpuDice.onPause();gpuDice=null;}
  if(gpuD20!=null){gpuD20.onPause();gpuD20=null;}
  dice=null;
  root=vertical();root.setBackgroundColor(BG);
  scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  page=vertical();page.setPadding(dp(17),dp(12),dp(17),dp(9));
  scroll.addView(page);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  nav=horizontal();nav.setBackground(shape(0xFF221326,0xFF1A1021,0,0));pad(nav,9,6);
  root.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));
  ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
   int top=insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
   int bottom=insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
   root.setPadding(0,top,0,0);
   nav.setPadding(dp(9),dp(4),dp(9),dp(5)+bottom);
   ViewGroup.LayoutParams params=nav.getLayoutParams();
   int desired=dp(58)+bottom;
   if(params.height!=desired){params.height=desired;nav.setLayoutParams(params);}
   return insets;
  });
  setContentView(root);
  ViewCompat.requestApplyInsets(root);
  switch(tab){
   case "game":drawGame();break;
   case "library":drawLibrary();break;
   case "advent":drawAdvent();break;
   case "special":drawSpecial();break;
   case "settings":drawSettings();break;
   default:drawHome();break;
  }
  drawNav();
 }
 void drawNav(){
  for(String[] item:new String[][]{{"home","⌂","Accueil"},{"game","⚄","Soirée"},{"special","✦","Défi spécial"},{"advent","🎁","Avent"},{"settings","⚙","Réglages"}}){
   LinearLayout col=vertical();col.setGravity(Gravity.CENTER);
   boolean active=tab.equals(item[0])||(item[0].equals("settings")&&tab.equals("library"));
   TextView icon=text(item[1],item[0].equals("advent")?22:24,active?PINK:MUTED,false);icon.setGravity(Gravity.CENTER);col.addView(icon);
   TextView name=text(item[2],item[0].equals("special")?10:11,active?PALE:MUTED,active);name.setGravity(Gravity.CENTER);col.addView(name);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
   nav.addView(col,lp);col.setOnClickListener(v->{if(rolling)return;tab=item[0];draw();});
  }
 }
 void drawHome(){
  heading("♡  DÉS À DEUX","Un jeu intime, juste pour vous deux");
  addText(page,"LE RITUEL DE LA SEMAINE",12,PINK,true,6);
  int goal=prefs.getInt("goal",-1),done=prefs.getInt("done",0);
  showDice(6,goal>0?goal:1);
  if(goal>0){
   LinearLayout official=card("✦  RÉSULTAT OFFICIEL    "+goal+" SOIRÉE"+(goal>1?"S":""),null);
   addText(official,"Enregistré jusqu'à lundi  ·  Face "+goal,12,MUTED,false,0);
  }
  if(goal<0){
   LinearLayout intro=card("🎲  Le dé hebdomadaire","Choisissez le nombre de soirées de la semaine par un tirage pondéré.");
   LinearLayout chips=horizontal();intro.addView(chips);
   for(String p:new String[]{"1×\n50%","2×\n25%","3×\n12%","4×\n8%","5×\n4%","6×\n1%"}){
    TextView v=text(p,11,PALE,true);v.setGravity(Gravity.CENTER);
    v.setBackground(shape(0xFF49223B,0xFF2F1730,9,0x337A4A69));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(41),1);lp.rightMargin=dp(3);
    chips.addView(v,lp);
   }
   addButton(intro,"🎲  BRASSER LE DÉ HEBDOMADAIRE",true,()->{
    if(prefs.getInt("goal",-1)>0)return;
    int n=GameLogic.rollWeek();
    if(!prefs.edit().putInt("goal",n).commit()){
     Toast.makeText(this,"Impossible d'enregistrer le tirage. Réessayez.",Toast.LENGTH_LONG).show();
     return;
    }
    roll(n,()->{
     draw();
     showResultDialog("♥",""+n+" SOIRÉE"+(n>1?"S":""),"Votre tirage est enregistré jusqu'à lundi. C'est un jeu, jamais une obligation.","C'EST PARTI !");
    });
   });
  }else{
   LinearLayout c=card("♥  "+goal+" soirée(s) cette semaine","Votre progression commune");
   addText(c,"PROGRESSION  "+done+" / "+goal,13,WHITE,true,4);
   progress(c,goal==0?0:(float)Math.min(done,goal)/goal);
   addButton(c,"COMMENCER UNE SOIRÉE   →",true,()->{tab="game";draw();});
  }
  LinearLayout info=card("♥  Votre rituel   ·   3 types de dés",null);
  addText(info,"SEMAINE  →  soirées     ·     D6 × 2  →  défis reçus",12,PALE,true,2);
  addText(info,"D20  →  Sensuel  ·  Coquin  ·  Sexuel",12,MUTED,false,0);
 }
 void progress(LinearLayout dest,float fraction){
  FrameLayout track=new FrameLayout(this);
  track.setBackground(shape(0xFF56314C,0xFF56314C,10,0));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(9));dest.addView(track,lp);
  View fill=new View(this);fill.setBackground(shape(PINK,0xFFBF3278,10,0));
  track.addView(fill,new FrameLayout.LayoutParams(-1,-1));
  track.post(()->{ViewGroup.LayoutParams p=fill.getLayoutParams();
    p.width=Math.max(0,(int)(track.getWidth()*fraction));fill.setLayoutParams(p);});
 }
 void resetEvening(){
  prefs.edit().putBoolean("active",false).putBoolean("pending",false).putBoolean("accepted",false)
   .putInt("fred_target",-1).putInt("emilie_target",-1)
   .putInt("fred_completed",0).putInt("emilie_completed",0)
   .putLong("fred_seen",0L).putLong("emilie_seen",0L).apply();
 }
 String seenKey(String who){return who.equals("Fred")?"fred_seen":"emilie_seen";}
 long seenMask(String who){return prefs.getLong(seenKey(who),0L);}
 void offerChallenge(String receiver){
  long history=seenMask(receiver);
  ChallengePicker.Pick next=ChallengePicker.draw(selected,randomMode,history);
  if(next==null){
   new AlertDialog.Builder(this)
    .setTitle("60 défis déjà proposés à "+receiver)
    .setMessage("Aucun défi inédit ne reste pour cette personne ce soir. Vous pouvez terminer ou recommencer la soirée.")
    .setPositiveButton("Compris",null).show();
   return;
  }
  prefs.edit().putLong(seenKey(receiver),ChallengePicker.mark(history,next.category,next.face))
   .putBoolean("pending",true).putBoolean("accepted",false)
   .putString("actual",next.category.name()).putString("card_player",receiver)
   .putInt("face",next.face).apply();
  roll(next.face,()->{
   draw();
   if(next.category!=selected)
    Toast.makeText(this,"SURPRISE ! Catégorie "+categoryName(next.category),Toast.LENGTH_LONG).show();
  });
 }
 int target(String who){return prefs.getInt(who.equals("Fred")?"fred_target":"emilie_target",-1);}
 int completed(String who){return prefs.getInt(who.equals("Fred")?"fred_completed":"emilie_completed",0);}
 String other(String who){return who.equals("Fred")?"Émilie":"Fred";}
 void setReceiver(String who){
  player=who;prefs.edit().putString("player",who).apply();draw();
 }
 void duoProgress(){
  int f=target("Fred"),e=target("Émilie");
  LinearLayout c=card("♥  Qui donne, qui reçoit ?",null);
  if(e>0){
   addText(c,"♡  Émilie reçoit "+completed("Émilie")+"/"+e+"     ·     Fred donne "+e,14,WHITE,true,4);
   if(prefs.getBoolean("active",false))progress(c,(float)completed("Émilie")/e);
  }else addText(c,"♡  Émilie reçoit : à brasser   ·   Fred donne",13,MUTED,false,4);
  gap(c,3);
  if(f>0){
   addText(c,"♡  Fred reçoit "+completed("Fred")+"/"+f+"     ·     Émilie donne "+f,14,WHITE,true,4);
   if(prefs.getBoolean("active",false))progress(c,(float)completed("Fred")/f);
  }else addText(c,"♡  Fred reçoit : à brasser   ·   Émilie donne",13,MUTED,false,0);
 }
 void showGameD20(int value,boolean pending,Category actual,String receiver,int done,int total){
  LinearLayout hero=horizontal();
  hero.setBackground(shape(0xFF231126,0xFF32182E,19,0x66FC6AAB));
  hero.setPadding(dp(6),dp(6),dp(10),dp(6));
  LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(164));
  hp.bottomMargin=dp(8);
  page.addView(hero,hp);
  FrameLayout die=new FrameLayout(this);
  LinearLayout.LayoutParams dp20=new LinearLayout.LayoutParams(0,-1,.47f);
  hero.addView(die,dp20);
  gpuDice=null;gpuD20=null;dice=null;
  if(PremiumDice3DView.supported(this)){
   gpuD20=new PremiumD20View(this);
   gpuD20.setResult(value);
   die.addView(gpuD20,new FrameLayout.LayoutParams(-1,-1));
  }else{
   dice=new Dice3DView(this);dice.setSides(20);dice.setResult(value);
   die.addView(dice,new FrameLayout.LayoutParams(-1,-1));
  }
  LinearLayout detail=vertical();
  detail.setGravity(Gravity.CENTER_VERTICAL);
  detail.setPadding(dp(8),0,0,0);
  hero.addView(detail,new LinearLayout.LayoutParams(0,-1,.53f));
  addText(detail,pending?categoryName(actual).toUpperCase(java.util.Locale.CANADA_FRENCH)+" · FACE "+value:"D20 · DÉFIS",13,PINK,true,7);
  if(pending)addText(detail,"Pour : "+receiver+"\nDe : "+other(receiver),14,WHITE,true,7);
  else addText(detail,"Brassez pour découvrir le prochain défi.",13,PALE,false,7);
  addText(detail,done+" / "+total+" réalisés",14,WHITE,true,5);
  progress(detail,(float)done/Math.max(1,total));
 }
 void compactCoupleProgress(int fred,int emilie){
  LinearLayout box=vertical();box.setPadding(dp(12),dp(9),dp(12),dp(10));
  box.setBackground(shape(SURFACE,CARD2,16,0x55FF78B1));
  LinearLayout.LayoutParams outside=new LinearLayout.LayoutParams(-1,-2);
  outside.bottomMargin=dp(8);page.addView(box,outside);
  LinearLayout row=horizontal();box.addView(row);
  for(String receiver:new String[]{"Émilie","Fred"}){
   LinearLayout entry=vertical();
   LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);
   if(receiver.equals("Émilie"))cell.rightMargin=dp(9);
   row.addView(entry,cell);
   int goal=receiver.equals("Émilie")?emilie:fred;
   int done=completed(receiver);
   addText(entry,receiver+" reçoit "+done+"/"+goal,13,WHITE,true,2);
   addText(entry,other(receiver)+" donne "+goal,11,MUTED,false,5);
   progress(entry,(float)done/Math.max(1,goal));
  }
 }
 void pairButton(LinearLayout target,String caption,boolean primary,Runnable callback){
  Button b=new Button(this);
  b.setAllCaps(false);b.setText(caption);b.setTextSize(12);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  b.setTextColor(primary?WHITE:PALE);
  b.setBackground(shape(primary?0xFFFF4998:0xFF3D2639,
    primary?0xFFC62D76:0xFF241729,14,primary?0x99FFD1E6:0x77F270AD));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(49),1);
  lp.rightMargin=dp(5);target.addView(b,lp);
  b.setOnClickListener(v->{if(!rolling)callback.run();});
 }
 void drawGame(){
  heading("🔥  Votre soirée","Chacun décide de son rythme et reçoit ses propres défis");
  if(prefs.getInt("goal",-1)<0){
   card("D'abord le dé hebdomadaire","Brassez le dé hebdomadaire à l'accueil avant de commencer.");
   addButton(page,"ALLER À L'ACCUEIL",true,()->{tab="home";draw();});
   return;
  }
  int fred=target("Fred"),emilie=target("Émilie");
  boolean active=prefs.getBoolean("active",false);
  if(!active){
   addText(page,"ÉTAPE 2 / 3   ·   DEUX D6 INDIVIDUELS",12,PINK,true,6);
   int last=prefs.getInt("setup_last_face",1);
   showDice(6,last);
   String lastReceiver=prefs.getString("setup_last_receiver","");
   if(!lastReceiver.isEmpty()&&(fred>0||emilie>0))
    addText(page,"🎯  "+lastReceiver+" reçoit "+last+" défi"+(last>1?"s":""),15,PINK,true,6);
   LinearLayout explanation=card("🎲  Un dé chacun",null);
   addText(explanation,"Chacun brasse le nombre de défis qu'il reçoit (1 à 6). L'autre les offre.",13,PALE,false,2);
   addText(explanation,"Ex. Émilie reçoit 4 → Fred donne 4.",12,MUTED,false,0);
   duoProgress();
   if(emilie<0){
    addButton(page,"🎲  ÉMILIE BRASSE : JE REÇOIS…",true,()->{
     int n=GameLogic.d6();
     if(!prefs.edit().putInt("emilie_target",n).putInt("setup_last_face",n)
       .putString("setup_last_receiver","Émilie").commit())return;
     roll(n,()->{
      draw();
      showResultDialog("♥","ÉMILIE REÇOIT "+n,
       "Fred lui offre "+n+" défi"+(n>1?"s":"")+" pendant la soirée.","CONTINUER");
     });
    });
   }
   if(fred<0){
    addButton(page,"🎲  FRED BRASSE : JE REÇOIS…",true,()->{
     int n=GameLogic.d6();
     if(!prefs.edit().putInt("fred_target",n).putInt("setup_last_face",n)
       .putString("setup_last_receiver","Fred").commit())return;
     roll(n,()->{
      draw();
      showResultDialog("♥","FRED REÇOIT "+n,
       "Émilie lui offre "+n+" défi"+(n>1?"s":"")+" pendant la soirée.","CONTINUER");
     });
    });
   }
   if(fred>0&&emilie>0){
    LinearLayout summary=card("♡  Votre programme de soirée",
       (fred+emilie)+" défis au total. À chacun son rythme.");
    addButton(summary,"♥  COMMENCER LA SOIRÉE",true,()->{
     prefs.edit().putBoolean("active",true).putBoolean("pending",false).apply();
     player=completed("Émilie")<emilie?"Émilie":"Fred";
     prefs.edit().putString("player",player).apply();draw();
    });
   }
   addButton(page,"↻  RECOMMENCER LES DEUX D6",false,()->confirmResetEvening());
   return;
  }
  int fredDone=completed("Fred"),emilieDone=completed("Émilie");
  int total=fred+emilie,done=fredDone+emilieDone;
  addText(page,"ÉTAPE 3 / 3   ·   D20 DES DÉFIS",12,PINK,true,5);
  boolean pending=prefs.getBoolean("pending",false);
  Category actual=Category.valueOf(prefs.getString("actual","COQUIN"));
  int face=prefs.getInt("face",1);
  String receiver=prefs.getString("card_player","Émilie");
  showGameD20(pending?face:20,pending,actual,receiver,done,total);
  compactCoupleProgress(fred,emilie);
  if(pending){
   String giver=other(receiver);
   String[] result=GameData.get(actual,face);
   LinearLayout resultCard=card("♥ "+categoryName(actual)+" · Face "+face,null);
   addText(resultCard,giver+" OFFRE  →  "+receiver+" REÇOIT",14,PINK,true,6);
   addText(resultCard,result[0],20,WHITE,true,5);
   addText(resultCard,result[1],14,PALE,false,6);
   addText(resultCard,"Exemple : "+result[2].replaceFirst("^Exemple : ",""),12,MUTED,false,7);
   addText(resultCard,"Défis proposés à "+receiver+" : "+
    (60-ChallengePicker.remaining(seenMask(receiver)))+" / 60",11,MUTED,false,7);
   LinearLayout actions=horizontal();resultCard.addView(actions);
   if(!prefs.getBoolean("accepted",false)){
    pairButton(actions,"✓  ACCEPTER",true,()->{
     prefs.edit().putBoolean("accepted",true).apply();draw();
    });
    pairButton(actions,"↻  PASSER · NOUVEAU",false,()->offerChallenge(receiver));
   }else{
    pairButton(actions,"✓  DÉFI TERMINÉ",true,()->{
     String key=receiver.equals("Fred")?"fred_completed":"emilie_completed";
     prefs.edit().putInt(key,completed(receiver)+1)
      .putBoolean("pending",false).putBoolean("accepted",false).apply();draw();
    });
    pairButton(actions,"↻  CHANGER D'AVIS",false,()->offerChallenge(receiver));
   }
  }else if(done<total){
   if(target(player)<0||completed(player)>=target(player)){
    player=emilieDone<emilie?"Émilie":"Fred";
    prefs.edit().putString("player",player).apply();
   }
   LinearLayout selection=card("Qui reçoit le prochain défi ?",null);
   LinearLayout who=horizontal();selection.addView(who);
   if(emilieDone<emilie)
    chip(who,"Émilie",player.equals("Émilie"),()->setReceiver("Émilie"),1);
   if(fredDone<fred)
    chip(who,"Fred",player.equals("Fred"),()->setReceiver("Fred"),1);
   addText(selection,other(player)+" propose à "+player,13,PALE,false,5);
   addText(selection,"CHOIX DE LA CATÉGORIE",11,PINK,true,6);
   LinearLayout choices=horizontal();selection.addView(choices);
   for(Category cat:Category.values()){
    chip(choices,categoryName(cat),!randomMode&&selected==cat,()->{
     selected=cat;randomMode=false;
     prefs.edit().putString("selected",cat.name()).putBoolean("random_mode",false).apply();draw();
    },1);
   }
   chip(choices,"Aléatoire",randomMode,()->{
    randomMode=true;prefs.edit().putBoolean("random_mode",true).apply();draw();
   },1);
   addText(selection,randomMode?
     "Aléatoire : Sexuel 40 % · Coquin 30 % · Sensuel 30 %":
     "Choisie : 70 % · autres catégories : 15 % chacune",12,MUTED,false,3);
   addButton(selection,"🎲  "+other(player).toUpperCase()+" BRASSE LE D20",true,
     ()->offerChallenge(player));
  }else{
   LinearLayout finish=card("♥  Tous les défis sont réalisés",
    "Vous avez complété les défis prévus pour vous deux.");
   addButton(finish,"TERMINER ET COMPTER LA SOIRÉE",true,()->{
    prefs.edit().putInt("done",prefs.getInt("done",0)+1).apply();
    resetEvening();tab="home";draw();
   });
  }
  addButton(page,"Arrêter la soirée sans la compter",false,()->confirmResetEvening());
  addText(page,"Chacun peut refuser, modifier ou interrompre un défi. Passer ne réduit jamais le nombre restant.",
    11,MUTED,false,0);
 }
 void confirmResetEvening(){
  new AlertDialog.Builder(this).setTitle("Recommencer cette soirée ?")
   .setMessage("Les dés et la progression de la soirée seront remis à zéro. Le dé hebdomadaire restera inchangé.")
   .setPositiveButton("Recommencer",(d,w)->{resetEvening();draw();})
   .setNegativeButton("Annuler",null).show();
 }
 String categoryName(Category c){
  switch(c){case COQUIN:return "Coquin";case SEXUEL:return "Sexuel";default:return "Sensuel";}
 }

 String specialPrefix(LocalDate date){return "special_"+SpecialDateData.monthKey(date)+"_";}
 SpecialDateData.Draw specialSaved(LocalDate date){
  String p=specialPrefix(date);
  int face=prefs.getInt(p+"face",-1);
  if(face<1||face>20)return null;
  return new SpecialDateData.Draw(face,prefs.getBoolean(p+"fred",false),
    prefs.getBoolean(p+"optionB",false));
 }
 void showSpecialCatalogue(){
  StringBuilder sb=new StringBuilder("20 idées possibles à Québec :\n\n");
  for(int i=0;i<20;i++)sb.append(i+1).append(".  ")
    .append(SpecialDateData.ACTIVITIES[i][0]).append("\n");
  sb.append("\nLa personne qui invite est tirée à 50/50. Pour une sortie au restaurant, vous brassez un D6 supplémentaire pour choisir resto connu ou nouveau.");
  new AlertDialog.Builder(this).setTitle("✦ Nos 20 sorties").setMessage(sb.toString())
   .setPositiveButton("Compris",null).show();
 }
 void drawSpecial(){
  LocalDate now=LocalDate.now(SpecialDateData.ZONE);
  String prefix=specialPrefix(now);
  SpecialDateData.Draw saved=specialSaved(now);
  heading("✦  Défi spécial","Une vraie sortie en amoureux, une fois par mois");
  addText(page,"RENDEZ-VOUS DE "+SpecialDateData.monthLabel(now).toUpperCase(java.util.Locale.CANADA_FRENCH),
    12,PINK,true,7);
  boolean restaurant=saved!=null&&SpecialDateData.isRestaurant(saved.face);
  int restaurantD6=restaurant?prefs.getInt(prefix+"restaurant_variant_face",0):0;
  if(restaurant){
   addText(page,"🎲 D20 : "+SpecialDateData.title(saved)+" · Face "+saved.face,13,PINK,true,6);
   showDice(6,restaurantD6>0?restaurantD6:1);
  }else{
   showDice(20,saved==null?20:saved.face);
  }
  if(saved==null){
   LinearLayout intro=card("♡  Votre rendez-vous mensuel",
    "20 activités adaptées à votre vie de parents, dans la région de Québec.");
   addText(intro,"🎲 Sortie : 1 chance sur 20 par activité",13,PALE,true,5);
   addText(intro,"♥ Fred invite / Émilie invite : 50 % / 50 %",13,PALE,true,5);
   addText(intro,"✦ Restaurant : second D6 (1–3 connu, 4–6 nouveau)",13,PALE,true,4);
   addText(intro,"Autres sorties : variante A/B à 50 % / 50 %",12,MUTED,false,4);
   addText(intro,"Gardienne à prévoir : environ 2 à 6 heures selon la sortie. Aucune réservation faite automatiquement.",12,MUTED,false,5);
   addButton(intro,"🎲  BRASSER LE D20 DU MOIS",true,()->{
    if(!SpecialDateData.monthKeyNow().equals(SpecialDateData.monthKey(now))){draw();return;}
    if(specialSaved(LocalDate.now(SpecialDateData.ZONE))!=null)return;
    SpecialDateData.Draw pick=SpecialDateData.draw();
    // Commit all draws together before starting the animation. No re-roll on restart.
    boolean stored=prefs.edit().putInt(prefix+"face",pick.face)
      .putBoolean(prefix+"fred",pick.fredInvites)
      .putBoolean(prefix+"optionB",pick.optionB)
      .putInt(prefix+"restaurant_variant_face",0)
      .putString(prefix+"date",now.toString())
      .putBoolean(prefix+"done",false).commit();
    if(!stored){
     Toast.makeText(this,"Tirage non enregistré. Réessaie.",Toast.LENGTH_LONG).show();
     return;
    }
    roll(pick.face,()->{
     draw();
     showResultDialog("✦",SpecialDateData.title(pick),
       SpecialDateData.inviter(pick)+" invite "+SpecialDateData.invited(pick)+
       (SpecialDateData.isRestaurant(pick.face)?
         " !\nBrassez maintenant le D6 pour choisir RESTO CONNU ou NOUVEAU RESTO (50/50).":
         " !\n"+SpecialDateData.label(pick)+" · "+SpecialDateData.option(pick,now)),
       "DÉCOUVRIR NOTRE SORTIE");
    });
   });
  }else{
   LinearLayout result=card("✦  "+SpecialDateData.title(saved)+"  ·  FACE "+saved.face,null);
   addText(result,"♥  QUI INVITE : "+SpecialDateData.inviter(saved).toUpperCase(java.util.Locale.CANADA_FRENCH),
      16,PINK,true,5);
   addText(result,"♡  QUI EST INVITÉ(E) : "+SpecialDateData.invited(saved).toUpperCase(java.util.Locale.CANADA_FRENCH),
      15,WHITE,true,8);
   if(restaurant){
    if(restaurantD6==0){
     addText(result,"🎲 RESTO CONNU OU NOUVEAU RESTO ?",16,PINK,true,7);
     addText(result,"Un second D6 décidera à 50/50 : 1, 2, 3 = connu · 4, 5, 6 = nouveau.",13,PALE,false,7);
     addButton(result,"🎲  BRASSER LE DÉ DU RESTAURANT",true,()->{
      if(!SpecialDateData.monthKeyNow().equals(SpecialDateData.monthKey(now))){draw();return;}
      if(prefs.getInt(prefix+"restaurant_variant_face",0)!=0)return;
      int faceD6=GameLogic.d6();
      boolean isNew=SpecialDateData.restaurantIsNew(faceD6);
      boolean committed=prefs.edit()
       .putInt(prefix+"restaurant_variant_face",faceD6)
       .putBoolean(prefix+"optionB",isNew).commit();
      if(!committed){
       Toast.makeText(this,"Impossible d'enregistrer le résultat.",Toast.LENGTH_LONG).show();
       return;
      }
      roll(faceD6,()->{
       draw();
       showResultDialog("♥",SpecialDateData.restaurantChoiceLabel(faceD6),
        "Dé du restaurant : "+faceD6+"\n"+SpecialDateData.option(
          new SpecialDateData.Draw(saved.face,saved.fredInvites,isNew),now),
        "C'EST NOTÉ !");
      });
     });
    }else{
     addText(result,"🎯  "+SpecialDateData.restaurantChoiceLabel(restaurantD6)+
       " · D6 FACE "+restaurantD6,16,PINK,true,6);
     addText(result,SpecialDateData.option(saved,now),15,WHITE,true,8);
    }
   }else{
    addText(result,SpecialDateData.label(saved)+"  ·  "+SpecialDateData.option(saved,now),
      16,WHITE,true,10);
   }
   addText(result,SpecialDateData.description(saved),13,PALE,false,10);
   addText(result,"📍 "+SpecialDateData.location(saved)+"     ·     Gardienne : "+SpecialDateData.childcare(saved),
      12,MUTED,false,5);
   addText(result,"La personne qui invite propose et organise; le partage des frais reste votre choix.",12,MUTED,false,7);
   if(restaurant&&restaurantD6==0){
    addText(result,"La sortie sera validable une fois le deuxième dé brassé.",12,MUTED,false,5);
   }else if(prefs.getBoolean(prefix+"done",false)){
    addText(result,"✓  Votre rendez-vous du mois est réalisé !",15,GREEN,true,5);
    addButton(result,"↻  MARQUER COMME À ORGANISER",false,()->{
     prefs.edit().putBoolean(prefix+"done",false).apply();draw();
    });
   }else{
    addButton(result,"✓  SORTIE RÉALISÉE",true,()->{
     prefs.edit().putBoolean(prefix+"done",true).apply();draw();
    });
   }
   addText(page,"🔒 Le D20 est verrouillé jusqu'au mois prochain. Même après un redémarrage, ce résultat reste enregistré.",
     12,MUTED,false,10);
  }
  addButton(page,"VOIR LES 20 IDÉES POSSIBLES",false,()->showSpecialCatalogue());
  StringBuilder history=new StringBuilder();
  for(int n=1;n<=12;n++){
   LocalDate previous=now.minusMonths(n);
   SpecialDateData.Draw d=specialSaved(previous);
   if(d==null)continue;
   history.append("•  ").append(SpecialDateData.monthLabel(previous))
     .append(" : ").append(SpecialDateData.title(d))
     .append(" — ").append(SpecialDateData.inviter(d))
     .append(prefs.getBoolean(specialPrefix(previous)+"done",false)?" ✓":"")
     .append("\n");
  }
  if(history.length()>0){
   LinearLayout old=card("♡  Vos rendez-vous précédents",null);
   addText(old,history.toString().trim(),12,PALE,false,0);
  }
 }
 void drawLibrary(){
  heading("♡  Les défis","Réglages  ›  Bibliothèque des 60 défis");
  addButton(page,"←  RETOUR AUX RÉGLAGES",false,()->{tab="settings";draw();});
  section("Explorer les catégories");
  LinearLayout row=horizontal();page.addView(row);
  for(Category c:Category.values())chip(row,categoryName(c),selected==c,()->{
   selected=c;prefs.edit().putString("selected",c.name()).apply();draw();
  },1);
  gap(page,16);
  addText(page,categoryName(selected).toUpperCase()+"  ·  20 FACES",12,PINK,true,13);
  for(int i=1;i<=20;i++){
   String[] item=GameData.get(selected,i);
   LinearLayout c=card(String.format(java.util.Locale.CANADA_FRENCH,"%02d  ♥  %s",i,item[0]),"À faire : "+item[1]);
   addText(c,"Exemple : "+item[2].replaceFirst("^Exemple : ",""),13,MUTED,false,0);
  }
 }

 LocalDate adventToday(){return LocalDate.now(ZoneId.of("America/Montreal"));}
 int adventYear(){return adventToday().getYear();}
 String adventKey(int day,String kind){return "advent_"+adventYear()+"_"+day+"_"+kind;}
 boolean adventDone(int day){
  return AdventData.unlocked(day,adventToday())&&prefs.getBoolean(adventKey(day,"done"),false);
 }
 boolean adventSwap(int day){return prefs.getBoolean(adventKey(day,"swapped"),false);}
 int adventProgress(){
  int done=0;
  for(int day=1;day<=AdventData.unlockedCount(adventToday());day++)if(adventDone(day))done++;
  return done;
 }
 void drawAdvent(){
  LocalDate today=adventToday();
  int unlocked=AdventData.unlockedCount(today);
  int year=today.getYear();
  heading("🎁  Calendrier de l'Avent","25 surprises qui deviennent de plus en plus coquines");
  addText(page,"DÉCEMBRE "+year+"  ·  UN JOUR, UNE CASE À DÉCOUVRIR",12,PINK,true,8);
  LinearLayout intro=card("♥  "+adventProgress()+" / 25 cases réalisées",
   "Un seul calendrier pour Fred et Émilie. Chaque surprise s'ouvre uniquement à partir de sa date.");
  progress(intro,adventProgress()/25f);
  if(unlocked==0){
   LinearLayout waiting=card("🔒  Calendrier encore fermé",
    "Rendez-vous le 1er décembre "+year+" pour dévoiler la première surprise.");
   addText(waiting,"Les 25 activités resteront secrètes jusque-là. Impossible d'ouvrir une case en avance.",14,PALE,false,0);
  }else if(unlocked<25){
   card("🎁  Cases 1 à "+unlocked+" disponibles",
    "La prochaine case se débloque le "+(unlocked+1)+" décembre. Les suivantes restent secrètes.");
  }else{
   card("🎁  Toutes les cases sont débloquées",
    "Vous pouvez revoir les surprises des jours précédents et cocher celles réalisées.");
  }
  for(int row=0;row<5;row++){
   LinearLayout week=horizontal();page.addView(week);
   for(int col=0;col<5;col++){
    final int day=row*5+col+1;
    final boolean available=AdventData.unlocked(day,today);
    String label=available?(adventDone(day)?"✓ "+day:"🎁 "+day):"🔒 "+day;
    chip(week,label,available&&adventSelected==day,()->{
     if(!AdventData.unlocked(day,adventToday())){
      Toast.makeText(this,"La case "+day+" sera dévoilée le "+day+" décembre.",Toast.LENGTH_LONG).show();
      return;
     }
     adventSelected=day;
     prefs.edit().putInt("advent_selected",day).apply();
     draw();
    },1);
   }
   gap(page,7);
  }
  if(unlocked==0){
   gap(page,13);
   card("♥  Le suspense fait partie du jeu",
    "Le contenu des cases et le défi du samedi spécial seront révélés seulement le jour venu.");
   return;
  }
  if(adventSelected<1||adventSelected>unlocked){
   adventSelected=unlocked;
   prefs.edit().putInt("advent_selected",adventSelected).apply();
  }
  int day=adventSelected;
  String[] item=AdventData.revealedActivity(day,today);
  if(item==null)return; // Never show unopened day contents, even if an old selection is stored.
  boolean together=AdventData.isTogether(day,year);
  String giver=AdventData.giver(day,adventSwap(day),year);
  String receiver=AdventData.recipient(day,adventSwap(day),year);
  gap(page,13);
  LinearLayout chosen=card("🎁  CASE "+day+" / 25  ·  "+item[0],null);
  if(together){
   addText(chosen,"♥  ACTIVITÉ ENSEMBLE : FRED + ÉMILIE",16,PINK,true,9);
  }else{
   addText(chosen,"🎁  QUI OFFRE : "+giver.toUpperCase(java.util.Locale.CANADA_FRENCH),16,PINK,true,7);
   addText(chosen,"♡  QUI REÇOIT : "+receiver.toUpperCase(java.util.Locale.CANADA_FRENCH),16,WHITE,true,9);
  }
  addText(chosen,"À FAIRE",12,PINK,true,6);
  addText(chosen,AdventData.instruction(day,adventSwap(day),year),17,WHITE,false,12);
  addText(chosen,"EXEMPLE",12,PINK,true,5);
  addText(chosen,AdventData.example(day,adventSwap(day),year).replaceFirst("^Exemple : ",""),15,MUTED,false,12);
  if(!together){
   addButton(chosen,"↔  INVERSER FRED ET ÉMILIE",false,()->{
    if(!AdventData.unlocked(day,adventToday()))return;
    prefs.edit().putBoolean(adventKey(day,"swapped"),!adventSwap(day)).apply();
    draw();
   });
  }
  if(!adventDone(day)){
   addButton(chosen,"✓  MARQUER CETTE CASE TERMINÉE",true,()->{
    if(!AdventData.unlocked(day,adventToday()))return;
    prefs.edit().putBoolean(adventKey(day,"done"),true).apply();
    draw();
   });
  }else{
   addText(chosen,"✓ Cette activité a été réalisée.",15,GREEN,true,6);
   addButton(chosen,"↻  REMETTRE À FAIRE",false,()->{
    if(!AdventData.unlocked(day,adventToday()))return;
    prefs.edit().putBoolean(adventKey(day,"done"),false).apply();draw();
   });
  }
  addText(page,"Chaque proposition est facultative. Vous pouvez la modifier, la remettre à plus tard ou passer sans justification.",13,MUTED,false,16);
  if(adventProgress()==25)
   card("♥  25 moments accomplis !","Le calendrier sera renouvelé en décembre prochain.");
 }
 void drawSettings(){
  heading("⚙  Réglages","Personnalisez votre expérience");
  section("Bibliothèque");
  LinearLayout library=card("♡  Les 60 défis","Sensuel · Coquin · Sexuel — chaque défi avec son exemple.");
  addButton(library,"CONSULTER ET GÉRER LES DÉFIS   →",true,()->{tab="library";draw();});
  section("Mises à jour");
  LinearLayout c=card("✦  Mises à jour automatiques","Vérification au lancement de l'application.");
  CheckBox auto=new CheckBox(this);auto.setText("Rechercher automatiquement les nouveautés");
  auto.setTextColor(PALE);auto.setButtonTintList(ColorStateList.valueOf(PINK));
  auto.setChecked(prefs.getBoolean("auto_update",true));
  auto.setOnCheckedChangeListener((v,b)->prefs.edit().putBoolean("auto_update",b).apply());
  c.addView(auto);
  addText(c,"Source : GitHub Releases · Dés à Deux",12,MUTED,false,4);
  String last=prefs.getString("last_success","");
  addText(c,last.isEmpty()?"Dernière vérification : aucune":"Dernière vérification réussie : "+last,12,GREEN,false,9);
  addButton(c,"↻   RECHERCHER UNE MISE À JOUR",true,()->UpdateManager.check(this,false));
  addText(c,"Téléchargement dans l'app; installation confirmée par Android. Une même signature est nécessaire pour conserver les données.",12,MUTED,false,0);
  section("Dé hebdomadaire");
  LinearLayout weekly=card("🎲  Réinitialiser le dé de la semaine","Permet un nouveau tirage, même avant lundi. Remet aussi le compteur de soirées à zéro.");
  addButton(weekly,"↻   RÉINITIALISER LE DÉ HEBDOMADAIRE",false,()->{
   new AlertDialog.Builder(this).setTitle("Réinitialiser le dé ?")
    .setMessage("Le résultat et la progression de cette semaine seront effacés. Confirmer ?")
    .setPositiveButton("Réinitialiser",(d,w)->{
     prefs.edit().putInt("goal",-1).putInt("done",0)
      .putBoolean("active",false).putBoolean("pending",false)
      .putInt("fred_target",-1).putInt("emilie_target",-1)
      .putInt("fred_completed",0).putInt("emilie_completed",0)
      .putLong("fred_seen",0L).putLong("emilie_seen",0L).apply();
     tab="home";draw();
    }).setNegativeButton("Annuler",null).show();
  });
  section("Règles du jeu");
  card("Répartition hebdomadaire","1× 50 % · 2× 25 % · 3× 12 % · 4× 8 % · 5× 4 % · 6× 1 %");
  card("Quatre tirages en 3D","Semaine : D6 pondéré · Soirée : un D6 chacun · D20 sélection 70/15/15, aléatoire 30/30/40.");
  card("Défis sans répétition","Toute carte proposée, même passée, ne peut pas ressortir pour la même personne pendant la soirée.");
  card("Calendrier de l'Avent","25 cases secrètes qui s'ouvrent à partir de leur date en décembre; dernier samedi avant Noël : défi spécial à deux.");
  card("Intimité et respect","Les défis sont des invitations. Vous pouvez toujours passer. Les données de jeu restent sur ce téléphone.");
  gap(page,8);addText(page,"Dés à Deux · Version "+BuildConfig.VERSION_NAME,12,MUTED,false,0);
 }
 @Override protected void onPause(){
  if(gpuDice!=null)gpuDice.onPause();
  if(gpuD20!=null)gpuD20.onPause();
  super.onPause();
 }
 @Override protected void onResume(){
  super.onResume();
  if(gpuDice!=null)gpuDice.onResume();
  if(gpuD20!=null)gpuD20.onResume();
 }
 @Override public void onBackPressed(){
  if(tab.equals("library")){tab="settings";draw();}
  else if(!tab.equals("home")){tab="home";draw();}
  else super.onBackPressed();
 }
}
