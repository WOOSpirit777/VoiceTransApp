package com.quniu.baselib.util

import android.content.Context
import android.os.Parcelable
import com.tencent.mmkv.MMKV
import java.util.Collections

object MMKVUtils {

    val mmkv by lazy {
        MMKV.defaultMMKV()
    }

    fun initialize(context: Context) {
        MMKV.initialize(context)
    }

    fun put(key: String, value: Any?): Boolean {
        return when (value) {
            is String -> mmkv.encode(key, value)
            is Float -> mmkv.encode(key, value)
            is Boolean -> mmkv.encode(key, value)
            is Int -> mmkv.encode(key, value)
            is Long -> mmkv.encode(key, value)
            is Double -> mmkv.encode(key, value)
            else -> false
        }
    }
    // 获取指定 ID 的 MMKV 实例（多进程支持）
    private fun getMMKVWithID(mmkvId: String, mode: Int = MMKV.SINGLE_PROCESS_MODE): MMKV {
        return MMKV.mmkvWithID(mmkvId, mode)
    }

fun putMultiData(key: String, value: String?, mmkvId: String): Boolean {
    return try {
        val mm = getMMKVWithID(mmkvId, MMKV.MULTI_PROCESS_MODE)
        mm.encode(key, value)
        true
    } catch (_: Throwable) {
        false
    }
}
    fun getMultiData(key: String, default: Any, mmkvId: String): Any {
        val mmkv = getMMKVWithID(mmkvId, MMKV.MULTI_PROCESS_MODE)
        return when (default) {
            is String -> mmkv.decodeString(key, default) ?: ""
            is Int -> mmkv.decodeInt(key, default)
            is Float -> mmkv.decodeFloat(key, default)
            is Boolean -> mmkv.decodeBool(key, default)
            is Double -> mmkv.decodeDouble(key, default)
            is Long -> mmkv.decodeLong(key, default)
            else -> ""
        }
    }
    fun getMultiStr(key: String, mmkvId: String): String {
        val mmkv = getMMKVWithID(mmkvId, MMKV.MULTI_PROCESS_MODE)
        return mmkv.decodeString(key, "") ?: ""
    }


    /**
     * @param name 文件名字
     * @author wangrui
     * @date 2022/8/18 17:17
     * @description 单独存储到一个文件中
     **/
    fun putNewFile(key: String, value: Any?, name: String): Boolean {
        val mmkv2 = MMKV.mmkvWithID(name)
        return when (value) {
            is String -> mmkv2.encode(key, value)
            is Float -> mmkv2.encode(key, value)
            is Boolean -> mmkv2.encode(key, value)
            is Int -> mmkv2.encode(key, value)
            is Long -> mmkv2.encode(key, value)
            is Double -> mmkv2.encode(key, value)
            else -> false
        }

    }

    fun get(key: String, default: Any?): Any {
        return when (default) {
            is String -> mmkv.decodeString(key, default) ?: ""
            is Int -> mmkv.decodeInt(key, default)
            is Float -> mmkv.decodeFloat(key, default)
            is Boolean -> mmkv.decodeBool(key, default)
            is Double -> mmkv.decodeDouble(key, default)
            is Long -> mmkv.decodeLong(key, default)
            else -> ""
        }
    }

    /**
     * mmkv目前只支持存入Parcelable序列化的
     */
    fun <T : Parcelable> put(key: String, t: T?): Boolean {
        if (t == null) {
            return false
        }
        return mmkv.encode(key, t)!!
    }

    fun put(key: String, sets: Set<String>?): Boolean {
        if (sets == null) {
            return false
        }
        return mmkv?.encode(key, sets)!!
    }

    fun getInt(key: String, default: Int = 0): Int {
        return mmkv?.decodeInt(key, default) ?: default
    }


    fun getDouble(key: String, default: Double = 0.00): Double {
        return mmkv?.decodeDouble(key, default) ?: default
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return mmkv?.decodeLong(key, default) ?: default
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return mmkv?.decodeBool(key, default) ?: default
    }

    fun getFloat(key: String, default: Float = 0F): Float {
        return mmkv?.decodeFloat(key, 0F) ?: default
    }


    fun getString(key: String, default: String = ""): String {
        return mmkv?.decodeString(key, default) ?: default
    }

    /**
     * MKUtils.getParcelable<Class>("")
     */
    inline fun <reified T : Parcelable> getParcelable(key: String): T? {
        return mmkv?.decodeParcelable(key, T::class.java)
    }

    fun getStringSet(key: String): Set<String>? {
        return mmkv?.decodeStringSet(key, Collections.emptySet())
    }

    // ------------------ Multi-process typed helpers ------------------
    fun putMultiInt(key: String, value: Int, mmkvId: String): Boolean {
        return try {
            val mm = getMMKVWithID(mmkvId, MMKV.MULTI_PROCESS_MODE)
            mm.encode(key, value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getMultiInt(key: String, default: Int = 0, mmkvId: String): Int {
        return try {
            val mm = getMMKVWithID(mmkvId, MMKV.MULTI_PROCESS_MODE)
            mm.decodeInt(key, default)
        } catch (_: Throwable) {
            default
        }
    }

    // -------------------------------------
    // Expiration / TTL helpers
    // -------------------------------------
    private fun expiryKey(key: String) = "${key}_expiry"

    /**
     * Put a value with a TTL (in milliseconds). When TTL elapses the stored value will be
     * treated as expired and will be removed on next access.
     * Returns true if value write succeeded.
     */
    fun putWithExpire(key: String, value: Any?, ttlMillis: Long): Boolean {
        val ok = put(key, value)
        return try {
            if (ok) {
                val expiry = if (ttlMillis > 0) System.currentTimeMillis() + ttlMillis else 0L
                mmkv.encode(expiryKey(key), expiry)
            } else {
                mmkv.encode(expiryKey(key), 0L)
            }
            ok
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Check whether the key has expired. Returns true if an expiry exists and current time is past it.
     */
    fun isExpired(key: String): Boolean {
        val expiry = mmkv.decodeLong(expiryKey(key), 0L)
        return expiry != 0L && System.currentTimeMillis() > expiry
    }

    /**
     * Get remaining TTL in milliseconds for a key. Returns 0 if no expiry or already expired.
     */
    fun getRemainingTtl(key: String): Long {
        val expiry = mmkv.decodeLong(expiryKey(key), 0L)
        if (expiry == 0L) return 0L
        val remain = expiry - System.currentTimeMillis()
        return if (remain > 0L) remain else 0L
    }

    /**
     * Get value with expiry-check. If expired, the value and its expiry metadata will be removed
     * and the provided default will be returned.
     */
    fun getWithExpire(key: String, default: Any): Any {
        try {
            val expiry = mmkv.decodeLong(expiryKey(key), 0L)
            if (expiry != 0L && System.currentTimeMillis() > expiry) {
                // expired
                removeKey(key)
                mmkv.removeValueForKey(expiryKey(key))
                return default
            }
        } catch (_: Throwable) {
            // ignore and fallback to normal get
        }
        return get(key, default)
    }

    /**
     * Clear expiry metadata for a key (keeps the value intact).
     */
    fun clearExpiry(key: String) {
        try {
            mmkv.removeValueForKey(expiryKey(key))
        } catch (_: Throwable) {}
    }

    fun removeKey(key: String) {
        mmkv.removeValueForKey(key)
    }

    fun clearAll() {
        mmkv.clearAll()
    }
    fun clearAllNamedInstances(vararg mmkvIds: String) {
        mmkv.clearAll()

        mmkvIds.forEach { id ->
            try {
                getMMKVWithID(id).clearAll()
            } catch (e: Exception) {
                // 处理异常
            }
        }
    }

}
