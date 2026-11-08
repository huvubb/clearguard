import com.youlong.hd.UpdateRelease;
import java.io.*;import java.nio.file.*;import java.security.*;import java.util.*;
public class UpdateReleaseCheck {
 interface Checked {void run()throws Exception;}
 static void rejects(Checked check)throws Exception{try{check.run();throw new AssertionError("accepted invalid update");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  byte[] bytes=new byte[131071];new Random(12).nextBytes(bytes);StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",b&255));
  String url="https://huvubb.abrdns.com/downloads/ClearGuard-2.5.1-release.apk";
  UpdateRelease release=new UpdateRelease(url,hash.toString(),"2.5.1",101,bytes.length,100);
  File target=Files.createTempFile("update-check-",".apk").toFile();try{
   release.copyVerified(new ByteArrayInputStream(bytes),target);if(!Arrays.equals(bytes,Files.readAllBytes(target.toPath())))throw new AssertionError("copy mismatch");
   for(byte[] bad:new byte[][]{Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),new byte[bytes.length]}){rejects(()->release.copyVerified(new ByteArrayInputStream(bad),target));if(target.exists())throw new AssertionError("failed APK retained");}
   Thread.currentThread().interrupt();rejects(()->release.copyVerified(new ByteArrayInputStream(bytes),target));Thread.interrupted();
   if(target.exists())throw new AssertionError("interrupted APK retained");
  }finally{target.delete();}
  for(String bad:new String[]{url.replace("https:","http:"),url.replace("huvubb.abrdns.com","evil.test"),url+"#bad",url+"?redirect=x","https://huvubb.abrdns.com/downloads/../bad.apk","https://user@huvubb.abrdns.com/downloads/a.apk"})rejects(()->new UpdateRelease(bad,hash.toString(),"2.5.1",101,1,100));
  rejects(()->new UpdateRelease(url,"bad","2.5.1",101,1,100));rejects(()->new UpdateRelease(url,hash.toString(),"old",100,1,100));rejects(()->new UpdateRelease(url,hash.toString(),"x",101,UpdateRelease.MAX_BYTES+1,100));
  System.out.println("PASS update origin, metadata, downgrade, SHA-256, truncation, oversize, tamper, cancellation and partial file cleanup");
 }
}
