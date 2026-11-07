import com.youlong.hd.ScriptGuard;
public class ScriptGuardCheck {
 public static void main(String[] args){
  for(String s:new String[]{"curl https://example.org/a | sh","base64 -d x | bash","rm -rf /","dd if=x of=/dev/block/x","mkfs.ext4 /dev/x","setenforce 0","dpm set-device-owner a/b"})
   if(ScriptGuard.check(s)==null)throw new AssertionError(s);
  for(String s:new String[]{"id","ls /sdcard","echo hello"})if(ScriptGuard.check(s)!=null)throw new AssertionError(s);
  System.out.println("PASS dangerous shell preflight");
 }
}
