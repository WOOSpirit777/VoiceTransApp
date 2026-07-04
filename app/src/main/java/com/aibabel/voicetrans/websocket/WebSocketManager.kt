package com.aibabel.voicetrans.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.voicetrans.model.TranslationResult
import com.aibabel.voicetrans.ui.MainActivity
import com.aibabel.voicetrans.util.Constants
import com.aibabel.voicetrans.util.Constants.WS_URL_CN
import com.aibabel.voicetrans.util.Constants.WS_URL_COM
import com.google.gson.Gson
import commonutil.ContentProviderUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import java.util.UUID
import java.util.concurrent.TimeUnit

class WebSocketManager private constructor() {

    interface WebSocketCallback {
        fun onConnected()
        fun onDisconnected()
        fun onMessage(message: String)
        fun onResult(result: TranslationResult)
        fun onBinaryData(data: ByteArray)
        fun onError(error: String)
        fun onReconnected() {}
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient
    private var gson: Gson
    private var langSource: String = Constants.LANG_ZH
    private var langTarget: String = Constants.LANG_ID
    private var mode: String = Constants.MODE_S2S
    @Volatile
    private var isConnected = false
    private var isManualDisconnect = false
    private var hasConnectedBefore = false
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var callback: WebSocketCallback? = null
    private var mainHandler: Handler
    private var currentSessionId: String = ""

    init {
        gson = Gson()
        mainHandler = Handler(Looper.getMainLooper())

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.i(TAG, "OkHttp: $message")
        }
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        client = OkHttpClient.Builder()
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .connectTimeout(300, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun setCallback(callback: WebSocketCallback?) {
        this.callback = callback
    }

    fun setLanguage(sourceLang: String, targetLang: String) {
        langSource = sourceLang
        langTarget = targetLang
    }

    fun setMode(newMode: String) {
        mode = newMode
    }

    fun getLangSource(): String = langSource

    fun getLangTarget(): String = langTarget

    fun isConnected(): Boolean = isConnected

    fun getSessionId(): String = currentSessionId

    fun connect() {
        Log.i(TAG, ">>> WebSocket connect() called")
        if (isConnected && webSocket != null) {
            Log.e(TAG, "WebSocket already connected")
            return
        }

        isManualDisconnect = false
        Log.i(TAG, "Connecting to: " +if (ContentProviderUtil.isInCN()) WS_URL_CN else WS_URL_COM)
        Log.i(TAG, "langSource: $langSource, langTarget: $langTarget")

        val sessionId = UUID.randomUUID().toString()
        this.currentSessionId = sessionId
        Log.i(TAG, "SessionId: $sessionId")

        val request = Request.Builder()
            .url(if (ContentProviderUtil.isInCN()) WS_URL_CN else WS_URL_COM)
            .addHeader("Authorization", Constants.ACCESS_TOKEN)
            .addHeader("langSource", langSource)
            .addHeader("langTarget", langTarget)
            .addHeader("sessionId", sessionId)
            .addHeader("mode", mode)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.e(TAG, "WebSocket connected successfully!")
                Log.e(TAG, "Response code: " + response.code)
                isConnected = true
                reconnectAttempts = 0
//                sendConnect()
                mainHandler.post {
                    if (hasConnectedBefore) {
                        Log.e(TAG, "WebSocket reconnected, notifying callback")
                        callback?.onReconnected()
                    } else {
                        hasConnectedBefore = true
                        callback?.onConnected()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "<<< Received text message: $text")
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.i(TAG, "<<< Received binary message, length: " + bytes.size)

                val data = bytes.toByteArray()
                val hexBuilder = StringBuilder()
                val printLen = minOf(data.size, 64)
                for (i in 0 until printLen) {
                    hexBuilder.append(String.format("%02X ", data[i]))
                }
                Log.i(TAG, "Binary data (first $printLen bytes): ${hexBuilder.toString()}")

                // 直接在OkHttp回调线程处理，避免大量二进制数据通过mainHandler.post
                // 阻塞主线程消息队列，导致文本消息（如"translate finish"）延迟处理
                // onBinaryData中的操作（ttsAudioBuffer同步写、feedAudioData入队、scope.launch存库）均为线程安全
                callback?.onBinaryData(data)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.e(TAG, "WebSocket closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.e(TAG, "WebSocket closed: $code $reason")
                isConnected = false

                mainHandler.post {
                    callback?.onDisconnected()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: " + t.message)
                Log.e(TAG, "WebSocket failure cause: " + t.cause?.message)
                Log.e(TAG, "Failure type: " + t.javaClass.simpleName)

                if (response != null) {
                    Log.e(TAG, "Response code: " + response.code)
                    try {
                        Log.e(TAG, "Response body: " + response.body!!.string())
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not read response body")
                    }
                }

                isConnected = false
                reconnectAttempts++
               /* if (reconnectAttempts <= 3) {
                    reconnect()
                } else {
                    mainHandler.post {
                        callback?.onError(t.message ?: "Unknown error")
                    }
                }*/
                mainHandler.post {
                    callback?.onError(t.message ?: "Unknown error")
                }
            }
        })
    }

    private fun handleTextMessage(text: String) {
        mainHandler.post {
            try {
                val result = gson.fromJson(text, TranslationResult::class.java)
                if (result != null && result.type != null) {
                    Log.e(TAG, "Parsed result: $result")
                    callback?.onResult(result)
                    return@post
                }
            } catch (e: Exception) {
                Log.e(TAG, "Not a JSON result, treating as text message")
            }

            callback?.onMessage(text)
        }
    }

    fun sendAudioData(data: ByteArray): Boolean {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send audio")
            return false
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected (isConnected=false), cannot send audio. Queue size=" + (webSocket!!.queueSize()))
            return false
        }
        val hexBuilder = StringBuilder()
        val printLen = minOf(data.size, 64)
        for (i in 0 until printLen) {
            hexBuilder.append(String.format("%02X ", data[i]))
        }
        Log.i(TAG2, ">>> Sending audio data: (first $printLen bytes): ${hexBuilder.toString()}")
        val sent = webSocket!!.send(ByteString.of(*data))
        if (sent) {
            Log.i(TAG2, "Audio data sent successfully, size=${data.size}, queue size=" + webSocket!!.queueSize())
        } else {
            Log.e(TAG2, "Failed to send audio data, queue size=" + webSocket!!.queueSize())
        }
        return sent
    }

    fun sendCommand(command: String) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send command")
            return
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected, cannot send command: $command")
            return
        }

        Log.i(TAG, ">>> Sending command: $command")
        val sent = webSocket!!.send(command)
        if (sent) {
            Log.i(TAG, "Command sent successfully: $command")
        } else {
            Log.e(TAG, "Failed to send command: $command, queue size=" + webSocket!!.queueSize())
        }
    }

    fun sendFinish() {
        sendCommand(Constants.CMD_FINISH)
    }

    fun sendConnect() {
        sendCommand(Constants.CMD_CONNECT)
    }

    fun sendClose() {
        sendCommand(Constants.CMD_CLOSE)
        disconnect()
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectRunnable?.let {
            mainHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
        webSocket?.let {
            isConnected = false
            it.cancel()
        }
        hasConnectedBefore = false
    }

    fun reconnect() {
        isManualDisconnect = false
        reconnectRunnable?.let {
            mainHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
        webSocket?.let {
            isConnected = false
            it.close(1000, "Reconnect")
        }
        mainHandler.postDelayed({ connect() }, getReconnectDelay())
    }

    private fun getReconnectDelay(): Long {
        val delay = when (reconnectAttempts) {
            1 -> 1000L
            2 -> 2000L
            3 -> 4000L
            else -> 8000L
        }
        return delay
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private const val TAG2 = "SendAudios"
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private var instance: WebSocketManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): WebSocketManager {
            if (instance == null) {
                instance = WebSocketManager()
            }
            return instance!!
        }
    }
}
