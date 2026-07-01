package com.aibabel.voicetrans.ui;

import android.Manifest;
import android.app.Presentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aibabel.voicetrans.R;
import com.aibabel.voicetrans.audio.AudioPlayerManager;
import com.aibabel.voicetrans.audio.AudioRecorderManager;
import com.aibabel.voicetrans.model.ChatMessage;
import com.aibabel.voicetrans.model.TranslationResult;
import com.aibabel.voicetrans.util.Constants;
import com.aibabel.voicetrans.websocket.WebSocketManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 1001;

    private TextView tvStatus;
    private TextView tvRecordHint;
    private Button btnChinese;
    private Button btnIndonesian;
    private ImageButton btnSettings;
    private RecyclerView rvChat;
    private AudioButtonView audioButtonView;

    private ChatAdapter chatAdapter;

    private WebSocketManager webSocketManager;
    private AudioRecorderManager audioRecorderManager;
    private AudioPlayerManager audioPlayerManager;

    private String currentLang = Constants.LANG_ZH;
    private boolean isRecording = false;
    private ChatMessage currentMessage = null;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    private ByteArrayOutputStream ttsAudioBuffer = new ByteArrayOutputStream();

    // 副屏相关
    private SecondaryPresentation presentation;
    private Display secondaryDisplay;
    private boolean isDualScreen = false;

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable translateFinishTimeoutRunnable;

    @Override
    protected void onResume() {
        super.onResume();
        // 重新显示副屏
        if (isDualScreen && secondaryDisplay != null) {
            if (presentation == null || !presentation.isShowing()) {
                presentation = new SecondaryPresentation(this, secondaryDisplay);
                presentation.show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停时关闭副屏Presentation
        if (presentation != null && presentation.isShowing()) {
            presentation.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭副屏
        turnOffSubScreen();
        if (presentation != null && presentation.isShowing()) {
            presentation.dismiss();
        }
        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
        if (audioRecorderManager != null) {
            audioRecorderManager.release();
        }
        if (audioPlayerManager != null) {
            audioPlayerManager.release();
        }
    }

    /**
     * 检测并初始化副屏
     */
    private void detectAndInitSecondaryScreen() {
        DisplayManager displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();

        Log.e(TAG, "检测到 " + displays.length + " 个屏幕");

        for (int i = 1; i < displays.length; i++) {
            if (displays[i].isValid()) {
                secondaryDisplay = displays[i];
                isDualScreen = true;

                DisplayMetrics metrics = new DisplayMetrics();
                secondaryDisplay.getMetrics(metrics);
                Log.e(TAG, "副屏: " + secondaryDisplay.getName() + 
                      ", 分辨率: " + metrics.widthPixels + "x" + metrics.heightPixels);
                break;
            }
        }

        if (isDualScreen && secondaryDisplay != null) {
            presentation = new SecondaryPresentation(this, secondaryDisplay);
            presentation.show();
            Log.e(TAG, "双屏模式已启用");
        } else {
            Log.e(TAG, "仅检测到单屏");
        }
    }

    /**
     * 开启副屏
     */
    private void turnOnSubScreen() {
        Intent intent = new Intent("custom.intent.TURN_ON_SUB_SCREEN");
        intent.setPackage("com.android.settings");
        sendBroadcast(intent);
        Log.e(TAG, "Turn on sub screen");
    }

    /**
     * 关闭副屏
     */
    private void turnOffSubScreen() {
        Intent intent = new Intent("custom.intent.TURN_OFF_SUB_SCREEN");
        intent.setPackage("com.android.settings");
        sendBroadcast(intent);
        Log.e(TAG, "Turn off sub screen");
    }

    /**
     * 更新副屏显示
     */
    private void updateSecondScreen(String sourceText, String targetText, String sourceLang, String targetLang) {
        if (isDualScreen && presentation != null) {
            presentation.updateContent(targetText, sourceLang, targetLang);
            Log.e(TAG, "Update second screen: " + targetText);
        }
    }

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "=== onCreate called ===");
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);

        initViews();
        initManagers();
        setupListeners();
        checkPermissions();

        // 检测并初始化副屏
        detectAndInitSecondaryScreen();

        // 开启副屏
        turnOnSubScreen();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRecordHint = findViewById(R.id.tvRecordHint);
        btnChinese = findViewById(R.id.btnChinese);
        btnIndonesian = findViewById(R.id.btnIndonesian);
        btnSettings = findViewById(R.id.btnSettings);
        rvChat = findViewById(R.id.rvChat);
        audioButtonView = findViewById(R.id.audioButtonView);

        chatAdapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        updateLanguageButtons(Constants.LANG_ZH);
    }

    private void initManagers() {
        webSocketManager = WebSocketManager.getInstance();
        webSocketManager.setCallback(webSocketCallback);
        updateLanguageInManager();

        audioRecorderManager = AudioRecorderManager.getInstance();
        audioRecorderManager.setCallback(audioRecordCallback);

        audioPlayerManager = AudioPlayerManager.getInstance();
        audioPlayerManager.setCallback(audioPlayerCallback);
    }

    private void updateLanguageInManager() {
        String targetLang = Constants.LANG_ZH.equals(currentLang) ? Constants.LANG_ID : Constants.LANG_ZH;
        webSocketManager.setLanguage(currentLang, targetLang);
    }

    private void setupListeners() {
        btnChinese.setOnClickListener(v -> {
            if (!isRecording) {
                currentLang = Constants.LANG_ZH;
                updateLanguageInManager();
                updateLanguageButtons(currentLang);
            }
        });

        btnIndonesian.setOnClickListener(v -> {
            if (!isRecording) {
                currentLang = Constants.LANG_ID;
                updateLanguageInManager();
                updateLanguageButtons(currentLang);
            }
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        audioButtonView.setCallback(new AudioButtonView.AudioButtonCallback() {
            @Override
            public void onRecordingStart() {
                startRecording();
            }

            @Override
            public void onRecordingEnd() {
                stopRecording();
            }
        });

        chatAdapter.setOnItemClickListener(new ChatAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(ChatMessage message) {
                Log.e(TAG, "Message clicked: " + message.getId());
            }

            @Override
            public void onSourceAudioClick(ChatMessage message) {
                byte[] audioData = message.getSourceAudioData();
                if (audioData != null && audioData.length > 0) {
                    audioPlayerManager.playPcm(audioData);
                    Toast.makeText(MainActivity.this, "正在播放原文音频", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "无原文音频数据", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onTargetAudioClick(ChatMessage message) {
                byte[] audioData = message.getTtsAudioData();
                if (audioData != null && audioData.length > 0) {
                    audioPlayerManager.playPcm(audioData);
                    Toast.makeText(MainActivity.this, "正在播放翻译音频", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "无翻译音频数据", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateLanguageButtons(String selectedLang) {
        btnChinese.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
        btnIndonesian.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));

        if (Constants.LANG_ZH.equals(selectedLang)) {
            btnChinese.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        } else if (Constants.LANG_ID.equals(selectedLang)) {
            btnIndonesian.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        }
    }

    private void checkPermissions() {
        Log.i(TAG, "=== Checking permissions ===");
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                Log.i(TAG, "Permission not granted: " + permission);
            }
        }

        Log.i(TAG, "Permission check result - needRequest: " + needRequest);

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        } else {
            Log.i(TAG, "Permissions already granted, connecting WebSocket...");
            connectWebSocket();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                connectWebSocket();
            } else {
                Toast.makeText(this, "需要麦克风和存储权限才能使用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectWebSocket() {
        updateStatus("连接中...", 0xFFFFF3E0, 0xFFE65100);
        webSocketManager.connect();
    }

    private void startRecording() {
        if (audioPlayerManager.isPlaying()) {
            audioPlayerManager.stop();
            Log.i(TAG, "Stopped TTS playback to start recording");
        }

        isRecording = true;
        isProcessing.set(true);

        tvRecordHint.setText("正在录音...");
        btnChinese.setEnabled(false);
        btnIndonesian.setEnabled(false);

        if (!webSocketManager.isConnected()) {
            updateStatus("连接中...", 0xFFFFF3E0, 0xFFE65100);
            webSocketManager.connect();
        }

        synchronized (ttsAudioBuffer) {
            ttsAudioBuffer.reset();
        }

        currentMessage = new ChatMessage();
        currentMessage.setSourceLang(currentLang);
        currentMessage.setTargetLang(Constants.LANG_ZH.equals(currentLang) ? Constants.LANG_ID : Constants.LANG_ZH);
        chatAdapter.addMessage(currentMessage);
        scrollToBottom();

        audioRecorderManager.setLanguage(currentLang);
        audioRecorderManager.startRecording();
    }

    private void stopRecording() {
        if (!isRecording) return;

        isRecording = false;

        mainHandler.post(() -> {
            tvRecordHint.setText("正在识别...");
            btnChinese.setEnabled(true);
            btnIndonesian.setEnabled(true);
        });

        audioRecorderManager.stopRecording();

        webSocketManager.sendFinish();

        translateFinishTimeoutRunnable = () -> {
            Log.e(TAG, "Translate finish timeout after " + Constants.TRANSLATE_FINISH_TIMEOUT_MS + "ms, disconnecting");
            isProcessing.set(false);
            tvRecordHint.setText("翻译超时");
            webSocketManager.disconnect();
        };
        mainHandler.postDelayed(translateFinishTimeoutRunnable, Constants.TRANSLATE_FINISH_TIMEOUT_MS);
    }

    private WebSocketManager.WebSocketCallback webSocketCallback = new WebSocketManager.WebSocketCallback() {
        @Override
        public void onConnected() {
            mainHandler.post(() -> {
                updateStatus("已连接", 0xFFE8F5E9, 0xFF2E7D32);
                Toast.makeText(MainActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onDisconnected() {
            mainHandler.post(() -> updateStatus("连接断开", 0xFFFFEBEE, 0xFFC62828));
        }

        @Override
        public void onMessage(String message) {
            Log.e(TAG, "Text message: " + message);
            if (Constants.RESP_TRANSLATE_FINISH.equals(message)) {
                handleTranslateFinish();
            } else if (Constants.RESP_PROCESSING_COMPLETED.equals(message)) {
                Log.e(TAG, "Processing completed");
            } else if (Constants.RESP_CONNECTION_CLOSING.equals(message) || "close".equals(message)) {
                Log.e(TAG, "Connection closing received");
                mainHandler.post(() -> {
                    tvRecordHint.setText("点击按钮开始说话");
                    webSocketManager.disconnect();
                });
            }
        }

        @Override
        public void onResult(TranslationResult result) {
            Log.e(TAG, "Result: " + result);
            mainHandler.post(() -> handleResult(result));
        }

        @Override
        public void onBinaryData(byte[] data) {
            Log.e(TAG, "Received binary data: " + data.length + " bytes, buffering...");
            synchronized (ttsAudioBuffer) {
                try {
                    ttsAudioBuffer.write(data);
                    Log.e(TAG, "Total buffered: " + ttsAudioBuffer.size() + " bytes");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to buffer audio: " + e.getMessage());
                }
            }
        }

        @Override
        public void onError(String error) {
            mainHandler.post(() -> {
                updateStatus("连接错误: " + error, 0xFFFFEBEE, 0xFFC62828);
                Toast.makeText(MainActivity.this, "连接错误: " + error, Toast.LENGTH_SHORT).show();
                isRecording = false;
                isProcessing.set(false);
                tvRecordHint.setText("连接失败，点击按钮重试");
                btnChinese.setEnabled(true);
                btnIndonesian.setEnabled(true);
                audioButtonView.setRecordingState(false);
            });
        }
    };

    private void handleResult(TranslationResult result) {
        Log.e(TAG, "Handle result: " + result);

        if (result.isText()) {
            if (currentMessage != null) {
                if (result.isSource()) {
                    String existingSource = currentMessage.getSourceText();
                    if (existingSource == null) {
                        currentMessage.setSourceText(result.getText());
                    } else {
                        currentMessage.setSourceText(existingSource + result.getText());
                    }
                } else if (result.isTarget()) {
                    String existingTarget = currentMessage.getTargetText();
                    if (existingTarget == null) {
                        currentMessage.setTargetText(result.getText());
                    } else {
                        currentMessage.setTargetText(existingTarget + result.getText());
                    }
                    // 实时更新副屏显示目标文本
                    updateSecondScreen(currentMessage.getSourceText(),
                            currentMessage.getTargetText(),
                            currentMessage.getSourceLang(),
                            currentMessage.getTargetLang());
                } else if (result.isTtsFinish()) {
                    Log.e(TAG, "TTS finish received");
                }
                chatAdapter.updateLastMessage(currentMessage);
                scrollToBottom();
            }
        } else if (result.isError()) {
            if (currentMessage != null) {
                currentMessage.setError(true);
                chatAdapter.updateLastMessage(currentMessage);
            }
            String errorMsg = "错误: " + (result.getMessage() != null ? result.getMessage() : "未知错误");
            if (result.getCode() != null) {
                errorMsg = "错误(" + result.getCode() + "): " + result.getMessage();
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            isProcessing.set(false);
        } else {
            Log.e(TAG, "Other message: " + result.getText());
        }
    }

    private void handleTranslateFinish() {
        Log.e(TAG, "Translate finish received");

        if (translateFinishTimeoutRunnable != null) {
            mainHandler.removeCallbacks(translateFinishTimeoutRunnable);
            translateFinishTimeoutRunnable = null;
        }

        tvRecordHint.setText("翻译完成");
        Toast.makeText(this, "翻译完成", Toast.LENGTH_SHORT).show();

        // 更新副屏显示
        if (currentMessage != null) {
            String sourceLang = currentMessage.getSourceLang();
            String targetLang = currentMessage.getTargetLang();
            String targetText = currentMessage.getTargetText() != null ? currentMessage.getTargetText() : "";
            updateSecondScreen(currentMessage.getSourceText(), targetText, sourceLang, targetLang);
        }

        synchronized (ttsAudioBuffer) {
            byte[] audioData = ttsAudioBuffer.toByteArray();
            if (audioData.length > 0) {
                if (currentMessage != null) {
                    currentMessage.setTtsAudioData(audioData);
                    chatAdapter.updateLastMessage(currentMessage);
                }

                saveTtsAudioToFile(audioData);

                // 根据设置判断是否自动播放
                boolean autoPlay = prefs.getBoolean(SettingsActivity.KEY_AUTO_PLAY, true);
                if (autoPlay) {
                    Log.e(TAG, "Playing TTS audio: " + audioData.length + " bytes");
                    audioPlayerManager.playPcm(audioData);
                } else {
                    Log.e(TAG, "Auto-play disabled, TTS audio saved but not played");
                }
            } else {
                Log.e(TAG, "No TTS audio data to play");
            }
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            webSocketManager.sendClose();
            isProcessing.set(false);
            currentMessage = null;
            audioButtonView.setRecordingState(false);
            tvRecordHint.setText("点击按钮开始说话");
        }, 500);
    }

    /**
     * 保存TTS音频到文件
     */
    private void saveTtsAudioToFile(byte[] audioData) {
        try {
            android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC).mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String targetLang = Constants.LANG_ZH.equals(currentLang) ? Constants.LANG_ID : Constants.LANG_ZH;
            String fileName = "TTS_" + currentLang + "_to_" + targetLang + "_" + timeStamp + ".pcm";
            File file = new File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                    fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(audioData);
            fos.close();

            Log.e(TAG, "TTS audio saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving TTS audio: " + e.getMessage());
        }
    }

    private AudioRecorderManager.AudioRecordCallback audioRecordCallback = new AudioRecorderManager.AudioRecordCallback() {
        @Override
        public void onRecordingStart() {
            Log.e(TAG, "Recording started");
        }

        @Override
        public void onRecordingData(byte[] data, int size) {
            if (webSocketManager.isConnected()) {
                webSocketManager.sendAudioData(data);
            }
        }

        @Override
        public void onRecordingStop(byte[] fullData) {
            Log.e(TAG, "Recording stopped, total: " + (fullData != null ? fullData.length : 0) + " bytes");
            if (currentMessage != null && fullData != null && fullData.length > 0) {
                currentMessage.setSourceAudioData(fullData);
                chatAdapter.updateLastMessage(currentMessage);
            }
        }

        @Override
        public void onRecordingError(String error) {
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "录音错误: " + error, Toast.LENGTH_SHORT).show();
                isRecording = false;
                isProcessing.set(false);
                tvRecordHint.setText("点击按钮开始说话");
                btnChinese.setEnabled(true);
                btnIndonesian.setEnabled(true);
            });
        }
    };

    private AudioPlayerManager.AudioPlayerCallback audioPlayerCallback = new AudioPlayerManager.AudioPlayerCallback() {
        @Override
        public void onPlaybackStart() {
            Log.e(TAG, "Playback started");
        }

        @Override
        public void onPlaybackComplete() {
            Log.e(TAG, "Playback completed");
        }

        @Override
        public void onPlaybackError(String error) {
            Log.e(TAG, "Playback error: " + error);
        }
    };

    private void updateStatus(String text, int bgColor, int textColor) {
        tvStatus.setText(text);
        tvStatus.setBackgroundColor(bgColor);
        tvStatus.setTextColor(textColor);
    }

    private void scrollToBottom() {
        if (chatAdapter.getItemCount() > 0) {
            rvChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }
}