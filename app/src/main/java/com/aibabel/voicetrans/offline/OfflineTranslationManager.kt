package com.aibabel.voicetrans.offline

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.asr.onnx.translate.BilingualCallbackAdapter
import com.aibabel.asr.onnx.translate.BilingualOptions
import com.aibabel.asr.onnx.translate.BilingualTranslateSdk
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 离线翻译的全局状态中心。
 *
 * ## 职责
 * - 在 **Application 启动阶段**触发一次初始化（`BilingualTranslateSdk.init`），
 *   之后整个进程复用同一份引擎，页面来回切换不会重新加载；
 * - 对外暴露可观察的状态（未启用 / 加载中 / 就绪 / 失败），供设置页与离线界面订阅；
 * - 二次启动走本地缓存：模型已装好时 `initModel` 命中缓存（fromCache=true），
 *   只做校验与加载，不产生任何网络请求或文件拷贝。
 *
 * ## 为什么放在 Application
 * 题目要求「模型初始化统一放在应用启动阶段」。放在这里的好处是：
 * 用户第一次进入离线界面时模型已经就绪（或正在加载），不需要在
 * Activity 里等 30s 的加载；同时避免了「进页面就初始化一次」的重复开销。
 *
 * ## 与在线链路的关系
 * 完全独立：使用独立的 `modelRootDir`（`asr_onnx_models/offline`）与独立的
 * modelId（`offline-bi-zh` / `offline-bi-id`），不会与在线 WebSocket 链路冲突。
 */
object OfflineTranslationManager {

    private const val TAG = "OfflineManager"

    /** 离线模型的根目录名（与在线链路的 asr_onnx_models 隔离，避免互相覆盖）。 */
    private const val MODEL_ROOT = "asr_onnx_models_offline"

    enum class State {
        /** 用户未启用离线功能（设置页开关关闭）。 */
        DISABLED,

        /** 已启用，尚未开始加载。 */
        IDLE,

        /** 正在下载/校验/加载模型。 */
        LOADING,

        /** 双方向模型与翻译引擎就绪。 */
        READY,

        /** 加载失败。 */
        FAILED,
    }

    /** 状态快照，供 UI 直接渲染。 */
    data class Snapshot(
        val state: State = State.DISABLED,
        val progress: Int = 0,
        val message: String = "",
        /** 初始化耗时（ms），READY 时有效。 */
        val costMs: Long = 0L,
        /** MT 是否可用；false 时只有识别没有译文。 */
        val mtReady: Boolean = false,
        /** 已就绪的方向数（正常应为 2）。 */
        val readyDirections: Int = 0,
    )

    fun interface Observer {
        fun onChanged(snapshot: Snapshot)
    }

    /**
     * 会话事件监听：识别/翻译的过程与结果。
     *
     * SDK 的回调是单例级的（init 时注册一次），多个页面不能各自注册；
     * 因此由本 manager 统一持有 SDK 回调，再向订阅者广播。
     */
    interface SessionListener {
        /** 过程文本（含未确认半句）。 */
        fun onPartial(text: String)

        /**
         * 一段识别 + 翻译结果。
         *
         * @param asrText 识别原文
         * @param mtText 译文；翻译不可用或失败时为 null
         * @param direction 这句话的方向（副屏据此决定显示原文还是译文）
         */
        fun onSegment(asrText: String, mtText: String?, direction: BilingualTranslateSdk.Direction)

        /** 本次会话结束。 */
        fun onFinished(fullText: String, elapsedMs: Long)

        /** 录音状态变化。 */
        fun onRecordingChanged(recording: Boolean, zhToId: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<Observer>()
    private val sessionListeners = CopyOnWriteArrayList<SessionListener>()

    /**
     * 一条已完成的结果。
     *
     * 保留结构化字段（而不只拼成字符串）是因为副屏需要单独拿 asrText / mtText /
     * direction：副屏按「自身语言 == 源语言 → 原文；== 目标语言 → 译文」取内容，
     * 拼成 "原文\n→ 译文" 的整串在 240x320 的小屏上既放不下也没法分流。
     *
     * @param asrText 识别原文
     * @param mtText 译文；翻译不可用或失败时为 null
     * @param direction 这句话的方向
     */
    data class ResultLine(
        val asrText: String,
        val mtText: String?,
        val direction: BilingualTranslateSdk.Direction,
    ) {
        /** 主界面列表里的显示形态：有译文时第二行带箭头。 */
        fun display(): String = if (mtText.isNullOrBlank()) asrText else "$asrText\n→ $mtText"
    }

    /**
     * 已完成的结果历史。
     *
     * 放在 manager 而不是 Activity 里：页面重建（被系统回收/am start 重复拉起）
     * 后新实例能立即恢复已有内容，不会因为回调打给了旧实例而显示空白；
     * 副屏恢复时也能从这里取最后一条重刷。
     */
    private val history = CopyOnWriteArrayList<ResultLine>()

    /** 当前累积的结果（结构化）。 */
    fun results(): List<ResultLine> = history.toList()

    /** 当前累积的结果文本（每段一段，用空行分隔）。界面直接遍历用这个。 */
    fun resultHistory(): List<String> = history.map { it.display() }

    /** 最后一条结果；没有时返回 null。副屏恢复重刷用。 */
    fun lastResult(): ResultLine? = history.lastOrNull()

    fun clearHistory() {
        history.clear()
    }

    @Volatile
    private var snapshot = Snapshot()
        private set

    /**
     * 当前（或最近一次）会话的方向。
     *
     * SDK 的 onSegment 回调不带方向，而副屏需要它来决定显示原文还是译文，
     * 因此在 onStateChanged 里记一份，供 onSegment 使用。
     */
    @Volatile
    private var currentDirection: BilingualTranslateSdk.Direction =
        BilingualTranslateSdk.Direction.ZH_TO_ID

    @Volatile
    private var initStarted = false

    @Volatile
    private var initStartedAt = 0L

    private var appContext: Context? = null

    // ---------------- 对外 API ----------------

    /**
     * 应用启动时调用一次（Application.onCreate）。
     *
     * 是否真正加载由用户开关决定：
     * - 开关关闭 → 什么都不做（状态 DISABLED），不占内存；
     * - 开关打开 → 立即开始加载两个方向的模型。
     *
     * 重复调用是安全的（幂等）。
     */
    @Synchronized
    fun bootstrap(context: Context) {
        appContext = context.applicationContext
        val ctx = appContext ?: return
        if (!OfflinePrefs.isEnabled(ctx)) {
            update(Snapshot(state = State.DISABLED, message = "离线翻译未启用"))
            return
        }
        startLoading(ctx)
    }

    /**
     * 用户打开/关闭开关时调用。
     *
     * 打开 → 开始加载（若尚未加载）；关闭 → 释放引擎，回收内存。
     */
    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        val ctx = appContext ?: return
        OfflinePrefs.setEnabled(ctx, enabled)
        if (enabled) {
            startLoading(ctx)
        } else {
            releaseInternal("用户关闭离线翻译")
            update(Snapshot(state = State.DISABLED, message = "离线翻译未启用"))
        }
    }

    /**
     * 切换模型来源（下载 / 内置）。会释放当前引擎并按新来源重新加载。
     *
     * 注意：两套目录结构一致，若新来源下本地已有同一 `content_version` 的模型，
     * `initModel` 直接命中缓存，不会重复下载或拷贝。
     */
    @Synchronized
    fun setSource(context: Context, source: Int) {
        appContext = context.applicationContext
        val ctx = appContext ?: return
        OfflinePrefs.setSource(ctx, source)
        if (!OfflinePrefs.isEnabled(ctx)) return
        // 换来源等价于换一套模型来源，必须重建
        releaseInternal("切换模型来源")
        initStarted = false
        startLoading(ctx)
    }

    /** 重试（FAILED 状态下用）。 */
    @Synchronized
    fun retry(context: Context) {
        appContext = context.applicationContext
        val ctx = appContext ?: return
        if (!OfflinePrefs.isEnabled(ctx)) return
        releaseInternal("用户重试")
        initStarted = false
        startLoading(ctx)
    }

    fun current(): Snapshot = snapshot

    fun isReady(): Boolean = snapshot.state == State.READY

    /** 注册观察者；注册时立即回调一次当前状态。 */
    fun observe(observer: Observer) {
        observers.addIfAbsent(observer)
        val s = snapshot
        mainHandler.post { observer.onChanged(s) }
    }

    fun removeObserver(observer: Observer) {
        observers.remove(observer)
    }

    /** 注册会话事件监听（离线界面用）。 */
    fun addSessionListener(listener: SessionListener) {
        sessionListeners.addIfAbsent(listener)
    }

    fun removeSessionListener(listener: SessionListener) {
        sessionListeners.remove(listener)
    }

    /**
     * 开始一次识别。
     *
     * @param zhToId true = 中文说给印尼人听（中文 ASR + zh→id）；false = 反过来
     * @return 是否被接受
     */
    fun startRecording(zhToId: Boolean): Boolean {
        if (!isReady()) {
            Log.w(TAG, "start rejected: not ready (${snapshot.state})")
            return false
        }
        val dir = if (zhToId) {
            BilingualTranslateSdk.Direction.ZH_TO_ID
        } else {
            BilingualTranslateSdk.Direction.ID_TO_ZH
        }
        return BilingualTranslateSdk.start(dir)
    }

    fun stopRecording(): Boolean = BilingualTranslateSdk.stop()

    /**
     * 用 WAV 文件跑一次完整识别+翻译（自动化回归用，不碰麦克风）。
     *
     * @param zhToId true = 中文音频走 zh→id；false = 印尼语音频走 id→zh
     * @return 是否被接受
     */
    fun startRecordingFromFile(wavFile: java.io.File, zhToId: Boolean): Boolean {
        if (!isReady()) {
            Log.w(TAG, "startFromFile rejected: not ready (${snapshot.state})")
            return false
        }
        val dir = if (zhToId) {
            BilingualTranslateSdk.Direction.ZH_TO_ID
        } else {
            BilingualTranslateSdk.Direction.ID_TO_ZH
        }
        return BilingualTranslateSdk.start(dir, wavFile)
    }

    fun cancelRecording() = BilingualTranslateSdk.cancel()

    fun isRecording(): Boolean = BilingualTranslateSdk.isRunning()

    /** 释放全部引擎（进程退出时可选调用；Application 结束会自然回收）。 */
    @Synchronized
    fun release() {
        releaseInternal("release")
    }

    // ---------------- 内部 ----------------

    private fun startLoading(ctx: Context) {
        if (initStarted) {
            Log.i(TAG, "bootstrap skipped: init already started (${snapshot.state})")
            return
        }
        initStarted = true
        initStartedAt = System.currentTimeMillis()

        val source = OfflinePrefs.source(ctx)
        val server = OfflinePrefs.server(ctx)
        Log.i(
            TAG,
            "==== 开始初始化离线翻译 source=${OfflinePrefs.sourceLabel(ctx)} " +
                "server=$server modelRoot=$MODEL_ROOT ====",
        )
        update(Snapshot(state = State.LOADING, progress = 0, message = "准备加载离线模型…"))

        val options = BilingualOptions(
            serverBaseUrl = server,
            modelRootDir = java.io.File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, MODEL_ROOT),
            installMode = OfflinePrefs.installMode(ctx),
            enableMt = true,
            autoInstall = true,
        )

        BilingualTranslateSdk.init(ctx, options, object : BilingualCallbackAdapter() {
            override fun onProgress(percent: Int, message: String) {
                update(snapshot.copy(state = State.LOADING, progress = percent, message = message))
            }

            override fun onReady(info: String) {
                val cost = System.currentTimeMillis() - initStartedAt
                Log.i(TAG, "==== 离线翻译就绪 cost=${cost}ms mt=${BilingualTranslateSdk.isMtReady()} ====")
                update(
                    Snapshot(
                        state = State.READY,
                        progress = 100,
                        message = info,
                        costMs = cost,
                        mtReady = BilingualTranslateSdk.isMtReady(),
                        readyDirections = BilingualTranslateSdk.readyDirections().size,
                    )
                )
            }

            override fun onError(message: String) {
                Log.e(TAG, "离线翻译初始化失败: $message")
                update(Snapshot(state = State.FAILED, message = message))
            }

            // ---- 会话事件：统一代理后广播给当前注册的页面 ----

            override fun onPartial(text: String) {
                sessionListeners.forEach { runCatching { it.onPartial(text) } }
            }

            override fun onSegment(asrText: String, mtText: String?) {
                // 先落到历史，再广播：页面即使此刻不在，事后 onResume 也能恢复；
                // 副屏也靠这里补上方向信息（onSegment 本身不带方向）
                history.add(ResultLine(asrText, mtText, currentDirection))
                sessionListeners.forEach {
                    runCatching { it.onSegment(asrText, mtText, currentDirection) }
                }
            }

            override fun onFinished(fullText: String, elapsedMs: Long) {
                sessionListeners.forEach { runCatching { it.onFinished(fullText, elapsedMs) } }
            }

            override fun onStateChanged(
                recording: Boolean,
                direction: BilingualTranslateSdk.Direction,
            ) {
                // 记住本次方向：后续 onSegment 拿不到方向参数，靠这个补
                currentDirection = direction
                val zhToId = direction == BilingualTranslateSdk.Direction.ZH_TO_ID
                sessionListeners.forEach { runCatching { it.onRecordingChanged(recording, zhToId) } }
            }
        })
    }

    @Synchronized
    private fun releaseInternal(reason: String) {
        Log.i(TAG, "释放离线引擎：$reason")
        runCatching { BilingualTranslateSdk.release() }
        initStarted = false
    }

    private fun update(next: Snapshot) {
        snapshot = next
        mainHandler.post {
            observers.forEach {
                runCatching { it.onChanged(next) }
            }
        }
    }
}