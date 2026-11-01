package roro.stellar.server.service.permission

internal object PendingPermissionConfirmations {
    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_PER_UID = 16
    private const val MAX_PENDING = 1024
    private data class Key(val uid: Int, val pid: Int, val code: Int, val permission: String)
    private val pendingMap = mutableMapOf<Key, Long>()

    @Synchronized
    fun register(requestCode: Int, uid: Int, pid: Int, permission: String) {
        val now = System.nanoTime() / 1_000_000
        pendingMap.entries.removeAll { it.value <= now }
        val key = Key(uid, pid, requestCode, permission)
        if (key !in pendingMap && (pendingMap.size >= MAX_PENDING ||
                    pendingMap.keys.count { it.uid == uid } >= MAX_PER_UID)) {
            throw SecurityException("Too many pending permission requests")
        }
        pendingMap[key] = now + TTL_MS
    }

    @Synchronized
    fun consume(requestCode: Int, uid: Int, pid: Int, permission: String): Boolean {
        val expires = pendingMap.remove(Key(uid, pid, requestCode, permission)) ?: return false
        return expires > System.nanoTime() / 1_000_000
    }
}
