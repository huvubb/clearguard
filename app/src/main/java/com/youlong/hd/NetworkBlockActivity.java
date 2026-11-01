package com.youlong.hd;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.VpnService;
import android.widget.*;
import java.util.*;
public final class NetworkBlockActivity extends Activity {
 private String pkg;
 @Override public void onCreate(Bundle state){super.onCreate(state);pkg=getIntent().getStringExtra("package");if(!PkgGuard.isValid(pkg)||pkg.equals(getPackageName())){finish();return;}
 new AlertDialog.Builder(this).setTitle("隔离应用网络").setMessage(pkg+"\n启用本地 VPN，阻止目标应用所有经此通道的 IPv4/IPv6 网络连接。会替换当前 VPN；相同 UID 的共享应用也可能受影响。") .setNegativeButton("取消",(d,w)->finish()).setNeutralButton("解除隔离",(d,w)->save(false)).setPositiveButton("阻止网络",(d,w)->{Intent consent=VpnService.prepare(this);if(consent!=null)startActivityForResult(consent,1);else save(true);}).setOnCancelListener(d->finish()).show();}
 @Override protected void onActivityResult(int q,int result,Intent data){super.onActivityResult(q,result,data);if(q==1&&result==RESULT_OK)save(true);else finish();}
 private void save(boolean block){Set<String> apps=new HashSet<>(getSharedPreferences("network_block",0).getStringSet("packages",Collections.emptySet()));if(block)apps.add(pkg);else apps.remove(pkg);getSharedPreferences("network_block",0).edit().putStringSet("packages",apps).apply();Intent service=new Intent(this,NetworkBlockService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);finish();}
}
