package com.youlong.hd;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


public final class PrivRouter {

    private static final String TAG = "YlPrivRouter";

    
    public static final int SOURCE_AUTO = 0;
    
    public static final int SOURCE_BUILTIN = 1;
    
    public static final int SOURCE_SHIZUKU = 2;

    static final String PREFS = "shield_prefs";
    static final String KEY_SOURCE = "priv_source";
    static final String KEY_ALLOW_EXTERNAL = "priv_allow_external_shizuku";

    private PrivRouter() {}

    // ======================================================================
    
    // ======================================================================

    private static SharedPreferences prefs(Context ctx) {
        Context c = ctx != null ? ctx : YouLongApp.instance();
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getSource(Context ctx) {
        try {
            int v = prefs(ctx).getInt(KEY_SOURCE, SOURCE_SHIZUKU);
            return (v == SOURCE_BUILTIN || v == SOURCE_SHIZUKU || v == SOURCE_AUTO) ? v : SOURCE_SHIZUKU;
        } catch (Throwable t) {
            return SOURCE_SHIZUKU;
        }
    }

    public static void setSource(Context ctx, int source) {
        try {
            prefs(ctx).edit().putInt(KEY_SOURCE, source).apply();
        } catch (Throwable t) {
            Log.w(TAG, "保存特权来源失败", t);
        }
    }

    public static String sourceName(Context ctx) {
        switch (getSource(ctx)) {
            case SOURCE_BUILTIN:  return "内置内核";
            case SOURCE_SHIZUKU:  return "系统 Shizuku";
            default:              return "自动";
        }
    }

    
    public static boolean isExternalShizukuAllowed(Context ctx) {
        try {
            return prefs(ctx).getBoolean(KEY_ALLOW_EXTERNAL, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setExternalShizukuAllowed(Context ctx, boolean allow) {
        try {
            prefs(ctx).edit().putBoolean(KEY_ALLOW_EXTERNAL, allow).apply();
            CrashLogger.event("[特权来源] 外部 Shizuku 投递许可 = " + allow);
        } catch (Throwable t) {
            Log.w(TAG, "保存外部 Shizuku 许可失败", t);
        }
    }

    // ======================================================================
    
    // ======================================================================

    
    public static boolean builtinAlive() {
        try {
            return roro.stellar.Stellar.INSTANCE.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static boolean shizukuAlive() {
        return ShizukuBackend.isBinderAlive();
    }

    
    public static int activeSource(Context ctx) {
        int chosen = getSource(ctx);
        boolean builtin = builtinAlive();
        boolean shizuku = shizukuAlive();

        if (chosen == SOURCE_SHIZUKU) {
            return SOURCE_SHIZUKU;
        }
        if (chosen == SOURCE_BUILTIN) {
            return SOURCE_BUILTIN;
        }
        // AUTO: 优先系统 Shizuku
        if (shizuku) return SOURCE_SHIZUKU;
        if (builtin) return SOURCE_BUILTIN;
        return SOURCE_SHIZUKU;
    }

    public static String activeName(Context ctx) {
        return activeSource(ctx) == SOURCE_SHIZUKU ? "系统 Shizuku" : "内置内核";
    }

    
    public static boolean isBinderAlive(Context ctx) {
        return activeSource(ctx) == SOURCE_SHIZUKU ? shizukuAlive() : builtinAlive();
    }

    
    public static boolean hasPermission(Context ctx) {
        try {
            if (activeSource(ctx) == SOURCE_SHIZUKU) return ShizukuBackend.hasPermission();
            return roro.stellar.Stellar.INSTANCE.checkSelfPermission("stellar");
        } catch (Throwable t) {
            Log.w(TAG, "权限查询失败", t);
            return false;
        }
    }

    // ======================================================================
    
    // ======================================================================

    
    public static Process newProcess(Context ctx, String[] cmd, String[] env, String dir) {
        if (activeSource(ctx) == SOURCE_SHIZUKU) {
            return ShizukuBackend.newProcess(cmd, env, dir);
        }
        Process p = roro.stellar.Stellar.INSTANCE.newProcess(cmd, env, dir);
        if (p == null) throw new IllegalStateException("内置内核未返回进程（可能未授权）");
        return p;
    }

    
    public static void requestReconnect(Context ctx) {
        try {
            if (ctx == null) return;
            android.content.Intent i = new android.content.Intent(
                    "roro.stellar.intent.action.REQUEST_BINDER");
            i.setComponent(new android.content.ComponentName(ctx.getPackageName(),
                    "roro.stellar.manager.receiver.StellarReceiver"));
            ctx.sendBroadcast(i);
            Log.i(TAG, "已请求内置服务端重新投递 Binder");
        } catch (Throwable t) {
            Log.w(TAG, "请求重投 Binder 失败", t);
        }
    }

    
    public static String describe(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("来源=").append(activeName(ctx))
          .append("(设定:").append(sourceName(ctx)).append(')')
          .append(" 内置=").append(builtinAlive())
          .append(" Shizuku=").append(shizukuAlive());
        try {
            sb.append(" 外部许可=").append(isExternalShizukuAllowed(ctx));
            if (ctx != null) {
                sb.append(" Shizuku已安装=").append(ShizukuBackend.isManagerInstalled(ctx));
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }
}
