import com.youlong.hd.LocalRunCommand;
import java.util.Arrays;
public class LocalRunCommandCheck {
    public static void main(String[] args) {
        String prompt = "hello; $(id) \"quoted\"";
        String[] model = LocalRunCommand.arguments(2,"/runtime/llama","/models/a b.gguf",prompt,256);
        assert model[2].equals("/models/a b.gguf"); assert model[4].equals(prompt);
        String[] wrapped = LocalRunCommand.adbOnly(model);
        assert Arrays.equals(Arrays.copyOfRange(wrapped,4,wrapped.length),model);
        assert wrapped[2].contains("!= 2000"); assert !wrapped[2].contains(prompt);
        for (int tokens : new int[]{0,4097,-1}) {
            try { LocalRunCommand.arguments(2,"llama","/a.gguf",prompt,tokens); throw new AssertionError(); }
            catch (IllegalArgumentException expected) { }
        }
        try { LocalRunCommand.arguments(2,"llama","relative.gguf",prompt,256); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        System.out.println("PASS local model argument boundaries, token bounds and explicit ADB UID guard");
    }
}
