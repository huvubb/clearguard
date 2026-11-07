package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public final class DiskCleanupActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView report;private Button inspect,clean;private File[] roots;
 @Override public void onCreate(Bundle state){
  setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);List<File> paths=new ArrayList<>();paths.add(getCacheDir());Collections.addAll(paths,getExternalCacheDirs());roots=paths.toArray(new File[0]);
  NativeUi.Page page=new NativeUi.Page(this,"存储清理","先看清范围，再释放空间。","仅清理澄盾自身的内部与外部缓存。保留模型、照片、文档与其他应用数据。不跟随符号链接。缓存中的临时下载可能需要重新下载，清理前会再次确认。");
  LinearLayout summary=page.section("缓存概览");report=new TextView(this);NativeUi.report(this,summary,report,false);
  LinearLayout actions=page.section("清理操作");inspect=NativeUi.action(this,actions,"重新检查缓存",()->scan(false),false);clean=NativeUi.action(this,actions,"清理临时缓存  ↗",()->new AlertDialog.Builder(this).setTitle("清理澄盾缓存？").setMessage("仅删除缓存目录中的临时文件，不触碰模型、照片和文档。删除不能撤销，临时下载可能需要重试。").setNegativeButton("取消",null).setPositiveButton("清理",(d,w)->scan(true)).show(),true);
  LinearLayout files=page.section("清理其他文件");NativeUi.action(this,files,"选择文件夹，预览并清理",()->startActivity(new Intent(this,FolderCleanupActivity.class)),false);
  LinearLayout system=page.section("更多空间管理");system.addView(NativeUi.label(this,"其他应用和整机存储交由系统管理。",13,0xFF526358));NativeUi.action(this,system,"打开系统存储管理",()->{try{startActivity(new Intent("android.os.storage.action.MANAGE_STORAGE"));}catch(ActivityNotFoundException absent){startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}},false);page.show();scan(false);
 }
 private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 private Button button(LinearLayout parent,String title,Runnable action){Button button=new Button(this);button.setText(title);button.setMinHeight(dp(56));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFDDEEE1);bg.setCornerRadius(dp(28));button.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(button,lp);button.setOnClickListener(v->action.run());return button;}
 private void scan(boolean delete){if(Build.VERSION.SDK_INT<26){report.setText("Android 8 以下请使用系统存储清理入口。");inspect.setEnabled(false);clean.setEnabled(false);return;}inspect.setEnabled(false);clean.setEnabled(false);report.setText(delete?"正在清理…":"正在检查…");worker.execute(()->{String message;try{CacheCleaner.Result result=CacheCleaner.inspect(roots,delete);StatFs disk=new StatFs(getFilesDir().getPath());message=(delete?"清理完成":"缓存检查完成")+"\n"+(delete?"已释放：":"可清理：")+android.text.format.Formatter.formatFileSize(this,result.bytes)+"\n文件："+result.files+"\n未处理："+result.skipped+"\n内部存储可用："+android.text.format.Formatter.formatFileSize(this,disk.getAvailableBytes());}catch(IOException error){message="清理未完成："+error.getMessage();}final String text=message;runOnUiThread(()->{if(!isDestroyed()){report.setText(text);inspect.setEnabled(true);clean.setEnabled(true);}});});}
 @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
