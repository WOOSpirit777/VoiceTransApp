package commonutil

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.BaseApplication

/**
 * 位置数据工具类
 * created by Vvv
 * version:1.0
 * 2025-10-11 10:44:34
 */
object LocationDataUtils {

    private const val LOCATION_CONTENT_URI =
        "content://com.aibabel.location.provider.RoomContentProvider/location"
    val CONTENT_URI_LOCATION =
        "content://com.aibabel.location.provider.RoomContentProvider/location".toUri()
    /**详细地址信息*/
    const val COLUMN_ADDR = "addr"
    const val COLUMN_COUNTRY = "country" //国家
    const val COLUMN_PROVINCE = "province" //省份
    const val COLUMN_CITY = "city" //城市
    const val COLUMN_DISTRICT = "district" //区县
    const val COLUMN_STREET = "street" //街道信息
    const val COLUMN_DESCRIBE = "describe" //位置描述信息
    /**纬度*/
    const val COLUMN_LATITUDE = "latitude"
    /**经度*/
    const val COLUMN_LONGITUDE = "longitude"
    /**国内外        1是国内    0是国外*/
    const val COLUMN_LOCATIONWHERE = "locationWhere"
    /**是否是esim卡        1是    0不是*/
    const val COLUMN_ISESIM = "isEsim" //是否是esim卡

    /**
     * 使用默认uri
     *
     * @param columnName 列名
     * @return 列名对应的值
     */
    fun getInfo(columnName: String): String {
        return getInfo(CONTENT_URI_LOCATION, columnName)
    }

    /**
     * @param providerUri uri地址
     * @param columnName  列名
     * @return 列名对应的值
     */
    fun getInfo(providerUri: Uri, columnName: String): String {
        var info = ""
        val cursor = BaseApplication.mContext.contentResolver.query(
            providerUri, null, null, null, null
        )
        try {
            cursor?.use {
                it.moveToFirst()
                val columnIndex = it.getColumnIndex(columnName)
                if (columnName == COLUMN_LATITUDE || columnName == COLUMN_LONGITUDE) {
                    val doubleValue = it.getDouble(columnIndex)
                    info = doubleValue.toString()
                } else {
                    info = it.getString(columnIndex)
                }
                LogUtils.e("info:$info")
            }
        } catch (e: Exception) {
            LogUtils.e("没有获取到定位信息")
            e.printStackTrace()
        }
        return info
    }
    /**
     * 从ContentProvider获取位置数据
     * @param context 上下文
     * @return 包含所有位置数据的列表，如果出错则返回空列表
     */
    fun fetchLocationData(context: Context): List<LocationData> {
        val locationList = mutableListOf<LocationData>()
        val contentResolver: ContentResolver = context.contentResolver
        val uri = Uri.parse(LOCATION_CONTENT_URI)

        try {
            val cursor = contentResolver.query(
                uri,
                null,  // 返回所有列
                null,  // 无筛选条件
                null,  // 无参数
                null   // 默认排序
            )

            cursor?.use {
                val latitudeIndex = it.getColumnIndex("latitude")
                val longitudeIndex = it.getColumnIndex("longitude")

                it.moveToNext()
                if (latitudeIndex >= 0 && longitudeIndex >= 0) {
                    val latitude = it.getDouble(latitudeIndex)
                    val longitude = it.getDouble(longitudeIndex)
                    locationList.add(LocationData(latitude, longitude))
                    LogUtils.d("Fetched location: latitude=$latitude, longitude=$longitude")
                }
            }
        } catch (e: Exception) {
            LogUtils.e("Error fetching location data: ${e.message}")
        }

        return locationList
    }

    /**
     * 位置数据模型类
     */
    data class LocationData(
        val latitude: Double,
        val longitude: Double
    )
}