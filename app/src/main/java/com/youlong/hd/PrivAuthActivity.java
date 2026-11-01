package com.youlong.hd;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class PrivAuthActivity extends Activity {

    private static final String TAG = "PrivAuth";

    private static final String ACTION_REQUEST_PERMISSION =
            "com.youlong.hd.intent.action.REQUEST_PERMISSION";

    
    private static final String KEY_ALLOWED = "stellar:request-permission-reply-allowed";
    private static final String KEY_ONETIME = "stellar:request-permission-reply-is-onetime";
    private static final String KEY_PERMISSION = "stellar:request-permission-reply-permission";
    private static final String DEFAULT_PERMISSION = "stellar";

    private int mUid = -1;
    private int mPid = -1;
    private int mRequestCode = -1;
    private String mPermission = DEFAULT_PERMISSION;
    private boolean mAnswered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readIntent();
        setContentView(buildContentView());
    }

    
    private void readIntent() {
        Intent it = getIntent();
        if (it == null) return;
        mUid = it.getIntExtra("uid", -1);
        mPid = it.getIntExtra("pid", -1);
        mRequestCode = it.getIntExtra("requestCode", -1);
        String p = it.getStringExtra("permission");
        mPermission = TextUtils.isEmpty(p) ? DEFAULT_PERMISSION : p;
        Log.i(TAG, "收到授权请求 uid=" + mUid + " pid=" + mPid
                + " requestCode=" + mRequestCode + " permission=" + mPermission);
    }

    // ==================================================================
    
    // ==================================================================

    private View buildContentView() {
        final int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        root.setPadding(pad, dp(28), pad, dp(20));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        
        ImageView icon = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(getPackageName());
            icon.setImageDrawable(d);
        } catch (Throwable ignored) {
        }
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(64), dp(64));
        ip.bottomMargin = dp(14);
        root.addView(icon, ip);

        TextView title = new TextView(this);
        title.setText("授权澄盾 ClearGuard使用特权服务");
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1C1C1E);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("授权后，本应用可以以系统 shell 身份执行卸载、强制停止、冻结等操作，"
                + "用于清除顽固病毒。整个过程不需要 root。");
        sub.setTextSize(14);
        sub.setTextColor(0xFF6E6E73);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(12), 0, dp(18));
        root.addView(sub);

        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFF5F5F7);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addRow(card, "请求来源", describeRequester());
        addRow(card, "权限类型", mPermission);
        addRow(card, "执行身份", "系统 shell (uid 2000)");

        
        Button allow = new Button(this);
        allow.setText("允许");
        allow.setTextSize(17);
        allow.setBackgroundColor(0xFF34C759);
        allow.setTextColor(Color.WHITE);
        allow.setOnClickListener(v -> answer(true, false));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(22);
        root.addView(allow, ap);

        Button allowOnce = new Button(this);
        allowOnce.setText("仅本次允许");
        allowOnce.setTextSize(15);
        allowOnce.setOnClickListener(v -> answer(true, true));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin = dp(10);
        root.addView(allowOnce, op);

        Button deny = new Button(this);
        deny.setText("拒绝");
        deny.setTextSize(15);
        deny.setTextColor(0xFF8E8E93);
        deny.setOnClickListener(v -> answer(false, false));
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp2.topMargin = dp(10);
        root.addView(deny, dp2);

        TextView tip = new TextView(this);
        tip.setText("可以随时在「特权服务」页面里重新授权或收回。");
        tip.setTextSize(12);
        tip.setTextColor(0xFF8E8E93);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(16), 0, 0);
        root.addView(tip);

        return root;
    }

    private void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(0xFF8E8E93);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.35f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(0xFF1C1C1E);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.65f));

        parent.addView(row);
    }

    
    private String describeRequester() {
        if (mUid <= 0) return "本应用";
        PackageManager pm = getPackageManager();
        try {
            String[] pkgs = pm.getPackagesForUid(mUid);
            if (pkgs != null && pkgs.length > 0) {
                ApplicationInfo ai = pm.getApplicationInfo(pkgs[0], 0);
                String label = pm.getApplicationLabel(ai).toString();
                return label + "\n" + pkgs[0];
            }
        } catch (Throwable ignored) {
        }
        return "uid=" + mUid;
    }

    // ==================================================================
    
    // ==================================================================

    private void answer(boolean allowed, boolean onetime) {
        if (mAnswered) return;
        mAnswered = true;
        Log.i(TAG, "用户选择 allowed=" + allowed + " onetime=" + onetime);
        CrashLogger.event("[授权页] 用户选择 allowed=" + allowed + " onetime=" + onetime);
        try {
            
            awaitBinder(5000);

            Bundle data = new Bundle();
            data.putBoolean(KEY_ALLOWED, allowed);
            data.putBoolean(KEY_ONETIME, onetime);
            data.putString(KEY_PERMISSION, mPermission);

            
            roro.stellar.Stellar.INSTANCE.dispatchPermissionConfirmationResult(
                    mUid, mPid, mRequestCode, data);
        } catch (Throwable t) {
            Log.e(TAG, "回写授权结果失败", t);
            CrashLogger.event("[授权页] 回写结果失败", t);
        }
        finish();
    }

    private void awaitBinder(long timeoutMs) {
        try {
            if (roro.stellar.Stellar.INSTANCE.pingBinder()) return;
            final CountDownLatch latch = new CountDownLatch(1);
            roro.stellar.Stellar.OnBinderReceivedListener l =
                    () -> latch.countDown();
            roro.stellar.Stellar.INSTANCE.addBinderReceivedListener(l, null);
            try {
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } finally {
                try {
                    roro.stellar.Stellar.INSTANCE.removeBinderReceivedListener(l);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onBackPressed() {
        
        answer(false, false);
        super.onBackPressed();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
