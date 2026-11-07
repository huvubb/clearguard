package com.youlong.hd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit native interaction is required to run arbitrary commands. Never exported. */
public final class TerminalActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Process process;
    private volatile boolean cancelled;
    private Button run;
    private EditText command;
    private Spinner shell;
    private Spinner privilege;
    private TextView output;

    @Override public void onCreate(Bundle state) {
        setTheme(R.style.Theme_ClearGuard_Native);super.onCreate(state);
        NativeUi.Page page=new NativeUi.Page(this,"命令终端","明确权限，确认后执行。","默认以应用自身权限执行。CMD／PowerShell 只转译常用命令；Bash 模式使用 Android sh 兼容语法。每次运行是独立会话，最长 30 秒。命令可能修改设备数据，执行前会展示实际命令。危险脚本预检查只覆盖澄盾终端，无法覆盖全系统。");
        LinearLayout settings=page.section("执行环境");
        shell=new Spinner(this);shell.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Android sh","Bash 兼容语法","CMD 常用转译","PowerShell 常用转译"}));NativeUi.field(this,settings,"命令语言",shell);
        privilege=new Spinner(this);privilege.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"应用权限 · user","Shizuku · shell","超级用户 · Root"}));NativeUi.field(this,settings,"执行权限",privilege);
        LinearLayout editor=page.section("命令编辑");command=new EditText(this);command.setHint("输入命令，例如 id");command.setTypeface(Typeface.MONOSPACE);command.setMinLines(4);command.setGravity(android.view.Gravity.TOP);command.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);editor.addView(command,new LinearLayout.LayoutParams(-1,-2));
        run=NativeUi.action(this,editor,"确认并执行  ↗",this::confirmRun,true);NativeUi.action(this,editor,"停止当前命令",this::stopProcess,false);
        LinearLayout result=page.section("执行输出");output=new TextView(this);output.setTypeface(Typeface.MONOSPACE);output.setText("等待输入命令。\n执行结果将在这里显示。");NativeUi.report(this,result,output,true);NativeUi.action(this,result,"复制输出",()->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("terminal",output.getText())),false);page.show();
    }

    private void confirmRun() {
        String text = command.getText().toString().trim();
        if (text.isEmpty() || text.length() > 8192) { output.setText("请输入 1–8192 字符的命令。"); return; }
        final boolean adb = privilege.getSelectedItemPosition() == 1;
        final boolean rootMode = privilege.getSelectedItemPosition() == 2;
        if (adb && (!PrivRouter.isBinderAlive(this) || !PrivRouter.hasPermission(this))) {
            output.setText("特权服务未就绪，请先激活并授权。");
            startActivity(new Intent(this, PrivilegeActivity.class)); return;
        }
        final String translated;
        try { translated = TerminalCommand.translate(shell.getSelectedItemPosition(), text); }
        catch (IllegalArgumentException error) { output.setText(error.getMessage()); return; }
        String blocked = ScriptGuard.check(translated);
        if (blocked != null) { output.setText("主动防护已阻止：" + blocked); return; }
        new AlertDialog.Builder(this).setTitle("确认在设备上执行")
                .setMessage("权限：" + (rootMode ? "Root（启动时核验 UID 0）" : adb ? "ADB shell UID 2000" : "应用 UID " + android.os.Process.myUid()) + "\n\n输入：\n" + text + "\n\n实际执行（Android sh）：\n" + translated)
                .setNegativeButton("取消", null)
                .setPositiveButton("执行", (dialog, which) -> execute(rootMode ? new String[]{"su", "-c", "[ $(id -u) = 0 ] || exit 126; " + translated} : new String[]{"/system/bin/sh", "-c", translated}, adb)).show();
    }

    private void execute(String[] args, boolean adb) {
        if (!run.isEnabled()) return;
        run.setEnabled(false); shell.setEnabled(false); privilege.setEnabled(false); cancelled = false; output.setText("执行中…");
        executor.execute(() -> {
            StringBuilder result = new StringBuilder();
            Process active = null;
            try {
                active = adb ? PrivRouter.newProcess(this, LocalRunCommand.adbOnly(args), null, null)
                        : new ProcessBuilder(args).directory(getFilesDir()).start();
                process = active;
                if (cancelled) active.destroy();
                final Process running = active;
                Thread stdout = new Thread(() -> drain(running.getInputStream(), result), "terminal-stdout");
                Thread stderr = new Thread(() -> drain(running.getErrorStream(), result), "terminal-stderr");
                stdout.start(); stderr.start();
                long deadline = android.os.SystemClock.elapsedRealtime() + 30_000;
                boolean done = false;
                while (!done && !cancelled && android.os.SystemClock.elapsedRealtime() < deadline) {
                    try { int code = active.exitValue(); append(result, "\n[退出状态 " + code + "]"); done = true; }
                    catch (IllegalThreadStateException pending) { Thread.sleep(100); }
                }
                if (!done) { active.destroy(); append(result, cancelled ? "\n[用户停止]" : "\n[运行超时，已停止]"); }
                stdout.join(1000); stderr.join(1000);
            } catch (Exception error) {
                append(result, "\n执行失败：" + error.getMessage());
            } finally {
                if (active != null) active.destroy();
                process = null;
                final String content;
                synchronized (result) { content = result.toString(); }
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { output.setText(content); run.setEnabled(true); shell.setEnabled(true); privilege.setEnabled(true); } });
            }
        });
    }

    private static void append(StringBuilder result, String text) {
        synchronized (result) {
            int remaining = 256 * 1024 - result.length();
            if (remaining > 0) result.append(text, 0, Math.min(text.length(), remaining));
        }
    }

    private static void drain(InputStream input, StringBuilder result) {
        try (java.io.Reader reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048]; int size;
            while ((size = reader.read(buffer)) != -1) append(result, new String(buffer, 0, size));
        } catch (Exception ignored) { /* Process shutdown closes its streams. */ }
    }

    private void stopProcess() { cancelled = true; Process active = process; if (active != null) active.destroy(); }
    @Override protected void onDestroy() { stopProcess(); executor.shutdownNow(); super.onDestroy(); }
}
