package com.aibabel.voicetrans.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibabel.voicetrans.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.WebSocket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 音频发送队列管理器
 * 
 * 工作流程：
 * 1. 录音数据先入库
 * 2. 通知发送队列开始发送
 * 3. 从DB读取未发送分段 → 通过WebSocket发送 → 标记为已发送
 * 4. 如果WebSocket断开，暂停发送
 * 5. WebSocket重连后，自动恢复发送未标记的数据
 */
class AudioSendManager(
    private val repository: AudioSegmentRepository,
    private val webSocketManager: WebSocketManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Mutex()
    private var sendJob: Job? = null
    private var currentSessionId: String? = null
    private val isSending = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 设置当前会话ID
     */
    fun setSessionId(sessionId: String) {
        currentSessionId = sessionId
    }

    /**
     * 保存音频分段到数据库并触发发送
     */
    fun saveAndSend(sessionId: String, segmentIndex: Int, audioData: ByteArray) {
        scope.launch {
            val id = repository.insertSegment(sessionId, segmentIndex, audioData)
            Log.i(TAG, "Segment saved: session=$sessionId, index=$segmentIndex, id=$id, size=${audioData.size}")
            triggerSend(sessionId)
        }
    }

    /**
     * 触发发送（如果未在发送中且WebSocket已连接）
     */
    fun triggerSend(sessionId: String) {
        if (!webSocketManager.isConnected()) {
            Log.w(TAG, "WebSocket not connected, segments will be sent after reconnect")
            return
        }
        if (!isSending.compareAndSet(false, true)) {
            Log.d(TAG, "Send loop already running")
            return
        }

        sendJob = scope.launch {
            try {
                sendLoop(sessionId)
            } catch (e: Exception) {
                Log.e(TAG, "Send loop error: ${e.message}")
            } finally {
                isSending.set(false)
            }
        }
    }

    /**
     * 发送循环：持续从DB读取未发送分段并发送
     */
    private suspend fun sendLoop(sessionId: String) {
        while (true) {
            if (!webSocketManager.isConnected()) {
                Log.w(TAG, "WebSocket disconnected during send loop, pausing")
                break
            }

            val segments = sendLock.withLock {
                repository.getUnsentSegments(sessionId)
            }

            if (segments.isEmpty()) {
                Log.d(TAG, "No more unsent segments for session=$sessionId")
                break
            }

            for (segment in segments) {
                if (!webSocketManager.isConnected()) {
                    Log.w(TAG, "WebSocket disconnected at segment ${segment.segmentIndex}, will resume after reconnect")
                    return
                }

                val sent = webSocketManager.sendAudioData(segment.audioData)
                if (sent) {
                    repository.markAsSent(segment.id)
                    Log.i(TAG, "Sent segment: index=${segment.segmentIndex}, id=${segment.id}, size=${segment.audioData.size}")
                } else {
                    Log.e(TAG, "Failed to send segment: index=${segment.segmentIndex}, stopping send loop")
                    return
                }
            }
        }
    }

    /**
     * WebSocket重连后调用，恢复未发送数据的发送
     */
    fun onResumeAfterReconnect() {
        val sessionId = currentSessionId ?: return
        Log.i(TAG, "Resuming send after reconnect, session=$sessionId")
        triggerSend(sessionId)
    }

    /**
     * 发送finish指令
     */
    fun sendFinish() {
        webSocketManager.sendFinish()
    }

    /**
     * 检查是否所有分段都已发送
     */
    suspend fun isAllSent(sessionId: String): Boolean {
        return repository.getUnsentCount(sessionId) == 0
    }

    /**
     * 清理会话数据
     */
    suspend fun cleanupSession(sessionId: String) {
        repository.deleteBySession(sessionId)
        Log.i(TAG, "Cleaned up session=$sessionId")
    }

    /**
     * 清理旧数据（已发送且超过指定时间）
     */
    suspend fun cleanupOldSent(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        repository.cleanupOldSent(System.currentTimeMillis() - maxAgeMs)
    }

    fun release() {
        sendJob?.cancel()
        currentSessionId = null
    }

    companion object {
        private const val TAG = "AudioSendManager"
    }
}
