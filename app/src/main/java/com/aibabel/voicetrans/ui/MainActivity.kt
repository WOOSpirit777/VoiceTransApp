package com.aibabel.voicetrans.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.databinding.ActivityMainBinding
import com.aibabel.voicetrans.model.ChatMessage
import com.aibabel.voicetrans.util.Constants
import com.aibabel.voicetrans.viewmodel.MainViewModel
import com.quniu.baselib.activity.BaseVBRefreshActivityOpt
import netlib.request.Page

class MainActivity : BaseVBRefreshActivityOpt<MainViewModel, ActivityMainBinding>(
    inflate = { ActivityMainBinding.inflate(it) }
) {

    private lateinit var tvStatus: TextView
    private lateinit var tvRecordHint: TextView
    private lateinit var btnChinese: Button
    private lateinit var btnIndonesian: Button
    private lateinit var btnQrCode: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnClear: TextView
    private lateinit var audioButtonView: AudioButtonView
    private lateinit var svMainDisplay: ScrollView
    private lateinit var tvMainDisplayLang: TextView
    private lateinit var tvMainDisplayText: TextView

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefs: SharedPreferences

    private lateinit var displayManager: SecondaryDisplayManager
    private lateinit var translateHelper: TranslateHelper

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        translateHelper.updateMainDisplayLanguage()
        if (displayManager.isDualScreenAvailable()) {
            displayManager.showPresentation()
        }
    }

    override fun onPause() {
        super.onPause()
//        displayManager.dismissPresentation()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.release()
        translateHelper.release()
    }

    override fun initView() {
        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        tvStatus = binding.tvStatus
        tvRecordHint = binding.tvRecordHint
        btnChinese = binding.btnChinese
        btnIndonesian = binding.btnIndonesian
        btnQrCode = binding.btnQrCode
        btnSettings = binding.btnSettings
        btnClear = binding.btnClear
        audioButtonView = binding.audioButtonView
        svMainDisplay = binding.svMainDisplay
        tvMainDisplayLang = binding.tvMainDisplayLang
        tvMainDisplayText = binding.tvMainDisplayText

        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter

        updateLanguageButtons(Constants.LANG_ZH)

        displayManager = SecondaryDisplayManager(this, mainHandler)
        displayManager.turnOnSubScreen()
        displayManager.register()

        translateHelper = TranslateHelper(
            activity = this,
            prefs = prefs,
            handler = mainHandler,
            chatAdapter = chatAdapter,
            secondaryDisplayManager = displayManager,
            svMainDisplay = svMainDisplay,
            tvMainDisplayText = tvMainDisplayText,
            tvMainDisplayLang = tvMainDisplayLang,
            tvRecordHint = tvRecordHint,
            audioButtonView = audioButtonView,
            onStatusUpdate = { text, bg, fg -> updateStatus(text, bg, fg) },
            onRecordingStateChange = { enabled ->
                btnChinese.isEnabled = enabled
                btnIndonesian.isEnabled = enabled
            }
        )
        translateHelper.init()
        translateHelper.updateMainDisplayLanguage()

        mainHandler.postDelayed({ displayManager.detectAndInit() }, 500)
    }

    override fun initData() {
        checkPermissions()
    }

    override fun initListener() {
        btnChinese.setOnClickListener {
            if (!translateHelper.isRecording()) {
                translateHelper.setCurrentLang(Constants.LANG_ZH)
                updateLanguageButtons(Constants.LANG_ZH)
            }
        }

        btnIndonesian.setOnClickListener {
            if (!translateHelper.isRecording()) {
                translateHelper.setCurrentLang(Constants.LANG_ID)
                updateLanguageButtons(Constants.LANG_ID)
            }
        }

        btnQrCode.setOnClickListener {
            startActivity(Intent(this, BroadcastUrlActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnClear.setOnClickListener {
            translateHelper.clearDisplay()
        }

        tvMainDisplayText.setOnClickListener {
            translateHelper.playMainDisplayAudio()
        }

        audioButtonView.setCallback(object : AudioButtonView.AudioButtonCallback {
            override fun onRecordingStart() { translateHelper.startRecording() }
            override fun onRecordingEnd() { translateHelper.stopRecording() }
        })

        chatAdapter.setOnItemClickListener(object : ChatAdapter.OnItemClickListener {
            override fun onItemClick(message: ChatMessage) {
                Log.e(TAG, "Message clicked: ${message.id}")
            }

            override fun onSourceAudioClick(message: ChatMessage) {
                translateHelper.playSourceAudio(message)
            }

            override fun onTargetAudioClick(message: ChatMessage) {
                translateHelper.playTargetAudio(message)
            }
        })
    }

    override fun loadForList(more: Boolean, page: Page) {}

    override fun useEventBus(): Boolean = false

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i(TAG, "Permissions granted")
            } else {
                Toast.makeText(this, "需要麦克风和存储权限才能使用", Toast.LENGTH_SHORT).show()
            }
        }
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
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS)
        }
    }

    private fun updateStatus(text: String, bgColor: Int, textColor: Int) {
        tvStatus.text = text
        tvStatus.setBackgroundColor(bgColor)
        tvStatus.setTextColor(textColor)
    }

    fun scrollChatToBottom() {
        if (chatAdapter.itemCount > 0) {
            binding.rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_PERMISSIONS = 1001
    }
}
