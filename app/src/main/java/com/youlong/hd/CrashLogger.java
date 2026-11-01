package com.youlong.hd;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;


public final class CrashLogger {

    private static final String TAG = "CrashLogger";

    
    private static final String FILE_LAST_CRASH = "last_crash.txt";
    
    private static final String FILE_HISTORY = "crash_history.txt";

    
    private static final int MAX_EVENTS = 240;
    
    private static final int MAX_STACK_LINES = 200;
    
    private static final int MAX_HISTORY = 5;

    private static final SimpleDateFormat TIME_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    
    private static String now() {
        synchronized (TIME_FMT) {
            return TIME_FMT.format(new Date());
        }
    }

    
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();

    private static volatile Application sApp;
    private static volatile boolean sInstalled;
    private static volatile Thread.UncaughtExceptionHandler sPreviousHandler;
    
    private static volatile boolean sWriting;

    private CrashLogger() {
    }

    // ==================================================================
    
    // ==================================================================

    
    public static void install(Application app) {
        if (app == null) return;
        sApp = app;
        if (sInstalled) return;
        synchronized (CrashLogger.class) {
            if (sInstalled) return;
            sInstalled = true;
        }

        
        try {
            app.registerActivityLifecycleCallbacks(new ActivityCallbacks());
        } catch (Throwable tr) {
            Log.w(TAG, "registerActivityLifecycleCallbacks failed", tr);
        }

        sPreviousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Handler());
        event("CrashLogger 已安装（上一个处理器="
                + (sPreviousHandler == null ? "无" : sPreviousHandler.getClass().getName()) + "）");
    }

    // ==================================================================
    
    // ==================================================================

    
    public static void event(String message) {
        String line = "[" + now() + "] "
                + "tid=" + Process.myTid() + " " + message;
        synchronized (EVENTS) {
            EVENTS.addLast(line);
            while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
        }
        Log.i(TAG, message);
    }

    
    public static void event(String message, Throwable tr) {
        event(message + " -> " + (tr == null ? "null" : tr.toString()));
        if (tr != null) {
            for (StackTraceElement el : tr.getStackTrace()) {
                event("    at " + el);
            }
        }
    }

    // ==================================================================
    
    // ==================================================================

    private static final class Handler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            try {
                writeCrash(thread, ex);
            } catch (Throwable inner) {
                Log.e(TAG, "写崩溃日志本身又失败了", inner);
            }
            
            
            Thread.UncaughtExceptionHandler prev = sPreviousHandler;
            if (prev != null && prev != this) {
                prev.uncaughtException(thread, ex);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        }
    }

    private static void writeCrash(Thread thread, Throwable ex) {
        synchronized (CrashLogger.class) {
            if (sWriting) return;
            sWriting = true;
        }
        try {
            event("*** 崩溃：线程 " + (thread == null ? "?" : thread.getName())
                    + " " + (ex == null ? "null" : ex.toString()));

            StringBuilder sb = new StringBuilder(8192);
            header(sb, "崩溃");
            sb.append("崩溃线程: ").append(thread == null ? "?" : thread.getName()).append('\n');
            sb.append("异常类型: ").append(ex == null ? "null" : ex.getClass().getName()).append('\n');
            sb.append("异常信息: ").append(ex == null ? "" : String.valueOf(ex.getMessage())).append('\n');

            sb.append("\n--------- 异常堆栈 ---------\n");
            if (ex != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ex.printStackTrace(pw);
                pw.flush();
                sb.append(clipLines(sw.toString(), MAX_STACK_LINES));
            } else {
                sb.append("(无异常对象)\n");
            }

            sb.append("\n").append(snapshotEvents());
            sb.append("\n--------- 日志文件位置 ---------\n");
            sb.append(new File(crashDir(sApp), FILE_LAST_CRASH).getAbsolutePath()).append('\n');

            String text = sb.toString();
            writeFile(lastCrashFile(sApp), text);
            appendHistory(sApp, text);
            Log.e(TAG, "崩溃日志已写入 " + lastCrashFile(sApp).getAbsolutePath());
        } finally {
            sWriting = false;
        }
    }

    // ==================================================================
    
    // ==================================================================

    
    public static boolean hasCrash(Context ctx) {
        File f = lastCrashFile(ctx);
        return f != null && f.isFile() && f.length() > 0;
    }

    
    public static String readCrash(Context ctx) {
        File f = lastCrashFile(ctx);
        if (f == null || !f.isFile()) return "";
        return readFile(f, 64 * 1024);
    }

    
    public static String buildDiagnostics(Context ctx) {
        StringBuilder sb = new StringBuilder(16384);
        header(sb, hasCrash(ctx) ? "诊断报告（含最近一次崩溃）" : "诊断报告（本次运行暂无崩溃）");
        sb.append(snapshotEvents());

        if (hasCrash(ctx)) {
            sb.append("\n--------- 最近一次崩溃 ---------\n");
            sb.append(readCrash(ctx));
        } else {
            sb.append("\n（未捕获到崩溃）\n");
        }

        sb.append("\n--------- 崩溃历史（最近 ").append(MAX_HISTORY).append(" 次）---------\n");
        String hist = readFile(historyFile(ctx), 64 * 1024);
        sb.append(hist.isEmpty() ? "（无）\n" : hist);

        sb.append("\n--------- 日志文件位置 ---------\n");
        sb.append(new File(crashDir(ctx), FILE_LAST_CRASH).getAbsolutePath()).append('\n');
        return sb.toString();
    }

    
    public static File dumpEventsToFile(Context ctx, String fileName, String extraHeader) {
        try {
            File dir = crashDir(ctx);
            if (!dir.exists() && !dir.mkdirs()) return null;
            File out = new File(dir, fileName);
            StringBuilder sb = new StringBuilder(16384);
            if (extraHeader != null && !extraHeader.isEmpty()) {
                sb.append(extraHeader).append('\n');
            }
            sb.append(snapshotEvents());
            FileOutputStream fos = new FileOutputStream(out, false);
            try {
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.flush();
            } finally {
                try { fos.close(); } catch (Exception ignored) {}
            }
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "写轨迹文件失败: " + fileName, t);
            return null;
        }
    }

    
    public static void clear(Context ctx) {
        deleteQuietly(lastCrashFile(ctx));
        deleteQuietly(historyFile(ctx));
        event("崩溃日志已被用户清除");
    }

    // ==================================================================
    
    // ==================================================================

    private static void header(StringBuilder sb, String kind) {
        sb.append("========== 澄盾 ClearGuard ").append(kind).append(" ==========\n");
        sb.append("时间: ").append(now()).append('\n');
        Application app = sApp;
        if (app != null) {
            sb.append("包名: ").append(app.getPackageName()).append('\n');
            try {
                android.content.pm.PackageInfo pi =
                        app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
                sb.append("版本: ").append(pi.versionName)
                        .append(" (").append(pi.versionCode).append(")\n");
            } catch (Throwable ignored) {
            }
        }
        sb.append("进程: ").append(Process.myPid()).append('\n');
        sb.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("系统: Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")")
                .append("  ABI=").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?")
                .append('\n');
        
        sb.append("签名校验: 开源版无内置签名指纹，未校验").append('\n');
        sb.append('\n');
    }

    private static String snapshotEvents() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("--------- 运行轨迹（最近 ").append(MAX_EVENTS).append(" 条）---------\n");
        synchronized (EVENTS) {
            if (EVENTS.isEmpty()) {
                sb.append("（无）\n");
            } else {
                for (String e : EVENTS) sb.append(e).append('\n');
            }
        }
        return sb.toString();
    }

    private static String clipLines(String text, int maxLines) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        if (lines.length <= maxLines) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) sb.append(lines[i]).append('\n');
        sb.append("... （省略 ").append(lines.length - maxLines).append(" 行）\n");
        return sb.toString();
    }

    
    private static File crashDir(Context ctx) {
        File dir = null;
        if (ctx != null) {
            try {
                dir = ctx.getExternalFilesDir(null);       // /sdcard/Android/data/<pkg>/files
            } catch (Throwable ignored) {
            }
            if (dir == null) {
                try {
                    dir = ctx.getFilesDir();
                } catch (Throwable ignored) {
                }
            }
            if (dir == null) {
                try {
                    dir = ctx.getCacheDir();
                } catch (Throwable ignored) {
                }
            }
        }
        File target = (dir == null) ? new File("/data/local/tmp") : new File(dir, "crash");
        //noinspection ResultOfMethodCallIgnored
        if (!target.isDirectory()) target.mkdirs();
        return target;
    }

    private static File lastCrashFile(Context ctx) {
        return new File(crashDir(ctx), FILE_LAST_CRASH);
    }

    private static File historyFile(Context ctx) {
        return new File(crashDir(ctx), FILE_HISTORY);
    }

    private static void appendHistory(Context ctx, String text) {
        try {
            File f = historyFile(ctx);
            String old = readFile(f, 256 * 1024);
            String merged = text + "\n\n" + old;
            
            String[] parts = merged.split("========== 澄盾 ClearGuard 崩溃 ==========");
            StringBuilder sb = new StringBuilder(parts.length * 64);
            int kept = 0;
            for (int i = 1; i < parts.length && kept < MAX_HISTORY; i++, kept++) {
                sb.append("========== 澄盾 ClearGuard 崩溃 ==========").append(parts[i]);
            }
            writeFile(f, sb.toString());
        } catch (Throwable tr) {
            Log.w(TAG, "appendHistory failed", tr);
        }
    }

    private static void writeFile(File f, String text) {
        if (f == null || text == null) return;
        Writer w = null;
        try {
            
            FileOutputStream fos = new FileOutputStream(f, false);
            w = new OutputStreamWriter(fos, "UTF-8");
            w.write(text);
            w.flush();
            Log.i(TAG, "written " + f.getAbsolutePath() + " (" + f.length() + " bytes)");
        } catch (Throwable tr) {
            Log.e(TAG, "writeFile failed: " + f, tr);
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String readFile(File f, int maxBytes) {
        if (f == null || !f.isFile()) return "";
        BufferedReader r = null;
        try {
            FileInputStream fis = new FileInputStream(f);
            r = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder(4096);
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.length() > maxBytes) break;
            }
            return sb.toString();
        } catch (Throwable tr) {
            Log.e(TAG, "readFile failed: " + f, tr);
            return "";
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f == null) return;
        try {
            if (f.isFile() && !f.delete()) {
                
                writeFile(f, "");
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================================================================
    
    // ==================================================================

    private static final class ActivityCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity a, Bundle b) {
            event("Activity.onCreate  " + a.getClass().getName());
        }

        @Override
        public void onActivityStarted(Activity a) {
            event("Activity.onStart   " + a.getClass().getSimpleName());
        }

        @Override
        public void onActivityResumed(Activity a) {
            event("Activity.onResume  " + a.getClass().getSimpleName());
        }

        @Override
        public void onActivityPaused(Activity a) {
            event("Activity.onPause   " + a.getClass().getSimpleName());
        }

        @Override
        public void onActivityStopped(Activity a) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity a, Bundle b) {
        }

        @Override
        public void onActivityDestroyed(Activity a) {
            event("Activity.onDestroy " + a.getClass().getSimpleName());
        }
    }
}
