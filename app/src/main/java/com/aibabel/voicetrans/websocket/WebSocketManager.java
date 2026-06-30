package com.aibabel.voicetrans.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aibabel.voicetrans.model.TranslationResult;
import com.aibabel.voicetrans.util.Constants;
import com.google.gson.Gson;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private WebSocket webSocket;
    private OkHttpClient client;
    private Gson gson;

    private String langSource = Constants.LANG_ZH;
    private String langTarget = Constants.LANG_EN;
    private boolean isConnected = false;

    private WebSocketCallback callback;
    private Handler mainHandler;

    public interface WebSocketCallback {
        void onConnected();
        void onDisconnected();
        void onMessage(String message);
        void onResult(TranslationResult result);
        void onBinaryData(byte[] data);
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

    public void setLanguage(String sourceLang, String targetLang) {
        this.langSource = sourceLang;
        this.langTarget = targetLang;
    }

    public String getLangSource() {
        return langSource;
    }

    public String getLangTarget() {
        return langTarget;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connect() {
        Log.i(TAG, ">>> WebSocket connect() called");
        if (isConnected && webSocket != null) {
            Log.e(TAG, "WebSocket already connected");
            return;
        }

        Log.i(TAG, "Connecting to: " + Constants.WS_URL);
        Log.i(TAG, "langSource: " + langSource + ", langTarget: " + langTarget);

        String sessionId = UUID.randomUUID().toString();
        Log.i(TAG, "SessionId: " + sessionId);

        Request request = new Request.Builder()
                .url(Constants.WS_URL)
                .addHeader("Authorization", Constants.ACCESS_TOKEN)
                .addHeader("langSource", langSource)
                .addHeader("langTarget", langTarget)
                .addHeader("sessionId", sessionId)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.e(TAG, "WebSocket connected successfully!");
                Log.e(TAG, "Response code: " + response.code());
                isConnected = true;

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onConnected();
                    }
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.i(TAG, "<<< Received text message: " + text);
                handleTextMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                Log.i(TAG, "<<< Received binary message, length: " + bytes.size());
                
                byte[] data = bytes.toByteArray();
                StringBuilder hexBuilder = new StringBuilder();
                int printLen = Math.min(data.length, 64);
                for (int i = 0; i < printLen; i++) {
                    hexBuilder.append(String.format("%02X ", data[i]));
                }
                Log.i(TAG, "Binary data (first " + printLen + " bytes): " + hexBuilder.toString());
                
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onBinaryData(data);
                    }
                });
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                Log.e(TAG, "WebSocket closing: " + code + " " + reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.e(TAG, "WebSocket closed: " + code + " " + reason);
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

    private void handleTextMessage(String text) {
        mainHandler.post(() -> {
            try {
                TranslationResult result = gson.fromJson(text, TranslationResult.class);
                if (result != null && result.getType() != null) {
                    Log.e(TAG, "Parsed result: " + result);
                    if (callback != null) {
                        callback.onResult(result);
                    }
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Not a JSON result, treating as text message");
            }
            
            if (callback != null) {
                callback.onMessage(text);
            }
        });
    }

    public void sendAudioData(byte[] data) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send audio");
            return;
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected (isConnected=false), cannot send audio. Queue size=" + (webSocket.queueSize()));
            return;
        }

        Log.i(TAG, ">>> Sending audio data: " + data.length + " bytes");
        boolean sent = webSocket.send(ByteString.of(data));
        if (sent) {
            Log.i(TAG, "Audio data sent successfully, queue size=" + webSocket.queueSize());
        } else {
            Log.e(TAG, "Failed to send audio data, queue size=" + webSocket.queueSize());
        }
    }

    public void sendCommand(String command) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send command");
            return;
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected, cannot send command: " + command);
            return;
        }

        Log.i(TAG, ">>> Sending command: " + command);
        boolean sent = webSocket.send(command);
        if (sent) {
            Log.i(TAG, "Command sent successfully: " + command);
        } else {
            Log.e(TAG, "Failed to send command: " + command + ", queue size=" + webSocket.queueSize());
        }
    }

    public void sendFinish() {
        sendCommand(Constants.CMD_FINISH);
    }

    public void sendClose() {
        sendCommand(Constants.CMD_CLOSE);
    }

    public void disconnect() {
        if (webSocket != null) {
            isConnected = false;
            webSocket.close(1000, "User disconnect");
        }
    }

    public void reconnect() {
        disconnect();
        connect();
    }
}