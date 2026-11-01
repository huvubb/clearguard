package com.youlong.hd;
import android.net.VpnService;
import android.os.*;
import android.content.Intent;
import android.app.*;
import java.io.FileInputStream;
import java.util.*;
public final class NetworkBlockService extends VpnService {
 private ParcelFileDescriptor tunnel;
 @Override public int onStartCommand(Intent intent,int flags,int id) {
  if(tunnel!=null)try{tunnel.close();}catch(Exception ignored){}
  Set<String> apps=new HashSet<>(getSharedPreferences("network_block",0).getStringSet("packages",Collections.emptySet()));
  if(apps.isEmpty()){stopSelf();return START_NOT_STICKY;}
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("network_block","应用网络隔离",NotificationManager.IMPORTANCE_LOW));
  Notification.Builder nb=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"network_block"):new Notification.Builder(this);
  startForeground(2204,nb.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("澄盾网络隔离正在运行").setContentText("已阻止隔离应用的 IPv4 / IPv6 流量").setOngoing(true).build());
  try{Builder b=new Builder().setSession("ClearGuard quarantine").addAddress("10.93.0.1",32).addAddress("fd93::1",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("10.93.0.2");int count=0;
   for(String p:apps)try{if(PkgGuard.isValid(p)&&!p.equals(getPackageName())){b.addAllowedApplication(p);count++;}}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
   if(count==0){stopSelf();return START_NOT_STICKY;}
   final ParcelFileDescriptor fd=b.establish();if(fd==null){stopSelf();return START_NOT_STICKY;}tunnel=fd;
   new Thread(()->{try(FileInputStream in=new FileInputStream(fd.getFileDescriptor())){byte[] packet=new byte[32768];while(in.read(packet)>=0){} }catch(Exception ignored){}},"network-deny").start();
  }catch(Exception e){CrashLogger.event("网络隔离启动失败",e);stopSelf();}return START_NOT_STICKY;
 }
 @Override public void onDestroy(){if(tunnel!=null)try{tunnel.close();}catch(Exception ignored){}super.onDestroy();}
}
