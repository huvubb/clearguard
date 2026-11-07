package com.youlong.hd;
import java.io.*;import java.util.*;
/** Explicit research IOCs, distinct from probabilistic Bloom membership. */
public final class ExactHashIndex {
 private final Map<String,String> entries=new HashMap<>();
 public static ExactHashIndex read(Reader source)throws IOException{ExactHashIndex out=new ExactHashIndex();BufferedReader in=new BufferedReader(source);String line;while((line=in.readLine())!=null){if(line.startsWith("#")||line.trim().isEmpty())continue;String[] parts=line.split("\t",3);if(parts.length!=3||!parts[0].matches("[a-f0-9]{64}")||parts[1].length()>120||!parts[2].startsWith("https://raw.githubusercontent.com/eset/malware-ioc/"))throw new IOException("精确病毒库记录格式无效");out.entries.put(parts[0],parts[1]+"\n来源："+parts[2]);if(out.entries.size()>1_000_000)throw new IOException("精确病毒库超限");}return out;}
 public String lookup(String hash){return entries.get(hash);}
 public int size(){return entries.size();}
}
