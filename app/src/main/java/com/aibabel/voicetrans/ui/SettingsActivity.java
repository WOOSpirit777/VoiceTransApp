package com.aibabel.voicetrans.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.aibabel.voicetrans.R;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "VoiceTransSettings";
    public static final String KEY_AUTO_PLAY = "auto_play_tts";

    private ImageButton btnBack;
    private Switch switchAutoPlay;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        btnBack = findViewById(R.id.btnBack);
        switchAutoPlay = findViewById(R.id.switchAutoPlay);

        // 加载保存的设置
        boolean autoPlay = prefs.getBoolean(KEY_AUTO_PLAY, true);
        switchAutoPlay.setChecked(autoPlay);

        btnBack.setOnClickListener(v -> finish());

        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_AUTO_PLAY, isChecked).apply();
        });
    }
}
