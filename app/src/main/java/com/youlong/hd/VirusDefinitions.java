package com.youlong.hd;
import android.content.Context;import java.io.*;import java.security.*;import java.util.*;import java.util.function.*;
public final class VirusDefinitions {
 private static volatile VirusDefinitions instance;
 private final BloomFilter bloom;private final ExactHashIndex exact;
 private VirusDefinitions(Context context)throws IOException{bloom=BloomFilter.loadFromAsset(context,"virus-bloom.bin");try(InputStream in=context.getAssets().open("apk-signatures.tsv")){exact=ExactHashIndex.read(new InputStreamReader(in,"UTF-8"));}}
 VirusDefinitions(BloomFilter bloom,ExactHashIndex exact){this.bloom=bloom;this.exact=exact;}
 public static synchronized VirusDefinitions get(Context context)throws IOException{if(instance==null)instance=new VirusDefinitions(context.getApplicationContext());return instance;}
 public int exactCount(){return exact.size();}
 public long approximateCount(){return bloom.estimatedEntries();}
 public static final class Match {public final String hash,evidence;public final boolean confirmed,suspect;Match(String hash,String evidence,boolean confirmed,boolean suspect){this.hash=hash;this.evidence=evidence;this.confirmed=confirmed;this.suspect=suspect;}}
 public Match lookup(String hash){String source=exact.lookup(hash);if(source!=null)return new Match(hash,source,true,false);boolean hit=bloom.contains(hash);return new Match(hash,hit?"Bloom 筛查命中，需要精确复核":"未命中当前已知哈希特征",false,hit);}
 public Match inspect(InputStream in,BooleanSupplier cancelled,Consumer<Long> progress)throws IOException{
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;long bytes=0,next=0;while((n=in.read(buffer))!=-1){if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("已停止扫描");bytes+=n;if(bytes>2L*1024*1024*1024)throw new IOException("单文件扫描上限为 2 GiB");digest.update(buffer,0,n);if(bytes>=next){progress.accept(bytes);next=bytes+4L*1024*1024;}}
   StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));return lookup(hash.toString());
  }catch(NoSuchAlgorithmException impossible){throw new IOException("SHA-256 不可用",impossible);}
 }
}
