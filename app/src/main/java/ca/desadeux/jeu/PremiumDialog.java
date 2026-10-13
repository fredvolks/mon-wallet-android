package ca.desadeux.jeu;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared premium dark dialogs; no stock Android white popup. */
public final class PremiumDialog {
 private PremiumDialog(){}
 private static int dp(Activity a,float n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
 private static GradientDrawable background(Activity a,int start,int end,int radius,int stroke){
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});
  g.setCornerRadius(dp(a,radius));
  if(stroke!=0)g.setStroke(dp(a,1),stroke);
  return g;
 }
 private static TextView label(Activity a,String t,int sp,int color,boolean bold){
  TextView text=new TextView(a);text.setText(t);text.setTextSize(sp);text.setTextColor(color);
  if(bold)text.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  text.setGravity(Gravity.CENTER);return text;
 }
 public static void info(Activity a,String title,String message){
  open(a,title,message,"COMPRIS",null,null,null);
 }
 public static void confirm(Activity a,String title,String message,String yes,Runnable accept){
  open(a,title,message,yes,accept,"ANNULER",null);
 }
 public static void choice(Activity a,String title,String message,String yes,Runnable accept,
  String no,Runnable cancel){
  open(a,title,message,yes,accept,no,cancel);
 }
 private static void open(Activity a,String title,String message,String yes,Runnable accept,String no,Runnable cancel){
  LinearLayout popup=new LinearLayout(a);popup.setOrientation(1);
  popup.setPadding(dp(a,20),dp(a,22),dp(a,20),dp(a,18));
  popup.setBackground(background(a,0xFF3A1A34,0xFF1B1028,25,0x99FF78B1));
  TextView headline=label(a,title,20,0xFFFFEAF5,true);
  LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);
  hl.bottomMargin=dp(a,13);popup.addView(headline,hl);
  ScrollView scroll=new ScrollView(a);
  scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
  TextView description=label(a,message,15,0xFFE4C7D9,false);
  description.setGravity(Gravity.START);
  description.setLineSpacing(dp(a,3),1f);
  scroll.addView(description);
  LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);
  sl.bottomMargin=dp(a,16);
  sl.height=Math.min(dp(a,420),Math.max(dp(a,80),dp(a,20)+(message.length()/32)*dp(a,19)));
  popup.addView(scroll,sl);
  AlertDialog dialog=new AlertDialog.Builder(a).setView(popup).create();
  Button primary=makeButton(a,yes,true);
  popup.addView(primary,new LinearLayout.LayoutParams(-1,dp(a,48)));
  primary.setOnClickListener(v->{dialog.dismiss();if(accept!=null)accept.run();});
  if(no!=null){
   Button secondary=makeButton(a,no,false);
   LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(-1,dp(a,45));
   nl.topMargin=dp(a,7);popup.addView(secondary,nl);
   secondary.setOnClickListener(v->{dialog.dismiss();if(cancel!=null)cancel.run();});
  }
  dialog.setOnShowListener(x->{
   if(dialog.getWindow()!=null){
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    dialog.getWindow().setDimAmount(.72f);
    dialog.getWindow().setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.9f),
      ViewGroup.LayoutParams.WRAP_CONTENT);
   }
   popup.setAlpha(0);popup.setScaleX(.94f);popup.setScaleY(.94f);
   popup.animate().alpha(1).scaleX(1).scaleY(1).setDuration(240).start();
  });
  dialog.show();
 }
 private static Button makeButton(Activity a,String caption,boolean primary){
  Button b=new Button(a);
  b.setText(caption);b.setAllCaps(false);b.setTextSize(14);
  b.setTextColor(primary?Color.WHITE:0xFFFFD9ED);
  b.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
  b.setBackground(background(a,primary?0xFFFF509B:0xFF47293F,
    primary?0xFFC52672:0xFF291C32,14,primary?0xAAFFCCE2:0x885D3C58));
  return b;
 }
}
