package com.aibabel.voicetrans.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import com.aibabel.voicetrans.R
import com.aibabel.voicetrans.databinding.ActivitySettingsBinding
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

        val mode = prefs.getInt(KEY_SECONDARY_SCREEN_MODE, MODE_INDONESIAN)
        when (mode) {
            MODE_CHINESE -> binding.rbChinese.isChecked = true
            else -> binding.rbIndonesian.isChecked = true
        }

        binding.tvVersionName.text = "当前版本 ${packageManager.getPackageInfo(packageName, 0).versionName}"
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

    override fun initListener() {
        binding.btnBack.setOnClickListener { finish() }

        binding.switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply()
            ToastUtil.showShort(if (isChecked) "已开启自动播放" else "已关闭自动播放")
        }

        binding.rgSecondaryScreenMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbChinese -> MODE_CHINESE
                else -> MODE_INDONESIAN
            }
            prefs.edit().putInt(KEY_SECONDARY_SCREEN_MODE, mode).apply()
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
        const val MODE_INDONESIAN = 0  // 显示印尼语
        const val MODE_CHINESE = 1      // 显示中文
    }
}
