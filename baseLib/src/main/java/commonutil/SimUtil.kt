package commonutil

import android.Manifest
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.annotation.RequiresPermission

object SimUtil {

    /**
     * 获取当前启用的数据卡槽位
     * @return 0 卡1, 1 卡2, -1 未识别/无卡
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @JvmStatic
    fun getActiveDataSlot(context: Context): Int {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return -1
        // API >= 24 直接拿默认数据卡
        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (dataSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            val info = sm.getActiveSubscriptionInfo(dataSubId)
            return info?.simSlotIndex ?: -1
        }

        return -1
    }

}