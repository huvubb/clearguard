package com.youlong.hd;
import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import moe.shizuku.server.IRemoteProcess;
/** Dedicated Shizuku UserService. Only this app UID may create processes. */
public final class ShellUserService extends IClearGuardShell.Stub {
 private final int ownerUid;
 public ShellUserService(Context context){ownerUid=context.getApplicationInfo().uid;}
 private void authorize(){if(Binder.getCallingUid()!=ownerUid)throw new SecurityException("拒绝其他应用调用命令服务");}
 @Override public int uid(){authorize();return android.os.Process.myUid();}
 @Override public IBinder start(String[] command,String[] environment,String directory,IBinder owner){
  authorize();if(command==null||command.length<1||command.length>256||owner==null)throw new IllegalArgumentException("无效命令或会话");int length=0;for(String arg:command){if(arg==null||arg.indexOf(0)>=0)throw new IllegalArgumentException("命令包含无效参数");length+=arg.length();}if(length>131072)throw new IllegalArgumentException("命令过大");
  try{ProcessBuilder builder=new ProcessBuilder(command);if(directory!=null)builder.directory(new File(directory));if(environment!=null){builder.environment().clear();for(String item:environment){int equals=item.indexOf('=');if(equals<=0)throw new IllegalArgumentException("无效环境变量");builder.environment().put(item.substring(0,equals),item.substring(equals+1));}}
   java.lang.Process created=builder.start();try{return new Remote(created,owner,ownerUid).asBinder();}catch(IOException failure){created.destroy();throw failure;}
  }catch(IOException error){throw new IllegalStateException("无法创建 shell 进程："+error.getMessage(),error);}
 }
 @Override public void destroy(){int uid=Binder.getCallingUid();if(uid!=ownerUid&&uid!=0&&uid!=2000)throw new SecurityException("未授权");System.exit(0);}
 private static final class Remote extends IRemoteProcess.Stub {
  private final java.lang.Process process;private final int uid;private final IBinder owner;private final IBinder.DeathRecipient death;
  private final ParcelFileDescriptor[] input,output,error;
  Remote(java.lang.Process process,IBinder owner,int uid)throws IOException{
   this.process=process;this.owner=owner;this.uid=uid;ParcelFileDescriptor[] first=null,second=null,third=null;
   try{first=ParcelFileDescriptor.createPipe();second=ParcelFileDescriptor.createPipe();third=ParcelFileDescriptor.createPipe();}catch(IOException failure){for(ParcelFileDescriptor[] pair:new ParcelFileDescriptor[][]{first,second,third})if(pair!=null)for(ParcelFileDescriptor fd:pair)try{fd.close();}catch(IOException ignored){}throw failure;}
   input=first;output=second;error=third;death=this::close;
   try{owner.linkToDeath(death,0);}catch(RemoteException dead){close();throw new IOException("命令会话已断开",dead);}
   copy(new ParcelFileDescriptor.AutoCloseInputStream(input[0]),process.getOutputStream());copy(process.getInputStream(),new ParcelFileDescriptor.AutoCloseOutputStream(output[1]));copy(process.getErrorStream(),new ParcelFileDescriptor.AutoCloseOutputStream(error[1]));
  }
  private void check(){if(Binder.getCallingUid()!=uid)throw new SecurityException("拒绝访问其他应用进程");}
  private static void copy(InputStream in,OutputStream out){Thread thread=new Thread(()->{try(InputStream source=in;OutputStream target=out){byte[] bytes=new byte[8192];int n;while((n=source.read(bytes))!=-1){target.write(bytes,0,n);target.flush();}}catch(IOException closed){}},"shell-pipe");thread.setDaemon(true);thread.start();}
  @Override public ParcelFileDescriptor getOutputStream(){check();return input[1];}
  @Override public ParcelFileDescriptor getInputStream(){check();return output[0];}
  @Override public ParcelFileDescriptor getErrorStream(){check();return error[0];}
  @Override public int waitFor(){check();try{return process.waitFor();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("等待被中断",interrupted);}}
  @Override public int exitValue(){check();return process.exitValue();}
  @Override public boolean alive(){check();try{process.exitValue();return false;}catch(IllegalThreadStateException running){return true;}}
  @Override public boolean waitForTimeout(long timeout,String unit){check();try{return process.waitFor(timeout,TimeUnit.valueOf(unit));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}}
  @Override public void destroy(){check();close();}
  private synchronized void close(){process.destroy();try{owner.unlinkToDeath(death,0);}catch(Exception ignored){}for(ParcelFileDescriptor[] pair:new ParcelFileDescriptor[][]{input,output,error})for(ParcelFileDescriptor fd:pair)try{fd.close();}catch(IOException ignored){}}
 }
}
