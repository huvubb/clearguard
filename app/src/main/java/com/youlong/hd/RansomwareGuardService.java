package com.youlong.hd;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Explicit opt-in background inspection. Quarantine is local and reversible. */
public final class RansomwareGuardService extends Service {
 public static volatile boolean running;
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
 private final Map<String,Long> checked=new HashMap<>();private int inspected;private boolean registered;private ScheduledFuture<?> scheduled;
 private final BroadcastReceiver installed=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){if(intent.getData()!=null&&!worker.isShutdown()){String pkg=intent.getData().getSchemeSpecificPart();worker.execute(()->inspect(pkg));}}};
 @Override public void onCreate(){super.onCreate();NotificationManager nm=getSystemService(NotificationManager.class);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("ransom_guard","勒索风险主动检查",NotificationManager.IMPORTANCE_LOW));
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REPLACED);filter.addDataScheme("package");if(Build.VERSION.SDK_INT>=33)registerReceiver(installed,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(installed,filter);registered=true;
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"ransom_guard"):new Notification.Builder(this);
  startForeground(2210,b.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("澄盾 · 勒索风险主动检查").setContentText("监测应用安装、更新；命中特征组合后隔离网络").setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,RansomwareGuardActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).build());
  if(!getSharedPreferences("ransom_guard",0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}
  if(scheduled==null)scheduled=worker.scheduleWithFixedDelay(()->{try{for(ApplicationInfo app:getPackageManager().getInstalledApplications(0)){if(Thread.currentThread().isInterrupted())return;inspect(app.packageName);}}catch(Exception error){record("检查未完成："+error.getMessage());}},0,3,TimeUnit.MINUTES);
  running=true;getSharedPreferences("ransom_guard",0).edit().putString("progress","自动检查已启动，正在检查应用变化").apply();return START_STICKY;
 }
 private void inspect(String pkg){
  if(!getSharedPreferences("ransom_guard",0).getBoolean("enabled",false)||!PkgGuard.isValid(pkg)||pkg.equals(getPackageName()))return;
  try{
   PackageInfo info=getPackageManager().getPackageInfo(pkg,0);ApplicationInfo app=info.applicationInfo;
   if(app==null||(app.flags&ApplicationInfo.FLAG_SYSTEM)!=0)return;
   if(Objects.equals(checked.get(pkg),info.lastUpdateTime))return;
   List<File> paths=new ArrayList<>();paths.add(new File(app.sourceDir));if(app.splitSourceDirs!=null)for(String path:app.splitSourceDirs)paths.add(new File(path));
   boolean known=false;String knownEvidence="";VirusDefinitions definitions=VirusDefinitions.get(this);for(File apk:paths)try(InputStream input=new FileInputStream(apk)){VirusDefinitions.Match match=definitions.inspect(input,()->Thread.currentThread().isInterrupted(),bytes->{});if(match.confirmed){known=true;knownEvidence=match.evidence;break;}}
   RansomwareDetector.Verdict verdict=known?new RansomwareDetector.Verdict(true,false,"精确恶意哈希命中："+knownEvidence):RansomwareDetector.scan(paths.toArray(new File[0]));
   if(getPackageManager().getPackageInfo(pkg,0).lastUpdateTime!=info.lastUpdateTime)return;
   checked.put(pkg,info.lastUpdateTime);getSharedPreferences("ransom_guard",0).edit().putString("progress","本轮已检查 "+(++inspected)+" 个应用；最近："+pkg+"\n完成时间："+new Date()).apply();
   if(!getSharedPreferences("ransom_guard",0).getBoolean("enabled",false))return;
   if(!verdict.suspect){if(verdict.truncated)record(pkg+"：检查达到上限，未做完整判断。");return;}
   synchronized(NetworkBlockService.class){Set<String> blocked=new HashSet<>(getSharedPreferences("network_block",0).getStringSet("packages",Collections.emptySet()));blocked.add(pkg);if(!getSharedPreferences("network_block",0).edit().putStringSet("packages",blocked).commit())throw new IOException("无法保存隔离名单");}
   record(pkg+"\n"+verdict.reason+"\n已加入隔离名单；是否阻断成功以隔离通道状态为准。");
   if(VpnService.prepare(this)!=null){record(pkg+"：发现疑似勒索风险，但 VPN 授权已撤销，未能自动断网。请打开澄盾重新授权。");return;}
   stopService(new Intent(this,SiteFilterService.class));Intent service=new Intent(this,NetworkBlockService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
  }catch(Exception error){record(pkg+"：检查或隔离失败："+error.getMessage());}
 }
 private void record(String text){getSharedPreferences("ransom_guard",0).edit().putString("last_report",new Date()+"\n"+text).apply();}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onDestroy(){running=false;if(registered)unregisterReceiver(installed);worker.shutdownNow();stopForeground(true);super.onDestroy();}
}
