package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;
import java.net.*;
import java.io.*;
import org.json.JSONObject;
public final class SupportActivity extends Activity {
 private android.widget.TextView updateStatus;
 private final Handler updateHandler=new Handler(Looper.getMainLooper());
 private final java.util.concurrent.ExecutorService updateWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
 private boolean resumed,verifying,pendingInstall;private long attemptedId=-1,verifiedId=-1;
 private final Runnable updateTick=new Runnable(){public void run(){if(!resumed||updateStatus==null)return;pollDownload();updateHandler.postDelayed(this,1000);}};
 private static final String BASE="https://huvubb.abrdns.com";
 @Override public void onCreate(Bundle state){setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);if(state!=null){attemptedId=state.getLong("updateAttempt",-1);verifiedId=state.getLong("verifiedUpdate",-1);}String pkg=getIntent().getStringExtra("sample");if(pkg==null){NativeUi.Page page=new NativeUi.Page(this,"检查更新","从项目网站获取版本信息。","通过 HTTPS 访问 huvubb.abrdns.com 查询版本。确认更新后在后台下载，通知栏显示进度。下载完成后校验哈希、包名、版本和当前安装证书，再打开系统安装界面。应用在后台时请点击下载完成通知继续，不静默安装。正式签名与 Debug 签名不同，不可相互覆盖安装。");android.widget.LinearLayout version=page.section("当前版本");version.addView(NativeUi.label(this,"ClearGuard "+BuildConfig.VERSION_NAME,22,0xFF173C30));version.addView(NativeUi.label(this,"澄盾 · 本机安全工具",13,0xFF526358));android.widget.LinearLayout status=page.section("更新状态");updateStatus=new android.widget.TextView(this);updateStatus.setText("点击下方按钮检查是否有新版本。");NativeUi.report(this,status,updateStatus,false);NativeUi.action(this,status,"检查新版本  ↗",()->{updateStatus.setText("正在连接项目网站…");new Thread(this::checkUpdate,"update-check").start();},true);NativeUi.action(this,status,"安装已下载更新／重试校验",()->verifyDownload(true),false);NativeUi.action(this,status,"取消后台下载",()->{if(verifying){done("正在校验，请稍后再取消。");return;}AppUpdateDownload.cancel(this);attemptedId=-1;done("已取消下载。");},false);page.show();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},96);return;}if(!PkgGuard.isValid(pkg)){finish();return;}
 new AlertDialog.Builder(this).setTitle("是否发送此 APK 样本？").setMessage(pkg+"\n仅发送该应用的安装 APK 到 huvubb.abrdns.com，不收集运行数据或自动附带日志。APK 本身也可能含私密信息。发送完全自愿，样本存放在非公开隔离目录。") .setNegativeButton("不发送",(d,w)->finish()).setPositiveButton("确认发送",(d,w)->new Thread(()->upload(pkg),"sample-upload").start()).setOnCancelListener(d->finish()).show();}
 private void done(String text){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(updateStatus!=null)updateStatus.setText(text);else new AlertDialog.Builder(this).setTitle("澄盾").setMessage(text).setPositiveButton("知道了",(d,w)->finish()).show();});}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putLong("updateAttempt",verifying?-1:attemptedId);state.putLong("verifiedUpdate",verifiedId);}
 @Override protected void onResume(){super.onResume();resumed=true;if(pendingInstall)installVerified();updateHandler.post(updateTick);}
 @Override protected void onPause(){resumed=false;updateHandler.removeCallbacks(updateTick);super.onPause();}
 @Override protected void onDestroy(){updateHandler.removeCallbacksAndMessages(null);updateWorker.shutdownNow();super.onDestroy();}
 private void pollDownload(){
  try{if(AppUpdateDownload.prefs(this).getLong("version",Long.MAX_VALUE)<=BuildConfig.VERSION_CODE){AppUpdateDownload.cancel(this);return;}
   AppUpdateDownload.State state=AppUpdateDownload.state(this);if(state==null)return;
   if(state.status==android.app.DownloadManager.STATUS_SUCCESSFUL){if(!verifying&&attemptedId!=AppUpdateDownload.id(this))verifyDownload(false);return;}
   if(verifying)return;
   if(state.status==android.app.DownloadManager.STATUS_FAILED){updateStatus.setText("下载失败："+downloadReason(state.reason)+"。请检查网络或空间后重新检查更新。");return;}
   String progress=state.total>0?Math.min(100,state.bytes*100/state.total)+"%":"大小待确认";
   updateStatus.setText((state.status==android.app.DownloadManager.STATUS_PAUSED?"下载暂停，系统会重试："+downloadReason(state.reason):state.status==android.app.DownloadManager.STATUS_PENDING?"等待系统开始下载":"后台下载中")+"\n进度："+progress+"\n已下载："+android.text.format.Formatter.formatFileSize(this,state.bytes)+"\n可以离开本页；完成后校验并安装。");
  }catch(Exception error){done("无法读取下载状态："+error.getMessage());}
 }
 private String downloadReason(int reason){switch(reason){case 1:return "正在重试";case 2:return "等待网络";case 3:return "等待 Wi-Fi";case 1006:return "存储空间不足";case 1007:return "存储不可用";case 1008:return "无法继续下载";case 1009:return "目标文件已存在";case 1010:return "下载被系统阻止";default:return "系统状态 "+reason;}}
 private void verifyDownload(boolean manual){if(verifying||updateStatus==null)return;long id=AppUpdateDownload.id(this);if(id<0){if(manual)done("暂无更新下载，请先检查更新。");return;}attemptedId=id;verifying=true;done("下载完成，正在校验安装包…");updateWorker.execute(()->{try{AppUpdateDownload.verify(this,id);runOnUiThread(()->{verifying=false;if(isDestroyed())return;verifiedId=id;pendingInstall=true;if(resumed)installVerified();});}catch(Exception error){runOnUiThread(()->{verifying=false;done("未能安装更新："+error.getMessage()+"\n可点击重试校验；文件损坏请取消并重新下载。");});}});}
 private void installVerified(){pendingInstall=false;if(!resumed||isDestroyed()||verifiedId!=AppUpdateDownload.id(this))return;
  try{if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){
    done("请在系统设置中允许澄盾安装应用，返回后继续安装。");startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),97);return;
   }
   Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".fileprovider",new File(getFilesDir(),"updates/update.apk"));
   startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));getSystemService(NotificationManager.class).cancel(AppUpdateDownload.READY_NOTIFICATION);done("已打开系统安装界面。若取消安装，可点击“安装已下载更新”重试。");
  }catch(Exception error){done("无法打开安装界面："+error.getMessage());}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==97){if(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls()){pendingInstall=true;if(resumed)installVerified();}else done("未授予安装权限；安装包已保留，可授权后重试。");}}
 private void checkUpdate(){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(BASE+"/downloads/clearguard-update.json").openConnection();c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(10000);if(c.getResponseCode()!=200)throw new IOException("更新服务暂不可用");ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(b.size()+n>65536)throw new IOException("更新信息过大");b.write(block,0,n);}}JSONObject o=new JSONObject(b.toString("UTF-8"));if(o.getLong("versionCode")<=BuildConfig.VERSION_CODE){done("当前已是最新版本。");return;}
  UpdateRelease release=new UpdateRelease(o.getString("url"),o.getString("sha256"),o.getString("versionName"),o.getLong("versionCode"),o.getLong("size"),BuildConfig.VERSION_CODE);
  runOnUiThread(()->{if(isDestroyed()||isFinishing()||!resumed)return;new AlertDialog.Builder(this).setTitle("发现新版本 "+release.name).setMessage(o.optString("notes")+"\n\n下载大小："+android.text.format.Formatter.formatFileSize(this,release.size)+"\n后台下载完成后校验并启动系统安装。").setNegativeButton("稍后",null).setPositiveButton("后台下载",(d,w)->{try{if(verifying)throw new IOException("正在校验，请稍后重试");AppUpdateDownload.start(this,release);attemptedId=-1;pollDownload();}catch(Exception error){done("无法开始下载："+error.getMessage());}}).show();});
 }catch(Exception e){done("检查更新失败："+e.getMessage());}finally{if(c!=null)c.disconnect();}}
 private void upload(String pkg){HttpURLConnection c=null;try{File apk=new File(getPackageManager().getApplicationInfo(pkg,0).sourceDir);long size=apk.length();if(size<=0||size>30L*1024*1024)throw new IOException("仅支持 30 MB 以内的基础 APK 样本");c=(HttpURLConnection)new URL(BASE+"/api/clearguard/sample").openConnection();c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setFixedLengthStreamingMode(size);c.setRequestProperty("Content-Type","application/vnd.android.package-archive");try(InputStream in=new FileInputStream(apk);OutputStream out=c.getOutputStream()){byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}int status=c.getResponseCode();if(status!=200&&status!=201)throw new IOException("服务器返回 "+status);done("样本已发送。感谢你的自愿反馈。");}catch(Exception e){done("发送失败："+e.getMessage());}finally{if(c!=null)c.disconnect();}}
}
