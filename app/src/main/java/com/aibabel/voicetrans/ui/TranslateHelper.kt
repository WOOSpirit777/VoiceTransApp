package com.aibabel.voicetrans.ui

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.audio.AudioPlayerManager
import com.aibabel.voicetrans.audio.AudioRecorderManager
import com.aibabel.voicetrans.data.AudioSendManager
import com.aibabel.voicetrans.data.AudioSegmentRepository
import com.aibabel.voicetrans.data.TranslationSessionRepository
import com.aibabel.voicetrans.model.ChatMessage
import com.aibabel.voicetrans.model.TranslationResult
import com.aibabel.voicetrans.util.Constants
import com.aibabel.voicetrans.utils.LanguageUtils
import com.aibabel.voicetrans.websocket.WebSocketManager
import com.apkfuns.logutils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 翻译流程管理器
 * 负责WebSocket回调、翻译结果处理、Loading弹窗、TTS音频保存与播放
 * 录音数据先入库，再由AudioSendManager发送，支持断线重连后续传
 */
class TranslateHelper(
    private val activity: android.app.Activity,
    private val prefs: SharedPreferences,
    private val handler: Handler,
    private val chatAdapter: ChatAdapter,
    private val secondaryDisplayManager: SecondaryDisplayManager,
    private val svMainDisplay: ScrollView,
    private val tvMainDisplayText: TextView,
    private val tvMainDisplayLang: TextView,
    private val tvRecordHint: TextView,
    private val audioButtonView: AudioButtonView,
    private val onStatusUpdate: (String, Int, Int) -> Unit,
    private val onRecordingStateChange: (Boolean) -> Unit
) {

    private val webSocketManager = WebSocketManager.getInstance()
    private val audioRecorderManager = AudioRecorderManager.getInstance()
    private val audioPlayerManager = AudioPlayerManager.getInstance()

    private val ttsAudioBuffer = ByteArrayOutputStream()
    private val isProcessing = AtomicBoolean(false)
    private var currentMessage: ChatMessage? = null
    private var currentLang = Constants.LANG_ZH
    private var isRecording = false
    private var loadingDialog: AlertDialog? = null
    private var translateFinishTimeoutRunnable: Runnable? = null
    private var recordTimeoutRunnable: Runnable? = null

    // 录音分段发送相关
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sendManager: AudioSendManager
    private lateinit var sessionRepository: TranslationSessionRepository
    private var currentSessionId: String = ""
    private val segmentIndex = AtomicInteger(0)

    // 直接发送模式开关：true=边录边发（测试用），false=先入库再发送（原逻辑）
    private val directSendMode = true

    fun init() {
        val repository = AudioSegmentRepository(activity)
        sendManager = AudioSendManager(repository, webSocketManager)
        sessionRepository = TranslationSessionRepository(activity)

        webSocketManager.setCallback(webSocketCallback)
        audioRecorderManager.setCallback(audioRecordCallback)
        audioPlayerManager.setCallback(audioPlayerCallback)
        updateLanguageInManager()

        // 从设置读取会话模式
        val sessionMode = prefs.getString(SettingsActivity.KEY_SESSION_MODE, Constants.MODE_S2S)
        webSocketManager.setMode(sessionMode ?: Constants.MODE_S2S)
        Log.i(TAG, "Session mode: $sessionMode")

        // 清理旧的已发送数据
        scope.launch {
            sendManager.cleanupOldSent()
            sessionRepository.cleanupOldSessions()
        }
    }

    fun setCurrentLang(lang: String) {
        if (currentLang != lang) {
            currentLang = lang
            updateLanguageInManager()
            if (webSocketManager.isConnected()) {
                webSocketManager.disconnect()
                handler.postDelayed({
                    webSocketManager.connect()
                }, 500)
            }
        }
    }

    fun getCurrentLang() = currentLang

    fun isProcessing() = isProcessing.get()

    fun isRecording() = isRecording

    fun startRecording() {
        if (!webSocketManager.isConnected()) {
            // 连接前读取最新的会话模式
            val sessionMode = prefs.getString(SettingsActivity.KEY_SESSION_MODE, Constants.MODE_S2S)
            webSocketManager.setMode(sessionMode ?: Constants.MODE_S2S)
            onStatusUpdate("连接中...", 0xFFFFF3E0.toInt(), 0xFFE65100.toInt())
            webSocketManager.connect()
        }

        if (audioPlayerManager.isPlaying()) {
            audioPlayerManager.stop()
            Log.i(TAG, "Stopped TTS playback to start recording")
        }

        isRecording = true
        isProcessing.set(true)

        tvRecordHint.text = getMainString(R.string.recording)
        onRecordingStateChange(false)

        // 使用WebSocket连接时创建的sessionId
        currentSessionId = webSocketManager.getSessionId()
        Log.i(TAG,"currentSessionId =$currentSessionId")
        segmentIndex.set(0)
        sendManager.setSessionId(currentSessionId)

        // 创建数据库会话记录
        val targetLang = if (Constants.LANG_ZH == currentLang) Constants.LANG_ID else Constants.LANG_ZH
        scope.launch {
            sessionRepository.createSession(currentSessionId, currentLang, targetLang)
        }



        synchronized(ttsAudioBuffer) {
            ttsAudioBuffer.reset()
        }

        currentMessage = ChatMessage()
        currentMessage!!.sourceLang = currentLang
        currentMessage!!.targetLang = if (Constants.LANG_ZH == currentLang) Constants.LANG_ID else Constants.LANG_ZH
        chatAdapter.addMessage(currentMessage!!)
        scrollToBottom()

        audioRecorderManager.setLanguage(currentLang)
        audioRecorderManager.startRecording()

        // 录音一分钟限制，到时间自动停止
        recordTimeoutRunnable = Runnable {
            Log.e(TAG, "Recording timeout after 60s, auto stop")
            stopRecording()
        }
        handler.postDelayed(recordTimeoutRunnable!!, 60000)
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false

        // 取消录音超时计时
        recordTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            recordTimeoutRunnable = null
        }

        handler.post {
            tvRecordHint.text = getMainString(R.string.recognizing)
            onRecordingStateChange(true)
        }

        audioRecorderManager.stopRecording()

        showLoadingDialog()

        // 先初始化超时 Runnable，避免协程内使用时还未赋值导致 NPE
        translateFinishTimeoutRunnable = Runnable {
            Log.e(TAG, "Translate finish timeout after ${Constants.TRANSLATE_FINISH_TIMEOUT_MS}ms")
//            dismissLoadingDialog()
//            audioPlayerManager.stopStreamingPlayback()
//            isProcessing.set(false)
//            tvRecordHint.text = getMainString(R.string.translate_timeout)
//            webSocketManager.sendClose()
        }

        // 发送finish并启动播放
        scope.launch {
            val sessionId = currentSessionId

            if (directSendMode) {
                // 直接发送模式：边录边发，录音停止后直接发送finish
                Log.i(TAG, "[DirectSend] Recording stopped, sending finish command")
                webSocketManager.sendFinish()
            } else {
                // 原逻辑：轮询等待所有分段从数据库发送完成
                var waitCount = 0
                while (!sendManager.isAllSent(sessionId) && waitCount < 50) {
                    Thread.sleep(100)
                    waitCount++
                    Log.i(TAG, "Waiting for segments to be sent... ($waitCount)")
                }

                if (sendManager.isAllSent(sessionId)) {
                    Log.i(TAG, "All segments sent, sending finish command")
                    sendManager.sendFinish()
                } else {
                    Log.e(TAG, "Timeout waiting for segments to send, sending finish anyway")
                    sendManager.sendFinish()
                }
            }

            // 发送finish后启动流式播放，把录音过程中缓存的音频先喂进去
            val autoPlay = prefs.getBoolean(SettingsActivity.KEY_AUTO_PLAY, true)
            if (autoPlay) {
                handler.post {
                    synchronized(ttsAudioBuffer) {
                        val cachedData = ttsAudioBuffer.toByteArray()
                        if (cachedData.isNotEmpty()) {
                            audioPlayerManager.startStreamingPlayback()
                            audioPlayerManager.feedAudioData(cachedData)
                            Log.e(TAG, "Start streaming playback with cached data: ${cachedData.size} bytes")
                        } else {
                            audioPlayerManager.startStreamingPlayback()
                            Log.e(TAG, "Start streaming playback (no cached data yet)")
                        }
                    }
                }
            }
            //结束录制设置翻译超时计时
            handler.postDelayed(translateFinishTimeoutRunnable!!, Constants.TRANSLATE_FINISH_TIMEOUT_MS)
        }

    }

    fun playSourceAudio(message: ChatMessage) {
        val audioData = message.sourceAudioData
        if (audioData != null && audioData.isNotEmpty()) {
            audioPlayerManager.playPcm(audioData)
            Log.i(TAG, "Playing source audio, size=${audioData.size}")
        } else {
            Log.w(TAG, "No source audio data")
        }
    }

    fun playTargetAudio(message: ChatMessage) {
        val audioData = message.ttsAudioData
        if (audioData != null && audioData.isNotEmpty()) {
            audioPlayerManager.playPcm(audioData)
            Log.i(TAG, "Playing target audio, size=${audioData.size}")
        } else {
            Log.w(TAG, "No target audio data")
        }
    }

    fun playMainDisplayAudio() {
        //点击主屏播放音频应该播放target 语种的音频
        val msg = currentMessage ?: return
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        if (secondaryMode == SettingsActivity.MODE_CHINESE) {
            playTargetAudio(msg)
        } else {
            playSourceAudio(msg)
        }
    }

    fun updateMainDisplayLanguage() {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        val mainShowChinese = secondaryMode == SettingsActivity.MODE_INDONESIAN
        val mainDisplayLang = if (mainShowChinese) "zh" else "id"
        val labelResId = if (mainShowChinese) R.string.chinese else R.string.indonesian
        tvMainDisplayLang.text = LanguageUtils.getStringByLang(activity, mainDisplayLang, labelResId)
    }

    fun release() {
        sendManager.release()
        webSocketManager.disconnect()
        audioRecorderManager.release()
        audioPlayerManager.release()
        dismissLoadingDialog()
    }

    // ==================== WebSocket 回调 ====================

    private val webSocketCallback = object : WebSocketManager.WebSocketCallback {
        override fun onConnected() {
            handler.post {
                onStatusUpdate("已连接", 0xFFE8F5E9.toInt(), 0xFF2E7D32.toInt())
                Log.i(TAG, "WebSocket connected")
            }
        }

        override fun onDisconnected() {
            handler.post { onStatusUpdate("连接断开", 0xFFFFEBEE.toInt(), 0xFFC62828.toInt()) }
        }

        override fun onMessage(message: String) {
            Log.e(TAG, "Text message: $message")
            when (message) {
                Constants.RESP_TRANSLATE_FINISH -> handleTranslateFinish()
                Constants.RESP_PROCESSING_COMPLETED -> Log.e(TAG, "Processing completed")
            }
        }

        override fun onResult(result: TranslationResult) {
            Log.e(TAG, "Result: $result")
            handler.post { handleResult(result) }
        }

        override fun onBinaryData(data: ByteArray) {
            synchronized(ttsAudioBuffer) {
                try {
                    ttsAudioBuffer.write(data)
                    Log.e(TAG, "Total buffered: ${ttsAudioBuffer.size()} bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to buffer audio: ${e.message}")
                }
            }
            // 只有流式播放已启动时才喂给播放器（录音过程中只缓存不播放）
            if (audioPlayerManager.isStreaming()) {
                audioPlayerManager.feedAudioData(data)
            }
            // 保存TTS音频到数据库
            scope.launch {
                sessionRepository.appendTtsAudio(currentSessionId, data)
            }
        }

        override fun onError(error: String) {
            handler.post {
                dismissLoadingDialog()
                onStatusUpdate("连接错误: $error", 0xFFFFEBEE.toInt(), 0xFFC62828.toInt())
                Log.e(TAG, "WebSocket error: $error")
                isRecording = false
                isProcessing.set(false)
                tvRecordHint.text = "连接失败，点击按钮重试"
                onRecordingStateChange(true)
                audioButtonView.setRecordingState(false)
            }
        }

        override fun onReconnected() {
            handler.post {
                onStatusUpdate("已重连", 0xFFE8F5E9.toInt(), 0xFF2E7D32.toInt())
                Log.i(TAG, "WebSocket reconnected, resuming data send")
            }
            // 重连后恢复发送未发送的分段数据
            sendManager.onResumeAfterReconnect()
        }
    }

    // ==================== 翻译结果处理 ====================

    private fun handleResult(result: TranslationResult) {
        if (result.isText) {
            if (currentMessage != null) {
                if (result.isSource) {
                    val existing = currentMessage!!.sourceText ?: ""
                    currentMessage!!.sourceText = existing + result.text
                    // 保存源文本到数据库
                    scope.launch {
                        sessionRepository.appendSourceText(currentSessionId, result.text.toString())
                    }
                    updateMainDisplay(currentMessage!!.sourceText, currentMessage!!.targetText,
                        currentMessage!!.sourceLang, currentMessage!!.targetLang)
                } else if (result.isTarget) {
                    val existing = currentMessage!!.targetText ?: ""
                    currentMessage!!.targetText = existing + result.text
                    // 保存目标文本到数据库
                    scope.launch {
                        sessionRepository.appendTargetText(currentSessionId, result.text.toString())
                    }
                    updateSecondScreen()
                    updateMainDisplay(currentMessage!!.sourceText, currentMessage!!.targetText,
                        currentMessage!!.sourceLang, currentMessage!!.targetLang)
                } else if (result.isTtsFinish) {
                    Log.e(TAG, "TTS finish received")
                }
                chatAdapter.updateLastMessage(currentMessage!!)
                scrollToBottom()
            }
        } else if (result.isError) {
            if (currentMessage != null) {
                currentMessage!!.isError = true
                chatAdapter.updateLastMessage(currentMessage!!)
            }
            val msg = if (result.code != null) "错误(${result.code}): ${result.message}"
                      else "错误: ${result.message ?: "未知错误"}"
            Log.e(TAG, "Translation error: $msg")
            isProcessing.set(false)
        }
    }

    private fun handleTranslateFinish() {
        Log.e(TAG, "Translate finish received")

        dismissLoadingDialog()
        translateFinishTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            translateFinishTimeoutRunnable = null
        }

        tvRecordHint.text = "翻译完成"
        Log.i(TAG, "Translation finished")

        updateSecondScreen()

        synchronized(ttsAudioBuffer) {
            val audioData = ttsAudioBuffer.toByteArray()
            if (audioData.isNotEmpty()) {
                currentMessage?.let {
                    it.ttsAudioData = audioData
                    chatAdapter.updateLastMessage(it)
                }
                saveTtsAudioToFile(audioData)

                // 停止流式播放（让缓冲区中剩余的数据播放完）
                audioPlayerManager.stopStreamingPlayback()
                Log.e(TAG, "Stop streaming playback, total audio: ${audioData.size} bytes")
            }
        }

        // 清理本次会话的数据库记录
        val sessionId = currentSessionId
        scope.launch {
            sendManager.cleanupSession(sessionId)
        }

        Handler(Looper.getMainLooper()).post {
            webSocketManager.sendClose()
            isProcessing.set(false)
            currentMessage = null
            audioButtonView.setRecordingState(false)
            tvRecordHint.text = "点击按钮开始说话"
        }
    }

    // ==================== Loading 弹窗 ====================

    private fun showLoadingDialog() {
        if (loadingDialog != null && loadingDialog!!.isShowing) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_loading, null)
        view.findViewById<android.widget.Button>(R.id.btnCancel).setOnClickListener {
            Log.e(TAG, "Loading dialog cancelled by user")
            dismissLoadingDialog()
            translateFinishTimeoutRunnable?.let {
                handler.removeCallbacks(it)
                translateFinishTimeoutRunnable = null
            }
            webSocketManager.sendClose()

            audioPlayerManager.stopStreamingPlayback()
            val autoPlay = prefs.getBoolean(SettingsActivity.KEY_AUTO_PLAY, true)
            if (autoPlay && ttsAudioBuffer.size() > 0) {
                synchronized(ttsAudioBuffer) {
                    val audioData = ttsAudioBuffer.toByteArray()
                    if (audioData.isNotEmpty()) {
                        currentMessage?.let {
                            it.ttsAudioData = audioData
                            chatAdapter.updateLastMessage(it)
                        }
                        saveTtsAudioToFile(audioData)
                    }
                }
            }

            isProcessing.set(false)
            audioButtonView.setRecordingState(false)
            tvRecordHint.text = "已取消"
        }

        loadingDialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.let { if (it.isShowing) it.dismiss() }
        loadingDialog = null
    }

    // ==================== 音频回调 ====================

    private val audioRecordCallback = object : AudioRecorderManager.AudioRecordCallback {
        override fun onRecordingStart() {
            Log.e(TAG, "Recording started, sessionId=$currentSessionId")
//            webSocketManager.sendConnect()
        }

        override fun onRecordingData(data: ByteArray, size: Int) {
            val index = segmentIndex.getAndIncrement()
            val actualData = if (size < data.size) data.copyOf(size) else data

            if (directSendMode) {
                // 直接发送模式：边录边发，收到数据立即通过WebSocket发送
                val sent = webSocketManager.sendAudioData(actualData)
            } else {
                // 原逻辑：录音数据先入库，再通过发送队列发送
                sendManager.saveAndSend(currentSessionId, index, actualData)
            }
        }

        override fun onRecordingStop(fullData: ByteArray?) {
            Log.e(TAG, "Recording stopped, total: ${fullData?.size ?: 0} bytes")
            if (currentMessage != null && fullData != null && fullData.isNotEmpty()) {
                currentMessage!!.sourceAudioData = fullData
                chatAdapter.updateLastMessage(currentMessage!!)
            }
        }

        override fun onRecordingError(error: String) {
            handler.post {
                Log.e(TAG, "Recording error: $error")
                isRecording = false
                isProcessing.set(false)
                tvRecordHint.text = getMainString(R.string.start_speaking)
                onRecordingStateChange(true)
            }
        }
    }

    private val audioPlayerCallback = object : AudioPlayerManager.AudioPlayerCallback {
        override fun onPlaybackStart() { Log.e(TAG, "Playback started") }
        override fun onPlaybackComplete() {
            Log.e(TAG, "Playback completed")
//            webSocketManager.sendClose()
        }
        override fun onPlaybackError(error: String) { Log.e(TAG, "Playback error: $error") }
    }

    // ==================== 工具方法 ====================

    private fun getMainDisplayLang(): String {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        val mainShowChinese = secondaryMode == SettingsActivity.MODE_INDONESIAN
        return if (mainShowChinese) "zh" else "id"
    }

    private fun getMainString(resId: Int): String {
        return LanguageUtils.getStringByLang(activity, getMainDisplayLang(), resId)
    }

    private fun updateLanguageInManager() {
        val targetLang = if (Constants.LANG_ZH == currentLang) Constants.LANG_ID else Constants.LANG_ZH
        webSocketManager.setLanguage(currentLang, targetLang)
    }

    private fun updateSecondScreen() {
        if (currentMessage == null) return
        val mode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        secondaryDisplayManager.updateContent(
            currentMessage!!.sourceText, currentMessage!!.targetText,
            currentMessage!!.sourceLang, currentMessage!!.targetLang, mode
        )
    }

    private fun updateMainDisplay(sourceText: String?, targetText: String?, sourceLang: String?, targetLang: String?) {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        val mainShowChinese = secondaryMode == SettingsActivity.MODE_INDONESIAN
        val mainDisplayLang = if (mainShowChinese) "zh" else "id"
        val waitingResId = if (mainShowChinese) R.string.waiting_for_chinese else R.string.waiting_for_indonesian

        val textToShow = if (mainShowChinese) {
            if (sourceLang == "zh") sourceText ?: LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
            else if (targetLang == "zh") targetText ?: LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
            else LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
        } else {
            if (sourceLang == "id") sourceText ?: LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
            else if (targetLang == "id") targetText ?: LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
            else LanguageUtils.getStringByLang(activity, mainDisplayLang, waitingResId)
        }

        tvMainDisplayText.text = textToShow
        svMainDisplay.post { svMainDisplay.fullScroll(View.FOCUS_DOWN) }
    }

    fun clearDisplay() {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        val mainShowChinese = secondaryMode == SettingsActivity.MODE_INDONESIAN
        val mainDisplayLang = if (mainShowChinese) "zh" else "id"
        tvMainDisplayText.text = LanguageUtils.getStringByLang(activity, mainDisplayLang, R.string.start_translation)
        secondaryDisplayManager.clearContent(secondaryMode)
        chatAdapter.clear()
    }

    private fun clearMainDisplay() {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        val mainShowChinese = secondaryMode == SettingsActivity.MODE_INDONESIAN
        val mainDisplayLang = if (mainShowChinese) "zh" else "id"
        tvMainDisplayText.text = LanguageUtils.getStringByLang(activity, mainDisplayLang, R.string.waiting_for_translation)
    }

    private fun saveTtsAudioToFile(audioData: ByteArray) {
        try {
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            ).mkdirs()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val targetLang = if (Constants.LANG_ZH == currentLang) Constants.LANG_ID else Constants.LANG_ZH
            val fileName = "TTS_${currentLang}_to_${targetLang}_$timeStamp.pcm"
            val file = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                fileName
            )

            FileOutputStream(file).use { it.write(audioData) }
            Log.e(TAG, "TTS audio saved: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving TTS audio: ${e.message}")
        }
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            (activity as? MainActivity)?.scrollChatToBottom()
        }
    }

    companion object {
        private const val TAG = "TranslateHelper"
    }
}
