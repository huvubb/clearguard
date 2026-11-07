package com.youlong.hd;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import roro.stellar.shizuku.ShizukuCompat;


public final class ShizukuBackend {

    private static final String TAG = "YlShizuku";

    
    public static final String BINDER_DESCRIPTOR = "moe.shizuku.server.IShizukuService";

    
    public static final String PACKAGE_SHIZUKU = "moe.shizuku.privileged.api";
    
    public static final String PACKAGE_SHIZUKU_DEBUG = "moe.shizuku.privileged.api.debug";

    private ShizukuBackend() {}

    
    static boolean isKnownManagerPackage(String pkg) {
        if (pkg == null) return false;
        return PACKAGE_SHIZUKU.equals(pkg) || PACKAGE_SHIZUKU_DEBUG.equals(pkg);
    }

    
    public static boolean isManagerInstalled(Context ctx) {
        if (ctx == null) return false;
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : new String[]{PACKAGE_SHIZUKU, PACKAGE_SHIZUKU_DEBUG}) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    // ======================================================================
    
    // ======================================================================

    private static IShizukuService service() {
        try {
            IBinder b = ShizukuCompat.INSTANCE.getBinder();
            if (b == null || !b.pingBinder()) return null;
            return IShizukuService.Stub.asInterface(b);
        } catch (Throwable t) {
            Log.w(TAG, "取 Shizuku 服务失败", t);
            return null;
        }
    }

    
    public static boolean isBinderAlive() {
        try {
            IBinder b = ShizukuCompat.INSTANCE.getBinder();
            return b != null && b.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static boolean hasPermission() {
        try {
            return ShizukuCompat.INSTANCE.checkSelfPermission() == 0;
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku 权限查询失败", t);
            return false;
        }
    }

    
    public static void requestPermission(int requestCode) {
        try {
            ShizukuCompat.INSTANCE.requestPermission(requestCode);
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku 授权请求失败", t);
        }
    }

    public static boolean shouldShowRequestPermissionRationale() {
        try {
            return ShizukuCompat.INSTANCE.shouldShowRequestPermissionRationale();
        } catch (Throwable t) {
            return false;
        }
    }

    
    public static int serverUid() {
        try {
            return ShizukuCompat.INSTANCE.getUid();
        } catch (Throwable t) {
            return -1;
        }
    }

    
    public static String secontext() {
        try {
            return ShizukuCompat.INSTANCE.getSELinuxContext();
        } catch (Throwable t) {
            return null;
        }
    }

    // ======================================================================
    
    // ======================================================================

    
    private static volatile IClearGuardShell shellService;
    private static final Object SHELL_LOCK=new Object();
    private static final android.os.Binder SESSION=new android.os.Binder();
    private static final moe.shizuku.server.IShizukuServiceConnection CONNECTION=new moe.shizuku.server.IShizukuServiceConnection.Stub(){
        @Override public void connected(android.os.IBinder binder){synchronized(SHELL_LOCK){shellService=IClearGuardShell.Stub.asInterface(binder);SHELL_LOCK.notifyAll();}}
        @Override public void died(){synchronized(SHELL_LOCK){shellService=null;SHELL_LOCK.notifyAll();}}
    };
    public static Process newProcess(String[] cmd,String[] env,String dir){
        IShizukuService service=service();if(service==null)throw new IllegalStateException("Shizuku 未连接，请在权限服务中启动并授权");
        if(!hasPermission())throw new SecurityException("Shizuku 尚未授权，请先在权限服务中申请授权");
        try{
            IClearGuardShell shell=shellService;
            if(shell==null||!shell.asBinder().pingBinder()){
                android.os.Bundle args=new android.os.Bundle();Context context=YouLongApp.instance();
                args.putParcelable("shizuku:user-service-arg-component",new android.content.ComponentName(context,ShellUserService.class));
                args.putString("shizuku:user-service-arg-tag","clearguard-command-v1");args.putString("shizuku:user-service-arg-process-name","shell");args.putInt("shizuku:user-service-arg-version-code",BuildConfig.VERSION_CODE);args.putBoolean("shizuku:user-service-arg-daemon",false);args.putBoolean("shizuku:user-service-arg-debuggable",BuildConfig.DEBUG);
                service.addUserService(CONNECTION,args);
                if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("正在准备命令服务，请稍后在后台重试");
                long end=android.os.SystemClock.elapsedRealtime()+15000;
                synchronized(SHELL_LOCK){while(shellService==null&&android.os.SystemClock.elapsedRealtime()<end)SHELL_LOCK.wait(Math.max(1,end-android.os.SystemClock.elapsedRealtime()));shell=shellService;}
                if(shell==null)throw new IllegalStateException("Shizuku 命令服务启动超时，请重新启动 Shizuku 后重试");
            }
            int uid=shell.uid();if(uid!=2000&&uid!=0)throw new SecurityException("命令服务身份异常：UID "+uid);
            IRemoteProcess remote=IRemoteProcess.Stub.asInterface(shell.start(cmd,env,dir==null?"/":dir,SESSION));if(remote==null)throw new IllegalStateException("命令服务没有返回进程");return new ShizukuProcess(remote);
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("命令服务等待已取消",interrupted);}catch(RemoteException error){shellService=null;throw new IllegalStateException("Shizuku 命令服务通信失败："+error.getMessage(),error);}
    }
    
    static final class ShizukuProcess extends Process {

        private final IRemoteProcess remote;
        private OutputStream os;
        private InputStream is;
        private InputStream es;

        ShizukuProcess(IRemoteProcess remote) {
            this.remote = remote;
        }

        @Override
        public OutputStream getOutputStream() {
            if (os == null) {
                try {
                    os = new ParcelFileDescriptor.AutoCloseOutputStream(remote.getOutputStream());
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            return os;
        }

        @Override
        public InputStream getInputStream() {
            if (is == null) {
                try {
                    is = new ParcelFileDescriptor.AutoCloseInputStream(remote.getInputStream());
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            return is;
        }

        @Override
        public InputStream getErrorStream() {
            try {
                if(es==null)es=new ParcelFileDescriptor.AutoCloseInputStream(remote.getErrorStream());
                return es;
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int waitFor() throws InterruptedException {
            try {
                return remote.waitFor();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                return remote.waitForTimeout(timeout, unit.toString());
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int exitValue() {
            try {
                if(remote.alive())throw new IllegalThreadStateException("进程仍在运行");
                return remote.exitValue();
            } catch (RemoteException e) {
                throw new IllegalStateException("命令进程连接已断开: " + e, e);
            }
        }

        @Override
        public void destroy() {
            try {
                remote.destroy();
            } catch (RemoteException ignored) {
            }
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            try {
                return remote.alive();
            } catch (RemoteException e) {
                return false;
            }
        }
    }
}
