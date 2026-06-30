package com.aibabel.voicetrans.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aibabel.voicetrans.util.Constants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * PCM音频录制管理器
 * 支持实时分块录制和发送
 */
public class AudioRecorderManager {

    private static final String TAG = "AudioRecorderManager";

    private static AudioRecorderManager instance;

    private AudioRecord audioRecord;
    private int bufferSize;
    private boolean isRecording = false;
    private Thread recordingThread;

    private AudioRecordCallback callback;
    private Handler mainHandler;

    // 录制的音频数据（用于完整保存）
    private ByteArrayOutputStream recordedData;

    // PCM文件保存
    private File pcmFile;
    private FileOutputStream pcmFileOutputStream;
    private String currentLang = "zh";

    public interface AudioRecordCallback {
        void onRecordingStart();
        void onRecordingData(byte[] data, int size);
        void onRecordingStop(byte[] fullData);
        void onRecordingError(String error);
    }

    private AudioRecorderManager() {
        mainHandler = new Handler(Looper.getMainLooper());

        // 计算最小缓冲区大小
        int minBufferSize = AudioRecord.getMinBufferSize(
                Constants.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        // 缓冲区大小设置为能容纳多个chunk
        bufferSize = Math.max(minBufferSize, Constants.CHUNK_SIZE * 4);
        Log.e(TAG, "Buffer size: " + bufferSize);
    }

    public static synchronized AudioRecorderManager getInstance() {
        if (instance == null) {
            instance = new AudioRecorderManager();
        }
        return instance;
    }

    public void setCallback(AudioRecordCallback callback) {
        this.callback = callback;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void setLanguage(String lang) {
        this.currentLang = lang;
    }

    /**
     * 保存录音数据到文件
     */
    private void saveToFile(byte[] data, boolean append) {
        if (pcmFile == null || pcmFileOutputStream == null) {
            return;
        }
        try {
            if (append) {
                pcmFileOutputStream.write(data);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file: " + e.getMessage());
        }
    }

    /**
     * 开始录制
     */
    public void startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return;
        }

        try {
            // 创建PCM文件
            File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = "VoiceTrans_" + currentLang + "_" + timeStamp + ".pcm";
            pcmFile = new File(storageDir, fileName);

            pcmFileOutputStream = new FileOutputStream(pcmFile);
            Log.e(TAG, "PCM file created: " + pcmFile.getAbsolutePath());

            // 创建AudioRecord
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    Constants.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                if (callback != null) {
                    mainHandler.post(() -> callback.onRecordingError("麦克风初始化失败"));
                }
                return;
            }

            // 开始录制
            audioRecord.startRecording();
            isRecording = true;
            recordedData = new ByteArrayOutputStream();

            Log.e(TAG, "Recording started");

            if (callback != null) {
                mainHandler.post(callback::onRecordingStart);
            }

            // 启动录制线程
            recordingThread = new Thread(this::recordingLoop);
            recordingThread.start();

        } catch (Exception e) {
            Log.e(TAG, "Start recording error: " + e.getMessage());
            if (callback != null) {
                mainHandler.post(() -> callback.onRecordingError(e.getMessage()));
            }
        }
    }

    /**
     * 录制循环
     */
    private void recordingLoop() {
        byte[] buffer = new byte[Constants.CHUNK_SIZE];

        while (isRecording) {
            int bytesRead = audioRecord.read(buffer, 0, Constants.CHUNK_SIZE);

            if (bytesRead > 0) {
                // 复制有效数据
                byte[] validData = Arrays.copyOf(buffer, bytesRead);

                // 保存到PCM文件
                saveToFile(validData, true);

                // 保存完整数据
                if (recordedData != null) {
                    recordedData.write(validData, 0, bytesRead);
                }

                // 回调发送数据
                if (callback != null) {
                    final int size = bytesRead;
                    mainHandler.post(() -> callback.onRecordingData(validData, size));
                }
            } else if (bytesRead < 0) {
                Log.e(TAG, "Recording error: " + bytesRead);
                break;
            }
        }
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        try {
            if (recordingThread != null) {
                recordingThread.join(500);
                recordingThread = null;
            }

            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }

            // 关闭PCM文件
            if (pcmFileOutputStream != null) {
                pcmFileOutputStream.close();
                pcmFileOutputStream = null;
                Log.e(TAG, "PCM file saved: " + pcmFile.getAbsolutePath());
            }

            Log.e(TAG, "Recording stopped");

            // 获取完整数据
            byte[] fullData = null;
            if (recordedData != null) {
                fullData = recordedData.toByteArray();
                Log.e(TAG, "Total recorded: " + fullData.length + " bytes");
            }

            if (callback != null) {
                final byte[] data = fullData;
                mainHandler.post(() -> callback.onRecordingStop(data));
            }

        } catch (Exception e) {
            Log.e(TAG, "Stop recording error: " + e.getMessage());
            if (callback != null) {
                mainHandler.post(() -> callback.onRecordingError(e.getMessage()));
            }
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stopRecording();
        instance = null;
    }
}
