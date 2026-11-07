package com.youlong.hd;
import moe.shizuku.server.IRemoteProcess;
public class RemoteProcessContractCheck {
 static class Fake implements IRemoteProcess {
  boolean running=true;int code=7;
  public android.os.IBinder asBinder(){return null;}
  public android.os.ParcelFileDescriptor getOutputStream(){return null;}
  public android.os.ParcelFileDescriptor getInputStream(){return null;}
  public android.os.ParcelFileDescriptor getErrorStream(){return null;}
  public int waitFor(){running=false;return code;}
  public int exitValue(){if(running)throw new IllegalArgumentException("remote runtime still running");return code;}
  public void destroy(){running=false;}
  public boolean alive(){return running;}
  public boolean waitForTimeout(long n,String unit){return !running;}
 }
 public static void main(String[] args){Fake remote=new Fake();Process process=new ShizukuBackend.ShizukuProcess(remote);try{process.exitValue();throw new AssertionError("running process returned exit code");}catch(IllegalThreadStateException expected){}remote.running=false;if(process.exitValue()!=7)throw new AssertionError("exit code lost");System.out.println("PASS remote running/exit semantics preserve Process contract");}
}
