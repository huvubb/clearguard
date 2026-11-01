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
        super.onCreate(state);prefs=getSharedPreferences("site_filter",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,28,24,24);
        TextView title=new TextView(this);title.setText("澄盾 · 敏感网站防护");title.setTextSize(24);root.addView(title);
        TextView help=new TextView(this);help.setText("按域名拦截木马钓鱼、成人和赌博网站，不解密 HTTPS 页面。内置 HaGeZi 分类名单快照：2026-11-01。未拦截的 DNS 查询通过 HTTPS 交给 Cloudflare Security；该服务会看到查询域名。\n\n应用自带的加密 DNS、直连 IP 或其他 VPN 可能绕过域名过滤。网站过滤与应用断网隔离共用 Android VPN 通道，切换会停止另一模式。VPN/DNS 无法完全替代杀毒扫描。");root.addView(help);
        status=new TextView(this);root.addView(status);
        threat=box(root,"木马、钓鱼与恶意域名","threat");adult=box(root,"成人网站","adult");gambling=box(root,"赌博网站","gambling");
        allow=field(root,"放行域名，每行一个（同时放行其子域名）",prefs.getString("allow",""));
        deny=field(root,"自定义拦截域名，每行一个",prefs.getString("deny",""));
        Button save=new Button(this);save.setText("保存名单并停止当前过滤");save.setOnClickListener(v -> {if(save()){stopService(new Intent(this,SiteFilterService.class));status.setText("已保存，请重新开启使新名单生效。");}});root.addView(save);
        Button enable=new Button(this);enable.setText("开启网站防护");enable.setOnClickListener(v -> {
            if(SiteFilterService.running){status.setText("网站防护已在运行。修改规则后请先保存并停止。");return;}
            if(!save())return;
            new AlertDialog.Builder(this).setTitle("开启域名过滤")
                    .setMessage("允许澄盾使用本机 VPN 过滤 DNS；未拦截域名由 Cloudflare Security 解析。不解密网页，不上传页面正文。开启后将替换其他 VPN / 应用断网隔离模式。")
                    .setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->{Intent request=VpnService.prepare(this);if(request!=null)startActivityForResult(request,90);else start();}).show();
        });root.addView(enable);
        Button disable=new Button(this);disable.setText("停止网站过滤");disable.setOnClickListener(v -> {stopService(new Intent(this,SiteFilterService.class));status.setText("已请求停止网站过滤。");});root.addView(disable);
        Button last=new Button(this);last.setText("查看最近拦截 / 单独放行");last.setOnClickListener(v -> {
            String domain=prefs.getString("last_blocked","");if(domain.isEmpty()){status.setText("尚无拦截记录。");return;}
            new AlertDialog.Builder(this).setTitle("域名已拦截").setMessage(domain+"\n类别："+prefs.getString("last_category","")+"\n放行规则保存后，重新开启防护生效。")
                    .setNegativeButton("保持拦截",null).setPositiveButton("加入放行名单",(d,w)->{allow.append((allow.length()>0?"\n":"")+domain);if(save()){stopService(new Intent(this,SiteFilterService.class));status.setText("已放行并停止旧规则，请重新开启防护。");}}).show();
        });root.addView(last);
        ScrollView page=new ScrollView(this);page.addView(root);setContentView(page);
    }
    private CheckBox box(LinearLayout root,String title,String key){CheckBox box=new CheckBox(this);box.setText(title);box.setChecked(prefs.getBoolean(key,true));root.addView(box);return box;}
    private EditText field(LinearLayout root,String title,String text){EditText input=new EditText(this);input.setHint(title);input.setMinLines(2);input.setText(text);root.addView(input);return input;}
    private boolean save(){
        try{SiteDomainPolicy.parse(allow.getText().toString());SiteDomainPolicy.parse(deny.getText().toString());}
        catch(IllegalArgumentException error){status.setText(error.getMessage());return false;}
        prefs.edit().putBoolean("threat",threat.isChecked()).putBoolean("adult",adult.isChecked()).putBoolean("gambling",gambling.isChecked())
                .putString("allow",allow.getText().toString()).putString("deny",deny.getText().toString()).putString("error","").apply();return true;
    }
    private void start(){Intent service=new Intent(this,SiteFilterService.class);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);status.setText("正在加载名单，实际状态以常驻通知为准。");}
    @Override protected void onResume(){super.onResume();if(status!=null)status.setText(SiteFilterService.running?"网站防护正在运行。":"网站防护未运行。"+prefs.getString("error",""));}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==90&&result==RESULT_OK)start();}
}
