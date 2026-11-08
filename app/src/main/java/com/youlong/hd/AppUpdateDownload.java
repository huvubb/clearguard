package com.youlong.hd;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.os.*;
import java.io.*;
import java.security.*;
import java.util.*;
/** System download tasks survive the update page and app process. */
final class AppUpdateDownload {
 static final int READY_NOTIFICATION=2501;
 static android.app.DownloadManager manager(Context c){return (android.app.DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);}
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("app_update",0);}
 static long id(Context c){return prefs(c).getLong("id",-1);}
 static final class State{final int status,reason;final long bytes,total;State(int s,int r,long b,long t){status=s;reason=r;bytes=b;total=t;}}
 static State state(Context c){long id=id(c);if(id<0)return null;try(Cursor cursor=manager(c).query(new android.app.DownloadManager.Query().setFilterById(id))){if(cursor==null||!cursor.moveToFirst())return null;return new State(cursor.getInt(cursor.getColumnIndexOrThrow("status")),cursor.getInt(cursor.getColumnIndexOrThrow("reason")),cursor.getLong(cursor.getColumnIndexOrThrow("bytes_so_far")),cursor.getLong(cursor.getColumnIndexOrThrow("total_size")));}}
 static synchronized void start(Context c,UpdateRelease release)throws IOException{
  State previous=state(c);if(previous!=null&&(previous.status==1||previous.status==2||previous.status==4))throw new IOException("已有后台下载，请等待或取消后重试");
  long old=id(c);if(old>=0)manager(c).remove(old);
  android.app.DownloadManager.Request request=new android.app.DownloadManager.Request(android.net.Uri.parse(release.url)).setTitle("澄盾 "+release.name+" 更新").setDescription("后台下载，完成后校验并打开系统安装界面").setMimeType("application/vnd.android.package-archive").setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE).setDestinationInExternalFilesDir(c,Environment.DIRECTORY_DOWNLOADS,"update-"+release.version+"-"+UUID.randomUUID()+".apk");
  long task=manager(c).enqueue(request);
  if(!prefs(c).edit().putLong("id",task).putString("url",release.url).putString("hash",release.hash).putString("name",release.name).putLong("version",release.version).putLong("size",release.size).commit()){manager(c).remove(task);throw new IOException("无法保存后台下载任务");}
  c.getSystemService(NotificationManager.class).cancel(READY_NOTIFICATION);
 }
 static synchronized void cancel(Context c){long task=id(c);if(task>=0)manager(c).remove(task);prefs(c).edit().clear().commit();c.getSystemService(NotificationManager.class).cancel(READY_NOTIFICATION);}
 static synchronized File verify(Context c,long expectedId)throws Exception{
  if(id(c)!=expectedId)throw new IOException("下载任务已变化");State state=state(c);if(state==null||state.status!=android.app.DownloadManager.STATUS_SUCCESSFUL)throw new IOException("下载尚未完成");
  SharedPreferences p=prefs(c);PackageManager pm=c.getPackageManager();long current=version(pm.getPackageInfo(c.getPackageName(),0));
  UpdateRelease release=new UpdateRelease(p.getString("url",""),p.getString("hash",""),p.getString("name",""),p.getLong("version",0),p.getLong("size",0),current);
  File dir=new File(c.getFilesDir(),"updates");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建安装目录");File apk=new File(dir,"candidate.apk");
  try{
   try(ParcelFileDescriptor fd=manager(c).openDownloadedFile(expectedId);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){release.copyVerified(in,apk);}
   int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
   PackageInfo archive=pm.getPackageArchiveInfo(apk.getPath(),flags),installed=pm.getPackageInfo(c.getPackageName(),flags);
   if(archive==null||!c.getPackageName().equals(archive.packageName)||version(archive)!=release.version||!signers(archive).equals(signers(installed)))throw new IOException("安装包包名、版本或发布签名不匹配；Debug 包不能覆盖正式版");
   File ready=new File(dir,"update.apk");if(!apk.renameTo(ready))throw new IOException("无法保存已校验安装包");return ready;
  }catch(Exception error){apk.delete();throw error;}
 }
 private static long version(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
 private static Set<String> signers(PackageInfo info)throws Exception{
  android.content.pm.Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
  if(signatures==null||signatures.length==0)throw new IOException("安装包缺少签名");Set<String> result=new HashSet<>();for(android.content.pm.Signature signature:signatures)result.add(android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()),android.util.Base64.NO_WRAP));return result;
 }
 static void notifyReady(Context c){
  State state=state(c);if(state==null||state.status!=android.app.DownloadManager.STATUS_SUCCESSFUL)return;
  NotificationManager nm=c.getSystemService(NotificationManager.class);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("app_update","应用更新",NotificationManager.IMPORTANCE_DEFAULT));
  PendingIntent tap=PendingIntent.getActivity(c,READY_NOTIFICATION,new Intent(c,SupportActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,"app_update"):new Notification.Builder(c);
  try{nm.notify(READY_NOTIFICATION,b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("澄盾更新已下载").setContentText("点击校验并安装新版本").setContentIntent(tap).setAutoCancel(true).build());}catch(SecurityException denied){/* The update page still resumes the saved task. */}
 }
}
