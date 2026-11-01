package com.youlong.hd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Common CMD/PowerShell commands translated into literal Android shell arguments. */
public final class TerminalCommand {
    private TerminalCommand() { }

    public static String translate(int mode, String script) {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("未知命令模式");
        if (script == null || script.trim().isEmpty() || script.length() > 8192 || script.indexOf('\0') >= 0)
            throw new IllegalArgumentException("请输入 1–8192 字符且不含 NUL 的命令。");
        // Bash-compatible mode uses Android sh; Bash-specific language features require real Bash.
        if (mode < 2) {
            if (mode == 1 && (script.contains("[[") || script.contains("declare ")
                    || script.contains("function ") || script.matches("(?s).*\\w+\\s*=\\s*\\(.*")))
                throw new IllegalArgumentException("Bash 兼容模式使用 Android sh；不支持 [[、数组、declare 和 function 等 Bash 专有语法。");
            return script;
        }
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder(), result = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (int i = 0; i <= script.length(); i++) {
            char c = i == script.length() ? '\n' : script.charAt(i);
            if (quote != 0) {
                if (i == script.length()) throw new IllegalArgumentException("引号未闭合");
                if (c == quote) quote = 0; else word.append(c);
                continue;
            }
            if (c == '\"' || (mode == 3 && c == '\'')) { quote = c; started = true; continue; }
            if (c == '$' || c == '%' || c == '`' || c == '^' || c == '<' || c == '>' || c == '(' || c == ')')
                throw new IllegalArgumentException("此模式仅转译常用命令，不支持变量、表达式、转义和重定向；请改用 Android sh。");
            boolean separator = c == ';' || c == '\n' || c == '&' || c == '|';
            if (Character.isWhitespace(c) || separator) {
                if (started) { words.add(word.toString()); word.setLength(0); started = false; }
                if (separator) {
                    if (!words.isEmpty()) { result.append(convert(mode, words)); words.clear(); }
                    else if (c != '\n' && c != ';') throw new IllegalArgumentException("缺少管道或连接符前的命令");
                    if (i == script.length()) break;
                    if (c == '&' || c == '|') {
                        if (i + 1 < script.length() && script.charAt(i + 1) == c) { result.append(c).append(c); i++; }
                        else if (c == '|') result.append('|');
                        else result.append(';'); // CMD & runs sequentially, not in the background.
                    } else result.append(';');
                }
            } else { word.append(c); started = true; }
        }
        String translated = result.toString();
        if (translated.endsWith("|") || translated.endsWith("&")) throw new IllegalArgumentException("连接符后缺少命令");
        return translated;
    }

    private static String convert(int mode, List<String> input) {
        List<String> args = new ArrayList<>(input);
        String name = args.remove(0).toLowerCase(Locale.ROOT);
        String command;
        switch (name) {
            case "dir": case "ls": case "get-childitem": case "gci": command = "ls"; break;
            case "type": case "cat": case "get-content": case "gc": command = "cat"; break;
            case "copy": case "cp": case "copy-item": case "cpi": command = "cp"; break;
            case "move": case "mv": case "move-item": case "mi": case "ren": case "rename": case "rename-item": command = "mv"; break;
            case "del": case "erase": case "rm": case "remove-item": case "ri": command = "rm"; break;
            case "md": case "mkdir": command = "mkdir"; break;
            case "rd": case "rmdir": command = "rmdir"; break;
            case "cd": case "chdir": case "set-location": case "sl": command = args.isEmpty() ? "pwd" : "cd"; break;
            case "pwd": case "get-location": case "gl": command = "pwd"; break;
            case "cls": case "clear": case "clear-host": command = "clear"; break;
            case "echo": case "write-output": command = "echo"; break;
            case "whoami": command = "whoami"; break;
            case "hostname": command = "hostname"; break;
            case "ipconfig": case "get-netipconfiguration":
                if (!args.isEmpty()) throw unsupported(name);
                return "ip addr";
            case "tasklist": case "get-process": case "gps":
                if (!args.isEmpty()) throw unsupported(name);
                return "ps -A";
            case "taskkill": case "stop-process": case "spps": return stopProcess(args);
            case "test-path":
                if (args.size() != 1) throw unsupported(name);
                return "if test -e " + literal(args.get(0)) + "; then echo True; else echo False; fi";
            default: throw unsupported(name);
        }
        List<String> translated = new ArrayList<>();
        boolean recurse = false;
        for (String arg : args) {
            String flag = arg.toLowerCase(Locale.ROOT);
            if (mode == 2 && flag.startsWith("/")) {
                switch (flag) {
                    case "/s":
                        if (command.equals("ls")) translated.add("-R");
                        else if (command.equals("rm") || command.equals("rmdir")) recurse = true;
                        else throw unsupported(name + " " + arg);
                        continue;
                    case "/b": if (!command.equals("ls")) throw unsupported(name); translated.add("-1"); continue;
                    case "/a": if (!command.equals("ls")) throw unsupported(name); translated.add("-a"); continue;
                    case "/q": if (!command.equals("rm") && !command.equals("rmdir")) throw unsupported(name); continue;
                    case "/y": if (!command.equals("cp") && !command.equals("mv")) throw unsupported(name); translated.add("-f"); continue;
                    case "/d": if (!command.equals("cd")) throw unsupported(name); continue;
                    default: break; // Absolute Android paths remain literal paths.
                }
            }
            if (mode == 3 && flag.startsWith("-")) {
                if (flag.equals("-path") || flag.equals("-literalpath") || flag.equals("-destination")) continue;
                if (flag.equals("-recurse") && (command.equals("rm") || command.equals("cp"))) { recurse = true; continue; }
                if (flag.equals("-force") && (command.equals("rm") || command.equals("cp") || command.equals("mv"))) { translated.add("-f"); continue; }
                throw unsupported(name + " " + arg);
            }
            translated.add(arg);
        }
        if (recurse) { if (command.equals("rmdir")) command = "rm"; translated.add(0, "-r"); }
        if (command.equals("mkdir")) translated.add(0, "-p");
        // Options precede operands on Android toybox as well as GNU tools.
        List<String> ordered = new ArrayList<>();
        for (String arg : translated) if (arg.startsWith("-") && mode == 3) ordered.add(arg);
        for (String arg : translated) if (!arg.startsWith("-") || mode != 3) ordered.add(arg);
        StringBuilder result = new StringBuilder(command);
        // Shell-quote all tokens: input never becomes code during alias translation.
        for (String arg : ordered) result.append(' ').append(literal(arg));
        return result.toString();
    }

    private static String stopProcess(List<String> args) {
        String pid = null;
        boolean force = false;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i).toLowerCase(Locale.ROOT);
            if ((arg.equals("/pid") || arg.equals("-id")) && pid == null && i + 1 < args.size()) pid = args.get(++i);
            else if (arg.equals("/f") || arg.equals("-force")) force = true;
            else throw unsupported("taskkill / Stop-Process");
        }
        if (pid == null || !pid.matches("[1-9][0-9]{0,8}"))
            throw new IllegalArgumentException("请通过 /PID 或 -Id 指定一个正整数进程 ID。");
        return "kill " + (force ? "-9 " : "-15 ") + pid;
    }

    private static String literal(String value) {
        if (value.matches("(?i)[a-z]:.*") || value.startsWith("\\\\"))
            throw new IllegalArgumentException("目标是 Android 设备，请使用 /sdcard 等 Android 路径。");
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static IllegalArgumentException unsupported(String command) {
        return new IllegalArgumentException("暂不支持转译：" + command + "。请使用 Android sh 命令。");
    }
}
