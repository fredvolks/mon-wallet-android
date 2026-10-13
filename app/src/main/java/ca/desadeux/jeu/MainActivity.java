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
 String tab="home",player="Émilie";
 int adventSelected=1;
 Category selected=Category.COQUIN;
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
  t.setLineSpacing(dp(2),1.0f);
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
  LinearLayout c=vertical();c.setBackground(shape(SURFACE,CARD2,22,0x4CFF6FA4));pad(c,18,16);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.bottomMargin=dp(15);page.addView(c,lp);
  if(title!=null)addText(c,title,18,PALE,true,subtitle==null?0:5);
  if(subtitle!=null)addText(c,subtitle,13,MUTED,false,0);
  return c;
 }
 void heading(String big,String subtitle){
  addText(page,big,27,WHITE,true,5);
  if(subtitle!=null)addText(page,subtitle,14,MUTED,false,16);
 }
 void section(String title){gap(page,9);addText(page,title,19,PINK,true,10);}
 void addButton(LinearLayout dest,String title,boolean highlighted,Runnable action){
  Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);
  b.setTextColor(highlighted?WHITE:PALE);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  b.setBackground(shape(highlighted?0xFFFF4A9A:0xFF47243D,highlighted?0xFFC82870:0xFF2F1A31,16,highlighted?0x99FFB6D8:0x66F26B9C));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.topMargin=dp(9);
  dest.addView(b,lp);b.setOnClickListener(v->{if(!rolling)action.run();});
 }
 void chip(LinearLayout target,String title,boolean active,Runnable action,int weight){
  TextView v=text(title,13,active?WHITE:PALE,true);v.setGravity(Gravity.CENTER);
  v.setPadding(dp(6),dp(11),dp(6),dp(11));
  v.setBackground(shape(active?0xFFD02D77:0xFF3B263C,active?0xFF822353:0xFF241628,13,active?PINK:0x554B3A4C));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),weight);
  lp.rightMargin=dp(6);target.addView(v,lp);v.setOnClickListener(q->{if(!rolling)action.run();});
 }
 void showDice(int sides,int result){
  FrameLayout frame=new FrameLayout(this);
  frame.setBackground(shape(0xFF220D24,0xFF180E22,26,0x335A3558));
  LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(270));
  fl.bottomMargin=dp(14);
  page.addView(frame,fl);
  dice=null;gpuDice=null;
  if(sides==6&&PremiumDice3DView.supported(this)){
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
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(29),Gravity.BOTTOM);
  frame.addView(caption,cp);
  caption.setOnClickListener(v->{
   Toast.makeText(this,"Brasse le dé avec le bouton pour obtenir un résultat officiel.",Toast.LENGTH_SHORT).show();
  });
 }
 void roll(int value,Runnable end){
  if(rolling||(dice==null&&gpuDice==null))return;
  rolling=true;
  vibrate(36);
  Runnable finished=()->{
   rolling=false;vibrate(85);end.run();
  };
  if(gpuDice!=null)gpuDice.roll(value,finished);
  else dice.roll(value,finished);
 }
 void vibrate(long ms){
  try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
   if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
  }catch(Exception ignored){}
 }
 void draw(){
  if(gpuDice!=null){
   gpuDice.onPause();
   gpuDice=null;
  }
  dice=null;
  root=vertical();root.setBackgroundColor(BG);
  scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  page=vertical();page.setPadding(dp(20),dp(25),dp(20),dp(20));
  scroll.addView(page);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  nav=horizontal();nav.setBackground(shape(0xFF221326,0xFF1A1021,0,0));pad(nav,9,11);
  root.addView(nav,new LinearLayout.LayoutParams(-1,dp(76)));
  ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
   int top=insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
   int bottom=insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
   root.setPadding(0,top,0,0);
   nav.setPadding(dp(9),dp(9),dp(9),dp(9)+bottom);
   ViewGroup.LayoutParams params=nav.getLayoutParams();
   int desired=dp(66)+bottom;
   if(params.height!=desired){params.height=desired;nav.setLayoutParams(params);}
   return insets;
  });
  setContentView(root);
  ViewCompat.requestApplyInsets(root);
  switch(tab){
   case "game":drawGame();break;
   case "library":drawLibrary();break;
   case "advent":drawAdvent();break;
   case "settings":drawSettings();break;
   default:drawHome();break;
  }
  drawNav();
 }
 void drawNav(){
  for(String[] item:new String[][]{{"home","⌂","Accueil"},{"game","⚄","Soirée"},{"advent","🎁","Avent"},{"library","♡","Défis"},{"settings","⚙","Réglages"}}){
   LinearLayout col=vertical();col.setGravity(Gravity.CENTER);
   boolean active=tab.equals(item[0]);
   TextView icon=text(item[1],item[0].equals("advent")?23:25,active?PINK:MUTED,false);icon.setGravity(Gravity.CENTER);col.addView(icon);
   TextView name=text(item[2],10,active?PALE:MUTED,active);name.setGravity(Gravity.CENTER);col.addView(name);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
   nav.addView(col,lp);col.setOnClickListener(v->{if(rolling)return;tab=item[0];draw();});
  }
 }
 void drawHome(){
  heading("♡  DÉS À DEUX","Un jeu intime, juste pour vous deux");
  addText(page,"LE RITUEL DE LA SEMAINE",12,PINK,true,9);
  int goal=prefs.getInt("goal",-1),done=prefs.getInt("done",0);
  showDice(6,goal>0?goal:1);
  if(goal>0)card("🎯  Résultat officiel : "+goal+" soirée(s)",
   "Ce nombre est enregistré pour cette semaine.");
  if(goal<0){
   LinearLayout c=card("🎲  Le dé hebdomadaire","Un seul tirage par semaine. Le dé choisit le nombre de soirées.");
   LinearLayout chips=horizontal();c.addView(chips);
   for(String p:new String[]{"1×\n50%","2×\n25%","3×\n12%","4×\n8%","5×\n4%","6×\n1%"}){
    TextView v=text(p,11,PALE,true);v.setGravity(Gravity.CENTER);
    v.setBackground(shape(0xFF49223B,0xFF2F1730,9,0x337A4A69));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);
    lp.rightMargin=dp(3);chips.addView(v,lp);
   }
   gap(c,13);
   addButton(c,"🎲   BRASSER LE DÉ HEBDOMADAIRE",true,()->{
    if(prefs.getInt("goal",-1)>0)return;
    int n=GameLogic.rollWeek();
    // Commit BEFORE animation so app termination cannot grant an extra weekly roll.
    if(!prefs.edit().putInt("goal",n).commit()){
     Toast.makeText(this,"Impossible d'enregistrer le tirage. Réessayez.",Toast.LENGTH_LONG).show();
     return;
    }
    roll(n,()->{
     draw();
     new AlertDialog.Builder(this).setTitle("♥  "+n+" soirée(s) cette semaine")
      .setMessage("Votre tirage est enregistré jusqu'à lundi. C'est un jeu, jamais une obligation.")
      .setPositiveButton("C'est parti !",(d,w)->{}).show();
    });
   });
  }else{
   LinearLayout c=card("♥  "+goal+" soirée(s) cette semaine","Le dé a été brassé. Nouveau tirage lundi !");
   addText(c,"PROGRESSION    "+done+" / "+goal,18,WHITE,true,9);
   progress(c,goal==0?0:((float)Math.min(done,goal)/goal));
   addButton(c,"COMMENCER UNE SOIRÉE   →",true,()->{tab="game";draw();});
  }
  section("Votre rituel");
  LinearLayout info=card("3 dés, une soirée unique","① Dé hebdomadaire : nombre de soirées\n② Deux D6 : défis reçus par Fred et Émilie\n③ D20 : 20 défis par catégorie");
  addText(info,"🔥 Sensuel  ·  Coquin  ·  Sexuel",14,PALE,true,0);
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
  ChallengePicker.Pick next=ChallengePicker.draw(selected,history);
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
   addText(c,"Émilie reçoit : "+completed("Émilie")+" / "+e+"  •  Fred donne",15,WHITE,true,8);
   progress(c,(float)completed("Émilie")/e);
  }else addText(c,"Émilie reçoit : dé non brassé",15,MUTED,false,7);
  gap(c,12);
  if(f>0){
   addText(c,"Fred reçoit : "+completed("Fred")+" / "+f+"  •  Émilie donne",15,WHITE,true,8);
   progress(c,(float)completed("Fred")/f);
  }else addText(c,"Fred reçoit : dé non brassé",15,MUTED,false,0);
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
   addText(page,"ÉTAPE 2 / 3   ·   DEUX D6 INDIVIDUELS",12,PINK,true,13);
   int last=prefs.getInt("setup_last_face",1);
   showDice(6,last);
   String lastReceiver=prefs.getString("setup_last_receiver","");
   if(!lastReceiver.isEmpty())card("🎯 Résultat : "+lastReceiver+" reçoit "+last+" défi(s)",
    "Le tirage est confirmé et enregistré.");
   LinearLayout explanation=card("🎲  Un dé chacun","Chaque personne brasse pour connaître le nombre de défis qu'elle reçoit (1 à 6). Le partenaire donne exactement ce nombre de défis.");
   addText(explanation,"Si Émilie reçoit 4, Fred donne 4. Si Fred reçoit 2, Émilie donne 2.",13,PALE,false,0);
   duoProgress();
   if(emilie<0){
    addButton(page,"🎲  ÉMILIE BRASSE : JE REÇOIS…",true,()->{
     int n=GameLogic.d6();
     // Preserve Émilie's number even if Android interrupts the GPU animation.
     if(!prefs.edit().putInt("emilie_target",n).putInt("setup_last_face",n)
       .putString("setup_last_receiver","Émilie").commit())return;
     roll(n,()->{
      draw();
      new AlertDialog.Builder(this).setTitle("♥ Émilie reçoit "+n+" défi(s)")
       .setMessage("Fred donne "+n+" défi(s) à Émilie durant la soirée.")
       .setPositiveButton("Continuer",null).show();
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
      new AlertDialog.Builder(this).setTitle("♥ Fred reçoit "+n+" défi(s)")
       .setMessage("Émilie donne "+n+" défi(s) à Fred durant la soirée.")
       .setPositiveButton("Continuer",null).show();
     });
    });
   }
   if(fred>0&&emilie>0){
    LinearLayout summary=card("♡  Votre programme de soirée",null);
    addText(summary,"Fred : reçoit "+fred+"  ·  donne "+emilie,17,WHITE,true,8);
    addText(summary,"Émilie : reçoit "+emilie+"  ·  donne "+fred,17,WHITE,true,8);
    addText(summary,(fred+emilie)+" défis au total à partager.",13,PALE,false,0);
    addButton(summary,"♥  COMMENCER LA SOIRÉE",true,()->{
     prefs.edit().putBoolean("active",true).putBoolean("pending",false).apply();
     if(completed("Émilie")<emilie)player="Émilie";else player="Fred";
     prefs.edit().putString("player",player).apply();draw();
    });
   }
   addButton(page,"↻   RECOMMENCER LES DEUX D6",false,()->confirmResetEvening());
   return;
  }
  int fredDone=completed("Fred"),emilieDone=completed("Émilie");
  int total=fred+emilie,done=fredDone+emilieDone;
  addText(page,"ÉTAPE 3 / 3   ·   D20 DES DÉFIS",12,PINK,true,8);
  boolean pending=prefs.getBoolean("pending",false);
  Category actual=Category.valueOf(prefs.getString("actual","COQUIN"));
  int face=prefs.getInt("face",1);
  showDice(20,pending?face:20);
  if(pending)card("🎯  "+categoryName(actual)+" · Face "+face,
   "Défi proposé à "+prefs.getString("card_player","Émilie")+".");
  LinearLayout counter=card("♢  "+done+" / "+total+" défis réalisés","Le D20 est brassé par celui ou celle qui donne le défi.");
  progress(counter,(float)done/Math.max(1,total));
  duoProgress();
  if(pending){
   String receiver=prefs.getString("card_player","Émilie");
   String giver=other(receiver);
   String[] result=GameData.get(actual,face);
   LinearLayout resultCard=card("♥  "+categoryName(actual)+"  ·  Face "+face,null);
   addText(resultCard,"🎁  QUI OFFRE / PROPOSE : "+giver.toUpperCase(java.util.Locale.CANADA_FRENCH),15,PINK,true,7);
   addText(resultCard,"♡  QUI REÇOIT : "+receiver.toUpperCase(java.util.Locale.CANADA_FRENCH),15,WHITE,true,13);
   addText(resultCard,result[0],24,WHITE,true,9);
   addText(resultCard,"À faire : "+result[1],17,PALE,false,11);
   addText(resultCard,"EXEMPLE CONCRET",12,PINK,true,5);
   addText(resultCard,result[2],15,MUTED,false,13);
   addText(resultCard,"Défis différents proposés à "+receiver+" : "+
    (60-ChallengePicker.remaining(seenMask(receiver)))+" / 60",12,MUTED,false,9);
   if(!prefs.getBoolean("accepted",false)){
    addButton(resultCard,"✓   ACCEPTER CE DÉFI",true,()->{
     prefs.edit().putBoolean("accepted",true).apply();draw();
    });
    addButton(resultCard,"↻   PASSER · NOUVEAU DÉFI",false,()->offerChallenge(receiver));
   }else{
    addText(resultCard,"✓ Défi accepté — terminez-le à votre rythme.",15,GREEN,true,7);
    addButton(resultCard,"✓   DÉFI TERMINÉ",true,()->{
     String key=receiver.equals("Fred")?"fred_completed":"emilie_completed";
     prefs.edit().putInt(key,completed(receiver)+1)
      .putBoolean("pending",false).putBoolean("accepted",false).apply();
     draw();
    });
    addButton(resultCard,"↻   CHANGER D'AVIS · PASSER",false,()->offerChallenge(receiver));
   }
  }else if(done<total){
   if(target(player)<0||completed(player)>=target(player)){
    player=emilieDone<emilie?"Émilie":"Fred";
    prefs.edit().putString("player",player).apply();
   }
   section("Qui reçoit le prochain défi ?");
   LinearLayout players=horizontal();page.addView(players);
   if(emilieDone<emilie)
    chip(players,"Émilie reçoit",player.equals("Émilie"),()->setReceiver("Émilie"),1);
   if(fredDone<fred)
    chip(players,"Fred reçoit",player.equals("Fred"),()->setReceiver("Fred"),1);
   addText(page,other(player)+" brasse pour "+player,16,PALE,true,10);
   section("Catégorie du prochain défi");
   LinearLayout row=horizontal();page.addView(row);
   for(Category cat:Category.values())
    chip(row,categoryName(cat),selected==cat,()->{
     selected=cat;prefs.edit().putString("selected",cat.name()).apply();draw();
    },1);
   gap(page,15);
   card("✦  20 % de surprise","80 % dans la catégorie choisie · 10 % dans chacune des deux autres.");
   addButton(page,"🎲  "+other(player).toUpperCase()+" BRASSE LE D20",true,()->offerChallenge(player));
  }else{
   LinearLayout c=card("♥  Tous les défis sont réalisés","Fred et Émilie ont chacun reçu leur nombre de défis.");
   addButton(c,"TERMINER ET COMPTER LA SOIRÉE",true,()->{
    prefs.edit().putInt("done",prefs.getInt("done",0)+1).apply();
    resetEvening();tab="home";draw();
   });
  }
  gap(page,8);
  addButton(page,"Arrêter la soirée sans la compter",false,()->confirmResetEvening());
  gap(page,10);
  addText(page,"Chacun peut refuser, modifier ou interrompre un défi. Un défi passé ne réduit pas le nombre restant.",12,MUTED,false,0);
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
 void drawLibrary(){
  heading("♡  Les défis","60 cartes revues avec consignes et exemples précis");
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
  heading("⚙  Paramètres","Personnalisez votre expérience");
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
  card("Quatre tirages en 3D","Semaine : D6 pondéré · Soirée : un D6 chacun · Défis : D20 à 80/10/10.");
  card("Défis sans répétition","Toute carte proposée, même passée, ne peut pas ressortir pour la même personne pendant la soirée.");
  card("Calendrier de l'Avent","25 cases secrètes qui s'ouvrent à partir de leur date en décembre; dernier samedi avant Noël : défi spécial à deux.");
  card("Intimité et respect","Les défis sont des invitations. Vous pouvez toujours passer. Les données de jeu restent sur ce téléphone.");
  gap(page,8);addText(page,"Dés à Deux · Version "+BuildConfig.VERSION_NAME,12,MUTED,false,0);
 }
 @Override protected void onPause(){
  if(gpuDice!=null)gpuDice.onPause();
  super.onPause();
 }
 @Override protected void onResume(){
  super.onResume();
  if(gpuDice!=null)gpuDice.onResume();
 }
 @Override public void onBackPressed(){
  if(!tab.equals("home")){tab="home";draw();}else super.onBackPressed();
 }
}
