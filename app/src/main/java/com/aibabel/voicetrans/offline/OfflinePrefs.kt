package com.aibabel.voicetrans.offline

import android.content.Context
import android.content.SharedPreferences
import com.aibabel.asr.onnx.OnnxAsrConfig
import com.aibabel.voicetrans.ui.SettingsActivity

/**
 * 离线翻译的本地配置。
 *
 * 只负责「用户意愿」的持久化（是否启用、模型来源），不碰任何模型文件或引擎；
 * 真正的加载由 [OfflineTranslationManager] 完成。
 */
object OfflinePrefs {

    /** 是否启用离线翻译（设置页开关）。关掉时应用启动不加载模型，节省内存。 */
    const val KEY_ENABLED = "offline_enabled"

    /**
     * 模型来源：
     * - [SOURCE_DOWNLOAD]（默认）服务端下载，首次联网拉取，之后走本地缓存；
     * - [SOURCE_BUILTIN]  APK 内置（assets），首次启动拷贝，零网络依赖。
     */
    const val KEY_SOURCE = "offline_model_source"

    const val SOURCE_DOWNLOAD = 0
    const val SOURCE_BUILTIN = 1

    /** 模型服务地址（DOWNLOAD 模式用）。留空则用 [DEFAULT_SERVER] 或最后一次成功的地址。 */
    const val KEY_SERVER = "offline_server"

    const val DEFAULT_SERVER = "http://127.0.0.1:8765"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun source(context: Context): Int = prefs(context).getInt(KEY_SOURCE, SOURCE_DOWNLOAD)

    fun setSource(context: Context, source: Int) {
        prefs(context).edit().putInt(KEY_SOURCE, source).apply()
    }

    fun server(context: Context): String =
        prefs(context).getString(KEY_SERVER, DEFAULT_SERVER)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER

    fun setServer(context: Context, url: String) {
        prefs(context).edit().putString(KEY_SERVER, url.trim()).apply()
    }

    /** 当前来源对应的安装模式。 */
    fun installMode(context: Context): OnnxAsrConfig.InstallMode =
        if (source(context) == SOURCE_BUILTIN) {
            OnnxAsrConfig.InstallMode.COPY
        } else {
            OnnxAsrConfig.InstallMode.DOWNLOAD
        }

    fun sourceLabel(context: Context): String = when (source(context)) {
        SOURCE_BUILTIN -> "APK 内置"
        else -> "服务端下载"
    }
}