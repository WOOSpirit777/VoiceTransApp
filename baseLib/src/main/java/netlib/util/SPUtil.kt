package netlib.util

import android.content.Context
import com.quniu.baselib.BaseApplication

object SPUtil {

    private const val SP_NAME = "sp_foam"

    fun putData(key: String, data: Any?) {
        data?.let {
            val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            val editor = sp.edit()
            when (data) {
                is Boolean -> editor.putBoolean(key, data)
                is String -> editor.putString(key, data)
                is Int -> editor.putInt(key, data)
                is Float -> editor.putFloat(key, data)
                is Long -> editor.putLong(key, data)
            }
            editor.apply()
        }
    }

    fun getStringData(key: String): String? {
        val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getString(key, "")
    }

    fun getIntData(key: String): Int {
        val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getInt(key, 0)
    }

    fun getBooleanData(key: String): Boolean {
        val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key, false)
    }

    fun getSwitchData(key: String): Boolean {
        val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(key, true)
    }

    fun getLongData(key: String): Long {
        val sp = BaseApplication.mContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        return sp.getLong(key, 0L)
    }
}