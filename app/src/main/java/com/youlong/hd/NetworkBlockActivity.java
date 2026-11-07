package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.net.VpnService;
import android.widget.*;
import java.util.*;
public final class NetworkBlockActivity extends Activity {
 private String pkg;private TextView status;
 @Override public void onCreate(Bundle state){setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);pkg=getIntent().getStringExtra("package");if(pkg!=null){if(!PkgGuard.isValid(pkg)||pkg.equals(getPackageName())){finish();return;}confirm();return;}
  NativeUi.Page page=new NativeUi.Page(this,"应用外传阻断","为指定应用划出网络边界。","通过本机 VPN 直接丢弃所选应用的 IPv4／IPv6 TCP、UDP 和其他 IP 数据包，不转发和解密上传内容。它不根据上传动作判断勒索软件。会替换其他 VPN 和域名过滤，同 UID 的应用可能受影响。VPN 停止后网络可能恢复，持续阻断需在系统设置始终开启及阻止非 VPN 连接，这也可能影响其他应用。");
  LinearLayout summary=page.section("隔离状态");status=new TextView(this);NativeUi.report(this,summary,status,false);
  LinearLayout choose=page.section("隔离名单");choose.addView(NativeUi.label(this,"只有你指定或自动防护命中的应用会被隔离。",13,0xFF526358));NativeUi.action(this,choose,"选择／修改应用",this::choose,false);
  LinearLayout controls=page.section("连接控制");NativeUi.action(this,controls,"启动网络隔离  ↗",this::consent,true);NativeUi.action(this,controls,"停止网络隔离",()->{stopService(new Intent(this,NetworkBlockService.class));status.setText("已请求停止隔离；保留已保存名单。停止后网络可以恢复。");},false);NativeUi.action(this,controls,"刷新隔离状态",this::refresh,false);page.show();refresh();
 }
 private void add(LinearLayout body,String text,Runnable action){Button b=new Button(this);b.setText(text);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFDDEEE1);bg.setCornerRadius(28*getResources().getDisplayMetrics().density);b.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,(int)(56*getResources().getDisplayMetrics().density));lp.bottomMargin=(int)(12*getResources().getDisplayMetrics().density);body.addView(b,lp);b.setOnClickListener(v->action.run());}
 private Set<String> selected(){return new HashSet<>(getSharedPreferences("network_block",0).getStringSet("packages",Collections.emptySet()));}
 private void refresh(){if(status==null)return;android.content.SharedPreferences p=getSharedPreferences("network_block",0);status.setText("状态："+(NetworkBlockService.running?"隔离通道运行中":"隔离通道未运行")+"\n已选应用："+selected().size()+"\n本次已丢弃："+NetworkBlockService.droppedPackets+" 包，"+NetworkBlockService.droppedBytes+" 字节\n"+p.getString("error",""));}
 private void choose(){
  List<ApplicationInfo> apps=new ArrayList<>(getPackageManager().getInstalledApplications(0));apps.removeIf(a->a.packageName.equals(getPackageName()));apps.sort(Comparator.comparing(a->a.packageName));String[] labels=new String[apps.size()];boolean[] checked=new boolean[apps.size()];Set<String> next=selected();final Set<String> original=new HashSet<>(next);
  for(int i=0;i<apps.size();i++){ApplicationInfo app=apps.get(i);labels[i]=getPackageManager().getApplicationLabel(app)+"\n"+app.packageName;checked[i]=next.contains(app.packageName);}
  new AlertDialog.Builder(this).setTitle("选择要丢弃全部流量的应用").setMultiChoiceItems(labels,checked,(d,i,on)->{if(on)next.add(apps.get(i).packageName);else next.remove(apps.get(i).packageName);}).setNegativeButton("取消",null).setPositiveButton("保存并停止旧通道",(d,w)->{synchronized(NetworkBlockService.class){Set<String> merged=selected();for(String item:original)if(!next.contains(item))merged.remove(item);merged.addAll(next);getSharedPreferences("network_block",0).edit().putStringSet("packages",merged).apply();}stopService(new Intent(this,NetworkBlockService.class));status.setText("已保存。点击启动隔离，以应用新名单。");}).show();
 }
 private void confirm(){new AlertDialog.Builder(this).setTitle("隔离应用网络").setMessage(pkg+"\n阻止目标应用经此通道的全部 IPv4／IPv6 TCP／UDP 流量，会替换当前 VPN。共享 UID 的应用也可能受影响。").setNegativeButton("取消",(d,w)->finish()).setNeutralButton("解除隔离",(d,w)->save(false)).setPositiveButton("阻止网络",(d,w)->consent()).setOnCancelListener(d->finish()).show();}
 private void consent(){if(pkg==null&&selected().isEmpty()){status.setText("请先选择至少一个隔离应用。");return;}Intent request=VpnService.prepare(this);if(request!=null)startActivityForResult(request,1);else enable();}
 private void enable(){if(pkg!=null){save(true);return;}start();}
 @Override protected void onActivityResult(int q,int result,Intent data){super.onActivityResult(q,result,data);if(q==1&&result==RESULT_OK)enable();else if(pkg!=null)finish();else status.setText("VPN 授权未通过，隔离未启动。");}
 private void start(){stopService(new Intent(this,SiteFilterService.class));Intent service=new Intent(this,NetworkBlockService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);if(status!=null)status.setText("正在建立隔离通道，稍后刷新查看实际状态。");}
 private void save(boolean block){synchronized(NetworkBlockService.class){Set<String> apps=selected();if(block)apps.add(pkg);else apps.remove(pkg);getSharedPreferences("network_block",0).edit().putStringSet("packages",apps).apply();}if(selected().isEmpty())stopService(new Intent(this,NetworkBlockService.class));else start();finish();}
}
