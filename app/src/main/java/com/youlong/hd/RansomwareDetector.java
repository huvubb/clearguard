package com.youlong.hd;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
/** Evidence-based static triage, never treats encryption or uploading alone as ransomware. */
public final class RansomwareDetector {
 public static final class Verdict {
  public final boolean suspect,truncated;public final String reason;
  Verdict(boolean suspect,boolean truncated,String reason){this.suspect=suspect;this.truncated=truncated;this.reason=reason;}
 }
 private RansomwareDetector(){}
 public static Verdict evaluate(Set<String> evidence,boolean truncated){
  boolean payment=evidence.contains("bitcoin")||evidence.contains("monero")||evidence.contains("ransom payment");
  boolean note=evidence.contains("your files have been encrypted")||evidence.contains("your files are encrypted")||evidence.contains("decrypt your files")||evidence.contains("unlock your files");
  boolean crypto=evidence.contains("javax/crypto/cipher")||evidence.contains("javax.crypto.cipher");
  boolean lock=evidence.contains("resetpassword")&&(evidence.contains("devicepolicymanager"));
  boolean risk=note&&payment&&(crypto||lock);
  return new Verdict(risk,truncated,risk?"同时发现勒索提示、付款要求和"+(crypto?"加密 API":"设备锁定 API")+"。静态疑似勒索风险，需人工复核。":"未发现本规则覆盖的勒索特征组合"+(truncated?"；扫描达到上限，结论不完整":""));
 }
 private static final String[] TOKENS={"bitcoin","monero","ransom payment","your files have been encrypted","your files are encrypted","decrypt your files","unlock your files","javax/crypto/cipher","javax.crypto.cipher","resetpassword","devicepolicymanager"};
 public static Verdict scan(File[] apks)throws IOException{
  Set<String> found=new HashSet<>();long remaining=64L*1024*1024;long deadline=System.nanoTime()+30_000_000_000L;
  for(File apk:apks)try(ZipFile zip=new ZipFile(apk)){
   Enumeration<? extends ZipEntry> entries=zip.entries();
   while(entries.hasMoreElements()){
    ZipEntry entry=entries.nextElement();String name=entry.getName().toLowerCase(Locale.ROOT);
    if(entry.isDirectory()||!(name.endsWith(".dex")||name.startsWith("assets/")||name.startsWith("res/raw/")))continue;
    String carry="";try(InputStream input=zip.getInputStream(entry)){byte[] buffer=new byte[32768];int n;
     while((n=input.read(buffer))!=-1){
      if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("扫描已停止");
      if((remaining-=n)<0||System.nanoTime()>deadline)return evaluate(found,true);
      String text=carry+new String(buffer,0,n,StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
      for(String token:TOKENS)if(text.contains(token))found.add(token);
      carry=text.substring(Math.max(0,text.length()-64));
     }
    }
   }
  }
  return evaluate(found,false);
 }
}
