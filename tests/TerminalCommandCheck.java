import com.youlong.hd.TerminalCommand;
import java.util.Arrays;
public class TerminalCommandCheck {
    public static void main(String[] args) {
        String script = "echo \"hello\"; $(id)\nGet-Process";
        for (int mode = 0; mode < 4; mode++) {
            String[] command = TerminalCommand.arguments(mode, TerminalCommand.defaultExecutable(mode), script);
            assert command[command.length - 1].equals(script);
            String[] launch = TerminalCommand.checkedLaunch(command);
            assert Arrays.equals(Arrays.copyOfRange(launch, 4, launch.length), command);
            assert !launch[2].contains(script);
        }
        assert TerminalCommand.arguments(2, "cmd.exe", "dir")[1].equals("/D");
        assert TerminalCommand.arguments(3, "/data/local/tmp/pwsh", "Get-Process")[4].equals("-Command");
        for (String invalid : new String[]{"", "-x", "bash -x", "sh\nrm", "sh\0bad"}) {
            try { TerminalCommand.arguments(1, invalid, "id"); throw new AssertionError(invalid); }
            catch (IllegalArgumentException expected) { }
        }
        try { TerminalCommand.arguments(4, "sh", "id"); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS terminal interpreter arguments and script boundaries");
    }
}
