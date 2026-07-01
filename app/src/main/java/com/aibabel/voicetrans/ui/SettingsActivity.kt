package com.aibabel.voicetrans.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import com.aibabel.voicetrans.R

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var switchAutoPlay: Switch
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        btnBack = findViewById(R.id.btnBack)
        switchAutoPlay = findViewById(R.id.switchAutoPlay)

        val autoPlay = prefs.getBoolean(KEY_AUTO_PLAY, true)
        switchAutoPlay.isChecked = autoPlay

        btnBack.setOnClickListener { finish() }

        switchAutoPlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply()
        }
    }

    companion object {
        const val PREFS_NAME = "VoiceTransSettings"
        const val KEY_AUTO_PLAY = "auto_play_tts"
    }
}
