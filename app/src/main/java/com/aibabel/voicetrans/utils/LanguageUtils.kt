package com.aibabel.voicetrans.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LanguageUtils {

    /**
     * 根据指定语言获取字符串资源
     * @param context 上下文
     * @param lang 语言代码 ("zh" 中文, "id" 印尼语)
     * @param resId 字符串资源ID
     * @return 对应语言的字符串
     */
    fun getStringByLang(context: Context, lang: String, resId: Int): String {
        val resources = context.resources
        val config = Configuration(resources.configuration)
        
        // Android 标准 locale 代码: 中文="zh", 印尼语="in"
        val localeCode = when (lang) {
            "id" -> "in"
            else -> lang
        }
        config.setLocale(Locale(localeCode))
        
        val tempContext = context.createConfigurationContext(config)
        return tempContext.getString(resId)
    }
}
