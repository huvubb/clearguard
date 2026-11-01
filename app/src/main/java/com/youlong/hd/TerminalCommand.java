package com.youlong.hd;

/** Keep scripts as a single argument, regardless of the selected interpreter. */
public final class TerminalCommand {
    private static final String[] EXECUTABLES = {"/system/bin/sh", "bash", "cmd.exe", "pwsh"};
    private TerminalCommand() { }

    public static String defaultExecutable(int mode) {
        if (mode < 0 || mode >= EXECUTABLES.length) throw new IllegalArgumentException("未知解释器");
        return EXECUTABLES[mode];
    }

    public static String[] arguments(int mode, String executable, String script) {
        defaultExecutable(mode);
        if (executable == null || executable.isEmpty() || executable.startsWith("-")
                || executable.indexOf('\0') >= 0 || executable.indexOf('\n') >= 0
                || executable.indexOf('\r') >= 0 || (executable.contains(" ") && !executable.startsWith("/")))
            throw new IllegalArgumentException("请填写一个解释器名称或绝对路径，不包含启动参数。需要额外参数时请指定启动器脚本。");
        if (script == null || script.trim().isEmpty() || script.length() > 8192 || script.indexOf('\0') >= 0)
            throw new IllegalArgumentException("请输入 1–8192 字符且不含 NUL 的命令。");
        if (mode == 2) return new String[]{executable, "/D", "/S", "/C", script};
        if (mode == 3) return new String[]{executable, "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", script};
        return new String[]{executable, "-c", script};
    }

    public static String[] checkedLaunch(String[] args) {
        String[] launch = new String[args.length + 4];
        launch[0] = "/system/bin/sh";
        launch[1] = "-c";
        // Resolve in the privileged service environment, never interpolate user input into sh.
        launch[2] = "if ! command -v \"$1\" >/dev/null 2>&1; then printf '%s\\n' \"解释器不可用：$1。请安装兼容运行时或指定可执行路径。\" >&2; exit 127; fi; exec \"$@\"";
        launch[3] = "clearguard-terminal";
        System.arraycopy(args, 0, launch, 4, args.length);
        return launch;
    }
}
