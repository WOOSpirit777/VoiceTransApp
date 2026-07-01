package commonutil

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 从 location 模块暴露的 ContentProvider 读取最新定位的工具。
 * 注意：ContentProvider 的 authority 和 path 必须与 location 模块一致。
 */
object LocationProviderClient {
    private const val AUTHORITY = "com.aibabel.location.provider.RoomContentProvider"
    private val BASE_URI = "content://$AUTHORITY/location"
    private val URI = BASE_URI.toUri()

    data class SimpleLocation(
        val id: String?,
        val latitude: Double?,
        val longitude: Double?,
        val city: String?,
        val province: String?,
        val country: String?,
        val address: String?,
        val district: String?,
        val street: String?,
        val coorType: String?,
        val ips: String?,
        val describe: String?,
        val locationWhere: String?
    )

    // 同步查询（在调用方的后台线程调用）
    fun getLatestSync(context: Context, force: Boolean = false, timeoutMs: Long = 15_000L): SimpleLocation? {
        val cr = context.contentResolver
        try {
            val queryUri = if (force) {
                // content://.../location?force=1&timeout=15000
                "$BASE_URI?force=1&timeout=$timeoutMs".toUri()
            } else URI
            val cursor = cr.query(queryUri, null, null, null, null) ?: return null
            cursor.use { c ->
                if (!c.moveToLast()) return null
                return SimpleLocation(
                    id = getStringSafe(c, "_id"),
                    latitude = getDoubleSafe(c, "latitude"),
                    longitude = getDoubleSafe(c, "longitude"),
                    city = getStringSafe(c, "city"),
                    province = getStringSafe(c, "province"),
                    country = getStringSafe(c, "country"),
                    address = getStringSafe(c, "address") ?: getStringSafe(c, "addr"),
                    district = getStringSafe(c, "district"),
                    street = getStringSafe(c, "street"),
                    coorType = getStringSafe(c, "coorType") ?: getStringSafe(c, "coor"),
                    ips = getStringSafe(c, "ips"),
                    describe = getStringSafe(c, "describe"),
                            locationWhere = getStringSafe(c, "locationWhere")
                )
            }
        } catch (_: Throwable) {
            Log.w("LocationProviderClient", "query failed")
        }
        return null
    }

    // 异步查询：在后台线程查询并在主线程回调
    fun getLatest(context: Context, force: Boolean = false, timeoutMs: Long = 15_000L, cb: (SimpleLocation?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val loc = getLatestSync(context, force, timeoutMs)
            withContext(Dispatchers.Main) {
                cb(loc)
            }
        }
    }

    private fun getStringSafe(c: Cursor, name: String): String? {
        return try {
            val idx = c.getColumnIndex(name)
            if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun getDoubleSafe(c: Cursor, name: String): Double? {
        return try {
            val idx = c.getColumnIndex(name)
            if (idx >= 0 && !c.isNull(idx)) c.getDouble(idx) else null
        } catch (_: Throwable) {
            null
        }
    }
}