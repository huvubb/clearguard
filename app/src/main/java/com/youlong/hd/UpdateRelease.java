package com.youlong.hd;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.util.*;
/** Values from the update server are untrusted until the APK is checked. */
public final class UpdateRelease {
 public static final long MAX_BYTES=512L*1024*1024;
 public final String url,hash,name;public final long version,size;
 public UpdateRelease(String url,String hash,String name,long version,long size,long current)throws IOException{
  try{URI uri=new URI(url);String path=uri.getPath();if(!"https".equals(uri.getScheme())||!"huvubb.abrdns.com".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getFragment()!=null||uri.getQuery()!=null||path==null||!path.matches("/downloads/[A-Za-z0-9._-]+\\.apk")||path.contains(".."))throw new IOException("更新地址无效");}catch(java.net.URISyntaxException invalid){throw new IOException("更新地址无效",invalid);}
  if(hash==null||!hash.matches("[a-fA-F0-9]{64}")||version<=current||size<=0||size>MAX_BYTES||name==null||name.length()>80)throw new IOException("更新版本、大小或校验信息无效");
  this.url=url;this.hash=hash.toLowerCase(Locale.ROOT);this.name=name;this.version=version;this.size=size;
 }
 public void copyVerified(InputStream input,File target)throws IOException{
  boolean success=false;
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;byte[] buffer=new byte[65536];
   try(OutputStream out=new FileOutputStream(target)){int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("校验已停止");count+=n;if(count>size)throw new IOException("更新文件大于声明大小");digest.update(buffer,0,n);out.write(buffer,0,n);}}
   StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));if(count!=size||!actual.toString().equals(hash))throw new IOException("更新文件不完整或 SHA-256 不匹配，请重新下载");success=true;
  }catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}finally{if(!success&&target.exists()&&!target.delete())target.deleteOnExit();}
 }
}
