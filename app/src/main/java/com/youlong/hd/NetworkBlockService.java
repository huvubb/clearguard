package com.youlong.hd;
import android.net.VpnService;
import android.os.*;
import android.content.Intent;
import android.app.*;
import java.io.*;
import java.util.*;
/** Selected applications are routed to a local sink, with no upstream socket. */
public final class NetworkBlockService extends VpnService {
 public static volatile boolean running;
 public static volatile long droppedPackets,droppedBytes;
 private volatile ParcelFileDescriptor tunnel;
 @Override public int onStartCommand(Intent intent,int flags,int id){
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("network_block","应用网络隔离",NotificationManager.IMPORTANCE_LOW));
  startForeground(2204,notification("正在建立隔离通道…"));
  running=false;closeTunnel();droppedPackets=0;droppedBytes=0;
  getSharedPreferences("network_block",0).edit().putString("error","").apply();
  Set<String> apps=new HashSet<>(getSharedPreferences("network_block",0).getStringSet("packages",Collections.emptySet()));
  if(apps.isEmpty()){stopSelf();return START_NOT_STICKY;}
  try{
   Builder builder=new Builder().setBlocking(true).setSession("澄盾 · 丢弃隔离应用流量")
    .addAddress("10.93.0.1",32).addAddress("fd93::1",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("10.93.0.2").setMtu(1500);
   int count=0;
   for(String pkg:apps)try{if(PkgGuard.isValid(pkg)&&!pkg.equals(getPackageName())){builder.addAllowedApplication(pkg);count++;}}catch(android.content.pm.PackageManager.NameNotFoundException absent){}
   // Never establish an unscoped tunnel when every selected package is absent.
   if(count==0)throw new IOException("所选应用已不存在，未建立全局隔离通道");
   final ParcelFileDescriptor fd=builder.establish();if(fd==null)throw new IOException("VPN 授权不可用");tunnel=fd;running=true;
   startForeground(2204,notification("正在丢弃所选应用的 TCP／UDP 和其他 IP 流量"));
   new Thread(()->{
    try(FileInputStream input=new FileInputStream(fd.getFileDescriptor())){
     byte[] packet=new byte[65535];int bytes;
     while(tunnel==fd&&(bytes=input.read(packet))>=0){if(tunnel!=fd)break;if(bytes==0)continue;droppedPackets++;droppedBytes+=bytes;}
    }catch(IOException failure){if(tunnel==fd)getSharedPreferences("network_block",0).edit().putString("error","隔离通道已停止："+failure.getMessage()).apply();}
    finally{if(tunnel==fd){running=false;stopSelf();}}
   },"network-drop").start();
  }catch(Exception error){getSharedPreferences("network_block",0).edit().putString("error","隔离未启动："+error.getMessage()).apply();stopSelf();}
  return START_NOT_STICKY;
 }
 private Notification notification(String text){Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"network_block"):new Notification.Builder(this);return builder.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("澄盾 · 外传阻断").setContentText(text).setOngoing(true).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,NetworkBlockActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();}
 private void closeTunnel(){ParcelFileDescriptor fd=tunnel;tunnel=null;if(fd!=null)try{fd.close();}catch(IOException ignored){}}
 @Override public void onRevoke(){running=false;closeTunnel();stopSelf();}
 @Override public void onDestroy(){running=false;closeTunnel();stopForeground(true);super.onDestroy();}
}
