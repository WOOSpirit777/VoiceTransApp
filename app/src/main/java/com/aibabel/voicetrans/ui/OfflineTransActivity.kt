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
 * ## 与主界面的关系
 * 视觉沿用主界面同一套配色与圆形按钮（`bg_circle_button_*`），但入口独立：
 * 主界面仍然是在线 WebSocket 链路，互不干扰。
 */
class OfflineTransActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOfflineTransBinding

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

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        checkAudioPermission()
        bindViews()
        renderState(OfflineTranslationManager.current())
        handleTestIntent(intent)
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
        super.onDestroy()
    }

    // ---------------- 视图绑定 ----------------

    private fun bindViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener {
            OfflineTranslationManager.clearHistory()
            resultLines.clear()
            binding.tvPartial.text = ""
            binding.tvPartial.visibility = View.GONE
            renderResults()
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

        override fun onSegment(asrText: String, mtText: String?) {
            Log.i(TAG, "onSegment asr=\"$asrText\" mt=${mtText?.let { "\"$it\"" } ?: "null"}")
            // 一段结束后清掉过程文本，避免与已确认结果重复显示
            binding.tvPartial.text = ""
            binding.tvPartial.visibility = View.GONE
            appendResult(asrText, mtText)
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

    /** 把 manager 的历史同步到本地并刷新。译文为空时只显示原文，避免出现空的箭头行。 */
    private fun appendResult(asrText: String, mtText: String?) {
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