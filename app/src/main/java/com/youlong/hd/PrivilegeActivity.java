package com.youlong.hd;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;


public class PrivilegeActivity extends AppCompatActivity {

    private static final String TAG = "PrivilegePanel";

    
    private static final String PREFS = "shield_prefs";
    private static final String KEY_GRANTED = "priv_granted";

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mBusy = new AtomicBoolean(false);

    private TextView mStatusValue;
    private TextView mIdentityValue;
    private TextView mAuthValue;
    private TextView mSourceValue;
    private TextView mShizukuValue;
    private TextView mResultValue;
    private Button mBtnSource;
    private Button mBtnExternal;
    private Button mBtnShizukuAuth;
    private Button mBtnRequest;
    private Button mBtnTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_ClearGuard_Compat);super.onCreate(savedInstanceState);
        setTitle("特权服务");
        setContentView(buildContentView());
        NativeUi.legacy(this,"权限服务","连接状态与授权范围，一目了然。");
        refresh();
    }

    
    public void runSelfTestAndLog() {
        new Thread(() -> {
            PrivStatus.Snapshot s = PrivStatus.collect();
            CrashLogger.event("[特权面板] " + s.summary());
            YouLongApp app = YouLongApp.instance();
            if (app != null) {
                CrashLogger.dumpEventsToFile(app, "probe.txt", "=== 自研特权面板 自检 ===");
            }
        }, "priv-panel-selftest").start();
    }

    // ==================================================================
    
    // ==================================================================

    private View buildContentView() {
        final int pad = dp(20);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF5F7F2);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        
        TextView title = new TextView(this);
        title.setText("特权服务");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF1C1C1E);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("免 root 以系统 shell 身份执行卸载 / 强停 / 冻结等操作");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF6E6E73);
        subtitle.setPadding(0, dp(6), 0, dp(18));
        root.addView(subtitle);

        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(pad, dp(16), pad, dp(16));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mStatusValue = addRow(card, "连接状态", "检测中…");
        mIdentityValue = addRow(card, "运行身份", "检测中…");
        mAuthValue = addRow(card, "授权状态", "检测中…");
        
        mSourceValue = addRow(card, "特权来源", "检测中…");
        mShizukuValue = addRow(card, "Shizuku 协议", "检测中…");

        
        
        
        
        Button btnPair = new Button(this);
        btnPair.setText("无线调试配对（免电脑启动特权服务）");
        btnPair.setTextSize(16);
        btnPair.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, AdbPairActivity.class));
            } catch (Throwable t) {
                CrashLogger.event("[特权面板] 打开无线配对页失败", t);
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(18);
        btnPair.setLayoutParams(plp);
        root.addView(btnPair);

        mBtnRequest = new Button(this);
        mBtnRequest.setText("申请特权授权");
        mBtnRequest.setTextSize(16);
        mBtnRequest.setOnClickListener(v -> onRequestPermission());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(18);
        mBtnRequest.setLayoutParams(rp);
        root.addView(mBtnRequest);

        
        mBtnSource = new Button(this);
        mBtnSource.setText("切换特权来源");
        mBtnSource.setTextSize(16);
        mBtnSource.setOnClickListener(v -> onSwitchSource());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(10);
        mBtnSource.setLayoutParams(slp);
        root.addView(mBtnSource);

        mBtnExternal = new Button(this);
        mBtnExternal.setText("允许外部 Shizuku 投递特权");
        mBtnExternal.setTextSize(16);
        mBtnExternal.setOnClickListener(v -> onToggleExternalShizuku());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(10);
        mBtnExternal.setLayoutParams(elp);
        root.addView(mBtnExternal);

        mBtnShizukuAuth = new Button(this);
        mBtnShizukuAuth.setText("申请 Shizuku 授权");
        mBtnShizukuAuth.setTextSize(16);
        mBtnShizukuAuth.setOnClickListener(v -> onRequestShizukuPermission());
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shp.topMargin = dp(10);
        mBtnShizukuAuth.setLayoutParams(shp);
        root.addView(mBtnShizukuAuth);

        // 未安装 Shizuku 时点击状态文字直接跳转下载
        mShizukuValue.setOnClickListener(v -> {
            if (!ShizukuBackend.isManagerInstalled(this)) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
                } catch (Exception ignored) {}
            }
        });

        mBtnTest = new Button(this);
        mBtnTest.setText("自检：跑一条特权命令");
        mBtnTest.setTextSize(16);
        mBtnTest.setOnClickListener(v -> onSelfTest());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(10);
        mBtnTest.setLayoutParams(tp);
        root.addView(mBtnTest);

        Button btnReconnect = new Button(this);
        btnReconnect.setText("重新连接特权服务");
        btnReconnect.setTextSize(16);
        btnReconnect.setOnClickListener(v -> {
            PrivStatus.requestReconnect(this);
            mMain.postDelayed(this::refresh, 2500);
        });
        LinearLayout.LayoutParams rcp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rcp.topMargin = dp(10);
        btnReconnect.setLayoutParams(rcp);
        root.addView(btnReconnect);
        Button btnList = new Button(this);
        btnList.setText("查看已安装应用");
        btnList.setTextSize(16);
        btnList.setOnClickListener(v -> {
            try { startActivity(new Intent(this, AppListActivity.class)); }
            catch (Throwable t) { CrashLogger.event("[特权面板] 打开应用列表失败", t); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btnList.setLayoutParams(lp);
        root.addView(btnList);

        Button btnSettings = new Button(this);
        btnSettings.setText("特权服务设置");
        btnSettings.setTextSize(16);
        btnSettings.setOnClickListener(v -> {
            try { startActivity(new Intent(this, PrivSettingsActivity.class)); }
            catch (Throwable t) { CrashLogger.event("[特权面板] 打开设置页失败", t); }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        btnSettings.setLayoutParams(sp);
        root.addView(btnSettings);
        
        TextView resultLabel = new TextView(this);
        resultLabel.setText("命令输出");
        resultLabel.setTextSize(13);
        resultLabel.setTextColor(0xFF6E6E73);
        resultLabel.setPadding(0, dp(22), 0, dp(8));
        root.addView(resultLabel);

        mResultValue = new TextView(this);
        mResultValue.setText("（点上面的按钮跑一条看看）");
        mResultValue.setTextSize(12);
        mResultValue.setTextColor(0xFF3A3A3C);
        mResultValue.setTypeface(Typeface.MONOSPACE);
        mResultValue.setTextIsSelectable(true);
        mResultValue.setBackgroundColor(Color.WHITE);
        mResultValue.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(mResultValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setText("说明：特权由系统 shell 用户（uid 2000）提供，不需要 root，"
                + "也不需要安装任何第三方应用。");
        tip.setTextSize(12);
        tip.setTextColor(0xFF8E8E93);
        tip.setPadding(0, dp(16), 0, 0);
        root.addView(tip);

        return scroll;
    }

    
    private TextView addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(0xFF1C1C1E);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.42f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(0xFF6E6E73);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0.58f));

        parent.addView(row);
        return v;
    }

    // ==================================================================
    
    // ==================================================================

    
    private void refresh() {
        
        if (!StellarUtils.isPrivilegeBinderAlive()) {
            PrivStatus.requestReconnect(this);
        }
        new Thread(() -> {
            
            final boolean available = StellarUtils.isPrivilegeBinderAlive();
            final boolean granted = available && StellarUtils.hasStellarPermission();
            final String sourceName = PrivRouter.activeName(this);
            final int sourceMode = PrivRouter.getSource(this);
            final boolean externalAllowed = PrivRouter.isExternalShizukuAllowed(this);
            final boolean shizukuAlive = PrivRouter.shizukuAlive();
            final boolean shizukuGranted = shizukuAlive && ShizukuBackend.hasPermission();
            final boolean shizukuInstalled = ShizukuBackend.isManagerInstalled(this);
            final String identity;
            if (!available) {
                identity = "未连接（点下方按钮重连）";
            } else if (!granted) {
                identity = "已连接，待授权";
            } else {
                
                String out = StellarUtils.runCommand("id -u", 3000);
                identity = "uid=" + (TextUtils.isEmpty(out) ? "?" : out.trim()) + "（shell 为 2000）";
            }
            mMain.post(() -> {
                mStatusValue.setText(available ? "已连接" : "未连接");
                mStatusValue.setTextColor(available ? 0xFF34C759 : 0xFFFF3B30);
                mIdentityValue.setText(identity);
                mAuthValue.setText(granted ? "已授权" : "未授权");
                mAuthValue.setTextColor(granted ? 0xFF34C759 : 0xFFFF9500);
                mBtnRequest.setEnabled(!granted);
                mBtnRequest.setText(granted ? "已授权" : "申请特权授权");

                
                String mode = sourceMode == PrivRouter.SOURCE_BUILTIN ? "固定内置"
                        : sourceMode == PrivRouter.SOURCE_SHIZUKU ? "固定 Shizuku" : "自动";
                mSourceValue.setText(sourceName + "（" + mode + "）");
                mSourceValue.setTextColor(available ? 0xFF34C759 : 0xFFFF9500);

                StringBuilder sb = new StringBuilder();
                if (shizukuAlive) {
                    int uid = ShizukuBackend.serverUid();
                    sb.append(shizukuGranted ? "已连接已授权" : "已连接待授权");
                    if (uid > 0) sb.append("，服务端 uid=").append(uid);
                } else if (shizukuInstalled) {
                    sb.append("已安装 Shizuku，未连接（打开 Shizuku 启动服务后回来刷新）");
                } else {
                    sb.append("未安装 Shizuku，点击此处下载");
                }
                if (externalAllowed) sb.append(" · 已允许外部投递");
                mShizukuValue.setText(sb.toString());
                mShizukuValue.setTextColor(shizukuAlive ? 0xFF34C759 : 0xFF8E8E93);

                mBtnSource.setText("切换特权来源（当前：" + mode + "）");
                mBtnExternal.setText(externalAllowed
                        ? "禁止外部 Shizuku 投递（当前：已允许）"
                        : "允许外部 Shizuku 投递（当前：已禁止）");
                mBtnShizukuAuth.setEnabled(true);
                if (!shizukuInstalled) {
                    mBtnShizukuAuth.setText("下载安装 Shizuku");
                } else if (!shizukuAlive) {
                    mBtnShizukuAuth.setText("打开 Shizuku 启动服务");
                } else if (shizukuGranted) {
                    mBtnShizukuAuth.setText("Shizuku 已授权");
                } else {
                    mBtnShizukuAuth.setText("申请 Shizuku 授权");
                }
            });
        }, "priv-refresh").start();
    }

    // ==================================================================
    
    // ==================================================================

    
    private void onSwitchSource() {
        int next;
        switch (PrivRouter.getSource(this)) {
            case PrivRouter.SOURCE_AUTO:    next = PrivRouter.SOURCE_BUILTIN; break;
            case PrivRouter.SOURCE_BUILTIN: next = PrivRouter.SOURCE_SHIZUKU; break;
            default:                        next = PrivRouter.SOURCE_AUTO; break;
        }
        PrivRouter.setSource(this, next);
        CrashLogger.event("[特权来源] 切换为 " + PrivRouter.sourceName(this));
        refresh();
    }

    
    private void onToggleExternalShizuku() {
        boolean now = PrivRouter.isExternalShizukuAllowed(this);
        PrivRouter.setExternalShizukuAllowed(this, !now);
        mResultValue.setText(!now
                ? "已允许外部 Shizuku 投递。\n\n说明：只有在设备上装了官方 Shizuku"
                  + "（moe.shizuku.privileged.api）并启动其服务后，本应用才会通过"
                  + " Shizuku 协议拿到特权 Binder；其它应用的投递一律拒绝。"
                : "已禁止外部 Shizuku 投递，仅使用内置内核。");
        refresh();
    }

    
    private void onRequestShizukuPermission() {
        PrivRouter.setSource(this,PrivRouter.SOURCE_SHIZUKU);
        PrivRouter.setExternalShizukuAllowed(this,true);
        if (!ShizukuBackend.isManagerInstalled(this)) {
            try {
                startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/RikkaApps/Shizuku/releases")));
            } catch (Exception e) {
                mResultValue.setText("无法打开下载页，请手动安装 Shizuku");
            }
            return;
        }
        if (!PrivRouter.shizukuAlive()) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(ShizukuBackend.PACKAGE_SHIZUKU);
                if (launch != null) startActivity(launch);
            } catch (Exception ignored) {}
            mResultValue.setText("Shizuku 已安装但未启动，请在 Shizuku 应用中启动服务后回来");
            return;
        }
        ShizukuBackend.requestPermission(1002);
        CrashLogger.event("[特权来源] 已通过 Shizuku 协议发起授权请求");
        mMain.postDelayed(this::refresh, 1500);
    }

    
    private void onRequestPermission() {
        try {
            
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putBoolean(KEY_GRANTED, false).apply();

            
            
            roro.stellar.Stellar.INSTANCE.requestPermission("stellar", 1001);
        } catch (Throwable t) {
            CrashLogger.event("[特权面板] 申请授权失败: " + t);
        }
        mMain.postDelayed(this::refresh, 1200);
    }

    
    private void onSelfTest() {
        if (!mBusy.compareAndSet(false, true)) return;
        mResultValue.setText("执行中…");
        new Thread(() -> {
            final String out = StellarUtils.runCommand("id", 12000);
            CrashLogger.event("[特权面板] 自检 id => " + out.replace('\n', ' '));
            mMain.post(() -> {
                mResultValue.setText(out);
                mBusy.set(false);
                refresh();
            });
        }, "priv-selftest").start();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
