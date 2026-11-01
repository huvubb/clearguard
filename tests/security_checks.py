"""Compile unmodified repository Kotlin classes against minimal JVM Android stubs.
Usage: python run_checks.py REPO GRADLE_LIB WORK_DIR
These are component checks, not Android/Binder integration tests.
"""
import pathlib, subprocess, sys

repo, lib, out = map(pathlib.Path, sys.argv[1:])
out.mkdir(parents=True, exist_ok=True)
sources = {
'Android.kt': """package android.os
open class RemoteException : Exception()
interface IBinder {
 fun interface DeathRecipient { fun binderDied() }
 fun linkToDeath(recipient: DeathRecipient, flags: Int) {}
 fun pingBinder(): Boolean = true
}
open class Bundle {
 private val values = mutableMapOf<String, Any?>()
 fun putString(k:String,v:String?) { values[k]=v }
 fun getString(k:String, d:String?=null):String? = values[k] as? String ?: d
 fun putInt(k:String,v:Int) { values[k]=v }
 fun getInt(k:String,d:Int=0):Int = values[k] as? Int ?: d
 fun putLong(k:String,v:Long) { values[k]=v }
 fun putBoolean(k:String,v:Boolean) { values[k]=v }
 fun getBoolean(k:String,d:Boolean=false):Boolean = values[k] as? Boolean ?: d
 fun putParcelable(k:String,v:Any?) {values[k]=v}
 @Suppress("UNCHECKED_CAST") fun <T> getParcelable(k:String):T? = values[k] as? T
}
class RemoteCallbackList<T> {
 private val items=mutableListOf<T>()
 fun register(v:T) {items.add(v)}
 fun unregister(v:T) {items.remove(v)}
 fun beginBroadcast()=items.size
 fun getBroadcastItem(i:Int)=items[i]
 fun finishBroadcast() {}
}

object Build {object VERSION { const val SDK_INT=35 }; object VERSION_CODES {const val Q=29}}
""",
'Content.kt': """package android.content
class ComponentName(val packageName:String,val className:String)

interface IContentProvider
""",
'Stellar.kt': """package com.stellar.server
import android.os.*
interface IStellarApplication {
 fun asBinder():IBinder
 fun dispatchRequestPermissionResult(code:Int, reply:Bundle) {}
}
interface IUserServiceCallback {
 fun onServiceConnected(service:IBinder, verificationToken:String?)
 fun onServiceDisconnected()
 fun onServiceStartFailed(errorCode:Int,message:String?)
 abstract class Stub:IUserServiceCallback
}
""",
'Shizuku.kt': """package moe.shizuku.server
import android.os.*
interface IShizukuApplication {
 fun asBinder():IBinder
 fun dispatchRequestPermissionResult(code:Int,reply:Bundle) {}
 fun bindApplication(reply:Bundle) {}
}
interface IShizukuServiceConnection {
 fun connected(binder:IBinder)
 fun died()
}
""",
'Constants.kt': """package roro.stellar
object StellarApiConstants {
 const val PERMISSION_KEY="roro.stellar.permissions"
 val PERMISSIONS=arrayOf("stellar","follow_stellar_startup")
 const val PERMISSION_STELLAR="stellar"
 const val REQUEST_PERMISSION_REPLY_ALLOWED="allowed"
 const val REQUEST_PERMISSION_REPLY_IS_ONETIME="onetime"
 const val REQUEST_PERMISSION_REPLY_PERMISSION="permission"
}
""",
'Logger.kt': """package roro.stellar.server.util
class Logger(name:String) {
 fun e(error:Throwable,message:String,vararg args:Any?) {}
 fun v(message:String,vararg args:Any?) {}
 fun i(message:String,vararg args:Any?) {}
 fun w(message:String,vararg args:Any?) {}
 fun w(error:Throwable,message:String,vararg args:Any?) {}
 fun d(message:String,vararg args:Any?) {}
}
""",
'UserServices.kt': """package roro.stellar.server.userservice
import android.os.Bundle
import com.stellar.server.IUserServiceCallback
class UserServiceManager {
 var stopped:String?=null
 fun startUserService(uid:Int,pid:Int,args:Bundle,cb:IUserServiceCallback?):String="test-token"
 fun stopUserService(token:String) {stopped=token}
}
""",
'Checks.kt': """import android.os.*
import android.content.ComponentName
import com.stellar.server.IStellarApplication
import moe.shizuku.server.IShizukuServiceConnection
import roro.stellar.server.*
import roro.stellar.server.shizuku.*
import roro.stellar.server.service.permission.PendingPermissionConfirmations
import rikka.hidden.compat.PackageManagerApis
import roro.stellar.server.util.PackageManagerCompat
fun main() {
 val config=ConfigManager()
 config.createConfigWithAllPermissions(10101,"test.owner")
 config.updatePermission(10101,"stellar",1)
 check(config.getPermissionFlag(10101,"stellar")==1)
 check(config.getPermissionFlag(10101,"stellar")==1)
 PackageManagerApis.packages[10101]=arrayOf("test.replacement")
 check(config.getPermissionFlag(10101,"stellar")==0)
 config.createConfigWithAllPermissions(10101,"test.replacement")
 config.updatePermission(10101,"stellar",1)
 PackageManagerCompat.signer="signer-b"
 check(config.find(10101)==null)
 println("PASS actual ConfigManager rejects recycled UID and changed signer; stable owner retains grant")
 PackageManagerApis.packages[10101]=arrayOf("test.owner")
 PendingPermissionConfirmations.register(7,10101,201,"stellar")
 PendingPermissionConfirmations.register(7,10102,202,"stellar")
 check(!PendingPermissionConfirmations.consume(7,10101,999,"stellar"))
 check(PendingPermissionConfirmations.consume(7,10101,201,"stellar"))
 check(!PendingPermissionConfirmations.consume(7,10101,201,"stellar"))
 check(PendingPermissionConfirmations.consume(7,10102,202,"stellar"))
 for (i in 0..15) PendingPermissionConfirmations.register(i,10101,201,"stellar")
 check(runCatching {PendingPermissionConfirmations.register(100,10101,201,"stellar")}.exceptionOrNull() is SecurityException)
 val pool=java.util.concurrent.Executors.newFixedThreadPool(8)
 PendingPermissionConfirmations.register(999,10102,202,"stellar")
 val consumed=(0..31).map {pool.submit<Boolean>{PendingPermissionConfirmations.consume(999,10102,202,"stellar")}}.count {it.get()}
 pool.shutdown(); check(consumed==1)
 println("PASS pending requests isolate UID/PID, reject replay, bound growth, and consume atomically")
 val users=roro.stellar.server.userservice.UserServiceManager()
 val adapter=ShizukuUserServiceAdapter(users)
 val options=Bundle().apply {putParcelable(ShizukuApiConstants.UserServiceArgs.COMPONENT,ComponentName("test.owner","test.owner.Service"))}
 var received:IBinder?=null
 val owner=object:IShizukuServiceConnection {override fun connected(binder:IBinder){received=binder};override fun died(){}}
 check(adapter.addUserService(owner,options,10101,201)==0)
 val binder=object:IBinder{}
 adapter.onStellarServiceAttached("test-token",binder)
 options.putBoolean(ShizukuApiConstants.UserServiceArgs.NO_CREATE,true)
 check(runCatching{adapter.addUserService(owner,options,10102,202)}.exceptionOrNull() is SecurityException)
 check(runCatching{adapter.removeUserService(owner,options,10102)}.exceptionOrNull() is SecurityException)
 check(users.stopped==null)
 check(adapter.addUserService(owner,options,10101,201)==0 && received===binder)
 adapter.removeUserService(owner,options,10101)
 check(users.stopped=="test-token")
 println("PASS user service owner can reconnect/stop; foreign UID cannot retrieve or delete")
}
""",
'Packages.kt': """package android.content.pm
class Signature(private val value:String) {fun toByteArray()=value.toByteArray()}
class ApplicationInfo {var uid=10101;var metaData:android.os.Bundle?=null}
class PackageInfo {var packageName="";var applicationInfo:ApplicationInfo?=ApplicationInfo();var signatures:Array<Signature>?=arrayOf(Signature("signer-a"))}
object PackageManager {const val MATCH_ALL=1;const val GET_META_DATA=2;const val GET_PROVIDERS=4;const val GET_SIGNATURES=8}
""",
'Compat.kt': """package rikka.hidden.compat
object PackageManagerApis {
 val packages=mutableMapOf(10101 to arrayOf("test.owner"),10102 to arrayOf("test.attacker"))
 fun getPackagesForUidNoThrow(uid:Int):Array<String> = packages[uid] ?: emptyArray()
}
object UserManagerApis {fun getUserIdsNoThrow():List<Int> = emptyList()}
object ActivityManagerApis {
 fun getContentProviderExternal(a:String,b:Int,c:android.os.IBinder?,d:String):android.content.IContentProvider?=null
 fun removeContentProviderExternal(a:String,b:android.os.IBinder?) {}
}
""",
'PackageManagerCompat.kt': """package roro.stellar.server.util
object PackageManagerCompat {
 var signer="signer-a"
 fun getPackageInfo(pkg:String,flags:Long,user:Int)=android.content.pm.PackageInfo().apply {packageName=pkg; signatures=arrayOf(android.content.pm.Signature(signer))}
 fun getApplicationInfo(pkg:String,flags:Long,user:Int)=android.content.pm.ApplicationInfo()
 fun getInstalledPackagesNoThrow(flags:Long,user:Int):List<android.content.pm.PackageInfo?> = emptyList()
}
object UserHandleCompat {fun getUserId(uid:Int)=uid/100000}
object ProviderDiscovery {fun hasStellarProvider(p:android.content.pm.PackageInfo)=false;fun hasShizukuProvider(p:android.content.pm.PackageInfo)=false}
""",
'Provider.kt': """package roro.stellar.server.api
object IContentProviderUtils {fun callCompat(a:android.content.IContentProvider,b:Any?,c:String,d:String,e:Any?,f:android.os.Bundle):android.os.Bundle?=null}
""",
'Handler.kt': """package roro.stellar.server.ktx
class Handler {fun hasCallbacks(r:Runnable)=false;fun removeCallbacks(r:Runnable){};fun postDelayed(r:Runnable,t:Long){}}
val workerHandler=Handler()
""",
'ServerConstants.kt': """package roro.stellar.server
object ServerConstants {const val MANAGER_APPLICATION_ID="test.manager"}
""",
'Gson.kt': """package com.google.gson
class Gson {fun <T> fromJson(s:String,c:Class<T>):T?=null;fun toJson(value:Any)="{}"}
class GsonBuilder {fun create()=Gson();fun setVersion(v:Double)=this}
""",
'Annotations.kt': """package com.google.gson.annotations
annotation class SerializedName(val value:String)
""",
}
files=[]
for name, content in sources.items():
    target=out/name
    target.write_text(content,encoding='utf-8')
    files.append(str(target))
base=repo/'embedded/server/src/main/kotlin/roro/stellar/server'
for path in ['ClientManager.kt','ClientRecord.kt','shizuku/ShizukuApiConstants.kt','shizuku/ShizukuUserServiceAdapter.kt','shizuku/ShizukuUserServiceRecord.kt','userservice/UserServiceConstants.kt','ConfigManager.kt','StellarConfig.kt','service/permission/PendingPermissionConfirmations.kt']:
    files.append(str(base/path))
jar=out/'checks.jar'
stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
subprocess.run(['java','-cp',str(lib/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(jar),*files],check=True)
subprocess.run(['java','-cp',str(jar)+__import__('os').pathsep+str(stdlib),'ChecksKt'],check=True)
