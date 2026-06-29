package com.example.voicetrans.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.voicetrans.util.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 音频播放管理器
 * 支持PCM和WAV格式音频播放
 */
public class AudioPlayerManager {

    private static final String TAG = "AudioPlayerManager";

    private static AudioPlayerManager instance;

    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    private ExecutorService executor;
    private Handler mainHandler;

    private AudioPlayerCallback callback;

    public interface AudioPlayerCallback {
        void onPlaybackStart();
        void onPlaybackComplete();
        void onPlaybackError(String error);
    }

    private AudioPlayerManager() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized AudioPlayerManager getInstance() {
        if (instance == null) {
            instance = new AudioPlayerManager();
        }
        return instance;
    }

    public void setCallback(AudioPlayerCallback callback) {
        this.callback = callback;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 播放PCM数据
     */
    public void playPcm(byte[] pcmData) {
        if (isPlaying) {
            stop();
        }

        executor.execute(() -> {
            try {
                int bufferSize = AudioTrack.getMinBufferSize(
                        Constants.SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                );

                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(Constants.SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                audioTrack = track;
                audioTrack.play();
                isPlaying = true;

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPlaybackStart();
                    }
                });

                // 分块播放
                int offset = 0;
                while (isPlaying && offset < pcmData.length) {
                    int length = Math.min(bufferSize, pcmData.length - offset);
                    audioTrack.write(pcmData, offset, length);
                    offset += length;
                }

                // 等待播放完成
                Thread.sleep(100);

                mainHandler.post(() -> {
                    isPlaying = false;
                    if (callback != null) {
                        callback.onPlaybackComplete();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Play error: " + e.getMessage());
                mainHandler.post(() -> {
                    isPlaying = false;
                    if (callback != null) {
                        callback.onPlaybackError(e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 从URL播放音频
     */
    public void playFromUrl(String url) {
        if (isPlaying) {
            stop();
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            ByteArrayOutputStream outputStream = null;

            try {
                URL audioUrl = new URL(url);
                connection = (HttpURLConnection) audioUrl.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onPlaybackError("HTTP " + responseCode);
                        }
                    });
                    return;
                }

                // 检测是否为WAV格式
                inputStream = connection.getInputStream();
                byte[] header = new byte[4];
                inputStream.read(header);

                // 判断是否为WAV (RIFF....WAVE)
                boolean isWav = header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F';

                if (isWav) {
                    // 跳过WAV头部（44字节），读取PCM数据
                    byte[] skip = new byte[40];
                    inputStream.read(skip);
                } else {
                    // 不是WAV，从头开始
                    inputStream.close();
                    inputStream = connection.getInputStream();
                }

                // 读取音频数据
                outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                byte[] audioData = outputStream.toByteArray();
                playPcm(audioData);

            } catch (Exception e) {
                Log.e(TAG, "Play from URL error: " + e.getMessage());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPlaybackError(e.getMessage());
                    }
                });
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                    if (outputStream != null) outputStream.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    // ignore
                }
            }
        });
    }

    /**
     * 停止播放
     */
    public void stop() {
        isPlaying = false;
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Stop error: " + e.getMessage());
            }
            audioTrack = null;
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        instance = null;
    }
}
