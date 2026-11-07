package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.util.Log;
import androidx.core.content.ContextCompat;

/** Native dashboard with separate overview, protection and tools surfaces. */
public final class MainActivity extends Activity {
 private static final int RESTART_ALARM_REQ=0x5A1;
 static final String KEY_AUTO_RESTART_AT="auto_restart_at";
 private LinearLayout root,page,nav;private int selected;
 private final int ink=0xFF173C30,muted=0xFF617165,bg=0xFFF5F7F2;
 @Override public void onCreate(Bundle state){setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);if(getSharedPreferences("ransom_guard",0).getBoolean("enabled",false)&&!RansomwareGuardService.running)try{ContextCompat.startForegroundService(this,new Intent(this,RansomwareGuardService.class));}catch(Exception error){Log.w("Guard","恢复主动检查失败",error);}show(state==null?0:state.getInt("tab",0));if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},61);}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("tab",selected);}
 @Override protected void onResume(){super.onResume();if(root!=null)show(selected);}
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(Typeface.create("sans-serif-medium",0));return t;}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;}
 private void gap(LinearLayout parent,int h){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
 private void show(int tab){
  selected=tab;root=column();root.setBackgroundColor(bg);NativeUi.insets(this,root);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);page=column();page.setPadding(dp(24),dp(18),dp(24),dp(20));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView brand=text("澄盾",24,ink,true);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView badge=text("本机安全",11,ink,true);badge.setPadding(dp(12),dp(7),dp(12),dp(7));badge.setBackground(shape(0xFFE5ECDF,20));header.addView(badge);page.addView(header);gap(page,5);page.addView(text("CLEAR GUARD  /  设备由你掌控",10,muted,false));gap(page,26);
  if(tab==0)overview();else if(tab==1)protection();else tools();
  nav=new LinearLayout(this);nav.setPadding(dp(7),dp(7),dp(7),dp(7));nav.setBackground(shape(0xFFFFFFFF,30));nav.setElevation(dp(2));String[] labels={"◉  总览","◇  守护","⊞  工具"};for(int i=0;i<labels.length;i++){final int index=i;TextView item=text(labels[i],14,i==tab?0xFFFFFFFF:muted,true);item.setGravity(Gravity.CENTER);item.setBackground(shape(i==tab?ink:Color.TRANSPARENT,24));item.setContentDescription(new String[]{"总览","守护","工具"}[i]);item.setOnClickListener(v->show(index));nav.addView(item,new LinearLayout.LayoutParams(0,dp(46),1));}
  LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(60));bar.setMargins(dp(24),dp(4),dp(24),dp(16));root.addView(nav,bar);setContentView(root);
 }
 private void overview(){
  LinearLayout hero=column();hero.setPadding(dp(24),dp(24),dp(24),dp(24));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF143D2E,0xFF285742});gradient.setCornerRadius(dp(32));hero.setBackground(gradient);
  TextView label=text("安 全 概 览",11,0xFFBDCEBD,true);hero.addView(label);gap(hero,12);
  LinearLayout heroTop=new LinearLayout(this);heroTop.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=column();copy.addView(text("守住边界\n安心使用",30,0xFFF1F7E9,true));gap(copy,10);copy.addView(text("先检查，再决定如何处置",12,0xFFBDCEBD,false));heroTop.addView(copy,new LinearLayout.LayoutParams(0,-2,1));heroTop.addView(new Shield(),new LinearLayout.LayoutParams(dp(108),dp(140)));hero.addView(heroTop);gap(hero,20);
  TextView scan=text("开始安全检查    ↗",15,ink,true);scan.setGravity(Gravity.CENTER);scan.setBackground(shape(0xFFDDF1B4,26));scan.setOnClickListener(v->open(NativeScanActivity.class));hero.addView(scan,new LinearLayout.LayoutParams(-1,dp(54)));gap(hero,16);
  boolean enabled=getSharedPreferences("ransom_guard",0).getBoolean("enabled",false);hero.addView(text(enabled?"◉  已设置自动检查 · 查看运行通知确认状态":"○  自动检查尚未开启",11,0xFFBDCEBD,false));page.addView(hero);gap(page,26);
  section("常用检查","LOCAL FIRST");gap(page,12);
  LinearLayout row=new LinearLayout(this);tile(row,"◎","应用检查","查看风险与检测依据",NativeScanActivity.class,0xFFE7EEDB);tile(row,"↗","存储清理","整理临时文件",DiskCleanupActivity.class,0xFFF0E9DD);page.addView(row);gap(page,24);
  section("主动防护","PROTECTION");gap(page,12);entry("◇","勒索风险防护","监测新应用，疑似命中后隔离网络",RansomwareGuardActivity.class);gap(page,12);entry("⌁","网络边界","危险域名过滤与应用断网",NetworkBlockActivity.class);gap(page,20);page.addView(text("检测结论以报告为准，未命中不代表没有风险。",11,muted,false));
 }
 private void section(String title,String tag){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(title,19,ink,true),new LinearLayout.LayoutParams(0,-2,1));row.addView(text(tag,9,muted,false));page.addView(row);}
 private void tile(LinearLayout row,String icon,String title,String subtitle,Class<?> activity,int color){LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(14),dp(18));card.setBackground(shape(color,26));card.addView(text(icon,27,ink,false));gap(card,18);card.addView(text(title,17,ink,true));gap(card,6);card.addView(text(subtitle,11,muted,false));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)lp.leftMargin=dp(12);row.addView(card,lp);card.setOnClickListener(v->open(activity));card.setContentDescription(title);}
 private void entry(String icon,String title,String detail,Class<?> activity){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(18),dp(18),dp(16),dp(18));row.setBackground(shape(0xFFFFFFFF,24));TextView symbol=text(icon,25,0xFF547F60,true);symbol.setGravity(Gravity.CENTER);row.addView(symbol,new LinearLayout.LayoutParams(dp(34),dp(44)));LinearLayout copy=column();copy.setPadding(dp(12),0,dp(8),0);copy.addView(text(title,15,ink,true));gap(copy,5);copy.addView(text(detail,11,muted,false));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("›",25,muted,false));row.setOnClickListener(v->open(activity));page.addView(row);}
 private void protection(){page.addView(text("让防护，各就其位。",28,ink,true));gap(page,10);page.addView(text("按需开启，随时暂停。每一次拦截都有依据。",13,muted,false));gap(page,24);entry("◇","勒索风险自动检查","监测安装与更新，支持疑似风险自动断网",RansomwareGuardActivity.class);gap(page,12);entry("⌁","危险网址防护","命中域名规则时丢弃 DNS 查询",SiteFilterActivity.class);gap(page,12);entry("⊘","应用外传阻断","选择应用，丢弃其 TCP / UDP 流量",NetworkBlockActivity.class);gap(page,12);entry("◎","应用与风险处置","查看已安装应用并手动处置",AppListActivity.class);gap(page,24);page.addView(text("网站过滤与应用隔离目前不能同时运行，开启时会提示切换。",12,muted,false));}
 private void tools(){page.addView(text("掌控设备的工具箱",28,ink,true));gap(page,10);page.addView(text("从本地推理到命令执行，能力与权限清晰可见。",13,muted,false));gap(page,24);entry("⌘","命令终端","Android sh · CMD / PowerShell 常用转译",TerminalActivity.class);gap(page,12);entry("✧","本地模型与脚本","GGUF 本机推理与 Python 运行",LocalRunActivity.class);gap(page,12);entry("↗","磁盘清理","预览缓存，再决定清理范围",DiskCleanupActivity.class);gap(page,12);entry("⌁","网络诊断","检查当前网络与指定主机",NativeScanActivity.class);gap(page,12);entry("⚿","权限服务","Shizuku 与本机授权状态",PrivilegeActivity.class);gap(page,12);entry("↻","检查更新","连接项目网站查询版本",SupportActivity.class);gap(page,12);entry("≡","运行记录","查看设备上的处理记录",RuntimeLogActivity.class);}
 private void open(Class<?> activity){startActivity(new Intent(this,activity));}
 private final class Shield extends View{
  private final Paint paint=new Paint(3);
  Shield(){super(MainActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),x=w*.5f,y=h*.48f,r=w*.43f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x335EA478);c.drawCircle(x,y,r,paint);c.drawCircle(x,y,r*.78f,paint);paint.setColor(0xFF638D68);c.drawCircle(x+r*.86f,y-r*.52f,dp(3),paint);Path p=new Path();p.moveTo(x,y-r*.65f);p.lineTo(x+r*.54f,y-r*.39f);p.lineTo(x+r*.47f,y+r*.19f);p.cubicTo(x+r*.4f,y+r*.45f,x+r*.15f,y+r*.63f,x,y+r*.72f);p.cubicTo(x-r*.15f,y+r*.63f,x-r*.4f,y+r*.45f,x-r*.47f,y+r*.19f);p.lineTo(x-r*.54f,y-r*.39f);p.close();paint.setColor(0xFFDDF1B4);paint.setStrokeWidth(dp(2));c.drawPath(p,paint);Path tick=new Path();tick.moveTo(x-r*.22f,y+r*.04f);tick.lineTo(x-r*.03f,y+r*.22f);tick.lineTo(x+r*.26f,y-r*.14f);paint.setStrokeWidth(dp(3));paint.setStrokeCap(Paint.Cap.ROUND);c.drawPath(tick,paint);paint.setStyle(Paint.Style.FILL);paint.setColor(0x33DDF1B4);c.drawCircle(x+r*.73f,y+r*.68f,dp(6),paint);}
 }
    static void restartAfterTaskRemoved(Context ctx) {


        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> keepAliveAfterTaskRemoved(app), 600);
    }

    private static void keepAliveAfterTaskRemoved(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("shield_prefs", Context.MODE_PRIVATE);
        boolean anyOn = sp.getBoolean("protect_on", false)
                || sp.getBoolean("shake_trigger_on", false)
                || sp.getBoolean("volume_trigger_on", false);
        if (!anyOn) return;

        long now = System.currentTimeMillis();

        if (now - sp.getLong(KEY_AUTO_RESTART_AT, 0L) < 15000L) {
            Log.i("Shield", "防终结保活冷却中，跳过");
            return;
        }
        sp.edit().putLong(KEY_AUTO_RESTART_AT, now).apply();

        Log.i("Shield", "应用被从最近任务划掉，保活守护服务（不拉起界面）");

        try {
            ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, ProtectService.class));
        } catch (Exception ignored) {}
        try {
            ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, ForegroundService.class));
        } catch (Exception ignored) {}

        cancelRestartAlarm(ctx);
    }


    static void cancelRestartAlarm(Context ctx) {
        try {
            Intent i = new Intent(ctx, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, RESTART_ALARM_REQ, i,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi == null) return;
            AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) {}
    }

}
