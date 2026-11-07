import com.youlong.hd.ExactHashIndex;
import java.io.*;
import java.nio.file.*;
public class ExactHashIndexCheck {
 public static void main(String[] args)throws Exception{
  String hash="a".repeat(64),url="https://raw.githubusercontent.com/eset/malware-ioc/pinned/samples.sha256";
  ExactHashIndex index=ExactHashIndex.read(new StringReader(hash+"\tfixture\t"+url+"\n"));
  if(index.size()!=1||!index.lookup(hash).contains("fixture")||index.lookup("b".repeat(64))!=null)throw new AssertionError("exact membership");
  for(String invalid:new String[]{"invalid\tfamily\t"+url,hash+"\tfamily\thttp://untrusted.test",hash+"\tfamily"})try{ExactHashIndex.read(new StringReader(invalid));throw new AssertionError("accepted malformed record");}catch(IOException expected){}
  try(Reader reader=Files.newBufferedReader(Path.of(args[0]))){ExactHashIndex bundled=ExactHashIndex.read(reader);if(bundled.size()!=92)throw new AssertionError("unexpected bundled signature count: "+bundled.size());}
  System.out.println("PASS exact hits, misses, malformed input and 92 bundled IOCs");
 }
}
