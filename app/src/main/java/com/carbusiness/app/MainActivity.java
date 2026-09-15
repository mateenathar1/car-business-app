package com.carbusiness.app;

import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.webkit.*;
import android.net.Uri;
import android.app.AlertDialog;
import android.widget.EditText;
import android.text.InputType;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends ComponentActivity {
  private WebView webView; private byte[] pendingBytes; private ValueCallback<Uri[]> fileChooserCallback;
  private ActivityResultLauncher<Intent> saveLauncher,openLauncher;
  private SharedPreferences prefs; private long backgroundAt=0; private boolean unlocked=false;
  private File backupDir, permanentDir;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b); prefs=getSharedPreferences("mw_secure",MODE_PRIVATE);
    backupDir=new File(getFilesDir(),"My Wheels/Backups/Recovery"); if(!backupDir.exists()) backupDir.mkdirs();
    permanentDir=new File(getFilesDir(),"My Wheels/Backups/Permanent"); if(!permanentDir.exists()) permanentDir.mkdirs();
    saveLauncher=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{if(r.getResultCode()==RESULT_OK&&r.getData()!=null&&r.getData().getData()!=null&&pendingBytes!=null){try(OutputStream out=getContentResolver().openOutputStream(r.getData().getData())){out.write(pendingBytes);out.flush();js("toast('File saved successfully')");}catch(Exception e){js("alert('Could not save file. Please try another folder.')");}}pendingBytes=null;});
    openLauncher=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{if(fileChooserCallback==null)return;Uri[] u=null;if(r.getResultCode()==RESULT_OK&&r.getData()!=null&&r.getData().getData()!=null)u=new Uri[]{r.getData().getData()};fileChooserCallback.onReceiveValue(u);fileChooserCallback=null;});
    webView=new WebView(this);setContentView(webView);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);
    webView.setWebViewClient(new WebViewClient());webView.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){if(fileChooserCallback!=null)fileChooserCallback.onReceiveValue(null);fileChooserCallback=cb;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");openLauncher.launch(i);return true;}});
    webView.addJavascriptInterface(new AndroidBridge(),"AndroidBridge");webView.loadUrl("file:///android_asset/index.html");
    if(isLockEnabled()) showUnlock(); else unlocked=true;
  }
  private void js(String x){runOnUiThread(()->webView.evaluateJavascript(x,null));}
  private String sha(String x){try{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] a=d.digest(x.getBytes(StandardCharsets.UTF_8));StringBuilder z=new StringBuilder();for(byte q:a)z.append(String.format("%02x",q));return z.toString();}catch(Exception e){return "";}}
  private boolean isLockEnabled(){return !prefs.getString("pin_hash","").isEmpty();}
  private void showUnlock(){
    if(unlocked)return;
    if(androidx.biometric.BiometricManager.from(this).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG|androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)==androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS){doBiometric();return;}
    showPinDialog();
  }
  private void doBiometric(){
    BiometricPrompt p=new BiometricPrompt(this,ContextCompat.getMainExecutor(this),new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){unlocked=true;}@Override public void onAuthenticationError(int c,CharSequence e){showPinDialog();}});
    p.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Unlock My Wheels").setSubtitle("Use fingerprint, face or device credential").setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG|androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build());
  }
  private void showPinDialog(){
    EditText e=new EditText(this);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setHint("PIN");
    new AlertDialog.Builder(this).setTitle("My Wheels Locked").setMessage("Enter PIN. Use Recovery if you forgot it.").setView(e).setCancelable(false).setPositiveButton("Unlock",(d,w)->{if(sha(e.getText().toString()).equals(prefs.getString("pin_hash",""))){unlocked=true;}else{new AlertDialog.Builder(this).setMessage("Incorrect PIN").setPositiveButton("Try again",(a,q)->showPinDialog()).show();}}).setNegativeButton("Recovery",(d,w)->showRecoveryDialog()).show();
  }
  private void showRecoveryDialog(){
    EditText e=new EditText(this);e.setHint("Recovery code");
    new AlertDialog.Builder(this).setTitle("Recover App Access").setMessage("Enter your recovery code. Your business data will not be deleted.").setView(e).setCancelable(false).setPositiveButton("Verify",(d,w)->{if(sha(e.getText().toString().trim().toUpperCase(Locale.ROOT)).equals(prefs.getString("recovery_hash",""))){prefs.edit().remove("pin_hash").apply();unlocked=true;js("toast('PIN reset. Set a new PIN in Settings.')");}else showPinDialog();}).setNegativeButton("Back",(d,w)->showPinDialog()).show();
  }
  private String ensureRecovery(){
    String raw=prefs.getString("recovery_plain","");
    if(raw.isEmpty()){raw="MW-"+UUID.randomUUID().toString().replace("-","").substring(0,4).toUpperCase()+"-"+UUID.randomUUID().toString().replace("-","").substring(0,4).toUpperCase()+"-"+UUID.randomUUID().toString().replace("-","").substring(0,4).toUpperCase();prefs.edit().putString("recovery_plain",raw).putString("recovery_hash",sha(raw)).apply();}
    return raw;
  }
  private void writeAuto(String text){
    try{String n="MyWheels_"+new SimpleDateFormat("yyyy-MM-dd_HHmm",Locale.US).format(new Date())+".backup";try(FileOutputStream o=new FileOutputStream(new File(backupDir,n))){o.write(text.getBytes(StandardCharsets.UTF_8));}prune();prefs.edit().putLong("last_backup",System.currentTimeMillis()).apply();js("toast('Automatic backup saved')");}catch(Exception e){js("toast('Automatic backup failed')");}
  }
  private void writePermanent(String text){
    try{String n="MyWheels_Weekly_"+new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date())+".backup";File f=new File(permanentDir,n);if(!f.exists()){try(FileOutputStream o=new FileOutputStream(f)){o.write(text.getBytes(StandardCharsets.UTF_8));}prefs.edit().putLong("last_permanent_backup",System.currentTimeMillis()).apply();js("toast('Permanent weekly backup saved')");}}catch(Exception e){js("toast('Permanent backup failed')");}
  }
  private void prune(){int days=prefs.getInt("retention",14);long cutoff=System.currentTimeMillis()-days*86400000L;File[] f=backupDir.listFiles();if(f!=null)for(File x:f)if(x.lastModified()<cutoff)x.delete();}
  private void maybeBackup(){String sch=prefs.getString("schedule","twice");long now=System.currentTimeMillis();if(!sch.equals("off")){long gap=sch.equals("daily")?86400000L:43200000L;if(now-prefs.getLong("last_backup",0)>=gap)js("if(window.AndroidBridge){AndroidBridge.createAutoBackup(JSON.stringify({...state,exportedAt:new Date().toISOString()}))}");}if(now-prefs.getLong("last_permanent_backup",0)>=604800000L)js("if(window.AndroidBridge){AndroidBridge.createPermanentBackup(JSON.stringify({...state,exportedAt:new Date().toISOString()}))}");}
  @Override protected void onResume(){super.onResume();if(backgroundAt>0&&isLockEnabled()){int m=prefs.getInt("lock_minutes",0);if(m==0||System.currentTimeMillis()-backgroundAt>=m*60000L){unlocked=false;showUnlock();}}maybeBackup();}
  @Override protected void onPause(){backgroundAt=System.currentTimeMillis();super.onPause();}
  public class AndroidBridge{
    @JavascriptInterface public void saveTextFile(String n,String m,String t){pendingBytes=t.getBytes(StandardCharsets.UTF_8);runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(m);i.putExtra(Intent.EXTRA_TITLE,n);saveLauncher.launch(i);});}
    @JavascriptInterface public String setAppPin(String pin,int mins){if(!pin.matches("\\d{4,8}"))return "Invalid PIN";prefs.edit().putString("pin_hash",sha(pin)).putInt("lock_minutes",mins).apply();unlocked=true;return "PIN enabled. Recovery code: "+ensureRecovery();}
    @JavascriptInterface public boolean isLockEnabled(){return MainActivity.this.isLockEnabled();}
    @JavascriptInterface public String getRecoveryCode(){return ensureRecovery();}
    @JavascriptInterface public void requestBiometric(){runOnUiThread(()->doBiometric());}
    @JavascriptInterface public void configureBackups(String schedule,int days){prefs.edit().putString("schedule",schedule).putInt("retention",days).apply();prune();}
    @JavascriptInterface public void createAutoBackup(String text){writeAuto(text);}
    @JavascriptInterface public void createPermanentBackup(String text){writePermanent(text);}
    @JavascriptInterface public String getBackupHistory(String kind){JSONArray a=new JSONArray();File dir="permanent".equals(kind)?permanentDir:backupDir;try{File[] f=dir.listFiles();if(f!=null){Arrays.sort(f,(x,y)->Long.compare(y.lastModified(),x.lastModified()));for(File x:f){JSONObject o=new JSONObject();o.put("name",x.getName());o.put("time",new SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.US).format(new Date(x.lastModified())));o.put("size",Math.max(1,x.length()/1024)+" KB");o.put("kind",kind);a.put(o);}}}catch(Exception e){}return a.toString();}
    @JavascriptInterface public void restoreBackup(String kind,String name){try{File dir="permanent".equals(kind)?permanentDir:backupDir;File f=new File(dir,name);String text=new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);String q=JSONObject.quote(text);js("try{const x=JSON.parse("+q+");if(confirm('Restore this backup? Current data will be replaced.')){state={cars:x.cars||[],ledger:x.ledger||[],clients:x.clients||[],meta:{...(x.meta||{}),version:10}};persist();toast('Backup restored')}}catch(e){alert('Backup is invalid')}");}catch(Exception e){js("alert('Could not restore backup')");}}
  }
  @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
