package com.youlong.hd;

public final class LocalRunCommand {
    private LocalRunCommand() { }
    public static String[] arguments(int type, String runtime, String file, String prompt, int tokens) {
        if (type < 0 || type > 2) throw new IllegalArgumentException("未知运行类型");
        if (runtime == null || runtime.isEmpty() || runtime.startsWith("-") || runtime.indexOf('\0') >= 0
                || runtime.indexOf('\n') >= 0 || (runtime.contains(" ") && !runtime.startsWith("/")))
            throw new IllegalArgumentException("解释器须为单个名称或绝对路径，不含启动参数");
        if (file == null || !file.startsWith("/") || file.indexOf('\0') >= 0)
            throw new IllegalArgumentException("请输入 Android 绝对文件路径，或导入本地文件");
        if (type == 0) return new String[]{"/system/bin/sh", file};
        if (type == 1) return new String[]{runtime, "-u", file};
        if (tokens < 1 || tokens > 4096 || prompt == null || prompt.length() > 8192 || prompt.indexOf('\0') >= 0)
            throw new IllegalArgumentException("提示词上限 8192 字符，生成长度为 1–4096 tokens");
        return new String[]{runtime, "-m", file, "-p", prompt, "-n", Integer.toString(tokens),
                "-c", "2048", "-ngl", "0", "-no-cnv", "--no-warmup"};
    }
    public static String[] adbOnly(String[] args) {
        String[] launch = new String[args.length + 4];
        launch[0] = "/system/bin/sh"; launch[1] = "-c";
        launch[2] = "if [ \"$(/system/bin/id -u)\" != 2000 ]; then echo '需要 ADB shell UID 2000；拒绝以其他 UID 执行' >&2; exit 126; fi; exec \"$@\"";
        launch[3] = "clearguard-adb";
        System.arraycopy(args, 0, launch, 4, args.length);
        return launch;
    }
}
