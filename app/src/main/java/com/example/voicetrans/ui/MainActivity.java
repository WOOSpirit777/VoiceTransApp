package com.example.voicetrans.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.voicetrans.R;
import com.example.voicetrans.audio.AudioPlayerManager;
import com.example.voicetrans.audio.AudioRecorderManager;
import com.example.voicetrans.model.ChatMessage;
import com.example.voicetrans.model.TranslationResult;
import com.example.voicetrans.util.Constants;
import com.example.voicetrans.websocket.WebSocketManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 主界面 - 语音翻译功能
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 1001;

    // UI组件
    private TextView tvStatus;
    private TextView tvRecordHint;
    private Button btnChinese;
    private Button btnEnglish;
    private RecyclerView rvChat;
    private AudioButtonView audioButtonView;

    // 适配器和消息列表
    private ChatAdapter chatAdapter;

    // 管理器
    private WebSocketManager webSocketManager;
    private AudioRecorderManager audioRecorderManager;
    private AudioPlayerManager audioPlayerManager;

    // 状态
    private String currentLang = Constants.LANG_ZH_CN;
    private boolean isRecording = false;
    private ChatMessage currentMessage = null;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "=== onCreate called ===");
        setContentView(R.layout.activity_main);

        initViews();
        initManagers();
        setupListeners();
        checkPermissions();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRecordHint = findViewById(R.id.tvRecordHint);
        btnChinese = findViewById(R.id.btnChinese);
        btnEnglish = findViewById(R.id.btnEnglish);
        rvChat = findViewById(R.id.rvChat);
        audioButtonView = findViewById(R.id.audioButtonView);

        // 设置RecyclerView
        chatAdapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(chatAdapter);

        // 默认选中中文
        updateLanguageButtons(Constants.LANG_ZH_CN);
    }

    private void initManagers() {
        // WebSocket管理器
        webSocketManager = WebSocketManager.getInstance();
        webSocketManager.setCallback(webSocketCallback);
        webSocketManager.setLanguage(currentLang);

        // 音频录制管理器
        audioRecorderManager = AudioRecorderManager.getInstance();
        audioRecorderManager.setCallback(audioRecordCallback);

        // 音频播放管理器
        audioPlayerManager = AudioPlayerManager.getInstance();
        audioPlayerManager.setCallback(audioPlayerCallback);
    }

    private void setupListeners() {
        // 中文按钮
        btnChinese.setOnClickListener(v -> {
            if (!isRecording) {
                currentLang = Constants.LANG_ZH_CN;
                webSocketManager.setLanguage(currentLang);
                updateLanguageButtons(currentLang);
            }
        });

        // 英文按钮
        btnEnglish.setOnClickListener(v -> {
            if (!isRecording) {
                currentLang = Constants.LANG_EN_US;
                webSocketManager.setLanguage(currentLang);
                updateLanguageButtons(currentLang);
            }
        });

        // 录音按钮
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

        // 消息点击事件
        chatAdapter.setOnItemClickListener(message -> {
            // 可以播放音频等操作
            Log.d(TAG, "Message clicked: " + message.getId());
        });
    }

    private void updateLanguageButtons(String selectedLang) {
        if (Constants.LANG_ZH_CN.equals(selectedLang)) {
            btnChinese.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
            btnEnglish.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
        } else {
            btnChinese.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.gray));
            btnEnglish.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
        }
    }

    private void checkPermissions() {
        Log.i(TAG, "=== Checking permissions ===");
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
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
                Toast.makeText(this, "需要麦克风权限才能录音", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void connectWebSocket() {
        updateStatus("连接中...", 0xFFFFF3E0, 0xFFE65100);
//        webSocketManager.connect();
    }

    private void startRecording() {
//        if (isProcessing.get()) {
//            Toast.makeText(this, "正在处理上一次请求，请稍候", Toast.LENGTH_SHORT).show();
//            return;
//        }

        isRecording = true;
        isProcessing.set(true);

        // 更新UI
        tvRecordHint.setText("正在录音...");
        btnChinese.setEnabled(false);
        btnEnglish.setEnabled(false);

        // 如果未连接，先连接
        if (!webSocketManager.isConnected()) {
            updateStatus("连接中...", 0xFFFFF3E0, 0xFFE65100);
            webSocketManager.connect();
        }

        // 创建新消息
        currentMessage = new ChatMessage();
        currentMessage.setSourceLang(currentLang);
        currentMessage.setTargetLang("zh_CN".equals(currentLang) ? "en_US" : "zh_CN");
        chatAdapter.addMessage(currentMessage);
        scrollToBottom();

        // 开始录制
        audioRecorderManager.startRecording();
    }

    private void stopRecording() {
        if (!isRecording) return;

        isRecording = false;

        // 更新UI
        mainHandler.post(() -> {
            tvRecordHint.setText("正在识别...");
            btnChinese.setEnabled(true);
            btnEnglish.setEnabled(true);
        });

        // 停止录制
        audioRecorderManager.stopRecording();

        // 发送finish命令
        webSocketManager.sendFinish();
    }

    // WebSocket回调
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
            Log.d(TAG, "Text message: " + message);
            // 处理非JSON格式的完成消息
            if ("translate finish".equals(message)) {
                handleTranslateFinish();
            }
        }

        @Override
        public void onResult(TranslationResult result) {
            Log.d(TAG, "Result: " + result);
            mainHandler.post(() -> handleResult(result));
        }

        @Override
        public void onError(String error) {
            mainHandler.post(() -> {
                updateStatus("错误: " + error, 0xFFFFEBEE, 0xFFC62828);
                Toast.makeText(MainActivity.this, "错误: " + error, Toast.LENGTH_SHORT).show();
                isProcessing.set(false);
            });
        }
    };

    private void handleResult(TranslationResult result) {
        Log.d(TAG, "Handle result: " + result);

        if (result.isResult()) {
            if (currentMessage != null) {
                if (result.isSource()) {
                    // 源语言结果 - 累积拼接
                    String existingSource = currentMessage.getSourceText();
                    if (existingSource == null) {
                        currentMessage.setSourceText(result.getText());
                    } else {
                        currentMessage.setSourceText(existingSource + result.getText());
                    }
                } else if (result.isTarget()) {
                    // 目标语言结果 - 累积拼接
                    String existingTarget = currentMessage.getTargetText();
                    if (existingTarget == null) {
                        currentMessage.setTargetText(result.getText());
                    } else {
                        currentMessage.setTargetText(existingTarget + result.getText());
                    }
                }
                chatAdapter.updateLastMessage(currentMessage);
                scrollToBottom();
            }
        } else if ("translate finish".equals(result.getText())) {
            // 翻译完成
            handleTranslateFinish();
        } else if (result.isError()) {
            // 错误
            if (currentMessage != null) {
                currentMessage.setError(true);
                chatAdapter.updateLastMessage(currentMessage);
            }
            Toast.makeText(this, "错误: " + result.getMessage(), Toast.LENGTH_SHORT).show();
            isProcessing.set(false);
        } else {
            // 其他消息
            Log.d(TAG, "Other message: " + result.getText());
        }
    }

    /**
     * 处理翻译完成
     */
    private void handleTranslateFinish() {
        Log.d(TAG, "Translate finish received");
        tvRecordHint.setText("翻译完成");
        Toast.makeText(this, "翻译完成", Toast.LENGTH_SHORT).show();

        // 发送close关闭连接
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            webSocketManager.sendClose();
            isProcessing.set(false);
            currentMessage = null;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tvRecordHint.setText("长按按钮开始录音");
                webSocketManager.disconnect();
            }, 1000);
        }, 500);
    }

    // 音频录制回调
    private AudioRecorderManager.AudioRecordCallback audioRecordCallback = new AudioRecorderManager.AudioRecordCallback() {
        @Override
        public void onRecordingStart() {
            Log.d(TAG, "Recording started");
        }

        @Override
        public void onRecordingData(byte[] data, int size) {
            // 实时发送音频数据
            if (webSocketManager.isConnected()) {
                webSocketManager.sendAudioData(data);
            }
        }

        @Override
        public void onRecordingStop(byte[] fullData) {
            Log.d(TAG, "Recording stopped, total: " + (fullData != null ? fullData.length : 0) + " bytes");
        }

        @Override
        public void onRecordingError(String error) {
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "录音错误: " + error, Toast.LENGTH_SHORT).show();
                isRecording = false;
                isProcessing.set(false);
                tvRecordHint.setText("长按按钮开始录音");
                btnChinese.setEnabled(true);
                btnEnglish.setEnabled(true);
            });
        }
    };

    // 音频播放回调
    private AudioPlayerManager.AudioPlayerCallback audioPlayerCallback = new AudioPlayerManager.AudioPlayerCallback() {
        @Override
        public void onPlaybackStart() {
            Log.d(TAG, "Playback started");
        }

        @Override
        public void onPlaybackComplete() {
            Log.d(TAG, "Playback completed");
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
}
