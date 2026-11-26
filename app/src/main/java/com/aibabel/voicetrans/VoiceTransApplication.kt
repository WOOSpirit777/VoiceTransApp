package com.aibabel.voicetrans

import android.util.Log
import com.aibabel.voicetrans.offline.OfflineTranslationManager
import com.quniu.baselib.BaseApplication

/**
 * 宿主 Application：在进程启动阶段统一完成离线翻译模型的初始化。
 *
 * ## 为什么放在这里
 * 离线模型加载（中文 24MB + 印尼语 164MB，第二次启动走缓存也要做完整性校验）
 * 是一个秒级操作。如果放到 Activity 里做，用户每次进入离线页面都要等一次；
 * 放在进程启动阶段，则：
 * - 首次进入离线界面时通常已经就绪，可直接说话；
 * - 页面来回切换、Activity 重建都不会触发重新加载；
 * - 模型下载/拷贝只发生一次，二次启动直接复用本地文件。
 *
 * 初始化是**异步**的（在 SDK 的 IO 线程上跑），不会阻塞应用启动。
 *
 * ## 开关联动
 * 只有用户在设置页打开「离线翻译」后才真正加载模型（见 [OfflineTranslationManager.bootstrap]）。
 * 关闭状态下零内存占用，避免给不需要离线能力的设备增加负担。
 */
class VoiceTransApplication : BaseApplication() {

    override fun onCreate() {
        super.onCreate()
        // 兜底：把未捕获异常写进 logcat（E 级），便于在设备上定位偶发崩溃。
        // 线上版本可关闭；这里保留成本极低（只包一层 handler）。
        installCrashLogger()
        val startedAt = System.currentTimeMillis()
        // 只做「按开关决定是否启动加载」，内部异步执行，不阻塞启动流程
        OfflineTranslationManager.bootstrap(this)
        Log.i(
            TAG,
            "Application.onCreate 完成 cost=${System.currentTimeMillis() - startedAt}ms " +
                "offlineState=${OfflineTranslationManager.current().state}",
        )
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val TAG = "VoiceTransApp"
    }
}