package com.aibabel.voicetrans.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.voicetrans.model.TranslationResult
import com.aibabel.voicetrans.util.Constants
import com.google.gson.Gson
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
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient
    private var gson: Gson
    private var langSource: String = Constants.LANG_ZH
    private var langTarget: String = Constants.LANG_ID
    @Volatile
    private var isConnected = false
    private var isManualDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectRunnable: Runnable? = null
    private var callback: WebSocketCallback? = null
    private var mainHandler: Handler

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

    fun getLangSource(): String = langSource

    fun getLangTarget(): String = langTarget

    fun isConnected(): Boolean = isConnected

    fun connect() {
        Log.i(TAG, ">>> WebSocket connect() called")
        if (isConnected && webSocket != null) {
            Log.e(TAG, "WebSocket already connected")
            return
        }

        isManualDisconnect = false
        Log.i(TAG, "Connecting to: " + Constants.WS_URL)
        Log.i(TAG, "langSource: $langSource, langTarget: $langTarget")

        val sessionId = UUID.randomUUID().toString()
        Log.i(TAG, "SessionId: $sessionId")

        val request = Request.Builder()
            .url(Constants.WS_URL)
            .addHeader("Authorization", Constants.ACCESS_TOKEN)
            .addHeader("langSource", langSource)
            .addHeader("langTarget", langTarget)
            .addHeader("sessionId", sessionId)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.e(TAG, "WebSocket connected successfully!")
                Log.e(TAG, "Response code: " + response.code)
                isConnected = true
                reconnectAttempts = 0

                mainHandler.post {
                    callback?.onConnected()
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

                mainHandler.post {
                    callback?.onBinaryData(data)
                }
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
                if (reconnectAttempts <= 3) {
                    reconnect()
                } else {
                    mainHandler.post {
                        callback?.onError(t.message ?: "Unknown error")
                    }
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

    fun sendAudioData(data: ByteArray) {
        if (webSocket == null) {
            Log.e(TAG, "WebSocket is null, cannot send audio")
            return
        }
        if (!isConnected) {
            Log.e(TAG, "WebSocket not connected (isConnected=false), cannot send audio. Queue size=" + (webSocket!!.queueSize()))
            return
        }

        Log.i(TAG, ">>> Sending audio data: " + data.size + " bytes")
        val sent = webSocket!!.send(ByteString.of(*data))
        if (sent) {
            Log.i(TAG, "Audio data sent successfully, queue size=" + webSocket!!.queueSize())
        } else {
            Log.e(TAG, "Failed to send audio data, queue size=" + webSocket!!.queueSize())
        }
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

    fun sendClose() {
        sendCommand(Constants.CMD_CLOSE)
    }

    fun disconnect() {
        isManualDisconnect = true
        reconnectRunnable?.let {
            mainHandler.removeCallbacks(it)
            reconnectRunnable = null
        }
        webSocket?.let {
            isConnected = false
            it.close(1000, "User disconnect")
        }
    }

    fun reconnect() {
        disconnect()
        mainHandler.postDelayed({ connect() }, 500)
    }

    companion object {
        private const val TAG = "WebSocketManager"
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
