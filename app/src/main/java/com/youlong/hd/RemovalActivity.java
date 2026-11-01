package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.widget.*;

public final class RemovalActivity extends Activity {
 private String pkg; private TextView status; private Button remove;
 private static boolean safe(Context c,String p) {return PkgGuard.isValid(p) && !p.equals(c.getPackageName()) && !java.util.Arrays.stream(c.getSharedPreferences("shield_prefs",0).getString("whitelist_pkgs","").split(",")).anyMatch(x->x.trim().equals(p));}
 private static int boot(Context c){return android.provider.Settings.Global.getInt(c.getContentResolver(),"boot_count",0);}
 @Override public void onCreate(Bundle b){super.onCreate(b);pkg=getIntent().getStringExtra("package");if(!safe(this,pkg)){finish();return;}
 LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,64,32,32);root.setBackgroundColor(0xFFF5F7F2);
 TextView heading=new TextView(this);heading.setText("澄盾 · 风险处置");heading.setTextSize(26);heading.setTextColor(0xFF172D25);root.addView(heading);
 status=new TextView(this);status.setText("待处理应用："+pkg+"\n先尝试已授权 shell 卸载；失败会显示原因并提供系统卸载。重启重试不会强制重启设备。");status.setTextSize(15);status.setPadding(0,32,0,32);root.addView(status);
 remove=new Button(this);remove.setText("先尝试 shell 卸载");root.addView(remove);remove.setOnClickListener(v->{remove.setEnabled(false);status.setText("正在卸载…");new Thread(()->{String result=uninstall(this,pkg);runOnUiThread(()->{if(!isFinishing()){status.setText(result);remove.setEnabled(true);}});},"removal").start();});
 Button stop=new Button(this);stop.setText("撤销特权并系统强停");stop.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("停止此应用").setMessage("先撤销通过澄盾获得的特权，再由系统强停应用及其归属进程。共享 UID 应用可能一起受影响；不会修改其他进程的 Linux UID。") .setNegativeButton("取消",null).setPositiveButton("确认强停",(d,w)->new Thread(()->{try{int uid=getPackageManager().getApplicationInfo(pkg,0).uid;roro.stellar.Stellar.INSTANCE.updateFlagForUid(uid,"stellar",2);roro.stellar.Stellar.INSTANCE.updateFlagForUid(uid,"shizuku",4);String out=StellarUtils.runCommand("am force-stop '"+pkg+"'",10000);runOnUiThread(()->status.setText("已请求撤权与系统强停："+out));}catch(Exception e){runOnUiThread(()->status.setText("处理失败："+e.getMessage()));}},"revoke-stop").start()).show());root.addView(stop);
 Button system=new Button(this);system.setText("打开系统卸载确认");system.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg))));root.addView(system);
 Button later=new Button(this);later.setText("下次重启、特权就绪后重试");later.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("确认保存待卸载任务").setMessage("仅在下一次启动、服务就绪且应用身份未改变时重试一次；不会杀死系统服务。") .setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{try{PackageInfo pi=getPackageManager().getPackageInfo(pkg,PackageManager.GET_SIGNATURES);String identity=pi.signatures[0].toCharsString();getSharedPreferences("pending_removal",0).edit().putString(pkg,boot(this)+":"+identity).apply();status.setText("任务已保存。你可以自行重启，之后激活特权服务以重试。");}catch(Exception e){status.setText("无法保存："+e.getMessage());}}).show());root.addView(later);
 Button close=new Button(this);close.setText("取消待处理并返回");close.setOnClickListener(v->{getSharedPreferences("pending_removal",0).edit().remove(pkg).apply();finish();});root.addView(close);setContentView(root);}
 private static String uninstall(Context c,String p){if(!safe(c,p))return "已取消：目标受保护或已加入白名单";if(!StellarUtils.hasStellarPermission())return "shell 未授权，请先激活特权服务，或选择系统卸载。";String out=StellarUtils.runCommand("pm uninstall '"+p+"'",15000);try{c.getPackageManager().getApplicationInfo(p,0);return "卸载未完成："+out+"\n可以选择系统卸载或重启后重试。";}catch(PackageManager.NameNotFoundException e){return "卸载成功："+p;}}
 public static synchronized void retryPending(Context c){if(!StellarUtils.hasStellarPermission())return;new Thread(()->{for(java.util.Map.Entry<String,?> e:c.getSharedPreferences("pending_removal",0).getAll().entrySet()){try{String[] record=String.valueOf(e.getValue()).split(":",2);if(boot(c)<=Integer.parseInt(record[0]))continue;c.getSharedPreferences("pending_removal",0).edit().remove(e.getKey()).apply();PackageInfo pi=c.getPackageManager().getPackageInfo(e.getKey(),PackageManager.GET_SIGNATURES);if(record.length==2&&record[1].equals(pi.signatures[0].toCharsString()))CrashLogger.event("[待卸载] "+uninstall(c,e.getKey()));}catch(Exception error){CrashLogger.event("待卸载任务失败",error);}}},"pending-removal").start();}
}
