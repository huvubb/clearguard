package com.youlong.hd;
import java.util.regex.Pattern;
/** Preflight checks for this app's terminal; not a global shell sandbox. */
public final class ScriptGuard {
 private ScriptGuard() {}
 private static final String[][] RULES={
  {"(?is)\\b(?:curl|wget)\\b.*\\|\\s*(?:sh|bash)\\b","下载内容直接执行"},
  {"(?is)\\bbase64\\b.*\\|.*\\b(?:sh|bash|eval)\\b","解码内容直接执行"},
  {"(?is)\\brm\\s+[^;\\n]*-[a-z]*r[a-z]*[^;\\n]*\\s/(?:\\s|$|[;*])","递归删除根目录"},
  {"(?is)\\b(?:mkfs(?:\\.\\w+)?|mke2fs|wipefs)\\b","格式化分区"},
  {"(?is)\\bdd\\b[^;\\n]*\\bof\\s*=\\s*/dev/","覆写块设备"},
  {"(?is)\\bsetenforce\\s+0\\b","关闭 SELinux"},
  {"(?is)\\bdpm\\s+set-(?:device|profile)-owner\\b","设置设备管理所有者"}
 };
 public static String check(String script){
  if(script==null||script.indexOf(0)>=0||script.length()>1048576)return "脚本无效或过大";
  for(String[] rule:RULES)if(Pattern.compile(rule[0]).matcher(script).find())return rule[1];
  return null;
 }
}
