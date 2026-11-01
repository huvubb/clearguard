package com.youlong.hd;

import android.app.Application;
import android.content.Context;


public class YouLongApp extends Application {

    
    private static volatile YouLongApp sInstance;

    
    public static YouLongApp instance() {
        return sInstance;
    }

    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        roro.stellar.Stellar.INSTANCE.addBinderReceivedListener(() -> RemovalActivity.retryPending(this), null);

        
        
        
        
        
        
        
        

        
        
        
        
        
        CrashLogger.install(this);
        CrashLogger.event("YouLongApp.onCreate 开始");

        // ==================================================================
        
        // ------------------------------------------------------------------
        
        
        
        
        
        
        //
        
        
        
        
        // ==================================================================
        CrashLogger.event("开源版：跳过签名校验（无内置签名指纹）");

        
        
        //   application = this       → attachApplication(this)
        
        //   BootStartNotifications.createChannel(this)
        
        
        
        //
        
        try {
            CrashLogger.event("StellarApplication 初始化开始");
            roro.stellar.manager.StellarApplication.Companion.attachApplication(this);
            roro.stellar.manager.StellarApplication.Companion.init(this);
            roro.stellar.manager.startup.notification.BootStartNotifications.INSTANCE.createChannel(this);
            CrashLogger.event("StellarApplication 初始化完成");
        } catch (Throwable tr) {
            android.util.Log.w("YouLongApp", "StellarApplication init failed", tr);
            CrashLogger.event("StellarApplication 初始化失败", tr);
        }

        // Shizuku protocol binder listener (dual-source privilege, 2026-10):
        // the built-in Stellar kernel and an installed Shizuku / Sui both speak the
        // Shizuku protocol; the latter is delivered through YlShizukuProvider and
        // announced here. Only a trace is written; nothing heavy runs in the callback.
        try {
            roro.stellar.shizuku.ShizukuCompat.INSTANCE.addBinderReceivedListener(() ->
                    CrashLogger.event("[特权来源] Shizuku 协议 Binder 已就绪"));
            roro.stellar.shizuku.ShizukuCompat.INSTANCE.addBinderDeadListener(() ->
                    CrashLogger.event("[特权来源] Shizuku 协议 Binder 已断开"));
            CrashLogger.event("[特权来源] 双来源路由已就绪：" + PrivRouter.describe(this));
        } catch (Throwable tr) {
            CrashLogger.event("[特权来源] 注册 Shizuku 监听失败", tr);
        }
        CrashLogger.event("YouLongApp.onCreate 结束");
    }
}
