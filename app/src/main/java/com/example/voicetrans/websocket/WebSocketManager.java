package com.example.voicetrans.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.voicetrans.model.TranslationResult;
import com.example.voicetrans.util.Constants;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket管理器 - 处理与服务器的WebSocket通信
 */
public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private WebSocket webSocket;
    private OkHttpClient client;
    private Gson gson;

    private String currentLang = Constants.LANG_ZH_CN;
    private boolean isConnected = false;

    // 回调接口
    private WebSocketCallback callback;
    private Handler mainHandler;

    public interface WebSocketCallback {
        void onConnected();
        void onDisconnected();
        void onMessage(String message);
        void onResult(TranslationResult result);
        void onError(String error);
    }

    private WebSocketManager() {
        gson = new Gson();
        mainHandler = new Handler(Looper.getMainLooper());

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    public void setCallback(WebSocketCallback callback) {
        this.callback = callback;
    }

    public void setLanguage(String lang) {
        this.currentLang = lang;
    }

    public String getLanguage() {
        return currentLang;
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 连接WebSocket服务器
     */
    public void connect() {
        Log.i(TAG, ">>> WebSocket connect() called");
        if (isConnected && webSocket != null) {
            Log.d(TAG, "WebSocket already connected");
            return;
        }

        Log.i(TAG, "Connecting to: " + Constants.WS_URL);
        Log.i(TAG, "Language: " + currentLang);

        // 生成sessionId
        String sessionId = UUID.randomUUID().toString();
        Log.i(TAG, "SessionId: " + sessionId);

        // 构建请求，使用标准WebSocket头
        Request request = new Request.Builder()
                .url(Constants.WS_URL)
                .addHeader("Authorization", Constants.ACCESS_TOKEN)
                .addHeader("lang", currentLang)
                .addHeader("sessionId", sessionId)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "WebSocket connected successfully!");
                Log.d(TAG, "Response code: " + response.code());
                isConnected = true;

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onConnected();
                    }
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.e(TAG, "<<< Received text message: " + text);
                handleTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.i(TAG, "<<< Received binary message, length: " + bytes.size());
                // 打印二进制数据的前64字节
                StringBuilder hexBuilder = new StringBuilder();
                int printLen = Math.min(bytes.size(), 64);
                for (int i = 0; i < printLen; i++) {
                    hexBuilder.append(String.format("%02X ", bytes.getByte(i)));
                }
                Log.i(TAG, "Binary data (first " + printLen + " bytes): " + hexBuilder.toString());
                
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onMessage("Binary data: " + bytes.size() + " bytes");
                    }
                });
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                isConnected = false;

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onDisconnected();
                    }
                });
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure: " + t.getMessage());
                Log.e(TAG, "Failure type: " + t.getClass().getSimpleName());
                
                if (response != null) {
                    Log.e(TAG, "Response code: " + response.code());
                    try {
                        Log.e(TAG, "Response body: " + response.body().string());
                    } catch (Exception e) {
                        Log.e(TAG, "Could not read response body");
                    }
                }
                
                isConnected = false;

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(t.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 处理文本消息
     */
    private void handleTextMessage(String text) {
        Log.e(TAG, "tex2112t111："+text);
        mainHandler.post(() -> {
            // 尝试解析为JSON结果
            try {
                TranslationResult result = gson.fromJson(text, TranslationResult.class);
                Log.e(TAG, "text111："+text);
                if (result != null && result.getType() != null) {
                    Log.e(TAG, "Parsed result: " + result);
                    if (callback != null) {
                        Log.e(TAG, "text222："+text);
                        callback.onResult(result);
                    }
                    return;
                }else{
                    Log.e(TAG, "text："+text);
                }
            } catch (Exception e) {
                Log.e(TAG, "tex2t："+text);
                Log.e(TAG, "Not a JSON result, treating as text message");
            }
            Log.e(TAG, "text333："+text);
            // 普通文本消息
            if (callback != null) {
                Log.e(TAG, "text333："+text);
                callback.onMessage(text);
            }
        });
    }

    /**
     * 发送二进制音频数据
     */
    public void sendAudioData(byte[] data) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send audio");
            return;
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected (isConnected=false), cannot send audio. Queue size=" + (webSocket.queueSize()));
            return;
        }

        // 打印发送的数据信息
//        Log.i(TAG, ">>> Sending audio data: " + data.length + " bytes");
        StringBuilder hexBuilder = new StringBuilder();
        int printLen = Math.min(data.length, 64);
        for (int i = 0; i < printLen; i++) {
            hexBuilder.append(String.format("%02X ", data[i]));
        }
        Log.w(TAG, "Audio data (first " + printLen + " bytes): " + hexBuilder.toString());

        boolean sent = webSocket.send(ByteString.of(data));
        if (sent) {
            Log.i(TAG, "Audio data sent successfully, queue size=" + webSocket.queueSize());
        } else {
            Log.e(TAG, "Failed to send audio data, queue size=" + webSocket.queueSize());
        }
    }

    /**
     * 发送文本命令
     */
    public void sendCommand(String command) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send command");
            return;
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected, cannot send command: " + command);
            return;
        }

        Log.e(TAG, ">>> Sending command: " + command);
        boolean sent = webSocket.send(command);
        if (sent) {
            Log.i(TAG, "Command sent successfully: " + command);
        } else {
            Log.e(TAG, "Failed to send command: " + command + ", queue size=" + webSocket.queueSize());
        }
    }

    /**
     * 发送finish命令 - 通知服务器音频传输完成
     */
    public void sendFinish() {
        sendCommand(Constants.CMD_FINISH);
    }

    /**
     * 发送close命令 - 请求关闭连接
     */
    public void sendClose() {
        sendCommand(Constants.CMD_CLOSE);
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (webSocket != null) {
            isConnected = false;
            webSocket.close(1000, "User disconnect");
//            webSocket = null;
        }
    }

    /**
     * 重新连接
     */
    public void reconnect() {
        disconnect();
        connect();
    }
}
