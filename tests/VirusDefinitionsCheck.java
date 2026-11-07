package com.youlong.hd;
import java.io.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
public class VirusDefinitionsCheck {
 public static void main(String[] args)throws Exception{
  byte[] fixture=new byte[9*1024*1024];new Random(71).nextBytes(fixture);
  StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(fixture))hash.append(String.format(Locale.ROOT,"%02x",b&255));
  ExactHashIndex exact=ExactHashIndex.read(new StringReader(hash+"\tSAFE_TEST_FIXTURE\thttps://raw.githubusercontent.com/eset/malware-ioc/test/fixture\n"));
  BloomFilter bloom;try(InputStream in=new FileInputStream(args[0])){bloom=BloomFilter.read(in);}
  VirusDefinitions definitions=new VirusDefinitions(bloom,exact);List<Long> progress=new ArrayList<>();
  VirusDefinitions.Match match=definitions.inspect(new ByteArrayInputStream(fixture),()->false,progress::add);
  if(!match.confirmed||match.suspect||!match.hash.equals(hash.toString())||progress.size()<3)throw new AssertionError("full stream hash/exact/progress");
  if(definitions.lookup("0".repeat(64)).confirmed)throw new AssertionError("false exact hit");
  try{definitions.inspect(new ByteArrayInputStream(fixture),()->true,n->{});throw new AssertionError("ignored cancellation");}catch(InterruptedIOException expected){}
  InputStream unreadable=new InputStream(){public int read()throws IOException{throw new IOException("fixture read error");}};
  try{definitions.inspect(unreadable,()->false,n->{});throw new AssertionError("hidden IO failure");}catch(IOException expected){}
  System.out.println("PASS 9 MiB full-file SHA-256, exact hit/miss, progress, cancellation and read failures (harmless random fixture)");
 }
}
