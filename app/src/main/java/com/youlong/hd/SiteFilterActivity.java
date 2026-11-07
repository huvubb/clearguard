package com.youlong.hd;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.*;

public final class SiteFilterActivity extends Activity {
    private CheckBox threat,adult,gambling;
    private EditText allow,deny;
    private TextView status;
    private SharedPreferences prefs;
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);prefs=getSharedPreferences("site_filter",0);
        NativeUi.Page page=new NativeUi.Page(this,"危险网址防护","让不可信域名止于解析之前。","命中名单时直接丢弃 DNS 查询包，不向上游发送、不返回解析结果。内置 HaGeZi 分类名单快照：2026-11-01。未拦截查询通过 HTTPS 交给 Cloudflare Security，服务会看到域名。不解密网页；自带加密 DNS、直连 IP、缓存地址与已建立连接可能绕过。网站过滤与应用隔离共用 VPN，切换会停止另一模式。");
        LinearLayout summary=page.section("防护状态");status=new TextView(this);NativeUi.report(this,summary,status,false);
        LinearLayout categories=page.section("过滤类别");threat=box(categories,"木马、钓鱼与恶意域名","threat");adult=box(categories,"成人网站","adult");gambling=box(categories,"赌博网站","gambling");
        LinearLayout rules=page.section("自定义规则");allow=field(rules,"放行域名，每行一个（含子域名）",prefs.getString("allow",""));deny=field(rules,"拦截域名，每行一个",prefs.getString("deny",""));NativeUi.action(this,rules,"保存规则",()->{if(save()){stopService(new Intent(this,SiteFilterService.class));status.setText("规则已保存，旧过滤已停止。请重新开启以应用新名单。");}},false);
        LinearLayout controls=page.section("防护控制");NativeUi.action(this,controls,"开启网站防护  ↗",()->{if(SiteFilterService.running){status.setText("网站防护正在运行。修改名单后请先保存规则。");return;}if(!save())return;new AlertDialog.Builder(this).setTitle("开启域名过滤？").setMessage("使用本机 VPN 丢弃命中名单的 DNS 查询，未拦截域名由 Cloudflare Security 解析。会替换其他 VPN／应用隔离模式；不解密网页，可能被加密 DNS 或直连 IP 绕过。").setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->{Intent request=VpnService.prepare(this);if(request!=null)startActivityForResult(request,90);else start();}).show();},true);
        NativeUi.action(this,controls,"停止网站过滤",()->{stopService(new Intent(this,SiteFilterService.class));status.setText("已请求停止网站过滤。");},false);
        NativeUi.action(this,controls,"查看最近拦截",()->{String domain=prefs.getString("last_blocked","");if(domain.isEmpty()){status.setText("尚无拦截记录。");return;}new AlertDialog.Builder(this).setTitle("最近拦截").setMessage(domain+"\n类别："+prefs.getString("last_category","")+"\n放行规则保存后需要重新开启防护。").setNegativeButton("保持拦截",null).setPositiveButton("加入放行名单",(d,w)->{allow.append((allow.length()>0?"\n":"")+domain);if(save()){stopService(new Intent(this,SiteFilterService.class));status.setText("已加入放行名单，请重新开启防护。");}}).show();},false);page.show();
    }
    private CheckBox box(LinearLayout root,String title,String key){CheckBox box=new CheckBox(this);box.setText(title);box.setChecked(prefs.getBoolean(key,true));root.addView(box);return box;}
    private EditText field(LinearLayout root,String title,String text){EditText input=new EditText(this);input.setHint(title);input.setMinLines(2);input.setText(text);root.addView(input);return input;}
    private boolean save(){
        try{SiteDomainPolicy.parse(allow.getText().toString());SiteDomainPolicy.parse(deny.getText().toString());}
        catch(IllegalArgumentException error){status.setText(error.getMessage());return false;}
        prefs.edit().putBoolean("threat",threat.isChecked()).putBoolean("adult",adult.isChecked()).putBoolean("gambling",gambling.isChecked())
                .putString("allow",allow.getText().toString()).putString("deny",deny.getText().toString()).putString("error","").apply();return true;
    }
    private void start(){stopService(new Intent(this,NetworkBlockService.class));Intent service=new Intent(this,SiteFilterService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);status.setText("正在加载名单，实际状态以常驻通知为准。");}
    @Override protected void onResume(){super.onResume();if(status!=null)status.setText(SiteFilterService.running?"网站防护正在运行。":"网站防护未运行。"+prefs.getString("error",""));}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==90&&result==RESULT_OK)start();}
}
