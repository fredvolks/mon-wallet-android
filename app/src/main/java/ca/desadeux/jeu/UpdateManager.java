package ca.desadeux.jeu;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public final class UpdateManager {
 public static void check(Activity a,String repo,boolean quiet){
  if(!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")){
   if(!quiet)toast(a,"Configure un dépôt public pour les mises à jour.");return;
  }
  new Thread(()->{
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL("https://api.github.com/repos/"+repo+"/releases/latest").openConnection();
    c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","DesADeux");
    if(c.getResponseCode()!=200)throw new Exception("aucune release publique trouvée");
    ByteArrayOutputStream output=new ByteArrayOutputStream();
    try(InputStream input=c.getInputStream()){byte[] buffer=new byte[8192];int n;
     while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}
    JSONObject release=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
    String version=release.optString("tag_name","").replaceFirst("^[vV]","");
    JSONArray assets=release.optJSONArray("assets");String apkUrl="";
    if(assets!=null)for(int i=0;i<assets.length();i++){
     JSONObject item=assets.getJSONObject(i);
     if(item.optString("name").toLowerCase().endsWith(".apk")){
      apkUrl=item.optString("browser_download_url");break;
     }
    }
    if(apkUrl.isEmpty())throw new Exception("aucun APK disponible");
    if(!apkUrl.startsWith("https://github.com/"))throw new Exception("lien non approuvé");
    if(compare(version,BuildConfig.VERSION_NAME)<=0){
     if(!quiet)toast(a,"Ton application est à jour.");return;
    }
    String url=apkUrl;
    a.runOnUiThread(()->new AlertDialog.Builder(a)
     .setTitle("Nouvelle version "+version)
     .setMessage("Télécharger la mise à jour ? Android te demandera de confirmer l'installation.")
     .setPositiveButton("Télécharger",(d,w)->download(a,url))
     .setNegativeButton("Plus tard",null).show());
   }catch(Exception e){if(!quiet)toast(a,"Vérification impossible : "+e.getMessage());}
   finally{if(c!=null)c.disconnect();}
  }).start();
 }
 static int compare(String first,String second){
  String[] a=first.replaceAll("[^0-9.]","").split("\\.");
  String[] b=second.replaceAll("[^0-9.]","").split("\\.");
  for(int i=0;i<Math.max(a.length,b.length);i++){
   int x=i<a.length&&!a[i].isEmpty()?Integer.parseInt(a[i]):0;
   int y=i<b.length&&!b[i].isEmpty()?Integer.parseInt(b[i]):0;
   if(x!=y)return Integer.compare(x,y);
  }return 0;
 }
 private static void download(Activity a,String url){
  if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
   a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
    Uri.parse("package:"+a.getPackageName())));
   toast(a,"Autorise l'installation puis relance la recherche de mise à jour.");return;
  }
  toast(a,"Téléchargement de la mise à jour...");
  new Thread(()->{
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(15000);c.setReadTimeout(30000);
    if(c.getResponseCode()!=200)throw new Exception("erreur HTTP "+c.getResponseCode());
    File dir=new File(a.getCacheDir(),"updates");
    if(!dir.exists()&&!dir.mkdirs())throw new Exception("cache inaccessible");
    File apk=new File(dir,"des-a-deux-update.apk");
    try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(apk)){
     byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
    }
    Uri uri=FileProvider.getUriForFile(a,a.getPackageName()+".fileprovider",apk);
    a.runOnUiThread(()->{
     Intent intent=new Intent(Intent.ACTION_VIEW);
     intent.setDataAndType(uri,"application/vnd.android.package-archive");
     intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
     a.startActivity(intent);
    });
   }catch(Exception e){toast(a,"Mise à jour impossible : "+e.getMessage());}
   finally{if(c!=null)c.disconnect();}
  }).start();
 }
 private static void toast(Activity a,String text){
  a.runOnUiThread(()->Toast.makeText(a,text,Toast.LENGTH_LONG).show());
 }
}
