package ca.desadeux.jeu;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.ZoneId;
import ca.desadeux.jeu.GameLogic.Category;

public final class MainActivity extends Activity {
 static final int BG=Color.rgb(20,13,27),PANEL=Color.rgb(48,29,55),
  PINK=Color.rgb(234,67,142),WHITE=Color.rgb(250,238,246),MUTED=Color.rgb(194,173,195);
 SharedPreferences prefs;
 LinearLayout layout;
 boolean settings=false;
 int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  prefs=getSharedPreferences("des_a_deux_game",MODE_PRIVATE);
  if(!GameLogic.monday().equals(prefs.getString("week",""))){
   prefs.edit().putString("week",GameLogic.monday()).putInt("goal",-1)
      .putInt("done",0).putBoolean("active",false).putBoolean("pending",false).apply();
  }
  draw();
  String repo=prefs.getString("repo","");
  String today=LocalDate.now(ZoneId.of("America/Montreal")).toString();
  if(prefs.getBoolean("auto_update",true)&&!repo.isEmpty()&&!today.equals(prefs.getString("checked",""))){
   prefs.edit().putString("checked",today).apply();
   UpdateManager.check(this,repo,true);
  }
 }
 void draw(){
  ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);sc.setFillViewport(true);
  layout=new LinearLayout(this);layout.setOrientation(1);
  layout.setPadding(dp(22),dp(28),dp(22),dp(30));sc.addView(layout);
  setContentView(sc);if(settings)drawSettings();else drawGame();
 }
 void t(String value,int size,int color,boolean bold){
  TextView tv=new TextView(this);tv.setText(value);tv.setTextSize(size);tv.setTextColor(color);
  if(bold)tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  tv.setPadding(0,dp(6),0,dp(6));layout.addView(tv);
 }
 void gap(int n){View v=new View(this);layout.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
 void title(String text){gap(15);t(text,21,PINK,true);}
 void btn(String label,boolean primary,Runnable action){
  Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(WHITE);
  GradientDrawable background=new GradientDrawable();
  background.setColor(primary?PINK:PANEL);background.setCornerRadius(dp(14));
  b.setBackground(background);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
  p.topMargin=dp(10);layout.addView(b,p);b.setOnClickListener(v->action.run());
 }
 void drawGame(){
  t("🎲  DÉS À DEUX",30,WHITE,true);
  t("Un jeu intime à deux ♥",14,MUTED,false);
  int goal=prefs.getInt("goal",-1), done=prefs.getInt("done",0);
  title("Dé hebdomadaire");
  if(goal<0){
   t("Première fois cette semaine : tirez au sort le nombre de soirées.",16,WHITE,false);
   t("1× 50%  ·  2× 25%  ·  3× 12%  ·  4× 8%  ·  5× 4%  ·  6× 1%",13,MUTED,false);
   btn("🎲 BRASSER LE DÉ HEBDOMADAIRE",true,()->{
    int value=GameLogic.rollWeek();prefs.edit().putInt("goal",value).apply();
    new AlertDialog.Builder(this).setTitle("🎲 "+value+" soirée(s) !")
     .setMessage("C'est une invitation à jouer, jamais une obligation. Nouveau tirage lundi.")
     .setPositiveButton("Jouer",(d,w)->draw()).show();
   });showSettingsButton();return;
  }
  t("Objectif : "+done+" / "+goal+" soirée(s) cette semaine",20,WHITE,true);
  t("Le résultat reste sauvegardé jusqu'à lundi.",13,MUTED,false);
  if(!prefs.getBoolean("active",false)){
   title("Commencer une soirée");
   t("Un D6 détermine le nombre de défis pour ce soir.",15,MUTED,false);
   btn("🎲 BRASSER LE D6",true,()->{
    int value=GameLogic.d6();
    prefs.edit().putBoolean("active",true).putInt("total",value).putInt("remaining",value)
      .putBoolean("pending",false).apply();
    new AlertDialog.Builder(this).setTitle("🎲 "+value+" défi(s) ce soir !")
      .setPositiveButton("Continuer",(d,w)->draw()).show();
   });
   if(done>=goal)t("Objectif atteint ! Les soirées bonus restent possibles.",14,MUTED,false);
  }else{
   int remaining=prefs.getInt("remaining",0);
   title("🔥 Soirée en cours");
   t("Défis à lancer : "+remaining+" / "+prefs.getInt("total",0),18,WHITE,true);
   if(prefs.getBoolean("pending",false)){
    Category category=Category.valueOf(prefs.getString("actual","SENSUEL"));
    int face=prefs.getInt("face",1);
    String[] card=GameData.get(category,face);
    title("Dé D20 : "+face+"  ·  "+categoryName(category));
    t(card[0],24,WHITE,true);
    t("Pour "+prefs.getString("card_player","Elle")+" : "+card[1],17,WHITE,false);
    btn("DÉFI TERMINÉ",true,()->{prefs.edit().putBoolean("pending",false).apply();draw();});
    btn("PASSER ET RELANCER SANS PÉNALITÉ",false,()->roll(true));
   }else if(remaining>0){
    title("Qui lance le dé ?");
    String player=prefs.getString("player","Elle");
    spinner(new String[]{"Elle","Lui"},player.equals("Lui")?1:0,true);
    title("Catégorie choisie");
    Category selected=Category.valueOf(prefs.getString("selected","SENSUEL"));
    spinner(new String[]{"Sensuel","Coquin","Sexuel"},selected.ordinal(),false);
    t("80% catégorie choisie, 10% dans chacune des deux autres.",14,MUTED,false);
    btn("🎲 LANCER LE D20",true,()->roll(false));
   }else{
    t("Tous les défis sont terminés. Bravo !",16,WHITE,false);
    btn("TERMINER CETTE SOIRÉE",true,()->{
     prefs.edit().putBoolean("active",false).putInt("done",prefs.getInt("done",0)+1).apply();draw();
    });
   }
   btn("ANNULER LA SOIRÉE",false,()->new AlertDialog.Builder(this)
    .setTitle("Annuler ?").setMessage("Cette partie ne comptera pas dans le total de la semaine.")
    .setPositiveButton("Oui",(d,w)->{
      prefs.edit().putBoolean("active",false).putBoolean("pending",false).apply();draw();
    }).setNegativeButton("Non",null).show());
  }
  showSettingsButton();
  gap(15);
  t("Chaque défi est une proposition libre. Vous pouvez le modifier, passer ou arrêter à tout moment.",13,MUTED,false);
 }
 void roll(boolean reroll){
  int left=prefs.getInt("remaining",0);if(!reroll&&left<=0)return;
  Category selected=Category.valueOf(prefs.getString("selected","SENSUEL"));
  Category actual=GameLogic.choose(selected);
  prefs.edit().putBoolean("pending",true).putInt("face",GameLogic.d20())
    .putInt("remaining",reroll?left:left-1).putString("actual",actual.name())
    .putString("card_player",prefs.getString("player","Elle")).apply();
  draw();
  if(actual!=selected)Toast.makeText(this,
     "SURPRISE ! Catégorie "+categoryName(actual),Toast.LENGTH_LONG).show();
 }
 String categoryName(Category c){
  switch(c){case COQUIN:return "Coquin";case SEXUEL:return "Sexuel";default:return "Sensuel";}
 }
 void spinner(String[] choices,int selected,boolean player){
  Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,choices);
  a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  s.setAdapter(a);s.setSelection(selected);s.setBackgroundColor(PANEL);
  layout.addView(s,new LinearLayout.LayoutParams(-1,dp(52)));
  s.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
   public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
    if(player)prefs.edit().putString("player",choices[pos]).apply();
    else prefs.edit().putString("selected",Category.values()[pos].name()).apply();
   }
  });
 }
 void showSettingsButton(){gap(20);btn("⚙ PARAMÈTRES ET MISES À JOUR",false,()->{settings=true;draw();});}
 void drawSettings(){
  t("⚙ Paramètres",29,WHITE,true);title("Mises à jour de l'application");
  CheckBox auto=new CheckBox(this);auto.setText("Vérifier les nouvelles versions au lancement");
  auto.setTextColor(WHITE);auto.setChecked(prefs.getBoolean("auto_update",true));
  auto.setOnCheckedChangeListener((v,b)->prefs.edit().putBoolean("auto_update",b).apply());
  layout.addView(auto);
  t("Dépôt public des APK (propriétaire/dépôt GitHub)",14,MUTED,false);
  EditText repo=new EditText(this);repo.setSingleLine(true);repo.setTextColor(WHITE);repo.setHintTextColor(MUTED);
  repo.setHint("ex. fredvolks/des-a-deux-android");repo.setText(prefs.getString("repo",""));
  layout.addView(repo);
  btn("SAUVEGARDER",false,()->{prefs.edit().putString("repo",repo.getText().toString().trim()).apply();
   Toast.makeText(this,"Paramètres enregistrés",Toast.LENGTH_SHORT).show();});
  btn("CHERCHER UNE MISE À JOUR",true,()->{
   String name=repo.getText().toString().trim();prefs.edit().putString("repo",name).apply();
   UpdateManager.check(this,name,false);
  });
  t("Les nouvelles versions doivent être publiées en APK dans une release GitHub et signées avec la même clé. Android demande une autorisation avant installation.",13,MUTED,false);
  title("Règles");
  t("Dé hebdomadaire : 50/25/12/8/4/1 %.",15,WHITE,false);
  t("Chaque D20 : 80 % dans la catégorie choisie, 10 % dans chacune des deux autres.",15,WHITE,false);
  t("D6 de soirée : 1 à 6 défis. Possibilité de passer sans pénalité.",15,WHITE,false);
  btn("← RETOUR AU JEU",false,()->{settings=false;draw();});
 }
 @Override public void onBackPressed(){if(settings){settings=false;draw();}else super.onBackPressed();}
}
