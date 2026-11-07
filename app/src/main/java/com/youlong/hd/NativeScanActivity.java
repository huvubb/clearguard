package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Native reports. Structured legacy data never appears in the interface. */
public final class NativeScanActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView output;
 private EditText target;
 private volatile boolean cancelled,busy;
 @Override public void onCreate(Bundle state){
  setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);
  NativeUi.Page page=new NativeUi.Page(this,"安全检查","风险与依据，一起看清楚。","应用检查使用当前本地特征；未命中不代表安全，内容筛查可能达到时间上限。网络诊断仅检查你指定主机的常见 TCP 端口，不发送利用载荷。可连接端口不等于存在漏洞。");
  LinearLayout apps=page.section("应用安全");apps.addView(NativeUi.label(this,"检查已安装应用，查看检测依据与覆盖范围。",13,0xFF526358));NativeUi.action(this,apps,"开始应用检查  ↗",()->run(this::scan),true);NativeUi.action(this,apps,"选择文件扫描",()->{if(!busy)startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),83);},false);NativeUi.action(this,apps,"停止当前扫描",()->cancelled=true,false);
  LinearLayout network=page.section("网络诊断");target=new EditText(this);target.setSingleLine();target.setHint("单个主机名或 IP；留空检查本机网络");NativeUi.field(this,network,"诊断目标",target);NativeUi.action(this,network,"检查网络连接",()->{String host=target.getText().toString().trim();run(()->network(host));},false);
  LinearLayout result=page.section("检查报告");output=new TextView(this);output.setText("等待开始检查。\n结果会以可读报告显示在这里。");NativeUi.report(this,result,output,false);NativeUi.action(this,result,"复制报告",()->((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("澄盾报告",output.getText())),false);page.show();
 }
 private void action(LinearLayout parent,String text,Runnable work){
  Button button=new Button(this);button.setText(text);GradientDrawable shape=new GradientDrawable();shape.setColor(0xFFDCECDF);shape.setCornerRadius(56);button.setBackground(shape);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,120);lp.bottomMargin=16;parent.addView(button,lp);button.setOnClickListener(v->work.run());
 }
 private interface Task{String execute()throws Exception;}
 private void run(Task task){if(busy)return;busy=true;cancelled=false;output.setText("正在加载病毒库…");worker.execute(()->{String report;try{report=task.execute();}catch(Exception error){report="检查未完成\n原因："+error.getMessage();}finally{busy=false;}show(report);});}
 private void show(String text){runOnUiThread(()->{if(!isDestroyed())output.setText(text);});}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==83&&result==RESULT_OK&&data!=null&&data.getData()!=null){android.net.Uri uri=data.getData();run(()->{VirusDefinitions definitions=VirusDefinitions.get(this);try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取所选文件");VirusDefinitions.Match match=definitions.inspect(in,()->cancelled,bytes->show("正在扫描所选文件\n已读取："+android.text.format.Formatter.formatFileSize(this,bytes)));return "文件扫描完成\n\n"+(match.confirmed?"已命中精确恶意哈希":match.suspect?"筛查命中，待复核":"未命中当前已知特征")+"\n"+match.evidence+"\n\nSHA-256："+match.hash+"\n\n未命中不代表安全，哈希检测不能发现所有新变种。";}});}}
 private String scan()throws Exception{
  checkedCount=0;VirusDefinitions definitions=VirusDefinitions.get(this);android.content.pm.PackageManager pm=getPackageManager();java.util.List<android.content.pm.ApplicationInfo> apps=pm.getInstalledApplications(0);int checked=0,failed=0,skipped=0,confirmed=0,suspect=0;StringBuilder hits=new StringBuilder();
  for(android.content.pm.ApplicationInfo app:apps){if(cancelled||Thread.currentThread().isInterrupted())break;if(app.packageName.equals(getPackageName())||((app.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)!=0&&(app.flags&android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)==0)){skipped++;continue;}
   final String name=String.valueOf(pm.getApplicationLabel(app));java.util.List<String> paths=new java.util.ArrayList<>();paths.add(app.sourceDir);if(app.splitSourceDirs!=null)java.util.Collections.addAll(paths,app.splitSourceDirs);
   try{boolean known=false,possible=false;StringBuilder evidence=new StringBuilder();for(String path:paths){try(InputStream in=new FileInputStream(path)){VirusDefinitions.Match match=definitions.inspect(in,()->cancelled,bytes->show("正在检查："+name+"\n已完成应用："+checkedCount+"\n当前文件已读取："+android.text.format.Formatter.formatFileSize(this,bytes)));if(match.confirmed||match.suspect){known|=match.confirmed;possible|=match.suspect;evidence.append(match.evidence).append("\nSHA-256：").append(match.hash).append("\n");}}}
    checked++;checkedCount=checked;if(known)confirmed++;else if(possible)suspect++;if(known||possible)hits.append(known?"精确指标命中：":"待复核：").append(name).append("\n包名：").append(app.packageName).append('\n').append(evidence).append('\n');
   }catch(InterruptedIOException stopped){cancelled=true;break;}catch(IOException unreadable){failed++;hits.append("未能完成：").append(name).append("\n原因：").append(unreadable.getMessage()).append("\n\n");}
  }
  checkedCount=0;return "应用扫描"+(cancelled?"已停止（结果不完整）":"完成")+"\n\n精确哈希指标："+definitions.exactCount()+" 条\nBloom 筛查项估计："+definitions.approximateCount()+" 条（非精确计数）\n已完整检查应用："+checked+"\n读取失败："+failed+"\n跳过系统／自身应用："+skipped+"\n精确指标命中："+confirmed+"\n待复核："+suspect+"\n\n"+hits+"\n覆盖当前用户可见第三方应用及其分包。没有 8 秒截断；可随时停止。未命中不代表安全，Bloom 命中不用于自动删除。";
 }
 private volatile int checkedCount;
 private String network(String host)throws Exception{
  ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  Network network=cm.getActiveNetwork();LinkProperties lp=network==null?null:cm.getLinkProperties(network);
  StringBuilder report=new StringBuilder("澄盾 · 网络诊断\n\n");
  report.append("网络：").append(network==null?"未连接":"已连接").append("\n");
  if(lp!=null){report.append("接口：").append(lp.getInterfaceName()).append("\nDNS：");for(InetAddress dns:lp.getDnsServers())report.append(dns.getHostAddress()).append(' ');report.append("\n");}
  if(host.isEmpty())return report+"\n未指定主机，仅检查本机网络。";
  if(!host.matches("[a-zA-Z0-9.:-]{1,253}"))throw new IOException("请输入单个 IP 或主机名");
  InetAddress address=InetAddress.getByName(host);report.append("目标：").append(address.getHostAddress()).append("\n");int open=0;
  for(int port:new int[]{21,22,23,53,80,139,443,445,3389,5555,8000,8080,8443}){
   if(cancelled||Thread.currentThread().isInterrupted())throw new IOException("用户停止");
   try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(address,port),700);open++;report.append("可连接 TCP ").append(port).append(port==5555?"：请核实 ADB 授权范围。\n":"：请确认是否为预期服务。\n");}catch(IOException ignored){}
  }
  return report+"\n可连接端口："+open+"\n开放端口不代表存在漏洞；连接失败可能是超时或过滤。";
 }
 @Override protected void onDestroy(){cancelled=true;worker.shutdownNow();super.onDestroy();}
}
