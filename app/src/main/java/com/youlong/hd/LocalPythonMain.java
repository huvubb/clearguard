package com.youlong.hd;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Isolated app_process entry, invoked with application UID or an explicitly checked shell UID. */
@androidx.annotation.Keep
public final class LocalPythonMain {
    public static void main(String[] args) {
        try {
            if (args.length != 5) throw new IllegalArgumentException("package, working directory, script name, APK and native directory required");
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("");
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getMethod("systemMain").invoke(null);
            Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
            android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo();
            info.packageName = args[0]; info.uid = android.os.Process.myUid();
            info.dataDir = args[1]; info.targetSdkVersion = android.os.Build.VERSION.SDK_INT;
            info.sourceDir = args[3]; info.publicSourceDir = args[3]; info.nativeLibraryDir = args[4];
            android.content.res.AssetManager assets = android.content.res.AssetManager.class.getDeclaredConstructor().newInstance();
            int cookie = (Integer) android.content.res.AssetManager.class.getMethod("addAssetPath",String.class).invoke(assets,args[3]);
            if (cookie == 0) throw new IllegalArgumentException("cannot load runtime assets");
            File base = new File(args[1]).getCanonicalFile();
            if (!base.isDirectory()) throw new IllegalArgumentException("working directory missing");
            Python.start(new AndroidPlatform(new RuntimeApplication(system, base, assets, info)));
            String source = new String(readScript(), java.nio.charset.StandardCharsets.UTF_8);
            Python.getInstance().getModule("clearguard_runner").callAttr("run_source", source, args[2]);
        } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
    private static byte[] readScript() throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int size;
        while ((size = System.in.read(buffer)) != -1) {
            if (bytes.size() + size > 1024 * 1024) throw new java.io.IOException("script exceeds 1 MiB");
            bytes.write(buffer, 0, size);
        }
        return bytes.toByteArray();
    }
    private static final class RuntimeApplication extends Application {
        private final File base;
        private final android.content.res.AssetManager assets;
        private final android.content.pm.ApplicationInfo info;
        private final SharedPreferences prefs = new MemoryPreferences();
        RuntimeApplication(Context context, File base, android.content.res.AssetManager assets, android.content.pm.ApplicationInfo info) {
            attachBaseContext(context); this.base = base; this.assets = assets; this.info = info;
        }
        @Override public android.content.res.AssetManager getAssets() { return assets; }
        @Override public android.content.pm.ApplicationInfo getApplicationInfo() { return info; }
        @Override public String getPackageName() { return info.packageName; }
        @Override public String getPackageCodePath() { return info.sourceDir; }
        @Override public ClassLoader getClassLoader() { return LocalPythonMain.class.getClassLoader(); }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { File dir = new File(base, "files"); dir.mkdirs(); return dir; }
        @Override public File getCacheDir() { File dir = new File(base, "cache"); dir.mkdirs(); return dir; }
        @Override public File getCodeCacheDir() { return getCacheDir(); }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
    }
    private static final class MemoryPreferences implements SharedPreferences, SharedPreferences.Editor {
        private final Map<String, Object> data = new HashMap<>();
        public Map<String, ?> getAll() { return new HashMap<>(data); }
        public String getString(String key, String value) { return (String) data.getOrDefault(key, value); }
        @SuppressWarnings("unchecked") public Set<String> getStringSet(String key, Set<String> value) { return (Set<String>) data.getOrDefault(key, value); }
        public int getInt(String key, int value) { return (Integer) data.getOrDefault(key, value); }
        public long getLong(String key, long value) { return (Long) data.getOrDefault(key, value); }
        public float getFloat(String key, float value) { return (Float) data.getOrDefault(key, value); }
        public boolean getBoolean(String key, boolean value) { return (Boolean) data.getOrDefault(key, value); }
        public boolean contains(String key) { return data.containsKey(key); }
        public Editor edit() { return this; }
        public Editor putString(String key, String value) { data.put(key, value); return this; }
        public Editor putStringSet(String key, Set<String> value) { data.put(key, value); return this; }
        public Editor putInt(String key, int value) { data.put(key, value); return this; }
        public Editor putLong(String key, long value) { data.put(key, value); return this; }
        public Editor putFloat(String key, float value) { data.put(key, value); return this; }
        public Editor putBoolean(String key, boolean value) { data.put(key, value); return this; }
        public Editor remove(String key) { data.remove(key); return this; }
        public Editor clear() { data.clear(); return this; }
        public boolean commit() { return true; }
        public void apply() { }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
    }
}
