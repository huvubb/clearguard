package com.youlong.hd;
import java.io.*;
/** Deletes only descendants of caller-supplied cache roots, never follows links. */
public final class CacheCleaner {
 public static final class Result { public long bytes, files, skipped; }
 private CacheCleaner() {}
 public static Result inspect(File[] roots,boolean delete)throws IOException{
  Result result=new Result();
  for(File candidate:roots){if(candidate==null||!candidate.exists())continue;File root=candidate.getCanonicalFile();if(!root.isDirectory())throw new IOException("缓存目录无效");walk(root,root,delete,result,0);}
  return result;
 }
 private static void walk(File root,File current,boolean delete,Result result,int depth)throws IOException{
  if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已取消");
  if(java.nio.file.Files.isSymbolicLink(current.toPath())||depth>64||!current.getCanonicalFile().equals(current.getAbsoluteFile())||(!current.equals(root)&&!current.getPath().startsWith(root.getPath()+File.separator))){result.skipped++;return;}
  if(current.isDirectory()){
   File[] children=current.listFiles();if(children==null){result.skipped++;return;}
   for(File child:children)walk(root,child,delete,result,depth+1);
   if(delete&&!current.equals(root)){String[] left=current.list();if(left!=null&&left.length==0&&!current.delete())result.skipped++;}
  }else if(current.isFile()){
   long size=current.length();if(!delete||current.delete()){result.files++;result.bytes+=size;}else result.skipped++;
  }else result.skipped++;
 }
}
