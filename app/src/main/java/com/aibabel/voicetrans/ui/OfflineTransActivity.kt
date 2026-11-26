package com.aibabel.voicetrans.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aibabel.asr.onnx.translate.BilingualCallbackAdapter
import com.aibabel.asr.onnx.translate.BilingualTranslateSdk
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.databinding.ActivityOfflineTransBinding
import com.aibabel.voicetrans.offline.OfflineTranslationManager

/**
 * 独立的离线翻译界面。
 *
 * ## 交互
 * - **两个圆形大按钮**分别代表两种语言：左=中文（青绿），右=印尼语（珊瑚红）；
 * - **长按**按钮开始说该语言，**松开**结束并出译文（push-to-talk）；
 * - **物理按键** 134（左）/ 135（右）以同样的 push-to-talk 方式并行驱动——
 *   与屏幕按钮共用同一条路径，硬按键按下时对应按钮也会同步高亮缩小。
 *
 * ## 为什么切语言不需要等
 * 两个方向的 ASR 引擎在应用启动时就已常驻（见 [OfflineTranslationManager]），
 * 这里按按钮只是「选哪个引擎说话」，不涉及任何模型加载或重建，
 * 因此左说一句、右说一句可以连续交替，没有等待。
 *
 * ## 副屏（HDMI 外接）
 * 与在线链路复用同一套 [SecondaryDisplayManager] / [SecondaryPresentation]：
 * - 副屏显示哪种语言由设置页「副屏显示设置」决定，主屏显示与之相反的内容；
 * - 每次识别/翻译结果都同步推到副屏，让**对面的人**能直接看副屏读译文；
 * - 副屏的亮灭、皮套（HALL）、息屏恢复、每 1.5s 保活全部复用在线那套逻辑。
 *
 * 之所以能零改动复用，是因为 [SecondaryDisplayManager.updateContent] 的判定是
 * 「副屏语言 == 源语言 → 显示原文；== 目标语言 → 显示译文」，而离线双向对话
 * 每句话天然带 sourceLang/targetLang：
 * - 按中文按钮：source=zh / target=id → 副屏配印尼语则显示印尼译文
 * - 按印尼按钮：source=id / target=zh → 副屏配中文则显示中文译文
 * 正好都是「给对面那个人的语言」。
 *
 * ## 与主界面的关系
 * 视觉沿用主界面同一套配色与圆形按钮（`bg_circle_button_*`），但入口独立：
 * 主界面仍然是在线 WebSocket 链路，互不干扰。
 */
class OfflineTransActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineTransBinding

    /** 副屏管理器：与在线链路同一个类，负责检测/电源/皮套/Presentation 生命周期。 */
    private lateinit var displayManager: SecondaryDisplayManager

    private lateinit var prefs: android.content.SharedPreferences

    /** 已确认的结果行（按「识别 → 译文」成对追加）。 */
    private val resultLines = ArrayList<String>()

    /** 当前按下的物理按键对应哪个方向，防止重复触发。 */
    private var physicalKeyDirection: BilingualTranslateSdk.Direction? = null

    /** 录音冷却：松手后短时间内禁止再次开启，避免误触（与主界面一致）。 */
    private var coolDown = false

    /** 正在录音的方向；null 表示空闲。 */
    private var recordingDirection: BilingualTranslateSdk.Direction? = null

    /** 页面是否处于前台（onResume 之后为 true）。测试通道要等它变 true 再跑。 */
    private var isResumed = false

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ---------------- 生命周期 ----------------

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityOfflineTransBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkAudioPermission()
        bindViews()
        initSecondaryDisplay()
        renderState(OfflineTranslationManager.current())
        handleTestIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // 打开副屏 + 显示 Presentation（内部有 500ms 延时与失败重试）
        displayManager.onStart()
    }

    override fun onStop() {
        super.onStop()
        // 关闭副屏电源，避免离开页面后仍占着 HDMI 显示
        displayManager.onStop()
    }

    /**
     * 自动化测试通道（仅调试用，正式交付可移除）。
     *
     * `am start -n .../.ui.OfflineTransActivity -e test_wav /sdcard/Download/zh.wav -e test_dir zh`
     *
     * 用文件注入代替麦克风，跑完整的识别 + 翻译链路，便于无人值守回归：
     * 真机麦克风需要人对着说话，脚本无法稳定复现。
     */
    private fun handleTestIntent(intent: android.content.Intent?) {
        val path = intent?.getStringExtra("test_wav") ?: return
        val zhToId = (intent.getStringExtra("test_dir") ?: "zh").equals("zh", ignoreCase = true)
        val file = java.io.File(path)
        Log.i(TAG, "test_wav=$path dir=${if (zhToId) "zh->id" else "id->zh"} exists=${file.isFile}")

        // 等 manager 就绪再跑（页面可能比模型先起来）
        val runner = object : Runnable {
            private var tries = 0
            override fun run() {
                when {
                    !isResumed -> {
                        // 页面还没到 onResume，监听器尚未注册，等下一拍
                        mainHandler.postDelayed(this, 500L)
                    }

                    OfflineTranslationManager.isReady() -> {
                        // 每次测试前清掉旧结果，保证看到的是这一次的输出
                        OfflineTranslationManager.clearHistory()
                        resultLines.clear()
                        renderResults()
                        val ok = OfflineTranslationManager.startRecordingFromFile(file, zhToId)
                        Log.i(TAG, "test_wav start accepted=$ok")
                    }

                    tries++ < 240 -> mainHandler.postDelayed(this, 1000L)
                    else -> Log.e(TAG, "test_wav 放弃：等待模型就绪超时")
                }
            }
        }
        mainHandler.post(runner)
    }

    override fun onResume() {
        super.onResume()
        OfflineTranslationManager.observe(stateObserver)
        OfflineTranslationManager.addSessionListener(sessionListener)
        registerReceiver(
            modeChangeReceiver,
            android.content.IntentFilter(SettingsActivity.ACTION_SECONDARY_MODE_CHANGED),
        )
        BilingualTranslateSdk.setLogcatEnabled(true)
        isResumed = true
        // 页面重建时从 manager 的历史恢复已出结果，避免显示空白
        resultLines.clear()
        resultLines.addAll(OfflineTranslationManager.resultHistory())
        renderResults()
        Log.i(TAG, "onResume listener registered, restored ${resultLines.size} lines")
    }

    override fun onPause() {
        super.onPause()
        OfflineTranslationManager.removeObserver(stateObserver)
        OfflineTranslationManager.removeSessionListener(sessionListener)
        unregisterReceiver(modeChangeReceiver)
        isResumed = false
        Log.i(TAG, "onPause listener removed")
        // 离开页面时若还在录音，主动收尾，避免占着麦克风
        if (OfflineTranslationManager.isRecording()) {
            OfflineTranslationManager.stopRecording()
        }
        recordingDirection = null
        physicalKeyDirection = null
    }

    override fun onDestroy() {
        OfflineTranslationManager.removeObserver(stateObserver)
        OfflineTranslationManager.removeSessionListener(sessionListener)
        mainHandler.removeCallbacksAndMessages(null)
        // 释放副屏：关闭副屏电源、注销显示器/HALL/广播监听
        runCatching { displayManager.release() }
        super.onDestroy()
    }

    /**
     * 副屏语言切换（设置页改「副屏显示设置」时发出）。
     *
     * 与在线链路一致：换模式后要重建 Presentation 内容并刷新当前这句，
     * 否则副屏会一直停在旧语言。
     */
    private val modeChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action != SettingsActivity.ACTION_SECONDARY_MODE_CHANGED) return
            secondaryMode = intent.getIntExtra(
                SettingsActivity.EXTRA_MODE,
                SettingsActivity.MODE_INDONESIAN,
            )
            Log.i(TAG, "secondary mode changed -> $secondaryMode")
            displayManager.setMode(secondaryMode)
            displayManager.showPresentation(secondaryMode)
            refreshSecondaryDisplay()
        }
    }

    // ---------------- 视图绑定 ----------------

    /**
     * 初始化副屏，逻辑与 [MainActivity] 完全一致：
     * 注册显示器监听（拔插 HDMI）、HALL 传感器（皮套开合）、USER_PRESENT 广播（解锁恢复）。
     *
     * 副屏语言取自设置页的 `KEY_SECONDARY_SCREEN_MODE`，与在线链路共用同一份配置——
     * 用户在设置里选了「副屏显示印尼语」，在线和离线看到的副屏行为一致。
     */
    private fun initSecondaryDisplay() {
        displayManager = SecondaryDisplayManager(this, mainHandler)
        displayManager.register()
        secondaryMode = prefs.getInt(
            SettingsActivity.KEY_SECONDARY_SCREEN_MODE,
            SettingsActivity.MODE_INDONESIAN,
        )
        displayManager.setMode(secondaryMode)
        // 副屏从息屏/拔插恢复后，把当前已有结果重新刷上去（否则副屏会停在"等待翻译"）
        displayManager.setOnDisplayReadyCallback {
            refreshSecondaryDisplay()
        }
        Log.i(TAG, "secondary display init mode=$secondaryMode")
    }

    private fun bindViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener {
            OfflineTranslationManager.clearHistory()
            resultLines.clear()
            binding.tvPartial.text = ""
            binding.tvPartial.visibility = View.GONE
            renderResults()
            // 副屏同步清空，否则会残留上一句
            displayManager.clearContent(secondaryMode)
        }

        // 屏幕按钮：长按开始、松开结束（与主界面同一套手势）
        setupPushToTalk(binding.btnChinese, BilingualTranslateSdk.Direction.ZH_TO_ID)
        setupPushToTalk(binding.btnIndonesian, BilingualTranslateSdk.Direction.ID_TO_ZH)
    }

    private fun setupPushToTalk(button: Button, direction: BilingualTranslateSdk.Direction) {
        button.setOnLongClickListener {
            // 长按才触发录音；返回 true 表示事件已消费，避免再走 onClick
            startRecording(direction, button)
            true
        }
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下就给一点视觉反馈（缩小），即使还没到长按阈值
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(120).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (recordingDirection == direction) {
                        stopRecording()
                    }
                }
            }
            false
        }
    }

    // ---------------- 物理按键（134/135） ----------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KEY_LEFT -> {
                // 只在空闲且冷却结束时启动，避免长按连发
                if (recordingDirection == null && physicalKeyDirection == null && !coolDown) {
                    startRecording(BilingualTranslateSdk.Direction.ZH_TO_ID, binding.btnChinese)
                    physicalKeyDirection = BilingualTranslateSdk.Direction.ZH_TO_ID
                }
                true
            }

            KEY_RIGHT -> {
                if (recordingDirection == null && physicalKeyDirection == null && !coolDown) {
                    startRecording(BilingualTranslateSdk.Direction.ID_TO_ZH, binding.btnIndonesian)
                    physicalKeyDirection = BilingualTranslateSdk.Direction.ID_TO_ZH
                }
                true
            }

            KEY_BACK_EXIT -> {
                finish()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KEY_LEFT, KEY_RIGHT -> {
                if (physicalKeyDirection != null) {
                    stopRecording()
                    physicalKeyDirection = null
                }
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    // ---------------- 录音控制 ----------------

    private fun startRecording(direction: BilingualTranslateSdk.Direction, button: Button) {
        if (recordingDirection != null) {
            Log.w(TAG, "start ignored: already recording ($recordingDirection)")
            return
        }
        if (!OfflineTranslationManager.isReady()) {
            showToast("模型准备中，请稍候")
            return
        }
        val zhToId = direction == BilingualTranslateSdk.Direction.ZH_TO_ID
        if (!OfflineTranslationManager.startRecording(zhToId)) {
            showToast("启动失败，请重试")
            return
        }
        recordingDirection = direction
        button.animate().scaleX(0.85f).scaleY(0.85f).setDuration(150).start()
        button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        binding.tvRecordHint.text = getString(R.string.offline_listening)
        binding.tvRecordHint.setTextColor(ContextCompat.getColor(this, R.color.accent_teal))
        Log.i(TAG, "recording start direction=$direction")
    }

    private fun stopRecording() {
        val dir = recordingDirection ?: return
        recordingDirection = null
        // 恢复两个按钮的视觉状态（物理按键与屏幕按钮都会走到这里）
        binding.btnChinese.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        binding.btnIndonesian.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        binding.tvRecordHint.text = getString(R.string.offline_hold_to_talk)
        binding.tvRecordHint.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
        OfflineTranslationManager.stopRecording()
        Log.i(TAG, "recording stop direction=$dir")

        coolDown = true
        mainHandler.postDelayed({ coolDown = false }, 800L)
    }

    // ---------------- 副屏内容同步 ----------------

    /**
     * 副屏当前语言模式（0=印尼 1=中文 2=英文）。
     *
     * 不用每次从 prefs 读：广播里带来的 mode 才是"用户刚选的"，
     * 而 prefs 可能还没落盘（设置页是 apply() 异步写）。
     * 启动时从 prefs 初始化，之后以广播为准。
     */
    private var secondaryMode: Int = SettingsActivity.MODE_INDONESIAN

    /**
     * 把「当前这句话」推到副屏，让对面的人能直接看。
     *
     * 判定交给 [SecondaryDisplayManager.updateContent]：它按
     * 「副屏语言 == sourceLang → 原文；== targetLang → 译文」自己选。
     * 双向对话天然满足这个模型，因此不需要在离线侧写任何语言判断。
     *
     * @param asrText 识别原文
     * @param mtText 译文；null 时副屏若配的是目标语言会显示"等待翻译"
     * @param direction 这句话的方向，决定 sourceLang/targetLang
     */
    private fun pushToSecondary(asrText: String, mtText: String?, direction: BilingualTranslateSdk.Direction) {
        displayManager.updateContent(
            asrText,
            mtText,
            direction.sourceLanguage,
            direction.targetLanguage,
            secondaryMode,
        )
        Log.i(
            TAG,
            "secondary push mode=$secondaryMode ${direction.sourceLanguage}->${direction.targetLanguage} " +
                "asr=\"${asrText.take(20)}\" mt=\"${mtText?.take(20) ?: "-"}\"",
        )
    }

    /**
     * 用最新一条结果刷新副屏（副屏恢复/重建后调用）。
     *
     * 只推最后一条：副屏是 240x320 的小屏，一次只放得下一句话，
     * 把整段历史铺上去反而看不清。
     */
    private fun refreshSecondaryDisplay() {
        val last = OfflineTranslationManager.lastResult()
        if (last == null) {
            displayManager.clearContent(secondaryMode)
            return
        }
        pushToSecondary(last.asrText, last.mtText, last.direction)
    }

    // ---------------- 状态与结果渲染 ----------------

    private val stateObserver = OfflineTranslationManager.Observer { snapshot ->
        renderState(snapshot)
    }

    private fun renderState(snapshot: OfflineTranslationManager.Snapshot) {
        when (snapshot.state) {
            OfflineTranslationManager.State.READY -> {
                binding.tvState.visibility = View.GONE
                setButtonsEnabled(true)
            }

            OfflineTranslationManager.State.LOADING -> {
                binding.tvState.visibility = View.VISIBLE
                binding.tvState.text = snapshot.message.ifBlank {
                    getString(R.string.offline_waiting_model)
                }
                setButtonsEnabled(false)
            }

            OfflineTranslationManager.State.FAILED -> {
                binding.tvState.visibility = View.VISIBLE
                binding.tvState.text = snapshot.message.ifBlank {
                    getString(R.string.offline_status_failed)
                }
                binding.tvState.setTextColor(ContextCompat.getColor(this, R.color.text_status))
                setButtonsEnabled(false)
            }

            else -> {
                binding.tvState.visibility = View.VISIBLE
                binding.tvState.text = getString(R.string.offline_status_idle)
                setButtonsEnabled(false)
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnChinese.isEnabled = enabled
        binding.btnIndonesian.isEnabled = enabled
    }

    /**
     * 会话事件监听：把 SDK 输出的识别/翻译结果显示到界面。
     *
     * 由 [OfflineTranslationManager] 统一代理 SDK 回调后广播过来，
     * 因此本页面进出不会影响 SDK 的注册状态。
     */
    private val sessionListener = object : OfflineTranslationManager.SessionListener {
        override fun onPartial(text: String) {
            if (text.isBlank()) {
                binding.tvPartial.visibility = View.GONE
                return
            }
            binding.tvPartial.visibility = View.VISIBLE
            binding.tvPartial.text = text
            binding.svResult.post { binding.svResult.fullScroll(View.FOCUS_DOWN) }
        }

        override fun onSegment(
            asrText: String,
            mtText: String?,
            direction: BilingualTranslateSdk.Direction,
        ) {
            Log.i(TAG, "onSegment asr=\"$asrText\" mt=${mtText?.let { "\"$it\"" } ?: "null"}")
            // 一段结束后清掉过程文本，避免与已确认结果重复显示
            binding.tvPartial.text = ""
            binding.tvPartial.visibility = View.GONE
            syncResultsFromHistory()
            // 同步到副屏：让对面的人直接看译文（副屏语言由设置决定，内部自动选原文/译文）
            pushToSecondary(asrText, mtText, direction)
        }

        override fun onFinished(fullText: String, elapsedMs: Long) {
            binding.tvPartial.visibility = View.GONE
            binding.tvRecordHint.text = getString(R.string.offline_hold_to_talk)
            binding.tvRecordHint.setTextColor(ContextCompat.getColor(this@OfflineTransActivity, R.color.text_hint))
            Log.i(TAG, "session finished cost=${elapsedMs}ms len=${fullText.length}")
        }

        override fun onRecordingChanged(recording: Boolean, zhToId: Boolean) {
            if (recording) {
                binding.tvRecordHint.text = getString(R.string.offline_listening)
                binding.tvRecordHint.setTextColor(
                    ContextCompat.getColor(this@OfflineTransActivity, R.color.accent_teal)
                )
            }
        }
    }

    private fun renderResults() {
        if (resultLines.isEmpty()) {
            binding.tvResult.visibility = View.GONE
            binding.tvEmptyHint.visibility = View.VISIBLE
            return
        }
        binding.tvEmptyHint.visibility = View.GONE
        binding.tvResult.visibility = View.VISIBLE
        binding.tvResult.text = resultLines.joinToString("\n\n")
        binding.svResult.post { binding.svResult.fullScroll(View.FOCUS_DOWN) }
    }

    /** 把 manager 的结果历史同步到本地并刷新列表。 */
    private fun syncResultsFromHistory() {
        resultLines.clear()
        resultLines.addAll(OfflineTranslationManager.resultHistory())
        renderResults()
    }

    // ---------------- 其它 ----------------

    private fun checkAudioPermission() {
        val perm = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_AUDIO)
        }
    }

    private fun showToast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "OfflineTransActivity"
        private const val REQ_AUDIO = 1001

        /** 左键：说中文。 */
        private const val KEY_LEFT = 134

        /** 右键：说印尼语。 */
        private const val KEY_RIGHT = 135

        /** 返回/退出。 */
        private const val KEY_BACK_EXIT = 136
    }
}