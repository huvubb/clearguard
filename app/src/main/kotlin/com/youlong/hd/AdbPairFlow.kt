package com.youlong.hd

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.lifecycle.Observer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbClient
import roro.stellar.manager.adb.AdbKey
import roro.stellar.manager.adb.AdbMdns
import roro.stellar.manager.adb.AdbPairingClient
import roro.stellar.manager.adb.PreferenceAdbKeyStore
import roro.stellar.manager.startup.worker.AdbStarter
import roro.stellar.manager.util.EnvironmentUtils


@SuppressLint("NewApi")
object AdbPairFlow {

    
    private const val KEY_NAME = "stellar"

    
    fun adbKey(): AdbKey =
        AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), KEY_NAME)

    
    @SuppressLint("NewApi")
    fun pair(port: Int, code: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || port !in 1..65535 || !code.matches(Regex("[0-9]{6}"))) return false
        return AdbPairingClient("127.0.0.1", port, code, adbKey()).use { client ->
            client.start()
        }
    }

    
    fun grantSecureSettings(port: Int, pkg: String, onOutput: (String) -> Unit) {
        require(PkgGuard.isValid(pkg) && port in 1..65535) { "Invalid package or port" }
        AdbClient("127.0.0.1", port, adbKey()).use { client ->
            client.connect()
            client.shellCommand("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS") { out ->
                onOutput(String(out).trim())
            }
        }
    }

    
    suspend fun discover(
        context: Context,
        serviceType: String,
        timeoutMs: Long,
        onStatus: (String) -> Unit = {}
    ): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val deferred = CompletableDeferred<Int>()
        var mdns: AdbMdns? = null
        return try {
            val observer = Observer<Int> { port ->
                if (port > 0 && !deferred.isCompleted) deferred.complete(port)
            }
            mdns = AdbMdns(
                context,
                serviceType,
                observer,
                { if (!deferred.isCompleted) deferred.complete(-1) },
                { status -> if (status.isNotEmpty()) onStatus(status) },
                AdbMdns.MAX_REFRESH_COUNT
            )
            mdns.start()
            val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
            result?.takeIf { it > 0 }
        } catch (t: Throwable) {
            onStatus("搜索服务出错：" + (t.message ?: t.javaClass.simpleName))
            null
        } finally {
            try {
                mdns?.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    
    suspend fun reconnect(context: Context, onStatus: (String) -> Unit): Boolean {
        if (roro.stellar.Stellar.pingBinder()) return true
        val saved = StellarSettings.getPreferences().getInt("clearguard_last_adb_port", -1)
        val system = systemPort()
        for (port in listOf(saved, system).filter { it in 1..65535 }.distinct()) {
            onStatus("正在连接已授权端口：$port")
            if (AdbStarter.startAdb("127.0.0.1", port, 1)) return true
        }
        onStatus("正在发现无线调试连接端口…")
        val discovered = discover(context, AdbMdns.TLS_CONNECT, 8_000L, onStatus) ?: return false
        return AdbStarter.startAdb("127.0.0.1", discovered, 2)
    }

    suspend fun startService(port: Int): Boolean =
        AdbStarter.startAdb("127.0.0.1", port)

    
    suspend fun waitBinder(timeoutMs: Long = 20_000L): Boolean =
        AdbStarter.waitForBinder(timeoutMs)

    
    fun systemPort(): Int = try {
        EnvironmentUtils.getAdbTcpPort()
    } catch (t: Throwable) {
        -1
    }
}
