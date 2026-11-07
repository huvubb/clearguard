import java.io.*;import java.security.*;import java.nio.charset.*;import java.util.*;
import com.google.common.hash.Funnels;
public class BloomCompatibilityCheck {
 static String hash(int i)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(("harmless-fixture-"+i).getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format("%02x",v&255));return out.toString();}
 public static void main(String[] args)throws Exception{
  com.google.common.hash.BloomFilter<CharSequence> original=com.google.common.hash.BloomFilter.create(Funnels.stringFunnel(StandardCharsets.US_ASCII),1000,1e-7);for(int i=0;i<1000;i++)original.put(hash(i));ByteArrayOutputStream out=new ByteArrayOutputStream();original.writeTo(out);byte[] bytes=out.toByteArray();com.youlong.hd.BloomFilter own=com.youlong.hd.BloomFilter.read(new ByteArrayInputStream(bytes));
  for(int i=0;i<11000;i++)if(own.contains(hash(i))!=original.mightContain(hash(i)))throw new AssertionError("Guava mismatch "+i);
  if(own.contains(null)||own.contains("not-a-hash"))throw new AssertionError("invalid hash");
  for(byte[] invalid:new byte[][]{new byte[0],Arrays.copyOf(bytes,bytes.length-1),Arrays.copyOf(bytes,bytes.length+1),new byte[]{2,17,0,0,0,1}}){try{com.youlong.hd.BloomFilter.read(new ByteArrayInputStream(invalid));throw new AssertionError("accepted invalid format");}catch(IOException expected){}}
  try(InputStream a=new FileInputStream(args[0]);InputStream b=new FileInputStream(args[0])){com.youlong.hd.BloomFilter actual=com.youlong.hd.BloomFilter.read(a);com.google.common.hash.BloomFilter<CharSequence> reference=com.google.common.hash.BloomFilter.readFrom(b,Funnels.stringFunnel(StandardCharsets.US_ASCII));for(int i=0;i<2000;i++)if(actual.contains(hash(i))!=reference.mightContain(hash(i)))throw new AssertionError("real DB mismatch");if(actual.estimatedEntries()!=reference.approximateElementCount())throw new AssertionError("entry estimate");System.out.println("Actual DB: estimated entries="+actual.estimatedEntries()+", k="+actual.hashCount()+", theoretical FPP="+actual.expectedFalsePositiveRate());}
  System.out.println("PASS Guava cross-implementation positive/negative queries, actual DB parsing, truncated/trailing/invalid inputs");
 }
}
