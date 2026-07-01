package com.quniu.baselib.util

import com.quniu.baselib.BaseApplication

object DisplayUtil {

    /**
     * 屏幕宽
     */
    fun getScreenWith(): Int {
        val displayMetrics = BaseApplication.mContext.resources.displayMetrics
        return displayMetrics.widthPixels
    }

    /**
     * 屏幕搞
     */
    fun getScreenHeight(): Int {
        val displayMetrics = BaseApplication.mContext.resources.displayMetrics
        return displayMetrics.heightPixels
    }

    /**
     * dp 转换 px
     *
     * @param dp
     */
    fun dp2px(dp: Int): Int {
        val density = BaseApplication.mContext.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    /**
     * px 转换 dp
     *
     * @param px
     */
    fun px2dp(px: Int): Int {
        val density = BaseApplication.mContext.resources.displayMetrics.density
        return (px / density + 0.5f).toInt()
    }
    /**
     * sp 转换 px
     *
     * @param px
     */
    fun sp2px(sp: Float): Int {
        val scaledDensity = BaseApplication.mContext.resources.displayMetrics.scaledDensity
        return (sp * scaledDensity + 0.5f).toInt()
    }

    fun changeLongWithUnit(number: Long): String {
        return when {
            number > 10000L -> "${number / 10000}W"
            number > 1000L -> "${number / 1000}K"
            else -> "$number"
        }
    }
}
