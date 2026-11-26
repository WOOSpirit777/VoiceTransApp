package com.aibabel.voicetrans.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.databinding.ActivitySettingsBinding
import com.aibabel.voicetrans.offline.OfflinePrefs
import com.aibabel.voicetrans.offline.OfflineTranslationManager
import com.aibabel.voicetrans.util.Constants
import com.aibabel.voicetrans.viewmodel.SettingsViewModel
import com.quniu.baselib.activity.BaseVBActivityOpt
import com.quniu.baselib.util.ToastUtil

class SettingsActivity : BaseVBActivityOpt<SettingsViewModel, ActivitySettingsBinding>(
    inflate = { ActivitySettingsBinding.inflate(it) }
) {

    private lateinit var prefs: SharedPreferences
    private var updateDialog: UpdateDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        val autoPlay = prefs.getBoolean(KEY_AUTO_PLAY, true)
        binding.switchAutoPlay.isChecked = autoPlay

        val pair = prefs.getString(KEY_TRANSLATION_PAIR, PAIR_ZH_ID)
        updateSecondaryScreenOptions(pair)

        val mode = prefs.getInt(KEY_SECONDARY_SCREEN_MODE, MODE_INDONESIAN)
        when (mode) {
            MODE_CHINESE -> binding.rbChinese.isChecked = true
            else -> binding.rbIndonesian.isChecked = true
        }

        val sessionMode = prefs.getString(KEY_SESSION_MODE, Constants.MODE_S2S)
        when (sessionMode) {
            Constants.MODE_S2T -> binding.rbS2t.isChecked = true
            else -> binding.rbS2s.isChecked = true
        }

        binding.tvVersionName.text = "当前版本 ${packageManager.getPackageInfo(packageName, 0).versionName}"

        initOfflineViews()
    }

    // ---------------- 离线翻译 ----------------

    /**
     * 初始化离线卡片。
     *
     * 开关状态来自 [OfflinePrefs]，实际模型加载由 [OfflineTranslationManager] 负责；
     * 本页只做「展示 + 把用户操作转成 manager 调用」，不直接碰 SDK。
     */
    private fun initOfflineViews() {
        binding.switchOffline.isChecked = OfflinePrefs.isEnabled(this)
        renderOfflineSource()
        renderOfflineStatus(OfflineTranslationManager.current())

        binding.switchOffline.setOnCheckedChangeListener { _, checked ->
            binding.layoutOfflineStatus.visibility = if (checked) View.VISIBLE else View.GONE
            OfflineTranslationManager.setEnabled(this, checked)
            ToastUtil.showShort(
                getString(if (checked) R.string.offline_toggle_on else R.string.offline_toggle_off)
            )
        }

        // 进来时若开关是开的，状态区直接显示（与开关联动，避免残留）
        binding.layoutOfflineStatus.visibility =
            if (binding.switchOffline.isChecked) View.VISIBLE else View.GONE

        binding.btnOfflineRetry.setOnClickListener {
            OfflineTranslationManager.retry(this)
        }

        binding.btnOfflineSource.setOnClickListener {
            val next = if (OfflinePrefs.source(this) == OfflinePrefs.SOURCE_DOWNLOAD) {
                OfflinePrefs.SOURCE_BUILTIN
            } else {
                OfflinePrefs.SOURCE_DOWNLOAD
            }
            OfflinePrefs.setSource(this, next)
            renderOfflineSource()
            OfflineTranslationManager.setSource(this, next)
            ToastUtil.showShort(getString(R.string.offline_source_changed))
        }

        binding.btnOpenOffline.setOnClickListener {
            if (!OfflineTranslationManager.isReady()) {
                ToastUtil.showShort(getString(R.string.offline_waiting_model))
                return@setOnClickListener
            }
            startActivity(Intent(this, OfflineTransActivity::class.java))
        }
    }

    private fun renderOfflineSource() {
        binding.tvOfflineSource.text = when (OfflinePrefs.source(this)) {
            OfflinePrefs.SOURCE_BUILTIN -> getString(R.string.offline_source_builtin)
            else -> getString(R.string.offline_source_download)
        }
    }

    /** 把 manager 的状态快照映射成界面元素。 */
    private fun renderOfflineStatus(snapshot: OfflineTranslationManager.Snapshot) {
        val dot = binding.viewOfflineDot
        val status = binding.tvOfflineStatus
        val pb = binding.pbOffline
        val retry = binding.btnOfflineRetry

        when (snapshot.state) {
            OfflineTranslationManager.State.DISABLED -> {
                status.text = getString(R.string.offline_status_idle)
                dot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_hint)
                pb.visibility = View.GONE
                retry.visibility = View.GONE
            }

            OfflineTranslationManager.State.IDLE -> {
                status.text = getString(R.string.offline_status_idle)
                dot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_hint)
                pb.visibility = View.GONE
                retry.visibility = View.GONE
            }

            OfflineTranslationManager.State.LOADING -> {
                status.text = snapshot.message.ifBlank { getString(R.string.offline_status_loading) }
                dot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_teal)
                pb.visibility = View.VISIBLE
                pb.progress = snapshot.progress
                retry.visibility = View.GONE
            }

            OfflineTranslationManager.State.READY -> {
                status.text = if (snapshot.mtReady) {
                    getString(R.string.offline_status_ready)
                } else {
                    getString(R.string.offline_status_asr_only)
                }
                dot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_teal)
                pb.visibility = View.GONE
                retry.visibility = View.GONE
            }

            OfflineTranslationManager.State.FAILED -> {
                status.text = snapshot.message.ifBlank { getString(R.string.offline_status_failed) }
                dot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_status)
                pb.visibility = View.GONE
                retry.visibility = View.VISIBLE
            }
        }
    }

    private val offlineObserver = OfflineTranslationManager.Observer { snapshot ->
        renderOfflineStatus(snapshot)
    }

    override fun onResume() {
        super.onResume()
        OfflineTranslationManager.observe(offlineObserver)
    }

    override fun onPause() {
        super.onPause()
        OfflineTranslationManager.removeObserver(offlineObserver)
    }

    /** 根据翻译语言对动态更新副屏RadioButton文本 */
    private fun updateSecondaryScreenOptions(pair: String?) {
        if (pair == PAIR_ZH_EN) {
            binding.rbIndonesian.text = getString(R.string.english)
            binding.rbIndonesian.isChecked = false
        } else {
            binding.rbIndonesian.text = getString(R.string.indonesian)
        }
    }

    override fun initData() {
        viewModel.checkResult.observe(this) { versionData ->
            if (versionData.is_update) {
                showUpdateDialog(versionData.url)
            } else {
                ToastUtil.showShort(getString(R.string.no_new_version))
            }
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            136 ->{
                finish()
                true
            }
            else -> false
        }
    }
    override fun initListener() {
        binding.btnBack.setOnClickListener { finish() }

        binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply()
            ToastUtil.showShort(if (isChecked) "已开启自动播放" else "已关闭自动播放")
        }

        binding.rgSecondaryScreenMode.setOnCheckedChangeListener { _, checkedId ->
            val pair = prefs.getString(KEY_TRANSLATION_PAIR, PAIR_ZH_ID)
            val mode = when (checkedId) {
                R.id.rbChinese -> MODE_CHINESE
                else -> if (pair == PAIR_ZH_EN) MODE_ENGLISH else MODE_INDONESIAN
            }
            prefs.edit().putInt(KEY_SECONDARY_SCREEN_MODE, mode).apply()
            
            val intent = Intent(ACTION_SECONDARY_MODE_CHANGED)
            intent.putExtra(EXTRA_MODE, mode)
            sendBroadcast(intent)
        }

        binding.rgSessionMode.setOnCheckedChangeListener { _, checkedId ->
            val sessionMode = when (checkedId) {
                R.id.rbS2t -> Constants.MODE_S2T
                else -> Constants.MODE_S2S
            }
            prefs.edit().putString(KEY_SESSION_MODE, sessionMode).apply()
            
            val intent = Intent(ACTION_SESSION_MODE_CHANGED)
            intent.putExtra(EXTRA_SESSION_MODE, sessionMode)
            sendBroadcast(intent)
        }

        binding.layoutCheckUpdate.setOnClickListener {
            viewModel.checkUpdate()
        }
    }

    private fun showUpdateDialog(url: String) {
        if (updateDialog == null) {
            updateDialog = UpdateDialog(this)
        }
        updateDialog?.setDownloadUrl(url)
        updateDialog?.show()
    }

    override fun useEventBus(): Boolean = false

    companion object {
        const val PREFS_NAME = "VoiceTransSettings"
        const val KEY_AUTO_PLAY = "auto_play_tts"
        const val KEY_SECONDARY_SCREEN_MODE = "secondary_screen_mode"
        const val KEY_SESSION_MODE = "session_mode"
        const val KEY_TRANSLATION_PAIR = "translation_pair"
        const val PAIR_ZH_ID = "zh_id"
        const val PAIR_ZH_EN = "zh_en"
        const val MODE_INDONESIAN = 0  // 显示印尼语
        const val MODE_CHINESE = 1      // 显示中文
        const val MODE_ENGLISH = 2      // 显示英文

        const val ACTION_SECONDARY_MODE_CHANGED = "com.aibabel.voicetrans.ACTION_SECONDARY_MODE_CHANGED"
        const val ACTION_SESSION_MODE_CHANGED = "com.aibabel.voicetrans.ACTION_SESSION_MODE_CHANGED"
        const val ACTION_TRANSLATION_PAIR_CHANGED = "com.aibabel.voicetrans.ACTION_TRANSLATION_PAIR_CHANGED"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SESSION_MODE = "session_mode"
        const val EXTRA_TRANSLATION_PAIR = "translation_pair"
    }
}
