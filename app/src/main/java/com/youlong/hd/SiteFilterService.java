package com.youlong.hd;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

public final class SiteFilterService extends VpnService {
    public static volatile boolean running;
    private ParcelFileDescriptor tunnel;
    private final ExecutorService startup=Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),new ThreadPoolExecutor.DiscardPolicy());
    private volatile boolean stopped;
    private boolean started;
    private final java.util.concurrent.atomic.AtomicInteger failures=new java.util.concurrent.atomic.AtomicInteger();
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(started)return START_NOT_STICKY;started=true;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("site_filter","敏感网站防护",NotificationManager.IMPORTANCE_LOW));
        startForeground(2205,notification("正在加载网站分类名单…"));
        startup.execute(() -> {
            try {
                Map<String,String> domains=new HashMap<>();SharedPreferences prefs=getSharedPreferences("site_filter",0);
                for(String category:new String[]{"threat","adult","gambling"}) if(prefs.getBoolean(category,true)) {
                    try(BufferedReader input=new BufferedReader(new InputStreamReader(new GZIPInputStream(getAssets().open("site-lists/"+category+".txt.gz")),java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;int count=0;while((line=input.readLine())!=null) {
                            if(stopped)return;
                            try{domains.put(SiteDomainPolicy.normalize(line),category);count++;}catch(IllegalArgumentException ignored){}
                        }
                        if(count<1000)throw new IOException("分类名单不完整："+category);
                    }
                }
                SiteDomainPolicy policy=new SiteDomainPolicy(domains,prefs.getString("allow",""),prefs.getString("deny",""));
                ConnectivityManager connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                Network underlying=physicalNetwork(connectivity);
                if(underlying==null)throw new IOException("没有可用的基础网络");
                Builder builder=new Builder().setSession("ClearGuard 网站域名防护").addAddress("10.94.0.1",32)
                        .addDnsServer("10.94.0.2").addRoute("10.94.0.2",32).setMtu(1500);
                builder.addDisallowedApplication(getPackageName());
                ParcelFileDescriptor fd=builder.establish();if(fd==null)throw new IOException("VPN 授权不可用");
                if(stopped){fd.close();return;}tunnel=fd;running=true;
                post(manager,2205,notification("网站域名防护中 · "+domains.size()+" 条分类规则"));
                try(FileInputStream input=new FileInputStream(fd.getFileDescriptor());FileOutputStream output=new FileOutputStream(fd.getFileDescriptor())) {
                    byte[] buffer=new byte[65535];int size;
                    while(!stopped && (size=input.read(buffer))>=0) {
                        final DnsPacket packet;
                        try{packet=new DnsPacket(buffer,size);}catch(IOException malformed){continue;}
                        workers.execute(() -> {
                            try {
                                String category=policy.blockedCategory(packet.domain);byte[] response;
                                if(category!=null) {
                                    response=packet.blocked();prefs.edit().putString("last_blocked",packet.domain).putString("last_category",category).apply();
                                    post(manager,2205,notification("已拦截："+packet.domain+" · "+category));
                                } else {
                                    Network network=physicalNetwork(connectivity);if(network==null)throw new IOException("基础网络不可用");
                                    HttpURLConnection connection=(HttpURLConnection)network.openConnection(new URL("https://security.cloudflare-dns.com/dns-query"));
                                    try {
                                        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);
                                        connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(packet.query.length);
                                        connection.setRequestProperty("Content-Type","application/dns-message");connection.setRequestProperty("Accept","application/dns-message");
                                        try(OutputStream request=connection.getOutputStream()){request.write(packet.query);}
                                        if(connection.getResponseCode()!=200)throw new IOException("DNS 上游返回 HTTP "+connection.getResponseCode());
                                        try(InputStream body=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                                            byte[] part=new byte[4096];int read;while((read=body.read(part))!=-1){if(bytes.size()+read>65507)throw new IOException("DNS 响应过大");bytes.write(part,0,read);}
                                            response=packet.reply(bytes.toByteArray());
                                        }
                                    } finally {connection.disconnect();}
                                }
                                failures.set(0);synchronized(output){if(!stopped)output.write(response);}
                            }catch(Exception error){if(!stopped && failures.incrementAndGet()>=3){prefs.edit().putString("error","DNS 上游不可达，防护已停止："+error.getMessage()).apply();post(manager,2206,notification("网站防护因网络错误停止，请检查后重新开启"));stopSelf();}}
                        });
                    }
                }
            } catch(Throwable error) {
                if(!stopped)getSharedPreferences("site_filter",0).edit().putString("error","防护启动失败："+error.getMessage()).apply();
            } finally {running=false;stopSelf();}
        });
        return START_NOT_STICKY;
    }
    private static Network physicalNetwork(ConnectivityManager connectivity) {
        Network fallback=null;
        for(Network candidate:connectivity.getAllNetworks()) {
            NetworkCapabilities cap=connectivity.getNetworkCapabilities(candidate);
            if(cap!=null && !cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                if(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return candidate;
                if(fallback==null || cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))fallback=candidate;
            }
        }
        return fallback;
    }
    private void post(NotificationManager manager,int id,Notification notification) {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        try{manager.notify(id,notification);}catch(SecurityException denied){ }
    }
    private Notification notification(String text) {
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"site_filter"):new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("澄盾 · 敏感网站防护").setContentText(text)
                .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,SiteFilterActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();
    }
    @Override public void onRevoke(){stopSelf();}
    @Override public void onDestroy(){stopped=true;running=false;if(tunnel!=null)try{tunnel.close();}catch(IOException ignored){}workers.shutdownNow();startup.shutdownNow();super.onDestroy();}
}
