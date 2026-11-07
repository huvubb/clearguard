package com.youlong.hd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Local files run only after native confirmation, under the explicitly selected UID. */
public final class LocalRunActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Spinner type, privilege;
    private EditText path, prompt, source, tokens;
    private TextView output;
    private Button run, fetch, choose;
    private volatile boolean cancelled;
    private volatile Process process;
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);
        NativeUi.Page page=new NativeUi.Page(this,"本地模型与脚本","把推理和运行留在设备上。","模型仅在本机 CPU 生成文本，不自动执行命令。脚本可读写所选权限能够访问的数据，请只运行可信内容。每次运行最长 10 分钟。Python 支持内置 Android ARM 运行时，第三方包需兼容环境。模型下载可能消耗数 GB 空间及流量，下载后仍需手动启动。");
        LinearLayout config=page.section("运行方式");config.addView(NativeUi.label(this,"运行类型",12,0xFF526358));type=spinner(config,new String[]{"sh 脚本","Python 3.11","GGUF · 本机 CPU"});config.addView(NativeUi.label(this,"执行权限",12,0xFF526358));privilege=spinner(config,new String[]{"应用权限 · user","Shizuku · shell"});
        LinearLayout file=page.section("本地文件");path=edit(file,"选择文件，或填写 Android 绝对路径",false);choose=NativeUi.action(this,file,"导入本地文件",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),81),false);
        LinearLayout model=page.section("模型输入 · GGUF");prompt=edit(model,"写下你想让模型处理的内容",true);prompt.setMinLines(3);tokens=edit(model,"最大生成长度（1–4096 tokens）",false);tokens.setText("256");tokens.setInputType(2);
        LinearLayout download=page.section("下载模型");source=edit(download,"Hugging Face 文件链接或 Ollama 模型名",false);fetch=NativeUi.action(this,download,"下载到本机",this::confirmDownload,false);
        LinearLayout actions=page.section("运行控制");run=NativeUi.action(this,actions,"确认并运行  ↗",this::confirmRun,true);NativeUi.action(this,actions,"停止运行／取消下载",this::cancel,false);
        LinearLayout result=page.section("运行输出");output=new TextView(this);output.setTypeface(android.graphics.Typeface.MONOSPACE);output.setText("等待选择本地文件。\n模型输出与运行记录会显示在这里。");NativeUi.report(this,result,output,true);page.show();
    }
    private Spinner spinner(LinearLayout root, String[] labels) {
        Spinner widget = new Spinner(this); widget.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels)); root.addView(widget); return widget;
    }
    private EditText edit(LinearLayout root, String hint, boolean multiline) {
        EditText widget = new EditText(this); widget.setHint(hint); widget.setSingleLine(!multiline); root.addView(widget); return widget;
    }
    private File modelDirectory() throws IOException {
        File external = getExternalFilesDir(null);
        if (external == null) throw new IOException("外部应用存储不可用");
        File directory = new File(external, "models");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建模型目录");
        return directory;
    }
    private void busy(boolean value) {
        run.setEnabled(!value); fetch.setEnabled(!value); choose.setEnabled(!value); type.setEnabled(!value); privilege.setEnabled(!value);
    }
    private void show(String text) { runOnUiThread(() -> { if (!isDestroyed()) output.setText(text); }); }
    private void confirmDownload() {
        String request = source.getText().toString().trim();
        new AlertDialog.Builder(this).setTitle("下载到本机")
                .setMessage(request + "\n\n模型可能占用数 GB 流量和空间。下载后校验 GGUF 文件头；Ollama 还校验清单 SHA-256。不会自动运行。")
                .setNegativeButton("取消",null).setPositiveButton("下载",(d,w) -> {
                    cancelled = false; busy(true); output.setText("解析模型下载地址…");
                    executor.execute(() -> {
                        try {
                            File model = ModelDownload.download(request, modelDirectory(), () -> cancelled, this::show);
                            runOnUiThread(() -> { if (!isDestroyed()) { path.setText(model.getAbsolutePath()); type.setSelection(2); } });
                        } catch (Exception error) { show("下载失败：" + error.getMessage()); }
                        finally { runOnUiThread(() -> { if (!isDestroyed()) busy(false); }); }
                    });
                }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 81 || result != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData(); final boolean model = type.getSelectedItemPosition() == 2;
        cancelled = false; busy(true); output.setText("复制到本机应用存储…");
        executor.execute(() -> {
            File file = null;
            try {
                File directory = model ? modelDirectory() : new File(getFilesDir(),"scripts");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建导入目录");
                file = File.createTempFile("local-",model ? ".gguf" : ".script",directory);
                long total = 0, limit = model ? ModelDownload.MAX_BYTES : 1024 * 1024;
                try (InputStream input = getContentResolver().openInputStream(uri); OutputStream target = new FileOutputStream(file)) {
                    if (input == null) throw new IOException("无法打开文件");
                    byte[] buffer = new byte[65536]; int size;
                    while ((size = input.read(buffer)) != -1) {
                        total += size;
                        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("已取消导入");
                        if (total > limit || directory.getUsableSpace() < size + 16 * 1024 * 1024) throw new IOException("文件过大或空间不足");
                        target.write(buffer,0,size);
                    }
                }
                if (model) validateGguf(file);
                final String imported = file.getAbsolutePath(); file = null;
                runOnUiThread(() -> { if (!isDestroyed()) { path.setText(imported); output.setText("已导入到 " + imported); } });
            } catch (Exception error) { show("导入失败：" + error.getMessage()); }
            finally { if (file != null) file.delete(); runOnUiThread(() -> { if (!isDestroyed()) busy(false); }); }
        });
    }
    private static void validateGguf(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            if (input.readInt() != 0x47475546) throw new IOException("不是 GGUF 模型文件");
        }
    }
    private void confirmRun() {
        final int selected = type.getSelectedItemPosition(); final boolean adb = privilege.getSelectedItemPosition() == 1;
        final File file = new File(path.getText().toString().trim());
        final String[] args; final byte[] script;
        try {
            if (!file.isAbsolute()) throw new IOException("请选择文件或填写 Android 绝对路径");
            if (selected < 2) {
                try (InputStream input = new FileInputStream(file); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int size;
                    while ((size = input.read(buffer)) != -1) {
                        if (bytes.size() + size > 1024 * 1024) throw new IOException("脚本超过 1 MiB");
                        bytes.write(buffer,0,size);
                    }
                    script = bytes.toByteArray();
                }
                if(selected==0){String reason=ScriptGuard.check(new String(script,StandardCharsets.UTF_8));if(reason!=null)throw new IOException("主动防护已阻止："+reason);}
                if (selected == 0) args = new String[]{"/system/bin/sh","-s"};
                else {
                    String working = adb ? "/data/local/tmp/clearguard-python-" + android.os.Process.myUid() : new File(getFilesDir(),"python-runner").getAbsolutePath();
                    args = new String[]{"/system/bin/sh","-c",
                            "export CLASSPATH=\"$1\"; export LD_LIBRARY_PATH=\"$2\"; mkdir -p \"$3\" || exit; exec /system/bin/app_process \"-Djava.library.path=$2\" /system/bin com.youlong.hd.LocalPythonMain \"$4\" \"$3\" \"$5\" \"$1\" \"$2\"",
                            "clearguard-python",getApplicationInfo().sourceDir,getApplicationInfo().nativeLibraryDir,working,getPackageName(),file.getAbsolutePath()};
                }
            } else {
                script = null; validateGguf(file);
                File runtime = new File(getApplicationInfo().nativeLibraryDir,"libclearguard_llama.so");
                if (!runtime.canExecute()) throw new IOException("内置 GGUF 推理需要 ARM64 设备及 64 位应用进程");
                args = LocalRunCommand.arguments(2,runtime.getAbsolutePath(),file.getAbsolutePath(),prompt.getText().toString(),Integer.parseInt(tokens.getText().toString()));
            }
            if (adb && (!PrivRouter.isBinderAlive(this) || !PrivRouter.hasPermission(this))) throw new IOException("请先激活并授权 ADB/Shizuku 特权服务");
        } catch (Exception error) { output.setText("无法运行：" + error.getMessage()); return; }
        new AlertDialog.Builder(this).setTitle("确认本地运行")
                .setMessage("文件：" + file + "\n权限：" + (adb ? "ADB shell UID 2000（启动时核验）" : "应用 user UID " + android.os.Process.myUid())
                        + "\n\n" + (selected == 2 ? "仅生成文本，不自动执行模型输出。" : "脚本可读写此 UID 有权访问的数据；不是额外安全沙箱。"))
                .setNegativeButton("取消",null).setPositiveButton("运行",(d,w) -> execute(args,script,adb)).show();
    }
    private void execute(String[] args, byte[] script, boolean adb) {
        cancelled = false; busy(true); output.setText("本机运行中…");
        executor.execute(() -> {
            StringBuffer text = new StringBuffer(); Process active = null;
            try {
                active = adb ? PrivRouter.newProcess(this,LocalRunCommand.adbOnly(args),null,null) : new ProcessBuilder(args).directory(getFilesDir()).start();
                process = active; if (cancelled) active.destroy();
                final Process running = active;
                Thread stdout = new Thread(() -> drain(running.getInputStream(),text));
                Thread stderr = new Thread(() -> drain(running.getErrorStream(),text)); stdout.start(); stderr.start();
                try (OutputStream stdin = active.getOutputStream()) { if (script != null) stdin.write(script); }
                long deadline = android.os.SystemClock.elapsedRealtime() + 600000; boolean done = false;
                while (!cancelled && android.os.SystemClock.elapsedRealtime() < deadline) {
                    try { int code = active.exitValue(); append(text,"\n[退出状态 " + code + "]"); done = true; break; }
                    catch (IllegalThreadStateException pending) { show(text.toString()); Thread.sleep(500); }
                }
                if (!done) { active.destroy(); append(text,cancelled ? "\n[用户停止]" : "\n[运行超时，已停止]"); }
                stdout.join(1000); stderr.join(1000);
            } catch (Exception error) { append(text,"\n运行失败：" + error.getMessage()); }
            finally { if (active != null) active.destroy(); process = null; show(text.toString()); runOnUiThread(() -> { if (!isDestroyed()) busy(false); }); }
        });
    }
    private static void append(StringBuffer buffer, String text) {
        synchronized (buffer) { int left = 256 * 1024 - buffer.length(); if (left > 0) buffer.append(text,0,Math.min(left,text.length())); }
    }
    private static void drain(InputStream input, StringBuffer text) {
        try (Reader reader = new InputStreamReader(input,StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096]; int size;
            while ((size = reader.read(buffer)) != -1) append(text,new String(buffer,0,size));
        } catch (IOException ignored) { }
    }
    private void cancel() { cancelled = true; Process active = process; if (active != null) active.destroy(); }
    @Override protected void onDestroy() { cancel(); executor.shutdownNow(); super.onDestroy(); }
}
