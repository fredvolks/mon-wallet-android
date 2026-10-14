package ca.desadeux.jeu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.CredentialManagerCallback;
import androidx.core.content.ContextCompat;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import org.json.JSONObject;

import java.security.SecureRandom;
import android.util.Base64;

/**
 * LoveRoll signed-in experience stays inside the installed Android app.
 * Auth Google uses Android Credential Manager, NEVER an embedded Google OAuth web page.
 * This screen reuses the existing connected site and Supabase account, without moving,
 * clearing, or modifying PrivateJournal / legacy SharedPreferences.
 */
public final class ConnectedLoveRollActivity extends Activity {
 private static final String TRUSTED_HOST = "fredvolks.github.io";
 private static final String TRUSTED_PATH = "/mon-wallet-android/";
 private static final String SITE = "https://" + TRUSTED_HOST + TRUSTED_PATH;
 // Public OAuth client ID (not a client secret); must match Supabase Google provider.
 private static final String GOOGLE_WEB_CLIENT_ID =
  "158439565502-3cj6kjdntrec6enjpllbi2tknie278hb.apps.googleusercontent.com";
 private WebView web;
 private boolean pageReady = false;
 private boolean authInProgress = false;
 private String pendingGoogleResult;
 private final SecureRandom random = new SecureRandom();

 private final class NativeAuthBridge {
  @JavascriptInterface public void googleSignIn() {
   runOnUiThread(() -> beginGoogleSignIn());
  }
  @JavascriptInterface public void closeLoveRoll() {
   runOnUiThread(() -> finish());
  }
  @JavascriptInterface public void openLegacyOffline() {
   runOnUiThread(() -> {
    if(web==null||!pageReady||!isTrusted(Uri.parse(web.getUrl())))return;
    Intent previous=new Intent(ConnectedLoveRollActivity.this,MainActivity.class)
     .setData(Uri.parse("loveroll://offline"));
    startActivity(previous);
   });
  }
  @JavascriptInterface public void checkForUpdates() {
   runOnUiThread(() -> {
    if(web==null||!pageReady||!isTrusted(Uri.parse(web.getUrl())))return;
    UpdateManager.check(ConnectedLoveRollActivity.this,false);
   });
  }
  // Android shares the personal invitation using the system chooser (Messenger supported).
  @JavascriptInterface public void openShare(String message) {
   runOnUiThread(() -> {
    if(web==null||!pageReady||!isTrusted(Uri.parse(web.getUrl())))return;
    try {
     Intent share=new Intent(Intent.ACTION_SEND);
     share.setType("text/plain");
     share.putExtra(Intent.EXTRA_TEXT,message==null?"":message);
     startActivity(Intent.createChooser(share,"Partager mon invitation LoveRoll"));
    } catch(Exception ignored){show("Partage non disponible sur cet appareil.");}
   });
  }
  // Free SMS share: opens the system composer, never sends silently.
  @JavascriptInterface public void openSms(String message) {
   runOnUiThread(() -> {
    if(web==null || !pageReady || !isTrusted(Uri.parse(web.getUrl())))return;
    try {
     Intent sms = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
     sms.putExtra("sms_body", message == null ? "" : message);
     startActivity(sms);
    } catch(Exception ignored) {show("Aucune application SMS disponible.");}
   });
  }
 }
 @Override public void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  getWindow().setStatusBarColor(Color.rgb(19,8,22));
  getWindow().setNavigationBarColor(Color.rgb(19,8,22));
  LinearLayout layout = new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setBackgroundColor(Color.rgb(16,7,20));
  // Shared LoveRoll web app is the only visible UI; no duplicate native header or tabs.
  web = new WebView(this);
  web.setBackgroundColor(Color.rgb(17,8,21));
  web.setOverScrollMode(View.OVER_SCROLL_NEVER);
  WebSettings settings = web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);
  settings.setAllowContentAccess(false);
  settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setSupportMultipleWindows(false);
  if(Build.VERSION.SDK_INT>=26) settings.setSafeBrowsingEnabled(true);
  web.addJavascriptInterface(new NativeAuthBridge(),"LoveRollNativeAuth");
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient() {
   @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    if(!request.isForMainFrame()) return false;
    Uri url=request.getUrl();
    if("loveroll".equals(url.getScheme()) && "offline".equals(url.getHost())) {
     startActivity(new Intent(ConnectedLoveRollActivity.this, MainActivity.class)
      .setData(url).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
     finish();
     return true;
    }
    if(isTrusted(url))return false;
    if("https".equals(url.getScheme())||"mailto".equals(url.getScheme())) {
     if(request.hasGesture())try {
      startActivity(new Intent(Intent.ACTION_VIEW,url));
     }catch(Exception ignored){show("Impossible d’ouvrir ce lien.");}
    }
    return true;
   }
   @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon) {
    pageReady=false;
   }
   @Override public void onPageFinished(WebView view,String url) {
    pageReady=isTrusted(Uri.parse(url));
    if(pageReady&&pendingGoogleResult!=null) {
     String result=pendingGoogleResult;pendingGoogleResult=null;
     sendGoogleResult(result);
    }
   }
  });
  layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(layout);
  String destination=getIntent().getStringExtra("destination");
  String suffix="";
  if(destination!=null&&destination.startsWith("settings")){
   String section=destination.contains(":")?destination.substring(destination.indexOf(":")+1):"";
   if(section.matches("[a-z]{0,24}"))
    suffix="?settings=1"+(section.isEmpty()?"":"&section="+section);
   else suffix="?settings=1";
  } else if("auth".equals(destination))suffix="?auth=1";
  // Android launcher defaults to the exact same LoveRoll home as iPhone and iPad.
  web.loadUrl(SITE+suffix);
  if(getSharedPreferences("des_a_deux_game",MODE_PRIVATE).getBoolean("auto_update",true))
   UpdateManager.check(this,true);
 }
 private boolean isTrusted(Uri url) {
  return url!=null&&"https".equals(url.getScheme())&&TRUSTED_HOST.equals(url.getHost())
   &&url.getEncodedPath()!=null&&url.getEncodedPath().startsWith(TRUSTED_PATH);
 }
 private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
 private void show(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
 private String randomNonce(){
  byte[] bytes=new byte[32];random.nextBytes(bytes);
  return Base64.encodeToString(bytes,Base64.NO_WRAP|Base64.NO_PADDING|Base64.URL_SAFE);
 }
 private void beginGoogleSignIn() {
  if(authInProgress||web==null||!pageReady||!isTrusted(Uri.parse(web.getUrl())))return;
  authInProgress=true;
  // Supabase hashes the raw nonce before comparing with Google's ID-token claim.
  // Google MUST receive SHA-256 hex; Supabase MUST receive the original nonce.
  final String nonce=randomNonce();
  final String googleNonce=GoogleNonce.sha256Hex(nonce);
  final GetSignInWithGoogleOption option =
   new GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).setNonce(googleNonce).build();
  final GetCredentialRequest request=new GetCredentialRequest.Builder()
   .addCredentialOption(option).build();
  CredentialManager.create(this).getCredentialAsync(
   this,request,new CancellationSignal(),ContextCompat.getMainExecutor(this),
   new CredentialManagerCallback<GetCredentialResponse,GetCredentialException>(){
    @Override public void onResult(GetCredentialResponse response){
     authInProgress=false;
     if(!(response.getCredential() instanceof CustomCredential)){
      deliverError("Réponse de connexion Google inattendue.");return;
     }
     CustomCredential credential=(CustomCredential)response.getCredential();
     if(!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())){
      deliverError("Compte Google non reconnu.");return;
     }
     try{
      String token=GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken();
      JSONObject data=new JSONObject();
      data.put("token",token);
      data.put("nonce",nonce);
      deliver(data);
     }catch(Exception e){deliverError("Connexion Google impossible : vérifiez sa configuration.");}
    }
    @Override public void onError(GetCredentialException error){
     authInProgress=false;
     deliverError("Connexion Google annulée ou indisponible. Réessayez.");
    }
   });
 }
 private void deliverError(String message){
  try{JSONObject o=new JSONObject();o.put("error",message);deliver(o);}catch(Exception ignored){}
 }
 private void deliver(JSONObject result){deliver(result.toString());}
 private void deliver(String result){
  if(web==null)return;
  if(!pageReady){pendingGoogleResult=result;return;}
  sendGoogleResult(result);
 }
 private void sendGoogleResult(String result){
  if(web==null||!isTrusted(Uri.parse(web.getUrl())))return;
  web.evaluateJavascript(
   "window.dispatchEvent(new CustomEvent('loveroll-native-google-result',{detail:"+result+"}));",null);
 }
 @Override public void onBackPressed(){
  if(web!=null&&web.canGoBack())web.goBack();else finish();
 }
 @Override public void onDestroy(){
  if(web!=null){
   web.removeJavascriptInterface("LoveRollNativeAuth");
   web.stopLoading();
   web.destroy();web=null;
  }
  super.onDestroy();
 }
}
