package netlib.util

import android.os.Build
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.BaseApplication
import commonutil.CommonUtils
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

object ParamUtil {

    fun jsonToMap(jsonObject: JSONObject): HashMap<String, String> {
        val map = HashMap<String, String>()
        try {
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonObject[key].toString()
                map[key] = value
            }
        } catch (e: Exception) {

        }
        return map
    }
    fun getRequestBody(hashMap: HashMap<String, Any?>): RequestBody {
        with(hashMap) {
            put("sn", CommonUtils.getSN())
            put("sv", CommonUtils.getDeviceInfo())
            put("task_id", UUID.randomUUID().toString())
            put("time", System.currentTimeMillis()/1000)
            put("language", Locale.getDefault())
//            put("imei",CommonUtils.getIMEIBySlot(BaseApplication.mContext, 0))
        }
        // 将 HashMap 转换为 JSON 字符串
        val jsonObject = JSONObject()
        for ((key, value) in hashMap) {
            jsonObject.put(key, value)
        }
        val jsonString = jsonObject.toString()

        // 创建 JSON 格式的 RequestBody
        return RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonString)
    }

}
