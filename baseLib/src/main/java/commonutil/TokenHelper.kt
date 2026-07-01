package commonutil

import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.util.MMKVConst
import com.quniu.baselib.util.MMKVUtils
import netlib.NetConst

object TokenHelper {
    fun clearToken() {
        NetConst.TOKEN = ""
        NetConst.TOKEN_TYPE = ""
        MMKVUtils.putMultiData(MMKVConst.TOKEN, "", MMKVConst.MMKV_ID)
        MMKVUtils.putMultiData(MMKVConst.TOKEN_TYPE, "", MMKVConst.MMKV_ID)
        val savedToken = MMKVUtils.getMultiStr(MMKVConst.TOKEN, MMKVConst.MMKV_ID)
        val savedTokenType = MMKVUtils.getMultiStr(MMKVConst.TOKEN_TYPE, MMKVConst.MMKV_ID)
        LogUtils.d("savedToken: $savedToken, savedTokenType: $savedTokenType")
    }
}