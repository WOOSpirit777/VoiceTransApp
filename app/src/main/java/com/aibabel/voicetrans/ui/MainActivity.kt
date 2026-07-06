package com.aibabel.voicetrans.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
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
import com.apkfuns.logutils.LogUtils
import com.quniu.baselib.activity.BaseVBRefreshActivityOpt
import commonutil.CommonUtils
import commonutil.VolumeController
import netlib.request.Page

class MainActivity : BaseVBRefreshActivityOpt<MainViewModel, ActivityMainBinding>(
    inflate = { ActivityMainBinding.inflate(it) }
) {

    private lateinit var tvStatus: TextView
    private lateinit var tvRecordHint: TextView
    private lateinit var btnChinese: Button
    private lateinit var btnIndonesian: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnQrCode: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnClear: TextView
    private lateinit var audioButtonView: AudioButtonView
    private lateinit var svMainDisplay: NestedScrollView
    private lateinit var tvMainDisplayLang: TextView
    private lateinit var tvMainDisplayText: TextView
    private lateinit var btnMainScreen: Button
    private lateinit var btnSecondaryScreen: Button
    private lateinit var bottomContainerS2t: LinearLayout
    private lateinit var bottomContainerS2s: LinearLayout

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefs: SharedPreferences

    private lateinit var displayManager: SecondaryDisplayManager
    private lateinit var translateHelper: TranslateHelper
    private lateinit var modeChangeReceiver: BroadcastReceiver
    private lateinit var sessionModeReceiver: BroadcastReceiver

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onResume() {
        super.onResume()
        translateHelper.updateMainDisplayLanguage()
        if (displayManager.isDualScreenAvailable()) {
            val mode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
            displayManager.showPresentation(mode)
        }
        registerReceiver(modeChangeReceiver, IntentFilter(SettingsActivity.ACTION_SECONDARY_MODE_CHANGED))
        registerReceiver(sessionModeReceiver, IntentFilter(SettingsActivity.ACTION_SESSION_MODE_CHANGED))
        updateSessionMode()
        updateS2sButtonText()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(modeChangeReceiver)
        unregisterReceiver(sessionModeReceiver)
//        displayManager.dismissPresentation()
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager.release()
        translateHelper.release()
    }
    private var physicalKeyPressedButton: Button? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            134 -> {
                if (!translateHelper.isRecording() && physicalKeyPressedButton == null) {
                    startRecordingByPhysicalKey(btnMainScreen)
                }
                true
            }
            135 -> {
                if (!translateHelper.isRecording() && physicalKeyPressedButton == null) {
                    startRecordingByPhysicalKey(btnSecondaryScreen)
                }
                true
            }
            136 ->{
                finish()
                true
            }
            else -> false
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            134, 135 -> {
                stopRecordingByPhysicalKey()
                true
            }
            else -> false
        }
    }

    private fun startRecordingByPhysicalKey(button: Button) {
        val lang = if (button.id == R.id.btnMainScreen) getMainScreenLang() else getSecondaryScreenLang()
        translateHelper.startRecordingWithLang(lang)
        animateButtonPress(button, true)
        physicalKeyPressedButton = button
    }

    private fun stopRecordingByPhysicalKey() {
        physicalKeyPressedButton?.let {
            translateHelper.stopRecording()
            animateButtonRelease(it)
        }
        physicalKeyPressedButton = null
    }
    override fun initView() {
        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        tvStatus = binding.tvStatus
        tvRecordHint = binding.tvRecordHint
        btnChinese = binding.btnChinese
        btnIndonesian = binding.btnIndonesian
        btnBack = binding.btnBack
        btnQrCode = binding.btnQrCode
        btnSettings = binding.btnSettings
        btnClear = binding.btnClear
        audioButtonView = binding.audioButtonView
        svMainDisplay = binding.svMainDisplay
        tvMainDisplayLang = binding.tvMainDisplayLang
        tvMainDisplayText = binding.tvMainDisplayText
        btnMainScreen = binding.btnMainScreen
        btnSecondaryScreen = binding.btnSecondaryScreen
        bottomContainerS2t = binding.bottomContainerS2t
        bottomContainerS2s = binding.bottomContainerS2s

        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter

        updateLanguageButtons(Constants.LANG_ZH)

        displayManager = SecondaryDisplayManager(this, mainHandler)
        displayManager.turnOnSubScreen()
        displayManager.register()
        val mode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        displayManager.setMode(mode)

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
            bigSoundView = binding.bigSoundView,

            recordingContainer = binding.recordingContainer,
            onStatusUpdate = { text, bg, fg -> updateStatus(text, bg, fg) },
            onRecordingStateChange = { enabled ->
                btnChinese.isEnabled = enabled
                btnIndonesian.isEnabled = enabled
            }
        )
        translateHelper.init()
        translateHelper.updateMainDisplayLanguage()

        modeChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SettingsActivity.ACTION_SECONDARY_MODE_CHANGED) {
                    val mode = intent.getIntExtra(SettingsActivity.EXTRA_MODE, SettingsActivity.MODE_INDONESIAN)
                    displayManager.setMode(mode)
                    displayManager.showPresentation(mode)
                    translateHelper.updateMainDisplayLanguage()
                    translateHelper.clearDisplay()
                    updateS2sButtonText()
                }
            }
        }

        sessionModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == SettingsActivity.ACTION_SESSION_MODE_CHANGED) {
                    updateSessionMode()
                }
            }
        }

        updateSessionMode()
        updateS2sButtonText()

        mainHandler.postDelayed({ displayManager.detectAndInit() }, 500)
    }

    override fun initData() {
        checkPermissions()
    }

    override fun initListener() {
        btnBack.setOnClickListener {
            finish()
        }

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
//            translateHelper.playMainDisplayAudio()
        }

        audioButtonView.setCallback(object : AudioButtonView.AudioButtonCallback {
            override fun onRecordingStart() { translateHelper.startRecording() }
            override fun onRecordingEnd() { translateHelper.stopRecording() }
        })

        btnMainScreen.setOnLongClickListener {
            val lang = getMainScreenLang()
            translateHelper.startRecordingWithLang(lang)
            animateButtonPress(btnMainScreen, true)
            true
        }
        btnMainScreen.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> animateButtonPress(btnMainScreen, false)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    translateHelper.stopRecording()
                    animateButtonRelease(btnMainScreen)
                }
            }
            false
        }

        btnSecondaryScreen.setOnLongClickListener {
            val lang = getSecondaryScreenLang()
            translateHelper.startRecordingWithLang(lang)
            animateButtonPress(btnSecondaryScreen, true)
            true
        }
        btnSecondaryScreen.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> animateButtonPress(btnSecondaryScreen, false)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    translateHelper.stopRecording()
                    animateButtonRelease(btnSecondaryScreen)
                }
            }
            false
        }

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

    private fun updateSessionMode() {
        val sessionMode = prefs.getString(SettingsActivity.KEY_SESSION_MODE, Constants.MODE_S2S)
        if (sessionMode == Constants.MODE_S2S) {
            bottomContainerS2t.visibility = View.GONE
            bottomContainerS2s.visibility = View.VISIBLE
        } else {
            bottomContainerS2t.visibility = View.VISIBLE
            bottomContainerS2s.visibility = View.GONE
        }
    }

    private fun updateS2sButtonText() {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        if (secondaryMode == SettingsActivity.MODE_INDONESIAN) {
            btnMainScreen.text = getString(R.string.chinese)
            btnSecondaryScreen.text = getString(R.string.indonesian)
        } else {
            btnMainScreen.text = getString(R.string.indonesian)
            btnSecondaryScreen.text = getString(R.string.chinese)
        }
    }

    private fun getMainScreenLang(): String {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        return if (secondaryMode == SettingsActivity.MODE_INDONESIAN) Constants.LANG_ZH else Constants.LANG_ID
    }

    private fun getSecondaryScreenLang(): String {
        val secondaryMode = prefs.getInt(SettingsActivity.KEY_SECONDARY_SCREEN_MODE, SettingsActivity.MODE_INDONESIAN)
        return if (secondaryMode == SettingsActivity.MODE_INDONESIAN) Constants.LANG_ID else Constants.LANG_ZH
    }

    private fun animateButtonPress(button: android.widget.Button, recording: Boolean) {
        button.animate()
            .scaleX(if (recording) 0.85f else 0.95f)
            .scaleY(if (recording) 0.85f else 0.95f)
            .setDuration(150)
            .start()
    }

    private fun animateButtonRelease(button: android.widget.Button) {
        button.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(150)
            .start()
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
