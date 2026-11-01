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
import android.widget.AdapterView;
import android.view.View;
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
    private EditText interpreter;
    private Spinner shell;
    private TextView output;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 36, 24, 24);
        root.setBackgroundColor(0xFFF5F7F2);
        TextView title = new TextView(this);
        title.setText("澄盾 · 原生终端"); title.setTextSize(24); title.setTextColor(0xFF172D25);
        root.addView(title);
        TextView tip = new TextView(this);
        tip.setText("使用你已授权的内置内核或 Shizuku 执行。命令可能改变设备数据，请确认内容。单次运行上限 30 秒。");
        tip.setTextColor(0xFF6C7B72); tip.setPadding(0, 16, 0, 16); root.addView(tip);
        shell = new Spinner(this);
        shell.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Android sh", "Bash", "CMD（需兼容运行时）", "PowerShell"}));
        root.addView(shell);
        interpreter = new EditText(this);
        interpreter.setSingleLine(true);
        interpreter.setHint("解释器名称或绝对路径");
        root.addView(interpreter, new LinearLayout.LayoutParams(-1, -2));
        TextView runtimeTip = new TextView(this);
        runtimeTip.setText("Bash / pwsh 需已安装 Android 兼容运行时；CMD 需 Windows 兼容运行时提供的 cmd.exe 或启动器。本应用不下载运行时，也不会把 Windows 命令假装成 sh 执行。每次执行为独立会话。");
        root.addView(runtimeTip);
        shell.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                interpreter.setText(getPreferences(MODE_PRIVATE).getString("shellPath" + position,
                        TerminalCommand.defaultExecutable(position)));
            }
        });
        command = new EditText(this);
        command.setHint("例如：id"); command.setTextColor(0xFF172D25);
        command.setTypeface(Typeface.MONOSPACE); command.setMinLines(3); command.setMaxLines(7);
        command.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(command, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout buttons = new LinearLayout(this);
        run = new Button(this); run.setText("执行命令");
        run.setOnClickListener(v -> confirmRun()); buttons.addView(run, new LinearLayout.LayoutParams(0, -2, 1));
        Button cancel = new Button(this); cancel.setText("停止");
        cancel.setOnClickListener(v -> stopProcess()); buttons.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(buttons);
        output = new TextView(this); output.setText("等待执行。"); output.setTextColor(0xFF172D25);
        output.setTextIsSelectable(true); output.setTypeface(Typeface.MONOSPACE); output.setTextSize(12);
        output.setPadding(12, 20, 12, 20);
        ScrollView scroll = new ScrollView(this); scroll.addView(output, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button copy = new Button(this); copy.setText("复制输出");
        copy.setOnClickListener(v -> ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("terminal", output.getText())));
        root.addView(copy);
        setContentView(root);
    }

    private void confirmRun() {
        String text = command.getText().toString().trim();
        if (text.isEmpty() || text.length() > 8192) { output.setText("请输入 1–8192 字符的命令。"); return; }
        if (!PrivRouter.isBinderAlive(this) || !PrivRouter.hasPermission(this)) {
            output.setText("特权服务未就绪，请先激活并授权。");
            startActivity(new Intent(this, PrivilegeActivity.class)); return;
        }
        final String[] args;
        final int mode = shell.getSelectedItemPosition();
        final String executable = interpreter.getText().toString().trim();
        try { args = TerminalCommand.arguments(mode, executable, text); }
        catch (IllegalArgumentException error) { output.setText(error.getMessage()); return; }
        new AlertDialog.Builder(this).setTitle("确认在设备上执行")
                .setMessage("解释器：" + executable + "\n\n" + text).setNegativeButton("取消", null)
                .setPositiveButton("执行", (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("shellPath" + mode, executable).apply();
                    execute(args);
                }).show();
    }

    private void execute(String[] args) {
        if (!run.isEnabled()) return;
        run.setEnabled(false); shell.setEnabled(false); interpreter.setEnabled(false); cancelled = false; output.setText("执行中…");
        executor.execute(() -> {
            StringBuilder result = new StringBuilder();
            Process active = null;
            try {
                active = PrivRouter.newProcess(this, TerminalCommand.checkedLaunch(args), null, null);
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
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { output.setText(content); run.setEnabled(true); shell.setEnabled(true); interpreter.setEnabled(true); } });
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
