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
            if (args.length != 3) throw new IllegalArgumentException("package, working directory and script name required");
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("");
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getMethod("systemMain").invoke(null);
            Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
            Context packageContext = system.createPackageContext(args[0], Context.CONTEXT_IGNORE_SECURITY);
            File base = new File(args[1]).getCanonicalFile();
            if (!base.isDirectory()) throw new IllegalArgumentException("working directory missing");
            Python.start(new AndroidPlatform(new RuntimeApplication(packageContext, base)));
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
        private final SharedPreferences prefs = new MemoryPreferences();
        RuntimeApplication(Context context, File base) { attachBaseContext(context); this.base = base; }
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
