package com.aibabel.voicetrans.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.audio.AudioPlayerManager
import com.aibabel.voicetrans.audio.AudioRecorderManager
import com.aibabel.voicetrans.model.ChatMessage
import com.aibabel.voicetrans.model.TranslationResult
import com.aibabel.voicetrans.util.Constants
import com.aibabel.voicetrans.websocket.WebSocketManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvRecordHint: TextView
    private lateinit var btnChinese: Button
    private lateinit var btnIndonesian: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var rvChat: RecyclerView
    private lateinit var audioButtonView: AudioButtonView

    private lateinit var chatAdapter: ChatAdapter

    private lateinit var webSocketManager: WebSocketManager
    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var audioPlayerManager: AudioPlayerManager

    private var currentLang = Constants.LANG_ZH
    private var isRecording = false
    private var currentMessage: ChatMessage? = null
    private val isProcessing = AtomicBoolean(false)
    private val ttsAudioBuffer = ByteArrayOutputStream()

    private var presentation: SecondaryPresentation? = null
    private var secondaryDisplay: Display? = null
    private var isDualScreen = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var translateFinishTimeoutRunnable: Runnable? = null

    private lateinit var prefs: SharedPreferences

    override fun onResume() {
        super.onResume()
        if (isDualScreen && secondaryDisplay != null) {
            if (presentation == null || !presentation!!.isShowing) {
                presentation = SecondaryPresentation(this, secondaryDisplay!!)
                presentation!!.show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (presentation != null && presentation!!.isShowing) {
            presentation!!.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        turnOffSubScreen()
        if (presentation != null && presentation!!.isShowing) {
            presentation!!.dismiss()
        }
        webSocketManager.disconnect()
        audioRecorderManager.release()
        audioPlayerManager.release()
    }

    private fun detectAndInitSecondaryScreen() {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        Log.e(TAG, "检测到 " + displays.size + " 个屏幕")

        for (i in 1 until displays.size) {
            if (displays[i].isValid) {
                secondaryDisplay = displays[i]
                isDualScreen = true

                val metrics = DisplayMetrics()
                secondaryDisplay!!.getMetrics(metrics)
                Log.e(TAG, "副屏: " + secondaryDisplay!!.name +
                        ", 分辨率: " + metrics.widthPixels + "x" + metrics.heightPixels)
                break
            }
        }

        if (isDualScreen && secondaryDisplay != null) {
            presentation = SecondaryPresentation(this, secondaryDisplay!!)
            presentation!!.show()
            Log.e(TAG, "双屏模式已启用")
        } else {
            Log.e(TAG, "仅检测到单屏")
        }
    }

    private fun turnOnSubScreen() {
        val intent = Intent("custom.intent.TURN_ON_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        sendBroadcast(intent)
        Log.e(TAG, "Turn on sub screen")
    }

    private fun turnOffSubScreen() {
        val intent = Intent("custom.intent.TURN_OFF_SUB_SCREEN")
        intent.setPackage("com.android.settings")
        sendBroadcast(intent)
        Log.e(TAG, "Turn off sub screen")
    }

    private fun updateSecondScreen(sourceText: String?, targetText: String?, sourceLang: String?, targetLang: String?) {
        if (isDualScreen && presentation != null) {
            presentation!!.updateContent(targetText, sourceLang, targetLang)
            Log.e(TAG, "Update second screen: $targetText")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== onCreate called ===")
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        initViews()
        initManagers()
        setupListeners()
        checkPermissions()

        detectAndInitSecondaryScreen()
        turnOnSubScreen()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvRecordHint = findViewById(R.id.tvRecordHint)
        btnChinese = findViewById(R.id.btnChinese)
        btnIndonesian = findViewById(R.id.btnIndonesian)
        btnSettings = findViewById(R.id.btnSettings)
        rvChat = findViewById(R.id.rvChat)
        audioButtonView = findViewById(R.id.audioButtonView)

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        updateLanguageButtons(Constants.LANG_ZH)
    }

    private fun initManagers() {
        webSocketManager = WebSocketManager.getInstance()
        webSocketManager.setCallback(webSocketCallback)
        updateLanguageInManager()

        audioRecorderManager = AudioRecorderManager.getInstance()
        audioRecorderManager.setCallback(audioRecordCallback)

        audioPlayerManager = AudioPlayerManager.getInstance()
        audioPlayerManager.setCallback(audioPlayerCallback)
    }

    private fun updateLanguageInManager() {
        val targetLang = if (Constants.LANG_ZH == currentLang) Constants.LANG_ID else Constants.LANG_ZH
        webSocketManager.setLanguage(currentLang, targetLang)
    }

    private fun setupListeners() {
        btnChinese.setOnClickListener {
            if (!isRecording) {
                currentLang = Constants.LANG_ZH
                updateLanguageInManager()
                updateLanguageButtons(currentLang)
            }
        }

        btnIndonesian.setOnClickListener {
            if (!isRecording) {
                currentLang = Constants.LANG_ID
                updateLanguageInManager()
                updateLanguageButtons(currentLang)
            }
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this@MainActivity, SettingsActivity::class.java)
            startActivity(intent)
        }

        audioButtonView.setCallback(object : AudioButtonView.AudioButtonCallback {
            override fun onRecordingStart() {
                startRecording()
            }

            override fun onRecordingEnd() {
                stopRecording()
            }
        })

        chatAdapter.setOnItemClickListener(object : ChatAdapter.OnItemClickListener {
            override fun onItemClick(message: ChatMessage) {
                Log.e(TAG, "Message clicked: " + message.id)
            }

            override fun onSourceAudioClick(message: ChatMessage) {
                val audioData = message.sourceAudioData
                if (audioData != null && audioData.isNotEmpty()) {
                    audioPlayerManager.playPcm(audioData)
                    Toast.makeText(this@MainActivity, "正在播放原文音频", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "无原文音频数据", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onTargetAudioClick(message: ChatMessage) {
                val audioData = message.ttsAudioData
                if (audioData != null && audioData.isNotEmpty()) {
                    audioPlayerManager.playPcm(audioData)
                    Toast.makeText(this@MainActivity, "正在播放翻译音频", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "无翻译音频数据", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun updateLanguageButtons(selectedLang: String) {
        btnChinese.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)
        btnIndonesian.backgroundTintList = ContextCompat.getColorStateList(this, R.color.gray)

        when (selectedLang) {
            Constants.LANG_ZH -> btnChinese.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            Constants.LANG_ID -> btnIndonesian.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun checkPermissions() {
        Log.i(TAG, "=== Checking permissions ===")
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        var needRequest = false
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true
                Log.i(TAG, "Permission not granted: $permission")
            }
        }

        Log.i(TAG, "Permission check result - needRequest: $needRequest")

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
        } else {
            Log.i(TAG, "Permissions already granted, connecting WebSocket...")
            connectWebSocket()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            var allGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (allGranted) {
                connectWebSocket()
            } else {
                Toast.makeText(this, "需要麦克风和存储权限才能使用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectWebSocket() {
        updateStatus("连接中...", 0xFFFFF3E0.toInt(), 0xFFE65100.toInt())
        webSocketManager.connect()
    }

    private fun startRecording() {
        if (audioPlayerManager.isPlaying()) {
            audioPlayerManager.stop()
            Log.i(TAG, "Stopped TTS playback to start recording")
        }

        isRecording = true
        isProcessing.set(true)

        tvRecordHint.text = "正在录音..."
        btnChinese.isEnabled = false
        btnIndonesian.isEnabled = false

        if (!webSocketManager.isConnected()) {
            updateStatus("连接中...", 0xFFFFF3E0.toInt(), 0xFFE65100.toInt())
            webSocketManager.connect()
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
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false

        mainHandler.post {
            tvRecordHint.text = "正在识别..."
            btnChinese.isEnabled = true
            btnIndonesian.isEnabled = true
        }

        audioRecorderManager.stopRecording()
        webSocketManager.sendFinish()

        translateFinishTimeoutRunnable = Runnable {
            Log.e(TAG, "Translate finish timeout after ${Constants.TRANSLATE_FINISH_TIMEOUT_MS}ms, disconnecting")
            isProcessing.set(false)
            tvRecordHint.text = "翻译超时"
            webSocketManager.disconnect()
        }
        mainHandler.postDelayed(translateFinishTimeoutRunnable!!, Constants.TRANSLATE_FINISH_TIMEOUT_MS)
    }

    private val webSocketCallback = object : WebSocketManager.WebSocketCallback {
        override fun onConnected() {
            mainHandler.post {
                updateStatus("已连接", 0xFFE8F5E9.toInt(), 0xFF2E7D32.toInt())
                Toast.makeText(this@MainActivity, "连接成功", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDisconnected() {
            mainHandler.post { updateStatus("连接断开", 0xFFFFEBEE.toInt(), 0xFFC62828.toInt()) }
        }

        override fun onMessage(message: String) {
            Log.e(TAG, "Text message: $message")
            when {
                Constants.RESP_TRANSLATE_FINISH == message -> handleTranslateFinish()
                Constants.RESP_PROCESSING_COMPLETED == message -> Log.e(TAG, "Processing completed")
                Constants.RESP_CONNECTION_CLOSING == message || "close" == message -> {
                    Log.e(TAG, "Connection closing received")
                    mainHandler.post {
                        tvRecordHint.text = "点击按钮开始说话"
                        webSocketManager.disconnect()
                    }
                }
            }
        }

        override fun onResult(result: TranslationResult) {
            Log.e(TAG, "Result: $result")
            mainHandler.post { handleResult(result) }
        }

        override fun onBinaryData(data: ByteArray) {
            Log.e(TAG, "Received binary data: " + data.size + " bytes, buffering...")
            synchronized(ttsAudioBuffer) {
                try {
                    ttsAudioBuffer.write(data)
                    Log.e(TAG, "Total buffered: " + ttsAudioBuffer.size() + " bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to buffer audio: " + e.message)
                }
            }
        }

        override fun onError(error: String) {
            mainHandler.post {
                updateStatus("连接错误: $error", 0xFFFFEBEE.toInt(), 0xFFC62828.toInt())
                Toast.makeText(this@MainActivity, "连接错误: $error", Toast.LENGTH_SHORT).show()
                isRecording = false
                isProcessing.set(false)
                tvRecordHint.text = "连接失败，点击按钮重试"
                btnChinese.isEnabled = true
                btnIndonesian.isEnabled = true
                audioButtonView.setRecordingState(false)
            }
        }
    }

    private fun handleResult(result: TranslationResult) {
        Log.e(TAG, "Handle result: $result")

        if (result.isText) {
            if (currentMessage != null) {
                if (result.isSource) {
                    val existingSource = currentMessage!!.sourceText ?: ""
                    currentMessage!!.sourceText = existingSource + result.text
                } else if (result.isTarget) {
                    val existingTarget = currentMessage!!.targetText ?: ""
                    currentMessage!!.targetText = existingTarget + result.text
                    updateSecondScreen(
                        currentMessage!!.sourceText,
                        currentMessage!!.targetText,
                        currentMessage!!.sourceLang,
                        currentMessage!!.targetLang
                    )
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
            val errorMsg = "错误: " + (result.message ?: "未知错误")
            val finalMsg = if (result.code != null) "错误(" + result.code + "): " + result.message else errorMsg
            Toast.makeText(this, finalMsg, Toast.LENGTH_SHORT).show()
            isProcessing.set(false)
        } else {
            Log.e(TAG, "Other message: " + result.text)
        }
    }

    private fun handleTranslateFinish() {
        Log.e(TAG, "Translate finish received")

        translateFinishTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            translateFinishTimeoutRunnable = null
        }

        tvRecordHint.text = "翻译完成"
        Toast.makeText(this, "翻译完成", Toast.LENGTH_SHORT).show()

        if (currentMessage != null) {
            val sourceLang = currentMessage!!.sourceLang
            val targetLang = currentMessage!!.targetLang
            val targetText = currentMessage!!.targetText ?: ""
            updateSecondScreen(currentMessage!!.sourceText, targetText, sourceLang, targetLang)
        }

        synchronized(ttsAudioBuffer) {
            val audioData = ttsAudioBuffer.toByteArray()
            if (audioData.isNotEmpty()) {
                if (currentMessage != null) {
                    currentMessage!!.ttsAudioData = audioData
                    chatAdapter.updateLastMessage(currentMessage!!)
                }

                saveTtsAudioToFile(audioData)

                val autoPlay = prefs.getBoolean(SettingsActivity.KEY_AUTO_PLAY, true)
                if (autoPlay) {
                    Log.e(TAG, "Playing TTS audio: " + audioData.size + " bytes")
                    audioPlayerManager.playPcm(audioData)
                } else {
                    Log.e(TAG, "Auto-play disabled, TTS audio saved but not played")
                }
            } else {
                Log.e(TAG, "No TTS audio data to play")
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            webSocketManager.sendClose()
            isProcessing.set(false)
            currentMessage = null
            audioButtonView.setRecordingState(false)
            tvRecordHint.text = "点击按钮开始说话"
        }, 500)
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

            val fos = FileOutputStream(file)
            fos.write(audioData)
            fos.close()

            Log.e(TAG, "TTS audio saved: " + file.absolutePath)
        } catch (e: IOException) {
            Log.e(TAG, "Error saving TTS audio: " + e.message)
        }
    }

    private val audioRecordCallback = object : AudioRecorderManager.AudioRecordCallback {
        override fun onRecordingStart() {
            Log.e(TAG, "Recording started")
        }

        override fun onRecordingData(data: ByteArray, size: Int) {
            if (webSocketManager.isConnected()) {
                webSocketManager.sendAudioData(data)
            }
        }

        override fun onRecordingStop(fullData: ByteArray?) {
            Log.e(TAG, "Recording stopped, total: " + (fullData?.size ?: 0) + " bytes")
            if (currentMessage != null && fullData != null && fullData.isNotEmpty()) {
                currentMessage!!.sourceAudioData = fullData
                chatAdapter.updateLastMessage(currentMessage!!)
            }
        }

        override fun onRecordingError(error: String) {
            mainHandler.post {
                Toast.makeText(this@MainActivity, "录音错误: $error", Toast.LENGTH_SHORT).show()
                isRecording = false
                isProcessing.set(false)
                tvRecordHint.text = "点击按钮开始说话"
                btnChinese.isEnabled = true
                btnIndonesian.isEnabled = true
            }
        }
    }

    private val audioPlayerCallback = object : AudioPlayerManager.AudioPlayerCallback {
        override fun onPlaybackStart() {
            Log.e(TAG, "Playback started")
        }

        override fun onPlaybackComplete() {
            Log.e(TAG, "Playback completed")
        }

        override fun onPlaybackError(error: String) {
            Log.e(TAG, "Playback error: $error")
        }
    }

    private fun updateStatus(text: String, bgColor: Int, textColor: Int) {
        tvStatus.text = text
        tvStatus.setBackgroundColor(bgColor)
        tvStatus.setTextColor(textColor)
    }

    private fun scrollToBottom() {
        if (chatAdapter.itemCount > 0) {
            rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PERMISSIONS = 1001
    }
}
