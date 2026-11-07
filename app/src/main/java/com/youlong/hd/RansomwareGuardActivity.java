package com.youlong.hd;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.widget.*;
public final class RansomwareGuardActivity extends Activity {
 private TextView report;
 @Override public void onCreate(Bundle state){setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);
  NativeUi.Page page=new NativeUi.Page(this,"勒索风险防护","监测安装变化，发现疑似风险后隔离。","检查第三方应用的安装与更新。仅当勒索提示、付款要求、加密或锁定 API 同时出现时，才标记疑似风险。也检查内置精确恶意哈希指标。静态规则可能误报或漏报，不能保证发现混淆、动态载荷与未知变种。每应用读取上限 64 MiB／30 秒，系统应用跳过。开启后常驻通知，取得 VPN 授权后命中会隔离应用全部 IP 流量，并替换其他 VPN。检查完成前或 VPN 不可用时仍可能有流量。停止检查不解除已保存隔离。");
  LinearLayout summary=page.section("当前状态");report=new TextView(this);NativeUi.report(this,summary,report,false);
  LinearLayout controls=page.section("自动检查");NativeUi.action(this,controls,"开启自动防护  ↗",()->new AlertDialog.Builder(this).setTitle("启用疑似勒索自动隔离？").setMessage("静态规则可能误报。命中后会切换到本机 VPN，丢弃目标应用的 TCP／UDP 流量。检查完成前可能已有流量；可随时停止并手动解除隔离。").setNegativeButton("取消",null).setPositiveButton("启用",(d,w)->{Intent request=VpnService.prepare(this);if(request!=null)startActivityForResult(request,92);else enable();}).show(),true);NativeUi.action(this,controls,"暂停自动检查",()->{getSharedPreferences("ransom_guard",0).edit().putBoolean("enabled",false).apply();stopService(new Intent(this,RansomwareGuardService.class));refresh();},false);
  LinearLayout more=page.section("隔离与记录");NativeUi.action(this,more,"管理已隔离应用",()->startActivity(new Intent(this,NetworkBlockActivity.class)),false);NativeUi.action(this,more,"刷新检测记录",this::refresh,false);page.show();refresh();
 }
 private void add(LinearLayout body,String title,Runnable action){Button button=new Button(this);button.setText(title);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xFFDDEEE1);bg.setCornerRadius(28*getResources().getDisplayMetrics().density);button.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,(int)(56*getResources().getDisplayMetrics().density));lp.bottomMargin=(int)(12*getResources().getDisplayMetrics().density);body.addView(button,lp);button.setOnClickListener(v->action.run());}
 private void enable(){getSharedPreferences("ransom_guard",0).edit().putBoolean("enabled",true).apply();Intent service=new Intent(this,RansomwareGuardService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);refresh();new Handler(Looper.getMainLooper()).postDelayed(()->{if(!isDestroyed())refresh();},700);}
 private void refresh(){if(report!=null)report.setText("自动检查："+(getSharedPreferences("ransom_guard",0).getBoolean("enabled",false)?(RansomwareGuardService.running?"运行中":"已设置开启，但服务未运行；请重新开启"):"已关闭")+"\n停止检查不会自动解除已有隔离。\n\n"+getSharedPreferences("ransom_guard",0).getString("progress","尚未开始本轮检查")+"\n\n"+getSharedPreferences("ransom_guard",0).getString("last_report","暂无命中记录。"));}
 @Override protected void onActivityResult(int q,int r,Intent data){super.onActivityResult(q,r,data);if(q==92&&r==RESULT_OK)enable();}
}
