import com.youlong.hd.RansomwareDetector;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.zip.*;
public class RansomwareDetectorCheck{
 static Set<String> set(String... s){return new HashSet<>(Arrays.asList(s));}
 public static void main(String[] args)throws Exception{
  if(RansomwareDetector.evaluate(set("javax/crypto/cipher","bitcoin"),false).suspect)throw new AssertionError("crypto is not ransomware");
  if(RansomwareDetector.evaluate(set("your files are encrypted"),false).suspect)throw new AssertionError("text alone");
  if(!RansomwareDetector.evaluate(set("your files are encrypted","bitcoin","javax/crypto/cipher"),false).suspect)throw new AssertionError("combined encryption evidence");
  if(!RansomwareDetector.evaluate(set("unlock your files","monero","resetpassword","devicepolicymanager"),false).suspect)throw new AssertionError("locking evidence");
  Path apk=Files.createTempFile("ransom-fixture-",".apk");try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(apk))){zip.putNextEntry(new ZipEntry("classes.dex"));byte[] padding=new byte[32760];zip.write(padding);zip.write("Your files are encrypted; bitcoin; javax/crypto/Cipher".getBytes("UTF-8"));zip.closeEntry();}
  if(!RansomwareDetector.scan(new File[]{apk.toFile()}).suspect)throw new AssertionError("cross-buffer detection");Files.delete(apk);
  System.out.println("PASS benign crypto, partial evidence, locking, encryption and APK cross-buffer evidence");
 }
}
