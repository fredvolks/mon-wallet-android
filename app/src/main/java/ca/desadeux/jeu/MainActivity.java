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

public final class MainActivity extends Activity {
 static final int BG=0xFF110B19,SURFACE=0xFF261527,CARD2=0xFF35182F;
 static final int PINK=0xFFFF4F9B,PALE=0xFFFFDDED,WHITE=0xFFFFF5FA,MUTED=0xFFBBA7B8;
 static final int GREEN=0xFF6BE0A7;
 SharedPreferences prefs;
 LinearLayout root,page,nav;
 ScrollView scroll;
 Dice3DView dice;
 String tab="home",player="Elle";
 Category selected=Category.COQUIN;
 boolean rolling=false;
 int width=0;
 int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
 @Override public void onCreate(Bundle bundle){
  super.onCreate(bundle);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
  prefs=getSharedPreferences("des_a_deux_game",MODE_PRIVATE);
  if(!GameLogic.monday().equals(prefs.getString("week",""))){
   prefs.edit().putString("week",GameLogic.monday()).putInt("goal",-1).putInt("done",0)
    .putBoolean("active",false).putBoolean("pending",false).apply();
  }
  player=prefs.getString("player","Elle");
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
  FrameLayout frame=new FrameLayout(this);frame.setBackground(shape(0xFF220D24,0xFF180E22,26,0x335A3558));
  LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(255));fl.bottomMargin=dp(14);page.addView(frame,fl);
  dice=new Dice3DView(this);dice.setSides(sides);dice.setResult(result);
  frame.addView(dice,new FrameLayout.LayoutParams(-1,-1));
  TextView caption=text("GLISSE POUR TOURNER LE DÉ",10,0xFFD2A0BD,true);caption.setGravity(Gravity.CENTER);
  FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(28),Gravity.BOTTOM);cp.bottomMargin=dp(5);
  frame.addView(caption,cp);
 }
 void roll(int value,Runnable end){
  if(rolling||dice==null)return;rolling=true;
  vibrate(36);
  dice.roll(value,()->{rolling=false;vibrate(85);end.run();});
 }
 void vibrate(long ms){
  try{Vibrator v=(Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
   if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));
  }catch(Exception ignored){}
 }
 void draw(){
  root=vertical();root.setBackgroundColor(BG);
  scroll=new ScrollView(this);scroll.setFillViewport(false);
  scroll.setVerticalScrollBarEnabled(false);
  page=vertical();page.setPadding(dp(20),dp(25),dp(20),dp(20));
  scroll.addView(page);
  root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  nav=horizontal();nav.setBackground(shape(0xFF221326,0xFF1A1021,0,0));pad(nav,9,11);
  root.addView(nav,new LinearLayout.LayoutParams(-1,dp(76)));
  setContentView(root);
  switch(tab){
   case "game":drawGame();break;
   case "library":drawLibrary();break;
   case "settings":drawSettings();break;
   default:drawHome();break;
  }
  drawNav();
 }
 void drawNav(){
  for(String[] item:new String[][]{{"home","⌂","Accueil"},{"game","⚄","Soirée"},{"library","♡","Défis"},{"settings","⚙","Réglages"}}){
   LinearLayout col=vertical();col.setGravity(Gravity.CENTER);
   boolean active=tab.equals(item[0]);
   TextView icon=text(item[1],27,active?PINK:MUTED,false);icon.setGravity(Gravity.CENTER);col.addView(icon);
   TextView name=text(item[2],11,active?PALE:MUTED,active);name.setGravity(Gravity.CENTER);col.addView(name);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);
   nav.addView(col,lp);col.setOnClickListener(v->{if(rolling)return;tab=item[0];draw();});
  }
 }
 void drawHome(){
  heading("♡  DÉS À DEUX","Un jeu intime, juste pour vous deux");
  addText(page,"LE RITUEL DE LA SEMAINE",12,PINK,true,9);
  int goal=prefs.getInt("goal",-1),done=prefs.getInt("done",0);
  showDice(6,goal>0?goal:1);
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
    int n=GameLogic.rollWeek();
    roll(n,()->{
     prefs.edit().putInt("goal",n).apply();draw();
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
  LinearLayout info=card("3 dés, une soirée unique","① Dé hebdomadaire : nombre de soirées\n② D6 de soirée : 1 à 6 défis\n③ D20 : 20 défis par catégorie");
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
 void drawGame(){
  heading("🔥  Votre soirée","Laissez le hasard guider vos envies");
  int goal=prefs.getInt("goal",-1);
  if(goal<0){
   card("D'abord le tirage hebdomadaire","Brassez le premier dé à l'accueil.");
   addButton(page,"ALLER À L'ACCUEIL",true,()->{tab="home";draw();});
   return;
  }
  boolean active=prefs.getBoolean("active",false);
  if(!active){
   addText(page,"ÉTAPE 2 / 3    ·    DÉ DE SOIRÉE",12,PINK,true,13);
   showDice(6,1);
   LinearLayout c=card("🎲  Combien de défis ce soir ?","Un D6 équitable : chaque résultat de 1 à 6 a la même chance.");
   addButton(c,"BRASSER LE D6 EN 3D",true,()->{
    int n=GameLogic.d6();
    roll(n,()->{
     prefs.edit().putBoolean("active",true).putInt("total",n).putInt("remaining",n)
      .putBoolean("pending",false).apply();
     draw();
     new AlertDialog.Builder(this).setTitle("🎲  "+n+" défi(s) ce soir")
      .setMessage("Choisissez vos catégories et brassez le D20 pour découvrir les défis.")
      .setPositiveButton("On joue !",(d,w)->{}).show();
    });
   });
   return;
  }
  int remaining=prefs.getInt("remaining",0),total=prefs.getInt("total",1);
  addText(page,"ÉTAPE 3 / 3    ·    D20 DES DÉFIS",12,PINK,true,8);
  boolean pending=prefs.getBoolean("pending",false);
  Category actual=Category.valueOf(prefs.getString("actual","COQUIN"));
  int face=prefs.getInt("face",1);
  showDice(20,pending?face:20);
  LinearLayout counter=card("♢  "+(total-remaining)+" / "+total+" défis lancés","À chacun son tour. Changez de catégorie quand vous voulez.");
  progress(counter,(float)(total-remaining)/total);
  if(pending){
   String[] result=GameData.get(actual,face);
   LinearLayout resultCard=card("♥  "+categoryName(actual)+"  ·  Face "+face,null);
   addText(resultCard,result[0],23,WHITE,true,9);
   addText(resultCard,result[1],17,PALE,false,7);
   addText(resultCard,"Pour : "+prefs.getString("card_player","Elle"),13,MUTED,false,0);
   addButton(resultCard,"✓   DÉFI TERMINÉ",true,()->{
    prefs.edit().putBoolean("pending",false).apply();draw();
   });
   addButton(resultCard,"↻   PASSER / RELANCER",false,()->{
    prefs.edit().putBoolean("pending",false).putInt("remaining",remaining+1).apply();draw();
   });
  }else if(remaining>0){
   section("Qui brasse ?");
   LinearLayout players=horizontal();page.addView(players);
   chip(players,"♀  Elle",player.equals("Elle"),()->{player="Elle";prefs.edit().putString("player",player).apply();draw();},1);
   chip(players,"♂  Lui",player.equals("Lui"),()->{player="Lui";prefs.edit().putString("player",player).apply();draw();},1);
   section("Choisissez votre catégorie");
   LinearLayout row=horizontal();page.addView(row);
   for(Category cat:Category.values()){
    chip(row,categoryName(cat),selected==cat,()->{
     selected=cat;prefs.edit().putString("selected",cat.name()).apply();draw();
    },1);
   }
   gap(page,15);
   LinearLayout odds=card("✦  20 % de surprise","80 % dans votre catégorie · 10 % dans chacune des autres.");
   addText(odds,"La catégorie surprise est déterminée automatiquement.",12,PALE,false,0);
   addButton(page,"🎲  BRASSER LE D20 EN 3D",true,()->{
    Category category=GameLogic.choose(selected);
    int chosenFace=GameLogic.d20();
    roll(chosenFace,()->{
     prefs.edit().putBoolean("pending",true).putString("actual",category.name())
      .putString("card_player",player).putInt("face",chosenFace)
      .putInt("remaining",remaining-1).apply();
     draw();
     if(category!=selected)Toast.makeText(this,"SURPRISE ! Catégorie "+categoryName(category),Toast.LENGTH_LONG).show();
    });
   });
  }else{
   LinearLayout c=card("♥  Votre soirée est terminée","Bravo ! Vous pouvez revenir pour une autre soirée quand vous en avez envie.");
   addButton(c,"TERMINER ET COMPTER LA SOIRÉE",true,()->{
    prefs.edit().putBoolean("active",false).putInt("done",prefs.getInt("done",0)+1).apply();
    tab="home";draw();
   });
  }
  gap(page,8);
  addButton(page,"Arrêter la soirée sans la compter",false,()->new AlertDialog.Builder(this)
   .setTitle("Arrêter la soirée ?")
   .setMessage("Le compteur hebdomadaire ne sera pas modifié.")
   .setPositiveButton("Arrêter",(d,w)->{
    prefs.edit().putBoolean("active",false).putBoolean("pending",false).apply();draw();
   }).setNegativeButton("Continuer",null).show());
  gap(page,10);
  addText(page,"Tout défi peut être passé, modifié ou arrêté, sans justification.",12,MUTED,false,0);
 }
 String categoryName(Category c){
  switch(c){case COQUIN:return "Coquin";case SEXUEL:return "Sexuel";default:return "Sensuel";}
 }
 void drawLibrary(){
  heading("♡  Les défis","60 idées pour vos soirées intimes");
  section("Explorer les catégories");
  LinearLayout row=horizontal();page.addView(row);
  for(Category c:Category.values())chip(row,categoryName(c),selected==c,()->{
   selected=c;prefs.edit().putString("selected",c.name()).apply();draw();
  },1);
  gap(page,16);
  addText(page,categoryName(selected).toUpperCase()+"  ·  20 FACES",12,PINK,true,13);
  for(int i=1;i<=20;i++){
   String[] item=GameData.get(selected,i);
   LinearLayout c=card(String.format(java.util.Locale.CANADA_FRENCH,"%02d  ♥  %s",i,item[0]),item[1]);
  }
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
      .putBoolean("active",false).putBoolean("pending",false).apply();
     tab="home";draw();
    }).setNegativeButton("Annuler",null).show();
  });
  section("Règles du jeu");
  card("Répartition hebdomadaire","1× 50 % · 2× 25 % · 3× 12 % · 4× 8 % · 5× 4 % · 6× 1 %");
  card("Trois dés 3D","Semaine : D6 pondéré · Soirée : D6 équitable · Défi : D20 avec 80/10/10.");
  card("Intimité et respect","Les défis sont des invitations. Vous pouvez toujours passer. Les données de jeu restent sur ce téléphone.");
  gap(page,8);addText(page,"Dés à Deux · Version "+BuildConfig.VERSION_NAME,12,MUTED,false,0);
 }
 @Override public void onBackPressed(){
  if(!tab.equals("home")){tab="home";draw();}else super.onBackPressed();
 }
}
