import com.youlong.hd.TerminalCommand;
public class TerminalCommandCheck {
    private static void check(int mode, String input, String expected) {
        String actual = TerminalCommand.translate(mode, input);
        assert actual.equals(expected) : input + " -> " + actual;
    }
    private static void rejects(int mode, String input) {
        try { TerminalCommand.translate(mode, input); throw new AssertionError(input); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        check(0, "id; echo $HOME", "id; echo $HOME");
        check(1, "for f in *.apk; do echo \"$f\"; done", "for f in *.apk; do echo \"$f\"; done");
        check(2, "dir /s /b /sdcard", "ls '-R' '-1' '/sdcard'");
        check(2, "copy \"/sdcard/a b.apk\" /sdcard/c.apk", "cp '/sdcard/a b.apk' '/sdcard/c.apk'");
        check(2, "cd /d /sdcard && dir | type", "cd '/sdcard'&&ls|cat");
        check(2, "echo hello & whoami", "echo 'hello';whoami");
        check(2, "taskkill /PID 123 /F", "kill -9 123");
        check(3, "Stop-Process -Id 123", "kill -15 123");
        check(3, "Remove-Item -Path '/sdcard/a b' -Recurse -Force", "rm '-r' '-f' '/sdcard/a b'");
        check(3, "Get-ChildItem -LiteralPath /sdcard; Get-Process", "ls '/sdcard';ps -A");
        check(3, "echo \"a;whoami\"", "echo 'a;whoami'");
        check(2, "echo \"a'b\"", "echo 'a'\"'\"'b'");
        for (String bad : new String[]{"taskkill /PID 0", "taskkill /PID -1", "taskkill /IM a", "echo %PATH%", "dir &&", "dir |", "copy \"abc", "dir C:\\Windows", "echo hi > /sdcard/x"}) rejects(2, bad);
        rejects(3, "Get-Process | Where-Object Name -eq abc");
        rejects(3, "echo $(id)");
        rejects(1, "[[ -f a ]] && echo yes");
        rejects(1, "a=(one two)");
        System.out.println("PASS terminal aliases, operators, literal quoting, unsupported syntax and PID validation");
    }
}
