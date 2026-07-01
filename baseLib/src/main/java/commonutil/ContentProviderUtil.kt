package commonutil

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
import com.apkfuns.logutils.LogUtils
import java.util.TimeZone

object ContentProviderUtil {
    /**
     * 获取当前定位城市（省）
     *
     * @param context
     * @return
     */
    fun getCity(): String {
        val cityName = LocationDataUtils.getInfo(LocationDataUtils.COLUMN_CITY)
        return cityName
    }

    fun getLat(): Float {
        val lat = LocationDataUtils.getInfo(LocationDataUtils.COLUMN_LATITUDE)
        LogUtils.d("getLat: $lat")
        return if (lat.isNullOrEmpty()) {
            0.0f
        }else {
            lat.toFloat()
        }
    }
    fun getLng(): Float {
        val lng = LocationDataUtils.getInfo(LocationDataUtils.COLUMN_LONGITUDE)
        LogUtils.d("getLng: $lng")
        return if (lng.isNullOrEmpty()) {
            0.0F
        }else {
            lng.toFloat()
        }
    }

    /**
     * 判定国内外
     * @return  true 国内     false 国外
     */
    fun isInCN(): Boolean {
        val locationWhere = LocationDataUtils.getInfo(LocationDataUtils.COLUMN_LOCATIONWHERE)
        if (!TextUtils.isEmpty(locationWhere)) {
            if (locationWhere == "0") {
                Log.e("isInCN", "-----------定位:国外")
                return false
            } else if (locationWhere == "1") {
                Log.e("isInCN", "-----------定位:国内")
                return true
            }
        } else {
            val timerID = TimeZone.getDefault().id
            if (timerID == "Asia/Shanghai") {
                Log.e("isInCN", "-----------时区:国内")
                return true
            } else {
                Log.e("isInCN", "-----------时区:国外")
                return false
            }
        }
        return false
    }
    /**
     * 查询 Content Provider 数据
     *
     * @param context 上下文
     * @param providerUri Provider URI
     * @param projection 查询列数组，null表示查询所有列
     * @param selection 查询条件
     * @param selectionArgs 查询参数
     * @param sortOrder 排序规则
     * @return 查询结果Map集合
     */
    fun query(
        context: Context,
        providerUri: String,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<Map<String, Any?>> {
        val uri = providerUri.toUri()
        val resolver = context.contentResolver
        val results = mutableListOf<Map<String, Any?>>()

        resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val item = mutableMapOf<String, Any?>()
                for (i in 0 until cursor.columnCount) {
                    val columnName = cursor.getColumnName(i)
                    val value = when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                        else -> cursor.getString(i)
                    }
                    item[columnName] = value
                }
                results.add(item)
            }
        }

        return results
    }

    /**
     * 获取第一条记录
     *
     * @param context 上下文
     * @param providerUri Provider URI
     * @param projection 查询列数组
     * @return 第一条记录的Map
     */
    fun queryFirst(
        context: Context,
        providerUri: String,
        projection: Array<String>? = null
    ): Map<String, Any?>? {
        val results = query(context, providerUri, projection, null, null, null)
        return results.firstOrNull()
    }

    /**
     * 插入数据到 Content Provider
     *
     * @param context 上下文
     * @param providerUri Provider URI
     * @param values 要插入的值
     * @return 插入后的URI，失败返回null
     */
    fun insert(
        context: Context,
        providerUri: String,
        values: Map<String, Any?>
    ): Uri? {
        val uri = providerUri.toUri()
        val resolver = context.contentResolver
        val contentValues = mapToContentValues(values)

        return try {
            resolver.insert(uri, contentValues)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 更新 Content Provider 数据
     *
     * @param context 上下文
     * @param providerUri Provider URI
     * @param values 要更新的值
     * @param selection 更新条件
     * @param selectionArgs 更新参数
     * @return 影响的行数
     */
    fun update(
        context: Context,
        providerUri: String,
        values: Map<String, Any?>,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        val uri = providerUri.toUri()
        val resolver = context.contentResolver
        val contentValues = mapToContentValues(values)

        return try {
            resolver.update(uri, contentValues, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 删除 Content Provider 数据
     *
     * @param context 上下文
     * @param providerUri Provider URI
     * @param selection 删除条件
     * @param selectionArgs 删除参数
     * @return 影响的行数
     */
    fun delete(
        context: Context,
        providerUri: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Int {
        val uri = providerUri.toUri()
        val resolver = context.contentResolver

        return try {
            resolver.delete(uri, selection, selectionArgs)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * 将 Map 转换为 ContentValues
     */
    private fun mapToContentValues(map: Map<String, Any?>): ContentValues {
        val contentValues = ContentValues()
        map.forEach { (key, value) ->
            when (value) {
                is String -> contentValues.put(key, value)
                is Int -> contentValues.put(key, value)
                is Long -> contentValues.put(key, value)
                is Float -> contentValues.put(key, value)
                is Double -> contentValues.put(key, value)
                is Boolean -> contentValues.put(key, value)
                is Byte -> contentValues.put(key, value)
                is ByteArray -> contentValues.put(key, value)
                is Short -> contentValues.put(key, value)
                null -> contentValues.putNull(key)
            }
        }
        return contentValues
    }
}
