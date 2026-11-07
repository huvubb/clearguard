package com.youlong.hd;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
/** Hypatia/Guava writeTo format; membership is probabilistic, never a diagnosis. */
public final class BloomFilter {
 private final long[] words;private final int strategy,k;
 private BloomFilter(long[] words,int strategy,int k){this.words=words;this.strategy=strategy;this.k=k;}
 public static BloomFilter read(InputStream source)throws IOException{
  DataInputStream in=new DataInputStream(source);int strategy=in.readUnsignedByte(),k=in.readUnsignedByte(),length=in.readInt();
  if(strategy>1||k<1||k>64||length<1||length>4_194_304)throw new IOException("病毒库格式不受支持或大小超限");
  long[] words=new long[length];for(int i=0;i<length;i++)words[i]=in.readLong();if(in.read()!=-1)throw new IOException("病毒库包含异常尾部数据");return new BloomFilter(words,strategy,k);
 }
 public static BloomFilter load(File file)throws IOException{try(InputStream in=new FileInputStream(file)){return read(in);}}
 public static BloomFilter loadFromAsset(android.content.Context context,String name)throws IOException{try(InputStream in=context.getAssets().open(name)){return read(in);}}
 public int size(){return words.length*64;}
 public int hashCount(){return k;}
 public long estimatedEntries(){long used=0;for(long word:words)used+=Long.bitCount(word);double fraction=used/(double)size();return fraction>=1?Long.MAX_VALUE:Math.round(-Math.log1p(-fraction)*size()/k);}
 public double expectedFalsePositiveRate(){long used=0;for(long word:words)used+=Long.bitCount(word);return Math.pow(used/(double)size(),k);}
 public boolean contains(String hash){
  if(hash==null||!hash.matches("[0-9a-fA-F]{64}"))return false;
  long[] h=murmur(hash.toLowerCase(Locale.ROOT));long total=size();
  if(strategy==1){long combined=h[0];for(int i=0;i<k;i++){long bit=(combined&Long.MAX_VALUE)%total;if((words[(int)(bit>>>6)]&(1L<<(bit&63)))==0)return false;combined+=h[1];}}
  else{int first=(int)h[0],second=(int)(h[0]>>>32);for(int i=1;i<=k;i++){int combined=first+i*second;if(combined<0)combined=~combined;long bit=combined%total;if((words[(int)(bit>>>6)]&(1L<<(bit&63)))==0)return false;}}
  return true;
 }
 private static long little(byte[] b,int start){long out=0;for(int i=0;i<8;i++)out|=(long)(b[start+i]&255)<<(8*i);return out;}
 private static long mix(long value){value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;value*=0xc4ceb9fe1a85ec53L;return value^(value>>>33);}
 private static long[] murmur(String hex){
  // SHA-256 hex is always 64 ASCII bytes, so no partial Murmur3 block is needed.
  byte[] bytes=hex.getBytes(StandardCharsets.US_ASCII);long h1=0,h2=0,c1=0x87c37b91114253d5L,c2=0x4cf5ad432745937fL;
  for(int offset=0;offset<64;offset+=16){long k1=little(bytes,offset),k2=little(bytes,offset+8);k1*=c1;k1=Long.rotateLeft(k1,31);k1*=c2;h1^=k1;h1=Long.rotateLeft(h1,27);h1+=h2;h1=h1*5+0x52dce729L;k2*=c2;k2=Long.rotateLeft(k2,33);k2*=c1;h2^=k2;h2=Long.rotateLeft(h2,31);h2+=h1;h2=h2*5+0x38495ab5L;}
  h1^=64;h2^=64;h1+=h2;h2+=h1;h1=mix(h1);h2=mix(h2);h1+=h2;h2+=h1;return new long[]{h1,h2};
 }
}
